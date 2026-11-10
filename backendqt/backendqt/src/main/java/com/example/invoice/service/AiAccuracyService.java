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

    /**
     * Benchmark-style scores for one group of fields (or ALL). Every compared value is one slot: kept = true positive;
     * changed = one false positive (the AI value) and one false negative (the right value it missed); filled in by the
     * accountant = false negative; deleted by the accountant = false positive.
     */
    public record GroupScore(String group, int compared, int correct, int wrong, int missing, int extra,
            Double precision, Double recall, Double f1) {
        static GroupScore of(String group, int compared, int wrong, int missing, int extra) {
            int correct = compared - wrong - missing - extra;
            int fp = wrong + extra, fn = wrong + missing;
            Double precision = correct + fp == 0 ? null : (double) correct / (correct + fp);
            Double recall = correct + fn == 0 ? null : (double) correct / (correct + fn);
            Double f1 = precision == null || recall == null || precision + recall == 0 ? null : 2 * precision * recall / (precision + recall);
            return new GroupScore(group, compared, correct, wrong, missing, extra, precision, recall, f1);
        }
    }

    /**
     * One approved document. {@code status}: EXACT (the accountant kept every AI value), CHANGED, or SKIPPED with a
     * {@code skipReason}: NO_AI_RESULT (approved without an AI run on record), NO_APPROVAL_AFTER_AI (AI ran again
     * after the approval), UNREADABLE_SNAPSHOT.
     */
    public record DocumentRow(Long documentId, String fileName, LocalDateTime approvedAt, String status, String skipReason,
            int compared, int correct, int changes, Double accuracy) {}

    public record Report(LocalDate from, LocalDate to, int approvedDocuments, int comparedDocuments, int skippedDocuments,
            int unchangedDocuments, int compared, int correct, int wrong, int missing, int extra, Double accuracy,
            List<FieldScore> byField, List<DocumentChanges> recentChanges, List<GroupScore> groups, List<DocumentRow> documents) {}

    /** The comparison of one approved document, or why it could not be compared. */
    record Outcome(AiChangeComparison.Result result, String skipReason) {}

    @Transactional(readOnly = true)
    public Report report(LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) throw new IllegalArgumentException("INVALID_DATE_RANGE");
        LocalDateTime start = from == null ? LocalDateTime.of(2000, 1, 1, 0, 0) : from.atStartOfDay();
        LocalDateTime end = to == null ? LocalDateTime.now().plusDays(1) : to.plusDays(1).atStartOfDay();
        var approved = new LinkedHashMap<Long, Document>();
        var approvedAt = new HashMap<Long, LocalDateTime>();
        for (DocumentReview review : reviews.findDecisionsBetween(start, end))
            if (ReviewEffortService.isApproval(review)) {
                approved.put(review.getDocument().getId(), review.getDocument());
                approvedAt.merge(review.getDocument().getId(), review.getReviewedAt() == null ? LocalDateTime.MIN : review.getReviewedAt(),
                        (left, right) -> left.isAfter(right) ? left : right);
            }
        var events = new HashMap<Long, List<AuditLog>>();
        if (!approved.isEmpty()) for (AuditLog event : audit.findByEntityTypeAndEntityIdInOrderByCreatedAtAscIdAsc(
                DocumentAuditService.TYPE, approved.keySet().stream().map(String::valueOf).toList()))
            events.computeIfAbsent(Long.valueOf(event.getEntityId()), id -> new ArrayList<>()).add(event);

        int compared = 0, correct = 0, wrong = 0, missing = 0, extra = 0, skipped = 0, unchanged = 0;
        var fields = new TreeMap<String, int[]>();
        // Per group: compared, wrong, missing, extra.
        var groups = new LinkedHashMap<String, int[]>();
        for (String group : List.of(AiChangeComparison.GROUP_HEADER, AiChangeComparison.GROUP_ITEMS, AiChangeComparison.GROUP_CLASSIFICATION))
            groups.put(group, new int[4]);
        var recent = new ArrayList<DocumentChanges>();
        var rows = new ArrayList<DocumentRow>();
        for (var entry : approved.entrySet()) {
            Long id = entry.getKey();
            String fileName = entry.getValue().getOriginalFileName();
            LocalDateTime at = approvedAt.get(id) == LocalDateTime.MIN ? null : approvedAt.get(id);
            Outcome outcome = compareApprovedOutcome(events.getOrDefault(id, List.of()));
            var result = outcome.result();
            if (result == null) {
                skipped++;
                rows.add(new DocumentRow(id, fileName, at, "SKIPPED", outcome.skipReason(), 0, 0, 0, null));
                continue;
            }
            compared += result.compared(); correct += result.correct();
            result.comparedByGroup().forEach((group, count) -> groups.computeIfAbsent(group, key -> new int[4])[0] += count);
            if (result.changes().isEmpty()) unchanged++;
            else recent.add(new DocumentChanges(id, fileName, true, true, result.compared(), result.correct(), result.changes()));
            rows.add(new DocumentRow(id, fileName, at, result.changes().isEmpty() ? "EXACT" : "CHANGED", null, result.compared(),
                    result.correct(), result.changes().size(), result.compared() == 0 ? null : (double) result.correct() / result.compared()));
            for (Change change : result.changes()) {
                int[] tally = fields.computeIfAbsent(change.field(), key -> new int[3]);
                int[] group = groups.computeIfAbsent(AiChangeComparison.group(change.field()), key -> new int[4]);
                switch (change.kind()) {
                    case AiChangeComparison.WRONG -> { wrong++; tally[0]++; group[1]++; }
                    case AiChangeComparison.MISSING -> { missing++; tally[1]++; group[2]++; }
                    default -> { extra++; tally[2]++; group[3]++; }
                }
            }
        }
        var byField = new ArrayList<FieldScore>();
        fields.forEach((field, tally) -> byField.add(new FieldScore(field, tally[0] + tally[1] + tally[2], tally[0], tally[1], tally[2])));
        byField.sort(Comparator.comparingInt(FieldScore::changes).reversed());
        Collections.reverse(recent);
        var groupScores = new ArrayList<GroupScore>();
        groupScores.add(GroupScore.of("ALL", compared, wrong, missing, extra));
        groups.forEach((group, tally) -> groupScores.add(GroupScore.of(group, tally[0], tally[1], tally[2], tally[3])));
        // Newest approvals first; documents never approved in the range do not appear.
        rows.sort(Comparator.comparing(DocumentRow::approvedAt, Comparator.nullsLast(Comparator.reverseOrder())));
        return new Report(from, to, approved.size(), approved.size() - skipped, skipped, unchanged, compared, correct, wrong, missing,
                extra, compared == 0 ? null : (double) correct / compared, List.copyOf(byField),
                recent.stream().limit(RECENT_LIMIT).toList(), List.copyOf(groupScores), List.copyOf(rows));
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
        return compareApprovedOutcome(eventsOldestFirst).result();
    }

    Outcome compareApprovedOutcome(List<AuditLog> eventsOldestFirst) {
        AuditLog ai = null, approval = null;
        for (AuditLog event : eventsOldestFirst) {
            if (AI_RESULT.equals(event.getAction())) { ai = event; approval = null; }
            else if (APPROVAL.equals(event.getAction()) && ai != null) approval = event;
        }
        if (ai == null) return new Outcome(null, "NO_AI_RESULT");
        if (approval == null) return new Outcome(null, "NO_APPROVAL_AFTER_AI");
        try { return new Outcome(AiChangeComparison.compare(json.readTree(ai.getNewValue()), json.readTree(approval.getNewValue())), null); }
        catch (java.io.IOException | IllegalArgumentException unreadable) { return new Outcome(null, "UNREADABLE_SNAPSHOT"); }
    }

    private JsonNode latest(List<AuditLog> newestFirst) {
        for (AuditLog event : newestFirst) if (AI_RESULT.equals(event.getAction())) {
            try { return json.readTree(event.getNewValue()); }
            catch (java.io.IOException unreadable) { return null; }
        }
        return null;
    }
}
