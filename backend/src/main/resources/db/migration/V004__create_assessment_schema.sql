-- Initial physical schema: V004. Applied migrations are immutable.
-- Application supplies UUIDs, timestamps, and lifecycle values; no seed data.

CREATE TABLE public.quizzes (
    id uuid NOT NULL,
    course_id uuid NOT NULL,
    title text NOT NULL,
    kind text NOT NULL,
    time_limit_seconds integer NOT NULL,
    publication_status text NOT NULL,
    CONSTRAINT pk_quizzes PRIMARY KEY (id),
    CONSTRAINT fk_quizzes_course_id FOREIGN KEY (course_id) REFERENCES public.courses (id) ON DELETE NO ACTION ON UPDATE NO ACTION NOT DEFERRABLE,
    CONSTRAINT ck_quizzes_text CHECK (title ~ '[^[:space:]]'),
    CONSTRAINT ck_quizzes_kind CHECK (kind IN ('diagnostic', 'practice', 'test')),
    CONSTRAINT ck_quizzes_time_limit CHECK (time_limit_seconds > 0),
    CONSTRAINT ck_quizzes_publication_status CHECK (publication_status IN ('hidden', 'published'))
);

CREATE INDEX ix_quizzes_course_id ON public.quizzes (course_id);

CREATE TABLE public.questions (
    id uuid NOT NULL,
    skill_id uuid NOT NULL,
    prompt text NOT NULL,
    kind text NOT NULL,
    difficulty text NOT NULL,
    options text[] NOT NULL,
    correct_options boolean[] NOT NULL,
    CONSTRAINT pk_questions PRIMARY KEY (id),
    CONSTRAINT fk_questions_skill_id FOREIGN KEY (skill_id) REFERENCES public.skills (id) ON DELETE NO ACTION ON UPDATE NO ACTION NOT DEFERRABLE,
    CONSTRAINT ck_questions_text CHECK (prompt ~ '[^[:space:]]'),
    CONSTRAINT ck_questions_kind CHECK (kind IN ('single_choice', 'multiple_choice', 'true_false')),
    CONSTRAINT ck_questions_difficulty CHECK (difficulty IN ('easy', 'medium', 'hard')),
    CONSTRAINT ck_questions_option_arrays CHECK (CASE WHEN array_ndims(options) = 1 AND array_ndims(correct_options) = 1 AND array_lower(options, 1) = 1 AND array_lower(correct_options, 1) = 1 THEN cardinality(options) >= 2 AND cardinality(options) = cardinality(correct_options) AND array_position(options, NULL) IS NULL AND array_position(correct_options, NULL) IS NULL ELSE false END),
    CONSTRAINT ck_questions_answer_key CHECK (CASE WHEN array_ndims(correct_options) = 1 THEN CASE WHEN kind IN ('single_choice', 'true_false') THEN cardinality(array_positions(correct_options, true)) = 1 ELSE cardinality(array_positions(correct_options, true)) >= 1 END ELSE false END),
    CONSTRAINT ck_questions_true_false CHECK (kind <> 'true_false' OR options = ARRAY['True', 'False']::text[])
);

CREATE INDEX ix_questions_skill_id ON public.questions (skill_id);

CREATE TABLE public.quiz_questions (
    id uuid NOT NULL,
    quiz_id uuid NOT NULL,
    question_id uuid NOT NULL,
    position integer NOT NULL,
    CONSTRAINT pk_quiz_questions PRIMARY KEY (id),
    CONSTRAINT fk_quiz_questions_quiz_id FOREIGN KEY (quiz_id) REFERENCES public.quizzes (id) ON DELETE NO ACTION ON UPDATE NO ACTION NOT DEFERRABLE,
    CONSTRAINT fk_quiz_questions_question_id FOREIGN KEY (question_id) REFERENCES public.questions (id) ON DELETE NO ACTION ON UPDATE NO ACTION NOT DEFERRABLE,
    CONSTRAINT uq_quiz_questions_pair UNIQUE (quiz_id, question_id) NOT DEFERRABLE,
    CONSTRAINT uq_quiz_questions_position UNIQUE (quiz_id, position) DEFERRABLE INITIALLY IMMEDIATE,
    CONSTRAINT ck_quiz_questions_position CHECK (position >= 1)
);

CREATE INDEX ix_quiz_questions_question_id ON public.quiz_questions (question_id);

CREATE TABLE public.attempts (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    quiz_id uuid NOT NULL,
    attempt_number integer NOT NULL,
    started_at timestamptz NOT NULL,
    expires_at timestamptz NOT NULL,
    submitted_at timestamptz NULL,
    score_percentage numeric(5,2) NULL,
    CONSTRAINT pk_attempts PRIMARY KEY (id),
    CONSTRAINT fk_attempts_user_id FOREIGN KEY (user_id) REFERENCES public.users (id) ON DELETE NO ACTION ON UPDATE NO ACTION NOT DEFERRABLE,
    CONSTRAINT fk_attempts_quiz_id FOREIGN KEY (quiz_id) REFERENCES public.quizzes (id) ON DELETE NO ACTION ON UPDATE NO ACTION NOT DEFERRABLE,
    CONSTRAINT uq_attempts_user_quiz_number UNIQUE (user_id, quiz_id, attempt_number) NOT DEFERRABLE,
    CONSTRAINT ck_attempts_number CHECK (attempt_number >= 1),
    CONSTRAINT ck_attempts_times CHECK (expires_at > started_at AND (submitted_at IS NULL OR submitted_at >= started_at)),
    CONSTRAINT ck_attempts_submission CHECK ((submitted_at IS NULL AND score_percentage IS NULL) OR (submitted_at IS NOT NULL AND score_percentage IS NOT NULL)),
    CONSTRAINT ck_attempts_score CHECK (score_percentage IS NULL OR score_percentage BETWEEN 0 AND 100)
);

