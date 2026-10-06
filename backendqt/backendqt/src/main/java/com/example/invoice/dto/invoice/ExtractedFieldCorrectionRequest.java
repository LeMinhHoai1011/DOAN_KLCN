package com.example.invoice.dto.invoice;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ExtractedFieldCorrectionRequest(
		@NotBlank @Size(max = 10000) String fieldValue) {
}
