package com.example.invoice.dto.invoice;

import java.math.BigDecimal;

public record InvoiceItemResponse(
		Long id,
		String productName,
		BigDecimal quantity,
		String unit,
		BigDecimal unitPrice,
		BigDecimal taxRate,
		BigDecimal taxAmount,
		BigDecimal amount) {
}
