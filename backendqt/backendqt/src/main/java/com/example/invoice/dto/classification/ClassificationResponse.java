package com.example.invoice.dto.classification;

import com.example.invoice.entity.ClassificationStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ClassificationResponse(
		Long id,
		Long documentId,
		String category,
		BigDecimal confidence,
		String reason,
		ClassificationStatus status,
		boolean aiGenerated,
		LocalDateTime createdAt,
		LocalDateTime updatedAt) {
}
