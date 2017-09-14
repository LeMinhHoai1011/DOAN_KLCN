package com.example.invoice.dto.document;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

public record OCRResultRequest(
		@NotBlank String rawText,
		@PositiveOrZero BigDecimal confidence) {
}
