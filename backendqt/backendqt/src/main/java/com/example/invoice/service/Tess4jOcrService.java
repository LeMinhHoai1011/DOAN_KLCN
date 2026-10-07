package com.example.invoice.service;

import com.example.invoice.config.AiProperties;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import lombok.RequiredArgsConstructor;
import net.sourceforge.tess4j.ITessAPI;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.Word;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class Tess4jOcrService {
	private static final Logger log = LoggerFactory.getLogger(Tess4jOcrService.class);
	private final AiProperties properties;

	static {
		System.setProperty("jna.encoding", "UTF-8");
	}

	/** Failure is represented in the result so the vision provider remains a safe fallback. */
	public OcrPageResult recognize(byte[] imageBytes, int pageNumber) {
		long started = System.nanoTime();
		try {
			String requestedLanguage = properties.getOcr().getLanguage() == null
					|| properties.getOcr().getLanguage().isBlank()
							? "vie+eng" : properties.getOcr().getLanguage().trim();
			String configuredPath = properties.getOcr().getDataPath();
			Path dataPath = Path.of(configuredPath == null || configuredPath.isBlank()
					? "./tessdata" : configuredPath).toAbsolutePath().normalize();
			boolean vieAvailable = Files.isRegularFile(dataPath.resolve("vie.traineddata"));
			boolean engAvailable = Files.isRegularFile(dataPath.resolve("eng.traineddata"));
			List<String> missingLanguages = java.util.Arrays.stream(requestedLanguage.split("\\+"))
					.map(String::trim)
					.filter(language -> !Files.isRegularFile(dataPath.resolve(language + ".traineddata")))
					.toList();
			String effectiveLanguage = !properties.getOcr().isEnabled() ? "disabled"
					: missingLanguages.isEmpty() ? requestedLanguage : "not-run";
			log.info("OCR page={} requestedLanguage={} effectiveLanguage={} tessdataPath={} vieAvailable={} engAvailable={} fallbackUsed={}",
					pageNumber, requestedLanguage, effectiveLanguage, dataPath, vieAvailable, engAvailable, false);
			if (!properties.getOcr().isEnabled()) {
				return OcrPageResult.unavailable(pageNumber, "Tess4J OCR is disabled", elapsed(started));
			}
			if (!missingLanguages.isEmpty()) {
				String warning = "OCR_LANGUAGE_DATA_MISSING: thiếu " + String.join(", ", missingLanguages)
						+ ".traineddata tại " + dataPath;
				log.error("OCR page={} failed: {}; requestedLanguage={} effectiveLanguage={} tessdataPath={} vieAvailable={} engAvailable={} fallbackUsed={}",
						pageNumber, warning, requestedLanguage, effectiveLanguage, dataPath, vieAvailable, engAvailable, false);
				return OcrPageResult.unavailable(pageNumber, warning, elapsed(started));
			}

			BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageBytes));
			if (image == null) return OcrPageResult.unavailable(pageNumber, "Image format could not be decoded for OCR", elapsed(started));
			return recognize(image, pageNumber, effectiveLanguage, null, started, dataPath.toString());
		} catch (Exception | LinkageError exception) {
			String warning = "Tess4J OCR unavailable: " + exception.getClass().getSimpleName() + ": " + safe(exception.getMessage());
			log.error("OCR page={} failed: {}; fallbackUsed=false", pageNumber, warning);
			return OcrPageResult.unavailable(pageNumber, warning, elapsed(started));
		}
	}

	private OcrPageResult recognize(BufferedImage image, int pageNumber, String language, String warning, long started, String dataPath) {
		System.setProperty("jna.encoding", "UTF-8");
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

	private static double ratio(int value, int total) { return total == 0 ? 0 : (double) value / total; }
	private static long elapsed(long started) { return (System.nanoTime() - started) / 1_000_000; }
	private static String safe(String value) { return value == null ? "unknown error" : value.replaceAll("[\\r\\n]+", " "); }
}
