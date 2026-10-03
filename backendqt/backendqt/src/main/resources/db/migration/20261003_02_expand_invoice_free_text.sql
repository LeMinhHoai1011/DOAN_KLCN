-- Preserve real-world invoice free text without weakening bounded identifiers.
-- PostgreSQL casts VARCHAR to TEXT in place; existing rows and constraints remain intact.
ALTER TABLE invoice_data
    ALTER COLUMN seller_name TYPE TEXT,
    ALTER COLUMN seller_address TYPE TEXT,
    ALTER COLUMN buyer_name TYPE TEXT,
    ALTER COLUMN buyer_address TYPE TEXT;

ALTER TABLE invoice_items
    ALTER COLUMN item_name TYPE TEXT,
    ALTER COLUMN unit TYPE VARCHAR(100);
