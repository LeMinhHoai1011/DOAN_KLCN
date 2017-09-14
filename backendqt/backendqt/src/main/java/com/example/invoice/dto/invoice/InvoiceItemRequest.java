package com.example.invoice.dto.invoice;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

public record InvoiceItemRequest(
		@NotBlank String productName,
		@PositiveOrZero BigDecimal quantity,
		@PositiveOrZero BigDecimal unitPrice,
		@PositiveOrZero BigDecimal amount) {
}
