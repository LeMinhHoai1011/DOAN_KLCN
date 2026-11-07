package com.example.invoice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class AiChangeComparisonTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    private static JsonNode snapshot(String invoice) throws Exception {
        return JSON.readTree("{\"classification\":{\"category\":\"HANG_HOA\"},\"invoice\":" + invoice + "}");
    }

    @Test
    void untouchedValuesCountAsCorrectAndNumbersCompareByValue() throws Exception {
        var ai = snapshot("{\"invoiceNumber\":\"00001\",\"subtotal\":\"100.00\",\"items\":[{\"productName\":\"Ván\",\"amount\":\"100.00\"}]}");
        var kept = snapshot("{\"invoiceNumber\":\"00001\",\"subtotal\":\"100\",\"items\":[{\"productName\":\"Ván\",\"amount\":\"100\"}]}");
        var result = AiChangeComparison.compare(ai, kept);
        assertEquals(5, result.compared());
        assertEquals(5, result.correct());
        assertTrue(result.changes().isEmpty());
    }

    @Test
    void reportsWrongMissingAndExtraValues() throws Exception {
        var ai = snapshot("{\"invoiceNumber\":\"1\",\"sellerPhone\":\"028\",\"items\":[]}");
        var kept = snapshot("{\"invoiceNumber\":\"2\",\"invoiceSeries\":\"1C25TAP\",\"items\":[]}");
        var changes = AiChangeComparison.compare(ai, kept).changes();
        assertEquals(List.of(
                new AiChangeComparison.Change("invoiceNumber", null, AiChangeComparison.WRONG, "1", "2"),
                new AiChangeComparison.Change("invoiceSeries", null, AiChangeComparison.MISSING, null, "1C25TAP"),
                new AiChangeComparison.Change("sellerPhone", null, AiChangeComparison.EXTRA, "028", null)), changes);
    }

    @Test
    void aRowTheModelDroppedIsMissingWithoutFailingTheRowsAfterIt() throws Exception {
        var ai = snapshot("{\"items\":[{\"productName\":\"A\",\"amount\":\"1\"},{\"productName\":\"C\",\"amount\":\"3\"}]}");
        var kept = snapshot("{\"items\":[{\"productName\":\"A\",\"amount\":\"1\"},{\"productName\":\"B\",\"amount\":\"2\"},{\"productName\":\"C\",\"amount\":\"3\"}]}");
        var changes = AiChangeComparison.compare(ai, kept).changes();
        assertEquals(2, changes.size());
        assertTrue(changes.stream().allMatch(change -> change.row() == 2 && AiChangeComparison.MISSING.equals(change.kind())));
    }

    @Test
    void aRowTheAccountantRemovedCountsAsExtra() throws Exception {
        var ai = snapshot("{\"items\":[{\"productName\":\"A\",\"amount\":\"1\"},{\"productName\":\"B\",\"amount\":\"2\"}]}");
        var kept = snapshot("{\"items\":[{\"productName\":\"A\",\"amount\":\"1\"}]}");
        var changes = AiChangeComparison.compare(ai, kept).changes();
        assertEquals(2, changes.size());
        assertTrue(changes.stream().allMatch(change -> AiChangeComparison.EXTRA.equals(change.kind())));
    }
}
