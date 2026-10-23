# CodeMastery Mastery-Based Adaptive Learning

## 1. Purpose and Scope

Mastery-Based Adaptive Learning is CodeMastery's selected advanced component:
Direction 3 of the official course requirements. It is integrated into the main
course-learning flow, including next-step recommendations, Test-out/remedial
guidance, and the learner-facing Mastery Map.

The approved component tracks mastery at `Skill` level, uses skill prerequisites,
and recommends an appropriate next learning step. Learners see their skill states
and prerequisite relationships on the Mastery Map. Instructors can inspect
mastery, progress, and recommendation history for learners enrolled in their own
courses, as established by the approved acceptance criteria.

The design is rule-based. This document separates approved behavior and
architectural invariants from evaluation expectations and deferred implementation
decisions. It records the approved design without claiming completed functionality
or measured evaluation results.

## 2. Architectural Position

Adaptive Learning belongs to the `adaptive` module inside the single deployable
Spring Boot Modular Monolith. It is not a separate Python, AI, or ML service.
Communication with other modules must use approved public application
interfaces, ports, or event mechanisms and preserve encapsulation.

Assessment supplies quiz evidence. Course/Learning Progress retains course
structure and progress responsibilities. Interaction creates and delivers in-app
notifications from recommendation/reason information. Admin/Reporting consumes
learning data while Adaptive owns adaptive decisions.

The [module-boundary ADR](decisions/001-modular-monolith-module-boundaries.md)
defines these boundaries. Section 9 below records the integration invariants
relevant to this component.

## 3. Skill and Prerequisite Model

- Each `Skill` belongs to one `Course`.
- Prerequisites relate skills within that course context. The directed
  prerequisite graph must be acyclic, and a relationship introducing a cycle
  must be rejected.
- Skills and prerequisites are relational domain data in PostgreSQL.
- Lesson-to-skill mappings associate learning content with skills.
- Question-to-skill mappings associate assessment evidence with skills, and
  question difficulty is part of the approved assessment context.

The approved Instructor acceptance criteria require lessons to be mapped to at
least one skill and questions to carry a skill and difficulty tag. Adaptive mode
can be enabled only when all published lessons are mapped and each skill has the
required minimum question coverage. The numeric minimum and detailed coverage
policy remain deferred.

