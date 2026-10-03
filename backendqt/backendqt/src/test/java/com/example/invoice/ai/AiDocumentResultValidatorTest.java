package com.example.invoice.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.invoice.dto.ai.AiDocumentResult;
import com.example.invoice.exception.AiProviderException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AiDocumentResultValidatorTest {
	private final AiDocumentResultValidator validator = new AiDocumentResultValidator();

	@Test
	void flagsMoneyMismatchAndLowConfidenceForReview() {
		AiDocumentResult.AiInvoiceExtraction invoice = new AiDocumentResult.AiInvoiceExtraction(
				"0001", null, "2026-09-27", null, "0123456789", null, null, null, null,
				new BigDecimal("100"), new BigDecimal("10"), new BigDecimal("120"), List.of());
		AiDocumentResult result = new AiDocumentResult("ollama", "vision", "INVOICE", new BigDecimal("0.70"),
				"text", invoice, List.of(), List.of(), "raw", 1L);

		var validated = validator.validate(result, Set.of("INVOICE"));
		assertEquals(0, validated.confidence().compareTo(new BigDecimal("0.70")));
		assertEquals(true, validated.requiresReview(new BigDecimal("0.75")));
	}

	@Test
	void rejectsUnknownDocumentType() {
		AiDocumentResult result = new AiDocumentResult("ollama", "vision", "UNKNOWN", BigDecimal.ONE,
				null, null, List.of(), List.of(), "raw", 1L);
		assertThrows(AiProviderException.class, () -> validator.validate(result, Set.of("INVOICE")));
	}

	@Test
	void rejectsConfidenceOutsideNormalizedRange() {
		AiDocumentResult result = new AiDocumentResult("ollama", "vision", "INVOICE", new BigDecimal("1.01"),
				null, null, List.of(), List.of(), "raw", 1L);
		assertThrows(AiProviderException.class, () -> validator.validate(result, Set.of("INVOICE")));
	}

	@Test
	void invoiceWithoutStructuredInvoiceIsAReviewWarningNotAValidationFailure() {
		AiDocumentResult result = new AiDocumentResult("ollama", "text", "INVOICE", BigDecimal.ONE,
				"invoice text", null, List.of(), List.of(), "raw", 1L);
		var validated = validator.validate(result, Set.of("INVOICE"));
		assertTrue(validated.requiresReview(new BigDecimal("0.75")));
		assertTrue(validated.warnings().contains(
				"AI classified the document as INVOICE but structured invoice extraction is missing."));
	}

	@Test
	void flagsAbnormallyLongIdentifiersWithoutChangingTheirValues() {
		String invoiceNumber = "I".repeat(400);
		String taxCode = "T".repeat(500);
		AiDocumentResult.AiInvoiceExtraction invoice = new AiDocumentResult.AiInvoiceExtraction(
				invoiceNumber, null, null, "Seller", taxCode, "Address", "Buyer", null, "Address",
				null, null, null, List.of());
		AiDocumentResult result = new AiDocumentResult("ollama", "vision", "INVOICE", BigDecimal.ONE,
				null, invoice, List.of(), List.of(), "raw", 1L);

		var validated = validator.validate(result, Set.of("INVOICE"));

		assertTrue(validated.requiresReview(new BigDecimal("0.75")));
		assertTrue(validated.warnings().stream().anyMatch(w -> w.startsWith("INVOICE_NUMBER_INVALID_LENGTH:")));
		assertTrue(validated.warnings().stream().anyMatch(w -> w.startsWith("SELLER_TAX_CODE_INVALID_LENGTH:")));
		assertEquals(invoiceNumber, validated.result().invoice().invoiceNumber());
		assertEquals(taxCode, validated.result().invoice().sellerTaxCode());
	}
}
