# CodeMastery - hardened PostgreSQL image (DP01).
#
# postgres:17.11-alpine3.24, selected only by this OCI digest, with su-exec
# replacing gosu. The upstream entrypoint's embedded Go gosu binary carried the
# image's only CRITICAL/HIGH findings (including CRITICAL CVE-2025-68121); the
# Alpine 3.24 OS packages have none. su-exec is a minimal C program performing
# the same setuid/setgid handoff the upstream entrypoint expects from
# `gosu postgres`. PostgreSQL major version, the data directory layout,
# healthcheck and initialization semantics are unchanged.
#
# The `layered` stage makes these changes on the pinned base; there the
# original gosu bytes stay in an inherited layer, hidden only by a whiteout. The
# final stage copies that stage's complete merged filesystem into one layer, so
# no layer of the image holds gosu (ADR003 amendment of 3 November 2026; see
# docs/deployment.md, "DP01 PostgreSQL Alpine/su-exec image hardening" and
# "DP01 final security remediation", F4).
#
# Build for the VM platform:
#   docker build --platform linux/amd64 -t <non-release tag> infrastructure/postgres
FROM postgres:17.11-alpine3.24@sha256:266d3c257594e195b309ae9d1805e54c788f1d7b375f56bf6c367aac6c917e7b AS layered

# The base image sets no labels; describe this image without inventing a
# version or source revision that a local build cannot attest.
LABEL org.opencontainers.image.title="CodeMastery PostgreSQL" \
      org.opencontainers.image.description="PostgreSQL 17.11 Alpine with su-exec replacing the gosu executable" \
      org.opencontainers.image.source="https://github.com/chi-thanh-nguyen/codemastery" \
      org.opencontainers.image.base.name="postgres:17.11-alpine3.24" \
      org.opencontainers.image.base.digest="sha256:266d3c257594e195b309ae9d1805e54c788f1d7b375f56bf6c367aac6c917e7b"

# Pinned to the reviewed package from Alpine v3.24/main (aports 3fd3d4a8), verified
# by apk against the signing keys in the pinned base. x86_64: index checksum
# Q1qSe5GhOmAg5kGDr0aUxb07P589Q=, package sha256 4003163f…69be5. The build fails if
# the index no longer offers exactly this version.
RUN apk add --no-cache su-exec=0.3-r0

RUN rm /usr/local/bin/gosu

RUN ln -s "$(command -v su-exec)" /usr/local/bin/gosu

# Data-safety boundary. A data directory initialized by another C library (for
# example glibc postgres:17.11-bookworm) keeps indexes ordered by that library's
# collation, which musl reads inconsistently and without any startup error.
# The server therefore starts only on an empty directory or on one carrying the
# marker this image writes after initdb; anything else is refused unchanged and
# must be migrated by logical backup and restore (docs/setup.md). Other
# commands, such as client tools, pass through to the upstream entrypoint.
RUN set -eu; \
    printf '%s\n' \
        '#!/bin/sh' \
        '# CodeMastery data-safety boundary; see infrastructure/postgres/Dockerfile.' \
        'set -eu' \
        'case "${1:-}" in' \
        '    postgres|-*) ;;' \
        '    *) exec docker-entrypoint.sh "$@" ;;' \
        'esac' \
        'data="${PGDATA:-/var/lib/postgresql/data}"' \
        'if [ -d "$data" ] && [ -n "$(ls -A "$data" 2>/dev/null || echo unreadable)" ] \' \
        '        && [ "$(cat "$data/codemastery-cluster-libc" 2>/dev/null)" != musl ]; then' \
        '    echo "codemastery-postgres: refusing to start: $data holds data this image did not initialize." >&2' \
        '    echo "codemastery-postgres: a cluster from another C library (for example glibc postgres:17.11-bookworm)" >&2' \
        '    echo "codemastery-postgres: has indexes that musl would read inconsistently. Nothing was changed;" >&2' \
        '    echo "codemastery-postgres: migrate it by logical backup and restore into a new volume (docs/setup.md)." >&2' \
        '    exit 78' \
        'fi' \
        'exec docker-entrypoint.sh "$@"' \
        > /usr/local/bin/codemastery-postgres-entrypoint; \
    chmod 0755 /usr/local/bin/codemastery-postgres-entrypoint; \
    printf '%s\n' \
        '# CodeMastery: marks a cluster initialized by this image; see infrastructure/postgres/Dockerfile.' \
        'echo musl > "$PGDATA/codemastery-cluster-libc"' \
        > /docker-entrypoint-initdb.d/00-codemastery-cluster-libc.sh

# Setting ENTRYPOINT clears the inherited CMD, so the upstream default is restated.
ENTRYPOINT ["codemastery-postgres-entrypoint"]
CMD ["postgres"]

# Byte-clean image: the merged filesystem above, owners and modes included.
# FROM scratch starts with no configuration, so the layered stage's is restated;
# CI checks that it is identical except for GOSU_VERSION and the upstream
# build-time DOCKER_PG_LLVM_DEPS, which describe nothing left in the image.
FROM scratch
COPY --from=layered / /
LABEL org.opencontainers.image.title="CodeMastery PostgreSQL" \
      org.opencontainers.image.description="PostgreSQL 17.11 Alpine with su-exec replacing the gosu executable" \
      org.opencontainers.image.source="https://github.com/chi-thanh-nguyen/codemastery" \
      org.opencontainers.image.base.name="postgres:17.11-alpine3.24" \
      org.opencontainers.image.base.digest="sha256:266d3c257594e195b309ae9d1805e54c788f1d7b375f56bf6c367aac6c917e7b"
ENV PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin \
    LANG=en_US.utf8 \
    PG_MAJOR=17 \
    PG_VERSION=17.11 \
    PG_SHA256=dd27f2b3c59e73ed14aa3324901242bf69a032a6347805f274e6260322d42979 \
    PGDATA=/var/lib/postgresql/data
VOLUME /var/lib/postgresql/data
WORKDIR /
STOPSIGNAL SIGINT
EXPOSE 5432
ENTRYPOINT ["codemastery-postgres-entrypoint"]
CMD ["postgres"]
