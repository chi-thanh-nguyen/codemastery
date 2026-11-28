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
| CP-064 | CT11.create / [#137](https://github.com/chi-thanh-nguyen/codemastery/issues/137) | `CT11-CREATE-2026-11-15-r2` | §4.1 | CT11-READ-FIELDS r1 |
| CP-065 | CT11.read / #137 | `CT11-READ-2026-11-15-r1` | §4.2 | CT11-READ-FIELDS r1 (field set retained) |
| CP-066 | CT30.access / [#146](https://github.com/chi-thanh-nguyen/codemastery/issues/146) | `CT30-ACCESS-2026-11-15-r1` | §6.1 | CT03-2026-10-21-r1 U04 |
| CP-067 | CT31.actions / [#147](https://github.com/chi-thanh-nguyen/codemastery/issues/147) | `CT31-ACTIONS-2026-11-15-r1` | §7.1 | V006 `audit_logs` |
| CP-068 | CT31.consistency / #147 | `CT31-CONSISTENCY-2026-11-15-r1` | §7.2 | ADR005 Course-root guard |
| CP-069 | CT27.event / [#143](https://github.com/chi-thanh-nguyen/codemastery/issues/143) | `CT27-EVENT-2026-11-15-r1` | §9.2 | CP-041 CT10.grading r1 (ADR010, on `main`) |
| CP-070 | CT30.posts / #146 | `CT30-POSTS-2026-11-15-r1` | §6.2 | CP-066 |
| CP-071 | CT30.reports / #146 | `CT30-REPORTS-2026-11-15-r1` | §6.3 | CP-066 |
| CP-072 | CT31.read / #147 | `CT31-READ-2026-11-15-r1` | §7.3 | CP-067; §3.4 |
| CP-073 | CT12.user / [#138](https://github.com/chi-thanh-nguyen/codemastery/issues/138) | `CT12-USER-2026-11-15-r2` | §10.1 | CP-067, CP-068; CT01 r1 |
| CP-074 | CT13.metrics / [#139](https://github.com/chi-thanh-nguyen/codemastery/issues/139) | `CT13-METRICS-2026-11-15-r1` | §13.1 | CP-011 CT22.progress r1 |
| CP-075 | CT26.event / [#142](https://github.com/chi-thanh-nguyen/codemastery/issues/142) | `CT26-EVENT-2026-11-15-r1` | §9.1 | CP-015 CT21.authoring r1 |
| CP-076 | CT27.subscription / #143 | `CT27-SUBSCRIPTION-2026-11-15-r2` | §9.2 | CP-069, CP-064; §§3.2, 3.3 |
| CP-077 | CT30.notify / #146 | `CT30-NOTIFY-2026-11-15-r1` | §6.4 | CP-070, CP-064 |
| CP-078 | CT12.course / #138 | `CT12-COURSE-2026-11-15-r2` | §10.2 | CP-067, CP-068; CP-015 (applies: reuses publication semantics); §3.4 |
| CP-079 | CT32.queue / [#148](https://github.com/chi-thanh-nguyen/codemastery/issues/148) | `CT32-QUEUE-2026-11-15-r1` | §11.1 | CP-071 |
| CP-080 | CT26.subscription / #142 | `CT26-SUBSCRIPTION-2026-11-15-r1` | §9.1 | CP-075, CP-064 |
| CP-081 | CT29.event / [#145](https://github.com/chi-thanh-nguyen/codemastery/issues/145) | `CT29-EVENT-2026-11-15-r1` | §9.4 | CP-054 CT07.reasons r1 (ADR011, on `main`) |
| CP-082 | CT30.rest / #146 | `CT30-REST-2026-11-15-r1` | §6.5 | CP-070, CP-071, CP-077 |
| CP-083 | CT12.rest / #138 | `CT12-REST-2026-11-15-r1` | §10.3 | CP-073, CP-078 |
| CP-084 | CT32.hide / #148 | `CT32-HIDE-2026-11-15-r1` | §11.2 | CP-079, CP-067 |
| CP-085 | CT29.subscription / #145 | `CT29-SUBSCRIPTION-2026-11-15-r1` | §9.4 | CP-081, CP-064 |
| CP-086 | CT32.rest / #148 | `CT32-REST-2026-11-15-r1` | §11.3 | CP-084 |
| CP-087 | CT11.reminders / #137 | `CT11-REMINDERS-2026-11-15-r2` | §5 | CP-064, CP-040 CT10.deadline r1; §§3.2, 3.3, 3.5 |
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

**`CT22-ACTIVE-LEARNERS-2026-11-15-r2` — DELEGATED_APPROVED** (r1 → r2, §21), [CT22 #43](https://github.com/chi-thanh-nguyen/codemastery/issues/43).
Add to existing `modules/course/application/service/EnrollmentService.java`:

```java
public Set<UUID> joinedLearnerIds(UUID courseId);
```

Immutable set of learners whose Enrollment status is `active` or `completed`
(the CT03 learning-access population); empty for a missing Course. Actorless,
unlocked: a statement snapshot inside the caller's transaction. Callers, exactly:
Interaction `DeadlineReminderScheduler` (§5) and `NotificationEventListener` for the
CT27 subscription only (§9.2, recipient eligibility).

### 3.3 CT10.reminder-read — LD-07 option (a)

**`CT10-REMINDER-READ-2026-11-15-r2` — DELEGATED_APPROVED** (r1 → r2, §21), [CT10 #136](https://github.com/chi-thanh-nguyen/codemastery/issues/136).
Assessment owns deadlines (ADR001 producer ownership; ADR010 §8 left this choice to
CT11.reminders). Add to existing
`modules/assessment/application/service/AssignmentService.java`:

```java
public List<ReminderCandidate> findReminderCandidates(Instant now, Instant windowEnd,
        ReminderCursor after, int limit);
public Optional<ReminderCandidate> lockNotificationState(UUID assignmentId);
public record ReminderCandidate(UUID assignmentId, UUID courseId, UUID lessonId,
        String assignmentTitle, Instant deadlineAt, Set<UUID> submittedLearnerIds) {}
public record ReminderCursor(Instant deadlineAt, UUID assignmentId) {}
```

**Discovery.** `findReminderCandidates` returns **published** Assignments with
`now < deadline_at <= windowEnd` (CT10.deadline's exclusive end: an Assignment whose
deadline equals `now` is already late), ordered by (`deadline_at`, `id`) ascending,
strictly after the nullable keyset cursor, at most `limit` rows (1..2147483647).
`submittedLearnerIds` holds every learner with a Submission row of any state.
`lessonId` is the Assignment's nullable bound Lesson. Course and Lesson publication are
not filtered here (§5 checks them). Its rows are an unlocked snapshot used **only** to
choose which Assignment IDs to process and to advance the cursor; no notification
recipient, title, Lesson or deadline may be taken from them.

**Admission read.** `lockNotificationState` requires an active read-write transaction
(`IllegalStateException` otherwise) and locks the Assignment row `FOR SHARE`, the
shared mode of ADR010 §10's lock: every submission, replacement, grading and
Assignment edit takes that row `FOR UPDATE` first, so they and this read serialize.
After the lock it re-reads, with new statements, the Assignment's current Course,
bound Lesson, title, deadline and the learners with a Submission row; it returns empty
when the Assignment does not exist or is not `published`. Until the caller's
transaction ends, no submission, replacement, grade, deadline, title, Lesson or
publication change of that Assignment can commit, and every such change committed
before the lock is visible. It takes no Submission, Course or Lesson lock, and each
caller must make it the first row lock of its transaction (§4.1); because ADR010's
writers also lock the Assignment first, this adds no lock cycle. Callers, exactly: `DeadlineReminderScheduler` (§5) and
`NotificationEventListener` for CT27 (§9.2). Both methods are actorless and have no
REST exposure.

### 3.4 CT01.user-summaries

**`CT01-USER-SUMMARIES-2026-11-15-r2` — DELEGATED_APPROVED** (r1 → r2, §21), [CT01 #18](https://github.com/chi-thanh-nguyen/codemastery/issues/18).
Q&A authors may be Learners or Instructors and audit actors are Administrators;
`learnerDisplayNames` (ADR010 §3.1) is Course-only, and CT01.identity's
`instructorDisplayNames` is limited to published-course results (ADR001 §11). Add to existing
`modules/auth/application/service/UserAccountService.java`, leaving both CT01.identity
methods and `learnerDisplayNames` unchanged:

```java
public Map<UUID, UserSummary> userSummaries(Set<UUID> userIds);
public record UserSummary(UUID userId, String displayName, String role) {}
```

Stored display name and current role literal for existing IDs; unknown IDs omitted;
at most 100 IDs per call (callers split larger sets, for example a discussion page
with many replies or a moderation page with reporters and authors, into chunks of at
most 100); no email, account status or credential data. Callers, exactly:
Interaction `DiscussionService` (authors of posts it returns), Admin
`AuditLogService` and `ContentModerationService` (actors and reporters of rows they
return), and Admin `AdminCourseService` (only the `instructorId` values of the
Administrator Course page or hide result it is returning, §10.2, after its active-`admin` check; a
page has at most 100 rows, so at most 100 IDs). Each caller passes only IDs from rows
it is returning; no caller may pass a search term, a role filter or arbitrary IDs, so
this is not a user directory. A missing account is omitted (the caller shows null);
a later role change does not hide a name (the current role is returned). Auth never
calls Interaction or Admin.

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
  inside one call are collapsed first, and the rows are then inserted in ascending
  `id` order (r2), so two transactions creating overlapping ID sets (for example two
  application instances reminding the same Assignment) wait on the first common key
  instead of deadlocking on unique-index checks; the later one then inserts nothing
  for those keys. No schema change: the V006 primary key is the
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
- **Visibility precondition and admission point** (r2). Every creator reads §3.1's
  context in the same transaction as its insert and creates nothing (counted, logged
  at debug level) unless the Course is `published` and, when the message names a
  Lesson, that Lesson is `published`; Assignment-based creators additionally admit
  only on `lockNotificationState` (§3.3), which must be the first row lock of their
  transaction. The guarantee is exactly this: **Assessment-owned Assignment state**
  (publication, bound Lesson, title, deadline, submissions) is the state at the
  locked admission read and cannot change before the insert commits; **Course and
  Lesson publication and membership** are statement snapshots read in that
  transaction and are not locked, so a hide or leave that commits between the read
  and the insert's commit can still be followed by a notification naming that
  content. No creator takes the Course root: that would make quiz submission,
  authoring and progress wait on notification jobs (ADR005 §13.3). This is the
  contract's defined visibility guarantee, not an accepted risk; it adds no exposure
  class beyond the existing rule that rows created earlier stay when content is
  hidden later (§11.2), and the frontend never links a notification to content.

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
3. For each candidate's `assignmentId`, in its **own** read-write READ COMMITTED
   transaction (r2), in this order:
   1. **Admission:** `lockNotificationState(assignmentId)` (§3.3) as the first row
      lock; skip on empty (no longer published). Every later step uses
      only this locked state, never the discovery row.
   2. Sample `checkedAt = clock.instant()` (truncated to microseconds) **after** the
      lock returns; skip unless `checkedAt < deadlineAt <= windowEnd` (a deadline
      edited out of the window, or passed meanwhile, is skipped; a later run handles
      a deadline moved later).
   3. Read §3.1's context with the **locked** `lessonId` when present; skip unless the
      Course is published and, for a Lesson-bound Assignment, that Lesson is published
      (CT03 U03 conceals it otherwise).
   4. Recipients = §3.2's joined learners minus the **locked**
      `submittedLearnerIds`; call CT11.create with ID
      `derivedId("deadline-reminder", assignmentId, learnerId, deadlineAt)` (locked
      deadline) and message
      `Reminder: the assignment "{assignmentTitle}" in "{courseTitle}" is due soon. Open the assignment to see its deadline.`
      (locked title); commit, which releases the lock.
4. A failing candidate is logged (IDs only) and the run continues; the next run
   retries it and the derived ID prevents duplicates.

**Semantics.** One reminder per learner, Assignment and deadline value. Changing the
deadline produces a new reminder if the new deadline enters the window; a deadline
that already passed is never reminded (no catch-up after downtime). The message
carries no time, so no time-zone conversion is needed; the Assignment page shows the
deadline. Lessons, Quizzes and attempt time limits are not reminded. Assessment
exposes facts only; Interaction owns the reminder policy.

**Concurrency (r2).** The linearization point for Assignment state is the locked
admission read: a hide, Lesson rebinding, title or deadline edit, or a submission
that commits before it is honoured; one that arrives later waits until the reminder
transaction commits, and the reminder then correctly reflects the earlier state (a
learner who submits right after it was still unsubmitted at that point). The
remaining, stated races are those of §4.1: Course and Lesson hiding and membership
changes are unlocked snapshots, so a learner who leaves, or content hidden, after the
§3.1/§3.2 reads but before commit can still receive that one reminder. Submissions,
grading and edits of the same Assignment wait for the per-candidate transaction (one
context read, one membership read and one insert statement); no other writer is
delayed. The reminder never holds the lock across candidates or pages.

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

**Scope (corrected, §21 R01).** These rules govern Interaction's **notification**
subscriptions only. They do not govern Adaptive's existing `QuizSubmittedEventListener`,
which ADR011 §§4–5 approves as a plain `@EventListener` inside the submission unit and
which this record leaves unchanged. The two `QuizSubmittedEvent` subscribers therefore
behave differently by design:

| Subscriber | Phase and transaction | Failure effect |
|---|---|---|
| Adaptive `QuizSubmittedEventListener` (ADR011) | Plain `@EventListener`: same thread, **inside** the producer's transaction, invoked synchronously by `publishEvent` | Mandatory: any exception in ADR011 §4 steps 1–7 propagates out of `publishEvent` and rolls back the whole unit (quiz result, mastery, progress, recommendations), ADR011 §5 |
| Interaction `NotificationEventListener` (this record) | `@TransactionalEventListener(AFTER_COMMIT)` + `REQUIRES_NEW`: runs only after that unit **committed**, in its own transaction | Best effort: its failure never rolls back or fails the committed unit |

Spring's default multicaster (no executor is configured or approved) invokes
listeners synchronously. For a plain listener, an exception propagates to the
`publishEvent` caller. For a transactional listener, `publishEvent` only registers a
transaction synchronization and returns; the method runs from the completion
callback after commit (and not at all after rollback, nor without a transaction,
because `fallbackExecution = false`), and an exception there is logged by Spring,
not rethrown to the producer. (Inspected in the locally resolved spring-tx 7.0.9 jar: the
listener synchronization implements only `beforeCommit` and `afterCompletion`, runs the
AFTER_COMMIT phase from `afterCompletion` on `STATUS_COMMITTED`, and
`TransactionSynchronizationUtils.invokeAfterCompletion` logs, not rethrows, its
exceptions.)

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
  `ApplicationEventPublisher.publishEvent`, after their rows are flushed. Registering
  the Interaction subscription adds no failure path to publishing and cannot change
  the producer's outcome. For `ContentPublishedEvent`, `AssignmentGradedEvent` and
  `RecommendationCreatedEvent`, Interaction's after-commit method is the **only**
  subscriber, so publishing them cannot throw because of a subscriber. For
  `QuizSubmittedEvent`, `publishEvent` still runs Adaptive's in-transaction listener
  and propagates its exceptions exactly as ADR011 §5 requires; this record neither
  catches nor weakens that. **No additional plain `@EventListener`** may subscribe to
  any of the four events: it would join the producer transaction and could fail it.
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

**CT27.subscription (r2).** The event's `assignmentPublished`, `lessonId` and
`assignmentTitle` are producer-time facts (kept for logs and tests); the subscription
never uses them for admission or message text, because the Assignment can be hidden,
renamed or rebound between the grade commit and the listener. In the listener's own
`REQUIRES_NEW` transaction, in this order: (1) `lockNotificationState(assignmentId)`
(§3.3) as the first row lock; skip on empty (not published); (2) skip unless the event's
`learnerId` is in the locked `submittedLearnerIds` (the Submission still exists, as it
always does: no deletion is approved); (3) read §3.1's context with the **locked**
`lessonId`; skip unless the Course is published and, when bound, that Lesson is
published; (4) skip unless `learnerId` is in §3.2's `joinedLearnerIds` (a learner who
left has no current access to the Assignment or grade, ADR010 §12); (5) create one
notification for `learnerId` with ID `derivedId("assignment-graded", submissionId, gradedAt)`
and, using the **locked** title, the message
`Your submission for "{assignmentTitle}" in "{courseTitle}" has been graded.` or,
for a regrade, `The grade for your submission to "{assignmentTitle}" in "{courseTitle}" was updated.`
A later regrade waits for this transaction and then publishes its own event with its
own `gradedAt`. §4.1's stated Course/Lesson/membership race applies. A notification
failure never rolls back grading.

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
subscriber rule is unchanged: Adaptive stays the only in-transaction subscriber, its
failures still propagate through `publishEvent` and roll back the submission unit
(ADR011 §5), and §8's after-commit rules apply to the Interaction member only
(clarified, §21 R01; B7-O14).

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
- **Protections (r2).** An Administrator cannot change their own role or status
  (409). Under concurrency, each mutation, before any other row lock or write, locks
  every `admin` user row in `id` order and then the target row with
  **`FOR NO KEY UPDATE`** (one native statement each, as the existing
  `UserRepositoryImpl` locks; never `FOR UPDATE`), then, with new statements issued
  after all locks are held, re-checks that the actor is still an active Administrator
  (else 403) and rejects any result leaving no active Administrator (409); then it
  updates only `role` or `account_status` and inserts the audit row. `FOR NO KEY
  UPDATE` conflicts with itself, `FOR UPDATE` and `FOR SHARE`, so two mutations still
  serialize on the lowest-`id` common admin row and two Administrators demoting each
  other cannot both succeed. It does **not** conflict with the `FOR KEY SHARE` locks
  that PostgreSQL's foreign-key checks take when another transaction inserts a row
  referencing a user (posts, notifications, reports, audit rows, enrollments,
  attempts), so those inserts never wait for an Administrator mutation and cannot
  close a lock cycle with it. The updated columns carry no unique index usable by a
  foreign key (V001 has only `pk_users` and `uq_users_email`), so PostgreSQL's own
  row lock for that `UPDATE` is also `FOR NO KEY UPDATE`; the mode never escalates.
  The row-locking clause needs UPDATE on at least one column, which the
  `users` UPDATE (role, account_status) ceiling supplies (§19.2).
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
        UUID instructorId) {}
public record CourseChange(AdminCourseView course, boolean changed) {}
public record LessonChange(UUID courseId, UUID lessonId, String publicationStatus,
        boolean changed) {}
```

- Each method requires an active `admin` through CT01.identity. The list includes
  hidden and published Courses; `query` matches a case-insensitive title substring;
  order title `COLLATE "C"`, then `id`.
- **Instructor names (r2).** Course returns only `instructorId` and never calls
  CT01.identity's `instructorDisplayNames` for this list: that method's effective
  scope (ADR001 §11) is IDs from published-course results, and this page includes
  hidden Courses. Admin `AdminCourseService`, after its own active-`admin` check,
  collects the distinct `instructorId` values of the page (at most 100) and calls
  §3.4's `userSummaries` once; `instructorDisplayName` in the REST row is the
  returned `displayName`, or null when the account is missing. A former Instructor
  whose role later changed keeps their name (rows are owned Courses, not a role
  directory). The hide response's single row (`CourseChange.course`) is named the
  same way, with one ID. The REST shape `B7AdminCourse` is unchanged; Course's published
  catalogue and its use of `instructorDisplayNames` are unchanged.
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
real-PostgreSQL suite `InteractionAdminIntegrationTest` approved in §19.3 (which also
carries B7-O14–O17). B7-O18 belongs to the implementing consumer's CT17-P6 runtime-role
privilege test under the extension procedure.

| Oracle | Required result |
|---|---|
| B7-O01 | Derived IDs: the same request twice creates one row; `create` returns 0 the second time; two reminder runs over the same window create one reminder per learner, Assignment and deadline; two concurrent `create` calls with the same IDs supplied in opposite orders both finish without `40P01` and leave each row once (§21 post-fix check). |
| B7-O02 | A rolled-back publication, grade, submission or recommendation unit creates no notification; a forced **Interaction** listener failure leaves the producer committed and returns its normal response. The only plain `@EventListener` for any of the four events is Adaptive's `QuizSubmittedEventListener`; `ContentPublishedEvent`, `AssignmentGradedEvent` and `RecommendationCreatedEvent` have exactly one subscriber (Interaction, after commit) and `QuizSubmittedEvent` exactly the two approved subscribers (B6-V04). (Corrected, §21 R01.) |
| B7-O03 | Reminder window: deadline = now is not reminded, now + lead is; submitted, left and non-member learners get none; a changed deadline inside the window gives a second reminder; a hidden Course, or a hidden bound Lesson, gives none (also for graded notifications); a deadline passing during the run is not reminded; a graded notification is not created for a learner who left or for an Assignment hidden after grading. |
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
| B7-O14 | Two listener paths of `QuizSubmittedEvent` (§21 R01), real PostgreSQL: (a) with a test-scoped fault in Adaptive's in-transaction processing, the submission returns the safe 500, no Attempt result, mastery, progress or recommendation row commits, the Attempt stays active, and Interaction's after-commit method is never invoked (no quiz-result or recommendation notification); (b) with a test-scoped fault in Interaction's method only, the submission returns 200 and the quiz result, mastery, progress and recommendations are committed (independent committed read), with the failure logged and no notification; (c) without faults, the notification exists only after the submission's commit is visible. |
| B7-O15 | Assignment admission under concurrency (§21 R02), barriers on two connections: (a) discovery returns an Assignment, the Instructor hides it and commits, then the per-candidate transaction creates no reminder; (b) the per-candidate transaction holds `lockNotificationState`, a concurrent hide, deadline edit, Lesson rebinding or submission blocks until it commits, and the reminder reflects the pre-change state; (c) a deadline edited out of the window before admission gives no reminder, one moved inside gives a reminder keyed by the new deadline; (d) a learner whose submission commits before admission gets no reminder; (e) an Assignment hidden, renamed or rebound to a hidden Lesson between the grade commit and the CT27 listener gives no notification (hidden) or the locked current title (renamed); (f) no deadlock occurs between reminders, submissions, grading and edits (ADR010 §10 lock order). |
| B7-O16 | Foreign-key lock cycle (§21 R03), real PostgreSQL with barriers: Learner A, the author of a question, has since been promoted to Administrator; transaction R inserts Learner B's reply (FK `KEY SHARE` on B) and pauses; transaction M, an Administrator locking B, takes every admin row (including A) and B with `FOR NO KEY UPDATE` and updates B's `account_status`; R then inserts the notification for A (FK `KEY SHARE` on A). Both commit within the test timeout, no `40P01` occurs, B is locked and the reply and notification exist. The same schedule with `FOR UPDATE` is the recorded counterexample and must not be the implementation. B7-O08's mutual demotions still leave an active Administrator. |
| B7-O17 | Administrator Course names (§21 R04): a hidden Course's row shows its Instructor's name from `userSummaries`; a missing account gives null; a demoted former Instructor keeps the name; `instructorDisplayNames` is never called from the Administrator list or hide path and only with published-course IDs (allowlist test); `userSummaries` receives at most the page's distinct `instructorId` values; a non-Administrator gets 403 before any lookup. |
| B7-O18 | Runtime-role ceiling (§19.2 r2), once the CT22.resume migration exists: under the runtime role a visit updates `resume_lesson_id` and `last_visited_at` together; a fault after the update rolls both back; an update of `enrollments.user_id` or `enrollments.course_id` fails with `42501`; a CT23 composition replacement that updates `quiz_questions.position` succeeds and an update of `quiz_id` or `question_id` fails with `42501`. |

## 16. Compatibility and alternatives

No effective approval is amended or superseded (re-checked for the §21 corrections: ADR001's `instructorDisplayNames` scope is now respected rather than widened, ADR010 §10's writer lock order is joined by a compatible shared reader, ADR006 CT22.resume is enabled by the ceiling, and ADR011 §§4–5 are restated, not changed). CT11-READ-FIELDS r1 is completed
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
FE13/FE20–FE23/FE28 implementation and tests; QA06 execution of §15 (including B7-O14–O18, §21); runtime database
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

### 19.2 CT17-P6-PLAN-2026-11-15-r2 — least-privilege ceiling for Batch 2–7 consumers

*Revision r2 (§21 R05) adds `enrollments.last_visited_at` and `quiz_questions.position`
to the ceiling and states the scope of unlisted relations; r1 was otherwise identical.*

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
| `users` | existing r2 + UPDATE (role, account_status) | CT12.user; its `FOR NO KEY UPDATE` row locks use these columns (§10.1) | email, display_name, id updates; DELETE |
| `courses` | existing + INSERT; UPDATE (title, description, prerequisite_description, publication_status, adaptive_enabled, adaptive_settings) | CT21.authoring, CT06 settings, CT12.course | instructor_id update; DELETE |
| `chapters` | SELECT, INSERT, UPDATE (title, position) | CT21.authoring, reorder | course_id update; DELETE |
| `lessons` | SELECT, INSERT, UPDATE (title, content_markdown, position, publication_status) | CT21.authoring, CT12/CT32 hide | chapter_id update; DELETE |
| `learning_materials` | SELECT; INSERT; UPDATE (title, publication_status, text_content, external_video_url, object_key, original_filename) | CT21.authoring, CT09 | lesson_id, material_type update; DELETE |
| `enrollments` | existing + UPDATE (resume_lesson_id, last_visited_at) | CT22.resume: pointer and time are written atomically (ADR006 `CT22-RESUME-SCHEMA-2026-11-09-r1`); `last_visited_at` is listed once its approved migration exists (r2) | id, user_id, course_id updates; DELETE |
| `lesson_progress` | SELECT, INSERT, UPDATE (completed, mastery_skipped) | CT22.progress, CT22.atomic | DELETE |
| `skills` | SELECT, INSERT, UPDATE (name, description), DELETE | CT05 (deleteSkill) | course_id update |
| `skill_prerequisites`, `lesson_skills` | SELECT, INSERT, DELETE | CT05 add/remove, map/unmap | UPDATE |
| `quizzes` | SELECT, INSERT, UPDATE (title, kind, time_limit_seconds, publication_status), DELETE | CT23 replace/delete unused hidden | course_id update |
| `questions` | SELECT, INSERT, UPDATE (skill_id, prompt, kind, difficulty, options, correct_options), DELETE | CT23 replace/delete unused | — |
| `quiz_questions` | SELECT, INSERT, UPDATE (position), DELETE | CT23 composition replacement under the deferrable position uniqueness (r2: an in-place reorder may update positions) | quiz_id, question_id updates |
| `attempts` | SELECT, INSERT, UPDATE (submitted_at, score_percentage) | CT04 | user/quiz/number/start/expiry updates; DELETE |
| `attempt_answers` | SELECT, INSERT, UPDATE (selected_options, is_correct) | CT04 save/grade | DELETE |
| `assignments` | SELECT, INSERT, UPDATE (lesson_id, title, instructions, deadline_at, publication_status) | CT10.rest; ADR010 §10 `FOR UPDATE` and §3.3's `FOR SHARE` admission lock (row locks need UPDATE on one column) | course_id update; DELETE |
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
an implementation defect, not a reason to widen the ceiling. Relations not listed
(`password_reset_tokens`, `flyway_schema_history`) keep their effective r1/r2 rows;
this ceiling neither widens nor narrows them. A ceiling that omits a column an
effective contract must write is a defect of this record, corrected by a dated
revision of the ceiling, never by a consumer exceeding it. Verification: B7-O18.
TRUNCATE, REFERENCES,
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

## 21. Independent review remediation — 15 November 2026 (third instruction)

### 21.1 Authority and method

The Project Leader's explicit instruction of 15 November 2026, **“CodeMastery —
Independent Review Findings Verification & Direct Remediation on Existing PR
#244/#245”**, provided a separate read-only technical review of this PR at
`3ef9bc09ff4c2c3bc2a97a0124c113852ffbf460` (findings R01–R05 here, R06 and E01–E02 in
ADR013) and authorized verification, bounded technical decisions, contract amendments
and oracle corrections on this PR. It excludes overriding Leader-reserved decisions,
weakening the approved architecture, accepting residual risk and bypassing review.
Each finding was treated as a falsifiable claim and re-derived from the sources
(V001/V006, ADR001 §11, ADR006 CT22.resume, ADR010 §10, ADR011 §§4–5, the CT17-P6
matrix, PostgreSQL row-lock conflict rules and Spring's event dispatch). This is
delegated technical approval, not personal Leader approval, a GitHub or CODEOWNER
review, or a test result.

### 21.2 Dispositions

| ID | Verified? | Root cause | Correction (sections) | Revision |
|---|---|---|---|---|
| R01 | **Valid.** §8 said publishing “must not throw” and banned plain listeners for all four events, and B7-O02 required “no plain `@EventListener`”, while §9.3 and ADR011 keep Adaptive's plain in-transaction listener whose failures must propagate and roll back the unit | §8 was written for notification subscribers but worded for every subscriber of the events | §8 scoped to Interaction with a two-path table; publishing rule restated per event; §9.3 cross-reference; B7-O02 corrected; B7-O14 added | Clarification: `CT28-SUBSCRIPTION-2026-11-15-r1` kept (semantics already as §9.3 stated) |
| R02 | **Valid.** Reminders used the unlocked discovery row's publication, Lesson, title, deadline and submitters; the CT27 listener used the event's producer-time snapshot. Counterexamples reproduced: hide after discovery, hide/rename/rebind after grading, submission after discovery, deadline edit before insert | No Assessment-owned admission read inside the creating transaction; “at creation time” overstated the guarantee | `lockNotificationState` (`FOR SHARE`, ADR010 §10 compatible) as the first row lock of each creating transaction; reminder steps and CT27 subscription use only the locked state; CT27 adds membership eligibility; §4.1 states the exact admission point and the remaining unlocked Course/Lesson/membership snapshots; B7-O03 extended, B7-O15 added | `CT10-REMINDER-READ`, `CT22-ACTIVE-LEARNERS`, `CT11-CREATE`, `CT11-REMINDERS`, `CT27-SUBSCRIPTION` → r2 |
| R03 | **Valid.** V006 posts and notifications reference `users`; PostgreSQL's FK checks take `FOR KEY SHARE`, which conflicts with `FOR UPDATE`. Reachable cycle: a question author later promoted to Administrator receives a reply notification while an Administrator mutation targets the replier (also reachable through multi-recipient notification inserts) | `FOR UPDATE` is stronger than needed: role and status are not key columns | `FOR NO KEY UPDATE` for every CT12.user row lock (§10.1); invariants re-derived (mutual conflict kept, re-check after locks); B7-O16 added, B7-O08 kept | `CT12-USER` → r2 |
| R04 | **Valid.** ADR001 §11 limits `instructorDisplayNames` to IDs from published-course results; the Administrator list includes hidden Courses and also omits names of Instructors whose role changed | Reuse of a scoped catalogue lookup | Course returns IDs only; Admin names the page through `userSummaries` (allowlisted caller, ≤ 100 page IDs, null for missing accounts); REST shape and ADR001 unchanged; B7-O17 added | `CT12-COURSE`, `CT01-USER-SUMMARIES` → r2 |
| R05 | **Valid.** CT22.resume writes `resume_lesson_id` and `last_visited_at` atomically, but the ceiling allowed only the pointer. Same-class check of every ceiling row against V001–V007 and its contracts found one more: CT23's in-place composition replacement may update `quiz_questions.position` under its deferrable uniqueness | Ceiling derived from current columns; the approved future column and an implementation-permitted update were missed | Both columns added; unlisted relations stated; B7-O18 added; nothing granted | `CT17-P6-PLAN` → r2 |

### 21.3 Post-fix adversarial check

Each original counterexample was replayed against the corrected text:

- **R01:** a fault in Adaptive now propagates (allowed and required); a fault in
  Interaction cannot reach the producer; B7-O02 no longer forbids ADR011's listener.
  No new contradiction: `RecommendationCreatedEvent` is published inside the Adaptive
  listener while the unit is active, so its after-commit subscription registers on the
  same unit and never runs after a rollback.
- **R02:** an Assignment hidden after discovery is re-read under the lock and skipped;
  a hide arriving after admission waits, so the reminder reflects the earlier,
  published state. The new lock adds no cycle: it is the creating transaction's first
  row lock; ADR010 writers lock the Assignment first; and after R03 no Administrator
  lock blocks the subsequent foreign-key checks. The remaining Course/Lesson/membership
  race is stated, not hidden, and is no wider than §11.2's retained rows.
- **R03:** the cycle needs a lock that conflicts with `FOR KEY SHARE`; `FOR NO KEY
  UPDATE` does not, and the role/status `UPDATE` itself also takes only that mode.
  Two mutations still conflict, so the last-Administrator protection is unchanged.
  Existing AU07 password-reset `FOR UPDATE` locks one row and waits on nothing that a
  CT12 mutation holds, so it serializes without a cycle.
- **Executed SQL-level evidence (PostgreSQL semantics only, not the application).**
  On a disposable container of the project's PostgreSQL image (17.11) with V001–V007
  applied and synthetic rows, the B7-O16 schedule was run with two sessions: with
  `FOR UPDATE` the reply transaction was aborted with `deadlock detected` (reply and
  notification lost) while the mutation committed; with `FOR NO KEY UPDATE` both
  committed (B locked, reply and notification present). With `lock_timeout` probes,
  a held `FOR NO KEY UPDATE` blocked `FOR NO KEY UPDATE` and `FOR SHARE` and admitted
  `FOR KEY SHARE`; a held `FOR SHARE` (R02's admission lock) blocked `FOR UPDATE` and
  admitted `FOR KEY SHARE` and `FOR SHARE`. Two transactions inserting the same two
  notification IDs with `ON CONFLICT (id) DO NOTHING` deadlocked in opposite order and
  both committed (two rows) in ascending order. The scripts stayed outside the
  repository and the containers were removed.
- **Same-class finding during the check (lock order):** with R02's shared admission
  lock, two application instances can process the same reminder candidate at once.
  If CT11.create inserted overlapping IDs in different orders, their unique-index
  waits could deadlock (one candidate would fail and be retried next run). CT11.create
  r2 therefore inserts in ascending `id` order (§4.1); B7-O01 covers it.
- **R04:** the hidden-Course row is named without calling the published-scope method;
  `userSummaries` remains bounded to rows being returned.
- **R05:** CT22.resume's atomic write fits the ceiling; no ceiling row now forbids a
  write that an effective contract requires.

Checkpoint count and dispositions are unchanged: twenty-seven checkpoints,
**DELEGATED_APPROVED**, with the revisions in §2. The r1 identifiers above remain
traceable as superseded open-PR drafts; none was integrated. Retained gates (§17)
are unchanged. Apart from the SQL-level demonstration above, no test was run: no
application code exists for these contracts, and B7-O14–O18 are specified, NOT_RUN.

## 22. IN20 Course admission refinement — 28 November 2026

**`CT12-COURSE-ADMISSION-2026-11-28-r1` — DELEGATED_APPROVED.** Authority:
Project Leader @chi-thanh-nguyen's explicit instruction **“CodeMastery — PR #312:
IN20 Full Implementation Completion, Bounded Upstream Remediation and Comprehensive
Final Self-Audit”** (28 November 2026, Asia/Ho_Chi_Minh) delegates investigation,
selection, documentation and implementation of the smallest necessary compatible
technical decisions, and upstream additions below approximately 30% of their Issue.
This is that delegated selection, not independent owner review or protected integration.

Section 12 requires a missing Course's 404 before body 400. The existing §10.2
mutation cannot be used for admission: it would hide a Course before validating the
body. Add exactly one public method in existing `CourseManagementService.java`:

```java
public void requireCourseForAdministration(UUID courseId);
```

Only caller: `AdminCourseService.requireCourseForHiding`. Both services require the
request's authenticated Administrator through CT01.identity. Course owns the plain
existence read and the missing 404; no entity, persistence handle or extra data crosses
the boundary. It acquires no row lock and makes no write. It participates in an existing
transaction, or performs a read-only snapshot when called alone. After successful
admission, the controller validates the body. The actual hide still starts Admin's
READ COMMITTED read-write unit, locks the root first and re-reads authoritative state.
A concurrent change or disappearance between admission and mutation is checked again;
admission is no promise of later mutation success.

Alternatives rejected: mutating to discover existence (invalid requests would write),
Admin persistence access (boundary violation), and parsing the body first (the known
400/404 precedence defect). This is one read-side responsibility within CO06.admin,
well below 30% of CO06's authoring, publication, structure and REST responsibilities.
It leaves `CT12-COURSE-2026-11-15-r2`, CT12.rest r1, §3.4, CT31, CT21, CT22 and
ADR005 §13.3 semantics effective. No general callback, new endpoint, publish/unhide
power, architecture change, migration or runtime privilege expansion is approved.

IN20 also uses the existing strict Jackson parsing pattern with duplicate detection
and trailing-token refusal; ordinary JSON escapes remain valid. Exact authenticated
GET-list and PUT-publication Security matchers leave stage-3 role checks to Admin.
The same exact operations bypass pre-security form decoding and receive no-store
headers on success and failure. These implement §§10.3/12 without changing them.
Test placement is recorded as `CT14-IN20-PLACEMENT-2026-11-28-r1` in the repository
structure record. Author-side tests do not constitute independent QA06 or owner review.

**Independent IN20 review, 28 November 2026.** Under the Project Leader's later
“PR #312: Comprehensive Independent Review” delegation, Codex independently
checked this admission/placement decision against incoming `3e914119` and the
effective contracts, retaining the exact scope and exclusions above. No additional
public seam, production capability, privilege or structural placement is selected.
The admission's sole production caller is now enforced at method level by an AST
regression, and real PostgreSQL contention proves the read succeeds while the
Course root is held elsewhere. The actual hide rechecks disappearance after
admission. Test-isolation/oracle findings and their self-verified fixes are recorded
in `docs/testing.md`; independent technical review of an incoming revision and
self-verification of this review's fixes do not confer human/CODEOWNER acceptance,
protected integration, whole QA06 acceptance or deployment authority.
