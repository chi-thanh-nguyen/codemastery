# Testing

## Purpose

This document describes CodeMastery's current testing contract, based on
[the backend build](../backend/pom.xml), [the frontend manifest](../frontend/package.json),
and [the CI workflow](../.github/workflows/ci.yml).

The reusable backend integration-test foundation and initial CI configuration
are implemented. CourseFixtures provides representation-only Course construction,
with CourseFixturesTest as its pure structural consumer. Course business/service
and PostgreSQL integration test scaffolds remain empty. SH01 has executable
shared-infrastructure unit and Spring MVC
integration tests. Frontend typechecking
and build validation exist; bounded FE01 automated regressions are implemented,
while integrated E2E remains planned.
Successful compilation or builds do not establish tested business behavior.

## Backend Test Layers

- **Unit tests:** isolated business logic and deterministic adaptive rules,
  using Mockito where appropriate. Maven Surefire executes eligible tests
  outside the integration path.
- **Integration tests:** Spring configuration, persistence, transactions, and
  approved module interactions. Maven Failsafe executes concrete tests under
  the integration path. The foundation exists; feature suites are unimplemented.
- **Future E2E tests:** Playwright will exercise main user flows through the
  frontend and backend. Files under `e2e/` remain empty scaffolds and define no
  executable E2E commands or CI job.

## JUnit / Mockito Baseline

Backend tests use JUnit Jupiter 6. Its exact version is managed by Spring Boot
4.1.1 and currently resolves to 6.0.3. Do not override the managed JUnit version
without a separately approved dependency decision. Mockito remains the backend
mocking tool where appropriate. This selected toolchain does not imply that
feature tests have been implemented.

## Unit-Test Lifecycle

Use the checked-in Maven Wrapper; no global Maven installation is required:

```bash
cd backend
./mvnw test
```

The command compiles application and test sources and executes eligible Surefire
tests. Surefire uses its standard `Test*`, `*Test`, `*Tests`, and `*TestCase`
naming patterns, with explicit exclusions for `**/integration/**` and `**/*$*`.
Place unit tests in the approved module test locations outside `integration`.
SH01 unit tests execute from the existing shared support test package. The
integration base class is compiled but is not initialized by this unit-test lifecycle.

[CourseFixturesTest](../backend/src/test/java/com/codemastery/modules/course/CourseFixturesTest.java)
is the authorized FT01 structural consumer of the accepted CO01 records and enums.
It checks caller-value and UUID-reference preservation across all six records,
full factories and builders, populated/nullable optional fields, explicit builder
primitive inputs and retained setters, material payload variants, and progress
flag combinations including `skippedProgress()` producing `(true, true)`.
Settings assertions preserve caller JSON content without specifying policy keys
or map-copying guarantees. Fixture construction remains unchecked.

Run the focused consumer from `backend/` with
`./mvnw -Dtest=CourseFixturesTest test`; use the Surefire XML to establish actual
execution and counts. Compilation alone does not prove fixture use. These local
construction assertions do not verify US-02/04/10 business acceptance, enrollment
or progress decisions, adaptive behavior, Spring contexts or PostgreSQL. Later
dataset-dependent, behavioral and integration consumers retain separate approval
and execution checkpoints.

## Integration-Test Lifecycle

[AbstractIntegrationTest](../backend/src/test/java/com/codemastery/integration/AbstractIntegrationTest.java)
provides `@SpringBootTest` with `CodeMasteryApplication`, a mock web environment
without a listening HTTP server, and `@ActiveProfiles("test")`.

It manually starts one singleton PostgreSQL Testcontainer before Spring
initializes its datasource. The approved image is `postgres:17.11-bookworm`;
this selects an integration-test database only, not the production/deployment
PostgreSQL version. No persistent container reuse is enabled. The singleton is
not managed by per-class `@Container` callbacks.

An inherited `@DynamicPropertySource` registers `spring.datasource.url`,
`spring.datasource.username`, and `spring.datasource.password` from the running
container. No fixed host port or developer-local database credentials are used.
The profile only sets `codemastery.scheduling.enabled: false`; scheduler
implementation is still pending. No JWT or storage consumer is currently
implemented, so the profile supplies no fake secrets or storage services.

The normal application Flyway settings remain enabled against this datasource,
using `classpath:db/migration`. Hibernate retains `ddl-auto: validate`, and
does not generate a test schema. V001–V006 now implement the approved initial
23-table PostgreSQL schema. Migration execution, checksum/restart validation, and
constraint checks are part of persistence/integration validation on disposable
databases. Accepted Course/Adaptive scalar entity mappings now exist, but concrete
PostgreSQL feature suites remain empty. Successful schema migration alone proves
neither an entity round trip nor feature behavior.

Concrete integration tests belong under
`backend/src/test/java/com/codemastery/integration/`, including its approved
subdirectories, and must end in `IntegrationTest.java`. Failsafe explicitly
selects `**/integration/**/*IntegrationTest.java`; conventional `*IT.java`
names are not selected. Use the shared base class when applicable.

Run broader verification from `backend/`:

```bash
./mvnw verify
```

This includes compilation, unit tests, application packaging/repackaging,
Failsafe integration-test execution and verification, and JaCoCo reporting when
execution data exists. Docker and access to the test image are required once
concrete tests initialize the Testcontainers foundation. Compose and a manually
provisioned PostgreSQL service are not required for that foundation.

