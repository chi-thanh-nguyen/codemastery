# CodeMastery - MinIO storage initializer image (DP01).
#
# Runs infrastructure/minio/minio-init.sh with the MinIO Client from the same
# digest-pinned Community build used by the local Compose initializer. The image
# supplies its own entrypoint because the VM Compose override resets the local
# inline entrypoint and command. Build for the VM platform:
#   docker build --platform linux/amd64 -t <non-release tag> infrastructure/minio
# Qualification: infrastructure/minio/test-minio-init.sh (see docs/deployment.md).
FROM ghcr.io/golithus/mc:RELEASE.2025-08-13T08-35-41Z@sha256:dde55ccc0bc9e65dc582c8e652d652eea1438c6e60d774524f4da15d7b26a900

# The inherited labels describe the base mc image; restate this image's identity.
LABEL org.opencontainers.image.title="CodeMastery MinIO initializer" \
      org.opencontainers.image.description="One-shot bucket and bucket-scoped application identity provisioning for CodeMastery" \
      org.opencontainers.image.source="https://github.com/chi-thanh-nguyen/codemastery" \
      org.opencontainers.image.version="" \
      org.opencontainers.image.revision="" \
      org.opencontainers.image.base.name="ghcr.io/golithus/mc:RELEASE.2025-08-13T08-35-41Z" \
      org.opencontainers.image.base.digest="sha256:dde55ccc0bc9e65dc582c8e652d652eea1438c6e60d774524f4da15d7b26a900"

# Root-owned and read-only, so the runtime user cannot replace it.
COPY --chmod=0555 minio-init.sh /usr/local/bin/codemastery-minio-init

# Unprivileged numeric identity; the Compose contract forbids a `user:` override.
USER 65532:65532
ENTRYPOINT ["/usr/local/bin/codemastery-minio-init"]
CMD []
