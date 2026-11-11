# CodeMastery - MinIO storage initializer image (DP01).
#
# Runs infrastructure/minio/minio-init.sh with a project-built MinIO Client.
# Source: minio/mc commit 7394ce0 (RELEASE.2025-08-13T08-35-41Z), the commit
# that Chainguard's minio-client image and the local Compose initializer's
# Golithus image build. Only the dependency versions differ: Wolfi's recipe
# raises, plus the fixed klauspost/compress, x/net, x/crypto and etcd versions
# for the advisories in docs/deployment.md, "DP01 final security
# remediation" (F1). The runtime filesystem is the pinned Chainguard image's
# (bash and GNU coreutils for the script) without its mc, copied into one layer,
# so no layer carries the replaced binary. The image supplies its own
# entrypoint because the VM Compose override resets the local inline one.
#
# mc is AGPL-3.0-or-later, and this build is a modified version. The mc-source
# stage produces its Corresponding Source (the mc tree with the changed go.mod
# and go.sum, every module zip the build uses, and build instructions); the
# binary is built offline from exactly that archive. Export it and keep it with
# any copy of this image that is given to anyone (docs/deployment.md):
#   docker build --platform linux/amd64 --target mc-source-archive \
#     --output type=local,dest=<directory> infrastructure/minio
# Build for the VM platform:
#   docker build --platform linux/amd64 -t <non-release tag> infrastructure/minio
# Qualification: infrastructure/minio/test-minio-init.sh (see docs/deployment.md).

# --------------------------------------------------------------------- source
FROM --platform=$BUILDPLATFORM golang:1.27.3-bookworm@sha256:d9b6a1beb9474adc2a1ce842573639b44b2a0dd49b6669f51d3821baf9ec37c9 AS mc-source
ARG TARGETOS TARGETARCH
ENV CGO_ENABLED=0 GOTOOLCHAIN=local GOPROXY=https://proxy.golang.org GOSUMDB=sum.golang.org GOFLAGS=-mod=mod
WORKDIR /src/mc
RUN set -eu; \
    git init -q; \
    git fetch -q --depth 1 https://github.com/minio/mc.git 7394ce0dd2a80935aded936b09fa12cbb3cb8096; \
    git checkout -q FETCH_HEAD; \
    test "$(git rev-parse HEAD)" = 7394ce0dd2a80935aded936b09fa12cbb3cb8096
# New go.sum lines are checked against sum.golang.org; the result must be exactly
# the reviewed module graph. x/crypto is the Go vulnerability database's fixed
# version for GO-2026-5932: v0.58.0 plus one upstream commit (2f1f834) that moves
# the unmaintained openpgp packages, which mc does not use, into their own module.
RUN set -eu; \
    GOMODCACHE=/tmp/resolve go get \
        github.com/klauspost/compress@v1.18.7 golang.org/x/net@v0.62.0 \
        golang.org/x/crypto@v0.58.1-0.20261009181718-2f1f834330b2 golang.org/x/sys@v0.49.0 golang.org/x/term@v0.47.0 \
        golang.org/x/text@v0.43.0 google.golang.org/grpc@v1.83.2 \
        github.com/prometheus/prometheus@v0.311.3 github.com/prometheus/client_golang@v1.23.2 \
        go.etcd.io/etcd/client/pkg/v3@v3.5.33; \
    GOMODCACHE=/tmp/resolve go mod tidy; \
    rm -rf /tmp/resolve; \
    printf '%s\n' \
        '091e2cfcfcf221bfc0a17ac65dd0bc7204860788312e7c7711ca2b2b26a9ee27  go.mod' \
        '363a7760e2d827c5a39e00399e8141888fdb8be509e2598f77ab22ca6db02e9e  go.sum' | sha256sum -c -
