package com.example.invoice.evaluation;

import com.example.invoice.ai.InvoiceNumberFormat;
import com.example.invoice.evaluation.LiveEvaluationData.Label;
import com.example.invoice.evaluation.LiveEvaluationData.Sample;
import com.example.invoice.evaluation.LiveEvaluationData.State;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Turns a confirmed label and a stored AI run into the benchmark records, so both share one scorer. */
final class LiveEvaluationScoring {
    private LiveEvaluationScoring() {}
    private static final Set<String> NUMBERS = Set.of("subtotal", "vatAmount", "totalAmount", "quantity", "unitPrice", "taxAmount", "amount");
    private static final Set<String> TAX_CODES = Set.of("sellerTaxCode", "buyerTaxCode");
    private static final Set<String> RECONCILIATION_INPUTS = Set.of("subtotal", "vatAmount", "totalAmount");
    private static final List<DateTimeFormatter> DATES = List.of(DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("d/M/uuuu"), DateTimeFormatter.ofPattern("d-M-uuuu"), DateTimeFormatter.ofPattern("d.M.uuuu"));

    private enum Source { HUMAN, AI }

    /** Same value, same text: "180.000" typed by a person equals 180000 from the model. */
    static String canonical(String field, String value) { return canonical(field, value, Source.HUMAN); }

    private static String canonical(String field, String value, Source source) {
        String text = value.strip();
        if (NUMBERS.contains(field)) { BigDecimal number = number(text, source); return number == null ? text : plain(number); }
        if ("taxRate".equals(field)) return rate(text, source);
        if ("invoiceDate".equals(field)) return date(text);
        if (TAX_CODES.contains(field)) return text.replaceAll("\\s+", "");
        return text;
    }

    /** AI values are machine numbers: read as Vietnamese text, a quantity of 1.125 would become 1125. */
    private static BigDecimal number(String text, Source source) {
        try { return source == Source.AI ? new BigDecimal(text) : InvoiceNumberFormat.parse(text); }
        catch (IllegalArgumentException unreadable) { return null; }
    }

    /** Decimal comma keeps the result unambiguous for InvoiceNumberFormat in reconciliation. */
    private static String plain(BigDecimal number) { return number.stripTrailingZeros().toPlainString().replace('.', ','); }

    private static String rate(String text, Source source) {
        boolean percent = text.endsWith("%");
        BigDecimal number = number(percent ? text.substring(0, text.length() - 1).strip() : text, source);
        if (number == null) return text;
        return plain(percent || number.abs().compareTo(BigDecimal.ONE) > 0 ? number : number.movePointRight(2)) + "%";
    }

    private static String date(String text) {
        for (var format : DATES) {
            try { return LocalDate.parse(text, format).toString(); }
            catch (DateTimeParseException ignored) { /* Try the next printed format. */ }
        }
        return text;
    }

    private static List<Map<String, String>> rows(List<Map<String, String>> rows, Source source) {
        var result = new ArrayList<Map<String, String>>();
        for (var row : rows) {
            var cells = new LinkedHashMap<String, String>();
            row.forEach((key, value) -> { if (value != null && !value.isBlank()) cells.put(key, canonical(key, value, source)); });
            result.add(cells);
        }
        return result;
    }

    private static boolean unreadable(Label label) { return label != null && label.state() == State.UNREADABLE; }

    static Map<String, String> truthFields(Sample sample) {
        var fields = new LinkedHashMap<String, String>();
        sample.getFields().forEach((key, label) -> { if (label.state() == State.PRESENT) fields.put(key, canonical(key, label.value())); });
        return fields;
    }

    static List<Map<String, String>> truthItems(Sample sample) {
        return switch (sample.getItemsState()) {
            case PRESENT -> rows(sample.getItems(), Source.HUMAN);
            case ABSENT -> List.of();
            default -> null;
        };
    }

    /** Null when the labels cannot support a fair check: retail rules need details, unreadable money has no answer. */
    static Map<String, String> reconciliationDraft(Sample sample) {
        if (EvaluationService.RETAIL_INVOICE.equals(sample.getDocumentType()) || sample.getItemsState() == State.UNREADABLE
                || RECONCILIATION_INPUTS.stream().anyMatch(key -> unreadable(sample.getFields().get(key)))) return null;
        var items = truthItems(sample);
        return EvaluationReconciliation.statuses(truthFields(sample), items);
    }

    /**
     * Order-preserving match, so one row the AI skipped does not shift every later row into a mismatch.
     * Result: one entry per truth row (empty when missing), then rows matching nothing, which score as extra.
     */
    static List<Map<String, String>> align(List<Map<String, String>> truth, List<Map<String, String>> predicted) {
        int m = truth.size(), n = predicted.size();
        int[][] best = new int[m + 1][n + 1];
        for (int i = m - 1; i >= 0; i--) for (int j = n - 1; j >= 0; j--) {
            int same = sameCells(truth.get(i), predicted.get(j));
            best[i][j] = Math.max(Math.max(best[i + 1][j], best[i][j + 1]), same == 0 ? 0 : same + best[i + 1][j + 1]);
        }
        var aligned = new ArrayList<Map<String, String>>();
        var extra = new ArrayList<Map<String, String>>();
        int i = 0, j = 0;
        while (i < m && j < n) {
            int same = sameCells(truth.get(i), predicted.get(j));
            if (same > 0 && best[i][j] == same + best[i + 1][j + 1]) { aligned.add(predicted.get(j++)); i++; }
            else if (best[i + 1][j] >= best[i][j + 1]) { aligned.add(Map.of()); i++; }
            else extra.add(predicted.get(j++));
        }
        while (i++ < m) aligned.add(Map.of());
        while (j < n) extra.add(predicted.get(j++));
        aligned.addAll(extra);
        return aligned;
    }

    private static int sameCells(Map<String, String> truth, Map<String, String> predicted) {
        return (int) truth.entrySet().stream().filter(cell -> Objects.equals(cell.getValue(), predicted.get(cell.getKey()))).count();
    }

    static EvaluationData.Sample toSample(Sample sample) {
        boolean category = sample.getCategory().state() == State.PRESENT;
        return new EvaluationData.Sample("document-" + sample.getDocumentId(), null, sample.getDocumentType(), List.of(),
                sample.getConfirmedBy(), sample.getConfirmedAt(), sample.getOcrText(), truthFields(sample), null, truthItems(sample),
                category ? "company-" + sample.getCompanyId() : null, category ? sample.getCategory().value() : null,
                sample.getReconciliation());
    }

    static EvaluationData.Result toResult(Sample sample, LivePrediction prediction) {
        var fields = new LinkedHashMap<String, String>();
        prediction.fields().forEach((key, value) -> {
            // Nothing can be said about a field no one could read, even when the AI returned a value.
            if (!unreadable(sample.getFields().get(key))) fields.put(key, canonical(key, value, Source.AI));
        });
        var truthRows = truthItems(sample);
        var items = truthRows == null ? null : align(truthRows, rows(prediction.items(), Source.AI));
        return new EvaluationData.Result("document-" + sample.getDocumentId(), null, null, "SUCCESS", null, prediction.durationMs(),
                null, null, prediction.ocrText(), null, fields, items, prediction.documentType(), prediction.category());
    }
}
