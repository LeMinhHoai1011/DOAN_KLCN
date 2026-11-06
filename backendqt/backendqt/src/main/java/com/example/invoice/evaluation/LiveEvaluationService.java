package com.example.invoice.evaluation;

import com.example.invoice.entity.Document;
import com.example.invoice.entity.DocumentStatus;
import com.example.invoice.entity.DocumentVersion;
import com.example.invoice.evaluation.LiveEvaluationData.Label;
import com.example.invoice.evaluation.LiveEvaluationData.Sample;
import com.example.invoice.evaluation.LiveEvaluationData.Selection;
import com.example.invoice.evaluation.LiveEvaluationData.State;
import com.example.invoice.evaluation.LiveEvaluationData.Status;
import com.example.invoice.exception.BadRequestException;
import com.example.invoice.repository.DocumentVersionRepository;
import com.example.invoice.service.DocumentService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Selection, blind labelling and scoring of real documents. Reads business data; writes only to the label store. */
@Service
@RequiredArgsConstructor
public class LiveEvaluationService {
    private static final Set<DocumentStatus> PROCESSED = Set.of(DocumentStatus.PROCESSED, DocumentStatus.NEED_REVIEW, DocumentStatus.COMPLETED);
    private static final int RANDOM_POOL = 500;
    private final DocumentService documents;
    private final DocumentVersionRepository versions;
    private final LiveEvaluationStore store;
    private final LivePrediction.Reader predictions;

    public record DraftRequest(Integer revision, String documentType, Map<String, Label> fields, Label category,
            State itemsState, List<Map<String, String>> items, String ocrText) {}
    public record ConfirmRequest(Integer revision, Map<String, String> reconciliation) {}
    public record RandomRequest(LocalDate dateFrom, LocalDate dateTo, Long companyId, Integer perType) {}
    public record Summary(Long documentId, String fileName, String documentType, Status status, Selection selection,
            boolean stale, boolean revisedAfterReveal, Instant selectedAt, Instant confirmedAt) {}
    public record Comparison(String field, String truth, String predicted, String outcome) {}
    public record Reveal(String model, String documentType, String category, List<Comparison> fields, List<Map<String, String>> items) {}
    public record Workspace(Sample sample, String fileName, String fileType, boolean stale, List<String> fieldCatalog,
            Map<String, String> reconciliationDraft, Reveal reveal) {}
    public record RandomResult(int added, int candidates) {}
    public record ModelReport(String model, int samples, EvaluationMetrics.Score score) {}
    public record Report(int selected, int labelling, int confirmed, int stale, int scored, int randomSelected, int manualSelected,
            int unreadableFields, int ocrAssisted, int revisedAfterReveal, Double averageAiMs, List<ModelReport> models, List<String> notes) {}

    private static boolean invoiceType(String type) { return type != null && (type.equals("INVOICE") || type.endsWith("_INVOICE")); }
    private static String typeOf(Document document) { return document.getType() == null ? document.getDocumentType() : document.getType().getCode(); }

    private Long fileVersion(Document document) {
        return versions.findByDocumentId(document.getId()).stream().filter(v -> Objects.equals(v.getObjectKey(), document.getFilePath()))
                .max(Comparator.comparing(DocumentVersion::getVersionNumber)).map(DocumentVersion::getId).orElse(null);
    }

