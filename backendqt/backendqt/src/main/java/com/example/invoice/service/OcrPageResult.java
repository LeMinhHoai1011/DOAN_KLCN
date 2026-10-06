package com.example.invoice.service;

import java.util.List;

public record OcrPageResult(int pageNumber, int imageWidth, int imageHeight, String text,
		float confidence, List<OcrWord> words, long durationMs, String warning, String language,
		List<OcrLine> lines, List<OcrBlock> blocks) {
	public OcrPageResult {
		words = words == null ? List.of() : List.copyOf(words);
		lines = lines == null ? List.of() : List.copyOf(lines);
		blocks = blocks == null ? List.of() : List.copyOf(blocks);
	}

	/** Source-compatible constructor for OCR recognition and Phase 1 bbox mapping. */
	public OcrPageResult(int pageNumber, int imageWidth, int imageHeight, String text,
			float confidence, List<OcrWord> words, long durationMs, String warning, String language) {
		this(pageNumber, imageWidth, imageHeight, text, confidence, words, durationMs, warning, language,
				List.of(), List.of());
	}
	public boolean successful() {
		return text != null && !text.isBlank();
	}

	public static OcrPageResult unavailable(int pageNumber, String warning, long durationMs) {
		return new OcrPageResult(pageNumber, 0, 0, "", 0, List.of(), durationMs, warning, null,
				List.of(), List.of());
	}
}
