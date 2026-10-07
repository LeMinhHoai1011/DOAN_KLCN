package com.example.invoice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.invoice.config.AiProperties;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Test;

class Tess4jOcrServiceTest {
	@TempDir
	Path tempDirectory;

	@Test
	void recognizesImageTextAndReturnsNormalizedWordBoundaries() throws Exception {
		AiProperties properties = new AiProperties();
		properties.getOcr().setLanguage("eng");
		BufferedImage image = new BufferedImage(900, 220, BufferedImage.TYPE_INT_RGB);
		Graphics2D graphics = image.createGraphics();
		graphics.setColor(Color.WHITE);
		graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
		graphics.setColor(Color.BLACK);
		graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 72));
		graphics.drawString("TOTAL 12345", 40, 140);
		graphics.dispose();
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		ImageIO.write(image, "png", output);

		OcrPageResult result = new Tess4jOcrService(properties).recognize(output.toByteArray(), 1);
		assertTrue(result.successful(), result.warning());
		assertTrue(result.text().contains("12345"));
		assertTrue(result.words().stream().allMatch(word -> word.x() >= 0 && word.y() >= 0
				&& word.x() + word.width() <= 1.00001 && word.y() + word.height() <= 1.00001));
	}

	@Test
	void recognizesVietnameseDiacriticsWithConfiguredDefaultLanguage() throws Exception {
		BufferedImage image = new BufferedImage(1500, 260, BufferedImage.TYPE_INT_RGB);
		Graphics2D graphics = image.createGraphics();
		graphics.setColor(Color.WHITE);
		graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
		graphics.setColor(Color.BLACK);
		graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 66));
		graphics.drawString("CỘNG HÒA XÃ HỘI CHỦ NGHĨA VIỆT NAM", 25, 155);
		graphics.dispose();
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		ImageIO.write(image, "png", output);

		byte[] source = output.toByteArray();
		AiProperties properties = new AiProperties();
		Tess4jOcrService ocr = new Tess4jOcrService(properties);
		OcrPageResult original = ocr.recognize(source, 1);

		assertVietnameseText(original);
		assertEquals("UTF8", System.getProperty("jna.encoding"));

		OpenCvImageProcessor processor = new OpenCvImageProcessor(properties);
		ImageQualityResult quality = new ImageQualityResult(170, 50, 500, 0, 0, 100, 100, 0.8);
		for (OcrProcessingProfile profile : List.of(OcrProcessingProfile.ENHANCED, OcrProcessingProfile.AGGRESSIVE)) {
			OpenCvImageProcessor.ProcessedImage processed = processor.process(source, profile, quality);
			OcrPageResult result = ocr.recognize(processed.bytes(), 1);
			System.out.printf("OCR_PREPROCESS profile=%s operations=%s text=%s%n",
					profile, processed.operations(), result.text());
			assertVietnameseText(result);
		}
	}

	@Test
	void missingVietnameseDataDoesNotFallBackToEnglish() throws Exception {
		Files.write(tempDirectory.resolve("eng.traineddata"), new byte[] { 1 });
		AiProperties properties = new AiProperties();
		properties.getOcr().setDataPath(tempDirectory.toString());
		properties.getOcr().setLanguage("vie+eng");

		OcrPageResult result = new Tess4jOcrService(properties).recognize(new byte[] { 1, 2, 3 }, 1);

		assertFalse(result.successful());
		assertTrue(result.warning().contains("vie.traineddata"));
		assertTrue(result.words().isEmpty());
	}

	@Test
	void disabledOcrCleanlyFallsBackWithoutLoadingNativeLibraries() {
		AiProperties properties = new AiProperties();
		properties.getOcr().setEnabled(false);
		OcrPageResult result = new Tess4jOcrService(properties).recognize(new byte[] { 1, 2, 3 }, 2);
		assertFalse(result.successful());
		assertTrue(result.warning().contains("disabled"));
		assertTrue(result.words().isEmpty());
	}

	private static void assertVietnameseText(OcrPageResult result) {
		assertTrue(result.successful(), result.warning());
		assertEquals("vie+eng", result.language());
		assertTrue(result.text().contains("CỘNG"), result.text());
		assertTrue(result.text().contains("VIỆT"), result.text());
	}
}
