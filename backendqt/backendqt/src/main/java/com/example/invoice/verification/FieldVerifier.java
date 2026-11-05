package com.example.invoice.verification;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.*;

/**
 * Per-field verification of extracted values against independent checks: OCR evidence, format,
 * business consistency and cross-field rules. Pure and side-effect free; it never reads the
 * model's self-reported confidence and never changes a value.
 *
 * VERIFIED only means "passed every check that could be applied", not "proven correct".
 */
public final class FieldVerifier {
    public static final String RULE_VERSION = "field-verification-v1";
    public static final String SCORE_NOTE = "Điểm heuristic 0–100 chưa hiệu chuẩn; không phải xác suất đúng.";

    public enum Status { VERIFIED, WARNING, UNVERIFIED, MISSING }
    public enum Outcome { PASS, FAIL, NOT_APPLICABLE }
    public enum Kind { TEXT, TAX_CODE, DATE, AMOUNT, QUANTITY, PERCENT, INVOICE_NUMBER, INVOICE_SERIES, CURRENCY, CODE }
    /** What the model was given: only text (OCR / PDF text layer), the image, both across pages, or unknown. */
    public enum AiInputMode { TEXT, VISION, MIXED, UNKNOWN }
    public enum Origin { AI, MANUAL }

    public record Location(int page, int x, int y, int width, int height, int pageWidth, int pageHeight) {}
    public record Evidence(Outcome outcome, double matchScore, String method, String matchedText, Location location) {
        public static Evidence notFound() { return new Evidence(Outcome.FAIL, 0d, "NOT_FOUND", null, null); }
        public static Evidence unavailable(String why) { return new Evidence(Outcome.NOT_APPLICABLE, 0d, why, null, null); }
        /** Not applicable by design (not a lack of data): the value is inferred or too short to match meaningfully. */
        boolean notApplicableByDesign() { return outcome == Outcome.NOT_APPLICABLE && BY_DESIGN.contains(method); }
    }
    public static final String NOT_EXPECTED_IN_TEXT = "NOT_EXPECTED_IN_TEXT";
    public static final String TOO_SHORT_TO_MATCH = "TOO_SHORT_TO_MATCH";
    /** Per-line VAT is usually not printed; the model often computes it, so absence is not suspicious. */
    public static final String OPTIONAL_NOT_PRINTED = "OPTIONAL_NOT_PRINTED";
    private static final Set<String> BY_DESIGN = Set.of(NOT_EXPECTED_IN_TEXT, TOO_SHORT_TO_MATCH, OPTIONAL_NOT_PRINTED);
    /** Looks a value up in the OCR/PDF text. Implementations must be deterministic. */
    public interface EvidenceFinder { Evidence find(String fieldKey, Kind kind, String value); }

    public record RuleResult(String ruleId, Outcome outcome, String message) {}
    public record FieldInput(String key, Kind kind, String value, boolean core, Origin origin) {}
    public record ItemInput(String productName, BigDecimal quantity, BigDecimal unitPrice, BigDecimal taxRate,
            BigDecimal taxAmount, BigDecimal amount, Origin origin) {}
    public record Input(String documentType, AiInputMode aiInputMode, boolean ocrAvailable,
            List<FieldInput> fields, List<ItemInput> items, LocalDate today) {}
    public record Result(String key, Integer itemIndex, boolean core, String value, Origin origin, Status status,
            Integer score, Evidence evidence, List<RuleResult> rules, List<String> reasons) {}

    /** Amount tolerance: one currency unit absorbs per-line rounding on printed invoices. */
    static final BigDecimal TOLERANCE = BigDecimal.ONE;
    static final Set<BigDecimal> VAT_RATES = Set.of(new BigDecimal("0.00"), new BigDecimal("0.05"),
            new BigDecimal("0.08"), new BigDecimal("0.10"));

    private final EvidenceFinder evidence;

    public FieldVerifier(EvidenceFinder evidence) { this.evidence = evidence; }

