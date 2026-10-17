package com.example.invoice.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.invoice.ai.AiDocumentResultValidator;
import com.example.invoice.config.AiProperties;
import com.example.invoice.dto.ai.AiDocumentResult;
import com.example.invoice.entity.Classification;
import com.example.invoice.entity.Document;
import com.example.invoice.entity.DocumentType;
import com.example.invoice.entity.Invoice;
import com.example.invoice.entity.InvoiceItem;
import com.example.invoice.entity.OCRResult;
import com.example.invoice.entity.ExtractedField;
import com.example.invoice.entity.DocumentStatus;
import com.example.invoice.entity.ReviewStatus;
import com.example.invoice.repository.ClassificationRepository;
import com.example.invoice.repository.DocumentTypeRepository;
import com.example.invoice.repository.ExtractedFieldRepository;
import com.example.invoice.repository.InvoiceRepository;
import com.example.invoice.repository.OCRResultRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
class DocumentAiResultPersistenceServiceTest {
	@Mock private DocumentService documentService;
	@Mock private DocumentTypeRepository documentTypeRepository;
	@Mock private OCRResultRepository ocrResultRepository;
	@Mock private ClassificationRepository classificationRepository;
	@Mock private InvoiceRepository invoiceRepository;
	@Mock private ExtractedFieldRepository extractedFieldRepository;
	@Mock private ProcessingLogService processingLogService;

	@Test
	void successfulOcrUsesAnIndependentTransactionAndUpsertsTheExistingRow() throws Exception {
		Transactional transaction = DocumentAiResultPersistenceService.class
				.getMethod("persistOcr", Long.class, OcrDocumentResult.class)
				.getAnnotation(Transactional.class);
		assertEquals(Propagation.REQUIRES_NEW, transaction.propagation());

		Document document = new Document(); document.setId(9L);
		OCRResult existing = new OCRResult(); existing.setId(21L);
		when(documentService.load(9L)).thenReturn(document);
		when(ocrResultRepository.findFirstByDocumentIdOrderByProcessedAtDesc(9L)).thenReturn(Optional.of(existing));
		when(ocrResultRepository.save(any(OCRResult.class))).thenAnswer(invocation -> invocation.getArgument(0));
		DocumentAiResultPersistenceService service = new DocumentAiResultPersistenceService(documentService,
				documentTypeRepository, ocrResultRepository, classificationRepository, invoiceRepository,
				extractedFieldRepository, processingLogService, new AiProperties());
		service.persistOcr(9L, new OcrDocumentResult("recognized", 92f,
				List.of(new OcrPageResult(1, 2480, 3508, "recognized", 92f,
						List.of(new OcrWord("1", "recognized", 92f, .1, .2, .3, .04)), 15L, null, "vie+eng",
						List.of(new OcrLine("p1-l1", 1, "recognized", 92f, .1, .2, .3, .04, List.of("1"))),
						List.of(new OcrBlock("p1-b1", 1, "recognized", 92f, .1, .2, .3, .04, List.of("p1-l1"))))),
				15L, List.of()));

		verify(ocrResultRepository).save(same(existing));
		assertEquals(0, new BigDecimal("0.92").compareTo(existing.getConfidence()));
		assertTrue(existing.getLayoutJson().contains("\"width\":2480"));
		assertTrue(existing.getLayoutJson().contains("\"x\":248"));
		assertTrue(existing.getLayoutJson().contains("\"version\":2"));
		assertTrue(existing.getLayoutJson().contains("\"lines\""));
		assertTrue(existing.getLayoutJson().contains("\"blocks\""));
	}

