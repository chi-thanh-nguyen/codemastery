#!/usr/bin/env bash
# CI regression test for job "VM deployment containerd image store" (deployment-script-containerd),
# step "Verify registry archives through real save/load round trips" in .github/workflows/ci-vm.yml.
# Body moved verbatim from that step's former inline `run:` script.
# CI runs it from the repository root, where its relative paths resolve:
#   bash infrastructure/vm/tests/test-containerd-archive-roundtrip.sh
set -euo pipefail
script=infrastructure/vm/deploy.sh
work="$(mktemp -d)"
prefix="codemastery-ci-containerd-${GITHUB_RUN_ID:-local}-$$"
cleanup() {
  docker ps -aq --filter "name=$prefix" | xargs -r docker rm -f >/dev/null 2>&1 || true
  docker image ls --format '{{.Repository}}:{{.Tag}}' | grep "^$prefix/" | xargs -r docker image rm -f >/dev/null 2>&1 || true
  rm -rf "$work"
}
trap cleanup EXIT
# The base Compose file's registry images: the digest-pinned multi-platform
# MinIO server and client. They remain local-only pins; this exercises
# their real registry index and attestation structure, not their
# production admission. PostgreSQL is built locally from its Dockerfile.
mapfile -t registry < <(sed -n 's/^    image: //p' infrastructure/docker-compose.yml)
[[ ${#registry[@]} -eq 2 ]] || { echo "Unexpected base Compose image list." >&2; exit 1; }
for ref in "${registry[@]}"; do docker pull -q "$ref" >/dev/null; done
printf 'fixture\n' > "$work/payload"
printf 'FROM scratch\nCOPY payload /payload\nCMD ["/payload"]\n' > "$work/backend.Dockerfile"
printf 'FROM scratch\nCOPY payload /payload\nHEALTHCHECK CMD ["/payload"]\nCMD ["/payload"]\n' > "$work/frontend.Dockerfile"
for service in backend frontend; do
  docker build -q --platform linux/amd64 -f "$work/$service.Dockerfile" -t "$prefix/$service:r1" "$work" >/dev/null
done
# Disposable names, so removing and reloading exercises the real store.
refs=("$prefix/postgres:r1")
docker build -q --platform linux/amd64 -t "${refs[0]}" infrastructure/postgres >/dev/null
for i in 0 1; do
  service=(minio minio-init); refs+=("$prefix/${service[$i]}:r1")
  docker tag "${registry[$i]}" "${refs[$((i + 1))]}"
done
refs+=("$prefix/backend:r1" "$prefix/frontend:r1")
cat > "$work/structure.py" <<'PY'
import json, sys, tarfile
# Prove the archive has the structure the old check rejected.
t = tarfile.open(sys.argv[1]); names = set(t.getnames()); mode = sys.argv[2]
tops = json.load(t.extractfile("index.json"))["manifests"]
if mode == "default":
    index = next(m for m in tops if m["mediaType"].endswith("image.index.v1+json")
                 and "/minio:" in (m.get("annotations") or {}).get("io.containerd.image.name", ""))
    children = json.loads(t.extractfile("blobs/sha256/" + index["digest"][7:]).read())["manifests"]
    runnable = [c for c in children
                if (c.get("annotations") or {}).get("vnd.docker.reference.type") != "attestation-manifest"]
    absent = [c for c in runnable if "blobs/sha256/" + c["digest"][7:] not in names]
    assert len(runnable) > 1 and absent, "MinIO index is not a partial multi-platform save"
    print(f"MinIO index: {len(runnable)} platforms listed, {len(absent)} absent from the archive")
else:
    # Docker 29 writes attestations as top-level referrers; Docker 28
    # does not. Both layouts are valid; synthetic fixtures cover both.
    referrers = [m for m in tops if "io.containerd.manifest.subject" in (m.get("annotations") or {})]
    print(f"--platform save: {len(referrers)} top-level attestation referrers")
PY
cat > "$work/ids.py" <<'PY'
import json, sys, tarfile
# Image IDs a --platform archive will produce: its named top-level manifests.
t = tarfile.open(sys.argv[1])
named = {(m.get("annotations") or {}).get("io.containerd.image.name"): m["digest"]
         for m in json.load(t.extractfile("index.json"))["manifests"]}
for ref in sys.argv[2:]:
    print(f"{ref}={named['docker.io/' + ref]}")
PY
cat > "$work/mutate.py" <<'PY'
import io, json, sys, tarfile
# Real-archive mutations: drop MinIO's amd64 manifest, or alter a referrer.
source, target, mode = sys.argv[1:]
NAME, SUBJECT = "io.containerd.image.name", "io.containerd.manifest.subject"
with tarfile.open(source) as t:
    index = json.load(t.extractfile("index.json"))
    skip = set()
    if mode == "drop-amd64":
        top = next(m for m in index["manifests"] if "/minio:" in (m.get("annotations") or {}).get(NAME, ""))
        children = json.loads(t.extractfile("blobs/sha256/" + top["digest"][7:]).read())["manifests"]
        amd64 = next(c for c in children if (c.get("platform") or {}).get("architecture") == "amd64")
        skip.add("blobs/sha256/" + amd64["digest"][7:])
    else:
        referrer = next((m for m in index["manifests"] if SUBJECT in (m.get("annotations") or {})), None)
        if referrer is None:
            sys.exit(0)  # This Engine wrote no referrer; no mutated archive.
        if mode == "referrer-subject":
            referrer["annotations"][SUBJECT] = "sha256:" + "1" * 64
        else:
            referrer["annotations"][NAME] = "docker.io/library/unreviewed:latest"
    out = tarfile.open(target, "w")
    for member in t.getmembers():
        if member.name in skip:
            continue
        data = t.extractfile(member).read() if member.isfile() else None
        if member.name == "index.json":
            data = json.dumps(index).encode()
            member.size = len(data)
        out.addfile(member, io.BytesIO(data) if data is not None else None)
    out.close()
PY
bash -c '
  set -euo pipefail
  source "$1"; work="$2"; shift 2; refs=("$@")
  services=(postgres minio minio-init backend frontend)
  detect_image_store
  [[ "$IMAGE_STORE" == containerd ]] || { echo "Expected the containerd image store." >&2; exit 1; }
  COMPOSE_MODEL="$(python3 -c "import json,sys; s=sys.argv[1:]; print(json.dumps({\"services\": {s[i]: {\"image\": s[i+1]} for i in range(0, len(s), 2)}}))" \
    postgres "${refs[0]}" minio "${refs[1]}" minio-init "${refs[2]}" backend "${refs[3]}" frontend "${refs[4]}")"
  RELEASE_IMAGES="$(printf "%s\n" "${refs[@]}")"
  current_ids() { local ref; for ref in "${refs[@]}"; do printf "%s=%s\n" "$ref" "$(docker image inspect --format "{{.Id}}" "$ref")"; done; }
  remove_release() { local ref; for ref in "${refs[@]}"; do docker image rm -f "$(docker image inspect --format "{{.Id}}" "$ref")" >/dev/null; done; }
  check_archive() { ARCHIVE="$1"; ARCHIVE_SHA256="$(sha256sum "$ARCHIVE" | awk "{print \$1}")"; verify_archive; }
  rejects() { if ("$@") >/dev/null 2>&1; then echo "Accepted: $*" >&2; exit 1; fi; }
  containers() {
    local i cid
    for i in "${!services[@]}"; do
      cid="$(docker create "${refs[$i]}")"
      verify_service_container "${services[$i]}" "$cid"
      docker rm "$cid" >/dev/null
    done
  }

  mapfile -t default_ids < <(current_ids)
  docker save -o "$work/default.tar" "${refs[@]}"
  python3 "$work/structure.py" "$work/default.tar" default
  EXPECT_IMAGES=("${default_ids[@]}"); check_archive "$work/default.tar"
  remove_release; docker load -i "$work/default.tar" >/dev/null
  verify_images; containers
  echo "Default save: archive, loaded image and container identities agree."

  docker save --platform linux/amd64 -o "$work/platform.tar" "${refs[@]}"
  python3 "$work/structure.py" "$work/platform.tar" platform
  mapfile -t platform_ids < <(python3 "$work/ids.py" "$work/platform.tar" "${refs[@]}")
  EXPECT_IMAGES=("${platform_ids[@]}"); check_archive "$work/platform.tar"
  remove_release; docker load -i "$work/platform.tar" >/dev/null
  verify_images; containers
  echo "--platform save: archive, loaded image and container identities agree."

  EXPECT_IMAGES=("${default_ids[@]}"); rejects check_archive "$work/platform.tar"
  EXPECT_IMAGES=("${platform_ids[@]}"); rejects check_archive "$work/default.tar"
  EXPECT_IMAGES=("${default_ids[@]}"); rejects eval "IMAGE_STORE=graphdriver; check_archive \"$work/default.tar\""
  EXPECT_IMAGES=("${refs[0]}=${default_ids[1]#*=}" "${refs[1]}=${default_ids[0]#*=}" "${default_ids[@]:2}")
  rejects check_archive "$work/default.tar"
  for mode in drop-amd64 referrer-subject referrer-name; do
    source_archive="$work/platform.tar"; EXPECT_IMAGES=("${platform_ids[@]}")
    [[ "$mode" != drop-amd64 ]] || { source_archive="$work/default.tar"; EXPECT_IMAGES=("${default_ids[@]}"); }
    python3 "$work/mutate.py" "$source_archive" "$work/$mode.tar" "$mode"
    if [[ ! -f "$work/$mode.tar" ]]; then
      echo "No top-level referrer from this Docker Engine; $mode is covered by the synthetic fixtures."
      continue
    fi
    rejects check_archive "$work/$mode.tar"
  done
  echo "Wrong identities, store kind, swapped mapping, missing amd64 manifest and altered referrers are rejected."
' _ "$script" "$work" "${refs[@]}"
