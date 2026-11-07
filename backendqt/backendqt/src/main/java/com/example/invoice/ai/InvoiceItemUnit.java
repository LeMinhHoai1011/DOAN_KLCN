package com.example.invoice.ai;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Repairs a column shift from OCR rows like "Kính 10 ly m2 12,63 238.182 3.008.239": the unit is glued to the
 * product name, so the model returns a neighbouring number as the unit. Real units always contain a letter.
 */
public final class InvoiceItemUnit {
    private InvoiceItemUnit() {}

    private static final Pattern NUMBER_ONLY = Pattern.compile("[\\s\\d.,]+");
    private static final Pattern NAME_ENDING_IN_UNIT = Pattern.compile("(.*\\S)\\s+(\\p{L}\\S{0,11})", Pattern.DOTALL);

    public record NameAndUnit(String productName, String unit) {}

    public static NameAndUnit repair(String productName, String unit) {
        if (unit == null || !NUMBER_ONLY.matcher(unit).matches()) return new NameAndUnit(productName, unit);
        Matcher trailingUnit = NAME_ENDING_IN_UNIT.matcher(productName.strip());
        return trailingUnit.matches()
                ? new NameAndUnit(trailingUnit.group(1), trailingUnit.group(2))
                : new NameAndUnit(productName, null);
    }
}
