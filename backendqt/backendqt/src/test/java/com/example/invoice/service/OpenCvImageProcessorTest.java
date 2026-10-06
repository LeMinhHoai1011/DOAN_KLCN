package com.example.invoice.service;

import static org.junit.jupiter.api.Assertions.*;

import com.example.invoice.config.AiProperties;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class OpenCvImageProcessorTest {
	@Test void normalDoesNotAlterDimensionsOrDeskewStraightImage() throws Exception {
		byte[] source=image(800,1400); ImageQualityResult quality=new ImageQualityAnalyzer().analyze(source);
		var result=new OpenCvImageProcessor(new AiProperties()).process(source,OcrProcessingProfile.NORMAL,quality);
		assertEquals(800,result.processedWidth()); assertEquals(1400,result.processedHeight());
		assertEquals(0,result.appliedDeskewAngleDegrees()); assertTrue(result.operations().isEmpty());
	}

	@Test void enhancedConvertsToGrayAndUpscalesSmallImage() throws Exception {
		byte[] source=image(200,100); ImageQualityResult quality=new ImageQualityAnalyzer().analyze(source);
		var result=new OpenCvImageProcessor(new AiProperties()).process(source,OcrProcessingProfile.ENHANCED,quality);
		BufferedImage output=ImageIO.read(new ByteArrayInputStream(result.bytes()));
		assertEquals(400,output.getWidth()); assertEquals(200,output.getHeight());
		assertTrue(result.operations().contains("grayscale")); assertTrue(result.operations().contains("upscale"));
	}

	@Test void aggressiveAppliesAdaptiveThresholdWithoutBreakingDimensions() throws Exception {
		byte[] source=image(1400,1400); ImageQualityResult quality=new ImageQualityAnalyzer().analyze(source);
		var result=new OpenCvImageProcessor(new AiProperties()).process(source,OcrProcessingProfile.AGGRESSIVE,quality);
		assertEquals(1400,result.processedWidth()); assertEquals(1400,result.processedHeight());
		assertTrue(result.operations().contains("adaptive-threshold"));
	}

	private byte[] image(int width,int height) throws Exception {
		BufferedImage image=new BufferedImage(width,height,BufferedImage.TYPE_INT_RGB);
		for(int y=0;y<height;y++) for(int x=0;x<width;x++) image.setRGB(x,y,((x/10+y/10)%2==0?Color.WHITE:Color.BLACK).getRGB());
		ByteArrayOutputStream out=new ByteArrayOutputStream(); ImageIO.write(image,"png",out); return out.toByteArray();
	}
}
