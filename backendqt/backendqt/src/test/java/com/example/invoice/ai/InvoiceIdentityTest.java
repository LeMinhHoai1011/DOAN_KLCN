package com.example.invoice.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class InvoiceIdentityTest {
    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "0001011 | HÓA ĐƠN GIÁ TRỊ GIA TĂNG Ký hiệu: 1C25TAP Số: 00001011 | 00001011",
            "36      | Số (No).:00000036 Ký hiệu (Serial): 1C26TPL | 00000036",
            "000141  | No.: 00000141 | 00000141",
            "1011    | Số tài khoản: 3839886688 Số: 1011 | 1011",
            "1011    | Mã tra cứu 00001011 không có nhãn | 1011",
            "141     | Số: 100141 | 141",
            "AB12    | Số: 00AB12 | AB12",
    })
    void restoresOnlyTheLeadingZerosPrintedAfterTheNumberLabel(String extracted, String source, String expected) {
        assertEquals(expected, InvoiceIdentity.restoreNumber(extracted, source));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', nullValues = "NULL", value = {
            "NULL    | HÓA ĐƠN Ký hiệu: 1C25TAP Số: 00001011 | 1C25TAP",
            "NULL    | Ký hiệu (Serial): 1C26TPL Số (No).:00000036 | 1C26TPL",
            "1C25TVE | Ký hiệu: 1C25TAP | 1C25TVE",
            "NULL    | Không có nhãn 1C25TAP | NULL",
            "NULL    | Ký hiệu: không rõ | NULL",
    })
    void fillsABlankSeriesFromThePrintedLabel(String extracted, String source, String expected) {
        assertEquals(expected, InvoiceIdentity.restoreSeries(extracted, source));
    }

    @Test
    void comparesInvoicesWithoutLeadingZeros() {
        assertTrue(InvoiceIdentity.sameInvoice("00036", "1C26TPL", "00000036", "1c26tpl"));
        assertTrue(InvoiceIdentity.sameInvoice("000475", null, "00000475", "1C25TAN"));
        assertFalse(InvoiceIdentity.sameInvoice("00036", "1C26TPL", "00036", "1C25TPL"));
        assertFalse(InvoiceIdentity.sameInvoice("00036", "A", "00037", "A"));
        assertFalse(InvoiceIdentity.sameInvoice(null, "A", null, "A"));
    }
}
