package com.example.invoice.dto.document;

import com.example.invoice.entity.DocumentStatus;
import com.example.invoice.entity.ReviewStatus;

public record DocumentUploadResponse(boolean success, Long documentId, DocumentStatus processingStatus,
		ReviewStatus reviewStatus, String message, UploadError error) {
	public record UploadError(String code, String message, boolean retryable) {}
}
