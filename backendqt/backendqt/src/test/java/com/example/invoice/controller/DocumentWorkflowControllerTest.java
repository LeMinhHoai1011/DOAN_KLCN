package com.example.invoice.controller;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.invoice.dto.document.DocumentResponse;
import com.example.invoice.dto.document.ReviewActionRequest;
import com.example.invoice.exception.BadRequestException;
import com.example.invoice.service.DocumentAiProcessingService;
import com.example.invoice.service.ReviewWorkflowService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DocumentWorkflowControllerTest {
  @Mock private ReviewWorkflowService workflow;
  @Mock private DocumentAiProcessingService processing;

  @Test void submitInvokesCanonicalProcessingBoundaryOnce() {
    DocumentResponse response = null;
    when(workflow.execute(9L, "SUBMIT", null, null)).thenReturn(response);
    new DocumentWorkflowController(workflow, processing).act(9L, new ReviewActionRequest("SUBMIT", null), null);
    verify(processing).process(9L, false);
  }

  @Test void invalidSubmitDoesNotInvokeProcessing() {
    when(workflow.execute(9L, "SUBMIT", null, null)).thenThrow(new BadRequestException("invalid state"));
    assertThrows(BadRequestException.class, () -> new DocumentWorkflowController(workflow, processing).act(9L, new ReviewActionRequest("SUBMIT", null), null));
    verify(processing, never()).process(9L, false);
  }

  @Test void resubmitInvokesProcessingAndProcessingFailurePropagates() {
    when(workflow.execute(9L, "RESUBMIT", null, null)).thenReturn(null);
    org.mockito.Mockito.doThrow(new IllegalStateException("AI unavailable")).when(processing).process(9L, false);
    assertThrows(IllegalStateException.class, () -> new DocumentWorkflowController(workflow, processing).act(9L, new ReviewActionRequest("RESUBMIT", null), null));
    verify(processing).process(9L, false);
  }
}
