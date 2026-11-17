# CodeMastery Repository Structure

**Status:** Approved
**Source:** `docs/references/originals/final-repository-tree.docx`

This document defines the approved repository structure for CodeMastery.
Agents and contributors must preserve this structure unless an explicit
architectural change is approved.

## Bounded Auth test inventory addition

On 2026-10-22 (Asia/Ho_Chi_Minh), Project Leader and repository Owner
@chi-thanh-nguyen explicitly approved exactly one file-inventory exception in the
instruction “CodeMastery — AU03 B2: Approve and Record Bounded Test Placement”:
`backend/src/test/java/com/codemastery/modules/auth/UserPersistenceTest.java`.
Its sole purpose is `UserMapper` unit and `UserRepositoryImpl` mocked-adapter
regression coverage for effective `AU03.mapping` and `AU03.operations` behavior.
[The bounded testing decision](testing.md#approved-au03-persistence-regression-test-placement)
records permitted cases and retained gates. This approved addition is shown in
the Auth test inventory below. The initial placement-only documentation record
did not authorize or deliver implementation; protected integration of this record
is pending. No other file or directory is added to the approved inventory. The immutable
canonical `final-repository-tree.docx` is unchanged; this explicit exception does
not reinterpret its original contents or approve broader structural changes.

The Leader's subsequent instruction “CodeMastery — PR #94: Implement Approved B2
Remediation Directly” on the same date prospectively authorizes implementation of
this file on PR #94 and these two documentation updates. The existing Auth test
CODEOWNERS remain @duyduyit and @chi-thanh-nguyen; no ownership rule changes.
Only mapper unit and mocked-adapter regression coverage is authorized. PostgreSQL
verification, unrelated CT14 proposals and protected review/integration remain
separate gates; original AU03 implementation authorization is not established by
this grant.

## Bounded AU04 test inventory addition

On 2026-10-22 (Asia/Ho_Chi_Minh), Project Leader and repository Owner
@chi-thanh-nguyen explicitly approved one additional file-inventory exception in
“CodeMastery — PR #95: Direct B2 Regression-Test Remediation”:
`backend/src/test/java/com/codemastery/modules/auth/StrongPasswordTest.java`.
The same instruction prospectively authorizes this test and the two documentation
updates on PR #95's existing branch. Its sole responsibility is AU04 password
predicate and Jakarta Bean Validation regression coverage, including Unicode/byte
bounds, whitespace, null/requiredness composition and supported `CharSequence`
inputs. Existing Auth CODEOWNERS remain @duyduyit and @chi-thanh-nguyen.
[The bounded testing decision](testing.md#approved-au04-password-validation-regression-test-placement)
records the exact scope and retained AU06/QA01 consumer/integration gates.

This single approved addition is shown in the Auth test inventory below, alongside
the separate AU03 exception. The immutable canonical `final-repository-tree.docx`
is unchanged; its original contents are not reinterpreted or rewritten. No other
filename, directory, ownership or structural change is approved. Unrelated pending
CT14 proposals retain their dispositions; original AU04 implementation authorization
and protected review/integration are not established by this prospective grant.

## Bounded FE01 inventory additions

The Leader/Owner's explicit D1–D4 delegation in “CodeMastery — Authorized FE01
Remediation and Professional Dashboard UI/UX Implementation”, supplied on
2026-10-23 (Asia/Ho_Chi_Minh), authorized necessary frontend presentation and
verification additions within the existing subtrees. [PR #99](https://github.com/chi-thanh-nguyen/codemastery/pull/99)
merged them as `89998c6e0be1764ed5ec8fcf101124a369a984f2` on 2026-10-24.
The precise additions, now included in the tree below, are:

- `frontend/src/shared/components/PresentationIcon.tsx` — original shared outline SVGs.
- `frontend/src/shared/components/presentation.browser.cjs` — optional existing-Playwright browser verification and evidence capture.
- `frontend/src/shared/components/presentation.test.tsx` — presentation/semantics/callback regressions.
- `frontend/src/shared/hooks/useDebounce.test.tsx` — real React state/timer lifecycle regressions.
- `frontend/src/shared/utils/format.test.ts` — caller-owned formatting and finite-duration regressions.

[The bounded FE01 record](testing.md#bounded-fe01-decisions-and-regression-coverage)
preserves authority, rationale, contracts, verification and exclusions without a
new ADR. The Leader's 2026-10-24 post-merge documentation instruction authorizes
this synchronization-debt correction; independent review/protected integration
remain pending. This does not retroactively satisfy pre-merge documentation
timing, rewrite the immutable canonical tree, change ownership/hierarchy, approve
unrelated CT14/CT37 scope or establish Issue #37 completion.

## Bounded UI/UX r1 frontend and E2E inventory additions — 6 November 2026

On 6 November 2026 (Asia/Ho_Chi_Minh) the Project Leader/Owner's instruction
“CodeMastery — Finalize UI/UX Implementation, Verification & Local Commit”
approved the frontend structure needed for the UI/UX redesign of the local
`DESIGN.md` revision `DESIGN-2026-11-06-r1`, including `frontend/src/app/preview/`,
and the Playwright E2E infrastructure. The additions, now shown in the tree below,
are:

- `frontend/src/app/preview/` — the isolated design preview served by the existing
  `/preview` route as a lazily loaded chunk: `PreviewApp.tsx` (screen registry and
  shell), `LearnerPreview.tsx`, `InstructorPreview.tsx`, `AdminPreview.tsx`,
  `PublicPreview.tsx`, `DesignReference.tsx` (S26), `previewKit.tsx` (query-parameter
  navigation, sample-data banner), `fixtures.ts` (synthetic sample data) and
  `PreviewApp.test.tsx`. Only the lazy import in `App.tsx` reaches it; it calls no
  API and reads no session.
- `frontend/src/app/theme/figtree-latin-wght.woff2` and `frontend/public/figtree-OFL.txt`
  — the self-hosted Figtree variable font (`@fontsource-variable/figtree` 5.2.10,
  SIL Open Font License 1.1) and its licence.
- `frontend/src/shared/components/` — `CodeBlock.tsx`, `DataTable.tsx`,
  `GuidanceStack.tsx`, `PathSpine.tsx`, `StatusPill.tsx`, `Tile.tsx`, `ViewRail.tsx`:
  presentation primitives used by several features.
- `frontend/src/features/catalogue/pages/LandingPage.tsx` (S25) and
  `frontend/src/features/learning/pages/LearnerHomePage.tsx` (S6) — presentation for
  routes that remain unapproved; neither is routed in production.
- Tests: `frontend/src/features/assessment/components/assessment.test.tsx`,
  `frontend/src/features/instructor/course-management/components/QuestionForm.test.ts`,
  `frontend/src/features/mastery/utils/masteryGraphLayout.test.ts`.
- `e2e/fixtures/local-stack.ts` (Playwright global setup for a disposable local
  stack), `e2e/fixtures/catalogue-fixture.sql` (deterministic catalogue fixture for
  that stack only) and `e2e/fixtures/stack-down.mjs` (removes leftovers of an
  interrupted run). The existing `e2e/package.json`, `package-lock.json`,
  `playwright.config.ts`, `fixtures/auth-fixture.ts` and `tests/learner-flow.spec.ts`
  now hold the suite; `tests/adaptive-flow.spec.ts` stays empty.

Removed as verified unused: `frontend/src/app/theme/plus-jakarta-sans-latin-wght.woff2`
and `frontend/public/plus-jakarta-sans-OFL.txt` (neither was listed in the tree).
`presentation.browser.cjs` keeps its path with new contents. [The testing record](testing.md#uiux-r1-implementation-verification--6-november-2026)
holds the decisions and verification. No top-level directory, backend module,
package, migration or API path changes. The immutable canonical tree is unchanged;
this is an explicit, prospective addition, not a reinterpretation of its original
inventory. CODEOWNERS is unchanged: the existing `/frontend/src/app/`,
`/frontend/src/shared/`, feature and `/e2e/` rules cover the additions, except
`frontend/public/figtree-OFL.txt`, which (like the existing `favicon.ico`) matches
no rule; adding one is a separate ownership decision.

### Production activation addition — 6 November 2026

The Leader's follow-up instruction of the same date (activate every component that
works on approved contracts) adds one file:
`frontend/src/features/mastery/components/ExampleMasteryMap.tsx`, the fixed,
labelled example map of the public landing page, loaded as its own chunk. No other
path changes; the existing `/frontend/src/features/mastery/` CODEOWNERS rule covers it.

### Typography and brand asset change — 7 November 2026

The Leader's instruction “CodeMastery — PR #209 CI Remediation, Typography, Premium
Brand Identity & Navigation Indicator Refinement” (7 November 2026) replaces the
Figtree assets listed above. Added in `frontend/src/app/theme/`:
`google-sans-flex-{latin,latin-ext,vietnamese}-wght.woff2`,
`jetbrains-mono-{latin,latin-ext,vietnamese}-wght.woff2` and `poppins-latin-800.woff2`;
added in `frontend/public/`: `google-sans-flex-OFL.txt`, `jetbrains-mono-OFL.txt` and
`poppins-OFL.txt`. Removed: `frontend/src/app/theme/figtree-latin-wght.woff2` and
`frontend/public/figtree-OFL.txt`. `favicon.svg` and `favicon.ico` keep their paths with
a new palette. The three new licence files, like `favicon.ico`, match no CODEOWNERS
rule; the theme files are covered by `/frontend/src/app/`. No other path changes.

### PR #209 review regression tests — 7 November 2026

The repository owner's instruction of 7 November 2026 to fix the PR #209
independent-review findings with regression tests, and to record the files they
add, adds three test files, now shown in the tree below:

- `frontend/src/shared/components/presentation.browser.test.ts` — Vitest lifecycle
  tests for `presentation.browser.cjs` (build marker, readiness, cleanup after a
  failed start); it runs with `npm test`.
- `e2e/fixtures/local-stack.test.mjs` — `node --test` tests for the readiness and
  backend-diagnostics helpers and the failure path of `fixtures/local-stack.ts`.
- `e2e/fixtures/stack-down.test.mjs` — `node --test` tests for `fixtures/stack-down.mjs`.

Both E2E tests use stub `docker` and `npx` executables and touch no Docker resource;
Playwright does not collect them (its `testDir` is `tests/`). The existing
`/frontend/src/shared/` and `/e2e/` CODEOWNERS rules cover them. No other path
changes; the immutable canonical tree is unchanged.

### Design specification and shared brand components record — 8 November 2026

The Leader's instruction "CodeMastery — Design Specification R6, Backlog Coverage and
Documentation Alignment" (8 November 2026) asks for the root UI/UX specification to be
tracked and for the following existing files to be recorded. The tree below now shows them:

- `DESIGN.md` — the root UI/UX specification (revision `DESIGN-2026-11-08-r6`). It was a
  local file excluded from Git; it is now tracked at the repository root. It matches no
  CODEOWNERS rule; adding one is a separate ownership decision.
- `frontend/src/shared/components/CourseMark.tsx` and its two official logo assets,
  `course-logo-python.svg` and `course-logo-typescript.svg` — the course mark and logo
  registry described in [setup](setup.md#frontend-setup-and-validation). They have been on
  `main` since 7 November 2026 and were not recorded here before.
- `frontend/src/shared/components/BrandLogo.tsx` — the logo artwork and wordmark lockup
  components (`BrandLogo`, `BrandWordmark`). The file has been on `main` since
  29 October 2026 and was not recorded here before.

This records files that already exist. It changes no code, path, ownership or
hierarchy, grants no retrospective approval, and leaves the immutable canonical tree
unchanged. The existing `/frontend/src/shared/` CODEOWNERS rule covers the frontend files.

### Shared read-only Mastery Map location — STRUCT-MASTERY-MAP-2026-11-08-r1 — DELEGATED_APPROVED structure decision, not yet implemented

Delegated technical approval by Claude Code under the Project Leader's explicit authorization for this task, “CodeMastery — Design Alignment Standardization, Contract Reconciliation & Issue Publication” (8 November 2026). It is not personal Leader review, CODEOWNER approval, implementation authorization, protected integration or runtime verification; those gates stay independent. No owner was consulted during this run; the owners named below review the record afterwards.

Resolves [DESIGN.md r6](https://github.com/chi-thanh-nguyen/codemastery/blob/4eb5b70be0c98df14e162b34d2c0314b02ab47b5/DESIGN.md#open-questions-do-not-block-design) open question 5.

**Decision.** The read-only Mastery Map graph presentation becomes shared presentation:
- `frontend/src/features/mastery/components/MasteryGraph.tsx` → `frontend/src/shared/components/MasteryGraph.tsx`;
- `frontend/src/features/mastery/utils/masteryGraphLayout.ts` and `masteryGraphLayout.test.ts` → `frontend/src/shared/utils/`.

The shared graph takes data through props only and defines its own node and edge view types; it imports no API, hook or feature type. The page, `SkillDetailPanel`, `NextRecommendedCard`, `PlacementPrompt`, `MasteryStateChip`, `ExampleMasteryMap`, hooks, API and types stay in `features/mastery`. Instructor learner detail composes the shared graph with its own CT33.insights data, the shared `DataTable` as its list equivalent and the shared `MasteryLegend`. The skill-graph editor canvas is unchanged; merging it is not decided.

**Implementation.** FE12 performs the move when it is authorized and records it in the tree; FE19 consumes the shared component. The `/frontend/src/shared/` CODEOWNERS rule covers the new paths.

**Alternatives considered.** Importing `features/mastery` from `features/instructor` (rejected: cross-feature internal import, CT16.final-state F1); a second read-only graph in learner-insights (rejected: duplication); a new shared feature directory (rejected: unnecessary structure level).

The tree below is unchanged until FE12 performs the move.

### Decision records directory record — 8 November 2026

The Leader’s “Batch 1 Complete Contract Approval & Integration Preparation” instruction additionally authorizes `docs/decisions/005-batch-01-contract-approvals.md` under this established directory. [ADR005](decisions/005-batch-01-contract-approvals.md) uses existing Java/test locations and nested types only; no code file, module or directory is added by its approval.

The Project Leader's 8 November 2026 instructions ("Pre-Approval Readiness Update" and the
LD-01 decision recorded in [ADR004](decisions/004-leader-approval-of-delegated-records.md))
authorize two new files under the existing `docs/decisions/` directory and ask for the
decision documentation to be indexed. The tree below now shows all of them:

- `docs/decisions/002-interim-delegated-contracts.md` and
  `docs/decisions/003-postgresql-alpine-su-exec-image.md` — existing ADRs, on `main` since
  PR #73 (11 October 2026) and 2 November 2026 respectively, not recorded here before.
- `docs/decisions/004-leader-approval-of-delegated-records.md` — new: the Leader's
  `LEADER-APPROVED-2026-11-08-r1` record.
- `docs/decisions/README.md` — new: index of decision records and definitions of the
  approval status labels.

This adds no directory, module or code path and moves no record. The existing
`/docs/decisions/` CODEOWNERS rule covers the files.

## Bounded Course unit-test placement correction

On 2026-10-26 (Asia/Ho_Chi_Minh), the Project Leader manually relocated
`CourseServiceTest.java` to
`backend/src/test/java/com/codemastery/modules/course/application/service/CourseServiceTest.java`
and explicitly authorized verification and the corresponding documentation updates.
The [ADR002 CT14.interim placement amendment](decisions/002-interim-delegated-contracts.md#ct14interim--delegated_approved)
records the previous location, this bounded supersession and its rationale.

This approved placement is included in the tree below. The directory matches the
existing `com.codemastery.modules.course.application.service` package and retains
the test's access to package-private Course assertions. The test contents and the
existing Course test CODEOWNERS, @TamiaoNguyen and @chi-thanh-nguyen, are unchanged.
The immutable canonical tree remains unchanged; this is an explicit placement
exception, not a reinterpretation of its original inventory. Other test paths,
contract decisions, test execution and protected review remain outside this
correction.

The Project Leader separately authorized retaining the existing
`EnrollmentServiceTest.java` relocation on 2026-10-29 (Asia/Ho_Chi_Minh) during
PR #114 remediation. [ADR002's Enrollment test placement amendment](decisions/002-interim-delegated-contracts.md#ct14interim--delegated_approved)
records the exact instruction and supersession of its former `modules/course/`
path by `modules/course/application/service/EnrollmentServiceTest.java`.
Existing test responsibilities, package-private access and Course CODEOWNERS
remain unchanged; no production behavior or other inventory change is approved.

## Bounded DP01 VM Compose override addition

On 2026-10-31 (Asia/Ho_Chi_Minh), the Project Leader/Owner explicitly approved
`infrastructure/vm/docker-compose.vm.yml` as the DP01 Interim release override,
together with the read-only `/etc/codemastery/tls` → `/etc/nginx/tls` certificate
directory bind it declares. `infrastructure/vm/deploy.sh` applies the override
on top of the unchanged local `infrastructure/docker-compose.yml`. [The DP01 record](deployment.md#leader-approved-path-bindings--31-october-2026)
preserves the exact authority and exclusions.

This single addition is shown in the infrastructure inventory below. The existing
`/infrastructure/` CODEOWNERS rule (@tihh647-ui and @chi-thanh-nguyen) already
covers it and is unchanged. The immutable canonical tree remains unchanged; this is
an explicit addition, not a reinterpretation of its original inventory. No other
file, host path, deployment, exposure or protected-review requirement is approved.

## Bounded DP01 MinIO initializer addition

On 2026-10-31 (Asia/Ho_Chi_Minh), the Project Leader @chi-thanh-nguyen directed
that the DP01 MinIO initializer executable and Dockerfile be placed under
`infrastructure/minio/`. The directory holds exactly `Dockerfile`, the image's
`minio-init.sh` executable and its `test-minio-init.sh` qualification suite.
[The DP01 MinIO record](deployment.md#dp01-minio-community-initializer--31-october-2026)
preserves the authority, behavior and exclusions.

These files are shown in the infrastructure inventory below. The existing
`/infrastructure/` CODEOWNERS rule (@tihh647-ui and @chi-thanh-nguyen) covers them
and is unchanged. The service Dockerfile rule for the backend and frontend is
unaffected: this image is storage provisioning infrastructure, not an application
service. The immutable canonical tree remains unchanged; this is an explicit
addition, not a reinterpretation of its original inventory. No release artifact,
storage technology change, deployment or protected-review exception is approved.

## Bounded CI workflow inventory replacement — 12 November 2026

On 2026-11-12 (Asia/Ho_Chi_Minh), Project Leader and repository Owner
@chi-thanh-nguyen explicitly authorized exactly one bounded workflow
file-inventory change in the instruction “CodeMastery — Phase 1:
Behavior-Preserving GitHub CI Workflow Reorganization”: `.github/workflows/ci.yml`
is replaced by `.github/workflows/ci-backend.yml`, `.github/workflows/ci-frontend.yml`
and `.github/workflows/ci-vm.yml`. The Backend job moves to `ci-backend.yml`, the
Frontend job to `ci-frontend.yml` and the six VM jobs to `ci-vm.yml`. Job IDs,
display names, steps, triggers and the `contents: read` permission are unchanged,
so the eight existing required status-check names are preserved.
[The CI section of the testing documentation](testing.md#ci) lists the jobs.

This replacement is shown in the `.github/` inventory below. The existing
`/.github/workflows/` CODEOWNERS rule (@chi-thanh-nguyen) covers the new files and
is unchanged. The immutable canonical tree remains unchanged; this is an explicit
replacement, not a reinterpretation of its original inventory. No new job, test
suite, E2E, release or deployment workflow, ruleset, required-check, CodeQL or
protected-review change is approved.

## Bounded VM CI test-script inventory addition — 12 November 2026

On 2026-11-12 (Asia/Ho_Chi_Minh), Project Leader and repository Owner
@chi-thanh-nguyen explicitly authorized, in the instruction “CodeMastery — Phase 2:
CI Maintainability, Test Extraction & Evidence Hardening”, the bounded addition of
CI-only test scripts under the existing `infrastructure/vm/tests/` directory. This
record adds exactly 18 scripts. Each holds, verbatim, the former inline `run:`
script of one step in `.github/workflows/ci-vm.yml`, and that step now invokes it
with `bash infrastructure/vm/tests/<script>` from the repository root:

| Job ID | Step | Script |
| --- | --- | --- |
| `deployment-script` | Check shell syntax and authorization gates | `test-deploy-syntax-and-authorization-gates.sh` |
| `deployment-script` | Check offline Compose and provisioning safety guards | `test-deploy-offline-compose-guards.sh` |
| `deployment-script` | Check the runtime privilege matrix and its contract record | `test-deploy-privilege-matrix-contract.sh` |
| `deployment-script` | Check env-file custody | `test-deploy-env-file-custody.sh` |
| `deployment-script` | Check preflight against rendered Compose | `test-deploy-preflight-rendered-compose.sh` |
| `deployment-script` | Check release archive identity models | `test-deploy-release-archive-identity.sh` |
| `deployment-script` | Check image identity through a real save/load round trip | `test-deploy-image-identity-roundtrip.sh` |
| `deployment-script-postgres` | Exercise the runtime privilege matrix on the real schema and failure containment | `test-postgres-privilege-matrix-containment.sh` |
| `deployment-script-postgres` | Exercise published-port verification against real containers | `test-postgres-published-ports.sh` |
| `deployment-script-postgres` | Check the PostgreSQL image refuses a data volume it did not initialize | `test-postgres-foreign-volume-guard.sh` |
| `deployment-script-postgres` | Check the PostgreSQL image is byte-clean and equivalent to its layered build | `test-postgres-image-layer-equivalence.sh` |
| `deployment-script-runtime` | Exercise real Auth, catalogue and membership under the approved matrix | `test-runtime-real-backend-privileges.sh` |
| `deployment-script-containerd` | Verify registry archives through real save/load round trips | `test-containerd-archive-roundtrip.sh` |
| `deployment-ingress` | Build the release and local images; reject invalid release origins | `test-ingress-release-image-build.sh` |
| `deployment-ingress` | Check the release image CA store against the deployed public chain | `test-ingress-release-ca-store.sh` |
| `deployment-ingress` | Exercise the HTTPS ingress against a stub backend | `test-ingress-stub-backend.sh` |
| `deployment-ingress` | Exercise the HTTPS ingress with the real backend | `test-ingress-real-backend.sh` |
| `deployment-ingress` | Run DP01 preflight with the committed override and certificate layout | `test-ingress-dp01-preflight.sh` |

These scripts are shown in the infrastructure inventory below. That listing shows
only the files this record adds. The directory's earlier files,
`test-frontend-stage-cleanup.sh` and `test-java-truststore.sh`, are not listed
there and this record does not change their status. The existing
`/infrastructure/` CODEOWNERS rule (@tihh647-ui and @chi-thanh-nguyen) covers the
new scripts and is unchanged. The immutable canonical tree remains unchanged; this
is an explicit addition, not a reinterpretation of its original inventory. Job IDs,
display names, the existing step names and their order, conditions, assertions and
required check names are unchanged. One step, “Check VM test script syntax”, is
added after checkout in the `deployment-script` job; it runs `bash -n` on every
script in the directory. No new job, test framework, directory, deployment, ruleset,
required-check or protected-review change is approved.

## Bounded AU07 file additions — 13 November 2026

On 2026-11-13 (Asia/Ho_Chi_Minh), Project Leader and repository Owner
@chi-thanh-nguyen explicitly approved, in the instruction "CodeMastery — PR #239
Leader Authorization, Contract Synchronization & PR Finalization", the following two
file additions for AU07
([PR #239](https://github.com/chi-thanh-nguyen/codemastery/pull/239);
`AU07-IMPL-2026-11-13-r1`,
[ADR006 §10.2](decisions/006-batch-02-contract-approvals.md#102-leader-decisions--au07-impl-2026-11-13-r1-and-ct17-p6-privileges-2026-11-13-r2)):

| File | Purpose |
| --- | --- |
| `backend/src/main/java/com/codemastery/modules/auth/infrastructure/email/SmtpPasswordResetEmailAdapter.java` | Auth-owned implementation of the CT02 delivery port: plain-text SMTP submission with mandatory STARTTLS, in a new `infrastructure/email/` directory of the owning Auth module |
| `backend/src/main/resources/db/migration/V007__create_password_reset_tokens.sql` | The NEW migration for the ADR006 §9.2 `password_reset_tokens` design, with no grant |

Both files are shown in the tree below. The existing Auth-module and migration-directory
CODEOWNERS rules cover them and are unchanged. The immutable canonical tree remains
unchanged; this is an explicit addition, not a reinterpretation of its original
inventory. No other directory, module or file and no execution of V007 against a
shared or production database are approved.

## Bounded FE24 E2E lifecycle inventory addition — 13 November 2026

On 2026-11-13 (Asia/Ho_Chi_Minh), Project Leader and repository Owner
@chi-thanh-nguyen explicitly authorized, in the instruction “CodeMastery — Phase 3:
Contract-Compliant Playwright E2E CI Integration”, the minimum tracked
launcher/coordinator, inventory/recovery helpers and regression tests under the
existing `e2e/fixtures/` directory needed to implement
`CT34-FIXTURES-2026-11-10-r2` ([ADR008 §10](decisions/008-batch-04-contract-approvals.md#10-f1f2-fixture-amendment--10-november-2026)).
This record adds exactly nine files:

| File | Responsibility | Why it is needed |
| --- | --- | --- |
| `e2e/fixtures/lifecycle.py` | Pre-entry coordinator: private registry, nonblocking `flock` keyed by the checkout's device/inode, output-root identity checks, run admission, the persisted run record, supervision of the runner and the recovery entry point; also the platform process-birth identity | §10.2 requires exclusivity before the Playwright CLI starts, with Python 3's standard-library `fcntl` |
| `e2e/fixtures/run-inventory.mjs` | Versioned, bounded, non-secret run inventory with durable atomic replacement and creation intents | §10.3 persistent inventory, written by the Node side of the lifecycle |
| `e2e/fixtures/host-identity.mjs` | Process-birth, host/boot/PID-namespace and recorded-directory identity checks | §10.3: a PID or a path alone is not ownership evidence |
| `e2e/fixtures/stack-recovery.mjs` | The single cleanup and recovery engine used by normal teardown, the coordinator's finalization and `npm run stack:down` | §10.3: normal cleanup and recovery use the same protocol |
| `e2e/fixtures/preview-server.mjs` | The run's Vite preview, hosted through the pinned Vite preview API, with a private run-scoped Unix-domain control channel | §10.3 cooperative self-termination of an orphaned preview |
| `e2e/fixtures/lifecycle.test.mjs` | `node --test` tests of the coordinator: exclusivity, aliases, isolated checkouts, blocked reuse, unsafe outputs | §10.4 acceptance cases |
| `e2e/fixtures/run-inventory.test.mjs` | `node --test` tests of the inventory protocol | §10.3 |
| `e2e/fixtures/stack-recovery.test.mjs` | `node --test` failure-injection tests of the recovery engine with a fake Docker CLI | §10.3 and §10.4 |
| `e2e/fixtures/preview-control.test.mjs` | `node --test` tests of the control channel with a real process and socket and a stub Vite module | §10.3 |

The existing `e2e/package.json` scripts, `e2e/playwright.config.ts`,
`fixtures/local-stack.ts`, `fixtures/stack-down.mjs`, `fixtures/local-stack.test.mjs`
and `fixtures/stack-down.test.mjs` keep their paths with new contents. The private
`~/.codemastery-e2e/` registry is generated local tooling state outside every
checkout, not a tracked path. No dependency, directory, top-level path, workflow,
ruleset, required check or CODEOWNERS rule is added or changed by this record; the
existing `/e2e/` rule (@heizseung and @chi-thanh-nguyen) covers the new files.
Playwright does not collect the tests (its `testDir` is `tests/`). The immutable
canonical tree remains unchanged; this is an explicit addition, not a
reinterpretation of its original inventory.
[The testing record](testing.md#fe24-ct34-r2-lifecycle-implementation--13-november-2026)
holds the design mapping and the verification actually performed.

## Bounded SH07 E2E CI workflow addition — 13 November 2026

Under the same instruction of 2026-11-13, the Project Leader explicitly authorized one
bounded workflow addition for SH07, `.github/workflows/ci-e2e.yml` (workflow `CI E2E`,
job `E2E`), implementing `CT34-CI-2026-11-09-r1`
([ADR008 §7](decisions/008-batch-04-contract-approvals.md#7-ct34ci)). This record adds
exactly two files:

| File | Responsibility |
| --- | --- |
| `.github/workflows/ci-e2e.yml` | The E2E job: locked installs, pinned browsers, lifecycle tests, the five-project suite against the run's own disposable stack, an `always()` recovery step and an informational summary |
| `e2e/fixtures/ci-evidence.mjs` | Writes that summary from an allowlist of facts (pinned versions, per-project counts, titles and skip reasons of non-passing tests, run ID, inventory state, image IDs, resource outcomes) and copies no error output, trace, report or log |

The existing `/.github/workflows/` (@chi-thanh-nguyen) and `/e2e/` (@heizseung and
@chi-thanh-nguyen) CODEOWNERS rules cover them and are unchanged. The eight existing
jobs, their names and the `main-quality-gates` ruleset are unchanged; `E2E` is not a
required check. No new action, action version, dependency, secret, permission or
artifact upload is introduced: the workflow reuses `actions/checkout@v7` and
`actions/setup-node@v7`. The immutable canonical tree remains unchanged; this is an
explicit addition, not a reinterpretation of its original inventory.

```text
codemastery/

├── .github/

    ├── workflows/

    │   ├── ci-backend.yml

    │   ├── ci-e2e.yml

    │   ├── ci-frontend.yml

    │   └── ci-vm.yml

    ├── ISSUE_TEMPLATE/

    │   ├── feature.md

    │   └── bug-report.md

    ├── CODEOWNERS

    └── PULL_REQUEST_TEMPLATE.md

├── backend/

│   ├── pom.xml

│   ├── mvnw

│   ├── mvnw.cmd

│   ├── .mvn/

│   │   └── wrapper/

│   │       └── maven-wrapper.properties

│   ├── Dockerfile

│   ├── .dockerignore

│   └── src/

│       ├── main/

│       │   ├── java/

│       │   │   └── com/

│       │   │       └── codemastery/

│       │   │           ├── CodeMasteryApplication.java

│       │   │           ├── common/

│       │   │           │   ├── api/

│       │   │           │   │   └── PageResponse.java

│       │   │           │   ├── config/

│       │   │           │   │   └── AppConfig.java

│       │   │           │   ├── exception/

│       │   │           │   │   ├── BusinessRuleException.java

│       │   │           │   │   ├── GlobalExceptionHandler.java

│       │   │           │   │   └── ResourceNotFoundException.java

│       │   │           │   ├── logging/

│       │   │           │   │   └── CorrelationIdFilter.java

│       │   │           │   └── validation/

│       │   │           │       └── StrongPassword.java

│       │   │           ├── infrastructure/

│       │   │           │   └── storage/

│       │   │           │       ├── FileStorage.java

│       │   │           │       └── s3/

│       │   │           │           └── S3FileStorage.java

│       │   │           └── modules/

│       │   │               ├── auth/

│       │   │               │   ├── api/

│       │   │               │   │   ├── controller/

│       │   │               │   │   │   ├── AuthController.java

│       │   │               │   │   │   └── UserProfileController.java

│       │   │               │   │   └── dto/

│       │   │               │   │       ├── AuthRequests.java

│       │   │               │   │       ├── AuthResponse.java

│       │   │               │   │       └── UserResponse.java

│       │   │               │   ├── application/

│       │   │               │   │   └── service/

│       │   │               │   │       ├── AuthService.java

│       │   │               │   │       ├── PasswordResetService.java

│       │   │               │   │       └── UserAccountService.java

│       │   │               │   ├── domain/

│       │   │               │   │   ├── model/

│       │   │               │   │   │   ├── AccountStatus.java

│       │   │               │   │   │   ├── Role.java

│       │   │               │   │   │   └── User.java

│       │   │               │   │   └── repository/

│       │   │               │   │       └── UserRepository.java

│       │   │               │   └── infrastructure/

│       │   │               │       ├── persistence/

│       │   │               │       │   ├── entity/

│       │   │               │       │   │   └── UserEntity.java

│       │   │               │       │   ├── mapper/

│       │   │               │       │   │   └── UserMapper.java

│       │   │               │       │   └── repository/

│       │   │               │       │       ├── UserJpaRepository.java

│       │   │               │       │       └── UserRepositoryImpl.java

│       │   │               │       ├── security/

│       │   │               │       │   ├── AuthenticatedUser.java

│       │   │               │       │   ├── JwtAuthenticationFilter.java

│       │   │               │       │   ├── JwtTokenProvider.java

│       │   │               │       │   ├── RestSecurityErrorHandler.java

│       │   │               │       │   └── SecurityConfig.java

│       │   │               │       └── email/

│       │   │               │           └── SmtpPasswordResetEmailAdapter.java

│       │   │               ├── course/

│       │   │               │   ├── api/

│       │   │               │   │   ├── controller/

│       │   │               │   │   │   ├── CourseCatalogueController.java

│       │   │               │   │   │   ├── CourseManagementController.java

│       │   │               │   │   │   ├── CourseStructureController.java

│       │   │               │   │   │   └── EnrollmentController.java

│       │   │               │   │   └── dto/

│       │   │               │   │       ├── CourseDetailResponse.java

│       │   │               │   │       ├── CourseProgressResponse.java

│       │   │               │   │       ├── CourseRequest.java

│       │   │               │   │       ├── CourseSearchRequest.java

│       │   │               │   │       ├── CourseStructureRequests.java

│       │   │               │   │       ├── CourseStructureResponses.java

│       │   │               │   │       └── CourseSummaryResponse.java

│       │   │               │   ├── application/

│       │   │               │   │   ├── service/

│       │   │               │   │   │   ├── CourseAccessService.java

│       │   │               │   │   │   ├── CourseCatalogueService.java

│       │   │               │   │   │   ├── CourseManagementService.java

│       │   │               │   │   │   ├── CourseStructureService.java

│       │   │               │   │   │   ├── EnrollmentService.java

│       │   │               │   │   │   ├── LearningMaterialService.java

│       │   │               │   │   │   └── LessonProgressService.java

│       │   │               │   │   └── event/

│       │   │               │   │       └── ContentPublishedEvent.java

│       │   │               │   ├── domain/

│       │   │               │   │   ├── model/

│       │   │               │   │   │   ├── Chapter.java

│       │   │               │   │   │   ├── Course.java

│       │   │               │   │   │   ├── Enrollment.java

│       │   │               │   │   │   ├── LearningMaterial.java

│       │   │               │   │   │   ├── Lesson.java

│       │   │               │   │   │   ├── LessonProgress.java

│       │   │               │   │   │   └── PublishStatus.java

│       │   │               │   │   └── repository/

│       │   │               │   │       ├── CourseContentRepository.java

│       │   │               │   │       ├── CourseRepository.java

│       │   │               │   │       └── EnrollmentRepository.java

│       │   │               │   └── infrastructure/

│       │   │               │       └── persistence/

│       │   │               │           ├── entity/

│       │   │               │           │   ├── ChapterEntity.java

│       │   │               │           │   ├── CourseEntity.java

│       │   │               │           │   ├── EnrollmentEntity.java

│       │   │               │           │   ├── LearningMaterialEntity.java

│       │   │               │           │   ├── LessonEntity.java

│       │   │               │           │   └── LessonProgressEntity.java

│       │   │               │           ├── mapper/

│       │   │               │           │   ├── CourseContentMapper.java

│       │   │               │           │   ├── CourseMapper.java

│       │   │               │           │   └── EnrollmentMapper.java

│       │   │               │           └── repository/

│       │   │               │               ├── ChapterJpaRepository.java

│       │   │               │               ├── CourseContentRepositoryImpl.java

│       │   │               │               ├── CourseJpaRepository.java

│       │   │               │               ├── CourseRepositoryImpl.java

│       │   │               │               ├── EnrollmentJpaRepository.java

│       │   │               │               ├── EnrollmentRepositoryImpl.java

│       │   │               │               ├── LearningMaterialJpaRepository.java

│       │   │               │               ├── LessonJpaRepository.java

│       │   │               │               └── LessonProgressJpaRepository.java

│       │   │               ├── assessment/

│       │   │               │   ├── api/

│       │   │               │   │   ├── controller/

│       │   │               │   │   │   ├── AssignmentController.java

│       │   │               │   │   │   ├── QuestionBankController.java

│       │   │               │   │   │   ├── QuizAttemptController.java

│       │   │               │   │   │   └── QuizController.java

│       │   │               │   │   └── dto/

│       │   │               │   │       ├── AssignmentRequests.java

│       │   │               │   │       ├── AssignmentResponses.java

│       │   │               │   │       ├── AttemptResponses.java

│       │   │               │   │       ├── QuestionRequest.java

│       │   │               │   │       ├── QuestionResponse.java

│       │   │               │   │       ├── QuizRequest.java

│       │   │               │   │       ├── QuizResponses.java

│       │   │               │   │       └── SubmitAttemptRequest.java

│       │   │               │   ├── application/

│       │   │               │   │   ├── service/

│       │   │               │   │   │   ├── AssignmentService.java

│       │   │               │   │   │   ├── AttemptService.java

│       │   │               │   │   │   ├── QuestionBankService.java

│       │   │               │   │   │   ├── QuestionSelectionService.java

│       │   │               │   │   │   ├── QuizGradingService.java

│       │   │               │   │   │   ├── QuizService.java

│       │   │               │   │   │   └── SubmissionService.java

│       │   │               │   │   ├── event/

│       │   │               │   │   │   ├── AssignmentGradedEvent.java

│       │   │               │   │   │   └── QuizSubmittedEvent.java

│       │   │               │   │   ├── port/

│       │   │               │   │   │   └── PracticeDifficultyProvider.java

│       │   │               │   │   └── scheduler/

│       │   │               │   │       └── AttemptAutoSubmitScheduler.java

│       │   │               │   ├── domain/

│       │   │               │   │   ├── model/

│       │   │               │   │   │   ├── Assignment.java

│       │   │               │   │   │   ├── Attempt.java

│       │   │               │   │   │   ├── AttemptAnswer.java

│       │   │               │   │   │   ├── DifficultyLevel.java

│       │   │               │   │   │   ├── Question.java

│       │   │               │   │   │   ├── Quiz.java

│       │   │               │   │   │   └── Submission.java

│       │   │               │   │   └── repository/

│       │   │               │   │       ├── AssignmentRepository.java

│       │   │               │   │       ├── AttemptRepository.java

│       │   │               │   │       ├── QuestionRepository.java

│       │   │               │   │       └── QuizRepository.java

│       │   │               │   └── infrastructure/

│       │   │               │       └── persistence/

│       │   │               │           ├── entity/

│       │   │               │           │   ├── AssignmentEntity.java

│       │   │               │           │   ├── AttemptAnswerEntity.java

│       │   │               │           │   ├── AttemptEntity.java

│       │   │               │           │   ├── QuestionEntity.java

│       │   │               │           │   ├── QuizEntity.java

│       │   │               │           │   ├── QuizQuestionEntity.java

│       │   │               │           │   └── SubmissionEntity.java

│       │   │               │           ├── mapper/

│       │   │               │           │   ├── AssignmentMapper.java

│       │   │               │           │   ├── AttemptMapper.java

│       │   │               │           │   └── QuizMapper.java

│       │   │               │           └── repository/

│       │   │               │               ├── AssignmentJpaRepository.java

│       │   │               │               ├── AssignmentRepositoryImpl.java

│       │   │               │               ├── AttemptJpaRepository.java

│       │   │               │               ├── AttemptRepositoryImpl.java

│       │   │               │               ├── QuestionJpaRepository.java

│       │   │               │               ├── QuestionRepositoryImpl.java

│       │   │               │               ├── QuizJpaRepository.java

│       │   │               │               ├── QuizRepositoryImpl.java

│       │   │               │               └── SubmissionJpaRepository.java

│       │   │               ├── adaptive/

│       │   │               │   ├── api/

│       │   │               │   │   ├── controller/

│       │   │               │   │   │   ├── AdaptiveSettingsController.java

│       │   │               │   │   │   ├── LearnerInsightsController.java

│       │   │               │   │   │   ├── MasteryController.java

│       │   │               │   │   │   ├── RecommendationController.java

│       │   │               │   │   │   └── SkillController.java

│       │   │               │   │   └── dto/

│       │   │               │   │       ├── AdaptiveSettingsRequest.java

│       │   │               │   │       ├── AdaptiveSettingsResponse.java

│       │   │               │   │       ├── LearnerInsightResponses.java

│       │   │               │   │       ├── MasteryResponses.java

│       │   │               │   │       ├── RecommendationResponse.java

│       │   │               │   │       ├── SkillGraphResponse.java

│       │   │               │   │       ├── SkillRequests.java

│       │   │               │   │       └── SkillResponse.java

│       │   │               │   ├── application/

│       │   │               │   │   ├── skillgraph/

│       │   │               │   │   │   └── SkillGraphService.java

│       │   │               │   │   ├── mastery/

│       │   │               │   │   │   ├── AdaptiveSettingsService.java

│       │   │               │   │   │   ├── LearnerInsightsService.java 

│       │   │               │   │   │   ├── MasteryCalculator.java

│       │   │               │   │   │   ├── MasteryQueryService.java

│       │   │               │   │   │   ├── MasteryStateClassifier.java

│       │   │               │   │   │   ├── MasteryUpdateService.java

│       │   │               │   │   │   └── PlacementDiagnosticService.java

│       │   │               │   │   ├── decision/

│       │   │               │   │   │   ├── AdaptiveDecisionEngine.java

│       │   │               │   │   │   ├── AdaptiveDecisionService.java

│       │   │               │   │   │   ├── NextLessonResolver.java

│       │   │               │   │   │   └── PracticeDifficultySelector.java

│       │   │               │   │   ├── recommendation/

│       │   │               │   │   │   ├── RecommendationReasonBuilder.java

│       │   │               │   │   │   └── RecommendationService.java

│       │   │               │   │   ├── event/

│       │   │               │   │   │   └── RecommendationCreatedEvent.java

│       │   │               │   │   └── listener/

│       │   │               │   │       └── QuizSubmittedEventListener.java

│       │   │               │   ├── domain/

│       │   │               │   │   ├── model/

│       │   │               │   │   │   ├── AdaptiveDecision.java

│       │   │               │   │   │   ├── LearnerSkillMastery.java

│       │   │               │   │   │   ├── LessonSkill.java

│       │   │               │   │   │   ├── MasteryState.java

│       │   │               │   │   │   ├── MasteryThresholds.java

│       │   │               │   │   │   ├── PathRecommendation.java

│       │   │               │   │   │   ├── RecommendationType.java

│       │   │               │   │   │   ├── Skill.java

│       │   │               │   │   │   ├── SkillGraph.java

│       │   │               │   │   │   └── SkillPrerequisite.java

│       │   │               │   │   └── repository/

│       │   │               │   │       ├── LearnerSkillMasteryRepository.java

│       │   │               │   │       ├── PathRecommendationRepository.java

│       │   │               │   │       └── SkillRepository.java

│       │   │               │   └── infrastructure/

│       │   │               │       └── persistence/

│       │   │               │           ├── entity/

│       │   │               │           │   ├── LearnerSkillMasteryEntity.java

│       │   │               │           │   ├── LessonSkillEntity.java

│       │   │               │           │   ├── PathRecommendationEntity.java

│       │   │               │           │   ├── SkillEntity.java

│       │   │               │           │   └── SkillPrerequisiteEntity.java

│       │   │               │           ├── mapper/

│       │   │               │           │   ├── MasteryMapper.java

│       │   │               │           │   ├── RecommendationMapper.java

│       │   │               │           │   └── SkillMapper.java

│       │   │               │           └── repository/

│       │   │               │               ├── LearnerSkillMasteryJpaRepository.java

│       │   │               │               ├── LearnerSkillMasteryRepositoryImpl.java

│       │   │               │               ├── LessonSkillJpaRepository.java

│       │   │               │               ├── PathRecommendationJpaRepository.java

│       │   │               │               ├── PathRecommendationRepositoryImpl.java

│       │   │               │               ├── SkillJpaRepository.java

│       │   │               │               ├── SkillPrerequisiteJpaRepository.java

│       │   │               │               └── SkillRepositoryImpl.java

│       │   │               ├── interaction/

│       │   │               │   ├── api/

│       │   │               │   │   ├── controller/

│       │   │               │   │   │   ├── ContentReportController.java

│       │   │               │   │   │   ├── DiscussionController.java

│       │   │               │   │   │   └── NotificationController.java

│       │   │               │   │   └── dto/

│       │   │               │   │       ├── InteractionRequests.java

│       │   │               │   │       └── InteractionResponses.java

│       │   │               │   ├── application/

│       │   │               │   │   ├── service/

│       │   │               │   │   │   ├── ContentReportService.java

│       │   │               │   │   │   ├── DiscussionService.java

│       │   │               │   │   │   └── NotificationService.java

│       │   │               │   │   ├── listener/

│       │   │               │   │   │   └── NotificationEventListener.java

│       │   │               │   │   └── scheduler/

│       │   │               │   │       └── DeadlineReminderScheduler.java

│       │   │               │   ├── domain/

│       │   │               │   │   ├── model/

│       │   │               │   │   │   ├── ContentReport.java

│       │   │               │   │   │   ├── DiscussionPost.java

│       │   │               │   │   │   ├── Notification.java

│       │   │               │   │   │   └── NotificationType.java

│       │   │               │   │   └── repository/

│       │   │               │   │       ├── ContentReportRepository.java

│       │   │               │   │       ├── DiscussionPostRepository.java

│       │   │               │   │       └── NotificationRepository.java

│       │   │               │   └── infrastructure/

│       │   │               │       └── persistence/

│       │   │               │           ├── entity/

│       │   │               │           │   ├── ContentReportEntity.java

│       │   │               │           │   ├── DiscussionPostEntity.java

│       │   │               │           │   └── NotificationEntity.java

│       │   │               │           ├── mapper/

│       │   │               │           │   └── InteractionMapper.java

│       │   │               │           └── repository/

│       │   │               │               ├── ContentReportJpaRepository.java

│       │   │               │               ├── ContentReportRepositoryImpl.java

│       │   │               │               ├── DiscussionPostJpaRepository.java

│       │   │               │               ├── DiscussionPostRepositoryImpl.java

│       │   │               │               ├── NotificationJpaRepository.java

│       │   │               │               └── NotificationRepositoryImpl.java

│       │   │               └── admin/

│       │   │                   ├── api/

│       │   │                   │   ├── controller/

│       │   │                   │   │   ├── AdminContentReportController.java

│       │   │                   │   │   ├── AdminCourseController.java

│       │   │                   │   │   ├── AdminReportingController.java

│       │   │                   │   │   └── AdminUserController.java

│       │   │                   │   └── dto/

│       │   │                   │       ├── AdminRequests.java

│       │   │                   │       ├── AdminResponses.java

│       │   │                   │       └── ReportingResponses.java

│       │   │                   ├── application/

│       │   │                   │   ├── service/

│       │   │                   │   │   ├── AdminCourseService.java

│       │   │                   │   │   ├── AuditLogService.java

│       │   │                   │   │   ├── ContentModerationService.java

│       │   │                   │   │   └── UserManagementService.java

│       │   │                   │   └── reporting/

│       │   │                   │       └── PlatformMetricsService.java

│       │   │                   ├── domain/

│       │   │                   │   ├── model/

│       │   │                   │   │   ├── AuditAction.java

│       │   │                   │   │   ├── AuditLog.java

│       │   │                   │   │   ├── CourseMetrics.java

│       │   │                   │   │   └── SkillMasteryDistribution.java

│       │   │                   │   └── repository/

│       │   │                   │       ├── AuditLogRepository.java

│       │   │                   │       └── ReportingQueryRepository.java

│       │   │                   └── infrastructure/

│       │   │                       └── persistence/

│       │   │                           ├── entity/

│       │   │                           │   └── AuditLogEntity.java

│       │   │                           ├── mapper/

│       │   │                           │   └── AuditLogMapper.java

│       │   │                           └── repository/

│       │   │                               ├── AuditLogJpaRepository.java

│       │   │                               ├── AuditLogRepositoryImpl.java

│       │   │                               └── ReportingQueryRepositoryImpl.java

│       │   └── resources/

│       │       ├── application.yml

│       │       └── db/

│       │           └── migration/

│       │               ├── V001__create_auth_schema.sql

│       │               ├── V002__create_course_learning_schema.sql

│       │               ├── V003__create_skill_graph_schema.sql

│       │               ├── V004__create_assessment_schema.sql

│       │               ├── V005__create_mastery_and_recommendation_schema.sql

│       │               ├── V006__create_interaction_and_audit_schema.sql

│       │               └── V007__create_password_reset_tokens.sql

│       └── test/

│           ├── java/

│           │   └── com/

│           │       └── codemastery/

│           │           ├── modules/

│           │           │   ├── auth/

│           │           │   │   ├── AuthServiceTest.java

│           │           │   │   ├── JwtTokenProviderTest.java

│           │           │   │   ├── StrongPasswordTest.java

│           │           │   │   └── UserPersistenceTest.java

│           │           │   ├── course/

│           │           │   │   ├── application/

│           │           │   │   │   └── service/

│           │           │   │   │       ├── CourseServiceTest.java

│           │           │   │   │       └── EnrollmentServiceTest.java

│           │           │   │   └── LessonProgressServiceTest.java

│           │           │   ├── assessment/

│           │           │   │   ├── AttemptServiceTest.java

│           │           │   │   ├── QuizGradingServiceTest.java

│           │           │   │   └── SubmissionServiceTest.java

│           │           │   ├── adaptive/

│           │           │   │   ├── skillgraph/

│           │           │   │   │   └── SkillGraphTest.java

│           │           │   │   ├── mastery/

│           │           │   │   │   ├── AdaptiveSettingsServiceTest.java

│           │           │   │   │   ├── MasteryCalculatorTest.java

│           │           │   │   │   ├── MasteryStateClassifierTest.java

│           │           │   │   │   ├── MasteryUpdateServiceTest.java

│           │           │   │   │   └── PlacementDiagnosticServiceTest.java

│           │           │   │   ├── decision/

│           │           │   │   │   ├── AdaptiveDecisionEngineTest.java

│           │           │   │   │   ├── NextLessonResolverTest.java

│           │           │   │   │   └── PracticeDifficultySelectorTest.java

│           │           │   │   ├── listener/

│           │           │   │   │   └── QuizSubmittedEventListenerTest.java

│           │           │   │   └── evaluation/

│           │           │   │       └── AdaptiveProfileScenarioTest.java

│           │           │   ├── interaction/

│           │           │   │   ├── DeadlineReminderSchedulerTest.java

│           │           │   │   └── NotificationEventListenerTest.java

│           │           │   └── admin/

│           │           │       └── UserManagementServiceTest.java

│           │           ├── integration/

│           │           │   ├── AbstractIntegrationTest.java

│           │           │   ├── AdaptiveLearningFlowIntegrationTest.java

│           │           │   ├── AuthorizationIntegrationTest.java

│           │           │   ├── MasteryProgressTransactionIntegrationTest.java

│           │           │   └── repository/

│           │           │       └── CourseRepositoryIntegrationTest.java

│           │           └── support/

│           │               ├── ScenarioLoader.java

│           │               ├── TestClockConfig.java

│           │               └── fixtures/

│           │                   ├── CourseFixtures.java

│           │                   └── SkillGraphFixtures.java

│           └── resources/

│               ├── application-test.yml

│               └── adaptive-scenarios/

│                   ├── p1-strong-foundation.json

│                   ├── p2-loops-gap.json

│                   └── p3-complete-beginner.json

├── frontend/

│   ├── index.html

│   ├── package.json

│   ├── package-lock.json

│   ├── tsconfig.json

│   ├── vite.config.ts

│   ├── nginx.conf

│   ├── Dockerfile

│   ├── .dockerignore

│   ├── public/

│   │   ├── favicon.svg

│   │   ├── google-sans-flex-OFL.txt

│   │   ├── jetbrains-mono-OFL.txt

│   │   └── poppins-OFL.txt

│   └── src/

│       ├── main.tsx

│       ├── vite-env.d.ts

│       ├── app/

│       │   ├── App.tsx

│       │   ├── providers/

│       │   │   └── AuthProvider.tsx

│       │   ├── preview/

│       │   │   ├── AdminPreview.tsx

│       │   │   ├── DesignReference.tsx

│       │   │   ├── InstructorPreview.tsx

│       │   │   ├── LearnerPreview.tsx

│       │   │   ├── PreviewApp.tsx

│       │   │   ├── PreviewApp.test.tsx

│       │   │   ├── PublicPreview.tsx

│       │   │   ├── fixtures.ts

│       │   │   └── previewKit.tsx

│       │   └── theme/

│       │       ├── google-sans-flex-latin-ext-wght.woff2

│       │       ├── google-sans-flex-latin-wght.woff2

│       │       ├── google-sans-flex-vietnamese-wght.woff2

│       │       ├── jetbrains-mono-latin-ext-wght.woff2

│       │       ├── jetbrains-mono-latin-wght.woff2

│       │       ├── jetbrains-mono-vietnamese-wght.woff2

│       │       ├── poppins-latin-800.woff2

│       │       └── theme.ts

│       ├── routes/

│       │   ├── index.tsx

│       │   ├── routePaths.ts

│       │   └── guards/

│       │       └── ProtectedRoute.tsx

│       ├── layouts/

│       │   ├── AuthLayout.tsx

│       │   └── DashboardLayout.tsx

│       ├── features/

│       │   ├── auth/

│       │   │   ├── api/

│       │   │   │   └── authApi.ts

│       │   │   ├── components/

│       │   │   │   └── RegisterForm.tsx

│       │   │   ├── hooks/

│       │   │   │   └── useAuth.ts

│       │   │   ├── pages/

│       │   │   │   ├── ForgotPasswordPage.tsx

│       │   │   │   ├── LoginPage.tsx

│       │   │   │   ├── ProfilePage.tsx

│       │   │   │   ├── RegisterPage.tsx

│       │   │   │   └── ResetPasswordPage.tsx

│       │   │   └── types/

│       │   │       └── authTypes.ts

│       │   ├── catalogue/

│       │   │   ├── api/

│       │   │   │   └── catalogueApi.ts

│       │   │   ├── components/

│       │   │   │   └── CourseCard.tsx

│       │   │   ├── hooks/

│       │   │   │   └── useCourseCatalogue.ts

│       │   │   ├── pages/

│       │   │   │   ├── CourseCataloguePage.tsx

│       │   │   │   ├── CourseDetailPage.tsx

│       │   │   │   └── LandingPage.tsx

│       │   │   └── types/

│       │   │       └── catalogueTypes.ts

│       │   ├── learning/

│       │   │   ├── api/

│       │   │   │   └── learningApi.ts

│       │   │   ├── components/

│       │   │   │   ├── CourseOutline.tsx

│       │   │   │   └── LessonContent.tsx

│       │   │   ├── hooks/

│       │   │   │   └── useCourseLearning.ts

│       │   │   ├── pages/

│       │   │   │   ├── CourseLearningPage.tsx

│       │   │   │   ├── LearnerHomePage.tsx

│       │   │   │   ├── LessonPage.tsx

│       │   │   │   └── MyCoursesPage.tsx

│       │   │   └── types/

│       │   │       └── learningTypes.ts

│       │   ├── assessment/

│       │   │   ├── api/

│       │   │   │   ├── assignmentApi.ts

│       │   │   │   └── quizApi.ts

│       │   │   ├── components/

│       │   │   │   ├── QuestionRenderer.tsx

│       │   │   │   ├── QuizTimer.tsx

│       │   │   │   └── assessment.test.tsx

│       │   │   ├── hooks/

│       │   │   │   ├── useAssignment.ts

│       │   │   │   └── useQuizAttempt.ts

│       │   │   ├── pages/

│       │   │   │   ├── AssignmentDetailPage.tsx

│       │   │   │   ├── AttemptHistoryPage.tsx

│       │   │   │   ├── CourseAssessmentsPage.tsx

│       │   │   │   ├── QuizAttemptPage.tsx

│       │   │   │   └── QuizResultPage.tsx

│       │   │   └── types/

│       │   │       └── assessmentTypes.ts

│       │   ├── mastery/

│       │   │   ├── api/

│       │   │   │   └── masteryApi.ts

│       │   │   ├── components/

│       │   │   │   ├── ExampleMasteryMap.tsx

│       │   │   │   ├── MasteryGraph.tsx

│       │   │   │   ├── MasteryStateChip.tsx

│       │   │   │   ├── NextRecommendedCard.tsx

│       │   │   │   ├── PlacementPrompt.tsx

│       │   │   │   └── SkillDetailPanel.tsx

│       │   │   ├── hooks/

│       │   │   │   └── useMasteryMap.ts

│       │   │   ├── pages/

│       │   │   │   └── MasteryMapPage.tsx

│       │   │   ├── types/

│       │   │   │   └── masteryTypes.ts

│       │   │   └── utils/

│       │   │       ├── masteryGraphLayout.ts

│       │   │       └── masteryGraphLayout.test.ts

│       │   ├── interaction/

│       │   │   ├── api/

│       │   │   │   ├── discussionApi.ts

│       │   │   │   └── notificationApi.ts

│       │   │   ├── components/

│       │   │   │   ├── DiscussionPanel.tsx

│       │   │   │   ├── NotificationBell.tsx

│       │   │   │   ├── NotificationItem.tsx

│       │   │   │   └── ReportContentDialog.tsx

│       │   │   ├── hooks/

│       │   │   │   ├── useDiscussion.ts

│       │   │   │   └── useNotifications.ts

│       │   │   ├── pages/

│       │   │   │   └── NotificationsPage.tsx

│       │   │   └── types/

│       │   │       └── interactionTypes.ts

│       │   ├── instructor/

│       │   │   ├── course-management/

│       │   │   │   ├── api/

│       │   │   │   │   ├── assessmentAuthoringApi.ts

│       │   │   │   │   └── instructorCourseApi.ts

│       │   │   │   ├── components/

│       │   │   │   │   ├── ChapterList.tsx

│       │   │   │   │   ├── MaterialForm.tsx

│       │   │   │   │   ├── PublishToggle.tsx

│       │   │   │   │   ├── QuestionForm.tsx

│       │   │   │   │   └── QuestionForm.test.ts

│       │   │   │   ├── hooks/

│       │   │   │   │   ├── useAssessmentEditor.ts

│       │   │   │   │   ├── useCourseEditor.ts

│       │   │   │   │   └── useQuestionBank.ts

│       │   │   │   ├── pages/

│       │   │   │   │   ├── AssignmentEditorPage.tsx

│       │   │   │   │   ├── CourseEditorPage.tsx

│       │   │   │   │   ├── InstructorCoursesPage.tsx

│       │   │   │   │   ├── LessonEditorPage.tsx

│       │   │   │   │   ├── LessonQaPage.tsx

│       │   │   │   │   ├── QuestionBankPage.tsx

│       │   │   │   │   └── QuizEditorPage.tsx

│       │   │   │   └── types/

│       │   │   │       └── courseManagementTypes.ts

│       │   │   ├── skill-graph-editor/

│       │   │   │   ├── api/

│       │   │   │   │   └── skillGraphApi.ts

│       │   │   │   ├── components/

│       │   │   │   │   ├── AdaptiveSettingsPanel.tsx

│       │   │   │   │   ├── LessonSkillMappingTable.tsx

│       │   │   │   │   ├── SkillForm.tsx

│       │   │   │   │   └── SkillGraphCanvas.tsx

│       │   │   │   ├── hooks/

│       │   │   │   │   └── useSkillGraph.ts

│       │   │   │   ├── pages/

│       │   │   │   │   └── SkillGraphEditorPage.tsx

│       │   │   │   └── types/

│       │   │   │       └── skillGraphTypes.ts

│       │   │   ├── grading/

│       │   │   │   ├── api/

│       │   │   │   │   └── gradingApi.ts

│       │   │   │   ├── components/

│       │   │   │   │   └── SubmissionTable.tsx

│       │   │   │   ├── hooks/

│       │   │   │   │   └── useSubmissions.ts

│       │   │   │   ├── pages/

│       │   │   │   │   ├── GradeSubmissionPage.tsx

│       │   │   │   │   └── SubmissionListPage.tsx

│       │   │   │   └── types/

│       │   │   │       └── gradingTypes.ts

│       │   │   └── learner-insights/

│       │   │       ├── api/

│       │   │       │   └── learnerInsightsApi.ts

│       │   │       ├── components/

│       │   │       │   ├── LearnerTable.tsx

│       │   │       │   └── RecommendationLogTable.tsx

│       │   │       ├── hooks/

│       │   │       │   └── useLearnerInsights.ts

│       │   │       ├── pages/

│       │   │       │   ├── LearnerDetailPage.tsx

│       │   │       │   └── LearnerInsightsPage.tsx

│       │   │       └── types/

│       │   │           └── learnerInsightsTypes.ts

│       │   └── admin/

│       │       ├── user-management/

│       │       │   ├── api/

│       │       │   │   └── adminUserApi.ts

│       │       │   ├── components/

│       │       │   │   └── UserTable.tsx

│       │       │   ├── hooks/

│       │       │   │   └── useUserManagement.ts

│       │       │   ├── pages/

│       │       │   │   └── UserManagementPage.tsx

│       │       │   └── types/

│       │       │       └── userManagementTypes.ts

│       │       ├── content-reports/

│       │       │   ├── api/

│       │       │   │   └── adminReportApi.ts

│       │       │   ├── components/

│       │       │   │   ├── ReportDetailDialog.tsx

│       │       │   │   └── ReportTable.tsx

│       │       │   ├── hooks/

│       │       │   │   └── useReportQueue.ts

│       │       │   ├── pages/

│       │       │   │   └── ContentReportQueuePage.tsx

│       │       │   └── types/

│       │       │       └── contentReportTypes.ts

│       │       ├── course-management/

│       │       │   ├── api/

│       │       │   │   └── adminCourseApi.ts

│       │       │   ├── components/

│       │       │   │   └── AdminCourseTable.tsx

│       │       │   ├── hooks/

│       │       │   │   └── useAdminCourses.ts

│       │       │   ├── pages/

│       │       │   │   └── AdminCoursesPage.tsx

│       │       │   └── types/

│       │       │       └── adminCourseTypes.ts

│       │       └── reporting-dashboard/

│       │           ├── api/

│       │           │   └── reportingApi.ts

│       │           ├── components/

│       │           │   ├── AuditLogTable.tsx

│       │           │   ├── EnrolmentChart.tsx

│       │           │   └── MasteryDistributionChart.tsx

│       │           ├── hooks/

│       │           │   ├── useAuditLog.ts

│       │           │   └── useReportingMetrics.ts

│       │           ├── pages/

│       │           │   ├── AuditLogPage.tsx

│       │           │   └── ReportingDashboardPage.tsx

│       │           └── types/

│       │               └── reportingTypes.ts

│       └── shared/

│           ├── components/

│           │   ├── AppHeader.tsx

│           │   ├── BrandLogo.tsx

│           │   ├── CodeBlock.tsx

│           │   ├── ConfirmDialog.tsx

│           │   ├── CourseMark.tsx

│           │   ├── DataTable.tsx

│           │   ├── FileUploadField.tsx

│           │   ├── GuidanceStack.tsx

│           │   ├── MarkdownRenderer.tsx

│           │   ├── PageHeader.tsx

│           │   ├── PathSpine.tsx

│           │   ├── PresentationIcon.tsx

│           │   ├── SidebarNav.tsx

│           │   ├── StatusPill.tsx

│           │   ├── StatusViews.tsx

│           │   ├── Tile.tsx

│           │   ├── ViewRail.tsx

│           │   ├── course-logo-python.svg

│           │   ├── course-logo-typescript.svg

│           │   ├── presentation.browser.cjs

│           │   ├── presentation.browser.test.ts

│           │   └── presentation.test.tsx

│           ├── hooks/

│           │   ├── useDebounce.ts

│           │   └── useDebounce.test.tsx

│           ├── api/

│           │   └── httpClient.ts

│           ├── types/

│           │   └── apiTypes.ts

│           └── utils/

│               ├── format.ts

│               └── format.test.ts

├── e2e/

│   ├── package.json

│   ├── package-lock.json

│   ├── playwright.config.ts

│   ├── tests/

│   │   ├── learner-flow.spec.ts

│   │   └── adaptive-flow.spec.ts

│   └── fixtures/

│       ├── auth-fixture.ts

│       ├── catalogue-fixture.sql

│       ├── ci-evidence.mjs

│       ├── host-identity.mjs

│       ├── lifecycle.py

│       ├── lifecycle.test.mjs

│       ├── local-stack.test.mjs

│       ├── local-stack.ts

│       ├── preview-control.test.mjs

│       ├── preview-server.mjs

│       ├── run-inventory.mjs

│       ├── run-inventory.test.mjs

│       ├── stack-down.mjs

│       ├── stack-down.test.mjs

│       ├── stack-recovery.mjs

│       ├── stack-recovery.test.mjs

│       └── files/

│           └── sample-submission.txt

├── infrastructure/

│   ├── docker-compose.yml

│   ├── minio/

│   │   ├── Dockerfile

│   │   ├── minio-init.sh

│   │   └── test-minio-init.sh

│   └── vm/

│       ├── deploy.sh

│       ├── docker-compose.vm.yml

│       └── tests/

│           ├── test-containerd-archive-roundtrip.sh

│           ├── test-deploy-env-file-custody.sh

│           ├── test-deploy-image-identity-roundtrip.sh

│           ├── test-deploy-offline-compose-guards.sh

│           ├── test-deploy-preflight-rendered-compose.sh

│           ├── test-deploy-privilege-matrix-contract.sh

│           ├── test-deploy-release-archive-identity.sh

│           ├── test-deploy-syntax-and-authorization-gates.sh

│           ├── test-ingress-dp01-preflight.sh

│           ├── test-ingress-real-backend.sh

│           ├── test-ingress-release-ca-store.sh

│           ├── test-ingress-release-image-build.sh

│           ├── test-ingress-stub-backend.sh

│           ├── test-postgres-foreign-volume-guard.sh

│           ├── test-postgres-image-layer-equivalence.sh

│           ├── test-postgres-privilege-matrix-containment.sh

│           ├── test-postgres-published-ports.sh

│           └── test-runtime-real-backend-privileges.sh

├── sample-data/

│   ├── README.md

│   ├── materials/

│   │   └── sample-slides.pdf

│   └── seed/

│       ├── 01-users.sql

│       ├── 02-course-python-fundamentals.sql

│       ├── 03-skill-graph-and-lesson-mapping.sql

│       ├── 04-questions-quizzes-assignments.sql

│       └── 05-learner-profiles.sql

├── docs/

│   ├── requirements.md

│   ├── architecture.md

│   ├── repository-structure.md

│   ├── references/

│       └── originals/

│           ├── project-requirements.pdf

│           ├── approved-architecture.docx

│           └── final-repository-tree.docx

│   ├── setup.md

│   ├── data-model.md

│   ├── adaptive-learning.md

│   ├── testing.md

│   ├── deployment.md

│   ├── api/

│   │   └── openapi.yaml

│   └── decisions/

│       ├── 001-modular-monolith-module-boundaries.md

│       ├── 002-interim-delegated-contracts.md

│       ├── 003-postgresql-alpine-su-exec-image.md

│       ├── 004-leader-approval-of-delegated-records.md

│       ├── 005-batch-01-contract-approvals.md

│       └── README.md

├── .env.example

├── .gitignore

├── AGENTS.md

├── DESIGN.md

├── README.md

└── CONTRIBUTING.md
```

## ADR014 hybrid-authentication file additions — 16 November 2026

`STRUCT-AUTH-SESSION-2026-11-16-r1` is **DELEGATED_APPROVED** under the Project
Leader's explicit instruction "CodeMastery — End-to-End Hybrid Authentication Upgrade
with Delegated Technical Approval" of 16 November 2026
([ADR014 §10](decisions/014-hybrid-refresh-session-authentication.md#10-struct-auth-session-2026-11-16-r1--file-additions)).
Exactly these three files are added, inside existing directories of the owning Auth
module and the migration directory:

| File | Purpose |
| --- | --- |
| `backend/src/main/java/com/codemastery/modules/auth/application/service/RefreshSessionService.java` | Auth application service for refresh-session start, rotation, replay detection and logout |
| `backend/src/main/java/com/codemastery/modules/auth/infrastructure/security/RefreshCookieSecurity.java` | Refresh-cookie policy and the Origin/Fetch Metadata guard of the cookie operations |
| `backend/src/main/resources/db/migration/V008__create_refresh_sessions.sql` | The NEW migration for `refresh_sessions` and `refresh_tokens`, with no grant |

The existing Auth-module and migration-directory CODEOWNERS rules cover them and are
unchanged. Persistence methods extend the existing `UserRepository` and
`UserRepositoryImpl`; tests were added to existing test files only. The immutable
canonical tree and the inventory tree below are unchanged; this is an explicit
addition. No other directory, module, file or dependency, and no execution of V008
against a shared or production database, is approved.

## CO08 resume-time migration addition — 17 November 2026

`STRUCT-CO08-V009-2026-11-17-r1` records one file addition explicitly authorized by the
Project Leader on 17 November 2026, within the "CodeMastery — Fully Implement CO08,
CO07 and CO16 in Three Existing PRs" instruction, for CO08
([#174](https://github.com/chi-thanh-nguyen/codemastery/issues/174),
[PR #248](https://github.com/chi-thanh-nguyen/codemastery/pull/248)). It implements the
NEW migration required by `CT22-RESUME-SCHEMA-2026-11-09-r1`
([ADR006 §7.2](decisions/006-batch-02-contract-approvals.md#72-bounded-visit-time-schema-and-learner-level-resume-read)),
inside the existing migration directory:

| File | Purpose |
| --- | --- |
| `backend/src/main/resources/db/migration/V009__add_enrollment_last_visited_at.sql` | The NEW migration adding `enrollments.last_visited_at timestamptz NULL`, with no default, backfill or grant |

The existing migration-directory CODEOWNERS rule covers it and is unchanged. The CO08
implementation otherwise uses existing Course files and existing test files only. The
immutable canonical tree and the inventory tree below are unchanged; this is an
explicit addition. No other directory, module, file or dependency, and no execution of
V009 against a shared or production database, is approved.

## Bounded Interim FE03/FE07 test inventory

**CT14-INTERIM-FE03-FE07-2026-10-24-r1 —
[LEADER_APPROVED](decisions/README.md#approval-status-labels).** The Leader's explicit
“CodeMastery — Complete Interim Technical Contract Approvals (CT16, CT17, CT14)”,
24 October 2026, delegates technical approval of the smallest FE03/FE07 test
inventory. Codex acts as the delegated approval authority for exactly:

- frontend/src/shared/api/httpClient.test.ts
- frontend/src/app/providers/AuthProvider.test.tsx
- frontend/src/routes/index.test.tsx
- frontend/src/features/catalogue/api/catalogueApi.test.ts
- frontend/src/features/catalogue/pages/CourseCataloguePage.test.tsx
- frontend/src/features/catalogue/pages/CourseDetailPage.test.tsx

These six planned additions use existing owning directories and current
@heizseung/@chi-thanh-nguyen CODEOWNERS; no new helper, hierarchy or ownership rule.
[The exact testing contract](testing.md#approved-interim-fe03fe07-verification-contract--24-october-2026)
records coverage, isolation, CT16 pending-PR dependency and execution limits.
No listed file is created or claimed implemented by this documentation change.
The original canonical tree and existing AU03/AU04/FE01 exceptions are unchanged;
this explicit bounded exception does not approve unrelated test inventory.
Separate implementation authority and compliant protected integration are required.


### FE07 delegated test placement — 27 October 2026

The Leader's explicit full-FE07 task authorizes the necessary test additions below
within existing owning feature/module and integration directories (ADR002
`FE07-2026-10-27-r1`). Existing scaffolds remain the production callable locations;
no module or production directory is added.

- frontend/src/features/learning/api/learningApi.test.ts
- frontend/src/features/learning/pages/MyCoursesPage.test.tsx
- backend/src/test/java/com/codemastery/integration/EnrollmentIntegrationTest.java

Existing CourseCatalogue API/page tests and routes/index.test.tsx cover the other
FE07 changes. e2e/tests/learner-flow.spec.ts is the existing browser-flow location;
the external Playwright installation used for local execution is not a dependency
or E2E pipeline change.


### SD01/SD02 delegated test placement — 30 October 2026

The Leader's explicit SD01/SD02 completion task authorizes the single test addition
below within the existing integration directory (ADR002 `CT15-SD-2026-10-30-r3`,
V1). It verifies the existing `sample-data/seed/` files; no seed, material,
loader-script or helper path is added.

- backend/src/test/java/com/codemastery/integration/SampleDataSeedIntegrationTest.java

The approved `sample-data/` inventory is unchanged: `README.md`,
`materials/sample-slides.pdf` and `seed/01`–`05` keep their existing paths.

### Batch 7 delegated integration-test placement — 15 November 2026

`CT14-INTEGRATION-PLACEMENT-2026-11-15-r1` is **DELEGATED_APPROVED** under the Project
Leader's explicit instruction of 15 November 2026, “CodeMastery — Final Contract
Resolution & Comprehensive Independent Re-Audit of PR #244 and #245”, which delegated
the bounded test-structure decision for the gaps ADR008 §5 recorded
([ADR012 §19.3](decisions/012-batch-07-contract-approvals.md#193-ct14-integration-placement-2026-11-15-r1--interactionadmin-and-storage-suites)).
Exactly these two test files may be added; they are not yet created:

- backend/src/test/java/com/codemastery/integration/InteractionAdminIntegrationTest.java
- backend/src/test/java/com/codemastery/integration/FileStorageIntegrationTest.java

Both extend the existing `AbstractIntegrationTest`. The storage suite uses a
Testcontainers `GenericContainer` from the already declared test dependency with the
MinIO image digest pinned in `infrastructure/docker-compose.yml`; no new dependency,
directory or production file is approved. The canonical tree above is unchanged; this
is an explicit addition.

### IN06.repo test placement — 18 November 2026

`CT14-IN06-REPO-PLACEMENT-2026-11-18-r1` is
**[LEADER_APPROVED](decisions/README.md#approval-status-labels)**: the Project Leader
approved it on 18 November 2026 (Asia/Ho_Chi_Minh), in response to the placement question
raised during the instruction "CodeMastery — PR #250 Complete Remediation, Git Attribution
& Cross-PR Integration". ADR008 §5 records Q&A and reporting test inventories as a gap,
and ADR012 §19.3's `InteractionAdminIntegrationTest.java` has a different responsibility
(B7-O01–O03 and O06–O11). Exactly these two test files are added inside existing directories:

| File | Responsibility |
| --- | --- |
| `backend/src/test/java/com/codemastery/modules/interaction/InteractionMapperTest.java` | Unit tests of `InteractionMapper`: entity/domain round trips and exact V006 report status codes |
| `backend/src/test/java/com/codemastery/integration/repository/InteractionRepositoryIntegrationTest.java` | Real-PostgreSQL IN06.repo regression: insert and permitted updates, immutable-field rejection, hidden-post filtering before paging and counting, caller-provided ordering, V006 checks, and execution under a transaction-scoped role that holds exactly the ADR012 §19.2 ceiling for `discussion_posts` and `content_reports`; extends `AbstractIntegrationTest` |

CODEOWNERS is unchanged: the existing Interaction test rule (@tihh647-ui,
@chi-thanh-nguyen) covers the unit test; like other unlisted integration suites, the
integration test matches no rule, and adding one is outside this record. No production file,
dependency, directory or other test path is approved. `InteractionAdminIntegrationTest.java`
keeps its §19.3 scope and is not created by this addition. The canonical tree above is
unchanged; this is an explicit addition.
