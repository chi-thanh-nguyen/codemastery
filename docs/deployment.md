# CodeMastery Deployment Preparation

## Status and scope

Planning and verification methodology under [ADR002](decisions/002-interim-delegated-contracts.md), CT17.I-scope/CT17.I-access. No actual deployment, paid service, public exposure, credentials or production access approved or performed. Retain approved one-VM Docker Compose architecture and existing infrastructure. Selected externally demonstrated capabilities are real Auth and Course catalogue/detail; local mastery results remain separately reproducible. The official lecturer-accessible prototype remains mandatory.

## Preconditions and local validation

Merge reviewed contracts, obtain separate implementation and deployment authority, implement/test selected features and provide real non-secret endpoint/configuration choices. Follow existing [setup](setup.md) and [testing](testing.md) commands; do not invent deployment scripts. Existing repository checks include backend Maven Wrapper verification, frontend npm run build and the documented Compose configuration validation. Local bindings remain loopback. This task did not run application deployment or these feature-validation commands.

## Runtime and health

Runtime secret values are supplied out of band; no secrets in documentation or VITE_* variables. Use existing /actuator/health without detail exposure, then exercise actual feature APIs/browser. Health alone does not demonstrate implementation or correct database access. Production TLS/origins/provider/access policy and rollback authorization require Leader decisions.

## External verification and evidence

An independent tester outside the developer LAN verifies the actual approved HTTPS URL, Auth/roles and browser→REST→PostgreSQL catalogue/detail, hidden404 behavior, English data and no secret disclosure. Record real URL, revision/build, UTC/ICT time, browser, tester identity, role and redacted expected/observed results. Stop on TLS/origin failure, data exposure, wrong authorization, placeholders or local-only access. Preserve failures and corrective evidence. Actual accounts/endpoints and lecturer instructions are external inputs, not invented values.

## Recovery and submission

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
28 October 2026 (ICT). It delegates to Claude Code the selection and technical
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
required checks and protected merge remain mandatory. Claude Code (Anthropic)
performed the investigation and authored this record, the implementation and its
tests under the delegation. No provisioning, deployment, exposure or merge was
performed or authorized.
