package com.example.invoice.service;

import com.example.invoice.config.AiProperties;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdaptiveOcrService {
	private static final Logger log = LoggerFactory.getLogger(AdaptiveOcrService.class);
	private static final int TARGET_TEXT_LENGTH = 120;
	private final AiProperties properties;
	private final ImageQualityAnalyzer qualityAnalyzer;
	private final OpenCvImageProcessor imageProcessor;
	private final Tess4jOcrService tess4jOcrService;

	public AdaptiveOcrResult recognize(byte[] source, String contentType, int pageNumber) {
		ImageQualityResult imageQuality;
		try {
			imageQuality = qualityAnalyzer.analyze(source);
		} catch (RuntimeException | LinkageError exception) {
			log.warn("OCR page={} quality analysis unavailable; original image used: {}", pageNumber, safe(exception.getMessage()));
			OcrPageResult fallback = tess4jOcrService.recognize(source, pageNumber);
			return new AdaptiveOcrResult(fallback, source, contentType, OcrProcessingProfile.NORMAL,
					evaluate(fallback), 1, null, "OpenCV quality analysis unavailable; original image used");
		}
		if (!properties.getOcr().getAdaptive().isEnabled()) {
			OcrPageResult page = tess4jOcrService.recognize(source, pageNumber);
			return new AdaptiveOcrResult(page, source, contentType, OcrProcessingProfile.NORMAL,
					evaluate(page), 1, imageQuality, null);
		}
		List<Attempt> attempts = new ArrayList<>();
		int attemptedProfiles = 0;
		for (OcrProcessingProfile profile : OcrProcessingProfile.values()) {
			if (attemptedProfiles >= Math.max(1, properties.getOcr().getAdaptive().getMaxAttempts())) break;
			attemptedProfiles++;
			try {
				OpenCvImageProcessor.ProcessedImage processed = imageProcessor.process(source, profile, imageQuality);
				OcrPageResult raw = tess4jOcrService.recognize(processed.bytes(), pageNumber);
				OcrPageResult mapped = mapToOriginal(raw, processed);
				double score = evaluate(mapped);
				attempts.add(new Attempt(mapped, processed, score));
				log.info("OCR page={} profile={} quality={} words={} operations={}", pageNumber, profile,
						String.format("%.3f", score), mapped.words().size(), processed.operations());
				if (score >= properties.getOcr().getAdaptive().getGoodQualityScore()) break;
				log.info("OCR page={} retryAfter={} reason=LOW_QUALITY", pageNumber, profile);
			} catch (RuntimeException | LinkageError exception) {
				log.warn("OCR page={} profile={} preprocessing failed; retaining earlier attempts: {}",
						pageNumber, profile, safe(exception.getMessage()));
			}
		}
		if (attempts.isEmpty()) {
			OcrPageResult fallback = tess4jOcrService.recognize(source, pageNumber);
			return new AdaptiveOcrResult(fallback, source, contentType, OcrProcessingProfile.NORMAL,
					evaluate(fallback), 1, imageQuality, "OpenCV preprocessing unavailable; original image used");
		}
		Attempt baseline = attempts.getFirst();
		Attempt candidate = attempts.stream().max(Comparator.comparingDouble(Attempt::qualityScore)).orElseThrow();
		double requiredGain = Math.max(0, properties.getOcr().getAdaptive().getMinimumQualityImprovement());
		Attempt best = candidate.processed().profile() != OcrProcessingProfile.NORMAL
				&& candidate.qualityScore() < baseline.qualityScore() + requiredGain ? baseline : candidate;
		log.info("OCR page={} selected={} quality={} attempts={}", pageNumber, best.processed().profile(),
				String.format("%.3f", best.qualityScore()), attempts.size());
		return new AdaptiveOcrResult(best.page(), best.processed().bytes(), best.processed().contentType(),
				best.processed().profile(), best.qualityScore(), attempts.size(), imageQuality, null);
	}

	double evaluate(OcrPageResult result) {
		return evaluateDetailed(result).score();
	}

	OcrQualityEvaluation evaluateDetailed(OcrPageResult result) {
		if (result == null || !result.successful()) return OcrQualityEvaluation.empty();
		List<OcrWord> validWords = result.words().stream().filter(word -> word.text() != null
				&& word.text().codePoints().anyMatch(Character::isLetterOrDigit)).toList();
		String compact = result.text().replaceAll("\\s+", "");
		long validCharacters = compact.codePoints().filter(cp -> Character.isLetterOrDigit(cp)
				|| Character.getType(cp) == Character.CURRENCY_SYMBOL || ",.-/:()%".indexOf(cp) >= 0).count();
		double validCharacterRatio = compact.isEmpty() ? 0 : validCharacters / (double) compact.codePointCount(0, compact.length());
		double confidence = clamp01(result.confidence() / 100.0);
		double wordCoverage = clamp01(validWords.size() / (double) Math.max(1, properties.getOcr().getAdaptive().getMinimumValidWords()));
		double textCoverage = clamp01(compact.length() / (double) TARGET_TEXT_LENGTH);
		double lowConfidenceRatio = validWords.isEmpty() ? 1 : validWords.stream()
				.filter(word -> word.confidence() < properties.getOcr().getAdaptive().getLowConfidencePercent()).count()
				/ (double) validWords.size();
		double layoutQuality = layoutQuality(validWords);
		double criticalFieldCoverage = criticalFieldCoverage(result.text());
		double numericConsistency = numericConsistency(validWords);
		double score = clamp01(0.28 * confidence + 0.18 * wordCoverage + 0.14 * validCharacterRatio
				+ 0.12 * textCoverage + 0.10 * layoutQuality + 0.10 * criticalFieldCoverage
				+ 0.08 * numericConsistency - 0.12 * lowConfidenceRatio);
		return new OcrQualityEvaluation(score, confidence, validCharacterRatio, textCoverage, layoutQuality,
				criticalFieldCoverage, numericConsistency, lowConfidenceRatio, validWords.size());
	}

	private double layoutQuality(List<OcrWord> words) {
		if (words.size() < 2) return words.isEmpty() ? 0 : 0.35;
		long validBoxes = words.stream().filter(word -> Double.isFinite(word.x()) && Double.isFinite(word.y())
				&& word.x() >= 0 && word.y() >= 0 && word.width() > 0 && word.height() > 0
				&& word.x() + word.width() <= 1.001 && word.y() + word.height() <= 1.001).count();
		double boxRatio = validBoxes / (double) words.size();
		long approximateLines = words.stream().map(word -> Math.round(word.y() * 100)).distinct().count();
		double lineEvidence = approximateLines >= 2 ? 1 : 0.6;
		return clamp01(0.7 * boxRatio + 0.3 * lineEvidence);
	}

	private double criticalFieldCoverage(String text) {
		String lower = text == null ? "" : text.toLowerCase(java.util.Locale.ROOT);
		int groups = 0;
		if (containsAny(lower, "hóa đơn", "hoa don", "invoice")) groups++;
		if (containsAny(lower, "mã số thuế", "ma so thue", "mst", "tax code")) groups++;
		if (containsAny(lower, "ngày", "ngay", "date")) groups++;
		if (containsAny(lower, "tổng", "tong", "total")) groups++;
		if (containsAny(lower, "thuế", "thue", "vat")) groups++;
		return groups / 5.0;
	}

	private boolean containsAny(String value, String... candidates) {
		for (String candidate : candidates) if (value.contains(candidate)) return true;
		return false;
	}

	private double numericConsistency(List<OcrWord> words) {
		List<String> numeric = words.stream().map(OcrWord::text).filter(java.util.Objects::nonNull)
				.filter(value -> value.matches(".*\\d.*")).toList();
		if (numeric.isEmpty()) return 0.5;
		long plausible = numeric.stream().filter(value -> value.matches("[\\p{L}]*[+()\\-.,/:\\s%]*\\d[\\d+()\\-.,/:\\s%]*[\\p{L}]*")).count();
		return plausible / (double) numeric.size();
	}

	private OcrPageResult mapToOriginal(OcrPageResult page, OpenCvImageProcessor.ProcessedImage image) {
		if (page.words().isEmpty()) return new OcrPageResult(page.pageNumber(), image.originalWidth(), image.originalHeight(),
				page.text(), page.confidence(), page.words(), page.durationMs(), page.warning(), page.language());
		List<OcrWord> words = page.words().stream().map(word -> remap(word, image)).toList();
		return new OcrPageResult(page.pageNumber(), image.originalWidth(), image.originalHeight(), page.text(),
				page.confidence(), words, page.durationMs(), page.warning(), page.language());
	}

	private OcrWord remap(OcrWord word, OpenCvImageProcessor.ProcessedImage image) {
		double x1 = word.x() * image.originalWidth(), y1 = word.y() * image.originalHeight();
		double x2 = (word.x() + word.width()) * image.originalWidth();
		double y2 = (word.y() + word.height()) * image.originalHeight();
		if (Math.abs(image.appliedDeskewAngleDegrees()) > 0.0001) {
			double[][] corners = {{x1,y1},{x2,y1},{x2,y2},{x1,y2}};
			double radians = Math.toRadians(image.appliedDeskewAngleDegrees());
			double cosine = Math.cos(radians), sine = Math.sin(radians);
			double centerX = image.originalWidth()/2.0, centerY = image.originalHeight()/2.0;
			x1 = Double.POSITIVE_INFINITY; y1 = Double.POSITIVE_INFINITY; x2 = 0; y2 = 0;
			for (double[] point : corners) {
				double dx = point[0]-centerX, dy = point[1]-centerY;
				// Inverse of OpenCV getRotationMatrix2D(center, -angle, 1).
				double x = centerX + dx*cosine + dy*sine, y = centerY - dx*sine + dy*cosine;
				x1=Math.min(x1,x); y1=Math.min(y1,y); x2=Math.max(x2,x); y2=Math.max(y2,y);
			}
		}
		x1=clip(x1,0,image.originalWidth()); y1=clip(y1,0,image.originalHeight());
		x2=clip(x2,0,image.originalWidth()); y2=clip(y2,0,image.originalHeight());
		return new OcrWord(word.id(), word.text(), word.confidence(), x1/image.originalWidth(), y1/image.originalHeight(),
				Math.max(0,x2-x1)/image.originalWidth(), Math.max(0,y2-y1)/image.originalHeight());
	}

	private static double clamp01(double value) { return Math.max(0, Math.min(1, value)); }
	private static double clip(double value, double min, double max) { return Math.max(min, Math.min(max, value)); }
	private static String safe(String value) { return value == null ? "unknown error" : value.replaceAll("[\\r\\n]+", " "); }
	private record Attempt(OcrPageResult page, OpenCvImageProcessor.ProcessedImage processed, double qualityScore) {}

	record OcrQualityEvaluation(double score, double confidence, double validCharacterRatio,
			double textCoverage, double layoutQuality, double criticalFieldCoverage,
			double numericConsistency, double lowConfidenceRatio, int validWordCount) {
		static OcrQualityEvaluation empty() {
			return new OcrQualityEvaluation(0, 0, 0, 0, 0, 0, 0, 1, 0);
		}
	}

	public record AdaptiveOcrResult(OcrPageResult page, byte[] selectedImageBytes, String selectedContentType,
			OcrProcessingProfile selectedProfile, double qualityScore, int attempts,
			ImageQualityResult imageQuality, String warning) {
		public boolean usable(AiProperties properties) {
			return page.successful() && qualityScore >= properties.getOcr().getAdaptive().getUsableQualityScore();
		}
	}
}
