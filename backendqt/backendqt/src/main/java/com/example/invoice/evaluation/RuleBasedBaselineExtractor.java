package com.example.invoice.evaluation;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Strategy R: the "traditional" baseline. OCR text + regular expressions + keyword dictionaries only;
 * no LLM, no vision model, no learned weights. Used as the control group against A/B/C.
 * Matching runs on accent-folded upper-case text because Tesseract often drops Vietnamese diacritics.
 */
public final class RuleBasedBaselineExtractor {
    public static final String VERSION = "rule-baseline-v1";

    /** Ordered: first matching rule wins, so more specific titles come first. */
    public static final Map<String, List<String>> DEFAULT_TYPE_KEYWORDS = linked(
            "PAYMENT_VOUCHER", List.of("PHIEU CHI"),
            "RECEIPT_VOUCHER", List.of("PHIEU THU"),
            "CONTRACT", List.of("HOP DONG"),
            "VAT_INVOICE", List.of("GIA TRI GIA TANG", "VAT INVOICE", "HOA DON GTGT"),
            "RETAIL_INVOICE", List.of("HOA DON BAN LE", "HOA DON BAN HANG", "PHIEU THANH TOAN", "TAM TINH"),
            "RECEIPT", List.of("BIEN LAI", "RECEIPT"));

    public record Prediction(Map<String, String> fields, String predictedType, String predictedCategory) {}

    private final Map<String, List<String>> typeKeywords;
    private final Map<String, List<String>> categoryKeywords;

    public RuleBasedBaselineExtractor(Map<String, List<String>> typeKeywords, Map<String, List<String>> categoryKeywords) {
        this.typeKeywords = fold(typeKeywords == null || typeKeywords.isEmpty() ? DEFAULT_TYPE_KEYWORDS : typeKeywords);
        this.categoryKeywords = fold(categoryKeywords == null ? Map.of() : categoryKeywords);
    }

    public Prediction extract(String ocrText) {
        String text = fold(ocrText == null ? "" : ocrText);
        var fields = new TreeMap<String, String>();
        put(fields, "invoiceNumber", first(text, "\\b(?:SO HOA DON|SO HD|INVOICE NO|SO|NO)\\.?\\s*(?:\\(NO\\.?\\))?\\s*[:.]\\s*([0-9]{1,8})\\b"));
        put(fields, "invoiceSeries", first(text, "KY HIEU\\s*(?:\\(SERI(?:AL)?\\))?\\s*[:.]?\\s*([0-9A-Z]{1,2}[A-Z0-9]{4,6})\\b"));
        put(fields, "invoiceDate", date(text));
        var taxCodes = taxCodes(text);
        put(fields, "sellerTaxCode", taxCodes[0]);
        put(fields, "buyerTaxCode", taxCodes[1]);
        put(fields, "subtotal", amount(text, "CONG TIEN HANG", "THANH TIEN TRUOC THUE", "TIEN HANG"));
        put(fields, "vatAmount", amount(text, "TIEN THUE GTGT", "TIEN THUE", "THUE GTGT"));
        put(fields, "totalAmount", amount(text, "TONG CONG TIEN THANH TOAN", "TONG TIEN THANH TOAN", "TONG THANH TOAN", "TONG CONG", "TONG TIEN", "TOTAL", "THANH TOAN"));
        return new Prediction(fields, match(text, typeKeywords), match(text, categoryKeywords));
    }

    private static final Pattern TAX_CODE = Pattern.compile("(?:MA SO THUE|MST|TAX CODE)\\s*(?:\\([^)]{0,20}\\))?\\s*[:.]?\\s*([0-9][0-9 ]{8,13}[0-9](?:\\s*-\\s*[0-9]{3})?)");
    private static final Pattern BUYER = Pattern.compile("NGUOI MUA|BUYER|DON VI MUA|TEN DON VI MUA");

    /** First MST before the buyer section is the seller; the first one after it is the buyer. */
    private static String[] taxCodes(String text) {
        Matcher buyer = BUYER.matcher(text);
        int buyerAt = buyer.find() ? buyer.start() : Integer.MAX_VALUE;
        String seller = null, buyerCode = null;
        Matcher m = TAX_CODE.matcher(text);
        while (m.find()) {
            String code = m.group(1).replaceAll("\\s+", "");
            String digits = code.replace("-", "");
            if (digits.length() != 10 && digits.length() != 13) continue;
            if (m.start() < buyerAt) { if (seller == null) seller = code; }
            else if (buyerCode == null) buyerCode = code;
        }
        return new String[] { seller, buyerCode };
    }

    private static final Pattern WORD_DATE = Pattern.compile("NGAY\\s*(?:\\(DATE\\))?\\s*([0-9]{1,2})\\s*THANG\\s*(?:\\(MONTH\\))?\\s*([0-9]{1,2})\\s*NAM\\s*(?:\\(YEAR\\))?\\s*([0-9]{4})");
    private static final Pattern NUMERIC_DATE = Pattern.compile("\\b([0-9]{1,2})[/.-]([0-9]{1,2})[/.-]([0-9]{4})\\b");

    private static String date(String text) {
        for (Pattern pattern : List.of(WORD_DATE, NUMERIC_DATE)) {
            Matcher m = pattern.matcher(text);
            while (m.find()) {
                try {
                    return LocalDate.of(Integer.parseInt(m.group(3)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(1))).toString();
                } catch (RuntimeException ignored) { /* invalid calendar date: keep scanning */ }
            }
        }
        return null;
    }

    /**
     * Labels in priority order; takes the first amount shortly after a label, as a plain integer.
     * A short window instead of "rest of line" because Tess4J output may arrive without line breaks.
     */
    private static String amount(String text, String... labels) {
        for (String label : labels) {
            Matcher m = Pattern.compile("\\b" + label + "\\b[^0-9\\n]{0,40}([0-9]{1,3}(?:[.,][0-9]{3})+|[0-9]{4,})").matcher(text);
            if (m.find()) return new BigDecimal(m.group(1).replaceAll("[.,]", "")).toPlainString();
        }
        return null;
    }

    private static String first(String text, String regex) {
        Matcher m = Pattern.compile(regex).matcher(text);
        return m.find() ? m.group(1) : null;
    }

    private static String match(String text, Map<String, List<String>> rules) {
        for (var rule : rules.entrySet())
            for (String keyword : rule.getValue()) if (!keyword.isBlank() && text.contains(keyword)) return rule.getKey();
        return null;
    }

    private static void put(Map<String, String> fields, String key, String value) { if (value != null) fields.put(key, value); }

    static String fold(String value) {
        String noMarks = Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return noMarks.replace('đ', 'd').replace('Đ', 'D').toUpperCase(Locale.ROOT).replaceAll("[ \\t]+", " ");
    }

    private static Map<String, List<String>> fold(Map<String, List<String>> rules) {
        var folded = new LinkedHashMap<String, List<String>>();
        rules.forEach((key, keywords) -> folded.put(key, keywords.stream().map(RuleBasedBaselineExtractor::fold).map(String::trim).toList()));
        return Collections.unmodifiableMap(folded);
    }

    private static Map<String, List<String>> linked(Object... pairs) {
        var map = new LinkedHashMap<String, List<String>>();
        for (int i = 0; i < pairs.length; i += 2) {
            @SuppressWarnings("unchecked") List<String> keywords = (List<String>) pairs[i + 1];
            map.put((String) pairs[i], keywords);
        }
        return Collections.unmodifiableMap(map);
    }
}
