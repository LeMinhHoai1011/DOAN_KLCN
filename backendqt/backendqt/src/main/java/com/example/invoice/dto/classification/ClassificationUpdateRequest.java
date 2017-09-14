package com.example.invoice.dto.classification;

import com.example.invoice.entity.ClassificationStatus;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

public record ClassificationUpdateRequest(
		String category,
		@PositiveOrZero BigDecimal confidence,
		String reason,
		ClassificationStatus status,
		Boolean aiGenerated) {
}