    public List<Result> verify(Input input) {
        var rules = new LinkedHashMap<String, List<RuleResult>>();
        var fields = new LinkedHashMap<String, FieldInput>();
        for (FieldInput field : input.fields()) { fields.put(field.key(), field); rules.put(field.key(), new ArrayList<>()); }
        List<ItemInput> items = input.items() == null ? List.of() : input.items();

        for (FieldInput field : input.fields()) if (present(field.value())) formatRules(field, input.today(), rules.get(field.key()));
        crossFieldRules(fields, items, rules);

        var results = new ArrayList<Result>();
        for (FieldInput field : input.fields()) {
            if (!present(field.value())) {
                if (field.core()) results.add(new Result(field.key(), null, true, null, field.origin(), Status.MISSING, null,
                        null, List.of(), List.of("Trường bắt buộc theo loại chứng từ nhưng không có giá trị")));
                continue;
            }
            results.add(finish(field.key(), null, field.core(), field.kind(), field.value(), field.origin(), input, rules.get(field.key())));
        }
        for (int index = 0; index < items.size(); index++) results.addAll(itemResults(index, items.get(index), input));
        return List.copyOf(results);
    }

    // ---- per-field format rules -------------------------------------------------------------

    private void formatRules(FieldInput field, LocalDate today, List<RuleResult> out) {
        String value = field.value().trim();
        switch (field.kind()) {
            case TAX_CODE -> taxCodeRules(value, out);
            case DATE -> {
                LocalDate date = parseDate(value);
                if (date == null) { out.add(fail("DATE_VALID", "Không đọc được ngày hợp lệ (yyyy-MM-dd hoặc dd/MM/yyyy)")); return; }
                out.add(pass("DATE_VALID", "Ngày hợp lệ theo lịch"));
                LocalDate now = today == null ? LocalDate.now() : today;
                out.add(date.isAfter(now.plusDays(1)) ? fail("DATE_NOT_FUTURE", "Ngày ở tương lai so với thời điểm kiểm tra")
                        : pass("DATE_NOT_FUTURE", "Không ở tương lai"));
            }
            case AMOUNT -> {
                BigDecimal amount = parseAmount(value);
                if (amount == null) out.add(fail("AMOUNT_VALID", "Không phải số tiền hợp lệ"));
                else if (amount.signum() < 0) out.add(fail("AMOUNT_VALID", "Số tiền âm"));
                else out.add(pass("AMOUNT_VALID", "Số tiền không âm"));
            }
            case INVOICE_NUMBER -> out.add(value.matches("\\d{1,8}") ? pass("INVOICE_NUMBER_FORMAT", "Gồm 1–8 chữ số")
                    : fail("INVOICE_NUMBER_FORMAT", "Số hóa đơn phải gồm 1–8 chữ số"));
            case INVOICE_SERIES -> {
                String series = value.toUpperCase(Locale.ROOT).replaceAll("\\s+", "");
                // TT78: 1 digit + C/K + 2-digit year + letter + 2 chars (e.g. 1C24TAA); legacy forms like AA/20E.
                boolean modern = series.matches("[1-6][CK]\\d{2}[A-Z][A-Z0-9]{2}");
                boolean legacy = series.matches("[A-Z]{2}/\\d{2}[A-Z]");
                out.add(modern || legacy ? pass("INVOICE_SERIES_FORMAT", modern ? "Đúng mẫu ký hiệu TT78" : "Đúng mẫu ký hiệu cũ")
                        : fail("INVOICE_SERIES_FORMAT", "Ký hiệu không theo mẫu TT78 (vd 1C24TAA) hay mẫu cũ (vd AA/20E)"));
            }
            case CURRENCY -> {
                try {
                    String code = value.toUpperCase(Locale.ROOT);
                    if (!code.matches("[A-Z]{3}")) throw new IllegalArgumentException();
                    Currency.getInstance(code);
                    out.add(pass("CURRENCY_ISO", "Mã tiền tệ ISO 4217 hợp lệ"));
                } catch (IllegalArgumentException exception) { out.add(fail("CURRENCY_ISO", "Mã tiền tệ không hợp lệ theo ISO 4217")); }
            }
            default -> { }
        }
    }

