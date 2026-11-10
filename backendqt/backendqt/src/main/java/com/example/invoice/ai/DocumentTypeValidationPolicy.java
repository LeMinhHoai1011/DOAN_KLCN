package com.example.invoice.ai;

import com.example.invoice.dto.ai.AiDocumentResult;
import com.example.invoice.dto.ai.AiDocumentResult.AiExtraField;
import com.example.invoice.ai.normalize.DocumentDates;
import com.example.invoice.ai.normalize.LocalizedNumber;
import com.example.invoice.ai.normalize.TaxCodes;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Pattern;

/** Non-invoice rules. These fields belong to extracted_fields, never invoice_data. */
public final class DocumentTypeValidationPolicy {
    private DocumentTypeValidationPolicy() {}
    private static final Pattern PRINTED_VND = Pattern.compile("(?iu)(vnđ|vnd|đồng|₫|\\d\\s*đ\\b|\\d\\s*đ$)");
    public static boolean supports(String type) {
        return Set.of("RECEIPT", "PAYMENT_VOUCHER", "RECEIPT_VOUCHER", "CONTRACT").contains(type);
    }

    public static AiDocumentResult validate(AiDocumentResult result, List<String> warnings) {
        if (!supports(result.documentType())) return result;
        Map<String, AiExtraField> fields = new LinkedHashMap<>();
        List<AiDocumentResult.AiFieldNote> notes = new ArrayList<>(result.notes());
        if (result.extraFields() != null) for (var field : result.extraFields()) {
            if (field == null || field.name() == null) continue;
            if (fields.putIfAbsent(field.name(), field) != null)
                warn(warnings, "DUPLICATE_DOCUMENT_FIELD", field.name(), "Trường lặp lại; cần xác minh giá trị");
        }
        if (result.fields() != null) for (var field : result.fields()) {
            if (field == null || field.fieldName() == null) continue;
            var candidate = new AiExtraField(field.fieldName(), field.fieldName(), field.fieldValue(), field.confidence());
            var previous = fields.putIfAbsent(field.fieldName(), candidate);
            if (previous != null && !Objects.equals(previous.value(), candidate.value()))
                warn(warnings, "CONFLICTING_DOCUMENT_FIELD", field.fieldName(), "Hai nguồn trả giá trị khác nhau");
        }
        switch (result.documentType()) {
            case "PAYMENT_VOUCHER" -> {
                required(fields, warnings, "voucherDate", "payerName", "payeeName", "paymentReason");
                money(fields, warnings, true, notes);
            }
            case "RECEIPT_VOUCHER" -> {
                required(fields, warnings, "voucherDate", "payerName", "recipientName", "receiptReason");
                money(fields, warnings, true, notes);
            }
            case "RECEIPT" -> {
                required(fields, warnings, "receiptDate", "issuerName", "receiptKind");
                String kind = value(fields, "receiptKind");
                if ("PAYMENT".equals(kind)) {
                    required(fields, warnings, "payerName"); money(fields, warnings, true, notes);
                } else if ("NON_FINANCIAL".equals(kind)) {
                    required(fields, warnings, "recipientName", "subject");
                    if (value(fields, "totalAmount") != null || value(fields, "currency") != null)
                        warn(warnings, "RECEIPT_FINANCIAL_CONFLICT", "receiptKind", "Biên nhận phi tài chính có dữ liệu tiền; cần xác minh loại");
                } else warn(warnings, "RECEIPT_KIND_UNDETERMINED", "receiptKind", "Phải xác định PAYMENT hoặc NON_FINANCIAL từ nội dung chứng từ");
            }
            case "CONTRACT" -> {
                required(fields, warnings, "contractDate", "partyAName", "partyBName", "subject");
                if (value(fields, "contractNumber") == null && value(fields, "contractTitle") == null
                        && value(fields, "referenceNumber") == null)
                    warn(warnings, "CONTRACT_IDENTITY_MISSING", "contractNumber", "Cần số hợp đồng, tiêu đề hợp đồng hoặc mã tham chiếu để định danh");
                money(fields, warnings, false, notes);
            }
            default -> { }
        }
        for (String date : List.of("receiptDate", "voucherDate", "contractDate", "effectiveDate", "expiryDate"))
            normalizeDate(fields, warnings, date, notes);
        String effective = value(fields, "effectiveDate"), expiry = value(fields, "expiryDate");
        if (effective != null && expiry != null) try {
            if (LocalDate.parse(expiry).isBefore(LocalDate.parse(effective)))
                warn(warnings, "INVALID_DATE_RANGE", "expiryDate", "Ngày hết hạn trước ngày hiệu lực");
        } catch (java.time.format.DateTimeParseException ignored) { /* Already reported below. */ }
        for (String tax : List.of("issuerTaxCode", "payerTaxCode", "payeeTaxCode", "recipientTaxCode", "partyATaxCode", "partyBTaxCode")) {
            String value = value(fields, tax);
            if (value == null) continue;
            TaxCodes.Parsed parsed = TaxCodes.parse(value);
            if (!parsed.valid())
                warn(warnings, "INVALID_TAX_CODE", tax, "Mã số thuế phải gồm 10 chữ số (tùy chọn mã chi nhánh -xxx) hoặc 12 chữ số định danh");
            else {
                if (parsed.changed()) replace(fields, tax, parsed.value(), notes, "NORMALIZED", "TAX_CODE_FORMAT");
                if (!parsed.checksumOk()) warnings.add("TAX_CODE_CHECKSUM_MISMATCH: field=" + tax
                        + "; mã số thuế sai chữ số kiểm tra, có thể đọc nhầm một chữ số");
            }
        }
        // Canonical document fields are persisted once through extraFields.
        return result.with(result.warnings(), null, List.of(), List.copyOf(fields.values()), notes);
    }

