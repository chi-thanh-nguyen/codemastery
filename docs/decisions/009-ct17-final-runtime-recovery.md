# ADR009: CT17 Final Runtime and Recovery — Bounded Approvals

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
