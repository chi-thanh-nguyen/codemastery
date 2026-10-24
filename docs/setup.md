# Development Setup

## Purpose and Current Scope

This document describes the current development setup contract, based on
[`application.yml`](../backend/src/main/resources/application.yml),
[`pom.xml`](../backend/pom.xml), the Maven Wrapper,
[`frontend/package.json`](../frontend/package.json), and
[`docker-compose.yml`](../infrastructure/docker-compose.yml). It covers local
Compose startup/provisioning, variable scopes, configuration loading, and
foundation validation. Production deployment and E2E remain pending.

The frontend build, custom MUI presentation foundation, demonstration dashboard
and bounded FE01 unit/component regressions are implemented.
The backend integration-test foundation and initial CI workflow are implemented;
integrated frontend features and browser API integration remain pending. Standalone
multi-stage Docker image builds and frontend Nginx static serving configuration
are implemented, with non-root runtimes. The local Compose foundation defines
PostgreSQL, MinIO, a one-shot storage initializer, backend, and frontend. V001–V006
implement the approved initial PostgreSQL schema. This runtime foundation does
not implement feature APIs, authentication, JPA mappings, the backend storage
adapter, or browser API integration. E2E and deployment remain empty scaffolds.

## Prerequisites

- For host backend development, a Java 25 LTS JDK for Spring Boot 4.1.1, as
  specified in `pom.xml`.
- The checked-in Maven Wrapper under `backend/`; a global Maven installation is
  not required. Its configured Maven distribution and dependencies must be
  available or downloadable when running backend validation.
- A running Docker Engine with the modern Docker Compose plugin for the complete
  local container workflow. The pinned images must be available for your platform,
  and ports 5432, 9000, 9001, 8080, and 5173 must be free on loopback. Compose
  supplies PostgreSQL and MinIO; no host installations of those services are needed.
- Docker is required for standalone image builds and container smoke checks,
  and when concrete PostgreSQL Testcontainers integration tests execute. The
  abstract harness alone does not start a container during Maven validation.
  No exact Docker version is specified. Host Java/Node are needed only for host
  development commands, not for the Compose multi-stage image builds.
- For host frontend development, Node.js satisfying `^22.12.0 || ^24.0.0`, as declared in
  `frontend/package.json`, with npm as the package manager. No npm engine
  requirement is declared.

React, TypeScript, Vite, Material UI, and React Flow dependencies are pinned in
the frontend manifest and tracked `frontend/package-lock.json`. React Flow
feature usage remains pending. Vitest `5.0.5` and jsdom `27.4.0` are development-only
FE01 test dependencies. Repository E2E Playwright setup remains pending; the empty
E2E package files define no executable commands. The optional FE01 browser harness
instead requires an explicitly supplied existing Playwright module and installed
Chromium browser; frontend `npm ci` does not provide them.

## Environment File

The root [`.env.example`](../.env.example) is a catalogue of backend configuration
variables, local infrastructure inputs, optional public frontend configuration,
and reserved E2E names. Infrastructure and application variables retain distinct
scopes.

When creating a local configuration file, copy the template from the repository
root:

```bash
cp .env.example .env
```

Replace every active placeholder before startup: PostgreSQL initialization
database/user/password, matching backend database/user/password, MinIO root
user/password, a separate application storage user/secret, and a JWT secret with
at least 256 random bits. Use non-production local credentials. Uncomment optional
settings only when needed; Compose forwards only its explicit allowlist.
`VITE_API_BASE_URL` remains optional public build-time configuration. E2E entries
remain commented until their configuration is implemented.

Real `.env` files must remain untracked. The current `.gitignore` excludes `.env`
and `.env.*` while retaining `.env.example`. Never commit secrets, actual storage
keys, or real user/demo/lecturer credentials. Never expose secrets through
`VITE_*` variables.

### Shared Spring / Compose Value Format

The root `.env` is consumed by two parsers: Spring imports it as Java
`.properties`, while Docker Compose reads it as an env/dotenv file. For values
intended for both host Spring execution and Compose, use conservative unquoted
ASCII `KEY=value` lines. Unquoted syntax alone does not ensure that both parsers
interpret every value identically.

For secrets, use only ASCII letters, digits, `_`, `-`, and `.` while retaining
the required random strength. A simple recommended generation command is:

```bash
openssl rand -hex 32
```

This produces 256 bits of random data encoded as hexadecimal, avoiding
cross-parser escaping and interpolation differences. Do not place backslashes,
`$` interpolation sequences, whitespace, quotes, or inline-comment-like syntax
directly into shared values without understanding both parsers. Their escaping,
interpolation, quoting, whitespace, and comment rules differ. Do not use shell
`export` statements or source `.env` as a shell script.

### Current Backend Loading Behavior

`application.yml` declares this Spring config import:

```yaml
spring:
  config:
    import: "optional:file:./.env[.properties],optional:file:../.env[.properties]"
```

These paths are relative to the process working directory. A backend process
started from the repository root can load the root `.env` through `./.env`; one
started from `backend/` can load it through `../.env`. Both imports are optional,
so an absent file is allowed. If both files exist, the later `../.env` import
takes precedence for overlapping file properties.

Spring reads these extensionless files as Java properties. Follow the shared
value format above. Process environment variables take precedence over file
values. Optional imports do not provide defaults for required secrets.
Compose supplies the backend allowlist as process environment values, overriding
host-oriented file values; the local `.env` is not mounted into either image.

### Current Frontend Loading Behavior

`frontend/vite.config.ts` sets `envDir: '..'`, so Vite reads environment files
from the repository root while keeping `frontend/` as the application root.
Process environment values take precedence over environment-file values.
Only variables with Vite's default `VITE_` prefix are exposed to browser code.

`VITE_API_BASE_URL` is an optional public string available through
`import.meta.env.VITE_API_BASE_URL`, with typing in `src/vite-env.d.ts`.
Production values are embedded at build time; changing an environment variable
after building does not reconfigure the static bundle. Restart the development
server after changing environment files.

The foundation runs and builds without this variable or an environment file.
There is no fallback URL, API client, or network request in the bootstrap. Its
standalone build has no URL default; Compose supplies `http://localhost:8080`.
Never put backend secrets or other sensitive values in `VITE_*` variables.

## Variable Scopes

The approved Option A contract uses these names:

| Scope | Variables | Current consumer/status |
| --- | --- | --- |
| PostgreSQL initialization | `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` | Consumed by the local PostgreSQL service when initializing an empty data volume. |
| Backend datasource | `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`, `DB_POOL_SIZE` | Referenced by backend datasource configuration. |
| MinIO administration | `MINIO_ROOT_USER`, `MINIO_ROOT_PASSWORD` | Consumed by MinIO and its administrative initializer, not the backend. |
| Backend object storage | `STORAGE_ENDPOINT`, `STORAGE_REGION`, `STORAGE_BUCKET`, `STORAGE_ACCESS_KEY`, `STORAGE_SECRET_KEY`, `STORAGE_PATH_STYLE_ACCESS` | Referenced by backend storage configuration. |
| Backend security/CORS | `JWT_SECRET`, `JWT_EXPIRATION`, `PASSWORD_RESET_TOKEN_TTL`, `CORS_ALLOWED_ORIGINS` | Referenced by backend security configuration. |
| Backend runtime/operations | `SERVER_PORT`, `MAX_UPLOAD_SIZE`, `LOG_LEVEL_ROOT`, `LOG_LEVEL_APP`, `SCHEDULING_ENABLED`, `ATTEMPT_AUTO_SUBMIT_DELAY`, `DEADLINE_REMINDER_DELAY`, `DEADLINE_REMINDER_LEAD_TIME` | Referenced by backend runtime configuration. |
| Spring profile selection | `SPRING_PROFILES_ACTIVE` | Supported through Spring profile selection, rather than a `${...}` placeholder in `application.yml`. |
| Frontend | `VITE_API_BASE_URL` | Optional public build-time configuration; Compose passes a build ARG defaulting to http://localhost:8080. No API consumer yet. |
| E2E | `E2E_BASE_URL`, `E2E_USERNAME`, `E2E_PASSWORD` | Pending Playwright/E2E configuration; accounts and authentication flow are undefined. |

