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

## 11. CT01 completion proposal — pending authoritative integration

### 11.1 Authority and unchanged baseline

**Status: TECHNICAL_SELECTION_RECORDED; PENDING_AUTHORITATIVE_INTEGRATION.**
Sections 1–10 retain their existing approval. This section records Codex's scoped
technical selection under the Project Leader's instruction of 2026-10-17
(Asia/Ho_Chi_Minh), for [CT01 #18](https://github.com/chi-thanh-nguyen/codemastery/issues/18).
That instruction delegates analysis/selection and the three CT01 documentation
paths; it is not a separate manual Leader review or implementation authorization.
Required owner/consumer review, explicit Leader sign-off and protected merge into
`main` remain necessary. Logout additionally requires the explicit risk acceptance
in §11.3; merging without that evidence must not be described as logout approval.

Baseline inspected: `main` at `43e399927b4cb36587184fcf4f4474bc88095852`, current
CT01/CT16/CT18/CT38 and AU05/AU06/FE03/CO04/CO05/QA01/QA02/QA07 Issues,
canonical requirements §4.1/§6, approved architecture §4.1/US-01/US-02,
V001 and [ADR002](002-interim-delegated-contracts.md). Auth/security and Auth
frontend files are empty scaffolds at this revision. Shared REST/configuration
code exists; it does not implement authentication. ADR002's `CT01.core`,
`CT16.minimum`, `CT18.minimum` and `CT38.core` remain unchanged. Its historical
deferrals identify the gaps addressed here, not approval of these new selections.

### 11.2 Alternatives and selected contracts

| Decision | Selected solution and rationale | Rejected/deferred alternatives |
|---|---|---|
| Logout/revocation | Client-local logout, fixed non-sliding 15-minute access JWT; explicit residual-risk acceptance required. Fits existing bearer/memory policy without new state/schema. | Database token versions/session tables need schema approval; external revocation storage adds infrastructure; an in-memory denylist can resurrect revoked tokens on restart. A fail-closed allowlist avoids resurrection but adds session state, restart logouts and capacity/concurrency obligations without a requirement for immediate revocation. |
| Identity | Minimal read-only Auth application facade in existing `UserAccountService.java`, with immutable nested public boundary types. | Importing `AuthenticatedUser`, Auth entities/repositories or joining Auth tables leaks internals; a new top-level interface/file needs structural approval; JWT claims alone cannot enforce current account state. |
| Instructor identity | Bulk UUID-to-displayName lookup for Course-owned IDs; no email or account objects. | Per-row lookups encourage N+1 queries; extending REST DTOs here would reopen CT21's selected shape; a public user-directory endpoint is unnecessary. |
| Authorization | Auth validates identity/current status; owning module enforces explicit role and resource rules, default deny. | Frontend guards or token role claims alone permit bypass/stale privileges; implicit Administrator superuser inheritance is not approved. |
| Client coordination | Existing memory-only provider; session-generation checks prevent old responses restoring/clearing a newer session. | Persistent token storage, cookies, refresh sessions and automatic POST retries change approved contracts or replay risk. |

### 11.3 Login, logout and token lifecycle

Preserve ADR002 exactly: canonical ASCII email, unchanged password predicate,
BCrypt `$2b$` cost 14, Learner-only registration without auto-login, DTO role
values `Learner`/`Instructor`/`Administrator`, and the existing register/login
OpenAPI payloads. `AuthService` owns orchestration and the uniqueness-race-to-409
mapping. Supplied role/status or other unknown registration properties are 400.
Missing/null/wrongly typed login fields or invalid email syntax are 400; otherwise
incorrect credentials, including an unsuitable password, missing account or
locked account, share the same 401 representation. Never truncate/normalize a
password; do not call BCrypt with an over-72-byte candidate. Such candidates take
the generic failure path with dummy verification, as do missing users. Do not
log supplied values or expose which credential/account check failed.

JWT requirements remain HS256, the 32-byte hex-decoded runtime key, issuer
`codemastery`, audience `codemastery-api`, UUID `sub`, NumericDate `iat`/`exp`,
`exp = iat + 900 seconds`, zero skew and rejection at `now >= exp` or `iat > now`.
Issue `typ: JWT`; reject other token types, non-HS256 algorithms, missing/malformed
required claims and failed signature/issuer/audience validation. Never fetch keys
from token-supplied URLs. No password/email/displayName/status/role claims are
needed: role and active/locked status come from Auth-owned data on every protected
request, with no cross-request account-state cache. A missing/locked account is
401; a state-read failure fails closed with a safe 500, not cached authorization.
This does not promise cancellation of a request admitted before a concurrent
account lock; subsequent protected requests must observe committed account state.

There is no refresh token, sliding renewal, persistent login, idle-timeout claim,
server session or logout endpoint in this selection. Login creates an independent
15-minute credential; a later login does not revoke earlier credentials.
Logout immediately clears that browser provider's token, user and private cached
data and invalidates its outstanding session generation. No HTTP call is needed;
logout works offline. Reload also loses the credential. Independently logged-in
tabs/devices are separate sessions; no cross-tab/global logout guarantee is made.

**Material residual risk / Leader-reserved acceptance:** a copied access JWT
remains usable after client logout for its remaining lifetime (at most 15 minutes
from issuance), while the account is active and the signing key unchanged. An
attacker can act during that window; this design is not server-side revocation.
Account locking blocks subsequent protected requests but is a CT12-owned action,
not a logout implementation; unlocking before expiry can make an old token usable
again. Key replacement invalidates all old signatures and is an operational
incident action, not routine per-user logout. Explicit Leader sign-off must state
acceptance of these facts for this bounded policy, or require a separately
approved revocation design. Codex has not supplied that human risk acceptance.

### 11.4 CT01.identity: exact public boundary and U01 disposition

Proposed public application facade:
`com.codemastery.modules.auth.application.service.UserAccountService`, at
`backend/src/main/java/com/codemastery/modules/auth/application/service/UserAccountService.java`.
Its only cross-module read methods/types selected here are:

```java
public CurrentIdentity requireCurrentIdentity();
public Map<UUID, String> instructorDisplayNames(Set<UUID> instructorIds);
public record CurrentIdentity(UUID userId, IdentityRole role) {}
public enum IdentityRole { LEARNER, INSTRUCTOR, ADMINISTRATOR }
```

The record/enum are nested public types in that existing file, not new top-level
files. UUIDs and Java collection/string types are the only other exposed types.
`requireCurrentIdentity()` derives identity solely from the trusted request
security context established by Auth after JWT/current-account validation. No
caller-supplied user ID, request DTO or constructed record establishes identity.
No identity exists outside that authenticated request; absence/invalidity raises
Spring Security `AuthenticationException` for the generic 401 path. Infrastructure
principal adaptation stays inside Auth; the application-facing facade must not
expose or require an `AuthenticatedUser` parameter. A future implementation must
preserve application/infrastructure dependency inversion inside existing files.

`instructorDisplayNames` is a read-only, bulk Auth-owned lookup returning an
immutable map of requested Instructor IDs to stored public display names. Course
supplies only IDs from its selected published-course results, bounded by that
operation's existing page/detail scope; empty input returns an empty map, missing
or non-Instructor IDs are omitted, and storage failure propagates as a safe 500.
It returns no email, password/hash, status, entity or mutable account reference.
Account locking is an authentication restriction, not a new rule hiding an
otherwise published course's instructor name. Course must not fabricate names
for omitted IDs. The existing instructorId-only REST DTOs remain unchanged;
their display-name field/fallback requires a CT21 consumer contract before use.

Permitted direction: Course application services → these Auth application reads;
other modules may consume `requireCurrentIdentity()` for already-selected
protected operations, but gain no new resource policy or user-directory access.
Auth never calls Course to satisfy these reads. Auth repositories, domain account
objects and security infrastructure remain private. Own-module services resolve
ownership/enrolment using the trusted UUID; the identity record is not permission
to read another module's data.

The historical planning candidate **U01 (A04 → C03)** identifies infrastructure
identity leakage. This proposal binds its current-actor consumption to the
facade above, reviewed by Auth @duyduyit, Course @TamiaoNguyen and Leader
@chi-thanh-nguyen. U01 remains pending authoritative integration; no graph/native
relationship is changed. Public catalogue/detail need no current actor.
Protected Course method signatures/resource rules (CT03/CT21/CT22), instructor
display REST composition, other feature bindings, recovery (CT02), and Admin
mutation/audit boundaries (CT12/CT31) remain separately gated. Those exclusions
do not prevent review of this read-only identity contract.

### 11.5 Selected authorization, errors and client/runtime handoffs

| Surface/capability | Authentication and authorization contract |
|---|---|
| POST register/login; GET published catalogue/detail | Anonymous as already specified. The client sends no bearer credential on these public operations; unrelated invalid bearer input must not turn the selected public catalogue into a protected resource. No hidden data is exposed. |
| Current-actor identity read | Active, currently validated account of any of the three roles; returns only self UUID/current role. No new REST identity/probe endpoint. |
| Instructor-owned content management | Instructor role plus owning Course/resource check; Learner denied with 403. CT03/CT21 retain exact operation/binding approval. |
| Administrative capability | Administrator role; Learner/Instructor denied with 403. CT12 retains mutations, exact operations and auditing. No implicit role inheritance. |
| Other protected feature operations | Require explicit owning-contract role/resource policy before exposure; unselected routes/capabilities deny by default. No wildcard authenticated access to all `/api/**`. |

Authentication runs before protected business effects. Missing/malformed/expired
bearer credentials or missing/locked accounts yield identical safe 401 errors;
an authenticated active account failing its selected role/resource policy gets
403 and no business effects. Ownership predicates do not follow from role alone.
Auth's filter-chain `RestSecurityErrorHandler` owns authentication/authorization
errors, including method-security failures; it uses CT18 ProblemDetail just like
MVC, without relying only on `GlobalExceptionHandler` (which rethrows security
exceptions). Use `WWW-Authenticate: Bearer` on 401, without account/token detail.
Do not mislabel role denial as token failure or erase a valid session on 403.

CT16/FE03: tokens stay solely in AuthProvider memory; send Authorization only to
the configured API origin, never URL/cookies/storage/logs. Recheck the origin on
redirects: reject redirected authenticated requests rather than forward a bearer
credential to another origin. Clear on current-session 401/expiry/logout, ignore
late responses from superseded generations, never restore a logged-out session
from an in-flight login, and discard private results after logout. A late 401
from an old session must not clear a newer login. 403 preserves the session;
network/5xx failures are not authentication failures. The UI timer uses receipt
time plus expiresIn as an upper bound, also stops at the JWT exp (decoded only for
UI scheduling), and checks expiry before sends/on resume; server validation is
authoritative. No retry of mutations or silent renewal. Browser-controlled state
and role guards never authorize backend access.

CT18: preserve application/problem+json, about:blank, standard title, status,
safe detail, path-only instance and sorted validation errors without rejected
values. Use the existing shared messages `Authentication failed.` / `Access is
denied.`. Register/login responses and all authentication failures use
`Cache-Control: no-store`; token-carrying traffic requires HTTPS outside the
already-approved loopback development environment. Cookie/session authentication
is not selected. CORS must permit only the consumed explicit origins/methods and
Authorization/Content-Type headers; unauthenticated preflight grants no business
permission. This is not a general CSRF exemption for future cookie-based features.

CT38: retain JWT_SECRET/JWT_EXPIRATION/CORS_ALLOWED_ORIGINS/VITE_API_BASE_URL,
PT15M, fixed issuer/audience and consumption-scoped fail-fast validation. Existing
AppConfig bindings implement part of that input validation, not JWT/security/CORS
enforcement. No new environment variable, secret value, dependency or production
origin is selected. B01/B02 are addressed only for these consumed fields;
production abuse controls, host BCrypt capacity and deployment policy remain
separate prerequisites before external exposure.

Security limits: memory-only storage reduces persistence exposure but cannot
prevent active XSS from stealing/using the bearer token. HTTPS, safe rendering
and secret-free logs remain required; CORS is not authorization or XSS protection.
The already-approved registration 409 reveals canonical-email availability even
though login failures are generic; this is retained policy, not a new privacy
guarantee. Expensive BCrypt does not replace abuse controls or load testing.
No new numerical rate limit, automatic account lock, production exposure or
cookie policy is selected here.

### 11.6 Acceptance examples, owners and remaining gates

These are contract examples, **not executed security verification**:

| Given / action | Required result and evidence owner |
|---|---|
| Valid unused email, approved password/displayName; register | 201 Learner, encoded password, no token; supplied role/status → 400; canonical email race → 409. AU04/AU06, QA01. |
| Valid active account / wrong password / missing account / locked account; login | 200 exact LoginResponse for valid active account; otherwise generic 401 with costly verification and no credential disclosure. AU05/AU06, QA01. |
| Wrong alg/signature/issuer/audience/type; missing claims; future iat; now=exp | Protected authentication fails with generic 401, no side effect. AU05, QA01. |
| Valid token, then committed account lock | Next protected request → 401; database failure → safe 500 and no business work. AU05, QA01. |
| Learner invokes selected Instructor/Admin capability | 403, unchanged state; instructor with wrong resource ownership also denied under owning contract. QA01 requires an approved real operation binding. |
| Logout / copied token / delayed login or 401 response | Browser clears immediately; copied token can still authenticate until expiry while account active; delayed responses cannot revive/erase a newer session. FE03/QA07, AU05/QA01; residual-risk gate retained. |
| Course consumes identity / renders current selected public DTO | UUID/current role or requested public names only; no Auth internal imports; instructorId-only payload unchanged. AU06/CO04/CO05, Auth/Course review. |

Review must include Auth, Frontend @heizseung, Course and affected protected-operation
owners. The three documentation paths are Leader-owned. Evidence of technical
selection is this record and its PR; evidence of approval must identify actual
review/sign-off and merged revision, including explicit §11.3 risk acceptance.
No self-authored examples or CI build claim actual security verification. QA01
must later execute the real configured security chain and PostgreSQL account
state, selected approved role/resource operations, and contract JWT boundary cases
at the approved Auth/AuthorizationIntegrationTest locations; QA07 independently
checks browser session behavior. No fake security probe endpoint is authorized to
make a role-denial test pass. Missing protected operation bindings remain an
explicit execution gap, not proof that anonymous catalogue tests satisfy US-01.

Issue #18's registration checkbox can reflect effective ADR002 CT01.core after
authoritative provenance confirmation. The combined login/logout, identity/API,
cross-contract reconciliation, CT01.identity and U01 checkboxes stay pending until
their entire stated conditions have effective approval. No Issue closure, Project
status/readiness/dependency change, implementation, deployment or real security
verification is authorized by this contract proposal.

Technical references: [JWT BCP RFC8725](https://www.rfc-editor.org/rfc/rfc8725.html),
[Bearer transport RFC6750](https://www.rfc-editor.org/rfc/rfc6750.html),
[OWASP REST security](https://cheatsheetseries.owasp.org/cheatsheets/REST_Security_Cheat_Sheet.html).
They inform the analysis; project policy and approval come from the sources and
explicit authority above. Codex analyzed and authored this documentation; no AI
service is added to the application.
