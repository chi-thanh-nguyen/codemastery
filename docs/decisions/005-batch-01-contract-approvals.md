# ADR005: Batch 1 Contract Approvals

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
