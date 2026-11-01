package com.example.invoice.ai;

import java.math.BigDecimal;

/** Vietnamese invoice strings: grouping dot, decimal comma. Numeric JSON is never rewritten. */
public final class InvoiceNumberFormat {
    private InvoiceNumberFormat() {}

    public static BigDecimal parse(String raw) {
        String value = raw == null ? "" : raw.trim().replace("\u00a0", "").replace(" ", "");
        if (value.matches("[+-]?\\d{1,3}(?:\\.\\d{3})+,\\d+"))
            value = value.replace(".", "").replace(',', '.');
        else if (value.matches("[+-]?\\d{1,3}(?:,\\d{3})+\\.\\d+"))
            value = value.replace(",", "");
        else if (value.matches("[+-]?\\d+,\\d+"))
            value = value.replace(',', '.');
        else if (value.matches("[+-]?\\d{1,3}(?:\\.\\d{3})+"))
            value = value.replace(".", "");
        else if (!value.matches("[+-]?\\d+(?:\\.\\d+)?"))
            throw new IllegalArgumentException("Invalid invoice number format");
        return new BigDecimal(value);
    }
}
