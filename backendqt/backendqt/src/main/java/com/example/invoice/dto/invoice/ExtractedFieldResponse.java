package com.example.invoice.dto.invoice;

import java.math.BigDecimal;

/** Read-only dynamic field data associated with an invoice-backed document. */
public record ExtractedFieldResponse(
		Long id,
		String fieldName,
		String fieldValue,
		String source,
		BigDecimal confidence) {
}
