package com.example.invoice.evaluation;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** An isolated, versioned benchmark. Never populated from confidence or corrected business rows. */
public record EvaluationData(int schemaVersion, String datasetId, String datasetVersion,
        List<Sample> samples, List<Run> runs) {
    public record Sample(String id, String assetSha256, String documentType, List<String> tags,
            String verifiedBy, Instant verifiedAt, String ocrText, Map<String, String> fields,
            Map<String, String> ocrFields, List<Map<String, String>> items) {}
    public record Run(String id, String strategy, String model, String modelDigest, String configSha256,
            String environment, String codeRevision, Instant startedAt, List<Result> results) {}
    public record Result(String sampleId, String assetSha256, String evidenceSha256,
            String outcome, String failureCode, Long durationMs, Boolean visionFallback, Integer attemptCount,
            String ocrText, Map<String, String> ocrFields, Map<String, String> fields,
            List<Map<String, String>> items, String predictedType) {}
}
