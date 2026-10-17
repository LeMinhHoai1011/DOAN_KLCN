-- DRAFT / REVIEW ONLY. NOT APPROVED FOR EXECUTION.
-- Kept outside src/main/resources/db/migration to avoid automatic discovery.
-- Baseline inspected read-only: PostgreSQL 18.6, invoice_db, public.
-- Expected: accounting_categories exists; category_type does not exist.
-- Existing rows must remain unclassified (NULL). No backfill or default.
-- A separately approved backup/restore test and execution gate are required.

BEGIN;

SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '30s';

ALTER TABLE public.accounting_categories
    ADD COLUMN category_type VARCHAR(20);

ALTER TABLE public.accounting_categories
    ADD CONSTRAINT ck_accounting_categories_category_type
    CHECK (category_type IS NULL OR category_type IN ('INCOME', 'EXPENSE'));

COMMIT;

-- Rollback before COMMIT: ROLLBACK in the same session after any error.
-- Rollback after COMMIT: restore the previous application version and KEEP
-- the nullable column, constraint, and all data. No destructive down script.
