package com.example.invoice.service;

import com.example.invoice.entity.AuditLog;
import com.example.invoice.entity.Document;
import com.example.invoice.entity.DocumentReview;
import com.example.invoice.repository.AuditLogRepository;
import com.example.invoice.repository.DocumentReviewRepository;
import com.example.invoice.service.AiChangeComparison.Change;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Accuracy measured from approved documents: the AI_EXTRACT snapshot against the snapshot taken at approval. */
@Service
@RequiredArgsConstructor
public class AiAccuracyService {
    private static final String AI_RESULT = "AI_EXTRACT", APPROVAL = "WORKFLOW_APPROVE";
    private static final int RECENT_LIMIT = 20;

    private final DocumentReviewRepository reviews;
    private final AuditLogRepository audit;
    private final DocumentService documents;
    private final DocumentAuditService documentAudit;
    private final ObjectMapper json;

    public record FieldScore(String field, int changes, int wrong, int missing, int extra) {}
    public record DocumentChanges(Long documentId, String fileName, boolean hasAiResult, boolean approved, int compared, int correct, List<Change> changes) {}
    public record Report(LocalDate from, LocalDate to, int approvedDocuments, int comparedDocuments, int skippedDocuments,
            int unchangedDocuments, int compared, int correct, int wrong, int missing, int extra, Double accuracy,
            List<FieldScore> byField, List<DocumentChanges> recentChanges) {}

    @Transactional(readOnly = true)
    public Report report(LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) throw new IllegalArgumentException("INVALID_DATE_RANGE");
        LocalDateTime start = from == null ? LocalDateTime.of(2000, 1, 1, 0, 0) : from.atStartOfDay();
        LocalDateTime end = to == null ? LocalDateTime.now().plusDays(1) : to.plusDays(1).atStartOfDay();
        var approved = new LinkedHashMap<Long, Document>();
        for (DocumentReview review : reviews.findDecisionsBetween(start, end))
            if (ReviewEffortService.isApproval(review)) approved.put(review.getDocument().getId(), review.getDocument());
        var events = new HashMap<Long, List<AuditLog>>();
        if (!approved.isEmpty()) for (AuditLog event : audit.findByEntityTypeAndEntityIdInOrderByCreatedAtAscIdAsc(
                DocumentAuditService.TYPE, approved.keySet().stream().map(String::valueOf).toList()))
            events.computeIfAbsent(Long.valueOf(event.getEntityId()), id -> new ArrayList<>()).add(event);

        int compared = 0, correct = 0, wrong = 0, missing = 0, extra = 0, skipped = 0, unchanged = 0;
        var fields = new TreeMap<String, int[]>();
        var recent = new ArrayList<DocumentChanges>();
        for (var entry : approved.entrySet()) {
            var result = compareApproved(events.getOrDefault(entry.getKey(), List.of()));
            if (result == null) { skipped++; continue; }
            compared += result.compared(); correct += result.correct();
            if (result.changes().isEmpty()) unchanged++;
            else recent.add(new DocumentChanges(entry.getKey(), entry.getValue().getOriginalFileName(), true, true, result.compared(), result.correct(), result.changes()));
            for (Change change : result.changes()) {
                int[] tally = fields.computeIfAbsent(change.field(), key -> new int[3]);
                switch (change.kind()) {
                    case AiChangeComparison.WRONG -> { wrong++; tally[0]++; }
                    case AiChangeComparison.MISSING -> { missing++; tally[1]++; }
                    default -> { extra++; tally[2]++; }
                }
            }
        }
        var byField = new ArrayList<FieldScore>();
        fields.forEach((field, tally) -> byField.add(new FieldScore(field, tally[0] + tally[1] + tally[2], tally[0], tally[1], tally[2])));
        byField.sort(Comparator.comparingInt(FieldScore::changes).reversed());
        Collections.reverse(recent);
        return new Report(from, to, approved.size(), approved.size() - skipped, skipped, unchanged, compared, correct, wrong, missing,
                extra, compared == 0 ? null : (double) correct / compared, List.copyOf(byField),
                recent.stream().limit(RECENT_LIMIT).toList());
    }

    /** What the accountant has changed on one document so far, approved or not. */
    @Transactional
    public DocumentChanges documentChanges(Long documentId) {
        Document document = documents.load(documentId);
        JsonNode ai = latest(audit.findByEntityTypeAndEntityIdOrderByCreatedAtDescIdDesc(DocumentAuditService.TYPE, documentId.toString()));
        boolean approved = document.getReviewStatus() == com.example.invoice.entity.ReviewStatus.APPROVED;
        if (ai == null) return new DocumentChanges(documentId, document.getOriginalFileName(), false, approved, 0, 0, List.of());
        try {
            var result = AiChangeComparison.compare(ai, json.readTree(documentAudit.capture(document)));
            return new DocumentChanges(documentId, document.getOriginalFileName(), true, approved, result.compared(), result.correct(), result.changes());
        } catch (java.io.IOException unreadable) {
            throw new IllegalStateException("Không đọc được ảnh chụp chứng từ", unreadable);
        }
    }

    /** Null when the history cannot prove both sides, so the document is never counted as correct by default. */
    AiChangeComparison.Result compareApproved(List<AuditLog> eventsOldestFirst) {
        AuditLog ai = null, approval = null;
        for (AuditLog event : eventsOldestFirst) {
            if (AI_RESULT.equals(event.getAction())) { ai = event; approval = null; }
            else if (APPROVAL.equals(event.getAction()) && ai != null) approval = event;
        }
        if (ai == null || approval == null) return null;
        try { return AiChangeComparison.compare(json.readTree(ai.getNewValue()), json.readTree(approval.getNewValue())); }
        catch (java.io.IOException | IllegalArgumentException unreadable) { return null; }
    }

    private JsonNode latest(List<AuditLog> newestFirst) {
        for (AuditLog event : newestFirst) if (AI_RESULT.equals(event.getAction())) {
            try { return json.readTree(event.getNewValue()); }
            catch (java.io.IOException unreadable) { return null; }
        }
        return null;
    }
}
