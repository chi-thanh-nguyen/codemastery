-- CodeMastery final-v1 questions, quizzes and assignments seed (SD04, ADR013 §§4.3, 19.6 CT15.F-assessment).
--
-- Local/test-only: never run against a shared, staging or production database.
-- The target must be a disposable database attested as described in
-- sample-data/README.md; otherwise this script stops before any lock or write.
-- Load first 01-users.sql and 02-course-python-fundamentals.sql with
-- seed_dataset final-v1, and 03-skill-graph-and-lesson-mapping.sql: every row
-- belongs to course/python, its skills S01-S20 or its Lessons L10 and L20.
--
-- Inputs, as psql variables or, when unset, environment variables:
--   seed_target_system_identifier   CODEMASTERY_SEED_TARGET_SYSTEM_IDENTIFIER
--   assignment_loops_deadline       CODEMASTERY_SEED_ASSIGNMENT_LOOPS_DEADLINE
--   assignment_files_deadline       CODEMASTERY_SEED_ASSIGNMENT_FILES_DEADLINE
-- Both deadlines must be ISO-8601/RFC-3339 timestamps with an explicit timezone
-- offset (e.g. 2026-12-31T23:59:00Z), representable in years 0000-9999 with at
-- most microsecond precision, and strictly in the future at load time.
--
-- IDs are UUIDv5 values in the standard URL namespace for the name
-- https://codemastery.test/final-v1/<logical-key>; each row names its key.
-- 120 Questions (6 per skill: 2 easy, 2 medium, 2 hard), 82 published Quizzes
-- (placement, 20 per-skill mixed practice, 60 per-difficulty practice, final-test)
-- and 2 published Assignments (L10, L20). No submission, attempt or grade row is seeded.

\set ON_ERROR_STOP on
\set VERBOSITY terse

\if :{?seed_target_system_identifier}
\else
\getenv seed_target_system_identifier CODEMASTERY_SEED_TARGET_SYSTEM_IDENTIFIER
\endif
\if :{?assignment_loops_deadline}
\else
\getenv assignment_loops_deadline CODEMASTERY_SEED_ASSIGNMENT_LOOPS_DEADLINE
\endif
\if :{?assignment_files_deadline}
\else
\getenv assignment_files_deadline CODEMASTERY_SEED_ASSIGNMENT_FILES_DEADLINE
\endif

\if :{?seed_target_system_identifier}
\else
DO $sd04_input$
BEGIN
    RAISE EXCEPTION 'Required input seed_target_system_identifier is missing.';
END;
$sd04_input$;
\endif

\if :{?assignment_loops_deadline}
\else
DO $sd04_input$
BEGIN
    RAISE EXCEPTION 'Required input assignment_loops_deadline is missing.';
END;
$sd04_input$;
\endif

\if :{?assignment_files_deadline}
\else
DO $sd04_input$
BEGIN
    RAISE EXCEPTION 'Required input assignment_files_deadline is missing.';
END;
$sd04_input$;
\endif

-- READ COMMITTED regardless of the database default, for the same reason as SD02/SD03:
-- a loader waiting on the lock must see rows another loader has committed.
BEGIN ISOLATION LEVEL READ COMMITTED;

-- Verify the target and load-time deadline inputs before taking any lock: the
-- connected cluster must have the independently obtained system identifier, and this
-- database must carry the catalog-level disposable-target attestation bound to it.
SET LOCAL codemastery.seed_expected_target = :'seed_target_system_identifier';
SET LOCAL codemastery.seed_assignment_loops_deadline = :'assignment_loops_deadline';
SET LOCAL codemastery.seed_assignment_files_deadline = :'assignment_files_deadline';

DO $sd04_target$
DECLARE
    expected_identifier text := current_setting('codemastery.seed_expected_target');
    loops_deadline_raw text := current_setting('codemastery.seed_assignment_loops_deadline');
    files_deadline_raw text := current_setting('codemastery.seed_assignment_files_deadline');
    loops_deadline timestamptz;
    files_deadline timestamptz;
    actual_identifier text;
    attestation text;
    ts_pattern constant text := '^[0-9]{4}-[0-9]{2}-[0-9]{2}[Tt ][0-9]{2}:[0-9]{2}:[0-9]{2}(\.[0-9]{1,6})?([Zz]|[+-][0-9]{2}:[0-9]{2})$';
BEGIN
    IF expected_identifier !~ '^[1-9][0-9]{0,19}$' THEN
        RAISE EXCEPTION 'SD04 target verification failed: malformed expected system identifier';
    END IF;

    IF loops_deadline_raw !~ ts_pattern OR files_deadline_raw !~ ts_pattern THEN
        RAISE EXCEPTION 'SD04 requires ISO-8601 assignment deadlines with an explicit timezone offset';
    END IF;

    BEGIN
        loops_deadline := loops_deadline_raw::timestamptz;
        files_deadline := files_deadline_raw::timestamptz;
    EXCEPTION WHEN OTHERS THEN
        RAISE EXCEPTION 'SD04 assignment deadline is not a valid timestamp';
    END;

    IF loops_deadline > '9999-12-31T23:59:59.999999Z'::timestamptz
       OR files_deadline > '9999-12-31T23:59:59.999999Z'::timestamptz THEN
        RAISE EXCEPTION 'SD04 assignment deadline exceeds year 9999 representable limit';
    END IF;

    IF loops_deadline <= clock_timestamp() OR files_deadline <= clock_timestamp() THEN
        RAISE EXCEPTION 'SD04 requires both assignment deadlines to be in the future at load time';
    END IF;

    BEGIN
        SELECT system_identifier::text INTO actual_identifier FROM pg_control_system();
    EXCEPTION WHEN OTHERS THEN
        actual_identifier := NULL;
    END;
    IF actual_identifier IS NULL THEN
        RAISE EXCEPTION 'SD04 target verification failed: cluster identity is unavailable';
    END IF;
    IF actual_identifier <> expected_identifier THEN
        RAISE EXCEPTION 'SD04 target verification failed: connected cluster is not the expected target';
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
        RAISE EXCEPTION 'SD04 target verification failed: database is not attested as a disposable seed target';
    END IF;
END
$sd04_target$;

-- Keep course/python, its structure and its skills stable, then serialize Assessment
-- loaders/writers before taking a data snapshot. Lock order follows SD02/SD03 and V004.
LOCK TABLE public.courses, public.chapters, public.lessons, public.skills IN SHARE MODE;
LOCK TABLE public.quizzes, public.questions, public.quiz_questions, public.assignments IN SHARE ROW EXCLUSIVE MODE;

CREATE TEMP TABLE sd04_skill_ref (
    code text NOT NULL,
    skill_id uuid NOT NULL,
    name text NOT NULL
) ON COMMIT DROP;

INSERT INTO sd04_skill_ref (code, skill_id, name)
VALUES
    ('S01', '823af82e-e876-5225-92f7-7d71587744b1', 'Program Output'),
    ('S02', '625bb9f0-01f3-55fc-861b-162fbfc450dd', 'Variables and Assignment'),
    ('S03', '2ce5c887-3d69-507c-b26d-6e255d807beb', 'Numbers and Arithmetic'),
    ('S04', '8de90879-28ff-5d76-af55-117ffac52d1d', 'Strings'),
    ('S05', 'f0f3a655-bbd0-53b1-acb7-9cf82d81735a', 'Input and Type Conversion'),
    ('S06', '528661dc-7ffc-5b1a-bb5b-9adc8e3cb7ff', 'Booleans and Comparisons'),
    ('S07', '0ec304d6-e92a-532e-b5dd-ce660ab5a825', 'Conditionals'),
    ('S08', 'ce75d29b-3c4f-5951-9180-b9fdc08f289f', 'While Loops'),
    ('S09', '1761144d-4d90-5570-b8b5-adcc3f9f413b', 'For Loops'),
    ('S10', '7cabeda5-0c6c-5c73-8a67-967fd84bce45', 'Loop Control'),
    ('S11', 'a9d43b35-426f-5500-9122-1043ddfa7977', 'Lists'),
    ('S12', 'f26a0234-bcec-5ba6-abc3-66ecbefd415f', 'List Iteration Patterns'),
    ('S13', '18869e1c-168a-585d-a1b1-9c97c12383d1', 'Tuples and Sets'),
    ('S14', '37c01bbf-2268-5b7a-adae-6d4bebcdc504', 'Dictionaries'),
    ('S15', '4604bdb9-d7ac-5877-9fa0-5610ab6c64eb', 'Functions'),
    ('S16', 'da6797cf-4a5c-5e29-ad28-0a68292ed9c3', 'Parameters and Return Values'),
    ('S17', '807cc7ab-d993-5633-b93c-cc2896724d1f', 'Variable Scope'),
    ('S18', '23dec971-6865-59ab-b55b-24ccd3bda430', 'String Methods and Formatting'),
    ('S19', '41a0f9ce-a2e0-5243-bdb4-a8cd5088e1f7', 'Exceptions'),
    ('S20', '09b1a1bd-bef5-594c-afdf-ca43f0a01ab8', 'File Input and Output');

CREATE TEMP TABLE sd04_question_seed (
    id uuid NOT NULL,
    skill_id uuid NOT NULL,
    prompt text NOT NULL,
    kind text NOT NULL,
    difficulty text NOT NULL,
    options text[] NOT NULL,
    correct_options boolean[] NOT NULL
) ON COMMIT DROP;

