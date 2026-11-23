# ADR017: SSH Continuous Deployment, Recovery Execution and TLS Renewal for the Azure VM (Phase 5)

**PR #274 remediation addendum:** §11 refines the original implementation decisions
below under the Leader's explicit review-and-remediation instruction. Earlier executed
records remain historical evidence; their test counts and qualification limits are not
retroactively changed.

## 1. Authority, provenance and scope

**DELEGATED_TECHNICAL_APPROVAL; DELEGATED_APPROVED**, 23 November 2026
(Asia/Ho_Chi_Minh). The Project Leader and repository Owner @chi-thanh-nguyen gave the
explicit instruction **“CodeMastery — Full Phase 5 Implementation: Automated SSH-Based
Azure VM Deployment”**. It authorizes read-only discovery of the existing VM over the
Leader's SSH key, the complete SSH-based Phase 5 implementation (code, tests, workflows,
documentation) in one pull request, small necessary cross-Issue dependencies, and the
selection of every missing Phase 5 technical decision within the approved architecture,
recorded with its alternatives. SSH is the Leader-selected primary transport.

Claude Code (Anthropic) made every selection below **under that delegation**; the Leader
did not review any individual selection. The instruction reserves, and this record does
not claim: any production deployment, migration, image load, service restart, volume or
TLS change on the VM; credential creation or rotation; firewall/NSG changes; Azure
resources or spending; destructive restore or data-loss acceptance; public exposure;
publication of a release; ruleset, protection or CODEOWNERS changes; and approval or
merge of the pull request.

The decisions become effective on `main` only through the pull request's compliant
integration. Technical approval is not implementation completeness, review, integration,
qualification on the real VM, deployment authority or recovery readiness (section 9).

Known AI use: Claude Code (Anthropic) inspected the VM read-only, analysed the
repository, selected the design and wrote the implementation, tests and this record. No
AI service is added to the application, the release pipeline or the deployment.

## 2. Consumed decisions (unchanged unless section 6 says otherwise)

| Decision | What Phase 5 preserves |
| --- | --- |
| ADR009 r2 §3, §9.3 (CT17.F-runtime) | One VM, existing Compose plus override, frontend sole HTTPS ingress, root custody of `/etc/codemastery/runtime/.env`, immutable `/opt/codemastery/releases/<full-source-sha>`, root-only `/var/lib/codemastery/releases`, P8/P9, no on-VM build or download, acme.sh TLS-ALPN-01 renewal at ≤30 days, daily 03:30 UTC timer, exclusive operation lock `/run/codemastery/operations.lock` |
| ADR009 r2 §4–§5, §9.4–§9.5 (CT17.F-recovery) | 24 h RPO / 48 h RTO targets; capture every 8 h; 3 intraday + 7 daily + 4 weekly; pre-change points; writer exclusion and common cut; PostgreSQL custom dump restored under F1; opaque cold copy of the whole MinIO volume with the identical server; CMS AuthEnvelopedData AES-256-GCM to an RSA-3072 OAEP/SHA-256 recipient with `CMREC1` framing; RSA-PSS/SHA-256 producer signature; independent workstation custody of every point; Azure Hot/LRS design; completion marker last; no success-based pruning; no automatic restore |
| ADR015 + A1 | Releases are attested drafts; `verify-release.sh` from the release's tooling commit; source revision and tooling revision stay distinct; deploy.sh consumes `--archive`, `--archive-sha256`, `--expect-image`; publication and deployment are not granted by a release |
| `deploy.sh` and the Compose files | Remain the only code that loads images, provisions roles and starts services; every P6/P8/P9 gate stays. Phase 5 changes one predicate (section 6.1) |

## 3. Read-only VM discovery (23 November 2026, 10:27–10:40 UTC)

Connected as `azureuser@20.205.56.236` with `StrictHostKeyChecking=yes` against the
operator's existing `known_hosts` record (ED25519 `SHA256:1KMq6OQqC/oTFS0P/wn0rxnM/RqRxawQYV0SXmqxKoU`,
recorded 31 October 2026, consistent for the IP and the former Azure hostname, and equal
to the host's own `/etc/ssh/ssh_host_ed25519_key.pub`). The record is trust-on-first-use
provenance from 31 October, not an Azure serial-console fingerprint. A first attempt
timed out at TCP 22 from a new operator address; the Leader then admitted that address in
the NSG. Only metadata was read: no env value, key, token, credential or application row.

