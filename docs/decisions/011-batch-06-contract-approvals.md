# ADR011: Batch 6 Adaptive Runtime Contracts

## 1. Authority, baseline, stacking and provenance

**DELEGATED_TECHNICAL_APPROVAL; DELEGATED_APPROVED**, 12 November 2026
(Asia/Ho_Chi_Minh), under the same explicit Project Leader instruction as
[ADR010](010-batch-05-contract-approvals.md): **“CodeMastery — Batch 5 & Batch 6
Contract Approval, Two-PR Delivery & GitHub Synchronization”**. Claude Code
technically approves the eligible Batch 6 checkpoints and the strictly necessary
compatible slices below. This is not personal Leader approval, module-owner review
or a GitHub approving review. Implementation, merge, protection bypass, deployment,
spending and residual-risk acceptance are excluded; amendments of effective
approvals stay reserved to the Leader (one such recommendation is isolated in §17).
The Leader's implementation-start authorization recorded in ADR010 §1 applies to
the Batch 6 consumers once their contracts and start prerequisites are satisfied.

**Stacked on ADR010.** Starting `origin/main` is
**921801cef9919e5290f5d08f09699b227bc32c6b**. This record is prepared on top of the
open Batch 5 branch (PR #235, head `056cc276df8d7a476424c18664b4c06c6615e849`
at preparation; after #235 was rebased onto `52bdb8d` and corrected, this record was
restacked on its head `19ce6e0` without content change, see §19),
because CT33.insights (§14) names learners through
`CT01-LEARNER-NAMES-2026-11-12-r1`, which ADR010 §3.1 introduces. That is the only
Batch 6 dependency on Batch 5; every other decision here is independent of it.
Until PR #235 is integrated, the learner-name element of CT33.insights is not
effective on `main`, and this record must not be integrated before ADR010.

Planning sources: `run-20261108-135024/BATCH-06.md` and the verified
`verification-20261108-151823/` roadmap, dependency traceability and
`registers/revised-plan.json` (via `local_ids`). Historical **CR-2/CR-6 are not
Batch 6**. Verified membership: **CP-047–CP-063, seventeen checkpoints, six Issues**;
nothing is rebucketed. Leader register items: **LD-11** (CT22.atomic before
CT08.execution, decided jointly) is honoured by ordering both in this one record;
the owners' joint review it asks for remains a review gate. **LD-12(a)** (decision
effects inside the quiz transaction) is decided in §5. **LD-12(e)** is answered only
to the extent that CT33.results serves the map and insights; reporting stays CT13.

Inspected in addition to ADR010's sources: requirements §5.3; architecture §§4.7/6/7,
US-03/04/06/07/14; ADR001 §§6–9/12/14; ADR005 §§3–7/13 (CT40, CT05, CT06, guards,
threshold freeze, small-sample limitation); ADR006 §§3–4/7; ADR007 §§3/8–13 (guards,
attempt lifecycle, evidence schema); ADR008 §§5–6; adaptive-learning §§4–19; the
implemented `MasteryCalculator`, `MasteryStateClassifier`, `MasteryState`,
`RecommendationType`, `PathRecommendation`, `LearnerSkillMastery` and
`DifficultyLevel`; the empty decision/recommendation/listener/query scaffolds,
`PracticeDifficultyProvider`, `LessonProgressService` and frontend mastery and
insight presentation models; V002–V005; live Issues and Project #3. Known AI use:
Claude Code (Anthropic) authored this record; no AI runtime is added.

## 2. Inventory, dependency order and dispositions

All seventeen checkpoints are **DELEGATED_APPROVED** in the stated scopes.

