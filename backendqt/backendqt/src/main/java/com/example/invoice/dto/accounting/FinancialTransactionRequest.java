package com.example.invoice.dto.accounting;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import java.time.LocalDate;

public record FinancialTransactionRequest(
		@NotBlank @Pattern(regexp = "INCOME|EXPENSE", message = "Loại giao dịch phải là INCOME hoặc EXPENSE") String transactionType,
		@NotNull @DecimalMin(value = "0.01") BigDecimal amount,
		@NotNull LocalDate transactionDate,
		String description,
		Long categoryId,
		Long documentId,
		Long invoiceId,
		String paymentMethod,
		Long companyId) {
}
