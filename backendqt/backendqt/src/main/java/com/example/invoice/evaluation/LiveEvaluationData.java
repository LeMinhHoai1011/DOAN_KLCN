package com.example.invoice.evaluation;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Labels for real documents. Stored beside the file in object storage, never in business tables. */
public final class LiveEvaluationData {
    private LiveEvaluationData() {}

    /** Every invoice sample is labelled on this fixed list, so a field the AI omitted still counts as missing. */
    public static final List<String> FIELDS = List.of("invoiceNumber", "invoiceSeries", "invoiceDate", "sellerName", "sellerTaxCode",
            "sellerAddress", "buyerName", "buyerTaxCode", "buyerAddress", "subtotal", "vatAmount", "totalAmount", "paymentMethod");
    public static final List<String> ITEM_FIELDS = List.of("productName", "unit", "quantity", "unitPrice", "taxRate", "taxAmount", "amount");

    public enum State { PRESENT, ABSENT, UNREADABLE, UNCHECKED }
    public enum Status { SELECTED, DRAFT, CONFIRMED }
    public enum Selection { MANUAL, RANDOM }

    public record Label(State state, String value) {
        static Label unchecked() { return new Label(State.UNCHECKED, null); }
    }

    @Getter @Setter @NoArgsConstructor
    public static class Sample {
        private int schemaVersion = 1;
        private Long documentId;
        private Long companyId;
        /** The AI_EXTRACT audit row being scored; later reprocessing never replaces it. */
        private Long snapshotId;
        private Long fileVersionId;
        private Selection selection;
        private String selectedBy;
        private Instant selectedAt;
        private Status status = Status.SELECTED;
        private int revision;
        private String documentType;
        private Map<String, Label> fields = new LinkedHashMap<>();
        private Label category = Label.unchecked();
        private State itemsState = State.UNCHECKED;
        private List<Map<String, String>> items = new ArrayList<>();
        private String ocrText;
        /** The transcript started from OCR output, so CER/WER for this sample lean optimistic. */
        private boolean ocrAssisted;
        private Map<String, String> reconciliation;
        private String confirmedBy;
        private Instant confirmedAt;
        /** Reopened after AI values were revealed, so the label is no longer strictly blind. */
        private boolean revisedAfterReveal;
    }
}
