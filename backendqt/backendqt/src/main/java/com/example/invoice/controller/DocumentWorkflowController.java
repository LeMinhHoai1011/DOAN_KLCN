package com.example.invoice.controller;

import com.example.invoice.dto.document.DocumentResponse;
import com.example.invoice.dto.document.ReviewActionRequest;
import com.example.invoice.service.ReviewWorkflowService;
import com.example.invoice.service.DocumentAiProcessingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Single workflow endpoint; authorization and transition validation live in the service. */
@RestController
@RequestMapping("/api/v1/documents/{documentId}/workflow")
@RequiredArgsConstructor
public class DocumentWorkflowController {
	private final ReviewWorkflowService reviewWorkflowService;
	private final DocumentAiProcessingService documentAiProcessingService;

	@PostMapping
	public DocumentResponse act(@PathVariable Long documentId, @Valid @RequestBody ReviewActionRequest request,
			Authentication authentication) {
		DocumentResponse response = reviewWorkflowService.execute(documentId, request.action(), request.note(), authentication);
		String action = request.action().trim().toUpperCase(java.util.Locale.ROOT);
		if ("SUBMIT".equals(action) || "RESUBMIT".equals(action)) {
			documentAiProcessingService.process(documentId, false);
			return response;
		}
		return response;
	}
}
