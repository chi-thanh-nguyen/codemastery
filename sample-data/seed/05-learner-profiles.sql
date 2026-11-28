-- CodeMastery final-v1 adaptive learner profile seed (SD05 within SD03, ADR013 §4.2 CT15.F-adaptive).
--
-- Local/test-only: never run against a shared, staging or production database.
-- The target must be a disposable database attested as described in
-- sample-data/README.md; otherwise this script stops before any lock or write.
-- Load first 01-users.sql and 02-course-python-fundamentals.sql with
-- seed_dataset final-v1: the three final-v1 profile Learners come from the SD01
-- extension (ADR013 §3) and course/python from SD02.
--
-- Input, as a psql variable or, when unset, an environment variable:
--   seed_target_system_identifier   CODEMASTERY_SEED_TARGET_SYSTEM_IDENTIFIER
--
-- IDs are UUIDv5 values in the standard URL namespace for the name
-- https://codemastery.test/final-v1/<logical-key>; each row names its key.
-- Each profile gets one active Enrollment in course/python and nothing else: its
-- mastery, progress and recommendations come only from taking the quizzes through
-- the application (ADR013 §5 scripts).

\set ON_ERROR_STOP on
\set VERBOSITY terse

\if :{?seed_target_system_identifier}
\else
\getenv seed_target_system_identifier CODEMASTERY_SEED_TARGET_SYSTEM_IDENTIFIER
\endif

\if :{?seed_target_system_identifier}
\else
DO $sd05_input$
BEGIN
    RAISE EXCEPTION 'Required input seed_target_system_identifier is missing.';
END;
$sd05_input$;
\endif

-- READ COMMITTED regardless of the database default, for the same reason as SD02:
-- a loader waiting on the lock must see rows another loader has committed.
BEGIN ISOLATION LEVEL READ COMMITTED;

-- Verify the target before taking any lock: the connected cluster must have the
-- independently obtained system identifier, and this database must carry the
-- catalog-level disposable-target attestation bound to that identifier.
SET LOCAL codemastery.seed_expected_target = :'seed_target_system_identifier';

DO $sd05_target$
DECLARE
    expected_identifier text := current_setting('codemastery.seed_expected_target');
    actual_identifier text;
    attestation text;
BEGIN
    IF expected_identifier !~ '^[1-9][0-9]{0,19}$' THEN
        RAISE EXCEPTION 'SD05 target verification failed: malformed expected system identifier';
    END IF;

    BEGIN
        SELECT system_identifier::text INTO actual_identifier FROM pg_control_system();
    EXCEPTION WHEN OTHERS THEN
        actual_identifier := NULL;
    END;
    IF actual_identifier IS NULL THEN
        RAISE EXCEPTION 'SD05 target verification failed: cluster identity is unavailable';
    END IF;
    IF actual_identifier <> expected_identifier THEN
        RAISE EXCEPTION 'SD05 target verification failed: connected cluster is not the expected target';
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
        RAISE EXCEPTION 'SD05 target verification failed: database is not attested as a disposable seed target';
    END IF;
END
$sd05_target$;

-- Keep the referenced Learners and Course stable, then serialize loaders and other
-- membership writers before taking a data snapshot. Lock order follows SD01/SD02.
LOCK TABLE public.users, public.courses IN SHARE MODE;
LOCK TABLE public.enrollments IN SHARE ROW EXCLUSIVE MODE;

CREATE TEMP TABLE sd05_profile (
    id uuid NOT NULL,
    email text NOT NULL,
    display_name text NOT NULL
) ON COMMIT DROP;

INSERT INTO sd05_profile (id, email, display_name)
VALUES
    -- user/p1
    ('f53df771-d0d4-535d-ba42-4179e90806c9', 'p1@example.test', 'Profile P1 Strong Foundation'),
    -- user/p2
    ('f21a06a2-0ee9-50e9-8b38-c87275d3b6c6', 'p2@example.test', 'Profile P2 Loops Gap'),
    -- user/p3
    ('e2b24374-aa01-58ea-bd0f-be1e11bd6a79', 'p3@example.test', 'Profile P3 Complete Beginner');

