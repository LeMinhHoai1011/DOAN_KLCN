-- Run this upgrade explicitly; do not replay historical migrations.
-- Adds a nullable column only: existing review rows keep NULL (= not measured), nothing is backfilled.
BEGIN;
SET LOCAL lock_timeout = '5s';
ALTER TABLE public.document_reviews ADD COLUMN IF NOT EXISTS active_review_ms BIGINT;
ALTER TABLE public.document_reviews DROP CONSTRAINT IF EXISTS document_reviews_active_review_ms_range;
ALTER TABLE public.document_reviews ADD CONSTRAINT document_reviews_active_review_ms_range
    CHECK (active_review_ms IS NULL OR (active_review_ms > 0 AND active_review_ms <= 14400000));
COMMIT;
