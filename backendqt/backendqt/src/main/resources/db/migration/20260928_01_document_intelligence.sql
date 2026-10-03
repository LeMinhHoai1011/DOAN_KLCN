BEGIN;

ALTER TABLE documents
    ADD COLUMN IF NOT EXISTS company_role VARCHAR(20),
    ADD COLUMN IF NOT EXISTS company_role_confidence NUMERIC(5,2),
    ADD COLUMN IF NOT EXISTS company_role_reason TEXT,
    ADD COLUMN IF NOT EXISTS document_direction VARCHAR(20),
    ADD COLUMN IF NOT EXISTS transaction_assessment_type VARCHAR(20),
    ADD COLUMN IF NOT EXISTS transaction_assessment_confidence NUMERIC(5,2),
    ADD COLUMN IF NOT EXISTS transaction_assessment_reason TEXT;

ALTER TABLE extracted_fields ADD COLUMN IF NOT EXISTS document_id BIGINT;

UPDATE extracted_fields ef
SET document_id = invoice.document_id
FROM invoice_data invoice
WHERE ef.invoice_id = invoice.invoice_id
  AND ef.document_id IS NULL;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM extracted_fields WHERE document_id IS NULL) THEN
        RAISE EXCEPTION 'Cannot make extracted_fields.document_id mandatory: legacy rows could not be backfilled';
    END IF;
END $$;

ALTER TABLE extracted_fields ALTER COLUMN document_id SET NOT NULL;
ALTER TABLE extracted_fields ALTER COLUMN invoice_id DROP NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_extracted_fields_document') THEN
        ALTER TABLE extracted_fields ADD CONSTRAINT fk_extracted_fields_document
            FOREIGN KEY (document_id) REFERENCES documents(document_id);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_extracted_fields_document_id ON extracted_fields(document_id);

COMMIT;
