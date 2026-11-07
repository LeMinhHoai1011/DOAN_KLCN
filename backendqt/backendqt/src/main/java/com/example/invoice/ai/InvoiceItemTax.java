package com.example.invoice.ai;

import com.example.invoice.dto.ai.AiDocumentResult.AiInvoiceExtraction;
import com.example.invoice.dto.ai.AiDocumentResult.AiInvoiceItemExtraction;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * Many invoices print tax only as one total, so the row taxes the model returns are copies or guesses. When they do
 * not add up to the printed total, a row's tax is derived as amount × rate; that total stays what it is reconciled against.
 */
public final class InvoiceItemTax {
    private InvoiceItemTax() {}

    public static boolean rowTaxUnreliable(AiInvoiceExtraction invoice) {
        if (invoice == null || invoice.items() == null || invoice.vatAmount() == null
                || invoice.vatAmount().signum() == 0) return false;
        List<AiInvoiceItemExtraction> rows = rows(invoice);
        if (rows.isEmpty() || rows.stream().anyMatch(row -> row.taxAmount() == null)) return false;
        BigDecimal sum = rows.stream().map(AiInvoiceItemExtraction::taxAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        // Each row may round by up to half a dong, so the allowance grows with the row count.
        return sum.subtract(invoice.vatAmount()).abs().compareTo(BigDecimal.valueOf(rows.size())) >= 0;
    }

    /** A row without a rate takes the invoice's rate, but only when the other rows agree on a single one. */
    public static BigDecimal rowRate(AiInvoiceExtraction invoice, AiInvoiceItemExtraction row, boolean unreliable) {
        if (row.taxRate() != null || !unreliable) return row.taxRate();
        List<BigDecimal> rates = rows(invoice).stream().map(AiInvoiceItemExtraction::taxRate).filter(Objects::nonNull)
                .map(BigDecimal::stripTrailingZeros).distinct().toList();
        return rates.size() == 1 ? rates.getFirst() : null;
    }

    public static BigDecimal rowTax(AiInvoiceItemExtraction row, BigDecimal rate, boolean unreliable) {
        return unreliable || row.taxAmount() == null
                ? InvoiceReconciliationRules.lineTax(row.amount(), rate)
                : row.taxAmount();
    }

    private static List<AiInvoiceItemExtraction> rows(AiInvoiceExtraction invoice) {
        return invoice.items().stream().filter(Objects::nonNull).toList();
    }
}
