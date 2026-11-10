package com.example.invoice.dto.document;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Request/response shapes for picking a field value from a rectangle on the document preview. */
public final class RegionDtos {
	private RegionDtos() {}

	/** Rectangle in pixels of the original OCR page (same space as ocr_results.layout_json). */
	public record RegionRect(
			@NotNull @Min(1) @Max(1000) Integer page,
			@NotNull @PositiveOrZero Double x,
			@NotNull @PositiveOrZero Double y,
			@NotNull @Positive Double width,
			@NotNull @Positive Double height) {}

	public record RegionWord(String id, String text, Double confidence,
			double x, double y, double width, double height) {}

	public record RegionTextResponse(int page, double pageWidth, double pageHeight,
			String text, List<String> lines, List<RegionWord> words) {}

	/** Provenance sent with a correction or after an invoice save. */
	public record RegionProvenance(
			@NotNull @Valid RegionRect region,
			@Size(max = 10000) String extractedText) {}

	/**
	 * {@code fieldName}: an invoice column taken from a region (recorded after the invoice save).
	 * {@code fieldId}: an AI field whose location is set by hand without changing its value.
	 */
	public record RegionSelectionRequest(
			@Size(max = 100) String fieldName,
			@NotNull @Valid RegionRect region,
			@Size(max = 10000) String extractedText,
			@NotBlank @Size(max = 10000) String finalValue,
			Long fieldId) {
		public RegionSelectionRequest(String fieldName, RegionRect region, String extractedText, String finalValue) {
			this(fieldName, region, extractedText, finalValue, null);
		}
	}

	public record RegionSelectionBatchRequest(
			@NotEmpty @Size(max = 30) List<@Valid @NotNull RegionSelectionRequest> selections) {}

	public record RegionSelectionResponse(Long id, String fieldName, int page, double x, double y,
			double width, double height, String extractedText, String finalValue, String source) {}
}
