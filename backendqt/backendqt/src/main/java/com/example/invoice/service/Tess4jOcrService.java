package com.example.invoice.service;

import com.example.invoice.config.AiProperties;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import lombok.RequiredArgsConstructor;
import net.sourceforge.tess4j.ITessAPI;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.Word;
import net.sourceforge.tess4j.util.LoadLibs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class Tess4jOcrService {
	private static final Logger log = LoggerFactory.getLogger(Tess4jOcrService.class);
	private final AiProperties properties;

	/** Failure is represented in the result so the vision provider remains a safe fallback. */
	public OcrPageResult recognize(byte[] imageBytes, int pageNumber) {
		long started = System.nanoTime();
		if (!properties.getOcr().isEnabled()) return OcrPageResult.unavailable(pageNumber, "Tess4J OCR is disabled", 0);
		try {
			BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageBytes));
			if (image == null) return OcrPageResult.unavailable(pageNumber, "Image format could not be decoded for OCR", elapsed(started));
			try {
				LanguagePlan plan = resolveLanguage();
				return recognize(image, pageNumber, plan.language(), plan.warning(), started, plan.dataPath());
			} catch (Exception | LinkageError primary) {
				String configured = properties.getOcr().getLanguage();
				if (configured != null && configured.contains("eng") && !"eng".equals(configured.trim())) {
					String fallbackWarning = "Configured OCR language unavailable; fell back to bundled English: " + safe(primary.getMessage());
					try { return recognize(image, pageNumber, "eng", fallbackWarning, started,
							LoadLibs.extractTessResources("tessdata").getAbsolutePath()); }
					catch (Exception | LinkageError ignored) { /* report the primary deployment error below */ }
				}
				throw primary;
			}
		} catch (Exception | LinkageError exception) {
			// Native linkage and missing traineddata are deployment issues, not document failures.
			String warning = "Tess4J OCR unavailable: " + exception.getClass().getSimpleName() + ": " + safe(exception.getMessage());
			log.warn("{}", warning);
			return OcrPageResult.unavailable(pageNumber, warning, elapsed(started));
		}
	}

	private OcrPageResult recognize(BufferedImage image, int pageNumber, String language, String warning, long started, String dataPath) {
		Tesseract tesseract = new Tesseract();
		tesseract.setDatapath(dataPath);
		tesseract.setLanguage(language);
		tesseract.setPageSegMode(ITessAPI.TessPageSegMode.PSM_AUTO);
		// Uploads frequently carry a bogus 1-DPI metadata value. This affects Tesseract only;
		// the bitmap and therefore all returned word coordinates remain unchanged.
		tesseract.setVariable("user_defined_dpi", "300");
		List<Word> detected = tesseract.getWords(image, ITessAPI.TessPageIteratorLevel.RIL_WORD);
			List<OcrWord> words = new ArrayList<>();
			float confidenceTotal = 0;
			StringBuilder text = new StringBuilder();
			for (Word word : detected) {
				String value = word.getText() == null ? "" : word.getText().trim();
				if (value.isBlank()) continue;
				Rectangle box = word.getBoundingBox();
				String id = "p%d-w%d".formatted(pageNumber, words.size() + 1);
				words.add(new OcrWord(id, value, word.getConfidence(),
						ratio(box.x, image.getWidth()), ratio(box.y, image.getHeight()),
						ratio(box.width, image.getWidth()), ratio(box.height, image.getHeight())));
				confidenceTotal += word.getConfidence();
				if (!text.isEmpty()) text.append(' ');
				text.append(value);
			}
			float confidence = words.isEmpty() ? 0 : confidenceTotal / words.size();
		return new OcrPageResult(pageNumber, image.getWidth(), image.getHeight(), text.toString(), confidence,
				List.copyOf(words), elapsed(started), words.isEmpty() ? "Tess4J không nhận diện được văn bản" : warning, language);
	}

	private LanguagePlan resolveLanguage() {
		String configuredLanguage = properties.getOcr().getLanguage() == null ? "eng" : properties.getOcr().getLanguage().trim();
		String configuredPath = properties.getOcr().getDataPath();
		Path path = Path.of(configuredPath == null || configuredPath.isBlank() ? "./tessdata" : configuredPath).toAbsolutePath().normalize();
		List<String> missing = java.util.Arrays.stream(configuredLanguage.split("\\+"))
				.filter(language -> !Files.isRegularFile(path.resolve(language + ".traineddata"))).toList();
		if (missing.isEmpty()) return new LanguagePlan(path.toString(), configuredLanguage, null);
		String warning = "OCR_LANGUAGE_DATA_MISSING: thiếu " + String.join(", ", missing)
				+ ".traineddata tại " + path;
		log.warn("{}; fallback=eng", warning);
		String bundled = LoadLibs.extractTessResources("tessdata").getAbsolutePath();
		if (configuredLanguage.contains("eng")) return new LanguagePlan(bundled, "eng", warning + "; đã dùng eng");
		throw new IllegalStateException(warning);
	}

	private static double ratio(int value, int total) { return total == 0 ? 0 : (double) value / total; }
	private static long elapsed(long started) { return (System.nanoTime() - started) / 1_000_000; }
	private static String safe(String value) { return value == null ? "unknown error" : value.replaceAll("[\\r\\n]+", " "); }
	private record LanguagePlan(String dataPath, String language, String warning) {}
}
