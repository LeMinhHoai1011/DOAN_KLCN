package com.example.invoice.ai;

import com.example.invoice.dto.ai.AiDocumentResult;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class DocumentTypeValidationPolicyTest {
    @ParameterizedTest
    @ValueSource(strings = {"RECEIPT", "PAYMENT_VOUCHER", "RECEIPT_VOUCHER", "CONTRACT"})
    void validTypeSpecificDataCanBeProcessedWithoutInvoiceFields(String type) {
        var validated = validate(type, validFields(type));
        assertFalse(validated.requiresReview(new BigDecimal("0.75")), validated.warnings().toString());
        assertNull(validated.result().invoice());
        assertTrue(validated.warnings().stream().noneMatch(w -> w.startsWith("MISSING_INVOICE")));
    }
    @ParameterizedTest
    @ValueSource(strings = {"RECEIPT", "PAYMENT_VOUCHER", "RECEIPT_VOUCHER", "CONTRACT"})
    void missingRequiredFieldNeedsReviewNotTechnicalFailure(String type) {
        var fields = validFields(type); fields.remove(type.equals("CONTRACT") ? "partyAName" : "payerName");
        var validated = validate(type, fields);
        assertTrue(validated.requiresReview(new BigDecimal("0.75")));
        assertTrue(validated.reviewReasons(new BigDecimal("0.75")).stream().anyMatch(w -> w.startsWith("MISSING_REQUIRED_FIELD")));
    }
    @Test void contractAllowsTitleInsteadOfNumberAndNoMoney() {
        var fields = validFields("CONTRACT"); fields.remove("contractNumber"); fields.put("contractTitle", "Hợp đồng dịch vụ");
        assertFalse(validate("CONTRACT", fields).requiresReview(new BigDecimal("0.75")));
        fields.remove("contractTitle");
        assertTrue(validate("CONTRACT", fields).warnings().stream().anyMatch(w -> w.startsWith("CONTRACT_IDENTITY_MISSING")));
    }
    @Test void nonFinancialReceiptDoesNotRequireMoneyButRequiresEvidenceOfKind() {
        var fields = validFields("RECEIPT"); fields.put("receiptKind", "NON_FINANCIAL");
        fields.remove("totalAmount"); fields.remove("currency"); fields.remove("payerName");
        fields.put("recipientName", "Bên nhận"); fields.put("subject", "Nhận hồ sơ");
        assertFalse(validate("RECEIPT", fields).requiresReview(new BigDecimal("0.75")));
        fields.remove("receiptKind");
        assertTrue(validate("RECEIPT", fields).warnings().stream().anyMatch(w -> w.startsWith("RECEIPT_KIND_UNDETERMINED")));
    }
    @Test void normalizesDatesAndCurrencyWithoutInventingValues() {
        var fields = validFields("PAYMENT_VOUCHER"); fields.put("voucherDate", "16/10/2026"); fields.put("currency", "vnd");
        var result = validate("PAYMENT_VOUCHER", fields).result();
        assertEquals("2026-10-16", value(result, "voucherDate")); assertEquals("VND", value(result, "currency"));
        assertNull(value(result, "voucherNumber"));
    }
    @Test void rejectsImpossibleDatesUnknownCurrencyAndNonpositiveMoney() {
        var fields = validFields("RECEIPT_VOUCHER"); fields.put("voucherDate", "2026-02-30");
        fields.put("currency", "ZZZ"); fields.put("totalAmount", "-1"); fields.put("payerTaxCode", "invalid");
        var validated = validate("RECEIPT_VOUCHER", fields);
        for (String code : List.of("INVALID_DATE", "INVALID_CURRENCY", "INVALID_AMOUNT", "INVALID_TAX_CODE"))
            assertTrue(validated.warnings().stream().anyMatch(w -> w.startsWith(code)), code);
    }
    @Test void moneyOnContractMakesCurrencyConditional() {
        var fields = validFields("CONTRACT"); fields.put("totalAmount", "100");
        assertTrue(validate("CONTRACT", fields).warnings().stream().anyMatch(w -> w.contains("field=currency")));
    }
    private AiDocumentResultValidator.ValidatedAiDocumentResult validate(String type, Map<String,String> fields) {
        return new AiDocumentResultValidator().validate(result(type, fields), Set.of(type));
    }
    public static AiDocumentResult result(String type, Map<String,String> fields) {
        if (type.equals("INVOICE")) return new AiDocumentResult("ollama", "qwen", type, new BigDecimal("0.95"), null,
                new AiDocumentResult.AiInvoiceExtraction("INV-1", null, "2026-10-16", "Seller", "0312345678", null, null, null, null,
                        new BigDecimal("100"), new BigDecimal("10"), new BigDecimal("110"),
                        List.of(new AiDocumentResult.AiInvoiceItemExtraction("Product", BigDecimal.ONE, "unit", new BigDecimal("100"), new BigDecimal("0.10"), new BigDecimal("10"), new BigDecimal("100")))),
                List.of(), List.of("DOCUMENT_TYPE_REASON: Hóa đơn hợp lệ"), "raw", 1L);
        return new AiDocumentResult("ollama", "qwen", type, new BigDecimal("0.95"), null, null, null, null,
                List.of(), List.of("DOCUMENT_TYPE_REASON: Có bằng chứng loại chứng từ"), "raw", 1L, null, null,
                fields.entrySet().stream().map(e -> new AiDocumentResult.AiExtraField(e.getKey(), e.getKey(), e.getValue(), null)).toList(), null);
    }
    public static Map<String,String> validFields(String type) {
        Map<String,String> values = new LinkedHashMap<>();
        if (type.equals("CONTRACT")) {
            values.put("contractDate", "2026-10-16"); values.put("partyAName", "Bên A"); values.put("partyBName", "Bên B");
            values.put("subject", "Dịch vụ"); values.put("contractNumber", "HD-1"); return values;
        }
        values.put("payerName", "Bên trả"); values.put("totalAmount", "100000"); values.put("currency", "VND");
        if (type.equals("RECEIPT")) {
            values.put("receiptKind", "PAYMENT"); values.put("receiptDate", "2026-10-16"); values.put("issuerName", "Bên phát hành");
        } else {
            values.put("voucherDate", "2026-10-16");
            if (type.equals("PAYMENT_VOUCHER")) { values.put("payeeName", "Bên nhận"); values.put("paymentReason", "Thanh toán dịch vụ"); }
            else { values.put("recipientName", "Bên nhận"); values.put("receiptReason", "Thu dịch vụ"); }
        }
        return values;
    }
    private String value(AiDocumentResult result, String name) {
        return result.extraFields().stream().filter(f -> name.equals(f.name())).map(AiDocumentResult.AiExtraField::value).findFirst().orElse(null);
    }
}
