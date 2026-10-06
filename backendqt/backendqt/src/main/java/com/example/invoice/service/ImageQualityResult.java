package com.example.invoice.service;

public record ImageQualityResult(double brightness, double contrast, double sharpness,
		double skewAngleDegrees, double skewConfidence, int width, int height, double qualityScore) {
}
