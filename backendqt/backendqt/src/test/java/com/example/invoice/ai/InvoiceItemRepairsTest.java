package com.example.invoice.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.invoice.dto.ai.AiDocumentResult.AiInvoiceExtraction;
import com.example.invoice.dto.ai.AiDocumentResult.AiInvoiceItemExtraction;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Cases taken from the goods-row errors of benchmark run ai-full-001. */
class InvoiceItemRepairsTest {
    private static BigDecimal d(String value) { return value == null ? null : new BigDecimal(value); }

    private static AiInvoiceItemExtraction row(String name, String quantity, String unit, String price, String rate, String tax, String amount) {
        return new AiInvoiceItemExtraction(name, d(quantity), unit, d(price), d(rate), d(tax), d(amount));
    }

    private static AiInvoiceExtraction invoice(String subtotal, String vat, AiInvoiceItemExtraction... rows) {
        return new AiInvoiceExtraction("1", "1C25TAA", "2025-10-01", "Bán", "0312345678", null, null, null, null, null,
                d(subtotal), d(vat), null, null, null, null, null, List.of(rows));
    }

    @Test
    void movesTheUnitGluedToTheNameIntoAnEmptyUnitColumn() {
        List<String> warnings = new ArrayList<>();
        var fixed = InvoiceItemRepairs.splitUnit(row("Ống 27 x 1.6 x 4m Mét", "10", "", "15000", null, null, "150000"), warnings);
        assertEquals("Ống 27 x 1.6 x 4m", fixed.productName());
        assertEquals("Mét", fixed.unit());
        assertTrue(warnings.getFirst().startsWith("ITEM_UNIT_SPLIT"));
    }

    @Test
    void removesUnitAndQuantityGluedAtTheEndWhenTheUnitColumnIsFilled() {
        var fixed = InvoiceItemRepairs.splitUnit(row("Dây điện CV-2.5 Mét 200", "200", "Mét", "8715", null, null, "1743000"), new ArrayList<>());
        assertEquals("Dây điện CV-2.5", fixed.productName());
        assertEquals("Mét", fixed.unit());
        var sameUnit = InvoiceItemRepairs.splitUnit(row("Lưỡi cưa LU3D-0600 (300*3.2*30*96T) cái", "1", "Cái", "1", null, null, "1"), new ArrayList<>());
        assertEquals("Lưỡi cưa LU3D-0600 (300*3.2*30*96T)", sameUnit.productName());
    }

    @Test
    void leavesNamesAloneWhenTheTrailingWordIsNotSafelyAUnit() {
        List<String> warnings = new ArrayList<>();
        var shortName = row("Thép tấm", "1", null, "1", null, null, "1");
        assertEquals("Thép tấm", InvoiceItemRepairs.splitUnit(shortName, warnings).productName());
        var otherUnit = row("Dây điện CV 4 Mét", "1", "Cuộn", "1", null, null, "1");
        assertEquals("Dây điện CV 4 Mét", InvoiceItemRepairs.splitUnit(otherUnit, warnings).productName());
        var dimension = row("Dây cáp 2 x 1.5", "3", "", "1", null, null, "1");
        assertEquals("Dây cáp 2 x 1.5", InvoiceItemRepairs.splitUnit(dimension, warnings).productName());
        assertTrue(warnings.isEmpty());
    }

    @Test
    void keepsTheExistingShiftedColumnRule() {
        var fixed = InvoiceItemRepairs.splitUnit(row("Kính 10 ly m2", "12.63", "238182", null, null, null, "3008239"), new ArrayList<>());
        assertEquals("Kính 10 ly", fixed.productName());
        assertEquals("m2", fixed.unit());
    }

    @Test
    void mergesNameOnlyRowsIntoThePreviousRow() {
        List<String> warnings = new ArrayList<>();
        var rows = InvoiceItemRepairs.mergeWrappedRows(List.of(
                row("Ván LMR 8 mm x 1220 x 2440", "2", "Tấm", "244444.5", null, null, "488889"),
                row("phủ Melamine 2 mặt 125MW", null, null, null, null, null, null),
                row("Nẹp nhôm", "1", "Thanh", "1000", null, null, "1000")), warnings);
        assertEquals(2, rows.size());
        assertEquals("Ván LMR 8 mm x 1220 x 2440 phủ Melamine 2 mặt 125MW", rows.getFirst().productName());
        assertEquals(d("488889"), rows.getFirst().amount());
        assertTrue(warnings.getFirst().startsWith("ITEM_ROWS_MERGED"));
    }