# A fresh module cache holds only what the linux build loads; it becomes the
# archive's file-based module proxy. This Dockerfile goes into the archive as the
# script that fetches, builds and packages the binary.
COPY Dockerfile /out/mc-source/Dockerfile
RUN set -eu; \
    GOMODCACHE=/tmp/gomod GOFLAGS=-mod=readonly GOOS=$TARGETOS GOARCH=$TARGETARCH \
        go list -deps . >/dev/null; \
    mkdir -p /out/mc-source; \
    git diff --no-color go.mod go.sum > /out/mc-source/dependency-changes.diff; \
    rm -rf .git; \
    cp -a /src/mc /out/mc-source/mc; \
    cp -a /tmp/gomod/cache/download /out/mc-source/goproxy; \
    rm -rf /out/mc-source/goproxy/sumdb; \
    find /out/mc-source/goproxy -name '*.lock' -delete; \
    printf '%s\n' \
        'Modified MinIO Client (mc) - CodeMastery corresponding source' \
        '' \
        'mc is Copyright (c) 2015-2025 MinIO, Inc., licensed under the GNU Affero' \
        'General Public License version 3 or later (mc/LICENSE). This version was' \
        'modified by the CodeMastery project on 3 November 2026: only the dependency' \
        'versions in mc/go.mod and mc/go.sum changed (dependency-changes.diff) from' \
        'minio/mc commit 7394ce0dd2a80935aded936b09fa12cbb3cb8096. The source code is' \
        'otherwise unchanged. goproxy/ holds every module the build uses, as served' \
        'by proxy.golang.org; go.sum verifies each one. Dockerfile is the CodeMastery' \
        'build that fetched this source, wrote this archive, builds mc from it and' \
        'installs it in the initializer image.' \
        '' \
        'Build (Go 1.27.3: golang:1.27.3-bookworm@sha256:d9b6a1beb9474adc2a1ce842573639b44b2a0dd49b6669f51d3821baf9ec37c9,' \
        'linux/amd64), offline:' \
        '  cd mc' \
        '  CGO_ENABLED=0 GOTOOLCHAIN=local GOFLAGS=-mod=readonly GOSUMDB=off \' \
        '  GOPROXY=file://$PWD/../goproxy GOOS=linux GOARCH=amd64 \' \
        '  go build -trimpath -buildvcs=false \' \
        '    -ldflags "-buildid= -X github.com/minio/mc/cmd.Version=0.20250813.083541" -o mc .' \
        > /out/mc-source/README; \
    tar --sort=name --mtime=@0 --owner=0 --group=0 --numeric-owner --mode=go-w \
        -C /out -cf - mc-source | gzip -9n > /out/mc-source.tar.gz

FROM scratch AS mc-source-archive
COPY --from=mc-source /out/mc-source.tar.gz /

# ---------------------------------------------------------------------- build
# Built without network access from the archive alone, so the binary is exactly
# what the Corresponding Source produces. /usr/share/doc/mc receives the licence
# and notice texts of mc, the Go standard library and every linked module that
# ships one (colorjson and filepath carry Go's BSD licence in their file
# headers), the archive README, the binary's embedded module list (BUILDINFO) and
# SOURCE, which names the exact archive (SHA-256) the binary was built from.
FROM --platform=$BUILDPLATFORM golang:1.27.3-bookworm@sha256:d9b6a1beb9474adc2a1ce842573639b44b2a0dd49b6669f51d3821baf9ec37c9 AS mc-build
ARG TARGETOS TARGETARCH
COPY --from=mc-source /out/mc-source.tar.gz /tmp/
RUN --network=none set -eu; \
    tar -xzf /tmp/mc-source.tar.gz -C /tmp; \
    cd /tmp/mc-source/mc; \
    export CGO_ENABLED=0 GOTOOLCHAIN=local GOFLAGS=-mod=readonly GOSUMDB=off \
        GOPROXY=file:///tmp/mc-source/goproxy GOMODCACHE=/tmp/gomod GOOS=$TARGETOS GOARCH=$TARGETARCH; \
    go build -trimpath -buildvcs=false \
        -ldflags "-buildid= -X github.com/minio/mc/cmd.Version=0.20250813.083541" -o /out/mc .; \
    mkdir /out/doc; \
    cp LICENSE NOTICE CREDITS ../README /out/doc/; \
    printf '%s\n' \
        'Corresponding Source of /usr/bin/mc (modified MinIO Client, AGPL-3.0-or-later)' \
        '' \
        'This mc was built without network access only from mc-source.tar.gz with' \
        "SHA-256 $(sha256sum /tmp/mc-source.tar.gz | cut -d' ' -f1)," \
        'which holds the mc source with its changed go.mod and go.sum, every module it' \
        'links, the modification notice (README) and the build and packaging script' \
        '(Dockerfile). CodeMastery provides that archive with every copy of this image' \
        'it gives to anyone. It can be regenerated from the CodeMastery repository,' \
        'https://github.com/chi-thanh-nguyen/codemastery, at the revision that built' \
        'this image, and checked against the SHA-256 above:' \
        '  docker build --platform linux/amd64 --target mc-source-archive \' \
        '    --output type=local,dest=<directory> infrastructure/minio' \
        > /out/doc/SOURCE; \
    go version -m /out/mc | sed 's|^/out/mc:|/usr/bin/mc:|' > /out/doc/BUILDINFO; \
    { \
        printf '== Go standard library %s LICENSE\n' "$(go env GOVERSION)"; cat "$(go env GOROOT)/LICENSE"; \
        go list -deps -f '{{with .Module}}{{if .Version}}{{.Path}}@{{.Version}} {{.Dir}}{{end}}{{end}}' . | sort -u \
        | while read -r module dir; do \
            for f in $(ls "$dir" | grep -iE '^(licen[cs]e|unlicense|copying|notice|patents)([._-][a-z0-9]+)?$' \
                    | grep -viE '[.](go|py|minisig)$' | sort); do \
                printf '\n== %s %s\n' "$module" "$f"; cat "$dir/$f"; \
            done; \
        done; \
    } > /out/doc/THIRD_PARTY_NOTICES; \
    chmod 0755 /out/mc /out/doc; chmod 0444 /out/doc/*

