-- Add nullable VAT-invoice core fields without affecting existing invoice rows.
ALTER TABLE invoice_data
    ADD COLUMN IF NOT EXISTS seller_phone VARCHAR(100),
    ADD COLUMN IF NOT EXISTS payment_method VARCHAR(255),
    ADD COLUMN IF NOT EXISTS amount_in_words TEXT,
    ADD COLUMN IF NOT EXISTS tax_authority_code VARCHAR(255),
    ADD COLUMN IF NOT EXISTS sign_date DATE;