    @Test
    void scalesThousandsDotMisreadsOnlyWhenTheSubtotalConfirms() {
        List<String> warnings = new ArrayList<>();
        var rows = InvoiceItemRepairs.repairScale(List.of(
                row("A", "1", "Cái", "185.185", null, null, "185.185"),
                row("B", "2", "Cái", "100000", null, null, "200000")), d("385185"), warnings);
        assertEquals(0, rows.get(0).amount().compareTo(d("185185")));
        assertEquals(0, rows.get(0).unitPrice().compareTo(d("185185")));
        assertEquals(0, rows.get(1).amount().compareTo(d("200000")));
        assertTrue(warnings.getFirst().startsWith("ITEM_SCALE_REPAIRED"));

        List<String> none = new ArrayList<>();
        var genuine = List.of(row("Cà phê", "1.5", "kg", "120.5", null, null, "180.75"));
        assertEquals(genuine, InvoiceItemRepairs.repairScale(genuine, d("180.75"), none));
        assertEquals(genuine, InvoiceItemRepairs.repairScale(genuine, null, none));
        assertTrue(none.isEmpty());
    }

    @Test
    void scalesQuantityReadAsThousandsWithTheAmount() {
        var rows = InvoiceItemRepairs.repairScale(List.of(row("Vít", "50000", "Con", "3.592", null, null, "179.629")),
                d("179629"), new ArrayList<>());
        assertEquals(0, rows.getFirst().amount().compareTo(d("179629")));
        assertEquals(0, rows.getFirst().unitPrice().compareTo(d("3592")));
        assertEquals(0, rows.getFirst().quantity().compareTo(d("50")));
    }

    @Test
    void clearsRowTaxCopiedFromTheInvoiceTotalWithoutInventingValues() {
        List<String> warnings = new ArrayList<>();
        List<com.example.invoice.dto.ai.AiDocumentResult.AiFieldNote> notes = new ArrayList<>();
        var fixed = InvoiceItemRepairs.apply(invoice("4282500", "342600",
                row("A", "1", "Cái", "4102500", "0.08", "342600", "4102500"),
                row("B", "1", "Cái", "180000", null, "342600", "180000")), warnings, notes);
        assertNull(fixed.items().get(0).taxAmount());
        assertNull(fixed.items().get(1).taxRate(), "a rate is never borrowed from another row");
        assertNull(fixed.items().get(1).taxAmount());
        assertTrue(warnings.stream().anyMatch(w -> w.startsWith("ITEM_ROW_TAX_CLEARED")));
        var first = notes.stream().filter(n -> "taxAmount".equals(n.field()) && n.row() == 1).findFirst().orElseThrow();
        assertEquals("342600", first.rawValue());
        assertEquals("328200", new BigDecimal(first.normalizedValue()).stripTrailingZeros().toPlainString());
    }

    @Test
    void keepsRowTaxThatMissesTheTotalAndOnlySuggestsAValue() {
        List<String> warnings = new ArrayList<>();
        List<com.example.invoice.dto.ai.AiDocumentResult.AiFieldNote> notes = new ArrayList<>();
        var fixed = InvoiceItemRepairs.apply(invoice("4282500", "342600",
                row("A", "1", "Cái", "4102500", "0.08", "328000", "4102500"),
                row("B", "1", "Cái", "180000", "0.08", "10000", "180000")), warnings, notes);
        assertEquals(0, fixed.items().get(0).taxAmount().compareTo(d("328000")));
        assertEquals(0, fixed.items().get(1).taxAmount().compareTo(d("10000")));
        assertTrue(warnings.stream().anyMatch(w -> w.startsWith("ITEM_ROW_TAX_TOTAL_MISMATCH")));
        assertEquals("CRITICAL", AssessmentWarnings.normalize(warnings.getLast()).severity());
        assertTrue(notes.stream().anyMatch(n -> "SUGGESTED".equals(n.status()) && n.row() == 2));
    }

    @Test
    void nearMatchScaleIsOnlySuggested() {
        List<String> warnings = new ArrayList<>();
        List<com.example.invoice.dto.ai.AiDocumentResult.AiFieldNote> notes = new ArrayList<>();
        var rows = InvoiceItemRepairs.repairScale(List.of(row("Sơn", "3000", "Thùng", "444444.44", null, null, "1293333")),
                null, warnings, notes);
        assertEquals(0, rows.getFirst().quantity().compareTo(d("3000")));
        assertTrue(warnings.getFirst().startsWith("ITEM_SCALE_SUSPECT"));
        assertEquals("SUGGESTED", notes.getFirst().status());
        assertEquals("3", notes.getFirst().normalizedValue());
    }

    @Test
    void exactScaleRepairKeepsTheValueBeforeTheChange() {
        List<String> warnings = new ArrayList<>();
        List<com.example.invoice.dto.ai.AiDocumentResult.AiFieldNote> notes = new ArrayList<>();
        var rows = InvoiceItemRepairs.repairScale(List.of(row("Vít", "3", "Con", "80000000", null, null, "240000")),
                null, warnings, notes);
        assertEquals(0, rows.getFirst().unitPrice().compareTo(d("80000")));
        assertEquals("80000000", notes.getFirst().rawValue());
        assertEquals("REPAIRED", notes.getFirst().status());
    }

