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
review. ADR001–ADR009 are integrated documents. PR #234 (CI workflow split), open
when this record was prepared, merged on 11 November 2026 as `52bdb8d` with no
approving review; this branch was then rebased onto it without content change.
#234 touched no file of this record and no contract; §16 lists the later review
corrections.

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
CT18.minimum already keeps framework 405/415 with proper HTTP semantics and a safe
problem representation; it has no 413. For Batch 5 file operations only, this
slice adds **413** when a file part exceeds §6's limit (title `Content Too Large`)
and binds the existing framework **415** to one meaning: a multipart operation
receives another request media type, or a JSON operation receives multipart. It
does not narrow framework 415 elsewhere. Both use the shared
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
Implementation uses lazy multipart resolution (`spring.servlet.multipart.resolve-lazily=true`)
and §11's multipart binding, so that admission precedes reading any part; the
configured size limit bounds a part once it is read. Administrator inherits nothing.

| Operation | Actor | Admission before reading the part or storage |
|---|---|---|
| File material create/replace | owning Instructor | CT03 `requireLessonOwnership(courseId, lessonId)`; the write itself re-checks inside ADR005's `withCourseConfigurationWrite` (§11) |
| File material download | admitted Learner or owning Instructor preview | Package-private CT03 `requireMaterialReadAccess(courseId, lessonId, materialId)` from `LearningMaterialService`, then a material row of type `file` in the same coherent snapshot |
| Submission create/replace | self Learner | Learner assignment visibility (§12): exactly one CT03 gate (ADR001 §12.6) — `requireLearnerLessonAccess` for a lesson-bound assignment, otherwise `requireLearnerCourseAccess` — plus a published Assignment in that Course |
| Own submission download | self Learner | The same visibility plus `submissions.user_id` = trusted self |
| Submission list/download/grade | owning Instructor | CT03 `requireCourseOwnership(courseId)`, Assignment in that Course, Submission in that Assignment |

CT03's failure precedence applies: 401, 403 wrong role or published non-owner or
missing membership, 404 hidden/missing/foreign scope (never revealing another
Course or learner). A committed row whose object is missing is an integrity
failure (500), not 404, except for the concurrent-replacement case that §11's
download rule resolves first. Caller-supplied owner, learner, key or filename is
never trusted. §14 fixes the complete error precedence.

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
transaction, insert or update the row that references the new key. A row never
references an object that was not completely stored. The storage call and every
deletion run outside any database transaction. The file operation itself begins
the **outermost** transaction for the row write (ADR005's guard joins it for file
materials); called inside an existing transaction it fails with a safe 500 before
storage, because only the outermost owner can observe how the transaction ended.

The service classifies that ending into exactly one outcome and deletes only what
the outcome proves unreferenced:

