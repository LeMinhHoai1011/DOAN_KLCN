BEGIN;

-- Canonical document type is documents.type_id. Keep document_type untouched
-- for backward compatibility, and only fill rows that have an exact known code.
UPDATE documents document
SET type_id = type.id
FROM document_types type
WHERE document.type_id IS NULL
  AND document.document_type IS NOT NULL
  AND UPPER(BTRIM(document.document_type)) = type.code;

CREATE INDEX IF NOT EXISTS idx_documents_type_id ON documents(type_id);

COMMIT;
