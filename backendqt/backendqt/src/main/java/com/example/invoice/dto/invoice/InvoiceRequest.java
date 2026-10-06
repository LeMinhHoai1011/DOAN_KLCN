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
		@Size(max = 255) String invoiceSeries,
		LocalDate invoiceDate,
		String sellerName,
		@Size(max = 255) String sellerTaxCode,
		String sellerAddress,
		@Size(max = 100) String sellerPhone,
		String buyerName,
		@Size(max = 255) String buyerTaxCode,
		String buyerAddress,
		@PositiveOrZero BigDecimal subtotal,
		@PositiveOrZero BigDecimal vatAmount,
		@PositiveOrZero BigDecimal totalAmount,
		@Size(max = 255) String paymentMethod,
		String amountInWords,
		@Size(max = 255) String taxAuthorityCode,
		LocalDate signDate,
		@Valid List<InvoiceItemRequest> items) {
	public InvoiceRequest(Long documentId, String invoiceNumber, LocalDate invoiceDate, String sellerName,
			String sellerTaxCode, String sellerAddress, String buyerName, String buyerTaxCode, String buyerAddress,
			BigDecimal subtotal, BigDecimal vatAmount, BigDecimal totalAmount, List<InvoiceItemRequest> items) {
		this(documentId, invoiceNumber, null, invoiceDate, sellerName, sellerTaxCode, sellerAddress, null,
				buyerName, buyerTaxCode, buyerAddress, subtotal, vatAmount, totalAmount,
				null, null, null, null, items);
	}
}