| Subject | VERIFIED_ON_VM |
| --- | --- |
| Host | `vm-codemastery`, Standard_B2as_v2, East Asia, Ubuntu 24.04.5 LTS, kernel 7.0.0-1017-azure, x86_64, 2 vCPU (AMD EPYC 7763), 7.7 GiB RAM, no swap, 64 GB StandardSSD_LRS OS disk (54 GB free, 3 % inodes used), systemd 255, chrony synchronized, UTC; unattended-upgrades reports a pending restart |
| Docker | Engine 29.9.2, containerd v2.4.2, Compose v5.6.1, **containerd image store** (`io.containerd.snapshotter.v1`), no `daemon.json`, live-restore off |
| Running stack | Compose project `codemastery` from `/home/azureuser/codemastery-release-061102e3` (an extracted source tree, no git metadata, group-writable, owned by azureuser): postgres, minio, backend, frontend running since the 23 November 07:14 UTC boot, minio-init exited 0. The five image IDs equal the ADR009 §2.3 preview identities (`4cec267a…`, `70a39ea8…`, `85371a3d…`, `18b9bb45…`, `13798f82…`). Hardening as the override sets it |
| Data | Volumes `codemastery_postgres-data` (49.6 MB on disk, database 8.9 MB) and `codemastery_minio-data` (34 kB, `.minio.sys` and one bucket, owner 65532); PostgreSQL 17.11 musl; Flyway V001–V006 successful, installed by `cm_migrator`; roles `cm_bootstrap` (superuser), `cm_migrator`, `cm_runtime`; 3 users, 3 courses, 5 materials (preview seed scale) |
| Custody | `/etc/codemastery` root 0700; `runtime/.env` root 0600 (key names complete for deploy.sh); `tls/` UID 101 (`messagebus` on the host) 0700, `privkey.pem` 101 0400, `fullchain.pem` root 0644; `/opt/codemastery` root 0700 with earlier manual release artifacts, TLS and pre-application backups; `/var/lib/codemastery`, `/run/codemastery`, `/etc/codemastery/backup` and `/etc/codemastery/acme` absent |
| TLS | Leaf `CN=codemastery.app`, issuer Let's Encrypt YE2, valid 3 Nov 2026 – 1 Feb 2027, SHA-256 `05:BB:3C:6E:…:D6:69`; verifies for `codemastery.app` against the host store and is the certificate served on 443 (also verified from outside). acme.sh v3.1.7 at `/root/.acme.sh`, **no cron or timer**; its `codemastery.app` configuration installs into `/etc/codemastery/renewal-staging/`; a second configuration for the former Azure hostname would install into the **live** `/etc/codemastery/tls/` if `acme.sh --cron` ever ran |
| Network | Listeners: sshd 22, docker-proxy 0.0.0.0:443 only; UFW inactive; Docker's iptables chains admit only 8080 to the frontend; from outside, 80/5432/8080/9000/9001 do not answer |
| Access | `azureuser` has `(ALL) NOPASSWD: ALL`; one authorized key; root login key-only; no managed identity (IMDS token request → HTTP 400) |
| Recovery | No backup timer, cron, script or recovery set; `/opt/codemastery/backups/pre-app-478310b` holds a 1 November raw volume archive (not an ADR009 set) |

## 4. Decisions

### D1 — Transport: OpenSSH with a pinned host key and a forced-command identity

`infrastructure/vm/cd/vm-transport.sh` is the single client path. It uses only the
committed pin `infrastructure/vm/cd/known_hosts` (`UserKnownHostsFile`, no global file,
`HostKeyAlgorithms=ssh-ed25519`, `StrictHostKeyChecking=yes`, `UpdateHostKeys=no`),
batch mode, no agent or forwarding. Uploads are streamed on stdin to
`codemastery-ops receive NAME SHA256 SIZE`, which writes only a file of exactly that size
and hash (an interrupted or altered transfer never becomes a file; a retry resends).