| Outcome | Exact condition | Object deletions |
|---|---|---|
| **Confirmed commit** | The transaction manager reports completion as committed (Spring `TransactionSynchronization.STATUS_COMMITTED`) | Delete the superseded object (replacement only); keep the new one |
| **Confirmed not committed** | The failure happened **before the commit phase began**: in admission re-checks, locks, validation, the clock check, a statement or the explicit flush that ends the transaction body. No COMMIT was sent, and PostgreSQL never commits a transaction without one | Delete the new object; keep the superseded one, which the unchanged row still references |
| **Unknown outcome** | The commit phase began (Spring's `beforeCommit` callback ran) and committed completion was not observed: an exception during or after COMMIT, a lost acknowledgement, a connection failure, or a reported rollback after the commit request | Delete **neither** object |

The transaction body flushes all writes explicitly before it returns, so constraint
and statement failures fall into the second outcome rather than the third. A
status that Spring derives after a commit-phase exception (including
`STATUS_ROLLED_BACK` from a rollback attempted after a failed commit) is **not**
proof of rollback, in line with ADR005 §13.3. A database that reports an error for
COMMIT on a live connection has rolled back, but this contract does not require
distinguishing that case: keeping both objects costs at most one orphan, while a
wrong deletion would destroy a file that a committed row references. An unknown
outcome returns a safe 500, logs one safe record (operation, row ID, correlation ID,
never a key) and is resolved by the next ordinary read: the row then names
whichever key actually committed, and the other object is an orphan that is never
served. If `store` itself fails, the fresh key was never referenced, so one
best-effort `delete` of that key runs before the 500.

All deletions are best effort: a failed deletion is logged safely and leaves an
orphan. Deleting the superseded object immediately after a confirmed commit can
race with a download that read the old row; §11 defines that outcome. No
delayed-deletion queue or retention window is added.

**Deletion.** No REST operation deletes file materials (CT21 approves no hard
delete) or submissions. Objects are deleted only under the outcome table above.
Hidden or left content keeps its objects; no retention or expiry policy is
introduced.

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
import java.io.IOException;
import java.io.InputStream;

public interface FileStorage {
    void store(String objectKey, InputStream content, long contentLength, String mediaType);
    StoredObject open(String objectKey);
    void delete(String objectKey);

    record StoredObject(long contentLength, String mediaType, InputStream content)
            implements AutoCloseable {
        @Override
        public void close() throws IOException {
            content.close();
        }
    }

    class FileStorageException extends RuntimeException {
        public FileStorageException() {
            super("File storage operation failed.");
        }
    }
    final class ObjectNotFoundException extends FileStorageException { }
}
```

- `store` requires a §5-valid key, `contentLength` ≥ 1 and an §5 media type; it
  writes exactly `contentLength` bytes (a shorter or longer stream fails) and sets
  that media type as object metadata. It does not overwrite in a meaningful case,
  because keys are fresh random values.
- `open` returns length, media type and a stream that the caller closes. It raises
  `ObjectNotFoundException` **only** when the provider answers that the key does not
  exist in the configured bucket: the S3 error code `NoSuchKey` (HTTP 404). Every
  other failure raises plain `FileStorageException`: `NoSuchBucket`, `AccessDenied`
  or any other error code, a 5xx, a timeout, a connection or TLS failure, a
  malformed response, or a reported length or media type that does not match the
  §5 table. A provider failure is never reported as a missing object. Errors while
  reading the returned stream surface as `IOException` from the stream.
- `StoredObject.close()` delegates to the underlying `InputStream.close()` and
  propagates its checked `IOException`; it does not wrap it in a storage-provider
  rejection or delete any object. `store`, `open` and `delete` retain their unchecked
  exception contract. Download callers own the returned resource and use
  try-with-resources (or equivalent `finally` cleanup), including header preparation,
  copy failure and client disconnect. A close failure before response commitment
  maps to safe 500; after commitment it aborts the response. Never expose the
  exception message. If copying and closing both fail, preserve the copy failure
  as primary and the close failure as suppressed, as try-with-resources does.
- `delete` is idempotent: a missing object is success.
- Invalid arguments raise `IllegalArgumentException`; provider failures raise
  `FileStorageException` with a safe message. All of them map to a safe 500, with
  no automatic retry, except the single download re-read that §11 allows for
  `ObjectNotFoundException`.
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
receipt time and upload duration do not count. After the file part has passed
shape validation and before storing, an unlocked pre-check rejects an already
late or graded submission with 409 so that no object is written; the locked check
is authoritative (§14 gives the precedence).

**Monotonic version token.** `submitted_at` is the submission's version: the
`submittedAt` a grade request carries (§9) must name exactly one stored file. A
replacement is therefore admitted only if the sampled, truncated `now` is
**strictly later** than the existing row's `submitted_at`. `now` equal to or
earlier than it is a non-advancing or regressing clock: safe 500 and rollback,
with the new object deleted as compensation. The server never adjusts, increments
or fabricates a time. Concurrent replacements serialize on §10's locks, so the
second one compares against the first one's committed value. Tests with a fixed
clock must advance it between replacements; a test that does not advance it must
observe the 500.

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
`submittedAt` the Instructor reviewed; it is compared with the current value under
§10's locks at microsecond precision, and any difference is 409. Because §8 makes
every replacement strictly later, a grade never lands on a replacement file the
Instructor did not see. No separate version column is added.

**Opaque token on the wire.** `submittedAt` is serialized as an RFC 3339 UTC instant
carrying the stored microsecond value (Java `Instant` text, for example
`2026-11-20T09:15:02.123456Z`). Clients treat it as an **opaque string**: the grade
request echoes the exact string received in the Instructor's submission row,
never a value parsed into JavaScript `Date`, a number or any type with millisecond
precision and serialized again. Display formatting may parse a copy. The server
parses the echoed value and compares the instant exactly; a value with nonzero
digits below the microsecond is 400, any other different instant is 409.

A Learner sees grade, feedback and time only on their own graded submission.
Manual grading never publishes `QuizSubmittedEvent`, never calls Adaptive and
never changes mastery or progress. `AssignmentGradedEvent` and its notification
belong to CT27 (Batch 7); no event payload or publication timing is approved here.

## 10. CT10.replacement

One current submission per learner and assignment (V004 unique constraint).
Before the deadline and while ungraded, `PUT` replaces the file: new key, new
`original_filename`, a strictly later `submitted_at` (§8), same row ID; the
superseded object is deleted after a confirmed commit (§5). After the deadline: 409. After grading: 409, even
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

**Multipart binding** (every Batch 5 multipart operation, here and in §12). The
controller method declares no `@RequestPart`, `@RequestParam`, `MultipartFile`,
`Part`, `@ModelAttribute` or Bean Validation binding for the parts, because Spring
resolves such arguments, and therefore parses the body, before the method runs. It
receives the lazily resolved request (`MultipartHttpServletRequest` with
`resolve-lazily=true`) and passes the service an accessor that reads the parts only
when called. The service calls it only after §14 stage 3. Before that stage no
filter, interceptor, argument resolver or advice may call `getParameter*`,
`getParts`, `getPart`, `getInputStream` or `getReader` on the request; the
existing bearer filter reads only the `Authorization` header. The URL authorization
rule for these paths in the existing `SecurityConfig` is `authenticated()`, not
`hasRole(...)`: a role decided by the filter chain would answer 403 before the
415/400 of stage 2, contrary to §14. The role is decided by CT03 at stage 3.

**Early rejection of an unread body (transport limitation).** When admission
rejects a request whose body has not been read, the server answers without
consuming it and sends `Connection: close`. Tomcat drains a bounded amount of the
unread body and otherwise closes the connection; a client still sending a large
body can then see a connection reset before it reads the status. An ingress proxy
that buffers the request body delivers the status reliably. The client therefore
treats a network failure during an upload as an unknown outcome (§13), never as a
rejection with a known status, and never retries automatically.

**Wire rules.** Multipart requests contain exactly the named parts; a missing,
duplicate or unknown part is 400. The `file` part is raw binary, with
`Content-Disposition: form-data; name="file"; filename="..."`; a missing filename
is 400 under §5. `B5FileUpload` and `B5MaterialUpload` use an unconstrained file
property schema, and each multipart Media Type Object uses
`encoding.file.contentType: application/octet-stream`. No string schema,
`contentEncoding`, base64 conversion or JSON serialization applies to the bytes.
This follows [OpenAPI 3.1 §§4.8.14.3–5 and §4.8.15](https://spec.openapis.org/oas/v3.1.0.html#considerations-for-file-uploads).
The Encoding Object describes raw serialization, not the server's type admission:
the received part media type is still ignored in favour of §5's filename/signature.
PDF/ZIP download Media Type Objects likewise describe raw bodies without a string
schema. A successful download returns 200 with the full
body (no range support), `Content-Type` from §5's table derived from the stored
filename extension, `Content-Length`, `Content-Disposition: attachment;
filename="<ASCII fallback>"; filename*=UTF-8''<percent-encoded>` (RFC 6266/8187;
the fallback replaces non-ASCII, `"` and `\` with `_`), `X-Content-Type-Options:
nosniff` and `Cache-Control: no-store`. Storage is opened before the status is
written; a failure after streaming starts aborts the connection. Cross-origin
development needs `Content-Disposition` added to the CORS exposed headers.

**Download transaction and connection** (every Batch 5 download, here and in §12).
A download runs in phases, and no database transaction or pooled JDBC connection
is held while storage is called or the body is written:

1. **Read.** One read-only REPEATABLE READ transaction performs the complete
   admission (§4) and reads the row's key, filename and type, then ends. Its
   connection returns to the pool.
2. **Open.** `open(key)` runs outside any transaction.
3. **Write.** The status and the headers derived from the row read in phase 1 are
   written, then the stream is copied to the response and closed in a `finally`
   block, again outside any transaction. The controller is not transactional, and
   `spring.jpa.open-in-view` stays `false` as configured.

Authorization is decided at phase 1, as for any read: a membership or publication
change after phase 1 does not stop a body already being written, and the next GET
is admitted afresh. The headers always describe the object being written, because
both come from the same row read.

**Download versus concurrent replacement.** Phase 1 can name a key that a
replacement committed meanwhile and whose object §5 has already deleted. When
phase 2 raises `ObjectNotFoundException`, the service repeats phase 1 **exactly
once** in a new transaction, repeating the complete admission and reading the
current row. If the row now names a different key, it opens that key and serves it
with headers derived from the current row. If the key is unchanged, the row or
access is gone, or the second `open` fails in any way, the normal outcome applies:
404/403 from admission, otherwise 500. A missing object that the second read
explains as superseded is logged as a concurrent replacement, not as corruption.
Any other `FileStorageException` from `open` is a 500 at once, with no re-read: an
infrastructure failure is never retried as if it were a replacement race. The
re-read happens only before any status is written; once streaming has started, a
failure aborts the connection and the client may repeat the GET. The client never
retries automatically (§13); a repeated GET is an ordinary authorized download.

**Frontend transport (the multipart/download handling CT16-FINAL deferred).** The
existing `frontend/src/shared/api/httpClient.ts` gains two additive capabilities
and keeps its JSON callers, addressing, session-generation, abort and safe-error
behavior unchanged:

- multipart: sends `FormData` with the bearer token, lets the browser set the
  boundary, expects the operation's JSON status, and never retries automatically;
  append the selected `File` directly, or a `Blob` with the original filename
  explicitly supplied; never append file text, a JSON string or base64. The browser
  supplies each part's filename and may supply its own media type (§5 ignores it);
  a network failure and a 500 are reported to the caller as an **unknown
  outcome**, distinct from a rejection with a known status;
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
  automatic retry; cancellation uses the request signal. After a network failure or
  a 500 the outcome is unknown (§5 can keep an upload whose commit outcome was
  unknown): the UI reloads the resource and shows its current file before
  offering another upload.
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
- **Grading precondition.** The grading view (`gradingApi.ts`) keeps each
  submission row's `submittedAt` as the exact string from the response and sends
  that string unchanged as the grade request's `submittedAt` (§9); its TypeScript
  type is `string`, and it never passes through `Date`. After a 409 the view
  reloads the submission row before another grade can be saved.
- **Accessibility.** Keyboard operation, the existing `aria-describedby`
  requirement text and the existing `role="status"` region are kept; all text is English.

## 14. Errors, acceptance obligations and retained gates

Errors follow CT18.minimum plus §3.2: 401 authentication; 403 role, published
non-owner or missing membership; 404 hidden, missing or foreign scope; 400 malformed
UUID/JSON/page, multipart parts, filename, type, signature, grade, precision or
deadline; 413 size; 415 media type; 409 lifecycle (late, graded, stale grade
precondition); 500 storage, integrity, clock or transaction failure, with rollback
or, after the commit phase began, an unknown outcome handled by §5.
Authentication and role precede resource work; Course concealment precedes
descendants; shape validation precedes lifecycle decisions.

**Deterministic precedence.** For every Batch 5 operation the first failing stage
decides the response, and later stages do not run:

1. **401** authentication (CT01). The filter chain decides only authentication for
   these paths (§11, `authenticated()`).
2. Request-level checks that involve no resource lookup and no body part:
   **415** request media type and **400** malformed path UUID or page parameter.
3. Admission (§4): CT03 stages 2–5 (**403** role, **404** Course concealment,
   **403** membership or ownership, **404** descendants), then this record's
   resource scope (file-type material, Assignment visible to the Learner, Assignment
   and Submission in scope) → **404**. Nothing is read from the body before this stage.
4. Body shape: JSON **400**; for multipart, **413** when a part exceeds the limit
   while it is parsed (the other part checks cannot run), then **400** for parts,
   title, filename, extension, zero bytes and signature.
5. Unlocked lifecycle pre-check (submission `PUT` only, before storage): **409**
   late or graded; no object is written.
6. Storage write: **500** on failure; no row changes; one best-effort delete of
   the unreferenced fresh key (§5).
7. Locked authoritative checks (ADR005's guard for file materials, §11; the
   Assignment and Submission locks, §10): **403/404** when the re-checked
   ownership, ancestry or Assignment publication no longer holds, **409** late,
   graded or stale grade precondition, **500** clock, integrity or transaction
   failure. Every failure at this stage happens before the commit phase, so the new
   object is deleted (§5, confirmed not committed).
8. Commit: confirmed commit → success, superseded object deleted; unknown outcome
   → **500**, both objects kept (§5).

So a late request with an invalid file is 400, a late request with a valid file
is 409 without a storage write, and a request that becomes late while uploading
is 409 at stage 7.

The following oracles are **specified, NOT_RUN**. Placement follows ADR008 §5:
`SubmissionServiceTest`, `AuthorizationIntegrationTest` and existing Course suites;
the dedicated storage success/rollback integration placement remains the recorded
gap that needs a bounded structural decision.

| Oracle | Required result |
|---|---|
| B5-O01 | Every operation in §4's table rejects wrong role, non-owner, missing membership, hidden Course/Lesson/material/assignment and foreign IDs before reading the part or calling storage. |
| B5-O02 | Keys match §5 and are fresh per store; no key appears in any response, log or URL. |
| B5-O03 | Type table: correct extension with wrong signature, wrong extension, zero bytes, path separators, control characters and 256-byte names are 400; client `Content-Type` is ignored. Precedence follows §14 stage by stage: an unauthorized request with a bad or oversized file gets its admission status, a late request with an invalid file gets 400, a late request with a valid file gets 409 with no storage call. |
| B5-O04 | Above the configured limit → 413 ProblemDetail; at or below the limit minus framing → accepted. |
| B5-O05 | Store-then-commit, with faults from test-scoped bean overrides only: (a) a failure before the commit phase (re-check, lock, clock, statement, flush) leaves the row unchanged and deletes the new object; (b) a confirmed commit of a replacement deletes the superseded object; (c) a COMMIT that the database executes but whose acknowledgement is lost (a connection proxy that runs the commit and then throws) returns 500, deletes **neither** object, and the next read serves the new file; (d) a commit-phase exception after which the database did not commit returns 500, deletes neither object, keeps the old row and file, and leaves the new object as an orphan; (e) a failed `store` leaves no row change and attempts one delete of the fresh key; (f) a failed delete leaves an orphan that is never served; (g) a file operation called inside an existing transaction fails 500 before storage. |
| B5-O06 | Deadline: now = deadline − 1 µs accepted, now = deadline rejected; the time is sampled after the lock. A replacement with now equal to or earlier than the stored `submitted_at` (fixed clock not advanced, or regression) is 500 with rollback and the new object deleted; an advanced clock gives a strictly later `submitted_at`. |
| B5-O07 | Barriers: concurrent first submissions give one row; replacement versus grading and deadline edit versus submission serialize on the Assignment row. |
| B5-O08 | Grade 0, 100, 99.99 accepted; 100.01, −0.01, 1.234 and strings rejected; blank feedback rejected; stale `submittedAt` → 409, including a grade that names the value before a replacement committed between the Instructor's read and the grade; regrade replaces all three fields. For a stored `submittedAt` with nonzero microseconds (`…T09:15:02.123456Z`), the exact echoed string is accepted, the millisecond-truncated `…T09:15:02.123Z` is 409 and a value with nonzero sub-microsecond digits is 400. |
| B5-O09 | A graded or late submission cannot be replaced; no endpoint deletes files. |
| B5-O10 | Download headers match §11; disposition is always attachment. A download whose snapshot names a key superseded and deleted by a committed replacement re-admits once and serves the current file; a missing object whose row still names it is 500; no second retry occurs. A provider failure from `open` (5xx, timeout, `AccessDenied`, `NoSuchBucket`) is 500 with **no** re-read, and only `NoSuchKey` raises `ObjectNotFoundException` in the adapter. |
| B5-O11 | Grading publishes no quiz event and changes no mastery or progress row. |
| B5-O12 | Widget: accessible states, confirmation before replacement, no retry, Blob download without a token in any URL; a network failure or 500 on upload reloads the resource before another upload is offered; the grade request body carries the received `submittedAt` string byte for byte, microsecond digits included. |
| B5-O13 | Multipart binding: a body whose parts cannot be parsed (malformed boundary) or whose file part exceeds the limit returns the admission status (401/403/404) for an unauthorized or out-of-scope caller, and 400/413 only for an admitted one; a Learner on an Instructor upload path with a wrong request media type gets 415, not 403; a test multipart resolver that records part access observes no access before stage 3. A controller using `@RequestPart`, `MultipartFile` or a `hasRole` URL rule fails these cases. |
| B5-O14 | Downloads hold no transaction and no pooled connection while the body is written: with the test pool limited to one connection and a download paused mid-body by the test client, another request that needs the database completes; phase 1 runs read-only at REPEATABLE READ. |
| B5-O15 | Compile the complete §7 interface, including imports and nested types, with Java 25. A close-tracking InputStream is closed on successful copy, header-preparation failure, copy failure and client disconnect; close IOException propagates as checked, never as ObjectNotFoundException. If copy and close both fail, copy is primary and close is suppressed. Closure never deletes an object; failure before/after response commitment follows §7. Use applicable existing backend suites; real storage integration retains the placement gap above. |
| B5-O16 | All three upload operations use a raw file property and an Encoding Object for application/octet-stream, with no textual encoding. Serialize a browser FormData File, and a Blob with an explicit original filename, containing PDF/ZIP leading bytes plus NUL and non-UTF-8 bytes: the parsed part has exactly the original bytes and filename. A string append, base64/JSON/text conversion, or Blob without the required filename fails the client oracle; a server part without a filename is 400. The title remains a text part. Raw download responses preserve the same bytes. Verify the transport in existing httpClient.test.ts and widget suites; this does not replace real Tomcat/MinIO qualification. |

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

## 16. Independent review corrections — 12 November 2026

An independent technical review of PR #235 at `de3df67` raised four non-blocking
findings. Under the Leader's explicit remediation instruction for PR #235/#236,
they are corrected **before integration and within the same r1 revisions**: no
revision was ever effective, so no effective approval is amended. Checkpoint
membership, IDs, scopes and every other clause are unchanged.

| Finding | Correction | Clauses |
|---|---|---|
| F-06 | A replacement must sample a strictly later `submitted_at`; equal or earlier is 500 with rollback, so `submittedAt` is an unambiguous grade-precondition token without a new column | §§8–10, B5-O06, B5-O08 |
| F-09 | One staged error precedence; the unlocked 409 pre-check runs after shape validation and before storage | §8, §14, B5-O03 |
| F-10 | A download racing a committed replacement re-admits once and serves the current file instead of reporting corruption | §4, §5, §11, B5-O10 |
| F-11 | PR #234 merge recorded; §3.2 states that CT18.minimum already had framework 415 and only 413 is new | §1, §3.2 |

The new oracle clauses are specified and NOT_RUN. This section records documentation
corrections only; it is not independent review, CI evidence or Leader approval.

## 17. Final technical review remediation — 13 November 2026

A second independent technical review of PR #235 at `19ce6e0` found one approval-
blocking defect and four lower findings in this record. Under the Leader's explicit
instruction **“CodeMastery — Batch 5 & 6 Final Technical Findings Remediation”**
they are corrected **before integration and within the same r1 revisions**, as in
§16: no revision was ever effective, so no effective approval is amended. Checkpoint
membership, IDs and scopes are unchanged; no schema, endpoint, dependency or
architecture change is introduced.

| Finding | Severity | Correction | Clauses |
|---|---|---|---|
| F-B5-01 | P2 | Three transaction outcomes (confirmed commit, confirmed not committed, unknown); objects are deleted only when the outcome proves them unreferenced; an unknown commit outcome keeps both objects, consistent with ADR005 §13.3; the file operation owns the outermost transaction | §5, §13, §14 stages 6–8, B5-O05, B5-O12 |
| P3-3 | P3 | `ObjectNotFoundException` only for S3 `NoSuchKey`; every other provider failure is plain `FileStorageException`; the download re-read runs only for the former | §7, §11, B5-O10 |
| P3-5 | P3 | `submittedAt` is an opaque RFC 3339 token echoed byte for byte; never round-tripped through JavaScript `Date` | §9, §13, B5-O08, B5-O12 |
| P3-6 | P3 | Downloads run read → open → write with no transaction or pooled connection held during storage calls or body writing | §11, B5-O14 |
| P3-12 | P3 | No eager part binding; lazy resolution with a deferred part accessor; `authenticated()` URL rule; early-rejection transport limitation | §4, §11, §14 stage 1, B5-O03, B5-O13 |

The new and changed oracles are specified and NOT_RUN. This section records
documentation corrections only; it is not independent review, CI evidence or
Leader approval.

## 18. Comprehensive independent review remediation — 13 November 2026

Under the Project Leader's explicit **“CodeMastery — Execute All Independent
Review Findings Remediation”** instruction, Codex corrects N-B5-01 and N-B5-02
from the independent review of `6172c01`. These are pre-integration corrections
within the existing r1 revisions; checkpoint membership, effective upstream
contracts, storage operations, accepted types and authorization are unchanged.

| Finding | Correction | Verification obligation |
|---|---|---|
| N-B5-01 (P2) | Complete Java 25 declaration; close delegates to the stream with checked IOException and defined caller cleanup/failure semantics | §7, B5-O15 |
| N-B5-02 (P2) | Raw multipart file schemas plus per-operation Encoding Objects, filename-preserving FormData, and raw download bodies | §11, B5-O16 |

No feature implementation, dependency, migration, exposure or risk acceptance is
introduced. Isolated declaration/serialization probes are distinguished from the
future application oracles in delivery evidence; this is remediation
self-assessment, not independent approval.

Executed for this correction: Java 25 compilation of the complete normative
interface and an isolated close-tracking/suppressed-exception probe; Chromium
FormData File/Blob serialization with byte/filename readback and negative text/
unnamed-Blob cases; duplicate-key rejection, all 679 OpenAPI reference occurrences
resolved, and an exact semantic allowlist proving only the intended two file
schemas, three multipart encodings and five raw response representations changed.
All passed. Application/Tomcat/MinIO integration oracles remain NOT_RUN.
