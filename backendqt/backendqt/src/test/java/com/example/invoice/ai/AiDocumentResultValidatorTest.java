package com.example.invoice.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
	void confidence084WithValidExtractionIsProcessed() {
		var validated = validator.validate(result("0.84", validInvoice(), List.of()), Set.of("VAT_INVOICE"));
		assertFalse(validated.requiresReview(new BigDecimal("0.75")));
	}

	@Test
	void mildVietnameseDiacriticWarningDoesNotForceReview() {
		var validated = validator.validate(result("0.84", validInvoice(),
				List.of("NON_CRITICAL: địa chỉ có thể thiếu dấu tiếng Việt")), Set.of("VAT_INVOICE"));
		assertFalse(validated.requiresReview(new BigDecimal("0.75")));
	}

	@Test
	void confidenceBelowReviewThresholdRequiresReviewWithReason() {
		var validated = validator.validate(result("0.70", validInvoice(), List.of()), Set.of("VAT_INVOICE"));
		assertTrue(validated.requiresReview(new BigDecimal("0.75")));
		assertTrue(validated.reviewReasons(new BigDecimal("0.75")).getFirst()
				.startsWith("LOW_CLASSIFICATION_CONFIDENCE:"));
	}

	@Test
	void highConfidenceAmountMismatchStillRequiresReview() {
		var invoice = new AiDocumentResult.AiInvoiceExtraction("0001", null, "2026-09-27",
				"Người bán", "0123456789", "Địa chỉ", null, null, null,
				new BigDecimal("100"), new BigDecimal("10"), new BigDecimal("120"), List.of());
		var validated = validator.validate(result("0.96", invoice, List.of()), Set.of("VAT_INVOICE"));
		assertTrue(validated.requiresReview(new BigDecimal("0.75")));
		assertTrue(validated.reviewReasons(new BigDecimal("0.75")).stream()
				.anyMatch(reason -> reason.startsWith("TOTAL_MISMATCH:")));
	}

	@Test
	void highConfidenceInvalidCriticalFieldStillRequiresReview() {
		var invoice = new AiDocumentResult.AiInvoiceExtraction("0001", null, "2026-09-27",
				"Người bán", "ABC", "Địa chỉ", null, null, null,
				new BigDecimal("100"), new BigDecimal("10"), new BigDecimal("110"), List.of());
		var validated = validator.validate(result("0.95", invoice, List.of()), Set.of("VAT_INVOICE"));
		assertTrue(validated.requiresReview(new BigDecimal("0.75")));
		assertTrue(validated.reviewReasons(new BigDecimal("0.75")).stream()
				.anyMatch(reason -> reason.startsWith("INVALID_SELLER_TAX_CODE:")));
	}

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
	void acceptsEveryActiveDatabaseDocumentTypeCode() {
		Set<String> activeTypes = Set.of("VAT_INVOICE", "RECEIPT", "PAYMENT_VOUCHER", "RECEIPT_VOUCHER", "CONTRACT", "OTHER");
		for (String type : activeTypes) {
			AiDocumentResult result = new AiDocumentResult("ollama", "qwen3-vl", type, new BigDecimal("0.90"),
					null, "VAT_INVOICE".equals(type) ? validInvoice() : null, List.of(),
					List.of("DOCUMENT_TYPE_REASON: bằng chứng kiểm thử"), "raw", 1L);
			assertEquals(type, validator.validate(result, activeTypes).result().documentType());
		}
	}

	@Test
	void uncertainOtherRequiresReview() {
		AiDocumentResult result = new AiDocumentResult("ollama", "qwen3-vl", "OTHER", new BigDecimal("0.40"),
				null, null, List.of(), List.of("DOCUMENT_TYPE_REASON: không đủ bằng chứng"), "raw", 1L);
		assertTrue(validator.validate(result, Set.of("OTHER")).requiresReview(new BigDecimal("0.75")));
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

	@Test
	void acceptsNullableAndUnicodeVatFieldsButWarnsForInvalidSignDate() {
		AiDocumentResult.AiInvoiceExtraction invoice = new AiDocumentResult.AiInvoiceExtraction(
				"0008", "1C26TAA", "2026-09-25", "CÔNG TY TNHH ÁNH DƯƠNG", "0312345678", null,
				null, "Người mua", null, null, null, null, null, "TM/CK",
				"Mười triệu đồng.", "CQT-A9Z-001", "25/09/2026", List.of());
		AiDocumentResult result = new AiDocumentResult("ollama", "qwen3-vl", "VAT_INVOICE", BigDecimal.ONE,
				null, invoice, List.of(), List.of(), "raw", 1L);

		var validated = validator.validate(result, Set.of("VAT_INVOICE"));

		assertEquals("CÔNG TY TNHH ÁNH DƯƠNG", validated.result().invoice().sellerName());
		assertEquals("CQT-A9Z-001", validated.result().invoice().taxAuthorityCode());
		assertTrue(validated.warnings().stream().anyMatch(warning -> warning.startsWith("INVALID_SIGN_DATE:")));
	}

	@Test
	void rejectsAccountingCategoryOutsideCompanyActiveTaxonomy() {
		AiDocumentResult result = new AiDocumentResult("ollama", "qwen3-vl", "RECEIPT", BigDecimal.ONE,
				"CROSS_COMPANY", null, null, null, List.of(), List.of(), "raw", 1L);

		assertThrows(AiProviderException.class,
				() -> validator.validate(result, Set.of("RECEIPT"), Set.of("CHI_PHI_VAN_PHONG")));
	}

	private AiDocumentResult result(String confidence, AiDocumentResult.AiInvoiceExtraction invoice,
			List<String> warnings) {
		return new AiDocumentResult("ollama", "qwen3-vl", "VAT_INVOICE", new BigDecimal(confidence),
				null, invoice, List.of(), warnings, "raw", 1L);
	}

	private AiDocumentResult.AiInvoiceExtraction validInvoice() {
		return new AiDocumentResult.AiInvoiceExtraction("0001", null, "2026-09-27",
				"Công ty Ánh Dương", "0123456789", "Đường Nguyễn Văn Linh, Phường Tân Phong",
				null, null, null, new BigDecimal("100"), new BigDecimal("10"), new BigDecimal("110"), List.of());
	}
}
