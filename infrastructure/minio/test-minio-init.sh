#!/usr/bin/env bash
# CodeMastery - infrastructure/minio/test-minio-init.sh   (DP01 initializer qualification)
#
# Integration tests for the MinIO initializer image against disposable MinIO
# Community servers (the pinned local server image). Each scenario uses its own
# private Docker network and server; everything created is labelled and removed.
#
#   infrastructure/minio/test-minio-init.sh            build infrastructure/minio and test it
#   infrastructure/minio/test-minio-init.sh IMAGE      test an existing image reference or ID
#
# The built image is tagged codemastery-test/minio-init:qualification, never a
# codemastery.invalid/*:release name. MINIO_INIT_TEST_PLATFORM (default
# linux/amd64, the VM platform) selects the platform. Requires Docker with
# Compose v2.24+ and python3. Images are not pulled implicitly.
set -Eeuo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
PLATFORM="${MINIO_INIT_TEST_PLATFORM:-linux/amd64}"
RUN_ID="mi$$x${RANDOM}"
LABEL="codemastery.minio-init-test=${RUN_ID}"
WORK="$(mktemp -d)"
PROJECTS=()

BASE_COMPOSE="${ROOT}/infrastructure/docker-compose.yml"
VM_COMPOSE="${ROOT}/infrastructure/vm/docker-compose.vm.yml"
# The approved local pins are the single source of truth for both images.
SERVER_IMAGE="$(sed -n 's/^    image: \(ghcr\.io\/golithus\/minio:.*\)$/\1/p' "${BASE_COMPOSE}")"
MC_IMAGE="$(sed -n 's/^    image: \(ghcr\.io\/golithus\/mc:.*\)$/\1/p' "${BASE_COMPOSE}")"

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
  rm -rf "${WORK}"
}
trap cleanup EXIT

die() { printf 'FAIL: %s\n' "$1" >&2; exit 1; }
pass() { passed=$((passed + 1)); printf 'ok %d - %s\n' "${passed}" "$1"; }
section() { printf '\n== %s\n' "$1"; }

[[ -n "${SERVER_IMAGE}" && -n "${MC_IMAGE}" ]] || die "Cannot read the pinned MinIO images from ${BASE_COMPOSE}."

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

# Starts a fresh server on a new private network; prints the network name.
start_server() {  # name
  local net="cm-${RUN_ID}-$1"
  docker network create --label "${LABEL}" "${net}" >/dev/null
  docker run -d --platform "${PLATFORM}" \
    --name "${net}-minio" --label "${LABEL}" --network "${net}" --network-alias minio \
    -e MINIO_ROOT_USER="${ROOT_USER}" -e MINIO_ROOT_PASSWORD="${ROOT_PASS}" \
    "${SERVER_IMAGE}" >/dev/null
  local out
  out="$(CM_TIMEOUT=60 admin "${net}" ready t)"
  [[ "${out}" == *'"healthy":true'* ]] || die "MinIO test server $1 did not become ready."
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
printf 'server image: %s\nmc image: %s\n' "${SERVER_IMAGE}" "${MC_IMAGE}"
docker image inspect "${SERVER_IMAGE}" >/dev/null 2>&1 \
  || die "Pull the pinned server image first: docker pull --platform ${PLATFORM} ${SERVER_IMAGE}"

bash -n "${ROOT}/infrastructure/minio/minio-init.sh"
bash -n "${BASH_SOURCE[0]}"
pass "shell syntax"

from="$(sed -n 's/^FROM //p' "${ROOT}/infrastructure/minio/Dockerfile")"
expect_eq "Dockerfile base is the approved local mc pin" "${from}" "${MC_IMAGE}"
expect_contains "server pin" "${SERVER_IMAGE}" "@sha256:8793e960474071520bdb91bb9a6d1793eb229fdbc9dcb7ec75ad836b69bbab40"
expect_contains "mc pin" "${MC_IMAGE}" "@sha256:dde55ccc0bc9e65dc582c8e652d652eea1438c6e60d774524f4da15d7b26a900"
pass "server and mc images keep the approved digest pins"

cfg="$(docker image inspect --format '{{.Config.User}}|{{json .Config.Entrypoint}}|{{json .Config.Cmd}}|{{json .Config.Env}}|{{json .Config.ExposedPorts}}|{{json .Config.Volumes}}|{{json .Config.Healthcheck}}|{{.Os}}/{{.Architecture}}' "${IMAGE}")"
expect_eq "image configuration" "${cfg}" \
  '65532:65532|["/usr/local/bin/codemastery-minio-init"]|null|["PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"]|null|null|null|'"${PLATFORM}"
pass "non-root user, own entrypoint, no command, secrets, ports, volumes or healthcheck"

runtime="$(docker run --rm --platform "${PLATFORM}" --network none --label "${LABEL}" --entrypoint /bin/bash "${IMAGE}" -c '
  printf "uid=%s gid=%s\n" "$(id -u)" "$(id -g)"
  while read -r k v; do case "$k" in CapInh:|CapPrm:|CapEff:|CapAmb:) printf "%s%s\n" "$k" "$v";; esac; done </proc/self/status
  [ -w /usr/local/bin/codemastery-minio-init ] && echo "entrypoint writable"
  [ -w / ] && echo "root writable"
  [ -w /tmp ] || echo "tmp not writable"
  true')"
expect_eq "runtime identity and capabilities" "${runtime}" \
  $'uid=65532 gid=65532\nCapInh:0000000000000000\nCapPrm:0000000000000000\nCapEff:0000000000000000\nCapAmb:0000000000000000'
pass "runs as UID 65532 with no effective, permitted or ambient capabilities"

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
for k in ab 'has space' 'quo"te' 'back\slash' 'abcdefghijklmnopqrstu'; do
  check_rejected "access key '${k}'" "STORAGE_ACCESS_KEY must be 3-20" "STORAGE_ACCESS_KEY=${k}"
done
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
# Test-only images: no qualified server exists (the pinned local server stands in),
# and a sleeping placeholder stands in for the backend that waits on minio-init.
cat > "${WORK}/local-ports.yml" <<EOF
services:
  minio:
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

printf '\nAll %d MinIO initializer checks passed for %s (%s).\n' "${passed}" "${IMAGE}" "${IMAGE_ID}"
