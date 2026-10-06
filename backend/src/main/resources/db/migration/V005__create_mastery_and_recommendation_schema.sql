-- Initial physical schema: V005. Applied migrations are immutable.
-- Application supplies UUIDs, timestamps, and lifecycle values; no seed data.

CREATE TABLE public.learner_skill_masteries (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    skill_id uuid NOT NULL,
    state text NOT NULL,
    CONSTRAINT pk_learner_skill_masteries PRIMARY KEY (id),
    CONSTRAINT fk_learner_skill_masteries_user_id FOREIGN KEY (user_id) REFERENCES public.users (id) ON DELETE NO ACTION ON UPDATE NO ACTION NOT DEFERRABLE,
    CONSTRAINT fk_learner_skill_masteries_skill_id FOREIGN KEY (skill_id) REFERENCES public.skills (id) ON DELETE NO ACTION ON UPDATE NO ACTION NOT DEFERRABLE,
    CONSTRAINT uq_learner_skill_masteries_user_skill UNIQUE (user_id, skill_id) NOT DEFERRABLE,
    CONSTRAINT ck_learner_skill_masteries_state CHECK (state IN ('unknown', 'learning', 'mastered', 'weak'))
);

CREATE INDEX ix_learner_skill_masteries_skill_id ON public.learner_skill_masteries (skill_id);

CREATE TABLE public.path_recommendations (
    id uuid NOT NULL,
    enrollment_id uuid NOT NULL,
    lesson_id uuid NOT NULL,
    recommendation_type text NOT NULL,
    reason text NOT NULL,
    recommended_at timestamptz NOT NULL,
    CONSTRAINT pk_path_recommendations PRIMARY KEY (id),
    CONSTRAINT fk_path_recommendations_enrollment_id FOREIGN KEY (enrollment_id) REFERENCES public.enrollments (id) ON DELETE NO ACTION ON UPDATE NO ACTION NOT DEFERRABLE,
    CONSTRAINT fk_path_recommendations_lesson_id FOREIGN KEY (lesson_id) REFERENCES public.lessons (id) ON DELETE NO ACTION ON UPDATE NO ACTION NOT DEFERRABLE,
    CONSTRAINT ck_path_recommendations_text CHECK (reason ~ '[^[:space:]]'),
    CONSTRAINT ck_path_recommendations_type CHECK (recommendation_type IN ('continue', 'skip', 'remedial'))
);

CREATE INDEX ix_path_recommendations_enrollment_id ON public.path_recommendations (enrollment_id);
CREATE INDEX ix_path_recommendations_lesson_id ON public.path_recommendations (lesson_id);
