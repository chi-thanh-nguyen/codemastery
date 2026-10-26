-- CodeMastery interim-v1 synthetic User seed.
--
-- Supply locally generated BCrypt $2b$14$ hashes from operator-supplied
-- synthetic passwords satisfying CT01. Do not put plaintext passwords or
-- generated hashes in this file or commit them. For repeat runs, supply the
-- same hashes so existing rows can be compared exactly.
-- Example invocation shape (replace placeholders locally; do not commit them):
-- psql -X -v ON_ERROR_STOP=1 \
--   -v instructor_password_hash='<local BCrypt hash>' \
--   -v learner_password_hash='<local BCrypt hash>' -f 01-users.sql

\if :{?instructor_password_hash}
\else
\echo 'Required psql variable instructor_password_hash is missing.'
\quit 3
\endif
\if :{?learner_password_hash}
\else
\echo 'Required psql variable learner_password_hash is missing.'
\quit 3
\endif

BEGIN;

CREATE TEMP TABLE sd01_user_seed (
    id uuid NOT NULL,
    email text NOT NULL,
    password_hash text NOT NULL,
    display_name text NOT NULL,
    role text NOT NULL,
    account_status text NOT NULL
) ON COMMIT DROP;

INSERT INTO sd01_user_seed (id, email, password_hash, display_name, role, account_status)
VALUES
    ('1eacfb14-e494-596c-bb53-3d012e7caf72', 'instructor@example.test', :'instructor_password_hash', 'Instructor Demo', 'instructor', 'active'),
    ('6ae6a0b2-7ef7-5500-a2ac-db04127da8fa', 'learner@example.test', :'learner_password_hash', 'Learner Demo', 'learner', 'active');

DO $sd01$
DECLARE
    expected_user record;
BEGIN
    IF EXISTS (
        SELECT 1
        FROM sd01_user_seed
        WHERE password_hash !~ '^[$]2b[$]14[$][./A-Za-z0-9]{53}$'
    ) THEN
        RAISE EXCEPTION 'SD01 requires valid BCrypt $2b$14$ password hashes';
    END IF;

    FOR expected_user IN SELECT * FROM sd01_user_seed ORDER BY id LOOP
        IF EXISTS (
            SELECT 1
            FROM public.users existing_user
            WHERE existing_user.id = expected_user.id
              AND existing_user.email = expected_user.email
              AND existing_user.password_hash = expected_user.password_hash
              AND existing_user.display_name = expected_user.display_name
              AND existing_user.role = expected_user.role
              AND existing_user.account_status = expected_user.account_status
        ) THEN
            CONTINUE;
        END IF;

        IF EXISTS (
            SELECT 1
            FROM public.users existing_user
            WHERE existing_user.id = expected_user.id
               OR existing_user.email = expected_user.email
        ) THEN
            RAISE EXCEPTION 'SD01 User seed conflicts with an existing ID or canonical email';
        END IF;

        INSERT INTO public.users (id, email, password_hash, display_name, role, account_status)
        VALUES (
            expected_user.id,
            expected_user.email,
            expected_user.password_hash,
            expected_user.display_name,
            expected_user.role,
            expected_user.account_status
        );
    END LOOP;
END
$sd01$;

COMMIT;
