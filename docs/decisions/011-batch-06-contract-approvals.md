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
outbox or broker. The publication path (`AttemptService` and `QuizSubmittedEvent`)
imports no Adaptive type and calls no Adaptive service. This does not change
Assessment's existing approved uses of Adaptive's `SkillGraphService` for question
tagging and quiz scope validation (ADR005 §3.2, ADR007 §4), nor the Assessment-owned
port of §8 that Adaptive implements.

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
   effectively adaptive. The stored `adaptiveEnabled` is checked first; only when it
   is true is ADR005 §7.2 readiness re-evaluated now, from §3's published Lessons,
   Adaptive's graph and mappings, and Assessment coverage read by the
   execution-only companion of ADR005 §3.3 in existing `QuestionBankService`:
   `public CoverageSnapshot readCoverageForExecution(UUID courseId, Set<UUID> skillIds)`
   (same semantics, no actor, §12's execution-scope precondition, Adaptive
   execution caller only). A disabled Course therefore runs no readiness,
   coverage, decision, progress or recommendation code.
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
- **Answer durability under rollback.** A manual POST submit writes the supplied
  masks, grading, terminal fields and every Adaptive effect in this one unit
  (ADR007 §9 and §4 above). When the unit rolls back, the masks sent in that POST
  roll back as well, and the Attempt keeps only its **last committed saved masks**:
  those of the latest committed answer save, or the all-false masks captured at
  start. If the learner does not submit successfully before `expiresAt`, ADR007
  §10's finalizer grades those saved masks once the cause is removed. The stored
  result can then be lower than the answers the learner sent. It is immutable
  submitted evidence: mastery is classified from it (for example Weak instead of
  Mastered), the test-out it would have earned does not happen, and remedial or
  continue recommendations follow from it. No regrade or repair operation exists.
- **Mitigation: save before submit** (client obligation on existing operations).
  The quiz-taking client (`useQuizAttempt.ts` and `quizApi.ts`) performs every
  Submit as two steps of one user action: first the approved answer save (ADR007
  §9 `PUT` with the complete current mask set), then, only after that save returned
  200, the POST submit with the same set. Answer saves run no grading, event or
  Adaptive code, so they keep committing while an Adaptive failure blocks
  submission. If the save fails, is rejected or its outcome is unknown, the client
  does not send the POST: it keeps the local answers, reads the Attempt (ADR007 §11
  GET) to show its current state (active, overdue or already submitted) and reports
  the failure before the learner's next deliberate Submit. There is no automatic
  retry. **Conditional guarantee:** a committed save of masks M protects M across
  this POST's rollback only if no intervening accepted writer changes the saved
  masks and no other terminal submission commits. The two HTTP requests are two
  transactions; the Course root does not lock them as a pair. A later finalization
  has the same per-question correctness and score as a successful submit of M only
  while M remains the latest committed saved set until finalization and no other
  submission has already made the Attempt terminal. Grading uses immutable keys;
  finalization records `submitted_at = expiresAt`. This does not promise identical
  mastery, test-out or recommendations: the latest-evidence tuple, other quiz
  evidence, current progress and routing inputs can differ by processing time.

  **Intervening writer:** A saves M (200), B saves N (200), A submits M and Adaptive
  fails: A's POST rolls back to N, not M. If N is still the latest committed set
  when the scheduler finalizes, it grades N. Its score/correctness may differ from
  M, and the committed evidence selected by §4 step 4 drives mastery, test-out and
  recommendations (possibly Weak/no skip/remedial instead of Mastered/skip).
  A later accepted save can change the set again; an independently committed
  terminal submission is retained. A rollback undoes only its own transaction.

  **Limit:** nothing protects a particular tab's save from another accepted writer,
  answers changed after the latest committed save, or a save that failed, timed
  out, was never sent, arrived at or after `expiresAt` (409), or was lost with the
  browser or connection. After failure/unknown outcome the client reads the Attempt
  before another deliberate action; if it shows N, it presents that server state
  without claiming M is durable or automatically resaving M over N. Local unsaved
  answers may be retained for explicit reconciliation while the view/session is
  current. Client-side saving reduces this exposure; it cannot remove it. Periodic
  saves narrow one client's unsaved window but provide no cross-client isolation
  and are not required here. ADR007's last-accepted-save and repeated-submit rules
  remain unchanged; no revision token, optimistic lock or schema change is added.
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
  correlation ID, Attempt and Course IDs, manual or scheduler trigger, the failing
  orchestration step (1–7 of §4, or the Assessment write before publication) and
  the exception class, never answers, keys, reasons or names. A deterministic failure
  shows up as the same Attempt failing on consecutive scheduler passes, or repeated
  manual 500s in one Course, while other Courses keep committing (ADR007 §10 pages
  past failed candidates). No new metric, alert, threshold or endpoint is approved.
