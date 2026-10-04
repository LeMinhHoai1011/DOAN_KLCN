package com.example.invoice.dto.ai;

public record ImagePreprocessingMetadata(boolean applied, double detectedAngleDegrees, int originalWidth,
		int originalHeight, int processedWidth, int processedHeight, long durationMs, String warning) {
}
