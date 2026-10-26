-- Run this upgrade explicitly; do not replay historical migrations.
-- Changes schema only. Any failure rolls back the entire upgrade.
BEGIN;
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '60s';
LOCK TABLE public.financial_transactions IN ACCESS EXCLUSIVE MODE;

ALTER TABLE public.financial_transactions
    ADD COLUMN IF NOT EXISTS payment_confirmation VARCHAR(20),
    ADD COLUMN IF NOT EXISTS request_key VARCHAR(36),
    ADD COLUMN IF NOT EXISTS request_fingerprint VARCHAR(64);

-- Snapshot every existing row AFTER nullable columns have been added.
CREATE TEMP TABLE payment_upgrade_before ON COMMIT DROP AS
    SELECT to_jsonb(t) AS row_value FROM public.financial_transactions t;

-- Failed assertions abort the transaction without changing stored rows.
CREATE TEMP TABLE payment_upgrade_assert (
    label text, ok boolean NOT NULL CHECK (ok)
) ON COMMIT DROP;
INSERT INTO payment_upgrade_assert
SELECT 'recognized legacy indexes', NOT EXISTS (
    SELECT 1 FROM pg_indexes WHERE schemaname='public'
    AND indexname IN ('ux_financial_transactions_document','ux_financial_transactions_invoice')
    AND indexdef NOT IN (
        'CREATE UNIQUE INDEX ux_financial_transactions_document ON public.financial_transactions USING btree (document_id) WHERE (document_id IS NOT NULL)',
        'CREATE UNIQUE INDEX ux_financial_transactions_invoice ON public.financial_transactions USING btree (invoice_id) WHERE (invoice_id IS NOT NULL)'
    )
);
DROP INDEX IF EXISTS public.ux_financial_transactions_document;
DROP INDEX IF EXISTS public.ux_financial_transactions_invoice;
INSERT INTO payment_upgrade_assert
SELECT 'no unknown one-payment constraint', NOT EXISTS (
    SELECT 1 FROM pg_index i
    WHERE i.indrelid='public.financial_transactions'::regclass
    AND i.indisunique AND i.indnkeyatts=1
    AND pg_get_indexdef(i.indexrelid,1,true) IN ('document_id','invoice_id')
);

-- Duplicate non-null keys cause an error and rollback, never data cleanup.
-- Rebuild these dedicated indexes to avoid trusting an unrelated same-name index.
INSERT INTO payment_upgrade_assert
SELECT 'recognized upgrade indexes', NOT EXISTS (
    SELECT 1 FROM pg_indexes WHERE schemaname='public'
    AND indexname IN ('ux_financial_payment_request','ix_financial_payment_document','ix_financial_payment_invoice')
    AND indexdef <> CASE indexname
        WHEN 'ux_financial_payment_request' THEN 'CREATE UNIQUE INDEX ux_financial_payment_request ON public.financial_transactions USING btree (company_id, request_key) WHERE (request_key IS NOT NULL)'
        WHEN 'ix_financial_payment_document' THEN 'CREATE INDEX ix_financial_payment_document ON public.financial_transactions USING btree (company_id, document_id)'
        ELSE 'CREATE INDEX ix_financial_payment_invoice ON public.financial_transactions USING btree (company_id, invoice_id)' END
);
DROP INDEX IF EXISTS public.ux_financial_payment_request;
CREATE UNIQUE INDEX ux_financial_payment_request
    ON public.financial_transactions(company_id,request_key) WHERE request_key IS NOT NULL;
DROP INDEX IF EXISTS public.ix_financial_payment_document;
CREATE INDEX ix_financial_payment_document ON public.financial_transactions(company_id,document_id);
DROP INDEX IF EXISTS public.ix_financial_payment_invoice;
CREATE INDEX ix_financial_payment_invoice ON public.financial_transactions(company_id,invoice_id);

INSERT INTO payment_upgrade_assert
SELECT 'all stored rows unchanged', NOT EXISTS (
    (SELECT row_value FROM payment_upgrade_before
     EXCEPT ALL SELECT to_jsonb(t) FROM public.financial_transactions t)
    UNION ALL
    (SELECT to_jsonb(t) FROM public.financial_transactions t
     EXCEPT ALL SELECT row_value FROM payment_upgrade_before)
);
COMMIT;
