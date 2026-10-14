package com.example.invoice.service;

public record ImageQualityResult(double brightness, double contrast, double sharpness,
		double skewAngleDegrees, double skewConfidence, int width, int height, double qualityScore,
		double noiseLevel, double backgroundVariation, double estimatedTextHeight) {
	public ImageQualityResult(double brightness, double contrast, double sharpness,
			double skewAngleDegrees, double skewConfidence, int width, int height, double qualityScore) {
		this(brightness, contrast, sharpness, skewAngleDegrees, skewConfidence, width, height,
				qualityScore, 0, 0, 0);
	}
}
