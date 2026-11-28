-- CodeMastery synthetic User seed (SD01): dataset interim-v1 and its final-v1
-- extension (ADR013 §3, CT15-F-ADMIN-ACCOUNT-2026-11-15-r1).
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
--   seed_dataset                    CODEMASTERY_SEED_DATASET (optional)
-- With seed_dataset unset or interim-v1, the file loads exactly the interim-v1
-- Instructor and Learner, as before. With seed_dataset final-v1 it also loads
-- the four final-v1 accounts and then requires their hashes, one distinct hash
-- per account:
--   admin_password_hash             CODEMASTERY_SEED_ADMIN_PASSWORD_HASH
--   p1_password_hash                CODEMASTERY_SEED_P1_PASSWORD_HASH
--   p2_password_hash                CODEMASTERY_SEED_P2_PASSWORD_HASH
--   p3_password_hash                CODEMASTERY_SEED_P3_PASSWORD_HASH
-- A final-v1 hash supplied without seed_dataset final-v1 is rejected, so a
-- forgotten selector cannot silently load only the interim-v1 accounts.
--
-- final-v1 reuses the interim-v1 Instructor and Learner rows unchanged (V001's
-- unique email forbids a second row) and adds UUIDv5 values for
-- https://codemastery.test/final-v1/<logical-key>.
--
-- Identity handoff: 02-course-python-fundamentals.sql references the Instructor
-- 1eacfb14-e494-596c-bb53-3d012e7caf72 (logical key user/instructor) defined here;
-- 05-learner-profiles.sql references the final-v1 profile Learners user/p1-p3.

\set ON_ERROR_STOP on
-- Constraint-error DETAIL can include the complete row and its password hash.
\set VERBOSITY terse
\set ECHO none
\set ECHO_HIDDEN off

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
\if :{?seed_dataset}
\else
\getenv seed_dataset CODEMASTERY_SEED_DATASET
\endif
\if :{?admin_password_hash}
\else
\getenv admin_password_hash CODEMASTERY_SEED_ADMIN_PASSWORD_HASH
\endif
\if :{?p1_password_hash}
\else
\getenv p1_password_hash CODEMASTERY_SEED_P1_PASSWORD_HASH
\endif
\if :{?p2_password_hash}
\else
\getenv p2_password_hash CODEMASTERY_SEED_P2_PASSWORD_HASH
\endif
\if :{?p3_password_hash}
\else
\getenv p3_password_hash CODEMASTERY_SEED_P3_PASSWORD_HASH
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

-- Dataset selection. This literal comparison reads no table and takes no lock.
\if :{?seed_dataset}
\else
\set seed_dataset interim-v1
\endif
SELECT :'seed_dataset' IN ('interim-v1', 'final-v1') AS sd01_known_dataset,
       :'seed_dataset' = 'final-v1' AS sd01_final_dataset \gset
\if :sd01_known_dataset
\else
DO $sd01_input$
BEGIN
    RAISE EXCEPTION 'SD01 requires seed_dataset interim-v1 or final-v1.';
END;
$sd01_input$;
\endif
\if :sd01_final_dataset
\if :{?admin_password_hash}
\else
DO $sd01_input$
BEGIN
    RAISE EXCEPTION 'Required psql variable admin_password_hash is missing.';
END;
$sd01_input$;
\endif
\if :{?p1_password_hash}
\else
DO $sd01_input$
BEGIN
    RAISE EXCEPTION 'Required psql variable p1_password_hash is missing.';
END;
$sd01_input$;
\endif
\if :{?p2_password_hash}
\else
DO $sd01_input$
BEGIN
    RAISE EXCEPTION 'Required psql variable p2_password_hash is missing.';
END;
$sd01_input$;
\endif
\if :{?p3_password_hash}
\else
DO $sd01_input$
BEGIN
    RAISE EXCEPTION 'Required psql variable p3_password_hash is missing.';
END;
$sd01_input$;
\endif
\elif :{?admin_password_hash}
DO $sd01_input$
BEGIN
    RAISE EXCEPTION 'SD01 final-v1 password hashes require seed_dataset final-v1.';
