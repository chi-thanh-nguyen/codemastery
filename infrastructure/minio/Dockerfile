# CodeMastery - MinIO storage initializer image (DP01).
#
# Runs infrastructure/minio/minio-init.sh with Chainguard's MinIO Client image,
# selected only by this OCI index digest. It builds the same mc source commit
# (7394ce0, RELEASE.2025-08-13T08-35-41Z) as the local Compose initializer's
# Golithus image, with a current Go toolchain, and provides the bash and GNU
# coreutils the script uses. The image supplies its own entrypoint because the
# VM Compose override resets the local inline entrypoint and command. Build for
# the VM platform:
#   docker build --platform linux/amd64 -t <non-release tag> infrastructure/minio
# Qualification: infrastructure/minio/test-minio-init.sh (see docs/deployment.md).
FROM cgr.dev/chainguard/minio-client@sha256:3b67177c0e12f44f646a5cf21b20fc5a79dd2d7a6b4db632b436f0b9e2e46717

# The inherited labels describe the base mc image; restate this image's identity.
LABEL org.opencontainers.image.title="CodeMastery MinIO initializer" \
      org.opencontainers.image.description="One-shot bucket and bucket-scoped application identity provisioning for CodeMastery" \
      org.opencontainers.image.source="https://github.com/chi-thanh-nguyen/codemastery" \
      org.opencontainers.image.version="" \
      org.opencontainers.image.revision="" \
      org.opencontainers.image.base.name="cgr.dev/chainguard/minio-client" \
      org.opencontainers.image.base.digest="sha256:3b67177c0e12f44f646a5cf21b20fc5a79dd2d7a6b4db632b436f0b9e2e46717"

# Root-owned and read-only, so the runtime user cannot replace it.
COPY --chmod=0555 minio-init.sh /usr/local/bin/codemastery-minio-init

# Unprivileged numeric identity; the Compose contract forbids a `user:` override.
USER 65532:65532
ENTRYPOINT ["/usr/local/bin/codemastery-minio-init"]
CMD []
