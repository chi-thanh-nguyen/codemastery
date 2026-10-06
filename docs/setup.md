# Development Setup

## Purpose and Current Scope

This document describes the current development setup contract, based on
[`application.yml`](../backend/src/main/resources/application.yml),
[`pom.xml`](../backend/pom.xml), the Maven Wrapper, and
[`frontend/package.json`](../frontend/package.json). It covers environment
variable names, scopes, backend/frontend configuration loading, and foundation
setup and validation commands. It will expand as Docker Compose and E2E
configuration are implemented.

The frontend build and minimal React/Material UI bootstrap are implemented.
The backend integration-test foundation and initial CI workflow are implemented;
feature tests, frontend features, and API integration remain pending. Standalone
multi-stage Docker image builds and frontend Nginx static serving configuration
are implemented, with non-root runtimes. E2E package/Playwright configuration,
Compose configuration, and the deployment script remain empty scaffolds; there
is no complete application-stack startup, E2E, or deployment integration.

## Prerequisites

- A Java 25 LTS JDK for the Spring Boot 4.1.1 backend, as specified in `pom.xml`.
- The checked-in Maven Wrapper under `backend/`; a global Maven installation is
  not required. Its configured Maven distribution and dependencies must be
  available or downloadable when running backend validation.
- PostgreSQL for backend datasource/persistence work and MinIO through its
  S3-compatible API for object-storage work. Repository-supported service startup
  and provisioning steps are pending.
- Docker is required for standalone image builds and container smoke checks,
  and when concrete PostgreSQL Testcontainers integration tests execute. The
  abstract harness alone does not start a container during Maven validation.
  No Docker version is specified.
- Node.js satisfying `^22.12.0 || ^24.0.0` for the frontend, as declared in
  `frontend/package.json`, with npm as the package manager. No npm engine
  requirement is declared.

React, TypeScript, Vite, Material UI, and React Flow dependencies are pinned in
the frontend manifest and tracked `frontend/package-lock.json`. React Flow
feature usage remains pending. Docker Compose and Playwright setup is pending;
the empty E2E package files define no executable commands.

## Environment File

The root [`.env.example`](../.env.example) is a catalogue of backend configuration
variables, optional frontend configuration, and infrastructure/E2E names reserved
for future integration. Infrastructure variables and backend-consumer variables
have distinct scopes.

When creating a local configuration file, copy the template from the repository
root:

```bash
cp .env.example .env
```

Replace the required backend secret placeholders with local values before use.
Uncomment optional settings only when an override is needed. Infrastructure
placeholders do not start or provision services. `VITE_API_BASE_URL` remains
optional and commented because no frontend API consumer is implemented; E2E
entries remain commented until their configuration is implemented.

Real `.env` files must remain untracked. The current `.gitignore` excludes `.env`
and `.env.*` while retaining `.env.example`. Never commit secrets, actual storage
keys, or real user/demo/lecturer credentials. Never expose secrets through
`VITE_*` variables.

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

Spring reads these extensionless files as Java properties. Use unquoted
`KEY=value` lines, without shell `export` statements. The file is configuration
input rather than a shell script. Process environment variables take precedence
over file values. Optional imports do not provide defaults for required secrets.
There is currently no implemented Compose variable injection.

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
exact API URL remains pending. Never put backend secrets or other sensitive
values in `VITE_*` variables.

## Variable Scopes

The approved Option A contract uses these names:

| Scope | Variables | Current consumer/status |
| --- | --- | --- |
| PostgreSQL initialization | `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` | Intended for PostgreSQL service initialization; runtime/Compose wiring is pending. |
| Backend datasource | `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`, `DB_POOL_SIZE` | Referenced by backend datasource configuration. |
| MinIO administration | `MINIO_ROOT_USER`, `MINIO_ROOT_PASSWORD` | Administrative/root credentials; runtime/Compose wiring is pending. |
| Backend object storage | `STORAGE_ENDPOINT`, `STORAGE_REGION`, `STORAGE_BUCKET`, `STORAGE_ACCESS_KEY`, `STORAGE_SECRET_KEY`, `STORAGE_PATH_STYLE_ACCESS` | Referenced by backend storage configuration. |
| Backend security/CORS | `JWT_SECRET`, `JWT_EXPIRATION`, `PASSWORD_RESET_TOKEN_TTL`, `CORS_ALLOWED_ORIGINS` | Referenced by backend security configuration. |
| Backend runtime/operations | `SERVER_PORT`, `MAX_UPLOAD_SIZE`, `LOG_LEVEL_ROOT`, `LOG_LEVEL_APP`, `SCHEDULING_ENABLED`, `ATTEMPT_AUTO_SUBMIT_DELAY`, `DEADLINE_REMINDER_DELAY`, `DEADLINE_REMINDER_LEAD_TIME` | Referenced by backend runtime configuration. |
| Spring profile selection | `SPRING_PROFILES_ACTIVE` | Supported through Spring profile selection, rather than a `${...}` placeholder in `application.yml`. |
| Frontend | `VITE_API_BASE_URL` | Optional public build-time configuration with root environment loading and Vite typing; no API consumer or exact URL yet. |
| E2E | `E2E_BASE_URL`, `E2E_USERNAME`, `E2E_PASSWORD` | Pending Playwright/E2E configuration; accounts and authentication flow are undefined. |

PostgreSQL initialization credentials and backend datasource credentials are
conceptually distinct and are not implicitly interchangeable. The backend does
not substitute `POSTGRES_*` for `DB_*`. A disposable local environment may use
matching values only if its runtime/Compose configuration explicitly chooses
that mapping; this contract defines no application-user provisioning behavior.

MinIO root/admin credentials are also conceptually separate from backend
application storage credentials. The backend uses `STORAGE_ACCESS_KEY` and
`STORAGE_SECRET_KEY` without implicit substitution from `MINIO_ROOT_*`. This
contract defines no storage-user provisioning, bucket policy, or object-key rules.

### Required Inputs and Current Defaults

The following backend placeholders have no defaults in `application.yml`:
`DB_PASSWORD`, `JWT_SECRET`, `STORAGE_ACCESS_KEY`, and `STORAGE_SECRET_KEY`.
The template provides safe placeholders for these required secret inputs.

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

A full backend runtime needs PostgreSQL and MinIO for the approved persistence
and object-storage responsibilities. `infrastructure/docker-compose.yml` is
empty, so there is no repository-supported infrastructure startup command yet.
Supplying environment values alone does not provision databases, users, or
buckets or establish a complete runnable system.

Service names, container hostnames, published ports, real credentials,
PostgreSQL/MinIO application-user provisioning, bucket policy, and production
settings remain pending. Frontend deployment strategy and E2E account lifecycle
also remain pending.

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

Compose remains unimplemented, and PostgreSQL/MinIO runtime services, environment
mappings, published ports, volumes, startup/health dependencies, and database/
storage provisioning are not established. Backend runtime integration cannot yet
be demonstrated without approved dependency configuration. These images do not
form a complete local application stack or establish production deployment.
Dockerfile health checks remain deferred to Batch 7.

## Initial CI

[The GitHub Actions workflow](../.github/workflows/ci.yml) now defines separate
Backend and Frontend jobs on `ubuntu-24.04`, triggered by pull requests targeting
`main`, pushes to `main`, and manual dispatch. Backend uses Temurin 25 and runs
`./mvnw verify` from `backend/`; frontend uses Node 22 and runs `npm ci` followed
by `npm run build` from `frontend/`. Build already includes frontend typechecking.
Both jobs cache package-manager downloads using the committed build inputs.

This workflow configuration does not establish successful remote CI execution or
backend feature-test coverage. E2E, Compose, and deployment commands remain
pending until their configuration is implemented.
