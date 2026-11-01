#!/usr/bin/env bash
# CodeMastery - infrastructure/minio/test-minio-init.sh   (DP01 initializer qualification)
#
# Integration tests for the MinIO initializer image against disposable MinIO
# servers. Each scenario uses its own private Docker network and server;
# everything created is labelled and removed.
#
#   infrastructure/minio/test-minio-init.sh            build infrastructure/minio and test it
#   infrastructure/minio/test-minio-init.sh IMAGE      test an existing image reference or ID
#
# MINIO_INIT_TEST_SERVER selects the server under test:
#   golithus    (default) the digest-pinned local Community server from the base Compose file
#   chainguard  the digest-pinned DP01 release-server candidate below (not admitted)
#   minimal     infrastructure/vm/Dockerfile.minio-server, built beforehand from that
#               Chainguard pin under a non-release tag (not admitted; linux/amd64 only):
#                 docker build --platform linux/amd64 \
#                   -t codemastery-test/minio-server:qualification - < infrastructure/vm/Dockerfile.minio-server
# An unset variable selects golithus; any other value, including an empty one, fails.
#
# The built image is tagged codemastery-test/minio-init:qualification, never a
# codemastery.invalid/*:release name. MINIO_INIT_TEST_PLATFORM (default
# linux/amd64, the VM platform) selects the platform. Requires Docker with
# Compose v2.24+ and python3. The selected server image must already be present
# (and, for minimal, the Chainguard pin it is built from); Docker pulls the
# digest-pinned initializer base and test-client mc images on demand.
set -Eeuo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
PLATFORM="${MINIO_INIT_TEST_PLATFORM:-linux/amd64}"
RUN_ID="mi$$x${RANDOM}"
LABEL="codemastery.minio-init-test=${RUN_ID}"
WORK="$(mktemp -d)"
PROJECTS=()

BASE_COMPOSE="${ROOT}/infrastructure/docker-compose.yml"
VM_COMPOSE="${ROOT}/infrastructure/vm/docker-compose.vm.yml"
# The approved local pins are the single source of truth for both images. The
# local mc image is the suite's independent test client, not the initializer base.
LOCAL_SERVER_IMAGE="$(sed -n 's/^    image: \(ghcr\.io\/golithus\/minio:.*\)$/\1/p' "${BASE_COMPOSE}")"
MC_IMAGE="$(sed -n 's/^    image: \(ghcr\.io\/golithus\/mc:.*\)$/\1/p' "${BASE_COMPOSE}")"
# Initializer base: Chainguard's mc build, selected only by this index digest.
# Its mc --version carries no release, so the image's package record identifies it.
INIT_BASE_IMAGE='cgr.dev/chainguard/minio-client@sha256:3b67177c0e12f44f646a5cf21b20fc5a79dd2d7a6b4db632b436f0b9e2e46717'
INIT_MC_PACKAGE='0.20250813.083541-r27'
# The base's linux/amd64 identity, from its immutable image configuration: the
# RootFS layer diff IDs and the SHA-256 of its /usr/bin/mc. The initializer must
# be exactly these layers plus one layer holding only the reviewed script.
INIT_BASE_DIFF_IDS='sha256:ecb8a3a0b5d6c085899d9370c0792ea856f6bb5a3c6932e4700b7e086133d421
sha256:e0b29eecf1630223a78b94a8b4fe356436a0992973c7e7d30e00f98a5b8fc7bf
sha256:d49b2a0ee2c4068dd94bb5c503224023adb31d8371a9780eff2a477fe3f4324b
sha256:4c28d7f9b70feecf0f42d5f70e21b522bb4a4af67afa320e46f7c2c1de1ee932
sha256:43269dcf6e0c956aa8449d8c03ee11dc6a1c6ba346fccd4c62941a08a54e60f7
sha256:ecaa8a7c2d5bc3a81ded232f0d9ef96bc61583eb5283df980c1b858aaa82304b
sha256:cf8652f933d60f590ac714d600863f58f45de00c1395bac22ae84c45bb7cbfdf
sha256:0796987ef9b42d9a5ca931a96505aeb93034dc4178fea694a47a24ed315185fb
sha256:3a66f55cf0203c4b4be7a55e9b3ea078adc381d5c3d7b072bfd7d705885ac54e
sha256:c5f8425833dd54e0bf6e04024b7512ca2221294dae27aeb5302e0004783c0b41
sha256:468205ce088d063fd17bf59b048256dc9812d741b7fcb6fc813d355425332cdd'
INIT_MC_SHA256='8842390a4419ff18ec9c43aa71b41beb69030ea56df4f449c03b9623af0bc34a'
# DP01 release-server candidate: Chainguard's build of the chainguard-forks/minio
# fork, selected only by this immutable index digest (docs/deployment.md).
CHAINGUARD_SERVER_IMAGE='cgr.dev/chainguard/minio@sha256:3b6e40c30ecd5cea627e382ff0b82ca841f58c52294578787043ee80c8ef7c82'
# DP01 minimal server candidate: a local build with no registry digest, so it is
# identified by its content, checked against the Chainguard pin it copies from.
MINIMAL_SERVER_IMAGE='codemastery-test/minio-server:qualification'
MINIMAL_DOCKERFILE="${ROOT}/infrastructure/vm/Dockerfile.minio-server"
# /usr/bin/minio of the pin's linux/amd64 manifest, which the image copies unchanged.
MINIMAL_SERVER_BINARY_SHA256='3c3d615241314c531e15e57485c053e60dc1af04e87b20424d2db8bdec8a49b2'
# The base Compose server command; the Chainguard image has no default command.
SERVER_CMD=(server /data --console-address :9001)
SERVER="${MINIO_INIT_TEST_SERVER-golithus}"

ROOT_USER="cm-root-admin"
ROOT_PASS='root pass/with:"odd"\chars é'
APP_KEY="cm-storage-app"
APP_SECRET='app secret@with/odd:"chars"\ é'
BUCKET="codemastery"
REGION="us-east-1"
POLICY_NAME="codemastery-local-storage"
# Expected server-normalized policy, written out independently of the initializer.
EXPECTED_POLICY='{"Version":"2012-10-17","Statement":[{"Effect":"Allow","Action":["s3:GetBucketLocation","s3:ListBucket"],"Resource":["arn:aws:s3:::codemastery"]},{"Effect":"Allow","Action":["s3:DeleteObject","s3:GetObject","s3:PutObject"],"Resource":["arn:aws:s3:::codemastery/*"]}]}'

passed=0

cleanup() {
  local p
  for p in ${PROJECTS[@]+"${PROJECTS[@]}"}; do
    docker compose -p "${p}" --env-file "${WORK}/compose.env" -f "${BASE_COMPOSE}" \
      down -v --remove-orphans >/dev/null 2>&1 || true
  done
  local ids
  ids="$(docker ps -aq --filter "label=${LABEL}")"
  [[ -z "${ids}" ]] || docker rm -fv ${ids} >/dev/null 2>&1 || true
  ids="$(docker network ls -q --filter "label=${LABEL}")"
  [[ -z "${ids}" ]] || docker network rm ${ids} >/dev/null 2>&1 || true
  ids="$(docker volume ls -q --filter "label=${LABEL}")"
  [[ -z "${ids}" ]] || docker volume rm ${ids} >/dev/null 2>&1 || true
  rm -rf "${WORK}"
}
trap cleanup EXIT

