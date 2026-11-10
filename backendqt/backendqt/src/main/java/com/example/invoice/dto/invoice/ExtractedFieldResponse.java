package com.example.invoice.dto.invoice;

import java.math.BigDecimal;
import java.util.List;

/**
 * Effective extracted-field value plus immutable AI data and latest correction metadata.
 * {@code fieldName} is the stable technical key; {@code displayName} the Vietnamese name to show
 * ({@code displayNameSource}: CATALOG, AI_ORIGINAL or UNRESOLVED when the original name cannot be recovered).
 */
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
		List<FieldLocation> locations,
		String displayName,
		String displayNameSource) {
	public static final String NAME_CATALOG = "CATALOG", NAME_AI_ORIGINAL = "AI_ORIGINAL", NAME_UNRESOLVED = "UNRESOLVED";

	public ExtractedFieldResponse(Long id, String fieldName, String fieldValue, String source, BigDecimal confidence,
			String originalAiValue, String correctedValue, boolean manuallyCorrected, Long correctedById,
			java.time.LocalDateTime correctedAt, List<FieldLocation> locations) {
		this(id, fieldName, fieldValue, source, confidence, originalAiValue, correctedValue, manuallyCorrected,
				correctedById, correctedAt, locations, null, null);
	}

	/** {@code source}: AI (found by matching the AI value in the OCR layout) or MANUAL_REGION (drawn by an accountant). */
	public record FieldLocation(int page, int x, int y, int width, int height,
			int pageWidth, int pageHeight, BigDecimal matchConfidence, String source) {
		public static final String SOURCE_AI = "AI", SOURCE_MANUAL_REGION = "MANUAL_REGION";

		public FieldLocation(int page, int x, int y, int width, int height, int pageWidth, int pageHeight,
				BigDecimal matchConfidence) {
			this(page, x, y, width, height, pageWidth, pageHeight, matchConfidence, SOURCE_AI);
		}
	}
}