- **Recovery path** (the CT08 path that ADR005 §13.2 anticipated). Because a failed
  run commits nothing, there is no partial effect to undo and no replay to run.
  Recovery means removing the cause through an already approved route, then letting
  the ordinary paths re-execute: (a) a code or configuration defect is fixed by a
  normal reviewed change, CI and an authorized deployment; (b) Course data that an
  approved operation can correct (graph, mapping, question or settings commands
  under ADR005's guard) is corrected by its owner through that operation; (c) a
  failure in step 6 or 7 (readiness or coverage evaluation, the decision engine,
  test-out writes, recommendation inserts or §9's recommendation clock check) stops
  occurring once the owning Instructor disables adaptive mode through the approved
  settings operation (ADR005 §13.1–13.2: GET for the ETag, then PUT with
  `adaptiveEnabled=false` and the unchanged policy). Route (c) exists only while the
  stored policy is valid, because the settings read and every update require one;
  it can meet the settings entry's NOWAIT 409 while submissions hold the root and
  is then repeated deliberately. Because step 6 checks the stored flag first, a
  disabled Course commits submissions with mastery only. This uses an approved
  configuration operation, not a bypass of the unit. Route (c) does **not** avoid
  failures in steps 1–5 (a malformed stored policy, foreign or incomplete
  evidence, classification, mastery writes, a missing execution scope); those need
  route (a) or (b) or have no approved repair. While disabled, learners see no
  current recommendation (§15) and earn no test-out; re-enabling revalidates
  readiness only (ADR005 §13.2), and effects resume at the next submission without
  catch-up. After the cause is removed, the next scheduler pass finalizes overdue
  Attempts and learners resubmit active ones. Recomputation from latest evidence,
  test-out of incomplete Lessons only and §7's de-duplication give exactly one set of effects. **No** manual
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
  decision within this delegation. The *operational consequences* are residual
  risk: (1) a deterministic Adaptive failure stops quiz submission in the affected
  Course until the cause is removed; the only in-product relief is route (c), which
  covers step 6–7 failures only and needs a valid stored policy; (2) an Attempt
  that expires while submission is blocked is finalized from its last committed
  saved masks, which may be another tab's accepted set even after this tab received
  a successful save; overwritten or never-committed answers are not protected.
  The resulting immutable evidence drives mastery, test-out and recommendations;
  the save-before-submit obligation reduces unsaved-answer exposure but gives no
  cross-client isolation. Both are
  **PENDING_LEADER_RISK_ACCEPTANCE** before integrated quiz/adaptive behavior is
  exposed to users. The Leader's decision required: accept these risks, or
  authorize a separately approved mitigation (for example a submission degraded
  mode, a repair or regrade capability, or mandatory periodic answer saving). No
  acceptance is recorded or implied here.

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
helpers; `PracticeDifficultySelector` is §8. In execution the engine is called only
when §4 step 6 holds; §15's learner reads also call it, read-only, to test whether
the current row is still valid. Doubles conforming to these records may support local tests; integrated
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

Completion alone does not suppress step 2: even if every published Lesson is
completed, a Weak skill with a mapped unmastered prerequisite still selects a
completed remedial review target. Only all-completed **and no remedial candidate**
yields no draft. This preserves the distinction between progress and mastery.

The current recommendation of an Enrollment is its `continue`/`remedial` row with
the greatest `recommended_at`. §9 makes every processing run's instant strictly
later than all earlier rows of that Enrollment, and a run inserts at most one
`continue`/`remedial` row, so this row is unique and never chosen by a random UUID.
Finding two such rows with the same greatest instant is an integrity failure (500),
not a tie to break. A new `continue` or
`remedial` draft is inserted only if no current recommendation exists or it differs
in type, target or reason. `skip` rows are always inserted for newly tested-out Lessons.
The current row is history's latest guidance, not a promise that it still applies:
learners see it only through §15's validity rule.

**Prerequisite warning** (US-06): for Lesson l, the warning skills are the
prerequisites p of any s in M(l) with state(p) ≠ `Mastered` and p not in M(l),
in §9's name order. They are guidance only.

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
    DifficultyLevel targetDifficulty(UUID courseId, UUID skillId);
}
```

Assessment owns the port and `DifficultyLevel`; Adaptive's `PracticeDifficultySelector`
is the only implementation. The port takes **no learner argument**: it answers only
for the trusted current Learner, so no caller can read another learner's mastery
through it. Before any read the implementation requires, in order: an actual
read-write READ COMMITTED transaction (it runs inside a Learner attempt-start unit),
otherwise `IllegalStateException`; the CT01 `requireCurrentIdentity()` with role
Learner and CT03 `requireLearnerCourseAccess(courseId)`, propagating their
401/403/404 outcomes; and that the skill belongs to the Course (own repository),
otherwise `IllegalArgumentException`. It then reads only that learner's own row
(absent → `UNKNOWN`). The port reads no Assessment data, and the scheduler path,
which has no current identity, can never use it. **Caller allowlist:** there is no
approved production caller until the Leader-reserved amendment below is decided; if
it is approved, the only caller is `QuestionSelectionService` inside
`AttemptService.start`'s Learner guard. Allowlist tests prove that no other code
calls the port.

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
inserted verbatim in double quotes, multiple names in name order and joined with
“, ”. Names are snapshots: later renames do not change history.

**Name order** (reasons here and §7's warnings). Skills are compared by their stored
name with Java `String.compareTo`: lexicographic order of UTF-16 code units,
case-sensitive (`"Z"` before `"a"`), with no Unicode normalization (`"é"` as one code
point and `"e"` plus a combining accent are different strings), no trimming, no
locale and no database collation. Equal names are ordered by lowercase canonical
UUID text, again with `String.compareTo`. The order is computed in the application,
never by SQL `ORDER BY`, so it does not depend on PostgreSQL collation or the host
locale.

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
| B6-R12 | Reason strings equal §9's templates byte for byte, no IDs in text. Name order: skills named `"beta"`, `"Alpha"`, `"Zeta"`, `"Ärger"` and two named `"alpha"` order as `"Alpha"`, `"Zeta"`, `"alpha"` (smaller UUID), `"alpha"` (larger UUID), `"beta"`, `"Ärger"`, identically under any JVM default locale; a precomposed and a decomposed `"é"` are distinct names |
| B6-R13 | §15 validity: return R exactly when the enabled/published-target checks hold and the current engine draft equals R in type, target and reason. Unchanged inputs → visible. A completed continue target → null. For a sole remedial candidate, changing its Weak skill to Learning, mastering its prerequisite, or removing its only edge/target mapping makes that draft differ or disappear → null. Hidden target or disabled Course → null. No fallback to history and no writes; R14–R16 specify completion, rename and population cases |
| B6-R14 | Two-Lesson fixture only: L1 maps S1, L2 maps S2, S2 requires S1; S1 Unknown, S2 Weak, L1 completed, L2 incomplete, stored R = remedial L1. Manually complete L2: all Lessons completed, but the same remedial L1 is still selected and visible with targetCompleted=true in course guidance. Change S2 to Learning: no remedial candidate and no incomplete Lesson → no draft, null. This is R08's no-Weak-candidate condition, not an all-completed suppression rule |
| B6-R15 | Rename the selected Weak skill or prerequisite in a remedial reason → reason differs, R hidden until an execution run records the new draft. Rename an unrelated skill without changing the selected draft, or rename skills while a continue draft is unchanged → R remains visible. Equality of the selected type/target/reason, not any rename or input change by itself, decides visibility |
| B6-R16 | Same visibility predicate for both learner reads, with their approved populations: an ACTIVE published-Course row with valid R includes R and its title in Home; with invalid/no R it includes recommendation=null and targetLessonTitle=null. COMPLETED membership omits that Course row from Home, while course guidance still admits the learner and may show R14's valid review. LEFT/absent membership or hidden Course grants no course guidance and no Home row. A hidden target in an otherwise admitted ACTIVE Course yields null, not a hidden title. Owner history retains every row |

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
- **CT25.binding.** Start uses CT04 unchanged except the gate ADR007 §8 reserved for
  CT25: in `AttemptService.start`, a `diagnostic` Quiz is 409 if the learner has any
  attempt on another diagnostic Quiz of the Course or a submitted attempt on this
  one; an active attempt on this one resumes (200). The receiver is the same
  `QuizSubmittedEvent` path (§4; U35), with no extra REST (U19).

  **Exposure.** The server, not the client, names the Quiz to offer, so that an
  offer never leads to a 409 that the snapshot already predicts. Assessment adds, in
  existing `AttemptService.java`:

  ```java
  public UUID readMyPlacementQuizId(UUID courseId);
  ```

  It applies CT03 `requireLearnerCourseAccess(courseId)`, reads only the trusted
  current Learner, joins the caller's REPEATABLE READ read-only transaction, samples
  server time once and returns, applying exactly the start gate above to that
  snapshot:

  1. if the learner has any attempt on a diagnostic Quiz of the Course (the gate
     keeps them all on one Quiz): that Quiz's ID only when it has no submitted
     attempt, exactly one unsubmitted attempt with now < `expiresAt`, and the Quiz is
     currently published (start then resumes it with 200); otherwise `null`
     (overdue pending finalization, already submitted, or hidden);
  2. otherwise the first currently published diagnostic Quiz in CT23's learner
     list order (title `COLLATE "C"`, then UUID); `null` when there is none.

  Its only caller is Adaptive's existing `PlacementDiagnosticService`, composing
  §15's guidance. Guidance returns `placementQuizId` and `placementOffered`, which is
  true exactly when membership is active/completed, the stored `adaptiveEnabled` is
  true, a policy is stored, the Course has at least one skill, the learner has no
  mastery row for any Course skill **and** `placementQuizId` is non-null; otherwise
  `placementQuizId` is null. The client offers only that Quiz and starts it through
  CT04; it applies no selection of its own. A state change between the read and the
  start (expiry, hiding, another tab) still yields start's normal 409 or 404, and
  the client then reloads guidance. The one-time diagnostic invariant and the
  attempt lifecycle are unchanged.

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
  Adaptive's `MasteryUpdateService` in §4 step 4. It first verifies the
  execution-scope precondition below, otherwise `IllegalStateException` (safe 500)
  before any query. `learnerId` comes from the event's persisted Attempt.
- `readMyLatestSkillEvidence` applies CT03 `requireLearnerCourseAccess(courseId)`
  and reads only the trusted current Learner from CT01 `requireCurrentIdentity()`;
  no learner ID is accepted. Its caller is §13's learner map.
- `readOwnedLatestSkillEvidence` applies CT03 `requireCourseOwnership(courseId)`.
  Its callers are §§13–14's owner reads, which must first obtain
  `readOwnedLearnerState` (active/completed Enrollment, else 404) in the same
  transaction, exactly as for `listLearnerAttemptsForOwner`.

**Execution-scope precondition** (`readLatestSkillEvidence` and §4's
`QuestionBankService.readCoverageForExecution`, both in Assessment's
`application/service` package). Assessment cannot inspect Course's lock, and Course
exports no lock handle (ADR005 §5), so Assessment proves the held root through its
own position in the call chain. `AttemptService.submit` and `finalizeExpired` pass
their work to Course's guard as a callback (ADR007 §3.1), and Course invokes that
callback only after it holds this Course's root with `FOR UPDATE` and has rechecked
admission; the lock lasts until the transaction ends. As the callback's first
action, `AttemptService` binds an Assessment-private marker naming that Course ID to
the current thread and transaction (Spring
`TransactionSynchronizationManager.bindResource` under a key held by a
package-private nested type of `AttemptService`), and unbinds it in a `finally`
block before the callback returns; a transaction-completion synchronization unbinds
it as a backstop. Each guarded read requires, before any query: an actual
read-write transaction at READ COMMITTED, and the marker bound for exactly the
requested `courseId`. A bound marker therefore implies that the caller runs inside
that callback on that thread, with the root held. It cannot leak to another thread
(the binding is thread-local), to a later transaction (unbound before the callback
returns) or to another Course (the ID must match). `start`, answer saves and every
other path never bind it. Course adds no method, and Assessment reads no Course
lock, marker or internal type. The marker grants nothing by itself: only code
running inside the callback, that is the publication → listener path, can observe
it, and the caller allowlist tests still apply.

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
| `GET /api/courses/{courseId}/adaptive-guidance` | Self Learner: `adaptiveEnabled`, `placementOffered` and `placementQuizId` (§11), the **visible current recommendation** below (or null), and per published Lesson its warning skills (§7; empty when `adaptiveEnabled` is false) |
| `GET /api/me/recommendations` | Self Learner: one row per ACTIVE membership of a published Course, ordered like `/api/me/course-progress`, each with `courseTitle`, the **visible current recommendation** below (or null) and its `targetLessonTitle` (null exactly when the recommendation is null) |
| `GET /api/instructor/courses/{courseId}/learners` | Owner: §14 list |
| `GET .../learners/{learnerId}` | Owner: one §14 row |
| `GET .../learners/{learnerId}/mastery-map` | Owner: §13 map of that learner |
| `GET .../learners/{learnerId}/recommendations` | Owner: paged history |
| `GET .../learners/{learnerId}/attempts` | Owner: §12 attempt summaries |

For `/api/me/recommendations`, Course adds to `LessonProgressService`:

```java
public PageResponse<LearnerCourseRef> listMyAdaptiveEnrollments(int page, int size);
public record LearnerCourseRef(UUID courseId, String courseTitle, UUID enrollmentId,
        boolean adaptiveEnabled, List<LessonState> lessons, Map<UUID, String> lessonTitles) {}
```

It uses the same population, order and paging as `myCourseProgress`; `lessons` are
§3's published Lessons in canonical order with their flags, and `lessonTitles` maps
exactly those Lesson IDs to their stored titles (Course-owned data; no hidden Lesson
appears). Adaptive bulk-loads the graph, mappings and the learner's mastery rows for
the page's Courses; there is no per-Course request. The row carries the Course title
and the visible target's title, so Learner Home renders CT16.learner-home §3's entry
("type, target lesson, the backend reason verbatim, time … labelled with its course")
without client fan-out or cross-Course ranking. `/adaptive-guidance` needs no title:
its client is on the Course page, whose existing syllabus read lists every published
Lesson, and a visible target is always published. Recommendations are shown verbatim.

**Visible current recommendation** (one rule for both learner reads). History keeps
every row; a learner is shown only guidance that still holds. Adaptive takes §7's
current row R for the Enrollment and returns it only if, in the same snapshot:

1. the Course's stored `adaptiveEnabled` is true;
2. R's target Lesson is a currently published Lesson of the Course
   (`LearnerCourseRef.lessons`, or §3's `lessons` for `/adaptive-guidance`); and
3. **R is still valid**: evaluating §7 steps 1–4 with §6's engine, read-only, on the
   snapshot's current inputs (published Lessons in canonical order with their
   completion flags, the learner's stored mastery rows with absent as Unknown,
   Adaptive's graph and mappings, and current skill names) yields a
   `continue`/`remedial` draft whose type, target Lesson and reason equal R's byte
   for byte.

Otherwise it returns null. The evaluation applies no test-out, writes nothing and
does not re-evaluate readiness. It reproduces the run's own draft whenever the
inputs are unchanged since R was inserted, because a run applies its test-out
before deciding. It hides a completed `continue` target, a disappeared or changed
selected draft (including a rename that changes its reason), a disabled Course or
a hidden target. An unrelated rename or other input change that leaves the draft
identical does not hide it. All Lessons completed yields no draft only when no
remedial candidate remains; a still-selected completed remedial review stays
visible with `targetCompleted=true`. Manual completion changes progress, not
mastery (ADR006 §3). It never falls back to an older row, deletes history or
synthesizes or persists a recommendation. A learner without visible guidance still
has CT22's canonical `nextLessonId`. `targetCompleted` can be true only for a
`remedial` review target (§7.2).

The predicate is identical across endpoints; admission/population is not widened.
`/adaptive-guidance` admits ACTIVE/COMPLETED membership. `/api/me/recommendations`
includes only ACTIVE published-Course memberships: COMPLETED/LEFT/absent memberships
or hidden Courses produce no Course row, not a synthetic row with null guidance.
An included ACTIVE row with no visible recommendation has both recommendation and
targetLessonTitle null. Keep stored membership distinct from calculated completion:
ADR006's retained rejoin behavior can yield ACTIVE membership with all Lessons
completed; evaluate the same predicate there, without a client filter. This
implements CT16-LEARNER-HOME-2026-11-08-r1 §3
(no entry for a Course without adaptive mode or a current recommendation, shown
unaltered otherwise). Clients display the response as is and add no visibility
filter of their own. Owner insight reads stay unfiltered audit history (§14).

## 16. CT08.verification-plan

The following real-PostgreSQL cases are **specified, NOT_RUN**. QA04 owns atomicity in
`integration/MasteryProgressTransactionIntegrationTest.java`; QA03 owns flows in
`integration/AdaptiveLearningFlowIntegrationTest.java`; AD13/AS12/CO15 cover local
behavior with conforming doubles in `MasteryUpdateServiceTest`,
`QuizSubmittedEventListenerTest`, `AttemptServiceTest` and `LessonProgressServiceTest`.

| Case | Required observable outcome |
|---|---|
| B6-V01 | Manual submit in a ready Course commits result, mastery, skip progress and recommendation rows together |
| B6-V02 | Fault injected after mastery writes, inside Course participation and in recommendation insert: the failing transaction commits nothing; with no other terminal writer the Attempt remains unsubmitted; HTTP 500. The failed POST's masks roll back to the latest committed saved set. They equal POST masks M only if M was saved first and no intervening accepted writer changes that set; V12 covers another tab. An independent committed read checks result/mastery/progress/recommendation rollback |
| B6-V03 | Scheduler finalization follows the same path; a failure leaves the Attempt overdue and the next pass retries. A deterministic fault (test-scoped bean override) fails the same Attempt on two consecutive passes with nothing committed while another Course's candidate commits; after the override is removed (a simulated released fix) the next pass commits exactly one set of effects, and a manual resubmission of an active Attempt behaves the same. Answer durability: with M saved before the blocked manual submit, no other terminal writer and no accepted save changing M through finalization, expiry/fix stores M's deterministic correctness and score with `submitted_at` = `expires_at`. Without that condition, finalization grades the latest committed saved set (possibly another tab's N, V12). Mastery uses the latest-evidence tuple and routing uses current inputs; equal masks/score alone do not guarantee identical effects to an earlier manual submit. Residual exposure is observed, not prevented |
| B6-V04 | Duplicate submit and repeated finalize publish no second event and add no rows; the application context has exactly the approved `QuizSubmittedEvent` subscriber set (§4) |
| B6-V05 | Overdue older Attempt finalized after a newer submission does not regress any state |
| B6-V06 | Disabled or not-ready Course: mastery committed, no new progress or recommendation; disabled executes no readiness/coverage read. Null policy: no Adaptive rows. Where the Course row remains in the ACTIVE published population, disabling, hiding the target or completing a continue target makes guidance's currentRecommendation and Home's included recommendation/target title null. Apply R14: all Lessons complete with a still-valid remedial review remains visible in course guidance; no remedial candidate → null. On stored membership becoming COMPLETED, Home omits the Course row; ACTIVE rejoin with the same completed Lessons includes it and uses the identical validity predicate. R15's selected-name change hides R, unrelated rename leaves it visible. Reads write nothing, current titles come from Course, and owner history retains R in every case |
| B6-V07 | `left` learner finalized after leaving: mastery only |
| B6-V08 | Concurrent same-Course submissions serialize on the root; first submission versus threshold edit follows ADR005 §13.2 |
| B6-V09 | Calls to §3 methods, `readLatestSkillEvidence` or `readCoverageForExecution` without the held root, from another thread, for another Course ID than the bound marker, after the guard callback returned, or in a read-only/REPEATABLE READ transaction fail before any query or effect; a call from `start` or an answer save (no marker) fails the same way. `PracticeDifficultyProvider` outside a read-write transaction, for a non-Learner or a non-member, or with a foreign skill fails before reading mastery, and has no production caller. `readMyLatestSkillEvidence` returns only the current Learner and denies another Course, LEFT or absent membership and non-Learners; `readOwnedLatestSkillEvidence` denies non-owners and Administrators; allowlist tests find no other caller (§12) |
| B6-V10 | Diagnostic gate of §11 and init of in-scope skills only. `placementQuizId`: no diagnostic attempt → first published diagnostic Quiz; active attempt on D2 → D2 even when D1 sorts first; overdue or submitted diagnostic attempt, or an active attempt whose Quiz is now hidden → null and no offer; starting the returned Quiz never yields 409 or 404 in the same state |
| B6-V11 | Route (c): a deterministic fault injected in step 6 or 7 blocks submissions; the owner disables adaptive mode through the settings operation with the current ETag; the next submission commits result and mastery only, while a fault injected in steps 1–5 still fails after disabling |
| B6-V12 | Deterministic two-client schedule on one Attempt: A saves M and receives 200; a barrier allows B to save distinct N and receive 200 before A POSTs M. Inject an Adaptive fault: A gets 500; independent committed reads show N, unsubmitted state and no effects from A's POST. With no further writer, advance the test clock to expiry, remove the fault, finalize: correctness/score are grading(N), submittedAt=expiresAt, mastery is selected from committed evidence and skips/recommendations follow current routing. Choose M/N with different reviewed scores to detect accidental preservation of M; repeated finalize adds no effects. A second case saves N after A's rollback but before expiry, proving the conditional guarantee extends through finalization |

Client oracle B6-C01, in the existing `frontend/src/features/assessment/components/assessment.test.tsx`:
Submit captures one mask set M for the user action and sends its save, then its
POST only after 200, with that identical set. No POST follows a failed, rejected
(including 409), unknown or aborted save. A failed/rejected/unknown save or submit
reads the Attempt before another deliberate Submit is enabled; nothing is retried
automatically. Simulate another tab's accepted N between A's successful save and
failed POST: GET returns N (or another writer's terminal result), the client shows
that server state, retains any still-current local M only for explicit reconciliation,
and neither claims M was preserved nor automatically writes M over N. This client
oracle checks transport/UI behavior; V12 proves the database/event consequences.

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
B6-V cases and client oracle B6-C01; the save-before-submit sequence in the
quiz-taking client (§5); finite transaction and statement deadlines (ADR005 §13.3);
runtime grants; CT29 notifications; CT36.F evaluation data and results. LD-11's joint
Course, Assessment and Adaptive owner review of §§3–5 stays open until it actually
takes place. §5's operational risks (blocked submission and answers graded from
the last committed save) are PENDING_LEADER_RISK_ACCEPTANCE. No deployment,
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

## 20. Final technical review remediation — 13 November 2026

A second independent technical review of PR #236 at `2f3dbf2` found the findings
below. Under the Leader's explicit instruction **“CodeMastery — Batch 5 & 6 Final
Technical Findings Remediation”** they are corrected **before integration and within
the same r1 revisions**: no revision was ever effective, so no effective approval is
amended. The branch was first restacked onto the remediated Batch 5 head; that
restack changed no Batch 6 line. Checkpoint membership, IDs, scopes and the selected
transaction model are unchanged; no schema, migration, dependency or architecture
change is introduced. Two findings were re-assessed as P2 because, uncorrected,
they would show learners guidance that no longer holds (P3-4) and leave CT16's
Learner Home unable to label its entries without fan-out (P3-9).

| Finding | Severity (review → final) | Correction | Clauses |
|---|---|---|---|
| F-B6-01 | P2 → P2 | Answers sent in a rolled-back POST are lost and finalization grades the last committed save; consequences for result, mastery and recommendations stated; save-before-submit client obligation with its exact guarantee and limits; risk text extended | §5, §17, B6-V02, B6-V03, B6-C01 |
| P3-1 | P3 → P3 | Assessment-private, thread- and transaction-bound execution-scope marker bound inside Course's guard callback; no Course lock or type is read | §4, §12, B6-V09 |
| P3-2 | P3 → P3 | "Imports no Adaptive type" limited to the publication path; existing `SkillGraphService` uses and §8's port kept | §4; ADR001 §16 |
| P3-4 | P3 → **P2** | Visible current recommendation requires that a read-only evaluation of §7 on current inputs reproduces the stored row; history stays complete | §7, §15, B6-R13, B6-V06 |
| P3-7 | P3 → P3 | Server-chosen `placementQuizId` from Assessment's `readMyPlacementQuizId`, applying the start gate to the snapshot | §11, §15, B6-V10 |
| P3-8 | P3 → P3 | Name order: Java `String.compareTo` (UTF-16 code units), no normalization, locale or collation, UUID tie-break | §7, §9, B6-R12 |
| P3-9 | P3 → **P2** | `/api/me/recommendations` rows carry `courseTitle` and `targetLessonTitle` from Course's `LearnerCourseRef` | §15, B6-V06 |
| P3-10 | P3 → P3 | Recovery route (c): disabling adaptive mode avoids step 6–7 failures only and needs a valid stored policy; disabled Courses skip readiness entirely; failing step logged | §4, §5, B6-V11 |
| P3-11 | P3 → P3 | `PracticeDifficultyProvider` takes no learner argument; current Learner, CT03 admission, read-write transaction and skill scope required; no production caller until the §17 amendment | §8, B6-V09 |

The new and extended cases are specified and NOT_RUN. This section is documentation
correction only; it is not independent review, CI evidence or Leader approval.

## 21. Comprehensive independent review remediation — 13 November 2026

Under the Project Leader's explicit **“CodeMastery — Execute All Independent
Review Findings Remediation”** instruction, Codex corrects N-B6-01 and N-B6-02
from the independent review of `e91f273`. The branch receives the corrected
Batch 5 through a normal merge preserving all existing Batch 6 commits. These
are pre-integration corrections within the existing r1 revisions; checkpoint
membership, effective upstream contracts and algorithms remain unchanged.

| Finding | Correction | Verification obligations |
|---|---|---|
| N-B6-01 (P2) | Rollback retains the latest committed saved set; preservation of a particular tab's M is conditional on no intervening writer, through finalization for score equivalence; risks and client reconciliation retain ADR007's last-accepted-save semantics | §5, B6-V02/V03/V12, B6-C01 |
| N-B6-02 (P2) | Keep valid completed remedial review, restrict rename invalidation to changed selected drafts, distinguish Home row omission from null guidance, and use the same predicate within each endpoint's approved population | §§7/15, B6-R08/R13–R16, B6-V06 |

No Assessment revision token, schema, dependency, routing redesign, history
filter for Instructors, exposure or operational-risk acceptance is introduced.
Isolated model checks are not real PostgreSQL/client integration evidence; the
application oracles remain specified, NOT_RUN. This is remediation self-assessment,
not independent approval.

Executed for this correction: an independent pure routing/visibility model for
R08/R13–R16, including completed review versus no candidate, selected versus
unrelated/continue renames, changed mastery/edge/mapping, hidden target, disabled
mode and Home omission versus null; barrier-controlled isolated save/rollback
models with B's N before A's POST and after A's rollback. All passed, retaining N
for finalization and its distinct reviewed score. These models execute no
application service or database. OpenAPI duplicate-key/reference checks and exact
semantic comparison passed: all 751 reference occurrences resolve, all 87
operations remain, and only five Batch 6 descriptions change beyond the inherited
Batch 5 correction. Real PostgreSQL and quiz-client integration remain NOT_RUN.
