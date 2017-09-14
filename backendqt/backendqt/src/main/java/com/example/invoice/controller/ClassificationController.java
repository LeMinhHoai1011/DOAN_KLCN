package com.example.invoice.controller;

import com.example.invoice.dto.classification.ClassificationResponse;
import com.example.invoice.dto.classification.ClassificationUpdateRequest;
import com.example.invoice.service.ClassificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/documents/{documentId}/classification")
@RequiredArgsConstructor
public class ClassificationController {
	private final ClassificationService classificationService;

	@GetMapping
	public ClassificationResponse findByDocumentId(@PathVariable Long documentId) {
		return classificationService.findByDocumentId(documentId);
	}

	@PutMapping
	public ClassificationResponse upsert(@PathVariable Long documentId, @Valid @RequestBody ClassificationUpdateRequest request) {
		return classificationService.upsert(documentId, request);
	}

	@PostMapping("/approve")
	public ClassificationResponse approve(@PathVariable Long documentId) {
		return classificationService.approve(documentId);
	}

	@PostMapping("/review")
	public ClassificationResponse review(@PathVariable Long documentId) {
		return classificationService.review(documentId);
	}

	@PutMapping("/correction")
	public ClassificationResponse correction(@PathVariable Long documentId, @Valid @RequestBody ClassificationUpdateRequest request) {
		return classificationService.correction(documentId, request);
	}
}
