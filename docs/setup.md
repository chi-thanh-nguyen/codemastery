# Development Setup

## CO09 protected material consumer — 25 November 2026

PR #291 implements text/canonical external-video authoring and protected PDF material
create/title/replace/publication/download through the existing SH05 FileStorage adapter.
The backend keeps MinIO private and streams authorized attachments; it exposes no
object key, filename or storage URL in material JSON metadata. PDF downloads carry
attachment disposition with UTF-8 filename, exact length, no-store and nosniff.

Existing MAX_UPLOAD_SIZE configures both multipart/request limits and the per-file
check (1..2147483647 bytes); the 20MB fallback remains local configuration, not policy.
Multipart resolution is lazy. Exact material URL rules authenticate first; CT03 and
type/ancestry admission precede body/part access. The ordered form filter bypasses only
these routes so a wrong-media form PUT cannot parse before admission. Rejected unread
uploads use Connection: close; an ingress/client can observe a transport reset, which
is an unknown upload outcome, never a reason for automatic retry. Cross-origin
responses expose Content-Disposition. JSON callers and unrelated URL rules remain intact.

Storage configuration validates when the actual S3 bean is consumed; relational-only
operations do not invent storage credentials. Upload write transactions are owned by
the operation, begin after storage finishes and recheck under the Course root. Unknown
commit outcome retains both objects; no background cleanup or key reuse is introduced.
Download admission/metadata use a read-only REPEATABLE READ snapshot, ending before
storage opens and before synchronous streaming. Checked resource closure is preserved.

