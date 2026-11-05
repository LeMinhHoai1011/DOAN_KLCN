package com.example.invoice.controller;

import com.example.invoice.verification.FieldVerificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Per-field verification results; document scope is enforced by the service through DocumentService.load. */
@RestController
@RequestMapping("/api/v1/documents/{documentId}/field-verifications")
@RequiredArgsConstructor
public class FieldVerificationController {
	private final FieldVerificationService service;

	@GetMapping
	@PreAuthorize("hasAuthority('PERMISSION_DOCUMENT_VIEW') or hasRole('ADMIN')")
	public FieldVerificationService.Report get(@PathVariable Long documentId) {
		return service.report(documentId);
	}

	/** Re-checks the current values, e.g. after an accountant corrected a field. Never changes a value. */
	@PostMapping("/recompute")
	@PreAuthorize("hasAuthority('PERMISSION_DOCUMENT_UPDATE') or hasAuthority('PERMISSION_DOCUMENT_CREATE') or hasRole('ADMIN')")
	public FieldVerificationService.Report recompute(@PathVariable Long documentId) {
		return service.recompute(documentId);
	}
}
