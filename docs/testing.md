# Testing

**Post-PR231 integration update — 10 November 2026:** ADR008, including CP-030 r2,
is merged; ADR009's two r2 approvals remain pending #233 protected integration.
Batch 4 is **8/8 technically approved**. [ADR009 section 10](decisions/009-ct17-final-runtime-recovery.md#10-post-pr231-integration-and-azure-evidence--10-november-2026)
records the new Leader-attested Azure balance/expiration and unchanged execution
gates. The 9 November status below is historical. Neither rebase/document checks
nor financial attestation adds a runtime PASS: Q07 real RPO/RTO and Q08 full-product
F2/Flyway remain NOT_RUN; Q01–Q06/Q09 retain their exact fixture/mechanism limits.

**10 November 2026 fixture amendment:**
[ADR008 §10](decisions/008-batch-04-contract-approvals.md#10-f1f2-fixture-amendment--10-november-2026)
records CP-030 `CT34-FIXTURES-2026-11-10-r2`. It adds pre-Playwright checkout/output
exclusivity and persistent ownership-safe recovery. Current scripts do not implement
these requirements; the commands below describe the existing implementation.

**Batch 4 contract update — 9 November 2026:**
[ADR008](decisions/008-batch-04-contract-approvals.md) is the authoritative record
for CT34 runner/fixtures/future CI, CT14.coverage's placement and explicit gaps,
and CT39's selection of no shared Clock binding. Six decisions are
**DELEGATED_APPROVED**; the documentary PR still needs independent review, exact-head
checks and protected integration. This records design, not newly executed tests.
CT17's two Batch 4 operational checkpoints remain pending Leader inputs.
Earlier dated records below retain their approval scope and history.

## Purpose

This document describes CodeMastery's current testing contract, based on
[the backend build](../backend/pom.xml), [the frontend manifest](../frontend/package.json),
and the CI workflows: [CI Backend](../.github/workflows/ci-backend.yml),
[CI Frontend](../.github/workflows/ci-frontend.yml) and [CI VM](../.github/workflows/ci-vm.yml).

The sections from [Test Layers and Evidence](#test-layers-and-evidence) through
[Remaining Gaps](#remaining-gaps) describe the current repository. The later dated
sections, from [Authorized Interim verification contracts](#authorized-interim-verification-contracts)
onward, are decision and execution records. They keep the wording of their date.

Backend unit tests, Testcontainers PostgreSQL integration tests and frontend
Vitest/jsdom tests exist for the implemented Auth, Course catalogue and
membership, sample-data and shared REST slices, and run in CI. A local
Playwright Test suite in `e2e/` runs the implemented learner flows against a
disposable stack built from the checkout; it is not run in CI. A passing test
establishes only what that test exercises. It is not acceptance of a verification Issue: QA01 #61, QA02 #62,
QA07 #63, QA13 #64, QA14 #65 and DP02 #66 remain open, and their independent
verification is separate work.

## Test Layers and Evidence

| Layer | Tool and location | Runs in CI | What a pass establishes | What it does not establish |
| --- | --- | --- | --- | --- |
| Backend unit | JUnit Jupiter 6, Mockito; Maven Surefire; module test packages outside `integration/` | Yes (`Backend`) | Isolated service, mapper, adapter, validation, JWT, settings and pure mastery logic | Real PostgreSQL behavior, Spring Security wiring or HTTP serialization |
| Backend integration | Maven Failsafe; `backend/src/test/java/com/codemastery/integration/`; PostgreSQL 17 Testcontainer through `AbstractIntegrationTest` | Yes (`Backend`) | Real Flyway schema, persistence, transactions and concurrency. Auth, Enrollment and sample-data suites dispatch through MockMvc with the configured Spring Security chain; the other suites do not (see [Integration-Test Lifecycle](#integration-test-lifecycle)) | A running server, real browser, network/CORS from a browser, deployment |
| Frontend unit/component | Vitest 5 with jsdom and real React DOM; `frontend/src/**/*.test.ts(x)` | Yes (`Frontend`) | Client, provider, router, page, design-preview and presentation behavior against controlled responses | A real backend, browser layout or assistive-technology output |
| Optional browser harness | `npm run test:browser` (`presentation.browser.cjs`), using the Playwright installed in `e2e/` | No | Chromium/WebKit/Firefox layout at ten widths and 200% zoom, contrast, focus, dialogs, drawer, reduced motion, font loading and preview isolation, with controlled API responses ([interface](#browser-harness-and-e2e-suite)) | Live API integration, screen-reader output |
| E2E stack lifecycle | `node --test fixtures/*.test.mjs` from `e2e/` (`lifecycle.test.mjs`, `run-inventory.test.mjs`, `stack-recovery.test.mjs`, `preview-control.test.mjs`, `local-stack.test.mjs`, `stack-down.test.mjs`); Python 3 (standard library only) and Node 22.18+ or 24; the coordinator tests use disposable fake checkouts and the real per-account registry, the recovery tests an injected fake Docker CLI, the other tests a stub `docker` on `PATH`, a stub Vite build entry point and a stub Vite preview module | No | CT34 r2: exclusive ownership taken and persisted before Playwright starts; same-checkout and path-alias conflicts refused without touching outputs; isolated checkouts concurrent; nonterminal, corrupt, widened-mode or earlier-device-identity records block reuse; a stale or exported hand-down is refused; unsafe output roots refused; birth-identity process classification (PID reuse never signalled); cooperative preview shutdown over the private channel with no PID fallback; no build or Docker removal while a writer lives; endpoint-bound Docker calls and daemon-confirmed absence (a lost daemon socket is never absence; recorded states are re-verified); interrupted-intent reconciliation; volume associations and unaccounted volumes; verified temporary-path removal; bounded idempotent retry; readiness marker and backend diagnostics | Real Docker, Vite, browsers or the E2E flows themselves ([real-Docker record](#fe24-ct34-r2-lifecycle-implementation--13-november-2026)) |
| Playwright E2E | `e2e/` (`@playwright/test` 1.63.0, `playwright.config.ts`, `fixtures/`, `tests/learner-flow.spec.ts`), run locally with `npm test` from `e2e/` and by the non-required `E2E` job of `CI E2E` ([interface](#browser-harness-and-e2e-suite)) | No | Browser → production frontend build → REST → PostgreSQL for the landing page, registration, sign-in, session-ended notice, catalogue, detail, membership, Learner home, My courses, Profile, sign-out, role denial and not-found, on a disposable local stack | Deployed Nginx/TLS behavior, production data, CI; `tests/adaptive-flow.spec.ts` stays empty pending Adaptive contracts |
| VM deployment tests | Six `VM …` CI jobs exercising `deploy.sh`, the VM override, the frontend `release` image and the MinIO initializer image against real containers on the runner | Yes | Script guards, runtime privilege matrix, image identity, HTTPS ingress behavior and MinIO initializer provisioning against disposable MinIO servers on the runner | Any deployment, the real VM or outside-network access |
| Independent verification | QA01/QA02/QA07/QA13/QA14, DP02 and DP03 procedures by their owners | No | Acceptance on the named environment, when recorded | — |

### Backend integration suites

| Suite | Added by | What it exercises |
| --- | --- | --- |
| `AuthorizationIntegrationTest` | QA01 tests, PR #117 | Registration persists a canonical-email Learner with a verifiable `$2b$` cost-14 BCrypt hash; invalid input, caller-supplied role/status and duplicate or concurrent registrations are rejected without changing stored accounts; invalid credentials get a generic 401. DP01 additions: anonymous nondetailed `/actuator/health` UP/503 DOWN, default-deny for the other Actuator and API-documentation paths, invalid/locked-account JWT 401 and Learner-only 403 ([record](#dp01-private-health-probe-verification--1-november-2026)). AU07 additions (PR #239): digest-only issuance and single-use confirmation; identical 202s for unknown/locked/repeated requests, with only eligible accounts issued; delivery after commit without a held User lock; two concurrent confirmations yield exactly one success; rollback when consumption fails; re-check after the User-lock wait; generic 400 for expired, future, locked and reused capabilities; unchanged CT01 bearer validity; a nested context with unconfigured delivery, where health, registration and login work and nothing is issued; and the production 415 for both recovery operations plus the deny-by-default 403 in the full application context (test-only controllers are `@TestComponent`s and are not component-scanned). `AuthServiceTest` adds real SMTP exchanges with an isolated loopback STARTTLS server: delivery only after STARTTLS and successful AUTH, and no message for a server that does not offer AUTH, rejected credentials, missing STARTTLS, or an untrusted or wrongly named certificate. Uses an ephemeral JWT secret. |
| `repository/CourseRepositoryIntegrationTest` | QA02 tests, PR #119 | Course → Chapter → Lesson → LearningMaterial round trip after flush/clear, published-only catalogue filtering, and database constraint and referential-integrity violations |
| `CourseCatalogueIntegrationTest` | CO04/CO05, PRs #96 and #113 | Course access assertions bound to real Auth account checks, catalogue ordering and pagination, hidden-content concealment and snapshot consistency under concurrent publication changes |
| `EnrollmentIntegrationTest` | FE07, PR #101; CO08, PR #248 | Catalogue search/filter/paging validation, login binding and JWT, role and authentication precedence, hidden-versus-missing concealment, membership join/leave idempotency and concurrency, rollback, My Courses and CORS for the membership methods. CO08 additions: V009 column shape; CT22 progress calculation, idempotent completion, completion reconciliation and zero-Lesson Courses; admission precedence and concealment without writes; the ACTIVE-only progress summary; per-Course and account-level resume; rollback of progress, status, pointer and visit time together; concurrent completion; Course-root serialization against leave and publication ([record](#co08-progress-completion-and-resume-verification--17-november-2026)) |
| `SampleDataSeedIntegrationTest` | SD01/SD02, PR #116 | Seed loading, reruns, conflicts, target attestation and its consumers ([record](#sd01sd02-seed-verification--30-october-2026)) |
| `SharedRestInfrastructureIntegrationTest` | SH01, PR #78 | Spring MVC ProblemDetail, validation and paging with test-only routes; it does not start PostgreSQL or the security chain |

`AdaptiveLearningFlowIntegrationTest.java` and
`MasteryProgressTransactionIntegrationTest.java` are empty scaffolds.

Merged test code does not complete QA01 or QA02: each Issue's own acceptance
criteria, independent review and any environment-specific evidence remain open.

### AU07 SMTP diagnostic and negative regression coverage

`AuthServiceTest$SmtpSubmissionPolicy` uses synthetic credentials and an isolated
loopback STARTTLS server. Its 72 diagnostic exchanges cover LOGIN, PLAIN and a
test-only NTLM override at DEBUG/TRACE for success, rejected authentication and
rejection after DATA. Capture paths are raw JUL, an explicit JUL-to-SLF4J bridge,
direct Session output, and actual Spring Boot initialization in fresh JVMs. Boot's
logging API and JUL levels are raised after sender construction. Forked JVMs keep
Boot's logging lifecycle out of other test contexts; captured streams, handlers,
levels and filters are restored, and temporary process output is deleted.

Production permits only `LOGIN PLAIN` over mandatory certificate/hostname-verified
STARTTLS and explicitly disables SASL. NTLM, DIGEST-MD5, XOAUTH2 and unknown-only
offers fail before AUTH or message submission. NTLM is deliberately enabled only
inside the diagnostic test to exercise the second defense independently of the
production allowlist. The server decodes the UTF-16LE username and verifies the
NTLMv2 proof against its synthetic secret. Tests inspect actual exchanged bytes,
including PLAIN's `username\0username\0password` authorization identity, LOGIN
credentials, and NTLM messages/responses in Base64 and hexadecimal form. Positive
leak controls prove encoded identity and response detection works; independent
JUL, SLF4J and stream controls prove ordinary application logging remains available.
No reset URL/capability, credential, recipient, sender or sensitive payload is
observed in these supported logging configurations.

The diagnostic-source inventory was checked against resolved Angus Mail 2.0.5:

| Source | Relevant path and policy |
| --- | --- |
| `org.eclipse.angus.mail.smtp` | SMTP transport, LOGIN/PLAIN, DIGEST-MD5 and the SMTP SASL helper; source-level reject-all filter |
| `org.eclipse.angus.mail.smtp.protocol` | SMTP commands, replies and message data; separate source-level reject-all filter |
| `org.eclipse.angus.mail.auth` | NTLM Type 1/2/3 helper diagnostics; source-level reject-all filter even in the test-only NTLM override |
| `org.eclipse.angus.mail.util.socket` | Socket/TLS diagnostics; source-level reject-all filter |
| Jakarta Mail Session debug | Disabled on every sender Session access with a discarded debug stream |

The four named JUL filters are JVM-wide and strongly retained. They do not filter
application loggers. The fixed sender exposes no `spring.mail.*` binding, alternate
provider or SASL configuration input. Provider exceptions can contain sensitive
details in memory; the recovery worker catches them and records only its aggregate
failure counter. These checks do not claim confidentiality under arbitrary
in-process replacement of the fixed transport or its logging filters.

Negative exchanges include incompatible-only AUTH, a reserved non-listening
loopback endpoint (connection refusal or connection timeout, depending on the OS),
and stalls at the greeting and post-STARTTLS AUTH response. The stall tests retain
the production 5000 ms read deadline and assert a timeout with no message
submission. Connection/read/write timeout settings remain asserted at 5000 ms;
a write-buffer stall is not forced for the small reset message. Existing STARTTLS,
trust, hostname and rejected-credential tests remain. No real provider or deployed
qualification is implied.

### Latest recorded results

| Date (ICT) | Revision | Where | Command | Result |
| --- | --- | --- | --- | --- |
| 31 October 2026 | `main` `614f326` | GitHub Actions CI [run 41191743079](https://github.com/chi-thanh-nguyen/codemastery/actions/runs/41191743079); CodeQL [run 41191742751](https://github.com/chi-thanh-nguyen/codemastery/actions/runs/41191742751) | All CI jobs; CodeQL default setup | All seven CI jobs and CodeQL passed. Backend: Surefire 468 tests, Failsafe 150 tests, 0 failures, errors or skips. Frontend: 390 tests in 11 files, then a successful build |
| 31 October 2026 | `main` `614f326` | Local, macOS 27.2 arm64, OpenJDK 25.0.4.1, Docker 29.8.1, Node 24.21.0, npm 11.19.0 | `cd backend && ./mvnw -B verify` | BUILD SUCCESS; Surefire 468, Failsafe 150, 0 failures, errors or skips |
| 31 October 2026 | `main` `614f326` | Same machine | `cd frontend && npm ci && npm test && npm run build` | 390 tests in 11 files passed; build succeeded |
| 6 November 2026 (activation) | `feat/uiux-design-r1-implementation`, production-activation commit after `17cc444` | Same machine | Same commands | 422 tests in 15 files passed; build succeeded. Harness: Chromium and WebKit 34/34 each; Firefox did not launch in the agent sandbox. E2E: 53 passed, 4 skipped by design, Firefox 13 failed at browser launch ([record](#production-activation-of-eligible-uiux-components--6-november-2026)) |
| 6 November 2026 | `feat/uiux-design-r1-implementation` at `b2e9035` (code; base `061102e`) | Local, macOS 27.2 arm64, Node 24.21.0, npm 11.19.0, Docker 29.8.1 | `cd frontend && npm test && npm run build`; `npm run test:browser -- --engine all`; `cd e2e && npm ci && npx playwright test` | 414 tests in 15 files passed; build succeeded. Harness: Chromium 153 and WebKit 26.6 30/30 each; Firefox did not launch in the agent sandbox. E2E: 41 passed, 4 skipped by design, Firefox 10 failed at browser launch ([record](#uiux-r1-implementation-verification--6-november-2026)) |

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
initializes its datasource. The approved image is built from
`infrastructure/postgres/Dockerfile` (PostgreSQL 17.11 Alpine 3.24 with
`su-exec` replacing `gosu`; see [ADR003](decisions/003-postgresql-alpine-su-exec-image.md)
and [deployment.md](deployment.md#dp01-postgresql-alpinesu-exec-image-hardening--2-november-2026))
through Testcontainers' `ImageFromDockerfile`, so no developer-specific local
tag is required; this selects an integration-test database only, not the
production/deployment PostgreSQL version. No persistent container reuse is
enabled. The singleton is not managed by per-class `@Container` callbacks.

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
foundation, a running Docker Engine able to build
`infrastructure/postgres/Dockerfile` is required. Compose and a manually
provisioned PostgreSQL service are not required.

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
The optional browser harness is not run by CI; the E2E suite runs in the separate, non-required `CI E2E` workflow ([CI](#ci)).
Build success alone does not establish behavioral coverage or deployment readiness.

### Browser harness and E2E suite

Both use the Playwright Test package pinned in `e2e/package.json` (1.63.0, matching
the Chromium 153, Firefox 155 and WebKit 26.6 builds it expects). Install it once;
`npm ci` never downloads browsers, so install the pinned builds when missing:

```bash
cd e2e
npm ci
npx playwright install chromium firefox webkit   # only when the browsers are missing
```

**Browser harness** (from `frontend/`). Without `--url` it builds the frontend with
`VITE_API_BASE_URL=/api` into a temporary directory, writes a per-run marker into
that copy's `index.html`, serves it with `vite preview` on a free 127.0.0.1 port
chosen right before the preview starts, runs the checks and removes both. It is
ready only when the port answers with that marker while the preview process is
still running, so another server on the same port is never tested. With `--url`
no build is made and no marker is required. API responses are
`page.route` test doubles; no backend is contacted.

```bash
npm run test:browser                                   # Chromium
npm run test:browser -- --engine all                   # chromium, webkit, firefox
npm run test:browser -- --url <running-frontend-url> --out <evidence-dir> --engine webkit
npm run test:browser -- --playwright <path-to-@playwright/test>
```

It visits every `/preview` screen at 1280 and 390 px, the live pages and three
preview screens at 320, 360, 600, 768, 900, 1024, 1200, 1280, 1440 and 1536 px,
200% zoom (640 CSS px at 2×) and 200% text size, and checks: no page-level
horizontal scroll; the navigation mode per breakpoint; Google Sans Flex, JetBrains
Mono and the wordmark face served from the app with their OFL texts, Vietnamese,
code and wordmark text rendered without fallback glyphs (Chromium font inspection),
and the logo's colour regions distinct on the ink frame at 16–40 px; the active-tab
reach centred, symmetric and following clicks, child routes and history; production routes never loading the preview chunk; the
preview sending no API request and writing no storage; computed text contrast on
twelve screens; skip link, route focus and visible focus on every catalogue tab
stop; drawer and dialog focus behavior including the full-screen sheet below
600 px; form labels, required markers and error focus; `pagehide` input blur;
catalogue submit-only search and error states; the 1.2 s skeleton shimmer and its
reduced-motion static form; the landing page's public navigation, example-map chunk
and absence of API requests; Learner home and My courses tile membership actions;
and the Mastery Map's 220 × 72 nodes, initial viewport and keyboard operation. It writes
screenshots, accessibility snapshots and `browser-report.json` to `--out`
(default: a new directory under the OS temporary directory), and exits non-zero
on any failed check or browser that cannot launch. Positional FE01 arguments
(`<playwright-module> <evidence-dir> <url> [engine]`) are still accepted.

**E2E suite** (from `e2e/`):

```bash
npm test                         # all five projects
npm run test:chromium            # chromium-desktop and chromium-mobile
npm test -- --grep "<title>"     # extra Playwright arguments go after --
npm run stack:down               # reconcile and recover an interrupted run (same lock and inventory)
npm run report                   # open the last HTML report (read-only; needs no ownership)
python3 fixtures/lifecycle.py inspect  # print this checkout's run record(s) (read-only)
node --test fixtures/*.test.mjs  # lifecycle regression tests (no real Docker, Vite or browser)
```

**Run lifecycle (`CT34-FIXTURES-2026-11-10-r2`).** `npm test`, `npm run test:chromium`
and `npm run stack:down` enter through `fixtures/lifecycle.py` (Python 3, standard
library only), which takes exclusive ownership of the canonical `e2e/` directory and of
the three output roots (`test-results/artifacts`, `test-results/results.json`,
`test-results/html`) and persists the run record **before** the Playwright CLI starts.
Ownership is a nonblocking `flock` on a stable lock file in the private registry
`~/.codemastery-e2e/` (mode 0700, files 0600, `~` resolved from the effective UID, not
`HOME`), keyed by the checkout directory's device/inode, so path aliases contend for the
same lock and separate checkouts run concurrently. A second invocation of the same
checkout exits 2 without starting anything; an unresolved previous run blocks a new one
(exit 3) until `npm run stack:down` verifies its recovery; an unsafe registry, output
root or filesystem (including one whose type cannot be determined) is refused (exit 4).
A record left under an earlier device number of the same checkout (same path and inode;
macOS can renumber an APFS volume at boot) also blocks a new run, and `npm run stack:down`
reconciles it under its own lock first. A direct `npx playwright test` is refused by
`playwright.config.ts` before Playwright touches any output, and so is one that merely
carries exported lifecycle variables: the Playwright CLI must be the child of the
coordinator named in this checkout's active run record. `show-report`, `install`,
`--version` and `--help` remain available. The run inventory records every process with
its OS birth identity: the coordinator writes the runner's creation intent into the record
that admits the run, before the runner is spawned, and its PID, birth identity and process
group right after the spawn; global setup records the builder and the preview before it
spawns them. The runner never records itself, and global setup creates nothing until the
coordinator has recorded this exact process as the runner. The inventory also holds the pinned Docker
endpoint, context and daemon ID, each creation intent before the resource exists
(containers with a pending volume association), the observed container, network and
anonymous-volume IDs, and the temporary build's identity and run marker; it never records
credentials. Every Docker call of a run is bound to the pinned endpoint through
`DOCKER_HOST`. Normal teardown, the coordinator's finalization after the runner exits and
`npm run stack:down` all use the same engine (`fixtures/stack-recovery.mjs`): it stops an
orphaned preview only by asking it over its private Unix-domain control channel (no PID
is ever signalled), touches neither the temporary build nor any Docker resource while a
recorded writer is not verified as exited, counts a resource as absent only on Docker's
own not-found answer from the recorded daemon (a lost daemon socket, a timeout or another
daemon is inconclusive), never removes a container whose volume association is still
unresolved (a failed mount inspection keeps it for the next pass), re-inspects every recorded ID on every pass, removes only
resources the inventory proves are this run's (a volume only when it was recorded from this
run's container and Docker marks it anonymous), and exits nonzero until the run is
complete. A container that vanished before its volume association was recorded leaves an
unaccounted volume and an incomplete run. Resources of no current inventory (pre-r2
PID-label leftovers, other accounts' runs) are reported, never removed, and do not block
this run. The runner is started in a session of its own, so it leads a process group
holding every process it starts (Playwright workers, the build and the preview). After the
runner exits, the coordinator waits (at most 45 s, without signalling anything) for that
group to empty before it finalizes, and finalization and `npm run stack:down` touch neither
the build nor the stack while a live member remains: a disconnected Playwright 1.63 worker
can keep writing `test-results/artifacts` for up to 30 s while it finishes. The group is
checked before the preview is asked to close (the verified preview, matched by PID and birth
identity, is the only member allowed to remain at that point) and again, in full, afterwards.
A runner record without a usable group identity, an enumeration that is incomplete (on Linux
any `/proc` read error other than the process having gone, such as `EACCES`, `EPERM` or
`EIO`), or a record from another boot never counts as an empty group. A run is complete
only after the coordinator has also verified the runner's own exit.

Every change of a run record is serialized by a second stable lock file next to the record
(`runs/.<key>.json.lock`) and is a fenced compare-and-swap: inside that short, bounded
critical section the current record is re-read and the writer's handed-down run ID, lock
key, `fence` (ownership generation) and last-read `revision` must still match it; the write
then increments `revision`. The coordinator makes its own changes this way, and Node
writers (global setup, teardown and `stack:down`) commit through the coordinator's
`commit-record` helper. Each recovery takes the record over by incrementing `fence`, so a
writer of an earlier generation is refused at its next write however long it was paused,
and a later run has a new run ID. Finalizers are recorded writers like the runner (intent
before the spawn, PID and birth identity after it), so recovery stays incomplete, and the
checkout stays blocked, while an earlier finalizer may still be alive.

An incomplete record that recovery cannot resolve (for example processes recorded in a
previous boot, which need explicit reconciliation) stays in `~/.codemastery-e2e/runs/`.
Inspect it with `python3 fixtures/lifecycle.py inspect`, reconcile the listed resources by
hand, and only then move the record out of the registry. A record recovery cannot
interpret (corrupt or malformed) blocks reuse in the same way; another checkout's malformed
record is skipped and blocks nothing. `E2E_PYTHON`, `E2E_VITE_BIN` and `E2E_VITE_MODULE` are
test hooks used only by the fixture tests (an alternative Python, a stub Vite build entry
point and a stub Vite preview module). The coordinator never hands `E2E_VITE_BIN` or
`E2E_VITE_MODULE` to a run, and it sets `E2E_PYTHON` to its own interpreter for the run.

`fixtures/local-stack.ts` (Playwright global setup) needs Docker. It builds
`codemastery-e2e/backend:co-<dev>-<ino>` and `codemastery-e2e/postgres:co-<dev>-<ino>`
(per-checkout layer-cache tags named by the checkout's lock key) from
`backend/` and `infrastructure/postgres/`, starts them on a
private network with random database, JWT and storage values passed through the
environment, builds the production frontend pointed at that backend and serves it
through the pinned Vite preview API (`fixtures/preview-server.mjs`), waits for `/actuator/health`, registers an Instructor through
the API and promotes that account in the disposable database only, and applies
`fixtures/catalogue-fixture.sql` (21 published courses, one hidden course, hidden
lessons and an empty chapter). Containers run the exact image IDs this run built, and
another checkout builds under its own tags, so a concurrent run elsewhere cannot swap or
delete them. The two loopback ports are distinct. The frontend port is chosen right before
`vite preview` starts, and the backend starts afterwards because it needs that
origin for CORS. The temporary build carries a per-run marker in its `index.html`:
the preview counts as ready only when the port answers with that marker while the
preview process is still running, and it is checked again before the tests start.
A port collision therefore fails setup (at once with Vite's message if the preview
exits) instead of testing another server. If the backend container stops while
setup waits for health, setup fails at once; on that or on the 180 s timeout the
error carries the container state (status, exit code, OOM, error) and its last 100
log lines, with the generated secrets redacted, captured before the teardown removes
the container. Containers and the network carry the run and owner labels, which
only corroborate the IDs in the run inventory; the teardown stops the preview over its
control channel and removes exactly the recorded containers, their anonymous volume,
the network and the temporary directory, as described above. Each test registers its own synthetic Learner,
so tests are independent. Projects: `chromium-desktop`, `firefox-desktop`,
`webkit-desktop` (1280 × 900), `chromium-mobile` (Pixel 7) and `webkit-mobile`
(iPhone 14). Results go to `e2e/test-results/` (ignored), including
`results.json` and an HTML report.

To target an already running environment instead, set `E2E_BASE_URL` (and
`E2E_API_URL` when the API has another origin); fixture-dependent tests are then
skipped, and the Instructor check needs `E2E_USERNAME`/`E2E_PASSWORD` for an
existing synthetic Instructor. `E2E_WORKERS`, `E2E_BACKEND_IMAGE` and
`E2E_POSTGRES_IMAGE` are optional tooling overrides. Never point the suite at
production: it registers accounts and changes memberships.

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
| `src/shared/components/presentation.browser.cjs` | Optional Chromium checks, screenshots, accessibility tree, computed contrast, focus, dialogs, drawers, overflow and reduced motion. Its contents were replaced on 6 November 2026 ([current interface](#browser-harness-and-e2e-suite)). |

**Superseded harness interface (6 November 2026).** The paragraph after the
timezone commands records the FE01 harness as it was. That harness and its modes
(`--standalone`, `--test-catalogue-filter`, `--test-auth-focus`, `--test-api-errors`,
`--test-chart-escape`) targeted the retired FE01 preview; the UI/UX r1 record below
replaces them and maps their retained checks.

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

Three GitHub Actions workflows, `CI Backend` ([ci-backend.yml](../.github/workflows/ci-backend.yml)),
`CI Frontend` ([ci-frontend.yml](../.github/workflows/ci-frontend.yml)) and `CI VM`
([ci-vm.yml](../.github/workflows/ci-vm.yml)), each run on pull requests targeting
`main`, pushes to `main`, and manual `workflow_dispatch`, with `contents: read`
permissions. `CI Backend` holds the Backend job, `CI Frontend` the Frontend job and
`CI VM` the six VM jobs. They replace the earlier single `CI` workflow (`ci.yml`)
without changing any job ID, display name, step or trigger
([record](repository-structure.md#bounded-ci-workflow-inventory-replacement--12-november-2026)).
Together they define eight independent jobs on `ubuntu-24.04`:

| Job display name (ID) | What it runs |
| --- | --- |
| Backend (`backend`) | Temurin 25; `./mvnw verify` from `backend/`, including the Testcontainers integration tests on the runner's Docker |
| Frontend (`frontend`) | Node 22; `npm ci`, `npm test`, then `npm run build` from `frontend/` |
| VM deployment script (`deployment-script`) | Checks of `infrastructure/vm/deploy.sh` without starting the application: shell syntax, authorization gates, Compose and provisioning guards, the runtime privilege matrix, env-file custody, preflight against rendered Compose, release-archive identity models and image identity through a real `docker save`/`load` round trip |
| VM deployment PostgreSQL and containment tests (`deployment-script-postgres`) | The runtime privilege matrix on the real migrated schema, failure containment and published-port verification against real containers |
| VM runtime privileges with the real backend (`deployment-script-runtime`) | Builds the backend image and exercises real Auth, catalogue and membership under the approved privilege matrix |
| VM deployment containerd image store (`deployment-script-containerd`) | Release-archive verification through real `docker save`/`load` round trips with the containerd image store |
| VM HTTPS ingress (`deployment-ingress`) | Builds the release and local frontend images; exercises the HTTPS ingress against a stub and the real backend; runs `deploy.sh preflight` with the committed override |
| VM MinIO initializer qualification (`minio-initializer`) | Pulls the digest-pinned Golithus server, `mc` (the suite's test client) and Chainguard candidate server images, builds `infrastructure/minio` for linux/amd64 on its digest-pinned Chainguard `minio-client` base (pulled by the build) and runs `infrastructure/minio/test-minio-init.sh` against disposable MinIO servers and the effective VM Compose model, first with the Golithus server and then with `MINIO_INIT_TEST_SERVER=chainguard`. It then builds `infrastructure/vm/Dockerfile.minio-server` for linux/amd64 as the local non-release tag `codemastery-test/minio-server:qualification`, without pushing it, and runs the suite with `MINIO_INIT_TEST_SERVER=minimal`. The later suites run even when an earlier suite fails, provided the pull, and for the minimal suite its build, succeeded; any failure fails the job ([record](deployment.md#dp01-minio-community-initializer--31-october-2026), [candidate](deployment.md#dp01-chainguard-minio-release-server-candidate--1-november-2026), [minimal](deployment.md#dp01-minimal-minio-server-candidate--1-november-2026)) |

The Java setup enables Maven caching using `backend/pom.xml` and
`backend/.mvn/wrapper/maven-wrapper.properties` as dependency inputs. The Node
setup enables npm caching keyed by `frontend/package-lock.json`. Caches do not
replace installation or validation. CodeQL code scanning runs separately through
GitHub's default setup, with no workflow file in this repository.

Eighteen VM steps run a script from `infrastructure/vm/tests/` instead of an
inline block: the step runs `exec bash infrastructure/vm/tests/<script>` from the
repository root, and the script holds that step's former inline commands
unchanged. `exec` makes the script the step's own process, as the inline block
was, so a signal sent to the step on cancellation reaches the script and its
cleanup trap directly. The step-to-script mapping is in the
[inventory record](repository-structure.md#bounded-vm-ci-test-script-inventory-addition--12-november-2026).
The remaining short steps stay inline. The `deployment-script` job first runs
`bash -n` on every script in that directory. To run one script locally, run
`bash infrastructure/vm/tests/<script>` from the repository root. The scripts
assume the runner's environment: most use Docker, and four use `sudo`.

The `main-quality-gates` repository ruleset requires `CodeQL` and every job above
to pass before merging into `main`. A later read of the GitHub API on 1 November
2026 lists “VM MinIO initializer qualification” as required too; an earlier read
that day did not. That job therefore keeps its single check name. The VM jobs run only on the
CI runner: they deploy nothing and do not reach the real VM. No job starts the
full local Compose stack. The VM jobs start their containers with `docker run`;
only the MinIO initializer job uses Compose, starting just the `minio` and
`minio-init` services (and a placeholder backend under the VM override) in
disposable projects. A fourth workflow, `CI E2E` (described below), runs the E2E suite and is
not a required check. No workflow uploads artifacts. Workflow configuration does not prove a successful run; see
[Latest recorded results](#latest-recorded-results).

`CI E2E` ([ci-e2e.yml](../.github/workflows/ci-e2e.yml)) has one job, `E2E` (`e2e`), on
`ubuntu-24.04` with Node 24, Docker and `contents: read`, triggered like the other
workflows. It implements the SH07 contract `CT34-CI-2026-11-09-r1`
([ADR008 §7](decisions/008-batch-04-contract-approvals.md#7-ct34ci)). From `frontend/` it runs
`npm ci`; from `e2e/` it runs `npm ci`, then
`npx playwright install --with-deps chromium firefox webkit` (the browser builds pinned by
the lockfile), then `node --test fixtures/*.test.mjs`, then `E2E_WORKERS=1 npm test` with
`E2E_BASE_URL`, `E2E_API_URL`, `E2E_BACKEND_IMAGE`, `E2E_POSTGRES_IMAGE`,
`E2E_USERNAME` and `E2E_PASSWORD` unset. The suite therefore runs all five projects
against the run's own disposable stack, through the r2 lifecycle coordinator, with no
retries and no `continue-on-error`. An `always()` step then runs `npm run stack:down`. That step
reconciles the run inventory after the test process has ended and fails the job on its own
when recovery is incomplete. It never prunes Docker or removes a resource the inventory does
not prove belongs to the run. A test, setup, browser-launch or cleanup failure fails the job.
On a step timeout or cancellation the Actions runner signals only the step's root process
(SIGINT, then SIGTERM after 7.5 s, then a kill of that process alone after 2.5 s more). The
suite step therefore `exec`s `npm test` with npm's script shell set to bash
(`npm_config_script_shell=bash`): npm forwards the signal to its script shell, bash runs the
single script command in place (Ubuntu's `/bin/sh`, dash, does not), and so the lifecycle
coordinator receives it, forwards it to its runner and finalizes the run. Without this the
coordinator received nothing and kept running the suite after the step had ended. The
coordinator can still outlive the step while it finishes its teardown, so the recovery step
retries `npm run stack:down` only while it exits 2 (another invocation owns the checkout;
nothing was touched), at most 48 times 5 seconds; any other result, including incomplete
recovery, is final.
Step budgets (minutes): frontend install 5, E2E install 3, browsers 40, lifecycle tests 5,
suite 25, recovery 8, summary 2. Their sum (88) leaves 12 minutes of the 100-minute job limit
for checkout, Node setup, post steps and termination. They are sized from the first measured
run (browsers 66 s, lifecycle tests 33 s, suite 5.2 min including cold image builds, recovery
1 s, job about 7 min) with headroom for cold dependency downloads, and the recovery budget
covers bounded Docker retries against a slow daemon. The browsers budget was raised from 10
minutes after five attempts on 18 November 2026 timed out in that step before any test ran.
Each was downloading the same 125 MB of Ubuntu packages (181 new) that Playwright's
`--with-deps` installs, from the runner's `us-east-2.ec2.archive.ubuntu.com` mirror, at only
0.07–0.13 MB/s, which projects 16–31 minutes for the download alone. Sampled runs on healthy
mirrors fetched the same packages in 9–17 s, and the whole step took 37–97 s in all 24
attempts where it succeeded between 12 and 18 November. Forty minutes covers the slowest observed rate with margin and still
bounds a stalled install. A timed-out suite step still leaves recovery and
summary their own budgets. An `always()` step is best effort and cannot run after a forced
loss of the runner. The final step writes an informational job summary: tested commit and
pull request head, runner OS, Node, npm, Python and Docker versions, the lockfile hash, the
pinned Playwright and browser revisions, the run ID, the final inventory state, the image IDs
and per-resource outcomes, per-project pass/fail/flaky/skip counts, and the titles and skip
reasons of non-passing tests. It copies no error messages, traces, screenshots, request data,
storage state or logs, because those can contain the run's synthetic credentials and session
material. Raw reports are not uploaded. Making `E2E` a required check is a separate Project
Leader decision. A green run is infrastructure evidence, not CT19/CT35/CT20.F Final coverage
acceptance.

The Backend and Frontend jobs end with an informational evidence step that writes
to the run's job summary unless the run was cancelled, including after a failed
step. Both record the checked-out commit (the tested merge commit on pull
requests), the pull request head SHA and the event and ref. Backend adds
test, failure, error and skip counts from the Surefire and Failsafe XML reports
plus the names of failed or erroring test cases, written as literal code spans
so that a name cannot change the summary's formatting. It copies no test output
or failure messages. Frontend adds the Node and npm versions and the outcome
(`success`, `failure` or `skipped`) of its setup, install, test and build steps.
`npm test` writes no machine-readable report, so its counts stay in the step
log; the summary points there only when the test step ran and says so when it
was skipped. These steps cannot fail the job: a summary error becomes a warning,
and the earlier steps remain the gate.

## Remaining Gaps

The following are not covered by any executable test in the repository. No
test-count or coverage-percentage claim is made for them.

- **Empty scaffolds.** Assessment, Interaction and Admin unit tests; Adaptive
  decision, evaluation, listener, settings, mastery-update, placement and skill-graph
  tests; `LessonProgressServiceTest`; `AdaptiveLearningFlowIntegrationTest`;
  `MasteryProgressTransactionIntegrationTest`; `ScenarioLoader`, `TestClockConfig`,
  `SkillGraphFixtures` and the three adaptive scenario JSON files. They wait for
  their features and approved contracts.
- **E2E.** The Playwright suite exists but has no CI job; it runs locally with
  Docker. `e2e/tests/adaptive-flow.spec.ts` is empty until Adaptive, Assessment and
  lesson-content contracts exist. Firefox has not yet completed a recorded run
  (see the 6 November 2026 record).
- **Real-browser and runtime integration.** No CI job drives a real browser
  against a running backend and PostgreSQL. Local runs are recorded in the dated
  sections below; QA07 and DP02 acceptance are open. Group B/C design-preview
  screens have no backend to integrate with yet.
- **Deployment.** No test reaches the real VM or checks outside-network access
  (DP01 #71, DP03 #72). The private `/actuator/health` probe is verified only
  locally and in CI ([record](#dp01-private-health-probe-verification--1-november-2026)).
- **Object storage.** No backend storage adapter exists, so no test covers file
  upload or delivery (CT09).
- **Assistive technology.** No test establishes actual screen-reader output; no
  manual VoiceOver, NVDA or TalkBack session has been recorded.

Coverage threshold enforcement is not configured; any future enforcement
requires an approved decision.

## CT17 Final recovery qualification handoff — 9 November 2026

**Current update:** the following r1 NOT_RUN statements are historical. Executed
mechanism-level results and still-unexecuted operational/product obligations are
recorded in [ADR009 §9.6](decisions/009-ct17-final-runtime-recovery.md#96-actual-qualification-record--10-november-2026)
and the reproduction section below. CP-034 r2 is technically approved; no blanket
Q01–Q09, full-product F2 or production readiness PASS is asserted.

[ADR009 Q01–Q09](decisions/009-ct17-final-runtime-recovery.md#71-reproducible-qualification-plan--not_run)
defines the remaining actual-resource, coordinated-cut, PostgreSQL/MinIO complete
restore, encryption/key/provenance, publication/retention, RPO/RTO/resource and
product/security evidence. This is **NOT_RUN** planning, not a new test file,
framework, infrastructure grant or completed approval-level round trip. Existing
CT17-FINAL F1/F2, class B mechanism/compatibility gates, test inventories and
feature-specific prerequisites remain effective.

Read-only checks obtained public DNS and verified local historical release
checksums/OCI mappings; SSH/HTTPS/TLS access attempts timed out. No live VM,
certificate, resource capacity, backup, key recovery, encryption or restore test
passed. Public retail price retrieval supports estimates only. The selected
RPO 24h/RTO 48h and 8h capture cadence are future design targets, not measured
guarantees. Full MinIO/crypto and actual product qualification still block whole
CP-034 approval; actual bindings still block whole CP-033 approval.

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
[LEADER_APPROVED](decisions/README.md#approval-status-labels).** The Project Leader explicitly delegates
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
stays LEADER_APPROVED, with #102's dependency explicit.
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
LEADER_APPROVED.** Under the Leader's explicit
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
LEADER_APPROVED.** Under the Leader's explicit
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
LEADER_APPROVED.** Whole CT14/Final remains partially
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

## DP01 private health probe verification — 1 November 2026

Scope: the change in
[DP01 private health probe remediation](deployment.md#dp01-private-health-probe-remediation--1-november-2026).
Run on macOS arm64 with Docker Engine 29.8.1 and OpenJDK 25.0.4.1, from the
`fix/dp01-release-readiness` working tree based on `main` at `204483c`.

| Check | Command | Result |
| --- | --- | --- |
| Reproduction before the change | Prod-profile backend image from `204483c` with PostgreSQL 17.11 on a private network; `wget -S -O - http://backend:8080/actuator/health` from `nginx-unprivileged:1.30.5-alpine` | 401 `application/problem+json`, `WWW-Authenticate: Bearer`, from `RestSecurityErrorHandler` |
| Focused suite | `./mvnw -Dtest=AuthorizationIntegrationTest test` | 28 tests, 0 failures |
| Regression detection | The same two health tests with the new rule removed | Both fail: 401 instead of 200/503 |
| Full backend | `./mvnw -B verify` | BUILD SUCCESS; 468 + 167 tests, 0 failures, errors or skips |
| Real HTTP after the change | Same topology, image built from the change | Probe 200 `{"groups":["liveness","readiness"],"status":"UP"}`; 401 for `/actuator`, `/actuator/health/`, `liveness`, `readiness`, `db`, `info`, `metrics`, `/v3/api-docs`; 403 for `POST /actuator/health`; 401 for a malformed JWT on `/api/me/courses`; 200 for the public catalogue |
| Unhealthy dependency | PostgreSQL stopped, then probed | 503 after about 30 s, not `UP`; `UP` again after restart |
| CI gate logic | The new probe assertions of job **VM HTTPS ingress**, run against the same local stack | All assertions passed |

Not covered: the real VM, a linux/amd64 release backend, `deploy.sh deploy` and
outside-network access.

## DP01 minimal MinIO server qualification — 1 November 2026

Scope: the `minimal` server mode of `infrastructure/minio/test-minio-init.sh` and
its CI steps, described in
[DP01 minimal MinIO server candidate](deployment.md#dp01-minimal-minio-server-candidate--1-november-2026).
Run on macOS 27.2 arm64 (linux/amd64 under emulation) with Docker Engine 29.8.1,
Compose v5.5.1 and host bash 3.2.57, on branch `chore/dp01-minio-server-minimal`
at `8db0473` plus this change. To reproduce, pull the three pins as the CI job does, then:

```bash
infrastructure/minio/test-minio-init.sh
MINIO_INIT_TEST_SERVER=chainguard infrastructure/minio/test-minio-init.sh
docker build --platform linux/amd64 -t codemastery-test/minio-server:qualification - < infrastructure/vm/Dockerfile.minio-server
MINIO_INIT_TEST_SERVER=minimal infrastructure/minio/test-minio-init.sh
```

| Check | Command | Result |
| --- | --- | --- |
| Shell syntax | `bash -n infrastructure/minio/test-minio-init.sh` | Passed |
| Workflow structure | Parsed with Ruby's YAML library and compared with the previous revision | Trigger, permissions and the other seven jobs unchanged. Job `minio-initializer` keeps its name and runner; its three existing steps are unchanged; two steps were added |
| Golithus, variable unset | `infrastructure/minio/test-minio-init.sh` | 24/24 checks passed (288 s) |
| Chainguard | `MINIO_INIT_TEST_SERVER=chainguard …` | 24/24 checks passed (291 s) |
| Minimal | The CI build command above, then `MINIO_INIT_TEST_SERVER=minimal …` | Build succeeded; 24/24 checks passed (294 s) |
| Selection | `MINIO_INIT_TEST_SERVER=` (empty), `Minimal`, or `minimal` with `MINIO_INIT_TEST_PLATFORM=linux/arm64` | Each failed with exit 1 before testing |
| Missing candidate | `minimal` with the tag removed | Exit 1 with the build instruction; nothing was pulled |
| Wrong candidate | The Chainguard image, or a variant with `/bin/sh` added, under the candidate tag | Exit 1: configuration mismatch or filesystem mismatch |
| Suite constants (scratch copy, tracked file unchanged) | A mutable Chainguard reference; a wrong expected binary hash; a wrong Golithus version | Exit 1: not digest-pinned (Chainguard) or Dockerfile pin mismatch (minimal); binary mismatch; version mismatch (Golithus) |
| Cleanup | Containers, networks and volumes compared before and after | Unchanged apart from the removed test resources |

The rows above tested the original two-stage Dockerfile. Its first native run in
GitHub Actions ([run 41306581519](https://github.com/chi-thanh-nguyen/codemastery/actions/runs/41306581519),
`bfa06f9`) failed the minimal suite at check 3: `/data` and `/tmp` were `0755` on
the runner's builder. Golithus and Chainguard passed 24/24 natively there. After
the [staging-stage correction](deployment.md#native-ci):

| Check | Command | Result |
| --- | --- | --- |
| Shell syntax | `bash -n infrastructure/minio/test-minio-init.sh` | Passed |
| Minimal, corrected Dockerfile, local | The CI build command, then `MINIO_INIT_TEST_SERVER=minimal …` | Build succeeded; 24/24 checks passed, including `/data` and `/tmp` at `0700 65532:65532` |
| Root directory | Layers of the corrected image, read from `docker save` on the local builder only | No layer has an entry for `/` |

Native result: on PR #126 head `a23436b`, GitHub Actions
[run 41308772749](https://github.com/chi-thanh-nguyen/codemastery/actions/runs/41308772749)
(job 124854577665, native linux/amd64) passed Golithus, Chainguard and minimal
24/24 each ([Native CI](deployment.md#native-ci)). The root-directory row was not
repeated natively.

Not covered: any vulnerability scan or security admission of the minimal image,
a release archive, and the Azure VM.

## DP01 MinIO initializer base qualification — 1 November 2026

Scope: the initializer base change in
[DP01 MinIO initializer base remediation](deployment.md#dp01-minio-initializer-base-remediation--1-november-2026).
Run on macOS 27.2 arm64 (linux/amd64 under emulation) with Docker Engine 29.8.1,
Compose v5.5.1 and host bash 3.2.57, on branch `chore/dp01-minio-init-security`
based on `main` at `36c2b98`. The reproduction commands are unchanged from
[DP01 minimal MinIO server qualification](#dp01-minimal-minio-server-qualification--1-november-2026).

| Check | Command | Result |
| --- | --- | --- |
| Shell syntax | `bash -n` on `minio-init.sh` and `test-minio-init.sh` | Passed |
| Golithus, variable unset | `infrastructure/minio/test-minio-init.sh` | 24/24 checks passed (301 s) |
| Chainguard | `MINIO_INIT_TEST_SERVER=chainguard …` | 24/24 checks passed (304 s) |
| Minimal | CI build command, then `MINIO_INIT_TEST_SERVER=minimal …` | Build succeeded; 24/24 checks passed (384 s) |
| Selection | `MINIO_INIT_TEST_SERVER=` (empty) or `other` | Exit 1 before testing |
| Base pin | Scratch copy of the suite expecting another `FROM` | Exit 1 at check 2 |
| Previous image | The Golithus-based initializer as `IMAGE` | Exit 1 at check 4 (configuration) |
| `mc` identity | A test-only candidate copy whose package record names another `mc` release, as `IMAGE` | Exit 1 at check 5 |
| Vulnerability comparison | Grype 0.121.0, one database snapshot, both initializer archives | Golithus base 151 matches (4 critical, 41 high); candidate 0 |
| Supply chain | cosign v3.1.4 `verify` and `verify-attestation` | Signatures, SPDX SBOM and SLSA v1 provenance verified for the candidate |
| Cleanup | Labelled containers, networks and volumes | Removed; other resources untouched |

The rows above used the suite at PR #127 head `2c87571`. Review found that it
accepted mis-sourced initializer images. After the
[artifact identity remediation](deployment.md#dp01-minio-initializer-base-remediation--1-november-2026):

| Check | Command | Earlier suite | Current suite |
| --- | --- | --- | --- |
| A1: Golithus `mc` copied over `/usr/bin/mc`, package record kept | `test-minio-init.sh <image>` | 24/24 passed | Exit 1 at check 4: not the base plus one layer |
| A1b: the same substitution and the script in one added layer | Same | 24/24 passed | Exit 1 at check 4: `usr/bin/mc` in the added layer |
| A2b: unchanged Dockerfile on the `:latest-dev` digest, same package record | Same | 24/24 passed | Exit 1 at check 4: base diff IDs differ |
| `mc` hash alone | A1b with layer verification disabled in a test-only copy | — | Exit 1 at check 4: `mc` SHA-256 is Golithus `6c79b743…` |
| Stale constant | Wrong expected `mc` hash in a test-only copy | — | Exit 1 at check 4 on the pinned base |
| Golithus, Chainguard, minimal | As above, with the current suite | — | 24/24 each (328 s, 343 s, 335 s) |

Not covered: a GitHub Actions run of this change (results belong to the pull
request), `govulncheck`, security admission, a release artifact and the Azure VM.

## DP01 MinIO Console hardening verification — 2 November 2026

Scope: the release-only Console setting in
[DP01 MinIO Console hardening](deployment.md#dp01-minio-console-hardening--2-november-2026).
Run on macOS 27.2 arm64 (linux/amd64 under emulation) with Docker Engine 29.8.1
and Compose v5.5.1, on branch `fix/dp01-pre-release-hardening` based on `main` at
`902cd89`. The suite now has 26 checks per server: check 12 is a control showing
the Console listener with the base command, check 13 runs the initializer and
application S3 access against a server with `MINIO_BROWSER=off`, and checks 24–25
cover the release Compose model and its running server.

| Check | Command | Result |
| --- | --- | --- |
| Shell syntax | `bash -n` on `deploy.sh` and `test-minio-init.sh`; `git diff --check` | Passed |
| Compose models | `docker compose … config --format json` with and without the VM override | Local: Console on, `127.0.0.1:9000` and `:9001`. VM: `MINIO_BROWSER=off`, only the frontend publishes (443) |
| Minimal | CI build command, then `MINIO_INIT_TEST_SERVER=minimal infrastructure/minio/test-minio-init.sh` | 26/26 checks passed |
| Golithus, variable unset | `infrastructure/minio/test-minio-init.sh` | 26/26 checks passed (307 s) |
| Chainguard | `MINIO_INIT_TEST_SERVER=chainguard …` | 26/26 checks passed (311 s) |
| Console behavior | Minimal image with the base command and `MINIO_BROWSER` unset, `on`, `off`, `maybe` | Ports 9000+9001, 9000+9001, 9000 only, and exit 1 respectively |
| CVE-2026-97731 probe | Presigned PUT plus unsigned `x-amz-copy-source` against the minimal image with `MINIO_BROWSER=off` | HTTP 400 `AccessDenied`; nothing copied |
| Policy cases | The CI steps "Check offline Compose and provisioning safety guards" and "Check image identity through a real save/load round trip", run locally | Passed. Against `deploy.sh` from `main` both fail on the new cases, as expected |
| Preflight cases | The Compose policy on the rendered CI fixture and its new `minio-*` overrides, run locally | Fixture accepted apart from certificates (not created locally); `MINIO_BROWSER: "on"`, a missing setting and `MINIO_CONFIG_ENV_FILE` rejected with the expected messages |
| Cleanup | Labelled containers, networks and volumes | Removed |

Not covered locally: `deploy.sh preflight` itself (it requires the Ubuntu 24.04
linux/amd64 host; CI runs it), GitHub Actions results (they belong to the pull
request), a release artifact and the Azure VM.

## DP01 PostgreSQL Alpine/su-exec image qualification — 2 November 2026

Scope: [ADR003](decisions/003-postgresql-alpine-su-exec-image.md) and
[deployment.md, "DP01 PostgreSQL Alpine/su-exec image hardening"](deployment.md#dp01-postgresql-alpinesu-exec-image-hardening--2-november-2026),
which holds the measured details. Run on 2 November 2026 on macOS 27.2 arm64
(Docker Desktop, Engine 29.8.1; `linux/amd64` runs use Rosetta), Trivy 0.76.1
(database 2026-11-01), Maven Wrapper, OpenJDK 25.0.4.1. Sources: the audit
remediation measured on the tree committed as `cc5cb95`; the `su-exec` pin rows
on `55902cc` plus that pin. Artifact: local `linux/amd64` `--no-cache` build with
the pin, image ID
`sha256:f7cccf3d0c3f03268d18d1ca519bb1c23170cb65239b7ddad62972739af359bd`.

| Check | Command/method | Result |
| --- | --- | --- |
| Image identity | `postgres --version`; `readlink /usr/local/bin/gosu`; `gosu postgres id` | `17.11`; `/sbin/su-exec`; `uid=70(postgres)` |
| Trivy | `docker save --platform linux/amd64`, `trivy image --input` | This image (with and without the pin) 0 findings; Bookworm 4 CRITICAL / 88 HIGH (amd64 and arm64); Alpine base 1 / 25, all `gosu` |
| OCI layers | Each layer tar of the saved image | Base layer 2 keeps the 1,769,900-byte `gosu`; layer 11 is its whiteout; layer 12 the symlink; layer 13 the volume boundary. Residual risk pending acceptance |
| `su-exec` pin | `apk policy`, `apk fetch`, `apk verify`, APKINDEX entry, `apk add su-exec=0.3-r0` (amd64 and arm64) | Only `0.3-r0` offered; signatures verify; checksums recorded in deployment.md; an absent version fails the build |
| Reproducibility | Two `--no-cache` builds, layer-by-layer comparison | Different image IDs; base layers identical; ours differ only in `var/log/apk.log` and mtimes; `su-exec` binary identical. Not bit-for-bit reproducible |
| Byte-clean experiment (not adopted) | Flattened `FROM scratch` copy; every layer blob searched for `gosu`; Trivy; volume-boundary CI step | No `gosu` bytes; 0 findings; step passes. Awaiting a Leader decision |
| Foreign data volume | Bookworm-initialized and pre-marker volumes, direct `docker run` and `docker compose up --wait` | Exit 78, `refusing to start`, volume content unchanged; reproduced the silent start and `amcheck` failure at `b4c36bb` first |
| Fresh volume | Init, marker, write, restart | Marker `musl`; data kept |
| Local migration | [setup.md procedure](setup.md#existing-postgresql-volumes) on a throwaway project with 600 indexed rows | All rows identical by md5; `bt_index_check` clean; index and sort order agree |
| `deploy.sh preflight` | Ubuntu 24.04 `linux/amd64` container, committed override, synthetic env and TLS pair | `PREFLIGHT OK`; remote Git context, privileged, host-network and insecure-entitlement PostgreSQL builds rejected (all accepted at `b4c36bb`) |
| CI steps, run locally | Preflight against rendered Compose; containerd round trip (isolated `linux/amd64` Engine 29.8.1); new volume-boundary step | Pass. Containerd ran with the PostgreSQL image built on the host and `--provenance=false` fixtures, because emulated BuildKit cannot run `RUN` and Engine 29 adds attestations that Engine 28 on the runner does not. The volume-boundary step fails against the `b4c36bb` image |
| Credential inspection | Five `psqlVariables*` tests; mutation run with the old heuristic; real Rosetta run | Pass; old heuristic fails the translated (`0 0`) and decoy (`1 0`) cases; Rosetta: `1 0` clean, `1 1` leaking, as root and `postgres` |
| Backend suite | `cd backend && ./mvnw verify` | BUILD SUCCESS: 468 unit, 171 integration, 0 failures, 0 errors; no per-run `postgres-it` image left |

Not covered: GitHub Actions results for this revision, a release archive, and
the Azure VM.

## DP01 pre-release security hardening verification — 2 November 2026

Scope: [deployment.md, "DP01 pre-release security hardening"](deployment.md#dp01-pre-release-security-hardening--2-november-2026),
which holds the scan details. Run on 2–3 November 2026 on macOS 27.2 arm64 with
Docker Engine 29.8.1 (containerd image store; `linux/amd64` under emulation),
OpenJDK 25.0.4.1, Trivy 0.76.1 (database 2026-11-01 19:08 UTC), govulncheck
v1.9.0 and cosign v3.1.4, on branch `fix/pre-release-security-hardening` based on
`main` at `b2984a1`. Candidates used `codemastery-candidate/*` tags, never a
`codemastery.invalid/*:release` name. `deploy.sh` code paths ran in disposable
Ubuntu 24.04 `linux/amd64` or Alpine tool containers on the host Docker daemon,
because macOS has bash 3.2 and `deploy.sh` enforces the B1 host.

| Check | Command/method | Result |
| --- | --- | --- |
| Images and scans | `docker build --platform linux/amd64`; `docker save --platform linux/amd64`; `trivy image --input --scanners vuln`, default and `--detection-priority comprehensive` | Release frontend 0/0 (base: 1 CRITICAL, 2 HIGH); backend 0 CRITICAL/HIGH, 24 MEDIUM unfixable; PostgreSQL 0 (merged view); minimal MinIO GO-2026-5932 only |
| Pinned Alpine set | Sorted `apk list -I` versus the `main` recipe on the same day | Identical |
| govulncheck | `-mode=binary` on `mc`, `minio`, the inherited `gosu`, a stripped and unstripped control binary | As recorded; the stripped control reproduces the `openpgp/*` wildcard without `openpgp` |
| Upstream signatures | `cosign verify`, Chainguard release workflow identity | Initializer base and server pins verify |
| Image CI checks | Nginx ownership and setuid/setgid assertions | Fail on `main` images, pass on the candidates |
| Compose policy, offline | CI step "Check offline Compose and provisioning safety guards" | Passes; its new hardening cases fail against `main`'s `deploy.sh` |
| Compose policy, rendered | 21 override mutations of the committed model through `deploy.sh`'s policy code | Committed model accepted; every mutation rejected (a tmpfs over the PostgreSQL data volume is refused by Compose itself) |
| Preflight, rendered fixture | CI step "Check preflight against rendered Compose" (Ubuntu 24.04 amd64 container) | `PREFLIGHT OK`, all rejection cases including the 7 new ones; `main`'s `deploy.sh` rejects the hardened fixture |
| Preflight, committed override | CI step "Run DP01 preflight with the committed override and certificate layout" (non-root operator with `sudo`) | Passes, including the new hardening assertions |
| Archive identity | CI steps "Check release archive identity models" and "Check image identity through a real save/load round trip" | Pass |
| Five-image release path | Real `deploy.sh` `run_provision`/`run_deploy` on the hardened model with candidate images; archive, image-identity, port and host-TLS checks stubbed (no release archive; macOS cannot reach container addresses) | Completed: role provisioning and privilege matrix, initializer, private backend health, Flyway V001–V006, role separation, staged `nginx -t`, frontend healthy. Containers: read-only, `NoNewPrivs` 1, `CapBnd` 0 (PostgreSQL `0xcb`) |
| Behavior on that stack | HTTPS through the frontend | SPA 200; register 201 (approved origin) / 403 (foreign); login; `/api/me/courses` 401/200; `/actuator/health` is the SPA HTML, not proxied |
| Restart and recovery | Stop all, start in order, rerun initializer, log in again | Healthy; initializer `changes: 0`; account persisted |
| Foreign volume, hardened | PostgreSQL with the release restrictions on a non-empty unmarked volume | Exit 78; volume unchanged |
| Initializer writability | Release restrictions, UID 65532 | Only `/tmp` writable |
| MinIO initializer suite | `test-minio-init.sh` for `golithus`, `chainguard`, `minimal` (after the CI build of `Dockerfile.minio-server`) | 26/26 each. First round 321/355/441 s; second round, with the hardening checks, 372/364 s; the minimal run's elapsed time is not meaningful because the host slept during it |
| Ingress job | Its steps run locally with native images (CA store with an OpenSSL 3.5 container) | Build/origin rejection, CA store, stub and real-backend ingress passed |
| PostgreSQL volume boundary | CI step on the committed image | Passes |
| Backend suite | `cd backend && ./mvnw verify` | BUILD SUCCESS: 468 unit, 171 integration, 0 failures |
| Frontend | `npm ci`, `npm test`, `npm run typecheck`, `npm run build`, `npm audit` in `node:22.23.3-bookworm-slim` (pinned) | 390 tests; typecheck and build passed; 0 vulnerabilities |
| F1 Option B experiment | Disposable build; three builds compared; test-only suite copy (identity assertions retargeted) | `mc` bit-identical in all three; 26/26 against all three servers; not adopted |
| F4 flattened experiment | Disposable build; layer search; volume-boundary step; `./mvnw verify` with it as the Testcontainers Dockerfile; hardened five-image release path | No `gosu` bytes; merged filesystem identical; all pass; not adopted |
| Whitespace | `git diff --check` | Passed |

Not run: Playwright end-to-end tests (not available), the CI containerd registry
step (GitHub Actions runs it), a release archive with the
`codemastery.invalid/*:release` names, and the Azure VM. GitHub Actions results
belong to the pull request.

## DP01 final security remediation verification — 3 November 2026

Scope: [deployment.md, "DP01 final security remediation"](deployment.md#dp01-final-security-remediation--3-november-2026).
Run on 3 November 2026 on macOS 27.2 arm64 with Docker Engine 29.8.1 (containerd
image store; `linux/amd64` under emulation), OpenJDK 25.0.4.1 on the host,
Trivy 0.76.1 (database 2026-11-03 01:30 UTC), govulncheck v1.9.0 (database
2026-11-02T22:01:43Z) and cosign v3.1.4, on `fix/pre-release-security-hardening`
at `72e4786` plus the changes recorded there. Candidates used
`codemastery-candidate/*` tags; `test-minio-init.sh` used its own
`codemastery-test/minio-init:qualification` tag. `deploy.sh` code ran in
disposable Ubuntu 24.04 `linux/amd64` tool containers.

| Check | Command/method | Result |
| --- | --- | --- |
| Trivy, five candidates | `docker save --platform linux/amd64`; `trivy image --input --scanners vuln`, default and comprehensive | Initializer 0/0 (2 November image: 1 HIGH and 3 UNKNOWN distinct, comprehensive); PostgreSQL 0/0; backend 0 CRITICAL/HIGH, 24 MEDIUM and 4 LOW without a fix (unchanged); frontend 0/0 (2 November candidate with today's database: 2 HIGH, 1 MEDIUM, 1 UNKNOWN in `libexpat`); minimal MinIO GO-2026-5932 only |
| govulncheck | `-mode=binary` on both `mc` binaries | Project-built: none; Chainguard: GO-2026-5841 (package), GO-2026-5932, GO-2026-6107, GO-2026-6860, GO-2026-6866 (module) |
| `mc` reproducibility | BuildKit build (arm64 builder, cross-compiled); offline rebuild from the exported archive with its README command in an emulated `linux/amd64` Go container; the source archive from two separate BuildKit builds | `mc` `025691d4…` and archive `ed6f972e…` identical each time |
| `x/crypto` fix version | `diff -r` of the `v0.58.0` and `v0.58.1-0.20261009181718-2f1f834330b2` module trees | Only the `openpgp` directory differs (removed) |
| Initializer identity model | `test-minio-init.sh IMAGE` on the 2 November image, the Option B experiment and three mutated builds | All rejected with the expected assertion |
| MinIO initializer suite | `test-minio-init.sh` for `golithus`, `chainguard`, `minimal` | 27/27 each (474, 364 and 360 s) |
| Local initializer argv | Base Compose initializer with an argument-logging `mc` wrapper; `main`'s file and this branch's | `main`: both secrets in `mc` arguments; branch: neither, initialization succeeds; a secret with a tab is refused before any `mc` call |
| PostgreSQL equivalence | New CI step "Check the PostgreSQL image is byte-clean and equivalent to its layered build"; also against a classic-builder build | Passes: configuration equal except the two variables, 3,887 entries equal, one layer, no `gosu` |
| PostgreSQL volume boundary | CI step on the byte-clean image | Passes |
| Backend suite | `cd backend && ./mvnw -B verify` (Testcontainers builds the byte-clean image) | BUILD SUCCESS: 468 unit, 171 integration, 0 failures |
| Frontend | `npm ci`, `npm test`, `npm run typecheck`, `npm run build`, `npm audit` in `node:22.23.3-bookworm-slim` (pinned) | Typecheck, build and audit (0 vulnerabilities) passed. `npm test`: first run 389/390 with one failure while the host ran two Docker suites (failing test name not captured); rerun 390/390. The frontend source is unchanged by this branch |
| Frontend package set | `apk info -v` of the candidate versus `apk upgrade` on the pinned base the same day | Identical |
| Ingress job | Its first four steps locally with native images (CA store with an OpenSSL 3.5 container) | Build and origin rejection with the Nginx ownership and setuid/setgid checks (backend on Temurin 25.0.5), CA store, stub ingress, real backend: all passed |
| Deployment-script job | Shell syntax and authorization gates; offline Compose guards; runtime privilege matrix; env-file custody and committed-override preflight (non-root operator with `sudo`); rendered preflight; archive identity models | All passed |
| Five-image release path | Real `deploy.sh` `run_provision`/`run_deploy` on the hardened model with the five candidates; archive, image-identity, port and host-TLS checks stubbed | Completed. Read-only roots, `cap_drop` ALL, `no-new-privileges`; `NoNewPrivs` 1; `CapBnd` 0 (PostgreSQL `0xcb`); initializer `changes: 4`; PostgreSQL 1 layer, marker `musl`, server as `postgres`; backend on Java 25.0.5 |
| Behavior on that stack | HTTPS through the frontend | SPA 200; register 201 (approved origin) / 403 (foreign); login token; `/api/me/courses` 401 without, 200 with it; `/actuator/health` is the SPA HTML, not proxied |
| Restart | Stop all; start PostgreSQL and MinIO, rerun the initializer, start backend and frontend | Initializer `changes: 0`; frontend healthy; the account logs in again |
| tmpfs usage | `df`/`du` in the running containers | Backend `/tmp` 32 KiB, frontend 4 KiB, PostgreSQL socket directory 4 KiB; 3.9 GiB available each |
| Signatures and provenance | `cosign verify` of the Chainguard initializer base; BuildKit provenance of the Go, Alpine and Temurin pins | Signature verifies; provenance read, unsigned |
| SBOM evidence | `trivy image --format spdx-json` for the five candidates | Generated (not committed) |
| Whitespace | `git diff --check` | Passed |

Not run: Playwright end-to-end tests (not available); the CI steps "Exercise
the runtime privilege matrix on the real schema and failure containment",
"Exercise published-port verification against real containers", "Exercise
real Auth, catalogue and membership under the approved matrix" and the
containerd registry step (GitHub Actions runs them); a release archive with the
`codemastery.invalid/*:release` names; the Azure VM. GitHub Actions results
belong to the pull request.

## DP01 final qualification, second pass — 3 November 2026

Scope: the second task recorded in
[deployment.md, "DP01 final security remediation"](deployment.md#dp01-final-security-remediation--3-november-2026).
Same host and tools as the section above (Trivy database 2026-11-03 01:30 UTC,
govulncheck database 2026-11-02T22:01:43Z). The five candidates were built from
this branch's final files, all `linux/amd64`:

| Image | Candidate | Image ID | Layers |
| --- | --- | --- | --- |
| PostgreSQL | `codemastery-candidate/postgres:r5` | `sha256:d2780e42…` | 1 |
| Minimal MinIO server | `codemastery-candidate/minio-server:r5` | `sha256:fe27b3c4…` | 5 |
| MinIO initializer | `codemastery-candidate/minio-init:r5` | `sha256:fffa4d57…` | 4 |
| Backend | `codemastery-candidate/backend:r5` | `sha256:587ad4ad…` | 8 |
| Frontend (release) | `codemastery-candidate/frontend:r5` | `sha256:fdf3e288…` | 13 |

**Test-host correction.** In the first pass, the operator-container runs of the
env-file custody and committed-override preflight steps began with
`sudo chmod 666` on the Docker socket the container saw. On Docker Desktop that
path is the VM's `/run/host-services/docker.proxy.sock`; its change time matched
that run, and every other socket Docker Desktop created in the same directory at
the same boot is `0660 root:root`. It was set back to `0660` (owner and group
unchanged, the VM's own `/run/docker.sock` was never touched). The host CLI and
root containers still work, a container user without group 0 is refused, and the
operator image's user, which has supplementary group 0, works without any
permission change. Both steps were rerun that way. No repository file contains
the `chmod`; it was only in the local command line.

| Check | Method | Result |
| --- | --- | --- |
| Trivy, exact set | `docker save --platform linux/amd64`, default and comprehensive | PostgreSQL 0/0; initializer 0/0; frontend 0/0; backend 0 CRITICAL/HIGH, 24 MEDIUM and 4 LOW without a fix; minimal server GO-2026-5932 (UNKNOWN) only |
| govulncheck | `-mode=binary` | `mc`: none. `minio`: GO-2026-5932 at symbol level, the stripped-binary artifact shown with a control binary on 2 November |
| JDK trust store | OpenJDK `CheckCerts.java` compiled with the pinned JDK, run on Temurin 25.0.4.1+1, 25.0.5+8 and the candidate | Two, one and no listed roots; the build-time assertion fails on both unpatched stores |
| `mc` Corresponding Source | Exported archive versus the suite's hash and the image's `SOURCE`; archived Dockerfile versus the committed one; offline rebuild with the README command in an emulated `linux/amd64` Go container | All equal; rebuilt `mc` `025691d4…` |
| MinIO initializer suite | `test-minio-init.sh` for `golithus`, `chainguard`, and `minimal` with the `r5` server tagged as the suite's qualification image (tags restored afterwards) | 27/27 each (427, 505 and 369 s) |
| CI-equivalent steps | The deployment-script, PostgreSQL and ingress job steps listed in the first-pass section, plus "Run DP01 preflight with the committed override and certificate layout" after the ingress build; operator steps without any socket change | All 13 passed |
| Five-image release path | Real `deploy.sh` `run_provision`/`run_deploy` with the five `r5` images; archive, image-identity, port and host-TLS checks stubbed | Completed; privileges and roles as in the first pass; containers read-only, `cap_drop` ALL, `no-new-privileges`; PostgreSQL 1 layer, marker `musl`, server as `postgres`, `CapBnd` `0xcb`; initializer `changes: 4`; backend Java 25.0.5 |
| Behavior on that stack | HTTPS through the frontend | SPA 200; register 201 / 403 (foreign origin); login token; `/api/me/courses` 401 / 200; `/actuator/health` not proxied; a 2 MB body 413, a 0.9 MB body reaches the backend (400) |
| Restart | Stop all, start in order, rerun the initializer | Initializer `changes: 0`; frontend healthy; login 200 |
| tmpfs | `df`/`du` in the running containers; `nginx -T` | 32 KiB / 4 KiB / 4 KiB used; Nginx temp paths under `/tmp`, `worker_processes auto`, 1,024 connections |
| PostgreSQL capability sets | Fresh volume, restart with data, refused Bookworm volume, under the release restrictions | As tabulated in deployment.md |
| Backend suite | `cd backend && ./mvnw -B verify` | BUILD SUCCESS: 468 unit, 171 integration, 0 failures |
| Whitespace | `git diff --check` | Passed |

Not run: Playwright end-to-end tests (not available); the CI steps for the
runtime privilege matrix with failure containment, published-port verification,
real-backend Auth/catalogue/membership and the containerd registry (GitHub
Actions runs them); a release archive with the `codemastery.invalid/*:release`
names; the Azure VM. The frontend source is unchanged and its tests were not
rerun in this pass.

## DP01 independent-review remediation verification — 3 November 2026

Scope: the two P2 defects in the independent review of PR #132 at `d6a4eeb`,
corrected under the Leader's remediation task. See the
[implementation and corrected claims](deployment.md#dp01-independent-review-remediation--3-november-2026).
Earlier results above remain historical; successful old certificate-inventory
checks did not prove that inspection failures stopped the build, and ordinary
stdin credentials did not prove long-value preservation.

Local environment: macOS arm64, Docker Engine 29.8.1, Compose v5.5.1;
linux/amd64 containers run under emulation. Maven uses host Temurin 25.0.4.1;
the actual backend image remains pinned Temurin 25.0.5+8. All test state is
synthetic and disposable. No Docker socket permission was changed, production
resource accessed, or release tag created.

| Check | Exact command or method | Actual result |
| --- | --- | --- |
| Baseline F10 reproduction | Actual local Compose initializer, 5,000-byte synthetic secret; authenticate through private JSON aliases with full and truncated values | Before: exit 0, full value rejected with `SignatureDoesNotMatch`, 4,096-byte prefix accepted; server control accepts intact 5,000 bytes |
| Baseline F11 reproduction | Actual old negated pipeline with `keytool` returning 42 | Before: guard exits 0 |
| Corrected backend build | `docker build --platform linux/amd64 -t codemastery-pr132-remediation/backend:f11 backend` | PASS; 117 complete X.509 trust entries, no prohibited root |
| Trust-store build regressions | `bash infrastructure/vm/tests/test-java-truststore.sh codemastery-pr132-remediation/backend:f11` | PASS, 13 uncached build cases: published store rejected; final store accepted; all four introduced real roots rejected; keytool error, malformed/incomplete successful output, validator error, invalid and unreadable stores rejected; unrelated same-CN certificate accepted |
| Backend tests | `cd backend && ./mvnw -B verify` | BUILD SUCCESS; 468 unit and 171 integration tests, zero failures/errors/skips |
| Runtime restrictions and build-only files | Read-only backend with `/tmp` tmpfs, `cap_drop ALL`, `no-new-privileges`; inspect runtime and verifier/inventory paths | PASS: Temurin 25.0.5+8, UID 999, effective/bounding capabilities zero, NoNewPrivs 1; helper class and temporary inventory absent |
| Syntax/whitespace | `bash -n infrastructure/minio/minio-init.sh infrastructure/minio/test-minio-init.sh infrastructure/vm/tests/test-java-truststore.sh`; `git diff --check` | PASS |
| MinIO image and three-server qualification | Commands below, supplied candidate image | PASS, 28/28 for each of Golithus, Chainguard and minimal; the suite-build form additionally checks the unchanged Corresponding Source archive |
| Deployment/HTTPS regression checks | Existing CI-derived scripts; limitations below | PASS: cleanup, authorization, offline Compose, 24-relation matrix sync, archive fixtures and rendered Compose mutations; 65 real-backend HTTPS assertions |

The MinIO suite adds actual local Compose cases at 4,095/4,096 bytes and
repeats them against the existing identity. Full-value authentication must
succeed and a shortened value must fail. At 4,097/5,000 bytes, each credential
field is rejected before any `mc` call; server inspection must find no bucket,
policy or application identity. UTF-8 cases distinguish bytes from characters.
Control characters remain rejected. A wrapper records every `mc` argument list,
and initializer logs are checked for credential disclosure. Existing bucket
permissions, server Console checks, persistence, release conflict refusal and
idempotency remain in the same suite. The release additionally proves its
4,096-byte application-secret boundary, early overflow refusal and continued
5,000-byte root-password support through JSON.

Commands:

```bash
docker build --platform linux/amd64 \
  -t codemastery-pr132-remediation/minio-init:f10 infrastructure/minio
MINIO_INIT_TEST_SERVER=golithus bash infrastructure/minio/test-minio-init.sh codemastery-pr132-remediation/minio-init:f10
MINIO_INIT_TEST_SERVER=chainguard bash infrastructure/minio/test-minio-init.sh codemastery-pr132-remediation/minio-init:f10
MINIO_INIT_TEST_SERVER=minimal bash infrastructure/minio/test-minio-init.sh codemastery-pr132-remediation/minio-init:f10
```

The existing deployment CI steps are executed against this worktree for shell
and authorization gates, frontend cleanup, offline Compose guards, runtime
privilege-matrix synchronization and archive identity fixtures. Rendered Compose
mutation checks execute the actual policy but omit Linux-only host/env-custody
gates on macOS; they are not a full host preflight. The real-backend HTTPS CI
script uses the corrected backend, unchanged frontend/PostgreSQL candidates,
private synthetic PKI and the approved runtime restrictions. Its disposable
PostgreSQL probe waits for the final TCP server rather than the temporary
initialization socket.

GitHub CI on the published HEAD is recorded on PR #132 separately from these
local executions. Not independently repeated here: Linux host/env-custody gates,
archive save/load/containerd round trips, Playwright, vulnerability rescans or
upstream signatures. No Azure deployment or production admission is tested or
authorized by these corrections.

## DP01 post-merge security requalification — 3 November 2026

Baseline: `origin/main` `0535ec4f3ad9939f532648a78296516e7e61801e` (PR #132),
compared with the requested previous baseline `b2984a1`. Follow-up implementation
changes only the default local Nginx target and extends the existing ingress
image checks; the dated [deployment record](deployment.md#dp01-post-merge-security-requalification--3-november-2026)
holds the F1–F11 dispositions. No governance source is changed.

### Artifacts and scanners

All candidates were built for **linux/amd64**, under the separate namespace
`codemastery-followup-20261103/`. Docker Desktop runs on arm64, so these are
emulated-amd64 results, not native-amd64 or Azure evidence. Raw logs, scripts,
inspect JSON, archives, complete scanner JSON and the exported Corresponding
Source are retained outside Git in
`/private/tmp/codemastery-security-evidence-20261103/`.

Docker's containerd image store reports a local **index ID** from `image inspect
.Id`; Trivy archive metadata reports the **platform image configuration digest**.
They are different identities, recorded separately below. These are disposable
candidate bindings, not release identities or admission records.

| Artifact / tag suffix | Local index ID (`sha256:`) | Saved amd64 configuration digest (`sha256:`) |
| --- | --- | --- |
| `frontend:local-amd64` | `4fe800e7cfe38c769bb90ff743784cded682f752267242aad9f35d7a3aa2330f` | `a22a2e60dbf40a760760a6bbb83abbaff2d5624e9376f80e2891d23d8ec02910` |
| `frontend:ingress-amd64` | `35087fd059062d4d0705c3c2a93228562714fad43029836ec253a3ff770ae890` | `0bc1c72816f8ad6a186200874ba1e59ee2188df42f31d6828263e97e2cfb6cf7` |
| `backend:amd64` | `14bb9b03ff27289d326db605ecac9388911087243a1a179656d2a4a01349a052` | `705cb677d127988e24e40b2bf3f5ea8d802b56ebb61072ec6dd45ed382c2271d` |
| `postgres:candidate` | `4444bcb2c93d83b3afe2dfa308a6d5b2d7d73698f6030ac2c596178eb7dde0cf` | `ca3e326296afb43df89659d1b0e25eb6d6505e634a652bd269a0bebf11ca63ec` |
| `minio-init:candidate` | `f68f896394e7d2b09b096183f1d2f6ed166e60ad83b762c0c86cf0f84108bc50` | `fc72160eefd08e471aa31fcd0f4d367ff7c8e062fef31596ce0d4b04942cf245` |
| `minio:candidate` | `1348e6be5cdcf6f5b21cee6e6d3334bfc35f43b7059b683eb0166942f72e6398` | `1f7051c4b7fb3d9237e3fc35ca91e9fea2fe1062c8fac3bd44642056e61270ed` |

Trivy **0.76.1**, vulnerability DB updated `2026-11-03T01:30:30Z`;
`trivy image --input <single-image.tar> --scanners vuln --detection-priority
precise --format json --output <report.json>` and the same command with
`--detection-priority comprehensive`. Both flags are supported by the installed
CLI. No ignore, severity exclusion or suppression is used. Counts below are
finding rows, including separate affected installed packages.

| Artifact | Precise C/H/M/L/U | Comprehensive C/H/M/L/U |
| --- | --- | --- |
| frontend-baseline-local | 0/3/4/0/2 | 0/3/4/0/2 |
| frontend-local | 0/0/0/0/0 | 0/0/0/0/0 |
| frontend-ingress | 0/0/0/0/0 | 0/0/0/0/0 |
| backend-final | 0/0/24/4/0 | 0/0/24/4/0 |
| postgres | 0/0/0/0/0 | 0/0/0/0/0 |
| minio-init | 0/0/0/0/0 | 0/0/0/0/0 |
| minio | 0/0/0/0/1 | 0/0/0/0/1 |

The local image's Nginx executable is SHA-256
`97f9898b94114a136bd87e1eb7759aa2a15d5007154b670c447ee710e629f54b`
and reports 1.30.6. Layer inspection also recovered base-layer zlib
(`a1e2c03d…44e57`) and musl (`38d022ce…72806`) alongside the patched runtime
replacements (`ecc8b9df…7c5f2`, `095b047c…0de5`). All full hashes and layer
positions are in `frontend-local-layer-binaries.json` in the evidence directory.
These upgrades fix the merged runtime; the inherited library bytes remain.

The original local frontend's nine rows are enumerated in deployment.md. No
CRITICAL/HIGH finding remains in these candidate scans. The minimal server's
one UNKNOWN is `GO-2026-5932`, x/crypto v0.58.0, fixed-module version
`0.58.1-0.20261009181718-2f1f834330b2`. Runtime reachability remains
`NOT_VERIFIED`; this is not proof that every server CVE is absent.

The backend's complete residual inventory is below. Trivy reports no fixed
version for any row. A disposable runtime container's `apt-get update` followed
by `apt list --upgradable` lists no available upgrades; the current Temurin tag
still resolves to the consumed `sha256:a98128a5…` index. No packages were
installed, and no unbounded apt upgrade or Ubuntu distribution change was
introduced. The same counts/inventory occur in both detection modes.

| Package / installed version | Severity | All reported CVEs |
| --- | --- | --- |
| `dash` 0.5.12-6ubuntu5 | MEDIUM | `CVE-2026-102473`, `CVE-2026-102474` |
| `libc-bin` 2.39-0ubuntu8.9 | MEDIUM | `CVE-2026-18374`, `CVE-2026-89092` |
| `libc6` 2.39-0ubuntu8.9 | MEDIUM | `CVE-2026-18374`, `CVE-2026-89092` |
| `libexpat1` 2.6.1-2ubuntu0.6 | MEDIUM | `CVE-2025-66382` |
| `libgnutls30t64` 3.8.3-1.1ubuntu3.6 | MEDIUM | `CVE-2026-67693`, `CVE-2026-88647`, `CVE-2026-88648`, `CVE-2026-95184`, `CVE-2026-95209`, `CVE-2026-95210` |
| `libpcre2-8-0` 10.42-4ubuntu2.1 | MEDIUM | `CVE-2026-103111`, `CVE-2026-86145`, `CVE-2026-89156`, `CVE-2026-89157`, `CVE-2026-89158`, `CVE-2026-89160`, `CVE-2026-89161` |
| `libsystemd0` 255.4-1ubuntu8.18 | LOW | `CVE-2026-40228` |
| `libudev1` 255.4-1ubuntu8.18 | LOW | `CVE-2026-40228` |
| `locales` 2.39-0ubuntu8.9 | MEDIUM | `CVE-2026-18374`, `CVE-2026-89092` |
| `login` 1:4.13+dfsg1-4ubuntu3.2 | LOW | `CVE-2024-56433` |
| `passwd` 1:4.13+dfsg1-4ubuntu3.2 | LOW | `CVE-2024-56433` |
| `tar` 1.35+dfsg-3ubuntu0.4 | MEDIUM | `CVE-2026-18477`, `CVE-2026-18508` |

`govulncheck@v1.9.0` (Go 1.27.3), `-mode=binary -json`, DB
`https://vuln.go.dev` modified `2026-11-02T22:01:43Z`: exact candidate `mc`
SHA-256 `025691d43adb653c6ce34723c76450153f2c024bfa232edaca6906fe3e221809`
has no findings. Exact server SHA-256
`3c3d615241314c531e15e57485c053e60dc1af04e87b20424d2db8bdec8a49b2`
has only GO-2026-5932 (15 reported traces, one distinct advisory). JSON mode
returns zero even when findings exist; the finding objects, rather than exit
status, determine this result. No new source-mode/reachability claim is made.

The exported `mc-source.tar.gz` equals the existing suite assertion:
`a42400d4703c265258952b44bfd997a53dcca55bf47b691f48a1f2f483e98b50`.
No new independent offline binary rebuild was performed; prior reproducibility
evidence is historical, while the fresh archive/binary identity comparisons
passed. Cosign **3.1.4** freshly verifies both consumed Chainguard pins and the
new current upstream indexes. It also verifies the current indexes' SLSA v1
attestations with exact subjects and the issuer/workflow identity in
deployment.md. Nginx's unsigned BuildKit statement was inspected, not
independently authenticated. No project-image signing workflow was added.

### Executed functional and security checks

| Check / executed command or existing CI scenario | Result |
| --- | --- |
| `cd backend && ./mvnw verify` (Java 25.0.4.1 host; isolated Testcontainers build) | PASS: 468 unit + 171 integration, zero failures/errors/skips; runtime candidate separately uses Temurin 25.0.5+8 |
| `cd frontend && npm ci`; `npm test`; `npm run build` | PASS: 390 tests; build includes `npm run typecheck`; only the existing bundle-size advisory |
| `cd frontend && npm audit --json` | PASS: 0 vulnerabilities |
| `cd frontend && npm run test:browser` | NOT_RUN: attempted, exits 1 before browser execution because module `playwright` is unavailable; no E2E PASS claimed |
| `docker build --platform linux/amd64 ...` for all five candidates and both frontend targets; `docker save --platform linux/amd64 ...` per scan artifact | PASS; separate candidate tags, inspect JSON and archives retained |
| New version/ownership checks against the original local image | Expected FAIL (Red): both assertions exit 1; patched local target PASS (Green) |
| Ingress CI image step, including `bash infrastructure/vm/tests/test-java-truststore.sh <backend>` and invalid release origins | PASS: both frontend targets have Nginx 1.30.6, root-owned non-writable configuration, usable cache and no setuid/setgid files; backend also has no such files; trust-store suite 13/13 |
| `infrastructure/minio/test-minio-init.sh <candidate>` assertions, Golithus / Chainguard / minimal | PASS: 28/28 each, including exact OCI/binary identity, IAM, least privilege, persistence/idempotency, Console-off, credential boundaries and failure propagation |
| Existing deployment-script CI steps: shell/authorization, cleanup, offline Compose/provisioning, privilege-record consistency, env custody, rendered Compose, archive models, real save/load round trip | PASS: all eight steps; Linux-specific custody checks passed on the harness's native filesystem |
| Existing PostgreSQL CI privilege-matrix, foreign-volume and byte-clean equivalence steps | PASS: SQL privilege/failure cases; exit 78 and unchanged glibc data; musl marker/restart/persistence; single-layer content/configuration equivalence and no inherited gosu |
| Existing HTTPS stub-backend ingress scenario | PASS: 126 assertions, including local HTTP deep link/404/healthcheck behavior, TLS rejection, forwarding restrictions and unavailable-backend behavior |
| Existing public-chain CA-store fixture | PASS with Ubuntu OpenSSL 3: expected chain trusted, missing cross-sign and wrong hostname rejected; no Azure connection |
| Existing committed-override preflight and image-policy scenario | PASS: effective five-service model/hardening, certificate rejection cases, release-image acceptance and local-image rejection |
| Disposable hardened five-image scenario, extending the existing real-backend ingress scenario | PASS: 65 HTTPS/Auth/CORS/management/failure assertions; real Flyway V001–V006 under migrator, exact runtime/bootstrap/migrator separation; all five hardening configurations; full restart retains PostgreSQL data/marker/privileges and MinIO repeat initialization |
| Runtime `/proc/1/status` after restart | PASS: PostgreSQL UID/GID 70, CapEff 0, CapBnd 0xcb; MinIO UID/GID 65532, backend 999, frontend 101, each CapEff/CapBnd 0; all NoNewPrivs 1 |
| YAML parsing and `bash -n` for all Bash workflow blocks; `git diff --check` | PASS |

### Harness adaptations, failures and limits

CI blocks were extracted into the external evidence directory. Only disposable
image/resource names and explicit linux/amd64 builds were substituted for this
host; PostgreSQL role/ingress fixtures used the actual hardened candidate instead
of Bookworm. The MinIO harness changed only its repository root and minimal
candidate tag, preserving every qualification assertion. Supplied-image mode
omits the build-only source-archive assertion; that archive was independently
exported and hash-checked above.

The five-image extension uses new volumes/network, synthetic TLS and disposable
credentials, the actual `deploy.sh` provisioning/privilege functions, and the
approved read-only/capability/tmpfs controls. Curl trusts a test-only root through
a fixture mount. No VM command, production preflight, public port publication,
release promotion or end-to-end `deploy.sh deploy` execution is claimed.

Initial harness failures were preserved and corrected: native Docker builds
selected arm64 before an amd64 trust-store/export check; shared macOS filesystem
ownership failed strict Linux env-custody checks; macOS LibreSSL lacked the CA
fixture verification option; one preflight invocation omitted image variables;
and the first five-image extension queried PostgreSQL after the existing test
intentionally stopped it for the 503 assertion. Corrected invocations passed,
without weakening production tests. Multi-image Trivy input was also rejected;
all reported scans use separate single-image archives.

The daemon was not reconfigured. The full containerd job's setup and ID-removal
round trip were NOT_RUN to preserve existing daemon/image state; the existing
single-image save/load identity scenario did pass against the current containerd
store. A native-amd64 run and published-HEAD CI remain separate evidence. Browser
E2E and exact modified-server source reproduction/runtime OpenPGP reachability
remain NOT_VERIFIED. Production security admission and the Leader decisions in
deployment.md remain pending.

OpenAI Codex assisted this follow-up implementation, test execution and evidence
record. No application module, API, schema or adaptive behavior was changed.

## UI/UX r1 implementation verification — 6 November 2026

### Authority and scope

On 6 November 2026 (Asia/Ho_Chi_Minh) the Project Leader/Owner instructed
“CodeMastery — Complete Frontend Redesign & Implementation” and then
“CodeMastery — Finalize UI/UX Implementation, Verification & Local Commit”. The
second instruction approves, for this UI/UX task only: documentation and
repository-structure synchronization, the `frontend/src/app/preview/` structure,
frontend component/test/fixture changes, removal of verified-obsolete assets,
replacement of the browser harness, Playwright configuration and E2E
infrastructure with justified dependencies, and local commits. It excludes changes
to API endpoints or contracts, backend logic, schema, JWT/session policy, security
or authorization rules, deferred contracts, and any push, PR, merge, release or
deployment. The design source is the local, untracked root `DESIGN.md`, revision
`DESIGN-2026-11-06-r1`. These approvals are prospective from that date; no earlier
approval is implied.

### Decisions applied under that authority

| ID | Decision | Rationale |
| --- | --- | --- |
| UX-R1-T1 | `@playwright/test` 1.63.0 and `@types/node` 22.20.5 as `e2e/` dev dependencies, with a regenerated `e2e/package-lock.json` | One Playwright for E2E and the harness; 1.63.0 matches the browser builds already used by earlier recorded runs (Chromium 153.0.8010.12); `npm audit`: 0 vulnerabilities. No frontend or backend dependency changed. |
| UX-R1-T2 | Disposable local stack in Playwright global setup, deterministic `fixtures/catalogue-fixture.sql`, Instructor created through the API and promoted only in the disposable database | Reruns never depend on a developer database; generated secrets stay out of argv, logs and the repository. |
| UX-R1-T3 | `presentation.browser.cjs` rewritten for the new UI; FE01-only modes retired | The FE01 preview it targeted no longer exists. |
| UX-R1-T4 | `plus-jakarta-sans-latin-wght.woff2` and `public/plus-jakarta-sans-OFL.txt` deleted | No source, test, script or workflow referenced them after the switch to Figtree. Sample-material PDFs that embed Plus Jakarta Sans are unrelated and unchanged. |
| UX-R1-T5 | Design deviations closed in code: My courses tile overflow menu (Leave course / Re-enroll, reusing the approved CT22.membership calls); full-screen confirmation sheet below 600 px; 1.2 s skeleton shimmer, static with reduced motion; Mastery Map nodes 220 × 72 px with a fit-when-readable initial viewport (0.85 floor, a preselected node pulled into view at no less than 0.75); preview navigation per role as specified | Bring the implementation to the specification rather than amend it. |
| UX-R1-T6 | Keyboard focus ring restored on every MUI `ButtonBase` (the global ring lost to ButtonBase's `outline: 0`), with the light ring on the ink header, drawer footer and quiz header | Found by the new harness: contained buttons had no visible focus indicator. |

### Regression mapping for the 15 removed unit tests

`frontend/src/routes/index.test.tsx` lost 8 `it` and 3 `it.each` blocks (15 runtime
tests), all bound to the FE01 dashboard preview that the design preview replaced.

| Removed test | Disposition | Current coverage |
| --- | --- | --- |
| preserves current-main FE01 dashboard, formatter runtime and utilities as an independent preview | Isolation retained; formatter showcase retired | `PreviewApp.test.tsx`: sample-data label, no request, no storage; a real sign-in never reaches the preview. Harness: production routes never load the preview chunk. `format.test.ts` (11) is unchanged; no current screen consumes the formatter. |
| focuses each local preview heading after its initiating control is removed, without changing routes | Retained | `PreviewApp.test.tsx`: query-parameter navigation keeps `/preview` and focuses the new `h1`. |
| shares one header search with debounced sample results, clear, Escape and empty recovery | Retained on the Users search | `PreviewApp.test.tsx`: 300 ms debounce, clear keeps focus, Escape clears, “Clear filters” recovers; `presentation.test.tsx`: SearchPill Escape/clear focus. |
| associates the active sample filter tab with its result panel | Retained on course-editor sections | `PreviewApp.test.tsx`: pressed section button `aria-controls` the labelled region. |
| form submit / search button focuses its local result section (2) | Replaced | Catalogue submit-only search in `CourseCataloguePage.test.tsx`, the harness and E2E; every preview test asserts no request. |
| keeps the hovered chart tooltip across column gaps; clears chart selection (3); dismisses a hover tooltip with Escape (2); cleans up the chart Escape listener; observes Escape without consuming unrelated keys; keeps sample-week arithmetic and keyboard day selection | Retired with the interactive FE01 chart | Charts are now static bars with “Show as table”. Escape is tested where it exists: SearchPill, dialogs and drawer (harness), Mastery Map selection (harness). |

`presentation.test.tsx` renamed one test to “exposes controlled current navigation in
the top bar and the mobile drawer, and wraps zero badges” and kept its assertions.
Totals moved from 390 tests in 11 files to 414 tests in 15 files. Added coverage:
`app/preview/PreviewApp.test.tsx` (13), `features/assessment/components/assessment.test.tsx`
(4), `features/instructor/course-management/components/QuestionForm.test.ts` (3),
`features/mastery/utils/masteryGraphLayout.test.ts` (5), three My courses tile
membership tests and eleven new `presentation.test.tsx` cases (state pills, path
spine, guidance stack, search pill, data table, pagination, segmented control,
aside, confirmation sheet). No behavioral assertion of a still-existing feature was
removed.

The FE01 harness checks map as follows: favicon, logo, self-hosted font and licence,
overflow by width, contrast, focus visibility, skip link, drawer, destructive
dialog, reduced motion, form focus, `pagehide` blur and course error views are
retained against the new UI; the anonymous Profile guard, account links and deep
links moved to E2E against the real backend; chart, tooltip, KPI-card and
TextField notch checks retired with their components; the response-classifier
mode retired (classification stays covered by `httpClient.test.ts`).

### Executed checks and results

Revision: code commits `a1ed70e` and `b2e9035` on `feat/uiux-design-r1-implementation`
(the checks ran on the identical working tree before committing). Environment:
macOS 27.2 arm64, Node 24.21.0, npm 11.19.0, Docker 29.8.1, OpenJDK 25.0.4.1.

| Check | Result |
| --- | --- |
| `npm run typecheck`, `npm test`, `npm run build` in `frontend/` | PASS: 414 tests, 15 files; build succeeded with the existing >500 kB chunk warning (main 701.11 kB, preview 444.08 kB minified) |
| Preview isolation in `frontend/dist` | PASS: sample names, preview copy and React Flow appear only in the lazily loaded `PreviewApp` chunk |
| `npm run test:browser -- --engine all` | PASS Chromium 153.0.8010.12 30/30; PASS WebKit 26.6 30/30; BLOCKED Firefox 155: the browser exits with “Could not find profile folder” inside the agent's command sandbox, and running it outside the sandbox was refused |
| `npx playwright test` in `e2e/` (disposable stack) | PASS 41: chromium-desktop 11, chromium-mobile 10, webkit-desktop 10, webkit-mobile 10; 4 skipped by design (the API-only smoke runs once); BLOCKED firefox-desktop: 10 failed at browser launch with the same error. Stack removed afterwards (no labelled container, network or temp directory left) |
| Impeccable detector on 39 rendered URLs at 1280 × 900 and 390 × 844 (78 scans) | 5 warnings, each verified: dashed drop zone and dashed empty slot reported as nested cards (both specified); the sticky full-height lesson aside reported as a column imbalance |
| `npm audit` (`frontend --omit=dev`, `e2e`) | PASS: 0 vulnerabilities |
| Figtree font provenance | PASS: SHA-256 `4ba7d3d0…7462dd` equals `files/figtree-latin-wght-normal.woff2` in `@fontsource-variable/figtree@5.2.10` (OFL-1.1); `public/figtree-OFL.txt` is the OFL 1.1 text |
| Manual screen-reader session (VoiceOver) | NOT RUN: no interactive assistive-technology session was available to the agent; accessibility-tree snapshots are not a substitute |
| CI on the branch | NOT RUN: nothing was pushed |

These are local results on synthetic data. They are not production acceptance,
QA07/QA01 acceptance, WCAG conformance or a measured visual-similarity score.
Residual risks: Firefox and real assistive technology are unverified; the suite
has no CI job; the disposable stack uses the local HTTP model (cross-origin API
with CORS), not the VM's single-origin HTTPS ingress; and the 15-minute JWT with
no refresh can expire during a long timed quiz once Assessment is integrated,
which the Assessment contract must resolve without changing CT38 here.

Anthropic's Claude Code (Claude Opus 5.5) implemented these changes and ran the
checks, using the locally installed Impeccable skill (rendered detector) and
Playwright CLI for browser inspection. No human review is claimed.

## Production activation of eligible UI/UX components — 6 November 2026

### Authority

The Leader's instruction of 6 November 2026 asked to activate in production every
component that works on approved contracts or needs no backend, keeping everything
else in `/preview`, and approved the frontend routing decisions this needs without
any API or backend change. It supersedes, for these routes only, the CT16 shell's
root redirect (`/` → `/courses`) and resolves the `DESIGN.md` open questions on the
landing page (S25), a Learner home route (S6) and the session-ended notice (F2).
Post-sign-in navigation (`/courses`, or the one-use `/profile` return), the memory
session, expiry, 401 handling, guards and every API call are unchanged.

### Disposition by component

| Component | Disposition | Basis |
| --- | --- | --- |
| S25 landing page at `/` | Activated | Static content; public navigation (Catalogue, Sign in, Create account) and skip link; the example map is a fixed illustration labelled “Illustration · not learner data” in its own lazily loaded chunk (`ExampleMasteryMap`), below 600 px as a list. A signed-in visitor is redirected to `/home` (Learner) or `/courses`. |
| Public navigation | Activated | Logo → `/` for visitors, → start page when signed in; Catalogue for everyone; Sign in / Create account (drawer footer below 900 px); the sign-in and registration logo links to `/`. |
| S6 Learner home at `/home` | Activated (active memberships only) | Learner-only guard; reads `GET /api/me/courses?status=active` (CT22.membership). Rows link to course detail. Resume, progress and Up next are not rendered: CT22 progress/resume, Adaptive, Assessment and Interaction are pending; the full design stays in `/preview`. |
| F2 session-ended notice | Activated | After a session present in this tab ends without signing out (timer, resume/focus checks or a current-session 401), `/login` shows “Your session ended. Sign in again to continue.” once. Observed in the router from `useAuth()`; the provider and session policy are untouched. Not shown after sign-out or for a fresh document. |
| Learner navigation | Activated | Home · Catalogue · My courses (the bell stays omitted). |
| Instructor and Administrator navigation | Unchanged | Only Catalogue is implemented; Teaching, Grading, Learners and every Administrator page remain preview-only. |
| Password recovery (F1), lessons and progress (S7, S8), diagnostic and assessments (S9–S13), Mastery Map (S14), notifications and Q&A (S15), Instructor (S17–S22) and Administrator (S24) screens | Kept preview-only | Their contracts (CT02, CT21, CT22 progress/resume, CT09, CT04/CT10/CT23, Adaptive, Interaction, CT12/CT31, reporting) are pending. |
| S23 reporting, Markdown lesson rendering | Kept preview-only / blocked | No reporting definitions; no approved Markdown sanitisation decision. |

### Verification

| Check | Result |
| --- | --- |
| `tsc --noEmit` (also with `--noUnusedLocals`), `npm test`, `npm run build` | PASS: 422 tests in 15 files (8 new routing/home/landing/notice tests); build succeeded. Main chunk 532.06 kB plus a shared UI chunk 181.66 kB; the example map (186.10 kB, React Flow) and preview (250.97 kB) load on demand |
| Chunk isolation | PASS: no preview copy, sample names, React Flow or illustration text in the main or shared chunk; the landing page loads `ExampleMasteryMap` and never `PreviewApp` |
| Browser harness, `--engine all` | PASS Chromium 153 and WebKit 26.6, 34/34 each (adds landing, mobile landing header and Learner home checks; `/` joins the width, zoom and contrast sets). BLOCKED Firefox (same sandbox launch error) |
| E2E, five projects | PASS 53 (chromium-desktop 14, chromium-mobile 13, webkit-desktop 13, webkit-mobile 13), 4 skipped by design; BLOCKED firefox-desktop 13 at launch. New: landing with no API request, Learner home with a real enrolment, session-ended notice after a 15-minute clock fast-forward |
| Impeccable detector on `/`, `/courses`, `/login`, `/register` and the preview landing, session-ended and Learner-home screens at 1280 and 390 px | PASS: no findings. `/home` needs a session and was checked through harness screenshots instead |

The landing copy follows `DESIGN.md` S25 and describes the product's learning model
(diagnostic, quizzes, skill states, instructor tools). Several of those features are
not available until their contracts are implemented; the Leader may want that copy
reviewed before a public release. Anthropic's Claude Code (Claude Opus 5.5) made and
verified these changes.

## PR #209 CI remediation, typography, brand and navigation indicator — 7 November 2026

### Authority

The Leader's instruction “CodeMastery — PR #209 CI Remediation, Typography, Premium
Brand Identity & Navigation Indicator Refinement” (7 November 2026) asked to fix the
two CI failures on PR head `0ba1db0`, select one UI typeface from a ranked list,
keep JetBrains Mono for code, give the wordmark its own display face, refine the
logo palette for dark backgrounds and replace the active-tab indicator with the
reference's concave curved bottom edge. No commit was requested; the changes are
uncommitted on `feat/uiux-design-r1-implementation`.

### CI failures and root causes

| Job | Root cause (reproduced) | Fix |
| --- | --- | --- |
| Frontend: `PreviewApp.test.tsx` › “never reads the signed-in session …” (`/login` instead of `/courses`) | The test signs in through the real client but did not supply `VITE_API_BASE_URL`. Vite loads the developer's untracked root `.env`, so it passed locally; CI has no `.env`, the client refused the request with a configuration error and the page stayed on `/login`. Reproduced locally with `VITE_API_BASE_URL= npx vitest run src/app/preview`. Not an Auth or navigation regression. | The test stubs the API base (as `routes/index.test.tsx` does) and restores it; a bounded `waitUntil` (3 s, real timers) replaces the 200 ms loop and fails with the route and any visible alert; it also asserts one `POST /api/auth/login`. Full suite passes with and without the local API base. |
| VM HTTPS ingress: `Build the release and local images …` (`apk add`, exit 18) | Deterministic: the Alpine v3.24 index no longer serves `alpine-release=3.24.3-r0` and `zlib=1.3.2-r1` ([deployment record](deployment.md#frontend-alpine-package-pins-refreshed--7-november-2026)). | Pins raised to `3.24.4-r0` and `zlib 1.4-r0` in both targets; the CI step prints the build log tail on failure. |

### Typography, brand and indicator decisions

- **UI face:** Google Sans Flex 4.005 (Fontsource `@fontsource-variable/google-sans-flex`
  5.3.2, Google Fonts v23, SIL OFL 1.1). PP Neue Montreal 3 and Suisse Int'l, ranked
  higher, are commercial and the project holds no licence that permits bundling them
  for all team members and visitors; Google Sans Flex was the highest-ranked eligible
  candidate, so Inter and Be Vietnam Pro were not evaluated further or installed.
  Shipped: Latin (50.8 kB), Latin Extended (26.3 kB) and Vietnamese (11.1 kB) weight-axis
  WOFF2 files with Google Fonts `unicode-range`s. fontTools confirmed every Vietnamese
  base and tone-marked letter (Đ/đ, ă, â, ê, ô, ơ, ư, U+1EA0–U+1EF9) and combining marks;
  `₫` (U+20AB) is absent and unused.
- **Code face:** JetBrains Mono 2.211 (`@fontsource-variable/jetbrains-mono` 5.3.0, OFL),
  same three subsets, complete Vietnamese coverage. `codeFontStack` is the only code
  stack; the platform monospace stack is gone.
- **Wordmark face:** Poppins ExtraBold 800 4.004 (`@fontsource/poppins` 5.3.0, OFL), one
  Latin file (7.8 kB) declared as “CodeMastery Wordmark”, used only by `BrandWordmark`,
  now also in the auth brand panel.
- **Logo:** palette of `favicon.svg` retuned (electric-blue C, sky outer nodes, violet
  focal node, lighter links); `favicon.ico` regenerated from it at 16/32/48/64 px.
- **Indicator:** `canvasReachPath` draws an 88 × 14 px symmetric bell traced from the
  reference (concave shoulders, convex crest); it re-centres on font swaps and sibling
  resizes and takes the colour of the surface below the bar.
- Figtree assets removed; no hardcoded font stack remains outside the theme tokens.

A real defect found by the new harness check: in WebKit the reach stayed 2.4 px off
centre because the item list moved without resizing when the wordmark font swapped
in; the header now also observes its slots.

### Executed checks and results

Environment: macOS 27.2 arm64, Node 24.21.0, npm 11.19.0, Docker 29.8.1.

| Check | Result |
| --- | --- |
| `npx tsc --noEmit` (and `--noUnusedLocals`), `npm test`, `npm run build` (frontend; manifest and lockfile unchanged, so `npm ci` was not rerun) | PASS: 424 tests in 15 files; also 424/424 with `VITE_API_BASE_URL` empty (CI condition); build succeeded |
| Preview isolation in `dist` | PASS: no preview copy, sample names, React Flow, Figtree or Plus Jakarta in the main chunk |
| `docker build --no-cache` release and local targets, linux/amd64 and native | PASS (before the fix: release target failed with the apk error above) |
| Ingress job steps 1–4, extracted from `ci.yml`, linux/amd64, OpenSSL 3 for step 2 | PASS: trust store 13/13, UID 101/Nginx 1.30.6/read-only configuration, no setuid/setgid, 8/8 invalid origins rejected; CA chain checks; `INGRESS_STUB_TESTS_PASS`; `INGRESS_BACKEND_TESTS_PASS`. The new failure-log path was exercised with a deliberately stale pin |
| Ingress job step 5 (DP01 preflight) | NOT RUN: needs root on the host (`sudo install` under `/etc/codemastery`) |
| Trivy, all severities, both frontend images | PASS: 0 findings |
| `npm run test:browser -- --engine all` | PASS Chromium 153 38/38 and WebKit 26.6 38/38, including: only the three bundled faces load; the Vietnamese subset is fetched; Chromium font inspection shows Vietnamese text in Google Sans Flex, code in JetBrains Mono and the wordmark in Poppins with no fallback; logo regions ≥ 3:1 on ink and distinct at 16–40 px; reach centred (±1 px), 88 × 15 incl. 1 px overlap, symmetric, not focusable, following clicks, back/forward and child screens across Administrator, Instructor, Learner and anonymous navigation, present at 1024 px and absent at 390 px without overflow. BLOCKED Firefox (sandbox launch error, as before) |
| E2E, five projects, disposable stack | PASS 53 (chromium-desktop 14, chromium-mobile 13, webkit-desktop 13, webkit-mobile 13), 4 skipped by design; BLOCKED firefox-desktop 13 at launch |
| Impeccable detector, live pages and 20 preview screens at 1280 and 390 px | Live pages: no findings. Preview: the same accepted findings as on 6 November (dashed slot and drop zone as nested cards; sticky lesson aside) |
| CI on the PR | NOT RUN: nothing was pushed |

Visual comparison of the indicator with the reference was done by tracing the
reference profile and inspecting enlarged screenshots side by side; it is a
judgement, not a pixel-diff metric. No WCAG conformance or similarity score is
claimed. Anthropic's Claude Code (Claude Opus 5.5) made the changes and ran the
checks, with the local Impeccable detector and Playwright CLI.

## Preview band, header lockup and course marks — 7 November 2026

### Authority and scope

The Leader's follow-up instructions of 7 November 2026 asked to remove the light-blue
band at the top of the design preview (the sample-data notice with the workspace
switcher; nothing inside screens), enlarge the CodeMastery lockup in the top bars, and
show courses with official full-colour language logos in consistent neutral containers
with optical size normalisation, never on a dark ground. No commit was requested.

### Changes

- **Preview notice:** `SampleDataBanner` sits on `canvas-100` instead of `sky-100`
  (neutral icon, hairline only above bare screens); the reach takes the canvas colour.
  Its statement, workspace switcher, Preview index and Reset stay. In-content colours
  (info alerts, Sky specimens, pills) are unchanged.
- **Lockup:** `presentationTokens.brandMark` sets the mark to 40 px from 600 px and,
  below 600 px, fluidly 32 px at 360 px to 36 px from about 382 px; the wordmark stays
  0.625 × and the gap 0.3 × the mark. Bar heights (80/64 px) are unchanged. A fixed
  36 px mark overflowed the 360 px Learner bar (bell + account) by 16 px; the harness
  found it, hence the fluid phone size.
- **Course marks:** `CourseMark` replaces the ink initial square in `CourseTile`
  (catalogue, My courses, preview). One registry maps Python and TypeScript to their
  official SVGs with a per-logo visible-artwork box and optical scale (Python 0.66,
  TypeScript 0.56 of the 48 px container); the container is `canvas-100` with a
  `line-200` inset hairline at `rounded.md`. The language is read only when the course's
  title, else description, names exactly one language; otherwise the initial is shown
  in the same container. No current seed, E2E or preview course names a language, so
  live tiles show the neutral initial; the design reference shows the logo samples.

### Executed checks and results

| Check | Result |
| --- | --- |
| `npx tsc --noEmit`, `npm test`, `npm run build` (frontend) | PASS: 429 tests in 15 files, also 429/429 with `VITE_API_BASE_URL` empty; build succeeded (Vite warns that the `index` chunk is 536 kB, over its 500 kB advisory limit) |
| `npm run test:browser -- --engine chromium,webkit` | PASS Chromium 153 41/41 and WebKit 26.6 41/41, including the new checks: lockup size and clearance at 1280/1024/390/360 px on `/courses`, `/preview` and `/`; neutral preview notice and reach colour; course marks decoded, inside their containers, on `canvas-100`, colours kept, no ink container on catalogue or My courses at 1280 and 390 px |
| `npx playwright test` (chromium/webkit desktop and mobile, disposable stack) | PASS 53, 3 skipped by design; stack removed afterwards. Firefox not run (sandbox launch failure recorded above) |

### Follow-up the same day: preview chrome, generic course mark and avatar

The Leader then asked to remove the preview notice entirely (sample-data line,
workspace switcher, Preview index and Reset buttons) so screens look as deployed and
are opened by link; to replace the letter fallback with a white glyph on a coloured
fill; and to recolour the account avatar. Changes: `SampleDataBanner` and the workspace
switcher are removed from every screen, including the quiz attempt (its “Simulate time
running out” button went with it; the auto-submit test now runs the full 10-minute
sample timer); Preview index and Reset remain only in the preview account menu, and the
brand link still opens the index. Courses without a language logo show the `code`
glyph in white on `brandFill` (`#2A62F0` → `#0652C4`, white 5.12–6.97:1). The
signed-in avatar (`Monogram` tone `brand`, top bar and profile) uses the same fill;
Q&A author monograms stay on ink.

| Check | Result |
| --- | --- |
| `npx tsc --noEmit` (also `--noUnusedLocals`), `npm test`, `npm run build` | PASS: 430 tests in 15 files, also with `VITE_API_BASE_URL` empty; build succeeded (same 536 kB `index` chunk advisory) |
| `npm run test:browser -- --engine chromium,webkit` | PASS Chromium 153 42/42, WebKit 26.6 42/42, including: no notice, switcher, index, reset or simulate button on six preview screens; avatar fill; generic marks on catalogue and My courses at 1280 and 390 px |
| `npx playwright test` (chromium/webkit desktop and mobile, disposable stack) | PASS 53, 3 skipped by design; stack removed |

### Consistency fixes C1–C14 — 7 November 2026

The Leader approved the fixes for the 14 inconsistencies recorded in the local
`DESIGN.md` review. Changes: one neutral course-mark container with a `brand-700`
code glyph when no language logo applies (no gradient); shared sample-course
descriptions in the preview; account avatar in solid `brand-700`; a Learner's sign-in
fallback is `/home` (the one-use `/profile` return and other roles' `/courses` are
unchanged; amendment recorded beside CT16-PROFILE P1 in `docs/setup.md`); 32 px
course marks in Learner home rows; fixed lockup steps (36 px, 32 px below 390 px) and
a `display` type step for the landing hero; flat map nodes with a hairline and a
`shadow.control` token; a `pathLine` 4/6 dash for the Path Spine, prerequisite edges,
the legend and the auth brand path; the instructor named once on course detail; no
spinner in loading states; `chromeHover` and `rose800` colour tokens; a dashed
Proposed pill in the preview index; a current state for the preview bell; the lesson
outline at `presentationTokens.outlineWidth`.

Tests changed with the behavior: the routing tests and the preview sign-in test now
expect `/home` for a Learner (and `/courses` for Instructor and Administrator); the
loading tests assert a named status line, a skeleton and no spinner; E2E `signIn`
takes the role's start page, the enrolment flow opens the catalogue from Home, the
navigation test expects Home current after sign-in, and the reload check expects the
protected route to send the visitor to Sign in. New tests cover course detail facts,
the path-line dash, the shadow and type tokens, the current bell, Learner home course
marks and the Proposed pill.

| Check | Result |
| --- | --- |
| `npx tsc --noEmit` (also `--noUnusedLocals`), `npm test`, `npm run build` | PASS: 437 tests in 15 files, also with `VITE_API_BASE_URL` empty; build succeeded (536 kB `index` chunk advisory unchanged) |
| `npm run test:browser -- --engine chromium,webkit` | PASS Chromium 153 43/43, WebKit 26.6 43/43, including lockup steps at 1280/1024/600/599/390/389/375/360 px, solid avatar, neutral marks across catalogue, My courses, Teaching and Learner home, no loading spinner, flat nodes, 4/6 dashes, single instructor mention, current bell and the 280 px outline |
| `npx playwright test` (chromium/webkit desktop and mobile, disposable stack) | PASS 53, 3 skipped by design; stack removed |

### Button system hierarchy — 7 November 2026

The Leader asked for a clearer button hierarchy with fewer black buttons. Primary
buttons, primary circles and chosen controls now use graphite (`chrome-raised`,
hover `chrome-hover`) instead of the frame's ink; secondary buttons and circles are
neutral outlined surfaces (white, `line-200` border, hover `fill-150`); tertiary
stays a Logo Blue text button; destructive stays `rose-700`. The roles are theme
tokens (`control`) and button variants (including `color="onChrome"` for the frame),
replacing local styles. Competing primaries were demoted: the placement prompt's
"Start diagnostic", the Teaching and skill-graph empty-state actions that repeat
their title-row action, and the error block's "Try again".

| Check | Result |
| --- | --- |
| `npx tsc --noEmit` (also `--noUnusedLocals`), `npm test`, `npm run build` | PASS: 440 tests in 15 files, also with `VITE_API_BASE_URL` empty; build succeeded |
| `npm run test:browser -- --engine chromium,webkit` | PASS Chromium 153 44/44, WebKit 26.6 44/44, including button ranks, hover, keyboard focus and disabled states, focus visibility and text contrast |
| `npx playwright test` (chromium/webkit desktop and mobile) | PASS 53, 3 skipped by design |

### Tile arrows and the mask-cut canvas reach — 7 November 2026

Course and lesson tile arrows (`CircleAffordance`) are now secondary by default (white,
`line-200` border, graphite arrow, `fill-150` while the tile is hovered); graphite stays
only on the Learner home "Continue learning" tile and the resume lesson. The canvas
reach no longer paints a canvas-coloured shape: the bell path is a CSS mask layer
subtracted from the header (`mask-composite: exclude`), and the canvas starts 14 px
under the bar with a `clip-path` outline whose visible corners begin at the bar edge,
so the opening shows the real canvas, or content scrolling under the sticky bar.

| Check | Result |
| --- | --- |
| `npx tsc --noEmit` (also `--noUnusedLocals`), `npm test`, `npm run build` | PASS: 442 tests in 15 files; build succeeded |
| `npm run test:browser -- --engine chromium,webkit` | PASS Chromium 153 45/45, WebKit 26.6 45/45: reach centred and symmetric from the mask geometry; pixels show canvas inside the bell, ink beside and above it, no seam at the bar edge; a white tile scrolled under the bar shows through the opening |
| `npx playwright test` (chromium/webkit desktop and mobile) | PASS 53, 3 skipped by design |

### PR #209 self-review fixes — 7 November 2026

A logic-only self-review of PR #209 found no P0–P2 defects; these P3 items were fixed:

- **My courses filter from the URL:** `/my-courses?status=active|completed|left|all`
  preselects the filter (missing or unknown values mean Joined), the control keeps the
  URL in step, and Learner home's "See all N active courses" links to `?status=active`.
- **Catalogue:** a prerequisite filter change keeps the last submitted search instead of
  applying unsent text (submit-only search).
- **Navigation links:** top-bar and drawer items route in-app only on a plain click with
  a handler; Cmd/Ctrl/Shift/Alt-clicks and items without a handler follow the native link.
- **Browser harness:** startup is inside `try/finally`, so a failed build or preview still
  stops the preview process and removes the temporary build; probes are time-bounded and
  stop at once when the preview exits.
- **E2E stack:** run-scoped labels and owner-aware `stack:down`, image IDs instead of shared
  tags, distinct ports, bounded probes and a fast failure with Vite's message.

Not changed: cleanup after `SIGKILL` of the test runner (no handler can run;
`stack:down` removes the containers and network afterwards).

| Check | Result |
| --- | --- |
| `npx tsc --noEmit` (also `--noUnusedLocals`), `npm test`, `npm run build` | PASS: 445 tests in 15 files, also with `VITE_API_BASE_URL` empty; build succeeded |
| `npm run test:browser -- --engine chromium,webkit` | PASS Chromium 153 45/45, WebKit 26.6 45/45; no preview process or temporary build left |
| Harness with a forced preview port conflict | Failed in 4 s with Vite's "Port … is already in use"; no preview process or temporary build left |
| `stack-down.mjs` with synthetic labelled resources | Removed the dead-owner and unlabelled-owner container and network; kept the ones owned by a live process |
| `npx playwright test` (chromium/webkit desktop and mobile) | PASS 53, 3 skipped by design; no container, network, preview process or temporary directory left |

### PR #209 independent review fixes — 7 November 2026

An independent logic review of PR #209 at `39fce76` found no P0–P2 defects. Commit
`a9d9dac` fixes its confirmed P3 findings and two further reliability items:

- **Native links:** the brand link, course tiles (catalogue, My courses, Learner home),
  the course-detail back link, Learner home's "Browse the catalogue" and "See all N
  active courses" links and the My courses "View course details" menu item route in-app
  only on a plain primary click with a handler (shared `isPlainClick`, as the top bar
  and drawer already did). Cmd/Ctrl/Shift/Alt, middle and already-handled clicks, and
  links without a handler, follow the browser's native behavior.
- **My courses paging:** any filter change (control, `?status=` URL or history) starts
  at page 0; returning to a filter no longer restores its earlier page, and no request
  is made for the previous filter's page.
- **Frontend readiness (E2E stack and browser harness):** readiness requires a per-run
  marker in the temporary build and a still-running preview process, so HTTP 200 from
  another server on the port is not accepted; the frontend port is chosen right before
  the preview starts.
- **E2E backend start:** a stopped container fails setup at once, and its state and
  redacted log tail are kept in the error before teardown removes it.
- **`stack:down`:** per-resource inspection and removal, resilient to resources that a
  concurrent teardown removes first; an unreadable owner is never treated as orphaned.
- **CI:** both backend readiness loops read the log once more after the last poll, so a
  start marker logged during the final sleep is not missed.

Not changed: PID reuse or a different PID namespace (runner in a container or remote
Docker) can still mislead the owner check of `stack:down`; the PostgreSQL readiness
probe has no per-call timeout; the `node --test` lifecycle tests are not run in CI.

| Check | Result |
| --- | --- |
| `npm run typecheck`; `npx vitest run --exclude 'src/app/preview/**'`; `vite build` (local, Node 24.21.0) | PASS: 444 tests in 15 files; build succeeded |
| GitHub Actions `Frontend` on `a9d9dac` ([run 42361109743](https://github.com/chi-thanh-nguyen/codemastery/actions/runs/42361109743), Ubuntu 24.04, Node 22.23.4): `npm ci`, `npm test`, `npm run build` | PASS: 458 tests in 16 files (including `presentation.browser.test.ts`, 8 tests); build succeeded. All 12 checks of that run passed |
| `node --test fixtures/stack-down.test.mjs fixtures/local-stack.test.mjs` (from `e2e/`) | PASS 15/15; no container, network or Vite process touched |
| New regression tests against the previous code (each fixed file reverted in turn, or the fix removed) | FAIL as expected: modified clicks at each of the five link sites, page restore on a filter round trip (control and history), HTTP 200 without the marker, exit during a successful probe, and the old `stack-down.mjs` |
| CI readiness steps extracted from `ci.yml` before and after, with a stub `docker` | Marker logged during the final sleep: before FAIL, after PASS; early marker, no marker and an exited container unchanged; `bash -n` PASS for both steps and the inner script |
| `local-stack.ts` loaded through Playwright 1.63's TypeScript transform (scratch config; its default export not called) | PASS; no Docker resource created |
| `npx playwright test`, `npm run test:browser` | Not run: the suite rebuilds and re-tags the shared `codemastery-e2e/backend:local` image; the harness is a visual check outside this change |


## Batch 1 bounded verification allocation — 8 November 2026

> **Current amendment:** [CT14-B1-COVERAGE-2026-11-08-r2](decisions/005-batch-01-contract-approvals.md#13-focused-f1f4-amendments--8-november-2026) preserves the locations below but replaces the original whole-Course B1-O10 with lifecycle refusal/no-reclassification checks and adds B1-O12–O16. The earlier wording below is retained as r1 history.

**CT14-B1-COVERAGE-2026-11-08-r1 — DELEGATED_APPROVED**, under the Leader's
“Batch 1 Complete Contract Approval & Integration Preparation” instruction;
Codex independently approves this allocation in [ADR005 §9](decisions/005-batch-01-contract-approvals.md#9-amendments-prerequisites-and-compatibility).
Only these Batch 1 obligations are added to the existing inventory, relative to
`backend/src/test/java/com/codemastery/`:

| Existing location | Required contract coverage |
|---|---|
| `modules/adaptive/skillgraph/SkillGraphTest.java` | B1-O01/O02/O11: representation/graph, invalid references, cycle/self/duplicate behavior, mapping consumption and deletion outcomes using explicit fixtures/doubles. |
| `modules/adaptive/mastery/AdaptiveSettingsServiceTest.java` | B1-O05/O08/O09/O10: closed policy validation, coverage completeness, readiness, disabled-mode and settings-change orchestration with conforming producer doubles. |
| `modules/adaptive/mastery/MasteryCalculatorTest.java`, `MasteryStateClassifierTest.java` | B1-O06: unchanged pure CT24 arithmetic, symbolic thresholds/equality/invalid/max-int oracles; CT36 independent review remains separate. |
| `integration/AdaptiveLearningFlowIntegrationTest.java` | B1-O01/O03/O04/O05/O07/O08/O11: real Course/Adaptive/Assessment providers, authenticated scopes, PostgreSQL concurrent graph writes and coverage/publication races, FK and rollback evidence. Use independent transactions and barriers, not sleeps or mocks. |
| `integration/MasteryProgressTransactionIntegrationTest.java` | B1-O07/O09/O10: actual evidence ordering/replay, policy reclassification and mastery/progress all-or-nothing behavior after CT08/CT22 execution contracts and providers exist. |

Do not mistake allocated tests for implemented tests or runtime success. All paths
already exist; no new suite, framework, fixture lifecycle, runner or whole Final
CT14.coverage/E2E approval is granted. Preserve shared Testcontainers/Flyway lifecycle,
synthetic identities and explicit cleanup/isolation. Real concurrency assertions
must examine committed state after success/failure and prove guard participation;
unit doubles cannot establish the PostgreSQL guarantees. The normative expected
results are [ADR005 B1-O01–O11](decisions/005-batch-01-contract-approvals.md#82-required-oracles-specification-not-executed-tests).
Application verification has not been executed by this documentation task.


### F1–F4 focused Batch 1 verification amendment

**CT14-B1-COVERAGE-2026-11-08-r2 — DELEGATED_APPROVED.** Authority, exact predecessor,
expected results and limits: [ADR005 §13](decisions/005-batch-01-contract-approvals.md#13-focused-f1f4-amendments--8-november-2026).
Use existing locations relative to `backend/src/test/java/com/codemastery/`:

| Existing suite | Amended acceptance obligations; not executed functionality |
|---|---|
| `modules/adaptive/mastery/AdaptiveSettingsServiceTest.java` | B1-O10/O12/O13: full conditional-write cases, two-tab disable protection, canonical/GCD tags, documented ABA, frozen thresholds for submitted or any mastery history, n-only/toggle/re-enable without replay, provider failure. |
| `integration/AdaptiveLearningFlowIntegrationTest.java` | B1-O12–O15 with real Spring/PostgreSQL: self-invocation, external entry, actual isolation/read-only checks, compatible joining, outer rollback/commit failure, stale preloaded JPA state and dirty queued flush, lock ordering and NOWAIT settings vs enrollment. Verify committed states, lock lifetime/release, independent Courses and bounded cancellation/deadlines. |
| `integration/MasteryProgressTransactionIntegrationTest.java` | B1-O13/O14 after CT08/CT22 approval: first submission/mastery-row creation versus threshold edit shares Course root; outer failure cannot commit callback/mastery/progress effects. No reversed lower-level lock ordering. |
| `modules/adaptive/evaluation/AdaptiveProfileScenarioTest.java` | B1-O16 required CT36 evaluation cases: one-question success/failure, alternation, one later failure replacing older larger success, unassessed preservation and same evidence with larger bank n. Outcomes follow current policy; no invented stability target, sample floor or Final dataset approval. |

Finite test budgets are explicit fixtures, not production configuration approval.
Record actual statement/transaction timeout/cancellation behavior and query plans
before implementation acceptance; NOWAIT alone does not bound all database work.
Controlled models or parsing checks in this docs PR do not prove a real Spring
proxy, PostgreSQL lock, HTTP precondition or browser implementation.

### N1 successful-PUT validator correction

[ADR005 §15](decisions/005-batch-01-contract-approvals.md#15-n1-http-validator-correction),
`CT06-ENABLEMENT-N1-2026-11-08-r1`, preserves CT14 r2's approval and existing suite
allocation while adding B1-O17/N1-01–N1-06. In
`modules/adaptive/mastery/AdaptiveSettingsServiceTest.java`, cover canonicalized
fractions and unchanged conditional-write results. In
`integration/AdaptiveLearningFlowIntegrationTest.java`, verify actual HTTP GET ETag,
successful PUT **absence** of ETag/Last-Modified for canonicalized and already-reduced
fractions/no-ops, no-store, subsequent canonical GET and stale If-Match rejection.
Use real participants for the intervening-writer schedule; no new test path is added.
Assert validator absence on the final HTTP response through applicable filters and
ingress, so a downstream layer cannot reintroduce ETag or Last-Modified after the controller.

The later FE18 verification must also prove body/tag pairing after PUT→GET, a writer
between those requests, and failed/cancelled/obsolete follow-up GET without PUT
replay or false rollback. A fresh GET can yield the same tag for unchanged state.
These are verification obligations, not implemented endpoint/browser tests or a new
CT14/CT18 prerequisite approval. Current documentation/model results are recorded
separately in ADR005 §15.


### R1/R2 conditional HTTP and no-content verification amendment

**CT14-B1-COVERAGE-2026-11-09-r3 — DELEGATED_APPROVED**, independently selected
by Codex under the Leader's explicit PR #228 review-correction instruction.
[ADR005 §16](decisions/005-batch-01-contract-approvals.md#16-r1r3-independent-review-corrections--9-november-2026)
preserves r2/N1's approval provenance, B1-O01–O17 and all paths/harness boundaries.
B1-O18/O19 are future implementation acceptance obligations, not executed features:

| Existing allocation | Exact added obligations |
|---|---|
| `modules/adaptive/mastery/AdaptiveSettingsServiceTest.java` | B1-O18a: combined If-Match/If-None-Match, strong vs weak comparisons, wildcard, nonmatching/empty lists, header grammar, missing mandatory header, early failure/no provider effects; no-op cannot bypass conditions. |
| `integration/AdaptiveLearningFlowIntegrationTest.java` | B1-O18b/c: both checks against one fresh locked snapshot, A→B writer with If-Match {A,B} and exclusion B/A, real HTTP/application/DB, normal error and date/range precedence, generic safe 412, no post-write framework reversal and preserved N1. B1-O19c: final HTTP/ingress no-content 204 framing/no Content-Length. Preserve all F3 real guard/rollback/lock-order/deadline oracles. |
| `frontend/src/shared/api/httpClient.test.ts` | B1-O19a/b: opt-in void 204 without JSON parsing/media requirement; exact success status; invalid option combinations before fetch; default JSON callers unchanged; typed overload positive/negative cases; valid/malformed error fallback, current-only 401 invalidation, abort/logout/expiry/replacement/supersession and no retries. Mock fetch through the real client; no feature-local substitute. |

Backend entries are relative to `backend/src/test/java/com/codemastery/`; frontend
entry is repository-relative and already in the approved six-file frontend inventory.
FE18 consumer/browser review must cover all five OpenAPI operation IDs, successful
mutation then failed/obsolete graph refresh, Course/session switch and uncertain
completion, using existing approved allocations when feature execution is authorized.
No new frontend test file, runner, browser route or Final coverage scope is approved.
Mock Fetch null-body assertions cannot prove wire framing. Settings GET body/tag
pairing, validator-free PUT, exact CT18 error envelope and CT36 small-sample scenarios
remain required. Concrete results for this documentation revision are recorded in
ADR005 §16 separately from these implementation obligations.

## Batch 2 contract verification obligations — 9 November 2026

[ADR006 §§8–9](decisions/006-batch-02-contract-approvals.md#8-shared-errors-acceptance-oracles-and-remaining-gates)
specifies B2-O01–B2-O16. These are **NOT_RUN implementation oracles**, not executed
results, CT14 placement approvals or independent QA acceptance. The documentation
PR reports its actual static checks separately.

Required later evidence: integer/empty progress and preserved rejoin semantics;
true-only idempotent completion; per-Course concealment/fallback and global resume
timestamp/tie/null filtering; coherent pagination; owner/role/ancestry denials;
atomic reorder; root-lock races/rollback; unchanged B1 settings/readiness. Real
PostgreSQL barriers must establish constraints/serialization/rollback; doubles
cannot. Use existing approved test locations; necessary new files/allocations
require separate approval, including any new reset test suite.

Recovery tests must independently verify identical request responses for unknown,
locked and known accounts; bounded queue/full/lost work; outstanding-row PT1M
suppression; SMTP after commit and failure isolation; 32-byte entropy/digest-only
storage; exact PT30M expiry; invalid/replaced/used/locked generic400; password rules;
User-first request/confirm races and atomic capability consumption/hash update.
Browser evidence must prove fragment erasure/no leakage, no auto-login, no token
in history/query/storage/Referer and the unchanged existing JWT behavior.

Frontend checks cover unchanged old decoders, backend-selected resume with no
client ranking, generation-safe visit responses, Markdown literal HTML and embed
host attacks. Real browser/provider evidence must show no external request before
activation, usable player/link fallback and origin-only Referer after activation.
No new Course write, reset, global resume, provider playback or atomic Adaptive
integration was executed in this documentation task. Implementation authorization,
NEW migrations/grants, SMTP/abuse qualification, residual-risk acceptance before
exposure, independent owner/QA review, protected integration and CI are separate.


## Batch 3 contract acceptance obligations — specified, NOT_RUN

[ADR007 §14](decisions/007-batch-03-contract-approvals.md#14-deterministic-acceptance-obligations-and-retained-gates)
specifies B3-O01–O13 for question/quiz authoring, exact captured attempts, saved
answers, deadline/manual/worker races, immutable grading, visibility and submitted
evidence. These are future unit and real-PostgreSQL obligations in approved
inventories, not executed tests or expanded CT14/CT34/QA04 approval. Assessment
source remains empty at the starting baseline. No event atomicity, scheduler
qualification, independent review, E2E or delivery result is claimed by this record.

## Batch 4 testing contract handoff — specified, not newly executed

[ADR008 §§3–7](decisions/008-batch-04-contract-approvals.md#3-ct34runner) approves
the existing pinned Playwright 1.63.0 runner and its five projects, disposable
per-run fixture lifecycle, Final test placement/gap map and a future SH07 CI job
contract. The current commands and local suite described above remain valid;
**there is still no E2E CI job**. Runner adoption is not CT19/CT35 case selection,
CT20.F release allocation or acceptance of historical test results.

CT14-COVERAGE-2026-11-09-r1 adds the complete placement/gap map without changing
CT14.interim, the October records or ADR005's latest
CT14-B1-COVERAGE-2026-11-09-r3/N1 allocation. Existing Surefire/Failsafe,
singleton PostgreSQL, explicit cleanup and scheduler-disabled profile remain.
Dedicated authoring, storage and Interaction/Admin integration placements not
present in the approved inventory are explicit gaps requiring bounded structural
authorization before creating files. Feature-specific storage configuration,
remaining runtime/oracle approvals and independent QA execution stay pending.

CT39-BINDING-2026-11-09-r1 and CT39-CONSUMERS-2026-11-09-r1 select no shared Clock
bean or shared consumers. AS08 retains ADR007's local injected UTC Clock and
deterministic tests; AS09/IN08 require their owning deadline/reminder contracts.
No AppConfig/TestClockConfig implementation, global clock override or SH04
Issue closure follows. Use approved local inputs, not sleeps or public time controls.

FE24/SH07, owning feature teams and QA03/04/05/06/08/10/12 have the distinct
implementation, oracle and evidence responsibilities in ADR008. No new automated
business-test, browser, Docker, CI, recovery or deployment result is claimed.

### CP-030 r2 concurrency and recovery handoff — 10 November 2026

`CT34-FIXTURES-2026-11-10-r2` amends only the r1 concurrency/recovery contract.
Keep the five-project runner and fixed outputs: one active invocation owns its
canonical checkout/output roots. The future launcher must acquire exclusivity and
write its persistent private inventory **before invoking Playwright**, not in
globalSetup after output cleanup can begin. A conflict fails without touching
outputs/resources; independently isolated checkouts may run concurrently.

[ADR008 §§10.2–10.4](decisions/008-batch-04-contract-approvals.md#102-f1-exclusive-checkout-and-output-ownership-before-runner-entry)
specifies the stable OS lock, durable run inventory, process-birth/host/namespace
checks, private cooperative preview shutdown, exact Docker daemon/resource
identity, exclusive volumes, safe temporary-path removal and acceptance cases.
Owner death releases the OS lock but does not clear an incomplete inventory.
Uncertain ownership, unavailable process control, partial cleanup or unaccounted
resources must be preserved/reported and block reuse of that checkout. PID
existence/absence or Docker-only cleanup cannot establish full recovery success.

FE24 and QA08 must implement/qualify that protocol separately: current direct
Playwright scripts and PID-labelled `stack:down` are not r2-conformant. No new
command or runtime result is supplied here. CP-035 r1 consumes §4 **as amended by
r2**; [§10.5](decisions/008-batch-04-contract-approvals.md#105-m1-downstream-ci-execution-budget-guidance)
adds nonessential measured-budget guidance reserving teardown/report time, without
new timeout numbers. CP-029/031/032/036 and effective predecessor contracts remain
unchanged; CT19/CT35 feature cases and CT20.F allocation retain their own scope.

## CT17 r2 executed mechanism qualification — 10 November 2026

**Q03 correction:** the original PostgreSQL run below used excess database-level
CREATE and did not prove P6 compliance. Preserve its history and result hashes;
the [new focused reproduction](#q03p6-focused-reproduction--10-november-2026)
supplies the corrected evidence. Other qualification scopes remain unchanged.

Authority, exact image/binary/platform identities, synthetic input inventory,
results/hashes and limitations: [ADR009 §§9.5–9.6](decisions/009-ct17-final-runtime-recovery.md#95-qualified-mechanisms-and-security-boundaries).
The existing approved test inventories are unchanged. All harness scripts and
synthetic artifacts are local-only in
`/private/tmp/codemastery-ct17-qualification-20261110/`, protected by directory
0700; no new source/test path, dependency or production implementation was added.
`evidence-index.json` identifies preserved scripts/results by SHA256;
`commands.jsonl` records actual UTC commands, exit codes, durations and stdout
hashes. It retains failed initial attempts and corrections rather than hiding them.
Ephemeral credentials/keys and plaintext fixture archives must remain private.

The executed harness used the exact existing candidates, `--pull never`, isolated
internal Docker networking, no port publication and fresh uniquely labelled volumes.
It created V001–V006 schema using the actual migration SQL, a clearly synthetic
migration-digest history (not Flyway-engine history), object-reference/sequence
fixtures, version/delete-marker/metadata/bucket/IAM/service-account fixtures and
an incomplete multipart upload. No frontend/backend application ran. Source and
restored cold tree hashes/UID/GID/modes matched; PostgreSQL constraints/history/
sequence/ownership and object-reference content matched. Restored user and service
account could read authorized versions, could not write, and could not list the
private bucket. The restored in-progress multipart upload completed correctly.

Core **actually executed command shapes** (names and ephemeral credentials are
redacted; source/target aliases refer only to the disposable fixture):

```bash
# Exact candidate MC has bash, not /bin/sh; read-only root needs a writable config.
mc --config-dir /tmp/mc --json mb q/recovery
mc --config-dir /tmp/mc --json version enable q/recovery
mc --config-dir /tmp/mc --json pipe q/recovery/versioned.bin
mc --config-dir /tmp/mc --json tag set q/recovery/versioned.bin 'course=synthetic&purpose=recovery'
mc --config-dir /tmp/mc --json ls --recursive --versions q/recovery
mc --config-dir /tmp/mc --json admin group info q fixturegroup
mc --config-dir /tmp/mc --json admin user svcacct info q fixtureservice
mc --config-dir /tmp/mc --json admin config export q
# Raw signed S3 CreateMultipartUpload/UploadPart/ListParts/CompleteMultipartUpload
# used urllib/HMAC-SHA256 in the isolated Python helper (s3.py).

# Source was drained; client disconnected; late fixture INSERT failed read-only.
pg_dump -U fixture_migrator -d fixture -Fc
# Stop exact MinIO server; prevent restart. Mount source /data READ-ONLY in helper.
cd /data && tar --numeric-owner -cpf - .
# Mount a different empty target volume; preserve entire tree including .minio.sys.
cd /data && tar --numeric-owner -xpf -
# Original PG target: excess database CREATE; see the Q03/P6 correction below.
pg_restore -U fixture_migrator -d fixture --no-owner --no-acl --exit-on-error
# Provision bounded runtime SELECT separately, assert runtime CREATE TABLE denied.
# Compare all recorded inventories; test old/current versions and denied access.

# Existing OpenSSL 3 helper, synthetic recipient/signing keys only.
set -euo pipefail
{ printf 'CMREC1\n'; cat component; } |
  openssl cms -encrypt -binary -aes-256-gcm -outform DER -stream \
    -recip recipient.crt -keyopt rsa_padding_mode:oaep \
    -keyopt rsa_oaep_md:sha256 -keyopt rsa_mgf1_md:sha256 -out component.cms
openssl cms -decrypt -binary -inform DER -in component.cms \
  -inkey recipient.key -out component.staging
# Only after exit 0: validate 7-byte header, strip it, compare length/SHA256.
# Production uses capacity-bounded protected tmpfs; local plaintext was synthetic.
openssl dgst -sha256 -sign signer.key -sigopt rsa_padding_mode:pss \
  -sigopt rsa_pss_saltlen:digest -out manifest.sig manifest.json
openssl dgst -sha256 -verify signer.pub -sigopt rsa_padding_mode:pss \
  -sigopt rsa_pss_saltlen:digest -signature manifest.sig manifest.json
```

`qualify.py` records setup/fixture inputs; `capture.py` and `verify-restored.py`
record the cold capture/PG restore and exact comparisons. `crypto.sh` tests framed
empty/binary/16-MiB input, wrong/missing key, ciphertext mutation/truncation, producer
failure and forged signature/trust-key rejection. `bundle.sh` applies the same
crypto to the real **synthetic** PG/tar components and recovers with an encrypted
PKCS8 escrow copy; wrong passphrase fails. `publication.py` tests actual local
file copies and eight interrupted-boundary states, incomplete/corrupt/stale/forged
sets and safe retention/locking. `tls-server.py`/`tls-client.py` transfer ciphertext
over isolated authenticated TLS; unknown/wrong CA, hostname mismatch and wrong
bearer token fail. These are reference harnesses, not deployed implementations.

Reproduction requires an authorized disposable environment with the recorded
already-available images and preserved scripts, new unique task names/volumes,
synthetic credentials generated anew, and no production mounts. The corrected
fixture flow is `qualify.py init help fixture`, `capture.py prepare`,
`capture.py capture`, then the OpenSSL helper runs of `crypto.sh`/`bundle.sh`,
`publication.py` and isolated TLS helpers. The original interrupted fixture used
`resume-fixture.py`; command history identifies that recovery explicitly.
Never rerun `init` over an occupied namespace or claim the harness is a repository
production command. Remove only the new labelled task resources after readback;
the executed run removed its six containers, four volumes and one internal network.

Important observed limits: raw-empty CMS streaming failed on this OpenSSL build;
the selected mandatory framing round-tripped empty payload successfully. MinIO
CORS PUT returned 501; no configured-CORS recovery was tested. Default read-only
transactions do not exclude privileged writers. Native-VM performance, actual
Keychain/systemd/acme integration, Azure Entra/RBAC/transfer, physical independent
copy, Flyway-engine validation, full feature QA and complete incident RTO remain
**NOT_RUN**. Completion-marker/ledger checks used two local directories, not two
independent failure domains. Changed artifacts/dependencies require affected
qualification again. These limits are retained in CP-034's operational F2 gates;
they are not fabricated PASS or unresolved routine mechanism selections.

## Q03/P6 focused reproduction — 10 November 2026

[ADR009 §11](decisions/009-ct17-final-runtime-recovery.md#11-q03p6-evidence-correction--10-november-2026)
records authority, original F1/P2, exact identities, results and new hashes. This
is a new PostgreSQL-only reproduction, not a replay: the original temporary
harness is unavailable. The local private evidence root is
`/private/tmp/codemastery-q03-p6-20261110/`; no tracked test or production code changed.

Actually executed launcher: `python3 /private/tmp/codemastery-q03-p6-20261110/qualify.py`.
It used the original recorded linux/amd64 image ID, `--pull never`, two fresh
network-none containers and tmpfs database storage. PostgreSQL/server/dump/restore
all reported **17.11**. Actual qualified command shapes, with synthetic passwords
supplied through process environment and SQL through stdin:

```bash
# Unchanged deploy.sh is sourced for SQL generation only; no deployment entrypoint.
role_policy_sql
application_grants_sql
runtime_privilege_violations_sql present
runtime_privilege_violations_sql complete
# Authenticated sessions: verify current_user, then execute the recorded probes.
psql -X -qAt -v ON_ERROR_STOP=1 -v VERBOSITY=verbose \
  -h 127.0.0.1 -U fixture_migrator -d fixture
pg_dump -h 127.0.0.1 -U fixture_migrator -d fixture -Fc
pg_restore --list
pg_restore -h 127.0.0.1 -U fixture_migrator -d fixture \
  --no-owner --no-acl --exit-on-error
```

Bootstrap kept database ownership; neither role received database CREATE,
TEMPORARY, elevated attributes or memberships. PUBLIC database/schema rights were
revoked; schema public belongs to the migrator, runtime has USAGE only. Actual
P6 role policy, reviewed application grants and full matrix queries ran before
and after import. Catalog ACL/ownership/default/membership snapshots complement
execution denials; `rolcreatedb=false` alone is never treated as sufficient.

**PASS: 75 recorded cases**, including actual `42501` permission denials (exit 3),
`P0001` rejections of direct/PUBLIC/indirect/ownership drift, allowed schema DDL,
raw V001–V006 migrations, custom dump/restore and approved runtime access. Separate
`23505`/`23514`/`23503` failures prove restored unique/CHECK/FK enforcement. Role
identity was authenticated before negative probes. Both populated source and
restored target pass the actual complete runtime matrix; all table data,
structure/index/constraint/owner comparisons and synthetic object references match.
The history fixture uses a clearly synthetic layout in the reserved migrator-only
`flyway_schema_history` slot, not a Flyway-generated history. The project schema
has zero sequences; separate transactional sequence probes leave no archived object.

The first attempt's checks reached completion, but cleanup assertion casing caused
an overall failed command; its journal and exact-container reconciliation remain
under `initial-attempt/`. The corrected full run completed at **09:39:59 UTC**;
both task containers were verified absent, with no persistent volumes or network
created. `commands.jsonl`, `result.json`, emitted SQL and comparison artifacts are
indexed separately from the old §9.6 evidence. Reproduction needs fresh names and
new synthetic credentials; never reuse occupied namespaces or production mounts.

This resolves the tested Q03/P6 discrepancy, subject to independent re-review.
Flyway-engine validation/full-product F2/Q08 and real Q07 RPO/RTO remain NOT_RUN.
MinIO/CMS/cloud/renewal/Keychain/scheduler results retain their previous limits and
were not rerun. No deployment, risk acceptance or spending permission is supplied.

## Batch 5 contract acceptance obligations — specified, NOT_RUN

[ADR010 §14](decisions/010-batch-05-contract-approvals.md#14-errors-acceptance-obligations-and-retained-gates)
specifies oracles B5-O01–B5-O16 for authorization before storage, object keys,
type/filename/size validation and staged error precedence, store-then-commit
deletions by transaction outcome (including a lost commit acknowledgement, which
keeps both objects), the exclusive deadline and strictly later replacement time,
Assignment-row serialization, grading precision and the opaque `submittedAt`
precondition, download headers, the download/replacement race and missing-object
versus provider failures, no mastery effect of grading, the shared widget, lazy
multipart binding and downloads that hold no database connection, plus the Java 25
declaration/checked-close lifecycle (B5-O15) and raw multipart/download bytes with
filename-preserving browser FormData (B5-O16). Compile/stream and local serialization
probes do not prove application storage or transport integration. They use
ADR008 §5's existing placements (`SubmissionServiceTest`,
`AuthorizationIntegrationTest`, existing Course and frontend suites). Real MinIO
success, rollback and recovery tests still need qualified isolated storage
configuration and the dedicated storage integration placement that ADR008 records
as a gap; no MinIO testcontainer, new test file or dependency is approved, and no
result is claimed.

## Batch 6 verification plan — specified, NOT_RUN

[ADR011 §16](decisions/011-batch-06-contract-approvals.md#16-ct08verification-plan)
specifies real-PostgreSQL cases B6-V01–B6-V12 for the atomic submission unit,
rollback on injected faults including uncommitted POST answers, scheduler
finalization from the latest committed save (including another tab's set), the
conditional save-before-submit guarantee and deterministic intervening-writer
schedule (V12/C01), duplicates, evidence ordering,
disabled/not-ready/unconfigured Courses, left learners, same-Course concurrency,
held-root execution-scope and evidence-read admission enforcement, the difficulty
port's preconditions, the single approved event subscriber, deterministic-failure
recovery including disabling adaptive mode, learner recommendation validity and
titles, completed remedial reviews versus no-candidate completion, selected-reason
versus unrelated renames, omitted Home Course rows versus included null guidance,
and the diagnostic gate and server-chosen placement Quiz, plus client
oracle B6-C01 (save before submit) in the existing `assessment.test.tsx`. QA04 owns
`MasteryProgressTransactionIntegrationTest`, QA03 `AdaptiveLearningFlowIntegrationTest`;
unit doubles live in the existing Adaptive, Assessment and Course suites. Rule
oracles B6-R01–B6-R16 (ADR011 §10) belong to the existing decision and mastery unit
tests; CT36.F keeps the evaluation scenarios. Faults come only from test-scoped bean
overrides, outcomes from independent committed reads and concurrency from barriers.
No new test file, dependency or result is claimed.

## Batch 7 acceptance obligations — specified, NOT_RUN

[ADR012 §15](decisions/012-batch-07-contract-approvals.md#15-acceptance-oracles--specified-not_run)
specifies oracles B7-O01–B7-O12: derived notification IDs and idempotent reminders;
after-commit subscriptions that never notify for a rolled-back unit and never fail
their producer, with exactly the two approved `QuizSubmittedEvent` subscribers
(this also fixes the expected set of ADR011's B6-V04); the exclusive reminder window;
owner-scoped notification reads; CT03 Q&A admission, hidden-post concealment and
one-level replies; reply notifications; same-transaction audit rows and no-op
non-auditing; self and last-Administrator protections under concurrency; Course-root
locking for Administrator hiding; moderation resolution of every open report on a
target; hand-computed reporting metrics; and OpenAPI/plain-text rendering. They use
ADR008 §5's existing placements (`NotificationEventListenerTest`,
`DeadlineReminderSchedulerTest`, `UserManagementServiceTest`,
`AuthorizationIntegrationTest` and the owning modules' existing suites). *Update, 15
November 2026:* ADR012 §19.3 (`CT14-INTEGRATION-PLACEMENT-2026-11-15-r1`) closes the
structural gaps with exactly two new suites extending `AbstractIntegrationTest`:
`InteractionAdminIntegrationTest` for the real-PostgreSQL Batch 7 cases (including
B7-O13 pool exhaustion) and `FileStorageIntegrationTest` for ADR010's real MinIO cases,
using the pinned Compose MinIO image in a `GenericContainer`. Neither file exists yet,
no dependency is added and no result is claimed. Reminder tests supply explicitly
named CT38 case values and a fixed injected clock.

*Independent review remediation, 15 November 2026* ([ADR012 §21](decisions/012-batch-07-contract-approvals.md#21-independent-review-remediation--15-november-2026-third-instruction)):
B7-O02 now expects Adaptive's plain in-transaction `QuizSubmittedEvent` listener and
Interaction's after-commit listener as the only subscribers, and five oracles are
added to `InteractionAdminIntegrationTest`'s scope or the consumer's CT17-P6 test:
B7-O14 (an Adaptive fault rolls back the submission unit and suppresses notifications;
an Interaction fault does not affect the committed unit), B7-O15 (Assignment
admission under concurrency through the `FOR SHARE` `lockNotificationState`: hide,
deadline edit, rebinding and submission races for reminders and graded
notifications), B7-O16 (the foreign-key lock cycle between a reply notification and an
Administrator mutation finishes without deadlock under `FOR NO KEY UPDATE`), B7-O17
(Administrator Course names through `userSummaries`, never the published-scope
`instructorDisplayNames`) and B7-O18 (runtime-role ceiling for the CT22.resume
pointer and time and for `quiz_questions.position`). All remain specified, NOT_RUN.

## Batch 8 E2E selection, evaluation and Final coverage — specified, NOT_RUN

[ADR013](decisions/013-batch-08-contract-approvals.md) selects the Final E2E cases
and evaluation procedures. **CT19:** the fourteen merged `learner-flow.spec.ts` tests
are adopted as cases L-A1–L-A5 (historical results are not accepted), and FE25 adds
L-N1 (lesson completion, progress, resume), L-N2 (timed quiz and results), L-N3
(assignment submission, grading and notification) and L-N4 (lesson Q&A reply
notification and report); L-N3/L-N4 depend on ADR012. **CT35:** FE26 implements A-1
(two contrasting placement profiles with different guidance and Mastery Maps), A-2
(prerequisite warning without a hard block) and A-3 (skipped placement) in
`adaptive-flow.spec.ts`. Both suites keep CT34 r2's disposable stack and build their
data per test through approved REST operations as isolated records whose Course titles
start with `~E2E Journey `; because those published Courses join the shared catalogue,
L-A1/L-A4 assert fixture-relative results (first twenty fixture Courses on page 1,
total at least 21). No new fixture file or shared seed is used. *(ADR013 §17 F8-02.)* **CT36:** profile scripts P1–P3, gap scenarios G01–G24 and mastery cases
M01–M11 and the discriminating
variants G′01–G′24 and P2-R are policy-independent where stated and live in the existing scenario JSON
files and evaluation/integration tests. **CT20.F:** release requires L-A3, L-A4,
L-N1 and L-N2 on `chromium-desktop` and `chromium-mobile`, A-1 on `chromium-desktop`,
the CT36.F-measures report, deployment smoke checks, sample-data loading and the CI
jobs on one exact commit; other cases and projects are attempted and reported but do
not block release. No case has been executed for this record.

*Independent review remediation, 15 November 2026* ([ADR013 §18](decisions/013-batch-08-contract-approvals.md#18-independent-review-remediation--15-november-2026-third-instruction)):
the fixture-relative rule now also covers L-A5, whose far-page check keeps the exact
`content`, `page` and `size` but asserts `totalElements ≥ 21` and
`totalPages = ceil(totalElements / 20)` from the same response. Journey Courses must
not contain `foundations` and must have no prerequisite text, so L-A1's search and
filter results stay exact. L-N3 runs against a per-run MinIO added to the
disposable stack by ADR013 §7.3: Compose-pinned images, a private network, generated
credentials, a bucket provisioned by the unchanged Compose initializer, and
removal with the run. L-N3 checks an upload/download byte round trip. Nothing is
implemented or run.

*Leader decision E02, 15 November 2026* ([ADR013 §19](decisions/013-batch-08-contract-approvals.md#19-leader-decision-e02-and-practice-difficulty-consumption--15-november-2026)): the
mastery-based practice recommendation adds oracles B8-P01–B8-P11. They cover the
mapping, the governing skill and its ties, the candidate Quizzes, fallback, null
results, admission, read-only isolation from quiz capture and evidence, the
Assessment read's allowlist, the SD04 outputs and the frontend entry. They are placed
in the existing `PracticeDifficultySelectorTest`, the service unit tests,
`AuthorizationIntegrationTest`, `AdaptiveLearningFlowIntegrationTest` and the
learning frontend tests. CT35 adds A-4 (Hard versus Easy practice for contrasting
learners, plus a Medium-to-Easy fallback), which CT20.F r2 makes release-required on
`chromium-desktop` with A-1. All are specified, NOT_RUN.

## FE24 CT34 r2 lifecycle implementation — 13 November 2026

### Authority and scope

Project Leader and repository Owner @chi-thanh-nguyen authorized this bounded work
on 2026-11-13 (Asia/Ho_Chi_Minh) in “CodeMastery — Phase 3: Contract-Compliant
Playwright E2E CI Integration”. It implements `CT34-FIXTURES-2026-11-10-r2`
([ADR008 §10](decisions/008-batch-04-contract-approvals.md#10-f1f2-fixture-amendment--10-november-2026))
for the existing disposable stack, keeping `CT34-RUNNER-2026-11-09-r1` unchanged: the
same Playwright 1.63.0 lock, five projects, timeouts, no retries, output paths,
fixtures, readiness limits and authentication behaviour. No workflow, ruleset,
required check, dependency, test journey or application behaviour changes here. This
is implementation and recorded verification, not independent QA08 acceptance, review
approval or Final coverage acceptance.

### Contract mapping

| ADR008 clause | Implementation |
| --- | --- |
| §10.2 exclusivity before runner entry | `fixtures/lifecycle.py run` takes the lock and writes the run record before it starts `playwright test`; `npm test` and `npm run test:chromium` go through it; `playwright.config.ts` refuses an unadmitted output-producing invocation at config load, before Playwright touches outputs |
| §10.2 registry and lock | `~/.codemastery-e2e/` from `pwd.getpwuid(geteuid())` (not `HOME`), directories 0700, records and lock 0600 (verified again on every read, by both the coordinator and the Node side), owner and symlink checks, `O_NOFOLLOW`; nonblocking `fcntl.flock` on a stable `locks/<dev>-<ino>.lock` that is never unlinked or replaced and is not inherited by children; a second-process probe verifies exclusion on the actual lock file; known network/shared filesystems, and filesystems whose type cannot be determined, are refused; the checkout is derived only from the coordinator's own resolved location |
| §10.2 output roots | The three roots are inspected component by component without following symlinks; overlap, hard-link sharing and identities already claimed by another checkout's nonterminal record are refused |
| §10.2 admission | Conflict exits 2 without starting anything; a nonterminal or unreadable record exits 3 until recovery completes, including one recorded under an earlier device number of the same checkout path and inode; a completed record is archived to `archive/<key>.last.json` without deleting outputs or resources; the Playwright configuration and `stack-down.mjs` accept only a process whose parent is the recorded coordinator |
| §10.3 inventory | `fixtures/run-inventory.mjs` and the coordinator write one bounded (256 KiB, 128 intents), versioned record per checkout key with same-directory atomic replacement and file/directory `fsync`; intents are written before each resource is created and carry the observed ID afterwards; no credentials, tokens, environment or commands are stored |
| §10.3 process identity | Linux `/proc/<pid>/stat` start ticks with boot ID and PID namespace; macOS kernel `kinfo_proc` start time (microseconds) with `kern.boottime`; the host is the OS machine identity (macOS host UUID, Linux machine-id), not the network-assigned hostname; supervisor, runner, builder and preview are recorded; the coordinator writes the runner's intent before the spawn and its PID, birth identity and process group right after it, and global setup refuses to act unless it is that recorded runner; the builder and preview are recorded before they are spawned; the runner's process group must be empty (Playwright workers included) before the build or the stack is touched; Vite is started from its resolved entry point, not through `npx`, so the recorded PID is the real writer |
| §10.3 preview recovery | `fixtures/preview-server.mjs` hosts the build through the pinned Vite preview API and listens on `sockets/<runId>.sock` (0600); it closes its own server only for its own run ID and birth identity; no recovered PID is signalled; an unavailable channel preserves the process |
| §10.3 Docker | Endpoint, context and daemon ID are pinned at setup; every later Docker call is bound to that endpoint (`DOCKER_HOST`); the client context and daemon ID are re-verified before cleanup, behind every not-found verdict and before completion; only Unix-socket/pipe contexts are accepted; absence is only Docker's own not-found answer for the exact ID; removal targets recorded IDs corroborated by the run labels, and recorded `removed`/`absent` states are re-verified on every pass; pending intents are reconciled by exact run label and name; each container's volume association is a pending intent until its own mounts are recorded, a container with an unresolved association is never removed, and a vanished container leaves an unaccounted volume; a volume is removed only when it is exclusive to this run's container and Docker labels it anonymous; named volumes and bind mounts are refused; nothing Docker-side is touched while a writer is unresolved; resources of no current inventory (including pre-r2 PID-only ones) are reported, never removed, and do not block this run; no prune is issued |
| §10.3 completion | One engine (`fixtures/stack-recovery.mjs`) serves teardown, the coordinator's finalization and `npm run stack:down`; every inventory change is a fenced compare-and-swap under the record lock (run ID, lock key, `fence` and `revision` must match), and each recovery takes the record over with a new fence; finalizers are recorded writers; the temporary build is removed only after its parent, owner, device/inode and run marker are verified; the run becomes `complete` only after the coordinator has verified the runner's own exit; otherwise it stays nonterminal and the command exits nonzero |

The image cache tag is now per checkout (`codemastery-e2e/<component>:co-<dev>-<ino>`)
instead of the shared `:local` tag; containers still run the exact built image ID and
teardown removes no image. This was needed because, with a containerd image store,
moving a tag deletes the image it previously named (reproduced directly), so a tag
shared across checkouts let one checkout's concurrent build delete another run's
exact image before it started. Existing `:local` tags are left untouched.

### Executed verification

The results in this subsection were executed before the independent review, at the
heads `626e9f5`/`47f6f85`; the remediation record below holds the verification of the
current head.

Host: macOS 27.2 (arm64), Python 3.9.6, Node 24.21.0, npm 11.19.0, Docker Desktop
29.8.1 (containerd image store), Playwright 1.63.0 with Chromium 153.0.8010.12
(r1243), Firefox 155.0 (r1543) and WebKit 26.6 (r2359). Every real run cleared
`E2E_BASE_URL`, `E2E_API_URL`, `E2E_BACKEND_IMAGE` and `E2E_POSTGRES_IMAGE`. Only
synthetic accounts in disposable local stacks were used; no Azure VM, production
endpoint or real account was involved.

`node --test fixtures/*.test.mjs` (from `e2e/`): **PASS 85/85**. These are stub
scenarios (fake Docker, stub Vite, real coordinator, real processes and sockets); they
are not Docker or browser integration evidence.

Real Docker and Vite (and browsers where tests ran):

| Scenario | Observed result |
| --- | --- |
| Unadmitted `npx playwright test` | Refused at config load, exit 1; sentinels in all three output roots unchanged |
| Normal run, all five projects (`npm test`) | 70 results: `chromium-desktop` 14 passed; `webkit-desktop`, `chromium-mobile`, `webkit-mobile` 13 passed each; 4 skipped (the suite's own `API-only checks run once.`); `firefox-desktop` 13 **failed at browser launch** (see limits); 2 containers, 1 anonymous volume, 1 network and the build removed and verified; exit 1 because of the Firefox failures |
| Setup failure (a real defect found here, then fixed) | Exit 1 with the setup error; containers, volume, network and build removed and verified; Docker volumes, networks and containers byte-identical to the baseline |
| Deliberate assertion failure (temporary uncommitted spec, removed afterwards) | Exit 1; cleanup complete |
| Same checkout while a real run is active | Second invocation exit 2 immediately; no runner started, record unchanged |
| Graceful interruption (`SIGINT` to the coordinator during setup) | Signal forwarded, teardown ran, every resource verified removed, record `complete`, exit 130 |
| Coordinator and runner `SIGKILL`ed mid-test (7 tests done) | Record stayed `active`; preview and containers left running; new run exit 3; `npm run stack:down` stopped the live preview through its control channel, removed containers, anonymous volume, network and build with verified absence; the next run was admitted and passed |
| Pending creation intent whose ID was never persisted | The single run-labelled container was adopted, its anonymous volume recorded from its mounts before removal, both verified gone |
| Pending intent with nothing created | Resolved `absent` |
| Daemon ID drift | All Docker resources preserved, exit 1, record `incomplete` |
| Pre-r2 container with only a dead-PID label | Preserved and reported, exit 1; completion only after the operator removed it |
| Two isolated checkouts concurrently (temporary git worktree) | Before the cache-tag change: the second run failed closed (`No such image`) and cleaned up. After it: both admitted with distinct lock keys, run IDs, image IDs, ports, temporary builds and control sockets; both `8 passed`; both cleaned up independently |
| After all runs | 135 pre-existing volumes, networks and containers identical to the baseline; no preview process and no socket file left |

Defects found and fixed during this verification: Vite's `exports` map does not
expose `bin/vite.js` (now resolved from the manifest's `bin` field); the runner doing
its own teardown blocked removal of the build (it is now recorded as reconciling
itself, and completion waits for the coordinator to verify its exit); the shared
cache-tag race above; and control socket files left after a preview closed itself.

### Limits and outstanding evidence

- **Ubuntu is not yet qualified.** The Linux identity and filesystem paths are
  implemented but were not executed here; the SH07 CI job is their first execution.
- **`firefox-desktop` is blocked on this macOS host.** Playwright's pinned Firefox exits
  with “Could not find profile folder” when launched directly, outside the lifecycle and
  outside the agent sandbox, so this is a local browser problem, not a lifecycle
  result. Those 13 cases are failed/blocked evidence, not passes.
- Real PID reuse, foreign host/namespace records, unreadable processes, Docker removal
  timeouts/refusals and network-filesystem refusal were exercised only through stubs
  and crafted records. A crash exactly between `docker run` and ID persistence was
  represented by a crafted pending intent against a real container.
- Explicit external mode (`E2E_BASE_URL`) was not executed.
- A hard kill cannot promise automatic recovery: a preview whose channel is gone is
  preserved for operator reconciliation.
- QA08 independent verification, CODEOWNER review and protected integration remain
  separate gates. CT19/CT35/CT20.F Final coverage is not addressed.

### Independent review remediation — 13 November 2026

An independent review of head `47f6f85` reported three blocking findings, all reproduced
before they were fixed:

| Finding | Reproduction at `47f6f85` | Fix |
| --- | --- | --- |
| P1-1 false Docker absence | The absence pattern accepted any `no such`, so Docker's real `failed to connect … connect: no such file or directory` was read as absence; a later pass trusted the persisted `absent` states and recorded `complete` while the container and network still existed. Docker calls were not bound to the pinned endpoint | Every Docker call is bound to the recorded endpoint; only Docker's not-found answer for the exact ID counts, and only after the endpoint is confirmed to reach the recorded daemon; recorded states are re-verified on every pass; the daemon is re-identified before completion |
| P1-2 volume association gap | A container recorded with its ID, then gone before its anonymous volume was recorded, produced `complete`; the setup swallowed a failed mount inspection as "no volume" | Container intents carry a pending volume association written before creation and resolved from the container's own mounts; a failed inspection is an error; a vanished container leaves an unaccounted volume and an incomplete run; a volume is removed only when Docker labels it anonymous |
| P1-3 device renumbering | After a reboot this host's APFS volume moved from device 16777230 to 16777231 (same checkout inode), so a crafted incomplete record under the earlier key no longer blocked the checkout and `stack:down` reported success | A nonterminal record with the same canonical path and inode under another device number blocks admission (exit 3); `stack:down` reconciles it under its own lock first; a different path or inode is never associated |

The review's P2-5 (Docker cleanup while a writer is alive) was reclassified as blocking:
with only the coordinator killed, `stack:down` removed the containers of a runner that was
still executing tests. The engine now preserves the temporary build and every Docker
resource while any recorded writer is not verified as exited. Confirmed CT34 r2 deviations
among the other P2 findings were also fixed: a verified (not merely present) hand-down in
`playwright.config.ts` and `stack-down.mjs`, and removal of the checkout-override variable;
pre-r2 and other-account resources no longer block every checkout's completion; record mode,
symlink and structure validation on read, and temporary-path name ancestry; writer records
before spawn; machine identity instead of hostname; refusal of an undeterminable filesystem
type and `F_FULLFSYNC` for the coordinator's writes on macOS; a stale header comment; and the
new `lifecycle:inspect` npm script was withdrawn (the coordinator's `inspect` mode remains).

`node --test fixtures/*.test.mjs` (from `e2e/`): **PASS 111/111** (stub scenarios: fake
Docker using Docker 29.8.1's real error texts, stub Vite, real coordinator, processes and
sockets; not Docker integration evidence). Real Docker, Vite and Chromium on the same macOS host
(Docker Desktop 29.8.1), from a temporary worktree and a second isolated worktree, with
`E2E_BASE_URL`, `E2E_API_URL`, `E2E_BACKEND_IMAGE` and `E2E_POSTGRES_IMAGE` cleared except
where stated:

| Scenario | Observed result |
| --- | --- |
| Normal run (`npm run test:chromium`) | 27 passed, 1 skipped (the suite's API-only case); 2 containers, 1 anonymous volume, 1 network and the build removed and verified; record `complete`; exit 0; worker processes loaded the guarded configuration |
| Same checkout while a run is active | Second `npm test` exit 2; nothing started |
| Coordinator alone `SIGKILL`ed mid-test, runner still running | `stack:down` exit 1: runner preserved by birth identity, preview stopped over its channel, build and all Docker resources preserved; `npm test` exit 3; after the runner ended, its own teardown removed everything and the next `stack:down` completed (exit 0) |
| Coordinator and runner `SIGKILL`ed mid-test | Record stayed `active`; `npm test` exit 3; `stack:down` with `DOCKER_CONTEXT=default` (whose socket does not exist on this host) got Docker's real `connect: no such file or directory` and preserved all four resources (exit 1); `stack:down` on the recorded context then removed them with verified absence (exit 0); no preview process or socket left |
| `SIGINT` to the coordinator during setup | The runner abandoned setup; finalization adopted the backend container from its creation intent, recorded its volume association and removed everything; record `complete` |
| `SIGINT` to the coordinator during tests | Forwarded; the runner's teardown removed everything; finalization verified the runner's exit; record `complete` |
| Setup failure (`E2E_BACKEND_IMAGE` set to an existing image that exits at once) | Exit 1 with the backend failure and its diagnostics; every resource removed and verified; record `complete` |
| Deliberate assertion failure (temporary spec, removed afterwards) | Exit 1, `1 failed`; cleanup complete |
| Two isolated checkouts concurrently | Distinct lock keys, run IDs, containers, volumes and networks; both `27 passed`; both complete; neither blocked the other |
| After all runs | Containers, volumes and networks identical to the baseline taken before the first run |

Not executed: Ubuntu (the Linux identity, machine-id and filesystem paths run first in the
stacked SH07 job), real PID reuse, a real daemon outage during cleanup (represented by the
real error text in failure injection and by the drifted-context run), a real reboot with an
incomplete run (represented by a crafted record with the observed device renumbering),
`firefox-desktop` and WebKit (not part of these runs), and external mode. Processes recorded
in a previous boot remain preserved for explicit operator reconciliation, as §10.3 requires.

### Independent review round 2 remediation — 13 November 2026

A second independent review of head `dcbf877` reported two blocking findings. Both were
reproduced on `dcbf877` before they were fixed, and both reproductions were rerun on the
fixed code:

| Finding | Reproduction at `dcbf877` | Fix |
| --- | --- | --- |
| N1 runner ownership | Real coordinator, stub runner that passes the config-load admission check and writes the inventory 6 s later (as global setup does): after `SIGKILL` of the coordinator, `stack:down` exited 0 with the record `complete` while the runner was alive; a new run was admitted (exit 0); the orphan then wrote into that later run's record and overwrote `test-results/results.json` | The coordinator writes the runner's creation intent into the admitting record before the spawn and its PID, birth identity and process group right after it (a crash in between leaves a PID-less writer that recovery preserves); global setup acts only when it is that recorded runner; inventory changes from a process whose coordinator is gone are refused (the round 3 review showed this parent check alone was insufficient; it is now backed by the fenced compare-and-swap described above). Rerun: `stack:down` exit 1 with the runner preserved, new run exit 3, the orphan's write refused (`OwnershipLost`), outputs unchanged, recovery exit 0 after the runner exited |
| N2 container removed before its volume association | Recovery engine with a Docker double whose first mount inspection fails: the container was removed while its association was pending; the next pass marked the volume unaccounted and the anonymous volume was orphaned | A container whose association is unresolved is preserved (state `preserved`, association still `pending`); the next pass reads its mounts, records the volume, then removes the container and the volume. Rerun: pass 1 incomplete with the container kept, pass 2 complete with no orphaned volume |

The remaining review items were also addressed. Playwright workers (P2): in the pinned
Playwright 1.63, a worker whose runner disappears runs its own teardown and force-exits only
after 30 s, so it could still write `test-results/artifacts` after finalization. The runner now
leads its own process group (a new session), the coordinator waits for that group to empty
before finalizing, and finalization and `stack:down` preserve the build and the stack while a
live member remains. Membership is read from the OS (`/proc` on Linux, `KERN_PROC_PGRP` on
macOS), zombies excluded, and nothing is signalled. (The round 3 review showed that this round's
parent check did not fully stop an orphaned writer (N10) and that an incomplete enumeration or a
missing group identity could still count as an empty group; both are corrected in round 3.) The coordinator validates record structure and refuses a malformed
record with a clear error, while another checkout's malformed record is skipped (N4). The
documentation of writer recording and of the per-checkout image tags is corrected (N5). Fixture
records in the tests now match what production writes (N6). `E2E_VITE_BIN` and
`E2E_VITE_MODULE` are never handed to a coordinated run, and `E2E_PYTHON` is set to the
coordinator's own interpreter (N7). The real-Docker rerun also exposed that recovery asked a
surviving runner's preview to close while that runner was still testing: 17 of its tests failed.
The preview was then contacted only after every other recorded writer was verified gone, which the round 3
review showed to be insufficient (unrecorded workers); round 3 checks the runner's group first. The
filesystem check remains a deny-list combined with the live lock-exclusion probe (N8, unchanged).

`node --test fixtures/*.test.mjs` (from `e2e/`): **PASS 133/133** on macOS 27.2 (Python 3.9.6,
Node 24.21.0) and **PASS 133/133** in a Debian 13 arm64 container (Python 3.12.16, Node 22.23.3,
run as root). These are stub and real-process scenarios, not Docker integration evidence.
The new coordinator-level regression tests fail on `dcbf877`. Real Docker Desktop 29.8.1,
Vite and Chromium on the macOS host, from a separate worktree:

| Scenario | Observed result |
| --- | --- |
| Normal run (`npm run test:chromium`) | 27 passed, 1 skipped; global setup ran in the recorded runner; 2 containers, 1 anonymous volume, 1 network and the build removed and verified; runner group empty; record `complete`; exit 0 |
| Coordinator alone `SIGKILL`ed mid-test | `stack:down` exit 1: runner and its preview preserved, build and Docker untouched; new run exit 3; the surviving runner finished (27 passed) and its teardown refused to write; the next `stack:down` closed the preview over its channel, verified the group empty and removed everything (exit 0) |
| `SIGINT` to the coordinator mid-test | Forwarded to the runner only; 3 tests interrupted; teardown removed everything; record `complete` with the runner group empty |
| Runner alone `SIGKILL`ed mid-test (5 group members) | Workers left within about a second; the coordinator finalized: preview closed over its channel, group empty, everything removed; record `complete`; exit nonzero |
| After all runs | Containers, volumes and networks identical to the baseline; no preview, worker or socket left |

Not executed: Ubuntu 24.04 (the first execution of the Linux process-group and identity paths
on Ubuntu is the stacked SH07 job after it is rebased onto this branch), a non-root Linux
account, real PID reuse, a real reboot, `firefox-desktop` and WebKit (not part of these runs),
and external mode. A Playwright worker that outlives the runner by longer than the 45 s wait
leaves the run incomplete until it exits and `stack:down` is run again.

### Independent review round 3 remediation — 17 November 2026

A third independent review of head `1b1e473` reported three blocking findings. Each was
reproduced on `1b1e473` before it was fixed (stub Docker and real processes), and each
reproduction was rerun on the fixed code:

| Finding | Reproduction at `1b1e473` | Fix |
| --- | --- | --- |
| P1-1 stale finalizer | Finalizer A blocked inside a Docker removal, coordinator A `SIGKILL`ed, `stack:down` exit 0 (`complete`), run B admitted; when A resumed it replaced B's active record with its own `complete` state. The engine had captured its parent PID once, and its check and write were not atomic | Every record change is a fenced compare-and-swap under a per-record lock (run ID, lock key, `fence`, `revision`); each recovery increments `fence`; finalizers are recorded writers. Rerun: recovery exit 1 with finalizer A preserved, run B refused (exit 3), the resumed finalizer wrote nothing, recovery then completed |
| P1-2 preview before the group | Runner exited, preview and a worker of its group alive: recovery asked the preview to close | The runner's group is checked before the preview is contacted; only the preview, matched by PID and birth identity, may remain; the group is checked again in full afterwards. Rerun: the preview is not contacted |
| P1-3 group verification fail-open | A runner record without a group identity let recovery report `complete=true`; on Linux an `EACCES` while reading `/proc` gave `reliable: true` | A missing or unusable group identity, no runner entry, another boot, or any `/proc` error other than the process having gone keeps the run incomplete; the PID-reuse shortcut was removed (the group is always enumerated). Rerun: incomplete, `reliable: false` |
| P2 malformed record | `intents: 5` raised `TypeError` in the coordinator | Types are validated before use; a malformed own record blocks reuse with a clear error (exit 3), another checkout's is skipped |

Fencing design and crash behaviour. Passing the checkout lock to child processes was rejected
(ADR008 §10.2 forbids inheriting its descriptor), and Node has no built-in `flock`, so the
coordinator's own `commit-record` helper performs Node's writes. The record lock is a stable
file that is never unlinked; its critical sections are short, bounded (20 s) and never wait
for another lock or process, so there is no lock-ordering cycle (checkout lock, then record
lock, never the reverse). A writer that dies inside the section releases it with its
descriptor, and the atomic replacement leaves the old or the new record. A writer that is
paused while holding it makes other writers fail closed after the bound. A crash between a
writer's intent and its recorded PID leaves a PID-less writer that recovery preserves until
an operator reconciles it.

Records written before this protocol (without `fence` and `revision`) are malformed for the
coordinator and block their checkout until an operator has inspected them and moved them out
of `~/.codemastery-e2e/runs/`. On the verification host the worktree's own completed record
from the earlier rounds was moved to `archive/` this way.

`node --test fixtures/*.test.mjs` (from `e2e/`): **PASS 152/152** on macOS 27.2 (Python 3.9.6,
Node 24.21.0), and **PASS 152/152** in a Debian 13 arm64 container (Python 3.12.16, Node
22.23.3), run once as root and once as a non-root user (uid 1000). Of the new regression tests,
17 fail on `1b1e473`; the other 2 are positive controls (only a process verified gone is skipped
on Linux; a runner that never started needs no group). These are stub, real-process and
multi-process race tests, not Docker integration evidence. Real Docker Desktop 29.8.1, Vite and
Chromium on the macOS host, from a separate worktree:

| Scenario | Observed result |
| --- | --- |
| Normal run (`npm run test:chromium`) | 27 passed, 1 skipped; record `complete` at fence 1 (37 fenced commits); runner group empty; the finalizer recorded and reaped; exit 0 |
| Coordinator `SIGKILL`ed mid-test | Recovery exit 1 with the runner preserved and its preview left running (fence 2); new run exit 3; the surviving runner finished (27 passed) and its teardown was refused; the next recovery closed the preview, verified the group empty and removed everything (fence 3, exit 0) |
| Finalizer `SIGSTOP`ped, coordinator `SIGKILL`ed, recovery, finalizer `SIGCONT`ed | Recovery exit 1 with the paused finalizer preserved (fence 2); new run exit 3; the resumed finalizer was refused and left the record byte-identical; the next recovery found it gone and completed (exit 0) |
| `SIGINT` to the coordinator mid-test | Record `complete`, runner group empty, everything removed |
| Runner `SIGKILL`ed mid-test | Workers left; preview closed; group verified empty; everything removed; exit nonzero |
| After all runs | Containers, volumes and networks identical to the baseline; no preview, worker, finalizer or socket left |

Not executed: Ubuntu 24.04 (its first execution remains the stacked SH07 job once rebased onto
this branch), a Linux host with `hidepid` mounted (there an inaccessible `/proc` entry makes
every group check unreliable, so recovery stays incomplete), real PID reuse, a real reboot,
`firefox-desktop` and WebKit, and external mode.

## SH07 E2E CI first executions — 13 November 2026

The `CI E2E` workflow ([record](#ci)) ran for the first time on stacked draft PR
[#241](https://github.com/chi-thanh-nguyen/codemastery/pull/241) (built on FE24 PR
[#240](https://github.com/chi-thanh-nguyen/codemastery/pull/240); this is not final `main`
integration evidence). Run
[43296414482](https://github.com/chi-thanh-nguyen/codemastery/actions/runs/43296414482):
PR head `8f3be1d08484050d2d38b68bbca4a620b4dbe29c`, tested merge commit `eca7a86` (into
`main` `956fa35fa197d57dde61e04501ac03af46f9d155`), `ubuntu-24.04`, Node 24, Playwright
1.63.0 with its pinned browsers.

| Step | Result |
| --- | --- |
| Install pinned browsers (`--with-deps`) | success, 66 s |
| `node --test fixtures/*.test.mjs` | PASS 85/85, 33 s: the coordinator, inventory, recovery and control-channel tests on Linux |
| `E2E_WORKERS=1 npm test` | success: 70 results, **66 passed, 4 skipped, 0 failed**, all five projects including `firefox-desktop`; the 4 skips are the suite's own `API-only checks run once.` on the four non-`chromium-desktop` projects; run admitted by the coordinator before Playwright started; both containers, the anonymous volume, the network and the build removed and verified; the runner's exit verified by Linux birth identity before the run became complete |
| `npm run stack:down` (`always()`) | success: the completed run re-verified, nothing left |

This first execution qualifies the Linux identity, filesystem and lock paths on the
hosted Ubuntu runner, for the normal path. CI-level failure injection (a deliberately failing
setup, assertion, browser or cleanup inside the workflow) was not run. Those propagation
paths were exercised locally through the same `npm` entrypoints
([FE24 record](#fe24-ct34-r2-lifecycle-implementation--13-november-2026)). The step budgets
were then set from these durations. `E2E` is not a required check. No raw report was
uploaded. Coverage is the existing suite only, so this is not Final main-flow acceptance.

These first executions tested the pre-remediation FE24 lifecycle that the stacked branch then
carried (85 lifecycle tests). They are historical and are superseded by the record below.

## SH07 synchronization with the merged FE24 lifecycle — 17 November 2026

FE24 PR [#240](https://github.com/chi-thanh-nguyen/codemastery/pull/240) merged as `main`
`46a4540794bb1d31e2d24483cae48b19db1ad539`. `main` was merged into the SH07 branch rather than
rebased. Every FE24 lifecycle file (`e2e/fixtures/*` other than `ci-evidence.mjs`,
`e2e/package.json` and `e2e/playwright.config.ts`) takes `main`'s version unchanged. The SH07
diff against `main` is the workflow, `ci-evidence.mjs` and this documentation only.

Auditing the workflow against the final lifecycle found one SH07 defect: the signal path on a
step timeout or cancellation (see [CI](#ci)). The Actions runner signals only a step's root
process on Unix. Under the original step that process was bash, which absorbed SIGINT and died
on SIGTERM, so the coordinator never received a signal and kept running the suite after the step
had ended. The fix is the `exec`, bash-script-shell suite step and the exit-2 wait in the
recovery step. The lifecycle files did not change.

| Check | Result |
| --- | --- |
| `node --test fixtures/*.test.mjs` (macOS, Python 3.9.6, Node 24.21) | PASS 152/152, 25 s |
| Local `E2E_WORKERS=1 npm test`, then `npm run stack:down` (macOS, Docker 29.8.1) | 53 passed, 4 skipped (API-only), 13 failed: every `firefox-desktop` case, `browserType.launch: Failed to launch the browser process`. This is the known local Firefox launch failure on this host, not a lifecycle or suite result. Run complete; recovery exit 0; no Docker leftovers |
| Runner cancellation replayed against the original step (SIGINT to the step root, SIGTERM after 7.5 s, then a kill of the root alone) | The coordinator received no signal and was still running after the step ended. Recovery exited 2. The orphaned coordinator ran the rest of the suite to completion about 53 s later |
| The same replay against the fixed step | The coordinator logged `received signal 2; forwarding it to the runner`, Playwright stopped (1 interrupted, 23 did not run), and the run was finalized within 4 s. The recovery step then exited 0, `complete`, with no leftovers |
| The recovery step while a live run owned the checkout | Waited 23 times on exit 2, then recovered once the run ended. Exit 0, `complete` |
| The recovery step with stubbed `stack:down` exits 0, 1, 3 and 2 | Step exits 0, 1 and 3 after one call; on a permanent exit 2 it stops after 48 calls with an error and exit 2 |
| Shell behavior on `ubuntu:24.04` | `bash -c` runs a single command in place; `sh` (dash 0.5.12) does not |
| npm signal forwarding | Exercised on macOS (npm 11.19.0, real coordinator) and in a Debian container (npm 10.9.9, dash), where only the bash script shell delivered the signal |

CI run [43951662691](https://github.com/chi-thanh-nguyen/codemastery/actions/runs/43951662691)
tested PR head `beb25e830c911dca774ef1a5f27f345361ddee7a` as merge commit `a35b757` into `main`
`46a4540`. It ran on `ubuntu-24.04` (image 20261111.370.1) with Node 24.22.2 and Playwright
1.63.0 with its pinned browsers.

| Step | Result |
| --- | --- |
| Install pinned browsers (`--with-deps`) | success, 46 s |
| `node --test fixtures/*.test.mjs` | PASS 152/152, 38 s: the final fencing, CAS, process-group and recovery tests on Linux |
| `E2E_WORKERS=1 npm test` (bash script shell, `exec`) | success, 5.0 min: 70 results, **66 passed, 4 skipped, 0 failed**, 14 per project on all five projects. The 4 skips are the API-only REST smoke on the four non-`chromium-desktop` projects. The run was admitted before Playwright started, finalized by the coordinator with 4 resources removed, and became `complete` after the runner's exit was verified |
| `npm run stack:down` (`always()`) | success: the completed run was re-verified and nothing was left |

Not exercised in CI: deliberate setup, assertion, browser or cleanup failure, and a real
timeout or cancellation inside the hosted workflow. The cancellation path was replayed locally
only, as listed above. `E2E` is not a required check. Coverage is the existing suite only, so
this is not Final main-flow acceptance.

## ADR014 hybrid authentication verification — 16 November 2026

`CT14-SESSION-2026-11-16-r1` (**DELEGATED_APPROVED**,
[ADR014 §9](decisions/014-hybrid-refresh-session-authentication.md#9-ct14-session-2026-11-16-r1--verification-obligations))
replaces the earlier session expectations: E2E no longer expects zero cookies or a
signed-out reload. It expects exactly the HttpOnly refresh cookie, no token in Web
Storage or `document.cookie`, and restoration on reload. The landing page no longer
expects zero API requests. It expects exactly one bearer-less `POST /api/auth/refresh`
session check. The earlier records above keep their dated wording.

**Test locations (existing files only).**

| Area | Location | Coverage |
|---|---|---|
| Backend unit | `modules/auth/JwtTokenProviderTest` | required canonical `sid` claim (missing, mistyped, upper-case, list, null); revoked/expired/foreign session rejects a valid token without a business effect; refresh/logout never read a bearer and need a trusted Origin; cookie mode selection (`__Host-` for any HTTPS origin, `cm_refresh` only for pure HTTP loopback), exact Set-Cookie/clear strings, duplicate cookies unusable |
| Backend unit | `modules/auth/AuthServiceTest` | controller and security-slice wiring with the new collaborators |
| Backend integration (PostgreSQL) | `integration/AuthorizationIntegrationTest$RefreshSessions` | issuance, attributes, digest-only storage and `sid` binding; rotation; reuse within 30 s without revocation; replay after 30 s revoking the family and its access JWTs; six concurrent refreshes → exactly one 200; logout (also without or with unknown cookie); malformed/unknown/duplicate/expired-token/expired-session cookies; locked account; role change; login replacement and the 10-session cap; untrusted/`null` Origin, cross-site Fetch Metadata and navigation mode; credentialed CORS only on cookie paths |
| Backend integration | `AuthorizationIntegrationTest` (reset test) | a session-bound bearer issued before a password reset is rejected afterwards and its session is revoked (supersedes the ADR006 §9.4 residual assertion) |
| Backend integration | `CourseCatalogueIntegrationTest`, `EnrollmentIntegrationTest`, `SampleDataSeedIntegrationTest` | fixtures bind tokens to sessions and clean session rows; no behavior change |
| Frontend | `app/providers/AuthProvider.test.tsx` | restore (StrictMode, one request, nothing stored), refused restore, preview without restore, in-place renewal versus replacement, refused and transient renewal, late renewal after sign-out, sign-out with pending-sign-out retry, sign-in during restore, cross-tab messages, Web Lock use, wake-up restore, no mutation replay |
| Frontend | `shared/components/presentation.browser.cjs` (optional harness) | landing sends only the anonymous session check |
| Frontend | `shared/api/httpClient.test.ts`, `routes/index.test.tsx` | credentials only for the three cookie operations (keepalive for logout), 204 handling, refused refresh not invalidating; protected reload waits and then renders, refused restore redirects without the session-ended notice |
| E2E | `e2e/tests/learner-flow.spec.ts` | cookie attributes and no stored token; reload restoration; second tab adopts the session and follows sign-out; renewal before expiry (Playwright clock) with a private request afterwards; cross-site (`localhost` versus `127.0.0.1`) refresh/logout refused with 403 and the session survives; session-ended notice after a refused refresh; sign-out deletes the cookie; landing page sends only the anonymous session check |
| Ingress | `infrastructure/vm/tests/test-ingress-stub-backend.sh`, `test-ingress-real-backend.sh` | Cookie and Fetch Metadata only on the three exact locations; Set-Cookie pass-through; `;` rejection; real backend: issue, rotation, CSRF rejections, logout revocation through HTTPS |
| Privileges | `test-deploy-privilege-matrix-contract.sh`, `test-postgres-privilege-matrix-containment.sh`, `test-runtime-real-backend-privileges.sh` | r3 rows pinned to the record (27 relations); grants and denials on V001–V008; sign-in, rotation, logout and reset revocation under the runtime role |

**Executed on 16 November 2026** by Claude Code on the PR branch (macOS host, Docker
Desktop 29.8.1, Java 25.0.4.1, Node 24.21.0). These results are implementation
evidence by the author, not QA01/QA07 independent verification, CI or acceptance:

| Command | Result |
|---|---|
| `cd backend && ./mvnw verify` | PASS — BUILD SUCCESS; 631 unit tests (Surefire) and 197 integration tests (Failsafe, PostgreSQL Testcontainers), 0 failures, 0 errors |
| `cd frontend && npm run build` | PASS (typecheck and Vite build; the existing chunk-size warning is unchanged) |
| `cd frontend && npm test` | PASS — 16 files, 486 tests |
| `cd e2e && npx playwright test --project=chromium-desktop --project=chromium-mobile --project=webkit-desktop --project=webkit-mobile` | PASS — 65 passed, 3 skipped by design (API-only smoke runs once) |
| `firefox-desktop` project | NOT RUN — the pinned Playwright Firefox cannot launch on this macOS host (known environmental limitation) |
| `bash infrastructure/vm/tests/test-ingress-stub-backend.sh` (release and local images built from the branch) | PASS — 168 assertions, `INGRESS_STUB_TESTS_PASS` |
| `bash infrastructure/vm/tests/test-ingress-real-backend.sh` | PASS — 83 assertions, `INGRESS_BACKEND_TESTS_PASS` |
| `bash infrastructure/vm/tests/test-deploy-privilege-matrix-contract.sh` | PASS — `MATRIX_CONTRACT_SYNC_PASS: 27 relations` |
| `bash infrastructure/vm/tests/test-postgres-privilege-matrix-containment.sh` | PASS — `MATRIX_TESTS_PASS`, both containment checks |
| `bash infrastructure/vm/tests/test-runtime-real-backend-privileges.sh` | PASS — `RUNTIME_CONSUMER_TESTS_PASS` (Flyway 8/8) |
| `cd frontend && npm run test:browser` (Chromium) | PASS — 45 passed; the landing check now expects exactly the anonymous `POST /api/auth/refresh`, which the harness test double refuses with 401 |
| `test-deploy-syntax-and-authorization-gates.sh`, `test-deploy-offline-compose-guards.sh`, `bash -n` on every VM test script | PASS |
| `test-ingress-release-image-build.sh` | Images built; its Java-truststore sub-check FAILED locally on a Docker Desktop platform-resolution error (`no match for platform in manifest`) for a locally built tag; the backend Dockerfile is unchanged by this work. The CI job "VM HTTPS ingress", which runs this script first, passed on `8001e1a` |
| `test-deploy-preflight-rendered-compose.sh` | FAILS on this macOS host identically on unmodified `main` (`stat -c` is GNU-only); CI runs it on Ubuntu. Not affected by this work |

Not run: the browser harness on WebKit/Firefox, CI, and any test against a shared,
staging or production environment. Remaining: CI on the PR,
independent QA01/QA07 execution, and the review gates in ADR014 §12.

### Author self-review rerun — 16 November 2026

An **author self-review** of the whole PR #246 diff (not an independent review)
produced the corrections in
[ADR014 §14](decisions/014-hybrid-refresh-session-authentication.md#14-author-self-review-corrections--16-november-2026).
They are: sign-in lock order, purge `FOR UPDATE SKIP LOCKED`, case-insensitive trusted
Origins, a time-bounded sign-out inside the Web Lock, cookie-mode wording and the rollback
note. New tests in `AuthorizationIntegrationTest$RefreshSessions` cover purge of expired
sessions with their tokens, case-insensitive Origin matching and concurrent sign-ins
replacing different cookies at the cap. The last one is a smoke test: the deadlock it
guards against did not reproduce locally, even without the fix. `AuthProvider.test.tsx`
now asserts the sign-out signal.

CI on `8001e1a`, the head before these corrections: all eight jobs passed (Backend,
Frontend, VM HTTPS ingress, VM deployment script, PostgreSQL and containment, runtime
privileges, containerd, MinIO qualification).

Re-executed on the corrected tree:

| Command | Result |
|---|---|
| `cd backend && ./mvnw verify` | PASS — 631 unit, 200 integration, 0 failures |
| `cd frontend && npm run build` / `npm test` | PASS — 16 files, 486 tests |
| Playwright chromium/webkit desktop+mobile | PASS — 65 passed, 3 skipped by design |
| `test-ingress-stub-backend.sh`, `test-ingress-real-backend.sh` | PASS |
| `test-deploy-privilege-matrix-contract.sh`, `test-postgres-privilege-matrix-containment.sh`, `test-runtime-real-backend-privileges.sh` | PASS; no `42501` in the backend log (the new lock and purge statements run under the runtime role) |

Note: forcing an `*IntegrationTest` into the Surefire unit run together with
`AuthServiceTest$SmtpSubmissionPolicy` makes the SMTP log-capture tests fail. That is
an ordering artifact of that ad hoc command: the classes pass separately and in the
normal Surefire/Failsafe split above.

## Independent review remediation — 17 November 2026

An independent Codex review of `13dba67` reported five blocking findings (F01–F05).
[ADR014 §15](decisions/014-hybrid-refresh-session-authentication.md#15-independent-review-remediation--17-november-2026)
records the author's remediation; the Codex re-review is pending.

**Regression tests (existing files).**

| Finding | Test | Forcing mechanism |
|---|---|---|
| F01 | `AuthorizationIntegrationTest$RefreshSessions` `f01A` (reset commits between verification and issuance), `f01B` (reset commits while issuance waits for the account lock), `f01C` (issuance commits before a waiting reset, which revokes it), `f01D` (account locked after verification) | Service-level sequencing; a real User-row lock held on a separate connection; `pg_stat_activity` lock-waiter detection, no sleeps |
| F02 | `f02ElevenConcurrentIssuancesFromNoSessionKeepExactlyTenActive` (exactly 10 active); `f02ConcurrentReplacementsAboveTheCapLeaveAtMostTenActive` (exactly 10) | 11 transactions queued on one held User-row lock and released together (test pool raised to 20 connections) |
| F03 | `f03OppositeCrossAccountReplacementsBothSucceedWithoutDeadlock` | Both old session rows held, so the opposite switches overlap |
| Combined | `rotationQueuedTogetherWithAResetEndsRevokedWhicheverRunsFirst` | Both queued on one held session row |
| F04 | `AuthProvider.test.tsx` "F04 …"; Playwright "F04: a sign-out in another tab wins over that tab's pending session restore" | Real refresh request delayed in a second tab; native BroadcastChannel and Web Locks |
| F05 | `AuthProvider.test.tsx` "F05 …" (queued sign-in, failed sign-in restores, expiry during sign-in), "a newer sign-out still cancels a pending sign-in"; Playwright "F05: an explicit sign-in queued behind a pending restore …" | FIFO lock stand-in (Vitest); native Web Locks with a delayed real refresh (Playwright) |
| Privileges | `test-postgres-privilege-matrix-containment.sh` probes for the sign-in `FOR NO KEY UPDATE` and the purge `FOR UPDATE SKIP LOCKED` | Runtime role |

**Shown to fail on the previous implementation** (code from `13dba67`, new tests,
executed 17 November 2026):

- `f01A`, `f01B`, `f01D`: no exception, so the stale session was issued.
- `f02`-from-empty: 11 active sessions.
- `f03`: HTTP 500 from a deadlock.
- Vitest: the four F04/F05 tests.
- Playwright Chromium: F04 stayed on `/profile` showing the old account; F05 sent no
  login request and stayed on `/login`.

`f01C`, the replacement-above-cap test and "a newer sign-out …" also pass on the old
code; they guard properties that already held. Two unrelated Vitest tests failed there
only through leakage from the failing F05 tests, and pass in isolation.

**Executed on the corrected tree** (17 November 2026; macOS, Docker Desktop 29.8.1, Java
25.0.4.1, Node 24.21.0; author evidence, not independent verification):

| Command | Result |
|---|---|
| `cd backend && ./mvnw verify` | PASS — 631 unit, 207 integration, 0 failures |
| `cd frontend && npm run build` / `npm test` | PASS — 16 files, 491 tests (also three shuffled runs of the provider file) |
| `cd frontend && npm run test:browser` (Chromium) | PASS — 45 |
| `cd e2e && npx playwright test` (chromium-desktop/mobile, webkit-desktop/mobile) | PASS — 73 passed, 3 skipped by design (F04/F05 run on all four projects) |
| `test-deploy-privilege-matrix-contract.sh`, `test-postgres-privilege-matrix-containment.sh`, `test-runtime-real-backend-privileges.sh` | PASS |
| `test-ingress-stub-backend.sh`, `test-ingress-real-backend.sh` | PASS |
| `test-deploy-syntax-and-authorization-gates.sh`, `test-deploy-offline-compose-guards.sh` | PASS |

Not run: `firefox-desktop` (cannot launch on this host). Limits: the forced interleavings
use real PostgreSQL row locks and waiter detection. They do not reproduce every possible
scheduling. In particular, a cycle between a sign-in and a future CT12 implementation is
excluded by the lock order (ADR014 §15), not tested, because CT12.user is not
implemented.

## Second independent review remediation — 17 November 2026

A second independent Codex review of `56da61d` confirmed F01–F05 corrected and reported
N01 and N02 (P2).
[ADR014 §16](decisions/014-hybrid-refresh-session-authentication.md#16-second-independent-review-remediation--17-november-2026)
records the author's remediation; independent re-review is pending.

| Finding | Test | Mechanism |
|---|---|---|
| N01 | `AuthorizationIntegrationTest$RefreshSessions` `n01DelayedIssuanceReturnsASessionItsOwnCapEnforcementDidNotRevoke` | A's session row held; the sign-in to B presenting A's cookie waits (lock-waiter detection); ten sign-ins to B commit; release. Asserts the returned session is active, refreshes with 200, exactly 10 active, and a later sign-in revokes the oldest |
| N01 | `n01CapNeverSelectsTheIssuingSessionEvenOnATimestampTie` | 11 sessions with equal `created_at`; each one passed as the issuing session is never revoked |
| N02 | `AuthProvider.test.tsx` "N02: … queued behind it" (FIFO lock stand-in), "… while a sign-in is in flight …" (no Web Locks, the fallback path), "… failed sign-in renews …" | Deferred renewal responses with another `sid` |
| N02 | Playwright "N02: a delayed renewal returning another session does not cancel an explicit sign-in queued behind it" | Native Web Locks; a second real sign-in through the context's API client makes the renewal genuinely return another `sid`; the real renewal is delayed with the Playwright clock |

**Shown to fail on `56da61d`** (old code with the new tests, 17 November 2026):

- the N01 scenario test ("issuance must not revoke the session it returns": revoked);
- both N02 cancellation Vitest tests;
- the Playwright N02 test (stayed on `/login`, no login request).

The failed-sign-in renewal test checks the new re-renewal path. The tie test uses the new
repository signature, so it does not compile against the old code.

**Executed on the corrected tree** (author evidence, not independent verification):

| Command | Result |
|---|---|
| `cd backend && ./mvnw verify` | PASS — 631 unit, 209 integration, 0 failures (a first run exposed an incorrect final assertion in the new tie test; that assertion was removed, three focused reruns and the full rerun passed) |
| `cd frontend && npm run build` / `npm test` | PASS — 16 files, 494 tests (provider file also passed in two shuffled runs) |
| `cd frontend && npm run test:browser` (Chromium) | PASS — 45 |
| Playwright chromium/webkit desktop+mobile | PASS — 77 passed, 3 skipped by design (N02, F04, F05 on all four projects) |
| Privilege contract, containment, real-backend privileges; ingress stub and real backend; deploy gates and offline guards | PASS |

All F01–F05 regressions (PostgreSQL, Vitest, Playwright) pass again on this tree.
`firefox-desktop` was not run (host limitation).

## CO16 Course-owned reporting handoff verification — 17 November 2026

CO16 ([#179](https://github.com/chi-thanh-nguyen/codemastery/issues/179),
[PR #249](https://github.com/chi-thanh-nguyen/codemastery/pull/249)) implements the
Course part of `CT13-METRICS-2026-11-15-r1` and `CT13-READS-2026-11-15-r1`
([ADR012 §§13.1–13.2](decisions/012-batch-07-contract-approvals.md#13-ct13-reporting)):
both `EnrollmentService.readEnrollmentMetrics` methods and the `CourseEnrollmentMetrics`
record. They are actorless, read-only and `Propagation.MANDATORY`, so they run only
inside the caller's transaction; the approved caller, Admin's `PlatformMetricsService`
(IN12), opens one REPEATABLE READ transaction after admitting an Administrator. IN12,
the Assessment and Adaptive reads and `GET /api/admin/metrics/courses…` are not part of
this change, so no REST operation, OpenAPI shape or runtime caller is added.

| Figure (ADR012 §13.1) | Implementation |
| --- | --- |
| `activeEnrollments`, `completedEnrollments`, `leftEnrollments` | Counts of stored statuses per Course, hidden and published |
| `joinedLearnerIds` | Learners with `active` or `completed` status, one set-based query per page; internal only |
| `completedLearners` | Joined learners whose CT22.progress `isComplete` holds: R = the Course's currently published Lessons, R nonempty and every R member has a `completed = true` row of that Enrollment; hidden Lessons never count, a zero-Lesson Course completes nobody, left learners are excluded |
| Ordering and paging | Every Course by `title COLLATE "C"`, then `id`; CT18 page/size bounds; one aggregate statement per page plus one learner-ID statement |

B7-O11's Course part is covered by `EnrollmentIntegrationTest`
(`enrollmentMetricsFollowTheExactCt22CompletionRuleForEveryPopulation`,
`enrollmentMetricsPagesEveryCourseInTitleCollateCThenIdOrderWithExactTotals`,
`enrollmentMetricsRequireTheCallerTransaction`) on real PostgreSQL, and by the
`EnrollmentServiceTest` mapping, validation, snapshot-consistency and transaction-
attribute cases. Executed locally on the PR #249 working tree after merging `main`
`78e783d`: `cd backend && ./mvnw -Dtest=EnrollmentServiceTest -Dit.test=EnrollmentIntegrationTest verify`
ran 19 unit and 61 PostgreSQL integration tests with 0 failures or errors; the full
`./mvnw verify` result is recorded in the pull request. These are local developer runs,
not independent QA or CI evidence.

Runtime grants: the reads use SELECT on `courses`, `chapters`, `lessons`,
`enrollments` and `lesson_progress`. The first four are already in the effective
matrix; `lesson_progress` SELECT is added by CO08's `CT17-P6-PRIVILEGES-2026-11-17-r4`
(PR #248). No runtime caller exists until IN12, which must not be deployed before that
grant is effective.

## Comprehensive audit remediation (A01) and main integration — 17 November 2026

A comprehensive independent Codex audit of `b18d3cf` found F01–F05 and N01 fixed, and N02
partially fixed. It reported A01 (P2) and a `docs/testing.md` conflict with `main`.
[ADR014 §17](decisions/014-hybrid-refresh-session-authentication.md#17-comprehensive-audit-remediation-a01-and-main-integration--17-november-2026)
records the author's remediation and the merges of `main`: `46a4540` (FE24 lifecycle) and
then `cc6e9b7` (SH07 `CI E2E` workflow).
Independent re-review is pending.

| Scenario | Test |
|---|---|
| Renewal completes first, then the explicit sign-in fails (order A) | `AuthProvider.test.tsx` "A01 order A: …" |
| Sign-in fails first, then the delayed renewal completes (order B, the audit's A01) | "A01 order B: …" |
| Renewal completes while the sign-in is queued (FIFO lock stand-in) / in flight (no Web Locks) | "A01: a renewal that completes while the sign-in is queued …" / "… in flight neither aborts nor installs …" |
| Sign-in succeeds while the renewal is delayed | "A01: an explicit sign-in that succeeds while the renewal is delayed wins …" |
| Sign-out before the delayed renewal completes | "A01: a sign-out before the delayed renewal completes wins …" |
| Newer sign-in supersedes an older one | "A01: a newer sign-in supersedes the older one …" (one recovery only), "A01: a newer successful sign-in …" |
| Recovery returns another `sid` / is refused | "A01: the recovery … installs another sid as a replacement …", "A01: a refused recovery … ends the session …" |
| Queued sign-in abandoned by the user | "A01: a queued sign-in the user abandons …" |
| Native Web Locks, real backend | Playwright "A01: a delayed renewal superseded by an explicit sign-in that is abandoned / refused still renews the session from the cookie" |

Each Vitest test asserts the bearer actually sent afterwards and the next scheduled
renewal, not internal flags. The Playwright test asserts two `200` refreshes, the login
outcome, a `200` private request carrying the bearer the recovery refresh issued, and
the account menu.

**Shown to fail on `b18d3cf`** (old `AuthProvider.tsx` with the new tests):

- Vitest: order B, the other-`sid` and refused-recovery tests, and the abandoned queued
  sign-in (4 of 12);
- Playwright: the abandoned case (one refresh instead of two) on `chromium-desktop` and
  `chromium-mobile`.

The order A, queued/in-flight-failure, success, sign-out and newer-sign-in tests also
pass on `b18d3cf`. The old code already handled those orders, and the tests now guard
them.

**Merges of `main`.** Both merges (`46a4540`, then `cc6e9b7`) conflicted only in
`docs/testing.md`, where both sides had appended sections at the end. Each side's
sections were kept whole: `main`'s FE24 and SH07 sections precede the ADR014 sections.
Line comparisons found every line of both sides present, except merge-base lines that
`main` itself rewrote: 15 in the first merge, 3 in the second. `docs/setup.md` and
`docs/repository-structure.md` merged automatically. No application file changed. The
local runs below were made on the first merge. The second merge adds only
`.github/workflows/ci-e2e.yml`, `e2e/fixtures/ci-evidence.mjs` and documentation, and its
`CI E2E` job runs the suite in CI for this PR.

**Executed on the merged tree** (author evidence, not independent verification):

| Command | Result |
|---|---|
| `cd frontend && npm test` / `npm run build` | PASS — 16 files, 505 tests; build OK |
| `cd frontend && npm run test:browser` (Chromium) | PASS — 45 |
| `cd backend && ./mvnw verify -Dtest=none -Dsurefire.failIfNoSpecifiedTests=false -Dit.test='AuthorizationIntegrationTest$RefreshSessions'` | PASS — 24 of 24, including F01A–F01D, both F02, F03, both N01 and rotation-versus-reset (backend unchanged since `b18d3cf`) |
| `cd e2e && node --test fixtures/*.test.mjs` | PASS — 152 |
| `cd e2e && npm test -- --project=chromium-desktop --project=chromium-mobile --project=webkit-desktop --project=webkit-mobile` (FE24 lifecycle coordinator) | PASS — 85 passed, 3 skipped (the API-only REST smoke, which runs only on `chromium-desktop`); every owned resource was verified absent, and recovery completed |

**Earlier runs in this remediation:**

- A first full run failed the new Playwright test on all four projects. The test compared
  bearers with the sign-in's, and two access JWTs issued within one second are identical.
  It now compares with the token the recovery refresh returned.
- A second full run had one WebKit-desktop failure in the shared sign-in helper of
  "shows the profile snapshot, signs out and guards private routes again". The email
  field was invalid at submit, before any renewal. It did not reproduce in three repeats
  of every session test on both WebKit projects (78 passed).

F04, F05 and N02 Playwright regressions passed in the final run.

**Correction.** The earlier "Second independent review remediation" table attributes its
3 skips to N02, F04 and F05. In fact they were this same API-only REST smoke on the three
non-`chromium-desktop` projects; the N02, F04 and F05 tests ran. `firefox-desktop` was
not run (host limitation).

## CO07 My Courses in-progress and completed verification — 17 November 2026

CO07 ([#173](https://github.com/chi-thanh-nguyen/codemastery/issues/173),
[PR #247](https://github.com/chi-thanh-nguyen/codemastery/pull/247)) adds no new
operation or payload field. `GET /api/me/courses` already returns each Course's stored
membership `status` and accepts `status=joined|active|completed|left|all`
(FE07-2026-10-27-r1). Under `CT22-PROGRESS-2026-11-09-r1`
([ADR006 §3](decisions/006-batch-02-contract-approvals.md#3-ct22progress)) that stored
status is the in-progress (`active`) versus completed (`completed`) distinction: CO08
(PR #248) writes it on manual completion, and membership PUT keeps an existing
`completed` and turns `left` into `active` without recalculation. Calculated
`isComplete` and percentages stay in `GET /api/me/course-progress`.

| Acceptance criterion | Covered by |
| --- | --- |
| In-progress and completed Courses are distinguished | `EnrollmentServiceTest.myCoursesForwardsTheSelectedStatusAndReportsStoredInProgressCompletedOrLeftStatus` (all five filters), `EnrollmentIntegrationTest.myCoursesDistinguishesInProgressFromCompletedCoursesByStoredCt22Status` (joined/active/completed/all views, per-learner scoping, hidden Courses excluded) |
| Membership operations and shapes unchanged | Existing FE07 suites unchanged and passing; the new integration test also checks join-keeps-completed, leave and rejoin-as-active, and that no progress field enters the payload |
| No duplicated membership functionality | No new production file or method; `EnrollmentServiceTest.myCoursesNeverRecalculatesCompletionOrWritesMembership` |

Executed locally on the PR #247 working tree after merging `main` `78e783d`:
`cd backend && ./mvnw -Dtest=EnrollmentServiceTest -Dit.test=EnrollmentIntegrationTest verify`
ran 18 unit and 59 PostgreSQL integration tests with 0 failures or errors; the full
`./mvnw verify` result is recorded in the pull request. These are local developer
runs, not independent QA or CI evidence.

## IN06.repo repository regression coverage — 18 November 2026

IN06.repo ([#181](https://github.com/chi-thanh-nguyen/codemastery/issues/181),
[PR #250](https://github.com/chi-thanh-nguyen/codemastery/pull/250)) adds the Interaction
discussion-post and content-report repositories and `InteractionMapper`. It adds no
operation, service or schema change. An independent review found that `save()` of an
existing row went through a JPA merge. The resulting full-row `UPDATE` wrote columns
that the ADR012 §19.2 ceiling (`CT17-P6-PLAN-2026-11-15-r2`) never grants. The adapters
now insert new rows, update existing rows with one targeted statement
(`discussion_posts.is_hidden`, or `content_reports.status` and `resolved_at`), and reject
any change to another field before SQL runs. The rejection surfaces as Spring's
`InvalidDataAccessApiUsageException` with an `IllegalArgumentException` cause. Lifecycle
rules (CT30/CT32) stay with the future services.

Placement: `CT14-IN06-REPO-PLACEMENT-2026-11-18-r1`
([repository structure](repository-structure.md#in06repo-test-placement--18-november-2026)).

| Obligation | Covered by |
| --- | --- |
| Mapper round trips; exact `open`/`resolved`/`dismissed` codes; other spellings rejected | `InteractionMapperTest` (17 cases) |
| Insert and read-back from PostgreSQL; permitted updates; same-transaction reads not stale | `InteractionRepositoryIntegrationTest`: `insertsPostAndReport…`, `existingPostUpdatesOnlyTheHiddenFlag…`, `existingReportUpdatesOnlyStatusAndResolutionTime`, `unchangedExistingRows…` |
| Immutable fields cannot be overwritten | `changingAnyImmutablePostField…`, `changingAnyImmutableReportField…` (every immutable field; row unchanged) |
| Hidden posts filtered before paging and counting; caller-only ordering, stable across ties | `hiddenPostsAreFilteredBeforePagingAndCounting`, `orderingComesOnlyFromTheCallerAndIsStableAcrossTies` |
| V006 checks | `v006PostChecks…`, `v006ReportChecks…` (text, self-parent, parent FK, exclusive target, resolution, times, status code) |
| SQL under the §19.2 ceiling | `repositorySqlRunsUnderTheSection192PrivilegeCeiling`; negative controls `ceilingRejectsTheFullRowPostUpdate…`, `ceilingRejectsTheFullRowReportUpdate…` (the old merge path fails with `42501`) and `ceilingRejectsUpdatesOfImmutable…` |

The privilege tests create a `NOLOGIN` role inside the test transaction. The role holds
exactly `SELECT, INSERT, UPDATE (is_hidden)` on `discussion_posts` and
`SELECT, INSERT, UPDATE (status, resolved_at)` on `content_reports`, and the tests run
`SET LOCAL ROLE`. The rollback removes the role and its grants. This proves the SQL is
compatible with the ceiling. It is not the CT17-P6 matrix, the deployed runtime role or
B7-O18. No runtime grant, matrix revision, script or CI change is made. The dated matrix
revision for these tables remains a completion gate for the IN06 PR that first exercises
them through the real backend.

Executed locally on 18 November 2026, macOS host with Docker, project PostgreSQL image:

- `cd backend && ./mvnw -Dtest=InteractionMapperTest -Dit.test=InteractionRepositoryIntegrationTest verify`
  ran 17 unit and 14 integration tests, with 0 failures or errors.
- Mutation check: with only the two adapters reverted to the original merge-based `save()`,
  the integration suite failed (4 failures and 1 `42501` error).
- The first full `./mvnw verify` on the branch showed that a new cached Spring context
  exhausted the shared container's connections (`SampleDataSeedIntegrationTest`: "too many
  clients already"). The suite now closes its context after the class (`@DirtiesContext`).
- The rerun of full `./mvnw verify` on the branch (merged with `main` `65d14ca`) ran 654 unit
  and 224 integration tests: BUILD SUCCESS.
- A simulated combined tree (`main` + PR #248 `f50a156` + PR #249 `4204045` + this branch)
  merged without conflicts and passed `./mvnw verify` (713 unit and 240 integration tests),
  `test-deploy-privilege-matrix-contract.sh` and
  `test-postgres-privilege-matrix-containment.sh` (r4 and V009).

These are author-side developer runs, not independent QA or CI evidence. The real-backend
runtime privilege job runs in CI. QA06 and the §15 oracles remain NOT_RUN.

## PR #246 CI failures on `aa6995d` — 17 November 2026

`aa6995d` has the same tree as `be8bef0`, on which all CI checks had passed; only its commit metadata changed. On `aa6995d`, two jobs failed. Neither failure came from the hybrid-authentication change.

| Job | Actual cause | Action |
|---|---|---|
| `CI E2E` / `E2E`, step "Run E2E lifecycle regression tests" | A race in the FE24 test "finalizer A paused in a Docker removal, … B is never touched" (`e2e/fixtures/lifecycle.test.mjs`). The test snapshotted run B's record as soon as it was `active`, but B's own coordinator then records its spawned runner (revision 1 → 2, runner `starting` → `live`). The CI diff shows exactly that write and no write by A. | The test now waits for B's live runner, B's last write before its gate, then takes the snapshot. The fencing assertions are unchanged. |
| `CI` / "VM runtime privileges with the real backend", step "Build the backend image from this revision" | Transient. `./mvnw package -DskipTests` inside `docker build -q` exited 1 after about 13 s; the quiet build hides Maven's output. The same tree built without cache locally (BUILD SUCCESS), and the job's rerun on `aa6995d` (attempt 2) passed. | None: no workflow or build change. |

Executed (author evidence): the fixed test passed 5 times in isolation, and all 152 tests passed in `cd e2e && node --test fixtures/*.test.mjs`. The backend image built with `docker build --no-cache` locally. The `aa6995d` VM job rerun passed.

## CO08 progress, completion and resume verification — 17 November 2026

Implementation of CO08 ([#174](https://github.com/chi-thanh-nguyen/codemastery/issues/174),
[PR #248](https://github.com/chi-thanh-nguyen/codemastery/pull/248)) against
`CT22-PROGRESS-2026-11-09-r1`, `CT22-RESUME-2026-11-09-r1` and
`CT22-RESUME-SCHEMA-2026-11-09-r1` ([ADR006 §§3, 4, 7.2](decisions/006-batch-02-contract-approvals.md#3-ct22progress)),
with the runtime grants of `CT17-P6-PRIVILEGES-2026-11-17-r4`
([deployment record](deployment.md#ct17-p6-privileges-2026-11-17-r4--co08-progress-and-resume-within-the-adr012-192-ceiling-17-november-2026)).
CT22.atomic (CO15) and the authoring-side completion reconciliation after a change to
the required Lesson set (CO06) are not part of this change.

| Oracle (ADR006 §8) | Covered by |
| --- | --- |
| B2-O01 calculation, zero-Lesson and complete Courses | `LessonProgressServiceTest`; `EnrollmentIntegrationTest.progressCompletionAndReconciliationFollowTheExactCt22Rules`, `zeroLessonCourseReportsZeroAndCannotBeCompletedVacuously` |
| B2-O02 idempotent true-only completion, skip preserved, invalid bodies 400 without writes | `LessonProgressServiceTest` (strict body parsers, controller precedence); `EnrollmentIntegrationTest.completionAdmissionPrecedenceConcealmentAndNoDeniedWrites`, `concurrentCompletionsOfOneLessonConvergeOnOneRow` |
| B2-O03 ACTIVE/COMPLETED reconciliation on completion; leave/rejoin unchanged; My Courses (CO07) shows the stored transition | `EnrollmentIntegrationTest.progressCompletionAndReconciliationFollowTheExactCt22Rules`, `storedMembershipAndComputedCompletionStayDistinctAcrossLeaveAndRejoin` |
| B2-O04 Course-root serialization, rollback, independent Courses | `EnrollmentIntegrationTest.completionSerializesOnTheCourseRootAndRereadsCommittedState` (barrier on `pg_stat_activity` lock waits, leave and publication interleavings), `failureAfterTheWriteRollsBackProgressStatusPointerAndTimeTogether` |
| B2-O05 per-Course resume; same-Course visits follow the Course-root lock | `EnrollmentIntegrationTest.perCourseResumeRecordsExplicitVisitsOnlyAndRetainsHistory` (including 415/400 bodies), `visitSerializesOnTheCourseRootAndRechecksTheTargetAfterTheWait` |
| B2-O06 ACTIVE/published paginated summary; My Courses unchanged | `EnrollmentIntegrationTest.myCourseProgressListsOnlySelfActivePublishedCoursesInTitleOrderWithExactCounts` |
| B2-O11 account-level resume ranking, legacy NULL, tie-break, fallback | `EnrollmentIntegrationTest.accountResumeSelectsTheLatestValidRecordedVisitWithDeterministicTieBreak` |
| B7-O18 (CT22.resume part) runtime role writes pointer and time together | `test-runtime-real-backend-privileges.sh` CO08 section; `test-postgres-privilege-matrix-containment.sh` r4 probes |

Executed locally (macOS 27.2 arm64, OpenJDK 25, Docker 29.8.1) on the PR #248
working tree after merging `main` `78e783d`:

| Command | Result |
| --- | --- |
| `cd backend && ./mvnw -Dtest='LessonProgressServiceTest,EnrollmentServiceTest' test` | 52 and 12 tests, 0 failures or errors |
| `cd backend && ./mvnw -Dit.test=EnrollmentIntegrationTest verify` (PostgreSQL Testcontainer) | 70 tests, 0 failures or errors; 71 after the self-audit additions below |
| `cd backend && ./mvnw verify` | BUILD SUCCESS; Surefire 683, Failsafe 221, 0 failures, errors or skips |
| `bash infrastructure/vm/tests/test-deploy-privilege-matrix-contract.sh` | `MATRIX_CONTRACT_SYNC_PASS`: 27 relations; r1 history intact, r2, r3 and r4 rows match the record |
| `bash infrastructure/vm/tests/test-postgres-privilege-matrix-containment.sh` | `MATRIX_TESTS_PASS` and both containment passes on the real V001–V009 schema, including the r4 grants and denials |
| `docker build -t codemastery-backend:privilege-test backend` then `bash infrastructure/vm/tests/test-runtime-real-backend-privileges.sh` | `RUNTIME_CONSUMER_TESTS_PASS`; Flyway 9/9; CO08 progress, visit, account resume, completion and idempotent repeat under the runtime role; no `42501` in the backend log |

These are local developer runs, not independent QA, CI or deployment evidence.
Frontend progress/resume types and screens remain FE08/FE33-owned and unchanged.

Self-audit additions (17 November 2026, same day): a test proving that a visit waiting
on a held Course root rechecks the target after the wait (it fails with 200 instead of
404 if the access check is moved before the lock), stricter 415/400 representation
checks for both JSON writes, and My Courses assertions after completion and
reconciliation. No production code changed; the updated results are recorded in
PR #248.

Independent-review remediation (P2, 17–18 November 2026): an existing V008 database
could not be upgraded, because `provision_db_roles` runs before the backend applies V009
and the first r4 revision granted `UPDATE (last_visited_at)` and checked the V009 column
list before that column existed. The fix and its upgrade path are described in the
[r4 record](deployment.md#ct17-p6-privileges-2026-11-17-r4--co08-progress-and-resume-within-the-adr012-192-ceiling-17-november-2026).
Run locally on the remediated working tree:

| Command | Result |
| --- | --- |
| `bash infrastructure/vm/tests/test-postgres-privilege-matrix-containment.sh` | Run against the previous `deploy.sh` with the new upgrade section: fails with `column "last_visited_at" of relation "enrollments" does not exist`. With the fix: `UPGRADE_TESTS_PASS`. The test covers a fresh cluster, V001–V008 with the r3 grants, pre-migration provisioning and its idempotency, eight fail-closed drift cases, V009, r4 hardening, the complete verification, and an already-migrated V009 database. `MATRIX_TESTS_PASS` and both containment passes still hold |
| `docker build -t codemastery-backend:privilege-test backend` then `bash infrastructure/vm/tests/test-runtime-real-backend-privileges.sh` | `RUNTIME_CONSUMER_TESTS_PASS` and `RUNTIME_UPGRADE_TESTS_PASS`. Real Flyway stops at V008 (`spring.flyway.target=8`) and the r3 grants are applied. Pre-migration provisioning is then exact, the backend applies V009, and hardening gives the exact r4 matrix. A visit and a completion succeed under the runtime role with no `42501` |
| `bash infrastructure/vm/tests/test-deploy-privilege-matrix-contract.sh` | `MATRIX_CONTRACT_SYNC_PASS`. The migration-added list is pinned to `enrollments.last_visited_at` (V009), must name a reviewed column, and is never deferred in complete mode; malformed lists are rejected |

Review-findings verification (18 November 2026). The disposition of each finding is in
PR #248 and PR #247:

| Finding | Evidence executed |
| --- | --- |
| Trailing JSON after the body object | Jackson 3.1.8 enables `FAIL_ON_TRAILING_TOKENS` by default, and the strict mapper reports it enabled. New `LessonProgressServiceTest` cases and `EnrollmentIntegrationTest` bodies send JSON followed by garbage or by a second value. Both writes return a `no-store` 400 ProblemDetail, with no progress row and an unchanged resume pointer |
| `mastery_skipped` through INSERT | The containment test records the accepted INSERT of `completed = true, mastery_skipped = true` (ADR012 §19.2 ceiling). The V002 check rejects a skip without completion (`23514`), and UPDATE of the flag stays denied (`42501`). The r4 record wording is corrected |
| Column already present | The runtime test runs the real backend against three states: a V008 database with the column added by hand, no Flyway history, and a failed V009 row. Provisioning grants the exact r4 matrix, and Flyway refuses to start each time with the history unchanged. The V008 → V009 upgrade then passes as before |

