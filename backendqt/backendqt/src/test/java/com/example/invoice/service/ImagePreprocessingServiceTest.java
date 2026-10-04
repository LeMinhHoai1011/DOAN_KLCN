package com.example.invoice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.invoice.config.AiProperties;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class ImagePreprocessingServiceTest {
	@Test
	void enhancesLowContrastImageWithoutChangingOriginalInput() throws Exception {
		BufferedImage image = new BufferedImage(40, 20, BufferedImage.TYPE_INT_RGB);
		for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) image.setRGB(x, y, new Color(145, 145, 145).getRGB());
		ByteArrayOutputStream original = new ByteArrayOutputStream();
		ImageIO.write(image, "png", original);

		ImagePreprocessingResult result = new ImagePreprocessingService(new AiProperties()).preprocess(original.toByteArray(), "image/png");

		assertTrue(result.applied());
		assertEquals("image/png", result.contentType());
		assertEquals(40, result.originalWidth());
		assertEquals(20, result.originalHeight());
		assertTrue(result.bytes().length > 0);
	}
}
