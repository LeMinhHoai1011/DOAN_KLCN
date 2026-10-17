package com.example.invoice.dto.document;

import com.example.invoice.entity.DocumentStatus;
import com.example.invoice.entity.ReviewStatus;

public record DocumentUploadResponse(boolean success, Long documentId, DocumentStatus processingStatus,
		ReviewStatus reviewStatus, String documentTypeCode, String documentTypeName,
		java.math.BigDecimal documentTypeConfidence, String message, UploadError error) {
	public record UploadError(String code, String message, boolean retryable, String failedStage, int attemptCount, java.time.LocalDateTime timestamp) {
        public UploadError(String code, String message, boolean retryable) { this(code, message, retryable, "UNKNOWN", 0, java.time.LocalDateTime.now()); }
        public UploadError(PipelineFailure failure) { this(failure.errorCode(), failure.userMessage(), failure.retryable(), failure.failedStage(), failure.attemptCount(), failure.timestamp()); }
        @com.fasterxml.jackson.annotation.JsonProperty("errorCode") public String errorCode() { return code; }
        @com.fasterxml.jackson.annotation.JsonProperty("userMessage") public String userMessage() { return message; }
    }
}
