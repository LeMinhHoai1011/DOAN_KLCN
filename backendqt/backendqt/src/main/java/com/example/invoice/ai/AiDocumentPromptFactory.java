package com.example.invoice.ai;

import java.util.List;

public final class AiDocumentPromptFactory {
	private AiDocumentPromptFactory() {
	}

	public static String create(List<String> allowedDocumentTypes) {
		return """
			You are a document information extraction engine. Analyze the supplied document image once for both classification and extraction.
			Return JSON only, without markdown or explanation. Do not guess any value that is not visible. Use null when unknown.
			Document type must be exactly one of: %s
			classificationConfidence must be a number from 0.0 to 1.0.
			Keep Vietnamese text, invoice numbers and tax codes exactly as visible. Invoice dates must use yyyy-MM-dd only when unambiguous.
			Monetary values must be JSON numbers when visible. Do not correct inconsistent amounts; add a warning instead.
			Return this JSON object shape:
			{"documentType":null,"classificationConfidence":null,"rawText":null,"invoice":{"invoiceNumber":null,"invoiceSeries":null,"invoiceDate":null,"sellerName":null,"sellerTaxCode":null,"sellerAddress":null,"buyerName":null,"buyerTaxCode":null,"buyerAddress":null,"subtotal":null,"vatAmount":null,"totalAmount":null,"items":[]},"fields":[{"fieldName":null,"fieldValue":null,"confidence":null}],"warnings":[]}
			""".formatted(String.join(", ", allowedDocumentTypes));
	}
}
