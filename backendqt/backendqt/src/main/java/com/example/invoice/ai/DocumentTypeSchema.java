package com.example.invoice.ai;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One table of which fields each document type needs. REQUIRED is a business requirement for that type, OPTIONAL is
 * read when printed and may be absent, NOT_APPLICABLE does not belong to the type (and is never reported missing).
 * The validator's warnings follow the same table; the field report shows it to the accountant.
 */
public final class DocumentTypeSchema {
	private DocumentTypeSchema() {}

	public enum Requirement { REQUIRED, OPTIONAL, NOT_APPLICABLE }

	/** Header fields of an invoice, in display order. */
	public static final String[] INVOICE_FIELDS = {"invoiceNumber", "invoiceSeries", "invoiceDate", "sellerName",
			"sellerTaxCode", "sellerAddress", "sellerPhone", "buyerName", "buyerTaxCode", "buyerAddress", "subtotal",
			"vatAmount", "totalAmount", "paymentMethod", "amountInWords", "taxAuthorityCode", "signDate"};
	/** Columns of one goods row. */
	public static final String[] ITEM_FIELDS = {"productName", "quantity", "unit", "unitPrice", "taxRate", "taxAmount",
			"discountAmount", "amount"};

	/**
	 * Fields for the type. {@code receiptKind} only matters for RECEIPT (PAYMENT or NON_FINANCIAL); other types ignore it.
	 * An unknown type gets an empty map: nothing can be required of it.
	 */
	public static Map<String, Requirement> fields(String documentType, String receiptKind) {
		Map<String, Requirement> fields = new LinkedHashMap<>();
		if (documentType == null) return fields;
		switch (documentType) {
			case "VAT_INVOICE", "INVOICE" -> {
				for (String name : INVOICE_FIELDS) fields.put(name, Requirement.OPTIONAL);
				for (String name : new String[] {"invoiceNumber", "invoiceDate", "sellerName", "sellerTaxCode", "subtotal",
						"vatAmount", "totalAmount"}) fields.put(name, Requirement.REQUIRED);
				fields.put("items", Requirement.REQUIRED);
			}
			case "RETAIL_INVOICE" -> {
				for (String name : INVOICE_FIELDS) fields.put(name, Requirement.OPTIONAL);
				fields.put("invoiceDate", Requirement.REQUIRED);
				fields.put("sellerName", Requirement.REQUIRED);
				fields.put("totalAmount", Requirement.REQUIRED);
				fields.put("taxAuthorityCode", Requirement.NOT_APPLICABLE);
				fields.put("items", Requirement.REQUIRED);
			}
			case "RECEIPT" -> {
				required(fields, "receiptDate", "issuerName", "receiptKind");
				optional(fields, "issuerTaxCode");
				if ("PAYMENT".equals(receiptKind)) {
					required(fields, "payerName", "totalAmount", "currency");
					optional(fields, "payerTaxCode");
				} else if ("NON_FINANCIAL".equals(receiptKind)) {
					required(fields, "recipientName", "subject");
					optional(fields, "recipientTaxCode");
					fields.put("totalAmount", Requirement.NOT_APPLICABLE);
					fields.put("currency", Requirement.NOT_APPLICABLE);
				}
			}
			case "PAYMENT_VOUCHER" -> {
				required(fields, "voucherDate", "payerName", "payeeName", "paymentReason", "totalAmount", "currency");
				optional(fields, "voucherNumber", "payerTaxCode", "payeeTaxCode");
			}
			case "RECEIPT_VOUCHER" -> {
				required(fields, "voucherDate", "payerName", "recipientName", "receiptReason", "totalAmount", "currency");
				optional(fields, "voucherNumber", "payerTaxCode", "recipientTaxCode");
			}
			case "CONTRACT" -> {
				required(fields, "contractDate", "partyAName", "partyBName", "subject");
				optional(fields, "contractNumber", "contractTitle", "referenceNumber", "totalAmount", "currency",
						"partyATaxCode", "partyBTaxCode", "effectiveDate", "expiryDate");
			}
			default -> { }
		}
		return fields;
	}

	public static boolean isInvoice(String documentType) {
		return "VAT_INVOICE".equals(documentType) || "INVOICE".equals(documentType) || "RETAIL_INVOICE".equals(documentType);
	}

	private static void required(Map<String, Requirement> fields, String... names) {
		for (String name : names) fields.put(name, Requirement.REQUIRED);
	}

	private static void optional(Map<String, Requirement> fields, String... names) {
		for (String name : names) fields.putIfAbsent(name, Requirement.OPTIONAL);
	}
}
