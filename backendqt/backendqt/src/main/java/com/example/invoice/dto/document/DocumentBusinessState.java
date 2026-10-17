package com.example.invoice.dto.document;
import java.time.LocalDateTime;
import java.util.List;
/** Logical projections; persisted state vocabulary is unchanged. Null payment means no settlement evidence. */
public record DocumentBusinessState(String processing, String classification, String reconciliation,
    String review, String payment, String paymentReason, List<Event> history) {
  public record Event(Long id, String status, String action, Long actorId, LocalDateTime at) {}
}
