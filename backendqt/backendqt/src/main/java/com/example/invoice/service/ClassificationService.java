package com.example.invoice.service;

import com.example.invoice.dto.classification.ClassificationResponse;
import com.example.invoice.dto.classification.ClassificationUpdateRequest;
import com.example.invoice.entity.Classification;
import com.example.invoice.entity.ClassificationStatus;
import com.example.invoice.entity.Document;
import com.example.invoice.exception.ResourceNotFoundException;
import com.example.invoice.repository.ClassificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ClassificationService {
	private final ClassificationRepository classificationRepository;
	private final DocumentService documentService;

	public ClassificationResponse findByDocumentId(Long documentId) {
		return toResponse(loadByDocumentId(documentId));
	}

	@Transactional
	public ClassificationResponse upsert(Long documentId, ClassificationUpdateRequest request) {
		Document document = documentService.load(documentId);
		Classification classification = classificationRepository.findFirstByDocumentIdOrderByCreatedAtDesc(documentId).orElseGet(Classification::new);
		classification.setDocument(document);
		apply(classification, request);
		return toResponse(classificationRepository.save(classification));
	}

	@Transactional
	public ClassificationResponse approve(Long documentId) {
		Classification classification = loadByDocumentId(documentId);
		classification.setStatus(ClassificationStatus.ACCEPTED);
		return toResponse(classification);
	}

	@Transactional
	public ClassificationResponse review(Long documentId) {
		Classification classification = loadByDocumentId(documentId);
		classification.setStatus(ClassificationStatus.NEED_REVIEW);
		return toResponse(classification);
	}

	@Transactional
	public ClassificationResponse correction(Long documentId, ClassificationUpdateRequest request) {
		Classification classification = loadByDocumentId(documentId);
		apply(classification, request);
		classification.setStatus(ClassificationStatus.CORRECTED);
		classification.setAiGenerated(false);
		return toResponse(classification);
	}

	private Classification loadByDocumentId(Long documentId) {
		documentService.load(documentId);
		return classificationRepository.findFirstByDocumentIdOrderByCreatedAtDesc(documentId)
				.orElseThrow(() -> new ResourceNotFoundException("Classification not found"));
	}

	private void apply(Classification classification, ClassificationUpdateRequest request) {
		if (request.category() != null) classification.setCategory(request.category());
		if (request.category() != null) classification.setPredictedLabel(request.category());
		if (request.confidence() != null) classification.setConfidence(request.confidence());
		if (request.reason() != null) classification.setReason(request.reason());
		if (request.status() != null) classification.setStatus(request.status());
		if (request.aiGenerated() != null) classification.setAiGenerated(request.aiGenerated());
	}

	private ClassificationResponse toResponse(Classification classification) {
		return new ClassificationResponse(classification.getId(), classification.getDocument().getId(),
				classification.getCategory(), classification.getConfidence(), classification.getReason(),
				classification.getStatus(), classification.isAiGenerated(), classification.getCreatedAt(), classification.getUpdatedAt());
	}
}
