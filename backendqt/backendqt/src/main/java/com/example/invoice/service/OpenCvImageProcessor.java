package com.example.invoice.service;

import com.example.invoice.config.AiProperties;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.Point;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.CLAHE;
import org.opencv.imgproc.Imgproc;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OpenCvImageProcessor {
	private static final double LOW_CONTRAST = 45;
	private static final double LOW_SHARPNESS = 350;
	private static final int SMALL_MINIMUM_DIMENSION = 1200;
	private static final double SKEW_CONFIDENCE_REQUIRED = 0.30;
	private final AiProperties properties;

	public ProcessedImage process(byte[] source, OcrProcessingProfile profile, ImageQualityResult quality) {
		OpenCvRuntime.load();
		Mat color = Imgcodecs.imdecode(new MatOfByte(source), Imgcodecs.IMREAD_COLOR);
		if (color.empty()) throw new IllegalArgumentException("Không thể giải mã ảnh để tiền xử lý");
		int originalWidth = color.cols(), originalHeight = color.rows();
		List<String> operations = new ArrayList<>();
		double appliedAngle = 0;
		Mat current = color;
		if (profile != OcrProcessingProfile.NORMAL) {
			Mat gray = new Mat(); Imgproc.cvtColor(current, gray, Imgproc.COLOR_BGR2GRAY); current.release(); current = gray; operations.add("grayscale");
			if (quality.contrast() < LOW_CONTRAST || profile == OcrProcessingProfile.AGGRESSIVE) {
				Mat enhanced = new Mat(); CLAHE clahe = Imgproc.createCLAHE(2.0, new Size(8, 8)); clahe.apply(current, enhanced);
				current.release(); current = enhanced; operations.add("clahe");
			}
			Mat denoised = new Mat(); Imgproc.bilateralFilter(current, denoised, 5, 35, 35); current.release(); current = denoised; operations.add("denoise");
			if (quality.sharpness() < LOW_SHARPNESS || profile == OcrProcessingProfile.AGGRESSIVE) {
				Mat blurred = new Mat(), sharpened = new Mat(); Imgproc.GaussianBlur(current, blurred, new Size(0, 0), 1.0);
				Core.addWeighted(current, 1.5, blurred, -0.5, 0, sharpened); current.release(); blurred.release(); current = sharpened; operations.add("sharpen");
			}
			if (profile == OcrProcessingProfile.AGGRESSIVE && shouldDeskew(quality)) {
				appliedAngle = quality.skewAngleDegrees();
				Mat rotated = rotate(current, -appliedAngle); current.release(); current = rotated; operations.add("deskew");
			}
			if (Math.min(originalWidth, originalHeight) < SMALL_MINIMUM_DIMENSION) {
				double factor = properties.getOcr().getAdaptive().getUpscaleFactor();
				Mat upscaled = new Mat(); Imgproc.resize(current, upscaled, new Size(), factor, factor, Imgproc.INTER_CUBIC);
				current.release(); current = upscaled; operations.add("upscale");
			}
			if (profile == OcrProcessingProfile.AGGRESSIVE) {
				Mat thresholded = new Mat(); Imgproc.adaptiveThreshold(current, thresholded, 255,
						Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, Imgproc.THRESH_BINARY, 31, 12);
				current.release(); current = thresholded; operations.add("adaptive-threshold");
			}
		}
		MatOfByte output = new MatOfByte();
		if (!Imgcodecs.imencode(".png", current, output)) throw new IllegalStateException("Không thể mã hóa ảnh OpenCV");
		ProcessedImage result = new ProcessedImage(output.toArray(), "image/png", profile, originalWidth, originalHeight,
				current.cols(), current.rows(), appliedAngle, List.copyOf(operations));
		current.release(); output.release();
		return result;
	}

	private boolean shouldDeskew(ImageQualityResult quality) {
		double absolute = Math.abs(quality.skewAngleDegrees());
		return quality.skewConfidence() >= SKEW_CONFIDENCE_REQUIRED
				&& absolute >= properties.getOcr().getAdaptive().getMinimumDeskewAngleDegrees()
				&& absolute <= properties.getOcr().getAdaptive().getMaximumDeskewAngleDegrees();
	}

	private Mat rotate(Mat source, double degrees) {
		Point center = new Point(source.cols() / 2.0, source.rows() / 2.0);
		Mat matrix = Imgproc.getRotationMatrix2D(center, degrees, 1.0);
		Mat target = new Mat(); Imgproc.warpAffine(source, target, matrix, source.size(), Imgproc.INTER_CUBIC,
				Core.BORDER_CONSTANT, new org.opencv.core.Scalar(255)); matrix.release(); return target;
	}

	public record ProcessedImage(byte[] bytes, String contentType, OcrProcessingProfile profile,
			int originalWidth, int originalHeight, int processedWidth, int processedHeight,
			double appliedDeskewAngleDegrees, List<String> operations) {
		public boolean preprocessingApplied() { return !operations.isEmpty(); }
	}
}
