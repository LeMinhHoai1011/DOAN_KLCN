package com.example.invoice.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.example.invoice.entity.*;
import com.example.invoice.repository.AuditLogRepository;
import com.example.invoice.repository.DocumentReviewRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReviewEffortServiceTest {
    @Mock DocumentReviewRepository reviews;
    @Mock AuditLogRepository audit;
    final LocalDateTime t0 = LocalDateTime.of(2026, 10, 1, 9, 0);

    ReviewEffortService service() { return new ReviewEffortService(reviews, audit, new ObjectMapper()); }

    Document document(long id, String type) { var d = new Document(); d.setId(id); d.setDocumentType(type); return d; }
    DocumentReview review(Document d, String note, LocalDateTime at, Long ms) {
        var r = new DocumentReview(); r.setDocument(d); r.setReviewNote(note); r.setReviewedAt(at); r.setActiveReviewMs(ms); return r;
    }
    AuditLog event(Document d, String action, LocalDateTime at, String before, String after) {
        var e = new AuditLog(); e.setEntityType(DocumentAuditService.TYPE); e.setEntityId(d.getId().toString());
        e.setAction(action); e.setCreatedAt(at); e.setOldValue(before); e.setNewValue(after); return e;
    }
    static String snapshot(String total) { return "{\"document\":{\"documentType\":\"VAT_INVOICE\"},\"invoice\":{\"totalAmount\":\"" + total + "\"},\"fields\":[]}"; }

    @Test void separatesTouchlessFromEditedApprovalsAndKeepsUnmeasuredTimeNull() {
        var untouched = document(1, "VAT_INVOICE"); var edited = document(2, "VAT_INVOICE"); var legacy = document(3, "RECEIPT");
        var approve1 = review(untouched, "APPROVE", t0.plusMinutes(5), 40_000L);
        var reject2 = review(edited, "REJECT: sai MST", t0.plusMinutes(1), 30_000L);
        var approve2 = review(edited, "APPROVE", t0.plusMinutes(9), 50_000L);
        var approve3 = review(legacy, "APPROVE", t0.plusMinutes(9), null);
        var all = List.of(approve1, reject2, approve2, approve3);
        when(reviews.findDecisionsBetween(any(), any())).thenReturn(all);
        when(reviews.findByDocumentIdIn(any())).thenReturn(all);
        var events = new ArrayList<AuditLog>();
        events.add(event(untouched, "AI_EXTRACT", t0, null, snapshot("100")));
        // Saving without a content change is not an edit.
        events.add(event(untouched, "INVOICE_UPDATE", t0.plusMinutes(2), snapshot("100"), snapshot("100")));
        events.add(event(edited, "AI_EXTRACT", t0, null, snapshot("100")));
        events.add(event(edited, "INVOICE_UPDATE", t0.plusMinutes(3), snapshot("100"), snapshot("110")));
        when(audit.findByEntityTypeAndEntityIdInOrderByCreatedAtAscIdAsc(any(), any())).thenReturn(events);

        var report = service().report(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 1));
        assertEquals(4, report.decisions());
        assertEquals(3, report.approvals());
        assertEquals(1, report.rejections());
        assertEquals(3, report.approvedDocuments());
        assertEquals(2, report.touchlessApproved());
        assertEquals(2.0 / 3, report.touchlessRate(), 1e-9);
        assertEquals(2, report.timedApprovedDocuments());
        assertEquals(60_000.0, report.averageReviewMsPerDocument()); // (40s + (30s + 50s)) / 2
        assertEquals(3, report.timedDecisions());
        assertEquals(1, report.byDocumentType().get("VAT_INVOICE").touchless());
        assertNull(report.byDocumentType().get("RECEIPT").averageReviewMs());
    }

    @Test void editsBeforeLatestAiProposalOrAfterApprovalAreIgnored() {
        var d = document(1, "VAT_INVOICE");
        var events = List.of(
                event(d, "INVOICE_UPDATE", t0, snapshot("1"), snapshot("2")),
                event(d, "AI_EXTRACT", t0.plusMinutes(1), null, snapshot("2")),
                event(d, "INVOICE_UPDATE", t0.plusMinutes(10), snapshot("2"), snapshot("3")));
        assertEquals(0, service().humanEdits(events, t0.plusMinutes(5)));
    }

    @Test void rejectsInvertedRange() {
        assertThrows(IllegalArgumentException.class, () -> service().report(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 1, 1)));
    }
}
