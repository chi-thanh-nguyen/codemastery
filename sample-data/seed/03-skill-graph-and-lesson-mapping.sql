-- CodeMastery final-v1 skill graph and Lesson mapping seed (SD03, ADR013 §4.2 CT15.F-adaptive).
--
-- Local/test-only: never run against a shared, staging or production database.
-- The target must be a disposable database attested as described in
-- sample-data/README.md; otherwise this script stops before any lock or write.
-- Load 02-course-python-fundamentals.sql first: every row belongs to course/python.
--
-- Input, as a psql variable or, when unset, an environment variable:
--   seed_target_system_identifier   CODEMASTERY_SEED_TARGET_SYSTEM_IDENTIFIER
--
-- IDs are UUIDv5 values in the standard URL namespace for the name
-- https://codemastery.test/final-v1/<logical-key>; each row names its key.
-- Twenty skills S01-S20 and twenty-four direct-prerequisite edges, each pointing to
-- an earlier skill, so the graph is acyclic by construction (ADR005 CT05). Lesson Lk
-- maps exactly skill Sk. No mastery, attempt, progress or recommendation row is seeded.

\set ON_ERROR_STOP on
\set VERBOSITY terse

\if :{?seed_target_system_identifier}
\else
\getenv seed_target_system_identifier CODEMASTERY_SEED_TARGET_SYSTEM_IDENTIFIER
\endif

\if :{?seed_target_system_identifier}
\else
DO $sd03_input$
BEGIN
    RAISE EXCEPTION 'Required input seed_target_system_identifier is missing.';
END;
$sd03_input$;
\endif

-- READ COMMITTED regardless of the database default, for the same reason as SD02:
-- a loader waiting on the lock must see rows another loader has committed.
BEGIN ISOLATION LEVEL READ COMMITTED;

-- Verify the target before taking any lock: the connected cluster must have the
-- independently obtained system identifier, and this database must carry the
-- catalog-level disposable-target attestation bound to that identifier.
SET LOCAL codemastery.seed_expected_target = :'seed_target_system_identifier';

DO $sd03_target$
DECLARE
    expected_identifier text := current_setting('codemastery.seed_expected_target');
    actual_identifier text;
    attestation text;
BEGIN
    IF expected_identifier !~ '^[1-9][0-9]{0,19}$' THEN
        RAISE EXCEPTION 'SD03 target verification failed: malformed expected system identifier';
    END IF;

    BEGIN
        SELECT system_identifier::text INTO actual_identifier FROM pg_control_system();
    EXCEPTION WHEN OTHERS THEN
        actual_identifier := NULL;
    END;
    IF actual_identifier IS NULL THEN
        RAISE EXCEPTION 'SD03 target verification failed: cluster identity is unavailable';
    END IF;
    IF actual_identifier <> expected_identifier THEN
        RAISE EXCEPTION 'SD03 target verification failed: connected cluster is not the expected target';
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
        RAISE EXCEPTION 'SD03 target verification failed: database is not attested as a disposable seed target';
    END IF;
END
$sd03_target$;

-- Keep course/python's structure stable, then serialize loaders and other graph
-- writers before taking a data snapshot. Lock order follows SD02 (courses first).
LOCK TABLE public.courses, public.chapters, public.lessons IN SHARE MODE;
LOCK TABLE public.skills, public.skill_prerequisites, public.lesson_skills IN SHARE ROW EXCLUSIVE MODE;

CREATE TEMP TABLE sd03_skill_seed (
    id uuid NOT NULL,
    course_id uuid NOT NULL,
    name text NOT NULL,
    description text NULL
) ON COMMIT DROP;

