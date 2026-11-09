# ADR005: Batch 1 Contract Approvals

> **Current scoped refinement:** [§16](#16-r1r3-independent-review-corrections--9-november-2026)
> completes guarded PUT preconditions and the five 204 operations’ frontend transport
> handoff. It preserves N1 and every original approval/history outside those scopes.

> **N1 HTTP correction:** [§15](#15-n1-http-validator-correction) preserves
> `CT06.enablement r2` provenance and changes only successful PUT validator handling
> and its client/verification handoff. GET retains its strong ETag; PUT requires
> If-Match, returns no validator, and requires a subsequent client GET before another update.

> **Effective correction within this PR, 8 November 2026:** [§13](#13-focused-f1f4-amendments--8-november-2026)
> narrowly amends the settings write/lifecycle and verification contracts under the
> Leader's “PR #228 Focused Technical Corrections & Revalidation” instruction.
> Sections 1–12 preserve the original `8bc7469` record verbatim. Where they differ,
> §13 governs only the explicitly listed scopes/signatures/oracles; the other eight
> checkpoints, four original prerequisite records and their provenance are retained.
> Technical approval remains separate from protected integration and implementation.

## 1. Authority, baseline and precedence

**DELEGATED_TECHNICAL_APPROVAL; DELEGATED_APPROVED**, 8 November 2026
(Asia/Ho_Chi_Minh), independently selected and recorded by **Codex** under the
Project Leader @chi-thanh-nguyen's explicit instruction **“CodeMastery — Batch 1
Complete Contract Approval & Integration Preparation”**. That instruction delegates
technical selection, approval, necessary prerequisite slices and traceable amendments.
It does not represent personal technical review by the Leader or the module owners.
No implementation, deployment, release, residual-risk acceptance, protection bypass
or merge is authorized. The approval applies to these recorded bytes; **the PR is
pending integration into `main`**. `DELEGATED_APPROVED` has the meaning in the
[decision index](README.md#approval-status-labels), not GitHub review approval.

Inspected baseline: `main` **b51f03ffe7300b607bcdb6962b07018f6bf69460**; PR
[#227](https://github.com/chi-thanh-nguyen/codemastery/pull/227) is merged.
[ADR004](004-leader-approval-of-delegated-records.md) already resolves LD-01 and
`GOV.HC-U20`; its historical records and `LEADER_APPROVED` status remain effective.
This ADR supplies new approval authority rather than extending ADR004 to future records.

Planning provenance: authoritative `APPROVAL-CHECKPOINT-REGISTER.md`, audit
`run-20261108-135024`, as corrected by `verification-20261108-151823`'s
`APPROVAL-ROADMAP.md`, `DEPENDENCY-TRACEABILITY.md` and `registers/revised-plan.json`,
under `~/codemastery-backlog-publication/contract-checkpoint-batching/`.
The verified roadmap has eight batches. CP-002–CP-010 below are its exact nine
Batch 1 contract checkpoints; CP-001 is the separately resolved governance item.
This record does not change that immutable planning evidence or approve other batches.

Sources independently inspected: canonical requirements §4–6/§13 and their derivative;
canonical architecture §5–7 (US-03/06/07/10/11/12) and its derivative; approved tree;
AGENTS/CONTRIBUTING/CODEOWNERS/PR template; ADR001/002/004; current data-model,
adaptive, testing and OpenAPI records; V002–V005; actual Course, Adaptive and
Assessment service/repository/DTO inventories and calculator/classifier source;
live Issues #22/#24/#25/#27/#44/#46/#47/#48/#49/#20 and Project #3 (157/157 items).
The earlier readiness package is preparation evidence, not an approval.

**Precedence is scope-specific:** §§3–8 are the normative Batch 1 contract;
§9 records the exact amendments. They supersede only the pending CT06 proposal's
corresponding choices and the named restrictions/gates in older records. Historical
text remains intact, with forward notices. ADR001 §§1–10, all seven CT03 assertions,
CT06.prototype, immutable migrations, and unrelated approvals/exclusions remain intact.

## 2. Checkpoint dispositions and dependency order

Each row is independently **DELEGATED_APPROVED**; no approval depends on unapproved
upstream technical policy. Approve in table order (the independent rows may be read
together). The four CT05 rows remain distinct even though approved in one record.

| Register | Checkpoint | Revision | Normative scope | HARD predecessors |
|---|---|---|---|---|
| CP-002 | `CT40.lesson-read` | `CT40-LESSON-READ-2026-11-08-r1` | §3.1: U07, ancestry and canonical structural projection | none |
| CP-003 | `CT40.skill-tag` | `CT40-SKILL-TAG-2026-11-08-r1` | §3.2: U05, scoped skill validation | none |
| CP-004 | `CT40.coverage` | `CT40-COVERAGE-2026-11-08-r1` | §3.3: U09, complete distinct-question counts | none |
| CP-005 | `CT05.representation` | `CT05-REPRESENTATION-2026-11-08-r1` | §4.1: immutable graph and consumer projection | none |
| CP-006 | `CT06.production` | `CT06-PRODUCTION-2026-11-08-r1` | §6: evidence, history, classification and policy | none |
| CP-007 | `CT05.mutation` | `CT05-MUTATION-2026-11-08-r1` | §§4.2/5: commands, cycles and concurrency | representation |
| CP-008 | `CT05.mapping` | `CT05-MAPPING-2026-11-08-r1` | §4.3: Course ancestry and mapping consistency | lesson-read |
| CP-009 | `CT06.enablement` | `CT06-ENABLEMENT-2026-11-08-r1` | §7: U08, readiness, suspension and settings changes | coverage |
| CP-010 | `CT05.bindings` | `CT05-BINDINGS-2026-11-08-r1` | §8: application/repository/REST boundaries and errors | mutation, mapping |

The verified HARD edges are representation → mutation → bindings,
lesson-read → mapping → bindings, coverage → enablement. The CT05 intra-Issue
ordering is the verification's **decomposition**, not a newly discovered DG mandate.
Production ↔ enablement is INFORMATIONAL. The former HC-U20 conditional edges are
resolved by ADR004. Existing CT01.identity, CT03 r1 and CT18.minimum are consumed
only in their approved scope. §9 explicitly approves the needed CT24 Final reuse and
bounded CT14 allocation and CT18 feature mappings before their consumers rely on them.

## 3. CT40 producer and consumer boundaries

### 3.1 Course lesson identities and structure

For a mapping with known Course/Lesson UUIDs, **no additional structural lookup is
required**: Adaptive `SkillGraphService` calls the unchanged
`CourseAccessService.requireLessonOwnership(courseId, lessonId)` at its existing
`modules/course/application/service/` location. Course proves actual Lesson →
Chapter → Course ancestry and ownership, including hidden owner content, under
ADR001 §12.4. Normal void return discloses no structure. Adaptive then proves that
the skill belongs to that same Course. This explicitly completes U07's former
no-extra-read gate; it does not make an assertion into a durable write capability.

Canonical identities/order needed by enablement and subsequent structural consumers
use the existing file
`backend/src/main/java/com/codemastery/modules/course/application/service/CourseStructureService.java`:

```java
public StructureSnapshot readStructure(UUID courseId);
public record StructureSnapshot(UUID courseId, List<LessonReference> lessons) {}
public record LessonReference(UUID lessonId, UUID chapterId,
        int chapterPosition, int lessonPosition, boolean published) {}
```

These immutable records are **nested in CourseStructureService**. Course resolves
trusted identity itself: Learner uses `requireLearnerCourseAccess`, Instructor uses
`requireCourseOwnership`; other roles get 403 before resource lookup. Owner sees all
lessons; admitted Learner sees only published lessons. Return exactly one row per
visible lesson, sorted by chapter position then lesson position; gaps are retained.
Each row belongs to the returned Course. No lesson body, title, material, user data,
Course entity or repository crosses the boundary. Empty Course → empty list, not
missing. Provider/inconsistent ancestry → safe 500, never a partial list. CT03's
401/403/404 precedence is preserved. Hidden owner structure is not public metadata.

Permitted consumers: Adaptive settings readiness, graph/mapping assembly and later
CT07/CT33 structural consumers; Assessment may use this approved projection only
where its later CT23 binding selects it. This approves the data boundary, not those
consumers' routing, result, authoring or REST policies. Protected learner structural
reads do not depend on mastery or readiness. Actorless event/background access is
excluded until its owning execution contract approves that distinct context.

### 3.2 Adaptive skill identity and tagging

Existing file
`backend/src/main/java/com/codemastery/modules/adaptive/application/skillgraph/SkillGraphService.java`:

```java
public void requireSkillInCourse(UUID courseId, UUID skillId);
public List<UUID> readSkillIds(UUID courseId);
```

These authoring/coverage boundaries first consume Course's
`requireCourseOwnership(courseId)`. Missing/foreign skill → the same safe 404 after
Course admission; no foreign Course/skill name is disclosed. `readSkillIds` returns
the complete set for that Course, unique and sorted by lowercase UUID text, empty
when there are no skills. That sort is deterministic presentation, not lesson order.
Null Java identifiers → IllegalArgumentException; provider inconsistency/failure →
safe 500. No entity or graph internals are exported. Assessment QuestionBankService
and QuizService consume them for tagging/scope validation; Adaptive readiness uses
its own complete graph. Assessment owns question→skill binding and validates every
question used with a quiz against that quiz's Course. It cannot access SkillRepository
or infer a Question `course_id`/owner column. U05's **contract** is resolved here;
AS05/AS18 and later CT23 bindings must implement and verify it.

### 3.3 Assessment coverage

Existing file
`backend/src/main/java/com/codemastery/modules/assessment/application/service/QuestionBankService.java`:

```java
public CoverageSnapshot readCoverage(UUID courseId, Set<UUID> skillIds);
public record CoverageSnapshot(UUID courseId, Map<UUID, Long> questionCounts) {}
```

`CoverageSnapshot` is nested, deeply immutable and contains no answer keys. First
require Course ownership. Assessment validates each requested skill through §3.2,
then counts distinct Question IDs tagged to that skill. All requested skills must
belong to the Course. Return **exactly** the requested keys, including zero counts;
empty input is empty output, not proof of readiness. Counts are nonnegative `long`.
Adaptive obtains the complete Course skill set itself and rejects omitted/extra keys,
wrong Course, null/negative counts or a partial result as provider failure (500).

Population: all existing valid questions for those skills, at every approved difficulty
and kind, under V004 and data-model §11's option/key validity. Question has no
publication flag. An unused question counts; reuse in several quizzes counts once.
A malformed stored definition is integrity failure, not an invented zero or silently
omitted question. No per-difficulty minimum, executable/published-quiz guarantee,
random selection or numeric `n` belongs to this boundary. Assessment reads only its
own question data, using approved Adaptive lookup for scope. Adaptive applies `n`.
For a readiness decision all reads participate in §5's protected transaction.
U09's contract is resolved; real AS18/AD08 provider verification remains outstanding.

## 4. CT05 graph behavior

### 4.1 Representation

Existing `modules/adaptive/domain/model/SkillGraph.java` becomes an immutable,
Course-scoped domain container of existing `Skill`, `SkillPrerequisite` and
`LessonSkill` representations (no changes to their schema-backed scalar fields).
Its constructor surface is `(UUID courseId, List<Skill> skills,
List<SkillPrerequisite> prerequisites, List<LessonSkill> mappings)`. Defensively copy
collections; reject nulls, duplicate IDs/pairs, foreign skill references, self-edges
and cycles. Lesson ancestry requires the real Course producer; a pure container
cannot establish it. No I/O occurs during construction.

Cross-module readers use `SkillGraphService.readGraph(UUID courseId) -> GraphSnapshot`
after Course ownership. The nested immutable `GraphSnapshot(UUID courseId,
List<SkillReference> skills, List<PrerequisiteReference> prerequisites,
List<MappingReference> mappings)` uses nested `SkillReference(UUID id, String name,
String description)`, `PrerequisiteReference(UUID skillId, UUID prerequisiteSkillId)`
and `MappingReference(UUID lessonId, UUID skillId)` in that existing service.
Lists sort by ID, then by the pair's first/second ID, using lowercase UUID text;
there is no implied pedagogical topological ordering. Every referenced skill belongs
to the Course; mappings must pass §4.3. Empty graph is a valid draft representation,
but fails enablement. Read malformed persisted graph → 500, never silently repair.

The dependency direction is **skill → its prerequisite**; adding S → P is cyclic
iff P already reaches S along dependency edges (or S=P). UI can draw the inverse
arrow only with the same stated meaning. Pure validation uses full reachability,
not a fixed depth. Choose deterministic depth-first traversal with a visited set;
no traversal depth/weight or graph database is introduced. AD01 schema-backed
representations and FT02 reference-only fixtures remain independently usable;
only fixtures constructing this optional container consume these invariants.

### 4.2 Commands and deletion

All public methods below belong to the existing SkillGraphService, consume
Course ownership and execute under §5. Inputs are nonnull UUIDs; text follows
the existing nonblank data-model rule (optional description null or nonblank).
No length cap or skill-name uniqueness is introduced.

```java
public SkillReference createSkill(UUID courseId, String name, String description);
public SkillReference updateSkill(UUID courseId, UUID skillId, String name, String description);
public void deleteSkill(UUID courseId, UUID skillId);
public void addPrerequisite(UUID courseId, UUID skillId, UUID prerequisiteSkillId);
public void removePrerequisite(UUID courseId, UUID skillId, UUID prerequisiteSkillId);
public void mapLesson(UUID courseId, UUID lessonId, UUID skillId);
public void unmapLesson(UUID courseId, UUID lessonId, UUID skillId);
```

Create allocates a server UUID; names need not be unique. Update cannot change
identity or Course. Add existing identical edge is a no-op; remove absent edge
is a no-op **after validating both scoped endpoints**. Unknown/foreign endpoints
→ 404, self-edge in either edge command → 400, new edge closing any cycle → 409. Full validation precedes
the write and no partial edge appears on failure. A delete of a missing skill →
404; any incident prerequisite, mapping, Question or mastery reference → 409,
no cascade or evidence deletion. User removes allowed graph edges/mappings explicitly.
Private persistence translates known referential/uniqueness conflicts safely;
it must not classify arbitrary SQL/provider failure as a business conflict.
Foreign references may be protected by existing FKs without querying another
module's tables/repositories. Imported corrupt cyclic state fails closed (500).

### 4.3 Lesson mappings

Under §5, call the §3.1 ownership/ancestry assertion, then validate the skill in
Adaptive's own Course scope, before inserting/removing the pair. Cross-Course or
missing Lesson/skill → 404; duplicate map/unmap of an absent pair is a no-op once
both endpoints are validated. Many-to-many mappings remain permitted, including
unmapped hidden/draft lessons. Removing a final mapping may make readiness false;
it does not erase the Course flag or authorize continued Test-out (§7).

Structural identities are stable: a referenced Lesson/Chapter/skill cannot be
reparented into another Course in place. Reject such moves (409); use separately
approved create/copy/delete behavior instead. This bounded identity-stability rule
binds future Course authoring and Question tagging without approving their APIs.
Within-Course reordering preserves canonical positions. Existing NO ACTION FKs
continue to protect referenced identities on deletion. Same-Course mapping remains
an application responsibility even though UUIDs/FKs exist.

## 5. Explicit concurrency and ownership mechanism

> Read with [§13.3](#133-course-transaction-enforcement-and-contention-f3): the
> Course boundary and default graph-lock behavior remain; settings gain fail-fast
> admission on that same row/transaction and explicit enforcement requirements.

**Selection:** serialize Course configuration/graph/coverage-affecting writes on
the existing **Course row**, acquired by Course using PostgreSQL `SELECT … FOR
UPDATE`, before graph/structure/question/settings reads and writes. Execute at
**READ COMMITTED**, with all invariant inputs read after obtaining the guard.
This is an explicit new technical decision, not an assumption about current code
or framework defaults. Lock lifetime is the outer transaction's commit/rollback.
No migration, advisory-key mapping, global lock or new transaction library is needed.

Existing CourseManagementService at
`backend/src/main/java/com/codemastery/modules/course/application/service/CourseManagementService.java`
owns the public application boundary:

```java
public <T> T withCourseConfigurationWrite(UUID courseId, Supplier<T> work);
```

Only approved owning application use cases may supply the callback: Adaptive graph
commands/settings update, and Course/Assessment authoring changes that affect these
invariants. Course authenticates/admit-checks the owner, privately locks the Course,
then rechecks Course ownership/availability before invoking work. The guard starts a
write transaction or joins the same Course's compatible READ COMMITTED write
transaction; reject incompatible isolation/read-only context, null work or another
Course scope in a nested operation as internal contract error (500). No asynchronous
callback, `REQUIRES_NEW`, swallowed exception or early commit. Return callback value
only when the unit succeeds; when joining an outer transaction, the caller must
still commit before reporting HTTP success. Any error rolls back effects. No actor argument,
Course entity, repository or lock handle is exported. Not a general background or
learner-progress execution API; CT08/CT22 keep their distinct transaction authority.

All skill/edge/mapping mutations use it. Course lesson publication/deletion/ancestry
changes, settings writes, and Assessment question create/delete/retag/validity changes
must participate in the same guard before real readiness is claimed. Their full
feature operations remain with CT21/CT23. Graph reads used for an admission decision
and readiness validation run within the guard; owner display reads use a coherent
read snapshot and convey no durable permission. Existing actors may not bypass it
by calling private repositories from another module. Unsupported/uncoordinated
providers prevent enablement integration; they do not satisfy this contract by DDL.

Proof obligation: concurrent A→B and B→A additions serialize. The first commit can
succeed; the second reads it, detects the cycle and returns 409 with no new edge.
After a rollback the waiter recomputes against committed state. Concurrent coverage
loss and enablement commit in some order: enablement either rejects, or succeeds at
its snapshot and the later loss makes effective readiness false. A stored true flag
never grants an enduring readiness claim. This protocol covers phantom rows only
when **every** affected writer participates. Raw SQL/imports are not qualified writers.

Why selected: a Course already exists, so there is one stable serialization target
even for an empty graph/bank. Edge-only row locks miss absent/reverse edges;
precheck-then-insert without serialization admits write skew; SERIALIZABLE is viable
but adds abort/retry handling across participants. Global/table locks needlessly
serialize unrelated courses. No timeout or retry count/default is selected: aborted
or unavailable transactions fail safely (500), never a success or automatic mutation
retry. A future operational retry policy needs its own approval.

PostgreSQL mechanism references:
[row locks](https://www.postgresql.org/docs/17/explicit-locking.html#LOCKING-ROWS),
[READ COMMITTED snapshots](https://www.postgresql.org/docs/17/transaction-iso.html#XACT-READ-COMMITTED).
These justify feasibility; they do not prove application participation or tests.

## 6. CT06 production mastery policy

### 6.1 Evidence, history and educational decision LD-B1-1

Approve **latest eligible submitted attempt per assessed skill**, equal question
weight and no decay. This matches US-07's latest-assessment view, permits later
evidence to reveal weakness and keeps explanations reproducible for beginners.
Cumulative lifetime aggregation overweights repeated/old practice; best-score-only
cannot represent regression; time decay/weighted windows require unsupported parameters.
These are selected educational assumptions, not empirically validated learning claims.

Eligible evidence is server-accepted, fully submitted and graded quiz/diagnostic
history with complete captured question membership/order, authoritative nonnull
correctness, learner/attempt/quiz/skill identities and consistent Course references.
Both manual and automatic submission can qualify under the later CT04 lifecycle;
unsubmitted, rejected or partially graded attempts cannot. Every captured eligible
question for the assessed skill contributes one trial; an unanswered question is
incorrect only through Assessment's authoritative grading, never inferred from a
missing row. Malformed, missing or cross-Course evidence fails before any write.
No rounded `score_percentage`, client counts/states or assignment grade substitutes.

Select the maximum attempt for each (learner, Course, skill) by the lexicographic
tuple **(submitted_at, started_at, quiz_id, attempt_number)** ascending; timestamps
are UTC instants, quiz_id compares lowercase canonical UUID text, numbers compare
numerically. Attempt numbering is unique per learner/quiz, so this is total without
a new sequence field. UUID is solely a deterministic equal-time tie-breaker and
implies no pedagogical preference. A timestamp alone is not a total order; arrival
order is unstable under replay. Selection considers only attempts actually assessing
that skill. CT04/CT08 must preserve these fields/semantics in their later evidence
contract; this approves the selection policy, not an event schema or history-read API.

Count correct eligible items / total eligible items in the selected attempt for
that skill. Other skills keep their state; missing evidence is Unknown, distinct
from zero correct answers. A diagnostic initializes only assessed skills and cannot
erase unrelated earlier evidence. Duplicate/reordered delivery recomputes from
authoritative latest evidence, never incrementally accumulates or regresses to an
older attempt. A repeated identical input yields the same state and no extra
mastery/progress effects. Exact side-effect deduplication/replay and producer binding
remain CT08 execution obligations, not claims satisfied by this arithmetic.

History stays in immutable submitted Attempt/AttemptAnswer plus used Question
definitions, including skill association; no retention/deletion/decay of that source
history is approved. Current `LearnerSkillMastery` remains categorical, unique per
learner/skill; absent row means Unknown and explicit Unknown is still allowed.
No numeric accumulator, policy/event ledger or new timestamp column is introduced.
FKs alone do not prove immutability or complete capture. CT04/CT08 implementation
must demonstrate both before replay/integration; CT36 retains independent oracles.
Recommendation history remains unchanged under CT07, separate from raw mastery evidence.

### 6.2 Policy schema and LD-B1-2

Approve Course-configured values exclusively, **no production defaults or fixed
demonstration values**. The closed JSON object stored in `courses.adaptive_settings` is:

```text
{ weak: { numerator: integer, denominator: integer },
  mastered: { numerator: integer, denominator: integer },
  minimumQuestionsPerSkill: integer }
```

All keys are required; reject unknown keys, nulls, numeric strings, booleans,
fractional numbers and duplicate JSON keys. Every integer is in 1..2147483647;
numerator ≤ denominator; exact rationals satisfy `0 < weak < mastered <= 1`.
Normalize rational pairs by their greatest common divisor for canonical responses
and policy-change comparisons. `minimumQuestionsPerSkill` (`n`) is positive and
governs **bank readiness**, not sample size per submitted attempt. No floating or
rounded comparisons, confidence/minimum-attempt rule, or implicit prototype fallback.

Nonempty evidence: rate < weak → Weak; weak ≤ rate < mastered → Learning;
rate ≥ mastered → Mastered. No evidence → Unknown. All evidence-backed transitions
are allowed; Mastered is reversible; there is no hysteresis or automatic decay.
CT24's existing bounded-int records and exact long cross-products implement this
arithmetic unchanged (§9). Counts outside their domain are rejected, not truncated.
Invalid consumed policy is failure, not Unknown. Configuration is Instructor-owned
per Course under U08; SD03/CT15.F-adaptive must explicitly supply and justify demo
values later. Their absence does not leave this configuration contract undecided.

## 7. CT06 enablement and U08

> **Original r1 contract, retained as history.** [§13.1–13.2](#131-conditional-settings-writes-f1)
> supersede its unconditional write signatures and whole-Course/re-enable
> recomputation requirements. Only the r2 signatures/lifecycle are implementable.

### 7.1 Course storage and Adaptive validation boundary

Course owns both stored fields. In the existing CourseManagementService approve:

```java
public AdaptiveConfiguration readAdaptiveConfiguration(UUID courseId);
public void writeAdaptiveConfiguration(UUID courseId, boolean adaptiveEnabled,
        Map<String, Object> adaptiveSettings);
public record AdaptiveConfiguration(UUID courseId, boolean adaptiveEnabled,
        Map<String, Object> adaptiveSettings) {}
```

The record is nested and deeply immutable JSON data, never a Course entity.
Reads require Course ownership; absent legacy settings may return null only with
false. Write is permitted **only** from AdaptiveSettingsService's validated callback
inside §5's guard; Course verifies guard/owner and persists its own fields. It
rejects enabled/null and non-object data; Adaptive owns the full closed-schema
validation. No direct Course repository/entity dependency from Adaptive is permitted.

Existing `modules/adaptive/application/mastery/AdaptiveSettingsService.java` owns:

```java
public Configuration read(UUID courseId);
public Configuration update(UUID courseId, boolean adaptiveEnabled, Policy policy);
public record Policy(RationalThreshold weak, RationalThreshold mastered,
        int minimumQuestionsPerSkill) {}
public record Configuration(UUID courseId, boolean adaptiveEnabled, Policy policy) {}
```

Policy and Configuration are nested; RationalThreshold is the existing nested
MasteryStateClassifier type. API DTOs map to these approved application values;
Course storage maps carry exactly §6.2's canonical JSON. `read` may return null
policy for an unconfigured disabled Course; malformed existing object is 500,
never defaulted. Every update requires valid policy, even when disabling; clearing
policy is not an approved operation. Client schema violations → 400 with CT18 field
errors; internal invalid/null application values → IllegalArgumentException/500.
Guarded update writes fields only after validation and any required recomputation.
Provider failure aborts/rolls back, with no successful partial configuration.

### 7.2 Readiness and LD-B1-3

`adaptive_enabled` is **stored intent**. Effective routing/Test-out requires that
intent **and** current readiness. Under one §5-guarded view require:

1. Valid explicit §6.2 policy, at least one Course skill and one published Lesson.
2. A valid same-Course acyclic graph and no invalid mapping/reference.
3. Every published Lesson has at least one valid same-Course skill mapping.
4. Every Course skill (including those without a published Lesson) has coverage ≥ n
   from §3.3. Hidden lessons do not expand the published-Lesson predicate.

A requested true failing a known readiness predicate → 409; invalid input → 400;
unavailable/incomplete provider → 500. Disabled updates need valid policy but do
not require sufficient bank/mapping coverage; drafting and repairs remain possible.
`GET` returns intent/configuration, **not a cached readiness promise**. A later
authoring change may invalidate readiness without automatically rewriting Course
intent. Before any new routing/Test-out effect re-evaluate readiness under a
consistent guarded view (later system/learner execution binding remains CT08/CT22).
Unavailable readiness data fails closed. Ordinary authorized learning and canonical
progression remain available; never hard-block advanced content for missing mastery.

Disabled/not-ready: produce no new adaptive routing or mastery-based skip effect;
keep valid-policy quiz-driven categorical mastery updates, ordinary Assessment grading,
stored evidence and earned progress. US-06 is not waived by the flag. Missing/invalid
policy prevents a successful mastery update; never acknowledge a fake successful
update or substitute prototype constants. CT04/CT08 must enforce valid-policy
admission/failure and determine Assessment persistence coupling explicitly before
integrated execution; this record does not choose an event/listener transaction.

Policy threshold changes require deterministic whole-Course recomputation from
retained latest evidence and atomic publication under the new policy, including
coordination of any related progress under CT08/CT22. No mixed-policy results,
numeric inference from stored states, lazy next-learner-only update or automatic
revocation of earned completion. `n`-only changes revalidate readiness without
changing classification. Re-enable revalidates readiness and recomputes any stale
current mastery before effects resume. The §5 guard serializes relevant writes;
later CT08 must also coordinate concurrent submitted-evidence/mastery writes in
that unit. Until real history, CT08/CT22 participation and recomputation are
implemented, changed-policy/re-enable integrated execution is gated. The technical
policy is decided; implementation/integration is not certified or authorized here.

## 8. Bindings, REST and deterministic verification

### 8.1 Exact inventory and REST contract

> **Consumer compatibility:** [§16.2](#162-r2-bounded-ct16-no-content-transport-extension)
> adds the bounded CT16 no-content handoff for the five unchanged 204 operations.

All paths below are existing files under `backend/src/main/java/com/codemastery/`;
no new Java file/interface is authorized. Nested records listed above are explicitly
selected additions to their owning files. Domain repositories are module-private;
new capability requirements do not export JPA repositories/entities.

| Owner | Existing location and responsibility |
|---|---|
| Course | `modules/course/application/service/CourseAccessService.java` unchanged predicates; `CourseStructureService.java` minimal structure; `CourseManagementService.java` guard/settings persistence. Their own repository/adapter inventory performs private reads/locking/writes. |
| Adaptive | `modules/adaptive/application/skillgraph/SkillGraphService.java` graph/lookup/commands; `domain/model/SkillGraph.java` pure DAG; `domain/repository/SkillRepository.java` private graph/mapping persistence. Existing Skill/SkillPrerequisite/LessonSkill JPA repositories/entities remain private. |
| Assessment | `modules/assessment/application/service/QuestionBankService.java` coverage, its `domain/repository/QuestionRepository.java` and existing persistence adapters private. |
| Mastery | `modules/adaptive/application/mastery/AdaptiveSettingsService.java` validates/orchestrates U08/U09; `MasteryCalculator.java`/`MasteryStateClassifier.java` pure arithmetic. `MasteryUpdateService.java` later implements history/reclassification under CT08/CT22. |
| API | `modules/adaptive/api/controller/SkillController.java`, `AdaptiveSettingsController.java`; `api/dto/SkillRequests.java`, `SkillResponse.java`, `SkillGraphResponse.java`, `AdaptiveSettingsRequest.java`, `AdaptiveSettingsResponse.java`. |

The normative wire schemas/operations are in [OpenAPI](../api/openapi.yaml), marked
**APPROVED_NOT_IMPLEMENTED** per new operation. This status does not reclassify
the six existing implemented paths. Every selected operation requires current
authentication and **owning Instructor**, including reads. No Administrator
inheritance, anonymous authoring, learner graph-authoring read or answer-key exposure.
Future CT33 owns its distinct learner/instructor mastery view.

| Method and path (all prefixed `/api/courses/{courseId}`) | Success |
|---|---|
| GET `/skill-graph` | 200 complete GraphSnapshot wire projection |
| POST `/skills` | 201 created skill with server UUID |
| PUT `/skills/{skillId}` | 200 replaced name/description; identity/Course immutable |
| DELETE `/skills/{skillId}` | 204; referenced skill conflicts |
| PUT / DELETE `/skills/{skillId}/prerequisites/{prerequisiteSkillId}` | 204, idempotent pair operation |
| PUT / DELETE `/lessons/{lessonId}/skills/{skillId}` | 204, idempotent pair operation |
| GET / PUT `/adaptive-settings` | 200 stored intent/policy; PUT validates and commits §7 |

POST/PUT bodies are closed objects; description is optional/null or nonblank,
name required/nonblank. PUT skill replaces omitted description with null. No
client IDs, bulk replace, arbitrary JSON patch, per-item partial success, pagination
or new search/filter contract is introduced. Pair operations have no request body.
Use CT18.minimum's existing RFC9457 ProblemDetail shape, safe English details and
sorted field errors. 401 generic authentication; 403 wrong role/published non-owner;
404 concealed missing/hidden non-owner/scoped foreign descendant; 400 malformed
UUID/body/text/policy or self-edge; 409 cycle/referenced delete/readiness rejection;
500 unavailable/corrupt producer or unexpected transaction failure. Course
admission precedes descendant lookups; no foreign IDs/keys/SQL/stack traces leak.
New graph/policy predicates are feature-specific mappings, not a redesign of CT18.
No automatic client retry; existing FE03 clears sessions on 401, not 403/404/409.

### 8.2 Required oracles (specification, not executed tests)

| ID | Deterministic case and required result |
|---|---|
| B1-O01 | Owner maps same-Course Lesson/skill → one pair; duplicate → unchanged. Foreign Lesson/skill → 404/no pair. Learner/Admin → 403 before lookup. Anonymous → 401. |
| B1-O02 | Add A→B, B→C then C→A → 409; no C→A. Self-edge → 400. Duplicate edge unchanged; missing endpoint 404. Arbitrarily long path still rejects a closing edge. |
| B1-O03 | Two real PostgreSQL transactions race A→B/B→A behind a barrier. At most one commits; waiter observes committed winner and returns cycle 409; final graph acyclic. Winner rollback permits waiter to recompute/succeed. |
| B1-O04 | Independent Course graphs do not share the guard. Concurrent same-Course mapping/publication/question change and enablement use one guard; no admission from a mixture of inputs. Fault a provider inside callback → rollback graph/settings changes. |
| B1-O05 | One Question used in two quizzes counts once; unused valid Question counts; explicit requested skill without questions returns zero. Partial/wrong-Course/negative/null provider output fails, never enables. |
| B1-O06 | Symbolic valid policy: no evidence → Unknown; zero successes/nonempty → Weak; exact weak → Learning; exact mastered → Mastered. Maximum-int counts/thresholds stay exact; invalid policy rejected. Test fractions are explicit local fixtures, never production defaults. |
| B1-O07 | Newer eligible attempt assesses A only: A may move Mastered→Weak; B unchanged. Replay older/duplicate attempt cannot regress/double-count. Equal timestamps resolve by §6.1's complete tuple; missing answer/null correctness fails with no writes. |
| B1-O08 | Empty Course/graph, unmapped published Lesson or any skill below configured n prevents enable (409). Hidden unmapped Lesson alone does not. Distinguish provider error (500) from insufficient count. |
| B1-O09 | Disable preserves history/progress; new valid-policy quiz still updates assessed mastery. Not-ready or disabled produces no new routing/Test-out. No-policy update fails; canonical Lesson access still uses CT03. |
| B1-O10 | Equivalent reduced rational policy is unchanged. Threshold change recomputes all affected evidence under one policy or rolls back entirely; n-only change does not reclassify. Re-enable requires fresh coverage. No completion revocation is inferred. |
| B1-O11 | Delete skill referenced by edge/mapping/Question/mastery → 409/no cascade. CT40 structure exposes canonical identities/positions only; Learner excludes hidden lessons; owner sees drafts. Provider failure never returns an incomplete graph/structure. |

CT14 bounded placement (§9) allocates these obligations in existing suites; actual
PostgreSQL/concurrency/security tests and independent CT36 arithmetic review remain
required when implemented. Doubles verify local consumption only. This documentation
approval has executed no new graph/settings/provider/integration implementation.
Existing pure-calculator regression results are reported in §12, separately from
the new production and integrated oracle obligations.

## 9. Amendments, prerequisites and compatibility

| Revision and disposition | Prior authority/history preserved | Exact change and reason |
|---|---|---|
| `CT03-B1-2026-11-08-r2` — DELEGATED_APPROVED | CT03-2026-10-21-r1, PR #92, ADR001 §12; confirmed effective by ADR004/PR #227 | Complete CT03.consumers' U07 structural/no-extra-read gate through §3.1, and approve Course-owned structure/guard/settings boundaries §§3.1/5/7.1 only. Additive methods are in existing Course services; all seven access assertions, role/error precedence, U03/U04 and catalogue stay unchanged. This resolves a previously excluded dependency, not a claim it was already approved in r1. |
| `CT24-CALCULATION-2026-11-08-r2` — DELEGATED_APPROVED | ADR002 CT24.calculation, 11 October, PR #73; pure AD09 in PR #86 | Supersede **only** the Interim-only/Final-reuse deferral for the same calculator/classifier signatures, records, exceptions and int/long arithmetic. Permit Final CT06 policy with explicit validated parameters. No default thresholds, I/O, aggregation, decision callable or cross-module calculator API. Reuse avoids a duplicate production calculator. CT06.prototype's illustrative values and scope remain unchanged. |
| `CT14-B1-COVERAGE-2026-11-08-r1` — DELEGATED_APPROVED | ADR002 CT14.interim and CT14-FINAL F1/F2, PR #106; existing inventories in testing.md | Approve only Batch 1 oracle placement in existing graph/settings/pure-mastery and Adaptive integration suites; no new path/framework/runner, whole CT14.coverage or E2E approval. Necessary to hand off real DAG/concurrency/coverage evidence without global testing scope expansion. |
| `CT18-B1-ERRORS-2026-11-08-r1` — DELEGATED_APPROVED | ADR002 CT18.minimum, PR #73; safe shared ProblemDetail and existing 400/409 BusinessRuleException | Approve only §8.1's Batch 1 feature-specific failure mappings and consumers. Preserve the shared envelope, authentication/concealment, session and validation conventions; add no new exception file, pagination policy or whole CT18 approval. This is the bounded absorbed error-policy scope of CT05.bindings/CT06.enablement. |

The old CT06 proposal in adaptive-learning §19 / ADR001 §13 / data-model note was
**never effective**; §§6–7 now supersede its corresponding recommendations and
resolve LD-B1-1/2/3 under the present delegation. Preserve its 19 October baseline
and PR #86 observations as history, with the existing 8 November correction.
No original approval is erased or retrospectively attributed to Codex/Leader review.

Consumers share **these** CT40 artifacts: CT03 uses §3.1 for U07 closure; CT05
mapping consumes §3.1; CT06 uses §§3.1–3.3/5/7; CT23 must consume §§3.2–3.3 and the
Course guard, with its later question/composition rules. This ADR supplies no
competing ports. CT33.map consumes the graph/mapping representation, but its public
learner read/result projection is unapproved. CT07/CT25/CT36/CT15 consume only the
policy/representation actually needed; no other future checkpoint is approved.

## 10. Integration and implementation obligations

All nine Batch 1 technical dispositions and the four prerequisite revisions above
are approved. There is **no unresolved HARD technical approval prerequisite** for
these declared scopes. That is separate from the following retained gates:

- This PR must be reviewed/integrated through active `main` rules, without Codex
  approval/merge/bypass. Required CODEOWNER review, independent last-push approval,
  resolved threads, current-head Backend/Frontend/CodeQL and six VM checks remain.
  `/docs/` targets are sole-Leader-owned; a PR on the Leader's account cannot
  self-satisfy that review. LD-09's compliant author/review route remains an
  integration decision; PR #227's merge does not authorize another bypass.
- Module owners Course @TamiaoNguyen, Adaptive @thanhtran0311, Assessment
  @minhtan29042006, Frontend @heizseung and relevant QA/Leader reviewers have
  **not** been represented as having reviewed these decisions. Delegated approval
  does not fabricate their confirmation or GitHub review.
- Separate implementation authorization for AD07/AD08/AS05/AS18/CO04/CO14/FE15/FE18
  and other consumers. Most new services/DTOs/graph repositories are empty at the
  inspected baseline; CourseAccessService and pure calculator/classifier have code.
  No new contract operation is implemented by this PR, and no defect in those empty
  scaffolds is disguised as an executed guarantee.
- CT04/CT08 must approve actual qualifying submission capture, evidence payload,
  history-read binding, system actor, replay/failure mechanics and Assessment
  persistence coupling; CT08/CT22 approve actual atomic mastery/progress execution.
  They must satisfy §§6–7 before integrated update/reclassification can run.
  These downstream execution gates do not reverse the verified HARD approval order.
- CT21/CT23 must implement coordinated Course/Question writers; no readiness claim
  until all relevant mutation paths obey §5. Used-question/answer immutability and
  complete Course-consistent evidence require real provider verification.
- Final CT07/CT24.decision/CT25/CT33/CT36/CT15 data, oracles, routing, diagnostic,
  learner reads, evaluation and demo values remain excluded. No deployment,
  production admission, external QA or learning-outcome result is claimed.

## 11. History and disclosure

- **8 November 2026:** Codex restored the corrected eight-batch context, independently
  evaluated alternatives, and technically approved the nine checkpoints and bounded
  prerequisite revisions under the named explicit delegation. The PR commit records
  the exact revision; Issue entries link its unmerged PR and preserve older bodies.
- AI assistance for this record and synchronized contract documentation: **Codex
  (OpenAI)**, for source inspection, technical analysis, drafting and consistency
  validation under delegated authority. This is not personal Leader/module review
  or independently executed application verification.

## 12. Validation of this documentation revision

> These are the original `8bc7469` results, not evidence for the later §13 correction.

Executed on 8 November 2026 against the inspected main baseline and this docs-only
working tree, before the PR. Existing implementation bytes were unchanged.

| Check | Actual result and limit |
|---|---|
| `git diff --check` | Passed. |
| Local Markdown link/anchor and decision/register checks | 193 relative links/anchors resolve; exactly CP-002–CP-010, all five verified HARD edges in order, 13 indexed approval revisions. Original lines in six historical Markdown records preserved in order. |
| OpenAPI parsing/consistency | JSON parses without duplicate keys; 135 internal references resolve; ten new secured operations marked APPROVED_NOT_IMPLEMENTED; all six original paths and existing schema/response/header/security components unchanged. This is parsing/reference validation, not a claim of an external OpenAPI conformance certification. |
| `cd backend && ./mvnw -Dtest=MasteryCalculatorTest,MasteryStateClassifierTest test` | 27 tests passed, 0 failures/errors/skips; existing pure arithmetic only. No graph/coverage/history/transaction behavior is proved by this run. |
| `cd frontend && npm test -- src/features/catalogue/pages/CourseCataloguePage.test.tsx` | 12 tests passed; existing OpenAPI-consuming catalogue regression only. |

New graph/settings/provider and production-history tests were not implemented or
run. Required protected CI is evaluated separately on the PR head; no green CI,
human review, PostgreSQL integration or independent oracle reproduction is inferred.

## 13. Focused F1–F4 amendments — 8 November 2026

**DELEGATED_TECHNICAL_APPROVAL; DELEGATED_APPROVED**, independently selected by
Codex under the Project Leader's explicit **“CodeMastery — PR #228 Focused Technical
Corrections & Revalidation”** instruction. This is an amendment to the same PR,
not a new batch approval, personal Leader/module review or implementation authority.

Evidence: independent read-only review of `8bc7469d5376ff478b0af821db6db4a6c4406636`,
completed 8 November at 19:07 ICT, review session
`01a1bb00-ad7e-7033-8449-5f9c69825c2a`. Its F1/F2 MEDIUM findings concern stale
replacement and unbounded reclassification; F3 is an implementation acceptance
obligation, F4 an educational limitation. The review is local evidence, not a
submitted GitHub review. Its earlier executed tests are not reruns of this revision.

| Amendment | Exact predecessor and retained scope | Replacement scope |
|---|---|---|
| `CT06-ENABLEMENT-2026-11-08-r2` | `CT06-ENABLEMENT-2026-11-08-r1`; preserve readiness, ownership, configuration schema, disabled-mode mastery and earned progress | §§13.1–13.2 replace unconditional settings writes and automatic whole-Course threshold/re-enable recomputation. Approve only the necessary Assessment history-presence read below, not CT04 lifecycle or CT08 execution. |
| `CT03-B1-2026-11-08-r3` | `CT03-B1-2026-11-08-r2`; preserve U07, structural projection, all seven assertions and Course-owned guard | Expected-state storage write, fail-fast settings admission on the same guard, and §13.3 acceptance requirements. |
| `CT14-B1-COVERAGE-2026-11-08-r2` | `CT14-B1-COVERAGE-2026-11-08-r1`; preserve existing test paths/harness and B1-O01–O09/O11 | Replace B1-O10 as below; add precise F1–F4 implementation/evaluation obligations. No new test file, runner or complete CT36/CT14 Final approval. |
| `CT18-B1-ERRORS-2026-11-08-r2` | `CT18-B1-ERRORS-2026-11-08-r1`; preserve graph errors, safe ProblemDetail and authentication/concealment | Settings-only 412/428 and precise 409 lifecycle/busy mappings in §13.1. |

The other eight checkpoint revision IDs and `CT24-CALCULATION-2026-11-08-r2`
are unchanged. F4 adds a limitation/verification obligation to CT06.production r1,
not a new evidence rule or minimum sample size. All original IDs remain indexed;
only the replaced clauses above cease to govern. The five HARD edges remain intact.

### 13.1 Conditional settings writes (F1)

> **R1 refinement:** [§16.1](#161-r1-one-guarded-http-precondition-decision) replaces
> only the historical expected-tag input/parsing channel and 412 detail below with
> complete guarded tag conditions. The remaining F1/N1 decisions are preserved.

Select **strong state ETag + required If-Match** for the existing settings GET/PUT.
Keep the JSON request/response fields unchanged. GET emits `ETag`; GET and successful
PUT emit `Cache-Control: no-store`. Successful PUT MUST NOT emit validator fields,
including ETag or Last-Modified, because this operation transforms the submitted
representation. Clients must subsequently GET the canonical body and its ETag
before another update ([N1 correction, §15](#15-n1-http-validator-correction)).
A tag represents this Course's **configuration**,
not readiness, history, mastery, an audit revision or a lasting authorization.

Use the exact compact UTF-8 JSON response bytes, with keys in this order:
`courseId`, `adaptiveEnabled`, `policy`; policy keys `weak`, `mastered`,
`minimumQuestionsPerSkill`; rational keys `numerator`, `denominator`. Course UUID
is canonical lowercase, booleans/integer numbers use JSON notation, policy may be
null only for a disabled legacy read, rationals are GCD-normalized, no whitespace
or trailing newline. Return identity content encoding for this representation.
The strong ETag is `"sha256-<64 lowercase hexadecimal SHA-256 digits>"` over those
bytes. No extra hash library is required; Java supplies SHA-256. The Course ID
prevents cross-Course reuse. This fixed representation makes the validator strong;
it must not be reused if middleware changes the representation bytes/encoding.

PUT requires a syntactically valid If-Match list of entity-tags. Match if **any
strong** member equals the current tag; weak members never match. A weak-only
list therefore fails 412. This API profile rejects wildcard `*` with 400 because
existence alone does not protect an Instructor's read state. Missing header → 428;
empty/malformed header → 400. No missing/default validator is allowed internally.

After authentication, Course ownership/concealment and ordinary request validation,
acquire the Course guard, **freshly** read configuration and evaluate If-Match
before history/readiness queries or any write. A different current configuration
→ 412, including a repeated request whose original tag is now stale even when its
desired body happens to equal current state. The old tag does not authorize a
write merely because the desired policy is valid. Failure returns no new tag or
current configuration in the error body; the client must GET again. Guard contention
may fail 409 before a fresh comparison can run; it never justifies ignoring the tag.

**ABA limit:** A→B→A with byte-identical canonical configuration yields the same tag.
Such a request can proceed if all **current** lifecycle/readiness checks also pass.
This is deliberately an expected-current-state contract, not “no intervening write”.
No audit/event revision is claimed. History appearing while configuration stays A
does not change the tag, but §13.2 still prohibits threshold changes. Durable revision
columns, PostgreSQL `xmin`/`ctid`, process-local counters or timestamps are not selected:
the schema has no monotonic settings version, and database/system tuple identities
are not stable application revisions. Strict no-intervening-write semantics would
require a separately justified durable revision and migration. For the declared
state-replacement operation, that additional persistence is unnecessary.

Application contracts, all nested types in existing files:

```java
// AdaptiveSettingsService; replaces only update/Configuration in §7.1
public Configuration update(UUID courseId, Set<String> expectedStrongEtags,
        boolean adaptiveEnabled, Policy policy);
public record Configuration(UUID courseId, boolean adaptiveEnabled,
        Policy policy, String etag) {}
// CourseManagementService; replaces only the storage write in §7.1
public void writeAdaptiveConfiguration(UUID courseId, AdaptiveConfiguration expected,
        boolean adaptiveEnabled, Map<String, Object> adaptiveSettings);
```

The HTTP adapter parses the list and passes immutable strong tags (an empty set
from a weak-only list produces a failed match); no arbitrary caller actor is added.
The application compares the freshly derived tag inside the guard. It then passes
the raw Course snapshot it just read as `expected` to Course's write. Course verifies
the same held guard and fresh `(courseId, adaptive_enabled, adaptive_settings)`
expected state with structural JSON equality, not JSON string formatting. An internal
expected-state mismatch aborts as safe 500: it indicates an invalid guarded caller
or provider, not permission to persist stale state. The externally supplied stale
tag has already been handled as 412. `read` returns etag for the same immutable
snapshot/body; `etag` is response metadata, **not** an added JSON property.
The application Configuration type is unchanged; only GET exposes its etag as an
HTTP validator. The PUT adapter must omit validators even if the application result
contains an internally derived tag.

| Settings failure | HTTP and client behavior |
|---|---|
| Missing If-Match | 428 Precondition Required; no write; GET and obtain a validator. |
| Malformed/empty If-Match, wildcard or malformed body/policy | 400; no write. |
| No strong tag matches fresh current state | 412 Precondition Failed; preserve local draft, GET latest and require an explicit user reconciliation/resubmission. |
| Thresholds frozen, readiness insufficient, or Course guard busy | 409 Conflict; no write; explain safely and refresh before any deliberate retry. |
| Provider/guard compatibility/deadline/cancellation failure before commit | Safe 500 and complete rollback; no implicit success/retry. An unavailable provider is never “no history”. Commit/response uncertainty is handled explicitly in §13.3. |

401/403/404 retain existing precedence; missing preconditions do not disclose hidden
Course state. 412/428 use the unchanged CT18 ProblemDetail envelope and standard
titles, safe fixed English detail, no old/new values or SQL; responses are no-store.
They are not `BusinessRuleException` statuses (that class remains 400/409). Future
HTTP binding uses Spring's existing native `ErrorResponseException`/MVC handling for
these statuses, with a bounded application rejection mapped by the adapter; no new
top-level exception file/library or unsanitized exception reason is approved.

For a failed application tag comparison, use the explicitly approved nested
`AdaptiveSettingsService.ConfigurationPreconditionFailed` runtime rejection;
the HTTP adapter maps that type to the safe 412 above. The fail-fast Course entry
uses nested `CourseManagementService.ConfigurationBusy` only for the known
PostgreSQL row-lock-not-available outcome (SQLSTATE 55P03), mapped to 409 **after**
rollback. Other SQL/timeout failures remain safe 500. These nested types export no
state/diagnostic payload and add no file. Missing/malformed headers are rejected at
the HTTP boundary; the internal expected-tag set is nonnull (empty means no match).

Alternatives: an expected-state JSON body is feasible but duplicates the settings
payload and comparison convention; an incrementing revision is stronger against ABA
but unnecessary for current-state semantics and needs schema/lifecycle work; separate
toggle/policy endpoints still need stale-write detection and expand the REST surface.
HTTP basis: [RFC9110 If-Match](https://www.rfc-editor.org/rfc/rfc9110.html#section-13.1.1),
[RFC6585 428](https://www.rfc-editor.org/rfc/rfc6585.html#section-3).

### 13.2 Threshold lifecycle and bounded settings work (F2)

Select **configure thresholds before history; freeze afterwards**. No synchronous
whole-Course history replay, background job, policy ledger, migration, numeric
accumulator or new automatic completion effect is approved by the settings endpoint.

Inside the guard, after the precondition passes, compare normalized weak/mastered
ratios with the current policy (GCD-equivalent fractions are unchanged). A null
legacy policy becoming a valid policy also counts as a threshold change.

| Request | Decision |
|---|---|
| Initial configuration or changed thresholds; no submitted attempts and no stored mastery rows in the Course | Permit, subject to explicit valid policy and requested enablement readiness. |
| Changed thresholds (including null→policy repair); any submitted attempt **or any stored mastery row**, even explicit Unknown | Reject 409 with no effects. Disabling in the same request does not bypass the freeze. A separately approved reclassification/repair capability is required; none is silently promised here. |
| Same normalized thresholds; enable/disable or `n` change | Permit with matching tag and valid policy. Every requested true is revalidated for current readiness; false does not require sufficient mapping/bank coverage. No reclassification. |
| Re-enable under unchanged thresholds | Fresh readiness validation only; no whole-Course catch-up. Quiz-driven mastery must have continued while disabled under CT08's eventual execution contract. |
| Stale/corrupt current mastery or missed updates discovered during execution | Fail closed on adaptive effects and use CT08's separately approved recovery path; flag toggling does not repair/recompute history. Evidence and earned progress are preserved. |

The conservative barrier is **any submitted attempt**, not just a producer's claim
of valid qualifying evidence. Malformed/partial submitted history must not be
treated as absence. The necessary bounded prerequisite is Assessment-owned, in the
existing `modules/assessment/application/service/AttemptService.java`:

```java
public boolean hasSubmittedAttempts(UUID courseId);
```

It consumes Course ownership, reads only Assessment's quizzes/attempts and returns
whether any Course quiz has an attempt with nonnull submitted_at. Include hidden
quizzes, all learners and kinds; no answer payload, history export or pagination.
Unavailable/inconsistent provider → 500. Adaptive separately checks existence of
**any** `LearnerSkillMastery` row for its own Course skills. Both are existence
probes, not full history loading, counting or per-learner recomputation. They run
after the Course guard is acquired and only when thresholds change. This read is
approved only as the prerequisite to this lifecycle decision; CT04 start/submission/
results and actorless evidence APIs remain unapproved. No new Question/Course field.

CT04/CT08 must serialize **first submitted evidence and first mastery-row creation**
with this same Course-root mutex, before lower-level writes. If submission commits
first, a threshold edit sees the barrier and rejects. If the edit commits first,
the submission uses the newly current policy. Until that participation is approved,
implemented and verified, real threshold edits concurrent with Assessment execution
are not qualified. Never infer no history from a local double, missing provider,
stale persistence context, a zero answer count or a disabled flag. Enforcing the
barrier is monotonic because submitted history/used definitions cannot be deleted
through this contract; legacy mastery alone is also conservative refusal.

Preserve the same Course guard and enrollment lock identity. For **settings only**,
add an entry in the existing CourseManagementService:

```java
public <T> T tryWithCourseConfigurationWrite(UUID courseId, Supplier<T> work);
```

It has the same ownership/transaction/rollback contract as §5, but acquires the
same Course row with `FOR UPDATE NOWAIT`. Existing graph/default guard behavior and
FE07 enrollment operations are unchanged. A known row-lock-not-available outcome
is a safe 409 after transaction rollback; it is not success, no-history, or automatic
retry. Do not use SKIP LOCKED (which could falsely imply a missing Course). Owner
admission occurs before acquisition; Course state is rechecked once acquired.
This is a fail-fast entry on the existing boundary, not a new transaction mechanism.

The guarded body performs only fresh configuration/predicate reads, necessary graph/
coverage validation and the Course configuration write. No learner loop, external
I/O, callback network call, task waiting, sleep, new transaction or historical replay.
Do not select/mutate Enrollment/Progress rows during a settings request. Enrollment
can still wait briefly behind a settings transaction, and graph/coverage size affects
validation cost: no measured latency guarantee is claimed. NOWAIT bounds the Course
**row-lock** admission, not relation locks, queries or total duration. Implementation
acceptance must demonstrate finite database statement/transaction deadlines and
cancellation, rollback and lock release with explicit test budgets and recorded
configuration; zero/unbounded defaults are not acceptance evidence. No undocumented
timeout number or environment variable is selected by this documentation amendment.

This removes the review's unbounded historical work from the shared critical section.
Unrestricted synchronous replay is rejected for enrollment/pool contention and
missing workload qualification. Versioned staged/asynchronous reclassification is
deferred as unnecessary architecture/persistence complexity. Future reclassification
requires separate approval of publication, work bounds, cancellation, policy provenance
and CT08/CT22 atomic effects before accepting post-history threshold edits.

### 13.3 Course transaction enforcement and contention (F3)

Retain Course ownership, READ COMMITTED, one physical database transaction and the
same Course-row lock until outer commit/rollback. Implement both guard entries using
the existing Spring transaction facilities with **explicit programmatic enforcement
in the existing service**, so same-service authoring calls receive the same semantics.
No new transaction library, service/interface file or proxy self-injection is selected.

Implementation acceptance requires real Spring/PostgreSQL evidence that:

- Same-bean/self-invocation and external calls both start a real transaction when
  none exists. A method annotation alone is not proof of interception.
- Joining a compatible outer transaction shares the same physical transaction and
  root lock. Check actual isolation and read-only state **before callback effects**;
  reject read-only, REPEATABLE READ/SERIALIZABLE, wrong-Course nested scopes and
  incompatible contexts as 500. Spring's lenient REQUIRED defaults are insufficient.
- Callback exceptions, rollback-only completion, an outer failure after successful
  callback return, and cancellation/deadlines observed before commit must abort the
  transaction with no committed changes. HTTP success is emitted only after outer
  commit. Do not promise rollback for a cancellation arriving after commit, or a
  connection failure whose commit outcome is unknown. Report safe failure/transport
  loss without an automatic retry, then require a fresh authorized read and user
  reconciliation; a thrown exception alone is not proof of rollback. Tests distinguish
  confirmed rollback from committed or indeterminate response loss. No savepoint rollback releases
  the guard followed by continued work under a stale “held” marker; mark rollback-only.
- Do not trust pre-lock JPA entities, first-level/second-level cache or earlier
  authoring snapshots. After waiting/acquisition, explicitly refresh/reload invariant
  inputs and evict/refresh conflicting managed state **before any flush**; prove a
  queued stale managed update cannot flush over the winner. Never clear away unrelated
  pending changes to make a test pass; reject incompatible pending write contexts.
- Acquire the Course root before graph, Assessment, mastery or progress locks/writes.
  CT08/CT22 must define compatible ordering before integration. A nested different
  Course scope is rejected; this contract approves no cross-Course write unit or
  reverse-order acquisition. Do not join after an outer caller already acquired
  lower-level locks or queued invariant writes; reject before callback effects.
- NOWAIT settings contention with enrollment/graph returns 409 and preserves both
  participants. Waiting graph writers retain the B1-O03 cycle oracle. Verify owner
  denial, lock release after rollback, independent Course concurrency and a waiting
  enrollment observing the committed/rolled-back result. Use barriers, not sleeps.

Mechanism evidence: [Spring self-invocation](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/annotations.html),
[outer transaction joining](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/tx-propagation.html),
[PostgreSQL NOWAIT](https://www.postgresql.org/docs/17/sql-select.html#SQL-FOR-UPDATE-SHARE).
These are acceptance obligations, not a claim that the empty guard has been implemented.

### 13.4 Small-sample limitation and CT36 handoff (F4)

CT06.production r1's latest eligible attempt per assessed skill, equal question
weight/no decay, exact arithmetic and ordering remain unchanged. One correct
eligible question yields Mastered and a subsequent one-question failure yields Weak
for **every** permitted threshold pair. A large question bank and `n` do not increase
the sample in the selected attempt. These labels are categorical rule outcomes,
not statistical confidence or proof of durable knowledge. Do not silently add a
minimum sample, smoothing, weighting or a rolling window during implementation.

CT36 must include parameterized small-sample and alternating-result scenarios before
Final evaluation acceptance: single correct/incorrect attempts, repeated alternation,
many older successes replaced by one newer failure, unassessed-skill preservation,
equal-time ordering and a larger bank with the **same** one-question evidence. Report
actual states/changes and this sensitivity, without claiming an achieved learning
benefit or inventing a new quantitative target. CT07/Test-out consumers and demo
explanations must disclose the limitation; no routing rule or CT36 Final dataset is
approved here. A minimum-evidence/window policy remains a separate future decision.

### 13.5 Verification, direct consumers and retained gates

**B1-O10 is superseded**: equivalent ratios are unchanged; pre-history threshold
edits succeed; post-submission or any stored-mastery threshold change returns 409
with no effects; n-only and same-policy toggles never reclassify; re-enable checks
readiness only. The former whole-Course recomputation oracle is historical.

| New oracle | Required result before implementation acceptance |
|---|---|
| B1-O12 conditional replacement | Two tabs read P; first disables/changes configuration; second PUT with old tag → 412, unchanged newer state. Same-current-state ABA is explicitly allowed, subject to fresh lifecycle/readiness validation. Missing→428; malformed/wildcard→400; weak-only/nonmatching/foreign-Course tag→412. |
| B1-O13 history race | A matching tag with submitted history or any mastery row blocks threshold change. Race first submission/first mastery creation versus edit under the common root; whichever commits first determines the permitted policy. Provider failure→500/no write; no direct foreign repository access. |
| B1-O14 transaction reality | Self-invocation, compatible/incompatible outer contexts, callback/outer/commit failures, rollback-only and stale managed state satisfy §13.3 on real PostgreSQL. Verify committed state and actual transaction/lock lifetime, not mocked callbacks. |
| B1-O15 contention and duration | Enrollment holds root: settings NOWAIT→409/no change. Settings holds root: enrollment proceeds after commit/rollback; cancellation/deadline releases locks. Finite statement/transaction budgets are recorded; no history-sized loop, unbounded wait or connection-pool exhaustion is accepted. |
| B1-O16 sample sensitivity | For explicit valid fixture policies, one correct→Mastered, next one incorrect→Weak; alternation and older larger samples behave exactly as latest-attempt policy. Changing bank n does not change the evidence ratio. CT36 must review these limitations independently. |
| B1-O17 canonicalizing PUT | Apply §15's N1 cases: fraction normalization, successful PUT without any validator, subsequent GET body/tag pairing, stale If-Match refusal and intervening-writer/failed-refresh behavior. No endpoint execution is claimed here. |

CT14 uses existing `AdaptiveSettingsServiceTest.java` for local conditional/lifecycle
cases, `AdaptiveLearningFlowIntegrationTest.java` for O12–O15 real providers/guard/
enrollment races, and `MasteryProgressTransactionIntegrationTest.java` for CT08/CT22
first-evidence/outer-rollback ordering when their contracts exist. O16 belongs in
existing `AdaptiveProfileScenarioTest.java` under CT36's later scenario acceptance;
pure calculator/classifier suites retain their current scope. No new test path.

AD08/CO14 implement the amended exact signatures; the existing AttemptService is the
only new producer location. FE18's `skillGraphApi.ts`, `useSkillGraph.ts` and
`AdaptiveSettingsPanel.tsx` must keep body+ETag together, supply If-Match on **every**
settings write and retain local drafts on 412/409. GET latest and require deliberate
reconciliation; never replace the tag and automatically retry an old full body.
After successful PUT, invalidate the client's previous write validator and GET again
before permitting another update. Pair only that GET's body and ETag; never combine
the PUT response body with a later GET's tag. A failed follow-up GET does not undo
the committed PUT and does not authorize automatic PUT replay.
Apply the owning feature's Course/request-generation checks to body and ETag together;
an older response cannot replace either half of a newer snapshot.
Do not clear Auth on 412/428/409 or show disabled stale switches as authoritative.
Persist no credentials/ETag-based authorization; the tag is not an access capability.

The current shared HTTP client accepts request headers but returns decoded body
only and lacks 412/428 ProblemDetail titles. Its later authorized implementation
needs a bounded, additive way to observe the successful GET response's ETag (only after
status/decoding/current-session checks), preserving existing body-returning callers,
cancellation and generation isolation. Cross-origin consumers require explicit ETag
exposure and If-Match allowance in the existing CORS configuration; localhost
browser/production-origin proof remains separate. This consumer requirement does
not certify current transport/UI support or approve a new frontend store/framework.

No checkboxes asserting executed providers, owner review, CT36 Final approval or
implementation completeness may be ticked by this amendment. Existing nine Batch 1
approvals remain technical approvals; protected review/integration and separate
implementation authorization remain required. No new HARD approval edge to a future
batch is introduced: bounded owner-only history presence is approved here; actual
submission/progress/system-actor integration remains CT04/CT08/CT22-owned.

## 14. Focused revalidation and independent final assessment

> **Current assessment:** [§16](#16-r1r3-independent-review-corrections--9-november-2026)
> records the R1/R2 refinements and current external CodeQL blocker. The assessments
> and readiness conclusions below are retained as the historical F1–F4/N1 review,
> through `4d8ac216`; they do not establish acceptance of its R1/R2 gaps or satisfy
> current protected-integration gates. The current overall verdict is in §16.5.

The fresh critical review considered the amended decisions and their direct
consumers, including adverse schedules rather than treating successful parsing or
the original approval as sufficient evidence:

| Finding | Final assessment of the corrected decision and direct consumers |
|---|---|
| F1 | State-based conditional replacement prevents a stale form from reversing a newer different policy/enablement value. Adaptive compares after locking; Course independently checks the fresh expected snapshot; FE18 must preserve draft/body/tag sequencing and explicitly reconcile errors. Same-state ABA is intentionally permitted and is not represented as an audit version. No revision persistence is required for that stated semantic. |
| F2 | Pre-history changes plus a conservative submitted-attempt/mastery barrier avoid mixed-policy persisted states without a replay job. Unchanged-policy enable/disable/n changes remain supported. The owner-only Assessment existence provider and CT08's first-evidence mutex participation are explicit; a missing producer cannot count as absent history. NOWAIT settings admission preserves the same physical lock and default graph behavior; finite query/transaction/cancellation qualification remains mandatory before runtime acceptance. |
| F3 | Programmatic enforcement and adverse real-database obligations cover self-invocation, incompatible outer transactions, dirty/stale persistence contexts, root-first ordering and outer rollback. Confirmed rollback is distinguished from indeterminate commit/response loss. The boundary is retained; no implementation or tested transaction behavior is claimed for the empty service. |
| F4 | Latest-attempt arithmetic/order is unchanged. One-item success/failure sensitivity is proven from the permitted threshold inequalities and required as a CT36 evaluation limitation. No sample floor, confidence claim or unapproved smoothing/routing rule is introduced. |
| N1 | §15 corrects the successful-PUT validator requirement identified at `f226799a8cd1b8dbdc4621d03fe9c879f155bbb5`. GET retains the canonical strong ETag; canonicalizing PUT omits all validators; clients GET again before updating. If-Match comparison, Course serialization, history freeze and CT18 failure mappings are unchanged. |

**Technical verdict: READY_FOR_PROTECTED_REVIEW** for the documentation contract
as corrected through §15, including the N1 HTTP correction. The earlier verdict
at `f226799a` did not account for N1; it is not independent acceptance of the faulty
PUT response rule. No material contradiction remains in the corrected declared scope.
The complete five-file N1 diff was independently revalidated on 9 November 2026;
the current results and limits are recorded at the end of §15.
This is not GitHub approval or merge eligibility: current-head checks, eligible independent
CODEOWNER/last-push review and the protected review route remain mandatory.
Reclassification after history remains deliberately unsupported pending separate
approval, rather than an implicitly approved unbounded operation. Producer completion,
deadline/workload qualification, CT04/CT08/CT22 execution approval and real integration
verification remain implementation gates. No unrelated checkpoint is downgraded,
reapproved or certified; no implementation, release, deployment or risk acceptance.

Historical validation for the F1–F4 amendment at `f226799a` on 8 November 2026
(not reruns of the N1 correction; current validation is recorded in §15):

- `git diff --check`: passed. Eight documentation files changed in this correction;
  original lines preserved in all six amended historical Markdown records.
- 239 relative Markdown links/anchors resolve; all nine original checkpoint rows,
  five HARD edges and 13 original revision IDs preserved; four amendment IDs indexed.
- OpenAPI JSON parsing without duplicate keys, 137 internal references resolve.
  No route or wire-body property added; only settings GET/PUT changed from `8bc7469`.
  All six pre-Batch-1 paths and all other operations/component shapes are unchanged.
- A standalone **contract model** exercised 27 conditional/lifecycle cases and both
  first-submission/edit serial orders; small-sample outcomes checked for 231 explicit
  fixture threshold pairs. These are specification/model checks, not endpoint,
  database concurrency, Spring proxy, benchmark or learning-outcome results.
- `cd backend && ./mvnw -Dtest=MasteryCalculatorTest,MasteryStateClassifierTest test`:
  **27 passed**, zero failures/errors/skips; unchanged pure implementation only.
- `cd frontend && npm test -- src/features/catalogue/pages/CourseCataloguePage.test.tsx`:
  **12 passed**; existing OpenAPI-consuming catalogue regression only.

Full backend/VM/browser/reclassification execution was not rerun by this focused
documentation task. The independent review's historical verification remains tied
to its own exact head/environment and is not evidence of new behavior here.

## 15. N1 HTTP validator correction

**CT06-ENABLEMENT-N1-2026-11-08-r1 — DELEGATED_TECHNICAL_APPROVAL;
DELEGATED_APPROVED**, 8 November 2026. Authority is the Project Leader's explicit
instruction to fix N1 in PR #228, retain GET ETags/PUT If-Match, and commit/push only
the minimal contract correction. Codex records this bounded correction under that
instruction; no personal Leader/module review or GitHub approval is asserted.

**Finalization, 9 November 2026:** the existing five-file N1 draft and its
`CT06-ENABLEMENT-N1-2026-11-08-r1` identifier are retained. Codex independently
reviewed and finalized that work under the Leader's current explicit
**“CodeMastery — Fix N1 and Finalize PR #228”** instruction. The draft's 8 November
provenance is not a claim of a new personal Leader review or completed integration.

**Predecessor:** `CT06-ENABLEMENT-2026-11-08-r2`, recorded in §13 at
`f226799a8cd1b8dbdc4621d03fe9c879f155bbb5`. Its original approval, authority and
history remain intact. Only the successful-PUT validator clause and corresponding
frontend/CT14 handoff are corrected. The other eight Batch 1 checkpoint approvals,
CT03/CT14/CT18 prerequisite approval provenance, CT24 reuse, application signatures,
wire fields, failure mappings, history barrier and Course guard remain unchanged.

### HTTP basis and minimal correction

[RFC 9110 §9.3.4](https://www.rfc-editor.org/rfc/rfc9110.html#section-9.3.4)
prohibits a validator in a successful PUT response when the request representation
was transformed. Here GCD normalization can change `2/4` into `1/2`, and the
canonical response includes Course identity absent from the request. Returning the
canonical body does not make a successful-PUT validator compliant.

GET continues to return the canonical representation and its strong ETag.
PUT continues to require If-Match, evaluated on fresh state inside the Course guard.
Its successful 200 still returns the canonical body and no-store, but no ETag,
Last-Modified or other validator field. No route, method, status or wire field is added.
This prohibition applies to the final HTTP response, including applicable filters
and ingress; omitting headers in the controller alone is insufficient evidence.
CT18 r2's 400/401/403/404/409/412/428/500 mappings and safe error behavior are retained;
this correction creates no new failure mapping or exception type.

After successful PUT, the client must obtain another successful authorized GET's
body and ETag as one current-generation snapshot before another update. The GET
may observe a concurrent writer's newer state. A freshly obtained tag may equal
the previous value for unchanged canonical state; it is not a monotonic revision.
The server still checks current If-Match, without a new mechanism to track whether
a particular client performed GET. No PUT is retried automatically. If the
follow-up GET fails, the successful PUT is not reported as rolled back; the client
has no usable write snapshot until a later GET succeeds. Existing session/Course/
generation isolation applies to that refresh and to both halves of the snapshot.

Omitting PUT validators and using the existing GET is the smallest correction.
Making PUT input identical to the stored representation would expand the wire
contract; adding a revision column or endpoint is unnecessary and not authorized.

### B1-O17 verification cases and CT14 handoff

These are required future implementation oracles in the existing suites allocated
in docs/testing.md, supplemented by current documentation/model checks only:

| Case | Required result |
|---|---|
| N1-01 canonicalized fraction | An authorized, matching-tag pre-history PUT with valid fixture weak `2/4` and mastered `4/5` stores/returns weak `1/2`. Its 200 contains no ETag/Last-Modified and retains no-store. Fixture values are not production defaults. |
| N1-02 successful PUT variants | Reduced/equivalent fractions, ordinary JSON formatting and same-policy/no-op requests also return no validator. JSON response fields and success status remain unchanged. |
| N1-03 subsequent GET | Following PUT, GET returns one canonical body with the matching strong SHA-256 ETag. Only this pair becomes the next write snapshot; an unchanged state can return the same tag. |
| N1-04 stale If-Match | Two tabs read the same state; one changes/disables it. The other sends the old tag and receives 412/no effects. The mandatory follow-up GET never automatically replays the stale form. Existing missing/weak/malformed header cases remain unchanged. |
| N1-05 intervening writer | Another writer commits between successful PUT and follow-up GET. The client uses the GET body/tag together, not its earlier PUT body. A further intervening configuration change causes the next stale-tag PUT to fail 412. |
| N1-06 failed refresh | A failed/cancelled or obsolete follow-up GET supplies no write validator. Preserve the confirmed PUT outcome, require a later successful GET before updating, and do not replay PUT or infer rollback. |

The correction and §14's verdict concern documentation readiness only. Real HTTP,
Spring/PostgreSQL concurrency, CORS/browser and provider qualification remain
implementation gates. Required CI and eligible protected review remain separate;
no implementation, merge, bypass, release, deployment or risk acceptance is authorized.

### Historical preparation validation — 8 November 2026

Executed on 8 November 2026 against the `f226799a` predecessor and this correction:

- `git diff --check`: passed; only the five authorized documentation files changed.
- 189 relative documentation links/anchors resolve. All nine original checkpoint
  rows, five HARD edges, existing prerequisite authority/records, application
  signatures, history-freeze and guard clauses are preserved.
- OpenAPI parses without duplicate keys and all 137 internal references resolve.
  GET and PUT preconditions/security/request body are unchanged; only settings PUT
  operation metadata/success-validator handling and explanatory schema/approval
  metadata change. Other operations, wire fields and error mappings are unchanged.
- 15 standalone documentation/reference-model checks passed, including the original
  N1 counterexample, reduced/unreduced fraction normalization, validator-free PUT,
  PUT→GET→PUT, unchanged-state tags, intervening writers and failed/obsolete refresh.
  All six two-client serial schedules were checked; four stale writes were rejected
  with no effects. This is specification validation, not a running HTTP or database test.
- `cd frontend && npm test -- src/features/catalogue/pages/CourseCataloguePage.test.tsx`:
  12 passed; existing OpenAPI-consuming catalogue regression only.

Full backend verification, real settings endpoints, Spring/PostgreSQL races and
browser/CORS tests were not rerun locally for this documentation-only correction.
Current-head remote CI is evaluated separately after push. Historical results are
not represented as current verification of unimplemented behavior.

### Finalization validation — 9 November 2026

Freshly executed against `f226799a` and the complete retained five-file N1 diff:

- `git diff --check`: passed. No implementation, schema, unrelated documentation
  or Issue changes were made; the incoming five files and patch were preserved
  separately before finalization edits.
- 182 relative Markdown links/anchors resolve in the four changed Markdown files,
  excluding fenced examples. This fresh count uses that explicit scope; the older
  preparation count above is retained as its historical report.
- All 15 inspected documentation/reference-model checks passed again. OpenAPI
  has no duplicate keys; all 137 internal references resolve. GET is unchanged,
  PUT still requires If-Match, and its successful response declares only no-store,
  with no ETag or Last-Modified. Other operations, schemas' wire fields, failure
  mappings, application signatures and approved guard/history policies are unchanged.
- All six legal two-client serial schedules were checked; four stale writes were
  refused without effects. Follow-up GET pairing, normalization/no-op cases and
  intervening-writer/failed-refresh scenarios passed in the reference model.
  These are contract checks, not real HTTP or PostgreSQL concurrency verification.
- `cd frontend && npm test -- src/features/catalogue/pages/CourseCataloguePage.test.tsx`:
  **12 passed**, existing OpenAPI-consuming regression only.

The fresh review confirms §14's `READY_FOR_PROTECTED_REVIEW` verdict for this
documentation correction. New endpoint/browser/provider behavior remains
unimplemented and unverified; current-head CI and eligible protected review are
separate gates. No implementation, deployment, release, bypass or merge is authorized.

## 16. R1–R3 independent review corrections — 9 November 2026

**DELEGATED_TECHNICAL_APPROVAL; DELEGATED_APPROVED.** Codex independently
selects these bounded corrections under the Project Leader's explicit
“CodeMastery — Resolve Independent Review Findings in PR #228” instruction.
Review evidence is the independent assessment of
`4d8ac216dc22fdf5fd44182ac88ea4ff1e3a23ec`, completed 9 November 2026, session
`01a1bddd-c749-7f53-9f59-6460e944ca3b`. It is local review evidence, not a
submitted GitHub approval or personal Leader/module review.

| Revision | Exact scope and predecessor |
|---|---|
| `CT06-ENABLEMENT-R1-2026-11-09-r1` | Amends only §13.1's `Set<String>` update input, parsing/comparison channel and conditional-failure wording. Both tag conditions reach one guarded decision as specified below. Retains `CT06-ENABLEMENT-2026-11-08-r2`, §15's `CT06-ENABLEMENT-N1-2026-11-08-r1`, lifecycle, schema, readiness, ABA semantics and all other clauses. |
| `CT18-B1-ERRORS-2026-11-09-r3` | Amends only `CT18-B1-ERRORS-2026-11-08-r2`'s settings 412 detail and adds explicit optional-header validation; preserves its envelope, native error binding, 428 and all other mappings. |
| `CT16-B1-NO-CONTENT-2026-11-09-r1` | Bounded extension of `CT16-2026-10-24-r1`'s JSON-only transport scope for the five existing §8.1 204 operations. Adds no-content success mode; preserves JSON callers, Auth/security and error/cancellation semantics. No new REST operation or response status. |
| `CT14-B1-COVERAGE-2026-11-09-r3` | Extends `CT14-B1-COVERAGE-2026-11-08-r2` and N1's B1-O17 with B1-O18/O19 below in existing suites only. No new test location, harness or whole Final approval. |

Original revisions, authority and historical assessments remain intact. These
replacements govern only their named scopes. All nine checkpoint approvals and
four prerequisite approval histories, five HARD edges, CT24 arithmetic, latest-
attempt policy and CT36 small-sample obligation are preserved. CT05.bindings r1
keeps its five 204 outcomes; its direct frontend consumer now requires the bounded
CT16 extension below. No implementation, deployment, release, risk acceptance,
GitHub approval, ruleset change, bypass or merge is authorized. PR #228 remains
pending protected integration; none of these new revisions is integrated into main.

### 16.1 R1: one guarded HTTP precondition decision

For settings **PUT**, the adapter must convey every applicable parsed condition,
not only the strong members of If-Match. Replace only the §13.1 update signature
with these nested types in the existing `AdaptiveSettingsService.java`:

```java
public Configuration update(UUID courseId, WritePreconditions preconditions,
        boolean adaptiveEnabled, Policy policy);
public record WritePreconditions(Set<String> ifMatchStrongEtags,
        Optional<IfNoneMatch> ifNoneMatch) {}
public record IfNoneMatch(boolean any, Set<String> tags) {}
```

All collections are defensive immutable copies; fields/Optional are nonnull.
`ifMatchStrongEtags` contains the complete quoted strong tags from the required
valid list, as before; weak-only input gives an empty set and fails comparison.
Optional.empty means If-None-Match was absent. For its list form, `tags` contains
quoted tags with any `W/` prefix removed, preserving opaque bytes/case; this
implements **weak comparison**, including weak tags received from clients.
`any=true` represents standalone `*` and requires an empty tags set. An empty list
has `any=false` and an empty set, so matches nothing. Preserve RFC 9110 §5.6.1
list parsing/empty-element tolerance; do not split quoted opaque tags naively on
commas. Multiple field lines form one list. Mixed wildcard/list or malformed
entity-tag syntax is 400, with no state disclosure. The required If-Match profile
(missing 428, malformed/empty or wildcard 400) is unchanged. A valid optional
If-None-Match does not replace mandatory If-Match. Invalid internal record/caller
invariants fail closed before effects; no caller may drop an applicable condition.

After normal authentication, ownership/concealment and request validation, acquire
§13's same fail-fast Course guard and freshly read **one** current configuration.
Derive its canonical tag once. Evaluate in this order, before history/readiness
queries or any side effect, and keep the guard through validation and commit:

1. Evaluate If-Match with strong comparison. No member matches → 412; stop.
2. If present, evaluate If-None-Match with weak comparison against that **same**
   tag. Any matching list member → 412; standalone `*` → 412 because this authorized
   existing Course always has a settings representation, including disabled/null
   policy. Absent or nonmatching list → continue. Stop on failure.
3. Only after both pass, perform the unchanged lifecycle/readiness checks and write
   through Course's existing expected-state storage boundary.

For this PUT profile, ignore If-Unmodified-Since whenever If-Match is present;
missing If-Match still fails the mandatory 428 policy. Ignore If-Modified-Since
because the method is PUT. Ignore Range and If-Range because this target does not
support range updates. These ignored fields must not override tag conditions or
produce spurious 304/412/400 results, including invalid date values. No modification
date or persistence version is introduced. Follow
[RFC 9110 §§13.1–13.2.2](https://www.rfc-editor.org/rfc/rfc9110.html#section-13.2.2):
normal early failures retain precedence; guard contention may return 409 before
any snapshot exists. Conditional PUT never returns 304. Do not use the optional
already-applied success exception for a failed condition, even for a no-op body.

A framework/filter check before locking or against a separate read is insufficient.
The binding must pass the complete conditions into the application decision and
must not let automatic MVC/filter response processing reevaluate PUT conditions
against a post-write snapshot and turn a committed success into 412/304. Verify
this at the final HTTP boundary. Preserve N1: successful PUT has canonical body
and no-store, **no ETag/Last-Modified/other validator**, followed by client GET.
GET's body/tag representation is unchanged.

**CT18 mapping:** either failed condition raises the same nested
`ConfigurationPreconditionFailed`, mapped by the adapter through native sanitized
ErrorResponseException handling to 412, title `Precondition Failed`, fixed detail
**“The request preconditions are not satisfied. Fetch the current settings and
review the request conditions before resubmitting.”** It must not assert that
configuration necessarily changed: a matching If-None-Match can fail even without
an intervening write. Keep the unchanged ProblemDetail shape, no-store and absence
of replacement tags/configuration. Malformed applicable tag syntax is safe 400;
missing mandatory If-Match remains 428 with its existing safe wording. Graph
mappings and BusinessRuleException's 400/409 scope are unchanged.

FE18 normally sends only If-Match. On any 412 retain the draft, GET a fresh paired
snapshot and require deliberate review of both draft and request conditions.
Do not automatically replay an unsatisfiable If-None-Match (especially `*`) after
refresh, silently discard caller conditions, or clear Auth. CORS must permit
If-None-Match if the approved-origin caller sends it, in addition to existing
If-Match allowance/ETag exposure; no new origins or credentials are approved.

Alternatives rejected: dropping or ignoring If-None-Match violates HTTP semantics;
a controller-only comparison can race; evaluating after writing cannot undo a
committed effect. A small parsed immutable input preserves all relevant information
inside the existing transaction without another read, endpoint, lock or schema.

### 16.2 R2: bounded CT16 no-content transport extension

The existing `frontend/src/shared/api/httpClient.ts` always requires JSON media and
calls `response.json()` for successful responses. Merely setting expectedStatus=204
is insufficient; it currently produces a protocol failure after a server mutation
could have committed. This record specifies the later authorized extension; no
transport or application implementation is delivered here.

In the existing file, retain `RequestOptions<T>`/its JSON defaults and add optional
`responseMode?: 'json'`. Add a named `NoContentRequestOptions` based on the same
request/security/cancellation fields, with `responseMode: 'no-content'`,
`expectedStatus: 204` and `decode?: never`. Its exact additive shape and request
entry-point overloads are:

```typescript
type NoContentRequestOptions =
    Omit<RequestOptions<never>, 'responseMode' | 'expectedStatus' | 'decode'> &
    { responseMode: 'no-content'; expectedStatus: 204; decode?: never };
declare function request(path: string, options: NoContentRequestOptions): Promise<void>;
declare function request<T>(path: string, options: RequestOptions<T>): Promise<T>;
```

The function returned by `createHttpClient` exposes those same two call signatures.

No-content mode is explicitly selected only by approved 204 operations. Existing
callers without responseMode still require exact status, JSON media, a nonempty
valid JSON body and successful runtime decoding. They continue returning `Promise<T>`;
204 remains a protocol failure for a JSON caller. No-content mode rejects a decoder
or non-204 expectedStatus as a configuration error before fetch, including untyped
callers. No inference from method, missing decoder, empty payload or response status
may silently switch modes. Raw Response objects/credentials are not exposed.

For an explicitly selected no-content request: validate options, capture/check
session and operation generation, send through the same normalized path, bearer,
credentials:'omit', redirect:'error' and cancellation policy. After fetch, perform
all current-session/operation/expiry/abort checks and current authenticated-401
invalidation **before** success handling. Only exact **204** succeeds, followed by
a final current check immediately before returning `undefined`. Do not require
Content-Type, read/parse JSON or invoke any DTO decoder for that success. Incidental
Content-Type is not a JSON parsing instruction. Unexpected 200/201/202 successes
are protocol failures, not synthetic void success. Non-success responses use the
unchanged safe ProblemDetail/status/fallback logic even in no-content mode; a
matching expected success must never suppress an HTTP error. No retries.

Scope: `deleteCourseSkill`, `addCoursePrerequisite`, `removeCoursePrerequisite`,
`mapCourseLessonSkill`, `unmapCourseLessonSkill` in OpenAPI (§8.1). All five retain
204 with **no response content** and no fabricated JSON DTO. Server/final-ingress
acceptance must verify actual empty 204 framing (including no Content-Length under
[RFC 9110 §§8.6/15.3.5](https://www.rfc-editor.org/rfc/rfc9110.html#section-15.3.5));
a Fetch null body alone does not establish what was sent on the wire.

FE18's existing `skill-graph-editor/api/skillGraphApi.ts` returns `Promise<void>`
for these five operations through the shared no-content mode. The existing hook
and page update mutation outcome only for the still-current Course/operation.
An acknowledged current 204 is success and permits a deliberate graph GET to
refresh canonical state; a failed refresh leaves the acknowledged mutation intact
and the view requiring refresh. No optimistic fabricated graph, duplicate retry,
false rollback or authentication reset. Cancelled/obsolete results cannot update
a later Course/session; cancellation never proves the server rolled back.
The response-metadata extension required for settings GET/N1 remains additive and
separately verified; no-content support does not expose a PUT validator.

Changing the five successes to JSON 200 would unnecessarily alter the approved API;
a feature-local fetch duplicates security/cancellation logic; accepting empty JSON
success globally weakens existing decoder guarantees. The explicit opt-in overload
is the smallest compatible extension. It adds no dependency, file, framework,
endpoint, generic binary/multipart/streaming transport or feature implementation.

### 16.3 CT14 acceptance oracles and direct-consumer traceability

These are required implementation tests, **not executed endpoint/client changes**.
Keep all existing B1-O01–O17 and F3 lock/rollback/deadline obligations.

| Oracle | Required observable result and existing allocation |
|---|---|
| B1-O18a combined conditions | `AdaptiveSettingsServiceTest.java`: matching If-Match plus matching strong/weak If-None-Match, mixed list containing current tag, or `*` → 412/no history/readiness/write. Nonmatching/empty optional list → normal lifecycle; failed If-Match stops before If-None-Match. Missing If-Match + valid optional header → 428; malformed tag syntax/mixed wildcard → 400; weak-only If-Match → 412. Same-body no-op does not bypass either condition. |
| B1-O18b atomic HTTP decision | `integration/AdaptiveLearningFlowIntegrationTest.java`: real adapter/application/PostgreSQL, barriers/independent transactions. Another writer changes A→B before guard acquisition; submit If-Match list {A,B} and If-None-Match B: the strong match passes but the fresh exclusion fails 412 with no effects. Reverse exclusion to A: both pass against B if lifecycle/readiness permits. Prove a pre-lock read cannot decide either check and no post-write framework check changes an admitted success to a failed response. Verify final stored state, no callback/provider effects on 412, and N1 validator absence on success. |
| B1-O18c precedence/errors | Same existing integration suite: ignored date/range headers never change PUT results; combined conditions never yield 304. Auth/role/concealed Course and ordinary malformed request errors retain precedence; busy root 409 remains possible before evaluation. 412 uses the exact safe generic detail above without old/new values, no-store and no replacement validator. Pairing/refresh and ABA remain unchanged. |
| B1-O19a transport modes | `frontend/src/shared/api/httpClient.test.ts`: parameterize PUT/DELETE no-content 204 with absent or incidental Content-Type; Promise<void>/undefined, no body reader/decoder calls. Non-204 success fails; invalid mode/status/decoder options fail before fetch. Default JSON callers still reject 204 and malformed/empty/non-JSON successes and require decoders. Test overloads/type inference in the existing file/toolchain, including compile-time negative cases. |
| B1-O19b errors/isolation | Same shared suite: no-content errors 400/401/403/404/409/412/428/5xx retain actual status with valid or malformed/HTML/empty ProblemDetail; only a current authenticated 401 invalidates its session. Test absent/expired session, caller abort, logout, replacement/expiry and superseded operation while fetch settles and immediately before return; no obsolete success/error/private state, no retries. Preserve namespace, credential/header, redirect and JSON regression checks. |
| B1-O19c consumers/wire | FE18 consumer review exercises all five operation IDs, void success followed by canonical graph refresh, failed/obsolete refresh, Course switch and cancellation/uncertain server completion. Use the existing approved browser/QA route and test allocation when feature execution is separately approved; no new test path is selected here. Real endpoint/final-ingress checks in the existing Adaptive integration allocation verify bodyless 204 framing. Mock fetch cannot prove server framing or browser integration. |

Producer/consumer chain: HTTP adapter → Adaptive conditions + fresh guarded Course
configuration → unchanged Course expected-state write; native CT18 mapping → shared
safe error transport → FE18 reconciliation. Graph endpoints → CT16 no-content mode
→ existing FE18 API/hook/page → canonical graph GET. CT14 owns the above allocation;
CT03's guard/signature and CT40/Assessment providers are unchanged. Downstream
CT08/CT22/CT23/CT36 gates remain exactly as recorded in §13.

### 16.4 R3 traceability and protected-integration condition

Append N1 references only to the existing CT06 #24 and CT14 #27 decision histories,
linking §15 at its finalized `4d8ac216` revision and PR #228's actual unmerged state.
Preserve all previous Issue bytes, checkboxes and metadata. No unrelated Issue or
Project fields are in this correction's scope.

CodeQL is an external protected-integration blocker at the inspected head.
GitHub's managed workflow `dynamic/github-code-scanning/codeql` (ID 378344353)
exists, but there is no CodeQL run/check/status for `4d8ac216`. Its last observed
successful run, 42481863204, is for `f226799a`, not the current revision. Both
code-scanning default-setup and analyses APIs return 403 explicitly reporting
“Code scanning is not enabled for this repository.” This does not establish why
scanning is disabled or whether account/feature availability is the cause.

Active `main-quality-gates` ruleset 24102200 requires CodeQL from integration 57789
and CodeQL code-scanning results (medium-or-higher security alerts; errors/warnings).
No repository CodeQL YAML controls the managed setup; editing ci.yml or rerunning
an old-head scan would not establish the required current-head result. No synthetic
status, replacement check or protection relaxation is permitted. An authorized
repository/security administrator must restore/enable the intended scanning setup
(and resolve feature access if necessary), then obtain the required CodeQL result
and scanning qualification for the exact latest PR revision. Eligible CODEOWNER/
independent last-push review and all other required checks remain mandatory.
This correction changes no scanning settings, workflow, ruleset or review metadata.


### 16.5 Fresh revalidation and current independent verdict

The complete R1/R2 correction was reviewed against RFC 9110 §§9.3.4/13.1/13.2.2,
the original approval scopes, current V002 storage, Course guard/expected-state
boundary, native CT18 mapping, actual shared HTTP client and direct FE18 consumers.
No code/schema/version field or new API is required. Conditions are no longer
lost at the application boundary; exclusion checks cannot race or run after commit.
No-content success is explicit and does not weaken JSON decoding or session guards.
N1, bounded lifecycle, earned progress, F3 transaction obligations, CT36 limitations
and all nine checkpoint/four prerequisite histories remain intact.

Executed on 9 November 2026 against the full five-file correction from `4d8ac216`:

- `git diff --check`: PASS. Only ADR005, decision index, OpenAPI, setup and testing
  documentation changed; no application, migration, workflow or immutable reference.
- 19 inspected static/reference-model checks: PASS. Duplicate-key rejection,
  137 resolved OpenAPI internal references, unique operation IDs and ten secured
  approved/unimplemented operations; unchanged paths/wire schemas, GET and N1 PUT
  success. Exactly five 204s remain content-free. 195 relative Markdown links/anchors
  resolve in the four changed Markdown files, excluding fenced examples.
- Additional API metadata check: all ten operations' path parameters match their
  routes. Of 24 explicit decision-reference occurrences, all Batch 1/current
  references resolve; one unchanged CT01 metadata reference retains the baseline
  obsolete `11-ct01-completion-proposal--pending-authoritative-integration` anchor.
  Its current target is ADR001 §11; this unrelated historical metadata is preserved,
  not represented as a passing reference or amended approval by this correction.
- History/order checks: PASS. Original Markdown lines retained in order; all nine
  checkpoint rows, five HARD edges, original revision-index entries and complete
  N1 text preserved. Four bounded new amendment records are indexed with predecessors.
- Conditional reference model: PASS for combined strong/weak/list/wildcard exclusions,
  empty/absent optional lists, failed-match precedence, the A→B freshness counterexample,
  early errors, busy guard and ignored date/range fields. Six retained N1 model tests
  also pass, including six legal serial schedules/four stale-write refusals and failed
  or intervening-writer refresh. These are specification models, not production
  header parsing, HTTP, PostgreSQL concurrency or transaction verification.
- TypeScript `tsc --ignoreConfig --strict --noEmit --lib es2022,dom` on an isolated
  contract declaration model: PASS. Both entry-point call signatures retain typed
  JSON results, return Promise<void> for explicit 204, and reject decoder/wrong-status/
  missing-mode/unknown-mode cases at compile time. This does not implement the client.
- `cd frontend && npm test -- src/shared/api/httpClient.test.ts src/features/catalogue/pages/CourseCataloguePage.test.tsx`:
  PASS, 89 existing tests in two suites. These check the current JSON client and
  OpenAPI-consuming catalogue, not future no-content or guarded endpoint execution.

New endpoint/provider/browser/CORS/concurrency tests, full local backend verification
and an external OpenAPI conformance validator were **NOT_RUN**. Their implementation
acceptance obligations remain explicit; parsing/models and existing regressions do
not establish those guarantees. R3 is a narrowly authorized append-only Issue-history
update, separately verified by captured-original/body readback.

**Contract review:** R1 and R2 are resolved within the declared documentation scope;
no material contract contradiction remains in that scope. **Current overall verdict:
REVISIONS_REQUIRED_BEFORE_MERGE**, because §16.4's required current-head CodeQL
result/scanning qualification is unavailable. This supersedes only the earlier
§§14–15 readiness conclusions for present merge assessment; it does not revoke or
reapprove sound technical decisions. An authorized administrator must restore the
intended scanning and obtain the exact-head result. Eligible protected human review,
all required checks and separate implementation authorization remain outstanding.
