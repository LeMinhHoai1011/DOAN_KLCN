package com.example.invoice.ai;

import com.example.invoice.dto.ai.AiDocumentResult;
import com.example.invoice.dto.ai.AiDocumentResult.AiExtraField;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.*;

/** Non-invoice rules. These fields belong to extracted_fields, never invoice_data. */
public final class DocumentTypeValidationPolicy {
    private DocumentTypeValidationPolicy() {}
    public static boolean supports(String type) {
        return Set.of("RECEIPT", "PAYMENT_VOUCHER", "RECEIPT_VOUCHER", "CONTRACT").contains(type);
    }

    public static AiDocumentResult validate(AiDocumentResult result, List<String> warnings) {
        if (!supports(result.documentType())) return result;
        Map<String, AiExtraField> fields = new LinkedHashMap<>();
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
                money(fields, warnings, true);
            }
            case "RECEIPT_VOUCHER" -> {
                required(fields, warnings, "voucherDate", "payerName", "recipientName", "receiptReason");
                money(fields, warnings, true);
            }
            case "RECEIPT" -> {
                required(fields, warnings, "receiptDate", "issuerName", "receiptKind");
                String kind = value(fields, "receiptKind");
                if ("PAYMENT".equals(kind)) {
                    required(fields, warnings, "payerName"); money(fields, warnings, true);
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
                money(fields, warnings, false);
            }
            default -> { }
        }
        for (String date : List.of("receiptDate", "voucherDate", "contractDate", "effectiveDate", "expiryDate"))
            normalizeDate(fields, warnings, date);
        String effective = value(fields, "effectiveDate"), expiry = value(fields, "expiryDate");
        if (effective != null && expiry != null) try {
            if (LocalDate.parse(expiry).isBefore(LocalDate.parse(effective)))
                warn(warnings, "INVALID_DATE_RANGE", "expiryDate", "Ngày hết hạn trước ngày hiệu lực");
        } catch (java.time.format.DateTimeParseException ignored) { /* Already reported below. */ }
        for (String tax : List.of("issuerTaxCode", "payerTaxCode", "payeeTaxCode", "recipientTaxCode", "partyATaxCode", "partyBTaxCode")) {
            String value = value(fields, tax);
            if (value != null && !value.matches("\\d{10}(?:-\\d{3})?"))
                warn(warnings, "INVALID_TAX_CODE", tax, "Mã số thuế phải gồm 10 chữ số, tùy chọn mã chi nhánh -xxx");
        }
        // Canonical document fields are persisted once through extraFields.
        return new AiDocumentResult(result.provider(), result.model(), result.documentType(), result.classificationConfidence(),
                result.accountingCategoryCode(), result.accountingAccount(), result.rawText(), null, List.of(), result.warnings(),
                result.rawResponse(), result.durationMs(), result.documentDirection(), result.transactionAssessment(),
                List.copyOf(fields.values()), result.companyRole());
    }

    private static void required(Map<String, AiExtraField> fields, List<String> warnings, String... names) {
        for (String name : names) if (value(fields, name) == null)
            warn(warnings, "MISSING_REQUIRED_FIELD", name, "Thiếu trường bắt buộc theo loại chứng từ");
    }
    private static String value(Map<String, AiExtraField> fields, String name) {
        var field = fields.get(name);
        return field == null || field.value() == null || field.value().isBlank() ? null : field.value().trim();
    }
    private static void money(Map<String, AiExtraField> fields, List<String> warnings, boolean required) {
        String amount = value(fields, "totalAmount"), currency = value(fields, "currency");
        if (required && amount == null) warn(warnings, "MISSING_REQUIRED_FIELD", "totalAmount", "Thiếu số tiền thanh toán");
        if ((required || amount != null) && currency == null)
            warn(warnings, "MISSING_REQUIRED_FIELD", "currency", "Có số tiền phải có đơn vị tiền tệ trên chứng từ");
        if (amount != null) try {
            BigDecimal parsed = new BigDecimal(amount);
            if (parsed.signum() <= 0) throw new NumberFormatException();
            replace(fields, "totalAmount", parsed.toPlainString());
        } catch (NumberFormatException exception) { warn(warnings, "INVALID_AMOUNT", "totalAmount", "Số tiền phải là số thập phân lớn hơn 0, không có dấu phân nhóm mơ hồ"); }
        if (currency != null) try {
            String code = currency.toUpperCase(Locale.ROOT);
            if (!code.matches("[A-Z]{3}") || Set.of("XXX", "XTS").contains(code)) throw new IllegalArgumentException();
            Currency.getInstance(code); replace(fields, "currency", code);
        } catch (IllegalArgumentException exception) { warn(warnings, "INVALID_CURRENCY", "currency", "Đơn vị tiền tệ không hợp lệ theo ISO 4217"); }
    }
    private static void normalizeDate(Map<String, AiExtraField> fields, List<String> warnings, String name) {
        String text = value(fields, name); if (text == null) return;
        for (DateTimeFormatter format : List.of(DateTimeFormatter.ISO_LOCAL_DATE,
                DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT))) {
            try { replace(fields, name, LocalDate.parse(text, format).toString()); return; }
            catch (java.time.format.DateTimeParseException ignored) { }
        }
        warn(warnings, "INVALID_DATE", name, "Ngày không hợp lệ; chấp nhận yyyy-MM-dd hoặc dd/MM/yyyy và lưu ISO");
    }
    private static void replace(Map<String, AiExtraField> fields, String name, String value) {
        var old = fields.get(name); fields.put(name, new AiExtraField(name, old.label(), value, old.confidence()));
    }
    private static void warn(List<String> warnings, String code, String field, String message) {
        warnings.add(code + ": field=" + field + "; " + message);
    }
}
