# Development Setup

## Purpose and Current Scope

This document describes the current development setup contract, based on
[`application.yml`](../backend/src/main/resources/application.yml),
[`pom.xml`](../backend/pom.xml), and the Maven Wrapper. It covers environment
variable names, scopes, backend configuration loading, and backend validation
commands. It will expand as frontend, Docker Compose, and E2E configuration are
implemented.

The frontend package/Vite configuration, E2E package/Playwright configuration,
Dockerfiles, Compose configuration, CI workflow, and deployment script are
currently empty scaffolds. They do not provide executable setup or integration.

## Prerequisites

- A Java 25 LTS JDK for the Spring Boot 4.1.1 backend, as specified in `pom.xml`.
- The checked-in Maven Wrapper under `backend/`; a global Maven installation is
  not required. Its configured Maven distribution and dependencies must be
  available or downloadable when running backend validation.
- PostgreSQL for backend datasource/persistence work and MinIO through its
  S3-compatible API for object-storage work. Repository-supported service startup
  and provisioning steps are pending.
- Docker for PostgreSQL integration tests using the Testcontainers dependencies
  and Maven Failsafe configuration in `pom.xml`. No Docker version is specified.

React, TypeScript, Vite, Material UI, React Flow, Docker Compose, and Playwright
are approved technologies whose executable setup is pending. The empty frontend
and E2E package files define no Node/npm version requirements or commands.

## Environment File

The root [`.env.example`](../.env.example) is a catalogue of backend configuration
variables and infrastructure/frontend/E2E names reserved for future integration.
Infrastructure variables and backend-consumer variables have distinct scopes.

When creating a local configuration file, copy the template from the repository
root:

```bash
cp .env.example .env
```

Replace the required backend secret placeholders with local values before use.
Uncomment optional settings only when an override is needed. Infrastructure
placeholders do not start or provision services, and reserved frontend/E2E
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
| Frontend | `VITE_API_BASE_URL` | Reserved for the approved frontend stack; no implemented configuration consumer or exact URL yet. |
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

## Backend Validation

Use the Maven Wrapper from `backend/`:

```bash
cd backend
./mvnw test
```

`pom.xml` configures Maven Surefire to exclude the `integration` package from
this phase. For broader verification, the configured Maven Failsafe integration
phase is available through:

```bash
cd backend
./mvnw verify
```

The build declares Testcontainers PostgreSQL dependencies and Failsafe test
selection. Executing tests that use Testcontainers requires Docker. These
commands are supported by the current build configuration; they do not establish
that test implementations or a complete application runtime are available or
validated. Frontend, E2E, Compose, CI, and deployment commands will be documented
when their configuration is implemented.
