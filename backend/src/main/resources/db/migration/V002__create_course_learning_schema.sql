-- Initial physical schema: V002. Applied migrations are immutable.
-- Application supplies UUIDs, timestamps, and lifecycle values; no seed data.

CREATE TABLE public.courses (
    id uuid NOT NULL,
    instructor_id uuid NOT NULL,
    title text NOT NULL,
    description text NOT NULL,
    prerequisite_description text NULL,
    publication_status text NOT NULL,
    adaptive_enabled boolean NOT NULL,
    adaptive_settings jsonb NULL,
    CONSTRAINT pk_courses PRIMARY KEY (id),
    CONSTRAINT fk_courses_instructor_id FOREIGN KEY (instructor_id) REFERENCES public.users (id) ON DELETE NO ACTION ON UPDATE NO ACTION NOT DEFERRABLE,
    CONSTRAINT ck_courses_text CHECK (title ~ '[^[:space:]]' AND description ~ '[^[:space:]]' AND (prerequisite_description IS NULL OR prerequisite_description ~ '[^[:space:]]')),
    CONSTRAINT ck_courses_publication_status CHECK (publication_status IN ('hidden', 'published')),
    CONSTRAINT ck_courses_adaptive_configuration CHECK ((adaptive_settings IS NULL OR jsonb_typeof(adaptive_settings) = 'object') AND (NOT adaptive_enabled OR adaptive_settings IS NOT NULL))
);

CREATE INDEX ix_courses_instructor_id ON public.courses (instructor_id);

CREATE TABLE public.chapters (
    id uuid NOT NULL,
    course_id uuid NOT NULL,
    title text NOT NULL,
    position integer NOT NULL,
    CONSTRAINT pk_chapters PRIMARY KEY (id),
    CONSTRAINT fk_chapters_course_id FOREIGN KEY (course_id) REFERENCES public.courses (id) ON DELETE NO ACTION ON UPDATE NO ACTION NOT DEFERRABLE,
    CONSTRAINT uq_chapters_course_position UNIQUE (course_id, position) DEFERRABLE INITIALLY IMMEDIATE,
    CONSTRAINT ck_chapters_text CHECK (title ~ '[^[:space:]]'),
    CONSTRAINT ck_chapters_position CHECK (position >= 1)
);

CREATE TABLE public.lessons (
    id uuid NOT NULL,
    chapter_id uuid NOT NULL,
    title text NOT NULL,
    content_markdown text NULL,
    position integer NOT NULL,
    publication_status text NOT NULL,
    CONSTRAINT pk_lessons PRIMARY KEY (id),
    CONSTRAINT fk_lessons_chapter_id FOREIGN KEY (chapter_id) REFERENCES public.chapters (id) ON DELETE NO ACTION ON UPDATE NO ACTION NOT DEFERRABLE,
    CONSTRAINT uq_lessons_chapter_position UNIQUE (chapter_id, position) DEFERRABLE INITIALLY IMMEDIATE,
    CONSTRAINT ck_lessons_text CHECK (title ~ '[^[:space:]]' AND (content_markdown IS NULL OR content_markdown ~ '[^[:space:]]')),
    CONSTRAINT ck_lessons_position CHECK (position >= 1),
    CONSTRAINT ck_lessons_publication_status CHECK (publication_status IN ('hidden', 'published'))
);

CREATE TABLE public.learning_materials (
    id uuid NOT NULL,
    lesson_id uuid NOT NULL,
    title text NOT NULL,
    material_type text NOT NULL,
    publication_status text NOT NULL,
    text_content text NULL,
    object_key text NULL,
    original_filename text NULL,
    external_video_url text NULL,
    CONSTRAINT pk_learning_materials PRIMARY KEY (id),
    CONSTRAINT fk_learning_materials_lesson_id FOREIGN KEY (lesson_id) REFERENCES public.lessons (id) ON DELETE NO ACTION ON UPDATE NO ACTION NOT DEFERRABLE,
    CONSTRAINT ck_learning_materials_text CHECK (title ~ '[^[:space:]]' AND (text_content IS NULL OR text_content ~ '[^[:space:]]') AND (object_key IS NULL OR object_key ~ '[^[:space:]]') AND (original_filename IS NULL OR original_filename ~ '[^[:space:]]') AND (external_video_url IS NULL OR external_video_url ~ '[^[:space:]]')),
    CONSTRAINT ck_learning_materials_type CHECK (material_type IN ('text', 'file', 'external_video')),
    CONSTRAINT ck_learning_materials_publication_status CHECK (publication_status IN ('hidden', 'published')),
    CONSTRAINT ck_learning_materials_payload CHECK ((material_type = 'text' AND text_content IS NOT NULL AND object_key IS NULL AND original_filename IS NULL AND external_video_url IS NULL) OR (material_type = 'file' AND text_content IS NULL AND object_key IS NOT NULL AND original_filename IS NOT NULL AND external_video_url IS NULL) OR (material_type = 'external_video' AND text_content IS NULL AND object_key IS NULL AND original_filename IS NULL AND external_video_url IS NOT NULL))
);

CREATE INDEX ix_learning_materials_lesson_id ON public.learning_materials (lesson_id);

CREATE TABLE public.enrollments (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    course_id uuid NOT NULL,
    status text NOT NULL,
    resume_lesson_id uuid NULL,
    CONSTRAINT pk_enrollments PRIMARY KEY (id),
    CONSTRAINT fk_enrollments_user_id FOREIGN KEY (user_id) REFERENCES public.users (id) ON DELETE NO ACTION ON UPDATE NO ACTION NOT DEFERRABLE,
    CONSTRAINT fk_enrollments_course_id FOREIGN KEY (course_id) REFERENCES public.courses (id) ON DELETE NO ACTION ON UPDATE NO ACTION NOT DEFERRABLE,
    CONSTRAINT fk_enrollments_resume_lesson_id FOREIGN KEY (resume_lesson_id) REFERENCES public.lessons (id) ON DELETE NO ACTION ON UPDATE NO ACTION NOT DEFERRABLE,
    CONSTRAINT uq_enrollments_user_course UNIQUE (user_id, course_id) NOT DEFERRABLE,
    CONSTRAINT ck_enrollments_status CHECK (status IN ('active', 'completed', 'left'))
);

CREATE INDEX ix_enrollments_course_id ON public.enrollments (course_id);
CREATE INDEX ix_enrollments_resume_lesson_id ON public.enrollments (resume_lesson_id);

CREATE TABLE public.lesson_progress (
    id uuid NOT NULL,
    enrollment_id uuid NOT NULL,
    lesson_id uuid NOT NULL,
    completed boolean NOT NULL,
    mastery_skipped boolean NOT NULL,
    CONSTRAINT pk_lesson_progress PRIMARY KEY (id),
    CONSTRAINT fk_lesson_progress_enrollment_id FOREIGN KEY (enrollment_id) REFERENCES public.enrollments (id) ON DELETE NO ACTION ON UPDATE NO ACTION NOT DEFERRABLE,
    CONSTRAINT fk_lesson_progress_lesson_id FOREIGN KEY (lesson_id) REFERENCES public.lessons (id) ON DELETE NO ACTION ON UPDATE NO ACTION NOT DEFERRABLE,
    CONSTRAINT uq_lesson_progress_enrollment_lesson UNIQUE (enrollment_id, lesson_id) NOT DEFERRABLE,
    CONSTRAINT ck_lesson_progress_skip_completed CHECK (NOT mastery_skipped OR completed)
);

CREATE INDEX ix_lesson_progress_lesson_id ON public.lesson_progress (lesson_id);
