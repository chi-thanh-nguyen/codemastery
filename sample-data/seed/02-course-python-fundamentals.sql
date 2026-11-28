-- CodeMastery interim-v1 synthetic Course seed (SD02).
--
-- Local/test-only: never run against a shared, staging or production database.
-- The target must be a disposable database attested as described in
-- sample-data/README.md; otherwise this script stops before any lock or write.
-- Load 01-users.sql first: every Course references its synthetic Instructor.
--
-- Input, as a psql variable or, when unset, an environment variable:
--   seed_target_system_identifier   CODEMASTERY_SEED_TARGET_SYSTEM_IDENTIFIER
--
-- IDs are UUIDv5 values in the standard URL namespace for the name
-- https://codemastery.test/interim-v1/<logical-key>; each row names its key.
-- Course, Chapter and Lesson rows are the ADR002 CT15.I-data records. The text
-- LearningMaterial rows are the SD02 material addition recorded in ADR002
-- (CT15.SD02-materials); their English text is original synthetic content.
-- No file or external-video material is seeded: see sample-data/README.md.
--
-- The file also carries the final-v1 Course course/python of ADR013 §4.2
-- (CT15.F-adaptive, SD03): IDs are UUIDv5 values for
-- https://codemastery.test/final-v1/<logical-key>, owned by the reused interim-v1
-- Instructor. Lesson Lk of the canonical order L01-L20 is titled after skill Sk.
-- Its adaptive policy carries no committed number: it is supplied at load time and
-- validated against ADR005 §6.2 before any lock or write. Inputs, as psql variables
-- or, when unset, environment variables (approved demonstration values, ADR013
-- §16.1: 1/2, 4/5 and 6):
--   adaptive_weak                          CODEMASTERY_SEED_ADAPTIVE_WEAK
--   adaptive_mastered                      CODEMASTERY_SEED_ADAPTIVE_MASTERED
--   adaptive_minimum_questions_per_skill   CODEMASTERY_SEED_ADAPTIVE_MINIMUM_QUESTIONS_PER_SKILL
-- weak and mastered are "numerator/denominator" with positive decimal integers.

\set ON_ERROR_STOP on
\set VERBOSITY terse

\if :{?seed_target_system_identifier}
\else
\getenv seed_target_system_identifier CODEMASTERY_SEED_TARGET_SYSTEM_IDENTIFIER
\endif

\if :{?seed_target_system_identifier}
\else
DO $sd02_input$
BEGIN
    RAISE EXCEPTION 'Required input seed_target_system_identifier is missing.';
END;
$sd02_input$;
\endif

\if :{?adaptive_weak}
\else
\getenv adaptive_weak CODEMASTERY_SEED_ADAPTIVE_WEAK
\endif

\if :{?adaptive_weak}
\else
DO $sd02_input$
BEGIN
    RAISE EXCEPTION 'Required input adaptive_weak is missing.';
END;
$sd02_input$;
\endif

\if :{?adaptive_mastered}
\else
\getenv adaptive_mastered CODEMASTERY_SEED_ADAPTIVE_MASTERED
\endif

\if :{?adaptive_mastered}
\else
DO $sd02_input$
BEGIN
    RAISE EXCEPTION 'Required input adaptive_mastered is missing.';
END;
$sd02_input$;
\endif

\if :{?adaptive_minimum_questions_per_skill}
\else
\getenv adaptive_minimum_questions_per_skill CODEMASTERY_SEED_ADAPTIVE_MINIMUM_QUESTIONS_PER_SKILL
\endif

\if :{?adaptive_minimum_questions_per_skill}
\else
DO $sd02_input$
BEGIN
    RAISE EXCEPTION 'Required input adaptive_minimum_questions_per_skill is missing.';
END;
$sd02_input$;
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

DO $sd02_target$
DECLARE
    expected_identifier text := current_setting('codemastery.seed_expected_target');
    actual_identifier text;
    attestation text;
BEGIN
    IF expected_identifier !~ '^[1-9][0-9]{0,19}$' THEN
        RAISE EXCEPTION 'SD02 target verification failed: malformed expected system identifier';
    END IF;

    BEGIN
        SELECT system_identifier::text INTO actual_identifier FROM pg_control_system();
    EXCEPTION WHEN OTHERS THEN
        actual_identifier := NULL;
    END;
    IF actual_identifier IS NULL THEN
        RAISE EXCEPTION 'SD02 target verification failed: cluster identity is unavailable';
    END IF;
    IF actual_identifier <> expected_identifier THEN
        RAISE EXCEPTION 'SD02 target verification failed: connected cluster is not the expected target';
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
        RAISE EXCEPTION 'SD02 target verification failed: database is not attested as a disposable seed target';
    END IF;
END
$sd02_target$;

-- ADR005 §6.2 policy schema, checked before any lock or write: every integer in
-- 1..2147483647, numerator <= denominator and exactly 0 < weak < mastered <= 1.
SET LOCAL codemastery.seed_adaptive_weak = :'adaptive_weak';
SET LOCAL codemastery.seed_adaptive_mastered = :'adaptive_mastered';
SET LOCAL codemastery.seed_adaptive_minimum_questions_per_skill = :'adaptive_minimum_questions_per_skill';

DO $sd02_policy$
DECLARE
    weak text := current_setting('codemastery.seed_adaptive_weak');
    mastered text := current_setting('codemastery.seed_adaptive_mastered');
    minimum text := current_setting('codemastery.seed_adaptive_minimum_questions_per_skill');
    wn bigint;
    wd bigint;
    mn bigint;
    md bigint;
BEGIN
    IF weak !~ '^[1-9][0-9]{0,9}/[1-9][0-9]{0,9}$'
       OR mastered !~ '^[1-9][0-9]{0,9}/[1-9][0-9]{0,9}$'
       OR minimum !~ '^[1-9][0-9]{0,9}$' THEN
        RAISE EXCEPTION 'SD02 requires a valid adaptive policy (ADR005 6.2)';
    END IF;
    wn := split_part(weak, '/', 1)::bigint;
    wd := split_part(weak, '/', 2)::bigint;
    mn := split_part(mastered, '/', 1)::bigint;
    md := split_part(mastered, '/', 2)::bigint;
    IF greatest(wn, wd, mn, md, minimum::bigint) > 2147483647
       OR wn > wd OR mn > md OR wn * md >= mn * wd THEN
        RAISE EXCEPTION 'SD02 requires a valid adaptive policy (ADR005 6.2)';
    END IF;