| Option | Assessment |
| --- | --- |
| **SSH with forced command + stdin uploads (selected)** | Standard tools only; one auditable entry; works with `restrict`; resend on interruption is cheap at ~400 MB |
| rsync/SFTP | Resumable, but needs `rsync --server`/`sftp-server` through the forced command, widening it |
| Self-hosted runner or Azure Run Command | Rejected by the instruction as primary transport; agent custody or cloud credentials |
| Registry pull on the VM | Violates ADR009/P8 (no on-VM download) |

### D2 — Identities and roles on the VM

A system user `cmdeploy` (locked password, key-only) whose single authorized key is
`restrict,command="/usr/local/sbin/codemastery-ssh-gate"`. The gate splits the requested
command without shell evaluation, accepts only `[A-Za-z0-9._:/=@+_-]` words and runs
`sudo -n /usr/local/sbin/codemastery-ops WORDS`; sudoers grants `cmdeploy` that one
program (validated with `visudo`). `codemastery-ops` derives the role from `SUDO_USER`:
`cmdeploy` → **deploy** (receive, install-tooling, plan, prepare, deploy, verify, resume,
status, inventory, tls-check); anyone else → **operator** (adds recovery list/send/ack,
capture, TLS renewal, timers). The deployment identity can therefore never acknowledge a
recovery point, read a recovery set or renew a certificate. Rejected: Docker-group
membership (root-equivalent, no accountability, ADR009 §9.3); reusing the Leader's key
in GitHub (explicitly excluded by the instruction); broad passwordless sudo.

Trust model: an approved run from `main` executes `main`'s tooling as root on the VM
(the same trust deploy.sh already requires). The tooling is uploaded as a `git archive`
and installed only if its git tree equals the commit's tree, immutable under
`/opt/codemastery/tooling/<sha>`.

### D3 — GitHub Actions: manual dispatch, protected `production` environment

Workflow **Deploy** (`.github/workflows/deploy.yml`), dispatched on `main` only; never on
push or merge. Job `Deployment request` authenticates the dispatcher (actor, triggering
actor, event sender and run record, login and numeric ID, first attempt, `deploy.yml` on
`main`) against the same pinned authority as ADR015 A1 and shows the request. Job
`Deployment operation` runs in environment `production`; its first step reads the
environment's rules and the run's approvals back and refuses unless the authority is a
required reviewer, administrators cannot bypass, only `main` may deploy, and the run was
approved by the authority (`cd.py approval`, same fail-closed logic as A1.4). The
deployment key is the environment secret `VM_SSH_PRIVATE_KEY`, written to a 0600 runner
file only for that job and removed at the end. One concurrency group, never cancelled;
the VM lock is the second barrier. No artifact is uploaded (public repository); the VM
returns secret-free JSON. Pull requests run only the unit tests (`Deploy tooling`).

Reachability: the NSG admits SSH only from the operator's address and GitHub-hosted
runners change address. **Optional gate**: with protected-environment variables
`AZURE_CLIENT_ID/TENANT_ID/SUBSCRIPTION_ID`, `NSG_RESOURCE_GROUP`, `NSG_NAME`,
`NSG_RULE_PRIORITY`, the job logs in with GitHub OIDC (no stored cloud secret), adds an
inbound rule for exactly its own /32 on TCP 22 and always deletes it. It needs a custom
role limited to the NSG's security rules — a Leader-reserved permission (section 9).
Without it, the workflow stops at the genuine network/authentication gate and the local
operator path (D9) remains.

### D4 — Operations and gates

| Operation | What it does |
| --- | --- |
| `plan` | Uploads tooling, source and the package manifests; verifies `release.json`'s release-workflow attestation; on the VM installs the immutable source checkout (git tree must equal `release.json` `source_tree`) and runs all **read-only** gates: `deploy.sh preflight`, image store, schema compatibility (D5), unknown project volumes, capacity (D6) |
| `prepare` | Full package verification with the release tooling's own `verify-release.sh`; stages the package root-only and immutable; re-runs every gate; then a **pre-update capture with hold** (D7) |
| `deploy` | Requires the held point named in the dispatch, its signature, the publisher's acknowledgement with a passing restore test, proof that the held containers did not run after the cut, unchanged volumes and Flyway history; then runs the release's `deploy.sh all` with each P9 flag backed by that evidence and verifies the result (D8) |
| `verify`, `resume` | Re-verification; cancellation of a hold by restarting the exact held containers (refused if a deployment attempt replaced them) |

