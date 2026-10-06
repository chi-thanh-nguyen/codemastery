-- Initial physical schema: V003. Applied migrations are immutable.
-- Application supplies UUIDs, timestamps, and lifecycle values; no seed data.

CREATE TABLE public.skills (
    id uuid NOT NULL,
    course_id uuid NOT NULL,
    name text NOT NULL,
    description text NULL,
    CONSTRAINT pk_skills PRIMARY KEY (id),
    CONSTRAINT fk_skills_course_id FOREIGN KEY (course_id) REFERENCES public.courses (id) ON DELETE NO ACTION ON UPDATE NO ACTION NOT DEFERRABLE,
    CONSTRAINT uq_skills_course_id UNIQUE (course_id, id) NOT DEFERRABLE,
    CONSTRAINT ck_skills_text CHECK (name ~ '[^[:space:]]' AND (description IS NULL OR description ~ '[^[:space:]]'))
);

CREATE TABLE public.skill_prerequisites (
    id uuid NOT NULL,
    course_id uuid NOT NULL,
    skill_id uuid NOT NULL,
    prerequisite_skill_id uuid NOT NULL,
    CONSTRAINT pk_skill_prerequisites PRIMARY KEY (id),
    CONSTRAINT fk_skill_prerequisites_skill_id FOREIGN KEY (course_id, skill_id) REFERENCES public.skills (course_id, id) ON DELETE NO ACTION ON UPDATE NO ACTION NOT DEFERRABLE,
    CONSTRAINT fk_skill_prerequisites_prerequisite_skill_id FOREIGN KEY (course_id, prerequisite_skill_id) REFERENCES public.skills (course_id, id) ON DELETE NO ACTION ON UPDATE NO ACTION NOT DEFERRABLE,
    CONSTRAINT uq_skill_prerequisites_edge UNIQUE (course_id, skill_id, prerequisite_skill_id) NOT DEFERRABLE,
    CONSTRAINT ck_skill_prerequisites_not_self CHECK (skill_id <> prerequisite_skill_id)
);

CREATE INDEX ix_skill_prerequisites_prerequisite ON public.skill_prerequisites (course_id, prerequisite_skill_id);

CREATE TABLE public.lesson_skills (
    id uuid NOT NULL,
    lesson_id uuid NOT NULL,
    skill_id uuid NOT NULL,
    CONSTRAINT pk_lesson_skills PRIMARY KEY (id),
    CONSTRAINT fk_lesson_skills_lesson_id FOREIGN KEY (lesson_id) REFERENCES public.lessons (id) ON DELETE NO ACTION ON UPDATE NO ACTION NOT DEFERRABLE,
    CONSTRAINT fk_lesson_skills_skill_id FOREIGN KEY (skill_id) REFERENCES public.skills (id) ON DELETE NO ACTION ON UPDATE NO ACTION NOT DEFERRABLE,
    CONSTRAINT uq_lesson_skills_pair UNIQUE (lesson_id, skill_id) NOT DEFERRABLE
);

CREATE INDEX ix_lesson_skills_skill_id ON public.lesson_skills (skill_id);
