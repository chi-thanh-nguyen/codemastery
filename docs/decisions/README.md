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

## Architecture decision records

| ADR | Subject | Status |
|---|---|---|
| [ADR001](001-modular-monolith-module-boundaries.md) | Modular-monolith module boundaries (§§1–10); CT01 (§11) and CT03 (§12) contract records; CT06 boundary proposal (§13) | §§1–10 approved architecture; §11/§12 `LEADER_APPROVED` (ADR004); §13 `PROPOSED_NOT_EFFECTIVE` |
| [ADR002](002-interim-delegated-contracts.md) | Bounded Interim delegated contracts and full source disposition inventory; FE07 and SD01/SD02 records | Slices `DELEGATED_APPROVED` (CT20.I Leader-authorized); FE07 and CT15-SD `LEADER_APPROVED` (ADR004); other dispositions as listed |
| [ADR003](003-postgresql-alpine-su-exec-image.md) | PostgreSQL image hardening (Alpine with su-exec) | `LEADER_APPROVED` (2 November 2026); residual risks `PENDING_LEADER_RISK_ACCEPTANCE` |
| [ADR004](004-leader-approval-of-delegated-records.md) | Leader approval of the earlier delegated records; label rename | `LEADER_APPROVED` (8 November 2026) |

## Contract decision records by revision

| Revision | Subject | Record | Status | Introduced by |
|---|---|---|---|---|
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
| `LEADER-APPROVED-2026-11-08-r1` | Effectiveness of the records above that carried the retired label | [ADR004](004-leader-approval-of-delegated-records.md) | `LEADER_APPROVED` | this change |

## Proposals and open Leader decisions recorded in the repository

| Record | Status |
|---|---|
| [CT06 production and enablement proposal](../adaptive-learning.md#19-ct06-reconciliation-and-completion-proposal) (with [ADR001 §13](001-modular-monolith-module-boundaries.md#13-ct06-settings-and-coverage-boundary-reconciliation--proposal) and the [data-model note](../data-model.md#ct06-production-and-enablement-proposal--no-physical-change)) | `PROPOSED_NOT_EFFECTIVE`; Leader decisions listed in its §19.5 |
| [CT14 coverage and isolation proposal](../testing.md#ct14-coverage-and-isolation-reconciliation--proposal) | `PROPOSED_NOT_EFFECTIVE`, partly overtaken by CT14-FINAL |
| [CT16 proposal, 18 October](../setup.md#ct16-frontend-contract-proposal--18-october-2026-historical-record) and [CT17 proposal, 18 October](../deployment.md#ct17-completion-proposal--18-october-2026-historical-record) | Historical records, superseded by the approvals above |
| DP01 security and image decisions in [deployment](../deployment.md) (sections "Outstanding Leader decisions", "Remaining decisions", "Admission and outstanding decisions") | Open Leader decisions F1, F4, F5, F6, F10, F11 and image admissions |