die() { printf 'FAIL: %s\n' "$1" >&2; exit 1; }
pass() { passed=$((passed + 1)); printf 'ok %d - %s\n' "${passed}" "$1"; }
section() { printf '\n== %s\n' "$1"; }

[[ -n "${LOCAL_SERVER_IMAGE}" && -n "${MC_IMAGE}" ]] || die "Cannot read the pinned MinIO images from ${BASE_COMPOSE}."

# Expected container defaults (user|entrypoint|command), binary version and
# owner of the server's data directory, per supported server.
case "${SERVER}" in
  golithus)
    SERVER_IMAGE="${LOCAL_SERVER_IMAGE}"
    SERVER_CONFIG='|["/minio"]|["server","/data","--console-address",":9001"]'
    SERVER_VERSION='minio version DEVELOPMENT.2025-10-15T17-29-55Z (commit-id=9e49d5e7a648f00e26f2246f4dc28e6b07f8c84a)'
    SERVER_DATA_UID=0
    ;;
  chainguard)
    SERVER_IMAGE="${CHAINGUARD_SERVER_IMAGE}"
    SERVER_CONFIG='65532|["/usr/bin/minio"]|null'
    SERVER_VERSION='minio version RELEASE.2026-10-23T20-06-51Z (commit-id=76c06ff1d2cc844050fdeb1d54bf8fa70db72278)'
    SERVER_DATA_UID=65532
    ;;
  minimal)
    [[ "${PLATFORM}" == linux/amd64 ]] || die "The minimal server is qualified for linux/amd64 only, not ${PLATFORM}."
    SERVER_IMAGE="${MINIMAL_SERVER_IMAGE}"
    SERVER_CONFIG='65532:65532|["/usr/bin/minio"]|null'
    # The Chainguard binary, copied unchanged.
    SERVER_VERSION='minio version RELEASE.2026-10-23T20-06-51Z (commit-id=76c06ff1d2cc844050fdeb1d54bf8fa70db72278)'
    SERVER_DATA_UID=65532
    ;;
  *) die "MINIO_INIT_TEST_SERVER must be golithus, chainguard or minimal, not '${SERVER}'." ;;
esac

# ------------------------------------------------------------------ helpers

# Runs mc --json as the given identity, inside the pinned mc image. Credentials
# go into a private config file in the container, so any character works.
MC_SHIM='set -u
d="$(mktemp -d)"
e() { local s="$1"; s="${s//\\/\\\\}"; printf "%s" "${s//\"/\\\"}"; }
printf "{\"version\":\"10\",\"aliases\":{\"t\":{\"url\":\"http://minio:9000\",\"accessKey\":\"%s\",\"secretKey\":\"%s\",\"api\":\"S3v4\",\"path\":\"on\"}}}" \
  "$(e "${CM_AK}")" "$(e "${CM_SK}")" > "${d}/config.json"
export MC_CONFIG_DIR="${d}"
if [ -n "${CM_TIMEOUT:-}" ]; then exec timeout "${CM_TIMEOUT}" mc --json "$@"; fi
exec mc --json "$@"'

mc_as() {  # network access-key secret mc-args... (stdin is passed through)
  local net="$1" ak="$2" sk="$3"; shift 3
  CM_AK="${ak}" CM_SK="${sk}" docker run --rm -i --platform "${PLATFORM}" --network "${net}" \
    --label "${LABEL}" -e CM_AK -e CM_SK -e CM_TIMEOUT --entrypoint /bin/bash "${MC_IMAGE}" \
    -c "${MC_SHIM}" mc-shim "$@" 2>&1 || true
}
admin() { local net="$1"; shift; mc_as "${net}" "${ROOT_USER}" "${ROOT_PASS}" "$@" </dev/null; }
app() { local net="$1"; shift; mc_as "${net}" "${APP_KEY}" "${APP_SECRET}" "$@"; }

expect_ok() { [[ "$2" != *'"status":"error"'* ]] || die "$1: ${2:0:300}"; }
expect_code() {  # description code output
  [[ "$3" == *"\"Code\":\"$2\""* ]] || die "$1: expected $2, got ${3:0:300}"
}
# mc reports some S3 denials without a code ("Insufficient permissions ...").
expect_denied() {  # description output
  [[ "$2" == *'"status":"error"'* ]] || die "$1: not denied: ${2:0:300}"
  [[ "$2" == *'"Code":"AccessDenied"'* || "$2" == *'Insufficient permissions'* ]] \
    || die "$1: failed for another reason: ${2:0:300}"
}
expect_eq() { [[ "$2" == "$3" ]] || die "$1:"$'\n'"  expected: $3"$'\n'"  actual:   $2"; }
expect_contains() { [[ "$2" == *"$3"* ]] || die "$1: missing '$3' in: ${2:0:400}"; }

# Runs the selected server with the base Compose command and waits until ready.
run_server() {  # network container-name [docker run options...]
  local net="$1" name="$2"; shift 2
  docker run -d --platform "${PLATFORM}" \
    --name "${name}" --label "${LABEL}" --network "${net}" --network-alias minio \
    -e MINIO_ROOT_USER="${ROOT_USER}" -e MINIO_ROOT_PASSWORD="${ROOT_PASS}" \
    "$@" "${SERVER_IMAGE}" "${SERVER_CMD[@]}" >/dev/null
  local out
  out="$(CM_TIMEOUT=60 admin "${net}" ready t)"
  [[ "${out}" == *'"healthy":true'* ]] || die "MinIO test server ${name} did not become ready."
}

# Starts a fresh server on a new private network; prints the network name.
start_server() {  # name
  local net="cm-${RUN_ID}-$1"
  docker network create --label "${LABEL}" "${net}" >/dev/null
  run_server "${net}" "${net}-minio"
  printf '%s' "${net}"
}

stop_server() { docker rm -fv "$1-minio" >/dev/null 2>&1 || true; docker network rm "$1" >/dev/null 2>&1 || true; }

INIT_OUT=""
INIT_RC=0
# Runs the image exactly as the VM Compose model does: no entrypoint, command,
# user or volume override, only the six approved environment variables.
run_init() {  # network [VAR=value ...]
  local net="$1"; shift
  local -a envs=(
    "MINIO_ROOT_USER=${ROOT_USER}" "MINIO_ROOT_PASSWORD=${ROOT_PASS}"
    "STORAGE_ACCESS_KEY=${APP_KEY}" "STORAGE_SECRET_KEY=${APP_SECRET}"
    "STORAGE_BUCKET=${BUCKET}" "STORAGE_REGION=${REGION}")
  local kv args=()
  for kv in "${envs[@]}" "$@"; do args+=(-e "${kv}"); done
  INIT_RC=0
  INIT_OUT="$(docker run --rm --platform "${PLATFORM}" --network "${net}" --label "${LABEL}" \
    "${args[@]}" "${IMAGE}" 2>&1)" || INIT_RC=$?
  [[ "${INIT_OUT}" != *"${ROOT_PASS}"* && "${INIT_OUT}" != *"${APP_SECRET}"* ]] \
    || die "initializer output disclosed a secret"
}

expect_init_ok() {
  [[ ${INIT_RC} -eq 0 ]] || die "$1: initializer exited ${INIT_RC}: ${INIT_OUT}"
}
expect_init_fail() {  # description expected-message
  [[ ${INIT_RC} -ne 0 ]] || die "$1: initializer unexpectedly succeeded: ${INIT_OUT}"
  expect_contains "$1" "${INIT_OUT}" "$2"
}