CREATE INDEX ix_attempts_quiz_id ON public.attempts (quiz_id);

CREATE TABLE public.attempt_answers (
    id uuid NOT NULL,
    attempt_id uuid NOT NULL,
    question_id uuid NOT NULL,
    position integer NOT NULL,
    selected_options boolean[] NOT NULL,
    is_correct boolean NULL,
    CONSTRAINT pk_attempt_answers PRIMARY KEY (id),
    CONSTRAINT fk_attempt_answers_attempt_id FOREIGN KEY (attempt_id) REFERENCES public.attempts (id) ON DELETE NO ACTION ON UPDATE NO ACTION NOT DEFERRABLE,
    CONSTRAINT fk_attempt_answers_question_id FOREIGN KEY (question_id) REFERENCES public.questions (id) ON DELETE NO ACTION ON UPDATE NO ACTION NOT DEFERRABLE,
    CONSTRAINT uq_attempt_answers_question UNIQUE (attempt_id, question_id) NOT DEFERRABLE,
    CONSTRAINT uq_attempt_answers_position UNIQUE (attempt_id, position) NOT DEFERRABLE,
    CONSTRAINT ck_attempt_answers_position CHECK (position >= 1),
    CONSTRAINT ck_attempt_answers_selection CHECK (CASE WHEN array_ndims(selected_options) = 1 AND array_lower(selected_options, 1) = 1 THEN cardinality(selected_options) >= 2 AND array_position(selected_options, NULL) IS NULL ELSE false END)
);

CREATE INDEX ix_attempt_answers_question_id ON public.attempt_answers (question_id);

CREATE TABLE public.assignments (
    id uuid NOT NULL,
    course_id uuid NOT NULL,
    lesson_id uuid NULL,
    title text NOT NULL,
    instructions text NOT NULL,
    deadline_at timestamptz NOT NULL,
    publication_status text NOT NULL,
    CONSTRAINT pk_assignments PRIMARY KEY (id),
    CONSTRAINT fk_assignments_course_id FOREIGN KEY (course_id) REFERENCES public.courses (id) ON DELETE NO ACTION ON UPDATE NO ACTION NOT DEFERRABLE,
    CONSTRAINT fk_assignments_lesson_id FOREIGN KEY (lesson_id) REFERENCES public.lessons (id) ON DELETE NO ACTION ON UPDATE NO ACTION NOT DEFERRABLE,
    CONSTRAINT ck_assignments_text CHECK (title ~ '[^[:space:]]' AND instructions ~ '[^[:space:]]'),
    CONSTRAINT ck_assignments_publication_status CHECK (publication_status IN ('hidden', 'published'))
);

CREATE INDEX ix_assignments_course_id ON public.assignments (course_id);
CREATE INDEX ix_assignments_lesson_id ON public.assignments (lesson_id);

CREATE TABLE public.submissions (
    id uuid NOT NULL,
    assignment_id uuid NOT NULL,
    user_id uuid NOT NULL,
    object_key text NOT NULL,
    original_filename text NOT NULL,
    submitted_at timestamptz NOT NULL,
    grade_percentage numeric(5,2) NULL,
    feedback text NULL,
    graded_at timestamptz NULL,
    CONSTRAINT pk_submissions PRIMARY KEY (id),
    CONSTRAINT fk_submissions_assignment_id FOREIGN KEY (assignment_id) REFERENCES public.assignments (id) ON DELETE NO ACTION ON UPDATE NO ACTION NOT DEFERRABLE,
    CONSTRAINT fk_submissions_user_id FOREIGN KEY (user_id) REFERENCES public.users (id) ON DELETE NO ACTION ON UPDATE NO ACTION NOT DEFERRABLE,
    CONSTRAINT uq_submissions_assignment_user UNIQUE (assignment_id, user_id) NOT DEFERRABLE,
    CONSTRAINT ck_submissions_text CHECK (object_key ~ '[^[:space:]]' AND original_filename ~ '[^[:space:]]' AND (feedback IS NULL OR feedback ~ '[^[:space:]]')),
    CONSTRAINT ck_submissions_grading CHECK ((grade_percentage IS NULL AND feedback IS NULL AND graded_at IS NULL) OR (grade_percentage IS NOT NULL AND feedback IS NOT NULL AND graded_at IS NOT NULL)),
    CONSTRAINT ck_submissions_grade CHECK (grade_percentage IS NULL OR grade_percentage BETWEEN 0 AND 100),
    CONSTRAINT ck_submissions_times CHECK (graded_at IS NULL OR graded_at >= submitted_at)
);

CREATE INDEX ix_submissions_user_id ON public.submissions (user_id);
