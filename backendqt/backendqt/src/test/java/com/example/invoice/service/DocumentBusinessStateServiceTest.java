package com.example.invoice.service;
import com.example.invoice.entity.*;
import com.example.invoice.repository.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
class DocumentBusinessStateServiceTest {
 DocumentService docs=mock(DocumentService.class); ClassificationRepository cs=mock(ClassificationRepository.class);
 ReconciliationRepository rs=mock(ReconciliationRepository.class); DocumentReviewRepository reviews=mock(DocumentReviewRepository.class);
 DocumentBusinessStateService service=new DocumentBusinessStateService(docs,cs,rs,reviews); Document d;
 @BeforeEach void setup(){d=new Document();d.setId(1L);d.setStatus(DocumentStatus.PROCESSED);when(docs.load(1L)).thenReturn(d);}
 @Test void classifiedDoesNotMeanApprovedOrPaid(){var c=new Classification();c.setStatus(ClassificationStatus.CLASSIFIED);when(cs.findFirstByDocumentIdOrderByCreatedAtDesc(1L)).thenReturn(Optional.of(c));var state=service.get(1L);assertEquals("COMPLETED",state.classification());assertEquals("PENDING",state.review());assertNull(state.payment());assertEquals("NOT_RUN",state.reconciliation());verify(rs).findByDocumentIdOrderByCreatedAtDesc(1L);verifyNoMoreInteractions(rs);}
 @Test void approvedLegacyCompletedIsProcessedButPaymentUnknown(){d.setReviewStatus(ReviewStatus.APPROVED);d.setStatus(DocumentStatus.COMPLETED);var state=service.get(1L);assertEquals("PROCESSED",state.processing());assertEquals("APPROVED",state.review());assertNull(state.payment());}
 @Test void rejectedAndReconciliationWarningRemainIndependent(){d.setReviewStatus(ReviewStatus.REJECTED);d.setStatus(DocumentStatus.NEED_REVIEW);var r=new Reconciliation();r.setId(1L);r.setStatus("INSUFFICIENT_DATA");when(rs.findByDocumentIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(r));var state=service.get(1L);assertEquals("REJECTED",state.review());assertEquals("WARNING",state.reconciliation());}
 @Test void acceptedChecksAreReportedApartFromMatched(){var a=new Reconciliation();a.setComment("TOTAL: chenh lech");a.setStatus("ACCEPTED");var m=new Reconciliation();m.setComment("SUBTOTAL: ok");m.setStatus("MATCHED");when(rs.findByDocumentIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(a,m));assertEquals("ACCEPTED",service.get(1L).reconciliation());}
 @Test void invoiceEditedAfterRunIsStaleEvenWhenChecksMatched(){var batch=new ReconciliationBatch();batch.setCreatedAt(java.time.LocalDateTime.now());var r=new Reconciliation();r.setComment("TOTAL: ok");r.setStatus("MATCHED");r.setReconciliationBatch(batch);var invoice=new Invoice();invoice.setUpdatedAt(java.time.LocalDateTime.now().plusMinutes(1));d.setInvoice(invoice);when(rs.findByDocumentIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(r));var state=service.get(1L);assertEquals("WARNING",state.reconciliation());assertTrue(state.reconciliationStale());}
 @Test void latestStartReviewAndCorrectionHistoryAreProjectedWithoutWriting(){var r=new DocumentReview();r.setId(1L);r.setReviewNote("START_REVIEW");when(reviews.findByDocumentId(1L)).thenReturn(List.of(r));assertEquals("IN_REVIEW",service.get(1L).review());d.setReviewStatus(ReviewStatus.CORRECTED);assertEquals("PENDING",service.get(1L).review());assertEquals(1,service.get(1L).history().size());verify(reviews,never()).save(any());verify(rs,never()).save(any());}
}
