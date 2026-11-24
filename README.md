# CodeMastery

Mastery-based programming learning for beginner university students.

CodeMastery is a course-based, self-paced web application for introductory
programming, developed for CO3103 at HCMUT. Its intended difference is skill-level
mastery: learners receive explainable guidance to continue, test out, or revisit
prerequisites, while instructors follow their progress. That adaptive journey is
still being built; the implemented capabilities are listed below.

[![Backend](https://github.com/chi-thanh-nguyen/codemastery/actions/workflows/ci-backend.yml/badge.svg?branch=main&event=push)](https://github.com/chi-thanh-nguyen/codemastery/actions/workflows/ci-backend.yml)
[![Frontend](https://github.com/chi-thanh-nguyen/codemastery/actions/workflows/ci-frontend.yml/badge.svg?branch=main&event=push)](https://github.com/chi-thanh-nguyen/codemastery/actions/workflows/ci-frontend.yml)
[![E2E](https://github.com/chi-thanh-nguyen/codemastery/actions/workflows/ci-e2e.yml/badge.svg?branch=main&event=push)](https://github.com/chi-thanh-nguyen/codemastery/actions/workflows/ci-e2e.yml)

[Setup](docs/setup.md#prerequisites) · [Contributing](CONTRIBUTING.md) ·
[Documentation](#documentation-index) · [Security](SECURITY.md)

## What works today

- Learners can register, sign in, restore a session, browse published courses,
  join or leave a course, and view their enrolled courses. Password recovery has
  backend and frontend support; delivery requires the documented SMTP configuration.
- Backend APIs support Course, Chapter and Lesson authoring/publication, Learner
  lesson reads, completion/progress and resume, Instructor skill graphs, question
  banks, and quiz composition/publication. Most of these newer APIs have no
  production frontend screen yet.
- Adaptive foundations include deterministic mastery calculation, skill graph
  validation, categorical mastery persistence and append-only recommendation
  history. Quiz-driven mastery updates, next-lesson recommendations and the
  learner Mastery Map are not integrated.

Adaptive learning is rule-based inside the Java backend. It uses no ML model,
external AI inference service or separate Python service. Python Fundamentals is
sample course content, not the backend language.

## Current project status

Repository snapshot: 24 November 2026. “Implemented” describes code on `main`;
[test records](docs/testing.md) identify what was tested and at which revision.
Contract approval, implementation, passing tests, independent acceptance, release
publication and deployment are separate states.

| Area | Implemented | Remaining work or limitation |
| --- | --- | --- |
| Authentication | Learner registration, BCrypt passwords, locked-account rejection, memory-only access JWTs, rotating HttpOnly refresh cookies, server logout and password recovery | Account administration; operational recovery-delivery configuration. See [ADR014](docs/decisions/014-hybrid-refresh-session-authentication.md). |
| Course | Catalogue, membership, content persistence, Course guard/settings storage, authoring, lesson reads, progress and resume APIs | File-material delivery and production lesson/authoring UI. |
| Assessment | Question-bank and quiz authoring/publication APIs; internal quiz grading service | Learner attempt/submission flow, assignments and grading UI. |
| Adaptive | Skill-graph APIs, mastery calculation and mastery/history write persistence | End-to-end quiz-to-mastery execution, routing, placement, insights and learner Mastery Map. |
| Interaction and admin | Domain representations; service/API scaffolds | Q&A, notifications, reports and administration features. |
| Frontend | Landing page, sign-in/registration/recovery, read-only Profile, catalogue/detail, Learner home and My courses; Light, Dark and System themes | Lesson, assessment, mastery, Instructor and Administrator screens remain sample-data presentations at `/preview`. |
| Data and storage | Flyway V001–V009; synthetic Users, Courses, Lessons and text materials; local MinIO provisioning | Backend file adapter/delivery and final skill/question/learner-profile datasets. The [sample slides](sample-data/README.md#sample-materials) are repository-only. |

The [OpenAPI document](docs/api/openapi.yaml) contains both implemented and future
operations. Its declarations are not an endpoint inventory: compare the relevant
controllers, [security route rules](backend/src/main/java/com/codemastery/modules/auth/infrastructure/security/SecurityConfig.java)
and test evidence before relying on an operation. Empty scaffolds do not supply
missing contracts or functionality.

### Releases and deployment

The published `v0.1.0-preview.1` is a prerelease. At this snapshot,
`v0.1.0-rc.2` is a draft, not a published production release. See
[GitHub Releases](https://github.com/chi-thanh-nguyen/codemastery/releases).

The [23 November VM observation](docs/deployment.md#historical-vm-observation-on-23-november-2026-read-only)
records an older preview running on Azure; the subsequent
[setup record](docs/deployment.md#phase-5-one-time-setup-executed--23-november-2026)
records bootstrap and recovery-key preparation, with timers disabled. These dated
records do not establish deployment or independent acceptance of current `main`.
Release admission, measured recovery, production deployment and lecturer-accessible
acceptance retain the gates in the operational documentation.

## Stack and architecture

One Spring Boot modular monolith contains `auth`, `course`, `assessment`,
`adaptive`, `interaction` and `admin`. Cross-module access follows approved public
interfaces, ports and events; [ADR001](docs/decisions/001-modular-monolith-module-boundaries.md)
defines ownership and transaction boundaries.
Shared REST support provides problem details, validated pagination, correlation
IDs and password validation.

| Layer | Technology |
| --- | --- |
| Backend | Java 25 LTS, Spring Boot 4.1.1, Spring Data JPA, Bean Validation, Spring Security/JWT, Actuator and structured logging |
| Frontend | TypeScript, React, Vite, Material UI, React Router; React Flow for example/preview maps |
| Data | PostgreSQL and Flyway; MinIO through its S3-compatible API for approved file storage |
| Validation and delivery | JUnit Jupiter 6 (Boot-managed), Mockito, Testcontainers, Vitest, Playwright, Docker Compose and GitHub Actions |

Exact versions and commands live in [the backend build](backend/pom.xml),
[Maven Wrapper configuration](backend/.mvn/wrapper/maven-wrapper.properties),
[frontend manifest](frontend/package.json) and [E2E manifest](e2e/package.json).
See the [architecture](docs/architecture.md) and [data model](docs/data-model.md)
for the approved design. Video uses external embed links; hosting/transcoding is
out of scope.

## Quick start

Follow [Development Setup](docs/setup.md#prerequisites) for prerequisites and
configuration. The [local Compose guide](docs/setup.md#local-docker-compose-runtime)
runs PostgreSQL, MinIO, its initializer, the backend and the frontend.

1. Copy `.env.example` to the ignored `.env` and replace the required placeholders
   using the [environment contract](docs/setup.md#environment-file). Database
   initialization/runtime credentials and MinIO root/application credentials have
   distinct roles. Never put secrets in `VITE_*` variables.
2. From the repository root, validate configuration, build and start:

   ```bash
   docker compose --env-file .env -f infrastructure/docker-compose.yml config --quiet
   docker compose --env-file .env -f infrastructure/docker-compose.yml build
   docker compose --env-file .env -f infrastructure/docker-compose.yml up -d
   docker compose --env-file .env -f infrastructure/docker-compose.yml ps -a
   ```

3. Open [localhost:5173](http://localhost:5173). The fresh database has no accounts
   or courses; follow the [sample-data guide](sample-data/README.md) for synthetic
   demo data and its disposable-target safeguards.

Use `docker compose --env-file .env -f infrastructure/docker-compose.yml down`
to stop the stack while retaining volumes. `down -v` deletes local data.
For host development, use the [contributor commands](CONTRIBUTING.md#testing-guidelines).

## Testing and delivery

[Backend](.github/workflows/ci-backend.yml),
[Frontend](.github/workflows/ci-frontend.yml),
[E2E](.github/workflows/ci-e2e.yml) and [VM](.github/workflows/ci-vm.yml)
workflows validate pull requests and pushes to `main`. E2E runs the existing learner
journey on a disposable Docker stack across five browser/device projects.
CodeQL also scans the repository. Badges show workflow outcomes, not coverage,
security certification or production health.

[Release](.github/workflows/release.yml) builds and qualifies candidates and
performs admission-gated promotion into draft releases. [Deploy](.github/workflows/deploy.yml)
provides manually dispatched SSH operations with production-environment gates.
Their path-filtered PR jobs test the tooling; merging a PR does not deploy it.
Use [Testing](docs/testing.md) for commands, evidence and coverage limits, and
[Deployment](docs/deployment.md#phase-4-release-pipeline--23-november-2026) for
release verification, authority and operating procedures.

## Documentation index

| Start here | Reference |
| --- | --- |
| Requirements and authority | [Course brief (PDF)](docs/references/originals/project-requirements.pdf), [requirements](docs/requirements.md), [AGENTS.md](AGENTS.md) |
| System design | [Approved architecture (DOCX)](docs/references/originals/approved-architecture.docx), [architecture](docs/architecture.md), [repository structure](docs/repository-structure.md), [decision index](docs/decisions/README.md) |
| Features and contracts | [OpenAPI](docs/api/openapi.yaml), [data model](docs/data-model.md), [adaptive learning](docs/adaptive-learning.md), [UI/UX design](DESIGN.md) |
| Development and operations | [Setup](docs/setup.md), [environment template](.env.example), [testing](docs/testing.md), [deployment](docs/deployment.md), [sample data](sample-data/README.md) |

README is an entry point. Source precedence and the Blocker Protocol are defined
in AGENTS.md; immutable originals and effective scoped decisions retain their authority.

## Contributing, security and license

Read [CONTRIBUTING.md](CONTRIBUTING.md) before selecting work or opening a pull
request. Report vulnerabilities through the private channel in
[SECURITY.md](SECURITY.md), rather than public Issues.

Original CodeMastery code and documentation are available under the
[MIT License](LICENSE), copyright 2026 CodeMastery contributors. On 24 November
2026, the Project Leader confirmed the contributors' consent and authority to
license that work, including the absence of conflicting university obligations.
The grant excludes the original reference artifacts in `docs/references/originals/`,
the [sample slide deck](sample-data/README.md#permitted-use-and-provenance), and
third-party content. Those retain their existing rights and permissions.

Bundled fonts retain their notices: [Google Sans Flex](frontend/public/google-sans-flex-OFL.txt),
[JetBrains Mono](frontend/public/jetbrains-mono-OFL.txt) and [Poppins](frontend/public/poppins-OFL.txt).
Libraries and container components keep their own licenses, including the
[modified MinIO Client's AGPL and Corresponding Source obligations](docs/deployment.md#minio-client-licence-and-corresponding-source).
The MIT grant does not relicense these components or assets.

## Academic context and credits

CodeMastery is developed for CO3103 – Programming Integration Project, Semester
261, Software Engineering track, at Ho Chi Minh City University of Technology
(HCMUT). The approved roles are Learner, Instructor and Administrator. The course
requires business-logic tests, at least one main E2E flow, sample data, reproducible
setup, a lecturer-accessible deployment and disclosure of AI/third-party use.
An isolated adaptive calculation or preview is not completion of the advanced
component; its integrated journey and evaluation remain to be delivered.
Reports, grading documentation, presentation, demo and Q&A must be in English;
all user-facing interface text must also be in English.

The approved scope excludes code-execution sandboxes, AI chatbots, certificates,
payments, video hosting, email/SMS/push notifications, real-time chat, native mobile
apps, cross-course adaptation and plagiarism detection. Notifications are in-app
only; configured password-recovery email is a separate authentication capability.

Contributors are credited in the [contribution history](https://github.com/chi-thanh-nguyen/codemastery/graphs/contributors).
The frontend visual reference and its non-reuse boundaries are credited in
[Setup](docs/setup.md#frontend-setup-and-validation); sample material provenance is
recorded in the [sample-data guide](sample-data/README.md#content-provenance).

## AI and third-party service disclosure

<details>
<summary>Development assistance and third-party tools</summary>

The course requires disclosure of AI use, AI-assisted development, and
third-party services with their actual purposes. OpenAI Codex has assisted with
repository documentation, environment-contract synchronization, the frontend
build/bootstrap foundation, the backend integration-test/CI foundations,
the standalone container-build/Nginx foundation, the initial PostgreSQL
schema/Flyway foundation, and the local Compose/provisioning foundation during
development. AI-assisted development was also used for the Interim
deployment-privilege work and for the sample datasets, their verification and
documentation, and the revised sample slides; Anthropic Claude Code assisted with
the DP01 HTTPS ingress integration, its tests and its deployment documentation,
and with the DP01 MinIO initializer, its qualification tests and the MinIO artifact
review. Anthropic Claude Code also wrote the UI/UX specification ([DESIGN.md](DESIGN.md);
revisions r1 on 6 November 2026 through r6 on 8 November 2026) and implemented the frontend redesign (and on 7 November its
typography, logo-palette and navigation-indicator refinement and the frontend
Alpine pin refresh), the design preview, the
browser harness and the Playwright E2E suite (including its 19 November 2026 image-build and
sign-in failure diagnostics), using the Impeccable design skill and Playwright CLI locally; the redesign's visual reference is credited in
[setup](docs/setup.md#frontend-setup-and-validation).
Anthropic Claude Code also implemented the CO03 Course content-persistence
capabilities and the CO14 Course configuration guard and adaptive-settings storage
handoff, with their unit and PostgreSQL integration tests and testing documentation
([PR #262](https://github.com/chi-thanh-nguyen/codemastery/pull/262), 21 November 2026).
Anthropic Claude Code also audited and completed the AD06 Adaptive mastery and
recommendation-history write persistence: the write mechanism, its unit and PostgreSQL
integration tests, the CT17-P6 r8 runtime privileges and ADR016
([PR #263](https://github.com/chi-thanh-nguyen/codemastery/pull/263), 23 November 2026).
The team reviews this work through the normal pull-request process. This
disclosure describes development assistance; CodeMastery's approved adaptive
engine is rule-based and uses no AI/ML service.

Backend third-party libraries and their configured purposes are recorded in
`pom.xml`; frontend dependencies are recorded in `frontend/package.json` and its
lockfile. Local PostgreSQL/MinIO infrastructure uses the images identified in
[setup](docs/setup.md#services-and-local-urls), including the approved third-party
Golithus builds for local MinIO server/client use. The release initializer in
`infrastructure/minio/` builds a modified MinIO Client with its Corresponding Source
archive and third-party notices. Azure VM observations and setup are recorded in
the deployment documentation linked above; no external video host is identified
as already in use.
Contributors must record further actual tools,
services, uses, and content sources in the final project documentation without
inventing or concealing details.

The release pipeline and SSH deployment/recovery tooling were developed with
Anthropic Claude Code assistance, as recorded in
[ADR015](docs/decisions/015-release-pipeline.md) and
[ADR017](docs/decisions/017-ssh-continuous-deployment.md). Their tooling uses GitHub
Actions, Trivy, Sigstore artifact attestations, OpenSSH, OpenSSL, optional Azure CLI
integration and acme.sh. The 23 November VM records above supersede older statements
about deployment absence; they are dated evidence, not a current runtime check.

</details>
