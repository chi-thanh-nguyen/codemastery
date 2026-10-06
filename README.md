# CodeMastery – Mastery-Based Programming Learning Platform for Beginner University Students

CodeMastery is an academic online learning platform for **CO3103 – Programming
Integration Project, Semester 261**, Software Engineering track. The approved
product supports course-based, self-paced introductory university programming
with Learner, Instructor, and Administrator roles.

The approved architecture combines a **Spring Boot Modular Monolith** backend,
a **React + TypeScript** frontend, **PostgreSQL** for relational domain data, and
**MinIO** through its S3-compatible API for files. Its advanced component is
**rule-based Mastery-Based Adaptive Learning** inside the backend. This
repository currently provides the foundation described below; the product
features are still to be implemented.

## Current Project Status

| Area | Current state |
| --- | --- |
| Governance | [AGENTS.md](AGENTS.md), [requirements](docs/requirements.md), [architecture](docs/architecture.md), [repository structure](docs/repository-structure.md), and the [module-boundary ADR](docs/decisions/001-modular-monolith-module-boundaries.md) exist. |
| Backend foundation | The Maven build, Maven Wrapper, dependencies/plugins, and Spring Boot application entry point exist. Feature-module, common, and storage implementation files remain empty scaffolds. |
| Runtime configuration | [application.yml](backend/src/main/resources/application.yml) defines backend configuration and optional local environment-file imports. Configuration does not establish implemented authentication, storage, scheduling, or business behavior. |
| Data model | [docs/data-model.md](docs/data-model.md) records conceptual entities and relationships. The approved initial 23-table PostgreSQL schema is implemented in V001–V006; JPA mappings and feature persistence remain pending. |
| Adaptive design | [docs/adaptive-learning.md](docs/adaptive-learning.md) records approved behavior, invariants, evaluation expectations, and deferred contracts. The adaptive engine is not implemented. |
| Environment/setup | [.env.example](.env.example) and [docs/setup.md](docs/setup.md) document approved variable scopes, current defaults, backend loading, and optional public frontend configuration. |
| Frontend | The React/TypeScript/Vite build foundation, manifest/lockfile, minimal Material UI bootstrap, React Flow dependency, and type-check/build commands are implemented. Routing, authentication UI, API client/integration, feature pages, and Mastery Map remain pending. |
| E2E | Package files, Playwright configuration, fixtures, and test files remain empty scaffolds. |
| CI and infrastructure | The initial GitHub Actions workflow defines Backend and Frontend validation jobs. Backend/frontend Dockerfiles implement standalone multi-stage builds with non-root runtimes and frontend Nginx static serving configuration. Compose, PostgreSQL/MinIO runtime integration, Compose environment mappings, and deployment remain pending. Configuration alone does not establish remote CI success or backend runtime integration. |
| Tests, API, and sample data | The backend integration-test base class and test profile exist. Feature tests remain empty; the abstract harness alone does not prove runtime database integration. OpenAPI and sample-data files remain empty. |
| Testing/deployment documentation | [docs/testing.md](docs/testing.md) documents the current lifecycle, foundation, CI, and planned suites. `docs/deployment.md` remains empty; setup commands are documented in `docs/setup.md`. |

Empty filenames reserve approved locations and responsibilities; they do not
define functionality or implementation contracts. This foundation is not yet a
complete runnable learning platform.

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
adaptive learning. Python Fundamentals is planned sample course content, not a
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
| Security | Spring Security, JWT via JJWT **0.13.0**, and BCrypt; application authentication/authorization is pending. |
| Object storage | MinIO/S3-compatible storage through AWS SDK **2.44.7**; storage adapter implementation is pending. |
| API documentation | REST/JSON, OpenAPI, springdoc **3.1.1**; public API contracts are pending. |
| Backend testing | Approved JUnit Jupiter **6**, managed by Spring Boot **4.1.1** (currently **6.0.3**), + Mockito; Surefire/Failsafe and JaCoCo **0.8.15** are configured. The integration-test foundation exists; feature suites remain unimplemented and meaningful coverage is not established. |
| Observability | Actuator and logging configuration, including ECS JSON console logging under `prod`; this profile is not complete production configuration. |
| Frontend | React **19.3.0**, TypeScript **7.0.2**, Vite **8.3.3**, Material UI **9.4.0**, and React Flow **12.12.0** are pinned in [package.json](frontend/package.json) and its npm lockfile. Minimal Material UI bootstrap is implemented; feature and React Flow usage remain pending. |
| Infrastructure/E2E | Initial GitHub Actions validation uses Temurin 25 and Node 22 on `ubuntu-24.04`. Docker Compose, cloud VM deployment, and Playwright executable configuration remain pending. |

Use the actual build/configuration files for dependency versions and executable
commands. Current defaults do not establish final security, deployment, or
business policy.

## Repository Structure

Follow the fixed [approved repository structure](docs/repository-structure.md).

| Location | Responsibility |
| --- | --- |
| `backend/` | Modular Monolith, configuration, migrations, and backend tests. |
| `frontend/` | Implemented build/bootstrap foundation with approved feature-oriented scaffolds. |
| `e2e/` | Playwright test/configuration scaffold. |
| `infrastructure/` | Compose and VM deployment scaffolds. |
| `.github/` | Collaboration templates and the workflow scaffold. |
| `sample-data/` | Reserved seed SQL and learning-material locations. |
| `docs/` | Approved design, setup documentation, references, and pending specifications. |

