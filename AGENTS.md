# CodeMastery Agent Instructions

## Purpose

This file defines repository-level rules for coding agents working on CodeMastery.

CodeMastery is a Mastery-Based Programming Learning Platform for beginner university students developed for CO3103 – Programming Integration Project, Semester 261.

The project scope, architecture, and repository structure are approved. Implement the approved system; do not redesign it.

These rules apply to the whole repository.

A more specific `AGENTS.md` in a subdirectory may add or override instructions for files within that subtree. Non-conflicting repository-level rules continue to apply.

## Core Rules

- Inspect before editing.
- Read only sources relevant to the task.
- Preserve approved requirements, architecture, module boundaries, and repository structure.
- Do not invent project-specific behavior, configuration, API contracts, schema details, thresholds, credentials, ports, URLs, or architectural decisions.
- Make the smallest complete change.
- Do not perform unrelated refactoring, cleanup, renaming, or restructuring.
- Do not expand the requested file scope without explicit user approval.
- Use repository-defined build, test, run, and deployment commands when available.
- Do not invent project commands or scripts.
- Standard non-destructive inspection commands may be used as needed.
- Never claim a test, build, command, or validation passed unless it was actually executed successfully.
- If required information is missing, ambiguous, or conflicting, stop before editing and follow the Blocker Protocol.

## Source-of-Truth Map

### Mandatory Requirements — WHAT

Canonical source:

`docs/references/originals/project-requirements.pdf`

Agent-readable derivative when implemented and approved:

`docs/requirements.md`

Use for mandatory functional scope, minimum roles, quality/security/testing/deployment requirements, advanced-component requirements, deliverables, evaluation constraints, and grading/language requirements.

A project decision must never violate a mandatory course requirement.

### Approved Architecture — HOW

Canonical source:

`docs/references/originals/approved-architecture.docx`

Agent-readable derivative when implemented and approved:

`docs/architecture.md`

Use for architectural style, selected technology stack, module responsibilities/interactions, adaptive-learning behavior, high-level data model, storage/infrastructure choices, and explicit non-goals.

When requirements allow several choices and the architecture has selected one, follow the approved architecture.

### Repository Structure — WHERE

Canonical source:

`docs/references/originals/final-repository-tree.docx`

Agent-readable source:

`docs/repository-structure.md`

Use for approved directories, packages, filenames, module locations, frontend feature locations, tests, migrations, infrastructure, and documentation locations.

Treat the approved structure as fixed unless the user explicitly approves a structural change.

### Current Build/Configuration — CURRENT STATE

Use implemented repository files for exact dependency/plugin versions, scripts, environment-variable names, profiles, ports, service names, CI jobs, and test commands.

Typical sources:

- `backend/pom.xml`
- `.github/workflows/ci.yml`
- `.env.example`
- `frontend/package.json` when implemented
- `e2e/package.json` when implemented
- `infrastructure/docker-compose.yml` when implemented

Current code/configuration does not silently override approved requirements, architecture, or structure.

If implementation conflicts with an approved source and no explicit approved change explains it, report the conflict and stop.

### Contribution Workflow

Use `CONTRIBUTING.md` for branch/commit/PR conventions, module boundaries, backend layering, migrations, testing, documentation synchronization, secret handling, and Definition of Done.

### README

Use `README.md` for overview/navigation only.

It must not override mandatory requirements, approved architecture, approved structure, or exact current configuration.

If README is stale, report it instead of changing implementation to match it.

Approved backend stack: Java 25 LTS + Spring Boot 4.1.1.

## Governance Documents

Governance files:

- `AGENTS.md`
- `docs/requirements.md`
- `docs/architecture.md`
- `docs/repository-structure.md`

Do not modify them merely to make implementation appear compliant.

Changes to governance files require explicit user authorization.

If implementation conflicts with a governance document:

1. do not rewrite the governance document;
2. do not silently reinterpret it;
3. report the conflict;
4. ask the user how to proceed.

Files under `docs/references/originals/` are immutable reference artifacts.

Do not edit, rename, replace, regenerate, or delete them unless explicitly requested.

If a Markdown derivative conflicts with its canonical original, report the discrepancy and stop.

## Blocker Protocol

If a task depends on missing, ambiguous, inconsistent, or unsupported information:

1. Stop before modifying affected files.
2. Report `BLOCKED`.
3. State the exact missing/conflicting information.
4. Explain why it is required.
5. Identify the source that should define it, if known.
6. Ask for the smallest clarification, decision, or file needed.
7. Do not continue the blocked work until the user provides the context.

Do not guess, infer, fabricate, or silently choose project-specific values.

Block when undefined choices affect API behavior, schema, thresholds, JWT policy, CORS, ports, deployment URLs, storage policies, credentials, production dependencies, modules/layers/services, cross-module behavior, security, grading, integration, deployment, or another contributor's work.

Do not choose a framework default merely because one exists when the choice can affect externally relevant behavior.