INSERT INTO sd03_skill_seed (id, course_id, name, description)
VALUES
    -- course/python/skill/S01
    ('823af82e-e876-5225-92f7-7d71587744b1', '68ea4540-e9da-5605-9054-28b29afa0992', 'Program Output', NULL),
    -- course/python/skill/S02
    ('625bb9f0-01f3-55fc-861b-162fbfc450dd', '68ea4540-e9da-5605-9054-28b29afa0992', 'Variables and Assignment', NULL),
    -- course/python/skill/S03
    ('2ce5c887-3d69-507c-b26d-6e255d807beb', '68ea4540-e9da-5605-9054-28b29afa0992', 'Numbers and Arithmetic', NULL),
    -- course/python/skill/S04
    ('8de90879-28ff-5d76-af55-117ffac52d1d', '68ea4540-e9da-5605-9054-28b29afa0992', 'Strings', NULL),
    -- course/python/skill/S05
    ('f0f3a655-bbd0-53b1-acb7-9cf82d81735a', '68ea4540-e9da-5605-9054-28b29afa0992', 'Input and Type Conversion', NULL),
    -- course/python/skill/S06
    ('528661dc-7ffc-5b1a-bb5b-9adc8e3cb7ff', '68ea4540-e9da-5605-9054-28b29afa0992', 'Booleans and Comparisons', NULL),
    -- course/python/skill/S07
    ('0ec304d6-e92a-532e-b5dd-ce660ab5a825', '68ea4540-e9da-5605-9054-28b29afa0992', 'Conditionals', NULL),
    -- course/python/skill/S08
    ('ce75d29b-3c4f-5951-9180-b9fdc08f289f', '68ea4540-e9da-5605-9054-28b29afa0992', 'While Loops', NULL),
    -- course/python/skill/S09
    ('1761144d-4d90-5570-b8b5-adcc3f9f413b', '68ea4540-e9da-5605-9054-28b29afa0992', 'For Loops', NULL),
    -- course/python/skill/S10
    ('7cabeda5-0c6c-5c73-8a67-967fd84bce45', '68ea4540-e9da-5605-9054-28b29afa0992', 'Loop Control', NULL),
    -- course/python/skill/S11
    ('a9d43b35-426f-5500-9122-1043ddfa7977', '68ea4540-e9da-5605-9054-28b29afa0992', 'Lists', NULL),
    -- course/python/skill/S12
    ('f26a0234-bcec-5ba6-abc3-66ecbefd415f', '68ea4540-e9da-5605-9054-28b29afa0992', 'List Iteration Patterns', NULL),
    -- course/python/skill/S13
    ('18869e1c-168a-585d-a1b1-9c97c12383d1', '68ea4540-e9da-5605-9054-28b29afa0992', 'Tuples and Sets', NULL),
    -- course/python/skill/S14
    ('37c01bbf-2268-5b7a-adae-6d4bebcdc504', '68ea4540-e9da-5605-9054-28b29afa0992', 'Dictionaries', NULL),
    -- course/python/skill/S15
    ('4604bdb9-d7ac-5877-9fa0-5610ab6c64eb', '68ea4540-e9da-5605-9054-28b29afa0992', 'Functions', NULL),
    -- course/python/skill/S16
    ('da6797cf-4a5c-5e29-ad28-0a68292ed9c3', '68ea4540-e9da-5605-9054-28b29afa0992', 'Parameters and Return Values', NULL),
    -- course/python/skill/S17
    ('807cc7ab-d993-5633-b93c-cc2896724d1f', '68ea4540-e9da-5605-9054-28b29afa0992', 'Variable Scope', NULL),
    -- course/python/skill/S18
    ('23dec971-6865-59ab-b55b-24ccd3bda430', '68ea4540-e9da-5605-9054-28b29afa0992', 'String Methods and Formatting', NULL),
    -- course/python/skill/S19
    ('41a0f9ce-a2e0-5243-bdb4-a8cd5088e1f7', '68ea4540-e9da-5605-9054-28b29afa0992', 'Exceptions', NULL),
    -- course/python/skill/S20
    ('09b1a1bd-bef5-594c-afdf-ca43f0a01ab8', '68ea4540-e9da-5605-9054-28b29afa0992', 'File Input and Output', NULL);

CREATE TEMP TABLE sd03_prerequisite_seed (
    id uuid NOT NULL,
    course_id uuid NOT NULL,
    skill_id uuid NOT NULL,
    prerequisite_skill_id uuid NOT NULL
) ON COMMIT DROP;