    private static void required(Map<String, AiExtraField> fields, List<String> warnings, String... names) {
        for (String name : names) if (value(fields, name) == null)
            warn(warnings, "MISSING_REQUIRED_FIELD", name, "Thiếu trường bắt buộc theo loại chứng từ");
    }
    private static String value(Map<String, AiExtraField> fields, String name) {
        var field = fields.get(name);
        return field == null || field.value() == null || field.value().isBlank() ? null : field.value().trim();
    }
    private static void money(Map<String, AiExtraField> fields, List<String> warnings, boolean required,
            List<AiDocumentResult.AiFieldNote> notes) {
        String amount = value(fields, "totalAmount"), currency = value(fields, "currency");
        // A currency printed inside the amount ("1.500.000 đ") is visible evidence, not an assumption.
        if (amount != null && currency == null && PRINTED_VND.matcher(amount).find()) {
            fields.put("currency", new AiExtraField("currency", "Đơn vị tiền tệ", "VND", null));
            notes.add(new AiDocumentResult.AiFieldNote("currency", null, amount, "VND", "NORMALIZED", "CURRENCY_PRINTED_WITH_AMOUNT"));
            currency = "VND";
        }
        if (required && amount == null) warn(warnings, "MISSING_REQUIRED_FIELD", "totalAmount", "Thiếu số tiền thanh toán");
        if ((required || amount != null) && currency == null)
            warn(warnings, "MISSING_REQUIRED_FIELD", "currency", "Có số tiền phải có đơn vị tiền tệ trên chứng từ");
        if (amount != null) {
            LocalizedNumber.Parsed parsed = LocalizedNumber.parse(amount, LocalizedNumber.Kind.MONEY, LocalizedNumber.Convention.UNKNOWN);
            if (parsed.value() == null || parsed.value().signum() <= 0) {
                notes.add(new AiDocumentResult.AiFieldNote("totalAmount", null, amount, null, "INVALID", parsed.reason()));
                warn(warnings, "INVALID_AMOUNT", "totalAmount", "Số tiền phải là số lớn hơn 0 đọc được từ chứng từ");
            } else {
                String status = parsed.status() == LocalizedNumber.Status.VALID ? "VALID" : parsed.status().name();
                replace(fields, "totalAmount", parsed.value().toPlainString(), notes, status, parsed.reason());
                if (parsed.status() == LocalizedNumber.Status.AMBIGUOUS)
                    warnings.add("AMBIGUOUS_NUMBER: field=totalAmount; \"" + amount + "\" được đọc là "
                            + parsed.value().toPlainString() + "; cần kiểm tra trên chứng từ");
            }
        }
        if (currency != null) try {
            String code = currency.toUpperCase(Locale.ROOT);
            if (!code.matches("[A-Z]{3}") || Set.of("XXX", "XTS").contains(code)) throw new IllegalArgumentException();
            Currency.getInstance(code); replace(fields, "currency", code, notes, "NORMALIZED", "CURRENCY_CODE");
        } catch (IllegalArgumentException exception) { warn(warnings, "INVALID_CURRENCY", "currency", "Đơn vị tiền tệ không hợp lệ theo ISO 4217"); }
    }
    private static void normalizeDate(Map<String, AiExtraField> fields, List<String> warnings, String name,
            List<AiDocumentResult.AiFieldNote> notes) {
        String text = value(fields, name); if (text == null) return;
        DocumentDates.Parsed parsed = DocumentDates.parse(text);
        if (parsed.value() != null) { replace(fields, name, parsed.value(), notes, parsed.status().name(), parsed.reason()); return; }
        notes.add(new AiDocumentResult.AiFieldNote(name, null, text, null, parsed.status().name(), parsed.reason()));
        warn(warnings, "INVALID_DATE", name, "Ngày không hợp lệ hoặc thiếu năm đủ 4 chữ số; cần nhập theo chứng từ");
    }
    /** Writes the normalized value and, when it differs from what was read, a note keeping the original text. */
    private static void replace(Map<String, AiExtraField> fields, String name, String value,
            List<AiDocumentResult.AiFieldNote> notes, String status, String reason) {
        var old = fields.get(name);
        if (old.value() != null && !old.value().trim().equals(value))
            notes.add(new AiDocumentResult.AiFieldNote(name, null, old.value(), value, status, reason));
        fields.put(name, new AiExtraField(name, old.label(), value, old.confidence()));
    }
    private static void warn(List<String> warnings, String code, String field, String message) {
        warnings.add(code + ": field=" + field + "; " + message);
    }
}
