package com.example.invoice.dto.accounting;
import java.math.BigDecimal;
public record PaymentSummary(Long documentId,Long invoiceId,BigDecimal totalAmount,BigDecimal confirmedPaid,
 BigDecimal remaining,String paymentStatus,String verifiedPaymentStatus,long unverifiedCount,String currency,String comparison) {}