-- Edge skill -> prerequisite: the prerequisite is a direct prerequisite of the skill.
INSERT INTO sd03_prerequisite_seed (id, course_id, skill_id, prerequisite_skill_id)
VALUES
    -- course/python/skill/S02/prerequisite/S01
    ('58e74c9c-3ffc-555c-b419-2337503e3f77', '68ea4540-e9da-5605-9054-28b29afa0992', '625bb9f0-01f3-55fc-861b-162fbfc450dd', '823af82e-e876-5225-92f7-7d71587744b1'),
    -- course/python/skill/S03/prerequisite/S02
    ('1200170b-40f3-5729-88eb-f3f0813ab0b5', '68ea4540-e9da-5605-9054-28b29afa0992', '2ce5c887-3d69-507c-b26d-6e255d807beb', '625bb9f0-01f3-55fc-861b-162fbfc450dd'),
    -- course/python/skill/S04/prerequisite/S02
    ('f180c56c-380c-553f-a192-fbdce4a04525', '68ea4540-e9da-5605-9054-28b29afa0992', '8de90879-28ff-5d76-af55-117ffac52d1d', '625bb9f0-01f3-55fc-861b-162fbfc450dd'),
    -- course/python/skill/S05/prerequisite/S03
    ('42a3b370-cc21-56ab-90de-1732d2e41ee9', '68ea4540-e9da-5605-9054-28b29afa0992', 'f0f3a655-bbd0-53b1-acb7-9cf82d81735a', '2ce5c887-3d69-507c-b26d-6e255d807beb'),
    -- course/python/skill/S05/prerequisite/S04
    ('d0f330fc-b64e-53b7-b231-0bdf7949c918', '68ea4540-e9da-5605-9054-28b29afa0992', 'f0f3a655-bbd0-53b1-acb7-9cf82d81735a', '8de90879-28ff-5d76-af55-117ffac52d1d'),
    -- course/python/skill/S06/prerequisite/S03
    ('4b92cc2a-cb4f-5853-86d7-0dcce054ca13', '68ea4540-e9da-5605-9054-28b29afa0992', '528661dc-7ffc-5b1a-bb5b-9adc8e3cb7ff', '2ce5c887-3d69-507c-b26d-6e255d807beb'),
    -- course/python/skill/S07/prerequisite/S06
    ('395f5128-2781-5f7d-867d-b7c19bef2b29', '68ea4540-e9da-5605-9054-28b29afa0992', '0ec304d6-e92a-532e-b5dd-ce660ab5a825', '528661dc-7ffc-5b1a-bb5b-9adc8e3cb7ff'),
    -- course/python/skill/S08/prerequisite/S07
    ('f063ddfa-0f7b-5b3a-b3ca-2b4a6c18caba', '68ea4540-e9da-5605-9054-28b29afa0992', 'ce75d29b-3c4f-5951-9180-b9fdc08f289f', '0ec304d6-e92a-532e-b5dd-ce660ab5a825'),
    -- course/python/skill/S09/prerequisite/S07
    ('494b01a4-3e35-58d6-8798-c63438839294', '68ea4540-e9da-5605-9054-28b29afa0992', '1761144d-4d90-5570-b8b5-adcc3f9f413b', '0ec304d6-e92a-532e-b5dd-ce660ab5a825'),
    -- course/python/skill/S10/prerequisite/S08
    ('fa3fc32e-4141-5b17-aa01-d4337998368c', '68ea4540-e9da-5605-9054-28b29afa0992', '7cabeda5-0c6c-5c73-8a67-967fd84bce45', 'ce75d29b-3c4f-5951-9180-b9fdc08f289f'),
    -- course/python/skill/S10/prerequisite/S09
    ('44b05024-5466-5e6d-b75c-96651c8e541c', '68ea4540-e9da-5605-9054-28b29afa0992', '7cabeda5-0c6c-5c73-8a67-967fd84bce45', '1761144d-4d90-5570-b8b5-adcc3f9f413b'),
    -- course/python/skill/S11/prerequisite/S09
    ('7fe5ee64-d344-5530-98e5-84fc97619d5d', '68ea4540-e9da-5605-9054-28b29afa0992', 'a9d43b35-426f-5500-9122-1043ddfa7977', '1761144d-4d90-5570-b8b5-adcc3f9f413b'),
    -- course/python/skill/S12/prerequisite/S09
    ('bb9cb264-9568-503e-9332-570976c7ceea', '68ea4540-e9da-5605-9054-28b29afa0992', 'f26a0234-bcec-5ba6-abc3-66ecbefd415f', '1761144d-4d90-5570-b8b5-adcc3f9f413b'),
    -- course/python/skill/S12/prerequisite/S11
    ('7781221a-7a4b-5e5e-831d-7c1b726b0698', '68ea4540-e9da-5605-9054-28b29afa0992', 'f26a0234-bcec-5ba6-abc3-66ecbefd415f', 'a9d43b35-426f-5500-9122-1043ddfa7977'),
    -- course/python/skill/S13/prerequisite/S11
    ('f31f02e8-7cb3-5f66-ba02-cc0daeca8095', '68ea4540-e9da-5605-9054-28b29afa0992', '18869e1c-168a-585d-a1b1-9c97c12383d1', 'a9d43b35-426f-5500-9122-1043ddfa7977'),
    -- course/python/skill/S14/prerequisite/S11
    ('25c17c87-ad13-5f90-8e7b-d2f29b890d5a', '68ea4540-e9da-5605-9054-28b29afa0992', '37c01bbf-2268-5b7a-adae-6d4bebcdc504', 'a9d43b35-426f-5500-9122-1043ddfa7977'),
    -- course/python/skill/S15/prerequisite/S07
    ('28b2a0f8-7ce0-59d5-a933-c4b16d363661', '68ea4540-e9da-5605-9054-28b29afa0992', '4604bdb9-d7ac-5877-9fa0-5610ab6c64eb', '0ec304d6-e92a-532e-b5dd-ce660ab5a825'),
    -- course/python/skill/S16/prerequisite/S15
    ('5eab75c7-88b6-55a1-a5ef-8fb6802bded9', '68ea4540-e9da-5605-9054-28b29afa0992', 'da6797cf-4a5c-5e29-ad28-0a68292ed9c3', '4604bdb9-d7ac-5877-9fa0-5610ab6c64eb'),
    -- course/python/skill/S17/prerequisite/S16
    ('9f3875a8-a93b-51f3-9e3a-6a6ca064608b', '68ea4540-e9da-5605-9054-28b29afa0992', '807cc7ab-d993-5633-b93c-cc2896724d1f', 'da6797cf-4a5c-5e29-ad28-0a68292ed9c3'),
    -- course/python/skill/S18/prerequisite/S04
    ('585197d5-39b1-5b6e-ab58-0d55e4ea61c3', '68ea4540-e9da-5605-9054-28b29afa0992', '23dec971-6865-59ab-b55b-24ccd3bda430', '8de90879-28ff-5d76-af55-117ffac52d1d'),
    -- course/python/skill/S18/prerequisite/S09
    ('0c64e916-e994-594f-8617-75dac28f06dd', '68ea4540-e9da-5605-9054-28b29afa0992', '23dec971-6865-59ab-b55b-24ccd3bda430', '1761144d-4d90-5570-b8b5-adcc3f9f413b'),
    -- course/python/skill/S19/prerequisite/S15
    ('868fe66f-6386-55eb-8218-891185444638', '68ea4540-e9da-5605-9054-28b29afa0992', '41a0f9ce-a2e0-5243-bdb4-a8cd5088e1f7', '4604bdb9-d7ac-5877-9fa0-5610ab6c64eb'),
    -- course/python/skill/S20/prerequisite/S09
    ('cddec3a8-c48d-5e51-a5f5-9ba48e60f829', '68ea4540-e9da-5605-9054-28b29afa0992', '09b1a1bd-bef5-594c-afdf-ca43f0a01ab8', '1761144d-4d90-5570-b8b5-adcc3f9f413b'),
    -- course/python/skill/S20/prerequisite/S19
    ('6ce6713d-9caa-5dc7-b9d5-2062ac46daaf', '68ea4540-e9da-5605-9054-28b29afa0992', '09b1a1bd-bef5-594c-afdf-ca43f0a01ab8', '41a0f9ce-a2e0-5243-bdb4-a8cd5088e1f7');

