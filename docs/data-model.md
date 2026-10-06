# CodeMastery Data Model

## 1. Purpose and Scope

This document records the conceptual data model and data responsibilities of the
currently approved CodeMastery architecture. Its entity and relationship baseline
comes from section 9 of the
[canonical approved architecture](references/originals/approved-architecture.docx),
read together with the project requirements and module-boundary rules.

The approved concepts describe the information the system must represent. The
implementation constraints describe how persistence and storage must be governed.
Neither establishes that a physical schema or persistence behavior is implemented.

Sections 2–9 preserve the approved conceptual model. Section 11 records the
separately approved Batch 7A.1 physical baseline implemented in Batch 7A.2,
including the leader's `admin` role-literal amendment. Conceptual cardinalities
alone do not supply physical constraints; the explicit physical section does.
Feature-specific behavior still deferred is identified in section 12.

## 2. Data Storage Responsibilities

| Storage | Approved responsibility |
|---|---|
| PostgreSQL | Relational domain data, including skills and prerequisite relationships, learning progress, assessment information, mastery, recommendations, interaction data, and administrative audit information. |
| MinIO through its S3-compatible interface | Approved file-based content such as slides, attachments, and assignment submissions. |
| External video hosts | Video learning material accessed through external embed links. Project-hosted video and transcoding are outside scope. |

