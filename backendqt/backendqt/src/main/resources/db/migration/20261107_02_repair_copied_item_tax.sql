-- Run this upgrade explicitly; do not replay historical migrations.
-- Repairs invoices where the header VAT was copied onto every row: row tax becomes amount × rate, rounded to whole dong.
-- Same rule as InvoiceItemTax. Touching updated_at marks the previous reconciliation as stale.
BEGIN;
SET LOCAL lock_timeout = '5s';
CREATE TEMP TABLE copied_tax ON COMMIT DROP AS
SELECT i.invoice_id
FROM public.invoice_items i JOIN public.invoice_data d ON d.invoice_id = i.invoice_id
WHERE d.tax_amount <> 0
GROUP BY i.invoice_id
HAVING count(*) > 1 AND count(i.tax_amount) = count(*)
   AND min(i.tax_amount) = max(i.tax_amount) AND min(i.tax_amount) = max(d.tax_amount);
UPDATE public.invoice_items i
SET tax_amount = round(i.total_amount * CASE WHEN abs(i.tax_rate) <= 1 THEN i.tax_rate ELSE i.tax_rate / 100 END)
FROM copied_tax c WHERE c.invoice_id = i.invoice_id;
UPDATE public.invoice_data d SET updated_at = now()
FROM copied_tax c WHERE c.invoice_id = d.invoice_id;
COMMIT;
