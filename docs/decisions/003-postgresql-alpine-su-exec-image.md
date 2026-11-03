# ADR003: PostgreSQL Image Hardening — Alpine 3.24 with su-exec Replacing gosu

## Status and authority

LEADER_APPROVED for the technical decision. The Project Leader @chi-thanh-nguyen
explicitly authorized this decision on 2 November 2026 (Asia/Ho_Chi_Minh),
directly in the authorizing task, after reviewing the vulnerability investigation
summarized below. This ADR records that technical authorization; it does not
retroactively authorize any other pending or deferred decision in this
repository, and it does not grant production deployment authorization (see
Scope).

Technical authorization is not risk acceptance. The residual risks in Scope
(inherited `gosu` layer bytes; glibc/musl collation) are **PENDING_LEADER_RISK_ACCEPTANCE**:
no record of an explicit acceptance exists, and this ADR does not supply one.

Sources: AGENTS.md; the approved architecture's PostgreSQL + Flyway selection
(unaffected by this decision); [docs/deployment.md, "DP01 PostgreSQL
Alpine/su-exec image hardening"](../deployment.md#dp01-postgresql-alpinesu-exec-image-hardening--2-november-2026),
which holds the full qualification evidence this ADR summarizes.

## Decision

Replace the PostgreSQL image used by local Docker Compose, backend
Testcontainers, and the VM release with a locally built image:
`postgres:17.11-alpine3.24` (pinned by OCI digest
`sha256:266d3c257594e195b309ae9d1805e54c788f1d7b375f56bf6c367aac6c917e7b`),
with the Debian/Bookworm image's vulnerable `gosu` executable replaced by
`su-exec` (pinned to the signed Alpine v3.24/main package `0.3-r0`), built from
the new `infrastructure/postgres/Dockerfile`. `su-exec` is a runtime
privilege-switching utility in the image; Trivy, used to qualify it, is a
security scanner outside the image.

PostgreSQL major version (17), database names, environment variable
contracts, the `/var/lib/postgresql/data` volume layout, the healthcheck,
Flyway migrations/schema, runtime role and grant separation, and private
networking (no published PostgreSQL port) are unchanged.

## Rationale

The prior image, `postgres:17.11-bookworm`, scans (Trivy 0.76.1, database of
1 November 2026, `linux/amd64` and `linux/arm64` alike) at 4 CRITICAL and
88 HIGH findings: Debian 12 OS packages mostly without an available fix, and
`gosu`'s embedded Go 1.24.6 standard library (1 CRITICAL, including
CVE-2025-68121, and 25 HIGH of those findings). The 4 CRITICAL / 94 HIGH figure
in the earlier 1 November record came from Trivy 0.58.1 on the arm64 image and
could not be reproduced; 4/88 is the verified baseline.
`postgres:17.11-alpine3.24` scans at 1 CRITICAL and 25 HIGH, all of them
`gosu`'s; its Alpine OS packages have none. `su-exec`, Alpine's minimal C
program, performs the same `setuid`/`setgid` handoff the upstream entrypoint's
`gosu postgres` invocation expects, with no embedded language runtime to carry
its own CVEs. The final image scans at 0 findings at any severity in Trivy's
merged-filesystem view; the original `gosu` bytes remain in an inherited layer
(see Scope).

## Alternatives considered

- **`apt upgrade` on the Bookworm base.** Rejected: three packages
  (`libsqlite3-0`, `libxml2`, `zlib1g`) have no newer candidate in the
  configured Bookworm repositories; only three other packages were
  upgradable. This does not address the `gosu` findings at all.
- **`postgres:17.11-trixie`.** Rejected: still 2 CRITICAL, 88 HIGH (26 of them
  `gosu`'s; a newer Debian base does not remove it).
- **A from-scratch/scratch-rebuilt PostgreSQL image** (as
  `infrastructure/vm/Dockerfile.minio-server` attempts for MinIO). Rejected
  for this decision: PostgreSQL's runtime has materially more files (locale
  data, shared libraries, `docker-entrypoint.sh`, initialization scripts)
  than a single static Go binary, making a full rebuild a larger, separately
  scoped effort. The layered `su-exec` patch meets the CVE target without it.

