-- Retried approval/reprocess requests must not create duplicate financial effects.
CREATE UNIQUE INDEX IF NOT EXISTS ux_financial_transactions_document
    ON financial_transactions(document_id) WHERE document_id IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS ux_financial_transactions_invoice
    ON financial_transactions(invoice_id) WHERE invoice_id IS NOT NULL;
