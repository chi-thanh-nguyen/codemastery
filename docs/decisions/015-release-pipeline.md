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

A full 40-character SHA that is an ancestor of `origin/main` (default: the
dispatched `main` commit), on which **every** required status check of the live
`main` rules (`GET /repos/{owner}/{repo}/rules/branches/main`) has a completed,
successful check run from the required app. All runs are read (`filter=all`): the
newest run per context must have succeeded, **every run of that context must have
completed, and no completed run may have any other conclusion**, because any workflow
with `checks: write` in the repository can add a run under the required app's identity
on any commit. A revision whose required check failed once and was re-run is therefore
refused; pick a later revision. A queued or running run also refuses it, however
recent a success beside it; dispatch again once it has finished.
The list is read at run time, never hard-coded, so later ruleset changes apply
automatically. GitHub reports the `CodeQL` code-scanning check (app 57789) only on
pull requests; on `main` pushes only the `Analyze (…)` jobs appear (observed on
`257a915`). So a context with **no run at all** on the revision may be satisfied on
the head of the single merged pull request whose merge commit is the revision, where
the ruleset enforced it at merge time, **provided the head's tree equals the
revision's tree** (squash-only merges of up-to-date branches make them equal; observed
for PRs #252, #260 and #261); a failed or pending run on the revision is never
overridden, and without such a pull request there is no fallback. Checked read-only
against the live API on 23 November 2026: all ten contexts pass for `257a915` (nine
on the revision, `CodeQL` on PR #260's head `cd9d43d`). The workflow itself always runs from `main`. Release tooling comes
from the dispatched `main` checkout; images, `deploy.sh`, Compose files and the
qualification suites come from a worktree of the source revision, as the VM's
immutable checkout will.

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
| Mutable tags or self-declared inputs | Identities are digests recomputed from archive bytes; the admission is a reviewed file on `main`; the source revision must be on `main` with green required checks |
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
   The required-check evaluation trusts check runs from the required app; any
   workflow with `checks: write` in this repository can create such runs on any
   commit, as it can for the ruleset itself. A forged success cannot hide a failed or
   unfinished run (D2), but it can stand in for a context that has no run at all.
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