END
$sd02_policy$;

-- Keep the referenced Instructor stable, then serialize loaders and other
-- Course-content writers before taking a data snapshot. Lock order matches SD01.
LOCK TABLE public.users IN SHARE MODE;
LOCK TABLE public.courses, public.chapters, public.lessons, public.learning_materials
    IN SHARE ROW EXCLUSIVE MODE;

CREATE TEMP TABLE sd02_course_seed (
    id uuid NOT NULL,
    instructor_id uuid NOT NULL,
    title text NOT NULL,
    description text NOT NULL,
    prerequisite_description text NULL,
    publication_status text NOT NULL,
    adaptive_enabled boolean NOT NULL,
    adaptive_settings jsonb NULL
) ON COMMIT DROP;

-- course/intro, course/practice, course/draft
INSERT INTO sd02_course_seed (id, instructor_id, title, description, prerequisite_description, publication_status, adaptive_enabled, adaptive_settings)
VALUES
    ('bd8ce62d-90d6-525b-ae6c-652c53ccfe6a', '1eacfb14-e494-596c-bb53-3d012e7caf72', 'Introduction to Programming', 'Demo course for the Interim catalogue', 'No prerequisites', 'published', false, NULL),
    ('824937a6-f333-580a-96af-7b9b2c57453b', '1eacfb14-e494-596c-bb53-3d012e7caf72', 'Programming Practice', 'Demo course for the Interim catalogue', 'No prerequisites', 'published', false, NULL),
    ('34ea40e0-54fd-5fb6-a402-5df72e49c45a', '1eacfb14-e494-596c-bb53-3d012e7caf72', 'Instructor Draft', 'Demo course for the Interim catalogue', 'No prerequisites', 'hidden', false, NULL);

-- course/python (final-v1, ADR013 §4.2 CT15.F-adaptive): adaptive policy from the validated inputs,
-- each rational pair stored reduced by its greatest common divisor (ADR005 §6.2).
INSERT INTO sd02_course_seed (id, instructor_id, title, description, prerequisite_description, publication_status, adaptive_enabled, adaptive_settings)
SELECT '68ea4540-e9da-5605-9054-28b29afa0992', '1eacfb14-e494-596c-bb53-3d012e7caf72', 'Python Fundamentals',
       'Learn Python step by step, from displaying output to reading and writing files.', 'No prerequisites', 'published', true,
       jsonb_build_object(
           'weak', jsonb_build_object('numerator', wn / gcd(wn, wd), 'denominator', wd / gcd(wn, wd)),
           'mastered', jsonb_build_object('numerator', mn / gcd(mn, md), 'denominator', md / gcd(mn, md)),
           'minimumQuestionsPerSkill', minimum)
FROM (
    SELECT split_part(current_setting('codemastery.seed_adaptive_weak'), '/', 1)::bigint AS wn,
           split_part(current_setting('codemastery.seed_adaptive_weak'), '/', 2)::bigint AS wd,
           split_part(current_setting('codemastery.seed_adaptive_mastered'), '/', 1)::bigint AS mn,
           split_part(current_setting('codemastery.seed_adaptive_mastered'), '/', 2)::bigint AS md,
           current_setting('codemastery.seed_adaptive_minimum_questions_per_skill')::bigint AS minimum
) policy;

CREATE TEMP TABLE sd02_chapter_seed (
    id uuid NOT NULL,
    course_id uuid NOT NULL,
    title text NOT NULL,
    position integer NOT NULL
) ON COMMIT DROP;

-- course/intro/chapter/1, course/practice/chapter/1
INSERT INTO sd02_chapter_seed (id, course_id, title, position)
VALUES
    ('b87c15db-8ae8-5458-ac5d-ab347cdc70aa', 'bd8ce62d-90d6-525b-ae6c-652c53ccfe6a', 'Getting Started', 1),
    ('49fe9687-cf8a-5397-b704-fb6b2f3d5930', '824937a6-f333-580a-96af-7b9b2c57453b', 'Getting Started', 1);

-- course/python/chapter/1..6 (final-v1)
INSERT INTO sd02_chapter_seed (id, course_id, title, position)
VALUES
    ('f23a7485-11cf-59e1-a6ae-49fceda9a1e3', '68ea4540-e9da-5605-9054-28b29afa0992', 'Basics', 1),
    ('ba09ec27-3514-5704-8cb8-ef14afe3f6b1', '68ea4540-e9da-5605-9054-28b29afa0992', 'Decisions', 2),
    ('d76443d3-0c5e-5e5c-aea9-f94df466764d', '68ea4540-e9da-5605-9054-28b29afa0992', 'Loops', 3),
    ('4a95963f-e6af-52b9-8236-33ba960b1474', '68ea4540-e9da-5605-9054-28b29afa0992', 'Collections', 4),
    ('8712c346-d667-58c5-9ea8-e3d5d5a9a93e', '68ea4540-e9da-5605-9054-28b29afa0992', 'Functions', 5),
    ('b18b2d05-06fe-5e92-b59a-aa384320819e', '68ea4540-e9da-5605-9054-28b29afa0992', 'Text, errors and files', 6);

CREATE TEMP TABLE sd02_lesson_seed (
    id uuid NOT NULL,
    chapter_id uuid NOT NULL,
    title text NOT NULL,
    content_markdown text NULL,
    position integer NOT NULL,
    publication_status text NOT NULL
) ON COMMIT DROP;