CREATE TEMP TABLE sd05_enrollment_seed (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    course_id uuid NOT NULL,
    status text NOT NULL,
    resume_lesson_id uuid NULL,
    last_visited_at timestamptz NULL
) ON COMMIT DROP;

INSERT INTO sd05_enrollment_seed (id, user_id, course_id, status, resume_lesson_id, last_visited_at)
VALUES
    -- user/p1/enrollment/course/python
    ('0b96aa07-017d-5502-a3d6-73ff19f613e2', 'f53df771-d0d4-535d-ba42-4179e90806c9', '68ea4540-e9da-5605-9054-28b29afa0992', 'active', NULL, NULL),
    -- user/p2/enrollment/course/python
    ('125c19e2-48d7-5b14-bce7-c96307023bc5', 'f21a06a2-0ee9-50e9-8b38-c87275d3b6c6', '68ea4540-e9da-5605-9054-28b29afa0992', 'active', NULL, NULL),
    -- user/p3/enrollment/course/python
    ('11078f84-b0ff-5518-ac66-91274349080e', 'e2b24374-aa01-58ea-bd0f-be1e11bd6a79', '68ea4540-e9da-5605-9054-28b29afa0992', 'active', NULL, NULL);

DO $sd05$
DECLARE
    expected_enrollment record;
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM public.courses WHERE id = '68ea4540-e9da-5605-9054-28b29afa0992' AND publication_status = 'published'
    ) THEN
        RAISE EXCEPTION 'SD05 requires the SD02 course/python Course; load 02-course-python-fundamentals.sql first';
    END IF;
    IF (SELECT count(*)
        FROM sd05_profile profile
        JOIN public.users account ON account.id = profile.id
         AND account.email = profile.email
         AND account.display_name = profile.display_name
         AND account.role = 'learner'
         AND account.account_status = 'active') <> 3 THEN
        RAISE EXCEPTION 'SD05 requires the three final-v1 profile Learners; load 01-users.sql with seed_dataset final-v1 first';
    END IF;

    FOR expected_enrollment IN SELECT * FROM sd05_enrollment_seed ORDER BY id LOOP
        IF EXISTS (
            SELECT 1
            FROM public.enrollments existing_enrollment
            WHERE existing_enrollment.id = expected_enrollment.id
              AND existing_enrollment.user_id = expected_enrollment.user_id
              AND existing_enrollment.course_id = expected_enrollment.course_id
              AND existing_enrollment.status = expected_enrollment.status
              AND existing_enrollment.resume_lesson_id IS NOT DISTINCT FROM expected_enrollment.resume_lesson_id
              AND existing_enrollment.last_visited_at IS NOT DISTINCT FROM expected_enrollment.last_visited_at
        ) THEN
            CONTINUE;
        END IF;

        IF EXISTS (
            SELECT 1
            FROM public.enrollments existing_enrollment
            WHERE existing_enrollment.id = expected_enrollment.id
               OR (existing_enrollment.user_id = expected_enrollment.user_id
                   AND existing_enrollment.course_id = expected_enrollment.course_id)
        ) THEN
            RAISE EXCEPTION 'SD05 Enrollment % conflicts with an existing Enrollment ID or membership', expected_enrollment.id;
        END IF;

        INSERT INTO public.enrollments (id, user_id, course_id, status, resume_lesson_id, last_visited_at)
        VALUES (
            expected_enrollment.id,
            expected_enrollment.user_id,
            expected_enrollment.course_id,
            expected_enrollment.status,
            expected_enrollment.resume_lesson_id,
            expected_enrollment.last_visited_at
        );
    END LOOP;
END
$sd05$;

COMMIT;
