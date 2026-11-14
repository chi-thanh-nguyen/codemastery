# ADR013: Batch 8 Final Evaluation Data, E2E Cases, Coverage and Residual Contract Reconciliation

## 1. Authority, stacking and provenance

**DELEGATED_TECHNICAL_APPROVAL; DELEGATED_APPROVED** (one checkpoint
**DELEGATED_PARTIALLY_APPROVED**), 15 November 2026 (Asia/Ho_Chi_Minh), under the
same explicit Project Leader instruction as [ADR012](012-batch-07-contract-approvals.md):
**“CodeMastery — Final Contract Approval Campaign: Batch 7, Batch 8 & Remaining
Technical Contracts”**. Claude Code technically approves the eligible Batch 8
checkpoints and records the final reconciliation of all 100 roadmap checkpoints and
of residual contract obligations. This is not personal Leader approval, module-owner
review or a GitHub approving review. Merge, deployment, spending, credential access,
residual-risk acceptance and amendment of effective Leader-reserved contracts are
excluded. ADR012 §1's implementation-start confirmation applies to the Batch 8
consumers once their start prerequisites (§13) are satisfied.

**Stacked on ADR012.** Starting `origin/main` is
**30f9a6c16443a4a0ad5eee93075df29d69adfc3b**; this record is prepared on the open
Batch 7 branch (PR #244, `docs/batch-07-contract-approvals`). That stacking is the
**only** Batch 7 dependency: two of the learner E2E cases selected in §7 exercise
ADR012's graded-assignment notification and lesson Q&A contracts, and §11's release
allocation refers to them. Until PR #244 is integrated, those two cases are not
start-ready and this record must not be integrated before ADR012. Every other
decision here depends only on contracts already effective on `main`.

Planning sources: `run-20261108-135024/BATCH-08.md`, corrected by
`verification-20261108-151823/APPROVAL-ROADMAP.md` and `registers/revised-plan.json`
(via `local_ids`). Historical CR groups are not Batch 8. Verified membership:
**CP-091–CP-100, ten checkpoints, five Issues**; nothing is rebucketed. Leader
register: **LD-12(d)** (adopt the merged PR #209 learner E2E cases) is decided in
§7; **D-17** and **D-19** are these checkpoints and are decided here within the
delegation, except the items in §14. LD-05 was resolved by ADR009 r2. Additional
sources: requirements §§5–6, 8.4, 10.7; architecture §6, US-01–US-09; ADR002
CT15.I-data, CT15-SD r3, CT20.I and CT36.I-results; ADR005 §§6/13.4; ADR008
§§3–7; ADR009 §§9–11; ADR011 §§7–11/15–16; `sample-data/README.md`; the merged
`e2e/` suite, configuration and fixtures; the empty `adaptive-flow.spec.ts`, seed
files 03–05 and scenario JSON placeholders; open PRs #240/#241 (FE24/SH07
implementation, not inspected for approval). Known AI use: Claude Code (Anthropic)
authored this record; no AI runtime is added.

## 2. Inventory, dependency order and dispositions

| CP | Checkpoint / Issue | Revision | Disposition | Section |
|---|---|---|---|---|
| CP-091 | CT15.F-admin-account / [#28](https://github.com/chi-thanh-nguyen/codemastery/issues/28) | `CT15-F-ADMIN-ACCOUNT-2026-11-15-r1` | DELEGATED_APPROVED | §3 |
| CP-092 | CT36.F-scenarios / [#26](https://github.com/chi-thanh-nguyen/codemastery/issues/26) | `CT36-F-SCENARIOS-2026-11-15-r1` | DELEGATED_APPROVED | §5 |
| CP-093 | CT15.F-assessment / #28 | `CT15-F-ASSESSMENT-2026-11-15-r1` | DELEGATED_APPROVED | §4.3 |
| CP-094 | CT36.F-measures / #26 | `CT36-F-MEASURES-2026-11-15-r1` | DELEGATED_APPROVED | §6 |
| CP-095 | CT15.F-adaptive / #28 | `CT15-F-ADAPTIVE-2026-11-15-r1` | DELEGATED_PARTIALLY_APPROVED (demonstration policy values PENDING_LEADER_APPROVAL, §14.1) | §4.2 |
| CP-096 | CT19.cases / [#140](https://github.com/chi-thanh-nguyen/codemastery/issues/140) | `CT19-CASES-2026-11-15-r1` | DELEGATED_APPROVED | §7.1 |
| CP-097 | CT19.fixtures / #140 | `CT19-FIXTURES-2026-11-15-r1` | DELEGATED_APPROVED | §7.2 |
| CP-098 | CT35.cases / [#151](https://github.com/chi-thanh-nguyen/codemastery/issues/151) | `CT35-CASES-2026-11-15-r1` | DELEGATED_APPROVED | §8.1 |
| CP-099 | CT35.fixtures / #151 | `CT35-FIXTURES-2026-11-15-r1` | DELEGATED_APPROVED | §8.2 |
| CP-100 | CT20.F / [#17](https://github.com/chi-thanh-nguyen/codemastery/issues/17) | `CT20-F-2026-11-15-r1` | DELEGATED_APPROVED | §11 |

The revised plan has **25 relationships** into Batch 8 (15 HARD, 10 CONDITIONAL).
HARD sources outside the batch are effective on `main`: CP-006 CT06.production,
CP-009 CT06.enablement (ADR005), CP-050 CT24.decision and CP-051 CT07.routing
(ADR011), CP-030 CT34.fixtures r2 (ADR008) and CP-033 CT17.F-runtime r2 (ADR009).
Conditional edges: CP-019 CT23.questions → CP-093 and CP-008 CT05.mapping → CP-095
apply (the dataset obeys their validity rules); CP-011 → CP-096 and CP-026 → CP-096
apply (L-N1 and L-N2 exercise progress and quiz results); CP-057 → CP-098 applies
(the adaptive case exercises quiz-driven mastery); CP-041 → CP-093 does **not**
apply (no submission or grade rows are seeded); CP-095 → CP-099 does **not** apply
(E2E data are built per run, §8.2, not loaded from SD03); the three VERIFY-C1 edges
from GOV.HC-U20 (CP-091, CP-093, CP-095) no longer apply because LD-01 ruled
CT15-SD r3 effective (ADR004).

## 3. CT15.F-admin-account — trial accounts per role

Requirement §8.4 asks for trial accounts for each role; §10.7 for credentials in a
secure location. Selected:

- **Dataset accounts.** Dataset `final-v1` (§4.1) adds synthetic accounts with
  UUIDv5 IDs: `user/admin` → `admin@example.test`, “Administrator Demo”, `admin`;
  `user/instructor` → `instructor@example.test`, “Instructor Demo”, `instructor`;
  `user/learner` → `learner@example.test`, “Learner Demo”, `learner`; and the three
  profile learners of §4.2. All `active`, all in existing `01-users.sql` (SD01
  extension), loaded with the same insert-only, no-overwrite, attested-target rules
  as `interim-v1`.
- **Passwords.** Never committed. A distinct password per account is chosen at load
  time; its BCrypt `$2b$` cost-14 hash is produced by the documented Step 3 procedure
  (15 characters to 72 UTF-8 bytes) and passed through environment variables, never
  argv, files, logs or `VITE_*` inputs. The seed rejects a missing or malformed hash.
- **Deployed trial accounts.** On the deployment target the operator loads the same
  accounts only after the Leader authorizes demonstration seeding there (this record
  does not), generates fresh high-entropy passwords on a trusted machine and hands
  them to the lecturer only through a Leader-controlled secure location. Credentials
  never enter the repository, Issues, PRs, CI logs, reports or screenshots.
- **No Administrator feature.** Administrator accounts exist only through seeding;
  there is no admin registration or promotion endpoint. CT34's direct role promotion
  stays limited to the disposable E2E database.

Excluded: actual credential values, their custody and rotation/lock after grading
(operator and Leader actions), production seeding authorization and any new loader
or script.

## 4. Final datasets

### 4.1 Common rules

Dataset key `final-v1`. Every ID is a UUIDv5 in the standard URL namespace for
`https://codemastery.test/final-v1/<logical-key>`, like `interim-v1`. Seeds are
insert-only and idempotent for identical rows, stop on any conflicting row, run only
against an attested disposable or Leader-authorized target, contain no credential,
mastery, attempt, progress or recommendation row, and keep demo data out of Flyway
migrations. Content is original English text written by the team; the only external
file is the already-licensed `sample-data/materials/sample-slides.pdf`. Values that
are load-time inputs are named below; nothing else varies.

### 4.2 CT15.F-adaptive (SD03) — partially approved

**Course.** `course/python` “Python Fundamentals”, published, owned by
`user/instructor`, in existing `02-course-python-fundamentals.sql` alongside the
unchanged `interim-v1` rows. Six Chapters and twenty Lessons in canonical order
L01–L20, all published, each with one published text material; L01 also gets the
PDF file material, provisioned through the CT09 upload operation after loading (no
direct object-store write). No external video URL is selected.

**Skills and graph** (existing `03-skill-graph-and-lesson-mapping.sql`). Edge
`s → p` means p is a direct prerequisite of s. Every Lesson Lk maps exactly skill Sk.

| Chapter | Lesson / skill | Prerequisites |
|---|---|---|
| 1 Basics | S01 Program Output | — |
| | S02 Variables and Assignment | S01 |
| | S03 Numbers and Arithmetic | S02 |
| | S04 Strings | S02 |
| | S05 Input and Type Conversion | S03, S04 |
| 2 Decisions | S06 Booleans and Comparisons | S03 |
| | S07 Conditionals | S06 |
| 3 Loops | S08 While Loops | S07 |
| | S09 For Loops | S07 |
| | S10 Loop Control | S08, S09 |
| 4 Collections | S11 Lists | S09 |
| | S12 List Iteration Patterns | S09, S11 |
| | S13 Tuples and Sets | S11 |
| | S14 Dictionaries | S11 |
| 5 Functions | S15 Functions | S07 |
| | S16 Parameters and Return Values | S15 |
| | S17 Variable Scope | S16 |
| 6 Text, errors and files | S18 String Methods and Formatting | S04, S09 |
| | S19 Exceptions | S15 |
| | S20 File Input and Output | S09, S19 |

Twenty skills and twenty-four edges, all pointing to an earlier skill, so the graph is
acyclic by construction (ADR005 CT05 validity). Skill names are unique and stored
verbatim; they appear in reasons (CT07.reasons).

**Profiles** (existing `05-learner-profiles.sql`): `user/p1` `p1@example.test`
“Profile P1 Strong Foundation”, `user/p2` “Profile P2 Loops Gap”, `user/p3`
“Profile P3 Complete Beginner”, each with an `active` Enrollment in `course/python`
and nothing else. Their mastery, progress and recommendations are produced only by
taking the quizzes through the application (UI or REST) according to §5's scripts.

**Adaptive settings.** `adaptive_enabled = true` with a policy supplied as three
load-time inputs validated against ADR005 §6.2 (`weak`, `mastered`,
`minimumQuestionsPerSkill`). The seed carries **no** numeric value. The concrete
demonstration values are `PENDING_LEADER_APPROVAL` (§14.1); until approved, SD03 may
be authored and tested with explicitly named test values, but the demonstration
course must not be loaded with invented values.

### 4.3 CT15.F-assessment (SD04)

Existing `04-questions-quizzes-assignments.sql`, CT23 validity rules unchanged:

- **Questions:** six per skill (two `easy`, two `medium`, two `hard`), 120 in total,
  each tagged with exactly one Course skill and one difficulty; kinds mixed among
  `single_choice`, `multiple_choice` and `true_false`; English, original, with
  unambiguous answer keys reviewed by Assessment and Adaptive owners.
- **Quizzes (published):** `quiz/placement` (`diagnostic`): the first `easy` and
  first `medium` question of S01–S10 (20 questions) — CT25 scope is therefore
  S01–S10; `quiz/practice/Sk` (`practice`), one per skill with all six of its
  questions (20 quizzes); `quiz/final-test` (`test`): the first `hard` question of
  every skill (20 questions). Time limits are content values chosen by the SD04
  author and reviewed in the SD04 PR; they have no policy meaning.
- **Assignments (published):** `assignment/loops` bound to L10 and
  `assignment/files` bound to L20, PDF or ZIP submissions (CT09). Their deadlines are
  two load-time inputs that must be in the future at load; no submission or grade row
  is seeded.

Readiness: with six questions per skill, the Course is adaptive-ready for any
approved `minimumQuestionsPerSkill` ≤ 6.

## 5. CT36.F-scenarios

**Baseline.** The fixed linear path of the same Course: L01–L20 in canonical order,
no test-out and no remedial step (path length 20).

**Policy independence.** Scripts use only all-correct (rate 1) and all-incorrect
(rate 0) answers for a skill. Because every valid policy has `0 < weak < mastered ≤ 1`
(ADR005 §6.2), rate 1 is always `Mastered` and rate 0 always `Weak`, so the expected
states and routes below hold for any approved policy. Unassessed skills stay Unknown.

| Profile | Script (placement quiz, CT25) | Expected after processing (ADR011 §7) |
|---|---|---|
| P1 strong foundation | All 20 answers correct | S01–S10 Mastered; `skip` L01–L10; `continue` L11 (“Continue with the next lesson in the course order.”) |
| P2 Loops gap | S01–S07 correct; S08–S10 incorrect | S01–S07 Mastered, S08–S10 Weak; `skip` L01–L07; `remedial` L08 with reason `Review this lesson first: your skill "Loop Control" is Weak and its prerequisite "While Loops" is not yet Mastered.` |
| P3 complete beginner | All 20 answers incorrect | S01–S10 Weak; no `skip`; `remedial` L01 with reason `Review this lesson first: your skill "Variables and Assignment" is Weak and its prerequisite "Program Output" is not yet Mastered.` |

Derivation for P2: Weak skills in Lesson order are S08, S09, S10; S08 and S09 have
only the Mastered prerequisite S07 (no candidate); S10's candidates S08 and S09 have
targets L08 and L09, so L08 wins by target index. P1 and P2 therefore receive
different next recommendations (US-06), and all three Mastery Maps differ (US-07).

**Prerequisite-gap scenarios G01–G24.** One per graph edge `s → p`, numbered in the
§4.2 table order (skill, then prerequisite as listed: G01 S02 → S01, G02 S03 → S02, …,
G10 S10 → S08, G14 S12 → S11, G24 S20 → S19). In each, every skill is
Mastered except s and p, which are Weak. Expected: `remedial` to L(p) (p comes first
in Lesson order but has no unmastered prerequisite; s's only candidate is p, whose
Lesson is incomplete because it was not tested out). The reason names s and p.

**Mastery-update cases M01–M10** (ADR005 §13.4 handoff): M01 all-correct → Mastered;
M02 all-incorrect → Weak; M03 one-question attempt correct → Mastered; M04
one-question attempt incorrect → Weak; M05 alternation Mastered → Weak → Mastered
follows the latest attempt; M06 an older 6/6 result replaced by a newer 0/1 → Weak;
M07 a skill not assessed by an attempt keeps its state; M08 the placement quiz
initializes only S01–S10; M09 (policy-dependent) the smallest k with
`weak ≤ k/6 < mastered` on a six-question attempt gives Learning, reported N/A when
no such k exists; M10 adding questions to the bank without a new attempt changes no
state.

**Oracle rule.** Expected outcomes are derived by hand and by an independent
re-implementation of ADR011 §7 from this dataset, reviewed by the Adaptive owner
**before** the system is run, and stored in the existing scenario files
`backend/src/test/resources/adaptive-scenarios/p1-strong-foundation.json`,
`p2-loops-gap.json` and `p3-complete-beginner.json` (with G and M cases in the
existing evaluation test). Outputs never redefine an expectation. The ≥80% routing
target (§6) is an evaluation target, never a mastery threshold.

## 6. CT36.F-measures

All measures run against an exact commit on a disposable PostgreSQL instance with
`final-v1` and the recorded policy values.

| Measure | Procedure | Reporting |
|---|---|---|
| Path divergence | Per profile after its script: skipped = number of `skip` rows; remediated = number of distinct `remedial` rows; divergence = skipped + remediated, against the baseline's 0. Expected P1 10 (10 + 0), P2 8 (7 + 1), P3 1 (0 + 1). | Table per profile with rows and reasons |
| Routing correctness | G01–G24 at rule level (`AdaptiveDecisionEngine.decide` with the scenario's states) **and** at integrated level for P2, P3 and G10/G14/G24 (edges S10 → S08, S12 → S11, S20 → S19) by real quiz submissions. Correct = current recommendation is `remedial` to the expected Lesson. | Percentage per level with numerator/denominator; target ≥ 80% (architecture), reported, not assumed |
| Mastery-update correctness | M01–M10 by real submissions through the quiz-to-mastery unit (ADR011 B6-V cases may be reused). | Passed / applicable cases; N/A cases listed |
| Learning gain | Simulated only: P2 completes L08 and L10, then retakes `quiz/practice/S10` with a scripted all-correct answer set; gain = post score − pre score for S10, compared with the baseline P2 who receives no remedial step. Labelled as a demonstration of the measurement procedure, **not** evidence of learning. | Pre, post and difference with the explicit limitation |

Every report records the commit SHA, dataset key, policy values, command, date,
pass/fail/N/A per case and any deviation; failures are reported, not hidden or
re-baselined. A pilot with real students is **not selected**: it would process real
personal learning data and needs consent and a Leader decision (§14.2).

## 7. CT19 — learner E2E suite (F20)

### 7.1 CT19.cases (LD-12(d): adopt the merged learner cases)

The fourteen tests of the merged `e2e/tests/learner-flow.spec.ts` (PR #209, adopted
as runner infrastructure by ADR008) are **adopted as selected cases** without
retroactively accepting their historical results:

| Case | Existing group | Story |
|---|---|---|
| L-A1 | catalogue and course detail (anonymous), 3 tests | US-02 |
| L-A2 | public landing and Learner start, 3 tests | US-02, CT16 |
| L-A3 | accounts and sessions, 5 tests | US-01 |
| L-A4 | membership, 2 tests | US-02 |
| L-A5 | REST contract smoke, 1 test | US-01/02 validation |

New cases to implement in the same file (FE25):

| Case | Journey | Story / contracts |
|---|---|---|
| L-N1 | Enrolled learner opens a Lesson, marks it complete, sees the progress percentage change; Resume opens the last visited Lesson | US-04; CT22.progress/resume |
| L-N2 | Learner starts a timed quiz, answers, submits, sees per-question correctness and the attempt in history | US-05; CT04 |
| L-N3 | Learner submits a PDF before the deadline; the run's Instructor grades it through REST; the learner sees grade, feedback and a notification | US-08/US-13; CT09, CT10, CT27 (**ADR012**) |
| L-N4 | Learner asks a question on a Lesson; the Instructor replies through REST; the learner sees the reply and a notification; the learner reports a post and gets a receipt | US-09; CT30, CT11 (**ADR012**) |

Password recovery is excluded (CT02 is verified by its own suites; no mailbox in
E2E). Each case states its story in its title. L-N3/L-N4 become start-ready only
after ADR012 is integrated.

### 7.2 CT19.fixtures

Reuse CT34.fixtures r2 unchanged: one disposable stack per run, the catalogue fixture
for L-A1, per-test synthetic accounts through the existing `auth-fixture.ts` helpers,
and the run's synthetic Instructor (`E2E_USERNAME`/`E2E_PASSWORD`). Course content
for L-N1–L-N4 is created per test through the approved Instructor REST operations
(CT21, CT23, CT10) with titles made unique by the test ID, so parallel workers never
share mutable data. The PDF is generated in the test (bytes starting with `%PDF-`);
deadlines are explicit case values relative to the test clock. No shared demo seed,
direct SQL (other than CT34's existing catalogue load and role promotion), new
fixture file or cleanup step is used; the disposable database is discarded with the
stack.

## 8. CT35 — adaptive E2E suite (F21)

### 8.1 CT35.cases

In existing `e2e/tests/adaptive-flow.spec.ts`, on a small Course mirroring the SD03
loops sub-graph: K1 Conditionals; K2 While Loops → K1; K3 For Loops → K1; K4 Loop
Control → K2, K3; K5 Lists → K3; Lessons E1–E5 map K1–K5 one to one; the placement
quiz has two questions for each of K1–K4.

| Case | Journey | Expected (policy-independent, ADR011 §7) |
|---|---|---|
| A-1 contrasting profiles | Learner A answers the placement quiz all correct; Learner B answers K1 correctly and K2–K4 incorrectly | A: E1–E4 tested out and labelled Mastered, guidance `continue` E5. B: E1 tested out, guidance `remedial` E2 with reason `Review this lesson first: your skill "Loop Control" is Weak and its prerequisite "While Loops" is not yet Mastered.` Mastery Maps differ (US-03, US-04, US-06, US-07). |
| A-2 no hard block | Learner B opens E5 | A prerequisite warning names "For Loops" and the Lesson opens (US-06). |
| A-3 skipped placement | Learner C skips the placement offer | No mastery row is written and the default path starts at E1 (US-03, CT25.skip). |

At least two contrasting profiles (A and B) are selected, satisfying the CT35
criterion. Quiz-driven mastery runs through the real submission unit (CP-057).

### 8.2 CT35.fixtures

Per run and per test, inside CT34's disposable stack: the run's Instructor builds the
Course, skills, edges, mappings, questions, placement quiz and adaptive settings
through approved REST operations, then enables adaptive mode; learners register
through the API. The policy is an explicitly named test value; any valid policy gives
the same results. SD03 is not loaded in E2E, and no profile data are shared between
tests or runs.

## 9. Responsibilities unlocked

SD01 (admin account extension), SD03 (except the demonstration policy values), SD04,
QA05 (evaluation execution and report), FE25 (L-N1/L-N2 now; L-N3/L-N4 after ADR012),
FE26, QA08 (execution and evidence), DO03 (Final report allocation). Each still needs
its implementation, execution and the reviews in §13.

## 10. Verification data obligations

`AdaptiveProfileScenarioTest` (existing) holds the rule-level P, G and M cases; the
existing `AdaptiveLearningFlowIntegrationTest` holds the integrated P1–P3, G10/G14/G24
and M cases on real PostgreSQL; `ScenarioLoader`, `CourseFixtures` and
`SkillGraphFixtures` (existing) build the `final-v1` graph for tests. No new file,
dependency or result is claimed; all cases are specified, NOT_RUN.

## 11. CT20.F — Final coverage allocation (U33/U34)

| Final obligation | Evidence that represents it | Release-blocking? |
|---|---|---|
| Core functions (requirements §4) | Backend unit and real-PostgreSQL integration suites at ADR008 §5 placements; CT14 F1 authorization regression; OpenAPI conformance of implemented operations | Yes, for implemented operations |
| Security (requirements §6) | `AuthorizationIntegrationTest`, password hashing and token tests, secret scans, DP01/ADR003 admission evidence | Yes, plus the Leader decisions in §14 |
| **E2E, main flow (U33)** | CT19 L-A3, L-A4, L-N1 and L-N2 pass on `chromium-desktop` **and** `chromium-mobile` | **Yes** |
| **Adaptive E2E (U34)** | CT35 A-1 passes on `chromium-desktop` | **Yes** (advanced component in the main flow) |
| Other selected E2E cases and projects | Remaining CT19/CT35 cases on all five CT34 projects | Attempted and reported with failures, skips and launch blocks; not release-blocking |
| Adaptive evaluation | CT36.F-measures report (QA05) | Yes: the report must exist; the ≥80% target is reported, not a gate on release |
| Deployment | CT17.F-runtime/recovery r2 qualification, smoke checks of the deployed URL and one sign-in per trial role | Yes |
| Sample data | Disposable load and login of SD01–SD04 (QA13) | Yes |
| CI | CT14 F2 jobs and CT34.ci SH07 on the release commit | Yes |

Release evidence is collected on one exact commit and recorded with commands, dates,
browsers and results. Both E2E suites are therefore required at release, but only for
the named cases and projects; neither empty suite is required by default. CT20.I is
unchanged.

## 12. Complete contract reconciliation — 15 November 2026

**All 100 roadmap checkpoints are accounted for.** No checkpoint was renamed,
rebucketed or double-counted; prerequisite slices are counted separately.

| Scope | Checkpoints | Approved | Partial / pending | Record |
|---|---:|---:|---:|---|
| Batch 1 (CP-001–010) | 10 | 10 (CP-001 GOV.HC-U20 decided by LD-01) | 0 | ADR004, ADR005 (#227, #228) |
| Batch 2 (CP-011–018) | 8 | 8 | 0 | ADR006 (#229) |
| Batch 3 (CP-019–028) | 10 | 10 | 0 | ADR007 (#230) |
| Batch 4 (CP-029–036) | 8 | 8 | 0 | ADR008, ADR009 (#231, #233) |
| Batch 5 (CP-037–046) | 10 | 10 | 0 | ADR010 (#235) |
| Batch 6 (CP-047–063) | 17 | 17 | 0 | ADR011 (#236) |
| Batch 7 (CP-064–090) | 27 | 27 | 0 | ADR012 (#244, open) |
| Batch 8 (CP-091–100) | 10 | 9 | 1 (CP-095 policy values) | this record (open) |
| **Total** | **100** | **99** | **1** | |

Separately counted prerequisite or companion slices approved outside the roadmap
count: Batches 1–6 records as listed in the decision index; Batch 7 adds five
(ADR012 §3); Batch 8 adds none.

**Residual obligations, classified** (none is eligible for new delegated approval):

| Item | Classification | Next action |
|---|---|---|
| CT15.F-adaptive demonstration policy values | PENDING_LEADER_APPROVAL | §14.1 |
| Real-student learning-gain pilot | PENDING_LEADER_APPROVAL (privacy, consent); not selected | §14.2 |
| ADR011 §17 adaptive practice capture (CT04-START/CT08-EVIDENCE amendment) | PENDING_LEADER_APPROVAL; review recommendation: defer beyond Final; status unchanged | Leader decision when wanted |
| CT17-P6 runtime grants for Batch 2–7 tables | PENDING_LEADER_APPROVAL (CT17-P6 r1/r2 are Leader-approved; an extension needs the same authority) | Leader approval of a r3 matrix drafted from implemented consumers |
| CT14 dedicated Interaction/Admin (and storage) integration suites | PENDING_LEADER_APPROVAL (bounded structural change) | §14.3 |
| ADR010 submission-download hostile content; ADR011 §5 operational risks; AU07 reset JWT risk; ADR003 residual risks (HC-U21) | PENDING_LEADER_RISK_ACCEPTANCE | Leader acceptance or mitigation before exposure |
| DP01 F1, F4, F5, F6, F10, F11 and image admissions | PENDING_LEADER_APPROVAL (release security) | Leader decisions before release |
| LD-02 notification scope; LD-09 review route; LD-11 joint review | Open Leader / review decisions; none blocks a contract | Leader |
| CT17 technical approval vs. actual VM/runtime/recovery admission | IMPLEMENTATION_OR_VERIFICATION_GATE_ONLY | Execute ADR009 qualification and record acceptance |
| CT18 whole-family criteria | ALREADY_EFFECTIVELY_APPROVED for every operation in scope (CT18.minimum plus per-batch mappings); no new error scope is needed | None |
| CT38.remaining | DEFERRED (only the approved feature slices exist) | Approve further slices when a feature needs them |
| Module-owner reviews for ADR005–ADR013, CI, protected integration of #244 and this PR | IMPLEMENTATION_OR_VERIFICATION_GATE_ONLY (review gates) | Reviewers and Leader |

## 13. Retained gates

**Start prerequisites:** independent CODEOWNER and module review of this record —
Adaptive and scenarios @thanhtran0311, Assessment @minhtan29042006, Course
@TamiaoNguyen, Auth/SD01 @duyduyit, Frontend/E2E @heizseung, together with the Leader
CODEOWNER — required CI and protected integration **after** ADR012. Then the
implementation-start confirmation (ADR012 §1) applies within each Issue's scope.

**Still required:** authoring SD01 (admin extension)/SD03/SD04 and their disposable
load evidence; Leader approval of the demonstration policy values before loading
the demonstration Course; FE25/FE26 implementation and FE24/SH07 (#240/#241) review;
QA05/QA08 execution; deployment admission, demonstration seeding authorization and
credential handover on the target; every item of §12. No result, deployment,
credential handling or risk acceptance is implied.

## 14. Leader decisions requested

### 14.1 Demonstration policy values for `course/python`

**Smallest decision:** approve, revise or decline these values as the CT15.F-adaptive
demonstration inputs. **Recommended:** `weak = 1/2`, `mastered = 4/5`,
`minimumQuestionsPerSkill = 6`. Rationale: on a six-question practice quiz this
partitions results cleanly (0–2 correct Weak, 3–4 Learning, 5–6 Mastered), and on the
two placement questions per skill 0, 1 and 2 correct give Weak, Learning and Mastered;
`6` equals the per-skill bank, so readiness proves the complete SD04 bank is loaded.
The values are chosen from this partition, not from the separate ≥80%
routing-correctness evaluation target, which stays no mastery rule. The scripted scenarios do not depend on these values (§5). Affects SD03,
the live demonstration and M09 only.

### 14.2 Real-student pilot

**Recommended:** do not run one for Final; report simulated learning gain with its
limitation. Running one needs informed consent, anonymization and a data-handling
decision.

### 14.3 Interaction/Admin integration test placement

**Recommended:** authorize one file,
`backend/src/test/java/com/codemastery/integration/InteractionAdminIntegrationTest.java`,
extending the existing `AbstractIntegrationTest`, for ADR012's real-PostgreSQL cases
(after-commit isolation, derived-ID idempotence, audit atomicity, moderation and
metrics). Without it those cases stay in existing unit suites and real database
evidence for QA06 remains a gap.

## 15. Preparation validation — 15 November 2026

Validated before commit: Markdown links and anchors in changed files;
`git diff --check`; secret and artifact scan; arithmetic of §5–§6 expectations
re-derived from the §4.2 graph. No OpenAPI change. No backend, frontend, E2E or seed
command was run because this is documentation only.
