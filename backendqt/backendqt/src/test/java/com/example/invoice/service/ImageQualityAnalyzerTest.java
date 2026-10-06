package com.example.invoice.service;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class ImageQualityAnalyzerTest {
	private final ImageQualityAnalyzer analyzer = new ImageQualityAnalyzer();

	@Test void distinguishesBrightAndDarkImages() throws Exception {
		assertTrue(analyzer.analyze(solid(245)).brightness() > 230);
		assertTrue(analyzer.analyze(solid(15)).brightness() < 30);
	}

	@Test void detectsLowContrastImage() throws Exception {
		assertTrue(analyzer.analyze(solid(145)).contrast() < 2);
	}

	@Test void sharpEdgesScoreHigherThanBlurredUniformImage() throws Exception {
		BufferedImage clear = new BufferedImage(200, 100, BufferedImage.TYPE_INT_RGB);
		Graphics2D graphics = clear.createGraphics(); graphics.setColor(Color.WHITE); graphics.fillRect(0,0,200,100);
		graphics.setColor(Color.BLACK); for (int x=0;x<200;x+=10) graphics.fillRect(x,0,5,100); graphics.dispose();
		assertTrue(analyzer.analyze(bytes(clear)).sharpness() > analyzer.analyze(solid(145)).sharpness());
	}

	private byte[] solid(int value) throws Exception {
		BufferedImage image = new BufferedImage(200,100,BufferedImage.TYPE_INT_RGB);
		Graphics2D graphics=image.createGraphics(); graphics.setColor(new Color(value,value,value)); graphics.fillRect(0,0,200,100); graphics.dispose();
		return bytes(image);
	}
	private byte[] bytes(BufferedImage image) throws Exception { ByteArrayOutputStream out=new ByteArrayOutputStream(); ImageIO.write(image,"png",out); return out.toByteArray(); }
}
