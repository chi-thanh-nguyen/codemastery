# ADR 001: Modular Monolith Module Boundaries

## 1. Status and Scope

**Status: Approved.** This ADR records the already-approved CodeMastery Modular
Monolith module-boundary decision. It is the project record for these boundaries
and must be read with the approved architecture and repository instructions.
It defines architectural obligations, not evidence that the corresponding
implementation is complete.

## 2. Context

CodeMastery is a course-based programming learning platform developed as a
multi-contributor academic project. Its approved architecture uses one backend
language, a relational database, and object storage, with functional modules
inside a single application. This supports maintainability, debugging, and
parallel development without introducing microservices at the project's current
scale.

Explicit module ownership and layering are necessary to coordinate contributors,
keep business rules in the appropriate module, and integrate learning,
assessment, adaptive decisions, notifications, and reporting consistently.

## 3. Decision

The backend is one deployable Spring Boot application containing six logical
modules. Microservices and independently deployable backend services are outside
the current architecture. Module locations must follow the
[approved repository structure](../repository-structure.md).

| Module | Approved responsibility |
|---|---|
| `auth` | Accounts, roles, authentication, API protection, and account status. |
| `course` | Course catalogue and structure, lessons, learning materials, enrolment, and learning progress. |
| `assessment` | Quizzes, assignments, grading, attempts, and assessment results. |
| `adaptive` | Skill graph, mastery updates, adaptive next-lesson and practice-difficulty decisions, and explainable recommendations. |
| `interaction` | Lesson Q&A, creation and delivery of in-app notifications, and content reports. |
| `admin` | User/course administration, content-report handling, reporting metrics, and administrative audit logging. |

These responsibilities remain logically separated within the same deployment.

## 4. Internal Layering

Each module follows the four approved layers:

| Layer | Responsibility |
|---|---|
| `api` | REST controllers, request/response DTOs, and API-boundary input validation. |
| `application` | Use cases, application services, orchestration, transaction boundaries, and approved ports/events. |
| `domain` | Domain models, business rules, invariants, and domain abstractions. |
| `infrastructure` | Persistence adapters and technical integrations. |

