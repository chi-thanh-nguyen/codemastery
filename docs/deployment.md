# CodeMastery Deployment Preparation

## Current status and reproduction summary

Status as inspected on 31 October 2026 at `main`
`614f3260a2df640dcfa47c166e4a5325f56bd1a7`. This summary describes what exists; it
approves nothing. Approvals, authority boundaries and evidence are in the dated
records below, which keep the wording of their date. Changes in open pull
requests are not described here.

The 1 November repository integration below combines the Chainguard MinIO
qualification and the private health-probe remediation. MinIO is functionally
qualified but not production-admitted; the backend probe is fixed and tested in
the repository/CI, but not yet verified on the Azure VM. The dated records retain
their original evidence and limitations; DP01 remains incomplete.

### What exists and what has been shown

| Level | Status | Evidence |
| --- | --- | --- |
| Design and approval status | The one-VM Docker Compose deployment is approved (ADR002 CT17.I-scope and the approved architecture). The frontend Nginx as the only public HTTPS ingress, and the TLS bindings, are recorded in the dated CT17 records as `APPROVED_PENDING_PROTECTED_INTEGRATION`. Provider, VM, TLS/DNS, secrets, production origin and public access remain Leader-reserved (ADR002 CT17.I-runtime is `PROPOSED_LEADER_RESERVED`) | [B2](#b2--approved-engineering-ingressconnection-bindings), [DP01 HTTPS ingress integration](#dp01-https-ingress-integration--31-october-2026) |
| Deployment tooling | Implemented and merged | `infrastructure/vm/deploy.sh` (PR #110), `infrastructure/vm/docker-compose.vm.yml` and the frontend `release` target (PR #118) |
| Tested configuration | The tooling passes its CI jobs on the GitHub runner, against real containers but not the VM | Six `VM …` jobs, see [testing.md](testing.md#ci). The five deployment-script and ingress jobs passed on `614f326` in [run 41191743079](https://github.com/chi-thanh-nguyen/codemastery/actions/runs/41191743079); the MinIO initializer job is described in [DP01 MinIO Community initializer](#dp01-minio-community-initializer--31-october-2026) and [DP01 Chainguard MinIO release-server candidate](#dp01-chainguard-minio-release-server-candidate--1-november-2026) |
| Actual deployment | **None recorded.** No `preflight`, `load`, `provision` or `deploy` on the VM is recorded in this repository | — |
| External / lecturer access | **Not verified.** No independently verified public URL exists | DP03 #72 is open |

The Leader has reported a provisioned Azure VM, an intended origin and a TLS
certificate layout ([inputs](#authority-inputs-and-boundary)). They are reported
inputs, not verified results, and do not authorize public exposure.

### Deployment artifacts

| Path | Responsibility |
| --- | --- |
| `infrastructure/docker-compose.yml` | The local base file, used as is; `deploy.sh` always applies it first |
| `infrastructure/vm/docker-compose.vm.yml` | Release override: removes local ports and builds, publishes only the frontend on 443, mounts the TLS directory read-only, sets the backend release environment and fixes the `codemastery.invalid/*:release` image names |
| `frontend/Dockerfile`, `release` target | Builds the bundle with `VITE_API_BASE_URL=/api`, terminates TLS, proxies `/api` to the backend and bakes an HTTPS healthcheck. Requires the `PUBLIC_ORIGIN` build argument |
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
| `CORS_ALLOWED_ORIGINS` | Explicit actual approved HTTPS browser origin; no wildcard, localhost production default or cookie-based session inference. |
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
APPROVED_PENDING_PROTECTED_INTEGRATION.** Under the Project Leader
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
APPROVED_PENDING_PROTECTED_INTEGRATION.** Authority is the Project Leader's
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

All new policy remains APPROVED_PENDING_PROTECTED_INTEGRATION. The sole-Leader
CODEOWNER/PR-authorship conflict is unchanged: a non-owner review or another
contributor's push alone cannot satisfy it. Eligible independent human review,
last-push approval, required checks and compliant exact-revision integration are
mandatory; no bypass, review submission or personal Leader acceptance is claimed.

## CT17 consolidation and bounded Final approvals — 25 October 2026

### Authority and preserved history

**CT17-FINAL-2026-10-25-r1: DELEGATED_TECHNICAL_APPROVAL;
APPROVED_PENDING_PROTECTED_INTEGRATION.** Under the Leader's explicit
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
APPROVED_PENDING_PROTECTED_INTEGRATION** for B1–B3 below only. The Leader's explicit
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
**APPROVED_PENDING_PROTECTED_INTEGRATION**, subject to eligible independent
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
APPROVED_PENDING_PROTECTED_INTEGRATION** for checkpoints
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
APPROVED_PENDING_PROTECTED_INTEGRATION. Concrete bindings remain
PENDING_LEADER_INPUT, and runtime verification is pending.
`docs/deployment.md` and `.github/workflows/` have the Leader as sole CODEOWNER;
`infrastructure/` adds @tihh647-ui. The existing sole-Leader CODEOWNER/authorship
conflict is unchanged: eligible independent human review, last-push approval,
required checks and protected merge remain mandatory. The investigation, this
record, the implementation and its tests were produced through AI-assisted
development under the delegation. No provisioning, deployment, exposure or merge was
performed or authorized.

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
  `nginxinc/nginx-unprivileged:1.30.5-alpine` variant: same publisher and Nginx
  version, adding `curl` and keeping the `wget` that `deploy.sh`'s private backend
  probe uses. Timings reuse the local Compose healthcheck values.
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

The required tags are fixed by the override. Every image must be linux/amd64 (B1),
whatever the build host's own architecture. The recipe needs Docker Engine 28 or
later on the build host, for `docker save --platform`. It follows the archive flow
that CI job “VM deployment containerd image store” verifies.

```bash
docker build --platform linux/amd64 -t codemastery.invalid/backend:release ./backend
docker build --platform linux/amd64 --target release \
  --build-arg PUBLIC_ORIGIN=https://codemastery.eastasia.cloudapp.azure.com \
  -t codemastery.invalid/frontend:release ./frontend
# A registry image holds only the build host's platform until amd64 is fetched.
docker pull --platform linux/amd64 postgres:17.11-bookworm
# minio and minio-init: only qualified artifacts may be tagged with these names.
# The initializer is built from infrastructure/minio under a non-release tag and
# qualified there first; no MinIO server artifact is qualified (release blocker 2).
docker save --platform linux/amd64 -o release.tar postgres:17.11-bookworm \
  codemastery.invalid/minio:release codemastery.invalid/minio-init:release \
  codemastery.invalid/backend:release codemastery.invalid/frontend:release
shasum -a 256 release.tar   # --archive-sha256 (sha256sum on Linux)
```

Without the pull, an Apple Silicon build host saves only its arm64 PostgreSQL
variant. Without `--platform`, `docker save` writes whichever platforms the host
holds. `deploy.sh` rejects such an archive: "Archive does not contain exactly the
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
2. **Storage artifacts.** The Chainguard server candidate is functionally
   qualified, and the initializer image definition and qualification suite exist,
   but neither production artifact has been admitted. Security and maintenance
   limitations and the admission gates remain; see
   [DP01 MinIO Community initializer](#dp01-minio-community-initializer--31-october-2026)
   and [Chainguard candidate](#dp01-chainguard-minio-release-server-candidate--1-november-2026).
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
