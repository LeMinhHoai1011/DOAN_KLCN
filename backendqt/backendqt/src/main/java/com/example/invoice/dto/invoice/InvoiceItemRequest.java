package com.example.invoice.dto.invoice;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

public record InvoiceItemRequest(
		@NotBlank String productName,
		@PositiveOrZero BigDecimal quantity,
		String unit,
		@PositiveOrZero BigDecimal unitPrice,
		@PositiveOrZero BigDecimal taxRate,
		@PositiveOrZero BigDecimal taxAmount,
		@PositiveOrZero BigDecimal amount) {
	public InvoiceItemRequest(String productName, BigDecimal quantity, BigDecimal unitPrice, BigDecimal amount) {
		this(productName, quantity, null, unitPrice, null, null, amount);
	}
}
