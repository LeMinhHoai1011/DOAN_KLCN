BEGIN;

ALTER TABLE public.users
    ADD COLUMN token_version BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN row_version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE public.users
    ADD CONSTRAINT ck_users_token_version_nonnegative CHECK (token_version >= 0),
    ADD CONSTRAINT ck_users_row_version_nonnegative CHECK (row_version >= 0);

COMMIT;
