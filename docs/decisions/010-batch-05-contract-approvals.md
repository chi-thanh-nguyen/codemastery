# ADR010: Batch 5 Protected File and Assignment Contracts

## 1. Authority, baseline and provenance

**DELEGATED_TECHNICAL_APPROVAL; DELEGATED_APPROVED**, 12 November 2026
(Asia/Ho_Chi_Minh). Project Leader @chi-thanh-nguyen explicitly delegated technical
evaluation, selection and approval of all eligible Batch 5 and Batch 6 checkpoints,
and strictly necessary compatible prerequisite slices, to Claude Code in
**“CodeMastery — Batch 5 & Batch 6 Contract Approval, Two-PR Delivery & GitHub
Synchronization”**. The instruction excludes implementation, merge, protection
bypass, deployment, spending, resource grants and residual-risk acceptance, and
reserves amendments of effective approvals and Leader-reserved decisions to the
Leader. This record needs no such amendment. It is delegated technical approval,
not personal Leader approval, module-owner review or a GitHub approving review.
Independent review, required CI and protected integration of this record remain.

The same instruction gives the Leader's **implementation-start authorization**:
members responsible for the consuming Issues may begin eligible, properly scoped
work once the applicable contracts and actual start prerequisites are satisfied.
It does not change a contract, waive a gate, accept tests or authorize merge,
deployment or risk. Section 14 lists which start prerequisites remain.

