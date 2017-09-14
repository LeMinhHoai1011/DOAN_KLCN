package com.example.invoice.dto.invoice;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record InvoiceRequest(
		@NotNull Long documentId,
		String invoiceNumber,
		LocalDate invoiceDate,
		String sellerName,
		String sellerTaxCode,
		String sellerAddress,
		String buyerName,
		String buyerTaxCode,
		String buyerAddress,
		@PositiveOrZero BigDecimal subtotal,
		@PositiveOrZero BigDecimal vatAmount,
		@PositiveOrZero BigDecimal totalAmount,
		@Valid List<InvoiceItemRequest> items) {
}
