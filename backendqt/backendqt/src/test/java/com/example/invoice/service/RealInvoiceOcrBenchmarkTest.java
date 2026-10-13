package com.example.invoice.service;

import static org.junit.jupiter.api.Assertions.assertFalse;

import com.example.invoice.config.AiProperties;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "OCR_REAL_PDF_PATH", matches = ".+")
class RealInvoiceOcrBenchmarkTest {
	@Test
	void comparesProfilesOnConfirmedVietnameseInvoice() throws Exception {
		byte[] pdf = Files.readAllBytes(Path.of(System.getenv("OCR_REAL_PDF_PATH")));
		for (int dpi : new int[] { 180, 300 }) {
			AiProperties properties = new AiProperties();
			properties.getOcr().setDataPath(System.getenv().getOrDefault("TESSDATA_PATH", "./tessdata"));
			properties.getOcr().setLanguage("vie+eng");
			properties.getPdf().setDpi(dpi);
			PdfPageImage page = new PdfImageConversionService(properties).convert(pdf).getFirst();
			ImageQualityResult quality = new ImageQualityAnalyzer().analyze(page.bytes());
			OpenCvImageProcessor processor = new OpenCvImageProcessor(properties);
			Tess4jOcrService ocr = new Tess4jOcrService(properties);
			AdaptiveOcrService evaluator = new AdaptiveOcrService(properties, new ImageQualityAnalyzer(), processor, ocr);
			for (OcrProcessingProfile profile : OcrProcessingProfile.values()) {
				OpenCvImageProcessor.ProcessedImage processed = processor.process(page.bytes(), profile, quality);
				OcrPageResult result = ocr.recognize(processed.bytes(), 1);
				String text = result.text();
				int fields = confirmedFieldHits(text);
				System.out.printf(java.util.Locale.ROOT,
						"REAL_INVOICE_OCR dpi=%d profile=%s score=%.6f confidence=%.3f fields=%d durationMs=%d utf8Base64=%s%n",
						dpi, profile, evaluator.evaluate(result), result.confidence(), fields, result.durationMs(),
						Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8)));
				assertFalse(text.contains("\uFFFD"));
			}
		}
	}

	private int confirmedFieldHits(String text) {
		int hits = 0;
		for (String expected : new String[] {
				"HÓA ĐƠN GIÁ TRỊ GIA TĂNG", "CÔNG TY TNHH SX TM TRANG TRÍ NỘI THẤT ĐẠI PHÚC LONG",
				"0319174875", "CÔNG TY TNHH TRANG TRÍ NỘI THẤT NAL", "0318334251",
				"14.800.000", "1.184.000", "15.984.000",
				"Mười lăm triệu chín trăm tám mươi bốn nghìn đồng" }) {
			if (text.contains(expected)) hits++;
		}
		return hits;
	}
}
