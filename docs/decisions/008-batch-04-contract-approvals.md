# ADR008: Batch 4 Test Contracts and Pending Operational Inputs

## 1. Authority, baseline and provenance

Recorded **9 November 2026 (Asia/Ho_Chi_Minh)** under the Project Leader
@chi-thanh-nguyen's instruction **“CodeMastery — Batch 4 | Complete Contract
Approval, Dependency Reconciliation & GitHub Synchronization”**. This is
**Codex's delegated technical approval under this explicit Project Leader
instruction**, not personal Leader approval, module-owner review or a GitHub
APPROVE review. Only the six decisions marked **DELEGATED_APPROVED** below are
approved. Two mandatory CT17 checkpoints remain **PENDING_LEADER_INPUT**.
Batch 4 is therefore **partially approved**. No prior effective contract is amended.

The instruction authorizes technical selection, compatible necessary prerequisites,
documentation and bounded Issue/Project synchronization, and one open PR. It excludes
implementation, new architecture/structure, residual-risk acceptance, deployment,
protection changes/bypass and merge. Independent review, exact-head required checks
and protected integration of this record remain gates. No implementation acceptance
or retrospective review is inferred from existing merged code.

Starting freshly fetched `origin/main`: **3eeacc8f5b3244c6d3db403d3e7f476f9caeb554**.
PRs [#227](https://github.com/chi-thanh-nguyen/codemastery/pull/227),
[#228](https://github.com/chi-thanh-nguyen/codemastery/pull/228),
[#229](https://github.com/chi-thanh-nguyen/codemastery/pull/229) and
[#230](https://github.com/chi-thanh-nguyen/codemastery/pull/230) are merged;
ADR004–ADR007 are present on that main. PR #230 merged as the starting SHA on
9 November 2026 at 20:31:42 ICT. Historical integration-pending statements in
those documents/Issues retain their dated provenance; the merge establishes
document presence, not a counted independent approval or runtime result.

Planning sources are under the external, immutable
`~/codemastery-backlog-publication/contract-checkpoint-batching/`:

- `run-20261108-135024/`: README, DG reconciliation/change proposals, checkpoint
  register, roadmap and `BATCH-04.md`;
- `verification-20261108-151823/`: README, pre-approval verification,
  Leader-decision inventory, roadmap, dependency traceability,
  `registers/revised-plan.json` and `registers/cr-index-to-register.csv`.

Directory discovery found no newer applicable run/verification. Use the verification's
corrected relationships and `local_ids` mapping, **not array order in the JSON**.
They preserve CP-029–036 membership. The old CR-5/CR-6 review groups are not this
eight-checkpoint batch. DG B11/B13/B14, E110.1/E110.2, F19/L10/L11/L15/I16 and
DG-U18 are planning traceability, not additional approvals or new dependencies.
DG-U18 is a deployment binding; it does not block independent test contracts.

The handoff Leader register still lists LD-05 production facts as pending.
ADR004 resolves HC-U20/LD-01 for the consumed CT14/CT17 records; that condition
does not require reapproval. LD-12's ordinary runner/coverage/clock selections
are exercised under the present delegation. LD-09's protected review route is
not waived. Neither Batch 2's reset-only email/visit-time exceptions nor Batch 3's
assessment guard/quiz-expiry approvals authorize broader work here.

## 2. Inventory and dependency disposition

| CP | Slice / owning Issue | Exact revision | Disposition and scope |
|---|---|---|---|
| CP-029 | CT34.runner / [#150](https://github.com/chi-thanh-nguyen/codemastery/issues/150) | `CT34-RUNNER-2026-11-09-r1` | **DELEGATED_APPROVED**, §3; adopt existing runner, not all PR #209 work/cases |
| CP-030 | CT34.fixtures / #150 | `CT34-FIXTURES-2026-11-09-r1` | **DELEGATED_APPROVED**, §4; disposable environment and isolation interface |
| CP-031 | CT14.coverage / [#27](https://github.com/chi-thanh-nguyen/codemastery/issues/27) | `CT14-COVERAGE-2026-11-09-r1` | **DELEGATED_APPROVED**, §5; placement/coverage-gap map; feature configuration remains conditional |
| CP-032 | CT39.binding / [#153](https://github.com/chi-thanh-nguyen/codemastery/issues/153) | `CT39-BINDING-2026-11-09-r1` | **DELEGATED_APPROVED**, §6; negative selection: no shared Clock bean |
| CP-033 | CT17.F-runtime / [#70](https://github.com/chi-thanh-nguyen/codemastery/issues/70) | No approved revision; §8 disposition only | **PENDING_LEADER_INPUT**, actual resource/custody/exposure facts |
| CP-034 | CT17.F-recovery / #70 | No approved revision; §8 disposition only | **PENDING_LEADER_INPUT**, recovery objectives/custody/authority and separate technical evidence |
| CP-035 | CT34.ci / #150 | `CT34-CI-2026-11-09-r1` | **DELEGATED_APPROVED**, §7; future SH07 job contract, no workflow implementation |
| CP-036 | CT39.consumers / #153 | `CT39-CONSUMERS-2026-11-09-r1` | **DELEGATED_APPROVED**, §6; empty shared-binding consumer set; local alternatives/gates retained |

Read in this verified order. CP-035 has HARD sign-off dependencies on CP-029 and
CP-030, supplied by §§3–4. CP-036 has the HARD dependency on CP-032, supplied
by §6's negative selection. Neither is contingent on the blocked CT17 rows.
There are no cross-batch HARD sign-off prerequisites. CT14's per-feature oracle
rows are CONDITIONAL on their owning effective contracts, not complete feature
implementation. Runtime/QA and deployment dependencies stay separate.
No additional prerequisite slice is necessary or approved.

## 3. CT34.runner

**Adopt the runner configuration already on starting main**, including the
infrastructure introduced by merged [PR #209](https://github.com/chi-thanh-nguyen/codemastery/pull/209)
(merge `882751e0d16556e665cc4cf53dda22ebf9dc7ba6`, 7 November 2026).
The dated Issue assertion that #209 is open is stale. This approval covers only
the runner/fixture scope enumerated here and in §4, not that PR's UI changes,
selected learner journeys, Adaptive cases or historical result claims.

Use `@playwright/test` **1.63.0**, `@types/node` **22.20.5**, and the existing
`e2e/package-lock.json`; no version range, upgrade or parallel browser library.
These are development dependencies. Node must satisfy the existing manifest
`^22.12.0 || ^24.0.0`; the native TypeScript lifecycle-test command additionally
needs Node 22.18+ or 24 as documented. Java 25/Spring Boot 4.1.1 and Boot-managed
JUnit Jupiter 6/Mockito remain unchanged. Use Playwright's matching browser
revisions; a system browser or another Playwright version is not equivalent evidence.

Preserve these exact projects in `e2e/playwright.config.ts`:

| Project | Device/viewport |
|---|---|
| `chromium-desktop` | Desktop Chrome, 1280 × 900 |
| `firefox-desktop` | Desktop Firefox, 1280 × 900 |
| `webkit-desktop` | Desktop Safari, 1280 × 900 |
| `chromium-mobile` | Pixel 7 descriptor from the pinned package |
| `webkit-mobile` | iPhone 14 descriptor from the pinned package |

Preserve existing testDir/globalSetup/output paths; `locale: en-US`, timezone UTC,
60-second test timeout, 10-second assertion timeout, no retries, `fullyParallel`,
three local workers unless explicitly overridden, and CI `forbidOnly`. These
are explicit runner selections from inspected configuration, not production
timeouts, policy defaults or performance acceptance thresholds. Reporter outputs
are list, JSON `test-results/results.json`, HTML `test-results/html` and failure
artifacts under `test-results/artifacts`; video off, screenshots only on failure,
traces retained on failure. Reports may contain synthetic credentials/session
material and must be handled under §7 before sharing.

Existing callable commands from `e2e/`: `npm ci`, `npm test`,
`npm run test:chromium`, `npm run stack:down`, `npm run report` and the documented
`node --test fixtures/*.test.mjs`. Install absent browser revisions explicitly
with the documented `npx playwright install chromium firefox webkit`; CI adds
supported OS dependencies as in §7. These are reproduction instructions, not
claims of execution here. The optional frontend browser harness remains distinct.

Acceptance: a reviewer resolves the manifest/lock to the stated versions,
discovers exactly these five projects and records every attempted project's
pass/fail/skip/launch status. A browser launch failure is blocked/failed evidence,
never PASS or permission to silently reduce coverage. CT19/CT35/CT20.F still own
which cases represent Final acceptance; adopting the runner approves none of them.

## 4. CT34.fixtures

**Select a disposable stack per test run**, reusing the existing
`e2e/fixtures/local-stack.ts`, `auth-fixture.ts`, `catalogue-fixture.sql` and
`stack-down.mjs`. Prefer this over shared developer/demo seed databases: destructive
role setup, parallel account writes and cleanup must stay inside a known disposable
target. No global sample-data loader, new seed, schema or production endpoint is
introduced. The catalogue fixture's 21 published courses, hidden course/lessons
and empty chapter are a runner fixture, not Final educational/evaluation data.

### Setup, outputs and resource ownership

Input is this checkout plus installed locked frontend/E2E dependencies and Docker.
Default setup builds backend/PostgreSQL images from existing Dockerfiles, runs
the exact resulting IDs, and uses a run-owned private network/database. Existing
`E2E_BACKEND_IMAGE`/`E2E_POSTGRES_IMAGE` overrides require explicitly recorded
immutable image IDs tied to the tested source; mutable tags alone cannot establish
which artifact ran. Shared build tags are layer caches, not container identity.
No service/image admission to production follows.

Generate ephemeral DB/JWT/storage/account values per run. Pass secrets through
the environment/stdin, not argv or committed files. Use the actual two distinct
loopback ports selected by setup; there is no fixed E2E origin. Build frontend
against that backend origin; configure backend CORS with that frontend origin.
No wildcard CORS or security bypass is selected. Output to worker environments:
`E2E_STACK=disposable`, actual `E2E_BASE_URL`/`E2E_API_URL`, generated synthetic
Instructor `E2E_USERNAME`/`E2E_PASSWORD`, and `E2E_FIXTURE` identity. Never expose
secrets through frontend `VITE_*` inputs.

Readiness requires the temporary frontend's run marker while its process lives,
healthy current backend, and successful fixture provisioning. Port collision,
process exit, timeout, failed migration/registration/promotion or fixture load
fails setup; tests must not fall back to another server or seed. Preserve inspected
readiness limits: PostgreSQL 90 s, preview 60 s, backend 180 s, final preview
recheck 10 s. They bound test setup only. Capture state and a redacted bounded
backend log tail before cleanup on failure; health alone does not prove a feature.

Instructor creation uses real registration followed by direct role promotion in
**this run's disposable PostgreSQL only**. It is test provisioning, not an Admin
feature or production permission. SQL uses `ON_ERROR_STOP`; partial setup fails
and the whole run's resources are discarded. Existing fixture identities are
reused only inside that isolated DB. Flyway applies the actual versioned schema;
never disable constraints or edit V001–V006 to suit tests.

### Per-test isolation and failure handling

Playwright gives each test its own browser context. `learner` generates a fresh
synthetic account through the real registration API; no shared Learner token or
dependent test ordering. Shared catalogue rows are read-only. A case mutating
shared Instructor/catalogue data needs its owning case contract and isolated
records or explicit serialization; `fullyParallel` is not permission for racing
shared writes. DB effects are not rolled back by browser-context teardown.
Default run teardown owns the full disposable DB, not individual test rollback.

Normal/failing teardown stops its preview and removes only its containers,
attached disposable volumes, network and temporary build directory. It must
not remove project/developer volumes, shared image caches or another live run.
Interrupted-run `stack:down` relies on existing run/PID labels; keep and report
resources with a live or unreadable owner and report removal failures. Cleanup
failure is not successful isolation; record leftovers and use the bounded
recovery command after the owner exits. Starting a fresh isolated run is safe;
automatically replaying a partially executed case against the old DB is not.

Explicit external mode (`E2E_BASE_URL`, optionally `E2E_API_URL`) is restricted
to an authorized disposable/local test environment with synthetic accounts. It
does not seed, own or remove that environment; cases can still register users and
change memberships. It requires a separate cleanup/permission record and cannot
stand in for fixture-specific assertions, which must be listed as skipped.
Never use production, lecturer accounts, real users or personal browser profiles.
No external environment is contacted by this decision task.

Acceptance: FE24/QA08 verify two concurrent runs retain their own image IDs,
ports, marker, accounts and resources; wrong-server/readiness/fixture failures
abort; failures capture redacted evidence; normal/interrupted cleanup preserves
other runs. Existing stub lifecycle tests prove only stub scenarios, not Docker
or browser integration. Future CT19/CT35 fixtures reuse this lifecycle, but must
separately approve their data IDs, validity, expected outcomes and cleanup.
Storage-backed cases additionally need CT09 and its real isolated configuration;
dummy storage env values here prove no MinIO integration.

## 5. CT14.coverage

Approve **placement and the explicit coverage-gap inventory**, with per-feature
oracle rows bound to their own effective revisions. This does not activate the
whole historical CT14 proposal. Preserve CT14.interim, all three 24–25 October
records recognized by ADR004, and **CT14-B1-COVERAGE-2026-11-09-r3** in ADR005
§16 (including retained r2/N1 obligations). Do not fall back to r1's superseded
whole-Course reclassification oracle. ADR006/ADR007 provide their exact approved
behaviors, not implementation proof.

All backend paths below are relative to `backend/src/test/java/com/codemastery/`.
They already exist in the approved inventory. Unit paths run through Surefire;
integration paths use `*IntegrationTest.java` and Failsafe. No test file, production
API, fixture dataset or dependency is created/authorized for implementation here.

| Behavior and owning contract | Existing placement | Evidence responsibility and remaining gate |
|---|---|---|
| Course progress/resume and authoring, ADR006 | `modules/course/LessonProgressServiceTest.java`; `modules/course/application/service/EnrollmentServiceTest.java` only for its membership responsibility; `integration/EnrollmentIntegrationTest.java`, `integration/CourseCatalogueIntegrationTest.java`, `integration/repository/CourseRepositoryIntegrationTest.java` for their matching read/mapping/constraint responsibilities | Course/QA02: authorization, ordering, hidden/missing equivalence, idempotency and real persistence/races. Do not put unrelated authoring tests in Enrollment suites. Dedicated authoring unit placement is a recorded gap if these responsibilities cannot contain it. |
| CT23 question/quiz authoring and publication; CT04 start/save/submit/results, ADR007 | `modules/assessment/AttemptServiceTest.java`, `modules/assessment/QuizGradingServiceTest.java`; `integration/AuthorizationIntegrationTest.java` for real endpoint authorization/no denied effects; `integration/AdaptiveLearningFlowIntegrationTest.java` for Assessment participation in the actual adaptive flow | Assessment owns B3-O01–O13 and reviewed grading values. QA01/QA03 record real security/PG/flow evidence. A dedicated QuestionBank/Quiz authoring unit or standalone Assessment integration suite is a placement gap; no unrelated submission test absorbs it. |
| File submission/deadline/replacement/manual grading, CT10 | `modules/assessment/SubmissionServiceTest.java`; real access-denial cases in `integration/AuthorizationIntegrationTest.java` | Assessment/QA10; CT09/CT10 policies and isolated storage configuration still pending. Dedicated storage success/rollback/recovery integration placement remains a gap requiring a bounded structural decision. Manual grading never triggers real-time mastery. |
| Graph/settings/pure mastery, ADR005 including §§13/15/16 | `modules/adaptive/skillgraph/SkillGraphTest.java`; `modules/adaptive/mastery/AdaptiveSettingsServiceTest.java`, `MasteryCalculatorTest.java`, `MasteryStateClassifierTest.java`; existing two Adaptive integration suites for actual guard/locking/HTTP/transaction obligations | Adaptive/QA03/QA04 preserve B1-O01–O19 and N1-01–06, exact rational policy, DAG isolation, threshold-history race, conditional HTTP and no-content framing. Pure arithmetic does not establish production aggregation or concurrency. |
| Production evidence update/atomic execution, CT08/CT22.atomic | `modules/adaptive/mastery/MasteryUpdateServiceTest.java`; `modules/adaptive/listener/QuizSubmittedEventListenerTest.java`; `integration/AdaptiveLearningFlowIntegrationTest.java`, `integration/MasteryProgressTransactionIntegrationTest.java` | QA03/QA04: real successful commit, induced rollback on either side, duplicate/failure/replay according to future effective CT08 contracts. CT08.evidence approval alone does not supply execution/failure or Course atomic participation. |
| Diagnostic/routing/difficulty/reasons, CT25/CT07/CT24.decision | `modules/adaptive/mastery/PlacementDiagnosticServiceTest.java`; `modules/adaptive/decision/AdaptiveDecisionEngineTest.java`, `NextLessonResolverTest.java`, `PracticeDifficultySelectorTest.java`; existing Adaptive flow integration suite | Adaptive/QA03: await owning callable/oracle revisions; preserve canonical order, no prerequisite hard block and course isolation. No new thresholds or decision policy here. |
| Notifications/reminders, CT11/CT26–CT30; Admin users, CT12/CT31 | `modules/interaction/DeadlineReminderSchedulerTest.java`, `NotificationEventListenerTest.java`; `modules/admin/UserManagementServiceTest.java`; real authorization cases in `integration/AuthorizationIntegrationTest.java` | Interaction/Admin/QA06: actual event creation/delivery, deadline boundaries, authorization and audit cardinality need their owning contracts. Dedicated Q&A/reporting/audit unit/integration inventories not present remain gaps; no broad reassignment into a misleading existing suite. |
| Deterministic profiles and evaluation, CT15/CT36 | `modules/adaptive/evaluation/AdaptiveProfileScenarioTest.java`; existing `support/ScenarioLoader.java`, `support/fixtures/SkillGraphFixtures.java`; versioned existing `backend/src/test/resources/adaptive-scenarios/` locations | Adaptive/QA05: two contrasting profiles and independent measures after CT36/CT07/runtime approvals. Helpers/scaffold names are not approved datasets or oracles; do not infer a passed target. |
| Frontend and actual learner/adaptive journeys | Existing feature/component suites in the approved structure; `e2e/tests/learner-flow.spec.ts`, `e2e/tests/adaptive-flow.spec.ts` | Frontend/QA08; CT34 runner/lifecycle here, CT19/CT35 cases/data and CT20.F allocation remain pending. Presentation doubles do not prove REST/PG/security. New feature test locations still require explicit structural approval. |

Retain `AbstractIntegrationTest`'s shared per-JVM disposable PostgreSQL container,
dynamic datasource, Flyway/validate, no persistent reuse and scheduler-disabled
test profile. Synchronous rollback covers only work actually joining that
transaction. Commit/HTTP/concurrency/thread tests join work before cleanup, track
their own IDs and clean in committed FK-safe transactions; no truncation,
test-order dependency, broad `@DirtiesContext` reset or parallel shared-DB mutation.
Real commit/rollback assertions query independent committed state. Bind time
locally under §6, without sleeps. Use ephemeral JWT values only in consumers
needing them; CT09 still owns real storage configuration. No global permissive
Auth stub or universal JWT/storage requirement is approved.

QA12 owns the coverage ledger: requirement/story and exact contract revision,
case/oracle ID, approved path/layer/owner, fixture/configuration needs, observed
run/revision/environment and explicit unimplemented/blocked/skipped gaps. Feature
owners supply rows as each contract is approved; QA03/04/05/06/10/08 independently
validate their actual boundaries. An absent suitable path is reported and sent
for narrowly scoped structural approval, never silently invented or hidden.
No percentage/latency/load threshold is introduced. Acceptance of CP-031 is this
complete placement/gap map, not a claim all Final coverage is executable or ready.

## 6. CT39.binding and CT39.consumers

**Select no global production Clock bean and no shared Clock consumer API.**
The optional SH04 shared binding is not selected for implementation. This is a
delegated negative technical selection, not Issue closure, a new `retired` status,
or removal of an approved repository path. `support/TestClockConfig.java` remains
an unused scaffold; no bean/signature is silently invented for it or AppConfig.

Reason: ADR007 §8 already approves a constructor-injected, service-local
`java.time.Clock` in existing AttemptService, with UTC production time and fixed/
mutable test time in existing test inventory. ADR006 recovery is also locally
testable without CT39. An extra shared bean adds lifecycle/coupling without a
current missing callable requirement. Keep that compatible alternative instead
of changing effective Auth/JWT/reset/quiz policies or introducing a time service.

The **shared-binding consumer set is empty**. AS08 uses ADR007's approved local
seam, including time sampling after locks, microsecond truncation and regression
failure. AS09 and IN08 may not adopt a shared bean on this approval: deadline and
reminder semantics/seams still require CT10/CT11 approval. Their future local time
inputs can be decided there without making the optional SH04 a mandatory blocker.
No new AS09/IN08 callable or behavior is approved here. Existing JWT policy/time
validation is unchanged; this decision is not permission to mock away security.

Acceptance for the approved AS08 alternative: arrange explicit UTC instants in
AttemptServiceTest and applicable existing integration suites; verify before,
equal-to and after expiry, time captured after contention, and ADR007 clock-regression
failure/rollback without wall-clock sleeps. Reset TTL tests follow ADR006 exactly.
Test time changes are local to each test and cannot affect parallel tests or
production; no HTTP clock-control route, system-clock mutation, global static
mutable clock or externally configurable fake time. AS09/IN08 assertions wait
for their own approved predicates; no default deadline/reminder window is selected.
The JDK Clock supports pluggable/fixed clocks; it does not guarantee monotonicity.
This preserves ADR007's explicit regression handling rather than replacing it.

## 7. CT34.ci

Approve the future **SH07 executable E2E job contract**, after §§3–4, separately
from implementation and from making any check required by branch protection.
The current workflow has no E2E job. Add none in this PR. Preserve all existing
Backend, Frontend, VM and managed CodeQL responsibilities/check names unchanged.

- Trigger on the existing PR-to-main, push-to-main and manual-dispatch events.
  Test the exact checked-out commit (record PR head and tested merge SHA where
  different); never substitute old-head results or pull mutable application images.
- Use the existing `ubuntu-24.04` hosted-runner family with Docker and Node **24**
  (satisfies manifest and native lifecycle-test requirements); backend image builds
  retain their approved Java 25/Spring Boot stack. Existing jobs retain their own
  Node/Java choices. Workflow permissions stay `contents: read`; no repository/
  Project/deployment write credentials, environment secrets or real `.env`.
- Install locked frontend dependencies from `frontend/` with `npm ci` and locked
  E2E dependencies from `e2e/` with `npm ci`. Install the pinned browsers/OS needs
  from `e2e/` using Playwright's documented
  `npx playwright install --with-deps chromium firefox webkit`.
- From `e2e/`, run existing `node --test fixtures/*.test.mjs`, then
  `E2E_WORKERS=1 npm test`. One CI worker is explicitly selected for deterministic
  resource use, not a business concurrency limit. Clear external-target and image
  override variables before setup; run the default owned disposable stack.
  All five projects are selected. No auto-retry, `continue-on-error` or silent
  browser removal. Missing packages/browser, setup, assertion or cleanup failures
  must fail the job. Browser/case skips must be enumerated, never called coverage.
- Capture tested source, lock/package/browser versions, actual image IDs, command,
  environment, test/project totals and failures/skips. Keep list/JSON/HTML and
  failure evidence. In an `always` cleanup step use the existing bounded
  `npm run stack:down` after the test process ends; record cleanup failure/leftovers
  even when tests failed. Do not prune Docker globally or touch live-owner runs.
- Artifact publication must exclude real/generated credentials, authorization
  values, request bodies containing passwords and reusable browser storage. Inspect
  failure traces/reports, which can contain synthetic session material. Publish only
  sanitized evidence; when safe sanitization is unavailable, omit sensitive raw
  artifacts, record that limit and keep safe counts/diagnostics. No arbitrary new
  artifact-retention commitment or public report hosting is selected. Any new action
  version/dependency and artifact-retention configuration need the normal inspected
  implementation decision before a workflow change.

CT34 does not select CT19/CT35 journeys or Final release allocation. SH07 can
implement infrastructure against the inspected existing suite after separate task
authorization, labelling it unaccepted for Final coverage. It cannot claim the
mandatory Final main-flow gate satisfied until CT19/CT35/CT20.F and actual execution
evidence supply the required selected scope. A green job with missing required
cases, zero tests or unexplained skips is insufficient acceptance. QA08 owns actual
browser/case coverage; QA12 owns the exact-revision CI evidence handoff to DO04.

Acceptance of later SH07 implementation: all five projects are discoverable/run,
the owned stack/cleanup match §4, deliberately failed setup/assertions produce
failure, artifacts respect the credential boundary, and other required jobs remain
unchanged. Enforcing this job as a required check remains a separate Leader decision;
this contract changes no protections and grants no CI/risk/QA acceptance.

## 8. CT17 blocked scope and smallest remaining decisions

**CP-033 and CP-034 are BLOCKED for approval, PENDING_LEADER_INPUT.** This is an
unapproved disposition, not an approved CT17 revision. Rechecked the local Leader
register (LD-05), live #70 and the later repository deployment/security records.
No complete, authoritative Final input packet was found. Operator-reported Azure
preview, approved TLS path conventions or engineering assignment do not supply
actual resource/custody/access/exposure authority.

CP-033 needs the Leader's actual VM/account/budget/capacity identity, origin/DNS/TLS
and renewal/key custodian, operator/emergency access/grants, approved production
configuration/secret locations/custodians and public-exposure authorization.
Record identities and access references securely, never secret values. Reuse
approved one-VM Compose, P1–P10/B1–B3/P6 and current documented locations where
actually authorized; do not invent replacements or adopt historical price estimates.

CP-034 needs the Leader's RPO/RTO, backup frequency/retention, authorized off-VM
destination and key custodian, recovery operator/authority and accepted data-loss
commitment. Reuse CT17-FINAL-2026-10-25-r1 F1/F2's PostgreSQL logical mechanism and
coordinated qualification interface. Full MinIO objects/versions/IAM/config capture/
restore and concrete encryption/transport still need supported technical evidence
and isolated round-trip qualification. Resource facts alone do not prove these.
The prior exact-image residual-risk/admission/signing/volume-migration gates remain.

Best recommendation: retain the six independently approved test decisions in one
focused PR, keep these two checkpoints unapproved, and obtain the actual non-secret
Leader input packet before their technical compatibility/qualification review.
No predecessor amendment is recommended. DP01/DP03 and CT20.F retain their applicable
deployment/release gates; no provisioning, backup/restore or public exposure occurs.

## 9. Compatibility, handoffs and verification responsibilities

No public REST endpoint/payload, database mapping/migration, application module,
event schema or runtime configuration changes. OpenAPI, data-model and architecture
therefore need no new normative API/schema definitions. Preserve ADR005's exact
accepted corrections, ADR006's bounded exceptions and ADR007's local Clock,
Course guard, expiry and evidence-only boundaries. The two negative CT39 selections
introduce no new cross-module API and do not close SH04.

Feature/test owners retain the existing CODEOWNERS assignments. FE24 implements/
reconciles only the approved runner/lifecycle after separate authorization;
FE25/CT19 and FE26/CT35 select cases/data separately. SH07 implements §7;
SH04's shared binding is not selected. QA03/04/05/06/10/12 use §5's precise
allocations/gaps; QA08 independently executes the selected browser coverage.
No owner confirmation, test result, implementation completion or readiness promotion
is inferred from these handoffs. Issue checklists requiring personal Leader or
module-owner review stay unchecked without that evidence.

Technical alternatives considered: reuse the existing locked runner versus replacing
it (unnecessary dependency/churn); disposable per-run DB versus shared demo seeds
(shared data/cleanup risk); explicit placement/gap map versus new unauthorized paths;
local Clock versus shared bean (existing contract suffices). No prior contract needs
amendment, no residual risk is accepted, and no additional prerequisite is approved.

Documentation checks cover checkpoint identity/order, links, approved source/lock/
project alignment, previous-definition preservation and diff scope. They do not prove
the future CI job, business tests, Docker/browser stack, operational recovery or
Final academic acceptance. Exact checks actually run are reported in the PR.

Implementation guidance was checked against primary documentation:
[Playwright projects](https://playwright.dev/docs/test-projects),
[fixtures](https://playwright.dev/docs/test-fixtures),
[CI](https://playwright.dev/docs/ci-intro) and
[Java 25 Clock](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/time/Clock.html).
Those documents explain tool capability, not project approval or current runtime proof.
Known AI assistance: OpenAI Codex inspected sources and authored these contract
decisions/documentation under the explicit delegation; no AI runtime is introduced.
