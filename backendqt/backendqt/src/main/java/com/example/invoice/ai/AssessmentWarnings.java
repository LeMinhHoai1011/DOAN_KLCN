package com.example.invoice.ai;

import com.example.invoice.dto.document.DocumentAssessment.Warning;
import java.util.Locale;

/** One policy shared by status decisions and the assessment presented to users. */
public final class AssessmentWarnings {
    private AssessmentWarnings() {}

    public static Warning normalize(String text) {
        String message = text == null ? "Cảnh báo không có nội dung" : text.trim();
        int separator = message.indexOf(':');
        String prefix = separator < 0 ? message : message.substring(0, separator);
        String code = prefix.matches("[A-Z][A-Z0-9_]*") ? prefix : "AI_VALIDATION_WARNING";
        String lower = message.toLowerCase(Locale.ROOT);
        // An explicit CRITICAL must never be downgraded by cosmetic text.
        String severity = "CRITICAL";
        if (!message.startsWith("CRITICAL")) {
            if (code.equals("DOCUMENT_TYPE_REASON")) severity = "INFO";
            else if (code.equals("NON_CRITICAL") || (code.equals("AI_VALIDATION_WARNING") && (lower.contains("thiếu dấu") || lower.contains("mất dấu")
                    || lower.contains("diacritic") || lower.contains("hoa/thường") || lower.contains("khoảng trắng")
                    || lower.contains("whitespace") || lower.contains("định dạng địa chỉ")
                    || lower.contains("address format") || lower.contains("preprocessing warning")))) severity = "WARNING";
        }
        String field = null;
        if (code.contains("ITEM")) field = "items";
        else if (code.contains("SELLER_TAX")) field = "sellerTaxCode";
        else if (code.contains("BUYER_TAX")) field = "buyerTaxCode";
        else if (code.contains("SELLER_NAME")) field = "sellerName";
        else if (code.contains("INVOICE_NUMBER")) field = "invoiceNumber";
        else if (code.contains("INVOICE_DATE")) field = "invoiceDate";
        else if (code.contains("SIGN_DATE")) field = "signDate";
        else if (code.contains("SUBTOTAL")) field = "subtotal";
        else if (code.contains("VAT")) field = "vatAmount";
        else if (code.contains("TOTAL")) field = "totalAmount";
        else if (code.contains("CLASSIFICATION")) field = "documentType";
        var explicitField = java.util.regex.Pattern.compile("(?:^|[ :;])field=([A-Za-z][A-Za-z0-9]*)").matcher(message);
        if (explicitField.find()) field = explicitField.group(1);
        return new Warning(code, severity, message, field);
    }

    public static boolean critical(String text) { return "CRITICAL".equals(normalize(text).severity()); }
}
