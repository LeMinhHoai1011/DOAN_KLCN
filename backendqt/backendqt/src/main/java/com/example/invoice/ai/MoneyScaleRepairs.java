package com.example.invoice.ai;

import com.example.invoice.dto.ai.AiDocumentResult;
import com.example.invoice.dto.ai.AiDocumentResult.AiFieldNote;
import com.example.invoice.dto.ai.AiDocumentResult.AiInvoiceExtraction;
import com.example.invoice.dto.ai.AiDocumentResult.AiInvoiceItemExtraction;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Money read 1000 times too small: the model takes the thousands dot of "1.305.500" for a decimal point and returns
 * the JSON number 1305.5. A JSON number never reaches {@code LocalizedNumber}, so the parser cannot see it.
 *
 * <p>VND amounts have no decimals, so a money value with a fraction is suspicious. It is scaled ×1000 only when the
 * source text the model read (OCR or the PDF text layer) prints that exact ×1000 amount; arithmetic alone is never
 * enough. The value before the change is kept as a field note. Without that evidence the value is left as returned
 * and reported for the accountant when it is an invoice total (row prices may legitimately carry decimals).
 */
public final class MoneyScaleRepairs {
    private MoneyScaleRepairs() {}

    public static final String REPAIRED = "MONEY_SCALE_REPAIRED";
    public static final String SUSPECT = "MONEY_SCALE_SUSPECT";
    public static final String NOTE_REASON = "MONEY_SCALE_X1000_PRINTED_IN_SOURCE";
    private static final BigDecimal THOUSAND = BigDecimal.valueOf(1000);

    public static AiDocumentResult apply(AiDocumentResult result, String sourceText) {
        if (result == null || result.invoice() == null || !vnd(result)) return result;
        AiInvoiceExtraction in = result.invoice();
        String digits = sourceText == null ? "" : sourceText;
        var warnings = new ArrayList<>(result.warnings() == null ? List.<String>of() : result.warnings());
        var notes = new ArrayList<>(result.notes());
        var counter = new int[1];
        BigDecimal subtotal = fix("subtotal", null, in.subtotal(), digits, warnings, notes, counter);
        BigDecimal vat = fix("vatAmount", null, in.vatAmount(), digits, warnings, notes, counter);
        BigDecimal total = fix("totalAmount", null, in.totalAmount(), digits, warnings, notes, counter);
        List<AiInvoiceItemExtraction> items = in.items();
        if (items != null) {
            var rows = new ArrayList<AiInvoiceItemExtraction>(items.size());
            for (int i = 0; i < items.size(); i++) {
                var item = items.get(i);
                if (item == null) { rows.add(null); continue; }
                rows.add(new AiInvoiceItemExtraction(item.productName(), item.quantity(), item.unit(),
                        fix("unitPrice", i + 1, item.unitPrice(), digits, warnings, notes, counter),
                        item.taxRate(),
                        fix("taxAmount", i + 1, item.taxAmount(), digits, warnings, notes, counter),
                        fix("amount", i + 1, item.amount(), digits, warnings, notes, counter)));
            }
            items = rows;
        }
        if (counter[0] == 0 && warnings.size() == (result.warnings() == null ? 0 : result.warnings().size())) return result;
        var invoice = new AiInvoiceExtraction(in.invoiceNumber(), in.invoiceSeries(), in.invoiceDate(), in.sellerName(),
                in.sellerTaxCode(), in.sellerAddress(), in.sellerPhone(), in.buyerName(), in.buyerTaxCode(), in.buyerAddress(),
                subtotal, vat, total, in.paymentMethod(), in.amountInWords(), in.taxAuthorityCode(), in.signDate(), items,
                in.retailDetails());
        return result.with(warnings, invoice, result.fields(), result.extraFields(), notes);
    }

    private static BigDecimal fix(String field, Integer row, BigDecimal value, String source, List<String> warnings,
                                  List<AiFieldNote> notes, int[] repaired) {
        if (value == null || value.signum() == 0) return value;
        BigDecimal plain = value.stripTrailingZeros();
        if (plain.scale() <= 0 || plain.scale() > 3) return value;
        BigDecimal scaled = plain.multiply(THOUSAND).stripTrailingZeros();
        if (scaled.scale() > 0) return value;
        String where = "field=" + field + (row == null ? "" : "; row=" + row);
        if (printed(source, scaled.abs().toBigInteger().toString())) {
            BigDecimal fixed = scaled.setScale(0);
            notes.add(new AiFieldNote(field, row, value.toPlainString(), fixed.toPlainString(), "NORMALIZED", NOTE_REASON));
            warnings.add(REPAIRED + ": " + where + "; " + value.toPlainString() + " -> " + fixed.toPlainString()
                    + " (số tiền in trên chứng từ)");
            repaired[0]++;
            return fixed;
        }
        // Row prices may legitimately carry decimals; only invoice totals are reported without evidence.
        if (row != null) return value;
        warnings.add(SUSPECT + ": " + where + "; " + value.toPlainString()
                + " có phần lẻ, có thể bị đọc nhỏ đi 1000 lần; cần đối chiếu chứng từ");
        return value;
    }

    /** The amount printed with or without thousand separators, not part of a longer number. */
    static boolean printed(String source, String digits) {
        if (source.isEmpty() || digits.length() < 4) return false;
        var groups = new StringBuilder();
        int head = digits.length() % 3 == 0 ? 3 : digits.length() % 3;
        groups.append(digits, 0, head);
        for (int i = head; i < digits.length(); i += 3) groups.append("[.,\\u00A0 ]?").append(digits, i, i + 3);
        return Pattern.compile("(?<![\\d.,])" + groups + "(?![\\d]|[.,]\\d)").matcher(source).find();
    }

    private static boolean vnd(AiDocumentResult result) {
        if (result.extraFields() == null) return true;
        return result.extraFields().stream()
                .filter(f -> f != null && f.name() != null && f.name().toLowerCase(Locale.ROOT).contains("currency"))
                .map(f -> f.value() == null ? "" : f.value().trim().toUpperCase(Locale.ROOT))
                .allMatch(v -> v.isEmpty() || v.equals("VND") || v.equals("VNĐ") || v.equals("Đ") || v.equals("₫"));
    }
}