# MinIO returns Action/Resource set members in varying order; sort them so
# documents compare exactly otherwise (same rule as the initializer).
canonical_sets() {  # json -> stdout
  local LC_ALL=C delim='":[' s="$1" out="" before key inner item i j n
  local -a items
  while [[ "${s}" == *"${delim}"* ]]; do
    before="${s%%"${delim}"*}"
    s="${s#*"${delim}"}"
    key="${before##*\"}"
    case "${key}" in
      Action|NotAction|Resource|NotResource)
        inner="${s%%]*}"
        s="${s#*]}"
        IFS=, read -r -a items <<< "${inner}"
        n=${#items[@]}
        for ((i = 1; i < n; i++)); do
          item="${items[i]}"
          for ((j = i - 1; j >= 0; j--)); do
            [[ "${items[j]}" > "${item}" ]] || break
            items[j + 1]="${items[j]}"
          done
          items[j + 1]="${item}"
        done
        inner="$(IFS=,; printf '%s' "${items[*]-}")"
        out="${out}${before}${delim}${inner}]"
        ;;
      *) out="${out}${before}${delim}" ;;
    esac
  done
  printf '%s' "${out}${s}"
}

strip_timestamp() {  # blanks the volatile "timestamp":"..." value
  local s="$1" pre post
  [[ "${s}" == *'"timestamp":"'* ]] || { printf '%s' "${s}"; return; }
  pre="${s%%\"timestamp\":\"*}"; post="${s#*\"timestamp\":\"}"; post="${post#*\"}"
  printf '%s"timestamp":""%s' "${pre}" "${post}"
}

# Full identity/policy snapshot; UpdateDate also proves a policy was not rewritten.
snapshot() {  # network
  printf '%s\n' "$(admin "$1" admin user info t "${APP_KEY}")" \
    "$(canonical_sets "$(admin "$1" admin policy info t "${POLICY_NAME}")")" \
    "$(strip_timestamp "$(admin "$1" admin policy entities t --policy "${POLICY_NAME}")")" \
    "$(admin "$1" anonymous get "t/${BUCKET}")" \
    "$(admin "$1" admin user svcacct ls t "${APP_KEY}")"
}

expect_initialized_state() {  # description network
  local net="$2" out
  out="$(admin "${net}" admin user info t "${APP_KEY}")"
  expect_eq "$1: identity" "${out}" \
    '{"status":"success","accessKey":"'"${APP_KEY}"'","policyName":"'"${POLICY_NAME}"'","userStatus":"enabled"}'
  out="$(canonical_sets "$(admin "${net}" admin policy info t "${POLICY_NAME}")")"
  expect_contains "$1: policy document" "${out}" \
    '"policyInfo":{"PolicyName":"'"${POLICY_NAME}"'","Policy":'"${EXPECTED_POLICY}"',"CreateDate"'
  out="$(strip_timestamp "$(admin "${net}" admin policy entities t --policy "${POLICY_NAME}")")"
  expect_eq "$1: policy bindings" "${out}" \
    '{"status":"success","result":{"timestamp":"","policyMappings":[{"policy":"'"${POLICY_NAME}"'","users":["'"${APP_KEY}"'"],"groups":null}]}}'
  out="$(admin "${net}" anonymous get "t/${BUCKET}")"
  expect_contains "$1: bucket is private" "${out}" '"permission":"private"'
  out="$(admin "${net}" admin user svcacct ls t "${APP_KEY}")"
  expect_eq "$1: no service accounts" "${out}" ""
}

expect_nothing_created() {  # description network
  expect_code "$1: identity absent" XMinioAdminNoSuchUser "$(admin "$2" admin user info t "${APP_KEY}")"
  expect_code "$1: policy absent" XMinioAdminNoSuchPolicy "$(admin "$2" admin policy info t "${POLICY_NAME}")"
  expect_code "$1: bucket absent" NoSuchBucket "$(admin "$2" anonymous get "t/${BUCKET}")"
}

# Minimal server only. Its expected contents follow Dockerfile.minio-server; Docker
# adds the runtime entries skipped below to every container. Modification times
# and build metadata differ between builds and are not compared.
MINIMAL_INVENTORY='d 0700 65532:65532 data
d 0700 65532:65532 data/.minio.sys
d 0700 65532:65532 data/.minio.sys/buckets
d 0700 65532:65532 data/.minio.sys/config
d 0700 65532:65532 data/.minio.sys/multipart
d 0700 65532:65532 data/.minio.sys/tmp
d 0700 65532:65532 data/.minio.sys/tmp-old
d 0700 65532:65532 data/.minio.sys/tmp/.trash
d 0755 0:0 etc
f 0644 0:0 etc/group
f 0644 0:0 etc/passwd
d 0755 0:0 etc/ssl
d 0755 0:0 etc/ssl/certs
f 0444 0:0 etc/ssl/certs/.ca-certificates.crt.sha256
l 0777 0:0 etc/ssl/certs/ca-bundle.crt -> ca-certificates.crt
f 0644 0:0 etc/ssl/certs/ca-certificates.crt
d 0700 65532:65532 tmp
d 0755 0:0 usr
d 0755 0:0 usr/bin
f 0755 0:0 usr/bin/minio'

binary_sha256() {  # image path -> SHA-256 of that regular file in the image
  local cid
  cid="$(docker create --platform "${PLATFORM}" --label "${LABEL}" "$1")"
  docker cp "${cid}:$2" - | python3 -c '
import hashlib, os, sys, tarfile
with tarfile.open(fileobj=sys.stdin.buffer, mode="r|") as t:
    m = next(iter(t))
    assert m.isreg() and m.name == os.path.basename(sys.argv[1]), (m.name, m.type)
    print(hashlib.sha256(t.extractfile(m).read()).hexdigest())' "$2"
  docker rm "${cid}" >/dev/null
}