## Development and Setup

Start with [AGENTS.md](AGENTS.md) and [CONTRIBUTING.md](CONTRIBUTING.md).
Use [docs/setup.md](docs/setup.md) for prerequisites, environment-file loading,
variable scopes, and the limits of current setup support.

The minimal backend validation command supported by the build is:

```bash
cd backend
./mvnw test
```

The backend integration-test foundation exists, but feature test files remain
empty. A successful build alone does not demonstrate tested feature behavior or
exercise PostgreSQL through the abstract harness. `./mvnw verify` provides the
broader lifecycle; `./mvnw verify -DskipITs` skips Failsafe execution while still
compiling integration-test sources. See [testing](docs/testing.md) and
[setup](docs/setup.md) for conventions and Docker prerequisites.

For the frontend, use Node.js satisfying `^22.12.0 || ^24.0.0` and npm:

```bash
cd frontend
npm ci
npm run typecheck
npm run build
```

`npm run dev` starts the minimal bootstrap; `npm run preview` previews an
existing production build. `VITE_API_BASE_URL` has optional public build-time
typing and root environment-file loading, with no URL fallback or API consumer.
The bootstrap builds without an API URL and uses default Material UI styling.
`npm run build` already includes typechecking. Initial CI runs backend
`./mvnw verify` and frontend `npm ci` / `npm run build` in separate jobs for
pull requests targeting `main`, pushes to `main`, and manual dispatch. Frontend
features and automated tests, E2E, Compose, runtime PostgreSQL/MinIO integration,
and production deployment remain pending. Standalone container build commands
and frontend Nginx serving are documented in
[setup](docs/setup.md#standalone-container-builds). These images do not yet form
a working full application stack or prove backend integration with real
PostgreSQL/MinIO. Remote CI results must come from actual workflow runs.

## Documentation Index

[AGENTS.md](AGENTS.md) defines source precedence and the Blocker Protocol. Use
these documents for their specific responsibilities:

| Document | Purpose/status |
| --- | --- |
| [Course requirements PDF](docs/references/originals/project-requirements.pdf) / [requirements Markdown](docs/requirements.md) | Canonical mandatory requirements and readable derivative. |
| [Approved architecture DOCX](docs/references/originals/approved-architecture.docx) / [architecture Markdown](docs/architecture.md) | Approved design and stack. |
| [Repository structure](docs/repository-structure.md) | Approved file/module locations. |
| [ADR 001](docs/decisions/001-modular-monolith-module-boundaries.md) | Approved module boundaries and interactions. |
| [Data model](docs/data-model.md) | Conceptual model and approved initial PostgreSQL physical baseline. |
| [Adaptive Learning](docs/adaptive-learning.md) | Approved design, planned evaluation, and deferred decisions. |
| [Setup](docs/setup.md) / [environment template](.env.example) | Current development configuration contract. |
| [Testing](docs/testing.md) / [deployment](docs/deployment.md) | Current testing contract and planned suites / empty deployment placeholder. |
| [OpenAPI](docs/api/openapi.yaml) | Empty placeholder for the future public API specification. |
| [Contributing](CONTRIBUTING.md) | Branch, commit, PR, testing, and documentation workflow. |

Original files under `docs/references/originals/` are immutable references.
Governance changes require explicit authorization. README is an entry point and
does not override these sources.

## Scope, Security, and Delivery Obligations

The approved scope excludes code-execution sandboxes, ML/AI chatbots, certificates,
payments, video hosting/transcoding, email/SMS/push, real-time chat, native mobile
apps, cross-course adaptation, and plagiarism detection. Notifications are
in-app only.

Backend authorization, JWT authentication, BCrypt password hashing, locked-account
rejection, protected-file authorization, privacy, and licensed/original content
are implementation obligations, not completed features. Never commit real
secrets or demo/lecturer credentials, and never expose secrets through `VITE_*`.

The course requires automated business-logic tests, at least one main E2E flow,
sample data, reproducible setup, and a deployed application accessible to the
lecturer. These deliverables remain to be completed and evidenced. Reports,
grading documentation, the presentation, live demo, and Q&A must be in English;
all user-facing interface text must also be in English.

## AI and Third-Party Service Disclosure

The course requires disclosure of AI use, AI-assisted development, and
third-party services with their actual purposes. **OpenAI Codex has assisted with
repository documentation, environment-contract synchronization, the frontend
build/bootstrap foundation, the backend integration-test/CI foundations,
the standalone container-build/Nginx foundation, and the initial PostgreSQL
schema/Flyway foundation during development.** This disclosure describes
development assistance; CodeMastery's approved adaptive engine is rule-based
and uses no AI/ML service.

Backend third-party libraries and their configured purposes are recorded in
`pom.xml`; frontend dependencies are recorded in `frontend/package.json` and its
lockfile. Infrastructure integration remains pending. No hosted provider,
deployed service, or external video host is identified as already in use.
Contributors must record further actual tools,
services, uses, and content sources in the final project documentation without
inventing or concealing details.