	@Test
	void reprocessUpdatesTheExistingInvoiceInsteadOfCreatingAnotherOne() {
		Document document = new Document();
		document.setId(7L);
		DocumentType type = new DocumentType();
		type.setCode("INVOICE");
		Invoice existing = new Invoice();
		existing.setId(11L);
		existing.setAiGenerated(true);
		AiDocumentResult result = new AiDocumentResult("ollama", "vision", "INVOICE", new BigDecimal("0.95"),
				null, new AiDocumentResult.AiInvoiceExtraction("0001", null, "2026-09-27", null, null, null,
					null, null, null, new BigDecimal("100"), new BigDecimal("10"), new BigDecimal("110"), List.of()),
				List.of(), List.of(), "raw", 12L);
		var validated = new AiDocumentResultValidator().validate(result, Set.of("INVOICE"));

		when(documentService.load(7L)).thenReturn(document);
		when(documentTypeRepository.findByCode("INVOICE")).thenReturn(Optional.of(type));
		when(classificationRepository.findFirstByDocumentIdOrderByCreatedAtDesc(7L)).thenReturn(Optional.empty());
		when(invoiceRepository.findByDocumentId(7L)).thenReturn(Optional.of(existing));
		when(invoiceRepository.save(any(Invoice.class))).thenAnswer(invocation -> invocation.getArgument(0));
		DocumentAiResultPersistenceService service = new DocumentAiResultPersistenceService(documentService,
				documentTypeRepository, ocrResultRepository, classificationRepository, invoiceRepository,
				extractedFieldRepository, processingLogService, new AiProperties());
		service.persist(7L, validated);

		verify(invoiceRepository).save(same(existing));
	}

	@Test
	void reprocessWithEmptyItemsPreservesItemsButFlagsStaleAiData() {
		assertReprocessPreservesItems(List.of());
	}

	@Test
	void reprocessWithNullItemsPreservesItemsButFlagsStaleAiData() {
		assertReprocessPreservesItems(null);
	}

	@Test
	void reprocessWithOnlyInvalidItemsPreservesItemsButFlagsStaleAiData() {
		assertReprocessPreservesItems(List.of(new AiDocumentResult.AiInvoiceItemExtraction(
				"  ", BigDecimal.ONE, "cái", BigDecimal.TEN, new BigDecimal("0.08"),
				new BigDecimal("0.8"), BigDecimal.TEN)));
	}

	private void assertReprocessPreservesItems(List<AiDocumentResult.AiInvoiceItemExtraction> replacementItems) {
		Document document = new Document(); document.setId(701L);
		DocumentType type = new DocumentType(); type.setCode("VAT_INVOICE");
		Invoice existing = new Invoice(); existing.setId(702L); existing.setAiGenerated(true);
		InvoiceItem existingItem = new InvoiceItem(); existingItem.setId(703L); existingItem.setInvoice(existing);
		existingItem.setProductName("Dòng hàng đã xác nhận"); existingItem.setQuantity(BigDecimal.ONE);
		existingItem.setUnitPrice(new BigDecimal("100000")); existingItem.setAmount(new BigDecimal("100000"));
		existing.getItems().add(existingItem);
		AiDocumentResult.AiInvoiceExtraction extraction = new AiDocumentResult.AiInvoiceExtraction(
				"INV-701", "1C26TEST", "2026-10-13", "Seller", "0312345678", null, null,
				"Buyer", "0398765432", null, new BigDecimal("100000"), new BigDecimal("8000"),
				new BigDecimal("108000"), null, null, null, null, replacementItems);
		when(documentService.load(701L)).thenReturn(document);
		when(documentTypeRepository.findByCode("VAT_INVOICE")).thenReturn(Optional.of(type));
		when(classificationRepository.findFirstByDocumentIdOrderByCreatedAtDesc(701L)).thenReturn(Optional.empty());
		when(invoiceRepository.findByDocumentId(701L)).thenReturn(Optional.of(existing));
		when(invoiceRepository.save(any(Invoice.class))).thenAnswer(invocation -> invocation.getArgument(0));

		service().persist(701L, new AiDocumentResultValidator().validate(invoiceResult(extraction), Set.of("VAT_INVOICE")));

		assertEquals(1, existing.getItems().size());
		assertEquals(703L, existing.getItems().getFirst().getId());
		assertEquals("Dòng hàng đã xác nhận", existing.getItems().getFirst().getProductName());
		assertEquals(DocumentStatus.NEED_REVIEW, document.getStatus());
		var assessment = ArgumentCaptor.forClass(com.example.invoice.dto.document.DocumentAssessment.class);
		verify(processingLogService).recordAssessment(org.mockito.ArgumentMatchers.eq(701L), assessment.capture());
		assertTrue(assessment.getValue().warnings().stream().anyMatch(w -> "STALE_AI_ITEMS_PRESERVED".equals(w.code())));
	}

