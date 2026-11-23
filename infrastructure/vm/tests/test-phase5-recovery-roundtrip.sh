#!/usr/bin/env bash
# CI regression test for job "VM continuous deployment and recovery" in .github/workflows/ci-vm.yml (ADR017).
#
# A disposable stand-in of the VM's Compose project (real PostgreSQL 17 with the project's
# migrations, real MinIO with versions, delete markers and IAM, stand-in backend/frontend
# writers), synthetic keys and credentials only, zero published ports:
#   capture (scheduled and held pre-update) -> independent publication with full
#   verification and an isolated PostgreSQL restore -> MinIO restore into a fresh volume and
#   a server boot with the same identity -> fault injection (writer exclusion, lock
#   contention, corrupt/forged/replayed sets).
# Run from the repository root:  bash infrastructure/vm/tests/test-phase5-recovery-roundtrip.sh
# Needs Docker and OpenSSL 3 (`openssl` on Ubuntu; CODEMASTERY_OPENSSL elsewhere).
set -Eeuo pipefail
export CODEMASTERY_OPENSSL="${CODEMASTERY_OPENSSL:-openssl}"
OPENSSL="${CODEMASTERY_OPENSSL}"
"${OPENSSL}" version | grep -q '^OpenSSL 3\.' || { echo "OpenSSL 3 is required (CODEMASTERY_OPENSSL)." >&2; exit 1; }
PY="${PYTHON:-python3}"
project="cmrt$(printf '%s' "${GITHUB_RUN_ID:-local}$$" | cksum | cut -c1-8)"
network="${project}_default"
PG_IMAGE="${PG_IMAGE:-postgres:17.11-bookworm}"
# The release's minimal MinIO server (no baked MINIO_* settings, UID 65532), built from its pinned source.
MINIO_IMAGE="${MINIO_IMAGE:-}"
MC_IMAGE="$(sed -n 's/^    image: \(ghcr\.io\/golithus\/mc:.*\)$/\1/p' infrastructure/docker-compose.yml)"
HELPER_IMAGE="${HELPER_IMAGE:-ubuntu:24.04}"
STANDIN_IMAGE="${STANDIN_IMAGE:-alpine:3.24}"
TAR_MODE="${TAR_MODE:-container:${HELPER_IMAGE}}"
work="$(mktemp -d)"; chmod 700 "${work}"
containers=()
cleanup() {
  for c in ${containers[@]+"${containers[@]}"}; do docker rm -f -v "$c" >/dev/null 2>&1 || true; done
  docker ps -aq --filter "label=cmrt.project=${project}" | xargs -r docker rm -f -v >/dev/null 2>&1 || true
  for v in "${project}_postgres-data" "${project}_minio-data" "${project}_restored-minio"; do docker volume rm -f "$v" >/dev/null 2>&1 || true; done
  docker network rm "${network}" >/dev/null 2>&1 || true
  [[ -z "${built_image:-}" ]] || docker image rm "${built_image}" >/dev/null 2>&1 || true
  rm -rf "${work}"
}
trap cleanup EXIT
pass() { printf '  PASS: %s\n' "$*"; }
expect_refusal() {  # label substring python-snippet
  local out
  if out="$("${PY}" -I -c "$3" 2>&1)"; then echo "NOT REFUSED: $1 ${out}" >&2; exit 1; fi
  [[ "$out" == *"$2"* ]] || { echo "WRONG REFUSAL for $1: $out" >&2; exit 1; }
  printf '  refused: %-58s [%s]\n' "$1" "$2"
}
secret() { "${OPENSSL}" rand -hex 20; }
pg_pw="$(secret)"; minio_user="cmroot$(secret | cut -c1-8)"; minio_pw="$(secret)"

echo "== keys (synthetic, this run only)"
exec </dev/null   # OpenSSL must not inherit a non-terminal stdin (it then ignores -pass* sources)
mkdir -p "${work}/keys" "${work}/vm/backup" "${work}/ws"
pass_phrase="$(secret)"; printf '%s' "${pass_phrase}" > "${work}/keys/passphrase"
"${OPENSSL}" genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 -out "${work}/keys/plain.pem" 2>/dev/null
"${OPENSSL}" req -x509 -new -key "${work}/keys/plain.pem" -subj "/CN=CodeMastery recovery recipient (test)" -days 2 \
  -out "${work}/keys/recipient.crt"