Starting `origin/main`: **921801cef9919e5290f5d08f09699b227bc32c6b** (AS01
domain/entity commit of 11 November 2026). [PR #231](https://github.com/chi-thanh-nguyen/codemastery/pull/231)
merged ADR008 as `b6fa24b` on 10 November and [PR #233](https://github.com/chi-thanh-nguyen/codemastery/pull/233)
merged ADR009 as `fe73f9a` on 11 November 2026; neither recorded an approving
review. ADR001–ADR009 are integrated documents. Open PR #234 (CI workflow split)
is unrelated and untouched.

Planning sources under `~/codemastery-backlog-publication/contract-checkpoint-batching/`:
`run-20261108-135024/BATCH-05.md` and `APPROVAL-CHECKPOINT-REGISTER.md`, corrected by
`verification-20261108-151823/APPROVAL-ROADMAP.md`, `DEPENDENCY-TRACEABILITY.md`,
`registers/dependency-traceability.csv` and `registers/revised-plan.json` (resolved
through its `local_ids`, not array order). No newer run exists. Historical **CR-3 is not Batch 5**. The verified membership is
**CP-037–CP-046, ten checkpoints, three Issues**; nothing is rebucketed. The
Leader register (`~/codemastery-handoffs/current/leader-decisions.md`) was read:
LD-07 is handled in §8, LD-09 remains an integration-route question.

Inspected: root CLAUDE/AGENTS, context guide, CONTRIBUTING, CODEOWNERS, PR template;
requirements §§4.2/4.4/6; architecture §§4.2/4.4/7/8, US-08/10/12/13; repository
structure; ADR001 §§1–12, ADR002 CT18.minimum/CT38.core/CT15-SD r3 M2, ADR004,
ADR005 §§3/5/13, ADR006 §§5/6/8, ADR007 §§3/13, ADR008 §§5/6, ADR009 §§6–7;
data-model §11, setup variable scopes/CT16 records, deployment storage/ingress
notes; `application.yml`, `.env.example`, Compose and MinIO initializer policy;
the empty `FileStorage`, `S3FileStorage`, `AssignmentService`, `SubmissionService`,
`AssignmentGradedEvent` scaffolds, the AS01 `Assignment`/`Submission` records and
entities, `FileUploadField.tsx` and `httpClient.ts`; live Issues and all 157
Project #3 items. Known AI use: Claude Code (Anthropic) analysed sources and
authored this record; no AI runtime is added to the application.

## 2. Inventory, dependency order and dispositions

All ten checkpoints are **DELEGATED_APPROVED** with exactly the scopes below.
Approve §3's prerequisites first, then the table order.

| CP | Checkpoint / Issue | Revision | Section | Satisfied prerequisites |
|---|---|---|---|---|
| CP-037 | CT09.authorization / [#135](https://github.com/chi-thanh-nguyen/codemastery/issues/135) | `CT09-AUTHORIZATION-2026-11-12-r1` | §4 | CT01 r1, CT03 r1 predicates |
| CP-038 | CT09.lifecycle / #135 | `CT09-LIFECYCLE-2026-11-12-r1` | §5 | V002/V004 object-key columns |
| CP-039 | CT09.config / #135 | `CT09-CONFIG-2026-11-12-r1` | §6 | CT38.core; CP-033 informational |
| CP-040 | CT10.deadline / [#136](https://github.com/chi-thanh-nguyen/codemastery/issues/136) | `CT10-DEADLINE-2026-11-12-r1` | §8 | ADR008 §6 local clock seam |
| CP-041 | CT10.grading / #136 | `CT10-GRADING-2026-11-12-r1` | §9 | V004 grade/feedback constraints |
| CP-042 | CT09.interface / #135 | `CT09-INTERFACE-2026-11-12-r1` | §7 | CP-037 |
| CP-043 | CT10.replacement / #136 | `CT10-REPLACEMENT-2026-11-12-r1` | §10 | CP-040, CP-038 |
| CP-044 | CT09.rest / #135 | `CT09-REST-2026-11-12-r1` | §11 | CP-042, CP-038, CP-037 |
| CP-045 | CT10.rest / #136 | `CT10-REST-2026-11-12-r1` | §12 | CP-043, CP-041, CP-044; §3.1 |
| CP-046 | CT37.widget / [#152](https://github.com/chi-thanh-nguyen/codemastery/issues/152) | `CT37-WIDGET-2026-11-12-r1` | §13 | CP-044 |

These are the eleven verified relationships: ten HARD edges inside Batch 5 and
the INFORMATIONAL CP-033 → CP-039 edge. CP-033 r2 (ADR009) is effective; its
production values bind deployment, not these names. No Batch 1–4 checkpoint is a
HARD predecessor. CT27 (graded event) and CT11.reminders stay in Batch 7.

## 3. Necessary prerequisite slices

### 3.1 CT01.learner-names

**`CT01-LEARNER-NAMES-2026-11-12-r1` — DELEGATED_APPROVED**, [CT01 #18](https://github.com/chi-thanh-nguyen/codemastery/issues/18).
US-13 requires an Instructor to recognise who submitted; no approved read returns
learner names (CT01.identity has only `instructorDisplayNames`). Add, as a bounded
companion that leaves both Leader-approved CT01.identity methods unchanged, to
existing `modules/auth/application/service/UserAccountService.java`:

```java
public Map<UUID, String> learnerDisplayNames(Set<UUID> learnerIds);
```

Read-only bulk lookup of stored public display names for the requested existing
user IDs, immutable result; empty input → empty map; unknown IDs omitted; account
status and later role changes do not hide a name. No email, role, status, entity
or account reference. Storage failure → safe 500. Only Course application services
call it (ADR001 §12.6's Assessment → Course → Auth direction is kept), and only for
IDs that hold an Enrollment row in a Course whose ownership Course has just proven,
bounded by the caller's page. Assessment reaches it through this Course method in
existing `modules/course/application/service/EnrollmentService.java`:

```java
public Map<UUID, String> learnerDisplayNames(UUID courseId, Set<UUID> learnerIds);
```

Course applies CT03 `requireCourseOwnership(courseId)`, keeps only IDs with an
Enrollment row of any status in that Course (a submission can outlive membership),
asks Auth for those and omits the rest. At most 100 IDs per call. No REST user
directory, search or Administrator use is approved; Auth never calls Course or
Assessment. Auth @duyduyit and Course @TamiaoNguyen review.

### 3.2 CT18.b5-errors

**`CT18-B5-ERRORS-2026-11-12-r1` — DELEGATED_APPROVED**, [CT18 #20](https://github.com/chi-thanh-nguyen/codemastery/issues/20).
Uploads need two statuses outside CT18.minimum's family. For Batch 5 file
operations only: **413** when a file part exceeds §6's limit (title
`Content Too Large`) and **415** when a multipart operation receives another
request media type or a JSON operation receives multipart. Both use the shared
RFC 9457 ProblemDetail envelope, safe English detail, no rejected values and
`Cache-Control: no-store`. A non-ProblemDetail 413 from an ingress proxy remains
a generic transport failure for clients. No other status or exception file.

## 4. CT09.authorization

**Select backend-mediated streaming; reject pre-signed URLs.** The approved
deployment keeps MinIO private behind one HTTPS origin (CT17-2026-10-24-r1 P1
single-origin ingress and P2 private storage connectivity; ADR009 private
topology), and bearer tokens never travel in URLs (CT01 bearer-only transport). Pre-signed URLs would expose MinIO or add a proxy/origin/CORS/TLS path, would
place a reusable capability in a URL and could not re-check authorization (for
example after a learner leaves). Streaming costs backend bandwidth, bounded by §6.

Every upload or download authenticates (CT01), checks role and resource rules,
and completes all scoping **before** a file part is read or storage is called.
Implementation uses lazy multipart resolution (`spring.servlet.multipart.resolve-lazily`
or an equivalent mechanism) so that admission precedes reading the part; the
container size limit still bounds unread bodies. Administrator inherits nothing.

| Operation | Actor | Admission before reading the part or storage |
|---|---|---|
| File material create/replace | owning Instructor | CT03 `requireLessonOwnership(courseId, lessonId)`; the write itself re-checks inside ADR005's `withCourseConfigurationWrite` (§11) |
| File material download | admitted Learner or owning Instructor preview | Package-private CT03 `requireMaterialReadAccess(courseId, lessonId, materialId)` from `LearningMaterialService`, then a material row of type `file` in the same coherent snapshot |
| Submission create/replace | self Learner | Learner assignment visibility (§12): exactly one CT03 gate (ADR001 §12.6) — `requireLearnerLessonAccess` for a lesson-bound assignment, otherwise `requireLearnerCourseAccess` — plus a published Assignment in that Course |
| Own submission download | self Learner | The same visibility plus `submissions.user_id` = trusted self |
| Submission list/download/grade | owning Instructor | CT03 `requireCourseOwnership(courseId)`, Assignment in that Course, Submission in that Assignment |

CT03's failure precedence applies: 401, 403 wrong role or published non-owner or
missing membership, 404 hidden/missing/foreign scope (never revealing another
Course or learner). A database row whose object is missing is an integrity
failure (500), not 404. Caller-supplied owner, learner, key or filename is never
trusted.

## 5. CT09.lifecycle

**Object keys.** The backend generates every key; no client text enters it:
`materials/{uuid}` for file materials and `submissions/{uuid}` for submissions,
where `{uuid}` is a fresh lowercase canonical random UUID v4 per stored object.
Keys match `^(materials|submissions)/[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$`;
a stored key that does not match fails closed (500). Keys are never reused or
overwritten and never appear in REST, logs, frontend state or URLs.

**Accepted types.** Validation uses the filename extension (case-insensitive) and
the leading bytes; the client part `Content-Type` is ignored.

| Use | Extension | Stored/served media type | Required leading bytes |
|---|---|---|---|
| File material (architecture §4.2 slides/attachments) | `.pdf` | `application/pdf` | `%PDF-` |
| Submission | `.pdf` | `application/pdf` | `%PDF-` |
| Submission | `.zip` | `application/zip` | `PK\x03\x04` or `PK\x05\x06` |

No archive extraction, PDF parsing/rendering, code execution, malware scanning or
plagiarism check runs on the server. Each request carries exactly one file.

**Size.** A part of zero bytes is invalid (400). A part larger than §6's limit is
rejected (413). The limit is configuration, not a number chosen here.

**Filename.** `original_filename` stores the client's final name verbatim after
these checks: nonblank, at most 255 UTF-8 bytes, no `/`, `\` or control character
(U+0000–U+001F, U+007F), extension allowed for the use. Violations are 400; the
backend never silently renames. Downloads present it (§11); it is never part of a key.

**Write order and replacement.** Store the object first; then, in one database
transaction, insert or update the row that references the new key. After commit,
delete the superseded object; after rollback, delete the new object. Both deletes
are best effort: failure is logged safely and leaves an orphan. A row never
references an object that was not completely stored.

**Deletion.** No REST operation deletes file materials (CT21 approves no hard
delete) or submissions. Objects are deleted only after a replacement commit or as
rollback compensation. Hidden or left content keeps its objects; no retention or
expiry policy is introduced.

**Orphans.** An object under an approved prefix referenced by no row is an
orphan. Orphans are never served, because downloads require a row. The application
runs no sweeper or listing. Any reconciliation is a separately authorized operator
procedure under ADR009's write quiescence: a dry-run listing first, then deletion
only of keys unreferenced at the quiesced cut. Storage waste stays bounded by
failed operations times the size limit.

## 6. CT09.config

Reuse the existing names only; no new variable is introduced:
`STORAGE_ENDPOINT`, `STORAGE_REGION`, `STORAGE_BUCKET`, `STORAGE_ACCESS_KEY`,
`STORAGE_SECRET_KEY`, `STORAGE_PATH_STYLE_ACCESS` (`codemastery.storage.*`) and
`MAX_UPLOAD_SIZE` (`spring.servlet.multipart.max-file-size` and `max-request-size`).

- **Validation on consumption.** When the storage adapter bean is created (CT38.core
  validates optional integrations when consumed): endpoint is an absolute `http` or
  `https` URL with a host and no user information, query, fragment or path other
  than `/`; region nonblank; bucket a valid S3 bucket name (as the existing
  initializer checks); access and secret key present, non-placeholder; path-style
  an explicit boolean. Failure stops startup with a message naming the input,
  never its value.
- **Upload limit.** `MAX_UPLOAD_SIZE` is a Spring `DataSize` from 1 byte to
  2147483647 bytes (representation bound, not a recommendation). The application
  enforces it per file part as well. The `20MB` fallback in `application.yml` is
  current local configuration, not approved policy: deployment and acceptance
  require an operator-recorded explicit value. Because one variable also sets the
  request limit, a file within the multipart framing margin of the limit may be
  rejected with 413; a file above the limit is always rejected.
- **Identity.** The backend uses only the application identity (`STORAGE_*`), never
  `MINIO_ROOT_*`; it never creates buckets, changes policy or enables anonymous
  access. The initializer keeps provisioning the bucket-scoped
  Get/Put/Delete/List/Location policy, which suffices for §7.
- **Secrets.** No credential in source, documentation, logs or `VITE_*`. The
  frontend receives no storage setting.
- **Production values.** Endpoint, bucket, region, credentials and the upload
  limit for the VM come from CP-033 r2 custody and operator records. The ingress
  proxy body limit (currently the 1 MB default, see deployment) must be raised to
  at least the configured limit plus framing before an upload is exposed; that is
  a deployment change outside this record. Spooled multipart parts use the
  backend's temporary storage (tmpfs under the release restrictions), so the memory
  budget gate recorded in deployment also applies.

## 7. CT09.interface

Existing `backend/src/main/java/com/codemastery/infrastructure/storage/FileStorage.java`
is the shared storage port; existing `infrastructure/storage/s3/S3FileStorage.java`
is its only production adapter, using the existing AWS SDK v2 `s3` dependency.
Course `LearningMaterialService` and Assessment `SubmissionService` depend only on
the interface. No other module, controller or frontend reaches storage.

```java
public interface FileStorage {
    void store(String objectKey, InputStream content, long contentLength, String mediaType);
    StoredObject open(String objectKey);
    void delete(String objectKey);

    record StoredObject(long contentLength, String mediaType, InputStream content)
            implements AutoCloseable {
        @Override public void close();
    }

    final class FileStorageException extends RuntimeException { }
}
```

- `store` requires a §5-valid key, `contentLength` ≥ 1 and an §5 media type; it
  writes exactly `contentLength` bytes (a shorter or longer stream fails) and sets
  that media type as object metadata. It does not overwrite in a meaningful case,
  because keys are fresh random values.
- `open` returns length, media type and a stream that the caller closes; a missing
  object or provider failure raises `FileStorageException`.
- `delete` is idempotent: a missing object is success.
- Invalid arguments raise `IllegalArgumentException`; provider failures raise
  `FileStorageException` with a safe message. Both map to a safe 500, with no
  automatic retry.
- No list, presign, public URL, bucket or policy management, multipart-upload,
  versioning or encryption API is exposed. Streams are not buffered whole in memory.
- The adapter must use finite connection and request timeouts recorded in its
  configuration and qualified in tests; no value is selected here.

## 8. CT10.deadline

`assignments.deadline_at` is an exclusive end instant. A submission or
replacement is admitted only if **now < deadline_at**; equality is late.
`now` comes from a `java.time.Clock` injected into `SubmissionService` (UTC in
production, fixed in tests), the same local seam ADR008 §6 left to CT10; no shared
clock bean. It is sampled **after** the Assignment row lock is acquired (§10) and
truncated to microseconds, and becomes `submitted_at`. Browser time, request
receipt time and upload duration do not count. A cheap pre-check before storing
rejects an already late request with 409 to avoid useless uploads; the locked check
is authoritative. A sampled `now` earlier than an existing submission's
`submitted_at` is clock regression: safe 500 and rollback.

The owning Instructor may set any representable deadline, including a past one,
and may change it at any time. A change affects only later admissions; accepted
submissions remain valid. REST uses RFC 3339 instants; values with more than
microsecond precision are rejected (400) rather than rounded.

**LD-07 scope note.** This revision approves deadline *evaluation* only. It exposes
no deadline query to another module. Which contract owns the reminder query
(LD-07 options a/b) is decided with CT11.reminders in Batch 7, which must use
these semantics.

## 9. CT10.grading

`grade_percentage` is a decimal from 0 to 100 inclusive with at most two fraction
digits; more precision, strings, NaN or out-of-range values are 400 (never
rounded). Feedback is required with every grade, contains a non-whitespace
character, is stored verbatim and is shown as plain text with line breaks preserved.
`graded_at` is the server instant sampled after locks and truncated to microseconds;
it must not precede `submitted_at` (clock regression → safe 500, rollback).

Grading writes grade, feedback and time together, as V004 requires. The owning
Instructor may **regrade**: a later save replaces all three, with a new time.
There is no ungrade and no grade history. Grading is allowed whether or not the
assignment is published or past its deadline. Each grade request carries the
`submittedAt` the Instructor reviewed; a different current value is 409, so a
grade never lands on a replacement file the Instructor did not see.

A Learner sees grade, feedback and time only on their own graded submission.
Manual grading never publishes `QuizSubmittedEvent`, never calls Adaptive and
never changes mastery or progress. `AssignmentGradedEvent` and its notification
belong to CT27 (Batch 7); no event payload or publication timing is approved here.

## 10. CT10.replacement

One current submission per learner and assignment (V004 unique constraint).
Before the deadline and while ungraded, `PUT` replaces the file: new key, new
`original_filename`, new `submitted_at`, same row ID; the superseded object is
deleted after commit (§5). After the deadline: 409. After grading: 409, even
before the deadline. No learner withdrawal or deletion exists.

Every submission, replacement, grading and assignment edit locks the Assignment
row `FOR UPDATE` first, then the Submission row, at READ COMMITTED, and re-reads
publication, deadline and grading state under those locks. This one lock order
serializes concurrent first submissions, replacement versus grading, and deadline
edits versus admission without the Course root guard: assignments do not affect
adaptive invariants, so ADR005/ADR007 guards are neither used nor widened. CT03
admission remains a snapshot, as ADR001 §12.4 allows. No automatic retry.

## 11. CT09.rest

OpenAPI's Batch 5 Course paths are normative and **APPROVED_NOT_IMPLEMENTED**:

| Operation | Behavior |
|---|---|
| `POST /api/instructor/courses/{courseId}/lessons/{lessonId}/file-materials` | multipart `title` + `file`; creates a hidden `file` material; 201 `FileMaterialMetadata` |
| `PUT .../file-materials/{materialId}` | JSON `{title}`; 200 |
| `PUT .../file-materials/{materialId}/file` | multipart `file`; replaces the object (§5); 200 |
| `PUT .../file-materials/{materialId}/publication` | `PublicationWrite`; 200 |
| `GET /api/courses/{courseId}/lessons/{lessonId}/materials/{materialId}/file` | binary download for an admitted Learner (published material) or owner preview |

Course mutations use ADR005's unchanged `withCourseConfigurationWrite` guard, as
ADR006 §5 requires for every existing-Course authoring mutation; titles are trimmed
and nonblank. File material type and Lesson are immutable. The JSON metadata keeps
CT21's `FileMaterialMetadata` shape: no key, filename or URL. Bindings: existing
`CourseStructureController`, `CourseStructureRequests`, `CourseStructureResponses`
and `LearningMaterialService`; nested records only, no new file.

**Wire rules.** Multipart requests contain exactly the named parts; a missing,
duplicate or unknown part is 400. A successful download returns 200 with the full
body (no range support), `Content-Type` from §5's table derived from the stored
filename extension, `Content-Length`, `Content-Disposition: attachment;
filename="<ASCII fallback>"; filename*=UTF-8''<percent-encoded>` (RFC 6266/8187;
the fallback replaces non-ASCII, `"` and `\` with `_`), `X-Content-Type-Options:
nosniff` and `Cache-Control: no-store`. Storage is opened before the status is
written; a failure after streaming starts aborts the connection. Cross-origin
development needs `Content-Disposition` added to the CORS exposed headers.

**Frontend transport (the multipart/download handling CT16-FINAL deferred).** The
existing `frontend/src/shared/api/httpClient.ts` gains two additive capabilities
and keeps its JSON callers, addressing, session-generation, abort and safe-error
behavior unchanged:

- multipart: sends `FormData` with the bearer token, lets the browser set the
  boundary, expects the operation's JSON status, and never retries automatically;
- binary download: authenticated GET returning a `Blob` and the server filename
  parsed from `Content-Disposition` (`filename*` first); error bodies are parsed as
  ProblemDetail; 413 maps to the title `Content Too Large`.

Only 401 clears the session; 403/404/409/413/415 preserve it. No token in a URL,
no cookie, no persistent storage of files or names, no service worker.

## 12. CT10.rest

OpenAPI's Batch 5 Assessment paths are normative and **APPROVED_NOT_IMPLEMENTED**.
Paging uses the existing 0/20 defaults, 1..100 size bounds and `PageResponse`.

| Role | Operations |
|---|---|
| Owning Instructor | `POST /api/courses/{courseId}/assignments` (hidden, 201); `GET /owned-assignments` (page) and `/owned-assignments/{id}`; `PUT /owned-assignments/{id}` (full replacement); `PUT .../publication`; `GET .../submissions` (page); `GET .../submissions/{submissionId}/file`; `PUT .../submissions/{submissionId}/grade` |
| Self Learner | `GET /api/courses/{courseId}/assignments` (page) and `/assignments/{id}`; `PUT /assignments/{id}/submission` (multipart, 201 created or 200 replaced); `GET /assignments/{id}/submission/file` |

Assignment fields: title (trimmed, nonblank), instructions (nonblank, stored
verbatim, rendered under CT37.markdown), `deadlineAt`, optional `lessonId`.
Authoring uses CT03 `requireCourseOwnership`, or `requireLessonOwnership` when a
Lesson is referenced (ADR001 §12.6 U03), which proves the Lesson is in that Course;
Course is immutable. No assignment deletion.

Learner visibility: published Course, own ACTIVE/COMPLETED membership and, for a
lesson-bound assignment, a published Lesson (one CT03 gate, §4), plus a published
Assignment. Hidden or foreign assignments are 404. Leaving the Course ends current
access to the learner's own submission and grade, as for quiz results (ADR007 §11);
the records are retained. Learner lists are
ordered by `deadlineAt` then UUID; owner lists by title (`COLLATE "C"`) then UUID;
submission lists by `submittedAt` then UUID. Learner DTOs carry a derived
`open` flag (server now < deadline) and their own submission only; owner
submission rows carry `learnerDisplayName` from §3.1 (null when omitted) and never
an object key. Reads use one REPEATABLE READ read-only snapshot for admission,
rows and counts.

Bindings: existing `AssignmentController`, `AssignmentRequests`,
`AssignmentResponses`, `AssignmentService` (authoring, learner reads),
`SubmissionService` (submit, replace, download, list, grade), `AssignmentRepository`
with `AssignmentRepositoryImpl`, `AssignmentJpaRepository` and
`SubmissionJpaRepository` (private persistence for both tables). Nested immutable
records only; no new file, table, column or migration. All mutation success is
reported after commit.

## 13. CT37.widget

The shared widget is the existing `frontend/src/shared/components/FileUploadField.tsx`.
Its upload field stays presentational; the same file may also export the shared
download action. Feature API files (`assignmentApi.ts`, `gradingApi.ts`,
`instructorCourseApi.ts`, `learningApi.ts`) call the §11 transport. No new file
or dependency.

- **Upload.** One file. `accept` and the requirement text come from §5: “PDF file”
  for materials, “PDF or ZIP file” for submissions. Client checks (extension,
  non-empty) only improve feedback; the backend decides. States: idle, uploading
  (indeterminate progress), accepted (file name and server time), rejected (safe
  message). The control is disabled while a request is pending; there is no
  automatic retry; cancellation uses the request signal. After a network failure the
  outcome is unknown: the UI reloads the resource before offering another upload.
  Replacing an existing submission requires an explicit confirmation showing the
  current file name and time.
- **Messages.** 400 field errors are shown verbatim; 409 maps to “The deadline has
  passed.” or “This submission has been graded and can no longer be replaced.”
  (from the reloaded state); 413 to “The file is larger than the allowed size.”;
  other failures to the shared generic messages. The size limit is not displayed,
  because the frontend receives no configuration.
- **Download.** A button fetches the Blob through the shared transport, creates an
  object URL, triggers an anchor with the server filename, then revokes the URL.
  No inline preview, iframe or new tab; nothing is persisted.
- **Accessibility.** Keyboard operation, the existing `aria-describedby`
  requirement text and the existing `role="status"` region are kept; all text is English.

## 14. Errors, acceptance obligations and retained gates

Errors follow CT18.minimum plus §3.2: 401 authentication; 403 role, published
non-owner or missing membership; 404 hidden, missing or foreign scope; 400 malformed
UUID/JSON/page, multipart parts, filename, type, signature, grade, precision or
deadline; 413 size; 415 media type; 409 lifecycle (late, graded, stale grade
precondition); 500 storage, integrity, clock or transaction failure with rollback.
Authentication and role precede resource work; Course concealment precedes
descendants; shape validation precedes lifecycle decisions.

The following oracles are **specified, NOT_RUN**. Placement follows ADR008 §5:
`SubmissionServiceTest`, `AuthorizationIntegrationTest` and existing Course suites;
the dedicated storage success/rollback integration placement remains the recorded
gap that needs a bounded structural decision.

| Oracle | Required result |
|---|---|
| B5-O01 | Every operation in §4's table rejects wrong role, non-owner, missing membership, hidden Course/Lesson/material/assignment and foreign IDs before reading the part or calling storage. |
| B5-O02 | Keys match §5 and are fresh per store; no key appears in any response, log or URL. |
| B5-O03 | Type table: correct extension with wrong signature, wrong extension, zero bytes, path separators, control characters and 256-byte names are 400; client `Content-Type` is ignored. |
| B5-O04 | Above the configured limit → 413 ProblemDetail; at or below the limit minus framing → accepted. |
| B5-O05 | Store-then-commit: a failed transaction deletes the new object; a committed replacement deletes the old one; a failed delete leaves an orphan that is never served. |
| B5-O06 | Deadline: now = deadline − 1 µs accepted, now = deadline rejected; the time is sampled after the lock; clock regression rolls back. |
| B5-O07 | Barriers: concurrent first submissions give one row; replacement versus grading and deadline edit versus submission serialize on the Assignment row. |
| B5-O08 | Grade 0, 100, 99.99 accepted; 100.01, −0.01, 1.234 and strings rejected; blank feedback rejected; stale `submittedAt` → 409; regrade replaces all three fields. |
| B5-O09 | A graded or late submission cannot be replaced; no endpoint deletes files. |
| B5-O10 | Download headers match §11; disposition is always attachment; a missing object for an existing row is 500. |
| B5-O11 | Grading publishes no quiz event and changes no mastery or progress row. |
| B5-O12 | Widget: accessible states, confirmation before replacement, no retry, Blob download without a token in any URL. |

No previously effective approval is amended or superseded. ADR005's guard, ADR006
CT21 shapes and exclusions, ADR007's Assessment guards and ADR008's placement map
are reused unchanged. Alternatives rejected: pre-signed URLs (exposure, URL
capabilities, no re-check), an any-type submission policy (hostile content with no
benefit for the course), overwrite-in-place keys (partial-write and rollback risk),
an automatic orphan sweeper (deletion risk without quiescence) and a public
upload-policy endpoint (unnecessary surface).

**Retained gates.** Independent CODEOWNER and module review (Course @TamiaoNguyen,
Assessment @minhtan29042006, Auth @duyduyit, Frontend @heizseung, storage
@chi-thanh-nguyen), required CI and protected integration of this record are
**start prerequisites** for the consuming implementation, after which the Leader's
implementation-start authorization applies. Still required: SH05/CO09/AS09/FE
implementation and tests; qualified isolated MinIO test configuration; lazy
multipart and CORS configuration; runtime grants; ingress body limit and tmpfs
budget; an operator-recorded upload limit; and CT27 for the graded event.
Delivering hostile file content to a downloading user is not scanned for; that
residual risk is **PENDING_LEADER_RISK_ACCEPTANCE** before submission downloads are
publicly exposed. No deployment, exposure or risk acceptance is implied.

## 15. Preparation validation — 12 November 2026

Executed documentation checks are reported in the pull request: `git diff --check`,
OpenAPI parsing with duplicate-key rejection and reference resolution, relative
link/anchor resolution and a checkpoint/edge check against the verified register.
They are not runtime tests, external OpenAPI certification, storage qualification
or independent review.
