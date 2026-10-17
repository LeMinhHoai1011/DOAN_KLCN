package com.example.invoice.service;

import com.example.invoice.exception.AiProviderException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PipelineFailuresTest {
    @Test void timeoutIsTemporaryButInvalidJsonAndConfigurationAreNot() {
        var timeout = PipelineFailures.classify(new AiProviderException("OLLAMA_TIMEOUT: token=secret"), "AI_EXTRACTION", 2);
        assertEquals("AI_TIMEOUT", timeout.errorCode()); assertTrue(timeout.retryable()); assertEquals(2, timeout.attemptCount());
        assertFalse(timeout.userMessage().contains("secret"));
        assertFalse(PipelineFailures.classify(new AiProviderException("OLLAMA_INVALID_JSON: secret"), "AI_EXTRACTION", 1).retryable());
        assertFalse(PipelineFailures.classify(new AiProviderException("OLLAMA_MODEL_NOT_FOUND"), "AI_EXTRACTION", 1).retryable());
    }
    @Test void storageTimeoutIsNotAnAiTimeoutAndOnlyTemporaryStorageErrorsRetry() {
        var failure = PipelineFailures.classify(new IllegalStateException("private host and bucket", new java.net.SocketTimeoutException()), "STORAGE", 1);
        assertEquals("STORAGE_FAILED", failure.errorCode()); assertTrue(failure.retryable());
        assertFalse(failure.userMessage().contains("bucket"));
        assertFalse(PipelineFailures.classify(new IllegalStateException("AccessDenied"), "STORAGE", 1).retryable());
    }
    @Test void schemaDatabaseErrorsAndMissingOcrLanguageAreNotRetryable() {
        assertFalse(PipelineFailures.classify(new org.springframework.dao.InvalidDataAccessResourceUsageException("missing column"), "PERSISTENCE", 1).retryable());
        assertEquals("OCR_FAILED", PipelineFailures.classify(new IllegalStateException("OCR_LANGUAGE_DATA_MISSING: path"), "OCR", 1).errorCode());
        assertFalse(PipelineFailures.classify(new IllegalStateException("OCR_LANGUAGE_DATA_MISSING"), "OCR", 1).retryable());
        String technical = PipelineFailures.technicalDetails(new IllegalStateException("secret-token", new java.sql.SQLException("private SQL", "42703")));
        assertTrue(technical.contains("42703")); assertFalse(technical.contains("secret-token")); assertFalse(technical.contains("private SQL"));
    }
}
