package com.example.invoice.service;

public record ImagePreprocessingResult(byte[] bytes, String contentType, boolean applied, double detectedAngleDegrees,
		int originalWidth, int originalHeight, int processedWidth, int processedHeight, long durationMs, String warning) {
}
