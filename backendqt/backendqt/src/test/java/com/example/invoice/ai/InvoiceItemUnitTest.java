package com.example.invoice.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class InvoiceItemUnitTest {
    @ParameterizedTest
    @CsvSource({
            "Kính 10 ly trắng cường lực m2, 'Kính 10 ly trắng cường lực', m2, '12,63'",
            "Nhôm dẻo Mét, 'Nhôm dẻo', Mét, '15.000'",
            "Nguồn 12v 300w Bộ, 'Nguồn 12v 300w', Bộ, '120.000'",
    })
    void numericUnitTakesTheUnitGluedToTheName(String name, String expectedName, String expectedUnit, String unit) {
        assertEquals(new InvoiceItemUnit.NameAndUnit(expectedName, expectedUnit), InvoiceItemUnit.repair(name, unit));
    }

    @Test
    void numericUnitIsDroppedWhenTheNameHasNoUnit() {
        assertEquals(new InvoiceItemUnit.NameAndUnit("Nguồn 12v", null), InvoiceItemUnit.repair("Nguồn 12v", "3,00"));
        assertEquals(new InvoiceItemUnit.NameAndUnit("Xi", null), InvoiceItemUnit.repair("Xi", "3"));
    }

    @Test
    void realUnitsAreKept() {
        assertEquals(new InvoiceItemUnit.NameAndUnit("Kính cường lực m2", "m2"), InvoiceItemUnit.repair("Kính cường lực m2", "m2"));
        assertEquals(new InvoiceItemUnit.NameAndUnit("Dịch vụ", null), InvoiceItemUnit.repair("Dịch vụ", null));
    }
}