# -------------------------------------------------------------------- runtime
FROM cgr.dev/chainguard/minio-client@sha256:3b67177c0e12f44f646a5cf21b20fc5a79dd2d7a6b4db632b436f0b9e2e46717 AS base

# The pinned base filesystem without Chainguard's mc binary and its package
# records; every other apk database entry is kept unchanged.
FROM alpine:3.24@sha256:be2656061b536c1883c4034790e21d0a09c5aaaf51968b89368a3c0371ea2c9a AS rootfs
COPY --from=base / /rootfs/
RUN set -eu; \
    rm /rootfs/usr/bin/mc /rootfs/var/lib/db/sbom/mc-0.20250813.083541-r27.spdx.json; \
    awk 'BEGIN { RS = ""; ORS = "\n\n" } $0 !~ /(^|\n)P:mc\n/' /rootfs/usr/lib/apk/db/installed > /tmp/installed; \
    cat /tmp/installed > /rootfs/usr/lib/apk/db/installed

FROM scratch
COPY --from=rootfs /rootfs/ /
COPY --from=mc-build --chown=0:0 /out/mc /usr/bin/mc
COPY --from=mc-build --chown=0:0 /out/doc/ /usr/share/doc/mc/

LABEL org.opencontainers.image.title="CodeMastery MinIO initializer" \
      org.opencontainers.image.description="One-shot bucket and bucket-scoped application identity provisioning for CodeMastery" \
      org.opencontainers.image.source="https://github.com/chi-thanh-nguyen/codemastery" \
      org.opencontainers.image.licenses="AGPL-3.0-or-later" \
      org.opencontainers.image.base.name="cgr.dev/chainguard/minio-client" \
      org.opencontainers.image.base.digest="sha256:3b67177c0e12f44f646a5cf21b20fc5a79dd2d7a6b4db632b436f0b9e2e46717"

# Root-owned and read-only, so the runtime user cannot replace it.
COPY --chmod=0555 minio-init.sh /usr/local/bin/codemastery-minio-init

# The base image's environment, restated because FROM scratch starts empty.
ENV PATH=/usr/local/sbin:/usr/local/bin:/usr/bin:/usr/sbin:/sbin:/bin \
    SSL_CERT_FILE=/etc/ssl/certs/ca-certificates.crt
# Unprivileged numeric identity; the Compose contract forbids a `user:` override.
USER 65532:65532
ENTRYPOINT ["/usr/local/bin/codemastery-minio-init"]
CMD []