# The initializer image itself, built here or supplied as IMAGE, against its
# trusted identity. Package metadata, Dockerfile text and layer counts are not
# proof: every layer is re-hashed, the base layers must be exactly the pin's, and
# the one added layer may hold only the reviewed script and its directories.
verify_initializer_artifact() {
  [[ "${PLATFORM}" == linux/amd64 ]] \
    || die "Initializer artifact identity is recorded for linux/amd64 only, not ${PLATFORM}."
  docker save -o "${WORK}/initializer.tar" "${IMAGE}" || die "Cannot export ${IMAGE} for verification."
  python3 - "${WORK}/initializer.tar" "${ROOT}/infrastructure/minio/minio-init.sh" "${INIT_BASE_DIFF_IDS}" <<'PY' \
    || die "${IMAGE} is not the approved initializer base plus the reviewed script."
import gzip, hashlib, io, json, sys, tarfile
archive, script, base = sys.argv[1], sys.argv[2], sys.argv[3].split()
with open(script, "rb") as f:
    script_sha = hashlib.sha256(f.read()).hexdigest()
with tarfile.open(archive) as o:
    manifest = json.load(o.extractfile("manifest.json"))
    assert len(manifest) == 1, "expected one image in the archive, found %d" % len(manifest)
    config = json.load(o.extractfile(manifest[0]["Config"]))
    assert (config.get("os"), config.get("architecture")) == ("linux", "amd64"), (config.get("os"), config.get("architecture"))
    diff_ids, layers = config["rootfs"]["diff_ids"], manifest[0]["Layers"]
    assert diff_ids[:-1] == base and len(diff_ids) == len(base) + 1, \
        "RootFS is not the pinned base plus one layer: %s" % diff_ids
    assert len(layers) == len(diff_ids), (len(layers), len(diff_ids))
    for path, diff_id in zip(layers, diff_ids):
        data = o.extractfile(path).read()
        if data[:2] == b"\x1f\x8b":
            data = gzip.decompress(data)
        assert "sha256:" + hashlib.sha256(data).hexdigest() == diff_id, "layer %s does not match %s" % (path, diff_id)
top = tarfile.open(fileobj=io.BytesIO(data))
entries = {}
for m in top.getmembers():
    name = m.name[2:] if m.name.startswith("./") else m.name
    entries[name.strip("/")] = m
script_path = "usr/local/bin/codemastery-minio-init"
assert set(entries) == {"usr", "usr/local", "usr/local/bin", script_path}, sorted(entries)
for name in ("usr", "usr/local", "usr/local/bin"):
    m = entries[name]
    # Directory modes vary by builder; they must be root-owned and not writable by others.
    assert m.isdir() and (m.uid, m.gid) == (0, 0) and not m.mode & 0o022, (name, m.type, m.uid, m.gid, oct(m.mode))
m = entries[script_path]
assert m.isreg() and (m.uid, m.gid, m.mode & 0o7777) == (0, 0, 0o555), (m.type, m.uid, m.gid, oct(m.mode))
assert hashlib.sha256(top.extractfile(m).read()).hexdigest() == script_sha, \
    "initializer script differs from infrastructure/minio/minio-init.sh"
PY
  rm -f "${WORK}/initializer.tar"
  # docker create pulls the pin by digest if it is missing; an unavailable pin fails.
  expect_eq "pinned initializer base mc binary" "$(binary_sha256 "${INIT_BASE_IMAGE}" /usr/bin/mc)" "${INIT_MC_SHA256}"
  expect_eq "initializer mc binary" "$(binary_sha256 "${IMAGE}" /usr/bin/mc)" "${INIT_MC_SHA256}"
}

verify_minimal_server() {
  docker image inspect "${SERVER_IMAGE}" | python3 -c '
import json, sys
image = json.load(sys.stdin)[0]
c = image["Config"]
assert (image["Os"], image["Architecture"]) == ("linux", "amd64"), (image["Os"], image["Architecture"])
assert c.get("User") == "65532:65532", c.get("User")
assert c.get("Entrypoint") == ["/usr/bin/minio"], c.get("Entrypoint")
default_path = "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
env = [e for e in c.get("Env") or [] if e != default_path]
assert env == ["SSL_CERT_FILE=/etc/ssl/certs/ca-certificates.crt"], c.get("Env")
for key in ("Cmd", "ExposedPorts", "Volumes", "Healthcheck", "OnBuild", "Labels", "Shell"):
    assert not c.get(key), (key, c[key])
' || die "minimal server image configuration differs from ${MINIMAL_DOCKERFILE}"

  local cid inventory
  cid="$(docker create --platform "${PLATFORM}" --label "${LABEL}" "${SERVER_IMAGE}")"
  inventory="$(docker export "${cid}" | python3 -c '
import sys, tarfile
runtime = {".dockerenv", "dev", "proc", "sys", "etc/hostname", "etc/hosts", "etc/mtab", "etc/resolv.conf"}
rows = []
with tarfile.open(fileobj=sys.stdin.buffer, mode="r|") as t:
    for m in t:
        name = m.name[2:] if m.name.startswith("./") else m.name
        name = name.strip("/")
        if not name or name in runtime or name.startswith("dev/"):
            continue
        kind = "d" if m.isdir() else "l" if m.issym() else "f" if m.isreg() else "?" + m.type.decode()
        link = " -> " + m.linkname if m.issym() or m.islnk() else ""
        rows.append("%s %04o %d:%d %s%s" % (kind, m.mode & 0o7777, m.uid, m.gid, name, link))
print("\n".join(sorted(rows, key=lambda r: r.split(" ", 3)[3])))')"
  docker rm "${cid}" >/dev/null
  # Exact contents: no shell, mc or other tool, no setuid/setgid bit; private data and tmp.
  expect_eq "minimal server filesystem" "${inventory}" "${MINIMAL_INVENTORY}"

  # The pin is trusted by digest; the local build only by matching it.
  expect_eq "Chainguard pin binary" "$(binary_sha256 "${CHAINGUARD_SERVER_IMAGE}" /usr/bin/minio)" "${MINIMAL_SERVER_BINARY_SHA256}"
  expect_eq "minimal server binary" "$(binary_sha256 "${SERVER_IMAGE}" /usr/bin/minio)" "${MINIMAL_SERVER_BINARY_SHA256}"

  # The image has no shell: read the running server's status from a sidecar
  # in its PID namespace.
  local name="cm-${RUN_ID}-minimal-process" status
  docker run -d --platform "${PLATFORM}" --name "${name}" --label "${LABEL}" --network none \
    -e MINIO_ROOT_USER="${ROOT_USER}" -e MINIO_ROOT_PASSWORD="${ROOT_PASS}" \
    "${SERVER_IMAGE}" "${SERVER_CMD[@]}" >/dev/null
  status="$(docker run --rm --platform "${PLATFORM}" --pid "container:${name}" --network none \
    --label "${LABEL}" --entrypoint /bin/bash "${MC_IMAGE}" -c '
    while read -r k v; do case "$k" in Uid:|Gid:|Groups:|CapInh:|CapPrm:|CapEff:|CapAmb:) echo $k $v;; esac; done </proc/1/status' 2>&1)"
  docker rm -fv "${name}" >/dev/null
  expect_eq "minimal server process identity and capabilities" "${status}" \
    $'Uid: 65532 65532 65532 65532\nGid: 65532 65532 65532 65532\nGroups: 65532\nCapInh: 0000000000000000\nCapPrm: 0000000000000000\nCapEff: 0000000000000000\nCapAmb: 0000000000000000'
}

# ------------------------------------------------------------------ image

