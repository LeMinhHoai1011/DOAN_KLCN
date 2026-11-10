package com.example.invoice.dto.ai;

import com.fasterxml.jackson.annotation.JsonAlias;
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
		AiCompanyRole companyRole,
		List<AiFieldNote> fieldNotes) {

	/** Constructor used before field notes existed; the result carries no notes. */
	public AiDocumentResult(String provider, String model, String documentType, BigDecimal classificationConfidence,
			String accountingCategoryCode, String accountingAccount, String rawText, AiInvoiceExtraction invoice,
			List<AiExtractedField> fields, List<String> warnings, String rawResponse, long durationMs,
			DocumentDirection documentDirection, AiTransactionAssessment transactionAssessment,
			List<AiExtraField> extraFields, AiCompanyRole companyRole) {
		this(provider, model, documentType, classificationConfidence, accountingCategoryCode, accountingAccount, rawText,
				invoice, fields, warnings, rawResponse, durationMs, documentDirection, transactionAssessment, extraFields,
				companyRole, List.of());
	}

	/**
	 * What normalization did to one value: the printed/model text, the value kept, and why. {@code row} is the
	 * one-based goods row, null for header fields. Never filled by the model; the parser and validators write it.
	 */
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record AiFieldNote(String field, Integer row, String rawValue, String normalizedValue, String status, String reason) {}

	public List<AiFieldNote> notes() { return fieldNotes == null ? List.of() : fieldNotes; }

	/** Same result with other warnings, invoice, extra fields and notes; everything else is kept. */
	public AiDocumentResult with(List<String> newWarnings, AiInvoiceExtraction newInvoice, List<AiExtractedField> newFields,
			List<AiExtraField> newExtraFields, List<AiFieldNote> newNotes) {
		return new AiDocumentResult(provider, model, documentType, classificationConfidence, accountingCategoryCode,
				accountingAccount, rawText, newInvoice, newFields, newWarnings, rawResponse, durationMs, documentDirection,
				transactionAssessment, newExtraFields, companyRole, newNotes == null ? List.of() : List.copyOf(newNotes));
	}

	public AiDocumentResult withWarnings(List<String> newWarnings) {
		return with(newWarnings, invoice, fields, extraFields, notes());
	}

	public AiDocumentResult withInvoice(AiInvoiceExtraction newInvoice, List<AiFieldNote> newNotes) {
		return with(warnings, newInvoice, fields, extraFields, newNotes);
	}

	public AiDocumentResult withoutAccountingCategory() {
		return new AiDocumentResult(provider, model, documentType, classificationConfidence, null, accountingAccount, rawText,
				invoice, fields, warnings, rawResponse, durationMs, documentDirection, transactionAssessment, extraFields,
				companyRole, notes());
	}

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
				companyRole == null ? new AiCompanyRole(CompanyRole.UNKNOWN, null, null) : companyRole, notes());
	}

	public AiDocumentResult withCompanyRoleAndDirection(AiCompanyRole role, DocumentDirection direction) {
		return new AiDocumentResult(provider, model, documentType, classificationConfidence, accountingCategoryCode,
				accountingAccount, rawText, invoice, fields == null ? List.of() : List.copyOf(fields),
				warnings == null ? List.of() : List.copyOf(warnings), rawResponse, durationMs,
				direction == null ? DocumentDirection.UNKNOWN : direction, transactionAssessment,
				extraFields == null ? List.of() : List.copyOf(extraFields),
				role == null ? new AiCompanyRole(CompanyRole.UNKNOWN, null, null) : role, notes());
	}

	public AiDocumentResult withTransactionAssessment(AiTransactionAssessment assessment) {
		return new AiDocumentResult(provider, model, documentType, classificationConfidence, accountingCategoryCode,
				accountingAccount, rawText, invoice, fields == null ? List.of() : List.copyOf(fields),
				warnings == null ? List.of() : List.copyOf(warnings), rawResponse, durationMs, documentDirection,
				assessment == null ? new AiTransactionAssessment(TransactionAssessmentType.UNKNOWN, null, null) : assessment,
				extraFields == null ? List.of() : List.copyOf(extraFields), companyRole, notes());
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
			String sellerPhone,
			String buyerName,
			String buyerTaxCode,
			String buyerAddress,
			BigDecimal subtotal,
			BigDecimal vatAmount,
			BigDecimal totalAmount,
			String paymentMethod,
			String amountInWords,
			String taxAuthorityCode,
			String signDate,
			@JsonAlias("invoiceItems") List<AiInvoiceItemExtraction> items,
            com.example.invoice.dto.invoice.RetailInvoiceDetails retailDetails) {
        public AiInvoiceExtraction withRetailDetails(com.example.invoice.dto.invoice.RetailInvoiceDetails details) {
            return new AiInvoiceExtraction(invoiceNumber,invoiceSeries,invoiceDate,sellerName,sellerTaxCode,sellerAddress,
                    sellerPhone,buyerName,buyerTaxCode,buyerAddress,subtotal,vatAmount,totalAmount,paymentMethod,
                    amountInWords,taxAuthorityCode,signDate,items,details);
        }
        public AiInvoiceExtraction(String invoiceNumber,String invoiceSeries,String invoiceDate,
                String sellerName,String sellerTaxCode,String sellerAddress,String sellerPhone,String buyerName,
                String buyerTaxCode,String buyerAddress,BigDecimal subtotal,BigDecimal vatAmount,BigDecimal totalAmount,
                String paymentMethod,String amountInWords,String taxAuthorityCode,String signDate,List<AiInvoiceItemExtraction> items) {
            this(invoiceNumber,invoiceSeries,invoiceDate,sellerName,sellerTaxCode,sellerAddress,sellerPhone,buyerName,
                    buyerTaxCode,buyerAddress,subtotal,vatAmount,totalAmount,paymentMethod,amountInWords,taxAuthorityCode,signDate,items,null);
        }
		public AiInvoiceExtraction(String invoiceNumber, String invoiceSeries, String invoiceDate,
				String sellerName, String sellerTaxCode, String sellerAddress, String buyerName,
				String buyerTaxCode, String buyerAddress, BigDecimal subtotal, BigDecimal vatAmount,
				BigDecimal totalAmount, List<AiInvoiceItemExtraction> items) {
			this(invoiceNumber, invoiceSeries, invoiceDate, sellerName, sellerTaxCode, sellerAddress, null,
					buyerName, buyerTaxCode, buyerAddress, subtotal, vatAmount, totalAmount,
					null, null, null, null, items);
		}
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record AiInvoiceItemExtraction(
			@JsonAlias({"description", "itemName", "name", "productDescription"}) String productName,
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