CREATE TEMP TABLE sd03_lesson_skill_seed (
    id uuid NOT NULL,
    lesson_id uuid NOT NULL,
    skill_id uuid NOT NULL
) ON COMMIT DROP;

INSERT INTO sd03_lesson_skill_seed (id, lesson_id, skill_id)
VALUES
    -- course/python/chapter/1/lesson/1/skill/S01
    ('078571ea-1191-5009-b895-57dd64907b33', '28a0dcbc-cc05-59f7-bcf2-7ab591581299', '823af82e-e876-5225-92f7-7d71587744b1'),
    -- course/python/chapter/1/lesson/2/skill/S02
    ('20c70898-a53e-57d8-9030-dad9fb334dfe', '405046b8-4f5b-5ffc-bbd0-bf6b6f705844', '625bb9f0-01f3-55fc-861b-162fbfc450dd'),
    -- course/python/chapter/1/lesson/3/skill/S03
    ('38c46104-41ee-5080-bf0e-dec25f2cbc7d', '97d15c80-c325-5074-bc9f-ac96d82a1aff', '2ce5c887-3d69-507c-b26d-6e255d807beb'),
    -- course/python/chapter/1/lesson/4/skill/S04
    ('526ccbcc-17bb-52bd-960d-c4073dcdfa76', 'fe1aaecf-d49a-5da1-81df-33924f779b1e', '8de90879-28ff-5d76-af55-117ffac52d1d'),
    -- course/python/chapter/1/lesson/5/skill/S05
    ('08bcbc9b-4a68-56e3-bc24-50081c4a1e5d', 'c861e5e7-7cb2-5db9-b60b-650a9ef3d3af', 'f0f3a655-bbd0-53b1-acb7-9cf82d81735a'),
    -- course/python/chapter/2/lesson/1/skill/S06
    ('2bf95769-4f7b-5cbd-a572-5e89d8929594', '19c599c1-75b9-532b-88f9-edfbfeb1b2e1', '528661dc-7ffc-5b1a-bb5b-9adc8e3cb7ff'),
    -- course/python/chapter/2/lesson/2/skill/S07
    ('19c9b211-8fe2-585f-9f56-e62b4a722a81', '7359f85d-917a-524a-9dfa-4bafce55fb3b', '0ec304d6-e92a-532e-b5dd-ce660ab5a825'),
    -- course/python/chapter/3/lesson/1/skill/S08
    ('cb3611ea-84ed-5f86-b8cb-ded88e70ec4d', '16c0ed9e-7d97-5d21-b1d5-6d2d517b0605', 'ce75d29b-3c4f-5951-9180-b9fdc08f289f'),
    -- course/python/chapter/3/lesson/2/skill/S09
    ('4ec71b71-b2d5-55c3-96ce-0babc1348f12', '75e8e26f-9800-5b2a-ae80-8578e4a2b4bc', '1761144d-4d90-5570-b8b5-adcc3f9f413b'),
    -- course/python/chapter/3/lesson/3/skill/S10
    ('3a985fbb-6d15-55de-9696-5d7c4d72e54d', '3b413f38-5f6d-50c2-a28a-6ebbc9f70a12', '7cabeda5-0c6c-5c73-8a67-967fd84bce45'),
    -- course/python/chapter/4/lesson/1/skill/S11
    ('420baaa3-ef23-5cb9-a700-8d704038aa0b', '554f05ef-3e4d-5b06-9fc3-21bb48841877', 'a9d43b35-426f-5500-9122-1043ddfa7977'),
    -- course/python/chapter/4/lesson/2/skill/S12
    ('563c831e-b352-5961-869d-f091704753a5', 'aa4beb45-b8f6-573d-b3c2-c274eeea7d53', 'f26a0234-bcec-5ba6-abc3-66ecbefd415f'),
    -- course/python/chapter/4/lesson/3/skill/S13
    ('145b175a-4bfd-5c46-9c50-9e1a7892c053', 'cd209ec7-e47a-5db1-8bfe-fcba36f657ac', '18869e1c-168a-585d-a1b1-9c97c12383d1'),
    -- course/python/chapter/4/lesson/4/skill/S14
    ('41f6263f-ca79-58cc-b31b-e2552f6f152a', 'dcf76c4d-40d3-50c1-904e-0a3ba39530dc', '37c01bbf-2268-5b7a-adae-6d4bebcdc504'),
    -- course/python/chapter/5/lesson/1/skill/S15
    ('708e6725-cc17-5a0d-bd92-7b19312e2fed', '6e81f2f3-5f15-55b6-a67e-8bc741b0c3b3', '4604bdb9-d7ac-5877-9fa0-5610ab6c64eb'),
    -- course/python/chapter/5/lesson/2/skill/S16
    ('aed258b2-18e6-531d-9d12-3f6c76da9601', '8298297a-4a97-5acc-bc68-a21e2d912461', 'da6797cf-4a5c-5e29-ad28-0a68292ed9c3'),
    -- course/python/chapter/5/lesson/3/skill/S17
    ('8250037b-7a46-57db-a743-5e521c430703', '26e6a91e-cc44-58e6-ab31-3f26d707c534', '807cc7ab-d993-5633-b93c-cc2896724d1f'),
    -- course/python/chapter/6/lesson/1/skill/S18
    ('29638572-be01-5080-9794-2d3ba709e7df', '66bf3619-0f99-505c-af0a-d622c71be14f', '23dec971-6865-59ab-b55b-24ccd3bda430'),
    -- course/python/chapter/6/lesson/2/skill/S19
    ('e6bb84e3-8fac-5328-a962-70d59b64b78f', 'd12de280-e91d-5536-b384-34575ed8cb20', '41a0f9ce-a2e0-5243-bdb4-a8cd5088e1f7'),
    -- course/python/chapter/6/lesson/3/skill/S20
    ('70906a9b-6520-5cbf-aca3-1d0d482da108', '7cdc242d-2dc4-51fa-abaa-852d131818bd', '09b1a1bd-bef5-594c-afdf-ca43f0a01ab8');

