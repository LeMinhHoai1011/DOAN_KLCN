package com.example.invoice.dto.ai;

import com.example.invoice.entity.DocumentStatus;
import java.util.List;

public record AiDocumentProcessingResponse(
		Long documentId,
		DocumentStatus status,
		boolean requiresReview,
		List<String> warnings) {
}
