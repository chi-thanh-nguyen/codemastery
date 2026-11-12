#!/usr/bin/env bash
# CI regression test for job "VM deployment PostgreSQL and containment tests" (deployment-script-postgres),
# step "Check the PostgreSQL image refuses a data volume it did not initialize" in .github/workflows/ci-vm.yml.
# Body moved verbatim from that step's former inline `run:` script.
# CI runs it from the repository root, where its relative paths resolve:
#   bash infrastructure/vm/tests/test-postgres-foreign-volume-guard.sh
set -euo pipefail
# infrastructure/postgres starts only on an empty volume or one it initialized:
# a glibc (Bookworm) cluster's indexes would be misread by musl without any error.
image=codemastery-ci/postgres:volume-guard
prefix="codemastery-ci-pg-guard-${GITHUB_RUN_ID:-local}-$$"
cleanup() {
  docker rm -f "$prefix-glibc" "$prefix-refused" "$prefix-fresh" >/dev/null 2>&1 || true
  docker volume rm "$prefix-glibc" "$prefix-fresh" >/dev/null 2>&1 || true
  docker image rm "$image" >/dev/null 2>&1 || true
}
trap cleanup EXIT
docker build -q -t "$image" infrastructure/postgres >/dev/null
ready() {  # container: the final server, never the entrypoint's initdb-time one
  # The temporary initialization server listens only on the Unix socket
  # (listen_addresses=''), so TCP readiness identifies the final server; the
  # socket check then covers the path the assertions connect through.
  for _ in $(seq 1 60); do
    docker exec "$1" pg_isready -h 127.0.0.1 -U postgres >/dev/null 2>&1 \
      && docker exec "$1" pg_isready -U postgres >/dev/null 2>&1 && return 0
    sleep 1
  done
  docker logs "$1" 2>&1 | tail -20 >&2; return 1
}
fingerprint() {  # volume: every file's path and content
  docker run --rm -v "$1:/data:ro" --entrypoint sh "$image" -c \
    'cd /data && find . -type f -exec sha256sum {} + | sort | sha256sum'
}
docker run -d --name "$prefix-glibc" -e POSTGRES_PASSWORD="$(openssl rand -hex 16)" \
  -v "$prefix-glibc:/var/lib/postgresql/data" postgres:17.11-bookworm >/dev/null
ready "$prefix-glibc"
docker stop "$prefix-glibc" >/dev/null
before="$(fingerprint "$prefix-glibc")"
docker run -d --name "$prefix-refused" -v "$prefix-glibc:/var/lib/postgresql/data" "$image" >/dev/null
status="$(timeout 60 docker wait "$prefix-refused")" \
  || { echo "PostgreSQL kept running on a glibc-initialized volume." >&2; exit 1; }
[[ "$status" == 78 ]] || { echo "Expected exit 78 on a glibc volume, got $status." >&2; exit 1; }
[[ "$(docker logs "$prefix-refused" 2>&1)" == *"codemastery-postgres: refusing to start"* ]]
[[ "$(fingerprint "$prefix-glibc")" == "$before" ]] || { echo "The refused volume was changed." >&2; exit 1; }
echo "ok: a Bookworm-initialized volume is refused with exit 78 and left unchanged"
docker run --rm -v "$prefix-glibc:/var/lib/postgresql/data" "$image" psql --version >/dev/null
echo "ok: client tools still run against the refused volume"
docker run -d --name "$prefix-fresh" -e POSTGRES_PASSWORD="$(openssl rand -hex 16)" \
  -v "$prefix-fresh:/var/lib/postgresql/data" "$image" >/dev/null
ready "$prefix-fresh"
[[ "$(docker exec "$prefix-fresh" cat /var/lib/postgresql/data/codemastery-cluster-libc)" == musl ]]
docker exec "$prefix-fresh" psql -X -q -U postgres -c 'CREATE TABLE kept(v text)' -c "INSERT INTO kept VALUES ('kept')"
docker restart "$prefix-fresh" >/dev/null
ready "$prefix-fresh"
[[ "$(docker exec "$prefix-fresh" psql -X -At -U postgres -c 'SELECT v FROM kept')" == kept ]]
echo "ok: a fresh volume is initialized, marked and restarted with its data"