## Scope

**Approved:** the Dockerfile and its build; local Compose, Testcontainers and
VM-release Compose-override wiring to this image. The release model loads this
image only from the reviewed archive, so `deploy.sh` keeps `build` outside
`postgres`'s `allowed_service_options` and the VM override resets it (see
Amendment).

**Not approved by this ADR:**

- Production deployment to the Azure VM. No image was built, loaded or run
  there. Packaging the release archive with this image remains a separate
  release-readiness step.
- Migration of an existing PostgreSQL data volume from a Bookworm (glibc)
  deployment to this Alpine (musl) image. Whether such a volume exists on the
  VM was not verified; the VM was not inspected. A glibc-initialized volume
  mounted into this image has been shown to start silently and return
  inconsistent index-scan and sequential-scan orderings, and `amcheck` reports
  its index ordering invalid. The image therefore refuses to start the server on
  any non-empty data directory it did not initialize (see Amendment). Local
  developers migrate by the logical backup and restore in
  [setup.md](../setup.md#existing-postgresql-volumes); any VM migration still
  needs its own reviewed procedure and authorization.
- A byte-level-clean OCI artifact. The original `gosu` binary remains in the
  image's inherited base layer, hidden from the merged filesystem view by a
  whiteout rather than erased; a scanner that reads layer blobs individually
  finds it. See the deployment.md record for the layer-by-layer evidence.
- Acceptance of either residual risk above (`gosu` layer bytes; collation of
  pre-existing data). Both are **PENDING_LEADER_RISK_ACCEPTANCE**; the
  technical authorization in Status does not imply it.
- A Trivy CI job. Trivy scanning of this image remains a manual step,
  consistent with this repository's existing practice; no Trivy automation
  exists in `.github/workflows/ci.yml` today, and adding it is a separate,
  unscoped decision.

## Evidence

Full qualification results (image build/digest verification, Trivy counts,
OCI layer inspection, PostgreSQL runtime validation, CodeMastery role
provisioning compatibility, locale/collation findings, and the resolution of
the `psqlVariablesKeepCredentialsOutOfProcessArguments` integration test
failure and its root cause) are recorded in
[docs/deployment.md](../deployment.md#dp01-postgresql-alpinesu-exec-image-hardening--2-november-2026)
and [docs/testing.md](../testing.md), not duplicated here.

## Owner and consumers

Owner: Leader @chi-thanh-nguyen. Consumers: `infrastructure/postgres/`,
`infrastructure/docker-compose.yml`, `infrastructure/vm/docker-compose.vm.yml`,
`infrastructure/vm/deploy.sh`, `backend/src/test/java/com/codemastery/integration/AbstractIntegrationTest.java`,
`.github/workflows/ci.yml`.

## Amendment — 2 November 2026 audit remediation

Authority: the Leader's 2 November 2026 (ICT) task directing remediation of
the independent audit of PR #131 (verdict CHANGES_REQUIRED, reviewed head
`b4c36bb`). It supersedes only the statements below; the technical decision
and its authorization are unchanged.

- **Preflight scope.** The original Scope approved permitting `build` for
  `postgres` in `deploy.sh`. That permission let preflight accept a remote Git
  build context, privileged builds, host-network builds and insecure
  entitlements on the release model. It is withdrawn; `postgres` admits no
  build configuration, and CI proves those mutations are rejected.
- **Data-volume boundary.** Added to the image: the server starts only on an
  empty data directory or one carrying the `codemastery-cluster-libc` marker
  written after `initdb`; otherwise it exits 78 without changing the volume.
- **Corrected facts.** The verified Bookworm baseline is 4 CRITICAL / 88 HIGH,
  not 4/94; the original "no release has run on the VM" statement was not
  verified; the residual risks were recorded as accepted without an acceptance
  record and are now pending (see Status).

## Deferred

Production packaging and VM deployment of this image; any VM volume migration
procedure; explicit acceptance or rejection of the residual risks; Trivy CI
automation; adoption of a byte-clean image without the inherited `gosu` layer.
A disposable flattened (`FROM scratch`) experiment removed every copy of the
`gosu` bytes and passed the scan and data-volume checks; deployment.md records
it and its trade-offs. It is not adopted and needs a Leader decision.

## Amendment — 3 November 2026: byte-clean image

Authority: the Project Leader's task of 3 November 2026 (ICT), "PR #132 Final
Security Remediation and Delegated Technical Authorization", item F4. It
authorizes adopting a qualified byte-clean PostgreSQL image in place of the
layered artifact, if it preserves the approved PostgreSQL contract and the
data-safety properties, and this narrowly scoped amendment recording it. This
amendment records that technical decision only. It has not been independently
reviewed or merged at the time of writing, and it supersedes only the
statements named below; the rest of this ADR, including the 2 November
amendment, is unchanged.

**Decision.** `infrastructure/postgres/Dockerfile` keeps the approved changes
above as its `layered` stage (same base digest, `su-exec` pin, symlink and
data-volume boundary). Its final stage, `FROM scratch` with
`COPY --from=layered / /`, ships that stage's complete merged filesystem as one
layer, and restates its configuration: user, entrypoint, command, volume, port,
working directory, `STOPSIGNAL SIGINT`, labels and environment. Only
`GOSU_VERSION` and the upstream build-time `DOCKER_PG_LLVM_DEPS` are dropped;
they describe nothing left in the image. No file list is maintained by hand.

- Previous artifact: 14 layers; layer 2 (inherited from the base) still holds
  the 1,769,900-byte upstream `gosu` (SHA-256 `52c8749d…`, go1.24.6), hidden by
  a whiteout.
- Replacement: 1 layer; no file in it is that binary or contains its module
  path, and no whiteout is needed.

**Alternatives.** Keeping the layered image and accepting the layer bytes
(rejected: the Leader preferred removal, and removal preserves the contract);
rebuilding the filesystem from a hand-maintained file list (rejected:
an incomplete list could silently drop locale, library or initialization files);
`docker build --squash` or `docker export`/`import` (rejected: classic-builder
only or configuration-losing, and not reproducible in CI and Testcontainers).

**Qualification.** Recorded in
[docs/deployment.md, "DP01 final security remediation", F4](../deployment.md#f4-3-november--byte-clean-postgresql-adopted):
merged filesystem and configuration equal to the layered stage (BuildKit and
the classic builder Testcontainers uses); Trivy 0 in both detection modes; the
exit-78 refusal of a glibc volume and the fresh-volume marker, restart and
persistence checks; `./mvnw verify` through Testcontainers; and the hardened
five-image deployment path. A CI step now fails the build if the configuration
or merged filesystem drifts from the layered stage, if the image has more than
one layer, or if any layer holds `gosu`.

**Superseded statements.** In Scope, "Not approved by this ADR: A byte-level-clean
OCI artifact"; in Deferred, "adoption of a byte-clean image without the
inherited `gosu` layer". The residual risk "inherited `gosu` layer bytes" no
longer describes the image this Dockerfile builds.

**Trade-offs.** The shipped image no longer exposes the base's layer identities,
so its relation to the pinned base is established by the build and the CI
equivalence check rather than by shared layers. Its image ID differs from the
layered artifact's and needs its own qualification and admission.

**Still separately controlled.** Production deployment and release packaging;
any migration of an existing (for example glibc-initialized) data volume; the
glibc/musl collation residual risk, which stays
**PENDING_LEADER_RISK_ACCEPTANCE**; and security admission of the image.
