package com.example.invoice.evaluation;

import com.example.invoice.entity.AuditLog;
import com.example.invoice.repository.AuditLogRepository;
import com.example.invoice.service.DocumentAuditService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** What OCR/AI produced in one run, read from the immutable AI_EXTRACT audit row and never from corrected data. */
public record LivePrediction(Long snapshotId, Long fileVersionId, String model, String documentType, String category,
        Map<String, String> fields, List<Map<String, String>> items, String ocrText, Long durationMs, LocalDateTime processedAt) {

    @Component
    public static class Reader {
        private static final String ACTION = "AI_EXTRACT";
        private final AuditLogRepository audit;
        private final ObjectMapper json;

        public Reader(AuditLogRepository audit, ObjectMapper json) {
            this.audit = audit;
            this.json = json;
        }

        /** Newest run on this file version; runs on another version analysed a different file. */
        public Optional<LivePrediction> latest(Long documentId, Long fileVersionId) {
            return snapshots(documentId).filter(p -> Objects.equals(p.fileVersionId(), fileVersionId)).findFirst();
        }

        public Optional<LivePrediction> find(Long documentId, Long snapshotId) {
            return snapshots(documentId).filter(p -> Objects.equals(p.snapshotId(), snapshotId)).findFirst();
        }

        private java.util.stream.Stream<LivePrediction> snapshots(Long documentId) {
            return audit.findByEntityTypeAndEntityIdOrderByCreatedAtDescIdDesc(DocumentAuditService.TYPE, documentId.toString()).stream()
                    .filter(row -> ACTION.equals(row.getAction())).map(this::parse).flatMap(Optional::stream);
        }

        private Optional<LivePrediction> parse(AuditLog row) {
            try {
                JsonNode snapshot = json.readTree(row.getNewValue());
                JsonNode proposal = snapshot.path("aiProposal");
                if (!proposal.isObject()) return Optional.empty(); // Older rows hold no untouched AI output.
                JsonNode invoice = proposal.path("invoice");
                var fields = new LinkedHashMap<String, String>();
                for (String key : LiveEvaluationData.FIELDS) put(fields, key, invoice.path(key));
                var items = new ArrayList<Map<String, String>>();
                for (JsonNode node : invoice.path("items")) {
                    var row2 = new LinkedHashMap<String, String>();
                    for (String key : LiveEvaluationData.ITEM_FIELDS) put(row2, key, node.path(key));
                    items.add(row2);
                }
                Long fileVersion = snapshot.path("fileVersionId").canConvertToLong() ? snapshot.path("fileVersionId").asLong() : null;
                Long duration = proposal.path("durationMs").canConvertToLong() ? proposal.path("durationMs").asLong() : null;
                return Optional.of(new LivePrediction(row.getId(), fileVersion, text(proposal.path("model")), text(proposal.path("documentType")),
                        text(proposal.path("accountingCategoryCode")), fields, items, text(snapshot.path("ocr").path("rawText")), duration, row.getCreatedAt()));
            } catch (com.fasterxml.jackson.core.JsonProcessingException e) { return Optional.empty(); }
        }

        private static void put(Map<String, String> target, String key, JsonNode value) {
            String text = text(value);
            if (text != null) target.put(key, text);
        }

        private static String text(JsonNode node) {
            return node.isMissingNode() || node.isNull() || node.asText().isBlank() ? null : node.asText();
        }
    }
}
