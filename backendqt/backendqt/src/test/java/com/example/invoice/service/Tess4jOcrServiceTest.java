package com.example.invoice.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.invoice.config.AiProperties;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class Tess4jOcrServiceTest {
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
	void disabledOcrCleanlyFallsBackWithoutLoadingNativeLibraries() {
		AiProperties properties = new AiProperties();
		properties.getOcr().setEnabled(false);
		OcrPageResult result = new Tess4jOcrService(properties).recognize(new byte[] { 1, 2, 3 }, 2);
		assertFalse(result.successful());
		assertTrue(result.warning().contains("disabled"));
		assertTrue(result.words().isEmpty());
	}
}
