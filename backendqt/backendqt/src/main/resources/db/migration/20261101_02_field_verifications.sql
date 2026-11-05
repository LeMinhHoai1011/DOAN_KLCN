-- Run this upgrade explicitly; do not replay historical migrations.
-- Adds a new table only. No existing row is read, changed or backfilled:
-- documents processed before this upgrade simply have no field verification until reprocessed or recomputed.
BEGIN;
SET LOCAL lock_timeout = '5s';
CREATE TABLE IF NOT EXISTS public.field_verifications (
    verification_id BIGSERIAL PRIMARY KEY,
    document_id     BIGINT       NOT NULL REFERENCES public.documents(document_id) ON DELETE CASCADE,
    field_key       VARCHAR(120) NOT NULL,
    item_index      INTEGER,
    is_core         BOOLEAN      NOT NULL,
    status          VARCHAR(20)  NOT NULL CHECK (status IN ('VERIFIED', 'WARNING', 'UNVERIFIED', 'MISSING')),
    score           INTEGER CHECK (score IS NULL OR score BETWEEN 0 AND 100),
    checked_value   TEXT,
    ai_value        TEXT,
    value_origin    VARCHAR(10)  NOT NULL CHECK (value_origin IN ('AI', 'MANUAL')),
    ai_input_mode   VARCHAR(10)  NOT NULL CHECK (ai_input_mode IN ('TEXT', 'VISION', 'MIXED', 'UNKNOWN')),
    evidence_json   TEXT,
    rules_json      TEXT         NOT NULL,
    reasons_json    TEXT         NOT NULL,
    rule_version    VARCHAR(40)  NOT NULL,
    trigger_source  VARCHAR(30)  NOT NULL,
    verified_at     TIMESTAMP    NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_field_verifications_document ON public.field_verifications(document_id);
COMMIT;
