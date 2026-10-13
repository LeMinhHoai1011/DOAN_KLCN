package com.example.invoice.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.example.invoice.config.AiProperties;
import com.example.invoice.dto.ai.AiDocumentRequest;
import com.example.invoice.service.PdfImageConversionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "OCR_REAL_PDF_PATH", matches = ".+")
@EnabledIfEnvironmentVariable(named = "OLLAMA_RUNTIME_MODEL", matches = ".+")
class RealInvoiceVisionBenchmarkTest {
	@Test
	void visionReadsConfirmedFieldsFromRealVietnameseInvoice() throws Exception {
		AiProperties properties = new AiProperties();
		properties.getOllama().setBaseUrl("http://localhost:11434");
		properties.getOllama().setModel(System.getenv("OLLAMA_RUNTIME_MODEL"));
		properties.getOllama().setThink(false);
		properties.getPdf().setDpi(180);
		properties.setRequestTimeout(Duration.ofMinutes(2));
		byte[] pdf = Files.readAllBytes(Path.of(System.getenv("OCR_REAL_PDF_PATH")));
		byte[] image = new PdfImageConversionService(properties).convert(pdf).getFirst().bytes();
		OllamaAiProvider provider = new OllamaAiProvider(properties, new ObjectMapper(), new OkHttpClient());
		String prompt = AiDocumentPromptFactory.create(List.of("VAT_INVOICE"), List.of(), null, null);

		var result = provider.analyze(new AiDocumentRequest(
				"real-invoice-263-page-1.png", "image/png", image, List.of("VAT_INVOICE"), prompt));
		assertNotNull(result.invoice());
		var invoice = result.invoice();
		System.out.printf("REAL_INVOICE_VISION durationMs=%d sellerUtf8Base64=%s buyerUtf8Base64=%s amountWordsUtf8Base64=%s%n",
				result.durationMs(), encoded(invoice.sellerName()), encoded(invoice.buyerName()), encoded(invoice.amountInWords()));
		assertEquals("0319174875", invoice.sellerTaxCode());
		assertEquals("0318334251", invoice.buyerTaxCode());
		assertEquals(new BigDecimal("15984000"), invoice.totalAmount());
	}

	private String encoded(String value) {
		return value == null ? "null" : Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
	}
}
