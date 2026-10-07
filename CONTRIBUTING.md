# Contributing to CodeMastery

This guide defines the contribution workflow for CodeMastery, the
Mastery-Based Programming Learning Platform for Beginner University Students
developed for CO3103 – Programming Integration Project, Semester 261.

## Before You Start

1. Read [AGENTS.md](AGENTS.md) for source precedence, scope restrictions, and the
   Blocker Protocol, then use [README.md](README.md) for navigation/current status.
2. Read the relevant [requirements](docs/requirements.md),
   [architecture](docs/architecture.md), [repository structure](docs/repository-structure.md),
   [module-boundary ADR](docs/decisions/001-modular-monolith-module-boundaries.md),
   [data model](docs/data-model.md), and [adaptive design](docs/adaptive-learning.md).
   Canonical originals have the authority defined in AGENTS.md.
3. Inspect target files, current configuration, and existing staged/unstaged
   changes. Preserve work that is unrelated to the task.
4. Follow [docs/setup.md](docs/setup.md) for Java 25 LTS, the Maven Wrapper, and
   local environment configuration, plus frontend Node/npm setup and commands.
   Local Compose runtime instructions are available; E2E and deployment remain
   pending.
5. Confirm the authorized file scope and required contracts before editing.
   Establish a test baseline when relevant implemented tests are available.

Approved design, implemented foundation, and planned work must be distinguished.
Module, test, API, migration, frontend, and infrastructure filenames currently
include empty scaffolds; a filename supplies no missing contract.

