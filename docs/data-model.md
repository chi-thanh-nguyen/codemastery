# CodeMastery Conceptual Data Model

## 1. Purpose and Scope

This document records the conceptual data model and data responsibilities of the
currently approved CodeMastery architecture. Its entity and relationship baseline
comes from section 9 of the
[canonical approved architecture](references/originals/approved-architecture.docx),
read together with the project requirements and module-boundary rules.

The approved concepts describe the information the system must represent. The
implementation constraints describe how persistence and storage must be governed.
Neither establishes that a physical schema or persistence behavior is implemented.

This document does not by itself define exact SQL DDL, additional column names,
SQL data types, UUID versus numeric identifiers, indexes, foreign-key names,
cascade/delete behavior, database-generated defaults, detailed normalization,
or migration contents. Nullability and other physical constraints remain
undefined unless explicitly approved. Entity names and conceptual cardinalities
do not automatically establish table names, columns, keys, or SQL constraints.

Deferred physical choices require approval before their dependent schema,
mapping, or persistence implementation begins.

## 2. Data Storage Responsibilities

| Storage | Approved responsibility |
|---|---|
| PostgreSQL | Relational domain data, including skills and prerequisite relationships, learning progress, assessment information, mastery, recommendations, interaction data, and administrative audit information. |
| MinIO through its S3-compatible interface | Approved file-based content such as slides, attachments, and assignment submissions. |
| External video hosts | Video learning material accessed through external embed links. Project-hosted video and transcoding are outside scope. |

PostgreSQL represents the metadata/references needed to associate stored files
with domain records. File content belongs in object storage. Bucket layouts,
object-key formats, storage tables, and metadata columns are not defined here.
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
active/locked; their persistence representation remains deferred.

The architecture explicitly describes the course's `adaptive_settings` as JSON
configuration for mastery thresholds. That conceptual configuration choice is
already approved. Its keys, threshold values, SQL storage type, defaults, and
validation details are not defined by this document.

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
  `Course/Lesson`. This document preserves that description without choosing
  whether or how both associations are physically represented. A `Submission`
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

Scoring columns, grading scales, attempt limits, answer schemas, and question
payload representations are not defined here.

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

This document does not define moderation-status sets, notification payloads,
audit-action enums, or physical audit/timestamp conventions.

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

These policies do not supply physical-schema details. This document specifies no
SQL, migration contents, or migration versions. Follow the
[database and migration guidelines](../CONTRIBUTING.md#database-and-migration-guidelines)
and the [approved repository structure](repository-structure.md).

## 11. Deferred Physical-Schema Decisions

The following decisions are **DEFERRED** until approved for their dependent
implementation:

- Identifier strategy, including UUID versus numeric identifiers and generation.
- Exact physical table/column names where not already approved; conceptual
  entity and association names do not settle these choices.
- SQL data types, lengths, precision, and the storage type for approved JSON
  configuration.
- Nullability, required participation, unique constraints, and indexes.
- Foreign-key names and enforcement, cascade/delete policies, and how the
  conceptual Course/Lesson assignment association is represented.
- Timestamp/audit column conventions and database-generated defaults.
- Persistence representations for roles, statuses, recommendation types, and
  other conceptual classifications.
- JSON versus normalized representation where the architecture has not already
  selected a representation; the course's `adaptive_settings` JSON configuration
  remains an approved conceptual fact.
- Physical representations of associations and other detailed normalization
  choices.
- Object-storage metadata/reference representation and associated columns.
- Exact migration contents implementing the approved physical decisions.

Approved cardinalities, course-scoped acyclic skills, storage responsibilities,
and atomic mastery/progress updates remain binding while these physical choices
are deferred. Concrete JSON keys, mastery thresholds, scoring rules, API DTOs,
and integration payloads also require their appropriate approved contracts;
this conceptual document does not define them.

If a dependent task requires an undefined choice, follow the
[AGENTS.md Blocker Protocol](../AGENTS.md#blocker-protocol) before implementation.
Existing scaffold filenames do not supply the missing specification.

## 12. References

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

This document records the approved conceptual model under that source hierarchy.
It does not amend governance or immutable reference artifacts.
