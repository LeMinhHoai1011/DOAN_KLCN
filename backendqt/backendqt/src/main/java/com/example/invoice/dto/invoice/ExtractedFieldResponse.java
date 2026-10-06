package com.example.invoice.dto.invoice;

import java.math.BigDecimal;
import java.util.List;

/** Effective extracted-field value plus immutable AI data and latest correction metadata. */
public record ExtractedFieldResponse(
		Long id,
		String fieldName,
		String fieldValue,
		String source,
		BigDecimal confidence,
		String originalAiValue,
		String correctedValue,
		boolean manuallyCorrected,
		Long correctedById,
		java.time.LocalDateTime correctedAt,
		List<FieldLocation> locations) {
	public record FieldLocation(int page, int x, int y, int width, int height,
			int pageWidth, int pageHeight, BigDecimal matchConfidence) {}
}
