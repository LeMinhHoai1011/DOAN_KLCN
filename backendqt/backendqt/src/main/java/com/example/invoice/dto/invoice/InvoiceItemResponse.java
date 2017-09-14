package com.example.invoice.dto.invoice;

import java.math.BigDecimal;

public record InvoiceItemResponse(
		Long id,
		String productName,
		BigDecimal quantity,
		BigDecimal unitPrice,
		BigDecimal amount) {
}