-- <course-key>/chapter/1/lesson/1 and <course-key>/chapter/1/lesson/2
INSERT INTO sd02_lesson_seed (id, chapter_id, title, content_markdown, position, publication_status)
VALUES
    ('b69f291a-e0b3-5698-87c6-db1f21126802', 'b87c15db-8ae8-5458-ac5d-ab347cdc70aa', 'Concepts', NULL, 1, 'published'),
    ('824f67f9-9f73-5ff1-ac74-6e4f629bb432', 'b87c15db-8ae8-5458-ac5d-ab347cdc70aa', 'Practice', NULL, 2, 'published'),
    ('417c19ff-5c93-5aaa-a4d8-883f20574b55', '49fe9687-cf8a-5397-b704-fb6b2f3d5930', 'Concepts', NULL, 1, 'published'),
    ('bb0e05c9-ba94-5cb8-a861-39a999266373', '49fe9687-cf8a-5397-b704-fb6b2f3d5930', 'Practice', NULL, 2, 'published');

-- course/python Lessons L01-L20 (final-v1), one per skill Sk of 03-skill-graph-and-lesson-mapping.sql
INSERT INTO sd02_lesson_seed (id, chapter_id, title, content_markdown, position, publication_status)
VALUES
    -- course/python/chapter/1/lesson/1 (L01)
    ('28a0dcbc-cc05-59f7-bcf2-7ab591581299', 'f23a7485-11cf-59e1-a6ae-49fceda9a1e3', 'Program Output', NULL, 1, 'published'),
    -- course/python/chapter/1/lesson/2 (L02)
    ('405046b8-4f5b-5ffc-bbd0-bf6b6f705844', 'f23a7485-11cf-59e1-a6ae-49fceda9a1e3', 'Variables and Assignment', NULL, 2, 'published'),
    -- course/python/chapter/1/lesson/3 (L03)
    ('97d15c80-c325-5074-bc9f-ac96d82a1aff', 'f23a7485-11cf-59e1-a6ae-49fceda9a1e3', 'Numbers and Arithmetic', NULL, 3, 'published'),
    -- course/python/chapter/1/lesson/4 (L04)
    ('fe1aaecf-d49a-5da1-81df-33924f779b1e', 'f23a7485-11cf-59e1-a6ae-49fceda9a1e3', 'Strings', NULL, 4, 'published'),
    -- course/python/chapter/1/lesson/5 (L05)
    ('c861e5e7-7cb2-5db9-b60b-650a9ef3d3af', 'f23a7485-11cf-59e1-a6ae-49fceda9a1e3', 'Input and Type Conversion', NULL, 5, 'published'),
    -- course/python/chapter/2/lesson/1 (L06)
    ('19c599c1-75b9-532b-88f9-edfbfeb1b2e1', 'ba09ec27-3514-5704-8cb8-ef14afe3f6b1', 'Booleans and Comparisons', NULL, 1, 'published'),
    -- course/python/chapter/2/lesson/2 (L07)
    ('7359f85d-917a-524a-9dfa-4bafce55fb3b', 'ba09ec27-3514-5704-8cb8-ef14afe3f6b1', 'Conditionals', NULL, 2, 'published'),
    -- course/python/chapter/3/lesson/1 (L08)
    ('16c0ed9e-7d97-5d21-b1d5-6d2d517b0605', 'd76443d3-0c5e-5e5c-aea9-f94df466764d', 'While Loops', NULL, 1, 'published'),
    -- course/python/chapter/3/lesson/2 (L09)
    ('75e8e26f-9800-5b2a-ae80-8578e4a2b4bc', 'd76443d3-0c5e-5e5c-aea9-f94df466764d', 'For Loops', NULL, 2, 'published'),
    -- course/python/chapter/3/lesson/3 (L10)
    ('3b413f38-5f6d-50c2-a28a-6ebbc9f70a12', 'd76443d3-0c5e-5e5c-aea9-f94df466764d', 'Loop Control', NULL, 3, 'published'),
    -- course/python/chapter/4/lesson/1 (L11)
    ('554f05ef-3e4d-5b06-9fc3-21bb48841877', '4a95963f-e6af-52b9-8236-33ba960b1474', 'Lists', NULL, 1, 'published'),
    -- course/python/chapter/4/lesson/2 (L12)
    ('aa4beb45-b8f6-573d-b3c2-c274eeea7d53', '4a95963f-e6af-52b9-8236-33ba960b1474', 'List Iteration Patterns', NULL, 2, 'published'),
    -- course/python/chapter/4/lesson/3 (L13)
    ('cd209ec7-e47a-5db1-8bfe-fcba36f657ac', '4a95963f-e6af-52b9-8236-33ba960b1474', 'Tuples and Sets', NULL, 3, 'published'),
    -- course/python/chapter/4/lesson/4 (L14)
    ('dcf76c4d-40d3-50c1-904e-0a3ba39530dc', '4a95963f-e6af-52b9-8236-33ba960b1474', 'Dictionaries', NULL, 4, 'published'),
    -- course/python/chapter/5/lesson/1 (L15)
    ('6e81f2f3-5f15-55b6-a67e-8bc741b0c3b3', '8712c346-d667-58c5-9ea8-e3d5d5a9a93e', 'Functions', NULL, 1, 'published'),
    -- course/python/chapter/5/lesson/2 (L16)
    ('8298297a-4a97-5acc-bc68-a21e2d912461', '8712c346-d667-58c5-9ea8-e3d5d5a9a93e', 'Parameters and Return Values', NULL, 2, 'published'),
    -- course/python/chapter/5/lesson/3 (L17)
    ('26e6a91e-cc44-58e6-ab31-3f26d707c534', '8712c346-d667-58c5-9ea8-e3d5d5a9a93e', 'Variable Scope', NULL, 3, 'published'),
    -- course/python/chapter/6/lesson/1 (L18)
    ('66bf3619-0f99-505c-af0a-d622c71be14f', 'b18b2d05-06fe-5e92-b59a-aa384320819e', 'String Methods and Formatting', NULL, 1, 'published'),
    -- course/python/chapter/6/lesson/2 (L19)
    ('d12de280-e91d-5536-b384-34575ed8cb20', 'b18b2d05-06fe-5e92-b59a-aa384320819e', 'Exceptions', NULL, 2, 'published'),
    -- course/python/chapter/6/lesson/3 (L20)
    ('7cdc242d-2dc4-51fa-abaa-852d131818bd', 'b18b2d05-06fe-5e92-b59a-aa384320819e', 'File Input and Output', NULL, 3, 'published');

