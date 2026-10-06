-- Initial physical schema: V001. Applied migrations are immutable.
-- Application supplies UUIDs, timestamps, and lifecycle values; no seed data.

CREATE TABLE public.users (
    id uuid NOT NULL,
    email text NOT NULL,
    password_hash text NOT NULL,
    display_name text NOT NULL,
    role text NOT NULL,
    account_status text NOT NULL,
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uq_users_email UNIQUE (email) NOT DEFERRABLE,
    CONSTRAINT ck_users_text CHECK (email ~ '[^[:space:]]' AND password_hash ~ '[^[:space:]]' AND display_name ~ '[^[:space:]]'),
    CONSTRAINT ck_users_email_canonical CHECK (email <> '' AND email = lower(email COLLATE "C") AND email !~ '[[:space:]]' AND octet_length(email) = char_length(email)),
    CONSTRAINT ck_users_role CHECK (role IN ('learner', 'instructor', 'admin')),
    CONSTRAINT ck_users_account_status CHECK (account_status IN ('active', 'locked'))
);
