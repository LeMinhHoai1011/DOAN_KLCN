package com.example.invoice.verification;

import static org.junit.jupiter.api.Assertions.*;

import com.example.invoice.verification.FieldVerifier.AiInputMode;
import com.example.invoice.verification.FieldVerifier.FieldInput;
import com.example.invoice.verification.FieldVerifier.Input;
import com.example.invoice.verification.FieldVerifier.ItemInput;
import com.example.invoice.verification.FieldVerifier.Origin;
import com.example.invoice.verification.FieldVerifier.Result;
import com.example.invoice.verification.FieldVerifier.Status;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FieldVerifierTest {
    static final LocalDate TODAY = LocalDate.of(2026, 11, 1);
    // Valid check digits: 0100109106 and 0309876547.
    static final String OCR = """
            HÓA ĐƠN GIÁ TRỊ GIA TĂNG
            Ký hiệu: 1C24TAA   Số: 0000123
            Ngày 15 tháng 03 năm 2024
            Đơn vị bán hàng: CÔNG TY TNHH ALPHA
            Mã số thuế: 0100109106
            Người mua: CÔNG TY BETA   Mã số thuế: 0309876547
            1 Cước vận chuyển   2   500.000   1.000.000
            2 Phí bốc xếp       1   200.000     200.000
            Cộng tiền hàng: 1.200.000
            Thuế suất GTGT 8%   Tiền thuế GTGT: 96.000
            Tổng cộng tiền thanh toán: 1.296.000
            """;

    static Map<String, String> vatInvoice() {
        var values = new LinkedHashMap<String, String>();
        values.put("invoiceSeries", "1C24TAA"); values.put("invoiceNumber", "123"); values.put("invoiceDate", "2024-03-15");
        values.put("sellerName", "CÔNG TY TNHH ALPHA"); values.put("sellerTaxCode", "0100109106");
        values.put("buyerTaxCode", "0309876547");
        values.put("subtotal", "1200000"); values.put("vatAmount", "96000"); values.put("totalAmount", "1296000");
        return values;
    }
    static List<ItemInput> twoItems() {
        return List.of(
                new ItemInput("Cước vận chuyển", new BigDecimal("2"), new BigDecimal("500000"), new BigDecimal("0.08"), new BigDecimal("80000"), new BigDecimal("1000000"), Origin.AI),
                new ItemInput("Phí bốc xếp", BigDecimal.ONE, new BigDecimal("200000"), new BigDecimal("8"), new BigDecimal("16000"), new BigDecimal("200000"), Origin.AI));
    }
    static List<Result> run(String type, Map<String, String> values, List<ItemInput> items, AiInputMode mode, String ocr) {
        var fields = new ArrayList<FieldInput>();
        List<String> core = CoreFieldProfiles.core(type, values.get("receiptKind"));
        var all = new LinkedHashMap<>(values); core.forEach(key -> all.putIfAbsent(key, null));
        all.forEach((key, value) -> fields.add(new FieldInput(key, CoreFieldProfiles.kindOf(key), value, core.contains(key), Origin.AI)));
        return new FieldVerifier(new OcrEvidenceFinder(ocr, null)).verify(new Input(type, mode, ocr != null, fields, items, TODAY));
    }
    static Result field(List<Result> results, String key) {
        return results.stream().filter(r -> r.key().equals(key)).findFirst().orElseThrow(() -> new AssertionError("no " + key));
    }
    static boolean failed(Result result, String rule) {
        return result.rules().stream().anyMatch(r -> r.ruleId().equals(rule) && r.outcome() == FieldVerifier.Outcome.FAIL);
    }

    @Test void consistentInvoiceFoundInOcrIsVerified() {
        var results = run("VAT_INVOICE", vatInvoice(), twoItems(), AiInputMode.TEXT, OCR);
        for (String key : List.of("invoiceSeries", "invoiceNumber", "invoiceDate", "sellerName", "sellerTaxCode", "subtotal", "vatAmount", "totalAmount"))
            assertEquals(Status.VERIFIED, field(results, key).status(), key + " " + field(results, key).reasons());
        assertTrue(results.stream().noneMatch(r -> r.status() == Status.WARNING), results.toString());
        Integer score = field(results, "totalAmount").score();
        assertNotNull(score); assertTrue(score >= 0 && score <= 100);
    }

    @Test void taxCodeFormatAndChecksum() {
        assertTrue(FieldVerifier.taxCodeChecksumValid("0100109106"));
        assertFalse(FieldVerifier.taxCodeChecksumValid("0100109107"));
        var values = vatInvoice(); values.put("sellerTaxCode", "0100109107");
        var seller = field(run("VAT_INVOICE", values, List.of(), AiInputMode.TEXT, OCR.replace("0100109106", "0100109107")), "sellerTaxCode");
        assertEquals(Status.WARNING, seller.status());
        assertTrue(failed(seller, "TAX_CODE_CHECKSUM"));
        values.put("sellerTaxCode", "01001091"); // wrong length
        assertTrue(failed(field(run("VAT_INVOICE", values, List.of(), AiInputMode.TEXT, OCR), "sellerTaxCode"), "TAX_CODE_FORMAT"));
        values.put("sellerTaxCode", "0100109106-001"); // branch suffix allowed
        assertFalse(failed(field(run("VAT_INVOICE", values, List.of(), AiInputMode.TEXT, OCR), "sellerTaxCode"), "TAX_CODE_FORMAT"));
        values.put("sellerTaxCode", "0309876547"); // same as buyer
        assertTrue(failed(field(run("VAT_INVOICE", values, List.of(), AiInputMode.TEXT, OCR), "sellerTaxCode"), "SELLER_BUYER_DISTINCT"));
    }

    @Test void datesMustBeRealAndNotInTheFuture() {
        var values = vatInvoice(); values.put("invoiceDate", "2024-02-30");
        assertTrue(failed(field(run("VAT_INVOICE", values, List.of(), AiInputMode.TEXT, OCR), "invoiceDate"), "DATE_VALID"));
        values.put("invoiceDate", "2027-01-01");
        assertTrue(failed(field(run("VAT_INVOICE", values, List.of(), AiInputMode.TEXT, OCR), "invoiceDate"), "DATE_NOT_FUTURE"));
        values.put("invoiceDate", "2024-03-15"); values.put("signDate", "2024-03-10");
        assertTrue(failed(field(run("VAT_INVOICE", values, List.of(), AiInputMode.TEXT, OCR), "signDate"), "SIGN_NOT_BEFORE_ISSUE"));
        // "Ngày 15 tháng 03 năm 2024" is accepted as evidence for 2024-03-15.
        values.remove("signDate");
        assertEquals("OCR_TEXT", field(run("VAT_INVOICE", values, List.of(), AiInputMode.TEXT, OCR), "invoiceDate").evidence().method());
    }

    @Test void amountsMustAddUp() {
        var values = vatInvoice(); values.put("totalAmount", "1269000"); // digits transposed
        var results = run("VAT_INVOICE", values, List.of(), AiInputMode.TEXT, OCR);
        for (String key : List.of("subtotal", "vatAmount", "totalAmount"))
            assertTrue(failed(field(results, key), "TOTAL_EQUALS_SUBTOTAL_PLUS_VAT"), key);
        assertTrue(failed(field(results, "totalAmount"), "OCR_EVIDENCE"), "AI value absent from the text it was given");
        values.put("totalAmount", "-5");
        assertTrue(failed(field(run("VAT_INVOICE", values, List.of(), AiInputMode.TEXT, OCR), "totalAmount"), "AMOUNT_VALID"));
        values.put("totalAmount", "1296000.5"); // one-unit rounding tolerance
        assertFalse(failed(field(run("VAT_INVOICE", values, List.of(), AiInputMode.TEXT, OCR), "totalAmount"), "TOTAL_EQUALS_SUBTOTAL_PLUS_VAT"));
    }

    @Test void vatRateAndLineTaxAreChecked() {
        var items = new ArrayList<>(twoItems());
        items.set(1, new ItemInput("Phí bốc xếp", BigDecimal.ONE, new BigDecimal("200000"), new BigDecimal("0.07"), new BigDecimal("14000"), new BigDecimal("200000"), Origin.AI));
        var results = run("VAT_INVOICE", vatInvoice(), items, AiInputMode.TEXT, OCR);
        assertTrue(failed(field(results, "items[1].taxRate"), "ITEM_VAT_RATE_ALLOWED"));
        assertTrue(failed(field(results, "vatAmount"), "ITEMS_VAT_SUM_MATCHES"), "80000 + 14000 ≠ 96000");
        // 8 (percent) and 0.08 (fraction) are the same rate.
        assertEquals(Status.VERIFIED, field(run("VAT_INVOICE", vatInvoice(), twoItems(), AiInputMode.TEXT, OCR), "items[1].taxRate").status());
    }

    @Test void manyItemLinesAreCheckedRowByRowAndAgainstTheHeader() {
        var items = new ArrayList<ItemInput>();
        var ocr = new StringBuilder("HÓA ĐƠN\n");
        BigDecimal sum = BigDecimal.ZERO;
        for (int i = 1; i <= 25; i++) {
            BigDecimal price = BigDecimal.valueOf(10_000L * i);
            items.add(new ItemInput("Mặt hàng số " + i, new BigDecimal("3"), price, null, null, price.multiply(new BigDecimal("3")), Origin.AI));
            ocr.append("Mặt hàng số ").append(i).append(" 3 ").append(price).append(' ').append(price.multiply(new BigDecimal("3"))).append('\n');
            sum = sum.add(price.multiply(new BigDecimal("3")));
        }
        var values = new LinkedHashMap<String, String>();
        values.put("sellerName", "CỬA HÀNG GAMMA"); values.put("invoiceDate", "2024-05-02"); values.put("totalAmount", sum.toPlainString());
        ocr.append("CỬA HÀNG GAMMA 02/05/2024 Tổng: ").append(sum);
        var results = run("RETAIL_INVOICE", values, items, AiInputMode.TEXT, ocr.toString());
        assertEquals(25, results.stream().filter(r -> r.key().endsWith(".amount")).count());
        assertTrue(results.stream().noneMatch(r -> r.status() == Status.WARNING), results.stream().filter(r -> r.status() == Status.WARNING).toList().toString());
        assertFalse(failed(field(results, "totalAmount"), "ITEMS_SUM_MATCHES"));

        items.set(17, new ItemInput("Mặt hàng số 18", new BigDecimal("3"), new BigDecimal("180000"), null, null, new BigDecimal("450000"), Origin.AI));
        results = run("RETAIL_INVOICE", values, items, AiInputMode.TEXT, ocr.toString());
        assertTrue(failed(field(results, "items[17].amount"), "ITEM_LINE_AMOUNT"));
        assertTrue(failed(field(results, "totalAmount"), "ITEMS_SUM_MATCHES"));
        assertFalse(failed(field(results, "items[16].amount"), "ITEM_LINE_AMOUNT"), "only the broken row is flagged");
    }

    @Test void missingCoreFieldsDependOnDocumentType() {
        var results = run("VAT_INVOICE", Map.of("totalAmount", "1296000"), List.of(), AiInputMode.TEXT, OCR);
        assertEquals(Status.MISSING, field(results, "sellerTaxCode").status());
        assertNull(field(results, "sellerTaxCode").score());
        // A retail bill has no tax code requirement, so it is not reported missing.
        var retail = run("RETAIL_INVOICE", Map.of("sellerName", "CÔNG TY TNHH ALPHA", "invoiceDate", "2024-03-15", "totalAmount", "1296000"), List.of(), AiInputMode.TEXT, OCR);
        assertTrue(retail.stream().noneMatch(r -> r.key().equals("sellerTaxCode")));
        assertTrue(retail.stream().noneMatch(r -> r.status() == Status.MISSING));
        // A non-financial receipt must not be forced to carry an amount.
        var receipt = run("RECEIPT", Map.of("receiptDate", "2024-03-15", "issuerName", "CÔNG TY TNHH ALPHA", "receiptKind", "NON_FINANCIAL"), List.of(), AiInputMode.TEXT, OCR);
        assertTrue(receipt.stream().noneMatch(r -> r.key().equals("totalAmount")));
        var payment = run("RECEIPT", Map.of("receiptDate", "2024-03-15", "issuerName", "CÔNG TY TNHH ALPHA", "receiptKind", "PAYMENT"), List.of(), AiInputMode.TEXT, OCR);
        assertEquals(Status.MISSING, field(payment, "totalAmount").status());
    }

    @Test void valueAbsentFromOcrIsWarningInTextModeButUnverifiedForVision() {
        var values = vatInvoice(); values.put("sellerName", "CÔNG TY CỔ PHẦN DELTA");
        assertEquals(Status.WARNING, field(run("VAT_INVOICE", values, List.of(), AiInputMode.TEXT, OCR), "sellerName").status());
        Result vision = field(run("VAT_INVOICE", values, List.of(), AiInputMode.VISION, OCR), "sellerName");
        assertEquals(Status.UNVERIFIED, vision.status(), "vision read is not assumed wrong");
        assertNull(vision.score(), "no check could be applied, so no heuristic score");
        assertEquals(Status.UNVERIFIED, field(run("VAT_INVOICE", values, List.of(), AiInputMode.MIXED, OCR), "sellerName").status());
        // No OCR at all: nothing to compare with.
        assertEquals(Status.UNVERIFIED, field(run("VAT_INVOICE", vatInvoice(), List.of(), AiInputMode.TEXT, null), "sellerName").status());
    }

    @Test void ocrMisreadsAreToleratedOnlyWithinLimits() {
        // Diacritics lost by OCR still count as evidence for the accented value.
        String noAccents = OcrEvidenceFinder.fold(OCR);
        assertEquals(Status.VERIFIED, field(run("VAT_INVOICE", vatInvoice(), List.of(), AiInputMode.TEXT, noAccents), "sellerName").status());
        // An OCR digit error in the tax code means the AI value has no evidence in the text.
        String misread = OCR.replace("0100109106", "0100109186");
        assertTrue(failed(field(run("VAT_INVOICE", vatInvoice(), List.of(), AiInputMode.TEXT, misread), "sellerTaxCode"), "OCR_EVIDENCE"));
        // "10 100" must not become evidence for 10100.
        assertTrue(OcrEvidenceFinder.numbers("10 100").stream().noneMatch(n -> n.compareTo(new BigDecimal("10100")) == 0));
        assertTrue(OcrEvidenceFinder.numbers("1.296.000,00").stream().anyMatch(n -> n.compareTo(new BigDecimal("1296000")) == 0));
    }

    @Test void inferredCodesAreNotExpectedInTextAndShortValuesAreNotMatched() {
        var values = new LinkedHashMap<String, String>();
        values.put("voucherDate", "2024-03-15"); values.put("payerName", "CÔNG TY BETA"); values.put("payeeName", "CÔNG TY TNHH ALPHA");
        values.put("paymentReason", "Cước vận chuyển"); values.put("totalAmount", "1296000"); values.put("currency", "VND");
        var results = run("PAYMENT_VOUCHER", values, List.of(), AiInputMode.TEXT, OCR);
        assertEquals(Status.VERIFIED, field(results, "currency").status(), "ISO code passes its format rule");
        values.put("currency", "VNĐ");
        assertEquals(Status.WARNING, field(run("PAYMENT_VOUCHER", values, List.of(), AiInputMode.TEXT, OCR), "currency").status());
        var quantity = field(run("VAT_INVOICE", vatInvoice(), twoItems(), AiInputMode.TEXT, OCR), "items[0].quantity");
        assertEquals("TOO_SHORT_TO_MATCH", quantity.evidence().method());
        assertEquals(Status.VERIFIED, quantity.status(), "2 × 500.000 = 1.000.000 is an independent check");
    }

    @Test void manualValuesAbsentFromOcrAreUnverifiedNotWarning() {
        var fields = List.of(new FieldInput("sellerName", FieldVerifier.Kind.TEXT, "CÔNG TY KHÁC", true, Origin.MANUAL));
        var result = new FieldVerifier(new OcrEvidenceFinder(OCR, null))
                .verify(new Input("VAT_INVOICE", AiInputMode.TEXT, true, fields, List.of(), TODAY)).getFirst();
        assertEquals(Status.UNVERIFIED, result.status());
    }

    @Test void verifierNeverChangesValues() {
        var values = vatInvoice(); values.put("totalAmount", "1269000");
        for (Result result : run("VAT_INVOICE", values, twoItems(), AiInputMode.TEXT, OCR))
            if (result.itemIndex() == null && values.containsKey(result.key())) assertEquals(values.get(result.key()), result.value());
    }
}
