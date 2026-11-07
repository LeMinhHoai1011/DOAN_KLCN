-- Run this upgrade explicitly; do not replay historical migrations.
-- Repairs rows whose quantity or unit price is 1000 times too large (same rule as InvoiceItemScale).
-- Approved documents are skipped: they are corrected by reopening them, not by a script.
BEGIN;
SET LOCAL lock_timeout = '5s';
CREATE TEMP TABLE scaled_rows ON COMMIT DROP AS
SELECT i.item_id, i.invoice_id
FROM public.invoice_items i
JOIN public.invoice_data d ON d.invoice_id = i.invoice_id
JOIN public.documents doc ON doc.document_id = d.document_id
WHERE doc.review_status <> 'APPROVED'
  AND i.total_amount <> 0 AND i.quantity <> 0 AND i.unit_price <> 0
  AND abs(round(i.quantity * i.unit_price / 1000) - i.total_amount) < 1
  AND (mod(i.quantity, 1000) = 0) <> (mod(i.unit_price, 1000) = 0);
UPDATE public.invoice_items i
SET quantity = CASE WHEN mod(i.quantity, 1000) = 0 THEN i.quantity / 1000 ELSE i.quantity END,
    unit_price = CASE WHEN mod(i.unit_price, 1000) = 0 THEN i.unit_price / 1000 ELSE i.unit_price END
FROM scaled_rows s WHERE s.item_id = i.item_id;
UPDATE public.invoice_data d SET updated_at = now()
FROM scaled_rows s WHERE s.invoice_id = d.invoice_id;
COMMIT;
