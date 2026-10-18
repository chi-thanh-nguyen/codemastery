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

**Bounded update, 2026-10-18:** PR #82 is integrated as `39748f6`. The Leader
separately signed off CT01.identity and its limited U01 binding by explicit
instruction in this Codex session; §11.4 records that approval. Remaining CT01
conditions, logout risk acceptance and required review/implementation/verification
gates are not approved or waived by that instruction.

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
Already-admitted server requests can finish after logout; clearing browser state
does not roll back committed work or cancel server-side effects.

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

**CT01.identity — explicit Leader technical approval, 2026-10-18
(Asia/Ho_Chi_Minh).** @chi-thanh-nguyen approved this read-only facade's existing
location, both methods, documented boundary types/semantics, Course → Auth
direction and limited U01 current-identity binding, referring to the shape already
integrated through PR #82 (`39748f62223fcb90bb198366a377ab9b26e9c05b`). Provenance:
the Leader's explicit response to the CT03 reconciliation question in this Codex
session; Codex records that instruction, not a separate manual GitHub PR review.
The approval expressly excludes logout residual risk, unresolved CT01 checkpoints,
implementation/deployment authorization and integration/security verification.
PR #82 has a Course APPROVED review from @TamiaoNguyen; no Auth/Frontend review
was found there. This instruction does not fabricate those reviews or waive
applicable protected review requirements for this reconciliation PR.

Approved read-only public application facade:
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
identity leakage. The Leader's 2026-10-18 sign-off approves its current-identity
binding to this facade with the documented consumer-specific limitations. Auth
@duyduyit, Course @TamiaoNguyen and Leader @chi-thanh-nguyen remain the affected
owners; no unobserved review is asserted. No graph/native relationship is changed.
Public catalogue/detail need no current actor.
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
after authentication, an unsupported role gets 403 before resource lookup.
The owning contract then distinguishes unavailable/concealed resources (404)
from authorization denial on a disclosed resource (403). For the proposed Course
predicates, §12.4 defines the exact order and concealment rules; there is no
blanket 403 override for those 404s. All denials prevent protected effects.
Ownership predicates do not follow from role alone. This clarification of pending
authorization selections changes neither effective CT01.core/identity approval
nor CT18's error representation.
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
their entire stated conditions have effective approval. The Leader's separate
2026-10-18 CT01.identity/U01 sign-off now supplies those two technical approval
records (§11.4); it does not satisfy the remaining combined conditions. No Issue closure, Project
status/readiness/dependency change, implementation, deployment or real security
verification is authorized by this contract proposal.

