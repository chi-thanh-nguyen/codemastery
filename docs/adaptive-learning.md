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

The conceptual relationships are recorded in [the data model](data-model.md).
This document establishes no traversal order, graph algorithm, SQL representation,
cycle-detection implementation, separate graph database, or prerequisite
depth/weighting rule.

## 4. Mastery Model

The mastery states are exactly:

- `Unknown`
- `Learning`
- `Mastered`
- `Weak`

Assessment evidence is mapped to these states using configurable thresholds.
The resulting mastery state is an input to adaptive decisions. Threshold values
are configuration/implementation details requiring approval before dependent
implementation. They must be applied consistently across assessment processing,
mastery evaluation, adaptive decisions, and evaluation scenarios.

The architecture describes course `adaptive_settings` as JSON configuration for
mastery thresholds. As explained in the data model, that conceptual choice does
not define JSON keys, physical storage, or numeric values.

No score ranges, percentages, weighting formulas, decay functions, confidence
scores, attempt aggregation, historical weighting, or state-transition formulas
are selected here.

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

The exact difficulty catalogue, state-to-difficulty mapping, question-selection
algorithm, probability distributions, and minimum/maximum quiz question counts
are **DEFERRED**. No specific levels or mapping are implied by this document.

## 8. Assessment Inputs and Mastery Updates

Quiz results provide evidence for real-time mastery updates. Per-attempt and
answer-level information are approved inputs, with question-to-skill mappings
connecting that evidence to the assessed skills. `Attempt` and `AttemptAnswer`
support this information in the conceptual data model.

Manually graded file-submission assignments contribute to course assessment and
completion grades, but do not directly trigger real-time mastery updates in
Adaptive. This preserves the approved distinction between quiz evidence and
manual grading.

Scoring and aggregation across questions or attempts remain undefined. Neither
the conceptual entities nor the input flow supply those formulas.

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
Instructor access to learner recommendation history supports inspection of the
adaptive decisions. No additional persisted fields or physical schema are
established here.

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
must remain consistent with [the conceptual data model](data-model.md).

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

The following remain **DEFERRED** until approved before dependent implementation:

| Area | Undefined details |
|---|---|
| Mastery classification | Concrete threshold values, scoring formula, and state-transition mechanics beyond the four approved conceptual states. |
| Evidence handling | Aggregation across questions/attempts, weighting, historical evidence handling, and any additional calculation rules. |
| Skill graph implementation | Traversal and cycle-detection implementation; no prerequisite depth/weighting policy is established. |
| Practice/coverage | Difficulty levels and state-to-difficulty mapping, question-selection rules, quiz counts, and numeric minimum question/coverage requirements. |
| Routing/Test-out | Complete skip eligibility formula, remedial-selection algorithm, multiple-prerequisite precedence, tie-breaking, and recommendation prioritization. |
| Integration | Concrete public signatures and event payload schemas, listener/execution timing, retry/idempotency behavior, and detailed transaction propagation. |
| Persistence/configuration | Exact persisted representation beyond the conceptual data model, concrete adaptive-settings keys, and physical schema details. |
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
- [Conceptual data model](data-model.md): skills, progress, mastery,
  recommendations, assessment evidence, and deferred physical representations.
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