	@Test
	void reprocessDoesNotEraseExistingNamesAndAddressesWhenAiReturnsNullOrBlank() {
		Document document = new Document(); document.setId(71L);
		DocumentType type = new DocumentType(); type.setCode("VAT_INVOICE");
		Invoice existing = new Invoice(); existing.setId(72L); existing.setAiGenerated(true);
		existing.setSellerName("Công ty Ánh Dương");
		existing.setSellerAddress("123 Đường Nguyễn Huệ");
		existing.setBuyerName("Công ty Mua");
		existing.setBuyerAddress("45 Phố Huế");
		AiDocumentResult.AiInvoiceExtraction extraction = new AiDocumentResult.AiInvoiceExtraction(
				"INV-71", null, null, null, null, "  ", null, "", null, null,
				null, null, null, null, null, null, null, List.of());
		AiDocumentResult result = invoiceResult(extraction);
		when(documentService.load(71L)).thenReturn(document);
		when(documentTypeRepository.findByCode("VAT_INVOICE")).thenReturn(Optional.of(type));
		when(classificationRepository.findFirstByDocumentIdOrderByCreatedAtDesc(71L)).thenReturn(Optional.empty());
		when(invoiceRepository.findByDocumentId(71L)).thenReturn(Optional.of(existing));
		when(invoiceRepository.save(any(Invoice.class))).thenAnswer(invocation -> invocation.getArgument(0));

		service().persist(71L, new AiDocumentResultValidator().validate(result, Set.of("VAT_INVOICE")));

		assertEquals("Công ty Ánh Dương", existing.getSellerName());
		assertEquals("123 Đường Nguyễn Huệ", existing.getSellerAddress());
		assertEquals("Công ty Mua", existing.getBuyerName());
		assertEquals("45 Phố Huế", existing.getBuyerAddress());
	}

	@Test
	void reprocessPreservesAccentedValueWhenAiReturnsOnlyItsAsciiFold() {
		Document document = new Document(); document.setId(73L);
		DocumentType type = new DocumentType(); type.setCode("VAT_INVOICE");
		Invoice existing = new Invoice(); existing.setId(74L); existing.setAiGenerated(true);
		existing.setSellerName("CÔNG TY ÁNH DƯƠNG");
		existing.setBuyerAddress("45 Phố Huế");
		AiDocumentResult.AiInvoiceExtraction extraction = new AiDocumentResult.AiInvoiceExtraction(
				"INV-73", null, null, "CONG TY ANH DUONG", null, null, null, null, null, "45 Pho Hue",
				null, null, null, null, null, null, null, List.of());
		when(documentService.load(73L)).thenReturn(document);
		when(documentTypeRepository.findByCode("VAT_INVOICE")).thenReturn(Optional.of(type));
		when(classificationRepository.findFirstByDocumentIdOrderByCreatedAtDesc(73L)).thenReturn(Optional.empty());
		when(invoiceRepository.findByDocumentId(73L)).thenReturn(Optional.of(existing));
		when(invoiceRepository.save(any(Invoice.class))).thenAnswer(invocation -> invocation.getArgument(0));

		service().persist(73L, new AiDocumentResultValidator().validate(invoiceResult(extraction), Set.of("VAT_INVOICE")));

		assertEquals("CÔNG TY ÁNH DƯƠNG", existing.getSellerName());
		assertEquals("45 Phố Huế", existing.getBuyerAddress());
	}

	@Test
	void reprocessFromVatInvoiceToReceiptReplacesCurrentTypeAndRemovesAiInvoice() {
		Document document = new Document(); document.setId(8L);
		DocumentType previousType = new DocumentType(); previousType.setCode("VAT_INVOICE");
		document.setType(previousType); document.setDocumentType("VAT_INVOICE");
		DocumentType type = new DocumentType(); type.setCode("RECEIPT");
		Invoice stale = new Invoice(); stale.setId(12L); stale.setAiGenerated(true);
		AiDocumentResult result = new AiDocumentResult("ollama", "text", "RECEIPT", new BigDecimal("0.95"),
				"receipt text", null, List.of(), List.of(), "raw", 10L);
		var validated = new AiDocumentResultValidator().validate(result, Set.of("RECEIPT"));
		when(documentService.load(8L)).thenReturn(document);
		when(documentTypeRepository.findByCode("RECEIPT")).thenReturn(Optional.of(type));
		when(invoiceRepository.findByDocumentId(8L)).thenReturn(Optional.of(stale));
		when(extractedFieldRepository.findByDocumentId(8L)).thenReturn(List.of());
		when(classificationRepository.findFirstByDocumentIdOrderByCreatedAtDesc(8L)).thenReturn(Optional.empty());
		DocumentAiResultPersistenceService service = new DocumentAiResultPersistenceService(documentService,
				documentTypeRepository, ocrResultRepository, classificationRepository, invoiceRepository,
				extractedFieldRepository, processingLogService, new AiProperties());
		service.persist(8L, validated);
		assertEquals(type, document.getType());
		assertEquals("RECEIPT", document.getDocumentType());
		ArgumentCaptor<com.example.invoice.entity.Classification> classification =
				ArgumentCaptor.forClass(com.example.invoice.entity.Classification.class);
		verify(classificationRepository).save(classification.capture());
		assertEquals("RECEIPT", classification.getValue().getPredictedLabel());
		verify(invoiceRepository).deleteAiGeneratedByDocumentId(8L);
		verify(invoiceRepository, never()).save(stale);
	}

