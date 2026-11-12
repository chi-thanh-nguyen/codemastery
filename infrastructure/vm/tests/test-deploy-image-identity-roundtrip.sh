#!/usr/bin/env bash
# CI regression test for job "VM deployment script" (deployment-script),
# step "Check image identity through a real save/load round trip" in .github/workflows/ci-vm.yml.
# Body moved verbatim from that step's former inline `run:` script.
# CI runs it from the repository root, where its relative paths resolve:
#   bash infrastructure/vm/tests/test-deploy-image-identity-roundtrip.sh
set -euo pipefail
script=infrastructure/vm/deploy.sh
work="$(mktemp -d)"
prefix="codemastery-ci-fixture-${GITHUB_RUN_ID:-local}-$$"
cleanup() {
  docker rm -f "$prefix-backend" "$prefix-frontend" "$prefix-minio" >/dev/null 2>&1 || true
  docker image rm -f "$prefix/backend:r1" "$prefix/frontend:r1" >/dev/null 2>&1 || true
  rm -rf "$work"
}
trap cleanup EXIT
printf 'fixture\n' > "$work/payload"
printf 'FROM scratch\nCOPY payload /payload\nCMD ["/payload"]\n' > "$work/backend.Dockerfile"
printf 'FROM scratch\nCOPY payload /payload\nHEALTHCHECK CMD ["/payload"]\nCMD ["/payload"]\n' > "$work/frontend.Dockerfile"
for service in backend frontend; do
  docker build -q --platform linux/amd64 -f "$work/$service.Dockerfile" \
    -t "$prefix/$service:r1" "$work" >/dev/null
done
bash -c '
  set -euo pipefail
  source "$1"; prefix="$2"; work="$3"
  detect_image_store
  echo "Docker image store: $IMAGE_STORE"
  be="$prefix/backend:r1"; fe="$prefix/frontend:r1"
  be_id="$(docker image inspect --format "{{.Id}}" "$be")"
  fe_id="$(docker image inspect --format "{{.Id}}" "$fe")"
  EXPECT_IMAGES=("$be=$be_id" "$fe=$fe_id")
  RELEASE_IMAGES="$(printf "%s\n" "$be" "$fe")"
  COMPOSE_MODEL="$(python3 -c "import json,sys; b,f=sys.argv[1:]; print(json.dumps({\"services\": {
    \"postgres\": {\"image\": b}, \"minio\": {\"image\": b}, \"minio-init\": {\"image\": b},
    \"backend\": {\"image\": b}, \"frontend\": {\"image\": f}}}))" "$be" "$fe")"
  ARCHIVE="$work/release.tar"
  docker save -o "$ARCHIVE" "$be" "$fe"
  ARCHIVE_SHA256="$(sha256sum "$ARCHIVE" | awk "{print \$1}")"
  verify_archive
  docker image rm "$be" "$fe" >/dev/null
  docker load -i "$ARCHIVE" >/dev/null
  verify_images
  docker create --name "$prefix-backend" "$be" >/dev/null
  docker create --name "$prefix-frontend" "$fe" >/dev/null
  be_cid="$(docker inspect --format "{{.Id}}" "$prefix-backend")"
  fe_cid="$(docker inspect --format "{{.Id}}" "$prefix-frontend")"
  verify_service_container backend "$be_cid"
  verify_service_container frontend "$fe_cid"
  echo "Archive, loaded image and container identities agree."
  if (verify_service_container backend "$fe_cid") >/dev/null 2>&1; then
    echo "A frontend container was accepted for the backend service." >&2; exit 1
  fi
  if (EXPECT_IMAGES=("$be=$fe_id" "$fe=$be_id"); verify_archive) >/dev/null 2>&1; then
    echo "A swapped image mapping was accepted." >&2; exit 1
  fi
  if (EXPECT_IMAGES=("$be=sha256:$(printf "0%.0s" {1..64})" "$fe=$fe_id"); verify_images) >/dev/null 2>&1; then
    echo "An incorrect image identity was accepted." >&2; exit 1
  fi
  other=containerd; [[ "$IMAGE_STORE" != containerd ]] || other=graphdriver
  if (IMAGE_STORE="$other"; verify_archive) >/dev/null 2>&1; then
    echo "Archive identities were accepted for the wrong image store." >&2; exit 1
  fi
  echo "Mismatched services, identities and image-store digest kinds are rejected."
  # A MinIO container must carry exactly the modelled MINIO_* settings.
  COMPOSE_MODEL="$(python3 -c "import json,sys; print(json.dumps({\"services\": {\"minio\": {\"image\": sys.argv[1],
    \"environment\": {\"MINIO_ROOT_USER\": \"ci-root\", \"MINIO_ROOT_PASSWORD\": \"ci-pass\", \"MINIO_BROWSER\": \"off\"}}}}))" "$be")"
  minio_container() {  # accepted|rejected docker-create-options...
    local want="$1" result=accepted; shift
    docker create --name "$prefix-minio" "$@" "$be" >/dev/null
    (verify_service_container minio "$(docker inspect --format "{{.Id}}" "$prefix-minio")") >/dev/null 2>&1 || result=rejected
    docker rm "$prefix-minio" >/dev/null
    [[ "$result" == "$want" ]] || { echo "MinIO container unexpectedly $result: $*" >&2; exit 1; }
  }
  root=(-e MINIO_ROOT_USER=ci-root -e MINIO_ROOT_PASSWORD=ci-pass)
  minio_container accepted "${root[@]}" -e MINIO_BROWSER=off
  minio_container rejected "${root[@]}"
  minio_container rejected "${root[@]}" -e MINIO_BROWSER=on
  minio_container rejected "${root[@]}" -e MINIO_BROWSER=off -e MINIO_CONFIG_ENV_FILE=/data/.minio.env
  echo "MinIO containers without MINIO_BROWSER=off or with extra MINIO_* settings are rejected."
' _ "$script" "$prefix" "$work"