Business logic belongs in the domain and application layers. Controllers use
DTOs and must not expose persistence entities through REST APIs. Infrastructure
concerns remain in infrastructure, and business invariants are enforced in domain
or application logic. Centralized exception handling is used where applicable,
as required by [the contribution rules](../../CONTRIBUTING.md#backend-guidelines).

## 5. Encapsulation and Dependency Rules

- Module internals are encapsulated. A module must not directly access another
  module's internal implementation.
- Cross-module communication uses an approved public application interface,
  approved port, or approved event mechanism. An undefined interaction requires
  clarification and approval before dependent implementation begins.
- Infrastructure implementation details must not become informal cross-module
  APIs. Persistence adapters and other technical implementations remain subject
  to the same encapsulation rules.
- Backend APIs enforce authorization independently. Frontend route guards do not
  replace backend authorization.

This ADR does not establish additional dependency directions or concrete public
interfaces beyond the approved interactions below.

## 6. Approved Cross-Module Interactions

The [canonical approved architecture](../references/originals/approved-architecture.docx),
section 7, establishes these interactions:

| Interaction | Approved boundary |
|---|---|
| Assessment → Adaptive | Submitted-quiz results and answer-level information are communicated through the approved event-driven mechanism. Assessment provides the assessment information; Adaptive owns mastery updates and adaptive decisions. |
| Adaptive → Course/Learning Progress | Adaptive, test-out, and mastery decisions affect the related `LessonProgress` state and learning guidance while preserving canonical course order. |
| Adaptive → Interaction/Notification | Adaptive provides recommendation and reason information through the approved event interaction. Interaction owns creation and delivery of in-app notifications. |
| Adaptive → Admin/Reporting | Reporting consumes mastery/progress information as learning-outcome data. Adaptive retains ownership of adaptive-decision logic. |

The mastery update and related `LessonProgress` update must succeed or fail
together within one database transaction. Partial updates must not leave these
states inconsistent. This is an architectural consistency requirement;
implementation-level transaction propagation and event execution details are
deferred to approved integration contracts.

## 7. Adaptive-Learning Boundary Invariants

- Adaptive Learning is rule-based and runs inside the Spring Boot backend. It is
  not a separate AI, ML, or Python service.
- The course structure retains its canonical lesson order. Adaptive recommends
  next steps and marks related learning-path/progress state without rewriting
  that structure.
- A missing prerequisite mastery state must not, by itself, hard-block access to
  an advanced lesson; the approved behavior provides guidance and a warning.
- Adaptive behavior is scoped to one course.
- Admin/Reporting consumes learning data and does not own adaptive decisions.
- Interaction owns in-app notification creation and delivery.

The full adaptive business-rule and evaluation specification belongs in
[adaptive-learning documentation](../adaptive-learning.md), subject to the
[approved architecture](../architecture.md#6-advanced-component) and
[repository instructions](../../AGENTS.md#critical-domain-invariants).

## 8. Consequences

The decision provides explicit ownership for contributors and limits direct
coupling between module implementations. The approved package and layer
boundaries support parallel work while requiring coordination whenever modules
exchange information.

The backend remains a single deployment unit, preserving the operational
simplicity selected for the current project scale. Cross-module contracts must
be explicitly agreed, and changes must preserve the relevant ownership and
transactional invariants. These are design consequences, not measured benefits
or claims of verified implementation.

## 9. Deferred Decisions

This ADR intentionally leaves the following details to later approved contracts:

| Deferred detail | Required contract before dependent implementation |
|---|---|
| Concrete public application interface/port signatures and package APIs | The approved public application or integration contract for the affected modules. |
| Event payload schemas or concrete implementation classes | The approved event contract between the producer and consumers. |
| Listener/execution timing, retry policy, and idempotency behavior | The approved execution and failure-handling contract for the interaction. |
| Detailed transaction propagation and event/transaction execution settings | The approved integration contract, preserving the atomic mastery/progress requirement. |
| REST operations, request/response payloads, and feature-specific DTOs | [The OpenAPI specification](../api/openapi.yaml) and corresponding approved API contracts. |
| Physical database schema | Approved details in [the data-model document](../data-model.md), implemented through Flyway migrations. |

These contracts must be defined and approved before their dependent code is
implemented. Their filenames or scaffold presence do not establish their
contents. Deferral does not relax the boundaries or consistency requirements
recorded in this ADR.

## 10. Compliance Rules and References

Future backend work must preserve this ADR unless an explicit architecture
change is approved. Changes must remain consistent with mandatory requirements,
the canonical approved architecture, and the approved repository structure.
Undefined or conflicting cross-module contracts must follow the Blocker Protocol
in [AGENTS.md](../../AGENTS.md#blocker-protocol) before affected implementation
proceeds.

Use these documents for their respective authority:

- [AGENTS.md](../../AGENTS.md): repository-wide instructions, architectural
  invariants, source precedence, and blocker handling.
- [CONTRIBUTING.md](../../CONTRIBUTING.md): contribution workflow, module
  encapsulation, layering, testing, and documentation synchronization.
- [Canonical approved architecture](../references/originals/approved-architecture.docx),
  especially sections 7–8, and [its Markdown derivative](../architecture.md):
  approved responsibilities, interactions, and architectural rationale.
- [Repository structure](../repository-structure.md): approved module, layer,
  and file locations.
- [Requirements](../requirements.md), with the
  [course requirements PDF](../references/originals/project-requirements.pdf) as
  its canonical source: mandatory project constraints.

The source-of-truth hierarchy remains the one defined in AGENTS.md. This ADR
records the existing approved boundary decision without changing governance
documents or immutable reference artifacts.
