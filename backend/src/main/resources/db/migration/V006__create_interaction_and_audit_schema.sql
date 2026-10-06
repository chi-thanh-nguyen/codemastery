-- Initial physical schema: V006. Applied migrations are immutable.
-- Application supplies UUIDs, timestamps, and lifecycle values; no seed data.

CREATE TABLE public.discussion_posts (
    id uuid NOT NULL,
    lesson_id uuid NOT NULL,
    author_id uuid NOT NULL,
    parent_post_id uuid NULL,
    content text NOT NULL,
    created_at timestamptz NOT NULL,
    is_hidden boolean NOT NULL,
    CONSTRAINT pk_discussion_posts PRIMARY KEY (id),
    CONSTRAINT fk_discussion_posts_lesson_id FOREIGN KEY (lesson_id) REFERENCES public.lessons (id) ON DELETE NO ACTION ON UPDATE NO ACTION NOT DEFERRABLE,
    CONSTRAINT fk_discussion_posts_author_id FOREIGN KEY (author_id) REFERENCES public.users (id) ON DELETE NO ACTION ON UPDATE NO ACTION NOT DEFERRABLE,
    CONSTRAINT fk_discussion_posts_parent_post_id FOREIGN KEY (parent_post_id) REFERENCES public.discussion_posts (id) ON DELETE NO ACTION ON UPDATE NO ACTION NOT DEFERRABLE,
    CONSTRAINT ck_discussion_posts_text CHECK (content ~ '[^[:space:]]'),
    CONSTRAINT ck_discussion_posts_not_self CHECK (parent_post_id IS NULL OR parent_post_id <> id)
);

CREATE INDEX ix_discussion_posts_lesson_id ON public.discussion_posts (lesson_id);
CREATE INDEX ix_discussion_posts_author_id ON public.discussion_posts (author_id);
CREATE INDEX ix_discussion_posts_parent_post_id ON public.discussion_posts (parent_post_id);

CREATE TABLE public.notifications (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    message text NOT NULL,
    is_read boolean NOT NULL,
    created_at timestamptz NOT NULL,
    CONSTRAINT pk_notifications PRIMARY KEY (id),
    CONSTRAINT fk_notifications_user_id FOREIGN KEY (user_id) REFERENCES public.users (id) ON DELETE NO ACTION ON UPDATE NO ACTION NOT DEFERRABLE,
    CONSTRAINT ck_notifications_text CHECK (message ~ '[^[:space:]]')
);

CREATE INDEX ix_notifications_user_id ON public.notifications (user_id);

CREATE TABLE public.content_reports (
    id uuid NOT NULL,
    reporter_id uuid NOT NULL,
    discussion_post_id uuid NULL,
    lesson_id uuid NULL,
    reason text NOT NULL,
    status text NOT NULL,
    created_at timestamptz NOT NULL,
    resolved_at timestamptz NULL,
    CONSTRAINT pk_content_reports PRIMARY KEY (id),
    CONSTRAINT fk_content_reports_reporter_id FOREIGN KEY (reporter_id) REFERENCES public.users (id) ON DELETE NO ACTION ON UPDATE NO ACTION NOT DEFERRABLE,
    CONSTRAINT fk_content_reports_discussion_post_id FOREIGN KEY (discussion_post_id) REFERENCES public.discussion_posts (id) ON DELETE NO ACTION ON UPDATE NO ACTION NOT DEFERRABLE,
    CONSTRAINT fk_content_reports_lesson_id FOREIGN KEY (lesson_id) REFERENCES public.lessons (id) ON DELETE NO ACTION ON UPDATE NO ACTION NOT DEFERRABLE,
    CONSTRAINT ck_content_reports_text CHECK (reason ~ '[^[:space:]]'),
    CONSTRAINT ck_content_reports_target CHECK ((discussion_post_id IS NOT NULL AND lesson_id IS NULL) OR (discussion_post_id IS NULL AND lesson_id IS NOT NULL)),
    CONSTRAINT ck_content_reports_status CHECK (status IN ('open', 'resolved', 'dismissed')),
    CONSTRAINT ck_content_reports_resolution CHECK ((status = 'open' AND resolved_at IS NULL) OR (status IN ('resolved', 'dismissed') AND resolved_at IS NOT NULL)),
    CONSTRAINT ck_content_reports_times CHECK (resolved_at IS NULL OR resolved_at >= created_at)
);

CREATE INDEX ix_content_reports_reporter_id ON public.content_reports (reporter_id);
CREATE INDEX ix_content_reports_discussion_post_id ON public.content_reports (discussion_post_id);
CREATE INDEX ix_content_reports_lesson_id ON public.content_reports (lesson_id);

CREATE TABLE public.audit_logs (
    id uuid NOT NULL,
    actor_id uuid NOT NULL,
    action text NOT NULL,
    target_type text NOT NULL,
    target_reference text NOT NULL,
    occurred_at timestamptz NOT NULL,
    CONSTRAINT pk_audit_logs PRIMARY KEY (id),
    CONSTRAINT fk_audit_logs_actor_id FOREIGN KEY (actor_id) REFERENCES public.users (id) ON DELETE NO ACTION ON UPDATE NO ACTION NOT DEFERRABLE,
    CONSTRAINT ck_audit_logs_text CHECK (action ~ '[^[:space:]]' AND target_type ~ '[^[:space:]]' AND target_reference ~ '[^[:space:]]')
);

CREATE INDEX ix_audit_logs_actor_id ON public.audit_logs (actor_id);