"${OPENSSL}" pkcs8 -topk8 -v2 aes-256-cbc -v2prf hmacWithSHA256 -iter 600000 -passout "file:${work}/keys/passphrase" \
  -in "${work}/keys/plain.pem" -out "${work}/keys/recipient.encrypted.pem"
rm -f "${work}/keys/plain.pem"
"${OPENSSL}" genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 -out "${work}/keys/producer.key" 2>/dev/null
"${OPENSSL}" pkey -in "${work}/keys/producer.key" -pubout -out "${work}/keys/producer.pub"

echo "== disposable project ${project}"
if [[ -z "${MINIO_IMAGE}" ]]; then
  MINIO_IMAGE="cmrt/minio-server:${project}"
  docker build -q -t "${MINIO_IMAGE}" - < infrastructure/vm/Dockerfile.minio-server >/dev/null
  built_image="${MINIO_IMAGE}"
fi
docker network create --internal --label "cmrt.project=${project}" "${network}" >/dev/null
for v in postgres-data minio-data; do docker volume create --label "com.docker.compose.project=${project}" "${project}_${v}" >/dev/null; done
labels() { echo --label "com.docker.compose.project=${project}" --label "com.docker.compose.service=$1" --label "cmrt.project=${project}"; }
docker run -d --name "${project}-postgres" $(labels postgres) --network "${network}" --network-alias postgres \
  -v "${project}_postgres-data:/var/lib/postgresql/data" -e POSTGRES_USER=cm_bootstrap -e "POSTGRES_PASSWORD=${pg_pw}" \
  -e POSTGRES_DB=codemastery --health-cmd 'pg_isready -U cm_bootstrap -d codemastery' --health-interval 2s \
  "${PG_IMAGE}" >/dev/null
docker run --rm -v "${project}_minio-data:/data" --network none "${HELPER_IMAGE}" chown 65532:65532 /data
docker run -d --name "${project}-minio" $(labels minio) --network "${network}" --network-alias minio --user 65532:65532 \
  -v "${project}_minio-data:/data" -e "MINIO_ROOT_USER=${minio_user}" -e "MINIO_ROOT_PASSWORD=${minio_pw}" -e MINIO_BROWSER=off \
  "${MINIO_IMAGE}" server /data >/dev/null
for s in backend frontend; do
  docker run -d --init --name "${project}-${s}" $(labels "$s") --network "${network}" "${STANDIN_IMAGE}" sleep 36000 >/dev/null
done
for i in $(seq 1 60); do
  [[ "$(docker inspect --format '{{.State.Health.Status}}' "${project}-postgres")" == healthy ]] && break; sleep 2
done
[[ "$(docker inspect --format '{{.State.Health.Status}}' "${project}-postgres")" == healthy ]]

