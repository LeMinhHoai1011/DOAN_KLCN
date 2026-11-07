package com.example.invoice.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class InvoiceReconciliationRulesTest {
    @ParameterizedTest
    @CsvSource({
            "3008239, 3008238.66, MATCHED",
            "240659, 240659.12, MATCHED",
            "100, 100.99, MATCHED",
            "100, 99.01, MATCHED",
            "100, 101, MISMATCH",
            "100, 99, MISMATCH",
            "100, 105, MISMATCH",
    })
    void differencesUnderOneDongAreRoundingNotMismatches(BigDecimal declared, BigDecimal calculated, String status) {
        assertEquals(status, InvoiceReconciliationRules.status(declared, calculated, false));
    }

    @Test
    void lineCalculationsRoundToWholeDongFromHalfUp() {
        var checks = InvoiceReconciliationRules.evaluate(List.of(new InvoiceReconciliationRules.Row(new BigDecimal("12.63"),
                new BigDecimal("238182"), new BigDecimal("0.08"), new BigDecimal("240659"), new BigDecimal("3008239"))),
                new BigDecimal("3008239"), new BigDecimal("240659"), new BigDecimal("3248898"));
        assertEquals(new BigDecimal("3008239.00"), checks.get(0).calculated());
        assertEquals(new BigDecimal("240659.00"), checks.get(1).calculated());
        assertEquals(new BigDecimal("1502.00"), InvoiceReconciliationRules.toDong(new BigDecimal("1501.50")));
        assertEquals(new BigDecimal("1501.00"), InvoiceReconciliationRules.toDong(new BigDecimal("1501.49")));
        assertEquals(new BigDecimal("-1502.00"), InvoiceReconciliationRules.toDong(new BigDecimal("-1501.50")));
    }

    @Test
    void missingDataStaysInsufficientWhateverTheDifference() {
        assertEquals(InvoiceReconciliationRules.INSUFFICIENT_DATA, InvoiceReconciliationRules.status(null, BigDecimal.ONE, false));
        assertEquals(InvoiceReconciliationRules.INSUFFICIENT_DATA, InvoiceReconciliationRules.status(BigDecimal.ONE, BigDecimal.ONE, true));
    }
}
