# ADR012: Batch 7 Interaction, Notification, Administration and Reporting Contracts

## 1. Authority, baseline and provenance

**DELEGATED_TECHNICAL_APPROVAL; DELEGATED_APPROVED**, 15 November 2026
(Asia/Ho_Chi_Minh). Project Leader @chi-thanh-nguyen explicitly delegated the
investigation, selection and technical approval of every eligible Batch 7 and
Batch 8 checkpoint, the strictly necessary compatible prerequisite slices and
other eligible residual technical contracts to Claude Code in **“CodeMastery —
Final Contract Approval Campaign: Batch 7, Batch 8 & Remaining Technical
Contracts”** (15 November 2026). The instruction excludes merge, protection
bypass, deployment, spending, credential access, residual-risk acceptance and
changes to the approved architecture, and reserves amendments of effective
Leader-reserved contracts to the Leader. This record needs no such amendment. It is
delegated technical approval, not personal Leader approval, module-owner review or a
GitHub approving review. Independent review, required CI and protected integration
of this record remain.

The same instruction confirms that the members responsible for eligible Issues may
begin implementation **once all applicable start prerequisites are actually
satisfied** (as ADR010 §1 did for Batches 5–6). That confirmation changes no
contract, waives no gate and authorizes no merge, deployment or risk acceptance.
Section 17 lists the start prerequisites that remain.

