# ADR015: Release Pipeline — Attested Candidates, Admission-Gated Promotion and Offline VM Packages

## 1. Authority, provenance and scope

**DELEGATED_TECHNICAL_APPROVAL; DELEGATED_APPROVED**, 23 November 2026
(Asia/Ho_Chi_Minh). The Project Leader and repository Owner @chi-thanh-nguyen gave
the explicit instruction **“CodeMastery — Phase 4: Full Release Pipeline
Implementation with Delegated Technical Decisions”**. It authorizes the full Phase 4
implementation (workflow, scripts, tests, documentation), small cross-Issue
dependencies, bounded repository additions for Phase 4, and delegates the selection
of every Phase 4 technical decision that is missing or unclear, including
vulnerability scanning, SBOM, build provenance, artifact integrity, signing or
attestation, admission verification, artifact storage and retention, and the
package format. It asks that each choice be recorded with its alternatives.

Claude Code (Anthropic) made every selection below **under that delegation**. The
Leader did not review any individual selection. The instruction expressly reserves,
and this record does not claim: acceptance of any residual vulnerability; security
admission of any actual artifact; production secrets; Azure resources or spending;
any operation on the existing VM; changes to rulesets or protections; approving or
merging the pull request; and any statement that an independent review happened.

These revisions are acted on in branch `feature/phase4-release-pipeline`
([PR #267](https://github.com/chi-thanh-nguyen/codemastery/pull/267)). They become
effective on `main` only through that PR's compliant integration. Technical
approval is not implementation completeness, review, integration, qualification of
a real candidate, admission, publication or deployment; §9 keeps them apart.

Known AI use: Claude Code (Anthropic) analysed the repository, selected the design,
wrote the implementation, tests and this record. No AI service is added to the
application or to the release pipeline.

## 2. Baseline and consumed contracts

Starting `main`: `257a9152e16c8ba4fae467242ccad964e1d899b4`, re-read on
22–23 November 2026 (four workflows; `main-quality-gates` requires ten contexts:
the eight CI jobs, `E2E` and `CodeQL`; repository public). The pipeline consumes,
without changing them:

| Contract | What the pipeline must preserve |
| --- | --- |
| F3 release tagging and admission ([deployment](../deployment.md#f3--release-tagging-and-admission-workflow)) | Candidate → qualification → security admission → release promotion; release names only for admitted image IDs |
| CT17 B1 / P8 ([deployment](../deployment.md#p8--release-transfer-persistence-and-restart-behavior)) | linux/amd64 only; build off the VM; transfer channel and custody remain P8 inputs |
| ADR009 §3, §9.3 | Off-host build, no on-VM downloads, immutable checkout of the full source SHA, P9 update preconditions, `https://codemastery.app` origin (L2) |
| `infrastructure/vm/deploy.sh` and the VM override | `--archive`/`--archive-sha256`, `--expect-image` for every service (containerd OCI manifest digest or graph-driver config digest), `codemastery.invalid/*:release` names, `guard_update()` |
| DP01 F1 ([deployment](../deployment.md#minio-client-licence-and-corresponding-source)) | The modified AGPL `mc` travels with its exact Corresponding Source |
| ADR003 amendment | Byte-clean single-layer PostgreSQL image |

`deploy.sh`, the Compose files, every Dockerfile and every existing CI job, check
name and assertion are unchanged. One existing test script gains an optional input
(§4 D5).

## 3. Problem

No pipeline produced release artifacts. The one recorded package
(`061102e3`, [ADR009 §2.3](009-ct17-final-runtime-recovery.md#23-exact-local-historical-release-identities))
was built by hand on a workstation; its admission named build-host image IDs that
`deploy.sh` cannot use, its integrity rested on a local `SHA256SUMS`, and nothing
tied archive, admission and source together verifiably. F6 left signing and
provenance `PENDING_LEADER_APPROVAL`.

## 4. Decisions

### D1 — One workflow, two manual stages, and a pull-request rehearsal

`.github/workflows/release.yml`, workflow **Release**:

- `stage=candidate` (dispatch from `main`): build, qualify, scan, package, attest,
  store as a draft release.
- `stage=promote` (dispatch from `main`): verify a committed admission record and the
  candidate's attestation, promote, verify, attest, store as a draft release.
- `pull_request` (path-filtered to release inputs): the same scripts, unprivileged,
  with a synthetic `REHEARSAL-` admission; no attestation, upload or release.

Rejected: a tag-push trigger (tags are unprotected and can point at unmerged
commits); automatic promotion (admission is a Leader act); separate jobs passing
the archive through workflow artifacts (they are downloadable by any signed-in user
of a public repository, see D9). Each stage is one job, so no artifact leaves the
runner except through D9. Dispatched stages run one at a time and a running stage is
never cancelled; GitHub keeps one pending dispatch, which a newer dispatch replaces.

### D2 — A valid source revision

*Amended by [A2](#11-amendment-a2--latest-authenticated-ci-evaluation-24-november-2026)
on 24 November 2026. The rule it replaces (every run of a required check must have
succeeded) is quoted in A2.2; the rest of D2 is unchanged.*

A full 40-character SHA that is an ancestor of `origin/main` (default: the dispatched
`main` commit) for which the **latest valid evaluation** of **every** required status
check of the live `main` rules (`GET /repos/{owner}/{repo}/rules/branches/main`) has
completed and succeeded, with its provenance authenticated against GitHub's own records
and the evidence recorded in the candidate manifest (A2.4–A2.6). The list is read at
run time, never hard-coded, so later ruleset changes apply automatically; a required
check that names no app, or whose app this pipeline cannot authenticate, refuses the
revision until D2 is extended.

- A check of **GitHub Actions** (app 15368) counts only through GitHub's job record:
  the newest attempt of the newest trusted run (push or `workflow_dispatch` on `main`,
  in this repository, of a workflow file of the revision other than `release.yml`) of
  the single workflow that has a job of that name decides, and the revision's check run
  with that job's ID must match it. Any workflow with `checks: write` can create check runs under this app's
  identity; such a run, without a matching job, is ignored and recorded, never evidence.
- A check of a **GitHub-operated app** whose check runs no workflow, user or other app
  can create (code scanning, app 57789) counts through its newest check run.
- An earlier failure no longer refuses the revision once a newer valid evaluation
  passed; a newer evaluation that failed, was cancelled or skipped, or has not finished
  always refuses it, whatever passed before. Results of different attempts or runs are
  never combined. Superseded results stay in the evidence.

GitHub reports the `CodeQL` code-scanning check (app 57789) only on pull requests; on
`main` pushes only the `Analyze (…)` jobs appear (observed on `257a915`). So a context
of such an app with **no run of that app at all** on the revision may be satisfied on
the head of the single merged pull request of this repository whose merge commit is the
revision, where the ruleset enforced it at merge time, **provided the head's tree
equals the revision's tree** (squash-only merges of up-to-date branches make them equal;
observed for PRs #252, #260 and #261); a failed or pending run of that app on the
revision is never overridden, without such a pull request there is no fallback, and a
GitHub Actions check never falls back (a pull-request run tests a merge commit, not the
revision). Checked read-only against the live API on 23 November 2026 under the earlier
rule: all ten contexts pass for `257a915` (nine on the revision, `CodeQL` on PR #260's
head `cd9d43d`); for `84d10a3` under A2 see A2.9. The workflow itself always runs from
`main`. Release tooling comes from the dispatched `main` checkout; images, `deploy.sh`,
Compose files and the qualification suites come from a worktree of the source revision,
as the VM's immutable checkout will.

### D3 — Provenance and signing (resolves the F6 technical selection)

**GitHub artifact attestations** (`actions/attest-build-provenance`, pinned by commit
to v4.2.2): one SLSA build-provenance attestation per package whose subjects are
**every file in the package's `SHA256SUMS`**. The repository is public, so the
attestation is signed with a short-lived Sigstore certificate bound to the
workflow's GitHub OIDC identity (`release.yml` at `refs/heads/main`) and logged in
the public-good Rekor transparency log; GitHub stores it with the repository. The
Sigstore bundle is shipped beside each package (`*.sigstore.json`) for offline
verification.

Verification policy (promotion and D12): the certificate identity must equal
`https://github.com/<repo>/.github/workflows/release.yml@refs/heads/main` exactly
(`--cert-identity`, rather than the `--signer-workflow` pattern), the source ref must
be `refs/heads/main`, the source digest must equal the `tooling.sha` the attested
manifest records, and self-hosted runners are refused. Exercised with `gh` 2.102.0
against a real public attestation (online, and offline with `--bundle` and
`--custom-trusted-root`): a different ref or source digest is refused.

| Option | Assessment |
| --- | --- |
| **GitHub artifact attestations (selected)** | Keyless: no signing secret to create, store or rotate; identity is the protected workflow on `main`; verifiable with `gh attestation verify` online or offline (`--bundle`, `--custom-trusted-root`); works on public repositories on every plan; covers files, so it fits an offline archive |
| cosign with a managed key | A new long-lived secret and custody problem; nothing gained for this consumer |
| cosign keyless on registry images | Needs a registry push (D9 rejects it) and verifies images, not the offline archive `deploy.sh` consumes |
| BuildKit provenance inside images | Unsigned, so not authenticity evidence; kept only because `docker build` adds it and `deploy.sh` already accepts it |

Not selected: changing `deploy.sh` to require attestations. The VM may have no route
to GitHub or Sigstore, and `deploy.sh` already refuses any archive whose SHA-256 or
image identities differ from the operator's inputs. Those inputs come from the
attested `release.json`, which the operator verifies **before transfer** with
`infrastructure/release/verify-release.sh` (D12). Byte-for-byte reproducibility is
**not** claimed; identity comes from the attested archive (D4).

### D4 — Candidate artifacts

The five images the VM override requires, built for linux/amd64 on a GitHub-hosted
`ubuntu-24.04` runner with the **containerd image store** (so OCI identities are
native) from the source revision's own Dockerfiles and digest pins, under
`codemastery-candidate/<service>:<12-hex>`:

| Service | Source | Notes |
| --- | --- | --- |
| postgres | `infrastructure/postgres` | ADR003 byte-clean image |
| minio | `infrastructure/vm/Dockerfile.minio-server` | **Selected server artifact**: the minimal server copied from the digest-pinned Chainguard build. It is the only server build the repository defines, carries no `mc` (the full Chainguard image ships `mc` with the F1 findings), is natively qualified in CI, and is the server of the 4 November preview admission. Production admission remains the Leader's |
| minio-init | `infrastructure/minio` | The project-built `mc`; its Corresponding Source (`mc-source-archive` target) must equal the suite's `INIT_MC_SOURCE_SHA256` **and** the hash named by the image's own `/usr/share/doc/mc/SOURCE` |
| backend | `backend` | |
| frontend | `frontend`, target `release` | `PUBLIC_ORIGIN` = workflow input, default `https://codemastery.app` (ADR009 L2); recorded in the manifest and bound by the admission |

No base image is rebuilt differently from CI, nothing is pulled at deployment time,
and no development image (local frontend target, Golithus server) can enter: the
archive holds exactly these five references, which `deploy.sh` enforces.

### D5 — Qualification of the exact candidates

After saving the archive, the job deletes the candidate images and reloads them from
it, checks that each loaded image ID equals the archive identity, and runs, on those
exact bytes:

| ID | Check |
| --- | --- |
| Q01 | The source revision's `test-minio-init.sh` with the exact initializer against the exact minimal server |
| Q02 | `test-java-truststore.sh` on the exact backend |
| Q03 | `test-runtime-real-backend-privileges.sh` with the exact backend (Flyway as migrator, privilege matrix, Auth and consumers, upgrade path) |
| Q04 | `test-postgres-image-layer-equivalence.sh` on the exact PostgreSQL image via a new optional `POSTGRES_FLAT_IMAGE` input (unset: unchanged behavior, so the required CI step is unaffected) |
| Q05 | Frontend UID 101, root-owned read-only Nginx configuration, ingress and healthcheck rendered for the input origin; no setuid/setgid file in backend, frontend or MinIO server |
| Smoke | `deploy.sh` `preflight`, three refused `load`s (wrong SHA-256, swapped identities, a missing identity), a real `load` and `provision` (PostgreSQL, MinIO, initializer under the release model), then three refusals by `guard_update()` over the created state |

The smoke needs release names. In the candidate stage it uses a release-named copy
that exists only inside the disposable runner and is deleted there: a
**pre-admission compatibility rehearsal, not a promotion**. Nothing with a release
name leaves the runner, so F3's rule that only admitted images are published under
release names holds. The smoke refuses to run anywhere but a fresh GitHub-hosted
runner. `deploy.sh deploy` is not run: the release frontend's healthcheck requires a
publicly trusted certificate for the origin. Q03 covers the exact backend instead.

Source qualification (the ten required checks, D2) is recorded in the manifest
next to these artifact checks; the Docker build skips tests, so it is not a
substitute for them.

### D6 — Vulnerability scanning and SBOM

Trivy **0.76.2**, the official image pinned by index digest, scanning a single-image
`docker save --platform linux/amd64` archive of each exact image, `--scanners vuln`,
in default and `--detection-priority comprehensive` modes, nothing ignored; one
vulnerability database per stage, its timestamps recorded. Trivy also writes an
**SPDX JSON SBOM** per image. Trivy was already the project's review scanner, so no
new tool family is added; govulncheck, used in earlier manual reviews, needs a Go
toolchain per binary and is not included (Trivy reads Go module versions).

The pipeline **records** findings and never judges them: no severity threshold
passes or fails a candidate, because accepting risk is reserved to the Leader.
Instead every finding must be explicitly accepted in the admission record (D7), and
promotion rescans with a fresh database and **fails on any finding the admission does
not cover**, whatever its severity. A rescan row is covered only when its
`{service, id, package}` is accepted **and** the admitted candidate reported the same
row (same installed version) at the same or a higher severity: a new advisory, a newly
matched installed version, or a severity raised since the candidate scan (including any
reclassification of `UNKNOWN`) makes the candidate unpromotable: an admission accepts
exactly its candidate's findings, so the remedy is **a new candidate and a new admission
for it**. Each manifest also records the scan coverage per image (detected operating
system, end-of-support flag, scanned targets), because an operating system the scanner
does not detect yields fewer findings without failing the scan; the job summary shows it
to the Leader. Promotion **refuses a rescan that covers less** than the candidate scan:
the candidate's operating system no longer (or differently) detected, newly reported as
end of support, or any candidate target not scanned. Targets are compared by content
(the OS result by its type, package results by their path in the image), not by the
scanned archive's name.

### D7 — The admission record

> **Superseded by [Amendment A1](#10-amendment-a1--authenticated-admission-without-a-pull-request-per-candidate-23-november-2026)
> (23 November 2026).** Kept as the historical decision; no admission was ever made under it.

A Leader decision committed through the protected review path at
`docs/decisions/release-admissions/<record_id>.json` (covered by the existing
`/docs/decisions/` CODEOWNERS rule). `decided_by` is only text that anyone with write
access can type, so the promote job also requires **GitHub's merge record**: the
commit that last changed the record on `main` must be the squash merge of a pull
request into `main` that changed **only that record**, whose head held the same blob,
and that the `decided_by` account **merged** (`merged_by`), an authenticated act on
exactly that content. Under the current rules only repository administrators can merge
into `main`, so this also holds if those rules later loosen. Schema `codemastery.release-admission/v1`, no
unknown fields:

| Field | Rule |
| --- | --- |
| `record_id` | `^[A-Z0-9][A-Z0-9-]{7,79}$`, equal to the file name; `REHEARSAL-` is refused outside rehearsals |
| `decision` | Only `ADMITTED` permits promotion (`PENDING`, `REJECTED` and anything else are refused) |
| `decided_by`, `decided_on`, `scope` | A code owner of `/docs/decisions/`; ISO date; non-empty scope statement |
| `source_revision`, `public_origin` | Equal to the candidate's |
| `candidate` | Exactly `release_tag` (the candidate draft), `manifest_sha256` (of `release-candidate.json`) and `archive_sha256` |
| `images` | Exactly the five services, each with its `containerd` and `graphdriver` identity, equal to the candidate's |
| `accepted_findings` | `{service, id, package}` entries, each once; exactly the candidate's findings (none missing, none the candidate did not report) |
| `release.version` | `v<major>.<minor>.<patch>[-pre]`, the release tag |
| `not_granted` | Must include `deployment`, `public-exposure` and `existing-vm-update` |

The promote job reads it only from the checked-out `main`, requires it to be tracked
and unmodified, verifies the merge record above (`release.py admission-provenance`), and
records the commit, pull request and merger in `release.json`. `release.py admission-template` prints a
`PENDING` template from a candidate manifest. Rejected: a GitHub Environment
approval (an environment that does not exist is created unprotected, so a typo fails
open; the approval is a click, not a durable record of exact identities; it needs a
settings change); a workflow input (self-declared); a key-signed file (key custody).

### D8 — Promotion

(Admission steps amended by A1: the admission is made, attested and verified inside the
promote run; there is no merge-record check.)

Download the one workflow-created candidate draft named by the record; verify
`SHA256SUMS` and the manifest's file map; verify the candidate manifest, archive and
`mc` source attestations (online and with the shipped bundle; D3 policy); verify the
admission and its merge record (D7);
run `deploy.sh`'s own `verify_archive` on the candidate archive for both stores;
`docker load`, check every identity, `docker tag` to `codemastery.invalid/*:release`,
check the identity is unchanged, `docker save --platform linux/amd64`; recompute all
ten identities from the release archive and require equality with the admission;
`verify_archive` again for both stores; rescan (D6); run the stack smoke (D5) on the
release archive itself; write `release.json`; attest; store as a draft.

### D9 — Storage, retention and publication

Both packages are stored as **draft GitHub releases**, visible only to users with
write access. Candidate drafts are tagged `candidate-<12-hex>-<run>-<attempt>`;
release drafts `v<version>`. The workflow refuses an existing tag or release of the
same name, reads every uploaded asset back (name, size, GitHub SHA-256 digest), and
deletes nothing except its own partial draft after a failed creation. A rerun builds
a new candidate under a new name; it can never replace one. Drafts do not expire, so
earlier packages remain available for recovery. The pipeline never publishes a
release, creates a git tag, pushes an image or uploads a workflow artifact.

| Option | Assessment |
| --- | --- |
| **Draft releases (selected)** | Not public; no storage charge or expiry; up to 2 GiB per asset |
| Workflow artifacts | Downloadable by any signed-in user of a public repository; 90-day maximum retention |
| GHCR | Public by default for public repositories or quota-limited when private; images rather than the offline archive; package-visibility settings outside this task |

A draft's assets can be changed by anyone with write access; the attestation detects
any change, and verification fails closed. **Publication** (un-drafting, creating the
tag, making binaries public) is a Leader act and needs, first, the open F1/F5
licensing determination: a public archive conveys the AGPL `mc` (whose Corresponding
Source is bundled) and the AGPL MinIO server. Enabling GitHub immutable releases
before any publication is recommended (a repository setting, reserved to the Leader).

Attestations are public: Rekor and GitHub record subject digests, file names,
repository, workflow, commit and run. No secret, credential, configuration value,
database content or raw test log is in any package; scanner reports are package
inventories and advisory rows only.

### D10 — Package format

Candidate draft: `codemastery-candidate-<12-hex>-linux-amd64.tar`, `mc-source.tar.gz`,
per service `<s>.trivy.json`, `<s>.trivy-comprehensive.json`, `<s>.spdx.json`;
`findings.json`, `qualification.json`, `migrations.json`, `release-candidate.json`,
`SHA256SUMS`; plus `release-candidate.sigstore.json`.

Release draft: `codemastery-release-<version>-linux-amd64.tar`, `mc-source.tar.gz`,
`expected-images.containerd.txt`, `expected-images.graphdriver.txt` (ready
`--expect-image` values), `<s>.spdx.json`, `<s>.trivy-rescan.json`,
`<s>.trivy-rescan-comprehensive.json`, `rescan-findings.json`, `admission.json`,
`release-candidate.json`, `release.json`, `SHA256SUMS`; plus `release.sigstore.json`.

Each manifest lists the SHA-256 of every other package file, so verifying the
manifest's attestation authenticates the whole package. `release.json` carries
version, source revision and tree, admission (record, decider, scope, file hash,
commit), candidate chain, both identities per image, archive hash and size, scanner
versions and database dates, verification summary, compatibility data (D11) and
status lines that keep candidate, admission, promotion, publication and deployment
apart.

### D11 — Compatibility data for a future VM update (Phase 5 handoff)

The manifests record the SHA-256 of `deploy.sh`, the base Compose file and the VM
override at the source revision (the operator must use that checkout), the Flyway
migration inventory (version, file, SHA-256), and the `update_requirements`: the four
`deploy.sh` P9 attestations an existing host needs and what the release does **not**
verify (the target's deployed revision, identities, volumes and Flyway history; a
recovery point; capacity, TLS, packet filtering, custody). An optional
`compatibility_baseline` input names a revision a target may already hold; the
candidate **fails** if any of its migrations is missing or modified, or a new one is
not newer, because Flyway validation would stop the update on the VM. The baseline
is declared, not observed: no VM state is inferred. Release qualification is never
update or deployment authority.

### D12 — Operator verification

(Amended by A1: the admission-on-`main` check is replaced by the admission seal check
offline and GitHub's run and approval records online; see A1.6.)

`infrastructure/release/verify-release.sh PACKAGE [--source CHECKOUT] [--store …]
[--trusted-root FILE]` (bash 3.2-compatible, no Docker) checks the package files,
the consistency of release, admission and candidate records, the attestation of
`release.json`, the archive, `mc-source.tar.gz` and `admission.json` (online, or
offline against a previously fetched Sigstore trusted root; D3 policy), that
`release.json` records the decider's merge of the admission, that the package is the
release the operator names (`--version`, required), online that the admission record
is still on `main` unchanged, and with `--source` the
`deploy.sh`/Compose hashes and the source revision's `deploy.sh` archive verifier (run
through the verifier's own `release.sh`, so a source revision older than
`infrastructure/release/` works). The repository, the trust anchor, is fixed in the
script. It prints the
`deploy.sh` archive, identity and revision arguments. It grants nothing.

### D13 — Cost and checks

The rehearsal runs only when release inputs change and is not a required check; no
required check name, ruleset or existing job changes. The attest action is pinned by
commit; `actions/checkout@v7` follows the existing workflows' tag convention.

## 5. Threats addressed

| Threat | Control |
| --- | --- |
| Artifact substitution (archive, image, SBOM, report) | Every package file is an attestation subject; manifests hash every file; `deploy.sh` verifies the archive SHA-256 and all identities |
| Mutable tags or self-declared inputs | Identities are digests recomputed from archive bytes; the admission is a reviewed file on `main`; the source revision must be on `main` whose required checks' latest authenticated evaluation passed (D2, A2) |
| Forged or cherry-picked check runs | GitHub Actions checks count only through GitHub's job record of a trusted run, the newest attempt decides, all runs are re-read for changes; other apps only if GitHub-operated; untrusted runs are recorded, never evidence (A2) |
| Promotion before admission | The promote job refuses without an `ADMITTED`, committed, code-owner record matching the attested candidate exactly |
| Forged admission authority | `decided_by` must be the account that merged a pull request changing only the record (GitHub's `merged_by`), not just a typed login |
| Security gate bypass | Exactly the candidate's findings must be accepted; promotion rescans and fails on any row not admitted at that installed version and severity, and on any loss of scan coverage |
| Token exposure to build or test code | Checkouts do not persist the token; `GH_TOKEN` is set only on the steps that call the API; Docker builds receive no runner environment |
| Rerun or tag replacement | Unique candidate names, refusal of existing tags/releases, asset read-back, attestations |
| Rehearsal leakage into a real release | Rehearsal records need `REHEARSAL-` and `rehearsal: true`; real promotion refuses both and any rehearsal candidate; rehearsals have read-only tokens |
| Secrets exposure | No secret is used; smoke credentials are generated and discarded; no logs, traces or env files are packaged |
| Persistent state | The smoke refuses any host but a fresh GitHub-hosted runner; `guard_update()` refusals are tested; nothing contacts a VM |

## 6. Residual risks (not accepted by the Leader)

1. Trust in GitHub Actions, the GitHub OIDC issuer, Sigstore and the runner image;
   a compromise of `main` or of the workflow could produce valid attestations.
2. The admission's authenticity rests on GitHub's account authentication and merge
   record and on protected `main`; the `main-merge-authority` bypass and the
   sole-Leader CODEOWNER paths are governance facts this pipeline cannot change.
   Until A2 the required-check evaluation trusted check runs from the required app
   (any workflow with `checks: write` can create such runs on any commit, as it can
   for the ruleset itself), so a forged success could stand in for a context with no
   run at all. A2 binds GitHub Actions checks to GitHub's job records instead; what
   remains is listed in A2.8.
3. Scanner false negatives and database lag; the rescan narrows, not closes, the
   window between admission and promotion.
4. Images are not reproducible; a rebuilt candidate is a new artifact needing new
   admission.
5. `deploy.sh deploy`, public TLS and anything on the real VM are not exercised.
6. Draft assets are mutable by writers (detected, not prevented).
7. Licensing of any public conveyance (F1/F5) is undetermined.
8. The candidate and promote jobs hold `id-token: write` for their whole run; the
   host steps that run there are the tooling from `main` and the source revision's
   suites (both protected code), while third-party build code runs inside Docker
   builds without the runner environment. A separate signing job (attesting digests
   passed as job outputs) was considered and not adopted: it would sign whatever
   digests the building job reports, so a compromised building job would still obtain
   valid attestations; malicious code on `main` is residual risk 1.
9. Writers can cancel a running stage through a pull request whose own `release.yml`
   uses the same concurrency group with cancellation, leave a partial draft when a
   stage is cancelled or times out (it blocks that name until removed), re-run an old
   `release.yml` run (old tooling, still signed as `main`), or publish a draft
   (making the binaries public before the F1/F5 determination). All fail closed or are
   visible in the repository; enabling immutable releases and keeping write access
   narrow are the mitigations, both Leader decisions.

## 7. Supersession and amendments

- **Focused review, 23 November 2026 (same session, not independent):** D2 tree
  binding and refusal of any unsuccessful run, D3 exact signer identity and source
  digest, D6 row-and-severity-bound rescan and scan coverage, D7 merge-record
  verification and exact acceptance set, D12 verifier fixes (own `release.sh`, fixed
  repository, required `--version`, admission still on `main`), fail-closed draft
  probes, and token hardening. Same delegation and authority; recorded in
  [testing.md](../testing.md#focused-review-remediation--23-november-2026).
- **Independent review remediation, 23 November 2026 (separate session):** D2 also
  refuses any unfinished run of a required context; D6 names the remedy for an
  uncovered promotion-time finding (a new candidate, then a new admission) and adds the
  scan-coverage gate. Same delegation and authority; recorded in
  [testing.md](../testing.md#independent-review-remediation--23-november-2026).

- **Amendment A1, 23 November 2026:** PR-free authenticated admission (protected
  environment approval of a Leader dispatch) replaces D7 and the admission parts of D8,
  D10 and D12; see §10.
- **Amendment A2, 24 November 2026:** the latest authenticated CI evaluation of the
  source revision replaces D2's rule that every run of a required check must have
  succeeded; see §11.
- **F3:** implemented as a pipeline; the procedure is unchanged.
- **F6:** the technical selection of signing/attestation and trust policy for project
  release packages is made by D3 and D12 under this delegation; the decision that
  `deploy.sh` need not refuse unsigned input is D3's. No residual risk is accepted.
- **Release recipe** ([Release build outside the VM](../deployment.md#release-build-outside-the-vm)):
  remains as a historical manual procedure; the pipeline is the supported path.
- **Not changed:** P8 transfer channel and custody, CT17/ADR009 runtime and recovery
  policies, `deploy.sh`, DP01 open decisions (F1, F4, F5, F10, F11 dispositions,
  budgets, collation), and every Issue's scope. DP01, DP03 and Phase 5 remain open.

## 8. Verification

Recorded in [testing.md](../testing.md#phase-4-release-pipeline-verification--23-november-2026).

## 9. Remaining gates

| Gate | Owner |
| --- | --- |
| Independent review, CODEOWNER approval, required checks on the exact head, protected integration of PR #267 | Reviewers and the Leader |
| A dispatched candidate from a `main` revision (requires this PR on `main`) | Leader or maintainer |
| Security admission of that candidate's exact identities and every finding | Leader |
| Licensing determination before any public conveyance; optional immutable releases | Leader |
| Transfer to the VM, custody, recovery point, update authorization, deployment (Phase 5, P8, P9) | Leader under separate authorization |

## 10. Amendment A1 — Authenticated admission without a pull request per candidate (23 November 2026)

### A1.1 Authority and status

**DELEGATED_TECHNICAL_APPROVAL; DELEGATED_APPROVED**, 23 November 2026
(Asia/Ho_Chi_Minh). The Project Leader and repository Owner @chi-thanh-nguyen gave the
explicit instruction **“CodeMastery — Redesign Security Admission, Rewrite PR #269, and
Complete Pending Admission & Release Promotion”**. It delegates the selection of the
technical admission architecture and its implementation, asks for an authenticated
GitHub-native Leader admission that needs no new pull request per candidate, and keeps
every other D1–D13 guarantee. Claude Code (Anthropic) compared the options and made the
selection below under that delegation; the Leader did not review the individual
selection. The instruction reserves, and A1 does not claim: approving or merging the
pull request; changes to rulesets, branch protection or CODEOWNERS; acceptance of any
vulnerability beyond the Leader's own admission act; publication; VM deployment; and
production secrets.

A1 is acted on in [PR #269](https://github.com/chi-thanh-nguyen/codemastery/pull/269)
(branch `docs/release-admission-1d0658a7`, rebuilt from `main` `b84de03`). It becomes
effective on `main` only through that PR's integration. A1 replaces D7 and the
admission parts of D8, D10 and D12; every other decision is unchanged. The history in
§1–§9 and in A1.2 is kept as it happened.

### A1.2 Why the pull-request admission is replaced

Under D7 every candidate needed a JSON record committed under
`docs/decisions/release-admissions/` by a pull request that changed only the record
and that its `decided_by` merged. For the first real candidate
(`candidate-1d0658a7215c-45091629955-1`, run
[45091629955](https://github.com/chi-thanh-nguyen/codemastery/actions/runs/45091629955),
source `1d0658a7215c2a3d3fed569738d522137989b666`, manifest SHA-256 `25802cda…4a63`,
archive SHA-256 `0b3bfef2…f44f0`), an earlier session verified the package, ten
identities, attestations, Q00–Q05, the 37 findings, and prepared
`CM-REL-ADMISSION-2026-11-23-1D0658A7.json`. The existing validator accepted it and 20
deliberate mutations were refused. It was committed to the branch of PR #269 and never
merged, so under D7 it never became an admission. The recurring cycle (record →
commit → pull request → review → merge → dispatch) costs a governance pull request
per candidate and mixes the security decision with repository change control. That
prepared record is not an admission and is not carried into `main`; A1 removes the
tracked record and the merge-record check from the active release path.

### A1.3 Options compared

| Option | Assessment |
| --- | --- |
| A. `workflow_dispatch` by the Leader only | No configuration. GitHub sets the actor; a pinned login and account ID, `run_attempt == 1` and agreement of the runner environment, event payload and run record identify who started the run. Limits: anyone who can make the Leader's account start a run (for example a writer's branch workflow, triggered by a Leader review, dispatching `release.yml` with `GITHUB_TOKEN`, which GitHub allows for `workflow_dispatch`) produces a run whose actor attribution GitHub's documentation does not state; a dispatch is a form, not an interactive confirmation of the verified candidate |
| B. Protected environment with a required reviewer only | Approval is an interactive act by an authenticated reviewer that neither `GITHUB_TOKEN` nor another writer can perform; GitHub keeps the review (`GET …/actions/runs/{id}/approvals`); a re-run needs a new approval. Limits: needs an environment (repository setting); an environment that does not exist is created **unprotected** when a job names it, so a typo or deletion fails open unless the workflow checks the rules; administrators may bypass unless disabled; anyone with write access can request an approval |
| C. A + B in one run (**selected**) | The Leader dispatches the request naming the exact manifest digest; a no-approval job verifies the dispatcher and shows the verified candidate; the Leader approves the pending environment deployment; the promote job reads the environment rules and the approval back and fails closed. Security decision (dispatch + approval) and promotion stay distinct steps of one run, so the decision cannot drift to another run |
| D1. Pull-request record (D7) | Strong (merge record), but one pull request per candidate; replaced |
| D2. Signed admission file (cosign / GPG key) | Key custody and rotation for one approver; no GitHub-native audit |
| D3. Custom in-toto predicate (`actions/attest`) carrying the decision | Equivalent integrity to A1.5's sealed `admission.json` with the already pinned provenance action, but adds a second action and a second verification policy; not adopted |
| D4. Issue comment / label / `repository_dispatch` | Weaker authentication or extra tokens; rejected |

D7 had rejected an environment approval because it can fail open and is not a durable
record of identities. A1 keeps the identities in the attested `admission.json` and
addresses the fail-open risk by verifying the environment's rules and the approval at
run time (A1.4), which D7's analysis did not consider.

### A1.4 Selected design

`stage=promote` of the **Release** workflow, dispatched from `main` by the admission
authority with these inputs (untrusted text, read by `release.py` from GitHub's event
payload, never interpolated into a shell):

| Input | Rule |
| --- | --- |
| `candidate` | `candidate-<12 hex>-<run>-<attempt>`; must equal the tag the attested manifest's own revision and run imply |
| `candidate_manifest_sha256` | The SHA-256 of the `release-candidate.json` the Leader reviewed; must equal the downloaded, attested manifest. The manifest names every identity, the archive digest and every finding with installed version and severity, so this one value commits the decision to all of them |
| `version` | `v<major>.<minor>.<patch>[-pre]`, not `v0.0.0-rehearsal`, unused as tag, release or draft |
| `accepted_findings` | Must equal the candidate's number of findings (an explicit acknowledgement) |
| `accept_critical_or_high` | Default `false`: any CRITICAL/HIGH finding refuses the admission unless set |
| `note` | Optional one-line risk-acceptance statement, recorded verbatim |

Jobs and gates:

1. **`Admission request`** (`contents: write` only to read drafts, `actions: read`;
   nothing written). Authenticates the dispatch (`release.py admission-request`): the
   runner environment, the event payload and GitHub's run record must agree that this
   is attempt 1 of a `workflow_dispatch` of `release.yml` at `refs/heads/main` in
   repository ID `1391772534`, and that the actor, triggering actor and sender (login
   **and** numeric ID, sender of type User) are one account equal to the pinned
   authority. It checks the version is unused, downloads only the manifest and its
   bundle, verifies the manifest attestation (D3 policy) and writes the admission
   preview (identities, digests, counts, every finding) to the job summary.
2. **`Release admission and promotion`**, `environment: release-admission`. GitHub
   holds the job until the authority approves it. Its first step re-reads the run
   record, the environment, its branch policies and the run's approvals and refuses
   unless: the authority's account ID is a required reviewer, administrators cannot
   bypass, deployments are limited to the `main` branch policy only, the run has an
   `approved` review in that environment by the authority's account and no other review
   (`release.py admission-approval`). It then downloads the candidate, verifies the
   package and attestations (D3/D8), and **records the admission**
   (`release.py admission-decide`), self-verifies it, and **attests `admission.json`
   before any candidate image is loaded or run**. The rest is D8 unchanged, with the
   release package now carrying `admission.json` and its own bundle
   `admission.sigstore.json`.

Trust anchors are fixed in `release.py`: repository and repository ID, workflow path and
ref, environment name, and the authority `chi-thanh-nguyen` → account ID `212181152`.
In addition the authority must remain a code owner of `/docs/decisions/` at the
dispatched `main` commit. Changing an anchor is a reviewed change of `release.py`.

### A1.5 Admission evidence (`codemastery.release-admission/v2`)

`admission.json`, no unknown fields: `record_id` (`CM-REL-ADMISSION-<run>-<attempt>`),
`decision: ADMITTED`, `decided_by` {login, id} (the approver), `decided_at` (UTC),
generated `scope`, the Leader's `statement`, `source_revision`, `public_origin`,
`candidate` {release_tag, manifest_sha256, archive_sha256, run}, `images` (five
services × containerd and graph-driver identity), `accepted_findings` (every candidate
row: service, id, package, installed version, severity), `accepted_counts`,
`release.version`, `not_granted` (`deployment`, `existing-vm-update`,
`licensing-determination`, `production-secrets`, `public-exposure`,
`public-publication`, all mandatory), and `authorization` {mechanism, repository and
ID, workflow ref, event, run ID, attempt, run URL, tooling commit, `dispatched_by`,
`approved_by`, environment rules, approval comment, the normalized request}.

`verify-admission` **re-derives** the whole record from its recorded request, its
authorization evidence and the candidate manifest, and refuses any difference. It also
binds the record to the run that uses it (run ID, attempt, tooling commit): promotion
accepts only the admission its own run made, and a release package only the admission
of the run that promoted it.

### A1.6 Integrity, replay and online/offline boundaries

| Property | How | Offline? |
| --- | --- | --- |
| Artifact integrity | `SHA256SUMS`, manifest file maps, identities recomputed (D3–D8) | Yes |
| Signing identity | Sigstore: `release.yml@refs/heads/main`, tooling digest, GitHub-hosted (D3) | Yes, with `--bundle` and a trusted root |
| Admission sealed by its own run | `admission.sigstore.json`: certificate `runInvocationURI` = the admission's run/attempt 1, `buildTrigger` = `workflow_dispatch`, repository ID (`release.py check-attestation-run`) | Yes |
| Admission content | Re-derived from evidence and the bundled candidate manifest; pinned authority; run binding | Yes, as a statement of the attested workflow |
| Authenticated authority | GitHub's run record (dispatcher) and approval record (approver) for that run must match (`release.py check-run-record`) | **No**: needs GitHub's API; offline verification proves only that the attested `main` workflow recorded these accounts |
| Risk acceptance | Exactly the candidate rows; rescan refuses any new, re-versioned or escalated row of any severity and any loss of coverage (D6 unchanged) | — |
| Promotion, publication, deployment | Unchanged: draft only; publication and deployment are not granted | — |

Replay: a re-run (`run_attempt > 1`) is refused before anything is decided (GitHub
documents that a re-run uses the original actor's privileges); a record names its run
and is accepted only by that run; the version is used once (D9); the manifest digest
binds one candidate; no admission is ever an input to another run.

### A1.7 Remaining threats (not accepted by the Leader)

1. Compromise of the authority's GitHub account or session (dispatch and approval
   are then both available to the attacker), of GitHub Actions, the OIDC issuer or
   Sigstore (residual risk 1).
2. GitHub's documentation does not state the actor of a `workflow_dispatch` created
   by another workflow's `GITHUB_TOKEN`; A1 therefore does not rely on the dispatch
   alone: the environment approval is an interactive act `GITHUB_TOKEN` cannot
   perform. The authority should approve only a run whose `Admission request` summary
   it asked for.
3. A repository administrator can change the environment between runs; each run reads
   the rules back and refuses a weakened environment, but a change made and reverted
   outside a run is visible only in the audit log.
4. Approval records and run records live as long as GitHub keeps the run; deleting the
   run (write access) removes the online authority evidence, while the attested
   `admission.json` remains. `verify-release.sh` reports that as a failure online.
5. Writers can request approvals (spam) and cancel runs; both fail closed.

### A1.8 Operator procedure (future candidates)

1. Read the candidate draft's summary; note `release-candidate.json`'s SHA-256 (its
   `SHA256SUMS` line, or the asset digest GitHub shows) and its number of findings.
2. Actions → **Release** → *Run workflow* on `main`: `stage=promote`, `candidate`,
   `candidate_manifest_sha256`, `version`, `accepted_findings`, optional `note`
   (`accept_critical_or_high` only if you accept such findings). CLI:
   `gh workflow run release.yml --ref main -f stage=promote -f candidate=<tag> -f candidate_manifest_sha256=<sha> -f version=<vX.Y.Z> -f accepted_findings=<n> -f note='<statement>'`.
3. Read the `Admission request` job summary; if it is what you reviewed, approve the
   pending `release-admission` deployment (run page → *Review deployments*, or
   `gh api -X POST repos/chi-thanh-nguyen/codemastery/actions/runs/<run>/pending_deployments -F 'environment_ids[]=<id>' -f state=approved -f comment='<text>'`).
4. Everything else is automatic; the result is the draft `v<x.y.z>`, verified with
   `verify-release.sh` (D12, amended) run from a clean checkout of the release's tooling
   commit (`release.json` `tooling.sha`, once `gh attestation verify` of `release.json`
   names it), with `--source` a separate checkout of its `source_revision` for the
   `deploy.sh`, Compose and archive checks. A source revision's own verifier may predate
   A1 and is never used; the verifier refuses any other tooling commit
   ([deployment.md](../deployment.md#using-a-release-package-with-deploysh)).

### A1.9 Required configuration

One environment, `release-admission`: required reviewer `chi-thanh-nguyen`, self-review
allowed (the authority dispatches and approves), administrator bypass disabled,
deployment branches limited to the custom policy `main`. No secret, ruleset, branch
protection or CODEOWNERS change. If the environment is missing or weaker, promotion
fails closed at its first step.

### A1.10 Verification

Recorded in [testing.md](../testing.md#release-admission-redesign-verification--23-november-2026).

## 11. Amendment A2 — Latest authenticated CI evaluation (24 November 2026)

### A2.1 Authority and status

**LEADER_APPROVED_IN_PRINCIPLE (policy); DELEGATED_TECHNICAL_APPROVAL (design)**,
24 November 2026 (Asia/Ho_Chi_Minh). The Project Leader and repository Owner
@chi-thanh-nguyen gave the explicit instruction **“CodeMastery — Replace PR #275 with
Authenticated Latest-CI Evaluation”**. It approves in principle replacing D2's rule that
every run of a required check in the source revision's history must pass with this
policy: *the latest valid CI evaluation of the exact source revision must have completed
and passed 100% of the required checks, with its provenance independently authenticated
and its evidence fully recorded*. It authorizes Claude Code to design and implement that
policy in [PR #275](https://github.com/chi-thanh-nguyen/codemastery/pull/275) (branch
`docs/phase5-setup-record`) and keeps every other gate of D1–D13 and A1. Claude Code
(Anthropic) made the design selections below under that authorization; the Leader did
not review the individual selections. The instruction reserves, and A2 does not claim:
approving or merging the pull request; an independent review; ruleset, branch protection
or environment changes; and any candidate, admission, promotion, publication or
deployment.

A2 becomes effective on `main` only through that pull request's compliant integration.
It replaces the check-evaluation rule of D2 and the corresponding sentence of residual
risk 2; every other decision is unchanged. The history in §1–§10 and in A2.2 is kept as
it happened.

### A2.2 Why D2 is changed

The rule A2 replaces read, in D2 as merged on 23 November 2026:

> A full 40-character SHA that is an ancestor of `origin/main` (default: the
> dispatched `main` commit), on which **every** required status check of the live
> `main` rules (`GET /repos/{owner}/{repo}/rules/branches/main`) has a completed,
> successful check run from the required app. All runs are read (`filter=all`): the
> newest run per context must have succeeded, **every run of that context must have
> completed, and no completed run may have any other conclusion**, because any workflow
> with `checks: write` in the repository can add a run under the required app's identity
> on any commit. A revision whose required check failed once and was re-run is therefore
> refused; pick a later revision. A queued or running run also refuses it, however
> recent a success beside it; dispatch again once it has finished.

On `84d10a3` (the merge of PR #274), job `137762522469` (`VM runtime privileges with the
real backend`) failed in attempt 1 of workflow run
[45194866405](https://github.com/chi-thanh-nguyen/codemastery/actions/runs/45194866405)
(`CI VM`); attempt 2 re-ran all seven jobs of that run and every one passed. The rule
above refuses the revision forever; its only remedy is a new commit whose sole purpose is
to pass the gate. The rule existed because check runs of app 15368 can be forged, but
counting every run is a weak defence both ways: it blocks legitimate re-runs, and a forged
success could still stand in for a context with no run at all (residual risk 2). A2
removes the reason for the rule by authenticating each result instead of counting runs.

### A2.3 Options compared

| Option | Assessment |
| --- | --- |
| A. Keep D2; a new commit after each transient failure | Safe for the failure case, but forces commits that change nothing, and still trusts any check run of a context that has no other run |
| B. Newest check run per context by name, app and time | Rejected: any workflow token creates app-15368 check runs with arbitrary `details_url`, `external_id` and timestamps |
| C. A Leader exception or override record per revision | Rejected: a new approval system that can drift into a CI bypass; provenance answers the question without it |
| D. Bind each GitHub Actions check to GitHub's job record of a trusted run; the newest attempt decides (**selected**) | Uses only data GitHub Actions writes itself (`actions: read`); handles re-runs, new runs and forgeries with one rule |
| E. Require every required check to come from one run attempt | Impossible: the required checks come from four workflows (`ci-backend`, `ci-frontend`, `ci-e2e`, `ci-vm`) and code scanning |

### A2.4 Trust model

- **What GitHub writes.** Workflow run records (`GET …/actions/runs?head_sha=`) and job
  records (`GET …/actions/runs/{id}/attempts/{n}/jobs`, `GET …/actions/runs/{id}/jobs?filter=all`)
  are written by GitHub Actions; the REST API offers no way to create a job. Check runs
  can be created by any GitHub App, and a workflow's `GITHUB_TOKEN` with `checks: write`
  creates them under `github-actions` (app 15368); GitHub's REST documentation states that
  only GitHub Apps can write checks and that `details_url` and `external_id` are supplied
  by the caller (read 24 November 2026). An Actions job's ID is the ID of its check run
  (`check_run_url`).
- **Trusted run.** Repository and head repository ID `1391772534`; `head_sha` equal to the
  revision; event `push` or `workflow_dispatch`; `head_branch` `main`; `path` a file under
  `.github/workflows/` that exists in the revision's tree (`git ls-tree`). For these
  events GitHub runs the workflow file of the run's own commit, so the executed
  definition is the revision's reviewed file. `pull_request` runs are excluded (they
  test a merge commit, not the revision), as are GitHub-managed `dynamic` workflows,
  every other event, and runs of `release.yml` itself: release stages are not CI
  evaluations, and the dispatched candidate run is one of them, still running while it
  evaluates.
- **Producer.** The single workflow path that has a job named after the context among the
  trusted runs. Two workflows with such a job make the producer ambiguous: refused.
- **GitHub-operated apps.** Code scanning (app 57789) check runs can be created only by
  GitHub's code scanning service, so the app identity authenticates the check run; it
  must also name the commit (`head_sha`) and this repository (`url`).
- **Fail closed.** A required check that names no app (`integration_id` absent: any app
  could satisfy it), or that names any other app, refuses the revision until D2 is
  extended for that app. Listings whose pages do not add up to GitHub's `total_count`,
  malformed or repeated IDs, or missing job listings refuse it as well.

### A2.5 Selection of the latest valid evaluation

For a GitHub Actions context:

1. the newest trusted run (`created_at`, then ID) of the producer workflow on the
   revision, and its newest attempt (`run_attempt`), are the latest evaluation; the
   run must be `completed`;
2. GitHub's record of that attempt must hold exactly one job of that name; every job in
   the listing must belong to that run and attempt, the revision, this repository's
   check-run and job URLs; the job must be `completed`/`success`;
3. the revision's check run with that job's ID must match it: name, app 15368,
   `head_sha`, status, conclusion, the run's check suite, API and web URLs;
4. the attempt listing and the all-attempt listing must agree on that attempt's jobs;
5. the run listing is read first and again after every other read; any difference
   (a run or re-run started or finished meanwhile) refuses the revision.

Every other job of that name in the trusted runs (earlier attempts, older runs) is kept
under `superseded`. Check runs under a required name that are not jobs of a trusted run
are recorded under `ignored_check_runs`; they neither pass nor fail a revision.

For a code scanning context: the newest check run (by ID) of the app on the revision, or,
when the app has none there, on the head of the merged pull request (D2 conditions, now
also requiring the pull request's base repository to be this one); any unfinished run
refuses; the newest must have succeeded.

| GitHub action | What GitHub records | A2 |
| --- | --- | --- |
| Re-run all jobs | A new attempt holding a new job record for every job | Accepted when that attempt's required jobs passed |
| Re-run failed jobs | A new attempt; the failed jobs and their dependents run again, and every other job is recorded again under the new attempt with its previous result and start time (observed on runs 45137936869, 45118284163 and 44948344999; GitHub's documentation: jobs not re-run “keep their outputs from the previous attempt”) | Accepted: GitHub's own record of the attempt is complete; such a job is flagged `result_kept_from_earlier_attempt` |
| Re-run one job | The same, for that job and its dependents | Accepted when the attempt's record holds every required job |
| New run of the workflow on the same commit (`workflow_dispatch` on `main` while `main` is the revision) | A separate run | The newest run decides; the older run's results are `superseded` |
| An attempt whose record holds no job, or two jobs, of a required context | — | Refused, with the instruction to re-run all jobs or start a new run |

### A2.6 Evidence (`codemastery.source-checks/v2`)

`release.py evaluate-checks` writes one record that the candidate job stores, with
`verified: true`, as `source_qualification` in `release-candidate.json` (so it is
hashed, attested and shown in the draft summary):

- `schema`, `policy` (the A2 rule in words), `repository`, `repository_id`,
  `source_revision`;
- per required check: `context`, `app_id`, `provenance` (`github-actions-job` or
  `github-app-check-run (GitHub code scanning)`), `evidence` (`revision` or
  `merged pull request #N head SHA`), `source_sha`, `evaluated_sha`, `status`,
  `conclusion`, `check_run_id`, `check_suite_id`, `url`, `started_at`, `completed_at`,
  `superseded`; for Actions also `workflow` (`path`, `id`, `name`, `event`, `ref`),
  `run_id`, `run_attempt`, `run_url`, `job_id`, `result_kept_from_earlier_attempt`; for the
  fallback also `pull_request` (`number`, `head_sha`, `head_tree`, `revision_tree`);
- `ignored_check_runs` and `untrusted_workflow_runs`, each with its reason.

Every value comes from GitHub's API responses read by the candidate job, none from a
dispatch input. The candidate schema stays `codemastery.release-candidate/v1`: the change
is confined to the versioned `source_qualification` object, promotion still requires
`verified: true`, and `candidate-manifest` refuses evidence of another schema or another
revision. Candidates made before A2 carry the earlier list and remain verifiable; the
summary labels them a legacy record. The candidate summary lists, per required check,
the decisive result and every superseded result, so the admission authority sees a
re-run before admitting.

### A2.7 What is refused

A newest attempt or run that failed, was cancelled, skipped, timed out or has not
finished; a forged success with the same name and app (with or without a real job's URL);
a check run that differs from its job record; a run of another workflow file, event,
branch, repository or commit; job records of another run, attempt, commit or repository;
results stitched from different attempts or runs; a missing required check; a context
two workflows produce; missing, short or changing API listings; a required check without
an app or from an app A2 cannot authenticate; the pull-request fallback with a different
tree, without a merged pull request, or over a failed or unfinished run of the app on
the revision. Regression tests cover each case (A2.9).

### A2.8 Remaining threats (not accepted by the Leader)

1. **Re-run until green.** Anyone allowed to re-run workflows can re-run a
   nondeterministic test until it passes; the latest attempt decides. The superseded
   results are recorded and shown in the candidate summary, and admission remains the
   Leader's act.
2. Trust in GitHub's records and API, GitHub Actions and the runner image (residual risk
   1). A change merged to `main` that adds a workflow producing a required job name is
   protected code; two producers refuse the revision.
3. The code scanning verdict summarizes analyses that workflows with
   `security-events: write` can upload; A2 trusts that verdict exactly as the ruleset
   does.
4. A re-run started after the second run listing is not seen; the candidate records
   the evaluation it relied on, with run and attempt, and a later failing attempt does
   not revoke it.
5. A job of `release.yml` (such as `Release tooling`) made a required check would refuse
   every revision until D2 is extended (fail closed); on 24 November 2026 none is
   required. An unrelated workflow run of the revision that starts or finishes between
   the two run listings also refuses it; dispatch again.
6. GitHub lists at most 1,000 workflow runs per `head_sha` query and check runs of the
   1,000 most recent check suites; a listing that exceeds them fails closed.
7. Not independently reviewed (A2.10).

### A2.9 Verification

Recorded in [testing.md](../testing.md#latest-authenticated-ci-evaluation-verification--24-november-2026):
28 source-check tests (81 release tooling tests in all), a mutation check of the
decisive conditions, and a read-only evaluation of `84d10a3` against the live API on
24 November 2026. That evaluation accepted the revision: nine GitHub Actions checks bound
to their job records (the six `CI VM` checks to attempt 2 of run 45194866405, with
attempt 1's failed job `137762522469` recorded as superseded and the decisive job
`137775683393`), and `CodeQL` from PR #274's head `cac03de` (check run `137717841069`, tree
`7c2d462` equal to the revision's). The tooling on `main` before A2 refuses the same data.

### A2.10 Remaining gates

| Gate | Owner |
| --- | --- |
| Independent security review, CODEOWNER approval, required checks on the exact head, protected integration of PR #275 | Reviewers and the Leader |
| A candidate dispatched under A2, its admission, promotion and any deployment | Leader under the existing authorizations |
