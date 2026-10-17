-- DRAFT ONLY. NOT APPROVED FOR EXECUTION ON invoice_db.
-- Baseline: PostgreSQL 18.6; 32 historical transactions; only PK index.
-- Historical status/amount/IDs/FKs remain unchanged. New columns remain NULL.
-- Depends on separately approved application/schema deployment planning,
-- including 20261016_01_accounting_category_type.sql; do not run all history.
BEGIN;
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '30s';

-- A historical script proposes one-row-per-document/invoice indexes. Such
-- indexes do not exist in the inspected database. Refuse another baseline;
-- never silently drop an unknown uniqueness rule or edit historical SQL.
DO $preflight$
BEGIN
    IF EXISTS (
        SELECT 1 FROM pg_index i
        JOIN pg_attribute a ON a.attrelid = i.indrelid
            AND a.attnum = ANY(i.indkey)
        WHERE i.indrelid = 'public.financial_transactions'::regclass
          AND i.indisunique AND i.indnkeyatts = 1
          AND a.attname IN ('document_id', 'invoice_id')
    ) THEN
        RAISE EXCEPTION 'Unexpected one-payment-per-record unique index; review migration separately';
    END IF;
END
$preflight$;

ALTER TABLE public.financial_transactions
    ADD COLUMN payment_confirmation VARCHAR(20),
    ADD COLUMN request_key VARCHAR(36),
    ADD COLUMN request_fingerprint VARCHAR(64);

ALTER TABLE public.financial_transactions
    ADD CONSTRAINT ck_financial_payment_provenance CHECK (
        (payment_confirmation IS NULL AND request_key IS NULL AND request_fingerprint IS NULL)
        OR
        (payment_confirmation IN ('CONFIRMED', 'CANCELLED')
            AND payment_confirmation IS NOT NULL
            AND request_key IS NOT NULL AND length(request_key) = 36
            AND request_fingerprint IS NOT NULL
            AND request_fingerprint ~ '^[0-9a-f]{64}$'
            AND created_by IS NOT NULL
            AND ((payment_confirmation = 'CONFIRMED' AND status = 'ACTIVE')
              OR (payment_confirmation = 'CANCELLED' AND status = 'CANCELLED'))
            AND amount > 0
            AND transaction_type IN ('INCOME', 'EXPENSE')
            AND payment_method IS NOT NULL
            AND payment_method IN ('CASH', 'BANK_TRANSFER', 'CARD', 'OTHER'))
    );

CREATE UNIQUE INDEX ux_financial_payment_request
    ON public.financial_transactions(company_id, request_key)
    WHERE request_key IS NOT NULL;

CREATE INDEX ix_financial_payment_document
    ON public.financial_transactions(company_id, document_id);
CREATE INDEX ix_financial_payment_invoice
    ON public.financial_transactions(company_id, invoice_id);
COMMIT;

-- Before COMMIT: ROLLBACK after any failure; do not auto-retry DDL.
-- After COMMIT: stop new payment writes and KEEP columns/indexes/data.
-- Do NOT blindly restore old approval/delete/report code: it treats business
-- records as cash and does not preserve cancelled/confirmed payment semantics.
-- Use a separately reviewed compatibility/hotfix version or a read-only mode.
