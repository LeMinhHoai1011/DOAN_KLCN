package com.example.invoice.dto.invoice;

import com.example.invoice.dto.document.RegionDtos.RegionProvenance;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** {@code region} is set when the value was picked from a rectangle on the preview (MANUAL_REGION). */
public record ExtractedFieldCorrectionRequest(
		@NotBlank @Size(max = 10000) String fieldValue,
		@Valid RegionProvenance region) {
	public ExtractedFieldCorrectionRequest(String fieldValue) {
		this(fieldValue, null);
	}
}
