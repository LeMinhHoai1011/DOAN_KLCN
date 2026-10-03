package com.example.invoice.service;

import java.util.List;

public record OcrPageResult(int pageNumber, int imageWidth, int imageHeight, String text,
		float confidence, List<OcrWord> words, long durationMs, String warning, String language) {
	public boolean successful() {
		return text != null && !text.isBlank();
	}

	public static OcrPageResult unavailable(int pageNumber, String warning, long durationMs) {
		return new OcrPageResult(pageNumber, 0, 0, "", 0, List.of(), durationMs, warning, null);
	}
}
