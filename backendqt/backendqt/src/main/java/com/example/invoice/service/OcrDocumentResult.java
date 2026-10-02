package com.example.invoice.service;

import java.util.List;

public record OcrDocumentResult(String rawText, float confidence, List<OcrPageResult> pages,
		long durationMs, List<String> warnings) {
	public boolean successful() { return rawText != null && !rawText.isBlank(); }
}
