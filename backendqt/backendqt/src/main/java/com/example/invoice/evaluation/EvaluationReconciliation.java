package com.example.invoice.evaluation;

import com.example.invoice.ai.InvoiceNumberFormat;
import com.example.invoice.ai.InvoiceReconciliationRules;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Applies the production VAT checks to benchmark values, so scoring needs no business rows. */
final class EvaluationReconciliation {
    private EvaluationReconciliation() {}

    /** Check type to status; a value that cannot be read as a number counts as missing, never as zero. */
    static Map<String,String> statuses(Map<String,String> fields, List<Map<String,String>> items) {
        Map<String,String> header = fields == null ? Map.of() : fields;
        var rows = (items == null ? List.<Map<String,String>>of() : items).stream().filter(java.util.Objects::nonNull)
                .map(row -> new InvoiceReconciliationRules.Row(amount(row.get("quantity")), amount(row.get("unitPrice")),
                        rate(row.get("taxRate")), amount(row.get("taxAmount")), amount(row.get("amount")))).toList();
        var statuses = new LinkedHashMap<String,String>();
        for (var check : InvoiceReconciliationRules.evaluate(rows, amount(header.get("subtotal")),
                amount(header.get("vatAmount")), amount(header.get("totalAmount"))))
            statuses.put(check.type(), InvoiceReconciliationRules.status(check.declared(), check.calculated(), check.missing()));
        return statuses;
    }

    private static BigDecimal amount(String value) {
        if (value == null || value.isBlank()) return null;
        try { return InvoiceNumberFormat.parse(value); }
        catch (IllegalArgumentException unreadable) { return null; }
    }

    /** "1%" must stay one percent: the rules read a bare 1 as the fraction 100%. */
    private static BigDecimal rate(String value) {
        if (value == null || !value.strip().endsWith("%")) return amount(value);
        BigDecimal percent = amount(value.strip().replaceAll("%$", ""));
        return percent == null ? null : percent.movePointLeft(2);
    }
}
