package com.example.invoice.ai;

import com.example.invoice.dto.ai.AiDocumentResult;
import com.example.invoice.dto.ai.AiDocumentResult.AiFieldNote;
import com.example.invoice.dto.ai.AiDocumentResult.AiInvoiceExtraction;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The model drops or adds a leading zero of a long zero run ("00004848" returned as "0004848"); the prompt already says
 * to keep them. The number is replaced only when the source text the model read prints the same number with a
 * different zero padding, and that printed form is unambiguous: one form after a number label ("Số", "No."), or else
 * one zero-padded form anywhere. A number the source prints exactly as returned is never changed.
 */
public final class InvoiceNumberRepairs {
    private InvoiceNumberRepairs() {}

    public static final String REPAIRED = "INVOICE_NUMBER_REPAIRED";
    public static final String NOTE_REASON = "INVOICE_NUMBER_ZEROS_PRINTED_IN_SOURCE";
    private static final String LABEL = "(?iu)(?:s[oốồổỏôó]|no\\.?|number)\\s*(?:\\([^)]{0,16}\\))?\\s*[:.]?\\s*";

    public static AiDocumentResult apply(AiDocumentResult result, String sourceText) {
        if (result == null || result.invoice() == null || sourceText == null || sourceText.isEmpty()) return result;
        String number = result.invoice().invoiceNumber();
        String printed = printedForm(sourceText, number);
        if (printed == null) return result;
        var warnings = new ArrayList<>(result.warnings() == null ? List.<String>of() : result.warnings());
        warnings.add(REPAIRED + ": field=invoiceNumber; " + number + " -> " + printed + " (số hóa đơn in trên chứng từ)");
        var notes = new ArrayList<>(result.notes());
        notes.add(new AiFieldNote("invoiceNumber", null, number, printed, "NORMALIZED", NOTE_REASON));
        AiInvoiceExtraction in = result.invoice();
        var invoice = new AiInvoiceExtraction(printed, in.invoiceSeries(), in.invoiceDate(), in.sellerName(),
                in.sellerTaxCode(), in.sellerAddress(), in.sellerPhone(), in.buyerName(), in.buyerTaxCode(), in.buyerAddress(),
                in.subtotal(), in.vatAmount(), in.totalAmount(), in.paymentMethod(), in.amountInWords(), in.taxAuthorityCode(),
                in.signDate(), in.items(), in.retailDetails());
        return result.with(warnings, invoice, result.fields(), result.extraFields(), notes);
    }

    /** The printed zero padding of {@code number}, or null when it is already as printed, absent or ambiguous. */
    static String printedForm(String source, String number) {
        if (number == null || !number.matches("\\d{1,8}")) return null;
        String core = number.replaceFirst("^0+", "");
        if (core.isEmpty()) return null;
        String token = "(0*" + core + ")(?![\\d]|[.,]\\d)";
        String start = "(?<![\\dA-Za-z.,/-])";
        Set<String> all = forms(Pattern.compile(start + token).matcher(source));
        if (all.contains(number)) return null;
        Set<String> labelled = forms(Pattern.compile(LABEL + token).matcher(source));
        labelled.removeIf(f -> f.length() > 8);
        if (labelled.size() == 1) return labelled.iterator().next();
        if (!labelled.isEmpty()) return null;
        Set<String> padded = new LinkedHashSet<>();
        for (String f : all) if (f.startsWith("0") && f.length() <= 8) padded.add(f);
        return padded.size() == 1 ? padded.iterator().next() : null;
    }

    private static Set<String> forms(java.util.regex.Matcher m) {
        Set<String> found = new LinkedHashSet<>();
        while (m.find()) found.add(m.group(1));
        return found;
    }
}