    static void taxCodeRules(String value, List<RuleResult> out) {
        String code = value.replaceAll("\\s+", "");
        if (!code.matches("\\d{10}(-\\d{3})?")) { out.add(fail("TAX_CODE_FORMAT", "MST phải gồm 10 chữ số, tùy chọn -xxx chi nhánh")); return; }
        out.add(pass("TAX_CODE_FORMAT", "Đúng định dạng 10 hoặc 10-3 chữ số"));
        out.add(taxCodeChecksumValid(code.substring(0, 10)) ? pass("TAX_CODE_CHECKSUM", "Chữ số kiểm tra khớp")
                : fail("TAX_CODE_CHECKSUM", "Chữ số kiểm tra thứ 10 không khớp; có thể đọc sai một chữ số"));
    }

    /** Vietnamese MST check digit: weights 31,29,23,19,17,13,7,5,3 on the first 9 digits; N10 = 10 − (sum mod 11). */
    static boolean taxCodeChecksumValid(String tenDigits) {
        int[] weights = { 31, 29, 23, 19, 17, 13, 7, 5, 3 };
        int sum = 0;
        for (int i = 0; i < 9; i++) sum += (tenDigits.charAt(i) - '0') * weights[i];
        int check = 10 - (sum % 11);
        return check < 10 && check == tenDigits.charAt(9) - '0';
    }

    // ---- cross-field and business consistency -----------------------------------------------

    private void crossFieldRules(Map<String, FieldInput> fields, List<ItemInput> items, Map<String, List<RuleResult>> rules) {
        BigDecimal subtotal = amount(fields, "subtotal"), vat = amount(fields, "vatAmount"), total = amount(fields, "totalAmount");
        if (subtotal != null && vat != null && total != null) {
            BigDecimal expected = subtotal.add(vat);
            RuleResult rule = close(expected, total, TOLERANCE)
                    ? pass("TOTAL_EQUALS_SUBTOTAL_PLUS_VAT", "Tiền hàng + thuế = tổng thanh toán")
                    : fail("TOTAL_EQUALS_SUBTOTAL_PLUS_VAT", "Tiền hàng + thuế = " + plain(expected) + " khác tổng " + plain(total));
            for (String key : List.of("subtotal", "vatAmount", "totalAmount")) rules.get(key).add(rule);
        }
        var amounts = items.stream().map(ItemInput::amount).filter(Objects::nonNull).toList();
        if (!items.isEmpty() && amounts.size() == items.size()) {
            BigDecimal sum = amounts.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal tolerance = BigDecimal.valueOf(Math.max(1, items.size()));
            String target = subtotal != null ? "subtotal" : total != null && vat == null ? "totalAmount" : null;
            if (target != null) {
                BigDecimal expected = amount(fields, target);
                rules.get(target).add(close(sum, expected, tolerance)
                        ? pass("ITEMS_SUM_MATCHES", "Tổng thành tiền " + items.size() + " dòng hàng khớp")
                        : fail("ITEMS_SUM_MATCHES", "Tổng thành tiền các dòng = " + plain(sum) + " khác " + plain(expected)));
            }
            var taxes = items.stream().map(ItemInput::taxAmount).filter(Objects::nonNull).toList();
            if (vat != null && taxes.size() == items.size()) {
                BigDecimal taxSum = taxes.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
                rules.get("vatAmount").add(close(taxSum, vat, tolerance)
                        ? pass("ITEMS_VAT_SUM_MATCHES", "Tổng tiền thuế các dòng khớp")
                        : fail("ITEMS_VAT_SUM_MATCHES", "Tổng tiền thuế các dòng = " + plain(taxSum) + " khác " + plain(vat)));
            }
        }
        String seller = value(fields, "sellerTaxCode"), buyer = value(fields, "buyerTaxCode");
        if (seller != null && buyer != null) {
            RuleResult rule = seller.replaceAll("\\s+", "").equals(buyer.replaceAll("\\s+", ""))
                    ? fail("SELLER_BUYER_DISTINCT", "MST người bán trùng MST người mua")
                    : pass("SELLER_BUYER_DISTINCT", "MST người bán khác người mua");
            rules.get("sellerTaxCode").add(rule); rules.get("buyerTaxCode").add(rule);
        }
        LocalDate issued = date(fields, "invoiceDate"), signed = date(fields, "signDate");
        if (issued != null && signed != null) rules.get("signDate").add(signed.isBefore(issued)
                ? fail("SIGN_NOT_BEFORE_ISSUE", "Ngày ký trước ngày lập hóa đơn")
                : pass("SIGN_NOT_BEFORE_ISSUE", "Ngày ký không trước ngày lập"));
        LocalDate effective = date(fields, "effectiveDate"), expiry = date(fields, "expiryDate");
        if (effective != null && expiry != null) rules.get("expiryDate").add(expiry.isBefore(effective)
                ? fail("EXPIRY_AFTER_EFFECTIVE", "Ngày hết hạn trước ngày hiệu lực")
                : pass("EXPIRY_AFTER_EFFECTIVE", "Ngày hết hạn sau ngày hiệu lực"));
    }

