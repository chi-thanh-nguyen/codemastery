# ADR003: PostgreSQL Image Hardening — Alpine 3.24 with su-exec Replacing gosu

## Status and authority

LEADER_APPROVED. The Project Leader @chi-thanh-nguyen explicitly authorized this
decision on 2 November 2026 (Asia/Ho_Chi_Minh), directly in the authorizing task,
after reviewing the vulnerability investigation summarized below. This ADR
records that authorization; it does not retroactively authorize any other
pending or deferred decision in this repository, and it does not grant
production deployment authorization (see Scope).

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
`su-exec`, built from the new `infrastructure/postgres/Dockerfile`.

PostgreSQL major version (17), database names, environment variable
contracts, the `/var/lib/postgresql/data` volume layout, the healthcheck,
Flyway migrations/schema, runtime role and grant separation, and private
networking (no published PostgreSQL port) are unchanged.

## Rationale

The prior image, `postgres:17.11-bookworm`, scanned (Trivy, 1 November 2026)
at 4 CRITICAL and 94 HIGH findings: Debian 12 OS packages mostly without an
available fix, and `gosu`'s embedded Go 1.24.6 standard library (26 of those
findings, including CRITICAL CVE-2025-68121). `postgres:17.11-alpine3.24` has
zero CRITICAL/HIGH findings in its Alpine OS packages; only `gosu` remained.
`su-exec` (Alpine's own minimal C program, ~60 lines) performs the same
`setuid`/`setgid` handoff the upstream entrypoint's `gosu postgres` invocation
expects, with no embedded language runtime to carry its own CVEs. The final
image scans at 0 CRITICAL, 0 HIGH, 0 at any severity.

## Alternatives considered

- **`apt upgrade` on the Bookworm base.** Rejected: three packages
  (`libsqlite3-0`, `libxml2`, `zlib1g`) have no newer candidate in the
  configured Bookworm repositories; only three other packages were
  upgradable. This does not address the `gosu` findings at all.
- **`postgres:17.11-trixie`.** Rejected: still 2 CRITICAL, 88 HIGH (same
  `gosu` problem; a newer Debian base does not remove it).
- **A from-scratch/scratch-rebuilt PostgreSQL image** (as
  `infrastructure/vm/Dockerfile.minio-server` attempts for MinIO). Rejected
  for this decision: PostgreSQL's runtime has materially more files (locale
  data, shared libraries, `docker-entrypoint.sh`, initialization scripts)
  than a single static Go binary, making a full rebuild a larger, separately
  scoped effort. The layered `su-exec` patch meets the CVE target without it.

## Scope

**Approved:** the Dockerfile and its build; local Compose, Testcontainers and
VM-release Compose-override wiring to this image; the corresponding
`deploy.sh` preflight policy update (`postgres`'s `allowed_service_options`
now also permits `build`).

**Not approved by this ADR:**

- Production deployment to the Azure VM. No image was built, loaded or run
  there. Packaging the release archive with this image remains a separate
  release-readiness step.
- Migration of an existing PostgreSQL data volume from a Bookworm (glibc)
  deployment to this Alpine (musl) image. No such volume currently exists (no
  release has run on the VM). Alpine/musl does not implement distinct named
  locales the way glibc does; a fresh cluster reports the same `en_US.utf8`
  label as Debian but sorts by byte order, and mounting an existing
  glibc-initialized volume into this image is not verified safe. Should a
  real migration ever be needed, it requires a separately reviewed
  backup/restore (`pg_dump`/`pg_restore`) procedure, never a direct volume
  mount across this change.
- A byte-level-clean OCI artifact. The original `gosu` binary remains present
  (inert, not runtime-reachable) in the image's inherited OCI layers, hidden
  from the merged filesystem view by a whiteout rather than erased; see the
  deployment.md record for the layer-by-layer evidence. Accepted as a known
  residual risk for this decision.
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
`infrastructure/vm/deploy.sh`, `backend/src/test/java/com/codemastery/integration/AbstractIntegrationTest.java`.

## Deferred

Production packaging and VM deployment of this image; any future existing-
volume migration procedure; a from-scratch PostgreSQL rebuild eliminating the
inherited `gosu` layer entirely; Trivy CI automation.