To skip integration-test execution while retaining the other verification work:

```bash
./mvnw verify -DskipITs
```

This still compiles integration-test sources and runs eligible unit tests, but
skips Failsafe execution. It is not the CI validation command.

The abstract harness contains no executable tests, and its concrete feature
test scaffolds remain empty. The SH01 MVC integration test executes a real Spring
web context and dispatcher, but does not initialize this PostgreSQL harness or
prove database integration.
Runtime validation requires an actual concrete test that initializes the base.

## Test Isolation

The PostgreSQL container is shared within one test JVM. Each Maven execution
that initializes the harness starts a fresh disposable container/database;
different test JVMs do not share its static instance. Testcontainers' resource
reaper cleans it up after the JVM exits. There is no persistent reuse across
runs. This follows the documented [singleton-container lifecycle](https://java.testcontainers.org/test_framework_integration/manual_lifecycle_control/).

Reuse Spring contexts where configuration is identical. The base class does
not impose a transaction on every test. Suitable future persistence tests can
use Spring test transactions with automatic rollback. Tests that commit, use
independent transactions, perform HTTP work, or execute work in other threads
must explicitly clean up changes that rollback does not cover. No generic
truncation or schema reset infrastructure is implemented. CourseFixtures only
constructs in-memory representations; it does not load or reset a database.

## JaCoCo

JaCoCo 0.8.15 is configured to attach its agent during Maven initialization and
generate a report at `verify` when execution data is available. No coverage
threshold or coverage enforcement goal is configured. SH01 coverage reports
concern shared infrastructure; meaningful feature coverage is not established.
A successful build or report goal alone is not evidence of feature coverage.

## Frontend Validation

Use Node.js satisfying `^22.12.0 || ^24.0.0` and npm with the committed lockfile:

```bash
cd frontend
npm ci
npm test
npm run typecheck
npm run build
```

`npm ci` installs the locked dependency graph. `typecheck` checks source and
Vite configuration without emitting files. The `build` script already runs
`npm run typecheck` before `vite build`, so CI runs only install and build.
Build output goes to `frontend/dist/`. Development-only Vitest `5.0.5` and jsdom
`27.4.0` execute the bounded frontend suite through `npm test` (`vitest run`).
No lint script is implemented. The required Frontend CI job still runs only
installation and build; it does not run `npm test` or the browser harness.
Build success alone does not establish behavioral coverage or deployment readiness.

### Bounded FE01 decisions and regression coverage

**Authority and timing.** Project Leader/Owner @chi-thanh-nguyen explicitly
delegated D1–D4 in “CodeMastery — Authorized FE01 Remediation and Professional
Dashboard UI/UX Implementation”, supplied on 2026-10-23 (Asia/Ho_Chi_Minh;
source SHA-256 `8a24e52a354dfaa333f48f8c614b3a8a3d31661f19a6f627a1a921e079b5ee5b`).
The implementing agent selected the following bounded decisions under that
delegation. [PR #99](https://github.com/chi-thanh-nguyen/codemastery/pull/99)
delivered them at merge `89998c6e0be1764ed5ec8fcf101124a369a984f2` on
2026-10-24 at 00:06:34 ICT. The Leader's subsequent instruction “CodeMastery —
Post-Merge FE01 Documentation, Governance and Completion Review”, supplied on
2026-10-24, authorizes this documentation reconciliation. This is a post-merge
correction of synchronization debt, not retrospective satisfaction of the
same-logical-change documentation requirement or retrospective approval of PR #74.
Independent CODEOWNER review and protected integration of this correction remain
required. This existing-document record adds no ADR and leaves ADR001/ADR002,
unrelated CT14 proposals and the remaining CT37 family unchanged.

**D1 — Caller-owned formatting.** [format.ts](../frontend/src/shared/utils/format.ts)
retains the helper names and configuration objects, with explicit supported locale,
display timezone and date/time presentation. Dates accept a valid `Date`, epoch
milliseconds, or a complete four-digit-year ISO timestamp with seconds, optional
one-to-three fractional digits, and `Z` or a numeric offset. Nullish, invalid,
date-only and timezone-less inputs return empty output. Valid date inputs require
valid caller configuration; native Intl validation remains effective. Number
formatting requires a style, rounding mode and exactly one complete minimum/maximum
fraction-digit or significant-digit pair, plus currency/unit identifiers where
applicable. Missing/invalid policy is rejected rather than defaulted; runtime
support for the requested rounding mode is checked. Nullish/NaN numbers return
empty output; Infinity retains native Intl display. Locale-derived conventions
remain caller-selected. This avoids host-timezone interpretation and implicit
precision without demanding irrelevant Intl options. The showcase alone selects
`en-US`, `UTC` and its numeric presentation; these are not platform policies.

**D2 — Finite duration parts.** Support nonnegative finite seconds whose floored
value is a safe integer. Floor fractions, normalize negative zero, and preserve
the `{ hours, minutes, seconds }` shape. Nullish, NaN, infinite, negative or unsafe
values return zero parts. This preserves valid behavior and the existing fallback;
it intentionally does not distinguish invalid input from a valid zero.

**D3 — Bounded verification.** Vitest/jsdom with real React DOM, `act` and controlled
timers avoids a second renderer and keeps production dependencies unchanged. These
versions retain the manifest's `^22.12.0 || ^24.0.0` Node range. The exact additions,
also recorded in [the inventory](repository-structure.md#bounded-fe01-inventory-additions),
are:

| File under `frontend/` | Coverage |
| --- | --- |
| `src/shared/utils/format.test.ts` | Explicit locale/timezone/precision/rounding, invalid dates, duration boundaries and reconstruction. |
| `src/shared/hooks/useDebounce.test.tsx` | Function identity, Strict Mode, timing/rescheduling, rapid updates and unmount cleanup. |
| `src/shared/components/presentation.test.tsx` | Controlled navigation/current state, zero badges, named loaders, status content, callbacks and confirmation semantics. |
| `src/shared/components/presentation.browser.cjs` | Optional Chromium checks, screenshots, accessibility tree, computed contrast, focus, dialogs, drawers, overflow and reduced motion. |

To reproduce host-timezone checks from `frontend/`, run
`TZ=UTC npm test -- src/shared/utils/format.test.ts` and
`TZ=America/Los_Angeles npm test -- src/shared/utils/format.test.ts`.
The browser script requires a separately available compatible Playwright Node
module and its installed Chromium browser. Neither `npm ci` nor this harness
installs Playwright or downloads browsers. The empty `e2e/` package/configuration
defines no installation or execution command. Build and start `npm run preview`
(or use `npm run dev`); in another terminal, from `frontend/`, substitute actual
paths and the URL printed by Vite:

```bash
npm run test:browser -- "<installed-playwright-module>" "<evidence-directory>" "<preview-url>"
```

Supply the URL explicitly: the harness fallback `http://127.0.0.1:5173/` need not
match Vite's chosen port. It writes screenshots, `browser-report.json` and
`studio-accessibility-tree.yml` to the supplied evidence directory. Retain those
outside tracked source together with the exact revision, environment, commands
and outcomes. The harness covers desktop 1440×900, tablet 1024×768, mobile
390×844/320px and relevant breakpoint boundaries. DOM semantics, Chromium
accessibility output and keyboard checks do not establish actual screen-reader
announcements, full WCAG certification, backend integration or Final E2E delivery.
Report unavailable checks explicitly; successful build/CI is not a substitute.

**D4 — Scope and retained gates.** The same delegation authorized all seven FE74
corrections, necessary frontend test/dependency/file extensions and a separate
presentation-only dashboard redesign. [Setup](setup.md#frontend-setup-and-validation)
records its design foundation, attribution and known AI assistance. It authorizes
no router, role menus, Auth/session behavior, HTTP client, backend/API changes or
new business workflows. Caller-facing signatures remain local implementation
artifacts. Focused independent review, applicable consumer compatibility review
before binding, documentation integration and both FE01 checkpoint/Issue #37
acceptance remain distinct from implementation merge and test success. Broader
CT14/CT37 approval and frontend/backend integration are not implied.

## Initial CI

The `CI` GitHub Actions workflow runs on pull requests targeting `main`, pushes
to `main`, and manual `workflow_dispatch`. It uses `contents: read` permissions
and two independent jobs on `ubuntu-24.04`:

| Job ID / display name | Setup | Commands and working directory |
| --- | --- | --- |
| `backend` / `Backend` | `actions/checkout@v7`, `actions/setup-java@v6`, Temurin 25 | `./mvnw verify` from `backend/` |
| `frontend` / `Frontend` | `actions/checkout@v7`, `actions/setup-node@v7`, Node 22 | `npm ci`, then `npm run build` from `frontend/` |

The Java setup enables Maven caching using `backend/pom.xml` and
`backend/.mvn/wrapper/maven-wrapper.properties` as dependency inputs. The Node
setup enables npm caching keyed by `frontend/package-lock.json`. Caches do not
replace installation or validation. The backend runs directly on the hosted
runner, whose Docker support is needed when concrete Testcontainers tests exist.

CI currently validates build foundations and prepares the future test lifecycle;
it does not validate backend business behavior or exercise PostgreSQL through
the abstract harness alone. There is no E2E job, Compose invocation, deployment,
Docker image build, or artifact upload. No remote branch-protection settings or
required status-check names are defined here. Workflow configuration does not
prove a successful remote run; actual GitHub Actions results must be reported
from executed runs.

## Planned Testing

The following remain planned, with no test-count or coverage-percentage claim:

- Feature unit tests, including deterministic adaptive-learning cases.
- Feature integration tests for persistence, transactions, configuration, and
  approved module interactions.
- Frontend automated tests if a framework is later approved.
- Playwright E2E flows and fixtures.
- E2E CI.

Coverage threshold enforcement is not configured; any future enforcement
requires an approved decision.

## Authorized Interim verification contracts

[ADR002](decisions/002-interim-delegated-contracts.md) remains authoritative for
`CT14.interim`, `CT36.I-results` and `NC-EVIDENCE-MODE`. The detailed placement,
isolation and evidence proposal below extends those bounded decisions without
redefining their behavior or claiming execution. Test authoring, actual execution,
independent verification and external deployment remain separate obligations.

## SH01 delegated verification organization

The current SH01 implementation instruction delegates this local test placement
within existing directories. It does not amend CT14 for other contributors or add
a framework, dependency, test runner, coverage threshold or production endpoint.

| Existing directory | New SH01 test | Verification boundary |
| --- | --- | --- |
| support | PageResponseTest | Defaults, invalid bounds, immutable content and total-page arithmetic |
| support | AppConfigTest | Consumed settings, lazy optional bindings, YAML defaults and Boot datasource initialization validation |
| support | CorrelationIdFilterTest | Untrusted headers, server IDs, dispatch propagation, failure cleanup and MDC restoration |
| integration | SharedRestInfrastructureIntegrationTest | MVC dispatch, validation, ProblemDetail JSON, statuses/headers and test-only page endpoints |

Run `./mvnw test` and `./mvnw verify` from `backend/`. Surefire executes the three
shared tests; Failsafe executes the MVC integration test. These tests need no
Docker, real database or runtime credentials. Test JWT keys are generated
ephemerally. The MVC fixture creates no feature APIs and intentionally includes
no Auth SecurityFilterChain. Its security-exception check only establishes that
MVC advice leaves Auth ownership intact; it does not verify authentication.

Configuration tests include both accepted and deliberately rejected contexts.
Expected context-initialization warnings are negative-test evidence, not successful
application startup. Check Surefire/Failsafe XML for exact counts and outcomes.
No PostgreSQL feature integration, browser flow, container feature runtime,
production deployment or remote CI result is implied. Focused review and the
applicable downstream QA checkpoints remain necessary.

## Approved AU03 persistence regression-test placement

**Leader technical approval, 2026-10-22 (Asia/Ho_Chi_Minh); protected integration
pending.** In the instruction “CodeMastery — AU03 B2: Approve and Record Bounded
Test Placement”, Project Leader and repository Owner @chi-thanh-nguyen explicitly
approved exactly one additional test filename, subject to consistency verification,
and delegated that verification and this two-document record to Codex. Verification
against `main` at `37c33a4056975f42c65a6704ec69c272d4a1f6e1` found the placement
consistent with the approved architecture, Auth ownership, existing test stack and
Maven lifecycle. This records the Leader's approval, not an independent GitHub
Approve review or completed protected integration.

The exact approved addition is
`backend/src/test/java/com/codemastery/modules/auth/UserPersistenceTest.java`.
It is a bounded exception to the finalized file inventory, recorded in
[the repository structure](repository-structure.md#bounded-auth-test-inventory-addition).
The initial placement-only record did not implement the file. Its only
responsibilities are unit tests of `UserMapper` and mocked-adapter tests of
`UserRepositoryImpl` for the effective `AU03.mapping` and `AU03.operations` checkpoints in
[AU03 #40](https://github.com/chi-thanh-nguyen/codemastery/issues/40), addressing
the B2 regression-coverage finding for
[PR #94](https://github.com/chi-thanh-nguyen/codemastery/pull/94).

Permitted regression cases are:

- Bidirectional preservation of all six User fields: UUID, canonical email,
  encoded password hash, display name, role and account status; valid round trips.
- Explicit stored-code conversion for roles `learner`, `instructor`, `admin`
  and statuses `active`, `locked`, including invalid and null stored-code rejection.
- Present and absent `findById`/`findByEmail` results, exact lookup delegation,
  `save` delegation and mapping of the entity returned by persistence.
- Relevant failure propagation without invented normalization, hashing,
  account-state policy or repository-level HTTP error translation.

Use the existing Boot-managed JUnit Jupiter 6 and Mockito infrastructure in the
existing Auth test package. The `*Test.java` filename follows the current Surefire
selection outside `integration`; no new framework, dependency, directory,
configuration, production change, coverage threshold or unrelated testing policy
is approved. The decision adds a local regression location to ADR002's effective
`CT14.interim` baseline. It resolves only this bounded placement under
[CT14 #27](https://github.com/chi-thanh-nguyen/codemastery/issues/27), not the
remaining `CT14.coverage` proposals below. PR #87's merge does not activate
clauses still marked `PROPOSED_NOT_EFFECTIVE`. Existing valid decisions,
historical evidence and unrelated pending gates retain their dispositions.

Unit conversion and mocked persistence assertions do not verify Spring Data
query execution, transactions, PostgreSQL round trips or physical constraints,
authentication, authorization or browser integration. `AU03.database-verification`
remains an outstanding integration gate under AU03's Definition of Done; this
placement neither executes nor waives it and adds no standalone integration-test
filename. The initial placement-only approval required separate explicit
implementation/file-scope authorization before authoring this test, followed by
actual validation and review. The placement approval alone does not resolve B2,
complete AU03, establish PR #94 approval
readiness or retrospectively authorize earlier AU03 implementation.

**Prospective B2 implementation authorization, 2026-10-22 (Asia/Ho_Chi_Minh).**
In “CodeMastery — PR #94: Implement Approved B2 Remediation Directly”, Leader/Owner
@chi-thanh-nguyen reaffirmed this single-file placement and expressly authorized
its focused tests, these two documentation records, and the PR #94 description
update directly on the existing PR branch. This supplies the separate authorization
required by the initial placement-only record above. Auth ownership remains
@duyduyit and @chi-thanh-nguyen under the existing CODEOWNERS rule; documentation
ownership remains @chi-thanh-nguyen. The test uses a real `UserMapper` and mocked
`UserJpaRepository`, with separate mapper and adapter groups and no Spring context
or PostgreSQL. Record actual commands, results and coverage evidence in the PR
handoff. Protected review/integration and fresh independent assessment remain
required; this prospective grant does not establish the original implementation's
authorization provenance. All unrelated pending CT14 proposals and the outstanding
`AU03.database-verification` gate remain unchanged.

## Approved AU04 password-validation regression-test placement

**Explicit Leader technical approval and prospective implementation authorization,
2026-10-22 (Asia/Ho_Chi_Minh).** In “CodeMastery — PR #95: Direct B2
Regression-Test Remediation”, Project Leader and repository Owner
@chi-thanh-nguyen approved exactly
`backend/src/test/java/com/codemastery/modules/auth/StrongPasswordTest.java`
and authorized its implementation and these two documentation updates directly
on PR #95's existing `feature/password-validation` branch. This instruction is
the approval provenance; it does not retrospectively establish authorization for
the original AU04 production implementation or waive protected review/integration.
Auth test ownership remains @duyduyit and @chi-thanh-nguyen under CODEOWNERS;
these documentation files remain Leader-owned.

This is a bounded AU04 extension of ADR002's effective `CT14.interim` placement
for [AU04 #51](https://github.com/chi-thanh-nguyen/codemastery/issues/51), recorded
as [one structural exception](repository-structure.md#bounded-au04-test-inventory-addition).
`AuthServiceTest.java` retains application-orchestration responsibilities;
the independently executable constraint needs regression protection before AU06
is implemented. The new file covers only:

- The effective `CT01.core` predicate: minimum 15 Unicode code points, maximum
  72 UTF-8 bytes, without trimming, normalization or character-class rules.
- Independent Unicode/byte boundary oracles, whitespace, null and compatible
  `CharSequence` inputs; malformed/unpaired surrogate policy remains unspecified.
- Actual Jakarta Bean Validation discovery and `@NotNull @StrongPassword`
  composition, including null acceptance by the standalone constraint and safe
  default violation messages, using fixtures local to this test file.

Use existing Boot-managed JUnit Jupiter 6 parameterized tests and the existing
Bean Validation provider, without a Spring context, PostgreSQL, new dependency
or configuration. Surefire selects this `*Test.java` outside `integration`.
Run `./mvnw test` and `./mvnw verify` from `backend/`; report actual Surefire,
Failsafe and JaCoCo evidence separately from downstream integration.

This approval resolves only AU04's dedicated placement and prospective B2 work.
It does not approve unrelated `CT14.coverage` or `PROPOSED_NOT_EFFECTIVE` clauses,
change ADR001/ADR002, or authorize other files or production functionality.
Existing AU03 decisions and all other pending proposals retain their dispositions.
AU06 #53 retains registration DTO requiredness, service/API validation and
consumer handoff verification; QA01 #61 retains real BCrypt/security/repository
integration acceptance. These local tests neither execute nor waive those gates,
complete AU04 acceptance, nor establish PR approval readiness. The PR description
remains Leader-maintained and is not changed by this remediation.

## CT14 coverage and isolation reconciliation — proposal

### Authority and historical reconciliation

**New selections: PROPOSED_NOT_EFFECTIVE.** On 2026-10-18 (Asia/Ho_Chi_Minh),
the Leader delegated CT14 investigation and technical selection to Codex, with
edits limited to this file and [setup](setup.md). This is proposal provenance,
not a separate manual Leader review, implementation authorization or test result.
Revalidated on 2026-10-19 against the same live `main` revision and PR #86 head.
New clauses require affected-owner review, explicit Leader approval and protected
integration; effective bounded approvals below remain unchanged.

Inspected baseline: `main` at `754b6511e85bc8a70ef6a4b8fb7b1210c6cbd6b4`;
[CT14 #27](https://github.com/chi-thanh-nguyen/codemastery/issues/27), actual
Maven/test/profile/CI files, approved requirements §6/§8.3, architecture testing
strategy and repository inventory. PRs below link to their actual historical
changes; merge, technical authority and independently verified behavior are
separate facts.

| Decision and source | Current evidence, authority and limit | Classification; disposition |
|---|---|---|
| Foundation [PR #2](https://github.com/chi-thanh-nguyen/codemastery/pull/2), [#4](https://github.com/chi-thanh-nguyen/codemastery/pull/4), [#5](https://github.com/chi-thanh-nguyen/codemastery/pull/5); AGENTS testing-baseline supersession; current `pom.xml` | Boot 4.1.1 dependency management resolves Jupiter 6.0.3, Mockito 5.23.0, Surefire/Failsafe 3.5.6 and Testcontainers 2.0.5; Wrapper/Surefire/Failsafe/JaCoCo and singleton PostgreSQL harness remain suitable. JUnit 5 in the canonical architecture is explicitly superseded only for that tool version. | APPROVED_AND_CONSISTENT; REAFFIRM |
| [PR #73](https://github.com/chi-thanh-nguyen/codemastery/pull/73), merge `729ba60`; ADR002 `CT14.interim`, `CT20.I`, `NC-EVIDENCE-MODE` | Approved bounded placements, two exact Course test-file additions, backend automation plus independent manual browser protocol. No execution or Final E2E waiver. | APPROVED_AND_CONSISTENT; REUSE |
| ADR002 `CT06.prototype`, `CT24.calculation`, `CT36.I-results`; [PR #79](https://github.com/chi-thanh-nguyen/codemastery/pull/79), merge `1e43194` | Pure calculation/signatures/oracles and accepted categorical AD03 representations. AD03 declarations are neither calculation nor database round-trip evidence. | APPROVED_AND_CONSISTENT within bounded scope; REUSE |
| [PR #78](https://github.com/chi-thanh-nguyen/codemastery/pull/78), merge `166484e`; SH01 handoff above | Concrete shared tests exercise local configuration/page/correlation/MVC behavior; MVC fixture has no Auth chain or PostgreSQL. Wider independent SH01 acceptance remains separate. | APPROVED_BUT_INCOMPLETE; REUSE bounded primitives, retain verification gates |
| [PR #82](https://github.com/chi-thanh-nguyen/codemastery/pull/82), merge `39748f6`; ADR001 §11.4 and explicit 18 October Leader identity sign-off | Read-only Auth identity boundary is approved; security-chain implementation and logout residual-risk acceptance do not follow. | APPROVED_AND_CONSISTENT for identity only; REUSE |
| [PR #83](https://github.com/chi-thanh-nguyen/codemastery/pull/83), merge `8906c59`; [#84](https://github.com/chi-thanh-nguyen/codemastery/pull/84), merge `754b651`; [#85](https://github.com/chi-thanh-nguyen/codemastery/pull/85), merge `35cf5ae` | Integrated CT03/CT16/CT17 documentation retains explicit sign-off/dependency gates. Recorded PR approval and merge do not independently discharge reserved contract conditions. Test against each effective consumed clause; proposed clauses permit preparation only. | PROPOSED_NOT_EFFECTIVE for still-reserved selections; DEFER those acceptance claims |
| [AD09 PR #86](https://github.com/chi-thanh-nguyen/codemastery/pull/86), head `d51930b` | OPEN at inspection; four-file calculator/classifier and two-test proposal. Main's corresponding four files are empty. Its green checks are head-specific execution evidence, not integration or independent oracle acceptance. Actual diff differs from CT24 (Snapshot/Calculation/Thresholds/Rational rather than approved records, no isUnknown, nulls throw NullPointerException, public output bypasses validation and thresholds use long operands). Preserve CT24; correct implementation under its own authorization. | PROPOSED_NOT_EFFECTIVE as a main artifact; CLARIFY nonconformance, no contract supersession |
| CT14 remaining coverage/isolation detail below; CT34/CT35/CT39 and remaining CT36 | Previously undefined detail is selected here; runner, selected E2E journeys, optional clock behavior and Final evaluation oracles retain their owning approvals. | UNRESOLVED before this proposal; EXTEND CT14 only, DEFER other contracts |

No harness replacement or effective-contract supersession is justified. Existing
code is evidence of implementation scope, not independent approval authority.
Empty scaffolds are missing coverage, not executed failures or proof of success.

### CT14.coverage — placement, ownership and prerequisites

Paths below are relative to `backend/src/test/java/com/codemastery/`. All named
existing Java paths were inventoried; the two **approved additions** are absent on
this baseline and were selected by ADR002 B5, not newly authorized here. Creating
or editing any test still needs its separate implementation/file authorization.

| Capability and owner | Exact approved location / layer | Oracle, fixtures, prerequisites and independent handoff |
|---|---|---|
| Auth orchestration/password; Auth | `modules/auth/AuthServiceTest.java`; unit | CT01.core canonical email, learner-only registration, unchanged password input, Unicode/code-point and UTF-8 byte bounds, real BCrypt verification and safe failures. Mockito can isolate repositories, but cannot prove uniqueness races or persistence. Generate synthetic test secrets/password inputs locally; QA01 checks actual encoded storage and no disclosure. Internal password helper is initially covered through Auth behavior; no extra utility test file selected. |
| JWT; Auth | `modules/auth/JwtTokenProviderTest.java`; unit | Effective CT01/CT38 fixed algorithm/issuer/audience/claims/expiry and malformed/missing/invalid inputs, including exact expiry boundary. Use explicit instants/inputs through approved callable shapes; do not add a clock API or weaken validation to make tests pass. Current-account lock/role enforcement additionally needs actual Auth data/security integration. |
| Backend security and registration persistence; QA01/Auth | `integration/AuthorizationIntegrationTest.java`; integration | Real configured Auth security components and PostgreSQL, approved selected endpoints and role/resource rules, unauthenticated 401 versus role 403 versus concealed 404, locked or removed account, registration canonical-email conflict including concurrent inserts. Check CT18 safe problems and 401 headers. Prove no denied side effect. Mocked identity/security alone cannot establish this handoff. |
| Catalogue service; Course | `modules/course/CourseServiceTest.java`; unit, **approved addition** | Effective CT03.catalogue/CT21.catalogue DTO/privacy and syllabus mapping/order, missing/hidden detail equivalence and validated pagination. Isolated repository doubles establish orchestration only; no Auth dependency is required for anonymous catalogue. |
| Catalogue REST/query composition; Course/QA02 | `integration/CourseCatalogueIntegrationTest.java`; integration, **approved addition** | Real PostgreSQL publication filtering before pagination/count, stable title/UUID order and canonical syllabus order, empty/beyond-last page, invalid/default/boundary sizes, unsupported parameters, safe 404 and no hidden fields/content/storage/settings. Exercise actual Course API/advice for REST claims; a repository-only test cannot prove serialization. |
| Course mappings/constraints; QA02/Course | `integration/repository/CourseRepositoryIntegrationTest.java`; integration | V002/accepted scalar mappings, real FK/uniqueness/nullability and approved query predicates. Flush/clear or otherwise force database evaluation before claiming a round trip/constraint; setup and assertions must not merely return cached/in-memory representations. Coordinate catalogue assertions with the preceding row without duplicating identical tests. |
| Local mastery; AD09 with independent CT36/QA14 reviewer | `modules/adaptive/mastery/MasteryCalculatorTest.java`, `modules/adaptive/mastery/MasteryStateClassifierTest.java`; unit | Only effective CT06.prototype/CT24.calculation and exact CT36.I-results table, including unknown versus zero successes, equality, alternate rational policy, invalid/null inputs and maximum-int multiplication. Pure in-memory evidence; no Spring, DB, clock, events, grading or production aggregation prerequisite. Reviewer independently checks arithmetic before accepting results; CT14 creates no new threshold/oracle. |
| Shared infrastructure; SH01 | `support/PageResponseTest.java`, `support/AppConfigTest.java`, `support/CorrelationIdFilterTest.java`; local tests; `integration/SharedRestInfrastructureIntegrationTest.java`; MVC integration | Preserve current delegated tests and their limited boundary above. Real feature security/PG assertions belong to owning integration suites, not invented shared endpoints. Existing `CourseFixturesTest.java` in `modules/course/` establishes structural fixture behavior only. |
| Browser Auth/catalogue and UI; Frontend/QA07 | Existing Auth/catalogue clients/pages, read-only browser protocol; no new test file | NC-EVIDENCE-MODE requires actual browser → REST → PostgreSQL plus redacted query evidence and hidden-course contrast, memory-only session/reload/401/403 behavior under effective policies, safe English validation/errors and responsive presentation. Actual FE03/FE07, AU06/CO05 and consumed configuration are execution prerequisites; doubles allow preparation only. Record localhost integration separately from DP03 external access. |
| Final/selected Stretch; owning modules and QA | Existing files listed below; unit/integration and conditional E2E | Map required behavior to these inventories now; behavior, fixtures and expected outcomes must first be approved by their owning contracts. Unimplemented and unselected cases remain explicit coverage gaps; they do not block independently approved Interim units. |

The remaining approved inventory is preserved, without selecting new behavior:

- Course enrolment/progress: `modules/course/EnrollmentServiceTest.java` and
  `modules/course/LessonProgressServiceTest.java`; CT22 owns mutation/resume semantics.
- Assessment: `modules/assessment/AttemptServiceTest.java`,
  `modules/assessment/QuizGradingServiceTest.java`,
  `modules/assessment/SubmissionServiceTest.java`; consumed lifecycle,
  exact-mask grading and manual-submission policies precede assertions.
- Adaptive graph/settings/routing/listener/scenarios:
  `modules/adaptive/skillgraph/SkillGraphTest.java`,
  `modules/adaptive/mastery/AdaptiveSettingsServiceTest.java`,
  `modules/adaptive/mastery/MasteryUpdateServiceTest.java`,
  `modules/adaptive/mastery/PlacementDiagnosticServiceTest.java`,
  `modules/adaptive/decision/AdaptiveDecisionEngineTest.java`,
  `modules/adaptive/decision/NextLessonResolverTest.java`,
  `modules/adaptive/decision/PracticeDifficultySelectorTest.java`,
  `modules/adaptive/listener/QuizSubmittedEventListenerTest.java`, and
  `modules/adaptive/evaluation/AdaptiveProfileScenarioTest.java`.
  CT05/CT06/CT07/CT08/CT24/CT36/CT40 supply their consumed policies and interfaces.
- Integrated Adaptive/atomic progress: `integration/AdaptiveLearningFlowIntegrationTest.java`
  and `integration/MasteryProgressTransactionIntegrationTest.java`; QA03/QA04 verify actual
  cross-module effects and commit/rollback, with CT08/CT22 authorization.
- Interaction/Admin: `modules/interaction/DeadlineReminderSchedulerTest.java`,
  `modules/interaction/NotificationEventListenerTest.java`,
  `modules/admin/UserManagementServiceTest.java`;
  feature policy, time/event inputs and approved access rules precede execution.
- Playwright locations remain `e2e/playwright.config.ts`,
  `e2e/fixtures/auth-fixture.ts`, `e2e/tests/learner-flow.spec.ts`, and
  `e2e/tests/adaptive-flow.spec.ts`, relative to the repository root. CT34 owns runner/fixtures;
  CT19/CT35 own selected cases, FE24–FE26 their implementation and QA08 actual
  execution. Existing empty package/config files supply no command. Frontend
  automated unit/component-test placement and framework outside the
  [bounded FE01 grant](#bounded-fe01-decisions-and-regression-coverage) remain
  unapproved by this proposal.

### Isolation and feature configuration selections

Retain the existing singleton and profile unchanged. Prefer per-test Spring
rollback for synchronous persistence work that actually joins the test-managed
transaction; this is simpler than global truncation or a fresh container per
class. Force the relevant flush/commit boundary when the assertion depends on
constraints or commit behavior. Do not claim rollback of a test transaction
proved a successful commit, an asynchronous effect or a production transaction.

For real HTTP, separate transactions, concurrent inserts or asynchronous work,
the test owner must explicitly track created IDs, wait for work to finish, and
clean up owned records in fresh committed transactions in FK-safe order, including
failure paths. Do not leave late work mutating the shared database after cleanup.
If deterministic cleanup cannot be demonstrated, stop that suite until separately
authorized isolation is available; do not truncate shared tables, disable FKs or
reset Flyway. Thread-bound transactions do not roll back work on other threads;
avoid preemptive timeouts that move such tests outside their transaction.

Tests must arrange their own synthetic inputs and not require test-class order,
pre-existing developer data or another test's success. Keep the current execution
configuration; database-mutating tests sharing the singleton must not be made
parallel without an explicit isolation decision. A fresh JVM has a fresh
container, not a fresh database per test. Context caching is not database cleanup;
`@DirtiesContext` neither replaces cleanup nor owns container shutdown.

Reuse `CourseFixtures` for explicit representations; it supplies neither valid
business defaults nor database loading/reset. Use ADR002 CT15.I-data only when
that selected versioned dataset is actually consumed and reviewed; never run its
loader implicitly in every test or write seed data into migrations. Pure mastery
counts/policy require no DB dataset. Final scenario JSON remains governed by
CT15/CT36, not an assumed approved oracle merely because a filename exists.

Keep `application-test.yml` scheduler suppression and dynamic datasource values.
Feature tests that actually consume JWT settings generate a fresh 32-byte key,
encode it as 64 hex characters and apply effective CT01/CT38 validation; malformed
config cases use synthetic rejected values without leaking them. Do not globally
require JWT/storage configuration for unused features or replace real security
with permissive stubs in claimed authorization tests. No storage configuration,
MinIO testcontainer, new credentials policy or feature mock is approved here:
unit doubles must be labelled, and real storage evidence requires CT09 policy,
existing abstraction and separately approved test configuration/placement. That
storage-specific gate does not block independent Auth/catalogue/mastery tests.
Listening-server HTTP configuration is also a separately authorized test-specific
choice; the unchanged shared base uses MOCK and does not start a server.

### Evidence, alternatives and consumer gates

Select layer-specific evidence instead of a single large mocked suite: mocks
cannot prove PostgreSQL constraints, MVC dispatch cannot prove browser networking,
and browser screenshots cannot prove every server denial. Preserve manual
independent Interim browser verification instead of introducing a runner now;
Playwright and mandatory automated Final E2E remain obligations. Retain exact
existing test locations rather than duplicate suites or extra frameworks. No
coverage percentage, fixed timeout or new script is selected.

| Relationship | Exact consumed prerequisite and retained owner |
|---|---|
| CT06 ↔ CT14 | CT14 places and isolates tests; CT06 owns mastery/enablement policy. The parallel CT06 proposal is not an effective oracle. Production/settings tests wait for their specific approved policy and CT40 boundary, not closure of prototype testing. |
| CT24 ↔ CT36 ↔ AD09 | CT24 owns callable types; CT36 owns independently reviewed oracles; AD09 owns its two tests. QA14 reproduces actual reviewed results and limitations rather than authoring duplicate tests. |
| CT40 / CT07 / CT08 / CT22 | Structural lookup, routing and events/atomic mutation remain their owners' decisions. Test through approved producer/consumer boundaries; do not authorize internal cross-module imports for convenience. |
| QA01 / QA02 / QA07 | Each owns its actual security, persistence or browser verification. CT14 coverage approval is a plan, not verification or authorization to edit their files. |
| CT20.I / NC-EVIDENCE-MODE / DP03 | Independent local arithmetic, actual Auth/catalogue integration and independent external access are separate evidence items. Local Compose health, compilation and CI alone satisfy none of the missing feature claims. |
| CT34 / CT19 / CT35 / CT39 | Runner/fixtures, selected E2E cases and optional clock behavior stay deferred to their owners. This proposal adds no dependency or interface to resolve them. |

Each reported case must identify effective contract/oracle revision, implementation
commit, test name or manual step, exact command, runtime/browser/OS and applicable
DB/image/configuration, synthetic input provenance, expected and observed result,
execution time/tester, adverse cases and failures. Retain redacted Surefire/Failsafe
XML and relevant logs; never attach credentials, bearer tokens, unredacted account
records or full secret-bearing configuration. Independent CT36 arithmetic review
and reproduction require someone other than the implementation/oracle author,
with reviewer identity/time recorded. Reports label unexecuted, blocked, failed,
passed-local and independently verified separately; no result is claimed here.

Require Leader/shared-harness, Auth, Course, Adaptive and Frontend/QA review for
consumed placements and evidence; Assessment/Interaction/Admin owners review
applicable Final handoffs. Existing CODEOWNERS and required checks/last-push review
remain in force. Where PR author is the sole documentation CODEOWNER, use a
compliant independent contributor-authored/pushed review path; no bypass is
approved. This documentation proposal changes no code, test, dependency, migration,
runner, configuration, test result or academic evaluation evidence.

Framework references: [Spring test-managed transactions](https://docs.spring.io/spring-framework/reference/testing/testcontext-framework/tx.html)
and [Testcontainers singleton lifecycle](https://java.testcontainers.org/test_framework_integration/manual_lifecycle_control/).