INSERT INTO sd04_question_seed (id, skill_id, prompt, kind, difficulty, options, correct_options)
VALUES
    -- course/python/skill/S01/question/easy/1
    ('9c3ac0be-d86d-5271-b27d-a07149066035', '823af82e-e876-5225-92f7-7d71587744b1', 'What does print("Score", 10, "points") display?', 'single_choice', 'easy', ARRAY['Score 10 points', 'Score10points', 'Score, 10, points', '("Score", 10, "points")']::text[], ARRAY[true, false, false, false]::boolean[]),
    -- course/python/skill/S01/question/easy/2
    ('d50e0753-9528-58cc-98cd-b0b971ca31d9', '823af82e-e876-5225-92f7-7d71587744b1', 'In Python, print("3 + 4") displays 7.', 'true_false', 'easy', ARRAY['True', 'False']::text[], ARRAY[false, true]::boolean[]),
    -- course/python/skill/S01/question/medium/1
    ('b358748e-fafe-58fe-b2df-d9871c4f6d3f', '823af82e-e876-5225-92f7-7d71587744b1', 'Which statements display hello on screen without raising an error?', 'multiple_choice', 'medium', ARRAY['print("hello")', 'print(''hello'')', 'PRINT("hello")', 'print(hello)']::text[], ARRAY[true, true, false, false]::boolean[]),
    -- course/python/skill/S01/question/medium/2
    ('f1e8f1bc-ce65-5101-a6e9-438b31892750', '823af82e-e876-5225-92f7-7d71587744b1', 'What does print(3 + 4) display?', 'single_choice', 'medium', ARRAY['7', '3 + 4', '"7"', '34']::text[], ARRAY[true, false, false, false]::boolean[]),
    -- course/python/skill/S01/question/hard/1
    ('53b89cbd-52d7-5d3b-8a46-5644e2362274', '823af82e-e876-5225-92f7-7d71587744b1', 'How many lines of output do two consecutive print("A") and print("B") calls produce?', 'single_choice', 'hard', ARRAY['2 lines: A then B', '1 line: A B', '1 line: AB', '0 lines unless flushed']::text[], ARRAY[true, false, false, false]::boolean[]),
    -- course/python/skill/S01/question/hard/2
    ('b47b5206-4b65-5291-8d27-16505cb90268', '823af82e-e876-5225-92f7-7d71587744b1', 'Which statements hold about Python''s print function?', 'multiple_choice', 'hard', ARRAY['By default each call ends with a newline.', 'Multiple comma-separated arguments are separated by a single space by default.', 'Numbers passed to print must always be wrapped in str().', 'print automatically writes output to a disk file.']::text[], ARRAY[true, true, false, false]::boolean[]),
    -- course/python/skill/S02/question/easy/1
    ('bf18cc06-62d6-57a9-b1e6-f3606927bfb3', '625bb9f0-01f3-55fc-861b-162fbfc450dd', 'In Python, the = operator checks whether two values are equal.', 'true_false', 'easy', ARRAY['True', 'False']::text[], ARRAY[false, true]::boolean[]),
    -- course/python/skill/S02/question/easy/2
    ('1028cb3f-1a77-5749-8fa0-c748d4ba12a5', '625bb9f0-01f3-55fc-861b-162fbfc450dd', 'Which variable name is invalid in Python?', 'single_choice', 'easy', ARRAY['2nd_score', 'second_score', '_score', 'score2']::text[], ARRAY[true, false, false, false]::boolean[]),
    -- course/python/skill/S02/question/medium/1
    ('289c2492-5d9d-5c7f-91d9-4f5aaa71e0d0', '625bb9f0-01f3-55fc-861b-162fbfc450dd', 'After a = 2, b = a, and then a = 5, what value does b refer to?', 'single_choice', 'medium', ARRAY['2', '5', '7', 'a']::text[], ARRAY[true, false, false, false]::boolean[]),
    -- course/python/skill/S02/question/medium/2
    ('d120a564-84e4-543a-9f8e-d1e190f80b7b', '625bb9f0-01f3-55fc-861b-162fbfc450dd', 'Which lines create or update a variable named total without syntax errors?', 'multiple_choice', 'medium', ARRAY['total = 0', 'total = total + 5', '5 = total', 'total_count = 10']::text[], ARRAY[true, true, false, true]::boolean[]),
    -- course/python/skill/S02/question/hard/1
    ('d2eedd97-a799-5f1c-b2cb-39edee8c6b0e', '625bb9f0-01f3-55fc-861b-162fbfc450dd', 'Which statements accurately describe variable assignment in Python?', 'multiple_choice', 'hard', ARRAY['Variable names are case-sensitive.', 'The right-hand side is evaluated before updating the name on the left.', 'Reassigning x changes variables that were earlier copied from x.', 'Names may include underscores and digits after the first character.']::text[], ARRAY[true, true, false, true]::boolean[]),
    -- course/python/skill/S02/question/hard/2
    ('f0b39c73-70e6-5571-9727-75b5e626d0dd', '625bb9f0-01f3-55fc-861b-162fbfc450dd', 'What error occurs when you evaluate print(score) before score has been assigned a value?', 'single_choice', 'hard', ARRAY['NameError', 'SyntaxError', 'TypeError', 'ValueError']::text[], ARRAY[true, false, false, false]::boolean[]),
    -- course/python/skill/S03/question/easy/1
    ('a0aa03ca-5c53-5d68-bca3-3db07691f501', '2ce5c887-3d69-507c-b26d-6e255d807beb', 'What is the value of 7 // 2 in Python?', 'single_choice', 'easy', ARRAY['3', '3.5', '1', '4']::text[], ARRAY[true, false, false, false]::boolean[]),
    -- course/python/skill/S03/question/easy/2
    ('f0f24b93-984f-5876-89f8-835c049bb8ac', '2ce5c887-3d69-507c-b26d-6e255d807beb', 'In Python, 8 / 2 produces the float 4.0 rather than the int 4.', 'true_false', 'easy', ARRAY['True', 'False']::text[], ARRAY[true, false]::boolean[]),
    -- course/python/skill/S03/question/medium/1
    ('d7438085-a880-5520-abaf-97e51e6351b4', '2ce5c887-3d69-507c-b26d-6e255d807beb', 'Which expressions evaluate to 14?', 'multiple_choice', 'medium', ARRAY['2 + 3 * 4', '(2 + 3) * 4', '7 * 2', '2 ** 4 - 2']::text[], ARRAY[true, false, true, true]::boolean[]),
    -- course/python/skill/S03/question/medium/2
    ('4b09d392-34fe-5e09-a893-45cf07d8a3d7', '2ce5c887-3d69-507c-b26d-6e255d807beb', 'What is the remainder 45 % 7?', 'single_choice', 'medium', ARRAY['3', '6', '1', '7']::text[], ARRAY[true, false, false, false]::boolean[]),
    -- course/python/skill/S03/question/hard/1
    ('9295bb15-c299-5795-bce7-ea13648f734b', '2ce5c887-3d69-507c-b26d-6e255d807beb', 'What does 2 ** 3 * 2 evaluate to?', 'single_choice', 'hard', ARRAY['16', '64', '12', '8']::text[], ARRAY[true, false, false, false]::boolean[]),
    -- course/python/skill/S03/question/hard/2
    ('226975a1-55e0-5bb7-bffe-ab4ccdaaa20f', '2ce5c887-3d69-507c-b26d-6e255d807beb', 'Which expressions evaluate to an int (not a float) in Python 3?', 'multiple_choice', 'hard', ARRAY['10 // 4', '10 % 4', '10 / 2', '2 ** 5']::text[], ARRAY[true, true, false, true]::boolean[]),
    -- course/python/skill/S04/question/easy/1
    ('5902205d-e312-5812-99c7-c15ffd1b4722', '8de90879-28ff-5d76-af55-117ffac52d1d', 'Python strings can be modified in place by assigning text[0] = "X".', 'true_false', 'easy', ARRAY['True', 'False']::text[], ARRAY[false, true]::boolean[]),
    -- course/python/skill/S04/question/easy/2
    ('5344ddbb-7770-5332-8e0d-2961fb7d30e9', '8de90879-28ff-5d76-af55-117ffac52d1d', 'What is "code"[1:3]?', 'single_choice', 'easy', ARRAY['"od"', '"co"', '"ode"', '"de"']::text[], ARRAY[true, false, false, false]::boolean[]),
    -- course/python/skill/S04/question/medium/1
    ('1cd39950-3e19-53b4-a540-15001521ef4f', '8de90879-28ff-5d76-af55-117ffac52d1d', 'Given name = "Ada", what does name[-1] return?', 'single_choice', 'medium', ARRAY['"a"', '"A"', '"d"', '""']::text[], ARRAY[true, false, false, false]::boolean[]),
    -- course/python/skill/S04/question/medium/2
    ('8972206b-e768-5f13-9a02-ac5edd2fecf5', '8de90879-28ff-5d76-af55-117ffac52d1d', 'Which expressions produce the string "HiHi"?', 'multiple_choice', 'medium', ARRAY['"Hi" * 2', '"Hi" + "Hi"', '2 * "Hi"', '"Hi" + 2']::text[], ARRAY[true, true, true, false]::boolean[]),
    -- course/python/skill/S04/question/hard/1
    ('46085bea-1eb5-5c3b-bbee-4b2653c3bf14', '8de90879-28ff-5d76-af55-117ffac52d1d', 'Given word = "Python", which expressions evaluate without an IndexError?', 'multiple_choice', 'hard', ARRAY['word[-6]', 'word[0:100]', 'word[6]', 'word[2:2]']::text[], ARRAY[true, true, false, true]::boolean[]),
    -- course/python/skill/S04/question/hard/2
    ('cbc8f9c2-c540-5511-b46c-841684a2cf84', '8de90879-28ff-5d76-af55-117ffac52d1d', 'What does len("Ada Lovelace") return?', 'single_choice', 'hard', ARRAY['12', '11', '10', '2']::text[], ARRAY[true, false, false, false]::boolean[]),
    -- course/python/skill/S05/question/easy/1
    ('66f85ef9-185a-505e-ba77-6171a39a5b46', 'f0f3a655-bbd0-53b1-acb7-9cf82d81735a', 'What data type does input() always return?', 'single_choice', 'easy', ARRAY['str', 'int', 'float', 'bool']::text[], ARRAY[true, false, false, false]::boolean[]),
    -- course/python/skill/S05/question/easy/2
    ('19789354-82db-568c-b1ba-155e5ac72e85', 'f0f3a655-bbd0-53b1-acb7-9cf82d81735a', 'If a user types 5, the expression input() + "1" evaluates to "51".', 'true_false', 'easy', ARRAY['True', 'False']::text[], ARRAY[true, false]::boolean[]),
    -- course/python/skill/S05/question/medium/1
    ('0a113214-8bb3-500f-8580-5919e14691a2', 'f0f3a655-bbd0-53b1-acb7-9cf82d81735a', 'Which conversions succeed without raising ValueError?', 'multiple_choice', 'medium', ARRAY['int("42")', 'float("1.75")', 'str(42)', 'int("forty")']::text[], ARRAY[true, true, true, false]::boolean[]),
    -- course/python/skill/S05/question/medium/2
    ('c875e997-f3b3-5a83-93d3-a5c33daa4a00', 'f0f3a655-bbd0-53b1-acb7-9cf82d81735a', 'What is the result of int("8") + int("2")?', 'single_choice', 'medium', ARRAY['10', '"82"', '"10"', '8.2']::text[], ARRAY[true, false, false, false]::boolean[]),
    -- course/python/skill/S05/question/hard/1
    ('48e9b47e-c2aa-53f5-938d-276c6b805f9e', 'f0f3a655-bbd0-53b1-acb7-9cf82d81735a', 'Which error is raised by int("3.5")?', 'single_choice', 'hard', ARRAY['ValueError', 'TypeError', 'NameError', 'ZeroDivisionError']::text[], ARRAY[true, false, false, false]::boolean[]),
    -- course/python/skill/S05/question/hard/2
    ('e2450614-7fe8-5e0b-a571-0bdfee838601', 'f0f3a655-bbd0-53b1-acb7-9cf82d81735a', 'Which expressions convert a numeric variable n = 5 so it can be joined to text with +?', 'multiple_choice', 'hard', ARRAY['"Count: " + str(n)', 'str(n) + " items"', '"Count: " + n', '"Count: " + int(n)']::text[], ARRAY[true, true, false, false]::boolean[]),
    -- course/python/skill/S06/question/easy/1
    ('c31da348-74a2-5c9a-9731-a44cf67887e6', '528661dc-7ffc-5b1a-bb5b-9adc8e3cb7ff', 'The expression a and b is True whenever at least one of a or b is True.', 'true_false', 'easy', ARRAY['True', 'False']::text[], ARRAY[false, true]::boolean[]),
    -- course/python/skill/S06/question/easy/2
    ('7fcc92f1-63dd-506e-a38f-003ff8f23179', '528661dc-7ffc-5b1a-bb5b-9adc8e3cb7ff', 'With score = 72, what is score >= 50 and score < 70?', 'single_choice', 'easy', ARRAY['False', 'True', '72', 'None']::text[], ARRAY[true, false, false, false]::boolean[]),
    -- course/python/skill/S06/question/medium/1
    ('2b405b34-5384-53d4-a212-2be28a38740f', '528661dc-7ffc-5b1a-bb5b-9adc8e3cb7ff', 'What does not (3 > 5) evaluate to?', 'single_choice', 'medium', ARRAY['True', 'False', '2', 'None']::text[], ARRAY[true, false, false, false]::boolean[]),
    -- course/python/skill/S06/question/medium/2
    ('c4d632e1-f6ed-5509-8844-0967e9f6397e', '528661dc-7ffc-5b1a-bb5b-9adc8e3cb7ff', 'With day = "Sat", which Boolean expressions evaluate to True?', 'multiple_choice', 'medium', ARRAY['day == "Sat" or day == "Sun"', 'day != "Mon"', 'not (day == "Sun")', 'day == "Sat" and day == "Sun"']::text[], ARRAY[true, true, true, false]::boolean[]),
    -- course/python/skill/S06/question/hard/1
    ('322fb12c-c7bf-522c-ac4d-2a333f606173', '528661dc-7ffc-5b1a-bb5b-9adc8e3cb7ff', 'Which expressions evaluate to True?', 'multiple_choice', 'hard', ARRAY['True or False and False', '(True or False) and False', 'not False and 4 == 4', '3 != 3 or 2 >= 2']::text[], ARRAY[true, false, true, true]::boolean[]),
    -- course/python/skill/S06/question/hard/2
    ('6019ed9f-42b0-53f2-a367-7e0e6d2e7c11', '528661dc-7ffc-5b1a-bb5b-9adc8e3cb7ff', 'With x = 10, what does 5 < x <= 10 evaluate to?', 'single_choice', 'hard', ARRAY['True', 'False', 'SyntaxError', 'TypeError']::text[], ARRAY[true, false, false, false]::boolean[]),
    -- course/python/skill/S07/question/easy/1
    ('c0730e76-14b4-5000-bd79-240f4f7f3b0d', '0ec304d6-e92a-532e-b5dd-ce660ab5a825', 'In an if / elif / else chain, how many branches can execute on one run?', 'single_choice', 'easy', ARRAY['At most one', 'All branches whose conditions are True', 'Always two', 'Every branch after the first True']::text[], ARRAY[true, false, false, false]::boolean[]),
    -- course/python/skill/S07/question/easy/2
    ('e21daba6-38f8-5d78-b9d5-d006fab32a7f', '0ec304d6-e92a-532e-b5dd-ce660ab5a825', 'Every statement inside an if block must share consistent indentation.', 'true_false', 'easy', ARRAY['True', 'False']::text[], ARRAY[true, false]::boolean[]),
    -- course/python/skill/S07/question/medium/1
    ('66460d0a-e09e-5783-8377-79ef8093ccc3', '0ec304d6-e92a-532e-b5dd-ce660ab5a825', 'With score = 85, what happens in: if score >= 80: grade = ''A'' / elif score >= 50: grade = ''Pass'' / else: grade = ''Retry''?', 'multiple_choice', 'medium', ARRAY['grade is set to ''A''.', 'The elif condition is not checked because if matched.', 'grade is overwritten with ''Pass''.', 'else runs only when both if and elif are False.']::text[], ARRAY[true, true, false, true]::boolean[]),
    -- course/python/skill/S07/question/medium/2
    ('aa4592a5-457f-510a-9760-151aa800bab0', '0ec304d6-e92a-532e-b5dd-ce660ab5a825', 'What is assigned to label when temp = 15 in: if temp > 25: label = ''warm'' / elif temp > 10: label = ''mild'' / else: label = ''cold''?', 'single_choice', 'medium', ARRAY['''mild''', '''warm''', '''cold''', 'Unassigned']::text[], ARRAY[true, false, false, false]::boolean[]),
    -- course/python/skill/S07/question/hard/1
    ('9c4197f7-3435-50f9-a8b6-9cb4fca0a41a', '0ec304d6-e92a-532e-b5dd-ce660ab5a825', 'What happens if an if condition evaluates to False and there is no elif or else?', 'single_choice', 'hard', ARRAY['Execution skips the if block and continues after it.', 'Python raises IndentationError.', 'Python raises ValueError.', 'The program exits immediately.']::text[], ARRAY[true, false, false, false]::boolean[]),
    -- course/python/skill/S07/question/hard/2
    ('f47c6be3-3098-5daf-a319-dbd2d57f5feb', '0ec304d6-e92a-532e-b5dd-ce660ab5a825', 'Which rules apply to Python conditional statements?', 'multiple_choice', 'hard', ARRAY['A colon : is required at the end of if, elif, and else headers.', 'An else clause cannot take its own condition.', 'Multiple independent if statements can each execute in the same pass.', 'An elif clause can appear before any if clause.']::text[], ARRAY[true, true, true, false]::boolean[]),
    -- course/python/skill/S08/question/easy/1
    ('341fa1fa-e9c1-515f-b6bd-fe7202160556', 'ce75d29b-3c4f-5951-9180-b9fdc08f289f', 'A while loop checks its condition before every iteration, including the first.', 'true_false', 'easy', ARRAY['True', 'False']::text[], ARRAY[true, false]::boolean[]),
    -- course/python/skill/S08/question/easy/2
    ('af8beb6f-98d9-5124-b8cc-5fb248f3f8fb', 'ce75d29b-3c4f-5951-9180-b9fdc08f289f', 'How many times does the body run if count = 5 and the loop is while count <= 3:?', 'single_choice', 'easy', ARRAY['0 times', '1 time', '3 times', 'Infinitely many times']::text[], ARRAY[true, false, false, false]::boolean[]),
    -- course/python/skill/S08/question/medium/1
    ('7c7ac83d-ccd2-53aa-a012-6bd42e9de871', 'ce75d29b-3c4f-5951-9180-b9fdc08f289f', 'What causes a while loop to become an infinite loop?', 'single_choice', 'medium', ARRAY['Its condition never becomes False during execution.', 'Using <= instead of < in the condition.', 'Initializing the counter at 0.', 'Placing print() inside the body.']::text[], ARRAY[true, false, false, false]::boolean[]),
    -- course/python/skill/S08/question/medium/2
    ('a36de1fc-fd13-50b4-9c6c-779dff8392f8', 'ce75d29b-3c4f-5951-9180-b9fdc08f289f', 'With count = 1; while count <= 3: print(count); count = count + 1, what is true?', 'multiple_choice', 'medium', ARRAY['1, 2, and 3 are printed.', 'count is 4 after the loop finishes.', 'The loop runs forever.', 'The body runs 3 times.']::text[], ARRAY[true, true, false, true]::boolean[]),
    -- course/python/skill/S08/question/hard/1
    ('9fe24547-5ac5-5fe2-8740-73aa7d91d528', 'ce75d29b-3c4f-5951-9180-b9fdc08f289f', 'When is a while loop more natural than a for loop?', 'multiple_choice', 'hard', ARRAY['Repeating input prompts until the user enters a valid command.', 'Repeating until a computed value crosses a threshold after an unknown number of steps.', 'Processing each character of a fixed string once.', 'Iterating over range(5) without early exit conditions.']::text[], ARRAY[true, true, false, false]::boolean[]),
    -- course/python/skill/S08/question/hard/2
    ('4de3396b-56f4-5758-a9f0-5b3c95eb0d6f', 'ce75d29b-3c4f-5951-9180-b9fdc08f289f', 'After n = 10; while n > 1: n = n // 2, what is the final value of n?', 'single_choice', 'hard', ARRAY['1', '0', '2', '5']::text[], ARRAY[true, false, false, false]::boolean[]),
    -- course/python/skill/S09/question/easy/1
    ('75b6e65e-f2f6-502d-89b0-deb920af668d', '1761144d-4d90-5570-b8b5-adcc3f9f413b', 'What sequence of numbers does range(2, 6) produce?', 'single_choice', 'easy', ARRAY['2, 3, 4, 5', '2, 3, 4, 5, 6', '1, 2, 3, 4, 5', '2, 4, 6']::text[], ARRAY[true, false, false, false]::boolean[]),
    -- course/python/skill/S09/question/easy/2
    ('97a7aaaf-ccca-54b2-8dbc-426d15c2ed88', '1761144d-4d90-5570-b8b5-adcc3f9f413b', 'In Python, range(5) includes the number 5.', 'true_false', 'easy', ARRAY['True', 'False']::text[], ARRAY[false, true]::boolean[]),
    -- course/python/skill/S09/question/medium/1
    ('cf774e61-3bff-54f9-a1eb-6ad212f0f68b', '1761144d-4d90-5570-b8b5-adcc3f9f413b', 'Which range() calls produce exactly 3 numbers?', 'multiple_choice', 'medium', ARRAY['range(3)', 'range(1, 4)', 'range(3, 0, -1)', 'range(0, 3, 2)']::text[], ARRAY[true, true, true, false]::boolean[]),
    -- course/python/skill/S09/question/medium/2
    ('0c587264-59c8-5734-af9d-a51d426e70c7', '1761144d-4d90-5570-b8b5-adcc3f9f413b', 'What is total after: total = 0; for n in range(1, 5): total = total + n?', 'single_choice', 'medium', ARRAY['10', '15', '6', '4']::text[], ARRAY[true, false, false, false]::boolean[]),
    -- course/python/skill/S09/question/hard/1
    ('55777aea-ea16-5b83-8d38-f6ceca56f246', '1761144d-4d90-5570-b8b5-adcc3f9f413b', 'What sequence does range(0, 10, 3) produce?', 'single_choice', 'hard', ARRAY['0, 3, 6, 9', '0, 3, 6, 9, 10', '3, 6, 9', '0, 1, 2']::text[], ARRAY[true, false, false, false]::boolean[]),
    -- course/python/skill/S09/question/hard/2
    ('6caa041a-ce9e-5398-970f-6dfa3a9995f7', '1761144d-4d90-5570-b8b5-adcc3f9f413b', 'Which loops print every character of ''ab'' once?', 'multiple_choice', 'hard', ARRAY['for ch in ''ab'': print(ch)', 'for i in range(2): print(''ab''[i])', 'for i in range(1, 3): print(''ab''[i])', 'for ch in [''a'', ''b'']: print(ch)']::text[], ARRAY[true, true, false, true]::boolean[]),
    -- course/python/skill/S10/question/easy/1
    ('34d0d846-31f6-5da6-9781-7935f33a4bd6', '7cabeda5-0c6c-5c73-8a67-967fd84bce45', 'The continue statement ends the loop permanently without running any later iterations.', 'true_false', 'easy', ARRAY['True', 'False']::text[], ARRAY[false, true]::boolean[]),
    -- course/python/skill/S10/question/easy/2
    ('a4dbf079-8d61-51c5-8b5d-cf96357210e4', '7cabeda5-0c6c-5c73-8a67-967fd84bce45', 'What does the break statement do inside a loop?', 'single_choice', 'easy', ARRAY['Exits the innermost enclosing loop immediately.', 'Skips only the current iteration.', 'Restarts the loop from its first item.', 'Exits the entire Python program.']::text[], ARRAY[true, false, false, false]::boolean[]),
    -- course/python/skill/S10/question/medium/1
    ('7d295949-5089-5990-97f2-6c53079c1126', '7cabeda5-0c6c-5c73-8a67-967fd84bce45', 'In: for n in range(5): if n == 2: break / print(n), how many numbers are printed?', 'single_choice', 'medium', ARRAY['2 (prints 0 and 1)', '3 (prints 0, 1, and 2)', '4 (prints 0, 1, 3, 4)', '0']::text[], ARRAY[true, false, false, false]::boolean[]),
    -- course/python/skill/S10/question/medium/2
    ('eca786ea-ae10-5227-9b51-63c09d8854d2', '7cabeda5-0c6c-5c73-8a67-967fd84bce45', 'In: for n in range(4): if n == 2: continue / print(n), which numbers are printed?', 'multiple_choice', 'medium', ARRAY['0', '1', '2', '3']::text[], ARRAY[true, true, false, true]::boolean[]),
    -- course/python/skill/S10/question/hard/1
    ('7693f9a0-e5b8-5f16-b150-4466d03d0d1b', '7cabeda5-0c6c-5c73-8a67-967fd84bce45', 'Which statements hold for break and continue?', 'multiple_choice', 'hard', ARRAY['Both can be used in both for loops and while loops.', 'Code in the loop body after a triggered continue is skipped for that iteration.', 'In a while True: loop, break provides a way to exit the loop.', 'continue exits the enclosing function.']::text[], ARRAY[true, true, true, false]::boolean[]),
    -- course/python/skill/S10/question/hard/2
    ('0cb404b3-4d87-5d9f-ac9b-52a9dc22e0af', '7cabeda5-0c6c-5c73-8a67-967fd84bce45', 'In nested loops, what does break inside the inner loop exit?', 'single_choice', 'hard', ARRAY['Only the inner loop', 'Both outer and inner loops', 'Only the outer loop', 'The enclosing function']::text[], ARRAY[true, false, false, false]::boolean[]),
    -- course/python/skill/S11/question/easy/1
    ('a2a5de7e-7f95-5a16-9868-d847c8adce7f', 'a9d43b35-426f-5500-9122-1043ddfa7977', 'After items = [1, 2]; items.append(3), what is len(items)?', 'single_choice', 'easy', ARRAY['3', '2', '4', '6']::text[], ARRAY[true, false, false, false]::boolean[]),
    -- course/python/skill/S11/question/easy/2
    ('1a324327-c0e9-5345-ac2f-25dd5e411354', 'a9d43b35-426f-5500-9122-1043ddfa7977', 'In Python, lists are mutable, so elements can be replaced, added, or removed.', 'true_false', 'easy', ARRAY['True', 'False']::text[], ARRAY[true, false]::boolean[]),
    -- course/python/skill/S11/question/medium/1
    ('7b4177a9-797b-547e-aa80-e5b07496ddcd', 'a9d43b35-426f-5500-9122-1043ddfa7977', 'Given scores = [72, 85, 90], which expressions are True?', 'multiple_choice', 'medium', ARRAY['scores[0] == 72', 'scores[-1] == 90', '85 in scores', 'len(scores) == 4']::text[], ARRAY[true, true, true, false]::boolean[]),
    -- course/python/skill/S11/question/medium/2
    ('2059a88e-892b-5337-9795-0567ea98b4fb', 'a9d43b35-426f-5500-9122-1043ddfa7977', 'After a = [1, 2]; b = a; b.append(3), what is a?', 'single_choice', 'medium', ARRAY['[1, 2, 3]', '[1, 2]', '[3]', 'Raises NameError']::text[], ARRAY[true, false, false, false]::boolean[]),
    -- course/python/skill/S11/question/hard/1
    ('3ec37a02-cf3f-525e-9196-f04d2cc31b3d', 'a9d43b35-426f-5500-9122-1043ddfa7977', 'What does items.remove(72) remove from items = [72, 85, 72]?', 'single_choice', 'hard', ARRAY['Only the first 72, leaving [85, 72]', 'All 72 values, leaving [85]', 'Index 72', 'Returns [72, 85] unchanged']::text[], ARRAY[true, false, false, false]::boolean[]),
    -- course/python/skill/S11/question/hard/2
    ('2b58516f-d75c-52dc-9798-8e0e3ab7470e', 'a9d43b35-426f-5500-9122-1043ddfa7977', 'Which operations create an independent list copy rather than another alias to nums?', 'multiple_choice', 'hard', ARRAY['nums.copy()', 'list(nums)', 'nums[:]', 'alias = nums']::text[], ARRAY[true, true, true, false]::boolean[]),
    -- course/python/skill/S12/question/easy/1
    ('88720a00-57e1-5ed5-9f78-757c314635f6', 'f26a0234-bcec-5ba6-abc3-66ecbefd415f', 'Using enumerate(scores) in a for loop yields both each item''s index and its value.', 'true_false', 'easy', ARRAY['True', 'False']::text[], ARRAY[true, false]::boolean[]),
    -- course/python/skill/S12/question/easy/2
    ('61f0c107-34c8-51cf-a88c-235927c0540e', 'f26a0234-bcec-5ba6-abc3-66ecbefd415f', 'For scores = [40, 70, 55], how many values pass score >= 50 in a counting loop?', 'single_choice', 'easy', ARRAY['2', '1', '3', '0']::text[], ARRAY[true, false, false, false]::boolean[]),
    -- course/python/skill/S12/question/medium/1
    ('b234ed5b-1cdf-51ad-b472-19ddfce0a465', 'f26a0234-bcec-5ba6-abc3-66ecbefd415f', 'When finding the maximum of scores = [-5, -2, -9], why initialize best = scores[0] instead of best = 0?', 'single_choice', 'medium', ARRAY['If all scores are negative, 0 would wrongly stay the largest.', 'Python forbids best = 0 before a loop.', 'scores[0] is always the maximum.', 'Using 0 causes a TypeError.']::text[], ARRAY[true, false, false, false]::boolean[]),
    -- course/python/skill/S12/question/medium/2
    ('22afbbe2-3c43-52d1-b661-ebe867e13f8e', 'f26a0234-bcec-5ba6-abc3-66ecbefd415f', 'Which patterns safely build a doubled copy from scores = [2, 5]?', 'multiple_choice', 'medium', ARRAY['doubled = [] followed by doubled.append(s * 2) in a loop', '[s * 2 for s in scores]', 'Appending to scores while iterating for s in scores:', 'Initializing doubled = [0] * len(scores) and assigning by index']::text[], ARRAY[true, true, false, true]::boolean[]),
    -- course/python/skill/S12/question/hard/1
    ('a319563c-3f22-567a-8777-f5ec21389064', 'f26a0234-bcec-5ba6-abc3-66ecbefd415f', 'Why should you avoid removing items directly from a list while iterating over that same list with for?', 'multiple_choice', 'hard', ARRAY['Removing shifts later elements left so items can be skipped.', 'Building a new filtered list avoids index-shift bugs.', 'Python always raises SyntaxError if remove() appears inside a loop.', 'Iterating over a slice copy xs[:] or filtering into a new list is safer.']::text[], ARRAY[true, true, false, true]::boolean[]),
    -- course/python/skill/S12/question/hard/2
    ('d0e801ff-da73-5cea-aff2-d1e680653265', 'f26a0234-bcec-5ba6-abc3-66ecbefd415f', 'What does list(enumerate([''a'', ''b''])) produce?', 'single_choice', 'hard', ARRAY['[(0, ''a''), (1, ''b'')]', '[(''a'', 0), (''b'', 1)]', '[1, 2]', '[''a'', ''b'']']::text[], ARRAY[true, false, false, false]::boolean[]),
    -- course/python/skill/S13/question/easy/1
    ('b21c2f55-0227-5a64-b890-c77a7904f7a3', '18869e1c-168a-585d-a1b1-9c97c12383d1', 'What does len({1, 1, 2}) return?', 'single_choice', 'easy', ARRAY['2', '3', '1', 'Raises TypeError']::text[], ARRAY[true, false, false, false]::boolean[]),
    -- course/python/skill/S13/question/easy/2
    ('d0c983be-a6f5-5de3-869b-795570414af2', '18869e1c-168a-585d-a1b1-9c97c12383d1', 'A set supports indexing like my_set[0] to fetch its first element.', 'true_false', 'easy', ARRAY['True', 'False']::text[], ARRAY[false, true]::boolean[]),
    -- course/python/skill/S13/question/medium/1
    ('e6f6567c-6ff8-58ae-a4ef-2492679faef6', '18869e1c-168a-585d-a1b1-9c97c12383d1', 'Which statements describe Python tuples?', 'multiple_choice', 'medium', ARRAY['Tuples preserve element order and support indexing.', 'Tuples cannot be modified in place after creation.', 'A one-element tuple needs a trailing comma, as in (5,).', 'Tuples automatically remove duplicate values.']::text[], ARRAY[true, true, true, false]::boolean[]),
    -- course/python/skill/S13/question/medium/2
    ('902cefa8-405a-5a25-9064-87e289ed86d5', '18869e1c-168a-585d-a1b1-9c97c12383d1', 'Given a = {1, 2, 3} and b = {2, 3, 4}, what is a & b?', 'single_choice', 'medium', ARRAY['{2, 3}', '{1, 2, 3, 4}', '{1}', '{4}']::text[], ARRAY[true, false, false, false]::boolean[]),
    -- course/python/skill/S13/question/hard/1
    ('919fb0bd-d4e3-5723-865b-7dd3baa8a3dc', '18869e1c-168a-585d-a1b1-9c97c12383d1', 'Given a = {1, 2, 3} and b = {2, 4}, what is a - b?', 'single_choice', 'hard', ARRAY['{1, 3}', '{4}', '{2}', '{1, 2, 3, 4}']::text[], ARRAY[true, false, false, false]::boolean[]),
    -- course/python/skill/S13/question/hard/2
    ('3d322f31-d12e-5474-b92c-4292c01debef', '18869e1c-168a-585d-a1b1-9c97c12383d1', 'Which expressions create a valid empty or non-empty set in Python?', 'multiple_choice', 'hard', ARRAY['set()', '{1, 2}', 'set([1, 2, 2])', '{}']::text[], ARRAY[true, true, true, false]::boolean[]),
    -- course/python/skill/S14/question/easy/1
    ('92bf5207-b9a3-55f4-a59d-925bac4f44ab', '37c01bbf-2268-5b7a-adae-6d4bebcdc504', 'Accessing a missing key with d[''missing''] raises a KeyError.', 'true_false', 'easy', ARRAY['True', 'False']::text[], ARRAY[true, false]::boolean[]),
    -- course/python/skill/S14/question/easy/2
    ('ce9eb7c5-b6e1-56f8-bc5d-f96fd6566f11', '37c01bbf-2268-5b7a-adae-6d4bebcdc504', 'What does {"a": 1}.get("b", 5) return?', 'single_choice', 'easy', ARRAY['5', 'None', '1', 'Raises KeyError']::text[], ARRAY[true, false, false, false]::boolean[]),
    -- course/python/skill/S14/question/medium/1
    ('7632e4ca-e31c-5547-b89d-15a11ec9504d', '37c01bbf-2268-5b7a-adae-6d4bebcdc504', 'Given ages = {"Ada": 36}, what happens after ages["Ada"] = 37?', 'single_choice', 'medium', ARRAY['ages becomes {"Ada": 37}', 'ages holds two "Ada" entries', 'Raises KeyError', 'ages stays unchanged']::text[], ARRAY[true, false, false, false]::boolean[]),
    -- course/python/skill/S14/question/medium/2
    ('b538764c-cf6d-5c5d-96b7-f40c1b8cb58b', '37c01bbf-2268-5b7a-adae-6d4bebcdc504', 'Which Python values can safely be used as dictionary keys?', 'multiple_choice', 'medium', ARRAY['"Ada"', '42', '(1, 2)', '[1, 2]']::text[], ARRAY[true, true, true, false]::boolean[]),
    -- course/python/skill/S14/question/hard/1
    ('69cb172b-9754-5778-98ae-963cbbad9b31', '37c01bbf-2268-5b7a-adae-6d4bebcdc504', 'With d = {''x'': 10, ''y'': 20}, which checks or lookups are safe without raising KeyError?', 'multiple_choice', 'hard', ARRAY['''z'' in d', 'd.get(''z'')', 'd.get(''z'', 0)', 'd[''z'']']::text[], ARRAY[true, true, true, false]::boolean[]),
    -- course/python/skill/S14/question/hard/2
    ('d3fce7dd-084c-57fc-9b8d-c4a20fb5c06f', '37c01bbf-2268-5b7a-adae-6d4bebcdc504', 'What does iterating over d.items() yield on each step?', 'single_choice', 'hard', ARRAY['A (key, value) pair', 'Only the keys', 'Only the values', 'An integer index']::text[], ARRAY[true, false, false, false]::boolean[]),
    -- course/python/skill/S15/question/easy/1
    ('0fb4d8be-aceb-57f3-983c-77a57bb9f177', '4604bdb9-d7ac-5877-9fa0-5610ab6c64eb', 'When does the body of a def greet(): function run?', 'single_choice', 'easy', ARRAY['When greet() is called', 'Immediately when def is read', 'Only at program exit', 'Only inside a loop']::text[], ARRAY[true, false, false, false]::boolean[]),
    -- course/python/skill/S15/question/easy/2
    ('6cf13941-0a1c-50fe-94b6-06aa636e9ff8', '4604bdb9-d7ac-5877-9fa0-5610ab6c64eb', 'Defining a function twice with the same name runs its body automatically.', 'true_false', 'easy', ARRAY['True', 'False']::text[], ARRAY[false, true]::boolean[]),
    -- course/python/skill/S15/question/medium/1
    ('79a4b286-881f-5770-954c-4e7645c2d2f0', '4604bdb9-d7ac-5877-9fa0-5610ab6c64eb', 'Which statements describe Python functions defined with def?', 'multiple_choice', 'medium', ARRAY['A function must be defined before code that calls it runs.', 'The body of a function is indented under the def header.', 'Calling a function once executes its body once.', 'A function header ends without a colon.']::text[], ARRAY[true, true, true, false]::boolean[]),
    -- course/python/skill/S15/question/medium/2
    ('adcb795b-722f-5a06-9eb6-5611455f69cd', '4604bdb9-d7ac-5877-9fa0-5610ab6c64eb', 'A program defines greet() but never calls greet(). What does it display?', 'single_choice', 'medium', ARRAY['Nothing', 'The function name', 'The body once', 'Raises NameError']::text[], ARRAY[true, false, false, false]::boolean[]),
    -- course/python/skill/S15/question/hard/1
    ('44a7142f-cd4c-53e9-a531-e59682617a7c', '4604bdb9-d7ac-5877-9fa0-5610ab6c64eb', 'What happens if you call greet() above the line where def greet(): is executed?', 'single_choice', 'hard', ARRAY['NameError', 'SyntaxError', 'IndentationError', 'Runs normally anyway']::text[], ARRAY[true, false, false, false]::boolean[]),
    -- course/python/skill/S15/question/hard/2
    ('0dda663b-80d1-5ecb-ad75-22b7e0322a9a', '4604bdb9-d7ac-5877-9fa0-5610ab6c64eb', 'Why are functions useful in Python programs?', 'multiple_choice', 'hard', ARRAY['They let you name and reuse a block of logic without duplicating code.', 'They make programs easier to organize and modify in one place.', 'Each call runs the body with current state or inputs.', 'Every function must contain at least one while loop.']::text[], ARRAY[true, true, true, false]::boolean[]),
    -- course/python/skill/S16/question/easy/1
    ('f9ff556a-16a5-5179-b107-d1ac282acbeb', 'da6797cf-4a5c-5e29-ad28-0a68292ed9c3', 'A function that finishes without executing a return statement returns None.', 'true_false', 'easy', ARRAY['True', 'False']::text[], ARRAY[true, false]::boolean[]),
    -- course/python/skill/S16/question/easy/2
    ('66443188-379d-5356-903b-099d652b8a05', 'da6797cf-4a5c-5e29-ad28-0a68292ed9c3', 'Given def area(w, h): return w * h, what does area(area(1, 2), 5) return?', 'single_choice', 'easy', ARRAY['10', '7', '5', 'None']::text[], ARRAY[true, false, false, false]::boolean[]),
    -- course/python/skill/S16/question/medium/1
    ('fb49fb73-7307-5601-92df-b8ac7c7ba3a0', 'da6797cf-4a5c-5e29-ad28-0a68292ed9c3', 'In def f(x): return x + 1; print(x + 2), when does print(x + 2) run?', 'single_choice', 'medium', ARRAY['Never, because return exits the function immediately.', 'After returning x + 1.', 'Only when x is 0.', 'Raises SyntaxError.']::text[], ARRAY[true, false, false, false]::boolean[]),
    -- course/python/skill/S16/question/medium/2
    ('c3d0bc80-6443-5513-a1ae-d80b81352fd1', 'da6797cf-4a5c-5e29-ad28-0a68292ed9c3', 'Given def diff(a, b): return a - b, which calls evaluate to 3?', 'multiple_choice', 'medium', ARRAY['diff(5, 2)', 'diff(a=5, b=2)', 'diff(b=2, a=5)', 'diff(2, 5)']::text[], ARRAY[true, true, true, false]::boolean[]),
    -- course/python/skill/S16/question/hard/1
    ('98406e3f-dcaf-529b-a870-226635325928', 'da6797cf-4a5c-5e29-ad28-0a68292ed9c3', 'Which statements are true about parameters and return values?', 'multiple_choice', 'hard', ARRAY['Positional arguments match parameters by order.', 'Keyword arguments match parameters by parameter name.', 'return sends a value back to the caller so it can be stored or combined.', 'print() inside a function automatically sets the function''s return value.']::text[], ARRAY[true, true, true, false]::boolean[]),
    -- course/python/skill/S16/question/hard/2
    ('8075cd3a-4a28-5952-a58a-6de2ad0d0c2a', 'da6797cf-4a5c-5e29-ad28-0a68292ed9c3', 'Given def show(v): print(v), what value is stored in r after r = show(5)?', 'single_choice', 'hard', ARRAY['None', '5', '"5"', 'True']::text[], ARRAY[true, false, false, false]::boolean[]),
    -- course/python/skill/S17/question/easy/1
    ('08ea5b82-46f8-51eb-b9f5-5c8a9211f69d', '807cc7ab-d993-5633-b93c-cc2896724d1f', 'With count = 0; def reset(): count = 5 / reset(); print(count), what is printed?', 'single_choice', 'easy', ARRAY['0', '5', 'None', 'Raises NameError']::text[], ARRAY[true, false, false, false]::boolean[]),
    -- course/python/skill/S17/question/easy/2
    ('88fa62a1-bf82-5a4e-8374-e0114500a985', '807cc7ab-d993-5633-b93c-cc2896724d1f', 'A variable first assigned inside a function body is local to that function by default.', 'true_false', 'easy', ARRAY['True', 'False']::text[], ARRAY[true, false]::boolean[]),
    -- course/python/skill/S17/question/medium/1
    ('8873e464-617a-5ca4-b809-1f0a352c88d3', '807cc7ab-d993-5633-b93c-cc2896724d1f', 'Given def make_total(): total = 10; return total, what holds after res = make_total()?', 'multiple_choice', 'medium', ARRAY['res refers to 10.', 'Accessing total outside make_total raises NameError.', 'total remains alive globally.', 'Passing inputs via parameters avoids hidden global state.']::text[], ARRAY[true, true, false, true]::boolean[]),
    -- course/python/skill/S17/question/medium/2
    ('e07c1086-eda3-5a9d-a520-2bf4a5807913', '807cc7ab-d993-5633-b93c-cc2896724d1f', 'What happens if a function reads a global variable limit = 10 without assigning to limit?', 'single_choice', 'medium', ARRAY['It reads 10.', 'It raises UnboundLocalError.', 'It modifies limit to None.', 'It raises SyntaxError.']::text[], ARRAY[true, false, false, false]::boolean[]),
    -- course/python/skill/S17/question/hard/1
    ('4c675368-45f3-54a2-bfd6-fae3c5c32ee5', '807cc7ab-d993-5633-b93c-cc2896724d1f', 'What happens inside def f(): print(x); x = 2 when x = 10 is defined globally?', 'single_choice', 'hard', ARRAY['UnboundLocalError, because assigning x makes x local throughout f.', 'Prints 10 then sets local x to 2.', 'Prints 2.', 'Modifies global x to 2.']::text[], ARRAY[true, false, false, false]::boolean[]),
    -- course/python/skill/S17/question/hard/2
    ('a4998e48-456d-5262-a633-c15866e45409', '807cc7ab-d993-5633-b93c-cc2896724d1f', 'Which practices accurately reflect Python variable scoping?', 'multiple_choice', 'hard', ARRAY['Parameters and return values make data flow explicit.', 'Local variables disappear when the function call finishes.', 'Two different functions can each have their own independent local variable named total.', 'Variables created in one function are automatically readable in every other function.']::text[], ARRAY[true, true, true, false]::boolean[]),
    -- course/python/skill/S18/question/easy/1
    ('79621bfa-974d-5536-9d3d-dc41d8c823fd', '23dec971-6865-59ab-b55b-24ccd3bda430', 'String methods like .strip() and .replace() modify the original string in place.', 'true_false', 'easy', ARRAY['True', 'False']::text[], ARRAY[false, true]::boolean[]),
    -- course/python/skill/S18/question/easy/2
    ('0743766c-df70-59a1-9652-b83fc714bc00', '23dec971-6865-59ab-b55b-24ccd3bda430', 'What does "Hello World".lower().split() return?', 'single_choice', 'easy', ARRAY['["hello", "world"]', '["Hello", "World"]', '"hello world"', '["helloworld"]']::text[], ARRAY[true, false, false, false]::boolean[]),
    -- course/python/skill/S18/question/medium/1
    ('f0eb6550-8ba8-50b5-a57f-74efabae60b1', '23dec971-6865-59ab-b55b-24ccd3bda430', 'Given score = 0.875, what does f"{score:.1%}" produce?', 'single_choice', 'medium', ARRAY['"87.5%"', '"0.9%"', '"87%"', '"0.875%"']::text[], ARRAY[true, false, false, false]::boolean[]),
    -- course/python/skill/S18/question/medium/2
    ('b561ba22-895e-515d-a2f2-2b8e140590f5', '23dec971-6865-59ab-b55b-24ccd3bda430', 'Which expressions evaluate to ''a-b''?', 'multiple_choice', 'medium', ARRAY['"-".join(["a", "b"])', '" a-b ".strip()', '"a,b".replace(",", "-")', '"a-b".upper()']::text[], ARRAY[true, true, true, false]::boolean[]),
    -- course/python/skill/S18/question/hard/1
    ('3a3ab603-4441-5823-9ee1-f3495609152d', '23dec971-6865-59ab-b55b-24ccd3bda430', 'With text = ''  hi  '', which method calls return a new string or test prefix/suffix accurately?', 'multiple_choice', 'hard', ARRAY['text.strip() == ''hi''', 'text.lstrip() == ''hi  ''', '''hi''.startswith(''h'')', '''hi''.endswith(''H'')']::text[], ARRAY[true, true, true, false]::boolean[]),
    -- course/python/skill/S18/question/hard/2
    ('414e9cf5-04bc-5372-946e-599e1a5bc769', '23dec971-6865-59ab-b55b-24ccd3bda430', 'What does "banana".find("na") return?', 'single_choice', 'hard', ARRAY['2', '4', 'True', '-1']::text[], ARRAY[true, false, false, false]::boolean[]),
    -- course/python/skill/S19/question/easy/1
    ('9fdecdcd-012b-5e56-b437-b6359f58965f', '41a0f9ce-a2e0-5243-bdb4-a8cd5088e1f7', 'With: try: n = int(input()); print(100 / n) / except ValueError: ... / except ZeroDivisionError: ..., which block runs when the user types 0?', 'single_choice', 'easy', ARRAY['except ZeroDivisionError', 'except ValueError', 'Both except blocks', 'No except block']::text[], ARRAY[true, false, false, false]::boolean[]),
    -- course/python/skill/S19/question/easy/2
    ('89efff69-556b-52f4-be10-9d2129073dee', '41a0f9ce-a2e0-5243-bdb4-a8cd5088e1f7', 'Naming specific exception types in except avoids silently hiding unrelated errors.', 'true_false', 'easy', ARRAY['True', 'False']::text[], ARRAY[true, false]::boolean[]),
    -- course/python/skill/S19/question/medium/1
    ('aef875cf-bb7b-5cce-ba60-87b1199ddf68', '41a0f9ce-a2e0-5243-bdb4-a8cd5088e1f7', 'Which Python operations raise the named exception?', 'multiple_choice', 'medium', ARRAY['int("abc") raises ValueError', '1 / 0 raises ZeroDivisionError', '{"a": 1}["b"] raises KeyError', '[1, 2][0] raises IndexError']::text[], ARRAY[true, true, true, false]::boolean[]),
    -- course/python/skill/S19/question/medium/2
    ('228a8b39-7a82-5dbd-845e-a9f8a3876129', '41a0f9ce-a2e0-5243-bdb4-a8cd5088e1f7', 'When does a finally block execute in try / except / finally?', 'single_choice', 'medium', ARRAY['Always, whether or not an exception was raised in try.', 'Only when an exception occurred.', 'Only when no exception occurred.', 'Only when except re-raises.']::text[], ARRAY[true, false, false, false]::boolean[]),
    -- course/python/skill/S19/question/hard/1
    ('3ff08f45-f438-5fdc-88fa-b08f8a759b38', '41a0f9ce-a2e0-5243-bdb4-a8cd5088e1f7', 'If an exception occurs on line 1 of a 3-line try block and is caught by except, what happens to lines 2 and 3 of try?', 'single_choice', 'hard', ARRAY['They are skipped and control jumps directly to the matching except block.', 'They still run before except.', 'They run after except finishes.', 'Python retries line 1 automatically.']::text[], ARRAY[true, false, false, false]::boolean[]),
    -- course/python/skill/S19/question/hard/2
    ('7a76dc09-0fb5-59c9-80a2-ee2c9254d661', '41a0f9ce-a2e0-5243-bdb4-a8cd5088e1f7', 'Which statements describe good exception-handling behavior in Python?', 'multiple_choice', 'hard', ARRAY['Only lines inside try are protected by its except clauses.', 'At most one matching except block executes for a single raised exception.', 'Different except blocks can handle ValueError and ZeroDivisionError separately.', 'An empty try block with no except or finally is valid syntax.']::text[], ARRAY[true, true, true, false]::boolean[]),
    -- course/python/skill/S20/question/easy/1
    ('92a86f58-47b3-5372-99c7-ad17efd59262', '09b1a1bd-bef5-594c-afdf-ca43f0a01ab8', 'Using with open(...) as f: closes the file automatically when the with block exits.', 'true_false', 'easy', ARRAY['True', 'False']::text[], ARRAY[true, false]::boolean[]),
    -- course/python/skill/S20/question/easy/2
    ('7ba936d7-bd89-5be4-a7bb-a59db78a02d4', '09b1a1bd-bef5-594c-afdf-ca43f0a01ab8', 'A program opens an existing non-empty file with open("notes.txt", "w") and writes nothing. What is inside notes.txt afterwards?', 'single_choice', 'easy', ARRAY['Nothing (the file is emptied)', 'Its original content stays untouched', 'A blank newline is added', 'FileExistsError is raised']::text[], ARRAY[true, false, false, false]::boolean[]),
    -- course/python/skill/S20/question/medium/1
    ('63db8d08-cf64-5499-bcc7-17b0ed5de2f6', '09b1a1bd-bef5-594c-afdf-ca43f0a01ab8', 'What exception is raised when opening a missing file in ''r'' mode?', 'single_choice', 'medium', ARRAY['FileNotFoundError', 'KeyError', 'IndexError', 'ZeroDivisionError']::text[], ARRAY[true, false, false, false]::boolean[]),
    -- course/python/skill/S20/question/medium/2
    ('2b97d87b-30f1-553a-a4cf-158d0ff5c269', '09b1a1bd-bef5-594c-afdf-ca43f0a01ab8', 'Which statements describe Python text file modes?', 'multiple_choice', 'medium', ARRAY['"r" opens a file for reading and is the default mode.', '"w" creates a new file or overwrites an existing one.', '"a" appends new text to the end of the file.', 'file.write("hi") automatically adds a newline character at the end.']::text[], ARRAY[true, true, true, false]::boolean[]),
    -- course/python/skill/S20/question/hard/1
    ('a77231eb-97a1-5fe8-82ec-a73e2dfb048a', '09b1a1bd-bef5-594c-afdf-ca43f0a01ab8', 'Which practices work reliably when reading or writing text files?', 'multiple_choice', 'hard', ARRAY['Wrapping open() in a with statement ensures cleanup even if an error occurs.', 'Include "\n" explicitly when calling file.write(...) to start a new line.', 'Handle FileNotFoundError with try/except when opening an optional file for reading.', 'You can read lines from a file opened only in ''w'' mode.']::text[], ARRAY[true, true, true, false]::boolean[]),
    -- course/python/skill/S20/question/hard/2
    ('d4cf9cdb-997e-56d6-a044-286352bf3ffc', '09b1a1bd-bef5-594c-afdf-ca43f0a01ab8', 'When reading lines using for line in file:, why is line.strip() often used?', 'single_choice', 'hard', ARRAY['To remove trailing newline characters such as ''\n'' from each line.', 'To delete the line from the file on disk.', 'To convert the line into a list of integers.', 'To close the file after the first line.']::text[], ARRAY[true, false, false, false]::boolean[]);

CREATE TEMP TABLE sd04_quiz_seed (
    id uuid NOT NULL,
    course_id uuid NOT NULL,
    title text NOT NULL,
    kind text NOT NULL,
    time_limit_seconds integer NOT NULL,
    publication_status text NOT NULL
) ON COMMIT DROP;

INSERT INTO sd04_quiz_seed (id, course_id, title, kind, time_limit_seconds, publication_status)
VALUES
    -- course/python/quiz/placement
    ('4737c62b-77c2-5951-9f97-0430d02e153f', '68ea4540-e9da-5605-9054-28b29afa0992', 'Python Fundamentals Placement Quiz', 'diagnostic', 1800, 'published'),
    -- course/python/quiz/practice/S01
    ('94074861-7b0a-5e8e-bd61-1ac97784f53b', '68ea4540-e9da-5605-9054-28b29afa0992', 'Program Output Practice', 'practice', 600, 'published'),
    -- course/python/quiz/practice/S02
    ('bafc0c7a-abf4-5dda-ae1f-15f4e5859520', '68ea4540-e9da-5605-9054-28b29afa0992', 'Variables and Assignment Practice', 'practice', 600, 'published'),
    -- course/python/quiz/practice/S03
    ('a148db7d-a2e7-581c-b1b7-86fdcafd31a5', '68ea4540-e9da-5605-9054-28b29afa0992', 'Numbers and Arithmetic Practice', 'practice', 600, 'published'),
    -- course/python/quiz/practice/S04
    ('16072f31-8bb1-5224-807d-4dc81d208710', '68ea4540-e9da-5605-9054-28b29afa0992', 'Strings Practice', 'practice', 600, 'published'),
    -- course/python/quiz/practice/S05
    ('a3e9a33c-5726-5951-a61a-0b50997787d8', '68ea4540-e9da-5605-9054-28b29afa0992', 'Input and Type Conversion Practice', 'practice', 600, 'published'),
    -- course/python/quiz/practice/S06
    ('ea08ccdc-f915-5ccb-92fb-ec79374f51b5', '68ea4540-e9da-5605-9054-28b29afa0992', 'Booleans and Comparisons Practice', 'practice', 600, 'published'),
    -- course/python/quiz/practice/S07
    ('b0d68d0a-64b0-599d-8d29-3047a7cc58c3', '68ea4540-e9da-5605-9054-28b29afa0992', 'Conditionals Practice', 'practice', 600, 'published'),
    -- course/python/quiz/practice/S08
    ('081b5603-882d-5503-af8c-eda46c5d9052', '68ea4540-e9da-5605-9054-28b29afa0992', 'While Loops Practice', 'practice', 600, 'published'),
    -- course/python/quiz/practice/S09
    ('d0abe6a3-f1aa-5809-a3b3-71f732746490', '68ea4540-e9da-5605-9054-28b29afa0992', 'For Loops Practice', 'practice', 600, 'published'),
    -- course/python/quiz/practice/S10
    ('0748319a-a4e3-5fbc-b3c3-3091aa44b2f9', '68ea4540-e9da-5605-9054-28b29afa0992', 'Loop Control Practice', 'practice', 600, 'published'),
    -- course/python/quiz/practice/S11
    ('c85ed207-9c84-50ba-b386-5a725b8095a3', '68ea4540-e9da-5605-9054-28b29afa0992', 'Lists Practice', 'practice', 600, 'published'),
    -- course/python/quiz/practice/S12
    ('fb5c0466-d1fb-5c60-bad4-21c7c48a5d1c', '68ea4540-e9da-5605-9054-28b29afa0992', 'List Iteration Patterns Practice', 'practice', 600, 'published'),
    -- course/python/quiz/practice/S13
    ('073e29a0-8484-50ff-94b9-5b312c985535', '68ea4540-e9da-5605-9054-28b29afa0992', 'Tuples and Sets Practice', 'practice', 600, 'published'),
    -- course/python/quiz/practice/S14
    ('ebd78c4e-0655-53c7-8ffb-eae82a309594', '68ea4540-e9da-5605-9054-28b29afa0992', 'Dictionaries Practice', 'practice', 600, 'published'),
    -- course/python/quiz/practice/S15
    ('be689582-a765-5be2-8a7c-6f7eb6b1cfdc', '68ea4540-e9da-5605-9054-28b29afa0992', 'Functions Practice', 'practice', 600, 'published'),
    -- course/python/quiz/practice/S16
    ('adf0a1e1-2ca7-5e61-8895-1cf2ebc5c2bb', '68ea4540-e9da-5605-9054-28b29afa0992', 'Parameters and Return Values Practice', 'practice', 600, 'published'),
    -- course/python/quiz/practice/S17
    ('64b483c8-a4ed-5dbe-99a7-468136ad4ead', '68ea4540-e9da-5605-9054-28b29afa0992', 'Variable Scope Practice', 'practice', 600, 'published'),
    -- course/python/quiz/practice/S18
    ('bbd032f7-0949-57df-ab5f-f2ca0488ca38', '68ea4540-e9da-5605-9054-28b29afa0992', 'String Methods and Formatting Practice', 'practice', 600, 'published'),
    -- course/python/quiz/practice/S19
    ('842a2c95-5e15-51f5-844e-720f6382266c', '68ea4540-e9da-5605-9054-28b29afa0992', 'Exceptions Practice', 'practice', 600, 'published'),
    -- course/python/quiz/practice/S20
    ('3c2ccff4-2d73-5587-8a60-04c45187f6be', '68ea4540-e9da-5605-9054-28b29afa0992', 'File Input and Output Practice', 'practice', 600, 'published'),
    -- course/python/quiz/practice/S01/easy
    ('03ed10c0-89e6-5f1e-9a89-3294207f2738', '68ea4540-e9da-5605-9054-28b29afa0992', 'Program Output practice (Easy)', 'practice', 300, 'published'),
    -- course/python/quiz/practice/S01/medium
    ('4d5902b8-0ff2-56d6-9f97-f4a57ab9f612', '68ea4540-e9da-5605-9054-28b29afa0992', 'Program Output practice (Medium)', 'practice', 300, 'published'),
    -- course/python/quiz/practice/S01/hard
    ('86e93c0b-95b4-52af-acd8-5a9152479da4', '68ea4540-e9da-5605-9054-28b29afa0992', 'Program Output practice (Hard)', 'practice', 300, 'published'),
    -- course/python/quiz/practice/S02/easy
    ('d14d312c-71c7-5620-b919-4826990f8ef3', '68ea4540-e9da-5605-9054-28b29afa0992', 'Variables and Assignment practice (Easy)', 'practice', 300, 'published'),
    -- course/python/quiz/practice/S02/medium
    ('3a8a532c-437f-5eb7-a156-222ac734ec4d', '68ea4540-e9da-5605-9054-28b29afa0992', 'Variables and Assignment practice (Medium)', 'practice', 300, 'published'),
    -- course/python/quiz/practice/S02/hard
    ('20d6f846-4e64-5507-bd2a-465eff42df6a', '68ea4540-e9da-5605-9054-28b29afa0992', 'Variables and Assignment practice (Hard)', 'practice', 300, 'published'),
    -- course/python/quiz/practice/S03/easy
    ('cfdb091a-153f-537e-ad78-8f85ecb30f74', '68ea4540-e9da-5605-9054-28b29afa0992', 'Numbers and Arithmetic practice (Easy)', 'practice', 300, 'published'),
    -- course/python/quiz/practice/S03/medium
    ('351ca32d-028b-553f-a7a1-ba14bcc6fc2a', '68ea4540-e9da-5605-9054-28b29afa0992', 'Numbers and Arithmetic practice (Medium)', 'practice', 300, 'published'),
    -- course/python/quiz/practice/S03/hard
    ('485be76e-4294-5af2-8420-52b4f6d7660e', '68ea4540-e9da-5605-9054-28b29afa0992', 'Numbers and Arithmetic practice (Hard)', 'practice', 300, 'published'),
    -- course/python/quiz/practice/S04/easy
    ('d6643654-1baf-5461-9409-86c866284d7e', '68ea4540-e9da-5605-9054-28b29afa0992', 'Strings practice (Easy)', 'practice', 300, 'published'),
    -- course/python/quiz/practice/S04/medium
    ('8253563d-ec3c-57a5-b2a8-3a81543a2356', '68ea4540-e9da-5605-9054-28b29afa0992', 'Strings practice (Medium)', 'practice', 300, 'published'),
    -- course/python/quiz/practice/S04/hard
    ('79dd541f-1f5d-585d-b43f-0e1de79ce50e', '68ea4540-e9da-5605-9054-28b29afa0992', 'Strings practice (Hard)', 'practice', 300, 'published'),
    -- course/python/quiz/practice/S05/easy
    ('18636ba5-2907-5d91-824c-b14985a01c41', '68ea4540-e9da-5605-9054-28b29afa0992', 'Input and Type Conversion practice (Easy)', 'practice', 300, 'published'),
    -- course/python/quiz/practice/S05/medium
    ('937452b1-0ad7-523f-84a8-cf108ef9f0f8', '68ea4540-e9da-5605-9054-28b29afa0992', 'Input and Type Conversion practice (Medium)', 'practice', 300, 'published'),
    -- course/python/quiz/practice/S05/hard
    ('97c0e7b1-09d5-56c4-82a0-984bdb7f53e1', '68ea4540-e9da-5605-9054-28b29afa0992', 'Input and Type Conversion practice (Hard)', 'practice', 300, 'published'),
    -- course/python/quiz/practice/S06/easy
    ('621d5c58-a997-53a5-a313-79dcd4280295', '68ea4540-e9da-5605-9054-28b29afa0992', 'Booleans and Comparisons practice (Easy)', 'practice', 300, 'published'),
    -- course/python/quiz/practice/S06/medium
    ('9ad5f260-5dc2-5de2-901b-4d9770e2ad82', '68ea4540-e9da-5605-9054-28b29afa0992', 'Booleans and Comparisons practice (Medium)', 'practice', 300, 'published'),
    -- course/python/quiz/practice/S06/hard
    ('357b3ab0-577f-5cd0-a08d-5e845e30dfde', '68ea4540-e9da-5605-9054-28b29afa0992', 'Booleans and Comparisons practice (Hard)', 'practice', 300, 'published'),
    -- course/python/quiz/practice/S07/easy
    ('38a65cd8-d739-5ff5-b82d-35112b54ac2b', '68ea4540-e9da-5605-9054-28b29afa0992', 'Conditionals practice (Easy)', 'practice', 300, 'published'),
    -- course/python/quiz/practice/S07/medium
    ('6f9fafe1-d02c-5c1f-8bb1-7a52359d9e9f', '68ea4540-e9da-5605-9054-28b29afa0992', 'Conditionals practice (Medium)', 'practice', 300, 'published'),
    -- course/python/quiz/practice/S07/hard
    ('8a314459-7ddf-5d19-baf7-1a3348dd0c8d', '68ea4540-e9da-5605-9054-28b29afa0992', 'Conditionals practice (Hard)', 'practice', 300, 'published'),
    -- course/python/quiz/practice/S08/easy
    ('bba1745a-26b6-5ff6-a52f-4291eac2139d', '68ea4540-e9da-5605-9054-28b29afa0992', 'While Loops practice (Easy)', 'practice', 300, 'published'),
    -- course/python/quiz/practice/S08/medium
    ('903f9238-9ebe-57c4-b6e6-6b32367ab613', '68ea4540-e9da-5605-9054-28b29afa0992', 'While Loops practice (Medium)', 'practice', 300, 'published'),
    -- course/python/quiz/practice/S08/hard
    ('92f2a561-776d-5c8b-bb9b-53c8e1e3ee43', '68ea4540-e9da-5605-9054-28b29afa0992', 'While Loops practice (Hard)', 'practice', 300, 'published'),
    -- course/python/quiz/practice/S09/easy
    ('534e6710-f439-5c8f-9b98-72d246c8ca41', '68ea4540-e9da-5605-9054-28b29afa0992', 'For Loops practice (Easy)', 'practice', 300, 'published'),
    -- course/python/quiz/practice/S09/medium
    ('ca947ac2-60e4-55b6-abb7-78c25a49d244', '68ea4540-e9da-5605-9054-28b29afa0992', 'For Loops practice (Medium)', 'practice', 300, 'published'),
    -- course/python/quiz/practice/S09/hard
    ('6333f000-e02d-5229-96cf-60423ea6ea7f', '68ea4540-e9da-5605-9054-28b29afa0992', 'For Loops practice (Hard)', 'practice', 300, 'published'),
    -- course/python/quiz/practice/S10/easy
    ('72489347-7cf3-58cc-9b4a-2e6d82be8145', '68ea4540-e9da-5605-9054-28b29afa0992', 'Loop Control practice (Easy)', 'practice', 300, 'published'),
    -- course/python/quiz/practice/S10/medium
    ('d4c5b03e-f456-56fa-adb2-4fea716e5e9e', '68ea4540-e9da-5605-9054-28b29afa0992', 'Loop Control practice (Medium)', 'practice', 300, 'published'),
    -- course/python/quiz/practice/S10/hard
    ('314bfb63-1356-5f9b-9e92-613be42bc43d', '68ea4540-e9da-5605-9054-28b29afa0992', 'Loop Control practice (Hard)', 'practice', 300, 'published'),
    -- course/python/quiz/practice/S11/easy
    ('49756cc7-4a37-59e3-bfc1-48367b1a37dd', '68ea4540-e9da-5605-9054-28b29afa0992', 'Lists practice (Easy)', 'practice', 300, 'published'),
    -- course/python/quiz/practice/S11/medium
    ('c8b1f3d2-8b87-59b6-a660-275e7eb1c6b3', '68ea4540-e9da-5605-9054-28b29afa0992', 'Lists practice (Medium)', 'practice', 300, 'published'),
    -- course/python/quiz/practice/S11/hard
    ('c921c7c7-b2ca-570a-8c1a-7469591c2e81', '68ea4540-e9da-5605-9054-28b29afa0992', 'Lists practice (Hard)', 'practice', 300, 'published'),
    -- course/python/quiz/practice/S12/easy
    ('c2791023-29a0-5e04-9301-32995a5dd9ed', '68ea4540-e9da-5605-9054-28b29afa0992', 'List Iteration Patterns practice (Easy)', 'practice', 300, 'published'),
    -- course/python/quiz/practice/S12/medium
    ('1b26619c-1d65-5b8f-b176-24aa5981fb4f', '68ea4540-e9da-5605-9054-28b29afa0992', 'List Iteration Patterns practice (Medium)', 'practice', 300, 'published'),
    -- course/python/quiz/practice/S12/hard
    ('19841ab6-ae11-5bc0-93e0-b92579a8610d', '68ea4540-e9da-5605-9054-28b29afa0992', 'List Iteration Patterns practice (Hard)', 'practice', 300, 'published'),
    -- course/python/quiz/practice/S13/easy
    ('37012a01-45eb-590a-901c-e490b9a87b40', '68ea4540-e9da-5605-9054-28b29afa0992', 'Tuples and Sets practice (Easy)', 'practice', 300, 'published'),
    -- course/python/quiz/practice/S13/medium
    ('4e0ec97f-2581-5fde-8a07-f326409d7b40', '68ea4540-e9da-5605-9054-28b29afa0992', 'Tuples and Sets practice (Medium)', 'practice', 300, 'published'),
    -- course/python/quiz/practice/S13/hard
    ('6a4a29ef-7fe1-5ce0-94ae-6444afedd8af', '68ea4540-e9da-5605-9054-28b29afa0992', 'Tuples and Sets practice (Hard)', 'practice', 300, 'published'),
    -- course/python/quiz/practice/S14/easy
    ('9e231fcc-d5f4-52d4-9e97-40a1d5d54052', '68ea4540-e9da-5605-9054-28b29afa0992', 'Dictionaries practice (Easy)', 'practice', 300, 'published'),
    -- course/python/quiz/practice/S14/medium
    ('96ae8dc1-a8b1-5bf3-8d35-b359ed40f1fb', '68ea4540-e9da-5605-9054-28b29afa0992', 'Dictionaries practice (Medium)', 'practice', 300, 'published'),
    -- course/python/quiz/practice/S14/hard
    ('c168fe41-e03c-5297-b5af-9a878aae97bd', '68ea4540-e9da-5605-9054-28b29afa0992', 'Dictionaries practice (Hard)', 'practice', 300, 'published'),
    -- course/python/quiz/practice/S15/easy
    ('a29ed7e2-7933-537e-9125-16972a3e3685', '68ea4540-e9da-5605-9054-28b29afa0992', 'Functions practice (Easy)', 'practice', 300, 'published'),
    -- course/python/quiz/practice/S15/medium
    ('cc84c519-7fa2-5cc2-9e18-19138ec86c64', '68ea4540-e9da-5605-9054-28b29afa0992', 'Functions practice (Medium)', 'practice', 300, 'published'),
    -- course/python/quiz/practice/S15/hard
    ('28780356-feb7-5e9b-83bb-fba1dc058867', '68ea4540-e9da-5605-9054-28b29afa0992', 'Functions practice (Hard)', 'practice', 300, 'published'),
    -- course/python/quiz/practice/S16/easy
    ('ac020bb2-4527-540c-9b2f-babe45968cb7', '68ea4540-e9da-5605-9054-28b29afa0992', 'Parameters and Return Values practice (Easy)', 'practice', 300, 'published'),
    -- course/python/quiz/practice/S16/medium
    ('af68d0c1-8fcd-5fb0-92a6-c5a17e3397b3', '68ea4540-e9da-5605-9054-28b29afa0992', 'Parameters and Return Values practice (Medium)', 'practice', 300, 'published'),
    -- course/python/quiz/practice/S16/hard
    ('660e11b3-443d-56b7-8bc4-c2915e460ac4', '68ea4540-e9da-5605-9054-28b29afa0992', 'Parameters and Return Values practice (Hard)', 'practice', 300, 'published'),
    -- course/python/quiz/practice/S17/easy
    ('ce0df680-e8df-5508-95d8-bfb9f613ece4', '68ea4540-e9da-5605-9054-28b29afa0992', 'Variable Scope practice (Easy)', 'practice', 300, 'published'),
    -- course/python/quiz/practice/S17/medium
    ('b6bad63c-e059-5d20-b24e-5cc723479be4', '68ea4540-e9da-5605-9054-28b29afa0992', 'Variable Scope practice (Medium)', 'practice', 300, 'published'),
    -- course/python/quiz/practice/S17/hard
    ('92249c0e-3f0d-5480-ad08-450108f6b637', '68ea4540-e9da-5605-9054-28b29afa0992', 'Variable Scope practice (Hard)', 'practice', 300, 'published'),
    -- course/python/quiz/practice/S18/easy
    ('0e460e4f-0888-598e-990c-eedc2592ae06', '68ea4540-e9da-5605-9054-28b29afa0992', 'String Methods and Formatting practice (Easy)', 'practice', 300, 'published'),
    -- course/python/quiz/practice/S18/medium
    ('9124000a-7784-5639-9f11-d044cd99c048', '68ea4540-e9da-5605-9054-28b29afa0992', 'String Methods and Formatting practice (Medium)', 'practice', 300, 'published'),
    -- course/python/quiz/practice/S18/hard
    ('cb2afc36-705a-5331-a750-871798586a54', '68ea4540-e9da-5605-9054-28b29afa0992', 'String Methods and Formatting practice (Hard)', 'practice', 300, 'published'),
    -- course/python/quiz/practice/S19/easy
    ('8acece65-3a78-5f5d-9c51-9378e9113c6c', '68ea4540-e9da-5605-9054-28b29afa0992', 'Exceptions practice (Easy)', 'practice', 300, 'published'),
    -- course/python/quiz/practice/S19/medium
    ('d2017e65-0935-5a00-9673-1df5abe78018', '68ea4540-e9da-5605-9054-28b29afa0992', 'Exceptions practice (Medium)', 'practice', 300, 'published'),
    -- course/python/quiz/practice/S19/hard
    ('8f759f3e-0d73-5fbd-abc8-febca82deb6a', '68ea4540-e9da-5605-9054-28b29afa0992', 'Exceptions practice (Hard)', 'practice', 300, 'published'),
    -- course/python/quiz/practice/S20/easy
    ('9a89f795-8bf5-5a0e-ac84-415a09366ecf', '68ea4540-e9da-5605-9054-28b29afa0992', 'File Input and Output practice (Easy)', 'practice', 300, 'published'),
    -- course/python/quiz/practice/S20/medium
    ('d865b335-183c-56ea-a7d5-39d97cdb521c', '68ea4540-e9da-5605-9054-28b29afa0992', 'File Input and Output practice (Medium)', 'practice', 300, 'published'),
    -- course/python/quiz/practice/S20/hard
    ('8d2a8290-ded1-56ff-a23b-8f8bb030f367', '68ea4540-e9da-5605-9054-28b29afa0992', 'File Input and Output practice (Hard)', 'practice', 300, 'published'),
    -- course/python/quiz/final-test
    ('544c488f-e521-55d2-9744-51b92ad8e8da', '68ea4540-e9da-5605-9054-28b29afa0992', 'Python Fundamentals Final Test', 'test', 2400, 'published');

CREATE TEMP TABLE sd04_quiz_question_seed (
    id uuid NOT NULL,
    quiz_id uuid NOT NULL,
    question_id uuid NOT NULL,
    position integer NOT NULL
) ON COMMIT DROP;

INSERT INTO sd04_quiz_question_seed (id, quiz_id, question_id, position)
VALUES
    -- course/python/quiz/placement/question/1
    ('ced2507c-ecda-5e3d-bde4-c71b3b7b40bb', '4737c62b-77c2-5951-9f97-0430d02e153f', '9c3ac0be-d86d-5271-b27d-a07149066035', 1),
    -- course/python/quiz/placement/question/2
    ('1ae0cfea-4717-5e80-9dd2-95f6e5b056b0', '4737c62b-77c2-5951-9f97-0430d02e153f', 'b358748e-fafe-58fe-b2df-d9871c4f6d3f', 2),
    -- course/python/quiz/placement/question/3
    ('efd71918-4bf8-5ed9-b05f-341a94f77116', '4737c62b-77c2-5951-9f97-0430d02e153f', 'bf18cc06-62d6-57a9-b1e6-f3606927bfb3', 3),
    -- course/python/quiz/placement/question/4
    ('b2136bab-5263-5b42-9f1a-89acaa0b3f10', '4737c62b-77c2-5951-9f97-0430d02e153f', '289c2492-5d9d-5c7f-91d9-4f5aaa71e0d0', 4),
    -- course/python/quiz/placement/question/5
    ('15159994-2eb8-5b57-8e80-51ac1b679017', '4737c62b-77c2-5951-9f97-0430d02e153f', 'a0aa03ca-5c53-5d68-bca3-3db07691f501', 5),
    -- course/python/quiz/placement/question/6
    ('b8d14e31-74ee-5193-8085-0171ee2babfa', '4737c62b-77c2-5951-9f97-0430d02e153f', 'd7438085-a880-5520-abaf-97e51e6351b4', 6),
    -- course/python/quiz/placement/question/7
    ('1aff878a-21e3-5073-9dfa-cbb1a0afa557', '4737c62b-77c2-5951-9f97-0430d02e153f', '5902205d-e312-5812-99c7-c15ffd1b4722', 7),
    -- course/python/quiz/placement/question/8
    ('2c7e9f23-67f4-5875-8ade-8ba95ee51a50', '4737c62b-77c2-5951-9f97-0430d02e153f', '1cd39950-3e19-53b4-a540-15001521ef4f', 8),
    -- course/python/quiz/placement/question/9
    ('545d8ddb-7a4c-52c4-ae43-bddb7e9a4d5a', '4737c62b-77c2-5951-9f97-0430d02e153f', '66f85ef9-185a-505e-ba77-6171a39a5b46', 9),
    -- course/python/quiz/placement/question/10
    ('53fc7951-c62d-582c-b97d-d3dd12528204', '4737c62b-77c2-5951-9f97-0430d02e153f', '0a113214-8bb3-500f-8580-5919e14691a2', 10),
    -- course/python/quiz/placement/question/11
    ('66899a6a-d40f-5860-8d9c-0d0d0a2bed1d', '4737c62b-77c2-5951-9f97-0430d02e153f', 'c31da348-74a2-5c9a-9731-a44cf67887e6', 11),
    -- course/python/quiz/placement/question/12
    ('9e6b3f14-d9bc-5ca9-8e74-2fe2d062538e', '4737c62b-77c2-5951-9f97-0430d02e153f', '2b405b34-5384-53d4-a212-2be28a38740f', 12),
    -- course/python/quiz/placement/question/13
    ('e9d6d749-d211-56c3-b9d2-8af2f87299f2', '4737c62b-77c2-5951-9f97-0430d02e153f', 'c0730e76-14b4-5000-bd79-240f4f7f3b0d', 13),
    -- course/python/quiz/placement/question/14
    ('4a052688-a655-5337-b09f-8bed6a076b0e', '4737c62b-77c2-5951-9f97-0430d02e153f', '66460d0a-e09e-5783-8377-79ef8093ccc3', 14),
    -- course/python/quiz/placement/question/15
    ('e019c844-9e21-5455-8f68-56849f894c3a', '4737c62b-77c2-5951-9f97-0430d02e153f', '341fa1fa-e9c1-515f-b6bd-fe7202160556', 15),
    -- course/python/quiz/placement/question/16
    ('4f52ab56-031f-53da-bb65-1efca6b77999', '4737c62b-77c2-5951-9f97-0430d02e153f', '7c7ac83d-ccd2-53aa-a012-6bd42e9de871', 16),
    -- course/python/quiz/placement/question/17
    ('0595f342-77dd-50b7-a02f-8fb2b7bf07ce', '4737c62b-77c2-5951-9f97-0430d02e153f', '75b6e65e-f2f6-502d-89b0-deb920af668d', 17),
    -- course/python/quiz/placement/question/18
    ('bad37a19-5796-5239-a95c-a6a6087b435a', '4737c62b-77c2-5951-9f97-0430d02e153f', 'cf774e61-3bff-54f9-a1eb-6ad212f0f68b', 18),
    -- course/python/quiz/placement/question/19
    ('5def1ace-75db-5f74-85b2-2e3b24e1c3e9', '4737c62b-77c2-5951-9f97-0430d02e153f', '34d0d846-31f6-5da6-9781-7935f33a4bd6', 19),
    -- course/python/quiz/placement/question/20
    ('4d8ef742-b44d-5c94-88dd-2835a409f639', '4737c62b-77c2-5951-9f97-0430d02e153f', '7d295949-5089-5990-97f2-6c53079c1126', 20),
    -- course/python/quiz/practice/S01/question/1
    ('3e0d1709-2312-5189-9f99-6287d3a83eb4', '94074861-7b0a-5e8e-bd61-1ac97784f53b', '9c3ac0be-d86d-5271-b27d-a07149066035', 1),
    -- course/python/quiz/practice/S01/question/2
    ('dd4c1f36-0d13-505c-be2b-f8b80fc85064', '94074861-7b0a-5e8e-bd61-1ac97784f53b', 'd50e0753-9528-58cc-98cd-b0b971ca31d9', 2),
    -- course/python/quiz/practice/S01/question/3
    ('fc6b84da-949c-5341-86bb-1710488ab40a', '94074861-7b0a-5e8e-bd61-1ac97784f53b', 'b358748e-fafe-58fe-b2df-d9871c4f6d3f', 3),
    -- course/python/quiz/practice/S01/question/4
    ('a17b6adc-588a-55f4-82e9-6ffaf7ed5a60', '94074861-7b0a-5e8e-bd61-1ac97784f53b', 'f1e8f1bc-ce65-5101-a6e9-438b31892750', 4),
    -- course/python/quiz/practice/S01/question/5
    ('30098365-bd1a-504e-83c6-7a538683f39c', '94074861-7b0a-5e8e-bd61-1ac97784f53b', '53b89cbd-52d7-5d3b-8a46-5644e2362274', 5),
    -- course/python/quiz/practice/S01/question/6
    ('57ccf750-b913-591d-9362-e86b0efeefe5', '94074861-7b0a-5e8e-bd61-1ac97784f53b', 'b47b5206-4b65-5291-8d27-16505cb90268', 6),
    -- course/python/quiz/practice/S02/question/1
    ('95d71721-3d46-5de4-9c55-227783ff7d8b', 'bafc0c7a-abf4-5dda-ae1f-15f4e5859520', 'bf18cc06-62d6-57a9-b1e6-f3606927bfb3', 1),
    -- course/python/quiz/practice/S02/question/2
    ('8c959825-4495-5027-8695-f0298bfca8de', 'bafc0c7a-abf4-5dda-ae1f-15f4e5859520', '1028cb3f-1a77-5749-8fa0-c748d4ba12a5', 2),
    -- course/python/quiz/practice/S02/question/3
    ('f8da6674-a716-52ba-9da6-82df05b3e4a1', 'bafc0c7a-abf4-5dda-ae1f-15f4e5859520', '289c2492-5d9d-5c7f-91d9-4f5aaa71e0d0', 3),
    -- course/python/quiz/practice/S02/question/4
    ('83d416b8-ea2a-58c2-b418-2602d0518df3', 'bafc0c7a-abf4-5dda-ae1f-15f4e5859520', 'd120a564-84e4-543a-9f8e-d1e190f80b7b', 4),
    -- course/python/quiz/practice/S02/question/5
    ('35962246-c122-5e82-b2fa-2958f0a71537', 'bafc0c7a-abf4-5dda-ae1f-15f4e5859520', 'd2eedd97-a799-5f1c-b2cb-39edee8c6b0e', 5),
    -- course/python/quiz/practice/S02/question/6
    ('1e9e19ed-4b52-52a8-9f9d-0956084f8484', 'bafc0c7a-abf4-5dda-ae1f-15f4e5859520', 'f0b39c73-70e6-5571-9727-75b5e626d0dd', 6),
    -- course/python/quiz/practice/S03/question/1
    ('395e07bb-b7f7-524b-9002-e965e7eb392c', 'a148db7d-a2e7-581c-b1b7-86fdcafd31a5', 'a0aa03ca-5c53-5d68-bca3-3db07691f501', 1),
    -- course/python/quiz/practice/S03/question/2
    ('453786c3-8bef-5e8b-a3f0-abcfdf53643c', 'a148db7d-a2e7-581c-b1b7-86fdcafd31a5', 'f0f24b93-984f-5876-89f8-835c049bb8ac', 2),
    -- course/python/quiz/practice/S03/question/3
    ('113254b3-f60b-56b6-b3b3-e3f013cc3b6a', 'a148db7d-a2e7-581c-b1b7-86fdcafd31a5', 'd7438085-a880-5520-abaf-97e51e6351b4', 3),
    -- course/python/quiz/practice/S03/question/4
    ('976fbd17-212b-5d34-a2c0-1c8713463ba6', 'a148db7d-a2e7-581c-b1b7-86fdcafd31a5', '4b09d392-34fe-5e09-a893-45cf07d8a3d7', 4),
    -- course/python/quiz/practice/S03/question/5
    ('9efdaae0-9e03-56d6-8b10-7c7965ab10db', 'a148db7d-a2e7-581c-b1b7-86fdcafd31a5', '9295bb15-c299-5795-bce7-ea13648f734b', 5),
    -- course/python/quiz/practice/S03/question/6
    ('5d38849d-7be6-5f5f-8a6b-b6e7e9427f7f', 'a148db7d-a2e7-581c-b1b7-86fdcafd31a5', '226975a1-55e0-5bb7-bffe-ab4ccdaaa20f', 6),
    -- course/python/quiz/practice/S04/question/1
    ('20eb6ca0-c6d0-52d8-a115-871d19a2abe8', '16072f31-8bb1-5224-807d-4dc81d208710', '5902205d-e312-5812-99c7-c15ffd1b4722', 1),
    -- course/python/quiz/practice/S04/question/2
    ('510fd8e2-d584-553b-a7c1-97e1039ee4cb', '16072f31-8bb1-5224-807d-4dc81d208710', '5344ddbb-7770-5332-8e0d-2961fb7d30e9', 2),
    -- course/python/quiz/practice/S04/question/3
    ('a1f13f4f-ca1f-5cc4-b565-c0d36df35680', '16072f31-8bb1-5224-807d-4dc81d208710', '1cd39950-3e19-53b4-a540-15001521ef4f', 3),
    -- course/python/quiz/practice/S04/question/4
    ('02db3060-5cb4-5416-b316-27aa3e73baaa', '16072f31-8bb1-5224-807d-4dc81d208710', '8972206b-e768-5f13-9a02-ac5edd2fecf5', 4),
    -- course/python/quiz/practice/S04/question/5
    ('ea2b82d0-329b-56f5-8b0c-02a733f1faea', '16072f31-8bb1-5224-807d-4dc81d208710', '46085bea-1eb5-5c3b-bbee-4b2653c3bf14', 5),
    -- course/python/quiz/practice/S04/question/6
    ('11472724-05b4-58c2-b50a-87c859e33277', '16072f31-8bb1-5224-807d-4dc81d208710', 'cbc8f9c2-c540-5511-b46c-841684a2cf84', 6),
    -- course/python/quiz/practice/S05/question/1
    ('9d1b5fff-9015-52b8-a403-cc18dc1ae1ec', 'a3e9a33c-5726-5951-a61a-0b50997787d8', '66f85ef9-185a-505e-ba77-6171a39a5b46', 1),
    -- course/python/quiz/practice/S05/question/2
    ('a7bafd3a-9eeb-570c-b456-f7b47e1d45fe', 'a3e9a33c-5726-5951-a61a-0b50997787d8', '19789354-82db-568c-b1ba-155e5ac72e85', 2),
    -- course/python/quiz/practice/S05/question/3
    ('ef68c62a-80f5-5d66-a2c8-23e60388c43d', 'a3e9a33c-5726-5951-a61a-0b50997787d8', '0a113214-8bb3-500f-8580-5919e14691a2', 3),
    -- course/python/quiz/practice/S05/question/4
    ('762e3441-1573-5fc0-8c99-a1f6d85bcac4', 'a3e9a33c-5726-5951-a61a-0b50997787d8', 'c875e997-f3b3-5a83-93d3-a5c33daa4a00', 4),
    -- course/python/quiz/practice/S05/question/5
    ('929c3d08-03e8-5657-9d6f-ac51ec457601', 'a3e9a33c-5726-5951-a61a-0b50997787d8', '48e9b47e-c2aa-53f5-938d-276c6b805f9e', 5),
    -- course/python/quiz/practice/S05/question/6
    ('88579d61-8586-5bd5-8bf1-d38b66980f61', 'a3e9a33c-5726-5951-a61a-0b50997787d8', 'e2450614-7fe8-5e0b-a571-0bdfee838601', 6),
    -- course/python/quiz/practice/S06/question/1
    ('b9bf219e-086c-5faa-9dd8-f56696cf2f5d', 'ea08ccdc-f915-5ccb-92fb-ec79374f51b5', 'c31da348-74a2-5c9a-9731-a44cf67887e6', 1),
    -- course/python/quiz/practice/S06/question/2
    ('b4cb7cd2-3c74-544b-94cd-58a9139372f2', 'ea08ccdc-f915-5ccb-92fb-ec79374f51b5', '7fcc92f1-63dd-506e-a38f-003ff8f23179', 2),
    -- course/python/quiz/practice/S06/question/3
    ('794c3540-4932-5740-be08-a038ebe65e7b', 'ea08ccdc-f915-5ccb-92fb-ec79374f51b5', '2b405b34-5384-53d4-a212-2be28a38740f', 3),
    -- course/python/quiz/practice/S06/question/4
    ('4d307c90-2e51-5aea-8430-974743f3698e', 'ea08ccdc-f915-5ccb-92fb-ec79374f51b5', 'c4d632e1-f6ed-5509-8844-0967e9f6397e', 4),
    -- course/python/quiz/practice/S06/question/5
    ('7cac4431-fa5e-5fb9-b7df-f08f45b19f86', 'ea08ccdc-f915-5ccb-92fb-ec79374f51b5', '322fb12c-c7bf-522c-ac4d-2a333f606173', 5),
    -- course/python/quiz/practice/S06/question/6
    ('4243c215-21fe-56a1-a3d7-9d751076f2e8', 'ea08ccdc-f915-5ccb-92fb-ec79374f51b5', '6019ed9f-42b0-53f2-a367-7e0e6d2e7c11', 6),
    -- course/python/quiz/practice/S07/question/1
    ('b7608b65-070e-5ed0-9edd-edda262a2945', 'b0d68d0a-64b0-599d-8d29-3047a7cc58c3', 'c0730e76-14b4-5000-bd79-240f4f7f3b0d', 1),
    -- course/python/quiz/practice/S07/question/2
    ('f1e9ac4d-bc94-529b-b8be-bbce757c0e1d', 'b0d68d0a-64b0-599d-8d29-3047a7cc58c3', 'e21daba6-38f8-5d78-b9d5-d006fab32a7f', 2),
    -- course/python/quiz/practice/S07/question/3
    ('caa07cb2-39d6-5d1f-a231-b048931da9d6', 'b0d68d0a-64b0-599d-8d29-3047a7cc58c3', '66460d0a-e09e-5783-8377-79ef8093ccc3', 3),
    -- course/python/quiz/practice/S07/question/4
    ('ba6ed6a3-247a-5618-b5ba-12a60216637b', 'b0d68d0a-64b0-599d-8d29-3047a7cc58c3', 'aa4592a5-457f-510a-9760-151aa800bab0', 4),
    -- course/python/quiz/practice/S07/question/5
    ('fb97a147-3b41-596e-9ba1-012444a5cd25', 'b0d68d0a-64b0-599d-8d29-3047a7cc58c3', '9c4197f7-3435-50f9-a8b6-9cb4fca0a41a', 5),
    -- course/python/quiz/practice/S07/question/6
    ('d77d0432-10eb-54d6-82f1-a4cc5789e0ee', 'b0d68d0a-64b0-599d-8d29-3047a7cc58c3', 'f47c6be3-3098-5daf-a319-dbd2d57f5feb', 6),
    -- course/python/quiz/practice/S08/question/1
    ('30e2450d-16bc-5ea9-a539-41b438327e96', '081b5603-882d-5503-af8c-eda46c5d9052', '341fa1fa-e9c1-515f-b6bd-fe7202160556', 1),
    -- course/python/quiz/practice/S08/question/2
    ('beb1de1e-1a9d-5b6d-a76e-e5bf41dad78d', '081b5603-882d-5503-af8c-eda46c5d9052', 'af8beb6f-98d9-5124-b8cc-5fb248f3f8fb', 2),
    -- course/python/quiz/practice/S08/question/3
    ('6a9b17c4-53c2-5cc5-8071-5442c4d13d64', '081b5603-882d-5503-af8c-eda46c5d9052', '7c7ac83d-ccd2-53aa-a012-6bd42e9de871', 3),
    -- course/python/quiz/practice/S08/question/4
    ('c6cc6b2a-337a-5444-92df-db7e908445dd', '081b5603-882d-5503-af8c-eda46c5d9052', 'a36de1fc-fd13-50b4-9c6c-779dff8392f8', 4),
    -- course/python/quiz/practice/S08/question/5
    ('6d77dfd8-c738-5bdb-8bbe-f80fc6f8dfae', '081b5603-882d-5503-af8c-eda46c5d9052', '9fe24547-5ac5-5fe2-8740-73aa7d91d528', 5),
    -- course/python/quiz/practice/S08/question/6
    ('bcb06b77-407d-5957-8eb1-6bc5ec55e2ed', '081b5603-882d-5503-af8c-eda46c5d9052', '4de3396b-56f4-5758-a9f0-5b3c95eb0d6f', 6),
    -- course/python/quiz/practice/S09/question/1
    ('a7dc2a93-2e92-561d-88a3-400713df6942', 'd0abe6a3-f1aa-5809-a3b3-71f732746490', '75b6e65e-f2f6-502d-89b0-deb920af668d', 1),
    -- course/python/quiz/practice/S09/question/2
    ('7747fec2-5959-5e02-823a-b06fecf6dd0e', 'd0abe6a3-f1aa-5809-a3b3-71f732746490', '97a7aaaf-ccca-54b2-8dbc-426d15c2ed88', 2),
    -- course/python/quiz/practice/S09/question/3
    ('b98a8a42-93cf-57bf-a368-62de5aa972a8', 'd0abe6a3-f1aa-5809-a3b3-71f732746490', 'cf774e61-3bff-54f9-a1eb-6ad212f0f68b', 3),
    -- course/python/quiz/practice/S09/question/4
    ('d21a1b88-831d-5f8c-9142-c11201589eac', 'd0abe6a3-f1aa-5809-a3b3-71f732746490', '0c587264-59c8-5734-af9d-a51d426e70c7', 4),
    -- course/python/quiz/practice/S09/question/5
    ('0872ca51-e567-53b5-8033-f7357bf62150', 'd0abe6a3-f1aa-5809-a3b3-71f732746490', '55777aea-ea16-5b83-8d38-f6ceca56f246', 5),
    -- course/python/quiz/practice/S09/question/6
    ('f2e7694f-54a1-5f12-b83b-dc3674af7628', 'd0abe6a3-f1aa-5809-a3b3-71f732746490', '6caa041a-ce9e-5398-970f-6dfa3a9995f7', 6),
    -- course/python/quiz/practice/S10/question/1
    ('9f8a1b7e-e01e-5739-b553-838b37547dbd', '0748319a-a4e3-5fbc-b3c3-3091aa44b2f9', '34d0d846-31f6-5da6-9781-7935f33a4bd6', 1),
    -- course/python/quiz/practice/S10/question/2
    ('9497cc53-1d38-5b86-8664-1a5d0db7a606', '0748319a-a4e3-5fbc-b3c3-3091aa44b2f9', 'a4dbf079-8d61-51c5-8b5d-cf96357210e4', 2),
    -- course/python/quiz/practice/S10/question/3
    ('11139907-6689-51d5-8d94-bc59e324bfe0', '0748319a-a4e3-5fbc-b3c3-3091aa44b2f9', '7d295949-5089-5990-97f2-6c53079c1126', 3),
    -- course/python/quiz/practice/S10/question/4
    ('081d6f0b-bd81-5517-97fe-4f3f290db5a4', '0748319a-a4e3-5fbc-b3c3-3091aa44b2f9', 'eca786ea-ae10-5227-9b51-63c09d8854d2', 4),
    -- course/python/quiz/practice/S10/question/5
    ('07378812-cf29-5e90-91ee-413565a2eae8', '0748319a-a4e3-5fbc-b3c3-3091aa44b2f9', '7693f9a0-e5b8-5f16-b150-4466d03d0d1b', 5),
    -- course/python/quiz/practice/S10/question/6
    ('5df30f8d-84c2-5631-86e7-ce38ef3e8458', '0748319a-a4e3-5fbc-b3c3-3091aa44b2f9', '0cb404b3-4d87-5d9f-ac9b-52a9dc22e0af', 6),
    -- course/python/quiz/practice/S11/question/1
    ('7642cb4d-99f0-5719-a7b1-6bff5e65bc68', 'c85ed207-9c84-50ba-b386-5a725b8095a3', 'a2a5de7e-7f95-5a16-9868-d847c8adce7f', 1),
    -- course/python/quiz/practice/S11/question/2
    ('fd6ac728-d942-5af7-b519-59d30de1e761', 'c85ed207-9c84-50ba-b386-5a725b8095a3', '1a324327-c0e9-5345-ac2f-25dd5e411354', 2),
    -- course/python/quiz/practice/S11/question/3
    ('9ea62b48-f5ff-56ea-840e-04ddd957bbf5', 'c85ed207-9c84-50ba-b386-5a725b8095a3', '7b4177a9-797b-547e-aa80-e5b07496ddcd', 3),
    -- course/python/quiz/practice/S11/question/4
    ('ee93f38b-1826-595c-a32d-bfbd603d4446', 'c85ed207-9c84-50ba-b386-5a725b8095a3', '2059a88e-892b-5337-9795-0567ea98b4fb', 4),
    -- course/python/quiz/practice/S11/question/5
    ('c0628ae9-b923-5d99-bdb4-b16487f52c3b', 'c85ed207-9c84-50ba-b386-5a725b8095a3', '3ec37a02-cf3f-525e-9196-f04d2cc31b3d', 5),
    -- course/python/quiz/practice/S11/question/6
    ('792c7816-22f9-55f2-9e7d-ca88fe436bd3', 'c85ed207-9c84-50ba-b386-5a725b8095a3', '2b58516f-d75c-52dc-9798-8e0e3ab7470e', 6),
    -- course/python/quiz/practice/S12/question/1
    ('e7176d8c-8149-52db-8c1c-e2abd1a3be05', 'fb5c0466-d1fb-5c60-bad4-21c7c48a5d1c', '88720a00-57e1-5ed5-9f78-757c314635f6', 1),
    -- course/python/quiz/practice/S12/question/2
    ('ffeeac37-e909-52fe-99c7-e76addf99d7c', 'fb5c0466-d1fb-5c60-bad4-21c7c48a5d1c', '61f0c107-34c8-51cf-a88c-235927c0540e', 2),
    -- course/python/quiz/practice/S12/question/3
    ('0a20f524-2f5c-5aaf-a5b1-57743b4cccfb', 'fb5c0466-d1fb-5c60-bad4-21c7c48a5d1c', 'b234ed5b-1cdf-51ad-b472-19ddfce0a465', 3),
    -- course/python/quiz/practice/S12/question/4
    ('c567ef6a-0c08-5530-a047-2d374bc9ae70', 'fb5c0466-d1fb-5c60-bad4-21c7c48a5d1c', '22afbbe2-3c43-52d1-b661-ebe867e13f8e', 4),
    -- course/python/quiz/practice/S12/question/5
    ('e286050c-677d-5090-9fdb-3f53896865f6', 'fb5c0466-d1fb-5c60-bad4-21c7c48a5d1c', 'a319563c-3f22-567a-8777-f5ec21389064', 5),
    -- course/python/quiz/practice/S12/question/6
    ('469f9220-6ae4-55eb-b28e-0eea2b05caa1', 'fb5c0466-d1fb-5c60-bad4-21c7c48a5d1c', 'd0e801ff-da73-5cea-aff2-d1e680653265', 6),
    -- course/python/quiz/practice/S13/question/1
    ('4afbf759-6467-5031-9a06-692526aa8367', '073e29a0-8484-50ff-94b9-5b312c985535', 'b21c2f55-0227-5a64-b890-c77a7904f7a3', 1),
    -- course/python/quiz/practice/S13/question/2
    ('44d59ccf-913f-5324-b482-8f852fec6b5e', '073e29a0-8484-50ff-94b9-5b312c985535', 'd0c983be-a6f5-5de3-869b-795570414af2', 2),
    -- course/python/quiz/practice/S13/question/3
    ('eaeea3ac-c80c-51ac-bd78-f5d633a8c492', '073e29a0-8484-50ff-94b9-5b312c985535', 'e6f6567c-6ff8-58ae-a4ef-2492679faef6', 3),
    -- course/python/quiz/practice/S13/question/4
    ('1f346be6-4ad5-524c-8b47-445622fc947c', '073e29a0-8484-50ff-94b9-5b312c985535', '902cefa8-405a-5a25-9064-87e289ed86d5', 4),
    -- course/python/quiz/practice/S13/question/5
    ('45a177f0-71d0-52d2-a46c-9d21b564de4a', '073e29a0-8484-50ff-94b9-5b312c985535', '919fb0bd-d4e3-5723-865b-7dd3baa8a3dc', 5),
    -- course/python/quiz/practice/S13/question/6
    ('314b4b29-62d9-55a4-a213-9f45046a5336', '073e29a0-8484-50ff-94b9-5b312c985535', '3d322f31-d12e-5474-b92c-4292c01debef', 6),
    -- course/python/quiz/practice/S14/question/1
    ('87c3074c-999e-572a-9615-8919a2d2e8cb', 'ebd78c4e-0655-53c7-8ffb-eae82a309594', '92bf5207-b9a3-55f4-a59d-925bac4f44ab', 1),
    -- course/python/quiz/practice/S14/question/2
    ('ee613f40-5b8c-52cf-8753-6e9e438d02ca', 'ebd78c4e-0655-53c7-8ffb-eae82a309594', 'ce9eb7c5-b6e1-56f8-bc5d-f96fd6566f11', 2),
    -- course/python/quiz/practice/S14/question/3
    ('67c2906f-f7e8-56a4-8293-61f69ce34eea', 'ebd78c4e-0655-53c7-8ffb-eae82a309594', '7632e4ca-e31c-5547-b89d-15a11ec9504d', 3),
    -- course/python/quiz/practice/S14/question/4
    ('9136f67e-8581-5cc7-bbea-bd3e3309f6f3', 'ebd78c4e-0655-53c7-8ffb-eae82a309594', 'b538764c-cf6d-5c5d-96b7-f40c1b8cb58b', 4),
    -- course/python/quiz/practice/S14/question/5
    ('76b24fa9-1819-53d7-aed0-433e02ecbf6a', 'ebd78c4e-0655-53c7-8ffb-eae82a309594', '69cb172b-9754-5778-98ae-963cbbad9b31', 5),
    -- course/python/quiz/practice/S14/question/6
    ('5da902b9-8977-5ad7-95c1-946525d63470', 'ebd78c4e-0655-53c7-8ffb-eae82a309594', 'd3fce7dd-084c-57fc-9b8d-c4a20fb5c06f', 6),
    -- course/python/quiz/practice/S15/question/1
    ('e60e407c-ed4d-5b0c-a59d-3b26d3963c61', 'be689582-a765-5be2-8a7c-6f7eb6b1cfdc', '0fb4d8be-aceb-57f3-983c-77a57bb9f177', 1),
    -- course/python/quiz/practice/S15/question/2
    ('d92038d8-388a-55ea-84a6-c7db6811be47', 'be689582-a765-5be2-8a7c-6f7eb6b1cfdc', '6cf13941-0a1c-50fe-94b6-06aa636e9ff8', 2),
    -- course/python/quiz/practice/S15/question/3
    ('df1eafb7-df31-5606-8373-ca9fdb7f5473', 'be689582-a765-5be2-8a7c-6f7eb6b1cfdc', '79a4b286-881f-5770-954c-4e7645c2d2f0', 3),
    -- course/python/quiz/practice/S15/question/4
    ('45676d9e-f19a-5621-8db0-61960f72afba', 'be689582-a765-5be2-8a7c-6f7eb6b1cfdc', 'adcb795b-722f-5a06-9eb6-5611455f69cd', 4),
    -- course/python/quiz/practice/S15/question/5
    ('474bd179-6613-52fb-a3b0-e4b62888b47c', 'be689582-a765-5be2-8a7c-6f7eb6b1cfdc', '44a7142f-cd4c-53e9-a531-e59682617a7c', 5),
    -- course/python/quiz/practice/S15/question/6
    ('6ffbe943-7d10-518c-a791-ad3db0d81700', 'be689582-a765-5be2-8a7c-6f7eb6b1cfdc', '0dda663b-80d1-5ecb-ad75-22b7e0322a9a', 6),
    -- course/python/quiz/practice/S16/question/1
    ('3b30ebef-83a8-50c8-9370-d1dc80e43131', 'adf0a1e1-2ca7-5e61-8895-1cf2ebc5c2bb', 'f9ff556a-16a5-5179-b107-d1ac282acbeb', 1),
    -- course/python/quiz/practice/S16/question/2
    ('8072ef52-d2b3-558b-a843-d85eb8b9470b', 'adf0a1e1-2ca7-5e61-8895-1cf2ebc5c2bb', '66443188-379d-5356-903b-099d652b8a05', 2),
    -- course/python/quiz/practice/S16/question/3
    ('db20d7f6-c9ff-52eb-a305-9c709f06d418', 'adf0a1e1-2ca7-5e61-8895-1cf2ebc5c2bb', 'fb49fb73-7307-5601-92df-b8ac7c7ba3a0', 3),
    -- course/python/quiz/practice/S16/question/4
    ('0825d479-ddb4-5d0b-8780-36802337c622', 'adf0a1e1-2ca7-5e61-8895-1cf2ebc5c2bb', 'c3d0bc80-6443-5513-a1ae-d80b81352fd1', 4),
    -- course/python/quiz/practice/S16/question/5
    ('69b199a1-c83c-5b46-8832-3a2bcbfab568', 'adf0a1e1-2ca7-5e61-8895-1cf2ebc5c2bb', '98406e3f-dcaf-529b-a870-226635325928', 5),
    -- course/python/quiz/practice/S16/question/6
    ('7a7e71bc-a853-5756-b272-1628df9c420c', 'adf0a1e1-2ca7-5e61-8895-1cf2ebc5c2bb', '8075cd3a-4a28-5952-a58a-6de2ad0d0c2a', 6),
    -- course/python/quiz/practice/S17/question/1
    ('0e406b30-f3f1-5d10-9f78-e3a1f35f5026', '64b483c8-a4ed-5dbe-99a7-468136ad4ead', '08ea5b82-46f8-51eb-b9f5-5c8a9211f69d', 1),
    -- course/python/quiz/practice/S17/question/2
    ('f84ca998-c287-537d-8941-b1ffceb78296', '64b483c8-a4ed-5dbe-99a7-468136ad4ead', '88fa62a1-bf82-5a4e-8374-e0114500a985', 2),
    -- course/python/quiz/practice/S17/question/3
    ('2d77dde7-b2af-515d-82f9-7bd428423e44', '64b483c8-a4ed-5dbe-99a7-468136ad4ead', '8873e464-617a-5ca4-b809-1f0a352c88d3', 3),
    -- course/python/quiz/practice/S17/question/4
    ('a14c7d51-f1d7-5bb2-9d9b-e299c934fbb0', '64b483c8-a4ed-5dbe-99a7-468136ad4ead', 'e07c1086-eda3-5a9d-a520-2bf4a5807913', 4),
    -- course/python/quiz/practice/S17/question/5
    ('6718c2cc-ca4f-59b9-89ff-d13950f35557', '64b483c8-a4ed-5dbe-99a7-468136ad4ead', '4c675368-45f3-54a2-bfd6-fae3c5c32ee5', 5),
    -- course/python/quiz/practice/S17/question/6
    ('d02cb2d5-c654-52aa-81e3-ec0375f4d9ab', '64b483c8-a4ed-5dbe-99a7-468136ad4ead', 'a4998e48-456d-5262-a633-c15866e45409', 6),
    -- course/python/quiz/practice/S18/question/1
    ('b1ff8456-1f63-5ba6-a089-616233948f42', 'bbd032f7-0949-57df-ab5f-f2ca0488ca38', '79621bfa-974d-5536-9d3d-dc41d8c823fd', 1),
    -- course/python/quiz/practice/S18/question/2
    ('2b15838c-134e-549e-8afd-a595c34b253e', 'bbd032f7-0949-57df-ab5f-f2ca0488ca38', '0743766c-df70-59a1-9652-b83fc714bc00', 2),
    -- course/python/quiz/practice/S18/question/3
    ('fddd1486-5522-5ac2-a655-4a21098a5393', 'bbd032f7-0949-57df-ab5f-f2ca0488ca38', 'f0eb6550-8ba8-50b5-a57f-74efabae60b1', 3),
    -- course/python/quiz/practice/S18/question/4
    ('94956335-27b6-5b51-bbf1-3e231c385f84', 'bbd032f7-0949-57df-ab5f-f2ca0488ca38', 'b561ba22-895e-515d-a2f2-2b8e140590f5', 4),
    -- course/python/quiz/practice/S18/question/5
    ('5c147e64-e93a-57a7-856c-88f5fbd820a2', 'bbd032f7-0949-57df-ab5f-f2ca0488ca38', '3a3ab603-4441-5823-9ee1-f3495609152d', 5),
    -- course/python/quiz/practice/S18/question/6
    ('4dd7970a-b4de-50ec-9dd4-3ceae1d1e6e9', 'bbd032f7-0949-57df-ab5f-f2ca0488ca38', '414e9cf5-04bc-5372-946e-599e1a5bc769', 6),
    -- course/python/quiz/practice/S19/question/1
    ('15863672-7371-5a2e-b7c3-09a74af8548f', '842a2c95-5e15-51f5-844e-720f6382266c', '9fdecdcd-012b-5e56-b437-b6359f58965f', 1),
    -- course/python/quiz/practice/S19/question/2
    ('b8212234-ff77-5cd2-a1c9-6e89f845b951', '842a2c95-5e15-51f5-844e-720f6382266c', '89efff69-556b-52f4-be10-9d2129073dee', 2),
    -- course/python/quiz/practice/S19/question/3
    ('cc8c6f82-0d0c-5af8-ad7e-55d65b5198b2', '842a2c95-5e15-51f5-844e-720f6382266c', 'aef875cf-bb7b-5cce-ba60-87b1199ddf68', 3),
    -- course/python/quiz/practice/S19/question/4
    ('df683ff7-3510-5984-a67e-68e7a2a20223', '842a2c95-5e15-51f5-844e-720f6382266c', '228a8b39-7a82-5dbd-845e-a9f8a3876129', 4),
    -- course/python/quiz/practice/S19/question/5
    ('49173c5b-4c5f-5841-93a8-686c7847de05', '842a2c95-5e15-51f5-844e-720f6382266c', '3ff08f45-f438-5fdc-88fa-b08f8a759b38', 5),
    -- course/python/quiz/practice/S19/question/6
    ('273b0e33-5508-56b6-a8ec-4ae3e8930f4f', '842a2c95-5e15-51f5-844e-720f6382266c', '7a76dc09-0fb5-59c9-80a2-ee2c9254d661', 6),
    -- course/python/quiz/practice/S20/question/1
    ('5faa47e1-2c63-5885-a8eb-b24002bcb1a9', '3c2ccff4-2d73-5587-8a60-04c45187f6be', '92a86f58-47b3-5372-99c7-ad17efd59262', 1),
    -- course/python/quiz/practice/S20/question/2
    ('33c30eea-e05e-5061-aacf-119dbccf0da2', '3c2ccff4-2d73-5587-8a60-04c45187f6be', '7ba936d7-bd89-5be4-a7bb-a59db78a02d4', 2),
    -- course/python/quiz/practice/S20/question/3
    ('9889a079-4fcf-5392-afd9-7d9c5d971a41', '3c2ccff4-2d73-5587-8a60-04c45187f6be', '63db8d08-cf64-5499-bcc7-17b0ed5de2f6', 3),
    -- course/python/quiz/practice/S20/question/4
    ('49031d9d-5748-59f5-933e-042f8428a3fd', '3c2ccff4-2d73-5587-8a60-04c45187f6be', '2b97d87b-30f1-553a-a4cf-158d0ff5c269', 4),
    -- course/python/quiz/practice/S20/question/5
    ('0615d33e-751d-576d-910f-870caf3fe750', '3c2ccff4-2d73-5587-8a60-04c45187f6be', 'a77231eb-97a1-5fe8-82ec-a73e2dfb048a', 5),
    -- course/python/quiz/practice/S20/question/6
    ('3dd6ca39-58a0-5c97-a5d5-95acbee84903', '3c2ccff4-2d73-5587-8a60-04c45187f6be', 'd4cf9cdb-997e-56d6-a044-286352bf3ffc', 6),
    -- course/python/quiz/practice/S01/easy/question/1
    ('e5564bc1-35cd-5e0e-9cb2-605921b8c94c', '03ed10c0-89e6-5f1e-9a89-3294207f2738', '9c3ac0be-d86d-5271-b27d-a07149066035', 1),
    -- course/python/quiz/practice/S01/easy/question/2
    ('8a6ae55d-5534-5a1c-8b63-293b45d31689', '03ed10c0-89e6-5f1e-9a89-3294207f2738', 'd50e0753-9528-58cc-98cd-b0b971ca31d9', 2),
    -- course/python/quiz/practice/S01/medium/question/1
    ('ba1b5a98-893a-5fee-b654-c17656688e51', '4d5902b8-0ff2-56d6-9f97-f4a57ab9f612', 'b358748e-fafe-58fe-b2df-d9871c4f6d3f', 1),
    -- course/python/quiz/practice/S01/medium/question/2
    ('e2d96788-548a-5db7-8240-1a0a03ba0887', '4d5902b8-0ff2-56d6-9f97-f4a57ab9f612', 'f1e8f1bc-ce65-5101-a6e9-438b31892750', 2),
    -- course/python/quiz/practice/S01/hard/question/1
    ('fe1e6890-e63b-5001-a4f0-ebbb44d608a2', '86e93c0b-95b4-52af-acd8-5a9152479da4', '53b89cbd-52d7-5d3b-8a46-5644e2362274', 1),
    -- course/python/quiz/practice/S01/hard/question/2
    ('b1a8e7fd-28b0-572f-8e59-9a080c88e77e', '86e93c0b-95b4-52af-acd8-5a9152479da4', 'b47b5206-4b65-5291-8d27-16505cb90268', 2),
    -- course/python/quiz/practice/S02/easy/question/1
    ('c78f955a-4e2e-5bc3-a632-b6cf8d605924', 'd14d312c-71c7-5620-b919-4826990f8ef3', 'bf18cc06-62d6-57a9-b1e6-f3606927bfb3', 1),
    -- course/python/quiz/practice/S02/easy/question/2
    ('83bc6115-a76b-53c4-8ee5-82e5fb011fd3', 'd14d312c-71c7-5620-b919-4826990f8ef3', '1028cb3f-1a77-5749-8fa0-c748d4ba12a5', 2),
    -- course/python/quiz/practice/S02/medium/question/1
    ('2521afb0-129f-57d0-9449-ce38b97a57c0', '3a8a532c-437f-5eb7-a156-222ac734ec4d', '289c2492-5d9d-5c7f-91d9-4f5aaa71e0d0', 1),
    -- course/python/quiz/practice/S02/medium/question/2
    ('04eb4ee8-fdde-5aef-bcf0-f98f533dbe61', '3a8a532c-437f-5eb7-a156-222ac734ec4d', 'd120a564-84e4-543a-9f8e-d1e190f80b7b', 2),
    -- course/python/quiz/practice/S02/hard/question/1
    ('9c1beeb1-61c6-56bb-815b-205c902e2641', '20d6f846-4e64-5507-bd2a-465eff42df6a', 'd2eedd97-a799-5f1c-b2cb-39edee8c6b0e', 1),
    -- course/python/quiz/practice/S02/hard/question/2
    ('3c21b1e0-eab0-5411-a685-6e2bf2072ef9', '20d6f846-4e64-5507-bd2a-465eff42df6a', 'f0b39c73-70e6-5571-9727-75b5e626d0dd', 2),
    -- course/python/quiz/practice/S03/easy/question/1
    ('1d6ea54f-520c-555b-9a34-559ff6c0c9d2', 'cfdb091a-153f-537e-ad78-8f85ecb30f74', 'a0aa03ca-5c53-5d68-bca3-3db07691f501', 1),
    -- course/python/quiz/practice/S03/easy/question/2
    ('72e6bb58-e713-5303-b560-1093db8a0c2d', 'cfdb091a-153f-537e-ad78-8f85ecb30f74', 'f0f24b93-984f-5876-89f8-835c049bb8ac', 2),
    -- course/python/quiz/practice/S03/medium/question/1
    ('c2744dc7-bd48-512c-8dd0-f8ce7f457bbb', '351ca32d-028b-553f-a7a1-ba14bcc6fc2a', 'd7438085-a880-5520-abaf-97e51e6351b4', 1),
    -- course/python/quiz/practice/S03/medium/question/2
    ('2f4fd29f-3429-5fe4-a795-215506bea3b6', '351ca32d-028b-553f-a7a1-ba14bcc6fc2a', '4b09d392-34fe-5e09-a893-45cf07d8a3d7', 2),
    -- course/python/quiz/practice/S03/hard/question/1
    ('80441579-389f-5aa5-b425-b280b0d49705', '485be76e-4294-5af2-8420-52b4f6d7660e', '9295bb15-c299-5795-bce7-ea13648f734b', 1),
    -- course/python/quiz/practice/S03/hard/question/2
    ('14b03910-6020-5732-a1a5-6661c20c13f4', '485be76e-4294-5af2-8420-52b4f6d7660e', '226975a1-55e0-5bb7-bffe-ab4ccdaaa20f', 2),
    -- course/python/quiz/practice/S04/easy/question/1
    ('d3cd0b50-8665-5d83-8b8c-cb9696f9b38d', 'd6643654-1baf-5461-9409-86c866284d7e', '5902205d-e312-5812-99c7-c15ffd1b4722', 1),
    -- course/python/quiz/practice/S04/easy/question/2
    ('08342f97-e787-59a6-a7f5-d4b32e4eecf4', 'd6643654-1baf-5461-9409-86c866284d7e', '5344ddbb-7770-5332-8e0d-2961fb7d30e9', 2),
    -- course/python/quiz/practice/S04/medium/question/1
    ('eace1add-b2ff-5a29-8169-707adaf37fd0', '8253563d-ec3c-57a5-b2a8-3a81543a2356', '1cd39950-3e19-53b4-a540-15001521ef4f', 1),
    -- course/python/quiz/practice/S04/medium/question/2
    ('906b243c-3a78-51f2-8618-eb50bca41fd2', '8253563d-ec3c-57a5-b2a8-3a81543a2356', '8972206b-e768-5f13-9a02-ac5edd2fecf5', 2),
    -- course/python/quiz/practice/S04/hard/question/1
    ('e49ea986-113d-5755-a913-b0a4ab37cec2', '79dd541f-1f5d-585d-b43f-0e1de79ce50e', '46085bea-1eb5-5c3b-bbee-4b2653c3bf14', 1),
    -- course/python/quiz/practice/S04/hard/question/2
    ('06da53b0-c74d-5f56-a508-5fbbf6857e58', '79dd541f-1f5d-585d-b43f-0e1de79ce50e', 'cbc8f9c2-c540-5511-b46c-841684a2cf84', 2),
    -- course/python/quiz/practice/S05/easy/question/1
    ('d9a09131-8054-57b7-85ec-c79580bd273a', '18636ba5-2907-5d91-824c-b14985a01c41', '66f85ef9-185a-505e-ba77-6171a39a5b46', 1),
    -- course/python/quiz/practice/S05/easy/question/2
    ('6bfcd9af-c6dd-5746-80f9-1cf75f1ab9cc', '18636ba5-2907-5d91-824c-b14985a01c41', '19789354-82db-568c-b1ba-155e5ac72e85', 2),
    -- course/python/quiz/practice/S05/medium/question/1
    ('eace262a-a414-5b01-b44e-95b7243e0c44', '937452b1-0ad7-523f-84a8-cf108ef9f0f8', '0a113214-8bb3-500f-8580-5919e14691a2', 1),
    -- course/python/quiz/practice/S05/medium/question/2
    ('bf3ecfa2-7d6c-574d-b9f0-9177e007d28b', '937452b1-0ad7-523f-84a8-cf108ef9f0f8', 'c875e997-f3b3-5a83-93d3-a5c33daa4a00', 2),
    -- course/python/quiz/practice/S05/hard/question/1
    ('5ea722e2-a57c-5cbb-9d8e-13ab7063aa83', '97c0e7b1-09d5-56c4-82a0-984bdb7f53e1', '48e9b47e-c2aa-53f5-938d-276c6b805f9e', 1),
    -- course/python/quiz/practice/S05/hard/question/2
    ('8dc18cd5-7f69-5c21-b910-fd11fecb6fc9', '97c0e7b1-09d5-56c4-82a0-984bdb7f53e1', 'e2450614-7fe8-5e0b-a571-0bdfee838601', 2),
    -- course/python/quiz/practice/S06/easy/question/1
    ('b61081d7-6661-5fc2-b56d-8bc203a843d3', '621d5c58-a997-53a5-a313-79dcd4280295', 'c31da348-74a2-5c9a-9731-a44cf67887e6', 1),
    -- course/python/quiz/practice/S06/easy/question/2
    ('0999f8a8-245a-581c-8dc1-82fe0e7f1346', '621d5c58-a997-53a5-a313-79dcd4280295', '7fcc92f1-63dd-506e-a38f-003ff8f23179', 2),
    -- course/python/quiz/practice/S06/medium/question/1
    ('aae596cf-4365-581d-9cb8-12ac9016cac2', '9ad5f260-5dc2-5de2-901b-4d9770e2ad82', '2b405b34-5384-53d4-a212-2be28a38740f', 1),
    -- course/python/quiz/practice/S06/medium/question/2
    ('133b824e-b67f-536f-a4be-96eb113b142b', '9ad5f260-5dc2-5de2-901b-4d9770e2ad82', 'c4d632e1-f6ed-5509-8844-0967e9f6397e', 2),
    -- course/python/quiz/practice/S06/hard/question/1
    ('cb790437-60bf-524e-b63c-767f60c44598', '357b3ab0-577f-5cd0-a08d-5e845e30dfde', '322fb12c-c7bf-522c-ac4d-2a333f606173', 1),
    -- course/python/quiz/practice/S06/hard/question/2
    ('50408483-8c8f-52ec-9797-f22023e20975', '357b3ab0-577f-5cd0-a08d-5e845e30dfde', '6019ed9f-42b0-53f2-a367-7e0e6d2e7c11', 2),
    -- course/python/quiz/practice/S07/easy/question/1
    ('42904bf2-94b9-50c1-b9da-fba5734b4073', '38a65cd8-d739-5ff5-b82d-35112b54ac2b', 'c0730e76-14b4-5000-bd79-240f4f7f3b0d', 1),
    -- course/python/quiz/practice/S07/easy/question/2
    ('4a6dea7b-c2a0-5408-80a7-11f81fc54355', '38a65cd8-d739-5ff5-b82d-35112b54ac2b', 'e21daba6-38f8-5d78-b9d5-d006fab32a7f', 2),
    -- course/python/quiz/practice/S07/medium/question/1
    ('9f744feb-e466-57af-92c4-cac2e54afc7b', '6f9fafe1-d02c-5c1f-8bb1-7a52359d9e9f', '66460d0a-e09e-5783-8377-79ef8093ccc3', 1),
    -- course/python/quiz/practice/S07/medium/question/2
    ('4b82ff19-bc9f-5a92-906d-f98c1aa9228b', '6f9fafe1-d02c-5c1f-8bb1-7a52359d9e9f', 'aa4592a5-457f-510a-9760-151aa800bab0', 2),
    -- course/python/quiz/practice/S07/hard/question/1
    ('d8fbfe72-3ea3-5bc9-9f36-581d8d844810', '8a314459-7ddf-5d19-baf7-1a3348dd0c8d', '9c4197f7-3435-50f9-a8b6-9cb4fca0a41a', 1),
    -- course/python/quiz/practice/S07/hard/question/2
    ('54c93038-6825-50a0-bac6-e5f71c8482b9', '8a314459-7ddf-5d19-baf7-1a3348dd0c8d', 'f47c6be3-3098-5daf-a319-dbd2d57f5feb', 2),
    -- course/python/quiz/practice/S08/easy/question/1
    ('0fdfbcdd-8c67-524d-b851-dfc5f3d64734', 'bba1745a-26b6-5ff6-a52f-4291eac2139d', '341fa1fa-e9c1-515f-b6bd-fe7202160556', 1),
    -- course/python/quiz/practice/S08/easy/question/2
    ('856ce18d-724f-5a11-919b-2b99f2d4f1ba', 'bba1745a-26b6-5ff6-a52f-4291eac2139d', 'af8beb6f-98d9-5124-b8cc-5fb248f3f8fb', 2),
    -- course/python/quiz/practice/S08/medium/question/1
    ('c8dfb57d-de7b-5354-bde9-1a51bc815197', '903f9238-9ebe-57c4-b6e6-6b32367ab613', '7c7ac83d-ccd2-53aa-a012-6bd42e9de871', 1),
    -- course/python/quiz/practice/S08/medium/question/2
    ('82495f85-08fd-5ec1-ab4b-a4196163a8d4', '903f9238-9ebe-57c4-b6e6-6b32367ab613', 'a36de1fc-fd13-50b4-9c6c-779dff8392f8', 2),
    -- course/python/quiz/practice/S08/hard/question/1
    ('d3928737-9e89-5f64-b41e-58cf41473fbb', '92f2a561-776d-5c8b-bb9b-53c8e1e3ee43', '9fe24547-5ac5-5fe2-8740-73aa7d91d528', 1),
    -- course/python/quiz/practice/S08/hard/question/2
    ('c08cdfca-7616-5a3c-b72b-32f6c9628169', '92f2a561-776d-5c8b-bb9b-53c8e1e3ee43', '4de3396b-56f4-5758-a9f0-5b3c95eb0d6f', 2),
    -- course/python/quiz/practice/S09/easy/question/1
    ('ae11a6b7-843b-5e23-8833-983519eb7853', '534e6710-f439-5c8f-9b98-72d246c8ca41', '75b6e65e-f2f6-502d-89b0-deb920af668d', 1),
    -- course/python/quiz/practice/S09/easy/question/2
    ('449120a3-2fe3-5c7c-b50b-fdb8762a5dbf', '534e6710-f439-5c8f-9b98-72d246c8ca41', '97a7aaaf-ccca-54b2-8dbc-426d15c2ed88', 2),
    -- course/python/quiz/practice/S09/medium/question/1
    ('a6717d10-c5c1-5bae-97fc-d4a50a56768c', 'ca947ac2-60e4-55b6-abb7-78c25a49d244', 'cf774e61-3bff-54f9-a1eb-6ad212f0f68b', 1),
    -- course/python/quiz/practice/S09/medium/question/2
    ('ebf59b8d-058a-5143-ab22-dc6cdeb03754', 'ca947ac2-60e4-55b6-abb7-78c25a49d244', '0c587264-59c8-5734-af9d-a51d426e70c7', 2),
    -- course/python/quiz/practice/S09/hard/question/1
    ('c502ba63-d99f-5784-85f8-2d439be3fd4d', '6333f000-e02d-5229-96cf-60423ea6ea7f', '55777aea-ea16-5b83-8d38-f6ceca56f246', 1),
    -- course/python/quiz/practice/S09/hard/question/2
    ('6dd5b2ed-2bd9-574e-9d6e-3a5dc8ff1185', '6333f000-e02d-5229-96cf-60423ea6ea7f', '6caa041a-ce9e-5398-970f-6dfa3a9995f7', 2),
    -- course/python/quiz/practice/S10/easy/question/1
    ('605088e0-6d76-5b48-b432-90287497da82', '72489347-7cf3-58cc-9b4a-2e6d82be8145', '34d0d846-31f6-5da6-9781-7935f33a4bd6', 1),
    -- course/python/quiz/practice/S10/easy/question/2
    ('d525b598-630c-5b5d-ba79-8d92e54d0fba', '72489347-7cf3-58cc-9b4a-2e6d82be8145', 'a4dbf079-8d61-51c5-8b5d-cf96357210e4', 2),
    -- course/python/quiz/practice/S10/medium/question/1
    ('039b69c7-5c6d-552c-ab02-a9f1dd98aeeb', 'd4c5b03e-f456-56fa-adb2-4fea716e5e9e', '7d295949-5089-5990-97f2-6c53079c1126', 1),
    -- course/python/quiz/practice/S10/medium/question/2
    ('f5f4e0d7-648a-5d72-9993-a6dfc988cd35', 'd4c5b03e-f456-56fa-adb2-4fea716e5e9e', 'eca786ea-ae10-5227-9b51-63c09d8854d2', 2),
    -- course/python/quiz/practice/S10/hard/question/1
    ('8aeb5144-d347-5e1c-a00c-bfcf91a83380', '314bfb63-1356-5f9b-9e92-613be42bc43d', '7693f9a0-e5b8-5f16-b150-4466d03d0d1b', 1),
    -- course/python/quiz/practice/S10/hard/question/2
    ('528eaa87-f9fb-59ca-8623-a3cbb6c34e7e', '314bfb63-1356-5f9b-9e92-613be42bc43d', '0cb404b3-4d87-5d9f-ac9b-52a9dc22e0af', 2),
    -- course/python/quiz/practice/S11/easy/question/1
    ('96ad3e69-f327-5f42-a9aa-eb451c58ea2a', '49756cc7-4a37-59e3-bfc1-48367b1a37dd', 'a2a5de7e-7f95-5a16-9868-d847c8adce7f', 1),
    -- course/python/quiz/practice/S11/easy/question/2
    ('2fbec14f-08a8-53e6-9f29-33106c7c1825', '49756cc7-4a37-59e3-bfc1-48367b1a37dd', '1a324327-c0e9-5345-ac2f-25dd5e411354', 2),
    -- course/python/quiz/practice/S11/medium/question/1
    ('6235a3a6-35e7-515f-a511-f2ef348b2c5d', 'c8b1f3d2-8b87-59b6-a660-275e7eb1c6b3', '7b4177a9-797b-547e-aa80-e5b07496ddcd', 1),
    -- course/python/quiz/practice/S11/medium/question/2
    ('2ac99b7c-5018-5185-9e2a-e6f854ce41fe', 'c8b1f3d2-8b87-59b6-a660-275e7eb1c6b3', '2059a88e-892b-5337-9795-0567ea98b4fb', 2),
    -- course/python/quiz/practice/S11/hard/question/1
    ('3bdf282e-8ebf-5f50-b045-034396f58023', 'c921c7c7-b2ca-570a-8c1a-7469591c2e81', '3ec37a02-cf3f-525e-9196-f04d2cc31b3d', 1),
    -- course/python/quiz/practice/S11/hard/question/2
    ('bc9a2263-00db-51ec-8432-95d71d818ea1', 'c921c7c7-b2ca-570a-8c1a-7469591c2e81', '2b58516f-d75c-52dc-9798-8e0e3ab7470e', 2),
    -- course/python/quiz/practice/S12/easy/question/1
    ('b7f97db7-f689-53c6-a2c2-9534362fb88b', 'c2791023-29a0-5e04-9301-32995a5dd9ed', '88720a00-57e1-5ed5-9f78-757c314635f6', 1),
    -- course/python/quiz/practice/S12/easy/question/2
    ('4affff06-1142-5da1-9dd0-df4c5db70125', 'c2791023-29a0-5e04-9301-32995a5dd9ed', '61f0c107-34c8-51cf-a88c-235927c0540e', 2),
    -- course/python/quiz/practice/S12/medium/question/1
    ('64af23fe-3284-50ad-9d4f-b709e9de68da', '1b26619c-1d65-5b8f-b176-24aa5981fb4f', 'b234ed5b-1cdf-51ad-b472-19ddfce0a465', 1),
    -- course/python/quiz/practice/S12/medium/question/2
    ('f4660f32-28cc-59ee-8800-c4ed60929a79', '1b26619c-1d65-5b8f-b176-24aa5981fb4f', '22afbbe2-3c43-52d1-b661-ebe867e13f8e', 2),
    -- course/python/quiz/practice/S12/hard/question/1
    ('cd02b2ab-52b5-5b17-b13e-4712b2c333e3', '19841ab6-ae11-5bc0-93e0-b92579a8610d', 'a319563c-3f22-567a-8777-f5ec21389064', 1),
    -- course/python/quiz/practice/S12/hard/question/2
    ('b46bc00b-e11e-5c84-80cc-10aad3bb837a', '19841ab6-ae11-5bc0-93e0-b92579a8610d', 'd0e801ff-da73-5cea-aff2-d1e680653265', 2),
    -- course/python/quiz/practice/S13/easy/question/1
    ('7e02211c-ed22-5444-98e8-53db72d82488', '37012a01-45eb-590a-901c-e490b9a87b40', 'b21c2f55-0227-5a64-b890-c77a7904f7a3', 1),
    -- course/python/quiz/practice/S13/easy/question/2
    ('10415731-be8e-5f59-b211-06b811b1442f', '37012a01-45eb-590a-901c-e490b9a87b40', 'd0c983be-a6f5-5de3-869b-795570414af2', 2),
    -- course/python/quiz/practice/S13/medium/question/1
    ('6352c06f-af5d-5af8-abe9-65bdc67cd952', '4e0ec97f-2581-5fde-8a07-f326409d7b40', 'e6f6567c-6ff8-58ae-a4ef-2492679faef6', 1),
    -- course/python/quiz/practice/S13/medium/question/2
    ('afd5e09a-7de5-5f86-bde1-e1412e1fc387', '4e0ec97f-2581-5fde-8a07-f326409d7b40', '902cefa8-405a-5a25-9064-87e289ed86d5', 2),
    -- course/python/quiz/practice/S13/hard/question/1
    ('ecec4269-053f-5da9-974f-9e5c53069945', '6a4a29ef-7fe1-5ce0-94ae-6444afedd8af', '919fb0bd-d4e3-5723-865b-7dd3baa8a3dc', 1),
    -- course/python/quiz/practice/S13/hard/question/2
    ('596de010-0a74-50a3-b63e-6067be3555f6', '6a4a29ef-7fe1-5ce0-94ae-6444afedd8af', '3d322f31-d12e-5474-b92c-4292c01debef', 2),
    -- course/python/quiz/practice/S14/easy/question/1
    ('3672990c-696d-5bcb-a1c0-cbb4f957590c', '9e231fcc-d5f4-52d4-9e97-40a1d5d54052', '92bf5207-b9a3-55f4-a59d-925bac4f44ab', 1),
    -- course/python/quiz/practice/S14/easy/question/2
    ('c57a94a6-0dbe-5753-9840-b76fa270809e', '9e231fcc-d5f4-52d4-9e97-40a1d5d54052', 'ce9eb7c5-b6e1-56f8-bc5d-f96fd6566f11', 2),
    -- course/python/quiz/practice/S14/medium/question/1
    ('acec200e-0dfd-5131-95f1-82fbf1239811', '96ae8dc1-a8b1-5bf3-8d35-b359ed40f1fb', '7632e4ca-e31c-5547-b89d-15a11ec9504d', 1),
    -- course/python/quiz/practice/S14/medium/question/2
    ('8e0ca2bd-b667-518c-85bb-18f520ed4fd1', '96ae8dc1-a8b1-5bf3-8d35-b359ed40f1fb', 'b538764c-cf6d-5c5d-96b7-f40c1b8cb58b', 2),
    -- course/python/quiz/practice/S14/hard/question/1
    ('3a8834ba-f517-59fa-b586-e692e548ffb3', 'c168fe41-e03c-5297-b5af-9a878aae97bd', '69cb172b-9754-5778-98ae-963cbbad9b31', 1),
    -- course/python/quiz/practice/S14/hard/question/2
    ('a0a43003-d567-5236-a703-8b4b51cd8828', 'c168fe41-e03c-5297-b5af-9a878aae97bd', 'd3fce7dd-084c-57fc-9b8d-c4a20fb5c06f', 2),
    -- course/python/quiz/practice/S15/easy/question/1
    ('34b95d9c-dda4-542f-b036-9ba128a5bcdd', 'a29ed7e2-7933-537e-9125-16972a3e3685', '0fb4d8be-aceb-57f3-983c-77a57bb9f177', 1),
    -- course/python/quiz/practice/S15/easy/question/2
    ('9b108c25-cb41-5904-a2d3-ec7efb4adfc2', 'a29ed7e2-7933-537e-9125-16972a3e3685', '6cf13941-0a1c-50fe-94b6-06aa636e9ff8', 2),
    -- course/python/quiz/practice/S15/medium/question/1
    ('e75cc59e-df11-5e95-8435-3ccf9646f79f', 'cc84c519-7fa2-5cc2-9e18-19138ec86c64', '79a4b286-881f-5770-954c-4e7645c2d2f0', 1),
    -- course/python/quiz/practice/S15/medium/question/2
    ('a446e4c0-241b-56d8-8c4c-b8fef72f88ad', 'cc84c519-7fa2-5cc2-9e18-19138ec86c64', 'adcb795b-722f-5a06-9eb6-5611455f69cd', 2),
    -- course/python/quiz/practice/S15/hard/question/1
    ('49ecab28-59b6-5089-8862-cedd8a914439', '28780356-feb7-5e9b-83bb-fba1dc058867', '44a7142f-cd4c-53e9-a531-e59682617a7c', 1),
    -- course/python/quiz/practice/S15/hard/question/2
    ('70a5650a-8690-5c05-8e27-29253b5472f1', '28780356-feb7-5e9b-83bb-fba1dc058867', '0dda663b-80d1-5ecb-ad75-22b7e0322a9a', 2),
    -- course/python/quiz/practice/S16/easy/question/1
    ('ba229ff2-b435-5fab-98d4-0f897dd3f9d6', 'ac020bb2-4527-540c-9b2f-babe45968cb7', 'f9ff556a-16a5-5179-b107-d1ac282acbeb', 1),
    -- course/python/quiz/practice/S16/easy/question/2
    ('8b2d3986-229c-567d-9611-b7b01416b2dc', 'ac020bb2-4527-540c-9b2f-babe45968cb7', '66443188-379d-5356-903b-099d652b8a05', 2),
    -- course/python/quiz/practice/S16/medium/question/1
    ('14a096e2-a12e-5440-8809-4af5acab198a', 'af68d0c1-8fcd-5fb0-92a6-c5a17e3397b3', 'fb49fb73-7307-5601-92df-b8ac7c7ba3a0', 1),
    -- course/python/quiz/practice/S16/medium/question/2
    ('dd4af15a-71ee-58f0-985d-7985e15f3a75', 'af68d0c1-8fcd-5fb0-92a6-c5a17e3397b3', 'c3d0bc80-6443-5513-a1ae-d80b81352fd1', 2),
    -- course/python/quiz/practice/S16/hard/question/1
    ('c7edef61-40aa-5d18-8e70-9ce23374adae', '660e11b3-443d-56b7-8bc4-c2915e460ac4', '98406e3f-dcaf-529b-a870-226635325928', 1),
    -- course/python/quiz/practice/S16/hard/question/2
    ('b3fca554-60e2-5dcf-b1b1-f2ef05d23777', '660e11b3-443d-56b7-8bc4-c2915e460ac4', '8075cd3a-4a28-5952-a58a-6de2ad0d0c2a', 2),
    -- course/python/quiz/practice/S17/easy/question/1
    ('5092de8f-82fa-5045-baf9-a13c45b80072', 'ce0df680-e8df-5508-95d8-bfb9f613ece4', '08ea5b82-46f8-51eb-b9f5-5c8a9211f69d', 1),
    -- course/python/quiz/practice/S17/easy/question/2
    ('524a260a-6d9d-50a4-8640-876d3cef3e7a', 'ce0df680-e8df-5508-95d8-bfb9f613ece4', '88fa62a1-bf82-5a4e-8374-e0114500a985', 2),
    -- course/python/quiz/practice/S17/medium/question/1
    ('fffd4ef5-7b45-5e4c-8426-9f66446ae21c', 'b6bad63c-e059-5d20-b24e-5cc723479be4', '8873e464-617a-5ca4-b809-1f0a352c88d3', 1),
    -- course/python/quiz/practice/S17/medium/question/2
    ('b81b03ef-7a68-58e6-ada5-0fc8b9aef01e', 'b6bad63c-e059-5d20-b24e-5cc723479be4', 'e07c1086-eda3-5a9d-a520-2bf4a5807913', 2),
    -- course/python/quiz/practice/S17/hard/question/1
    ('e974baa3-bcb0-55a1-8431-a2dc4895b312', '92249c0e-3f0d-5480-ad08-450108f6b637', '4c675368-45f3-54a2-bfd6-fae3c5c32ee5', 1),
    -- course/python/quiz/practice/S17/hard/question/2
    ('89775ad7-c0e2-5ea4-a616-387d85a87d06', '92249c0e-3f0d-5480-ad08-450108f6b637', 'a4998e48-456d-5262-a633-c15866e45409', 2),
    -- course/python/quiz/practice/S18/easy/question/1
    ('e27896d0-8f2b-51b9-ae05-394bf82dc764', '0e460e4f-0888-598e-990c-eedc2592ae06', '79621bfa-974d-5536-9d3d-dc41d8c823fd', 1),
    -- course/python/quiz/practice/S18/easy/question/2
    ('e2b3663b-43ac-559e-a268-742142b78d1e', '0e460e4f-0888-598e-990c-eedc2592ae06', '0743766c-df70-59a1-9652-b83fc714bc00', 2),
    -- course/python/quiz/practice/S18/medium/question/1
    ('672e3887-5c69-5570-bb23-5a941992a17d', '9124000a-7784-5639-9f11-d044cd99c048', 'f0eb6550-8ba8-50b5-a57f-74efabae60b1', 1),
    -- course/python/quiz/practice/S18/medium/question/2
    ('cbb42792-b5ad-555c-a47b-08873b1c48dd', '9124000a-7784-5639-9f11-d044cd99c048', 'b561ba22-895e-515d-a2f2-2b8e140590f5', 2),
    -- course/python/quiz/practice/S18/hard/question/1
    ('5f0258dd-6c89-5f98-a23e-9780d85f242c', 'cb2afc36-705a-5331-a750-871798586a54', '3a3ab603-4441-5823-9ee1-f3495609152d', 1),
    -- course/python/quiz/practice/S18/hard/question/2
    ('e57de44c-7f69-5ccb-b9a5-ced3a55b09f6', 'cb2afc36-705a-5331-a750-871798586a54', '414e9cf5-04bc-5372-946e-599e1a5bc769', 2),
    -- course/python/quiz/practice/S19/easy/question/1
    ('eca09490-2e9b-5c45-8778-d5b4631c0b7e', '8acece65-3a78-5f5d-9c51-9378e9113c6c', '9fdecdcd-012b-5e56-b437-b6359f58965f', 1),
    -- course/python/quiz/practice/S19/easy/question/2
    ('b72cc4e5-5c72-58a5-8b8d-695f3d23f445', '8acece65-3a78-5f5d-9c51-9378e9113c6c', '89efff69-556b-52f4-be10-9d2129073dee', 2),
    -- course/python/quiz/practice/S19/medium/question/1
    ('d2730f83-5d6a-5e23-bbb8-10950b1f4ebc', 'd2017e65-0935-5a00-9673-1df5abe78018', 'aef875cf-bb7b-5cce-ba60-87b1199ddf68', 1),
    -- course/python/quiz/practice/S19/medium/question/2
    ('77f19c62-2bcc-54f9-a9bf-f388a49465da', 'd2017e65-0935-5a00-9673-1df5abe78018', '228a8b39-7a82-5dbd-845e-a9f8a3876129', 2),
    -- course/python/quiz/practice/S19/hard/question/1
    ('e114cf06-e6d6-5314-a19e-a67b7b07791b', '8f759f3e-0d73-5fbd-abc8-febca82deb6a', '3ff08f45-f438-5fdc-88fa-b08f8a759b38', 1),
    -- course/python/quiz/practice/S19/hard/question/2
    ('a777187c-176b-529c-aa92-cd80f99d1c32', '8f759f3e-0d73-5fbd-abc8-febca82deb6a', '7a76dc09-0fb5-59c9-80a2-ee2c9254d661', 2),
    -- course/python/quiz/practice/S20/easy/question/1
    ('6b3acb4e-ee32-513f-ac21-459c62a801f1', '9a89f795-8bf5-5a0e-ac84-415a09366ecf', '92a86f58-47b3-5372-99c7-ad17efd59262', 1),
    -- course/python/quiz/practice/S20/easy/question/2
    ('d89be119-fa81-559e-9f7d-1c8dc6f603c6', '9a89f795-8bf5-5a0e-ac84-415a09366ecf', '7ba936d7-bd89-5be4-a7bb-a59db78a02d4', 2),
    -- course/python/quiz/practice/S20/medium/question/1
    ('febaf203-b37f-50d2-8f34-6129d7dcdbc2', 'd865b335-183c-56ea-a7d5-39d97cdb521c', '63db8d08-cf64-5499-bcc7-17b0ed5de2f6', 1),
    -- course/python/quiz/practice/S20/medium/question/2
    ('64f46570-a8fc-58c8-93d3-b1c0d265e7ad', 'd865b335-183c-56ea-a7d5-39d97cdb521c', '2b97d87b-30f1-553a-a4cf-158d0ff5c269', 2),
    -- course/python/quiz/practice/S20/hard/question/1
    ('db70a4e0-0b2d-58fd-8adc-b1d5541c33bf', '8d2a8290-ded1-56ff-a23b-8f8bb030f367', 'a77231eb-97a1-5fe8-82ec-a73e2dfb048a', 1),
    -- course/python/quiz/practice/S20/hard/question/2
    ('793dd200-0d45-54d3-91bc-0661217d2f1d', '8d2a8290-ded1-56ff-a23b-8f8bb030f367', 'd4cf9cdb-997e-56d6-a044-286352bf3ffc', 2),
    -- course/python/quiz/final-test/question/1
    ('3d113e65-10cf-56e9-8ff3-07119bc994bb', '544c488f-e521-55d2-9744-51b92ad8e8da', '53b89cbd-52d7-5d3b-8a46-5644e2362274', 1),
    -- course/python/quiz/final-test/question/2
    ('e26e24d0-f933-5453-a611-509511db004d', '544c488f-e521-55d2-9744-51b92ad8e8da', 'd2eedd97-a799-5f1c-b2cb-39edee8c6b0e', 2),
    -- course/python/quiz/final-test/question/3
    ('36486e97-0851-50e2-b1af-96d85c534479', '544c488f-e521-55d2-9744-51b92ad8e8da', '9295bb15-c299-5795-bce7-ea13648f734b', 3),
    -- course/python/quiz/final-test/question/4
    ('11b1bf9b-64a1-5cee-8f65-e592cf702622', '544c488f-e521-55d2-9744-51b92ad8e8da', '46085bea-1eb5-5c3b-bbee-4b2653c3bf14', 4),
    -- course/python/quiz/final-test/question/5
    ('7b441e59-70be-51ba-8970-eafece6a68cf', '544c488f-e521-55d2-9744-51b92ad8e8da', '48e9b47e-c2aa-53f5-938d-276c6b805f9e', 5),
    -- course/python/quiz/final-test/question/6
    ('e6d9a838-7563-5a98-8841-5f8f8f74d901', '544c488f-e521-55d2-9744-51b92ad8e8da', '322fb12c-c7bf-522c-ac4d-2a333f606173', 6),
    -- course/python/quiz/final-test/question/7
    ('b3637094-c557-5698-80d7-2c24e22c3544', '544c488f-e521-55d2-9744-51b92ad8e8da', '9c4197f7-3435-50f9-a8b6-9cb4fca0a41a', 7),
    -- course/python/quiz/final-test/question/8
    ('494b87f7-899b-5a4b-9c93-d9354148bbfe', '544c488f-e521-55d2-9744-51b92ad8e8da', '9fe24547-5ac5-5fe2-8740-73aa7d91d528', 8),
    -- course/python/quiz/final-test/question/9
    ('a20d6f19-90c6-53ea-954f-6526dbdcfe25', '544c488f-e521-55d2-9744-51b92ad8e8da', '55777aea-ea16-5b83-8d38-f6ceca56f246', 9),
    -- course/python/quiz/final-test/question/10
    ('2703ebcf-3eec-559b-a843-8e1afcdd890b', '544c488f-e521-55d2-9744-51b92ad8e8da', '7693f9a0-e5b8-5f16-b150-4466d03d0d1b', 10),
    -- course/python/quiz/final-test/question/11
    ('81634af3-ee4a-5d3d-93e1-07b9e1f95edc', '544c488f-e521-55d2-9744-51b92ad8e8da', '3ec37a02-cf3f-525e-9196-f04d2cc31b3d', 11),
    -- course/python/quiz/final-test/question/12
    ('a10e57a1-a21d-5d38-9e6d-d26477e3dd91', '544c488f-e521-55d2-9744-51b92ad8e8da', 'a319563c-3f22-567a-8777-f5ec21389064', 12),
    -- course/python/quiz/final-test/question/13
    ('a2f11404-531c-5202-bd46-2eb46cdea292', '544c488f-e521-55d2-9744-51b92ad8e8da', '919fb0bd-d4e3-5723-865b-7dd3baa8a3dc', 13),
    -- course/python/quiz/final-test/question/14
    ('fe56c8d3-5bbf-5f41-b72b-a5fab3864c21', '544c488f-e521-55d2-9744-51b92ad8e8da', '69cb172b-9754-5778-98ae-963cbbad9b31', 14),
    -- course/python/quiz/final-test/question/15
    ('2bbc7e52-fca1-54ad-b428-93c318780909', '544c488f-e521-55d2-9744-51b92ad8e8da', '44a7142f-cd4c-53e9-a531-e59682617a7c', 15),
    -- course/python/quiz/final-test/question/16
    ('489f56af-71c7-50f3-83ec-b8cd75448cbd', '544c488f-e521-55d2-9744-51b92ad8e8da', '98406e3f-dcaf-529b-a870-226635325928', 16),
    -- course/python/quiz/final-test/question/17
    ('4c4b29eb-db19-5383-a2d2-26ddcb6dca97', '544c488f-e521-55d2-9744-51b92ad8e8da', '4c675368-45f3-54a2-bfd6-fae3c5c32ee5', 17),
    -- course/python/quiz/final-test/question/18
    ('5112f0b0-785f-502e-afa4-41fd53837773', '544c488f-e521-55d2-9744-51b92ad8e8da', '3a3ab603-4441-5823-9ee1-f3495609152d', 18),
    -- course/python/quiz/final-test/question/19
    ('8c0aa3a3-5bce-5d3f-9b7e-5a6dee83c1df', '544c488f-e521-55d2-9744-51b92ad8e8da', '3ff08f45-f438-5fdc-88fa-b08f8a759b38', 19),
    -- course/python/quiz/final-test/question/20
    ('09e42380-a566-5ece-ae3c-029638cb056b', '544c488f-e521-55d2-9744-51b92ad8e8da', 'a77231eb-97a1-5fe8-82ec-a73e2dfb048a', 20);

CREATE TEMP TABLE sd04_assignment_seed (
    id uuid NOT NULL,
    course_id uuid NOT NULL,
    lesson_id uuid NOT NULL,
    title text NOT NULL,
    instructions text NOT NULL,
    deadline_at timestamptz NOT NULL,
    publication_status text NOT NULL
) ON COMMIT DROP;

INSERT INTO sd04_assignment_seed (id, course_id, lesson_id, title, instructions, deadline_at, publication_status)
VALUES
    -- course/python/assignment/loops (bound to L10)
    ('28b2b80e-3fd4-52d9-b1a4-6ae5e7482551', '68ea4540-e9da-5605-9054-28b29afa0992', '3b413f38-5f6d-50c2-a28a-6ebbc9f70a12', 'Loop Control Practice Assignment', 'Write short Python programs that practice loop control with while, for, break and continue. Include: (1) a loop that sums positive numbers entered until a sentinel value triggers break, and (2) a loop over range(1, 31) that skips multiples of 3 using continue. Submit your solution and brief sample output trace as a single PDF or ZIP archive.', current_setting('codemastery.seed_assignment_loops_deadline')::timestamptz, 'published'),
    -- course/python/assignment/files (bound to L20)
    ('95d707a8-346b-5d81-bb07-1eee39247bba', '68ea4540-e9da-5605-9054-28b29afa0992', '7cdc242d-2dc4-51fa-abaa-852d131818bd', 'File Input and Output Assignment', 'Write a Python program that opens an input text file using with open(...), strips blank lines, counts non-empty lines, handles FileNotFoundError with try/except, and writes a summary report to an output text file. Submit your source code and sample run report as a single PDF or ZIP archive.', current_setting('codemastery.seed_assignment_files_deadline')::timestamptz, 'published');

DO $sd04$
DECLARE
    python_course constant uuid := '68ea4540-e9da-5605-9054-28b29afa0992';
    lesson_l10 constant uuid := '3b413f38-5f6d-50c2-a28a-6ebbc9f70a12';
    lesson_l20 constant uuid := '7cdc242d-2dc4-51fa-abaa-852d131818bd';
    expected_question record;
    expected_quiz record;
    expected_member record;
    expected_assignment record;
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM public.courses
        WHERE id = python_course
          AND instructor_id = '1eacfb14-e494-596c-bb53-3d012e7caf72'
          AND publication_status = 'published'
    ) THEN
        RAISE EXCEPTION 'SD04 requires the SD02 course/python Course; load 02-course-python-fundamentals.sql with seed_dataset final-v1 first';
    END IF;

    IF (
        SELECT count(*)
        FROM public.lessons l
        JOIN public.chapters ch ON ch.id = l.chapter_id AND ch.course_id = python_course
        WHERE l.id IN (lesson_l10, lesson_l20)
          AND l.publication_status = 'published'
    ) <> 2 THEN
        RAISE EXCEPTION 'SD04 requires published course/python Lessons L10 and L20; load 02-course-python-fundamentals.sql with seed_dataset final-v1 first';
    END IF;

    IF (
        SELECT count(*)
        FROM sd04_skill_ref ref
        JOIN public.skills s ON s.id = ref.skill_id
                            AND s.course_id = python_course
                            AND s.name = ref.name
    ) <> 20 THEN
        RAISE EXCEPTION 'SD04 requires the twenty SD03 course/python skills S01-S20; load 03-skill-graph-and-lesson-mapping.sql first';
    END IF;

    FOR expected_question IN SELECT * FROM sd04_question_seed ORDER BY id LOOP
        IF EXISTS (
            SELECT 1
            FROM public.questions existing_q
            WHERE existing_q.id = expected_question.id
              AND existing_q.skill_id = expected_question.skill_id
              AND existing_q.prompt = expected_question.prompt
              AND existing_q.kind = expected_question.kind
              AND existing_q.difficulty = expected_question.difficulty
              AND existing_q.options = expected_question.options
              AND existing_q.correct_options = expected_question.correct_options
        ) THEN
            CONTINUE;
        END IF;

        IF EXISTS (SELECT 1 FROM public.questions WHERE id = expected_question.id) THEN
            RAISE EXCEPTION 'SD04 Question % conflicts with an existing Question', expected_question.id;
        END IF;

        INSERT INTO public.questions (id, skill_id, prompt, kind, difficulty, options, correct_options)
        VALUES (
            expected_question.id,
            expected_question.skill_id,
            expected_question.prompt,
            expected_question.kind,
            expected_question.difficulty,
            expected_question.options,
            expected_question.correct_options
        );
    END LOOP;

    FOR expected_quiz IN SELECT * FROM sd04_quiz_seed ORDER BY id LOOP
        IF EXISTS (
            SELECT 1
            FROM public.quizzes existing_quiz
            WHERE existing_quiz.id = expected_quiz.id
              AND existing_quiz.course_id = expected_quiz.course_id
              AND existing_quiz.title = expected_quiz.title
              AND existing_quiz.kind = expected_quiz.kind
              AND existing_quiz.time_limit_seconds = expected_quiz.time_limit_seconds
              AND existing_quiz.publication_status = expected_quiz.publication_status
        ) THEN
            CONTINUE;
        END IF;

        IF EXISTS (SELECT 1 FROM public.quizzes WHERE id = expected_quiz.id) THEN
            RAISE EXCEPTION 'SD04 Quiz % conflicts with an existing Quiz', expected_quiz.id;
        END IF;

        INSERT INTO public.quizzes (id, course_id, title, kind, time_limit_seconds, publication_status)
        VALUES (
            expected_quiz.id,
            expected_quiz.course_id,
            expected_quiz.title,
            expected_quiz.kind,
            expected_quiz.time_limit_seconds,
            expected_quiz.publication_status
        );
    END LOOP;

    FOR expected_member IN SELECT * FROM sd04_quiz_question_seed ORDER BY quiz_id, position LOOP
        IF EXISTS (
            SELECT 1
            FROM public.quiz_questions existing_member
            WHERE existing_member.id = expected_member.id
              AND existing_member.quiz_id = expected_member.quiz_id
              AND existing_member.question_id = expected_member.question_id
              AND existing_member.position = expected_member.position
        ) THEN
            CONTINUE;
        END IF;

        IF EXISTS (
            SELECT 1
            FROM public.quiz_questions existing_member
            WHERE existing_member.id = expected_member.id
               OR (existing_member.quiz_id = expected_member.quiz_id
                   AND existing_member.question_id = expected_member.question_id)
               OR (existing_member.quiz_id = expected_member.quiz_id
                   AND existing_member.position = expected_member.position)
        ) THEN
            RAISE EXCEPTION 'SD04 quiz question % conflicts with an existing membership ID, question or position', expected_member.id;
        END IF;

        INSERT INTO public.quiz_questions (id, quiz_id, question_id, position)
        VALUES (
            expected_member.id,
            expected_member.quiz_id,
            expected_member.question_id,
            expected_member.position
        );
    END LOOP;

    IF EXISTS (
        SELECT 1
        FROM public.quiz_questions existing_member
        WHERE existing_member.quiz_id IN (SELECT id FROM sd04_quiz_seed)
          AND existing_member.id NOT IN (SELECT id FROM sd04_quiz_question_seed)
    ) THEN
        RAISE EXCEPTION 'SD04 seeded Quizzes already contain membership rows outside the approved set';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM public.questions existing_q
        WHERE existing_q.skill_id IN (SELECT skill_id FROM sd04_skill_ref)
          AND existing_q.id NOT IN (SELECT id FROM sd04_question_seed)
    ) THEN
        RAISE EXCEPTION 'SD04 course/python skills already have Questions outside the approved bank';
    END IF;

    FOR expected_assignment IN SELECT * FROM sd04_assignment_seed ORDER BY id LOOP
        IF EXISTS (
            SELECT 1
            FROM public.assignments existing_assignment
            WHERE existing_assignment.id = expected_assignment.id
              AND existing_assignment.course_id = expected_assignment.course_id
              AND existing_assignment.lesson_id IS NOT DISTINCT FROM expected_assignment.lesson_id
              AND existing_assignment.title = expected_assignment.title
              AND existing_assignment.instructions = expected_assignment.instructions
              AND existing_assignment.deadline_at = expected_assignment.deadline_at
              AND existing_assignment.publication_status = expected_assignment.publication_status
        ) THEN
            CONTINUE;
        END IF;

        IF EXISTS (SELECT 1 FROM public.assignments WHERE id = expected_assignment.id) THEN
            RAISE EXCEPTION 'SD04 Assignment % conflicts with an existing Assignment', expected_assignment.id;
        END IF;

        INSERT INTO public.assignments (id, course_id, lesson_id, title, instructions, deadline_at, publication_status)
        VALUES (
            expected_assignment.id,
            expected_assignment.course_id,
            expected_assignment.lesson_id,
            expected_assignment.title,
            expected_assignment.instructions,
            expected_assignment.deadline_at,
            expected_assignment.publication_status
        );
    END LOOP;
END
$sd04$;

COMMIT;

