package com.example.invoice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.example.invoice.entity.*;
import com.example.invoice.exception.BadRequestException;
import com.example.invoice.repository.DocumentReviewRepository;
import com.example.invoice.repository.ReconciliationRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReviewWorkflowServiceTest {
 @Mock DocumentReviewRepository reviews; @Mock DocumentService documents; @Mock UserService users; @Mock FinancialTransactionService transactions; @Mock ReconciliationRepository reconciliations;
 private ReviewWorkflowService service() { lenient().when(reviews.save(any())).thenAnswer(i -> i.getArgument(0)); return new ReviewWorkflowService(reviews, documents, users, transactions, reconciliations); }
 private User actor(String code) { Role role = new Role(); role.setCode(code); UserRoleAssignment assignment = new UserRoleAssignment(); assignment.setRole(role); User user = new User(); user.setUserRoles(Set.of(assignment)); return user; }
 private Document document(DocumentStatus status, ReviewStatus review) { Document d = new Document(); d.setId(4L); d.setStatus(status); d.setReviewStatus(review); return d; }
 private void current(User user, Document document) { when(users.loadCurrent(null)).thenReturn(user); when(documents.lockForWorkflow(4L)).thenReturn(document); }

 @Test void startReviewCreatesPendingHistory() { Document d=document(DocumentStatus.PROCESSED,ReviewStatus.PENDING); User u=actor("ACCOUNTANT"); current(u,d); service().execute(4L,"START_REVIEW",null,null); assertEquals(DocumentStatus.NEED_REVIEW,d.getStatus()); ArgumentCaptor<DocumentReview> c=ArgumentCaptor.forClass(DocumentReview.class); verify(reviews).save(c.capture()); assertEquals(u,c.getValue().getReviewer()); assertEquals(ReviewStatus.PENDING,c.getValue().getReviewStatus()); }
 @Test void employeeCannotStartReviewOrApprove() { Document d=document(DocumentStatus.PROCESSED,ReviewStatus.PENDING); current(actor("EMPLOYEE"),d); assertThrows(org.springframework.security.access.AccessDeniedException.class,()->service().execute(4L,"START_REVIEW",null,null)); assertThrows(org.springframework.security.access.AccessDeniedException.class,()->service().execute(4L,"APPROVE",null,null)); verify(reviews,never()).save(any()); }
 @Test void approveCompletesReviewAndPersistsHistoryWithoutCreatingCash() { Document d=document(DocumentStatus.NEED_REVIEW,ReviewStatus.PENDING); User u=actor("ADMIN"); current(u,d); service().execute(4L,"APPROVE",null,null); assertEquals(DocumentStatus.COMPLETED,d.getStatus()); assertEquals(ReviewStatus.APPROVED,d.getReviewStatus()); verify(reviews).save(any(DocumentReview.class)); verify(transactions,never()).createFromApprovedDocument(d,u); }
 @Test void rejectAndRequestInfoPersistReasons() { Document rejected=document(DocumentStatus.PROCESSED,ReviewStatus.PENDING); current(actor("ACCOUNTANT"),rejected); service().execute(4L,"REJECT","missing tax code",null); assertEquals(ReviewStatus.REJECTED,rejected.getReviewStatus()); ArgumentCaptor<DocumentReview> c=ArgumentCaptor.forClass(DocumentReview.class); verify(reviews).save(c.capture()); assertEquals("REJECT: missing tax code",c.getValue().getReviewNote()); reset(reviews); Document info=document(DocumentStatus.PROCESSED,ReviewStatus.PENDING); current(actor("ACCOUNTANT"),info); service().execute(4L,"REQUEST_INFO","attach receipt",null); assertEquals(ReviewStatus.CORRECTED,info.getReviewStatus()); verify(reviews).save(any(DocumentReview.class)); }
 @Test void resubmitRequiresReturnedState() { Document d=document(DocumentStatus.NEED_REVIEW,ReviewStatus.REJECTED); current(actor("EMPLOYEE"),d); service().execute(4L,"RESUBMIT",null,null); assertEquals(DocumentStatus.UPLOADED,d.getStatus()); assertEquals(ReviewStatus.PENDING,d.getReviewStatus()); Document invalid=document(DocumentStatus.UPLOADED,ReviewStatus.PENDING); current(actor("EMPLOYEE"),invalid); assertThrows(BadRequestException.class,()->service().execute(4L,"RESUBMIT",null,null)); }

 @Test void repeatedApprovalAndLockedLegacyStateCannotCreateAnotherTransaction() {Document d=document(DocumentStatus.PROCESSED,ReviewStatus.PENDING);current(actor("ADMIN"),d);service().execute(4L,"APPROVE",null,null);assertThrows(BadRequestException.class,()->service().execute(4L,"APPROVE",null,null));verify(transactions,never()).createFromApprovedDocument(any(),any());verify(reviews,times(1)).save(any());}
 @Test void rejectedDocumentRequiresResubmissionAndKeepsHistory(){Document d=document(DocumentStatus.NEED_REVIEW,ReviewStatus.REJECTED);current(actor("ACCOUNTANT"),d);assertThrows(BadRequestException.class,()->service().execute(4L,"APPROVE",null,null));verifyNoInteractions(transactions);}
 @Test void inactiveReviewerCannotApprove(){Document d=document(DocumentStatus.PROCESSED,ReviewStatus.PENDING);User user=actor("ADMIN");user.getUserRoles().iterator().next().getRole().setActive(false);current(user,d);assertThrows(org.springframework.security.access.AccessDeniedException.class,()->service().execute(4L,"APPROVE",null,null));verifyNoInteractions(transactions);}
 private Document invoiceDocument() { Document d=document(DocumentStatus.NEED_REVIEW,ReviewStatus.PENDING); Invoice invoice=new Invoice(); invoice.setUpdatedAt(LocalDateTime.now().minusHours(1)); d.setInvoice(invoice); return d; }
 private Reconciliation check(String type,String status) { ReconciliationBatch batch=new ReconciliationBatch(); batch.setId(1L); batch.setCreatedAt(LocalDateTime.now()); Reconciliation r=new Reconciliation(); r.setReconciliationBatch(batch); r.setComment(type+": note"); r.setStatus(status); return r; }
 private void results(Reconciliation... rows) { when(reconciliations.findByDocumentIdOrderByCreatedAtDesc(4L)).thenReturn(List.of(rows)); }
 private void assertApprovalBlocked(Document d,String message) { BadRequestException e=assertThrows(BadRequestException.class,()->service().execute(4L,"APPROVE",null,null)); assertEquals(true,e.getMessage().startsWith("RECONCILIATION_REQUIRED: ")); assertEquals(true,e.getMessage().contains(message),e.getMessage()); assertEquals(ReviewStatus.PENDING,d.getReviewStatus()); verify(reviews,never()).save(any()); }

 @Test void invoiceCannotBeApprovedBeforeReconciliation(){Document d=invoiceDocument();current(actor("ACCOUNTANT"),d);assertApprovalBlocked(d,"Cần chạy đối soát");}
 @Test void unresolvedChecksBlockApprovalAndAreNamed(){Document d=invoiceDocument();current(actor("ACCOUNTANT"),d);results(check("TOTAL","MISMATCH"),check("SUBTOTAL","INSUFFICIENT_DATA"),check("VAT_TOTAL","MATCHED"));assertApprovalBlocked(d,"Còn 2 kiểm tra đối soát chưa xử lý: TOTAL, SUBTOTAL");}
 @Test void reopenedDecisionBlocksApproval(){Document d=invoiceDocument();current(actor("ADMIN"),d);results(check("TOTAL","NEED_REVIEW"));assertApprovalBlocked(d,"TOTAL");}
 @Test void invoiceEditedAfterLatestRunBlocksApproval(){Document d=invoiceDocument();d.getInvoice().setUpdatedAt(LocalDateTime.now().plusMinutes(1));current(actor("ACCOUNTANT"),d);results(check("TOTAL","MATCHED"));assertApprovalBlocked(d,"cần chạy lại đối soát");}
 @Test void matchedAndAcceptedChecksAllowApproval(){Document d=invoiceDocument();current(actor("ACCOUNTANT"),d);results(check("TOTAL","ACCEPTED"),check("SUBTOTAL","MATCHED"));service().execute(4L,"APPROVE",null,null);assertEquals(ReviewStatus.APPROVED,d.getReviewStatus());}
 @Test void onlyLatestRunCounts(){Document d=invoiceDocument();current(actor("ACCOUNTANT"),d);Reconciliation old=check("TOTAL","MISMATCH");old.getReconciliationBatch().setId(0L);results(check("TOTAL","MATCHED"),old);service().execute(4L,"APPROVE",null,null);assertEquals(ReviewStatus.APPROVED,d.getReviewStatus());}
 @Test void rejectAndRequestInfoStayAvailableWhileReconciliationIsUnresolved(){Document d=invoiceDocument();current(actor("ACCOUNTANT"),d);service().execute(4L,"REJECT","sai tong tien",null);assertEquals(ReviewStatus.REJECTED,d.getReviewStatus());verifyNoInteractions(reconciliations);
  Document info=invoiceDocument();current(actor("ACCOUNTANT"),info);service().execute(4L,"REQUEST_INFO","bo sung",null);assertEquals(ReviewStatus.CORRECTED,info.getReviewStatus());verifyNoInteractions(reconciliations);}
 @Test void decisionsKeepMeasuredReviewTimeWithinBounds(){Document d=document(DocumentStatus.NEED_REVIEW,ReviewStatus.PENDING);current(actor("ACCOUNTANT"),d);service().execute(4L,"APPROVE",null,90_000L,null);ArgumentCaptor<DocumentReview> c=ArgumentCaptor.forClass(DocumentReview.class);verify(reviews).save(c.capture());assertEquals(90_000L,c.getValue().getActiveReviewMs());
  reset(reviews);Document idle=document(DocumentStatus.NEED_REVIEW,ReviewStatus.PENDING);current(actor("ACCOUNTANT"),idle);service().execute(4L,"REJECT","sai MST",ReviewWorkflowService.MAX_ACTIVE_REVIEW_MS+1,null);c=ArgumentCaptor.forClass(DocumentReview.class);verify(reviews).save(c.capture());assertNull(c.getValue().getActiveReviewMs());
  reset(reviews);Document start=document(DocumentStatus.PROCESSED,ReviewStatus.PENDING);current(actor("ACCOUNTANT"),start);service().execute(4L,"START_REVIEW",null,5_000L,null);c=ArgumentCaptor.forClass(DocumentReview.class);verify(reviews).save(c.capture());assertNull(c.getValue().getActiveReviewMs());}
}
