package com.example.invoice.service;

import com.example.invoice.dto.classification.ClassificationResponse;
import com.example.invoice.dto.classification.ClassificationUpdateRequest;
import com.example.invoice.entity.Classification;
import com.example.invoice.entity.ClassificationStatus;
import com.example.invoice.entity.AccountingCategory;
import com.example.invoice.entity.Document;
import com.example.invoice.entity.DocumentStatus;
import com.example.invoice.entity.ReviewStatus;
import com.example.invoice.exception.ResourceNotFoundException;
import com.example.invoice.repository.ClassificationRepository;
import com.example.invoice.repository.AccountingCategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ClassificationService {
	private final ClassificationRepository classificationRepository;
	private final AccountingCategoryRepository accountingCategoryRepository;
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
		classification.getDocument().setStatus(DocumentStatus.COMPLETED);
		classification.getDocument().setReviewStatus(ReviewStatus.APPROVED);
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
		// Keep predictedLabel, confidence and reason as the original AI evidence.
		// The effective category is the user's correction and is marked separately below.
		if (request.category() != null) applyAccountingCategory(classification, request.category());
		classification.setStatus(ClassificationStatus.CORRECTED);
		classification.setAiGenerated(false);
		return toResponse(classification);
	}

	private Classification loadByDocumentId(Long documentId) {
		documentService.load(documentId);
		return classificationRepository.findFirstByDocumentIdOrderByCreatedAtDesc(documentId)
				.orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy kết quả phân loại"));
	}

	private void apply(Classification classification, ClassificationUpdateRequest request) {
		if (request.category() != null) applyAccountingCategory(classification, request.category());
		if (request.confidence() != null) classification.setConfidence(request.confidence());
		if (request.reason() != null) classification.setReason(request.reason());
		if (request.status() != null) classification.setStatus(request.status());
		if (request.aiGenerated() != null) classification.setAiGenerated(request.aiGenerated());
	}

	private void applyAccountingCategory(Classification classification, String categoryCode) {
		Document document = classification.getDocument();
		if (categoryCode.isBlank()) {
			classification.setCategory(null);
			classification.setAccountingCategory(null);
			return;
		}
		if (document.getCompany() == null) throw new IllegalArgumentException("Chứng từ chưa được gán công ty");
		AccountingCategory category = accountingCategoryRepository
				.findByCompanyIdAndCategoryCodeAndActiveTrue(document.getCompany().getId(), categoryCode.trim())
				.orElseThrow(() -> new IllegalArgumentException("Nhóm nghiệp vụ không hoạt động hoặc thuộc công ty khác"));
		classification.setCategory(category.getCategoryCode());
		classification.setAccountingCategory(category);
	}

	private ClassificationResponse toResponse(Classification classification) {
		AccountingCategory accountingCategory = classification.getAccountingCategory();
		return new ClassificationResponse(classification.getId(), classification.getDocument().getId(),
				classification.getCategory(), accountingCategory == null ? null : accountingCategory.getId(),
				accountingCategory == null ? classification.getCategory() : accountingCategory.getCategoryCode(),
				accountingCategory == null ? null : accountingCategory.getCategoryName(), classification.getAccountingAccount(),
				classification.getConfidence(), classification.getReason(),
				classification.getStatus(), classification.isAiGenerated(), classification.getCreatedAt(), classification.getUpdatedAt());
	}
}
