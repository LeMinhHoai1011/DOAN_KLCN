package com.example.invoice.dto.document;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record OCRResultResponse(
		Long id,
		Long documentId,
		String rawText,
		BigDecimal confidence,
		LocalDateTime processedAt) {
}
