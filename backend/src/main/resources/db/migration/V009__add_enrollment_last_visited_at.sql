-- CO08 / CT22-RESUME-SCHEMA-2026-11-09-r1 (ADR006 7.2): server time of the latest
-- explicit Lesson visit, written atomically with resume_lesson_id. Additive only:
-- no default and no backfill; a legacy NULL means unknown recency, never a visit.
-- Runtime privileges are provisioned by the CT17 matrix, never by migrations.

ALTER TABLE public.enrollments
    ADD COLUMN last_visited_at timestamptz NULL;