    private List<Result> itemResults(int index, ItemInput item, Input input) {
        var lineRules = new ArrayList<RuleResult>();
        if (item.quantity() != null && item.unitPrice() != null && item.amount() != null) {
            BigDecimal expected = item.quantity().multiply(item.unitPrice());
            lineRules.add(close(expected, item.amount(), TOLERANCE)
                    ? pass("ITEM_LINE_AMOUNT", "Số lượng × đơn giá = thành tiền")
                    : fail("ITEM_LINE_AMOUNT", "Số lượng × đơn giá = " + plain(expected) + " khác thành tiền " + plain(item.amount())));
        }
        BigDecimal rate = normalizeRate(item.taxRate());
        var rateRules = new ArrayList<RuleResult>();
        if (rate != null) rateRules.add(VAT_RATES.contains(rate) ? pass("ITEM_VAT_RATE_ALLOWED", "Thuế suất thuộc 0/5/8/10%")
                : fail("ITEM_VAT_RATE_ALLOWED", "Thuế suất " + plain(rate.movePointRight(2)) + "% không thuộc 0/5/8/10%"));
        var taxRules = new ArrayList<RuleResult>();
        if (rate != null && item.amount() != null && item.taxAmount() != null) {
            BigDecimal expected = item.amount().multiply(rate).setScale(0, RoundingMode.HALF_UP);
            taxRules.add(close(expected, item.taxAmount(), TOLERANCE)
                    ? pass("ITEM_TAX_AMOUNT", "Thành tiền × thuế suất = tiền thuế")
                    : fail("ITEM_TAX_AMOUNT", "Thành tiền × thuế suất = " + plain(expected) + " khác " + plain(item.taxAmount())));
        }
        String prefix = "items[" + index + "].";
        var results = new ArrayList<Result>();
        addItem(results, prefix + "productName", index, true, Kind.TEXT, item.productName(), item.origin(), input, List.of());
        addItem(results, prefix + "quantity", index, false, Kind.QUANTITY, plainOrNull(item.quantity()), item.origin(), input, lineRules);
        addItem(results, prefix + "unitPrice", index, false, Kind.AMOUNT, plainOrNull(item.unitPrice()), item.origin(), input, lineRules);
        addItem(results, prefix + "amount", index, true, Kind.AMOUNT, plainOrNull(item.amount()), item.origin(), input, lineRules);
        addItem(results, prefix + "taxRate", index, false, Kind.PERCENT, plainOrNull(item.taxRate()), item.origin(), input, rateRules);
        var amountTaxRules = new ArrayList<>(taxRules);
        addItem(results, prefix + "taxAmount", index, false, Kind.AMOUNT, plainOrNull(item.taxAmount()), item.origin(), input, amountTaxRules, true);
        return results;
    }

