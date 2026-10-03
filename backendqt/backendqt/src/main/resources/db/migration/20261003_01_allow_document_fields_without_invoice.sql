-- Extracted fields belong to a document. Invoice linkage is optional for non-invoice documents.
-- This is intentionally non-destructive: the existing FK and indexes are preserved.
ALTER TABLE extracted_fields
    ALTER COLUMN invoice_id DROP NOT NULL;
