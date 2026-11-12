# Decision records — index

This index lists where CodeMastery's technical decision records live and what their
status labels mean. It is navigation only and approves nothing. The normative text
is always the linked record.

Most contract decisions are recorded next to the subject they govern: setup, testing,
deployment, data model and adaptive learning. The ADRs in this directory hold
module-boundary, Interim and governance decisions.

**Pending contract checkpoints** are tracked in the Contract Issues (`type:contract`,
stable IDs `CT01`–`CT40`) in GitHub and in Project #3, not here.

## Approval status labels

| Label | Meaning |
|---|---|
| `LEADER_APPROVED` | Approved and effective **under the Project Leader's authority**, for exactly the scope written in the record. The approval may be the Leader's own decision (for example [ADR003](003-postgresql-alpine-su-exec-image.md)) or a delegated technical approval that the Leader has confirmed as effective (records covered by [ADR004](004-leader-approval-of-delegated-records.md)). The record's own authority line, for example "DELEGATED_TECHNICAL_APPROVAL; LEADER_APPROVED", says which. **It does not authorize implementation**, and does not establish implementation completeness, integration readiness, integration verification, deployment or release. It accepts no residual risk unless the record says so. Excluded, deferred or Leader-reserved items inside the record keep their own labels. |
| `DELEGATED_APPROVED` | Technical approval made by a delegate under an explicit Leader instruction for that task, effective for its stated scope ([ADR002](002-interim-delegated-contracts.md), the 8 November 2026 records). Same non-implications as above. |
| `DELEGATED_PARTIALLY_APPROVED` | As `DELEGATED_APPROVED`, but only for the named part of a checkpoint; the rest stays pending ([CT11 field set](../data-model.md#notification-fields--ct11read-field-set-only--ct11-read-fields-2026-11-08-r1--delegated_partially_approved)). |
| `DELEGATED_TECHNICAL_APPROVAL` | Authority line: a delegate made the technical selection under an explicit Leader instruction. It is paired with a status label. |
| `LEADER_AUTHORIZED_BY_EXPLICIT_INSTRUCTION` | A scope selection made by the Leader's instruction ([ADR002 CT20.I](002-interim-delegated-contracts.md#ct20i--leader_authorized_by_explicit_instruction)). |
| `APPROVED_PENDING_PROTECTED_INTEGRATION` | **Retired label, used until 8 November 2026.** Technical approval awaiting integration through the protected review path. Every occurrence in `docs/` was renamed to `LEADER_APPROVED` by [ADR004](004-leader-approval-of-delegated-records.md), which lists the records. |
| `PROPOSED_NOT_EFFECTIVE` | A proposal. Not approved; nothing may rely on it as a contract. |
| `PROPOSED_LEADER_RESERVED` | A proposal whose decision is reserved to the Leader personally. |
| `PENDING_LEADER_INPUT`, `PENDING_LEADER_APPROVAL`, `PENDING_LEADER_RISK_ACCEPTANCE` | Awaiting, respectively, concrete Leader-supplied facts, a Leader approval, or an explicit Leader risk acceptance. |
| `DEFERRED`, `BLOCKED_OR_UNRESOLVED`, `BLOCKED_BY_MISSING_EVIDENCE` | Not decided; the record states why and what is missing. |

Analysis labels that appear inside reconciliation tables describe evidence, not
approval: `APPROVED_AND_CONSISTENT`, `APPROVED_BUT_INCOMPLETE`, `PREVIOUSLY_APPROVED`,
`EFFECTIVE_APPROVAL`, `IMPLEMENTED_NOT_APPROVED` and similar.

## Current Batch 4 checkpoint inventory — 10 November 2026

**8/8 technical checkpoints are DELEGATED_APPROVED.** ADR008 is integrated by
[PR #231](https://github.com/chi-thanh-nguyen/codemastery/pull/231), main
`b6fa24b396d08e323ea54882af0a008077301ab6`; ADR009 is integrated by
[PR #233](https://github.com/chi-thanh-nguyen/codemastery/pull/233), main
`fe73f9af640317541b0deaca57ad30e0cd198c0d` (11 November 2026; no approving review
recorded, so independent review remains outstanding). This is the current
one-row-per-checkpoint inventory; the revision/history tables below retain their
dated preparation states. *Current-state correction, 12 November 2026 (ADR010 task):
the earlier wording “ADR009 awaits PR #233” and the CP-033/034 “pending #233” cells
were stale after that merge.*

| CP | Slice | Current revision | Authoritative record / integration |
|---|---|---|---|
| CP-029 | CT34.runner | `CT34-RUNNER-2026-11-09-r1` | [ADR008 section 3, merged #231](008-batch-04-contract-approvals.md#3-ct34runner) |
| CP-030 | CT34.fixtures | `CT34-FIXTURES-2026-11-10-r2` | [ADR008 section 10, merged #231](008-batch-04-contract-approvals.md#10-f1f2-fixture-amendment--10-november-2026) |
| CP-031 | CT14.coverage | `CT14-COVERAGE-2026-11-09-r1` | [ADR008 section 5, merged #231](008-batch-04-contract-approvals.md#5-ct14coverage) |
| CP-032 | CT39.binding | `CT39-BINDING-2026-11-09-r1` | [ADR008 section 6, merged #231](008-batch-04-contract-approvals.md#6-ct39binding-and-ct39consumers) |
| CP-033 | CT17.F-runtime | `CT17-F-RUNTIME-2026-11-10-r2` | [ADR009 section 9, merged #233](009-ct17-final-runtime-recovery.md#9-continuation-attestation-delegated-selections-and-executed-qualification) |
| CP-034 | CT17.F-recovery | `CT17-F-RECOVERY-2026-11-10-r2` | [ADR009 section 9, merged #233](009-ct17-final-runtime-recovery.md#9-continuation-attestation-delegated-selections-and-executed-qualification) |
| CP-035 | CT34.ci | `CT34-CI-2026-11-09-r1` | [ADR008 section 7, merged #231; consumes CP-030 r2](008-batch-04-contract-approvals.md#7-ct34ci) |
| CP-036 | CT39.consumers | `CT39-CONSUMERS-2026-11-09-r1` | [ADR008 section 6, merged #231](008-batch-04-contract-approvals.md#6-ct39binding-and-ct39consumers) |

The Leader's dated Education Hub evidence supplies **$88 remaining / 31 October
2027 expiration**; [ADR009 section 10](009-ct17-final-runtime-recovery.md#10-post-pr231-integration-and-azure-evidence--10-november-2026)
preserves its attested UI provenance and earlier gaps. Forecast-to-Final funding,
capacity, runtime access, Final security admission and actual recovery/deployment
acceptance remain gates; no new spending or resource/grant authorization follows.

## CT17 Final follow-up — 9 November 2026

**Current:** [ADR009 §9](009-ct17-final-runtime-recovery.md#9-continuation-attestation-delegated-selections-and-executed-qualification)
records `CT17-F-RUNTIME-2026-11-10-r2` and
`CT17-F-RECOVERY-2026-11-10-r2` **DELEGATED_APPROVED** (technical design,
Leader-confirmed resource/credit-only funding policy and identity-bounded mechanisms).
The Leader's current factual attestation and expanded technical delegation,
executed synthetic qualification and remaining operational gates are explicit.
Batch 4 is **8/8 technically approved**; section 10's new dated credit evidence supersedes
the earlier balance/expiry gap. Fresh funding feasibility and
implementation/runtime/recovery acceptance remain gates; Readiness stays Bounded.
PR #233 is the existing follow-up; no new ADR/PR or change to #231 is introduced.
The paragraph below preserves the original r1 preparation state.

[ADR009](009-ct17-final-runtime-recovery.md) records
`CT17-F-RUNTIME-2026-11-09-r1` and `CT17-F-RECOVERY-2026-11-09-r1` as
**DELEGATED_PARTIALLY_APPROVED**: Leader-confirmed resource/origin/responsibility
selections, bounded runtime policy and recovery design objectives/protocol.
Actual runtime/destination/cost bindings and executed MinIO/encryption qualification
remain gates; Batch 4 is not 8/8 approved. The six ADR008 decisions remain in
unmerged [PR #231](https://github.com/chi-thanh-nguyen/codemastery/pull/231),
head `235bc237bcc1a55f7e6015f715d283991b307f76`, and are not copied or amended here.
ADR009 is numbered after that reserved record and can be reviewed independently.
Neither document establishes implementation, runtime acceptance or deployment authority.

## Architecture decision records

| ADR | Subject | Status |
|---|---|---|
| [ADR001](001-modular-monolith-module-boundaries.md) | Modular-monolith module boundaries (§§1–10); CT01 (§11) and CT03 (§12) contract records; CT06 boundary proposal (§13) | §§1–10 approved architecture; §11/§12 `LEADER_APPROVED` (ADR004); §13 historical proposal; Batch 1 choices superseded by ADR005 |
| [ADR002](002-interim-delegated-contracts.md) | Bounded Interim delegated contracts and full source disposition inventory; FE07 and SD01/SD02 records | Slices `DELEGATED_APPROVED` (CT20.I Leader-authorized); FE07 and CT15-SD `LEADER_APPROVED` (ADR004); other dispositions as listed |
| [ADR003](003-postgresql-alpine-su-exec-image.md) | PostgreSQL image hardening (Alpine with su-exec) | `LEADER_APPROVED` (2 November 2026); residual risks `PENDING_LEADER_RISK_ACCEPTANCE` |
| [ADR004](004-leader-approval-of-delegated-records.md) | Leader approval of the earlier delegated records; label rename | `LEADER_APPROVED` (8 November 2026), merged PR #227 |
| [ADR005](005-batch-01-contract-approvals.md) | Nine Batch 1 approvals and bounded CT03/CT24/CT14/CT18 prerequisites | `DELEGATED_APPROVED` (8 November 2026); integrated via PR #228 (9 November 2026) |
| [ADR006](006-batch-02-contract-approvals.md) | Eight verified Batch 2 checkpoints: progress, resume, embed, authoring and recovery | Eight `DELEGATED_APPROVED`; integrated via PR #229, 9 November 2026; implementation/exposure gates retained |
| [ADR007](007-batch-03-contract-approvals.md) | Ten verified Batch 3 checkpoints CP-019–028; two bounded prerequisite slices | `DELEGATED_APPROVED`; integrated via PR #230, 9 November 2026; implementation/verification gates retained |
| [ADR008](008-batch-04-contract-approvals.md) | Six Batch 4 testing/clock decisions; CP-030 r2 | `DELEGATED_APPROVED`; integrated via #231 on 10 November. Its original CT17 pending dispositions are historical, subsequently resolved by ADR009 r2 |
| [ADR009](009-ct17-final-runtime-recovery.md) | CP-033/034 r2 runtime/recovery decisions, bounded qualification and dated Azure evidence | Two `DELEGATED_APPROVED`; integrated via #233 on 11 November (no approving review recorded); execution/admission gates retained |
| [ADR010](010-batch-05-contract-approvals.md) | Ten Batch 5 checkpoints CP-037–046: protected files, assignments, file widget; CT01/CT18 bounded prerequisites | `DELEGATED_APPROVED` (12 November 2026); pending independent review, CI and protected integration; implementation/exposure gates retained |

## Contract decision records by revision

| Revision | Subject | Record | Status | Introduced by |
|---|---|---|---|---|
| `CT17-F-RUNTIME-2026-11-10-r2` | CP-033 runtime attestation, custody/paths/TLS/resource policy | [ADR009 §9](009-ct17-final-runtime-recovery.md#9-continuation-attestation-delegated-selections-and-executed-qualification) | `DELEGATED_APPROVED`; dated balance/expiry supplied in section 10; funding feasibility/runtime acceptance pending | PR #233 |
| `CT17-F-RECOVERY-2026-11-10-r2` | CP-034 destination/custody design; qualified cold MinIO/CMS/provenance protocol | [ADR009 §9](009-ct17-final-runtime-recovery.md#9-continuation-attestation-delegated-selections-and-executed-qualification) | `DELEGATED_APPROVED`; provisioning/implementation/F2/operational acceptance pending | PR #233 |
| ADR002 slices (11 Oct 2026) | CT20.I, CT18.minimum, CT01.core, CT16.minimum, CT38.core, CT03.catalogue, CT21.catalogue, CT06.prototype, CT24.calculation, CT15.I-data, CT14.interim, CT36.I-results, NC-EVIDENCE-MODE, CT17.I-scope, CT17.I-access | [ADR002 approved slices](002-interim-delegated-contracts.md#approved-slices) | `DELEGATED_APPROVED` | PR #73 |
| CT01.identity (18 Oct 2026) | Cross-module identity facade, U01 | [ADR001 §11.4](001-modular-monolith-module-boundaries.md#114-ct01identity-exact-public-boundary-and-u01-disposition) | Leader approval, reaffirmed in CT01-2026-10-21-r1 | PR #83 |
| `CT01-2026-10-21-r1` | Login/JWT lifecycle, logout, authorization handoff | [ADR001 §11](001-modular-monolith-module-boundaries.md#11-ct01-approved-technical-contract--protected-integration-pending) | `LEADER_APPROVED` | PR #91 |
| `CT03-2026-10-21-r1` | Learning access, assertions, U03/U04, U07 authorization half | [ADR001 §12](001-modular-monolith-module-boundaries.md#12-ct03-access-and-consumer-contract-reconciliation) | `LEADER_APPROVED` | PR #92 |
| `CT16-2026-10-24-r1` | Frontend HTTP, auth session, shell | [setup](../setup.md#ct16-delegated-technical-approval--24-october-2026) | `LEADER_APPROVED` | PR #102 |
| `CT16-INTERIM-COVERAGE-2026-10-24-r1` | CT16 Interim coverage certification | [setup](../setup.md#ct16-interim-technical-coverage-certification--24-october-2026) | `LEADER_APPROVED` | PR #102 |
| `CT16-FINAL-2026-10-25-r1` | CT16 Final F1/F2 | [setup](../setup.md#ct16-consolidation-and-bounded-final-approvals--25-october-2026) | `LEADER_APPROVED` (F1/F2 only) | PR #102 |
| `CT16-PROFILE-2026-10-25-r1` | Profile extension | [setup](../setup.md#ct16-bounded-profile-extension--25-october-2026) | `LEADER_APPROVED` | PR #102 |
| `CT14-INTERIM-FE03-FE07-2026-10-24-r1` | Interim FE03/FE07 verification contract | [testing](../testing.md#approved-interim-fe03fe07-verification-contract--24-october-2026); [structure](../repository-structure.md#bounded-interim-fe03fe07-test-inventory) | `LEADER_APPROVED` | PR #106 |
| `CT14-FINAL-2026-10-25-r1` | CT14 Final F1/F2 | [testing](../testing.md#ct14-consolidation-and-bounded-final-approvals--25-october-2026) | `LEADER_APPROVED` (F1/F2 only) | PR #106 |
| `CT14-PROFILE-AUTH-2026-10-25-r1` | Profile and Auth UI verification | [testing](../testing.md#ct14-profile-and-auth-ui-verification-extension--25-october-2026) | `LEADER_APPROVED` | PR #106 |
| `CT17-2026-10-24-r1` | Deployment policy | [deployment](../deployment.md#ct17-delegated-technical-approval--24-october-2026) | `LEADER_APPROVED` | PR #103 |
| `CT17-INTERIM-2026-10-24-r2` | Interim policy completion (P1–P10) | [deployment](../deployment.md#ct17-interim-technical-policy-completion--24-october-2026) | `LEADER_APPROVED` | PR #103 |
| `CT17-FINAL-2026-10-25-r1` | CT17 Final F1/F2 | [deployment](../deployment.md#ct17-consolidation-and-bounded-final-approvals--25-october-2026) | `LEADER_APPROVED` (F1/F2 only); concrete bindings `PENDING_LEADER_INPUT` | PR #103 |
| `CT17-INTERIM-BINDINGS-2026-10-25-r1` | Deployment bindings B1–B3 | [deployment](../deployment.md#ct17-additional-interim-deployment-bindings--25-october-2026) | `LEADER_APPROVED` (B1–B3 only) | PR #103 |
| `CT17-P6-PRIVILEGES-2026-10-28-r1` | Runtime database privileges and operation gates | [deployment](../deployment.md#ct17-runtime-database-privileges-and-operation-gates--28-october-2026) | `LEADER_APPROVED` | PR #110 |
| `FE07-2026-10-27-r1` | CT22.membership, catalogue extension | [ADR002 FE07](002-interim-delegated-contracts.md#fe07-full-implementation--delegated-approval-27-october-2026) | `LEADER_APPROVED` | PR #101 |
| `CT15-SD-2026-10-30-r3` | SD01/SD02 sample data | [ADR002 SD01/SD02](002-interim-delegated-contracts.md#sd01sd02-sample-data-completion--delegated-approval-30-october-2026) | `LEADER_APPROVED` | PR #116 |
| `CT16-ROUTES-2026-11-08-r1` | Final route map, navigation, start pages | [setup](../setup.md#ct16routes--ct16-routes-2026-11-08-r1--delegated_approved) | `DELEGATED_APPROVED` | PR #226 |
| `CT16-LEARNER-HOME-2026-11-08-r1` | Final Learner home composition | [setup](../setup.md#ct16learner-home--ct16-learner-home-2026-11-08-r1--delegated_approved) | `DELEGATED_APPROVED` | PR #226 |
| `CT37-MARKDOWN-2026-11-08-r1` | Markdown rendering policy | [setup](../setup.md#ct37markdown--ct37-markdown-2026-11-08-r1--delegated_approved) | `DELEGATED_APPROVED` | PR #226 |
| `CT11-READ-FIELDS-2026-11-08-r1` | Notification row field set | [data model](../data-model.md#notification-fields--ct11read-field-set-only--ct11-read-fields-2026-11-08-r1--delegated_partially_approved) | `DELEGATED_PARTIALLY_APPROVED` | PR #226 |
| `STRUCT-MASTERY-MAP-2026-11-08-r1` | Shared read-only Mastery Map location (structure) | [structure](../repository-structure.md#shared-read-only-mastery-map-location--struct-mastery-map-2026-11-08-r1--delegated_approved-structure-decision-not-yet-implemented) | `DELEGATED_APPROVED`, not yet implemented | PR #226 |
| `LEADER-APPROVED-2026-11-08-r1` | Effectiveness of the records above that carried the retired label | [ADR004](004-leader-approval-of-delegated-records.md) | `LEADER_APPROVED` | PR #227 |
| `CT40-LESSON-READ-2026-11-08-r1` | CT40.lesson-read | [ADR005](005-batch-01-contract-approvals.md#2-checkpoint-dispositions-and-dependency-order) | `DELEGATED_APPROVED`; integrated via PR #228 | PR #228 |
| `CT40-SKILL-TAG-2026-11-08-r1` | CT40.skill-tag | [ADR005](005-batch-01-contract-approvals.md#2-checkpoint-dispositions-and-dependency-order) | `DELEGATED_APPROVED`; integrated via PR #228 | PR #228 |
| `CT40-COVERAGE-2026-11-08-r1` | CT40.coverage | [ADR005](005-batch-01-contract-approvals.md#2-checkpoint-dispositions-and-dependency-order) | `DELEGATED_APPROVED`; integrated via PR #228 | PR #228 |
| `CT05-REPRESENTATION-2026-11-08-r1` | CT05.representation | [ADR005](005-batch-01-contract-approvals.md#2-checkpoint-dispositions-and-dependency-order) | `DELEGATED_APPROVED`; integrated via PR #228 | PR #228 |
| `CT05-MUTATION-2026-11-08-r1` | CT05.mutation | [ADR005](005-batch-01-contract-approvals.md#2-checkpoint-dispositions-and-dependency-order) | `DELEGATED_APPROVED`; integrated via PR #228 | PR #228 |
| `CT05-MAPPING-2026-11-08-r1` | CT05.mapping | [ADR005](005-batch-01-contract-approvals.md#2-checkpoint-dispositions-and-dependency-order) | `DELEGATED_APPROVED`; integrated via PR #228 | PR #228 |
| `CT05-BINDINGS-2026-11-08-r1` | CT05.bindings | [ADR005](005-batch-01-contract-approvals.md#2-checkpoint-dispositions-and-dependency-order) | `DELEGATED_APPROVED`; integrated via PR #228 | PR #228 |
| `CT06-PRODUCTION-2026-11-08-r1` | CT06.production | [ADR005](005-batch-01-contract-approvals.md#2-checkpoint-dispositions-and-dependency-order) | `DELEGATED_APPROVED`; integrated via PR #228 | PR #228 |
| `CT06-ENABLEMENT-2026-11-08-r1` | CT06.enablement | [ADR005](005-batch-01-contract-approvals.md#2-checkpoint-dispositions-and-dependency-order) | `DELEGATED_APPROVED`; original record, named scope amended by `CT06-ENABLEMENT-2026-11-08-r2` | PR #228 |
| `CT03-B1-2026-11-08-r2` | U07 completion and bounded Course boundaries | [ADR005 §9](005-batch-01-contract-approvals.md#9-amendments-prerequisites-and-compatibility) | `DELEGATED_APPROVED`; original record, named scope amended by `CT03-B1-2026-11-08-r3` | PR #228 |
| `CT24-CALCULATION-2026-11-08-r2` | Final reuse of existing pure arithmetic | [ADR005 §9](005-batch-01-contract-approvals.md#9-amendments-prerequisites-and-compatibility) | `DELEGATED_APPROVED`; integrated via PR #228 | PR #228 |
| `CT14-B1-COVERAGE-2026-11-08-r1` | Batch 1 existing test allocation | [ADR005 §9](005-batch-01-contract-approvals.md#9-amendments-prerequisites-and-compatibility) | `DELEGATED_APPROVED`; original record, named scope amended by `CT14-B1-COVERAGE-2026-11-08-r2` | PR #228 |
| `CT18-B1-ERRORS-2026-11-08-r1` | Batch 1 feature-specific failure mappings | [ADR005 §8.1](005-batch-01-contract-approvals.md#81-exact-inventory-and-rest-contract) | `DELEGATED_APPROVED`; original record, named scope amended by `CT18-B1-ERRORS-2026-11-08-r2` | PR #228 |
| `CT06-ENABLEMENT-2026-11-08-r2` | F1/F2 conditional settings and pre-history threshold lifecycle | [ADR005 §13](005-batch-01-contract-approvals.md#13-focused-f1f4-amendments--8-november-2026) | `DELEGATED_APPROVED`; provenance retained; successful-PUT validator clause corrected by N1 below; integrated via PR #228 | PR #228 |
| `CT03-B1-2026-11-08-r3` | Expected Course storage state; same-guard fail-fast admission/enforcement | [ADR005 §13](005-batch-01-contract-approvals.md#13-focused-f1f4-amendments--8-november-2026) | `DELEGATED_APPROVED`; integrated via PR #228 | PR #228 |
| `CT14-B1-COVERAGE-2026-11-08-r2` | F1–F4 verification obligations in existing suites | [ADR005 §13](005-batch-01-contract-approvals.md#13-focused-f1f4-amendments--8-november-2026) | `DELEGATED_APPROVED`; integrated via PR #228 | PR #228 |
| `CT18-B1-ERRORS-2026-11-08-r2` | Settings-only 412/428 and 409 lifecycle/busy mappings | [ADR005 §13](005-batch-01-contract-approvals.md#13-focused-f1f4-amendments--8-november-2026) | `DELEGATED_APPROVED`; integrated via PR #228 | PR #228 |
| `CT06-ENABLEMENT-N1-2026-11-08-r1` | Scoped HTTP correction to CT06.enablement r2: GET ETag, validator-free successful PUT and client follow-up GET | [ADR005 §15](005-batch-01-contract-approvals.md#15-n1-http-validator-correction) | `DELEGATED_APPROVED`; other checkpoints/prerequisite approvals unchanged; integrated via PR #228 | PR #228 |
| `CT06-ENABLEMENT-R1-2026-11-09-r1` | Complete same-snapshot guarded PUT conditions; scoped amendment of CT06.enablement r2, preserving N1 | [ADR005 §16](005-batch-01-contract-approvals.md#16-r1r3-independent-review-corrections--9-november-2026) | `DELEGATED_APPROVED`; only named input/condition/detail clauses replaced; integrated via PR #228 | PR #228 |
| `CT18-B1-ERRORS-2026-11-09-r3` | Scoped amendment of CT18 B1 r2: either failed PUT condition and safe 412 detail | [ADR005 §16](005-batch-01-contract-approvals.md#16-r1r3-independent-review-corrections--9-november-2026) | `DELEGATED_APPROVED`; remaining r2 mappings preserved; integrated via PR #228 | PR #228 |
| `CT16-B1-NO-CONTENT-2026-11-09-r1` | Bounded extension of CT16-2026-10-24-r1 for five unchanged 204 operations | [ADR005 §16](005-batch-01-contract-approvals.md#16-r1r3-independent-review-corrections--9-november-2026) | `DELEGATED_APPROVED`; existing JSON/Auth contracts preserved; not implemented; integrated via PR #228 | PR #228 |
| `CT14-B1-COVERAGE-2026-11-09-r3` | Extends CT14 B1 r2/N1 with B1-O18/O19 in existing suites | [ADR005 §16](005-batch-01-contract-approvals.md#16-r1r3-independent-review-corrections--9-november-2026) | `DELEGATED_APPROVED`; original allocations preserved; integrated via PR #228 | PR #228 |
| `CT22-PROGRESS-2026-11-09-r1` | CT22.progress | [ADR006 §3](006-batch-02-contract-approvals.md#3-ct22progress) | `DELEGATED_APPROVED`; integration pending | Batch 2 PR |
| `CT22-RESUME-2026-11-09-r1` | CT22.resume, including authorized recency | [ADR006 §4](006-batch-02-contract-approvals.md#4-ct22resume--per-course-visit-and-resume) | `DELEGATED_APPROVED`; implementation/integration pending | Batch 2 PR |
| `CT37-EMBED-2026-11-09-r1` | CT37.embed | [ADR006 §6](006-batch-02-contract-approvals.md#6-ct37embed) | `DELEGATED_APPROVED`; integration pending | Batch 2 PR |
| `CT21-AUTHORING-2026-11-09-r1` | CT21.authoring, retaining file/Admin/event exclusions | [ADR006 §5](006-batch-02-contract-approvals.md#5-ct21authoring) | `DELEGATED_APPROVED`; integration pending | Batch 2 PR |
| `CT02-DELIVERY-2026-11-09-r1` | CT02.delivery | [ADR006 §9](006-batch-02-contract-approvals.md#9-ct02-recovery-chain--approved-after-the-explicit-exception) | `DELEGATED_APPROVED`; implementation/integration pending | Batch 2 PR |
| `CT02-WORKFLOW-2026-11-09-r1` | CT02.workflow | [ADR006 §9](006-batch-02-contract-approvals.md#9-ct02-recovery-chain--approved-after-the-explicit-exception) | `DELEGATED_APPROVED`; implementation/integration pending | Batch 2 PR |
| `CT02-TOKEN-2026-11-09-r1` | CT02.token | [ADR006 §9](006-batch-02-contract-approvals.md#9-ct02-recovery-chain--approved-after-the-explicit-exception) | `DELEGATED_APPROVED`; implementation/integration pending | Batch 2 PR |
| `CT02-REST-2026-11-09-r1` | CT02.rest | [ADR006 §9](006-batch-02-contract-approvals.md#9-ct02-recovery-chain--approved-after-the-explicit-exception) | `DELEGATED_APPROVED`; implementation/integration pending | Batch 2 PR |

## Proposals and open Leader decisions recorded in the repository

| Record | Status |
|---|---|
| [CT06 production and enablement proposal](../adaptive-learning.md#19-ct06-reconciliation-and-completion-proposal) (with [ADR001 §13](001-modular-monolith-module-boundaries.md#13-ct06-settings-and-coverage-boundary-reconciliation--proposal) and the [data-model note](../data-model.md#ct06-production-and-enablement-proposal--no-physical-change)) | Historical `PROPOSED_NOT_EFFECTIVE` proposal; corresponding Batch 1 choices now superseded by [ADR005](005-batch-01-contract-approvals.md), with LD-B1-1/2/3 resolved by delegation |
| [CT14 coverage and isolation proposal](../testing.md#ct14-coverage-and-isolation-reconciliation--proposal) | `PROPOSED_NOT_EFFECTIVE`, partly overtaken by CT14-FINAL |
| [CT16 proposal, 18 October](../setup.md#ct16-frontend-contract-proposal--18-october-2026-historical-record) and [CT17 proposal, 18 October](../deployment.md#ct17-completion-proposal--18-october-2026-historical-record) | Historical records, superseded by the approvals above |
| DP01 security and image decisions in [deployment](../deployment.md) (sections "Outstanding Leader decisions", "Remaining decisions", "Admission and outstanding decisions") | Open Leader decisions F1, F4, F5, F6, F10, F11 and image admissions |

Batch 2 additional scope authorizations are recorded in
[ADR006 §7](006-batch-02-contract-approvals.md#7-compatibility-exceptions-expressly-authorized-by-the-leader):
`ARCH-RESET-EMAIL-2026-11-09-r1` and `CT22-RESUME-SCHEMA-2026-11-09-r1`.
CT02 delivery/workflow/token/REST revisions `CT02-DELIVERY-2026-11-09-r1`,
`CT02-WORKFLOW-2026-11-09-r1`, `CT02-TOKEN-2026-11-09-r1` and
`CT02-REST-2026-11-09-r1` are **DELEGATED_APPROVED** in
[ADR006 §9](006-batch-02-contract-approvals.md#9-ct02-recovery-chain--approved-after-the-explicit-exception).
Implementation, SMTP qualification, NEW migrations and reset-specific residual-risk
acceptance before public exposure remain separate; no out-of-batch approval occurred.


### Batch 3 revisions — 9 November 2026

ADR007 is the authoritative record for the following **DELEGATED_APPROVED**
revisions. All await independent review/CI/protected integration; no runtime work
is implemented by the approvals. CT08 remains evidence-only at whole-Issue level.

| Revision | Exact slice | Record |
|---|---|---|
| `CT03-ASSESSMENT-GUARD-2026-11-09-r1` | CT03.assessment-guard | [ADR007](007-batch-03-contract-approvals.md#31-ct03assessment-guard) |
| `CT38-QUIZ-EXPIRY-2026-11-09-r1` | CT38.feature.quiz-expiry | [ADR007](007-batch-03-contract-approvals.md#32-ct38featurequiz-expiry) |
| `CT23-QUESTIONS-2026-11-09-r1` | CT23.questions | [ADR007](007-batch-03-contract-approvals.md#4-ct23questions) |
| `CT23-COMPOSITION-2026-11-09-r1` | CT23.composition | [ADR007](007-batch-03-contract-approvals.md#5-ct23composition) |
| `CT23-PUBLICATION-2026-11-09-r1` | CT23.publication | [ADR007](007-batch-03-contract-approvals.md#6-ct23publication) |
| `CT23-BINDINGS-2026-11-09-r1` | CT23.bindings | [ADR007](007-batch-03-contract-approvals.md#7-ct23bindings-and-authoring-api) |
| `CT04-START-2026-11-09-r1` | CT04.start | [ADR007](007-batch-03-contract-approvals.md#8-ct04start) |
| `CT04-SUBMISSION-2026-11-09-r1` | CT04.submission | [ADR007](007-batch-03-contract-approvals.md#9-ct04submission-and-saved-answers) |
| `CT04-EXPIRY-2026-11-09-r1` | CT04.expiry | [ADR007](007-batch-03-contract-approvals.md#10-ct04expiry) |
| `CT04-RESULTS-2026-11-09-r1` | CT04.results | [ADR007](007-batch-03-contract-approvals.md#11-ct04results) |
| `CT08-EVIDENCE-2026-11-09-r1` | CT08.evidence | [ADR007](007-batch-03-contract-approvals.md#12-ct08evidence-only) |
| `CT04-BINDINGS-2026-11-09-r1` | CT04.bindings | [ADR007](007-batch-03-contract-approvals.md#13-ct04bindings-errors-and-compatibility) |

Batch 2 present-state confirmation: PR #229 merged as
`912d4324263e19e80b87d899eae51c03842d592f` at 14:51:56 ICT on 9 November
2026 (ADR006 §1). Revision-table integration-pending text above describes its
preparation state; documentation integration is now confirmed. All residual
implementation, protected-review provenance, risk and exposure gates remain.

### Batch 4 revisions — 9 November 2026

**Historical preparation state:** the paragraph/table below retain the original
six-decision record. The current inventory above incorporates CP-030 r2, merged
ADR008 and ADR009's two technically approved r2 decisions pending #233 integration.

[ADR008](008-batch-04-contract-approvals.md) records the corrected CP-029–036
inventory. The six decisions below are **DELEGATED_APPROVED** under the explicit
Batch 4 instruction; independent review/CI/protected integration and separate
implementation/verification remain required. CT17.F-runtime/CT17.F-recovery
are **PENDING_LEADER_INPUT**, with no approved revision. No prerequisite slice
or earlier-contract amendment is introduced.

| Revision | Exact slice | Record |
|---|---|---|
| `CT34-RUNNER-2026-11-09-r1` | CT34.runner | [ADR008 §3](008-batch-04-contract-approvals.md#3-ct34runner) |
| `CT34-FIXTURES-2026-11-09-r1` | CT34.fixtures | [ADR008 §4](008-batch-04-contract-approvals.md#4-ct34fixtures) |
| `CT14-COVERAGE-2026-11-09-r1` | CT14.coverage placement and gap map | [ADR008 §5](008-batch-04-contract-approvals.md#5-ct14coverage) |
| `CT39-BINDING-2026-11-09-r1` | CT39.binding: no shared bean | [ADR008 §6](008-batch-04-contract-approvals.md#6-ct39binding-and-ct39consumers) |
| `CT34-CI-2026-11-09-r1` | CT34.ci: future job contract | [ADR008 §7](008-batch-04-contract-approvals.md#7-ct34ci) |
| `CT39-CONSUMERS-2026-11-09-r1` | CT39.consumers: empty shared set; local alternatives/gates | [ADR008 §6](008-batch-04-contract-approvals.md#6-ct39binding-and-ct39consumers) |

**10 November 2026 bounded amendment:** CP-030's current revision is
`CT34-FIXTURES-2026-11-10-r2` — **DELEGATED_APPROVED** under the Leader's explicit
PR #231 remediation instruction. [ADR008 §10](008-batch-04-contract-approvals.md#10-f1f2-fixture-amendment--10-november-2026)
amends only r1's concurrent-invocation/output ownership and interrupted-resource
recovery; the r1 row above retains its history. CP-035 r1 consumes that amended
fixture contract and retains its own revision; M1 is downstream budget guidance.
The other five decision revisions, predecessor contracts and separate
implementation/verification obligations remain unchanged.

Batch 3 present-state confirmation: PR #230 merged as
`3eeacc8f5b3244c6d3db403d3e7f476f9caeb554` on 9 November 2026 at 20:31:42 ICT.
The dated preparation-time pending-integration text above remains historical;
its unchanged approvals are integrated documents, not runtime or independent
review evidence. CT08 remains evidence-only at whole-Issue level.

### Batch 5 revisions — 12 November 2026

[ADR010](010-batch-05-contract-approvals.md) is the authoritative record for these
**DELEGATED_APPROVED** revisions (Claude Code under the Leader's explicit Batch 5/6
delegation). They await independent review, required CI and protected integration;
no storage, assignment or widget behavior is implemented by the approvals.

| Revision | Exact slice | Record |
|---|---|---|
| `CT01-LEARNER-NAMES-2026-11-12-r1` | Prerequisite: Auth learner display-name read, Course-only caller | [ADR010 §3.1](010-batch-05-contract-approvals.md#31-ct01learner-names) |
| `CT18-B5-ERRORS-2026-11-12-r1` | Prerequisite: 413/415 for Batch 5 file operations | [ADR010 §3.2](010-batch-05-contract-approvals.md#32-ct18b5-errors) |
| `CT09-AUTHORIZATION-2026-11-12-r1` | CT09.authorization: backend streaming, admission before storage | [ADR010 §4](010-batch-05-contract-approvals.md#4-ct09authorization) |
| `CT09-LIFECYCLE-2026-11-12-r1` | CT09.lifecycle: keys, types, filenames, replacement, orphans | [ADR010 §5](010-batch-05-contract-approvals.md#5-ct09lifecycle) |
| `CT09-CONFIG-2026-11-12-r1` | CT09.config: existing names, validation, upload limit input | [ADR010 §6](010-batch-05-contract-approvals.md#6-ct09config) |
| `CT10-DEADLINE-2026-11-12-r1` | CT10.deadline: exclusive server-time deadline | [ADR010 §8](010-batch-05-contract-approvals.md#8-ct10deadline) |
| `CT10-GRADING-2026-11-12-r1` | CT10.grading: grade, feedback, regrade, no mastery effect | [ADR010 §9](010-batch-05-contract-approvals.md#9-ct10grading) |
| `CT09-INTERFACE-2026-11-12-r1` | CT09.interface: `FileStorage` port and S3 adapter | [ADR010 §7](010-batch-05-contract-approvals.md#7-ct09interface) |
| `CT10-REPLACEMENT-2026-11-12-r1` | CT10.replacement: before deadline, ungraded, Assignment-row lock | [ADR010 §10](010-batch-05-contract-approvals.md#10-ct10replacement) |
| `CT09-REST-2026-11-12-r1` | CT09.rest: file-material REST and frontend multipart/download transport | [ADR010 §11](010-batch-05-contract-approvals.md#11-ct09rest) |
| `CT10-REST-2026-11-12-r1` | CT10.rest: assignment authoring, submission and grading REST | [ADR010 §12](010-batch-05-contract-approvals.md#12-ct10rest) |
| `CT37-WIDGET-2026-11-12-r1` | CT37.widget: shared upload/download widget | [ADR010 §13](010-batch-05-contract-approvals.md#13-ct37widget) |

[ADR010 §16](010-batch-05-contract-approvals.md#16-independent-review-corrections--12-november-2026)
records pre-integration review corrections inside these r1 revisions (strictly
later replacement time, staged error precedence, download/replacement race and two
wording fixes); scopes and IDs are unchanged.

LD-07 (reminder deadline query ownership) is not resolved by CT10.deadline and stays
with CT11.reminders (Batch 7). Submission-download hostile-content residual risk is
`PENDING_LEADER_RISK_ACCEPTANCE` before public exposure.
