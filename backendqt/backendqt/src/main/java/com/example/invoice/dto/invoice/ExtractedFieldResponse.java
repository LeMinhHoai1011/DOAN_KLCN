package com.example.invoice.dto.invoice;

import java.math.BigDecimal;
import java.util.List;

/** Read-only dynamic field data associated with an invoice-backed document. */
public record ExtractedFieldResponse(
		Long id,
		String fieldName,
		String fieldValue,
		String source,
		BigDecimal confidence,
		List<FieldLocation> locations) {
	public record FieldLocation(int page, int x, int y, int width, int height,
			int pageWidth, int pageHeight, BigDecimal matchConfidence) {}
}
