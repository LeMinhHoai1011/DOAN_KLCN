package com.example.invoice.dto.invoice;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record InvoiceRequest(
		@NotNull Long documentId,
		@Size(max = 255) String invoiceNumber,
		LocalDate invoiceDate,
		String sellerName,
		@Size(max = 255) String sellerTaxCode,
		String sellerAddress,
		String buyerName,
		@Size(max = 255) String buyerTaxCode,
		String buyerAddress,
		@PositiveOrZero BigDecimal subtotal,
		@PositiveOrZero BigDecimal vatAmount,
		@PositiveOrZero BigDecimal totalAmount,
		@Valid List<InvoiceItemRequest> items) {
}
