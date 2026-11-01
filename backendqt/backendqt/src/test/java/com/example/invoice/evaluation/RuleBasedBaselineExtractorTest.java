package com.example.invoice.evaluation;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RuleBasedBaselineExtractorTest {
    private final RuleBasedBaselineExtractor extractor = new RuleBasedBaselineExtractor(null,
            Map.of("TRANSPORT", List.of("cước vận chuyển", "taxi"), "OFFICE", List.of("văn phòng phẩm")));

    @Test void extractsVatInvoiceFieldsWithDiacritics() {
        var p = extractor.extract("""
                HÓA ĐƠN GIÁ TRỊ GIA TĂNG
                Ký hiệu (Serial): 1C24TAA   Số (No.): 0000123
                Ngày 15 tháng 03 năm 2024
                Đơn vị bán hàng: CÔNG TY A
                Mã số thuế: 0101234567
                Họ tên người mua hàng: CÔNG TY B
                Mã số thuế: 0309876543-001
                1 Cước vận chuyển chuyến 1  1.000.000
                Cộng tiền hàng: 1.000.000
                Thuế suất GTGT: 10%  Tiền thuế GTGT: 100.000
                Tổng cộng tiền thanh toán: 1.100.000
                """);
        assertEquals("VAT_INVOICE", p.predictedType());
        assertEquals("TRANSPORT", p.predictedCategory());
        assertEquals("1C24TAA", p.fields().get("invoiceSeries"));
        assertEquals("0000123", p.fields().get("invoiceNumber"));
        assertEquals("2024-03-15", p.fields().get("invoiceDate"));
        assertEquals("0101234567", p.fields().get("sellerTaxCode"));
        assertEquals("0309876543-001", p.fields().get("buyerTaxCode"));
        assertEquals("1000000", p.fields().get("subtotal"));
        assertEquals("100000", p.fields().get("vatAmount"));
        assertEquals("1100000", p.fields().get("totalAmount"));
    }

    @Test void toleratesOcrThatLostDiacritics() {
        var p = extractor.extract("PHIEU CHI\nNgay 02/01/2025\nLy do: mua van phong pham\nTong tien: 250.000");
        assertEquals("PAYMENT_VOUCHER", p.predictedType());
        assertEquals("OFFICE", p.predictedCategory());
        assertEquals("2025-01-02", p.fields().get("invoiceDate"));
        assertEquals("250000", p.fields().get("totalAmount"));
    }

    @Test void amountsStayNextToTheirLabelWhenOcrDropsLineBreaks() {
        var p = extractor.extract("HÓA ĐƠN GIÁ TRỊ GIA TĂNG Cộng tiền hàng: 1.000.000 Tiền thuế GTGT: 100.000 Tổng cộng tiền thanh toán: 1.100.000 Số tài khoản 123456789");
        assertEquals("1000000", p.fields().get("subtotal"));
        assertEquals("100000", p.fields().get("vatAmount"));
        assertEquals("1100000", p.fields().get("totalAmount"));
    }

    @Test void returnsNothingRatherThanGuessing() {
        var p = extractor.extract("ảnh mờ không đọc được");
        assertNull(p.predictedType());
        assertNull(p.predictedCategory());
        assertTrue(p.fields().isEmpty());
        assertTrue(extractor.extract(null).fields().isEmpty());
    }
}
