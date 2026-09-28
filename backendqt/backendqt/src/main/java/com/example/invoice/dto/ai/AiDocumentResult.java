package com.example.invoice.dto.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.util.List;

/** Structured, provider-independent result of one document analysis. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AiDocumentResult(
		String provider,
		String model,
		String documentType,
		BigDecimal classificationConfidence,
		String accountingCategoryCode,
		String accountingAccount,
		String rawText,
		AiInvoiceExtraction invoice,
		List<AiExtractedField> fields,
		List<String> warnings,
		String rawResponse,
		long durationMs,
		DocumentDirection documentDirection,
		AiTransactionAssessment transactionAssessment,
		List<AiExtraField> extraFields,
		AiCompanyRole companyRole) {

	/**
	 * Compatibility constructor for providers and tests that only return the original
	 * invoice-oriented contract. General document metadata is deliberately optional.
	 */
	public AiDocumentResult(String provider, String model, String documentType, BigDecimal classificationConfidence,
			String accountingCategoryCode, String accountingAccount, String rawText, AiInvoiceExtraction invoice,
			List<AiExtractedField> fields, List<String> warnings, String rawResponse, long durationMs) {
		this(provider, model, documentType, classificationConfidence, accountingCategoryCode, accountingAccount, rawText,
				invoice, fields, warnings, rawResponse, durationMs, DocumentDirection.UNKNOWN,
				new AiTransactionAssessment(TransactionAssessmentType.UNKNOWN, null, null), List.of(),
				new AiCompanyRole(CompanyRole.UNKNOWN, null, null));
	}

	public AiDocumentResult(String provider, String model, String documentType, BigDecimal classificationConfidence,
			String rawText, AiInvoiceExtraction invoice, List<AiExtractedField> fields, List<String> warnings,
			String rawResponse, long durationMs) {
		this(provider, model, documentType, classificationConfidence, null, null, rawText, invoice, fields, warnings,
				rawResponse, durationMs);
	}

	public AiDocumentResult withMetadata(String providerName, String modelName, String response, long duration) {
		return new AiDocumentResult(providerName, modelName, documentType, classificationConfidence, accountingCategoryCode, accountingAccount, rawText,
				invoice, fields == null ? List.of() : List.copyOf(fields), warnings == null ? List.of() : List.copyOf(warnings),
				response, duration, documentDirection == null ? DocumentDirection.UNKNOWN : documentDirection,
				transactionAssessment == null ? new AiTransactionAssessment(TransactionAssessmentType.UNKNOWN, null, null) : transactionAssessment,
				extraFields == null ? List.of() : List.copyOf(extraFields),
				companyRole == null ? new AiCompanyRole(CompanyRole.UNKNOWN, null, null) : companyRole);
	}

	public AiDocumentResult withCompanyRoleAndDirection(AiCompanyRole role, DocumentDirection direction) {
		return new AiDocumentResult(provider, model, documentType, classificationConfidence, accountingCategoryCode,
				accountingAccount, rawText, invoice, fields == null ? List.of() : List.copyOf(fields),
				warnings == null ? List.of() : List.copyOf(warnings), rawResponse, durationMs,
				direction == null ? DocumentDirection.UNKNOWN : direction, transactionAssessment,
				extraFields == null ? List.of() : List.copyOf(extraFields),
				role == null ? new AiCompanyRole(CompanyRole.UNKNOWN, null, null) : role);
	}

	public AiDocumentResult withTransactionAssessment(AiTransactionAssessment assessment) {
		return new AiDocumentResult(provider, model, documentType, classificationConfidence, accountingCategoryCode,
				accountingAccount, rawText, invoice, fields == null ? List.of() : List.copyOf(fields),
				warnings == null ? List.of() : List.copyOf(warnings), rawResponse, durationMs, documentDirection,
				assessment == null ? new AiTransactionAssessment(TransactionAssessmentType.UNKNOWN, null, null) : assessment,
				extraFields == null ? List.of() : List.copyOf(extraFields), companyRole);
	}

	public enum DocumentDirection {
		INCOMING, OUTGOING, INTERNAL, UNKNOWN
	}

	public enum TransactionAssessmentType {
		INCOME, EXPENSE, TRANSFER, NON_FINANCIAL, UNKNOWN
	}

	public enum CompanyRole {
		SELLER, BUYER, ISSUER, RECIPIENT, INTERNAL, UNRELATED, UNKNOWN
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record AiTransactionAssessment(
			TransactionAssessmentType type,
			BigDecimal confidence,
			String reason) {
		public AiTransactionAssessment {
			type = type == null ? TransactionAssessmentType.UNKNOWN : type;
			reason = normalizeReason(reason);
		}

		private static String normalizeReason(String value) {
			if (value == null || value.isBlank()) return null;
			String normalized = value.replaceAll("[\\r\\n\\t]+", " ").trim();
			return normalized.length() <= 500 ? normalized : normalized.substring(0, 500);
		}
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record AiExtraField(String name, String label, String value, BigDecimal confidence) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record AiCompanyRole(CompanyRole role, BigDecimal confidence, String reason) {
		public AiCompanyRole {
			role = role == null ? CompanyRole.UNKNOWN : role;
		}
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record AiInvoiceExtraction(
			String invoiceNumber,
			String invoiceSeries,
			String invoiceDate,
			String sellerName,
			String sellerTaxCode,
			String sellerAddress,
			String buyerName,
			String buyerTaxCode,
			String buyerAddress,
			BigDecimal subtotal,
			BigDecimal vatAmount,
			BigDecimal totalAmount,
			List<AiInvoiceItemExtraction> items) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record AiInvoiceItemExtraction(
			String productName,
			BigDecimal quantity,
			String unit,
			BigDecimal unitPrice,
			BigDecimal taxRate,
			BigDecimal taxAmount,
			BigDecimal amount) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record AiExtractedField(String fieldName, String fieldValue, BigDecimal confidence) {
	}
}
