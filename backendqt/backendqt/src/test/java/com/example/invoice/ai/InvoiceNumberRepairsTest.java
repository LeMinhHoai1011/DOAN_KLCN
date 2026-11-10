package com.example.invoice.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.example.invoice.dto.ai.AiDocumentResult;
import com.example.invoice.dto.ai.AiDocumentResult.AiInvoiceExtraction;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Case from benchmark run both-raster-001: OCR printed "Số (No.): 00004848", the model returned "0004848". */
class InvoiceNumberRepairsTest {
    private static final String OCR = "HÓA ĐƠN GIÁ TRỊ GIA TĂNG Ký hiệu (Serial): 1C25TAA Số (No.): 00004848 "
            + "Ngày 05 tháng 08 năm 2025 Tổng tiền thanh toán: 4.848.000";

    private static AiDocumentResult result(String number) {
        var invoice = new AiInvoiceExtraction(number, "1C25TAA", "2025-08-05", "NGUYEN HOANG GIANG", "0312345678", null, null,
                null, null, null, null, null, new BigDecimal("4848000"), null, null, null, null, List.of());
        return new AiDocumentResult("ollama", "qwen3-vl:8b", "VAT_INVOICE", new BigDecimal("0.99"), null, invoice, List.of(),
                List.of(), "raw", 1L);
    }

    @Test
    void restoresTheZeroPaddingPrintedAfterTheNumberLabel() {
        var fixed = InvoiceNumberRepairs.apply(result("0004848"), OCR);
        assertEquals("00004848", fixed.invoice().invoiceNumber());
        var note = fixed.notes().getFirst();
        assertEquals("0004848", note.rawValue());
        assertEquals(InvoiceNumberRepairs.NOTE_REASON, note.reason());
        var warning = AssessmentWarnings.normalize(fixed.warnings().getFirst());
        assertEquals("WARNING", warning.severity());
        assertEquals("invoiceNumber", warning.affectedField());
    }

    @Test
    void numberPrintedAsReturnedOrNotPrintedIsLeftAlone() {
        var exact = result("00004848");
        assertSame(exact, InvoiceNumberRepairs.apply(exact, OCR));
        var other = result("0005946");
        assertSame(other, InvoiceNumberRepairs.apply(other, OCR));
        var none = result("0004848");
        assertSame(none, InvoiceNumberRepairs.apply(none, "ảnh mờ"));
    }

    @Test
    void ambiguousOrNonNumericValuesAreNeverGuessed() {
        // Two different labelled paddings: no single printed form.
        assertNull(InvoiceNumberRepairs.printedForm("Số: 0004848 ... No.: 00004848", "4848"));
        // The amount 4.848.000 and a tax code containing the digits are not the number.
        assertNull(InvoiceNumberRepairs.printedForm("Tổng: 4.848.000 MST 0304848123", "04848"));
        assertNull(InvoiceNumberRepairs.printedForm("Số: 00000100", "100 IC25TTN"));
        // No label: one zero-padded form anywhere is enough.
        assertEquals("00000143", InvoiceNumberRepairs.printedForm("Ky hiéu (Serial No): C25TQL 00000143", "000143"));
    }
}