    private void addItem(List<Result> out, String key, int index, boolean core, Kind kind, String value, Origin origin,
            Input input, List<RuleResult> shared) {
        addItem(out, key, index, core, kind, value, origin, input, shared, false);
    }

    private void addItem(List<Result> out, String key, int index, boolean core, Kind kind, String value, Origin origin,
            Input input, List<RuleResult> shared, boolean evidenceOptional) {
        if (!present(value)) {
            if (core) out.add(new Result(key, index, true, null, origin, Status.MISSING, null, null, List.of(),
                    List.of("Dòng hàng thiếu giá trị bắt buộc")));
            return;
        }
        var rules = new ArrayList<>(shared);
        if (kind == Kind.AMOUNT) formatRules(new FieldInput(key, kind, value, core, origin), input.today(), rules);
        out.add(finish(key, index, core, kind, value, origin, input, rules, evidenceOptional));
    }

    // ---- evidence, status and score ---------------------------------------------------------

    private Result finish(String key, Integer itemIndex, boolean core, Kind kind, String value, Origin origin,
            Input input, List<RuleResult> fieldRules) {
        return finish(key, itemIndex, core, kind, value, origin, input, fieldRules, false);
    }

    private Result finish(String key, Integer itemIndex, boolean core, Kind kind, String value, Origin origin,
            Input input, List<RuleResult> fieldRules, boolean evidenceOptional) {
        var rules = new ArrayList<RuleResult>();
        Evidence found = evidenceFor(key, kind, value, input);
        if (evidenceOptional && found.outcome() == Outcome.FAIL) found = Evidence.unavailable(OPTIONAL_NOT_PRINTED);
        rules.add(evidenceRule(found, kind, origin, input));
        rules.addAll(fieldRules);
        var reasons = rules.stream().filter(r -> r.outcome() == Outcome.FAIL).map(RuleResult::message).toList();
        Status status;
        if (!reasons.isEmpty()) status = Status.WARNING;
        else if (found.outcome() == Outcome.PASS) status = Status.VERIFIED;
        // Values that cannot be matched in printed text by design (inferred codes, 1–2 digit numbers)
        // are VERIFIED only when an independent rule passed. Missing OCR or vision-only reads stay UNVERIFIED.
        else if (found.notApplicableByDesign() && rules.stream().anyMatch(r -> r.outcome() == Outcome.PASS)) status = Status.VERIFIED;
        else status = Status.UNVERIFIED;
        var explained = new ArrayList<>(reasons);
        if (status == Status.UNVERIFIED) explained.add(rules.getFirst().message());
        return new Result(key, itemIndex, core, value, origin, status, score(rules, found), found, List.copyOf(rules), List.copyOf(explained));
    }

    private Evidence evidenceFor(String key, Kind kind, String value, Input input) {
        if (evidenceNotExpected(kind)) return Evidence.unavailable(NOT_EXPECTED_IN_TEXT);
        if (!input.ocrAvailable()) return Evidence.unavailable("NO_OCR_TEXT");
        Evidence result = evidence.find(key, kind, value);
        return result == null ? Evidence.notFound() : result;
    }

    private RuleResult evidenceRule(Evidence found, Kind kind, Origin origin, Input input) {
        if (found.outcome() == Outcome.PASS)
            return pass("OCR_EVIDENCE", "Tìm thấy trong văn bản OCR (" + found.method() + ", khớp "
                    + Math.round(found.matchScore() * 100) + "%)");
        if (NOT_EXPECTED_IN_TEXT.equals(found.method())) return na("OCR_EVIDENCE", "Giá trị suy luận/mã chuẩn hóa, không kỳ vọng xuất hiện nguyên văn");
        if (TOO_SHORT_TO_MATCH.equals(found.method())) return na("OCR_EVIDENCE", "Giá trị quá ngắn để đối chiếu OCR có ý nghĩa");
        if (OPTIONAL_NOT_PRINTED.equals(found.method())) return na("OCR_EVIDENCE", "Tiền thuế theo dòng thường không in; giá trị có thể do AI tính");
        if (!input.ocrAvailable()) return na("OCR_EVIDENCE", "Không có văn bản OCR để đối chiếu");
        // Absent from OCR is only suspicious when the value had to come from that very text.
        if (origin == Origin.MANUAL) return na("OCR_EVIDENCE", "Giá trị do người nhập, không tìm thấy nguyên văn trong OCR");
        if (input.aiInputMode() == AiInputMode.TEXT)
            return fail("OCR_EVIDENCE", "AI chỉ nhận văn bản OCR nhưng giá trị không có trong văn bản đó");
        return na("OCR_EVIDENCE", "AI đọc từ ảnh; OCR không chứa giá trị này nên chưa thể xác minh");
    }

