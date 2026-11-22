#!/usr/bin/env bash
# CodeMastery - infrastructure/release/release.sh   (ADR015, Phase 4 release pipeline)
#
# Release stages run by .github/workflows/release.yml. Each stage works on exact
# artifacts and fails closed; nothing is retried, skipped or overwritten.
#
#   release.sh build    SOURCE OUT REVISION ORIGIN   build the five candidates, export the
#                                                    mc Corresponding Source, save the
#                                                    candidate archive
#   release.sh qualify  SOURCE TOOLING OUT REVISION ORIGIN
#                                                    run the source revision's suites on the
#                                                    exact candidate images, reloaded from
#                                                    the candidate archive
#   release.sh scan     OUT SUFFIX REF...            Trivy reports (and, for the candidate,
#                                                    SPDX SBOMs) of exact images
#   release.sh promote  CANDIDATE_DIR OUT VERSION    retag the admitted images to the release
#                                                    names and save the release archive
#   release.sh smoke    SOURCE ARCHIVE MANIFEST      deploy.sh preflight/load/provision and
#                                                    update-guard checks on a disposable
#                                                    GitHub-hosted runner only
#   release.sh verify-archive SOURCE ARCHIVE MANIFEST [--release-names]
#                                                    deploy.sh's own archive verifier, for
#                                                    both image stores
#
# SOURCE is a checkout of the release source revision: its Dockerfiles, deploy.sh,
# Compose files and qualification suites are the ones the VM checkout will use.
# TOOLING is this repository at the dispatching revision. No secret is used, read
# or printed: the smoke stage generates throwaway credentials and certificates.
set -Eeuo pipefail
umask 022

TOOLING_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
RELEASE_PY="${TOOLING_DIR}/infrastructure/release/release.py"
readonly SERVICES=(postgres minio minio-init backend frontend)
# Scanner: the official Trivy image, selected only by its index digest.
readonly TRIVY_IMAGE='aquasec/trivy:0.76.2@sha256:8f6bb947dbc528902e11f2f5be52f7b519f6a39d5d148f41166f2040499768fb'

log()  { printf '[release %s] %s\n' "$(date -u +%H:%M:%SZ)" "$*"; }
fail() { printf 'RELEASE ERROR: %s\n' "$*" >&2; exit 1; }

candidate_ref() { printf 'codemastery-candidate/%s:%s' "$1" "${2:0:12}"; }
release_ref()   { printf 'codemastery.invalid/%s:release' "$1"; }

image_store() {
  local t; t="$(docker info --format '{{range .DriverStatus}}{{if eq (index . 0) "driver-type"}}{{index . 1}}{{end}}{{end}}')"
  case "$t" in (io.containerd.snapshotter.v1) echo containerd ;; ("") echo graphdriver ;; (*) fail "Unrecognized Docker image store." ;; esac
}

quiet_build() {  # label docker-build-args...; prints the end of the log only on failure
  local label="$1" log rc=0; shift
  log="$(mktemp)"
  BUILDKIT_PROGRESS=plain docker build "$@" >"$log" 2>&1 || rc=$?
  if [[ $rc -ne 0 ]]; then
    printf 'docker build of %s failed (exit %d); last 80 lines of its log:\n' "$label" "$rc" >&2
    tail -n 80 "$log" >&2
  fi
  rm -f "$log"
  return "$rc"
}

require_revision() { [[ "$1" =~ ^[0-9a-f]{40}$ ]] || fail "The source revision must be a full 40-character commit SHA."; }

refuse_release_names() {
  local existing
  existing="$(docker image ls --format '{{.Repository}}:{{.Tag}}' | grep '^codemastery\.invalid/' || true)"
  [[ -z "$existing" ]] || fail "codemastery.invalid/* images already exist on this host; release names are applied only by promotion."
}

# ---------------------------------------------------------------------------
build() {
  local source="$1" out="$2" revision="$3" origin="$4" service ref sha expected cid
  require_revision "$revision"
  [[ "$(git -C "$source" rev-parse HEAD)" == "$revision" ]] || fail "SOURCE is not checked out at the requested revision."
  [[ -z "$(git -C "$source" status --porcelain)" ]] || fail "SOURCE has uncommitted changes."
  [[ "$(uname -m)" == x86_64 ]] || log "Build host is $(uname -m); images are still built for linux/amd64."
  refuse_release_names
  mkdir -p "$out"
  for service in "${SERVICES[@]}"; do
    ref="$(candidate_ref "$service" "$revision")"
    ! docker image inspect "$ref" >/dev/null 2>&1 || fail "Candidate tag $ref already exists; refusing to reuse it."
  done
  log "Building five linux/amd64 candidates from ${revision}."
  quiet_build backend --platform linux/amd64 -t "$(candidate_ref backend "$revision")" "$source/backend"
  quiet_build frontend --platform linux/amd64 --target release --build-arg "PUBLIC_ORIGIN=${origin}" \
    -t "$(candidate_ref frontend "$revision")" "$source/frontend"
  quiet_build postgres --platform linux/amd64 -t "$(candidate_ref postgres "$revision")" "$source/infrastructure/postgres"
  quiet_build minio-init --platform linux/amd64 -t "$(candidate_ref minio-init "$revision")" "$source/infrastructure/minio"
  # The minimal server Dockerfile copies only from its digest-pinned stage: no build context.
  quiet_build minio --platform linux/amd64 -t "$(candidate_ref minio "$revision")" - < "$source/infrastructure/vm/Dockerfile.minio-server"

  # The initializer's mc is a modified AGPL-3.0-or-later build. Its Corresponding
  # Source comes from the same Dockerfile and revision and must equal both the
  # suite's recorded value and the SOURCE file inside the exact image.
  local work; work="$(mktemp -d)"
  quiet_build mc-source --platform linux/amd64 --target mc-source-archive \
    --output "type=local,dest=${work}/mc-source" "$source/infrastructure/minio"
  sha="$(sha256sum "$work/mc-source/mc-source.tar.gz" | awk '{print $1}')"
  expected="$(sed -n "s/^INIT_MC_SOURCE_SHA256='\([0-9a-f]\{64\}\)'$/\1/p" "$source/infrastructure/minio/test-minio-init.sh")"
  [[ "$expected" =~ ^[0-9a-f]{64}$ ]] || fail "Cannot read INIT_MC_SOURCE_SHA256 from the source revision's suite."
  [[ "$sha" == "$expected" ]] || fail "Exported mc Corresponding Source does not match the suite's recorded SHA-256."
  cid="$(docker create "$(candidate_ref minio-init "$revision")")"
  docker cp "$cid:/usr/share/doc/mc/SOURCE" "$work/SOURCE" >/dev/null
  docker rm "$cid" >/dev/null
  grep -Fq "$sha" "$work/SOURCE" || fail "The initializer image's SOURCE file does not name the exported archive."
  install -m 0644 "$work/mc-source/mc-source.tar.gz" "$out/mc-source.tar.gz"
  rm -rf "$work"

  local refs=()
  for service in "${SERVICES[@]}"; do refs+=("$(candidate_ref "$service" "$revision")"); done
  docker save --platform linux/amd64 -o "$out/codemastery-candidate-${revision:0:12}-linux-amd64.tar" "${refs[@]}"
  chmod 0644 "$out/codemastery-candidate-${revision:0:12}-linux-amd64.tar"
  local ref_args=(); for ref in "${refs[@]}"; do ref_args+=(--ref "$ref"); done
  python3 "$RELEASE_PY" archive-identities "$out/codemastery-candidate-${revision:0:12}-linux-amd64.tar" \
    "${ref_args[@]}" > "$out.identities.json"
  log "Candidate archive saved; mc Corresponding Source ${sha} verified against the suite and the image."
}

