package com.example.invoice.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.example.invoice.ai.InvoiceItemScale.QuantityAndPrice;
import java.math.BigDecimal;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class InvoiceItemScaleTest {
    private static void assertRepair(String quantity, String price, String amount, String expectedQuantity, String expectedPrice) {
        QuantityAndPrice repaired = InvoiceItemScale.repair(new BigDecimal(quantity), new BigDecimal(price), new BigDecimal(amount));
        assertEquals(0, new BigDecimal(expectedQuantity).compareTo(repaired.quantity()));
        assertEquals(0, new BigDecimal(expectedPrice).compareTo(repaired.unitPrice()));
    }

    @ParameterizedTest
    @CsvSource({
            "50000, 700, 35000, 50, 700",
            "3, 80000000, 240000, 3, 80000",
            "12, 45000000, 540000, 12, 45000",
    })
    void aThousandfoldFactorIsScaledDownToMatchThePrintedAmount(String quantity, String price, String amount, String q, String p) {
        assertRepair(quantity, price, amount, q, p);
    }

    @ParameterizedTest
    @CsvSource({
            "12.63, 238182, 3008239",   // already consistent
            "3, 330957.41, 1440439",    // a different extraction error, not a scale error
            "2000, 30000, 60000",       // either factor could be wrong
    })
    void otherRowsAreLeftForReview(String quantity, String price, String amount) {
        assertRepair(quantity, price, amount, quantity, price);
    }
}
