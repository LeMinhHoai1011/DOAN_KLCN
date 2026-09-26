BEGIN;

DO $$
DECLARE
    target RECORD;
    sequence_name TEXT;
    maximum_id BIGINT;
    sequence_value BIGINT;
    sequence_called BOOLEAN;
BEGIN
    IF to_regclass('public.accounting_categories') IS NOT NULL THEN
        ALTER TABLE accounting_categories
            ADD COLUMN IF NOT EXISTS active BOOLEAN NOT NULL DEFAULT TRUE;

        UPDATE accounting_categories
        SET active = TRUE
        WHERE active IS NULL;

        ALTER TABLE accounting_categories
            ALTER COLUMN active SET DEFAULT TRUE,
            ALTER COLUMN active SET NOT NULL;
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'ai_classifications'
          AND column_name = 'reason'
          AND data_type = 'oid'
    ) THEN
        ALTER TABLE ai_classifications
            ALTER COLUMN reason TYPE TEXT
            USING CASE
                WHEN reason IS NULL THEN NULL
                ELSE convert_from(lo_get(reason), 'UTF8')
            END;
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'ocr_results'
          AND column_name = 'raw_text'
          AND data_type = 'oid'
    ) THEN
        ALTER TABLE ocr_results
            ALTER COLUMN raw_text TYPE TEXT
            USING CASE
                WHEN raw_text IS NULL THEN NULL
                ELSE convert_from(lo_get(raw_text), 'UTF8')
            END;
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'users'
          AND column_name = 'role'
    ) THEN
        ALTER TABLE users
            ALTER COLUMN role DROP NOT NULL;
    END IF;

    FOR target IN
        SELECT table_name, column_name
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND is_identity = 'YES'
        ORDER BY table_name, ordinal_position
    LOOP
        sequence_name := pg_get_serial_sequence('public.' || target.table_name, target.column_name);
        IF sequence_name IS NOT NULL THEN
            EXECUTE format('SELECT MAX(%I)::BIGINT FROM public.%I', target.column_name, target.table_name)
                INTO maximum_id;
            IF maximum_id IS NOT NULL THEN
                EXECUTE format('SELECT last_value, is_called FROM %s', sequence_name)
                    INTO sequence_value, sequence_called;
                IF sequence_value < maximum_id OR (sequence_value = maximum_id AND NOT sequence_called) THEN
                    PERFORM setval(sequence_name::regclass, maximum_id, TRUE);
                END IF;
            END IF;
        END IF;
    END LOOP;
END;
$$;

COMMIT;