CREATE TEMP TABLE sd02_material_seed (
    id uuid NOT NULL,
    lesson_id uuid NOT NULL,
    title text NOT NULL,
    material_type text NOT NULL,
    publication_status text NOT NULL,
    text_content text NULL,
    object_key text NULL,
    original_filename text NULL,
    external_video_url text NULL
) ON COMMIT DROP;

INSERT INTO sd02_material_seed (id, lesson_id, title, material_type, publication_status, text_content, object_key, original_filename, external_video_url)
VALUES
    -- course/intro/chapter/1/lesson/1/material/1
    ('a1bb3d63-1519-5ab6-87dc-8a4468f82022', 'b69f291a-e0b3-5698-87c6-db1f21126802', 'What a Program Is', 'text', 'published', $sd02_text$A program is a sequence of instructions that a computer follows exactly, one step at a time.

Most beginner programs combine three ideas:
- Values, such as the number 42 or the text "hello".
- Variables, which are names that refer to values so you can use them later.
- Statements, which tell the computer to do something, such as calculate a result or display it.

In Python, the statement
    total = 3 + 4
calculates 7 and makes the name total refer to it. The statement
    print(total)
then displays 7.

The computer does not guess what you meant. If an instruction is misspelled or appears in the wrong order, the program stops with an error or produces a different result. Reading each line in order and asking "what value does each name refer to now?" is the most reliable way to understand a short program.$sd02_text$, NULL, NULL, NULL),
    -- course/intro/chapter/1/lesson/2/material/1
    ('7ca42bd5-0c62-53d6-9cb2-d614cb4a8a59', '824f67f9-9f73-5ff1-ac74-6e4f629bb432', 'Practice: Variables and Output', 'text', 'published', $sd02_text$Work through these exercises in order. Predict the result before you run anything.

1. Write two statements: the first makes the name price refer to 12, and the second displays price.
2. What does this program display?
       count = 5
       count = count + 1
       print(count)
3. A program contains the line print(score), but no earlier line assigns a value to score. Explain why the program stops with an error, and add one line that fixes it.
4. Choose clear variable names for a learner's age, the number of completed lessons and a course title. Explain why each name is easy to read.

Check your answer to exercise 2: the program displays 6, because the second line replaces the old value of count with the result of 5 + 1.$sd02_text$, NULL, NULL, NULL),
    -- course/practice/chapter/1/lesson/1/material/1
    ('8e74fd91-2b8e-5f74-ae12-32cb310de9c5', '417c19ff-5c93-5aaa-a4d8-883f20574b55', 'Breaking a Problem into Steps', 'text', 'published', $sd02_text$Before writing code, describe the problem in plain language and split it into small steps. A useful pattern is input, processing and output:

- Input: what information does the program start with?
- Processing: what calculations or decisions turn that input into a result?
- Output: what should the program show or return?

Example: find the average of three quiz scores.
- Input: the three scores.
- Processing: add the scores, then divide the sum by 3.
- Output: display the average.

When a step is still too large to write as one or two lines of code, split it again. Then trace your plan with small example values, such as 6, 8 and 10, whose average is 8. If the traced result is wrong, fix the plan before writing more code.$sd02_text$, NULL, NULL, NULL),
    -- course/practice/chapter/1/lesson/2/material/1
    ('05368d30-9616-575e-b680-9e1614e22566', 'bb0e05c9-ba94-5cb8-a861-39a999266373', 'Practice: Trace and Fix Short Programs', 'text', 'published', $sd02_text$For each exercise, trace the program line by line and record the value of every variable after each line.

1. Trace this program and state what it displays:
       a = 2
       b = a * 3
       a = b - 1
       print(a, b)
2. This program should display the average of 4 and 8, but it displays 8.0:
       first = 4
       second = 8
       average = first + second / 2
       print(average)
   Explain the mistake and correct the third line.
3. Write the input, processing and output steps for a program that converts a distance in kilometres to metres.

Check your answers: exercise 1 displays 5 6. In exercise 2, division happens before addition, so the third line should be average = (first + second) / 2.$sd02_text$, NULL, NULL, NULL),
    -- course/practice/chapter/1/lesson/2/material/2 (hidden visibility contrast)
    ('500fddc5-eed2-588c-b39a-f82adc3d24ca', 'bb0e05c9-ba94-5cb8-a861-39a999266373', 'Instructor Notes', 'text', 'hidden', $sd02_text$Facilitation notes for the owning Instructor. This material is hidden, so it is not part of the published learner content.

Let learners attempt each tracing exercise individually, then compare traces in pairs before revealing the answers. Learners who confuse assignment with equality usually benefit from rewriting a line such as count = count + 1 in words: "calculate count plus one, then make count refer to the result."$sd02_text$, NULL, NULL, NULL);