    /** Rows exactly as qwen3-vl returned them for invoice 1C25TYY/00001583 (column "Tiền chiết khấu", quantity "3,000"). */
    @Test
    void discountReadIntoTheTaxColumnIsMovedAndTheThousandfoldQuantityIsSetBack() {
        List<String> warnings = new ArrayList<>();
        List<com.example.invoice.dto.ai.AiDocumentResult.AiFieldNote> notes = new ArrayList<>();
        var fixed = InvoiceItemRepairs.apply(invoice(null, "174600",
                row("Ván LMR 17 mm x 1220 x 2440 phủ Melamine 2 mặt 01T Tấm", "3000", null, "444444.44", "0.08", "40000", "1293333"),
                row("Ván LMR 8 mm x 1220 x 2440 phủ Melamine 2 mặt trắng 01T Tấm", "3000", null, "305555.56", "0.08", "27500", "889167")),
                warnings, notes);
        for (var item : fixed.items()) {
            assertEquals(0, item.quantity().compareTo(d("3")));
            assertNull(item.taxAmount(), "a discount is not a row tax");
            assertEquals(0, item.taxRate().compareTo(d("0.08")));
        }
        assertEquals(0, fixed.items().get(0).amount().compareTo(d("1293333")), "the printed amount is never changed");
        assertEquals("Tấm", fixed.items().get(0).unit());
        var discounts = InvoiceItemRepairs.discounts(notes);
        assertEquals(0, discounts.get(1).compareTo(d("40000")));
        assertEquals(0, discounts.get(2).compareTo(d("27500")));
        assertTrue(notes.stream().anyMatch(n -> "quantity".equals(n.field()) && "3000".equals(n.rawValue()) && "3".equals(n.normalizedValue())));
        assertTrue(warnings.stream().anyMatch(w -> w.startsWith("ITEM_DISCOUNT_IN_TAX_COLUMN")));
        assertEquals("WARNING", AssessmentWarnings.normalize(
                warnings.stream().filter(w -> w.startsWith("ITEM_DISCOUNT")).findFirst().orElseThrow()).severity());
    }

    /** Invoice 1C25THP/1843: no row tax column, VAT 8% printed once; the model copied each "Thành tiền" into taxAmount. */
    @Test
    void rowTaxCopiedFromTheRowAmountIsClearedAndTaxStaysAtInvoiceLevel() {
        List<String> warnings = new ArrayList<>();
        List<com.example.invoice.dto.ai.AiDocumentResult.AiFieldNote> notes = new ArrayList<>();
        var fixed = InvoiceItemRepairs.apply(invoice("451151", "36092",
                row("Công tắc 1 chiều S30/1/2M", "5", "Cái", "6937", "0.08", "34685", "34685"),
                row("Mặt nạ 2 S182/x", "1", "Cái", "7875", "0.08", "7875", "7875"),
                row("Mặt nạ 3 S183/X", "1", "Cái", "7875", "0.08", "7875", "7875"),
                row("Đế nổi chữ nhật CK157RH", "12", "Cái", "4623", "0.08", "55476", "55476"),
                row("Ổ cắm 3-2 chấu - S18U3", "10", "Cái", "34524", "0.08", "345240", "345240")), warnings, notes);
        for (var item : fixed.items()) {
            assertNull(item.taxAmount(), "a row amount is not a row tax");
            assertEquals(0, item.taxRate().compareTo(d("0.08")));
        }
        assertEquals(0, fixed.items().get(0).amount().compareTo(d("34685")));
        assertEquals(0, fixed.items().get(0).quantity().compareTo(d("5")));
        var first = notes.stream().filter(n -> "taxAmount".equals(n.field()) && n.row() == 1).findFirst().orElseThrow();
        assertEquals("34685", first.rawValue());
        assertEquals("2775", first.normalizedValue(), "amount × rate is only a suggestion");
        assertTrue(warnings.stream().anyMatch(w -> w.startsWith("ITEM_ROW_TAX_CLEARED")));
        assertTrue(warnings.stream().noneMatch(w -> w.startsWith("ITEM_ROW_TAX_TOTAL_MISMATCH") || w.startsWith("ITEM_DISCOUNT")));
    }

