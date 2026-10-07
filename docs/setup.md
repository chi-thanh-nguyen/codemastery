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