If uncertain, block and ask.

## Architecture Invariants

### System Style

CodeMastery is a Modular Monolith: one deployable Spring Boot application with clearly separated logical modules.

Do not introduce microservices or additional independently deployed backend services unless the architecture is explicitly revised.

### Backend Modules

Approved modules:

- `auth`
- `course`
- `assessment`
- `adaptive`
- `interaction`
- `admin`

Do not add, remove, merge, rename, or relocate architecture-level modules without approval.

### Backend Layering

Within each module:

- `api` — REST controllers and request/response DTOs
- `application` — use cases, application services, orchestration, transactions, approved ports/events
- `domain` — domain models, business rules, domain abstractions
- `infrastructure` — persistence adapters and technical integrations

Rules:

- keep business logic out of controllers;
- use DTOs at API boundaries;
- do not expose persistence entities through REST APIs;
- keep infrastructure concerns in infrastructure;
- enforce business invariants in domain/application logic;
- enforce backend authorization independently of frontend route guards;
- use centralized exception handling where applicable.

### Module Boundaries

Do not access another module's internals directly.

Cross-module communication must use an existing approved public application interface, port, or event mechanism.

Do not create a new cross-module interface, port, or event merely to avoid a blocker.

If the interaction is not defined by approved sources, stop and ask.

## Approved Technology Stack

- Frontend: TypeScript + React + Vite + Material UI
- Mastery Map: React Flow
- Backend: Java 25 LTS + Spring Boot 4.1.1
- Persistence: Spring Data JPA
- Validation: Bean Validation
- API: REST/JSON + OpenAPI + springdoc
- Security: Spring Security + JWT + BCrypt
- Database: PostgreSQL
- Migrations: Flyway
- Object Storage: MinIO via S3-compatible interface
- Infrastructure: Docker Compose + GitHub Actions
- Testing: JUnit 5 + Mockito + Playwright
- Observability: Spring Boot Actuator + structured logging

Use actual build/configuration files for exact dependency/plugin versions.

Do not upgrade, downgrade, replace, or add a parallel technology stack during an unrelated task.

## Critical Domain Invariants

### Adaptive Learning

- Rule-based only; no ML model, external AI service, or separate Python service.
- Skills belong to a course.
- Prerequisites form an acyclic directed graph within a course; cycles must be rejected.
- Mastery states are exactly `Unknown`, `Learning`, `Mastered`, `Weak`.
- Thresholds are configurable; never invent concrete values.
- Quiz results drive real-time mastery updates.
- Manual file-submission assignments do not directly trigger real-time mastery updates.
- Decisions are `continue`, `skip`/Test-out, or `remedial`.
- Canonical course order remains the baseline.
- Adaptive behavior does not rewrite course structure.
- A prerequisite gap must not hard-block an advanced lesson solely because mastery is missing.
- Recommendations must be explainable and traceable.
- Recommendation history preserves type, target lesson, reason, and timestamp.
- Assessment sends submitted-quiz information to Adaptive through the approved event mechanism.
- Mastery update and related `LessonProgress` update must be transactionally consistent.
- Interaction owns in-app notification creation/delivery.
- Admin/Reporting consumes learning data but does not own adaptive decisions.
- Adaptive behavior is scoped to one course.

### Database

PostgreSQL stores relational domain data.

Flyway owns schema evolution.

Migration path:

`backend/src/main/resources/db/migration/`

Use versioned migrations; keep migrations, JPA mappings, entities, repositories, and constraints consistent; never edit a migration that may already have been applied; correct it with a new migration; keep seed/demo data outside schema migrations.

Seed SQL:

`sample-data/seed/`

Sample learning materials:

`sample-data/materials/`

If exact schema details are undefined, block and ask.

### Object Storage

MinIO stores approved file-based content such as slides, attachments, and assignment submissions.

Keep relational domain data in PostgreSQL.

Use the approved backend storage abstraction and enforce authorization before serving or accepting protected files.

Video uses external embed links; hosting/transcoding is out of scope.

### Security

Authentication/authorization use Spring Security, JWT, and RBAC.

Passwords use BCrypt.

Locked accounts must not authenticate.

Never commit real `.env` files, passwords, tokens, API keys, JWT secrets, storage credentials, private keys, or real demo/lecturer credentials.

Never expose secrets through frontend `VITE_*` variables.

Use `.env.example` only as a placeholder template.

## Repository Structure

Follow `docs/repository-structure.md`.

Do not create new top-level directories, rename architecture-level directories/packages, relocate files for stylistic preference, introduce parallel structures, collapse module boundaries, or create duplicate abstractions where an approved location exists.

A filename in the tree defines location/responsibility, not permission to invent its contents.

## API, Frontend, and Documentation

### API

The project uses REST/JSON and OpenAPI.

Canonical specification when implemented:

`docs/api/openapi.yaml`

Do not invent public API contracts.