    /** The invoice in the review screen: "2,000 | 444.444,44 | 177.778 | 711.111". */
    @Test
    void discountWithTwoUnitsIsRecognisedToo() {
        var fixed = InvoiceItemRepairs.apply(invoice(null, null,
                row("Ván LMR 17 mm x 1220 x 2440 phủ Melamine 2 mặt 125MW", "2000", "Tấm", "444444.44", "0.08", "177778", "711111"),
                row("Ván LMR 8 mm x 1220 x 2440 phủ Melamine 2 mặt 125MW", "2000", "Tấm", "305555.56", "0.08", "122222", "488889")),
                new ArrayList<>(), new ArrayList<>());
        assertEquals(0, fixed.items().get(0).quantity().compareTo(d("2")));
        assertNull(fixed.items().get(1).taxAmount());
    }

    @Test
    void realRowTaxesAndUnexplainedValuesAreLeftAlone() {
        // amount × rate: a real row tax.
        var taxed = InvoiceItemRepairs.apply(invoice("1000000", "80000",
                row("A", "1", "Cái", "1000000", "0.08", "80000", "1000000")), new ArrayList<>(), new ArrayList<>());
        assertEquals(0, taxed.items().getFirst().taxAmount().compareTo(d("80000")));
        // quantity × price − value is not the amount: no evidence, nothing moved.
        List<com.example.invoice.dto.ai.AiDocumentResult.AiFieldNote> notes = new ArrayList<>();
        var unexplained = InvoiceItemRepairs.apply(invoice(null, null,
                row("B", "3000", "Tấm", "444444.44", "0.08", "40000", "1200000")), new ArrayList<>(), notes);
        assertEquals(0, unexplained.items().getFirst().quantity().compareTo(d("3000")));
        assertEquals(0, unexplained.items().getFirst().taxAmount().compareTo(d("40000")));
        assertTrue(InvoiceItemRepairs.discounts(notes).isEmpty());
        // Row values that add up to the printed tax total are taxes, whatever else they match.
        var summing = InvoiceItemRepairs.apply(invoice(null, "40000",
                row("C", "3", "Tấm", "444444.44", "0.08", "40000", "1293333")), new ArrayList<>(), new ArrayList<>());
        assertEquals(0, summing.items().getFirst().taxAmount().compareTo(d("40000")));
    }

    @Test
    void aDiscountReturnedInItsOwnFieldIsUsedForTheScaleCheck() {
        List<com.example.invoice.dto.ai.AiDocumentResult.AiFieldNote> notes = new ArrayList<>(List.of(
                new com.example.invoice.dto.ai.AiDocumentResult.AiFieldNote("discountAmount", 1, "40.000", "40000", "NORMALIZED", "ROW_DISCOUNT")));
        List<String> warnings = new ArrayList<>();
        var rows = InvoiceItemRepairs.repairScale(List.of(row("Ván", "3000", "Tấm", "444444.44", "0.08", null, "1293333")),
                null, warnings, notes);
        assertEquals(0, rows.getFirst().quantity().compareTo(d("3")));
        assertTrue(warnings.getFirst().startsWith("ITEM_SCALE_REPAIRED"));
    }

    /** Benchmark sample S_a527ce…: the model lost a row and kept only its wrapped second line. */
    @Test
    void aTextLineIsNotMergedIntoARowWhoseNameAlreadyEndsWithItsUnit() {
        List<String> warnings = new ArrayList<>();
        var rows = InvoiceItemRepairs.mergeWrappedRows(List.of(
                row("Chỉ 122 (21 x 0.8) mm Cuộn", "1000", "Cuộn", "185.185", "0.08", "5.556", "179.629"),
                row("phủ Melamine 2 mặt 163MM", null, null, null, null, null, null)), warnings);
        assertEquals(2, rows.size());
        assertEquals("Chỉ 122 (21 x 0.8) mm Cuộn", rows.getFirst().productName());
        assertTrue(warnings.isEmpty());
    }

    @Test
    void promotionLinesAreNotMergedIntoThePreviousName() {
        List<String> warnings = new ArrayList<>();
        var rows = InvoiceItemRepairs.mergeWrappedRows(List.of(
                row("Nước suối", "2", "Chai", "5000", null, null, "10000"),
                row("Hàng khuyến mại không thu tiền", null, null, null, null, null, null)), warnings);
        assertEquals(2, rows.size());
        assertTrue(warnings.isEmpty());
    }

    @Test
    void cleanRowsAndRepairWarningsAreNotCritical() {
        List<String> warnings = new ArrayList<>();
        var rows = invoice("100000", "8000", row("Bút bi", "10", "Cây", "10000", "0.08", "8000", "100000"));
        assertEquals(rows.items(), InvoiceItemRepairs.apply(rows, warnings).items());
        assertTrue(warnings.isEmpty());
        assertNull(InvoiceItemRepairs.apply(null, warnings));
        assertEquals("WARNING", AssessmentWarnings.normalize("ITEM_UNIT_SPLIT: x").severity());
        assertEquals("CRITICAL", AssessmentWarnings.normalize("ITEM_SOMETHING_ELSE: x").severity());
    }
}
