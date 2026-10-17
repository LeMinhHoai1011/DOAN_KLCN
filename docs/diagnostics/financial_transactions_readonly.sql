-- SELECT ONLY. No migration, no DDL/DML. Use a connection with default_transaction_read_only=on.
SELECT current_database(),current_schema(),inet_server_addr(),inet_server_port(),current_setting('server_version'),current_setting('TimeZone'),current_setting('transaction_read_only');
SELECT column_name,data_type,is_nullable,character_maximum_length,numeric_precision,numeric_scale,is_identity,column_default
FROM information_schema.columns WHERE table_schema='public' AND table_name='financial_transactions' ORDER BY ordinal_position;
SELECT conname,contype,pg_get_constraintdef(oid) FROM pg_constraint WHERE conrelid='public.financial_transactions'::regclass ORDER BY conname;
SELECT indexname,indexdef FROM pg_indexes WHERE schemaname='public' AND tablename='financial_transactions';
SELECT payment_confirmation,status,company_id,count(*) FROM public.financial_transactions GROUP BY 1,2,3 ORDER BY 1 NULLS FIRST,2,3;
-- Business calendar dates: inclusive, no timestamp/UTC conversion.
SELECT transaction_date,transaction_type,payment_confirmation,status,count(*),sum(amount)
FROM public.financial_transactions WHERE company_id=13 AND transaction_date BETWEEN DATE '2026-10-01' AND DATE '2026-10-17' GROUP BY 1,2,3,4 ORDER BY 1;
SELECT transaction_date,transaction_type,payment_confirmation,status,count(*),sum(amount)
FROM public.financial_transactions WHERE company_id=13 AND transaction_date=DATE '2026-10-17' GROUP BY 1,2,3,4;
SELECT count(*) FILTER (WHERE payment_confirmation IS NOT NULL AND (request_key IS NULL OR length(request_key)<>36 OR request_fingerprint IS NULL OR request_fingerprint !~ '^[0-9a-f]{64}$')) AS incomplete_provenance,
count(*) FILTER (WHERE payment_confirmation IS NULL AND (request_key IS NOT NULL OR request_fingerprint IS NOT NULL)) AS unexpected_legacy_request FROM public.financial_transactions;
-- The following must return zero rows; verifies all three mapped columns exist without reading values.
SELECT payment_confirmation,request_key,request_fingerprint FROM public.financial_transactions WHERE false;