END;
$sd01_input$;
\elif :{?p1_password_hash}
DO $sd01_input$
BEGIN
    RAISE EXCEPTION 'SD01 final-v1 password hashes require seed_dataset final-v1.';
END;
$sd01_input$;
\elif :{?p2_password_hash}
DO $sd01_input$
BEGIN
    RAISE EXCEPTION 'SD01 final-v1 password hashes require seed_dataset final-v1.';
END;
$sd01_input$;
\elif :{?p3_password_hash}
DO $sd01_input$
BEGIN
    RAISE EXCEPTION 'SD01 final-v1 password hashes require seed_dataset final-v1.';
END;
$sd01_input$;
\endif

-- READ COMMITTED regardless of the database default: the guard below runs the
-- transaction's first query, so under REPEATABLE READ or SERIALIZABLE a loader
-- that then waits on the lock would keep a snapshot taken before an earlier
-- loader committed, and would conflict instead of skipping its rows.
BEGIN ISOLATION LEVEL READ COMMITTED;

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

-- SD-F-IR-01: the validated selector overrides every inherited session/role/database GUC.
SET LOCAL codemastery.seed_dataset = :'seed_dataset';

-- SD-F-IR-06: credential-bearing statements run only after the disposable-target
-- guard and with transaction-local built-in server diagnostics suppressed. An
-- operator lacking permission fails here, before any hash reaches an SQL statement.
-- No database/role/global logging configuration changes; commit/rollback restores
-- every setting, including after a constraint failure. The client retains terse errors.
SET LOCAL log_min_messages = 'panic';
SET LOCAL log_statement = 'none';
SET LOCAL log_duration = off;
SET LOCAL log_min_duration_statement = -1;
SET LOCAL log_min_duration_sample = -1;
SET LOCAL log_transaction_sample_rate = 0;
SET LOCAL log_parameter_max_length = 0;
SET LOCAL log_parameter_max_length_on_error = 0;
SET LOCAL log_error_verbosity = 'terse';
SET LOCAL debug_print_parse = off;
SET LOCAL debug_print_rewritten = off;
SET LOCAL debug_print_plan = off;
SET LOCAL log_statement_stats = off;
SET LOCAL log_parser_stats = off;
SET LOCAL log_planner_stats = off;
SET LOCAL log_executor_stats = off;

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

\if :sd01_final_dataset
-- final-v1 accounts (ADR013 §3 and §4.2), UUIDv5 for https://codemastery.test/final-v1/<logical-key>.
INSERT INTO sd01_user_seed (id, email, password_hash, display_name, role, account_status)
VALUES
    -- user/admin
    ('db59a121-7599-5159-8372-99df0dd0702b', 'admin@example.test', :'admin_password_hash', 'Administrator Demo', 'admin', 'active'),
    -- user/p1
    ('f53df771-d0d4-535d-ba42-4179e90806c9', 'p1@example.test', :'p1_password_hash', 'Profile P1 Strong Foundation', 'learner', 'active'),
    -- user/p2
    ('f21a06a2-0ee9-50e9-8b38-c87275d3b6c6', 'p2@example.test', :'p2_password_hash', 'Profile P2 Loops Gap', 'learner', 'active'),
    -- user/p3
    ('e2b24374-aa01-58ea-bd0f-be1e11bd6a79', 'p3@example.test', :'p3_password_hash', 'Profile P3 Complete Beginner', 'learner', 'active');
\endif

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

    -- final-v1 requires a distinct password per account (ADR013 §3). BCrypt salts
    -- make equal passwords hash differently, so this catches one hash reused for
    -- several accounts; choosing distinct passwords remains the operator's duty.
    IF current_setting('codemastery.seed_dataset', true) IS NOT DISTINCT FROM 'final-v1'
       AND (SELECT count(DISTINCT password_hash) FROM sd01_user_seed) <> (SELECT count(*) FROM sd01_user_seed) THEN
        RAISE EXCEPTION 'SD01 final-v1 requires a distinct password hash per account';
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
