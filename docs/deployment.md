# CodeMastery Deployment Preparation

**Batch 4 disposition — 9 November 2026:**
[ADR008 §8](decisions/008-batch-04-contract-approvals.md#8-ct17-blocked-scope-and-smallest-remaining-decisions)
retains CP-033 CT17.F-runtime and CP-034 CT17.F-recovery as
**PENDING_LEADER_INPUT / BLOCKED for approval**, without an approved new revision.
Actual resource/custody/exposure facts and recovery objectives/authority are still
required; full MinIO recovery/encryption qualification and exact-image risk/admission
gates remain separate. The six independently approved testing decisions do not
approve these two checkpoints or any deployment. Prior decisions/history below
are preserved; no operational binding, budget, secret or numerical promise is selected.

## Current status and reproduction summary

Status as inspected on 31 October 2026 at `main`
`614f3260a2df640dcfa47c166e4a5325f56bd1a7`. This summary describes what exists; it
approves nothing. Approvals, authority boundaries and evidence are in the dated
records below, which keep the wording of their date. Changes in open pull
requests are not described here.

The 1 November repository integration below combines the Chainguard MinIO
qualification and the private health-probe remediation. MinIO is functionally
qualified but not production-admitted; the backend probe is fixed and tested in
the repository/CI, but not yet verified on the Azure VM. The 2 November
[Console hardening](#dp01-minio-console-hardening--2-november-2026) disables the
embedded MinIO Console in the release override only; its residual-risk acceptance
is `PENDING_LEADER_APPROVAL`. The 2 November
[pre-release security hardening](#dp01-pre-release-security-hardening--2-november-2026)
pins the frontend and backend base images and Alpine updates, makes the release
Nginx configuration read-only to its runtime user, removes the backend's
setuid/setgid programs, and runs every release service read-only with
`no-new-privileges` and dropped capabilities, enforced by `deploy.sh`. Its MinIO,
PostgreSQL and JDK findings remain `PENDING_LEADER_APPROVAL`; nothing is admitted. The dated records retain
their original evidence and limitations; DP01 remains incomplete.

### What exists and what has been shown

| Level | Status | Evidence |
| --- | --- | --- |
| Design and approval status | The one-VM Docker Compose deployment is approved (ADR002 CT17.I-scope and the approved architecture). The frontend Nginx as the only public HTTPS ingress, and the TLS bindings, are recorded in the dated CT17 records as `LEADER_APPROVED`. Provider, VM, TLS/DNS, secrets, production origin and public access remain Leader-reserved (ADR002 CT17.I-runtime is `PROPOSED_LEADER_RESERVED`) | [B2](#b2--approved-engineering-ingressconnection-bindings), [DP01 HTTPS ingress integration](#dp01-https-ingress-integration--31-october-2026) |
| Deployment tooling | Implemented and merged | `infrastructure/vm/deploy.sh` (PR #110), `infrastructure/vm/docker-compose.vm.yml` and the frontend `release` target (PR #118) |
| Tested configuration | The tooling passes its CI jobs on the GitHub runner, against real containers but not the VM | Six `VM …` jobs, see [testing.md](testing.md#ci). The five deployment-script and ingress jobs passed on `614f326` in [run 41191743079](https://github.com/chi-thanh-nguyen/codemastery/actions/runs/41191743079); the MinIO initializer job is described in [DP01 MinIO Community initializer](#dp01-minio-community-initializer--31-october-2026), [DP01 Chainguard MinIO release-server candidate](#dp01-chainguard-minio-release-server-candidate--1-november-2026) and [DP01 minimal MinIO server candidate](#dp01-minimal-minio-server-candidate--1-november-2026) |
| Actual deployment | **None recorded.** No `preflight`, `load`, `provision` or `deploy` on the VM is recorded in this repository | — |
| External / lecturer access | **Not verified.** No independently verified public URL exists | DP03 #72 is open |

The Leader has reported a provisioned Azure VM, an intended origin and a TLS
certificate layout ([inputs](#authority-inputs-and-boundary)). They are reported
inputs, not verified results, and do not authorize public exposure.

### Deployment artifacts

| Path | Responsibility |
| --- | --- |
| `infrastructure/docker-compose.yml` | The local base file, used as is; `deploy.sh` always applies it first |
| `infrastructure/vm/docker-compose.vm.yml` | Release override: removes local ports and builds, publishes only the frontend on 443, mounts the TLS directory read-only, sets the backend release environment, disables the MinIO Console (`MINIO_BROWSER=off`), applies the runtime hardening (read-only root, `no-new-privileges`, dropped capabilities, listed tmpfs) and fixes the `codemastery.invalid/*:release` image names |
| `frontend/Dockerfile`, `release` target | Builds the bundle with `VITE_API_BASE_URL=/api`, terminates TLS, proxies `/api` to the backend and bakes an HTTPS healthcheck. Requires the `PUBLIC_ORIGIN` build argument |
| `infrastructure/vm/Dockerfile.minio-server` | Experimental minimal MinIO server built from the Chainguard candidate pin. Functionally qualified natively in CI ([run 41308772749](https://github.com/chi-thanh-nguyen/codemastery/actions/runs/41308772749)) after a first native run failed on directory modes. Not admitted, and not referenced by the override or `deploy.sh` ([record](#dp01-minimal-minio-server-candidate--1-november-2026)) |
| `infrastructure/vm/deploy.sh` | Operator script with the subcommands `preflight`, `load`, `provision`, `deploy` and `all`. It verifies inputs, images and grants, provisions the PostgreSQL roles and the runtime privilege matrix, starts services in order and prints a deployment record without secrets |
| `.env.example` | Lists the VM-only entries (`SPRING_FLYWAY_*` migrator identity, single `CORS_ALLOWED_ORIGINS` origin) |

### Operator inputs

`deploy.sh` has no defaults for these. `infrastructure/vm/deploy.sh --help` prints
its usage without changing anything.

- `--env-file`: the real runtime env file, owned by the invoking user, mode 0600,
  in a 0700 directory. Its location and custody are a Leader decision
  ([proposal](#operator-identity-and-env-file-custody-proposal-leader-decision)).
- `--origin`: the single approved `https://` origin. `deploy.sh` fails unless the
  env file's `CORS_ALLOWED_ORIGINS` equals it exactly. The frontend image's
  `PUBLIC_ORIGIN` build argument must carry the same value, but the script does
  not read or compare it; the Dockerfile validates only its form. Matching it is
  the operator's responsibility. A different hostname would be expected to fail
  the later private HTTPS check against `--origin`, but that is not a check of
  `PUBLIC_ORIGIN`.
- `--compose-override infrastructure/vm/docker-compose.vm.yml`.
- `--archive` and `--archive-sha256`: the reviewed `docker save` release archive,
  built outside the VM ([release build](#release-build-outside-the-vm)).
- `--expect-image REF=sha256:…` for every service image.
- `--wait-timeout` and, for `deploy`/`all`, `--source-revision`.
- Non-secret references to separately made decisions:
  `--deployment-authorization`, `--provision-authorization`,
  `--public-exposure-authorization`, `--packet-filter-verification` and
  `--initializer-qualification`; on a host with existing state, also the four
  update attestations ([operation gates](#ct17i-operation-gates--authority-boundaries-and-ingress-evidence)).
  The script records these references; it cannot check them and never treats them
  as approval.
- The TLS directory. The source `/etc/codemastery/tls`, its read-only bind to
  `/etc/nginx/tls` and the override path are Leader-approved
  ([path bindings](#leader-approved-path-bindings--31-october-2026)). B3 selects a
  private key of mode 0400 owned by the Nginx UID
  ([B3](#b3--approved-secret-delivery-and-certificate-replacement-conventions)).
  The actual owner and mode layout on the VM is a Leader-reported input, not an
  approved binding, and filesystem ownership and custody remain reserved
  ([inputs](#authority-inputs-and-boundary)). `deploy.sh preflight` rejects a
  layout that does not meet its checks.

### Intended procedure

Each step requires its own authority; this guide grants none.

1. Build the linux/amd64 release images and the archive outside the VM, and
   compute the archive SHA-256 and image identities
   ([recipe](#release-build-outside-the-vm)).
2. Transfer the archive to the VM. The channel and custody are not yet selected
   (P8, release blocker 3).
3. Run `deploy.sh preflight`. It changes nothing. It checks the origin, the env
   file, the host and Docker, and the effective Compose model, including the image
   and TLS-directory policies.
4. Run `deploy.sh load` to verify the archive and load it into the host image store.
5. Run `deploy.sh provision`, then `deploy.sh deploy`, or `all` for load,
   provision and deploy as one coordinated update. `deploy` requires a healthy
   PostgreSQL, starts the backend privately and waits for its health probe. It
   then checks Flyway history, hardens and verifies the runtime grants, and checks
   the private HTTPS handshake. Only then does it start the public frontend.
6. Keep the printed DEPLOYMENT RECORD. It marks feature behavior, external access
   and host packet filtering `NOT_VERIFIED`. Those need QA01/QA07 and the DP03
   outside-network check.

### Health checks and stop conditions

- PostgreSQL must report healthy before `deploy` starts the backend.
- The backend has no Compose healthcheck. `deploy` probes
  `http://backend:8080/actuator/health` privately and requires HTTP 200 with
  `"status":"UP"`. Spring Security permits only that exact anonymous `GET`; it
  answers 200 with `{"groups":["liveness","readiness"],"status":"UP"}`, or 503 with
  `DOWN` when a dependency such as PostgreSQL is down, and the probe then waits
  until its deadline and stops before ingress. Other Actuator paths stay denied
  ([remediation](#dp01-private-health-probe-remediation--1-november-2026)).
- The frontend `release` image's healthcheck verifies the served certificate chain,
  expiry and hostname over loopback.
- Any failed check stops the release. Nothing is retried or reset. Backup,
  restore, rollback and certificate renewal are outside the script (DP01.F-deploy
  and [renewal](#certificate-renewal-proposed-procedure-not-implemented)).

### Release blockers

The [current list](#verification-and-remaining-gates) has seven blockers: the
private health probe (fixed in the repository, not yet observed on the VM),
production-qualified MinIO server and initializer
artifacts, release transfer and admission (P8), operator and env-file custody,
certificate renewal, authority references, and evidence (B1 capacity, QA01, QA02,
QA07, DP03). Until they are resolved, no lecturer-accessible deployment can be
claimed.

## Original Interim deployment methodology — 11 October 2026 record

> **Dated record.** The four subsections below were written with ADR002 on
> 11 October 2026, before any deployment tooling existed. Their statements about
> missing scripts and pending checks describe that date; the summary above is
> current. Their methodology for runtime secrets, external verification and
> evidence remains applicable.

### Status and scope

Planning and verification methodology under [ADR002](decisions/002-interim-delegated-contracts.md), CT17.I-scope/CT17.I-access. No actual deployment, paid service, public exposure, credentials or production access approved or performed. Retain approved one-VM Docker Compose architecture and existing infrastructure. Selected externally demonstrated capabilities are real Auth and Course catalogue/detail; local mastery results remain separately reproducible. The official lecturer-accessible prototype remains mandatory.

### Preconditions and local validation

Merge reviewed contracts, obtain separate implementation and deployment authority, implement/test selected features and provide real non-secret endpoint/configuration choices. Follow existing [setup](setup.md) and [testing](testing.md) commands; do not invent deployment scripts. Existing repository checks include backend Maven Wrapper verification, frontend npm run build and the documented Compose configuration validation. Local bindings remain loopback. This task did not run application deployment or these feature-validation commands.

### Runtime and health

Runtime secret values are supplied out of band; no secrets in documentation or VITE_* variables. Use existing /actuator/health without detail exposure, then exercise actual feature APIs/browser. Health alone does not demonstrate implementation or correct database access. Production TLS/origins/provider/access policy and rollback authorization require Leader decisions.

### External verification and evidence

An independent tester outside the developer LAN verifies the actual approved HTTPS URL, Auth/roles and browser→REST→PostgreSQL catalogue/detail, hidden404 behavior, English data and no secret disclosure. Record real URL, revision/build, UTC/ICT time, browser, tester identity, role and redacted expected/observed results. Stop on TLS/origin failure, data exposure, wrong authorization, placeholders or local-only access. Preserve failures and corrective evidence. Actual accounts/endpoints and lecturer instructions are external inputs, not invented values.

### Recovery and submission

Record actual deployment steps, backups/rollback procedures and responsible operator when separately authorized. Do not reset data or publish credentials. Link verified prototype and local mastery evidence to the English Interim report, contributions and submission documentation. No academic submission is authorized here.


## CT17 completion proposal — 18 October 2026 historical record

**PROPOSED_NOT_EFFECTIVE.** This section records Codex's technical selections
under the Project Leader's scoped instruction to investigate CT16 and CT17 in
parallel. That instruction authorizes this proposal, not separate manual Leader
review, paid infrastructure, security-risk acceptance, provisioning or deployment.
This was the approval boundary on 18 October. The 24 October delegated decision
below now approves only its enumerated policy slices, pending protected integration.
All other selections here remain proposals; concrete inputs and Final operations
are not approved. Earlier approved slices retain their original authority.
This section is the detailed CT17 decision record; it is not an executable runbook.

### Historical reconciliation and checkpoint status

Baseline: `main` revision `35abfdb45667aca650df5f7fae8a2c218e98e2d1`.
The official [requirements](requirements.md), [architecture](architecture.md),
[repository structure](repository-structure.md), [ADR001](decisions/001-modular-monolith-module-boundaries.md),
[ADR002](decisions/002-interim-delegated-contracts.md), [contribution rules](../CONTRIBUTING.md)
and [CODEOWNERS](../.github/CODEOWNERS) govern this proposal. Canonical originals
remain under `docs/references/originals/`; the approved architecture specifies one
cloud VM running Docker Compose. Neither current code nor checked Issue markers
independently supply technical approval.

| Decision / evidence | Classification | Disposition and limits |
| --- | --- | --- |
| Architecture §8, canonical one-VM Compose topology; foundation PRs [#2](https://github.com/chi-thanh-nguyen/codemastery/pull/2), [#4](https://github.com/chi-thanh-nguyen/codemastery/pull/4), [#5](https://github.com/chi-thanh-nguyen/codemastery/pull/5) | APPROVED_AND_CONSISTENT | REAFFIRM topology; packaging, schema and ownership do not establish deployed features. |
| ADR002 CT17.I-scope and CT20.I, [PR #73](https://github.com/chi-thanh-nguyen/codemastery/pull/73), merged [729ba60](https://github.com/chi-thanh-nguyen/codemastery/commit/729ba60cf927ab178aeb0d5c2942db90c36d88c5), 11 October 2026 ICT | APPROVED_AND_CONSISTENT | REAFFIRM real external Auth/catalogue evidence, separate local mastery evidence and retained Final obligations. |
| ADR002 CT17.I-access | APPROVED_AND_CONSISTENT | REAFFIRM external-verification methodology only; no actual provider, hostname, TLS arrangement or access credential was approved. |
| ADR002 CT17.I-runtime, CT17.remaining | UNRESOLVED | EXTEND with the proposed constraints below; concrete infrastructure and security inputs remain Leader-reserved. |
| ADR002 CT38.core and [PR #78](https://github.com/chi-thanh-nguyen/codemastery/pull/78), merged [166484e](https://github.com/chi-thanh-nguyen/codemastery/commit/166484ea83aaa570014a04352548de2aeddfd677), 16 October 2026 ICT | APPROVED_BUT_INCOMPLETE | REUSE names and consumed validation; production bindings and actual security behavior are separate. The recorded independent human review remains outstanding. |
| ADR002 CT01.core; subsequent [PR #91](https://github.com/chi-thanh-nguyen/codemastery/pull/91), merged 21 October; ADR001 §11 | Existing core approval plus actual CT01 technical determinations | REUSE CT01 policy and separately recorded §11.3 personal Leader residual-risk acceptance. Protected-integration provenance and real security/runtime verification remain separate; this CT17 delegation supplies no new risk acceptance. |
| Leader's 18 October 2026 CT01.identity/U01 sign-off recorded in [Issue #18](https://github.com/chi-thanh-nguyen/codemastery/issues/18) | APPROVED_AND_CONSISTENT | REUSE only the approved read-only Auth boundary; no logout, deployment or verified-security approval follows. |
| ADR002 CT03.catalogue / CT21.catalogue | APPROVED_AND_CONSISTENT | REUSE anonymous published catalogue/detail and bounded DTOs; no protected-content or full instructor-display claim. |
| Historical #83; subsequent [PR #92](https://github.com/chi-thanh-nguyen/codemastery/pull/92), merged 21 October; ADR001 §12 | CT03-owned predicates now technically approved; protected-integration provenance retained | CT40 structural/full U07 remains unresolved. Neither protected Course predicates nor CT40 are prerequisites for anonymous catalogue. |
| [PR #74](https://github.com/chi-thanh-nguyen/codemastery/pull/74), merged presentation showcase; current empty HTTP/Auth/router artifacts | APPROVED_BUT_INCOMPLETE | REUSE presentation evidence only. Merged UI and successful builds do not establish a real browser/API/DB workflow. |
| Current production deployment and recovery procedure | UNRESOLVED | DEFER execution; `infrastructure/vm/deploy.sh` is empty. No actual external environment or feature verification is established. |

No effective decision is superseded. Local configuration is not classified as a
production defect merely because it is deliberately unsuitable for public use.

| Checkpoint | Effective scope | Newly selected proposal | Remaining gate |
| --- | --- | --- | --- |
| CT17.I-scope | ADR002 capability/evidence selection | Reaffirmed without changing selected Interim scope | Real producers, data and independent execution evidence |
| CT17.I-runtime | One-VM architecture; local foundations only | Existing service responsibilities, immutable releases, private state, credential and readiness constraints | Provider/VM/capacity/budget, production image artifacts, configuration implementation and execution authority |
| CT17.I-access | Independent outside-network HTTPS verification methodology | Single browser origin, prefix-preserving API proxy, restricted exposure and secure operator handoff | Actual DNS/TLS/access arrangement, consumed CT01 security gates, approved implementation locations |
| CT17.F-runtime | Final accessible product and reproducibility obligations | Migration-aware updates, coordinated backups, restore verification and bounded rollback | Recovery objectives, custody, concrete mechanisms and actual Final coverage/verification |

### Runtime and access selections

**Hosting.** Prefer an existing suitable, team-controlled institutional cloud VM
if available and authorized; otherwise obtain a separately approved IaaS VM.
Free/trial offers require review of expiry, availability, billing and lecturer
access constraints. No provider, SKU, account, region, IP, capacity or price is
selected here. Size CPU, RAM and disk using the selected artifacts, expected demo
concurrency, storage needs and target-host measurements; no unsupported numeric
minimum is asserted. Managed application hosting, managed databases and additional
backend services are not selected within the approved one-VM topology.

**Services and images.** Retain the existing PostgreSQL, MinIO, one-shot storage
initializer, backend and frontend responsibilities in Compose. Retain persistent
PostgreSQL and MinIO volumes. Reject a Kubernetes/microservices redesign and an
additional reverse-proxy service: the existing frontend Nginx is the proposed
HTTP/TLS ingress location. Certificate acquisition/renewal remains a separately
approved operational mechanism, not an implicitly added dependency or service.

Use the approved Java 25 / Spring Boot and React / TypeScript / Vite stack.
Release images must identify the source revision, build inputs, tested dependency
locks and immutable image digests. Review base-image provenance, architecture,
licensing, vulnerability findings and maintenance before approving a deployed
artifact. Do not use mutable `latest` as the release identity or silently upgrade
the stack. Current local PostgreSQL and third-party MinIO/MC image choices are
not production approval. In particular, approval of a production MinIO/MC build
source and digest is an unresolved artifact gate; no replacement storage product
or omission of the approved storage responsibility is selected.

Prefer building a tested release once outside the VM with existing Dockerfiles,
then transferring or pulling the identical approved artifacts. A new registry,
credentials, CI deployment workflow or transfer command is not approved here.
DP01 must document the actual approved build/transfer mechanism and locations.
Building on a small VM adds resource and reproducibility pressure; rebuilding
unreviewed source during deployment is not selected. Existing backend image
packaging skips tests, so release verification must precede deployment separately.

**Configuration locations and inputs.** Existing repository targets are
`infrastructure/docker-compose.yml`, `infrastructure/vm/deploy.sh`,
`backend/Dockerfile`, `frontend/Dockerfile`, `frontend/nginx.conf`, `.env.example`
and `backend/src/main/resources/application.yml`. They are supporting evidence;
this documentation proposal does not authorize editing them. DP01's script is
its primary file; additional implementation targets need explicit file scope.
Preserve the local workflow when implementing deployment support. If an override,
certificate file, host configuration or backup location outside the approved
inventory is needed, obtain exact location/structure approval before creating it.

Keep real runtime secrets out of Git, Issues, images, browser bundles, logs and
rendered configuration evidence. The existing untracked root `.env` convention
may supply approved environment inputs; it must remain operator-readable only,
with no secret passed as a frontend build argument. The actual host checkout,
configuration custody and certificate/private-key mount locations remain explicit
operator/Leader inputs. Do not source `.env` as a shell script. Use existing
[setup](setup.md) variable names and parsing constraints; do not introduce a
secret manager or fictitious environment variables.

| Binding | Proposed external-runtime rule |
| --- | --- |
| `POSTGRES_*` / `DB_*` | Separate bootstrap administration from application identity; internal PostgreSQL connectivity, not public database access. No implicit credential aliases. |
| `STORAGE_*` / `MINIO_ROOT_*` | Backend uses a separate bucket-scoped application identity; root credentials are confined to administration/provisioning. |
| `JWT_SECRET` / `JWT_EXPIRATION` | Preserve CT01/CT38's random hex key and exact PT15M policy; no actual secret selected. Rebenchmark approved BCrypt work on the target host before deployment. |
| `CORS_ALLOWED_ORIGINS` | Explicit actual approved HTTPS browser origin; no wildcard, localhost production default or cookie-based session inference. *(ADR014, 16 November 2026: the same list is also the exact trusted Origin list of the three refresh-cookie operations and selects the `__Host-` cookie mode; see [CT17-INGRESS-COOKIE-2026-11-16-r1](#ct17-ingress-cookie-2026-11-16-r1--refresh-cookie-transport-delegated_approved-16-november-2026).)* |
| `VITE_API_BASE_URL` | Propose `/api` for the single-origin deployed build; it is public build-time input, requiring an image rebuild when changed. |
| Profiles / scheduling / other options | Forward only approved consumed settings; `prod` currently adds JSON logging, not a complete production policy. Do not promote unused scheduler/upload/reset defaults. |

**Database privilege and migrations.** Use a non-superuser application database
role with no cluster administration, `CREATEDB` or `CREATEROLE`. Confine ownership
and privileges to the application's database/schema. Existing automatic Flyway
startup uses the same `DB_*` datasource, so it requires migration DDL rights;
this proposal does not claim a DML-only runtime identity. Its residual schema
modification capability must remain explicit. A separate migration identity is a
possible later hardening decision requiring approved configuration/provisioning
changes, not a binding invented here. Validate migration history; never edit an
applied migration, reset data, or run repair to conceal a checksum mismatch.

**Storage privilege.** Provision MinIO privately with a separate application
identity and only the approved bucket's required object/list/location rights.
Reject unexpected broader policies; never reuse root credentials in the backend
or enable anonymous bucket access. Preserve idempotency and stop on conflicting
state rather than overwriting data. CT09 still owns upload/download limits,
object keys and delivery authorization; no public storage URL or presigned-link
contract is selected. Provisioned storage is not evidence of a working file feature.

**Browser, proxy and TLS.** Select one approved external HTTPS origin for the SPA
and REST. Route `/api` and `/api/…` to the backend, preserving `/api` and query
parameters rather than stripping or doubling the prefix. API matching precedes
SPA history fallback; API errors must never become `index.html` or a successful
HTML response. Preserve backend status, ProblemDetail content type and relevant
headers; do not log bearer credentials or cache authenticated/API responses at the
proxy. Missing static assets remain 404; valid client deep links reach the SPA.
CT16 owns URL normalization/client behavior; this is CT17's deployment binding.

The current Nginx configuration has neither API proxying nor TLS. Implementing
these behaviors in existing Nginx/Compose locations needs separate authorization.
The real hostname, DNS control, certificate trust chain, acquisition/renewal
mechanism, private-key custody, listener/host mappings and forwarded-header trust
must be reviewed before execution. Do not invent certificate paths or a client.
Terminate HTTPS at the approved ingress; bearer-bearing requests must not be
accepted over public HTTP. A separately configured HTTP listener may redirect to
HTTPS, but cannot process credential or API operations. Do not redirect requests
to arbitrary origins. Reject expired/untrusted certificates and mixed content.

Only the intended SPA/API surface and nondetailed `/actuator/health` verification
exception may be exposed. Do not proxy backend management paths indiscriminately:
other Actuator endpoints, API documentation and unapproved administration remain
nonpublic. The current backend configuration lists info/metrics and springdoc;
that is not approval to expose them. Backend authorization independently protects
all protected operations; an edge allowlist is not a replacement security boundary.

PostgreSQL, MinIO API/console and direct backend ports remain inaccessible from
the public network. Use internal service networking; remove unnecessary host
publication for deployment rather than promoting current loopback ports to
public bindings. Host/provider firewall and restricted operator access must be
approved and externally verified. Exact operator identities, allowed source
networks, SSH/access mechanism and emergency access are Leader inputs. No public
endpoint or firewall change is authorized by this document.

Same-origin browser access avoids a cross-origin deployment requirement; CORS is
not authentication or protection against non-browser callers. Backend CORS still
uses only the approved origin and preserves CT38 constraints. No cookie/refresh
session, wildcard origin, external storage origin or new JWT policy is introduced.
*(Superseded in part on 16 November 2026 by [ADR014](decisions/014-hybrid-refresh-session-authentication.md):
refresh sessions with a `__Host-` cookie, server logout and session-bound access JWTs
now exist; the rest of this paragraph is history.)*
Client-local logout is not token revocation. ADR001 §11.3 records the Leader's
21 October acceptance of its precise residual-token risks; that decision is not
reopened or expanded here. Real configured Auth/security verification, capacity/
abuse controls and approved exposure remain separate gates; unrelated Issue
closure is not required.

**Startup and health.** Preserve PostgreSQL readiness and successful one-shot
storage initialization before backend startup; Flyway must succeed before the
backend is considered ready. Long-running services should recover from process
failure and host reboot using a separately implemented, verified Compose restart
policy; the initializer remains a one-shot operation. Dependency ordering and a
container health flag do not provide ongoing resilience or restart an unhealthy
running process automatically. Fail initialization on conflicting configuration,
migration or credential state; do not retry business mutations to hide failures.

Nondetailed health and container process status are infrastructure observations.
Real Auth, role protection, published catalogue/detail and PostgreSQL-backed
responses are separate readiness evidence. Frontend startup can be independent,
but a running presentation showcase is not a functional prototype. Actual retry,
readiness probes and post-reboot recovery need implementation and tests before
claims; no new health endpoint, timings or success result is invented here.

### Final updates, backup and recovery

Use a reviewed release record identifying source and image digests, public build
configuration, approved runtime inputs, migration compatibility and operator.
Check required CI and relevant QA results, obtain execution authority, back up
state, then deploy the immutable artifacts. Revalidate health and selected real
features before declaring the release usable. No automatic merge, deployment,
destructive cleanup or promise of zero downtime is selected for this single VM.

Before state-changing updates/migrations, preserve a restorable PostgreSQL backup,
MinIO objects and identity/policy metadata, configuration recovery information and
release identity. Prefer PostgreSQL logical custom-format backups with compatible
PostgreSQL tooling; include the necessary role/ownership restoration procedure.
A database dump is internally consistent but does not coordinate object storage.
Quiesce relevant application writes for a coordinated database/MinIO recovery
point; do not claim a live volume copy or independent copies are a consistent
cross-store backup. Check backup integrity and perform a restore drill in an
approved isolated target before relying on the procedure.

Protect backup confidentiality and integrity; retain an approved off-VM copy so
VM loss does not destroy the only recovery material. Destination, encryption/key
custody, retention, frequency, RPO/RTO, recovery operator and isolated restore
location require explicit Leader inputs. No numeric guarantee, new storage
service, repository path or executed restore is implied. Do not expose secrets
or sensitive backups in evidence or use production credentials in test restores.

Rollback to prior immutable images is allowed only after verifying compatibility
with the current schema/data. Image rollback does not reverse Flyway migrations.
If incompatible, stop writes and use an explicitly authorized forward fix or
verified coordinated restore; record the acknowledged data-loss implications.
Never edit applied migrations or silently restore over newer data. Stop on
migration mismatch, failed backup/restore validation, unknown compatibility,
credential exposure, TLS failure, authorization failure or inconsistent data.
Preserve evidence and obtain the smallest required recovery decision before
continuing. Actual recovery execution remains separately authorized DP01 work.

### Cross-contract dependencies, handoffs and remaining approval

| Boundary | Consumed effective decision | Exact remaining dependency / owner |
| --- | --- | --- |
| CT16 HTTP ↔ CT17 origin/TLS | ADR002 CT16.minimum permits a same-origin `/api` base | CT16 owns normalization/client semantics; CT17 owns real ingress/TLS. Any parallel CT16 completion PR remains a proposal until effective. Neither entire Issue must close first. |
| Frontend build ↔ deployment | Vite public build-time value, existing Dockerfile | FE03 supplies the actual client; DP01 builds/deploys its approved `/api` artifact and Nginx proxy after applicable file/implementation approval. |
| Auth/session ↔ backend/runtime | CT01.core, CT38.core and ADR001 §11 determinations | Existing §11.3 personal logout-risk acceptance is retained. Auth/Frontend review, protected-integration provenance, real security verification and actual exposure gates remain. |
| Catalogue ↔ CT03/CT21 | Effective anonymous catalogue/detail | CO05/FE07 provide real producers; PR83 protected predicates and CT40 structures are not prerequisites for public catalogue. |
| Runtime ↔ CT38 / CT09 | Existing variable scopes; approved stack | Production values/artifacts need approval; storage feature/delivery behavior stays CT09-owned. |
| Scope ↔ CT20.I / QA | Selected Interim capabilities, separate local mastery | QA01/02/07/13 supply applicable executed evidence; QA14 independently verifies local mastery. No fabricated integrated adaptive deployment. |
| Local ↔ external verification | CT17.I-access methodology | DP02 supplies applicable local evidence, DP01 actual environment, DP03 independent outside-network checks; local success never replaces DP03. |
| Final release ↔ coverage | Retained Final requirements | U18 environment bindings and U33/U34 release-coverage allocation remain unresolved where not expressly decided. No particular E2E suite is mandated here. |

DP01.I-config can proceed after its specific contracts, compatible artifacts,
locations and implementation authority are approved; it need not wait for every
Final feature. DP01.I-provision additionally needs approved infrastructure,
production image sources, credentials and selected data. DP01.I-deploy requires
actual selected producers, applicable predeployment QA01/QA02/QA07 evidence and
explicit execution/public-access authorization. Before public Auth deployment,
effective consumed CT01 security/logout decisions must include explicit residual-
token risk acceptance or an explicitly approved alternative. These are consumed
checkpoints, not whole-Issue closure gates; independent configuration and public
catalogue work remain separately usable.
DP03.I-access/I-features require the real identifiable environment, safe access
handoff and applicable QA evidence. DO01 records actual commands/results in
existing guides; DO03 consumes usable prototype access and truthful limitations.

Reaffirm ADR002's outside-developer-network verification: record actual HTTPS URL,
revision/build, UTC and Vietnam timestamps, tester/browser/role, expected and
observed outcomes, failures and redacted evidence. Verify real Auth and
browser → REST → PostgreSQL catalogue/detail, hidden-resource 404, English data,
role protection and absent secret disclosure. Share lecturer trial accounts and
access instructions through an approved private channel, not Git/Issues; exact
accounts/roles and access procedure remain inputs. No seeded Administrator or
new role is implicitly approved. Health and screenshots alone are insufficient.

Required reviewers are Infrastructure, Auth, Frontend and affected Course/data
owners, with explicit Leader sign-off and applicable CODEOWNER/last-push reviews,
resolved threads, required CI/security checks and protected integration. The
Leader is the sole CODEOWNER of this documentation path: if PR authorship prevents
a valid review, use an independent contributor-authored/pushed workflow under the
existing rules. Do not bypass protection or represent Codex review as human review.
No implementation, deployment, verification or academic submission is authorized.

Technical references supporting these selections: [Docker startup semantics](https://docs.docker.com/compose/how-tos/startup-order/),
[Nginx proxy URI handling](https://nginx.org/en/docs/http/ngx_http_proxy_module.html),
[Nginx HTTPS configuration](https://nginx.org/en/docs/http/configuring_https_servers.html),
[Vite environment replacement](https://vite.dev/guide/env-and-mode.html) and
[PostgreSQL logical backups](https://www.postgresql.org/docs/current/backup-dump.html).
These product references do not supply CodeMastery-specific approval or secrets.

## CT17 delegated technical approval — 24 October 2026

### Authority, baseline and approval boundary

**CT17-2026-10-24-r1: DELEGATED_TECHNICAL_APPROVAL;
[LEADER_APPROVED](decisions/README.md#approval-status-labels).** Under the Project Leader
@chi-thanh-nguyen's explicit instruction **“CodeMastery — Bounded Technical
Approval Under Explicit Leader Delegation”**, dated 24 October 2026 (ICT),
**Codex, as delegated technical decision-maker, approves only P1–P4 below**.
The authority permits actual bounded technical determinations and this record,
not personal Leader review/risk acceptance, implementation, public exposure or
whole-Issue approval. New policy takes effect only through compliant protected
integration of the exact reviewed revision. The original 18 October proposal and
its unselected Final/runtime choices remain historical proposals.

Fresh inspected remote `main` is
[`9d76732bab363a63d2d91b5bddbf0a72b66a6d91`](https://github.com/chi-thanh-nguyen/codemastery/commit/9d76732bab363a63d2d91b5bddbf0a72b66a6d91),
24 October, unchanged since the preceding assessment. Inspected
[CT17 #70](https://github.com/chi-thanh-nguyen/codemastery/issues/70),
[DP01 #71](https://github.com/chi-thanh-nguyen/codemastery/issues/71),
[DP03 #72](https://github.com/chi-thanh-nguyen/codemastery/issues/72),
[FE03 #98](https://github.com/chi-thanh-nguyen/codemastery/pull/98) at
`2f459d47a9c398d55dc75c2b4c16622cdaa2adeb`, and
[FE07 #101](https://github.com/chi-thanh-nguyen/codemastery/pull/101) at
`8417205b0c5445a1ebace7b8a5f13847c50eddf5`. No deployment PR is open at inspection;
deploy.sh remains empty. Existing frontend/nginx.conf serves static files/history
fallback without an API proxy or TLS; Compose remains local-only. No actual
host, public origin, operator credentials or external access result was found in
the inspected authoritative records. Real secret files were not accessed.

Preserve approved one-VM Docker Compose architecture, existing service/module
responsibilities, ADR002 CT17.I-scope/I-access methodology and CT20.I Minimum.
Reuse CT01/CT18/CT38 only within their authority. ADR001 §11 already records actual
CT01 technical determinations and separately sourced personal risk acceptance;
#91/#92 merges do not independently establish their protected-integration
compliance. Neither that provenance gap nor a Project value is silently waived.

Publication preflight rechecked remote `main` at
[`0b2967226fcab946e0145160b1a18e21c30613d8`](https://github.com/chi-thanh-nguyen/codemastery/commit/0b2967226fcab946e0145160b1a18e21c30613d8)
after AD09 [PR #86](https://github.com/chi-thanh-nguyen/codemastery/pull/86)
merged on 24 October. The only delta from the initial baseline is its four
calculator/classifier implementation/test files; none changes these contract
sources or scopes. This task branch starts from that refreshed main revision.

### Actual approved policy determinations

| Slice | Delegated approval | Interfaces, invariants and exclusions |
| --- | --- | --- |
| P1 — single-origin ingress | APPROVE one subsequently Leader-selected external HTTPS browser origin for SPA and REST, using existing frontend Nginx and approved Compose locations | CT16 builds with `/api`; API-root addressing is coordinated with, not dependent on whole-Issue closure of, CT16. No actual hostname, TLS mechanism, certificate path or listener mapping is selected. |
| P2 — private state and credential separation | APPROVE private PostgreSQL/MinIO/direct-backend connectivity and separation of bootstrap/admin and application credentials | No public DB/storage/console/backend ports; no backend MinIO root identity, anonymous bucket or frontend secret. Actual identities, database grants, policies, custody and provisioning remain unapproved inputs. |
| P3 — immutable release evidence | APPROVE source revision, reviewed dependency lock, build/public configuration identity and immutable image digest as release identifiers | Build/verify once and deploy the same reviewed artifacts; mutable tags alone are insufficient. No image, registry, transfer mechanism or runtime baseline upgrade is selected. |
| P4 — startup and readiness boundaries | APPROVE ordered prerequisite checks, successful Flyway and storage initialization, nondetailed health and separate real selected feature evidence | Health/container startup alone never proves Auth, roles or PostgreSQL-backed catalogue. No new probe, numeric timeout, restart policy, recovery mechanism or public health endpoint is approved. |

P1 requires `/api` and `/api/…` matching before SPA history fallback, preserving
method, body, `/api` prefix, query parameters, backend status, JSON/ProblemDetail
content types and relevant end-to-end headers, including `WWW-Authenticate` and
`Cache-Control`. Preserve trusted internal bearer transport without logging it;
do not cache authenticated/API responses. Hop-by-hop headers are not blindly
forwarded. API 4xx/5xx must never become index.html or HTML 200; a failed upstream
must remain an HTTP failure. Client deep links reach the SPA while missing assets
remain real 404. Unknown API paths do not become browser routes. Backend security
and owning-module authorization remain authoritative, irrespective of ingress.

HTTPS termination uses the existing frontend ingress responsibility; actual TLS
acquisition/renewal, key custody, forwarded-header trust and host mapping need
Leader inputs before implementation/exposure. No public credential/API processing
over plaintext HTTP is approved. Only separately approved API capabilities may
be exposed; proxying `/api` is not approval of CT22 or another feature. Nonpublic
management/docs surfaces stay nonpublic. Nondetailed health may be checked
privately by the operator; the earlier proposal's public health exception is
**not** approved by P4. No gateway, additional service or infrastructure technology
is introduced.

P2 preserves named-volume persistence and migration history. Current automatic
Flyway uses the backend datasource and therefore consumes migration DDL rights;
this observation does not approve a production privilege plan or accept its risks.
Concrete grants or a separately approved migration identity must be reconciled
before provisioning. New secret/certificate/host/backup locations and broader
storage capabilities remain outside delegation. Protected files and object-key/
delivery behavior remain CT09-owned; provisioned storage is not feature evidence.

P4 preserves existing PostgreSQL healthy/storage-init completed prerequisites and
requires Flyway success before backend readiness. Fail on configuration, migration,
credential or initialization conflicts. Dependency ordering is not continuous
resilience; health does not restart an unhealthy running container automatically.
Exact restart/reboot/recovery choices remain proposals, not implicitly activated.
Auth acceptance requires the real configured security chain/current PostgreSQL
account state and approved role/resource cases. Catalogue acceptance requires
real browser → REST → PostgreSQL list/detail and concealed 404 behavior.
Independent outside-LAN HTTPS verification and secure lecturer access remain
execution evidence, while deterministic local mastery is reproduced separately.

### Evidence, alternatives and acceptance

[Existing Nginx configuration](../frontend/nginx.conf),
[Compose](../infrastructure/docker-compose.yml), service Dockerfiles, application
configuration, Vite configuration and OpenAPI were inspected read-only. Existing
locations can express the selected policy without another deployable component;
they are references, not permission to edit them. Supporting official technical
sources are [Nginx URI/header/error proxy semantics](https://nginx.org/en/docs/http/ngx_http_proxy_module.html),
[Compose startup semantics](https://docs.docker.com/compose/how-tos/startup-order/)
and [Vite build-time environment replacement](https://vite.dev/guide/env-and-mode.html).

Alternatives: separate SPA/API origins add CORS/certificate/public-surface work;
select one origin for this bounded deployment while retaining CT16's independent
local absolute-origin mode. An additional proxy/gateway changes service structure
without demonstrated need; use existing Nginx. Mutable latest/build-on-deploy
identity is less reproducible than immutable reviewed artifacts. These decisions
approve policy, not a provider, artifact or procurement choice.

Required later acceptance: `/api/courses` keeps its prefix/query and JSON/status;
missing/hidden course returns approved 404; malformed/Auth failures preserve
ProblemDetail/status and 401 headers; a browser deep-link serves the SPA, a missing
asset stays 404, and an API error never does SPA fallback. Public scans must find
no PostgreSQL/MinIO/direct backend/management exposure. Migration/init failure
prevents readiness; health-only results cannot satisfy feature acceptance.
Record source/image/build identity, actual origin, tester/browser, UTC/ICT time,
expected/observed results and redacted failures. These are unexecuted acceptance
criteria, not claims that a deployed environment exists.

### Withheld determinations and Leader-reserved inputs

| Input / decision | Actual evidence and smallest remaining decision |
| --- | --- |
| Host, provider, capacity and budget | No selected actual environment evidenced. Compare an authorized existing institutional/team VM with a separately approved IaaS VM, preserving one VM/Compose; Leader chooses ownership, expenditure and measured capacity. |
| Actual origin and TLS/access | No domain/DNS authority, certificate lifecycle/key custody, operator/lecturer access or forwarded-header trust plan evidenced. Leader supplies the actual arrangements and exact approved locations. |
| Production artifacts | Local image pins and dependency locks exist, not production selection evidence. Obtain artifact provenance/architecture/license/maintenance/security review and actual digests before selecting; no local MinIO/MC pin is promoted. |
| Credentials and locations | Existing variable names and role separation are evidence, not real secrets/grants. Leader approves operator/custody/provisioning and exact configuration/certificate/host locations without publishing values. |
| Security and concrete readiness | Target-host BCrypt capacity, production abuse controls and applicable configured Auth/API checks remain prerequisites. No material risk exception, rate-limit number or exposure is accepted here. |
| Final operations/recovery | Existing backup/restore/rollback text remains proposal. Destinations, retention/RPO/RTO, key custody, restore targets and execution authority are unselected; no new location or recovery action is approved. |

Only P1–P4 are technically approved; these missing inputs are **WITHHELD / LEADER
RESERVED**, not defaults to be filled by implementers. U18 retains its unresolved
actual environment binding; P1–P4 do not mark that combined relation resolved. U33/U34,
whole Final coverage and CT17.F-runtime remain unresolved. CT16 is approved only
by its own separate decision; its protected integration is an independent gate.
CT14 preparation, CT22 membership, CT21 extensions, CT40/full U07 and production
Adaptive/Assessment contracts are excluded.

### Owners and protected integration

Infrastructure @tihh647-ui prepares the separately authorized configuration;
Frontend @heizseung and Auth @duyduyit review browser/security consumption, Course
and data owners review selected feature evidence. Leader remains sole CODEOWNER
of docs/deployment.md and decides reserved inputs/execution authority. Current
rules require CODEOWNER/last-push review, resolved threads, strict Backend/Frontend/
CodeQL checks and security scanning. A Leader-authored Draft PR cannot self-approve;
use a compliant independent author/pusher and eligible human review without
impersonation, bypass or protection changes. No completed review is asserted.

This task approves policy documentation only. All infrastructure edits, provisioning,
public exposure, deployment, recovery, runtime/QA acceptance and submission remain
separately authorized work. Issue #70 is not wholly approved/completed. Codex
assisted the investigation and authored this record; no AI application service
or new infrastructure technology is introduced.

## CT17 Interim technical policy completion — 24 October 2026

### Delegation, predecessor and classification

**CT17-INTERIM-2026-10-24-r2: DELEGATED_TECHNICAL_APPROVAL;
LEADER_APPROVED.** Authority is the Project Leader's
explicit **“CodeMastery — Complete Interim Technical Contract Approvals
(CT16, CT17, CT14)”**, supplied 24 October 2026 (ICT). Codex acts as the delegated
technical decision-maker and **approves P5–P10 below**. Ordinary engineering
selection is exercised here; no personal Leader risk/financial/access authority
or implementation/deployment authorization is exercised.

Preserve **CT17-2026-10-24-r1 P1–P4** byte-for-byte and with their original meaning.
This extension selects previously unapproved engineering detail; it does not
backdate or activate the 18 October proposal, reopen settled policy, or approve
remaining CT17.F-runtime. Prior statements withholding these technical choices
remain authentic history; only the named P5–P10 choices are resolved prospectively.

Fresh main is
[`0b2967226fcab946e0145160b1a18e21c30613d8`](https://github.com/chi-thanh-nguyen/codemastery/commit/0b2967226fcab946e0145160b1a18e21c30613d8),
including AD09 #86. This follow-up is safely stacked on [Draft PR #103](https://github.com/chi-thanh-nguyen/codemastery/pull/103),
exact **a64a94b9e93785ae97fbfaaed6ee6f8da7455267**; #103 remains unmerged with no
review. No existing PR content/metadata is changed. P1–P4 and this extension require
sequential compliant protected integration and revalidation; pending Draft content
is not an effective main contract. CT16 addressing is a separate dependency on
[Draft PR #102](https://github.com/chi-thanh-nguyen/codemastery/pull/102),
**802ee19e38f22b35267bb51dfe04c8ee56b64acb**.

### P5 — runtime envelope, locations and fail-fast inputs

Retain one VM/Compose, existing PostgreSQL, MinIO/init, backend and frontend service
responsibilities, Java 25/Spring Boot 4.1.1, PostgreSQL 17 baseline and current
React/Vite/Nginx responsibilities. No new service, technology or runtime upgrade.
The release must match the actual VM's OS/CPU architecture and demonstrate memory,
CPU/disk/storage headroom, selected concurrent Auth/catalogue workload and BCrypt
cost-14 capacity. Do not invent VM sizes, numeric load targets, rate limits or
availability guarantees: record measured results against the Leader-approved
actual resource/access envelope before exposure. That is a concrete qualification
gate, not an unresolved choice of deployment architecture.

Select existing configuration responsibilities only: infrastructure/docker-compose.yml,
infrastructure/vm/deploy.sh, backend/src/main/resources/application.yml, existing
service Dockerfiles and frontend/nginx.conf; templates/guides must be synchronized
in a separately authorized implementation. Use existing ignored root .env convention
or approved process environment, never shell-source it, bake secrets into images,
forward every host variable, or publish rendered configuration. Actual host checkout,
file permissions/custody, certificate/key mounts and any additional host/backup
location remain Leader inputs. No new certificate or secret-manager location is
selected. Certificate trust/hostname, readable key material and renewal ownership
are mandatory before ingress start; TLS failures stop exposure, not downgrade it.

Keep DB_*, POSTGRES_*, STORAGE_*, MINIO_ROOT_*, JWT_SECRET/JWT_EXPIRATION,
CORS_ALLOWED_ORIGINS and public VITE_API_BASE_URL meanings. Build the external SPA
with `/api`; explicitly bind backend CORS to the actual approved origin and retain
PT15M/HS256/issuer/audience requirements. No localhost/wildcard/secret defaults are
accepted in that environment. Enforce consumed required inputs before startup;
unused scheduling/recovery/upload defaults do not become approved feature policy.

### P6 — bounded database and storage privileges

Select separate **bootstrap administrator**, **schema migrator** and **runtime
application** identities. Runtime DB_* uses a non-superuser role with only CONNECT,
USAGE and selected tables/sequences' required DML privileges in the existing
application database/schema, no ownership/CREATE/DDL/CREATEDB/CREATEROLE, no role
membership permitting elevation, and no access to other application databases.
The non-superuser migrator owns/manages only the existing application schema and
Flyway history; it cannot administer the cluster/create databases or roles.
Bootstrap credentials are not the application's or migrator's credentials.

Use **Spring Boot's existing native Flyway datasource**, with the explicit
`spring.flyway.url`, `spring.flyway.user`, `spring.flyway.password` properties.
For Compose/process-environment binding select exactly **SPRING_FLYWAY_URL**,
**SPRING_FLYWAY_USER**, **SPRING_FLYWAY_PASSWORD**. These are newly technically
approved CT17 deployment inputs, not implemented/current template entries or
approval of all CT38.feature. All three must be supplied and validated together;
URL targets the same approved database/schema as DB_*, contains no credentials,
and uses a distinct migrator identity. Reject missing/placeholder/mismatched
inputs or fallback to runtime/admin credentials. No new dependency, migration
file, schema name or separate deployable migrator is introduced.

Flyway remains automatic at startup; Hibernate remains validate-only. Apply only
reviewed versioned migrations and verify history/checksums before readiness.
Never reset/truncate/clean, edit applied V001–V006, or repair away a mismatch.
Provision future required application DML grants under the selected role policy
before feature readiness; do not grant ownership merely to make startup pass.
Actual role names, credentials/access grants and provisioning authority are
Leader-reserved. Both credential sets are present in the backend process for
startup: this is database-connection privilege separation, not process isolation
or a claim of protection from arbitrary code execution. No broader risk exception
is accepted. A later separate migration job/process would require its own approval.

MinIO root remains confined to administration/init; backend gets only the separate
approved bucket-scoped identity. Require private endpoints, no anonymous access,
idempotent exact-state checking and conflict-stop provisioning. Existing init code
must be verified for this behavior; creation/update commands are not proof that
safe re-execution or secret rotation has been implemented. CT09 object delivery
and Final file-feature behavior remain excluded.

### P7 — ingress and forwarding trust

Keep P1's one-origin existing Nginx ingress, prefix-preserving `/api` proxy and
API-before-SPA precedence. Bind routing to the actual approved host and reject
unexpected Host values. Terminate valid HTTPS at that ingress; support TLS 1.2/1.3,
without selecting a certificate client, DNS provider, key path or real hostname.
Drop untrusted inbound Forwarded/X-Forwarded-* identity/trust claims; any forwarded
values emitted by Nginx are regenerated from its validated connection/canonical
origin, not appended blindly from caller input. Select backend
`server.forward-headers-strategy=NONE` for this bounded REST application: current
operations do not need absolute external URL generation or trusted proxy identity.
Do not rely on spoofable headers for authentication, authorization or access grants.

Direct backend/database/storage/console access stays private. No upstream load
balancer/CDN or trusted-proxy address range is assumed. If an actual environment
adds an upstream proxy or requires backend forwarding interpretation, reconcile
that new consumed boundary explicitly before exposure. Preserve P1 error/status/
content-type/cache rules, asset 404 and browser deep links. Private operator
health checks do not create a public Actuator/docs exception.

### P8 — release transfer, persistence and restart behavior

Select build/verify outside the VM using existing Dockerfiles and reviewed locks;
package the resulting immutable images with Docker's native save/load archive
format, with a SHA-256 archive checksum and image content IDs/config/platform
record. For registry-sourced images also record registry manifest digests; a loaded
archive is not claimed to recreate a missing RepoDigest. Transfer through a
subsequently approved authenticated encrypted operator channel, verify checksum
and content identity before use, and never silently rebuild source on the VM.
This avoids requiring a new registry/account/service. No concrete image archive,
production base/storage image, transfer account or filesystem destination is
approved merely by this format decision.

Approve restart **unless-stopped** for long-running existing services and **no**
automatic restart for the one-shot storage initializer. Preserve named volumes;
no down -v, data reset or automatic conflict reconciliation. Require healthy
PostgreSQL, successful init and Flyway, then selected feature checks before readiness.
Do not hide migration/configuration failure behind container restarts: stop the
release on such failure. Startup dependency conditions are not runtime supervision;
process restart does not restart an unhealthy-but-running process. Verify actual
reboot behavior separately and ensure operator access before relying on it.

Image admission requires provenance, license, architecture, maintenance and
vulnerability review plus actual immutable identity. Do not promote local-only
MinIO/MC pins to production or certify future unbuilt Auth/Course artifacts.
Missing release artifacts are an implementation/evidence gate, not permission to
invent a production image or require personal Leader approval of every ordinary
image-engineering selection. Material exceptions remain Leader-reserved.

### P9 — logging and deployment-stop/rollback prerequisites

Use the existing prod structured console logging capability and safe ingress
status/route diagnostics. Exclude Authorization/password/token/credential values,
request/response bodies, sensitive query strings, rendered env/config and keys;
keep exception responses within CT18. Record revision/artifact/correlation/status
and redacted observed failures. No new logging service, log-retention value or
host log location is selected. Production debug/raw SQL/body logging is not a
shortcut to evidence.

Before a state-changing update, require verified schema/image compatibility,
preserved persistent state and an independently checked, restorable pre-update
recovery artifact under the operator's approved custody. If that prerequisite or
authority is missing, stop the update. Do not automatically restore or roll back
schema/data. An image-only rollback is eligible only with unchanged/compatible
schema/data and separate authorized operator action. Otherwise stop effects,
preserve evidence and obtain the required recovery decision. This approves
preconditions only: Final backup destinations/retention/RPO/RTO/restore execution
and operational liability are not decided or authorized.

### P10 — Interim acceptance and operational handoff

Infrastructure @tihh647-ui prepares separately authorized packaging/configuration
and reports init/migration/process outcomes; Auth @duyduyit, Course @TamiaoNguyen
and Frontend @heizseung supply actual producers and consumed-boundary evidence.
QA01 checks configured Auth/authorization/current account state; QA02 verifies
PostgreSQL queries/visibility; QA07 verifies real browser interactions; DP02 local
container evidence and DP03 independent outside-network HTTPS evidence remain
distinct. QA14 independently reproduces AD09's exact CT06/CT24/CT36 local oracles;
DO03 receives truthful selected-Minimum access/results for the report.
The Leader nominates the actual operator, tester and credential/access custodians;
CODEOWNERS assignment is not proof of their availability or execution acceptance.

The handoff identifies source/image/configuration identity, actual origin and
approved access, migrations/data provenance, UTC/ICT timestamps, tester/browser,
expected/observed results, failures and limitations. Verify real register/login,
locked/invalid authentication and approved role/resource cases, public list/detail
through PostgreSQL including hidden 404, secret-free English/responsive UI,
reload/expiry/logout races and nonpublic infrastructure. Do not invent a protected
API merely to demonstrate role denial. Health/screenshots/build success are not
substitutes, and local mastery is not claimed as deployed integrated Adaptive.
Use the existing independently documented browser protocol for Interim; no new
Final E2E journey, runner or academic submission is approved.

### Evidence, alternatives and exact unresolved inputs

The inspected source includes current Issues #19/#70/#27 and #18/#22/#20/#17/#23/
#21, AU05/AU06/CO04/CO05, QA01/QA02/QA07/QA14, DP01–DP03 and actual manifest,
Dockerfiles, Compose, Nginx and configuration. No actual authorized host/domain/
access arrangement is evidenced there; no private cloud account or secret file
was accessed. Available *verified* deployable environments: **none established**.
An existing authorized institutional/team VM versus a separately funded IaaS VM
are conditional options, not verified available resources or financial commitments.

Technical evidence: [exact Boot 4.1.1 Flyway properties](https://raw.githubusercontent.com/spring-projects/spring-boot/v4.1.1/module/spring-boot-flyway/src/main/java/org/springframework/boot/flyway/autoconfigure/FlywayProperties.java)
and [datasource selection](https://raw.githubusercontent.com/spring-projects/spring-boot/v4.1.1/module/spring-boot-flyway/src/main/java/org/springframework/boot/flyway/autoconfigure/FlywayAutoConfiguration.java),
[PostgreSQL privileges](https://www.postgresql.org/docs/17/ddl-priv.html),
[Nginx HTTPS](https://nginx.org/en/docs/http/configuring_https_servers.html),
[Docker restart](https://docs.docker.com/engine/containers/start-containers-automatically/)
and [archive transfer format](https://docs.docker.com/reference/cli/docker/image/save/).
All are compatible mechanisms, not executed application verification.

Alternatives: retain one schema-owner runtime credential (simpler but unnecessary
DDL privilege), add a migration service (extra lifecycle), or use Boot's already
available separate datasource (selected least-privilege connection boundary).
A new registry is unnecessary for a single VM; the selected native archive format
keeps transfer identity reviewable. Manual/restart-free operation lacks reboot
recovery; the selected container policy retains explicit operator stop controls.
No actual resource or risk exception follows from these engineering choices.

Classification of the complete P1–P10 policy set:

- **CT17_INTERIM_TECHNICAL_POLICY_COMPLETE**: all independent engineering policy
  decisions for this bounded one-VM Interim plan are specified; this is technical
  coverage, still pending protected integration.
- **CT17_INTERIM_CONCRETE_BINDING: PENDING_LEADER_INPUT**: actual VM/account/budget,
  domain/DNS authority, certificate/key custody, actual credential/grant/operator
  arrangements, exact host/certificate/recovery-artifact locations and public
  exposure authorization remain missing. No concrete-binding completion claim.
- **CT17_INTERIM_IMPLEMENTATION_AUTHORIZATION_PENDING**.
- **CT17_INTERIM_RUNTIME_VERIFICATION_PENDING**.

Capacity/security qualification, actual production artifact admission and safe
re-execution/readiness evidence additionally remain producer/verification gates.
U18 actual environment binding and U33/U34 are not marked resolved. P1–P4, CT01,
CT03, CT06/CT24/CT36, CT18/CT20/CT21/CT38 remain unchanged except these explicitly
named, additive CT17 deployment conventions. No whole family is approved.

All new policy remains LEADER_APPROVED. The sole-Leader
CODEOWNER/PR-authorship conflict is unchanged: a non-owner review or another
contributor's push alone cannot satisfy it. Eligible independent human review,
last-push approval, required checks and compliant exact-revision integration are
mandatory; no bypass, review submission or personal Leader acceptance is claimed.

## CT17 consolidation and bounded Final approvals — 25 October 2026

### Authority and preserved history

**CT17-FINAL-2026-10-25-r1: DELEGATED_TECHNICAL_APPROVAL;
LEADER_APPROVED.** Under the Leader's explicit
**“CodeMastery — Consolidate Contract Approval PRs and Complete Eligible Final
Technical Approvals”**, supplied 25 October 2026 (ICT), Codex acts as delegated
technical decision-maker and **approves F1/F2 below only**. No personal financial,
security, access, recovery-risk or liability acceptance is inferred.

Fresh main is **0b2967226fcab946e0145160b1a18e21c30613d8**. Canonical
[PR #103](https://github.com/chi-thanh-nguyen/codemastery/pull/103) preserves all
original **a64a94b9e93785ae97fbfaaed6ee6f8da7455267** content plus all unique
[PR #105](https://github.com/chi-thanh-nguyen/codemastery/pull/105) content at
**ee085e05bf0ebf3b7fa75e21e6ac9165f3eef21d**. The earlier stack/integration
instructions are historical process descriptions; this authorized consolidation
replaces that path with #103 targeting main. Close #105 without merge only after
remote preservation verification. Original heads remain recoverable.

CT17-2026-10-24-r1 P1–P4 and CT17-INTERIM-2026-10-24-r2 P5–P10, their restrictions
and Interim certifications are unchanged. Their earlier Final deferrals are
resolved prospectively only for the two named engineering mechanisms below.
Neither the full earlier proposal nor concrete runtime or Final operations are
approved by consolidation. CT16 #102 remains an explicit consumed dependency,
not an effective main contract simply because its Draft contains approval text.

### F1 — PostgreSQL backup/restore payload and privilege mechanism

**Checkpoint CT17.final-pg-backup: APPROVE** PostgreSQL-native logical custom
archive backup, using pg_dump compatible with the deployed PostgreSQL 17 baseline,
and pg_restore into a separately authorized empty isolated target. Include the
application schema/data, sequences, constraints and Flyway history; no selective
business-table omission. Record server/client versions and archive checksum;
refuse incompatible/downgrade restoration. Do not use a live volume copy as the
PostgreSQL backup mechanism or introduce another database/service/backup platform.

Restore as the P6 bounded migrator with **--no-owner --no-acl --exit-on-error**;
provision the P6 runtime grants independently from reviewed provisioning inputs.
For custom archives no-owner is a restore-time choice, not a claimed pg_dump
ownership filter. Do not restore cluster-wide role/password dumps or grant
runtime DDL/administrative rights merely to make recovery pass. Actual target,
accounts/grants and execution authority remain reserved inputs. Treat archives
as confidential: domain password hashes/private data may be present. No archive
may be committed, published, sent to an unapproved destination or created without
an approved protected custody/encryption arrangement.

The database snapshot is internally consistent, not automatically synchronized
with MinIO. When claiming a full product recovery point, quiesce and drain all
application/background/admin writers to both stores, keep that write exclusion
through capture of the selected complete object/bucket/IAM/configuration state,
and record proof. If a writer cannot be bounded or the object-state mechanism is
unqualified, stop the *combined* backup claim. F1 independently approves the
PostgreSQL mechanism; it does not approve an unknown MinIO mechanism.

Alternatives: plain SQL is less suitable for inspectable selective restore;
physical PostgreSQL backup adds version/WAL/recovery tooling not currently needed.
Native custom archives reuse the approved database toolchain. Acceptance requires
archive readability and an actual isolated restore/constraints/history check;
a successful dump command alone is insufficient. This is an unexecuted technical
specification, not a backup, restore or recovery commitment.

Evidence: [PostgreSQL 17 pg_dump](https://www.postgresql.org/docs/17/app-pgdump.html),
[pg_restore](https://www.postgresql.org/docs/17/app-pgrestore.html), current Flyway/
Hibernate configuration and P6 privilege boundary. Only PostgreSQL mechanics are
selected; no RPO/RTO/retention/destination or ownership of actual keys is selected.

### F2 — coordinated recovery qualification and release compatibility gate

**Checkpoint CT17.final-recovery-qualification: APPROVE** a fail-closed recovery
acceptance protocol and evidence interface. A candidate recovery set must identify:
source/configuration revision; image content identities/platform; database and
object-state artifact hashes; required bucket/IAM/configuration dependencies;
Flyway versions/checksums; UTC capture interval and write-quiescence evidence;
required secret/key custody references without values; isolated validation results
and operator authorization reference. No new tracked manifest file/path is added.

Rehearse in an isolated, nonpublic instance of the existing stack against copied
artifacts, not the live database/volumes. Verify hash integrity, schema/history and
privileges, object/reference integrity under effective owning contracts, private
storage/access, and the applicable approved Auth/Course/other feature cases.
Background or business effects must not escape the rehearsal; no event replay,
new notification delivery or synthetic public security-probe endpoint is invented.
An unavailable feature test or missing object dependency is recorded as a failure/
limitation, never as a passed complete-product recovery.

A candidate application image must be compatible with the restored/current schema
and data. Image rollback cannot undo migrations. On unknown compatibility,
partial capture, missing keys, failed restoration, unexpected grants or incomplete
feature verification, stop release/recovery and preserve evidence. Only a
separately authorized operator can enact an approved compatible image change or
restore. Never restore over newer state or accept data loss automatically.

Alternatives: independent uncoordinated copies cannot establish a cross-store
recovery point; a health-only drill cannot establish data/security recovery.
The selected protocol produces explicit, reproducible acceptance inputs without
automatic rollback or another operational service. Infrastructure coordinates
technical execution; data/feature owners validate their artifacts, QA records
observed results and the Leader retains actual recovery authorization/custody/
liability choices. No test result or operator nomination is fabricated here.

### Entire retained Final decision inventory

| Subject | Classification | Exact missing prerequisite or disposition |
| --- | --- | --- |
| One-VM architecture, configuration/Flyway privileges, ingress/forwarding, releases, restart/logging/evidence boundaries | E — ALREADY APPROVED | Preserve P1–P10; no second approval or new infrastructure technology |
| PostgreSQL logical mechanism and recovery qualification interface | A — READY FOR DELEGATED TECHNICAL APPROVAL | F1/F2 technically approved above, pending protected integration |
| Complete MinIO objects/versions/bucket/IAM/configuration backup and restore mechanism | B — REQUIRES ADDITIONAL TECHNICAL EVIDENCE | Need production-qualified server/MC versions and demonstrated complete capture/restore semantics. Vendor IAM/bucket/config exports are separate from object data; a plain mirror is not proof of a full recovery set. Local-only third-party pins and AIStor documentation do not establish compatibility for the actual deployment. |
| Concrete backup encryption/transport tooling and compatibility | B — REQUIRES ADDITIONAL TECHNICAL EVIDENCE | Need an evidenced supported mechanism, compatibility/security review and isolated round trip; F1/F2 do not silently select a new tool or claim encryption implemented. Key/destination custody is separately C. |
| Actual production image/resource qualification | B — REQUIRES ADDITIONAL TECHNICAL EVIDENCE | Actual built artifacts, architecture/license/maintenance/vulnerability review and measured workload/capacity; ordinary artifact engineering is not automatically personal Leader authority |
| Host/provider/account/budget, domain/DNS, TLS/private keys, actual credentials/grants/locations and public exposure | C — LEADER-RESERVED DECISION | Explicit actual selection/custody/access/exposure authority; no defaults or inferred resources |
| RPO/RTO, retention/frequency, off-VM destination/key custody, recovery authority and accepted data-loss/liability | C — LEADER-RESERVED DECISION | Personal operational/financial decisions and actual resource ownership; no numerical promise is selected |
| Feature-specific readiness/load/recovery assertions | D — DEFER UNTIL CONSUMER OR INTEGRATION READINESS | CT09 and Course/Assessment/Adaptive/Interaction/Admin feature interfaces, actual producers and approved expected outcomes |
| Final external/demo coverage and particular E2E suite allocation | D — DEFER UNTIL CONSUMER OR INTEGRATION READINESS | CT20.F and CT19/CT34/CT35/CT36 where consumed; U33/U34 remain unresolved |

Supporting storage evidence: [mc mirror](https://docs.min.io/aistor/reference/cli/mc-mirror/),
[IAM export](https://docs.min.io/aistor/reference/cli/admin/mc-admin-cluster-iam/mc-admin-cluster-iam-export/)
and [configuration export limits](https://docs.min.io/aistor/reference/cli/admin/mc-admin-config/).
These vendor documents identify evidence boundaries, not an approved product/image,
new service or completed operational validation. No complete MinIO recovery,
encryption implementation or production artifact selection is certified here.

**CT17_FINAL_TECHNICAL_POLICY_PARTIALLY_APPROVED.** Interim policy completeness
remains technical coverage only; concrete bindings are PENDING_LEADER_INPUT.
Implementation authorization and operational verification remain pending. Final
F1/F2 do not change the Interim Minimum, waive Final integrated-product obligations
or approve a whole Issue. Existing sole-Leader CODEOWNER/author conflict, eligible
independent review/last-push approval, exact-head CI/security and protected
integration remain required. No provisioning, backup, restore, deployment, public
exposure, runtime verification or protected merge was performed or authorized.

## CT17 additional Interim deployment bindings — 25 October 2026

### Authority, revision and actual resource evidence

**CT17-INTERIM-BINDINGS-2026-10-25-r1: DELEGATED_TECHNICAL_APPROVAL;
LEADER_APPROVED** for B1–B3 below only. The Leader's explicit
**“CodeMastery — Complete Additional CT16, CT17 and CT14 Technical Approvals in
Existing PRs”**, supplied 25 October 2026 (ICT), delegates ordinary technical
selection/approval to Codex, the technical decision-maker. Actual expenditure,
resource ownership, access grants, credential/key custody, public exposure and
material risk acceptance remain personal Leader decisions. No provisioning,
credential generation, deployment, renewal, recovery or external test is authorized.

Fresh remote main is
[e2128919fe0d47c0f24989166343e1d5ecf50483](https://github.com/chi-thanh-nguyen/codemastery/commit/e2128919fe0d47c0f24989166343e1d5ecf50483).
[PR #103](https://github.com/chi-thanh-nguyen/codemastery/pull/103) starts at
9d966c469d008afe21b0bb1a98e7181f81224ab7. The sole new main delta since
0b29672 is six bounded Auth account-operation/DTO/test files; no infrastructure
configuration or actual runtime binding was added. @TamiaoNguyen's APPROVED
review covers the initial PR head; it is not approval of this new commit or
sole-Leader documentation CODEOWNER approval.

Search scope: full current CT01/CT14/CT16/CT17/CT18/CT20/CT21/CT38 Issue records,
DP01 #71/DP03 #72, FE03/FE07 and QA handoffs; deployment/configuration source,
ADRs and canonical PR records. Relevant #18/#19/#27/#70/#71/#72/#21 comment
threads are empty at inspection. No actual institutional/team VM, cloud account,
authorized VM identity/IP, owned hostname/DNS zone, certificate, operator grant,
production secret/custody location or approved paid budget is established there.
No cloud-console session, secret file or unrelated connected account was searched.
**Verified authorized deployable environments: none established by these sources.**
That is an evidence limitation, not a claim that no such resource exists.

Preserve CT17-2026-10-24-r1 P1–P4, CT17-INTERIM-2026-10-24-r2 P5–P10 and
CT17-FINAL-2026-10-25-r1 F1/F2 byte-for-byte. Existing one-VM topology,
privilege separation, /api semantics, forwarding policy, immutable archives,
restart/logging/readiness and Final recovery mechanisms are E — already approved;
the following additional engineering selections do not approve them again.
Earlier unselected OS/listener/permission statements remain dated records; this
revision resolves only B1–B3. Actual host, origin, custody and location deferrals
remain unresolved.

### B1 — approved host platform and qualification boundary

**Checkpoint CT17.I-host-platform: APPROVE** Ubuntu Server **24.04 LTS**, **x86_64
(amd64)**, for the recommended new-VM technical platform, with Docker Engine and
the Docker Compose plugin from Docker's official signed stable apt repository.
This selects a host distribution/architecture and installation channel, not a
provider/account/instance, package-install action or a new application technology.
Keep Java 25/Spring Boot 4.1.1/PostgreSQL 17 and all existing application baselines.
Release images/archives must be qualified for **linux/amd64**; host compatibility
does not prove that a particular image or extension has that platform.

The choice matches the existing Noble-based backend responsibility and officially
supported Docker platform. Prefer the mature compatible LTS over a new host OS,
Docker Desktop, an unreviewed convenience script or an ARM-only offer requiring
unqualified images. An existing authorized team VM with another supported Linux
platform remains an alternative needing its exact compatibility/authority evidence;
this record does not order its replacement. Select and record the exact OS image
and Engine/Compose/package versions before their separately authorized installation;
verify effective OS/kernel/package versions and qualified image identities on the
actual host before application deployment/exposure. Their current installed state
and release compatibility cannot be certified without the selected host/artifacts.

Do not infer capacity from this platform choice. Qualification must measure the
selected Auth/catalogue concurrency, cost-14 real and dummy BCrypt work, JVM/PG/
MinIO memory and startup peaks, filesystem/volume/artifact headroom, transfer and
lecturer-network latency. Keep off-VM builds. Record the actual workload, limits,
results and stop conditions; no RPS, latency target, heap/pool size, disk-retention
promise or availability guarantee is invented. Do not lower BCrypt cost, omit
MinIO or change storage architecture to make a small VM appear sufficient.
CT01's production abuse-control evidence remains required before exposure; this
record supplies no new numerical rate limit or exception.

### B2 — approved engineering ingress/connection bindings

**Checkpoint CT17.I-ingress-bindings: APPROVE** the following values/topology as
engineering bindings for a future separately authorized host. They do not establish
an actual browser origin, firewall grant or externally reachable service.

| Binding | Selected value and invariant |
| --- | --- |
| External browser transport | HTTPS default TCP 443; one actual Leader-approved hostname/origin for SPA and REST, without a second product/API origin. Host 443 maps to TLS on the existing frontend Nginx container port 8080. No privileged in-container listener or extra gateway/service is needed. |
| Internal API upstream | Existing Compose backend:8080, with /api retained. Match exact /api and the /api/ subtree before SPA fallback; use a prefix-preserving upstream URI. Preserve queries, HTTP status, content type and relevant headers; do not intercept API errors into HTML/200. |
| Internal data connectivity | Retain existing service DNS postgres:5432 and MinIO API minio:9000 on private Compose connectivity; no host/public PostgreSQL, MinIO API/console or direct backend port. This reuses inspected service identities, not actual database/bucket/user names or grants. |
| Frontend/backend public configuration | Reuse already approved VITE_API_BASE_URL=/api at build time; backend CORS equals the supplied actual HTTPS origin, no wildcard/localhost fallback. P7 forward-headers-strategy=NONE and regenerated ingress headers remain unchanged. |
| Browser routes | Static shell and real asset 404 behavior; /profile is an additional client route only after the exact CT16 Profile contract validly integrates. It adds no REST endpoint and is not a CT20.I deployment-completion requirement. |

A trusted full-chain certificate must match that actual hostname. Publish A and/or
AAAA only for verified allocated addresses that really serve this ingress; do not
publish untested IPv6 or a guessed DNS record. Reject unexpected Host values.
No public HTTP application/API listener, Actuator/docs/storage-console exception,
CDN or upstream proxy is selected. Certificate acquisition may need a separately
approved port-80 challenge arrangement; B2 does not grant that exposure. SSH is
limited to subsequently approved operator source addresses/identity, not 0.0.0.0/0
or an invented access mechanism. Actual ingress/admin firewall rules and grants
require the reserved inputs below. Verify Docker-published ports against its
packet-filtering path; UFW configuration alone is not proof of private services.

Changing the existing frontend listener to TLS requires a compatible separately
authorized health-check implementation: the current plaintext HTTP loopback check
is no longer a valid TLS-listener probe. Use qualified existing image tooling and
verify intended certificate/hostname behavior; do not disable verification to
claim TLS readiness or introduce a public health route. Liveness/configuration,
real external TLS and selected feature readiness remain separate P4/P10 evidence.

### B3 — approved secret delivery and certificate replacement conventions

**Checkpoint CT17.I-secret-delivery: APPROVE** owner-only **0600** permissions for
the existing ignored root .env when the actual approved checkout/location uses
that input, with a non-public **0700** containing directory under the approved
operator's control. Alternatively use the already approved scoped process
environment; do not invent another secret file/service/location or source .env as
shell code. Actual owner UID, checkout directory, custody and access grants remain
unbound. Exclude real secret inputs from Git, image contexts/layers, CLI arguments,
logs and rendered configuration; forward only each service's consumed variables.
Runtime/admin identities remain P6-distinct; Docker-daemon/operator authority can
read service environments and is privileged, not a secret-isolation boundary.
Do not grant Docker-group/sudo/emergency access or claim least privilege from a
GitHub assignment alone.

Select a **read-only directory bind** for the existing Nginx's serving certificate
chain/private key, after its actual source/target location is approved. The
operator-supplied mapping must let the effective Nginx master UID traverse/read
only required material; select private key mode **0400**, owned by that approved
mapped UID, with owner-traversable restricted directories. A separately authorized
privileged custodian performs replacement. Never solve unreadable keys with
world/group readability or a root backend. Mount only serving material, not CA
account keys/DNS credentials/other application secrets, and never place it in the
SPA document root or image. Actual filesystem ownership/custody remains reserved.

Require certificate/private-key match, full-chain/hostname/validity checks and a
successful Nginx configuration test before a controlled reload, then independently
observe the certificate actually served. A reload request alone is not acceptance;
failed reload can leave the old certificate active. A directory mount must be
qualified with the renewal method's atomic replacement/symlink layout so renewed
files remain visible; do not rely on a stale single-file bind or mount a referenced
key outside the approved directory. Record effective UID, readable paths, served
certificate identity and renewal/reload outcomes without key content. No new CA
client, timer, host path, certificate issuance or operational renewal schedule is
selected or executed. Acquisition/renewal tooling depends on actual domain control,
CA account/custody and the approved network arrangement; it is not silently filled
with a default.

Credential changes require a separately authorized operator procedure and coherent
service reconfiguration/verification; do not overwrite roles/policies, silently
reset volumes or accept old credentials as a fallback. Preserve ADR001 §11.3:
JWT key replacement invalidates old signatures and is an operational incident
action, not ordinary per-user logout. No new multi-key rotation, refresh, revocation,
key generation or maintenance-risk acceptance is approved here.

### Recommended resource and TLS packet — not approved actual bindings

Priority is an already authorized compatible institutional/team VM, if the Leader
can provide its real identity, capacity and access evidence; none is currently
verified. Otherwise the selected **recommendation** is a DigitalOcean Basic Regular
Droplet in Singapore SGP1, 2 shared vCPUs, 4 GiB RAM, 80 GiB SSD, published base
price **USD 24/month**. The 4-vCPU/8-GiB/160-GiB **USD 48/month** tier offers more
headroom if qualification fails. Published specifications/prices and regional
listing were checked on 25 October 2026; account quota/stock, taxes, optional
services and total payable cost are not verified. Neither plan is measured or
certified sufficient for this workload; no account, VM, spend, region availability
guarantee or capacity acceptance is approved. Shared CPU variability especially
matters for BCrypt. A paid dedicated-CPU plan costs more without current evidence
that it is necessary; application builds already occur off-host.

For TLS, prefer a domain-owner-managed publicly trusted certificate/renewal flow
that can supply the selected PEM serving boundary. If none exists, recommend
ACME HTTP-01 through the existing Nginx only when the actual owner authorizes
port 80/challenge location and a qualified host client/lifecycle; it avoids placing
DNS-wide credentials in this VM. DNS-01 is the alternative when HTTP challenge
access is unavailable and scoped DNS automation/custody exists. No wildcard is
needed for one origin. Manual renewal without an accountable tested lifecycle is
not a complete binding. No CA account, client dependency, challenge path, new
host location, port-80 exposure, renewal timer or DNS grant is approved by these
conditional recommendations. Failure to establish this lifecycle blocks exposure.

### Exact remaining inputs, ownership and implementation handoff

| Input | Evidence/status and smallest required decision |
| --- | --- |
| Actual host/provider/account/capacity/budget | Leader supplies or approves real resource identity, OS/architecture compatibility, paid budget/period and owner. Candidate price/specification is advisory; measured workload/capacity remains qualification evidence. |
| Actual origin and DNS | Leader supplies an owned/authorized hostname/zone and DNS operator, actual IP allocations and single HTTPS origin; no placeholder is treated as an endpoint. |
| TLS acquisition/renewal and key custody | Actual CA/client/challenge arrangement, accountable custodian/operator, serving directory/UID mapping and lifecycle/access authorization; B3 is the technical interface, not these grants. |
| Operators and emergency access | GitHub lists @tihh647-ui on CT17/DP01/DP03 and CODEOWNS infrastructure with Leader; this is engineering responsibility only. Leader must name actual deployment/emergency identities, authenticated access method/source restrictions and grants. No operator consent or independent verifier is fabricated. |
| DB/storage/runtime credentials | Leader-approved actual bootstrap/migrator/runtime identities, DB/schema/bucket, grants, JWT and other secret custodians/delivery locations, without publishing values. Do not copy local DB_USER=POSTGRES_USER into production or promote MinIO root to backend credentials. |
| Release and production image qualification | Actual reviewed source/lock/build IDs, linux/amd64 images/checksums, license/maintenance/vulnerability and safe-init evidence. Local golithus MinIO/MC pins remain local-only; no qualified production replacement/build evidence is available. Ordinary artifact selection can be technically approved when that evidence exists; financial/material exceptions remain reserved. |
| Host and artifact locations | Actual approved checkout/.env, serving certificate directory/mounts and transfer/recovery destinations plus ownership/access; no absolute host path is invented. |
| Exposure and verification | Explicit Leader authorization for actual public/admin exposure; separate DP01 implementation/provision/deploy authority, QA01/QA07/DP03 actual component/security/external results and lecturer handoff. No credentials or trial accounts are created here. |

DP01 #71 consumes B1–B3 only after protected integration and separate file/task
authorization; frontend/nginx.conf, Compose, script, template and health checks
still need implementation. Auth @duyduyit supplies actual JWT/login/security,
Course @TamiaoNguyen supplies actual catalogue/detail endpoints and data, Frontend
@heizseung supplies compatible clients; QA01/QA07/DP03 retain independent evidence.
Missing registration/login/security/catalogue producers cannot be replaced by a
healthy container. Final MinIO/encryption/RPO/RTO/retention/recovery commitments,
U33/U34 and other feature contracts remain unchanged and pending.

**CT17_INTERIM_CONCRETE_BINDING_PENDING_LEADER_INPUT.** B1–B3 complete the
independently supported additional engineering selections above; actual binding,
production artifact qualification, implementation authorization and runtime/external
verification remain incomplete. U18 is not closed. No whole CT17 approval or
ready-to-provision/deploy claim is made. All new technical selections remain
**LEADER_APPROVED**, subject to eligible independent
CODEOWNER/last-push review, new-head required checks and resolved review threads.

Primary evidence: [Docker's Ubuntu support/install channel and firewall limitations](https://docs.docker.com/engine/install/ubuntu/),
[Docker privileged operator boundary](https://docs.docker.com/engine/install/linux-postinstall),
[bind-mount semantics](https://docs.docker.com/engine/storage/bind-mounts/),
[Nginx TLS/key/chain requirements](https://nginx.org/en/docs/http/configuring_https_servers.html)
and [configuration/reload behavior](https://nginx.org/en/docs/control.html),
[DigitalOcean specifications/base prices](https://www.digitalocean.com/pricing/droplets)
and [regional listing](https://docs.digitalocean.com/products/droplets/details/availability/),
[ACME challenge trade-offs](https://letsencrypt.org/docs/challenge-types/).
These are vendor engineering/product facts, not CodeMastery resource ownership,
account availability, risk acceptance or executed verification.

## CT17 runtime database privileges and operation gates — 28 October 2026

### Authority, baseline and classification

**CT17-P6-PRIVILEGES-2026-10-28-r1: DELEGATED_TECHNICAL_APPROVAL;
LEADER_APPROVED** for checkpoints
**CT17.I-runtime-privileges** and **CT17.I-operation-gates** below only. Authority is
the Project Leader @chi-thanh-nguyen's explicit **“CodeMastery — PR #110: Delegated
Technical Authority, Contract Approval & Complete Remediation”**, effective
28 October 2026 (ICT). It delegates to AI-assisted development the selection and technical
approval of individual PostgreSQL privileges and an engineering approval matrix for
PR #110. It withholds real VM/provider selection, expenditure, infrastructure
ownership, production credentials and secret custody, SSH/firewall access grants,
production operations, public exposure, recovery-risk acceptance and protected
merge approval. Those remain personal Leader decisions; this AI technical approval
is not a CODEOWNER review.

Baseline: remote main
[`7ec2c43490aeac47a5774ed0c22b31485d607da7`](https://github.com/chi-thanh-nguyen/codemastery/commit/7ec2c43490aeac47a5774ed0c22b31485d607da7);
[PR #110](https://github.com/chi-thanh-nguyen/codemastery/pull/110) head
`16b33c5e8fe9b57ed990a49815173dae8e27f5f6`. Inspected: V001–V006, every backend
persistence adapter, entity, service and the exposed security operations; ADR002
CT20.I, CT03/CT21 catalogue and FE07 CT22.membership; data-model §11; CT17 P1–P10,
F1/F2 and B1–B3. CT17-2026-10-24-r1 P1–P4, CT17-INTERIM-2026-10-24-r2 P5–P10,
CT17-FINAL-2026-10-25-r1 F1/F2 and CT17-INTERIM-BINDINGS-2026-10-25-r1 B1–B3 are
preserved byte-for-byte; only the clauses named under Supersession are refined.

### CT17.I-runtime-privileges — approved runtime privilege matrix

Consumer evidence at the baseline: the backend exposes only POST
`/api/auth/register` and `/api/auth/login`, GET `/api/courses` and
`/api/courses/{courseId}`, GET/PUT/DELETE `/api/courses/{courseId}/membership` and
GET `/api/me/courses`; every other route is denied. Their persistence is Auth
`UserRepositoryImpl` (JPA find by ID/email; registration `save` merges, so SELECT
then INSERT) and Course `CourseJpaRepository`, `CourseContentRepositoryImpl` and
`EnrollmentRepositoryImpl` (native SQL). Assessment, Adaptive, Interaction and
Admin repositories are empty scaffolds. LessonProgress, LearnerSkillMastery and
PathRecommendation entities exist only for Hibernate `validate`, which reads catalog
metadata and needs no table privilege. V001–V006 create 23 tables and no sequence
(application-supplied UUIDs), view, routine or trigger.

| Relation (owner) | Approved runtime privilege | Consumer and operation | Status / basis |
| --- | --- | --- | --- |
| `users` (auth) | SELECT, INSERT | Registration email check and insert; login; JWT current-account check; CT01.identity instructor names | GRANTED — CT01.core, CT01.identity |
| `courses` (course) | SELECT; UPDATE (id) | Catalogue/search/detail `SELECT *`, access snapshots, My Courses join; UPDATE (id) only enables the membership `FOR UPDATE` row lock | GRANTED — CT03/CT21 catalogue, FE07 CT22.membership |
| `chapters` (course) | SELECT | Published syllabus, canonical lesson order, access snapshots | GRANTED — CT03/CT21 |
| `lessons` (course) | SELECT | Published syllabus (entity load), access snapshots | GRANTED — CT03/CT21 |
| `learning_materials` (course) | SELECT (id, lesson_id, publication_status) | Membership access-snapshot join only; payload and object-key columns stay denied | GRANTED — CT03 |
| `enrollments` (course) | SELECT, INSERT; UPDATE (status) | Membership find, first join, join/leave status transitions, My Courses, access snapshots | GRANTED — FE07 CT22.membership |
| `lesson_progress` (course) | none | No consumer | DEFERRED — CT22.progress |
| `skills` (adaptive) | none | No consumer | DEFERRED — Adaptive contracts |
| `skill_prerequisites` (adaptive) | none | No consumer | DEFERRED — Adaptive contracts |
| `lesson_skills` (adaptive) | none | No consumer | DEFERRED — Adaptive contracts |
| `quizzes` (assessment) | none | No consumer | DEFERRED — Assessment contracts |
| `questions` (assessment) | none | No consumer | DEFERRED — Assessment contracts |
| `quiz_questions` (assessment) | none | No consumer | DEFERRED — Assessment contracts |
| `attempts` (assessment) | none | No consumer | DEFERRED — Assessment contracts |
| `attempt_answers` (assessment) | none | No consumer | DEFERRED — Assessment contracts |
| `assignments` (assessment) | none | No consumer | DEFERRED — Assessment contracts |
| `submissions` (assessment) | none | No consumer | DEFERRED — Assessment contracts, CT09 |
| `learner_skill_masteries` (adaptive) | none | No consumer | DEFERRED — Adaptive contracts |
| `path_recommendations` (adaptive) | none | No consumer | DEFERRED — Adaptive contracts |
| `discussion_posts` (interaction) | none | No consumer | DEFERRED — Interaction contracts |
| `notifications` (interaction) | none | No consumer | DEFERRED — Interaction contracts |
| `content_reports` (interaction) | none | No consumer | DEFERRED — Interaction contracts |
| `audit_logs` (admin) | none | No consumer | DEFERRED — CT12; UPDATE/DELETE permanently prohibited |
| `flyway_schema_history` (migrator) | none | Migrator only | PROHIBITED — P6 |

The runtime role additionally keeps only CONNECT on the application database and
USAGE on `public` (P6). No sequence privilege is granted, because none exists. On
every relation the runtime role never receives TRUNCATE, REFERENCES, TRIGGER or
MAINTAIN, DDL, ownership, grant options or role membership. These are permanent
prohibitions, not deferrals. The matrix is implemented verbatim as
`RUNTIME_PRIVILEGE_MATRIX` in `infrastructure/vm/deploy.sh`, together with every
reviewed column. CI checks that its privileges equal this table.

**Course-root lock.** FE07 requires membership mutations to hold a PostgreSQL row
lock on the published Course for the whole transaction. PostgreSQL 17 requires
UPDATE on at least one column for every row-locking clause; this was verified on
`postgres:17.11-bookworm`, and FOR KEY SHARE needs it too. The alternatives were
rejected:

- Table-level UPDATE would make publication, instructor ownership, content and
  adaptive settings writable.
- UPDATE on a business column grants that business capability.
- Advisory locks or a weaker lock mode change the approved semantics, and still need
  the privilege or no longer block publication changes. FE07 already rejected
  per-member advisory locks.
- A new SECURITY DEFINER migration adds a DDL object that runs with migrator rights.
- Removing the lock reintroduces the race FE07 excludes.

Selected: **column-level UPDATE (id) only**, with no application change. Every other
`courses` column is denied (42501). Every V002–V004 child foreign key is
`ON UPDATE NO ACTION`, so a Course with any chapter, enrollment, skill, quiz or
assignment cannot be re-keyed (23503). The residual is recorded, not accepted on the
Leader's behalf: a compromised runtime identity could rewrite the UUID of a Course
that has no dependent row. That discloses no data and grants no capability, and it
is smaller than any alternative that preserves the approved lock.

### Fail-closed provisioning and verification

Provisioning, post-migration hardening and the pre-ingress verification apply one
policy:

1. **Reconcile what the script owns.** In the role-policy transaction, revoke every
   runtime privilege on every `public` relation, column grants included and without
   CASCADE. Then grant exactly the matrix on reviewed tables that exist. Detected
   excess is logged by object and privilege name before it is removed. Repeated
   provisioning is idempotent.
2. **Stop on anything else, before ingress.** Each of these stops the release and
   requires explicit operator reconciliation:
   - an unreviewed relation (table, partitioned table, view, materialized view,
     foreign table or sequence), or a reviewed name of another kind;
   - a changed reviewed column set, or a reviewed table missing after migration;
   - any effective table- or column-level privilege that differs from the matrix,
     whether from PUBLIC, another grantor or inheritance, including MAINTAIN;
   - any routine in `public`;
   - default privileges that would grant future tables or sequences to PUBLIC or to
     the runtime role;
   - any runtime role membership, INHERIT or SET;
   - non-migrator ownership;
   - a grant the runtime role passed on to another role.

   A future migration therefore receives no runtime privilege automatically, and
   cannot widen a table-level grant with a new column.
3. **Verify effective state, not intended statements.** Before migration the script
   checks existing objects; after Flyway it checks completeness. Rolled-back probes
   confirm that the runtime role can take the Course row lock and cannot change
   account roles or publication, delete memberships, read answer keys or material
   payloads, or erase audit records. The deployment record states the verified
   matrix identifier.

Startup order is unchanged. On a fresh database, the backend starts after Flyway
with no table grants. Hardening and verification then complete before the private
TLS check and ingress.

### Deferred privileges and extension procedure

Deferred rows remain without privileges until all of the following hold:

1. The release image contains an implemented consumer of an approved owning contract.
2. The extension grants the least privilege that consumer exercises:
   - table-level SELECT/INSERT only for whole-row access;
   - UPDATE only on the columns that the approved transition changes;
   - DELETE only where an approved contract defines deletion;
   - sequence USAGE/SELECT/UPDATE only for sequences that are actually used.
   Data-model §11 immutability, history and durability rules are never weakened by a
   grant.
3. A new dated revision of this record, `RUNTIME_PRIVILEGE_MATRIX`, its reviewed
   columns and the CI consumer tests change together. CI applies the real V001+
   migrations, so a new table or column fails until it is listed.
4. The applicable owning-module, infrastructure and Leader CODEOWNER reviews and
   protected integration are complete.
5. On the target host, the pre-ingress verification equals the new matrix.

### CT17.I-operation-gates — authority boundaries and ingress evidence

| Subcommand | State changed | Required non-secret authority/evidence references |
| --- | --- | --- |
| `preflight` | None (read-only) | None |
| `load` | Host image store only (release artifacts) | `--deployment-authorization` (**new**); the archive SHA-256 and identity are verified before loading |
| `provision` | PostgreSQL roles/grants, PostgreSQL/MinIO containers, MinIO initializer state | `--provision-authorization`, `--initializer-qualification` |
| `deploy` | Roles/grants again (provision and hardening), backend, frontend and public ingress | `--provision-authorization`, `--deployment-authorization`, `--public-exposure-authorization`, `--packet-filter-verification`, `--initializer-qualification` |
| `all` | All of the above | The union of the above |

On a host with existing project containers or volumes, every state-changing
subcommand also requires the P9 update attestations and `--update-authorization`.
`load` now requires the deployment reference because it is the first deployment
step that changes the host. References record separately made Leader or operator
decisions. The script cannot verify them and never treats them as approval.

For B2, the script verifies from Docker Engine state that only the frontend
publishes, exactly container `8080/tcp` to host 443, on the declared non-loopback
address or all addresses, without publish-all or host/container network modes. The
deployment record lists the actual bindings. Host packet filtering, including
Docker's forwarding path, and SSH source restrictions are outside what the script
can observe. They require the operator evidence reference and the DP03
outside-network scan, and the record marks them NOT_VERIFIED. No address, firewall
rule or SSH grant is selected here.

### Supersession, exclusions and acceptance

The following clauses are refined prospectively; the original text remains authentic
history:

- **P6 "selected tables/sequences' required DML privileges"** now means the matrix
  above.
- **P6 "Provision future required application DML grants … before feature
  readiness"** now follows the extension procedure.
- **P6 "Actual role names, credentials/access grants and provisioning authority are
  Leader-reserved"** and **P2 "Actual identities, database grants, policies,
  custody and provisioning remain unapproved inputs"** are narrowed only for the
  technical grant policy, which the 28 October delegation expressly covers. Actual
  role names, credentials, custody and the authority to execute provisioning on a
  real database remain Leader-reserved.
- **F1** already requires runtime grants to be provisioned "independently from
  reviewed provisioning inputs". This matrix is that input, and a restored database
  must pass the same reconciliation and verification.

Exclusions:

- P1–P4's statement that CT17 does not approve CT22 membership still holds. This
  record approves no API, does not count CT22 as CT20.I evidence, and does not
  authorize exposure. It provisions the DML required by the separately approved and
  implemented FE07 producer.
- CT20.I, CT01, CT03, CT21 and CT22, V001–V006, application code, MinIO policy and the
  fail-closed backend health check are unchanged.
- The known Auth `/actuator/health` HTTP 401 remains an Auth-owned blocker.

Acceptance:

- **Technical:** the PR #110 CI and local evidence covers exact effective grants on the
  real Flyway schema, the denials, real Auth/catalogue/membership operations under
  the matrix, the held Course-root lock, idempotency and every drift case above. That
  evidence is not a deployment claim.
- **Operational:** on the actual host, a separately authorized `deploy` must report
  in its `DB runtime grants` record line that the exact
  CT17-P6-PRIVILEGES-2026-10-28-r1 matrix was verified before ingress.

**CT17.I-runtime-privileges** and **CT17.I-operation-gates** are
LEADER_APPROVED. Concrete bindings remain
PENDING_LEADER_INPUT, and runtime verification is pending.
`docs/deployment.md` and `.github/workflows/` have the Leader as sole CODEOWNER;
`infrastructure/` adds @tihh647-ui. The existing sole-Leader CODEOWNER/authorship
conflict is unchanged: eligible independent human review, last-push approval,
required checks and protected merge remain mandatory. The investigation, this
record, the implementation and its tests were produced through AI-assisted
development under the delegation. No provisioning, deployment, exposure or merge was
performed or authorized.

### CT17-P6-PRIVILEGES-2026-11-13-r2 — AU07 extension (LEADER_APPROVED, 13 November 2026)

**LEADER_APPROVED** on 13 November 2026 (Asia/Ho_Chi_Minh) by the explicit instruction
of Project Leader and repository Owner @chi-thanh-nguyen, "CodeMastery — PR #239
Leader Authorization, Contract Synchronization & PR Finalization". It is recorded with
`AU07-IMPL-2026-11-13-r1` in
[ADR006 §10.2](decisions/006-batch-02-contract-approvals.md#102-leader-decisions--au07-impl-2026-11-13-r1-and-ct17-p6-privileges-2026-11-13-r2).
The revision was prepared for
[PR #239](https://github.com/chi-thanh-nguyen/codemastery/pull/239) under the extension
procedure above, through AI-assisted development.

The r1 record and table above are unchanged history. The effective matrix is r1 with
the two rows below replacing or adding the named relations; no other privilege, role
elevation or change is approved.

| Relation (owner) | Approved runtime privilege | Consumer and operation | Status / basis |
| --- | --- | --- | --- |
| `users` (auth) | SELECT, INSERT; UPDATE (password_hash) | r1 consumers, plus the AU07 User-first `FOR UPDATE` lock and the reset password-hash update | r1 SELECT, INSERT unchanged; UPDATE (password_hash) LEADER_APPROVED — r2 |
| `password_reset_tokens` (auth) | SELECT, INSERT, DELETE | AU07 capability lookup, issue/replace (delete then insert) and single-use consumption; no UPDATE and no row lock | LEADER_APPROVED — r2 (CT02-TOKEN-2026-11-09-r1, V007) |

Least privilege exercised by the consumer (`PasswordResetService`, `UserRepositoryImpl`):

| Operation | SQL | Privilege |
| --- | --- | --- |
| Request worker and confirmation lock the User first | `SELECT … FROM users … FOR UPDATE` | SELECT plus UPDATE on at least one column (PostgreSQL row-lock rule above), supplied by UPDATE (password_hash) |
| Confirmation changes the credential | `UPDATE users SET password_hash = ? WHERE id = ?` | UPDATE (password_hash) |
| Issue/replace one outstanding capability | `DELETE … WHERE user_id = ?` then `INSERT` | SELECT, DELETE, INSERT on `password_reset_tokens` |
| Resolve and re-read a capability after the User lock | `SELECT … WHERE token_digest = ?` | SELECT |
| Single-use consumption | `DELETE … WHERE user_id = ? AND token_digest = ?` | DELETE |

No token-row lock is taken. Every capability write happens under the User row lock, so
a re-read after acquiring that lock is serialized without UPDATE on
`password_reset_tokens`. The runtime role still cannot do any of the following:

- change email, role, account status or display name;
- rewrite capability digests or expiry;
- truncate either table.

`verify_role_separation` probes the role, email/status and expiry denials. Security
impact: a compromised runtime role could overwrite any password hash, which the reset
transition inherently requires.

The following stay synchronized with this record:

- `RUNTIME_PRIVILEGE_MATRIX` in `infrastructure/vm/deploy.sh`;
- `test-deploy-privilege-matrix-contract.sh`, which checks the r1 table plus these r2
  rows against the script and pins the r1 history;
- `test-postgres-privilege-matrix-containment.sh`: grants and denials on the real
  V001–V007 schema;
- `test-runtime-real-backend-privileges.sh`: a real reset confirmation under the
  runtime role, and a restart with recovery activated by synthetic inputs, where the
  request worker issues capabilities (User lock, token DELETE/INSERT) under the same
  role.

Remaining gates:

- `@tihh647-ui` infrastructure review and independent CODEOWNER review;
- protected integration;
- the operational host verification in the acceptance above, under a separately
  authorized deployment;
- V007 execution on any shared or production database. Flyway applies pending
  migrations automatically when the backend starts, so once V007 is on `main`, starting
  any build of it against such a database executes V007. That execution stays
  Leader-reserved (`AU07-IMPL-2026-11-13-r1` item 2) and must be authorized before any
  deployment of that build.

This approval executes no provisioning on any real database.


### CT17-P6-PRIVILEGES-2026-11-16-r3 — ADR014 refresh sessions (DELEGATED_APPROVED, 16 November 2026)

**DELEGATED_TECHNICAL_APPROVAL; DELEGATED_APPROVED** on 16 November 2026 by Claude Code
under the Project Leader's explicit instruction "CodeMastery — End-to-End Hybrid
Authentication Upgrade with Delegated Technical Approval", recorded in
[ADR014 §8](decisions/014-hybrid-refresh-session-authentication.md#8-ingress-and-database-privileges).
It follows the extension procedure above: the implemented consumer
(`RefreshSessionService`, `UserRepositoryImpl`, the session-bound JWT filter and the
AU07 reset transaction), `RUNTIME_PRIVILEGE_MATRIX` and the CI tests change together.
The two relations are new in V008 and are outside the CT17-P6-PLAN ceiling, which
lists only earlier relations. This is not personal Leader approval or review.

The r1 and r2 records above are unchanged history. The effective matrix is r1 plus r2
plus the two rows below; no other privilege, role elevation or change is approved.

| Relation (owner) | Approved runtime privilege | Consumer and operation | Status / basis |
| --- | --- | --- | --- |
| `refresh_sessions` (auth) | SELECT, INSERT, DELETE; UPDATE (revoked_at) | sign-in insert, per-request session join, rotation `FOR UPDATE` lock, revocation (logout, replay, reset, cap, locked account), purge of expired sessions | DELEGATED_APPROVED — r3 (AUTH-SESSION-2026-11-16-r1, V008) |
| `refresh_tokens` (auth) | SELECT, INSERT, DELETE; UPDATE (consumed_at) | digest lookup, issue and successor insert, single consumption, purge with the session | DELEGATED_APPROVED — r3 (AUTH-SESSION-2026-11-16-r1, V008) |

Least privilege exercised by the consumer:

| Operation | SQL | Privilege |
| --- | --- | --- |
| Sign-in | `SELECT … FROM users WHERE id = ? FOR NO KEY UPDATE` (ADR014 §15 account lock); `INSERT` session and first token; `UPDATE refresh_sessions SET revoked_at` for a presented cookie's session (another account's in its own transaction) and beyond the 10-session cap | `users` row lock via the existing r2 UPDATE (password_hash), no new grant; INSERT on both; UPDATE (revoked_at) |
| Protected request | `SELECT … FROM users JOIN refresh_sessions …` | SELECT on both |
| Rotation | `SELECT … FROM refresh_tokens WHERE token_digest = ?`; `SELECT … FROM refresh_sessions … FOR UPDATE`; `UPDATE refresh_tokens SET consumed_at`; `INSERT` successor | SELECT; row lock via UPDATE (revoked_at); UPDATE (consumed_at); INSERT |
| Logout, replay, reset, locked account | `UPDATE refresh_sessions SET revoked_at …` | UPDATE (revoked_at) |
| Purge after sign-in | `SELECT … FOR UPDATE SKIP LOCKED` of expired sessions; `DELETE` their tokens, then the sessions | row lock via UPDATE (revoked_at); DELETE on both |

The runtime role still cannot do any of the following:

- change a session's owner, creation time or absolute expiry;
- rewrite a token's digest, session, issue time or expiry;
- truncate either table.

A compromised runtime role could insert or un-revoke sessions, which the sign-in and
revocation transitions inherently require (`users` SELECT/INSERT already allow it to
create accounts). `verify_role_separation` probes the expiry/owner and digest denials.

The following stay synchronized with this record:

- `RUNTIME_PRIVILEGE_MATRIX` in `infrastructure/vm/deploy.sh` (27 relations);
- `test-deploy-privilege-matrix-contract.sh`, which pins r1 history, the r2 rows and
  these r3 rows against the script;
- `test-postgres-privilege-matrix-containment.sh`: grants and denials on the real
  V001–V008 schema;
- `test-runtime-real-backend-privileges.sh`: real sign-in, rotation, logout and reset
  revocation under the runtime role (Flyway history now 8/8).

Remaining gates: `@tihh647-ui` infrastructure review and independent CODEOWNER
review; protected integration; V008 execution and these grants on any shared or
production database (Flyway applies V008 when any build containing it starts, so that
execution must be authorized before deploying such a build; Leader-reserved). This
approval executes no provisioning on any real database.

### CT17-P6-PRIVILEGES-2026-11-17-r4 — CO08 progress and resume (within the ADR012 §19.2 ceiling, 17 November 2026)

Prepared for CO08 ([#174](https://github.com/chi-thanh-nguyen/codemastery/issues/174),
[PR #248](https://github.com/chi-thanh-nguyen/codemastery/pull/248)) under the Project
Leader's explicit instruction of 17 November 2026 to implement CO08 together with its
strictly necessary database-grant support. It follows the extension procedure above
and stays inside the DELEGATED_APPROVED `CT17-P6-PLAN-2026-11-15-r2` ceiling
([ADR012 §19.2](decisions/012-batch-07-contract-approvals.md#192-ct17-p6-plan-2026-11-15-r2--least-privilege-ceiling-for-batch-27-consumers)):
the implemented consumer (`LessonProgressService`, `EnrollmentRepositoryImpl`), V009,
`RUNTIME_PRIVILEGE_MATRIX` and the CI tests change together, and every privilege below
is exercised by that consumer's SQL. No approval beyond that ceiling is claimed; this
is not personal Leader review.

The r1–r3 records above are unchanged history. The effective matrix is r1 plus r2
plus r3, with the two rows below replacing the named relations; no other privilege,
role elevation or change is introduced.

| Relation (owner) | Runtime privilege | Consumer and operation | Status / basis |
| --- | --- | --- | --- |
| `enrollments` (course) | SELECT, INSERT; UPDATE (status, resume_lesson_id, last_visited_at) | r1 membership consumers, plus the CT22 Enrollment `FOR UPDATE` lock, the completion status reconciliation and the atomic visit write of pointer and server time; reviewed columns now include V009 `last_visited_at` | r4 within ceiling (CT22-PROGRESS-2026-11-09-r1, CT22-RESUME-2026-11-09-r1, CT22-RESUME-SCHEMA-2026-11-09-r1) |
| `lesson_progress` (course) | SELECT, INSERT; UPDATE (completed) | progress reads in canonical order, idempotent manual completion (`INSERT … ON CONFLICT … DO UPDATE SET completed = true`) | r4 within ceiling (CT22-PROGRESS-2026-11-09-r1) |

Least privilege exercised by the consumer:

| Operation | SQL | Privilege |
| --- | --- | --- |
| Completion and visit lock the Course root first | `SELECT id FROM courses WHERE id = ? FOR UPDATE` | existing r1 SELECT and UPDATE (id); no new grant |
| Then the self Enrollment | `SELECT … FROM enrollments … FOR UPDATE` | SELECT plus the existing UPDATE (status) |
| Manual completion | `INSERT INTO lesson_progress … ON CONFLICT (enrollment_id, lesson_id) DO UPDATE SET completed = true` | SELECT, INSERT, UPDATE (completed) |
| Completion reconciliation | `UPDATE enrollments SET status = ? WHERE id = ?` | existing UPDATE (status) |
| Visit | `UPDATE enrollments SET resume_lesson_id = ?, last_visited_at = clock_timestamp() WHERE id = ?` | UPDATE (resume_lesson_id, last_visited_at) |
| Progress, resume and summary reads | `SELECT` over `courses`, `chapters`, `lessons`, `enrollments`, `lesson_progress` | SELECT |

The runtime role still cannot do any of the following:

- change an Enrollment's id, learner or Course, or delete it;
- update `mastery_skipped` on an existing row (CT22.atomic, CO15, adds UPDATE
  (mastery_skipped) within the ceiling when it is implemented) or delete progress rows;
- truncate either table.

A compromised runtime role could mark any Lesson complete or rewrite any learner's
resume pointer and visit time, which the approved transitions inherently require.
The table-level INSERT on `lesson_progress` also covers `mastery_skipped`: that column
is `NOT NULL` without a default, and manual completion inserts it explicitly as
`false`. A compromised runtime role could therefore insert a new progress row with
`completed = true, mastery_skipped = true`; the V002 check constraint still rejects a
skip without completion. This is within the ADR012 §19.2 ceiling, which grants INSERT
on `lesson_progress` for CT22.progress and CT22.atomic, and no API path reaches it: the
completion operation accepts only `{"completed":true}` and never a skip flag. Removing
that column from the INSERT grant would need a schema default, which no approved
contract provides. `verify_role_separation` probes the re-ownership, mastery-skip
update and progress-deletion denials; the containment test also records the accepted
INSERT capability.

**Upgrade of an existing V008 database (independent-review remediation, 17 November
2026).** `deploy` and `provision` run `provision_db_roles` before the backend starts,
so on an existing V008 database the r4 grant `UPDATE (last_visited_at)` and the
reviewed column list name a column that only V009 creates; the first r4 revision
therefore failed with `column "last_visited_at" of relation "enrollments" does not
exist`. `RUNTIME_MIGRATION_ADDED_COLUMNS` now names that one column and the migration
that adds it (V009). Pre-migration provisioning defers it only when the database is
exactly the V008 predecessor: the column is absent and the Flyway history holds
exactly the successful versioned migrations 1–8, each once. It then grants every other
r4 privilege and checks the column inventory without that column. Any other state
(missing, extra, failed, repeatable or out-of-sequence history rows, no history, a
recorded V009 without its column, unreviewed columns or excess privileges) defers
nothing and fails closed. After Flyway V009, `harden_after_migration` grants the whole
matrix and the complete verification requires it exactly. The matrix rows, their
privileges and the r1–r4 records are unchanged; a fresh database and an already
migrated V009 database defer nothing. When the column already exists, provisioning
treats the schema as current and grants exactly the r4 matrix, as every earlier
revision did for existing tables. Provisioning never grants beyond the matrix, and
Flyway and the deploy gate own the migration state. If the column was added by hand to
a V008 database, or the Flyway history is missing or has a failed V009 row, the real
backend's Flyway refuses to start. `deploy` then stops before hardening and ingress,
and its Flyway-history gate also rejects failed rows.

The following stay synchronized with this record:

- `RUNTIME_PRIVILEGE_MATRIX` and `RUNTIME_MIGRATION_ADDED_COLUMNS` in `infrastructure/vm/deploy.sh` (27 relations, V001–V009);
- `test-deploy-privilege-matrix-contract.sh`, which pins r1 history and the r2, r3 and
  r4 rows against the script;
- `test-postgres-privilege-matrix-containment.sh`: grants and denials on the real
  V001–V009 schema, and the V008/r3 → pre-migration provisioning → V009 → r4 upgrade
  with its fail-closed drift cases on a fresh cluster;
- `test-runtime-real-backend-privileges.sh`: real completion, visit and resume reads
  under the runtime role (Flyway history now 9/9), and the same upgrade with real
  Flyway stopped at V008 and then run to V009 by the backend.

Remaining gates: owning-module (Course @TamiaoNguyen), `@tihh647-ui` infrastructure and
independent Leader CODEOWNER review; protected integration; V009 execution and these
grants on any shared or production database (Flyway applies V009 when any build
containing it starts, so that execution must be authorized before deploying such a
build; Leader-reserved). This revision executes no provisioning on any real database.

### CT17-P6-PRIVILEGES-2026-11-22-r5 — AD07 skill graph (within the ADR012 §19.2 ceiling, 22 November 2026)

Prepared for AD07 ([#165](https://github.com/chi-thanh-nguyen/codemastery/issues/165),
[PR #264](https://github.com/chi-thanh-nguyen/codemastery/pull/264)) under the Project
Leader's explicit instruction of 22 November 2026 to complete AD07 together with the
minimum runtime privileges it needs, inside the approved ceiling and with a dated record,
privilege tests and CI evidence. It follows the extension procedure above and stays inside
the DELEGATED_APPROVED `CT17-P6-PLAN-2026-11-15-r2` ceiling
([ADR012 §19.2](decisions/012-batch-07-contract-approvals.md#192-ct17-p6-plan-2026-11-15-r2--least-privilege-ceiling-for-batch-27-consumers)):
the implemented consumer (`SkillGraphService`, `SkillRepositoryImpl` and its three JPA
repositories), `RUNTIME_PRIVILEGE_MATRIX` and the CI tests change together, and every
privilege below is exercised by that consumer's SQL. No migration is involved: the three
relations exist since V003. No approval beyond that ceiling is claimed; this is not
personal Leader review.

The r1–r4 records above are unchanged history. The effective matrix is r1 plus r2, r3 and
r4, with the three rows below replacing the named relations; no other privilege, role
elevation or change is introduced. Each row equals its ceiling row.

| Relation (owner) | Runtime privilege | Consumer and operation | Status / basis |
| --- | --- | --- | --- |
| `skills` (adaptive) | SELECT, INSERT, DELETE; UPDATE (name, description) | graph read and CT40 lookups; create; replace name/description; delete of an unreferenced skill | r5 within ceiling (CT05-MUTATION-2026-11-08-r1, CT05-BINDINGS-2026-11-08-r1, CT40-SKILL-TAG-2026-11-08-r1) |
| `skill_prerequisites` (adaptive) | SELECT, INSERT, DELETE | graph read; add and remove an edge | r5 within ceiling (CT05-MUTATION-2026-11-08-r1) |
| `lesson_skills` (adaptive) | SELECT, INSERT, DELETE | graph read; map and unmap a Lesson | r5 within ceiling (CT05-MAPPING-2026-11-08-r1) |

Least privilege exercised by the consumer:

| Operation | SQL | Privilege |
| --- | --- | --- |
| Every graph write locks the Course root first (CO14 guard) | `SELECT id FROM courses WHERE id = ? FOR UPDATE` | existing r1 SELECT and UPDATE (id); no new grant |
| Owner ownership and Lesson ancestry (CT03) | `SELECT` over `courses`, `chapters`, `lessons` | existing r1 SELECT |
| Graph read and CT40 lookups | `SELECT … FROM skills / skill_prerequisites / lesson_skills WHERE …` | SELECT |
| Create skill, add edge, map Lesson | `INSERT INTO skills / skill_prerequisites / lesson_skills …` (Hibernate merges an assigned ID with a SELECT first) | SELECT, INSERT |
| Replace a skill | `UPDATE skills SET name = ?, description = ? WHERE course_id = ? AND id = ?` | UPDATE (name, description); SELECT for the predicate |
| Delete skill, remove edge, unmap | `DELETE FROM skills / skill_prerequisites / lesson_skills WHERE …` | DELETE, SELECT |
| Referenced skill delete | the V004/V005 `NO ACTION` foreign keys from `questions` and `learner_skill_masteries` refuse it with SQLSTATE 23503, translated to 409 | none: PostgreSQL runs referential checks with the table owner's rights |

The runtime role still cannot do any of the following:

- move a skill to another Course, or rewrite an edge or a mapping in place (no UPDATE on
  `course_id`, `skill_prerequisites` or `lesson_skills`);
- read or write `questions`, `quizzes`, `quiz_questions` or `learner_skill_masteries`;
- truncate any of the three tables.

A compromised runtime role could create, rename or delete skills, edges and mappings of
any Course, which the approved CT05 commands inherently require; Course scoping, the
cycle rule and Lesson ancestry stay application invariants. Referential integrity still
refuses a mapping to a missing Lesson and the deletion of a skill that a Question,
mastery row, edge or mapping references.

The following stay synchronized with this record:

- `RUNTIME_PRIVILEGE_MATRIX` in `infrastructure/vm/deploy.sh` (27 relations, V001–V009),
  with `verify_role_separation` probing the three in-place UPDATE denials;
- `test-deploy-privilege-matrix-contract.sh`, which pins r1 history and the r2, r3, r4 and
  r5 rows against the script;
- `test-postgres-privilege-matrix-containment.sh`: the AD07 graph SQL, the referenced-delete
  refusal (23503) and the in-place UPDATE and TRUNCATE denials on the real schema;
- `test-runtime-real-backend-privileges.sh`: the eight AD07 operations over HTTP under the
  runtime role, including the referenced-delete 409.

Remaining gates: owning-module (Adaptive @thanhtran0311), `@tihh647-ui` infrastructure and
independent Leader CODEOWNER review; protected integration; these grants on any shared or
production database (Leader-reserved). This revision executes no provisioning on any real
database.

### CT17-P6-PRIVILEGES-2026-11-22-r6 — AS05 question bank (within the ADR012 §19.2 ceiling, 22 November 2026)

Prepared for AS05 ([#156](https://github.com/chi-thanh-nguyen/codemastery/issues/156),
[PR #259](https://github.com/chi-thanh-nguyen/codemastery/pull/259)) under the Project Leader's
explicit instruction of 22 November 2026 to complete AS05 together with the runtime privileges its
implemented SQL needs, inside the approved ceiling. It follows the extension procedure above and
stays inside the DELEGATED_APPROVED `CT17-P6-PLAN-2026-11-15-r2` ceiling
([ADR012 §19.2](decisions/012-batch-07-contract-approvals.md#192-ct17-p6-plan-2026-11-15-r2--least-privilege-ceiling-for-batch-27-consumers)):
the implemented consumer (`QuestionBankService`, `QuestionRepositoryImpl` and
`QuestionJpaRepository`), `RUNTIME_PRIVILEGE_MATRIX` and the CI tests change together, and every
privilege below is exercised by that consumer's SQL. It depends on r5: CT40 scope comes from AD07's
`SkillGraphService`, whose reads use the r5 `skills` grant. No migration is involved. No approval
beyond that ceiling is claimed; this is not personal Leader review.

The r1–r5 records above are unchanged history. The effective matrix is r1 plus r2, r3, r4 and r5,
with the three rows below replacing the named relations; no other privilege, role elevation or
change is introduced. `questions` equals its ceiling row; the two column-level SELECT rows are
narrower than their ceiling rows.

| Relation (owner) | Runtime privilege | Consumer and operation | Status / basis |
| --- | --- | --- | --- |
| `questions` (assessment) | SELECT, INSERT, DELETE; UPDATE (skill_id, prompt, kind, difficulty, options, correct_options) | owner list and read (answer keys included), create, full replacement of an unused definition, delete of an unreferenced one, the lifecycle row lock | r6 within ceiling (CT23-QUESTIONS-2026-11-09-r1, ADR007 §4) |
| `quiz_questions` (assessment) | SELECT (id, question_id) | the delete-time QuizQuestion reference check | r6 within ceiling (ADR007 §4) |
| `attempt_answers` (assessment) | SELECT (id, question_id) | the replace- and delete-time AttemptAnswer reference check | r6 within ceiling (ADR007 §4) |

Least privilege exercised by the consumer:

| Operation | SQL | Privilege |
| --- | --- | --- |
| Every write locks the Course root first (CO14 guard) | `SELECT id FROM courses WHERE id = ? FOR UPDATE` | existing r1 SELECT and UPDATE (id); no new grant |
| CT40 scope (AD07) | `SELECT … FROM skills WHERE course_id = ?` | existing r5 SELECT |
| List, count and read | `SELECT … FROM questions WHERE skill_id IN (…)` | SELECT |
| Lifecycle row lock | `SELECT * FROM questions WHERE id = ? AND skill_id IN (…) FOR UPDATE` | SELECT plus any column UPDATE |
| Create | Hibernate merge of an assigned ID: `SELECT`, then `INSERT INTO questions …` | SELECT, INSERT |
| Replace | `UPDATE questions SET correct_options = ?, difficulty = ?, kind = ?, options = ?, prompt = ?, skill_id = ? WHERE id = ?` | UPDATE on those six columns |
| Reference checks | `SELECT count(qq.id) FROM quiz_questions qq WHERE qq.question_id = ?`; the same over `attempt_answers` | SELECT (id, question_id) on each |
| Delete | `DELETE FROM questions WHERE id = ?` | DELETE; the V004 `NO ACTION` foreign keys from `quiz_questions` and `attempt_answers` are checked with the table owner's rights |

The runtime role still cannot do any of the following:

- re-key a question (no UPDATE on `questions.id`) or truncate `questions`;
- read a quiz's membership order or quiz IDs, or a learner's captured selections and correctness
  (`quiz_questions.quiz_id`/`position`, `attempt_answers.attempt_id`/`selected_options`/`is_correct`);
- write `quiz_questions`, `attempt_answers`, `quizzes` or `attempts`.

A compromised runtime role could read every answer key and create, replace or delete questions of
any Course, which the approved owner operations inherently require; Course scoping, the
used-definition freeze and the reference rules stay application invariants, and referential
integrity still refuses deleting a referenced question. The earlier "no answer-key read" probe is
replaced by the re-key and captured-selection denials, because CT23 owner reads include keys.

The following stay synchronized with this record:

- `RUNTIME_PRIVILEGE_MATRIX` in `infrastructure/vm/deploy.sh` (27 relations, V001–V009), with
  `verify_role_separation` probing the re-key and captured-selection denials;
- `test-deploy-privilege-matrix-contract.sh`, which pins r1 history and the r2–r6 rows;
- `test-postgres-privilege-matrix-containment.sh`: the AS05 SQL, the referenced-delete refusal
  (23503) and the denials above on the real schema;
- `test-runtime-real-backend-privileges.sh`: the five AS05 operations over HTTP under the runtime
  role, including the used-definition and referenced-delete 409s.

Remaining gates: owning-module (Assessment @minhtan29042006), `@tihh647-ui` infrastructure and
independent Leader CODEOWNER review; protected integration (r5 is on `main` since PR #264,
`dfd816c`); these grants on any shared or production database (Leader-reserved). This revision
executes no provisioning on any real database.

### CT17-P6-PRIVILEGES-2026-11-22-r7 — AS06 quiz composition (within the ADR012 §19.2 ceiling, 22 November 2026)

Prepared for AS06 ([#157](https://github.com/chi-thanh-nguyen/codemastery/issues/157),
[PR #260](https://github.com/chi-thanh-nguyen/codemastery/pull/260)) under the Project Leader's
explicit instruction of 22 November 2026 to complete AS06 together with the runtime privileges its
implemented SQL needs, inside the approved ceiling. It follows the extension procedure above and
stays inside the DELEGATED_APPROVED `CT17-P6-PLAN-2026-11-15-r2` ceiling
([ADR012 §19.2](decisions/012-batch-07-contract-approvals.md#192-ct17-p6-plan-2026-11-15-r2--least-privilege-ceiling-for-batch-27-consumers)):
the implemented consumer (`QuizService`, `QuestionSelectionService`, `QuizRepositoryImpl` and
`QuizJpaRepository`), `RUNTIME_PRIVILEGE_MATRIX` and the CI tests change together, and every
privilege below is exercised by that consumer's SQL. It depends on r5 (CT40 scope through AD07) and
r6 (`questions` SELECT for membership and owner reads). No migration is involved. No approval beyond
that ceiling is claimed; this is not personal Leader review.

The r1–r6 records above are unchanged history. The effective matrix is r1 plus r2–r6, with the
three rows below replacing the named relations (r7's `quiz_questions` row supersedes r6's narrower
one); no other privilege, role elevation or change is introduced.

| Relation (owner) | Runtime privilege | Consumer and operation | Status / basis |
| --- | --- | --- | --- |
| `quizzes` (assessment) | SELECT, INSERT, DELETE; UPDATE (title, kind, time_limit_seconds, publication_status) | owner and Learner lists and reads, create, targeted replacement, publication, delete of a hidden never-attempted quiz, the lifecycle row lock | r7 within ceiling (CT23-COMPOSITION-2026-11-09-r1, CT23-PUBLICATION-2026-11-09-r1) |
| `quiz_questions` (assessment) | SELECT, INSERT, DELETE | captured membership reads and counts, atomic membership replacement, delete with the quiz | r7 within ceiling (CT23-COMPOSITION-2026-11-09-r1) |
| `attempts` (assessment) | SELECT (id, quiz_id) | the edit/delete-time attempt-existence probe | r7 within ceiling (ADR007 §5) |

Least privilege exercised by the consumer:

| Operation | SQL | Privilege |
| --- | --- | --- |
| Every write locks the Course root first (CO14 guard) | `SELECT id FROM courses WHERE id = ? FOR UPDATE` | existing r1 SELECT and UPDATE (id); no new grant |
| CT40 scope and member questions | `SELECT … FROM skills …` (r5); `SELECT … FROM questions WHERE id IN (…) AND skill_id IN (…)` (r6) | existing r5 and r6 SELECT |
| Lists, counts and reads | `SELECT … FROM quizzes WHERE course_id = ? [AND publication_status = 'published'] ORDER BY title COLLATE "C", id`; membership join `quiz_questions` × `questions` | SELECT |
| Lifecycle row lock | `SELECT * FROM quizzes WHERE id = ? AND course_id = ? FOR UPDATE` | SELECT plus any column UPDATE |
| Create | `SELECT count(*) FROM quizzes WHERE id = ?`, then `INSERT INTO quizzes …` | SELECT, INSERT |
| Replace and publish | `UPDATE quizzes SET title = ?, kind = ?, time_limit_seconds = ?, publication_status = ? WHERE id = ? AND course_id = ?` | UPDATE on those four columns |
| Membership replacement | `DELETE FROM quiz_questions WHERE quiz_id = ?`, then `INSERT INTO quiz_questions …` at positions 1..N | DELETE, INSERT |
| Attempt-existence probe | `SELECT count(a.id) FROM attempts a WHERE a.quiz_id = ?` | SELECT (id, quiz_id) |
| Delete | membership delete, then `DELETE FROM quizzes WHERE id = ?`; the V004 `NO ACTION` foreign keys from `attempts` and `quiz_questions` are checked with the table owner's rights | DELETE |

The runtime role still cannot do any of the following:

- move a quiz to another Course, or rewrite a membership row in place (no UPDATE on
  `quizzes.course_id` or on `quiz_questions`; the ceiling's `position` update is not used);
- read an attempt's learner, number, timing or score, or create, update or delete attempts
  (AS08/CT04 add those within the ceiling when they are implemented);
- truncate `quizzes` or `quiz_questions`.

A compromised runtime role could create, replace, publish or delete quizzes and their membership in
any Course, which the approved owner operations inherently require; same-Course membership, the
publication and attempt freezes and Learner visibility stay application invariants, and referential
integrity still refuses deleting a quiz that an attempt references.

The following stay synchronized with this record:

- `RUNTIME_PRIVILEGE_MATRIX` in `infrastructure/vm/deploy.sh` (27 relations, V001–V009), with
  `verify_role_separation` probing the quiz re-parenting, in-place membership and attempt-detail
  denials;
- `test-deploy-privilege-matrix-contract.sh`, which pins r1 history and the r2–r7 rows;
- `test-postgres-privilege-matrix-containment.sh`: the AS06 SQL, the attempted-quiz delete refusal
  (23503) and the denials above on the real schema;
- `test-runtime-real-backend-privileges.sh`: the seven AS06 operations over HTTP under the runtime
  role, including the Learner's keyless list and the published and attempted 409s.

Remaining gates: owning-module (Assessment @minhtan29042006), `@tihh647-ui` infrastructure and
independent Leader CODEOWNER review; protected integration of PR #260 (r5 and r6 are on `main` since
`dfd816c` and `03b2a45`); these grants on any shared or production database (Leader-reserved). Instructor authoring of the
Course itself on a deployed VM also needs CO06's own CT17-P6 revision (`courses`, `chapters`,
`lessons` writes), which is not part of this record. This revision executes no provisioning on any
real database.

### CT17-P6-PRIVILEGES-2026-11-23-r8 — AD06 mastery and history writes (within the ADR012 §19.2 ceiling, 23 November 2026)

Prepared for AD06 ([#42](https://github.com/chi-thanh-nguyen/codemastery/issues/42),
[PR #263](https://github.com/chi-thanh-nguyen/codemastery/pull/263)) under the Project Leader's
explicit instruction of 23 November 2026 to complete AD06 together with the runtime privileges its
implemented SQL needs, inside the approved ceiling. It follows the extension procedure above and
stays inside the DELEGATED_APPROVED `CT17-P6-PLAN-2026-11-15-r2` ceiling
([ADR012 §19.2](decisions/012-batch-07-contract-approvals.md#192-ct17-p6-plan-2026-11-15-r2--least-privilege-ceiling-for-batch-27-consumers)):
the implemented consumer (`LearnerSkillMasteryRepositoryImpl`, `LearnerSkillMasteryJpaRepository`,
`PathRecommendationRepositoryImpl` and `PathRecommendationJpaRepository`, mechanism
[`AD06-PERSISTENCE-2026-11-23-r1`](decisions/016-ad06-mastery-history-persistence.md)),
`RUNTIME_PRIVILEGE_MATRIX` and the CI tests change together, and every privilege below is exercised
by that consumer's SQL. No migration is involved. No approval beyond that ceiling is claimed; this is
not personal Leader review.

The r1–r7 records above are unchanged history. The effective matrix is r1 plus r2–r7, with the two
rows below replacing the named relations; no other privilege, role elevation or change is
introduced. Both rows are narrower than their ceiling rows: the table-level `SELECT` of the ceiling
is not granted, because AD06 issues no read. AD16's reads add it in their own revision.

| Relation (owner) | Runtime privilege | Consumer and operation | Status / basis |
| --- | --- | --- | --- |
| `learner_skill_masteries` (adaptive) | INSERT; SELECT (user_id, skill_id, state); UPDATE (state) | `storeState`: insert or state update of one (learner, skill) row | r8 within ceiling (CT08.execution, ADR011 §4 step 5; ADR005 §6.1) |
| `path_recommendations` (adaptive) | INSERT | `append`: one immutable history row | r8 within ceiling (CT07, ADR011 §9: history is append-only) |

Least privilege exercised by the consumer:

| Operation | SQL | Privilege |
| --- | --- | --- |
| Store a mastery state | `INSERT INTO public.learner_skill_masteries (id, user_id, skill_id, state) VALUES (?, ?, ?, ?) ON CONFLICT (user_id, skill_id) DO UPDATE SET state = EXCLUDED.state` | INSERT; UPDATE (state); PostgreSQL also requires SELECT on the conflict-target columns and on the `EXCLUDED` column the update reads, so SELECT (user_id, skill_id, state). Measured on PostgreSQL 17.11: without SELECT (state), or with only SELECT (user_id, skill_id), the statement fails with 42501 |
| Append history | `INSERT INTO public.path_recommendations (id, enrollment_id, lesson_id, recommendation_type, reason, recommended_at) VALUES (?, ?, ?, ?, ?, ?)` | INSERT; a reused identifier fails with 23505 |
| Foreign keys to `users`, `skills`, `enrollments` and `lessons` | checked by PostgreSQL with the table owner's rights | none |

The runtime role still cannot do any of the following:

- re-key a mastery row or move it to another learner or skill (no UPDATE on `id`, `user_id` or
  `skill_id`), or read mastery row identifiers;
- delete or truncate mastery rows;
- read, update, delete or truncate recommendation history.

A compromised runtime role could set any learner's state for any skill and append history rows for
any Enrollment, which the approved writes inherently require; Course scoping, calculation and the
strictly-later recommendation instant stay application invariants (ADR011 §§4 and 9), and V005's
foreign keys, check constraints and keys stay authoritative.

The following stay synchronized with this record:

- `RUNTIME_PRIVILEGE_MATRIX` in `infrastructure/vm/deploy.sh` (27 relations, V001–V009), with
  `verify_role_separation` probing the mastery re-key and delete and the history rewrite and erase
  denials;
- `test-deploy-privilege-matrix-contract.sh`, which pins r1 history and the r2–r8 rows;
- `test-postgres-privilege-matrix-containment.sh`: the exact AD06 SQL under the provisioned runtime
  role on the real schema, the reused-identifier refusal (23505) and the denials above;
- `AdaptivePersistenceIntegrationTest` runs the adapters themselves under a transaction-scoped role
  holding exactly these rows.

`test-runtime-real-backend-privileges.sh` gains no AD06 case: no HTTP operation reaches these
adapters yet. Their first HTTP path is the quiz submission orchestrated by AD13 (ADR011 §4), which
must add its real-backend case when it wires them.

Remaining gates: owning-module (Adaptive @thanhtran0311), `@tihh647-ui` infrastructure and
independent Leader CODEOWNER review; protected integration of PR #263; these grants on any shared or
production database (Leader-reserved). This revision executes no provisioning on any real database.

### CT17-P6-PRIVILEGES-2026-11-23-r9 — multi-version predecessor deferral (DELEGATED_APPROVED, 23 November 2026)

Recorded under the Leader's Phase 5 delegation ([ADR017 §6.1](decisions/017-ssh-continuous-deployment.md#61-ct17-p6-privileges-2026-11-23-r9--multi-version-predecessor-deferral)).
r4 let pre-migration provisioning defer a migration-added column only on the exact
predecessor (history 1..version−1). The deployed preview holds V001–V006, so every
release that reaches V009 would be refused by provisioning after `deploy.sh all` had
already quiesced the application. r9 defers the column when the column is absent and the
Flyway history holds exactly the successful versioned migrations 1..k, each once, for any
1 ≤ k < version. An empty history, a gap, a failed or repeatable entry, a recorded version
whose column is missing, or k ≥ version still defer nothing, so provisioning fails closed
as before. The matrix rows, the column pins and the complete post-migration verification
are unchanged; nothing is granted earlier than r4 granted it.

Evidence: `test-postgres-privilege-matrix-containment.sh` keeps every r4 case (exact V008
predecessor, all drift refusals) and adds a fresh V001–V006 database → pre-migration
provisioning → V007–V009 → hardening → exact matrix, with refusals for an empty history,
a gap and a failed migration. Read-only evaluation on the VM on 23 November 2026: with
v0.1.0-rc.1's `deploy.sh` (r4) the update of the deployed V006 database is refused by the
Phase 5 plan gate before any change; with r9 it is an upgrade with V007–V009 pending.

### CT17-P6-PRIVILEGES-2026-11-24-r10 — IN10 audit recording and read (within the ADR012 §19.2 ceiling, 24 November 2026)

Prepared for IN10 ([#184](https://github.com/chi-thanh-nguyen/codemastery/issues/184),
[PR #276](https://github.com/chi-thanh-nguyen/codemastery/pull/276)) under the Project Leader's
explicit instruction of 24 November 2026 to complete IN10 on PR #276, including the runtime privileges
its implemented SQL needs, inside the approved ceiling. It follows the extension procedure above and
stays inside the DELEGATED_APPROVED `CT17-P6-PLAN-2026-11-15-r2` ceiling
([ADR012 §19.2](decisions/012-batch-07-contract-approvals.md#192-ct17-p6-plan-2026-11-15-r2--least-privilege-ceiling-for-batch-27-consumers)):
the implemented consumer (`AuditLogRepositoryImpl` and `AuditLogJpaRepository`, used by
`AuditLogService` for CT31.actions/CT31.consistency recording and the CT31.read operation
`GET /api/admin/audit-logs`), `RUNTIME_PRIVILEGE_MATRIX` and the CI tests change together, and every
privilege below is exercised by that consumer's SQL. r9 changed no row; this is the next dated
revision. No migration is involved. No approval beyond that ceiling is claimed; this is not personal
Leader review.

The r1–r9 records above are unchanged history. The effective matrix is r1 plus r2–r8, with the row
below replacing the r1 `audit_logs` row (none); no other privilege, role elevation or change is
introduced. The row equals its ceiling row.

| Relation (owner) | Runtime privilege | Consumer and operation | Status / basis |
| --- | --- | --- | --- |
| `audit_logs` (admin) | SELECT, INSERT | `append`: one row per audited change, inside the caller's transaction; `search`: the filtered, ordered page and its count | r10 within ceiling (CT31, ADR012 §7) |

Least privilege exercised by the consumer:

| Operation | SQL | Privilege |
| --- | --- | --- |
| Record one audited change | `insert into audit_logs (action, actor_id, occurred_at, target_reference, target_type, id) values (?, ?, ?, ?, ?, ?)` (Hibernate `persist` with an assigned identifier, executed by `flush` before `record` returns) | INSERT; a reused identifier fails with 23505 |
| Read a page | `select … from audit_logs where <optional AND filters> order by occurred_at desc, id desc offset ? rows fetch first ? rows only`, and `select count(…) from audit_logs where …` when the page does not reveal the total | SELECT |
| Actor display names | CT01.user-summaries reads `users` by identifier | existing `users` SELECT (r1) |
| Foreign key to `users` (`actor_id`) | checked by PostgreSQL with the table owner's rights | none |

The runtime role still cannot do any of the following (CT31: rows are append-only):

- update any audit column (no UPDATE privilege on `audit_logs`);
- delete or truncate audit rows.

A compromised runtime role could read the whole audit log and append rows naming any existing user
as actor, which the approved recording inherently requires; that only Administrators record and read,
and that `actor_id` is the authenticated Administrator, stay application invariants (ADR012 §§7.1,
7.3), and V006's primary key, foreign key and text check stay authoritative.

The following stay synchronized with this record:

- `RUNTIME_PRIVILEGE_MATRIX` in `infrastructure/vm/deploy.sh` (27 relations, V001–V009), with
  `verify_role_separation` probing the audit rewrite and erase denials;
- `test-deploy-privilege-matrix-contract.sh`, which pins r1 history and the r2–r8 and r10 rows;
- `test-postgres-privilege-matrix-containment.sh`: the recording and read SQL under the provisioned
  runtime role on the real schema, the reused-identifier (23505) and unknown-actor (23503) refusals and
  the rewrite, erase and truncate denials (42501);
- `test-runtime-real-backend-privileges.sh`: the real backend's audit read under the runtime role
  (Administrator page with actor names and both time bounds, Learner 403, malformed query 400, no row
  written by the read);
- `AuditLogRepositoryIntegrationTest` runs the adapter's insert and read under a transaction-scoped role
  holding exactly this row and shows that the role cannot update or delete audit rows.

No HTTP operation records an audit row yet: the audited Administrator mutations are IN11, IN20 and
IN21, which add their own real-backend cases (and their own `users`, `courses`, `lessons`,
`discussion_posts` and `content_reports` revisions) when they are implemented.

Remaining gates: owning-module (Admin @tihh647-ui), `@tihh647-ui` infrastructure and independent
Leader CODEOWNER review; protected integration of PR #276; these grants on any shared or production
database (Leader-reserved). This revision executes no provisioning on any real database.

### CT17-P6-PRIVILEGES-2026-11-24-r11 — CO15 atomic progress (within the ADR012 §19.2 ceiling, 24 November 2026)

The Project Leader explicitly authorized this minimal extension in **“CodeMastery — PR #285:
Complete CO15, Add AD11 Course Read, Remediate Findings”**, 24 November 2026, with Course owner
@TamiaoNguyen's consent. Scope: CO15 [#178](https://github.com/chi-thanh-nguyen/codemastery/issues/178)
on [PR #285](https://github.com/chi-thanh-nguyen/codemastery/pull/285), the bounded CT33 Course read
for AD11 [#168](https://github.com/chi-thanh-nguyen/codemastery/issues/168), supporting tests and
this runtime revision. This is explicit implementation and matrix-extension authorization within
the effective `CT17-P6-PLAN-2026-11-15-r2` ceiling
([ADR012 §19.2](decisions/012-batch-07-contract-approvals.md#192-ct17-p6-plan-2026-11-15-r2--least-privilege-ceiling-for-batch-27-consumers)),
not an independent CODEOWNER review, deployment authorization or runtime acceptance.

r10 was the latest recorded revision when this work began. Records r1–r10 remain authentic history;
the row below replaces only r4's `lesson_progress` row. All other effective rows, migration-column
deferrals and containment restrictions remain unchanged. No Flyway migration changes.

| Relation (owner) | Runtime privilege | Consumer and operation | Status / basis |
| --- | --- | --- | --- |
| `lesson_progress` (course) | SELECT, INSERT; UPDATE (completed, mastery_skipped) | `EnrollmentRepositoryImpl.markMasterySkipped`: insert absent progress or upgrade an existing incomplete row; Course participant locks/reads and CT33 learner snapshot | r11, explicitly Leader-authorized within ceiling, CT22-ATOMIC-2026-11-12-r1 (ADR011 §3) |

The sole added privilege is **UPDATE (mastery_skipped)**. Existing SELECT/INSERT and UPDATE
(completed) remain unchanged. The actual consumer uses:

```sql
INSERT INTO public.lesson_progress (id,enrollment_id,lesson_id,completed,mastery_skipped)
VALUES (?,?,?,true,true) ON CONFLICT (enrollment_id,lesson_id) DO NOTHING;
-- Only when the INSERT affected zero rows:
UPDATE public.lesson_progress SET completed=true, mastery_skipped=true
WHERE enrollment_id=? AND lesson_id=? AND completed=false;
```

Before those statements, the Course root is already held; the participant locks Enrollment then
existing progress in Lesson UUID order. It iterates the published set in Chapter/Lesson canonical
order. An absent row becomes true/true, an incomplete row becomes true/true, a completed row stays
unchanged, including a manual true/false row. It never clears either flag, and repeats add no row.
Enrollment reconciliation uses the existing UPDATE (status). No mastery table is read by Course.
`readLearnerState(UUID courseId)` joins the caller's read-only REPEATABLE READ snapshot, applies
CT03 before Course business reads, then reads only self Enrollment, published placements/progress
and stored adaptive fields; it requires no root marker and no additional privileges.

**Least-privilege limits.** No table-wide UPDATE, DELETE, TRUNCATE, DDL, grant option or UPDATE of
progress identifiers/ancestry is granted. `verify_role_separation` now probes identifier/ancestry
rewrite and erase denials; the exact ACL comparison still checks every column. Column grants can
permit a compromised role to alter both flags of arbitrary progress rows: same-Course binding,
manual-label preservation and monotonicity are application invariants, with V002's
`mastery_skipped implies completed` check as a database invariant. Narrowing INSERT or UPDATE
further would prevent the approved SQL; triggers, schema changes and new database role policies
are excluded. These grants apply to existing V002 columns even on an admitted V006/V008 predecessor;
only V009's `enrollments.last_visited_at` remains deferred under r9.

**Synchronized evidence surfaces.** `RUNTIME_PRIVILEGE_MATRIX`, the matrix-contract test (r11's
single row and unchanged r1–r10), the PostgreSQL containment test (absent/incomplete/manual rows,
repeat, constraint failure and identifier/erase/TRUNCATE denials), and
`EnrollmentIntegrationTest` (actual Spring participant SQL with exactly the Course subset of
r11, pre-r11 42501, rollback and read-only snapshot tests) ship together. Execution results and
commands are recorded in [testing.md](testing.md#co15-atomic-lessonprogress-participant-verification--24-november-2026).
The containment test provisions only disposable local/CI PostgreSQL containers. No HTTP path
currently invokes CO15 or this new internal Course read, so the existing real-backend HTTP suite
establishes regression compatibility only, not full Assessment → Adaptive → Course integration.

**Retained gates.** Independent Course/infrastructure/Leader CODEOWNER review, required CI and
protected integration; AD13/AS12/QA04 real event/orchestration acceptance. Provisioning this
revision on any shared database or VM remains a separately authorized Leader operation. This
change does not provision, deploy, alter credentials or mutate a real VM/database.

### CT17-P6-PRIVILEGES-2026-11-25-r12 — CO06 authoring and AS09 owner assignments (within the ADR012 §19.2 ceiling, 25 November 2026)

The Project Leader explicitly authorized this extension in **"CodeMastery — PR #273: Independent
Finding Verification & Autonomous Remediation"**, 25 November 2026 (finding U1). The instruction
authorizes a dated revision inside the effective `CT17-P6-PLAN-2026-11-15-r2` ceiling
([ADR012 §19.2](decisions/012-batch-07-contract-approvals.md#192-ct17-p6-plan-2026-11-15-r2--least-privilege-ceiling-for-batch-27-consumers)),
granting only privileges that the implemented SQL exercises and that real PostgreSQL proves. It is not
an independent CODEOWNER, owning-module or infrastructure review, deployment authorization or runtime
acceptance.

Scope:

- **AS09 owner slice** ([#160](https://github.com/chi-thanh-nguyen/codemastery/issues/160)), whose
  consumer ships in [PR #273](https://github.com/chi-thanh-nguyen/codemastery/pull/273):
  `AssignmentService`, `AssignmentRepositoryImpl`, `AssignmentJpaRepository` and
  `SubmissionJpaRepository`.
- **CO06 Instructor authoring** ([PR #265](https://github.com/chi-thanh-nguyen/codemastery/pull/265),
  on `main` since `75cdb8b`): `CourseManagementService`, `CourseStructureService`,
  `CourseRepositoryImpl` and `CourseContentRepositoryImpl`. CO06 merged without its own revision. The
  r7 record already noted that deployed Course authoring needs one. PR #273's FE15/FE14 screens are the
  first UI over these operations, so the revision ships with them.

r11 was the latest recorded revision when this work began. Records r1–r11 remain authentic history.
The rows below replace r1's `courses`, `chapters`, `lessons`, `learning_materials` (column SELECT
for access snapshots), `assignments` (none) and `submissions` (none) rows. All other effective rows, the migration-column deferral (r4/r9) and the containment
restrictions are unchanged. No Flyway migration changes.

| Relation (owner) | Runtime privilege | Consumer and operation | Status / basis |
| --- | --- | --- | --- |
| `courses` (course) | SELECT, INSERT; UPDATE (id, title, description, prerequisite_description, publication_status) | CO06 create, metadata replacement and publication; existing reads; root locks | r12 within ceiling (CT21.authoring); UPDATE (id) is r1's unchanged lock enabler |
| `chapters` (course) | SELECT, INSERT; UPDATE (title, position) | CO06 append, rename and sibling reorder | r12 equals ceiling row (CT21.authoring, reorder) |
| `lessons` (course) | SELECT, INSERT; UPDATE (title, content_markdown, position, publication_status) | CO06 append, content replacement, reorder and publication | r12 equals ceiling row (CT21.authoring) |
| `learning_materials` (course) | SELECT | CO06 owner Course and Lesson reads (every material of the tree, hidden ones included); the same column list serves the CO03.content Learner Lesson read | r12 within ceiling (CT21.authoring; SELECT is the ceiling's read) |
| `assignments` (assessment) | SELECT, INSERT; UPDATE (lesson_id, title, instructions, deadline_at, publication_status) | AS09 create, owner list and read, definition replacement, publication, the `FOR UPDATE` row lock | r12 equals ceiling row (CT10.rest, ADR010 §10) |
| `submissions` (assessment) | SELECT (id, assignment_id, graded_at) | AS09 owner submission and graded counts | r12 within ceiling (CT10.rest owner counts) |

Least privilege exercised by the consumers:

| Operation | SQL (as issued) | Privilege |
| --- | --- | --- |
| Course root lock (every CO06 write; settings use `NOWAIT`) | `SELECT id FROM public.courses WHERE id = ? FOR UPDATE [NOWAIT]` | SELECT plus any column UPDATE (r1) |
| Create a Course | `INSERT INTO public.courses (id, instructor_id, title, description, prerequisite_description, publication_status, adaptive_enabled, adaptive_settings) VALUES (…)` | INSERT |
| Replace Course metadata | `UPDATE public.courses SET title = ?, description = ?, prerequisite_description = ? WHERE id = ?` | UPDATE on those three columns |
| Course or Lesson publication (no cascade) | `UPDATE public.courses SET publication_status = ? WHERE id = ?`; `UPDATE public.lessons SET publication_status = ? WHERE id = ? AND chapter_id = ?`; a real change then reconciles joined completion with the existing r4 Enrollment UPDATE (status) | UPDATE (publication_status) on each |
| Append a Chapter or Lesson | `INSERT INTO public.chapters (id, course_id, title, position) SELECT …, next_position FROM (SELECT COALESCE(max(position), 0)::bigint + 1 …) … RETURNING position`; the same shape for `lessons` | SELECT, INSERT |
| Rename a Chapter; replace a Lesson | `UPDATE public.chapters SET title = ? WHERE id = ? AND course_id = ?`; `UPDATE public.lessons SET title = ?, content_markdown = ? WHERE id = ? AND chapter_id = ?` | UPDATE on those columns |
| Reorder siblings (ADR006 §5) | `SET CONSTRAINTS public.uq_chapters_course_position DEFERRED`; `UPDATE public.chapters SET position = ? WHERE id = ? AND course_id = ?` for 1..N; `SET CONSTRAINTS … IMMEDIATE` (the same for Lessons) | UPDATE (position); `SET CONSTRAINTS` needs no grant |
| Create an Assignment | Hibernate `persist` with an assigned identifier: `insert into assignments (course_id, deadline_at, instructions, lesson_id, publication_status, title, id) values (…)` | INSERT |
| Assignment row lock (replace, publication) | `SELECT * FROM public.assignments WHERE id = ? AND course_id = ? FOR UPDATE`, then an entity refresh | SELECT plus any column UPDATE |
| Replace or publish an Assignment | `update assignments set lesson_id = ?, title = ?, instructions = ?, deadline_at = ? where id = ? and course_id = ?`; `update assignments set publication_status = ? where id = ? and course_id = ?` | UPDATE on those columns |
| Owner list and read | `select count(a1_0.id) …`; `SELECT * FROM public.assignments WHERE course_id = ? ORDER BY title COLLATE "C", id LIMIT ? OFFSET ?` | SELECT |
| Submission counts | `select s1_0.assignment_id, count(s1_0.id), count(s1_0.graded_at) from submissions s1_0 where s1_0.assignment_id in (…) group by s1_0.assignment_id` | SELECT (id, assignment_id, graded_at); removing `id` fails with 42501 |
| Owner Course read (every CO06 response) and owner or Learner Lesson read | `SELECT m.id, m.lesson_id, m.title, m.material_type, m.publication_status, m.text_content, m.object_key, m.original_filename, m.external_video_url FROM public.learning_materials m JOIN public.lessons l … JOIN public.chapters ch … WHERE ch.course_id = ? ORDER BY …` (`CourseContentRepositoryImpl.MATERIAL_COLUMNS`) | SELECT on all nine columns; with only r1's three columns `GET /api/instructor/courses/{id}` and every authoring response failed with 42501 (500) under the runtime role |
| Foreign keys (`chapters.course_id`, `lessons.chapter_id`, `assignments.course_id`/`lesson_id`) | checked by PostgreSQL with the table owner's rights | none (no REFERENCES) |

Deliberately not granted, because no HTTP operation reaches the SQL: `courses` UPDATE (adaptive_enabled,
adaptive_settings) (`CourseManagementService.writeAdaptiveConfiguration` has no caller), every
`learning_materials` write (CT09), and every `submissions` write or wider read (Learner submission
and grading, AS10/AS11). Those consumers add their own revisions within the ceiling when they are
implemented.

The runtime role still cannot do any of the following:

- re-own a Course (no UPDATE on `courses.instructor_id`), rewrite its adaptive configuration, or delete
  or truncate Courses, Chapters or Lessons;
- move a Chapter to another Course or a Lesson to another Chapter (no UPDATE on `chapters.course_id`
  or `lessons.chapter_id`), or re-key either;
- move an Assignment to another Course (no UPDATE on `assignments.course_id`), re-key, delete or
  truncate it;
- create, update, delete or truncate learning materials;
- read a submission's learner, object key, file name, grade or feedback, or create, update, delete or
  truncate submissions.

A compromised runtime role could create Courses naming any existing user as Instructor, and could
rewrite the metadata, structure and publication of any Course and any Assignment. It can now also read
material payload columns, including `object_key` and `original_filename`, which r1 kept unreadable.
The CO06 SQL selects them, although its responses never expose them. An object key does not grant
storage access without the MinIO credentials. Narrowing the Course module's material reads so that this
column-level containment returns is a Course-module change, outside this revision. The approved owner
operations inherently require that. Ownership (`instructor_id` equals the authenticated Instructor),
Course scoping, same-Course Lesson binding and the publication rules stay application invariants.
V002 and V004 keys, checks and the deferrable sibling-position uniqueness stay authoritative.

**Synchronized evidence surfaces.** These change together with this record:

- `RUNTIME_PRIVILEGE_MATRIX` in `infrastructure/vm/deploy.sh` (27 relations, V001–V009).
  `verify_role_separation` now probes Course re-ownership, adaptive-setting, structure delete and move,
  material write and delete, Assignment move and delete, and submission file and grade denials. It no
  longer expects Course publication or material payload reads to be denied.
- `test-deploy-privilege-matrix-contract.sh` pins r12's six rows, checks them against the ceiling's
  "Never granted" column, and keeps r1–r11 unchanged.
- `test-postgres-privilege-matrix-containment.sh` runs the CO06 and AS09 SQL above under the
  provisioned runtime role on the real schema: deferred reorder, the 23505 sibling refusal, the 23503
  Lesson foreign key, and owner counts over admin-inserted submissions. It also checks the denials above
  (42501).
- `test-runtime-real-backend-privileges.sh` runs the CO06 authoring and AS09 owner operations over HTTP
  under the runtime role, plus the owner and Learner Lesson reads. Before the `learning_materials` row
  was added, this test found the 500 on the first metadata replacement.
- `AssessmentPersistenceIntegrationTest` runs the AS09 adapter under a transaction-scoped role holding
  exactly the r12 `assignments` and `submissions` rows. That includes the `FOR UPDATE` lock that blocks
  a concurrent submission insert (55P03).

Execution results are recorded in
[testing.md](testing.md#pr-273-u1u6-remediation-verification--25-november-2026). The containment and
real-backend tests provision only disposable local or CI PostgreSQL containers.

**Retained gates.** These remain open:

- Owning-module review: Course (@TamiaoNguyen) for the CO06 rows, Assessment (@minhtan29042006) for
  the AS09 rows.
- `@tihh647-ui` infrastructure review and independent Leader CODEOWNER review.
- Required CI and protected integration of PR #273.

Provisioning this revision on any shared database or VM remains a separately authorized Leader
operation. This change does not provision, deploy, alter credentials or mutate a real VM or database.

## CT17-INGRESS-COOKIE-2026-11-16-r1 — refresh-cookie transport (DELEGATED_APPROVED, 16 November 2026)

**DELEGATED_TECHNICAL_APPROVAL; DELEGATED_APPROVED** under the same instruction,
[ADR014 §§5 and 8](decisions/014-hybrid-refresh-session-authentication.md#5-cookie-transport-csrf-origin-and-cors).
Bounded amendment of P7's request-header allowlist in the release image
(`frontend/Dockerfile`, `release-config` stage):

- `location = /api/auth/login`, `= /api/auth/refresh` and `= /api/auth/logout` repeat
  the complete allowlist (`proxy_set_header` is not inherited by a location that sets
  its own) and additionally forward `Cookie`, `Sec-Fetch-Site` and `Sec-Fetch-Mode`;
- they keep the raw-path `;`/`%3B` rejection;
- every other `/api` location, including `/api/auth/refresh/`, `/api/auth/refreshx` and
  `/api/auth/login/extra`, still forwards no `Cookie` or Fetch Metadata;
- `Set-Cookie` from the backend passes through unchanged; nothing is added or
  rewritten at the ingress.

Unchanged: single HTTPS origin, TLS/SNI/Host handling, `proxy_pass_request_headers
off`, the forwarding-claim policy, API-before-SPA precedence and the local HTTP image.
The backend's cookie policy is `__Host-cm_refresh` (Secure, HttpOnly, SameSite=Strict,
Path=/) for this HTTPS origin, and `CORS_ALLOWED_ORIGINS` (which `deploy.sh` binds to
`--origin`) is also the trusted Origin list of the cookie operations; no new runtime
variable exists.

Verification: `test-ingress-stub-backend.sh` (cookie and Fetch Metadata scoping,
`Set-Cookie` pass-through, path-parameter rejection on the new locations) and
`test-ingress-real-backend.sh` (issue, rotation, CSRF rejections and logout revocation
through the ingress with the real backend). Remaining gates: infrastructure and
CODEOWNER review, protected integration and a separately authorized deployment.
## DP01 HTTPS ingress integration — 31 October 2026

### Authority, inputs and boundary

The Project Leader @chi-thanh-nguyen's explicit **“CodeMastery — DP01 Interim Azure
HTTPS Integration”**, 31 October 2026 (ICT), authorizes the repository-side HTTPS
integration, its tests and a pull request. This change implements already-approved
CT17 policy (P1, P5–P8, B1–B3 and the 28 October operation gates). It approves no
new contract and changes no VM, firewall rule, certificate, credential or Project
record. It does not complete DP01 #71 or DP03 #72. It was first prepared against
`main` `08f66fc99ed5bbe72c2a9aa76ed2edd8e528ec5d`. On 31 October it was synchronized
onto `main` `1e5553e1adb76a3551f7e82ecc6887ce19c79b49` for final review.

The Leader reports these observed infrastructure inputs. They are not authority for
public exposure, credential use or operational changes:

- Ubuntu 24.04 LTS, linux/amd64, 2 vCPU and 8 GiB RAM; Docker Engine 29.9.2 and
  Docker Compose 5.6.1. Docker administration currently uses `sudo docker`.
- Intended origin `https://codemastery.eastasia.cloudapp.azure.com`, with a public
  A record. Inbound TCP 443 is permitted, and SSH is restricted to the operator.
- A publicly trusted Let's Encrypt ECDSA P-256 certificate, issued through
  TLS-ALPN-01 with acme.sh. `/etc/codemastery` is root-owned 0700. `tls/` is
  owned by UID 101 with mode 0700. `privkey.pem` is UID 101, 0400, and
  `fullchain.pem` is root-owned 0644. Renewal is not configured.

### Leader-approved path bindings — 31 October 2026

The Project Leader/Owner @chi-thanh-nguyen explicitly approved these exact DP01
Interim bindings in **“CodeMastery — PR #118 Independent Review Remediation”**,
31 October 2026 (ICT):

| Binding | Approved value |
| --- | --- |
| Repository Compose override | `infrastructure/vm/docker-compose.vm.yml` |
| VM TLS serving directory (B3 source) | `/etc/codemastery/tls` |
| Frontend container TLS mount target (B3 target) | `/etc/nginx/tls` |
| Mount | Read-only directory bind. Nginx keeps running as the non-root UID 101, and the private key keeps UID 101 ownership and mode 0400 (B3). |

This is the location approval that P5 (existing configuration responsibilities
only; certificate/key mounts remain Leader inputs) and B3 (a directory bind "after
its actual source/target location is approved") left open, for these exact paths
and this mapping only. It approves no other host path, runtime secret or env-file
location, operator identity, provisioning, deployment, public exposure, renewal
automation or architecture change. It is not a GitHub review: CODEOWNERS
(`/infrastructure/` covers the override), last-push approval, required checks and
protected merge still apply. [The repository structure](repository-structure.md#bounded-dp01-vm-compose-override-addition)
records the tree addition.

### Implementation

| Changed file | Responsibility |
| --- | --- |
| `infrastructure/vm/docker-compose.vm.yml` (new; Leader-approved path above) | Release override applied by `deploy.sh --compose-override` on top of the unchanged local base |
| `frontend/Dockerfile` | New `release` target; the default local target is unchanged |
| `.env.example`, `docs/setup.md`, `README.md` | Template, guide and AI-disclosure synchronization |
| `.github/workflows/ci.yml` | New “VM HTTPS ingress” job |

The effective deployment model keeps exactly the five services, the `codemastery`
project, its private default network and the `postgres-data`/`minio-data` named
volumes. Compose `!reset`/`!override` removes the local host ports, the local frontend
HTTP healthcheck, both `build` sections and the initializer's inline
entrypoint/command. Merging alone would not remove them.

- **Ingress.** The existing frontend Nginx is the only public ingress, published as
  `0.0.0.0:443` to container `8080`. IPv4 only is published: no AAAA record exists
  and IPv6 is untested (B2). It terminates TLS 1.2/1.3 from the read-only
  `/etc/codemastery/tls` → `/etc/nginx/tls` directory bind, using
  `create_host_path: false`, so a missing directory fails instead of being created.
- **Host validation.** A handshake without SNI or with another name is refused
  (`ssl_reject_handshake`), so IP-address scans get no certificate. A request whose
  Host differs from the approved name receives HTTP 421. Plaintext HTTP on the TLS
  port receives Nginx's 400.
- **Routing.** Exact `/api` and the `/api/` subtree match before the SPA. They are
  proxied to `backend:8080` with the original path and query unchanged, and
  backend status, content type and headers pass through. Error interception and
  caching are disabled. Client routes load `index.html`. A missing `/assets/`
  file, or any missing path ending in an extension, is a real 404. An unavailable
  backend is an HTTP 502, not the SPA.
- **Exposure.** Nothing else reaches the backend: Actuator, `/v3/api-docs` and
  Swagger paths are served by the SPA or as 404. No backend, PostgreSQL, MinIO API
  or MinIO Console host port remains.
- **Path parameters.** Both `/api` locations reject, with Nginx's own 400 before
  proxying, any request whose raw path contains `;` or `%3B` in any case. The raw
  path is the request target up to its first `?`. Tomcat drops `;` path
  parameters, so `..;` acts as `..` there. Nginx selects locations on its
  normalized URI but forwards the raw target. The check therefore reads the raw
  target (`$request_uri`), not the normalized `$uri`. Normalization can remove a
  `..;` segment, for example routing `/api/x/..;/../actuator/health` as
  `/api/x/actuator/health`. It can also route `/x;/../api` into `/api`. A check on
  `$uri` would miss both. Today the backend also rejects `;` in request paths: such
  requests reach only its error dispatch, which answers 401. The ingress rule keeps
  these paths from reaching the backend even if a private health endpoint is
  later permitted. Semicolons in the query string, literal or encoded, are
  proxied unchanged.
- **Forwarding (P7).** Nginx cannot remove a header family by name prefix, so a
  denylist would still pass any unlisted `X-Forwarded-*` name. The `/api` proxy
  therefore passes no client header except this allowlist: `Authorization`,
  `Accept`, `Content-Type`, `Origin`, `Access-Control-Request-Method` and
  `Access-Control-Request-Headers`. These are the bearer credential, the
  representation headers the SPA client sets, and the CORS inputs the backend
  checks. Nginx itself sets `Host` (the private `backend:8080`) and the body
  framing. `Forwarded`, every `X-Forwarded-*` header and other identity, routing or
  method claims (for example `X-Real-IP`, `X-Original-URL`, `X-Rewrite-URL`,
  `True-Client-IP` and `X-HTTP-Method-Override`) never reach the backend, nor do
  cookies, `Proxy-Authorization`, `Accept-Language` or `User-Agent`. None are added.
  The backend also runs with `SERVER_FORWARD_HEADERS_STRATEGY=NONE`; besides Spring's
  CORS, content negotiation and body handling it reads only `Authorization`, and
  its responses do not vary by locale. No trusted-proxy range is configured. A
  future consumer of another request header must change this allowlist and its CI
  assertions together.
- **Backend runtime.** The override sets `SPRING_PROFILES_ACTIVE=prod` and
  `JWT_EXPIRATION=PT15M`. `CORS_ALLOWED_ORIGINS` and the
  `SPRING_FLYWAY_URL/USER/PASSWORD` triple come from the env file, which `deploy.sh`
  validates as distinct bootstrap, migrator and runtime identities. Application
  security, JWT and the P6 privilege matrix are unchanged.
- **Frontend `release` target.** It builds with `VITE_API_BASE_URL=/api`. It
  renders the Nginx server and the healthcheck from the public `PUBLIC_ORIGIN`
  build argument. That argument has no default and uses `deploy.sh`'s origin
  rules, so no hostname is committed. Both files are root-owned and read-only to
  the Nginx user.
- **Healthcheck.** The baked check runs `curl` over loopback with
  `--resolve <host>:8080:127.0.0.1`. It verifies the served chain against the
  image CA store, expiry and the hostname, and never disables verification. The
  slim image's BusyBox `wget` cannot keep the approved SNI/hostname while
  connecting to loopback. The release stage therefore uses the non-slim
  `nginxinc/nginx-unprivileged` Alpine variant, pinned by digest to Nginx 1.30.6,
  adding `curl` and keeping the `wget` that `deploy.sh`'s private backend probe
  uses. It runs `apk upgrade --no-cache` for Alpine security updates (Nginx stays
  pinned by the image) and restores the non-root UID 101. Timings reuse the local
  Compose healthcheck values. (2 November: the upgrade is now an exact list of
  package versions and `/etc/nginx` is root-owned; see
  [pre-release security hardening](#dp01-pre-release-security-hardening--2-november-2026).)
- **Release images.** Images are never built or pulled on the VM. The override's
  `codemastery.invalid/*:release` names cannot resolve to any registry. They are
  bound only by `docker load` of the reviewed archive and pinned by
  `--expect-image`. PostgreSQL keeps its existing `postgres:17.11-bookworm`
  reference.
- **Storage images.** Neither MinIO image keeps the local-only golithus pin, so
  deploying the override cannot silently promote it. The initializer must provide
  its executable from the qualified image. That image must preserve the local
  initializer's behavior: a distinct application identity, a valid bucket name,
  the bucket-scoped least-privilege policy, refusal of group inheritance and of
  other policy bindings, verification of the application identity, and safe
  repeat runs. These properties are evidenced through `--initializer-qualification`.
  The initializer source, image definition and qualification suite now exist; see
  [DP01 MinIO Community initializer](#dp01-minio-community-initializer--31-october-2026).
  No release-tagged storage artifact exists.

### Release build outside the VM

**Superseded as the supported path, 23 November 2026:** the
[Phase 4 release pipeline](#phase-4-release-pipeline--23-november-2026)
([ADR015](decisions/015-release-pipeline.md)) builds, qualifies, attests and packages
the five images in GitHub Actions. The manual recipe below is kept as the dated
historical procedure.

The required tags are fixed by the override. Every image must be linux/amd64 (B1),
whatever the build host's own architecture. The recipe needs Docker Engine 28 or
later on the build host, for `docker save --platform`. It follows the archive flow
that CI job “VM deployment containerd image store” verifies.

Since the [pre-release security hardening](#dp01-pre-release-security-hardening--2-november-2026),
images are built under non-release candidate tags and receive their
`codemastery.invalid/*:release` names only by promotion, after qualification and
a recorded security admission of that exact image ID. `<rev>` is the reviewed
source revision.

```bash
# 1. Candidates (never a release name).
docker build --platform linux/amd64 -t codemastery-candidate/backend:<rev> ./backend
docker build --platform linux/amd64 --target release \
  --build-arg PUBLIC_ORIGIN=https://codemastery.eastasia.cloudapp.azure.com \
  -t codemastery-candidate/frontend:<rev> ./frontend
# DP01 hardened PostgreSQL: postgres:17.11-alpine3.24 with su-exec replacing
# gosu, shipped as one byte-clean layer. See "DP01 PostgreSQL Alpine/su-exec
# image hardening" and "DP01 final security remediation" (F4) below.
docker build --platform linux/amd64 -t codemastery-candidate/postgres:<rev> \
  ./infrastructure/postgres
docker build --platform linux/amd64 -t codemastery-candidate/minio-init:<rev> \
  ./infrastructure/minio
# The initializer's mc is a modified AGPL-3.0-or-later build: export its
# Corresponding Source from the same revision and keep it with the archive
# ("DP01 final security remediation", F1).
docker build --platform linux/amd64 --target mc-source-archive \
  --output type=local,dest=release-source ./infrastructure/minio
shasum -a 256 release-source/mc-source.tar.gz   # must equal the suite's INIT_MC_SOURCE_SHA256
# The release MinIO server (Chainguard or minimal candidate) is not selected or
# admitted yet (blocker 2); no command is given for it here.

# 2. Qualification of these exact images, for example:
infrastructure/minio/test-minio-init.sh codemastery-candidate/minio-init:<rev>
# plus the scans and checks in the dated records; record the image IDs.

# 3. Security admission: a Leader record naming each admitted image ID.

# 4. Promotion of admitted image IDs only, then the archive.
docker tag codemastery-candidate/backend:<rev> codemastery.invalid/backend:release
# ... likewise for each admitted image ...
docker save --platform linux/amd64 -o release.tar \
  codemastery.invalid/postgres:release \
  codemastery.invalid/minio:release codemastery.invalid/minio-init:release \
  codemastery.invalid/backend:release codemastery.invalid/frontend:release
shasum -a 256 release.tar   # --archive-sha256 (sha256sum on Linux)
```

Without `--platform`, `docker save` writes whichever platforms the host holds.
`deploy.sh` rejects such an archive: "Archive does not contain exactly the
verified linux/amd64 release artifacts".

Take the `--expect-image` identities from the archive, not from `docker image
inspect` on the build host. After `docker load`, the VM's containerd image store
reports each image's ID as the digest of its named top-level manifest in the
archive's `index.json`. The build host's multi-platform index ID differs and is
rejected. This prints one `REF=sha256:…` value per Compose image reference:

```bash
python3 -c 'import json,sys,tarfile
for m in json.load(tarfile.open(sys.argv[1]).extractfile("index.json"))["manifests"]:
    n = (m.get("annotations") or {}).get("io.containerd.image.name")
    if n: print(n.removeprefix("docker.io/library/") + "=" + m["digest"])' release.tar
```

`deploy.sh load` recomputes the identities from the archive and stops on any
mismatch. `deploy.sh preflight` reports the VM's image store. These identities
apply to the containerd store; a graph-driver store reports image config digests
instead.

The transfer channel, archive custody and artifact admission review remain P8
inputs. This record selects none of them. No production-qualified MinIO server or
initializer artifact exists yet (release blocker 2).

### Operator identity and env-file custody (proposal; Leader decision)

`deploy.sh` reads the certificate directory itself during preflight. With the
reported layout, only root or UID 101 can traverse `/etc/codemastery` and read the
key. A non-root operator, even with Docker access, therefore fails with "frontend
certificate directory must be owner-restricted", while root passes. The CI job
below checks both cases. The env file must be owned by the invoking
user, mode 0600 with one link, inside a 0700 directory that user owns, with only
root- or user-owned, non-writable ancestors. Because `azureuser` uses `sudo docker`,
running `deploy.sh` as `azureuser` stops at Docker reachability.

**Proposed compatible arrangement:**

- Run `sudo infrastructure/vm/deploy.sh …`.
- Keep the env file root-owned, 0600, in a root-owned 0700 directory that the
  Leader names. No path is selected here.
- Root executes the checkout's `deploy.sh`, so the checkout must be the reviewed
  revision and not writable by other accounts.
- If the checkout is owned by another user, Git's ownership check makes the
  record's "Script checkout rev" read `unavailable`. Record the reviewed revision
  through `--source-revision` and the change record. Do not change global Git
  configuration without authority.

Rejected alternatives:

- Docker-group membership: root-equivalent daemon access without sudo
  accountability, and not authorized.
- Loosening `/etc/codemastery` or key permissions: violates B3.
- Copying key material to another location for the check: invents a secret
  location.

None of this is configured or authorized here.

### Certificate renewal (proposed procedure; not implemented)

Status: **unattended renewal is not configured and is not claimed.** acme.sh was
installed with `--no-cron`. Its standalone TLS-ALPN-01 needs host TCP 443, which the
running frontend holds. Customer DNS-01 records are unavailable for an
Azure-managed `cloudapp.azure.com` name. HTTP-01 would need a separately approved
port-80 listener. Public Certificate Transparency logs list one certificate for
this name, valid from 2026-10-30T23:12:57Z to 2027-01-28T23:12:56Z (public-key
SHA-256 prefix `3755aca1c118`), which would outlive the 13 December Final deadline.
The operator should still record the served certificate's `notAfter` and
fingerprint; this change did not observe the served certificate.

Proposed separately authorized procedure, outside evaluation and demo windows:

1. Record the served certificate fingerprint and expiry.
2. Stop only `frontend`. `restart: unless-stopped` keeps it stopped.
3. Renew through TLS-ALPN-01. Install into a root-only staging directory outside
   `/etc/codemastery/tls`.
4. Verify the staged pair:
   - the key matches the leaf;
   - the chain is complete;
   - the SAN is the approved host;
   - the validity period is correct.
5. Set the staged files' owner and mode: key UID 101 0400, chain root 0644, one
   link each.
6. Keep the previous pair in a root-only backup directory outside the mounted
   directory. A backup key left inside it fails preflight.
7. Rename each file into the mounted directory on the same filesystem. The
   directory bind exposes the new inodes; a single-file bind would pin the old one.
8. Start `frontend`, run `nginx -t`, and wait for the baked healthcheck.
9. Rerun `deploy.sh preflight` and verify the newly served certificate from
   outside.
10. On any failure, restore the backup pair, restart, and preserve the evidence.

A zero-downtime `nginx -t` then `nginx -s reload` path becomes possible only with a
renewal method that does not need host 443, and requires its own approval. A failed
reload keeps serving the old certificate. No timer, hook, port-80 exposure or
certificate change is made here.

### Verification and remaining gates

CI job **VM HTTPS ingress** builds the release and local frontend images and the
backend image, then exercises:

- the ingress behaviors above against a stub backend and the real backend
  (including exact-origin CORS, ProblemDetail `instance` paths and 401 headers);
- the request-header allowlist: allowlisted headers, the body and CORS preflight
  reach the backend, while forged forwarding, routing, method-override, cookie and
  proxy-credential headers, including an unanticipated `X-Forwarded-*` name, do
  not, and cannot change the real backend's paths, methods or ProblemDetail;
- path-parameter rejection on the raw request target: literal, `%3b` and `%3B`
  semicolons, `;jsessionid=` segments and `..;` segments that normalization
  removes. Each variant receives Nginx's 400 without reaching the stub or the
  real backend. Query-string semicolons still reach both;
- the release image's CA store against the public certificate chain below;
- negative release builds;
- `deploy.sh`'s Compose and image policies on the committed override;
- a real `deploy.sh preflight` with the reported certificate layout, as root and as
  a non-root operator, including renewal-time layout errors;
- `deploy.sh`'s exact private health probe against the real backend: 200/`UP`
  without details, 401 for probe groups and other Actuator paths, and 503 after
  PostgreSQL stops;
- local-image regressions.

Results belong to the pull request. No VM preflight, load, provision, deploy or
external verification was run.

**Certificate chain.** The Leader reports that on the VM
`/etc/codemastery/tls/fullchain.pem` contains the leaf issued by Let's Encrypt YE2,
YE2 issued by Root YE, Root YE cross-signed by ISRG Root X2, and ISRG Root X2
cross-signed by ISRG Root X1. The Leader also reports that `openssl verify -purpose
sslserver -verify_hostname codemastery.eastasia.cloudapp.azure.com -CAfile
/etc/ssl/certs/ca-certificates.crt -untrusted <fullchain> <fullchain>` returned
`OK` there on 31 October. That establishes trust in the host store, which
`deploy.sh`'s private TLS check uses. The release image's `curl` does not fetch
missing issuers, so its healthcheck depends on the served Root YE cross-sign. The CI
step checks the same chain shape, rebuilt from public CT and issuer records, against
the image's own CA store with OpenSSL at a fixed time: it is trusted for the
approved hostname, and rejected without the cross-sign or for another name. The
in-image `curl` handshake with the real key is first observed by `deploy.sh`'s
staged-frontend healthcheck on the VM; that remains an image-specific deployment
check.

Release blockers remain:

1. **Private health probe (Auth-owned).** Fixed in the repository on 1 November:
   the probe gets 200/`UP` without a JWT and the real-backend CI step now enforces
   it ([remediation](#dp01-private-health-probe-remediation--1-november-2026)).
   It stays open until a release backend built from the merged revision answers
   `deploy.sh` on the VM.
2. **Storage artifacts.** The Chainguard server candidate and the minimal server
   built from it are functionally qualified, natively in CI. The initializer now
   builds on Chainguard's `minio-client` image instead of the Golithus `mc` image;
   its CRITICAL/HIGH scanner findings went from 45 to 0 under the same scanner and
   database. No production server or initializer artifact has been admitted.
   Security and maintenance limitations and the admission gates remain; see
   [DP01 MinIO Community initializer](#dp01-minio-community-initializer--31-october-2026),
   [Chainguard candidate](#dp01-chainguard-minio-release-server-candidate--1-november-2026),
   [minimal candidate](#dp01-minimal-minio-server-candidate--1-november-2026)
   and [initializer base remediation](#dp01-minio-initializer-base-remediation--1-november-2026).
   The release override disables the Console; the proposed residual-risk
   acceptance is pending ([Console hardening](#dp01-minio-console-hardening--2-november-2026)).
3. **Release logistics.** Release archive transfer, custody and image admission
   review (P8), including the non-slim Nginx variant.
4. **Operator custody.** The operator identity and env-file custody above.
5. **Renewal.** The renewal lifecycle above.
6. **Authority references.** Provision, deployment, public-exposure,
   packet-filter, initializer and P9 update references.
7. **Evidence.** B1 capacity qualification, QA01/QA02/QA07 evidence and DP03
   outside-network verification.

The earlier governance gate for the override path and the certificate mount
location is closed for the exact bindings in
[Leader-approved path bindings](#leader-approved-path-bindings--31-october-2026).
That approval does not close any gate above. Independent CODEOWNER review,
last-push approval, required checks and protected merge remain mandatory.

The implementation, this record and its tests were produced through AI-assisted
development (Anthropic Claude Code) under the Leader's task; no AI service or new
infrastructure technology is introduced into CodeMastery.

## DP01 MinIO Community initializer — 31 October 2026

### Authority and boundary

The Project Leader @chi-thanh-nguyen's 31 October 2026 (ICT) task on branch
`feat/dp01-minio-release` directs a MinIO Community initializer image under
`infrastructure/minio/`, real qualification tests and a review of the existing
server artifact. It keeps the storage technology and the digest-pinned Community
server and client. Migration to MinIO AIStor is excluded. The task approves no
new contract or credential, and no change to any VM, DNS, TLS or production data.
It creates no `codemastery.invalid/*:release` tag and does not close DP01 #71. The
[repository structure](repository-structure.md#bounded-dp01-minio-initializer-addition)
records the path addition.

### Implementation

| File | Responsibility |
| --- | --- |
| `infrastructure/minio/minio-init.sh` (new) | Initializer executable |
| `infrastructure/minio/Dockerfile` (new) | Image `FROM` the existing `mc` pin; runs as UID/GID 65532:65532, with the executable as its own entrypoint |
| `infrastructure/minio/test-minio-init.sh` (new) | Qualification suite against disposable MinIO servers |
| `.github/workflows/ci.yml` | New “VM MinIO initializer qualification” job; runs the suite on linux/amd64 |

The image reads only the six variables the VM model admits. The endpoint is
fixed at `http://minio:9000`, and the readiness deadline stays 120 seconds. The
policy name stays `codemastery-local-storage`, so state created by the local
initializer is accepted unchanged. The local base Compose file and its inline
initializer are unchanged. The VM override already resets that inline
entrypoint and command, so the image's own entrypoint runs. The override,
`deploy.sh` and the Compose contract are unchanged.

### Initializer behavior

Retained from the local initializer:

- A distinct application identity.
- The bucket-scoped `s3:GetBucketLocation`/`s3:ListBucket` and
  `s3:GetObject`/`s3:PutObject`/`s3:DeleteObject` policy.
- Refusal of group membership and of other policy bindings.
- Verification of the application credentials.
- A non-zero exit on any failure.

Strengthened:

- **Inspect before any change.** All existing state is read before anything is
  changed. A conflict stops the run and leaves the server as found.
- **Fail closed on unexpected state.** The run stops when:
  - a policy with the approved name has any other content (no overwrite);
  - the policy is bound to any other user or group;
  - the identity is disabled (it is not re-enabled);
  - the identity has service accounts;
  - the bucket allows anonymous access;
  - the identity exists but rejects `STORAGE_SECRET_KEY` (its secret is not
    reset).
- **Policy compared by content.** The stored document must equal the approved
  one exactly. The only exception is the member order of `Action`/`Resource`
  arrays: MinIO returns these sets in a different order on each read.
- **Only absent resources are created.** A repeat run on initialized state makes
  no change and reports `changes: 0`.
- **Credentials exercised.** The application must authenticate, list its bucket
  and be refused administration.
- **Stricter input validation, before any change.** Bucket names follow the S3
  rules. The access key must be 3–20 characters from `A-Z a-z 0-9 . _ @ + -`;
  MinIO rejects `=` and `,`. The application secret must be at least 8 bytes
  (MinIO's minimum) and differ from the root password. The region is restricted
  to letters, digits, `_` and `-`. Control characters are rejected. MinIO itself
  reports an invalid access key or short secret only when it creates the
  identity, after the bucket and policy exist, so these are checked first.
- **Secrets stay out of argv and output.** `mc` reads credentials from a private
  0700 temporary config file, and the new user's secret from stdin. Secrets never
  reach argv, which is visible in host process listings. `mc` output is never
  printed, because `user add` echoes the secret.
- **Exit status and JSON status both checked.** Some `mc --json` commands exit 0
  after reporting an error.

The local inline initializer remains as it was. These defects in it were
observed on the pinned server:

- `mc admin policy create` silently replaces an existing policy with the same
  name.
- `mc admin user add` resets an existing identity's secret and re-enables it.
- Root and application secrets appear in `mc` argv.

It is local development only and the release model does not run it. Aligning it
with the image needs a separate decision.

### Qualification suite and recorded results

Run `infrastructure/minio/test-minio-init.sh` to build and test the image. Pass
an image reference or ID as its argument to test an existing image instead. It
needs Docker with Compose v2.24+ and python3. The pinned server image must
already be present locally; Docker pulls the digest-pinned `mc` image on demand
if it is missing. Twenty-one checks cover:

- **Static checks:** the digest pins and image configuration; UID 65532 with
  zero inheritable, permitted, effective and ambient capability sets (the
  bounding set remains Docker's default and `no_new_privs` is unset, because the
  contract forbids `cap_drop` and `security_opt`); a read-only executable; no
  setuid/setgid files.
- **Input validation:** rejection of invalid input before MinIO is contacted,
  including an access key containing `=` or `,` and a 7-byte secret.
- **First run:** the exact resulting state, plus least privilege. The
  application can put, get, list and delete in its bucket. It cannot read, list
  or write another bucket. It cannot create or delete buckets, change anonymous
  access or administer.
- **Repeat runs:** an unchanged snapshot, including the policy `UpdateDate`.
- **Wrong credentials:** wrong root credentials, and an existing identity with
  another secret.
- **Invalid application credentials on a live server:** an access key with `=`
  and a 7-byte secret are refused and leave no bucket, policy or identity. A
  secret of seven characters encoded as eight UTF-8 bytes is accepted, matching
  MinIO's byte count.
- **Conflicts:** a same-named broad policy; an anonymous bucket; an extra policy;
  group membership; a foreign binding; a disabled identity; service accounts.
  Each conflict is left as found. An equivalent policy with reordered members is
  accepted unchanged.
- **Unreachable server:** failure after the 120-second deadline.
- **Effective VM Compose model:** base, VM override and test images.
  - The image entrypoint runs with only the six variables.
  - The image accepts state created by the local inline initializer unchanged.
  - A later conflict yields exit 1 and the dependent service is not started.

Local run on 31 October 2026, on an Apple Silicon host: linux/amd64 under
emulation, Docker Engine 29.8.1, Compose v5.5.1, host bash 3.2. All 20 checks
passed. Two deliberately broken builds were rejected: one overwrote a conflicting
policy, the other reset an existing secret. A test-built image is not the
release artifact: image IDs differ per build.

Local run on 1 November 2026 after the credential-validation fix, on the same
host type and versions: all 21 checks passed. The same suite failed against an
image built from the earlier `6589ad6` source. That earlier initializer accepted
an access key containing `=` and left a bucket and policy behind when MinIO then
rejected the identity.

On GitHub, the “VM MinIO initializer qualification” job passed all 20 checks at
`6589ad6` in [run 41214541688](https://github.com/chi-thanh-nguyen/codemastery/actions/runs/41214541688).
It is not one of the protected required checks.

### MinIO artifact review (as of 31 October 2026)

| Aspect | Evidence | Finding |
| --- | --- | --- |
| Architecture | Both pins are OCI indexes with linux/amd64 and linux/arm64 manifests. Server amd64: `sha256:186184b0b9fcf9743c20c114adecb3411b46c9b71d88e4ca854f6da79ec3440a`; `mc` amd64: `sha256:3a7b97e9b7bb78ee00343dd2100f07948dcdce4b224decbc3a52935fd2c17eb0` | The amd64 variants run in the suite. B1 capacity remains unmeasured |
| Source correspondence | The server binary reports `DEVELOPMENT.2025-10-15T17-29-55Z`, commit `9e49d5e7a648`, go1.24.9. `mc` reports `RELEASE.2025-08-13T08-35-41Z`, commit `7394ce0dd2a8`, go1.24.10. Both commits equal the upstream release tags | Self-reported build metadata; not a reproduced build |
| Build provenance | `golithus/minio-builds` (third party; repository license NOASSERTION). It clones upstream by **mutable tag**, uses floating Go `1.24.x` and actions by major version, and copies the pre-built binary into the image. BuildKit provenance is `min` with incomplete materials; no SBOM packages; no signatures. The repository was last pushed on 2025-11-06 | Weak provenance: binaries cannot be traced to source by attestation |
| Runtime security, server | Scratch image with only `/minio`; runs as **root**; Docker default capabilities. The Compose contract forbids `user`, `cap_drop` and `security_opt`. The console on `:9001` is enabled by the approved command; unpublished, but reachable on the private network | Hardening needs a derived image and contract change; not approved |
| Runtime security, initializer | UBI 9 micro 9.6 base with `mc`. Runs as 65532 with zero inheritable, permitted, effective and ambient capability sets (Docker's default bounding set); one-shot, no ports, volumes or restart | Meets this task's runtime requirements |
| License | Server and `mc` are AGPL-3.0 (labels and `--version`). Base image under the Red Hat UBI EULA. Used unmodified | No legal determination is made here |
| Maintenance | `minio/minio` and `minio/mc` are **archived** on GitHub. Their latest community releases are exactly the pinned versions. The Go 1.24 toolchain is outside the Go project's support window | No upstream Community fixes can be expected |
| MinIO advisories after the pin | Fixed only in MinIO AIStor releases: CVE-2026-41145 and CVE-2026-40344 (high; unauthenticated object writes that need a valid write-capable access key); CVE-2026-39414 (high; S3 Select memory exhaustion); CVE-2026-34204 (high; SSE metadata injection by authenticated users); CVE-2026-42600 (medium) | Unpatched in the pinned server |
| Go vulnerabilities | `govulncheck` v1.1.4, go1.26.10, vuln.go.dev, binary mode. Server: 106 reachable findings in 13 modules and the standard library. `mc`: 87 in 6 modules and the standard library | Symbol reachability, not demonstrated exploitability; MinIO's own advisories are not in this database |

**Verdict: the pinned MinIO server is not production-qualified.** Its private
placement reduces exposure. It has no host port, and the advisories above need
network reach to `minio:9000` plus a known write-capable access key. Every
container on the project network has that reach, including the public-facing
frontend. Reduced exposure is not risk acceptance.

The initializer's behavior is qualified by the suite for the tested build. The
image is still not admitted: it carries the `mc` findings above, and its OS
packages have not been scanned.

### Remaining qualification blockers and Leader decisions

1. **Server artifact.** The Leader must decide the server artifact. Options:
   accept the documented risk for Interim with these compensating controls, or
   select another Community-compatible source through a separate dependency
   decision. AIStor is excluded by this task.
2. **Missing review evidence:** an OS-package vulnerability scan of both images,
   independent binary reproduction or signed provenance, and a license
   assessment for the course context. None was performed here.
3. **Release initializer admission.** Build the initializer from the reviewed
   revision and run `test-minio-init.sh IMAGE` against that exact artifact.
   Record that evidence as the `--initializer-qualification` reference. Only
   then tag it, after CODEOWNER review.
4. **CI.** The new job runs on every pull request but is not a required check.
   Making it one is a Leader decision. *(Superseded as current state on
   1 November 2026: the job is now required; see
   [Current required-check status](#dp01-chainguard-minio-release-server-candidate--1-november-2026).)*
5. **Operational limits.**
   - The initializer assumes no concurrent IAM administration during its run.
   - Secret rotation for an existing identity needs a separate authorized
     procedure.
   - Server-wide grants outside this identity are not inspected: other users'
     global policies, LDAP/OIDC, replication, versioning, lifecycle or
     notifications.

The implementation, this record and its tests were produced through AI-assisted
development (Anthropic Claude Code) under the Leader's task. `govulncheck` and
the Go vulnerability database were used only for this review. No AI service or
new infrastructure technology is introduced into CodeMastery.

## DP01 Chainguard MinIO release-server candidate — 1 November 2026

### Decision and boundary

The Project Leader @chi-thanh-nguyen's 1 November 2026 (ICT) task on branch
`chore/dp01-chainguard-minio` selects Chainguard's MinIO image as the **proposed**
DP01 release storage server and directs its repository qualification. This
answers the "another Community-compatible source" option in
[Remaining qualification blockers](#remaining-qualification-blockers-and-leader-decisions)
for qualification only. It does not admit the image, approve it for production,
create a `codemastery.invalid/minio:release` tag, deploy anything or close DP01 #71.

Candidate, selected only by this immutable OCI index digest:

`cgr.dev/chainguard/minio@sha256:3b6e40c30ecd5cea627e382ff0b82ca841f58c52294578787043ee80c8ef7c82`

Unchanged: the Golithus local baseline (server and `mc` pins, the base Compose
file and its inline initializer), `infrastructure/minio/minio-init.sh` and its
Dockerfile, the VM override with its abstract `codemastery.invalid/minio:release`
name, `deploy.sh`, `.env.example` and the five-service model. The image has no
default command; the base Compose `command: ["server", "/data", "--console-address", ":9001"]`
supplies it, and the VM override inherits it. No Compose change is needed.

### Qualification

`infrastructure/minio/test-minio-init.sh` now selects its server with
`MINIO_INIT_TEST_SERVER`:

```bash
docker pull --platform linux/amd64 ghcr.io/golithus/minio:RELEASE.2025-10-15T17-29-55Z@sha256:8793e960474071520bdb91bb9a6d1793eb229fdbc9dcb7ec75ad836b69bbab40
docker pull --platform linux/amd64 cgr.dev/chainguard/minio@sha256:3b6e40c30ecd5cea627e382ff0b82ca841f58c52294578787043ee80c8ef7c82
infrastructure/minio/test-minio-init.sh                                  # golithus (default)
MINIO_INIT_TEST_SERVER=chainguard infrastructure/minio/test-minio-init.sh
```

An unset variable selects Golithus; any other value, including an empty one,
fails before testing. Each server is fixed by digest in the suite
and must match its expected user, entrypoint, default command and `--version`
output for the platform. Servers start with the base Compose command. The 21
existing checks are unchanged. Three checks are added for both servers:

- **Server artifact:** a digest-pinned reference with the expected
  configuration and binary version.
- **Named-volume persistence:** a disposable named volume on a clean first-time
  server. The test writes an object, removes the container (keeping the volume)
  and recreates it. It then reads the object back exactly. A repeat initializer
  run reports `changes: 0` with unchanged IAM state, and the data directory
  owner is checked (0 for Golithus, 65532 for Chainguard).
- **Effective VM `minio` model:** base plus VM override keeps
  `codemastery.invalid/minio:release`, inherits the base command, mounts the
  Docker named volume `minio-data` at `/data` (a bind mount or another volume
  name fails) and restarts `unless-stopped`; with the test images the selected
  server runs it, with no entrypoint, user or port overrides.

The local inline initializer in the Compose checks runs against the selected
server too, so no data volume passes between server builds. **Migration of an
existing data volume was not tested.** A volume written by the root Golithus
server would hold root-owned files, which UID 65532 is not expected to be able to
use. Any such migration needs a separate procedure. Only clean first-time
deployment is qualified.

Local run on 1 November 2026, on an Apple Silicon host: linux/amd64 under
emulation, Docker Engine 29.8.1, Compose v5.5.1, host bash 3.2. On the final
suite source, Golithus passed 24/24 checks (287 s) and Chainguard passed 24/24
(286 s). Deliberately wrong configurations were rejected with exit 1:

- an unknown or an empty server name;
- the Chainguard selector bound to another image (configuration mismatch);
- a mutable tag (not digest-pinned);
- a VM `minio` override using a bind mount, a differently named volume or restart
  policy `no` (each failed the effective-model check).

Labelled containers, networks and volumes were removed, and pre-existing Docker
volumes were unchanged.

CI job **VM MinIO initializer qualification** pulls all three pins. It then runs
the suite for Golithus, then for Chainguard, in one job. The second run is
reported even when the first fails; either failure fails the job. One job keeps
the check name, which the `main-quality-gates` ruleset requires (read from the
GitHub API on 1 November 2026); a matrix would rename it. Results belong to the
pull request.

**Current required-check status (1 November 2026).** `VM MinIO initializer
qualification` is now a required GitHub status check: the `main-quality-gates`
ruleset, read through the GitHub API on 1 November 2026, lists it among the
required checks. This supersedes, as current state, the statements in the
[31 October record](#dp01-minio-community-initializer--31-october-2026) that the
job is not a required check and that making it one is a Leader decision
(blocker 4); those dated statements are kept unchanged as that day's record. This
record does not say who changed the ruleset or when. A pull request therefore
cannot merge while either server suite fails, and the required job now also
depends on pulling the Chainguard candidate from `cgr.dev`.

### Artifact review (as of 1 November 2026)

Evidence was taken from the selected artifact. Signatures were checked with
cosign v3.1.4 (`cgr.dev/chainguard/cosign`, index
`sha256:35b607c4eba6e914b3b8b80f5f8a63ce2eccd48617717d5edd572dd62f0cdf66`).
Vulnerabilities were scanned with Grype 0.121.0 (`anchore/grype`,
`sha256:621d56d1d68106b787022c81546f7e0a36202a25d1239f0c20195fab8c16f7ea`;
database built 2026-10-31T06:18:17Z) on a `docker save` archive of the amd64
manifest. Both tools ran locally in containers and are not project dependencies.

| Aspect | Evidence | Finding |
| --- | --- | --- |
| Architecture and version | OCI index with linux/amd64 `sha256:9cc9cf7bde067425120f37019f5c7f5d565737b2ee6ef66200c4c34e4b9b0fac` and linux/arm64 `sha256:f4c90d3148e56e4b720a5663a9e45428bac3e38c39681bbc0d8692ed1086ff09`. Created 2026-10-29T14:33:12Z. `--version`: `RELEASE.2026-10-23T20-06-51Z`, commit `76c06ff1d2cc844050fdeb1d54bf8fa70db72278`, go1.27.3 linux/amd64 | The amd64 variant runs in the suite. B1 capacity is unmeasured |
| Source correspondence | The in-image SBOM names `chainguard-forks/minio@RELEASE.2026-10-23T20-06-51Z`, built by `chainguard-dev/stereo` `minio.yaml` at `fa994fc0`. On GitHub the tag resolves to `76c06ff`. The fork is 48 commits ahead of and 1 behind archived `minio/minio`. Go build info: `github.com/chainguard-forks/minio v0.0.0-20261023200316-76c06ff1d2cc+dirty` | Self-reported. `+dirty` means changes beyond the tagged commit. Not a reproduced build |
| Signatures and provenance | Keyless signatures on the index and the amd64 manifest verified. Issuer `https://token.actions.githubusercontent.com`; identity `https://github.com/chainguard-images/images/.github/workflows/release.yaml@refs/heads/main`; workflow SHA `cabe8541a98ef0fea78a95fd236bacf87b6bdb5c`; transparency-log inclusion verified offline. Signed SLSA v1 provenance: apko build type, `terraform-provider-apko` v1.2.28 / apko v1.4.14, no external parameters or resolved dependencies | Signed image assembly. The provenance does not trace the `minio` binary to source |
| SBOM | Signed SPDX attestation: 470 packages for amd64. 23 OS packages, including `bash`, `coreutils`, `glibc` 2.44, OpenSSL 4.0 `libcrypto`, `minio-0.20261023.200651-r2` and an `mc` client (`mc-0.20250813.083541-r27`, upstream `mc` `7394ce0` +dirty) | Available and signed |
| Vulnerability scan | Grype: **0 matches**. Control: the same tool and database found 94 on the Golithus server (11 critical, 55 high, 26 medium, 2 low), but none of the five MinIO advisories listed on 31 October | Zero matches is not vulnerability-free. Grype does not cover MinIO's own advisories. `govulncheck`: NOT_VERIFIED |
| Security fixes | Fork commits before the tag name CVE-2026-34204 (`624d9a21f`), CVE-2026-42600 (`a611cd0d1`, `d43fb3476`), CVE-2026-41145 (`b94db7d78`, with a regression test), CVE-2026-40344 (`68719216d`), CVE-2026-33814 (`4bdc9f94e`) and CVE-2026-33322 (`54da56a8d`). No commit names CVE-2026-39414. `8bd18fa6d` ("bound CSV line reads in S3 Select parser") may correspond to it | In source ancestry only. Presence in the binary is NOT_VERIFIED (self-reported commit, `+dirty`, no exploit test). CVE-2026-39414 is NOT_VERIFIED |
| Maintenance | `minio/minio` is archived. `chainguard-forks/minio` is active (last push 2026-10-31; `master` is 2 dependency-bump commits past the tag) | The fork is active now. Continued availability of this digest and of the fork is NOT_VERIFIED |
| Runtime security | User `65532`; entrypoint `/usr/bin/minio`; no command or `VOLUME`. `/data` is root-owned mode 0777, so a new named volume is writable by 65532; the suite observed `.minio.sys` owned by 65532. No setuid/setgid files. Contains `bash`/`sh` (the Golithus server is scratch). Docker default capabilities; the console on `:9001` is enabled by the approved command | Non-root, unlike the root Golithus server. Shell and world-writable `/data` widen the surface; hardening needs a contract change |
| License | `minio` and `mc` are AGPL-3.0-or-later (SBOM and `--version`). Other packages carry their own licenses (for example GPL `bash`, LGPL `glibc`). Used unmodified | No legal determination is made. Chainguard's image terms are NOT_VERIFIED |

**Verdict: functionally qualified as a release candidate, not admitted.** The
suite qualifies initializer compatibility, persistence and non-root startup on a
clean volume for this digest. Admission needs the Leader's dependency decision on
the risks above.

### Admission and release packaging requirements

1. The Leader decides admission of this exact digest, or rejects it.
2. Re-verify the signature and re-scan at admission. Record the tool versions,
   the database date and the result.
3. Package only the amd64 variant: `docker pull --platform linux/amd64` of the
   digest, tag it `codemastery.invalid/minio:release`, then include it in the
   `docker save --platform linux/amd64` archive under
   [Release build outside the VM](#release-build-outside-the-vm). Take its
   `--expect-image` identity from the archive. The archive keeps the bytes if the
   registry stops serving the digest. Not done here.
4. Before first deployment, confirm that the VM has no existing MinIO data volume,
   or plan its migration separately. This was not verified.

The remaining DP01 blockers in
[Verification and remaining gates](#verification-and-remaining-gates) are
unchanged. Storage blocker 2 now stands at:

- **Server:** a qualified candidate that is not admitted.
- **Initializer:** a release artifact that is not admitted.

Azure deployment and DP01 completion are not claimed.

This record and its test changes were produced through AI-assisted development
(Anthropic Claude Code) under the Leader's task. cosign, Grype and their
databases, and the Sigstore public-good transparency log, were used only for
this review. No AI service or new infrastructure technology is introduced into
CodeMastery's runtime.

## DP01 minimal MinIO server candidate — 1 November 2026

### Boundary

Under the Project Leader's 1 November 2026 (ICT) "Package 1: Minimal MinIO Server
CI Integration" task on branch `chore/dp01-minio-server-minimal`, the suite and
the required CI job also qualify `infrastructure/vm/Dockerfile.minio-server`
(commit `8db0473`). This is functional qualification only. It does not admit the
image, approve it for production, create a `codemastery.invalid/minio:release`
tag, choose it over the [Chainguard candidate](#dp01-chainguard-minio-release-server-candidate--1-november-2026),
deploy anything or close DP01 #71. The VM override, `deploy.sh`, the base Compose
file, the initializer and its Dockerfile are unchanged.

### Image definition

The source stage is the Chainguard candidate, by the same index digest. A
`scratch` staging stage places the source's `/data` and `/tmp` under one parent
directory. The final `FROM scratch` stage copies only `/usr/bin/minio`, the CA
certificates, `/etc/passwd` and `/etc/group`, plus that parent's contents into
`/` with `--chown=65532:65532 --chmod=0700`. `/data` and `/tmp` are therefore
copied entries, owned by `65532:65532` with mode 0700. The staging stage was
added after the first native CI run (see [Native CI](#native-ci)). It sets `SSL_CERT_FILE`, `USER 65532:65532` and
entrypoint `/usr/bin/minio`, with no command, `EXPOSE`, `VOLUME` or healthcheck.
Unlike the Chainguard image, it has no shell, no `mc` client and no world-writable
`/data`. The base Compose command supplies `server /data --console-address :9001`,
as for the Chainguard image.

### Qualification

```bash
docker pull --platform linux/amd64 cgr.dev/chainguard/minio@sha256:3b6e40c30ecd5cea627e382ff0b82ca841f58c52294578787043ee80c8ef7c82
docker build --platform linux/amd64 -t codemastery-test/minio-server:qualification - < infrastructure/vm/Dockerfile.minio-server
MINIO_INIT_TEST_SERVER=minimal infrastructure/minio/test-minio-init.sh
```

The build has no context: the Dockerfile copies only from its source stage.
`codemastery-test/minio-server:qualification` is a local non-release tag. The
suite never builds, pulls or substitutes it: if it is missing, or the Chainguard
pin is missing, the run fails before testing. The mode accepts only linux/amd64.
`golithus` (the default when unset) and `chainguard` behave as before, and an
empty or unknown value still fails.

A local build has no registry digest, so the suite identifies the candidate by its
contents. Expected values are fixed in the suite, never read from the candidate
itself. They are checked inside the existing 24 checks, so every server still
reports 24:

- **Pin:** the Dockerfile's stages are exactly the Chainguard digest `AS source`,
  `scratch AS layout` and `scratch`.
- **Configuration:** linux/amd64, user `65532:65532`, entrypoint
  `/usr/bin/minio`, no command, ports, volumes, healthcheck, labels or `ONBUILD`.
  The environment holds only `SSL_CERT_FILE` and BuildKit's default `PATH`.
- **Filesystem:** the exact list of entries with type, mode, owner and link
  target, apart from entries that Docker adds to every container. Any shell, `mc`
  or other added file, setuid/setgid bit, or change in ownership or mode fails.
- **Binary:** SHA-256 of `/usr/bin/minio` is
  `3c3d615241314c531e15e57485c053e60dc1af04e87b20424d2db8bdec8a49b2`, both in the
  digest-pinned Chainguard image and in the candidate. `--version` reports
  `RELEASE.2026-10-23T20-06-51Z`, commit `76c06ff`, linux/amd64.
- **Process:** a running server, read through a sidecar in its PID namespace
  because the image has no shell. Its UID/GID and groups are 65532, and its
  inheritable, permitted, effective and ambient capability sets are empty.
  Docker's default bounding set and the console on `:9001` are unchanged from the
  Chainguard candidate.

All other checks run unchanged: input validation, first and repeat runs, least
privilege, conflicting state, readiness failure, named-volume persistence
(`.minio.sys` owned by 65532), and the effective VM Compose model with failure
propagation. As before, only clean first-time volumes are qualified.

CI job **VM MinIO initializer qualification** keeps its single job and check name.
After the Golithus and Chainguard suites, it builds the candidate with the
command above and runs the suite with `MINIO_INIT_TEST_SERVER=minimal`. The build
and the minimal suite also run when an earlier suite fails. Any failed build or
suite fails the job. The image is not pushed and no artifact is published.

### Evidence (1 November 2026)

Local runs of the original two-stage Dockerfile, on branch
`chore/dp01-minio-server-minimal` at `8db0473` plus the suite change: macOS 27.2 on Apple Silicon (linux/amd64 under
emulation), Docker Engine 29.8.1, Compose v5.5.1, host bash 3.2.57. The CI steps
were run as local commands. The commands and the negative cases are in
[testing.md](testing.md#dp01-minimal-minio-server-qualification--1-november-2026).

| Server | Result |
| --- | --- |
| Golithus (variable unset) | 24/24 checks passed (288 s) |
| Chainguard | 24/24 checks passed (291 s) |
| Minimal, built with the CI command (image ID `e5b79330af4c…`) | 24/24 checks passed (294 s) |

The negative cases each failed with exit 1:

- an empty, unknown or non-amd64 selection;
- a missing candidate, which was not pulled or replaced;
- the Chainguard image or a variant with an added `/bin/sh` under the candidate tag;
- a suite whose Chainguard pin no longer matched the Dockerfile;
- a wrong expected binary hash;
- a mutable Chainguard reference and a wrong Golithus version, both still rejected.

Labelled containers, networks and volumes were removed, and other Docker
resources were unchanged. These are local results, not GitHub Actions results; CI
results belong to the pull request.

#### Native CI

The first native linux/amd64 run of the required job on PR #126 failed: GitHub
Actions [run 41306581519](https://github.com/chi-thanh-nguyen/codemastery/actions/runs/41306581519),
job 124848097864, at `bfa06f9`. Golithus and Chainguard passed 24/24 and the
minimal image built. The minimal suite then stopped at check 3: on the runner's
builder, `/data` and `/tmp` were `0755`, not `0700`. Their contents had the
expected mode and every other entry matched. On that builder, `COPY --chmod`
set the mode of copied entries but not of the directory that the `COPY` itself
created. The local Docker Desktop builder (BuildKit v0.33.0) set both. The
two-stage image was therefore not reproducible across builders.

The staging stage above makes both directories copied entries. On the local
Docker Desktop builder, no image layer of the corrected image contains an entry
for `/`, so there the final `COPY` does not change the root directory. Native CI
did not inspect the image layers, and the suite's filesystem check does not cover
the root directory, so this property is verified on the local builder only. The
correction changes no test assertion other than the expected stage list.

Locally, the corrected Dockerfile built with the CI command passed the minimal
suite 24/24 under emulation. Native CI qualification was still pending when the
correction was committed.

**Native qualification passed.** On PR #126 head `a23436b`, GitHub Actions
[run 41308772749](https://github.com/chi-thanh-nguyen/codemastery/actions/runs/41308772749),
job 124854577665, ran natively on linux/amd64 (`ubuntu-24.04`). Its log shows the
build running the staging-stage `COPY`. Golithus, Chainguard and minimal each
logged 24 passing checks and no failure, including the minimal filesystem check
with `/data` and `/tmp` at `0700 65532:65532`. All other required checks and
CodeQL passed at that head. This is functional qualification only, not security
admission or release approval.

Earlier evidence from the Leader's terminal, before this change and not repeated
here: a no-cache rebuild of the Dockerfile produced a different image ID and some
different layers. All 27 examined filesystem entries matched in content and
checked metadata apart from modification times, and the binary SHA-256 matched.
**Bit-for-bit reproducibility is not established**, and image IDs are not
compared between builds. For a release, its identity would come from the release
archive through `--expect-image`, as for every release image.

### Not established and remaining gates

Functional qualification PASS is not a vulnerability-free artifact, not a
security admission, not release approval and not a production deployment.

- **No security review of this image.** It was not scanned and has no signature,
  SBOM attestation or provenance of its own. The [Chainguard review](#artifact-review-as-of-1-november-2026)
  applies to the source image; the binary is byte-identical to it, but the
  findings about that binary (self-reported source, `+dirty`, CVE-2026-39414
  NOT_VERIFIED) apply here unchanged.
- **Admission.** The Leader decides whether to admit this image, the Chainguard
  candidate or neither. Release packaging of a locally built server is not
  defined.
- **Initializer.** The initializer image (Golithus `mc` base) remains a separate
  storage blocker: it carries the `mc` findings in the
  [31 October review](#minio-artifact-review-as-of-31-october-2026) and is not
  admitted. This change does not alter or remediate it.
- **Deployment.** Nothing was built, loaded or run on the Azure VM. All seven
  release blockers in [Verification and remaining gates](#verification-and-remaining-gates)
  remain open; this change updates only the description of blocker 2. CI on the
  pull request, CODEOWNER review and protected merge are still required.

This record and its test changes were produced through AI-assisted development
(Anthropic Claude Code) under the Leader's task. No AI service or new
infrastructure technology is introduced into CodeMastery's runtime.

## DP01 MinIO initializer base remediation — 1 November 2026

### Boundary

The Project Leader's 1 November 2026 (ICT) "Package 2: MinIO Initializer
Security Remediation" task on branch `chore/dp01-minio-init-security` replaces
the base of the initializer image. It does not admit the image, approve a new
security contract, create a `codemastery.invalid/minio-init:release` tag, deploy
anything or close DP01 #71.

- **Changed:** only the `FROM` line and its base labels in `infrastructure/minio/Dockerfile`.
- **Unchanged:**
  - `minio-init.sh`, its entrypoint, the six variables, the bucket-scoped policy,
    the one-shot lifecycle and UID/GID `65532:65532`;
  - the local Compose pins and inline initializer;
  - the VM override, `deploy.sh` and the release image names;
  - the servers.

### Selected base

`cgr.dev/chainguard/minio-client@sha256:3b67177c0e12f44f646a5cf21b20fc5a79dd2d7a6b4db632b436f0b9e2e46717`
(OCI index; linux/amd64 manifest
`sha256:5ec4d9fc1c4ebe66d7fd9532c885b14924d9b134b9d162da690575002aee4e29`).
It was the public `:latest` tag on 1 November and is selected only by digest. The
`:latest-dev` variant (with busybox) was not needed.

| Aspect | Evidence | Finding |
| --- | --- | --- |
| Contents | Image created 2026-11-01T05:21:18Z. User `65532`; `/bin` links to `/usr/bin`; bash 5.3, GNU coreutils 9.12 (`timeout`, `mktemp`, `mkdir`, `rm`), `mc`; `/tmp` mode 1777; no setuid/setgid files. 21 Wolfi packages, including glibc 2.44 and OpenSSL 4.0 `libcrypto` | Provides everything `minio-init.sh` uses without a script change (its shebang is `#!/bin/bash`). Smaller than the Golithus UBI base: no `/bin/sh`, `sed`, busybox, package manager, `curl` or `wget` |
| Source correspondence | Package `mc 0.20250813.083541-r27`. Its SPDX record names `minio/mc@RELEASE.2025-08-13T08-35-41Z`, commit `7394ce0dd2a80935aded936b09fa12cbb3cb8096` (the Golithus `mc` commit), built by `chainguard-dev/stereo` `mc.yaml` at `4b2ea14`. Go build info: `github.com/minio/mc v0.0.0-20250813083541-7394ce0dd2a8+dirty`, go1.27.3 | Same `mc` source with updated Go dependencies (`+dirty`). `mc --version` reports `DEVELOPMENT.GOGET`, so the suite identifies the release by the image's package record. Self-reported, not a reproduced build |
| Signatures and provenance | cosign v3.1.4: keyless signatures on the index and the amd64 manifest verified, identity `https://github.com/chainguard-images/images/.github/workflows/release.yaml@refs/heads/main`, workflow SHA `e33e1d775a88d0d9773dce5b03fff028f9d1abbd`. Signed SPDX attestation (209 packages) and signed SLSA v1 provenance (apko build type, no resolved dependencies) | Signed image assembly. The Golithus base had none. The provenance does not trace the `mc` binary to source |
| License | `mc` AGPL-3.0-or-later (SBOM); other packages carry their own licenses | No legal determination. Chainguard's image terms are NOT_VERIFIED |
| Maintenance | `minio/mc` is archived upstream; this image rebuilds the archived release with current dependencies and toolchain | Continued availability of this digest and of free Chainguard images is NOT_VERIFIED. A release archive would keep the bytes |

### Vulnerability comparison

Both initializer images were built for linux/amd64 from the same `minio-init.sh`
and scanned as `docker save` archives with Grype 0.121.0 (`anchore/grype`
`sha256:621d56d1d68106b787022c81546f7e0a36202a25d1239f0c20195fab8c16f7ea`) and
one database snapshot (schema v6.1.10, built 2026-11-01T06:20:12Z). Default
policy; nothing was ignored or suppressed.

| Initializer base | Catalogued packages | Critical | High | Medium | Low | Total |
| --- | --- | ---: | ---: | ---: | ---: | ---: |
| Golithus `mc` (UBI 9 micro) | 100 Go modules, 22 RPM | 4 | 41 | 82 | 24 | 151 |
| Chainguard `minio-client` | 102 Go modules, 21 apk | 0 | 0 | 0 | 0 | 0 |

The 45 previous CRITICAL/HIGH findings all had published fixes:

| Package | Count | Example identifiers |
| --- | ---: | --- |
| Go stdlib | 25 | GO-2026-4337, GO-2026-6612 (critical); GO-2025-4155 |
| `golang.org/x/net` | 5 | GO-2026-6612 (critical), GO-2026-5026 |
| `google.golang.org/grpc` | 4 | GHSA-p77j-4mvh-x3m3 (critical) |
| `github.com/prometheus/prometheus` | 2 | GHSA-8rm2-7qqf-34qm |
| `golang.org/x/text` | 1 | GO-2026-5970 |
| RPM `pcre2`, `pcre2-syntax`, `libcap`, `libacl` | 8 | CVE-2026-103111, CVE-2026-4878, CVE-2026-54369 |

The candidate carries those modules at or above the fixed versions: Go
1.24.10 → 1.27.3, `x/net` v0.42.0 → v0.60.0, `grpc` v1.71.0 → v1.83.2,
`prometheus` v0.303.0 → v0.311.3, `x/text` v0.27.0 → v0.42.0.

Limits:

- Zero matches is not vulnerability-free. Grype matches known advisories to the
  packages it catalogues; it does not cover MinIO's own advisories or show
  reachability.
- `govulncheck` was NOT_VERIFIED for the candidate.
- The result is specific to this digest and database date. Admission requires a
  re-scan with recorded versions.

### Qualification

`infrastructure/minio/test-minio-init.sh` keeps the Golithus `mc` from the base
Compose file as its independent test client and checks its pin as before. The
initializer base is a separate suite constant.

- Check 2 requires the Dockerfile's `FROM` to equal that digest-pinned constant.
- Check 4 expects the base's environment (`PATH` and `SSL_CERT_FILE`).
- Check 5 now also requires the image's package record to name `mc
  0.20250813.083541-r27`.
- Check 4 also verifies the artifact itself. This applies to an image built by
  the suite and to one supplied as `IMAGE`, for linux/amd64 only:
  - **Layers.** The suite exports the image with `docker save` and re-hashes every
    layer against its RootFS diff ID. The diff IDs must be exactly the pinned
    base's 11, recorded in the suite from the base's immutable configuration,
    plus one layer.
  - **Added layer.** That layer may contain only `usr`, `usr/local`,
    `usr/local/bin` (root-owned, not group- or world-writable) and
    `usr/local/bin/codemastery-minio-init`. That file must be root-owned, mode
    0555, and byte-identical to `infrastructure/minio/minio-init.sh`.
  - **Binary.** SHA-256 of `/usr/bin/mc` must be
    `8842390a4419ff18ec9c43aa71b41beb69030ea56df4f449c03b9623af0bc34a`, both in
    the digest-pinned base (pulled by digest when missing) and in the image under
    test.
  - **Fail closed.** A missing manifest, configuration or file, or an unavailable
    base, fails the check.

  This replaces an earlier version of the suite that relied on package metadata
  and Dockerfile text; review showed it accepted wrongly sourced images (see the
  bypass results below).

Every server still reports 24 checks. CI builds the initializer through the suite
as before; the workflow is unchanged.

Local runs on 1 November 2026 under linux/amd64 emulation (macOS 27.2 on Apple
Silicon, Docker Engine 29.8.1, Compose v5.5.1), with the CI command for the
minimal server:

| Server | Result |
| --- | --- |
| Golithus | 24/24 checks passed (301 s) |
| Chainguard | 24/24 checks passed (304 s) |
| Minimal | 24/24 checks passed (384 s) |

Each of these failed with exit 1:

- an empty or unknown server selection;
- a `FROM` that differs from the approved base;
- the previous Golithus-based initializer as `IMAGE` (configuration mismatch);
- a test-only copy of the candidate whose package record names another `mc`
  release.

No initializer output disclosed a secret. Labelled test resources were removed.
These are local results; native CI results belong to the pull request.

**Artifact identity bypasses (review remediation, 1 November 2026).** Three
deliberately mis-sourced, test-only images were run as `IMAGE`:

- **A1:** the Golithus `/usr/bin/mc` copied over the base's in a separate layer,
  package record kept.
- **A1b:** the same substitution and the script in one added layer, so the layer
  count matches.
- **A2b:** the unchanged Dockerfile on a different Chainguard `minio-client`
  digest (`:latest-dev`, `sha256:8419d1a4…`) with the same `mc` package record.

The earlier suite (PR head `2c87571`) passed all three 24/24. The current suite
fails each at check 4: A1 and A2b on the RootFS diff IDs (A2b has the same layer
count), A1b on the extra `usr/bin/mc` entry in the added layer. With the layer
verification disabled in a test-only copy, A1b still fails on the `mc` SHA-256
(Golithus `6c79b743…`), and a wrong expected hash fails on the pinned base itself.
Legitimate runs with the current suite: Golithus 24/24 (328 s), Chainguard 24/24
(343 s), minimal 24/24 (335 s), locally under emulation.

An older Chainguard digest (`sha256:a8a0480c…`) with the same `mc` package
version also failed functionally under the earlier suite, at check 8 after it had
created the bucket, policy and identity. This is a further sign that package
metadata does not identify behavior.

### Not established and remaining gates

Functional qualification and a lower scanner count are not security admission,
release approval or production deployment.

- **Admission.** The Leader decides whether to admit this base and the resulting
  initializer, or to reject them. Re-verify the signature and re-scan at
  admission.
- **Release.** Build the release initializer from the reviewed revision, run
  `test-minio-init.sh IMAGE` against that exact artifact and record it as the
  `--initializer-qualification` reference. Tag only after CODEOWNER review. Not
  done here.
- **Server and DP01.** The server admission decision and release blockers 1 and
  3–7 in [Verification and remaining gates](#verification-and-remaining-gates)
  are unchanged. Nothing was built, loaded or run on the Azure VM.

This record and its changes were produced through AI-assisted development
(Anthropic Claude Code) under the Leader's task. Grype, cosign and their
databases, and the Sigstore public-good transparency log, were used only for
this review. No AI service or new infrastructure technology is introduced into
CodeMastery's runtime.

## DP01 private health probe remediation — 1 November 2026

### Authority and boundary

Under the Leader's “DP01 Deployment Blocker Remediation” task, this change
implements the existing contract: P4 nondetailed health, checked privately by the
operator, and CT17.I-access's existing `/actuator/health` without details. It adds
no public health route, probe group, endpoint, ingress rule or `deploy.sh`
behavior. It replaces only the 401 observation recorded under
[Supersession, exclusions and acceptance](#supersession-exclusions-and-acceptance).
The Security change needs Auth CODEOWNER review.

### Root cause

`SecurityConfig` had no rule for `/actuator/health`, so the request fell through to
`anyRequest().denyAll()`. For a caller without a token, Spring Security sends that
denial to the authentication entry point. `RestSecurityErrorHandler` then answered
401 `application/problem+json` with `WWW-Authenticate: Bearer`. Nginx was not in
the path, and the image was not stale. The same response came from a prod-profile
backend built from `main` at `204483c`, probed by the exact `deploy.sh` `wget`
command over a private Docker network.

### Change

One rule, `requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()`, placed
after the public operations. Path matching is exact, so `/actuator/health/` and
`/actuator/health/{group}` still fall to default-deny. Health stays
`show-details: never`. The release Nginx still does not proxy `/actuator`, which CI
already checks through the ingress. Protected operations, role rules, JWT
validation and CORS are unchanged.

### Verification

- **Integration tests** in `AuthorizationIntegrationTest` cover:
  - anonymous UP with only `status` and the probe group names;
  - DOWN as 503 without diagnostics;
  - 401 for anonymous callers and 403 for authenticated callers on other Actuator
    and API-documentation paths;
  - 403 for non-GET health requests;
  - 401 for a missing, malformed, tampered, unknown-account or locked-account JWT;
  - 403 for Instructor and Administrator on a Learner-only operation.

  The two health tests fail with 401 when the rule is removed.
- **Real HTTP, local.** On a prod-profile backend image built from this change
  (arm64 build host), the exact probe returned
  `200 {"groups":["liveness","readiness"],"status":"UP"}`. After PostgreSQL was
  stopped it returned 503 within about 30 seconds. Other Actuator paths, probe
  groups and `/v3/api-docs` returned 401, and `POST /actuator/health` returned 403.
- **CI.** The real-backend step of job **VM HTTPS ingress** now asserts these
  results instead of printing the status for information.

### Limits

Nothing was built, loaded or run on the VM, and no release image was produced or
admitted. Blocker 1 closes only when a release backend from the merged revision
passes `deploy.sh`'s probe on the VM. Blockers 2–7 are unchanged.

**Image scan for admission review (blocker 3).** Trivy 0.58.1 scanned the local
arm64 backend image and `postgres:17.11-bookworm` for HIGH and CRITICAL
vulnerabilities, using its database as of 1 November.

- **Backend:** 4 CRITICAL in `tomcat-embed-core` 11.0.24, all fixed in 11.0.25.
  10 HIGH in Jackson 2.21.5 and 3.1.5 core/databind.
- **PostgreSQL:** 4 CRITICAL and 94 HIGH, in Debian 12.15 packages (most without a
  fix) and in `gosu`'s Go 1.24.6 standard library.

These library versions are managed by Spring Boot 4.1.1. Overriding them, or
changing the PostgreSQL image, needs a separate dependency decision; this change
makes neither. Reachability was not assessed, and a linux/amd64 release artifact
still needs its own scan.

## DP01 MinIO Console hardening — 2 November 2026

### Boundary

Pre-release hardening of the VM release configuration. It changes the release
override, `deploy.sh`'s MinIO environment checks and their tests. The base Compose
file and local development (Console on `127.0.0.1:9001`), the private S3 API on
`minio:9000`, the initializer, the `codemastery.invalid/*:release` names and the
published ports are unchanged. The `minio` service keeps its image name, command,
volume and network; one environment entry is added (CT17.I-scope). It removes an
administrative interface and grants no access; access decisions remain
Leader-reserved (CT17.I-runtime). Nothing was admitted, packaged, tagged or
deployed.

### Change

| File | Change |
| --- | --- |
| `infrastructure/vm/docker-compose.vm.yml` | `minio` gets `MINIO_BROWSER: "off"`. The inherited command still names `--console-address :9001`; MinIO ignores it when the Console is off |
| `infrastructure/vm/deploy.sh` | Compose policy: `minio` may carry only `MINIO_ROOT_USER`, `MINIO_ROOT_PASSWORD` and `MINIO_BROWSER`, and `MINIO_BROWSER` must be exactly `off`. Image policy: the `minio` image may not bake any `MINIO_*` variable. Container check: the `minio` container's `MINIO_*` variables must be exactly the modelled ones |
| `infrastructure/minio/test-minio-init.sh` | Console checks for every server (26 checks instead of 24) |
| `.github/workflows/ci.yml` | Fail-closed cases for the three checks; the synthetic preflight override carries the setting; the committed-override step asserts it |

The image and container checks exist because MinIO loads the file named by
`MINIO_CONFIG_ENV_FILE` at startup and sets every variable in it
(`cmd/common-main.go` at fork commit `76c06ff`). A baked or injected variable
could otherwise re-enable the Console or replace credentials behind an unchanged
Compose model.

### Behavior of `MINIO_BROWSER=off`

At `76c06ff`, MinIO parses `MINIO_BROWSER` before startup (default `on`; an
invalid value is fatal). The Console server starts only when it is enabled, and
only that path calls `minioConfigToConsoleFeatures`, which sets the Console's
PBKDF salt and passphrase to the deployment ID.

Observed on the minimal image built from `Dockerfile.minio-server` (image ID
`sha256:684e55a2…`, binary `3c3d6152…`) with the base command. Docker's embedded
DNS listener on `127.0.0.11` is excluded.

| `MINIO_BROWSER` | Server | Listening TCP ports | Startup log |
| --- | --- | --- | --- |
| unset | Running | 9000, 9001 | `WebUI: …:9001` |
| `on` | Running | 9000, 9001 | `WebUI: …:9001` |
| `off` | Running; 9001 refuses connections | 9000 | No `WebUI` line |
| `maybe` | Exit 1 | — | `FATAL Invalid MINIO_BROWSER value` |

### Vulnerability re-check

Sources: NVD and GitHub advisory records retrieved on 2 November 2026, the fork's
commit history and the pinned binary. This corrects the
[1 November artifact review](#artifact-review-as-of-1-november-2026). That review
listed CVE-2026-33814 as a MinIO fix; it is a Go HTTP/2 advisory (GO-2026-4918),
which Grype's Go module matching covers and did not report. The review also
missed CVE-2026-97731, which has no package mapping, and CVE-2026-113466.

An NVD keyword search for advisories published between 1 October 2025 and
1 November 2026 found 10 MinIO server CVEs:

| CVE | Severity | Status at `76c06ff` |
| --- | --- | --- |
| CVE-2025-62506 | High | Upstream fix `c1a49490c` in ancestry |
| CVE-2026-33322 | Critical | Fix `54da56a8d` in ancestry |
| CVE-2026-33419 | Critical | `8478a2086` (LDAP rate limiting) matches the description; LDAP is not configured |
| CVE-2026-34204 | High | Fix `624d9a21f` in ancestry |
| CVE-2026-39414 | High | `8bd18fa6d` matches the description |
| CVE-2026-40344 | High | Fix `68719216d` in ancestry |
| CVE-2026-41145 | High | Fix `b94db7d78` and test `d3015c5af` in ancestry |
| CVE-2026-97731 | High (7.1) | Fix `df34868a8` and three regression tests in ancestry. **Verified on the binary** (below) |
| CVE-2026-42600 | Medium | Fixes `a611cd0d1`, `d43fb3476` in ancestry |
| CVE-2026-113466 | Medium (NVD 5.3, CNA VulDB; status Deferred) | **Not fixed** |

"In ancestry" means the commit is an ancestor of `76c06ff`. Except for
CVE-2026-97731, presence in the binary is not verified (`vcs.modified=true`, and
the build recipe is not public).

**CVE-2026-97731 (GHSA-r7wh-x6rf-8g63).** A presigned PUT replayed with an
unsigned `x-amz-copy-source` header received HTTP 400 `AccessDenied` from the
minimal image with `MINIO_BROWSER=off`, and no object was copied (2 November).
On 1 November the same probe copied a private object through the Golithus server,
so the probe detects the flaw.

**CVE-2026-113466 (GHSA-q95x-chg5-qwqh, no patched version).** At `76c06ff` the
Console key is still derived from the deployment ID (`cmd/common-main.go`
lines 121–122), and S3 errors still expose that ID as `HostID`
(`cmd/object-handlers.go` lines 251, 270 and 3596). The fork's commits after the
tag, up to its last push on 31 October, are dependency updates. With the Console
off, the release server does not run the affected component and has no Console
listener, even on the private network. **The weakness stays in the binary and is
not patched.** Re-enabling the Console would restore the exposure; `deploy.sh`
now refuses that configuration.

### Release-readiness checks

| Item | Result |
| --- | --- |
| Image identities | Unchanged. The server pin, the minimal binary SHA-256 and the initializer base diff IDs are checked on every suite run |
| Runtime privileges | Unchanged. The minimal server runs as UID 65532 with no effective, permitted or ambient capabilities; neither image has setuid/setgid files or file capabilities |
| Private ports | The VM model publishes only the frontend on 443; `deploy.sh` rejects published `minio` ports; the release server listens only on 9000 |
| Initializer compatibility | With the Console off: first run, application put/get/list/delete, and the release Compose path on local-initializer state |
| Release artifacts | None built, tagged or loaded. The archive and `--expect-image` requirements in [Release build outside the VM](#release-build-outside-the-vm) are unchanged |

### Qualification, admission and deployment

- **Functional qualification:** the three candidate servers and the initializer
  pass the suite locally ([testing.md](testing.md#dp01-minio-console-hardening-verification--2-november-2026)).
  GitHub Actions results belong to the pull request.
- **Security admission:** not granted. No MinIO server or initializer artifact
  is admitted.
- **Production deployment authorization:** not granted. Deployment, public
  exposure and the release blockers in
  [Verification and remaining gates](#verification-and-remaining-gates) are
  unchanged.

### Proposed residual-risk acceptance — `PENDING_LEADER_APPROVAL`

These dispositions are proposals for the Leader's admission decision. None is
accepted.

| Risk | Control | Proposed disposition |
| --- | --- | --- |
| CVE-2026-113466 unpatched in the binary | Console off and enforced; root credentials only in `minio` and `minio-init`; no published MinIO ports; private bucket | `PENDING_LEADER_APPROVAL` |
| Binary provenance: `+dirty` build, non-public build recipe; fixes other than CVE-2026-97731 checked only in source | Signed upstream image; minimal binary byte-identical to the pin | `PENDING_LEADER_APPROVAL` |
| Locally built images are unsigned | Archive SHA-256, `--expect-image` and the suite's identity checks | `PENDING_LEADER_APPROVAL` |
| AGPL-3.0-or-later `minio` and `mc` | Used unmodified, as separate private services; neither image carries the license text; complete corresponding source for the `+dirty` build is not public | `PENDING_LEADER_APPROVAL`. Legal judgment is needed before the images or archive are given to anyone else, and on whether copying the binary into the minimal image counts as modification |
| Docker default bounding capabilities | Non-root with no effective capabilities; no setuid/setgid files or file capabilities | `PENDING_LEADER_APPROVAL` |
| Existing MinIO data volume | Not enforced by `deploy.sh`; the operator confirms none exists before the first deployment | `PENDING_LEADER_APPROVAL` (operational) |

### Remaining gates

- **Admission.** The Leader decides admission of the server and initializer
  artifacts and the dispositions above. Re-verify signatures and re-scan at
  admission.
- **Packaging.** Build the release images from the merged revision, run
  `test-minio-init.sh IMAGE` against that exact initializer and record it as the
  `--initializer-qualification` reference. Not done here.
- **DP01.** Release blockers 1 and 3–7 are unchanged. Nothing was built, loaded or
  run on the Azure VM.

This record and its changes were produced through AI-assisted development
(Anthropic Claude Code) under the Leader's task. No AI service or new
infrastructure technology is introduced into CodeMastery's runtime.

## DP01 PostgreSQL Alpine/su-exec image hardening — 2 November 2026

### Decision and boundary

The Project Leader @chi-thanh-nguyen's 2 November 2026 (ICT) task authorizes
replacing `postgres:17.11-bookworm` with a locally built hardened image —
`postgres:17.11-alpine3.24` with `su-exec` replacing the vulnerable `gosu`
executable — as the PostgreSQL image used by local Compose, Testcontainers and
the VM release ([ADR003](decisions/003-postgresql-alpine-su-exec-image.md)).
This follows up the unresolved dependency decision noted in
[Image scan for admission review (blocker 3)](#dp01-private-health-probe-remediation--1-november-2026):
"changing the PostgreSQL image needs a separate dependency decision; this
change makes neither." It is that separate decision. Technical authorization
does not accept the residual risks below; that acceptance is pending.

Unchanged: PostgreSQL major version 17, database names, environment variable
contracts, `/var/lib/postgresql/data` persistence, the healthcheck, Flyway
migrations and schema, runtime role/grant separation, private networking (no
published PostgreSQL port), and the backend/MinIO/frontend service boundaries.

New file: `infrastructure/postgres/Dockerfile`, selected only by this immutable
base digest:

`postgres:17.11-alpine3.24@sha256:266d3c257594e195b309ae9d1805e54c788f1d7b375f56bf6c367aac6c917e7b`

It installs `su-exec=0.3-r0`, removes `/usr/local/bin/gosu` and links that path
to `su-exec` in three separate `RUN` layers, labels the image without an empty or
invented version/revision, and adds a data-volume boundary (below). The
separate removal layer matters to Trivy, not to the bytes: removal writes a
whiteout (`usr/local/bin/.wh.gosu`), and Trivy drops a lower layer's findings
only for a whiteout. Combined into one `rm … && ln -s …` layer, the symlink
replaces the file without a whiteout, and Trivy still reports the base layer's
`gosu` (1 CRITICAL, 25 HIGH), although the runtime view is identical.

### Qualification

Recorded on 2 November 2026 on macOS 27.2 arm64, Docker Desktop with Engine
29.8.1, Trivy 0.76.1 (database updated 2026-11-01 19:08 UTC, not refreshed
during the scans). Sources: the audit remediation measured on the tree
committed as `cc5cb95`, and the `su-exec` pin measured on `55902cc` plus that
pin. Artifact: a local `docker build --no-cache --platform linux/amd64
infrastructure/postgres` with the pin, image ID
`sha256:f7cccf3d0c3f03268d18d1ca519bb1c23170cb65239b7ddad62972739af359bd`
(14 layers).

- `postgres --version` → `postgres (PostgreSQL) 17.11`; `/usr/local/bin/gosu`
  → `/sbin/su-exec`; `gosu postgres id` → `uid=70(postgres) gid=70(postgres)`;
  the server runs as `postgres`.

**`su-exec` package.** Alpine v3.24/main offers exactly `0.3-r0` (aports commit
`3fd3d4a8594debe3a34527c531dec7c791bc0ec9`). `apk-tools` 3.0.8 installs the
pinned version and fails when the index does not offer it. Both packages
pass `apk verify` against the Alpine developer keys shipped in the pinned base:

| Architecture | Index checksum (`C:`) | Package SHA-256 | Signing key | Installed `/sbin/su-exec` SHA-256 |
| --- | --- | --- | --- | --- |
| x86_64 (deployed) | `Q1qSe5GhOmAg5kGDr0aUxb07P589Q=` | `4003163f58cd4a75df79973171d25e4912df4284d8bac9850ac267b58b969be5` (3,775 bytes) | `alpine-devel@lists.alpinelinux.org-6165ee59` | `725e4e701f4b05fae05e2121a088ac3efe18768ffde9d542f3a310f5853eb574` |
| aarch64 | `Q1+O7B2wiWhg5VbXFCiAnyDcfnpmg=` | `e40ecb44d07d25686f0e0836f4990e7c47d5d44d8956bcb523f5cab9108dd156` (4,062 bytes) | `alpine-devel@lists.alpinelinux.org-616ae350` | `d8210266a508d301fe0a5111e2486af823c5a9a57629ca5c1d53f5e91ed4951e` |

**Reproducibility.** The build is not bit-for-bit reproducible. A second
`--no-cache` build gave image ID `sha256:58156baf…6f76f`: layers 0–9 (the
pinned base) are identical; in layers 10–13 the only content difference is
`var/log/apk.log`, and the rest differ in file modification times only. The
installed `su-exec` binary is identical. Image identity for a release
therefore comes from the reviewed archive and `--expect-image`, not from a
rebuild.

**Trivy, CRITICAL/HIGH** (`docker save --platform …`, `trivy image --input`):

| Image | CRITICAL | HIGH | Of which `gosu` |
| --- | --- | --- | --- |
| `postgres:17.11-bookworm` (`sha256:839fbb5a…57a5`), `linux/amd64` and `linux/arm64` | 4 | 88 | 1 / 25 |
| `postgres:17.11-trixie` (`sha256:3b0cda93…84e1`), `linux/amd64` | 2 | 88 | 26 combined |
| `postgres:17.11-alpine3.24` base, `linux/amd64` | 1 | 25 | 1 / 25 |
| Single combined `rm && ln` layer variant | 1 | 25 | 1 / 25 |
| This image | 0 | 0 | 0 (no findings at any severity) |

The [1 November record](#dp01-private-health-probe-remediation--1-november-2026)
reports 4 CRITICAL / 94 HIGH for Bookworm from Trivy 0.58.1 on the arm64 image.
That figure could not be reproduced with the scanner and database above; 4/88
is the verified baseline. The 1 November record itself is unchanged.

**OCI layers** (saved `linux/amd64` image, each layer tar read directly):

- Layer 2 (inherited base) still holds `usr/local/bin/gosu`, the original
  1,769,900-byte Go binary.
- Layer 10 holds the `su-exec` package; layer 11 only the whiteout
  `usr/local/bin/.wh.gosu`; layer 12 the `usr/local/bin/gosu` → `/sbin/su-exec`
  symlink; layer 13 the data-volume boundary scripts.

The zero-finding result is therefore Trivy's merged, whiteout-applied view. The
vulnerable bytes are still distributed in the base layer blob, and a scanner
that reads layer blobs individually finds them. They are not reachable through
the container's merged filesystem. This residual risk is **pending explicit
Leader acceptance**; it is not accepted by this record or by ADR003.

**Byte-clean alternative (disposable experiment, not adopted).** A two-stage
build that copies the qualified image's merged filesystem into `FROM scratch`
and restates its configuration (environment without `GOSU_VERSION`, volume,
`SIGINT` stop signal, port, entrypoint and command) produced a one-layer
`linux/amd64` image. No layer blob in it contains the 1,769,900-byte `gosu` or
any fragment of it, compared with layer 2 of the qualified image. Trivy reported
0 findings, the CI data-volume boundary step passed against it (exit 78 and
unchanged volume, fresh initialization, marker, restart), and `gosu postgres id`
resolved to `su-exec` as `uid=70`. Adopting it would discard the base image's
layer identities, so the base digest could no longer be checked layer by layer;
every upstream configuration change would have to be mirrored by hand; and the
release image would need full requalification. It is recorded as evidence for
a Leader decision and is not part of `infrastructure/postgres/Dockerfile`.

**PostgreSQL runtime** (disposable containers and volumes): fresh
initialization, `pg_isready`, SQL write/read, restart with the same volume
(data persisted) and non-root server process all passed.

### Existing data volumes (glibc/musl)

A fresh cluster on this image reports `datcollate` `en_US.utf8`, the same label
as Bookworm, but musl does not sort like glibc. Reproduced on a disposable
volume: a Bookworm cluster (`datcollversion` 2.36) with an index on
`('a','B','b','A','c','C')`, mounted into the image at `b4c36bb`, **started
without any error**; an index scan returned `a A b B c C` while a sequential
scan and sort returned `A B C a b c`, and `bt_index_check` reported
`item order invariant violated`. PostgreSQL only warns per connection that the
collation version is missing.

The image now starts the server only on an empty data directory or on one
carrying `codemastery-cluster-libc` (written by
`/docker-entrypoint-initdb.d/00-codemastery-cluster-libc.sh` after `initdb`).
Otherwise its entrypoint exits 78 with `codemastery-postgres: refusing to
start` before `docker-entrypoint.sh` runs; other commands, such as client
tools, pass through. Verified:

| Case | Result |
| --- | --- |
| Bookworm-initialized volume | Exit 78; content fingerprint of all 1,002 entries unchanged |
| Volume initialized by the image at `b4c36bb` (no marker) | Exit 78 |
| `docker compose up --wait postgres` on a Bookworm-initialized volume (throwaway project) | Container exits 78; `compose down` keeps the volume |
| `psql --version` against the refused volume | Runs |
| Fresh volume | Initialized, marker `musl`, restart keeps data |
| [Documented migration](setup.md#existing-postgresql-volumes) of a Bookworm volume with 600 indexed rows | `pg_dump` / byte copy verified; restore exit 0; md5 of all rows identical; `bt_index_check` clean; index-scan order equals sort order |

The VM is outside this verification. Whether its volume was initialized by
Bookworm was not checked. `deploy.sh` already refuses to change existing
persistent state without operator-attested compatibility and recovery (P9),
and this image refuses a foreign cluster. A VM migration still needs its own
reviewed procedure and authorization.

### Backend integration and the credential-inspection fix

`AbstractIntegrationTest` builds this image through Testcontainers'
`ImageFromDockerfile` under a tag unique to each JVM
(`localhost/codemastery/postgres-it:<UUID>`, removed at the end of the session).
The former shared `codemastery/postgres:latest` tag let parallel checkouts or
JVMs start one another's builds.

`SampleDataSeedIntegrationTest.psqlVariablesKeepCredentialsOutOfProcessArguments`
must find the blocked `psql` and prove no credential is in its argv. Measured on
this host: under Rosetta (`linux/amd64`), `/proc/<pid>/exe` is
`/run/rosetta/rosetta` for every process; across OS users it is unreadable
(no `CAP_SYS_PTRACE`); and `comm` belongs to any executable named `psql` (a
shell script decoy reports `comm` `psql` natively). The heuristic at `b4c36bb`
accepted `exe` `*/psql` or empty after a `comm` match, so it returned `0 0` for
a real clean `psql` under Rosetta, and `1 0` (found, no leak) when a decoy run
by another user stood beside a real leaking client whose exe and comm were not
`psql`.

The client is now identified by correlation: a launcher records its own PID
and `exec`s `psql` in place, the waiting session carries a per-run
`application_name`, and the argv of every process in the container is checked
for registered credentials. Five tests cover the same-user, cross-user and
translated-executable (musl loader) positives, and a synthetic leak and a
cross-user decoy as negatives. The two negatives fail against the old
heuristic (`0 0` and `1 0`); under real Rosetta the new inspection returns
`1 0` for a clean and `1 1` for a leaking client, as root and as `postgres`.

`cd backend && ./mvnw verify`: BUILD SUCCESS, 468 unit and 171 integration
tests, 0 failures, 0 errors; no `postgres-it` image remained afterwards.

### Integration

- **Local Compose:** `infrastructure/docker-compose.yml`'s `postgres` service
  builds `infrastructure/postgres` instead of pulling
  `postgres:17.11-bookworm`.
- **VM release:** `infrastructure/vm/docker-compose.vm.yml`'s `postgres`
  service sets `image: codemastery.invalid/postgres:release` and
  `build: !reset null`; the [release build](#release-build-outside-the-vm)
  builds this image.
- **`deploy.sh`:** unchanged from `main`. `postgres` admits no `build` option,
  so preflight rejects any build configuration on it, including a remote Git
  context, `privileged`, `network: host` and `security.insecure`
  entitlements. Verified on Ubuntu 24.04 `linux/amd64` with the committed
  override: `PREFLIGHT OK`, and all four mutations rejected (`unsupported
  Compose options: build`); at `b4c36bb` all four were accepted.
- **CI:** job **VM deployment script** (preflight fixture resets the PostgreSQL
  build; five rejection cases), job **VM deployment containerd image store**
  (PostgreSQL built from its Dockerfile; MinIO carries the multi-platform
  checks), and job **VM deployment PostgreSQL and containment tests** (new
  volume-boundary step, which fails within 60 seconds against the image at
  `b4c36bb`). Other `postgres:17.11-bookworm` references in CI start disposable
  databases for `deploy.sh`'s SQL and port logic and are unchanged. Trivy
  scanning remains manual.

### Qualification, admission and deployment

- **Functional qualification:** PASS locally, as recorded above. GitHub Actions
  results for this revision are not part of this record.
- **Security scan:** 0 CRITICAL, 0 HIGH in Trivy's merged view; the inherited
  `gosu` layer bytes are a disclosed residual risk pending acceptance.
- **Technical authorization:** the Leader's 2 November 2026 instruction
  (ADR003). **Risk acceptance:** not recorded.
- **Production deployment authorization:** not granted. No image was built,
  loaded or run on the Azure VM; release blockers 2 and 4–7 are unchanged.

### Remaining gates

- **Risk acceptance.** The Leader explicitly accepts or rejects the inherited
  `gosu` layer bytes and the treatment of pre-existing volumes.
- **Byte-clean image.** Adopting the flattened alternative above, or keeping
  the layered image, is a Leader decision.
- **Packaging.** Build `codemastery.invalid/postgres:release` from the merged
  revision and include it in the release archive alongside the other four
  images. Not done here.
- **VM data.** Establish whether the VM volume exists and how it was
  initialized before any release with this image.
- **DP01.** Release blockers 2 and 4–7 are unchanged.

This record and its changes were produced through AI-assisted development
(Anthropic Claude Code) under the Leader's tasks; the audit findings it
remediates came from an independent Codex audit. No AI service is introduced
into CodeMastery's runtime. `su-exec` is a runtime component of the PostgreSQL
image: the entrypoint uses it to drop from root to the `postgres` user before
starting the server. Trivy is a security scanner used manually for
qualification; it is not part of any image or CI job.

## DP01 pre-release security hardening — 2 November 2026

The [3 November record](#dp01-final-security-remediation--3-november-2026)
updates F1, F4, F5, F6, F10 and F11 below.

### Boundary and baseline

Two Project Leader tasks of 2 November 2026 (ICT) on branch
`fix/pre-release-security-hardening` (PR #132): "Comprehensive Pre-Release
Security Remediation PR", and its continuation, which also authorizes a narrowly
scoped, restrictive runtime-hardening change to the Compose policy. They address
the pre-release audit findings F1–F11 reported against `b2984a1`; `origin/main`
was still `b2984a1b15895945d7a8d7821ef13d114cda2a1d` throughout. The tasks
authorize engineering remediation and the pull request. They do not accept a
residual risk, approve a governance or ADR change, admit an image, create a
`codemastery.invalid/*:release` tag, deploy, or close DP01. Earlier records are
unchanged; where this record re-measures or corrects one, it says so.

Status terms: `FIXED` (removed or enforced, verified on the artifact or the
running model); `MITIGATED` (still present; a verified control or verified
absence of the vulnerable code reduces exposure); `NOT_VERIFIED`;
`PENDING_LEADER_APPROVAL` (needs a Leader decision, admission or risk
acceptance); `BLOCKED` (needs an unapproved contract).

### Changes

| File | Change |
| --- | --- |
| `frontend/Dockerfile` | `release` stage: the unbounded `apk upgrade` is replaced by ten exact Alpine 3.24 package versions; `/etc/nginx` becomes root-owned without group/other write. Both `node:22.23.3-bookworm-slim` stages are pinned to the index digest the tag resolved to (`sha256:3ed98443…`) |
| `backend/Dockerfile` | Temurin `25.0.4.1_1` JDK and JRE stages pinned to their index digests (`sha256:a9c5ddd3…`, `sha256:ce8543dd…`); the runtime stage clears the setuid/setgid bits of the base's 12 programs |
| `infrastructure/vm/docker-compose.vm.yml` | All five services: `read_only: true`, `security_opt: ["no-new-privileges:true"]`, `cap_drop: [ALL]`. Writable tmpfs only where needed: `/var/run/postgresql` (PostgreSQL), `/tmp` (initializer, backend, frontend), none for MinIO. PostgreSQL keeps exactly `CHOWN`, `DAC_OVERRIDE`, `FOWNER`, `SETGID`, `SETUID` |
| `infrastructure/vm/deploy.sh` | Compose policy: those values are required exactly per service. Any other `security_opt`, `cap_add`, `cap_drop`, `read_only` or `tmpfs` value, a missing value, and every previously rejected option (`privileged`, host namespaces, devices, `user`, extra services, published ports, Console on) are rejected |
| `infrastructure/minio/test-minio-init.sh` | The release-model checks also require the initializer's and server's hardening in the effective model and on the running containers |
| `.github/workflows/ci.yml` | Nginx ownership and setuid/setgid image checks; 16 offline hardening policy cases; a hardened preflight fixture with 7 preflight rejection cases; hardening assertions on the committed override |
| `docs/deployment.md` | This record; the release recipe now builds candidates and promotes admitted images only (F3) |

Unchanged: both MinIO images and `minio-init.sh`, the PostgreSQL image, the base
Compose file and local development, the release image names, published ports,
environment contracts, `--expect-image` and the archive model.

### Scanners and modes

Trivy 0.76.1, database updated 2026-11-01 19:08 UTC; each image exported with
`docker save --platform linux/amd64` and scanned with `trivy image --input
<archive> --scanners vuln`, by default (`--detection-priority precise`) and with
`--detection-priority comprehensive`. govulncheck v1.9.0 (Go 1.27.3),
`-mode=binary`, database `https://vuln.go.dev` modified 2026-10-27T21:16:18Z.
cosign v3.1.4. Nothing was ignored or suppressed. A lower count is not by itself
evidence; each row names the artifact property checked.

### F1 — MinIO initializer `mc`

Artifact: the pinned base `cgr.dev/chainguard/minio-client@sha256:3b67177c…`
(linux/amd64), `/usr/bin/mc` SHA-256 `8842390a…bc34a`, go1.27.3,
`vcs.modified=true`.

| Advisory | Module, found → fixed | Trivy default | Trivy comprehensive | govulncheck | Package in the binary |
| --- | --- | --- | --- | --- | --- |
| CVE-2026-63209 (GO-2026-5841) | `klauspost/compress` v1.18.5 → v1.18.7 | — | HIGH | `s2` imported; no vulnerable symbol called | `s2` present |
| CVE-2026-78661 (GO-2026-6860) | `x/net` v0.60.0 → v0.62.0 | — | UNKNOWN | Module only | `x/net/html` absent |
| CVE-2026-97045 (GO-2026-6866) | `x/net` v0.60.0 → v0.62.0 | — | UNKNOWN | Module only | `x/net/html` absent |
| GO-2026-5932 | `x/crypto` v0.57.0, no fix | — | UNKNOWN | Module only | `openpgp` absent |
| CVE-2026-73500 (GO-2026-6107) | `etcd/client/pkg/v3` v3.5.19 → v3.5.33 | — | — | Module only | — |

Trivy's default mode attributes the binary to the Wolfi `mc` package, which has
no advisory; comprehensive mode reads the Go build information. "Package in the
binary" is a search of the function-name table, calibrated under F5.

- **Option A — newer trusted upstream image: not available.** Rechecked on
  2–3 November: `:latest` still resolves to the pinned index, `:latest-dev`
  (`sha256:5a80cba5…`) carries the byte-identical `mc`, the newest `mc` in both the
  Wolfi and Chainguard x86_64 indexes is the pinned `0.20250813.083541-r27`, and
  Wolfi's `mc.yaml` was last changed on 21 October (toolchain rebuild). Its
  `bump` list still pins `klauspost/compress` v1.18.5 and `x/net` v0.60.0.
  `minio/mc` is archived. The pin's signature verifies.
- **Option B — reproducible project-built `mc`: candidate built and qualified as
  evidence, not adopted.** Disposable Dockerfile, outside the repository:
  - Source `minio/mc` tag `RELEASE.2025-08-13T08-35-41Z`, commit `7394ce0` checked
    against the expected hash; the same commit and `-X …cmd.Version` ldflag as
    Wolfi's recipe.
  - Wolfi's `bump` list with the fixed versions: `klauspost/compress` v1.18.7,
    `x/net` v0.62.0, `x/crypto` v0.58.0, `x/sys` v0.49.0, `x/term` v0.47.0,
    `x/text` v0.43.0, `grpc` v1.83.2, `prometheus` v0.311.3, `client_golang`
    v1.23.2, plus `etcd/client/pkg/v3` v3.5.33; `go mod tidy` and `go mod verify`
    against `sum.golang.org`.
  - Toolchain `golang:1.27.3-bookworm@sha256:d9b6a1be…` (unsigned BuildKit
    provenance naming `docker-library/golang@41d9b4d`); `CGO_ENABLED=0
    -trimpath -buildvcs=false -ldflags "-buildid= …"`, cross-compiled for
    linux/amd64; symbols kept so govulncheck can work at symbol level.
  - Packaging: the pinned base's filesystem without its `mc` and `mc` package
    records, copied into `FROM scratch` with the new binary and the unchanged
    script (3 layers). No layer contains any byte sequence of the old `mc`. Apart
    from `usr/bin/mc`, the `mc` SBOM file and the apk database entry, every path,
    owner and mode equals the base. Configuration, UID/GID 65532 and entrypoint
    are unchanged.
  - Result: `mc` SHA-256 `fe164f1ac4a044c196d0c66d2ce91e2c6963ece9935e8c2e970f3e9840a69e9c`,
    identical in two native cross-builds (one `--no-cache`) and an independent
    build on an emulated amd64 builder. The image itself is not reproducible
    (image IDs `sha256:87cd4082…` and `sha256:e8097899…`). govulncheck: only
    GO-2026-5932, module level. Trivy: GO-2026-5932 only (UNKNOWN), now also in
    default mode because no package record hides the binary.
  - Functional: a test-only copy of `test-minio-init.sh`, changed only in its base
    layer, `mc` SHA-256 and package-record assertions, passed 26/26 against the
    Golithus, Chainguard and minimal servers.

  It is not adopted because it would replace Chainguard's signed binary with a
  project-built, unsigned one whose provenance rests on this recipe; it is a
  modified AGPL-3.0-or-later build, so giving the image to anyone requires the
  corresponding source (`7394ce0` plus the resulting `go.mod`/`go.sum`) and the
  pending legal judgment; and the committed suite's identity model (pinned base
  layers plus one script layer) would have to be replaced.
- **Option C — keep the signed image: current state.**

**Status: `PENDING_LEADER_APPROVAL`.** No vulnerable symbol is called and three
of the four affected packages are not linked.

### F2 — security records

This record replaces the first-round grouping with the audit's F1–F11 numbering,
records both rounds' evidence, and keeps the earlier dated records unchanged.
Verification is in [testing.md](testing.md#dp01-pre-release-security-hardening-verification--2-november-2026).
**Status: `FIXED` for the record gaps found; it accepts nothing.**

### F3 — release tagging and admission workflow

The [release recipe](#release-build-outside-the-vm) built images directly under
their `codemastery.invalid/*:release` names. It now builds
`codemastery-candidate/*` images, requires qualification and a recorded
admission of the exact image ID, and only then promotes by `docker tag`. Four
stages:

1. **Candidate:** built from a reviewed revision under a non-release tag.
2. **Qualification:** functional suites and scans of that exact artifact, with
   scanner versions and database dates.
3. **Security admission:** the Leader admits or rejects each image ID and decides
   each `PENDING_LEADER_APPROVAL` item; signatures and scans are repeated then.
4. **Release promotion:** only admitted images are tagged, saved into the archive
   and bound by `--expect-image`.

`codemastery.invalid/*:release` tags that already exist on a build host were
created outside this process and are not admitted by it; this work did not
touch them. **Status: `FIXED` (procedure); no artifact has passed stage 3.**

### F4 — PostgreSQL inherited `gosu` layer

Committed image (unchanged): 0 findings in both Trivy modes for the merged
filesystem; layer 2 of 14 still holds the 1,769,900-byte `gosu` (SHA-256
`52c8749d…`, go1.24.6; 59 Go advisories including GO-2026-4337/CVE-2025-68121, one
at symbol level, GO-2026-6686), hidden by the layer-11 whiteout and replaced by
the layer-12 `su-exec` symlink. It is unreachable at runtime.

**Byte-clean experiment (not adopted).** The committed Dockerfile's stages,
followed by `FROM scratch`, `COPY --from=layered / /`, and the same labels,
environment (without the build-time `GOSU_VERSION` and `DOCKER_PG_LLVM_DEPS`),
volume, `SIGINT`, port, entrypoint and command:

| Check | Result |
| --- | --- |
| Layers | 1 instead of 14; no byte sequence of the `gosu` binary in it |
| Merged filesystem | Identical to the committed image: 3,896 entries, same content, owners and modes |
| Size | 297.9 MB uncompressed, 2.2 MB less than the layered image |
| Trivy | 0 in both modes |
| Volume boundary | CI step passes: Bookworm volume exit 78 and unchanged, client tools run, fresh volume marked `musl` and restarted with data |
| Testcontainers | `./mvnw verify` with it as `infrastructure/postgres/Dockerfile`: 468 unit and 171 integration tests passed (Flyway V001–V006, privilege matrix, seed data) |
| Release path | `deploy.sh` provision and deploy on the hardened five-image stack: roles, privilege matrix, Flyway V001–V006, role separation, healthy; marker `musl`; server runs as `postgres` |

Trade-offs: the base image's layer identities are no longer visible, so the
base digest cannot be checked layer by layer; every upstream configuration change
must be restated by hand; and a new image identity needs its own qualification
and admission. **Recommended (not approved):** a Leader amendment to ADR003
adopting the flattened build, with a CI check that the image configuration equals
the layered build's except the two build-time variables. Until then, **status
`PENDING_LEADER_APPROVAL`**; ADR003 is unchanged.

### F5 — MinIO server

| Item | Evidence | Status |
| --- | --- | --- |
| Newer build | `cgr.dev/chainguard/minio:latest` (`sha256:0a011000…`) has the byte-identical binary (`3c3d6152…`) and package `minio 0.20261023.200651-r2`; only `ncurses` differs. The fork's `master` (`a2c3f7c`) adds only documentation/debugging-tool dependency updates | No newer server; pin unchanged |
| GO-2026-5932 | govulncheck lists `x/crypto/openpgp/*` wildcards. **Correction:** the binary is stripped, and govulncheck v1.9.0 then reports every package of the vulnerable module. A stripped control binary importing only `x/crypto/blake2b` gave the same `openpgp/armor/*` result; unstripped it gave none. The server's function-name table contains other `x/crypto` packages (`argon2`, `blake2b`, `chacha20`, `pbkdf2`, `ssh` and more) and no `openpgp`; source mode at `76c06ff` with the binary's 21 newer module versions imports none. Trivy reports only this advisory for the minimal image (UNKNOWN) | `MITIGATED` (not linked; module finding has no fix). `PENDING_LEADER_APPROVAL` |
| CVE-2026-113466 | No patched version. The Console stays off and enforced; the release server now also runs read-only without capabilities | `MITIGATED`, still in the binary. `PENDING_LEADER_APPROVAL` |
| Other CRITICAL/HIGH | Minimal image: none in either mode. The full Chainguard server image also ships `mc` with the F1 findings; the minimal image does not | — |

### F6 — provenance, signing and determinism

| Item | Result | Status |
| --- | --- | --- |
| Mutable base tags | Backend and Node stages pinned to the digests their tags resolved to; content unchanged | `FIXED` |
| Unbounded `apk upgrade` | Exact versions: `alpine-release` 3.24.3-r0, `brotli-libs` 1.2.1-r0, `c-ares` 1.34.9-r0, `ca-certificates`/`-bundle` 20261020-r0, `curl`/`libcurl` 8.23.0-r0, `musl`/`musl-utils` 1.2.6-r3, `zlib` 1.3.2-r1. Same installed set as the `main` recipe on the same day. The build fails once Alpine drops a pinned version | `FIXED` |
| Base provenance | Temurin, Node, Nginx and Go indexes carry unsigned BuildKit SLSA provenance (`adoptium/containers@511f935`, `nodejs/docker-node@81f4191`, `nginx/docker-nginx-unprivileged@bb73931`, `docker-library/golang@41d9b4d`); not verified as build provenance. The Chainguard signatures verify | Content-equivalent pins, not independently verified provenance |
| Project images | Unsigned; not bit-for-bit reproducible (PostgreSQL record; Option B image IDs). Identity comes from the reviewed archive and `--expect-image`. No signing or attestation workflow added: it needs signing credentials and a trust policy that no approved contract defines | `PENDING_LEADER_APPROVAL` |

### F7 — backend runtime

- **Image:** the 12 setuid/setgid programs (`su`, `mount`, `umount`, `passwd`,
  `chfn`, `chsh`, `gpasswd`, `newgrp`, `chage`, `expiry`, `unix_chkpwd`,
  `pam_extrausers_chkpwd`) lose those bits; a CI check fails on a `main` backend
  image (12 files) and passes on the candidate. `FIXED`.
- **Runtime:** read-only root, `/tmp` tmpfs, no capabilities (`CapBnd` 0),
  `NoNewPrivs` 1, non-root `codemastery`. Startup, health, Flyway, Auth and CORS
  pass on the hardened stack. `FIXED` in the release model.

### F8 — frontend runtime

- **Image:** `/etc/nginx` root-owned and not writable by UID 101; `/var/cache/nginx`
  stays writable. CI check fails on the `main` image and passes on the candidate.
  `FIXED`.
- **Runtime:** read-only root, `/tmp` tmpfs (Nginx pid and temp paths; logs go to
  stdout/stderr), no capabilities, `NoNewPrivs` 1, UID 101. The staged
  `compose run`, `nginx -t`, the private backend probe, the HTTPS healthcheck and
  public ingress pass. `FIXED` in the release model.

The same policy covers PostgreSQL (read-only, `/var/run/postgresql` tmpfs, the
five capabilities in its bounding set, `CapEff` 0 for the server) and MinIO
(read-only, no tmpfs, no capabilities, UID 65532). Measured during development:
PostgreSQL fails with `--cap-drop ALL` (`chmod: /var/lib/postgresql/data:
Operation not permitted`) and without `DAC_OVERRIDE`. Docker's default tmpfs
mount is `rw,nosuid,nodev,noexec`, mode 1777; no size limit is set, because none
is approved. The tmpfs is RAM-backed, so whatever a service writes there (for
example Tomcat's temporary files) now uses memory rather than disk.

### F9 — MinIO initializer filesystem

The initializer base has `/var/tmp` and `/data` mode 0777 without the sticky bit,
`/tmp` 1777 and `/home/nonroot` owned by 65532. The script writes only a
`mktemp -d` directory and mounts no storage; the server's `/data/.minio.sys`
lives on the server's own volume. With the release model's read-only root and
`/tmp` tmpfs, only `/tmp` is writable at runtime (`/var/tmp`, `/data`,
`/home/nonroot` and `/usr/local/bin` are denied), and the suite verifies the
running container's restrictions. The image itself keeps the base's modes:
changing them would add a layer and the suite's identity model, and the
read-only root already removes the exposure. **Status: `FIXED` in the release
model; image modes unchanged.**

### F10 — secrets and credential controls

Re-inspected: `JWT_SECRET` must be exactly 64 hexadecimal characters; `<SET_ME…>`
placeholders are rejected; bootstrap, migrator and runtime identities and
passwords must differ; the env file must be owned by the operator, mode 0600,
single link, in a 0700 directory with safe ancestors; `deploy.sh` passes PostgreSQL
passwords on stdin and prints none; the release initializer keeps all secrets out
of argv and child environments and requires an application secret of at least
8 bytes that differs from the root identity. One local-only gap: the base
Compose file's inline development initializer passes `MINIO_ROOT_PASSWORD` to
`mc alias set` as an argument; the VM override replaces it, so the release is
not affected, and the local file is outside this change. No other length or
strength rule is authorized, so none was added. **Status: no release defect
found; a password-strength policy remains a Leader decision.**

### F11 — backend Ubuntu packages

Candidate backend: 0 CRITICAL/HIGH; 24 MEDIUM and 4 LOW Ubuntu 24.04 findings
(`libpcre2-8-0`, `libgnutls30t64`, `libc6`/`libc-bin`/`locales`, `tar`, `dash`,
`libexpat1`), none with a fixed version; no findings in the application's Java
libraries. `eclipse-temurin:25-jre-noble` (JDK 25.0.5+8, 29 October) has exactly
the same 28 findings, so it brings no OS fix; Trivy does not assess the Temurin
runtime itself. Moving from 25.0.4.1+1 is a runtime upgrade that
[CT17 P5](#p5--runtime-envelope-locations-and-fail-fast-inputs) excludes ("No
new service, technology or runtime upgrade"). **Status: `MITIGATED` (no fixable
package); the JDK upgrade is `PENDING_LEADER_APPROVAL` and its JDK-level effect
`NOT_VERIFIED`.**

### Outstanding Leader decisions

- F1: keep the signed image and accept the `mc` findings until an upstream
  rebuild exists, or authorize the Option B build (provenance, AGPL source
  provision, new qualification model).
- F4: amend ADR003 to adopt the flattened image, or accept the inherited
  `gosu` layer bytes.
- F5: accept GO-2026-5932 and CVE-2026-113466 with the controls above.
- F6: signing or attestation of project images, with its credentials and trust
  policy.
- F10: whether a credential strength policy is wanted.
- F11: the JDK 25.0.5 runtime upgrade.
- Security admission of every release image; the pending
  [Console hardening](#dp01-minio-console-hardening--2-november-2026) and
  [PostgreSQL hardening](#dp01-postgresql-alpinesu-exec-image-hardening--2-november-2026)
  dispositions; release blockers 1–7 in
  [Verification and remaining gates](#verification-and-remaining-gates).

Nothing was built, loaded or run on the Azure VM, and no release tag was
created or changed.

This record and its changes were produced through AI-assisted development
(Anthropic Claude Code) under the Leader's tasks. Trivy, govulncheck, cosign and
their databases, the Sigstore public-good transparency log, the Go module proxy
and checksum database, and the Wolfi and Chainguard package indexes were used
only for this review. No AI service or new infrastructure technology is
introduced into CodeMastery's runtime.

## DP01 final security remediation — 3 November 2026

### Authority and boundary

The Project Leader's task of 3 November 2026 (ICT), "PR #132 Final Security
Remediation and Delegated Technical Authorization", continues
`fix/pre-release-security-hardening` (PR #132). It delegates the choice and
implementation of the remaining remediations within stated conditions: a
qualified, reproducibly built `mc` (F1), a qualified byte-clean PostgreSQL image
with a narrowly scoped ADR003 amendment (F4), provenance and reproducibility
improvements (F6), confirmed secret-exposure fixes (F10) and a Java 25 patch
upgrade with a verified security benefit (F11). It does not accept a residual
risk, give legal clearance, admit an image, authorize deployment, merging or a
release, or touch the Azure VM. This record updates the
[2 November record](#dp01-pre-release-security-hardening--2-november-2026),
which stays as written; where they differ, this one describes the current
branch. `origin/main` was still `b2984a1`.

A second task the same day ("Continue the final security remediation of
CodeMastery PR #132") asked for the test host's Docker socket to be restored,
the open F11 CVEs to be verified on the image, the `mc` licensing to be
re-reviewed, residual F5/F6/tmpfs/PostgreSQL-capability exposure to be
re-examined, and the exact five-image set to be requalified. Its results are
included below; where they correct the first version of this record, the
section says so.

### Changes

| File | Change |
| --- | --- |
| `infrastructure/minio/Dockerfile` | Project-built `mc` (F1): source `minio/mc` `7394ce0`, fixed dependency versions, Go `golang:1.27.3-bookworm@sha256:d9b6a1be…`; the binary is built offline from its own Corresponding Source archive; the pinned Chainguard filesystem without its `mc` is copied into one layer; licence and notice texts in `/usr/share/doc/mc`; the archive also holds this Dockerfile, and `/usr/share/doc/mc/SOURCE` names the archive's SHA-256 |
| `infrastructure/minio/test-minio-init.sh` | New artifact identity model for that image (below); checks the Corresponding Source archive of a suite-built image; records every argument the local inline initializer passes to `mc` and fails on a secret |
| `infrastructure/postgres/Dockerfile` | The approved changes become the `layered` stage; the final stage ships its merged filesystem as one layer (F4) |
| `infrastructure/docker-compose.yml` | Local inline initializer: `MINIO_ROOT_PASSWORD` and `STORAGE_SECRET_KEY` reach `mc` on stdin instead of as arguments; values with control characters are refused (F10) |
| `backend/Dockerfile` | Temurin 25.0.5+8 JDK and JRE, pinned (`sha256:95e350ad…`, `sha256:a98128a5…`); the runtime's Java trust store loses "emSign Root CA - G1", and the build fails if any root OpenJDK lists for the eMudhra compromise remains (F11) |
| `frontend/Dockerfile` | `libexpat` 2.10.0-r0 added to the exact Alpine set: today's database reports two HIGH and two other `libexpat` 2.9.0 findings for the 2 November candidate (F6/F8) |
| `.github/workflows/ci.yml` | New step: the PostgreSQL image's configuration and merged filesystem equal its layered build, it has one layer and no layer holds `gosu` |
| `docs/decisions/003-postgresql-alpine-su-exec-image.md` | Append-only amendment recording the F4 decision |
| `docs/setup.md`, `docs/deployment.md`, `docs/testing.md` | Temurin version; release recipe exports the `mc` source; this record; verification |

Unchanged: `minio-init.sh`, both MinIO server images, the VM override, `deploy.sh`,
release image names, ports, environment contracts, `--expect-image` and the
archive model.

### Scanners

Trivy 0.76.1, database updated 2026-11-03 01:30 UTC, `docker save --platform
linux/amd64` archives, `--scanners vuln`, default and `--detection-priority
comprehensive`; govulncheck v1.9.0 `-mode=binary`, database modified
2026-11-02T22:01:43Z; cosign v3.1.4. Nothing ignored or suppressed. Trivy counts
below are per finding row; where a scanner reports one advisory twice (binary and
package record), it is counted once.

### F1 (3 November) — project-built `mc` adopted

| Advisory | Chainguard `mc` (`8842390a…`) | Project-built `mc` (`025691d4…`) |
| --- | --- | --- |
| CVE-2026-63209 / GO-2026-5841, `klauspost/compress` | v1.18.5; HIGH (comprehensive); `s2` imported | v1.18.7: absent |
| CVE-2026-78661 / GO-2026-6860, `x/net` | v0.60.0; module level | v0.62.0: absent |
| CVE-2026-97045 / GO-2026-6866, `x/net` | v0.60.0; module level | v0.62.0: absent |
| GO-2026-5932, `x/crypto/openpgp` | v0.57.0; module level, `openpgp` not linked | `v0.58.1-0.20261009181718-2f1f834330b2`: absent |
| CVE-2026-73500 / GO-2026-6107, `etcd/client/pkg/v3` | v3.5.19; module level | v3.5.33: absent |
| Trivy (default / comprehensive) | 0 / 4 distinct (1 HIGH, 3 UNKNOWN) | 0 / 0 |
| govulncheck | 5 advisories | none |

- **Option A, a newer trusted upstream build: still none.** On 3 November
  `cgr.dev/chainguard/minio-client:latest` moved to `sha256:2fc44123…`; it holds the
  byte-identical `mc` (`8842390a…`) and differs from the pin only by epoch
  rebuilds of `libacl1`, `libattr1`, `libbrotli*` and `libpcre2-8-0`. The pin is
  kept; its keyless signature still verifies against Chainguard's release
  workflow identity.
- **Option B, adopted.** The 2 November candidate, rebuilt into the committed
  Dockerfile with these changes:
  - The commit is fetched by its full hash. `go get` applies Wolfi's raises with
    the fixed versions, `go mod tidy` runs, and the resulting `go.mod` and `go.sum`
    must have the reviewed SHA-256 values (`091e2cfc…`, `363a7760…`). New `go.sum`
    lines are checked against `sum.golang.org`.
  - `x/crypto` is the vulnerability database's fixed version for GO-2026-5932, a
    pseudo-version one upstream commit (`2f1f834`, "openpgp: carve out openpgp as
    a module") after the `v0.58.0` tag. Its module content equals `v0.58.0` except
    that the `openpgp` directory is gone; `mc` used none of it.
  - The `mc-source` stage writes `mc-source.tar.gz`: the `mc` tree at `7394ce0`
    with the changed `go.mod`/`go.sum`, `dependency-changes.diff`, a README with
    the modification notice and offline build command, and the 100 module zips the
    linux build loads, as served by `proxy.golang.org`, in a file-proxy layout
    that `go.sum` verifies. The `mc-build` stage runs without network access
    (`RUN --network=none`) and builds only from that archive.
  - Runtime: the pinned base's merged filesystem without `/usr/bin/mc`, its
    SBOM file and its apk record (layer 1), the new `mc` (layer 2), the notices
    (layer 3) and the unchanged script (layer 4). Same user `65532:65532`,
    entrypoint, empty command, `PATH` and `SSL_CERT_FILE`.
- **Reproducibility.** `mc` SHA-256
  `025691d43adb653c6ce34723c76450153f2c024bfa232edaca6906fe3e221809` and the source
  archive SHA-256 `ed6f972e23de4074221d1e71436ea32932d9c83eb24c728417bf4b98ddb98d11`
  (first version; the second pass added the Dockerfile and a fuller README, giving
  `a42400d4703c265258952b44bfd997a53dcca55bf47b691f48a1f2f483e98b50` with the same
  `mc`) came out identical in a cross-compiling BuildKit build on an arm64 builder and
  in an offline rebuild from the exported archive with its README command in an
  emulated `linux/amd64` Go container; the archive was also identical across two
  separate BuildKit builds. The suite repeats the comparison wherever it builds
  the image, including CI's amd64 runner. The image itself is
  not reproducible: layer timestamps and BuildKit attestations change its ID on
  every build, so it is identified by content (below), not by image ID.
- **Identity model in `test-minio-init.sh`.** Replaces "pinned base layers plus
  one script layer": every layer is re-hashed and read; there must be exactly four
  and no whiteouts; no file in any layer may be the replaced `mc`; layer 1 must
  equal the pin's filesystem entry by entry (type, owner, mode, link target,
  content), checked against a recorded digest of that filesystem, except for the
  three removed or rewritten paths, whose new apk database content is recomputed;
  layers 2–4 may hold only `mc`, the seven notice files and the script, each with
  its recorded SHA-256 and mode. A suite-built image's source archive must have
  the recorded SHA-256. Rejected during development: the 2 November image
  (12 layers), the Option B experiment (3 layers), and three mutated builds
  (base `mc` copied back, `/etc` mode changed, script mode changed).
- **Function.** `test-minio-init.sh` against the Golithus, Chainguard and minimal
  servers: 27/27 each (one check more than before: the source archive).
  Bucket provisioning, the exact bucket-scoped policy, the bound identity, refusal of foreign or conflicting identity and policy state,
  idempotent repeat runs, fail-closed inputs and readiness, exit codes and
  propagation to the dependent service, and the release Compose model are the
  existing checks, unchanged.

**Status: `FIXED`** for the five advisories: the shipped binary no longer
contains the vulnerable module versions, and both scanners report nothing for the
image. Licensing below; admission pending.

### MinIO Client licence and Corresponding Source

Facts: `mc` is distributed under the GNU Affero General Public License version 3;
its source files say "either version 3 of the License, or (at your option) any
later version". The project-built binary is a modified version (dependency
versions changed). AGPL section 6 requires that conveying the object code is
accompanied by, or offers access to, its Corresponding Source, which covers the
build scripts; section 5(a) requires a prominent, dated modification notice;
section 13 concerns users interacting with a modified program over a network, and
`mc` here is a one-shot client with no network-facing interface of its own.

What this branch provides:

- The Corresponding Source archive, produced by the same Dockerfile and revision
  that build the image, and the binary's only source input. It holds the `mc`
  tree with its changed `go.mod`/`go.sum`, `dependency-changes.diff`, the 100
  module zips the linux build loads (each module's own licence and notice files
  inside), the README with the dated modification notice and the offline build
  command (Go image pinned by digest), and, since the second pass, the
  Dockerfile itself, which fetches, builds and installs the binary.
- In the image, `/usr/share/doc/mc`: `mc`'s `LICENSE` (the AGPL text), `NOTICE`
  and `CREDITS`; the archive's `README`; `BUILDINFO`, the binary's embedded
  module list with checksums; `THIRD_PARTY_NOTICES`, the Go standard library's
  licence and every linked module's licence, notice and patent files
  (`minio/colorjson` and `minio/filepath` have none and carry Go's BSD licence
  in their file headers); and, since the second pass, `SOURCE`, which names the
  exact archive (SHA-256) the binary was built from and how to regenerate it from
  the public repository. The image label `org.opencontainers.image.licenses` is
  `AGPL-3.0-or-later`.

Second-pass review, gaps found and fixed: the image did not say where its
Corresponding Source is (now `SOURCE`, with the archive hash); the archive did not
contain the script that installs the binary into the image (now the Dockerfile);
the README named the Go image by tag only (now by digest); and the notice
collection had swept in five non-notice files (`license.go`, `license_gen.go`,
`license_gen_test.go`, `licenseheaders.py`, `LICENSE.minisig`), which the
filename filter now excludes. Re-verified: the exported archive equals the one
recorded in the suite, the binary built offline from it with the README's
command is `025691d4…`, and the licences of the linked modules are MIT (51),
Apache-2.0 (35), BSD (18), MPL-2.0 (2), the Unlicense (1), LGPL-3.0 with a
static-linking exception (`juju/ratelimit`) and AGPL-3.0 (`minio/madmin-go`,
`minio/pkg`), with no licence found that forbids combination with AGPL-3.0.

Source-provision procedure: when the initializer image, or a release archive
containing it, is given to anyone outside the build host, export
`mc-source.tar.gz` from the same revision with the
[release recipe](#release-build-outside-the-vm), check that its SHA-256 equals
both `INIT_MC_SOURCE_SHA256` and the value in the image's `SOURCE`, and provide it
alongside the image, or from the same place at no further charge, for as long as
the image is distributed. Rebuilding the archive later depends on GitHub and
`proxy.golang.org`, so the exported copy is the one to keep. The licence defines
conveying as propagation that lets other parties make or receive copies; running
the image on the project's own VM without handing over a copy is not that.

Not established: this is an engineering record, not legal advice or clearance.
The Go toolchain and standard library are not in the archive; they are pinned by
digest and publicly available, and the licence's System Libraries and
general-purpose-tool exclusions may cover them, but that reading is not
confirmed. Whether a given release or course submission conveys the image, and
whether the procedure satisfies every obligation, need a legal or Leader
determination.

### F4 (3 November) — byte-clean PostgreSQL adopted

Recorded in the [ADR003 amendment](decisions/003-postgresql-alpine-su-exec-image.md#amendment--3-november-2026-byte-clean-image).

| Check | Result |
| --- | --- |
| Layers | 1 (was 14); no whiteout; no file equals the upstream `gosu` (`52c8749d…`) or contains its module path `github.com/tianon/gosu` |
| Merged filesystem | Equal to the `layered` stage: 3,887 entries with content, owner, mode, link target and extended attributes; BuildKit (linux/amd64 and arm64) and the classic builder Testcontainers uses |
| Configuration | Equal to the `layered` stage except the dropped `GOSU_VERSION` and `DOCKER_PG_LLVM_DEPS` |
| Trivy | 0 in both modes (layered image: 0 in the merged view; its layer 2 still held `gosu`) |
| Volume boundary | CI step: a Bookworm volume exits 78 and is unchanged; client tools run; a fresh volume is marked `musl`, restarted and keeps its data |
| Testcontainers | `./mvnw verify`: 468 unit and 171 integration tests (Flyway V001–V006, privilege matrix, seed data) |
| Release path | Hardened five-image `deploy.sh` provision and deploy (archive, image-identity, port and host-TLS checks stubbed): role provisioning, privilege matrix, Flyway V001–V006, role separation; marker `musl`; server runs as `postgres`, `CapBnd` `0xcb`, `CapEff` 0; data kept across a full restart |

The new CI step "Check the PostgreSQL image is byte-clean and equivalent to its
layered build" fails on any drift. **Status: `FIXED`** for the inherited `gosu`
bytes. The glibc/musl collation risk, volume migration and admission are
unchanged.

### F5 (3 November) — MinIO server

Rechecked: `cgr.dev/chainguard/minio:latest` (`sha256:090010a3…`) holds the same
binary (`3c3d6152…`). The fork's `master` gained only `x/net` v0.62.0 and
`go-jose` v4.1.7 bumps and CI changes after `a2c3f7c`, with no release tag or
Chainguard build. The database now names a fixed `x/crypto` version for
GO-2026-5932, but only a rebuild of the server could use it; a server built
from the fork's unreleased `master` would replace the reviewed Chainguard
artifact with unreviewed source, so none was made. `openpgp` is still not linked.
CVE-2026-113466 has no fix; `MINIO_BROWSER=off` and the read-only,
capability-free runtime stay. Rechecked again in the second pass: no new
Chainguard digest, fork commit or release; the rebuilt minimal server still
carries `3c3d6152…` and Trivy still reports only GO-2026-5932 (UNKNOWN). govulncheck
reports it at symbol level for this stripped binary, which the 2 November control
binary showed to be an artifact. **Status: `MITIGATED`, not fixed; risk
acceptance pending.**

### F6 (3 November) — provenance, reproducibility, signing

| Item | Result | Status |
| --- | --- | --- |
| Base pins | Temurin 25.0.5+8 pinned; all other pins unchanged. BuildKit provenance names `adoptium/containers@86e89ce`, `docker-library/golang@41d9b4d`, `alpinelinux/docker-alpine@5aec467`; unsigned. Chainguard pins verify with cosign | Pinned; upstream provenance not independently verified |
| Reproducibility | `mc` and its source archive: bit-identical across builds. PostgreSQL: content-equal to its layered build, image ID not reproducible. Others unchanged | Binary reproducible; images not |
| Dependency inventory | `mc`: embedded module list with `h1:` checksums (`BUILDINFO`), notices and the source archive. Trivy SPDX documents of all five candidates were generated as review evidence, not committed | Provided for `mc`; no SBOM pipeline |
| Signing | Not added; see the proposal below | `PENDING_LEADER_APPROVAL` |

No signing or attestation was added and no trust policy is set. A design
needs Leader decisions on: what is signed (each image or the release archive),
where it is signed (a release workflow and its tag protection), which identity
and key model is trusted (keyless with a transparency log, as Chainguard uses
for the pins verified above, or managed keys), and whether `deploy.sh` must
refuse unsigned input. Second-pass recheck: no base pin has a newer build
except the build-only Node stages (`node:22` now resolves to 22.23.4); the Node
toolchain does not ship in the runtime image and was not changed.

### F10 (3 November) — local initializer secrets

**Later correction:** the independent review found an incomplete enforcement
claim here; see [the remediation record](#dp01-independent-review-remediation--3-november-2026).

Confirmed: the base Compose file's inline initializer ran `mc alias set … USER
PASSWORD` and `mc admin user add … KEY SECRET`, so the MinIO root password and the
application secret were in process argument lists on the Docker host for the
run. Now both reach `mc` on stdin, one line each; `mc` reads exactly one line
without trimming, so values with control characters (which could not be passed
intact) are refused before any change, as the release initializer already does.
The suite wraps the local `mc` and checks that neither secret appears in any
argument list while the initialization still succeeds and the release image
accepts its state. Re-checked without change: no secret in Docker build
arguments, tracked files, CI logs, test output, commit messages or the pull
request. **Status: `FIXED` (local development only; the release path was not
affected).**

### F11 (3 November) — Java 25.0.5 and the trust store

**Later correction:** the independent review found an incomplete enforcement
claim here; see [the remediation record](#dp01-independent-review-remediation--3-november-2026).

The OpenJDK Vulnerability Advisory of 2026/10/20 lists 48 CVEs fixed in the
October release, affecting "25.0.4.1 … and earlier"; 39 of them are marked for
JDK 25. Among them: `java.net` (9), `java.security`/`javax.security` (7),
`javax.net.ssl` (2), `xml/jaxp` (3), the highest 7.0 (`client-libs/2d`).
Temurin 25.0.5+8 (built from `adoptium/jdk25u` `7ec19fb`, the merge of
`jdk-25.0.5+8`) carries them. The backend Ubuntu packages are unchanged: 24
MEDIUM and 4 LOW, none fixable, the same as 25.0.4.1+1. The runtime contract
(Java 25 LTS, Spring Boot 4.1.1) is unchanged; the backend suite, the image
startup and the release path pass (below).

**Correction (second pass).** The first version of this record said that
CVE-2026-91403 and CVE-2026-114820 "remain until a Temurin release includes
them". That was not verified and was inaccurate as stated. Both advisories
(2026/10/21 and 2026/10/24, CVSS 9.1, `security-libs/java.security`) refer to
OpenJDK's guidance on the compromised private keys of eight eMudhra intermediate
CA certificates (openjdk.org/emudhra, JDK-8395690): the fix removes four roots
from the runtime's trust store, "emSign ECC Root CA - G3" and "AAA Certificate
Services", to which the compromised intermediates chain, and "emSign Root CA -
G1" and "G2". The exposure is trust-store content, and the version ranges
describe OpenJDK's own `cacerts`. Temurin generates its trust store from
Mozilla's CA list (`adoptium/temurin-build`, `security/mk-cacerts.sh`), so the
store itself was checked with OpenJDK's `CheckCerts.java`, compiled with the
pinned JDK and run on each image:

| Runtime | Roots `CheckCerts` reports |
| --- | --- |
| Temurin 25.0.4.1+1 (2 November backend) | emSign ECC Root CA - G3, emSign Root CA - G1 |
| Temurin 25.0.5+8 as published | emSign Root CA - G1 |
| Backend candidate (this branch) | none: "No bad root certificates found" |

The upgrade removed the root of the compromised chains; the backend Dockerfile
now deletes the remaining eMudhra root and fails the build if any of the four
subjects is present (117 of 118 entries kept). If a future Temurin build drops
that entry, the `keytool -delete` fails the build, so the step is revisited
rather than silently kept. Actual exposure was low before the change: the
backend opens no outbound TLS connection in the release (PostgreSQL and MinIO
are reached over the private network without TLS, and there is no HTTP client or
remote key source), so its trust store is not consulted. **Status: `FIXED` for
both CVEs (verified on the image).** The 39 October CVEs above are fixed by the
upgrade; no other JDK advisory affecting 25.0.5 is open.

### Writable tmpfs mounts

Measured on the hardened five-image stack after deployment, a registration, a
login and a full restart: the backend's `/tmp` held 32 KiB, the frontend's 4 KiB
and PostgreSQL's `/var/run/postgresql` 4 KiB; each mount offered 3.9 GiB, half
of the 7.7 GiB Docker VM. Docker's default tmpfs size is
half of the host's memory. The only documented input is the 20 MB request limit
(`MAX_UPLOAD_SIZE`); no upload endpoint exists yet, and no concurrency or memory
budget is defined, so any size limit would be invented. None was set.

Second pass, on the exact candidate set: all requests reach the backend through
Nginx, which rejects bodies over its default 1 MB `client_max_body_size` (a 2 MB
POST returned 413; a 0.9 MB one reached the backend and got 400). Nginx spools
larger request bodies and proxied responses to `/tmp/client_temp` and
`/tmp/proxy_temp`, with `worker_processes auto` and 1,024 connections per worker,
so its `/tmp` use is per request and transient but has no fixed upper bound short
of connections times body size; the backend's spooling is bounded per request by
the same 1 MB. A tmpfs `size=` cap would turn memory exhaustion into request
failures, but choosing it still needs the VM's memory and an accepted
concurrency, neither of which is recorded. **Open:** a memory budget for the VM
services, from which tmpfs and container limits can be derived. Note also that the
1 MB ingress default is below the 20 MB `MAX_UPLOAD_SIZE`, which matters once
upload endpoints exist.

### PostgreSQL capabilities (second pass)

PostgreSQL keeps `CHOWN`, `DAC_OVERRIDE`, `FOWNER`, `SETGID` and `SETUID` in its
bounding set because the entrypoint starts as root. Measured on the byte-clean
image under the release restrictions (read-only root, `/var/run/postgresql`
tmpfs, `no-new-privileges`), each set tested with a fresh volume, a restart with
data, and a Bookworm volume that must be refused unchanged:

| Capabilities | Result |
| --- | --- |
| The five | All pass |
| `DAC_READ_SEARCH` instead of `DAC_OVERRIDE` | All pass |
| `CHOWN`, `DAC_READ_SEARCH`, `SETGID`, `SETUID` | All pass; the entrypoint's tolerated `chmod` of the data directory fails |
| Without `DAC_OVERRIDE`/`DAC_READ_SEARCH`, or without `CHOWN` | Initialization fails |
| None, running as `postgres` (`--user 70:70`) | All pass, `CapBnd` 0 |

Every server process already runs as `postgres` with `CapPrm` and `CapEff` 0
and `NoNewPrivs` 1, so only root processes (the entrypoint before it hands over,
and `docker exec`) can use the bounding set. Any root-start set keeps `SETUID`
(needed for `su-exec`), and with it root can act as `postgres` anyway; `FOWNER`
or `CHOWN` also allow writing others' files. The smaller root-start sets therefore
give little real separation, and the current set was kept. The effective
improvement is to start the server without root, which removes all five. That
changes the image's runtime user and the entrypoint path ADR003 approved, and the
Compose contract forbids a `user:` override, so it is a Leader decision (an ADR003
amendment), not made here.

### F2, F3, F7, F8, F9

Unchanged from the 2 November record and still enforced: candidate, qualification,
admission and promotion stages; root-owned Nginx configuration; no
setuid/setgid programs in the backend; read-only roots, `cap_drop: [ALL]`,
`no-new-privileges`, PostgreSQL's five capabilities and the approved tmpfs paths;
the initializer's writable paths (its layer 1 keeps the base's modes, which the
suite now checks entry by entry); private networking, HTTPS-only ingress and the
disabled Console.

### Remaining decisions

1. Security admission of each candidate image and the DP01 release blockers 1–7.
2. F5: acceptance of GO-2026-5932 and CVE-2026-113466 in the server, or a
   trusted patched server.
3. PostgreSQL: whether to start the server as `postgres` with no capabilities
   (ADR003 amendment; measured to work above).
4. F1 licensing: whether and how the image is conveyed, and legal confirmation
   of the source-provision procedure.
5. F6: whether to sign, what, and under which trust model.
6. A memory budget for tmpfs and container limits.
7. Still pending from earlier records: the glibc/musl collation risk, the
   Console-hardening disposition, and a credential strength policy (none added).

Nothing was built, loaded or run on the Azure VM; no release tag was created or
changed.

This record and its changes were produced through AI-assisted development
(Anthropic Claude Code) under the Leader's task. Trivy, govulncheck, cosign and
their databases, the Sigstore public-good transparency log, the Go module proxy
and checksum database, GitHub and the OpenJDK and Adoptium advisory and release
pages were used only for this review. No AI service or new infrastructure
technology is introduced into CodeMastery's runtime.

## DP01 independent-review remediation — 3 November 2026

### Authority and corrected completion claims

The Project Leader's "PR #132: Remediate Independent Review Findings" task
continues the existing branch and authorizes only the two P2 corrections,
necessary tests/evidence, and publication to PR #132. The independent review of
`d6a4eeb033e8ded469e98d41f0d116b0e62aa5af` returned `CHANGES_REQUIRED`:
F10 silently changed long credentials, and F11's inspection guard could pass on
a tool failure. Earlier dated records remain historical evidence. Their F10
`FIXED` claim covered argv disclosure but did not establish long-value
preservation; their F11 certificate inventory was correct, but the claimed
fail-closed enforcement was incomplete. This section supersedes those two
completion claims only.

The Leader's bounded integration acceptance of the previously documented
MinIO-server, unsigned-image, Ubuntu, tmpfs and Alpine/musl limitations remains
in effect for source integration. It grants no production image admission,
release promotion, legal clearance, data migration or deployment authority.
CODEOWNER/last-push approval and the existing DP01 release gates remain separate.

### F10 — reject the pinned client's actual stdin overflow before changes

Both pinned clients' `fetchAliasKeys` and `fetchUserKeys` use
`bufio.NewReader(os.Stdin).ReadLine()` and discard its continuation flag:
[minio/mc at 7394ce0](https://github.com/minio/mc/blob/7394ce0dd2a80935aded936b09fa12cbb3cb8096/cmd/admin-user-add.go).
One read preserves at most 4,096 bytes. Before correction, local initialization
with a 5,000-byte application secret exited 0, authenticated its truncated
4,096-byte prefix, and rejected the complete configured value with
`SignatureDoesNotMatch`. A control operation proved that the server accepts the
intact 5,000-byte secret. This is a client-input limitation, not a MinIO server
password limit or a new password-strength policy.

Alternatives checked: aliases can use private JSON configuration, as the release
initializer already does, but that does not fix `admin user add`; that command
accepts its secret only as an argument or through the bounded stdin reader.
Arguments expose it. The client's file-based alternative imports cluster-wide
IAM state and would broaden this one-user operation. Replacing or patching the
local client would change the separately pinned artifact. The smallest bounded
correction therefore rejects overflow explicitly, before any MinIO operation.

- **Local Compose:** checks both `MINIO_ROOT_PASSWORD` and `STORAGE_SECRET_KEY`
  before any `mc` call or temporary configuration. `LC_ALL=C` makes shell length
  count bytes. Values up to and including 4,096 bytes retain their exact value;
  longer values fail with an explicit diagnostic. The existing control-character
  rejection and stdin delivery remain; no password reaches `mc` argv or logs.
- **Release initializer:** the same reader is used only when adding the
  application identity. Its application secret receives the same early bound,
  before contacting MinIO or creating resources. Root credentials still use the
  private JSON alias file and support a 5,000-byte password. Existing IAM,
  bucket scope, conflict refusal and no-change repeat semantics are preserved.
- **Artifacts:** no client source, dependency, Go toolchain, binary hash,
  Corresponding Source archive, server, image pin or deployment-policy value
  changes. The release initializer image contains the corrected script.

Regression coverage uses actual full-value authentication, shortened-value
denial, repeat initialization, absence of mutation on rejected inputs, argv
capture and log checks. It covers 4,095, 4,096, 4,097 and 5,000 bytes, UTF-8
values crossing the byte boundary, and control characters. Exact commands and
results are in [testing.md](testing.md#dp01-independent-review-remediation-verification--3-november-2026).
The confirmed limitation is explicit rejection above the client buffer size;
arbitrary-length local credentials are not claimed supported.

### F11 — enumerate successfully and verify exact X.509 identities

The old `! keytool -list ... | grep ...` accepted both a listing failure and a
search error as if prohibited roots were absent. The pinned Temurin 25.0.5+8
runtime and deletion of the remaining G1 alias are unchanged.

`backend/Dockerfile` now compiles a build-only Java verifier. The runtime build
step first requires successful `keytool -list -rfc`, then independently loads
the actual binary keystore with Java's `KeyStore` API. Every trust entry must be
an X.509 certificate entry; the store must be nonempty. The complete multiset of
DER SHA-256 identities must equal the parsed certificate inventory. An
unreadable, malformed, empty or incomplete inventory, or an enumeration,
parsing or validator error fails the build. No `grep`, pipeline negation or
unchecked tool status decides acceptance. Human-readable subject formatting is
not used as certificate identity.

The four denied identities are from OpenJDK's
`jdk-25.0.4.1+1/src/java.base/share/data/cacerts/`: `comodoaaaca`,
`emsigneccrootcag3`, `emsignrootcag1`, `emsignrootcag2`, corresponding to the
four roots in [OpenJDK's advisory](https://openjdk.org/emudhra). Their exact
fingerprints are in the verifier; regression fixtures are checked against them.
An unrelated certificate with the same common name is accepted.

The regression script executes the **actual Dockerfile verification RUN** in
uncached test builds. It rejects the published Temurin store, each of the four
introduced real roots, listing and validator failures, malformed/incomplete
successful listing output, and invalid/unreadable keystores. The final backend
store passes with 117 entries; an unrelated same-CN addition passes with 118.
The helper class, compiler, fixtures and temporary inventory do not ship in the
runtime image. CI invokes this script in the existing HTTPS ingress job.

This remediation and its tests/documentation were produced with OpenAI Codex
assistance under the Leader's task. OpenJDK's public certificate data and the
pinned client source were inspected for these corrections; no AI service or new
production dependency is added to the application.

## DP01 post-merge security requalification — 3 November 2026

### Baseline and scope

Fresh `origin/main` is `0535ec4f3ad9939f532648a78296516e7e61801e`, the
[PR #132](https://github.com/chi-thanh-nguyen/codemastery/pull/132) squash merge.
Compared with the requested audit baseline `b2984a1`, it already contains the
project-built patched `mc`, byte-clean PostgreSQL, pinned release images,
restrictive Compose hardening and the corrected F10/F11 enforcement. Those
changes are retained. The Leader's current task authorizes remediation,
qualification and a follow-up PR, without risk acceptance, governance changes,
security admission, release promotion or VM operations. Historical records above
remain historical; this section records the post-merge recheck.

One additional defect was reproduced: the default **local HTTP** target in
`frontend/Dockerfile` still used mutable `1.30.5-alpine-slim` and allowed UID 101
to modify `/etc/nginx`. The release target was already patched. Both the version
and configuration-ownership assertions failed on the original local image.

### Additional remediation

The local target now uses
`nginxinc/nginx-unprivileged:1.30.6-alpine-slim@sha256:5d8bb9437d74a600ec5dbf90c035451c724dc33eac8d3a71078f6f8612c345f6`.
It pins the same reviewed fixes for the packages present in the slim runtime:
`alpine-release=3.24.3-r0`, `ca-certificates-bundle=20261020-r0`,
`musl=1.2.6-r3`, `musl-utils=1.2.6-r3`, `zlib=1.3.2-r1`. Configuration is
root-owned without group/other write, and the copied HTTP configuration is 0444.
UID 101, entrypoint, port 8080, HTTP routing, SPA fallback and the Compose-supplied
HTTP healthcheck are preserved. The existing ingress CI job now checks Nginx
1.30.6, configuration ownership/write denial and absence of setuid/setgid files
in both frontend targets.

The original local image reports three HIGH Nginx findings
(`CVE-2026-15176`, `CVE-2026-93479`, `CVE-2026-97205`), three MEDIUM Nginx findings
(`CVE-2026-90439`, `CVE-2026-95101`, `CVE-2026-97629`), MEDIUM
`CVE-2026-85091` in zlib, and UNKNOWN `CVE-2026-107891` in both musl packages.
The candidate reports zero in both Trivy modes. Installed package versions and
Nginx's running version were checked, rather than inferring a fix from counts.
These are scanner severities; [Nginx's own advisory ratings](https://nginx.org/en/security_advisories.html)
differ, and exploitability of each advisory under the local configuration was
not established.

This local artifact remains layered: the archive holds the old zlib and musl
files in its pinned base as well as their replacements. The runtime version and
merged-filesystem fixes are verified; erasure of those inherited bytes is not
claimed. PostgreSQL's separate no-gosu-layer requirement still passes. This
layer distinction remains relevant to any artifact admission decision.

### Current F1–F11 disposition

`FIXED` describes the specified engineering defect, not admission. `MITIGATED`
means a verified control reduces exposure while the finding remains.
`NOT_VERIFIED` describes missing evidence; `PENDING_LEADER_APPROVAL` reserves a
decision; `BLOCKED` applies to a change requiring an undefined contract.

| Finding / initial risk | Remediation and current supporting evidence | Remaining risk / status |
| --- | --- | --- |
| F1: vulnerable initializer `mc` dependencies | Already fixed by PR #132. Rebuilt binary SHA-256 `025691d43adb653c6ce34723c76450153f2c024bfa232edaca6906fe3e221809`; embedded compress v1.18.7, x/net v0.62.0, x/crypto `v0.58.1-0.20261009181718-2f1f834330b2`, etcd client/pkg v3.5.33. Trivy 0/0; binary govulncheck has no findings. All three server suites pass 28/28; source archive equals the existing asserted hash | `FIXED` for the named dependencies; unsigned project build and source-conveyance/legal determination remain separately controlled |
| F2: inaccurate/incomplete security records | Append-only post-merge record, exact artifact identities, complete scanner findings and execution limitations in [testing.md](testing.md#dp01-post-merge-security-requalification--3-november-2026) | `FIXED` for this record; historical qualifications do not grant admission |
| F3: release names before admission | Existing candidate → qualification → security admission → release promotion procedure retained; only unique non-release tags used here | Procedure `FIXED`; admission and promotion `PENDING_LEADER_APPROVAL` |
| F4: deleted inherited vulnerable `gosu` | Existing flattened PostgreSQL retained. Fresh single-layer/configuration/filesystem comparison passes; no layer contains gosu; Trivy 0/0. Foreign glibc volume refused unchanged with exit 78; fresh musl volume persists across restart | Inherited bytes `FIXED`; existing-volume migration, collation disposition and admission remain separate gates |
| F5: server OpenPGP/module finding, Console CVE and uncertain build provenance | Server bytes remain `3c3d615241314c531e15e57485c053e60dc1af04e87b20424d2db8bdec8a49b2`. Trivy reports GO-2026-5932 UNKNOWN; binary govulncheck reports that advisory. Console-off behavior and restricted runtime are requalified | Console exposure `MITIGATED`; CVE-2026-113466 remains unfixed. OpenPGP runtime reachability `NOT_VERIFIED`; residual-risk/admission decisions `PENDING_LEADER_APPROVAL` |
| F6: mutable bases, unbounded packages, incomplete provenance | Existing release pins retained; local Nginx pin and exact Alpine fixes added. Current/pinned Chainguard signatures verify; current upstream SLSA attestations verify against their exact subjects. Nginx index contains unsigned BuildKit provenance naming `nginx/docker-nginx-unprivileged@bb7393196554e11f7ba95452856d8b5faff217bf` | Mutable runtime base `FIXED`; unsigned provenance is not independently authenticated build provenance. Project image signing/trust policy `PENDING_LEADER_APPROVAL` |
| F7: backend privileges/setuid programs | Existing read-only root, no capabilities, no-new-privileges and setuid/setgid removal retained; image assertions and hardened integration rechecked | `FIXED` for the implemented controls; resource budgets remain open |
| F8: frontend vulnerabilities/configuration write access | Additional local-target fix above; both targets pass image/HTTP/HTTPS regressions and report 0 in both Trivy modes | `FIXED` for the reproduced defects; local HTTP is still a development target and is rejected as a release image |
| F9: writable initializer paths | Existing read-only release root, `/tmp` tmpfs, no capabilities and no-new-privileges pass all three qualification suites; exact filesystem assertions retained | `FIXED` in the release model; numeric tmpfs budgets `PENDING_LEADER_APPROVAL` |
| F10: secret exposure/truncation | Existing stdin/JSON delivery and early 4,096-byte overflow refusal retained; full-value authentication, rejection-before-mutation, argv/log checks and idempotency pass on all three server variants | Reproduced defects `FIXED`; no new password length/strength policy introduced |
| F11: Java advisories/trust roots and Ubuntu packages | Existing Temurin 25.0.5+8 and fail-closed X.509 identity/inventory verifier retained; all 13 build regressions pass. Exact backend artifact has 0 CRITICAL/HIGH, 24 MEDIUM and 4 LOW Ubuntu rows, listed in testing.md; no scanner fixed version is available | Named Java/trust-store defects `FIXED`; Ubuntu residual findings and image admission remain `PENDING_LEADER_APPROVAL` |

### Upstream and reachability limitations

At this recheck, the current Chainguard indexes are
`minio-client@sha256:1dccd2a6fd59914a98ac2d9dad95e552c91243cb0bd7242aaa224802df4b3432`
and `minio@sha256:3c60a2a8c4d33409a9bc7a623a7fcbfb4605adc3d89422d2f53b3f673a6e5597`.
Their binaries are still byte-identical to the previously analyzed upstream
`mc` (`8842390a…bc34a`) and server (`3c3d6152…49b2`). Signatures and
`https://slsa.dev/provenance/v1` attestations verify with cosign 3.1.4, exact
issuer `https://token.actions.githubusercontent.com` and identity
`https://github.com/chainguard-images/images/.github/workflows/release.yaml@refs/heads/main`.
This supplies upstream identity evidence, not a vulnerability fix or proof that
the unmodified source snapshot reproduces the server. Neither pin was changed.

The [current Go advisory](https://vuln.go.dev/ID/GO-2026-5932.json) now identifies
the x/crypto carve-out pseudo-version as fixed; the initializer already uses it.
The server still embeds x/crypto v0.58.0 and `vcs.modified=true`; the supplied
source snapshot's v0.57.0 graph is not the shipped build graph. Binary scanner
symbol reports, prior source-mode negative results and prior function-table
absence observations do not establish runtime reachability or non-reachability
for those exact modified server bytes. **Runtime OpenPGP reachability remains
`NOT_VERIFIED`.** No fresh source-mode result is claimed here, and disabling
Console does not remove CVE-2026-113466 from the binary. A reliable patched
server artifact was not established; no source substitution, storage behavior or
licensing-model change was made.

### Admission and outstanding decisions

All candidates still require security admission of their exact identities.
Preserve the existing decisions on server residual risks, client source/licence
handling, project signing and trust policy, PostgreSQL migration/collation and
any rootless PostgreSQL contract amendment, and service/tmpfs resource budgets.
The last two implementation changes remain `BLOCKED` on their respective
project-specific contract decisions; this follow-up selects no numeric budgets
or new runtime identity. Existing qualified controls remain in place.

After qualification, only an explicit admission record can authorize promotion
to release names; archive identity, protected review/CI, operational inputs and
production deployment authorization remain separate gates. This task grants
none of them. No existing release tags or persistent project volumes were
changed, and no Azure endpoint was contacted.

This follow-up used OpenAI Codex for investigation, remediation, qualification
and documentation, plus Trivy, govulncheck and cosign as external qualification
tools. It introduces no AI runtime service or new production technology.

## Frontend Alpine package pins refreshed — 7 November 2026

The `VM HTTPS ingress` CI job failed on PR #209 at `frontend/Dockerfile` (release
target, `apk add`, exit code 18). Rebuilding without cache for linux/amd64 showed
a deterministic resolution failure, not a network error:

```text
ERROR: unable to select packages:
  alpine-release-3.24.2-r0: breaks: world[alpine-release=3.24.3-r0]
  zlib-1.3.2-r0: breaks: world[zlib=1.3.2-r1]
```

The Alpine v3.24 `main` index (v3.24.4) now offers `alpine-release` 3.24.4-r0 and
`zlib` 1.4-r0 and no longer serves the pinned 3.24.3-r0 and 1.3.2-r1; Alpine keeps
only the current build of each package in a stable branch. Every other pinned
version is still the index version. The Alpine security database lists
CVE-2026-4738 as fixed in `zlib` 1.4-r0 (in addition to the fixes of 1.3.2-r1).
Both frontend targets now pin `alpine-release=3.24.4-r0` and `zlib=1.4-r0`; base
image digests, the other pins, configuration ownership and UID 101 are unchanged.
The CI build step now prints the last 80 lines of a failing `docker build` log
instead of discarding it (no build argument carries a secret).

Local verification (macOS arm64, Docker 29.8.1, builds forced to linux/amd64 as on
the runner): both targets built without cache; the job's first four steps passed
(trust-store regressions 13/13, UID 101, Nginx 1.30.6, root-owned read-only
configuration, no setuid/setgid files, all eight invalid release origins rejected;
CA store against the YE2 chain with OpenSSL 3; `INGRESS_STUB_TESTS_PASS`;
`INGRESS_BACKEND_TESTS_PASS`). Trivy (all severities) reported zero findings in
both images. The fifth step (DP01 preflight) needs root on the host (`sudo
install` under `/etc/codemastery`) and was not run locally. This is local evidence;
the job must still pass on the CI runner.

Anthropic's Claude Code (Claude Opus 5.5) diagnosed the failure, made the change and ran these checks.

## CT17 Final runtime and recovery follow-up — 9 November 2026

**Current r2 update, 10 November:** [ADR009 §9](decisions/009-ct17-final-runtime-recovery.md#9-continuation-attestation-delegated-selections-and-executed-qualification)
extends the exact r1 decisions below. `CT17-F-RUNTIME-2026-11-10-r2` is
**DELEGATED_APPROVED**, including the Leader-supplied full VM resource ID and
credit-only/no-new-billable-resource funding boundary. `CT17-F-RECOVERY-2026-11-10-r2`
is **DELEGATED_APPROVED** for the destination/custody/scheduler design and tested,
identity-bounded MinIO cold-copy, CMS authenticated encryption and provenance/
publication mechanisms. Batch 4 has **8/8 technical approvals**; operational acceptance remains pending.

ADR008 and CP-030 r2 are integrated by #231 at main `b6fa24b`; ADR009 awaits
#233's own independent review/protected integration. The dated paragraphs below
retain their original scope and cannot reopen the eight technical approvals.

The supplied operational packet's affirmative host/platform/operator/TLS-path
facts are current **LEADER_CONFIRMED** within their exact scope. The newly supplied
Education Hub observation is **$88 remaining**, expiring **31 October 2027**, dated
10 November approximately 11:05 ICT; [ADR009 section 10](decisions/009-ct17-final-runtime-recovery.md#10-post-pr231-integration-and-azure-evidence--10-november-2026)
updates the former balance/expiry evidence gap without changing r2. Current cost
feasibility and disk/capacity/access remain execution gates; the old-hostname certificate is not evidence
for codemastery.app. Technical selections include root-only runtime env/immutable
checkout, guarded acme.sh TLS-ALPN renewal and measured resource admission; native
8h captures, private Azure StorageV2 Hot/LRS East Asia staging/archive separation,
an independently retrievable encrypted workstation copy, separate key escrow and
signed manifest/monotonic ledger. No resource/grant/cost is claimed authorized or
existing. All production use requires separately authorized implementation,
actual bindings, changed-build qualification, exact Final security admission and
F2/product/capacity/RPO/RTO acceptance. The deployment script is not extended.

Synthetic full cold-volume restore and CMS/key/corruption tests passed within
ADR009's stated fixture/build limits; cloud behavior, current VM/TLS, full-product
QA and real 24h/48h capability did not run. See the detailed Q01–Q09 disposition
and [reproduction record](testing.md#ct17-r2-executed-mechanism-qualification--10-november-2026).
The paragraphs below are the retained **r1 historical preparation state**, not
current whole-recovery blockers.

[ADR009](decisions/009-ct17-final-runtime-recovery.md) is the authoritative bounded
follow-up for CP-033/034. `CT17-F-RUNTIME-2026-11-09-r1` and
`CT17-F-RECOVERY-2026-11-09-r1` are **DELEGATED_PARTIALLY_APPROVED**; neither entire
checkpoint is complete. All effective P1–P10/B1–B3/P6 and CT17-FINAL F1/F2 clauses
above remain unchanged. The pending six-decision ADR008/#231 is not modified or
treated as effective main integration by this follow-up.

The Leader personally confirmed retaining the existing Azure VM,
**https://codemastery.app**, primary operator/secret/key/recovery custody and public
access in principle after all gates. Actual resource identity, capacity, existing
permissions/configuration custody, served TLS/renewal and Final artifact admission
remain unverified. DNS resolved the domain and historical Azure hostname to
20.205.56.236; TCP 22/443 checks timed out. No cause or current VM state is inferred.
Local `061102e3` archive/OCI checks passed for historical limited-preview packaging,
not production identity, authenticity or Final admission.

The delegated recovery **design** selects RPO 24h/RTO 48h, capture every 8h,
three intraday + seven daily + four weekly qualified points and pre-change/demo
capture. Targets include publication delay/missed-run analysis and the complete
incident-to-reopen interval; none is measured capability or incident data-loss
acceptance. Actual private off-VM and independent non-Azure destinations/cost/access
bindings remain pending. Keep the previous known-good point on any partial/failing
capture or publication. No new configuration value, backup scheduler or command is
implemented in this task.

Full MinIO/version/IAM/config capture and concrete authenticated encryption remain
**PROPOSED_NOT_EFFECTIVE / BLOCKED_BY_MISSING_EVIDENCE** pending the existing
compatibility/security/isolated-round-trip gates. A mirror, PG-only dump, preview
admission or qualification plan alone cannot pass them. See ADR009 for alternatives,
current public price-meter estimates, complete-set/restore protocol and Q01–Q09.
Runtime/source/workflow/Compose/permissions are unchanged. No production operation,
new spending, access/exposure change, residual-risk acceptance or merge is authorized.

## CT17-P6 least-privilege ceiling for Batch 2–7 consumers — 15 November 2026

[ADR012 §19.2](decisions/012-batch-07-contract-approvals.md#192-ct17-p6-plan-2026-11-15-r2--least-privilege-ceiling-for-batch-27-consumers)
records `CT17-P6-PLAN-2026-11-15-r2` (DELEGATED_APPROVED; r2 adds
`enrollments.last_visited_at` for CT22.resume and `quiz_questions.position` for CT23
to r1): a per-relation **ceiling**
for the runtime-role grants that later consumers may request. It is not a matrix
revision. The effective matrix stays r1 plus r2 (and, since 16 November 2026, the
ADR014 r3 rows for two new V008 relations outside this ceiling; since 17 November 2026,
the CO08 r4 rows for `enrollments` and `lesson_progress` inside it; since 22 November 2026, the
AD07 r5 rows for `skills`, `skill_prerequisites` and `lesson_skills` inside it; since 22 November 2026,
the AS05 r6 rows for `questions`, `quiz_questions` and `attempt_answers` inside it, and the AS06 r7 rows
for `quizzes`, `quiz_questions` and `attempts` inside it; since 23 November 2026, the AD06 r8 rows for
`learner_skill_masteries` and `path_recommendations` inside it; since 24 November 2026, the IN10 r10 row
for `audit_logs` inside it, followed by CO15 r11's `lesson_progress` replacement), and the extension procedure above
still applies unchanged: each implemented consumer adds its own dated revision,
`RUNTIME_PRIVILEGE_MATRIX` and CI tests together, never beyond the ceiling. Nothing is
granted, and provisioning on any real database remains Leader-reserved.

## Phase 4 release pipeline — 23 November 2026

[ADR015](decisions/015-release-pipeline.md) records the authority (the Leader's
Phase 4 delegation), every technical decision and its alternatives. This section is
the operator's view. It approves nothing beyond ADR015 and changes no earlier record.
`deploy.sh`, the Compose files and the Dockerfiles are unchanged.

### Stages

| Stage | How | Result |
| --- | --- | --- |
| 1. Candidate | Actions → **Release** → *Run workflow* on `main`, `stage=candidate`; optional `revision` (full SHA on `main`, default the dispatched commit), `public_origin` (default `https://codemastery.app`) and `compatibility_baseline` | The latest authenticated evaluation of every required check of `main` must have passed on the revision ([ADR015 D2, amendment A2](decisions/015-release-pipeline.md#11-amendment-a2--latest-authenticated-ci-evaluation-24-november-2026)): a verified re-run replaces an earlier failure, a newer failure or unfinished run refuses, and every superseded result stays in the manifest. Five linux/amd64 candidates are built, reloaded from their archive, qualified (MinIO initializer suite with the minimal server, backend trust store and runtime privileges, byte-clean PostgreSQL, image hardening, `deploy.sh` preflight/load/provision smoke and update-guard refusals), scanned (Trivy default and comprehensive, SPDX SBOMs), packaged, attested and stored as the draft release `candidate-<12-hex>-<run>-<attempt>`, titled “NOT ADMITTED” |
| 2. Security admission (ADR015 A1; no pull request) | The admission authority (`chi-thanh-nguyen`) dispatches **Release** on `main` with `stage=promote`, `candidate=<tag>`, `candidate_manifest_sha256=<SHA-256 of the reviewed release-candidate.json>`, `version=v<x.y.z>`, `accepted_findings=<n>` and an optional `note`; reads the `Admission request` job summary; approves the run's pending `release-admission` deployment | Only a first-attempt dispatch from `main` by the authority's account (login and ID, three GitHub views agreeing), approved by that account in the protected environment (required reviewer, `main` only, no administrator bypass; rules and approval read back, fail closed), naming the attested manifest digest and the exact finding count (CRITICAL/HIGH refused unless `accept_critical_or_high`), produces `admission.json`, attested before any candidate image runs ([A1](decisions/015-release-pipeline.md#10-amendment-a1--authenticated-admission-without-a-pull-request-per-candidate-23-november-2026)) |
| 3. Promotion | Automatic in the same run after the admission | Candidate attestation and package verified; the admission re-derived and bound to this run; images retagged to `codemastery.invalid/*:release` and saved; identities must not change; `deploy.sh` accepts the archive for both image stores; every row of a fresh rescan must be an admitted candidate finding at the same installed version and no higher severity, with no loss of scan coverage; the smoke runs on the release archive; the attested package is stored as the draft release `v<x.y.z>` |
| 4. Publication | Not automated | Un-drafting, tagging or making binaries public is a Leader decision that first needs the F1/F5 licensing determination ([D9](decisions/015-release-pipeline.md#d9--storage-retention-and-publication)) |

A failure at any step stops the stage without a draft. Nothing is retried, and no
draft, tag or release is ever overwritten: a rerun is a new candidate.

### Using a release package with `deploy.sh`

1. Download every asset of the release draft into one directory (write access is
   needed to see drafts).
2. On a workstation, verify the package with **two separate checkouts**: the release
   tooling at `release.json` `tooling.sha` (the `main` commit whose `release.yml`
   promoted and attested the package, so its verifier understands the package format)
   and the application source at `release.json` `source_revision` (its `deploy.sh`,
   Compose files and archive check). Never run the source revision's own
   `infrastructure/release/verify-release.sh`: it may predate the package format (the
   first A1 release's source `1d0658a7…` still carries the pull-request admission
   verifier, which refuses every A1 package). No code from either checkout runs before
   your own `gh` has verified that `release.json` was attested by `release.yml` on
   `main` at the tooling commit it names. Run as one bash script (it stops at the first
   failure):

   ```bash
   set -euo pipefail
   pkg=<directory holding the downloaded assets>; version=v<x.y.z>; store=<containerd|graphdriver>
   repo=https://github.com/chi-thanh-nguyen/codemastery.git
   # Validate JSON strings before command substitution can strip trailing newlines.
   revisions="$(python3 -I -c '
   import json, re, sys
   with open(sys.argv[1]) as f:
       manifest = json.load(f)
   revisions = (manifest["tooling"]["sha"], manifest["source_revision"])
   if not all(isinstance(value, str) and re.fullmatch("[0-9a-f]{40}", value) for value in revisions):
       sys.exit("Invalid tooling or source revision SHA.")
   print(*revisions)
   ' "$pkg/release.json")" || {
     printf '%s\n' 'Invalid release manifest or revision SHA.' >&2
     exit 1
   }
   read -r tooling_sha source_revision <<< "$revisions"
   if ! [[ "$tooling_sha" =~ ^[0-9a-f]{40}$ && "$source_revision" =~ ^[0-9a-f]{40}$ ]]; then
     printf '%s\n' 'Invalid tooling or source revision SHA.' >&2
     exit 1
   fi
   gh attestation verify "$pkg/release.json" --repo chi-thanh-nguyen/codemastery \
     --cert-identity https://github.com/chi-thanh-nguyen/codemastery/.github/workflows/release.yml@refs/heads/main \
     --source-ref refs/heads/main --source-digest "$tooling_sha" --deny-self-hosted-runners || {
     printf '%s\n' 'Release manifest attestation verification failed.' >&2
     exit 1
   }
   git clone --quiet "$repo" tooling || {
     printf '%s\n' 'Failed to clone release tooling; existing directories are not reused.' >&2
     exit 1
   }
   git -C tooling checkout --quiet --detach "$tooling_sha" || {
     printf '%s\n' 'Failed to check out attested release tooling.' >&2
     exit 1
   }
   git clone --quiet "$repo" source || {
     printf '%s\n' 'Failed to clone application source; existing directories are not reused.' >&2
     exit 1
   }
   git -C source checkout --quiet --detach "$source_revision" || {
     printf '%s\n' 'Failed to check out the release source revision.' >&2
     exit 1
   }
   bash tooling/infrastructure/release/verify-release.sh "$pkg" --version "$version" --source source --store "$store"
   ```

   (`--version` names the release you intend to transfer; another release is refused.)
   Run in a directory with no `tooling` or `source` destination. A failed clone or
   checkout stops the procedure; it never removes or reuses an occupied destination.
   The verifier refuses to run from any commit other than `tooling.sha` or from a
   modified checkout, and refuses a `--source` checkout that is not exactly
   `source_revision`; the two are the same directory only when the two commits are
   equal. Offline: fetch `gh attestation trusted-root > trusted_root.jsonl` beforehand,
   add `--bundle "$pkg/release.sigstore.json" --custom-trusted-root trusted_root.jsonl`
   to the `gh attestation verify` line and `--trusted-root trusted_root.jsonl` to the
   verifier, and set `repo` to a local repository already holding both commits; keep
   fresh `tooling` and `source` destinations. The script verifies the attestation of
   `release.json`, the archive, `mc-source.tar.gz` and `admission.json` (signer
   `release.yml` on `refs/heads/main` exactly, at the recorded tooling commit), the
   agreement of release, admission and candidate records (the admission re-derived from
   its evidence and bound to the promoting run), that `admission.sigstore.json` was
   signed by the admission's own dispatch run, and online that GitHub's run and
   approval records name the same dispatching and approving account (offline: only
   what the attested workflow recorded; run it online on a connected host), the
   `deploy.sh`/Compose hashes and `deploy.sh`'s own archive verifier, then prints `--archive`,
   `--archive-sha256`, the five `--expect-image` values and `--source-revision`.
   `deploy.sh preflight` reports which image store the VM uses.
3. Transfer the archive with `mc-source.tar.gz` (the AGPL Corresponding Source of the
   initializer's `mc`). The channel and custody remain P8 inputs; nothing here selects
   them.
4. Use the `deploy.sh` checkout of the same `source_revision` (ADR009 §9.3). On a host
   with existing containers or volumes, `deploy.sh` still requires the four P9 update
   attestations (`--recovery-artifact-verified`, `--schema-image-compatibility-verified`,
   `--persistent-state-preserved`, `--update-authorization`). `release.json` lists the
   Flyway migration inventory to compare with the target's `flyway_schema_history`,
   and what the release does not verify.

### What this does not establish

No candidate has been built from `main` by this pipeline, admitted or promoted, and no
artifact has been published, transferred or loaded on the Azure VM. The deployed
preview's revision, image identities, volumes and Flyway state are not verified by
any repository evidence; the GitHub pre-release `v0.1.0-preview.1` (source
`061102e3…`, no assets) and the local preview package of
[ADR009 §2.3](decisions/009-ct17-final-runtime-recovery.md#23-exact-local-historical-release-identities)
remain historical records, not a verified VM state. Release blockers 2–7 above keep
their status, except that blocker 3's artifact admission review and package custody
now have an implemented mechanism; the VM transfer channel and custody remain open.
Pipeline verification is recorded in
[testing.md](testing.md#phase-4-release-pipeline-verification--23-november-2026).

This record, the pipeline and its tests were produced through AI-assisted development
(Anthropic Claude Code) under the Leader's task. Trivy, Sigstore (through GitHub
artifact attestations) and GitHub Actions are used by the pipeline; no AI service or
runtime technology is added to CodeMastery.

## DP01 release image security remediation — 23 November 2026

**Authority.** The Project Leader's instruction of 23 November 2026 (ICT), “CodeMastery —
DP01 Full Security Remediation & Release Candidate Pre-Merge Qualification”, authorizes
remediating the CRITICAL/HIGH findings of the first pipeline candidate in one pull request.
It does not grant security admission, deployment or publication, and this record grants
none. No decision record changes: the ADR003 decision (pinned `postgres:17.11-alpine3.24`
base, `su-exec`, data-volume boundary, byte-clean single layer), the ADR009 runtime
policies and every ADR015 release decision stay as they are. Package updates inside the
images follow the existing pinned-update practice of
[Frontend Alpine package pins refreshed](#frontend-alpine-package-pins-refreshed--7-november-2026).

### Baseline

The first candidate of the [Phase 4 pipeline](#phase-4-release-pipeline--23-november-2026):
run [45073820127](https://github.com/chi-thanh-nguyen/codemastery/actions/runs/45073820127)
on `main` `cc86c0803aa1ad9428f8511c6ba0f6669829814d`, draft release
`candidate-cc86c0803aa1-45073820127-1` (ID 454779483, 23 assets, origin
`https://codemastery.app`), status QUALIFIED and NOT ADMITTED. Its `findings.json` (hash
checked against the draft's `SHA256SUMS`; Trivy 0.76.2, vulnerability database updated
2026-11-22T19:00:46Z) holds 126 rows, 32 of them CRITICAL or HIGH:

| Image | CRITICAL | HIGH | Packages (installed → fixed) |
| --- | ---: | ---: | --- |
| frontend | 4 | 6 | `nginx` 1.30.6-r1 → 1.30.7-r0 (4 CRITICAL, 2 HIGH); `libxslt` 1.1.43-r3 → 1.1.43-r4 (3 HIGH); `libxml2` 2.13.9-r2 → 2.13.9-r3 (1 HIGH) |
| postgres | 0 | 20 | `krb5-libs` 1.22.2-r1 → 1.22.3-r0 (16 HIGH); `libxslt` (3 HIGH); `libxml2` (1 HIGH) |
| backend | 0 | 2 | `apt`, `libapt-pkg6.0t64` 2.8.3 → 2.8.4ubuntu0.1 (CVE-2026-109518) |
| minio, minio-init | 0 | 0 | — |

### Changes

Each fixed version was confirmed against the live package source on 23 November 2026,
not taken from the candidate report alone.

| Image | Change | Why this route |
| --- | --- | --- |
| frontend, both targets | Same base digests. Nginx and, in the release target, its four dynamic modules (`geoip`, `image-filter`, `njs`, `xslt`) raised together to nginx.org `1.30.7-r1` (`njs` `1.30.7.1.0.2-r1`) from nginx.org's signed Alpine v3.24 repository; Alpine pins added for `libxml2` 2.13.9-r3, `libxslt` 1.1.43-r4 (release target) and `libcrypto3`/`libssl3` 3.5.9-r1 (both) | The base installed Nginx from nginx.org, not from Alpine. No `nginx-unprivileged` 1.30.7 image was published (Docker Hub answers 404 for `1.30.7-alpine`, `-alpine-slim` and `-alpine3.24`), while upstream's generator Dockerfiles already install exactly these nginx.org packages. apk verifies them against nginx.org's key, which the base already trusts and whose SHA-512 (`e09fa32f…0655`, upstream's pin) the build checks first; only that repository is consulted for them. apk keeps the base's unprivileged `nginx.conf` and `default.conf` and writes the stock privileged files as `*.apk-new`, which the build removes. The build fails unless `nginx -v` reports 1.30.7 |
| postgres | Same base digest; one added `RUN` in the `layered` stage pins Alpine v3.24/main `krb5-libs` 1.22.3-r0, `libxml2` 2.13.9-r3, `libxslt` 1.1.43-r4, `libcrypto3`/`libssl3` 3.5.9-r1, `zlib` 1.4-r0 and `alpine-release` 3.24.4-r0 | Same-ABI library updates. PostgreSQL 17.11 binaries, musl 1.2.6-r3, ICU 78.1, `su-exec`, the entrypoint boundary and the data directory are unchanged; the final stage still copies the merged filesystem into one layer |
| backend | Runtime base re-pinned to the same tag's current index, `eclipse-temurin:25.0.5_8-jre-noble@sha256:23481dc33a759b79d62814ceb1ec27a0d1a84da9d7aab0609577427176fbb1b7` (Docker Hub, updated 20 November 2026); build stage unchanged | Same Temurin 25.0.5+8 (`java -version`); the JRE's `cacerts` file is byte-identical in both bases (SHA-256 `628b436e…a7ef`), so the existing eMudhra removal and the build-time trust-store verification apply unchanged. Refreshed Ubuntu 24.04 packages: `apt`/`libapt-pkg6.0t64` 2.8.4ubuntu0.1, `libc6` 2.39-0ubuntu8.12, `libpcre2-8-0` 10.42-4ubuntu2.2, `perl-base` 5.38.2-3.2ubuntu0.7 |
| minio, minio-init | None | See "Remaining findings" |

Alternatives not adopted:

- Frontend: the official `nginx:1.30.7-alpine` image (the unprivileged changes would have
  to be re-derived by hand); Alpine's own `nginx` 1.30.7-r0 (a differently built package
  that cannot replace nginx.org's modules); removing the unused dynamic modules (would
  also drop `libxml2`/`libxslt`, but changes image content beyond the fix; a possible
  later hardening).
- PostgreSQL: the 11 November rebuild of the same tag (`sha256:e5d2edd2…`) still carries
  every vulnerable package; `postgres:17.12` (21 November) is a PostgreSQL version
  change not needed for these findings.
- Backend: `apt-get` upgrades in the runtime stage (the official rebuild carries the same
  fixes and more, without adding package-manager operations to the runtime stage).

The release tooling gains two rehearsal refusal cases (a candidate finding accepted under
another service, and one with a modified package) and three unit-test mutations; no
release decision, gate or workflow changes.

### Results

Local rescan of the five rebuilt linux/amd64 images with the pipeline's pinned Trivy
0.76.2 image, `--scanners vuln`, default and comprehensive modes, nothing ignored, and
the **same vulnerability database** as the candidate (updated 2026-11-22T19:00:46Z), so
the comparison is like for like. Distinct rows, both modes merged:

| Image | CRITICAL | HIGH | MEDIUM | LOW | UNKNOWN |
| --- | --- | --- | --- | --- | --- |
| postgres | 0 → 0 | 20 → 0 | 13 → 0 | 4 → 0 | 0 → 0 |
| minio | 0 → 0 | 0 → 0 | 3 → 3 | 0 → 0 | 1 → 1 |
| minio-init | 0 → 0 | 0 → 0 | 4 → 4 | 0 → 0 | 0 → 0 |
| backend | 0 → 0 | 2 → 0 | 50 → 24 | 5 → 5 | 0 → 0 |
| frontend (release target) | 4 → 0 | 6 → 0 | 10 → 0 | 4 → 0 | 0 → 0 |
| **Total** | **4 → 0** | **28 → 0** | 80 → 31 | 13 → 5 | 1 → 1 |

None of the 32 original CRITICAL/HIGH rows is reported at any installed version, and no
row appeared that the candidate did not report. Scan coverage is unchanged for every
image: the same detected operating system family, no end-of-support flag, and the same
target classes (frontend `os-pkgs/alpine`; postgres `os-pkgs/alpine`, now detected as
3.24.4; backend Ubuntu 24.04 and the JAR; minio-init Wolfi and its Go binary; minio its Go
binary, without an OS, as in the candidate). Trivy still names the multi-layer frontend
“alpine 3.24.2”, as it did for the candidate, although `/etc/os-release` in the image is
owned by `alpine-release-3.24.4-r0` and reads 3.24.4.

### Remaining findings

37 rows, none CRITICAL or HIGH. All remain subject to the Leader's admission of a future
candidate; none is accepted here.

| Image | Rows | Status | Reason not fixed here |
| --- | --- | --- | --- |
| backend | 24 MEDIUM, 5 LOW (`libexpat1`, `dash`, `tar`, `libgnutls30t64`, util-linux family, `libsystemd0`/`libudev1`, `login`/`passwd`, `gpgv`) | No fixed version published by Ubuntu for any row | Nothing to install. `libsystemd0`/`libudev1` 255.4-1ubuntu8.19 exist in noble-updates, but Trivy lists no fix for their rows |
| minio | 3 MEDIUM (`otel/sdk`, `otel/trace` v1.45.0 → 1.47.0; Go stdlib 1.27.3 → 1.27.4), 1 UNKNOWN (GO-2026-5932, `x/crypto` v0.58.0) | Fixed versions exist | Needs a new MinIO server binary (a new Chainguard digest): a server artifact change, which this remediation does not make |
| minio-init | 3 MEDIUM (`glibc-2.44`, `glibc-2.44-locale-posix`, `ld-linux-2.44` 2.44-r11 → 2.44-r16), 1 MEDIUM (Go stdlib 1.27.3 → 1.27.4 in `mc`) | Fixed versions exist | Needs a new Chainguard `minio-client` base digest, and a Go toolchain change rebuilds the AGPL `mc` and changes its Corresponding Source archive and recorded hash (F1); both are separate changes |

### What this does not establish

The images measured above were built locally (macOS arm64 host, builds forced to
linux/amd64); image builds are not reproducible, so they are not the images a candidate
will contain. Their identities are recorded in
[testing.md](testing.md#dp01-release-image-security-remediation-verification--23-november-2026)
only to tie the evidence to the bytes that were scanned. No candidate has been built from
a `main` revision that contains this change, nothing was admitted, promoted, published,
transferred or deployed, and nothing on the Azure VM was contacted. The pull request's
`Release rehearsal` uses a synthetic `REHEARSAL-` admission, which is not a security
admission. After integration, a new `stage=candidate` run from the new `main` revision
must produce the candidate that the Leader may admit with a separate admission record
(ADR015 D7).

Anthropic's Claude Code (Claude Opus 5.5) investigated the findings, made the change and
ran the checks recorded in testing.md.

## Release admission without pull requests — 23 November 2026

[ADR015 Amendment A1](decisions/015-release-pipeline.md#10-amendment-a1--authenticated-admission-without-a-pull-request-per-candidate-23-november-2026)
records the authority (the Leader's instruction of 23 November 2026), the options
compared and the design. This section is the operator's view.

### What changed

- The security admission is no longer a JSON record merged through a pull request. It is
  made inside a `stage=promote` run: the admission authority dispatches the run naming
  the candidate, the SHA-256 of the `release-candidate.json` it reviewed, the version
  and the number of findings it accepts; the `Admission request` job shows the verified
  candidate; the authority approves the run in the protected `release-admission`
  environment; the run records `admission.json`, attests it and then promotes (stages 2
  and 3 of the [table above](#stages)).
- `release.py admission-template` and `admission-provenance` are removed; the promote
  stage no longer reads `docs/decisions/release-admissions/`.
- Release packages now contain `admission.sigstore.json` (the admission's own
  attestation bundle). `verify-release.sh` checks it offline and, online, GitHub's run
  and approval records of the admitting run.
- Required configuration: the `release-admission` environment (required reviewer
  `chi-thanh-nguyen`, administrator bypass disabled, deployments from `main` only). If
  it is missing or weaker, promotion stops at its first step.

### Approving a future candidate

1. On the candidate draft, take the SHA-256 of `release-candidate.json` (its line in
   `SHA256SUMS`) and the finding count from the draft summary.
2. Dispatch, in the Actions UI (**Release** → *Run workflow*, branch `main`) or:

   ```bash
   gh workflow run release.yml -R chi-thanh-nguyen/codemastery --ref main \
     -f stage=promote -f candidate=<candidate tag> \
     -f candidate_manifest_sha256=<sha256> -f version=v<x.y.z> \
     -f accepted_findings=<n> -f note='<one-line risk acceptance>'
   ```

3. Open the run, read the `Admission request` summary, then *Review deployments* →
   `release-admission` → *Approve and deploy*. Only this approval and the dispatch are
   manual; verification, admission record, attestation, rescan, promotion, smoke and the
   draft release are automatic. Rejecting, cancelling or not approving admits nothing.
   The review comment is optional free text (empty, several lines and any characters
   are accepted); it is recorded verbatim, JSON-escaped, in `admission.json`, is never
   printed to a log or summary, and authorizes nothing.
4. A re-run of the run cannot admit (it replays the earlier dispatch); dispatch again.

### Current state of the first candidate

The candidate `candidate-1d0658a7215c-45091629955-1` (draft release ID 454878027, run
[45091629955](https://github.com/chi-thanh-nguyen/codemastery/actions/runs/45091629955),
source `1d0658a7215c2a3d3fed569738d522137989b666`, 23 assets, `rehearsal: false`) was
qualified and verified in an earlier session (package, ten identities, attestations,
Q00–Q05, smoke, 37 findings: 0 CRITICAL, 0 HIGH, 31 MEDIUM, 5 LOW, 1 UNKNOWN). The
admission record prepared for it under D7 was never merged and is not an admission. As
of this record the candidate is **not admitted**, nothing has been promoted, no
`v0.1.0-rc.1` draft exists, nothing was published, and nothing on the Azure VM was
contacted. Its admission and promotion require this change on `main` and the Leader's
dispatch and approval as above.

Anthropic's Claude Code (Claude Opus 5.5) selected and implemented the design and ran the
checks recorded in [testing.md](testing.md#release-admission-redesign-verification--23-november-2026).

## Phase 5 SSH continuous deployment — 23 November 2026

[ADR017](decisions/017-ssh-continuous-deployment.md) records the authority (the Leader's
Phase 5 delegation), the read-only VM discovery, every decision and its alternatives.
This section is the operator's view. It approves nothing beyond ADR017; `deploy.sh`
changes only by the CT17-P6 r9 predicate above, the Compose files and Dockerfiles not at all.

### Components

| Path | Where it runs | Responsibility |
| --- | --- | --- |
| `.github/workflows/deploy.yml` | GitHub, manual dispatch on `main` | Workflow **Deploy**: `Deployment request` (authenticates the dispatcher), `Deployment operation` (protected `production` environment, approval read back, SSH operation, external checks, job summary); `Deploy tooling` unit tests on pull requests |
| `infrastructure/vm/cd/vm-transport.sh` | Runner or operator workstation | The only SSH client path: committed host-key pin, batch mode, hash-checked uploads, `codemastery-ops` invocation |
| `infrastructure/vm/cd/deploy-release.sh` | Runner or operator workstation | Downloads and verifies the release, uploads tooling/source/package, runs the VM operation |
| `infrastructure/vm/cd/cd.py` | Runner | Dispatch and environment authentication, deployment context, outside checks, report |
| `infrastructure/vm/cd/bootstrap.sh`, `bootstrap-remote.sh` | Operator workstation → VM (once) | Locations, tooling, `cmdeploy`, sudoers, `ops.conf`, recovery custody, disabled timers |
| `infrastructure/vm/cd/nsg-gate.sh` | Runner (optional) | Temporary NSG rule for the runner's /32 on TCP 22, always removed |
| `infrastructure/vm/cd/known_hosts` | Everywhere | The VM's ED25519 host-key pin |
| `infrastructure/vm/host/codemastery-ops`, `codemastery-ssh-gate` | VM `/usr/local/sbin` | Root entry point with deploy/operator roles; forced command of `cmdeploy` |
| `infrastructure/vm/host/ops.py` | VM (root) | Staging, gates, `deploy.sh` invocation, verification, hold/resume, status, TLS check/renewal, timers |
| `infrastructure/vm/host/recovery.py` | VM (capture) and workstation (publish, verify, restore test, status, prune) | ADR009 recovery sets |
| `infrastructure/vm/host/systemd/*`, `launchd/*.plist` | VM / workstation | Capture and renewal timers (installed disabled); publisher every 15 minutes |

### One-time setup (Leader)

1. Merge the Phase 5 pull request. From a clean checkout of `main`, read the VM state:
   `bash infrastructure/vm/cd/bootstrap.sh --identity ~/.ssh/vm-codemastery_key.pem --check`.
2. Recovery keys on the workstation (macOS needs OpenSSL 3, e.g. Homebrew):
   `CODEMASTERY_OPENSSL=/opt/homebrew/opt/openssl@3/bin/openssl python3 infrastructure/vm/host/recovery.py init-workstation`.
   Copy `~/CodeMasteryRecovery/keys/recipient.encrypted.pem` and its passphrase (login
   Keychain, service `codemastery-recovery-recipient`) to separate offline media.
3. A dedicated deployment key, never your personal key:
   `ssh-keygen -t ed25519 -N '' -C codemastery-deploy -f cm-deploy`.
4. Bootstrap (choose the readiness deadline `deploy.sh` waits; no default is approved):
   `bash infrastructure/vm/cd/bootstrap.sh --identity ~/.ssh/vm-codemastery_key.pem --apply --deploy-public-key cm-deploy.pub --recipient-cert ~/CodeMasteryRecovery/keys/recipient.crt --wait-timeout <seconds>`.
   It prints the producer public key and its SHA-256. Save the PEM to a file, then
   `python3 infrastructure/vm/host/recovery.py trust-producer <file> --fingerprint <sha256>`,
   and write `~/CodeMasteryRecovery/config.json`:
   `{"vm": {"host": "20.205.56.236", "user": "azureuser", "identity": "~/.ssh/vm-codemastery_key.pem"}, "producer_key_sha256": "<sha256>", "restore_test": {"postgres_image": "<a PostgreSQL 17.11 image>"}}`
   (add `"azure": {"account": "…", "container": "…"}` once a storage account is authorized).
5. GitHub → Settings → Environments → `production`: required reviewer
   `chi-thanh-nguyen`, "Allow administrators to bypass" off, deployment branches = the
   custom policy `main` only; secret `VM_SSH_PRIVATE_KEY` = the content of `cm-deploy`
   (then delete the local private key file); variables `PUBLIC_EXPOSURE_AUTHORIZATION` and
   `PACKET_FILTER_VERIFICATION` = references to your exposure decision and NSG rule-set
   record. Optionally the NSG gate variables (`nsg-gate.sh` header) with a federated
   credential whose only role is a custom role over that NSG's security rules.
6. Load the publisher (`infrastructure/vm/host/launchd/app.codemastery.recovery-publisher.plist`, header).
7. Qualify, then enable timers: `sudo codemastery-ops tls-renew --staging` (stops the
   frontend briefly), a manual capture `sudo codemastery-ops capture --kind manual` and its
   publication, then `sudo codemastery-ops timers enable`.

### Per release (three dispatches, two of them approvals)

1. Actions → **Deploy** → *Run workflow* on `main`: `operation=plan`, `version=v<x.y.z>`;
   approve `production`. Read the summary: preflight, schema state, capacity, blockers.
   Nothing changes on the VM except the uploaded tooling/source/manifests.
2. `operation=prepare`, same version; approve. The package is verified and staged, the
   gates re-run, then a **pre-update recovery point** is captured and the application is
   **held stopped** (maintenance window starts). The summary prints the set ID and its
   manifest SHA-256.
3. On the workstation the publisher verifies, decrypts, restore-tests, keeps and
   acknowledges the point within 15 minutes (or run `python3 infrastructure/vm/host/recovery.py publish`
   now; it prints the ledger entry).
4. `operation=deploy`, `version`, `recovery_set`, `recovery_manifest_sha256`; approve. The
   VM proves the hold and the acknowledgement, runs the release's `deploy.sh all` and
   verifies; the summary separates infrastructure, readiness, TLS, outside checks and
   (not verified) features.

CLI equivalent: `gh workflow run deploy.yml -R chi-thanh-nguyen/codemastery --ref main -f operation=plan -f version=v<x.y.z>`.

### Failures

- `BLOCKED` (plan/prepare/deploy): nothing changed (prepare before its capture). Fix the
  named cause; for a schema blocker build a new release (ADR015) rather than forcing.
- `FAILED_CONTAINED` (deploy): `deploy.sh` stopped and contained the application; the
  hold and the point stay. Read the VM log
  (`sudo ls /var/lib/codemastery/deployments/`). `operation=resume` restarts the previous
  containers only if no deployment attempt replaced them; otherwise restoring the point
  is a Leader recovery decision (ADR009 §5), never automatic.
- Cancel a held update: `operation=resume`.
- Lock contention or timer failures appear in `sudo codemastery-ops status` and the journal.

### Local fallback with the operator key

Same driver, operator role, no GitHub approval (your SSH session is the authority):

```bash
export VM_SSH_USER=azureuser VM_SSH_IDENTITY=~/.ssh/vm-codemastery_key.pem
bash infrastructure/vm/cd/vm-transport.sh check
bash infrastructure/vm/cd/deploy-release.sh --operation plan --version v<x.y.z>
bash infrastructure/vm/cd/deploy-release.sh --operation prepare --version v<x.y.z>
python3 infrastructure/vm/host/recovery.py publish
bash infrastructure/vm/cd/deploy-release.sh --operation deploy --version v<x.y.z> \
  --recovery-set <set> --recovery-manifest-sha256 <sha256> --context-file context.json
```

`context.json` is `{"encoded": "<base64url of {\"authorization\": …, \"public_exposure_authorization\": …, \"packet_filter_verification\": …}>"}`
with single-line references to your own records. Interactive inspection:
`ssh -i ~/.ssh/vm-codemastery_key.pem azureuser@20.205.56.236` then
`sudo codemastery-ops status`, `inventory` or `tls-check`.

### PR #274 remediation: authorization and recovery setup

[ADR017 §11](decisions/017-ssh-continuous-deployment.md#11-pr-274-review-remediation--23-november-2026)
refines the original Phase 5 implementation. The following applies to the remediated
revision; earlier test/VM observations retain their historical limits.

- `cmdeploy` requires a short-lived GitHub-signed command token as well as the SSH key.
  Each command, upload hash/size and deployment context is bound to its audience.
  Outside the approved `Deploy` job, use the separately privileged operator fallback.
  Bootstrap installs the verifier under `/usr/local/lib/codemastery`, outside selectable
  tooling. Updating that verifier requires operator bootstrap. Public GitHub OIDC JWKS
  and commit-metadata HTTPS endpoints must be reachable; an outage refuses execution.
- The capture's HBA fence rejects PostgreSQL network writers throughout the cut/hold;
  container-local bootstrap access remains reserved for trusted maintenance. Restart
  policies stay disabled during a hold. Root/Docker operators must not write to either
  store during maintenance. A durable `backup/capture-intent.json` after interruption
  requires inspection of recorded containers/policies, encrypted partial evidence and
  the PostgreSQL `pg_hba.conf.cm-recovery-original` file. Do not delete the intent or
  restart the application blindly. Reconcile the original HBA/restart policies and
  exact prior running services under operator authority; retain failed-set evidence.
- `resume` is a cancellation **before** a deployment attempt. After an attempt, old
  container IDs do not establish schema/data compatibility; inspect containment and
  obtain the Leader's recovery decision. `FAILED_CONTAINMENT_INCOMPLETE` is distinct
  from `FAILED_CONTAINED`; post-deployment verification findings keep the hold record.
- Configure `restore_test.postgres_image` with the **captured server image identity**,
  not merely any PostgreSQL 17 tag. Make that image, the captured MinIO/server client
  images and a GNU-tar helper image available on the workstation beforehand. Restore
  never pulls or builds them. Add `restore_test.minio_escrow` (absolute owner-only JSON
  file from authorized custody) and optionally `restore_test.helper_image` (default
  `ubuntu:24.04`). The escrow fields are `root_user`, `root_password`, `app_user`,
  `app_password`, `bucket`; use the actual deployment identities, never invented
  credentials. Do not commit the escrow or print it. For example, the non-secret config
  shape is `"restore_test": {"postgres_image": "<captured image ID>", "minio_escrow": "<absolute escrow path>", "helper_image": "<locally available GNU-tar image>"}`.
- Full qualification restores into isolated tmpfs, boots the identical captured MinIO
  image and checks version payloads, metadata/tags, configuration/IAM inventory and
  authorized bucket versus denied administration. Missing escrow produces at most
  `+PG_RESTORE_TESTED`, which **cannot** satisfy P9, qualified RPO status or retention
  advancement. Active external MinIO integrations/replication require separate affected
  qualification; do not configure them into this bounded recovery topology silently.
- Publisher retries authenticate existing byte-identical copies and resend a lost
  acknowledgement. The workstation lock excludes simultaneous publication/pruning.
  Partial ciphertext and interrupted state stay for inspection. Qualified ages use
  the signed cut; future cuts are clock errors. Pruning verifies the newest eligible
  independent/cloud point before deleting older eligible history.
- TLS staging and failed challenges both verify recovered frontend health, Nginx
  configuration and served certificate before returning. Timers still require actual
  VM staging/capture qualification and remain disabled by default. No production
  bootstrap, capture, restart, renewal, deployment or cloud mutation ran in this review.

OpenAI Codex performed this authorized review/remediation and disposable verification.
This adds no AI service to the application or deployment runtime. The earlier Claude
Code disclosure and historical VM/release evidence remain intact.

### Historical VM observation on 23 November 2026 (read-only)

The VM runs the 4 November preview (source `061102e3`, the ADR009 §2.3 image identities)
from an extracted tree in `/home/azureuser`, Flyway V001–V006, containerd image store,
trusted `codemastery.app` certificate until 1 February 2027, no recovery timers, acme.sh
without automation (ADR017 §3). Running the read-only Phase 5 gates there:
`v0.1.0-rc.1`'s `deploy.sh preflight` passes; its source tree equals `release.json`;
capacity passes; its schema gate is **BLOCKED** (its `deploy.sh` predates CT17-P6 r9 and
would refuse V006→V009 during provisioning). Deploying therefore needs a new release
built from a revision containing this change.

### What this does not establish

Nothing was bootstrapped, staged, captured, deployed, renewed or exposed on the VM; no
deployment key, environment, NSG rule, storage account or timer exists. Recovery evidence
is synthetic (CI and local); RPO/RTO, real capture duration, workstation availability and
Azure publication are not demonstrated. Application feature behavior after a deployment
remains QA01/QA07/DP03 evidence. This record, the implementation and its tests were
produced through AI-assisted development (Anthropic Claude Code) under the Leader's task;
OpenSSH, OpenSSL, GitHub Actions, the Azure CLI (optional gate) and acme.sh (existing)
are the tools used; no AI service or runtime technology is added to CodeMastery.

## Phase 5 one-time setup executed — 23 November 2026

**Authority.** In this session the Project Leader @chi-thanh-nguyen, after merging PR #274
(main `84d10a3202ef96b0230eff6c22db060a08bcc02d`), explicitly authorized two setup
steps: **bootstrap of the VM** and **creation of the recovery keys and MinIO escrow**.
Production deployment and the TLS staging challenge were **not** authorized and did not
run. This record supersedes the "nothing was bootstrapped" sentence above only for the
items below; every other statement keeps its date.

### Executed

| Step | Result |
| --- | --- |
| Workstation recovery keys (`recovery.py init-workstation`, Homebrew OpenSSL 3.6.5) | `~/CodeMasteryRecovery` (0700); RSA-3072 recipient key, encrypted PKCS#8; passphrase in the login Keychain (service `codemastery-recovery-recipient`); recipient key SHA-256 `21f9b726debb479ed4aee79852b597a4b5b4a6d2e4875d1e3c3a1e88f576c1b6`; an encrypt/decrypt round trip through the Keychain passphrase passed |
| Deployment identity key | ED25519 `SHA256:aoS/YF4Pm2dWhGb32AP739pN7xGTVlu2kmUuC4PNPDQ`, private key on the Leader's workstation only (not yet a GitHub secret) |
| `bootstrap.sh --apply --wait-timeout 300` (Leader-chosen readiness deadline) | Tooling `84d10a32…` installed with its verified git tree; `/usr/local/sbin/codemastery-ops`, the forced-command gate and `/usr/local/lib/codemastery/authorize.py`; user `cmdeploy` (locked password, forced command, sudoers rule validated by `visudo`); `/etc/codemastery/deploy/ops.conf`; recipient certificate; producer signing key generated on the VM (root 0400), public key SHA-256 `e0aa1ec505c5446117ee2de5555fe0104b58ed1037a4edfbc6218bda34bfe016`; capture and renewal timers installed **disabled** |
| Producer key pin | `recovery.py trust-producer` with that fingerprint; the VM key's fingerprint was re-read independently over SSH and matched |
| MinIO escrow | `~/CodeMasteryRecovery/escrow/minio.json` (0600 in a 0700 directory) built on the VM from the runtime env file and streamed to the file; no value was printed |
| Restore images | The captured preview images loaded on the workstation from the verified 061102e3 package (`SHA256SUMS` OK) under `codemastery-restore/{postgres,minio,minio-init}:061102e3` with the VM's identities `4cec267a…`, `70a39ea8…`, `85371a3d…`; `codemastery.invalid/*` local images untouched |
| Workstation `config.json` | Operator transport (`azureuser`, pinned host key), producer fingerprint, restore test with the images above, the escrow and helper `ubuntu:24.04` |

Read back afterwards: the four application containers still running as before;
`vm-transport.sh check` (operator) OK; `codemastery-ops status` reports no hold, no
capture and no ADR017 deployment; `tls-check` OK, 69 days left, served equals installed,
with the documented former-hostname acme.sh finding; both timers `disabled`.

### Still open

- **Release candidate.** On 23 November `release.py evaluate-checks` refused `84d10a32…`
  under the D2 rule then in force: its required `VM runtime privileges with the real
  backend` job failed in attempt 1 of run 45194866405 and passed when the run was
  re-run. [ADR015 amendment A2](decisions/015-release-pipeline.md#11-amendment-a2--latest-authenticated-ci-evaluation-24-november-2026)
  (PR #275, not yet on `main`) lets the latest authenticated evaluation decide; a
  read-only evaluation under A2 on 24 November accepts the revision (A2.9). No candidate
  has been dispatched from it (checked 24 November).
- Security admission and promotion of that candidate; production deployment
  (`plan` → `prepare` → publish → `deploy`); TLS staging qualification, a measured capture
  and timer enablement; the GitHub `production` environment, `VM_SSH_PRIVATE_KEY` and its
  two reference variables; runner SSH reachability (NSG); an Azure storage account; an
  offline copy of the recipient key and passphrase.

## DP01 CI package-index remediation — 25 November 2026

Leader-authorized investigation of CI VM failures on `main` and PR #290, based
on freshly fetched `main` `1e4c54be01c78b14aa4734eb6e6e50426dbe3fe6` and PR head
`a5d250623b2fd32843f57cb8a0d79a2244260e62`. Authority remains requirements,
approved architecture/structure, effective CT17/ADR009 runtime policy and
ADR015 qualification; this record grants no exception, acceptance or deployment.

### Cause and decision

**Alpine: CONFIRMED_REPOSITORY_REMEDIATION_REQUIRED.** The release target
requests `libexpat=2.10.0-r0` from the pinned base's official Alpine v3.24
repositories. A fresh x86_64 APK probe on the exact base
`nginxinc/nginx-unprivileged@sha256:f5116ed4606f3eb015dd4a8e28cb9b351476dc0ad4bc6e9f93b19cec130cdf03`
reproduced exit 4: installed `libexpat-2.9.0-r0`, needed by fontconfig's
`so:libexpat.so.1`, cannot satisfy that exact world constraint. The signed current
index offers 2.11.0-r0, not 2.10.0-r0. Fontconfig requires the ABI, not 2.9.0.

[Alpine's package record](https://pkgs.alpinelinux.org/package/v3.24/main/x86_64/libexpat)
and [aports commit e3335f476c69ffe92817447e265ce2f18323e4ae](https://gitlab.alpinelinux.org/alpine/aports/-/commit/e3335f476c69ffe92817447e265ce2f18323e4ae)
identify the 25 November 12:55:11 UTC security upgrade. Its APKBUILD retains
2.10.0's fixes for CVE-2026-113351, CVE-2026-115506, CVE-2026-117610 and
CVE-2026-118370, and adds CVE-2026-124317, CVE-2026-128241 and CVE-2026-134703.
The package still provides `so:libexpat.so.1` (1.14.0). This corroborates the
3 November hardening record's two HIGH and two other findings on 2.9.0;
returning to that inherited package would regress security.
An independent scan of the exact inherited base with the current 25 November
database reports all four of those libexpat advisories as HIGH, each fixed in
2.10.0-r0. This is severity drift from the historical two-HIGH/two-other record,
not a rewrite of that earlier result. None appears on the rebuilt frontend.

The old official APK URL still returned HTTP 200 during this investigation.
The confirmed defect is index unavailability for the exact pin, not permanent
deletion of the old APK. Index replacement is strongly supported by the upstream
record, current signed index, last observed successful build at 12:10 UTC and
first observed APK failure at 13:11 UTC. Historical index bytes and each runner's
CDN edge are unavailable, so exact propagation timing is not asserted.

Raise only the release libexpat pin to 2.11.0-r0 and assert that installed
revision in the existing ingress build regression. Keep both base digests,
all other reviewed packages, Nginx 1.30.7/module pins, signing-key hash,
signature/TLS verification, UID 101, immutable configuration and CA policy.
Both frontend targets resolve their other exact pins. No unbounded upgrade,
repository switch, retry-based APK workaround or security suppression is used.
Re-pinning a whole base or adding snapshot infrastructure would expand the
change without addressing an additional verified defect. Explicitly downloading
the old APK would retain 2.10.0's now-fixed security issues and is rejected.

**Maven, initial disposition: CONFIRMED_TRANSIENT_EXTERNAL_FAILURE_NO_CODE_CHANGE.**
The [continuation below](#maven-bootstrap-reliability--25-november-2026) supersedes
that decision after PR #293 exposed another recurrence. The main VM
runtime job and E2E job, and PR #290's first ingress attempt, each record four
HTTP 429 responses from Maven Central for the 3.9.16 tarball, with existing
5/15/30-second pauses. The pinned JDK image has no curl/wget/unzip; the wrapper
therefore selects tar.gz and its Java downloader. Host setup-java Maven caches
are not mounted into these Docker stages; separate fresh runners repeat the
bootstrap. No shared Docker cache is configured for these jobs. A bounded HEAD
probe returned 200 and a clean amd64 backend build bootstrapped successfully;
PR #290's unchanged backend also passed its runtime job at 14:00:33 UTC.
These are recurring external rate-limit episodes, not evidence that the URL or
application code is invalid. Existing bounded retries cannot guarantee recovery.
No rate-limit stress test was performed. More retries, an unqualified mirror or
shared cache would introduce additional policy/integrity concerns without a
demonstrated need after recovery; no immediate Maven change is justified.
The current wrapper properties specify no optional distributionSha256Sum;
this investigation does not claim an existing checksum guarantee or remove one.

### Incident evidence (UTC, 25 November)

| Run / attempt | Job | Execution / result |
| --- | --- | --- |
| [45586646767 / 1](https://github.com/chi-thanh-nguyen/codemastery/actions/runs/45586646767) | 139109839768 ingress | Main d8c6588, 09:34–09:36, success |
| [45598582352 / 1](https://github.com/chi-thanh-nguyen/codemastery/actions/runs/45598582352) | 139151114692 ingress | PR #290 dc834fe, 10:49–10:51, success |
| [45599953034 / 1, 2](https://github.com/chi-thanh-nguyen/codemastery/actions/runs/45599953034) | 139155927742, 139157037899 runtime | Main c5b9ac0, 10:58–10:59 and 11:00–11:01, Maven 429; ingress attempt 1 cancelled, not a failed/passed execution |
| [45609807176 / 1](https://github.com/chi-thanh-nguyen/codemastery/actions/runs/45609807176/attempts/1) | 139190071527 ingress | 12:01–12:02, Maven 429 |
| [45610936317 / 1](https://github.com/chi-thanh-nguyen/codemastery/actions/runs/45610936317/attempts/1) | 139193941685 ingress (carried as 139230203152 in attempt 2) | Other feature, execution 12:08–12:10, success |
| [45609807176 / 2–7](https://github.com/chi-thanh-nguyen/codemastery/actions/runs/45609807176) | 139229013535, 139229776237, 139230332607, 139231927814, 139241870992, 139253549420 | Each full job log inspected; 13:11, 13:12, 13:13, 13:16, 13:33, 13:52 starts, same APK failure |
| [45628708222 / 1](https://github.com/chi-thanh-nguyen/codemastery/actions/runs/45628708222) | 139255765615 ingress | Main 1e4c54b, 13:56, same APK failure |
| [45628708308 / 1](https://github.com/chi-thanh-nguyen/codemastery/actions/runs/45628708308) | 139255769488 E2E | Main 1e4c54b, Maven 429 in backend build; browser tests not reached |
| [45628968044 / 1–3](https://github.com/chi-thanh-nguyen/codemastery/actions/runs/45628968044) | 139256697100, 139269183011, 139269461087 ingress | PR #290 a5d2506; APK failure persists at 13:58, 14:17, 14:18 |
| [45632313771 / 1](https://github.com/chi-thanh-nguyen/codemastery/actions/runs/45632313771) | 139268485681 ingress | PR #291 b2b1d1f, 14:16–14:17, same APK failure |

All seven attempts of 45609807176 checked out synthetic merge
`95fe14ec0a994c36db5b1ea1b1481562e53fea42`, merging 353a394 into c5b9ac0.
Latest PR #290 ingress checked out `df44d3a7c6826673ac83b0697ae136fae7e23da9`,
merging a5d2506 into 1e4c54b; main checked out 1e4c54b itself. Logs identify
Ubuntu 24.04.5, runner image 20261116.133.1, runner 2.338.2. Rerun job lists
can carry forward successful jobs: copied successes are not new executions.

The six supplied revisions d8c6588, dc834fe, c5b9ac0, 353a394, 1e4c54b and
a5d2506 all have frontend Dockerfile blob eb02aac9854d68303676577ae86b2ed34c870868.
The complete PR #290 diff changes three Course Java production files, three
test files and testing documentation only. The recent main feature commits
change neither Dockerfile nor workflow. APK resolution runs independently of
Course code; Maven fails before source COPY/compilation. CO12 and those recent
features do not cause either observed failure. This does not qualify their
application behavior or independent acceptance.

### Security qualification and remaining gates

See [the executed test record](testing.md#ci-package-index-remediation-qualification--25-november-2026).
Clean builds use linux/amd64 emulation on an arm64 Docker Desktop host, no cached
RUN steps; later regression builds may reuse layers. The final exported artifacts
were scanned again after the regression rebuilds using ADR015's digest-pinned
Trivy 0.76.2, database updated 2026-11-25T13:07:24.554567818Z, downloaded
14:33:43 UTC, default and comprehensive modes, all severities, no ignore rules.

| Artifact | Configuration SHA-256 in scanned archive | Findings in each mode |
| --- | --- | --- |
| Release frontend | 059814518ee7684b8038eed5794f4731671cdb24f081320ae5150a3f9981586a | 0 |
| Local frontend | b2a330ede3449334fc0502a63accad88b9f717f65b70dc158cc7cc621a402ba0 | 0 |
| Unchanged backend | 0830a9c8320e324509337cc5e8a6041431b8f3f544b6449a34e3f7dcfefe9f78 | 0 CRITICAL/HIGH; 29 MEDIUM, 5 LOW, each without a scanner-listed fix |

The backend residuals include Ubuntu libexpat1, dash, tar, GnuTLS, util-linux,
systemd, login/passwd and gpgv; no Java dependency finding. Zero frontend results
do not qualify the backend or accept residual risks. Scans cover the merged
filesystem, not an assertion that inherited layers contain no old package bytes.
No feature branch, shared environment, deployment, release or merge was modified.
Independent review, protected CI and any later release admission remain required.
After protected integration, affected PRs should update from main and rerun CI;
this investigation performs none of those integration actions.

## Maven bootstrap reliability — 25 November 2026

**CONFIRMED_RECURRING_EXTERNAL_FAILURE_RELIABILITY_HARDENING_REQUIRED.**
[PR #293's first ingress run](https://github.com/chi-thanh-nguyen/codemastery/actions/runs/45638610536/job/139290718324)
built both frontend targets and then failed on `codemastery-ci/backend:ingress-test`.
Maven Central returned HTTP 429 at 0.777, 6.511, 22.25 and 52.96 seconds. The
ingress checks after the image builds were skipped. Independent E2E and runtime
jobs also exhausted the same bootstrap retries on 24 and 25 November, as did
[one release rehearsal](https://github.com/chi-thanh-nguyen/codemastery/actions/runs/45302491334/job/138140130489).
The rerun of main E2E succeeded. That measured recovery did not stop the next
fresh runner from repeating the separate distribution request. GitHub logs do
not identify the service's throttling key or runner egress policy.

| Consumer | Distribution and cache behavior |
| --- | --- |
| Host development and `CI Backend` | Unix or Windows Wrapper 3.3.4; Apache Maven 3.9.16 ZIP, with Unix TAR.GZ fallback when unzip is absent. The wrapper can reuse an extracted installation. `setup-java` restores a host Maven cache scoped to the workflow's branch and dependency key. |
| VM runtime privileges and HTTPS ingress | Separate runners and local Docker builders. Each image build uses the same offline Maven bootstrap stage. The ingress job builds the frontend targets first. |
| Java trust-store verification | Targets the pinned JDK stage directly. It compiles the verifier with networking disabled and consumes no Maven files. |
| Playwright E2E | Browser job uses the backend Dockerfile unless an existing backend image is supplied. Its checkout-local Docker cache and npm cache are separate from host Maven and release. |
| Release candidate and rehearsal | Use the same Dockerfile via `release.sh` with `--platform linux/amd64`. Source checks, image export/reload, scanning, manifest and attestation gates still apply. |
| Local Compose or standalone Docker build | Uses the same offline bootstrap, with content-addressed OCI layers and ordinary local BuildKit reuse. Deployment consumes its previously qualified release archive. |

The Maven source is the [Docker Official Image](https://hub.docker.com/_/maven)
`docker.io/library/maven:3.9.16-eclipse-temurin-25@sha256:93b8a14ea2f412782e4e842651273b4d903e35cc496284f178fbbe2d67d00976`.
The amd64 and arm64 Maven distribution trees matched. The Dockerfile copies the
Maven directory into the existing pinned JDK and removes the image's `ref`
directory. It checks the URL, checks the full remaining tree against an independently
derived SHA-512 with `RUN --network=none`, then prints the verified Maven version.
Compilation runs once. The JRE stage receives the application JAR and the existing
trust-store verification bind. Maven and the donor image's entrypoint, JDK, packages
and configuration outside the verified tree do not enter the runtime image.

The tree checksum is derived by downloading the TAR.GZ and separately checking
SHA-512 against the [Apache release catalog](https://releases.apache.org/catalog/maven)
and [Apache archive](https://archive.apache.org/dist/maven/maven-3/3.9.16/binaries/).
Extract the verified archive into a clean directory with `tar -xzf ARCHIVE
--strip-components=1 -C DIRECTORY`. Use GNU tar in the pinned JDK image and run
`tar --sort=name --mtime=@0 --owner=0 --group=0 --numeric-owner --format=gnu
-C DIRECTORY -cf /tmp/maven-tree.tar .`, then `sha512sum /tmp/maven-tree.tar`.
The digest covers names, bytes, types and modes, with normalized ownership and
timestamps. When updating Maven, verify both platform images against the new
Apache tree, then update the Wrapper URL, OCI index, expected tree digest and
regression expectations together. The ZIP archive has a different published checksum. Host wrapper
checksums and extracted host cache verification require separate cross-platform
work. Neither release nor Docker builds use that cache.

Cold builders pull the pinned registry layers. A warm local builder can reuse the
verified stage across application-source changes. A missing registry layer fails
the build if it cannot be fetched. A changed Maven URL invalidates the verification
step and a stale or mismatched pin fails before execution. A corrupt distribution
fails its digest check. CI does not share exported cache entries or executable
artifacts across pull requests and release. An untrusted pull request can change its
own Dockerfile and checksums, so protected source review and release provenance
remain the controls for trusted builds. No new workflow credentials are needed.

Keeping or increasing bootstrap retries would leave fresh runners requesting the
same Maven Central tarball. A checksum-verified CDN fallback reduces dependence
on one endpoint but still transfers the archive and introduces retry and source
selection code. Host or BuildKit cache export requires additional consumer wiring,
cache scope and cold-start handling. The pinned distribution removes this download
from every Docker consumer with zero direct distribution requests, including cold
builds, at the cost of Docker Hub layer pulls and reviewing the pinned OCI image on
Maven upgrades. Maven project dependencies, registry pulls, npm and package
repositories remain external dependencies.

The hosted [cache scope rules](https://docs.github.com/en/actions/reference/workflows-and-actions/dependency-caching#restrictions-for-accessing-a-cache)
permit a pull request to restore eligible base-branch caches and scope its new
entries to its merge ref. Restore prefixes can select older entries. A key match
does not verify the bytes. These rules are not used as artifact trust guarantees.
The pinned amd64 donor manifest describes nine compressed layers totaling about
168 MiB, compared with an 8.8 MiB Maven TAR.GZ. Actual registry transfer depends
on the builder's existing blobs. The reliability gain is zero Maven Central
distribution calls and fewer independent Maven bootstrap outcomes, with cold
transfer cost moved to the registry.

The bounded CI review also found a separate registry HTTP 500 on a Chainguard
manifest lookup and deterministic Java compilation errors after successful
bootstrap. PR #291's release rehearsal stopped on the old libexpat pin. The
sample did not establish recurring 503, unexpected 404, connection timeout,
reset, npm, Nginx or other APK retrieval failures that justify additional
repository changes. Future exact-pin retirement still requires a reviewed pin
change. An immutable Alpine snapshot is outside this remediation.

The Maven change is a separate commit in PR #293. At the decision point, its
required ingress job was failing on Maven and main still had the retired
libexpat pin. A separate main-based PR would fail on that pin. The combined PR
lets reviewers assess both changes on the same CI merge checkout. After protected
integration, PRs #290 and #291 can update from main and run their own checks.
