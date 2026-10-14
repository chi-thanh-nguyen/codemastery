# Native dependency projection — review and activation runbook

Status: implementation prepared for protected-branch review. **Not activated.**
No runtime credential, repository secret or activation variable was created.
No live Project or native dependency mutation was used for validation.

## Boundary and source of truth

[Project #3](https://github.com/users/chi-thanh-nguyen/projects/3) is private and
user-owned. Native Issue Dependencies are the only dependency authority. The
reconciler reads their direct membership, including closed Issues, and projects
it into exactly two existing Text fields:

| Resource | Pinned identity |
| --- | --- |
| Repository | `chi-thanh-nguyen/codemastery` / `R_kgDOUvTDdg` |
| Project | `PVT_kwHODKWgoM4Bmgtn` |
| Blocked by | `PVTF_lAHODKWgoM4BmgtnzhlrLhw` |
| Blocking | `PVTF_lAHODKWgoM4BmgtnzhlrLq0` |

The code pins the 67 published Issue node IDs, numbers #6–#72 and stable IDs
(11 Epics, 56 children). Unexpected, missing, archived, duplicate or substituted
items stop reconciliation; inventory changes require a reviewed allowlist PR.
It never adds items or creates fields. These identity checks prevent a field
with a recycled name or a different Issue from becoming a write target.

No DG edge, parent/child relationship, execution wave, partial contract approval,
checkpoint, Issue closure or scheduling value is converted into a blocker.
The Leader's reviewed-responsibility-evidence decision remains applicable:
Epic completion does not automatically create child-to-Epic native blockers.
No native relationships, Issue metadata, other fields or views are writable.
The two projected fields remain read-only by convention; a subsequent run repairs
manual drift from native truth, never the reverse.

## Proposed runtime credential — requires separate Leader approval

- **Type:** dedicated personal access token **(classic)** belonging to a user
  with at least Write access to this private Project and read access to the
  public repository's native Issue dependencies.
- **Scope:** `project` only, granting Project read/write. No `repo`, `public_repo`,
  `workflow`, administration or native Issue write scope is requested. Public
  repository information is readable without a repository scope. Actual access
  with this new reduced-scope credential must be verified before activation;
  the existing broader interactive CLI credential is not evidence of that test.
  If the repository/dependencies become private or inaccessible, fail closed and
  request a new permission decision rather than silently broadening scopes.
- **Proposed repository Actions secret:** `CODEMASTERY_PROJECT_SYNC_TOKEN`.
  Its value is supplied only to the reconciliation step as `GH_TOKEN`. Do not
  paste it into chat, PRs, commands, logs or committed files. The existing
  interactive credential must not be copied into this secret implicitly.
- **Standard workflow token:** `contents: read` for checkout only, with credential
  persistence disabled. The reconciliation step uses the dedicated PAT.
- **Expiration/rotation proposal:** finite 30-day lifetime, recorded expiry and
  Leader reminder seven days before expiry; rotate earlier on suspected exposure,
  role changes or revocation. After separate authorization, replace the secret,
  perform a manual dry-run, verify access, then revoke the old token. Expiry or
  lost Project access must fail the run without clearing cells. No automatic
  token provisioning or refresh is implemented.

GitHub documents that [fine-grained PATs cannot currently access user-owned
Projects](https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/managing-your-personal-access-tokens#fine-grained-personal-access-tokens-limitations)
and that [`GITHUB_TOKEN` cannot access Projects](https://docs.github.com/en/issues/planning-and-tracking-with-projects/automating-your-project/automating-projects-using-actions).
The [`project` scope](https://docs.github.com/en/apps/oauth-apps/building-oauth-apps/scopes-for-oauth-apps)
is broader than this script's allowlist: GitHub does not constrain a classic PAT
to one Project or two fields. The code boundary limits intended writes; it is
not a server-enforced credential sandbox. Credential compromise could affect
other Projects the token owner can access. This residual risk requires explicit
Leader acceptance before provisioning. No GitHub App or external service is
included in this authorization.

## Trigger and reconciliation behavior

- Schedule proposed: `17 * * * *` UTC, hourly at minute 17 (also minute 17 in
  Asia/Ho_Chi_Minh). Default-branch workflow only. GitHub may delay/drop scheduled
  runs; hourly is an eventual-reconciliation target, not a delivery guarantee.
- `workflow_dispatch` has an `apply` Boolean that defaults to **false**. Manual
  dry-run can validate future credentials without enabling scheduled writes.
- **Scheduled jobs and every apply require** repository variable
  `CODEMASTERY_DEPENDENCY_SYNC_ENABLED` to equal the literal `true`. It is not
  created/set by this PR. Manual apply also requires `apply: true`; an absent or
  false activation variable blocks it before any API write.
- Only this exact repository and `refs/heads/main` may execute the job. There is
  no pull-request, `pull_request_target`, dependency-webhook or dispatch-bridge
  trigger. Merely opening this PR cannot execute this credential-bearing job.
- A fixed Actions concurrency group serializes schedule/manual runs across
  runners (`cancel-in-progress: false`). GitHub may replace older pending runs;
  the next full reconciliation recovers without replaying stale operations.
- The standard-library Python program uses GitHub CLI on `ubuntu-24.04`, a
  commit-pinned checkout action, and no third-party Python dependencies. Offline
  tests run before the token-bearing step. A host-local nonblocking file lock
  also prevents overlapping local processes.
- All connection pages must succeed with consistent counts/cursors. Missing
  nodes, partial GraphQL errors, authorization failures, conflicting identities,
  inconsistent reciprocal membership or a detected cycle stop the run. Cycle
  checks cover the target inventory and observed adjacent edges; they cannot
  prove properties of inaccessible, untraversed external graphs.
- Same-repository references are deduplicated and numerically sorted; external
  references are repository-qualified. Closed/reopened Issues remain listed
  while native membership exists. No transitive links or truncation are added.
- A successful, fully paginated empty connection permits clearing a stale value.
  Failed/incomplete retrieval never means empty. Initial full reads precede all
  writes. Each changed cell rechecks Project identity/inventory and native truth.
- Bounded read retries (three attempts, at most 60 seconds per rate-limit wait),
  mutation timeout readback before retry, three convergence attempts per cell,
  and a 30-minute job timeout limit retries. Large rate-limit cooldowns stop for
  a later run. Permanent authorization/schema errors are not retried as writes.
- Every changed cell and the final full inventory are independently re-read.
  GraphQL provides no cross-service transaction or compare-and-swap here. Human
  edits/native changes can race; final drift fails the run and the next approved
  run recomputes truth. This is eventual consistency, not atomic synchronization.

[Supported Actions events](https://docs.github.com/en/actions/reference/workflows-and-actions/events-that-trigger-workflows)
and the [native dependency webhook](https://docs.github.com/en/webhooks/webhook-events-and-payloads#issue_dependencies)
are separate mechanisms. No unsupported `issue_dependencies` Actions trigger,
receiver, App, webhook secret or extra infrastructure is introduced.

## Security and execution evidence

Only aggregate counts/status go to public Actions logs. No full private Project
snapshots, native reference lists, raw HTTP errors, token or unrelated field
values are printed or uploaded. The fixed `github.com` CLI call receives JSON
through stdin, never interpolates Issue data into shell code, and disables CLI
debug output. The token is absent from checkout and the offline test step.
All four files are under the existing Leader-owned `.github/workflows/` rule.
CODEOWNERS, protected-branch settings and existing CI remain unchanged.

The new private output directory (mode 0700) contains a summary and, on changed
cells, a mode-0600 journal with old/expected values and readback results. It is
outside the repository. No artifact upload is configured: GitHub-hosted runner
storage is ephemeral and private old-value evidence is lost after job cleanup.
A crash may leave an intent without readback; a later run reads actual state.
If durable private old-value retention is required, its destination and access
policy need separate authorization before activation. Aggregate run logs remain
available under existing Actions retention; they cannot reconstruct old values.

## Validation

From the repository root, with no token or network required:

```bash
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s .github/workflows -p 'test_sync_native_dependencies.py' -v
```

The suite uses only synthetic in-memory relationships. It exercises pagination,
count/cursor failures, both directions, identity/field allowlists, closed/reopened
membership, normalization, cycles, no-clear-on-failure, stale-value clearing,
uncertain writes, concurrent changes, bounded convergence, rate limits,
activation guards, local locking and repeat-run idempotency. No production test
dependency is created.

A separately authorized non-mutating check uses existing approved read access:

```bash
PYTHONDONTWRITEBYTECODE=1 python3 .github/workflows/sync_native_dependencies.py --output-dir /private/tmp/codemastery-dependency-check-UNIQUE
```

Dry-run never sends mutations. Exit 0 means all 134 cells match; exit 2 reports
drift, and exit 1 reports retrieval/validation/convergence failure. Successful
read-only validation does not test the proposed new PAT, live field writes,
Actions triggers or permanent operation. The 67 live Issues currently have zero
native relationships; keep all nonempty/remove/update scenarios offline until
real, independently authorized native changes exist.

## Deployment after explicit Leader authorization — not performed by this PR

1. Complete normal review, required CODEOWNER approval and required checks.
   Obtain separate Leader approval before merging. Do not bypass protection.
2. Separately authorize credential provisioning, finite expiry/rotation and
   the residual scope/evidence-retention risks above. The Leader securely creates
   the proposed classic PAT and repository Actions secret. Do not set the
   activation variable yet. None of these operations is done during PR preparation.
3. After separately authorized merge, use Actions → Native dependency projection
   → Run workflow → branch `main`, `apply: false`. Verify the 67/134 counts,
   zero writes, intended access, secret-safe logs and offline-test success.
   Resolve any failure without broadening permissions implicitly.
4. Obtain explicit activation/live-write authorization. Only then set Settings
   → Secrets and variables → Actions → Variables →
   `CODEMASTERY_DEPENDENCY_SYNC_ENABLED=true`. This enables scheduled writes;
   coordinate the activation time. Run an approved manual `apply: true`, then
   inspect a real scheduled run. At the present empty state, expect zero writes;
   do not fabricate dependencies or Project drift to force a production write.
5. Require two successful, idempotent runs and the actual schedule execution.
   Validate real nonempty/add/remove behavior only after a separately authorized
   real relationship change. Until then, report live-write coverage as untested.
   Follow up failed/missed runs and expiry using existing Actions/Leader review;
   no new alerting service is implemented.

## Stop, recovery and rollback after authorization

Set the activation variable to `false` (or disable this workflow) and cancel any
in-flight run; disabling does not undo earlier writes. A job may already have
passed its activation check, so confirm it has stopped before incident work.
Revoke a compromised PAT immediately. None of these settings changes is performed
in PR preparation.

Re-run offline tests and an authorized manual dry-run. Resume only after approval;
reconciliation rebuilds both fields from current native truth and skips matching
values. It never replays an old journal blindly. If projection code is defective,
prepare a corrective/revert PR through the same protected review process. Once
approved, re-derive values from native dependencies; historic Project values may
be stale and are not a new source of truth. Native relationships and Issue
metadata require no rollback because this reconciler has no writer for them.
Any restoration of older values or additional remote action requires explicit
authorization. Keep the fields, views, Project and native links intact.

AI assistance: Codex assisted preparation of this automation code, offline tests
and runbook. GitHub API/Actions/CLI are the services/tools used; no application
runtime or grading disclosure policy is changed.