section "Image build and static properties (${PLATFORM})"
if [[ $# -ge 1 ]]; then
  IMAGE="$1"
  docker image inspect "${IMAGE}" >/dev/null 2>&1 || die "Image ${IMAGE} is not present locally."
else
  IMAGE="codemastery-test/minio-init:qualification"
  docker build -q --platform "${PLATFORM}" -t "${IMAGE}" "${ROOT}/infrastructure/minio" >/dev/null
fi
IMAGE_ID="$(docker image inspect --format '{{.Id}}' "${IMAGE}")"
printf 'image under test: %s (%s)\n' "${IMAGE}" "${IMAGE_ID}"
printf 'server under test: %s (%s)\nmc image: %s\n' "${SERVER}" "${SERVER_IMAGE}" "${MC_IMAGE}"
if [[ "${SERVER}" == minimal ]]; then
  # Never pulled or substituted: a missing candidate fails.
  docker image inspect "${SERVER_IMAGE}" >/dev/null 2>&1 \
    || die "Build the minimal server first: docker build --platform ${PLATFORM} -t ${SERVER_IMAGE} - < infrastructure/vm/Dockerfile.minio-server"
  docker image inspect "${CHAINGUARD_SERVER_IMAGE}" >/dev/null 2>&1 \
    || die "Pull its pinned source image first: docker pull --platform ${PLATFORM} ${CHAINGUARD_SERVER_IMAGE}"
  printf 'minimal server image ID: %s\n' "$(docker image inspect --format '{{.Id}}' "${SERVER_IMAGE}")"
else
  docker image inspect "${SERVER_IMAGE}" >/dev/null 2>&1 \
    || die "Pull the pinned server image first: docker pull --platform ${PLATFORM} ${SERVER_IMAGE}"
fi

bash -n "${ROOT}/infrastructure/minio/minio-init.sh"
bash -n "${BASH_SOURCE[0]}"
pass "shell syntax"

from="$(sed -n 's/^FROM //p' "${ROOT}/infrastructure/minio/Dockerfile")"
expect_eq "Dockerfile base is the approved initializer base pin" "${from}" "${INIT_BASE_IMAGE}"
[[ "${INIT_BASE_IMAGE}" =~ @sha256:[0-9a-f]{64}$ ]] || die "initializer base is not digest-pinned: ${INIT_BASE_IMAGE}"
expect_contains "server pin" "${LOCAL_SERVER_IMAGE}" "@sha256:8793e960474071520bdb91bb9a6d1793eb229fdbc9dcb7ec75ad836b69bbab40"
expect_contains "mc pin" "${MC_IMAGE}" "@sha256:dde55ccc0bc9e65dc582c8e652d652eea1438c6e60d774524f4da15d7b26a900"
if [[ "${SERVER}" == minimal ]]; then
  # A local build has no registry digest; its only source must be the Chainguard pin.
  expect_eq "minimal server Dockerfile stages" "$(sed -n 's/^FROM //p' "${MINIMAL_DOCKERFILE}")" \
    "${CHAINGUARD_SERVER_IMAGE} AS source"$'\n'"scratch AS layout"$'\n'"scratch"
  pass "server and mc images keep the approved digest pins; the minimal server is built only from the Chainguard pin"
else
  [[ "${SERVER_IMAGE}" =~ @sha256:[0-9a-f]{64}$ ]] || die "server under test is not digest-pinned: ${SERVER_IMAGE}"
  pass "server and mc images keep the approved digest pins"
fi

# The selected server must be exactly the expected artifact for this platform.
cid="$(docker create --platform "${PLATFORM}" --label "${LABEL}" "${SERVER_IMAGE}")"
scfg="$(docker inspect --format '{{.Config.User}}|{{json .Config.Entrypoint}}|{{json .Config.Cmd}}' "${cid}")"
# -v: the golithus image declares VOLUME /data, so create made an anonymous volume.
docker rm -v "${cid}" >/dev/null
expect_eq "${SERVER} server image configuration" "${scfg}" "${SERVER_CONFIG}"
version="$(docker run --rm --platform "${PLATFORM}" --network none --label "${LABEL}" "${SERVER_IMAGE}" --version 2>&1)"
expect_contains "${SERVER} server version" "${version}" "${SERVER_VERSION}"
expect_contains "${SERVER} server platform" "${version}" " ${PLATFORM}"
if [[ "${SERVER}" == minimal ]]; then
  verify_minimal_server
  pass "minimal server has the expected configuration, exact filesystem, the pin's binary, and runs as UID 65532 with no capabilities"
else
  pass "${SERVER} server has the expected user, entrypoint, default command and binary version for ${PLATFORM}"
fi

cfg="$(docker image inspect --format '{{.Config.User}}|{{json .Config.Entrypoint}}|{{json .Config.Cmd}}|{{json .Config.Env}}|{{json .Config.ExposedPorts}}|{{json .Config.Volumes}}|{{json .Config.Healthcheck}}|{{.Os}}/{{.Architecture}}' "${IMAGE}")"
expect_eq "image configuration" "${cfg}" \
  '65532:65532|["/usr/local/bin/codemastery-minio-init"]|null|["PATH=/usr/local/sbin:/usr/local/bin:/usr/bin:/usr/sbin:/sbin:/bin","SSL_CERT_FILE=/etc/ssl/certs/ca-certificates.crt"]|null|null|null|'"${PLATFORM}"
verify_initializer_artifact
pass "non-root user, own entrypoint, no command, secrets, ports, volumes or healthcheck; pinned base layers plus only the reviewed script; base mc binary"

runtime="$(docker run --rm --platform "${PLATFORM}" --network none --label "${LABEL}" --entrypoint /bin/bash "${IMAGE}" -c '
  printf "uid=%s gid=%s\n" "$(id -u)" "$(id -g)"
  while read -r k v; do case "$k" in CapInh:|CapPrm:|CapEff:|CapAmb:) printf "%s%s\n" "$k" "$v";; esac; done </proc/self/status
  [ -w /usr/local/bin/codemastery-minio-init ] && echo "entrypoint writable"
  [ -w / ] && echo "root writable"
  [ -w /tmp ] || echo "tmp not writable"
  while IFS= read -r l; do case "$l" in P:*) p="${l#P:}";; V:*) [ "$p" = mc ] && echo "mc package ${l#V:}";; esac; done </usr/lib/apk/db/installed
  true')"
expect_eq "runtime identity, capabilities and mc package" "${runtime}" \
  $'uid=65532 gid=65532\nCapInh:0000000000000000\nCapPrm:0000000000000000\nCapEff:0000000000000000\nCapAmb:0000000000000000\n'"mc package ${INIT_MC_PACKAGE}"
pass "runs as UID 65532 with no effective, permitted or ambient capabilities; packages the expected mc release"

list_setid() {  # reads `tar -tv` lines; prints entries with a setuid/setgid bit
  local mode rest
  while read -r mode rest; do
    case "${mode}" in
      ???[sS]*|??????[sS]*) printf '%s %s\n' "${mode}" "${rest}" ;;
    esac
  done
}
cid="$(docker create --platform "${PLATFORM}" --label "${LABEL}" "${IMAGE}")"
special="$(docker export "${cid}" | tar -tvf - | list_setid)"
docker rm "${cid}" >/dev/null
expect_eq "setuid/setgid files" "${special}" ""
pass "image filesystem has no setuid or setgid files"

# ------------------------------------------------------------------ inputs

section "Input validation (no server contacted)"
check_rejected() {  # description expected-message VAR=value...
  local d="$1" m="$2"; shift 2
  run_init none "$@"
  expect_init_fail "${d}" "${m}"
}
check_rejected "application key equals root" "must differ from MinIO root identity" "STORAGE_ACCESS_KEY=${ROOT_USER}"
check_rejected "application secret equals root password" "must differ from MINIO_ROOT_PASSWORD" "STORAGE_SECRET_KEY=${ROOT_PASS}"
for var in MINIO_ROOT_USER MINIO_ROOT_PASSWORD STORAGE_ACCESS_KEY STORAGE_SECRET_KEY STORAGE_BUCKET STORAGE_REGION; do
  check_rejected "empty ${var}" "${var} must be set" "${var}="
done
for b in ab UPPER a..b -abc abc- 192.168.1.10 'bad"name' a.-b a-.b 'with space' \
         "$(printf 'a%.0s' {1..64})"; do
  check_rejected "bucket '${b}'" "valid S3 bucket name" "STORAGE_BUCKET=${b}"
done
for k in ab 'has space' 'quo"te' 'back\slash' 'abcdefghijklmnopqrstu' 'cm=storage' 'cm,storage'; do
  check_rejected "access key '${k}'" "STORAGE_ACCESS_KEY must be 3-20" "STORAGE_ACCESS_KEY=${k}"
