package com.example.invoice.service;

import com.example.invoice.dto.classification.ClassificationResponse;
import com.example.invoice.dto.classification.ClassificationUpdateRequest;
import com.example.invoice.entity.Classification;
import com.example.invoice.entity.ClassificationStatus;
import com.example.invoice.entity.AccountingCategory;
import com.example.invoice.entity.Document;
import com.example.invoice.entity.DocumentStatus;
import com.example.invoice.entity.DocumentType;
import com.example.invoice.entity.ReviewStatus;
import com.example.invoice.exception.ResourceNotFoundException;
import com.example.invoice.repository.ClassificationRepository;
import com.example.invoice.repository.DocumentTypeRepository;
import com.example.invoice.repository.AccountingCategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ClassificationService {
    @org.springframework.beans.factory.annotation.Autowired private DocumentAuditService documentAudit;
    private String before(Document d){return documentAudit==null?null:documentAudit.capture(d);}
    private void remember(Document d,String action,String previous){if(documentAudit!=null)documentAudit.append(d,action,previous);}

	private final ClassificationRepository classificationRepository;
	private final AccountingCategoryRepository accountingCategoryRepository;
	private final DocumentService documentService;
	private final DocumentTypeRepository documentTypeRepository;

	@Transactional(readOnly = true)
	public ClassificationResponse findByDocumentId(Long documentId) {
		return toResponse(loadByDocumentId(documentId));
	}

	@Transactional
	public ClassificationResponse upsert(Long documentId, ClassificationUpdateRequest request) {
		Document document = documentService.lockForEdit(documentId);
        String previous=before(document);
		assertEditable(document);
		Classification classification = classificationRepository.findFirstByDocumentIdOrderByCreatedAtDesc(documentId).orElseGet(Classification::new);
		classification.setDocument(document);
		apply(classification, request);
		classificationRepository.saveAndFlush(classification);remember(document,"CLASSIFICATION_UPDATE",previous);return toResponse(classification);
	}

	@Transactional
	public ClassificationResponse approve(Long documentId) {
		Document document=documentService.lockForEdit(documentId);String previous=before(document);
		Classification classification = loadByDocumentId(documentId);
		if (classification.getStatus() == ClassificationStatus.ACCEPTED)
			throw new com.example.invoice.exception.BadRequestException("Phân loại đã được xác nhận");
		classification.setStatus(ClassificationStatus.ACCEPTED);
		remember(document,"CLASSIFICATION_APPROVE",previous);return toResponse(classification);
	}

	@Transactional
	public ClassificationResponse review(Long documentId) {
		Document document=documentService.lockForEdit(documentId);String previous=before(document);
		Classification classification = loadByDocumentId(documentId);
		assertEditable(classification.getDocument());
		classification.setStatus(ClassificationStatus.NEED_REVIEW);
		remember(document,"CLASSIFICATION_REVIEW",previous);return toResponse(classification);
	}

	@Transactional
	public ClassificationResponse correction(Long documentId, ClassificationUpdateRequest request) {
		Document document=documentService.lockForEdit(documentId);String previous=before(document);
		Classification classification = loadByDocumentId(documentId);
		assertEditable(classification.getDocument());
		// Keep predictedLabel, confidence and reason as the original AI evidence.
		// The effective category is the user's correction and is marked separately below.
		if (request.category() != null) applyAccountingCategory(classification, request.category());
		if (request.documentType() != null) applyDocumentType(classification.getDocument(), request.documentType());
		classification.setStatus(ClassificationStatus.CORRECTED);
		classification.setAiGenerated(false);
		remember(document,"CLASSIFICATION_CORRECTION",previous);return toResponse(classification);
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
		if (request.documentType() != null) {
			applyDocumentType(classification.getDocument(), request.documentType());
			classification.setAiGenerated(false);
			classification.setStatus(ClassificationStatus.CORRECTED);
		}
	}

	private void applyDocumentType(Document document, String typeCode) {
		if (typeCode.isBlank()) throw new IllegalArgumentException("Loại chứng từ không được để trống");
		DocumentType type = documentTypeRepository.findByCodeAndActiveTrue(typeCode.trim().toUpperCase(java.util.Locale.ROOT))
				.orElseThrow(() -> new IllegalArgumentException("Loại chứng từ không hoạt động hoặc không tồn tại"));
		document.setType(type);
		document.setDocumentType(type.getCode());
	}

	private void assertEditable(Document document) {
		if (document.getReviewStatus() == ReviewStatus.APPROVED) {
			throw new IllegalStateException("Không thể thay đổi chứng từ đã được duyệt");
		}
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
		Document document = classification.getDocument();
		String documentType = document.getType() == null ? document.getDocumentType() : document.getType().getCode();
		return new ClassificationResponse(classification.getId(), document.getId(), documentType,
				classification.getPredictedLabel(),
				classification.getCategory(), accountingCategory == null ? null : accountingCategory.getId(),
				accountingCategory == null ? classification.getCategory() : accountingCategory.getCategoryCode(),
				accountingCategory == null ? null : accountingCategory.getCategoryName(), classification.getAccountingAccount(),
				classification.getConfidence(), classification.getReason(),
				classification.getStatus(), classification.isAiGenerated(), classification.getCreatedAt(), classification.getUpdatedAt());
	}
}
