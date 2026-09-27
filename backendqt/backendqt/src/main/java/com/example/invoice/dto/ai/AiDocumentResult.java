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
		String rawText,
		AiInvoiceExtraction invoice,
		List<AiExtractedField> fields,
		List<String> warnings,
		String rawResponse,
		long durationMs) {

	public AiDocumentResult withMetadata(String providerName, String modelName, String response, long duration) {
		return new AiDocumentResult(providerName, modelName, documentType, classificationConfidence, rawText,
				invoice, fields == null ? List.of() : List.copyOf(fields), warnings == null ? List.of() : List.copyOf(warnings),
				response, duration);
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
