package com.example.invoice.dto.document;

import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonProperty;

@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
public record PipelineFailure(String errorCode, String failedStage, boolean retryable,
        int attemptCount, LocalDateTime timestamp, String userMessage) {
    @JsonProperty("error") public String error() { return errorCode; }
    @JsonProperty("message") public String message() { return userMessage; }
}
