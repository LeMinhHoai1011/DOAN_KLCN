package com.example.invoice.dto.invoice;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record InvoiceResponse(
		Long id,
		Long documentId,
		String invoiceNumber,
		LocalDate invoiceDate,
		String sellerName,
		String sellerTaxCode,
		String sellerAddress,
		String buyerName,
		String buyerTaxCode,
		String buyerAddress,
		BigDecimal subtotal,
		BigDecimal vatAmount,
		BigDecimal totalAmount,
		List<InvoiceItemResponse> items) {
}
