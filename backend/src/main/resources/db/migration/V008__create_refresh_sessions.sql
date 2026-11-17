-- AUTH-SESSION-2026-11-16-r1 (ADR014 §4): Auth-owned refresh sessions and rotating
-- opaque refresh-token digests. Plaintext tokens are never stored. The application
-- supplies UUIDs and timestamps. Runtime privileges are provisioned by the CT17
-- matrix (CT17-P6-PRIVILEGES-2026-11-16-r3), never by migrations.

-- One row per signed-in browser session (rotation family). revoked_at ends the whole
-- family, including every access JWT bound to it through the `sid` claim.
CREATE TABLE public.refresh_sessions (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    created_at timestamptz NOT NULL,
    expires_at timestamptz NOT NULL,
    revoked_at timestamptz,
    CONSTRAINT pk_refresh_sessions PRIMARY KEY (id),
    CONSTRAINT fk_refresh_sessions_user FOREIGN KEY (user_id)
        REFERENCES public.users (id)
        ON UPDATE NO ACTION ON DELETE NO ACTION NOT DEFERRABLE,
    CONSTRAINT ck_refresh_sessions_expiry CHECK (expires_at > created_at),
    CONSTRAINT ck_refresh_sessions_revoked CHECK (revoked_at IS NULL OR revoked_at >= created_at)
);

CREATE INDEX ix_refresh_sessions_user ON public.refresh_sessions (user_id);
CREATE INDEX ix_refresh_sessions_expires ON public.refresh_sessions (expires_at);

-- One row per issued refresh token. A rotated token keeps its row (consumed_at set) for
-- replay detection until its session is purged after expiry.
CREATE TABLE public.refresh_tokens (
    id uuid NOT NULL,
    session_id uuid NOT NULL,
    token_digest bytea NOT NULL,
    issued_at timestamptz NOT NULL,
    expires_at timestamptz NOT NULL,
    consumed_at timestamptz,
    CONSTRAINT pk_refresh_tokens PRIMARY KEY (id),
    CONSTRAINT uq_refresh_tokens_digest UNIQUE (token_digest),
    CONSTRAINT fk_refresh_tokens_session FOREIGN KEY (session_id)
        REFERENCES public.refresh_sessions (id)
        ON UPDATE NO ACTION ON DELETE NO ACTION NOT DEFERRABLE,
    CONSTRAINT ck_refresh_tokens_digest CHECK (octet_length(token_digest) = 32),
    CONSTRAINT ck_refresh_tokens_expiry CHECK (expires_at > issued_at),
    CONSTRAINT ck_refresh_tokens_consumed CHECK (consumed_at IS NULL OR consumed_at >= issued_at)
);

CREATE INDEX ix_refresh_tokens_session ON public.refresh_tokens (session_id);
-- A session has at most one unconsumed (current) token: rotation can never fork a family.
CREATE UNIQUE INDEX uq_refresh_tokens_session_current
    ON public.refresh_tokens (session_id) WHERE consumed_at IS NULL;
