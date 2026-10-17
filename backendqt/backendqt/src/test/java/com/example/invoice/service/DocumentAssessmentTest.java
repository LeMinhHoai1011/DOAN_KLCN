package com.example.invoice.service;

import com.example.invoice.dto.document.DocumentAssessment;
import com.example.invoice.entity.*;
import com.example.invoice.repository.ProcessingLogRepository;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DocumentAssessmentTest {
    @Test
    void fullWarningsSurviveSmallLogColumnsAndOldAttemptsAreNotReused() {
        var repository = mock(ProcessingLogRepository.class);
        var documents = mock(DocumentService.class);
        var document = new Document(); document.setId(1L); document.setStatus(DocumentStatus.NEED_REVIEW);
        when(documents.load(1L)).thenReturn(document);
        List<ProcessingLog> logs = new ArrayList<>();
        when(repository.save(any())).thenAnswer(call -> {
            ProcessingLog entry = call.getArgument(0); entry.setId((long) logs.size() + 1);
            assertTrue(entry.getMessage().length() <= 255);
            logs.add(entry); return entry;
        });
        when(repository.findByDocumentIdOrderByCreatedAtAsc(1L)).thenReturn(logs);
        var service = new ProcessingLogService(repository, documents);
        var unknown = new DocumentAssessment.Confidence(null, "model", "NOT_PROVIDED");
        String message = "CRITICAL: cảnh báo đầy đủ ".repeat(100);
        var assessment = new DocumentAssessment(unknown, unknown,
                new DocumentAssessment.Confidence(new BigDecimal("0.99"), "ollama/qwen", "MODEL_REPORTED"),
                "NEED_REVIEW", List.of(new DocumentAssessment.Warning("TOTAL_MISMATCH", "CRITICAL", message, "totalAmount")),
                List.of(message));
        service.recordAssessment(1L, assessment);
        assertEquals(assessment, service.assessment(1L));
		service.recordAssessment(1L, new DocumentAssessment(unknown, unknown, unknown, "VALID", List.of(), List.of()));
		assertTrue(service.assessment(1L).reviewReasons().getFirst().startsWith("WORKFLOW_REVIEW_REQUIRED"));
        service.append(1L, "AI_DOCUMENT", "START", "PROCESSING", null, null, "reprocess");
        var current = service.assessment(1L);
        assertNull(current.classificationConfidence().value());
        assertEquals("UNKNOWN", current.validationStatus());
    }

    @Test
    void assessmentChecksDocumentScopeBeforeReadingLogs() {
        var repository = mock(ProcessingLogRepository.class);
        var documents = mock(DocumentService.class);
        when(documents.load(9L)).thenThrow(new org.springframework.security.access.AccessDeniedException("scope"));
        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> new ProcessingLogService(repository, documents).assessment(9L));
        verifyNoInteractions(repository);
    }
}
