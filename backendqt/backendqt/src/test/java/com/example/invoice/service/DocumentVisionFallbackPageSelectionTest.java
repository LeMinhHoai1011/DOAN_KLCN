package com.example.invoice.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.example.invoice.ai.AiDocumentResultValidator;
import com.example.invoice.ai.AiProcessingService;
import com.example.invoice.dto.ai.AiDocumentRequest;
import com.example.invoice.dto.ai.AiDocumentResult;
import com.example.invoice.entity.Document;
import com.example.invoice.repository.AccountingCategoryRepository;
import com.example.invoice.repository.DocumentTypeRepository;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DocumentVisionFallbackPageSelectionTest {
	@Test
	void goodPageOneLowPageTwoSendsPageTwo() {
		Fixture fixture = fixture(2);
		DocumentTextExtractionResult extraction = extraction(page(1, false), page(2, true));
		fixture.service.analyzeVisionFallback(fixture.document, new byte[] {9}, "prompt", extraction, List.of("INVOICE"));
		ArgumentCaptor<AiDocumentRequest> request = ArgumentCaptor.forClass(AiDocumentRequest.class);
		verify(fixture.ai).analyzeDocument(request.capture());
		assertArrayEquals(new byte[] {2}, request.getValue().imageBytes());
		assertEquals("invoice.pdf#page-2", request.getValue().fileName());
	}

	@Test
	void goodFirstTwoLowPageThreeSendsPageThree() {
		Fixture fixture = fixture(3);
		DocumentTextExtractionResult extraction = extraction(page(1, false), page(2, false), page(3, true));
		fixture.service.analyzeVisionFallback(fixture.document, new byte[] {9}, "prompt", extraction, List.of("INVOICE"));
		ArgumentCaptor<AiDocumentRequest> request = ArgumentCaptor.forClass(AiDocumentRequest.class);
		verify(fixture.ai).analyzeDocument(request.capture());
		assertArrayEquals(new byte[] {3}, request.getValue().imageBytes());
		assertEquals("invoice.pdf#page-3", request.getValue().fileName());
	}

	@Test
	void multipleLowPagesAreProcessedIndividuallyThenAggregatedAsText() {
		Fixture fixture = fixture(3);
		DocumentTextExtractionResult extraction = extraction(page(1, false), page(2, true), page(3, true));
		fixture.service.analyzeVisionFallback(fixture.document, new byte[] {9}, "prompt", extraction, List.of("INVOICE"));
		ArgumentCaptor<AiDocumentRequest> requests = ArgumentCaptor.forClass(AiDocumentRequest.class);
		verify(fixture.ai, times(2)).analyzeDocument(requests.capture());
		assertEquals(List.of("invoice.pdf#page-2", "invoice.pdf#page-3"),
				requests.getAllValues().stream().map(AiDocumentRequest::fileName).toList());
		verify(fixture.ai).analyzeTextDocument(any());
	}

	private Fixture fixture(int pageCount) {
		AiProcessingService ai = mock(AiProcessingService.class);
		PdfImageConversionService pdf = mock(PdfImageConversionService.class);
		List<PdfPageImage> images = java.util.stream.IntStream.rangeClosed(1, pageCount)
				.mapToObj(page -> new PdfPageImage(page, new byte[] {(byte) page}, "image/png")).toList();
		when(pdf.convert(any())).thenReturn(images);
		AiDocumentResult result = new AiDocumentResult("ollama", "vision", "INVOICE", BigDecimal.ONE,
				"page", null, List.of(), List.of(), "{}", 1);
		when(ai.analyzeDocument(any())).thenReturn(result);
		when(ai.analyzeTextDocument(any())).thenReturn(result);
		Document document = new Document(); document.setOriginalFileName("invoice.pdf"); document.setFileType("application/pdf");
		DocumentAiProcessingService service = new DocumentAiProcessingService(mock(DocumentService.class),
				mock(DocumentTypeRepository.class), mock(AccountingCategoryRepository.class), mock(DocumentTextExtractionService.class),
				mock(OCRResultService.class), ai, mock(AiDocumentResultValidator.class), mock(DocumentAiResultPersistenceService.class),
				mock(ProcessingLogService.class), pdf);
		return new Fixture(service, ai, document);
	}

	private DocumentTextExtractionResult extraction(DocumentTextExtractionResult.PageText... pages) {
		return new DocumentTextExtractionResult("all pages", List.of(pages), "tesseract", "test", "vie+eng",
				"SCANNED_PDF_OCR", new BigDecimal("0.5"), 1, true, List.of("low"));
	}

	private DocumentTextExtractionResult.PageText page(int page, boolean fallback) {
		return new DocumentTextExtractionResult.PageText(page, "TESSERACT_OCR", "page " + page,
				fallback ? new BigDecimal("0.2") : new BigDecimal("0.9"),
				fallback ? new BigDecimal("0.2") : new BigDecimal("0.9"), fallback);
	}

	private record Fixture(DocumentAiProcessingService service, AiProcessingService ai, Document document) {}
}