Technical references: [JWT BCP RFC8725](https://www.rfc-editor.org/rfc/rfc8725.html),
[Bearer transport RFC6750](https://www.rfc-editor.org/rfc/rfc6750.html),
[OWASP REST security](https://cheatsheetseries.owasp.org/cheatsheets/REST_Security_Cheat_Sheet.html).
They inform the analysis; project policy and approval come from the sources and
explicit authority above. Codex analyzed and authored this documentation; no AI
service is added to the application.

## 12. CT03 access and consumer contract reconciliation

### 12.1 Status, authority and inspected baseline

**New selections: PROPOSED_NOT_EFFECTIVE — pending review, Leader sign-off and
protected integration.** The Leader's 2026-10-18 instruction delegates Codex's
CT03 analysis, technical selection and this three-file proposal. It is not a
manual review, an approval of another contract family, or implementation authority.
The separate CT01.identity instruction recorded in §11.4 supplies that slice's
Leader sign-off; it is distinct from this CT03 delegation.
Existing effective decisions below retain their own authority; no supersession
is proposed. [CT03 #22](https://github.com/chi-thanh-nguyen/codemastery/issues/22)
remains partially approved through ADR002 `CT03.catalogue`.

Sections 12.4–12.6 are the single detailed record of the proposed internal CT03
access contract. OpenAPI retains the separately approved REST contracts; its CT03
extension and the data-model note reference this record rather than duplicate it.

Baseline: `main` at `35abfdb45667aca650df5f7fae8a2c218e98e2d1`. Inspected canonical
requirements §4.2–4.5/§6, architecture US-02/04/09–13 and no-hard-block rule,
canonical tree, AGENTS/CONTRIBUTING/current CODEOWNERS, ADR001/002, OpenAPI,
V001–V004/V006 reference constraints, actual Course/Auth/consumer inventories,
PR patches/reviews and Issue records. PR #4's V002 bytes match current V002.
Course application services, repositories, controllers and DTO files, Auth's
identity service, and the relevant Assessment/Interaction/Adaptive services remain
empty. There is no current Course query or access implementation to ratify.

### 12.2 Historical approval and contract reconciliation

| Decision and source | Approved scope; current implementation/consumer evidence and gap | Classification | Disposition |
|---|---|---|---|
| Foundation: PR #2 `418b8eb`, PR #4 V002, PR #5 `f85628e`; ADR001 §§3–9; current CODEOWNERS/PR #80 | Six modules, Course ownership, existing service locations and scalar references. Foundation/schema and file presence approve no feature query or cross-module callable. | APPROVED_AND_CONSISTENT | REUSE |
| V002; data model §11 | Publication hidden/published; enrolment active/completed/left; one row per learner/course; chapters have no publication state. These are representation facts, not access/mutation policy. | APPROVED_AND_CONSISTENT | REUSE |
| ADR002 CT03.catalogue / PR #73 `729ba60` | Anonymous published metadata; uniform missing/hidden 404; only published lesson headings in public syllabus. Current OpenAPI agrees; CO03/04/05 and FE07/QA02 lack running queries. No defect warrants replacement. | APPROVED_AND_CONSISTENT | REAFFIRM |
| ADR002 CT21.catalogue / PR #73 | Four-field summary and bounded detail, fixed pagination/order; instructorId-only is intentionally partial US-02. Search/filter, display-name REST composition and authoring remain outside approval. | APPROVED_BUT_INCOMPLETE | REUSE bounded DTOs; DEFER extensions to CT21 |
| ADR002 CT18.minimum / PR #73 | Safe ProblemDetail and 400/401/403/404 conventions. PR #78 supplies shared MVC primitives, not Course decisions or security-chain integration. | APPROVED_AND_CONSISTENT | REUSE |
| CO01 / PR #77 `9007494`; Issue #30, 17 October Leader acceptance | Accepted Course records/enums/scalar entities, including explicit two-file scope extension. No authorization, repository queries, JPA cross-module associations or PostgreSQL access evidence follows. | APPROVED_AND_CONSISTENT | REUSE representations |
| SH01 / PR #78 `166484e`; Issue #50, 17 October ratification | Bounded error/page/config/correlation findings ratified; historical bypass is non-retroactive. Independent human review remains outstanding; local MVC tests are not real Course/Auth integration. | APPROVED_BUT_INCOMPLETE | REUSE contracts; DEFER implementation acceptance to SH01 |
| PR #81 `35abfdb`, CourseFixtures | Reviewed structural helpers over CO01 types; construction is unchecked and supplies no access oracle or business policy. | APPROVED_AND_CONSISTENT | REUSE fixtures only with explicit case inputs |
| ADR002 CT01.core / PR #73 | Current Auth-owned status/role validation is approved. This slice does not select a role-inheritance policy; the explicit CT03 role restrictions below are new proposals. Auth implementation remains empty. | APPROVED_AND_CONSISTENT | REUSE current-account validation only |
| CT01.identity / PR #82 `39748f6`; §11.4; explicit Leader instruction 2026-10-18 | Shape integrated; Course review exists. Leader explicitly approves read-only identity/U01 only in this session. No Auth/Frontend PR review, logout-risk acceptance or implementation/verification is inferred. | APPROVED_AND_CONSISTENT | REUSE; record new sign-off, preserve remaining gates |
| CT22 #43; V002 lifecycle facts | Row reuse/history preservation are approved; mutation, repeat-request, progress/resume and atomic execution policies remain unresolved. A read predicate must not supply them. | APPROVED_BUT_INCOMPLETE | CLARIFY separation; DEFER CT22 choices |
| CT40 #46 | Structural Course/lesson lookup, skill tagging and question coverage signatures/placement remain unresolved; CT03 cannot approve them indirectly. | UNRESOLVED | DEFER structural interface; provide CT03 authorization prerequisite |
| CT03.learning-access and U03/U04 Course checks / Issue #22 | Architecture supplies required outcomes, but exact predicates/signatures had no effective record. §§12.4–12.6 make technical selections for review. | PROPOSED_NOT_EFFECTIVE | EXTEND |
| Stale status prose: CONTRIBUTING says OpenAPI empty; data-model §11 says initial representations unimplemented | Actual OpenAPI and accepted CO01 supersede those implementation-status observations, not architecture. Report stale CONTRIBUTING wording; this task does not edit it. | CONFLICTING_OR_OUTDATED | CLARIFY relevant data-model status; DEFER unrelated prose repair |

No CT03 access implementation was found to classify IMPLEMENTED_NOT_APPROVED.
Missing approval/verification is not a demonstrated access-control defect. PR
numbers above refer to `https://github.com/chi-thanh-nguyen/codemastery/pull/<number>`;
Issue #30/#50 contain the bounded current ratifications and retained historical
exceptions, not authority for future review/check bypasses.

### 12.3 Catalogue reaffirmation and alternatives

Reaffirm ADR002 `CT03.catalogue` unchanged. Anonymous list/detail remain independent
of Auth identity and membership. Apply publication filtering before pagination,
counting and DTO mapping; totalElements must not count hidden courses. Missing
and unpublished detail share the same CT18 404 representation. Public syllabus
contains published lesson headings only, omits chapters without them and preserves
chapter/lesson positions. No chapter publication flag exists. No hidden title,
lesson body/material/storage URL, enrolment/progress or adaptive settings escapes.
Reuse CT21's existing fields, query validation and ordering; no new REST route,
payload field, search/filter option or instructor lookup is needed for this slice.

| Question | Selected solution and reason | Alternatives rejected or left separate |
|---|---|---|
| Public discovery | Retain anonymous published projection and 404 concealment; satisfies approved discovery without Auth coupling. | Mandatory login adds no required protection to public metadata; 403 for hidden public detail discloses existence. |
| Learner membership | Current enrolment ACTIVE or COMPLETED permits learning; LEFT/absent does not. Completion must not revoke study access. | ACTIVE-only denies completed-course learning; any historical row would let departed learners read. |
| Hidden content | Learner requires published Course and selected Lesson/material; owning Instructor may preview drafts. | Enrolment alone would expose hidden content contrary to US-10; published-only Instructor access prevents draft preparation. |
| Callable shape | Named void assertions in existing CourseAccessService; five public methods for identified external consumers, two package-private Course-only methods; derive actor inside Course from CT01 facade. | Boolean mode flags obscure purpose; caller-supplied actor UUID/role is impersonation-prone; exporting Course-only helpers, entity-returning or new interface files adds unnecessary coupling/scope. |
| Consumer data | Course asserts its access/ancestry; consumer owns assessment/post/graph-specific rules. | One universal gate would implicitly approve CT04/CT10/CT12/CT22/CT40 behavior or require their whole completion. |

### 12.4 Learning-access predicates and failure order

All new protected predicates consume the CT01 §11.4
`UserAccountService.requireCurrentIdentity()` boundary, explicitly signed off by the
Leader on 2026-10-18 as recorded above. No caller-provided actor,
role, enrollment row, request DTO or token claim proves identity. Its approved
active-account check must precede protected work. The identity approval does not
approve the new CT03 predicates: they retain their own review/sign-off/integration
gates. Logout risk acceptance remains outstanding; unrelated logout deferral does
not itself gate catalogue or the independently approved identity slice.
The explicit capability/role restrictions below, including Administrator exclusion,
are CT03 selections awaiting approval, not policies attributed to CT01.core.

| Predicate | Permitted actor and Course-owned conditions |
|---|---|
| Enrolment eligibility | Learner; published Course. No enrolment is created and no existing ACTIVE/COMPLETED/LEFT row is rejected merely to choose repeat/re-enrolment semantics; CT22 owns the operation. |
| Learner Course access | Learner; published Course; self enrolment ACTIVE or COMPLETED. LEFT/absent denies. |
| Learner Lesson access | Learner Course access plus actual Lesson → Chapter → requested Course ancestry and published Lesson. |
| Course ownership | Instructor with Course.instructorId equal to trusted self UUID; hidden or published Course allowed. No Administrator inheritance. |
| Lesson ownership | Course ownership plus actual Lesson/Chapter ancestry; hidden or published Lesson allowed. |
| General Lesson read | Learner Lesson access OR owning Instructor preview. No other role/capability is granted. |
| Material read | General Lesson read plus actual material → requested Lesson ancestry; Learner also requires published material. Owner preview may include hidden material. Storage delivery remains CT09. |

For valid nonnull identifiers, apply the following precedence to each assertion.
The first failing stage determines the result; stop the assertion at that result
and issue no further resource queries after denial. Evaluate applicable conditions
against the coherent snapshot described below; this order defines observable
failure precedence, not a requirement for one database round trip per stage.

| Stage | Required decision and failure |
|---|---|
| 1. Authentication | Missing/invalid credentials or missing/locked current account → generic 401 under CT01/CT18, regardless of role or resource state. |
| 2. Role | Require the predicate's role from the table above. General Lesson/material reads accept Learner or Instructor; the other predicates have one required role. Unsupported role → 403 before Course/descendant lookup, including when the requested resource is missing or hidden. |
| 3. Course availability/concealment | Missing Course → 404. Hidden Course → 404 for Learners and non-owning Instructors; an owning Instructor admitted by stage 2 may continue. Visibility concealment precedes membership denial. |
| 4. Course authorization | On a published Course, Learner learning-access/read predicates require self membership ACTIVE or COMPLETED; LEFT/absent → 403. Enrolment eligibility does not require membership. An Instructor admitted by stage 2 must own the Course; non-owner on a published Course → 403. |
| 5. Scoped descendants | For predicates accepting Lesson/material IDs, prove actual ancestry within the admitted Course and requested Lesson. Missing/foreign descendant → 404. Learner requires published Lesson and, when selected, material; hidden descendant → 404. Owning Instructor preview/ownership permits hidden descendants. Course-level predicates have no descendant stage. |

All stage-3/5 unavailable outcomes use the same safe 404 representation. Never
look up a foreign descendant independently and reveal its real Course. Required
identity/data-provider failures fail closed with safe 500; they are not missing
resources, authentication denials or permission to continue. An earlier denial
does not require a later provider call.

HTTP malformed UUID syntax is CT18 400 after protected authentication admission;
these Java signatures accept only nonnull UUIDs. Null is an internal caller-contract
error (IllegalArgumentException, safe 500 at the HTTP boundary), not a resource
lookup or a new client validation policy. Anonymous catalogue/detail bypass these
protected stages and retain §12.3's existing published-only contract.

Throw existing `ResourceNotFoundException` for unavailable/scoped-mismatch 404,
Spring Security `AccessDeniedException` for 403 and propagate authentication
failure to Auth's security handler. Do not use BusinessRuleException for 403: its
implemented contract allows only 400/409. Do not invent error subclasses/files.
CT18 ProblemDetail uses the same safe detail for indistinguishable 404s, no
resource/owner identifiers or rejected values. Keep 403 separate from 401 so FE03
does not erase a valid session on resource denial.

These are read/admission predicates, not durable capabilities or write locks.
Resolve ancestry and relevant publication/membership conditions from Course-owned
data as one coherent query snapshot per assertion, without a second-module table
join or cross-request allow cache. Protected content reads must also constrain
their actual output query to the same scope/visibility, not use a detached boolean
as permission to fetch arbitrary content. Each fresh Course assertion evaluates
Course visibility/membership against its coherent query snapshot, observing
hide/leave changes visible there. CT01 validates account state/role per new
protected request (§11.3); assertions within that admitted request use its trusted
identity without requiring redundant Auth revalidation. Subsequent protected
requests observe committed account locks; already-admitted work is not promised
cancellation. Perform checks before effects in the caller's approved unit of work.
No transaction propagation, lock protocol, progress mutation or new persistence
semantics is selected; CT21/CT22/CT08 retain those operation-level decisions where consumed.
Never consult mastery, prerequisite completion, adaptive_enabled, lesson position
or previous-lesson completion as a hard access gate. Canonical order is guidance.

### 12.5 Exact Course-side application surface

All seven proposed methods belong to the existing file
`backend/src/main/java/com/codemastery/modules/course/application/service/CourseAccessService.java`,
class `com.codemastery.modules.course.application.service.CourseAccessService`:

```java
public void requireLearnerCourseAccess(UUID courseId);
public void requireLearnerLessonAccess(UUID courseId, UUID lessonId);
public void requireCourseOwnership(UUID courseId);
public void requireLessonOwnership(UUID courseId, UUID lessonId);
public void requireLessonReadAccess(UUID courseId, UUID lessonId);
```

These five public methods are the cross-module surface for the consumers in
§12.6. Retain two Course-only assertions with package-private visibility:

```java
void requireEnrolmentEligibility(UUID courseId);
void requireMaterialReadAccess(UUID courseId, UUID lessonId, UUID materialId);
```

Their identified callers, EnrollmentService and LearningMaterialService, already
share `com.codemastery.modules.course.application.service` with CourseAccessService.
No external consumer is selected for either helper; widening visibility would
require a separate concrete consumer contract. All seven purposes remain distinct:
eligibility omits membership, learning access requires it, ownership permits draft
authoring, and general reads also admit owner preview. Eligibility is only a
role/publication prerequisite, not complete CT22 enrolment-operation eligibility.
A stronger Lesson assertion already includes its Course assertion; callers select
one appropriate assertion, not both. These checks address existing resources;
Course creation and exact chapter/material write scoping remain with CT21/CT09
rather than inventing another public assertion here.

Normal void return means only that the named predicate passed for the current
actor/snapshot; denial throws as above and consumers must not swallow it. Only
java.util.UUID crosses this surface; no Course entity/domain record, repository,
Auth principal, membership record, structural projection or new boundary type/file
is exported. Course uses its existing internal repository inventory; this contract
defines capability requirements, not repository signatures or implementation.
Trusted identity/current role is obtained inside Course through Auth's facade;
no method overload permits arbitrary-user or unauthenticated background access.

### 12.6 Consumer bindings, ownership and unresolved prerequisites

| Consumer / existing location | Selected Course call and responsibility | Approval/remaining gate |
|---|---|---|
| U03 Assessment QuizService/AttemptService; AssignmentService/SubmissionService in `modules/assessment/application/service/` | Select exactly one gate: requireLearnerCourseAccess with Assessment's stored Course ID for course-level actions, or requireLearnerLessonAccess with its stored Course/Lesson IDs for a lesson-bound assignment. The latter includes Course access; do not precede it with a redundant Course assertion. Assessment owns publication, deadlines, attempt/submission lifecycle, actor ownership and scoring; UUIDs from an untrusted request alone do not select another assessment resource. | CT03 predicate proposal; CT04/CT10/CT23 retain Assessment operation approval and real binding/execution. No new Quiz→Lesson field. |
| U03 Assessment authoring at QuestionBankService/QuizService/AssignmentService | Select requireCourseOwnership for Course-only authoring, or requireLessonOwnership when a Lesson is referenced; the latter includes Course ownership. Assessment/CT40 still validate skill/tagging/question Course consistency. | No implicit CT23/CT40 approval or cross-module query. |
| U04 Interaction DiscussionService/ContentReportService in `modules/interaction/application/service/` | Proposed Course gate: requireLessonReadAccess(courseId, lessonId) before selected Lesson Q&A/report intake. Interaction resolves its own post/report target to the actual Lesson and enforces parent/reply/post visibility, authorship and moderation rules; it cannot trust a mismatched supplied lesson ID. | Course-side predicate proposed; full U04 binding remains unresolved until its Course-context input is separately approved below. Post/report REST/signatures and CT12 Admin moderation remain separate; this is not an Admin report-queue authorization rule. |
| U07 Adaptive SkillGraphService in `modules/adaptive/application/skillgraph/` | Graph authoring calls requireCourseOwnership; a lesson mapping calls requireLessonOwnership before consuming a separately approved CT40 structural read. Course owns ancestry; Adaptive owns skills, graph and mapping rules. | Access half proposed only. Full U07 remains unresolved until CT40.lesson-read selects its exact structural signature/output/location. No structural interface is approved here. |
| Course own services/CT21 authoring | CourseManagementService/CourseStructureService use ownership assertions; LearningMaterialService uses appropriate owner/read assertions. | Payloads, endpoints and writes remain CT21/CT09-owned; no new REST operation selected. |
| CT22 EnrollmentService/LessonProgressService | Use eligibility/current-membership/Lesson predicates only where the selected operation consumes them. CT22 defines repeat enrol/leave/re-enrol, progress/resume and atomic participation. | No mutation, status transition, progress formula or propagation approved by CT03. |
| Frontend/QA | FE07/FE08 consume approved REST only, never Java gates. CO03/CO04 implement selected provider capability; QA01/QA02/QA07 verify real security/PG/browser scope later. | Current anonymous catalogue stays independently usable; no Final-only consumer blocks its bounded approval. |

Dependency directions are Assessment/Interaction/Adaptive application → Course
application → Auth public application identity, with all repositories remaining
private. No reverse call to a consumer is needed. In-process gates create no HTTP
endpoint. The supplied Course UUID is asserted operation scope that Course proves,
not trusted identity or a new persisted field in Assessment/Interaction records;
the consumer's own operation contract must define its context before binding.

**U04 Course-context prerequisite:** V006 DiscussionPost supplies lesson_id, and
a report targets either its own DiscussionPost or a Lesson; neither record stores
a Course ID. Resolving Interaction's own target therefore does not establish the
courseId required by the proposed gate. A separately approved Interaction operation
must either supply an asserted Course scope that Course verifies against the
actual Lesson, or consume a separately approved Course-owned lookup that supplies
that context. Neither alternative is selected here. No new endpoint, structural
lookup/signature, database field or access to Course repositories/entities is
approved. Interaction must not fabricate context or treat a successful check on
a different Lesson as authorization. Until the context binding is approved,
U04 remains unavailable for dependent implementation/integration; the Course
predicate itself can be independently reviewed and approved.

No background/scheduler/event actor context is selected; CT04/CT08 and
the relevant lifecycle contract must define it before such callers use an access
gate. Current Course learning admission does not silently determine retention or
historical attempt/submission/result access after leaving/hiding a course.

### 12.7 Contract examples, findings and integration gates

| Case | Expected contract result |
|---|---|
| Anonymous published catalogue/detail | Existing 200 and unchanged DTOs; no identity dependency. |
| Anonymous absent versus hidden detail | Same safe 404; hidden course excluded from count/list. |
| Published Course with hidden lesson / chapter with no published lessons | Public syllabus omits that heading / chapter. |
| Protected call without effective current authentication | 401, no protected work; public catalogue unaffected. |
| Missing/locked authentication plus unsupported role and hidden/missing Course | 401 at stage 1; no Course/descendant lookup. |
| Active Learner calls ownership predicate for a hidden/missing Course | 403 at stage 2; no Course/descendant lookup. |
| Active Administrator calls any proposed predicate for a missing resource | 403 at stage 2, not 404; no implicit inheritance. |
| Learner Lesson access: LEFT/absent membership, hidden Course and missing/foreign Lesson | 404 at stage 3; membership/descendant denial does not replace concealment. |
| Learner Lesson access: LEFT/absent membership, published Course and hidden/missing/foreign Lesson | 403 at stage 4; no descendant lookup. With ACTIVE/COMPLETED membership the same descendant produces 404 at stage 5. |
| Non-owner Instructor calls general read/ownership for a hidden versus published Course, both with missing Lesson | Hidden → 404 at stage 3; published → 403 at stage 4; neither probes the Lesson. |
| Learner ACTIVE or COMPLETED, published Course/Lesson | Learner Course/Lesson access passes; advanced lesson with unknown mastery also passes. |
| Learner LEFT/absent on published Course | Learning read denied 403; eligibility may pass but creates no enrolment. |
| Learner with membership, Course or Lesson hidden | Learning read denied 404, no hidden content; history is not deleted. |
| Material read: active Learner, ACTIVE/COMPLETED membership, published Course/Lesson and hidden material with valid ancestry | 404 at stage 5; enrolment does not expose hidden material. |
| Material read: active owning Instructor previews that same hidden material under the published Course/Lesson, with valid ancestry | Assertion passes; owner preview permits hidden material. Storage delivery remains CT09-owned. |
| Material read: active Learner, ACTIVE/COMPLETED membership, published Course and published material under a hidden Lesson, with valid ancestry | 404 at stage 5; material publication does not bypass hidden-Lesson denial. |
| Owning Instructor / different Instructor | Owner may preview own hidden Course/Lesson; other Instructor gets 403 for published Course, 404 for hidden Course. Learner-only methods still deny either Instructor with 403. |
| Administrator invokes these learner/owner/read methods | 403; CT12 must separately define any administrative capability. |
| Valid accessible Course ID, Lesson belonging to another Course | 404; material under another Lesson likewise 404; no foreign identifier exposed. |
| Interaction post target differs from supplied Lesson | Interaction rejects its mismatch; Course approval cannot authorize cross-Lesson posts/reports. |
| Interaction has only its post's Lesson ID and no approved Course-context binding | U04 binding remains unresolved; no direct Course query or fabricated Course ID. |
| U07 authorization passes | No structural data returned and no CT40 interface approved; mapping still needs CT40 and valid same-Course skill rules. |
| Provider/query failure | Safe 500 and no allow/effects; callers do not reinterpret failure as missing membership or success. |

These are review oracles, not executed access tests. Use approved Course service
and integration test locations from ADR002 CT14.interim; QA01 covers the real
security chain, QA02 real PostgreSQL predicates/ancestry/hidden filtering and QA07
browser behavior. Doubles/structural fixtures and CI compilation do not prove
authorization, publication filtering, database consistency or concurrency behavior.
Do not create extra test files or routes under this documentation authority.

Remaining consumer decisions are U04's Course-context binding and U07's CT40
structural binding (§12.6); neither blocks independent predicate review/approval
or the existing anonymous catalogue. CT01.identity's bounded sign-off and retained
gates are recorded in §11.4. CONTRIBUTING's stale empty-OpenAPI statement is
reported outside this file scope; the relevant data-model status is clarified.
No evidenced defect requires superseding approved CT03.catalogue, ADR002, CT21,
CT22 or CT40. No actual unauthorized content disclosure is asserted.

Required technical reviews: Course @TamiaoNguyen; Auth @duyduyit; Assessment
@minhtan29042006; Adaptive @thanhtran0311; Interaction @tihh647-ui; Frontend
@heizseung; Leader @chi-thanh-nguyen. These are requested reviews, not completed
reviews. The three documentation paths have the Leader as sole CODEOWNER. A
Leader-authored PR cannot satisfy its own CODEOWNER approval; obtain an independent
contributor-authored/pushed replacement through the normal workflow if required,
without impersonation, ownership/rules changes or bypass. Required Backend,
Frontend, CodeQL, last-push approval and thread resolution remain in force.

On Issue #22, the catalogue criterion is satisfied by effective ADR002. The
ownership-separation criterion can reflect the existing allocation to CT21/CT22;
new learning-access, combined consumers/signatures and identity-dependent criteria
remain unchecked pending complete effective approval, including CT40's retained
structural gate. Reaffirmation is not reapproval or implementation authorization.
No Issue closure, Project/workflow/native dependency update, application change or
deployment follows. Codex authored this scoped reconciliation under delegated
selection authority; actual review/Leader sign-off and merged revision must supply
approval provenance. HTTP concealment is compatible with
[RFC9110 §15.5.4](https://www.rfc-editor.org/rfc/rfc9110.html#name-403-forbidden);
the standard does not supply project policy or approval.
