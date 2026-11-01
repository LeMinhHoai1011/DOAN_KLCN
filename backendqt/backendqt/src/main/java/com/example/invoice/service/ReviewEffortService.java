package com.example.invoice.service;

import com.example.invoice.entity.AuditLog;
import com.example.invoice.entity.Document;
import com.example.invoice.entity.DocumentReview;
import com.example.invoice.repository.AuditLogRepository;
import com.example.invoice.repository.DocumentReviewRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Human effort around AI output, from real review decisions: measured review time and whether the
 * accountant had to change what AI produced before approving ("touchless" = approved unchanged).
 * Unmeasured time stays null; it is never estimated.
 */
@Service
@RequiredArgsConstructor
public class ReviewEffortService {
    /** Audit actions where a person can change AI output. Counted only when business content really changed. */
    static final Set<String> HUMAN_EDIT_ACTIONS = Set.of("FIELD_CORRECTION", "CLASSIFICATION_CORRECTION",
            "CLASSIFICATION_UPDATE", "INVOICE_CREATE", "INVOICE_UPDATE", "INVOICE_ITEMS_UPDATE",
            "RETAIL_DETAILS_UPDATE", "OCR_EDIT", "DOCUMENT_UPDATE");
    private static final Set<String> AI_RESULT_ACTIONS = Set.of("AI_EXTRACT");

    private final DocumentReviewRepository reviews;
    private final AuditLogRepository audit;
    private final ObjectMapper json;

    public record TypeEffort(int approved, int touchless, Double touchlessRate, int timed, Double averageReviewMs) {}
    public record Report(LocalDate from, LocalDate to, int decisions, int approvals, int rejections, int infoRequests,
            int timedDecisions, Double averageDecisionMs, Double medianDecisionMs,
            int approvedDocuments, int timedApprovedDocuments, Double averageReviewMsPerDocument,
            Double medianReviewMsPerDocument, int touchlessApproved, Double touchlessRate,
            Double averageEditsPerApprovedDocument, Map<String, TypeEffort> byDocumentType, List<String> notes) {}

    @Transactional(readOnly = true)
    public Report report(LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) throw new IllegalArgumentException("INVALID_DATE_RANGE");
        LocalDateTime start = from == null ? LocalDateTime.of(2000, 1, 1, 0, 0) : from.atStartOfDay();
        LocalDateTime end = to == null ? LocalDateTime.now().plusDays(1) : to.plusDays(1).atStartOfDay();
        List<DocumentReview> decisions = reviews.findDecisionsBetween(start, end);

        int approvals = 0, rejections = 0, infos = 0;
        var decisionTimes = new ArrayList<Long>();
        var approved = new LinkedHashMap<Long, DocumentReview>();
        for (var review : decisions) {
            String action = action(review);
            if ("APPROVE".equals(action)) { approvals++; approved.put(review.getDocument().getId(), review); }
            else if ("REJECT".equals(action)) rejections++;
            else if ("REQUEST_INFO".equals(action)) infos++;
            if (review.getActiveReviewMs() != null) decisionTimes.add(review.getActiveReviewMs());
        }

        var history = new HashMap<Long, List<DocumentReview>>();
        if (!approved.isEmpty()) for (var review : reviews.findByDocumentIdIn(approved.keySet()))
            history.computeIfAbsent(review.getDocument().getId(), id -> new ArrayList<>()).add(review);
        var events = new HashMap<Long, List<AuditLog>>();
        if (!approved.isEmpty()) for (var event : audit.findByEntityTypeAndEntityIdInOrderByCreatedAtAscIdAsc(
                DocumentAuditService.TYPE, approved.keySet().stream().map(String::valueOf).toList()))
            events.computeIfAbsent(Long.valueOf(event.getEntityId()), id -> new ArrayList<>()).add(event);

        var documentTimes = new ArrayList<Long>();
        int touchless = 0; long edits = 0;
        var types = new TreeMap<String, long[]>(); // approved, touchless, timed, totalMs
        for (var entry : approved.entrySet()) {
            DocumentReview approval = entry.getValue();
            Long reviewMs = documentReviewMs(history.getOrDefault(entry.getKey(), List.of()), approval);
            int changed = humanEdits(events.getOrDefault(entry.getKey(), List.of()), approval.getReviewedAt());
            edits += changed;
            if (changed == 0) touchless++;
            if (reviewMs != null) documentTimes.add(reviewMs);
            long[] type = types.computeIfAbsent(typeCode(approval.getDocument()), key -> new long[4]);
            type[0]++; if (changed == 0) type[1]++;
            if (reviewMs != null) { type[2]++; type[3] += reviewMs; }
        }
        var byType = new TreeMap<String, TypeEffort>();
        types.forEach((code, v) -> byType.put(code, new TypeEffort((int) v[0], (int) v[1], ratio(v[1], v[0]), (int) v[2],
                v[2] == 0 ? null : (double) v[3] / v[2])));

