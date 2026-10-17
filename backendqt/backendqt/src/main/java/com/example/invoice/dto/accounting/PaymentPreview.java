package com.example.invoice.dto.accounting;
import java.math.BigDecimal;
public record PaymentPreview(PaymentLinkOption link,PaymentSummary summary,BigDecimal availableAmount,
 boolean canCreate,String blockedCode,String userMessage,String directionSource,String suggestedDescription,String suggestedCategoryType) {}
