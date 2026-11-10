package com.example.invoice.ai;

import com.example.invoice.dto.ai.AiDocumentResult;

/** Repairs that need the source text the model read as evidence: money scale, then invoice-number zero padding. */
public final class SourceEvidenceRepairs {
    private SourceEvidenceRepairs() {}

    public static AiDocumentResult apply(AiDocumentResult result, String sourceText) {
        return InvoiceNumberRepairs.apply(MoneyScaleRepairs.apply(result, sourceText), sourceText);
    }
}
