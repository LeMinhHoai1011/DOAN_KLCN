package com.example.invoice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.example.invoice.entity.*;
import com.example.invoice.exception.BadRequestException;
import com.example.invoice.repository.DocumentReviewRepository;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReviewWorkflowServiceTest {
 @Mock DocumentReviewRepository reviews; @Mock DocumentService documents; @Mock UserService users; @Mock FinancialTransactionService transactions;
 private ReviewWorkflowService service() { lenient().when(reviews.save(any())).thenAnswer(i -> i.getArgument(0)); return new ReviewWorkflowService(reviews, documents, users, transactions); }
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
}
