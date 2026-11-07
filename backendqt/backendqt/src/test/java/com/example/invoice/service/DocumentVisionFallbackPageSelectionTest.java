package com.example.invoice.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.example.invoice.ai.AiDocumentResultValidator;
import com.example.invoice.ai.AiProcessingService;
import com.example.invoice.dto.ai.AiDocumentRequest;
import com.example.invoice.dto.ai.AiDocumentResult;
import com.example.invoice.entity.Document;
import com.example.invoice.exception.AiProviderException;
import com.example.invoice.repository.AccountingCategoryRepository;
import com.example.invoice.repository.DocumentTypeRepository;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DocumentVisionFallbackPageSelectionTest {
	@Test
	void invalidTextOutputUsesOneVisionFallbackWithoutRepeatingDeterministicTextFailure() {
		Fixture fixture = fixture(1);
		AiDocumentResult result = result();
		when(fixture.ai.analyzeTextDocument(any()))
				.thenThrow(new AiProviderException("OLLAMA_INVALID_JSON: invalid"));
        when(fixture.ai.analyzeDocument(any())).thenReturn(result);
		AiDocumentResult actual = fixture.service.analyzeSingleImage("invoice.png", "image/png", new byte[] {1},
				List.of("INVOICE"), List.of(), null, reliableOcr());
		assertEquals(result, actual);
		verify(fixture.ai, times(1)).analyzeTextDocument(any());
		verify(fixture.ai, times(1)).analyzeDocument(any());
	}

	@Test
	void invalidTextOutputHasAtMostOneTextAndOneVisionRequest() {
		Fixture fixture = fixture(1);
		AiDocumentResult vision = result();
		when(fixture.ai.analyzeTextDocument(any())).thenThrow(new AiProviderException("OLLAMA_INVALID_JSON: invalid"));
		when(fixture.ai.analyzeDocument(any())).thenReturn(vision);
		assertEquals(vision, fixture.service.analyzeSingleImage("invoice.png", "image/png", new byte[] {1},
				List.of("INVOICE"), List.of(), null, reliableOcr()));
		verify(fixture.ai, times(1)).analyzeTextDocument(any());
		verify(fixture.ai, times(1)).analyzeDocument(any());
	}

	@org.junit.jupiter.params.ParameterizedTest
	@org.junit.jupiter.params.provider.CsvSource({"0318334251, 0101234567, SELLER, OUTGOING", "0101234567, 0318334251, BUYER, INCOMING"})
	void directionFollowsTheCompanyRoleInsteadOfTheModelGuess(String sellerTaxCode, String buyerTaxCode,
			AiDocumentResult.CompanyRole role, AiDocumentResult.DocumentDirection direction) {
		var company = new com.example.invoice.entity.Company();
		company.setTaxCode("0318334251");
		var invoice = new AiDocumentResult.AiInvoiceExtraction("1", "A", "2026-01-01", "Bán", sellerTaxCode, "HN", "Mua",
				buyerTaxCode, "HN", BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ONE, List.of());
		var modelGuess = direction == AiDocumentResult.DocumentDirection.OUTGOING
				? AiDocumentResult.DocumentDirection.INCOMING : AiDocumentResult.DocumentDirection.OUTGOING;
		AiDocumentResult result = new AiDocumentResult("ollama", "vision", "VAT_INVOICE", BigDecimal.ONE, null, null, null, invoice,
				List.of(), List.of(), "{}", 1L, modelGuess, null, List.of(), null);
		AiDocumentResult resolved = fixture(1).service.resolveCompanyRole(result, company);
		assertEquals(role, resolved.companyRole().role());
		assertEquals(direction, resolved.documentDirection());
	}

	@Test
	void digitalPdfTextGetsOneStrictRetryAfterInvalidOutput() {
		Fixture fixture = fixture(1);
		AiDocumentResult retried = result();
		when(fixture.ai.analyzeTextDocument(any()))
				.thenThrow(new AiProviderException("OLLAMA_INVALID_JSON: invalid")).thenReturn(retried);
		assertEquals(retried, fixture.service.analyzeDigitalPdfText("invoice.pdf#page-1", "text", List.of("INVOICE"), List.of(), null));
		verify(fixture.ai, times(2)).analyzeTextDocument(any());
		verify(fixture.ai).analyzeTextDocument(org.mockito.ArgumentMatchers.argThat(request -> request.prompt().contains("RETRY REQUIREMENT")));
	}

	@Test
	void digitalPdfTextStopsAfterTheStrictRetryAndOnInfrastructureFailures() {
		Fixture fixture = fixture(1);
		when(fixture.ai.analyzeTextDocument(any())).thenThrow(new AiProviderException("OLLAMA_INVALID_JSON: invalid"));
		assertThrows(AiProviderException.class, () -> fixture.service.analyzeDigitalPdfText("invoice.pdf#page-1", "text", List.of("INVOICE"), List.of(), null));
		verify(fixture.ai, times(2)).analyzeTextDocument(any());

		Fixture offline = fixture(1);
		when(offline.ai.analyzeTextDocument(any())).thenThrow(new AiProviderException("AI_CONNECTION_ERROR: refused"));
		assertThrows(AiProviderException.class, () -> offline.service.analyzeDigitalPdfText("invoice.pdf#page-1", "text", List.of("INVOICE"), List.of(), null));
		verify(offline.ai, times(1)).analyzeTextDocument(any());
	}

	@Test
	void connectionFailureIsNotRetriedAndDoesNotFallBackToVision() {
		Fixture fixture = fixture(1);
		when(fixture.ai.analyzeTextDocument(any())).thenThrow(new AiProviderException("AI_CONNECTION_ERROR: refused"));
		assertThrows(AiProviderException.class, () -> fixture.service.analyzeSingleImage("invoice.png", "image/png",
				new byte[] {1}, List.of("INVOICE"), List.of(), null, reliableOcr()));
		verify(fixture.ai, times(1)).analyzeTextDocument(any());
		verify(fixture.ai, never()).analyzeDocument(any());
	}

	@Test
	void highConfidenceOcrWithoutLayoutUsesVision() {
		Fixture fixture = fixture(1);
		OcrPageResult missingLayout = new OcrPageResult(1, 100, 100, "invoice 41NVNO036", 95f,
				List.of(), 1L, null, "vie+eng");

		fixture.service.analyzeSingleImage("invoice.png", "image/png", new byte[] {1},
				List.of("INVOICE"), List.of(), null, missingLayout);

		verify(fixture.ai, never()).analyzeTextDocument(any());
		verify(fixture.ai).analyzeDocument(any());
	}

	@Test
	void highConfidenceInvoiceOcrWithoutAnyCriticalFieldUsesVision() {
		Fixture fixture = fixture(1);
		String text = "random readable words without document labels or financial fields";
		OcrPageResult suspicious = new OcrPageResult(1,100,100,text,96,
				List.of(new OcrWord("w1","random",96,.1,.1,.2,.05)),1,null,"eng",
				List.of(new OcrLine("l1",1,text,96,.1,.1,.8,.05,List.of("w1"))),
				List.of(new OcrBlock("b1",1,text,96,.1,.1,.8,.05,List.of("l1"))));
		fixture.service.analyzeSingleImage("invoice.png","image/png",new byte[]{1},
				List.of("INVOICE"),List.of(),null,suspicious);
		verify(fixture.ai,never()).analyzeTextDocument(any());
		verify(fixture.ai).analyzeDocument(any());
	}

	@Test
	void suspiciousVietnameseOcrWithoutDiacriticsUsesVision() {
		Fixture fixture = fixture(1);
		OcrPageResult suspicious = new OcrPageResult(1, 100, 100, "CONG TY ABC DIA CHI HA NOI", 95f,
				List.of(new OcrWord("w1", "CONG", 95f, .1, .1, .2, .05)), 1L, null, "vie+eng",
				List.of(new OcrLine("l1", 1, "CONG TY ABC", 95f, .1, .1, .2, .05, List.of("w1"))),
				List.of(new OcrBlock("b1", 1, "CONG TY ABC", 95f, .1, .1, .2, .05, List.of("l1"))));

		fixture.service.analyzeSingleImage("invoice.png", "image/png", new byte[] {1},
				List.of("INVOICE"), List.of(), null, suspicious);

		verify(fixture.ai, never()).analyzeTextDocument(any());
		verify(fixture.ai).analyzeDocument(any());
	}

	@Test
	void mixedVietnameseOcrWithRepeatedLostDiacriticsUsesVision() {
		Fixture fixture = fixture(1);
		String text = "HÓA ĐƠN GIÁ TRỊ GIA TĂNG Don vi ban hang CONG TY ABC NOI THAT "
				+ "Dia chi Thành phố Hồ Chí Minh Mười lam triệu đồng";
		OcrPageResult suspicious = new OcrPageResult(1, 100, 100, text, 92f,
				List.of(new OcrWord("w1", "HÓA", 92f, .1, .1, .2, .05)), 1L, null, "vie+eng",
				List.of(new OcrLine("l1", 1, text, 92f, .1, .1, .8, .05, List.of("w1"))),
				List.of(new OcrBlock("b1", 1, text, 92f, .1, .1, .8, .05, List.of("l1"))));

		fixture.service.analyzeSingleImage("invoice.png", "image/png", new byte[] {1},
				List.of("INVOICE"), List.of(), null, suspicious);

		verify(fixture.ai, never()).analyzeTextDocument(any());
		verify(fixture.ai).analyzeDocument(any());
	}

	@Test
	void oneAsciiBusinessPhraseDoesNotForceVisionWhenVietnameseIsOtherwiseIntact() {
		Fixture fixture = fixture(1);
		String text = "HÓA ĐƠN GIÁ TRỊ GIA TĂNG CONG TY ABC Địa chỉ Thành phố Hồ Chí Minh";
		OcrPageResult acceptable = new OcrPageResult(1, 100, 100, text, 92f,
				List.of(new OcrWord("w1", "HÓA", 92f, .1, .1, .2, .05)), 1L, null, "vie+eng",
				List.of(new OcrLine("l1", 1, text, 92f, .1, .1, .8, .05, List.of("w1"))),
				List.of(new OcrBlock("b1", 1, text, 92f, .1, .1, .8, .05, List.of("l1"))));

		fixture.service.analyzeSingleImage("invoice.png", "image/png", new byte[] {1},
				List.of("INVOICE"), List.of(), null, acceptable);

		verify(fixture.ai).analyzeTextDocument(any());
		verify(fixture.ai, never()).analyzeDocument(any());
	}
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

	private AiDocumentResult result() {
		return new AiDocumentResult("ollama", "vision", "INVOICE", BigDecimal.ONE,
				null, null, List.of(), List.of(), "{}", 1);
	}

	private OcrPageResult reliableOcr() {
		return new OcrPageResult(1, 100, 100, "invoice 41NVNO036", 95f,
				List.of(new OcrWord("w1", "invoice", 95f, .1, .1, .2, .05)), 1L, null, "vie+eng",
				List.of(new OcrLine("l1", 1, "invoice", 95f, .1, .1, .2, .05, List.of("w1"))),
				List.of(new OcrBlock("b1", 1, "invoice", 95f, .1, .1, .2, .05, List.of("l1"))));
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