    private Sample required(Document document) {
        if (document.getCompany() == null) throw new BadRequestException("Chứng từ chưa được gán công ty");
        return store.read(document.getCompany().getId(), document.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "LIVE_SAMPLE_NOT_FOUND"));
    }

    private Sample create(Document document, LivePrediction prediction, Selection selection, Authentication authentication) {
        var sample = new Sample();
        sample.setDocumentId(document.getId());
        sample.setCompanyId(document.getCompany().getId());
        sample.setSnapshotId(prediction.snapshotId());
        sample.setFileVersionId(prediction.fileVersionId());
        sample.setSelection(selection);
        sample.setSelectedBy(authentication.getName());
        sample.setSelectedAt(Instant.now());
        LiveEvaluationData.FIELDS.forEach(key -> sample.getFields().put(key, Label.unchecked()));
        store.write(sample);
        return sample;
    }

    @Transactional(readOnly = true)
    public Summary select(Long documentId, Authentication authentication) {
        Document document = documents.load(documentId);
        if (document.getCompany() == null) throw new BadRequestException("Chứng từ chưa được gán công ty");
        if (!invoiceType(typeOf(document))) throw new BadRequestException("LIVE_SAMPLE_UNSUPPORTED: Hiện chỉ đánh giá được chứng từ dạng hóa đơn");
        var existing = store.read(document.getCompany().getId(), documentId);
        if (existing.isPresent()) return summary(existing.get(), document);
        var prediction = predictions.latest(documentId, fileVersion(document)).orElseThrow(() -> new BadRequestException(
                "AI_SNAPSHOT_REQUIRED: Chứng từ chưa có kết quả AI gốc trên phiên bản file hiện tại"));
        return summary(create(document, prediction, Selection.MANUAL, authentication), document);
    }

    /** Random per document type, so the set is not limited to documents someone chose after seeing their results. */
    @Transactional(readOnly = true)
    public RandomResult selectRandom(RandomRequest request, Authentication authentication) {
        int perType = request.perType() == null ? 10 : request.perType();
        if (perType < 1 || perType > 50) throw new BadRequestException("Số mẫu mỗi loại phải từ 1 đến 50");
        var page = documents.findPage(request.dateFrom(), request.dateTo(), request.companyId(), null, null, null, null, null,
                PageRequest.of(0, RANDOM_POOL, Sort.by(Sort.Direction.DESC, "createdAt")), authentication);
        var taken = store.list(request.companyId()).stream().map(Sample::getDocumentId).collect(Collectors.toSet());
        var candidates = page.getContent().stream().filter(d -> PROCESSED.contains(d.status()) && !taken.contains(d.id()))
                .map(d -> documents.load(d.id())).filter(d -> d.getCompany() != null && invoiceType(typeOf(d)))
                .collect(Collectors.groupingBy(LiveEvaluationService::typeOf));
        int added = 0, total = 0;
        for (var group : candidates.values()) {
            total += group.size();
            var shuffled = new ArrayList<>(group);
            Collections.shuffle(shuffled);
            int chosen = 0;
            for (Document document : shuffled) {
                if (chosen == perType) break;
                var prediction = predictions.latest(document.getId(), fileVersion(document));
                if (prediction.isEmpty()) continue;
                create(document, prediction.get(), Selection.RANDOM, authentication);
                chosen++;
            }
            added += chosen;
        }
        return new RandomResult(added, total);
    }

    private Summary summary(Sample sample, Document document) {
        return new Summary(sample.getDocumentId(), document.getOriginalFileName(), typeOf(document), sample.getStatus(), sample.getSelection(),
                !Objects.equals(sample.getFileVersionId(), fileVersion(document)), sample.isRevisedAfterReveal(), sample.getSelectedAt(), sample.getConfirmedAt());
    }

    @Transactional(readOnly = true)
    public List<Summary> list(Long companyId) {
        // documents.load keeps the caller's company scope, including for an accountant passing no company.
        var scoped = companyId != null ? store.list(companyId) : store.list(null);
        var summaries = new ArrayList<Summary>();
        for (Sample sample : scoped) {
            try { summaries.add(summary(sample, documents.load(sample.getDocumentId()))); }
            catch (com.example.invoice.exception.ResourceNotFoundException | org.springframework.security.access.AccessDeniedException outOfScope) { /* Not visible to this caller. */ }
        }
        summaries.sort(Comparator.comparing(Summary::selectedAt).reversed());
        return summaries;
    }

    @Transactional(readOnly = true)
    public Workspace workspace(Long documentId) {
        Document document = documents.load(documentId);
        Sample sample = required(document);
        boolean stale = !Objects.equals(sample.getFileVersionId(), fileVersion(document));
        // AI values stay on the server until the label is confirmed, so they cannot anchor the reviewer.
        Reveal reveal = sample.getStatus() == Status.CONFIRMED
                ? predictions.find(documentId, sample.getSnapshotId()).map(prediction -> reveal(sample, prediction)).orElse(null) : null;
        return new Workspace(sample, document.getOriginalFileName(), document.getFileType(), stale, LiveEvaluationData.FIELDS,
                LiveEvaluationScoring.reconciliationDraft(sample), reveal);
    }

    private Reveal reveal(Sample sample, LivePrediction prediction) {
        var truth = LiveEvaluationScoring.toSample(sample);
        var result = LiveEvaluationScoring.toResult(sample, prediction);
        var comparisons = new ArrayList<Comparison>();
        for (String key : LiveEvaluationData.FIELDS) {
            String expected = truth.fields().get(key), actual = result.fields().get(key);
            State state = sample.getFields().get(key).state();
            String outcome = state == State.UNREADABLE ? "EXCLUDED" : expected == null ? (actual == null ? "NOT_APPLICABLE" : "SPURIOUS")
                    : actual == null ? "MISSING" : EvaluationMetrics.normalize(expected).equals(EvaluationMetrics.normalize(actual)) ? "CORRECT" : "INCORRECT";
            comparisons.add(new Comparison(key, expected, prediction.fields().get(key), outcome));
        }
        return new Reveal(prediction.model(), prediction.documentType(), prediction.category(), comparisons, prediction.items());
    }

    private void requireRevision(Sample sample, Integer revision) {
        if (revision == null || revision != sample.getRevision())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "LIVE_SAMPLE_CHANGED");
    }

    private void requireOpen(Sample sample) {
        if (sample.getStatus() == Status.CONFIRMED) throw new BadRequestException("Nhãn đã xác nhận, cần mở lại trước khi sửa");
    }

    private static void requireLength(String value, int limit) {
        if (value != null && value.length() > limit) throw new BadRequestException("Giá trị nhãn quá dài");
    }

    @Transactional(readOnly = true)
    public Workspace save(Long documentId, DraftRequest request) {
        Document document = documents.load(documentId);
        Sample sample = required(document);
        requireOpen(sample);
        requireRevision(sample, request.revision());
        var fields = new LinkedHashMap<String, Label>();
        for (String key : LiveEvaluationData.FIELDS) {
            Label label = request.fields() == null ? null : request.fields().get(key);
            if (label != null) requireLength(label.value(), 2000);
            fields.put(key, label == null || label.state() == null ? Label.unchecked() : label);
        }
        var items = request.items() == null ? List.<Map<String, String>>of() : request.items();
        if (items.size() > 500) throw new BadRequestException("Quá nhiều dòng hàng");
        var rows = new ArrayList<Map<String, String>>();
        for (var row : items) {
            var cells = new LinkedHashMap<String, String>();
            for (String key : LiveEvaluationData.ITEM_FIELDS) {
                String value = row.get(key);
                requireLength(value, 2000);
                if (value != null && !value.isBlank()) cells.put(key, value);
            }
            if (!cells.isEmpty()) rows.add(cells);
        }
        requireLength(request.ocrText(), 20000);
        sample.setDocumentType(request.documentType() == null || request.documentType().isBlank() ? null : request.documentType().trim());
        sample.setFields(fields);
        sample.setCategory(request.category() == null || request.category().state() == null ? Label.unchecked() : request.category());
        sample.setItemsState(request.itemsState() == null ? State.UNCHECKED : request.itemsState());
        sample.setItems(rows);
        sample.setOcrText(request.ocrText() == null || request.ocrText().isBlank() ? null : request.ocrText());
        return write(sample, Status.DRAFT, documentId);
    }

    private Workspace write(Sample sample, Status status, Long documentId) {
        sample.setStatus(status);
        sample.setRevision(sample.getRevision() + 1);
        store.write(sample);
        return workspace(documentId);
    }

    /** Marks the transcript as OCR-assisted before handing out the text, so the shortcut is always recorded. */
    @Transactional(readOnly = true)
    public String ocrDraft(Long documentId) {
        Document document = documents.load(documentId);
        Sample sample = required(document);
        requireOpen(sample);
        String text = predictions.find(documentId, sample.getSnapshotId()).map(LivePrediction::ocrText).orElse(null);
        if (text == null) throw new BadRequestException("Lần xử lý này không có văn bản OCR");
        if (!sample.isOcrAssisted()) { sample.setOcrAssisted(true); store.write(sample); }
        return text;
    }

    private static void requireComplete(Sample sample) {
        if (sample.getDocumentType() == null) throw new BadRequestException("Chưa chọn loại chứng từ đúng");
        for (var entry : sample.getFields().entrySet()) requireChecked(entry.getKey(), entry.getValue());
        requireChecked("nhóm chi phí", sample.getCategory());
        if (sample.getItemsState() == State.UNCHECKED) throw new BadRequestException("Chưa kiểm tra bảng hàng hóa");
        if (sample.getItemsState() == State.PRESENT && sample.getItems().isEmpty())
            throw new BadRequestException("Bảng hàng hóa được đánh dấu có trên chứng từ nhưng chưa có dòng nào");
    }

    private static void requireChecked(String name, Label label) {
        if (label.state() == State.UNCHECKED) throw new BadRequestException("Chưa kiểm tra: " + name);
        if (label.state() == State.PRESENT && (label.value() == null || label.value().isBlank()))
            throw new BadRequestException("Chưa nhập giá trị đúng: " + name);
    }

    @Transactional(readOnly = true)
    public Workspace confirm(Long documentId, ConfirmRequest request, Authentication authentication) {
        Document document = documents.load(documentId);
        Sample sample = required(document);
        requireOpen(sample);
        requireRevision(sample, request.revision());
        if (!Objects.equals(sample.getFileVersionId(), fileVersion(document)))
            throw new BadRequestException("LIVE_SAMPLE_STALE: File đã thay đổi so với lần AI được đánh giá");
        requireComplete(sample);
        var computed = LiveEvaluationScoring.reconciliationDraft(sample);
        if (computed != null) {
            // Computed from the labels, then reviewed: a person may correct a status but not skip the review.
            if (request.reconciliation() == null || !computed.keySet().equals(request.reconciliation().keySet())
                    || !EvaluationService.RECONCILIATION_STATUSES.containsAll(request.reconciliation().values()))
                throw new BadRequestException("Cần xác nhận kết quả đối soát của từng phép kiểm tra");
            sample.setReconciliation(new LinkedHashMap<>(request.reconciliation()));
        } else sample.setReconciliation(null);
        sample.setConfirmedBy(authentication.getName());
        sample.setConfirmedAt(Instant.now());
        return write(sample, Status.CONFIRMED, documentId);
    }

    @Transactional(readOnly = true)
    public Workspace reopen(Long documentId, Integer revision) {
        Document document = documents.load(documentId);
        Sample sample = required(document);
        if (sample.getStatus() != Status.CONFIRMED) throw new BadRequestException("Nhãn chưa được xác nhận");
        requireRevision(sample, revision);
        sample.setRevisedAfterReveal(true);
        sample.setConfirmedBy(null);
        sample.setConfirmedAt(null);
        return write(sample, Status.DRAFT, documentId);
    }

    private boolean current(Sample sample) {
        try { return Objects.equals(sample.getFileVersionId(), fileVersion(documents.load(sample.getDocumentId()))); }
        catch (com.example.invoice.exception.ResourceNotFoundException removed) { return false; }
    }

    @Transactional(readOnly = true)
    public Report report(Long companyId) {
        var samples = store.list(companyId);
        var byModel = new TreeMap<String, List<Map.Entry<Sample, LivePrediction>>>();
        int stale = 0, unreadable = 0, assisted = 0, revised = 0, random = 0, confirmed = 0, labelling = 0, scored = 0;
        long totalMs = 0, timed = 0;
        for (Sample sample : samples) {
            if (sample.getSelection() == Selection.RANDOM) random++;
            if (sample.getStatus() == Status.DRAFT) labelling++;
            var prediction = predictions.find(sample.getDocumentId(), sample.getSnapshotId()).orElse(null);
            if (prediction != null && prediction.durationMs() != null) { totalMs += prediction.durationMs(); timed++; }
            if (sample.getStatus() != Status.CONFIRMED) continue;
            confirmed++;
            boolean current = current(sample);
            if (!current) stale++;
            if (!current || prediction == null) continue;
            scored++;
            byModel.computeIfAbsent(prediction.model() == null ? "UNKNOWN" : prediction.model(), key -> new ArrayList<>()).add(Map.entry(sample, prediction));
            unreadable += (int) sample.getFields().values().stream().filter(label -> label.state() == State.UNREADABLE).count();
            if (sample.isOcrAssisted() && sample.getOcrText() != null) assisted++;
            if (sample.isRevisedAfterReveal()) revised++;
        }
        var models = new ArrayList<ModelReport>();
        byModel.forEach((model, entries) -> models.add(new ModelReport(model, entries.size(), EvaluationMetrics.calculate(
                entries.stream().map(e -> LiveEvaluationScoring.toSample(e.getKey())).toList(),
                entries.stream().map(e -> LiveEvaluationScoring.toResult(e.getKey(), e.getValue())).toList()))));
        return new Report(samples.size(), labelling, confirmed, stale, scored, random, samples.size() - random, unreadable, assisted, revised,
                timed == 0 ? null : (double) totalMs / timed, models, List.of(
                "Chỉ tính mẫu đã xác nhận trên đúng phiên bản file mà AI đã xử lý; chưa có nhãn thì chưa có độ chính xác.",
                "Nhãn do người đối chiếu file gốc khi chưa thấy kết quả AI; không dùng dữ liệu nghiệp vụ đã sửa làm đáp án.",
                "Trường đánh dấu không đọc được bị loại khỏi mẫu số và báo riêng.",
                "Kết quả được tách theo model; chứng từ thực không chạy trên cùng cấu hình nên không dùng để so sánh các phương án."));
    }
}