echo "== fixture: the project's migrations, Flyway-shaped history, rows; MinIO versions and IAM"
{
  echo "CREATE TABLE public.flyway_schema_history (installed_rank integer PRIMARY KEY, version varchar(50), description varchar(200) NOT NULL, type varchar(20) NOT NULL, script varchar(1000) NOT NULL, checksum integer, installed_by varchar(100) NOT NULL, installed_on timestamp NOT NULL DEFAULT now(), execution_time integer NOT NULL, success boolean NOT NULL);"
  rank=0
  for f in backend/src/main/resources/db/migration/V*.sql; do
    rank=$((rank+1)); name="$(basename "$f")"; v="${name#V}"; v="${v%%__*}"
    cat "$f"; echo
    sum="$("${PY}" -I -c 'import sys; sys.path.insert(0,"infrastructure/vm/host"); import ops; print(ops.flyway_checksum(open(sys.argv[1],"rb").read()))' "$f")"
    desc="$(printf '%s' "${name#*__}" | sed 's/\.sql$//; s/_/ /g')"
    echo "INSERT INTO public.flyway_schema_history VALUES (${rank},'${v}','${desc}','SQL','${name}',${sum},'cm_bootstrap',now(),1,true);"
  done
  echo "INSERT INTO public.users (id,email,password_hash,display_name,role,account_status) SELECT gen_random_uuid(), 'u'||g||'@example.test', 'x', 'User '||g, 'learner', 'active' FROM generate_series(1,25) g;"
} | docker exec -i "${project}-postgres" psql -X -q -v ON_ERROR_STOP=1 -U cm_bootstrap -d codemastery >/dev/null
mc() { docker run --rm --network "${network}" -e "MC_HOST_t=http://${minio_user}:${minio_pw}@minio:9000" "${MC_IMAGE}" "$@"; }
for i in $(seq 1 30); do mc ls t >/dev/null 2>&1 && break; sleep 1; done
mc mb --with-versioning t/recovery >/dev/null
docker run --rm --network "${network}" -e "MC_HOST_t=http://${minio_user}:${minio_pw}@minio:9000" --entrypoint sh "${MC_IMAGE}" -c '
  set -e; printf one > /tmp/v; mc cp -q /tmp/v t/recovery/versioned.bin >/dev/null; printf two > /tmp/v; mc cp -q /tmp/v t/recovery/versioned.bin >/dev/null
  : > /tmp/z; mc cp -q /tmp/z t/recovery/zero.bin >/dev/null; head -c 3000000 /dev/urandom > /tmp/l; mc cp -q /tmp/l t/recovery/large.bin >/dev/null
  printf gone > /tmp/d; mc cp -q /tmp/d t/recovery/deleted.bin >/dev/null; mc rm -q t/recovery/deleted.bin >/dev/null'
app_secret="$(secret)"
mc admin user add t cmapp "${app_secret}" >/dev/null
before="$(mc ls --versions --recursive t/recovery | awk '{print $NF, $(NF-1)}' | sort)"

driver() {  # python snippet with recovery/ops importable and the run's paths
  cat <<PY
import sys, os, json
sys.path.insert(0, "infrastructure/vm/host")
import recovery
W = "${work}"
K = W + "/keys"
CAP = dict(backup_root=W + "/vm/backup", recipient_cert=K + "/recipient.crt", producer_key=K + "/producer.key",
           project="${project}", tar_mode="${TAR_MODE}", stop_timeout=20, log=lambda m: None)
PY
}
running() { [[ "$(docker inspect --format '{{.State.Running}}' "${project}-$1")" == true ]]; }

echo "== scheduled capture: writers stop, both stores captured, application resumed"
out="$("${PY}" -I -c "$(driver)
r = recovery.capture('scheduled', False, **CAP); print(json.dumps(r))")"
set_scheduled="$(printf '%s' "$out" | "${PY}" -I -c 'import json,sys; print(json.load(sys.stdin)["set_id"])')"
running backend && running frontend && running minio && running postgres
pass "captured ${set_scheduled}; backend, frontend and MinIO running again"
[[ -z "$(ls "${work}/vm/backup/sets" | grep partial || true)" ]]
"${PY}" -I -c "$(driver)
d = W + '/vm/backup/sets/${set_scheduled}'
m = recovery.verify_set(d, K + '/producer.pub', recovery.public_key_fingerprint(K + '/producer.pub'),
                        decrypt=(K + '/recipient.encrypted.pem', open(K + '/passphrase','rb').read(), K + '/recipient.crt'))
assert len(m['postgres']['flyway_history']) == 9 and m['postgres']['row_counts']['users'] == 25, m['postgres']
assert m['writer_exclusion']['stopped'] == ['backend', 'frontend']
"
pass "signature, hashes and authenticated decryption verified; 9 Flyway rows and 25 users recorded"

echo "== writer-exclusion and custody faults (each restores the application)"
docker exec -d "${project}-postgres" psql -U cm_bootstrap -d codemastery -c 'SELECT pg_sleep(600)'
sleep 2
expect_refusal "a PostgreSQL client session during the capture" "client sessions" "$(driver)
recovery.capture('scheduled', False, **CAP)"
running backend && running frontend; pass "application restarted after the refusal"
docker exec "${project}-postgres" psql -q -U cm_bootstrap -d codemastery -c "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE query LIKE '%pg_sleep%' AND pid <> pg_backend_pid()" >/dev/null
intruder="${project}-intruder"; containers+=("${intruder}")
docker run -d --init --name "${intruder}" --label "cmrt.project=${project}" --network "${network}" "${STANDIN_IMAGE}" sleep 600 >/dev/null
expect_refusal "an unknown container on the project network" "attached to the project network" "$(driver)
recovery.capture('scheduled', False, **CAP)"
docker rm -f "${intruder}" >/dev/null
init="${project}-minio-init"; containers+=("${init}")
docker run -d --init --name "${init}" $(labels minio-init) --network "${network}" "${STANDIN_IMAGE}" sleep 600 >/dev/null
expect_refusal "a running MinIO initializer" "initializer is running" "$(driver)
recovery.capture('scheduled', False, **CAP)"
docker rm -f "${init}" >/dev/null
expect_refusal "a missing recipient certificate" "custody is not provisioned" "$(driver)
CAP['recipient_cert'] = W + '/absent.crt'; recovery.capture('scheduled', False, **CAP)"
expect_refusal "a hold requested by a scheduled capture" "only a pre-update capture" "$(driver)
recovery.capture('scheduled', True, **CAP)"
running backend && running frontend && running minio

echo "== operations lock contention (ops.py)"
cat > "${work}/ops.conf" <<JSON
{"origin": "https://codemastery.app", "env_file": "/nonexistent", "wait_timeout": 60, "stop_timeout": 20,
 "tls_dir": "/nonexistent", "tls_backup_root": "/nonexistent", "acme_home": "/nonexistent", "acme_domain": "codemastery.app",
 "tar_mode": "${TAR_MODE}", "capacity": {}}
JSON
export CODEMASTERY_OPS_CONF="${work}/ops.conf" CODEMASTERY_STATE_DIR="${work}/vm" CODEMASTERY_RUN_DIR="${work}/run" \
       CODEMASTERY_PROJECT="${project}" CODEMASTERY_RECIPIENT_CERT="${work}/keys/recipient.crt" CODEMASTERY_PRODUCER_KEY="${work}/keys/producer.key"
mkdir -p "${work}/run"
"${PY}" -I -c "
import fcntl, os, time
fd = os.open('${work}/run/operations.lock', os.O_RDWR | os.O_CREAT, 0o600); fcntl.flock(fd, fcntl.LOCK_EX); time.sleep(20)" &
holder=$!; sleep 2
if out="$("${PY}" -I infrastructure/vm/host/ops.py capture --kind manual 2>&1)"; then echo "capture ran under a held lock" >&2; exit 1; fi
[[ "$out" == *"holds the operations lock"* ]] || { echo "unexpected: $out" >&2; exit 1; }
kill "${holder}" 2>/dev/null || true; wait "${holder}" 2>/dev/null || true
pass "a second operation is refused while the lock is held (no waiting, visible failure)"

echo "== held pre-update capture through ops.py"
out="$("${PY}" -I infrastructure/vm/host/ops.py capture --kind manual)"
pass "ops.py manual capture: $(printf '%s' "$out" | "${PY}" -I -c 'import json,sys; print(json.load(sys.stdin)["set_id"])')"
out="$("${PY}" -I -c "$(driver)
r = recovery.capture('pre-update', True, **CAP); print(json.dumps(r))")"
set_pre="$(printf '%s' "$out" | "${PY}" -I -c 'import json,sys; print(json.load(sys.stdin)["set_id"])')"
pre_sha="$(printf '%s' "$out" | "${PY}" -I -c 'import json,sys; print(json.load(sys.stdin)["manifest_sha256"])')"
! running backend && ! running frontend && running minio && running postgres
[[ -f "${work}/vm/backup/hold.json" ]]
pass "held ${set_pre}: backend and frontend stay stopped, data services running"
expect_refusal "a capture while a hold is active" "hold is active" "$(driver)
recovery.capture('scheduled', False, **CAP)"
status="$("${PY}" -I infrastructure/vm/host/ops.py status)"
printf '%s' "$status" | "${PY}" -I -c 'import json,sys; d=json.load(sys.stdin); assert d["hold"]["set_id"]=="'"${set_pre}"'", d'
pass "ops.py status reports the hold"

echo "== workstation publication: verify, decrypt, isolated restore, independent copy, ledger, acknowledgement"
mkdir -p "${work}/ws/keys" "${work}/ws/trust" "${work}/ws/sets" "${work}/ws/ledger" "${work}/ws/incoming"
cp "${work}/keys/recipient.encrypted.pem" "${work}/keys/recipient.crt" "${work}/ws/keys/"
cp "${work}/keys/producer.pub" "${work}/ws/trust/producer.pub.pem"
export CODEMASTERY_RECOVERY_PASSPHRASE_FILE="${work}/keys/passphrase"
publish() {  # set-id -> runs publish_set with an in-process remote over the VM staging directory
  "${PY}" -I -c "$(driver)
ws = recovery.Workstation(W + '/ws')
cfg = {'vm': {}, 'producer_key_sha256': recovery.public_key_fingerprint(K + '/producer.pub')}
root = W + '/vm/backup'
class Remote(object):
    def fetch(self, s, n, dest):
        import shutil; shutil.copyfile(recovery.staged_file(s, n, root), dest)
    def ops(self, *a, **k):
        assert a[0] == 'recovery-ack'; recovery.acknowledge(a[1], a[2], a[3], 'operator', root); return b'{}'
entry = [e for e in recovery.list_staged(root) if e['set_id'] == '$1'][0]
restore = lambda d, m: recovery.restore_test(d, m, ws, '${PG_IMAGE}', log=lambda x: None)
print(json.dumps(recovery.publish_set(ws, cfg, Remote(), entry, restore=restore, log=lambda x: None)))"
}
rec="$(publish "${set_scheduled}")"
printf '%s' "$rec" | "${PY}" -I -c 'import json,sys; r=json.load(sys.stdin); assert r["level"]=="VERIFIED_INDEPENDENT+RESTORE_TESTED", r; assert r["restore_test"]["postgres"]=="PASS" and r["restore_test"]["minio"].startswith("STRUCTURE_PASS"), r'
pass "scheduled set published: PostgreSQL restored under F1 with equal history and row counts; MinIO structure verified"
rec="$(publish "${set_pre}")"
[[ -f "${work}/vm/backup/acks/${set_pre}.json" ]]
grep -q '"level": "VERIFIED_INDEPENDENT+RESTORE_TESTED"' "${work}/vm/backup/acks/${set_pre}.json"
pass "pre-update set published and acknowledged on the VM side (deploy gate evidence)"
[[ "$(wc -l < "${work}/ws/ledger/ledger.jsonl" | tr -d ' ')" == 2 ]]

echo "== replay, substitution and corruption at the publisher"
expect_refusal "republishing a set under a different manifest" "differs from the published one" "$(driver)
ws = recovery.Workstation(W + '/ws'); root = W + '/vm/backup'
entry = [e for e in recovery.list_staged(root) if e['set_id'] == '${set_pre}'][0]
entry['manifest_sha256'] = '0' * 64
recovery.publish_set(ws, {'vm': {}, 'producer_key_sha256': ''}, None, entry)"
cp -a "${work}/vm/backup/sets/${set_scheduled}" "${work}/corrupt"
printf 'X' | dd of="${work}/corrupt/minio-data.tar.cms" bs=1 seek=200 conv=notrunc 2>/dev/null
expect_refusal "a corrupted staged component" "does not match its manifest hash" "$(driver)
recovery.verify_set(W + '/corrupt', K + '/producer.pub', recovery.public_key_fingerprint(K + '/producer.pub'))"

echo "== MinIO restore into a fresh volume with the identical server and identity (Q04 scope)"
"${PY}" -I -c "$(driver)
d = W + '/ws/sets/${set_scheduled}'
m = json.load(open(d + '/manifest.json'))
c = [x for x in m['components'] if x['name'] == 'minio-data.tar.cms'][0]
out = open(W + '/minio.tar', 'wb')
n, h = recovery.decrypt_stream(d + '/minio-data.tar.cms', K + '/recipient.encrypted.pem', open(K + '/passphrase','rb').read(),
                               K + '/recipient.crt', consume=out.write)
out.close(); assert (n, h) == (c['payload_length'], c['payload_sha256'])"
docker volume create "${project}_restored-minio" >/dev/null
docker run --rm -i --network none -v "${project}_restored-minio:/data" --entrypoint tar "${HELPER_IMAGE}" \
  --same-owner --numeric-owner --xattrs --acls -xpf - -C /data < "${work}/minio.tar"
rm -f "${work}/minio.tar"
restored="${project}-minio-restored"; containers+=("${restored}")
docker run -d --name "${restored}" --label "cmrt.project=${project}" --network "${network}" --network-alias minio-restored \
  --user 65532:65532 -v "${project}_restored-minio:/data" -e "MINIO_ROOT_USER=${minio_user}" -e "MINIO_ROOT_PASSWORD=${minio_pw}" \
  -e MINIO_BROWSER=off "${MINIO_IMAGE}" server /data >/dev/null
mcr() { docker run --rm --network "${network}" -e "MC_HOST_r=http://${minio_user}:${minio_pw}@minio-restored:9000" "${MC_IMAGE}" "$@"; }
for i in $(seq 1 30); do mcr ls r >/dev/null 2>&1 && break; sleep 1; done
after="$(mcr ls --versions --recursive r/recovery | awk '{print $NF, $(NF-1)}' | sort)"
[[ "$after" == "$before" ]] || { echo "restored versions differ"; diff <(echo "$before") <(echo "$after"); exit 1; } >&2
mcr admin user info r cmapp >/dev/null
docker run --rm --network "${network}" -e "MC_HOST_a=http://cmapp:${app_secret}@minio-restored:9000" "${MC_IMAGE}" ls a/recovery >/dev/null 2>&1 \
  && { echo "a policy-less user could read the restored bucket" >&2; exit 1; }
pass "every version and delete marker restored; IAM user restored with its credential and still denied"

echo "== deploy recovery gate (ops.recovery_gate): evidence behind deploy.sh's P9 attestations"
gate() {  # python expression for the gate's problems, given set id and manifest digest
  "${PY}" -I -c "
import sys, json; sys.path.insert(0, 'infrastructure/vm/host')
import ops, recovery
found = recovery.Docker().project_containers('${project}')
report = {'volumes': ops.volumes_report(found)}
print(json.dumps(ops.recovery_gate('$1', '$2', found, report)['problems']))"
}
[[ "$(gate "${set_pre}" "${pre_sha}")" == "[]" ]] || { gate "${set_pre}" "${pre_sha}" >&2; exit 1; }
pass "held, acknowledged, restore-tested point: no gate problem"
[[ "$(gate "${set_pre}" "$(printf '%064d' 0)")" == *"not the held pre-update point"* ]]
pass "a different manifest digest is refused"
mv "${work}/vm/backup/acks/${set_pre}.json" "${work}/ack.bak"
[[ "$(gate "${set_pre}" "${pre_sha}")" == *"has not acknowledged"* ]]
mv "${work}/ack.bak" "${work}/vm/backup/acks/${set_pre}.json"
pass "an unacknowledged point is refused"
docker start "${project}-backend" >/dev/null; docker stop -t 2 "${project}-backend" >/dev/null
[[ "$(gate "${set_pre}" "${pre_sha}")" == *"ran after the recovery cut"* ]]
pass "a writer that ran after the cut is refused (writes could be missing from the point)"

echo "== resume after the hold (operator cancellation)"
"${PY}" -I infrastructure/vm/host/ops.py resume | grep -q '"result": "RESUMED"'
running backend && running frontend && [[ ! -f "${work}/vm/backup/hold.json" ]]
pass "held containers restarted; hold archived"
echo 'PHASE5_RECOVERY_ROUNDTRIP_PASS'