	@Test
	void reprocessDoesNotOverwriteManuallyCorrectedDocumentType() {
		Document document = new Document();
		document.setId(22L);
		DocumentType confirmedType = new DocumentType();
		confirmedType.setCode("CONTRACT");
		document.setType(confirmedType);
		document.setDocumentType("CONTRACT");
		Classification manualCorrection = new Classification();
		manualCorrection.setId(31L);
		manualCorrection.setDocument(document);
		manualCorrection.setStatus(com.example.invoice.entity.ClassificationStatus.CORRECTED);
		manualCorrection.setAiGenerated(false);
		DocumentType aiProposal = new DocumentType();
		aiProposal.setCode("OTHER");
		AiDocumentResult result = new AiDocumentResult("ollama", "qwen3-vl", "OTHER", new BigDecimal("0.95"),
				null, null, List.of(), List.of("DOCUMENT_TYPE_REASON: Chứng từ không khớp loại hợp đồng"),
				"raw", 10L);
		when(documentService.load(22L)).thenReturn(document);
		when(documentTypeRepository.findByCode("OTHER")).thenReturn(Optional.of(aiProposal));
		when(classificationRepository.findFirstByDocumentIdOrderByCreatedAtDesc(22L))
				.thenReturn(Optional.of(manualCorrection));
		when(invoiceRepository.findByDocumentId(22L)).thenReturn(Optional.empty());

		service().persist(22L, new AiDocumentResultValidator().validate(result, Set.of("OTHER")));

		assertEquals(confirmedType, document.getType());
		assertEquals("CONTRACT", document.getDocumentType());
		verify(classificationRepository, never()).save(any(Classification.class));
	}

	@Test
	void persistenceDoesNotChangeApprovedDocument() {
		Document document = new Document();
		document.setId(23L);
		document.setDocumentType("CONTRACT");
		document.setReviewStatus(ReviewStatus.APPROVED);
		document.setStatus(DocumentStatus.COMPLETED);
		when(documentService.load(23L)).thenReturn(document);
		AiDocumentResult result = new AiDocumentResult("ollama", "qwen3-vl", "OTHER", new BigDecimal("0.95"),
				null, null, List.of(), List.of("DOCUMENT_TYPE_REASON: Chứng từ khác"), "raw", 10L);

		boolean requiresReview = service().persist(23L,
				new AiDocumentResultValidator().validate(result, Set.of("OTHER")));

		assertFalse(requiresReview);
		assertEquals("CONTRACT", document.getDocumentType());
		assertEquals(ReviewStatus.APPROVED, document.getReviewStatus());
		assertEquals(DocumentStatus.COMPLETED, document.getStatus());
		verify(documentTypeRepository, never()).findByCode("OTHER");
		verify(classificationRepository, never()).findFirstByDocumentIdOrderByCreatedAtDesc(23L);
	}

