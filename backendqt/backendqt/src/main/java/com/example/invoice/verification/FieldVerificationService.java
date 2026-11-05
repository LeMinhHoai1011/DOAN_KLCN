package com.example.invoice.verification;

import com.example.invoice.entity.Document;
import com.example.invoice.entity.ExtractedField;
import com.example.invoice.entity.FieldCorrection;
import com.example.invoice.entity.FieldVerification;
import com.example.invoice.entity.Invoice;
import com.example.invoice.entity.InvoiceItem;
import com.example.invoice.entity.OCRResult;
import com.example.invoice.repository.ExtractedFieldRepository;
import com.example.invoice.repository.FieldCorrectionRepository;
import com.example.invoice.repository.FieldVerificationRepository;
import com.example.invoice.repository.InvoiceRepository;
import com.example.invoice.repository.OCRResultRepository;
import com.example.invoice.service.DocumentService;
import com.example.invoice.service.OcrFieldLocator;
import com.example.invoice.verification.FieldVerifier.AiInputMode;
import com.example.invoice.verification.FieldVerifier.FieldInput;
import com.example.invoice.verification.FieldVerifier.ItemInput;
import com.example.invoice.verification.FieldVerifier.Origin;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Runs FieldVerifier on the current stored values of a document and keeps one result set per document.
 * Read-only towards business data: it never corrects a value, and it is not used by the independent
 * benchmark (EvaluationService), whose metrics stay based on manual ground truth only.
 */
@Service
@RequiredArgsConstructor
public class FieldVerificationService {
    public static final String TRIGGER_AI = "AI_PROCESSING";
    public static final String TRIGGER_RECOMPUTE = "MANUAL_RECOMPUTE";
    /** Header names that live in invoice_data; extracted_fields copies of them are not verified twice. */
    private static final Set<String> HEADER = Set.copyOf(CoreFieldProfiles.INVOICE_HEADER);

    private final DocumentService documentService;
    private final InvoiceRepository invoices;
    private final ExtractedFieldRepository extractedFields;
    private final FieldCorrectionRepository corrections;
    private final OCRResultRepository ocrResults;
    private final FieldVerificationRepository verifications;
    private final OcrFieldLocator locator;
    private final ObjectMapper json;

    public record EvidenceView(String outcome, Double matchScore, String method, String matchedText, FieldVerifier.Location location) {}
    public record RuleView(String ruleId, String outcome, String message) {}
    public record FieldView(String fieldKey, Integer itemIndex, boolean core, String status, Integer score, String value,
            String aiValue, String valueOrigin, EvidenceView evidence, List<RuleView> rules, List<String> reasons) {}
    public record Report(Long documentId, String ruleVersion, String aiInputMode, String triggerSource,
            LocalDateTime verifiedAt, boolean stale, Map<String, Long> counts, String scoreNote, String statusNote,
            List<FieldView> fields) {}

    @Transactional
    public Report verifyAfterProcessing(Long documentId, AiInputMode mode) {
        return run(documentId, mode, TRIGGER_AI);
    }

    /** Re-checks current values (e.g. after manual edits), keeping the AI input mode of the last processing. */
    @Transactional
    public Report recompute(Long documentId) {
        documentService.load(documentId);
        AiInputMode mode = verifications.findByDocumentIdOrderByIdAsc(documentId).stream().findFirst()
                .map(v -> AiInputMode.valueOf(v.getAiInputMode())).orElse(AiInputMode.UNKNOWN);
        return run(documentId, mode, TRIGGER_RECOMPUTE);
    }

    @Transactional(readOnly = true)
    public Report report(Long documentId) {
        Document document = documentService.load(documentId);
        return toReport(document, verifications.findByDocumentIdOrderByIdAsc(documentId));
    }

