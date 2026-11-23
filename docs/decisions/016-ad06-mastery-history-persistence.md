# ADR016: AD06 Mastery and Recommendation-History Write Persistence

## 1. Authority, provenance and scope

**DELEGATED_TECHNICAL_APPROVAL; DELEGATED_APPROVED**, 23 November 2026
(Asia/Ho_Chi_Minh). The Project Leader and repository Owner @chi-thanh-nguyen gave the
explicit instruction **“CodeMastery — PR #263 Full AD06 Completion, Comprehensive Technical
Audit, Remediation & Git Attribution”**. It authorizes completing AD06
([#42](https://github.com/chi-thanh-nguyen/codemastery/issues/42)) on
[PR #263](https://github.com/chi-thanh-nguyen/codemastery/pull/263) and delegates the selection
and recording of the AD06-specific decisions that no effective contract makes: the write
mechanism (D1), the CT14 test placement (D2), the CT17-P6 runtime-privilege revision within the
approved ceiling (D3) and any small supporting documentation decision (D4).

Claude Code (Anthropic) made every selection below **under that delegation**. The Leader did not
review any individual selection. The delegation covers AD06 only. It does not grant personal
Leader review, CODEOWNER approval, acceptance of any residual risk, provisioning of any real
database, deployment, a waiver of a course requirement, or approval of any other Issue or contract.

These revisions become effective on `main` only through PR #263's compliant integration.
Technical approval is not review, integration, QA acceptance or deployment; §7 keeps them apart.

Known AI use: Claude Code (Anthropic) analysed the repository and PostgreSQL behaviour, selected
the design, and wrote the implementation, tests and this record. No AI service is added to the
application.

## 2. Gap and consumed contracts

Issue #42 states that V005 approves uniqueness per learner and skill and categorical storage but
“does not select upsert, concurrency, retry or update algorithms”, and ADR008 §5 lists no
standalone Adaptive repository test placement. No effective record closes either gap. The
selections below consume, without changing them:

| Contract (effective record, integration on `main`) | What AD06 must preserve |
| --- | --- |
| V005 (`backend/src/main/resources/db/migration/`) | `uq_learner_skill_masteries_user_skill` (not deferrable), the state and type checks, nonblank reason, NOT NULL columns, foreign keys; application-supplied UUIDs and timestamps |
| ADR005 §6.1, CT06 (DELEGATED_APPROVED, `81806fc`) | Categorical state only; one row per learner and skill; an absent row means Unknown and an explicit Unknown is allowed |
| ADR011 §4, CT08.execution (DELEGATED_APPROVED, `c80fda9`) | Step 5 inserts or updates the row of an assessed skill only; one synchronous transaction; lock order Course root → … → mastery rows → Enrollment → `lesson_progress` → `path_recommendations` inserts |
| ADR011 §5, CT08.failure | Any failure propagates and the whole unit rolls back; no retry |
| ADR011 §§7 and 9, CT07.routing and CT07.reasons | Whether to insert a row, the reason text and one microsecond-truncated, strictly-later instant per run are the caller's; history is append-only and never collapsed |
| ADR008 §5, CT14.coverage (DELEGATED_APPROVED, `b6fa24b`) | `AbstractIntegrationTest`; rolled-back or committed-and-cleaned isolation; no sleeps; absent placements need a bounded structural decision |
| ADR012 §19.2, `CT17-P6-PLAN-2026-11-15-r2` (DELEGATED_APPROVED, `093c659`) | Ceiling `learner_skill_masteries` SELECT, INSERT, UPDATE (state); `path_recommendations` SELECT, INSERT; each consumer adds a dated matrix revision with its script and CI change |

## 3. D1 — `AD06-PERSISTENCE-2026-11-23-r1`: write mechanism

### 3.1 Findings that drive the selection

The first PR revision used Spring Data `save()` with an assigned UUID. Spring Data treats a
non-null identifier as not new and calls `EntityManager.merge`. Running the new real-PostgreSQL
suite against that revision showed, on Hibernate 7.4.5 and PostgreSQL 17.11:

1. `append` of an existing identifier silently overwrote the committed history row through a
   full-row `UPDATE`. This violates ADR011 §9.
2. Both writes stayed pending in the persistence context. JDBC readers in the same transaction
   did not see them; Course's progress code is such a reader. They executed only at the commit
   flush, after the caller's later Enrollment and `lesson_progress` writes, which inverts
   ADR011 §4's lock order. Constraint failures surfaced at commit, not at the call.
3. Outside any transaction, `storeState` ran without error instead of being refused, so the
   adapter did not enforce the caller-owned transaction its contract states.
4. Each write issued an extra `SELECT` by identifier, which needs `SELECT (id)`.

### 3.2 Selected mechanism

**Mastery (`storeState`).** One native statement, executed at the call:

```sql
INSERT INTO public.learner_skill_masteries (id, user_id, skill_id, state)
VALUES (:candidateId, :userId, :skillId, :state)
ON CONFLICT (user_id, skill_id) DO UPDATE SET state = EXCLUDED.state
```

- An existing row keeps its identifier, learner and skill. Only `state` changes, so no full-row
  update exists. The random candidate identifier is used only for a new row.
- A concurrent first write of the same pair waits for the other writer's uncommitted key and then
  updates, instead of failing with 23505. ADR011 already serializes same-Course runs on the root,
  so this is defence in depth, not a new concurrency policy. No retry is added.
- The statement bypasses the persistence context. AD06 never loads mastery entities, so it leaves
  none stale. `flushAutomatically` first flushes the caller's pending changes, so the statement
  runs in the caller's order.

**History (`append`).** One native plain `INSERT` of the six columns, executed at the call. A
reused identifier fails with the `pk_path_recommendations` violation (23505) and never changes the
stored row. No update, delete, de-duplication or comparison exists.

**Inputs.** `storeState` rejects null arguments. `append` rejects a null recommendation or field
(the V005 NOT NULL columns, checked earlier with the same meaning). It also rejects an instant
finer than one microsecond: PostgreSQL would round it, and ADR011 §9 forbids the server from
adjusting a supplied time. Both are rejected before any SQL. Blank reasons, codes, keys and foreign
keys stay enforced by V005; violations surface as Spring `DataIntegrityViolationException`s with
the PostgreSQL SQLSTATE.

**Transaction participation.** Both adapter methods are
`@Transactional(propagation = MANDATORY)`. With an active transaction they join it, and nothing
else changes. Without one they throw `IllegalTransactionStateException` and execute nothing.
MANDATORY never starts, suspends, commits or narrows a transaction, so ADR011 §§4–5's unit is
neither narrowed nor widened. Failures propagate unchanged.

**Mapping.** `MasteryMapper` and `RecommendationMapper` are injected beans with instance methods.
They map the exact V005 codes and reject any other spelling, and they apply no default or clock.

### 3.3 Alternatives rejected

| Alternative | Reason rejected |
| --- | --- |
| Keep `save()` with an assigned UUID | Merge path: history rewrite, deferred writes, extra `SELECT` (§3.1) |
| Targeted `UPDATE`, then `EntityManager.persist` and flush when no row matched | Two statements. A concurrent first write fails with 23505 and the unit rolls back. A flush of the whole context is also needed to keep the lock order |
| `UPDATE`, then a native `INSERT` | Same race as the row above, with no benefit over one statement |
| `persist` and flush for history | Equivalent rows, but it keeps entities managed and flushes unrelated caller state. A native INSERT keeps the SQL exactly as the privilege matrix states |
| `REQUIRES_NEW`, adapter-owned `REQUIRED`, or retry | Would commit or retry outside the ADR011 unit |
| `ON CONFLICT DO NOTHING` for history | Would hide a reused identifier, which is an integrity defect in the caller |

The Course `markCompleted` upsert is a precedent for the technique. It was not the reason for
choosing it: the reasons are V005's non-deferrable key, single-statement atomicity and the
column ceiling.

## 4. D2 — `CT14-AD06-PLACEMENT-2026-11-23-r1`: test placement

Exactly two test files are added inside existing directories (recorded in
[repository structure](../repository-structure.md#ad06-test-placement--23-november-2026)):

| File | Responsibility |
| --- | --- |
| `backend/src/test/java/com/codemastery/modules/adaptive/AdaptivePersistenceMapperTest.java` | Unit (Surefire): exact four and three V005 codes, field-for-field round trips, rejection of other spellings, null behaviour |
| `backend/src/test/java/com/codemastery/integration/repository/AdaptivePersistenceIntegrationTest.java` | Real PostgreSQL (Failsafe), extends `AbstractIntegrationTest`: both write branches, row identity, no default rows, exact SQL issued, V005 constraints, duplicate identifier, concurrent first write, transaction participation and rollback, refusal outside a transaction, and execution under a transaction-scoped role holding exactly the r8 rows |

Alternatives rejected: adding cases to `AdaptiveLearningFlowIntegrationTest` or
`MasteryProgressTransactionIntegrationTest`, whose scope is the AD13 flow and atomicity (misleading
scope); H2 or mocks, which cannot show PostgreSQL upsert, locking or privilege behaviour; a new
directory, which would be a structural change without benefit. CODEOWNERS is unchanged: the
existing Adaptive test rule covers the unit test, and like the other `integration/repository/`
suites the integration test matches no rule.

## 5. D3 — `CT17-P6-PRIVILEGES-2026-11-23-r8`: runtime privileges

The normative record is
[deployment.md](../deployment.md#ct17-p6-privileges-2026-11-23-r8--ad06-mastery-and-history-writes-within-the-adr012-192-ceiling-23-november-2026).
`learner_skill_masteries`: INSERT; SELECT (user_id, skill_id, state); UPDATE (state).
`path_recommendations`: INSERT. Both rows are narrower than the ceiling. PostgreSQL 17.11 was
measured to require SELECT on the conflict-target columns and on `state` for the upsert;
without them it fails with 42501. AD06 issues no read, so no table-level SELECT is granted. The
matrix, `verify_role_separation` and the two VM tests change together. No HTTP operation reaches
the adapters yet, so the real-backend runtime test gains no case; AD13 adds it.

## 6. D4 — documentation

`docs/testing.md` records the verification and its evidence, `docs/repository-structure.md` the
D2 addition, `docs/deployment.md` the r8 record, and the decision index lists this ADR. The
immutable references, `docs/data-model.md` (its Batch 6 note already says the table is upserted)
and `docs/adaptive-learning.md` are unchanged.

## 7. Exclusions, reconciliation and remaining gates

- **Not AD06.** Calculation, classification, routing, reasons, the clock and the strictly-later
  instant check, readiness, diagnostics, notifications, reads and REST endpoints, and the
  orchestration of ADR011 §4 (AD13 and the consumers named in #42). This record does not claim
  calculator, event-flow or atomic mastery/progress verification.
- **AD16 reconciliation.** AD16 adds read methods to the same interfaces and Spring Data
  repositories, together with its own CT17-P6 revision, which adds the ceiling's table-level
  SELECT. A read that loads mastery entities must not rely on a managed instance after a
  `storeState` in the same transaction, because the upsert bypasses the persistence context.
- **AD13 obligations.** Call the adapters inside the ADR011 unit in its lock order; supply
  microsecond-truncated instants; add the real-backend runtime-privilege case for quiz
  submission; and verify end-to-end commit and rollback in its integration suites.
- **Gates.** Owning-module (@thanhtran0311), infrastructure (@tihh647-ui) and independent Leader
  CODEOWNER review; required CI on the final head; protected integration; provisioning of r8 on any
  real database (Leader-reserved); QA03/QA04 independent verification.