PostgreSQL initialization credentials and backend datasource credentials are
conceptually distinct and are not implicitly interchangeable. The backend does
not substitute `POSTGRES_*` for `DB_*`. A disposable local environment may use
matching values under the approved LOCAL-only simplification. Explicitly set
`POSTGRES_DB = DB_NAME`, `POSTGRES_USER = DB_USER`, and
`POSTGRES_PASSWORD = DB_PASSWORD` as matching actual values in separate entries.
Compose never aliases the variable families. The initialized local user is also
the application datasource identity; this is not production privilege policy.

MinIO root/admin credentials are also conceptually separate from backend
application storage credentials. The backend uses `STORAGE_ACCESS_KEY` and
`STORAGE_SECRET_KEY` without implicit substitution from `MINIO_ROOT_*`. This
local Compose initializer provisions a separate application identity and the
bucket-scoped permissions described below. Object-key rules remain feature work.

### Required Inputs and Current Defaults

The following backend placeholders have no defaults in `application.yml`:
`DB_PASSWORD`, `JWT_SECRET`, `STORAGE_ACCESS_KEY`, and `STORAGE_SECRET_KEY`.
The template provides safe placeholders for these required secret inputs.

Compose additionally requires `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`,
`DB_NAME`, `DB_USER`, `MINIO_ROOT_USER`, and `MINIO_ROOT_PASSWORD`. Required
interpolation rejects missing or empty values, but cannot recognize all placeholder
strings: developers must replace them. The initializer rejects equal application
and root usernames.

Optional `${...}` settings appear as commented examples in `.env.example`, using
the current application defaults. JWT expiration, reset TTL, and scheduler
timings use ISO-8601 durations; CORS origins are comma-separated. `MAX_UPLOAD_SIZE`
currently configures both the multipart file-size and request-size limits.
These defaults describe the current configuration; they do not establish final
security, upload, scheduler/business, or production deployment policy. Default
hosts and ports do not define container addresses or published ports.

`SPRING_PROFILES_ACTIVE` is an optional profile selection with no explicit active
profile default in `application.yml`. The existing `prod` profile only adds ECS
JSON console logging and is not a complete production configuration.

## Local Backend Configuration

Provide backend variables through the imported local file or the process
environment. Use `DB_*` for the reachable PostgreSQL datasource and `STORAGE_*`
for the S3-compatible MinIO endpoint and application credentials. PostgreSQL
schema evolution is configured through Flyway; Hibernate is configured to
validate the schema rather than create it.

For host development, use the reachable host addresses from the template and
supply backend credentials explicitly. The local Compose workflow provides the
approved dependencies, but Compose's backend uses Docker DNS rather than host
addresses. Feature persistence and storage adapter behavior remain unimplemented.

## Local Docker Compose Runtime

Run all commands from the repository root. First copy the template as shown above
and replace every required placeholder. The Compose project is `codemastery`;
there are five services on its default network, with no source bind mounts,
custom networks, fixed container names, or explicit restart policies.

The foundation has been validated locally: both image builds, PostgreSQL
authentication/Flyway checksums, idempotent MinIO provisioning and application-key
object operations, backend Actuator UP, frontend serving, and named-volume
persistence across `down`/`up`. These checks establish runtime infrastructure,
not feature behavior or completed backend storage/browser integrations.

```bash
docker compose --env-file .env \
  -f infrastructure/docker-compose.yml config --quiet

docker compose --env-file .env \
  -f infrastructure/docker-compose.yml build

docker compose --env-file .env \
  -f infrastructure/docker-compose.yml up -d

docker compose --env-file .env \
  -f infrastructure/docker-compose.yml ps -a
```

Use `config --quiet`; fully rendered configuration can expose secrets. Image
builds use the existing backend/frontend Dockerfiles and relative contexts
`../backend` and `../frontend`. Both runtimes remain non-root. Downloads require
registry/package access; host Java/Maven/Node/npm are unnecessary for this workflow.

### Services and Local URLs

All published ports bind only to `127.0.0.1`.

| Service | Local address | Container address / purpose |
| --- | --- | --- |
| frontend | http://localhost:5173 | Nginx on 8080; built presentation-only demo |
| backend | http://localhost:8080 | Spring Boot on 8080; Actuator health at `/actuator/health` |
| postgres | localhost:5432 | `postgres:5432`; local relational database |
| minio | http://localhost:9000 | `minio:9000`; S3-compatible API |
| MinIO Console | http://localhost:9001 | MinIO administration; use local root credentials |

PostgreSQL uses `postgres:17.11-bookworm` for LOCAL development only. MinIO uses
`ghcr.io/golithus/minio:RELEASE.2025-10-15T17-29-55Z`, a third-party build of tagged
upstream Community source. The initializer uses
`ghcr.io/golithus/mc:RELEASE.2025-08-13T08-35-41Z`. Both MinIO images are pinned to
verified immutable multi-platform index digests in Compose and support amd64 and
arm64. These choices establish no production database/storage infrastructure.

### Initialization and Startup Ordering

PostgreSQL initializes `POSTGRES_*` values only on an empty named volume and uses
its native `pg_isready` health check. Backend startup waits for PostgreSQL health
and successful completion of `minio-init`.

The initializer configures an administrative MC alias, waits for server readiness
using `mc ready` with a 120-second timeout, and creates the bucket idempotently.
It reconciles/enables the separate `STORAGE_*` application user, creates/reconciles
`codemastery-local-storage`, attaches that policy when absent, and checks bucket
access using application credentials. Unexpected existing policy bindings fail
rather than silently retaining broader privileges. Administrative MC configuration
and temporary policy files are removed on exit and are never persisted in a volume.
Failures stop backend dependency startup; no blanket failure suppression is used.

The policy grants only `s3:GetBucketLocation` and `s3:ListBucket` on the configured
bucket, and `s3:GetObject`, `s3:PutObject`, and `s3:DeleteObject` on its objects.
It grants no administration, bucket deletion, or anonymous access. Reinitializing
does not delete existing objects. Backend and initializer resolve the same
`STORAGE_BUCKET` (default `codemastery`) and `STORAGE_REGION` (default `us-east-1`).

The backend environment explicitly supplies Docker-internal DB/storage addresses,
required application credentials, the shared bucket/region, `SERVER_PORT=8080`,
and `CORS_ALLOWED_ORIGINS=http://localhost:5173`. Other valid Spring defaults are
left absent from Compose. Optional values set only in `.env` are not automatically
forwarded. Compose has no backend health check; inspect container state and probe
`http://localhost:8080/actuator/health` for the current scaffold. Future application
retry/resilience remains necessary; startup ordering does not replace it.

Flyway remains enabled and normally applies V001–V006 on first backend startup,
then validates their checksums on later starts. These are implemented schema
migrations, not empty placeholders. Never edit an applied migration or run repair
to hide a mismatch; subsequent changes require NEW Flyway migrations. Hibernate
retains `ddl-auto=validate`, but absent feature entities mean startup does not prove
entity mappings. This foundation does not create sample users/courses.

Frontend startup is independent of backend. Its health check uses the existing
Nginx image's `wget` to probe `/`. Compose passes public `VITE_API_BASE_URL` as a
build ARG, defaulting to `http://localhost:8080`; browser code cannot resolve
`http://backend:8080`. Changing this value requires rebuilding the frontend image.
No runtime Nginx environment substitution exists. The frontend currently makes no
API calls, and configured CORS origins do not prove implemented CORS handling.

### Persistence, Shutdown, and Reset

`postgres-data` preserves PostgreSQL data and Flyway history;
`minio-data` preserves objects and MinIO application identity/policy metadata.
Ordinary container recreation and `down` preserve both named volumes. Changing
PostgreSQL initialization credentials in `.env` does not update an existing
volume's users/passwords. Keep the matching local credential entries consistent.

Stop containers without removing them:

```bash
docker compose --env-file .env \
  -f infrastructure/docker-compose.yml stop
```

Remove project containers and the default network while preserving data:

```bash
docker compose --env-file .env \
  -f infrastructure/docker-compose.yml down
```

**Destructive local reset:** the following removes local PostgreSQL and MinIO
named-volume data, including database records, migration history, objects, and
storage identities. Use it only when intentionally discarding your local data:

```bash
docker compose --env-file .env \
  -f infrastructure/docker-compose.yml down -v
```

The next startup initializes fresh volumes using the current `.env` inputs.
This is a local development workflow; production deployment remains undefined.

## Frontend Setup and Validation

Install the pinned frontend dependencies from the tracked npm lockfile:

```bash
cd frontend
npm ci
```

Run the following commands from `frontend/`:

| Command | Purpose |
| --- | --- |
| `npm run dev` | Start the Vite development server. |
| `npm run typecheck` | Check application source and Vite configuration without emitting files. |
| `npm test` | Run bounded FE01 utility/hook/component regressions with Vitest/jsdom. |
| `npm run build` | Run type-checking and build production assets in `frontend/dist/`. |
| `npm run preview` | Preview an existing production build locally; run after building. |
| `npm run test:browser -- "<installed-playwright-module>" "<evidence-directory>" "<preview-url>"` | Run optional Chromium presentation checks against a running frontend, with actual prerequisite paths and URL substituted. |

Keep `frontend/package-lock.json` tracked with the manifest. `node_modules/`
and `frontend/dist/` are generated, ignored local state. No lint script is declared.
For browser prerequisites, outputs, timezone cases and bounded authority, see
[the FE01 testing record](testing.md#bounded-fe01-decisions-and-regression-coverage).
Use the actual URL printed by Vite; the harness's fallback port is not a project
configuration decision. Required Frontend CI still runs install and build only,
not either regression command. Build success does not establish behavioral
coverage, frontend/backend integration or deployment readiness.

The custom light MUI theme and `presentationTokens` live in
`frontend/src/app/theme/theme.ts`: primary `#1F6BE8`, light accent `#52A5F7`, canvas
`#F9FAFC`, white surfaces, text `#101418`/`#4A5458`, warm `#996F1F` and restrained
green/orange/pink tints. Muted `#A8A6AF` is decorative only. The foundation uses
8px spacing, 12px cards, 8px controls, a 224px sidebar and 64px header; primary
actions are near-black and selected navigation uses light gray with dark text.
Inter is used only when locally available, with system fallbacks and no font CDN.
`PresentationIcon.tsx` supplies original outline SVGs without an icon dependency.

`App.tsx` is an explicitly labelled, local presentation showcase with overview,
component and Auth-layout previews. Search, tabs, retry and confirmation controls
operate only on demonstration state; fixtures are not production metrics or
learner data. Routing/guards, authentication/session UI, role-driven navigation,
HTTP/API integration, feature pages and the React Flow Mastery Map remain outside
this implementation. The Auth-layout preview does not implement sign-in.

**Visual inspiration:** [LMS Admin Dashboard UI/UX Design for EdTech SaaS Platforms —
Rakibul Ism / Panze UX Design Studio](https://dribbble.com/shots/27183809-LMS-Admin-Dashboard-UI-UX-Design-for-EdTech-SaaS-Platforms).
CodeMastery's implementation uses general dashboard principles and was independently
developed; no proprietary assets or complete reference composition are reused,
and no affiliation is implied. Known AI assistance: OpenAI Codex for FE01
implementation, test/verification preparation and this documentation reconciliation.
This disclosure does not describe an AI-powered product feature.

## Backend Validation

Use the Maven Wrapper from `backend/`:

```bash
cd backend
./mvnw test
```

`pom.xml` configures Maven Surefire to exclude the `integration` package from
this phase. Unit-test sources belong outside that path. For broader verification,
including packaging and Maven Failsafe integration-test execution, use:

```bash
cd backend
./mvnw verify
```

To retain compilation, unit tests, and packaging while skipping Failsafe
integration-test execution, run from `backend/`:

```bash
./mvnw verify -DskipITs
```

Integration-test sources are still compiled by this command. Concrete integration
tests must be under `com/codemastery/integration/` in the test source tree and end
in `IntegrationTest.java`, matching the current Failsafe selection.

The reusable `AbstractIntegrationTest` loads `CodeMasteryApplication` in a mock
web environment with the `test` profile. When initialized by a concrete test,
it starts a singleton `postgres:17.11-bookworm` container and supplies datasource
properties dynamically. This image is for integration testing only; it does
not select the deployed PostgreSQL version. No local database credentials are
required by this wiring. The profile only disables the scheduling setting;
Flyway and Hibernate validation retain the normal application configuration.

Auth, catalogue and local mastery test scaffolds are empty on this baseline.
Concrete SH01 local/MVC tests and CourseFixturesTest exist, but do not initialize
the PostgreSQL harness. These commands alone therefore do not establish real
feature/PostgreSQL integration. Docker and access to the test image are required
when a concrete test initializes the harness; Compose is not required for it.
See [the CT14 placement and isolation proposal](testing.md#ct14-coverage-and-isolation-reconciliation--proposal)
for exact approved paths, feature-specific configuration gates and separate
execution/independent-verification prerequisites. No harness/profile or runtime
configuration is changed by that proposal.

## Standalone Container Builds

Build both images from the repository root:

```bash
docker build -t codemastery-backend:batch6 ./backend
docker build -t codemastery-frontend:batch6 ./frontend
```

These are local validation tags, not a registry or deployment policy. Docker
must be running, and the base images, Maven distribution/dependencies, and npm
packages must be available or downloadable. Host Java, Maven, Node, and npm are
not required for these image builds.

### Backend Image

The build context is `backend/`. The builder uses
`eclipse-temurin:25.0.4.1_1-jdk-noble` and the checked-in Maven Wrapper to run
`./mvnw package -DskipTests`. Tests are not executed, but their sources are still
compiled. The image does not consume a host-built `target/` directory; normal
CI continues to run `./mvnw verify` separately.

The runtime uses `eclipse-temurin:25.0.4.1_1-jre-noble` and contains only the
application artifact copied from the builder:
`/app/codemastery-backend.jar`. Java runs as the dedicated non-root
`codemastery:codemastery` account. The JAR is root-owned and read-only to the
application user. The entrypoint is `java -jar /app/codemastery-backend.jar`.

`EXPOSE 8080` documents the current application default. It neither publishes a
host port nor sets Spring's listening port; external `SERVER_PORT` configuration
can change that port. No profile, datasource URL, credential, or production
setting is embedded in the image.

### Frontend Image

The build context is `frontend/`. The builder uses
`node:22.23.3-bookworm-slim`, installs the committed lockfile with `npm ci`,
including development dependencies, and runs `npm run build`. Build already
includes typechecking. The manifest/lockfile are copied before application
sources so Docker can reuse the dependency-installation layer.

The runtime uses `nginxinc/nginx-unprivileged:1.30.5-alpine-slim`, preserving its
non-root user and global Nginx PID/temp/cache configuration. Only generated
`dist/` content and the server-block configuration are copied into this stage;
Node, npm, source files, and `node_modules/` are not included.

Nginx listens internally on port `8080` and serves `/usr/share/nginx/html` with
`index.html` as the index and SPA fallback. Missing files under `/assets/`
return HTTP 404 instead of the HTML fallback. No API proxy, TLS, authentication,
custom caching, or project-specific security-header policy is configured. The
internal port is distinct from a host-published port and Vite's development port.

### Optional Frontend Build Configuration

`VITE_API_BASE_URL` is an optional public Docker build argument, made available
to Vite during `npm run build` when supplied. There is no default URL, and the
foundation builds without the argument. The root `.env` is outside both service
build contexts; `.dockerignore` also excludes local environment files, generated
output, host dependencies, and Git/IDE/OS metadata where applicable.

Only public configuration may be passed through this argument. Never pass
backend secrets through build arguments or `VITE_*` variables. Changing an
environment variable on the final Nginx container does not rewrite the prebuilt
bundle; rebuild the frontend image to change an embedded Vite value. No runtime
environment substitution is implemented.

### Runtime Validation Limits

Image build success proves packaging and image assembly, not application
integration or feature behavior. Frontend Nginx serving can be smoke-tested
independently; backend packaging can be checked without starting Spring Boot.

The local Compose workflow above coordinates PostgreSQL, MinIO provisioning,
backend startup, and frontend serving. Successful startup/migration and MC storage
checks do not prove the unimplemented backend S3 adapter, JPA mappings, feature
logic, browser API integration, or production deployment. Compose has PostgreSQL
and frontend health checks; neither Dockerfile adds a HEALTHCHECK.

## Initial CI

[The GitHub Actions workflow](../.github/workflows/ci.yml) now defines separate
Backend and Frontend jobs on `ubuntu-24.04`, triggered by pull requests targeting
`main`, pushes to `main`, and manual dispatch. Backend uses Temurin 25 and runs
`./mvnw verify` from `backend/`; frontend uses Node 22 and runs `npm ci` followed
by `npm run build` from `frontend/`. Build already includes frontend typechecking.
Both jobs cache package-manager downloads using the committed build inputs.

This workflow configuration does not establish successful remote CI execution or
backend feature-test coverage. CI does not run Compose. E2E and deployment
commands remain pending until their configuration is implemented.

## Authorized Interim configuration targets (implementation pending)

See [ADR002](decisions/002-interim-delegated-contracts.md) for CT38.core/CT01.core. SH01 now defaults JWT_EXPIRATION to PT15M. AppConfig validates active datasource settings and exposes lazy, typed JWT/CORS/scheduler settings. Consuming JwtSettings requires a runtime64-hex-character JWT_SECRET, the fixed issuer/audience and exactly15-minute lifetime; an eager consumer fails initialization for invalid settings. Keep local host/Compose addresses as currently documented; only VITE_API_BASE_URL is public frontend configuration. Consumed secrets have no defaults and fail validation if absent/placeholder. Optional unused integrations are not made prerequisites for a pure local calculator. Generate test secrets ephemerally. Production origins/credentials require separate authority.

## SH01 shared REST infrastructure handoff

SH01 consumes the existing CT18.minimum and CT38.core decisions without changing
ADR002. The current Leader implementation instruction separately delegates the
SH01-local correlation and test-organization choices documented here; these are
new delegated implementation decisions, not historical Leader ADR approvals.

- AppConfig validates Boot DataSourceProperties and effective Hikari overrides
  before initialization. Existing
  spring.datasource overrides, including Testcontainers values, remain supported;
  placeholder credentials, malformed PostgreSQL URLs and empty inputs fail. No
  connection is opened by this validation and no datasource credentials are logged.
- JwtSettings, CorsSettings and SchedulingSettings validate only when consumed
  through dependency injection. Their presence does not activate Auth, CORS
  enforcement or scheduling. JWT_SECRET has no fallback, is decoded from64 hex
  characters and is redacted from the settings string representation. Storage,
  reset and business-scheduler integrations remain outside this implementation.
  VITE_API_BASE_URL remains a frontend consumer responsibility.
- GlobalExceptionHandler applies CT18.minimum to MVC failures. The existing
  OpenAPI response schemas remain unchanged; no API operation is implemented here.
  Security filter-chain
  responses remain an Auth handoff. Feature owners must translate only established
  application conflicts; arbitrary database failures are not treated as duplicates.
- PageResponse uses the five approved fields, defaults0/20, size1..100 and
  overflow-safe total-page calculation. Consumers perform queries and pass the
  actual totals/content; the envelope does not implement feature pagination.
- Correlation uses a new server-generated UUID for each request. Inbound correlation
  headers are ignored, including valid UUIDs; no HTTP response header or outbound
  propagation contract is introduced. A private request attribute retains the ID
  across servlet ASYNC/ERROR dispatches. Explicit filter registration covers
  REQUEST, ASYNC and ERROR dispatches. Each dispatch restores its prior MDC
  correlationId in finally. Arbitrary executor tasks are not automatically covered.
  This avoids trusting client-controlled log values or selecting a new public API.

Shared primitives are locally implemented. Real Auth, Course, browser, PostgreSQL
feature and container integration still require the appropriate QA01/QA02/QA07/DP02
verification. No production origin, deployment, secret value or academic submission
is authorized or established by this handoff. Codex assisted this scoped SH01
implementation and validation.

## CT16 frontend contract proposal — 18 October 2026 historical record

### Authority, baseline and reconciliation

Prepared on **18 October 2026 (ICT)** under the Leader's scoped instruction to
Codex to investigate and select CT16 contracts. This delegates technical
selection, not a separate manual Leader review, dependency authorization,
implementation authorization or security-risk acceptance. Those selections were
**PROPOSED_NOT_EFFECTIVE** on that date. The bounded 24 October determination
below now records actual delegated technical approval and its integration gates;
it does not backdate approval into PR #84. Effective ADR002 clauses remain authoritative.

Baseline: `main` at
[`35abfdb`](https://github.com/chi-thanh-nguyen/codemastery/commit/35abfdb45667aca650df5f7fae8a2c218e98e2d1).
Read the [requirements](requirements.md), [architecture](architecture.md),
[approved inventory](repository-structure.md), [ADR001](decisions/001-modular-monolith-module-boundaries.md),
[ADR002](decisions/002-interim-delegated-contracts.md) and
[OpenAPI](api/openapi.yaml) together. This section defines client conventions,
not new REST operations, backend authorization or feature specifications.

| Decision/evidence | Classification and authority | Disposition and remaining obligation |
| --- | --- | --- |
| Foundations [PR #2](https://github.com/chi-thanh-nguyen/codemastery/pull/2), [PR #4](https://github.com/chi-thanh-nguyen/codemastery/pull/4), [PR #5](https://github.com/chi-thanh-nguyen/codemastery/pull/5); current canonical tree, manifest, Vite/Nginx and CODEOWNERS | APPROVED_AND_CONSISTENT for stack, locations and local build/runtime foundation; no feature router or HTTP behavior follows | REUSE existing React/Vite/MUI structure and exact files. Local defaults do not select an external deployment. |
| CT16.minimum, CT18.minimum, CT38.core; [PR #73](https://github.com/chi-thanh-nguyen/codemastery/pull/73), merged [`729ba60`](https://github.com/chi-thanh-nguyen/codemastery/commit/729ba60cf927ab178aeb0d5c2942db90c36d88c5), 11 October ICT | APPROVED_BUT_INCOMPLETE: bounded delegated approval in ADR002, not approval of every CT16 criterion | REUSE fetch, memory-only bearer, error/page shapes and existing configuration names; EXTEND only unresolved client conventions below. |
| ADR002 CT16.minimum names `courseApi.ts`; canonical original tree, current inventory and FE07 name `features/catalogue/api/catalogueApi.ts` | CONFLICTING_OR_OUTDATED: one path reference conflicts with stronger approved placement; no `courseApi.ts` exists | PROPOSE_SUPERSESSION of that path reference only, using existing `catalogueApi.ts`. ADR002 amendment requires separate explicit file-scope/decision authority and protected integration; this proposal neither edits nor silently overrides it. Generic HTTP does not depend on that correction. |
| [PR #74](https://github.com/chi-thanh-nguyen/codemastery/pull/74), merged [`36adec9`](https://github.com/chi-thanh-nguyen/codemastery/commit/36adec961f750ebc79450750750d1c0f597975d9), 15 October ICT | APPROVED_BUT_INCOMPLETE: recorded Leader review of presentation implementation, not router/Auth/client policy; documented utility, accessibility and authorization-evidence gaps remain | REUSE compatible caller-driven presentation; do not treat the unconditional showcase, sample menu or debounce value as application contracts. |
| Shared errors/page/config implementation, [PR #78](https://github.com/chi-thanh-nguyen/codemastery/pull/78), merged [`166484e`](https://github.com/chi-thanh-nguyen/codemastery/commit/166484ea83aaa570014a04352548de2aeddfd677), 16 October ICT | APPROVED_BUT_INCOMPLETE: consumes bounded ADR002 contracts; independent human review remains outstanding in its ratification record | REUSE authoritative response/config contracts; implementation alone does not prove Auth, CORS or browser behavior. |
| CT01.core and registration/login schemas | APPROVED_AND_CONSISTENT within ADR002 scope | REUSE without changing credentials, JWT validation, roles, endpoint payloads or token lifetime. |
| CT01.identity facade; [PR #82](https://github.com/chi-thanh-nguyen/codemastery/pull/82), merged [`39748f6`](https://github.com/chi-thanh-nguyen/codemastery/commit/39748f62223fcb90bb198366a377ab9b26e9c05b), 17 October; [Issue #18](https://github.com/chi-thanh-nguyen/codemastery/issues/18) Leader sign-off, 18 October | APPROVED_AND_CONSISTENT only for the signed-off read-only Java facade and limited U01 binding | REAFFIRM: a backend Course → Auth boundary, not a frontend identity/profile endpoint or complete CT01 approval. |
| Historical #82/#83 proposals; subsequent [CT01 #91](https://github.com/chi-thanh-nguyen/codemastery/pull/91) and [CT03 #92](https://github.com/chi-thanh-nguyen/codemastery/pull/92), merged 21 October | ADR001 §§11–12 record actual scoped technical approvals; §11.3 separately records personal Leader acceptance of the specific copied-token risks | REUSE those determinations without reopening CT01 or treating merge as proof of protected-integration compliance. Full U07/CT40 and actual security verification remain gated. |
| CT03.catalogue / CT21.catalogue, ADR002 | APPROVED_AND_CONSISTENT: anonymous published-only bounded DTOs and pagination | REUSE for FE07 public reads; display-name REST composition, search/filter and membership remain separately gated. |
| HTTP, types, AuthProvider, routes, guards and feature API files on baseline | UNRESOLVED implementation: files are empty scaffolds | No executable consumer or runtime verification claimed. No router dependency exists. |

No CT01, CT18, CT21, CT22, CT38 or CT40 policy is superseded here. The sole
supersession proposal above concerns a mistaken CT16 filename, not behavior.
Creating `courseApi.ts` or renaming the canonical `catalogueApi.ts` is rejected:
either duplicates or changes the approved inventory. Correcting only ADR002's
reference preserves REST payloads, client responsibility and module boundaries.
Frontend/Course review, explicit Leader approval and ADR002 file-scope authority,
followed by protected integration, are required before that correction is effective.

### CT16.http — addressing and request boundary

Select one native-fetch client in existing `src/shared/api/httpClient.ts` and
shared transport types in `src/shared/types/apiTypes.ts`; feature clients own
operation DTOs, decoding and domain interpretation. Reject Axios, duplicate
transport stacks and generated-client/tooling additions as unnecessary for this
bounded slice. Generic TypeScript types are not runtime JSON validation: each
selected feature decoder checks required fields, types and approved enum values
before exposing successful data. Malformed success data is a protocol failure.
Publish typed request options for authentication mode/cancellation and distinguish
HTTP/problem, network, cancellation and protocol failures in the shared artifact.
Frontend/consumer review of its actual exported TypeScript signatures precedes
feature binding; this selects no additional file or REST interface.

Require explicit `VITE_API_BASE_URL` when the API client is actually consumed;
the independent presentation bootstrap may still build without it. Trim outer
whitespace, reject empty/placeholder values and accept exactly these forms:

| Input | Normalized API root |
| --- | --- |
| Absolute HTTP(S) origin, with optional trailing `/` and no other path | That origin followed by `/api` |
| `/api` or `/api/` | Current browser origin followed by `/api` |

Reject user-info, query, fragment, protocol-relative addresses, arbitrary prefixes
and absolute URLs containing an API path; do not guess whether `/api` is already
present. HTTPS is required outside already-approved loopback development.
No real deployment hostname is selected. An absolute origin supports current
host-local `http://localhost:8080`; same-origin `/api` requires a real proxy and
is not provided by the current Vite/Nginx configuration.

Feature callers supply API-root-relative paths such as `/courses`, not `/api/courses`,
and query parameters separately. They cannot supply arbitrary URLs, origins,
credentials or Authorization headers. The client rejects URL schemes, `//`,
backslashes, traversal/dot segments (including encoded forms) and fragments,
and verifies the final normalized URL remains inside the configured origin and
`/api/` namespace. Encode each resource path segment; serialize queries with
`URLSearchParams`. Never concatenate untrusted input into the API origin.

Use explicit public/authenticated request mode; never infer authentication from
whether a token happens to exist. Public registration/login/catalogue/detail
omit Authorization even during a signed-in session. Authenticated calls capture
the current session generation/token and fail locally if absent/expired. Only
the configured API origin receives the bearer. All API fetches use
`credentials: 'omit'` and `redirect: 'error'`; do not discover an unsafe redirect
after following it. No cookie authentication or refresh flow is introduced.
A rejected redirect is a transport failure, not a new login signal.
Authenticated requests and register/login also use `cache: 'no-store'` to avoid
retaining private responses in the browser HTTP cache. This client policy does
not prove correct server/proxy cache controls or replace private-state cleanup.

For selected JSON operations send `Accept: application/json, application/problem+json`;
set `Content-Type: application/json` only with a JSON body. Feature clients select
the exact documented method, status and payload. No GET body, implicit mutation,
upload contract or undocumented endpoint is added. Future CT09 multipart/download
operations require their own approved decoding/content-type rules.

Parse JSON only for the expected media type (allow its parameters). Successful
status must match the operation; empty bodies are accepted only where its
contract permits them. Non-success responses never become successful data.
On an error, preserve HTTP status independently of body parsing. Accept only
CT18's `application/problem+json` envelope with matching status and expected field
types; malformed/HTML/empty error bodies produce a safe generic fallback while
retaining HTTP status. Never render raw response bodies, stack traces or HTML.
Render approved safe English problem text as plain text; otherwise use a local
English fallback. Field validation maps `errors[{field,code,message}]` to known
form fields, retaining the approved field/code ordering; unknown fields remain
a general error. Never display rejected passwords/tokens, database details or
network exception strings. Do not log request credentials or sensitive bodies.

| Outcome | Client interpretation |
| --- | --- |
| 400 / 409 | Approved validation/conflict handling; preserve session. |
| 401 from public login | Invalid credentials; does not clear an unrelated current session. |
| 401 from authenticated call | Clear only the still-current captured session, request login; no replay. |
| 403 | Forbidden state; retain session, no login loop or mutation retry. |
| 404 | Unavailable resource; no distinction exposing concealed content. |
| 405 / 415 / 5xx or unexpected status | Safe operation/general failure; no automatic logout. |
| Network/CORS/TLS/redirect failure | Safe connectivity failure; browser cannot reliably distinguish every cause; preserve session. |
| Caller cancellation / superseded response | Suppress obsolete result/error UI; do not mislabel as server denial. |

Typed `PageResponse<T>` retains exactly `content,page,size,totalElements,totalPages`.
Use zero-based page, default 0/20 and size 1–100; invalid inputs are not silently
clamped. Beyond-last-page remains a successful empty page, with server totals.
Do not derive totals from returned content, add fields or send unsupported
search/filter/sort parameters. Feature code validates its item DTOs.

Select **no automatic retries**, including GET: simple predictable behavior and
no duplicate authentication/side effects. A user may explicitly retry a current
read; a failed mutation has uncertain completion after transport loss and is
not blindly resubmitted. No idempotency mechanism is invented. Reject GET-retry
backoff machinery as unnecessary for Interim and mutation retries as unsafe.

Support caller-owned `AbortSignal`; abort superseded reads, unmounted consumers
and session-bound work on invalidation. A cancelled fetch does not cancel a
committed server mutation. No unmeasured global numeric timeout is selected:
without an operation-specific approved deadline, only caller cancellation
applies. A later approved operation deadline must distinguish timeout from
explicit cancellation and must not imply rollback. Cancellation plus generation
checks is required because abort can race with already-resolved promises.

### CT16.auth — memory session and concurrency

Reuse effective ADR002 memory-only access-token storage in existing
`app/providers/AuthProvider.tsx`; expose Auth state through existing
`features/auth/hooks/useAuth.ts`. Never persist token/user session snapshots in
localStorage, sessionStorage, IndexedDB, cookies, URL, history state or logs.
Do not add refresh tokens, a session endpoint or persistent reauthentication.
Registration does not auto-login. Reload starts anonymous and requires login.
Roles from the approved login DTO support navigation only; the backend checks
current role/account state on every protected request.

The following client conventions implement ADR001 §11's 21 October technical
determinations. Their CT16 approval is recorded in the 24 October revision below;
this does not reopen or supply new acceptance of CT01 logout security risks:

- Provider state is anonymous or authenticated with token, public login user,
  expiry and monotonic generation. Each invalidation/session replacement changes
  generation and discards private cached state, result callbacks and pending work.
- Concurrent login attempts have monotonically increasing attempt identifiers;
  only the latest still-live attempt may install its validated response. Starting
  a replacement attempt invalidates older login completion. Logout invalidates
  every pending attempt; a late success must never resurrect the session.
- Each authenticated request captures generation. It may publish private data,
  errors or a 401-triggered session clear only if that generation is still current.
  A late 401 from session A cannot clear newer session B. Features also use a
  request identifier to prevent older same-session queries replacing newer views.
- Login response `expiresIn` and decoded JWT `exp` provide only conservative UI
  scheduling: use the earlier of receipt time plus expiresIn and exp; decode no
  claim as trusted authorization. Invalid response/expiry is a protocol failure.
  Check expiry before sends and on focus/resume as well as a timer; server JWT
  validation remains authoritative. Timer cleanup must tolerate React StrictMode.
- Selected client-local logout clears provider token/user/private data immediately,
  invalidates generations, attempts and cached private results, and works offline.
  It makes no server call and promises no cross-tab/device logout, revocation,
  cancellation of admitted server work or rollback. Other tabs have independent
  memory sessions. This is not a newly approved backend logout mechanism.

**Reconciled 24 October:** ADR001 §11.3 records the Leader's personal 21 October
acceptance of precisely the copied-token/expiry, unlock, other-session and admitted-
request residual risks. This is existing evidence, not acceptance inferred from
this delegation. CT01/CT16 protected integration and real FE03/Auth verification
remain separate gates. Public catalogue and independent HTTP/type authoring do
not wait for whole-Issue completion.

### CT16.shell — router, providers and navigation

The 18 October proposal recommended **React Router declarative `BrowserRouter`**,
with nested `Routes`,
`Route` and `Outlet`, using existing routes/index.tsx, routePaths.ts and
ProtectedRoute.tsx. Its original status was dependency proposal only: adding
the package,
manifest/lockfile edits and implementation require **separate explicit Leader
authorization** and normal review. No dependency is added by this contract.

| Alternative | Assessment |
| --- | --- |
| `react-router` **7.18.4**, declarative mode | Preferred compatible pinned candidate: official registry metadata declares Node >=20 and React/ReactDOM >=18, covering current manifest Node `^22.12.0 || ^24.0.0` and React 19.3.0. No framework/server/Vite-router plugin needed. Recheck release/security metadata at dependency approval. |
| Current `react-router` 8.4.0 | Not selected: requires Node >=22.22.0, excluding part of the repository's declared Node range; do not silently raise its floor. |
| Hash routing | Avoids server fallback but adds fragment URLs despite existing history fallback capability; not preferred. |
| Handwritten History API router | No package, but recreates matching, nested guards, history subscriptions and redirect edge cases; higher maintenance burden. |
| Framework/data-router stack | Loaders/actions/build/server conventions exceed the selected shell need and overlap feature HTTP/session ownership. |

Sources checked 18 October: [official declarative installation](https://reactrouter.com/start/declarative/installation),
[BrowserRouter](https://reactrouter.com/api/declarative-routers/BrowserRouter),
[pinned 7.18.4 package metadata](https://registry.npmjs.org/react-router/7.18.4) and
[8.4.0 package metadata](https://registry.npmjs.org/react-router/8.4.0).
Current CI uses Node 22; the frontend image uses Node 22.23.3. Neither narrows
the approved manifest range automatically. Keep existing npm scripts unchanged.

Propose public `/login`, `/register`, `/courses` and `/courses/:courseId` routes;
`/` replaces to `/courses`. These are browser routes, not new REST operations.
Keep future protected feature routes gated by their own approved functionality;
no profile, recovery, enrolment, Instructor or Administrator operation is approved
here. Render unknown-route and wrong-role English states within existing route
composition rather than inventing new page files. Public catalogue does not
require login or a complete protected Course access contract.

App.tsx composes the existing theme/CssBaseline, one BrowserRouter, one
AuthProvider above route consumers, then existing Auth/Dashboard layouts as
appropriate. AuthProvider state/HTTP coordination does not require router APIs;
route consumers perform navigation so the provider is independently usable.
Keep StrictMode in main.tsx. Do not treat PR #74's showcase navigation/sample
identity as real routes or signed-in state; reuse its presentation without
carrying demo callbacks into real behavior. No extra provider framework is needed.

Unauthenticated protected navigation replaces to login and retains only a
validated same-origin internal destination in memory. Reject external URLs,
protocol-relative paths and API/asset destinations. After successful login,
replace to that still-permitted route, otherwise `/courses`; do not replay an
attempted mutation. Wrong-role users retain their session and see forbidden UI,
not another login redirect. No implicit Administrator role inheritance. Backend
403 remains authoritative even if the navigation role is stale.

Support back/forward and direct deep links; reload loses Auth memory. Nginx's
existing history fallback serves browser routes, while assets retain real 404s.
CT17 must route `/api` before the SPA fallback; HTML returned for an API path is
a protocol failure. Show loading per current operation, safe errors with explicit
retry for reads, and empty states distinct from failures. Clean up subscriptions,
abort work and suppress obsolete callbacks on navigation/unmount. Never hide a
backend denial by routing to a fabricated success view.

### Consumer handoffs, cross-contract gates and acceptance examples

| Slice / consumer | Independently usable output after its approval and separate implementation authorization | Exact remaining dependency |
| --- | --- | --- |
| FE03 HTTP/types | Existing httpClient.ts/apiTypes.ts; public calls and conforming doubles without Auth UI/router/backend availability | CT16.http review/integration and consumed CT18/OpenAPI contracts; no whole CT17 gate. |
| FE03 provider/Auth | AuthProvider/useAuth generation discipline; existing authApi.ts and selected forms/pages | Reuse ADR001 §11 technical determinations and existing §11.3 personal risk acceptance; retain protected-integration and actual verification gates. No profile/recovery contract invented. |
| FE03 shell | Existing App/routes/guard composition and compatible FE01 layouts | Separate pinned-router dependency/file-scope authorization, CT16.shell approval and reviewed presentation compatibility. |
| FE07 catalogue | Existing catalogueApi.ts proposal, existing catalogue types/hooks/pages, anonymous list/detail and CT21 paging | Resolve ADR002 filename correction explicitly before treating this feature placement reconciliation as effective. Search/filter, membership and display-name REST composition remain separately gated. |
| QA07 | Reproducible public/auth/error/race/deep-link cases against doubles during preparation | Actual browser → approved backend → PostgreSQL needed for integration evidence; no test runner or fictitious command selected. |
| CT16 base URL ↔ CT17 access | Compatible proposed same external HTTPS origin and `/api` proxy; absolute-origin mode still supported | CT17 owns actual approved origin/TLS/proxy. Its proposal is not effective authority for CT16; local absolute-origin mode remains independent. |
| CT16 session ↔ CT01/U01 | Memory-only approved minimum; compatible conditional completion above | U01 is backend-only. No browser identity-facade call, frontend security guarantee or CT03 protected approval follows. |
| CT17 runtime ↔ CT38 | Public build-time VITE_API_BASE_URL only; rebuild needed to change it | Actual external origins/CORS/secrets remain independently authorized runtime inputs; no secret VITE variable. |
| CT16 build ↔ CT17 serving | Existing Vite bundle and Nginx static/history behavior | Proposed API proxy/TLS implementation and external verification are CT17/DP obligations, not current functionality. |
| CT18 / CT20.I / QA / DP | Existing error/page conventions and bounded Auth/catalogue Interim scope | Actual deployed Auth, catalogue, lecturer access and independent evidence remain separate; local mastery is not browser/deployment proof. |

Acceptance examples: origin `http://localhost:8080/` plus `/courses` resolves to
`http://localhost:8080/api/courses`; `/api/` on an approved HTTPS browser origin
resolves once to that origin's `/api/courses`, never `/api/api/courses`. A public
catalogue request omits a stored bearer. A malformed 401 problem still triggers
current-session handling only for its authenticated request; public login 401
and old-generation 401 cannot clear another session. A 403 retains the session;
HTML 200 fails JSON decoding. Logout before login completion prevents session
restoration. Hidden catalogue detail remains indistinguishable 404. Changing
VITE_API_BASE_URL after build changes nothing until rebuild/redeployment.

Require Frontend @heizseung, Auth @duyduyit, relevant Course/API owners and Leader
review, required checks, last-push approval and protected integration. No review
is asserted completed here. The documentation CODEOWNER is the current PR-author
account; a compliant independent contributor-authored/pushed PR may be necessary.
Do not bypass protections. No application, browser, deployment or security
verification is claimed by this contract selection or documentation validation.

## CT16 delegated technical approval — 24 October 2026

### Authority, revision and effective-scope boundary

**CT16-2026-10-24-r1: DELEGATED_TECHNICAL_APPROVAL;
APPROVED_PENDING_PROTECTED_INTEGRATION.** The Project Leader
@chi-thanh-nguyen's explicit instruction **“CodeMastery — Bounded Technical
Approval Under Explicit Leader Delegation”**, dated 24 October 2026 (ICT),
authorizes Codex to investigate, select, reconcile and technically approve only
the specified CT16/CT17 slices. **Codex is the delegated technical decision-maker**
for this revision and approves the determinations below. This is actual technical
approval, not readiness advice or a personal Leader/GitHub review.

The same instruction expressly authorizes these documentation changes and the
exact ADR002 path correction. It does not authorize application implementation,
installation, manifest/lockfile edits, runtime acceptance or Issue closure.
New determinations take effect only after compliant protected integration of the
exact reviewed revision. Earlier approvals retain their original authority.

Fresh inspected remote `main`:
[`9d76732bab363a63d2d91b5bddbf0a72b66a6d91`](https://github.com/chi-thanh-nguyen/codemastery/commit/9d76732bab363a63d2d91b5bddbf0a72b66a6d91),
24 October; unchanged from the preceding assessment. Inspected consumers:
[FE03 #98](https://github.com/chi-thanh-nguyen/codemastery/pull/98), head
`2f459d47a9c398d55dc75c2b4c16622cdaa2adeb`, and
[FE07 #101](https://github.com/chi-thanh-nguyen/codemastery/pull/101), head
`8417205b0c5445a1ebace7b8a5f13847c50eddf5`. Both are open with no submitted review
at inspection. No new approval is inferred from their code or passing checks.
Main still has empty HTTP/Auth/shell scaffolds and no router dependency.

Preserve ADR002 CT16.minimum/CT18.minimum/CT38.core and CT21.catalogue; ADR001
§11's existing CT01 technical determinations, including memory sessions and the
separately recorded personal residual-risk acceptance, are not reopened. PRs
#91/#92 are merged, but their ADR status and available review/protection evidence
do not establish compliant protected integration; that provenance gap is retained,
not silently waived by this CT16 decision. FE01 #99/#100's bounded D1–D4 contracts
remain authoritative for presentation/formatting, not HTTP/Auth/router selection.

Publication preflight rechecked remote `main` at
[`0b2967226fcab946e0145160b1a18e21c30613d8`](https://github.com/chi-thanh-nguyen/codemastery/commit/0b2967226fcab946e0145160b1a18e21c30613d8)
after AD09 [PR #86](https://github.com/chi-thanh-nguyen/codemastery/pull/86)
merged on 24 October. The only delta from the initial baseline is its four
calculator/classifier implementation/test files; none changes these contract
sources or scopes. This task branch starts from that refreshed main revision.

### Approved determinations, interfaces and rationale

The detailed `CT16.http`, `CT16.auth` and `CT16.shell` conventions above are
approved only as bounded by this revision; historical references to additional
manual technical sign-off are resolved by this explicit delegation. Consumer
review, protected integration and separate implementation authority remain.

| Slice | Actual delegated determination | Boundary and rationale |
| --- | --- | --- |
| CT16.http | APPROVE native fetch, API-base/path normalization, explicit public/authenticated modes, safe response decoding/errors, caller cancellation and no automatic retries, as specified above | Existing httpClient.ts/apiTypes.ts; feature clients own DTO decoders. No parallel client/schema library or new REST contract. |
| CT16.auth | APPROVE concrete generation/attempt/expiry discipline implementing ADR001 §11, without changing its JWT/logout decisions | AuthProvider/useAuth remain memory-only and independently consumable; no server logout, refresh, storage persistence or identity/profile API. |
| CT16.shell | APPROVE declarative BrowserRouter/Routes/Route/Outlet, one router and AuthProvider above route consumers, existing layouts and guards | URL/history/deep-link behavior without a framework/data-router/server stack or extra provider technology. |
| Router dependency | APPROVE the exact production dependency `react-router` version **7.18.4**, without a semver range, for declarative browser routing only | No react-router-dom compatibility wrapper, router Vite plugin, SSR/RSC, loaders/actions or framework packages. No dependency is installed or edited in this task. |
| CT16 path correction | APPROVE correction of ADR002's mistaken courseApi.ts reference to `frontend/src/features/catalogue/api/catalogueApi.ts` | Verified canonical original tree, current inventory and existing file. Only that reference and correction provenance change; no rename, duplicate client or REST behavior change. |

The independently usable transport contract accepts an API-root-relative path,
operation method, explicit authentication mode, expected success status, runtime
DTO decoder, separate query/body and optional caller AbortSignal. Callers cannot
supply arbitrary URLs, bearer credentials or cookie/redirect overrides. Transport
and provider coordinate a captured session generation; feature code additionally
tracks current operation identity. Exact exported TypeScript declarations must
express these requirements in the existing files and receive consumer review;
no new file/public backend interface is approved.

Normalize only an explicit absolute origin or same-origin `/api` input as above;
reject namespace escape before fetch and after normalization. Authenticated
requests reject absent/expired sessions locally. All fetches omit credentials and
reject redirects; public register/login/catalogue/detail never attach bearer.
Successful payloads require the operation's status, JSON media type and decoder;
this selected scope has no 204 operation. Typed HTTP errors retain actual status
even if ProblemDetail decoding fails; network, protocol and cancellation remain
distinct. Status handling must not depend on trusting arbitrary error-body text.

A failed replacement login preserves an unrelated valid session. Starting an
attempt supersedes earlier attempts without silently authenticating or logging
out the current user. Only the latest live attempt in the still-current generation
may install a validated response; logout, expiry or session replacement invalidates
pending work. Cancelled/superseded work cannot publish private data or session
side effects. A public 401 never clears a session; an authenticated 401 clears
only its still-current captured session. 403/network/5xx preserve it. Conservative
expiry scheduling, pre-send/resume checks and StrictMode-safe cleanup implement
CT01; browser decoding never supplies trusted authorization.

Approved browser routes are exactly `/login`, `/register`, `/courses`,
`/courses/:courseId`, plus `/` replacing to `/courses` and an in-composition unknown-
route state. Guard conventions are reusable for later separately approved routes,
not authorization to expose new protected features. Validated internal return
navigation remains in memory and cannot become an external/API/asset redirect.
Reuse FE01 layouts and caller-owned formatting parameters; remove showcase/mock
identity behavior from real application composition during separately authorized
remediation. No FE01 contract or demo display becomes an Auth guarantee.

### Router compatibility, maintenance and security evidence

Checked official sources on 24 October 2026, without installing packages:

- [Versioned declarative documentation](https://reactrouter.com/7.18.4/start/declarative/installation)
  supports this browser-only composition. Existing React/Vite/MUI capabilities do
  not supply URL/history routing; the handwritten state-only #98 selection does
  not meet direct-link/back-forward requirements.
- [Official registry metadata](https://registry.npmjs.org/react-router/7.18.4)
  and version-7 dist-tag identify 7.18.4 as the latest stable v7 release, published
  15 September 2026, MIT, Node >=20 and React/ReactDOM >=18. This covers the
  unchanged Node `^22.12.0 || ^24.0.0` and React/ReactDOM 19.3.0 baseline.
- [Maintainer security policy](https://github.com/remix-run/react-router/blob/main/SECURITY.md)
  supports security updates for v7 and v8. All 22 published advisories returned by
  the [upstream advisory endpoint](https://api.github.com/repos/remix-run/react-router/security-advisories?per_page=100)
  were checked; their react-router affected ranges do not include 7.18.4.
  In particular, GHSA-qwww-vcr4-c8h2 is fixed in 7.18.2; the July redirect,
  manifest, hydration and RSC advisories are patched before this selected version.
- [8.5.0 metadata](https://registry.npmjs.org/react-router/8.5.0) requires
  Node >=22.22.0, excluding part of the repository's declared range. It is not
  selected; CI/image versions do not silently raise that range.

Alternatives retained: handwritten History API avoids a package but adds matching,
subscription and guard maintenance; hash routing avoids server fallback but is
unnecessary with the approved existing Nginx location; framework/data modes add
unneeded ownership/runtime conventions. Select declarative 7.18.4 for the smallest
compatible, maintained capability. This dated advisory review is not a guarantee
of absent vulnerabilities. Its transitive cookie/set-cookie-parser ranges must
be resolved into a reviewed lockfile and scanned in a separately authorized
implementation task; no future lockfile or production artifact is approved here.

### Acceptance, discrepancies and remaining gates

Contract acceptance cases, not executed tests: valid base/path resolves `/api`
once; malicious URL/path or redirect cannot receive bearer; public requests omit
bearer; malformed/HTML success is protocol failure; malformed 401 retains status
but clears only the current authenticated generation; public/stale 401 and 403
preserve unrelated sessions; delayed login after logout cannot install; expiry
prevents sends; back/forward/deep-link works; reload loses memory session; hidden
catalogue detail retains approved 404; FE01 formatting/layout behavior is preserved.

#98's unconditional global-401 callback, absent generation/expiry protection,
arbitrary fetch URL and state-only navigation require separately authorized
remediation and review; this decision does not retroactively authorize that code.
#101 inherits shared Auth/HTTP changes and additionally calls unapproved CT22
membership APIs. Membership/profile/recovery, CT21 display/search/authoring,
CT40/U07, production Adaptive/Assessment contracts and Final coverage remain
excluded. Current OpenAPI still has only the four register/login/list/detail
operations; it is unchanged.

Frontend @heizseung owns implementation; Auth @duyduyit, Course/API owners and
Leader review consumed boundaries. CT14 FE03/FE07 test additions are preparation
only in the accompanying task report, not approved here. Actual AU05/AU06/CO05
producers, real browser/REST/PostgreSQL checks, CT17 concrete runtime and independent
QA remain downstream gates; no whole-Issue closure is required merely to author
an independently usable approved slice after separate authorization.

All three decision files are sole-Leader CODEOWNER paths. Current main rules
require CODEOWNER/last-push approval, resolved threads, strict Backend/Frontend/
CodeQL checks and security scanning. A Draft PR published under the Leader's
account cannot self-satisfy that review. Compliant independent authorship/push
and eligible human review must be resolved without impersonation or changing
protections. No such human review or effective integration is claimed here.
Codex investigated and authored this decision documentation; no AI runtime service
is introduced. Neither Issue #19 nor any implementation/verification is completed
by this bounded technical approval.

## CT16 Interim technical coverage certification — 24 October 2026

**CT16-INTERIM-COVERAGE-2026-10-24-r1** certifies
**CT16_INTERIM_TECHNICAL_SCOPE_COMPLETE**, limited to technical decision coverage
for the selected Interim Minimum. **APPROVED_PENDING_PROTECTED_INTEGRATION**;
this is neither effective main integration nor whole-Issue, implementation, QA,
runtime or deployment acceptance.

Authority: the Project Leader's explicit **“CodeMastery — Complete Interim
Technical Contract Approvals (CT16, CT17, CT14)”**, supplied 24 October 2026 (ICT),
delegates independent technical investigation, determination and documentation.
Codex is the delegated technical authority for this coverage certification.
No new CT16 behavior or dependency approval is needed: preserve
**CT16-2026-10-24-r1** without reopening, duplicating or broadening it.

Fresh inspected main is
[`0b2967226fcab946e0145160b1a18e21c30613d8`](https://github.com/chi-thanh-nguyen/codemastery/commit/0b2967226fcab946e0145160b1a18e21c30613d8),
including merged AD09 #86. This follow-up is stacked on [Draft PR #102](https://github.com/chi-thanh-nguyen/codemastery/pull/102)
at **802ee19e38f22b35267bb51dfe04c8ee56b64acb**. Its original record and all other
bytes are preserved. #102 is still unmerged; its technical approval is not an
effective main contract. Integrate its exact reviewed revision compliantly before
this follow-up, then revalidate/review the follow-up against main. No #102 PR body,
head, base, review or Draft state is changed by this task.

| Interim-required decision | Exact existing technical authority | Remaining category, not a missing CT16 decision |
| --- | --- | --- |
| Register/login request and response binding | ADR001 §11.3, ADR002 CT01.core, OpenAPI; CT16-r1 transport/decoder contract above | AU05/AU06 and frontend implementation; actual security/PostgreSQL verification |
| Form validation and safe failures | CT01.core predicates; CT18.minimum; CT16.http safe plain-text field/general errors | UI must use Unicode code-point/UTF-8 limits and preserve exact password; a UTF-16 length shortcut does not amend CT01 |
| Memory-only JWT and offline logout | CT01 §11.3/§11.5 and CT16-r1 auth | No persistent storage, refresh or server logout; implementation and QA |
| Attempts, generations, expiry and 401/403 | CT16-r1 auth and ADR001 §11.5 | Current/stale/public distinctions and StrictMode/resume cleanup require actual implementation/tests |
| API base, namespace, bearer and public mode | CT16-r1 http | Actual approved origin/proxy is CT17 binding; local absolute-origin authoring is independent |
| Runtime decoding, typed failures/cancellation | CT16-r1 http with CT18.minimum and operation-specific OpenAPI | Exported declarations and code must conform; no decoder library/new endpoint is implied |
| Public list/detail DTOs and paging | ADR002 CT03.catalogue/CT21.catalogue; CT16-r1 public transport | CO05/FE07 producer artifacts and real data; no membership/display/search extension |
| Browser routes/history/refresh/deep links | CT16-r1 shell, exactly login/register/courses/detail/root redirect/unknown state | Existing Nginx needs CT17 API-before-SPA binding; no new route is approved |
| Providers, guards and presentation | CT16-r1 shell; FE01 D1–D4 | One declarative BrowserRouter and AuthProvider; preserve caller-owned formatting; frontend guards never authorize backend effects |
| Router/runtime compatibility | CT16-r1 exact react-router@7.18.4 decision and its dated official compatibility/security evidence | Dependency installation/resolved lockfile review remain separately authorized implementation |
| Loading, validation, unavailable and unauthorized UI | CT16.http status table, CT16.shell states and CT21 concealed 404 | Actual English/responsive UI and error/race tests; no fabricated success or role inheritance |
| Independently usable client artifacts and verification | CT16-r1 consumer handoffs; ADR002 CT14.interim/NC-EVIDENCE-MODE | HTTP/provider artifacts do not require whole FE03 completion; CT14 test inventory and QA07 execution are separate |

Current inspected consumer heads remain FE03 #98
`2f459d47a9c398d55dc75c2b4c16622cdaa2adeb` and FE07 #101
`8417205b0c5445a1ebace7b8a5f13847c50eddf5`, with no submitted reviews. #98's global
401 callback, missing attempt/generation/expiry safeguards, arbitrary fetch
boundary and state-only routing are implementation discrepancies. Its registration
form's string.length checks do not enforce CT01 Unicode code-point/UTF-8 predicates.
#101 adds unapproved CT22 membership calls and carries the shared FE03 code.
Remediation needs separate task authority; none is retrospectively legitimized.

No missing CT16-owned Interim technical decision was found. CT22 membership,
CT21 extensions, CT40, recovery/profile APIs, production Adaptive/Assessment and
Final coverage remain excluded. Consumed CT01 protected-integration provenance,
#102/#103 and this follow-up's eligible CODEOWNER/last-push reviews, required checks,
actual producers and independent runtime evidence remain explicit gates. A
Leader-authored sole-Leader-CODEOWNER PR cannot self-satisfy that review; another
contributor's non-owner review or push alone does not resolve it. No reviewer,
protection bypass or personal Leader review is asserted.
