package com.example.invoice.dto.accounting;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record FinancialTransactionResponse(
		Long id, String transactionType, BigDecimal amount, LocalDate transactionDate, String description,
		Long categoryId, String categoryName, Long companyId, Long documentId, Long invoiceId,
		String paymentMethod, String status, Long createdById, LocalDateTime createdAt, LocalDateTime updatedAt) {
}