| CP | Checkpoint / Issue | Revision | Section | Satisfied prerequisites |
|---|---|---|---|---|
| CP-047 | CT07.difficulty / [#48](https://github.com/chi-thanh-nguyen/codemastery/issues/48) | `CT07-DIFFICULTY-2026-11-12-r1` | §8 | V004 difficulty catalogue; CP-051 informational |
| CP-048 | CT25.scope / [#141](https://github.com/chi-thanh-nguyen/codemastery/issues/141) | `CT25-SCOPE-2026-11-12-r1` | §11 | CP-005 informational |
| CP-049 | CT25.skip / #141 | `CT25-SKIP-2026-11-12-r1` | §11 | CT22.progress r1 canonical next |
| CP-050 | CT24.decision / [#25](https://github.com/chi-thanh-nguyen/codemastery/issues/25) | `CT24-DECISION-2026-11-12-r1` | §6 | CP-006 (consumed policy/readiness only) |
| CP-051 | CT07.routing / #48 | `CT07-ROUTING-2026-11-12-r1` | §7 | CP-006 (consumed readiness only) |
| CP-052 | CT22.atomic / [#43](https://github.com/chi-thanh-nguyen/codemastery/issues/43) | `CT22-ATOMIC-2026-11-12-r1` | §3 | CP-011 progress semantics |
| CP-053 | CT25.init / #141 | `CT25-INIT-2026-11-12-r1` | §11 | CP-048, CP-006 |
| CP-054 | CT07.reasons / #48 | `CT07-REASONS-2026-11-12-r1` | §9 | CP-051 |
| CP-055 | CT07.binding-oracles / #48 | `CT07-BINDING-ORACLES-2026-11-12-r1` | §10 | CP-050, CP-051, CP-047; CP-002 compatible |
| CP-056 | CT33.map / [#149](https://github.com/chi-thanh-nguyen/codemastery/issues/149) | `CT33-MAP-2026-11-12-r1` | §13 | CP-005, CP-008; CP-006 informational |
| CP-057 | CT08.execution / [#49](https://github.com/chi-thanh-nguyen/codemastery/issues/49) | `CT08-EXECUTION-2026-11-12-r1` | §4 | CP-027, CP-052, CP-006; CP-050 (applies) |
| CP-058 | CT25.binding / #141 | `CT25-BINDING-2026-11-12-r1` | §11 | CP-053, CP-049; CP-027, CP-024 (both apply) |
| CP-059 | CT33.results / #149 | `CT33-RESULTS-2026-11-12-r1` | §12 | CP-026 |
| CP-060 | CT08.failure / #49 | `CT08-FAILURE-2026-11-12-r1` | §5 | CP-057 |
| CP-061 | CT33.insights / #149 | `CT33-INSIGHTS-2026-11-12-r1` | §14 | CP-059; ADR010 §3.1 (pending integration) |
| CP-062 | CT08.verification-plan / #49 | `CT08-VERIFICATION-PLAN-2026-11-12-r1` | §16 | CP-060 |
| CP-063 | CT33.rest / #149 | `CT33-REST-2026-11-12-r1` | §15 | CP-056, CP-061; CP-054 (applies) |

All thirty verified relationships into Batch 6 (20 HARD, 7 CONDITIONAL, 3
INFORMATIONAL) are covered: their HARD sources in Batches 1–3
(CP-005/006/008/011/026/027) are effective on `main`; the seven conditional edges
apply as marked and are satisfied by Batches 1–3 or by this record. The verified
register has no Batch 5 → Batch 6 edge; the stacking in §1 comes only from the
learner-name prerequisite. CT40.lesson-read
r1 (CP-002) stays compatible: Batch 6 reads canonical identities through §3's
Course states, which carry the same ordering and publication semantics. No HARD
predecessor is pending. CT29 (recommendation notifications), CT36.F evaluation data
and CT13 reporting remain later batches.

## 3. CT22.atomic — Course participation and execution reads

Course owns enrollments, progress and Course settings. The following public methods
are added to existing `modules/course/application/service/LessonProgressService.java`,
with nested immutable records; no new file:

```java
public LearnerCourseState readExecutionState(UUID courseId, UUID learnerId);
public LearnerCourseState applyMasterySkips(UUID courseId, UUID learnerId, Set<UUID> lessonIds);
public record LearnerCourseState(UUID courseId, UUID learnerId, UUID enrollmentId,
        String membershipStatus, boolean adaptiveEnabled, Map<String, Object> adaptiveSettings,
        List<LessonState> lessons) {}
public record LessonState(UUID lessonId, UUID chapterId, int chapterPosition,
        int lessonPosition, boolean completed, boolean masterySkipped) {}
```

`membershipStatus` is `active`, `completed`, `left` or `none` (then `enrollmentId`
is null). `adaptiveSettings` is the stored canonical ADR005 §6.2 object, deeply
immutable, or null. `lessons` lists **published** Lessons only in canonical order
(Chapter then Lesson position, gaps kept) with progress flags; an absent row is
`false/false`. Hidden Lessons never appear.

**Caller obligations.** Both methods are actorless and run only inside an active,
read-write READ COMMITTED transaction in which Course's own guard already holds
**this Course's root**, entered through ADR007 §3.1's `withLearnerAssessmentWrite`
or `withAssessmentExpiryWrite`. Course verifies that marker itself; otherwise
`IllegalStateException` (safe 500) before any read or write. The only caller is
Adaptive's execution orchestration (§4); `learnerId` comes from the persisted
Attempt carried by `QuizSubmittedEvent`, never from HTTP. No REST, scheduler or
other module calls these methods; tests must prove that allowlist.

**`applyMasterySkips` guarantees.** Requires membership `active` or `completed`
(else `IllegalStateException`) and that every ID is in the returned published set
(else `IllegalArgumentException`); an empty set is a no-op. Under the held root it
locks the Enrollment row, then progress rows. For each Lesson: absent row → insert
`completed=true, mastery_skipped=true`; `completed=false` → set both true;
`completed=true` → unchanged (a manual completion is never relabelled). It then
recalculates ACTIVE/COMPLETED for that Enrollment exactly as CT22.progress (ADR006
§3) does. It never sets `completed=false`, never clears `mastery_skipped` and never
commits: any exception propagates and the caller's transaction rolls back everything.
It returns the post-write state. Mastery is never consulted by Course.

These participation rules keep ADR006's manual completion, membership and the
Instructor-only configuration guard unchanged; they authorize no learner REST write.

## 4. CT08.execution

**Selected mechanism: synchronous, in-transaction event handling.** Inside the
existing guarded unit of `AttemptService.submit` and `AttemptService.finalizeExpired`
(ADR007 §§3.1/9/10), after grading, answers and terminal fields are written and
flushed, Assessment builds the ADR007 §12 `QuizSubmittedEvent` from the persisted
rows and publishes it with Spring's `ApplicationEventPublisher.publishEvent`.
Adaptive's existing `QuizSubmittedEventListener` handles it with a plain
`@EventListener` method: same thread, same transaction. No `@Async`, no
`@TransactionalEventListener`, no `REQUIRES_NEW`, no asynchronous multicaster, queue,
outbox or broker. Assessment imports no Adaptive type.

**Exactly one subscriber.** This record approves one subscriber of
`QuizSubmittedEvent`: Adaptive's `QuizSubmittedEventListener`. Spring would run any
other plain `@EventListener` for the same event in the same thread and transaction,
so its failure would also fail the submission. No other subscriber, including a
CT28 or other Interaction consumer (Batch 7), may be registered until its own
contract approves its phase (in-transaction, before-commit or after-commit),
transaction participation and failure semantics. Such a contract must not weaken
§5's all-or-nothing unit, must not make a quiz submission depend on that
subscriber unless it says so explicitly, and must not create effects that commit
when this unit rolls back. Tests assert that the event has exactly the approved
subscriber set (B6-V04).

Rejected: after-commit or asynchronous handling (quiz results could commit without
mastery, requiring an outbox or ledger the schema lacks), and a direct Assessment →
Adaptive call (breaks the approved event decoupling). The selected model gives one
PostgreSQL transaction for **quiz result, mastery, Lesson progress and recommendation
history**: all commit or none do. This is a deliberate broader atomicity than the
architecture's minimum, and it couples submission availability to Adaptive
correctness (§5).

**Lock order** (extends ADR005 §13.3): Course root (held by the guard) → Quiz →
Attempt → AttemptAnswers → Adaptive `learner_skill_masteries` rows → Course Enrollment
row → `lesson_progress` rows → `path_recommendations` inserts. Graph, mapping and
question data are stable because their writers take the same root. Same-Course
processing is serialized; different Courses share no rows.

**Orchestration** in `MasteryUpdateService.applySubmittedEvidence(QuizSubmittedEvent)`
and `AdaptiveDecisionService` (existing files):

1. Check an active read-write READ COMMITTED transaction; obtain
   `readExecutionState(courseId, learnerId)` (§3).
2. Null `adaptiveSettings` (unconfigured Course): **no Adaptive effect**; return.
   A malformed stored policy is an integrity failure.
3. Validate that every event skill belongs to the Course (own repositories).
4. Read `AttemptService.readLatestSkillEvidence` (§12) for the assessed skills in
   the same transaction. It sees the just-flushed Attempt and returns, per skill,
   the latest eligible attempt by ADR005 §6.1's tuple.
5. Classify each assessed skill with the existing calculator and classifier under
   the policy; insert or update its mastery row. Unassessed skills are untouched.
6. Effects only when membership is `active`/`completed` **and** the Course is
   effectively adaptive: `adaptiveEnabled` plus ADR005 §7.2 readiness re-evaluated
   now, from §3's published Lessons, Adaptive's graph and mappings, and Assessment
   coverage read by the execution-only companion of ADR005 §3.3 in existing
   `QuestionBankService`: `public CoverageSnapshot readCoverageForExecution(UUID courseId,
   Set<UUID> skillIds)` (same semantics, no actor, held-root precondition, Adaptive
   execution caller only).
7. Decide with §6's engine; apply test-out Lessons through `applyMasterySkips`;
   insert recommendation rows (§§7, 9).

Disabled or not-ready Courses still get step 5 (ADR005 §7.2, US-06). A `left` or
`none` membership gets step 5 only. Manual assignment grading never reaches this path.
No notification is created here: CT29 decides any later recommendation event.
Manual and automatic submissions follow the same path; the recommendation time is the
processing instant (§9), while evidence order keeps ADR007's logical `submitted_at`.

## 5. CT08.failure

- **Visibility.** Any exception in steps 1–7 propagates through the publisher; the
  whole unit rolls back, including the quiz result. Manual submission returns the
  existing safe 500 and the Attempt stays active if not yet expired; the learner may
  deliberately resubmit. An overdue Attempt is retried only by ADR007 §10's next
  scheduler pass. Repeated failure stays visible as overdue attempts plus safe logs
  with correlation IDs (no answers, keys or reasons). The failure classes, recovery
  path and risk disposition follow below.
- **Duplicates.** A committed submission publishes exactly one event inside its
  own transaction; a repeated submit or finalize of a submitted Attempt publishes
  none (ADR007 §9/§10). Processing is idempotent anyway: states are recomputed from
  latest evidence, only incomplete Lessons can be tested out, and an unchanged
  recommendation is not reinserted (§7).
- **Replay and retry.** No automatic retry, outbox, ledger, replay job or endpoint.
  A restored backup contains only committed, mutually consistent states. Re-enabling
  a Course does not catch up routing (ADR005 §13.2); the next submission evaluates
  test-out over all Lessons.
- **Ordering.** An overdue Attempt finalized after a newer submission cannot regress
  a skill: step 4 always selects the newest evidence by the tuple.
- **Consistency.** Readers see either the state before or after the whole unit.
  Reported success always follows commit. A non-advancing or regressing
  recommendation clock (§9) is a safe 500 with rollback.
- **Unconfigured Courses.** With no stored policy, ADR005 §7.2 forbids a mastery
  update, so none is made and none is claimed; the quiz result still commits,
  because quizzes are core scope (§4.4) for non-adaptive Courses too. Under ADR005
  §13.2 a first policy cannot be set once submitted history exists, so an Instructor
  who wants adaptation must configure it before learners submit. This consequence of
  the effective freeze is recorded, not changed.
- **Failure classes.** *Transient* failures (lock or statement deadline,
  connection loss, unavailable database) roll back the unit like any other failure;
  the same Attempt can succeed on the learner's deliberate resubmission or the next
  scheduler pass without any other change. *Deterministic* failures (malformed stored
  policy, corrupt graph or mapping, cross-Course or incomplete evidence, an engine
  invariant violation, a persistent clock fault, a code defect) fail identically on
  every retry. Nothing distinguishes them automatically and nothing assumes
  progress: an unchanged cause yields an unchanged rollback.
- **Identification.** Each failed run logs one safe structured record with the
  correlation ID, Attempt and Course IDs, manual or scheduler trigger and the
  exception class, never answers, keys, reasons or names. A deterministic failure
  shows up as the same Attempt failing on consecutive scheduler passes, or repeated
  manual 500s in one Course, while other Courses keep committing (ADR007 §10 pages
  past failed candidates). No new metric, alert, threshold or endpoint is approved.
- **Recovery path** (the CT08 path that ADR005 §13.2 anticipated). Because a failed
  run commits nothing, there is no partial effect to undo and no replay to run.
  Recovery means removing the cause through an already approved route, then letting
  the ordinary paths re-execute: (a) a code or configuration defect is fixed by a
  normal reviewed change, CI and an authorized deployment; (b) Course data that an
  approved operation can correct (graph, mapping, question or settings commands
  under ADR005's guard) is corrected by its owner through that operation. After
  that, the next scheduler pass finalizes overdue Attempts and learners resubmit
  active ones. Recomputation from latest evidence, test-out of incomplete Lessons
  only and §7's de-duplication give exactly one set of effects. **No** manual
  database edit, replay job, skip flag or bypass of the unit is approved. A cause
  that no approved operation can correct (for example a malformed stored policy,
  which ADR005 §7.1 does not allow to be cleared) has **no approved repair path**
  and needs the separately approved repair capability that ADR005 §13.2 already
  names; none is introduced here.
- **Ownership and resumption evidence.** The Adaptive owner diagnoses the run; the
  Course or Assessment owner corrects data in their module; deployment follows its
  own gates; any new repair capability needs a Leader decision. Normal execution is
  considered resumed only when the blocked Attempts actually commit through the
  ordinary paths. A code fix must carry B6-V03's failure-then-recovery case passing
  on the exact revision.
- **Scheduler behavior.** ADR007 §10 is unchanged: one transaction per candidate,
  continue after a rollback, rediscover on the next pass. No backoff, quarantine,
  poison list or retry counter is introduced; a deterministically failing candidate
  costs one guarded transaction per pass until the cause is removed.
- **Trade-off and risk disposition.** The *architectural trade-off*, one atomic
  unit chosen over silent divergence between results and mastery, is a technical
  decision within this delegation. The *operational consequence*, that a
  deterministic Adaptive failure stops quiz submission in the affected Course,
  with no in-product degraded mode, until the cause is removed, is residual risk and
  is **PENDING_LEADER_RISK_ACCEPTANCE** before integrated quiz/adaptive behavior is
  exposed to users. The Leader's decision required: accept that risk, or authorize
  a separately approved mitigation (for example a degraded mode or a repair
  capability). No acceptance is recorded or implied here.

## 6. CT24.decision

The decision callable is pure (no I/O, clock or persistence) in existing
`modules/adaptive/application/decision/AdaptiveDecisionEngine.java`; its output is
the existing domain file `modules/adaptive/domain/model/AdaptiveDecision.java`:

```java
public AdaptiveDecision decide(DecisionContext context);
public record DecisionContext(UUID courseId, UUID learnerId, List<LessonInput> lessons,
        Map<UUID, MasteryState> masteryBySkill, List<PrerequisiteInput> prerequisites,
        Map<UUID, String> skillNames) {}
public record LessonInput(UUID lessonId, boolean completed, Set<UUID> skillIds) {}
public record PrerequisiteInput(UUID skillId, UUID prerequisiteSkillId) {}

public record AdaptiveDecision(List<UUID> testOutLessonIds, List<Recommendation> recommendations) {
    public record Recommendation(RecommendationType type, UUID targetLessonId, String reason) {}
}
```

`DecisionContext`, `LessonInput` and `PrerequisiteInput` are nested in the engine.
Inputs are non-null and deep-copied: `lessons` are the published Lessons in canonical
order with unique IDs; `masteryBySkill` holds **every** Course skill (absent rows are
`UNKNOWN`); every referenced skill exists in it and in `skillNames`. Invalid input →
`IllegalArgumentException`. Output: test-out IDs in canonical order; recommendations
are the `SKIP` drafts in that order followed by at most one `REMEDIAL` or `CONTINUE`
draft. `NextLessonResolver` and `RecommendationReasonBuilder` hold the engine's pure
helpers; `PracticeDifficultySelector` is §8. The engine is called only when §4 step 6
holds. Doubles conforming to these records may support local tests; integrated
behavior needs the real engine.

## 7. CT07.routing

For one learner and Course, with Λ the published Lessons in canonical order, M(l)
the skills mapped to Lesson l, state(s) the stored state (absent → Unknown) and
pre(s) the direct prerequisites of s (edge s → p):

1. **Test-out.** T = Lessons l in Λ with completed(l) false, M(l) non-empty and every
   s in M(l) `Mastered`. Apply T (§3) and record one `skip` per Lesson. Λ′ is Λ with
   T completed.
2. **Remedial.** Order the `Weak` skills by (the smallest canonical index of a Lesson
   in Λ mapped to the skill, or last if none; then lowercase UUID). For each Weak s in
   that order, its candidates are prerequisites p with state(p) ≠ `Mastered` that
   have at least one Lesson in Λ. The target of p is the first Lesson in Λ′ mapped to
   p that is incomplete, else the first Lesson in Λ mapped to p (review). Order
   candidates by (target index, lowercase UUID). The first s with a candidate gives
   `remedial` to that candidate's target. Only direct prerequisites are used.
3. **Continue.** Otherwise, if N, the first incomplete Lesson in Λ′, exists:
   `continue` to N.
4. Otherwise no `continue`/`remedial` row (nothing remains and nothing is Weak with
   a mapped unmastered prerequisite).

The current recommendation of an Enrollment is its `continue`/`remedial` row with
the greatest `recommended_at`. §9 makes every processing run's instant strictly
later than all earlier rows of that Enrollment, and a run inserts at most one
`continue`/`remedial` row, so this row is unique and never chosen by a random UUID.
Finding two such rows with the same greatest instant is an integrity failure (500),
not a tie to break. A new `continue` or
`remedial` draft is inserted only if no current recommendation exists or it differs
in type, target or reason. `skip` rows are always inserted for newly tested-out Lessons.

**Prerequisite warning** (US-06): for Lesson l, the warning skills are the
prerequisites p of any s in M(l) with state(p) ≠ `Mastered` and p not in M(l),
ordered by name then UUID. They are guidance only.

**Invariants and missing inputs.** Canonical order is never rewritten; no access is
ever blocked by mastery; Unknown never triggers remedial and counts as unmastered;
unmapped Lessons are never tested out but can be N; skills or prerequisites without
published Lessons cannot be targets; hidden Lessons never appear. Effects require
§4 step 6; otherwise no rows are written. Corrupt persisted graph or mappings are
integrity failures (ADR005 §4.1), while an unmet readiness predicate means no effects.
Rule-based only, scoped to one Course, no numeric threshold beyond ADR005's policy.

## 8. CT07.difficulty

Categorical mapping, in existing `PracticeDifficultySelector.java`:
`UNKNOWN → EASY`, `WEAK → EASY`, `LEARNING → MEDIUM`, `MASTERED → HARD`. Unknown starts
like a beginner (US-03 skip semantics); the three evidence-backed states rise with
competence. No numeric threshold is added.

**U06 binding.** Existing Assessment port `modules/assessment/application/port/PracticeDifficultyProvider.java`:

```java
public interface PracticeDifficultyProvider {
    DifficultyLevel targetDifficulty(UUID courseId, UUID learnerId, UUID skillId);
}
```

Assessment owns the port and `DifficultyLevel`; Adaptive's `PracticeDifficultySelector`
is the only implementation, reading the learner's stored state. A skill outside the
Course → `IllegalArgumentException`. The port reads no Assessment data.

**Selection rule** for a future practice consumer in existing `QuestionSelectionService`:
input is the candidate Questions (ID, skill, difficulty, membership position) and the
target per skill; output, per skill, the candidates at the target difficulty, falling
back in the order EASY→MEDIUM→HARD for EASY, MEDIUM→EASY→HARD for MEDIUM and
HARD→MEDIUM→EASY for HARD; membership order kept; no randomness or count cap.

**Consumption boundary.** ADR007's CT04.start captures **every** membership Question
and CT08.evidence verifies complete membership, so applying this selection at attempt
start would amend two effective approvals. That amendment is Leader-reserved and is
recommended in §17. Until then AS07 may publish the port and implement the pure rule
with tests; adaptive question capture stays blocked. Next-lesson adaptation (§7)
already satisfies §5.3's “difficulty **or** next lesson” requirement.

## 9. CT07.reasons

Each persisted row keeps type, target Lesson, reason and time (V005). Reasons are
fixed English templates, so every row traces to exactly one §7 rule; skill names are
inserted verbatim in double quotes, multiple names sorted by name then UUID and
joined with “, ”. Names are snapshots: later renames do not change history.

| Rule | Type | Reason template |
|---|---|---|
| §7.1 | `skip` | `Tested out: you have mastered every skill in this lesson ({names}).` |
| §7.2 | `remedial` | `Review this lesson first: your skill "{weak}" is Weak and its prerequisite "{prerequisite}" is not yet Mastered.` |
| §7.3 | `continue` | `Continue with the next lesson in the course order.` |

`recommended_at` is one server instant per processing run from a `Clock` injected into
`AdaptiveDecisionService` (UTC; tests fixed; no shared bean, ADR008 §6), truncated to
microseconds and sampled after the root is held. When a run inserts any row for an
Enrollment, that instant must be **strictly later** than the Enrollment's latest
existing `recommended_at`; equal or earlier is a non-advancing or regressing clock:
safe 500 and rollback of the whole unit (§5). The server never adjusts or fabricates
a time. Same-Course runs serialize on the root, so each compares against the
previous committed run. Tests with a fixed clock must advance it between runs that
insert rows. All rows of one run share its instant; history therefore orders by
`recommended_at` descending, then `continue`/`remedial` before `skip`, then target
Lesson lowercase UUID ascending. This is deterministic and needs no column. Rows are
created only by §4. Reads present reasons verbatim. Because ADR005 §13.4's latest-attempt
policy reacts to small samples, learner and Instructor screens must state:
“Mastery states reflect the latest submitted quiz evidence for each skill.”

## 10. CT07.binding-oracles

Consumed shapes: §6's engine, CT06's calculator/classifier with explicit policy, §3's
Course states, §12's Assessment evidence and ADR005 §3.3's coverage. These
deterministic **rule oracles** are specified, NOT_RUN, for `AdaptiveDecisionEngineTest`,
`NextLessonResolverTest`, `PracticeDifficultySelectorTest` and
`MasteryUpdateServiceTest` (Lessons L1–L3 map to S1–S3; S2 → S1 and S3 → S2):

| Oracle | Input → required result |
|---|---|
| B6-R01 | S1, S2 Mastered, S3 Unknown, nothing completed → T = {L1, L2}, two `skip` rows, `continue` L3 |
| B6-R02 | S1 Unknown, S2 Weak → no T, `remedial` L1 naming S2 and S1 |
| B6-R03 | S1 Mastered, S2 Weak → T = {L1}, no candidate, `continue` L2 |
| B6-R04 | S2 and S3 Weak with unmastered prerequisites → S2 is examined first (smaller Lesson index) |
| B6-R05 | Two unmastered prerequisites → the one whose target comes first in canonical order wins |
| B6-R06 | A prerequisite without a published Lesson is never a target; hidden Lessons never appear |
| B6-R07 | An unmapped Lesson is never tested out but can be the `continue` target |
| B6-R08 | All Lessons complete, no Weak candidate → no `continue`/`remedial` row |
| B6-R09 | Same type, target and reason as the current recommendation → no new row; any change → one row. With an advanced fixed clock the newest row is current regardless of UUID values; a run that would insert with an unadvanced clock fails with rollback; history order follows §9 exactly |
| B6-R10 | Disabled, not ready, `left` or `none` → mastery rows only; null policy → nothing |
| B6-R11 | Mapping UNKNOWN/WEAK→EASY, LEARNING→MEDIUM, MASTERED→HARD; fallback orders of §8 |
| B6-R12 | Reason strings equal §9's templates byte for byte, names sorted, no IDs in text |

Evaluation oracles (P1/P2/P3 path divergence, routing correctness against the ≥80%
target, mastery-update correctness) belong to CT36.F in Batch 8 and must use these
rules; the routing target is an evaluation target, never a routing threshold.

## 11. CT25 — placement diagnostic

- **CT25.scope.** The scope of a diagnostic Quiz is the set of skills of its captured
  Questions. Instructors define it through CT23 composition; no full-coverage rule.
- **CT25.init.** Diagnostic evidence is ordinary eligible evidence (ADR005 §6.1).
  §4 classifies every in-scope skill (each has at least one question) to `Weak`,
  `Learning` or `Mastered`; out-of-scope skills keep their state (Unknown when no row).
  Only the four approved states exist. Routing then runs as §7, so the Mastery Map
  shows the result as soon as the submission commits. A Course with null policy
  initializes nothing.
- **CT25.skip.** Skipping is a client navigation, not a server write: the learner
  starts on the canonical path (CT22.progress `nextLessonId`). No mastery, progress or
  recommendation row is written, so states stay Unknown. Because the offer condition
  still holds, the offer reappears on later visits until mastery data exists, as US-03
  states; the client may hide it for the current view only.
- **CT25.binding.** Exposure: §15's guidance read returns `placementOffered = true`
  when membership is active/completed, `adaptiveEnabled` is true, a policy is stored,
  the Course has at least one skill and the learner has no mastery row for any Course
  skill. The client offers the first diagnostic Quiz of the CT23 learner list order;
  with none published, no offer is shown. Start uses CT04 unchanged except the gate
  ADR007 §8 reserved for CT25: in `AttemptService.start`, a `diagnostic` Quiz is 409
  if the learner has any attempt on another diagnostic Quiz of the Course or a
  submitted attempt on this one; an active attempt on this one resumes (200). The
  receiver is the same `QuizSubmittedEvent` path (§4; U35), with no extra REST (U19).

## 12. CT33.results

Assessment-owned reads in existing `AttemptService.java`, nested records:

```java
public LatestSkillEvidence readLatestSkillEvidence(UUID courseId, UUID learnerId, Set<UUID> skillIds);
public record LatestSkillEvidence(UUID courseId, UUID learnerId, Map<UUID, SkillEvidence> bySkill) {}
public record SkillEvidence(UUID skillId, UUID attemptId, UUID quizId, int attemptNumber,
        Instant startedAt, Instant submittedAt, int correctCount, int totalCount,
        BigDecimal scorePercentage) {}
public LatestSkillEvidence readMyLatestSkillEvidence(UUID courseId, Set<UUID> skillIds);
public LatestSkillEvidence readOwnedLatestSkillEvidence(UUID courseId, UUID learnerId, Set<UUID> skillIds);

public PageResponse<LearnerAttemptSummary> listLearnerAttemptsForOwner(UUID courseId, UUID learnerId,
        int page, int size);
public record LearnerAttemptSummary(UUID attemptId, UUID quizId, String quizTitle, String quizKind,
        int attemptNumber, Instant submittedAt, BigDecimal scorePercentage) {}
```

`readLatestSkillEvidence`: for each requested skill, the learner's latest submitted
attempt on a Course Quiz that captured a Question of that skill, by ADR005 §6.1's
tuple; counts from captured `is_correct`; percentage = correct/total × 100, HALF_UP,
two decimals (consistent with AS04). Skills without evidence are absent. A submitted
attempt with missing or null correctness is an integrity failure.

**Access boundary.** The three evidence reads share that semantics but not their
admission, so none is a general read of an arbitrary learner:

- `readLatestSkillEvidence` is **execution-only and actorless**. Its only caller is
  Adaptive's `MasteryUpdateService` in §4 step 4. Like §3, it first verifies an
  active read-write READ COMMITTED transaction in which Course's guard holds this
  Course's root (ADR007 §3.1), otherwise `IllegalStateException` (safe 500) before
  any query. `learnerId` comes from the event's persisted Attempt.
- `readMyLatestSkillEvidence` applies CT03 `requireLearnerCourseAccess(courseId)`
  and reads only the trusted current Learner from CT01 `requireCurrentIdentity()`;
  no learner ID is accepted. Its caller is §13's learner map.
- `readOwnedLatestSkillEvidence` applies CT03 `requireCourseOwnership(courseId)`.
  Its callers are §§13–14's owner reads, which must first obtain
  `readOwnedLearnerState` (active/completed Enrollment, else 404) in the same
  transaction, exactly as for `listLearnerAttemptsForOwner`.

Both admitted reads join the caller's REPEATABLE READ read-only transaction. No
controller, scheduler, other module or other Adaptive service may call any of them;
tests prove that allowlist and the negative cases of B6-V09. `listLearnerAttemptsForOwner` applies CT03
`requireCourseOwnership`, lists submitted attempts of that learner on the Course's
Quizzes (hidden Quizzes included), ordered by `submittedAt` descending then attempt
UUID, with existing paging; callers verify membership first (§14). Neither read
exposes answer keys. Learning-outcome reporting (CT13) may consume them only under its
own contract.

## 13. CT33.map

The Mastery Map read lists **every** Course skill ordered by lowercase UUID, each
with name, nullable description, state (`unknown|learning|mastered|weak`, absent row →
unknown), direct `prerequisiteSkillIds` (sorted; arrow meaning skill → prerequisite,
ADR005 §4.1), `relatedLessonIds` (published Lessons mapped to the skill, canonical
order), `latestScore` and `latestAssessedAt` from §12 (null without evidence). It
reflects the latest committed states (US-07). Progress labels come from CT22.progress
reads; the map adds no Lesson titles or answer data.

Course provides the learner states, in existing `LessonProgressService.java`:

```java
public LearnerCourseState readLearnerState(UUID courseId);
public LearnerCourseState readOwnedLearnerState(UUID courseId, UUID learnerId);
```

`readLearnerState` applies CT03 `requireLearnerCourseAccess` for the current Learner;
`readOwnedLearnerState` applies `requireCourseOwnership` and then requires that the
learner holds an `active`/`completed` Enrollment (else safe 404). Both return §3's
record for read-only use and join the caller's REPEATABLE READ read-only transaction.
Adaptive's existing `MasteryQueryService` composes the map from these, its own graph,
mappings and mastery rows, and §12's admitted evidence read
(`readMyLatestSkillEvidence` for the learner map, `readOwnedLatestSkillEvidence`
for the owner map), in one snapshot.

## 14. CT33.insights

Owning Instructors see only learners with an `active` or `completed` Enrollment in
their Course (US-14); `left`, absent or foreign learners are a safe 404, Administrator
403, published non-owner 403, hidden non-owner 404. Course provides, in existing
`EnrollmentService.java`:

```java
public PageResponse<CourseLearner> listJoinedLearners(UUID courseId, int page, int size);
public CourseLearner readJoinedLearner(UUID courseId, UUID learnerId);
public record CourseLearner(UUID learnerId, String displayName, String membershipStatus,
        UUID enrollmentId, int completedLessons, int totalLessons, int progressPercentage) {}
```

Both apply `requireCourseOwnership`; progress uses CT22.progress's exact calculation;
`displayName` comes from `UserAccountService.learnerDisplayNames`
(`CT01-LEARNER-NAMES-2026-11-12-r1`, ADR010 §3.1; null if omitted); the list is ordered
by learner UUID with existing paging. Adaptive's existing `LearnerInsightsService`
adds, per learner, counts of the four states over all Course skills and the latest
recorded `continue`/`remedial` row's type and time (§7's current row, shown to the
owner unfiltered as an audit marker, unlike §15's learner visibility rule); the
detail adds §13's map, the recommendation history (every row in §9's order, paged)
and §12's attempts.
No other learner data, no export and no cross-Course aggregation.

## 15. CT33.rest

OpenAPI's Batch 6 paths are normative and **APPROVED_NOT_IMPLEMENTED**. All are
authenticated GETs in one REPEATABLE READ read-only snapshot with `Cache-Control:
no-store`, existing paging defaults and bounds, and CT18.minimum errors (400 malformed
UUID or page, 401, 403, 404, 500). Bindings: existing `MasteryController`,
`RecommendationController`, `LearnerInsightsController` and their DTO files
`MasteryResponses`, `RecommendationResponse`, `LearnerInsightResponses`.

| Path | Actor and result |
|---|---|
| `GET /api/courses/{courseId}/mastery-map` | Self Learner: §13 map |
| `GET /api/courses/{courseId}/adaptive-guidance` | Self Learner: `adaptiveEnabled`, `placementOffered` (§11), the **visible current recommendation** below (or null), and per published Lesson its warning skills (§7; empty when `adaptiveEnabled` is false) |
| `GET /api/me/recommendations` | Self Learner: one row per ACTIVE membership of a published Course, ordered like `/api/me/course-progress`, each with the **visible current recommendation** below (or null) |
| `GET /api/instructor/courses/{courseId}/learners` | Owner: §14 list |
| `GET .../learners/{learnerId}` | Owner: one §14 row |
| `GET .../learners/{learnerId}/mastery-map` | Owner: §13 map of that learner |
| `GET .../learners/{learnerId}/recommendations` | Owner: paged history |
| `GET .../learners/{learnerId}/attempts` | Owner: §12 attempt summaries |

For `/api/me/recommendations`, Course adds to `LessonProgressService`
`public PageResponse<LearnerCourseRef> listMyAdaptiveEnrollments(int page, int size)` with
`record LearnerCourseRef(UUID courseId, UUID enrollmentId, boolean adaptiveEnabled,
Set<UUID> publishedLessonIds, Set<UUID> completedLessonIds)`, using the same population,
order and paging as `myCourseProgress`. This satisfies CT16.learner-home's request
without client fan-out or cross-Course ranking. Recommendations are shown verbatim.

**Visible current recommendation** (one rule for both learner reads). Adaptive takes
§7's current row for the Enrollment and returns it only if, in the same snapshot,
(1) the Course's stored `adaptiveEnabled` is true and (2) the row's target Lesson is
in the current published Lesson set (`LearnerCourseRef.publishedLessonIds`, or §3's
`lessons` for `/adaptive-guidance`). Otherwise it returns null. This is a visibility
filter only: it does not fall back to an older row, delete history or synthesize a
recommendation. `targetCompleted` is true when the target is in the completed set.
Readiness is not re-evaluated on reads; a row written while the Course was enabled
and ready stays visible after re-enabling until the next run replaces it, flagged
by `targetCompleted` where applicable. This implements CT16-LEARNER-HOME-2026-11-08-r1
§3 (no entry for a Course without adaptive mode or a current recommendation, shown
unaltered otherwise). Clients display the response as is and add no visibility
filter of their own. Owner insight reads stay unfiltered (§14).

## 16. CT08.verification-plan

The following real-PostgreSQL cases are **specified, NOT_RUN**. QA04 owns atomicity in
`integration/MasteryProgressTransactionIntegrationTest.java`; QA03 owns flows in
`integration/AdaptiveLearningFlowIntegrationTest.java`; AD13/AS12/CO15 cover local
behavior with conforming doubles in `MasteryUpdateServiceTest`,
`QuizSubmittedEventListenerTest`, `AttemptServiceTest` and `LessonProgressServiceTest`.

| Case | Required observable outcome |
|---|---|
| B6-V01 | Manual submit in a ready Course commits result, mastery, skip progress and recommendation rows together |
| B6-V02 | Fault injected after mastery writes, inside Course participation and in recommendation insert: nothing commits; the Attempt is still active; HTTP 500 |
| B6-V03 | Scheduler finalization follows the same path; a failure leaves the Attempt overdue and the next pass retries. A deterministic fault (test-scoped bean override) fails the same Attempt on two consecutive passes with nothing committed while another Course's candidate commits; after the override is removed (a simulated released fix) the next pass commits exactly one set of effects, and a manual resubmission of an active Attempt behaves the same |
| B6-V04 | Duplicate submit and repeated finalize publish no second event and add no rows; the application context has exactly the approved `QuizSubmittedEvent` subscriber set (§4) |
| B6-V05 | Overdue older Attempt finalized after a newer submission does not regress any state |
| B6-V06 | Disabled or not-ready Course: mastery committed, no progress or recommendation; null policy: no Adaptive rows. After disabling, or hiding the target Lesson, `/adaptive-guidance` and `/api/me/recommendations` return null while the owner history still lists the row (§15) |
| B6-V07 | `left` learner finalized after leaving: mastery only |
| B6-V08 | Concurrent same-Course submissions serialize on the root; first submission versus threshold edit follows ADR005 §13.2 |
| B6-V09 | Calls to §3 methods, `readLatestSkillEvidence` or `readCoverageForExecution` without the held root, from another thread or in a read-only/REPEATABLE READ transaction fail before any query or effect. `readMyLatestSkillEvidence` returns only the current Learner and denies another Course, LEFT or absent membership and non-Learners; `readOwnedLatestSkillEvidence` denies non-owners and Administrators; allowlist tests find no other caller (§12) |
| B6-V10 | Diagnostic gate of §11 and init of in-scope skills only |

Fixtures use `AbstractIntegrationTest`'s disposable container with Flyway; each test
owns its records, commits real transactions and cleans them FK-safely (ADR008 §5).
Faults come from test-scoped bean overrides only; no production failure hook.
Outcomes are checked through independent committed reads, and concurrency through
barriers, never sleeps. Evidence must record revision, command, environment and results.
Passing doubles never substitutes for these cases.

## 17. Compatibility, exceptions and retained gates

No effective approval is amended. ADR005 policy, guards and freeze, ADR006 progress,
resume and manual completion, ADR007 lifecycle, guards and evidence, and ADR008
placement are reused unchanged; ADR007 §8's diagnostic gate and ADR005 §3.1's
actorless-context exclusion are completed exactly as those records anticipated. No
schema, migration, dependency or architecture change. The new Course, Assessment and
Auth-consuming methods are bounded, allowlisted and documented above.

**Leader-reserved recommendation — adaptive practice capture (not approved).**
Existing: `CT04-START-2026-11-09-r1` (capture every membership Question) and
`CT08-EVIDENCE-2026-11-09-r1` (producer verifies complete membership). Gap: §8's
selection cannot shape practice attempts without changing both. Recommended bounded
amendment: for `practice` Quizzes in an effectively adaptive Course, capture the §8
selection per skill instead of full membership, and verify evidence against the
captured selection. Alternatives: fixed per-difficulty practice Quizzes (no amendment,
more content) or next-lesson adaptation only (already satisfies §5.3). Affected: AS07,
AS08, AS12, FE10, QA03. **Smallest Leader decision:** authorize, revise or decline
that amendment. AS07's selection wiring stays blocked until then.

*Review recommendation, not a Leader decision:* defer this amendment beyond Final.
Next-lesson adaptation already satisfies §5.3, the amendment would change two
effective approvals, and CT06.production weighs every captured question equally:
evidence drawn mostly from EASY questions for Weak learners, or HARD ones for
Mastered learners, would bias the classification it feeds. Adopting it later would
also need a decision on that bias. CT04-START and CT08-EVIDENCE stay unchanged.

**Retained gates.** Independent CODEOWNER and module review (Adaptive @thanhtran0311,
Course @TamiaoNguyen, Assessment @minhtan29042006, Frontend @heizseung, QA), required
CI and protected integration — first of ADR010, then this record — are start
prerequisites for consumers. Still required: AD10/AD11/AD13/AD14/AD16, AS07 (port and
rule only), AS12, AS16, CO15, FE09/FE12/FE19/FE33 implementation and tests; the
B6-V cases; finite transaction and statement deadlines (ADR005 §13.3); runtime
grants; CT29 notifications; CT36.F evaluation data and results. LD-11's joint
Course, Assessment and Adaptive owner review of §§3–5 stays open until it actually
takes place. §5's operational risk is PENDING_LEADER_RISK_ACCEPTANCE. No deployment,
evaluation result or risk acceptance is implied.

## 18. Preparation validation — 12 November 2026

Executed documentation checks are reported in the pull request: `git diff --check`,
OpenAPI parsing with duplicate-key rejection and reference resolution, link/anchor
resolution and a checkpoint/edge check against the verified register. They are not
runtime tests, transaction verification or independent review.

## 19. Independent review corrections — 12 November 2026

An independent technical review of PR #236 at `94fbb0b` raised the findings below.
Under the Leader's explicit remediation instruction for PR #235/#236 they are
corrected **before integration and within the same r1 revisions**: no revision was
ever effective, so no effective approval is amended. The branch was first restacked
onto the corrected Batch 5 head `19ce6e0`; that restack changed no Batch 6 line.
Checkpoint membership, IDs, scopes and the selected transaction model are unchanged.

| Finding | Correction | Clauses |
|---|---|---|
| F-01 | One visible-current-recommendation rule for both learner reads: null unless the Course's `adaptiveEnabled` is true and the target is published; owner reads stay unfiltered | §14, §15, B6-V06 |
| F-02 | Failure classes, identification, recovery path through approved routes only, ownership and resumption evidence, unchanged scheduler; the operational consequence is PENDING_LEADER_RISK_ACCEPTANCE | §5, §17, B6-V03 |
| F-05 | A run that inserts rows must use a strictly later instant per Enrollment; the current row is unique; history order no longer depends on random UUIDs | §7, §9, §14, B6-R09 |
| F-07 | `readLatestSkillEvidence` is execution-only behind the held root; learner and owner maps use two admitted reads | §12, §13, B6-V09 |
| F-08 | Exactly one approved `QuizSubmittedEvent` subscriber; any other needs its own phase, transaction and failure contract | §4, B6-V04 |

The adaptive practice capture amendment stays pending with a recorded deferral
recommendation. The new and extended cases are specified and NOT_RUN. This section
is documentation correction only; it is not independent review, CI evidence or
Leader approval.
