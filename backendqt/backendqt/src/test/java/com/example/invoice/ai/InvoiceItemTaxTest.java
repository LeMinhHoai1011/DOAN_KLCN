package com.example.invoice.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.invoice.dto.ai.AiDocumentResult.AiInvoiceExtraction;
import com.example.invoice.dto.ai.AiDocumentResult.AiInvoiceItemExtraction;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class InvoiceItemTaxTest {
    private static final BigDecimal HEADER_TAX = new BigDecimal("576245");
    private static final BigDecimal RATE = new BigDecimal("0.08");

    private static AiInvoiceItemExtraction row(String amount, String tax, BigDecimal rate) {
        return new AiInvoiceItemExtraction("Ván", BigDecimal.ONE, "Tấm", new BigDecimal(amount), rate,
                tax == null ? null : new BigDecimal(tax), new BigDecimal(amount));
    }

    private static AiInvoiceItemExtraction row(String amount, String tax) { return row(amount, tax, RATE); }

    private static AiInvoiceExtraction invoice(AiInvoiceItemExtraction... rows) {
        return new AiInvoiceExtraction("1", "A", "2026-01-01", "Bán", "1", "HN", "Mua", "2", "HN",
                new BigDecimal("7203061"), HEADER_TAX, new BigDecimal("7779306"), List.of(rows));
    }

    @Test
    void headerTaxCopiedOntoEveryRowBecomesAmountTimesRateAndSumsToTheHeader() {
        AiInvoiceItemExtraction[] rows = {row("5444867", "576245"), row("1603861", "576245"), row("154333", "576245")};
        assertTrue(InvoiceItemTax.rowTaxUnreliable(invoice(rows)));
        assertEquals(new BigDecimal("435589.00"), InvoiceItemTax.rowTax(rows[0], RATE, true));
        assertEquals(new BigDecimal("128309.00"), InvoiceItemTax.rowTax(rows[1], RATE, true));
        assertEquals(new BigDecimal("12347.00"), InvoiceItemTax.rowTax(rows[2], RATE, true));
    }

    @Test
    void guessedRowTaxThatMissesTheHeaderIsDerivedToo() {
        assertTrue(InvoiceItemTax.rowTaxUnreliable(invoice(row("5444867", "435589"), row("1603861", "128309"), row("154333", "123466"))));
    }

    @Test
    void rowTaxAddingUpToTheHeaderIsKept() {
        AiInvoiceItemExtraction printed = row("5444867", "435590");
        assertFalse(InvoiceItemTax.rowTaxUnreliable(invoice(printed, row("1603861", "128308"), row("154333", "12347"))));
        assertEquals(new BigDecimal("435590"), InvoiceItemTax.rowTax(printed, RATE, false));
    }

    @Test
    void aSingleRowMayLegitimatelyEqualTheHeader() {
        assertFalse(InvoiceItemTax.rowTaxUnreliable(invoice(row("7203061", "576245"))));
    }

    @Test
    void missingRateTakesTheOnlyRateOnTheInvoice() {
        AiInvoiceItemExtraction blank = row("1000000", "330957", null);
        AiInvoiceExtraction invoice = invoice(row("1440439", "144044"), blank);
        assertEquals(0, RATE.compareTo(InvoiceItemTax.rowRate(invoice, blank, true)));
        assertEquals(new BigDecimal("80000.00"), InvoiceItemTax.rowTax(blank, InvoiceItemTax.rowRate(invoice, blank, true), true));
        assertNull(InvoiceItemTax.rowRate(invoice, blank, false));
        assertNull(InvoiceItemTax.rowRate(invoice(row("1", "1"), row("2", "1", new BigDecimal("0.10")), blank), blank, true));
    }

    @Test
    void blankRowTaxIsDerivedAndStaysBlankWithoutARate() {
        assertEquals(new BigDecimal("8000.00"), InvoiceItemTax.rowTax(row("100000", null), RATE, false));
        assertNull(InvoiceItemTax.rowTax(row("100000", null, null), null, false));
    }
}