If required behavior, schema, API, cross-module communication, configuration,
security, deployment, or business policy is missing or conflicting, **stop before
editing affected files**, report `BLOCKED`, identify the missing information and
why it is needed, and request the smallest clarification. Follow the
[Blocker Protocol](AGENTS.md#blocker-protocol). Do not invent a project-specific
value or select a framework default to resolve the missing decision.

Governance edits require explicit authorization. Original reference artifacts
are immutable unless their modification is explicitly requested.

## Repository Structure and Code Organization

Use [docs/repository-structure.md](docs/repository-structure.md) for exact
locations. Preserve the approved directories, packages, module boundaries, and
filenames.

| Change | Approved location |
| --- | --- |
| Backend features | Owning module under `backend/src/main/java/com/codemastery/modules/`. |
| Schema migrations / backend tests | `backend/src/main/resources/db/migration/` / `backend/src/test/`. |
| Frontend / E2E | Approved feature structure under `frontend/` / tests and fixtures under `e2e/`. |
| Compose / deployment | `infrastructure/`; service Dockerfiles stay in `backend/` or `frontend/`. |
| Repository collaboration / workflows | `.github/`. |
| Seed data / materials | `sample-data/seed/` / `sample-data/materials/`. |
| Documentation / API specification | `docs/` / `docs/api/openapi.yaml`. |

These locations describe responsibility, not completed functionality. Do not add
parallel structures or relocate files for stylistic preference.

## Development Workflow

Use a documented issue/task or approved requirement, a focused branch,
implementation, relevant local validation, a pull request, code review, and
merge into `main`. Initial CI is implemented: backend runs `./mvnw verify` from
`backend/`, and frontend runs `npm ci` then `npm run build` from `frontend/`.
Feature-specific automated test coverage remains future work.

- Create a dedicated branch from `main` for each logical change; avoid direct
  commits to `main` in the normal workflow.
- Keep the branch current when `main` advances before review.
- Keep changes focused and preserve attributable contribution history.
- Coding agents must follow AGENTS.md and task-specific authorization for Git
  operations. A branch/commit/PR convention does not authorize staging, committing,
  pushing, merging, or rewriting history.

## Branch Naming

Use lowercase `type/short-description` names with hyphen-separated descriptions.

| Prefix | Purpose |
| --- | --- |
| `feature/` | New functionality. |
| `fix/` | Bug fix. |
| `docs/` | Documentation. |
| `refactor/` | Behavior-preserving restructuring. |
| `test/` | Automated tests. |
| `chore/` | Build, CI, dependencies, tooling, or maintenance. |

Examples: `feature/course-enrolment`, `fix/enrol-hidden-course`,
`docs/update-setup`, `test/mastery-routing`.

## Commit Messages

Use Conventional Commits: `type(scope): description`. Supported types are
`feat`, `fix`, `docs`, `refactor`, `test`, and `chore`; the branch
prefix `feature/` corresponds to commit type `feat`.

Use concise English, an affected module/technical scope where appropriate, and
the contributor's own Git identity. Keep each commit focused. Examples:
`feat(auth): add password reset flow` and
`docs(readme): clarify foundation status`.

## Pull Requests

Complete [.github/PULL_REQUEST_TEMPLATE.md](.github/PULL_REQUEST_TEMPLATE.md).
Explain the final behavior/change and its purpose, link the issue or requirement,
and identify affected modules/files and any unresolved limitation.

- Report exact validation commands, working directories, and actual results.
  State when a test was not run, unavailable, or unnecessary.
- Explain configuration, dependency, migration, API, security, and cross-module
  changes where applicable, including the approved contracts they follow.
- Update affected documentation in the same logical change.
- Include appropriate evidence for visible UI changes when implemented.
- Record actual AI assistance and third-party service use when disclosure is
  affected; do not fabricate tools, services, or results.

Before review, inspect the diff, run applicable checks, verify scope/secret
handling, and account for relevant changes in `main`. Address review feedback
within the PR. Mark inapplicable template items `N/A` with a reason rather than
claiming a check passed.

## Issue Templates

Use the [feature/task template](.github/ISSUE_TEMPLATE/feature.md) for new work and
the [bug-report template](.github/ISSUE_TEMPLATE/bug-report.md) for defects.
Reference the relevant approved user story/acceptance criterion or requirement
when one exists. Describe current behavior, requested behavior, authorized scope,
dependencies, and missing contracts. Do not treat an unimplemented scaffold as
an existing user flow.

## CI Expectations

[The CI workflow](.github/workflows/ci.yml) defines independent Backend and
Frontend validation jobs on `ubuntu-24.04`. It runs for pull requests targeting
`main`, pushes to `main`, and manual dispatch. Backend uses Temurin 25 with
`./mvnw verify`; frontend uses Node 22 with `npm ci` and `npm run build`, which
already includes typechecking.

Run relevant supported local checks and report their results. Resolve failures
from the actual workflow, and report remote CI success only when a run has
completed successfully. There are no executable backend feature tests yet, so
CI build success does not prove business behavior or PostgreSQL integration.
Do not invent required branch-protection status checks. E2E CI and deployment
automation remain unimplemented.

## Backend Guidelines

The approved backend is one Spring Boot 4.1.1 application on Java 25 LTS.
The six modules are `auth`, `course`, `assessment`, `adaptive`,
`interaction`, and `admin`. Their implementation remains scaffolded.
Follow [ADR 001](docs/decisions/001-modular-monolith-module-boundaries.md) for
ownership, encapsulation, approved interactions, and layering:

| Layer | Responsibility |
| --- | --- |
| `api` | Controllers, request/response DTOs, and API input validation. |
| `application` | Use cases, orchestration, transactions, and approved ports/events. |
| `domain` | Domain models and business rules/invariants. |
| `infrastructure` | Persistence adapters and technical integrations. |

Keep business logic out of controllers and persistence entities out of REST
responses. Enforce backend authorization independently of frontend guards and
use centralized error handling where applicable. Cross-module communication
requires an approved public application interface, port, or event; do not access
internals or invent a new interface to avoid a blocker.

REST contracts must be approved before dependent implementation. The current
OpenAPI file is empty. When public API behavior changes, synchronize controllers,
DTOs, frontend clients/types, and OpenAPI.

Before a dependency change, inspect `pom.xml`, check existing capability,
confirm the approved need and compatibility, prefer current BOM management,
avoid overlapping libraries, and explain any new production dependency.
A new technology/architectural choice requires approval.

## Adaptive Learning Development Rules

[docs/adaptive-learning.md](docs/adaptive-learning.md) records the approved
rule-based design and deferred implementation contracts; it does not establish
an implemented engine.

Future implementation must preserve course-scoped acyclic skills, the exact four
mastery states, configurable approved thresholds, explainable recommendations,
canonical course order, and the prerequisite no-hard-block rule. Quiz results
drive real-time mastery updates; manual file-submission grading does not.
Mastery and related `LessonProgress` changes must be atomic. Interaction owns
in-app notifications, and reporting does not own adaptive decisions.

Use approved event/public contracts and deterministic tests. Concrete thresholds,
scoring/routing formulas, difficulty mappings, event payloads, and execution
details remain deferred. Update the adaptive design when approved behavior changes.

## Frontend Guidelines

React + TypeScript + Vite + Material UI and React Flow are approved. Frontend
build/package configuration and the minimal Material UI bootstrap are
implemented. React Flow is installed but unused. Routes, guards, authentication
UI, API clients/integration, feature pages, and Mastery Map remain unimplemented.

Use Node.js satisfying `^22.12.0 || ^24.0.0` and npm. Install and validate from
`frontend/`:

```bash
cd frontend
npm ci
npm run build
```

`npm run build` runs typechecking before Vite build. Use `npm run typecheck`
alone for a narrower check when a production build is unnecessary.

Run `npm run dev` from that directory for local development, or
`npm run preview` after building to inspect the production bundle. Keep
`package-lock.json` tracked with the manifest; dependencies and build output
remain ignored. No lint or frontend test script is configured, and frontend
automated tests remain pending. Initial CI validates installation and build.

`VITE_API_BASE_URL` is optional public build-time configuration. Vite loads
environment files from the repository root; the bootstrap makes no API requests
and supplies no fallback URL.

Follow the approved feature-oriented structure; place only genuinely shared code
under `shared/`. Reuse implemented components and routing mechanisms when they
exist. Synchronize clients/types with approved APIs, preserve responsive behavior,
and keep all user-facing text in English. Frontend guards never replace backend
authorization. Never expose secrets in frontend source or `VITE_*` variables.
Use commands from implemented package configuration when available.

## Local Runtime Contributions

Use [the local Compose workflow](docs/setup.md#local-docker-compose-runtime)
from the repository root. Copy `.env.example` to ignored `.env` and replace all
required placeholders. Independently populate matching `POSTGRES_*`/`DB_*`
values for disposable local development; keep MinIO root and application
identities separate. Never include secrets or fully rendered Compose configuration
in PR evidence.

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

Report actual startup, Flyway, storage-provisioning, and frontend-serving checks
when changing local runtime configuration. `minio-init` must complete successfully
before backend startup. Rebuild the frontend after changing the public
`VITE_API_BASE_URL` build input. Startup and MC object checks do not demonstrate
feature APIs, authorization, backend S3 integration, or browser API integration.

After validation, remove containers while preserving named volumes:

```bash
docker compose --env-file .env \
  -f infrastructure/docker-compose.yml down
```

`down -v` is destructive and deletes local PostgreSQL and MinIO data;
do not use it as normal validation cleanup. This local foundation does not
replace the approved API/schema/security/module contracts required before
implementing a feature. Applied V001–V006 remain immutable; later schema changes
require NEW migrations.

## Database and Migration Guidelines

[docs/data-model.md](docs/data-model.md) preserves the conceptual model and records
the approved initial physical PostgreSQL baseline. V001–V006 implement that
baseline and are no longer empty scaffolds. Future entity mappings must match
its columns, types, nullability, constraints, and indexes; feature contracts remain
required before dependent implementation.

Schema evolution after this baseline requires NEW Flyway migrations. Never edit
an applied or possibly applied migration, including V001–V006; obtain approval
for future physical changes and synchronize the data model.

Flyway owns schema evolution under `backend/src/main/resources/db/migration/`.
Use new versioned migrations; never edit a migration that may have been applied.
Keep migrations, JPA mappings/entities, repositories, and constraints consistent
and test relied-upon persistence behavior. Update the data model with approved
changes. Keep seed/demo SQL under `sample-data/seed/` and materials under
`sample-data/materials/`, separate from schema migrations.

## Object Storage Guidelines

MinIO/S3-compatible storage is approved for slides, attachments, and submissions.
Domain data and file references belong in PostgreSQL. Storage implementation
remains scaffolded.

Use the approved storage abstraction in its approved location and enforce
authorization/ownership before protected file operations. Backend application
credentials use `STORAGE_ACCESS_KEY` and `STORAGE_SECRET_KEY`; MinIO root/admin
credentials are separate. Local Compose provisions the approved bucket-scoped
application policy; production storage policy and feature object-key/lifecycle
rules still require their approved contracts. Video uses external embed
links; hosting/transcoding is outside scope.

## Testing Guidelines

Every behavior change needs relevant automated tests unless testing is genuinely
unnecessary. Use the approved JUnit Jupiter 6 + Mockito backend stack. The exact
JUnit Jupiter version follows Spring Boot 4.1.1 dependency management (currently
6.0.3). Use integration tests for persistence, transactions, module integration,
configuration, or infrastructure behavior. Adaptive tests must be deterministic.
Playwright is approved for main E2E flows when E2E configuration is implemented.

Use the Maven Wrapper for backend validation:

```bash
cd backend
./mvnw test
./mvnw verify
```

Unit tests belong in the approved module test locations outside the integration
package; Surefire excludes `**/integration/**`. Integration tests belong under
`backend/src/test/java/com/codemastery/integration/`, including approved
subdirectories, and end in `IntegrationTest.java` to match Failsafe selection.
Future integration tests should extend/use `AbstractIntegrationTest` when
applicable, preserving its shared container lifecycle instead of introducing
incompatible per-class container management or persistent reuse.

Docker is required once concrete Testcontainers tests execute. The abstract
foundation and test profile exist, but feature test files and E2E configuration
remain empty. Current verification compiles the harness without exercising
PostgreSQL. See [docs/testing.md](docs/testing.md) for lifecycle, isolation,
coverage limitations, and planned suites, and [docs/setup.md](docs/setup.md) for
prerequisites. `./mvnw verify -DskipITs` skips Failsafe execution while still
compiling integration-test sources; CI uses full `verify`.

Run the narrowest relevant validation first. Report executed checks accurately,
and distinguish a successful build from feature-test coverage. For documentation
changes, appropriate link/content/diff checks can be sufficient; explain why
automated behavior tests were unnecessary. Required checks that cannot run must
be reported with their limitation, not claimed successful.

## Secrets and Sensitive Data

Real environment files, passwords, tokens, keys, storage credentials, and real
demo/lecturer credentials must never be committed or included in issues, logs,
or screenshots. Redact sensitive evidence and personal/academic records.
Use [.env.example](.env.example) as the safe template and keep local `.env`
files untracked.

Follow [the environment contract](docs/setup.md): PostgreSQL `POSTGRES_*`
initialization and backend `DB_*` credentials are distinct, as are MinIO
`MINIO_ROOT_*` administration and backend `STORAGE_*` credentials. They are
not implicitly interchangeable. Existing defaults are not approved production
or business policy.

Update the template and setup documentation when supported variables change.
If a secret is exposed, notify the team, coordinate rotation/revocation and
authorized remediation, and remember that deletion or ignore rules do not remove
committed history. Do not perform unauthorized history rewriting.

Respect privacy and content copyright. Use original or properly licensed
materials, and share actual demo credentials only through the approved submission
or communication channel.

## Documentation Changes

Keep documentation synchronized with the same logical change and describe only
actual implementation, approved design, and explicitly pending work.

| Change | Relevant documentation |
| --- | --- |
| Setup/environment | `docs/setup.md`, `.env.example`; README when entry-point/status information changes. |
| Approved architecture/module boundaries | `docs/architecture.md`, `docs/decisions/`; governance changes require explicit authorization. |
| Data/schema | `docs/data-model.md`. |
| Adaptive behavior/evaluation | `docs/adaptive-learning.md`. |
| Testing / deployment | `docs/testing.md` / `docs/deployment.md` when implemented. |
| Public APIs | `docs/api/openapi.yaml` and affected clients/types. |
| Product overview/current status/stack | `README.md`. |

Grading documentation, reports, slides, presentation, demo, and Q&A must be in
English. Preserve the course-required disclosure of actual AI-assisted
development and third-party services, including purpose and use. See
[the known disclosure](README.md#ai-and-third-party-service-disclosure).
Do not imply the adaptive engine uses AI/ML.

## Definition of Done

Apply the following to the change; identify inapplicable or unavailable items and
their reasons instead of checking them as passed.

- [ ] Requested behavior/documentation is complete within the authorized scope.
- [ ] Mandatory requirements, approved design/structure, and contracts are respected.
- [ ] Relevant tests/checks were added or updated where needed and actually passed.
- [ ] Required implemented CI checks passed, or current CI unavailability is reported.
- [ ] Affected API, data-model, environment, and other documentation is synchronized.
- [ ] No secrets, sensitive local files, or unrelated changes were introduced.
- [ ] No unsupported project-specific decision or unapproved dependency/technology was introduced.
- [ ] AI/third-party disclosure is accurate where affected.
- [ ] Review feedback and unresolved limitations/blockers are accounted for.
- [ ] The change is focused, reviewable, and ready for the normal team review/merge workflow.
