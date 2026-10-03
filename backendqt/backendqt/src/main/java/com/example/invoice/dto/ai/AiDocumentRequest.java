package com.example.invoice.dto.ai;

import java.util.List;

/** Provider-neutral request created from a document stored in MinIO. */
public record AiDocumentRequest(
		String fileName,
		String contentType,
		byte[] imageBytes,
		List<String> allowedDocumentTypes,
		String prompt) {
}
