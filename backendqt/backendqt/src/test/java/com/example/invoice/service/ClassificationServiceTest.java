package com.example.invoice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.invoice.dto.classification.ClassificationUpdateRequest;
import com.example.invoice.entity.Classification;
import com.example.invoice.entity.ClassificationStatus;
import com.example.invoice.entity.Document;
import com.example.invoice.entity.DocumentType;
import com.example.invoice.entity.ReviewStatus;
import com.example.invoice.repository.AccountingCategoryRepository;
import com.example.invoice.repository.ClassificationRepository;
import com.example.invoice.repository.DocumentTypeRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ClassificationServiceTest {
	@Mock private ClassificationRepository classificationRepository;
	@Mock private AccountingCategoryRepository accountingCategoryRepository;
	@Mock private DocumentService documentService;
	@Mock private DocumentTypeRepository documentTypeRepository;

	private ClassificationService service;

	@BeforeEach
	void setUp() {
		service = new ClassificationService(classificationRepository, accountingCategoryRepository,
				documentService, documentTypeRepository);
	}

	@Test
	void accountantCorrectionChangesActiveDocumentTypeAndKeepsAiProposal() {
		Document document = new Document();
		document.setId(42L);
		document.setDocumentType("VAT_INVOICE");
		Classification classification = classification(document);
		DocumentType receipt = new DocumentType();
		receipt.setCode("RECEIPT");
		receipt.setActive(true);
		when(documentService.load(42L)).thenReturn(document);
		when(classificationRepository.findFirstByDocumentIdOrderByCreatedAtDesc(42L))
				.thenReturn(Optional.of(classification));
		when(documentTypeRepository.findByCodeAndActiveTrue("RECEIPT")).thenReturn(Optional.of(receipt));
		var response = service.correction(42L,
				new ClassificationUpdateRequest(null, "RECEIPT", null, null, null, null));

		assertEquals("RECEIPT", document.getDocumentType());
		assertEquals(receipt, document.getType());
		assertEquals("VAT_INVOICE", classification.getPredictedLabel());
		assertEquals("RECEIPT", response.documentType());
		assertEquals("VAT_INVOICE", response.predictedDocumentType());
		assertEquals(ClassificationStatus.CORRECTED, classification.getStatus());
		assertEquals(false, classification.isAiGenerated());
	}

	@Test
	void correctionRejectsInactiveOrUnknownDocumentType() {
		Document document = new Document();
		document.setId(43L);
		Classification classification = classification(document);
		when(documentService.load(43L)).thenReturn(document);
		when(classificationRepository.findFirstByDocumentIdOrderByCreatedAtDesc(43L))
				.thenReturn(Optional.of(classification));
		when(documentTypeRepository.findByCodeAndActiveTrue("OLD_CODE")).thenReturn(Optional.empty());

		assertThrows(IllegalArgumentException.class, () -> service.correction(43L,
				new ClassificationUpdateRequest(null, "OLD_CODE", null, null, null, null)));
	}

	@Test
	void approvedDocumentCannotBeModified() {
		Document document = new Document();
		document.setId(44L);
		document.setReviewStatus(ReviewStatus.APPROVED);
		when(documentService.load(44L)).thenReturn(document);

		assertThrows(IllegalStateException.class, () -> service.upsert(44L,
				new ClassificationUpdateRequest(null, "RECEIPT", null, null, null, null)));
		verify(classificationRepository, org.mockito.Mockito.never())
				.findFirstByDocumentIdOrderByCreatedAtDesc(44L);
	}

	private Classification classification(Document document) {
		Classification classification = new Classification();
		classification.setDocument(document);
		classification.setPredictedLabel("VAT_INVOICE");
		classification.setReason("DOCUMENT_TYPE_REASON: Có mã số thuế và số hóa đơn");
		classification.setStatus(ClassificationStatus.NEED_REVIEW);
		classification.setAiGenerated(true);
		return classification;
	}
}
