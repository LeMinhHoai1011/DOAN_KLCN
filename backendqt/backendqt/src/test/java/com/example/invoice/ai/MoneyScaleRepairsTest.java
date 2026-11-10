package com.example.invoice.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.invoice.dto.ai.AiDocumentResult;
import com.example.invoice.dto.ai.AiDocumentResult.AiExtraField;
import com.example.invoice.dto.ai.AiDocumentResult.AiInvoiceExtraction;
import com.example.invoice.dto.ai.AiDocumentResult.AiInvoiceItemExtraction;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Case from benchmark run smoke-raster-002: vision read "1.305.500" as the JSON number 1305.5. */
class MoneyScaleRepairsTest {
    /** Tesseract text of that invoice (amount lines only). */
    private static final String OCR = "STT Tên hàng ĐVT SL Đơn giá Thành tiền\n1 Tấm Laminate L7341 Tấm 1 1.305.500 1.305.500\n"
            + "Cộng tiền hàng: 1.305.500\nTiền thuế GTGT: 104.440\nTổng tiền thanh toán: 1.409.940";

    private static BigDecimal d(String value) { return value == null ? null : new BigDecimal(value); }

    private static AiDocumentResult result(String subtotal, String vat, String total, String price, String tax, String amount) {
        var item = new AiInvoiceItemExtraction("Tấm Laminate L7341", d("1"), "Tấm", d(price), d("0.08"), d(tax), d(amount));
        var invoice = new AiInvoiceExtraction("459", "1C25TKV", "2025-09-04", "KEDING", "0317822778", null, null, null, null, null,
                d(subtotal), d(vat), d(total), null, null, null, null, List.of(item));
        return new AiDocumentResult("ollama", "qwen3-vl:8b", "VAT_INVOICE", d("0.99"), null, invoice, List.of(), List.of(), "raw", 1L);
    }

    @Test
    void scalesEveryMoneyValueThatThePrintedTextConfirms() {
        var fixed = MoneyScaleRepairs.apply(result("1305.5", "104.44", "1409.94", "1305.5", "104.44", "1305.5"), OCR);
        assertEquals(d("1305500"), fixed.invoice().subtotal());
        assertEquals(d("104440"), fixed.invoice().vatAmount());
        assertEquals(d("1409940"), fixed.invoice().totalAmount());
        var row = fixed.invoice().items().getFirst();
        assertEquals(d("1305500"), row.unitPrice());
        assertEquals(d("1305500"), row.amount());
        assertEquals(d("104440"), row.taxAmount());
        assertEquals(d("1"), row.quantity());
        assertEquals(d("0.08"), row.taxRate());
        // The value before the change is kept; the repair is a warning, not a blocker.
        var note = fixed.notes().stream().filter(n -> n.field().equals("subtotal")).findFirst().orElseThrow();
        assertEquals("1305.5", note.rawValue());
        assertEquals("1305500", note.normalizedValue());
        assertEquals(MoneyScaleRepairs.NOTE_REASON, note.reason());
        assertEquals(6, fixed.warnings().stream().filter(w -> w.startsWith(MoneyScaleRepairs.REPAIRED)).count());
        assertFalse(fixed.warnings().stream().anyMatch(AssessmentWarnings::critical));
        assertEquals("subtotal", AssessmentWarnings.normalize(fixed.warnings().getFirst()).affectedField());
    }

    @Test
    void withoutPrintedEvidenceTotalsAreReportedAndNeverChanged() {
        var fixed = MoneyScaleRepairs.apply(result("1305.5", null, null, "1305.5", null, "1305.5"), "ảnh mờ, không đọc được số");
        assertEquals(d("1305.5"), fixed.invoice().subtotal());
        assertEquals(d("1305.5"), fixed.invoice().items().getFirst().amount());
        assertTrue(fixed.notes().isEmpty());
        // Only the invoice total is reported: row prices may legitimately have decimals.
        assertEquals(List.of("subtotal"), fixed.warnings().stream().map(AssessmentWarnings::normalize)
                .peek(w -> assertEquals("CRITICAL", w.severity())).map(w -> w.affectedField()).toList());
    }

    @Test
    void wholeAmountsAndForeignCurrencyAreLeftAlone() {
        var whole = result("1305500", "104440", "1409940", "1305500", "104440", "1305500");
        assertSame(whole, MoneyScaleRepairs.apply(whole, OCR));
        var usd = result("1305.5", null, null, "1305.5", null, "1305.5");
        usd = usd.with(usd.warnings(), usd.invoice(), usd.fields(), List.of(new AiExtraField("currency", "Tiền tệ", "USD", null)), usd.notes());
        assertSame(usd, MoneyScaleRepairs.apply(usd, "Total 1,305,500"));
    }

    @Test
    void evidenceMustBeTheWholeNumberNotPartOfAnotherOne() {
        assertTrue(MoneyScaleRepairs.printed("Tổng: 1.305.500 đ", "1305500"));
        assertTrue(MoneyScaleRepairs.printed("Tổng: 1305500", "1305500"));
        assertTrue(MoneyScaleRepairs.printed("Total 1,305,500.", "1305500"));
        assertFalse(MoneyScaleRepairs.printed("Tổng: 11.305.500", "1305500"));
        assertFalse(MoneyScaleRepairs.printed("Tổng: 1.305.5001", "1305500"));
        assertFalse(MoneyScaleRepairs.printed("Mã: 1.305.500,25", "1305500"));
        assertFalse(MoneyScaleRepairs.printed("", "1305500"));
    }
}
