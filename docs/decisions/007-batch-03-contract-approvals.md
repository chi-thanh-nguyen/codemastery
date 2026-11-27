# ADR007: Batch 3 Quiz Contracts and Submitted Evidence

## 1. Authority, baseline and provenance

**DELEGATED_TECHNICAL_APPROVAL; DELEGATED_APPROVED**, 9 November 2026
(Asia/Ho_Chi_Minh). Project Leader @chi-thanh-nguyen explicitly delegated technical
selection and approval to Codex in **“CodeMastery — Batch 3 | Complete Contract
Approval, Dependency Reconciliation & GitHub Synchronization”**. That instruction
covers all eligible verified Batch 3 checkpoints and strictly necessary compatible
prerequisite slices. It explicitly excludes implementation, architecture replacement,
residual-risk acceptance, deployment, protection bypass and merge. It also reserves
amendments to earlier effective approvals to the Leader. No such amendment is needed.
This is delegated technical approval, not personal Leader or module-owner review.
Independent review, required CI and protected integration remain outstanding.

Starting `origin/main`: **912d4324263e19e80b87d899eae51c03842d592f**.
[PR #229](https://github.com/chi-thanh-nguyen/codemastery/pull/229) merged at that
commit on **9 November 2026, 14:51:56 ICT**. [PR #228](https://github.com/chi-thanh-nguyen/codemastery/pull/228)
merged as `81806fc837d0619f1d60bf5f670b915e2a02eab5` at 09:12:37 ICT that day.
No later main commit was present at the starting fetch. ADR005 and ADR006 are
integrated documentation; their implementation, review and exposure gates are distinct.

Planning sources under `~/codemastery-backlog-publication/contract-checkpoint-batching/`:
`run-20261108-135024/BATCH-03.md`, `APPROVAL-CHECKPOINT-REGISTER.md`, and
`verification-20261108-151823/APPROVAL-ROADMAP.md`, `DEPENDENCY-TRACEABILITY.md`,
`registers/revised-plan.json`, `registers/cr-index-to-register.csv`. These identify
the existing corrected eight-batch roadmap, not technical approvals. Historical
**CR-3 is not Batch 3**. CP-001 is ADR004 governance; CP-002–010 are ADR005;
CP-011–018 are ADR006; **only CP-019–028 belong to Batch 3**. Nothing is rebucketed.
The original/revised register agrees on these ten members; corrected conditional
HC-U20 edges are resolved by ADR004, not new upstream approvals.

Inspected sources: root AGENTS/CLAUDE/context guide, CONTRIBUTING, CODEOWNERS and
actual PR template; canonical architecture and repository-tree DOCX; requirements
§§4.4/5.3/6/13; ADR001 §§1–12, ADR002 bounded contracts/dispositions, ADR003 retained
risk exclusions, ADR004, effective ADR005 §§3/5/6/13/15/16 and ADR006; OpenAPI,
data-model §11, adaptive/setup/testing records, V004 and current Course predicates;
live Issues and all 157 Project items/field options. The 53 Assessment Java files
are all empty at baseline. Their filenames establish placement, not functionality.
Known AI use: Codex authored these decisions/documentation; no AI runtime is added.

## 2. Inventory, dependency order and dispositions

All rows are **DELEGATED_APPROVED**, with only the named scopes. Each remaining
decision described in the register is resolved by its normative section below.
Approve the additional prerequisites in §3 first, then the following order.

| CP | Checkpoint / Issue | Decision revision | Normative section | Satisfied prerequisites |
|---|---|---|---|---|
| CP-019 | CT23.questions / [#44](https://github.com/chi-thanh-nguyen/codemastery/issues/44) | CT23-QUESTIONS-2026-11-09-r1 | §4 | CT40.skill-tag r1; CT03 ownership; §3 guard |
| CP-020 | CT23.composition / #44 | CT23-COMPOSITION-2026-11-09-r1 | §5 | CP-019; no adaptive difficulty consumed |
| CP-021 | CT23.publication / #44 | CT23-PUBLICATION-2026-11-09-r1 | §6 | CP-020; CT03 r1 |
| CP-022 | CT23.bindings / #44 | CT23-BINDINGS-2026-11-09-r1 | §7 | CP-021; CT40.coverage r1; no Lesson binding |
| CP-023 | CT04.start / [#45](https://github.com/chi-thanh-nguyen/codemastery/issues/45) | CT04-START-2026-11-09-r1 | §8 | CP-020/021; §3 learner guard |
| CP-024 | CT04.submission / #45 | CT04-SUBMISSION-2026-11-09-r1 | §9 | CP-023; existing AS04 valid-input baseline |
| CP-025 | CT04.expiry / #45 | CT04-EXPIRY-2026-11-09-r1 | §10 | CP-024; §3 expiry guard/configuration |
| CP-026 | CT04.results / #45 | CT04-RESULTS-2026-11-09-r1 | §11 | CP-024; CT03/CT01 |
| CP-027 | CT08.evidence / [#49](https://github.com/chi-thanh-nguyen/codemastery/issues/49) | CT08-EVIDENCE-2026-11-09-r1 | §12 | CP-024; CT40.skill-tag r1 (skill IDs carried) |
| CP-028 | CT04.bindings / #45 | CT04-BINDINGS-2026-11-09-r1 | §13 | CP-025/026; CT18.minimum |

CP-019→020→021→022, CP-020/021→023→024, CP-024→025/026/027 and
CP-025/026→028 are the verified HARD edges; CP-003→019 and CP-004→022
come from Batch 1. CP-002→022 is inapplicable: Quiz has Course only, no Lesson FK.
CP-003→027 applies and is satisfied. CT07.difficulty and CT39.binding are
informational here; neither is silently approved. Evidence is approved after
submission and before any future execution consumer. No Batch 2 member is repeated.

## 3. Additional necessary prerequisite slices

### 3.1 CT03.assessment-guard

**CT03-ASSESSMENT-GUARD-2026-11-09-r1; DELEGATED_APPROVED**, [CT03 #22](https://github.com/chi-thanh-nguyen/codemastery/issues/22).
This is a new bounded companion boundary, not a replacement for any CT03 assertion
or ADR005 Instructor guard. Existing `withCourseConfigurationWrite` deliberately
excludes Learners/background execution. Calling it as an Instructor, bypassing it
with an Assessment query of Course tables, or weakening its audience is rejected.
A private Assessment-only mutex would miss the first-history/settings race in
ADR005 §13.2. The same existing physical Course root solves that race without DDL.

Add only these public methods to existing Course
`application/service/CourseManagementService.java`:

```java
public <T> T withLearnerAssessmentWrite(UUID courseId, Supplier<T> work);
public <T> T withAssessmentExpiryWrite(UUID courseId, Supplier<T> work);
```

Learner entry authenticates with CT01's trusted current identity, requires the
unchanged CT03 Learner Course predicate before locking and rechecks it after
acquiring Course's private `SELECT ... FOR UPDATE` root. Only Assessment
AttemptService start/save/submit use cases may supply callbacks. They independently
bind attempt.user_id to trusted self; caller-supplied learner/role is never accepted.
A completed member remains eligible; absent/LEFT membership is denied as before.

Expiry entry is **internal only**, callable solely by AttemptService's overdue
finalizer invoked by existing AttemptAutoSubmitScheduler. Assessment first resolves
an actual persisted overdue Attempt and its Quiz Course from its own repositories;
Course locks that existing Course root; Assessment rechecks immutable ancestry,
expiry and unsubmitted state under the lock. Course publication, membership and
current account changes do not cancel an already captured timed obligation. No
security-context impersonation, synthetic user, REST endpoint, external timer
payload or arbitrary actor ID is allowed. A missing Course is integrity failure
(500), never permission to finalize a foreign record. Restrict call sites and test
the absence of public/controller/background callers other than this allowlist.
This entry authorizes no mastery/progress, routing or general background operation.

Both entries use ADR005's same READ COMMITTED write unit, root identity, lock order
and transaction-enforcement checks: root before Quiz/Attempt/Answer locks, no
cross-Course nesting, async callback, REQUIRES_NEW, read-only/incompatible outer
transaction, early commit or swallowed failure. Original Instructor entry and
settings NOWAIT semantics remain unchanged. Null callback/IDs are internal errors.
Failure rolls back the unit; report success only after actual commit. Resource
contention/timeout/provider failure is safe 500, no request retry. Finite wait,
query/transaction deadlines and cancellation qualification remain implementation
acceptance gates under ADR005 §13.3; no framework timeout is accepted implicitly.
Only Course accesses its persistence. Same-Course serialization cost is intentional.
This does not approve CT08.execution or CT22.atomic participation.

### 3.2 CT38.feature.quiz-expiry

**CT38-QUIZ-EXPIRY-2026-11-09-r1; DELEGATED_APPROVED**, [CT38 #21](https://github.com/chi-thanh-nguyen/codemastery/issues/21).
Strictly needed by CP-025; CT38.core supplies no quiz scheduling inputs. Approve
only backend host/container inputs, bound in existing `common/config/AppConfig.java`
and `application.yml`, consumed by the existing Assessment scheduler:

| Environment input | Spring property | Validation |
|---|---|---|
| APP_QUIZ_AUTO_SUBMIT_ENABLED | app.quiz.auto-submit.enabled | Required explicit boolean |
| APP_QUIZ_AUTO_SUBMIT_DELAY_MS | app.quiz.auto-submit.delay-ms | Required integer 1..2147483647 milliseconds |
| APP_QUIZ_AUTO_SUBMIT_BATCH_SIZE | app.quiz.auto-submit.batch-size | Required integer 1..2147483647 |

No production values/defaults are selected. Missing/invalid values fail startup
when this feature configuration is implemented, including an explicitly disabled
scheduler; tests provide explicit case values. Disabled suppresses the scheduler
only, never authorizes late saves/submissions. A deployed timed-quiz capability
requires an enabled qualified scheduler; disabling is no accepted production mode
for that capability. Fixed-delay runs do not overlap in one process; database
locking handles multiple instances. Batch size bounds each discovery query, not
quiz length, attempts per learner, retries or a deadline grace period. Secrets,
frontend VITE variables, CT02, storage and other CT38.feature slices are excluded.
No deployment values are approved by the integer representation bounds above.
Tests supply explicitly named case values; production enablement requires an
operator-recorded choice and qualification. Numeric deployment tuning and timely-finalization qualification remain operator
acceptance, not invented constants or new Leader-reserved architecture choices.
This specification task does not edit runtime configuration or .env.example.

## 4. CT23.questions

Assessment owns questions. Reuse V004 kinds/difficulties, nonblank prompt/options,
ordered options and parallel correctOptions boolean mask; true_false uses exactly
`["True","False"]`; single_choice/true_false has one true key, multiple_choice at
least one. Predict-output uses choice text, never code execution. Unknown/null/
duplicate JSON members or wrong types are 400. No string trimming, new length cap,
new kind, numeric skill threshold or random question selection is introduced.

Every operation authenticates current Instructor and consumes
`CourseAccessService.requireCourseOwnership(courseId)`. Tagging/create/retag uses
unchanged `SkillGraphService.requireSkillInCourse(courseId,skillId)`. Question has
no Course/owner column; for reads/updates/deletes, Assessment restricts queries to
Adaptive's complete `readSkillIds(courseId)` projection, never imports its repository
or resolves a foreign question and reveals its Course. Foreign/missing question or
skill is safe 404 after Course admission. Administrator has no Instructor inheritance.

Create returns a new Question UUID. PUT fully replaces an **unused** definition at
the same ID. Used means referenced by **any AttemptAnswer**, active or submitted:
all definition fields including skill association then become immutable. Used PUT
returns 409 even for a replacement request; explicitly POST a new Question and
replace quiz membership where §5 permits. Do not create hidden replacement IDs as
a side effect of PUT. DELETE permits only questions referenced by neither
QuizQuestion nor AttemptAnswer; otherwise 409. All create/update/delete operations
use ADR005's Instructor Course guard, including reference checks. No retention,
hard-delete of history, revision table, copy engine or cascade is introduced.
Owner reads include answer keys; learner endpoints never expose bank/key fields.

Coverage remains exactly ADR005 §3.3: every valid Question, used or unused, one
count per distinct ID, no publication/difficulty filtering or executable-quiz
promise. Blank/malformed stored masks are integrity failure, not omitted coverage.
Changing/removing an unused Question serializes against enablement; it may affect
fresh effective readiness, never rewrite stored settings/history/mastery.

## 5. CT23.composition

A Quiz belongs to one existing Course; title is nonblank, kind is exactly
`diagnostic|practice|test`, timeLimitSeconds is a positive int. Create hidden, with
an explicit ordered nonduplicate list of Question UUIDs (empty draft allowed).
Membership is the complete supplied list in that order; persist positions 1..N.
Validate each Question belongs to this Course through §4 and valid definitions.
No count cap, randomization, adaptive selection, weights or new persistence field.
Fixed manually composed practice is distinct from CT07/AS07 adaptive difficulty.
Diagnostic authoring is permitted; placement/init/exposure remains CT25 (§8).

PUT completely replaces title/kind/time limit/membership only while Quiz is hidden
and has **no Attempt**, including active attempts. The existence probe and write
run inside the same Course root as start, eliminating check/start races. Any Attempt
freezes these fields and membership permanently; publish/hide remains available.
Return 409 for a published or used Quiz edit. This preserves kind/time/membership
for immutable history without schema snapshots; author a new hidden Quiz when
another version is needed. DELETE only hidden, never-attempted Quizzes, removing
only their own membership rows in the same transaction; otherwise 409. No attempt
limit is introduced. Reject unrepresentable list positions or attempt numbers as
409, not integer wrap. Atomic membership replacement observes the existing
deferrable position uniqueness; no migration change.

## 6. CT23.publication

PUT publication uses the existing closed `PublicationWrite` request:
`{"publicationStatus":"published"}` or `{"publicationStatus":"hidden"}`.
The adapter maps `publicationStatus="published"` to `published=true` and
`publicationStatus="hidden"` to `published=false` for the existing module-local
`QuizService.publish(UUID courseId, UUID quizId, boolean published)` parameter.
Quiz response DTOs retain their existing `published` boolean field. This remains
an idempotent expected-current command under the Instructor root (no ETag rule
is inherited from settings).
Publishing requires nonempty, valid, same-Course membership and positive time limit;
invalid stored definitions are 500; a valid empty draft is 409. It need not cover
every skill or satisfy n; bank readiness is not quiz publication. A hidden Course
can contain a published Quiz, still invisible to Learners. Hiding stops new starts
and learner reads/saves/submissions, but does not cancel or delete existing attempts;
§10 finalization still applies. Republish revalidates. No notification is emitted
by this contract (CT28/CT11 remain separate). A used Quiz cannot change definition
by hiding it first. Current Learner access additionally requires published Course,
ACTIVE/COMPLETED self membership and published Quiz. No mastery gap hard-block.

## 7. CT23.bindings and authoring API

OpenAPI's Batch 3 paths/schemas are the REST source; all are
**APPROVED_NOT_IMPLEMENTED**. Authoring routes are Instructor-only; learner lists
are separately protected. Query page/size uses existing 0/20 defaults and 1..100
size bounds, PageResponse fields and canonical UUID rendering. Questions sort by
UUID ascending; quizzes sort title COLLATE C then UUID. All filtering occurs before
count/pagination; one coherent REPEATABLE READ read-only snapshot includes scope,
rows and count. Quiz owner list/details contain question definitions/keys; learner
list contains only QuizSummary and never bank content/keys. No lesson association,
new REST user directory or AS16/AS17 reporting read is approved.

Use existing QuestionBankController/QuizController, QuestionRequest/QuestionResponse,
QuizRequest/QuizResponses. Nested DTOs/command/record types stay in these existing
files. Authoring service callables in existing application/service files are:

```java
// QuestionBankService: QuestionView is an immutable nested record
QuestionView create(UUID courseId, QuestionInput input);
QuestionView update(UUID courseId, UUID questionId, QuestionInput input);
QuestionView delete(UUID courseId, UUID questionId); // deleted representation
QuestionView read(UUID courseId, UUID questionId);
PageResponse<QuestionView> list(UUID courseId, int page, int size);
// QuizService: QuizView/QuizSummary/QuizInput are nested immutable types
QuizView create(UUID courseId, QuizInput input);
QuizView update(UUID courseId, UUID quizId, QuizInput input);
QuizView delete(UUID courseId, UUID quizId);
QuizView publish(UUID courseId, UUID quizId, boolean published);
QuizView readOwned(UUID courseId, UUID quizId);
PageResponse<QuizView> listOwned(UUID courseId, int page, int size);
PageResponse<QuizSummary> listForLearner(UUID courseId, int page, int size);
```

Input fields and output fields/types match OpenAPI exactly, deep-copied with closed
validation; no API DTO/entity crosses a module. These are module-local application
bindings, not new cross-module APIs. QuestionSelectionService for this slice uses
exact explicit IDs/order only; adaptive port/difficulty selection remains excluded.
Repositories are private to Assessment: scoped pages, atomic writes, Course-wide
Question reference/Quiz attempt existence checks, captured rows and lifecycle row
locks. An Assessment query may join Assessment tables; it may not join Course,
Skill or Auth tables to bypass public boundaries. Existing CT40 coverage and
AttemptService.hasSubmittedAttempts signatures/populations remain unchanged.

## 8. CT04.start

Self Learner only: CT01 trusted UUID, unchanged CT03 Course access, published Quiz
within admitted Course. No instructor impersonation or body-supplied actor, time,
score, duration, difficulty or question IDs. Diagnostic start returns 409 until
CT25's separately approved diagnostic exposure/binding exists; that future binding
must reuse this captured lifecycle, not silently expose diagnostic initialization.
Normal fixed practice/test starts require no CT07 and do not promise adaptation.

POST start has no request body. Inside §3.1's Learner root, lock the Quiz and
inspect this learner/quiz's attempts. Return an existing unsubmitted, unexpired
attempt with 200; do not reset its timer or allocate another number. If an overdue
unsubmitted attempt exists, return 409 until the finalizer succeeds, never overlap
it. Otherwise allocate max committed attempt_number+1 (first=1) with 201; positive
int overflow is 409. No retake count/cooldown is invented. The root serializes
concurrent first starts; uniqueness is a backstop, not the race strategy.

Use server `java.time.Clock` injected locally into existing AttemptService (no
CT39 API or new production bean/file required; production uses UTC, tests use a
fixed mutable test clock in the existing test inventory). Obtain startedAt after
locks and eligibility, truncate to PostgreSQL microseconds; expiresAt = startedAt
plus this frozen Quiz's timeLimitSeconds. Positive expiry/representable Instant
required; persisted timing/score nullability remains V004. Capture every Question
ID and its QuizQuestion position into AttemptAnswer in one unit, with all-false
masks of matching length and is_correct null. §4/5 locks make definitions stable.
No attempt/partial membership survives capture failure. Return captured prompts,
options, selected masks and timestamps; never correctOptions/isCorrect/score.

## 9. CT04.submission and saved answers

PUT answers and POST submit accept the same full `{answers:[{questionId,
selectedOptions}]}` closed representation. Include exactly once **every captured
Question**; order of input rows is immaterial and is canonicalized to captured
position. Reject missing/extra/duplicate IDs, nulls/wrong types/lengths or multiple
true choices for single_choice/true_false as 400. All-false is an explicit unanswered
answer. Membership/keys never come from the request. A client that needs partial
editing retains other current masks and sends the complete set.

Every mutation locks root → Quiz → Attempt → captured answers, verifies self and
scope, then captures server now at microsecond precision. Admission is strictly
**now < expiresAt**; equality is expired. Evaluate time after lock acquisition, not
HTTP receipt or browser timer. Reject clock regression now < startedAt as safe
500 with rollback; no client clock/grace period. Save replaces all masks atomically,
returns the current active representation, and invokes no grading/event/mastery.
A terminal or overdue save is 409. Simultaneous saves use serialized last accepted
replacement; no optimistic revision or merging is promised. Clients sequence saves
and do not allow stale responses to replace newer UI state under CT16.

For manual submission before deadline, validate and store supplied masks, grade
**the entire captured nonempty set** against immutable keys using the existing
AS04 equal-weight exact-mask rule, persist each is_correct, score (HALF_UP, 2 decimals),
and submitted_at=now together in one transaction. A failure anywhere rolls back
selections, grading and terminal fields. No partial key match/percentage drives mastery.
A well-formed repeated submit on an already submitted self Attempt returns its
unchanged result with 200, ignoring replacement selections and producing no second
terminal transition or event. The request must still pass authentication, current
visibility and closed-shape validation. An overdue unsubmitted POST is 409 and
accepts no late masks; scheduler later grades the last committed saved masks.
Network/commit uncertainty requires GET before another deliberate action; no
automatic POST retry. Repeating start after a finished Attempt is a new retake.

Approve this **module-local typed binding**, not a change to AS04 mathematics, in
existing QuizGradingService.java:

```java
public Grade grade(List<GradingItem> items);
public record GradingItem(UUID questionId, List<Boolean> selectedOptions,
        List<Boolean> correctOptions) {}
public record GradedItem(UUID questionId, boolean correct) {}
public record Grade(BigDecimal scorePercentage, List<GradedItem> items) {}
```

Nested records defensively copy. AttemptService supplies valid unique, nonempty
items in captured order, validates matching masks/types first, and verifies the
Grade IDs/order/correctness/percentage against that input. Null/invalid internal
input or malformed provider output fails safe 500 before persistence; no invented
empty-quiz score or extension of AS04's accepted domain. Compatible doubles support
local orchestration; only actual AS04 execution proves integrated grading.
Submission does not call a dummy event consumer or claim Adaptive success. Actual
AS12 publication and its coupling to result persistence stay blocked by
CT08.execution/failure and CT22.atomic; exposing an integrated quiz flow remains
gated until those contracts and real participants are qualified. This local
transaction is not a decision that result persistence must precede or follow a
future mastery transaction; a compatible enclosing unit may include it later.

## 10. CT04.expiry

Existing `AttemptAutoSubmitScheduler` discovers unsubmitted due attempts in
ascending (expires_at, attempt UUID text), using server UTC and §3.2's explicit
batch size. Page with that full key across each pass; a failed earliest candidate
must not starve later candidates. A fresh pass begins after the configured fixed
delay and rediscovers rolled-back overdue work; this is the specified recovery
path, not a hidden request retry count, in-memory timer or event-delivery guarantee.
Restart loses no deadline: discovery reads existing persisted expires_at. Multiple
workers may discover the same candidate; root/Attempt recheck makes it a no-op
once submitted. One transaction per candidate; no transaction spans discovery,
multiple Courses or the entire scheduler pass.

`AttemptService.finalizeExpired(UUID attemptId)` is an internal scheduler callable,
not an HTTP operation. Derive Course from Assessment-owned Attempt/Quiz, enter
§3.1 expiry guard, recheck ancestry/due/unsubmitted state, and grade **last committed
saved masks** using §9. If not due/already submitted, no-op. Set submitted_at to
**expires_at** (logical auto-submit boundary) even if worker runs later; use no new
observed/event timestamp column. Never accept late answer input. Initially all-false
masks therefore produce explicit incorrect answers, not absent evidence. Commit
all result/answers or none. Continue other candidates after rollback and log a safe
operational failure with internal correlation; never credentials/answers/keys.
Repeated failure leaves overdue state visible as such and requires operator repair;
no fake success, poison-row deletion, retry ledger or risk acceptance.

Save/manual/expiry races serialize on the same root/Attempt. A manual transaction
that obtains the locks and samples now before expiry may commit once; finalizer
then observes terminal state. If the finalizer wins, manual submit returns that
same stored result; a late manual writer cannot overwrite it. Hiding Course/Quiz,
leaving or locking the learner after start never prevents internal overdue cleanup.
Current learner result visibility still follows §11. Scheduler lag is not extra
answer time. Timely feedback, finite transaction duration, restart and multiple
worker behavior require real qualification; no latency SLA is fabricated.

## 11. CT04.results

Learner GET active attempt includes only captured question IDs/positions/prompts/
options/selected masks and times. Derive display status from nullable submittedAt
and expiresAt versus server time: `active|overdue|submitted`; this is a DTO-only
label, **no persisted state enum**. Result GET before submission → 409, including
overdue pending finalization. GET is read-only and never finalizes or publishes.
Submitted result gives own exact selections, per-question correctness, score and
times; **answer keys remain withheld**. Correct/incorrect feedback satisfies US-05
without disclosing a reusable key through a result endpoint. A future key-release
policy needs its own approval. Owner Instructor result endpoint may read submitted
results of its Course, still without key fields in Result (owner bank holds keys).
No Instructor start/save/submit or Administrator implicit access.

All explicit Course routes perform CT03 admission before scoped Quiz/Attempt lookup;
wrong learner's Attempt is safe 404, never reveal owner. Learner reads require
current published Course/Quiz and self ACTIVE/COMPLETED membership, including for
historical results. LEFT/hidden states retain data but do not grant reads. Instructor
uses unchanged Course ownership and may read hidden Quiz history. Histories list
all own attempts including active/overdue and finished, with nullable score/time,
ordered attempt_number descending (unique within learner/quiz), paginated using
existing defaults/bounds. No foreign learner selector, public score read, aggregate
learning-outcome/AS16 reporting API or retention policy is added. Coherent
REPEATABLE READ read-only snapshot for admission, rows, answers and count;
Cache-Control no-store on all protected success/error responses.

## 12. CT08.evidence only

Existing location: `modules/assessment/application/event/QuizSubmittedEvent.java`.
Approve this immutable typed schema, with nested AnswerEvidence, **not a REST body**:

```java
public record QuizSubmittedEvent(UUID attemptId, UUID learnerId, UUID courseId,
        UUID quizId, String quizKind, int attemptNumber, Instant startedAt,
        Instant expiresAt, Instant submittedAt, List<AnswerEvidence> answers) {
    public record AnswerEvidence(UUID questionId, int position,
            UUID skillId, boolean correct) {}
}
```

IDs/times/list/items nonnull; positive attemptNumber/positions; distinct Question
IDs and positions, nonempty list in captured position order; expiresAt > startedAt,
startedAt <= submittedAt <= expiresAt, quizKind exactly V004 literals. Deep defensive
copy; public constructors enforce these shape invariants. Gaps in original positions
are preserved. Primitive correctness comes only from nonnull authoritative persisted
grading, never unboxing missing data as false. Producer builds exactly every captured
row and verifies complete membership against frozen Quiz membership, Question→Skill
Course consistency and immutable definitions. Captured Question IDs, positions and
correctness suffice; no key, email, entity, client counts, rounded score, mastery
state, event UUID or duplicated timestamp column is needed. Course/quizKind are
derived from frozen Quiz; skill from frozen used Question. No diagnostic binding
is approved by carrying its existing kind literal.

Manual and automatic fully graded submissions qualify equally. Active, overdue
unsubmitted, rejected, missing-answer, null-correctness or cross-Course records do
not. Constructor validation cannot prove database completeness; AS12 must prove
that producer obligation. Adaptive treats Assessment as grading/evidence authority,
never regrades or imports its entities. CT06's immutable history and maximum tuple
**(submittedAt, startedAt, quizId lowercase UUID text, attemptNumber)** remain exact.
Only skills present in complete evidence are assessed; other skills remain unchanged.
Late auto-finalization uses logical expiry time in that tuple, not delivery order.
An event alone is not authoritative latest-history proof; the actual history/read,
duplicate/replay orchestration stays a Batch 6 execution obligation.

Only evidence-shape authoring/conforming doubles for AS12/AD13 is unlocked by this
slice. **No** Spring listener phase, sync/async execution, propagation, publication
commit coupling, delivery guarantee, replay mechanism, Course progress callable,
notification subscription or diagnostic initialization is approved. AS12 producer,
AD13 orchestration, CO15 participant and QA04 real rollback proof remain distinct.
CT08.execution/failure/verification-plan and CT22.atomic remain pending in Batch 6;
CT28 remains Batch 7. The architectural requirement that mastery and related
LessonProgress succeed/fail together is unchanged and cannot be satisfied by this
schema or a mocked callback. Do not wire partial real integration while those gates
remain unresolved. ADR001 §9's remaining deferrals stay effective.

## 13. CT04.bindings, errors and compatibility

Use existing QuizAttemptController, SubmitAttemptRequest and AttemptResponses;
answer-save uses a nested request shape in SubmitAttemptRequest (no new file).
AttemptService's nested immutable input/output types mirror OpenAPI exactly:

```java
StartOutcome start(UUID courseId, UUID quizId); // attempt + created boolean
AttemptRead read(UUID courseId, UUID quizId, UUID attemptId); // active or submitted union
AttemptView saveAnswers(UUID courseId, UUID quizId, UUID attemptId, AnswersInput input);
AttemptResult submit(UUID courseId, UUID quizId, UUID attemptId, AnswersInput input);
AttemptResult readResult(UUID courseId, UUID quizId, UUID attemptId);
AttemptResult readOwnedResult(UUID courseId, UUID quizId, UUID attemptId);
PageResponse<AttemptSummary> history(UUID courseId, UUID quizId, int page, int size);
void finalizeExpired(UUID attemptId); // internal scheduler only
```

`AttemptRead` is a nested sealed interface permitting the nested immutable
`AttemptView` and `AttemptResult` records; these correspond to B3Attempt and
B3AttemptResult. StartOutcome contains `(AttemptView attempt, boolean created)`.
`AnswersInput` has the B3AnswersWrite fields; history uses B3AttemptSummary.
No domain enum or top-level type is added. Boundary representation names differ
from OpenAPI schema names only; fields, nullability and invariants are identical.
All actual mutation success follows commit. Same-Course writes use Course-root
then Quiz, Attempt, answers in position order; reference guards make used questions
and quizzes immutable. Repositories support scoped access, current Attempt lookup,
max attempt number, row locks, captured row order, transactional save/grade,
keyset due discovery and submitted existence. No new table/index is required by
approval; query/deadline performance on real data is an implementation gate.
No frontend type/client/controller implementation is changed by specification work.
Future delivery must synchronize them and preserve CT16 session generation, no
automatic mutation replay and no obsolete response restoring private state.

Preserve CT18.minimum: application/problem+json, safe English detail and no rejected
values. 401 invalid/locked current identity; 403 wrong role or CT03 published Course
membership/ownership denial; 404 missing/hidden/foreign scoped resources or another
learner's Attempt; 400 malformed UUID, closed JSON/input/page/mask; 409 valid request
in invalid lifecycle (used definition, empty publication, overdue/manual conflict,
active result, diagnostic exposure gate or representational exhaustion); 500
provider/data/clock/transaction/grading failure with rollback. Authentication and
role precede resource work; Course concealment/membership precedes descendants.
For admitted scoped requests, ordinary shape validation precedes lifecycle decisions.
No new status family, default retries or exception file. Reuse existing exceptions;
BusinessRuleException remains 400/409. Deleted representations use 200 JSON, avoiding
an extension of CT16's five approved 204 call sites. This does not inherit settings
ETag/If-Match, which remain exclusive to that earlier operation.

## 14. Deterministic acceptance obligations and retained gates

These are **specified, NOT_RUN** against Batch 3 implementation. Existing approved
Assessment AttemptServiceTest/QuizGradingServiceTest and integration suites may be
used within their authorized scopes; no new test file, CT14 global allocation,
CT34 runner, E2E case or framework is approved here. Additional placement expansion
requires its own scoped approval. QA04's transaction suite remains CT08-owned.

| Oracle | Required result |
|---|---|
| B3-O01 | Wrong role/locked/missing membership/hidden or foreign Course, Question, Quiz and Attempt obey §13 precedence; no key or foreign identifier leakage. |
| B3-O02 | Validate all V004 question kinds, true/false labels, blank options and masks; cross-Course tag 404; used retag/option edit/delete 409; explicit replacement preserves old evidence. |
| B3-O03 | Explicit question order is captured; duplicates/foreign IDs rejected; empty hidden draft allowed but publish 409; no n/random/difficulty selection. |
| B3-O04 | Barrier-based author/edit/start/reference-delete races share Course root; one consistent outcome, no phantom permission; other Courses proceed. Fault injection restores membership and all prior data. |
| B3-O05 | Concurrent first start yields one captured Attempt, responses 201/200; repeated active start preserves timer; next number after submission; int exhaustion 409; UTC microsecond expiry exact. |
| B3-O06 | Complete answer replacement, explicit all-false, duplicate/missing/foreign IDs, wrong length/type and single-choice multi-selection; invalid input causes no writes. |
| B3-O07 | At expiry minus one microsecond accept; at equality reject save/late manual; browser receipt time irrelevant; clock regression fails; no grace. |
| B3-O08 | AS04 exact match, partial/extra/unanswered zero; 1/3=33.33 and 2/3=66.67; rollback injected after one answer write leaves no partial selections/results. |
| B3-O09 | Duplicate submit preserves every terminal byte; manual/save/expiry races yield one result and no second terminal transition. GET remains read-only. |
| B3-O10 | Restart/multiple workers, hidden/LEFT/locked after start, failed earliest due candidate and multi-page passes preserve expiry grading and allow later candidates; config missing/invalid fails startup, disabled cannot accept late answers. |
| B3-O11 | Own result/history and owner Instructor result; current membership/visibility enforced; no key on active/result/history paths; count/order/filter agree under concurrent writes. |
| B3-O12 | Event rejects malformed constructors; producer detects omitted row/null correctness/foreign skill; complete auto/manual evidence preserves CT06 total ordering and unassessed skills. No claim that schema tests prove replay/atomic mastery. |
| B3-O13 | First submission versus settings threshold change shares same Course root; threshold writer either precedes evidence or sees the barrier and rejects. Expiry never uses Instructor credentials. Real CT08/CT22 effects remain untested/unapproved. |

No previously effective approval is amended or superseded. ADR005 threshold
freeze, validator semantics, evidence policy, coverage and owner guard are retained;
ADR006 progress/resume, authoring and its **reset-only email / bounded visit-time
schema/read exceptions** keep exactly their scope and outstanding gates. No new
architecture exception, schema/material structural change, dependency, risk
acceptance or recommendation policy is selected. Immutable originals and applied
migrations remain untouched. Alternatives requiring snapshots/version columns,
actor impersonation, direct cross-module tables, random/adaptive composition,
process-local expiry timers or full event execution are rejected as unnecessary or
outside scope. No proposal to reopen earlier approvals is justified.

All ten technical checkpoint decisions are complete in this record. CT23/CT04
whole-Issue independent boundary review remains unverified; CT08 is **partial at
Issue level**, evidence only. Original consumer approvals, AS01/AS04 artifacts,
actual bank/lifecycle/graded producers, configuration values, scheduler/deadline/
privilege qualification, deterministic and PostgreSQL tests, frontend delivery,
CT08/CT22 atomic integration, protected review/CI/main integration and separate
implementation authorization remain gates. No release/exposure/deployment readiness
or operator residual-risk acceptance is implied. Approval/Readiness/Status are
independent Project fields; a contract may be fully technically decided while a
consumer remains blocked on callable artifacts and integration.

Mechanism references (feasibility, not project execution evidence): PostgreSQL
[17 row locks](https://www.postgresql.org/docs/17/explicit-locking.html#LOCKING-ROWS),
[READ COMMITTED / REPEATABLE READ](https://www.postgresql.org/docs/17/transaction-iso.html),
and Spring's [fixed-delay scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html).
The project retains PostgreSQL 17 and existing Spring dependencies; no upgrade is selected.

## 15. Preparation validation — 9 November 2026

Executed local documentation checks: `git diff --check` passed. A temporary
read-only Python audit parsed OpenAPI with duplicate-key rejection, resolved all
513 internal references, checked 19 unique new secured operations/path parameters,
verified all pre-existing OpenAPI values and lines remained in order, resolved 19
new relative links/anchors, and matched all ten CP identifiers and thirteen HARD
edges against the corrected register. Nine documentation/specification files only
are changed. Learner response-schema closure contains no answer-key field.
These checks passed; they are not external OpenAPI conformance certification or
runtime behavior tests. No validator package was installed; no Batch 3 unit,
PostgreSQL, scheduler, frontend, E2E or atomic event test was run. No independent
GitHub/CODEOWNER approval or CI success is claimed here. Clock alignment and
scheduler/transaction timing across real instances remain qualification gates.

## 16. AS08 ancestry assertion companion — 26 November 2026

**AS08-ANCESTRY-2026-11-26-r1; DELEGATED_APPROVED.** The Project Leader's
“AS08 — Full Implementation Completion, Bounded Upstream Remediation, and Final
Self-Audit of PR #296” explicitly delegates bounded necessary technical decisions
and supporting public bindings. Author: Codex, AI-assisted follow-up work under
that instruction. This companion changes no earlier owner-only method, fixed
Quiz capture, evidence schema, grading, execution marker or authorization predicate.

Gap established against main `cc6cb54`: ADR005 §3.2's `readSkillIds` and
`requireSkillInCourse` both require Instructor ownership. Reusing them from
Learner attempts or actorless expiry fails 403/401, as reproduced by the real
AS08 PostgreSQL tests. ADR007 §12 and ADR011 §12 still require Course ancestry
validation, and Assessment cannot query Adaptive persistence.

Add exactly one public assertion to existing `SkillGraphService`:

```java
public void requireAssessmentSkillsInCourse(UUID courseId, Set<UUID> skillIds);
```

Only `AttemptService` calls it. It exports no Skill, name, graph, boolean lookup
or arbitrary learner data. Inputs are immutable IDs from Assessment-owned frozen
Quiz definitions, never an HTTP selector. The caller has already admitted the
Course in its read-only REPEATABLE READ snapshot, or runs inside its actual
Course-root READ COMMITTED write callback. The assertion checks the active
transaction/isolation/read-only combination before reading its own skill
repository, and requires every supplied skill to belong to the Course. A foreign
or inconsistent stored reference is an integrity failure (safe 500). Caller
allowlist tests enforce the boundary; this method does not export or inspect
Course's root marker. Course guard and CT03 remain the caller's responsibility.

Alternatives: weakening the existing owner-only projection would change a settled
boundary; actor impersonation and cross-module SQL violate the architecture;
skipping ancestry would accept corrupt evidence; a general actorless graph read
would disclose unnecessary data. This assertion preserves existing interfaces
and reuses AD07's scoped repository query. Its scope is one metadata check plus
focused tests, approximately 3–5% of AD07's full graph CRUD, DAG validation,
mapping, concurrency, persistence, authorization and REST implementation.
No new module, file, table, column, dependency or production configuration is
introduced. It authorizes no AS12/AD13 wiring, deployment, review approval,
operational acceptance or broader read audience. Independent CODEOWNER review
and protected integration remain required.

## 17. AS18 execution-coverage ancestry caller — 28 November 2026

**AS18-EXEC-COVERAGE-2026-11-28-r1; DELEGATED_APPROVED.** The Project Leader's
“AS18 Full Implementation Completion, Bounded Upstream Remediation, and Final
Self-Audit of PR #307” explicitly delegates bounded technical decisions necessary
for AS18 (#164). Author: Claude Code, AI-assisted work under that instruction.
This amendment refines only §16's caller set and input provenance; §16's
signature, transaction check, data returned and integrity semantics, ADR005 §3.3,
ADR011 §§4/12 and every owner-only CT40 method are unchanged.

Gap established against main `4ae384b`: ADR011 §4 step 6 requires
`QuestionBankService.readCoverageForExecution` to apply ADR005 §3.3's semantics,
including that every requested skill belongs to the Course, with no actor. ADR005
§3.2's `readSkillIds`/`requireSkillInCourse` require Instructor ownership, and
§16 allows only `AttemptService` with IDs from frozen Quiz definitions. Readiness
asks for every Course skill, including skills no submitted Quiz captured, so §16's
provenance does not cover it. Routing the call through an `AttemptService` helper
would satisfy a file-name allowlist without changing what is actually authorized.

`QuestionBankService.readCoverageForExecution` becomes the second and last direct
caller of `SkillGraphService.requireAssessmentSkillsInCourse`. It calls the
assertion only after `AttemptService.requireExecutionScope(courseId)` succeeds,
that is inside submission's or expiry's Course-root READ COMMITTED callback with
the Assessment-private marker bound for that Course, and only for a nonempty
requested set. Inputs are the skill IDs Adaptive's execution orchestration
supplies from its own Course graph (ADR011 §4 step 6), never an HTTP selector. A
foreign or inconsistent skill is an integrity failure (safe 500); no concealment
applies because no HTTP caller exists. The assertion still exports no Skill, name,
graph or boolean lookup, and `readCoverage` keeps §3.2's owner-only scope.

Alternatives: an `AttemptService` intermediary (rejected: circumvents the caller
restriction and adds an Assessment service cycle); trusting Adaptive's set without
ancestry (rejected: a foreign skill would become a silent zero); a new actorless
SkillGraphService lookup (rejected: duplicates this assertion and widens the public
surface); Instructor impersonation or cross-module SQL (rejected by ADR001).

Scope: one caller, a Javadoc line in `SkillGraphService` and allowlist tests,
about 1% of AD07's full scope. No new file, method, table, migration, dependency,
configuration or runtime privilege. Verification obligations: allowlist tests
(`AttemptServiceTest`) name exactly `AttemptService` and `QuestionBankService` as
callers, and real PostgreSQL tests prove the execution-scope refusals of ADR011
B6-V09 and cross-Course rejection. It authorizes no AD08/AD13 wiring, review
approval, protected integration, deployment or broader read audience.
