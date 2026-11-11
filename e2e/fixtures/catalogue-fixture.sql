-- Deterministic E2E catalogue fixture. Applied only by fixtures/local-stack.ts to the
-- disposable PostgreSQL container it creates, after Flyway has migrated the schema.
-- Never run this against a shared, demo or production database.
-- psql variable: instructor_id (UUID of the instructor account created through the API).

BEGIN;

-- Primary published course with a described prerequisite and a mixed syllabus.
INSERT INTO courses (id, instructor_id, title, description, prerequisite_description, publication_status, adaptive_enabled, adaptive_settings)
VALUES ('00000000-0000-4000-8000-000000000002', :'instructor_id', 'E2E Programming Foundations',
        'Variables, decisions and loops for first-year students, one short lesson at a time.',
        'Comfortable with basic arithmetic.', 'published', false, NULL);
INSERT INTO chapters (id, course_id, title, position) VALUES
    ('00000000-0000-4000-8000-000000000003', '00000000-0000-4000-8000-000000000002', 'Getting Started', 1),
    ('00000000-0000-4000-8000-000000000006', '00000000-0000-4000-8000-000000000002', 'Making Decisions', 2),
    ('00000000-0000-4000-8000-000000000008', '00000000-0000-4000-8000-000000000002', 'Unreleased Chapter', 3);
INSERT INTO lessons (id, chapter_id, title, content_markdown, position, publication_status) VALUES
    ('00000000-0000-4000-8000-000000000004', '00000000-0000-4000-8000-000000000003', 'What a Program Is', NULL, 1, 'published'),
    ('00000000-0000-4000-8000-000000000005', '00000000-0000-4000-8000-000000000003', 'Draft Lesson Hidden From Learners', NULL, 2, 'hidden'),
    ('00000000-0000-4000-8000-000000000007', '00000000-0000-4000-8000-000000000006', 'If Statements', NULL, 1, 'published'),
    ('00000000-0000-4000-8000-000000000009', '00000000-0000-4000-8000-000000000008', 'Hidden Chapter Lesson', NULL, 1, 'hidden');

-- Hidden course: never listed, uniform 404 on direct access.
INSERT INTO courses (id, instructor_id, title, description, prerequisite_description, publication_status, adaptive_enabled, adaptive_settings)
VALUES ('00000000-0000-4000-8000-000000000099', :'instructor_id', 'E2E Hidden Course',
        'This course is hidden and must never appear in the catalogue.', NULL, 'hidden', false, NULL);

-- Twenty more published courses (21 published in total: two catalogue pages of 20).
-- Course 01 has a 100-character unbroken title; Course 02 a 500-character unbroken description.
INSERT INTO courses (id, instructor_id, title, description, prerequisite_description, publication_status, adaptive_enabled, adaptive_settings)
SELECT ('00000000-0000-4000-8001-' || lpad(n::text, 12, '0'))::uuid,
       :'instructor_id',
       CASE WHEN n = 1 THEN repeat('W', 100) ELSE 'Course ' || lpad(n::text, 2, '0') END,
       CASE WHEN n = 2 THEN repeat('D', 500) ELSE 'Practice course number ' || n || ' for catalogue paging.' END,
       NULL, 'published', false, NULL
FROM generate_series(1, 20) AS n;

COMMIT;