PostgreSQL represents the metadata/references needed to associate stored files
with domain records. File content belongs in object storage. Object-key generation
and bucket layouts remain application/storage contracts.
The initial object reference and filename columns are specified in section 11.
Protected file operations must use the approved storage abstraction and enforce
authorization, as required by the
[contribution storage rules](../CONTRIBUTING.md#object-storage-guidelines).

## 3. Core Domain Entities

The following entities are explicitly listed in the approved high-level data
model. Their responsibilities are conceptual, not field or table definitions.

| Entity | Approved conceptual responsibility |
|---|---|
| `User` | An account with a project role and account status. |
| `Course` | A course, its publication state, learner-facing prerequisite description, and adaptive settings. |
| `Chapter` | An ordered group of lessons within a course. |
| `Lesson` | A learning unit in the canonical course order. |
| `LearningMaterial` | Lesson material such as text, slides/attachments, or an external video link. |
| `Enrollment` | A learner's participation in a course, participation status, and most recent learning position. |
| `LessonProgress` | Progress for a lesson within an enrollment, including completion and mastery-based skipping. |
| `Quiz` | A diagnostic, practice, or test quiz with a time limit. |
| `Question` | A question and its answer information, associated skill, and difficulty. |
| `Attempt` | A learner's quiz attempt, including result, timing, and answered questions. |
| `AttemptAnswer` | An answer within an attempt and its correctness information, used in mastery updates. |
| `Assignment` | A file-submission assessment with a deadline. |
| `Submission` | A learner's assignment submission, instructor grade, and feedback. |
| `Skill` | A programming skill within a course. |
| `SkillPrerequisite` | A directed prerequisite relationship between skills. |
| `LearnerSkillMastery` | A learner's mastery level and state for a skill. |
| `PathRecommendation` | Recommendation history describing the recommendation type, target lesson, reason, and time. |
| `DiscussionPost` | Lesson Q&A, including original posts and replies. |
| `Notification` | An in-app notification for a user, including read status. |
| `ContentReport` | A user's report of content and its handling status. |
| `AuditLog` | A significant administrative action, identifying who acted, the action, its target, and when it occurred. |

Learner, Instructor, and Administrator are the approved roles of `User`.
The architecture treats role as an account attribute; it does not establish
separate persistent role or actor entities. Account states are conceptually
active/locked; section 11 defines their initial persistence representation.

The architecture explicitly describes the course's `adaptive_settings` as JSON
configuration for mastery thresholds. That conceptual configuration choice is
already approved. Its keys, threshold values, and application validation remain
deferred; section 11 selects JSONB without a database default.

## 4. Approved Relationships

Section 9 preserves the main relationships and cardinalities from the approved
architecture. `1:N` means one-to-many, `N:1` means many-to-one, and `N:M` means
many-to-many. These express conceptual cardinality without selecting minimum
participation, physical uniqueness, nullability, or foreign-key behavior.

- A `User` in the Instructor role is associated with courses. Learner
  participation is represented separately by the `User`–`Course` relationship
  through `Enrollment`.
- Course content follows `Course` → `Chapter` → `Lesson` → `LearningMaterial`.
  `Enrollment` relates a user and course, and has related `LessonProgress`
  records for lessons.
- Skills belong to courses. `SkillPrerequisite` relates skills, while lessons
  have an `N:M` relationship with skills. The architecture calls the lesson–skill
  association `LessonSkill`; this association name does not define an additional
  standalone persistent entity or a physical join-table schema here.
- A `Question` relates to a skill, and quizzes and questions have an `N:M`
  relationship. An `Attempt` relates to a user and quiz and has associated
  `AttemptAnswer` information; each answer relates to a question.
- The architecture lists the assignment parent relationship as `N:1`
  `Course/Lesson`. The conceptual description is preserved; section 11 implements
  required Course and optional Lesson references. A `Submission`
  relates to an assignment and a user.
- `LearnerSkillMastery` relates a user and skill. `PathRecommendation` relates
  an enrollment and a target lesson.
- `DiscussionPost` relates to a lesson and user and is self-referencing for
  replies. `Notification` relates to a user. `ContentReport` relates to its
  reporting user and refers to a `DiscussionPost` or `Lesson`. `AuditLog`
  relates to a user acting as the administrative actor.

These associations do not grant access to another module's internals. Data
sharing must preserve the approved
[module-boundary ADR](decisions/001-modular-monolith-module-boundaries.md).

## 5. Skill Graph Model

Every `Skill` belongs to one `Course`. Prerequisite relationships are directed
relationships between skills within that course, represented conceptually by
`SkillPrerequisite`. The graph is scoped to a course and must be acyclic;
relationships that would introduce a cycle must be rejected.

Skills and prerequisite relationships are relational domain data in PostgreSQL.
The graph concept does not select a separate graph database. Traversal algorithms,
cycle-detection implementation, recursive queries, and physical constraint
mechanisms remain outside this conceptual model.

## 6. Learning Progress and Mastery

`LessonProgress` represents lesson completion within an enrollment, including
lessons skipped through demonstrated mastery. `LearnerSkillMastery` represents
knowledge of a skill for a learner. Lesson completion and skill mastery are
distinct information; completing content does not by itself establish mastery.

The approved mastery states are `Unknown`, `Learning`, `Mastered`, and `Weak`.
`PathRecommendation` preserves explainable recommendation history: `continue`,
`skip`/Test-out, or `remedial`, together with the target lesson, reason, and time.
These concepts do not prescribe SQL columns or enum persistence.

The course structure retains canonical lesson order. Adaptive decisions guide
learning and mark related progress without rewriting that structure. Within the
adaptive flow, mastery changes and the related `LessonProgress` update must
succeed or fail together in one database transaction. Transaction propagation
and event execution details belong to later approved integration contracts.

Mastery thresholds and scoring formulas are not defined here. Their detailed
business rules belong in [adaptive-learning documentation](adaptive-learning.md)
when approved.

## 7. Assessment Data

`Quiz` and `Question` represent quiz assessments and their question content.
`Attempt` and `AttemptAnswer` represent learner attempts and answer-level results,
supporting results and attempt history. Question-to-skill associations provide
the conceptual link between assessment evidence and mastery.

Submitted-quiz results and answer-level information can drive real-time mastery
updates through the approved Assessment → Adaptive event mechanism. The event's
payload and execution contract are separate from this data model.

`Assignment` and `Submission` represent deadline-based file submissions,
instructor grading, and feedback. Manually graded file-submission assignments are
tracked for course assessment/completion grades but do not directly trigger
real-time adaptive mastery updates.

Section 11 defines the initial assessment representation and grading baseline.
Attempt limits and detailed application execution remain feature contracts.

## 8. Interaction, Notification, Reporting, and Audit Data

- `DiscussionPost` supports lesson Q&A with original posts and replies. Its
  conceptual self-reference does not define a thread-storage implementation.
- `Notification` represents in-app information delivered to a user and whether
  it has been read. Interaction owns notification creation and delivery;
  Adaptive provides recommendation/reason information through approved
  communication contracts.
- `ContentReport` represents a learner/user report of a discussion post or
  lesson and its handling status. Interaction owns content-report functionality;
  Admin handles reported content through approved module communication.
- `AuditLog` records significant administrative actions for accountability.
  Admin owns administrative auditing.

Admin/Reporting consumes learning information for metrics such as enrolment,
completion, quiz outcomes, and mastery distribution. It does not own adaptive
decisions. A reporting view or metric is not thereby a new persistent entity.

Section 11 defines the initial moderation, notification, and audit representation.
Concrete audit action codes and module execution contracts remain feature work.

## 9. Conceptual Relationship Summary

Responsibilities and module ownership follow the architecture's functional
responsibilities and the module-boundary ADR. Ownership identifies the logical
module responsible for the concept, not a SQL schema or permission to bypass
another module's public contract.

| Entity | Responsibility | Approved main relationships | Owning module |
|---|---|---|---|
| `User` | Account, role, status | `1:N` Course as Instructor, Enrollment, Attempt, Submission, Notification | `auth` |
| `Course` | Course and adaptive configuration | `1:N` Chapter; `1:N` Skill; `N:M` User through Enrollment; Instructor/User association | `course` |
| `Chapter` | Ordered lesson group | `N:1` Course; `1:N` Lesson | `course` |
| `Lesson` | Learning unit | `N:1` Chapter; `1:N` LearningMaterial; `N:M` Skill through the LessonSkill association | `course` |
| `LearningMaterial` | Lesson content | `N:1` Lesson; file references to object storage | `course` |
| `Enrollment` | Course participation and resume position | `N:1` User; `N:1` Course; `1:N` LessonProgress | `course` |
| `LessonProgress` | Lesson completion/mastery-based skip | `N:1` Enrollment; `N:1` Lesson | `course` |
| `Quiz` | Timed quiz assessment | `N:1` Course; `N:M` Question | `assessment` |
| `Question` | Question, answers, skill, difficulty | `N:1` Skill; `N:M` Quiz | `assessment` |
| `Attempt` | Learner quiz attempt | `N:1` User; `N:1` Quiz; `1:N` AttemptAnswer | `assessment` |
| `AttemptAnswer` | Answer-level assessment evidence | `N:1` Attempt; `N:1` Question | `assessment` |
| `Assignment` | Deadline-based file assessment | `N:1` Course/Lesson, as recorded in the architecture; submissions relate through Submission | `assessment` |
| `Submission` | Submitted file, grade, feedback | `N:1` Assignment; `N:1` User | `assessment` |
| `Skill` | Course programming skill | `N:1` Course; `N:M` Skill through SkillPrerequisite | `adaptive` |
| `SkillPrerequisite` | Directed prerequisite relationship | Skill–Skill within a course | `adaptive` |
| `LearnerSkillMastery` | Learner mastery for a skill | `N:1` User; `N:1` Skill | `adaptive` |
| `PathRecommendation` | Explainable recommendation history | `N:1` Enrollment; `N:1` Lesson | `adaptive` |
| `DiscussionPost` | Lesson Q&A and replies | `N:1` Lesson; `N:1` User; self-reference for replies | `interaction` |
| `Notification` | In-app notification/read status | `N:1` User | `interaction` |
| `ContentReport` | Content report and handling status | `N:1` User as reporter; refers to DiscussionPost or Lesson | `interaction` |
| `AuditLog` | Administrative action record | `N:1` User as actor | `admin` |

## 10. Physical Schema and Migration Policy

The approved implementation constraints are:

- PostgreSQL is the relational database; Flyway owns schema evolution.
- Versioned migrations belong under
  `backend/src/main/resources/db/migration/`.
- JPA mappings, entities, repositories, constraints, and migrations must remain
  consistent with the approved data model and each other.
- A migration that has been applied, or may already have been applied in another
  environment, must not be modified. Corrections require a new versioned
  migration.
- Seed/demo data is separate from schema migrations. Seed SQL belongs under
  `sample-data/seed/`; sample learning materials belong under
  `sample-data/materials/`.

The initial physical schema and migration ownership are recorded below. Follow the
[database and migration guidelines](../CONTRIBUTING.md#database-and-migration-guidelines)
and the [approved repository structure](repository-structure.md).

## 11. Approved Initial PostgreSQL Physical Baseline

The project leader approved Batch 7A.1 decisions A1–G3 and its complete table
specifications for implementation in Batch 7A.2. Before population, the leader
confirmed that V001–V006 had never been applied to a persistent project database.
This approval resolves the initial physical choices without adding conceptual
entities or changing module ownership. The administrative role's stored literal
is amended to `admin`; `administrator` is not an accepted stored value.

### Conventions

- PostgreSQL `public` schema; lowercase unquoted snake_case identifiers.
- Every entity and association row has application-generated UUID v4 `id`,
  stored as native `uuid` and mapped to Java `UUID`.
- Descriptive text and codes use `text`; finite sets use named CHECK constraints,
  without PostgreSQL ENUMs or lookup tables.
- Instants use `timestamptz`, Java `Instant`, and UTC handling. Only lifecycle
  timestamps are stored; no blanket creation/change timestamp pair exists.
- No database defaults for identifiers, timestamps, states, or booleans.
- Every FK explicitly uses `ON DELETE NO ACTION ON UPDATE NO ACTION` and is
  not deferrable. Referenced history is not implicitly deleted.
- PKs are named `pk_<table>` and FKs `fk_<table>_<fk_column>`; composite
  prerequisite FKs use the skill endpoint column as their naming suffix.
- Chapter, Lesson, and QuizQuestion parent/position uniqueness is
  `DEFERRABLE INITIALLY IMMEDIATE`; all other UQs are not deferrable.
- PK/UQ supporting indexes and the exact additional B-tree indexes below are
  the initial index baseline. No JSON, array, search, or reporting index exists.
- No schema seed data, UUID/timestamp generators, business triggers/functions,
  views, or module schemas are introduced.

### Migration Ownership and Complete Column Inventory

`NN` means NOT NULL; `NULL` means nullable. Each table's `id` is its named PK.
The six SQL files under `backend/src/main/resources/db/migration/` are the exact
DDL reference. The 23 tables implement 21 entities and two associations;
`lesson_skills` and `quiz_questions` do not add conceptual entities.

| Migration | Module | Table | Columns (PostgreSQL type, nullability) |
| --- | --- | --- | --- |
| V001 | auth | `users` | `id` uuid NN; `email` text NN; `password_hash` text NN; `display_name` text NN; `role` text NN; `account_status` text NN |
| V002 | course | `courses` | `id` uuid NN; `instructor_id` uuid NN; `title` text NN; `description` text NN; `prerequisite_description` text NULL; `publication_status` text NN; `adaptive_enabled` boolean NN; `adaptive_settings` jsonb NULL |
| V002 | course | `chapters` | `id` uuid NN; `course_id` uuid NN; `title` text NN; `position` integer NN |
| V002 | course | `lessons` | `id` uuid NN; `chapter_id` uuid NN; `title` text NN; `content_markdown` text NULL; `position` integer NN; `publication_status` text NN |
| V002 | course | `learning_materials` | `id` uuid NN; `lesson_id` uuid NN; `title` text NN; `material_type` text NN; `publication_status` text NN; `text_content` text NULL; `object_key` text NULL; `original_filename` text NULL; `external_video_url` text NULL |
| V002 | course | `enrollments` | `id` uuid NN; `user_id` uuid NN; `course_id` uuid NN; `status` text NN; `resume_lesson_id` uuid NULL |
| V002 | course | `lesson_progress` | `id` uuid NN; `enrollment_id` uuid NN; `lesson_id` uuid NN; `completed` boolean NN; `mastery_skipped` boolean NN |
| V003 | adaptive | `skills` | `id` uuid NN; `course_id` uuid NN; `name` text NN; `description` text NULL |
| V003 | adaptive | `skill_prerequisites` | `id` uuid NN; `course_id` uuid NN; `skill_id` uuid NN; `prerequisite_skill_id` uuid NN |
| V003 | adaptive | `lesson_skills` | `id` uuid NN; `lesson_id` uuid NN; `skill_id` uuid NN |
| V004 | assessment | `quizzes` | `id` uuid NN; `course_id` uuid NN; `title` text NN; `kind` text NN; `time_limit_seconds` integer NN; `publication_status` text NN |
| V004 | assessment | `questions` | `id` uuid NN; `skill_id` uuid NN; `prompt` text NN; `kind` text NN; `difficulty` text NN; `options` text[] NN; `correct_options` boolean[] NN |
| V004 | assessment | `quiz_questions` | `id` uuid NN; `quiz_id` uuid NN; `question_id` uuid NN; `position` integer NN |
| V004 | assessment | `attempts` | `id` uuid NN; `user_id` uuid NN; `quiz_id` uuid NN; `attempt_number` integer NN; `started_at` timestamptz NN; `expires_at` timestamptz NN; `submitted_at` timestamptz NULL; `score_percentage` numeric(5,2) NULL |
| V004 | assessment | `attempt_answers` | `id` uuid NN; `attempt_id` uuid NN; `question_id` uuid NN; `position` integer NN; `selected_options` boolean[] NN; `is_correct` boolean NULL |
| V004 | assessment | `assignments` | `id` uuid NN; `course_id` uuid NN; `lesson_id` uuid NULL; `title` text NN; `instructions` text NN; `deadline_at` timestamptz NN; `publication_status` text NN |
| V004 | assessment | `submissions` | `id` uuid NN; `assignment_id` uuid NN; `user_id` uuid NN; `object_key` text NN; `original_filename` text NN; `submitted_at` timestamptz NN; `grade_percentage` numeric(5,2) NULL; `feedback` text NULL; `graded_at` timestamptz NULL |
| V005 | adaptive | `learner_skill_masteries` | `id` uuid NN; `user_id` uuid NN; `skill_id` uuid NN; `state` text NN |
| V005 | adaptive | `path_recommendations` | `id` uuid NN; `enrollment_id` uuid NN; `lesson_id` uuid NN; `recommendation_type` text NN; `reason` text NN; `recommended_at` timestamptz NN |
| V006 | interaction | `discussion_posts` | `id` uuid NN; `lesson_id` uuid NN; `author_id` uuid NN; `parent_post_id` uuid NULL; `content` text NN; `created_at` timestamptz NN; `is_hidden` boolean NN |
| V006 | interaction | `notifications` | `id` uuid NN; `user_id` uuid NN; `message` text NN; `is_read` boolean NN; `created_at` timestamptz NN |
| V006 | interaction | `content_reports` | `id` uuid NN; `reporter_id` uuid NN; `discussion_post_id` uuid NULL; `lesson_id` uuid NULL; `reason` text NN; `status` text NN; `created_at` timestamptz NN; `resolved_at` timestamptz NULL |
| V006 | admin | `audit_logs` | `id` uuid NN; `actor_id` uuid NN; `action` text NN; `target_type` text NN; `target_reference` text NN; `occurred_at` timestamptz NN |

### State Catalogues

| Catalogue | Stored values |
| --- | --- |
| User role | `learner`, `instructor`, `admin` |
| Account status | `active`, `locked` |
| Publication | `hidden`, `published` |
| Enrollment | `active`, `completed`, `left` |
| Material | `text`, `file`, `external_video` |
| Quiz kind | `diagnostic`, `practice`, `test` |
| Question kind | `single_choice`, `multiple_choice`, `true_false` |
| Difficulty | `easy`, `medium`, `hard` |
| Mastery | `unknown`, `learning`, `mastered`, `weak` |
| Recommendation | `continue`, `skip`, `remedial` |
| Content report | `open`, `resolved`, `dismissed` |

Codes require explicit value mapping in future JPA enums; ordinal persistence
is not the contract. Audit action/target codes remain open nonblank text.

### Lifecycle and Payload Contracts

- Email identity is ASCII, trimmed and lowercased by the application using
  locale-independent handling. Only canonical lowercase, whitespace-free ASCII
  is stored, with email uniqueness. Full email syntax validation is application
  work; provider-specific dot/plus rewriting is not performed. `display_name`
  supplies the minimum profile. Passwords are encoded hashes only.
- Required descriptive text is nonblank. Optional descriptive text is absent
  or nonblank. Nullability follows lifecycle semantics, not convenience.
- Course/lesson/material/assessment visibility is `hidden` or `published`.
  Chapters are structural groups without independent publication state.
- One Enrollment exists per learner/course. Leaving retains progress and history;
  re-enrollment reuses the row and recomputes active/completed status. A resume
  Lesson is absent before the first visit. Progress uses completion and mastery
  skip flags; mastery skip must imply completion. Missing progress means neither.
- Persisted Chapter, Lesson, QuizQuestion, and captured AttemptAnswer positions
  are positive, 1-based integers. Sibling positions are unique; gaps are allowed.
- Material payloads are exclusive: text has only `text_content`; file has only
  `object_key` plus `original_filename`; external video has only its URL.
  Lesson Markdown is optional because material records can supply all content.
- Each file material/submission stores one opaque object key and original
  filename. Bucket stays configuration. No bytes, signed URLs, media type, size,
  or checksum are stored. Object-key generation and protected access are not DDL.
- Questions have ordered text options and a parallel boolean answer-key mask,
  with at least two entries, one dimension, lower bound 1, equal lengths, and
  no NULL elements. Single-choice/true-false has exactly one correct option;
  multiple-choice has at least one. True/false options are exactly `True`, `False`.
  Predict-output questions use these choice formats, without executing code.
- The application rejects blank option labels and ensures each selected mask
  matches its Question. All-false means unanswered. At attempt start it captures
  question membership/order in AttemptAnswer rows. Used Question definitions are
  immutable; edits create replacement Questions. Submitted attempts/answers are
  immutable in application behavior. No revision entity or snapshot engine exists.
- Quiz time limits are positive seconds. Attempt numbering is positive within
  learner/quiz. Expiry is captured at start; submission time and score are both
  absent while active and both present when submitted. Submission cannot predate
  start, and expiry follows start. Correctness is absent until submission.
- Initial quiz scoring is equal-weight exact selection-mask match, with incorrect
  or unanswered questions receiving zero and no partial credit. Percentage scores
  are 0–100, rounded half-up to two decimals. This is not a mastery threshold.
- Assignments require Course and may reference Lesson. One current Submission
  per assignment/user permits replacement before deadline while ungraded; graded
  records cannot be replaced initially. Latest submission time identifies the
  accepted file. Grade, nonblank feedback, and grading time are all absent before
  grading and all present afterward; grade is 0–100, and grading cannot predate
  submission. This does not implement deadline enforcement or grading.
- Current mastery is categorical only, unique per user/skill. A missing row means
  Unknown; explicit Unknown is allowed. No numeric mastery field is selected.
- Adaptive settings are SQL NULL or a JSON object; enabling adaptation requires
  a non-NULL object. Approved keys, thresholds, coverage, and graph validity must
  be validated by the application. No configuration default or JSON index exists.
- Recommendations retain enrollment, target Lesson, type, reason, and time without
  history-collapsing uniqueness. Their creation is application behavior.
- Discussion original posts have no parent; replies reference a root post in the
  same Lesson. Direct self-reference is rejected by the DB; one-level reply shape
  and visibility propagation are application rules. Notifications contain message,
  read flag, and creation time without a route/event framework.
- Reports target exactly one DiscussionPost or Lesson. Open reports have no
  resolution time; resolved/dismissed reports require one not preceding creation.
  Resolved means addressed by hiding content; dismissed closes without that action.
- Audit actor is mandatory; action, target type/reference, and occurrence time
  form the minimal durable record. Opaque audit targets deliberately have no FK.

### Exact Uniqueness and Additional Indexes

| Table | Unique constraints (columns) |
| --- | --- |
| `users` | `uq_users_email` (email) |
| `chapters` | `uq_chapters_course_position` (course_id, position) |
| `lessons` | `uq_lessons_chapter_position` (chapter_id, position) |
| `enrollments` | `uq_enrollments_user_course` (user_id, course_id) |
| `lesson_progress` | `uq_lesson_progress_enrollment_lesson` (enrollment_id, lesson_id) |
| `skills` | `uq_skills_course_id` (course_id, id) |
| `skill_prerequisites` | `uq_skill_prerequisites_edge` (course_id, skill_id, prerequisite_skill_id) |
| `lesson_skills` | `uq_lesson_skills_pair` (lesson_id, skill_id) |
| `quiz_questions` | `uq_quiz_questions_pair` (quiz_id, question_id); `uq_quiz_questions_position` (quiz_id, position) |
| `attempts` | `uq_attempts_user_quiz_number` (user_id, quiz_id, attempt_number) |
| `attempt_answers` | `uq_attempt_answers_question` (attempt_id, question_id); `uq_attempt_answers_position` (attempt_id, position) |
| `submissions` | `uq_submissions_assignment_user` (assignment_id, user_id) |
| `learner_skill_masteries` | `uq_learner_skill_masteries_user_skill` (user_id, skill_id) |

| Additional B-tree index | Columns |
| --- | --- |
| `ix_courses_instructor_id` | courses (instructor_id) |
| `ix_learning_materials_lesson_id` | learning_materials (lesson_id) |
| `ix_enrollments_course_id` | enrollments (course_id) |
| `ix_enrollments_resume_lesson_id` | enrollments (resume_lesson_id) |
| `ix_lesson_progress_lesson_id` | lesson_progress (lesson_id) |
| `ix_skill_prerequisites_prerequisite` | skill_prerequisites (course_id, prerequisite_skill_id) |
| `ix_lesson_skills_skill_id` | lesson_skills (skill_id) |
| `ix_quizzes_course_id` | quizzes (course_id) |
| `ix_questions_skill_id` | questions (skill_id) |
| `ix_quiz_questions_question_id` | quiz_questions (question_id) |
| `ix_attempts_quiz_id` | attempts (quiz_id) |
| `ix_attempt_answers_question_id` | attempt_answers (question_id) |
| `ix_assignments_course_id` | assignments (course_id) |
| `ix_assignments_lesson_id` | assignments (lesson_id) |
| `ix_submissions_user_id` | submissions (user_id) |
| `ix_learner_skill_masteries_skill_id` | learner_skill_masteries (skill_id) |
| `ix_path_recommendations_enrollment_id` | path_recommendations (enrollment_id) |
| `ix_path_recommendations_lesson_id` | path_recommendations (lesson_id) |
| `ix_discussion_posts_lesson_id` | discussion_posts (lesson_id) |
| `ix_discussion_posts_author_id` | discussion_posts (author_id) |
| `ix_discussion_posts_parent_post_id` | discussion_posts (parent_post_id) |
| `ix_notifications_user_id` | notifications (user_id) |
| `ix_content_reports_reporter_id` | content_reports (reporter_id) |
| `ix_content_reports_discussion_post_id` | content_reports (discussion_post_id) |
| `ix_content_reports_lesson_id` | content_reports (lesson_id) |
| `ix_audit_logs_actor_id` | audit_logs (actor_id) |

### Database Versus Application Integrity

The database enforces approved PK/FK existence, uniqueness, nonblank scalar text,
state values, positive ordering/time limits, array shape/key cardinality, material
variants, score ranges, and row-local lifecycle checks. Skill prerequisites have
course-qualified composite FKs, duplicate-edge prevention, and self-edge rejection.

The application must enforce full DAG acyclicity with concurrency protection;
LessonSkill/QuizQuestion/assignment/progress/resume/recommendation course consistency;
enrollment/role eligibility; same-Lesson root replies; quiz membership and selected
mask compatibility; deadline/expiry behavior; grading calculations; used-definition
and history immutability; configuration validation; and authorization/storage use.
Independent FKs do not establish all of those cross-aggregate rules.

Mastery and related LessonProgress updates remain one application database
transaction through approved module contracts. No linking FK or business trigger
replaces that orchestration. Initial Java entities/repositories and feature tests
remain unimplemented; DDL does not prove their behavior.

## 12. Deferred Feature and Future Schema Work

The approved initial physical decisions above are no longer deferred. Later
changes require new Flyway migrations after this baseline; an applied or possibly
applied migration must never be edited.

Still deferred: public APIs/DTOs, password-reset persistence/security workflow,
additional profiles/storage metadata/submission history, numeric mastery if an
approved algorithm needs it, adaptive configuration keys/thresholds/routing and
evidence aggregation, module interfaces/events/transaction execution, and proven
future indexes. These require their feature contracts before implementation.
Seed/demo content remains separate under `sample-data/`; no versioned migration
in this foundation supplies application records.

## 13. References

- [Canonical approved architecture](references/originals/approved-architecture.docx)
  and [architecture Markdown](architecture.md), especially sections 6–9:
  conceptual entities, relationships, adaptive integration, and storage choices.
- [Canonical course requirements](references/originals/project-requirements.pdf)
  and [requirements Markdown](requirements.md), especially sections 3–6:
  user roles, course/progress/assessment/interaction responsibilities, the
  mastery-based advanced direction, and quality requirements.
- [Modular-monolith boundary ADR](decisions/001-modular-monolith-module-boundaries.md):
  ownership, encapsulation, approved interactions, and transactional consistency.
- [Repository structure](repository-structure.md): approved documentation,
  backend, migration, seed, and material locations.
- [CONTRIBUTING database/migration rules](../CONTRIBUTING.md#database-and-migration-guidelines)
  and [storage rules](../CONTRIBUTING.md#object-storage-guidelines): persistence
  consistency, versioned evolution, file references, and authorization.
- [AGENTS.md](../AGENTS.md): source precedence, architectural invariants, and
  handling of undefined decisions.

This document preserves the conceptual model and records the separately approved
initial physical baseline under that source hierarchy. It does not amend
governance or immutable reference artifacts.
