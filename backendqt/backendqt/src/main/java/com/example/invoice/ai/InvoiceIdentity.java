package com.example.invoice.ai;

import java.text.Normalizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The model tends to shorten printed numbers (00001011 becomes 0001011), so the stored number is taken back from the
 * source text, and two invoices are compared without leading zeros.
 */
public final class InvoiceIdentity {
    private InvoiceIdentity() {}

    private static final Pattern DIGITS = Pattern.compile("\\d+");
    private static final Pattern SERIES = Pattern.compile("(?iu:ký\\s*hiệu)[^:\\r\\n]{0,24}:\\s*([0-9]?[A-Z][0-9A-Z]{3,9})(?![0-9A-Za-z])");

    /** Only adds leading zeros that follow a "Số"/"No" label; any other printed number is left alone. */
    public static String restoreNumber(String extracted, String sourceText) {
        if (extracted == null || sourceText == null || !DIGITS.matcher(extracted).matches()) return extracted;
        String significant = withoutLeadingZeros(extracted);
        Matcher match = Pattern.compile("(?iu)(?<![\\p{L}\\d])(?:số|so|no)(?![\\p{L}\\d])[^\\d\\r\\n]{0,24}?(0*"
                + significant + ")(?!\\d)").matcher(Normalizer.normalize(sourceText, Normalizer.Form.NFC));
        String restored = extracted;
        while (match.find()) if (match.group(1).length() > restored.length()) restored = match.group(1);
        return restored;
    }

    /** The model often files the series under an extra field, so a blank one is read from the printed label. */
    public static String restoreSeries(String extracted, String sourceText) {
        if (extracted != null && !extracted.isBlank() || sourceText == null) return extracted;
        Matcher match = SERIES.matcher(Normalizer.normalize(sourceText, Normalizer.Form.NFC));
        return match.find() ? match.group(1) : extracted;
    }

    public static boolean sameInvoice(String number, String series, String otherNumber, String otherSeries) {
        if (number == null || otherNumber == null || number.isBlank() || otherNumber.isBlank()) return false;
        if (!withoutLeadingZeros(number).equalsIgnoreCase(withoutLeadingZeros(otherNumber))) return false;
        // A series the model missed must not hide a duplicate.
        return series == null || otherSeries == null || series.isBlank() || otherSeries.isBlank()
                || series.trim().equalsIgnoreCase(otherSeries.trim());
    }

    private static String withoutLeadingZeros(String number) {
        return number.trim().replaceFirst("^0+(?=.)", "");
    }
}
