package com.example.invoice.dto.invoice;

import java.math.BigDecimal;
import java.util.List;

/** Printed evidence, not payment confirmation. Missing values remain null. */
public record RetailInvoiceDetails(String transactionTime, LineAmountBasis lineAmountBasis,
        List<LineDiscount> lineDiscounts, Adjustment billDiscount, Adjustment tax, Adjustment fees,
        BigDecimal tenderedAmount, BigDecimal changeAmount) {
    public enum LineAmountBasis { BEFORE_DISCOUNT, AFTER_DISCOUNT, UNKNOWN }
    public enum Treatment { INCLUDED, SEPARATE, NOT_PRESENT, UNKNOWN }
    public record Adjustment(BigDecimal amount, Treatment treatment) {}
    /** One-based printed row number; omitted rows in a known list have no printed line discount. */
    public record LineDiscount(int rowNumber, BigDecimal amount) {}
}
