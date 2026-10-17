package com.example.invoice.dto.document;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record PipelineHistory(boolean retryAllowed, int maxAttempts, int attemptCount,
        long succeeded, long failed, long needsReview, long running, Map<String,Long> failuresByCode, List<Attempt> attempts) {
    public record Attempt(Long jobId, int attemptNumber, String status, LocalDateTime startedAt,
            LocalDateTime completedAt, PipelineFailure failure) {}
}
