-- Unknown reconciliation operands are not monetary zero. Nullable amounts let
-- rule-level INSUFFICIENT_DATA avoid publishing a fabricated difference.
ALTER TABLE reconciliations ALTER COLUMN expected_amount DROP NOT NULL;
ALTER TABLE reconciliations ALTER COLUMN actual_amount DROP NOT NULL;
ALTER TABLE reconciliations ALTER COLUMN difference_amount DROP NOT NULL;