    private static boolean evidenceNotExpected(Kind kind) { return kind == Kind.CURRENCY || kind == Kind.CODE || kind == Kind.PERCENT; }

    /** Uncalibrated heuristic: evidence weight 3 (scaled by match), other rules weight 1 each. */
    static Integer score(List<RuleResult> rules, Evidence evidence) {
        double earned = 0, possible = 0;
        for (RuleResult rule : rules) {
            if (rule.outcome() == Outcome.NOT_APPLICABLE) continue;
            double weight = "OCR_EVIDENCE".equals(rule.ruleId()) ? 3 : 1;
            possible += weight;
            if (rule.outcome() == Outcome.PASS) earned += "OCR_EVIDENCE".equals(rule.ruleId()) ? weight * evidence.matchScore() : weight;
        }
        if (possible == 0) return null;
        int value = (int) Math.round(100 * earned / possible);
        boolean evidenceChecked = rules.stream().anyMatch(r -> "OCR_EVIDENCE".equals(r.ruleId()) && r.outcome() != Outcome.NOT_APPLICABLE);
        return evidenceChecked ? value : Math.min(value, 60);
    }

    // ---- helpers ----------------------------------------------------------------------------

    static LocalDate parseDate(String value) {
        if (value == null) return null;
        for (DateTimeFormatter format : List.of(DateTimeFormatter.ISO_LOCAL_DATE,
                DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT),
                DateTimeFormatter.ofPattern("d/M/uuuu").withResolverStyle(ResolverStyle.STRICT))) {
            try { return LocalDate.parse(value.trim(), format); } catch (DateTimeParseException ignored) { }
        }
        return null;
    }
    static BigDecimal parseAmount(String value) {
        try { return value == null ? null : new BigDecimal(value.trim()); } catch (NumberFormatException e) { return null; }
    }
    /** Rates are stored either as fractions (0.08) or percentages (8); compare as fractions. */
    static BigDecimal normalizeRate(BigDecimal rate) {
        if (rate == null || rate.signum() < 0) return null;
        BigDecimal fraction = rate.compareTo(BigDecimal.ONE) > 0 ? rate.movePointLeft(2) : rate;
        return fraction.setScale(2, RoundingMode.HALF_UP);
    }
    private static boolean close(BigDecimal left, BigDecimal right, BigDecimal tolerance) {
        return left.subtract(right).abs().compareTo(tolerance) <= 0;
    }
    private static boolean present(String value) { return value != null && !value.isBlank(); }
    private static String value(Map<String, FieldInput> fields, String key) {
        FieldInput field = fields.get(key); return field == null || !present(field.value()) ? null : field.value().trim();
    }
    private static BigDecimal amount(Map<String, FieldInput> fields, String key) { return parseAmount(value(fields, key)); }
    private static LocalDate date(Map<String, FieldInput> fields, String key) { return parseDate(value(fields, key)); }
    static String plain(BigDecimal value) { return value.stripTrailingZeros().toPlainString(); }
    private static String plainOrNull(BigDecimal value) { return value == null ? null : plain(value); }
    private static RuleResult pass(String id, String message) { return new RuleResult(id, Outcome.PASS, message); }
    private static RuleResult fail(String id, String message) { return new RuleResult(id, Outcome.FAIL, message); }
    private static RuleResult na(String id, String message) { return new RuleResult(id, Outcome.NOT_APPLICABLE, message); }
}