-- course/python text materials (final-v1): original synthetic English text, one published material per Lesson.
-- L01's PDF file material is provisioned after loading through the CT09 upload operation, never by this seed.
INSERT INTO sd02_material_seed (id, lesson_id, title, material_type, publication_status, text_content, object_key, original_filename, external_video_url)
VALUES
    -- course/python/chapter/1/lesson/1/material/1
    ('afa920a8-dbd9-51a0-8f8a-8223e3b917fd', '28a0dcbc-cc05-59f7-bcf2-7ab591581299', 'Displaying Results with print', 'text', 'published', $sd02_text$The print function displays values on the screen. Everything a beginner program shows to its user goes through print.

    print("Hello, world!")
    print(7)
    print("Total:", 3 + 4)

Each call to print starts a new line. When you give print several values separated by commas, it displays them on one line with a single space between them, so the last line above displays Total: 7.

Text must be written inside quotation marks; numbers must not. print("3 + 4") displays the characters 3 + 4, while print(3 + 4) displays 7.

Quick check: what does print("Score", 10, "points") display? Answer: Score 10 points.$sd02_text$, NULL, NULL, NULL),
    -- course/python/chapter/1/lesson/2/material/1
    ('8a880a97-f7e4-5c53-b0ec-61d4d3502bb7', '405046b8-4f5b-5ffc-bbd0-bf6b6f705844', 'Naming Values with Variables', 'text', 'published', $sd02_text$A variable is a name that refers to a value. The assignment statement creates or updates that link:

    lessons = 20
    title = "Python Fundamentals"

Read = as "now refers to", not as "is equal to". The right-hand side is calculated first, then the name on the left is made to refer to the result:

    count = 5
    count = count + 1    # count now refers to 6

A name must be assigned before it is used, otherwise Python stops with a NameError. Names are case-sensitive, may contain letters, digits and underscores, and may not start with a digit. Choose names that describe the value, such as completed_lessons rather than x.

Quick check: after a = 2, b = a and a = 5, what does b refer to? Answer: 2, because b received the value a referred to at that moment.$sd02_text$, NULL, NULL, NULL),
    -- course/python/chapter/1/lesson/3/material/1
    ('f40bb93c-b656-52b1-9f57-544f7bf08c60', '97d15c80-c325-5074-bc9f-ac96d82a1aff', 'Working with Numbers', 'text', 'published', $sd02_text$Python has whole numbers (int), such as 12, and decimal numbers (float), such as 3.5. The arithmetic operators are:

    +   addition          7 + 2   gives 9
    -   subtraction       7 - 2   gives 5
    *   multiplication    7 * 2   gives 14
    /   division          7 / 2   gives 3.5
    //  floor division    7 // 2  gives 3
    %   remainder         7 % 2   gives 1
    **  power             7 ** 2  gives 49

Multiplication and division happen before addition and subtraction, so 2 + 3 * 4 gives 14. Use parentheses to change the order: (2 + 3) * 4 gives 20.

The / operator always produces a float, even when the result is whole: 8 / 2 gives 4.0.

Quick check: how many full weeks are in 45 days, and how many days are left over? Answer: 45 // 7 gives 6 and 45 % 7 gives 3.$sd02_text$, NULL, NULL, NULL),
    -- course/python/chapter/1/lesson/4/material/1
    ('25f0f6e4-0413-53bb-b2c9-f8eae6ffdb7d', 'fe1aaecf-d49a-5da1-81df-33924f779b1e', 'Text Values and Strings', 'text', 'published', $sd02_text$A string is a sequence of characters written between quotation marks. Single and double quotes work the same way:

    name = "Ada"
    greeting = 'Hello'

Joining strings with + is called concatenation, and * repeats a string:

    message = greeting + ", " + name    # "Hello, Ada"
    line = "-" * 10                     # "----------"

len(text) gives the number of characters. Each character has a position, called an index, starting at 0: name[0] is "A" and name[-1] is the last character, "a". A slice such as name[0:2] gives the characters from index 0 up to, but not including, index 2.

Strings cannot be changed in place. Operations such as + create a new string.

Quick check: what is "code"[1:3]? Answer: "od".$sd02_text$, NULL, NULL, NULL),
    -- course/python/chapter/1/lesson/5/material/1
    ('18f7b232-b1f4-59a2-b615-595430c585be', 'c861e5e7-7cb2-5db9-b60b-650a9ef3d3af', 'Reading Input and Converting Types', 'text', 'published', $sd02_text$The input function shows a prompt, waits for the user to type a line and returns that line as a string:

    name = input("What is your name? ")

input always returns a string, even when the user types digits. To calculate with the value, convert it first:

    age = int(input("Your age: "))
    height = float(input("Your height in metres: "))

int("42") gives 42 and float("1.75") gives 1.75. Going the other way, str(42) gives "42", which you need when joining a number to text with +:

    print("Next year you will be " + str(age + 1))

Converting text that is not a number, such as int("forty"), stops the program with a ValueError.

Quick check: if the user types 5, what does input() + "1" give? Answer: "51", because both values are strings.$sd02_text$, NULL, NULL, NULL),
    -- course/python/chapter/2/lesson/1/material/1
    ('b189975d-e9b0-57f3-b540-5dad9a9c1e98', '19c599c1-75b9-532b-88f9-edfbfeb1b2e1', 'True, False and Comparisons', 'text', 'published', $sd02_text$A Boolean value is either True or False. Comparisons produce Booleans:

    ==  equal to                 3 == 3    gives True
    !=  not equal to             3 != 4    gives True
    <   less than                2 < 1     gives False
    <=  less than or equal to    2 <= 2    gives True
    >   greater than
    >=  greater than or equal to

Note the difference between = (assignment) and == (comparison).

Combine conditions with and, or and not:

    age >= 13 and age <= 19    # True only for teenagers
    day == "Sat" or day == "Sun"
    not finished

and is True only when both sides are True; or is True when at least one side is True; not reverses a value.

Quick check: with score = 72, what is score >= 50 and score < 70? Answer: False, because the second comparison is False.$sd02_text$, NULL, NULL, NULL),
    -- course/python/chapter/2/lesson/2/material/1
    ('4675daa7-863d-525c-acb9-1bd81dd25aa1', '7359f85d-917a-524a-9dfa-4bafce55fb3b', 'Making Decisions with if', 'text', 'published', $sd02_text$An if statement runs a block of code only when its condition is True. The block is the indented lines after the colon:

    if temperature > 30:
        print("It is hot.")

Add else for the opposite case, and elif to test further conditions in order:

    if score >= 80:
        grade = "A"
    elif score >= 50:
        grade = "Pass"
    else:
        grade = "Retry"

Python checks the conditions from top to bottom and runs only the first block whose condition is True. Indentation is part of the syntax: every line of a block must be indented by the same amount, usually four spaces.

Quick check: with score = 85, which grade is assigned? Answer: "A". The elif condition is also True, but it is never checked.$sd02_text$, NULL, NULL, NULL),
    -- course/python/chapter/3/lesson/1/material/1
    ('9dd03b50-1153-575e-9d99-5b203ae180e7', '16c0ed9e-7d97-5d21-b1d5-6d2d517b0605', 'Repeating with while', 'text', 'published', $sd02_text$A while loop repeats its block as long as its condition stays True:

    count = 1
    while count <= 3:
        print(count)
        count = count + 1
    print("Done")

This displays 1, 2, 3 and then Done. Python checks the condition before every repetition; when it becomes False, the program continues after the loop.

Something inside the loop must eventually make the condition False. If count were never increased, the loop would run forever.

A while loop is the natural choice when you do not know in advance how many repetitions you need, for example repeating a question until the user gives a valid answer.

Quick check: how many times does the body run if count starts at 5 and the condition is count <= 3? Answer: zero times.$sd02_text$, NULL, NULL, NULL),
    -- course/python/chapter/3/lesson/2/material/1
    ('654b2bdd-2214-5356-9db5-1f3fae56110d', '75e8e26f-9800-5b2a-ae80-8578e4a2b4bc', 'Repeating with for and range', 'text', 'published', $sd02_text$A for loop runs its block once for each item of a sequence:

    for letter in "abc":
        print(letter)

range produces a sequence of whole numbers:

    range(5)          0, 1, 2, 3, 4
    range(2, 6)       2, 3, 4, 5
    range(0, 10, 3)   0, 3, 6, 9

The end value is never included. A common pattern adds up values:

    total = 0
    for number in range(1, 5):
        total = total + number
    print(total)    # 10

Use for when you know the sequence to process; use while when the number of repetitions depends on a condition that changes inside the loop.

Quick check: what numbers does range(3, 0, -1) produce? Answer: 3, 2, 1.$sd02_text$, NULL, NULL, NULL),
    -- course/python/chapter/3/lesson/3/material/1
    ('cd08b2e3-4157-5ebb-b8d1-4a914dac5d2c', '3b413f38-5f6d-50c2-a28a-6ebbc9f70a12', 'Controlling Loops with break and continue', 'text', 'published', $sd02_text$break ends the loop immediately; the program continues after the loop:

    for number in range(1, 100):
        if number * number > 50:
            break
    print(number)    # 8

continue skips the rest of the current repetition and moves on to the next one:

    for number in range(1, 6):
        if number % 2 == 0:
            continue
        print(number)    # 1, 3, 5

Both work in for and while loops. A loop such as while True: runs until a break is reached, which is a clear way to repeat a question until the answer is valid:

    while True:
        answer = input("Type yes or no: ")
        if answer == "yes" or answer == "no":
            break

Quick check: in a loop over range(5) that runs continue when the number is 2, which numbers are printed? Answer: 0, 1, 3, 4.$sd02_text$, NULL, NULL, NULL),
    -- course/python/chapter/4/lesson/1/material/1
    ('484ea919-2783-5851-a1bd-54009085adbb', '554f05ef-3e4d-5b06-9fc3-21bb48841877', 'Storing Items in Lists', 'text', 'published', $sd02_text$A list holds several values in order, written between square brackets:

    scores = [72, 85, 90]
    names = ["Ada", "Linus"]

Lists use the same indexes as strings: scores[0] is 72 and scores[-1] is 90. Unlike strings, lists can be changed:

    scores[1] = 88          # replace an item
    scores.append(64)       # add at the end
    scores.remove(72)       # remove the first 72
    len(scores)             # number of items

The in operator tests membership: 90 in scores gives True.

Assigning a list to another name does not copy it. After other = scores, both names refer to the same list, so a change made through one name is visible through the other.

Quick check: after items = [1, 2] and items.append(3), what is len(items)? Answer: 3.$sd02_text$, NULL, NULL, NULL),
    -- course/python/chapter/4/lesson/2/material/1
    ('ec728434-8de8-5924-9b52-aaac0f626a55', 'aa4beb45-b8f6-573d-b3c2-c274eeea7d53', 'Common List Loops', 'text', 'published', $sd02_text$Most list programs combine a for loop with one of a few patterns.

Accumulate a result:

    total = 0
    for score in scores:
        total = total + score

Find the largest item:

    best = scores[0]
    for score in scores:
        if score > best:
            best = score

Count items that match a condition:

    passed = 0
    for score in scores:
        if score >= 50:
            passed = passed + 1

Build a new list:

    doubled = []
    for score in scores:
        doubled.append(score * 2)

When you also need the position, use enumerate: for index, score in enumerate(scores):. Avoid adding or removing items in the list you are looping over; build a new list instead.

Quick check: for scores = [40, 70, 55], what is passed after the counting loop? Answer: 2.$sd02_text$, NULL, NULL, NULL),
    -- course/python/chapter/4/lesson/3/material/1
    ('cdd7a70f-f89d-5674-a23b-8f7f31b21519', 'cd209ec7-e47a-5db1-8bfe-fcba36f657ac', 'Tuples and Sets', 'text', 'published', $sd02_text$A tuple is an ordered collection that cannot be changed after it is created. Write it with parentheses:

    point = (3, 4)
    x, y = point    # unpacking: x is 3, y is 4

Use a tuple for a fixed group of values that belong together, such as coordinates or a date.

A set is an unordered collection without duplicates. Write it with curly braces or build it with set():

    tags = {"python", "loops", "python"}    # {"python", "loops"}
    unique = set([1, 2, 2, 3])               # {1, 2, 3}

Sets answer membership questions quickly and support set operations:

    a | b    items in either set
    a & b    items in both sets
    a - b    items in a but not in b

Sets have no indexes, because their items have no order.

Quick check: what is len({1, 1, 2})? Answer: 2.$sd02_text$, NULL, NULL, NULL),
    -- course/python/chapter/4/lesson/4/material/1
    ('c91ed295-40a4-5738-a06a-cbdb058b77bf', 'dcf76c4d-40d3-50c1-904e-0a3ba39530dc', 'Looking Up Values with Dictionaries', 'text', 'published', $sd02_text$A dictionary maps keys to values. Write it with curly braces and key: value pairs:

    ages = {"Ada": 36, "Linus": 21}

Look up, add and change values by key:

    ages["Ada"]          # 36
    ages["Grace"] = 45   # add a new pair
    ages["Linus"] = 22   # change an existing value

Looking up a missing key stops the program with a KeyError. Check first with "Grace" in ages, or use ages.get("Grace", 0), which returns 0 when the key is missing.

Loop over the pairs with items():

    for name, age in ages.items():
        print(name, age)

Keys must be unique and unchangeable, so strings, numbers and tuples work as keys, but lists do not.

Quick check: what does {"a": 1}.get("b", 5) return? Answer: 5.$sd02_text$, NULL, NULL, NULL),
    -- course/python/chapter/5/lesson/1/material/1
    ('1cb0f92d-719b-59d2-81b7-32a3c8c2c675', '6e81f2f3-5f15-55b6-a67e-8bc741b0c3b3', 'Defining and Calling Functions', 'text', 'published', $sd02_text$A function is a named block of code that you can run whenever you need it. Define it with def:

    def greet():
        print("Hello!")
        print("Welcome to the course.")

Defining a function does not run it. Calling it does:

    greet()
    greet()

This displays the two lines twice. Functions let you write a task once, give it a clear name and reuse it, which keeps programs shorter and easier to change.

A function must be defined before the line that calls it runs. The indented lines after def form the function body, exactly like the body of an if statement or a loop.

Quick check: a program defines greet but never writes greet(). What does it display? Answer: nothing; the body only runs when the function is called.$sd02_text$, NULL, NULL, NULL),
    -- course/python/chapter/5/lesson/2/material/1
    ('806ee040-a473-5187-ade8-76e14d89e7e2', '8298297a-4a97-5acc-bc68-a21e2d912461', 'Passing Data In and Out of Functions', 'text', 'published', $sd02_text$Parameters let a caller pass values into a function, and return sends a result back:

    def area(width, height):
        return width * height

    room = area(3, 4)    # room refers to 12

The values in the call, 3 and 4, are called arguments; they are assigned to the parameters width and height in order. You can also name them: area(height=4, width=3).

return ends the function immediately. A function without a return statement returns the special value None, so

    def show(value):
        print(value)

    result = show(5)

displays 5 but makes result refer to None. Prefer returning values over printing them inside the function: the caller can then decide what to do with the result.

Quick check: what does area(area(1, 2), 5) return? Answer: 10.$sd02_text$, NULL, NULL, NULL),
    -- course/python/chapter/5/lesson/3/material/1
    ('8d425c05-8614-565f-9948-83c5a318c646', '26e6a91e-cc44-58e6-ab31-3f26d707c534', 'Where Names Are Visible', 'text', 'published', $sd02_text$The scope of a name is the part of the program where it can be used. A variable assigned inside a function is local: it exists only while that function runs.

    def make_total():
        total = 10
        return total

    make_total()
    print(total)    # NameError: total is local to make_total

A variable assigned at the top level of the program is global. A function can read a global variable, but assigning to the same name inside the function creates a separate local variable:

    count = 0
    def reset():
        count = 5    # a new local count; the global count is still 0

Passing values as parameters and returning results is clearer than relying on global variables, because each function then shows exactly which data it uses and produces.

Quick check: after calling reset() above, what does print(count) display? Answer: 0.$sd02_text$, NULL, NULL, NULL),
    -- course/python/chapter/6/lesson/1/material/1
    ('aa3f0a88-e0d3-57dc-bbb9-fbbb6aaed736', '66bf3619-0f99-505c-af0a-d622c71be14f', 'Transforming and Formatting Text', 'text', 'published', $sd02_text$String methods return new strings; the original is unchanged:

    text = "  Python Basics  "
    text.strip()            # "Python Basics"
    text.lower()            # "  python basics  "
    text.replace("B", "b")  # "  Python basics  "
    "a,b,c".split(",")      # ["a", "b", "c"]
    "-".join(["a", "b"])    # "a-b"

startswith, endswith and find test or locate parts of a string.

f-strings insert values into text. Put f before the opening quote and expressions inside braces:

    name = "Ada"
    score = 0.875
    print(f"{name} scored {score:.1%}")    # Ada scored 87.5%
    print(f"{3.14159:.2f}")                # 3.14

Formatting codes after the colon control decimal places, percentages and alignment.

Quick check: what is "Hello World".lower().split()? Answer: ["hello", "world"].$sd02_text$, NULL, NULL, NULL),
    -- course/python/chapter/6/lesson/2/material/1
    ('20f07406-31c1-5776-8be8-3ea3be1dd7db', 'd12de280-e91d-5536-b384-34575ed8cb20', 'Handling Errors with try and except', 'text', 'published', $sd02_text$When an operation fails at run time, Python raises an exception and, unless it is handled, the program stops. Common ones are ValueError (int("abc")), ZeroDivisionError (1 / 0), KeyError and IndexError.

A try statement runs code that might fail and handles the failure in an except block:

    try:
        number = int(input("Enter a number: "))
        print(100 / number)
    except ValueError:
        print("That was not a whole number.")
    except ZeroDivisionError:
        print("Zero is not allowed.")

Only the lines inside try are protected. Name the specific exception you expect, so that unexpected errors are still reported instead of being silently hidden. An optional finally block runs whether or not an exception occurred, which is useful for clean-up.

Quick check: if the user types 0, which message is displayed? Answer: "Zero is not allowed."$sd02_text$, NULL, NULL, NULL),
    -- course/python/chapter/6/lesson/3/material/1
    ('c22c4d8a-88fe-5e0a-a52e-c52bfce7e436', '7cdc242d-2dc4-51fa-abaa-852d131818bd', 'Reading and Writing Text Files', 'text', 'published', $sd02_text$open connects a program to a file. Use it in a with statement, which closes the file automatically:

    with open("notes.txt", "w") as file:
        file.write("First line\n")
        file.write("Second line\n")

The mode "w" creates the file or replaces its content, "a" appends to the end and "r" (the default) reads. write does not add line breaks, so include "\n" yourself.

Read a file line by line with a for loop:

    with open("notes.txt") as file:
        for line in file:
            print(line.strip())

strip removes the line break at the end of each line. Opening a file that does not exist for reading raises FileNotFoundError, which you can handle with try and except.

Quick check: a program opens an existing file with mode "w" and writes nothing. What does the file contain afterwards? Answer: nothing; mode "w" empties it.$sd02_text$, NULL, NULL, NULL);

DO $sd02$
DECLARE
    expected_course record;
    expected_chapter record;
    expected_lesson record;
    expected_material record;
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM public.users WHERE id = '1eacfb14-e494-596c-bb53-3d012e7caf72'
    ) THEN
        RAISE EXCEPTION 'SD02 requires the SD01 synthetic Instructor; load 01-users.sql first';
    END IF;
    IF NOT EXISTS (
        SELECT 1
        FROM public.users
        WHERE id = '1eacfb14-e494-596c-bb53-3d012e7caf72'
          AND email = 'instructor@example.test'
          AND display_name = 'Instructor Demo'
          AND role = 'instructor'
          AND account_status = 'active'
    ) THEN
        RAISE EXCEPTION 'SD02 Instructor reference does not match the approved SD01 identity';
    END IF;

    FOR expected_course IN SELECT * FROM sd02_course_seed ORDER BY id LOOP
        IF EXISTS (
            SELECT 1
            FROM public.courses existing_course
            WHERE existing_course.id = expected_course.id
              AND existing_course.instructor_id = expected_course.instructor_id
              AND existing_course.title = expected_course.title
              AND existing_course.description = expected_course.description
              AND existing_course.prerequisite_description IS NOT DISTINCT FROM expected_course.prerequisite_description
              AND existing_course.publication_status = expected_course.publication_status
              AND existing_course.adaptive_enabled = expected_course.adaptive_enabled
              AND existing_course.adaptive_settings IS NOT DISTINCT FROM expected_course.adaptive_settings
        ) THEN
            CONTINUE;
        END IF;

        IF EXISTS (SELECT 1 FROM public.courses WHERE id = expected_course.id) THEN
            RAISE EXCEPTION 'SD02 Course % conflicts with an existing Course', expected_course.id;
        END IF;

        INSERT INTO public.courses (id, instructor_id, title, description, prerequisite_description, publication_status, adaptive_enabled, adaptive_settings)
        VALUES (
            expected_course.id,
            expected_course.instructor_id,
            expected_course.title,
            expected_course.description,
            expected_course.prerequisite_description,
            expected_course.publication_status,
            expected_course.adaptive_enabled,
            expected_course.adaptive_settings
        );
    END LOOP;

    FOR expected_chapter IN SELECT * FROM sd02_chapter_seed ORDER BY id LOOP
        IF EXISTS (
            SELECT 1
            FROM public.chapters existing_chapter
            WHERE existing_chapter.id = expected_chapter.id
              AND existing_chapter.course_id = expected_chapter.course_id
              AND existing_chapter.title = expected_chapter.title
              AND existing_chapter.position = expected_chapter.position
        ) THEN
            CONTINUE;
        END IF;

        IF EXISTS (
            SELECT 1
            FROM public.chapters existing_chapter
            WHERE existing_chapter.id = expected_chapter.id
               OR (existing_chapter.course_id = expected_chapter.course_id
                   AND existing_chapter.position = expected_chapter.position)
        ) THEN
            RAISE EXCEPTION 'SD02 Chapter % conflicts with an existing Chapter ID or Course position', expected_chapter.id;
        END IF;

        INSERT INTO public.chapters (id, course_id, title, position)
        VALUES (expected_chapter.id, expected_chapter.course_id, expected_chapter.title, expected_chapter.position);
    END LOOP;

    FOR expected_lesson IN SELECT * FROM sd02_lesson_seed ORDER BY id LOOP
        IF EXISTS (
            SELECT 1
            FROM public.lessons existing_lesson
            WHERE existing_lesson.id = expected_lesson.id
              AND existing_lesson.chapter_id = expected_lesson.chapter_id
              AND existing_lesson.title = expected_lesson.title
              AND existing_lesson.content_markdown IS NOT DISTINCT FROM expected_lesson.content_markdown
              AND existing_lesson.position = expected_lesson.position
              AND existing_lesson.publication_status = expected_lesson.publication_status
        ) THEN
            CONTINUE;
        END IF;

        IF EXISTS (
            SELECT 1
            FROM public.lessons existing_lesson
            WHERE existing_lesson.id = expected_lesson.id
               OR (existing_lesson.chapter_id = expected_lesson.chapter_id
                   AND existing_lesson.position = expected_lesson.position)
        ) THEN
            RAISE EXCEPTION 'SD02 Lesson % conflicts with an existing Lesson ID or Chapter position', expected_lesson.id;
        END IF;

        INSERT INTO public.lessons (id, chapter_id, title, content_markdown, position, publication_status)
        VALUES (
            expected_lesson.id,
            expected_lesson.chapter_id,
            expected_lesson.title,
            expected_lesson.content_markdown,
            expected_lesson.position,
            expected_lesson.publication_status
        );
    END LOOP;

    FOR expected_material IN SELECT * FROM sd02_material_seed ORDER BY id LOOP
        IF EXISTS (
            SELECT 1
            FROM public.learning_materials existing_material
            WHERE existing_material.id = expected_material.id
              AND existing_material.lesson_id = expected_material.lesson_id
              AND existing_material.title = expected_material.title
              AND existing_material.material_type = expected_material.material_type
              AND existing_material.publication_status = expected_material.publication_status
              AND existing_material.text_content IS NOT DISTINCT FROM expected_material.text_content
              AND existing_material.object_key IS NOT DISTINCT FROM expected_material.object_key
              AND existing_material.original_filename IS NOT DISTINCT FROM expected_material.original_filename
              AND existing_material.external_video_url IS NOT DISTINCT FROM expected_material.external_video_url
        ) THEN
            CONTINUE;
        END IF;

        IF EXISTS (SELECT 1 FROM public.learning_materials WHERE id = expected_material.id) THEN
            RAISE EXCEPTION 'SD02 LearningMaterial % conflicts with an existing LearningMaterial', expected_material.id;
        END IF;

        INSERT INTO public.learning_materials (id, lesson_id, title, material_type, publication_status, text_content, object_key, original_filename, external_video_url)
        VALUES (
            expected_material.id,
            expected_material.lesson_id,
            expected_material.title,
            expected_material.material_type,
            expected_material.publication_status,
            expected_material.text_content,
            expected_material.object_key,
            expected_material.original_filename,
            expected_material.external_video_url
        );
    END LOOP;
END
$sd02$;

COMMIT;