### D5 — Schema/image compatibility evaluated from evidence

Before any change, `ops.py` re-derives the release's migrations from the immutable source
checkout (each file's SHA-256 must equal `release.json`), computes Flyway's CRC32 checksum
and description per file (verified against the VM's recorded checksums for V001–V006),
reads `flyway_schema_history` and the migration-added columns, and parses the release's
own `deploy.sh` deferral rule. The target must hold an unbroken successful prefix of the
release's migrations with equal checksums and descriptions; pending migrations must be
provisionable by that `deploy.sh`. A target ahead of the release (image-only rollback
across a schema change) is refused. This is what `--schema-image-compatibility-verified`
now stands for.

### D6 — Capacity gate (engineering defaults under this delegation)

Free bytes ≥ archive × (1 + 3) + 2 × measured data bytes + 1 GiB, and ≥ 100,000 free
inodes, on the Docker filesystem; configurable in `ops.conf` `capacity`. These are
fail-closed margins for staging, image unpacking and two recovery sets, not performance
budgets (ADR009 §3's measured-workload sign-off stays open).

### D7 — Recovery capture, hold and acknowledgement

`recovery.py capture` implements ADR009 §5 on the VM: stop frontend and backend; prove
exclusion (only PostgreSQL and MinIO on the project network, no other PostgreSQL client
session sampled twice, initializer not running); take the cut; stream `pg_dump -Fc`
(server's own 17.x client) into the CMS encryptor; recheck sessions and the full
history/row-count inventory; stop MinIO and stream an opaque GNU tar of the whole volume
(numeric owners, ACLs, xattrs, sparse, sorted); recheck the writers were not restarted;
restart MinIO; write the manifest (cut, timings, volumes with creation times, image IDs,
tool versions, Flyway history, row counts, component lengths/hashes, recipient and
producer key fingerprints, exclusion evidence, dependency inventory) and sign it; publish
the set directory by rename. Plaintext never touches the disk. Any failure removes the
partial set and restarts what was stopped. A **pre-update** capture keeps frontend and
backend stopped (`hold.json`) so no write can follow the cut until the update.

The **publisher** (`recovery.py publish`, Leader workstation, launchd every 15 minutes)
pulls staged sets with `recovery-send` over the pinned SSH channel, verifies the listing,
the pinned producer key and signature, every length and hash, authenticates and decrypts
every component without writing plaintext, runs the isolated restore test when
configured, keeps the independent copy (`~/CodeMasteryRecovery/sets`, 0700/0600, read
back), publishes to Azure Blob when configured (conditional create `If-None-Match: *`,
full readback, `COMPLETE.json` last), appends the off-VM ledger (refusing a cut older than
its newest), and only then runs `recovery-ack` on the VM. The deploy gate accepts only an
acknowledged pre-update point whose level ends in `+RESTORE_TESTED`.

Implementation note: every OpenSSL child runs with stdin redirected from `/dev/null`.
With an inherited non-terminal stdin, the workstation's Homebrew OpenSSL 3.6.5 ignored
the `-passin`/`-passout` sources and fell back to its interactive UI ("unable to get
passphrase"); this was reproduced deterministically (100 % with the inherited stdin,
0 % with `/dev/null`) and the passphrase still travels only through a pipe descriptor.

### D8 — deploy.sh invocation and verification

`deploy.sh all` from `/opt/codemastery/releases/<source_sha>` with the staged archive,
the five identities for the VM's image store, the configured readiness deadline, the
candidate's Q01 initializer qualification as reference, the run's authenticated approval
as provision/deployment/update authority, and the protected-environment references for
public exposure and packet filtering (plus the runner's outside port scan). Verification
afterwards keeps classes apart: infrastructure (each container's image ID equals the
release, states, health, restart policy, only frontend publishes 8080→443, Flyway equals
the release), runtime readiness (private `/actuator/health` UP), TLS (served leaf equals
the installed one and names the origin), external (from the runner: trusted chain,
`GET /` 200, open ports), and feature behavior **NOT_VERIFIED** here.

### D9 — Local operator fallback

`deploy-release.sh` is the same driver the workflow uses. The Leader runs it with
`VM_SSH_USER=azureuser VM_SSH_IDENTITY=~/.ssh/vm-codemastery_key.pem`; the operator role
runs the same `codemastery-ops`. The environment approval is then replaced by the
Leader's own authenticated SSH session; deploy references come from a local context file.

### D10 — Isolated restore test and MinIO boot

The publisher's restore test decrypts the dump only into a tmpfs inside a disposable
`--network none` PostgreSQL container, after authentication and hash checks, restores it
as a non-superuser migrator with `--no-owner --no-acl --exit-on-error`, and compares
Flyway history and every table's row count with the manifest. The MinIO component is
authenticated and inspected in memory (paths, numeric owner 65532, `.minio.sys`,
`format.json`). Booting a restored MinIO needs the runtime root credentials, which belong
to the Leader's escrow (ADR009 §5); the CI round trip boots it with synthetic credentials
and the identical server image.

### D11 — TLS renewal

`codemastery-ops tls-check` (deploy and operator) records installed and served leaf,
days left, OK/WARN_30/WARN_14/WARN_7/EXPIRED and acme.sh configuration findings.
`tls-renew` (operator, timer) does nothing unless ≤30 days remain (or `--force`), refuses
under `/etc/codemastery/freeze`, takes the lock, stops **only** the frontend, runs
`acme.sh --renew -d codemastery.app --ecc` (never `--cron`, so the former-hostname
configuration stays inert), validates the staged pair (key matches leaf, SAN, chain
against the host store, more than 30 days), keeps the previous pair under
`/opt/codemastery/backups/tls-<UTC>`, writes both files beside the live ones and renames
them over (directory bind; key UID 101 0400, chain root 0644), restarts the frontend,
waits for health and reads back the served certificate; any failure restores the previous
pair and restarts. `--staging` issues against Let's Encrypt staging in a throw-away acme
home without installing: the qualification ADR009 requires before enabling the timer.

### D12 — Scheduling and monitoring

systemd `codemastery-capture.timer` (00:00/08:00/16:00 UTC, `Persistent=true`, no catch-up
burst) and `codemastery-tls-renew.timer` (03:30 UTC) are installed **disabled**;
`codemastery-ops timers enable` after qualification. Failures and lock contention are
journal entries and `status.json`; `codemastery-ops status` and `recovery.py status`
report the newest acknowledged/qualified point age with WARNING at 16 h and BREACH above
24 h. Retention (`recovery.py prune`, explicit `--confirm`) keeps the newest three, the
newest per UTC day for seven days, per ISO week for the four completed weeks, every
pre-update/manual point until released, and the newest point always; it refuses when the
newest point is not complete in Azure (if configured).

## 5. Alternatives considered for the overall automation

| Option | Assessment |
| --- | --- |
| **Dispatch + protected environment + SSH (selected)** | Leader authentication and approval are GitHub records; no stored cloud credential; maximal automation after one-time setup |
| Deploy on every merge | Rejected by the instruction and by P9 (each update needs a fresh qualified point) |
| Local-only operator script | Kept as fallback (D9); every step manual, no GitHub record |
| A single run that captures, publishes and deploys | Impossible without the recipient key off the VM in the runner, or without the independent copy; the hold makes the two-step flow lossless instead |

## 6. Amendments and refinements of earlier decisions

### 6.1 CT17-P6-PRIVILEGES-2026-11-23-r9 — multi-version predecessor deferral

Predecessor: `CT17-P6-PRIVILEGES-2026-11-17-r4` (deployment.md) and r5–r8. r4 deferred a
migration-added column (V009 `enrollments.last_visited_at`) only when the history held
exactly 1..(version−1). The deployed preview holds V001–V006, so every release from `main`
would be refused by provisioning — after the application had been quiesced. r9 accepts
any exact successful prefix 1..k with 1 ≤ k < version (same column-absent, all-success,
no-gap, no-repeatable conditions; an empty history, a gap, a failure or k ≥ version still
fail closed). The matrix rows and the complete post-migration verification are unchanged.
Evidence: the existing V008 cases pass unchanged and a new V006→V009 exercise passes in
`test-postgres-privilege-matrix-containment.sh`. Owning Issue: CT17 #70 / DP01 #71.

### 6.2 ADR009 §9.4 staging container → SSH pull (refinement)

ADR009 selected a VM managed identity writing to a staging container, with the publisher
reading it. The VM has **no managed identity** and no storage account exists. The
publisher now pulls staged sets over the pinned SSH channel. This removes a cloud
credential from the VM entirely (strictly less privilege) and keeps every other §9.4
property: the VM cannot reach known-good history, the publisher alone writes the archive
container, completion marker last, conditional create.

### 6.3 acme.sh home

ADR009 selected `/etc/codemastery/acme`; the existing account lives in `/root/.acme.sh`
(root 0700). `ops.conf` `acme_home` uses `/etc/codemastery/acme` when it exists and
otherwise the observed location; moving it is an operator choice, not a precondition.

## 7. Threats addressed

| Threat | Control |
| --- | --- |
| Host impersonation / MITM | Committed ED25519 pin, strict checking, no TOFU anywhere |
| Stolen deployment key | Forced command, deploy-role allowlist, no recovery read/ack, no TLS; usable only from an approved `main` run (environment secret) and, without the NSG gate, only from admitted addresses |
| Unauthorized dispatcher / approver | Pinned login + ID, three views, first attempt only, environment rules and approval read back |
| Modified or substituted release | Attestation of `release.json` at its tooling commit; `verify-release.sh`; every staged file against the attested map; deploy.sh's own archive and identity checks |
| Interrupted / altered transfer, unsafe paths | Exact size and SHA-256 before a file exists; strict names; git-tree-bound tooling and source; no special files or escaping links |
| Update without a recovery point / lost writes | Hold from cut to update; acknowledged, restore-tested point; volume and history identity |
| Partial or forged recovery points | Signature against an independently pinned key, exact listing, hashes, AEAD, ledger monotonicity, marker last, no prune on failure |
| Concurrent operations | Workflow concurrency + VM `flock` (non-blocking, visible) |
| Secret leakage | No env file read or printed; context is references only; Keychain via pipe; keys never in argv or logs |

## 8. Residual risks (not accepted by the Leader)

1. Compromise of `main`, of GitHub Actions or of the authority's account yields root on the VM through an approved run (as deploy.sh already implies).
2. The host-key pin's provenance is the operator's 31 October first connection; confirming it once through the Azure serial console would remove the TOFU residue.
3. The publisher depends on the workstation being on, unlocked and admitted by the NSG; missed polls surface as age warnings, never as success.
4. Scheduled captures quiesce the application three times a day; their duration on real data is unmeasured.
5. The VM's pending OS restart, Docker restart behavior and reboot recovery are not exercised.
6. `azureuser` retains `NOPASSWD: ALL`; narrowing it is outside this task.

## 9. Remaining gates (none is granted by this record)

| Gate | Owner |
| --- | --- |
| Independent review, required checks, protected integration of this PR | Reviewers and the Leader |
| One-time setup: deploy key + `production` environment (reviewer, no bypass, `main`), variables `PUBLIC_EXPOSURE_AUTHORIZATION`, `PACKET_FILTER_VERIFICATION`; `bootstrap.sh --apply`; `recovery.py init-workstation`, `trust-producer`; offline key copy | Leader |
| SSH reachability for runners: NSG gate role/variables, or another Leader-chosen path | Leader (cloud permission) |
| A release built from a revision containing CT17-P6 r9 (v0.1.0-rc.1's deploy.sh refuses V006→V009; the plan gate reports it before any change) | Leader dispatch of Release (ADR015) |
| Azure storage account/container, RBAC and cost authorization; until then points are independent-copy only | Leader |
| TLS staging qualification, then timer enablement; capture measurement, then timer enablement | Operator |
| Deployment, public exposure, data-loss and restore decisions | Leader |

## 10. Verification

Recorded in [testing.md](../testing.md#phase-5-ssh-deployment-verification--23-november-2026).

## 11. PR #274 review remediation — 23 November 2026

### 11.1 Authority and trust boundary

The Project Leader/Owner @chi-thanh-nguyen explicitly authorized **“CodeMastery —
PR #274 Comprehensive Independent Review, Direct Remediation & Verification”**:
confirmed security/correctness fixes, regression tests, necessary documentation and
technical-decision updates, commit and push on this PR's existing branch. OpenAI Codex
performed this review and remediation. These bounded choices remain subject to
independent review and protected integration. No production, cloud, credential,
spending, publication, exposure or restore authority is granted.

**Refinement of D2–D3:** possessing the deployment SSH key does not authenticate a
GitHub approval or authorize root tooling. A caller-supplied Git tree is integrity,
not provenance. Each `cmdeploy` command now requires a GitHub-signed RS256 OIDC token
on an stdin header. Its audience is `codemastery:ssh:` plus SHA-256 of canonical JSON
argv; this covers upload names/hashes/lengths, tooling SHA/tree, release digest and
the entire deployment context. Validate issuer, repository and immutable repository/
owner IDs, pinned actor/login ID, `production`, `workflow_dispatch`, exact `deploy.yml`
on `main`, equal workflow/source SHA, GitHub-hosted runner, first attempt and token
lifetime. A persistent exclusive-create JTI record rejects replay. No JWT or bearer
is printed, passed in argv, or stored. Tokens are not recovery acknowledgements.

The verifier `host/authorize.py` is installed by operator bootstrap as root-owned
`/usr/local/lib/codemastery/authorize.py`, outside the uploaded tooling tree; approved
workflow tooling cannot silently replace that fixed boundary. Changing it requires
operator bootstrap. For installation, the requested SHA must equal the signed workflow
SHA and the requested tree must equal GitHub's public commit record. HTTPS access to
`token.actions.githubusercontent.com/.well-known/jwks` and the repository's Git commit
API is required; outage fails closed. These are authentication metadata, not executable
or image downloads. No permanent service, cloud role, signing secret or library is added.
The runner still reads environment protection/approval records before requesting tokens.
OIDC establishes the executing job identity; it is not a separate independent approval
review. A compromised trusted `main` workflow remains a root-authority compromise.
The authenticated operator fallback remains separately privileged and does not use OIDC.

Alternatives rejected: trusting caller hashes/context, restricting command names alone,
or a new permanent authorization service. A stored VM GitHub API token or broad cloud
credentials would add custody without improving this boundary. Claims and key format
follow [GitHub's OIDC reference](https://docs.github.com/en/actions/reference/security/oidc).

### 11.2 Transfers, capture, holds and interrupted deployments

Uploads, tooling installation and staging consumers share an exclusive transport lock,
preventing replacement between verification and use. Source/tooling archives reject
links, special files and `.git` paths before extraction; no approved current repository
file requires a symlink. Individually relative links are insufficient protection against
an escaping link chain.

**Refinement of D7:** capture requires private data services on the single project
network and rejects another running container mounting either volume. Disable restart
policies and persist original policies/container identities in `capture-intent.json`
before stopping services. Replace PostgreSQL HBA temporarily with network rejection
and only the container-local `cm_bootstrap` socket login; retain the original file in
the PG volume, reload and exercise TCP rejection. Existing client sessions refuse the
cut; do not exempt a caller-controlled `application_name`. Stop MinIO before the common
cut and PG dump, keep it stopped throughout both captures, and preserve encrypted
partial evidence on failure. Intent/hold writes sync both file and parent directory.
An interrupted process leaves the intent/fence/restart-disabled state for explicit
operator inspection, not automatic restart or a silent second capture.

Root/Docker administrators remain trusted: they can bypass HBA, edit tooling or write
the volumes directly and must not write during maintenance. No sample of sessions or
row counts is claimed to exclude a malicious host administrator. A successful ordinary
capture restores the connection policy and original restart policies; a pre-update hold
retains the fence and disables writer restart until authorized deploy or cancellation.
Failures of cleanup remain visible and preserve the intent. Partial evidence is never
deleted by acknowledgement pruning.

**Refinement of D4/D8:** persist a deployment-attempt marker before releasing the fence
or invoking `deploy.sh`. Any such attempt can change schema/data even if old container
IDs survive; subsequent resume/replay is refused and needs a Leader recovery decision.
A failed deployment independently checks containment. Verification findings retain the
hold; success archives it. A cancellation before any attempt restores policies, starts
the exact held containers and checks that they run.

### 11.3 Actual restore qualification and publication

**Refinement of D7/D10:** PostgreSQL PASS alone yields `+PG_RESTORE_TESTED`, which cannot
satisfy P9 or the qualified-point age/retention calculation. `+RESTORE_TESTED` requires
all four evidence fields PASS: PostgreSQL, MinIO boot, object recovery and IAM recovery.
The operator-authenticated receipt carries those results and the exact set/manifest
digest; both acknowledgement and deployment gates check them. An old suffix alone is
insufficient. Mechanism restore testing does not establish full-product F2/Q08 feature
acceptance, measured RPO/RTO or production readiness.

Capture records hashes of MinIO versions/delete markers, every readable object version's
payload, stat/metadata/tags, IAM user listing and server configuration. No object names,
content, credentials or exported configuration are added to the public manifest.
Active external identity/KMS/notification/policy endpoints are outside this qualified
topology and refused; bucket replication metadata is refused during archive validation.
MinIO's required runtime credentials are read only into private process memory for these
operations. Extra environment configuration remains a capture refusal.

The workstation restores PG with the captured image and F1 flags as a bounded migrator
(PUBLIC database rights revoked). With owner-only `minio_escrow` configured, authenticate
and inspect the complete tar in bounded memory (maximum 2 GiB, no links/special files),
restore numeric ownership/modes/ACLs/xattrs into a Docker local-driver **tmpfs** volume,
and boot the **identical captured MinIO image** as UID/GID 65532 on an internal network,
with no published port, initializer, pull or build. A helper holds the tmpfs mount until
the server mounts it, preventing unmount-induced loss. Compare the signed inventories;
prove the escrow application identity can list its approved bucket and cannot administer
the server. Required root credentials enter the Docker client environment by name-only
`-e` arguments; private container metadata is within the trusted local engine boundary.
Client credentials use stdin. Containers and tmpfs volume are removed afterwards.
Missing escrow never becomes full qualification. Actual memory/capacity/escrow custody
and separate external-dependency qualification remain production setup obligations.

Publication/prune share an exclusive workstation lock. An interrupted copy/upload can
reuse only byte-identical readback, never overwrite. Completion markers are deterministic
and written last. A ledger-committed point retries its acknowledgement after verifying
the independent/cloud copies. Adding full restore evidence may append a stronger record
for the same point without inventing a newer cut. Qualified ages use signed `t_cut`,
reject future cuts as clock errors and exclude partial restore results. Pruning considers
qualified points and verifies the newest independent copy and cloud components/marker
before deleting older eligible points; incomplete or untested newer points cannot evict
the last qualified point. Existing 3/7/4 classes and protected pre-update/manual points
remain unchanged.

### 11.4 TLS, verification and network cleanup

**Refinement of D11:** staging returns only after frontend restart, health, `nginx -t`
and served-leaf readback. The same checks run after challenge failure. Mark replacement
before the first pair rename so an interruption between key/chain renames restores both;
verify recovery and report recovery failure separately. SAN comparison is exact.

Post-deployment verification checks top-level readiness, exact host port 443 and restart
policy, in addition to existing image/Flyway/TLS checks. Failed image-store discovery
refuses rather than assuming graphdriver. External deploy/verify findings fail the
workflow report. `verify` without a version correctly uses the current deployment.
NSG cleanup treats a failed lookup as unknown, verifies rule absence after deletion and
rejects priorities outside 100–4096. No real NSG rule was touched during remediation.

The CT17-P6 r9 predicate and final privilege matrix remain unchanged by remediation.
The live draft `v0.1.0-rc.1` manifest identifies source `1d0658a7215c2a3d3fed569738d522137989b666`
and tooling `e9d6da15b3fe4d581765d1c733abb70698dac779`; its source lacks r9. A separately
admitted new release is still required for V006→V009. No existing release was altered.
