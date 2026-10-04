package com.example.invoice.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.example.invoice.config.AiProperties;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class DocumentTextExtractionServiceTest {
	@Test
	void imageUsesPreprocessingThenTesseractAndKeepsPageBoundary() {
		AiProperties properties = new AiProperties();
		ImagePreprocessingService preprocessing = mock(ImagePreprocessingService.class);
		PdfImageConversionService pdf = mock(PdfImageConversionService.class);
		TesseractOcrService tesseract = mock(TesseractOcrService.class);
		byte[] original = {1}; byte[] normalized = {2};
		when(preprocessing.preprocess(original, "image/png")).thenReturn(new ImagePreprocessingResult(
				normalized, "image/png", true, 0, 10, 10, 10, 10, 1, null));
		when(tesseract.recognize(normalized, 1)).thenReturn(new TesseractOcrService.OcrPage(
				1, "Hoa don gia tri gia tang so 001234 nguoi ban Cong ty ABC tong tien 1000000", new BigDecimal("0.91"), 12));
		DocumentTextExtractionResult result = new DocumentTextExtractionService(properties, preprocessing, pdf, tesseract)
				.extract(original, "image/png");
		assertTrue(result.text().startsWith("[PAGE 1]"));
		assertEquals("tesseract", result.engine());
		assertEquals(new BigDecimal("0.9100"), result.confidence());
		assertFalse(result.visionFallbackRecommended());
		verify(preprocessing).preprocess(original, "image/png");
		verify(tesseract).recognize(normalized, 1);
	}
}