    private Report run(Long documentId, AiInputMode mode, String trigger) {
        Document document = documentService.load(documentId);
        String type = document.getType() != null ? document.getType().getCode() : document.getDocumentType();
        Map<String, String> previousAi = new HashMap<>();
        for (FieldVerification row : verifications.findByDocumentIdOrderByIdAsc(documentId))
            if (row.getAiValue() != null) previousAi.put(row.getFieldKey(), row.getAiValue());
        boolean aiRun = TRIGGER_AI.equals(trigger);

        var fields = new ArrayList<FieldInput>();
        var items = new ArrayList<ItemInput>();
        var values = new LinkedHashMap<String, String>();
        Invoice invoice = CoreFieldProfiles.isInvoiceType(type) ? invoices.findByDocumentId(documentId).orElse(null) : null;
        if (invoice != null) {
            invoiceValues(invoice).forEach(values::put);
            List<InvoiceItem> lines = invoice.getItems().stream()
                    .sorted(Comparator.comparing(InvoiceItem::getId, Comparator.nullsLast(Comparator.naturalOrder()))).toList();
            for (int index = 0; index < lines.size(); index++) {
                InvoiceItem line = lines.get(index);
                Origin origin = aiRun ? Origin.AI : itemOrigin(index, line, previousAi);
                items.add(new ItemInput(line.getProductName(), line.getQuantity(), line.getUnitPrice(), line.getTaxRate(),
                        line.getTaxAmount(), line.getAmount(), origin));
            }
        }
        var manualExtracted = new HashSet<String>();
        for (ExtractedField field : extractedFields.findByDocumentId(documentId)) {
            if (field.getFieldName() == null || (invoice != null && HEADER.contains(field.getFieldName()))) continue;
            FieldCorrection correction = corrections.findFirstByFieldIdOrderByCreatedAtDescIdDesc(field.getId()).orElse(null);
            values.putIfAbsent(field.getFieldName(), correction == null ? field.getFieldValue() : correction.getNewValue());
            if (correction != null || !"AI".equalsIgnoreCase(field.getSource())) manualExtracted.add(field.getFieldName());
        }
        List<String> core = CoreFieldProfiles.core(type, values.get("receiptKind"));
        for (String key : core) values.putIfAbsent(key, null);
        for (var entry : values.entrySet()) {
            String key = entry.getKey();
            Origin origin = manualExtracted.contains(key) ? Origin.MANUAL
                    : aiRun ? Origin.AI : headerOrigin(key, entry.getValue(), previousAi);
            fields.add(new FieldInput(key, CoreFieldProfiles.kindOf(key), entry.getValue(), core.contains(key), origin));
        }

        OCRResult ocr = ocrResults.findFirstByDocumentIdOrderByProcessedAtDesc(documentId).orElse(null);
        String rawText = ocr == null ? null : ocr.getRawText();
        String layout = ocr == null ? null : ocr.getLayoutJson();
        boolean ocrAvailable = rawText != null && !rawText.isBlank();
        var finder = new OcrEvidenceFinder(rawText, (name, value) -> locator.locateInLayout(layout, name, value));
        var results = new FieldVerifier(finder).verify(new FieldVerifier.Input(type, mode, ocrAvailable, fields, items, LocalDate.now()));

        verifications.deleteByDocumentId(documentId);
        LocalDateTime now = LocalDateTime.now();
        var saved = new ArrayList<FieldVerification>();
        for (var result : results) {
            var row = new FieldVerification();
            row.setDocument(document);
            row.setFieldKey(result.key());
            row.setItemIndex(result.itemIndex());
            row.setCore(result.core());
            row.setStatus(result.status().name());
            row.setScore(result.score());
            row.setCheckedValue(result.value());
            row.setAiValue(aiRun ? result.value() : previousAi.get(result.key()));
            row.setValueOrigin(result.origin().name());
            row.setAiInputMode(mode.name());
            row.setEvidenceJson(write(result.evidence()));
            row.setRulesJson(write(result.rules()));
            row.setReasonsJson(write(result.reasons()));
            row.setRuleVersion(FieldVerifier.RULE_VERSION);
            row.setTriggerSource(trigger);
            row.setVerifiedAt(now);
            saved.add(row);
        }
        return toReport(document, verifications.saveAll(saved));
    }

    static Map<String, String> invoiceValues(Invoice invoice) {
        var values = new LinkedHashMap<String, String>();
        values.put("invoiceSeries", invoice.getInvoiceSeries());
        values.put("invoiceNumber", invoice.getInvoiceNumber());
        values.put("invoiceDate", invoice.getInvoiceDate() == null ? null : invoice.getInvoiceDate().toString());
        values.put("signDate", invoice.getSignDate() == null ? null : invoice.getSignDate().toString());
        values.put("sellerName", invoice.getSellerName());
        values.put("sellerTaxCode", invoice.getSellerTaxCode());
        values.put("sellerAddress", invoice.getSellerAddress());
        values.put("sellerPhone", invoice.getSellerPhone());
        values.put("buyerName", invoice.getBuyerName());
        values.put("buyerTaxCode", invoice.getBuyerTaxCode());
        values.put("buyerAddress", invoice.getBuyerAddress());
        values.put("subtotal", plain(invoice.getSubtotal()));
        values.put("vatAmount", plain(invoice.getVatAmount()));
        values.put("totalAmount", plain(invoice.getTotalAmount()));
        values.put("paymentMethod", invoice.getPaymentMethod());
        values.put("amountInWords", invoice.getAmountInWords());
        values.put("taxAuthorityCode", invoice.getTaxAuthorityCode());
        return values;
    }