DO $sd03$
DECLARE
    python_course constant uuid := '68ea4540-e9da-5605-9054-28b29afa0992';
    expected_skill record;
    expected_edge record;
    expected_mapping record;
BEGIN
    IF (SELECT count(*)
        FROM sd03_lesson_skill_seed seed
        JOIN public.lessons lesson ON lesson.id = seed.lesson_id AND lesson.publication_status = 'published'
        JOIN public.chapters chapter ON chapter.id = lesson.chapter_id AND chapter.course_id = python_course) <> 20 THEN
        RAISE EXCEPTION 'SD03 requires the SD02 course/python Lessons; load 02-course-python-fundamentals.sql first';
    END IF;

    FOR expected_skill IN SELECT * FROM sd03_skill_seed ORDER BY id LOOP
        IF EXISTS (
            SELECT 1
            FROM public.skills existing_skill
            WHERE existing_skill.id = expected_skill.id
              AND existing_skill.course_id = expected_skill.course_id
              AND existing_skill.name = expected_skill.name
              AND existing_skill.description IS NOT DISTINCT FROM expected_skill.description
        ) THEN
            CONTINUE;
        END IF;

        IF EXISTS (
            SELECT 1
            FROM public.skills existing_skill
            WHERE existing_skill.id = expected_skill.id
               OR (existing_skill.course_id = expected_skill.course_id
                   AND existing_skill.name = expected_skill.name)
        ) THEN
            RAISE EXCEPTION 'SD03 Skill % conflicts with an existing Skill ID or Course skill name', expected_skill.id;
        END IF;

        INSERT INTO public.skills (id, course_id, name, description)
        VALUES (expected_skill.id, expected_skill.course_id, expected_skill.name, expected_skill.description);
    END LOOP;

    FOR expected_edge IN SELECT * FROM sd03_prerequisite_seed ORDER BY id LOOP
        IF EXISTS (
            SELECT 1
            FROM public.skill_prerequisites existing_edge
            WHERE existing_edge.id = expected_edge.id
              AND existing_edge.course_id = expected_edge.course_id
              AND existing_edge.skill_id = expected_edge.skill_id
              AND existing_edge.prerequisite_skill_id = expected_edge.prerequisite_skill_id
        ) THEN
            CONTINUE;
        END IF;

        IF EXISTS (
            SELECT 1
            FROM public.skill_prerequisites existing_edge
            WHERE existing_edge.id = expected_edge.id
               OR (existing_edge.course_id = expected_edge.course_id
                   AND existing_edge.skill_id = expected_edge.skill_id
                   AND existing_edge.prerequisite_skill_id = expected_edge.prerequisite_skill_id)
        ) THEN
            RAISE EXCEPTION 'SD03 prerequisite % conflicts with an existing prerequisite ID or edge', expected_edge.id;
        END IF;

        INSERT INTO public.skill_prerequisites (id, course_id, skill_id, prerequisite_skill_id)
        VALUES (expected_edge.id, expected_edge.course_id, expected_edge.skill_id, expected_edge.prerequisite_skill_id);
    END LOOP;

    FOR expected_mapping IN SELECT * FROM sd03_lesson_skill_seed ORDER BY id LOOP
        IF EXISTS (
            SELECT 1
            FROM public.lesson_skills existing_mapping
            WHERE existing_mapping.id = expected_mapping.id
              AND existing_mapping.lesson_id = expected_mapping.lesson_id
              AND existing_mapping.skill_id = expected_mapping.skill_id
        ) THEN
            CONTINUE;
        END IF;

        IF EXISTS (
            SELECT 1
            FROM public.lesson_skills existing_mapping
            WHERE existing_mapping.id = expected_mapping.id
               OR (existing_mapping.lesson_id = expected_mapping.lesson_id
                   AND existing_mapping.skill_id = expected_mapping.skill_id)
        ) THEN
            RAISE EXCEPTION 'SD03 Lesson mapping % conflicts with an existing mapping ID or pair', expected_mapping.id;
        END IF;

        INSERT INTO public.lesson_skills (id, lesson_id, skill_id)
        VALUES (expected_mapping.id, expected_mapping.lesson_id, expected_mapping.skill_id);
    END LOOP;

    -- The approved graph is the whole graph of course/python: any other skill, edge or
    -- mapping of the Course, its Lessons or its skills would change routing.
    IF EXISTS (SELECT 1 FROM public.skills WHERE course_id = python_course
                   AND id NOT IN (SELECT id FROM sd03_skill_seed))
       OR EXISTS (SELECT 1 FROM public.skill_prerequisites WHERE course_id = python_course
                   AND id NOT IN (SELECT id FROM sd03_prerequisite_seed))
       OR EXISTS (SELECT 1
                  FROM public.lesson_skills mapping
                  LEFT JOIN public.lessons lesson ON lesson.id = mapping.lesson_id
                  LEFT JOIN public.chapters chapter ON chapter.id = lesson.chapter_id
                  WHERE (chapter.course_id = python_course OR mapping.skill_id IN (SELECT id FROM sd03_skill_seed))
                    AND mapping.id NOT IN (SELECT id FROM sd03_lesson_skill_seed)) THEN
        RAISE EXCEPTION 'SD03 course/python already has skills, prerequisites or mappings outside the approved graph';
    END IF;

    IF EXISTS (
        WITH RECURSIVE reach (origin, skill_id) AS (
            SELECT skill_id, prerequisite_skill_id FROM public.skill_prerequisites WHERE course_id = python_course
            UNION
            SELECT reach.origin, edge.prerequisite_skill_id
            FROM reach
            JOIN public.skill_prerequisites edge ON edge.course_id = python_course AND edge.skill_id = reach.skill_id
        )
        SELECT 1 FROM reach WHERE origin = skill_id
    ) THEN
        RAISE EXCEPTION 'SD03 course/python skill graph is not acyclic';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM public.lessons lesson
        JOIN public.chapters chapter ON chapter.id = lesson.chapter_id AND chapter.course_id = python_course
        WHERE lesson.publication_status = 'published'
          AND NOT EXISTS (SELECT 1 FROM public.lesson_skills mapping WHERE mapping.lesson_id = lesson.id)
    ) THEN
        RAISE EXCEPTION 'SD03 leaves a published course/python Lesson unmapped';
    END IF;
END
$sd03$;

COMMIT;
