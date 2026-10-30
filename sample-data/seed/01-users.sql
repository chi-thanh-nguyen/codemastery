-- CodeMastery interim-v1 synthetic User seed (SD01).
--
-- Local/test-only: never run against a shared, staging or production database.
-- The target must be a disposable database attested as described in
-- sample-data/README.md; otherwise this script stops before any lock or write.
--
-- Supply locally generated BCrypt $2b$14$ hashes from operator-supplied
-- synthetic passwords satisfying CT01. Do not put plaintext passwords or
-- generated hashes in this file or commit them. For repeat runs, supply the
-- same hashes so existing rows can be compared exactly.
--
-- Inputs, as psql variables or, when a variable is unset, environment variables
-- (preferred: environment values do not appear in the process argument list):
--   seed_target_system_identifier   CODEMASTERY_SEED_TARGET_SYSTEM_IDENTIFIER
--   instructor_password_hash        CODEMASTERY_SEED_INSTRUCTOR_PASSWORD_HASH
--   learner_password_hash           CODEMASTERY_SEED_LEARNER_PASSWORD_HASH
--
-- Identity handoff: 02-course-python-fundamentals.sql references the Instructor
-- 1eacfb14-e494-596c-bb53-3d012e7caf72 (logical key user/instructor) defined here.

\set ON_ERROR_STOP on
-- Constraint-error DETAIL can include the complete row and its password hash.
\set VERBOSITY terse

\if :{?seed_target_system_identifier}
\else
\getenv seed_target_system_identifier CODEMASTERY_SEED_TARGET_SYSTEM_IDENTIFIER
\endif
\if :{?instructor_password_hash}
\else
\getenv instructor_password_hash CODEMASTERY_SEED_INSTRUCTOR_PASSWORD_HASH
\endif
\if :{?learner_password_hash}
\else
\getenv learner_password_hash CODEMASTERY_SEED_LEARNER_PASSWORD_HASH
\endif

\if :{?seed_target_system_identifier}
\else
DO $sd01_input$
BEGIN
    RAISE EXCEPTION 'Required input seed_target_system_identifier is missing.';
END;
$sd01_input$;
\endif
\if :{?instructor_password_hash}
\else
DO $sd01_input$
BEGIN
    RAISE EXCEPTION 'Required psql variable instructor_password_hash is missing.';
END;
$sd01_input$;
\endif
\if :{?learner_password_hash}
\else
DO $sd01_input$
BEGIN
    RAISE EXCEPTION 'Required psql variable learner_password_hash is missing.';
END;
$sd01_input$;
\endif

BEGIN;

-- Verify the target before taking any lock: the connected cluster must have the
-- independently obtained system identifier, and this database must carry the
-- catalog-level disposable-target attestation bound to that identifier.
SET LOCAL codemastery.seed_expected_target = :'seed_target_system_identifier';

DO $sd01_target$
DECLARE
    expected_identifier text := current_setting('codemastery.seed_expected_target');
    actual_identifier text;
    attestation text;
BEGIN
    IF expected_identifier !~ '^[1-9][0-9]{0,19}$' THEN
        RAISE EXCEPTION 'SD01 target verification failed: malformed expected system identifier';
    END IF;

    BEGIN
        SELECT system_identifier::text INTO actual_identifier FROM pg_control_system();
    EXCEPTION WHEN OTHERS THEN
        actual_identifier := NULL;
    END;
    IF actual_identifier IS NULL THEN
        RAISE EXCEPTION 'SD01 target verification failed: cluster identity is unavailable';
    END IF;
    IF actual_identifier <> expected_identifier THEN
        RAISE EXCEPTION 'SD01 target verification failed: connected cluster is not the expected target';
    END IF;

    -- Read the stored database setting, not current_setting(): a session or
    -- PGOPTIONS value must not be able to impersonate the attestation.
    SELECT substr(setting, length('codemastery.seed_target=') + 1)
    INTO attestation
    FROM pg_catalog.pg_db_role_setting stored
    CROSS JOIN LATERAL unnest(stored.setconfig) AS setting
    WHERE stored.setdatabase = (SELECT oid FROM pg_catalog.pg_database WHERE datname = current_database())
      AND stored.setrole = 0
      AND setting LIKE 'codemastery.seed\_target=%';
    IF attestation IS DISTINCT FROM 'disposable:' || actual_identifier THEN
        RAISE EXCEPTION 'SD01 target verification failed: database is not attested as a disposable seed target';
    END IF;
END
$sd01_target$;

-- Serialize loaders and other User writers before taking a data snapshot.
LOCK TABLE public.users IN SHARE ROW EXCLUSIVE MODE;

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
        WHERE password_hash !~ '^[$]2b[$]14[$][./A-Za-z0-9]{21}[.Oeu][./A-Za-z0-9]{30}[.CGKOSWaeimquy26]$'
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