    /** A value differing from what AI first produced was edited by a person. Unknown history counts as MANUAL. */
    private static Origin headerOrigin(String key, String value, Map<String, String> previousAi) {
        if (!previousAi.containsKey(key)) return value == null ? Origin.AI : Origin.MANUAL;
        return Objects.equals(normalize(previousAi.get(key)), normalize(value)) ? Origin.AI : Origin.MANUAL;
    }

    /** Rows are matched by position; inserting or deleting lines therefore marks later lines MANUAL. */
    private static Origin itemOrigin(int index, InvoiceItem line, Map<String, String> previousAi) {
        String prefix = "items[" + index + "].";
        return Objects.equals(previousAi.get(prefix + "productName"), line.getProductName())
                && Objects.equals(normalize(previousAi.get(prefix + "amount")), normalize(plain(line.getAmount())))
                ? Origin.AI : Origin.MANUAL;
    }

    private Report toReport(Document document, List<FieldVerification> rows) {
        var views = new ArrayList<FieldView>();
        var counts = new TreeMap<String, Long>();
        for (FieldVerifier.Status status : FieldVerifier.Status.values()) counts.put(status.name(), 0L);
        LocalDateTime verifiedAt = null;
        String mode = null, trigger = null, version = null;
        for (FieldVerification row : rows) {
            counts.merge(row.getStatus(), 1L, Long::sum);
            verifiedAt = row.getVerifiedAt(); mode = row.getAiInputMode(); trigger = row.getTriggerSource(); version = row.getRuleVersion();
            FieldVerifier.Evidence evidence = read(row.getEvidenceJson(), new TypeReference<>() {});
            List<FieldVerifier.RuleResult> rules = read(row.getRulesJson(), new TypeReference<>() {});
            List<String> reasons = read(row.getReasonsJson(), new TypeReference<>() {});
            views.add(new FieldView(row.getFieldKey(), row.getItemIndex(), row.isCore(), row.getStatus(), row.getScore(),
                    row.getCheckedValue(), row.getAiValue(), row.getValueOrigin(),
                    evidence == null ? null : new EvidenceView(evidence.outcome().name(), evidence.matchScore(), evidence.method(),
                            evidence.matchedText(), evidence.location()),
                    rules == null ? List.of() : rules.stream().map(r -> new RuleView(r.ruleId(), r.outcome().name(), r.message())).toList(),
                    reasons == null ? List.of() : reasons));
        }
        return new Report(document.getId(), version, mode, trigger, verifiedAt, verifiedAt != null && editedAfter(document, verifiedAt),
                counts, FieldVerifier.SCORE_NOTE,
                "VERIFIED chỉ có nghĩa đã vượt qua các kiểm tra được áp dụng, không chứng minh giá trị đúng tuyệt đối.",
                List.copyOf(views));
    }

    /** True when stored values changed after the last verification, so the shown result may be outdated. */
    private boolean editedAfter(Document document, LocalDateTime verifiedAt) {
        Invoice invoice = invoices.findByDocumentId(document.getId()).orElse(null);
        if (invoice != null && invoice.getUpdatedAt() != null && invoice.getUpdatedAt().isAfter(verifiedAt)) return true;
        for (ExtractedField field : extractedFields.findByDocumentId(document.getId())) {
            if (field.getUpdatedAt() != null && field.getUpdatedAt().isAfter(verifiedAt)) return true;
            var correction = corrections.findFirstByFieldIdOrderByCreatedAtDescIdDesc(field.getId()).orElse(null);
            if (correction != null && correction.getCreatedAt() != null && correction.getCreatedAt().isAfter(verifiedAt)) return true;
        }
        return false;
    }

    private static String plain(BigDecimal value) { return value == null ? null : FieldVerifier.plain(value); }
    private static String normalize(String value) {
        if (value == null || value.isBlank()) return null;
        BigDecimal number = FieldVerifier.parseAmount(value);
        return number != null ? FieldVerifier.plain(number) : value.trim();
    }
    private String write(Object value) {
        try { return json.writeValueAsString(value); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Không thể lưu kết quả kiểm chứng", exception); }
    }
    private <T> T read(String value, TypeReference<T> type) {
        if (value == null) return null;
        try { return json.readValue(value, type); } catch (JsonProcessingException exception) { return null; }
    }
}
