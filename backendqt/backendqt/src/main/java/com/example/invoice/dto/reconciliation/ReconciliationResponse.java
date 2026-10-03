package com.example.invoice.dto.reconciliation;
import java.math.BigDecimal;
public record ReconciliationResponse(Long id, String checkType, BigDecimal expectedAmount, BigDecimal actualAmount, BigDecimal differenceAmount, String status, String comment) {}
