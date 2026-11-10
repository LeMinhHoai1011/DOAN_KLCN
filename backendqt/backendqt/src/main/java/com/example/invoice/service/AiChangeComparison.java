package com.example.invoice.service;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Compares what the system saved after AI with what the accountant left in place. A value the accountant kept counts
 * as correct; a changed, added or removed value counts against AI. No score reported by the model is used.
 */
public final class AiChangeComparison {
    private AiChangeComparison() {}

    public static final String WRONG = "WRONG", MISSING = "MISSING", EXTRA = "EXTRA";
    private static final List<String> HEADER = List.of("invoiceNumber", "invoiceSeries", "invoiceDate", "signDate", "sellerName",
            "sellerTaxCode", "sellerAddress", "sellerPhone", "buyerName", "buyerTaxCode", "buyerAddress", "subtotal", "vatAmount",
            "totalAmount", "paymentMethod", "amountInWords", "taxAuthorityCode");
    private static final List<String> CLASSIFICATION = List.of("predictedLabel", "category", "accountingAccount");
    private static final List<String> ITEM = List.of("productName", "quantity", "unit", "unitPrice", "taxRate", "taxAmount", "amount");

    /** Field groups reported separately: invoice header, goods rows, classification. */
    public static final String GROUP_HEADER = "HEADER", GROUP_ITEMS = "ITEMS", GROUP_CLASSIFICATION = "CLASSIFICATION";

    public record Change(String field, Integer row, String kind, String aiValue, String finalValue) {}
    /** {@code comparedByGroup}: values compared per {@link #group(String)}. */
    public record Result(int compared, List<Change> changes, java.util.Map<String, Integer> comparedByGroup) {
        public Result(int compared, List<Change> changes) { this(compared, changes, java.util.Map.of()); }
        public int correct() { return compared - changes.size(); }
    }

    public static String group(String field) {
        if (field != null && field.startsWith("items.")) return GROUP_ITEMS;
        return CLASSIFICATION.contains(field) ? GROUP_CLASSIFICATION : GROUP_HEADER;
    }

    private static final class Tally {
        int compared;
        final List<Change> changes = new ArrayList<>();
        final java.util.Map<String, Integer> byGroup = new java.util.LinkedHashMap<>();

        void compare(String field, Integer row, JsonNode ai, JsonNode kept) {
            String left = text(ai), right = text(kept);
            if (left == null && right == null) return;
            compared++;
            byGroup.merge(group(field), 1, Integer::sum);
            if (same(left, right)) return;
            changes.add(new Change(field, row, left == null ? MISSING : right == null ? EXTRA : WRONG, left, right));
        }
    }

    public static Result compare(JsonNode ai, JsonNode kept) {
        Tally tally = new Tally();
        for (String field : CLASSIFICATION) tally.compare(field, null, ai.path("classification").path(field), kept.path("classification").path(field));
        for (String field : HEADER) tally.compare(field, null, ai.path("invoice").path(field), kept.path("invoice").path(field));
        compareItems(tally, ai.path("invoice").path("items"), kept.path("invoice").path("items"));
        return new Result(tally.compared, List.copyOf(tally.changes), java.util.Map.copyOf(tally.byGroup));
    }

    /** Rows pair by name first, so one inserted row is not reported as every later row being wrong. */
    private static void compareItems(Tally tally, JsonNode aiItems, JsonNode keptItems) {
        boolean[] used = new boolean[aiItems.size()];
        int[] partner = new int[keptItems.size()];
        for (int k = 0; k < keptItems.size(); k++) {
            partner[k] = -1;
            for (int a = 0; a < aiItems.size() && partner[k] < 0; a++)
                if (!used[a] && same(text(aiItems.get(a).path("productName")), text(keptItems.get(k).path("productName")))) {
                    partner[k] = a; used[a] = true;
                }
        }
        for (int k = 0; k < keptItems.size(); k++) {
            for (int a = 0; a < aiItems.size() && partner[k] < 0; a++)
                if (!used[a]) { partner[k] = a; used[a] = true; }
            JsonNode kept = keptItems.get(k);
            JsonNode ai = partner[k] < 0 ? com.fasterxml.jackson.databind.node.MissingNode.getInstance() : aiItems.get(partner[k]);
            for (String field : ITEM) tally.compare("items." + field, k + 1, ai.path(field), kept.path(field));
        }
        for (int a = 0; a < aiItems.size(); a++)
            if (!used[a]) for (String field : ITEM)
                tally.compare("items." + field, a + 1, aiItems.get(a).path(field), com.fasterxml.jackson.databind.node.MissingNode.getInstance());
    }

    private static String text(JsonNode value) {
        if (value == null || value.isMissingNode() || value.isNull()) return null;
        String text = value.asText().trim().replaceAll("\\s+", " ");
        return text.isEmpty() ? null : text;
    }

    private static boolean same(String left, String right) {
        if (left == null || right == null) return left == null && right == null;
        if (left.equals(right)) return true;
        try { return new BigDecimal(left).compareTo(new BigDecimal(right)) == 0; }
        catch (NumberFormatException notNumbers) { return false; }
    }
}
