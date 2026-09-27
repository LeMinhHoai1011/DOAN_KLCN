package com.example.invoice.ai;

import com.example.invoice.dto.ai.AiDocumentResult;
import com.example.invoice.exception.AiProviderException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class AiDocumentResultValidator {
	private static final Pattern TAX_CODE = Pattern.compile("\\d{10}(?:-\\d{3})?");
	private static final BigDecimal MONEY_TOLERANCE = new BigDecimal("0.01");

	public ValidatedAiDocumentResult validate(AiDocumentResult result, Set<String> allowedTypes) {
		if (result.documentType() == null || !allowedTypes.contains(result.documentType())) {
			throw new AiProviderException("AI_INVALID_RESPONSE: document type is not in the backend allowlist");
		}
		List<String> warnings = new ArrayList<>(result.warnings() == null ? List.of() : result.warnings());
		BigDecimal confidence = normalizeConfidence(result.classificationConfidence(), warnings);
		validateInvoice(result.invoice(), warnings);
		return new ValidatedAiDocumentResult(result, confidence, List.copyOf(warnings));
	}

	private BigDecimal normalizeConfidence(BigDecimal value, List<String> warnings) {
		if (value == null) {
			warnings.add("AI did not provide classification confidence");
			return BigDecimal.ZERO;
		}
		if (value.compareTo(BigDecimal.ZERO) < 0 || value.compareTo(BigDecimal.ONE) > 0) {
			throw new AiProviderException("AI_INVALID_RESPONSE: confidence must be between 0.0 and 1.0");
		}
		return value;
	}

	private void validateInvoice(AiDocumentResult.AiInvoiceExtraction invoice, List<String> warnings) {
		if (invoice == null) return;
		if (invoice.sellerTaxCode() != null && !TAX_CODE.matcher(invoice.sellerTaxCode().trim()).matches()) {
			warnings.add("Seller tax code has an unexpected format");
		}
		if (invoice.buyerTaxCode() != null && !TAX_CODE.matcher(invoice.buyerTaxCode().trim()).matches()) {
			warnings.add("Buyer tax code has an unexpected format");
		}
		if (invoice.invoiceDate() != null) {
			try {
				LocalDate.parse(invoice.invoiceDate());
			} catch (DateTimeParseException exception) {
				warnings.add("Invoice date is not an unambiguous yyyy-MM-dd value");
			}
		}
		if (invoice.subtotal() != null && invoice.vatAmount() != null && invoice.totalAmount() != null
				&& invoice.subtotal().add(invoice.vatAmount()).subtract(invoice.totalAmount()).abs().compareTo(MONEY_TOLERANCE) > 0) {
			warnings.add("Subtotal plus VAT does not match total amount");
		}
	}

	public record ValidatedAiDocumentResult(AiDocumentResult result, BigDecimal confidence, List<String> warnings) {
		public boolean requiresReview(BigDecimal threshold) {
			return confidence.compareTo(threshold) < 0 || !warnings.isEmpty();
		}
	}
}
