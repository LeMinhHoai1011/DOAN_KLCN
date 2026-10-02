package com.example.invoice.ai;

import java.util.List;

public final class AiDocumentPromptFactory {
	private AiDocumentPromptFactory() {
	}

	public static String create(List<String> allowedDocumentTypes, List<String> allowedAccountingCategories) {
		return create(allowedDocumentTypes, allowedAccountingCategories, null, null);
	}

	public static String create(List<String> allowedDocumentTypes, List<String> allowedAccountingCategories,
			String companyName, String companyTaxCode) {
		return """
			You are a document information extraction engine. Analyze the supplied document image once for both classification and extraction.
			Return JSON only: no markdown, explanation, preamble, or reasoning. Extract only values supported by the supplied source. Do not guess, repair, complete, or infer missing identifiers or amounts. Use null when missing or uncertain.
			Preserve [PAGE n] boundaries and inspect every page. Never discard later-page totals, tax, metadata, or invoice items. Do not duplicate the same item across pages.
			Current company context: name=%s; taxCode=%s. Determine its role in the document using only visible evidence. Prefer an exact tax-code match over name/layout evidence; do not claim a match when the tax code is absent.
			Document type must be exactly one of: %s. Use OTHER only when it is in that list; do not invent a type.
			documentDirection must be one of INCOMING, OUTGOING, INTERNAL, UNKNOWN.
			companyRole.role must be one of SELLER, BUYER, ISSUER, RECIPIENT, INTERNAL, UNRELATED, UNKNOWN. companyRole has confidence from 0.0 to 1.0 and a concise evidence-based reason.
			transactionAssessment is only a suggestion for backend normalization. Its type must be one of INCOME, EXPENSE, TRANSFER, NON_FINANCIAL, UNKNOWN. Its confidence must be 0.0 to 1.0; reason must be concise and based only on visible information. Do not infer EXPENSE solely from INCOMING or INCOME solely from OUTGOING. Use UNKNOWN when evidence is insufficient and never provide chain-of-thought.
			Accounting category must be null or exactly one of: %s. Suggest accountingAccount only if visible or strongly implied; otherwise null.
			classificationConfidence must be a number from 0.0 to 1.0.
			Keep Vietnamese text, invoice numbers and tax codes exactly as visible. Invoice dates must use yyyy-MM-dd only when unambiguous.
			Monetary values must be JSON numbers when visible. Do not correct inconsistent amounts; add a warning instead.
			For non-invoice documents, invoice must be null. Put document-specific information not covered by the invoice schema in extraFields using a stable name, a human-readable label, visible value, and confidence.
			Return this JSON object shape:
			{"documentType":null,"documentDirection":"UNKNOWN","companyRole":{"role":"UNKNOWN","confidence":null,"reason":null},"classificationConfidence":null,"transactionAssessment":{"type":"UNKNOWN","confidence":null,"reason":null},"accountingCategoryCode":null,"accountingAccount":null,"rawText":null,"invoice":{"invoiceNumber":null,"invoiceSeries":null,"invoiceDate":null,"sellerName":null,"sellerTaxCode":null,"sellerAddress":null,"buyerName":null,"buyerTaxCode":null,"buyerAddress":null,"subtotal":null,"vatAmount":null,"totalAmount":null,"items":[]},"fields":[{"fieldName":null,"fieldValue":null,"confidence":null}],"extraFields":[{"name":null,"label":null,"value":null,"confidence":null}],"warnings":[]}
			""".formatted(displayValue(companyName), displayValue(companyTaxCode), String.join(", ", allowedDocumentTypes), String.join(", ", allowedAccountingCategories));
	}

	private static String displayValue(String value) {
		return value == null || value.isBlank() ? "unknown" : value.trim();
	}
}