# ---------------------------------------------------------------------------
# deploy.sh's own archive verifier (sourced from SOURCE) for both image stores.
# Also used by verify-release.sh on an operator workstation: keep it bash 3.2-compatible.
verify_archive() {
  local source="$1" archive="$2" manifest="$3" names="${4:-}" store line args
  local flag=""; [[ "$names" != --release-names ]] || flag=--release-names
  for store in containerd graphdriver; do
    args=()
    while IFS= read -r line; do args+=("$line"); done \
      < <(python3 "$RELEASE_PY" expect-images "$manifest" --store "$store" ${flag:+"$flag"})
    [[ ${#args[@]} -eq 5 ]] || fail "The manifest does not name five image identities."
    bash -c '
      set -Eeuo pipefail
      source "$1/infrastructure/vm/deploy.sh"
      ARCHIVE="$2"; IMAGE_STORE="$3"; shift 3
      EXPECT_IMAGES=("$@")
      RELEASE_IMAGES="$(for e in "$@"; do printf "%s\n" "${e%%=*}"; done)"
      ARCHIVE_SHA256="$(sha256sum "$ARCHIVE" | awk "{print \$1}")"
      verify_archive' _ "$source" "$archive" "$store" "${args[@]}" \
      || fail "deploy.sh rejects the archive for the $store image store."
  done
  log "deploy.sh verify_archive accepts $(basename "$archive") for the containerd and graph-driver stores."
}

# ---------------------------------------------------------------------------
qualify() {
  local source="$1" tooling="$2" out="$3" revision="$4" origin="$5" archive store service ref want got pin
  archive="$out/codemastery-candidate-${revision:0:12}-linux-amd64.tar"
  local results="$out.qualification.json" entries=()
  record() { entries+=("$(python3 -c 'import json,sys; print(json.dumps({"id":sys.argv[1],"description":sys.argv[2],"result":"PASS","suite":sys.argv[3]}))' "$1" "$2" "$3")"); }
  store="$(image_store)"

  log "Reloading the candidates from the archive so the archived bytes are what is qualified."
  for service in "${SERVICES[@]}"; do docker image rm "$(candidate_ref "$service" "$revision")" >/dev/null; done
  docker load -i "$archive" >/dev/null
  for service in "${SERVICES[@]}"; do
    ref="$(candidate_ref "$service" "$revision")"
    want="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))[sys.argv[2]][sys.argv[3]])' "$out.identities.json" "$ref" "$store")"
    got="$(docker image inspect --format '{{.Id}}' "$ref")"
    [[ "$got" == "$want" ]] || fail "Loaded $service identity does not match the archive."
    [[ "$(docker image inspect --format '{{.Os}}/{{.Architecture}}' "$ref")" == linux/amd64 ]] || fail "$service is not linux/amd64."
  done
  record Q00-archive-reload "Candidates reloaded from the archive; image IDs equal the archive identities ($store store), linux/amd64" "release.sh"

  # MinIO: the source revision's initializer suite on the exact initializer and
  # the exact minimal server (aliased to the tag the suite expects).
  # Pre-pull the suite's digest pins, as the CI job does: an on-demand pull would
  # mix Docker's progress output into the suite's captured results.
  local pins=()
  while IFS= read -r pin; do pins+=("$pin"); done < <(sed -n 's/^    image: \(ghcr\.io\/golithus\/.*\)$/\1/p' "$source/infrastructure/docker-compose.yml")
  [[ ${#pins[@]} -eq 2 ]] || fail "Unexpected local MinIO image pins."
  for pin in CHAINGUARD_SERVER_IMAGE INIT_BASE_IMAGE; do
    pins+=("$(sed -n "s/^${pin}='\(cgr\.dev\/chainguard\/[a-z-]*@sha256:[0-9a-f]\{64\}\)'$/\1/p" "$source/infrastructure/minio/test-minio-init.sh")")
  done
  for pin in "${pins[@]}"; do
    [[ "$pin" == *@sha256:* ]] || fail "Cannot read a digest pin from the suite."
    docker pull -q --platform linux/amd64 "$pin" >/dev/null
  done
  docker tag "$(candidate_ref minio "$revision")" codemastery-test/minio-server:qualification
  MINIO_INIT_TEST_SERVER=minimal bash "$source/infrastructure/minio/test-minio-init.sh" "$(candidate_ref minio-init "$revision")"
  docker image rm codemastery-test/minio-server:qualification >/dev/null
  record Q01-minio-initializer "Initializer suite (provisioning, bucket-scoped policy, conflict stop, idempotency, release model) against the exact minimal server" "infrastructure/minio/test-minio-init.sh"

  bash "$source/infrastructure/vm/tests/test-java-truststore.sh" "$(candidate_ref backend "$revision")"
  record Q02-backend-truststore "Backend Java trust store: no compromised eMudhra roots; build-fault regressions" "infrastructure/vm/tests/test-java-truststore.sh"

  docker tag "$(candidate_ref backend "$revision")" codemastery-backend:privilege-test
  (cd "$source" && bash infrastructure/vm/tests/test-runtime-real-backend-privileges.sh)
  docker image rm codemastery-backend:privilege-test >/dev/null
  record Q03-backend-runtime "Exact backend: Flyway as migrator, runtime privilege matrix, Auth and consumer endpoints, upgrade path" "infrastructure/vm/tests/test-runtime-real-backend-privileges.sh"

  (cd "$source" && POSTGRES_FLAT_IMAGE="$(candidate_ref postgres "$revision")" \
     bash "$tooling/infrastructure/vm/tests/test-postgres-image-layer-equivalence.sh")
  record Q04-postgres-byte-clean "Exact PostgreSQL: one layer, no whiteout or gosu bytes, filesystem and configuration equal to the layered build" "infrastructure/vm/tests/test-postgres-image-layer-equivalence.sh"

  hardening "$(candidate_ref frontend "$revision")" "$(candidate_ref backend "$revision")" "$(candidate_ref minio "$revision")" "$origin"
  record Q05-image-hardening "Frontend UID 101 with root-owned read-only Nginx configuration and the baked origin; no setuid/setgid file in backend, frontend or MinIO server" "release.sh"

  printf '%s\n' "${entries[@]}" | python3 -c 'import json,sys; json.dump([json.loads(l) for l in sys.stdin if l.strip()], open(sys.argv[1], "w"), indent=2)' "$results"
  log "Qualification of the exact candidates passed (${#entries[@]} checks)."
}

hardening() {
  local frontend="$1" backend="$2" minio="$3" origin="$4" host image health
  host="${origin#https://}"
  docker run --rm --entrypoint sh "$frontend" -ec '
    [ "$(id -u)" = 101 ]
    bad="$(find /etc/nginx ! -type l \( ! -user root -o ! -group root -o -perm -g+w -o -perm -o+w \))"
    [ -z "$bad" ] || { echo "Nginx configuration not root-owned and read-only" >&2; exit 1; }
    for target in /etc/nginx/nginx.conf /etc/nginx/conf.d/default.conf; do
      if (: > "$target") 2>/dev/null; then echo "UID 101 can write $target" >&2; exit 1; fi
    done
    : > /var/cache/nginx/probe && rm /var/cache/nginx/probe'
  health="$(docker image inspect --format '{{json .Config.Healthcheck.Test}}' "$frontend")"
  [[ "$health" == '["CMD","/usr/local/bin/codemastery-https-healthcheck"]' ]] || fail "The frontend HTTPS healthcheck is missing."
  docker run --rm --entrypoint grep "$frontend" -qF -- "--resolve '${host}:8080:127.0.0.1' 'https://${host}:8080/'" \
    /usr/local/bin/codemastery-https-healthcheck || fail "The frontend healthcheck is not bound to ${origin}."
  docker run --rm --entrypoint grep "$frontend" -q "server_name ${host};" /etc/nginx/conf.d/default.conf \
    || fail "The frontend ingress is not rendered for ${origin}."
  for image in "$backend" "$frontend" "$minio"; do
    python3 - "$image" <<'PY' || fail "setuid/setgid file found in $image"
import subprocess, sys, tarfile
cid = subprocess.run(["docker", "create", sys.argv[1]], check=True, capture_output=True, text=True).stdout.strip()
try:
    proc = subprocess.Popen(["docker", "export", cid], stdout=subprocess.PIPE)
    with tarfile.open(fileobj=proc.stdout, mode="r|") as t:
        bad = [m.name for m in t if m.isreg() and m.mode & 0o6000]
    proc.wait()
    if proc.returncode or bad:
        sys.exit(1)
finally:
    subprocess.run(["docker", "rm", cid], check=True, capture_output=True)
PY
  done
}

# ---------------------------------------------------------------------------
scan() {
  local out="$1" suffix="$2" refs=("${@:3}") work service ref first=yes common
  [[ "$suffix" == candidate || "$suffix" == rescan ]] || fail "scan suffix must be candidate or rescan."
  work="$(mktemp -d)"; chmod 0755 "$work"; mkdir -p "$work/cache" "$work/out"
  docker pull -q --platform linux/amd64 "$TRIVY_IMAGE" >/dev/null
  trivy() { docker run --rm --platform linux/amd64 --user "$(id -u):$(id -g)" -e HOME=/tmp -v "$work:/work" "$TRIVY_IMAGE" --cache-dir /work/cache "$@"; }
  for ref in "${refs[@]}"; do
    service="${ref#*/}"; service="${service%%:*}"
    docker save --platform linux/amd64 -o "$work/$service.tar" "$ref"
    chmod 0644 "$work/$service.tar"
    common=(image --input "/work/$service.tar" --scanners vuln --quiet --timeout 30m)
    # One vulnerability database for the whole stage; the Java index is fetched
    # when the first JAR is met and then reused while it is current.
    [[ "$first" == yes ]] || common+=(--skip-db-update)
    first=no
    local name="$service.trivy"; [[ "$suffix" == candidate ]] || name="$service.trivy-rescan"
    trivy "${common[@]}" --format json --output "/work/out/$name.json"
    trivy "${common[@]}" --skip-db-update --detection-priority comprehensive \
      --format json --output "/work/out/$name-comprehensive.json"
    if [[ "$suffix" == candidate ]]; then
      trivy image --input "/work/$service.tar" --quiet --skip-db-update \
        --format spdx-json --output "/work/out/$service.spdx.json"
    fi
    rm -f "$work/$service.tar"
  done
  trivy version --format json > "$work/version.json"
  python3 - "$work/version.json" "$TRIVY_IMAGE" > "$out.scanner-$suffix.json" <<'PY'
import json, sys
v = json.load(open(sys.argv[1]))
def db(d):
    d = d or {}
    return {k: d.get(k) for k in ("Version", "UpdatedAt", "NextUpdate", "DownloadedAt")}
print(json.dumps({"tool": "trivy", "version": v.get("Version"), "image": sys.argv[2],
                  "vulnerability_db": db(v.get("VulnerabilityDB")), "java_db": db(v.get("JavaDB")),
                  "modes": ["default", "comprehensive"], "scanners": ["vuln"],
                  "suppressions": "none"}, indent=2, sort_keys=True))
PY
  install -m 0644 "$work"/out/* "$out/"
  rm -rf "$work"
  log "Scanned ${#refs[@]} exact images (${suffix})."
}

# ---------------------------------------------------------------------------
promote() {
  local candidate="$1" out="$2" version="$3" manifest store service cref rref want got refs=()
  manifest="$candidate/release-candidate.json"
  [[ "$version" =~ ^v[0-9]+\.[0-9]+\.[0-9]+(-[0-9A-Za-z]+(\.[0-9A-Za-z]+)*)?$ ]] || fail "Malformed release version."
  refuse_release_names
  store="$(image_store)"
  docker load -i "$candidate/$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["archive"]["name"])' "$manifest")" >/dev/null
  for service in "${SERVICES[@]}"; do
    read -r cref want < <(python3 -c 'import json,sys; i=json.load(open(sys.argv[1]))["images"][sys.argv[2]]; print(i["candidate_ref"], i[sys.argv[3]])' "$manifest" "$service" "$store")
    got="$(docker image inspect --format '{{.Id}}' "$cref")"
    [[ "$got" == "$want" ]] || fail "Loaded candidate $service does not have the admitted identity."
    rref="$(release_ref "$service")"
    docker tag "$cref" "$rref"
    [[ "$(docker image inspect --format '{{.Id}}' "$rref")" == "$want" ]] || fail "Promotion changed the $service identity."
    refs+=("$rref")
  done
  mkdir -p "$out"
  docker save --platform linux/amd64 -o "$out/codemastery-release-${version}-linux-amd64.tar" "${refs[@]}"
  chmod 0644 "$out/codemastery-release-${version}-linux-amd64.tar"
  local ref_args=(); for rref in "${refs[@]}"; do ref_args+=(--ref "$rref"); done
  python3 "$RELEASE_PY" archive-identities "$out/codemastery-release-${version}-linux-amd64.tar" \
    "${ref_args[@]}" > "$out.identities.json"
  python3 - "$out.identities.json" "$out" <<'PY'
import json, sys
ids = json.load(open(sys.argv[1]))
order = ["postgres", "minio", "minio-init", "backend", "frontend"]
for store in ("containerd", "graphdriver"):
    with open("%s/expected-images.%s.txt" % (sys.argv[2], store), "w") as f:
        for s in order:
            ref = "codemastery.invalid/%s:release" % s
            f.write("%s=%s\n" % (ref, ids[ref][store]))
PY
  log "Promoted the admitted identities to the release names; release archive saved."
}

# ---------------------------------------------------------------------------
# Disposable-runner smoke: deploy.sh's real preflight, load and provision paths
# on the exact archive, then its update guard. Refuses to run anywhere but a
# fresh GitHub-hosted runner, because it creates /etc/codemastery and a
# Compose project named codemastery and removes them afterwards.
smoke() {
  local source="$1" archive="$2" manifest="$3" script override origin tls env_dir work sha out expect=() s
  [[ "${GITHUB_ACTIONS:-}" == true && "${RUNNER_ENVIRONMENT:-}" == github-hosted ]] \
    || fail "The stack smoke runs only on a disposable GitHub-hosted runner."
  [[ ! -e /etc/codemastery ]] || fail "/etc/codemastery already exists; refusing to modify it."
  [[ -z "$(docker ps -aq --filter label=com.docker.compose.project=codemastery)" ]] || fail "A codemastery Compose project already exists."
  [[ -z "$(docker volume ls -q --filter label=com.docker.compose.project=codemastery)" ]] || fail "codemastery volumes already exist."
  script="$source/infrastructure/vm/deploy.sh"
  override="$source/infrastructure/vm/docker-compose.vm.yml"
  origin="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["public_origin"])' "$manifest")"
  tls=/etc/codemastery/tls
  env_dir=/root/codemastery-release-smoke
  work="$(mktemp -d)"; chmod 0700 "$work"
  smoke_cleanup() {
    sudo docker compose --project-name codemastery --env-file "$env_dir/.env" -f "$source/infrastructure/docker-compose.yml" \
      -f "$override" down -v --remove-orphans >/dev/null 2>&1 || true
    sudo rm -rf /etc/codemastery "$env_dir" "$work"
  }
  trap smoke_cleanup EXIT

  openssl ecparam -name prime256v1 -genkey -noout -out "$work/privkey.pem"
  openssl req -x509 -new -key "$work/privkey.pem" -sha256 -days 1 -subj "/CN=${origin#https://}" \
    -addext "subjectAltName=DNS:${origin#https://}" -out "$work/fullchain.pem" 2>/dev/null
  sudo install -d -m 700 -o root -g root /etc/codemastery
  sudo install -d -m 700 -o 101 -g 101 "$tls"
  sudo install -m 400 -o 101 -g 101 "$work/privkey.pem" "$tls/privkey.pem"
  sudo install -m 644 -o root -g root "$work/fullchain.pem" "$tls/fullchain.pem"
  rm -f "$work/privkey.pem"
  secret() { openssl rand -hex 16; }
  {
    printf 'POSTGRES_DB=codemastery\nPOSTGRES_USER=cm_bootstrap\nPOSTGRES_PASSWORD=%s\n' "$(secret)"
    printf 'DB_NAME=codemastery\nDB_USER=cm_runtime\nDB_PASSWORD=%s\n' "$(secret)"
    printf 'SPRING_FLYWAY_URL=jdbc:postgresql://postgres:5432/codemastery\nSPRING_FLYWAY_USER=cm_migrator\nSPRING_FLYWAY_PASSWORD=%s\n' "$(secret)"
    printf 'MINIO_ROOT_USER=smoke-root\nMINIO_ROOT_PASSWORD=%s\nSTORAGE_ACCESS_KEY=smoke-app\nSTORAGE_SECRET_KEY=%s\n' "$(secret)" "$(secret)"
    printf 'JWT_SECRET=%s\nCORS_ALLOWED_ORIGINS=%s\n' "$(openssl rand -hex 32)" "$origin"
  } > "$work/env"
  sudo install -d -m 700 -o root -g root "$env_dir"
  sudo install -m 600 -o root -g root "$work/env" "$env_dir/.env"
  rm -f "$work/env"

  local store; store="$(image_store)"
  mapfile -t expect < <(python3 "$RELEASE_PY" expect-images "$manifest" --store "$store" --release-names)
  local expect_args=(); for s in "${expect[@]}"; do expect_args+=(--expect-image "$s"); done
  sha="$(sha256sum "$archive" | awk '{print $1}')"
  local base=(--env-file "$env_dir/.env" --origin "$origin" --compose-override "$override")
  run_deploy() { sudo bash "$script" "$@" 2>&1; }
  expect_refusal() {  # fragment label args...
    local want="$1" label="$2"; shift 2
    if out="$(run_deploy "$@")"; then echo "deploy.sh accepted: $label" >&2; exit 1; fi
    [[ "$out" == *"$want"* ]] || { printf 'deploy.sh refused %s for another reason:\n%s\n' "$label" "$(printf '%s\n' "$out" | tail -5)" >&2; exit 1; }
    log "refused as required: $label"
  }

  # The release names must come from the archive, not from earlier steps.
  for s in "${SERVICES[@]}"; do docker image rm "$(release_ref "$s")" >/dev/null 2>&1 || true; done
  out="$(run_deploy preflight "${base[@]}")" || { printf '%s\n' "$out" | tail -20 >&2; fail "deploy.sh preflight failed."; }
  log "deploy.sh preflight passed."

  local auth=(--deployment-authorization CI-RELEASE-SMOKE-NOT-A-DEPLOYMENT)
  expect_refusal 'Archive SHA-256 mismatch' 'wrong archive SHA-256' load "${base[@]}" --archive "$archive" \
    --archive-sha256 "$(printf '0%.0s' {1..64})" "${expect_args[@]}" "${auth[@]}"
  local swapped=("${expect_args[@]}"); swapped[1]="${expect[1]%%=*}=${expect[0]#*=}"; swapped[3]="${expect[0]%%=*}=${expect[1]#*=}"
  expect_refusal 'Archive does not contain exactly the verified' 'swapped image identities' load "${base[@]}" \
    --archive "$archive" --archive-sha256 "$sha" "${swapped[@]}" "${auth[@]}"
  expect_refusal 'Every effective Compose service image must have' 'a missing service identity' load "${base[@]}" \
    --archive "$archive" --archive-sha256 "$sha" "${expect_args[@]:0:8}" "${auth[@]}"

  out="$(run_deploy load "${base[@]}" --archive "$archive" --archive-sha256 "$sha" "${expect_args[@]}" "${auth[@]}")" \
    || { printf '%s\n' "$out" | tail -20 >&2; fail "deploy.sh load failed on the exact archive."; }
  log "deploy.sh load verified and loaded the exact archive."

  local prov=(--wait-timeout 300 --initializer-qualification CI-RELEASE-SMOKE-Q01
              --provision-authorization CI-RELEASE-SMOKE-NOT-A-PROVISIONING)
  out="$(run_deploy provision "${base[@]}" "${expect_args[@]}" "${prov[@]}")" \
    || { printf '%s\n' "$out" | tail -30 >&2; fail "deploy.sh provision failed with the exact images."; }
  [[ "$out" == *"PROVISION PROCESS CHECK: postgres ready, minio running, minio-init exited 0."* ]] \
    || fail "deploy.sh provision did not report its process check."
  log "deploy.sh provision passed with the exact PostgreSQL, MinIO and initializer images."

  # Persistent state now exists: the update guard must stop any change without
  # the four P9 attestations, and before loading anything.
  expect_refusal 'No verified recovery artifact attested' 'load over existing state without update attestations' \
    load "${base[@]}" --archive "$archive" --archive-sha256 "$sha" "${expect_args[@]}" "${auth[@]}"
  expect_refusal 'No verified recovery artifact attested' 'provision over existing state without update attestations' \
    provision "${base[@]}" "${expect_args[@]}" "${prov[@]}"
  expect_refusal 'Update requires verified schema/image compatibility' 'update with only the recovery attestation' \
    load "${base[@]}" --archive "$archive" --archive-sha256 "$sha" "${expect_args[@]}" "${auth[@]}" --recovery-artifact-verified
  [[ -n "$(docker volume ls -q --filter label=com.docker.compose.project=codemastery)" ]] || fail "Persistent volumes disappeared."
  log "Update guard intact: existing state stops load and provision without the P9 attestations; volumes untouched."
  trap - EXIT
  smoke_cleanup
  [[ ! -e /etc/codemastery && -z "$(docker ps -aq --filter label=com.docker.compose.project=codemastery)" ]] \
    || fail "The disposable smoke stack was not removed."
}

# ---------------------------------------------------------------------------
# Whole stages, as the workflow runs them.

run_context() {  # rehearsal(true|false) -> JSON describing the tooling and run
  python3 - "$1" <<'PY'
import json, os, sys
env = os.environ.get
server, repo, run = env("GITHUB_SERVER_URL", ""), env("GITHUB_REPOSITORY", ""), env("GITHUB_RUN_ID", "")
print(json.dumps({
    "tooling": {"workflow_ref": env("GITHUB_WORKFLOW_REF"), "sha": env("GITHUB_SHA")},
    "run": {"id": run, "attempt": env("GITHUB_RUN_ATTEMPT"), "event": env("GITHUB_EVENT_NAME"),
            "url": "%s/%s/actions/runs/%s" % (server, repo, run) if run else None,
            "rehearsal": sys.argv[1] == "true"},
}))
PY
}

# candidate-package SOURCE OUT REVISION ORIGIN REHEARSAL [SOURCE_CHECKS_JSON] [BASELINE_REVISION]
candidate_package() {
  local source="$1" out="$2" revision="$3" origin="$4" rehearsal="$5" checks="${6:-}" baseline="${7:-}"
  local archive="codemastery-candidate-${revision:0:12}-linux-amd64.tar" refs=() s
  [[ "$rehearsal" == true || "$rehearsal" == false ]] || fail "REHEARSAL must be true or false."
  [[ "$rehearsal" == true || -n "$checks" ]] || fail "A non-rehearsal candidate needs verified source checks."
  [[ "$origin" =~ ^https://[a-z0-9]([a-z0-9-]*[a-z0-9])?(\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+$ ]] || fail "Malformed public origin."
  [[ "$(image_store)" == containerd ]] || fail "Candidates are built on the containerd image store (OCI identities)."
  [[ ! -e "$out" ]] || fail "Output directory already exists."
  for s in "${SERVICES[@]}"; do refs+=("$(candidate_ref "$s" "$revision")"); done

  build "$source" "$out" "$revision" "$origin"
  qualify "$source" "$TOOLING_DIR" "$out" "$revision" "$origin"
  scan "$out" candidate "${refs[@]}"
  local reports=(); for s in "${SERVICES[@]}"; do reports+=(--report "$s=$out/$s.trivy.json" --report "$s=$out/$s.trivy-comprehensive.json"); done
  python3 "$RELEASE_PY" findings "${reports[@]}" --output "$out.findings.json" --coverage-output "$out.coverage.json"

  local baseline_args=() bdir
  if [[ -n "$baseline" ]]; then
    require_revision "$baseline"
    git -C "$source" merge-base --is-ancestor "$baseline" "$revision" || fail "The compatibility baseline is not an ancestor of the source revision."
    bdir="$(mktemp -d)"
    git -C "$source" archive "$baseline" backend/src/main/resources/db/migration | tar -x -C "$bdir"
    baseline_args=(--baseline-directory "$bdir/backend/src/main/resources/db/migration" --baseline-revision "$baseline")
  fi
  python3 "$RELEASE_PY" migrations "$source/backend/src/main/resources/db/migration" "${baseline_args[@]}" --output "$out.migrations.json"

  python3 - "$source" "$revision" "$origin" "$out" "$(run_context "$rehearsal")" > "$out.context.json" <<'PY'
import hashlib, json, os, subprocess, sys
source, revision, origin, out, ctx = sys.argv[1], sys.argv[2], sys.argv[3], sys.argv[4], json.loads(sys.argv[5])
def sha(path):
    return hashlib.sha256(open(os.path.join(source, path), "rb").read()).hexdigest()
def run(*args):
    return subprocess.run(args, check=True, capture_output=True, text=True).stdout.strip()
services = ["postgres", "minio", "minio-init", "backend", "frontend"]
ctx.update({
    "repository": os.environ.get("GITHUB_REPOSITORY") or "local",
    "source_revision": revision,
    "source_tree": run("git", "-C", source, "rev-parse", revision + "^{tree}"),
    "public_origin": origin,
    "builder": {"runner_image": "%s %s" % (os.environ.get("ImageOS", "unknown"), os.environ.get("ImageVersion", "")),
                "docker_server": run("docker", "version", "--format", "{{.Server.Version}}"),
                "buildx": run("docker", "buildx", "version"), "image_store": "containerd",
                "platform": "linux/amd64", "reproducible": False},
    "mc_source": {"name": "mc-source.tar.gz",
                  "sha256": hashlib.sha256(open(os.path.join(out, "mc-source.tar.gz"), "rb").read()).hexdigest(),
                  "equals_suite_record": True, "named_by_image_source_file": True,
                  "licence": "AGPL-3.0-or-later (modified MinIO Client); keep with any conveyed copy of the initializer image"},
    "compatibility": {"deploy_sh_sha256": sha("infrastructure/vm/deploy.sh"),
                      "compose_base_sha256": sha("infrastructure/docker-compose.yml"),
                      "compose_override_sha256": sha("infrastructure/vm/docker-compose.vm.yml"),
                      "release_names": {s: "codemastery.invalid/%s:release" % s for s in services}},
    "candidate_refs": {s: "codemastery-candidate/%s:%s" % (s, revision[:12]) for s in services},
})
print(json.dumps(ctx, indent=2, sort_keys=True))
PY
  install -m 0644 "$out.findings.json" "$out/findings.json"
  install -m 0644 "$out.qualification.json" "$out/qualification.json"
  install -m 0644 "$out.migrations.json" "$out/migrations.json"
  local check_args=(); [[ -z "$checks" ]] || check_args=(--source-checks "$checks")
  python3 "$RELEASE_PY" candidate-manifest --package "$out" --archive-name "$archive" --context "$out.context.json" \
    --identities "$out.identities.json" --findings "$out.findings.json" --migrations "$out.migrations.json" \
    --qualification "$out.qualification.json" --scanner "$out.scanner-candidate.json" --coverage "$out.coverage.json" \
    "${check_args[@]}"
  python3 "$RELEASE_PY" verify-package "$out" --kind candidate
  verify_archive "$source" "$out/$archive" "$out/release-candidate.json"

  # Pre-admission compatibility rehearsal: deploy.sh on a release-named copy that
  # exists only inside this disposable runner and is deleted here. It is not a
  # promotion and nothing with a release name leaves the runner (ADR015 D5).
  if [[ "${GITHUB_ACTIONS:-}" == true ]]; then
    local tmp; tmp="$(mktemp -d)"
    promote "$out" "$tmp/out" v0.0.0-rehearsal
    smoke "$source" "$tmp/out/codemastery-release-v0.0.0-rehearsal-linux-amd64.tar" "$out/release-candidate.json"
    for s in "${SERVICES[@]}"; do docker image rm "$(release_ref "$s")" >/dev/null 2>&1 || true; done
    rm -rf "$tmp"
  else
    log "Not on a GitHub-hosted runner: the deploy.sh stack smoke was skipped."
  fi
  log "Candidate package complete: $out"
}

# release-package CANDIDATE_DIR SOURCE OUT ADMISSION REHEARSAL REPO_ROOT [ADMISSION_COMMIT ADMISSION_PROVENANCE_JSON]
release_package() {
  local candidate="$1" source="$2" out="$3" admission="$4" rehearsal="$5" repo_root="$6" commit="${7:-}" provenance="${8:-}"
  local flag=() version refs=() s archive
  [[ "$rehearsal" == true || "$rehearsal" == false ]] || fail "REHEARSAL must be true or false."
  [[ "$rehearsal" == false ]] || flag=(--rehearsal)
  # A real promotion needs the admission's merge evidence (release.py admission-provenance).
  [[ "$rehearsal" == true || ( -n "$commit" && -s "$provenance" ) ]] || fail "A real promotion needs the admission commit and its provenance."
  [[ ! -e "$out" ]] || fail "Output directory already exists."
  python3 "$RELEASE_PY" verify-package "$candidate" --kind candidate --allow-extra release-candidate.sigstore.json
  python3 "$RELEASE_PY" verify-admission --admission "$admission" --candidate-manifest "$candidate/release-candidate.json" \
    --codeowners "$repo_root/.github/CODEOWNERS" --repo-root "$repo_root" "${flag[@]}" > "$out.admission-check.json"
  version="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["version"])' "$out.admission-check.json")"
  [[ "$(git -C "$source" rev-parse HEAD)" == "$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["source_revision"])' "$candidate/release-candidate.json")" ]] \
    || fail "SOURCE is not checked out at the candidate's source revision."
  verify_archive "$source" "$candidate/$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["archive"]["name"])' "$candidate/release-candidate.json")" \
    "$candidate/release-candidate.json"

  promote "$candidate" "$out" "$version"
  archive="codemastery-release-${version}-linux-amd64.tar"
  for s in "${SERVICES[@]}"; do refs+=("$(release_ref "$s")"); done
  scan "$out" rescan "${refs[@]}"
  local reports=(); for s in "${SERVICES[@]}"; do reports+=(--report "$s=$out/$s.trivy-rescan.json" --report "$s=$out/$s.trivy-rescan-comprehensive.json"); done
  python3 "$RELEASE_PY" findings "${reports[@]}" --output "$out.rescan-findings.json" --coverage-output "$out.rescan-coverage.json"
  python3 "$RELEASE_PY" check-rescan --admission "$admission" --candidate-manifest "$candidate/release-candidate.json" \
    --findings "$out.rescan-findings.json"

  install -m 0644 "$admission" "$out/admission.json"
  install -m 0644 "$candidate/release-candidate.json" "$out/release-candidate.json"
  install -m 0644 "$candidate/mc-source.tar.gz" "$out/mc-source.tar.gz"
  for s in "${SERVICES[@]}"; do install -m 0644 "$candidate/$s.spdx.json" "$out/$s.spdx.json"; done
  install -m 0644 "$out.rescan-findings.json" "$out/rescan-findings.json"
  python3 - "$(run_context "$rehearsal")" "$commit" "$provenance" > "$out.context.json" <<'PY'
import json, sys
ctx = json.loads(sys.argv[1])
ctx["admission_commit"] = sys.argv[2] or None
ctx["admission_provenance"] = json.load(open(sys.argv[3])) if sys.argv[3] else None
ctx["verification"] = {
    "candidate_package": "SHA256SUMS and release-candidate.json file hashes verified",
    "candidate_attestation": "verified by the promote job before this stage" if not ctx["run"]["rehearsal"] else "REHEARSAL: no attestation",
    "admission": "schema, identities, candidate manifest/archive digests, accepted findings and code-owner decider verified"
                 + ("" if ctx["run"]["rehearsal"] else "; merged into main by decided_by in a pull request changing only the record"),
    "deploy_sh_verify_archive": "candidate and release archives accepted for the containerd and graph-driver stores",
    "rescan": "every promotion-time finding is an admitted candidate row (same installed version, severity not raised)",
    "stack_smoke": "deploy.sh preflight, load and provision on the release archive; update guard refuses existing state",
    "not_run": "deploy.sh deploy (needs a publicly trusted certificate for the origin), any VM operation",
}
print(json.dumps(ctx, indent=2, sort_keys=True))
PY
  python3 "$RELEASE_PY" release-manifest --package "$out" --archive-name "$archive" --context "$out.context.json" \
    --identities "$out.identities.json" --findings "$out.rescan-findings.json" --scanner "$out.scanner-rescan.json" \
    --coverage "$out.rescan-coverage.json"
  python3 "$RELEASE_PY" verify-package "$out" --kind release
  verify_archive "$source" "$out/$archive" "$out/release.json" --release-names
  smoke "$source" "$out/$archive" "$out/release.json"
  log "Release package ${version} complete: $out"
}

# ---------------------------------------------------------------------------
# Workflow helpers

enable_containerd_store() {  # disposable GitHub-hosted runner only
  [[ "${GITHUB_ACTIONS:-}" == true && "${RUNNER_ENVIRONMENT:-}" == github-hosted ]] \
    || fail "Changing the Docker daemon configuration is allowed only on a GitHub-hosted runner."
  sudo python3 - <<'PY'
import json, pathlib
path = pathlib.Path("/etc/docker/daemon.json")
text = path.read_text() if path.exists() else ""
config = json.loads(text) if text.strip() else {}
config.setdefault("features", {})["containerd-snapshotter"] = True
path.write_text(json.dumps(config, indent=2) + "\n")
PY
  sudo systemctl restart docker
  for _ in {1..30}; do docker info >/dev/null 2>&1 && break; sleep 2; done
  [[ "$(image_store)" == containerd ]] || fail "The containerd image store is not active."
  docker version --format 'Docker Engine {{.Server.Version}}, {{.Server.Os}}/{{.Server.Arch}}, containerd image store'
}

# rehearsal-admission create CANDIDATE_DIR DEST_DIR   -> prints the record path
# rehearsal-admission refusals CANDIDATE_DIR REPO_ROOT -> fail-closed checks on the real manifest
rehearsal_admission() {
  local mode="$1" candidate="$2" arg="$3" manifest id
  manifest="$candidate/release-candidate.json"
  id="REHEARSAL-${GITHUB_RUN_ID:-LOCAL}-${GITHUB_RUN_ATTEMPT:-1}"
  [[ "$mode" == create || "$mode" == refusals ]] || fail "rehearsal-admission create|refusals"
  local work; work="$(mktemp -d)"
  python3 "$RELEASE_PY" admission-template --candidate-manifest "$manifest" \
    --release-tag "candidate-$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["source_revision"][:12])' "$manifest")-${GITHUB_RUN_ID:-0}-${GITHUB_RUN_ATTEMPT:-1}" \
    --version v0.0.0-rehearsal > "$work/template.json"
  python3 - "$work/template.json" "$id" > "$work/$id.json" <<'PY'
import datetime, json, sys
a = json.load(open(sys.argv[1]))
a.update(record_id=sys.argv[2], decision="ADMITTED", decided_by="rehearsal-ci", rehearsal=True,
         decided_on=datetime.date.today().isoformat(),
         scope="Pull-request rehearsal of the promotion mechanism. Admits nothing; never published.")
print(json.dumps(a, indent=2, sort_keys=True))
PY
  if [[ "$mode" == create ]]; then
    install -m 0644 "$work/$id.json" "$arg/$id.json"
    rm -rf "$work"
    printf '%s\n' "$arg/$id.json"
    return
  fi
  local repo_root="$arg" n=0
  refused() {  # label json-mutation [--real]
    local label="$1" mutation="$2" real="${3:-}" file="$work/case/$id.json" flags=(--rehearsal) rc=0
    mkdir -p "$work/case"
    python3 -c 'import json,sys; a=json.load(open(sys.argv[1])); exec(sys.argv[2]); print(json.dumps(a))' \
      "$work/$id.json" "$mutation" > "$file"
    [[ "$real" != --real ]] || flags=()
    python3 "$RELEASE_PY" verify-admission --admission "$file" --candidate-manifest "$manifest" \
      --codeowners "$repo_root/.github/CODEOWNERS" --repo-root "$repo_root" "${flags[@]}" >/dev/null 2>&1 || rc=$?
    [[ $rc -eq 3 ]] || fail "Admission accepted or crashed (rc=$rc): $label"
    n=$((n + 1)); log "refused as required: $label"
  }
  python3 "$RELEASE_PY" verify-admission --admission "$work/$id.json" --candidate-manifest "$manifest" \
    --codeowners "$repo_root/.github/CODEOWNERS" --repo-root "$repo_root" --rehearsal >/dev/null \
    || fail "The unmodified rehearsal record must verify."
  refused 'rehearsal record used for a real promotion' 'pass' --real
  refused 'decision PENDING' 'a["decision"]="PENDING"'
  refused 'swapped backend/frontend identity' 'a["images"]["backend"]["containerd"], a["images"]["frontend"]["containerd"] = a["images"]["frontend"]["containerd"], a["images"]["backend"]["containerd"]'
  refused 'different candidate manifest digest' 'a["candidate"]["manifest_sha256"]="0"*64'
  refused 'different archive digest' 'a["candidate"]["archive_sha256"]="0"*64'
  refused 'different source revision' 'a["source_revision"]="0"*40'
  refused 'non-REHEARSAL record ID in a rehearsal' 'a["record_id"]="CM-REL-ADMISSION-NOT-REHEARSAL"'
  refused 'unknown field' 'a["approved_by_ci"]=True'
  refused 'acceptance of a finding the candidate did not report' 'a["accepted_findings"].append({"service":"backend","id":"CVE-0000-00000","package":"injected"})'
  if python3 -c 'import json,sys; sys.exit(0 if json.load(open(sys.argv[1]))["accepted_findings"] else 1)' "$work/$id.json"; then
    refused 'a candidate finding left unaccepted' 'a["accepted_findings"]=a["accepted_findings"][1:]'
  else
    log "The candidate has no findings; the unaccepted-finding case is covered by the unit tests."
  fi
  printf '[{"service":"backend","id":"CVE-0000-00000","package":"injected","installed":"1","fixed":"","severity":"CRITICAL","target":"t"}]\n' > "$work/injected.json"
  if python3 "$RELEASE_PY" check-rescan --admission "$work/$id.json" --candidate-manifest "$manifest" \
       --findings "$work/injected.json" >/dev/null 2>&1; then
    fail "A promotion-time finding outside the admission was accepted."
  fi
  n=$((n + 1)); log "refused as required: promotion-time finding outside the admission"
  python3 "$RELEASE_PY" check-rescan --admission "$work/$id.json" --candidate-manifest "$manifest" \
    --findings "$candidate/findings.json" >/dev/null || fail "The candidate's own findings must be covered by its admission."
  if python3 -c 'import json,sys; f=json.load(open(sys.argv[1])); sys.exit(0 if any(x["severity"] != "CRITICAL" for x in f) else 1)' "$candidate/findings.json"; then
    python3 -c 'import json,sys; f=json.load(open(sys.argv[1])); x=next(x for x in f if x["severity"] != "CRITICAL"); x["severity"]="CRITICAL"; print(json.dumps([x]))' \
      "$candidate/findings.json" > "$work/raised.json"
    if python3 "$RELEASE_PY" check-rescan --admission "$work/$id.json" --candidate-manifest "$manifest" \
         --findings "$work/raised.json" >/dev/null 2>&1; then
      fail "A promotion-time severity increase was accepted."
    fi
    n=$((n + 1)); log "refused as required: a finding whose severity rose after the candidate scan"
  fi
  rm -rf "$work"
  log "$n admission refusals verified against the real candidate manifest."
}

release_api() { gh api -H 'X-GitHub-Api-Version: 2022-11-28' "$@"; }

# draft-create TAG TARGET TITLE NOTES PACKAGE_DIR BUNDLE: a new draft only, never overwrites.
draft_create() {
  local tag="$1" target="$2" title="$3" notes="$4" dir="$5" bundle="$6" repo="${GITHUB_REPOSITORY:?}" files=() f
  [[ "$tag" =~ ^candidate-[0-9a-f]{12}-[0-9]+-[0-9]+$ || "$tag" =~ ^v[0-9]+\.[0-9]+\.[0-9]+(-[0-9A-Za-z]+(\.[0-9A-Za-z]+)*)?$ ]] \
    || fail "Malformed release tag."
  require_revision "$target"
  # Both probes fail closed: an API error stops the stage instead of reading as "absent".
  local tags existing
  tags="$(release_api "repos/$repo/git/matching-refs/tags/$tag" --jq "[.[] | select(.ref == \"refs/tags/$tag\")] | length")" \
    || fail "Cannot read the repository's tags."
  [[ "$tags" == 0 ]] || fail "Tag $tag already exists; a release is never replaced."
  existing="$(release_api --paginate "repos/$repo/releases?per_page=100" --jq ".[] | select(.tag_name == \"$tag\") | .id")" \
    || fail "Cannot list the repository's releases."
  [[ -z "$existing" ]] || fail "A release or draft named $tag already exists; a release is never replaced."
  for f in "$dir"/*; do files+=("$f"); done
  files+=("$bundle")
  local marker="<!-- codemastery-release-run: ${GITHUB_RUN_ID:-}-${GITHUB_RUN_ATTEMPT:-} -->"
  printf '\n%s\n' "$marker" >> "$notes"
  if ! gh release create "$tag" --repo "$repo" --draft --target "$target" --title "$title" --notes-file "$notes" "${files[@]}"; then
    # Remove only the partial draft this run created; never a published release.
    local id; id="$(release_api --paginate "repos/$repo/releases?per_page=100" \
      --jq ".[] | select(.tag_name == \"$tag\" and .draft == true and (.body | contains(\"$marker\"))) | .id")"
    [[ -z "$id" ]] || release_api -X DELETE "repos/$repo/releases/$id" >/dev/null || true
    fail "Creating the draft release failed; its partial draft was removed."
  fi
  # Read back: the draft must hold exactly the local files, byte for byte.
  local id listing; id="$(release_api --paginate "repos/$repo/releases?per_page=100" --jq ".[] | select(.tag_name == \"$tag\" and .draft == true) | .id")"
  listing="$(release_api --paginate "repos/$repo/releases/$id/assets?per_page=100" --jq '.[] | [.name, .size, (.digest // "")] | @tsv')"
  python3 - "$listing" "${files[@]}" <<'PY' || fail "The draft's assets do not match the local package."
import hashlib, os, sys
remote = {}
for line in sys.argv[1].splitlines():
    name, size, digest = line.split("\t")
    remote[name] = (int(size), digest)
local = {os.path.basename(p): p for p in sys.argv[2:]}
if set(remote) != set(local):
    sys.exit(1)
for name, path in local.items():
    size, digest = remote[name]
    if size != os.path.getsize(path):
        sys.exit(1)
    if digest and digest != "sha256:" + hashlib.sha256(open(path, "rb").read()).hexdigest():
        sys.exit(1)
PY
  log "Draft release $tag holds exactly the ${#files[@]} attested package files; it is not published."
}

# draft-download TAG DEST: the assets of the one workflow-created draft with that tag.
draft_download() {
  local tag="$1" dest="$2" repo="${GITHUB_REPOSITORY:?}" id rows aid name size digest
  [[ "$tag" =~ ^candidate-[0-9a-f]{12}-[0-9]+-[0-9]+$ ]] || fail "Malformed candidate tag."
  [[ ! -e "$dest" ]] || fail "Download directory already exists."
  rows="$(release_api --paginate "repos/$repo/releases?per_page=100" \
    --jq ".[] | select(.tag_name == \"$tag\") | [.id, .draft, .author.login] | @tsv")"
  [[ -n "$rows" && "$rows" != *$'\n'* ]] || fail "Exactly one release named $tag is required."
  IFS=$'\t' read -r id draft author <<<"$rows"
  [[ "$draft" == true && "$author" == 'github-actions[bot]' ]] || fail "$tag is not a workflow-created draft."
  mkdir -p "$dest"
  while IFS=$'\t' read -r aid name size digest; do
    [[ "$name" =~ ^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$ ]] || fail "Unexpected asset name."
    release_api -H 'Accept: application/octet-stream' "repos/$repo/releases/assets/$aid" > "$dest/$name"
    [[ "$(wc -c < "$dest/$name" | tr -d ' ')" == "$size" ]] || fail "Asset $name is incomplete."
    [[ -z "$digest" || "$digest" == "sha256:$(sha256sum "$dest/$name" | awk '{print $1}')" ]] || fail "Asset $name does not match its digest."
  done < <(release_api --paginate "repos/$repo/releases/$id/assets?per_page=100" --jq '.[] | [.id, .name, .size, (.digest // "")] | @tsv')
  log "Downloaded the candidate draft $tag."
}

case "${1:-}" in
  (candidate-package) shift; candidate_package "$@" ;;
  (enable-containerd-store) shift; enable_containerd_store "$@" ;;
  (rehearsal-admission) shift; rehearsal_admission "$@" ;;
  (draft-create)   shift; draft_create "$@" ;;
  (draft-download) shift; draft_download "$@" ;;
  (release-package)   shift; release_package "$@" ;;
  (build)          shift; build "$@" ;;
  (qualify)        shift; qualify "$@" ;;
  (scan)           shift; scan "$@" ;;
  (promote)        shift; promote "$@" ;;
  (smoke)          shift; smoke "$@" ;;
  (verify-archive) shift; verify_archive "$@" ;;
  (*) sed -n '2,26p' "${BASH_SOURCE[0]}" >&2; exit 2 ;;
esac
