package com.example.invoice.service;

import com.example.invoice.dto.document.*;
import com.example.invoice.entity.*;
import com.example.invoice.exception.*;
import com.example.invoice.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Every remote attempt is bounded by committed start/end transactions, never an open DB lock. */
@Service
@RequiredArgsConstructor
public class PipelineExecutionService {
    @org.springframework.beans.factory.annotation.Autowired private DocumentAuditService documentAudit;
    private String before(Document d){return documentAudit==null?null:documentAudit.capture(d);}
    private void remember(Document d,String action,String previous){if(documentAudit!=null)documentAudit.append(d,action,previous);}

    public static final int MAX_ATTEMPTS = 3;
    private static final String TYPE = "OCR_AI_PIPELINE";
    private final DocumentService documents;
    private final DocumentRepository documentRepository;
    private final ProcessingJobRepository jobs;
    private final ProcessingAttemptRepository attempts;
    private final ObjectMapper mapper;
    private final jakarta.persistence.EntityManager entityManager;
    private final ProcessingLogRepository processingLogs;

    public record Run(Document document, Long jobId, Long attemptId, int attemptNumber) {}

    @Transactional
    public Run start(Long documentId, boolean reprocess, boolean retryOnly) {
        documents.load(documentId);
        Document document = documentRepository.findByIdForUpdate(documentId).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy chứng từ"));
        // Scope lookup may have loaded this entity before waiting on another request's lock.
        entityManager.refresh(document, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        documents.load(documentId);
        if (document.getReviewStatus() == ReviewStatus.REJECTED || document.getReviewStatus() == ReviewStatus.CORRECTED)
            throw conflict("RESUBMISSION_REQUIRED", "Chứng từ đã trả lại cần được gửi lại qua workflow trước khi xử lý.", 0);
        if (document.getReviewStatus() == ReviewStatus.APPROVED || document.getStatus() == DocumentStatus.COMPLETED)
            throw conflict("APPROVED_DOCUMENT", "Chứng từ đã được duyệt và không thể xử lý lại.", 0);
        if (document.getStatus() == DocumentStatus.PROCESSING)
            throw conflict("PROCESSING_IN_PROGRESS", "Chứng từ đang được xử lý. Không tạo thêm lần xử lý trùng.", 0);
        ProcessingJob job = jobs.findFirstByDocumentIdAndJobTypeOrderByIdDesc(documentId, TYPE).orElse(null);
        if (jobs.existsByDocumentIdAndJobTypeAndStatus(documentId, TYPE, "RUNNING"))
            throw conflict("PROCESSING_IN_PROGRESS", "Lượt xử lý trước vẫn đang hoạt động. Không thể tạo lượt trùng.", 0);
        boolean failed = document.getStatus() == DocumentStatus.FAILED || (job != null && "FAILED".equals(job.getStatus()));
        int count = job == null ? 0 : attempts.findByProcessingJobIdOrderByAttemptNumberAsc(job.getId()).size();
        if (retryOnly && !failed) throw conflict("RETRY_NOT_ALLOWED", "Chỉ thử lại chứng từ xử lý thất bại tạm thời.", count);
        if (failed) {
            PipelineFailure failure = job == null ? null : readFailure(job.getErrorMessage());
            if (failure == null || !failure.retryable() || !"FAILED".equals(job.getStatus()) || count >= MAX_ATTEMPTS)
                throw conflict("RETRY_NOT_ALLOWED", "Không thể tự thử lại: lỗi không tạm thời, thiếu lịch sử hoặc đã hết số lần thử.", count);
        }
        String previous=before(document);
        documents.claimForAiProcessing(documentId, reprocess);
        if (!failed) {
            job = new ProcessingJob(); job.setDocument(document); job.setJobType(TYPE);
            job.setPriority(0); count = 0;
        }
        job.setStatus("RUNNING"); job.setStartedAt(LocalDateTime.now()); job.setCompletedAt(null); job.setErrorMessage(null);
        jobs.saveAndFlush(job);
        var attempt = new ProcessingAttempt(); attempt.setProcessingJob(job); attempt.setAttemptNumber(count + 1);
        attempt.setStatus("RUNNING"); attempt.setStartedAt(LocalDateTime.now());
        attempts.saveAndFlush(attempt);
        append(job, "START", "PROCESSING", "Bắt đầu lần xử lý " + (count + 1));
        if (document.getCompany() != null) {
            document.getCompany().getCompanyName(); document.getCompany().getTaxCode();
        }
        remember(document,reprocess?"REPROCESS":"PROCESS_START",previous);
        return new Run(document, job.getId(), attempt.getId(), count + 1);
    }

    @Transactional
    public void complete(Run run) {
        var attempt = attempts.findById(run.attemptId()).orElseThrow();
        var job = attempt.getProcessingJob();String previous=before(job.getDocument());
        String status = job.getDocument().getStatus() == DocumentStatus.NEED_REVIEW ? "NEED_REVIEW" : "SUCCESS";
        attempt.setStatus(status); attempt.setCompletedAt(LocalDateTime.now());
        job.setStatus(status); job.setCompletedAt(attempt.getCompletedAt()); job.setErrorMessage(null);
        append(job, "COMPLETE", status, "Hoàn tất lần xử lý " + attempt.getAttemptNumber());
        remember(job.getDocument(),"PROCESS_COMPLETED",previous);
    }

    @Transactional
    public void fail(Run run, PipelineFailure failure) {
        var attempt = attempts.findById(run.attemptId()).orElseThrow();
        String previous=before(attempt.getProcessingJob().getDocument());
        String json = writeFailure(failure);
        boolean review = Set.of("VALIDATION_FAILED", "OCR_LOW_QUALITY").contains(failure.errorCode());
        attempt.setStatus(review ? "NEED_REVIEW" : "FAILED"); attempt.setCompletedAt(LocalDateTime.now()); attempt.setErrorMessage(json);
        var job = attempt.getProcessingJob(); job.setStatus(attempt.getStatus()); job.setCompletedAt(attempt.getCompletedAt()); job.setErrorMessage(json);
        Document document = documentRepository.findByIdForUpdate(run.document().getId()).orElseThrow();
        if (document.getReviewStatus() != ReviewStatus.APPROVED) document.setStatus(review ? DocumentStatus.NEED_REVIEW : DocumentStatus.FAILED);
        append(job, failure.failedStage(), attempt.getStatus(), failure.errorCode() + ": " + failure.userMessage());
        remember(document,"PROCESS_FAILED",previous);
    }

    @Transactional(readOnly = true)
    public PipelineHistory history(Long documentId) {
        Document document = documents.load(documentId);
        List<PipelineHistory.Attempt> history = new ArrayList<>();
        List<ProcessingJob> documentJobs = jobs.findByDocumentIdAndJobTypeOrderByIdDesc(documentId, TYPE);
        Map<String,Long> causes = new LinkedHashMap<>();
        for (var job : documentJobs) for (var attempt : attempts.findByProcessingJobIdOrderByAttemptNumberAsc(job.getId())) {
            PipelineFailure failure = readFailure(attempt.getErrorMessage());
            if (failure != null) causes.merge(failure.errorCode(), 1L, Long::sum);
            history.add(new PipelineHistory.Attempt(job.getId(), attempt.getAttemptNumber(), attempt.getStatus(),
                    attempt.getStartedAt(), attempt.getCompletedAt(), failure));
        }
        // Logical ordering survives clock skew between backend instances.
        history.sort(Comparator.comparing(PipelineHistory.Attempt::jobId).reversed()
                .thenComparing(Comparator.comparingInt(PipelineHistory.Attempt::attemptNumber).reversed()));
        var latest = documentJobs.isEmpty() ? null : documentJobs.getFirst();
        var failure = latest == null ? null : readFailure(latest.getErrorMessage());
        int count = latest == null ? 0 : attempts.findByProcessingJobIdOrderByAttemptNumberAsc(latest.getId()).size();
        boolean retry = document.getStatus() == DocumentStatus.FAILED && document.getReviewStatus() != ReviewStatus.APPROVED
                && latest != null && "FAILED".equals(latest.getStatus()) && failure != null && failure.retryable() && count < MAX_ATTEMPTS;
        return new PipelineHistory(retry, MAX_ATTEMPTS, history.size(), history.stream().filter(a -> "SUCCESS".equals(a.status())).count(),
                history.stream().filter(a -> "FAILED".equals(a.status())).count(),
                history.stream().filter(a -> "NEED_REVIEW".equals(a.status())).count(),
                history.stream().filter(a -> "RUNNING".equals(a.status())).count(), causes, history);
    }

    private PipelineException conflict(String code, String message, int count) {
        return new PipelineException(new PipelineFailure(code, "CLAIM", false, count, LocalDateTime.now(), message), 409);
    }
    private void append(ProcessingJob job, String step, String status, String message) {
        var entry = new ProcessingLog(); entry.setProcessingJob(job); entry.setDocument(job.getDocument());
        entry.setProcessType("AI_DOCUMENT"); entry.setAgentStep(step); entry.setStatus(status); entry.setMessage(message);
        processingLogs.save(entry);
    }
    private String writeFailure(PipelineFailure failure) {
        try { return mapper.writeValueAsString(failure); }
        catch (com.fasterxml.jackson.core.JsonProcessingException exception) { throw new IllegalStateException("Không thể lưu lịch sử lỗi", exception); }
    }
    private PipelineFailure readFailure(String json) {
        if (json == null) return null;
        try { return mapper.readValue(json, PipelineFailure.class); }
        catch (com.fasterxml.jackson.core.JsonProcessingException exception) { return null; }
    }
}
