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

BEGIN;

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
