# ADR013: Batch 8 Final Evaluation Data, E2E Cases, Coverage and Residual Contract Reconciliation

## 1. Authority, stacking and provenance

**DELEGATED_TECHNICAL_APPROVAL; DELEGATED_APPROVED** (one checkpoint first
**DELEGATED_PARTIALLY_APPROVED**, completed by r2 in §16.1), 15 November 2026 (Asia/Ho_Chi_Minh), under the
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
| CP-093 | CT15.F-assessment / #28 | `CT15-F-ASSESSMENT-2026-11-15-r2` (r1 → r2, §19) | DELEGATED_APPROVED | §§4.3, 19.6 |
| CP-094 | CT36.F-measures / #26 | `CT36-F-MEASURES-2026-11-15-r1` | DELEGATED_APPROVED | §6 |
| CP-095 | CT15.F-adaptive / #28 | `CT15-F-ADAPTIVE-2026-11-15-r1`, completed by `CT15-F-ADAPTIVE-2026-11-15-r2` | r1 DELEGATED_PARTIALLY_APPROVED (policy values pending); **r2 DELEGATED_APPROVED** under the second instruction (§16.1) | §§4.2, 16.1 |
| CP-096 | CT19.cases / [#140](https://github.com/chi-thanh-nguyen/codemastery/issues/140) | `CT19-CASES-2026-11-15-r2` (r1 → r2, §18) | DELEGATED_APPROVED | §§7.1, 18 |
| CP-097 | CT19.fixtures / #140 | `CT19-FIXTURES-2026-11-15-r2` (r1 → r2, §18) | DELEGATED_APPROVED | §§7.2–7.3, 18 |
| CP-098 | CT35.cases / [#151](https://github.com/chi-thanh-nguyen/codemastery/issues/151) | `CT35-CASES-2026-11-15-r2` (r1 → r2, §19) | DELEGATED_APPROVED | §§8.1, 19.7 |
| CP-099 | CT35.fixtures / #151 | `CT35-FIXTURES-2026-11-15-r2` (r1 → r2, §19) | DELEGATED_APPROVED | §§8.2, 19.7 |
| CP-100 | CT20.F / [#17](https://github.com/chi-thanh-nguyen/codemastery/issues/17) | `CT20-F-2026-11-15-r2` (r1 → r2, §19) | DELEGATED_APPROVED | §§11, 19.7 |

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

- **Dataset accounts.** `final-v1` **reuses** the existing `interim-v1` rows
  `user/instructor` (`1eacfb14-…`, `instructor@example.test`) and `user/learner`
  (`6ae6a0b2-…`, `learner@example.test`) unchanged, because V001's unique email and the
  SD01 conflict rule forbid a second row with the same email. It adds only new UUIDv5
  accounts: `user/admin` → `admin@example.test`, “Administrator Demo”, `admin`, and the
  three profile learners of §4.2. All `active`, all in existing `01-users.sql` (SD01
  extension), loaded with the same insert-only, no-overwrite, attested-target rules
  as `interim-v1`. *(Corrected 15 November 2026, F8-01.)*
- **Passwords.** Never committed. A distinct password per account is chosen at load
  time; its BCrypt `$2b$` cost-14 hash is produced by the documented Step 3 procedure
  (15 characters to 72 UTF-8 bytes) and passed through environment variables, never
  argv, files, logs or `VITE_*` inputs. The seed rejects a missing or malformed hash.
- **Deployed trial accounts.** On the deployment target the operator loads the same
  accounts only after the Leader authorizes demonstration seeding there (this record
  does not), generates fresh high-entropy passwords on a trusted machine and hands
  them to the lecturer only through a Leader-controlled secure location. Credentials
  never enter the repository, Issues, PRs, CI logs, reports or screenshots.
- **Administrator provisioning.** The first Administrator exists only through
  seeding; there is no admin self-registration. Further Administrators are created only
  by an active Administrator through ADR012 CT12.user (audited). CT34's direct role
  promotion stays limited to the disposable E2E database. *(Corrected, F8-04.)*

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

**Course.** `course/python` “Python Fundamentals”, published, owned by the reused
`interim-v1` `user/instructor`, in existing `02-course-python-fundamentals.sql` alongside the
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
course must not be loaded with invented values. *(Resolved by r2, §16.1: `weak 1/2`,
`mastered 4/5`, `minimumQuestionsPerSkill 6`, still supplied as load-time inputs.)*

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

*r2 (§19.6) adds sixty per-difficulty practice Quizzes; the bank and the rules above are unchanged.*

Readiness: with six questions per skill, the Course is adaptive-ready for any
approved `minimumQuestionsPerSkill` ≤ 6.

## 5. CT36.F-scenarios

**Baseline.** The fixed linear path of the same Course: L01–L20 in canonical order,
no test-out and no remedial step (path length 20).

**Policy independence.** Scripts use only all-correct (rate 1) and all-incorrect
(rate 0) answers for a skill. Because every valid policy has `0 < weak < mastered ≤ 1`
(ADR005 §6.2), rate 1 is always `Mastered` and rate 0 always `Weak`, so the expected
states and routes below hold for any valid thresholds, provided
`minimumQuestionsPerSkill ≤ 6` so that the Course is ready (ADR005 §7.2 item 4).
Unassessed skills stay Unknown. *(Qualifier added, F8-05.)*

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

**Discriminating variants G′01–G′24 and P2-R** *(added, F8-07)*. In G01–G24 and in
P2/P3 the remedial target is also the first incomplete Lesson of Λ′, so a plain
`continue` would name the same Lesson and these cases alone cannot tell remedial
routing from linear progression. Each G′ case repeats its G case after L(p) was
completed manually: expected `remedial` to the completed review Lesson L(p), while
`continue` would name L(s) (re-derived for all 24). P2-R repeats P2 after L08 was
completed manually: expected `remedial` L08 (review) while `continue` would name L09.

**Mastery-update cases M01–M11** (ADR005 §13.4 handoff): M01 all-correct → Mastered;
M02 all-incorrect → Weak; M03 one-question attempt correct → Mastered; M04
one-question attempt incorrect → Weak; M05 alternation Mastered → Weak → Mastered
follows the latest attempt; M06 an older 6/6 result replaced by a newer 0/1 → Weak;
M07 a skill not assessed by an attempt keeps its state; M08 the placement quiz
initializes only S01–S10; M09 (policy-dependent) the smallest k with
`weak ≤ k/6 < mastered` on a six-question attempt gives Learning, reported N/A when
no such k exists; M10 adding questions to the bank without a new attempt changes no
state; M11 (ADR005 §13.4 equal-time ordering) two attempts with equal `submitted_at`
under a fixed clock are ordered by (`started_at`, `quiz_id`, `attempt_number`) and the
later tuple decides the state. *(M11 added, F8-06.)*

**Oracle rule.** Expected outcomes are derived by hand and by an independent
re-implementation of ADR011 §7 from this dataset, reviewed by the Adaptive owner, who must not be the oracle's author (CT36.I-results
rule; another reviewer otherwise), **before** the system is run, and stored in the existing scenario files
`backend/src/test/resources/adaptive-scenarios/p1-strong-foundation.json`,
`p2-loops-gap.json` and `p3-complete-beginner.json` (with G and M cases in the
existing evaluation test). Outputs never redefine an expectation. The ≥80% routing
target (§6) is an evaluation target, never a mastery threshold.

## 6. CT36.F-measures

All measures run against an exact commit on a disposable PostgreSQL instance with
`final-v1` and the recorded policy values.

| Measure | Procedure | Reporting |
|---|---|---|
| Path divergence | Per profile after its script, against the baseline (architecture §6: Lessons skipped or inserted): skipped = number of `skip` rows; inserted = number of `remedial` recommendations whose target differs from the first incomplete Lesson of Λ′ (the step a linear continuation would take); divergence = skipped + inserted. Expected P1 10 (10 + 0), P2 7 (7 + 0), P3 0 (0 + 0); P2-R adds one inserted review. *(Redefined, F8-07.)* | Table per profile with rows and reasons; recommendation rows reported separately |
| Routing correctness | G01–G24 and G′01–G′24 at rule level (`AdaptiveDecisionEngine.decide` with the scenario's states) **and** at integrated level for P2, P3, P2-R, G10/G14/G24 and G′10/G′14/G′24 (edges S10 → S08, S12 → S11, S20 → S19) by real quiz submissions. Correct = current recommendation is `remedial` to the expected Lesson; the G′ and P2-R results are reported separately because only they discriminate remedial routing from linear progression. | Percentage per level with numerator/denominator; target ≥ 80% (architecture), reported, not assumed |
| Mastery-update correctness | M01–M11 by real submissions through the quiz-to-mastery unit (ADR011 B6-V cases may be reused). | Passed / applicable cases; N/A cases listed |
| Learning gain | Simulated only: after the placement, P2 takes `quiz/practice/S10` with a scripted all-incorrect set (pre-score), completes L08 and L10, then takes the same quiz again with a scripted all-correct set (post-score); gain = post − pre. Because both answer sets are scripted, the result only demonstrates that the measurement pipeline works; no baseline comparison is made (it would be circular) and it is **not** evidence of learning. *(Redefined, F8-08.)* | Pre, post and difference with the explicit limitation |

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
E2E). Each **new** case states its story in its title; the adopted tests keep their
titles, and only the L-A1, L-A4 and L-A5 count assertions change as §7.2 states (L-A5 added by r2, §18 R06). L-N3 also downloads the submitted file through the Instructor's REST operation and compares its bytes with the generated PDF, so it proves a real storage round trip (§7.3). L-N3/L-N4 become start-ready only
after ADR012 is integrated.

### 7.2 CT19.fixtures

Reuse CT34.fixtures r2 unchanged except for the additive storage component of §7.3 (r2): one disposable stack per run, the catalogue fixture
for L-A1, per-test synthetic accounts through the existing `auth-fixture.ts` helpers,
and the run's synthetic Instructor (`E2E_USERNAME`/`E2E_PASSWORD`). Course content
for L-N1–L-N4 is created per test through the approved Instructor REST operations
(CT05 skills, CT21, CT23, CT10, and CT30/CT11 reads for L-N4) as **isolated records**:
each journey Course title starts with `~E2E Journey ` plus the test ID, so in the
catalogue's title order (`COLLATE "C"`) every journey Course sorts after every
catalogue-fixture title. Journey Courses must be published for enrolment and therefore
appear in the one shared public catalogue of the run, so the adopted L-A1 and L-A4
assertions become fixture-relative: page 1 shows exactly the first twenty fixture
Courses, the hidden fixture Course never appears, the total is at least the fixture's
21 and page 2 starts with the remaining fixture Course. No shared fixture row is
mutated (ADR008 §4).

**Catalogue-dependent assertions (r2, §18 R06).** Every adopted assertion that
depends on the shared public catalogue is fixture-relative, so a correct application
passes in any execution order and with any number of concurrently published journey
Courses:

| Test | Exact assertions kept | Assertions that become fixture-relative |
|---|---|---|
| L-A1 catalogue | page 1 lists exactly 20 items, all fixture Courses, in fixture title order; the hidden fixture Course never appears; `foundations` search returns exactly the primary fixture Course (`1 course`); `Prerequisites described` lists exactly the primary fixture Course; page 2's first item is the 100-character fixture title | the total in “N courses” and “Showing 1–20 of N” satisfies N ≥ 21; page 2 shows “Showing 21–… of N” with the same N and at least one item |
| L-A4 membership | own memberships of the test's fresh account | page-1 fixture assumptions as L-A1 |
| L-A5 REST smoke | far page `page=107374183&size=20`: `content` is `[]`, `page` is `107374183`, `size` is `20`; hidden fixture 404; validation statuses | `totalElements ≥ 21` and `totalPages = ceil(totalElements / 20)` from the **same** response (one snapshot); never a fixed 21/2 |

These hold only if journey Courses cannot match the fixture's search and filter
cases, so every Course created by CT19 or CT35 **must** have a title starting with
`~E2E Journey `, a description and title that do not contain `foundations` in any
letter case, and **no** prerequisite description (null). Adaptive-flow and later
suites follow the same rule. No assertion may depend on the exact number or order of
journey Courses. *(Corrected, F8-02: the earlier claim that parallel workers share
no mutable data was false.)* The PDF is generated in the test (bytes starting with `%PDF-`);
deadlines are explicit case values relative to the test clock. No shared demo seed,
direct SQL (other than CT34's existing catalogue load and role promotion), new
fixture file or cleanup step is used; the disposable database is discarded with the
stack.

### 7.3 CT19 storage fixture (r2, §18 E01)

ADR008 §4 states that storage-backed cases need CT09's real isolated configuration and
that CT34's dummy storage values prove no MinIO integration; the current
`local-stack.ts` passes only generated `STORAGE_ACCESS_KEY`/`STORAGE_SECRET_KEY` and
starts no object store, so L-N3 had no storage. This bounded supplement to
CT34.fixtures r2 (ADR008 §§4, 10) adds one per-run object store; it changes none of
CT34's runner, readiness, labelling, inventory, recovery or ownership rules and is
implemented in the existing `e2e/fixtures/local-stack.ts` (FE25 with the FE24 owner's
review; no new file, script, dependency or top-level structure):

| Aspect | Rule |
|---|---|
| Images | Exactly the digests pinned in `infrastructure/docker-compose.yml` for `minio` and `minio-init` (the same approved local-development builds; no tag-only reference, no other image). Test use is not a release admission |
| Identity and isolation | Containers `<runId>-minio` and `<runId>-minio-init` on the run's private network with the run labels, so normal teardown, `stack:down`, the r2 inventory and recovery own and remove them exactly like the run's PostgreSQL. No host port is published; only the backend reaches `http://minio:9000` over that network, so concurrent runs cannot collide |
| Credentials | Per run: a generated MinIO root user and password and the existing generated application `STORAGE_ACCESS_KEY`/`STORAGE_SECRET_KEY`, which must differ from the root user (the initializer rejects equality). Passed only through the environment or stdin, never argv or files; added to the redaction list of backend log capture |
| Bucket and region | Explicit E2E case values `STORAGE_BUCKET=codemastery-e2e`, `STORAGE_REGION=us-east-1`, `STORAGE_PATH_STYLE_ACCESS=true`; not production values |
| Provisioning | The `minio-init` container runs the Compose `minio-init` command unchanged, obtained from `infrastructure/docker-compose.yml` at run time (for example through `docker compose config` with the generated inputs), never a divergent copy: identity-separation and input checks, bucket creation and the bucket-scoped Get/Put/Delete/List/Location policy for the application identity (CT09.config). The backend never uses root credentials |
| Readiness | The initializer's own `mc ready` wait (its existing 120 s limit) establishes MinIO readiness; setup waits for the initializer to exit 0, bounded by CT34's existing 180 s backend limit, and a non-zero exit or timeout aborts setup with redacted evidence (ADR008 §4). The backend starts only after that, and its readiness rule is unchanged |
| Backend inputs | The existing CT09.config names: `STORAGE_ENDPOINT=http://minio:9000`, the bucket, region, path-style and application keys above, and an explicit `MAX_UPLOAD_SIZE=1MB` case value (the in-test PDF is far smaller); no `VITE_*` input |
| Cleanup | `docker rm -f -v` of both containers removes MinIO's anonymous data volume with the run, on success, failure or interruption, through the existing teardown and recovery paths; leftovers are reported, never reused |
| Case use | Only L-N3 uploads and downloads (learner `PUT` multipart PDF, Instructor `GET` file, byte comparison); other cases ignore storage. External mode (`E2E_BASE_URL`) skips L-N3 unless that environment is an authorized disposable one with its own storage record |

Verification (QA08, specified, NOT_RUN): two concurrent runs each reach their own
object store and bucket; a stopped MinIO or failed initializer aborts setup before
tests; teardown and interrupted-run recovery leave no MinIO container or volume of
either run; a backend log tail contains no generated storage secret.

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
| A-4 practice difficulty (r2, §19.7) | Learners A and B open E2; Learner B opens E5; Learner D answers one K1 placement question correctly and one incorrectly, then opens E1 | A: Hard practice for "While Loops" (Mastered); B: Easy practice for "While Loops" (Weak) and Easy for "Lists" on E5 (not yet assessed); D: K1 Learning, target Medium, no Medium Quiz, so Easy with `fallback = true`. Each with §19.4's reason, no access blocked (architecture §§4.7, 6). |

At least two contrasting profiles (A and B) are selected, satisfying the CT35
criterion. Quiz-driven mastery runs through the real submission unit (CP-057).

### 8.2 CT35.fixtures

Per run and per test, inside CT34's disposable stack: the run's Instructor builds the
Course, skills, edges, mappings, questions, placement quiz and adaptive settings
through approved REST operations, then enables adaptive mode; learners register
through the API. Each of K1–K5 has exactly two bank questions (the placement uses those
of K1–K4), so readiness holds for a test policy with `minimumQuestionsPerSkill ≤ 2`; the
thresholds are an explicitly named test value and any valid thresholds give the same
results. Course titles follow §7.2's `~E2E Journey ` rule. *(Corrected, F8-03.)* SD03 is not loaded in E2E, and no profile data are shared between
tests or runs.

*r2 (§19.7):* of each skill's two bank questions, one is `easy` and one is `hard`, and
the Course also has two published `practice` Quizzes per skill, `E2E practice Kn easy`
(the easy question) and `E2E practice Kn hard` (the hard question), so there is no
Medium Quiz and A-4 exercises the fallback. The test policy is fixed to the explicit
test values `weak 1/2`, `mastered 4/5`, `minimumQuestionsPerSkill 2`; A-1–A-3 stay
policy-independent, and A-4's Learner D depends on `weak ≤ 1/2 < mastered`, which these
values satisfy.

## 9. Responsibilities unlocked

After ADR012 and this record are integrated and reviewed: SD01 (admin account
extension), SD03, SD04, QA05 (evaluation execution and report), FE25 (L-N3/L-N4 also
consume ADR012),
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
| **Adaptive E2E (U34)** | CT35 A-1 and A-4 (r2, §19.7) pass on `chromium-desktop` | **Yes** (advanced component in the main flow) |
| Other selected E2E cases and projects | Remaining CT19/CT35 cases on all five CT34 projects | Attempted and reported with failures, skips and launch blocks; not release-blocking |
| Adaptive evaluation | CT36.F-measures report (QA05) | Yes: the report must exist; the ≥80% target is reported, not a gate on release |
| Deployment | CT17.F-runtime/recovery r2 qualification, smoke checks of the deployed URL and one sign-in per trial role; the Leader's decision on ADR011 §5 operational risks before integrated quiz/adaptive exposure, and on the other exposure risks of §16.5 | Yes |
| Sample data | Disposable load and login of SD01–SD04 (QA13) | Yes |
| CI | CT14 F2 jobs and CT34.ci SH07 on the release commit | Yes |

Release evidence is collected on one exact commit and recorded with commands, dates,
browsers and results. Both E2E suites are therefore required at release, but only for
the named cases and projects; neither empty suite is required by default. CT20.I is
unchanged.

## 12. Complete contract reconciliation — 15 November 2026

**All 100 roadmap checkpoints are accounted for.** No checkpoint was renamed,
rebucketed or double-counted; prerequisite slices are counted separately.
*This section is the state at first preparation; §16.6 gives the state after the
second instruction (CP-095 completed by r2; several residual rows resolved).*

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

**Residual obligations, classified** at first preparation (none was then eligible for
new delegated approval; §16 resolves the first, second, third and fifth rows, narrows the
fourth to a ceiling, and keeps the rest; §18.4 withdraws §16's resolution of the third row,
which is again PENDING_LEADER_DECISION):

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
load evidence; loading the demonstration Course with the §16.1 values; FE25/FE26 implementation and FE24/SH07 (#240/#241) review;
QA05/QA08 execution; deployment admission, demonstration seeding authorization and
credential handover on the target; implementation and tests of §19's practice recommendation (AD11, AS07, FE09/LessonPage, SD04, FE26); FE25's implementation of the §7.3 storage fixture together with FE24 (#240); every item of §12. No result, deployment,
credential handling or risk acceptance is implied.

## 14. Leader decisions requested

*§§14.1–14.3 are resolved by §16 (second instruction); the text below is retained as
the original request.*

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

## 16. Continuation: residual resolution — 15 November 2026 (second instruction)

**Authority.** The Project Leader's later explicit instruction of 15 November 2026,
**“CodeMastery — Final Contract Resolution & Comprehensive Independent Re-Audit of PR
#244 and #245”**, delegates bounded technical decisions that §14 had left for Leader
selection and authorizes corrections in this PR. It does not delegate merge,
deployment, credential or live-privilege changes, human review, residual-risk
acceptance or silent amendment of effective Leader-reserved approvals. Decisions
below are **DELEGATED_APPROVED** under it unless stated otherwise; none is personal
Leader approval. Shared decisions consumed here (CT17-P6 plan, CT14 placement, LD-02,
delivery semantics) are recorded in [ADR012 §19](012-batch-07-contract-approvals.md#19-continuation-decisions--15-november-2026-second-instruction).

### 16.1 CT15-F-ADAPTIVE-2026-11-15-r2 — demonstration policy (resolves §14.1)

`course/python` uses `weak = 1/2`, `mastered = 4/5`, `minimumQuestionsPerSkill = 6`.
These are **demonstration-course data** (Instructor-owned Course configuration under
ADR005 §6.2, which expressly left demo values to CT15.F-adaptive), not a system default
or production threshold; any other Course still sets its own policy. Verified:

- **Schema validity (ADR005 §6.2):** integers in range, `0 < 1/2 < 4/5 ≤ 1`; canonical
  pairs are already reduced.
- **Readiness (ADR005 §7.2 item 4, §3.3):** `n` counts distinct bank Questions tagged to
  each Course skill; SD04 supplies exactly six per skill, so every one of the twenty
  skills reaches coverage 6 and the Course is ready. `n` governs bank readiness only, so
  the two-question placement quiz is compatible (ADR005 §6.2: not a sample size). Trade-off
  recorded: deleting any unused question of a skill makes readiness false and suspends
  routing effects (fail-closed, ADR005 §7.2), which is acceptable for a fixed demo
  dataset and makes incomplete loading visible.
- **Classification:** on a six-question practice quiz 0–2 correct is Weak, 3–4
  Learning, 5–6 Mastered; on the two placement questions of a skill 0, 1 and 2 correct
  give Weak, Learning and Mastered. M09's smallest Learning k is 3.
- **Scenarios:** §5's scripts use rates 0 and 1 only, so their outcomes are unchanged.

The values are chosen from that partition, not from the separate ≥80% routing target.
r2 replaces only r1's pending policy clause; every other r1 clause is unchanged. CP-095
is now **DELEGATED_APPROVED**. The seed still takes the three values as load-time inputs
(§4.2), now with these approved values.

### 16.2 Learning-gain evaluation — no real-student pilot (resolves §14.2)

Requirement §5.3 requires modelling skills and prerequisites, adapting difficulty **or**
the next lesson, a Mastery Map and a flow change across at least two profiles; §5 asks
for supporting data or test scenarios, evaluation criteria and a live demonstration.
None requires real learners. Decision: **no real-student pilot for Final**; learning
gain is reported only as the simulated procedure of §6 with its stated limitation, and
the Final report must not present it as evidence of learning. No personal student data
is collected. A later pilot would need consent, anonymization and a Leader data
decision.

### 16.3 Adaptive practice capture (ADR011 §17) — technical disposition

*Withdrawn on 15 November 2026 by §18.4 (E02): the DEFERRED disposition below decided a
choice that ADR011 §17 reserves to the Leader and left the approved architecture's
practice-difficulty step (§§4.7, 6) unconsumed. The matter is again
**PENDING_LEADER_DECISION**; the text is kept as history.*

Re-assessed: next-lesson adaptation (test-out, remedial, continue) already satisfies
§5.3; the amendment would change two effective approvals (CT04-START, CT08-EVIDENCE);
and because CT06.production weighs every captured question equally under the
latest-attempt policy, difficulty-shaped evidence would bias classification (a Weak
learner who answers two easy questions correctly becomes Mastered). The compatible
alternative of fixed per-difficulty practice quizzes has the same bias. Disposition:
**the amendment is not adopted for Final (DEFERRED)**; CT04-START and CT08-EVIDENCE stay
unchanged; AS07 keeps only the port and the pure selection rule (ADR011 §8);
`QuestionSelectionService` stays without a production caller; Course is unaffected.
Declining changes no effective contract. The Leader keeps the reserved authority to
adopt the amendment later, which would also need a decision on that bias. The Final
report states that practice quizzes are not difficulty-shaped and that the
architecture's “practice quiz at selected difficulty” step is specified (CT07.difficulty)
but not consumed in Final.

### 16.4 LD-09, LD-11 and review routes

- **LD-09 (technical part):** the live `main-quality-gates` ruleset requires one approving
  review, CODEOWNER review, approval after the last push, resolved threads and the
  required checks, with **no bypass actor**; `/docs/decisions/` has only the Leader as
  CODEOWNER and these PRs are pushed from the Leader's account, so no reviewer can
  satisfy the CODEOWNER rule. Evidence required before any merge of #244/#245: approving
  reviews from the affected module owners given after the last push, green required
  checks and resolved threads. How the CODEOWNER rule is then met (a CODEOWNERS change,
  a ruleset bypass entry or another route) is a repository-rule decision that this
  delegation excludes: **PENDING_LEADER_APPROVAL**.
- **Integration of ADR005–ADR011 without approving reviews:** #228, #229, #230, #231,
  #233, #235 and #236 were merged by the Leader with no approving review while
  `main-quality-gates` requires one. Their content is integrated and their delegated
  approvals stand, but no record states that the Leader accepts that integration route
  (ADR004 covered only the earlier records). Recording that acceptance is the Leader's
  own act: **PENDING_LEADER_APPROVAL**, together with LD-09.
- **LD-11:** a human joint review by the Course, Assessment and Adaptive owners of
  ADR011 §§3–5 cannot be performed by this record. Its checklist: lock order (ADR011 §4),
  actorless allowlists (§3), rollback visibility and recovery route (§5), B6-V01–V12
  and the two-member `QuizSubmittedEvent` subscriber set (ADR012 §9.3). Status: open
  review gate.

### 16.5 Security and operational risk register

| Item | Control | Status |
|---|---|---|
| ADR010 hostile submission content | PDF/ZIP signature validation, attachment disposition, `nosniff`, owner-only download (ADR010 §§5, 11) | MITIGATION_SPECIFIED_NOT_IMPLEMENTED; no content scanning, so delivering hostile files to graders stays PENDING_PERSONAL_RISK_ACCEPTANCE before exposure |
| ADR011 §5 blocked submission on deterministic Adaptive failure; answers graded from last committed save | Disable-adaptive recovery route, save-before-submit client rule | MITIGATION_SPECIFIED_NOT_IMPLEMENTED; residual PENDING_PERSONAL_RISK_ACCEPTANCE |
| ADR012 notification loss after commit | Best-effort at-most-once, derived IDs (ADR012 §§8, 19.5) | Design property, disclosed; no acceptance needed because nothing claims delivery guarantees |
| AU07 reset does not revoke issued tokens (≤ PT15M) | Per-request account check; short lifetime | PENDING_PERSONAL_RISK_ACCEPTANCE. A verifiable mitigation (a password-hash fingerprint claim checked per request) would amend the Leader-approved CT01-2026-10-21-r1 token contract and is therefore not selected here |
| ADR003 glibc/musl collation of pre-existing data | None for existing data | PENDING_PERSONAL_RISK_ACCEPTANCE (not applicable to a fresh database) |
| DP01 F1, F4, F5, F6, F10, F11, image admissions, tmpfs/memory budgets | Qualified controls recorded in the deployment record | PENDING_VERIFICATION / PENDING_PERSONAL_RISK_ACCEPTANCE (licensing, signing trust, CVE acceptance and admission are Leader decisions) |
| CT17 runtime/recovery admission | ADR009 r2 qualification protocol | PENDING_VERIFICATION (actual VM execution and acceptance) |
| CT17-P6 runtime grants | ADR012 §19.2 ceiling | MITIGATION_SPECIFIED_NOT_IMPLEMENTED (per-consumer matrix revisions) |

No risk is accepted and no exposure or deployment is approved by this record.

### 16.6 Other residual rows of §12

- CT14 Interaction/Admin and storage integration placement: **resolved** by
  `CT14-INTEGRATION-PLACEMENT-2026-11-15-r1` (ADR012 §19.3).
- CT17-P6 runtime grants: `CT17-P6-PLAN-2026-11-15-r1` ceiling, revised to r2 by ADR012 §21 (ADR012 §19.2); matrix
  revisions per implemented consumer; real provisioning Leader-reserved.
- LD-02: **resolved** as option (a) (ADR012 §19.4).
- §12's labels `ALREADY_EFFECTIVELY_APPROVED`, `ELIGIBLE_FOR_NEW_DELEGATED_APPROVAL`,
  `IMPLEMENTATION_OR_VERIFICATION_GATE_ONLY` and similar are analysis labels describing
  evidence, not approval statuses (decision index, “Analysis labels”).
- Stale history noted, not rewritten: ADR008 §§1–2/8 still call CP-033/034 pending
  (resolved by ADR009 r2); ADR009's header says “awaits PR #233” (merged); ADR010 §2
  counts eleven relationships where the register has fourteen (the three extra are
  VERIFY-C1 edges resolved by LD-01). None changes an approval.

### 16.7 Updated reconciliation

After §16.1, all 100 roadmap checkpoints carry a technical approval record: CP-001–063
are integrated on `main` (ADR004–ADR011), CP-064–090 are in open PR #244 and CP-091–100
in open PR #245. This is technical approval only: 37 of the 100 are not integrated,
the practice-difficulty consumption decision reopened by §18.4 was decided by the Leader and specified in §19 (outside the 100),
none of ADR005–ADR013 has an approving GitHub review, and no checkpoint implies
implementation, test evidence, deployment or risk acceptance.

## 17. Adversarial re-audit corrections — 15 November 2026

Under §16, separately instructed review passes (reconciled against the sources; not a
GitHub or human review) produced these findings, corrected in place; revision IDs are
unchanged except CP-095's completion by r2 (§16.1).

| ID | Severity | Finding | Correction |
|---|---|---|---|
| F8-01 | HIGH | New `final-v1` IDs for `instructor@`/`learner@example.test` collide with the `interim-v1` rows (unique email, SD01 conflict rule), so SD01 and QA13 could never load | Reuse the interim rows; new IDs only for admin and P1–P3 (§§3, 4.2; sample-data README) |
| F8-02 | HIGH | Per-test published journey Courses enter the shared catalogue and break L-A1's exact counts and possibly L-A4's page-1 assumption; “never share mutable data” was false | `~E2E Journey ` title rule and fixture-relative L-A1/L-A4 assertions; isolated records (§§7.1–7.2, 8.2) |
| F8-03 | MEDIUM | The E2E Course gave K5 no questions, so adaptive enabling fails readiness; “any valid policy” was false | Two bank questions per K1–K5, `n ≤ 2` (§8.2) |
| F8-04 | MEDIUM | “No admin promotion endpoint” contradicted ADR012 CT12.user | First Administrator by seed, others by CT12.user (§3) |
| F8-05 | MEDIUM | “Any approved policy” ignored readiness | Qualifier `n ≤ 6` (§5) |
| F8-06 | MEDIUM | ADR005 §13.4's equal-time ordering case was missing | M11 (§5) |
| F8-07 | MEDIUM | Remedial expectations coincided with linear continuation; path divergence counted non-insertions | G′ variants, P2-R; divergence redefined to architecture §6 (P1 10, P2 7, P3 0) (§§5–6) |
| F8-08 | LOW | Learning gain retook an untaken quiz and compared against a circular scripted baseline | Pre/post on the same quiz, no baseline (§6) |
| F8-09 | LOW | Fixture operations omitted CT05 and CT30; §9 implied work could start “now” | Operations listed; start only after integration and review (§§7.2, 9) |
| F8-10 | LOW | Oracle review omitted the reviewer ≠ author rule; adopted tests were said to state stories in titles | Rule added; claim limited to new cases (§§5, 7.1) |
| F8-11 | LOW | CT20.F deployment row did not name the ADR011 §5 risk decision | Named (§11) |
| F8-12 | LOW | §12 used analysis labels without saying so; stale history in ADR008–ADR010 | §16.6 |

Verified as correct and unchanged: graph counts and acyclicity, G numbering, all P/G/A
derivations (independently re-implemented), dataset/schema compatibility, readiness and
partition arithmetic, E2E REST operation availability, minimal `%PDF-` acceptance, the
adopted test count, CT20.F against requirements §§5–6, the SD01 hashing procedure and
§12's batch counts.

## 18. Independent review remediation — 15 November 2026 (third instruction)

### 18.1 Authority and method

The Project Leader's explicit instruction of 15 November 2026, **“CodeMastery —
Independent Review Findings Verification & Direct Remediation on Existing PR
#244/#245”**, provided a separate read-only review of this PR at
`2806b926dec5cf49104191211bb397eb90534025` (R06, E01, E02 here; R01–R05 in
[ADR012 §21](012-batch-07-contract-approvals.md#21-independent-review-remediation--15-november-2026-third-instruction))
and authorized verification, bounded technical decisions and corrections in this PR.
It excludes overriding Leader-reserved decisions, weakening the approved architecture,
accepting residual risk and bypassing review. Findings were re-derived from the
sources (the merged `learner-flow.spec.ts`, `auth-fixture.ts`, `catalogue-fixture.sql`
and `local-stack.ts`; ADR002 CT21's catalogue search; ADR008 §§4, 10; ADR010 §6;
the Compose MinIO services; requirements §5.3; architecture §§4.7, 6, 10; ADR005 §6;
ADR007 §§5, 8; ADR011 §§8, 17). This is delegated technical approval, not personal
Leader approval or a GitHub, CODEOWNER or human review.

### 18.2 R06 — L-A5 global catalogue totals

**Valid.** L-A5 asserts the far page equals
`{content: [], page: 107374183, size: 20, totalElements: 21, totalPages: 2}`
(`fixture.publishedCount`). Any journey Course published by a parallel CT19 or CT35
test makes `totalElements` at least 22, so a correct application fails depending on
order. Root cause: F8-02 corrected L-A1/L-A4 but not L-A5. A further same-class
dependency was found: L-A1's `foundations` search and `Prerequisites described`
filter assert exactly one result, which a journey Course whose title or description
contains `foundations`, or which has prerequisite text, would break. Correction:
§7.2's assertion table (exact page, size, empty content, hidden 404, fixture order and
search/filter results kept; totals fixture-relative and internally consistent) and the
journey-Course isolation rule (title prefix, no `foundations`, null prerequisite
text). Revisions `CT19-CASES-2026-11-15-r2` and `CT19-FIXTURES-2026-11-15-r2`. The
adopted test file is not edited here; FE25 changes exactly these assertions.

### 18.3 E01 — storage-backed E2E fixture

**Gap confirmed.** No approved contract defined a MinIO lifecycle for Playwright:
ADR008 §4 requires CT09's real isolated configuration for storage-backed cases and
says CT34's dummy values prove nothing; `local-stack.ts` starts no object store;
ADR012 §19.3's `FileStorageIntegrationTest` is a JUnit container that gives the
browser stack nothing. Correction: §7.3, a bounded supplement to CT34.fixtures r2 that
fixes image identity (the Compose-pinned digests), per-run container identity and
network isolation, credentials, bucket, provisioning through the unchanged Compose
initializer, readiness bounds reused from existing limits, backend inputs, cleanup,
concurrent-run isolation and L-N3's upload/download round trip, plus QA08 checks.
Ownership: FE25 implements it in the existing `local-stack.ts` with the FE24 owner's
review, after or together with open PR #240's lifecycle implementation. **Resolved
as a contract amendment (part of `CT19-FIXTURES-2026-11-15-r2`); nothing is
implemented or run.**

### 18.4 E02 — practice difficulty and the approved architecture

*Resolved on 15 November 2026 by the Leader's explicit decision for option (a) and the
contracts of §19; the analysis below is kept as the basis of that decision.*

**Finding substantiated.** Requirements §5.3 asks to adjust “difficulty **or** the next
lesson”, and next-lesson adaptation satisfies it. The approved architecture, however,
selects more: §4.7 lists choosing practice-quiz difficulty from current mastery as
adaptive scope, §6's integration flow routes `Learning` to the canonical next lesson
“with practice quiz at selected difficulty”, and §10 does not place it out of scope.
The requirement's OR clause does not remove an architecture selection (root
`CLAUDE.md`: when the architecture has selected one option, follow it). §16.3 therefore
did more than defer an amendment: it set a Final scope that omits an architecture-listed
function, and it chose among ADR011 §17's options, which that record reserves to the
Leader (“authorize, revise or decline that amendment”). That exceeded this record's
delegation. **§16.3's disposition is withdrawn.**

**Is a compatible consumption possible within effective contracts?** No:

1. Shaping a practice attempt at start (ADR011 §17 option A) changes
   `CT04-START-2026-11-09-r1` (capture every membership Question) and
   `CT08-EVIDENCE-2026-11-09-r1` (verify complete membership) inside the atomic
   submission unit — the Leader-reserved amendment.
2. Guidance-level selection among fixed per-difficulty practice Quizzes (ADR011 §17's
   alternative) keeps capture and evidence unchanged, but needs a learner-facing
   target-difficulty value and a per-Quiz difficulty profile, which no effective
   contract defines (the port has no approved caller, ADR011 §8; Learner Quiz contracts
   carry no difficulty), new demonstration content, and the same choice among ADR011
   §17's options.
3. Every variant makes difficulty-shaped attempts eligible evidence under ADR005 §6's
   equal-weight latest-attempt policy: a Weak learner who answers two EASY questions
   correctly becomes Mastered and may be tested out of a Lesson. Avoiding that would
   change CT06/CT08 evidence semantics. Accepting or mitigating this bias is a Leader
   decision either way.

**Best technical solution (recommendation, not approved):** option 2, because it
leaves the submission unit, CT04-START, CT08-EVIDENCE and mastery semantics untouched
and keeps selection advisory and explainable:
- **Assessment:** an additive Learner read giving, for each published `practice` Quiz
  of an admitted Course, its question difficulties per skill (derived from existing
  `questions.difficulty`; no schema field).
- **Adaptive:** the CT33 Learner guidance gains, per skill of the next Lesson, the
  CT07.difficulty target from the learner's own mastery, computed by Adaptive's
  existing `PracticeDifficultySelector` mapping without calling the Assessment port.
- **Frontend:** the UI marks the practice Quiz whose profile matches the target, with
  the reason (for example “Recommended practice: Easy, because "Loop Control" is
  Weak”), falling back by CT07.difficulty's order.
- **Content and tests:** SD04 adds per-difficulty practice Quizzes. B6/CT35 oracles
  cover the target per state and the fallback.
- **Bias:** a recorded Leader decision on difficulty-shaped evidence.

**Minimum Leader decision (`PENDING_LEADER_DECISION`):** choose one of —
(a) authorize option 2 as above, including the evidence-bias disposition;
(b) authorize ADR011 §17's amendment of CT04-START/CT08-EVIDENCE (option 1), with
the same bias disposition; or (c) approve, as the architecture owner, a recorded
Final scope deviation from architecture §§4.7/6 so that practice difficulty is
specified (CT07.difficulty) but not consumed in Final.

Until then: CT04-START, CT08-EVIDENCE and CT07.difficulty are unchanged; AS07 keeps
only the port and the pure rule; `QuestionSelectionService` has no production caller;
nothing is approved or deferred on the Leader's behalf. This decision is outside the
100 roadmap checkpoints, so their count and dispositions are unchanged.

### 18.5 Downstream effects of ADR012's corrections

ADR012 §21's r2 revisions were checked against this record. L-N3 still expects the
graded notification, because its learner is joined and its Assignment and Course are
published, which `CT27-SUBSCRIPTION-2026-11-15-r2` requires. L-N4 is unchanged. §3's
rule that further Administrators come only through CT12.user is unchanged by
`CT12-USER-2026-11-15-r2`. §16.6 now points to `CT17-P6-PLAN-2026-11-15-r2`.

### 18.6 Post-fix check

- **R06 replay:** with one or more journey Courses published concurrently, every
  L-A1/L-A4/L-A5 assertion in §7.2 still holds for a correct application. Page 1 is
  the first 20 fixture titles, because `~` (0x7E) sorts after every fixture title
  under `COLLATE "C"`. Totals are bounded below, and `totalPages` is derived from
  the same response. Search and filter results are unchanged by the isolation rule.
  An application that leaks the hidden Course or pages incorrectly still fails.
- **E01 replay:** L-N3 has a reachable, isolated, provisioned bucket; concurrent runs
  have separate networks, credentials and containers; teardown and recovery remove the
  storage with the run.
- **E02 replay:** no clause now states that Final omits practice difficulty, or that
  the matter was decided. ADR011 §17's Leader-reserved status is restored, and the
  architecture conflict is stated.
- **No new contradiction.** Specifically:
  - §7.3 reuses existing images, limits and the CT09 input names.
  - §7.2's isolation rule constrains only test data.
  - §16.7's count of 100 technically approved checkpoints is unchanged.

Checkpoints CP-091–CP-100 keep their dispositions; CP-096/CP-097 move to r2. Nothing
is a test result.

## 19. Leader decision E02 and practice-difficulty consumption — 15 November 2026

### 19.1 Authority and provenance

In **“CodeMastery — Final Leader Authorization for Remaining Technical Decisions”**
(15 November 2026, Asia/Ho_Chi_Minh), Project Leader @chi-thanh-nguyen explicitly:

- **decided E02 as option (a)** of §18.4: mastery-based recommendation among fixed,
  per-difficulty practice Quizzes. CT04-START, CT08-EVIDENCE and the atomic
  submission unit are preserved, CT07.difficulty's mapping and fallback are reused,
  and only bounded Assessment, Adaptive, Frontend and sample-data contracts are added
  so that the Final design satisfies architecture §§4.7 and 6.
  **LEADER_APPROVED (decision).**
- **accepted a pedagogical limitation for the academic Final.** The existing
  latest-attempt, equally weighted mastery model can classify a learner as Mastered
  after a small number of correct Easy answers. Mastery mathematics must not change,
  and the limitation must not be presented as empirically validated learning.
  **LEADER_ACCEPTED**, limited to that pedagogical limitation.
- **authorized Claude Code** to verify option (a) and to finalize the directly
  related technical contracts. Those contracts are **DELEGATED_APPROVED** under that
  authorization; they are not personal Leader approval.

The authorization covers E02 and its directly related decisions only. It accepts no
security, deployment, operational or release risk and replaces no human or CODEOWNER
review.

**Consequences for ADR011 §17.** Its option (b), the amendment of CT04-START and
CT08-EVIDENCE, is **not adopted, by this Leader decision**. Option (c), a scope
deviation, is not needed. ADR011 itself is not edited.

### 19.2 Independent compatibility and completeness check

| Check | Result |
|---|---|
| Quiz capture and evidence | Unchanged. The learner starts whichever Quiz they choose through CT04.start, which captures its full membership; CT08.evidence verifies full membership; the submission unit, `QuizSubmittedEvent` path and ADR011 lock order are untouched |
| Mastery mathematics | Unchanged (ADR005 §6, CT06.production): per-difficulty Quizzes are ordinary practice evidence |
| CT07.difficulty reuse | The state → difficulty mapping of `PracticeDifficultySelector` and its fallback orders (EASY→MEDIUM→HARD, MEDIUM→EASY→HARD, HARD→MEDIUM→EASY) are reused verbatim at Quiz level |
| Port allowlist | `PracticeDifficultyProvider` keeps **no production caller** (ADR011 §8): the recommendation runs in Adaptive and uses the selector's pure mapping, never the port. AS07.port stays published; question-level selection in `QuestionSelectionService` (AS07.select) has no consumer in Final |
| Module boundaries | Adaptive → Course (existing CT33 `readLearnerState`) and Adaptive → Assessment (one new allowlisted read, §19.3) are already-approved directions. No new module, file, table, column, migration, dependency or route |
| Architecture §4.7 | “Choose practice-quiz difficulty from current mastery”: §19.4 chooses the practice Quiz's difficulty from the learner's current state |
| Architecture §6 | “Learning → continue canonical next lesson, with practice quiz at selected difficulty”: the guidance for the continue (or remedial) target shows the selected practice Quiz (§19.5). Canonical order is unchanged and no access is blocked or reordered |
| Explainability and traceability | Every recommendation carries a fixed English reason naming the governing skill and its state. It is a deterministic function of persisted mastery rows, the published Quiz bank and the graph, recomputed on read. It is not persisted: V005 types are the three path decisions, and the practice attempt itself is persisted as an Attempt |
| Determinism | Total orders throughout: CT07.reasons name order, then Quiz title with Java `String.compareTo`, then lowercase UUID; no randomness, clock or attempt-history input |
| Requirements §5.3 | Both halves are now covered: next lesson (ADR011 §7) and difficulty (this section) |

No incompatibility was found. Directly related ambiguities are resolved in
§§19.3–19.7: candidate Quizzes, multi-skill Lessons, ties, non-adaptive Courses,
failure behavior, UI placement and E2E evidence.

### 19.3 CT23-PRACTICE-PROFILES-2026-11-15-r1 — Assessment read

**DELEGATED_APPROVED**, [CT23 #44](https://github.com/chi-thanh-nguyen/codemastery/issues/44).
Add to existing `modules/assessment/application/service/QuizService.java`:

```java
public List<PracticeQuizProfile> readPracticeQuizProfiles(UUID courseId, Set<UUID> skillIds);
public record PracticeQuizProfile(UUID quizId, String title, Set<DifficultyLevel> difficulties) {}
```

- **Rows.** Every **published** Quiz of kind `practice` in the Course with at least one
  membership Question whose every membership Question's skill is in `skillIds`.
- **Profile.** `difficulties` is the set of its Questions' V004 difficulty values.
  `DifficultyLevel` is the existing Assessment type that ADR011 §8 already exposes
  to Adaptive.
- **Shape and transaction.** Empty `skillIds` returns an empty list, and the result is
  immutable. It is one set-based query and joins the caller's REPEATABLE READ
  read-only transaction.
- **Excluded data.** No prompt, option, answer key, question ID, attempt or score.
- **Callers, exactly:** Adaptive `RecommendationService` (§19.4), after
  `readLearnerState` has admitted the current Learner in the same transaction.
  Allowlist tests prove that no other caller exists.
- **Scope.** CT23 composition, publication and Learner Quiz reads are unchanged.

### 19.4 CT07-PRACTICE-RECOMMENDATION-2026-11-15-r1 — rule and REST

**DELEGATED_APPROVED**, [CT07 #48](https://github.com/chi-thanh-nguyen/codemastery/issues/48).

**Bindings.** Existing `RecommendationController`, `RecommendationResponse` and
`RecommendationService`, plus a pure, side-effect-free mapping and fallback method on
the existing `PracticeDifficultySelector`. The port method, its admission checks and its
empty production-caller allowlist are unchanged.

**Operation.** `GET /api/courses/{courseId}/lessons/{lessonId}/practice-recommendation`
(OpenAPI `b8ReadPracticeRecommendation`, `B8PracticeRecommendation`). It is a
read-only REPEATABLE READ snapshot with `Cache-Control: no-store` and CT18.minimum
errors:

| Order | Check | Failure |
|---|---|---|
| 1 | Authenticated | 401 |
| 2 | UUIDs well formed | 400 |
| 3 | Role Learner | 403 |
| 4 | CT33 `LessonProgressService.readLearnerState(courseId)`: CT03 Learner Course access, ACTIVE/COMPLETED, exactly as `/adaptive-guidance` | CT03's outcomes |
| 5 | `lessonId` is one of that state's currently published Lessons | 404 (`B8Unavailable`) |
| 6 | Provider or integrity failure | 500 |

**Rule.** For Lesson L with mapped skills M(L) (Adaptive's mappings) and state(s)
the learner's stored state (absent → Unknown):

1. **Null result.** `recommendation = null` if the Course's stored `adaptiveEnabled`
   (`LearnerCourseState.adaptiveEnabled`, the same snapshot) is false or M(L) is empty. Readiness is not re-evaluated, as for §15 guidance in
   ADR011.
2. **Target per skill.** target(s) is CT07.difficulty's mapping: Unknown and Weak →
   EASY, Learning → MEDIUM, Mastered → HARD.
3. **Governing skill g.** The skill of M(L) with the easiest target (EASY < MEDIUM <
   HARD); ties are broken by CT07.reasons name order. The weakest skill decides
   because practice should not be harder than the learner's weakest skill in that
   Lesson.
4. **Candidates.** The profiles from `readPracticeQuizProfiles(courseId, M(L))` whose
   `difficulties` has exactly one element, which is the Quiz's level. Mixed-difficulty
   practice Quizzes are never recommended but stay available.
5. **Level.** Walk CT07.difficulty's fallback order for target(g) and choose the first
   level that has a candidate. If none exists, `recommendation = null`.
6. **Quiz.** At that level, order candidates by title (Java `String.compareTo`), then
   lowercase quiz UUID; the first is recommended. `fallback = (level ≠ target(g))`.

**Fixed English reason templates.** `{Level}` and `{Target}` are `Easy`, `Medium` or
`Hard`; `{state}` is `not yet assessed`, `Weak`, `Learning` or `Mastered`; the skill
name is inserted verbatim:
- `Recommended practice: {Level}, because your skill "{skill}" is {state}.`
- with fallback:
  `Recommended practice: {Level}, because your skill "{skill}" is {state} and no {Target} practice quiz is available for this lesson.`

**Guarantees.** The response is a function of the snapshot only. It writes nothing,
publishes no event, never blocks or reorders access, never hides another Quiz and
never starts an attempt. Learners other than the caller and Instructor views are out
of scope.

### 19.5 Frontend binding (part of CT07-PRACTICE-RECOMMENDATION r1)

All UI changes stay in `features/learning`, so there is no cross-feature import
(repository-structure CT16.final-state F1). The existing files involved are
`learningApi.ts`, `learningTypes.ts`, `useCourseLearning.ts`,
`CourseLearningPage.tsx` and `LessonPage.tsx`.

- **Course workspace (FE09 guidance).** For the Lesson that guidance points to,
  the client calls the operation and shows a “Recommended practice” entry. That
  Lesson is the visible current recommendation's target when one exists, otherwise
  CT22's `nextLessonId`. This is §6's “continue … with practice quiz at selected
  difficulty”.
- **Lesson page.** The same entry is shown for the open Lesson.
- **Entry content.** The Quiz title and level, the reason verbatim and a link to
  the existing CT16 quiz route. It also carries ADR011 §9's required statement:
  “Mastery states reflect the latest submitted quiz evidence for each skill.”
- **Null result.** Nothing is shown.
- **Client rules.** The client never selects a Quiz itself and never shows
  thresholds. Text is plain and in English, and the layout is responsive.

### 19.6 CT15-F-ASSESSMENT-2026-11-15-r2 — SD04 per-difficulty practice Quizzes

r1 is unchanged. In addition, `04-questions-quizzes-assignments.sql` adds, for every
skill Sk, three published `practice` Quizzes:

- `quiz/practice/Sk/easy`, `quiz/practice/Sk/medium` and `quiz/practice/Sk/hard`;
- each contains exactly the two existing questions of Sk at that difficulty;
- sixty Quizzes in total, reusing the bank (no new question).

Titles follow `{skill name} practice (Easy|Medium|Hard)`; time limits are SD04
content values. The twenty mixed `quiz/practice/Sk` Quizzes stay, unchanged, for
general practice and for §6's learning-gain procedure.

Readiness, P1–P3, G, G′, P2-R and M expectations are unchanged: their scripts use
only the placement and `quiz/practice/S10`.

Expected demonstration outputs, policy-independent unless noted (rule-level oracles
in §19.8):

| Learner | Lesson | Governing skill and state | Recommended practice |
|---|---|---|---|
| P1 | L05 | Mastered | Hard: `Input and Type Conversion practice (Hard)` |
| P1 | L11 | not yet assessed | Easy |
| P2 | L08 | Weak | Easy: `While Loops practice (Easy)` |
| P3 | L01 | Weak | Easy |

A Learning case, with the demonstration policy `weak 1/2`, `mastered 4/5`, is a
learner with 3 or 4 of 6 correct on `quiz/practice/Sk`; it gives Medium.

### 19.7 E2E: CT35-CASES/FIXTURES-2026-11-15-r2 and CT20-F-2026-11-15-r2

- **CT35-CASES r2** adds A-4 (§8.1). **CT35-FIXTURES r2** adds the question
  difficulties, the per-difficulty E2E practice Quizzes and the named test policy
  (§8.2).
- **CT20-F r2** makes A-4 release-required on `chromium-desktop` together with A-1
  (§11). It is the only end-to-end evidence of the architecture's practice-difficulty
  step; nothing else in CT20.F changes.
- FE26 implements A-4 in the existing `adaptive-flow.spec.ts`; journey-Course rules
  (§7.2) apply.

### 19.8 Verification oracles — specified, NOT_RUN

Placement: the existing `PracticeDifficultySelectorTest` (rule), the Adaptive and
Assessment service unit tests, `AuthorizationIntegrationTest` (admission),
`AdaptiveLearningFlowIntegrationTest` (real PostgreSQL), and frontend tests beside
`learningApi.test.ts`.

| Oracle | Required result |
|---|---|
| B8-P01 | Mapping: Unknown/Weak → EASY, Learning → MEDIUM, Mastered → HARD; reasons use `not yet assessed` for Unknown |
| B8-P02 | Governing skill: in a multi-skill Lesson the easiest target wins; ties follow CT07.reasons name order, including case-sensitive names and equal names by UUID |
| B8-P03 | Candidates: hidden, `test`, `diagnostic`, mixed-difficulty, empty and foreign-skill Quizzes are excluded; a Quiz covering a subset of M(L) is included |
| B8-P04 | Fallback for each target, with `fallback` and the second template exactly when the level differs; no candidate at any level → null |
| B8-P05 | Ties at one level: title by `String.compareTo`, then UUID |
| B8-P06 | Null when `adaptiveEnabled` is false or M(L) is empty; no readiness evaluation |
| B8-P07 | Admission: 401; malformed UUID 400; Instructor and Administrator 403; LEFT/absent/hidden-Course outcomes as `/adaptive-guidance`; hidden, missing or foreign Lesson 404 |
| B8-P08 | Read-only and isolation: no row written; the port has no production caller (allowlist); starting and submitting a recommended Quiz captures its full membership and produces the same evidence and mastery as any practice Quiz (CT04-START/CT08-EVIDENCE unchanged) |
| B8-P09 | `readPracticeQuizProfiles` allowlist and data minimality (no answer data); one query per call |
| B8-P10 | Dataset: SD04 r2 has sixty uniform practice Quizzes; §19.6's table holds for P1–P3 after their scripts; the Learning case holds with the demonstration policy |
| B8-P11 | Frontend: the entry appears for the guidance target and the open Lesson, shows the reason verbatim and the mastery statement, renders nothing for null, and links to the existing quiz route; every Quiz remains listed |
| A-4 | E2E per §8.1 |

### 19.9 Accepted pedagogical limitation and its disclosure

- **The accepted limitation.** Because evidence is the latest attempt with equal
  question weight (ADR005 §6), a Weak learner who answers both questions of an Easy
  practice Quiz correctly becomes Mastered, and may then be tested out of a Lesson
  by ADR011 §7.
- **Leader acceptance.** The Leader accepted this for the academic Final (§19.1).
- **Disclosure duties.**
  - Learner screens keep ADR011 §9's statement.
  - The Final report (DO03) and the CT36 evaluation report state the limitation
    plainly.
  - Neither may claim that difficulty-targeted practice improves or validly measures
    learning. §6's learning gain stays a simulated procedure demonstration.
- **No compensating change.** No threshold, weighting or evidence rule is changed to
  offset the limitation.

### 19.10 Handoffs

The responsible members confirm scope in their Issues; Issue file lists are planning
records and do not limit these contracts.

| Contract | Responsible Issues |
|---|---|
| Rule, selector method and REST (§19.4) | AD11 (#168) |
| Profile read (§19.3) | AS07 (#158), Assessment owner. AS07.select's question-level wiring is not used in Final |
| Frontend (§19.5) | FE09 (#194) and the Lesson page's owner |
| Dataset (§19.6) | SD04 |
| A-4 E2E case | FE26 |
| Evaluation and evidence | QA05/QA08 |
| Limitation disclosure | DO03 |

**Start prerequisites** are those of §13: review, CI and protected integration of
ADR012 and then this record.

### 19.11 Post-change consistency review

- **Re-checked.** OpenAPI adds one path and three components and changes no existing
  value. ADR011 §8's allowlist, §15's guidance and §17's options are consistent with
  §19.1.
- **CT15/CT36.** Counts and dispositions are unchanged except CP-093's r2: ADR005
  readiness holds, and no scenario script uses the new Quizzes.
- **CT35.** A-1–A-3 stay policy-independent, and A-4's policy is explicit.
- **CT20.F.** Exactly one release-blocking case is added.
- **Recommendation history.** Path recommendations still preserve type, target,
  reason and time (V005). The practice recommendation adds no persisted state.

No new contradiction was found. Checkpoint count: 100 roadmap checkpoints, all with
a technical approval record; CP-093/098/099/100 move to r2. The E02 decision lies
outside the 100. No implementation or test result is claimed.
