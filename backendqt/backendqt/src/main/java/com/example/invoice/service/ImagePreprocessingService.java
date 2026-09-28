package com.example.invoice.service;

import com.example.invoice.config.AiProperties;
import com.example.invoice.exception.BadRequestException;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ImagePreprocessingService {
	private final AiProperties properties;

	public ImagePreprocessingResult preprocess(byte[] source, String contentType) {
		long started = System.nanoTime();
		try {
			BufferedImage image = ImageIO.read(new ByteArrayInputStream(source));
			if (image == null) throw new BadRequestException("Uploaded image cannot be decoded");
			int width = image.getWidth();
			int height = image.getHeight();
			if (!properties.getPreprocessing().isEnabled()) return result(source, contentType, false, 0, width, height, width, height, started, "Preprocessing is disabled");
			double angle = detectSkewAngle(image);
			boolean deskew = Math.abs(angle) >= properties.getPreprocessing().getMinimumDeskewAngleDegrees();
			boolean enhance = needsContrastEnhancement(image);
			if (!deskew && !enhance) return result(source, contentType, false, angle, width, height, width, height, started, null);
			BufferedImage processed = deskew ? rotate(image, -angle) : toArgb(image);
			if (processed.getWidth() * (long) processed.getHeight() <= properties.getPreprocessing().getMaxPixelsForDenoise()) processed = medianDenoise(processed);
			if (enhance) processed = enhanceContrast(processed);
			ByteArrayOutputStream output = new ByteArrayOutputStream();
			ImageIO.write(processed, "png", output);
			return result(output.toByteArray(), "image/png", true, angle, width, height, processed.getWidth(), processed.getHeight(), started, null);
		} catch (BadRequestException exception) {
			throw exception;
		} catch (Exception exception) {
			throw new IllegalStateException("Image preprocessing failed", exception);
		}
	}

	private ImagePreprocessingResult result(byte[] bytes, String type, boolean applied, double angle, int originalWidth, int originalHeight, int processedWidth, int processedHeight, long started, String warning) {
		return new ImagePreprocessingResult(bytes, type, applied, angle, originalWidth, originalHeight, processedWidth, processedHeight, (System.nanoTime() - started) / 1_000_000, warning);
	}

	private double detectSkewAngle(BufferedImage image) {
		double sumX = 0, sumY = 0, count = 0;
		for (int y = 0; y < image.getHeight(); y += 2) for (int x = 0; x < image.getWidth(); x += 2) if (brightness(image.getRGB(x, y)) < 160) { sumX += x; sumY += y; count++; }
		if (count < 100) return 0;
		double centerX = sumX / count, centerY = sumY / count, xx = 0, yy = 0, xy = 0;
		for (int y = 0; y < image.getHeight(); y += 2) for (int x = 0; x < image.getWidth(); x += 2) if (brightness(image.getRGB(x, y)) < 160) { double dx = x - centerX, dy = y - centerY; xx += dx * dx; yy += dy * dy; xy += dx * dy; }
		double angle = Math.toDegrees(0.5 * Math.atan2(2 * xy, xx - yy));
		if (angle > 45) angle -= 90;
		if (angle < -45) angle += 90;
		return angle;
	}

	private boolean needsContrastEnhancement(BufferedImage image) {
		double sum = 0, squared = 0; long count = 0;
		for (int y = 0; y < image.getHeight(); y += 4) for (int x = 0; x < image.getWidth(); x += 4) { int value = brightness(image.getRGB(x, y)); sum += value; squared += value * value; count++; }
		double variance = squared / count - Math.pow(sum / count, 2);
		return variance < 650;
	}

	private BufferedImage rotate(BufferedImage source, double degrees) {
		double radians = Math.toRadians(degrees), sin = Math.abs(Math.sin(radians)), cos = Math.abs(Math.cos(radians));
		int width = (int) Math.ceil(source.getWidth() * cos + source.getHeight() * sin), height = (int) Math.ceil(source.getHeight() * cos + source.getWidth() * sin);
		BufferedImage target = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = target.createGraphics();
		graphics.setColor(Color.WHITE); graphics.fillRect(0, 0, width, height);
		graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
		AffineTransform transform = AffineTransform.getTranslateInstance((width - source.getWidth()) / 2.0, (height - source.getHeight()) / 2.0);
		transform.rotate(radians, source.getWidth() / 2.0, source.getHeight() / 2.0); graphics.drawImage(source, transform, null); graphics.dispose();
		return target;
	}

	private BufferedImage medianDenoise(BufferedImage source) {
		BufferedImage target = toArgb(source);
		for (int y = 1; y < source.getHeight() - 1; y++) for (int x = 1; x < source.getWidth() - 1; x++) {
			int[] r = new int[9], g = new int[9], b = new int[9];
			int index = 0;
			for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) { int rgb = source.getRGB(x + dx, y + dy); r[index] = (rgb >> 16) & 0xff; g[index] = (rgb >> 8) & 0xff; b[index++] = rgb & 0xff; }
			java.util.Arrays.sort(r); java.util.Arrays.sort(g); java.util.Arrays.sort(b); target.setRGB(x, y, 0xff000000 | r[4] << 16 | g[4] << 8 | b[4]);
		}
		return target;
	}

	private BufferedImage enhanceContrast(BufferedImage source) {
		BufferedImage target = toArgb(source);
		for (int y = 0; y < source.getHeight(); y++) for (int x = 0; x < source.getWidth(); x++) { int rgb = source.getRGB(x, y); int r = clamp((((rgb >> 16) & 0xff) - 128) * 1.2 + 128); int g = clamp((((rgb >> 8) & 0xff) - 128) * 1.2 + 128); int b = clamp(((rgb & 0xff) - 128) * 1.2 + 128); target.setRGB(x, y, 0xff000000 | r << 16 | g << 8 | b); }
		return target;
	}

	private BufferedImage toArgb(BufferedImage source) { BufferedImage target = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB); Graphics2D graphics = target.createGraphics(); graphics.setColor(Color.WHITE); graphics.fillRect(0, 0, target.getWidth(), target.getHeight()); graphics.drawImage(source, 0, 0, null); graphics.dispose(); return target; }
	private int brightness(int rgb) { return (((rgb >> 16) & 0xff) * 299 + ((rgb >> 8) & 0xff) * 587 + (rgb & 0xff) * 114) / 1000; }
	private int clamp(double value) { return (int) Math.max(0, Math.min(255, Math.round(value))); }
}