The conceptual relationships and approved initial PostgreSQL schema are recorded
in [the data model](data-model.md#11-approved-initial-postgresql-physical-baseline).
V001–V006 implement that physical baseline, including skills, prerequisites, and
lesson/question mappings. Traversal order, graph and cycle-detection algorithms,
and prerequisite depth/weighting rules remain deferred. The design does not
select a separate graph database.

## 4. Mastery Model

The mastery states are exactly:

- `Unknown`
- `Learning`
- `Mastered`
- `Weak`

The approved physical baseline stores categorical mastery in
`learner_skill_masteries.state` as `unknown`, `learning`, `mastered`, and `weak`.
It selects no numeric mastery field.

Assessment evidence is mapped to these states using configurable thresholds.
The resulting mastery state is an input to adaptive decisions. Threshold values
are configuration/implementation details requiring approval before dependent
implementation. They must be applied consistently across assessment processing,
mastery evaluation, adaptive decisions, and evaluation scenarios.

The architecture describes course `adaptive_settings` as JSON configuration for
mastery thresholds. The approved physical baseline persists it as PostgreSQL
`jsonb`, with no database default. The database constraint permits SQL NULL while
adaptation is disabled and requires a non-NULL JSON object when it is enabled.
Adaptive-settings JSON keys, numeric values, and application validation remain
deferred; the physical representation does not supply them.

No production score ranges, weighting formulas, decay functions, confidence
scores, attempt aggregation, historical weighting or state-transition formulas
are selected here. The isolated Interim local prototype exception is defined
in section 18 and ADR002; it does not define persisted or Final mastery policy.

## 5. Adaptive Learning Flow

The approved high-level flow is:

```text
Enrol
  → Placement diagnostic / initial skill mastery
  → Learn lesson
  → Skill-tagged quiz
  → Assessment result
  → Mastery update
  → Adaptive decision
  → Recommendation + related progress + Mastery Map + notification update
```

For an enrolled learner without mastery data, the course offers a placement
diagnostic. Submitting it establishes initial mastery for the skills it covers
and reflects the result on the Mastery Map. A learner may skip the diagnostic
and begin on the default beginner path.

Subsequent skill-tagged quizzes provide evidence to update mastery for assessed
skills. Current mastery also informs practice-difficulty selection as approved
in section 7. The flow specifies responsibilities and intended outcomes without
defining requests, service methods, event fields, or asynchronous processing.

## 6. Adaptive Decisions

The approved decision categories and behavioral intent are:

| Decision | Approved intent |
|---|---|
| `continue` | Continue along the canonical next lesson, with practice guidance based on current mastery where applicable. The architecture explicitly describes this behavior for the `Learning` state. |
| `skip` / Test-out | Demonstrated mastery makes related content skippable. The approved acceptance criteria describe lessons covering only mastered skills as skippable/Test-out. |
| `remedial` | For weak mastery with a prerequisite that is not mastered, recommend a remedial lesson for that prerequisite before continuing, with a reason. |

Every recommendation must include a human-readable explanation. Canonical course
order remains the baseline structure; Adaptive must not rewrite it. A prerequisite
gap must not hard-block access to an advanced lesson solely because mastery is
missing. The approved behavior provides a warning and learning guidance.

These intents do not define numeric conditions, complete skip eligibility
formulas, multiple-prerequisite precedence, tie-breaking, lesson-selection or
remedial-search algorithms, or recommendation ranking. Behavior beyond the
approved statements, including a complete decision rule for every state/context,
requires an approved contract.

## 7. Practice Difficulty

The approved architecture establishes that current mastery influences the
selected difficulty of a practice quiz. Questions have difficulty tags that
support this assessment context.

The approved persisted difficulty catalogue is `easy`, `medium`, and `hard`, as
recorded in the physical data model. State-to-difficulty mapping,
question-selection algorithms, probability distributions, and minimum/maximum
quiz question counts remain **DEFERRED**. The catalogue does not establish a
mapping from mastery states to difficulty levels.

## 8. Assessment Inputs and Mastery Updates

Quiz results provide evidence for real-time mastery updates. Per-attempt and
answer-level information are approved inputs, with question-to-skill mappings
connecting that evidence to the assessed skills. `Attempt` and `AttemptAnswer`
support this information in the conceptual data model.

Manually graded file-submission assignments contribute to course assessment and
completion grades, but do not directly trigger real-time mastery updates in
Adaptive. This preserves the approved distinction between quiz evidence and
manual grading.

Mastery scoring and evidence aggregation across questions or attempts remain
undefined. The approved initial quiz-grading baseline in the data model does not
supply those mastery formulas.

## 9. Integration and Transactional Consistency

### Assessment → Adaptive

Assessment communicates submitted-quiz/results and answer-level information
through the approved event-driven mechanism. The canonical architecture names
`QuizSubmittedEvent` for this interaction. Its concrete definition, payload, and
execution timing remain deferred; the name does not establish implemented code.

### Adaptive → Course/Learning Progress

Adaptive, Test-out, and mastery decisions affect related `LessonProgress` state
and recommended progression while preserving canonical course order.

### Adaptive → Interaction/Notification

Adaptive supplies recommendation and reason information through the approved
event interaction. Interaction owns creation and delivery of in-app notifications.

### Adaptive → Admin/Reporting

Reporting consumes mastery/progress information for learning-outcome metrics.
Adaptive retains ownership of adaptive-decision logic.

The mastery update and related `LessonProgress` update must succeed or fail
together within one database transaction. Partial updates must not leave those
states inconsistent. Detailed transaction propagation, listener phases, event
execution timing, retries, and idempotency remain deferred to approved integration
contracts. No queue, message broker, or additional service is specified here.

## 10. Explainability and Recommendation History

Recommendations must be explainable and traceable. The approved conceptual
history information comprises:

- Recommendation type.
- Target lesson.
- Human-readable reason.
- Timestamp.

`PathRecommendation` represents this history in the conceptual data model.
The approved physical baseline implements it in `path_recommendations`, retaining
enrollment, target lesson, recommendation type, reason, and timestamp. Instructor
access to learner recommendation history supports inspection of adaptive
decisions. Recommendation creation and its application/API contracts remain
deferred.

## 11. Course and Progress Semantics

| Concept | Approved responsibility |
|---|---|
| Canonical course structure/order | Instructor-defined course, chapter, and lesson structure remains the baseline owned by Course/Learning. |
| Learner progress | `LessonProgress` represents lesson completion within an enrollment, including mastery-based skipping. |
| Skill mastery | `LearnerSkillMastery` represents a learner's knowledge state for a skill. Completing content does not by itself establish mastery. |
| Adaptive recommendation | `PathRecommendation` records explainable learner-specific guidance without rewriting the course structure. |

The approved learning acceptance criteria count lessons skipped through proven
mastery as completed and label them as mastered. Test-out and remedial guidance
can therefore change learner-specific progression while the instructor-defined
structure remains intact. Course-scoped recommendations, mastery, and progress
must remain consistent with [the data model](data-model.md).

## 12. Evaluation Plan

The approved evaluation compares adaptive behavior against the fixed linear path
of the same course using simulated learner profiles:

| Profile | Approved scenario |
|---|---|
| P1 | Strong prior knowledge. |
| P2 | Solid fundamentals with a weakness in Loops. |
| P3 | Complete beginner. |

The architecture proposes these evaluation metrics:

| Metric | Evaluation concept |
|---|---|
| Path divergence | Lessons skipped or inserted relative to the fixed baseline for each profile. |
| Routing correctness | Proportion of seeded prerequisite-gap scenarios for which the correct remedial lesson is recommended. The approved target is **at least 80%**. |
| Mastery-update correctness | Proportion of evaluation scenarios producing the expected mastery state. |
| Learning gain | Change in skill-check performance after remedial learning compared with the baseline, using simulation or an optional pilot where available. |

The **≥80% routing-correctness target is an evaluation target**, not a mastery
threshold or a condition for assigning a mastery state. It must not be reused as
an adaptive business-rule threshold without a separate approved decision.

The live demonstration must show at least two learners taking the same course
with different assessment results and receiving different learning flows and
Mastery Maps. This supports the official requirement to demonstrate changed
learning flow across at least two learner profiles.

The official advanced-component requirement also calls for integration into the
main usage flow, supporting data/test scenarios, evaluation criteria, and a live
demonstration. These are evaluation obligations and planned procedures. No
results, achieved targets, or completed demonstrations are claimed here.

## 13. Seed / Evaluation Data Expectations

The approved architecture anticipates approximately:

- 20–25 skills.
- 120 questions tagged with skills and difficulty.
- Three simulated learner profiles: P1, P2, and P3 as described above.

These are intended content/evaluation dataset expectations, not physical-schema
requirements or a claim about populated repository data. Actual skills,
questions, answers, learner scores, and seed records are not defined here.
Dataset scale also does not define a per-skill coverage minimum or quiz length.

Seed SQL and sample learning materials must follow the approved repository
locations and the separation from schema migrations described in the data model.

## 14. Responsibilities and Non-Goals

- Instructors define course skills, prerequisites, and lesson/question mappings,
  and inspect learners' mastery and recommendation history within their courses.
- Adaptive owns mastery-based decisions and explainable recommendations.
- Assessment owns quiz/attempt/answer assessment information and supplies the
  approved mastery evidence.
- Course/Learning owns course structure and learning progress, with related
  progress affected through approved adaptive integration.
- Interaction owns in-app notification creation and delivery.
- Admin/Reporting consumes learning data and does not own adaptive decisions.

The adaptive component has no ML-based engine or AI chatbot and is not a separate
Python service. Adaptive behavior is scoped to one course; cross-course adaptive
learning is outside the approved scope.

## 15. Deferred Adaptive Decisions

The difficulty catalogue, initial physical schema, PostgreSQL JSONB settings
representation, categorical mastery persistence, and recommendation-history table
are approved and resolved in the data model. They do not resolve the adaptive
behavior below.

The following remain **DEFERRED** until approved before dependent implementation:

| Area | Undefined details |
|---|---|
| Mastery classification | Production/Final thresholds, scoring and transitions remain deferred. The isolated Interim-only count-ratio prototype is specified in section 18; it does not select production settings. |
| Evidence handling | Aggregation across questions/attempts, weighting, historical evidence handling, and any additional calculation rules. |
| Skill graph implementation | Traversal and cycle-detection implementation; no prerequisite depth/weighting policy is established. |
| Practice/coverage | State-to-difficulty mapping, question-selection rules, quiz counts, and numeric minimum question/coverage requirements. |
| Routing/Test-out | Complete skip eligibility formula, remedial-selection algorithm, multiple-prerequisite precedence, tie-breaking, and recommendation prioritization. |
| Integration | Concrete public signatures and event payload schemas, listener/execution timing, retry/idempotency behavior, and detailed transaction propagation. |
| Adaptive configuration | Concrete adaptive-settings JSON keys, numeric values, and application validation beyond the approved JSONB database constraint. |
| API contracts | Public operations and request/response contracts, to be recorded in the approved OpenAPI specification before use. |

Deferral preserves the already-approved behavioral intents and invariants; it
does not authorize selecting a framework default or inventing missing values.
Empty implementation, configuration, or test filenames do not supply a contract.

## 16. Implementation Guardrails

Future Adaptive work must:

- Follow the approved module-boundary ADR and repository structure.
- Preserve rule-based behavior inside the backend and the exact four mastery
  states.
- Preserve canonical course order, per-course scope, and the no-hard-block rule
  for prerequisite mastery gaps.
- Preserve the distinction between quiz-driven mastery evidence and manual
  assignment grading.
- Preserve transactional mastery/progress consistency and Interaction's
  notification ownership.
- Preserve human-readable reasons and traceable recommendation history.
- Apply approved thresholds consistently across processing and evaluation.
- Use approved contracts before implementing undefined behavior.
- Use deterministic adaptive business-rule tests and scenarios, as required by
  CONTRIBUTING, including cycle rejection, mastery transitions, Test-out/remedial
  decisions, reasons, and prerequisite warnings.

When a task depends on an undefined or conflicting decision, follow the
[AGENTS.md Blocker Protocol](../AGENTS.md#blocker-protocol) before affected code is
implemented. This document supplies no speculative classes or algorithms.

## 17. References

- [Official requirements PDF](references/originals/project-requirements.pdf) and
  [requirements Markdown](requirements.md), sections 5 and 5.3: advanced-component
  integration/evaluation and Mastery-Based Adaptive Learning requirements.
- [Canonical approved architecture](references/originals/approved-architecture.docx)
  and [architecture Markdown](architecture.md), especially sections 4.7, 5–7,
  and 9: approved acceptance criteria, flow, integration, evaluation, and concepts.
- [Data model](data-model.md), especially sections 11–12: skills, progress,
  mastery, recommendations, assessment evidence, the approved initial physical
  baseline, and still-deferred feature contracts.
- [Module-boundary ADR](decisions/001-modular-monolith-module-boundaries.md):
  encapsulation, ownership, approved interactions, and transactional consistency.
- [Repository structure](repository-structure.md): approved component,
  documentation, testing, seed, and material locations.
- [CONTRIBUTING adaptive rules](../CONTRIBUTING.md#adaptive-learning-development-rules)
  and [testing guidance](../CONTRIBUTING.md#testing-guidelines): consistent rules,
  explainability, integration, and deterministic testing.

The project source-of-truth hierarchy remains unchanged. This document records
the approved adaptive design and its unresolved details without changing
governance or immutable reference artifacts.

## 18. Interim-only local calculation approval

[ADR002](decisions/002-interim-delegated-contracts.md) records CT06.prototype and CT24.calculation as INTERIM_ONLY_DELEGATED_APPROVAL. One synthetic already-classified skill snapshot uses exact correctCount/answeredCount: absent evidence→Unknown; rate<1/2→Weak;1/2<=rate<3/4→Learning;rate>=3/4→Mastered. Thresholds are explicitly passed validated rational values, configurable for the local prototype. These demonstration choices are not empirically validated production policies or derived from the routing evaluation target.

Use the existing calculator/classifier files with immutable nested types and existing categorical enum. No database, grading, event, clock, history, progress, routing or cross-module effect is approved. Settings JSON and Final algorithm remain deferred. Independently reviewed expected outcomes and actual reproducible execution are required before reporting accepted results. Implementation has not been performed by this documentation change.


## 19. CT06 reconciliation and completion proposal

### 19.1 Authority and historical reconciliation

**PROPOSED_NOT_EFFECTIVE — prepared/revalidated 2026-10-19, Asia/Ho_Chi_Minh.**
The Project Leader
explicitly delegated CT06 investigation, technical selection and preparation of
an approval proposal to Codex. This is selection authority, not a separate human
review, production educational-policy acceptance or implementation authorization.
The new selections below require affected-owner review, explicit Leader sign-off
and protected integration. No existing ADR002 clause is superseded.

Baseline: `main` at `754b6511e85bc8a70ef6a4b8fb7b1210c6cbd6b4`; canonical
requirements and approved architecture US-03/06/07/11 and §§6/7/9; foundation
PRs [#2](https://github.com/chi-thanh-nguyen/codemastery/pull/2),
[#4](https://github.com/chi-thanh-nguyen/codemastery/pull/4) and
[#5](https://github.com/chi-thanh-nguyen/codemastery/pull/5); actual V002–V006,
Adaptive/Course representations and relevant contract/implementation Issues.
PR [#78](https://github.com/chi-thanh-nguyen/codemastery/pull/78), merge
`166484e`, implements shared REST/configuration infrastructure, not Adaptive skill
representations or mastery functionality. The physical settings constraint is in **V002**; V005 owns categorical mastery
and recommendation history; V006 owns Interaction/audit, not an Adaptive policy.

| Decision and provenance | Current classification and scope | Disposition |
| --- | --- | --- |
| ADR002 `CT06.prototype`, `CT24.calculation`, `CT36.I-results`; PR [#73](https://github.com/chi-thanh-nguyen/codemastery/pull/73), merge `729ba60` (2026-10-11 local date), Leader-authorized bounded delegation | APPROVED_AND_CONSISTENT: isolated synthetic count calculation, exact callable shapes and arithmetic oracle protocol; no production aggregation/settings or accepted results | REAFFIRM unchanged; section 18 refers to the effective source |
| ADR002 `CT14.interim`, `CT20.I`, `CT15.I-data` and `NC-EVIDENCE-MODE`, same provenance | APPROVED_AND_CONSISTENT: existing test locations, independently reproducible local mastery evidence, separate browser/deployment evidence; selected database samples have adaptation disabled | REUSE only the consumed slices; local calculation does not require settings persistence or full integrated Adaptive delivery |
| AD03 categorical representations, PR [#79](https://github.com/chi-thanh-nguyen/codemastery/pull/79), merge `1e43194`, Leader review and explicit 2026-10-17 [Issue #34](https://github.com/chi-thanh-nguyen/codemastery/issues/34) acceptance | APPROVED_BUT_INCOMPLETE for CT06 consumers: AD03 fields/codes and scalar mappings exist; no calculation, repository execution, settings service, graph validation or numeric mastery | REUSE representations; do not infer producer functionality from classes |
| AD01 [Issue #33](https://github.com/chi-thanh-nguyen/codemastery/issues/33), V003 scalar skill/mapping inventory | UNRESOLVED implementation: physical fields are approved but current Skill/SkillEntity and graph/service scaffolds are empty | DEFER actual graph/coverage readiness until compatible authorized artifacts exist; physical DDL is not a callable producer |
| CT01.identity, ADR001 §11.4 and PR [#82](https://github.com/chi-thanh-nguyen/codemastery/pull/82), merge `39748f6`, separate explicit Leader identity-only sign-off | APPROVED_AND_CONSISTENT within its read-only identity scope; no automatic CT06 settings boundary | REUSE identity only where the protected owning-Course operation consumes it |
| CT03, PR [#83](https://github.com/chi-thanh-nguyen/codemastery/pull/83), merge `8906c59`; CT16/CT17, PRs [#84](https://github.com/chi-thanh-nguyen/codemastery/pull/84)/[#85](https://github.com/chi-thanh-nguyen/codemastery/pull/85), merges `754b651`/`35cf5ae` | APPROVED_BUT_INCOMPLETE as consumed records: documents are merged, but retained explicit sign-off, dependency, security and verification gates still apply; none selects production mastery or U08/U09 | REUSE existing ownership/no-hard-block boundaries; do not infer complete approval from merge/review |
| AD09 PR [#86](https://github.com/chi-thanh-nguyen/codemastery/pull/86), OPEN at the 2026-10-19 recheck, head `d51930ba76718100cc3332d47afa6ea901bba421`; no submitted reviews and all six reported checks successful | CONFLICTING_OR_OUTDATED against the effective callable/validation contract; unpublished implementation proposal, not effective authority | Correct implementation separately; §19.2 records discrepancies without changing the approved contract |
| `CT06.production`, `CT06.enablement`, U08 and U09; CT06 [#24](https://github.com/chi-thanh-nguyen/codemastery/issues/24), CT40 [#46](https://github.com/chi-thanh-nguyen/codemastery/issues/46) | UNRESOLVED baseline; the scoped selections below are PROPOSED_NOT_EFFECTIVE and remaining named prerequisites are not approved here | EXTEND with explicit limitations; DEFER missing educational values and cross-module callables |

### 19.2 Prototype implementation reconciliation

The effective CT24 contract remains
`MasteryCalculator.calculate(SkillEvidence) -> MasteryEvidenceRate`, including
validated immutable records and `isUnknown()`, and
`MasteryStateClassifier.classify(MasteryEvidenceRate, ClassificationThresholds)`
with `RationalThreshold(int numerator, int denominator)`. All these types remain
nested in the existing approved files in `modules/adaptive/application/mastery/`.
Invalid/null inputs raise `IllegalArgumentException`; validated bounded `int`
operands make exact `long` cross products safe. No new interface is needed.

Read-only inspection of PR #86's four changed files found:

- `MasteryCalculator.java` declares `Snapshot`/`Calculation`, not the approved
  record names, and has no `isUnknown()`. `Snapshot` checks IDs only, while the
  public `Calculation` record validates neither IDs nor counts. Constructing it
  directly bypasses calculation validation and permits invalid classifier input.
- `MasteryStateClassifier.java` declares `Thresholds`/`Rational(long,long)`,
  changing the approved signature/domain. Its checked multiplication can reject
  mathematically valid ratios whose products overflow; the approved bounded-int
  contract already avoids that problem. `Objects.requireNonNull` throws
  `NullPointerException` instead of the approved `IllegalArgumentException`.
- `MasteryCalculatorTest.java` and `MasteryStateClassifierTest.java` exercise
  basic valid boundaries, but assert the incompatible null exception and omit
  several CT36 cases, including the alternate policy and maximum-int case.
  Their existence, PR text and CI cannot establish complete oracle coverage or
  the independent arithmetic review/reproduction required by CT36.

These are implementation corrections for AD09, not reasons to supersede sound
CT06/CT24 decisions. Main's calculator/classifier and their test scaffolds are
empty at the baseline. A separate read-only checkout of PR #86 head `d51930b`
was executed with `./mvnw -Dtest=MasteryCalculatorTest,MasteryStateClassifierTest test`
from `backend/`: 21 tests, zero failures/errors/skips. This executes the current
incompatible tests; it does not establish approved-contract conformance, independent
oracle acceptance, persistence or integrated mastery verification. QA14/CT36 must retain
actual revision, command, environment, outputs, adverse cases and independent
reviewer identity/time when results are collected.

### 19.3 Production evidence and classification selections

The following are **approval proposals**, not a promotion of the prototype.
No concrete production thresholds, empirically validated learning claims or new
persistence fields are selected.

| Decision | Selected proposal and justification | Alternatives / retained approval gates |
| --- | --- | --- |
| Evidence authority | Only server-accepted, submitted and fully graded quiz/diagnostic attempt evidence from Assessment. Require learner, Course, attempt and question/skill identities, complete captured question membership and non-null authoritative correctness; all references must agree on one Course. Ignore client-supplied rates, states and grades. | Raw client counts and rounded `score_percentage` lose provenance or skill detail. CT04/CT08 still approve the actual qualifying submission, completeness proof, event payload and producer binding; CT06 creates none. |
| Per-skill calculation | Consume Assessment's exact boolean correctness for the selected eligible question set of one skill. Count correct items over all captured eligible items, including an explicitly captured unanswered item only as graded by Assessment. A missing answer row or null correctness is incomplete evidence, never silently an incorrect answer or Unknown. | CT06 does not regrade answers, infer missing rows, select quiz composition or redefine approved exact-mask grading. Multi-skill questions/new fractional grades need separate schema/policy approval. |
| Repeated attempts and history | Recommend replacement by the latest eligible submitted attempt **for each assessed skill**, with equal weight for its eligible questions and no decay. Skills not assessed retain their previous state. Diagnostic initializes only assessed skills; absent evidence remains Unknown. | Cumulative history weights repeated practice and old evidence; best-score-only prevents later evidence showing weakness; weighting/decay adds unsupported parameters/time behavior. Latest-evidence replacement is a new educational assumption requiring explicit Leader acceptance. Assessment/CT08 must define authoritative total ordering and equal-time handling; no UUID/time default or new sequence field is invented. |
| Classification | For an accepted nonempty skill snapshot, use exact rational comparison under explicit validated Course thresholds: below weak → Weak; weak inclusive to mastered exclusive → Learning; mastered inclusive → Mastered. Missing evidence → Unknown, distinct from zero correct responses. Transitions can move between all three evidence-backed states; no implicit hysteresis or irreversible Mastered state. | Rounded quiz percentages or numeric mastery persistence are unnecessary. Actual production weak/mastered values and the selected evidence/transition educational assumptions require Leader acceptance; prototype values are not defaults. No event can reset an unassessed skill to Unknown. |
| Configuration validation | Require explicit rational thresholds with positive integer numerator/denominator, each numerator no greater than denominator, and `0 < weak < mastered <= 1`. Require an explicit positive integer minimum-question parameter `n` for enablement. Reject malformed, absent or unordered consumed policy; no silent fallback to prototype constants. | Exact JSON keys, serialized bounds and settings read/write callables remain pending coordinated U08/AD08/CT24 decisions; no public payload, framework binding or production dependency is added. No arbitrary `n`, coverage percentage or empirical justification is invented. |
| Invalid/conflicting evidence | Reject incomplete, cross-Course, malformed or conflicting evidence before any Adaptive state/progress mutation. Repeated identical evidence must not change the recomputed result. Do not hide integrity failures as Unknown or overwrite valid state with a partial result. | Deduplication transport, failure response, transaction isolation, retries and replay mechanism belong to CT08/CT22; no ledger, event identifier, delivery guarantee or new HTTP status is approved here. |
| Replay | Recommend deterministic recomputation from authoritative immutable submitted answers and the approved selected policy, instead of incrementally adding counts to categorical state. Reordered/duplicate delivery must not regress a skill to older evidence or double-count it. | Categorical state cannot reconstruct earlier counts; V005 supplies no processed-event/policy-version ledger. Actual historical order, immutable skill provenance and consistent read/write execution must be established by Assessment/CT08 before this can be implemented or claimed idempotent. |
| Settings changes | Recommend retaining evidence and recomputing affected skills under one newly approved policy, with related progress handled atomically; reject exposure of mixed-policy mastery results. Never infer numeric rates from existing categorical rows or silently reclassify only the next learner who takes a quiz. | Retroactive educational effects, recomputation scope/trigger and policy provenance need explicit Leader acceptance and coordinated CT08/CT22/AD08 design. Current schema has no per-mastery policy revision. A selected recomputation/compatibility mechanism is a production gate, not an implied migration. |

Manual file-submission grading remains excluded from real-time mastery.
`LearnerSkillMastery` continues to persist only categorical state, unique per
learner/skill; missing rows mean Unknown and explicit Unknown remains allowed.
No row creation on read, numeric accumulator, history ledger or timestamp is
introduced. Current question/answer foreign keys alone do not prove immutable
skill tagging, complete attempt capture or cross-Course consistency. Preserve the
approved rule that used Question definitions and submitted answers are immutable;
Assessment must demonstrate its enforcement before historical replay can rely on
it. Reclassification does not by itself authorize routing, progress mutation or
removal of earned completion: those effects require CT07/CT22 decisions.

### 19.4 Enablement proposal and consumer boundaries

**Selected proposal:** treat `adaptive_enabled` as Course-owned intent, with
Adaptive readiness evaluated from approved settings and authoritative Course,
Adaptive and Assessment data. A stored `true` and any JSON object are necessary
physical conditions, not proof of business readiness. Validate coverage/settings
before accepting enablement and before routing/Test-out effects; classification
requires its own valid consumed policy and evidence. Require a consistent view
of the consumed inputs. Snapshot/concurrency participation remains subject to
CT08/CT40 approval and actual implementation verification.

- Every published Lesson must have at least one valid same-Course skill mapping;
  hidden Lessons do not expand this published-Lesson enablement predicate. Every
  Course skill, including skills without a published Lesson, must meet `n`.
- Recommend counting **distinct valid Question IDs per skill** in its owning
  Course's question bank, each with the approved skill/difficulty tag. Reusing a
  Question in multiple quizzes does not multiply coverage. Questions have no
  publication-status column; do not invent one or silently equate question-bank
  coverage with a published, executable quiz. CT40.coverage must explicitly
  approve the eligible population, completeness and provider semantics; CT07/CT23
  retain practice-selection/composition requirements, including difficulty needs.
- Missing/insufficient coverage, invalid settings, cross-Course mappings or an
  invalid prerequisite graph prevent enablement. Incomplete/unavailable lookup
  data is a failure, never a zero-count success. Require at least one published
  Lesson and one Course skill before enabling, so an empty population cannot pass
  by vacuous truth. This is a proposed readiness precondition, not a claim that
  the original architecture specified a minimum Course size.
- If required coverage/settings become invalid after enabling, recommend
  suspending routing/Test-out effects until repaired/revalidated, preserving stored
  mastery/history and Course-owned flags. Do not silently mutate another module's
  settings, erase evidence or manufacture a default policy. Course content/settings
  mutation rules and any automatic flag change require their own owner approval.
- When disabled or not ready, select no new Adaptive routing or mastery-based
  Test-out progress effects; retain ordinary authorized Course learning, canonical
  order and Assessment grading. Disabling never erases mastery/history or reverses
  progress. This does **not** authorize freezing categorical mastery: US-06 requires
  updates after skill-tagged quizzes. Prefer continuing valid-policy mastery
  updates independently of routing rather than silently waiving that requirement.
  The exact disabled-mode interpretation and failure behavior when no valid
  classification policy exists require explicit Leader/owner reconciliation under
  U08/CT08/CT22 before integrated execution. Freezing all updates is an alternative
  requiring an architecture-compatible explicit decision, not an approved default;
  neither fallback prototype constants nor falsely accepted updates are permitted.
  The isolated pure prototype remains callable with explicit policy, without this flag.
- Re-enabling requires current settings/coverage validation and an explicitly
  approved decision on any catch-up/reclassification needed from retained eligible
  Assessment history. Recommend deterministic recomputation under the current
  approved policy where needed before resuming routing/Test-out effects; do not
  assume categorical updates stopped while disabled, discard submissions implicitly
  or increment counts automatically. Historical eligibility and educational effects
  remain pending Leader acceptance; U08/CT08/CT22 own the exact trigger, consistent
  replay and atomic progress coordination.

These selected operational proposals require Course/Assessment/Adaptive review and
Leader sign-off. They never permit hard-blocking an advanced Lesson solely because
mastery or prerequisites are missing. Access remains governed by CT01/CT03;
this section creates no new REST endpoint, error mapping, database constraint,
interface or cross-module repository access.

| Boundary / consumer | Producer and permitted responsibility | Approval status / exact handoff |
| --- | --- | --- |
| U08 Course settings ↔ Adaptive settings validation, existing `modules/adaptive/application/mastery/AdaptiveSettingsService.java` | Course owns `courses.adaptive_enabled`/`adaptive_settings` and Instructor ownership checks; Adaptive owns mastery-policy/graph validation. Any Course operation uses only the effective CT01 identity/CT03 ownership slice it consumes. | UNRESOLVED callable and transaction binding: name the Course producer, exact existing location, immutable request/result, read/write ownership, errors and consistency obligations in a separate coordinated approval. An empty Adaptive service or Course `Map<String,Object>` is not permission to access Course repositories or write its entity. |
| U09 CT40.coverage, Assessment → Adaptive | Assessment owns Question validity/tagging and distinct eligible-question population; Adaptive consumes coverage for every same-Course skill and applies `n`. | UNRESOLVED CT40 signature/output/placement and actual provider; this proposal selects no lookup. A local approved double can support later authorized consumer tests, never real coverage evidence. |
| CT40.lesson-read / skill-tag | Course owns Lesson ancestry/publication; Adaptive owns skills/mappings/acyclic graph; Assessment owns questions. | Existing CT03 authorization does not return structural data. CT40 must approve the exact structural/skill lookup and Course consistency checks consumed; no new U07/U05 interface is approved by CT06. |

### 19.5 Approval and verification handoffs

| Relationship | Independently usable slice / remaining gate |
| --- | --- |
| CT06 ↔ CT14 / QA14 | Reaffirmed pure prototype and existing two unit-test locations remain usable independently of production/enablement. CT14 owns placement/isolation; CT36 owns independently reviewed arithmetic oracles. Neither changes thresholds or certifies results through test success alone. |
| CT06 ↔ CT24 / AD09 | Keep effective prototype signatures and exceptions. Repair PR #86 incompatibilities under separately authorized AD09 work; production callable shapes are not selected here. |
| CT06 ↔ CT36 / CT20.I | Local executed and independently reproduced prototype evidence supports the selected Interim slice only; P1/P2/P3 integrated evaluation, changed maps/flows and all Final requirements remain. |
| CT06 ↔ CT07 / CT08 / CT22 | Classification/evidence intent is separate from routing/difficulty, event payload/execution and atomic Course progress participation. Do not implement partial mastery/progress writes, select a listener phase or imply exactly-once delivery. |
| CT06 ↔ AD08 / CT40 | Numeric policy is separate from settings persistence/lookup, structural reads and Assessment coverage callables; only the specifically consumed approved checkpoint is a prerequisite. |

Required reviews: Adaptive @thanhtran0311; Assessment @minhtan29042006; Course
@TamiaoNguyen; QA/oracle reviewers for consumed test evidence; Leader
@chi-thanh-nguyen. The Leader is the sole documentation CODEOWNER. A
Leader-authored PR cannot self-satisfy CODEOWNER approval; use an independently
contributor-authored PR when necessary, preserving required checks, last-push
approval and thread resolution. No bypass or merge is authorized.

**Remaining Leader decisions:** accept or revise the recommended latest-per-skill,
equal-question/no-decay production evidence assumptions and classification effects;
select justified production thresholds and `n`; accept or revise the proposed
enablement readiness and settings-change/reclassification implications. Separately
approve U08/U09 and consumed CT04/CT08/CT24/CT40 bindings with their owners before
production readiness.
The absence of these decisions does not reopen CT06.prototype or block independent
CT14 preparation. No implementation change, approved-oracle verification, integrated
verification, deployment or accepted academic result is claimed by this proposal. The limited
PR #86 test execution above remains implementation evidence only.