**Baseline.** Starting `origin/main` is
**30f9a6c16443a4a0ad5eee93075df29d69adfc3b**. [PR #235](https://github.com/chi-thanh-nguyen/codemastery/pull/235)
merged ADR010 as `c9d1427` and [PR #236](https://github.com/chi-thanh-nguyen/codemastery/pull/236)
merged ADR011 as `c80fda9` on 13 November 2026; neither PR has an approving GitHub
review (re-checked 15 November 2026). ADR001–ADR011 are integrated documents; their
recorded module-owner reviews and risk acceptances remain outstanding where they say
so. Open PRs #240/#241 (E2E lifecycle and SH07 workflow) touch no file of this record.

**Planning sources** under `~/codemastery-backlog-publication/contract-checkpoint-batching/`:
`run-20261108-135024/BATCH-07.md` and `APPROVAL-CHECKPOINT-REGISTER.md`, corrected by
`verification-20261108-151823/APPROVAL-ROADMAP.md`, `DEPENDENCY-TRACEABILITY.md`,
`registers/dependency-traceability.csv` and `registers/revised-plan.json`, resolved
through `local_ids`. No newer batching run exists. Historical **CR-3/CR-4 are not
Batch 7**. Verified membership: **CP-064–CP-090, twenty-seven checkpoints, ten
Issues**; nothing is rebucketed. The verification's review groups 7A–7F were used as
review order only.

**Leader register** (`~/codemastery-handoffs/current/leader-decisions.md`):
**LD-07** (owner of the reminder deadline query) is decided here as its recommended
option (a), producer-owned (§3.3); **LD-12(e)** (learning-outcome basis) and
**LD-12(f)** (CT28 event choice) are decided with their checkpoints (§§13, 9.3), as
LD-12 directs. **LD-02** (all notification triggers or a recorded scope-down) is
**not** decided: contracts are approved for every architecture-listed trigger, so
either answer stays possible; a scope-down would leave an approved contract
unimplemented, not invalid. *(Superseded by §19.4 on 15 November 2026: LD-02 is resolved as option (a).)* LD-09 (review route) remains an integration question.

Inspected: root CLAUDE/AGENTS, the context guide, CONTRIBUTING, CODEOWNERS, the PR
template; requirements §§4.5–4.6, 6, 8.4; architecture §§4.5–4.6, 7, 9, US-08/09/13/15/16/17;
repository structure (Interaction, Admin, event and scheduler files); ADR001 §§5–12/15–16;
ADR002 CT18.minimum/CT38.core; ADR005 §§3/6/13; ADR006 §§3/5; ADR007 §§3/11/12;
ADR008 §§5/6; ADR010 §§3/8–10/14; ADR011 §§3–9/15/17/19–21; data-model §§8/11/12 and
the CT11 field record; deployment CT17-P6 matrix; V001–V007; the Interaction and Admin
representations merged in `ea9c960` and `30f9a6c`; the empty Interaction/Admin service,
listener, scheduler and repository scaffolds; live Contract Issues and all 157
Project #3 items. Known AI use: Claude Code (Anthropic) analysed sources and authored
this record; no AI runtime is added to the application.

## 2. Inventory, dependency order and dispositions

All twenty-seven checkpoints are **DELEGATED_APPROVED** in exactly the scopes below.
Approve §3's prerequisite slices first, then the table order.

| CP | Checkpoint / Issue | Revision | Section | Satisfied prerequisites |
|---|---|---|---|---|
| CP-064 | CT11.create / [#137](https://github.com/chi-thanh-nguyen/codemastery/issues/137) | `CT11-CREATE-2026-11-15-r1` | §4.1 | CT11-READ-FIELDS r1 |
| CP-065 | CT11.read / #137 | `CT11-READ-2026-11-15-r1` | §4.2 | CT11-READ-FIELDS r1 (field set retained) |
| CP-066 | CT30.access / [#146](https://github.com/chi-thanh-nguyen/codemastery/issues/146) | `CT30-ACCESS-2026-11-15-r1` | §6.1 | CT03-2026-10-21-r1 U04 |
| CP-067 | CT31.actions / [#147](https://github.com/chi-thanh-nguyen/codemastery/issues/147) | `CT31-ACTIONS-2026-11-15-r1` | §7.1 | V006 `audit_logs` |
| CP-068 | CT31.consistency / #147 | `CT31-CONSISTENCY-2026-11-15-r1` | §7.2 | ADR005 Course-root guard |
| CP-069 | CT27.event / [#143](https://github.com/chi-thanh-nguyen/codemastery/issues/143) | `CT27-EVENT-2026-11-15-r1` | §9.2 | CP-041 CT10.grading r1 (ADR010, on `main`) |
| CP-070 | CT30.posts / #146 | `CT30-POSTS-2026-11-15-r1` | §6.2 | CP-066 |
| CP-071 | CT30.reports / #146 | `CT30-REPORTS-2026-11-15-r1` | §6.3 | CP-066 |
| CP-072 | CT31.read / #147 | `CT31-READ-2026-11-15-r1` | §7.3 | CP-067; §3.4 |
| CP-073 | CT12.user / [#138](https://github.com/chi-thanh-nguyen/codemastery/issues/138) | `CT12-USER-2026-11-15-r1` | §10.1 | CP-067, CP-068; CT01 r1 |
| CP-074 | CT13.metrics / [#139](https://github.com/chi-thanh-nguyen/codemastery/issues/139) | `CT13-METRICS-2026-11-15-r1` | §13.1 | CP-011 CT22.progress r1 |
| CP-075 | CT26.event / [#142](https://github.com/chi-thanh-nguyen/codemastery/issues/142) | `CT26-EVENT-2026-11-15-r1` | §9.1 | CP-015 CT21.authoring r1 |
| CP-076 | CT27.subscription / #143 | `CT27-SUBSCRIPTION-2026-11-15-r1` | §9.2 | CP-069, CP-064 |
| CP-077 | CT30.notify / #146 | `CT30-NOTIFY-2026-11-15-r1` | §6.4 | CP-070, CP-064 |
| CP-078 | CT12.course / #138 | `CT12-COURSE-2026-11-15-r1` | §10.2 | CP-067, CP-068; CP-015 (applies: reuses publication semantics) |
| CP-079 | CT32.queue / [#148](https://github.com/chi-thanh-nguyen/codemastery/issues/148) | `CT32-QUEUE-2026-11-15-r1` | §11.1 | CP-071 |
| CP-080 | CT26.subscription / #142 | `CT26-SUBSCRIPTION-2026-11-15-r1` | §9.1 | CP-075, CP-064 |
| CP-081 | CT29.event / [#145](https://github.com/chi-thanh-nguyen/codemastery/issues/145) | `CT29-EVENT-2026-11-15-r1` | §9.4 | CP-054 CT07.reasons r1 (ADR011, on `main`) |
| CP-082 | CT30.rest / #146 | `CT30-REST-2026-11-15-r1` | §6.5 | CP-070, CP-071, CP-077 |
| CP-083 | CT12.rest / #138 | `CT12-REST-2026-11-15-r1` | §10.3 | CP-073, CP-078 |
| CP-084 | CT32.hide / #148 | `CT32-HIDE-2026-11-15-r1` | §11.2 | CP-079, CP-067 |
| CP-085 | CT29.subscription / #145 | `CT29-SUBSCRIPTION-2026-11-15-r1` | §9.4 | CP-081, CP-064 |
| CP-086 | CT32.rest / #148 | `CT32-REST-2026-11-15-r1` | §11.3 | CP-084 |
| CP-087 | CT11.reminders / #137 | `CT11-REMINDERS-2026-11-15-r1` | §5 | CP-064, CP-040 CT10.deadline r1; §§3.2, 3.3, 3.5 |
| CP-088 | CT28.subscription / [#144](https://github.com/chi-thanh-nguyen/codemastery/issues/144) | `CT28-SUBSCRIPTION-2026-11-15-r1` | §9.3 | CP-064; CP-027 CT08.evidence r1 (applies, option A) |
| CP-089 | CT13.reads / #139 | `CT13-READS-2026-11-15-r1` | §13.2 | CP-074; CP-026 and CP-006 (both apply) |
| CP-090 | CT13.rest / #139 | `CT13-REST-2026-11-15-r1` | §13.3 | CP-089 |

These cover all forty relationships into Batch 7 in the verified revised plan
(33 HARD, 7 CONDITIONAL): the HARD sources outside the batch (CP-011, CP-015,
CP-040, CP-041, CP-054 in Batches 2, 5 and 6) are effective on `main`, the HARD
edges inside the batch follow the table order, and the conditional edges apply as
marked. The VERIFY-C1 edge GOV.HC-U20 → CP-066 no longer applies because LD-01
ruled the consumed CT03 record effective (ADR004). CP-025 → CP-087 does **not** apply:
Quizzes have no calendar deadline (V004 has only a per-attempt time limit), so
quiz windows are not reminded (LD-07 option c is declined). CP-026 → CP-088 does
not apply because option A is selected. No HARD predecessor is pending.

## 3. Necessary prerequisite slices

Each slice is the smallest addition the named checkpoint needs; no existing
approved method changes. Callers are allowlisted, and tests must prove the
allowlist. All are read-only unless stated, participate in the caller's
transaction when one is active and otherwise run read-only at READ COMMITTED.

### 3.1 CT40.notification-context

**`CT40-NOTIFICATION-CONTEXT-2026-11-15-r1` — DELEGATED_APPROVED**, [CT40 #46](https://github.com/chi-thanh-nguyen/codemastery/issues/46).
Interaction messages and the Admin moderation queue name Courses and Lessons, and
no approved read returns titles without an actor. Add to existing
`modules/course/application/service/CourseCatalogueService.java`:

```java
public Optional<CourseContext> readCourseContext(UUID courseId, Set<UUID> lessonIds);
public Optional<LessonContext> readLessonContext(UUID lessonId);
public record CourseContext(UUID courseId, String courseTitle, String coursePublicationStatus,
        Map<UUID, LessonName> lessons) {}
public record LessonName(UUID lessonId, String title, String publicationStatus) {}
public record LessonContext(UUID courseId, String courseTitle, String coursePublicationStatus,
        UUID lessonId, String lessonTitle, String lessonPublicationStatus) {}
```

Actorless; empty when the Course or Lesson does not exist; `lessons` holds only the
requested Lessons that actually belong to that Course (others are omitted); at most
100 Lesson IDs per call (`IllegalArgumentException` above); callers needing more split
the set into calls of at most 100. Publication values are
the V002 literals. No membership, owner, body, material or progress data. Callers:
Interaction `NotificationEventListener`, `DeadlineReminderScheduler`,
`DiscussionService` and `ContentReportService`, and Admin `ContentModerationService`.
Each caller has either admitted its actor first or is an approved system path.

### 3.2 CT22.active-learners

**`CT22-ACTIVE-LEARNERS-2026-11-15-r1` — DELEGATED_APPROVED**, [CT22 #43](https://github.com/chi-thanh-nguyen/codemastery/issues/43).
Add to existing `modules/course/application/service/EnrollmentService.java`:

```java
public Set<UUID> joinedLearnerIds(UUID courseId);
```

Immutable set of learners whose Enrollment status is `active` or `completed`
(the CT03 learning-access population); empty for a missing Course. Actorless. Only
caller: Interaction `DeadlineReminderScheduler` (§5).

### 3.3 CT10.reminder-read — LD-07 option (a)

**`CT10-REMINDER-READ-2026-11-15-r1` — DELEGATED_APPROVED**, [CT10 #136](https://github.com/chi-thanh-nguyen/codemastery/issues/136).
Assessment owns deadlines (ADR001 producer ownership; ADR010 §8 left this choice to
CT11.reminders). Add to existing
`modules/assessment/application/service/AssignmentService.java`:

```java
public List<ReminderCandidate> findReminderCandidates(Instant now, Instant windowEnd,
        ReminderCursor after, int limit);
public record ReminderCandidate(UUID assignmentId, UUID courseId, UUID lessonId,
        String assignmentTitle, Instant deadlineAt, Set<UUID> submittedLearnerIds) {}
public record ReminderCursor(Instant deadlineAt, UUID assignmentId) {}
```

Returns **published** Assignments with `now < deadline_at <= windowEnd` (CT10.deadline's
exclusive end: an Assignment whose deadline equals `now` is already late), ordered by
(`deadline_at`, `id`) ascending, strictly after the nullable keyset cursor, at most
`limit` rows (1..2147483647). `submittedLearnerIds` holds every learner with a
Submission row of any state. `lessonId` is the Assignment's nullable bound Lesson.
Course and Lesson publication are not filtered here (§5 checks them).
Actorless. Only caller: `DeadlineReminderScheduler`. No REST exposure.

### 3.4 CT01.user-summaries

**`CT01-USER-SUMMARIES-2026-11-15-r1` — DELEGATED_APPROVED**, [CT01 #18](https://github.com/chi-thanh-nguyen/codemastery/issues/18).
Q&A authors may be Learners or Instructors and audit actors are Administrators;
`learnerDisplayNames` (ADR010 §3.1) is Course-only. Add to existing
`modules/auth/application/service/UserAccountService.java`, leaving both CT01.identity
methods and `learnerDisplayNames` unchanged:

```java
public Map<UUID, UserSummary> userSummaries(Set<UUID> userIds);
public record UserSummary(UUID userId, String displayName, String role) {}
```

Stored display name and current role literal for existing IDs; unknown IDs omitted;
at most 100 IDs per call (callers split larger sets, for example a discussion page
with many replies or a moderation page with reporters and authors, into chunks of at
most 100); no email, account status or credential data. Callers:
Interaction `DiscussionService` (authors of posts it returns) and Admin
`AuditLogService` and `ContentModerationService` (actors and reporters of rows they
return). Auth never calls Interaction or Admin.

### 3.5 CT38.feature.deadline-reminders

**`CT38-DEADLINE-REMINDERS-2026-11-15-r1` — DELEGATED_APPROVED**, [CT38 #21](https://github.com/chi-thanh-nguyen/codemastery/issues/21).
Backend host/container inputs only, bound in existing `common/config/AppConfig.java`
and `application.yml`, consumed by the existing Interaction scheduler, following the
CT38-QUIZ-EXPIRY r1 pattern:

| Environment input | Spring property | Validation |
|---|---|---|
| APP_DEADLINE_REMINDER_ENABLED | app.deadline-reminder.enabled | Required explicit boolean |
| APP_DEADLINE_REMINDER_DELAY_MS | app.deadline-reminder.delay-ms | Required integer 1..2147483647 milliseconds |
| APP_DEADLINE_REMINDER_LEAD_MINUTES | app.deadline-reminder.lead-minutes | Required integer 1..2147483647 minutes |
| APP_DEADLINE_REMINDER_BATCH_SIZE | app.deadline-reminder.batch-size | Required integer 1..2147483647 |

No production values or defaults are selected; the integer bounds are
representation limits, not approved values. Missing or invalid values fail startup
once the feature configuration is implemented, including when disabled; tests supply
explicitly named case values. Disabled suppresses reminders only. A deployed reminder
capability requires an operator-recorded lead time, delay and batch size. Secrets,
`VITE_*` variables and other CT38 slices are excluded; this record does not edit
runtime configuration or `.env.example`.

## 4. CT11 notification core

### 4.1 CT11.create

Interaction-internal creation operation in existing
`modules/interaction/application/service/NotificationService.java`:

```java
public int create(Collection<NewNotification> notifications);
public static UUID derivedId(String purpose, String... parts);
public record NewNotification(UUID id, UUID recipientId, String message) {}
```

- **Audience.** Only Interaction components call it: `NotificationEventListener`
  (§9), `DeadlineReminderScheduler` (§5) and `DiscussionService` (§6.4). Other modules
  never call it; they publish their approved events (ADR001 §6). No REST creation.
- **Transaction.** Requires an active read-write transaction owned by the Interaction
  caller (`IllegalStateException` otherwise). One call is all-or-nothing.
- **Identity and idempotence.** Every notification ID is derived, never random:
  `derivedId` returns `UUID.nameUUIDFromBytes` (MD5 name-based, version-3 bits, no
namespace UUID) of the UTF-8 string
  `codemastery/notification/{purpose}/{part1}/{part2}…`; instants are written with
  `Instant.toString()` of the microsecond-truncated value and UUIDs in lowercase.
  Rows are inserted with `INSERT … ON CONFLICT (id) DO NOTHING`, so a repeated
  request creates nothing; the return value is the number of new rows. Duplicate IDs
  inside one call are collapsed first. No schema change: the V006 primary key is the
  de-duplication key. Predictable IDs disclose nothing because every read and
  mark-read is owner-scoped (§4.2).
- **Row values.** `user_id` = recipient, `message` = the caller's fixed English
  template output, `is_read = false`, `created_at` = one instant per call from a
  `java.time.Clock` injected into `NotificationService` (UTC; fixed in tests; the
  ADR008 §6 local seam, no shared bean), truncated to microseconds.
- **Message rules.** Messages contain a non-whitespace character (V006 check), are
  plain text, name the Course, Lesson or Assignment by title in double quotes where
  useful, and contain no URL, ID, email, score, feedback or post content. Titles are
  inserted verbatim as stored. The frontend renders them as plain text
  (CT11-READ-FIELDS r1); no type, link or migration is added.
- **Recipients.** A missing recipient violates the foreign key and fails the call. A
  locked recipient still receives the row and sees it after unlocking.
- **Visibility precondition.** Every creator first reads §3.1's context in the same
  transaction and creates nothing (counted, logged at debug level) unless the Course
  is `published` and, when the message names a Lesson, that Lesson is `published`.
  A notification therefore never names content that is concealed **at creation
  time**; rows created earlier stay when content is hidden later (§11.2).

### 4.2 CT11.read

The CT11-READ-FIELDS r1 field set (identifier, message, read state, creation time)
is retained unchanged. Operations, all for any authenticated active account and
only on the caller's own rows (`NotificationController` → `NotificationService`):

| Operation | Behavior |
|---|---|
| `GET /api/me/notifications?unreadOnly=&page=&size=` | Page of own notifications, newest first (`created_at` desc, then `id` desc). `unreadOnly` defaults to false. Existing paging 0/20, 1..100. |
| `GET /api/me/notifications/unread-count` | `{unreadCount}` of own unread rows. |
| `PUT /api/me/notifications/{notificationId}/read` | Sets `is_read = true` on an own row; idempotent; returns the row. Missing or foreign ID → 404 (indistinguishable). |
| `PUT /api/me/notifications/read-all` | Marks every own unread row read in one statement; returns `{markedCount}`. |

There is no mark-unread, deletion or retention policy (none is invented). Reads run
read-only at REPEATABLE READ; responses are `Cache-Control: no-store`. Mutations
need no body; a body is ignored. **Client refresh:** no polling interval, push,
WebSocket or server-sent events are approved (real-time channels are non-goals).
FE28 reloads the count on route change and the list when the bell or page opens.
Exact shapes are in OpenAPI (`B7Notification*`).

## 5. CT11.reminders

`DeadlineReminderScheduler` (existing file) runs with a fixed delay of
`app.deadline-reminder.delay-ms` when `app.deadline-reminder.enabled` is true (§3.5).
Fixed-delay runs do not overlap in one process; multiple instances are safe because
creation is idempotent (§4.1). Each run:

1. Samples `now` from the scheduler's injected UTC `Clock` (truncated to microseconds)
   and sets `windowEnd = now + lead-minutes`.
2. Pages through `findReminderCandidates(now, windowEnd, cursor, batch-size)` (§3.3)
   with the keyset cursor until a page is shorter than the batch size. Batch size
   bounds each query, not the run.
3. For each candidate, in its **own** read-write transaction: skips it unless
   `deadlineAt` is still later than a fresh `clock.instant()`; reads §3.1's context
   with the candidate's `lessonId` when present (skip unless the Course is published
   and, for a Lesson-bound Assignment, that Lesson is published, because CT03 U03
   conceals such an Assignment otherwise); reads §3.2's joined learners, takes
   recipients = joined learners minus `submittedLearnerIds`, and calls CT11.create
   with ID `derivedId("deadline-reminder", assignmentId, learnerId, deadlineAt)` and
   message `Reminder: the assignment "{assignmentTitle}" in "{courseTitle}" is due soon. Open the assignment to see its deadline.`
4. A failing candidate is logged (IDs only) and the run continues; the next run
   retries it and the derived ID prevents duplicates.

**Semantics.** One reminder per learner, Assignment and deadline value. Changing the
deadline produces a new reminder if the new deadline enters the window; a deadline
that already passed is never reminded (no catch-up after downtime). Learners who
submitted, left or are not members get none. The message carries no time, so no
time-zone conversion is needed; the Assignment page shows the deadline. A deadline
edited during a run is not re-read until the next run; the per-candidate re-check only
stops reminders for deadlines that passed during the run. Lessons,
Quizzes and attempt time limits are not reminded. Assessment exposes facts only;
Interaction owns the reminder policy.

## 6. CT30 lesson Q&A and reports

### 6.1 CT30.access

Every Q&A and report-intake operation addresses a Lesson by ID and first calls
CT03 `CourseAccessService.requireLessonReadAccess(lessonId)` (ADR001 §12.4–12.6, U04)
with the Lesson actually stored on the target: active/completed Learner members of
the published Course reading a published Lesson, or the owning Instructor
(including hidden-Lesson preview), pass; everyone else gets CT03's 401, 403
(unsupported role, including **Administrator**) or concealed 404. A supplied
`postId` or `parentPostId` must belong to that Lesson and be visible, else 404; it
never selects another Lesson. Administrators act on reports only through CT32.

### 6.2 CT30.posts

- A **question** has `parent_post_id = null`; a **reply** names a visible question on
  the same Lesson. Depth is exactly one: naming a reply as parent is 400 (field
  `parentPostId`). A hidden, missing or foreign parent is 404.
- `content` is required, must contain a non-whitespace character, is stored
  verbatim (not trimmed) and is displayed as plain text with line breaks preserved;
  no Markdown, HTML or auto-linking. No new length limit is invented (CT21
  precedent). `author_id` is the trusted actor, `created_at` comes from an injected
  `Clock` truncated to microseconds, `is_hidden = false`.
- Authors cannot edit or delete posts in Final; correction is a new reply. Removal
  is only moderation hiding (CT32). Hidden questions conceal all their replies.
- Reply creation reads the parent `FOR SHARE`; moderation hiding locks it
  `FOR UPDATE`, so a reply never lands on a question hidden by a committed decision.
- **Read model.** Questions newest first (`created_at` desc, `id` desc), paged with
  existing defaults; each embeds all its visible replies oldest first (`created_at`,
  `id` asc). Each post shows `authorDisplayName` and `authorRole` from §3.4 (null if
  the user is missing; the role is mapped to the API vocabulary `Learner`,
  `Instructor`, `Administrator` as in §10.1), `mine`, content and time. Hidden posts never appear in any
  Q&A read, for any role.

### 6.3 CT30.reports

A report targets either one visible post of the addressed Lesson or the Lesson
itself (V006 exclusive target). `reason` is required, non-whitespace, stored verbatim.
`status = open`, `created_at` from the injected `Clock`, `resolved_at = null`.
Duplicate reports by the same or other users are allowed (no unique constraint is
added); CT32 resolves them together. The response is a receipt (id, status,
createdAt); reporters have no report-history read in Final. Reports never hide
anything by themselves.

### 6.4 CT30.notify

Inside the reply's own transaction, after the reply insert, `DiscussionService`
creates one notification for the question's author when that author is not the
replier, with ID `derivedId("discussion-reply", replyId)` and message
`New reply to your question in "{lessonTitle}".` (§3.1 context; skipped unless the
Course and Lesson are published, §4.1). Interaction owns both rows, so they commit
together; a database failure fails the reply. No notification goes to other
repliers, Instructors or for new questions.

### 6.5 CT30.rest

| Operation | Result |
|---|---|
| `GET /api/lessons/{lessonId}/discussion?page=&size=` | `B7DiscussionPage` |
| `POST /api/lessons/{lessonId}/discussion` | 201 `B7DiscussionPost` (question or reply) |
| `POST /api/lessons/{lessonId}/reports` | 201 `B7ContentReportReceipt` (Lesson target) |
| `POST /api/lessons/{lessonId}/discussion/{postId}/reports` | 201 `B7ContentReportReceipt` (post target) |

JSON only (415 otherwise), strict closed DTOs, `Cache-Control: no-store`. Writes run
at READ COMMITTED; reads read-only at REPEATABLE READ. Bindings: existing
`DiscussionController`, `ContentReportController`, `InteractionRequests`,
`InteractionResponses`, `DiscussionService`, `ContentReportService`.

## 7. CT31 audit

### 7.1 CT31.actions

Closed catalogue, persisted as `audit_logs.action` codes and mapped by the Admin
domain enum `AuditAction` (existing file):

| Action code | `target_type` | `target_reference` | Produced by |
|---|---|---|---|
| `user_role_set_learner`, `user_role_set_instructor`, `user_role_set_admin` | `user` | user UUID | CT12.user role change |
| `user_locked`, `user_unlocked` | `user` | user UUID | CT12.user status change |
| `course_hidden` | `course` | Course UUID | CT12.course |
| `report_post_hidden`, `report_lesson_hidden` | `content_report` | report UUID | CT32.hide (one row per resolved report) |
| `report_dismissed` | `content_report` | report UUID | CT32 dismissal |

`actor_id` is the authenticated Administrator; `target_reference` is lowercase
canonical UUID text; `occurred_at` comes from a `Clock` injected into
`AuditLogService` (UTC, microseconds). Only **actual changes** are audited: a no-op
request (same role, same status, already hidden) writes nothing. Reads, denied or
failed attempts, Instructor/Learner actions and system jobs are not audited. Rows
are append-only: no update or delete operation exists, and the CT17-P6 matrix
already prohibits UPDATE/DELETE on `audit_logs` permanently. The role code records
the new role; the old role is derivable from earlier rows. No detail column or
migration is added.

### 7.2 CT31.consistency

**Same transaction.** Every audited action runs in one read-write READ COMMITTED
transaction started by the Admin application service. It calls the owning module's
public mutation (which participates; owning methods must not start `REQUIRES_NEW`)
and then inserts its audit row(s) through `AuditLogService`. Any failure rolls back
both; there is no action-only or audit-only outcome, asynchronous audit, retry or
outbox. Lock order: Course root first for Course changes (ADR005 §13.3: nothing is
locked or written before the root), then the changed content row (Lesson or post),
then **all** open report rows of that target in one `ORDER BY id FOR UPDATE`
statement, then the audit insert. User changes lock Auth rows (§10.1) before the
audit insert. Two decisions on the same target therefore serialize on the content row
and cannot deadlock on report rows.

### 7.3 CT31.read

`GET /api/admin/audit-logs` (Administrator only) with optional AND filters `actorId`
(UUID), `action` (catalogue code), `targetType` (`user`, `course`, `content_report`),
`targetReference` (UUID text), `occurredFrom` (inclusive) and `occurredTo`
(exclusive; RFC 3339, at most microsecond precision, `occurredFrom < occurredTo`
when both are given), plus existing paging. Order `occurred_at` desc, then `id` desc.
Rows carry `actorDisplayName` from §3.4. Invalid filter values are 400. Read-only
REPEATABLE READ; no export. Bindings: existing `AdminReportingController`,
`ReportingResponses`, `AuditLogService`, `AuditLogRepository`.

## 8. Event execution rules (shared by §9)

**Approved mechanism: after-commit subscription.** Every Batch 7 notification
subscription is a method of Interaction's existing `NotificationEventListener`
annotated `@TransactionalEventListener(phase = AFTER_COMMIT)` (default
`fallbackExecution = false`) and `@Transactional(propagation = REQUIRES_NEW)`:

- It runs only after the producer's transaction **committed**, so a rolled-back
  publication, grade, submission or recommendation never notifies.
- It writes in its own transaction; its failure, which Spring reports from the
  completion callback without rethrowing, never rolls back or fails the producer.
  The listener additionally catches and logs failures (event type and IDs only).
- Delivery is at most once: no retry, outbox, broker, queue or replay is added; a
  crash between the producer commit and the listener can lose a notification. The
  derived IDs make an accidental second delivery harmless.
- Producers publish only inside an active transaction, with
  `ApplicationEventPublisher.publishEvent`, after their rows are flushed. Publishing
  must not throw or change the producer's outcome. **No plain `@EventListener`** may
  subscribe to these events: it would join the producer transaction.
- The listener needs a second pooled connection while the producer's is still held.
  If every pooled connection is held by a producer waiting in its listener, each
  listener waits for the pool's acquisition timeout, the producer's response is
  delayed by that timeout and the notification is lost (logged). Producers still
  succeed, so ADR011 §4 holds, but latency depends on the subscriber. Qualification
  item: the maximum pool size must exceed the maximum number of concurrent producer
  threads (request and scheduler threads), recorded with the runtime inputs; no value
  is selected here.

## 9. Notification events and subscriptions

### 9.1 CT26 — published content

**CT26.event.** Existing `modules/course/application/event/ContentPublishedEvent.java`:

```java
public record ContentPublishedEvent(UUID courseId, UUID lessonId, Instant publishedAt,
        List<UUID> recipientLearnerIds) {}
```

Published by CT21.authoring's Lesson publication operation inside its guarded
transaction, after the Lesson update and CT22's completion reconciliation are
flushed, **only** on an actual `hidden → published` transition of a Lesson whose
Course is `published` at that moment. `publishedAt` is that operation's injected
`Clock` instant (microseconds; not persisted). `recipientLearnerIds` are the joined
(`active`/`completed`) learners after reconciliation, ascending and distinct; when
empty, no event is published. Idempotent PUTs, Course publication, material,
Chapter, reorder and content edits publish nothing. Administrator hiding never
publishes.

**CT26.subscription.** One notification per recipient, ID
`derivedId("content-published", lessonId, publishedAt, recipientId)`, message
`New lesson in "{courseTitle}": "{lessonTitle}".`, under §4.1's visibility check.
A notification failure never rolls back publication (§8).

### 9.2 CT27 — graded assignment

**CT27.event.** Existing `modules/assessment/application/event/AssignmentGradedEvent.java`:

```java
public record AssignmentGradedEvent(UUID submissionId, UUID assignmentId, UUID courseId,
        UUID lessonId, UUID learnerId, String assignmentTitle, boolean assignmentPublished,
        boolean regrade, Instant gradedAt) {}
```

Published by CT10.grading's grade operation inside its locked transaction after the
grade, feedback and `graded_at` are written and flushed, for every successful save
(`regrade = true` when a grade already existed). No grade, feedback or file data is
carried. It is not `QuizSubmittedEvent` and never reaches Adaptive (ADR010 §9).

**CT27.subscription.** Recipient: the submission's learner, only when
`assignmentPublished` is true, the Course is published and, when `lessonId` (the
nullable bound Lesson) is present, that Lesson is published (§3.1). ID
`derivedId("assignment-graded", submissionId, gradedAt)`. Messages:
`Your submission for "{assignmentTitle}" in "{courseTitle}" has been graded.` or,
for a regrade, `The grade for your submission to "{assignmentTitle}" in "{courseTitle}" was updated.`
A notification failure never rolls back grading.

### 9.3 CT28 — quiz result (LD-12(f): reuse `QuizSubmittedEvent`)

**Option A selected:** Interaction subscribes to the existing ADR007 §12
`QuizSubmittedEvent`; no new result event or file is introduced (option B would add
an Assessment file outside the approved structure and duplicate the payload). This
is the subscriber contract that ADR011 §4 requires: phase **after commit**, its own
transaction, failure isolated; it adds no effect that can commit when the submission
unit rolls back and makes no submission depend on it. The approved subscriber set of
`QuizSubmittedEvent` is therefore exactly {Adaptive `QuizSubmittedEventListener`
(plain, in-transaction, ADR011), Interaction `NotificationEventListener` (after
commit)}; B6-V04 asserts this two-member set. ADR011's single in-transaction
subscriber rule is unchanged.

Recipient: `learnerId`, for every qualifying event (manual and automatic
submissions alike), when the Course is published. ID
`derivedId("quiz-result", attemptId)`. Message
`Your {kind} result in "{courseTitle}" is ready (attempt {attemptNumber}).`, where
`{kind}` is `placement quiz`, `practice quiz` or `test` for `diagnostic`, `practice`
or `test`. No score or answer data. A notification failure never rolls back the
attempt.

### 9.4 CT29 — recommendation created

**CT29.event.** Existing `modules/adaptive/application/event/RecommendationCreatedEvent.java`:

```java
public record RecommendationCreatedEvent(UUID courseId, UUID learnerId, UUID enrollmentId,
        Instant recommendedAt, List<CreatedRecommendation> recommendations) {
    public record CreatedRecommendation(UUID recommendationId, String type,
            UUID targetLessonId, String reason) {}
}
```

Published by `AdaptiveDecisionService` once per processing run that inserted at
least one `path_recommendations` row (ADR011 §4 step 7), after the inserts are
flushed, inside the submission unit; `recommendations` lists exactly the rows
inserted by that run (V005 `type` literals, CT07.reasons text verbatim), and
`recommendedAt` is the run's instant (ADR011 §9). Runs that insert nothing publish
nothing. This completes ADR011 §4's “CT29 decides any later recommendation event”
without changing its unit: publishing registers an after-commit callback only.

**CT29.subscription — not every recommendation notifies.** Per event, at most two
notifications, each under §4.1's visibility check:

- for an inserted `remedial` row: ID `derivedId("recommendation", recommendationId)`,
  message `New review recommendation in "{courseTitle}": "{lessonTitle}". {reason}`
  (target Lesson must be published);
- for inserted `skip` rows: ID `derivedId("test-out", lowestSkipRecommendationId)`
  (the lowest UUID among the run's inserted `skip` rows),
  message `You tested out of the lesson "{lessonTitle}" in "{courseTitle}".` for one
  row, or `You tested out of {count} lessons in "{courseTitle}".` for several;
- `continue` rows never notify: routine canonical progression is already shown on
  guidance and Learner Home (ADR011 §15).

A notification failure never rolls back the recommendation or the quiz unit.

## 10. CT12 Administrator mutations

### 10.1 CT12.user

Auth exposes Administrator-only operations in existing `UserAccountService.java`;
the only caller is Admin `UserManagementService`:

```java
public PageResponse<AdminUserView> searchUsers(String query, String role,
        String accountStatus, int page, int size);
public UserChange changeRole(UUID userId, String newRole);
public UserChange changeAccountStatus(UUID userId, String newStatus);
public record AdminUserView(UUID userId, String email, String displayName, String role,
        String accountStatus) {}
public record UserChange(AdminUserView user, boolean changed) {}
```

- **Authorization.** Each method first requires the current identity to be an
  active `admin` through CT01.identity; Admin's controller and service check it too.
- **Search.** `query` is optional and trimmed; a nonblank value matches a
  case-insensitive substring of the email or display name, with `%`, `_` and `\`
  escaped. `role` and `accountStatus` are optional exact V001 literals. Order email
  `COLLATE "C"`, then `id`; existing paging. No password hash or reset data appears.
- **Role change.** Any of `learner`, `instructor`, `admin` (Auth literals). REST uses
  the existing API role vocabulary of `PublicUser` (ADR001 §11.3): `Learner`,
  `Instructor`, `Administrator`, mapped one to one; storage literals stay internal and
  in audit codes. **Status change.** `active` or `locked`. Same value → `changed = false`, nothing audited.
- **Protections.** An Administrator cannot change their own role or status (409).
  Under concurrency, each mutation locks every `admin` user row `FOR UPDATE` in
  `id` order, then the target row, re-checks that the actor is still an active
  Administrator (else 403) and rejects any result leaving no active Administrator
  (409). Two Administrators demoting each other therefore serialize safely.
- **Effects.** No cascade: Courses keep their `instructor_id`, Enrollments,
  submissions, posts and history stay. CT01 re-validates account state and role on
  every request (ADR001 §11.3), so a lock or role change takes effect on the user's
  next request; an issued token cannot continue past it. Locked accounts never
  authenticate (CT01). There is no user creation, deletion, email or password edit
  by Administrators.

### 10.2 CT12.course

Course exposes Administrator-only operations in existing
`CourseManagementService.java`; the only caller is Admin `AdminCourseService`
(CT32 also uses the Lesson method, §11.2):

```java
public PageResponse<AdminCourseView> listCoursesForAdministration(String query,
        String publicationStatus, int page, int size);
public CourseChange hideCourseAsAdministrator(UUID courseId);
public LessonChange hideLessonAsAdministrator(UUID lessonId);
public record AdminCourseView(UUID courseId, String title, String publicationStatus,
        UUID instructorId, String instructorDisplayName) {}
public record CourseChange(AdminCourseView course, boolean changed) {}
public record LessonChange(UUID courseId, UUID lessonId, String publicationStatus,
        boolean changed) {}
```

- Each method requires an active `admin` through CT01.identity. The list includes
  hidden and published Courses; `query` matches a case-insensitive title substring;
  order title `COLLATE "C"`, then `id`; names come from the existing CT01.identity
  `instructorDisplayNames`.
- **Hiding** has exactly the effects of CT21.authoring's `hidden` publication change
  (no cascade; CT22 reconciliation for a Lesson). It is **not** run through the
  Instructor-only `withCourseConfigurationWrite` callback, whose audience is not
  widened: Course takes the same Course-root row lock first through its private
  persistence, then re-reads state, as CT22 learner writes do. It is a root-lock
  participant bound by ADR005 §13.3 (READ COMMITTED read-write check, root first,
  re-read after the lock, no earlier lock or write), so it serializes with readiness,
  coverage, authoring and progress writers of that Course. Already hidden →
  `changed = false`.
- **Selected Administrator Course actions: list and hide only.** No publish,
  unhide, edit, delete, ownership transfer or content change. The owning Instructor
  may republish through CT21; without a schema change there is no takedown lock, so
  repeated republication is handled by user management. This limitation is recorded,
  not accepted as a security risk.

### 10.3 CT12.rest

| Operation | Result |
|---|---|
| `GET /api/admin/users?query=&role=&accountStatus=&page=&size=` | `B7AdminUserPage` |
| `PUT /api/admin/users/{userId}/role` `{role}` | `B7AdminUserChange` |
| `PUT /api/admin/users/{userId}/account-status` `{accountStatus}` | `B7AdminUserChange` |
| `GET /api/admin/courses?query=&publicationStatus=&page=&size=` | `B7AdminCoursePage` |
| `PUT /api/admin/courses/{courseId}/publication` `{publicationStatus: "hidden"}` | `B7AdminCourseChange`; `published` is 400 |

Administrator only. Errors: 400 malformed input or values; 401; 403 non-Administrator
or an actor who lost the role; 404 missing user or Course; 409 self-change or
last-Administrator protection; 415; 500. Audit per §7. Bindings: existing
`AdminUserController`, `AdminCourseController`, `AdminRequests`, `AdminResponses`,
`UserManagementService`, `AdminCourseService`.

## 11. CT32 moderation

### 11.1 CT32.queue

V006 statuses only: `open → resolved` (content hidden) or `open → dismissed`; both
terminal, set `resolved_at` to the decision instant, never reopen. Interaction's
existing `ContentReportService` exposes Administrator-only methods (callers: Admin
`ContentModerationService`), each requiring an active `admin` through CT01.identity:

```java
public PageResponse<ModerationReport> listReportsForModeration(String status, int page, int size);
public ModerationDecision hidePostForReport(UUID reportId);
public ModerationDecision resolveLessonReports(UUID reportId, UUID lessonId);
public ModerationDecision dismissReport(UUID reportId);
public record ModerationReport(UUID reportId, String status, String reason, Instant createdAt,
        Instant resolvedAt, UUID reporterId, String targetType, UUID postId, String postContent,
        UUID postAuthorId, boolean postHidden, UUID lessonId) {}
public record ModerationDecision(String decision, List<UUID> resolvedReportIds,
        String status, Instant resolvedAt) {}
```

The queue filters by one status (default `open`), ordered `created_at` ascending,
then `id`, with existing paging. Admin adds reporter and author names (§3.4) and
Course/Lesson titles (§3.1; for post targets through the post's Lesson). Post content
is shown verbatim as plain text; hidden posts stay visible here only.

### 11.2 CT32.hide

Decisions on an **open** report (a non-open report is 409):

- **hide, post target:** read the selected report's target, lock the post
  `FOR UPDATE`, then lock all open reports on that post in one `ORDER BY id FOR UPDATE`
  statement and re-check that the selected report is still open; set
  `is_hidden = true` (already hidden is allowed); mark **every** locked open report
  `resolved`; one `report_post_hidden` audit row per resolved report.
- **hide, Lesson target:** read the Lesson's Course through §3.1, call
  `hideLessonAsAdministrator` first (Course root lock, §10.2), then lock all open
  reports on that Lesson in one `ORDER BY id FOR UPDATE` statement, re-check the
  selected report, mark them `resolved` through `resolveLessonReports`,
  and write one `report_lesson_hidden` row per resolved report.
- **dismiss:** lock only the selected report `FOR UPDATE`, re-check it is open, set
  `dismissed`; one `report_dismissed` row. A dismissal racing a hide of the same
  target waits on that report row and then sees it resolved (409).

All within §7.2's single transaction; status is re-read under lock. Hidden content is
not deleted. There is no unhide of posts in Final; a hidden Lesson may be republished
by its owner (§10.2). Existing notifications stay; they never contain post text.

### 11.3 CT32.rest

| Operation | Result |
|---|---|
| `GET /api/admin/content-reports?status=&page=&size=` | `B7ModerationReportPage` |
| `PUT /api/admin/content-reports/{reportId}/decision` `{decision: "hide" \| "dismiss"}` | `B7ModerationDecision` |

Administrator only; 400/401/403/404 (missing report)/409 (not open)/415/500.
Bindings: existing `AdminContentReportController`, `ContentModerationService`.

## 12. Errors, precedence and representation

CT18.minimum's ProblemDetail envelope, safe English details and
`Cache-Control: no-store` apply; no new status, exception class or error file. 409
uses the existing `BusinessRuleException` (400/409 only); 403 uses
`AccessDeniedException`; 404 uses `ResourceNotFoundException`. For every Batch 7
operation the first failing stage decides: (1) 401 authentication; (2) 415 request
media type and 400 malformed path or query values; (3) 403 role, then CT03 Lesson
admission or resource lookup (404 concealed or missing); (4) 400 body shape and
field rules; (5) 409 lifecycle or protection rules under lock; (6) 500 provider,
integrity, clock or transaction failure with rollback. One explicit exception: the
403 for an Administrator who lost the role while waiting (§10.1) is decided under
lock at stage 5.

## 13. CT13 reporting

### 13.1 CT13.metrics (LD-12(e): both bases)

US-17 needs both quiz outcomes (DG U13) and mastery (U14). Per Course, for **all**
Courses (hidden and published), at read time, with **joined learners** = Enrollments
whose status is `active` or `completed`:

| Metric | Definition |
|---|---|
| `activeEnrollments`, `completedEnrollments`, `leftEnrollments` | Counts of stored Enrollment statuses |
| `joinedLearners` | `activeEnrollments + completedEnrollments` |
| `completedLearners` | Joined learners whose CT22.progress `isComplete` would be true, computed with the same formula for hidden and published Courses (R = currently published Lessons; a zero-Lesson Course completes nobody). For a hidden Course this is a reporting-only extension of ADR006 §3, which defines learner-facing progress for published Courses |
| `completionRatePercent` | `floor(100 × completedLearners / joinedLearners)`; null when `joinedLearners = 0` |
| `scoredLearnerQuizPairs` | Number of (joined learner, Quiz) pairs with a submitted attempt, over all `practice` and `test` Quizzes of the Course whether currently published or hidden (diagnostics measure prior knowledge and are excluded) |
| `averageQuizScore` | Exact mean of `score_percentage` of each pair's **latest** submitted attempt by ADR005 §6.1's tuple, rounded half-up to two fraction digits; null when no pair |
| `masteryDistribution` | Per Course skill (CT07.reasons name order): counts of joined learners in `Unknown` (no row or explicit Unknown), `Learning`, `Mastered`, `Weak` |

Left learners are excluded from every rate. Metrics are descriptive snapshots: no
history, trend, threshold, cross-Course aggregate or adaptive decision uses them.

### 13.2 CT13.reads

Admin's existing `PlatformMetricsService` requires an active `admin` through
CT01.identity and composes, in **one** read-only REPEATABLE READ transaction, these
actorless owning-module reads (only caller: `PlatformMetricsService`):

```java
// Course — existing EnrollmentService.java
public PageResponse<CourseEnrollmentMetrics> readEnrollmentMetrics(int page, int size);
public Optional<CourseEnrollmentMetrics> readEnrollmentMetrics(UUID courseId);
public record CourseEnrollmentMetrics(UUID courseId, String title, String publicationStatus,
        long activeEnrollments, long completedEnrollments, long leftEnrollments,
        long completedLearners, Set<UUID> joinedLearnerIds) {}
// Assessment — existing AttemptService.java
public QuizOutcome readQuizOutcome(UUID courseId, Set<UUID> learnerIds);
public record QuizOutcome(UUID courseId, long scoredLearnerQuizPairs, BigDecimal averageQuizScore) {}
// Adaptive — existing MasteryQueryService.java
public List<SkillMasteryCount> readMasteryDistribution(UUID courseId, Set<UUID> learnerIds);
public record SkillMasteryCount(UUID skillId, String skillName, long unknown, long learning,
        long mastered, long weak) {}
```

The Course page is ordered title `COLLATE "C"`, then `id`, with existing paging;
`joinedLearnerIds` never leaves the backend. Admin owns only orchestration and the
REST shape; it never reads another module's tables. Admin's existing
`ReportingQueryRepository` must not query Course, Assessment or Adaptive tables (no
such use is approved). Each owning module computes its own figures with set-based
queries over its own data.

### 13.3 CT13.rest

| Operation | Result |
|---|---|
| `GET /api/admin/metrics/courses?page=&size=` | `B7CourseMetricsPage`: per-Course enrollment, completion and quiz figures |
| `GET /api/admin/metrics/courses/{courseId}` | `B7CourseMetricsDetail`: the same figures plus `masteryDistribution` |

Administrator only; 400/401/403/404/500; no-store. Bindings: existing
`AdminReportingController`, `ReportingResponses`, `PlatformMetricsService`.

## 14. Module boundaries

Call directions added by this record: Admin → {Auth, Course, Interaction, Assessment,
Adaptive} public application methods; Interaction → {Course, Auth, Assessment}
public methods, plus compile-time use of the Course, Assessment and Adaptive event
types it subscribes to. Existing approved directions are unchanged, including the
mutual Assessment ↔ Adaptive calls (Assessment uses `SkillGraphService`, ADR005 §3.2;
Adaptive reads Assessment evidence and coverage, ADR011 §§4, 12), Assessment → Course,
Adaptive → Course and Course → Auth. No new cycle is introduced: Course, Assessment
and Adaptive reach Interaction only through §9's events, Interaction never calls
Admin, and nothing calls Admin. No repository, entity or table of another module is accessed. The new
methods are the bounded, allowlisted surface listed in §§3, 10, 11 and 13. No new
module, service, dependency, file, migration or architecture change is introduced.

## 15. Acceptance oracles — specified, NOT_RUN

Placement follows ADR008 §5: existing `NotificationEventListenerTest`,
`DeadlineReminderSchedulerTest`, `UserManagementServiceTest`,
`AuthorizationIntegrationTest` and the owning modules' existing suites, plus the
real-PostgreSQL suite `InteractionAdminIntegrationTest` approved in §19.3.

| Oracle | Required result |
|---|---|
| B7-O01 | Derived IDs: the same request twice creates one row; `create` returns 0 the second time; two reminder runs over the same window create one reminder per learner, Assignment and deadline. |
| B7-O02 | A rolled-back publication, grade, submission or recommendation unit creates no notification; a forced listener failure leaves the producer committed and returns its normal response. No plain `@EventListener` exists for the four events; `QuizSubmittedEvent` has exactly the two approved subscribers. |
| B7-O03 | Reminder window: deadline = now is not reminded, now + lead is; submitted, left and non-member learners get none; a changed deadline inside the window gives a second reminder; a hidden Course, or a hidden bound Lesson, gives none (also for graded notifications); a deadline passing during the run is not reminded. |
| B7-O04 | Notification reads and mark-read are owner-scoped: a foreign ID is 404 like a missing one; `read-all` changes only own rows; ordering and paging as §4.2. |
| B7-O05 | Q&A admission follows CT03 for Learner active/completed/left/absent, owning and non-owning Instructor and Administrator (403); hidden posts and replies of hidden questions never appear; a reply to a reply is 400; a mismatched Lesson is 404. |
| B7-O06 | A reply notifies the question author once, never the replier, and commits with the reply. |
| B7-O07 | Every audited action writes exactly its catalogue row(s) in the same transaction; an injected audit-insert failure rolls back the action; no-ops write nothing. |
| B7-O08 | Self role/status change and last-Administrator removal are 409; two concurrent mutual demotions leave at least one active Administrator; a locked user's next request is 401. |
| B7-O09 | Course and Lesson hiding by an Administrator take the Course root lock first and produce CT21's effects; a hide racing a readiness, coverage or progress writer of the same Course serializes on the root (barrier test); `published` is 400; hide of an already hidden Course audits nothing. |
| B7-O10 | Moderation: hide resolves every open report on the same target; dismiss changes only the selected report; a decided report is 409; statuses never reopen; two concurrent hides through different reports of one post, and a dismissal racing a hide, finish without deadlock (content row first, reports `ORDER BY id`). |
| B7-O11 | Metrics match hand-computed fixtures: zero joined learners give null rates; latest-attempt selection follows ADR005's tuple; diagnostics are excluded; Unknown counts joined learners without rows; left learners are excluded. |
| B7-O12 | OpenAPI shapes, 401/403/404 precedence and no-store headers for every Batch 7 operation; REST role values use the API vocabulary; frontend renders every message and post as plain text. |
| B7-O13 | With the test pool limited so that every connection is held by a producer, the producer still commits and responds (after the acquisition timeout) and the lost notification is logged; with a pool larger than the producer threads, notifications are created. |

## 16. Compatibility and alternatives

No effective approval is amended or superseded. CT11-READ-FIELDS r1 is completed
without change; CT03 U04 is consumed as approved; CT21 publication semantics, CT22
reconciliation and ADR005's lock order are reused; ADR010 grading and
ADR011's unit, single in-transaction subscriber and recommendation rules are unchanged
(§9.3 is the subscriber contract ADR011 §4 anticipated). ADR005's guard callback is
not reused for Administrator hiding; that path is a root-lock participant under ADR005
§13.3 (§10.2). Rejected: random notification
IDs with a new de-duplication table (migration without benefit); in-transaction
notification listeners (would make producers fail with notifications, violating every
CT26–CT29 criterion); an outbox or broker (new infrastructure, non-goal scale); a
separate quiz-result event (structure change, duplicate payload); notifying every
`continue` recommendation (noise); a Course `type`/`link` column (rejected by CT11 r1);
Admin SQL over other modules' tables (ADR001); Administrator publish/unhide powers
(overrides Instructor ownership without a takedown model).

## 17. Retained gates

**Start prerequisites for consumers:** independent CODEOWNER and module review of
this record — Interaction and Admin @tihh647-ui, Course @TamiaoNguyen, Assessment
@minhtan29042006, Adaptive @thanhtran0311, Auth @duyduyit, Frontend @heizseung
(each together with the Leader CODEOWNER) — required CI, and protected integration
on `main`. Then the Leader's implementation-start confirmation (§1) applies within each
Issue's scope.

**Still required:** IN06/IN07/IN08/IN10/IN11/IN12/IN15/IN20/IN21, CO06 (admin slice),
CO12, CO16, AS09 (event), AS12 (unchanged), AS17, AD14 (event), AD16 (reporting),
FE13/FE20–FE23/FE28 implementation and tests; QA06 execution of §15; runtime database
grants within the §19.2 ceiling, added per consumer as dated CT17-P6 matrix
revisions: the four deferred Interaction/Admin tables, `users` UPDATE (role,
account_status) for CT12.user (r2 currently denies both), `courses`/`lessons`
UPDATE (publication_status) for Administrator hiding and the cross-module SELECTs
the reminder and CT13 reads need; operator-recorded reminder inputs (§3.5); pool
sizing for after-commit listeners (§8); the two integration suites of §19.3. LD-09 stays open; LD-02 is resolved (§19.4). No implementation,
deployment, migration execution or risk acceptance is implied.

## 18. Preparation validation — 15 November 2026

Validated before commit: Markdown links and anchors in changed files; OpenAPI JSON
parsing, internal `$ref` resolution, unique operation IDs and security declarations;
`git diff --check`; secret and artifact scan of the diff. No backend, frontend or
E2E test was run because this is documentation only. The continuation of the same day (§§19–20) repeated these checks plus a recursive
comparison proving that every pre-existing OpenAPI value is preserved.

## 19. Continuation decisions — 15 November 2026 (second instruction)

### 19.1 Authority

The Project Leader's later explicit instruction of 15 November 2026, **“CodeMastery —
Final Contract Resolution & Comprehensive Independent Re-Audit of PR #244 and #245”**,
expands this record's delegation to bounded technical decisions that earlier records
left for Leader selection, and authorizes corrections of verified findings in this PR.
It excludes merge, deployment, credential or privilege changes on any live system,
human review and residual-risk acceptance, and it does not permit silent amendment of
earlier effective Leader-reserved approvals. Every decision below is
**DELEGATED_APPROVED** under that instruction unless stated otherwise; none is personal
Leader approval. Corrections of findings from the adversarial re-audit are listed in
§20; earlier sections were edited in place only where §20 says so.

### 19.2 CT17-P6-PLAN-2026-11-15-r1 — least-privilege ceiling for Batch 2–7 consumers

The effective CT17-P6 extension procedure (deployment record, “Deferred privileges and
extension procedure”, r1 and the Leader-approved r2) requires a dated **matrix**
revision to ship together with the implemented consumer, `RUNTIME_PRIVILEGE_MATRIX`
in `infrastructure/vm/deploy.sh` and the CI consumer tests. A matrix revision now,
before any Batch 2–7 consumer exists, would break that procedure (and the CI contract
test that pins r1 and r2). The compatible decision is therefore a **ceiling**, not a
matrix revision: each consumer's implementation PR adds a dated revision with exactly
the privileges its SQL exercises, **never exceeding** the row below, plus its script
and CI changes. Grants on any real database stay Leader-reserved operations; nothing
is granted by this record.

| Relation | Ceiling for runtime role | Contract basis | Never granted |
|---|---|---|---|
| `users` | existing r2 + UPDATE (role, account_status) | CT12.user; row locks use these columns | email, display_name, id updates; DELETE |
| `courses` | existing + INSERT; UPDATE (title, description, prerequisite_description, publication_status, adaptive_enabled, adaptive_settings) | CT21.authoring, CT06 settings, CT12.course | instructor_id update; DELETE |
| `chapters` | SELECT, INSERT, UPDATE (title, position) | CT21.authoring, reorder | course_id update; DELETE |
| `lessons` | SELECT, INSERT, UPDATE (title, content_markdown, position, publication_status) | CT21.authoring, CT12/CT32 hide | chapter_id update; DELETE |
| `learning_materials` | SELECT; INSERT; UPDATE (title, publication_status, text_content, external_video_url, object_key, original_filename) | CT21.authoring, CT09 | lesson_id, material_type update; DELETE |
| `enrollments` | existing + UPDATE (resume_lesson_id) | CT22.resume | DELETE |
| `lesson_progress` | SELECT, INSERT, UPDATE (completed, mastery_skipped) | CT22.progress, CT22.atomic | DELETE |
| `skills` | SELECT, INSERT, UPDATE (name, description), DELETE | CT05 (deleteSkill) | course_id update |
| `skill_prerequisites`, `lesson_skills` | SELECT, INSERT, DELETE | CT05 add/remove, map/unmap | UPDATE |
| `quizzes` | SELECT, INSERT, UPDATE (title, kind, time_limit_seconds, publication_status), DELETE | CT23 replace/delete unused hidden | course_id update |
| `questions` | SELECT, INSERT, UPDATE (skill_id, prompt, kind, difficulty, options, correct_options), DELETE | CT23 replace/delete unused | — |
| `quiz_questions` | SELECT, INSERT, DELETE | CT23 composition replacement | UPDATE |
| `attempts` | SELECT, INSERT, UPDATE (submitted_at, score_percentage) | CT04 | user/quiz/number/start/expiry updates; DELETE |
| `attempt_answers` | SELECT, INSERT, UPDATE (selected_options, is_correct) | CT04 save/grade | DELETE |
| `assignments` | SELECT, INSERT, UPDATE (lesson_id, title, instructions, deadline_at, publication_status) | CT10.rest | course_id update; DELETE |
| `submissions` | SELECT, INSERT, UPDATE (object_key, original_filename, submitted_at, grade_percentage, feedback, graded_at) | CT10 replacement/grading | assignment/user updates; DELETE |
| `learner_skill_masteries` | SELECT, INSERT, UPDATE (state) | CT08.execution | DELETE |
| `path_recommendations` | SELECT, INSERT | CT07 (history is append-only) | UPDATE, DELETE |
| `discussion_posts` | SELECT, INSERT, UPDATE (is_hidden) | CT30.posts, CT32.hide (also enables the parent `FOR SHARE` lock) | content/author/lesson updates; DELETE |
| `notifications` | SELECT, INSERT, UPDATE (is_read) | CT11 (`ON CONFLICT DO NOTHING` needs INSERT only) | message/user updates; DELETE |
| `content_reports` | SELECT, INSERT, UPDATE (status, resolved_at) | CT30.reports, CT32 | reason/target/reporter updates; DELETE |
| `audit_logs` | SELECT, INSERT | CT31 | UPDATE, DELETE (already permanently prohibited) |

Column-level UPDATE means implementations must issue targeted updates (or an
equivalent dynamic update) and prove them with the existing real-backend privilege
test under the runtime role; a JPA full-row update that touches a forbidden column is
an implementation defect, not a reason to widen the ceiling. TRUNCATE, REFERENCES,
TRIGGER, MAINTAIN, DDL and grant options remain permanently prohibited. Gates: each
consumer's dated matrix revision, script and CI change; infrastructure review
(@tihh647-ui) and Leader CODEOWNER review; and Leader-authorized provisioning on any
real host.

### 19.3 CT14-INTEGRATION-PLACEMENT-2026-11-15-r1 — Interaction/Admin and storage suites

ADR008 §5 left dedicated Interaction/Admin and storage integration placements as gaps
needing a bounded structural decision; ADR010 §14 approved no MinIO test container.
Investigated: the existing integration suites under
`backend/src/test/java/com/codemastery/integration/` all extend `AbstractIntegrationTest`
(one disposable DP01-hardened PostgreSQL per JVM, Flyway, scheduler disabled), and
`testcontainers-junit-jupiter` already provides `GenericContainer`; no new dependency is
needed. Selected, exactly two new files and no other structure change:

| File | Responsibility |
|---|---|
| `backend/src/test/java/com/codemastery/integration/InteractionAdminIntegrationTest.java` | Real-PostgreSQL B7-O01–O03 and O06–O11: after-commit isolation and rollback silence for the four events, derived-ID idempotence, reminder window, reply notification atomicity, audit atomicity and no-op behaviour, last-Administrator concurrency, Administrator hide lock order, moderation resolution and metrics. Extends `AbstractIntegrationTest`; uses barriers, independent committed reads and committed FK-safe cleanup per ADR008 §5. |
| `backend/src/test/java/com/codemastery/integration/FileStorageIntegrationTest.java` | Real MinIO plus PostgreSQL B5-O05 (a)–(f), B5-O10 and B5-O14. Starts a `GenericContainer` from the **same pinned MinIO image digest** used by `infrastructure/docker-compose.yml`, with ephemeral credentials and bucket, never shared or production storage; extends `AbstractIntegrationTest`. |

Authorization matrices stay in `AuthorizationIntegrationTest` (B7-O04/O05/O12) and
unit rules in the existing Interaction/Admin unit tests; nothing is duplicated.
Alternatives rejected: one suite per module (every Batch 7 case crosses modules and
would duplicate fixtures); folding the cases into `AdaptiveLearningFlowIntegrationTest`
(misleading scope); a MinIO Testcontainers module (new dependency without benefit).
The structure record in `docs/repository-structure.md` is updated additively. Image
admission of MinIO for production is unaffected; the test container is not a release
artifact.

### 19.4 LD-02 — notification scope

The approved architecture (§4.5, US-09) already lists new content, upcoming deadlines,
results and Q&A replies, and §§4–9 approve a contract for each. The technically correct
disposition is therefore **all architecture-listed triggers are Final contract scope**
(LD-02 option a); no scope-down is selected, because a scope-down would deviate from the
approved architecture and remains the Leader's prerogative. Requirement §4.5's minimum
(at least one trigger) is exceeded. Delivery order is a planning matter, not a contract.

### 19.5 Event delivery and reminder configuration — confirmation

Delivery is **best effort, at most once** (§8): a crash between the producer commit and
the listener loses that notification; no retry exists, so retries cannot duplicate,
and derived IDs make an accidental re-delivery harmless. No document may describe
notifications as guaranteed. Reminder inputs stay operator values (§3.5); no number is
selected, because the window is an operational choice with no technical optimum.

## 20. Adversarial re-audit corrections — 15 November 2026

Under §19.1, an adversarial re-audit (three separately instructed review passes,
reconciled against the sources; not a GitHub or human review) produced these findings,
corrected in place. Revision IDs are unchanged because PR #244 is not integrated and
each correction narrows or completes the same scope.

| ID | Severity | Finding | Correction |
|---|---|---|---|
| F7-01 | HIGH | Reminders and graded notifications ignored a hidden bound Lesson, leaking a concealed Assignment title (CT03 U03 conceals it) | `lessonId` added to `ReminderCandidate` and `AssignmentGradedEvent`; Lesson publication checked (§§3.3, 5, 9.2); B7-O03 |
| F7-02 | MEDIUM | Post-hide locked report rows before the post and in undefined order, so two concurrent hides could deadlock; §7.2 and §11.2 disagreed | Content row first, then all open reports `ORDER BY id`; dismissal locks one report (§§7.2, 11.2); B7-O10 |
| F7-03 | MEDIUM | §14 called the dependency graph acyclic, ignoring the approved Assessment ↔ Adaptive calls and Interaction's use of Adaptive event types | §14 restated |
| F7-04 | MEDIUM | REST role values used storage literals instead of the existing API vocabulary | API values `Learner`, `Instructor`, `Administrator` in OpenAPI (§§6.2, 10.1); B7-O12 |
| F7-05 | MEDIUM | §17 grant gate omitted `users` UPDATE (role, account_status), publication updates and cross-module SELECTs | §19.2 ceiling; §17 restated |
| F7-06 | MEDIUM | After-commit listeners can exhaust the pool and delay producers | Consequence and qualification item in §8; B7-O13 |
| F7-07 | LOW | Test-out key depended on the clock; instant keys assume advancing clocks | Test-out keyed by the lowest inserted skip ID; the other instant keys keep the advancing-clock assumption already required by ADR010/ADR011 tests, and a same-instant duplicate is dropped (at most once) |
| F7-08 | LOW | “RFC 9562 version 3” overstated `nameUUIDFromBytes` | Wording corrected (§4.1) |
| F7-09 | LOW | A deadline passing during a run could still be reminded | Per-candidate re-check (§5) |
| F7-10 | LOW | “Never names concealed content” ignored later hiding | “At creation time” (§4.1) |
| F7-11 | LOW | CT13 applied `isComplete` to hidden Courses without saying so; hidden Quizzes unspecified; `scoredQuizAttempts` counted pairs | Reporting-only extension stated; all Quizzes counted; field renamed `scoredLearnerQuizPairs` (§13) |
| F7-12 | LOW | 100-ID caps could be exceeded by one page | Chunking stated (§§3.1, 3.4) |
| F7-13 | LOW | Lost-role 403 is decided under lock, unlike §12's stage order | Exception stated (§12) |
| F7-14 | LOW | Administrator hiding was described as reusing ADR005's guard | Root-lock participant under ADR005 §13.3 (§§10.2, 16); B7-O09 |
| F7-15 | MEDIUM | OpenAPI `x-batch-01`/`x-ct03` and `x-batch-03` integration flags still read pending although ADR005/ADR007/ADR011 are on `main` | Dated current-state notes added beside the historical flags, which are kept |

Verified as correct and unchanged: after-commit semantics and failure isolation; the
two-member `QuizSubmittedEvent` subscriber set; `ON CONFLICT (id) DO NOTHING` with
V006; owner-scoped reads; CT30 precedence against ADR001 §12.4; the last-Administrator
protection under concurrency; Lesson-target moderation order; CT31 atomicity; CT13
handling of empty populations, left learners and missing mastery rows; the reminder
window; and all Batch 7 OpenAPI operations against their sections.
