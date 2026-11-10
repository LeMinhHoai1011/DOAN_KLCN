package com.example.invoice.dto.invoice;

import java.math.BigDecimal;

/**
 * {@code discountAmount} is the printed row discount ("Tiền chiết khấu") from the latest AI run. The invoice has no
 * discount column, so it comes from the field report and is null when the row was changed since that run.
 */
public record InvoiceItemResponse(
		Long id,
		String productName,
		BigDecimal quantity,
		String unit,
		BigDecimal unitPrice,
		BigDecimal taxRate,
		BigDecimal taxAmount,
		BigDecimal amount,
		BigDecimal discountAmount) {

	public InvoiceItemResponse(Long id, String productName, BigDecimal quantity, String unit, BigDecimal unitPrice,
			BigDecimal taxRate, BigDecimal taxAmount, BigDecimal amount) {
		this(id, productName, quantity, unit, unitPrice, taxRate, taxAmount, amount, null);
	}
}