Shared HTTP transport now supports raw FormData and authenticated Blob downloads,
including unknown upload outcomes and filename* parsing. The production material editor
and Learner file widget are separate downstream work; no route/UI integration is claimed.
Runtime privileges require the consumer-specific r13 deployment matrix. Actual VM
values, explicit upload limit, tmpfs budget, ingress framing margin, private identity,
operational qualification and any applicable risk acceptance remain operator/Leader gates.
See [testing evidence](testing.md#co09-protected-materials-qualification--25-november-2026).

## Purpose and Current Scope

This document describes the current development setup contract, based on
[`application.yml`](../backend/src/main/resources/application.yml),
[`pom.xml`](../backend/pom.xml), the Maven Wrapper,
[`frontend/package.json`](../frontend/package.json), and
[`docker-compose.yml`](../infrastructure/docker-compose.yml). It covers local
Compose startup/provisioning, variable scopes, configuration loading, and
local build and test commands. The one-VM release deployment is documented
separately in [deployment.md](deployment.md); test suites and their evidence are
in [testing.md](testing.md).

The sections from [Prerequisites](#prerequisites) through
[CI](#ci) describe the current repository. The later dated sections, from
[Authorized Interim configuration targets](#authorized-interim-configuration-targets-implementation-pending)
onward, are decision and implementation records. They keep the wording of their
date and are not rewritten when the implementation changes.

### Implemented capabilities on `main`

Status as inspected on 31 October 2026 at `main`
`614f3260a2df640dcfa47c166e4a5325f56bd1a7`. Implemented code and passing tests are
not acceptance: QA01, QA02, QA07, QA13, DP01–DP03 and the other verification
Issues remain open.

| Area | Implemented in the repository | Not implemented |
| --- | --- | --- |
| Auth | Registration and login (`POST /api/auth/register`, `/api/auth/login`), BCrypt password storage, JWT bearer authentication with per-request account-status and role checks (registration creates Learners only) and locked-account rejection; password recovery (AU07); ADR014 hybrid sessions: PostgreSQL refresh sessions with rotating opaque refresh tokens in the HttpOnly `__Host-cm_refresh` cookie (`POST /api/auth/refresh`), server logout (`POST /api/auth/logout`) and session-bound access JWTs | Account administration (CT12) |
| Course | Public published-course catalogue with search, prerequisite filter and pagination (`GET /api/courses`); published course detail (`GET /api/courses/{courseId}`); Learner membership (`GET`/`PUT`/`DELETE /api/courses/{courseId}/membership`) and My Courses (`GET /api/me/courses`) | Course authoring, lesson content delivery, lesson progress |
| Security | Anonymous `GET /actuator/health` (nondetailed, for the private probe); every other route, including the rest of `/actuator/**`, `/v3/api-docs` and Swagger UI, is denied by Spring Security | — |
| Adaptive | Pure mastery calculator and state classifier (no database, events or API) | Skill graph, mastery persistence, routing, recommendations |
| Assessment, Interaction, Admin | — | Source files are empty scaffolds |
| Object storage | Local MinIO and its least-privilege initializer | Backend storage adapter and file delivery (CT09) |
| Frontend | Router with the public landing page at `/`, `/courses`, `/courses/:courseId`, `/login`, `/register`, Learner-only `/home` and `/my-courses`, authenticated `/profile` and the design preview at `/preview` (sample data for every designed screen, including the React Flow Mastery Map and Instructor/Administrator screens); shared HTTP client; memory-only access token with ADR014 session restoration, renewal and cross-tab coordination | Production routes and API integration for lessons, assessments, Mastery Map, notifications, Instructor and Administrator screens (their contracts are pending) |
| Sample data | SD01 Users and SD02 Courses in `sample-data/seed/01-…` and `02-…`, with a disposable-target loader procedure in [sample-data/README.md](../sample-data/README.md) | Seeds 03–05 are empty; no object is uploaded to MinIO |
| Runtime | Local Docker Compose stack (this guide) and the one-VM release tooling `infrastructure/vm/deploy.sh`, `infrastructure/vm/docker-compose.vm.yml` and the frontend `release` image target ([deployment.md](deployment.md)) | No deployment has been performed |
| E2E | Playwright Test suite in `e2e/` with a disposable Docker stack and deterministic fixture, run locally and by the non-required `CI E2E` workflow ([testing.md](testing.md#ci)) | A required E2E check; `e2e/tests/adaptive-flow.spec.ts` |

Exact request and response shapes are in [the OpenAPI document](api/openapi.yaml);
approved behavior is in [ADR002](decisions/002-interim-delegated-contracts.md) and
[ADR001](decisions/001-modular-monolith-module-boundaries.md).

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
  and for `./mvnw verify`: its concrete integration tests start a PostgreSQL
  Testcontainer (see [Backend Validation](#backend-validation)). `./mvnw test`
  does not need Docker. No exact Docker version is specified. Host Java/Node are
  needed only for host development commands, not for the Compose multi-stage
  image builds.
- For host frontend development, Node.js satisfying `^22.12.0 || ^24.0.0`, as declared in
  `frontend/package.json`, with npm as the package manager. No npm engine
  requirement is declared.

React, TypeScript, Vite, Material UI, and React Flow dependencies are pinned in
the frontend manifest and tracked `frontend/package-lock.json`. React Flow
feature usage remains pending. Vitest `5.0.5` and jsdom `27.4.0` are development-only
frontend test dependencies. Playwright Test 1.63.0 is pinned in `e2e/package.json`;
`npm ci` in `e2e/` installs it for both the E2E suite and the optional frontend
browser harness, and `npx playwright install chromium firefox webkit` installs the
browsers it expects. The E2E suite also needs Docker with a local daemon (a Unix-socket
context, which includes Docker Desktop's local VM; remote `tcp://`/`ssh://` contexts are
refused) and Python 3 on `PATH` as `python3`, standard library only, for the lifecycle
coordinator `e2e/fixtures/lifecycle.py`. It keeps generated, non-secret run state in
`~/.codemastery-e2e/` (outside the checkout; see
[the run lifecycle](testing.md#browser-harness-and-e2e-suite)). Supported platforms: macOS
(native `kinfo_proc` process start times) and Linux/Ubuntu (`/proc` start ticks with boot and
PID-namespace identity); elsewhere automatic process recovery is unsupported and processes
are preserved. Which hosts are actually qualified is recorded in
[the FE24 testing record](testing.md#fe24-ct34-r2-lifecycle-implementation--13-november-2026).

## CT17 Final binding status — 9 November 2026

**10 November update:** [ADR009 §9](decisions/009-ct17-final-runtime-recovery.md#9-continuation-attestation-delegated-selections-and-executed-qualification)
is the current r2 record. CP-033/034 are technically approved, with the Leader's
exact existing VM resource and credit-only financial binding plus
executed synthetic qualification. Host/platform/operator facts in the supplied
report are now Leader-attested, not stale solely by date. Select future root-only
`/etc/codemastery/runtime/.env`, immutable root-owned checkout under
`/opt/codemastery/releases/<full-source-sha>`, and the existing TLS directory.
These are technical conventions, not existing configured paths or new sudo grants.
The Leader supplied dated **$88 remaining / 31 October 2027 expiration** evidence
from Education Hub on 10 November, approximately 11:05 ICT; see
[ADR009 section 10](decisions/009-ct17-final-runtime-recovery.md#10-post-pr231-integration-and-azure-evidence--10-november-2026).
This supersedes only the former missing balance/expiry status. Fresh credit/cost
feasibility through Final still requires qualification before execution.
The selected acme.sh TLS-ALPN renewal timer, real certificate for codemastery.app,
secrets/custody, Final artifacts, resource capacity and deployment still require
separately authorized implementation/inspection. No setup command or configuration
file is changed. The paragraph below retains the r1 evidence state.

ADR008, including CP-030 r2, is merged through #231; ADR009 r2 remains pending
#233 independent review and protected integration. Batch 4 is 8/8 technically
approved, without implementation, production admission or deployment authorization.

[ADR009](decisions/009-ct17-final-runtime-recovery.md) records the Leader-selected
existing Azure VM and intended **https://codemastery.app** origin, with Leader
operator/secret/key/recovery custody. This is not permission to change a runtime
origin, access grant, key, .env or public listener. The current host resource ID,
disk/capacity/credit, checkout/environment custody, TLS/renewal, grants and exact
running release still need non-secret operational evidence. Historical paths and
preview artifacts are not adopted as verified Final bindings. Existing P6/B3,
configuration variables, topology and reproduction commands remain unchanged.
CP-033/034 remain partially approved; no recovery mechanism or deployment is
implemented by this documentation follow-up.

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
user/password, a separate application storage user/secret, and a JWT secret of
exactly 64 hexadecimal characters (32 random bytes, for example from the
`openssl rand -hex 32` command below). The backend's JWT provider rejects any
other secret format and any `JWT_EXPIRATION` other than `PT15M`, so the backend
does not start with them. Use non-production local credentials. Uncomment optional
settings only when needed; Compose forwards only its explicit allowlist.
`VITE_API_BASE_URL` remains optional public build-time configuration. E2E entries
stay commented: the E2E suite reads them from the shell, never from `.env`.

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

The shared HTTP client (`src/shared/api/httpClient.ts`) sends every API request to
this value. It accepts only `/api` (same origin, used by the VM `release` image),
or an `https://` origin, or an `http://` origin on `localhost`, `127.0.0.1` or
`[::1]`, with no path. The application builds without the variable, but every API
request then fails with "The API address is not configured correctly." There is
no fallback URL. The standalone Docker build has no default; Compose supplies
`http://localhost:8080`. Never put backend secrets or other sensitive values in
`VITE_*` variables.

## Variable Scopes

The approved Option A contract uses these names:

| Scope | Variables | Current consumer/status |
| --- | --- | --- |
| PostgreSQL initialization | `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` | Consumed by the local PostgreSQL service when initializing an empty data volume. |
| Backend datasource | `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`, `DB_POOL_SIZE` | Referenced by backend datasource configuration. |
| MinIO administration | `MINIO_ROOT_USER`, `MINIO_ROOT_PASSWORD` | Consumed by MinIO and its administrative initializer, not the backend. |
| Backend object storage | `STORAGE_ENDPOINT`, `STORAGE_REGION`, `STORAGE_BUCKET`, `STORAGE_ACCESS_KEY`, `STORAGE_SECRET_KEY`, `STORAGE_PATH_STYLE_ACCESS` | Referenced by backend storage configuration. |
| Backend security/CORS | `JWT_SECRET`, `JWT_EXPIRATION`, `CORS_ALLOWED_ORIGINS` | Required/validated by backend security configuration. |
| Password recovery (optional) | `SMTP_HOST`, `SMTP_USERNAME`, `SMTP_PASSWORD`, `PASSWORD_RESET_EMAIL_FROM`, `PASSWORD_RESET_FRONTEND_ORIGIN`, `PASSWORD_RESET_TOKEN_TTL` | Read by the backend for AU07 delivery. If any value is missing, a placeholder or invalid, recovery stays inactive and the rest of the backend starts normally. SMTP uses port 587 with required STARTTLS; TTL defaults to and must equal `PT30M`. SMTP authentication is mandatory: a server that does not offer AUTH after STARTTLS receives no message. AUTH mechanisms are fixed to `LOGIN PLAIN` over verified TLS; SASL is disabled. NTLM, DIGEST-MD5, XOAUTH2 and unknown-only offers fail closed. Angus SMTP/wire/socket and authentication-helper JUL diagnostics are source-filtered JVM-wide; Session debug is disabled with a discarded output stream. Synthetic DEBUG/TRACE coverage includes actual Spring Boot startup, encoded authentication material and a test-only NTLM override; see the SMTP coverage in `docs/testing.md`. Recovery failures retain only aggregate metrics. Local Compose and the VM release do not pass these values. No real credentials are supplied. Names and validation: `CT38-PASSWORD-RESET-2026-11-13-r1` ([ADR006 §10.5.1](decisions/006-batch-02-contract-approvals.md#1051-ct38-password-reset-2026-11-13-r1--ct38featurepassword-reset)). |
| Backend runtime/operations | `SERVER_PORT`, `MAX_UPLOAD_SIZE`, `LOG_LEVEL_ROOT`, `LOG_LEVEL_APP`, `SCHEDULING_ENABLED`, `ATTEMPT_AUTO_SUBMIT_DELAY`, `DEADLINE_REMINDER_DELAY`, `DEADLINE_REMINDER_LEAD_TIME` | Referenced by backend runtime configuration. |
| Spring profile selection | `SPRING_PROFILES_ACTIVE` | Supported through Spring profile selection, rather than a `${...}` placeholder in `application.yml`. |
| Frontend | `VITE_API_BASE_URL` | Public build-time API address read by the shared HTTP client; Compose passes a build ARG defaulting to http://localhost:8080. Required for any API request. |
| VM deployment only | `SPRING_FLYWAY_URL`, `SPRING_FLYWAY_USER`, `SPRING_FLYWAY_PASSWORD` | Separate Flyway migrator identity required by `deploy.sh` and the VM override. Local Compose ignores them; see [deployment.md](deployment.md#current-status-and-reproduction-summary). |
| E2E | `E2E_BASE_URL`, `E2E_API_URL`, `E2E_USERNAME`, `E2E_PASSWORD` | Read from the shell by `e2e/` only, to target an already running environment (`E2E_USERNAME`/`E2E_PASSWORD`: an existing synthetic Instructor). Unset, the suite starts a disposable stack ([testing.md](testing.md#browser-harness-and-e2e-suite)). Not loaded from `.env`. |

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
addresses. Auth and Course persistence are implemented through JPA and native
SQL repositories. The backend object-storage adapter is not implemented: no
backend Java code reads the `storage` properties that the `STORAGE_*` values feed.

## Local Docker Compose Runtime

Run all commands from the repository root. First copy the template as shown above
and replace every required placeholder. The Compose project is `codemastery`;
there are five services on its default network, with no source bind mounts,
custom networks, fixed container names, or explicit restart policies.

This file is the **local development** runtime only. It publishes every port on
loopback and builds the images locally. The one-VM release reuses it as a base,
but only through `infrastructure/vm/deploy.sh` and the
`infrastructure/vm/docker-compose.vm.yml` override, which remove the local ports
and builds and add HTTPS ingress. Do not start the override by hand; see
[deployment.md](deployment.md#current-status-and-reproduction-summary).

The runtime foundation was validated locally before Auth was integrated: both
image builds, PostgreSQL authentication/Flyway checksums, idempotent MinIO
provisioning and application-key object operations, backend Actuator UP, frontend
serving, and named-volume persistence across `down`/`up`. The Actuator result no
longer applies; see [Initialization and Startup Ordering](#initialization-and-startup-ordering).
On 30 October 2026 the
[sample-data procedure](../sample-data/README.md) was also run against an isolated
Compose project: Flyway applied V001–V006, both seeds loaded, sign-in succeeded
and the catalogue hid the draft Course
([record](testing.md#sd01sd02-seed-verification--30-october-2026)). None of these
local runs is QA07, QA13 or DP02 acceptance.

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

After startup, the stack has no users or courses. To use the application:

1. Load the synthetic dataset into the local database by following
   [sample-data/README.md](../sample-data/README.md) (Steps 1–5). It includes a
   database check and a `curl` catalogue check.
2. Open http://localhost:5173/courses. Sign in at `/login` with
   `learner@example.test` or `instructor@example.test` and the synthetic password
   you chose for the hash. As the Learner, join a Course from its detail page and
   open `/my-courses`.

The frontend keeps the access token in memory only. Signing in also sets the
HttpOnly refresh cookie, so reloading the page restores the session until you sign
out, stay idle for 24 hours or reach the 7-day limit
([ADR014](decisions/014-hybrid-refresh-session-authentication.md)). With the local
HTTP origins the cookie is `cm_refresh`; over HTTPS it is `__Host-cm_refresh`.

### Services and Local URLs

All published ports bind only to `127.0.0.1`.

| Service | Local address | Container address / purpose |
| --- | --- | --- |
| frontend | http://localhost:5173 | Nginx on 8080; the built single-page application, calling the backend at the build-time `VITE_API_BASE_URL` |
| backend | http://localhost:8080 | Spring Boot on 8080; the REST API under `/api`. `/actuator/health` answers nondetailed `UP`/`DOWN` (see below) |
| postgres | localhost:5432 | `postgres:5432`; local relational database |
| minio | http://localhost:9000 | `minio:9000`; S3-compatible API |
| MinIO Console | http://localhost:9001 | MinIO administration; use local root credentials |

PostgreSQL is built locally from `infrastructure/postgres/Dockerfile`
(`postgres:17.11-alpine3.24` with `su-exec` replacing `gosu`; see
[ADR003](decisions/003-postgresql-alpine-su-exec-image.md)) for LOCAL
development only. MinIO uses
`ghcr.io/golithus/minio:RELEASE.2025-10-15T17-29-55Z`, a third-party build of tagged
upstream Community source. The initializer uses
`ghcr.io/golithus/mc:RELEASE.2025-08-13T08-35-41Z`. Both MinIO images are pinned to
verified immutable multi-platform index digests in Compose and support amd64 and
arm64. These choices establish no production database/storage infrastructure.

### Existing PostgreSQL Volumes

The PostgreSQL image starts only on an empty data volume or on one it initialized
itself (it writes `codemastery-cluster-libc` into the data directory after
`initdb`). A volume created by the earlier `postgres:17.11-bookworm` image, or by
an earlier build of this image, is refused with exit code 78 and the message
`codemastery-postgres: refusing to start`. Nothing in the volume is changed.
glibc and Alpine's musl order text differently, so such a cluster's indexes would
otherwise return inconsistent results without any error.

Keep the old volume and migrate it by logical backup and restore. Use your `.env`
values for `<POSTGRES_USER>` and `<POSTGRES_DB>`; no port is published.

```bash
# 1. Stop the stack, keeping every volume (never use `down -v`).
docker compose --env-file .env -f infrastructure/docker-compose.yml down

# 2. Back up with the image that initialized the volume, then stop it.
docker run -d --name codemastery-pg-bookworm \
  -v codemastery_postgres-data:/var/lib/postgresql/data postgres:17.11-bookworm
docker exec codemastery-pg-bookworm pg_isready -U <POSTGRES_USER> -d <POSTGRES_DB>  # repeat until accepting
docker exec codemastery-pg-bookworm \
  pg_dump -U <POSTGRES_USER> -d <POSTGRES_DB> -Fc > codemastery-postgres.dump
docker stop codemastery-pg-bookworm && docker rm codemastery-pg-bookworm
pg_restore --list codemastery-postgres.dump | head  # or run it inside any postgres image

# 3. Keep a byte-for-byte copy of the old volume.
docker volume create codemastery_postgres-data-bookworm-backup
docker run --rm -v codemastery_postgres-data:/from:ro \
  -v codemastery_postgres-data-bookworm-backup:/to alpine:3.24 cp -a /from/. /to/

# 4. Only after checking the backup and the copy, remove the original yourself.
docker volume rm codemastery_postgres-data

# 5. Initialize a fresh volume with this image and restore into it.
docker compose --env-file .env -f infrastructure/docker-compose.yml up -d --wait postgres
docker compose --env-file .env -f infrastructure/docker-compose.yml exec -T postgres \
  pg_restore -U <POSTGRES_USER> -d <POSTGRES_DB> --no-owner --exit-on-error < codemastery-postgres.dump
```

Restoring rebuilds every index under musl. Text ordering that relies on the
database's default collation may change; the catalogue's explicit `COLLATE C`
ordering does not. Delete the dump and the backup volume only when you no longer
need them. The VM release is out of scope here: an existing VM volume is never
migrated by an image swap (see [deployment.md](deployment.md#dp01-postgresql-alpinesu-exec-image-hardening--2-november-2026)).

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
forwarded. Compose has no backend health check. Spring Security permits only
the public Auth and catalogue operations and anonymous `GET /actuator/health`,
which answers 200
`{"groups":["liveness","readiness"],"status":"UP"}` or 503 `DOWN` without details
([deployment.md](deployment.md#dp01-private-health-probe-remediation--1-november-2026)).
Health alone proves no feature. To check a local
backend, run the Compose `ps -a` and `logs backend` subcommands with the same
`--env-file`/`-f` options as above, and call a public operation, for example
`curl -s -o /dev/null -w '%{http_code}\n' http://localhost:8080/api/courses`,
which returns `200`. Future application retry/resilience remains necessary;
startup ordering does not replace it.

Flyway remains enabled and normally applies V001–V007 on first backend startup,
then validates their checksums on later starts. These are implemented schema
migrations, not empty placeholders. Never edit an applied migration or run repair
to hide a mismatch; subsequent changes require NEW Flyway migrations. Hibernate
retains `ddl-auto=validate`, so startup checks the existing Auth, Course and
Adaptive entity mappings against the migrated schema. The empty Assessment,
Interaction and Admin modules have no entities to check. Startup does not create
sample users/courses; to load
the synthetic local/test dataset into a disposable database, follow
[sample-data/README.md](../sample-data/README.md).

Frontend startup is independent of backend. Its health check uses the existing
Nginx image's `wget` to probe `/`. Compose passes public `VITE_API_BASE_URL` as a
build ARG, defaulting to `http://localhost:8080`; browser code cannot resolve
`http://backend:8080`. Changing this value requires rebuilding the frontend image.
No runtime Nginx environment substitution exists. The browser calls the backend
directly at that origin, so the backend's CORS configuration must admit the
frontend origin. Compose sets `CORS_ALLOWED_ORIGINS=http://localhost:5173` as a
literal value in the backend service of `infrastructure/docker-compose.yml`; it is
not forwarded from `.env`, so a `CORS_ALLOWED_ORIGINS` entry in `.env` affects
only a backend started on the host. Serving the containerised frontend from
another origin requires a different value in that Compose file.

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
This is a local development workflow. The VM release deployment, its persistent
state and its recovery prerequisites are documented in [deployment.md](deployment.md).

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
| `npm test` | Run the Vitest/jsdom suite: utilities and shared presentation, HTTP client, Auth provider and router, catalogue, detail, My courses, assessment and course-management components, Mastery Map layout and the design preview (15 files). |
| `npm run build` | Run type-checking and build production assets in `frontend/dist/`. |
| `npm run preview` | Preview an existing production build locally; run after building. |
| `npm run test:browser [-- --engine all] [--url <frontend-url>] [--out <evidence-dir>]` | Optional real-browser presentation checks; builds and serves the app itself unless `--url` is given ([details](testing.md#browser-harness-and-e2e-suite)). |

Keep `frontend/package-lock.json` tracked with the manifest. `node_modules/`
and `frontend/dist/` are generated, ignored local state. No lint script is declared.
For browser prerequisites and outputs see
[Browser harness and E2E suite](testing.md#browser-harness-and-e2e-suite); for the
timezone cases see [the FE01 testing record](testing.md#bounded-fe01-decisions-and-regression-coverage).
The required Frontend CI job runs `npm ci`, `npm test`
and `npm run build`; it does not run `test:browser` or the E2E suite. Passing unit/component tests
and a successful build do not establish frontend/backend integration in a real
browser or deployment readiness.

The MUI theme and `presentationTokens` live in `frontend/src/app/theme/theme.ts`
and implement the Light and Dark schemes of the root `DESIGN.md` specification (“The Lit Path”; first revision
`DESIGN-2026-11-06-r1`, tracked in Git from r6; current merged revision `DESIGN-2026-11-19-r12`,
PR #252, 19 November 2026). An ink frame `#0A0B0F` holds the top bar
(80 px, 64 px below 600 px) and a `#F6F6F7` canvas that rises under the current
navigation item; asides use `#EEEEF0`, tiles are white at a 24 px radius, buttons
are pills and icon actions 44 px circles. Text is ink `#15171C`/`#50545C`; the logo
blue `#0866F2` (and `#0652C4` for small text) marks interaction and focus. Pastel
sky, mint, butter, lilac and rose families carry Learning, Mastered/Completed,
Weak/warnings, recommendations and errors, always with a text label and glyph;
Unknown is a dashed outline. Typography uses exactly one UI face, Google Sans Flex
4.005, with JetBrains Mono 2.211 for code and Poppins ExtraBold 800 only for the
"CodeMastery" wordmark; all three are self-hosted variable or single-weight WOFF2
files in `src/app/theme/` (Fontsource 5.3.x builds of the Google Fonts releases,
SIL Open Font License 1.1, licence texts `public/google-sans-flex-OFL.txt`,
`public/jetbrains-mono-OFL.txt`, `public/poppins-OFL.txt`). The UI and code faces
ship Latin, Latin Extended and Vietnamese subsets selected by `unicode-range`. The
theme exports `fontStack`, `codeFontStack` and `brandFontStack`. No font CDN is used.
The logo artwork is `public/favicon.svg` (its palette was tuned for the ink frame on
7 November 2026); `public/favicon.ico` is generated from it. The top-bar lockup uses a
40 px mark from 600 px, 36 px below and 32 px below 390 px (`presentationTokens.brandMark`,
fixed steps), with the wordmark at 0.625 × the mark. Course tiles show a course mark
(`src/shared/components/CourseMark.tsx`): the official full-colour logo of the
programming language that the course's own title, or else its description, names
(exactly one language); otherwise a Deep Logo Blue (`#0652C4`) code glyph. Every mark
uses the same neutral `#F6F6F7` container with a hairline (48 px on tiles, 32 px in
Learner home rows). The course API has no language field, so no logo is inferred
beyond that text. The signed-in account avatar (top bar and profile) is solid Deep
Logo Blue with a white initial; no gradient is used. The design preview at `/preview` has no preview chrome: each screen is
opened by link, `/preview?screen=<id>`, and the index lists them all. The logo files are the unaltered official artwork:
`course-logo-python.svg` (python.org `python-logo-only.svg`, with a `viewBox` added so
it can scale; "Python" and the Python logos are trademarks of the Python Software
Foundation) and `course-logo-typescript.svg` (`ts-logo-256.svg` from the TypeScript
branding asset pack; TypeScript is a trademark of Microsoft Corporation). Both are
used only to name the language a course teaches.
`PresentationIcon.tsx` supplies original outline SVGs without an icon dependency.

**Colour schemes (FE34 #255, PR #261).** `DESIGN.md` r12 specifies three theme
preferences, System (the default), Light and Dark. FE34 implements them in the existing
files only:

- `theme.ts` defines one MUI theme with two colour schemes (`colorSchemes.light`,
  `colorSchemes.dark`, `cssVariables` with the selector `[data-color-scheme="%s"]`). The
  semantic roles of r12 are emitted as CSS variables (`--mui-palette-roles-*`) and exported
  as `roles`; components read roles, never the raw `color` palette. The light values equal
  the values on `main` before FE34. `@media print` re-declares the light variables and sets
  `color-scheme: light !important`, which overrides the inline `color-scheme` that the
  first-paint script and the preference state set, so native controls and system colours
  print light too. `prefers-contrast: more` and `forced-colors: active` follow r12's
  fallback table; under forced colours, floating surfaces (menus, popovers, dialogs, the
  drawer) also get a 1 px `CanvasText` outline, because their shadow edge is removed.
- `App.tsx` holds the one preference state above the router and the session. It is stored
  only under `localStorage` key `codemastery.theme` (`system`, `light` or `dark`); a missing,
  empty, unknown or unreadable value means System and an invalid value is left until the
  next choice; storage that throws keeps the choice in memory. System follows the
  `prefers-color-scheme` change event; other tabs follow the `storage` event. No request,
  profile field or backend state is involved, and sign-in, sign-out or another account
  never changes it.
- `index.html` sets `data-color-scheme` and `color-scheme` on the root before the
  application bundle runs, from the stored preference and
  `matchMedia('(prefers-color-scheme: dark)')`; the root colour is the frame `#0A0B0F` in
  both schemes and `theme-color` stays `#0A0B0F`. The inline script is static so that a
  future Content Security Policy can allow-list it: its SHA-256 source is
  `'sha256-gyoHhEeXJZ2cyULAqJ8wHhi0sXEzbmvDVoB9FkJ+UYc='` (recompute it whenever the
  script changes). No CSP is configured today.
- The Theme control (`ThemeControl`, exported from `AppHeader.tsx`) renders once per
  shell: the workspace top bar from 900 px (after page utilities such as the notification
  button and immediately before the account menu, or before Sign in for visitors), the
  mobile drawer footer below 900 px (on-chrome segmented control), the landing header
  (Catalogue → Theme → Sign in → Create account), the split auth layout, and the quiz and
  diagnostic Focus bar (`QuizAttemptPage.tsx`). It is never in the account menu.
  `AppHeader` takes page utilities (`utilities`, `DashboardLayout` `headerUtilities`)
  before the Theme button and session actions (`actions`) after it.
  Escape closes its menu and returns focus to the button; Tab and Shift+Tab also close it
  and return focus to the button first, so the tab sequence continues from the button
  (the menu is rendered at the end of the document). The "Theme" tooltip appears after
  400 ms on hover and at once on keyboard focus (`:focus-visible`); it is hidden while the
  menu is open, including after a choice (the menu stays open), and is reset whenever the
  menu opens or closes. After a close it is suppressed per source: for the pointer still
  resting on the button until the pointer enters again, and for the focus returned to the
  button until focus leaves it, so a later Tab back to the button shows it at once. The
  menu state is read from a ref, so a hover timer started before the menu opened cannot
  show it over the menu. Escape dismisses the tooltip until the pointer genuinely enters
  again or focus leaves the button; MUI Tooltip's own delayed focus open is ignored,
  because the button's `onFocus` already handles keyboard focus, so no pending timer
  reopens a dismissed tooltip (N-01). That ignored MUI timer still sets MUI's module-wide
  tooltip hysteresis when it fires, so this tooltip sets `enterNextDelay={400}` locally and every
  pointer hover takes the specified 400 ms (N-04); the global Tooltip delays are unchanged. The
  menu's popup container carries
  no `tooltip` role. Menu items (all menus) hover and focus on `bg.raisedHover`; only the
  selected item uses `bg.selected`.
- Glyphs in buttons and in `StatusPill` (pills, mastery, recommendation and other tags)
  use `currentColor`, the label's colour; guidance and attention glyphs keep their family
  accent (`DESIGN.md` D13-1).
- `/preview` follows the stored preference until a preview-local choice is made; that
  choice is held in memory for the document, never written to storage, never sent to
  other tabs and gone on reload or when the document leaves `/preview`.

While the ADR014 restore runs, `/` shows the landing header (lockup and Theme button, no
sign-in actions) with "Restoring your session…" (`LandingRestoringView`), and protected
routes show the same line inside the workspace frame, so every restore state keeps its
Theme control. Below 900 px the workspace drawer also leaves out "Create account" until
the restore has finished, as the header leaves out its sign-in actions (F2). Developer evidence is recorded in
[testing.md](testing.md#fe34-theme-system-verification--20-november-2026); independent
Light, Dark and System acceptance stays with QA07 #63.

**FE03 remediation on PR #261 (20 November 2026, Leader authorization).**

- *A13 preview isolation, fixed.* `AuthProvider` reads `restoreOnLoad` once at mount, and
  `App.tsx` decides once per document whether it was opened at `/preview`. A document opened
  there never restores by itself: not on load, not when another tab signs in, not after a
  failed sign-in and not on focus, page show or wake-up. A sign-out broadcast changes
  nothing there unless the document has a session or a pending sign-in to end. The isolation
  ends only when the person signs in explicitly in that document (after leaving the preview
  for the sign-in page); the document then follows the normal ADR014 lifecycle. Before the
  fix, a sign-in in another tab, or a theme change after leaving `/preview` in the same
  document, sent `POST /api/auth/refresh` from it.
- *AUD-01 preview URL aliases, fixed (21 November 2026).* "Opened at `/preview`" is decided
  with React Router's own `matchRoutes` against the same `PREVIEW_PATH` the route uses
  (`isPreviewPath` in `routes/index.tsx`), so it accepts exactly what renders the preview:
  any letter case (`/PREVIEW`), trailing slashes and percent-encoded characters
  (`/pre%76iew`). A malformed encoding is kept as typed by the router and renders "Page not
  found", so it restores normally, as does `/preview/<anything>`. The earlier pattern
  `^/preview(/|$)` missed the aliases (they restored a session) and treated
  `/preview/<anything>` as the preview although it shows "Page not found".
- *A14 shared-transport capability, implemented in `shared/api/httpClient.ts`.* The explicit
  no-content mode of `CT16-B1-NO-CONTENT-2026-11-09-r1` (`responseMode: 'no-content'`,
  `expectedStatus: 204`, no decoder, `Promise<void>`), a `GET`-only
  `responseMode: 'json-with-metadata'` that returns `{ body, etag }` with the `ETag` read only
  after the status, media type, decoding and current-generation checks, and safe 412/428
  titles and local messages. Existing JSON callers and the ADR014 sign-out 204 are unchanged.
  Still required before FE18 acceptance: FE18's own client, hook and UI binding, and the
  backend CORS change that allows `If-Match` and exposes `ETag` for approved origins
  (`SecurityConfig` allows only `Authorization` and `Content-Type` today); neither is part
  of this change.

`src/app/App.tsx` mounts one `BrowserRouter` and `AuthProvider` around the routes
in `src/routes/index.tsx`, listed in
[Implemented capabilities on `main`](#implemented-capabilities-on-main). `/` is
the public landing page (S25) for visitors without a session; its example Mastery
Map is a fixed illustration loaded in its own chunk, and a signed-in visitor is
sent to their start page instead (`/home` for Learners, `/teach` for Instructors,
`/courses` for Administrators until Platform overview is delivered; the logo uses
the same rule). Navigation shows Catalogue for everyone, Home (`/home`) and My
courses for Learners, and Teaching for Instructors. Profile and Sign out are in
the account menu. `/home` lists the Learner's active courses from `GET /api/me/courses`
only. After a session ends without signing out (a refused renewal or restore, a
current-session 401 or a sign-out in another tab; see
[ADR014 hybrid authentication session](#adr014-hybrid-authentication-session--16-november-2026)),
the sign-in page says “Your session ended. Sign in again to continue.” once.
After sign-in a Learner lands on `/home`, an Instructor on `/teach`, and an
Administrator on `/courses` until `/admin` is delivered. The one-use `/profile`
return (CT16-PROFILE P1) takes precedence and is consumed once. These are the
7 November 2026 Learner fallback and conditional CT16.routes start rules; P1's
return target, transient memory and explicit-logout rules are unchanged. `/preview` lazily loads the design
preview from `src/app/preview/` in its own chunk: every designed screen for the
Learner, Instructor and Administrator workspaces and the proposed public screens,
with synthetic sample data. Since 7 November 2026 it shows no preview notice or
switcher: screens open by link and the account menu keeps “Preview index” and
“Reset sample data”. It is designed never to call the API, read the session or write browser
storage, and it adds no route: screens change through `?screen=` query parameters. A
document opened at `/preview` also stays out of other tabs' sign-ins and sign-outs (the
`DESIGN.md` A13 defect, fixed by PR #261). Screens marked Preview only in `DESIGN.md` exist only
there, with sample data; their contracts are approved, but they are not built for
production routes or API integration.

**Visual inspiration (6 November 2026 redesign):** [E-learning Platform – Learning
Plan Dashboard — Nixtio](https://dribbble.com/shots/23956256). The redesign
follows its visual language (ink frame, canvas, pastel tiles); its composition,
screens and assets are not copied, the logo is CodeMastery's own, and no affiliation
is implied (see the `DESIGN.md` fidelity contract).
Known AI assistance: Anthropic's Claude Code (Claude Opus 5.5) wrote the
`DESIGN.md` revisions (r1 on 6 November 2026 through r6 on 8 November 2026) and implemented and verified the redesign and its 7 November
2026 typography, logo-palette and navigation-indicator refinement, with the locally
installed Impeccable skill for design review and Playwright CLI for browser
inspection. Claude Code also wrote revisions r7 (8 November 2026) through r12
(19 November 2026); r10–r12 respond to independent reviews of PR #252 by OpenAI
Codex, as the `DESIGN.md` Document Control records. These revisions are specification
only: the colour schemes, and the screen behavior they specify beyond the Live screens,
are not implemented.

**Earlier FE01 visual inspiration (historical):** [LMS Admin Dashboard UI/UX Design for EdTech SaaS Platforms —
Rakibul Ism / Panze UX Design Studio](https://dribbble.com/shots/27183809-LMS-Admin-Dashboard-UI-UX-Design-for-EdTech-SaaS-Platforms).
The FE01 implementation used general dashboard principles and was independently
developed; no proprietary assets or complete reference composition were reused,
and no affiliation is implied. Known AI assistance: OpenAI Codex for FE01
implementation, test/verification preparation and that documentation reconciliation.
These disclosures do not describe an AI-powered product feature.

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
it starts a singleton container, built from `infrastructure/postgres/Dockerfile`
through Testcontainers' `ImageFromDockerfile`, and supplies datasource
properties dynamically. This image is for integration testing only; it does
not select the deployed PostgreSQL version. No local database credentials are
required by this wiring. The profile only disables the scheduling setting;
Flyway and Hibernate validation retain the normal application configuration.

Concrete integration tests for Auth, Course catalogue and membership, Course
persistence, the sample-data seeds and the shared REST infrastructure now exist.
All except `SharedRestInfrastructureIntegrationTest` initialize the PostgreSQL
harness, so `./mvnw verify` needs a running Docker Engine able to build
`infrastructure/postgres/Dockerfile`; Compose is not required.
`SampleDataSeedIntegrationTest` also runs the image's own `psql` 17 inside that
container, so no host `psql` is needed. `./mvnw test` runs
only unit tests and needs no Docker. See
[testing.md](testing.md#test-layers-and-evidence) for the suite inventory, what
each suite establishes and the latest recorded results. No harness, profile or
runtime configuration is changed by that inventory.

## Standalone Container Builds

Build both images from the repository root:

```bash
docker build -t codemastery-backend:batch6 ./backend
docker build -t codemastery-frontend:batch6 ./frontend
```

These are local validation tags, not a registry or deployment policy. Docker
must be running, and the base images, Maven project dependencies, and npm
packages must be available or downloadable. Host Java, Maven, Node, and npm are
not required for these image builds.

### Backend Image

The build context is `backend/`. The builder uses the pinned
`eclipse-temurin:25.0.5_8-jdk-noble` with Apache Maven 3.9.16 copied from a
digest-pinned Docker Official Image. Before executing Maven, Docker checks the
distribution tree against a SHA-512 derived from Apache's verified archive. The
Wrapper properties file must end in a newline (LF or CRLF) and contain one
case-sensitive `distributionUrl=` declaration with the exact reviewed Apache ZIP
URL. Surrounding value whitespace is allowed. The bootstrap
layer has no network access. A fresh builder needs the pinned OCI layers from
Docker Hub, and Maven still contacts Maven Central for project dependencies and
plugins. `mvn package -DskipTests` runs once, compiling the test sources without
executing them. Maven Resolver retries a project download that returns HTTP 429
or 503 up to three times. Without a `Retry-After` header, or with a value it
cannot parse, the three waits are 15, 30 and 45 seconds. A number of seconds or
an HTTP date sets the wait when it is 60 seconds or less. A past date or a
negative number retries immediately. A requested wait above 60 seconds ends
that request with its error. These limits apply to each request; there is no
overall build deadline.

Host-side development and the Backend CI job use `./mvnw verify` (Windows:
`mvnw.cmd verify`). Both Wrapper scripts require both archive pins in
`.mvn/wrapper/maven-wrapper.properties`: exactly one `distributionSha256Sum=`
line and one `distributionTarGzSha256Sum=` line, each with 64 lowercase
hexadecimal characters. Spaces around the value are allowed. A missing, blank,
malformed or repeated pin stops the Wrapper before it downloads Maven or
starts an existing installation. So does another spelling of a pin's name:
different case, leading space, space before `=`, or `:` as the separator. An
environment variable cannot supply a pin. These Wrappers therefore do not
support upstream's unpinned mode or Maven Daemon (mvnd) distributions.

The Wrapper checks the selected archive's SHA-256 before extraction: the ZIP,
or on Unix without `unzip` the TAR.GZ. A mirror set with `MVNW_REPOURL` must
serve the same bytes. A cold Unix installation needs `sha256sum` or `shasum`.
The archive pin is part of the installation cache identity, so an installation
made without that pin is never reused. The directory name does not prove that
the extracted files are intact: a warm invocation reuses them from the user's
Maven directory, which must remain under that user's control. If the
installation is damaged, remove that specific installation directory and run the
Wrapper again to download and verify it. When updating Maven, verify Apache's
signatures before updating both Wrapper archive pins and the Docker distribution
reference and tree digest. The Backend CI job first runs
`infrastructure/vm/tests/test-maven-wrapper-pins.sh`, which checks these rules
offline against a synthetic distribution
([record](testing.md#pr-293-host-wrapper-pin-remediation-ir293-sec-01--26-november-2026)).
See [distribution provenance and pin updates](deployment.md#maven-bootstrap-reliability--25-november-2026).

The runtime uses `eclipse-temurin:25.0.5_8-jre-noble` and contains only the
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

This local image is the Dockerfile's default (last) target. A separate
`release` target exists only for the one-VM HTTPS deployment: it builds the
bundle with `VITE_API_BASE_URL=/api`, uses the non-slim
`nginxinc/nginx-unprivileged` Alpine variant pinned by digest to its Nginx 1.30.6
index, with Nginx raised to 1.30.7 from nginx.org and Alpine security updates
applied (both targets; see
[DP01 release image security remediation](deployment.md#dp01-release-image-security-remediation--23-november-2026)),
serves TLS on `8080`,
proxies `/api`, and bakes an HTTPS healthcheck. It requires the public
`PUBLIC_ORIGIN` build argument and a mounted certificate directory; see
[DP01 HTTPS ingress integration](deployment.md#dp01-https-ingress-integration--31-october-2026).
Local builds and Compose are unaffected.

### Optional Frontend Build Configuration

`VITE_API_BASE_URL` is an optional public Docker build argument, made available
to Vite during `npm run build` when supplied. There is no default URL. The image
builds without the argument, but its API requests then fail (see
[Current Frontend Loading Behavior](#current-frontend-loading-behavior)). The root `.env` is outside both service
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
checks do not prove the unimplemented backend S3 adapter, feature behavior,
browser API integration or the VM release deployment. Compose has PostgreSQL
and frontend health checks; the backend image and the default local frontend
target add no HEALTHCHECK. Only the frontend `release` target bakes one.

## CI

Three GitHub Actions workflows, [CI Backend](../.github/workflows/ci-backend.yml),
[CI Frontend](../.github/workflows/ci-frontend.yml) and [CI VM](../.github/workflows/ci-vm.yml),
run on pull requests targeting `main`, pushes to `main`, and manual dispatch, on
`ubuntu-24.04`.
Backend uses Temurin 25 and runs `./mvnw verify` from `backend/`, including the
Testcontainers integration tests. Frontend uses Node 22 and runs `npm ci`,
`npm test` and `npm run build` from `frontend/`. Both jobs cache package-manager
downloads using the committed build inputs. Six further jobs, in CI VM, test
`infrastructure/vm/deploy.sh`, the VM override, the frontend `release` image and
the MinIO initializer image against real containers on the runner; they do not
deploy anywhere. The separate, non-required `CI E2E` workflow runs the Playwright suite on
all five projects against a disposable stack owned by the run. CodeQL code
scanning runs through GitHub's default setup, which has no workflow file in this
repository.

The job list, the required checks and the latest recorded results are in
[testing.md](testing.md#ci). A workflow definition alone does not establish a
successful run. CI does not start the full local Compose stack; the E2E job builds its
own disposable stack instead.

## Authorized Interim configuration targets (implementation pending)

> **Dated record.** This heading and the sections below keep the wording of
> their date. For example, the JWT, CORS and datasource settings named here are
> now consumed by the implemented Auth and Course code. The current setup is
> described in the sections above.

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

> **Superseded in part (16 November 2026):** the memory-only reload, no-cookie,
> no-refresh and client-local logout clauses of this CT16 record are superseded by
> `CT16-SESSION-2026-11-16-r1` and `AUTH-SESSION-2026-11-16-r1`
> ([ADR014 §§7, 11](decisions/014-hybrid-refresh-session-authentication.md#11-supersession-register));
> the rest stays effective. The text below is unchanged history.


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

> **Superseded in part (16 November 2026):** the memory-only reload, no-cookie,
> no-refresh and client-local logout clauses of this CT16 record are superseded by
> `CT16-SESSION-2026-11-16-r1` and `AUTH-SESSION-2026-11-16-r1`
> ([ADR014 §§7, 11](decisions/014-hybrid-refresh-session-authentication.md#11-supersession-register));
> the rest stays effective. The text below is unchanged history.


### Authority, revision and effective-scope boundary

**CT16-2026-10-24-r1: DELEGATED_TECHNICAL_APPROVAL;
[LEADER_APPROVED](decisions/README.md#approval-status-labels).** The Project Leader
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

> **Bounded later extension:** [ADR005 §16.2](decisions/005-batch-01-contract-approvals.md#162-r2-bounded-ct16-no-content-transport-extension)
> adds no-content success only for the five Batch 1 204 operations. The original
> JSON-only scope below remains unchanged for its existing callers.

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
for the selected Interim Minimum. **LEADER_APPROVED**;
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

## CT16 consolidation and bounded Final approvals — 25 October 2026

> **Superseded in part (16 November 2026):** the memory-only reload, no-cookie,
> no-refresh and client-local logout clauses of this CT16 record are superseded by
> `CT16-SESSION-2026-11-16-r1` and `AUTH-SESSION-2026-11-16-r1`
> ([ADR014 §§7, 11](decisions/014-hybrid-refresh-session-authentication.md#11-supersession-register));
> the rest stays effective. The text below is unchanged history.


### Authority, provenance and canonical review path

**CT16-FINAL-2026-10-25-r1: DELEGATED_TECHNICAL_APPROVAL;
LEADER_APPROVED.** The Leader's explicit instruction
**“CodeMastery — Consolidate Contract Approval PRs and Complete Eligible Final
Technical Approvals”**, supplied 25 October 2026 (ICT), authorizes consolidation
and actual bounded Final technical approval. Codex is the delegated technical
decision-maker and **approves only F1 and F2 below**. No personal Leader review,
risk acceptance, implementation or verification is asserted.

Fresh main is **0b2967226fcab946e0145160b1a18e21c30613d8**, including AD09 #86.
Canonical [PR #102](https://github.com/chi-thanh-nguyen/codemastery/pull/102) now
preserves its original **802ee19e38f22b35267bb51dfe04c8ee56b64acb** content and
all unique [PR #104](https://github.com/chi-thanh-nguyen/codemastery/pull/104)
content at **5bf70b45bfafd85bdafcb40cd3742f6d40fb02cd**. The preceding 24 October
stack/dependency descriptions are retained historical process records; the
25 October authorized consolidation replaces that review path with #102 targeting
main. #104 may close as superseded only after independent preservation readback.
No approved decision is backdated, duplicated or silently superseded.

CT16-2026-10-24-r1, CT16-INTERIM-COVERAGE-2026-10-24-r1 and the exact ADR002 path
correction remain intact. CT16_INTERIM_TECHNICAL_SCOPE_COMPLETE still means
technical coverage only. Existing CT01/CT18/CT21/CT38 and FE01 obligations retain
their authority; the Interim-only CT06/CT24/CT36 grants do not become Final policy.

### F1 — feature state ownership and cross-feature invalidation

**Checkpoint CT16.final-state: APPROVE** the reusable composition boundary for
future *separately approved* feature operations, without approving those operations.
Keep transient feature/query state in its owning feature's existing hooks/pages;
share it only at the closest composing React owner that actually needs it.
AuthProvider owns authentication, theme owns presentation policy, and routes
compose features. Do not turn AuthProvider or a shared component into an enrolment,
attempt, mastery or administration data store. No global store/cache/event-bus
library, cross-feature internal import, new provider/file or duplicate client.

When approved feature operations interact, the composing caller explicitly owns
revalidation of affected reads through the existing approved feature client/hook.
Publish only a decoded, operation-defined outcome; do not infer a transaction's
completion or start dependent business effects from a generic HTTP success alone.
Do not optimistically manufacture server-owned membership/progress/mastery state,
replay a mutation after transport loss, or invent a cross-module event. On an
uncertain outcome, retain a safe pending/error state and re-read only an approved
read operation when the feature contract permits it. No rollback/retry semantics
are supplied to an unapproved feature.

Use operation identity plus the existing session-generation/cancellation rules;
private feature data cannot outlive its owning session. Route unmount invalidates
owned callbacks; same-session superseded reads cannot overwrite the current view.
Independent public catalogue state is not fabricated as private state. Navigation
availability is derived from the approved current role/feature policy, never used
as backend authorization; concrete routes/menu permissions await their owner.

Alternatives: a global cache/state framework adds dependency/invalidation policy
without current need; ad-hoc cross-feature subscriptions duplicate business state.
Select React ownership and explicit composing calls already supported by the stack.
Acceptance: a late response cannot restore discarded private state; one feature
cannot mutate another's owned state implicitly; successful rendering never implies
unapproved enrolment/scoring/admin effects. Interface review checks existing
hooks/client inputs/outputs, not invented backend DTOs or a new route registry.

### F2 — accessible and responsive application composition

**Checkpoint CT16.final-accessible-composition: APPROVE** application-level
integration conventions using existing React/MUI/FE01 components: semantic main/
navigation/form structure, named controls and links, keyboard-operable actions,
visible focus, programmatic field-error associations and announced loading/error
state. A new route moves focus to its meaningful content heading; an ordinary
async update must not steal focus from ongoing input. Failed form submission
exposes and focuses an actionable invalid field or error summary. Existing dialog
focus trapping/return behavior is preserved, not recreated in page code.

Do not communicate authorization/error/mastery state by colour alone; retain
readable English labels and actual unavailable/forbidden states. Compose the
existing responsive layouts without concealing required actions/content on mobile
or placing fixed overlays over focused content. Reuse current theme and FE01
caller-owned formatting; no new global breakpoint/locale/timezone/precision policy,
WCAG conformance claim, icon system or accessibility dependency is selected.
An inaccessible component integration must be corrected rather than treated as
compliant merely because MUI is installed.

Alternatives: mouse-only callbacks and visual-only errors fail the interaction
boundary; a second component/audit framework is unnecessary for these conventions.
Acceptance review observes keyboard traversal, meaningful names/status, focus after
navigation/error/dialog, mobile content access and no stale update focus theft.
Real browser/accessibility verification remains separate; existing CT14/QA07 and
feature-specific approved tests supply evidence only within their actual scope.

Technical evidence: [React state ownership](https://react.dev/learn/sharing-state-between-components),
[MUI composition](https://mui.com/material-ui/guides/composition/), actual existing
app/providers/routes/layouts/theme inventory and official requirements §6.
Sources inform the selections; this explicit delegation supplies their authority.
No installed package version is changed or claimed to have passed an audit here.

### Entire retained Final decision inventory

| Pending/retained subject | Classification | Exact prerequisite or disposition |
| --- | --- | --- |
| HTTP JSON addressing/errors/decoding/cancellation, generation/expiry, router and reusable guards | E — ALREADY APPROVED | Preserve 24 October clauses; no redundant approval or broader Auth policy |
| State ownership/invalidation and accessibility composition | A — READY FOR DELEGATED TECHNICAL APPROVAL | F1/F2 selected and technically approved above, pending integration |
| Concrete feature APIs, routes, menu destinations and role/resource policies | D — DEFER UNTIL CONSUMER OR INTEGRATION READINESS | CT21 authoring/display/search, CT22, Assessment/Adaptive/Interaction/Admin owning operations must define actual payloads/access/workflows first |
| Multipart, download/stream/binary errors, upload limits and storage delivery | D — DEFER UNTIL CONSUMER OR INTEGRATION READINESS | CT09/owning feature contract and actual response/request boundaries; current JSON contract does not approve these transports |
| Recovery/profile/session evolution for additional workflows | D — DEFER UNTIL CONSUMER OR INTEGRATION READINESS | CT02/owning account workflow and CT01 security authority; no new endpoint, refresh, server logout or persistent storage |
| New state/client/router dependency or runtime change | D — DEFER UNTIL CONSUMER OR INTEGRATION READINESS | No current need or compatible/security-reviewed candidate beyond existing approved stack; existing router pin retained |
| Adaptive/skill-map state/API integration | D — DEFER UNTIL CONSUMER OR INTEGRATION READINESS | CT05/CT06/CT07/CT08/CT24/CT40 and actual approved consumers; this record supplies no policy/threshold/event |
| Production origin/exposure/security exception | C — LEADER-RESERVED DECISION | Actual CT17 binding and personal authority, not a frontend-default selection |

**CT16_FINAL_TECHNICAL_SCOPE_PARTIALLY_APPROVED.** F1/F2 are complete reusable
CT16-owned selections; concrete transport/workflow integrations remain gated as
listed. No whole Final or Issue approval is claimed. #98 at
2f459d47a9c398d55dc75c2b4c16622cdaa2adeb and #101 at
8417205b0c5445a1ebace7b8a5f13847c50eddf5 still require separately authorized
remediation; their code is not retroactively approved. Frontend @heizseung owns
implementation; Auth/Course/affected feature owners review actual consumed
boundaries. Sole-Leader documentation CODEOWNER/author conflict, independent
last-push review, exact-head CI/security checks and protected integration remain.
No reviewer, merge, implementation permission or actual QA success is invented.

## CT16 bounded Profile extension — 25 October 2026

### Authority, revision and inspected baseline

**CT16-PROFILE-2026-10-25-r1: DELEGATED_TECHNICAL_APPROVAL;
LEADER_APPROVED.** The Project Leader
@chi-thanh-nguyen explicitly authorizes actual technical selection and approval
in **“CodeMastery — Complete Additional CT16, CT17 and CT14 Technical Approvals in
Existing PRs”**, supplied 25 October 2026 (ICT). Codex, the delegated technical
decision-maker, **approves the three checkpoints below only**. This instruction
authorizes these documentation/commit/PR updates, not application implementation,
production access, new Auth operations or personal security-risk acceptance.

Inspected remote main is
[e2128919fe0d47c0f24989166343e1d5ecf50483](https://github.com/chi-thanh-nguyen/codemastery/commit/e2128919fe0d47c0f24989166343e1d5ecf50483).
Since the previous 0b29672 baseline, six Auth account-operation/DTO/test files
changed; the governing ADRs, OpenAPI and frontend inventory did not. Inspected
[PR #102](https://github.com/chi-thanh-nguyen/codemastery/pull/102) starts at
27780018dd20f6ebe01881afa890b2dc121005d5; #103 starts at 9d966c4 and #106 at
a35059c. The existing @TamiaoNguyen APPROVED reviews cover those initial heads,
not this extension. New commits require current-head review under active rules.
No CODEOWNER, last-push or CT01 protected-integration gap is waived.

The authoritative [OpenAPI](api/openapi.yaml) defines PublicUser with exactly
id (UUID string), displayName (string) and role (Learner, Instructor or
Administrator); LoginResponse requires user: PublicUser. Main's
UserResponse.java now implements that public shape. AuthController implements
registration only; the token-producing login HTTP operation is still absent.
Its future contract is sufficient for a specification, not proof of a working
session. [ADR001 §11](decisions/001-modular-monolith-module-boundaries.md#11-ct01-approved-technical-contract--protected-integration-pending)
and ADR002 CT01.core/CT16.minimum/CT18.minimum/CT38.core retain their authority.
The approved tree already contains features/auth/pages/ProfilePage.tsx,
routes/index.tsx, routes/routePaths.ts, routes/guards/ProtectedRoute.tsx,
app/providers/AuthProvider.tsx and features/auth/hooks/useAuth.ts.

### P1 — authenticated Profile route and navigation

**Checkpoint CT16.profile-routing: APPROVE** one additional browser route,
**/profile**, for an effective, unexpired authenticated session of any existing
role. Compose the existing ProfilePage through the selected declarative
BrowserRouter/Routes/Route/Outlet architecture and existing ProtectedRoute; reuse
one AuthProvider and FE01 layouts. No new router/provider/file, role inheritance,
permission, role-specific administration route or backend operation is selected.
Show one English Profile navigation destination only for the current eligible
session; hiding a link is not authorization. Route protection must also apply to
address-bar entry, internal navigation and browser back/forward.

An anonymous/expired visit renders no Profile subtree or old user fields and
replaces the protected history entry with /login. Capture only the validated
literal return pathname /profile in transient provider/router memory, without
query/fragment, user fields or credentials. After a valid, still-current login,
consume it once and replace to /profile; otherwise retain the existing /courses
fallback. Do not accept a URL/query/history-payload return target supplied by a
caller as authority. Explicit logout clears a stored return destination; its
redirect must not re-arm that destination from the discarded Profile. A new
deliberate protected-route visit can establish a new return intent. Reuse CT16's
external/API/asset/traversal rejection; no open redirect. A fresh document load loses both session and return memory; a new
anonymous /profile entry may capture /profile again, not restore an old session.

*Amendment, 7 November 2026 (Leader instruction, recorded here without changing the
approved text above):* when no `/profile` return is pending, a Learner's sign-in
fallback is `/home` (Learner home) instead of `/courses`; Instructors and
Administrators keep `/courses`. The return capture, one-time consumption, rejection
of caller-supplied targets and logout behavior are unchanged.

*Amendment, 8 November 2026 (`CT16-ROUTES-2026-11-08-r1`, delegated technical approval; see [the CT16 route map](#ct16routes--ct16-routes-2026-11-08-r1--delegated_approved)):* Instructors start at `/teach` and Administrators at `/admin` once those screens are routed in production; until then both keep `/courses`. The Learner fallback, return capture and one-time consumption are unchanged.

Expiry, logout or current-generation authenticated 401 invalidates the session
before personal content can be published and replaces the active protected route
with /login. Navigating back cannot reveal the discarded session. Evaluate
current eligibility before rendering, not only in a post-render redirect effect;
check expiry on resume/pageshow as well as the existing timer/pre-send checks.
No Profile network request or polling is introduced. CT17 serves the same static
SPA shell for deep links; it must not embed personal data in HTML or turn /api
failures into that shell. Client guards do not replace backend authorization.

### P2 — exact read-only session presentation

**Checkpoint CT16.profile-data: APPROVE** projection of the validated current
LoginResponse.user snapshot only, through existing useAuth, in the existing
ProfilePage. The feature owns presentation; AuthProvider owns the snapshot and
its generation. There is no independently fetched/cached Profile resource.

| Existing field | Permitted presentation and invariant |
| --- | --- |
| id | Display only the current session's decoded UUID as plain text with an English User ID label; never as a token or lookup/directory capability. |
| displayName | Render the returned display name as React text with an English label/heading; do not interpret HTML/Markdown, derive identity from the login email, or add another normalization/truncation rule. Preserve the validated value. |
| role | Display exactly Learner, Instructor or Administrator with a Role label; do not present it as a list of granted permissions, ownership or implicit Administrator inheritance. |

The existing LoginResponse decoder must establish the approved user/token/expiry
shape before atomic session installation. Missing/invalid required user data is
a protocol failure; do not mount a partially populated Profile or synthesize data.
A failed replacement login leaves an unrelated valid current session intact.
The snapshot is login-time display data, not a live account-status/role authority
or a guarantee of subsequent freshness; backend checks remain authoritative.
Do not expose accessToken, JWT claims, email, account status, password/hash, image,
settings or permissions. No edit form, password change, account management,
/api/profile, /api/users/me, public directory or extra request is approved.
Additional fields/freshness/workflows await the owning CT01/CT02/API contract.

### P3 — lifecycle and privacy

**Checkpoint CT16.profile-lifecycle: APPROVE** the following concrete application
of the already approved generation/expiry/logout rules, without changing CT01:

- Install Profile eligibility/data only with the successfully decoded, latest live
  login attempt in the still-current generation. Do not reuse registration's
  PublicUser as an authenticated session or automatically log in after registration.
- Logout, expiry and eligible current-session 401 clear the token/user/private
  state, invalidate pending attempts/callbacks and remove personal UI. Stale/public
  failures, cancellation, 403 and network/5xx errors do not invalidate another
  valid session. Delayed login cannot resurrect a logout.
- Successful session replacement atomically removes the previous user's private
  view/state and installs the new snapshot/generation. A response from session A
  cannot publish into session B, even if both have the same user UUID.
- Read Profile values directly from current eligible provider state; do not copy
  them into an independently persistent page cache. Route unmount/navigation
  releases page-owned references/listeners/work and invalidates callbacks. Leaving
  Profile alone does not log out or delete the still-valid provider session.
  StrictMode mount/cleanup must not duplicate listeners or restore old data.
- Refresh starts anonymous. Tokens/user snapshots never enter localStorage,
  sessionStorage, IndexedDB, cookies, URLs/query/hash, history.state, logs,
  diagnostics, analytics or a service-worker cache. No new persistence or telemetry
  mechanism is introduced. This is state isolation, not forensic memory erasure.

### Acceptance, consumers and retained gates

Frontend @heizseung owns separately authorized implementation in the existing
files; Auth @duyduyit reviews DTO/session consumption, Leader reviews documentation,
and QA07 owns independent browser/integration evidence. CT14's bounded Profile/Auth
extension in [PR #106](https://github.com/chi-thanh-nguyen/codemastery/pull/106)
allocates deterministic cases to the existing provider and route suites. Verify
all three roles, anonymous deep link/refresh, safe one-use return, expiry/logout,
back/forward, replacement/late callbacks, exact plain-text fields and zero Profile
requests. Real browser history/focus/responsive and configured Auth/security proof
remain distinct from DOM tests. No result is claimed by this record.

Alternatives: a /me/Profile endpoint duplicates an adequate login snapshot and
requires missing Auth/API authority; local persistence changes CT01 security;
a dedicated Profile cache creates stale cross-session state. Select the existing
session projection and guard. Auth-free rendering or JWT role-based permission
claims violate the established boundary.

This is an **additional read-only Profile capability**, not part of or an expansion
to [CT20.I](decisions/002-interim-delegated-contracts.md#ct20i--leader_authorized_by_explicit_instruction).
CT16-2026-10-24-r1, CT16-INTERIM-COVERAGE-2026-10-24-r1, CT16-FINAL-2026-10-25-r1
and the ADR002 path correction remain intact. Earlier route/Profile deferrals
are prospectively refined only for /profile and these three fields/lifecycle;
all Profile API/mutation/recovery deferrals remain. CT16 Final coverage remains
partial. #98's existing Profile code/state-only navigation/session races are not
retrospectively approved. Effective CT01 and exact CT16 protected integration,
separate implementation authorization, actual login producer, required checks,
eligible independent CODEOWNER/last-push review and QA evidence remain gates.

Technical references: [declarative route composition](https://reactrouter.com/7.18.4/start/declarative/routing)
and [React state ownership/reset](https://react.dev/learn/preserving-and-resetting-state);
these support the existing-stack mechanism, not new backend authority.


## CT16 route map, Learner home composition and CT37 Markdown policy — 8 November 2026

These records were approved on 8 November 2026 by delegated technical approval, as each record states; this section is their authoritative repository location. Earlier CT16 records above are unchanged; where a record below resolves or amends one, it says so.

### `CT16.routes` — CT16-ROUTES-2026-11-08-r1 — DELEGATED_APPROVED

Delegated technical approval by Claude Code under the Project Leader's explicit authorization for this task, “CodeMastery — Design Alignment Standardization, Contract Reconciliation & Issue Publication” (8 November 2026). It is not personal Leader review, CODEOWNER approval, implementation authorization, protected integration or runtime verification; those gates stay independent. No owner was consulted during this run; the owners named below review the record afterwards.

**Scope.** Browser paths, guard roles, navigation destinations, active navigation item and role start pages for the Final screens of [DESIGN.md r6](https://github.com/chi-thanh-nguyen/codemastery/blob/4eb5b70be0c98df14e162b34d2c0314b02ab47b5/DESIGN.md#information-architecture). It resolves the "routes, menu destinations" part of the CT16-FINAL disposition "Concrete feature APIs, routes, menu destinations and role/resource policies — D — DEFER" (`docs/setup.md`); feature APIs and role/resource policies stay with their owning contracts.

| Path | Screen | Guard (navigation only) | Active item | Owning Issue |
|---|---|---|---|---|
| `/forgot-password`, `/reset-password` | Recovery | anonymous | — | FE06 (how the reset capability is carried is CT02's decision) |
| `/courses/:courseId/learn` | S7 Path | Learner | My courses | FE08 |
| `/courses/:courseId/lessons/:lessonId` | S8 Lesson | Learner | My courses | FE08 |
| `/courses/:courseId/diagnostic` | S9 entry and result | Learner | My courses | FE09 |
| `/courses/:courseId/mastery` | S14 Mastery Map | Learner | My courses | FE12 |
| `/courses/:courseId/assessments` | S10 | Learner | My courses | FE10 (quizzes), FE11 (assignments) |
| `/courses/:courseId/quizzes/:quizId`, `…/quizzes/:quizId/attempt`, `…/quizzes/:quizId/history` | Quiz intro, S11, S12 history | Learner | My courses | FE10 |
| `/courses/:courseId/attempts/:attemptId` | S12 result | Learner | My courses | FE10 |
| `/courses/:courseId/assignments/:assignmentId` | S13 | Learner | My courses | FE11 |
| `/notifications` | S15 | Learner, Instructor (only where CT11 grants the read) | bell (current) | FE28 |
| `/teach` | S17 Teaching home | Instructor | Teaching | FE14 |
| `/teach/courses/:courseId` with tabs `/content` (default), `/settings` | S18 | Instructor | Teaching | FE14 |
| `/teach/courses/:courseId/lessons/:lessonId` | S18 lesson editor | Instructor | Teaching | FE14 |
| `/teach/courses/:courseId/skills` | S20 | Instructor | Teaching | FE18 |
| `/teach/courses/:courseId/assessments`, `…/quizzes/:quizId` | S19 bank, quiz editor | Instructor | Teaching | FE15 |
| `/teach/courses/:courseId/assignments/:assignmentId` | S19 assignment editor | Instructor | Teaching | FE16 |
| `/teach/courses/:courseId/qa` | Lesson Q&A | Instructor | Teaching | FE13 |
| `/teach/courses/:courseId/learners`, `…/learners/:learnerId` | S22 | Instructor | Teaching | FE19 |
| `/teach/learners` | S22 entry (choose an owned course) | Instructor | Learners | FE19 |
| `/teach/grading`, `/teach/grading/:submissionId` | S21 | Instructor | Grading | FE17 |
| `/admin` | S23 Platform overview | Administrator | Overview | FE23 |
| `/admin/users`, `/admin/courses`, `/admin/reports`, `/admin/audit` | S24 | Administrator | Users, Courses, Reports, Audit log | FE20, FE21, FE22, FE23 |

Creation screens use the literal segment `new` in the identifier position (for example `/teach/courses/new`, `…/lessons/new`, `…/quizzes/new`); identifiers are UUIDs, so `new` cannot collide. Record detail in Administrator tables opens in a side sheet without a route. Query parameters inside a screen belong to its owning Issue.

**Navigation.** Learner: Home, Catalogue, My courses, bell. Instructor: Teaching, Grading, Learners, Catalogue, bell. Administrator: Overview, Users, Courses, Reports, Audit log, Catalogue. Every signed-in role keeps the account menu (Profile, Sign out). Roles never inherit each other's items. `/courses/:courseId` (detail) keeps Catalogue active; the Learner course-workspace paths above make My courses active.

**Activation rule.** A path is registered in production, and its navigation item rendered, only when its screen's consumed contracts are approved, its Issue is authorized for implementation and the screen is delivered. Until then the path shows the existing Page not found state; no preview component, placeholder or sample data is routed.

**Start pages.** Learner: `/home` (unchanged). Instructor: `/teach` once Teaching home is routed in production; Administrator: `/admin` once Platform overview is routed; until then both keep `/courses`. The one-use `/profile` return of CT16-PROFILE P1 keeps precedence and is consumed once; `/` and the logo use the same start page. This amends P1's fallback (as amended on 7 November 2026) for Instructor and Administrator only, conditional on activation; the earlier text is preserved.

**Guards.** Wrong role shows Access denied from the session role; anonymous visits replace to `/login`, and return capture stays limited to `/profile`. Guards are navigation conveniences; backend authorization in each owning contract stays authoritative.

**Alternatives considered.** A new contract Issue for routes (rejected: duplicates CT16's ownership of routing); deferring each path to its feature contract (rejected: 18 Issues gated on an unowned decision and inconsistent namespaces); the sitemap verbatim (adopted, with its "…" segments resolved directly under `/courses/:courseId/` and `/teach/courses/:courseId/`, and a `/teach/learners` entry for the Learners navigation item).

**Exclusions.** No API, payload, permission, role, dependency or new provider; no extension of return capture; no route for preview-only behavior.

**Owners for review.** Frontend lane CODEOWNER; Leader. **Unlocks.** Removes the route/navigation gate from FE06, FE08–FE23 and FE28 (each stays gated on its own contracts); consumed by FE32. **Retained gates.** Repository record in `docs/setup.md`, implementation authorization, CODEOWNER review, protected integration and verification.

### `CT16.learner-home` — CT16-LEARNER-HOME-2026-11-08-r1 — DELEGATED_APPROVED

Delegated technical approval by Claude Code under the Project Leader's explicit authorization for this task, “CodeMastery — Design Alignment Standardization, Contract Reconciliation & Issue Publication” (8 November 2026). It is not personal Leader review, CODEOWNER approval, implementation authorization, protected integration or runtime verification; those gates stay independent. No owner was consulted during this run; the owners named below review the record afterwards.

**Scope.** Which parts of [S6 Learner home](https://github.com/chi-thanh-nguyen/codemastery/blob/4eb5b70be0c98df14e162b34d2c0314b02ab47b5/DESIGN.md#s6-learner-home--implemented-active-courses--preview-full-design-high-fidelity) enter Final and from which owners, under CT16.final-state (F1: routes compose features; no cross-feature internal import or shared store). It approves no API of another contract.

1. **Your courses** stays the implemented list of active memberships (`GET /api/me/courses`). Each row may add the progress line ("N of M lessons · P%") and the canonical next lesson only when CT22.progress/CT22.resume approve a learner-scoped read that returns them for the listed memberships in one request. The page never loops one request per course.
2. **Continue learning** appears only if CT22.resume approves a learner-level "most recent position" read (US-04). The frontend never compares or ranks courses itself; without that read the tile is omitted.
3. **Up next** keeps only recommendation entries: at most one per active course, that course's current recommendation from an approved course-scoped Adaptive read (CT33 read, CT07 semantics), shown unaltered — type, target lesson, the backend reason verbatim, time — and labelled with its course. Entries follow the "Your courses" order returned by the API. No cross-course ranking, merging, scoring, filtering or selection; courses without adaptive mode or a current recommendation show none. Without an approved learner-scoped read returning per-enrollment items, the aside is omitted.
4. **Deadline, result-available and reply/announcement entries are not part of the Final Learner home** and stay Preview. Learners reach them through Notifications (CT11) and the course Assessments page.
5. Each element loads and fails independently and never hides "Your courses"; no sample data reaches `/home`.

**Alternatives considered.** A dedicated aggregated home endpoint (rejected: no single module owner and a path to cross-course adaptive logic); per-course requests from the browser (rejected: unbounded requests and partial failures); the full r6 design (rejected: no approved data for deadlines, results or typed notifications).

**Consumer requirements (recorded in CT22 and CT33; not approvals of those contracts).** CT22: a learner-scoped progress/next-lesson summary and a learner-level most-recent resume read. CT33: a learner-scoped read of each active enrollment's current recommendation without aggregation.

**Exclusions.** Cross-course adaptive learning remains a non-goal; no CT22/CT33 shape is approved. **Owners for review.** Course (CT22), Adaptive (CT33/CT07), frontend lane, Leader. **Unlocks.** FE33 (still gated on the CT22/CT33 reads).

### `CT37.markdown` — CT37-MARKDOWN-2026-11-08-r1 — DELEGATED_APPROVED

Delegated technical approval by Claude Code under the Project Leader's explicit authorization for this task, “CodeMastery — Design Alignment Standardization, Contract Reconciliation & Issue Publication” (8 November 2026). It is not personal Leader review, CODEOWNER approval, implementation authorization, protected integration or runtime verification; those gates stay independent. No owner was consulted during this run; the owners named below review the record afterwards.

**Scope.** Rendering of `lessons.content_markdown` and `learning_materials.text_content` (text materials). `CT37.embed` and `CT37.widget` stay pending.

- **Syntax.** CommonMark with GitHub-style tables and fenced code; no other extensions.
- **No HTML path.** Source is parsed to a syntax tree and rendered as React elements in `frontend/src/shared/components/MarkdownRenderer.tsx`. Raw HTML in the source (block or inline) is never interpreted: it is displayed as literal text. No `dangerouslySetInnerHTML`, `innerHTML` or HTML-string rendering is used anywhere in the path, so no HTML sanitiser is needed.
- **Links.** A link target is accepted only if it is (a) an absolute URL whose scheme is exactly `https` or `http`, or (b) a relative reference limited to a root-relative path (`/` followed by a character other than `/` or `\`) or an in-page fragment (`#…`). Everything else renders as plain text, including `javascript:`, `data:`, `vbscript:`, `file:` and any other scheme, protocol-relative `//host` and backslash forms (`\host`, `/\host`). The check runs after decoding character references and percent-encoding and removing whitespace and control characters, compares the scheme case-insensitively, and uses the standard URL parser rather than string prefixes, so encoded or obfuscated variants (for example `jav&#x61;script:` or `%6Aavascript:`) cannot pass. External links carry `rel="noopener noreferrer"`.
- **Images.** Markdown images are not loaded; they render as a link labelled by their alt text whose target follows the same link rule (otherwise the alt text is plain text), so lesson text makes no third-party requests.
- **Structure.** Markdown headings start at `h2` under the lesson's `h1`; fenced code renders with `CodeBlock` (no execution, no highlighting dependency); tables use the shared table style.
- **Backend.** Stores and returns Markdown source only; no server-side HTML rendering.
- **Verification.** Renderer tests must cover `<script>`, event-handler attributes, raw HTML blocks, `javascript:` and `data:` links and images.

**Dependency.** This policy is library-independent. Choosing a parser/renderer is a separate decision under the Dependency Policy at implementation time; any candidate must render without raw HTML and must not enable a raw-HTML plugin.

**Alternatives considered.** HTML string plus a sanitiser (rejected: keeps an HTML injection path and adds a dependency); plain text only (rejected: loses the approved text-material formatting); an in-house parser (not chosen here: maintenance and correctness risk; still allowed if it meets every rule above).

**Owners for review.** Course (CO09), frontend lane, Leader. **Unlocks.** FE08 lesson rendering and the FE14 Markdown preview tab (still gated on their other contracts).

## FE03 implementation in PR #98 — 25 October 2026

This is an implementation record, not a new technical decision or acceptance of
real integration. The Leader explicitly authorized remediation, the 14 Issue #56
primary files, the exact router manifest/lockfile change, the three CT14 FE03
suites, factual setup/testing synchronization and the existing Frontend CI test
step. The feature branch preserves its published commits and integrates main
`1e23fe240af962f4d36041ddb1f53beb5edba943` with a regular merge.

Consumed decisions are CT16-2026-10-24-r1, Final F1/F2 and
CT16-PROFILE-2026-10-25-r1 (PR #102, merged `aed6985`), CT14's existing six-path
exception, Final F1/F2 and Profile/Auth UI V1/V2 (PR #106, merged `1e23fe2`),
and the unchanged CT01/CT18/CT38 slices identified above. These implementation
facts do not alter the historical proposal, approval or provenance records.

Install with `npm ci` in `frontend/`. The sole added direct production dependency
is exact `react-router@7.18.4`; the lockfile also records its `cookie@1.1.1` and
`set-cookie-parser@2.7.3` transitive resolutions. The existing Node range and all
other direct versions remain unchanged. The clean installation reported zero
npm audit vulnerabilities on this date; this is a dated scan, not a guarantee.

Set the existing public build-time `VITE_API_BASE_URL` explicitly using one of
the CT16 forms above. No fallback origin, new variable, secret or Vite proxy is
introduced. Configuration is validated before any API request. Changing the
build-time value requires rebuilding. The documented host-local producer address
can be supplied for local frontend verification:

```bash
cd frontend
npm run typecheck
npm test
VITE_API_BASE_URL=http://localhost:8080 npm run build
npm run preview -- --host 127.0.0.1
```

One BrowserRouter and AuthProvider compose the current FE01 presentation at
`/courses`, public Auth pages, the public Course-detail composition placeholder,
the protected read-only `/profile`, root replacement and unknown-route state.
The current FE01 dashboard and utilities remain explicitly illustrative; actual
catalogue/detail rendering belongs to FE07. No Course membership is implemented.
Profile reads only the validated login snapshot and performs no API lookup.
Reload loses the memory session. Offline logout has the previously accepted
client-local scope and does not revoke copied tokens on a server or other tab.

The real login HTTP producer is absent from the integrated main AuthController.
Controlled Auth responses establish frontend behavior only; QA07 browser/real
REST/PostgreSQL, QA01 backend security, deployment/proxy/deep-link delivery and
independent protected review retain their own evidence gates. See the execution
record in [testing.md](testing.md#fe03-implementation-regression-evidence--25-october-2026).


## FE07 catalogue and membership integration — 27 October 2026

PR #101 implements ADR002 `FE07-2026-10-27-r1` under explicit Leader delegation;
the new technical decisions await protected integration. Existing runtime variables,
dependencies, ports/default origins and applied migrations are unchanged.

`/courses` is the real public catalogue, with literal title/description search,
prerequisite filter and server pagination. `/courses/:courseId` shows published
syllabus/instructor/prerequisites and learner membership actions. Anonymous users
sign in through the existing approved login contract; the backend now binds that
REST operation to credential verification and JWT issuance. `/my-courses` is
Learner-only, defaults to joined memberships and supports status filters. Leaving
requires confirmation; re-enrolment preserves the canonical row and learning
history. Reload still clears the memory-only session, and Profile return behavior
is unchanged. The FE01 illustrative workspace remains independently available at
`/preview`; it does not represent saved learning data.

Course owns GET/PUT/DELETE `/api/courses/{courseId}/membership` and GET
`/api/me/courses`. Read exact DTOs/visibility/idempotency in OpenAPI and ADR002;
old `/api/learning/my-courses` and `/enroll` candidates are not supported aliases.
CORS admits PUT/DELETE for the selected authenticated flow while other protected
routes remain denied. No token parameter is exposed to feature clients.
See docs/testing.md for executed local PostgreSQL/browser evidence, fixture and
external Playwright requirements; the E2E directory still has no npm pipeline.


## Batch 1 conditional settings client handoff — F1–F4

> **Current R1/R2 refinement:** [ADR005 §16](decisions/005-batch-01-contract-approvals.md#16-r1r3-independent-review-corrections--9-november-2026) completes the precondition channel and adds the bounded CT16 no-content extension. It preserves the N1 handoff below.

[ADR005 §13](decisions/005-batch-01-contract-approvals.md#13-focused-f1f4-amendments--8-november-2026)
(`CT06-ENABLEMENT-2026-11-08-r2`) governs FE18/AD08/CO14's current contract.
This documents requirements only; it implements no client, CORS or UI behavior.
The narrow [N1 correction](decisions/005-batch-01-contract-approvals.md#15-n1-http-validator-correction)
preserves r2 approval provenance: only GET emits an ETag; successful canonicalizing
PUT must omit all validator fields, including ETag and Last-Modified.

The existing Instructor `skill-graph-editor/api/skillGraphApi.ts`,
`hooks/useSkillGraph.ts` and `components/AdaptiveSettingsPanel.tsx` must retain the
successful GET settings body and its strong ETag as one snapshot. All settings PUTs
send If-Match; 412 retains the draft, refreshes latest state, and requires deliberate
user reconciliation before resubmission. Never refresh the tag and automatically
replay a stale full form. 428 requires fetching a tag; 409 requires safe explanation
and deliberate retry/review. None clears Auth. Same-policy disable, enable and n
changes remain possible; thresholds may be changed only before submitted history
and stored mastery. No whole-Course replay/catch-up progress indicator is promised.

After successful PUT, invalidate the previous write validator and perform GET before
another update. Retain only that GET's body and ETag as a paired snapshot: a concurrent
writer may have changed the resource since PUT. Do not combine the PUT body with the
GET tag, derive a tag in the client, or reuse the prior snapshot without this refresh.
A no-op can yield the same freshly obtained ETag. Failed/cancelled or obsolete GET
does not undo a successful PUT and must not trigger automatic PUT replay; wait for a
successful current-generation GET before another write. Existing session isolation
and explicit stale-draft reconciliation remain required.

`shared/api/httpClient.ts` currently accepts custom request headers and returns only
decoded bodies. A later authorized additive response-metadata capability is required
to observe GET's ETag only after successful status/decoding and current-generation checks;
preserve existing callers, cancellation and session isolation. Its existing 412/428
safe ProblemDetail title handling also needs implementation. Do not bypass shared
transport, expose credentials, or introduce a store/framework for validators.
The existing CORS policy must allow If-Match and expose ETag for approved origins,
without changing origins, credentials or role policy. This is a bounded header
requirement, not proof of current browser integration. Use a same-origin test and
an approved-origin cross-origin test before FE18 acceptance. Current preview/local
components do not prove backend readiness or conditional-write support.


### R1/R2 current frontend transport handoff — 9 November 2026

**CT16-B1-NO-CONTENT-2026-11-09-r1 — DELEGATED_APPROVED**, selected by Codex
under the Leader's explicit PR #228 independent-review correction instruction.
[ADR005 §16.2](decisions/005-batch-01-contract-approvals.md#162-r2-bounded-ct16-no-content-transport-extension)
is the exact scoped amendment of `CT16-2026-10-24-r1`'s JSON-only restriction;
all historical approval provenance, JSON/Auth/security behavior and unrelated
slices remain effective. Protected integration and separate implementation
remain required. This is contract documentation, not delivered client behavior.

Extend existing `shared/api/httpClient.ts` additively: omitted responseMode (or
`'json'`) preserves `RequestOptions<T>`'s decoder and Promise<T>. Explicit
`NoContentRequestOptions` selects `responseMode: 'no-content'`, expectedStatus=204,
no decoder, and Promise<void>, with overloads on both request/client entry points.
Only exact 204 succeeds without media-type requirement/body parsing/DTO decoding;
unexpected success is a protocol error. Invalid mode/status/decoder combinations
fail before fetch. Non-success still uses CT18 safe ProblemDetail/status handling.
Do not globally accept empty JSON, bypass transport or change approved 204s.
Keep all session/operation/abort/expiry checks through the final return, current
401 invalidation, credential/path/redirect rules and no retries.

FE18's existing skillGraphApi/useSkillGraph/SkillGraphEditorPage use void success
for deleteCourseSkill, addCoursePrerequisite, removeCoursePrerequisite,
mapCourseLessonSkill and unmapCourseLessonSkill. Refresh the canonical graph only
for a current Course/operation; a failed/obsolete refresh never undoes the confirmed
mutation or triggers replay. Review actual exported types and consumer tests before
binding. The existing client still needs this extension plus the already documented
GET metadata/412/428 capability; none is claimed implemented here.

Settings normally send If-Match alone. If an optional If-None-Match is sent, preserve
it into the same guarded decision; 412 can mean either failed condition even with
unchanged configuration. Preserve draft, obtain a fresh GET pair and require user
review of draft **and conditions**; do not blindly replay a matching exclusion or
`*`, discard it silently, or clear Auth. Approved-origin CORS must allow this header
when used, plus If-Match and GET ETag exposure; no origin/credential change. Preserve
N1's validator-free PUT and mandatory fresh GET body/tag pairing before another write.
[CT14 B1-O18/O19](decisions/005-batch-01-contract-approvals.md#163-ct14-acceptance-oracles-and-direct-consumer-traceability)
defines current tests and their existing allocation.

## Batch 2 frontend and operator handoff — 9 November 2026

[ADR006](decisions/006-batch-02-contract-approvals.md) records all eight delegated
approvals and the Leader's two explicit additional scope authorizations.
Most added operations in [OpenAPI](api/openapi.yaml) remain unimplemented. The two
CT02 password-reset operations are implemented under AU07 (PR #239); real SMTP
delivery and independent review remain outstanding.
Existing CT16 shared transport, session/abort/generation isolation, safe errors,
route guards and no automatic mutation retries remain effective.

FE08 consumes Course progress/Lesson/resume JSON reads. After loading a Lesson,
an explicit visit may write pointer/server time; GET/prefetch must not write.
FE33 uses one paginated Course progress read and the backend's learner-level
resume selection, keeping existing My Courses DTOs unchanged. Continue learning
can activate only after the separately authorized migration, grants and producer
delivery. CT33's Up next remains gated. FE14 text/video authoring uses owner-only
DTOs; files, Admin and publication events retain CT09/CT12/CT26 gates.

`CT37-EMBED-2026-11-09-r1` accepts only canonical
`https://www.youtube-nocookie.com/embed/{videoId}` under
[ADR006 §6](decisions/006-batch-02-contract-approvals.md#6-ct37embed).
Existing LessonContent/MaterialForm create React elements; no iframe HTML,
autoplay, SDK, API key or pre-activation request. Preserve provider-required origin
Referer using strict-origin-when-cross-origin, without credentials or protected
query parameters. Player minimum viewport and external-link fallback apply.
No concrete video asset, licence or actual playback is approved by provider policy.

AU07 implements the CT02 reset endpoints and V007. PostgreSQL behavior is covered by
the Testcontainers suite and the CT17 VM tests; real SMTP delivery is not verified.
The reset request is best effort and returns the same 202 even for unknown/locked/full
queue; it proves no delivery. Auth's bounded single-worker queue holds up to 128
pending canonical emails. Outstanding-token reissue interval is PT1M; token lifetime
is explicitly PT30M, and `PASSWORD_RESET_TOKEN_TTL` (default PT30M) must equal it.
The local service clock is testable, with no global CT39 binding. AppConfig decides
recovery activation: only a PT30M TTL, a trusted lowercase HTTPS origin, a bare sender
address and the three SMTP inputs together make recovery active. Otherwise the backend
logs one warning naming the variable, and reset requests are acknowledged without
lookup, token or email. Backend health excludes SMTP. SMTP host, credentials, sender
and origin remain deployment-provided values; local Compose and the VM release
deliberately pass none, so recovery is inactive there until a separately authorized
deployment binding exists.

The reset link uses a configured approved HTTPS origin and /reset-password#token=…,
never request Host/Forwarded. FE06 captures/erases the fragment before requests;
no token in persistent storage, query, returnTo, DOM, analytics or logs. Reset page
loads no third-party resources and sends no Referer. POST confirmation uses existing
password validation and returns JSON with no JWT; ordinary sign-in follows.

[ADR006 §10](decisions/006-batch-02-contract-approvals.md#10-au07-implementation-record-and-gate-status--1213-november-2026)
records the implementation choices. On 13 November 2026 the Leader approved
`AU07-IMPL-2026-11-13-r1`: AU07 implementation, V007 (repository and controlled test
environments only), the Spring Mail adapter and its placement, and feature-scoped
fail-closed delivery. The Leader also approved the runtime DML
`CT17-P6-PRIVILEGES-2026-11-13-r2`. Under unchanged CT01, existing JWTs remain
valid until their original expiry (at most PT15M) after reset, and reset-specific
risk acceptance remains PENDING_LEADER_RISK_ACCEPTANCE before public exposure. This
implementation does not authorize production credentials, live shared-database
migration, public exposure or bypass of independent review. Provider delivery,
load/abuse qualification and security review remain outstanding.


## Batch 3 expiry configuration specification — not implemented

[ADR007 §3.2](decisions/007-batch-03-contract-approvals.md#32-ct38featurequiz-expiry)
records **CT38-QUIZ-EXPIRY-2026-11-09-r1**: explicit backend
APP_QUIZ_AUTO_SUBMIT_ENABLED, APP_QUIZ_AUTO_SUBMIT_DELAY_MS and
APP_QUIZ_AUTO_SUBMIT_BATCH_SIZE, their property bindings, validation, consumer
locations and disabled-mode limits. No runtime configuration/default or .env.example
change is made here. Future implementation must bind/validate these required inputs
and synchronize the placeholder template. Deployment tuning and actual timely
finalization remain unverified; existing Auth/reset/storage configuration is unchanged.

## Batch 4 runner and time-input decisions — 9 November 2026

[ADR008](decisions/008-batch-04-contract-approvals.md) technically approves the
existing pinned E2E runner and disposable setup interface; current shell inputs
and reproduction commands remain as documented. External mode is only for an
explicitly authorized disposable/local environment; it can mutate accounts and
memberships and owns no external cleanup. No environment variable, production
configuration or dependency changes here. SH07's E2E CI contract is approved for
later separately authorized implementation; no job is added by this record.

CT39 selects no shared Clock bean or shared consumer API. Existing reset and
quiz local time seams retain their effective contracts. This does not configure
fake time in AppConfig, activate TestClockConfig, alter JWT expiry or approve
AS09/IN08 deadline/reminder behavior. Independent review, protected integration,
implementation authorization and runtime/QA evidence remain separate.

**10 November 2026 fixture amendment:** CP-030 is now
`CT34-FIXTURES-2026-11-10-r2`, specified in
[ADR008 §10](decisions/008-batch-04-contract-approvals.md#10-f1f2-fixture-amendment--10-november-2026).
The future launcher must establish checkout/output exclusivity before Playwright
entry and retain a private, non-secret inventory outside test outputs and temporary
builds. Current direct Playwright commands and PID-only `stack:down` do not yet
implement r2; do not infer concurrent-output protection or full interrupted-run
recovery from them. New lifecycle behavior requires separate implementation and
qualification; no environment variable or runnable command is added here.

## SH05 storage adapter configuration — 25 November 2026

SH05 implements `FileStorage` and its lazy `S3FileStorage` component under ADR010
§§4–7, including the §16–18 corrections. This implements the storage boundary;
CO09/AS09 file admission, REST delivery and transaction lifecycles remain downstream.
The earlier dated implementation snapshots below retain their historical meaning.

The adapter consumes only the existing `codemastery.storage.*` properties and
`STORAGE_ENDPOINT`, `STORAGE_REGION`, `STORAGE_BUCKET`, `STORAGE_ACCESS_KEY`,
`STORAGE_SECRET_KEY`, `STORAGE_PATH_STYLE_ACCESS` bindings. It validates them when
a consumer creates the bean; unrelated features do not require storage credentials.
Invalid consumed settings stop bean creation with the input name and no value.
Existing localhost/region/bucket/path-style fallbacks remain local convenience,
not approved deployment values. Supply an application identity provisioned by the
initializer; the adapter neither reads `MINIO_ROOT_*` nor provisions resources.

The Project Leader explicitly approved internal timeouts in the SH05 implementation
task on 25 November 2026: connection 5 seconds, socket read 30 seconds, attempt and
complete API call 60 seconds each, with no retry and no new environment variable.
Pool acquisition is bounded by the same connection timeout. These are adapter
settings, not an upload limit or a production capacity qualification. The existing
AWS SDK Apache transport is configured through its public SPI; no dependency changes.
SDK and Apache HTTP diagnostics are denied at the output boundary, including
DEBUG/TRACE wire and signing output; application logging remains available.
`backend/src/main/resources/logback-spring.xml` configures the existing console
output with `S3FileStorage.ProviderDiagnosticsFilter` before the appender starts
or is attached. It reuses Boot's patterns, charset and threshold and the existing
`logging.structured.format.console` setting (including the deployed ECS format).
The Project Leader authorized this additional configuration file and its structure
entry for SH05-R01 round 2 on 25 November 2026. No dependency, environment input,
output destination or storage contract is added.

A source TurboFilter can admit an event while its list is being reset; that event
can reach a newly attached appender after source denial is restored. The output
filter instead checks each event immediately before output, including an event
admitted before or during reconfiguration. Old appenders retain their filter
until stopped; replacement appenders start with their own filter. The stateless
filter owns no adapter, listener, thread or transport resource. Closing a Spring
context does not remove another adapter or active download's protection. There is
no constructor-time logging registration or lifecycle repair callback.

Application logging is configured before Spring consumes the adapter. Alternative
Joran configurations and programmatic test sinks must install the same filter
first in the chain before starting/attaching every output; a custom unfiltered appender or deliberate
replacement/removal of the confidentiality boundary is outside this guarantee.
Ordinary reset/stop/start and reinitialization with the supplied configuration
remain supported on the existing Boot 4.1.1 / Logback 1.5.38 versions.

Upload input streams belong to the caller. `open` returns a caller-owned
`StoredObject`; use try-with-resources across header preparation and body copying.
Read/close failures remain checked `IOException` with safe diagnostics. Input
length must be positive and exact, and the key/media type must match CT09. Upload
filename/signature/size-limit checks and authorization belong to the consumers.
The adapter does not buffer the full upload or perform compensating deletion:
the CT09 consumer owns the single best-effort cleanup of its fresh, unreferenced
key on failed store. No new retention, overwrite or orphan policy is introduced.

## Batch 5 storage configuration, file transport and widget — 12 November 2026

[ADR010](decisions/010-batch-05-contract-approvals.md) approves CT09.config,
CT09.rest's frontend transport and CT37.widget; nothing is implemented here.
Storage keeps the existing names `STORAGE_ENDPOINT`, `STORAGE_REGION`,
`STORAGE_BUCKET`, `STORAGE_ACCESS_KEY`, `STORAGE_SECRET_KEY` and
`STORAGE_PATH_STYLE_ACCESS`; the storage adapter validates them when it is created
and never uses `MINIO_ROOT_*`. `MAX_UPLOAD_SIZE` is the single upload limit (1 byte
to 2147483647 bytes); its `20MB` fallback is local configuration, and deployment
needs an operator-recorded value plus a matching ingress body limit. No variable,
default or `.env.example` entry changes, and no storage setting reaches `VITE_*`.

Files travel only through the backend: no pre-signed or public storage URL. The
shared `httpClient.ts` gains additive multipart (`FormData`, browser-set boundary,
JSON result, no retry; append a File or a Blob with its explicit original filename,
without text/JSON/base64 conversion) and authenticated binary download (`Blob` plus the
`Content-Disposition` filename) capabilities. Existing JSON callers, session
generation, abort handling and 401-only session clearing stay unchanged; 413 maps
to `Content Too Large`; a network failure or 500 on upload is an unknown outcome
that reloads the resource first. Grading echoes `submittedAt` as the exact string
received, never through `Date`. Cross-origin development must expose `Content-Disposition`
in CORS. The shared widget remains `shared/components/FileUploadField.tsx`: one
file, PDF or PDF/ZIP requirement text, indeterminate progress, confirmation before
replacement, Blob download through a revoked object URL, no token in any URL and
nothing persisted. Implementation, CORS/multipart configuration, runtime grants and
browser verification remain separate.

## Batch 7 reminder configuration and notification client — 15 November 2026

[ADR012 §3.5](decisions/012-batch-07-contract-approvals.md#35-ct38featuredeadline-reminders)
approves four backend-only inputs for the Interaction deadline-reminder scheduler,
following the CT38 quiz-expiry pattern: `APP_DEADLINE_REMINDER_ENABLED`,
`APP_DEADLINE_REMINDER_DELAY_MS`, `APP_DEADLINE_REMINDER_LEAD_MINUTES` and
`APP_DEADLINE_REMINDER_BATCH_SIZE` (properties under `app.deadline-reminder.*`).
They have no defaults and no approved production values; missing or invalid values
will fail startup once implemented, and a deployed reminder capability needs
operator-recorded values. They are **not implemented**; `.env.example` and
`application.yml` are unchanged by this record. They are never `VITE_*` inputs.

The notification client (FE28) uses the ADR012 §4.2 operations. No polling interval,
push channel, WebSocket or server-sent events are approved: the unread count is
reloaded on route change and the list when the bell or notifications page opens.
Notification messages and Q&A posts are rendered as plain text, never as Markdown
or HTML. Administrator screens (FE20–FE23) consume only the `/api/admin/**`
operations in OpenAPI; frontend route guards remain no security boundary.

## ADR014 hybrid authentication session — 16 November 2026

`CT16-SESSION-2026-11-16-r1` and `AUTH-SESSION-2026-11-16-r1` are
**DELEGATED_APPROVED** under the Project Leader's explicit instruction of 16 November
2026 ([ADR014](decisions/014-hybrid-refresh-session-authentication.md)); they are
implemented in the same PR and become effective on `main` with its protected
integration. Current behavior:

- **Access token.** Memory-only in `AuthProvider`, sent only as `Authorization: Bearer`
  to protected endpoints; never stored in cookies, Web Storage, IndexedDB, URLs, history
  or logs. Each token carries the `sid` of its refresh session and stops working when
  that session is revoked.
- **Refresh cookie.** Set by sign-in and each refresh; HttpOnly, SameSite=Strict,
  `Path=/`; `__Host-cm_refresh` (Secure) by default, and `cm_refresh` without Secure
  only when every `CORS_ALLOWED_ORIGINS` entry is an HTTP loopback origin (local Compose `http://localhost:5173`, the E2E stack). Scripts never
  see it.
- **Restore and renewal.** `App` mounts `AuthProvider refreshSessions`. On load it calls
  `POST /api/auth/refresh` once (shared under React StrictMode); protected routes show
  “Restoring your session…” instead of redirecting. A document opened at `/preview`
  does not restore. The access token is renewed 60 seconds before expiry; the same
  user and `sid` is an in-place rotation (no page remount); a refusal ends the session
  with the existing notice; network failures retry every 10 seconds until expiry.
- **Precedence** (ADR014 §15). A sign-out, local or from another tab, discards any pending
  restore or sign-in. An explicit sign-in discards a pending restore and is not cancelled
  by it or by the old session ending. If it fails, the superseded restore runs again.
- **Tabs.** Sign-in, refresh and sign-out run under the Web Lock
  `codemastery-auth-session`; the `BroadcastChannel` `codemastery-auth` signs other tabs
  out (no request) or lets them adopt a new sign-in.
- **Sign-out.** Clears the tab at once, notifies other tabs and calls
  `POST /api/auth/logout` (`keepalive`). If the call cannot be confirmed, the non-secret
  marker `codemastery.auth.pendingSignOut` makes the next load retry sign-out instead of
  restoring.
- **Transport.** `httpClient.ts` uses `credentials: 'include'` only for public `POST`
  `/auth/login`, `/auth/refresh` and `/auth/logout`; all other requests keep
  `credentials: 'omit'`. No mutation is ever retried automatically.

**Configuration.** No new environment variable. `CORS_ALLOWED_ORIGINS` is also the
exact trusted Origin list of the three cookie operations (a request from another origin
gets 403) and selects the cookie mode. Local development therefore works with the
existing Compose value `http://localhost:5173` and a backend on `http://localhost:8080`
(same site, credentialed CORS only on the cookie paths). Open the frontend through the
configured origin: a frontend served from another origin can sign in only if that
origin is listed. `VITE_API_BASE_URL` is unchanged. Applying V008 to a local database
happens automatically when the backend starts; doing so on any shared or production
database is Leader-reserved.


## FE32 role navigation and start-page implementation — 25 November 2026

Implementation in [PR #272](https://github.com/chi-thanh-nguyen/codemastery/pull/272),
rebuilt from `main` `d8c6588a02bbfbc09cfd2f2fe0d4323f1be9a018` under the Project
Leader's explicit FE32 (#224) instruction. The former FE15 API/hook commits on that
branch were superseded by merged PR #273; rebuilding preserves that merged code.
This record adds no contract approval or activation authorization.

- `routes/routePaths.ts` contains the existing workspace's native React Router
  route tree (`WORKSPACE_ROUTES`), approved constants and complete per-role
  navigation model. `AppRouter` binds its IDs to delivered screens using the same
  declarative `Routes`/`Route` composition. Start pages and navigation availability
  inspect that exact tree with `matchRoutes`; constants or preview components do
  not activate a screen. There is no new router, provider, store or dependency.
- `currentSection` uses router ranking and segment decoding, including case,
  trailing slashes and query/fragment independence. It requires a delivered route,
  the current role's eligibility and a live parent item. Learner course-workspace
  routes map to My courses; public detail maps to Catalogue; course-scoped
  Instructor insight maps to Teaching; Grading and Administrator destinations keep
  their own mapping. Unknown, inactive and wrong-role destinations have no current
  primary item. FE28's notification utility has its separate activation/current
  handoff, with no bell mounted before its screen is delivered.
- `roleStartPath` is shared with AuthProvider. Learner starts at `/home`, Instructor
  at `/teach` when delivered and Administrator at `/admin` when delivered; the last
  two otherwise use `/courses`. The provider exposes the approved pending start
  target without consuming it, and consumes Profile once on successful sign-in,
  authenticated landing redirect or following the workspace logo. Rendering and a
  cancelled editor navigation do not consume the return. Only canonical `/profile`
  is retained in memory, including equivalent router spellings; no supplied URL,
  query, fragment or persistent return value is trusted. Explicit logout clears it.
- Pathless native role groups compose `ProtectedRoute`/`Outlet`. Only matched,
  registered screens enter the guard: anonymous replaces to Sign in, wrong role
  sees Access denied without mounting the feature, and unresolved restoration
  waits. There is no `/teach/*` or `/admin/*` wildcard guard. Backend authorization
  remains authoritative and roles never inherit.
- Existing FE34 tokens, header/drawer controls, r13 utility order and shell
  navigation interception remain in place. PR #273's Course/skill context,
  acknowledged Settings state, session isolation and unknown-outcome recovery are
  unchanged. Three existing feature-test Auth doubles add the new `startPath`
  projection; this is test compatibility, with no upstream functional slice.

Current production integration remains Home/Catalogue/My courses for Learners,
Teaching/Catalogue for Instructors and Catalogue for Administrators. `/admin`, its
four child destinations, Grading, Learners, course-scoped insights, notification
page and undelivered Learner course-workspace screens remain inactive. Their
complete navigation and start rules are covered with test-only route fixtures.
No missing upstream code is needed to complete the FE32-owned mechanism: the
additional functional scope is **0%** for each of FE03 #56, FE34 #255, FE14 #199,
FE23 #208, FE17 #202, FE19 #204, FE20 #205, FE21 #206, FE22 #207 and FE28 #210.
The FE23 reporting/backend chain is not absorbed.

Authority: `CT16-ROUTES-2026-11-08-r1`, CT16.shell/final-state, CT16-PROFILE P1,
ADR014 §7 and its lifecycle corrections, and `DESIGN-2026-11-21-r13` (S1, S6,
S17, S23, F1, F2, F14, D12-5 and D13-1–D13-5). Some historical Issue/DESIGN
delivery descriptions predate PR #261/#273; actual production registration above
is the implementation evidence. The requested `CLAUDE_CONTEXT_SESSION_GUIDE.md`
was unavailable; the existing local `CLAUDE_SESSION_CONTEXT_GUIDE.md` was read as
a supplemental retrieval map, with repository governance authoritative.

[Verification and remaining gates](testing.md#fe32-role-navigation-self-audit--25-november-2026)
are implementer evidence, not independent CODEOWNER review, QA acceptance,
protected integration, release or deployment authorization.


## FE32 R1/B1 targeted remediation — 25 November 2026

The Project Leader's explicit instruction **“CodeMastery — PR #272: Targeted
Remediation of R1/B1 Findings”** transitions the completed review into bounded
remediation in PR #272 (Refs #224). It authorizes fixing inherited B1-F01/F02/F03,
normal commit/push, updating the PR description, and synchronizing these records;
it does not authorize merge, deployment, Issue closure or Project acceptance.

The same instruction explicitly authorizes retaining the existing AuthProvider
test-double compatibility changes, and directly relevant regressions only, in
`assessmentHooks.test.tsx`, `assessmentAuthoring.test.tsx` and
`MyCoursesPage.test.tsx`. No production feature work is authorized there. It also
authorizes narrowly scoped durable regressions in the existing
`shared/components/presentation.browser.cjs`; no new tracked file or dependency.
This resolves the additional-file authorization evidence gap identified in R1.

The starting reviewed HEAD is `10d3198fb681efb1f2ddce156cc89a274e6ebc86`.
`main` `c5b9ac0ac04b057cef7ee7f62fdcb8143202bc9a` (SH05 #289) is incorporated by a
normal merge, preserving the original FE32 commit, #273 and #289. Storage source,
its documentation and the FE32 historical records are retained; obsolete FE15
commits are not reintroduced and no force push is used.

- **B1-F01/F03:** the navigation slot reserves its intrinsic content width; the
  account slot may shrink through the existing display-name ellipsis while the
  visible role and full accessible name remain. Full role sets use existing
  compact spacing values. Brand sizing, labelled-pill padding, utilities, r13
  ordering and breakpoints stay in place. Icon-only anchors have actual 44×44 px
  rectangles, rather than theoretical or overlapping invisible extensions.
- **B1-F02:** route focus holds one intent for a pathname navigation. If the
  heading is delayed, a temporary observer watches only that route's main subtree.
  The first meaningful heading consumes the intent. Subsequent user interaction,
  query-only navigation, another route, session replacement, blur or unmount
  cancels it and removes the observer/listeners. An unfinished initial session
  restore may provide that navigation's first session; ordinary data updates do
  not create another intent. No timer repeatedly focuses content.
- Production registration, activation, role starts, Profile return memory,
  backend authorization, #273 Course/skill ownership, Settings reconciliation and
  mutation replay rules are unchanged. Future screens remain inactive. No upstream
  production capability, API, provider, store, route or dependency is absorbed.

The focused browser harness mode `--fe32` uses a caller-owned production URL and
`--fixture-url`. Its exported `createFE32Fixture` builds a temporary fixture from
actual reviewed shell/theme/icon components and `roleNavigation`, with synthetic
future route descriptors. It never registers those screens in production. Use
only URLs printed by the isolated servers; the normal broad harness mode remains
available. [Dated verification](testing.md#fe32-r1b1-targeted-remediation--25-november-2026)
separates remediation-author evidence from independent closure and acceptance.


Post-push harness follow-up: SonarCloud on `0582fd6` flagged the local polling
condition `pendingRead` as unchanged within its loop (new-code reliability gate).
The bounded loop was updated by an asynchronous request callback, but the probe
now uses an explicit request-start receipt and a bounded 7-second deadline with
cleanup instead. Held reads are released together, including superseded requests.
This changes test synchronization only, with no production implementation change;
no rule suppression, tolerance change or removed assertion. Focused browser verification passes 6 cases in each of Firefox 155 and WebKit 26.6,
and the 8 harness lifecycle tests pass for this follow-up; the earlier full
matrix and runtime-source evidence remain applicable to the identical application.


## FE14 implementation and bounded Markdown decision — 26 November 2026

Authority: the Project Leader's explicit “Complete FE14 Implementation, Bounded Upstream
Completion, PR #279 Replacement and Full-Scope Self-Audit” task. This record uses the
Section 6 delegated technical authority for implementation details, dependencies and focused
supporting/test files. It changes no REST contract, architecture, canonical design, owner review,
protected integration or operational exposure decision. Refs #199; bounded renderer only: #193.

### Implementation decisions and limits

- Preserve the merged PR #273 Teaching/Course/Settings/graph foundation, including D1/D1-R1/D1-R2.
  The Lesson editor consumes the shell's `getOwnedCourse` tree, never a duplicate Lesson read.
  Activate only `TEACH_LESSON`; FE13 Q&A and FE19 learner-insight routes stay unregistered.
- Whole-Lesson replacement uses `updateCourseLesson`; title/content drafts remain local.
  Confirmed responses acknowledge saves; a shared provider revalidation floor prevents another
  structure/Lesson mutation from using an older tree, even across a child remount. Settings'
  separate metadata acknowledgment and causal sequence are retained. Shared graph refresh remains
  single-flight, session-bound and explicitly retried after failure; mapping chips wait for it.
- Materials are independent commands using all eight CO09 operations in OpenAPI. Render their
  authoritative returned order. Do not synthesize filenames/timestamps/keys/URLs or counts.
  PDF fields retain the actual File. Multipart boundaries and binary decoding stay in the existing
  HTTP client. Cancellation, network/500 and unusable upload responses require resource re-read
  before another manual attempt; no mutation replay. Downloads use a transient object URL and the
  transport-validated server filename, followed by revocation. The owner DTO has no original
  filename/upload time: only the selected local filename is shown before upload, never invented
  server metadata.
- CT37 video admission compares a standard URL parse and the entire canonical privacy-enhanced
  embed serialization, including its ASCII identifier. Editing a video makes no provider request.
  This authoring surface displays the validated address; no third-party playback acceptance or
  media-rights assertion is made.
- Leave confirmation covers editor Back and shell navigation; `beforeunload` warns on reload.
  The declarative router's existing native Back/Forward limitation (DESIGN A15) is unchanged.

### Bounded FE08 renderer adjudication

`MarkdownRenderer.tsx` was empty on main `cc6cb54`; PR #286 at `532039a` has an unmerged
regex-based renderer. Its source does not provide a CommonMark syntax tree and its link admission
uses prefixes, so it is not a complete CT37 renderer baseline. No PR #286 code or commits are reused.

The full FE08 functional inventory below estimates complexity in relative implementation work
units; each row includes its focused testing. These are planning estimates, not LOC, file counts
or a claim that FE08 is complete.

| FE08 work group | Units |
| --- | ---: |
| Canonical outline/Path and order | 5 |
| Progress read and display | 4 |
| Resume/Continue/Start selection and fallback | 4 |
| Explicit completion and authoritative reconciliation | 4 |
| Silent post-render visit recording | 2 |
| Lesson data/routes/access/session isolation | 6 |
| Non-Markdown Lesson material integration: PDF and activated video | 6 |
| Shared Markdown parser/rendering/security regressions (this slice) | 7 |
| My courses aggregate progress read/join | 3 |
| Responsive Lesson reader/outline/drawer | 4 |
| Learning navigation and entry points | 3 |
| Learning-journey recovery/concurrency | 2 |
| Total | 50 |

The bounded addition is 7/50, approximately 14%, below the task's approximately 20% threshold.
It completes only the shared CT37 renderer and its tests/dependency support, required by FE14
Markdown Preview. The other 43 units, including learner progress, resume, completion, learning
routes, reader/PDF/video integration and adaptive guidance, are excluded. No FE08 completion or
independent acceptance is asserted. The estimate aggregates the entire renderer addition.

### Parser selection and dependency review

Selected exact production versions (MIT): `mdast-util-from-markdown` 2.1.0,
`mdast-util-gfm-table` 2.0.1, `micromark-extension-gfm-table` 2.1.2 and
`micromark-util-decode-string` 2.0.1. The npm registry was queried before installation;
Node 24.21.0 and Vite 8 consume their ESM modules, and the table adapter supports the parser's
2.x line. Manifests/lockfile are synchronized. Installation's audit returned zero known
vulnerabilities; that is a dated registry result, not a security guarantee.

Primary package documentation: [from-markdown](https://github.com/syntax-tree/mdast-util-from-markdown),
[table adapter](https://github.com/syntax-tree/mdast-util-gfm-table),
[table tokenizer](https://github.com/micromark/micromark-extension-gfm-table).
Alternatives: an in-house parser was rejected for CommonMark/security maintenance; full
`remark-gfm` would enable unapproved extensions; HTML generation plus sanitization retains an
unnecessary HTML-string path. The chosen AST is rendered through an explicit React node allowlist:
raw HTML is literal text, images become safe links, headings begin at h2, code uses CodeBlock,
and tables use MUI's shared table style. URL admission checks character references, iterative
percent decoding, whitespace/control removal and standard URL parsing. Backslashes are rejected
before Markdown escape decoding. No HTML/raw plugin, highlighter, HTML injection or automatic
image request is introduced. Original implementation was AI-assisted with Codex; package
attributions/licenses are retained in installed packages and the lockfile.

### Upstream availability on the implementation baseline

| Provider | Consumed implementation verified in source | Added functional scope |
| --- | --- | --- |
| CO06 #172 | owner Course/Chapter/Lesson operations in CourseManagement/Structure services/controllers | none |
| CO09 #175 | relational/file material lifecycle, owner tree metadata, protected download and shared HTTP transport | none |
| SH05 #154 | existing S3 FileStorage adapter, lifecycle/validation and configuration | none |
| AD07 #165 | owner graph read and 204 map/unmap | none |
| CO12 #176 | existing Course/Lesson publication event producer integration | none; materials retain their contract's event-silence semantics |
| FE03 #56 | existing HTTP/auth/session/router foundation | none |
| FE32 #224 | Teaching navigation/start and shell navigation registry | none |
| FE18 #203 | shared graph client/context and mapping operations | none; FE14 provider retains refresh barriers for its consumers |
| FE34 #255 | shared semantic Light/Dark/System theme foundation | none |

Only FE08 adds a missing upstream product capability. The additive cancellation handle in
`useAssessmentEditor` supports FE14's existing operation runner; it adds no FE15 product journey
and preserves its current callers. Test-only `E2E_MATERIAL_FILES=1` opts the disposable E2E setup
into the Compose-pinned MinIO server/initializer, random per-run identities and the existing
intent/ID/volume cleanup protocol. It publishes no storage port and makes no production-policy,
release or deployment change. Existing E2E runs leave this opt-in off.

### FE14 targeted independent-review remediation — 26 November 2026

The Leader explicitly authorized additive remediation of PR #279 after independent
`REQUEST_CHANGES` at `e4d7eb3febfad8f89db1b9091c7931ed3308654a`: FE14-IR-01
(P1, sent work lost on child unmount), IR-02 (P2, encoded URL semantics) and IR-03
(P2, nested reference definitions). The original implementation and A1–A4 audit
above remain historical evidence; they do not close these subsequent findings.
This is author remediation with Codex assistance, not independent acceptance.

Within CT16.final-state F1, the existing Course composing owner now registers a
single operation/controller before dispatch. Course and mapping mutation runners
share its synchronous entry barrier. A child can disappear without aborting that
owner's sent request; its callbacks are still discarded. Course/session replacement
or owner teardown aborts and invalidates the old work. Explicit upload cancellation
and the owner's "Stop waiting" action still signal cancellation, never rollback.
Known settlement establishes the floor of an authoritative Course/graph GET; an
older GET cannot answer it. Failed recovery retains the floor and supports explicit
read retry. Settings acknowledgments are recorded by the surviving owner; drafting
can continue after acknowledgment, but another dependent write waits for recovery.

Unknown/cancelled transport outcomes retain a visible uncertainty notice even
after inspection. CT16/CT37 permit resource inspection before a new deliberate
attempt; that inspection does not prove termination of an unobserved transaction.
There is no mutation replay, transaction-status endpoint, idempotency key, global
store/provider, timeout or browser-history blocker. Native Back/Forward retains
DESIGN A15's limitation. Leaving the whole composing owner still follows CT16
session/cancellation rules; this correction guarantees child-route survival.

Course metadata/publication, Chapter/Lesson authoring, all eight material operations
and Lesson–Skill mapping use the applicable owner. S20 mapping shares the same
lifecycle; other S20 multi-step skill/prerequisite work keeps its existing ownership
and checks the shared entry barrier. FE15 question/quiz and FE16 assignment writes
do not replace Course/Lesson/material/mapping state and retain their existing
operations. Their Course/skill selectors still consume the shared authoritative
reads. D12-3 ownership, D1/D1-R1/D1-R2 acknowledgment and A3/N1 deliberate graph
retry remain in place; no unrelated assessment operation was migrated.

Markdown keeps the approved parser stack. Security inspection decodes bounded
layers and compares admitted reference kind, effective scheme and origin against
the browser-parsed emitted destination. It emits the original mdast destination,
preserving encoded path/query/fragment data and CommonMark entity/escape handling.
Backslashes and malformed source encodings remain rejected. Definitions are
collected recursively in document order using mdast's normalized identifiers and
first-definition precedence. Images remain alt-text links; HTML remains literal.
Executed verification and the CodeQL test-helper correction are appended to
`docs/testing.md`. No effective contract, OpenAPI, dependency or governance rule
was changed.