done
check_rejected "7-byte secret" "STORAGE_SECRET_KEY must be at least 8 bytes" "STORAGE_SECRET_KEY=short7x"
check_rejected "region with space" "STORAGE_REGION must contain" "STORAGE_REGION=us east"
check_rejected "secret with newline" "must not contain control characters" $'STORAGE_SECRET_KEY=line1\nline2'
pass "invalid configuration fails closed before contacting MinIO"

# ------------------------------------------------------------------ first run

section "First initialization, least privilege and repeat runs"
net="$(start_server first)"
# Unrelated state that must stay invisible to and untouched by the application.
expect_ok "create other bucket" "$(admin "${net}" mb t/other-bucket)"
expect_ok "seed other object" "$(printf 'private\n' | mc_as "${net}" "${ROOT_USER}" "${ROOT_PASS}" pipe t/other-bucket/secret.txt)"

run_init "${net}"
expect_init_ok "first run"
expect_contains "first run" "${INIT_OUT}" "initialized (changes: 4)"
expect_initialized_state "first run" "${net}"
pass "first run creates bucket, exact policy, bound enabled identity; bucket private"

# While the bucket is empty, so mc sends DeleteBucket rather than object deletes.
expect_denied "bucket deletion" "$(app "${net}" rb "t/${BUCKET}" </dev/null)"
out="$(printf 'payload\n' | app "${net}" pipe "t/${BUCKET}/lessons/a.txt")"; expect_ok "app put" "${out}"
out="$(app "${net}" cat "t/${BUCKET}/lessons/a.txt" </dev/null)"; expect_eq "app get" "${out}" "payload"
out="$(app "${net}" ls --recursive "t/${BUCKET}" </dev/null)"; expect_contains "app list" "${out}" '"key":"lessons/a.txt"'
pass "application can put, get and list objects in its bucket"
out="$(app "${net}" ls t </dev/null)"
expect_contains "bucket listing" "${out}" "\"key\":\"${BUCKET}/\""
[[ "${out}" != *other-bucket* ]] || die "application can see another bucket"
expect_denied "other bucket read" "$(app "${net}" cat t/other-bucket/secret.txt </dev/null)"
expect_denied "other bucket list" "$(app "${net}" ls t/other-bucket </dev/null)"
expect_denied "other bucket write" "$(printf 'x\n' | app "${net}" pipe t/other-bucket/x.txt)"
expect_denied "bucket creation" "$(app "${net}" mb t/new-bucket </dev/null)"
expect_denied "anonymous policy change" "$(app "${net}" anonymous set public "t/${BUCKET}" </dev/null)"
expect_denied "administration" "$(app "${net}" admin user list t </dev/null)"
expect_contains "own bucket kept" "$(admin "${net}" anonymous get "t/${BUCKET}")" '"permission":"private"'
expect_contains "other bucket write left nothing" "$(admin "${net}" stat t/other-bucket/x.txt)" "Object does not exist"
expect_code "bucket creation left nothing" NoSuchBucket "$(admin "${net}" anonymous get t/new-bucket)"
expect_eq "other object intact" "$(admin "${net}" cat t/other-bucket/secret.txt)" "private"
pass "application cannot reach other buckets, create/delete buckets, change bucket policy or administer"

before="$(snapshot "${net}")"
run_init "${net}"
expect_init_ok "second run"
expect_contains "second run" "${INIT_OUT}" "initialized (changes: 0)"
run_init "${net}"
expect_init_ok "third run"
expect_eq "state after repeat runs" "$(snapshot "${net}")" "${before}"
expect_eq "object kept" "$(app "${net}" cat "t/${BUCKET}/lessons/a.txt" </dev/null)" "payload"
out="$(app "${net}" rm "t/${BUCKET}/lessons/a.txt" </dev/null)"; expect_ok "app delete" "${out}"
expect_contains "deleted" "$(admin "${net}" stat "t/${BUCKET}/lessons/a.txt")" "Object does not exist"
pass "repeat runs change nothing (policy UpdateDate unchanged); application can delete its objects"
stop_server "${net}"

# ------------------------------------------------------------------ persistence

# A clean first-time volume only; no existing data volume is migrated here.
section "Named-volume persistence across container recreation"
net="cm-${RUN_ID}-persist"
vol="cm-${RUN_ID}-persist-data"
docker network create --label "${LABEL}" "${net}" >/dev/null
docker volume create --label "${LABEL}" "${vol}" >/dev/null
run_server "${net}" "${net}-minio" -v "${vol}:/data"
run_init "${net}"
expect_init_ok "persistence first run"
expect_contains "persistence first run" "${INIT_OUT}" "initialized (changes: 4)"
payload="persist-${RUN_ID}-$(od -An -N24 -tx1 /dev/urandom | tr -d ' \n')"
expect_ok "persistence put" "$(printf '%s' "${payload}" | app "${net}" pipe "t/${BUCKET}/persist/object.txt")"
docker rm -f "${net}-minio" >/dev/null
expect_eq "named volume kept after container removal" "$(docker volume inspect --format '{{.Name}}' "${vol}")" "${vol}"
run_server "${net}" "${net}-minio" -v "${vol}:/data"
expect_eq "object after recreation" "$(app "${net}" cat "t/${BUCKET}/persist/object.txt" </dev/null)" "${payload}"
run_init "${net}"
expect_init_ok "persistence after recreation"
expect_contains "persistence after recreation" "${INIT_OUT}" "initialized (changes: 0)"
expect_initialized_state "persistence after recreation" "${net}"
owner="$(docker run --rm --platform "${PLATFORM}" --network none --label "${LABEL}" -v "${vol}:/data:ro" \
  --entrypoint /bin/bash "${MC_IMAGE}" -c 'stat -c %u /data/.minio.sys')"
expect_eq "data directory owner" "${owner}" "${SERVER_DATA_UID}"
stop_server "${net}"
docker volume rm "${vol}" >/dev/null
pass "object and IAM state survive container removal and recreation on a named volume written as UID ${SERVER_DATA_UID}"

# ------------------------------------------------------------------ credentials

section "Incorrect credentials"
net="$(start_server wrong-root)"
run_init "${net}" "MINIO_ROOT_PASSWORD=not-the-root-password"
expect_init_fail "wrong root password" "MinIO rejected the administrator credentials"
run_init "${net}" "MINIO_ROOT_USER=not-the-root-user"
expect_init_fail "wrong root user" "MinIO rejected the administrator credentials"
expect_nothing_created "wrong root credentials" "${net}"
pass "wrong administrator credentials fail before any change"
stop_server "${net}"

net="$(start_server wrong-app)"
expect_ok "pre-existing identity" "$(printf '%s\n' 'original-secret-1' | mc_as "${net}" "${ROOT_USER}" "${ROOT_PASS}" admin user add t "${APP_KEY}")"
run_init "${net}"
expect_init_fail "existing identity, other secret" "does not accept STORAGE_SECRET_KEY"
expect_code "original secret still valid" AccessDenied "$(mc_as "${net}" "${APP_KEY}" original-secret-1 admin user list t </dev/null)"
expect_code "configured secret not installed" SignatureDoesNotMatch "$(app "${net}" admin user list t </dev/null)"
expect_eq "identity left unbound" "$(admin "${net}" admin user info t "${APP_KEY}")" \
  '{"status":"success","accessKey":"'"${APP_KEY}"'","userStatus":"enabled"}'
expect_code "no policy created" XMinioAdminNoSuchPolicy "$(admin "${net}" admin policy info t "${POLICY_NAME}")"
expect_code "no bucket created" NoSuchBucket "$(admin "${net}" anonymous get "t/${BUCKET}")"
pass "existing application identity with another secret is not reset; nothing is changed"
stop_server "${net}"

