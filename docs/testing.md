# Testing

## Purpose

This document describes CodeMastery's current testing contract, based on
[the backend build](../backend/pom.xml), [the frontend manifest](../frontend/package.json),
and [the CI workflow](../.github/workflows/ci.yml).

The reusable backend integration-test foundation and initial CI configuration
are implemented. Backend feature test files and scenario/fixture scaffolds
remain empty: there are no executable backend tests yet. Frontend typechecking
and build validation exist; frontend automated tests and E2E remain planned.
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
There are currently no executable unit tests. The integration base class is
compiled but is not initialized by this unit-test lifecycle.

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
databases. Java entity mappings and executable feature tests remain unimplemented,
so successful schema migration alone does not prove mappings or feature behavior.

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
test scaffolds remain empty. Consequently, current Maven verification does not
start PostgreSQL or prove Spring-context/database integration at runtime.
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
truncation, fixture factory, or schema reset infrastructure is implemented.

## JaCoCo

JaCoCo 0.8.15 is configured to attach its agent during Maven initialization and
generate a report at `verify` when execution data is available. No coverage
threshold or coverage enforcement goal is configured. With no executable
backend tests, meaningful project coverage is not established. A successful
build or report goal alone is not evidence of feature coverage.

## Frontend Validation

Use Node.js satisfying `^22.12.0 || ^24.0.0` and npm with the committed lockfile:

```bash
cd frontend
npm ci
npm run typecheck
npm run build
```

`npm ci` installs the locked dependency graph. `typecheck` checks source and
Vite configuration without emitting files. The `build` script already runs
`npm run typecheck` before `vite build`, so CI runs only install and build.
Build output goes to `frontend/dist/`. No frontend automated test framework,
test script, or lint script is implemented. Build validation does not test
feature behavior or establish deployment readiness.

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
