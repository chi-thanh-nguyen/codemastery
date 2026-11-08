# ADR004: Leader Approval of Earlier Delegated Technical Records

## Status and authority

`LEADER-APPROVED-2026-11-08-r1`. **LEADER_APPROVED**, recorded on 8 November 2026
(Asia/Ho_Chi_Minh).

On that date the Project Leader @chi-thanh-nguyen decided, in a Claude Code working
session, that:

- the delegated technical approval records listed below are effective as merged;
- their status label `APPROVED_PENDING_PROTECTED_INTEGRATION` is renamed in place to
  [`LEADER_APPROVED`](README.md#approval-status-labels) throughout `docs/`.

The Leader confirmed this choice explicitly: "Rename = my LD-01 decision". The
decision is the Leader's. This record was drafted with Claude Code (Anthropic) at
the Leader's request.

This record resolves:

- planning decision **LD-01**, from the 8 November 2026 pre-approval verification;
- checkpoint **GOV.HC-U20**;
- handoff conflict **HC-U20**, "Effectiveness of
  APPROVED_PENDING_PROTECTED_INTEGRATION records", OPEN since 6 November 2026.

These IDs belong to local planning records outside this repository. `HC-U20` is
unrelated to the Development Dependency Graph candidate also numbered U20.

## Why this record exists

The records below were merged into `main` between 21 and 30 October 2026 with
`APPROVED_PENDING_PROTECTED_INTEGRATION`. That label said the technical approval
stands but takes effect only after integration through the protected review path:
eligible CODEOWNER review, last-push approval, resolved threads and required checks.

Several records note that the Leader is the sole CODEOWNER of the touched files, so a
Leader-authored change cannot satisfy that review itself
([ADR001 §11.7](001-modular-monolith-module-boundaries.md#117-approval-disposition-and-retained-gates)).
According to the handoff record kept outside the repository, the decision PRs passed
`main-quality-gates` but merged through the merge-authority bypass. The label was
never updated, and whether the records were effective remained open.

## Decision

1. The records in the table are **approved and effective under the Project Leader's
   authority**, with their content exactly as merged.
2. Their label is renamed in place from `APPROVED_PENDING_PROTECTED_INTEGRATION` to
   `LEADER_APPROVED`:
   - 38 occurrences in eight files: ADR001 (11), ADR002 (3), `setup.md` (4),
     `testing.md` (5), `deployment.md` (9), `data-model.md` (2),
     `repository-structure.md` (1) and `api/openapi.yaml` (3);
   - in `openapi.yaml`, the two `effectiveOnMain` flags change from `false` to `true`,
     and each extension gains an `effectivenessRecord` reference to this ADR.
3. Each record keeps its own authority line, for example
   "DELEGATED_TECHNICAL_APPROVAL; LEADER_APPROVED". `LEADER_APPROVED` states the
   current status; the authority line still says that a delegate made the technical
   selection under an explicit Leader instruction.
4. No technical clause is changed, added or reinterpreted.
5. Headings, verdict sentences and explanatory prose are **not** rewritten. They
   include phrases such as "pending protected integration", "new approvals await
   protected integration" and "this is neither effective main integration". GitHub
   Issues cite their anchors, and the prose records the state at the time of writing.
   This ADR governs where such text differs.

## Records covered

| Revision | Location | Introduced by |
|---|---|---|
| `CT01-2026-10-21-r1` | [ADR001 §11](001-modular-monolith-module-boundaries.md#11-ct01-approved-technical-contract--protected-integration-pending); `docs/data-model.md`; `docs/api/openapi.yaml` (`x-ct01-completion-proposal`) | PR #91 |
| `CT03-2026-10-21-r1` | [ADR001 §12](001-modular-monolith-module-boundaries.md#12-ct03-access-and-consumer-contract-reconciliation); `docs/data-model.md`; `docs/api/openapi.yaml` (`x-ct03-access-proposal`) | PR #92 |
| `CT16-2026-10-24-r1`, `CT16-INTERIM-COVERAGE-2026-10-24-r1`, `CT16-FINAL-2026-10-25-r1`, `CT16-PROFILE-2026-10-25-r1` | `docs/setup.md`; ADR002 CT16.minimum correction | PR #102 |
| `CT17-2026-10-24-r1`, `CT17-INTERIM-2026-10-24-r2`, `CT17-INTERIM-BINDINGS-2026-10-25-r1`, `CT17-FINAL-2026-10-25-r1` | `docs/deployment.md` | PR #103 |
| `CT17-P6-PRIVILEGES-2026-10-28-r1` | `docs/deployment.md` | PR #110 |
| `CT14-INTERIM-FE03-FE07-2026-10-24-r1`, `CT14-FINAL-2026-10-25-r1`, `CT14-PROFILE-AUTH-2026-10-25-r1` | `docs/testing.md`; `docs/repository-structure.md` | PR #106 |
| `FE07-2026-10-27-r1` (CT22.membership) | [ADR002 FE07 section](002-interim-delegated-contracts.md#fe07-full-implementation--delegated-approval-27-october-2026); `docs/api/openapi.yaml` (`x-fe07-delegated-approval`) | PR #101 |
| `CT15-SD-2026-10-30-r3` | [ADR002 SD01/SD02 section](002-interim-delegated-contracts.md#sd01sd02-sample-data-completion--delegated-approval-30-october-2026) | PR #116 |

The status-table row in `docs/deployment.md` ("Design and approval status"), which
quotes the label for the CT17 records, is renamed with them.

## Not covered

- **Anything the records themselves exclude or defer.** Examples: CT17 concrete
  bindings (`PENDING_LEADER_INPUT`), the CT16 deferred Final items, and the retained
  CT14 and CT17 Final inventories classified as pending, deferred or Leader-reserved.
- **Records with another label.** The ADR002 slices (`DELEGATED_APPROVED`),
  `CT01.identity` (Leader approval of 18 October 2026), ADR003 and the records of
  8 November 2026 keep their own labels.
- **Proposals that were never approved.** These are:
  - the CT06 production/enablement proposal (`PROPOSED_NOT_EFFECTIVE`; ADR001 §13 and
    `docs/adaptive-learning.md` §19);
  - the CT14 coverage proposal;
  - the 18 October CT16 and CT17 proposal records;
  - every `PROPOSED_LEADER_RESERVED`, `DEFERRED` or `BLOCKED_BY_MISSING_EVIDENCE`
    disposition in ADR002.
- **Risk acceptances.** This record accepts no residual risk, including ADR003's
  `PENDING_LEADER_RISK_ACCEPTANCE` items.
- **Implementation authorization.** `LEADER_APPROVED` never authorizes
  implementation, integration verification, deployment or release.
- **Future records.** New approvals need their own record. Their review route for
  sole-Leader CODEOWNER paths is still a Leader decision (planning item LD-09).
- **Text outside `docs/`.** The comment in `infrastructure/vm/deploy.sh` (line 6)
  still quotes the old label. The rename was limited to `docs/`.

## Consequences

- Contracts and implementations that rely on these records may treat them as
  effective approvals of their stated scope.
- Checkpoints that consume them no longer wait for an effectiveness ruling.
- The Leader remains the approval authority for every pending checkpoint.

## History

- **8 November 2026, earlier revision (not merged).** PR #227, commit `cf44876`,
  proposed `LEADER-RECOGNITION-2026-11-08-r1`. That revision added this ADR as
  `004-leader-recognition-of-delegated-approvals.md` with a new label,
  `LEADER_RECOGNIZED_EFFECTIVE`, and left every original occurrence untouched.
- **8 November 2026, this revision.** The Leader chose the in-place rename to
  `LEADER_APPROVED` instead. This revision replaces the earlier one in the same
  pull request, and `LEADER_RECOGNIZED_EFFECTIVE` is not used.