# MinIO itself rejects these only when creating the identity, after the bucket
# and policy exist; the initializer must refuse them before any change.
net="$(start_server invalid-app)"
run_init "${net}" "STORAGE_ACCESS_KEY=cm=storage"
expect_init_fail "access key with '='" "STORAGE_ACCESS_KEY must be 3-20"
expect_nothing_created "access key with '='" "${net}"
run_init "${net}" "STORAGE_SECRET_KEY=short7x"
expect_init_fail "7-byte secret" "STORAGE_SECRET_KEY must be at least 8 bytes"
expect_nothing_created "7-byte secret" "${net}"
# MinIO counts bytes: seven characters encoded as eight UTF-8 bytes is accepted.
run_init "${net}" "STORAGE_SECRET_KEY=abcdefé"
expect_init_ok "8-byte secret"
expect_initialized_state "8-byte secret" "${net}"
pass "invalid application access key or short secret is refused on a live server with nothing changed; an 8-byte secret is accepted"
stop_server "${net}"

# ------------------------------------------------------------------ conflicts

section "Conflicting IAM and bucket state"
net="$(start_server broad-policy)"
printf '%s' '{"Version":"2012-10-17","Statement":[{"Effect":"Allow","Action":["s3:*"],"Resource":["arn:aws:s3:::*"]}]}' > "${WORK}/broad.json"
out="$(CM_AK="${ROOT_USER}" CM_SK="${ROOT_PASS}" docker run --rm --platform "${PLATFORM}" --network "${net}" \
  --label "${LABEL}" -e CM_AK -e CM_SK -v "${WORK}/broad.json:/broad.json:ro" --entrypoint /bin/bash \
  "${MC_IMAGE}" -c "${MC_SHIM}" mc-shim admin policy create t "${POLICY_NAME}" /broad.json 2>&1)"
expect_ok "seed broad policy" "${out}"
before="$(canonical_sets "$(admin "${net}" admin policy info t "${POLICY_NAME}")")"
run_init "${net}"
expect_init_fail "broad policy with the approved name" "differs from the approved bucket-scoped policy"
expect_eq "broad policy untouched" "$(canonical_sets "$(admin "${net}" admin policy info t "${POLICY_NAME}")")" "${before}"
expect_code "no identity created" XMinioAdminNoSuchUser "$(admin "${net}" admin user info t "${APP_KEY}")"
pass "a same-named policy with other contents is refused, not overwritten"
stop_server "${net}"

net="$(start_server equivalent-policy)"
printf '%s' '{"Version":"2012-10-17","Statement":[{"Effect":"Allow","Action":["s3:ListBucket","s3:GetBucketLocation"],"Resource":["arn:aws:s3:::codemastery"]},{"Effect":"Allow","Action":["s3:PutObject","s3:GetObject","s3:DeleteObject"],"Resource":["arn:aws:s3:::codemastery/*"]}]}' > "${WORK}/reordered.json"
out="$(CM_AK="${ROOT_USER}" CM_SK="${ROOT_PASS}" docker run --rm --platform "${PLATFORM}" --network "${net}" \
  --label "${LABEL}" -e CM_AK -e CM_SK -v "${WORK}/reordered.json:/reordered.json:ro" --entrypoint /bin/bash \
  "${MC_IMAGE}" -c "${MC_SHIM}" mc-shim admin policy create t "${POLICY_NAME}" /reordered.json 2>&1)"
