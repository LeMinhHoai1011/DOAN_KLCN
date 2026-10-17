package com.example.invoice.dto.document;

import java.math.BigDecimal;
import java.util.List;

/** Assessment of one completed processing attempt; unknown scores remain null. */
public record DocumentAssessment(Confidence ocrConfidence, Confidence extractionConfidence,
        Confidence classificationConfidence, String validationStatus,
        List<Warning> warnings, List<String> reviewReasons) {
    public record Confidence(BigDecimal value, String source, String method) {}
    public record Warning(String code, String severity, String message, String affectedField) {}
}
