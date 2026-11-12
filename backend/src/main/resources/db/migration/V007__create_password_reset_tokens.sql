CREATE TABLE public.password_reset_tokens (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    token_digest bytea NOT NULL,
    issued_at timestamptz NOT NULL,
    expires_at timestamptz NOT NULL,
    CONSTRAINT pk_password_reset_tokens PRIMARY KEY (id),
    CONSTRAINT uq_password_reset_tokens_user UNIQUE (user_id),
    CONSTRAINT uq_password_reset_tokens_digest UNIQUE (token_digest),
    CONSTRAINT fk_password_reset_tokens_user FOREIGN KEY (user_id)
        REFERENCES public.users (id)
        ON UPDATE NO ACTION ON DELETE NO ACTION NOT DEFERRABLE,
    CONSTRAINT ck_password_reset_tokens_digest CHECK (octet_length(token_digest) = 32),
    CONSTRAINT ck_password_reset_tokens_expiry CHECK (expires_at > issued_at)
);

GRANT SELECT, INSERT, UPDATE, DELETE
    ON TABLE public.password_reset_tokens
    TO ${runtime-role};
