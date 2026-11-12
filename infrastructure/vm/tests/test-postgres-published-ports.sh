#!/usr/bin/env bash
# CI regression test for job "VM deployment PostgreSQL and containment tests" (deployment-script-postgres),
# step "Exercise published-port verification against real containers" in .github/workflows/ci-vm.yml.
# Body moved verbatim from that step's former inline `run:` script.
# CI runs it from the repository root, where its relative paths resolve:
#   bash infrastructure/vm/tests/test-postgres-published-ports.sh
set -euo pipefail
script=infrastructure/vm/deploy.sh
prefix="codemastery-pr110-ports-${GITHUB_RUN_ID:-local}-$$"
names=(ingress loopback publish-all private stopped)
cleanup() {
  local name
  for name in "${names[@]}"; do docker rm -f "$prefix-$name" >/dev/null 2>&1 || true; done
}
trap cleanup EXIT
# Real Docker port state. Nothing listens on container port 8080.
start() {
  docker run --detach --name "$prefix-$1" -e POSTGRES_PASSWORD="$(openssl rand -hex 16)" \
    "${@:2}" postgres:17.11-bookworm >/dev/null
}
start ingress -p 443:8080
start loopback -p 127.0.0.1:15432:5432
start publish-all -P
start private
docker create --name "$prefix-stopped" -p 443:8080 postgres:17.11-bookworm >/dev/null
bash -c '
  set -euo pipefail
  source "$1"; prefix="$2"
  fake_compose() {
    [[ "$1 $2 $3" == "ps -a -q" && "$4" == "$CHECK_SERVICE" ]] || return 97
    printf "%s\n" "$CHECK_CONTAINER"
  }
  COMPOSE=(fake_compose)
  check() {  # expected service container [declared-host-ip]
    local want="$1" service="$2" result=accepted
    CHECK_SERVICE="$service"; CHECK_CONTAINER="$prefix-$3"
    COMPOSE_MODEL="$(python3 -c "import json,sys; port={\"target\": 8080, \"published\": \"443\"}
if sys.argv[1]: port[\"host_ip\"] = sys.argv[1]
print(json.dumps({\"services\": {\"frontend\": {\"ports\": [port]}}}))" "${4:-}")"
    (verify_published_ports "$service") >/dev/null 2>&1 || result=rejected
    echo "$result: $service on $3${4:+ (declared $4)}"
    [[ "$result" == "$want" ]] || { echo "Expected $want." >&2; exit 1; }
  }
  check accepted frontend ingress
  check accepted backend private
  check rejected backend ingress
  check rejected postgres loopback
  check rejected minio publish-all
  check rejected frontend ingress 203.0.113.10
  check rejected frontend stopped
  check rejected frontend private
  CHECK_SERVICE=frontend; CHECK_CONTAINER="$prefix-ingress"
  verify_published_ports frontend
  echo "Recorded evidence: $PUBLISHED_PORTS_RESULT"
  [[ "$PUBLISHED_PORTS_RESULT" == *"0.0.0.0:443"* ]]
' _ "$script" "$prefix"
