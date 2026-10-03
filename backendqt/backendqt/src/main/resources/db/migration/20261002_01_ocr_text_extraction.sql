BEGIN;

ALTER TABLE ocr_results
    ADD COLUMN IF NOT EXISTS language VARCHAR(50),
    ADD COLUMN IF NOT EXISTS source_type VARCHAR(30);

ALTER TABLE invoice_data ADD COLUMN IF NOT EXISTS ai_generated BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE invoice_data invoice
SET ai_generated = TRUE
WHERE EXISTS (
    SELECT 1 FROM ai_classifications classification
    WHERE classification.document_id = invoice.document_id
      AND classification.ai_generated = TRUE
)
AND NOT EXISTS (
    SELECT 1 FROM extracted_fields field
    JOIN field_corrections correction ON correction.field_id = field.field_id
    WHERE field.document_id = invoice.document_id
);

COMMIT;
