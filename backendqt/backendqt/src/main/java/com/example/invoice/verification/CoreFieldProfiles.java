package com.example.invoice.verification;

import com.example.invoice.verification.FieldVerifier.Kind;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Core fields per document type. Mirrors the required fields of the AI validators
 * (AiDocumentResultValidator / DocumentTypeValidationPolicy) so verification and extraction
 * agree on what a type must contain. Types without a profile have no core fields.
 */
public final class CoreFieldProfiles {
    private CoreFieldProfiles() {}

    private static final Map<String, List<String>> CORE = Map.of(
            "VAT_INVOICE", List.of("invoiceSeries", "invoiceNumber", "invoiceDate", "sellerName", "sellerTaxCode",
                    "subtotal", "vatAmount", "totalAmount"),
            "RETAIL_INVOICE", List.of("sellerName", "invoiceDate", "totalAmount"),
            "PAYMENT_VOUCHER", List.of("voucherDate", "payerName", "payeeName", "paymentReason", "totalAmount", "currency"),
            "RECEIPT_VOUCHER", List.of("voucherDate", "payerName", "recipientName", "receiptReason", "totalAmount", "currency"),
            "RECEIPT", List.of("receiptDate", "issuerName", "receiptKind"),
            "CONTRACT", List.of("contractDate", "partyAName", "partyBName", "subject"));

    /** Invoice header fields stored in invoice_data, in display order. */
    public static final List<String> INVOICE_HEADER = List.of("invoiceSeries", "invoiceNumber", "invoiceDate", "signDate",
            "sellerName", "sellerTaxCode", "sellerAddress", "sellerPhone", "buyerName", "buyerTaxCode", "buyerAddress",
            "subtotal", "vatAmount", "totalAmount", "paymentMethod", "amountInWords", "taxAuthorityCode");

    public static List<String> core(String documentType, String receiptKind) {
        String type = documentType == null ? "" : documentType.toUpperCase(Locale.ROOT);
        List<String> base = CORE.getOrDefault(type, List.of());
        // A payment receipt carries money; a non-financial receipt must not be forced to.
        if ("RECEIPT".equals(type) && "PAYMENT".equalsIgnoreCase(receiptKind))
            return java.util.stream.Stream.concat(base.stream(), List.of("payerName", "totalAmount", "currency").stream()).toList();
        return base;
    }

    public static boolean isInvoiceType(String documentType) {
        return documentType != null && (documentType.equalsIgnoreCase("INVOICE")
                || documentType.toUpperCase(Locale.ROOT).endsWith("_INVOICE"));
    }

    private static final Set<String> AMOUNTS = Set.of("subtotal", "vatAmount", "taxAmount", "totalAmount", "amount");

    public static Kind kindOf(String fieldName) {
        String name = fieldName == null ? "" : fieldName;
        if (name.equals("invoiceNumber")) return Kind.INVOICE_NUMBER;
        if (name.equals("invoiceSeries")) return Kind.INVOICE_SERIES;
        if (name.equals("currency")) return Kind.CURRENCY;
        if (name.equals("receiptKind") || name.equals("paymentMethod")) return Kind.CODE;
        if (name.endsWith("TaxCode")) return Kind.TAX_CODE;
        if (name.endsWith("Date")) return Kind.DATE;
        if (AMOUNTS.contains(name) || name.endsWith("Amount")) return Kind.AMOUNT;
        return Kind.TEXT;
    }
}
