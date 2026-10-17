package com.example.invoice.ai;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AssessmentWarningsTest {
    @Test void criticalCannotBeDowngradedBecauseMessageMentionsDiacritics() {
        assertTrue(AssessmentWarnings.critical("CRITICAL: mất dấu làm sai số tiền"));
        assertFalse(AssessmentWarnings.critical("NON_CRITICAL: mất dấu địa chỉ"));
        assertEquals("INFO", AssessmentWarnings.normalize("DOCUMENT_TYPE_REASON: Có số hóa đơn").severity());
    }
    @Test void warningsIdentifyTheirBusinessField() {
        assertEquals("items", AssessmentWarnings.normalize("INVOICE_ITEMS_MISSING: thiếu dòng hàng").affectedField());
        assertEquals("totalAmount", AssessmentWarnings.normalize("TOTAL_MISMATCH: sai tổng").affectedField());
    }
}
