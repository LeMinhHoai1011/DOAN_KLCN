package com.example.invoice.dto.invoice;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

public record InvoiceItemRequest(
		@NotBlank String productName,
		@PositiveOrZero BigDecimal quantity,
		String unit,
		BigDecimal unitPrice, // Money may be negative on discount rows.
		@PositiveOrZero BigDecimal taxRate,
		BigDecimal taxAmount,
		BigDecimal amount) {
	public InvoiceItemRequest(String productName, BigDecimal quantity, BigDecimal unitPrice, BigDecimal amount) {
		this(productName, quantity, null, unitPrice, null, null, amount);
	}
}
