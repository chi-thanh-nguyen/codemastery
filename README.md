# CodeMastery – Mastery-Based Programming Learning Platform for Beginner University Students

CodeMastery is an academic online learning platform for **CO3103 – Programming
Integration Project, Semester 261**, Software Engineering track. The approved
product supports course-based, self-paced introductory university programming
with Learner, Instructor, and Administrator roles.

The approved architecture combines a **Spring Boot Modular Monolith** backend,
a **React + TypeScript** frontend, **PostgreSQL** for relational domain data, and
**MinIO** through its S3-compatible API for files. Its advanced component is
**rule-based Mastery-Based Adaptive Learning** inside the backend. The
repository contains the platform foundation and a first end-to-end Learner slice
(registration, sign-in, course catalogue and course membership). Most Instructor,
Administrator, assessment and adaptive features are still to be implemented.

## Current Project Status

Status values: **Implemented** (in place on `main`; code is covered by automated
tests), **Partial** (some approved capabilities implemented), **Planned**
(approved location or design only).

| Area | Status | Current state |
| --- | --- | --- |
| Governance and decisions | Implemented | [AGENTS.md](AGENTS.md), [requirements](docs/requirements.md), [architecture](docs/architecture.md), [repository structure](docs/repository-structure.md), [ADR 001](docs/decisions/001-modular-monolith-module-boundaries.md) and the bounded Interim contracts in [ADR 002](docs/decisions/002-interim-delegated-contracts.md). |
| Database | Implemented | The approved 23-table PostgreSQL schema is applied by Flyway migrations V001–V006; see [docs/data-model.md](docs/data-model.md). |
| Shared backend | Implemented | Problem-detail error handling, validated pagination, correlation IDs and password validation in `common`. The object-storage adapter is still an empty scaffold. |
| Authentication (`auth`) | Partial | Learner self-registration and sign-in (`POST /api/auth/register`, `POST /api/auth/login`), BCrypt `$2b$` cost 14, 15-minute HS256 JWTs and per-request account-status checks. Logout, password reset and account administration are planned. |
| Courses (`course`) | Partial | Public catalogue with search, filtering and pagination; course detail with the published syllabus; Learner join/leave and My Courses (`/api/courses/{courseId}/membership`, `/api/me/courses`); Course and Lesson access checks. Lesson viewing, progress, authoring and file materials are planned. |
| Adaptive learning (`adaptive`) | Partial | Local mastery calculation and state classification with deterministic tests. Skill graphs, quiz-driven mastery updates, recommendations and the Mastery Map are planned; see [docs/adaptive-learning.md](docs/adaptive-learning.md). |
| Assessment, interaction, admin | Planned | Approved scaffolds only. |
| REST API documentation | Partial | [docs/api/openapi.yaml](docs/api/openapi.yaml) specifies the six implemented Auth, catalogue and membership operations. |
| Frontend | Partial | React Router application with sign-in, registration, profile, course catalogue and detail, My Courses and the dashboard layout, with Vitest regression tests. Instructor, administrator, assessment and Mastery Map pages are planned. |
| Sample data | Partial | Synthetic Users, Courses, Lessons and text materials (SD01/SD02) with database-target protection, and one sample slide deck; see [sample-data/README.md](sample-data/README.md). Skill-graph, question and learner-profile datasets are planned. |
| Testing | Partial | Backend unit tests for Auth, Course, mastery calculation and shared REST; Testcontainers PostgreSQL integration tests for the catalogue, membership (including REST sign-in), shared REST and sample data; frontend Vitest suites. A Playwright learner-flow specification exists, but the `e2e/` package and configuration are not yet committed; see [docs/testing.md](docs/testing.md). |
| CI | Implemented | GitHub Actions runs the Backend (`./mvnw verify`), Frontend (`npm ci`, `npm test`, `npm run build`) and VM deployment-script jobs; CodeQL code scanning also runs on pull requests. |
| Local runtime and deployment | Partial | Docker Compose runs PostgreSQL, MinIO, the backend and the frontend locally ([setup](docs/setup.md#local-docker-compose-runtime)). An Interim VM deployment script and its decisions exist ([deployment](docs/deployment.md)); no public deployment has been performed or verified. |

Empty files reserve approved locations and responsibilities; they do not define
functionality or implementation contracts.

## Approved Product Scope

The approved roles and planned responsibilities are:

- **Learner:** enrol, study, take quizzes, submit assignments, track progress and
  mastery, and participate in lesson Q&A.
- **Instructor:** manage course content, skills, assessments, grading, and learner
  insights.
- **Administrator:** manage users/courses, handle content reports, and inspect
  metrics and audit information.

The scope covers accounts/RBAC, courses and materials, enrolment/progress,
assessment, in-app interaction/notifications, administration/reporting, and
adaptive learning. Python is the language of the sample course content, not a
separate backend language or service. User stories and acceptance criteria belong
in the [approved architecture](docs/architecture.md); mandatory course
obligations belong in the [requirements](docs/requirements.md).

Adaptive Learning uses course-scoped skills and an acyclic prerequisite graph,
the states `Unknown`, `Learning`, `Mastered`, and `Weak`, and explainable
`continue`, `skip`/Test-out, or `remedial` recommendations. It preserves
canonical course structure and must not hard-block advanced lessons solely
because prerequisite mastery is missing. Detailed rules, deferred thresholds,
and planned evaluation belong in [the adaptive design](docs/adaptive-learning.md).
No evaluation results or populated learner profiles are claimed here.

## Approved Architecture

The backend is one deployable application with six logical modules:

| Module | Approved responsibility |
| --- | --- |
| `auth` | Accounts, roles, authentication, API protection, and account status. |
| `course` | Courses, lessons, materials, enrolment, and learning progress. |
| `assessment` | Quizzes, assignments, attempts, grading, and results. |
| `adaptive` | Skills, mastery, adaptive decisions, and explainable recommendations. |
| `interaction` | Lesson Q&A, in-app notifications, and content reports. |
| `admin` | Administration, report handling, metrics, and audit logging. |

Layering, encapsulation, approved interactions, and atomic mastery/progress
updates are defined in [ADR 001](docs/decisions/001-modular-monolith-module-boundaries.md).
PostgreSQL owns relational data; MinIO is selected for slides, attachments, and
submissions. Videos use external embed links. Concrete public interfaces, event
payloads, and REST contracts require approval before dependent implementation.
The approved initial physical schema is recorded in the data model; future
schema evolution requires new Flyway migrations.

## Technology Stack

The backend baseline follows [pom.xml](backend/pom.xml) and the
[wrapper configuration](backend/.mvn/wrapper/maven-wrapper.properties).
Configured libraries do not establish completed feature integrations.

| Area | Approved/current baseline |
| --- | --- |
| Backend | Java **25 LTS**, Spring Boot **4.1.1**; Spring Framework **7**, Spring Security **7**, Hibernate **7**, Jackson **3**; Spring Data JPA and Bean Validation. |
| Build | Maven Wrapper **3.3.4**, configured Maven distribution **3.9.16**; no global Maven installation required. |
| Persistence | PostgreSQL and Flyway; initial V001–V006 schema migrations are implemented. |
| Security | Spring Security, JWT via JJWT **0.13.0**, and BCrypt; authentication is implemented for the current endpoints, and role-specific authorization for later features is pending. |
| Object storage | MinIO/S3-compatible storage through AWS SDK **2.44.7**; the storage adapter and file delivery are pending. |
| API documentation | REST/JSON, OpenAPI, springdoc **3.1.1**; the OpenAPI document covers the implemented endpoints. |
| Backend testing | Approved JUnit Jupiter **6**, managed by Spring Boot **4.1.1** (currently **6.0.3**), + Mockito and Testcontainers PostgreSQL; Surefire/Failsafe and JaCoCo **0.8.15** (reporting only, no thresholds). |
| Observability | Actuator and logging configuration, including ECS JSON console logging under `prod`; this profile is not complete production configuration. |
| Frontend | React **19.3.0**, TypeScript **7.0.2**, Vite **8.3.3**, Material UI **9.4.0**, React Router **7.18.4**, React Flow **12.12.0** and Vitest **5.0.5** are pinned in [package.json](frontend/package.json) and its npm lockfile. React Flow is not yet used. |
| Infrastructure/E2E | GitHub Actions uses Temurin 25 and Node 22 on `ubuntu-24.04`. Docker Compose (local) and an Interim VM deployment script exist; a committed Playwright configuration is pending. |

Use the actual build/configuration files for dependency versions and executable
commands. Current defaults do not establish final security, deployment, or
business policy.

## Repository Structure

Follow the fixed [approved repository structure](docs/repository-structure.md).

| Location | Responsibility |
| --- | --- |
| `backend/` | Modular Monolith, configuration, migrations, and backend tests. |
| `frontend/` | React application with feature-oriented modules; implemented and scaffolded features are listed above. |
| `e2e/` | Playwright learner-flow specification; package and configuration scaffolds. |
| `infrastructure/` | Local Compose runtime and the Interim VM deployment script. |
| `.github/` | Collaboration templates, CODEOWNERS and the CI workflow. |
| `sample-data/` | Synthetic seed SQL and sample learning material, with a [loading guide](sample-data/README.md). |
| `docs/` | Approved design, decision records, setup, testing, deployment and API documentation. |

## Development and Setup

Start with [AGENTS.md](AGENTS.md) and [CONTRIBUTING.md](CONTRIBUTING.md).
Use [docs/setup.md](docs/setup.md) for prerequisites, environment-file loading,
variable scopes, and the limits of current setup support.

Backend validation uses the Maven Wrapper:

```bash
cd backend
./mvnw test     # unit tests, no Docker required
./mvnw verify   # unit and integration tests; integration tests need Docker
```

`./mvnw verify -DskipITs` skips Failsafe execution while still compiling the
integration tests. See [testing](docs/testing.md) for suite placement and
isolation rules, and [setup](docs/setup.md) for prerequisites.

For the frontend, use Node.js satisfying `^22.12.0 || ^24.0.0` and npm:

```bash
cd frontend
npm ci
npm test
npm run build   # includes type checking
```

`npm run dev` starts the Vite development server and `npm run preview` serves an
existing production build. `VITE_API_BASE_URL` is a public build-time setting;
never place secrets in `VITE_*` variables.

The [local Compose workflow](docs/setup.md#local-docker-compose-runtime) runs the
full local stack. To populate a disposable local database with the synthetic
demonstration accounts and courses, follow
[sample-data/README.md](sample-data/README.md). CI results come from actual
GitHub Actions runs, not from local execution.

## Documentation Index

[AGENTS.md](AGENTS.md) defines source precedence and the Blocker Protocol. Use
these documents for their specific responsibilities:

| Document | Purpose/status |
| --- | --- |
| [Course requirements PDF](docs/references/originals/project-requirements.pdf) / [requirements Markdown](docs/requirements.md) | Canonical mandatory requirements and readable derivative. |
| [Approved architecture DOCX](docs/references/originals/approved-architecture.docx) / [architecture Markdown](docs/architecture.md) | Approved design and stack. |
| [Repository structure](docs/repository-structure.md) | Approved file/module locations. |
| [ADR 001](docs/decisions/001-modular-monolith-module-boundaries.md) | Approved module boundaries and interactions. |
| [ADR 002](docs/decisions/002-interim-delegated-contracts.md) | Bounded Interim technical contracts and their approval records. |
| [Data model](docs/data-model.md) | Conceptual model and approved initial PostgreSQL physical baseline. |
| [Adaptive Learning](docs/adaptive-learning.md) | Approved design, planned evaluation, and deferred decisions. |
| [Setup](docs/setup.md) / [environment template](.env.example) | Current development configuration contract. |
| [Testing](docs/testing.md) | Test strategy, suite placement and executed verification evidence. |
| [Deployment](docs/deployment.md) | Interim deployment decisions, privileges and operating procedure. |
| [OpenAPI](docs/api/openapi.yaml) | Specification of the implemented REST endpoints. |
| [Sample data](sample-data/README.md) | Synthetic dataset, safe loading procedure and material provenance. |
| [Contributing](CONTRIBUTING.md) | Branch, commit, PR, testing, and documentation workflow. |

Original files under `docs/references/originals/` are immutable references.
Governance changes require explicit authorization. README is an entry point and
does not override these sources.

## Scope, Security, and Delivery Obligations

The approved scope excludes code-execution sandboxes, ML/AI chatbots, certificates,
payments, video hosting/transcoding, email/SMS/push, real-time chat, native mobile
apps, cross-course adaptation, and plagiarism detection. Notifications are
in-app only.

JWT authentication, BCrypt password hashing and locked-account rejection are
implemented for the current endpoints. Role-specific authorization for later
features, protected-file authorization and privacy controls remain obligations
for those features. Never commit real secrets or demo/lecturer credentials, and
never expose secrets through `VITE_*`.

The course requires automated business-logic tests, at least one main E2E flow,
sample data, reproducible setup, and a deployed application accessible to the
lecturer. Automated tests, the Interim sample data and the local setup exist; the
complete E2E flow and the lecturer-accessible deployment remain to be completed
and evidenced. Reports,
grading documentation, the presentation, live demo, and Q&A must be in English;
all user-facing interface text must also be in English.

## AI and Third-Party Service Disclosure

The course requires disclosure of AI use, AI-assisted development, and
third-party services with their actual purposes. **OpenAI Codex has assisted with
repository documentation, environment-contract synchronization, the frontend
build/bootstrap foundation, the backend integration-test/CI foundations,
the standalone container-build/Nginx foundation, the initial PostgreSQL
schema/Flyway foundation, and the local Compose/provisioning foundation during
development.** AI-assisted development was also used for the Interim
deployment-privilege work and for the sample datasets, their verification and
documentation, and the revised sample slides; the team reviews this work through
the normal pull-request process. This disclosure describes
development assistance; CodeMastery's approved adaptive engine is rule-based
and uses no AI/ML service.

Backend third-party libraries and their configured purposes are recorded in
`pom.xml`; frontend dependencies are recorded in `frontend/package.json` and its
lockfile. Local PostgreSQL/MinIO infrastructure uses the images identified in
[setup](docs/setup.md#services-and-local-urls), including the approved third-party
Golithus builds for local MinIO server/client use. No production hosting provider,
public deployment, or external video host is identified as already in use.
Contributors must record further actual tools,
services, uses, and content sources in the final project documentation without
inventing or concealing details.
