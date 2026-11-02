# CodeMastery - hardened PostgreSQL image (DP01).
#
# postgres:17.11-alpine3.24, selected only by this OCI digest, with su-exec
# replacing gosu. The upstream entrypoint's embedded Go gosu binary carried
# 26 Go-stdlib findings (including CRITICAL CVE-2025-68121) on top of the
# Debian Bookworm base image's own 66 OS-package findings; Alpine 3.24 has
# zero CRITICAL/HIGH OS-package findings, and su-exec is a minimal C program
# performing the same setuid/setgid handoff the upstream entrypoint expects
# from `gosu postgres`. PostgreSQL major version, the data directory layout,
# healthcheck and initialization semantics are unchanged.
#
# Removing gosu and creating the symlink in separate layers is required: a
# single combined layer still carries the original gosu bytes in that
# layer's diff, and a layer-aware scanner finds them. See docs/deployment.md,
# "DP01 PostgreSQL Alpine/su-exec candidate", for the qualification scan and
# the residual-risk assessment of the original binary inside this image's
# inherited (not newly added) OCI layers.
#
# Build for the VM platform:
#   docker build --platform linux/amd64 -t <non-release tag> infrastructure/postgres
FROM postgres:17.11-alpine3.24@sha256:266d3c257594e195b309ae9d1805e54c788f1d7b375f56bf6c367aac6c917e7b

# The inherited labels describe the base image; restate this image's identity.
LABEL org.opencontainers.image.title="CodeMastery PostgreSQL" \
      org.opencontainers.image.description="PostgreSQL 17.11 Alpine with su-exec replacing the gosu executable" \
      org.opencontainers.image.source="https://github.com/chi-thanh-nguyen/codemastery" \
      org.opencontainers.image.version="" \
      org.opencontainers.image.revision="" \
      org.opencontainers.image.base.name="postgres:17.11-alpine3.24" \
      org.opencontainers.image.base.digest="sha256:266d3c257594e195b309ae9d1805e54c788f1d7b375f56bf6c367aac6c917e7b"

RUN apk add --no-cache su-exec

RUN rm /usr/local/bin/gosu

RUN ln -s "$(command -v su-exec)" /usr/local/bin/gosu
