# Contributing to CodeMastery

Start with an authorized task, keep the change focused, and submit a pull request
against `main`. CodeMastery is a university project with approved requirements,
architecture and technical contracts; a public repository does not make every
proposed feature part of its scope.

## Before you start

1. Read the [project status](README.md#current-project-status) and
   [AGENTS.md](AGENTS.md) for source precedence and the Blocker Protocol.
2. Read the relevant [requirements](docs/requirements.md),
   [architecture](docs/architecture.md), [repository structure](docs/repository-structure.md)
   and effective records in the [decision index](docs/decisions/README.md).
   Approval applies to the stated slice, not automatically to its whole feature.
3. Follow [Setup](docs/setup.md#prerequisites). Host development needs Java 25 LTS
   and the checked-in Maven Wrapper, or Node.js `^22.12.0 || ^24.0.0` and npm for
   the frontend. Integration/E2E tests need Docker; E2E also needs Python 3 and
   the pinned Playwright browsers. No global Maven installation is required.
4. Inspect target files and `git status --short`. Preserve unrelated local work.

Use an existing assigned Issue or explicit Project Leader task. Confirm its file
scope, dependencies and implementation authorization before editing. A contract
approval, Project status, design preview or scaffold alone is not that authorization.
For new work, use the [task](.github/ISSUE_TEMPLATE/feature.md) or
[bug-report](.github/ISSUE_TEMPLATE/bug-report.md) template and agree scope first.
Keep discussion respectful and give reviewers reproducible evidence.

If behavior, schema, API, configuration, security or a cross-module interaction
is missing or conflicting, stop before editing the affected files. Report
`BLOCKED`, identify the missing source/decision and ask for the smallest
clarification under the [Blocker Protocol](AGENTS.md#blocker-protocol).
Governance and structural changes require explicit authorization; original
reference artifacts remain immutable.

## Branches and commits

Create one branch per logical change from current `main`. For example:

```bash
git fetch origin main
git switch -c docs/update-setup origin/main
```

Members with push access can branch in this repository; external contributors
use a fork. Keep the branch current with `main` through the agreed team workflow;
coordinate before rewriting a branch another person uses. Agents still need the
Git-operation authorization specified in AGENTS.md.

Use lowercase `type/short-description` with hyphen-separated descriptions:

| Prefix | Use |
| --- | --- |
| `feature/` | New functionality, such as `feature/course-enrolment` |
| `fix/` | Bug fixes |
| `docs/` | Documentation and repository policies |
| `refactor/` | Authorized behavior-preserving restructuring |
| `test/` | Automated tests |
| `chore/` | Build, CI, dependencies and maintenance |

`feature/` is the preferred feature prefix; `feat/` is a supported legacy alias
seen in existing branches. Do not rename active branches solely for consistency.
An explicitly authorized integration or stacked branch may use its agreed name;
explain its base and intended merge order in the pull request.

Use Conventional Commits, `type(scope): description`, in concise English. Types
are `feat`, `fix`, `docs`, `refactor`, `test` and `chore`; `feature/` branches use
commit type `feat`. Keep each commit focused. Examples:

```text
feat(course): add lesson completion
fix(auth): reject expired refresh sessions
docs(repo): standardize documentation and repository policies
```

### Authorship and squash history

Use your own Git name and an email associated with your GitHub account (a GitHub
noreply address is also suitable). Do not impersonate another contributor or
replace their attribution. Credit genuine joint work with agreed
`Co-authored-by` trailers; do not invent contributors. Academic contribution
history must remain attributable.

Only **squash and merge** is enabled. The pull request title becomes the default
squash title, so it must describe the final change using Conventional Commits.
The repository's default squash body is blank: the authorized merger must check
and retain any genuine co-author trailers in the final message. Intermediate
commits support review in the pull request; `main` receives one squash commit,
not each intermediate commit. Start the next change on a new branch from `main`.

## Implement and validate

Follow the approved locations: backend modules under
`backend/src/main/java/com/codemastery/modules/`, feature-owned frontend code
under `frontend/src/features/`, E2E under `e2e/`, and operational tooling under
`infrastructure/`. Use [the structure document](docs/repository-structure.md) for
exact paths; do not create parallel structures or unrelated refactors.

### Backend guidelines

The baseline is Java 25 LTS and Spring Boot 4.1.1 in a single modular monolith.
The modules are `auth`, `course`, `assessment`, `adaptive`, `interaction` and
`admin`. Follow [ADR001](docs/decisions/001-modular-monolith-module-boundaries.md):

- Keep controllers and DTOs in `api`, orchestration/transactions in `application`,
  business rules in `domain`, and persistence/technical adapters in `infrastructure`.
- Do not return persistence entities from REST controllers. Use centralized error
  handling and enforce authorization in the backend.
- Cross-module calls use approved public application interfaces, ports or events.
  Do not access another module's internals or invent an interface to avoid a blocker.
- Synchronize changed public behavior with OpenAPI, DTOs and affected frontend
  clients/types. OpenAPI includes future operations; check actual implementation.

Before changing dependencies, inspect the build, existing capabilities, approved
need and runtime compatibility. Prefer framework/BOM management, avoid overlapping
libraries and explain new production dependencies. A new technology choice needs
approval.

### Adaptive learning development rules

Follow [the adaptive specification](docs/adaptive-learning.md) and the effective
contract slices it references. Preserve course-scoped acyclic skills, the states
`Unknown`, `Learning`, `Mastered` and `Weak`, approved configurable thresholds,
explainable recommendations, canonical order and the prerequisite no-hard-block
rule. Quiz-driven mastery and related `LessonProgress` updates must be atomic;
manual assignment grading does not directly trigger them. Interaction owns in-app
notifications. Use deterministic tests; never invent a threshold or treat an
isolated calculator as the integrated adaptive journey.

### Frontend guidelines

Use TypeScript, React, Vite and Material UI within the approved feature structure.
Put only genuinely shared code in `shared/`. Read the affected screens, flows,
theme, accessibility, gap and gate records in [DESIGN.md](DESIGN.md) before UI work.
Consume the shared Light/Dark/System theme and semantic tokens; do not duplicate
theme state or introduce light-only styles. Preserve keyboard and responsive
behavior and keep user-facing text in English.

Use runtime-decoded data from approved APIs, preserve session/cancellation and
unknown-outcome rules, and activate production routes only under the established
[route rule](docs/setup.md#ct16-route-map-learner-home-composition-and-ct37-markdown-policy--8-november-2026).
`/preview` uses sample data and is not production integration evidence. Route
guards never replace backend authorization.

### Database and migration guidelines

Flyway owns schema evolution in `backend/src/main/resources/db/migration/`.
V001–V009 are present. Never edit a migration that may already have been applied;
use a new versioned migration for an authorized schema change. Keep migrations,
JPA mappings, repositories, constraints and [the data model](docs/data-model.md)
consistent. Test persistence, transactions and database privileges against
PostgreSQL where the change relies on them. Keep seed SQL in `sample-data/seed/`
and materials in `sample-data/materials/`, outside schema migrations.

### Object storage guidelines

Use the approved MinIO/S3 abstraction and enforce authorization before protected
file operations. Keep relational data and file references in PostgreSQL. Object
keys, lifecycle and policy must follow effective contracts; local provisioning
does not establish an implemented backend file-delivery feature. Video uses
external embeds; hosting/transcoding is outside scope.

### Testing guidelines

Every behavior change needs relevant automated tests unless they are genuinely
unnecessary. Start with the narrowest useful check; record commands, working
directories, results and limitations. [Testing](docs/testing.md) owns suite
placement, isolation rules, coverage obligations and dated execution evidence.

Backend, from `backend/`:

```bash
./mvnw test
./mvnw verify
```

`test` runs unit tests without Docker; `verify` adds Testcontainers PostgreSQL
integration tests and JaCoCo reporting. `./mvnw verify -DskipITs` skips integration
execution but still compiles its sources; it does not replace full verification.
Use Boot-managed JUnit Jupiter 6 and Mockito. Surefire excludes `integration/`;
Failsafe selects `*IntegrationTest.java` under the approved integration package.
Use `AbstractIntegrationTest` where applicable and preserve its shared lifecycle.
Do not introduce incompatible per-class containers or persistent reuse.

Frontend, from `frontend/`:

```bash
npm ci
npm test
npm run build
```

The build includes typechecking; `npm run typecheck` is available separately.
`npm run dev` starts Vite; `npm run preview` serves an existing build.
`npm run test:browser` is the optional browser harness. There is no lint script.
Keep npm lockfiles synchronized with manifests. Vite loads environment files
from the repository root; `VITE_API_BASE_URL` is public build-time configuration.

E2E, after `npm ci` in `frontend/`, from `e2e/`:

```bash
npm ci
npx playwright install chromium firefox webkit
npm test
```

Use the lifecycle-managed npm entry point; direct `playwright test` bypasses
ownership and is refused. The default suite builds a disposable stack from the
checkout. Use a local Docker daemon and Python 3; see
[the lifecycle guide](docs/testing.md#browser-harness-and-e2e-suite) for supported
hosts, cleanup and `npm run stack:down` recovery. Do not target another environment
without authorization. The CI workflow additionally installs browser OS dependencies
and runs lifecycle regression tests before all five browser/device projects.

For Compose changes, use [the local runtime guide](docs/setup.md#local-docker-compose-runtime),
check configuration with `config --quiet`, and verify startup, Flyway, initializer
completion and frontend serving as applicable. Rebuild after changing frontend
build inputs. Normal `down` preserves volumes; `down -v` deletes data. Local
startup does not establish security admission or production readiness.

For documentation-only changes, link/content checks and `git diff --check` can
be sufficient. Explain why behavior tests are unnecessary; never report unrun
checks as passed. Respect [.editorconfig](.editorconfig) for new edits and
[.gitattributes](.gitattributes) for shell-script/binary handling. Preserve nearby
style in files with mixed conventions; do not reformat or renormalize the repository.
Git attributes do not grant executable permission: preserve script modes.

## Prepare the pull request

Complete [the pull request template](.github/PULL_REQUEST_TEMPLATE.md), preserving
its sections. Link the Issue or authorizing task; explain the final change,
affected files/modules, contract basis and unresolved limitations. Include exact
validation commands and results, and screenshots for visible UI changes when
useful. Leave inapplicable checkboxes unchecked and explain `N/A`.

### Documentation changes

Update documentation in the same logical change within the authorized file scope:

| Change | Documentation to check |
| --- | --- |
| Setup/environment | `docs/setup.md`, `.env.example`, README entry points |
| Schema/persistence | `docs/data-model.md` |
| Public API | `docs/api/openapi.yaml`, affected clients and types |
| Adaptive behavior | `docs/adaptive-learning.md` |
| Tests / operations | `docs/testing.md` / `docs/deployment.md` |
| UI/UX | Effective `DESIGN.md` requirements and implementation handoffs |
| Product status | `README.md` |

Governance, DESIGN.md and technical-contract edits need their own authorization;
do not rewrite them to make an implementation appear compliant. Preserve immutable
originals and meaningful links/anchors. Describe planned, approved, implemented,
tested, independently accepted, released and deployed states separately.

Grading documents, reports, slides, presentation, demo and Q&A must be in English.
Maintain the [actual AI/third-party disclosure](README.md#ai-and-third-party-service-disclosure),
including tools, purposes and content provenance. Development assistance does not
make the product's adaptive engine AI-based.

### Secrets and sensitive data

Never commit real environment files, credentials, tokens, keys or demo/lecturer
passwords, or expose them in logs, screenshots, Issues or `VITE_*`. Use the ignored
`.env` and tracked [.env.example](.env.example). `POSTGRES_*` initialization and
`DB_*` runtime identities differ; MinIO `MINIO_ROOT_*` administration and backend
`STORAGE_*` identities also differ. Defaults are not production policy.

Report a suspected vulnerability or leaked credential privately through
[SECURITY.md](SECURITY.md). Coordinate rotation/revocation with the owner;
deleting a file does not remove committed history, and history rewriting requires
authorization. Share actual demo credentials only through the approved academic
submission/communication channel.

Respect privacy and content copyright. Submit original or properly licensed work,
retain required notices and record material provenance. Original project work uses
the [MIT License](LICENSE); the [README license scope](README.md#contributing-security-and-license)
identifies excluded references, assets and third-party components. A contribution
must not claim rights its author does not hold.

## Review and merge

[CODEOWNERS](.github/CODEOWNERS) identifies reviewers by path. GitHub uses the last
matching rule; not every path has an owner. Root policy files are owned by
`@chi-thanh-nguyen`. A PR author cannot approve their own PR; where that prevents
an eligible CODEOWNER review, raise the review-route blocker with the Project
Leader. Do not count a self-review or a merge-authority bypass as approval.

The active [main-quality-gates](https://github.com/chi-thanh-nguyen/codemastery/rules/24102200)
and [main-merge-authority](https://github.com/chi-thanh-nguyen/codemastery/rules/25288301)
rulesets were checked on 24 November 2026. Before merge, verify the live rules and
checks again. They currently require:

- A pull request, one approving review and CODEOWNER review where applicable,
  with all review threads resolved. Dismissing stale approvals and requiring a
  separate approval of the last push are currently disabled.
- An up-to-date branch, linear history, and successful required checks:
  `Backend`, `Frontend`, `E2E`, `CodeQL`, `VM deployment PostgreSQL and containment tests`,
  `VM deployment containerd image store`, `VM deployment script`,
  `VM runtime privileges with the real backend`, `VM HTTPS ingress` and
  `VM MinIO initializer qualification`.
- CodeQL scanning with the configured high-or-higher security alert and
  errors-and-warnings thresholds. Branch creation/deletion and force pushes are
  restricted on `main`; the separate update rule reserves integration to its
  configured authority through a pull request.

Address feedback and inspect the final diff. Green CI does not approve a deferred
contract, establish independent QA acceptance or authorize deployment. The
[Release](.github/workflows/release.yml) and [Deploy](.github/workflows/deploy.yml)
workflows have additional path-filtered tooling checks and manually gated
operations; ordinary CI never authorizes those operations. Only an authorized
merger completes squash-and-merge after the applicable gates are satisfied.

## Definition of done

- [ ] The authorized change is complete, focused and consistent with effective contracts.
- [ ] Relevant automated tests or documentation checks passed; unrun checks and limits are stated.
- [ ] API, data, configuration, UI handoffs and documentation are synchronized where affected.
- [ ] No unrelated work, secrets, generated artifacts or unapproved dependencies are included.
- [ ] Contribution attribution, material rights and AI/third-party disclosures are accurate.
- [ ] Review feedback is addressed; remaining blockers and current CI/review state are explicit.