        return new Report(from, to, decisions.size(), approvals, rejections, infos, decisionTimes.size(),
                average(decisionTimes), median(decisionTimes), approved.size(), documentTimes.size(),
                average(documentTimes), median(documentTimes), touchless, ratio(touchless, approved.size()),
                approved.isEmpty() ? null : (double) edits / approved.size(), byType,
                List.of("Thời gian duyệt là thời gian màn hình chứng từ đang hiển thị trước quyết định; lượt không đo được để null, không ước lượng.",
                        "Một chứng từ chỉ được tính thời gian khi mọi quyết định của nó đều có số đo.",
                        "Touchless = được duyệt mà không có chỉnh sửa nội dung nào sau đề xuất AI gần nhất.",
                        "Số liệu là dữ liệu vận hành thực tế, không phải ground truth; dùng song song với bộ đánh giá A/B/C/R."));
    }

    /** Sum of measured time across every decision up to the approval; null if any decision was not measured. */
    private Long documentReviewMs(List<DocumentReview> history, DocumentReview approval) {
        long total = 0;
        for (var review : history) {
            String action = action(review);
            if (action == null || review.getReviewedAt() == null || review.getReviewedAt().isAfter(approval.getReviewedAt())) continue;
            if (review.getActiveReviewMs() == null) return null;
            total += review.getActiveReviewMs();
        }
        return total == 0 ? null : total;
    }

    /** Human edits after the latest AI proposal and up to the approval that changed business content. */
    int humanEdits(List<AuditLog> events, LocalDateTime approvedAt) {
        int lastAi = -1;
        for (int i = 0; i < events.size(); i++) {
            var at = events.get(i).getCreatedAt();
            if (AI_RESULT_ACTIONS.contains(events.get(i).getAction()) && (at == null || !at.isAfter(approvedAt))) lastAi = i;
        }
        int count = 0;
        for (int i = lastAi + 1; i < events.size(); i++) {
            var event = events.get(i);
            if (event.getCreatedAt() != null && event.getCreatedAt().isAfter(approvedAt)) break;
            if (HUMAN_EDIT_ACTIONS.contains(event.getAction()) && changedBusinessContent(event)) count++;
        }
        return count;
    }

    private boolean changedBusinessContent(AuditLog event) {
        try {
            JsonNode before = event.getOldValue() == null ? json.nullNode() : json.readTree(event.getOldValue());
            JsonNode after = event.getNewValue() == null ? json.nullNode() : json.readTree(event.getNewValue());
            return !content(before).equals(content(after));
        } catch (java.io.IOException exception) {
            return true; // Unreadable snapshot: count the edit rather than claim the output was untouched.
        }
    }

    private JsonNode content(JsonNode snapshot) {
        var node = json.createObjectNode();
        node.set("documentType", snapshot.path("document").path("documentType"));
        node.set("invoice", snapshot.path("invoice"));
        node.set("ocrText", snapshot.path("ocr").path("rawText"));
        var classification = snapshot.path("classification");
        for (String key : List.of("predictedLabel", "category", "accountingAccount"))
            node.set("classification." + key, classification.path(key));
        var fields = json.createObjectNode();
        snapshot.path("fields").forEach(f -> fields.set(f.path("fieldName").asText(), f.path("effectiveValue")));
        node.set("fields", fields);
        return node;
    }

    private static String action(DocumentReview review) {
        String note = review.getReviewNote();
        if (note == null) return null;
        for (String action : List.of("APPROVE", "REJECT", "REQUEST_INFO"))
            if (note.equals(action) || note.startsWith(action + ":")) return action;
        return null;
    }

    private static String typeCode(Document document) {
        if (document.getType() != null && document.getType().getCode() != null) return document.getType().getCode();
        return document.getDocumentType() == null ? "UNDETERMINED" : document.getDocumentType();
    }

    private static Double ratio(long part, long whole) { return whole == 0 ? null : (double) part / whole; }
    private static Double average(List<Long> values) { return values.isEmpty() ? null : values.stream().mapToLong(Long::longValue).average().orElseThrow(); }
    private static Double median(List<Long> values) {
        if (values.isEmpty()) return null;
        var sorted = values.stream().sorted().toList();
        int mid = sorted.size() / 2;
        return sorted.size() % 2 == 1 ? sorted.get(mid) : (sorted.get(mid - 1) + sorted.get(mid)) / 2.0;
    }
}
