# ADR006: Batch 2 Contract Approvals and Retained Gates

## 1. Authority, baseline and exact scope

**DELEGATED_TECHNICAL_APPROVAL**, 9 November 2026 (Asia/Ho_Chi_Minh).
Codex independently selects the decisions below under the Project Leader
@chi-thanh-nguyen's explicit instruction **“CodeMastery — Batch 2 | Complete
Contract Approval, Documentation & Project Synchronization”**. This is **Codex's
delegated technical approval under that explicit Project Leader instruction**;
it is not personal Leader review, module-owner review or a GitHub approval.
Only the named Batch 2 scopes are approved. Implementation, migration execution,
deployment, residual-risk acceptance, protection bypass and merge are not authorized.
This record requires independent review, CI and protected integration into `main`.

**Additional explicit Leader decisions in this same task, 9 November 2026:**
the Leader confirmed “Verified Batch 2: CP-011–CP-018 (recommended)”; then
“Authorize reset-only email exception and record”; then “Authorize bounded resume
schema contract and read”. These are personal scope authorizations, not personal
technical review or residual-risk acceptance. §§7.1/7.2 record their exact limits.
Codex's delegated technical selection supplies the detailed contracts below.

Verified baseline: `main` **81806fc837d0619f1d60bf5f670b915e2a02eab5**.
[PR #228](https://github.com/chi-thanh-nguyen/codemastery/pull/228) merged there
on **9 November 2026 at 09:12:37 ICT**. All nine Batch 1 approvals and the
scope-specific amendments in [ADR005 §§13/15/16](005-batch-01-contract-approvals.md#16-r1r3-independent-review-corrections--9-november-2026)
are integrated, including N1 and the guarded-precondition/no-content handoffs.
They remain unchanged. No Batch 1 implementation is inferred from this merge.

The historical `run-20261107/CONTRACT_REVIEW_INDEX.md` groups work into six CR
groups. Its **CR-2 (22 checkpoints) is not Batch 2 of the eight-batch roadmap**:
its CT05 checkpoints moved to Batch 1, its runtime choices to Batch 6 and its
evaluation/data choices to Batch 8. This record follows the same verified
checkpoint register as ADR005: `contract-checkpoint-batching/run-20261108-135024/`
`BATCH-02.md` and `APPROVAL-CHECKPOINT-REGISTER.md`, corrected by
`verification-20261108-151823/APPROVAL-ROADMAP.md`, `registers/revised-plan.json`
and `registers/cr-index-to-register.csv`. These immutable external planning
artifacts identify scope and dependencies; they do not grant approval.
The exact membership is **CP-011–CP-018, eight checkpoints, four Issues**.
No old CR-2/runtime/evaluation checkpoint is newly approved here.

Sources inspected: root/nested instruction discovery, session context guide,
CONTRIBUTING/template/CODEOWNERS; canonical requirements §4.1–4.3, §6 and §13;
canonical architecture §4.2/4.3, US-01/04/10 and §10; repository structure;
ADR001 §11/12, ADR002 CT01/CT18/CT21/FE07/material records, ADR004 and effective
ADR005; current OpenAPI/data/adaptive/setup/testing documents; V001/V002;
Course access, membership and repository implementation; empty progress,
authoring/material/reset service inventories; live Issues and all 157 Project items.
Graphify's 5 November graph was used for navigation only and checked against source.

**Present-state integration confirmation — 9 November 2026:** PR #229 merged
as `912d4324263e19e80b87d899eae51c03842d592f` at **14:51:56 ICT**. The
preparation-time pending-integration statements below remain historical. This
confirms documentation presence on main, not independent review, implementation,
SMTP/runtime qualification, residual-risk acceptance or deployment readiness.

## 2. Checkpoint dispositions and approval order

| Order | Checkpoint / Issue | Decision revision | Disposition | Exact scope / dependencies |
|---|---|---|---|---|
| CP-011 | `CT22.progress` / [#43](https://github.com/chi-thanh-nguyen/codemastery/issues/43) | `CT22-PROGRESS-2026-11-09-r1` | **DELEGATED_APPROVED** | §3; CT01.identity, CT03 r1, FE07 membership; CT21 overlap reconciled in §5 |
| CP-012 | `CT22.resume` / #43 | `CT22-RESUME-2026-11-09-r1` | **DELEGATED_APPROVED** | §4 per-Course visit/resume plus §7.2 explicitly authorized recency schema/read |
| CP-013 | `CT37.embed` / [#152](https://github.com/chi-thanh-nguyen/codemastery/issues/152) | `CT37-EMBED-2026-11-09-r1` | **DELEGATED_APPROVED** | §6; existing Markdown unchanged; no CT09/widget approval |
| CP-014 | `CT02.delivery` / [#134](https://github.com/chi-thanh-nguyen/codemastery/issues/134) | `CT02-DELIVERY-2026-11-09-r1` | **DELEGATED_APPROVED** | §9; reset-only architecture exception expressly authorized in §7.1 |
| CP-015 | `CT21.authoring` / [#23](https://github.com/chi-thanh-nguyen/codemastery/issues/23) | `CT21-AUTHORING-2026-11-09-r1` | **DELEGATED_APPROVED** | §5 text/video and Course/Chapter/Lesson authoring; consumes §6 for video; files remain CT09 |
| CP-016 | `CT02.workflow` / #134 | `CT02-WORKFLOW-2026-11-09-r1` | **DELEGATED_APPROVED** | §9.1; approved CT02.delivery |
| CP-017 | `CT02.token` / #134 | `CT02-TOKEN-2026-11-09-r1` | **DELEGATED_APPROVED** | §9.2; approved workflow; NEW migration implementation still needs authorization |
| CP-018 | `CT02.rest` / #134 | `CT02-REST-2026-11-09-r1` | **DELEGATED_APPROVED** | §9.3; approved workflow and token contract |

Previously approved, retained: `CT22.membership` (FE07-2026-10-27-r1),
CT21.catalogue including FE07 extensions, CT37-MARKDOWN-2026-11-08-r1,
CT01.core/identity, CT03 r1 plus B1 r2/r3, CT18.minimum and every ADR005 amendment.
CT22.atomic, CT09, CT12, CT26, CT33 and CT37.widget are **outside this batch**.
No new approval is attributed to them. All eight checkpoints are now technically approved in their stated scopes. None
is implemented, independently reviewed or integrated by this documentation task.

The verified order is the table order. CT02 delivery → workflow → token → REST
is HARD (workflow also directly precedes REST). Embed → authoring applies to the
selected video slice. VERIFY-C4's conditional authoring → progress/resume overlap
is resolved by the joint visibility/ordering/locking rules below, before any
consumer relies on those operations. ADR004 resolves the former HC-U20 condition.

## 3. CT22.progress

**Selected contract.** Course owns the calculation and manual completion.
For a published Course and current Learner membership ACTIVE or COMPLETED,
the required set R is **all currently published Lessons**, across its Chapters,
in ascending Chapter.position then Lesson.position. Hidden Lessons are excluded;
Chapter has no visibility flag. Gaps in positions remain valid. No assessment,
material count or mastery threshold is added to the completion denominator.
An absent progress row means incomplete and not skipped. Completed count C counts
only rows in R with completed=true; mastery_skipped=true must imply completed=true.
No extra count is awarded for both flags. A zero-Lesson Course has C=0, total=0,
progressPercentage=0, isComplete=false. Otherwise isComplete means C=|R| and the
percentage is floor(100*C/|R|), using nonoverflowing integer arithmetic.
Course completion does not establish mastery. Canonical nextLessonId is the first
incomplete Lesson in R, or null when there is none. Mastery gaps never exclude R
members or prevent an otherwise admitted visit/manual completion.

`PUT /api/courses/{courseId}/lessons/{lessonId}/completion` accepts only
`{completed:true}`. It idempotently inserts/sets completed=true on the current
learner's existing Enrollment, preserving any true mastery_skipped flag. No false,
learner-supplied skip flag, learner ID or Enrollment ID is accepted. Manual
completion is monotonic; this slice provides no reset/uncomplete operation and
does not call Adaptive. A future approved skip producer must obey the same
representation, but **eligibility and atomic mastery/progress writes remain
CT07/CT08/CT22.atomic-owned and are not approved here**.

The Course-owned mutation locks the Course root first, using the same row as
membership and ADR005 configuration writers, then rechecks CT03 explicit-Course
Learner Lesson access and membership, then locks/updates the Enrollment/progress.
Run at READ COMMITTED; hold all locks until commit. This is private Course
persistence in LessonProgressService, **not** the Instructor-only public
withCourseConfigurationWrite callback. Never pass a Learner through that owner
boundary or extend its audience. Failures roll back the entire Course unit.
Same-Course leave, publication, reorder and completion serialize; read invariant
inputs after obtaining the root. No automatic mutation retry or new timeout.

After an actual manual completion and a change to R, recalculate ACTIVE/COMPLETED
for affected joined Enrollments inside that Course transaction. LEFT rows retain
their history and status. Publishing another incomplete Lesson can change
COMPLETED to ACTIVE; hiding the last incomplete Lesson can complete a nonempty
Course. Hiding every Lesson yields ACTIVE, not vacuous completion. This revisits
Course completion only, never mastery/evidence/recommendations. Existing FE07
join/rejoin remains **exactly** unchanged: LEFT → ACTIVE, no recalculation during
membership PUT, existing COMPLETED stays COMPLETED. Reads expose the stored
membership status separately from isComplete; after rejoining already completed
content these can differ until a qualifying completion/content-set mutation.
Do not silently rewrite that approved membership transition.

`GET /api/courses/{courseId}/progress` returns CourseProgress in OpenAPI, including
published Lesson flags in canonical order. `GET /api/me/course-progress` uses
existing page/size defaults/bounds (0/20, 1..100) and PageResponse semantics.
It returns one progress summary per current learner **ACTIVE**, published Course,
ordered by Course.title COLLATE C then UUID, with no per-Course client fan-out.
It supplies FE33's progress and next-Lesson fields without altering the closed
GET /api/me/courses payload. No cross-course adaptive ranking occurs.
Reads use REPEATABLE READ for coherent list/count/structure/progress assembly;
they never change completion, membership or resume.

Existing CourseProgressResponse, EnrollmentController and LessonProgressService
own the DTO/REST/use-case bindings. Course-only repository/adapter/entity inventory
handles storage; no cross-module repository access. Application entry points are
`progress(UUID courseId)`, `complete(UUID courseId, UUID lessonId)` and
`myCourseProgress(int page,int size)` on existing LessonProgressService; immutable
result records may be nested there and mapped into CourseProgressResponse.
No new public cross-module interface, service or file is approved.

**Rationale:** existing flags, visibility and membership already supply all inputs;
an explicit integer calculation adds no schema, scoring or assessment coupling.
The separate summary read preserves existing client decoders and FE07 semantics.

## 4. CT22.resume — per-Course visit and resume

A visit is an **explicit successful authenticated PUT**, after the Lesson view has
loaded, to `/api/courses/{courseId}/resume` with only `{lessonId:UUID}`. Merely
reading metadata, prefetching, completing a Lesson, or previewing as Instructor
does not record a visit. Course applies the same Learner Course/Lesson access,
Course-root-first transaction and recheck rules as §3, then replaces that self
Enrollment's resume_lesson_id and records §7.2's server visit time atomically.
Repeating the same visit leaves the pointer unchanged but refreshes its recency;
retrying can therefore affect ordering, and automatic retry is prohibited. Two visits follow
Course-lock acquisition order; the last committed successful write wins. No client
time, cross-Course target or cross-course adaptive ranking is accepted. The sole
new lifecycle column and account-level positional read are expressly authorized in §7.2.
Leave/rejoin retains the stored pointer, as FE07 requires.

`GET /api/courses/{courseId}/resume` is read-only and returns
`{courseId,resumeLessonId,nextLessonId}`. For an admitted Learner, resumeLessonId is
the stored pointer **only if it still resolves to a published Lesson in that Course**;
otherwise null. nextLessonId uses §3. The read never clears retained history or
manufactures a visit. First visit absent → null. A hidden/changed target → null;
the frontend may offer nextLessonId as “Start/continue in course”, but must not
label that fallback the last visited Lesson. No valid next Lesson → null.
PUT returns the same representation after commit. GET uses REPEATABLE READ.
Bindings are `resume(UUID)` and `visit(UUID,UUID)` on existing LessonProgressService,
EnrollmentController and CourseProgressResponse. No independent actorless caller.

The learner-level read and nullable server timestamp are approved in §7.2 under
the Leader's additional bounded schema authorization. Both require a NEW migration,
implementation and privilege qualification before the FE33 Continue tile can activate.

## 5. CT21.authoring

Instructor identity comes from unchanged CT01 facade; Course fixes instructorId
from trusted self at creation. No supplied owner/role/adaptive configuration is
accepted. New Course/Lesson/material is hidden. New Course has adaptive_enabled=false
and adaptive_settings=null. Chapters have no publication state. Ownership never
transfers; no Administrator inheritance. No hard delete, reparent, bulk import,
cross-Course move, file upload/object registration or Admin mutation is approved.
Existing anonymous catalogue/detail and CT40 structure DTOs stay byte-shape compatible.

All operations and exact closed JSON DTOs are in OpenAPI under
`/api/instructor/courses`. Owner list/read includes hidden drafts, chapters and
lessons in canonical order and relational text/video materials sorted by UUID
(V002 has **no material position**). No public syllabus or learner response gains
hidden content. File references may be identified by id/title/type/publicationStatus
in owner/learner relational metadata; no object_key, original_filename, download URL
or signed URL is exposed. Actual file payload and delivery remain CT09/CO09 gates.

| Operation family | Selected behavior |
|---|---|
| Course POST / collection GET / item GET,PUT | Create from title, description, nullable prerequisiteDescription; edit full metadata only; list self Courses with existing page/size and title/UUID order |
| Chapter POST / item PUT | Create/edit title; create appends after maximum sibling position (1 if empty); chapter parent immutable |
| Lesson POST / item PUT | Create/edit title and nullable contentMarkdown; append position, parent immutable; owner preview includes hidden descendants |
| Chapter/Lesson order PUT | Full sibling UUID array, each current sibling exactly once; set positions 1..N atomically; missing/duplicate/foreign ID → 400/404 as below |
| Course/Lesson publication PUT | Explicit `{publicationStatus:hidden|published}`; idempotent, no cascade; hidden parent still conceals children |
| Material POST / item PUT / publication PUT | Text or external_video only; exact exclusive payload; type and lesson immutable after create; metadata/content full replacement; visibility explicit |
| Learner Lesson GET | `/api/courses/{courseId}/lessons/{lessonId}` returns title, nullable contentMarkdown and published relational materials only; CT03 explicit-Course Lesson/material read predicates; does not record visit |

Publication has no invented minimum content/material/assessment count: empty
published Course is allowed and §3 defines its progress. Two material types remain
a Final delivery obligation, not a validation rule on every Lesson. Preserve text,
files and external video as approved types; selecting text/video authoring here
does not remove the planned CT09 PDF/file work or approve a concrete video asset.
Course titles/descriptions and child/material titles are trimmed/nonblank;
optional descriptive text is null or nonblank. No new arbitrary length limit.
Markdown/text content is validated as nonblank when present and stored/returned
verbatim, preserving indentation; only descriptive metadata is trimmed. Rendering
still uses CT37.markdown. Invalid video URL/type payload → 400 with safe field errors.

Every existing-Course authoring mutation uses unchanged ADR005
`CourseManagementService.withCourseConfigurationWrite` at READ COMMITTED, root
first, same outer transaction; no lower-level lock or queued write before entry.
Course creation authenticates active Instructor before insertion (new UUID).
Parent/ancestry/owner checks run again under the guard. Reorder atomically defers
existing sibling-position uniqueness then restores it before commit; no migration.
Appending beyond representable positive integer position → 409, no silent wrap.
No last-write validator is invented: metadata PUT is full replacement and
serialized last successful commit wins; clients must not automatically replay a
stale form. ADR005's settings-only If-Match/412/428 contract is not broadened.

Course/Lesson publication/set changes affect §3's required set and must reconcile
joined completion inside the same transaction. Graph mapping and readiness changes
reuse ADR005's guard and exact existing U08/CT40 boundaries. Publishing an unmapped
Lesson does not rewrite adaptive_enabled, synthesize a mapping or alter mastery;
it can make effective readiness false. Disabled/not-ready adaptive effects stay
suspended under ADR005. No history replay or threshold-policy change is introduced.
Publication event/subscription remains CT26; no event payload, recipient policy or
notification failure semantics is selected. Such integration stays gated separately.

Bindings: existing CourseManagementController/CourseStructureController,
CourseRequest/CourseStructureRequests/CourseStructureResponses; application
CourseManagementService (Course metadata/publication), CourseStructureService
(Chapter/Lesson operations), LearningMaterialService (relational material operations).
Their method names mirror OpenAPI operationIds; immutable command/result records
stay nested in these existing files. Existing owning repositories/adapters perform
private persistence. EnrollmentController/LessonProgressService keep learner work.
No duplicate cross-module facade, new production dependency or new file is approved.

## 6. CT37.embed

Approve YouTube's privacy-enhanced embedded player as the **single initial provider**.
Canonical stored URL is exactly `https://www.youtube-nocookie.com/embed/{videoId}`,
where videoId is a nonempty ASCII sequence of letters, digits, `_` or `-`.
Backend accepts only this form after outer ASCII-space trim. Reject any query,
fragment, userinfo, port, backslash, percent escape, control character, additional
path segment, scheme/host variation, playlist or raw HTML. Parse as URL and compare
the entire canonical serialization; host-prefix or substring checks are insufficient.
Frontend revalidates before embedding and constructs src from the validated ID and
fixed origin. Existing unsupported stored URLs render safe unavailable text; do not
follow redirects or proxy/fetch arbitrary URLs server-side. Provider approval does
not approve any specific third-party video, licence, provenance or seed row.

Use existing LessonContent.tsx / instructor MaterialForm.tsx. Initially show a
keyboard-accessible “Load external video” control naming YouTube; create no iframe,
thumbnail, provider script, preconnect or external request before activation.
On activation use a React-created iframe with title, controls, allowFullScreen,
`allow="encrypted-media; fullscreen"`, no autoplay/JS API/query parameters and
referrerPolicy=`strict-origin-when-cross-origin` (origin only across origins, no
bearer or protected URL parameters). No arbitrary embed HTML or Markdown HTML path.
If viewport cannot provide the provider's 200×200 minimum, offer the validated
YouTube watch link instead of a too-small player. External links use noopener
noreferrer; loading/playback never marks completion or mastery. Provider failure
leaves text/progress/resume available. No SDK, token, API key or new dependency.

Rationale: one fixed origin and canonical URL prevent arbitrary-frame/SSRF paths,
while explicit activation isolates third-party loading from existing Markdown.
The provider requires a Referer; suppressing it would break playback. Privacy-
enhanced mode is not a claim of zero provider data collection after activation.
Source references: [YouTube embed/privacy mode](https://support.google.com/youtube/answer/171780?hl=en),
[player parameters and viewport](https://developers.google.com/youtube/player_parameters),
[embedded-client identification](https://developers.google.com/youtube/terms/required-minimum-functionality).
Actual browser/player behavior, content rights and deployment frame policy remain
verification gates; this task performs no playback or media-rights acceptance.

## 7. Compatibility exceptions expressly authorized by the Leader

### 7.1 Reset-only email architecture exception

**ARCH-RESET-EMAIL-2026-11-09-r1 — LEADER_AUTHORIZED_BY_EXPLICIT_INSTRUCTION.**
The Leader answered **“Authorize reset-only email exception and record”** after
reviewing the single recommendation in this task. The exception permits only
transactional password-reset email to the account's stored email address. All
learning notifications stay in-app; other email/SMS/push remains excluded.
This is the only architecture scope amendment authorized here. Canonical originals
and AGENTS remain untouched; the architecture derivative carries an explicit
supersession notice rather than rewriting the historical original.

Evidence and necessity: canonical architecture §10 excludes email/SMS/push;
US-01 requires a time-limited link; mandatory requirements §4.1 requires recovery.
V001 supplies email/password hash, with no prior verified recovery factor or token
table. The existing PasswordResetService is empty. An anonymous response cannot
prove ownership; an authenticated-only flow fails a user who lost the credential;
an Administrator-issued link would require an additional identity-verification and
administration contract absent from CT01/CT12. No pre-issued offline factor exists.
This is a missing secure approved channel, not a claim that email is the only
possible design. The single recommended exception was selected because it minimally
extends the existing email identity/self-service screen without adding Admin or
Interaction ownership. It is now authorized; §9 provides Codex's technical approval.

Impacts: Auth delivery orchestration, AU07/FE06, new token persistence and eventual
SMTP adapter/configuration. A trusted HTTPS reset origin, provider/sender, credentials,
adapter/dependency/placement qualification and production abuse/capacity controls
are implementation/deployment gates. No actual provider, host, secret, new deployable
service, runtime grant or residual risk is accepted here. CT01's stateless JWT
contract is unchanged, including the explicit exposure gate in §9.4.

### 7.2 Bounded visit-time schema and learner-level resume read

**CT22-RESUME-SCHEMA-2026-11-09-r1 — DELEGATED_APPROVED**, following the Leader's
explicit answer **“Authorize bounded resume schema contract and read”** to the
concrete one-column recommendation in this task. This authorizes the additive
contract below beyond CT22's earlier published no-new-schema scope; it does not
authorize editing/executing a migration, changing privileges or implementation.

V002 has only Enrollment.resume_lesson_id; LessonProgress has no time. Adaptive
recommendations are not visits. Different visit histories produce identical rows,
so title/UUID/recommendation ordering cannot supply truthful global recency.
The necessary, smallest addition is **enrollments.last_visited_at timestamptz NULL**,
Java Instant, no database default or blanket creation/update timestamps. A NEW
versioned Flyway migration adds this column without updating old rows; preserve
V001–V006 and all existing constraints. Legacy NULL means unknown recency, never
a fabricated backfill. No separate visit entity, history log or shared clock bean.

On successful admitted visit, after acquiring the Course root and Enrollment lock,
capture PostgreSQL clock_timestamp() once and atomically persist pointer/time in the
same transaction. The time is server UTC, never a client value; rollback restores
both. Leave/rejoin and manual completion retain pointer/time, as FE07 history requires.
Reading or content prefetch never writes. Cross-Course concurrent visits are ordered
by recorded time, not client click or commit order; equal times use Course UUID
ascending, an explicit deterministic display tie-break with no adaptive meaning.

`GET /api/me/resume` returns `{position:null}` or
`{position:{courseId,lessonId,lastVisitedAt}}`, as defined in OpenAPI. Authenticate
current Learner first; use a REPEATABLE READ snapshot of self ACTIVE Enrollments,
published Courses and accessible published same-Course resume Lessons. Exclude
NULL time/pointer, LEFT/COMPLETED, hidden or foreign targets; select descending
last_visited_at then Course UUID ascending. If the most recent stored target is
inaccessible, select the next valid recorded visit; no valid candidate → null.
Do not replace it with canonical next or change retained pointers. This is positional
recency for CT16.learner-home, not cross-course adaptive ranking. Existing
LessonProgressService.myResume(), EnrollmentController and CourseProgressResponse
own the binding; no per-Course frontend requests or client ranking.

Verification: identical pointers with different visit times select the later one;
ties select lower Course UUID; legacy null and inaccessible/LEFT/COMPLETED rows are
excluded; no candidate returns explicit null; failed visit rolls back pointer/time;
two same-Course visits follow root-lock order. Clock synchronization, NEW migration,
runtime read/write grants and browser/producer delivery remain separately qualified.
The additional schema authorization changes no membership transition or existing DTO.

No unrelated/out-of-batch checkpoint is approved. No previously approved technical
clause is amended outside these two explicitly authorized, bounded exceptions.

## 8. Shared errors, acceptance oracles and remaining gates

All new operations reuse CT18.minimum ProblemDetail and PageResponse, JSON DTOs,
safe no-store protected responses and CT16 shared transport. Protected identity
admission precedes malformed UUID/body validation; preserve CT03's exact role,
hidden-resource, owner/membership and descendant precedence. 401 invalid/current
locked account; 403 wrong role or published non-owner/missing membership; 404 safe
missing/hidden/foreign scoped resource; 400 malformed/unknown/duplicate input or
invalid order permutation/payload; 409 representable-position exhaustion; safe 500
provider/transaction/invariant failure. Null internal arguments are caller errors,
not HTTP resource absence. No new exception class or status family; use existing
ResourceNotFoundException, AccessDeniedException and BusinessRuleException(400/409).
Public CT02 errors and enumeration/locked behavior follow §9, not the protected
resource 401/403/404 table. No 204 operation is introduced, avoiding any extension of B1's five-operation
no-content approval. These operations return their JSON representation after commit.

The following are **specified deterministic oracles, NOT_RUN against implementation**.
They are downstream requirements, not execution evidence or new CT14 placement approval.

| Oracle | Input / required outcome |
|---|---|
| B2-O01 | Published Lessons A/B/C and hidden D; A completed, B mastery-skipped+completed → C=2,total=3,66%,next=C; D never counted. Zero published → 0/0,0%,false,null. All three → 100%,true,null. |
| B2-O02 | Missing row versus false/false both incomplete; manual true repeated produces one row and preserves skip=true. Manual false/skip/foreign user fields → 400, no write/mastery call. |
| B2-O03 | Complete last Lesson then publish incomplete Lesson → ACTIVE; hide that Lesson → COMPLETED; hide all → ACTIVE; LEFT unchanged. Leave/rejoin still returns ACTIVE without recomputation. |
| B2-O04 | Barrier-based completion/leave and completion/publication races serialize on Course root; failure after write rolls back flags/status; another Course proceeds independently. No sleeps as ordering proof. |
| B2-O05 | First resume null; visit B then A → A; invalid foreign/hidden visit denied and pointer unchanged; later hiding A returns null pointer plus canonical next, without deleting stored A. Reads/prefetch/Instructor preview never write. |
| B2-O06 | Self ACTIVE/published paginated summary has exact counts and title/UUID order under a coherent snapshot; no foreign/LEFT/hidden/COMPLETED rows or N+1 frontend calls; existing My Courses payload unchanged. |
| B2-O07 | Owner creates hidden Course/Chapter/Lesson/text/video; published non-owner 403, hidden non-owner 404, Administrator 403, locked 401. Foreign child 404 without foreign IDs. Learner sees only published Lesson/material, never file payload. |
| B2-O08 | Full reorder swaps occupied positions atomically; duplicate/missing UUID 400, foreign child 404; append overflow 409; failure restores original order. Identity/parents and CT40 projection shape remain unchanged. |
| B2-O09 | Publishing unmapped Lesson shares B1 guard, can invalidate effective readiness and cannot change stored settings/history/mastery. Settings NOWAIT versus authoring respects B1 409/no-partial-write semantics. |
| B2-O10 | Accepted canonical nocookie URL; reject subdomain suffix/userinfo/ports/%/backslashes/query/fragment/script/raw iframe. No third-party request before activation; accessible control, minimum viewport, provider-error fallback; Markdown raw HTML stays literal. |
| B2-O11 | Global resume filters/ranks only recorded valid ACTIVE visits under §7.2; time/pointer rollback is atomic. CT02 oracles B2-O12–O16 below remain separate from existing Auth tests. |

Tests belong in already approved inventories only: Course service/progress unit
tests and existing Course/Enrollment integration suites where their approved scope
permits, plus frontend tests in existing owning suites. Any necessary new file or
expanded CT14 allocation needs separate approval; none is smuggled into this batch.
Real PostgreSQL constraints/locks/rollback, current Auth, browser transport/rendering,
provider playback, runtime database privileges and independent owner/QA review must
be verified before implementation acceptance. In particular CT17's current runtime
matrix does not grant these new Course writes: a later authorized privilege scope
is a deployment gate, not permission to run against a live database now.

Project Approval is checkpoint completeness, Readiness is slice eligibility, and
Status is workflow. Contract records stay pending protected integration. A defined
local implementation slice can be Bounded while whole-Issue Admin/file/event/atomic
or producer gates remain blocked; no consumer is made Ready merely by this record.
All original Issue content is retained except evidenced checkbox completion and
dated appended history. Codex authored these decisions and documentation with AI
assistance; no AI service is added to the application. Validation results are
reported in the PR; no implementation/integration results are claimed here.


## 9. CT02 recovery chain — approved after the explicit exception

`CT02-DELIVERY-2026-11-09-r1`, `CT02-WORKFLOW-2026-11-09-r1`,
`CT02-TOKEN-2026-11-09-r1` and `CT02-REST-2026-11-09-r1` are each
**DELEGATED_APPROVED** under §1, in that order. Auth owns the user, capability,
password change and email orchestration; there is no Interaction/Admin handoff.
This supplies new feature decisions, not a claim that an unused framework default
or earlier CT01 exclusion already approved them.

### 9.1 Request, delivery and failure isolation

The public request accepts the existing CT01-canonical email representation. For
every syntactically valid request return identical **202** JSON:
`{"message":"If an account matches, a password reset link will be sent."}`.
Lookup, account-status decisions, token issuance and SMTP occur off the HTTP response
path; do not run the worker inline in a transaction after the controller returns. Submit only the canonical email to an Auth-owned single-worker FIFO memory
queue, capacity **128 pending requests**; rejection/full queue returns the same 202.
Queue processing is best effort, not durable delivery: shutdown/failure may lose
an acknowledged request and the user can request again. No account-dependent
response latency/status/body, synchronous SMTP, email echo or delivery status.
No public delivery-status endpoint. Malformed request remains CT18 400.
No credentials on these public operations, and failure never clears another session.

Worker locks the Auth User row before token state. Unknown/locked account sends
nothing; known active account with an outstanding row can reissue at most once per
**PT1M** since that row's issued_at. A successful reset deletes the row; the next
request can then issue immediately, since no issuance-history retention is approved.
Suppressed requests produce no new token, expiry extension or email. A subsequent
eligible request replaces the sole capability for that user and invalidates the
previous link. This is an explicit coarse single-VM protection, not proven
production abuse resistance. Queue size, worker count and minimum interval are
selected bounded technical values; they are not empirical service-level claims.
Ingress rate/size/connection controls and abuse/load qualification remain required
before exposure, without silently adding a rate-limit library or 429 API policy.

Generate the capability, commit its digest/expiry, release the user lock, then send
the link. Never send a link before its transaction commits or hold DB locks over
SMTP. Dispatch failure does not roll back a committed capability or affect login,
account status or password. No automatic send retry/reissue (avoids accidentally
reviving superseded tokens); later user request follows PT1M. A delayed superseded
message may contain an unusable link; confirmation handles it generically. Never
claim successful delivery from 202. Emit only redacted aggregate failure metrics.

Use a fixed, configured, deployment-approved HTTPS frontend origin plus
`/reset-password#token={capability}`. Never derive origin from Host/Forwarded or
request parameters; no open redirect, arbitrary recipient, template HTML or callback.
Email is plain text identifying CodeMastery and the link/30-minute expiry. Provider,
sender and actual trusted origin are required operator inputs with no guessed value.
SMTP must use authenticated certificate-verified TLS and qualified finite network
deadlines; no plaintext fallback.
Implement the Auth-internal delivery port as a nested interface/immutable message
in existing PasswordResetService; a later approved infrastructure adapter and
dependency/file placement must satisfy it. It is not a public cross-module facade.
No SMTP library, adapter file or production binding is added by this PR.

### 9.2 Capability, storage and confirmation transaction

Use 32 cryptographically random bytes from Java SecureRandom, Base64url without
padding (exactly 43 ASCII URL-safe characters). Persist only SHA-256 of the decoded
32 bytes, never plaintext or a reversible token. Decode and re-encode must equal
the original 43-character token, rejecting noncanonical unused-bit variants; use constant-time digest comparison
where compared in application code. Random opaque capability is distinct from JWT;
no signing-key reuse. SHA-256 is suitable for this 256-bit random value, not passwords.
Passwords retain unchanged BCrypt/StrongPassword; no trim, normalization or truncation.

Select **PT30M** expiry explicitly after evaluating the existing dormant PT30M
setting: it limits link exposure while allowing manual email handling, without
adding another duration. This task approves that exact feature value; it is not
inferred from the default. Service-local UTC Clock supplies issuance/verification
time and can be replaced by a test clock in the existing service's constructor;
no shared Clock bean/CT39 decision. Valid iff issued_at <= now < expires_at.

Approved NEW-migration design, **not implemented**: `public.password_reset_tokens`
owned by Auth; columns id uuid NOT NULL (application UUID v4), user_id uuid NOT NULL,
token_digest bytea NOT NULL, issued_at timestamptz NOT NULL, expires_at timestamptz
NOT NULL. PK pk_password_reset_tokens(id); unique uq_password_reset_tokens_user(user_id)
and uq_password_reset_tokens_digest(token_digest); FK fk_password_reset_tokens_user
to users(id), ON DELETE/UPDATE NO ACTION, NOT DEFERRABLE; CHECK
ck_password_reset_tokens_digest octet_length(token_digest)=32; CHECK
ck_password_reset_tokens_expiry expires_at > issued_at. No defaults, raw capability,
recovery email copy or consumed-history column. One outstanding row per user.
Replace on eligible request; delete on successful reset (single use) in the same
transaction as password update. Expired rows never authorize anything and may be
deleted by a later same-user issuance; no scheduler or retention policy is invented.
Existing UserRepository/UserRepositoryImpl own private SQL and nested value types;
no new entity/repository/service file or cross-module data access is approved.

Confirmation first validates closed request syntax and the unchanged password
predicate, then resolves digest privately, locks User first and re-reads the matching
capability after locking. Check current active status and exact expiry there.
Missing, malformed, used, replaced, expired, future-issued or locked-account token
has the same safe 400 detail **“Reset link is invalid or expired.”**, with no token
or account field error. Do not consume a valid token on password validation failure.
Update password hash and delete that matching token atomically; rollback restores
both. Two confirmations serialize and exactly one can succeed. Request/confirm
races use the same User-first order; the final matching token must still exist.
Never unlock an account, change email/role or silently accept a stale token.
No auto-login, new JWT, refresh token or redirect is returned.

### 9.3 Exact REST and frontend handoff

`POST /api/auth/password-reset-requests` accepts `{email:string}` and returns the
202 acknowledgement above. `POST /api/auth/password-resets` accepts
`{token:string,password:string}` and returns 200
`{"message":"Password reset. Sign in with your new password."}` after commit.
Both are explicitly public (no bearer consumed), JSON-only, no-store, strict
closed DTOs with duplicate/unknown/typed-invalid fields rejected. CT18 400 validation
and safe 500 operational failures apply; neither account existence nor account
locking produces a distinguishable 401/403/404. No new error envelope or status.
AuthController/AuthRequests bind PasswordResetService.requestReset(email) and
resetPassword(token,password). Secret-bearing DTO toString/logging is redacted.

FE06 uses existing authApi/authTypes and ForgotPasswordPage/ResetPasswordPage through
unchanged CT16 shared public transport. Capture fragment into component memory and
immediately replace browser history with `/reset-password` before any API/provider
request; never put capability in query, router returnTo, persistent storage or logs.
Reset page loads no third-party resources and uses Referrer-Policy no-referrer.
Submit capability only in the POST body, disable repeat submission while pending,
show generic invalid/expired feedback, and require ordinary sign-in after success.
Reload loses the capability and requires reopening the email. No token in the DOM,
prefilled visible input or error message. Existing session-generation/abort checks
and no automatic mutation replay remain; these public operations never authenticate
the caller or invalidate an unrelated current session.

### 9.4 Existing contracts and actual exposure gates

The CT01 stateless JWT validation/logout contract is **unchanged**. Changing a
password does not revoke already issued bearer tokens; they remain subject to
the approved current-account/key/expiry checks and PT15M maximum lifetime.
The earlier personal logout risk acceptance is **not** extended to reset.
This consequence is explicitly **PENDING_LEADER_RISK_ACCEPTANCE** for public
recovery exposure: no risk is accepted by this technical approval or this PR.
Do not claim account-compromise recovery or global logout. A different revocation
guarantee would require separately authorized CT01 change and consumer review.
The present contract remains compatible; it does not silently change token policy.

Required before implementation acceptance/exposure: NEW Flyway migration approval
and execution authorization, minimal runtime privileges, SMTP adapter/dependency
and approved placement, configured trusted origin/sender/TLS secrets, provider
delivery/failure/timing and queue-load tests, current-account lock races, independent
security/module/QA review, implementation authorization and protected merge/CI.
No production service choice, credential, residual-risk acceptance or deployment
is inferred from technical approval.

| Oracle | Input / required outcome — implementation NOT_RUN |
|---|---|
| B2-O12 | Known/unknown/locked/full-queue request → identical 202, no account-dependent HTTP wait. Only eligible active worker sends after commit; <PT1M repeat changes nothing. SMTP failure/queue loss never changes password/account or exposes token. |
| B2-O13 | Token 32-byte entropy/43-character canonical encoding; only 32-byte digest stored. PT30M exact-before/equal/after boundary; malformed/replaced/used/locked all generic 400. Password predicate boundaries unchanged. |
| B2-O14 | Barriers: two confirmations → one success; hash-write failure rolls back deletion; request versus confirm rechecks under same User-first lock. No lock held over network send. |
| B2-O15 | Fragment erased before requests; no capability in URL query/history after capture/log/storage/Referer; no third-party reset resources; success returns no JWT and requires sign-in. Existing bearer behavior unchanged, no invented revocation guarantee. |
| B2-O16 | Missing/invalid sender/trusted-origin/TLS/adapter/grant qualifications prevent activation/exposure. No assumed PT30M/default/provider makes unconfigured recovery ready. |

Security mechanism reference: [OWASP recovery guidance](https://cheatsheetseries.owasp.org/cheatsheets/Forgot_Password_Cheat_Sheet.html).
It supports side-channel possession, indistinguishable requests and expiring single
use; the exact CodeMastery values/semantics above come from this delegated decision.

## 10. AU07 implementation record and gate status — 12–13 November 2026

This section records the AU07 implementation in
[PR #239](https://github.com/chi-thanh-nguyen/codemastery/pull/239) and the Project
Leader's bounded decisions about it. **It does not re-approve, supersede or modify any
CT02 revision.** `CT02-DELIVERY/WORKFLOW/TOKEN/REST-2026-11-09-r1` and
`ARCH-RESET-EMAIL-2026-11-09-r1` remain effective exactly as written in §§7.1 and 9.
The §9.1 sentence "No SMTP library, adapter file or production binding is added by this
PR" described PR #229; the approved adapter below answers the §9.1 adapter gate rather
than superseding it.

### 10.1 Authorization provenance

- **12 November 2026, relayed claim (historical, never confirmed as relayed).** The PR
  author @duyduyit relayed, in a Codex session, a Leader instruction authorizing AU07
  implementation, V007 with runtime grants, and the SMTP adapter, and stating that the
  reset-specific JWT risk had been accepted. No Leader-authored record of it was found.
  It is not an authority source. §10.2 replaces it for the items listed there; the
  risk-acceptance part remains unconfirmed.
- **13 November 2026, Leader remediation instruction.** In a Claude Code session, the
  Leader instructed remediation engineering of PR #239 and an independent technical
  assessment, explicitly not as approval of new contracts or risk acceptance. It is the
  authority for the corrections listed after the table in §10.3.
- **13 November 2026, Leader decisions.** The authority for §10.2.

### 10.2 Leader decisions — `AU07-IMPL-2026-11-13-r1` and `CT17-P6-PRIVILEGES-2026-11-13-r2`

**LEADER_APPROVED, 13 November 2026 (Asia/Ho_Chi_Minh).** Authority: the explicit
instruction of Project Leader and repository Owner @chi-thanh-nguyen, "CodeMastery —
PR #239 Leader Authorization, Contract Synchronization & PR Finalization", given in a
Claude Code session and recorded on
[Issue #180](https://github.com/chi-thanh-nguyen/codemastery/issues/180) from the
authenticated Leader account. These are personal Leader approvals of bounded technical
choices and implementation scope. They are not independent review, CI or merge evidence.

`AU07-IMPL-2026-11-13-r1` approves:

1. **AU07 implementation authorization:** the password-recovery implementation under
   the four CT02 r1 revisions, including the existing Auth service, REST endpoints,
   persistence integration and required tests.
2. **Flyway V007:** `V007__create_password_reset_tokens.sql` exactly as specified in
   §9.2, with no embedded runtime GRANT and no Flyway role placeholder. This approves
   inclusion in the repository and execution in controlled test environments (CI,
   Testcontainers and disposable qualification databases). It does **not** authorize
   execution against a shared or production database.
3. **SMTP adapter and structure:** Spring Mail (`spring-boot-starter-mail`,
   Boot-managed version) and the Auth-owned `SmtpPasswordResetEmailAdapter` under
   `modules/auth/infrastructure/email/`. Transport stays fixed: authenticated SMTP on
   port 587, mandatory STARTTLS, certificate and hostname verification, finite
   timeouts, and transactional reset email only (`ARCH-RESET-EMAIL-2026-11-09-r1`).
4. **Feature-scoped fail-closed behavior:** the backend stays operational without valid
   SMTP/recovery settings. The reset request then keeps the generic 202, issues no
   token and sends no email. Unrelated functionality and backend health are unaffected.

`CT17-P6-PRIVILEGES-2026-11-13-r2` approves a bounded AU07 extension of the CT17 r1
runtime matrix: `users` keeps SELECT, INSERT and adds UPDATE (password_hash);
`password_reset_tokens` gets SELECT, INSERT, DELETE. It approves no other privilege,
role elevation or change. The
[deployment record](../deployment.md#ct17-p6-privileges-2026-11-13-r2--au07-extension-leader_approved-13-november-2026)
holds the revision; the r1 record stays unchanged as history.

**Exclusions:** public recovery exposure, production provisioning, real SMTP
credentials, VM deployment, bypass of independent review, and merging PR #239. The
reset-specific JWT residual risk (§9.4) remains **PENDING_LEADER_RISK_ACCEPTANCE**;
CT01 is unchanged.

### 10.3 Implementation decisions and their status

| Decision | Gate addressed | Status |
| --- | --- | --- |
| Spring Mail with the Auth-owned adapter in `modules/auth/infrastructure/email/`. The adapter builds its own `JavaMailSenderImpl`, so there is no `spring.mail.*` binding and no Spring Boot mail sender or health contributor | §9.1 adapter, dependency and placement | LEADER_APPROVED (§10.2 item 3) |
| Fixed transport policy: authenticated submission on port 587; STARTTLS enabled and required; server-identity check; 5000 ms connect, read and write deadlines; no trust override or plaintext fallback; plain-text message | §9.1 TLS and finite deadlines | LEADER_APPROVED (§10.2 item 3) |
| Feature-scoped activation. Recovery is active only when `SMTP_HOST`, `SMTP_USERNAME`, `SMTP_PASSWORD`, `PASSWORD_RESET_EMAIL_FROM` (a bare address) and `PASSWORD_RESET_FRONTEND_ORIGIN` are valid and `PASSWORD_RESET_TOKEN_TTL` is PT30M. A valid origin is HTTPS with a lowercase host and has no path, query, fragment, userinfo or wildcard. Otherwise: identical 202, no lookup, capability or email; one variable-named startup warning and an aggregate counter. Confirmation, unrelated features and health are unaffected | B2-O16 | LEADER_APPROVED (§10.2 item 4) |
| `management.health.mail.enabled: false` | §9.1 failure isolation | Conforms (item 4) |
| V007 creates exactly the §9.2 table and grants nothing | §9.2; §9.4 migration gate | LEADER_APPROVED (§10.2 item 2) |
| Runtime privileges come only from the CT17 matrix | §9.4 minimal runtime privileges | LEADER_APPROVED (`CT17-P6-PRIVILEGES-2026-11-13-r2`) |
| Confirmation re-reads the capability after the User lock, without a token row lock, because every capability write holds that lock | §9.2 | Conforms; no contract change |
| Queue capacity 128, single worker, PT1M reissue and PT30M lifetime | §9.1/§9.2 | Unchanged CT02 r1 values |
| JSON-only operations (other media types get CT18 415); strict closed DTOs; generic 400 detail from a code-fixed `BusinessRuleException` public detail (no new exception class or status) | §9.3, §8 | Conforms |
| Tests in `AuthServiceTest` and `AuthorizationIntegrationTest`, plus required updates to existing `CourseRepositoryIntegrationTest` and the CT17 VM test scripts | §8 placement | Existing files only; covered by item 1 |

The 12 November draft also contained a V007 `GRANT … TO ${runtime-role}`, a global
lowercase `DB_USER` rule, required `spring.mail.*` settings and Compose variables that
every environment had to supply. These broke direct-SQL migration paths, the CT17
verification, application health and the VM release, and the 13 November remediation
replaced them as above.

### 10.4 Gates that remain open

- **Reset-specific JWT residual risk:** §9.4 remains PENDING_LEADER_RISK_ACCEPTANCE. CT01
  is unchanged and nothing is revoked; a previously issued bearer stays valid until its
  own PT15M expiry. An integration test records this behavior and does not establish a
  revocation guarantee. Public recovery exposure requires a separate Leader acceptance.
- **Independent review and integration:**
  - CODEOWNER reviews: Leader for `common/`, configuration, migration, `pom.xml` and
    docs; @tihh647-ui for `infrastructure/`; @TamiaoNguyen for
    `CourseRepositoryIntegrationTest`;
  - the eligible non-last-pusher approval required by the ruleset;
  - required CI on the final head;
  - protected merge, which is not authorized here.
- **Migration execution** on any shared or production database: separately Leader-reserved.
- **Deployment-only:**
  - a separately authorized SMTP binding (the VM release environment allowlist passes
    no SMTP variables, so recovery stays inactive there);
  - real provider delivery and failure qualification;
  - ingress abuse and load qualification;
  - the FE06 frontend handoff.
