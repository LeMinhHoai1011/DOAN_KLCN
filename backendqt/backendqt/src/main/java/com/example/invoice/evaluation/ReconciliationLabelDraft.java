package com.example.invoice.evaluation;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.nio.file.Path;
import java.util.LinkedHashMap;

/**
 * Prints reconciliation statuses computed from the human-verified sample values, as a draft for
 * Sample.reconciliation. A reviewer must compare each draft with the original document before keeping it.
 *
 * Usage: ReconciliationLabelDraft <benchmark.json>
 */
public final class ReconciliationLabelDraft {
    private ReconciliationLabelDraft() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.err.println("Usage: ReconciliationLabelDraft <benchmark.json>");
            System.exit(1);
        }
        var json = new ObjectMapper().registerModule(new JavaTimeModule())
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).enable(SerializationFeature.INDENT_OUTPUT);
        var data = json.readValue(Path.of(args[0]).toFile(), EvaluationData.class);
        var drafts = new LinkedHashMap<String,Object>();
        for (var sample : data.samples())
            if (!EvaluationService.RETAIL_INVOICE.equals(sample.documentType()))
                drafts.put(sample.id(), EvaluationReconciliation.statuses(sample.fields(), sample.items()));
        System.out.println(json.writeValueAsString(drafts));
    }
}