	@Test
	void retryUpdatesExistingAiClassificationInsteadOfInsertingAnother() {
		Document document = new Document();
		document.setId(24L);
		Classification existing = new Classification();
		existing.setId(41L);
		existing.setDocument(document);
		existing.setAiGenerated(true);
		existing.setStatus(com.example.invoice.entity.ClassificationStatus.NEED_REVIEW);
		DocumentType type = new DocumentType();
		type.setCode("RECEIPT");
		AiDocumentResult result = new AiDocumentResult("ollama", "qwen3-vl", "RECEIPT",
				new BigDecimal("0.95"), null, null, List.of(),
				List.of("DOCUMENT_TYPE_REASON: Có nội dung xác nhận đã thu tiền"), "raw", 10L);
		when(documentService.load(24L)).thenReturn(document);
		when(documentTypeRepository.findByCode("RECEIPT")).thenReturn(Optional.of(type));
		when(classificationRepository.findFirstByDocumentIdOrderByCreatedAtDesc(24L))
				.thenReturn(Optional.of(existing), Optional.of(existing));
		when(classificationRepository.save(any(Classification.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));
		when(invoiceRepository.findByDocumentId(24L)).thenReturn(Optional.empty());

		DocumentAiResultPersistenceService service = service();
		var validated = new AiDocumentResultValidator().validate(result, Set.of("RECEIPT"));
		service.persist(24L, validated);
		service.persist(24L, validated);

		verify(classificationRepository, org.mockito.Mockito.times(2)).save(same(existing));
	}

	@Test
	void nonInvoicePersistsDocumentOwnedFieldWithoutInvoice() {
		Document document = new Document(); document.setId(13L);
		DocumentType type = new DocumentType(); type.setCode("RECEIPT");
		AiDocumentResult result = new AiDocumentResult("ollama", "text", "RECEIPT", new BigDecimal("0.95"),
				"receipt", null, List.of(new AiDocumentResult.AiExtractedField("receiptNumber", "R-41", new BigDecimal("0.90"))),
				List.of(), "raw", 10L);
		var validated = new AiDocumentResultValidator().validate(result, Set.of("RECEIPT"));
		when(documentService.load(13L)).thenReturn(document);
		when(documentTypeRepository.findByCode("RECEIPT")).thenReturn(Optional.of(type));
		when(classificationRepository.findFirstByDocumentIdOrderByCreatedAtDesc(13L)).thenReturn(Optional.empty());
		when(invoiceRepository.findByDocumentId(13L)).thenReturn(Optional.empty());
		DocumentAiResultPersistenceService service = new DocumentAiResultPersistenceService(documentService,
				documentTypeRepository, ocrResultRepository, classificationRepository, invoiceRepository,
				extractedFieldRepository, processingLogService, new AiProperties());
		service.persist(13L, validated);

		ArgumentCaptor<ExtractedField> field = ArgumentCaptor.forClass(ExtractedField.class);
		verify(extractedFieldRepository).save(field.capture());
		assertEquals(document, field.getValue().getDocument());
		assertNull(field.getValue().getInvoice());
		verify(invoiceRepository, never()).save(any());
	}

	@Test
	void confidence084WithOnlyMildTextWarningIsProcessed() {
		Document document = new Document(); document.setId(19L);
		DocumentType type = new DocumentType(); type.setCode("OTHER");
		AiDocumentResult result = new AiDocumentResult("ollama", "text", "OTHER", new BigDecimal("0.84"),
				"receipt", null, List.of(), List.of("DOCUMENT_TYPE_REASON: Có nội dung xác nhận đã thu tiền",
						"NON_CRITICAL: địa chỉ có thể thiếu dấu tiếng Việt"), "raw", 10L);
		when(documentService.load(19L)).thenReturn(document);
		when(documentTypeRepository.findByCode("OTHER")).thenReturn(Optional.of(type));
		when(classificationRepository.findFirstByDocumentIdOrderByCreatedAtDesc(19L)).thenReturn(Optional.empty());
		when(invoiceRepository.findByDocumentId(19L)).thenReturn(Optional.empty());

		service().persist(19L, new AiDocumentResultValidator().validate(result, Set.of("OTHER")));

		assertEquals(DocumentStatus.PROCESSED, document.getStatus());
		ArgumentCaptor<Classification> classification = ArgumentCaptor.forClass(Classification.class);
		verify(classificationRepository).save(classification.capture());
		assertTrue(classification.getValue().getReason()
				.contains("DOCUMENT_TYPE_REASON: Có nội dung xác nhận đã thu tiền"));
	}

	@Test
	void aiClassificationConfidenceIsNotPersistedAsOcrConfidence() {
		Document document = new Document();
		document.setId(20L);
		DocumentType type = new DocumentType();
		type.setCode("RECEIPT");
		AiDocumentResult result = new AiDocumentResult("ollama", "qwen3-vl", "RECEIPT",
				new BigDecimal("0.95"), null, null, "AI-derived text", null, List.of(), List.of(
						"DOCUMENT_TYPE_REASON: Có nội dung xác nhận đã thu tiền"), "raw", 10L);
		when(documentService.load(20L)).thenReturn(document);
		when(documentTypeRepository.findByCode("RECEIPT")).thenReturn(Optional.of(type));
		when(classificationRepository.findFirstByDocumentIdOrderByCreatedAtDesc(20L)).thenReturn(Optional.empty());
		when(invoiceRepository.findByDocumentId(20L)).thenReturn(Optional.empty());

		service().persist(20L, new AiDocumentResultValidator().validate(result, Set.of("RECEIPT")));

		verify(ocrResultRepository, never()).save(any(OCRResult.class));
	}

	@Test
	void invoiceWithoutStructuredExtractionPersistsFieldsAndNeedsReview() {
		Document document = new Document(); document.setId(14L);
		DocumentType type = new DocumentType(); type.setCode("INVOICE");
		AiDocumentResult result = new AiDocumentResult("ollama", "text", "INVOICE", BigDecimal.ONE,
				"invoice", null, List.of(new AiDocumentResult.AiExtractedField("invoiceNumber", "41NVNO036", BigDecimal.ONE)),
				List.of(), "raw", 10L);
		var validated = new AiDocumentResultValidator().validate(result, Set.of("INVOICE"));
		when(documentService.load(14L)).thenReturn(document);
		when(documentTypeRepository.findByCode("INVOICE")).thenReturn(Optional.of(type));
		when(classificationRepository.findFirstByDocumentIdOrderByCreatedAtDesc(14L)).thenReturn(Optional.empty());
		DocumentAiResultPersistenceService service = new DocumentAiResultPersistenceService(documentService,
				documentTypeRepository, ocrResultRepository, classificationRepository, invoiceRepository,
				extractedFieldRepository, processingLogService, new AiProperties());
		assertTrue(service.persist(14L, validated));
		assertEquals(DocumentStatus.NEED_REVIEW, document.getStatus());
		verify(invoiceRepository, never()).save(any());
		verify(extractedFieldRepository).save(any(ExtractedField.class));
	}

	@Test
	void fullInvoiceSkipsPersistedCoreFieldsButKeepsCustomFieldsAndDeduplicatesAiNames() {
		Document document = new Document(); document.setId(15L);
		DocumentType type = new DocumentType(); type.setCode("INVOICE");
		AiDocumentResult result = new AiDocumentResult("ollama", "text", "INVOICE", BigDecimal.ONE, null, null, "invoice",
				new AiDocumentResult.AiInvoiceExtraction("INV-15", null, null, null, null, null, null, null, null,
						null, null, null, List.of()),
				List.of(new AiDocumentResult.AiExtractedField("invoiceNumber", "INV-15", BigDecimal.ONE),
						new AiDocumentResult.AiExtractedField("deliveryCode", "D-1", new BigDecimal("0.8")),
						new AiDocumentResult.AiExtractedField("deliveryCode", "D-2", new BigDecimal("0.7"))),
				List.of(), "raw", 10L, AiDocumentResult.DocumentDirection.UNKNOWN,
				new AiDocumentResult.AiTransactionAssessment(AiDocumentResult.TransactionAssessmentType.UNKNOWN, null, null),
				List.of(new AiDocumentResult.AiExtraField("invoice_number", null, "INV-15", BigDecimal.ONE),
						new AiDocumentResult.AiExtraField("warehouse code", null, "WH-1", new BigDecimal("0.9"))),
				new AiDocumentResult.AiCompanyRole(AiDocumentResult.CompanyRole.UNKNOWN, null, null));
		when(documentService.load(15L)).thenReturn(document);
		when(documentTypeRepository.findByCode("INVOICE")).thenReturn(Optional.of(type));
		when(classificationRepository.findFirstByDocumentIdOrderByCreatedAtDesc(15L)).thenReturn(Optional.empty());
		when(invoiceRepository.findByDocumentId(15L)).thenReturn(Optional.empty());
		when(invoiceRepository.save(any(Invoice.class))).thenAnswer(invocation -> invocation.getArgument(0));
		DocumentAiResultPersistenceService service = service();

		service.persist(15L, new AiDocumentResultValidator().validate(result, Set.of("INVOICE")));

		ArgumentCaptor<ExtractedField> fields = ArgumentCaptor.forClass(ExtractedField.class);
		verify(extractedFieldRepository, org.mockito.Mockito.times(2)).save(fields.capture());
		assertEquals(Set.of("deliveryCode", "warehouseCode"), fields.getAllValues().stream()
				.map(ExtractedField::getFieldName).collect(java.util.stream.Collectors.toSet()));
		assertTrue(fields.getAllValues().stream().allMatch(f -> f.getInvoice() != null));
	}

	@Test
	void coreFieldIsRetainedWhenCorrespondingInvoiceValueWasNotPersisted() {
		Document document = new Document(); document.setId(16L);
		DocumentType type = new DocumentType(); type.setCode("INVOICE");
		AiDocumentResult result = new AiDocumentResult("ollama", "text", "INVOICE", BigDecimal.ONE, "invoice",
				new AiDocumentResult.AiInvoiceExtraction(null, null, null, null, null, null, null, null, null,
						null, null, null, List.of()),
				List.of(new AiDocumentResult.AiExtractedField("invoiceNumber", "OCR-ONLY", BigDecimal.ONE)), List.of(), "raw", 10L);
		when(documentService.load(16L)).thenReturn(document);
		when(documentTypeRepository.findByCode("INVOICE")).thenReturn(Optional.of(type));
		when(classificationRepository.findFirstByDocumentIdOrderByCreatedAtDesc(16L)).thenReturn(Optional.empty());
		when(invoiceRepository.findByDocumentId(16L)).thenReturn(Optional.empty());
		when(invoiceRepository.save(any(Invoice.class))).thenAnswer(invocation -> invocation.getArgument(0));
		DocumentAiResultPersistenceService service = service();

		service.persist(16L, new AiDocumentResultValidator().validate(result, Set.of("INVOICE")));

		ArgumentCaptor<ExtractedField> field = ArgumentCaptor.forClass(ExtractedField.class);
		verify(extractedFieldRepository).save(field.capture());
		assertEquals("invoiceNumber", field.getValue().getFieldName());
		assertEquals("OCR-ONLY", field.getValue().getFieldValue());
	}

	@Test
	void invoiceToNonInvoiceDetachesAndPreservesManualField() {
		Document document = new Document(); document.setId(17L);
		DocumentType type = new DocumentType(); type.setCode("OTHER");
		Invoice stale = new Invoice(); stale.setId(17L); stale.setAiGenerated(true);
		ExtractedField manual = new ExtractedField(); manual.setDocument(document); manual.setInvoice(stale);
		manual.setFieldName("approvedBy"); manual.setSource("MANUAL"); stale.getExtractedFields().add(manual);
		when(documentService.load(17L)).thenReturn(document);
		when(documentTypeRepository.findByCode("OTHER")).thenReturn(Optional.of(type));
		when(classificationRepository.findFirstByDocumentIdOrderByCreatedAtDesc(17L)).thenReturn(Optional.empty());
		when(invoiceRepository.findByDocumentId(17L)).thenReturn(Optional.of(stale));
		when(extractedFieldRepository.findByDocumentId(17L)).thenReturn(List.of(manual));
		DocumentAiResultPersistenceService service = service();
		AiDocumentResult result = new AiDocumentResult("ollama", "text", "OTHER", BigDecimal.ONE,
				"other", null, List.of(), List.of(), "raw", 10L);

		service.persist(17L, new AiDocumentResultValidator().validate(result, Set.of("OTHER")));

		assertNull(manual.getInvoice());
		assertTrue(stale.getExtractedFields().contains(manual));
		verify(extractedFieldRepository).saveAndFlush(same(manual));
		verify(invoiceRepository).saveAndFlush(stale);
		verify(invoiceRepository).deleteAiGeneratedByDocumentId(17L);
	}

	@Test
	void vatInvoicePersistsNewCoreFieldsAndCompleteItemsWithoutExtraFieldDuplicates() {
		Document document = new Document(); document.setId(18L);
		DocumentType type = new DocumentType(); type.setCode("VAT_INVOICE");
		AiDocumentResult.AiInvoiceItemExtraction item = new AiDocumentResult.AiInvoiceItemExtraction(
				"Dịch vụ kế toán", new BigDecimal("2"), "tháng", new BigDecimal("5000000"),
				new BigDecimal("0.08"), new BigDecimal("800000"), new BigDecimal("10000000"));
		AiDocumentResult.AiInvoiceExtraction extraction = new AiDocumentResult.AiInvoiceExtraction(
				"000018", "1C26TAA", "2026-10-04", "Công ty Bán", "0312345678", "Đường Nguyễn Văn Linh, Phường Tân Phong",
				"028 1234 5678", "Công ty Mua", "0398765432", "Hà Nội", new BigDecimal("10000000"),
				new BigDecimal("800000"), new BigDecimal("10800000"), "TM/CK", "Mười triệu tám trăm nghìn đồng",
				"CQT-2026-XYZ", "2026-10-04", List.of(item));
		List<AiDocumentResult.AiExtractedField> duplicates = List.of(
				new AiDocumentResult.AiExtractedField("sellerPhone", "028 1234 5678", BigDecimal.ONE),
				new AiDocumentResult.AiExtractedField("paymentMethod", "TM/CK", BigDecimal.ONE),
				new AiDocumentResult.AiExtractedField("amountInWords", "Mười triệu tám trăm nghìn đồng", BigDecimal.ONE),
				new AiDocumentResult.AiExtractedField("taxAuthorityCode", "CQT-2026-XYZ", BigDecimal.ONE),
				new AiDocumentResult.AiExtractedField("signDate", "2026-10-04", BigDecimal.ONE));
		AiDocumentResult result = new AiDocumentResult("ollama", "qwen3-vl", "VAT_INVOICE", BigDecimal.ONE,
				null, null, null, extraction, duplicates, List.of(), "raw", 5L,
				AiDocumentResult.DocumentDirection.INCOMING,
				new AiDocumentResult.AiTransactionAssessment(AiDocumentResult.TransactionAssessmentType.EXPENSE, BigDecimal.ONE, "Hóa đơn đầu vào"),
				List.of(), new AiDocumentResult.AiCompanyRole(AiDocumentResult.CompanyRole.BUYER, BigDecimal.ONE, "Trùng mã số thuế"));
		when(documentService.load(18L)).thenReturn(document);
		when(documentTypeRepository.findByCode("VAT_INVOICE")).thenReturn(Optional.of(type));
		when(classificationRepository.findFirstByDocumentIdOrderByCreatedAtDesc(18L)).thenReturn(Optional.empty());
		when(invoiceRepository.findByDocumentId(18L)).thenReturn(Optional.empty());
		when(invoiceRepository.save(any(Invoice.class))).thenAnswer(invocation -> invocation.getArgument(0));

		service().persist(18L, new AiDocumentResultValidator().validate(result, Set.of("VAT_INVOICE")));

		ArgumentCaptor<Invoice> invoice = ArgumentCaptor.forClass(Invoice.class);
		verify(invoiceRepository).save(invoice.capture());
		assertEquals("028 1234 5678", invoice.getValue().getSellerPhone());
		assertEquals("Đường Nguyễn Văn Linh, Phường Tân Phong", invoice.getValue().getSellerAddress());
		assertEquals("TM/CK", invoice.getValue().getPaymentMethod());
		assertEquals("Mười triệu tám trăm nghìn đồng", invoice.getValue().getAmountInWords());
		assertEquals("CQT-2026-XYZ", invoice.getValue().getTaxAuthorityCode());
		assertEquals(java.time.LocalDate.of(2026, 10, 4), invoice.getValue().getSignDate());
		assertEquals("tháng", invoice.getValue().getItems().getFirst().getUnit());
		assertEquals(new BigDecimal("0.08"), invoice.getValue().getItems().getFirst().getTaxRate());
		assertEquals(new BigDecimal("800000"), invoice.getValue().getItems().getFirst().getTaxAmount());
		verify(extractedFieldRepository, never()).save(any(ExtractedField.class));
	}

	private DocumentAiResultPersistenceService service() {
		return new DocumentAiResultPersistenceService(documentService, documentTypeRepository, ocrResultRepository,
				classificationRepository, invoiceRepository, extractedFieldRepository, processingLogService, new AiProperties());
	}

	private AiDocumentResult invoiceResult(AiDocumentResult.AiInvoiceExtraction extraction) {
		return new AiDocumentResult("ollama", "qwen3-vl", "VAT_INVOICE", BigDecimal.ONE,
				null, null, null, extraction, List.of(),
				List.of("DOCUMENT_TYPE_REASON: Hóa đơn có thông tin người bán và người mua"), "raw", 1L,
				AiDocumentResult.DocumentDirection.UNKNOWN,
				new AiDocumentResult.AiTransactionAssessment(AiDocumentResult.TransactionAssessmentType.UNKNOWN, null, null),
				List.of(), new AiDocumentResult.AiCompanyRole(AiDocumentResult.CompanyRole.UNKNOWN, null, null));
	}
}