When a public API changes, synchronize controller behavior, DTOs, frontend clients/types, and OpenAPI.

If ambiguous, block and ask.

### Frontend

Follow the approved feature-oriented structure.

Keep feature-specific code in the owning feature and only genuinely shared code under `shared/`.

Frontend route guards are not a security boundary.

Project convention: keep all user-facing interface text in English.

This is stricter than the course minimum requirement, which only requires the interface and demo data to be consistent and clearly presentable in English.

Maintain responsive behavior.

Never expose secrets in frontend source or `VITE_*` variables.

Do not invent npm scripts; inspect `frontend/package.json` when it exists.

### Documentation

All project documentation used for grading must be in English.

The presentation, live demo, and Q&A must be conducted in English.

Demo-facing interface text and demo data must be consistent and clearly presentable in English.

Documentation must describe what actually exists.

Do not present planned behavior as completed behavior.

Update affected documentation in the same logical change when the relevant documentation file exists.

Preserve the course-required disclosure of AI use, AI-assisted development, and third-party services in final project documentation.

Record only tools, services, purposes, and uses that are actually known.

Do not fabricate, infer, or conceal disclosure details.

## Testing and Validation

Every behavior change should include relevant automated tests unless testing is genuinely unnecessary.

Backend: JUnit 5 + Mockito; add integration tests for persistence, transactions, module integration, configuration, or infrastructure behavior.

End-to-end: Playwright.

Adaptive-learning tests should be deterministic.

Use the Maven Wrapper from `backend/`.

Typical backend validation:

```bash
cd backend
./mvnw test
```

Broader verification when appropriate:

```bash
cd backend
./mvnw verify
```

Do not require global Maven.

Do not invent frontend, E2E, Docker, CI, or deployment commands; inspect actual repository configuration first.

## Explicit Non-Goals

Do not introduce without formal scope approval:

- code-execution sandbox;
- ML/AI chatbot or ML-based adaptive engine;
- certificates;
- payments;
- video hosting/transcoding;
- email/SMS/push notifications;
- real-time chat;
- native mobile applications;
- cross-course adaptive learning;
- plagiarism detection.

Current notifications are in-app only.

Approved roles:

- Learner
- Instructor
- Administrator

Do not add another project role without explicit approval.

## Dependency Policy

Before adding/changing a dependency:

1. Inspect existing build/package configuration.
2. Check whether the capability already exists.
3. Confirm it is justified by approved requirements/design.
4. Verify compatibility with the current runtime/framework.
5. Prefer existing framework/BOM version management where appropriate.
6. Avoid overlapping libraries.
7. Explain any new production dependency.

Do not add dependencies merely for convenience.

If a dependency represents a new technology or architectural choice, block and ask.

## Task Workflow

For every task:

1. Read this file.
2. Identify the exact requested scope.
3. Inspect target files and only necessary dependencies/callers/consumers.
4. Read relevant source-of-truth documents.
5. Check for missing context/conflicts.
6. If blocked, stop before editing.
7. Implement the smallest complete change.
8. Add/update relevant tests.
9. Run the narrowest relevant validation first; broaden only when justified.
10. Inspect the final diff.
11. Report changed files, purpose, validations actually run, results, and unresolved blockers/assumptions.

If the user restricts the task to a file/file set, do not modify anything outside that scope.

If another file is required, stop and ask permission to expand scope.

## Git and Repository Safety

Treat pre-existing uncommitted changes as user-owned.

Before editing in a Git repository, inspect `git status --short` when practical.

Do not discard, overwrite, revert, clean, or otherwise modify unrelated pre-existing changes.

If the target file already contains uncommitted changes that may conflict with the task, inspect them carefully and ask the user before overwriting or discarding any part of them.

Do not perform unless explicitly requested:

- `git add` / staging;
- commit;
- push;
- merge;
- rebase;
- reset;
- revert;
- force-push;
- branch deletion;
- history rewriting;
- remote modification;
- repository-rule/protection changes.

Do not use destructive commands merely to repair a local mistake.

Do not stage unrelated files.

Before a user-controlled commit, report changed files and validation status.

Follow `CONTRIBUTING.md` for branch, commit, PR, and review conventions.

## Definition of Done

A task is complete only when all applicable conditions are satisfied:

- requested behavior is implemented;
- mandatory requirements are respected;
- architecture/module boundaries and repository structure are preserved;
- no unsupported project-specific decision was invented;
- relevant tests were added/updated when appropriate;
- relevant validations were actually executed successfully;
- affected configuration templates/docs are synchronized when applicable;
- course-required AI/third-party disclosure remains accurate when final documentation is affected;
- no secret or sensitive local file was introduced;
- no unrelated changes are present;
- no unapproved dependency/technology/architecture change was introduced;
- unresolved issues are explicitly reported.

If any required condition cannot be satisfied because context is missing, ambiguous, or conflicting, the task is `BLOCKED` and the agent must ask the user for the missing information before proceeding.
