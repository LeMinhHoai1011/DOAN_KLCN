package com.example.invoice.dto.accounting;
import java.math.BigDecimal;
public record PaymentLinkOption(Long documentId,Long invoiceId,String documentName,String invoiceNumber,
 BigDecimal totalAmount,String suggestedTransactionType,Long suggestedCategoryId,String currency) {}
