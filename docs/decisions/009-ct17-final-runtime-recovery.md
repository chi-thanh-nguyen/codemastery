# ADR009: CT17 Final Runtime and Recovery — Bounded Approvals

**Q03/P6 evidence correction (10 November 2026): see [§11](#11-q03p6-evidence-correction--10-november-2026).**
The original PostgreSQL fixture had excess database privileges. A new PostgreSQL-only
qualification supplies the corrected evidence without revising either r2 decision.

**Post-PR231 current-state addendum (10 November 2026): see
[§10](#10-post-pr231-integration-and-azure-evidence--10-november-2026).** ADR008,
including CP-030 r2, is merged; ADR009 awaits PR #233 protected integration. The
new dated Leader attestation supplies credit balance/expiration previously missing.
Sections 1–9 retain their original decision/evidence chronology; §10 updates only
integration and financial-evidence status, not the two r2 technical decisions.

**Current disposition (10 November 2026): see [§9](#9-continuation-attestation-delegated-selections-and-executed-qualification).**
Both r2 checkpoints are technically approved, including the Leader-supplied
resource/financial binding and qualified identity-bounded recovery mechanisms.
Batch 4 has **8/8 technical approvals**; deployment/recovery readiness is not claimed.
Sections 1–8 retain the r1 decision and evidence history; their then-current
missing-input/NOT_RUN statements are updated only by the explicit §9 deltas.

## 1. Authority, baseline and exact dispositions

Recorded **9 November 2026 (Asia/Ho_Chi_Minh)** under the Project Leader
@chi-thanh-nguyen's explicit instruction **“CodeMastery — Complete Batch 4
CP-033/034 | CT17 Final Runtime & Recovery Contract Approvals”**. This is Codex's
delegated technical approval under that instruction, limited to the approved
clauses identified below. Personal Leader confirmations are distinguished in §2.
No personal risk, spending, access grant or data-loss acceptance is inferred.

Publication preparation resumed **10 November 2026** after interruption. The
9 November decision revisions and dated evidence below are preserved; main and
PR #231 were rechecked before publication. No failed network check or completed
local qualification inspection was repeated or promoted to runtime acceptance.

| CP / owning Issue | Exact revision | Disposition | Approved portion / blocked remainder |
|---|---|---|---|
| CP-033 `CT17.F-runtime` / [CT17 #70](https://github.com/chi-thanh-nguyen/codemastery/issues/70) | `CT17-F-RUNTIME-2026-11-09-r1` | **DELEGATED_PARTIALLY_APPROVED** | §3 runtime policy incorporating §2; actual VM/resource/access/TLS/Final-artifact bindings remain **BLOCKED_BY_MISSING_EVIDENCE**, with actual resource/budget inputs **PENDING_LEADER_INPUT** |
| CP-034 `CT17.F-recovery` / #70 | `CT17-F-RECOVERY-2026-11-09-r1` | **DELEGATED_PARTIALLY_APPROVED** | §§4–5/7 design objectives, retention, consistency/qualification protocol; concrete destination/permissions and executed MinIO/encryption compatibility remain unapproved, as specified in §6 |

These are portions of the existing checkpoints, not new batch members or completion
of either CP. **Batch 4 remains six fully decided checkpoints plus two partial
checkpoints, not 8/8 technically approved.** Implementation, runtime qualification,
independent review, exact-head required CI and protected integration remain separate.

Fresh starting `origin/main`: **3eeacc8f5b3244c6d3db403d3e7f476f9caeb554**.
[PR #231](https://github.com/chi-thanh-nguyen/codemastery/pull/231) is **open and
unmerged** at `235bc237bcc1a55f7e6015f715d283991b307f76` during this review.
Its [ADR008](https://github.com/chi-thanh-nguyen/codemastery/blob/235bc237bcc1a55f7e6015f715d283991b307f76/docs/decisions/008-batch-04-contract-approvals.md)
contains six delegated decisions and the earlier CP-033/034 blockers. ADR008 is
not on starting main. This follow-up neither modifies that branch/record nor
copies its unrelated testing decisions. ADR009 is the next available number
after the reserved ADR008. Its CT17 decisions depend on effective CT17 predecessors,
not protected integration of #231. Both PRs need their normal independent gates.

The corrected planning register remains `contract-checkpoint-batching/`
`run-20261108-135024/BATCH-04.md` and `APPROVAL-CHECKPOINT-REGISTER.md`, corrected
by `verification-20261108-151823/` README, verification, roadmap, dependency
traceability and `registers/revised-plan.json` (`local_ids`: CP-033/034).
Directory discovery found no newer applicable run. No cross-batch HARD approval
predecessor is introduced. HC-U20/LD-01 is resolved by ADR004; DG-U18 is a deployment
binding, not a feature gate. The local handoff LD-05 register predates §2's new
confirmations and does not negate them. Its remaining actual-resource/destination
and qualification gaps are retained precisely below.

Effective predecessors preserved: ADR001 architecture/module boundaries; ADR002
CT17.I-scope/I-access; CT17-2026-10-24-r1 P1–P4;
CT17-INTERIM-2026-10-24-r2 P5–P10;
CT17-INTERIM-BINDINGS-2026-10-25-r1 B1–B3;
**CT17-FINAL-2026-10-25-r1 F1/F2**;
CT17-P6-PRIVILEGES-2026-10-28-r1 and the 31 October approved TLS/Compose paths.
ADR004 establishes effectiveness within their exact scopes. ADR003's amendments
and risk exclusions and all ADR005–ADR007 decisions are unchanged. No amendment
or broad companion prerequisite approval is necessary or made.

## 2. Leader confirmations and current evidence

### 2.1 New personal Leader confirmations

The authorizing instruction personally confirms **L1** retention of the existing
Azure VM; **L2** `codemastery.app`, intended origin **https://codemastery.app**;
**L3** @chi-thanh-nguyen as primary production operator, secrets custodian,
recovery decision authority and backup-key custodian; **L4** public website access
in principle **only after** applicable security/release/runtime/QA/deployment gates.
These selections require no reconfirmation. They do not verify a resource ID,
grant OS/cloud permissions, admit an image, fund resources or authorize exposure.

**L5** explicitly delegates selection of technical recovery-policy targets,
frequency, retention and qualification criteria. §4 uses that authority for design
targets, not a personal SLA, guaranteed outcome, actual incident data-loss decision
or spending commitment. Production restore remains separately authorized.

### 2.2 Evidence ledger — 9 November 2026, ICT

| Subject | Evidence obtained | Limit / remaining input |
|---|---|---|
| Public DNS | `dig` at 23:21 ICT: `codemastery.app` A = **20.205.56.236**, no AAAA answer; historical `codemastery.eastasia.cloudapp.azure.com` A = same IP | Public resolution only; does not prove Azure allocation, VM identity or permission to change DNS |
| Served TLS/HTTPS | Read-only `curl --head --max-time 20 https://codemastery.app` timed out; Python verified-TLS connection also timed out; independent web retrieval unavailable | **NOT_VERIFIED**: SAN/subject, issuer, validity/fingerprint, served chain and application origin. No certificate was accepted with verification disabled; the old Azure-hostname certificate is not evidence for this domain |
| VM read access | Existing-trust, batch-only SSH to `azureuser@20.205.56.236`, timeout 10 s, timed out on TCP 22 | No authentication/grants were changed. This does not establish why access failed or that the VM is stopped. No host command ran |
| Azure resource/account | No Azure CLI or Azure connector is available in this environment | Subscription/resource group/full VM ID, region/SKU/CPU/RAM/OS, disks/persistence/free space, credit/expiration and spend constraints remain **UNVERIFIED** |
| Host runtime/access/custody | Repository has B1–B3/P6 and approved Compose/TLS paths; historical Ubuntu/2-vCPU/8-GiB/Docker/azureuser observations exist | Installed versions/image-store, checkout/.env path/owners, grants, actual sudo/SSH/emergency access, firewall, volume topology and renewal remain **UNVERIFIED**; GitHub assignment is not an OS grant |
| Local release package | Existing `~/CodeProjects/codemastery-release-package-061102e3/`: all four `SHA256SUMS` entries passed; OCI index/manifest hashes and five expected-image mappings checked | Local integrity only; no VM image match, signature/authenticity, Final image admission or recovery-set measurement |
| Historical admission | `CM-DP01-SEC-ADMISSION-2026-11-04-061102E3`, local Leader-recorded limited Academic Preview record; ADMISSION-BINDING repeats its scope | Not unrestricted Final/production admission; no renewal of residual-risk acceptance. HC-U21 repository/local provenance discrepancy remains unresolved, not silently reconciled |
| Recovery evidence | Effective F1/F2, existing image/provisioning qualification and current source inspected | No complete MinIO/version/IAM/config recovery or authenticated encryption/key-recovery round trip found. Initializer tests and archive hashes do not satisfy these gates |

No secret files, private keys, authentication tokens or runtime environment values
were opened or printed. No Azure resources, firewall/DNS, grants, volumes or
production data were changed. No production endpoint mutation was attempted.
Missing measurements are evidence blockers, not reasons to ask again for L1–L4.

### 2.3 Exact local historical release identities

Source **061102e31a47cb73d43a92bb429c7d114492005a**; archive
`codemastery-release-061102e3-linux-amd64.tar`, SHA-256
**6ec24012fdcc15c22a424088f4d8928f5955731bf751d118df956f3e3c113643**,
393,993,728 bytes. The five **linux/amd64 OCI manifest** identities checked are:

| `codemastery.invalid/<service>:release` | OCI manifest digest |
|---|---|
| postgres | `sha256:4cec267a406d2340d54f6a6b5fdeeb98919c047b8331a962bdc17fb8d61c42b4` |
| minio | `sha256:70a39ea85e77febd6ebe3fda31fe2959ddcabd51349c8bc9705f5044668faf83` |
| minio-init | `sha256:85371a3db0a448de501113baa93bad3c2f5a1626d90aa23298275546600e3d53` |
| backend | `sha256:18b9bb45af41d06c1b7cf091ddf3ce5f148518196ba5bf68ca16fe40e7a143c3` |
| frontend | `sha256:13798f82e9b69324478c48367dc39657fe043885f607241ed30139e494d0c0df` |

These are not portable Docker image config IDs, proof of the admitted build-host
IDs, observed production identities or a newly selected Final release. Preserve
the P8/store-specific identity comparison and exact-source/admission chain. The
archive's size is executable packaging, **not PostgreSQL plus MinIO recovery size**.
Final artifacts need actual approved feature content, provenance, qualification,
current security admission and measured compatibility before deployment.

## 3. CP-033 approved runtime-policy portion

Approve the compatibility policy for the Leader-selected existing Azure VM and
single intended origin. Retain one deployable Java 25/Spring Boot 4.1.1 backend,
PostgreSQL, private MinIO plus one-shot initializer and existing Nginx frontend.
Use the existing base Compose plus approved
`infrastructure/vm/docker-compose.vm.yml`; no new service or VM topology.
Build off-host, qualify immutable linux/amd64 artifacts under B1/P8, preserve
persistent volumes and the complete approved Final feature scope.

Public application ingress remains HTTPS 443 to frontend TLS 8080, same-origin
`/api`; build frontend with `/api`, bind CORS to **https://codemastery.app** only
when implementation/deployment is separately authorized. Keep NONE forwarding,
host rejection, private backend/PG/MinIO/Console and independently verified
Docker/NSG packet-filtering boundaries. Existing source is engineering policy,
not proof of which host ports are exposed now. No HTTP/SSH firewall change,
wildcard origin, extra proxy or public health/storage exception is selected.

Retain P6 bootstrap/migrator/runtime separation, bounded grants, Flyway history
and Hibernate validation, no runtime DDL/role escalation, separate bucket-scoped
application storage identity and conflict-stop initialization. The existing
Interim runtime privilege matrix is not silently expanded for Final features:
each new consumer needs its effective contract and separately approved grants.
Neither a Final grant matrix nor physical schema migration is invented here.

Preserve approved `/etc/codemastery/tls` read-only directory mapping to
`/etc/nginx/tls`, UID 101 key mode 0400 and B3 ownership/traversal checks. The
historical `/etc/codemastery/runtime/.env` and `azureuser` remain discovery leads,
not approved actual Final locations/grants. Obtain metadata-only owner/mode/link/
ancestor checks and actual checkout identity; do not read private keys/.env.
Require a certificate actually matching `codemastery.app`, full-chain validation,
renewal/reload procedure with accountable operator and served-certificate readback.
No ACME client/challenge/listener, timer, secret path or new permission is added.

Capacity acceptance requires current VM/disk IDs and measurements: idle/peak
memory and startup/Flyway overhead; CPU and cost-14 BCrypt workload; PG/MinIO
working sets; available persistent/workspace bytes and inode headroom; backup
capture/upload/restore bandwidth and quiescence duration; restart/reboot behavior.
Use the actual selected Final workloads and approved feature oracles. No RPS,
heap/pool/tmpfs/latency or disk budget is guessed from historical 2 vCPU/8 GiB.
Stop capacity sign-off when measured peak plus required backup/recovery workspace
cannot fit actual available capacity. Do not weaken security or omit MinIO to fit.

Keep P8 restart behavior and dependency limits. Startup/init/migration/health
must succeed before actual feature checks. P9 update requires a qualified compatible
pre-update recovery point; image rollback never reverses a migration or accepts
data loss. Stop on unknown schema/image compatibility; preserve state and obtain
the Leader's recovery decision. The existing script explicitly excludes Final
backup/restore/renewal execution; this policy does not extend its implementation.

**Whole CP-033 remains blocked** until §2's actual resource, custody/access,
origin/TLS and exact Final artifact constraints can be bound without assumptions.
These are material to sign-off even though L1–L4 themselves are resolved.

## 4. CP-034 approved policy design and alternatives

**Design targets selected under L5: RPO 24 hours, RTO 48 hours; coordinated capture
every 8 hours; 7 daily and 4 weekly qualified recovery points, plus three rolling
intraday qualified points.** Also capture before important updates/migrations/demo.
These are future engineering objectives, **not existing capability or guarantees**.
Actual incident data-loss acceptance and restore/expenditure/access authority remain
the Leader's. No numerical production performance is inferred from these objectives.

All options below require the complete F1/F2 product set in §5, client-side
authenticated encryption before transfer, Leader key custody/recovery, approved
private off-VM destinations, hash/provenance verification and an isolated full
restore. Shared missing evidence is actual recovery size, bandwidth/quiescence,
resource/credit/custody bindings and executed MinIO/crypto/product qualification.
None substitutes a live PostgreSQL volume copy or a current-object-only mirror.

Let **S** be measured encrypted full data/configuration set size in billing GB
(all required object versions/metadata included), **R** unique compatible release
artifacts retained separately, and **B** worst measured schedule lateness plus
capture/verification/off-VM publication time. S and R are currently unknown for
Final. Use full independent sets initially in the design; no unqualified dedup/
incremental chain, compression ratio or restore dependency is assumed.

| Option | Targets; interval/retention | Failure domains and overhead | Complexity, detection, restore dependencies and critical risk |
|---|---|---|---|
| Lower cost/complexity | Nominal RPO 24h / RTO 72h; one daily capture, 7 daily points | One already authorized private off-VM operator-held destination; up to 7S stored and 30S transferred per 30 days, plus R; no new cloud spend only if that destination actually exists | Fewest jobs/copies, operator checks each run and point age; requires reachable destination/key/artifacts. Daily cadence alone cannot sustain strict 24h with positive B or a missed run; single off-VM copy can share operator/device failure. Not selected |
| Reference balanced | RPO 24h / RTO 48h; daily, 7 daily + 4 weekly; pre-change/demo; periodic independent encrypted copy | Private Hot/LRS candidate plus independent copy; at most 11S primary storage and 30S/month primary transfer plus extra copies/R | Moderate complexity, failure/age monitoring. Single missed daily capture exceeds 24h even before publication lag. Periodic independent copy may be too old for Azure credit/subscription/region loss; RTO not proven |
| Selected balanced refinement | RPO 24h / RTO 48h; every 8h; up to 3 intraday + 7 daily + 4 weekly distinct points; pre-change/demo | Up to **14S** on each approved off-VM destination plus R; **90S/month** primary transfer, and up to 90S additional independent-copy traffic; duplicates selected for multiple retention classes count once | Three captures/day, no extra permanent backend service. Failures reported immediately; latest qualified point age monitored, warning at 16h, breach above 24h. Both Azure and independent non-Azure custody must have a point within target for an Azure-loss claim. Additional quiescence/transfer must pass measured resource/cost feasibility |
| Higher protection | RPO 6h / RTO 12h; every 2h; 12 intraday + 7 daily + 4 weekly points | Up to 23S per destination and 360S/month per full-copy path plus R; consider ZRS/independent copy after budget approval | More interruptions, job/retention/credit overhead and tighter operator/replacement/key availability. Immediate failure/age checks; conditional one-miss bound 4h+B <= 6h needs B <= 2h. No evidence justifies this burden or 12h complete RTO here; not selected |

The refinement preserves the reference's objectives/longer retention and adds
cadence margin against one failed capture. It does not claim optimal measured
cost or capacity. If three coordinated captures cannot fit the real workload,
revise the proposed operational realization before full approval; do not silently
relax objectives, skip a store or count partial jobs. More frequent copying is not
a waiver of spending, access or maintenance authorization.

### 4.1 RPO and point-age correctness

Each set identifies a conservative common UTC cut **t_cut**, established after
all writers drain and no later than either store's snapshot. It becomes usable
only after complete off-VM upload, readback/integrity verification and required
qualification status. At incident time **t**, report `t - t_cut` for the newest
eligible retrievable point, not the backup start/completion time alone. A job's
existence, successful pg_dump or Azure multipart upload start does not create a
recovery point. Clock disagreement is a qualification failure, not a reason to
invent a grace interval.

For successful evenly scheduled captures, conservative point age is bounded by
`I + B`; after one missed capture it is `2I + B`, only with bounded lateness,
successful next publication and no additional failures. Thus the reference's
I=24h does not imply <=24h. Selected I=8h leaves up to 8h aggregate B in the
one-miss mathematical budget. **B <= 8h is a qualification ceiling, not an observed
duration or an acceptable 8h application outage.** Separately measure/approve
quiescence impact; no numerical maintenance window is invented here.

Detect a failed/late/corrupt/unpublished run immediately; at 16h point age the
remaining target margin is 8h. The operator must investigate and record the
response; age above 24h is an objective breach. No email/SMS/push integration,
public-exposure change or automatic destructive action is authorized as monitoring.
Unknown/missed schedules, repeated failures or destination/key unavailability
invalidate the bound. Keep evidence; never report that a target was met anyway.

Apply the target separately to each protected failure domain. Azure Hot/LRS
survives loss of the VM disk but does not remove shared subscription/credit,
credential or regional failure. An independent copy must be retrievable outside
those dependencies, including its key and compatible artifacts. Copy every
qualified set there if claiming the same 24h objective for Azure loss; a weekly
external copy cannot support that claim. No such copy/destination is verified now.

### 4.2 RTO, retention and demo freeze

RTO measures incident onset to safely restored, independently validated service,
including detection, operator response, Leader authorization, replacement compute/
funding/access, artifact/key retrieval, download/decryption, PG/MinIO restore,
schema/grant/reference checks, feature/security QA and explicit reopening authority.
Keep timestamps for each phase, including human waits. A timed pg_restore alone
does not establish 48h. Account/credit/key loss may breach the objective without
independent recovery resources; do not assume replacement availability or new spend.

Retention is by UTC capture date/week from **qualified** complete points: keep
the latest three intraday points, latest eligible point for each of the last seven
daily slots and four completed weekly slots. Overlap is one stored set, not duplicate
billing. Extra pre-change/demo and incident-preserved sets are additional, explicitly
accounted capacity. Never delete the last known-good point, an unresolved incident
hold or a point/artifact/key still referenced by retained sets. A failed run cannot
trigger success-based pruning. Missing slots remain visible gaps; do not age out the
only good set merely because the next scheduled run began. Pruning requires its
separately authorized identity, all references and successful destination readback.

Before a demo, require a fresh qualified point of the exact selected release/data,
verify independent retrieval/key custody, and freeze unqualified updates/migrations
until the demo ends. The operator schedules the actual freeze/window; this record
sets no date or new access grant. Continued writes still need scheduled protection;
a release freeze alone is not zero data loss or a substitute for backups.

## 5. Approved complete-set and consistency protocol

F1's PostgreSQL 17 compatible `pg_dump` custom archive and F2's coordinated,
fail-closed qualification interface remain unchanged. Include full application
schema/data, sequences/constraints and Flyway history/checksums. Restore with the
bounded migrator using **--no-owner --no-acl --exit-on-error**, after separately
authorized empty-target provisioning. Reapply the exact reviewed runtime grants
independently; never restore cluster-wide role/password dumps, elevate runtime
rights or import foreign memberships. Actual role identity/credential recovery
comes from the Leader's protected custody inputs, not guessed names or backup logs.

A full set additionally includes all required MinIO objects/versions/delete markers,
object metadata and attributes, bucket definitions/versioning/lifecycle/retention/
policies, IAM/policy/group/service-account state and storage configuration needed
by the actual deployment. Record all disks/mount topology, UID mappings, server/MC
versions, external secret/encryption dependencies and immutable application/config/
release references. Unknown external KMS, replication, lifecycle or IAM dependencies
block completeness; do not assume they are absent. Required secrets/configuration
must have separately authorized encrypted escrow/retrieval, never plaintext exports
in Git, shell arguments, logs, public artifacts or the VM-only recovery copy.

Future authorized capture proceeds as follows; no step is executed by this task:

1. Check actual approved scope, exact images/config, trusted time, available
   capacity and authorized custody/destinations. Record prior known-good point;
   prevent overlapping captures/pruning. No arbitrary new lock file/path is selected.
2. Quiesce and drain **all** business, scheduler, initializer, upload and direct
   admin writers to both stores, including open transactions/uploads and internal
   storage mutation relevant to the selected mechanism. Prove exclusion; an operator
   process or Docker restart policy that can resume writes invalidates it. Do not
   invent a public maintenance endpoint or assume an existing scheduler flag stops
   all writers. Exact orchestration needs separate implementation authorization.
3. Establish t_cut and the full store/config inventory. Hold write exclusion
   through PG snapshot/capture and complete qualified MinIO capture. Reject any
   detected mutation, missing component, inconsistent timestamp or producer error.
   No active PG filesystem copy, default `mc mirror` or database-only combined claim.
4. Encrypt each confidential stream before unprotected storage/transfer, using the
   qualified §6 mechanism when available. Capture every producer/encryptor exit and
   reject truncation. No plaintext aggregate spool on an unapproved host directory;
   any necessary plaintext workspace requires separately approved encrypted custody.
   Release quiescence only after immutable complete local capture and the separately
   authorized resumption checks; record downtime independently from upload duration.
5. Stage immutable, uniquely identified encrypted components at approved off-VM
   destinations. Check complete counts, lengths/hashes, decryption/authentication
   evidence and required qualification state against F2's trusted manifest. Commit
   the completion record **last**, only after full readback verification. No partial
   overwrite of the previous recovery point, public object access or auto-delete.
6. Publish eligibility only when all declared components/dependencies, trusted
   provenance, destination copies and applicable restore qualification pass. Record
   any untested feature limitation. Advance latest-good/retention only after this;
   failed publication leaves the previous known-good point intact and alerts the
   operator. Resume/cleanup failures remain visible, never swallowed as success.

Restore into a separately authorized, isolated nonpublic empty target; retain
production evidence/newer state untouched. Verify trusted manifest/hash provenance,
recipient key and complete ciphertext before consuming decrypted content. Recreate
approved private topology/identities, restore PG under F1 and MinIO by its qualified
exact mechanism, provision bounded grants and external secrets from custody, then
run F2 schema/history/constraints/object-reference/access and actual feature checks.
Do not auto-run initialization that overwrites restored IAM, migrations that change
the selected recovery schema, background notifications/events or reset-email delivery.
No event replay policy is invented. Keep external effects excluded until approved.
Record the data delta/point age; the Leader chooses whether to restore/reconcile and
accept actual data loss. Resume public service only after separate explicit authority
and successful security/QA/runtime gates, never merely because containers are healthy.

## 6. Destination, encryption and MinIO — precise unapproved remainder

### 6.1 Preferred destination design; actual binding pending

Recommend an **already authorized private Azure StorageV2 flat-namespace Hot/LRS
Blob container**, plus an independently accessible encrypted non-Azure copy, with
both required copies current for §4's full failure-domain claim. Hot avoids an
Archive rehydration dependency in the RTO path. LRS is a cost-conscious candidate,
not regional disaster protection, accidental-delete protection or accepted residual
risk. GRS/ZRS may improve infrastructure resilience but do not replace independent
custody, logical recovery history or the same-subscription failure analysis.

**Actual account/container/region/resource ID, data residency, approved cost/credit
envelope, writer/restorer/pruner identities and independent destination are
PENDING_LEADER_INPUT.** No storage account, policy, key or grant is created. Design
permissions are private, scoped least privilege: backup ingestion can write/verify
its sets but cannot delete known-good history; restore can read its authorized sets;
retention deletion requires separate custodian authority. Do not silently use an
account-wide key or VM root identity as a new cloud permission. Prefer existing
scoped identity authentication over reusable URL credentials; no auth method is
claimed implemented or actually granted. The Leader remains key/recovery custodian.

### 6.2 Concrete encryption recommendation — PROPOSED_NOT_EFFECTIVE

Prefer qualifying the existing Ubuntu/OpenSSL toolchain's **CMS authenticated
envelope with AES-256-GCM and a Leader-custodied recipient key** over adding a new
backup platform. Exact OpenSSL build/provider, recipient/key parameters and portable
binary streaming format must be selected/tested with actual supported versions before
mechanism approval. No default CMS cipher is acceptable. Do not use `openssl enc`
as if it supplied authenticated GCM backup envelopes. AES-GCM capability in vendor
documentation does not demonstrate this project's CLI/stream/restore interoperability.
The inspection host has **LibreSSL 3.3.6**, not evidence of the target Ubuntu toolchain.

Keep decryption material/recovery instructions under the Leader's independent custody,
with an independently recoverable protected key copy and reference/fingerprint only
in manifests. Retain required old keys until all referencing sets expire. No actual
key or credential is generated, copied or inspected here. Envelope authentication
does not authenticate the backup producer: a public recipient key allows others to
create ciphertext. F2 therefore additionally requires a trusted operator-controlled
manifest/hash provenance record retained outside the writable backup path; an
attacker-supplied replacement manifest is not trusted merely because its hashes match.
Do not invent a project signing/trust policy or broaden existing unsigned-image risk
acceptance to satisfy this condition. Use authenticated encrypted transfer with
verified hostname/chain; disable neither TLS nor authentication.

Existing **class B compatibility/security review and isolated round-trip gates remain
BLOCKED_BY_MISSING_EVIDENCE**. Needed: synthetic binary/large/empty payload streaming
round trip; wrong/missing key, altered/truncated ciphertext and substituted manifest
rejection; no restored side effects before successful authentication; producer-error
propagation; key recovery without VM/Azure access; measured workspace/time. A plan
does not satisfy these executed gates. No new production dependency is approved.

### 6.3 MinIO mechanism recommendation — PROPOSED_NOT_EFFECTIVE

Preferred first qualification candidate for the existing single-VM volume topology:
an **opaque complete cold copy of every MinIO data/configuration volume**, only while
the exact server and all relevant writers are stopped, restored into fresh isolated
volumes with identical qualified image/topology/UID mappings. Include hidden/system
metadata, not only visible bucket files; inventory separately all external config/
IAM/key dependencies. No selective `xl.meta` editing, live copy, new filesystem
snapshot service or assumption that internal metadata is an exported public format.
This is a candidate, **not vendor support certification or approved recovery mechanism**.

Alternative: a fully version-aware S3 export/import plus bucket/IAM/config exports.
It adds compatibility/identity/dependency reconciliation and must prove preservation
of version/delete-marker/metadata and application-reference semantics on the exact
Community server/MC builds. Replication alone may replicate deletions/corruption and
needs an additional target; it is not automatically a point-in-time backup. A simple
mirror is rejected. AIStor-only documentation does not qualify the shipped Community
artifact. If neither candidate demonstrably captures/restores the complete actual
state, stop full recovery approval; do not scope away versions/IAM/files to obtain PASS.

Required executed qualification includes populated current/noncurrent versions,
delete markers, metadata, multipart/zero-byte objects, bucket/IAM/config/policy state,
restored authorized/denied reads, same credentials by safe synthetic authentication,
and independent PG-to-object reference reconciliation. Existing initializer suites
test provisioning, not full disaster recovery. Record actual server/MC/image hashes;
newer artifact, topology, config or feature contracts invalidate affected evidence.
No production copy/restore or new test infrastructure is authorized by this record.

## 7. Cost feasibility and qualification handoff

Microsoft's public Retail Prices API was queried on **9 November 2026, 23:26 ICT**,
filtered to `Storage`, `eastasia`, `Consumption`, `Hot LRS`, **General Block Blob v2**,
USD; all nine returned meter/tier rows fit one page (`NextPageLink = null`). It reports:
data stored **$0.024 per billing GB-month** at tierMinimumUnits 0 (next tiers start
51,200 and 512,000 GB); writes and list/container operations **$0.05 / 10,000**;
reads/other operations **$0.004 / 10,000**. These are public retail estimates, not the
actual subscription's credit balance/contract bill, regional commitment or new spend.

At constant full set S below the first tier, the selected primary storage component
is at most `14 * S * 0.024`, plus retained R, extra pre-change/incident sets, actual
operations, network egress/retrieval and the independently funded destination.
Multipart requests count as actual operations, not one PUT per whole set. Azure-side
versioning/soft-delete/retention, if later selected, adds retained bytes; do not omit
them from the budget. Monthly full-copy traffic is approximately 90S per path before
retries; backup count is not storage capacity. S includes required versions/config,
not release-package size. Measure source disk occupancy, encrypted output, transfer,
peak CPU/RAM/workspace and restore staging before approving capacity or cost.

| Illustrative constant S, not measured Final usage | Lower-cost 7S primary storage equivalent | Reference 11S Hot/LRS storage | Selected 14S Hot/LRS storage |
|---|---|---|---|
| 1 billing GB | $0.168/month | $0.264/month | $0.336/month |
| 10 billing GB | $1.68/month | $2.64/month | $3.36/month |
| 50 billing GB | $8.40/month | $13.20/month | $16.80/month |

The lower-cost column is a like-for-like storage-meter comparison, not a price for
an operator-owned disk. Higher protection is `23 * S * P_ZRS` if choosing ZRS, with
its actual quote and 360S/month transfers; no current ZRS/egress/independent-storage
price or total bill is claimed. No tax, discount, free transfer allowance, available
student credit or expiry date is assumed. Azure for Students credit/expiration loss
can disable resources; independent recovery must not require the same failed
subscription/portal login or VM-only key. Do not switch to paid operation automatically.

### 7.1 Reproducible qualification plan — NOT_RUN

After separate authorization for appropriate disposable local resources/file scope,
record exact source, image/platform and tool versions; use only synthetic data and
ephemeral isolated credentials. Do not touch current project/production volumes,
reuse real accounts/keys or provision paid infrastructure.

| Case | Required evidence / acceptance |
|---|---|
| Q01 inventory/constraints | Complete PG schema/history and object/version/IAM/config inventory; actual role/grant mapping; every external restore dependency identified |
| Q02 coordinated cut | In-flight HTTP/upload/job/admin writes drain; a deliberate late writer invalidates capture; PG and MinIO describe the same admitted fixture state; quiescence proof and timestamps retained |
| Q03 PG restore | Exact compatible PG17 tools; custom archive readable; isolated restore under F1 migrator and flags; schema/Flyway checksum/data/constraints and bounded grants verified; incompatible version/privilege drift rejected |
| Q04 MinIO restore | Complete §6.3 object/version/delete-marker/system/IAM state restored on exact tested builds; loss/corruption of hidden/configuration or version state fails, never degrades to current-only recovery |
| Q05 encryption/provenance | §6.2 round trips, wrong/missing key, corruption/truncation, producer failure, key-recovery and forged/replaced manifest tests; no partial decrypted application effects |
| Q06 publication/retention | Interrupted/failed upload, stale/missing component and incomplete completion record cannot advance latest-good or prune prior points; independent retrieval/integrity verified; failure at each boundary preserves history |
| Q07 outage/time/resource | Measure incident-to-reopen phases including authorization/replacement/key waits; simulate VM and Azure-dependency loss; prove actual point ages and independent copies, disk/workspace/CPU/RAM/bandwidth/cost feasibility |
| Q08 product/security | Actual applicable Auth/Course/Assessment/Adaptive/Interaction/Admin/file behavior under effective contracts, reference integrity, denied access and private topology; absent feature tests remain unverified; no external event/email/replay escapes |
| Q09 restart/change/demo | Interrupted capture/resumption, overlapping run/prune, incompatible schema/image and pre-demo freeze/retrieval; no automatic rollback/restore/exposure; changed identity invalidates affected evidence |

The full-product case awaits actual approved feature interfaces/implementations,
including CT09 file semantics; a structural byte round trip alone cannot pass Q08.
These are protocols and acceptance criteria, not new scripts, approved test paths,
executed cases or a substitute for the class B approval-level evidence.

## 8. Minimum remaining inputs and publication boundary

Before complete CP-033 approval: a current non-secret Azure/VM evidence packet
(resource identity, disks/capacity, existing permissions/custody/paths and emergency
procedure, version/image-store/immutable running IDs, credit/budget constraints),
plus verified `codemastery.app` certificate/ingress/renewal/origin state. A read-only
operator snapshot or existing authorized inspection session is sufficient; no new
access grants, secrets or repeat decision on L1–L4 is requested. A TCP timeout
alone is not a technical reason to replace the VM or alter firewalls.

Before complete CP-034 approval: actual authorized private off-VM and independent
destination/custody/cost bindings, portable trusted-manifest/key-recovery arrangement,
and executed MinIO/encryption compatibility evidence required by existing class B
gates. Prefer reusing existing approved resources; if none exist, the Leader must
authorize the specific resource/cost/access packet after measured S and total-cost
estimation. No fresh personal decision on the delegated design targets is needed.
Actual incident data-loss/restoration, Final security admission, exposure and
deployment authority are separate future decisions, not approvals sought here.

The narrowly approved portions can be recorded independently in this one CT17 PR.
Issue #70 receives only qualifying checkbox transitions (none for incomplete whole
criteria) and one dated append; Project CT17 remains **Approval Partial** and
**Readiness Bounded**, with updated exact Checkpoint/Gate summary. Status, dates,
classification, owners and other items remain unchanged. No prerequisite Issue
is newly approved. Do not rewrite #231 or its six dispositions, close Issues,
self-approve, auto-merge, bypass protections or merge this PR.

### Sources and actual validation boundary

Primary engineering sources checked:
[PostgreSQL 17 pg_dump](https://www.postgresql.org/docs/17/app-pgdump.html),
[pg_restore](https://www.postgresql.org/docs/17/app-pgrestore.html),
[OpenSSL CMS](https://docs.openssl.org/3.0/man1/openssl-cms/),
[OpenSSL enc](https://docs.openssl.org/3.0/man1/openssl-enc/),
[MinIO mirror limitations](https://docs.min.io/aistor/reference/cli/mc-mirror/)
(capability boundary, **not** Community artifact qualification),
[Azure redundancy](https://learn.microsoft.com/en-us/azure/storage/common/storage-redundancy),
[access tiers](https://learn.microsoft.com/en-us/azure/storage/blobs/access-tiers-overview),
[Students subscription limitations](https://learn.microsoft.com/en-us/azure/cost-management-billing/manage/azurestudents-subscription-disabled),
[Blob pricing](https://azure.microsoft.com/en-us/pricing/details/storage/blobs/) and
[Retail Prices API](https://prices.azure.com/api/retail/prices).

Executed: read-only DNS and failed TCP/HTTPS/SSH/TLS attempts; local release
checksum/OCI descriptor checks; public price-meter retrieval; documentation
inventory/link/arithmetic/diff checks as reported in the PR. No recovery,
encryption, Docker feature, runtime-capacity or production test PASS is claimed.
Known AI use: OpenAI Codex investigated evidence and authored this technical
record/documentation under explicit delegation; no AI runtime is introduced.

## 9. Continuation: attestation, delegated selections and executed qualification

### 9.1 Authority and exact revised disposition

On **10 November 2026**, the same Project Leader explicitly delegated **all remaining
compatible technical decisions** in CP-033/034, including runtime conventions,
certificate lifecycle, destinations, authenticated encryption, key recovery,
MinIO completeness and qualification. The Leader authorized disposable local
synthetic qualification and scoped documentation/Issue/Project/commit/push on
the existing PR #233 branch. This expands r1's technical delegation, not financial,
cloud/OS permission, risk-acceptance, production execution or merge authority.

The Leader supplied the **9 November, approximately 22:59 ICT operational Decision
Packet** in the continuation's answer and explicitly attested its affirmative facts
as current within their stated scope. Its old recommendations, missing values,
unexecuted tests and read-only conclusions are not affirmative facts or new
approvals. The new delegation resolves technical choices that packet had reserved.

| CP | Current revision | Disposition | Exact newly resolved scope / remainder |
|---|---|---|---|
| CP-033 `CT17.F-runtime` | `CT17-F-RUNTIME-2026-11-10-r2` | **DELEGATED_APPROVED** | Attested host/platform/operator facts, §9.2 explicit resource and credit-only financial binding; approved §9.3 custody, locations, TLS lifecycle and capacity/release policies. Actual credit balance/expiry, runtime measurements and Final artifact admission remain execution/acceptance gates |
| CP-034 `CT17.F-recovery` | `CT17-F-RECOVERY-2026-11-10-r2` | **DELEGATED_APPROVED** | §9.4 destination/custody/scheduler design and §9.5 identity-bounded MinIO, CMS, provenance/publication mechanisms; synthetic executed evidence in §9.6. Provisioning, grants, measured operational feasibility and actual F2 product acceptance remain execution gates, not undecided mechanisms |

These r2 revisions extend the exact r1 revisions in §1. They replace only r1's
unapproved technical candidates and the obsolete whole-checkpoint dispositions;
approved r1 objectives, alternatives and predecessor F1/F2 remain intact. No
predecessor contract, production admission or residual-risk disposition is changed.
The corrected Batch 4 register defines CP-033 as **concrete VM/account/budget**
inputs. The Leader subsequently supplied the exact binding below before this r2
revision was published; it is not inferred from technical delegation. CP-034's
new-resource **design** is approved before provisioning under the explicit new
authority. The six ADR008 approvals plus CP-033/034 now total **8/8 technical
approvals**. Neither PR is claimed merged or independently approved.

### 9.2 Evidence reconciliation and blocker classification

**Evidence timing:** the ledger below preserves the r2 publication-time evidence.
Its missing credit balance/expiration statements are superseded for the dated
10 November observation only by [§10.2](#102-leader-attested-azure-ui-observations).
No earlier availability of that evidence is implied.

| Former blocker | Current classification and disposition |
|---|---|
| VM/platform/operator facts previously labelled historical solely by date | **(2) `LEADER_CONFIRMED`**: `vm-codemastery`, reported IP `20.205.56.236`; Ubuntu 24.04 LTS, linux/amd64, 2 vCPU, 8 GiB; Docker 29.9.2, Compose 5.6.1; `azureuser`, existing `sudo docker`, SSH restricted to operator. These are attested facts, not independently inspected grants or cloud allocation |
| TLS/filesystem/source facts in report and deployment record | **(2) `LEADER_CONFIRMED`** within exact scope: `/etc/codemastery` root 0700; TLS directory UID101/0700, `privkey.pem` UID101/0400, `fullchain.pem` root/0644; read-only directory mount. Existing acme.sh TLS-ALPN-01 certificate is for the **old Azure hostname**, expiry 28 January 2027; renewal was not configured. This positively identifies a renewal gap, not a valid certificate for `codemastery.app` |
| Topology/paths, existing release integrity and historical admission | **(2)/(3)** report attestation plus existing source/local verification in §§2–3. All five OCI identities and archive checksum retained. Preview admission stays preview-only; HC-U21 and Final security admission stay unresolved. Source topology is not observed running topology |
| Env/checkout/renewal/resource policy; destination, key and mechanism choices | **(1) delegated technical decisions**, selected/approved in §§9.3–9.5; no repeated Leader choice required |
| Complete MinIO, CMS/keys, corruption/provenance and publication semantics | **(4) mandatory mechanism-level qualification**, executed with the bounded results in §9.6. No claim that every Q01–Q09 case passed |
| Exact served certificate, deployed identities/config/grants, current disk/free space, numeric capacity, actual Final artifacts, scheduler/cloud integration, full F2/QA and measured 24h/48h capability | **(3)/(5) `MISSING_EVIDENCE`**, runtime/implementation/acceptance gates. Failed earlier SSH/HTTPS probes mean inspection unavailable, not incorrect runtime. No repeat probe or replacement VM is required for this decision |
| Subscription/resource group/full VM ID and funding/cost authority | **(2)/(6) resolved by the Leader's supplemental answer below**, with an explicit credit-only/no-new-paid-resource boundary. Credit balance/expiration remain **(3)/(5) `MISSING_EVIDENCE`** before operational reliance; no full $100 balance is inferred |
| New Blob resources/managed identity/RBAC, independent-device provisioning, production backup/restore, actual data loss/security risk, exposure/deployment and merge | **(5)/(6)** separate explicit execution/financial/access/risk gates. No resource, permission, successful deployment or accepted loss is fabricated |

**Supplemental current `LEADER_CONFIRMED` resource/financial binding:**

```text
/subscriptions/72a2b9e9-4347-4912-8c1e-7e927f79ef61/resourceGroups/rg-codemastery/providers/Microsoft.Compute/virtualMachines/vm-codemastery
```

The Leader explicitly selects that existing VM for Final. Funding is **Azure for
Students, $100 promotional-credit entitlement**; reported November 2026 actual
cost **$10.12**, forecast **$31.43**, Cost Analysis resource group
**rg-codemastery**; Azure Budget **not configured**. These are Leader-confirmed
observations, not independently queried Azure billing or verified future funding.
**Remaining promotional-credit balance and expiration are `MISSING_EVIDENCE`.**
No amount remaining is calculated from actual cost or the $100 entitlement.

The explicit financial boundary is **existing granted credit only**: no cost beyond
credit, paid-subscription conversion or new billable provisioning is authorized.
Select fail-closed operational admission: verify balance/expiration and projected
existing-VM/backup costs through Final before execution; if unavailable or
insufficient, stop and retain the independent recovery copy, without switching to
paid operation or replacing the VM. This completes the account/financial **policy
binding**, not proof the VM is funded through Final. An actual Azure Budget/alert
may be configured only with separately authorized account access; a budget is not
assumed to stop spending. New storage design still needs specific cost/resource
permission even if a quote might fit unused credit.

The Leader-decision register's LD-05 is reconciled at clause level by this table;
its earlier blanket “Leader decision” wording does not negate current delegation.
No external handoff/register is edited. Host disk characteristics, SKU and region
remain missing where the supplied report says missing. The primary storage region
selected below is a **new design choice**, not a claim about the VM's region.

### 9.3 Runtime technical selections — approved extension

- Retain §3's one-VM/private-services topology, sole frontend HTTPS 443 ingress,
  same-origin `/api`, exact `https://codemastery.app` origin, P6 separation and
  existing Compose override. No new numeric CPU/memory limit is justified by RAM
  alone: resource admission uses measured startup/peak workload plus simultaneous
  capture/restore workspace against actual available bytes/inodes/RAM/CPU. Record
  numeric budgets from those measurements before deployment; never lower BCrypt
  cost or drop a store to fit. Off-host builds, no on-VM dependency downloads.
- **Select future runtime custody**: root-owned `/etc/codemastery/runtime` 0700,
  `.env` inside it root-owned 0600, single link, trusted non-writable ancestors;
  explicit env-file parsing, never shell-source or print values. Select immutable
  root-owned checkout `/opt/codemastery/releases/<full-source-sha>` (directories
  0755, no other writer), using the existing deployment script and explicit source
  revision. Keep archives under root-only `/var/lib/codemastery/releases`.
  These are approved intended bindings, not claims the directories already exist.
  Root-run script is compatible with the existing Docker/TLS preflight; use only
  independently verified existing sudo authority. `sudo docker` alone does not
  establish authority to run an arbitrary root script. No Docker-group grant,
  permissive key modes, global Git trust exception or new account is selected.
- **Select renewal design using the existing acme.sh TLS-ALPN-01 mechanism**,
  avoiding a new DNS API secret/provider and port 80 ingress. Root custody at
  `/etc/codemastery/acme` 0700; daily systemd renewal check at 03:30 UTC, persistent
  missed-run handling and exclusive renewal/deploy/capture lock. Attempt renewal
  when remaining validity is at most 30 days; expiry warnings at 30/14/7 days,
  any failed renewal is visible immediately. Only a due challenge briefly stops
  frontend to free 443; it must preserve other services/state and restart the
  previous frontend on challenge failure. No unconditional daily frontend stop.
  First qualify the actual pinned acme.sh version and staging challenge, including
  deadline/failure cleanup, before enabling this timer. No DNS-01 plugin or DNS
  grant is assumed. Demo/change freeze prohibits an unplanned challenge outage.
- Validate new leaf SAN `codemastery.app`, trusted full chain, key correspondence,
  validity and permissions in root-only staging before atomically replacing
  `fullchain.pem`/`privkey.pem` under the existing directory bind. Keep key UID101
  0400; do not replace only a bind-mounted single file. Nginx config test and
  reload/restart then verified served-certificate readback must pass; retain the
  prior unexpired pair for recovery. No validity of the old-hostname certificate
  is extended to this domain. No TLS secret or actual renewal is changed here.
- Keep P8 health/start/restart and P9 update/rollback rules. Stop on config/init/
  migration/health failure; do not expose public health or MinIO. Pre-update
  qualified coordinated recovery point and schema/image compatibility evidence
  are mandatory. An image rollback cannot undo Flyway or accept actual data loss.
  Final feature grants remain consumer-owned P6 extensions, never blanket grants.

All runtime technical choices and the actual account/financial policy binding are
resolved. CP-033 is technically approved; unknown current credit/expiry remains
an explicit execution gate, as do actual measurements, grants and Final admission.

### 9.4 Recovery destinations, scheduling and custody — approved design

Retain **24h RPO / 48h RTO targets, 8h coordinated capture, 3 intraday + 7 daily +
4 weekly qualified points** and pre-change/migration/demo points. Daily capture
leaves no publication/missed-run margin; 2h capture adds unsupported overhead.
No synthetic timing is promoted to a demonstrated objective. Retention selects
the newest qualified cut in each class, deduplicates shared points and retains
extra incident/pre-change sets until explicit compatible-recovery release.

Select **Azure StorageV2, standard block Blob, flat namespace, Hot/LRS, East Asia**,
private containers, HTTPS/TLS1.2+ only, public blob access disabled. Restrict
account network rules to the verified VM/operator egress addresses before use;
no paid private endpoint or new public application listener is selected. Prefer an
existing authorized matching account; otherwise this is the exact service design
to provision **only after** separate resource/cost authorization. Do not claim an
account name, container resource ID, VM region or remaining student credit.
LRS plus an independently retrievable non-Azure copy is proportionate for this
single-VM academic system; GRS/ZRS alone still share cloud/account failure and add
cost. Archive-tier rehydration and a new permanent backup service are rejected.

Separate candidate ingestion from known-good custody: a VM managed identity may
write/read only a **staging container**, with no access to the archive container,
role management, keys or history deletion. The off-VM Leader-operated publisher
uses Entra authentication scoped to the **archive container** for write/readback;
restore is read-only, pruning a separate explicitly invoked custodian operation.
The publisher uses unique set IDs and conditional create (`If-None-Match: *`),
never replacement of an existing point. RBAC write access alone is **not** claimed
to enforce append-only/WORM; the qualified client protocol, trust ledger and
independent custody are required. Actual RBAC/conditional-write tests are a
provisioning gate; no account-wide key, public SAS or new grant is authorized.

The **independent destination is the Leader's workstation filesystem** at
`~/CodeMasteryRecovery/sets` (owner-only directory 0700, encrypted sets 0600),
outside the VM and Azure account. Copy **every** qualified point, not only weekly
points. Do not call two directories on one disk independent storage; the local
two-directory qualification tests the protocol only. Actual device/free space,
backup custody and availability must be bound before operational reliance. No
new device purchase is authorized. The independent copy, tools/artifacts, trust
record and escrow key must be retrievable with VM/Azure unavailable.

Select root-only `/run/codemastery` 0700 and
`/run/codemastery/operations.lock` for shared exclusive operation coordination.
Select native **systemd timer** capture at 00:00/08:00/16:00 UTC with Persistent
missed-run handling, one exclusive capture/renewal/deploy/prune lock and no catch-up
burst. Select an operator-workstation scheduled publisher polling every 15 minutes;
it must authenticate/decrypt to protected staging, verify the complete set, retain
an independent copy, publish the completion record last and advance its external
trust ledger. Sleep/offline/locked-key conditions are explicit failures/age warnings,
never successful backup. No permanent application service is added. Persistent
timers do not prove uptime. Capture/publisher integration needs later implementation
and failure qualification; workstation availability is part of Q07's real bound.

Use journald plus private owner-only run/status records at
`/var/lib/codemastery/backup` on the VM and `~/CodeMasteryRecovery/ledger` off-VM.
Record each deadline/result, both destination point ages and operator acknowledgment;
failed/missing/late jobs alert the operator immediately, warning at age 16h, breach
above 24h. No new email/SMS/push product channel is selected. The primary operator
checks private status daily and before important demos; notification delivery and
response availability need actual operational qualification. No success on a
missed poll or unobserved monitor. Lock contention skips with a visible failure,
not concurrency; stale-lock recovery is manual after inspecting process/state.

Qualified primary history is at most **14S + R**, independent history another
14S + R; allow separately for up to two staged full sets, protected plaintext
validation workspace, failed/pre-change/incident sets and transient transfer copies.
Primary base storage remains `14*S*$0.024` at the cited public first-tier meter;
staging up to `2*S*$0.024` is additional, as are actual requests/egress/local capacity.
Approximately 90S/month per full-copy path excludes retries/extras. This is a cost
model, not a spending ceiling or statement that credit/resources exist. Provisioning
requires measured S, total estimate, actual funding/expiry and explicit cost authority.

### 9.5 Qualified mechanisms and security boundaries

**MinIO:** approve opaque complete **cold** `/data` volume capture, including
`.minio.sys`, restored with numeric UID/GID/modes into an empty volume with the
**identical server binary/image, topology and external configuration/dependencies**.
Stop server and every writer, prevent restart, then tar the entire volume; no
live copy, selective internal-file edit, cross-version import or current-only mirror.
Bind approval to server SHA256
`3c3d615241314c531e15e57485c053e60dc1af04e87b20424d2db8bdec8a49b2`
and mc SHA256
`025691d43adb653c6ce34723c76450153f2c024bfa232edaca6906fe3e221809`.
The tested binaries, image identities and fixture scope are below. This is executed
project compatibility evidence, **not vendor support certification or Final security
admission**. A changed build/topology/feature or external KMS/identity/replication
dependency requires affected qualification again. Actual dependency inventory is
mandatory before any production set; never infer unused KMS/versioning from silence.

**Encryption:** approve OpenSSL 3 CMS `AuthEnvelopedData`, explicit AES-256-GCM,
RSA-3072 recipient with OAEP/SHA256 and MGF1/SHA256, binary streaming envelope.
Each encrypted component plaintext starts with the exact 7 bytes `CMREC1\n` then
the component bytes; record format/version/length and plaintext/ciphertext SHA256
in the signed manifest. This mandatory framing handles the tested build's failure
on raw empty input. Test empty *payload*, binary and large inputs; do not silently
publish a zero-length failed envelope. No `openssl enc`, default cipher or CBC-only
payload encryption is approved. Algorithm/provider/version must be recorded and
qualified on the execution host; host LibreSSL is not interchangeable evidence.

Use pipefail and every producer exit status. Decrypt only to owner-only **tmpfs
staging**, bounded by measured capacity; OpenSSL can write unauthenticated plaintext
before its final failure. No `decrypt | tar`, application import or side effect
until full success, expected format/length/hash checks and safe path validation.
For larger-than-proven tmpfs capacity, stop: encrypted scratch/disk redesign needs
separate qualification. No plaintext production spill to an ordinary host directory.

Recipient private key stays off the VM: Leader workstation
`~/CodeMasteryRecovery/keys/recipient.encrypted.pem`, PKCS8 encrypted using
AES-256-CBC/PBKDF2-HMAC-SHA256, 600,000 iterations, strong generated independent
passphrase and owner-only files/directories. This key-wrap choice is not payload
authentication; wrong/corrupt keys must fail GCM/manifest checks. Workstation
Keychain supplies the passphrase through a private descriptor for authorized
scheduled validation, never argv/log/env. Qualify the actual Keychain/scheduler
binding later without real secrets in test evidence. An encrypted offline key
copy on separate Leader-held media plus separately recoverable passphrase is
required before reliance; neither media nor key availability is asserted here.
Retain old keys until all referencing sets expire. The VM holds only the pinned
public recipient certificate at `/etc/codemastery/backup/recipient.crt`.

**Provenance:** a separate RSA-3072 producer signing key, root-only 0400 at
`/etc/codemastery/backup/producer-signing.key`, signs the exact manifest bytes
using RSA-PSS/SHA256, salt length digest. This grants no runtime application access.
The Leader pins its public-key fingerprint independently in
`~/CodeMasteryRecovery/trust`, together with an off-VM monotonic point/cut ledger.
Authenticate any rotation out of band; no key from an uploaded set is trusted.
The signed manifest binds source/config/image/tool identities, all required
components/dependencies, lengths/hashes, conservative UTC cut, writer-exclusion
evidence, custody references and qualification status. AEAD alone does not prove
producer identity. A valid old signature does not authorize rollback: compare
against the independent ledger and require explicit Leader recovery-point choice.
Source/VM compromise cannot be detected merely by its own signing key; protect
previous off-VM history and stop on suspected compromise for separate security
investigation/risk authority. No residual-risk acceptance is supplied here.

**Transfer/publication/retention:** certificate-verified authenticated HTTPS;
Azure uses Entra tokens, not the fixture's synthetic bearer token. Verify signatures
against the pinned key before trusting manifest filenames; reject duplicate keys,
absolute/traversing/link paths, unknown/missing components, wrong lengths/hashes,
partial/corrupt ciphertext and stale/substituted sets. Complete readback and local
authenticated validation precede completion-marker publication and external-ledger
advance. Interrupted upload, marker/ledger disagreement, full destination, failed
resumption or missing independent copy cannot replace the prior qualified point
or trigger prune. No age-only storage lifecycle rule may delete qualified history.
Pruning requires explicit custodian invocation, no active lock, both destination
coverage and recoverable key/artifact dependencies; keep the prior point on failure.
The local reference model qualifies protocol semantics; actual Azure/OS implementation
must rerun these faults. It is not falsely marked implemented or cloud-tested.

### 9.6 Actual qualification record — 10 November 2026

**Historical Q03 limitation:** this original run did not establish P6-compatible
restore privileges; see §11's correction and independently executed replacement.
The other cases and all original result hashes retain their stated scope.

**Environment:** local Docker Desktop 4.93.0 / Engine 29.8.1, Linux arm64 kernel
7.0.14-linuxkit, `linux/amd64` images emulated. Internal-only disposable network
`ct17q-20261110a`; **zero published ports**, six task-owned containers/four task-owned
volumes, all removed after evidence capture. No production data, credentials,
volumes, cloud resource, dependency download or public service was used.

| Artifact/tool | Actually executed identity |
|---|---|
| MinIO | RELEASE.2026-10-23T20-06-51Z, commit `76c06ff1d2cc844050fdeb1d54bf8fa70db72278`; image config `sha256:271fe69df3d41e8431dddc158678e920b1acd05cb3ebf8c40f46323131eddd16` |
| mc | Project source `7394ce0dd2a80935aded936b09fa12cbb3cb8096`, patched dependencies; reports DEVELOPMENT.GOGET, Go1.27.3; config `sha256:41ff90c8099be21d6626a0f9f602f3563b5fcd8585ddcd3598b7178b9c015ff2` |
| PostgreSQL/client | 17.11 Alpine; config `sha256:43ebe9dbb13e6afd71c8e643f0d47c8ae2abd438f899088aa1bcc130410da74a` |
| OpenSSL/tar helper | OpenSSL 3.0.13, Ubuntu package build `3.0.13-0ubuntu3.16`; existing backend config `sha256:e492780c800084e8a763bdd53d6f4a0955bbba4061c15043a38ebd82da6c7457` |
| HTTPS reference | Existing Python3.13-slim arm64 config `sha256:2d44738ebf4ce5f63b9d9ea871e9c7cfdc0426f7b0c97cac915fd88ded1d86bb`; ssl OpenSSL3.5.7 |

Config IDs above are the local Docker identity kind; they are **not** replacements
for §2.3's OCI manifests or proof of Final admission. Source baseline is
`4cb600e425bc39e182d5f9e9fac399a9579d025d`; no implementation files changed.

Inputs: fresh synthetic root/user/service-account credentials and ephemeral RSA
keys; recovery/private/locked buckets; two versions of `versioned.bin`, a deleted
object and marker, zero-byte object, metadata/tag fixture, 17 MiB binary object,
5 MiB in-progress multipart part, versioning/lifecycle/quota/governance-lock
settings, user/group/read-policy/service-account dependencies. No external KMS,
LDAP/OIDC, replication or real secrets. Applied actual V001–V006 SQL; added
`synthetic_history` with migration filenames/SHA256 and a `recovery_refs` row/
identity sequence linking the current object's SHA256. **No Flyway-engine history
or actual feature execution is claimed.** Conservative cut: **03:48:29 UTC**.
PG dump **67,673 bytes**; complete cold tar **23,275,520 bytes**. Framed encrypted
components **68,363** and **23,298,874 bytes** respectively; not measured Final S.

| Case | Actual result and exact limit |
|---|---|
| Q01 | **PASS for fixture inventory**, including hidden tree and IAM/config dependencies; actual Final/external-dependency inventory **NOT_RUN** |
| Q02 | **PASS for isolated fixture boundary**: disconnected writer client, read-only DB default, no other active DB writer; attempted late INSERT rejected; cut held through dump/cold capture. A read-only default is not a security fence against a privileged writer. Actual HTTP/jobs/admin drain and hostile/privileged-writer detection **NOT_RUN** |
| Q03 | **Historical PG17 fixture PASS, not P6-qualified**: actual project SQL/constraints, fixture history/data/sequence/owner/role equivalence after restore, custom restore flags, runtime SELECT allowed/ordinary DDL denied. The migrator had excess database-level CREATE; §11 supplies new P6 qualification. Original Flyway-engine checksum validation, incompatible-version and privilege-drift injection were **NOT_RUN** |
| Q04 | **PASS on exact tested fixture/builds**: full tree bytes/numeric ownership/modes matched before boot; seven listed versions/marker, old and current reads, tags/metadata, zero/large objects, bucket settings, IAM/group/service-account credentials and allow/deny behavior matched; pending multipart part survived and completed with correct bytes. CORS PUT returned **501 NotImplemented**, no CORS state was installed; no CORS-recovery PASS. Actual external-state variants require requalification |
| Q05 | **PASS on tested OpenSSL**: empty framed payload, binary and 16 MiB round trips; actual PG/tar encrypt/decrypt byte equality; wrong/missing key, corrupt/truncated envelope, wrong escrow passphrase, forged manifest/substituted trust key and failed producer rejected. Recovered from encrypted PKCS8 copy. Actual workstation Keychain/offline-media availability **NOT_RUN** |
| Q06 | **PASS local protocol model**: eight interruption boundaries preserve prior point/ledger; corruption/truncation/missing/stale part, replaced manifest/signature, incomplete marker and hidden-state omission rejected before restore; path/duplicate-key rejection, exclusive lock and second-directory readback. Actual Azure/RBAC/atomic cloud behavior **NOT_RUN** |
| Q07 | **NOT_RUN** for real RPO/RTO, replacement compute, actual cost/capacity/operator availability and physical independent-copy loss. Fixture bytes/command timings recorded, not a 24h/48h guarantee |
| Q08 | **NOT_RUN** for actual Auth/Course/Assessment/Adaptive/Interaction/Admin/file QA/Flyway; fixture PG object-reference SHA and storage/private-network denials passed. No full-product F2 PASS |
| Q09 | **PASS only local overlap/provenance/failure preservation** above; actual restart/timers/renewal/keychain/pre-demo/product compatibility **NOT_RUN** |
| Transport | **PASS isolated HTTPS** encrypted component byte equality; trusted chain/hostname required, unknown/wrong CA, wrong hostname and wrong bearer token rejected. Actual Azure Entra endpoint/permissions/Internet transfer **NOT_RUN** |

Evidence is preserved locally at
`/private/tmp/codemastery-ct17-qualification-20261110/`: sanitized `commands.jsonl`
(actual UTC commands, exits, durations, stdout hashes/errors), script/result
`evidence-index.json`, restored inventories and private synthetic input archives.
No private key, credential, dump or binary artifact is added to Git. Selected
result SHA256 values are:

```text
restore-result.json      5f678d62d085bce59fa470fb47d890ef3d1fdf3543103355db06c06d8a972c06
crypto-result.txt        476e1c7ddfc1810f5fd2506423f134c3340adc87eda694db39bd8e4a9c560527
publication-result.json d09cbd699a71f24b6ec83304c252e43b2c22226bb61cda380fde63488ea97e50
tls-result.json          cf15cac17593eb53bcb665743b280030f91f7baf04bbfa514812173e74a42020
```

The preserved journal includes unsuccessful harness attempts (read-only mc home,
synthetic service-key length and display URL/action-order comparison), corrected
without changing the production images; these are not recovery PASS assertions.
The raw-empty CMS failure and unsupported CORS call remain explicit findings.
The final bounded results above passed; no blanket Q01–Q09 PASS is asserted.
See [testing reproduction](../testing.md#ct17-r2-executed-mechanism-qualification--10-november-2026)
for actual command shapes and fixture interpretation.

### 9.7 Remaining execution gates and publication

**Current-state correction:** [§10](#10-post-pr231-integration-and-azure-evidence--10-november-2026)
records the subsequently merged predecessor and newly supplied credit evidence.
The original publication text below remains dated history; actual execution still
requires fresh financial/capacity/access qualification and separate authorization.

**No remaining CP-033/034 technical approval blocker.** The Leader's supplemental
resource ID and explicit credit-only/no-new-billable-resource boundary resolve the
last actual account/financial policy input; runtime and recovery r2 are both
`DELEGATED_APPROVED`. Existing VM/platform/domain/operator/resource/funding facts
need no reconfirmation. Before execution, verify actual remaining credit/expiry
and feasibility through Final, current disk/workspace/capacity, config/grants,
served certificate/renewal and exact Final artifact admission. Unknown balances
or failed acceptance stop execution; they are not treated as approved spending or
as unresolved technical choices. No guaranteed availability is inferred.

**CP-034 execution remainder:** bind/provision the selected private destinations
and independent device, authorize costs/grants, protect/recover real keys, implement
and qualify the actual timers/publisher/cloud fault handling, inventory actual
MinIO external dependencies and requalify changed Final builds. Then execute F2,
real Flyway/grants/features/security QA, representative capacity/capture/restore,
both-copy age and whole-incident RTO drill before claiming recovery ready. These
are implementation/operational gates; no technical mechanism remains unselected.
Current Final artifact admission/risk provenance is separate and still required.

Issue #70 stays open; only exact fulfilled existing checklist conditions may tick.
Project Approval records **Delegated approved** for the completed technical scope,
Readiness remains **Bounded**; Checkpoint/Gate summary point to these r2 decisions. Status and all other metadata remain.
PR #233 gets one focused continuation commit, no duplicate ADR/PR. PR #231/six
decisions and all predecessors remain unchanged. Independent CODEOWNER/last-push
review, exact-head required CI/security and protected integration still apply.

Additional primary references: [Azure Entra Blob authorization](https://learn.microsoft.com/en-us/azure/storage/blobs/authorize-access-azure-active-directory),
[custom RBAC](https://learn.microsoft.com/en-us/azure/role-based-access-control/custom-roles),
[Docker volume capture](https://docs.docker.com/engine/storage/volumes/),
[OpenSSL CMS](https://docs.openssl.org/3.0/man1/openssl-cms/).
Documentation/vendor capabilities support design review, not the unexecuted cloud
or production claims above. Codex performed this synthetic qualification and
authored the continuation under the stated delegation; no AI runtime is added.

## 10. Post-PR231 integration and Azure evidence — 10 November 2026

### 10.1 Integration and unchanged technical authority

Under the Project Leader's explicit **“CodeMastery — PR #233 Post-PR231
Integration, Conflict Resolution and Azure Evidence Reconciliation”** instruction,
the existing PR #233 is synchronized with freshly fetched main
`b6fa24b396d08e323ea54882af0a008077301ab6`. PR #231 merged into that commit on
10 November 2026. ADR008 and its six delegated technical decisions, including
`CT34-FIXTURES-2026-11-10-r2` in §10, are preserved unchanged. CP-035 continues
to consume that amended fixture contract; its own revision remains r1.

CP-033 **`CT17-F-RUNTIME-2026-11-10-r2`** and CP-034
**`CT17-F-RECOVERY-2026-11-10-r2`** remain **DELEGATED_APPROVED**, under §9's
existing delegation. This addendum neither reopens them nor issues a new revision.
Batch 4 is **8/8 technically approved by delegated authority**. ADR008 is integrated;
ADR009 remains pending PR #233's own independent review and protected integration.
The [current eight-checkpoint inventory](README.md#current-batch-4-checkpoint-inventory--10-november-2026)
distinguishes these states. Earlier open-PR, partial-count and missing-evidence
statements in §§1–9 and dated handoffs retain their historical meaning only.

The sole textual rebase conflict was the appended `docs/testing.md` handoffs:
ADR008's runner/CP-030 r2 material and ADR009's executed mechanism record both
remain. The other overlapping documents were checked after automatic merging.
No approved content, predecessor contract, runtime implementation, main's unrelated
backend changes or qualification result was replaced by this synchronization.

### 10.2 Leader-attested Azure UI observations

**Provenance: `LEADER_CONFIRMED`, not independently Azure-API-verified.** The
Project Leader explicitly attests the following Azure Portal observations on
10 November 2026. Only the minimum authorized identity/evidence is recorded;
screenshots, student email and billing-account details are not included.

| Source and observation time | Attested observation | Scope / interpretation |
|---|---|---|
| Azure subscription/VM views, 10 November | Subscription **Azure for Students**, status **Active**; Leader role **Owner**; **vm-codemastery** in **rg-codemastery**, same full VM Resource ID already recorded in §9.2 | Confirms the supplied account/resource identity within the attestation; does not prove current VM disk/capacity, OS permissions or resource-specific operating costs |
| Education → Overview, approximately **11:05 ICT, 10 November** | Available promotional credit **$88 out of $100**; **355 days** until expiration as displayed; expiration **31 October 2027**; November costs **$12.00** | Dated UI evidence of balance/expiration; neither an API retrieval nor a guarantee of future balance/availability |
| Earlier Azure Cost Analysis observation, same date | Actual cost **$10.12**; November forecast **$31.43**; Azure Budget **None** | Separate UI observations: do not add $12.00 and $10.12, derive another remaining balance from them, or treat forecast as incurred/guaranteed cost |
| Separate Azure Sponsorships website, same date | “This account does not have an active Sponsorship” | Does not establish expiry of the student offer: the actual subscription was shown Active and Education Hub separately supplied its balance and expiration |

**Current evidence disposition:** credit balance and expiration are now supplied
as dated **LEADER_CONFIRMED** evidence. The former `MISSING_EVIDENCE` label for
these two observations in §§9.1–9.3 and earlier handoffs is **HISTORICAL_ONLY**;
it is not an unresolved request for the already supplied 10 November figures.
The original r1/r2 records are retained rather than backdating the new evidence.

The course Final deadline, **13 December 2026**, precedes the displayed expiration.
That comparison and the $88 balance do **not** establish adequate credit through
Final, measured capacity, operational funding approval or production admission.
Before execution, refresh applicable balance/expiry evidence and assess actual
VM/storage/network costs, forecast-to-Final feasibility, selected backup costs,
available disk/workspace/capacity and required runtime access. Stop if unavailable
or insufficient; do not silently replace the VM or switch to paid operation.

The unchanged financial policy permits **existing Azure for Students promotional
credit only**. Paid-subscription conversion and spending beyond approved credits
remain prohibited. New billable storage/infrastructure/resources or privilege
grants require separate authorization within that credit-only boundary.
A confirmed balance grants none of these and
supplies no residual-risk or actual incident data-loss acceptance.

### 10.3 Qualification and independent-review boundary

§9.6's Q01–Q06 and Q09 retain only their specifically documented fixture/mechanism
PASS scopes; none is promoted to cloud or production evidence. Actual cloud/RBAC,
real scheduler, Keychain and TLS renewal integration remain unexecuted. Q07 real
RPO/RTO and Q08 full-product F2/real Flyway-engine/feature recovery remain **NOT_RUN**.
Current VM capacity/TLS/config/grants, exact Final artifacts/security admission,
real recovery readiness and deployment authorization remain separate execution gates.

Only documentation/metadata reconciliation and safe local document checks occur
in this continuation. No Azure API/VM operation, deployment, production restore,
resource provisioning, secret inspection, new grant or mechanism requalification
is performed. Self-check by this modifying session is not independent approval;
an eligible independent reviewer must review PR #233's new exact HEAD before
integration where required. No protection is bypassed and no merge is authorized.
Known AI assistance: OpenAI Codex performed this documentation reconciliation.

## 11. Q03/P6 evidence correction — 10 November 2026

### 11.1 Authority and original limitation

Under the Leader's **“CodeMastery — PR #233 Targeted P2 Remediation: PostgreSQL
Q03 / P6 Compatibility”** instruction, independently validate and correct F1/P2
using disposable synthetic PostgreSQL qualification and these two documentation
records. This corrects evidence, not the contract: CP-033/034 keep their exact
2026-11-10-r2 revisions, ADR008/CP-030 r2 and all predecessors remain unchanged.

The original harness definitions and execution outputs confirm source and target
`GRANT CONNECT, CREATE ON DATABASE fixture TO fixture_migrator`, plus ownership
of `public`. Database CREATE violates the effective `role_policy_sql()` for both
roles, including indirect grants; `rolcreatedb=false` does not check that ACL.
Ownership of only `public` is permitted for the migrator and is not itself F1.
The original restore succeeded with broader privileges; its hashes/history remain
historical evidence, **not retroactive P6 compliance**. The original temporary
harness is unavailable. The following is a **new independent reproduction**.

### 11.2 Corrected fixture and actually executed results

Executed **10 November 2026, completed 09:39:59 UTC / 16:39:59 ICT**, from source
`11cee7ed00c604f410535fb5897fcb03781bc983`; Docker Engine **29.8.1**, local arm64
host running the same emulated linux/amd64 PostgreSQL **17.11 Alpine** image ID
`sha256:43ebe9dbb13e6afd71c8e643f0d47c8ae2abd438f899088aa1bcc130410da74a`.
Server, `pg_dump` and `pg_restore` reported 17.11; their binary SHA256 values are
retained in `src-binary-identities.txt` and the evidence index. No substitute
image/pull, production data or new dependency was used.

Run `cm-q03-p6-20261110-5fc99e18` used two fresh containers with `--network none`,
no published ports, no persistent/bind mounts and tmpfs-only database storage.
Bootstrap owns the database; both login roles have no elevated role attributes,
memberships, database ownership, database CREATE or TEMPORARY. PUBLIC database
rights and PUBLIC schema rights were revoked; explicit CONNECT is supplied to
both roles, public-schema ownership/USAGE/CREATE only to the migrator and USAGE
only to runtime. No default table/sequence grant supplies runtime access.

The harness sources only function definitions from unchanged `deploy.sh`, emits
and executes its actual `role_policy_sql()`, `application_grants_sql()` and
`runtime_privilege_violations_sql()` (present/complete), without running deployment.
All six actual V001–V006 SQL files run as migrator. Synthetic users/course/content
and a file-object reference use the actual schema. A clearly synthetic digest
table in the migrator-only `flyway_schema_history` inventory slot records migration
filenames/SHA256; its fixture layout is **not Flyway's engine-managed history**.

| Executed assertion | Actual result |
|---|---|
| Role flags, recursive membership policy, effective database/schema ACLs, defaults and ownership before/after restore | PASS; both database CREATE/TEMPORARY false, migrator schema CREATE true, runtime false; zero memberships/elevation |
| Original direct CREATE grant, PUBLIC/inherited CREATE, runtime membership, database ownership and post-restore PUBLIC drift | Six expected policy rejections, SQLSTATE `P0001`, psql exit 3; transactions rolled back |
| Both roles' unauthorized schema/role/temp creation; runtime table creation, history/material-payload reads, unapproved UPDATE and future-table/sequence access | Expected `42501`, psql exit 3; authenticated identity checked before each probe |
| Migrator schema DDL, actual migrations and custom dump; restore with `--no-owner --no-acl --exit-on-error`; approved runtime SELECT/INSERT/Course row lock | PASS, exit 0; restore target empty and P6-qualified before import |
| Restored structure/constraints/indexes, all table data, synthetic digest history, object key, ownership and exact runtime matrix | PASS; source/target comparisons equal; restore granted no runtime table rights before separate matrix provisioning |
| Duplicate, CHECK and FK violations on source and restored target | Expected `23505`, `23514`, `23503`, exit 3; constraints remain enforced |

**75 positive/negative cases passed**: 13 positive, 50 permission denials, six
policy rejections and six constraint rejections. An additional deliberate history
grant produced matrix violations and was rolled back. Current project migrations
define zero sequences; that equality was checked, and transactional sequence
creation/use plus runtime denial was tested without adding an unapproved object
to the recovery archive. Final role policy and complete matrix checks passed.
Both exact task containers were removed and independently observed absent.

### 11.3 New evidence and retained limits

Private local evidence: `/private/tmp/codemastery-q03-p6-20261110/`, mode 0700.
`qualify.py`, emitted policy SQL, authenticated command/SQLSTATE journal, ACL/role
snapshots, source/target comparisons, result and index remain available there.
No credential value is retained in the journal or added to Git; only synthetic
fixture archives exist locally. New hashes, separate from §9.6's original hashes:

```text
qualify.py    391db94ef1bff2f85b8d2b821fd511b22c5fff633aeff0c27ae1d7fa12f1b153
result.json   4b3ca367c0ae64ca94d10c7f413c1cd048e59fe613dce68bdb05484e899cc86c
commands.jsonl 0dcda799e66623f8bc30fa8c8007a4aa89eda8cfb598ad0fc99bd43e64c569d0
postgres.dump 9fb8d496b12efd7a46c4e7f4427e45b395970a1d46ede2667c882b9e1d5f1634
```

The new dump is **68,313 bytes**. `initial-attempt/` preserves the first attempt:
qualification reached its final checks, but the overall command failed on a
case-sensitive Docker cleanup-message assertion. The remaining exact task
container was reconciled/removed, the assertion corrected, and the complete new
run above passed including cleanup. This intermediate command is not claimed PASS.

Q03's tested P6 mismatch is corrected; independent exact-new-HEAD review remains
required. Flyway-engine verification, incompatible-version recovery, full-product
F2/Q08 and real RPO/RTO/Q07 remain **NOT_RUN**. No MinIO/CMS/cloud/TLS/Keychain/
scheduler requalification or new production/financial/risk authority follows.
OpenAI Codex reconstructed and executed this focused qualification under the
stated instruction. See the [focused reproduction record](../testing.md#q03p6-focused-reproduction--10-november-2026).
