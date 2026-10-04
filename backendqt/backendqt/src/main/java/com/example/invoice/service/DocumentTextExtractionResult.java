package com.example.invoice.service;

import java.math.BigDecimal;
import java.util.List;

public record DocumentTextExtractionResult(
		String text, List<PageText> pages, String engine, String engineVersion,
		String language, String sourceType, BigDecimal confidence, long durationMs,
		boolean visionFallbackRecommended, List<String> warnings) {
	public record PageText(int pageNumber, String extractionMethod, String text, BigDecimal ocrConfidence,
			BigDecimal quality, boolean requiresVisionFallback) {
		/** Compatibility constructor for callers created before per-page quality metadata. */
		public PageText(int pageNumber, String text, BigDecimal confidence) {
			this(pageNumber, "UNKNOWN", text, confidence, confidence, false);
		}

		public BigDecimal confidence() { return ocrConfidence; }
	}
}