expect_ok "seed reordered policy" "${out}"
before="$(admin "${net}" admin policy info t "${POLICY_NAME}")"
run_init "${net}"
expect_init_ok "equivalent policy"
expect_contains "equivalent policy" "${INIT_OUT}" "initialized (changes: 3)"
[[ "${INIT_OUT}" != *"Created policy"* ]] || die "equivalent policy was recreated"
expect_eq "policy dates unchanged" "${before##*\"CreateDate\"}" \
  "$(admin "${net}" admin policy info t "${POLICY_NAME}" | { read -r l; printf '%s' "${l##*\"CreateDate\"}"; })"
expect_initialized_state "equivalent policy" "${net}"
pass "an existing policy with the same permissions in another member order is accepted unchanged"
stop_server "${net}"

net="$(start_server public-bucket)"
expect_ok "seed bucket" "$(admin "${net}" mb "t/${BUCKET}")"
expect_ok "seed anonymous access" "$(admin "${net}" anonymous set download "t/${BUCKET}")"
run_init "${net}"
expect_init_fail "anonymous bucket" "allows anonymous access"
expect_contains "anonymous access untouched" "$(admin "${net}" anonymous get "t/${BUCKET}")" '"permission":"download"'
expect_code "no identity created" XMinioAdminNoSuchUser "$(admin "${net}" admin user info t "${APP_KEY}")"
pass "an existing bucket with anonymous access is refused"
stop_server "${net}"

# Each case starts from correctly initialized state, then adds one conflict.
conflict_case() {  # name expected-message setup-function verify-function
  local net
  net="$(start_server "$1")"
  run_init "${net}"; expect_init_ok "$1 setup"
  "$3" "${net}"
  run_init "${net}"
  expect_init_fail "$1" "$2"
  "$4" "${net}"
  stop_server "${net}"
}
extra_policy() { expect_ok "attach readwrite" "$(admin "$1" admin policy attach t readwrite --user "${APP_KEY}")"; }
extra_policy_kept() {
  expect_contains "readwrite still attached" "$(admin "$1" admin user info t "${APP_KEY}")" \
    '"policyName":"'"${POLICY_NAME}"',readwrite"'
}
group_member() { expect_ok "add group" "$(admin "$1" admin group add t storage-team "${APP_KEY}")"; }
group_kept() { expect_contains "membership kept" "$(admin "$1" admin user info t "${APP_KEY}")" '"memberOf":[{"name":"storage-team"}]'; }
other_binding() {
  expect_ok "other user" "$(printf '%s\n' 'other-secret-1' | mc_as "$1" "${ROOT_USER}" "${ROOT_PASS}" admin user add t other-user)"
  expect_ok "bind other" "$(admin "$1" admin policy attach t "${POLICY_NAME}" --user other-user)"
}
other_binding_kept() { expect_contains "binding kept" "$(admin "$1" admin user info t other-user)" '"policyName":"'"${POLICY_NAME}"'"'; }
disabled() { expect_ok "disable" "$(admin "$1" admin user disable t "${APP_KEY}")"; }
disabled_kept() { expect_contains "still disabled" "$(admin "$1" admin user info t "${APP_KEY}")" '"userStatus":"disabled"'; }
service_account() { expect_ok "service account" "$(admin "$1" admin user svcacct add t "${APP_KEY}")"; }
service_account_kept() {
  local out; out="$(admin "$1" admin user svcacct ls t "${APP_KEY}")"
  [[ -n "${out}" ]] || die "service account was removed"
}
conflict_case extra-policy "Unexpected application policy bindings" extra_policy extra_policy_kept
conflict_case group "must not inherit group policies" group_member group_kept
conflict_case other-binding "bound to an unexpected user or group" other_binding other_binding_kept
conflict_case disabled "identity is disabled" disabled disabled_kept
conflict_case svcacct "has service accounts" service_account service_account_kept
pass "extra policy, group membership, foreign binding, disabled identity and service accounts are refused and left as found"

# ------------------------------------------------------------------ unavailable

section "Unavailable server"
net="cm-${RUN_ID}-no-server"
docker network create --label "${LABEL}" "${net}" >/dev/null
start=$(date +%s)
run_init "${net}"
expect_init_fail "no server" "did not become ready within 120 seconds"
printf 'readiness failure after %ss\n' "$(( $(date +%s) - start ))"
docker network rm "${net}" >/dev/null
pass "an unreachable server fails after the readiness deadline"

# ------------------------------------------------------------------ Compose

section "VM Compose contract and failure propagation"
cat > "${WORK}/compose.env" <<EOF
POSTGRES_DB=codemastery
POSTGRES_USER=bootstrap
POSTGRES_PASSWORD=unused-in-this-test
DB_NAME=codemastery
DB_USER=runtime
DB_PASSWORD=unused-in-this-test
JWT_SECRET=unused-in-this-test-unused-in-this-test
CORS_ALLOWED_ORIGINS=https://minio-init.codemastery.test
SPRING_FLYWAY_URL=jdbc:postgresql://postgres:5432/codemastery
SPRING_FLYWAY_USER=migrator
SPRING_FLYWAY_PASSWORD=unused-in-this-test
EOF
# Compose's env file is not shell-quoted; pass credentials through the environment.
export MINIO_ROOT_USER="${ROOT_USER}" MINIO_ROOT_PASSWORD="${ROOT_PASS}"
export STORAGE_ACCESS_KEY="${APP_KEY}" STORAGE_SECRET_KEY="${APP_SECRET}"
export STORAGE_BUCKET="${BUCKET}" STORAGE_REGION="${REGION}"
export DOCKER_DEFAULT_PLATFORM="${PLATFORM}"
# Test-only images: no admitted server exists (the selected server stands in),
# and a sleeping placeholder stands in for the backend that waits on minio-init.
# The local stack uses the same server, so its volume is never handed from one
# server build to another.
cat > "${WORK}/local-ports.yml" <<EOF
services:
  minio:
    image: ${SERVER_IMAGE}
    ports: !reset []
EOF
cat > "${WORK}/release-images.yml" <<EOF
services:
  minio:
    image: ${SERVER_IMAGE}
  minio-init:
    image: ${IMAGE}
  backend:
    image: ${MC_IMAGE}
    entrypoint: ["sleep", "600"]
    depends_on: !override
      minio-init:
        condition: service_completed_successfully
EOF
project="cm-${RUN_ID}-compose"
PROJECTS+=("${project}")
local_compose=(docker compose -p "${project}" --env-file "${WORK}/compose.env" -f "${BASE_COMPOSE}" -f "${WORK}/local-ports.yml")
vm_compose=(docker compose -p "${project}" --env-file "${WORK}/compose.env" -f "${BASE_COMPOSE}" -f "${VM_COMPOSE}" -f "${WORK}/release-images.yml")

"${vm_compose[@]}" config --format json | python3 -c '
import json, sys
init = json.load(sys.stdin)["services"]["minio-init"]
expected_env = {"MINIO_ROOT_USER", "MINIO_ROOT_PASSWORD", "STORAGE_ACCESS_KEY",
                "STORAGE_SECRET_KEY", "STORAGE_BUCKET", "STORAGE_REGION"}
assert init.get("entrypoint") is None and init.get("command") is None, "inline initializer survived"
assert init["image"] == sys.argv[1], init["image"]
assert set(init.get("environment", {})) == expected_env, sorted(init.get("environment", {}))
for key in ("user", "volumes", "ports", "cap_add", "privileged", "build"):
    assert not init.get(key), key
assert init.get("restart") in (None, "no")
' "${IMAGE}"
pass "effective VM model runs the image entrypoint with only the six approved variables"

# The override keeps its abstract server name, inherits the base command and the
# named data volume, and sets the approved restart policy. The test images then
# run that model unchanged with the selected server.
check_minio_model() {  # expected-image compose-command...
  local want="$1"; shift
  "$@" config --format json | python3 -c '
import json, sys
minio = json.load(sys.stdin)["services"]["minio"]
assert minio["image"] == sys.argv[1], minio["image"]
assert minio.get("command") == sys.argv[2:], minio.get("command")
for key in ("entrypoint", "user", "ports", "cap_add", "privileged", "build"):
    assert not minio.get(key), key
volumes = [(v.get("type"), v.get("source"), v.get("target")) for v in minio.get("volumes", [])]
assert volumes == [("volume", "minio-data", "/data")], minio.get("volumes")
assert minio.get("restart") == "unless-stopped", minio.get("restart")
' "${want}" "${SERVER_CMD[@]}"
}
check_minio_model codemastery.invalid/minio:release \
  docker compose -p "${project}" --env-file "${WORK}/compose.env" -f "${BASE_COMPOSE}" -f "${VM_COMPOSE}"
check_minio_model "${SERVER_IMAGE}" "${vm_compose[@]}"
pass "effective VM model keeps codemastery.invalid/minio:release, the base command, the named minio-data volume and restart unless-stopped"

# State created by the existing local inline initializer must be accepted as-is.
"${local_compose[@]}" up -d --no-build --pull never minio-init >"${WORK}/local-up.log" 2>&1 \
  || die "local stack failed to start: $(tail -5 "${WORK}/local-up.log")"
cid="$("${local_compose[@]}" ps -a -q minio-init)"
expect_eq "local inline initializer exit" "$(docker wait "${cid}")" "0"
"${vm_compose[@]}" up -d --no-build --pull never backend >"${WORK}/up.log" 2>&1 \
  || die "release initializer path failed: $(tail -5 "${WORK}/up.log")"
cid="$("${vm_compose[@]}" ps -a -q minio-init)"
expect_eq "minio-init exit" "$(docker inspect --format '{{.State.Status}}:{{.State.ExitCode}}' "${cid}")" "exited:0"
expect_contains "release run on local state" "$(docker logs "${cid}" 2>&1)" "initialized (changes: 0)"
expect_eq "backend placeholder" "$("${vm_compose[@]}" ps --format '{{.State}}' backend)" "running"
expect_initialized_state "local-then-release" "${project}_default"
pass "release image accepts local-initializer state unchanged and releases the dependent service"

# A conflict introduced later must stop the initializer and keep the dependent down.
"${vm_compose[@]}" rm -sf backend minio-init >/dev/null 2>&1
expect_ok "attach readwrite" "$(admin "${project}_default" admin policy attach t readwrite --user "${APP_KEY}")"
rc=0
"${vm_compose[@]}" up -d --no-build --pull never backend >"${WORK}/up.log" 2>&1 || rc=$?
[[ ${rc} -ne 0 ]] || die "compose up succeeded despite a failing initializer"
expect_contains "compose reports initializer failure" "$(cat "${WORK}/up.log")" "minio-init"
cid="$("${vm_compose[@]}" ps -a -q minio-init)"
expect_eq "minio-init exit" "$(docker inspect --format '{{.State.Status}}:{{.State.ExitCode}}' "${cid}")" "exited:1"
expect_eq "backend not started" "$("${vm_compose[@]}" ps --format '{{.State}}' backend)" ""
pass "initializer failure propagates: non-zero exit and the dependent service is not started"

printf '\nAll %d MinIO initializer checks passed for %s (%s) against the %s server %s.\n' \
  "${passed}" "${IMAGE}" "${IMAGE_ID}" "${SERVER}" "${SERVER_IMAGE}"
