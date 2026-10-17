package com.example.invoice;

import com.example.invoice.dto.ai.AiDocumentResult;
import com.example.invoice.ai.AiDocumentResultValidator;
import com.example.invoice.entity.*;
import com.example.invoice.exception.PipelineException;
import com.example.invoice.repository.*;
import com.example.invoice.service.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:pipeline_retry_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE"})
@ActiveProfiles("test")
class PipelineRetryIntegrationTest {
    @Autowired PipelineExecutionService execution;
    @Autowired DocumentRepository documents;
    @Autowired ProcessingJobRepository jobs;
    @Autowired ProcessingAttemptRepository attempts;
    @Autowired InvoiceRepository invoices;
    @Autowired DocumentTypeRepository types;
    @Autowired ExtractedFieldRepository fields;
    @Autowired FieldCorrectionRepository corrections;
    @Autowired ClassificationRepository classifications;
    @Autowired DocumentAiResultPersistenceService persistence;
    @Autowired DocumentAiProcessingService processor;
    @Autowired CompanyRepository companies;
    @MockitoBean DocumentService scopedDocuments;
    @MockitoBean com.example.invoice.ai.AiProcessingService ai;
    @MockitoBean AdaptiveOcrService ocr;

    @BeforeEach void scopeAndClaim() {
        reset(scopedDocuments, ai, ocr);
        when(scopedDocuments.load(anyLong())).thenAnswer(call -> documents.findById(call.getArgument(0)).orElseThrow());
        when(scopedDocuments.claimForAiProcessing(anyLong(), anyBoolean())).thenAnswer(call -> {
            var document = documents.findById((Long)call.getArgument(0)).orElseThrow();
            document.setStatus(DocumentStatus.PROCESSING); return document;
        });
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"STORAGE_FAILED", "AI_TIMEOUT", "AI_INVALID_RESPONSE"})
    void realPipelinePersistsSafeFailureAndDoesNotAutomaticallyRetry(String code) {
        types.findByCode("OTHER").orElseGet(() -> { var t = new DocumentType(); t.setCode("OTHER"); t.setName("Other"); return types.saveAndFlush(t); });
        var company = new Company(); company.setCompanyName("Isolated test company"); company = companies.saveAndFlush(company);
        var document = document(); document.setCompany(company); document = documents.saveAndFlush(document); long id = document.getId();
        if (code.equals("STORAGE_FAILED")) doThrow(new IllegalStateException("private bucket", new java.net.ConnectException("private host"))).when(scopedDocuments).loadFileBytes(id);
        else {
            when(scopedDocuments.loadFileBytes(id)).thenReturn(new byte[] {1});
            when(ocr.recognize(any(), any(), anyInt())).thenReturn(new AdaptiveOcrService.AdaptiveOcrResult(
                    new OcrPageResult(1, 10, 10, "text", 10, List.of(), 1, null, "vie"),
                    new byte[] {1}, "image/png", OcrProcessingProfile.NORMAL, .1, 1, null, null));
            when(ai.analyzeDocument(any())).thenThrow(new com.example.invoice.exception.AiProviderException(
                    code.equals("AI_TIMEOUT") ? "OLLAMA_TIMEOUT: token=private-token" : "OLLAMA_INVALID_JSON: invoice-private"));
        }
        PipelineException error = assertThrows(PipelineException.class, () -> processor.process(id, false));
        assertEquals(code, error.failure().errorCode()); assertEquals(1, error.failure().attemptCount());
        assertFalse(error.failure().userMessage().contains("private"));
        var history = execution.history(id);
        assertEquals(1, history.attemptCount()); assertEquals(1, history.failed());
        assertEquals(!code.equals("AI_INVALID_RESPONSE"), history.retryAllowed());
        assertEquals(code, history.attempts().getFirst().failure().errorCode());
        assertEquals(code.equals("STORAGE_FAILED") ? "STORAGE" : "AI_EXTRACTION", history.attempts().getFirst().failure().failedStage());
        assertEquals(DocumentStatus.FAILED, documents.findById(id).orElseThrow().getStatus());
        if (!code.equals("STORAGE_FAILED")) verify(ai, times(1)).analyzeDocument(any());
    }

    @Test void concurrentRetriesCreateOneAttemptAndLimitIsEnforcedAcrossRequests() throws Exception {
        long id = document().getId();
        var first = execution.start(id, false, false);
        execution.fail(first, PipelineFailures.create("AI_TIMEOUT", "AI_EXTRACTION", true, 1));
        assertTrue(execution.history(id).retryAllowed());
        var barrier = new CyclicBarrier(2); var pool = Executors.newFixedThreadPool(2);
        List<PipelineExecutionService.Run> accepted = new ArrayList<>();
        try {
            Callable<PipelineExecutionService.Run> retry = () -> {
                barrier.await(10, TimeUnit.SECONDS);
                try { return execution.start(id, true, true); } catch (PipelineException conflict) { assertEquals(409, conflict.httpStatus()); return null; }
            };
            var a = pool.submit(retry); var b = pool.submit(retry);
            for (var future : List.of(a,b)) { var run = future.get(20, TimeUnit.SECONDS); if (run != null) accepted.add(run); }
        } finally { pool.shutdownNow(); }
        assertEquals(1, accepted.size());
        assertEquals(2, attempts.findByProcessingJobIdOrderByAttemptNumberAsc(first.jobId()).size());
        var second = accepted.getFirst(); execution.fail(second, PipelineFailures.create("AI_TIMEOUT", "AI_EXTRACTION", true, 2));
        var third = execution.start(id, true, true); execution.fail(third, PipelineFailures.create("AI_TIMEOUT", "AI_EXTRACTION", true, 3));
        var shiftedClock = attempts.findById(third.attemptId()).orElseThrow();
        shiftedClock.setStartedAt(java.time.LocalDateTime.now().minusDays(1)); attempts.saveAndFlush(shiftedClock);
        assertEquals(3, execution.history(id).attempts().getFirst().attemptNumber());
        assertFalse(execution.history(id).retryAllowed());
        assertEquals(3, execution.history(id).failed());
        assertEquals(3L, execution.history(id).failuresByCode().get("AI_TIMEOUT"));
        assertThrows(PipelineException.class, () -> execution.start(id, true, true));
        assertEquals(List.of(1,2,3), attempts.findByProcessingJobIdOrderByAttemptNumberAsc(first.jobId()).stream().map(ProcessingAttempt::getAttemptNumber).toList());
        var resetLabel = documents.findById(id).orElseThrow(); resetLabel.setStatus(DocumentStatus.UPLOADED); documents.saveAndFlush(resetLabel);
        assertThrows(PipelineException.class, () -> execution.start(id, false, false));
        assertEquals(1, jobs.findByDocumentIdAndJobTypeOrderByIdDesc(id, "OCR_AI_PIPELINE").size());
    }

    @Test void deterministicFailureLegacyAndApprovedDocumentsCannotBeRetried() {
        long id = document().getId(); var run = execution.start(id, false, false);
        execution.fail(run, PipelineFailures.create("AI_INVALID_RESPONSE", "AI_EXTRACTION", false, 1));
        assertFalse(execution.history(id).retryAllowed()); assertThrows(PipelineException.class, () -> execution.start(id, true, true));
        var legacy = document(); legacy.setStatus(DocumentStatus.FAILED); documents.saveAndFlush(legacy);
        assertThrows(PipelineException.class, () -> execution.start(legacy.getId(), true, true));
        var approved = document(); approved.setReviewStatus(ReviewStatus.APPROVED); documents.saveAndFlush(approved);
        assertThrows(PipelineException.class, () -> execution.start(approved.getId(), true, false));
        assertTrue(jobs.findByDocumentIdAndJobTypeOrderByIdDesc(approved.getId(), "OCR_AI_PIPELINE").isEmpty());
    }

    @Test void successfulRetryPreservesManualInvoiceCorrectionsAndDoesNotDuplicateAiResults() {
        var document = document(); long id = document.getId();
        types.findByCode("INVOICE").orElseGet(() -> { var t = new DocumentType(); t.setCode("INVOICE"); t.setName("Invoice"); return types.saveAndFlush(t); });
        var invoice = new Invoice(); invoice.setDocument(document); invoice.setInvoiceNumber("MANUAL-1");
        invoice.setTotalAmount(new BigDecimal("123")); invoice.setAiGenerated(false); invoice = invoices.saveAndFlush(invoice);
        var field = new ExtractedField(); field.setDocument(document); field.setFieldName("projectCode"); field.setFieldValue("AI-OLD"); field.setSource("AI"); field = fields.saveAndFlush(field);
        var correction = new FieldCorrection(); correction.setField(field); correction.setNewValue("MANUAL-PROJECT"); corrections.saveAndFlush(correction);
        var first = execution.start(id, false, false); execution.fail(first, PipelineFailures.create("AI_TIMEOUT", "AI_EXTRACTION", true, 1));
        var retry = execution.start(id, true, true);
        var item = new AiDocumentResult.AiInvoiceItemExtraction("Test", BigDecimal.ONE, "unit", new BigDecimal("100"), new BigDecimal("0.1"), BigDecimal.TEN, new BigDecimal("100"));
        var extracted = new AiDocumentResult.AiInvoiceExtraction("AI-NEW", null, "2026-10-16", "Seller", "0312345678", null, null, null, null,
                new BigDecimal("100"), BigDecimal.TEN, new BigDecimal("110"), List.of(item));
        var result = new AiDocumentResult("ollama", "qwen", "INVOICE", new BigDecimal("0.95"), null, extracted,
                List.of(new AiDocumentResult.AiExtractedField("projectCode", "AI-NEW", new BigDecimal("0.95"))), List.of("DOCUMENT_TYPE_REASON: test"), "raw", 1);
        var validated = new AiDocumentResultValidator().validate(result, Set.of("INVOICE"));
        persistence.persist(id, validated, null); persistence.persist(id, validated, null); execution.complete(retry);
        assertEquals(invoice.getId(), invoices.findByDocumentId(id).orElseThrow().getId());
        assertEquals("MANUAL-1", invoices.findByDocumentId(id).orElseThrow().getInvoiceNumber());
        assertEquals(0, new BigDecimal("123").compareTo(invoices.findByDocumentId(id).orElseThrow().getTotalAmount()));
        assertEquals(1, fields.findByDocumentId(id).size());
        assertEquals("MANUAL-PROJECT", corrections.findFirstByFieldIdOrderByCreatedAtDescIdDesc(field.getId()).orElseThrow().getNewValue());
        assertEquals(1, classifications.findAll().stream().filter(c -> c.getDocument().getId().equals(id)).count());
        assertEquals(1, execution.history(id).succeeded()); assertEquals(1, execution.history(id).failed());
        assertEquals(ReviewStatus.PENDING, documents.findById(id).orElseThrow().getReviewStatus());
    }

    @Test void scopeIsCheckedBeforeAnyHistoryOrAttemptWrite() {
        long before = jobs.count(); doThrow(new org.springframework.security.access.AccessDeniedException("scope")).when(scopedDocuments).load(98765L);
        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> execution.history(98765L));
        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> execution.start(98765L, true, true));
        assertEquals(before, jobs.count());
    }

    @Test void completedBusinessReviewIsNotCountedAsTechnicalFailureOrStillRunning() {
        var document = document(); var run = execution.start(document.getId(), false, false);
        document.setStatus(DocumentStatus.NEED_REVIEW); documents.saveAndFlush(document);
        execution.complete(run);
        var history = execution.history(document.getId());
        assertEquals(1, history.needsReview()); assertEquals(0, history.failed()); assertEquals(0, history.running());
        assertFalse(history.retryAllowed()); assertEquals("NEED_REVIEW", history.attempts().getFirst().status());
    }

    @Test void changingDocumentStatusCannotBypassAnActiveJob() {
        var document = document(); var run = execution.start(document.getId(), false, false);
        document.setStatus(DocumentStatus.UPLOADED); documents.saveAndFlush(document);
        assertThrows(PipelineException.class, () -> execution.start(document.getId(), false, false));
        assertEquals(1, attempts.findByProcessingJobIdOrderByAttemptNumberAsc(run.jobId()).size());
    }

    private Document document() {
        var document = new Document(); document.setOriginalFileName("test.png"); document.setFileType("image/png"); document.setFileSize(1L);
        return documents.saveAndFlush(document);
    }
}
