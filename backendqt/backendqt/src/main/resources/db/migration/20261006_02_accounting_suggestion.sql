-- Preserve the optional LLM account suggestion separately from the selected category.
ALTER TABLE ai_classifications
    ADD COLUMN IF NOT EXISTS accounting_account VARCHAR(100);
