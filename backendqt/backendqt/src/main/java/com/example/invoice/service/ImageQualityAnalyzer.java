package com.example.invoice.service;

import java.util.ArrayList;
import java.util.List;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.RotatedRect;
import org.opencv.core.MatOfDouble;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.springframework.stereotype.Component;

@Component
public class ImageQualityAnalyzer {
	private static final double IDEAL_BRIGHTNESS = 175;
	private static final double BRIGHTNESS_TOLERANCE = 130;
	private static final double TARGET_CONTRAST = 55;
	private static final double TARGET_SHARPNESS = 500;
	private static final int MINIMUM_SKEW_POINTS = 100;

	public ImageQualityResult analyze(byte[] bytes) {
		OpenCvRuntime.load();
		Mat image = Imgcodecs.imdecode(new MatOfByte(bytes), Imgcodecs.IMREAD_COLOR);
		if (image.empty()) throw new IllegalArgumentException("Không thể giải mã ảnh để phân tích chất lượng");
		Mat gray = new Mat();
		Imgproc.cvtColor(image, gray, Imgproc.COLOR_BGR2GRAY);
		MatOfDouble mean = new MatOfDouble(), deviation = new MatOfDouble();
		Core.meanStdDev(gray, mean, deviation);
		Mat laplacian = new Mat();
		Imgproc.Laplacian(gray, laplacian, CvType.CV_64F);
		MatOfDouble lapMean = new MatOfDouble(), lapDeviation = new MatOfDouble();
		Core.meanStdDev(laplacian, lapMean, lapDeviation);
		double sharpness = lapDeviation.toArray()[0] * lapDeviation.toArray()[0];
		Skew skew = estimateSkew(gray);
		double brightness = mean.toArray()[0], contrast = deviation.toArray()[0];
		double brightnessScore = clamp01(1 - Math.abs(brightness - IDEAL_BRIGHTNESS) / BRIGHTNESS_TOLERANCE);
		double contrastScore = clamp01(contrast / TARGET_CONTRAST);
		double sharpnessScore = clamp01(sharpness / TARGET_SHARPNESS);
		double score = 0.30 * brightnessScore + 0.35 * contrastScore + 0.35 * sharpnessScore;
		ImageQualityResult result = new ImageQualityResult(brightness, contrast, sharpness,
				skew.angle(), skew.confidence(), image.cols(), image.rows(), clamp01(score));
		image.release(); gray.release(); laplacian.release(); mean.release(); deviation.release(); lapMean.release(); lapDeviation.release();
		return result;
	}

	private Skew estimateSkew(Mat gray) {
		Mat binary = new Mat();
		Imgproc.threshold(gray, binary, 0, 255, Imgproc.THRESH_BINARY_INV | Imgproc.THRESH_OTSU);
		List<Point> points = new ArrayList<>();
		for (int y = 0; y < binary.rows(); y += 2) for (int x = 0; x < binary.cols(); x += 2)
			if (binary.get(y, x)[0] > 0) points.add(new Point(x, y));
		binary.release();
		if (points.size() < MINIMUM_SKEW_POINTS) return new Skew(0, 0);
		MatOfPoint2f pointMat = new MatOfPoint2f();
		pointMat.fromList(points);
		RotatedRect rect = Imgproc.minAreaRect(pointMat);
		pointMat.release();
		double angle = rect.angle;
		if (angle > 45) angle -= 90;
		if (angle < -45) angle += 90;
		double coverage = rect.size.area() / Math.max(1.0, gray.cols() * (double) gray.rows());
		return new Skew(angle, clamp01(coverage * 2));
	}

	private static double clamp01(double value) { return Math.max(0, Math.min(1, value)); }
	private record Skew(double angle, double confidence) {}
}
