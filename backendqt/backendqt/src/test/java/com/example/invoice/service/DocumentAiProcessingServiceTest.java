package com.example.invoice.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.invoice.ai.AiDocumentResultValidator;
import com.example.invoice.ai.AiProcessingService;
import com.example.invoice.config.AiProperties;
import com.example.invoice.dto.ai.AiDocumentRequest;
import com.example.invoice.dto.ai.AiDocumentResult;
import com.example.invoice.dto.document.OCRResultResponse;
import com.example.invoice.entity.AccountingCategory;
import com.example.invoice.entity.Company;
import com.example.invoice.entity.Document;
import com.example.invoice.entity.DocumentStatus;
import com.example.invoice.entity.DocumentType;
import com.example.invoice.entity.ReviewStatus;
import com.example.invoice.exception.BadRequestException;
import com.example.invoice.repository.AccountingCategoryRepository;
import com.example.invoice.repository.DocumentTypeRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DocumentAiProcessingServiceTest {
	@Test
	void restoresStoredOcrLayoutWithRealDimensionsAndReadingOrder() {
		DocumentAiProcessingService service = serviceWithLayoutReconstructor();
		String layout = """
				{"version":2,"pages":[{"page":1,"width":2480,"height":3508,"language":"vie+eng",
				"words":[{"id":"w1","text":"Địa","confidence":94,"x":248,"y":351,"width":124,"height":70}],
				"lines":[{"id":"l1","text":"Địa","confidence":94,"x":248,"y":351,"width":124,"height":70,"wordIds":["w1"]}],
				"blocks":[{"id":"b1","text":"Địa","confidence":94,"x":248,"y":351,"width":124,"height":70,"lineIds":["l1"]}]}]}
				""";

		OcrPageResult restored = service.restoreStoredOcr(ocr("Địa chỉ", layout, new BigDecimal("0.94"))).orElseThrow();

		assertEquals(2480, restored.imageWidth());
		assertEquals(3508, restored.imageHeight());
		assertEquals("w1", restored.words().getFirst().id());
		assertEquals(List.of("w1"), restored.lines().getFirst().wordIds());
		assertEquals(List.of("l1"), restored.blocks().getFirst().lineIds());
		assertEquals(.1d, restored.words().getFirst().x(), .001d);
	}

	@Test
	void missingOrCorruptStoredLayoutFallsBackInsteadOfCreatingFakeBoxes() {
		DocumentAiProcessingService service = serviceWithLayoutReconstructor();

		assertTrue(service.restoreStoredOcr(ocr("Địa chỉ", null, new BigDecimal("0.94"))).isEmpty());
		assertTrue(service.restoreStoredOcr(ocr("Địa chỉ", "{broken", new BigDecimal("0.94"))).isEmpty());
	}

	@Test
	void nullStoredConfidenceRemainsUnknown() {
		DocumentAiProcessingService service = serviceWithLayoutReconstructor();
		String layout = """
				{"version":2,"pages":[{"page":1,"width":100,"height":100,"language":"vie+eng",
				"words":[{"id":"w1","text":"Công","confidence":90,"x":1,"y":2,"width":20,"height":5}]}]}
				""";

		OcrPageResult restored = service.restoreStoredOcr(ocr("Công ty", layout, null)).orElseThrow();

		assertTrue(Float.isNaN(restored.confidence()));
		assertFalse(restored.lines().isEmpty());
		assertFalse(restored.blocks().isEmpty());
	}

	@Test
	void reprocessRejectsApprovedDocumentBeforeStartingAiWork() {
		DocumentService documentService = mock(DocumentService.class);
		Document document = new Document();
		document.setId(7L);
		document.setFileType("image/png");
		document.setReviewStatus(ReviewStatus.APPROVED);
		when(documentService.load(7L)).thenReturn(document);
		when(documentService.claimForAiProcessing(7L, true)).thenThrow(new BadRequestException("approved"));
		DocumentAiProcessingService service = new DocumentAiProcessingService(documentService,
				mock(DocumentTypeRepository.class), mock(AccountingCategoryRepository.class),
				mock(DocumentTextExtractionService.class), mock(OCRResultService.class),
				mock(AiProcessingService.class), mock(AiDocumentResultValidator.class),
				mock(DocumentAiResultPersistenceService.class), mock(ProcessingLogService.class),
				mock(PdfImageConversionService.class));

		assertThrows(BadRequestException.class, () -> service.process(7L, true));

		verify(documentService).claimForAiProcessing(7L, true);
	}

	@Test
	void reprocessAnalyzesEveryPdfPageInsteadOfTreatingPdfAsOneImage() {
		DocumentService documentService = mock(DocumentService.class);
		DocumentTypeRepository documentTypeRepository = mock(DocumentTypeRepository.class);
		AccountingCategoryRepository categoryRepository = mock(AccountingCategoryRepository.class);
		AiProcessingService aiProcessingService = mock(AiProcessingService.class);
		DocumentAiResultPersistenceService persistenceService = mock(DocumentAiResultPersistenceService.class);
		ProcessingLogService processingLogService = mock(ProcessingLogService.class);
		PdfImageConversionService pdfConversionService = mock(PdfImageConversionService.class);
		AdaptiveOcrService adaptiveOcrService = mock(AdaptiveOcrService.class);
		OCRResultService ocrResultService = mock(OCRResultService.class);
		byte[] pdf = {1, 2, 3};
		Company company = new Company();
		company.setId(9L);
		Document document = new Document();
		document.setId(8L);
		document.setCompany(company);
		document.setFileType("application/pdf");
		document.setFileSize((long) pdf.length);
		document.setOriginalFileName("report.pdf");
		document.setStatus(DocumentStatus.PROCESSED);
		document.setReviewStatus(ReviewStatus.PENDING);
		when(documentService.claimForAiProcessing(8L, true)).thenReturn(document);
		when(documentService.load(8L)).thenReturn(document);
		when(documentService.loadFileBytes(8L)).thenReturn(pdf);
		when(documentTypeRepository.findByActiveTrue()).thenReturn(List.of(documentType("VAT_INVOICE"),
				documentType("RECEIPT"), documentType("PAYMENT_VOUCHER"), documentType("RECEIPT_VOUCHER"),
				documentType("CONTRACT"), documentType("OTHER")));
		AccountingCategory category = new AccountingCategory();
		category.setCategoryCode("CAT");
		category.setCategoryName("Category");
		when(categoryRepository.findByCompanyIdAndActiveTrueOrderByCategoryCodeAsc(9L))
				.thenReturn(List.of(category));
		when(pdfConversionService.convert(same(pdf))).thenReturn(List.of(
				new PdfPageImage(1, new byte[] {4}, "image/png"),
				new PdfPageImage(2, new byte[] {5}, "image/png")));
		when(adaptiveOcrService.recognize(any(), any(), org.mockito.ArgumentMatchers.anyInt()))
				.thenAnswer(invocation -> {
					int pageNumber = invocation.getArgument(2);
					OcrPageResult page = new OcrPageResult(pageNumber, 100, 100, "page " + pageNumber,
							10f, List.of(), 1L, null, "vie");
					return new AdaptiveOcrService.AdaptiveOcrResult(page, new byte[] {(byte) pageNumber},
							"image/png", OcrProcessingProfile.NORMAL, 0.5, 1, null, null);
				});
		when(aiProcessingService.analyzeDocument(any(AiDocumentRequest.class))).thenAnswer(invocation -> {
			AiDocumentRequest request = invocation.getArgument(0);
			String documentType = request.fileName().endsWith("#page-2") ? "CONTRACT" : "OTHER";
			return new AiDocumentResult("ollama", "qwen3-vl", documentType, new BigDecimal("0.95"), null,
					null, List.of(), List.of("DOCUMENT_TYPE_REASON: Có nội dung nhận diện trang"),
					"raw", 1L);
		});
		AiProperties properties = new AiProperties();
		when(persistenceService.persist(org.mockito.ArgumentMatchers.eq(8L), any(), any()))
				.thenAnswer(invocation -> {
					var validated = (AiDocumentResultValidator.ValidatedAiDocumentResult) invocation.getArgument(1);
					return validated.requiresReview(properties.getDocument().getReviewThreshold());
				});
		DocumentAiProcessingService service = new DocumentAiProcessingService(documentService,
				documentTypeRepository, categoryRepository, aiProcessingService, new AiDocumentResultValidator(),
				persistenceService, processingLogService, pdfConversionService, adaptiveOcrService,
				properties, null, null, ocrResultService);

		var response = service.process(8L, true);

		ArgumentCaptor<AiDocumentRequest> requests = ArgumentCaptor.forClass(AiDocumentRequest.class);
		verify(aiProcessingService, times(2)).analyzeDocument(requests.capture());
		assertEquals(List.of("report.pdf#page-1", "report.pdf#page-2"),
				requests.getAllValues().stream().map(AiDocumentRequest::fileName).toList());
		assertEquals(true, response.requiresReview());
		assertEquals(DocumentStatus.NEED_REVIEW, response.status());
		assertEquals(true, response.warnings().stream()
				.anyMatch(warning -> warning.startsWith("DOCUMENT_TYPE_PAGE_CONFLICT:")));
		verify(pdfConversionService).convert(same(pdf));
		verify(ocrResultService, never()).findByDocumentId(8L);
	}

	private DocumentType documentType(String code) {
		DocumentType type = new DocumentType();
		type.setCode(code);
		type.setActive(true);
		return type;
	}

	private OCRResultResponse ocr(String rawText, String layout, BigDecimal confidence) {
		return new OCRResultResponse(1L, 7L, rawText, layout, "vie+eng", "TESS4J_WORD_LAYOUT",
				"tesseract", confidence, LocalDateTime.now());
	}

	private DocumentAiProcessingService serviceWithLayoutReconstructor() {
		return new DocumentAiProcessingService(mock(DocumentService.class), mock(DocumentTypeRepository.class),
				mock(AccountingCategoryRepository.class), mock(AiProcessingService.class),
				new AiDocumentResultValidator(), mock(DocumentAiResultPersistenceService.class),
				mock(ProcessingLogService.class), mock(PdfImageConversionService.class),
				mock(AdaptiveOcrService.class), new AiProperties(), new OcrTextNormalizer(new AiProperties()),
				new OcrLayoutReconstructor(), mock(OCRResultService.class));
	}
}
