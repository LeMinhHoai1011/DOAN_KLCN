-- Run this upgrade explicitly; do not replay historical migrations.
-- Repairs AI rows whose unit holds a number because the real unit stayed glued to the product name.
-- Same rule as InvoiceItemUnit: a trailing word starting with a letter becomes the unit, otherwise the unit is cleared.
BEGIN;
SET LOCAL lock_timeout = '5s';
UPDATE public.invoice_items
SET unit = CASE WHEN item_name ~ '[[:space:]][^[:space:][:digit:][:punct:]][^[:space:]]{0,11}[[:space:]]*$'
                THEN substring(item_name from '([^[:space:]]+)[[:space:]]*$') END,
    item_name = CASE WHEN item_name ~ '[[:space:]][^[:space:][:digit:][:punct:]][^[:space:]]{0,11}[[:space:]]*$'
                     THEN regexp_replace(item_name, '[[:space:]]+[^[:space:]]+[[:space:]]*$', '') ELSE item_name END
WHERE unit ~ '^[[:space:]0-9.,]+$';
COMMIT;
