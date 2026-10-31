# Testing

## Purpose

This document describes CodeMastery's current testing contract, based on
[the backend build](../backend/pom.xml), [the frontend manifest](../frontend/package.json),
and [the CI workflow](../.github/workflows/ci.yml).

The sections from [Test Layers and Evidence](#test-layers-and-evidence) through
[Remaining Gaps](#remaining-gaps) describe the current repository. The later dated
sections, from [Authorized Interim verification contracts](#authorized-interim-verification-contracts)
onward, are decision and execution records. They keep the wording of their date.

Backend unit tests, Testcontainers PostgreSQL integration tests and frontend
Vitest/jsdom tests exist for the implemented Auth, Course catalogue and
membership, sample-data and shared REST slices, and run in CI. Integrated
Playwright E2E is not set up. A passing test establishes only what that test
exercises. It is not acceptance of a verification Issue: QA01 #61, QA02 #62,
QA07 #63, QA13 #64, QA14 #65 and DP02 #66 remain open, and their independent
verification is separate work.

## Test Layers and Evidence

| Layer | Tool and location | Runs in CI | What a pass establishes | What it does not establish |
| --- | --- | --- | --- | --- |
| Backend unit | JUnit Jupiter 6, Mockito; Maven Surefire; module test packages outside `integration/` | Yes (`Backend`) | Isolated service, mapper, adapter, validation, JWT, settings and pure mastery logic | Real PostgreSQL behavior, Spring Security wiring or HTTP serialization |
| Backend integration | Maven Failsafe; `backend/src/test/java/com/codemastery/integration/`; PostgreSQL 17 Testcontainer through `AbstractIntegrationTest` | Yes (`Backend`) | Real Flyway schema, persistence, transactions and concurrency. Auth, Enrollment and sample-data suites dispatch through MockMvc with the configured Spring Security chain; the other suites do not (see [Integration-Test Lifecycle](#integration-test-lifecycle)) | A running server, real browser, network/CORS from a browser, deployment |
| Frontend unit/component | Vitest 5 with jsdom and real React DOM; `frontend/src/**/*.test.ts(x)` | Yes (`Frontend`) | Client, provider, router, page and presentation behavior against controlled responses | A real backend, browser layout or assistive-technology output |
| Optional browser harness | `npm run test:browser` (`presentation.browser.cjs`) with an externally supplied Playwright module | No | Chromium layout, focus, contrast and accessibility-tree checks of presentation components | Live API integration |
| Standalone browser script | `e2e/tests/learner-flow.spec.ts`, run with an externally supplied Playwright module against a running frontend and backend ([record](#pr-101-full-fe07-verification--27-october-2026)) | No | When executed and recorded: the FE07 browser → REST → PostgreSQL learner flow on that environment | Repository E2E setup: `e2e/package.json` and `playwright.config.ts` are empty and no E2E job exists |
| VM deployment tests | Six `VM …` CI jobs exercising `deploy.sh`, the VM override, the frontend `release` image and the MinIO initializer image against real containers on the runner | Yes | Script guards, runtime privilege matrix, image identity, HTTPS ingress behavior and MinIO initializer provisioning against disposable MinIO servers on the runner | Any deployment, the real VM or outside-network access |
| Independent verification | QA01/QA02/QA07/QA13/QA14, DP02 and DP03 procedures by their owners | No | Acceptance on the named environment, when recorded | — |

### Backend integration suites

| Suite | Added by | What it exercises |
| --- | --- | --- |
| `AuthorizationIntegrationTest` | QA01 tests, PR #117 | Registration persists a canonical-email Learner with a verifiable `$2b$` cost-14 BCrypt hash; invalid input, caller-supplied role/status and duplicate or concurrent registrations are rejected without changing stored accounts; invalid credentials get a generic 401. Uses an ephemeral JWT secret. |
| `repository/CourseRepositoryIntegrationTest` | QA02 tests, PR #119 | Course → Chapter → Lesson → LearningMaterial round trip after flush/clear, published-only catalogue filtering, and database constraint and referential-integrity violations |
| `CourseCatalogueIntegrationTest` | CO04/CO05, PRs #96 and #113 | Course access assertions bound to real Auth account checks, catalogue ordering and pagination, hidden-content concealment and snapshot consistency under concurrent publication changes |
| `EnrollmentIntegrationTest` | FE07, PR #101 | Catalogue search/filter/paging validation, login binding and JWT, role and authentication precedence, hidden-versus-missing concealment, membership join/leave idempotency and concurrency, rollback, My Courses and CORS for the membership methods |
| `SampleDataSeedIntegrationTest` | SD01/SD02, PR #116 | Seed loading, reruns, conflicts, target attestation and its consumers ([record](#sd01sd02-seed-verification--30-october-2026)) |
| `SharedRestInfrastructureIntegrationTest` | SH01, PR #78 | Spring MVC ProblemDetail, validation and paging with test-only routes; it does not start PostgreSQL or the security chain |

`AdaptiveLearningFlowIntegrationTest.java` and
`MasteryProgressTransactionIntegrationTest.java` are empty scaffolds.

Merged test code does not complete QA01 or QA02: each Issue's own acceptance
criteria, independent review and any environment-specific evidence remain open.

### Latest recorded results

| Date (ICT) | Revision | Where | Command | Result |
| --- | --- | --- | --- | --- |
| 31 October 2026 | `main` `614f326` | GitHub Actions CI [run 41191743079](https://github.com/chi-thanh-nguyen/codemastery/actions/runs/41191743079); CodeQL [run 41191742751](https://github.com/chi-thanh-nguyen/codemastery/actions/runs/41191742751) | All CI jobs; CodeQL default setup | All seven CI jobs and CodeQL passed. Backend: Surefire 468 tests, Failsafe 150 tests, 0 failures, errors or skips. Frontend: 390 tests in 11 files, then a successful build |
| 31 October 2026 | `main` `614f326` | Local, macOS 27.2 arm64, OpenJDK 25.0.4.1, Docker 29.8.1, Node 24.21.0, npm 11.19.0 | `cd backend && ./mvnw -B verify` | BUILD SUCCESS; Surefire 468, Failsafe 150, 0 failures, errors or skips |
| 31 October 2026 | `main` `614f326` | Same machine | `cd frontend && npm ci && npm test && npm run build` | 390 tests in 11 files passed; build succeeded |

The two local runs were made for the DO01 documentation update. They confirm that
the documented commands work on that machine; they are not independent QA
verification. No coverage percentage is claimed.

## JUnit / Mockito Baseline

Backend tests use JUnit Jupiter 6. Its exact version is managed by Spring Boot
4.1.1 and currently resolves to 6.0.3. Do not override the managed JUnit version
without a separately approved dependency decision. Mockito remains the backend
mocking tool where appropriate.

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
Current unit suites are under `modules/auth/`, `modules/course/` (including
`application/service/`), `modules/adaptive/mastery/` and the shared `support/`
package. The other module test files are empty scaffolds. The integration base
class is compiled but is not initialized by this unit-test lifecycle, so
`./mvnw test` needs no Docker.

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
implementation is still pending. The profile supplies no secrets or storage
services. Suites that need the JWT provider register their own ephemeral
64-hex-character `JWT_SECRET` through `@DynamicPropertySource`; no secret is
committed. No storage consumer exists.

The normal application Flyway settings remain enabled against this datasource,
using `classpath:db/migration`. Hibernate retains `ddl-auto: validate`, and
does not generate a test schema. V001–V006 now implement the approved initial
23-table PostgreSQL schema. Migration execution, checksum/restart validation, and
constraint checks are part of persistence/integration validation on disposable
databases. Every suite that extends the base runs against this migrated schema;
see [Backend integration suites](#backend-integration-suites). Successful schema
migration alone proves neither an entity round trip nor feature behavior.

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
execution data exists. Because concrete suites initialize the Testcontainers
foundation, a running Docker Engine and access to `postgres:17.11-bookworm` are
required. Compose and a manually provisioned PostgreSQL service are not required.

To skip integration-test execution while retaining the other verification work:

```bash
./mvnw verify -DskipITs
```

This still compiles integration-test sources and runs eligible unit tests, but
skips Failsafe execution. It is not the CI validation command.

The abstract harness itself contains no tests. The SH01 MVC integration test
executes a real Spring web context and dispatcher, but does not extend the base,
so it neither starts PostgreSQL nor proves database integration. The suites
exercise the Spring Security chain differently:

- `AuthorizationIntegrationTest` (`DelegatingFilterProxy` on
  `springSecurityFilterChain`), `EnrollmentIntegrationTest` and the consumer case
  of `SampleDataSeedIntegrationTest` (`springSecurity()`) send requests through
  MockMvc with the configured chain.
- `CourseCatalogueIntegrationTest` uses no MockMvc. It takes the configured
  chain's `JwtAuthenticationFilter` from `FilterChainProxy`, calls it directly
  with a mock request, and runs its Course assertions inside the filter callback.
- `repository/CourseRepositoryIntegrationTest` sends no HTTP request and uses no
  security chain.
- `SharedRestInfrastructureIntegrationTest` uses MockMvc with only
  `CorrelationIdFilter`, so no security chain.

None of them uses a listening server, so none exercises Tomcat, a browser or a
reverse proxy.

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
must explicitly clean up changes that rollback does not cover. The current suites
do this themselves, for example by deleting the rows they committed after each
test; `SampleDataSeedIntegrationTest` creates a separate database per scenario.
No generic truncation or schema reset infrastructure is implemented. CourseFixtures only
constructs in-memory representations; it does not load or reset a database.

## JaCoCo

JaCoCo 0.8.15 is configured to attach its agent during Maven initialization and
generate a report at `verify` when execution data is available. No coverage
threshold or coverage enforcement goal is configured, and no coverage percentage
is recorded or claimed. A successful build or report goal alone is not evidence of
feature coverage.

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
`npm run typecheck` before `vite build`, so the CI build step also typechecks.
Build output goes to `frontend/dist/`. Development-only Vitest `5.0.5` and jsdom
`27.4.0` execute the bounded frontend suite through `npm test` (`vitest run`).
No lint script is implemented. The required Frontend CI job runs `npm ci`, `npm test`, then `npm run build`.
The optional browser harness is separate and is not run by that job.
Build success alone does not establish behavioral coverage or deployment readiness.

For the existing optional `npm run test:browser` script, supply an installed
Playwright module, artifact directory, preview URL and `chromium` or `webkit`.
No browser is downloaded by this script. When validating a standalone frontend
configured with the approved `/api` base and no API proxy, append `--standalone`.
That mode verifies the Course error view and records exactly the two detail-read
404s and one cold catalogue-read 404 separately from unexpected console errors.
It does not establish real browser → REST → PostgreSQL integration. Backend
connectivity/CORS, successful Auth/Course transactions and assistive-technology
acceptance require their independent evidence.

The optional `--test-catalogue-filter` harness mode measures search, prerequisite
and apply-button bounds while menus open, options change and filters are applied.
It covers empty and scrollable catalogue fixtures at 320, 390, 768, 900, 1024 and
1440 pixels, plus keyboard selection and unchanged explicit-submit semantics.
These controlled responses verify presentation and client criteria, not live API
integration.

The optional `--test-auth-focus` harness mode runs bounded Login/Register focus
checks, including catalogue-to-Auth CSS insertion order, pointer/keyboard and
validation focus, Back/Forward, persisted `pagehide`, and connected-input blur
before route/form removal. Catalogue and registration responses in that mode
are controlled fixtures. Unit regressions also cover StrictMode listener cleanup,
ordinary rerenders and focus outside the owning layout. AutoComplete semantics
remain enabled. Headless DOM/focus checks do not inspect native operating-system
AutoFill/password-manager popup rendering; that requires separate manual evidence.

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

## CI

The `CI` GitHub Actions workflow runs on pull requests targeting `main`, pushes
to `main`, and manual `workflow_dispatch`. It uses `contents: read` permissions
and eight independent jobs on `ubuntu-24.04`:

| Job display name (ID) | What it runs |
| --- | --- |
| Backend (`backend`) | Temurin 25; `./mvnw verify` from `backend/`, including the Testcontainers integration tests on the runner's Docker |
| Frontend (`frontend`) | Node 22; `npm ci`, `npm test`, then `npm run build` from `frontend/` |
| VM deployment script (`deployment-script`) | Checks of `infrastructure/vm/deploy.sh` without starting the application: shell syntax, authorization gates, Compose and provisioning guards, the runtime privilege matrix, env-file custody, preflight against rendered Compose, release-archive identity models and image identity through a real `docker save`/`load` round trip |
| VM deployment PostgreSQL and containment tests (`deployment-script-postgres`) | The runtime privilege matrix on the real migrated schema, failure containment and published-port verification against real containers |
| VM runtime privileges with the real backend (`deployment-script-runtime`) | Builds the backend image and exercises real Auth, catalogue and membership under the approved privilege matrix |
| VM deployment containerd image store (`deployment-script-containerd`) | Release-archive verification through real `docker save`/`load` round trips with the containerd image store |
| VM HTTPS ingress (`deployment-ingress`) | Builds the release and local frontend images; exercises the HTTPS ingress against a stub and the real backend; runs `deploy.sh preflight` with the committed override |
| VM MinIO initializer qualification (`minio-initializer`) | Pulls the digest-pinned Golithus server, `mc` and Chainguard candidate server images, builds `infrastructure/minio` for linux/amd64 and runs `infrastructure/minio/test-minio-init.sh` against disposable MinIO servers and the effective VM Compose model, first with the Golithus server and then with `MINIO_INIT_TEST_SERVER=chainguard` ([record](deployment.md#dp01-minio-community-initializer--31-october-2026), [candidate](deployment.md#dp01-chainguard-minio-release-server-candidate--1-november-2026)) |

The Java setup enables Maven caching using `backend/pom.xml` and
`backend/.mvn/wrapper/maven-wrapper.properties` as dependency inputs. The Node
setup enables npm caching keyed by `frontend/package-lock.json`. Caches do not
replace installation or validation. CodeQL code scanning runs separately through
GitHub's default setup, with no workflow file in this repository.

The `main-quality-gates` repository ruleset requires `CodeQL` and every job above
to pass before merging into `main`. A later read of the GitHub API on 1 November
2026 lists “VM MinIO initializer qualification” as required too; an earlier read
that day did not. That job therefore keeps its single check name. The VM jobs run only on the
CI runner: they deploy nothing and do not reach the real VM. No job starts the
full local Compose stack. The VM jobs start their containers with `docker run`;
only the MinIO initializer job uses Compose, starting just the `minio` and
`minio-init` services (and a placeholder backend under the VM override) in
disposable projects. There is no E2E job or artifact upload. Workflow configuration does not prove a successful run; see
[Latest recorded results](#latest-recorded-results).

## Remaining Gaps

The following are not covered by any executable test in the repository. No
test-count or coverage-percentage claim is made for them.

- **Empty scaffolds.** Assessment, Interaction and Admin unit tests; Adaptive
  decision, evaluation, listener, settings, mastery-update, placement and skill-graph
  tests; `LessonProgressServiceTest`; `AdaptiveLearningFlowIntegrationTest`;
  `MasteryProgressTransactionIntegrationTest`; `ScenarioLoader`, `TestClockConfig`,
  `SkillGraphFixtures` and the three adaptive scenario JSON files. They wait for
  their features and approved contracts.
- **E2E.** No Playwright package, configuration, fixtures or CI job. The
  standalone FE07 browser script needs an external Playwright installation and a
  running stack, and is run manually.
- **Real-browser and runtime integration.** No CI job drives a real browser
  against a running backend and PostgreSQL. Manual local runs, including the
  standalone FE07 script, are recorded in the dated sections below; QA07 and DP02
  acceptance are open.
- **Deployment.** No test reaches the real VM or checks outside-network access
  (DP01 #71, DP03 #72). The private `/actuator/health` probe answers 401
  ([deployment blocker 1](deployment.md#verification-and-remaining-gates)).
- **Object storage.** No backend storage adapter exists, so no test covers file
  upload or delivery (CT09).
- **Assistive technology.** No test establishes actual screen-reader output.

Coverage threshold enforcement is not configured; any future enforcement
requires an approved decision.

## Authorized Interim verification contracts

> **Dated records.** This section and those after it keep the wording of their
> date. Statements in them such as "scaffolds remain empty" or "CI runs install
> and build only" describe that date. The current state is in the sections above.

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
| Catalogue service; Course | `modules/course/application/service/CourseServiceTest.java`; unit, **approved addition** | Effective CT03.catalogue/CT21.catalogue DTO/privacy and syllabus mapping/order, missing/hidden detail equivalence and validated pagination. Isolated repository doubles establish orchestration only; no Auth dependency is required for anonymous catalogue. |
| Catalogue REST/query composition; Course/QA02 | `integration/CourseCatalogueIntegrationTest.java`; integration, **approved addition** | Real PostgreSQL publication filtering before pagination/count, stable title/UUID order and canonical syllabus order, empty/beyond-last page, invalid/default/boundary sizes, unsupported parameters, safe 404 and no hidden fields/content/storage/settings. Exercise actual Course API/advice for REST claims; a repository-only test cannot prove serialization. |
| Course mappings/constraints; QA02/Course | `integration/repository/CourseRepositoryIntegrationTest.java`; integration | V002/accepted scalar mappings, real FK/uniqueness/nullability and approved query predicates. Flush/clear or otherwise force database evaluation before claiming a round trip/constraint; setup and assertions must not merely return cached/in-memory representations. Coordinate catalogue assertions with the preceding row without duplicating identical tests. |
| Local mastery; AD09 with independent CT36/QA14 reviewer | `modules/adaptive/mastery/MasteryCalculatorTest.java`, `modules/adaptive/mastery/MasteryStateClassifierTest.java`; unit | Only effective CT06.prototype/CT24.calculation and exact CT36.I-results table, including unknown versus zero successes, equality, alternate rational policy, invalid/null inputs and maximum-int multiplication. Pure in-memory evidence; no Spring, DB, clock, events, grading or production aggregation prerequisite. Reviewer independently checks arithmetic before accepting results; CT14 creates no new threshold/oracle. |
| Shared infrastructure; SH01 | `support/PageResponseTest.java`, `support/AppConfigTest.java`, `support/CorrelationIdFilterTest.java`; local tests; `integration/SharedRestInfrastructureIntegrationTest.java`; MVC integration | Preserve current delegated tests and their limited boundary above. Real feature security/PG assertions belong to owning integration suites, not invented shared endpoints. Existing `CourseFixturesTest.java` in `modules/course/` establishes structural fixture behavior only. |
| Browser Auth/catalogue and UI; Frontend/QA07 | Existing Auth/catalogue clients/pages, read-only browser protocol; no new test file | NC-EVIDENCE-MODE requires actual browser → REST → PostgreSQL plus redacted query evidence and hidden-course contrast, memory-only session/reload/401/403 behavior under effective policies, safe English validation/errors and responsive presentation. Actual FE03/FE07, AU06/CO05 and consumed configuration are execution prerequisites; doubles allow preparation only. Record localhost integration separately from DP03 external access. |
| Final/selected Stretch; owning modules and QA | Existing files listed below; unit/integration and conditional E2E | Map required behavior to these inventories now; behavior, fixtures and expected outcomes must first be approved by their owning contracts. Unimplemented and unselected cases remain explicit coverage gaps; they do not block independently approved Interim units. |

The remaining approved inventory is preserved, without selecting new behavior:

- Course enrolment/progress: `modules/course/application/service/EnrollmentServiceTest.java` and
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

## Approved Interim FE03/FE07 verification contract — 24 October 2026

### Authority, exact revision and pending dependencies

**CT14-INTERIM-FE03-FE07-2026-10-24-r1: DELEGATED_TECHNICAL_APPROVAL;
APPROVED_PENDING_PROTECTED_INTEGRATION.** The Project Leader explicitly delegates
actual Interim CT14 technical selection/approval in **“CodeMastery — Complete
Interim Technical Contract Approvals (CT16, CT17, CT14)”**, supplied 24 October 2026
(ICT). Codex, as the delegated technical decision-maker, **approves the exact six
paths, ownership, case allocation, tooling reuse and isolation below**, including
the six-file inventory exception in [repository structure](repository-structure.md#bounded-interim-fe03fe07-test-inventory).
This is approval of the test contract, not permission to create or execute tests.

Fresh main is
[`0b2967226fcab946e0145160b1a18e21c30613d8`](https://github.com/chi-thanh-nguyen/codemastery/commit/0b2967226fcab946e0145160b1a18e21c30613d8),
including AD09 #86. Current frontend has Vitest 5.0.5/jsdom 27.4.0, React/ReactDOM
19.3.0, the existing npm test script and FE01 act/createRoot examples. No six-file
implementation exists on inspected main. Preserve FE01 D1–D4, Boot-managed
JUnit/Mockito, ADR002 CT14.interim/NC-EVIDENCE-MODE and all existing approved paths.
No whole CT14.coverage/Final approval or activation of the entire #87 proposal.

Behavior depends on **CT16-2026-10-24-r1** in unmerged [Draft PR #102](https://github.com/chi-thanh-nguyen/codemastery/pull/102),
exact **802ee19e38f22b35267bb51dfe04c8ee56b64acb**, including its canonical catalogue
path correction. CT16 completeness certification preserves that behavior; no new
behavioral prerequisite is invented. This CT14 PR is independently based on main,
with an explicit integration dependency on #102. Do not treat its pending text as
an effective main contract. Revalidate after that reviewed revision integrates;
separate explicit implementation authority is still required. Existing consumed
CT01 protected-integration provenance is retained, not waived by this test contract.

### Exact six-file inventory and nonduplicated case allocation

All paths are repository-relative. Under current CODEOWNERS every path has
**@heizseung and @chi-thanh-nguyen** ownership; Frontend authors under a separate
task, Auth @duyduyit and Course @TamiaoNguyen review their consumed behavior.
QA07 reviews the boundary between DOM and real integration evidence.

| Approved new path | Required observable behavior and boundary |
| --- | --- |
| frontend/src/shared/api/httpClient.test.ts | Generic base/path/query normalization and namespace escape rejection; bearer only for current authenticated API requests, none for public calls; credentials omitted/redirects rejected; expected status/media type and required decoder; malformed/HTML/empty bodies; HTTP status retained on invalid ProblemDetail; safe typed HTTP/network/protocol/cancel errors; no retries and no effect from cancelled/superseded work. Mock fetch, not the client under test. |
| frontend/src/app/providers/AuthProvider.test.tsx | Real provider/useAuth with controllable transport: latest login attempt, failed replacement preserves current session, logout-before-login completion, old/public 401 versus current authenticated 401, 403/network/5xx preservation, exact expiry/resume/pre-send behavior, no persistent token/private state, cancellation and StrictMode cleanup. Mount existing Auth forms/pages within this file for selected registration/login payload/validation/error handoff, no-auto-login and exact password preservation; include supplementary Unicode and UTF-8 boundaries from CT01. No extra Auth form test file is needed. |
| frontend/src/routes/index.test.tsx | Real selected route composition with BrowserRouter/guard and controlled approved provider state: root redirect, login/register/catalogue/detail, URL/back-forward transitions, invalid return destination rejection, unauthenticated/forbidden reusable guard behavior, unknown-route state, cleanup and FE01 layout compatibility. Test-only guarded child content is a UI fixture, never a new protected API/product route. DOM route tests do not prove Nginx deep-link serving. |
| frontend/src/features/catalogue/api/catalogueApi.test.ts | Real catalogue client with transport seam: exact public list/detail method/path/query and expected status; defaults/bounds from CT21/CT18; required DTO/page/UUID/type decoding and rejection of invalid shapes; required-nullable prerequisiteDescription; no hidden content/settings/storage/membership or invented display-name fields. Item/DTO decoding belongs here rather than duplicating generic fetch tests. |
| frontend/src/features/catalogue/pages/CourseCataloguePage.test.tsx | Page plus real useCourseCatalogue/CourseCard, controllable API results: loading/success/empty/error, approved page/size/beyond-last behavior, server totals/order preservation, detail navigation, current-request identity, rapid query replacement, cancellation and unmount cleanup. Exercise hook behavior here instead of adding a redundant useCourseCatalogue.test.tsx. |
| frontend/src/features/catalogue/pages/CourseDetailPage.test.tsx | Exact approved detail/prerequisite/syllabus rendering, canonical order supplied by server, missing/hidden indistinguishable unavailable state, loading/error/retry of current reads, route-ID changes and stale detail suppression. No enrolment/leave/My Courses or membership button/API is part of this contract. |

All six are justified: generic transport, provider/Auth composition, routing,
feature DTO client, list page and detail page have different public behavioral
boundaries. Fewer files would mix those responsibilities; extra hook/form/guard
files would duplicate the allocated cases without current need. No generic helper,
fixture/setup file, new directory, additional backend test path or tooling package
is approved by this inventory exception.

### Tools, deterministic isolation and permitted seams

Reuse existing **Vitest/jsdom**, React **act/createRoot**, existing app theme/layouts,
native DOM events and existing TypeScript configuration; use per-file jsdom
annotation when DOM is required. Keep manifest/lockfile/scripts/Node baseline
unchanged. React Router tests consume the exact dependency already technically
selected by CT16 only after separate installation/file authority; no data/framework
router, new renderer, testing-library, request mock server or E2E runner is added.

- Use explicit synthetic CT01/CT21-conforming inputs and deterministic fixture
  timestamps/UUIDs. A fabricated JWT payload used for UI expiry scheduling cannot
  prove signature/account validation or authenticate a real backend. Never use
  production/lecturer credentials, persist real secrets or print tokens/passwords.
- Use controlled promises and fake system time/timers where relevant; test just
  before, at and after the approved expiry. Do not wait real time, use random
  sleeps, change production expiry, or introduce a production Clock/CT39 API.
- Each test owns fresh provider state, DOM root, request identifiers, promise and
  AbortController state, fetch spy and history fixture. Restore URL/history,
  mocks/globals/environment/timers/listeners and module callback subscriptions;
  unmount inside act and settle or cancel owned work even on failure. No late
  completion may contaminate another test. Do not run shared-global DOM/history
  cases concurrently in one environment.
- Provider-global 401 callbacks must be unset on cleanup; abort alone is not a
  generation check. Verify public/stale/cancelled requests cannot clear another
  session or publish private state after logout.
- Assert observable requests, UI, navigation and denied effects; do not duplicate
  implementation internals or freeze incidental export signatures/layout snapshots.
  DOM tests cannot assert real browser navigation/TLS/network guarantees.

Future execution uses existing npm test from frontend/ with the approved file
filters; no command is executed by this documentation task. At the 24 October contract-selection baseline, CI built/typechecked frontend
without npm test; the later FE03 implementation added npm test to CI (see below). No new CI job, coverage percentage,
pass count, browser installation or success result is invented here.

### Existing Interim backend and independent evidence contracts

No further backend test-file approval is required for this selected Minimum:
ADR002 CT14.interim already places AuthServiceTest/JwtTokenProviderTest and
AuthorizationIntegrationTest, the two bounded Course additions and the two local
mastery tests. Keep actual module ownership and the existing singleton
AbstractIntegrationTest PostgreSQL lifecycle, test-profile scheduler=false and
Surefire/Failsafe selection. Dynamic test datasource inputs stay disposable;
production privilege configuration is not copied into the shared test harness.

For those consumed Interim tests, explicitly approve the narrow isolation detail:
rollback only work that actually joins the test transaction; force flush/commit
where a constraint/commit is claimed; record owned fixture IDs for separate
transactions/HTTP/concurrent work, await completion, then cleanup in fresh
committed FK-safe transactions even on failure. If deterministic cleanup is not
available, stop execution; no truncation/FK disabling/Flyway reset/container reuse.
Generate ephemeral consumed JWT test secrets; retain exact CT01 claims/lifetime
and current-account checks. No other Final fixture/configuration/clock selection.
This refines only existing CT14.interim isolation, not the full #87 proposal.

| Evidence layer | Required separate execution; never replaced by these six files |
| --- | --- |
| Configured backend security / QA01 | Actual configured security chain and approved operations/roles, account lock/state, password storage and PostgreSQL uniqueness; no fake security-probe endpoint |
| Persistence / QA02 | Real PostgreSQL/Flyway queries, publication/count/order/hidden cases, mapped data and transactional isolation |
| Browser integration / QA07 | Actual browser → approved Auth/Course REST → PostgreSQL, English/responsive interaction, session/reload/race behavior; labels manual observations and automation separately |
| External deployment / DP03 | Approved real HTTPS origin reachable outside developer network, correct proxy/deep links/private infrastructure and usable secure lecturer handoff |
| Local mastery / CT36, QA14 | Independently checked exact oracles and reproduction of merged AD09; not self-authored expectations, DOM results or cross-module integration claims |

Existing independent manual browser evidence mode remains sufficient for Interim;
mandatory Final Playwright obligations remain. No empty e2e scaffold supplies a
runner command. Required tests for a yet-unapproved protected operation await its
own binding; anonymous catalogue checks alone cannot prove authorization.

### Readiness, exclusions and protection

**CT14_INTERIM_TECHNICAL_SCOPE_COMPLETE** means the existing Interim contract plus
this exact frontend extension and consumed isolation detail covers the selected
Minimum's testing decisions. It does not certify executed coverage or approve
all CT14, CT19/CT34/CT35, Final evaluation or deployment. No source/test/dependency
is created or executed, and no implementation is authorized.

Current #98/#101 have no corresponding automated behavior suites; that is missing
implementation/evidence, not a reason to weaken CT01/CT16. Their scope discrepancies
must be remediated separately; CT22 remains excluded. Every new determination
stays APPROVED_PENDING_PROTECTED_INTEGRATION, with #102's dependency explicit.
The sole-Leader-CODEOWNER PR-authorship conflict, eligible independent human
review, last-push approval, checks and exact-revision integration remain unresolved;
non-owner review or another contributor's push alone cannot substitute. Issue #27
checkboxes/whole-Issue completion remain unchanged.

## Enrollment unit-test placement amendment — 29 October 2026

During PR #114 review/remediation, the Project Leader explicitly authorized
retaining the existing relocation and synchronizing documentation. The canonical
file is now `backend/src/test/java/com/codemastery/modules/course/application/service/EnrollmentServiceTest.java`,
matching its existing `com.codemastery.modules.course.application.service` package.
[ADR002](decisions/002-interim-delegated-contracts.md#ct14interim--delegated_approved)
records the exact authority and bounded location supersession. Existing test
contents/responsibilities, package-private access and Course CODEOWNERS remain
unchanged. This is placement approval, not approval of new CT22 behavior or an
executed test result; only one canonical test class is retained.

## CT14 consolidation and bounded Final approvals — 25 October 2026

### Authority, predecessor and canonical scope

**CT14-FINAL-2026-10-25-r1: DELEGATED_TECHNICAL_APPROVAL;
APPROVED_PENDING_PROTECTED_INTEGRATION.** Under the Leader's explicit
**“CodeMastery — Consolidate Contract Approval PRs and Complete Eligible Final
Technical Approvals”**, supplied 25 October 2026 (ICT), Codex acts as delegated
technical decision-maker and **approves F1/F2 below only**. This does not authorize
test creation/execution, workflow edits or implementation.

Fresh main is **0b2967226fcab946e0145160b1a18e21c30613d8**. Canonical
[PR #106](https://github.com/chi-thanh-nguyen/codemastery/pull/106) preserves its
original **39673f162afe5749eb21e1831c183834c9473d0a** testing record and exact
six-path repository-structure exception. Existing FE01 D1–D4, backend decisions,
CT14-INTERIM-FE03-FE07-2026-10-24-r1 and CT14_INTERIM_TECHNICAL_SCOPE_COMPLETE
remain intact. No new path or repository-structure exception is needed for F1/F2.
The CT16 dependency is canonical #102 after consolidation, retaining original
802ee19/5bf70b4 provenance; its pending approval is not effective on main.

### F1 — approved Course access regression specification for Final consumers

**Checkpoint CT14.final-course-access-regression: APPROVE** the test allocation
below, derived from ADR001 §12's approved CT03-owned predicates and failure order.
It extends coverage beyond the Interim public catalogue without approving a
Course mutation, Assessment/Interaction operation, CT40 projection or complete U07.
Existing case/test grants are E, not approved again; F1 newly approves the Final
regression allocation/evidence boundary only. These are already approved test
locations, not new inventory or permission to edit them. Current #96 at **9c6238c39b5a96bf41ece29f4716c1901feac22e** demonstrates
feasible boundaries, not authority or retrospective approval of its implementation.

| Exact repository-relative path | Layer, responsibility, owner and acceptance |
| --- | --- |
| backend/src/test/java/com/codemastery/modules/course/application/service/CourseServiceTest.java | Unit; Course @TamiaoNguyen and Leader CODEOWNERS. Cover all five public/two package-private approved assertions in their owning package context. The path matches the declared `com.codemastery.modules.course.application.service` package under the [Leader-authorized 26 October placement amendment](decisions/002-interim-delegated-contracts.md#ct14interim--delegated_approved); the former directory/package mismatch allowance is superseded for this file. Trusted-identity seams are labelled doubles, never security integration. |
| backend/src/test/java/com/codemastery/modules/course/CourseRepositoryImplTest.java | Unit adapter; same Course/Leader ownership. Check parameter/fact mapping and failure propagation at the existing private Course boundary; mocks cannot establish SQL, isolation or coherent PostgreSQL behavior. No new public repository interface is authorized. |
| backend/src/test/java/com/codemastery/integration/CourseCatalogueIntegrationTest.java | Real PostgreSQL/Flyway; the earlier bounded addition already permits repository/access/pagination coverage. Course is responsible and Leader coordinates integration review under ADR002; this exact file currently has **no matching CODEOWNERS rule**, which is not silently changed or reported as an assigned CODEOWNER. Preserve catalogue cases and add only distinct coherent-read/ancestry/publication/membership evidence; no duplicated mock assertions or fictitious REST endpoint. |

Required matrix: Learner ACTIVE/COMPLETED membership versus LEFT/absent;
Instructor own/other Course including approved hidden owner preview; no implicit
Administrator inheritance; Lesson/Chapter/Course and Material ancestry; unavailable
versus disclosed-denied resources with exact CT03 401/403/404 precedence; single-
Lesson read concealment; trusted-identity infrastructure failure propagated safely;
no hard block based solely on mastery/prerequisite absence. Enrolment eligibility
creates no row and does not decide repeat/leave/re-enrolment policy.

For coherence claims, exercise the actual PostgreSQL adapter and demonstrate that
the returned Course-owned facts correspond to a coherent permitted read context;
where interleaving is tested use controlled transaction barriers and joined worker
completion, not sleeps. Do not select a new lock/isolation/propagation policy or
claim whole-operation linearizability. Keep snapshots internal to Course. Use the
approved singleton, explicit owned fixture IDs and committed FK-safe cleanup for
non-rollback work. Test actual database outcomes, not only invocation counts.

No cross-module consumer reaches Course entities/repositories. Actual production
identity binding still needs the implemented approved Auth facade; #96's fail-closed
placeholder is not authentication. Full security-chain verification remains in
AuthorizationIntegrationTest and real selected operations; real consumer E2E/atomic
work waits for its owning contract. No new data fields, seeds, schema, permissions,
threshold or result is selected by this test specification.

Alternatives: a new access suite duplicates an already approved service location;
putting these assertions in enrolment/progress tests would imply unapproved mutation
semantics. Existing scoped files preserve layering and require no new dependency.
CT03 owner/consumer and Auth reviews accompany Leader/test review. All test edits,
actual execution, target-service coverage and QA acceptance remain separate tasks.

### F2 — existing-tool CI regression and exact-head evidence contract

**Checkpoint CT14.final-ci-regression: APPROVE** future execution of the already
selected backend/frontend local regressions in existing CI jobs after separate
workflow/file authorization. Backend retains Maven Wrapper **./mvnw verify** from
backend/ with the current JUnit/Mockito, Surefire/Failsafe and PostgreSQL harness;
claiming persistence coverage requires Docker and actual database cases, not
skipITs. Frontend uses existing **npm ci**, **npm test**, **npm run build** from
frontend/. npm test is the existing Vitest script; this is not selection of CT34's
Playwright runner, a new script/library or optional browser-harness dependencies.

This approves adding the existing frontend test execution to its existing job;
current ci.yml still performs install/build only and is unchanged by this task.
Preserve Backend/Frontend/CodeQL required contexts and security protections. Do
not make a skipped, empty, failed or wrong-head suite green by ignoring its exit
status. Record the resolved implementation head and actual checked-out revision;
PR merge-ref runs must identify both rather than mislabel a different commit.

Evidence includes invoked commands/environment, named cases and failures/skips,
Surefire/Failsafe XML, relevant target-service JaCoCo reports where applicable,
frontend Vitest output and run URL. Existing Maven report locations and Action
logs suffice; no new tracked report file/helper, artifact service, numeric coverage
threshold or retention commitment. Reports separate mocked unit, DOM, actual PG,
configured security, browser/E2E and external checks. Unit/build success cannot
certify an unexercised target. Reruns after changed heads need new evidence.

Alternatives: build-only frontend CI misses the already implemented/approved
regressions; another runner duplicates the selected toolchain. Select existing
jobs/tools with honest layer-specific evidence. Test owners maintain their cases;
workflow implementation remains Leader-owned. No CI job or test is executed locally
by this documentation task; automatic platform checks are reported separately.

### Entire retained Final decision inventory

| Subject | Classification | Exact remaining prerequisite or disposition |
| --- | --- | --- |
| Approved Interim Auth/catalogue/mastery, FE01 and six frontend paths/tools/isolation | E — ALREADY APPROVED | Preserve their exact scopes; no duplicate approval or promotion of prototype oracles into Final Adaptive policy |
| CT03-owned access regression and existing-tool CI execution/evidence | A — READY FOR DELEGATED TECHNICAL APPROVAL | F1/F2 approved above; no new paths, production operations or runner |
| Enrolment/progress/resume tests | D — DEFER UNTIL CONSUMER OR INTEGRATION READINESS | CT22 transitions, errors/concurrency and actual interfaces/oracles; existing EnrollmentServiceTest/LessonProgressServiceTest names supply no behavior |
| Assessment attempts/publication, graph, production mastery/settings/recommendation | D — DEFER UNTIL CONSUMER OR INTEGRATION READINESS | CT04/CT23/CT05/CT06/CT07/CT24/CT40 approved policies, callables and expected results |
| Submitted-quiz/atomic mastery-progress and failure/replay tests | D — DEFER UNTIL CONSUMER OR INTEGRATION READINESS | CT08/CT22 transaction/event/duplicate semantics and real producers; existing AdaptiveLearningFlowIntegrationTest/MasteryProgressTransactionIntegrationTest are not speculative authority |
| Other frontend feature test paths and API contract scenarios | D — DEFER UNTIL CONSUMER OR INTEGRATION READINESS | Owning feature request/response/access contracts and exact inventory review; do not duplicate the approved six suites |
| Storage-specific harness/configuration and recovery testing | B — REQUIRES ADDITIONAL TECHNICAL EVIDENCE | CT09 transport/storage semantics, qualified storage build/configuration and safe fixture isolation; no new MinIO testcontainer approved |
| Playwright runner/fixtures and particular E2E journeys | D — DEFER UNTIL CONSUMER OR INTEGRATION READINESS | CT34 runner/configuration, CT19/CT35 cases and effective feature interfaces; mandatory Final E2E still required, not waived or auto-assigned to both empty suites |
| P1/P2/P3 profiles, routing/learning metrics and independent evaluation | D — DEFER UNTIL CONSUMER OR INTEGRATION READINESS | CT36.final, CT06/CT07/CT24 rules and reviewed baseline/oracles; preserve the separately approved evaluation target without turning it into a mastery threshold |
| Performance/load/reliability numeric acceptance | B — REQUIRES ADDITIONAL TECHNICAL EVIDENCE | Actual workload, deployed capacity, feature timing/consistency obligations and measured baseline before choosing justified limits; no invented percentage/latency/concurrency |
| Access to real environment/accounts and material risk exception | C — LEADER-RESERVED DECISION | Actual access/custody/exposure grants; ordinary test placement is not personal Leader risk authority |

**CT14_FINAL_TECHNICAL_SCOPE_PARTIALLY_APPROVED.** The current Final test-contract
scope gains only F1/F2; independent assessment of every remaining subject does not
turn missing APIs/oracles/runner authority into an approval. Mandatory Final tests,
security, integrated advanced demonstration, evaluation and reproducibility remain.
No whole #87 proposal, other testing contract or Issue is approved by this record.

Evidence sources are ADR001 §12, ADR002 CT14, current #96 diff, exact approved test
inventory, backend pom.xml/AbstractIntegrationTest, frontend package/FE01 examples
and ci.yml. Actual code/results support feasibility only. No implementation, test
run, deployment, review submission or merge is authorized. Required eligible
CODEOWNER/last-push review, exact-head checks, resolved threads and protected
integration remain mandatory; sole-Leader documentation ownership cannot be
self-approved or satisfied by an unrelated non-owner review/push alone.

## CT14 Profile and Auth UI verification extension — 25 October 2026

### Authority, revision and behavioral dependency

**CT14-PROFILE-AUTH-2026-10-25-r1: DELEGATED_TECHNICAL_APPROVAL;
APPROVED_PENDING_PROTECTED_INTEGRATION.** Under the Leader's explicit
**“CodeMastery — Complete Additional CT16, CT17 and CT14 Technical Approvals in
Existing PRs”**, supplied 25 October 2026 (ICT), Codex acts as delegated technical
decision-maker and **approves the specification below only**. Test creation/run,
application/workflow/tooling changes and QA acceptance are not authorized.

Inspected main is
[e2128919fe0d47c0f24989166343e1d5ecf50483](https://github.com/chi-thanh-nguyen/codemastery/commit/e2128919fe0d47c0f24989166343e1d5ecf50483);
[PR #106](https://github.com/chi-thanh-nguyen/codemastery/pull/106) starts at
a35059cd254db7a087b97a069028eefe71479b26. Its @TamiaoNguyen APPROVED review
covers that old head, not the new extension. Preserve active re-review rules.
CT14-INTERIM-FE03-FE07-2026-10-24-r1, its six-path inventory exception, existing
FE01/backend grants and CT14-FINAL-2026-10-25-r1 F1/F2 are unchanged.

The exact producer contract is **CT16-PROFILE-2026-10-25-r1**, checkpoints
profile-routing/profile-data/profile-lifecycle in canonical
[PR #102](https://github.com/chi-thanh-nguyen/codemastery/pull/102), plus existing
CT16 HTTP/Auth/F2 and ADR001 §11/ADR002 CT01/CT18/CT38. These pending decisions
must validly integrate before dependent implementation. Main's UserResponse and
OpenAPI PublicUser/LoginResponse agree on id/displayName/role; a real login HTTP
producer and frontend implementation are still missing. Synthetic provider/HTTP
results below are labelled doubles, never proof of authentication/security.

### V1 — Profile allocation without new inventory

**Checkpoint CT14.profile-verification: APPROVE** cases in exactly the following
two already approved suites. The other four suites retain their existing cases.

| Existing approved path | Layer and nonduplicated responsibility |
| --- | --- |
| frontend/src/app/providers/AuthProvider.test.tsx | Real provider/useAuth with controlled approved login transport and session observation: atomically install the three-field snapshot; invalid response cannot install partial state; registration never authenticates; failed replacement preserves valid A; successful B removes A; same-UUID replacement still invalidates A callbacks; logout-before-login completion, exact expiry/resume and current/stale/public 401 rules; private-state/storage/log leakage and StrictMode listener cleanup. |
| frontend/src/routes/index.test.tsx | Mount the real existing ProfilePage with the real declarative route/guard/provider composition. Cover signed-in access for all three roles and navigation visibility; anonymous /profile redirect without even transient personal rendering; no external/query/history-payload return target; one-use memory-only /profile return, cleared on explicit logout without automatic re-arming; root fallback; deep-link entry, reload simulation, back/forward and unknown-route behavior; logout/expiry removes active Profile; session replacement never displays A in B; exact fields/English labels/plain-text hostile displayName; leaving/unmounting Profile cleans owned work but preserves a valid provider session. Assert no Profile lookup/API call, edit controls or invented fields/permissions. |

Observe requests through the existing transport/fetch seam; only explicit approved
login/register or unrelated approved reads initiated by the scenario are allowed.
Profile entry/render/resume must issue **zero additional data requests**, including
/api/profile, /api/users/me or a user-directory request. Never render actual token
or fixtures' email/secret sentinel values. Test current state and intermediate
commits/publications, not only the eventual redirect screen after effects settle.
Use controlled promises/fake time rather than sleeps for stale/race boundaries.
Reload simulation creates a fresh provider at /profile; it cannot establish real
HTTP deep-link delivery. Simulated popstate/history transitions cannot establish
browser bfcache behavior. Those remain V2/QA07 browser obligations.

**No ProfilePage.test.tsx is selected.** Profile is a three-field session projection;
its meaningful behavior is guard/provider composition, already exercised with the
real page in routes/index.test.tsx. A third suite would duplicate those boundaries.
The canonical component location is features/auth/pages/ProfilePage.tsx; this fact
does not approve a new test sibling. No repository-structure exception, helper,
fixture/setup file, dependency, runner or directory is needed.

### V2 — Auth UI regression and evidence boundary

**Checkpoint CT14.auth-ui-regression: APPROVE** the following explicit regression
allocation. Existing password/session cases remain previously approved; this
extension adds observable UI integration/accessible-state/focus obligations and
Profile composition without duplicating the earlier generic HTTP matrix.

In **AuthProvider.test.tsx**, mount the existing LoginPage, RegisterForm and
RegisterPage with the real AuthProvider; the route suite owns navigation/focus-on-route cases:

- Registration: 14 versus 15 Unicode code points; supplementary-plane characters
  count once, combining sequences count by code point, not grapheme/UTF-16 length.
  Independently constructed 71/72/73 UTF-8-byte cases also satisfy the minimum
  point count, so each boundary has an unambiguous oracle. Display name is trimmed
  first, nonblank, at most 100 points and has no control characters; cover 100/101
  after trimming. Do not silently truncate/normalize passwords or reuse JS length
  or HTML minlength/maxlength as the authoritative Unicode predicate.
- Exact password preservation: leading/trailing spaces and canonically equivalent
  but byte-distinct strings reach the request unchanged. Email uses the existing
  canonical ASCII contract. Login does not apply registration complexity rules:
  syntactically valid login requests with unsuitable credentials take the approved
  generic failure path; over-72-byte candidates are not truncated or assigned a
  fabricated client registration error. Backend costly/dummy BCrypt remains backend
  evidence, never a DOM assertion.
- Controlled pending/success/400/401/409/network/5xx results exercise applicable
  login versus registration outcomes, loading/status/error rendering, safe known
  field associations/general fallback, no registration auto-login and no hidden
  automatic mutation retry. A public login failure cannot erase valid Profile A.
- Assert semantic names, labels, role/status/error associations, focusable controls
  and intended programmatic invalid-field/error-summary focus. No raw problem HTML,
  rejected password, token, sensitive diagnostic or unapproved account field may
  appear in DOM/log/storage. Synthetic keyboard events exercise explicit handlers;
  jsdom does not simulate actual Tab traversal, default browser actions, assistive
  announcements or visual focus visibility.
- Reuse the controlled logout/expiry/replacement/late-login cases from V1/earlier
  CT14; each responsibility has one owning case rather than duplicated fixtures.
  In routes/index.test.tsx verify intended heading focus after approved navigation
  and no focus theft by ordinary delayed updates. DOM focus assertions establish
  wiring only, not correct focus behavior in a real browser.

Reuse the exact approved Vitest/jsdom/React act/createRoot toolchain, per-file DOM
environment, current scripts and six-path isolation rules. Each scenario owns
fresh provider/DOM/history, fetch/promise/abort state, fake time and listeners;
unmount/settle or cancel owned work and restore spies/globals/storage/history even
on failure. StrictMode double setup/cleanup leaves no duplicate subscriptions,
late updates or cross-test state. No concurrent shared-global DOM scenarios.
A route remount with the same live provider differs from a fresh-document reload.

**QA07 independent browser allocation:** verify actual keyboard Tab/Shift+Tab and
activation, visible focus, route/error/dialog focus, announced pending/error states,
English labels and desktop/mobile layout with real viewport/zoom. Record actual
browser/viewport/zoom and observations; reuse FE01's approved responsive design,
not a new numerical breakpoint or conformance claim. Check direct /profile load,
refresh/back/forward/pageshow, no stale personal content, and the absence of Profile
requests in real browser traffic. Inspect only redacted storage/log/network evidence;
do not publish credentials/tokens. Nginx/TLS/deep-link serving, browser-to-real-REST/
PostgreSQL, configured backend authorization and lecturer access require actual
producers/environment and separate evidence. No jsdom/CI result substitutes for
them. Preserve existing Interim manual browser mode and Final Playwright obligations;
no runner configuration, new E2E path or WCAG certification is selected.

### Owners, acceptance and retained gates

Frontend @heizseung and Leader remain the two suites' actual CODEOWNERS; Auth
@duyduyit reviews DTO/credential/session expectations, QA07 @heizseung conducts
independent browser verification under its existing responsibility, and Leader
coordinates evidence review. This is responsibility allocation, not a fabricated
independent person/result. Test authorship and independent verification must be
identified separately in actual evidence.

Accept the specification only with traceability from V1/V2 to the exact CT16
Profile revision, CT01 validation/session and CT16 F2; no contradictory expected
permissions, private fields, endpoint or refresh/revocation behavior. Record actual
head/commands/named cases/failures/skips and distinguish mocks/DOM/browser/security/
PostgreSQL. The six-file inventory is unchanged. Missing implementation/execution
is not a failing executed test and is not acceptance. CT20.I remains unchanged:
Profile is additional capability, not a new Interim completion prerequisite.

**Additional bounded Profile/Auth test specification: technically approved;
APPROVED_PENDING_PROTECTED_INTEGRATION.** Whole CT14/Final remains partially
approved. CT16/CT01 integration, separately authorized implementation, actual
producers, current-head checks, eligible independent CODEOWNER/last-push review
and real QA evidence remain required. No unrelated Final testing contract is
expanded and no tests are created or run by this decision record.

Tool evidence: [jsdom unimplemented navigation/layout](https://github.com/jsdom/jsdom#unimplemented-parts-of-the-web-platform)
confirms why DOM results cannot replace independent real-browser evidence.


## FE03 implementation regression evidence — 25 October 2026

PR #98 implements the approved FE03 allocation in exactly these existing CT14
locations, using the inherited Vitest/jsdom/React toolchain without helper files,
new testing libraries or runner configuration:

- `frontend/src/shared/api/httpClient.test.ts`: 77 transport cases, including
  explicit configuration/origin/namespace boundaries, all HeadersInit forms,
  provider-owned bearer access, fixed credentials/redirect/cache behavior,
  operation status/media/DTO validation, safe actual-status errors, cancellation
  and session replacement while fetch or JSON decoding is unfinished.
- `frontend/src/app/providers/AuthProvider.test.tsx`: 96 real-provider/Auth UI
  cases, including exact Unicode/UTF-8 predicates and password bytes, ASCII email,
  payload/status decoding, login races and replacement, public/stale/current 401,
  403/network retention, timers/resume/pre-send expiry, snapshot invalidation,
  registration without authentication, safe accessible pending/error/focus states
  StrictMode cleanup/unmount cancellation and consumer session-receipt checks
  after login resolution. Five UI continuation regressions were also run against
  the previous committed forms and failed as expected (stale navigation, success
  and errors); final source bytes were restored before the passing full run.
- `frontend/src/routes/index.test.tsx`: 21 real-router/Profile cases, covering the
  approved routes, guard/one-use return behavior, all roles, logout/expiry/401,
  replacement and fresh-provider reload, literal text/three-field presentation,
  no Profile API, browser history within jsdom, heading focus and current FE01
  composition/formatter runtime. DOM history is not real browser delivery evidence.

Commands were executed from the integrated branch's actual `frontend/` directory:

| Command | Observed result |
| --- | --- |
| `npm ci` | PASS: 187 packages added, 188 audited, zero vulnerabilities; npm noted optional fsevents install-script policy. |
| `npm run typecheck` | PASS. |
| `npm test` | PASS: 251 tests across six suites; 194 FE03 plus 57 inherited FE01; zero failures/skips. |
| `VITE_API_BASE_URL=http://localhost:8080 npm run build` | PASS; existing Vite large-chunk advisory remains (about 597 kB minified). |
| `npm run test:browser -- <installed-playwright-module> <evidence-directory> http://127.0.0.1:4173/` | PASS: existing FE01 harness, 31 checks in Chromium 153.0.8010.12. |

Frontend CI now runs the existing `npm test` between installation and build,
without continue-on-error or exit-code suppression. A temporary deliberate
failure in the approved HTTP suite returned npm exit code 1; the original bytes
were restored before the passing suite and commit. Backend, Frontend and CodeQL
contexts, triggers and protections are preserved. CI results belong to the
actual resulting PR HEAD, not historical green checks.

Additional local Chromium verification used controlled Auth responses and the
production preview: 28 browser checks passed for direct entry/refresh,
native Back/Forward, keyboard Tab/Enter and visible focus, named busy status,
associated/focused validation errors, all three Profile roles/plain text,
logout/expiry/one-use return, empty browser storage, Unicode registration,
unknown routes and zero invented Profile requests. Profile and registration
fit 320, 390, 1024 and 1440 CSS-pixel viewports at default zoom; FE01's harness
also exercised its breakpoint, dialog and responsive controls. A discovered Auth
submit focus-outline override was corrected in the authorized form/navigation
composition and verified in Chromium. Visual inspection also caught a mobile
header collision; bounded wrapping and removal of duplicate header identity were
verified with rectangle-intersection checks and screenshots. No screen-reader or WCAG certification is
claimed. Browser fixtures use synthetic users/tokens and are not backend
credentials; redacted local reports/screenshots are review evidence, not a new
committed E2E suite.

The absent main login HTTP producer prevents a real Auth browser/backend run in
this evidence. No actual PostgreSQL, configured backend authorization, lecturer
access, Nginx/TLS or deployed deep-link delivery is established. Independent QA07
and QA01 responsibilities remain; mocked/DOM/build evidence cannot replace them.
AI assistance: Codex assisted the authorized implementation, regression authoring
and local verification; no human approval or review is implied.

## PR #101 local merge verification — 26 October 2026

Historical conflict-only execution, superseded for the current PR by the full-FE07
verification below. The earlier CT22 blocker was valid under the authorization
available then; the later delegated approval is a new decision.

The local resolution combines PR head `8417205b0c5445a1ebace7b8a5f13847c50eddf5`
with `origin/main` at `21088c8cc3ae60cc679ef375e6fbfb7480dc2d68`.
It retains the remediated Auth/session/HTTP implementation and declarative router,
then connects the public catalogue/detail pages to the approved transport and DTOs.
The FE01 presentation preview remains independently covered; the application
entry now renders the public catalogue. No membership route or contract is added.

From `frontend/`, `npm test` passed all 279 tests in nine files. The three existing
CT14-approved catalogue test paths now cover client decoding, paging, cancellation,
list/detail rendering and stale-response suppression; routing tests also exercise
catalogue/detail navigation alongside Auth/Profile and the retained FE01 preview.
These are controlled-fetch/DOM results, not real backend/PostgreSQL, browser or
independent QA acceptance.

`npm run typecheck` and `VITE_API_BASE_URL=http://localhost:8080 npm run build`
fail on four preserved CT22-dependent TypeScript errors: `learningApi.ts` still
supplies caller-owned tokens and `MyCoursesPage.tsx` reads the former public token
property. All three membership source files remain byte-identical to the PR head.
CT22 is deferred, its endpoints are absent from OpenAPI, and the Leader confirmed
no later approved membership contract was identified. Do not weaken the shared
transport, fabricate response/status/transition contracts, or treat this portion
as integration-ready. The local merge remains uncommitted and is not push-ready.
Codex assisted the conflict resolution and local verification.


## PR #101 full FE07 verification — 27 October 2026

Authorization and contract revision: ADR002 `FE07-2026-10-27-r1`, newly approved
under the Leader's explicit delegation, pending protected integration. This work
extends the former frontend-only PR with Course-owned REST/application/persistence
implementation and the missing existing-contract Auth login REST binding. It does
not implement FE08, CT22 progress/resume/atomic or change V001–V006.

### Acceptance evidence

| FE07 Issue #57 criterion | Implementation and executed evidence |
| --- | --- |
| Catalogue/detail match approved fields and visibility | CourseCatalogueController/Service, scoped repository reads, strict frontend decoders; real PostgreSQL REST tests and browser verify published-only courses/lesson headings, omission of empty chapters, instructor name and prerequisites, uniform hidden/missing 404. |
| Search/filter/pagination | Literal q plus prerequisite filter before fixed-order paging/counting; query bounds/unknown/duplicate-key 400 tests; browser exercises filtering and the second page of 21 published fixtures. |
| Approved membership semantics | GET/PUT/DELETE membership singleton, Learner-only filter and application checks, coarse Course-root locking, repeat/leave/rejoin tests, six concurrent joins and six concurrent leaves, transaction rollback. |
| Actual My Courses results | Current-identity, published-only status-filtered page/count; leave removes the course from default Joined and Left filter permits rejoin. Real browser and PostgreSQL evidence. |
| English/responsive/loading/error/validation | Abort/current-session guards, mutation exclusion, confirmation dialog, retry/empty states; DOM tests and Chromium screenshots at 1280/768/390/320px, no horizontal overflow, no browser exceptions. |
| Doubles vs actual runtime distinguished | Vitest uses controlled API/transport fixtures; backend integration uses real shared disposable PostgreSQL and Spring Security/MVC; browser uses production Vite assets, real registration/login/REST and a separate disposable PostgreSQL container. No intercepted endpoints or mock server. |

### Executed commands and results

From `frontend/`:

```bash
npm ci --cache /private/tmp/codemastery-pr101-npm-cache
npm test
npm run typecheck
VITE_API_BASE_URL=http://localhost:8080 npm run build
```

All passed: 302 tests in 11 files, zero TypeScript errors, successful production
bundle. Clean install audited 188 packages with zero vulnerabilities. npm reported
an unapproved optional fsevents install script; no install-script policy was changed.
Vite reported a roughly 640 kB minified entry chunk warning; no dependency or
unrelated bundling change was introduced.

From `backend/`, `./mvnw verify` passed: 451 unit tests and 51 integration tests,
zero failures/errors/skips; 18 integration cases are in EnrollmentIntegrationTest.
The actual EnrollmentService has all 29 executable lines and all 16 branches
covered in the local JaCoCo report; this is execution evidence, not a new threshold.
The first sandboxed focused run failed because Mockito could not attach under
Java 25. Re-execution with sandbox permission succeeded without modifying JVM,
Mockito, Maven or dependency configuration. New test compilation/selector mistakes
were corrected before the final passing runs.

OpenAPI JSON parsing, all 63 local $ref resolutions and eight unique operation IDs
passed a Python standard-library structural check. This is not a full external
OpenAPI-validator certification; strict frontend DTO tests and real REST response
assertions additionally check the consumed contracts.

### Real browser runtime and reproducibility limits

The existing `e2e/tests/learner-flow.spec.ts` now supplies a local Playwright
verification script. It accepts an explicitly installed Playwright module,
frontend URL, backend URL and evidence directory; it adds no dependency, npm
script or CI pipeline. After provisioning its deterministic disposable fixture,
the executed command from the repository root was:

```bash
node --input-type=module - /Users/nguyenchithanh/.npm/_npx/e41f203b7505f1fb/node_modules/playwright http://localhost:5174 http://localhost:8080 /private/tmp/codemastery-pr101-fe07-runtime/evidence < e2e/tests/learner-flow.spec.ts
```

The local orchestrator `/private/tmp/pr101-runtime.py` created a task-owned
`postgres:17.11-bookworm` container, ran the built backend jar and
`npm run preview -- --host localhost --port 5174 --strictPort`, and applied only
disposable test fixtures. The already running localhost:5173 process was preserved.
It generated ephemeral database/JWT/browser credentials, then stopped only its
own processes/container and deleted its credential file. This local orchestration
helper and screenshots are outside Git; the committed browser script expects a
prepared fixture and is not a newly configured portable E2E pipeline.

Fixture: Instructor `00000000-0000-4000-8000-000000000001`, public FE07 Programming
Course `...0002`, Chapter `...0003`, published Lesson `...0004`, hidden Lesson
`...0005`, hidden FE07 Course `...0099`; 20 additional published Courses use UUIDs
`00000000-0000-4000-8001-000000000001` through `...000000000020`, titles Course 01–20.
The primary Course alone has prerequisite text, and its instructor's public name
is FE07 Runtime Instructor. Browser registration generates an independent synthetic
Learner, rather than relying on seeded credentials. Backend integration fixtures
are separately self-contained and cleaned up by the shared test harness.

All seven browser scenarios passed, with 10 authenticated membership requests,
16 public requests without bearer headers, and zero browser errors. Evidence is
local in the directory above: result.json and four membership screenshots. Manual
inspection of the 320px and 1280px images found legible, separated controls. The
browser also checks memory-only storage, guarded direct entry, sign-out, leave
confirmation and row-state-driven rejoin navigation. `/preview` retains the FE01
illustrative UI. Vitest additionally verifies session expiry, role rejection,
cancellation, stale responses and rapid mutation exclusion.

This establishes a local browser → production frontend → REST → PostgreSQL flow,
not deployed Nginx/TLS behavior, independent QA07/QA01 acceptance, a human review,
all-browser/screen-reader certification or formal Issue completion. Required CI,
CODEOWNER/last-push review and protected integration remain independent gates.
Codex assisted the delegated contract selection, implementation, conflict resolution,
automated regression authoring and local execution; no human approval is fabricated.

## PR #101 Round 2 review regressions — 27 October 2026

The reviewed baseline was `37fc2d17e239b4de80980a938a33061602c24c42`.
New PostgreSQL tests reproduced 500 responses for page=107374183,size=20 and
page=2147483647,size=100. The parser already accepted those inputs; no new page
limit, API representation or transaction policy was selected. Oversized offsets
now count the same published/filtered snapshot, return empty content without row
loading beyond its total, and use a bound PostgreSQL long offset if rows exist.
Tests cover ordinary paging, offset boundaries, filtered/zero counts, failure
propagation and the large-offset row-fetch fallback. CourseSearchRequest remains
unchanged; a parser regression explicitly preserves the reported valid request.

`cd backend && ./mvnw verify` passed 458 unit and 58 integration tests, with zero
failures/errors/skips. `npm test` from frontend passed 353 tests in 11 files;
`npm run typecheck` and `VITE_API_BASE_URL=http://localhost:8080 npm run build`
also passed. Existing pagination-recovery, UUID-casing and Auth/session tests remain.
The two affected learning API/type files now own their membership representation
and decoder, using the existing shared HTTP/error primitives. Import-boundary
checks and malformed/missing/extra-field cases protect CT16 compliance; no shared
contract or HTTP transport was added.

The existing browser script now additionally requires the primary published
fixture's description to be exactly `D` repeated 500 times. All other fixture
IDs, counts and credentials remain as described above. The local preparer
`python3 -B /private/tmp/pr101-r2-runtime.py` supplied that disposable fixture and
executed the committed script against production assets, real REST and PostgreSQL.
All nine scenarios passed, including the exact large-page 200 response with
content=[], page=107374183, size=20, totalElements=21 and totalPages=2.

Description layout checks at 320/768/1280px assert text-element client/scroll
widths, full 500-character content and every text Range rectangle, including
vertical containment; hiding/clipping/scrolling the paragraph is rejected.
At 320px the reproduced 5,332px scroll width in a 238px paragraph is now 238px.
Separate before/after measurements confirmed the same overflow in title,
instructor, prerequisites, chapter and lesson text; inherited wrapping contains
all of them. Local result.json, overflow-after.json and description screenshots
are in `/private/tmp/codemastery-pr101-r2-runtime/evidence/`. The runtime was
cleaned up; no credentials, screenshots, generated artifacts or dependencies were
committed. These are local execution results, not independent protected-review
or deployed QA acceptance. Codex assisted the scoped fixes and regression execution.

## PR #101 Round 3 input-validation regressions — 27 October 2026

Reviewed baseline: `95f600258f79634be115b2ad6d7dedcf80696889`. The explicit Round 3
remediation request supplies the NUL rejection requirement; it does not broaden
other character restrictions or change the approved UTF-16 limit/UUID identities.

New PostgreSQL/MVC tests reproduced five NUL searches returning 500 and nine
shortened/signed UUID requests returning 200. Search parsing now rejects NUL
before catalogue SQL. Both Course controllers receive path text and require full
8-4-4-4-12 hexadecimal syntax before UUID conversion/application calls. Protected
requests still pass authentication and role admission first. Tests verify no
application call for malformed IDs, unchanged persisted membership, full upper/
mixed-case identity, and safe hidden/missing 404 responses.

OpenAPI's q schema now documents NUL exclusion and the existing post-trim limit
through `x-max-utf16-code-units: 100`. It intentionally has no JSON Schema
`maxLength`: that keyword does not express this metric. The NUL-only pattern
adds no Unicode length restriction. Real PostgreSQL cases cover ordinary Unicode,
100/101 BMP units, 50/51 emoji, mixed BMP/supplementary input and outer trimming.
Frontend checks connect the OpenAPI extension to HTML maxlength=100 and preserve
supplementary search data; Chromium also verifies an extra emoji cannot exceed it.
ADR002's approved length rule remains unchanged.

Executed successfully: `cd backend && ./mvnw verify` (462 unit + 91 integration
tests, zero failures/errors/skips); from frontend, `npm test` (355 tests/11 files),
`npm run typecheck`, and `VITE_API_BASE_URL=http://localhost:8080 npm run build`.
OpenAPI JSON parsing, all 63 local references/eight operation IDs, the NUL pattern
and UTF-16 extension checks passed; this is a structural/consistency check rather
than an external OpenAPI-validator certification.

`python3 -B /private/tmp/pr101-r3-runtime.py` ran the committed browser script
against production assets, real REST and disposable PostgreSQL. All 12 runtime/
browser scenarios passed, including q=a%00b → safe ProblemDetail 400,
q=50 emoji → 200 / 51 emoji → 400, malformed membership GET/PUT/DELETE → 400 with
canonical membership still absent, anonymous malformed membership → 401, and
valid mixed-case nonexistent identifiers → 404. Earlier catalogue/detail,
large-offset, text-wrapping, membership, Auth and navigation scenarios also passed.
Local evidence is `/private/tmp/codemastery-pr101-r3-runtime/evidence/result.json`;
its temporary runtime/credentials were cleaned up. Independent protected review
and deployed QA acceptance remain separate. Codex assisted these scoped fixes,
contract-documentation correction and regression execution.

## PR #101 Course Card text regression — 27 October 2026

Baseline `b113f218375c320a2091251e857a22573d00259e` reproduced clipping in all
nine title-only, instructor-only and both-long cases at 320/768/1280px. Each long
value contains 100 consecutive W characters. Title text scrolled to 1,552px and
instructor text to 1,251px inside elements only 254/318/273px wide. Two scoped
Typography overflowWrap:anywhere declarations fix the measured defect; no
intrinsic-size override was needed. Description preview styles remain unchanged.

The existing e2e/tests/learner-flow.spec.ts now checks full text, element/card/text
Range rectangles, client/scroll width and height, distinct wrapped lines, absence
of ellipsis/clamping/scrolling workarounds and actual detail navigation in all nine
cases. It also checks that description preview retains its three-line clamp.
All nine cases passed; the both-long measurements were:

| Viewport | Title client/scroll width; lines | Instructor client/scroll width; lines |
| --- | --- | --- |
| 320px | 254/254px; 7 | 254/254px; 6 |
| 768px | 318/318px; 5 | 318/318px; 5 |
| 1280px | 273/273px; 6 | 273/273px; 6 |

The disposable fixture retains 21 published Courses. Course IDs ending in
8001-000000000001/2/3 supply title-only/instructor-only/both-long cases. A second
Instructor (00000000-0000-4000-8000-000000000006) has the long public display name;
only the latter two Courses use it. These valid test-owned rows do not change
backend behavior or contracts. The local preparer is
`python3 -B /private/tmp/pr101-card-runtime.py`; it executes the committed script
against production frontend assets, real REST and disposable PostgreSQL.

All 13 runtime/browser scenarios passed, retaining catalogue/detail, membership,
Auth and previous review coverage. From frontend, npm test passed all 355 tests
in 11 files, npm run typecheck passed and
VITE_API_BASE_URL=http://localhost:8080 npm run build passed with the existing
bundle-size warning. Local course-card-text.json and course-card-320/768/1280.png
are in /private/tmp/codemastery-pr101-card-runtime/evidence/. Task-owned runtime
resources were cleaned up; no generated artifacts, credentials or dependencies
were committed. Independent protected review remains a separate gate. Codex
assisted this scoped styling correction and regression execution.

## PR #101 syllabus accessibility regression — 27 October 2026

On baseline a97df9e, the focused CourseDetailPage regression failed because chapter
summary controls had no IDs. Each summary now supplies stable Course/chapter UUID
IDs and aria-controls; MUI derives the corresponding region ID and aria-labelledby.
No styling, expansion policy or syllabus data behavior changed.

The component regression verifies two chapters, four unique identifiers, correct
region/control relationships and stable associations through toggles/rerender.
The existing learner-flow Chromium script also verifies accessible region names,
Enter/Space collapse and expansion for both chapters, independent state, hidden
collapsed regions, focus retention and stable IDs. Its disposable fixture adds
a second published chapter (ID ending 0007, Practice) and lesson (ending 0008,
Apply concepts) to FE07 Programming; published Course counts remain unchanged.

From frontend/, npm test passed 356 tests in 11 files, npm run typecheck passed,
and VITE_API_BASE_URL=http://localhost:8080 npm run build passed with the existing
bundle-size warning. python3 -B /private/tmp/pr101-accordion-runtime.py executed
the committed e2e/tests/learner-flow.spec.ts against production assets, real REST
and disposable PostgreSQL: all 14 scenarios passed, including prior catalogue,
detail, membership, Auth, navigation and responsive regressions. Local evidence:
/private/tmp/codemastery-pr101-accordion-runtime/evidence/syllabus-accessibility.json
and result.json. Task-owned resources and ephemeral credentials were cleaned up;
no dependencies or generated artifacts were added. These focused checks do not
claim a comprehensive accessibility audit or replace protected review. Codex
assisted this scoped accessibility correction and regression execution.


## SD01/SD02 seed verification — 30 October 2026

Authority: ADR002 `CT15-SD-2026-10-30-r3` (V1 placement, T1–T3, M1–M2). The
operator procedure is [sample-data/README.md](../sample-data/README.md).

### Automated test

`backend/src/test/java/com/codemastery/integration/SampleDataSeedIntegrationTest.java`
runs the committed `01-users.sql` and `02-course-python-fundamentals.sql` with the
real psql 17 inside the existing Testcontainers PostgreSQL container. Each scenario
uses its own new database migrated by Flyway from `classpath:db/migration`. The
test reads the cluster identity from `pg_controldata`, as operators do, and
generates synthetic passwords and `$2b$14$` hashes in memory. It never prints or
commits them.

| Case group | What is asserted |
| --- | --- |
| Fresh load | Exact Users, Courses, Chapters, Lessons and text materials, with every ID re-derived as UUIDv5 from its logical key; instructor references, publication states, positions, adaptive values and NULL Lesson Markdown; exclusive text payloads, no file or video rows, no draft Chapters; every other V001–V006 table empty |
| Identical rerun | Environment and psql-variable inputs both succeed; row values and `xmin` row versions are unchanged |
| Conflicts (9) | Learner email, changed hash, Course ID, Chapter position, Lesson ID, Lesson position, edited material, missing Instructor, Instructor with the wrong role. Each stops with status 3 and leaves the database byte-identical, including rows that sort before the conflict in the same transaction |
| Invalid inputs (9) | Missing, empty or non-numeric target identifier; missing hashes; plaintext, `$2a$`, cost-12 and truncated hashes. Rejected before any write |
| Untrusted targets (14) | Unattested database, wrong expected identifier, attestation for another cluster, unbound attestation, role-scoped attestation, `PGOPTIONS` session impersonation, and a non-superuser denied `pg_control_system()`, each with an inherited READ COMMITTED and REPEATABLE READ default. Both seeds are rejected while the test holds `ACCESS EXCLUSIVE` locks with a 5-second lock timeout, which proves verification precedes locking |
| Concurrency (3) | With an inherited READ COMMITTED, REPEATABLE READ and SERIALIZABLE database default (confirmed in effect for new sessions), two loaders of each file start while the test blocks the seed lock. Both reach the lock after running the guard, then both succeed with exactly one dataset; reruns leave row values and `xmin` versions unchanged |
| Consumers | On the harness database: POST `/api/auth/login` succeeds for both seeded accounts with their roles and display names and returns 401 for a wrong password; the catalogue lists exactly the two published Courses with “Instructor Demo”; detail returns the canonical syllabus; the hidden draft returns 404. Seeded rows and the attestation are removed afterwards |
| Redaction | No output of any run contains a password, hash or salt |
| Failure diagnostics (3) | With sentinel passwords and BCrypt hashes, failed row and snapshot comparisons and failed process checks (wrong exit code, missing error, leaking output) throw errors whose message, string form, stack trace and attached values contain no sentinel, salt, checksum or BCrypt-shaped text, while still naming the context and the differing row |
| Slides | `sample-slides.pdf` matches the recorded SHA-256, has PDF header and trailer markers, contains 12 page objects and records the author “CodeMastery Project Team” |

### Executed commands and results

Executed on 30 October 2026 (ICT) with OpenJDK 25.0.4.1 and Docker 29.8.1, on the
ADR002 r3 revision:

- `cd backend && ./mvnw -B verify`: BUILD SUCCESS. 468 unit tests and 135
  integration tests passed, including 42 `SampleDataSeedIntegrationTest` cases.
- `./mvnw -B verify -Dtest=NoSuchUnitTest -Dsurefire.failIfNoSpecifiedTests=false
  -Dfailsafe.runOrder=reversealphabetical`: BUILD SUCCESS. With the seed test
  ordered before `EnrollmentIntegrationTest` and `CourseCatalogueIntegrationTest`,
  their exact catalogue totals still pass, which confirms that cleanup leaves the
  harness database unchanged.
- The three concurrency cases were repeated three further times; all passed. No
  test container remained afterwards.
- Before the r3 seed fix, the same new test failed under an inherited REPEATABLE
  READ default (`duplicate key value violates unique constraint "pk_users"`) and
  under SERIALIZABLE (`could not serialize access`); READ COMMITTED passed.
  After the one-line fix in each seed, all three pass.
- Disclosure check: with the Learner display name changed temporarily in
  `01-users.sql`, the r2 test's failure report contained two complete BCrypt
  hashes. The r3 test's report for the same defect contained no BCrypt-shaped text
  and showed redaction markers instead. The seed was restored afterwards.

On the r2 revision, `cd frontend && npm ci && npm test && npm run build` succeeded
with 390 tests in 11 files; r3 does not change the frontend. Fault injection on the
r1 revision, reverted afterwards, showed that reading the attestation through
`current_setting()`, locking before verification and removing the SD01 table lock
each made the corresponding cases fail.

The README procedure was executed verbatim in zsh on the r1 revision, against an
isolated Compose project built from this branch with throwaway volumes. Flyway
applied V001–V006 at backend startup, both seeds loaded, and the database check
matched. HTTP sign-in succeeded for both accounts, with 401 for a wrong password.
The catalogue returned the two published Courses, the draft returned 404, and a
seed run after removing the attestation was refused. Earlier rehearsals of the
identity, attestation, hash, load and rerun steps passed in both zsh and bash on
fresh volumes whose migrations were applied by psql instead of the backend. The
procedure's commands are unchanged since; r3 changes only each seed's `BEGIN`
statement, which the automated tests above execute with the same psql 17. The
Compose walkthrough was not repeated on r3. Project containers, volumes, the image
and the synthetic credentials were removed after the walkthrough.

The r2 slides were inspected page by page as rendered images and checked with
`pdfinfo`, `pdffonts`, `pdfimages` and `pdftotext`. For r3, slides 1 and 4 were
inspected as rendered images, the other ten were confirmed pixel-identical to r2 at
90 dpi, and the same tools report 12 pages, PDF 1.4, no encryption, forms or
JavaScript, only the two declared fonts and no extraction errors. The Unicode
identifier examples, the leading-digit rule and the keyword count were checked on
Python 3.12.16.

### Evidence boundaries

These results establish SQL correctness, disposable PostgreSQL loading and actual
Auth/catalogue consumption on local disposable databases. They do not establish
QA13 acceptance on the team's verification environment, frontend sign-in, MinIO
object provisioning or file-material delivery. Those gates remain open. This
implementation and its verification were AI-assisted under the Leader's
delegation; review remains with the CODEOWNERs.
