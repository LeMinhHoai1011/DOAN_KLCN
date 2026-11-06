package com.example.invoice.dto.reconciliation;
import java.math.BigDecimal;
public record ReconciliationResponse(Long id, String checkType, BigDecimal expectedAmount, BigDecimal actualAmount, BigDecimal differenceAmount, String status, String comment, Long batchId, java.time.LocalDateTime reviewedAt) {
    public ReconciliationResponse(Long id, String checkType, BigDecimal expectedAmount, BigDecimal actualAmount, BigDecimal differenceAmount, String status, String comment) {
        this(id, checkType, expectedAmount, actualAmount, differenceAmount, status, comment, null, null);
    }
}
