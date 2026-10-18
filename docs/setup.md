# Development Setup

## Purpose and Current Scope

This document describes the current development setup contract, based on
[`application.yml`](../backend/src/main/resources/application.yml),
[`pom.xml`](../backend/pom.xml), the Maven Wrapper,
[`frontend/package.json`](../frontend/package.json), and
[`docker-compose.yml`](../infrastructure/docker-compose.yml). It covers local
Compose startup/provisioning, variable scopes, configuration loading, and
foundation validation. Production deployment and E2E remain pending.

The frontend build and minimal React/Material UI bootstrap are implemented.
The backend integration-test foundation and initial CI workflow are implemented;
feature tests, frontend features, and API integration remain pending. Standalone
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
feature usage remains pending. Playwright setup is pending; the empty E2E package
files define no executable commands.

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
| frontend | http://localhost:5173 | Nginx on 8080; minimal built frontend |
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
| `npm run build` | Run type-checking and build production assets in `frontend/dist/`. |
| `npm run preview` | Preview an existing production build locally; run after building. |

Keep `frontend/package-lock.json` tracked with the manifest. `node_modules/`
and `frontend/dist/` are generated, ignored local state. The package declares
no lint or frontend test script. The minimal bootstrap uses default Material UI
styling; routing, authentication UI, API integration, feature pages, and the
React Flow Mastery Map remain unimplemented. Build success does not establish
feature-test coverage or deployment readiness.

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

Backend feature test files are still empty, so these commands currently compile
the abstract harness without executing PostgreSQL or proving Spring/database
integration at runtime. Docker and access to the test image become necessary
when concrete Testcontainers tests run; Compose is not required for those tests.
See [the testing contract](testing.md) for lifecycle and isolation details.

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

## CT16 frontend contract proposal — pending approval

### Authority, baseline and reconciliation

Prepared on **18 October 2026 (ICT)** under the Leader's scoped instruction to
Codex to investigate and select CT16 contracts. This delegates technical
selection, not a separate manual Leader review, dependency authorization,
implementation authorization or security-risk acceptance. New selections below
remain **PROPOSED_NOT_EFFECTIVE** until affected-owner review, explicit Leader
sign-off and protected integration. Effective ADR002 clauses remain authoritative.

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
| Other ADR001 §11 completion selections integrated through #82; [PR #83](https://github.com/chi-thanh-nguyen/codemastery/pull/83) remains open at [`47af5c9`](https://github.com/chi-thanh-nguyen/codemastery/commit/47af5c9a3128333ecfe5cea56590814919beff71) | PROPOSED_NOT_EFFECTIVE outside separately verified approvals; merge is not proof of every reserved sign-off | Select compatible client behavior conditionally below; logout residual risk remains Leader-reserved. Do not consume CT03 protected predicates as effective. |
| CT03.catalogue / CT21.catalogue, ADR002 | APPROVED_AND_CONSISTENT: anonymous published-only bounded DTOs and pagination | REUSE for FE07 public reads; display-name REST composition, search/filter and membership remain separately gated. |
| HTTP, types, AuthProvider, routes, guards and feature API files on baseline | UNRESOLVED implementation: files are empty scaffolds | No executable consumer or runtime verification claimed. No router dependency exists. |

No CT01, CT18, CT21, CT22, CT38 or CT40 policy is superseded here. The sole
supersession proposal above concerns a mistaken CT16 filename, not behavior.

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

The following completion behavior is selected **conditionally**, compatible with
ADR001 §11's pending proposal. It does not approve CT01 logout security policy:

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

**Leader-reserved gate:** CT01 must explicitly accept copied-token residual
validity through expiry while the account is active (including possible reuse
after unlock), or approve another design. Neither #82's merge, CT01.identity's
18 October sign-off nor this CT16 selection supplies that acceptance. Full
CT16.auth/logout and FE03 Auth completion remain pending; reusable HTTP/type
work and public catalogue do not wait for this whole gate.

### CT16.shell — router, providers and navigation

Recommend **React Router declarative `BrowserRouter`**, with nested `Routes`,
`Route` and `Outlet`, using existing routes/index.tsx, routePaths.ts and
ProtectedRoute.tsx. This is a dependency proposal only: adding the package,
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
| FE03 provider/Auth | AuthProvider/useAuth generation discipline; existing authApi.ts and selected forms/pages | CT01 consumed Auth API/security semantics; explicit logout-risk resolution for completion. No profile/recovery contract invented. |
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
