#!/usr/bin/env bash
# CodeMastery - infrastructure/vm/deploy.sh   (DP01, Interim scope)

# Implements, for the one-VM Docker Compose deployment, the CT17 technical
# policies and bindings recorded in docs/deployment.md 
# APPROVED_PENDING_PROTECTED_INTEGRATION).
#
# Covers : DP01.I-config (preflight), DP01.I-provision, DP01.I-deploy.
# Excludes: DP01.F-deploy (PostgreSQL/MinIO backup, restore, rollback execution,
#           certificate acquisition/renewal). 
# Safety rules enforced by design:
#   - never sources the env file (it is parsed as data);
#   - never prints secrets or the rendered Compose configuration;
#   - never builds on the VM and never pulls images (--no-build, --pull never);
#   - never runs `down -v`, volume removal, `flyway repair` or data reset;
#   - an update of an existing deployment requires an explicit attestation that
#     a verified recovery artifact exists (P9);
#   - PostgreSQL identities are separated (P6): the bootstrap administrator
#     (POSTGRES_USER) only provisions; Flyway runs as SPRING_FLYWAY_USER (owns the
#     schema objects); the backend runtime uses DB_USER (data access only, no DDL).
#     Existing roles are never overwritten, and separation is verified with
#     probes that are rolled back.
set -Eeuo pipefail
umask 077

readonly POLL_INTERVAL_SECONDS=5   # polling cadence only; the deadline is operator-supplied

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
BASE_COMPOSE="${REPO_ROOT}/infrastructure/docker-compose.yml"

SUBCOMMAND=""
ENV_FILE=""
ORIGIN=""
ARCHIVE=""
ARCHIVE_SHA256=""
SOURCE_REVISION=""
WAIT_TIMEOUT=""
RECOVERY_VERIFIED="no"
OVERRIDES=()
EXPECT_IMAGES=()
COMPOSE=()
DB_SEPARATION_RESULT="not checked"
HEALTH_RESULT="not checked"

log()  { printf '[%s] %s\n' "$(date -u +%H:%M:%SZ)" "$*"; }
fail() { printf 'ERROR: %s\n' "$*" >&2; exit 1; }

usage() {
  cat <<'EOF'
Usage: deploy.sh <preflight|load|provision|deploy|all> [options]

Always required:
  --env-file PATH               Real runtime env file (mode 0600, directory 0700)
  --origin https://HOST         The single approved external HTTPS origin

Compose:
  --compose-override PATH       Extra Compose file (repeatable). The local base
                                file infrastructure/docker-compose.yml is always
                                first. An override at an approved location is
                                needed to make it deployable (see preflight).

load / deploy / all:
  --archive PATH                `docker save` archive of the reviewed release
  --archive-sha256 HEX          Expected SHA-256 of that archive
  --expect-image REF=sha256:ID  Expected image content ID (repeatable)

provision / deploy / all:
  --wait-timeout SECONDS        Deadline for readiness waits (no default)

deploy / all:
  --source-revision REV         Source revision the images were built from

Updates:
  --recovery-artifact-verified  Operator attests that an independently checked,
                                restorable pre-update recovery artifact exists.
                                Required when a backend container already exists.
EOF
}

# Argument parsing

parse_args() {
  [[ $# -ge 1 ]] || { usage; exit 2; }
  SUBCOMMAND="$1"; shift
  while [[ $# -gt 0 ]]; do
    case "$1" in
      (--env-file)         ENV_FILE="${2:?--env-file needs a value}"; shift 2 ;;
      (--origin)           ORIGIN="${2:?--origin needs a value}"; shift 2 ;;
      (--compose-override) OVERRIDES+=("${2:?--compose-override needs a value}"); shift 2 ;;
      (--archive)          ARCHIVE="${2:?--archive needs a value}"; shift 2 ;;
      (--archive-sha256)   ARCHIVE_SHA256="${2:?--archive-sha256 needs a value}"; shift 2 ;;
      (--expect-image)     EXPECT_IMAGES+=("${2:?--expect-image needs a value}"); shift 2 ;;
      (--wait-timeout)     WAIT_TIMEOUT="${2:?--wait-timeout needs a value}"; shift 2 ;;
      (--source-revision)  SOURCE_REVISION="${2:?--source-revision needs a value}"; shift 2 ;;
      (--recovery-artifact-verified) RECOVERY_VERIFIED="yes"; shift ;;
      (-h|--help)          usage; exit 0 ;;
      (*)                  usage; fail "Unknown option: $1" ;;
    esac
  done
  case "${SUBCOMMAND}" in
    (preflight|load|provision|deploy|all) ;;
    (-h|--help) usage; exit 0 ;;
    (*) usage; fail "Unknown subcommand: ${SUBCOMMAND}" ;;
  esac
}

require_inputs() {
  [[ -n "${ENV_FILE}" ]] || fail "--env-file is required."
  [[ -n "${ORIGIN}" ]]   || fail "--origin is required."
  case "${SUBCOMMAND}" in
    (load|deploy|all)
      [[ -n "${ARCHIVE}" ]]        || fail "--archive is required for ${SUBCOMMAND}."
      [[ -n "${ARCHIVE_SHA256}" ]] || fail "--archive-sha256 is required for ${SUBCOMMAND}."
      [[ ${#EXPECT_IMAGES[@]} -ge 1 ]] || fail "At least one --expect-image is required for ${SUBCOMMAND}."
      ;;
  esac
  case "${SUBCOMMAND}" in
    (provision|deploy|all)
      [[ "${WAIT_TIMEOUT}" =~ ^[1-9][0-9]*$ ]] || fail "--wait-timeout must be a positive integer (seconds); no default is approved."
      ;;
  esac
  case "${SUBCOMMAND}" in
    (deploy|all)
      [[ -n "${SOURCE_REVISION}" ]] || fail "--source-revision is required for ${SUBCOMMAND}."
      ;;
  esac
}
# Env file handling (parsed as data, never sourced)
env_get() {
  # Last active KEY=value wins; CR stripped; no quoting/interpolation applied.
  awk -v k="$1" '
    { sub(/\r$/, "") }
    /^[[:space:]]*#/ { next }
    index($0, k "=") == 1 { v = substr($0, length(k) + 2); f = 1 }
    END { if (f) print v }
  ' "${ENV_FILE}"
}

need() {
  local v; v="$(env_get "$1")"
  [[ -n "${v}" ]] || fail "$1 is missing or empty in the env file."
}

need_secret_charset() {
  local v; v="$(env_get "$1")"
  [[ "${v}" =~ ^[A-Za-z0-9_.-]+$ ]] || fail "$1 must use only letters, digits, '_', '-' and '.' (shared Spring/Compose value format)."
}

validate_origin() {
  [[ "${ORIGIN}" =~ ^https://[a-z0-9]([a-z0-9-]*[a-z0-9])?(\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+$ ]] \
    || fail "--origin must be a bare HTTPS origin: https://hostname (no port, path, query or wildcard)."
  [[ ! "${ORIGIN}" =~ ^https://[0-9.]+$ ]] || fail "--origin must be a hostname, not an IP address."
  [[ "${ORIGIN}" != *localhost* ]] || fail "--origin must not be localhost."
}

validate_env_file() {
  [[ -f "${ENV_FILE}" && -r "${ENV_FILE}" ]] || fail "Env file not found or unreadable: ${ENV_FILE}"
  ENV_FILE="$(cd "$(dirname "${ENV_FILE}")" && pwd)/$(basename "${ENV_FILE}")"

  local fmode dmode
  fmode="$(stat -c '%a' "${ENV_FILE}")"
  dmode="$(stat -c '%a' "$(dirname "${ENV_FILE}")")"
  (( (8#${fmode} & 8#077) == 0 )) || fail "Env file must be owner-only (0600); found ${fmode}."
  (( (8#${dmode} & 8#077) == 0 )) || fail "Env file directory must be owner-only (0700); found ${dmode}."

  if tr -d '\r' < "${ENV_FILE}" | grep -Eq '^[[:space:]]*[A-Za-z_][A-Za-z0-9_]*=.*<SET_ME'; then
    fail "An active <SET_ME...> placeholder remains in the env file."
  fi

  local k
  for k in POSTGRES_DB POSTGRES_USER POSTGRES_PASSWORD DB_NAME DB_USER DB_PASSWORD \
           MINIO_ROOT_USER MINIO_ROOT_PASSWORD STORAGE_ACCESS_KEY STORAGE_SECRET_KEY \
           JWT_SECRET CORS_ALLOWED_ORIGINS \
           SPRING_FLYWAY_URL SPRING_FLYWAY_USER SPRING_FLYWAY_PASSWORD; do
    need "${k}"
  done
  for k in POSTGRES_PASSWORD DB_PASSWORD MINIO_ROOT_PASSWORD STORAGE_SECRET_KEY \
           JWT_SECRET SPRING_FLYWAY_PASSWORD; do
    need_secret_charset "${k}"
  done

  [[ "$(env_get JWT_SECRET)" =~ ^[0-9A-Fa-f]{64}$ ]] \
    || fail "JWT_SECRET must be exactly 64 hexadecimal characters."

  # distinct bootstrap, migrator and runtime identities and credentials.
  local pg_user db_user fw_user
  pg_user="$(env_get POSTGRES_USER)"; db_user="$(env_get DB_USER)"; fw_user="$(env_get SPRING_FLYWAY_USER)"
  [[ "${pg_user}" != "${db_user}" ]] || fail "DB_USER must differ from POSTGRES_USER (bootstrap admin is not the runtime identity)."
  [[ "${fw_user}" != "${db_user}" ]] || fail "SPRING_FLYWAY_USER must differ from DB_USER."
  [[ "${fw_user}" != "${pg_user}" ]] || fail "SPRING_FLYWAY_USER must differ from POSTGRES_USER."
  local fw_pw; fw_pw="$(env_get SPRING_FLYWAY_PASSWORD)"
  [[ "${fw_pw}" != "$(env_get DB_PASSWORD)" && "${fw_pw}" != "$(env_get POSTGRES_PASSWORD)" ]] \
    || fail "SPRING_FLYWAY_PASSWORD must differ from DB_PASSWORD and POSTGRES_PASSWORD."

  # valid identifiers (they are embedded in SQL) and one database for all identities.
  local nm
  for nm in "${db_user}" "${fw_user}"; do
    [[ "${nm}" =~ ^[a-z_][a-z0-9_]{0,62}$ ]] \
      || fail "Database role names must be lowercase letters, digits and '_' (start with a letter or '_')."
  done
  [[ "$(env_get DB_NAME)" == "$(env_get POSTGRES_DB)" ]] \
    || fail "DB_NAME must equal POSTGRES_DB (one application database)."

  #Flyway URL targets the same private database, carries no credentials.
  local fw_url; fw_url="$(env_get SPRING_FLYWAY_URL)"
  [[ "${fw_url}" == "jdbc:postgresql://postgres:5432/$(env_get DB_NAME)"* ]] \
    || fail "SPRING_FLYWAY_URL must target jdbc:postgresql://postgres:5432/<DB_NAME>."
  [[ "${fw_url}" != *@* && "${fw_url,,}" != *user=* && "${fw_url,,}" != *password=* ]] \
    || fail "SPRING_FLYWAY_URL must not embed credentials."

  # storage identity separation, CORS bound to the single approved origin.
  [[ "$(env_get STORAGE_ACCESS_KEY)" != "$(env_get MINIO_ROOT_USER)" ]] \
    || fail "STORAGE_ACCESS_KEY must differ from MINIO_ROOT_USER."
  [[ "$(env_get CORS_ALLOWED_ORIGINS)" == "${ORIGIN}" ]] \
    || fail "CORS_ALLOWED_ORIGINS must equal --origin exactly (no wildcard, no localhost, one origin)."

  local vite; vite="$(env_get VITE_API_BASE_URL)"
  [[ -z "${vite}" || "${vite}" == "/api" ]] \
    || fail "VITE_API_BASE_URL, if set, must be /api for the single-origin deployment."
}

# Host and Compose checks
validate_host() {
  [[ "$(uname -s)" == "Linux" ]] || fail "Linux host required."
  [[ "$(uname -m)" == "x86_64" ]] || fail "Approved host platform is linux/amd64 (B1); found $(uname -m)."
  command -v docker  >/dev/null 2>&1 || fail "docker not found."
  docker compose version >/dev/null 2>&1 || fail "Docker Compose plugin not found."
  docker info >/dev/null 2>&1 || fail "Docker daemon is not reachable for this user."
  command -v python3 >/dev/null 2>&1 || fail "python3 is required to inspect the rendered Compose configuration."
  command -v sha256sum >/dev/null 2>&1 || fail "sha256sum not found."
}

build_compose_cmd() {
  [[ -f "${BASE_COMPOSE}" ]] || fail "Base Compose file missing: ${BASE_COMPOSE}"
  COMPOSE=(docker compose --env-file "${ENV_FILE}" -f "${BASE_COMPOSE}")
  local f
  for f in ${OVERRIDES[@]+"${OVERRIDES[@]}"}; do
    [[ -f "${f}" ]] || fail "Compose override not found: ${f}"
    COMPOSE+=(-f "$(cd "$(dirname "${f}")" && pwd)/$(basename "${f}")")
  done
}

read -r -d '' PY_CHECK <<'PYEOF' || true
import json, sys

origin = sys.argv[1]
cfg = json.load(sys.stdin)
svc = cfg.get("services", {})
problems = []

def env_of(s):
    e = s.get("environment", {}) or {}
    if isinstance(e, list):
        e = dict(x.split("=", 1) for x in e if "=" in x)
    return e

for n in ("postgres", "minio", "minio-init", "backend", "frontend"):
    if n not in svc:
        problems.append("service '%s' is missing" % n)

# P2/B2: only the frontend ingress is published; data/backend stay private.
for n in ("postgres", "minio", "minio-init", "backend"):
    if svc.get(n, {}).get("ports"):
        problems.append("service '%s' must not publish host ports" % n)

fe = svc.get("frontend", {})
ports = fe.get("ports") or []
if [str(p.get("published")) for p in ports] != ["443"]:
    problems.append("frontend must publish exactly host port 443")
for p in ports:
    if str(p.get("target")) != "8080":
        problems.append("frontend host 443 must map to container port 8080")
    if p.get("host_ip") in ("127.0.0.1", "::1"):
        problems.append("frontend 443 must not be loopback-only")

# B3: certificate material through a read-only bind directory.
if not [v for v in (fe.get("volumes") or []) if v.get("type") == "bind" and v.get("read_only")]:
    problems.append("frontend needs a read-only bind mount for serving certificate material")

# P8: restart policy.
for n in ("postgres", "minio", "backend", "frontend"):
    if svc.get(n, {}).get("restart") != "unless-stopped":
        problems.append("service '%s' needs restart: unless-stopped" % n)
if svc.get("minio-init", {}).get("restart") not in (None, "no"):
    problems.append("minio-init must not auto-restart")

# P5/P6/P7: backend runtime bindings.
be = env_of(svc.get("backend", {}))
if be.get("CORS_ALLOWED_ORIGINS") != origin:
    problems.append("backend CORS_ALLOWED_ORIGINS does not equal the approved origin")
for k in ("SPRING_FLYWAY_URL", "SPRING_FLYWAY_USER", "SPRING_FLYWAY_PASSWORD"):
    if not be.get(k):
        problems.append("backend is not given %s" % k)
if be.get("SPRING_FLYWAY_USER") and be.get("SPRING_FLYWAY_USER") == be.get("DB_USER"):
    problems.append("backend migrator and runtime DB users are identical")
if any(k.startswith("MINIO_ROOT_") for k in be):
    problems.append("backend must not receive MinIO root credentials")

if problems:
    for p in problems:
        print("  - " + p, file=sys.stderr)
    sys.exit(1)
PYEOF

validate_compose() {
  log "Validating Compose configuration (no rendered output is printed)."
  "${COMPOSE[@]}" config --quiet || fail "docker compose config --quiet failed."
  if ! "${COMPOSE[@]}" config --format json | python3 -c "${PY_CHECK}" "${ORIGIN}"; then
    fail "Rendered Compose configuration is not deployable under the approved CT17 policy (see items above). The local base file alone is not; an approved override is required."
  fi
}

run_preflight() {
  validate_origin
  validate_env_file
  validate_host
  build_compose_cmd
  validate_compose
  log "PREFLIGHT OK: inputs, env file, host and Compose policy checks passed."
}

# Release archive and image identity (P3, P8)
verify_images() {
  local entry ref want got plat
  for entry in ${EXPECT_IMAGES[@]+"${EXPECT_IMAGES[@]}"}; do
    ref="${entry%%=*}"; want="${entry#*=}"
    [[ -n "${ref}" && "${want}" =~ ^sha256:[0-9a-f]{64}$ ]] \
      || fail "--expect-image must look like REF=sha256:<64 hex> (got: ${entry})."
    got="$(docker image inspect --format '{{.Id}}' "${ref}" 2>/dev/null)" \
      || fail "Image not present locally: ${ref}"
    [[ "${got}" == "${want}" ]] || fail "Image ${ref} content ID does not match the expected value."
    plat="$(docker image inspect --format '{{.Os}}/{{.Architecture}}' "${ref}")"
    [[ "${plat}" == "linux/amd64" ]] || fail "Image ${ref} platform is ${plat}; linux/amd64 required."
  done
  [[ ${#EXPECT_IMAGES[@]} -eq 0 ]] || log "Image identities verified (content ID, linux/amd64)."
}

run_load() {
  [[ -f "${ARCHIVE}" && -r "${ARCHIVE}" ]] || fail "Archive not found or unreadable: ${ARCHIVE}"
  [[ "${ARCHIVE_SHA256,,}" =~ ^[0-9a-f]{64}$ ]] || fail "--archive-sha256 must be 64 hex characters."
  local actual; actual="$(sha256sum "${ARCHIVE}" | awk '{print $1}')"
  [[ "${actual}" == "${ARCHIVE_SHA256,,}" ]] || fail "Archive SHA-256 mismatch; refusing to load."
  log "Archive checksum verified. Loading images (no registry pull, no rebuild)."
  docker load -i "${ARCHIVE}" >/dev/null || fail "docker load failed."
  verify_images
}

# PostgreSQL identities (P6): provision, harden, verify.
psql_admin() {  # bootstrap administrator over the container's local socket
  "${COMPOSE[@]}" exec -T postgres sh -c \
    'exec psql -X -q -tA -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" -f -'
}

psql_as() {  # $1=role $2=password; SQL on stdin. TCP to the service name, so the password is really checked.
  local role="$1" pw="$2" sql
  sql="$(cat)"
  printf '%s\n%s\n' "${pw}" "${sql}" | "${COMPOSE[@]}" exec -T postgres sh -c \
    'IFS= read -r PGPASSWORD; export PGPASSWORD; exec psql -X -q -tA -v ON_ERROR_STOP=1 -h postgres -U "'"${role}"'" -d "$POSTGRES_DB" -f -'
}

expect_denied() {  # description role password;SQL on stdin
  local desc="$1"; shift
  if psql_as "$@" >/dev/null 2>&1; then
    fail "ROLE SEPARATION FAILED: ${desc} was allowed."
  fi
  log "  ok: ${desc} is denied"
}

expect_allowed() {  # description role password; SQL on stdin
  local desc="$1"; shift
  psql_as "$@" >/dev/null 2>&1 || fail "ROLE CHECK FAILED: ${desc} was not allowed."
  log "  ok: ${desc} is allowed"
}

provision_db_roles() {
  local db app app_pw mig mig_pw sql
  db="$(env_get POSTGRES_DB)"
  app="$(env_get DB_USER)";            app_pw="$(env_get DB_PASSWORD)"
  mig="$(env_get SPRING_FLYWAY_USER)"; mig_pw="$(env_get SPRING_FLYWAY_PASSWORD)"

  read -r -d '' sql <<'SQL' || true
-- Never write statement text (it contains passwords) to the server log on error.
SET log_min_error_statement = 'panic';
DO $body$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = '@MIG@') THEN
    CREATE ROLE "@MIG@" LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS PASSWORD '@MIGPW@';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = '@APP@') THEN
    CREATE ROLE "@APP@" LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS PASSWORD '@APPPW@';
  END IF;
  -- Existing roles are not altered; unexpected privileges stop the release.
  IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname IN ('@MIG@', '@APP@')
             AND (rolsuper OR rolcreatedb OR rolcreaterole OR rolreplication OR rolbypassrls OR NOT rolcanlogin)) THEN
    RAISE EXCEPTION 'An existing application role has unexpected privileges; reconcile it before startup.';
  END IF;
END
$body$;
REVOKE "@MIG@" FROM "@APP@";
GRANT CONNECT ON DATABASE "@DB@" TO "@APP@", "@MIG@";
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
GRANT USAGE, CREATE ON SCHEMA public TO "@MIG@";
GRANT USAGE ON SCHEMA public TO "@APP@";
REVOKE CREATE ON SCHEMA public FROM "@APP@";
-- Tables/sequences that Flyway (the migrator) creates become usable by the runtime role.
ALTER DEFAULT PRIVILEGES FOR ROLE "@MIG@" IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO "@APP@";
ALTER DEFAULT PRIVILEGES FOR ROLE "@MIG@" IN SCHEMA public GRANT USAGE, SELECT ON SEQUENCES TO "@APP@";
SQL
  sql="${sql//@MIGPW@/${mig_pw}}"; sql="${sql//@APPPW@/${app_pw}}"
  sql="${sql//@MIG@/${mig}}";      sql="${sql//@APP@/${app}}";  sql="${sql//@DB@/${db}}"

  log "Provisioning PostgreSQL roles: migrator and runtime (existing roles are not overwritten)."
  if ! printf '%s\n' "${sql}" | psql_admin >/dev/null 2>&1; then
    fail "PostgreSQL role provisioning failed (output suppressed: it may contain credentials). Check role state with the bootstrap administrator."
  fi
  log "Roles ready: '${mig}' (migrations) and '${app}' (runtime)."
}

harden_after_migration() {
  local app; app="$(env_get DB_USER)"
  psql_admin >/dev/null <<<"REVOKE ALL ON TABLE public.flyway_schema_history FROM \"${app}\";" \
    || fail "Could not revoke runtime access to the Flyway history table."
}

verify_role_separation() {
  local app app_pw mig mig_pw owned by
  app="$(env_get DB_USER)";            app_pw="$(env_get DB_PASSWORD)"
  mig="$(env_get SPRING_FLYWAY_USER)"; mig_pw="$(env_get SPRING_FLYWAY_PASSWORD)"
  log "Verifying migration/runtime privilege separation (all probes are rolled back)."

  expect_allowed "runtime role connecting and reading" "${app}" "${app_pw}" <<<'SELECT 1;'
  expect_denied  "runtime role creating a table (DDL)" "${app}" "${app_pw}" <<'SQL'
BEGIN;
CREATE TABLE public.cm_ddl_probe (x integer);
ROLLBACK;
SQL
  expect_denied  "runtime role reading Flyway history" "${app}" "${app_pw}" <<<'SELECT 1 FROM public.flyway_schema_history LIMIT 1;'
  expect_allowed "migrator role creating a table (DDL, rolled back)" "${mig}" "${mig_pw}" <<'SQL'
BEGIN;
CREATE TABLE public.cm_ddl_probe (x integer);
ROLLBACK;
SQL

  owned="$(psql_admin <<<"SELECT count(*) FROM pg_tables WHERE schemaname='public' AND tableowner='${app}';")"
  [[ "${owned}" == "0" ]] || fail "ROLE SEPARATION FAILED: the runtime role owns ${owned} table(s) in public."
  log "  ok: runtime role owns no tables"

  by="$(psql_admin <<<"SELECT coalesce(string_agg(DISTINCT installed_by, ','), 'none') FROM public.flyway_schema_history;")"
  if [[ "${by}" == "${mig}" ]]; then
    log "  ok: Flyway history installed_by = migrator only"
  else
    log "  WARNING: Flyway history installed_by = ${by} (expected '${mig}'; mixed identities are expected only when updating an older setup)."
  fi
  DB_SEPARATION_RESULT="verified (Flyway installed_by: ${by})"
}
# Provisioning: PostgreSQL + MinIO availability (not feature behavior)
run_provision() {
  verify_images
  log "Starting PostgreSQL and MinIO (private network; images must already be loaded)."
  "${COMPOSE[@]}" up -d --no-build --pull never --wait --wait-timeout "${WAIT_TIMEOUT}" postgres minio \
    || fail "PostgreSQL/MinIO did not become ready within ${WAIT_TIMEOUT}s."

  provision_db_roles

  log "Running one-shot MinIO initializer (idempotent; stops on conflicting state)."
  "${COMPOSE[@]}" up -d --no-build --pull never --no-deps minio-init >/dev/null \
    || fail "Could not start minio-init."
  local cid code
  cid="$("${COMPOSE[@]}" ps -a -q minio-init)"
  [[ -n "${cid}" ]] || fail "minio-init container not found."
  code="$(timeout "${WAIT_TIMEOUT}" docker wait "${cid}")" \
    || fail "minio-init did not finish within ${WAIT_TIMEOUT}s."
  if [[ "${code}" != "0" ]]; then
    "${COMPOSE[@]}" logs --no-color --tail 20 minio-init >&2 || true
    fail "minio-init exited with code ${code}; backend startup must not proceed."
  fi
  log "PROVISION OK: postgres ready, minio running, minio-init exit 0."
  log "Note: this is service availability only; the application storage feature is NOT verified."
}

# Deploy: backend + frontend, private health, deployment record
guard_update() {
  local existing; existing="$("${COMPOSE[@]}" ps -a -q backend 2>/dev/null || true)"
  if [[ -n "${existing}" ]]; then
    [[ "${RECOVERY_VERIFIED}" == "yes" ]] \
      || fail "An existing backend was found (update). Stop: no verified recovery artifact attested. Re-run with --recovery-artifact-verified only after an independently checked backup exists (P9)."
    log "Update detected; operator attested a verified recovery artifact exists."
  else
    log "First deployment (no existing backend container)."
  fi
}

wait_backend_health() {
  local deadline out code
  deadline=$(( SECONDS + WAIT_TIMEOUT ))
  log "Waiting for the backend to answer privately (up to ${WAIT_TIMEOUT}s)."
  while :; do
    out="$(timeout 20 "${COMPOSE[@]}" exec -T frontend wget -S -O - http://backend:8080/actuator/health 2>&1 </dev/null || true)"
    code="$(printf '%s\n' "${out}" | grep -Eo 'HTTP/[0-9.]+ [0-9]{3}' | tail -1 | awk '{print $2}')"
    case "${code}" in
      (200)
        if [[ "${out}" == *'"status":"UP"'* ]]; then
          HEALTH_RESULT="UP (HTTP 200, non-detailed)"; break
        fi ;;
      (401|403)
        HEALTH_RESULT="backend answering; /actuator/health needs authentication at this revision (HTTP ${code}), so UP is not observable"
        break ;;
    esac
    if (( SECONDS >= deadline )); then
      "${COMPOSE[@]}" logs --no-color --tail 30 backend >&2 || true
      fail "Backend did not answer within ${WAIT_TIMEOUT}s. Migration/config failure stops the release; nothing is retried or reset."
    fi
    sleep "${POLL_INTERVAL_SECONDS}"
  done
  log "SERVICE_HEALTH: ${HEALTH_RESULT}"
}

run_deploy() {
  verify_images
  guard_update
  log "Starting backend and frontend from the loaded images (no build, no pull)."
  "${COMPOSE[@]}" up -d --no-build --pull never backend frontend \
    || fail "docker compose up failed."
  wait_backend_health
  harden_after_migration
  verify_role_separation
  print_record
}

print_record() {
  local dirty="clean" rev="unavailable" flyway
  if git -C "${REPO_ROOT}" rev-parse HEAD >/dev/null 2>&1; then
    rev="$(git -C "${REPO_ROOT}" rev-parse HEAD)"
    [[ -z "$(git -C "${REPO_ROOT}" status --porcelain 2>/dev/null)" ]] || dirty="DIRTY"
  fi
  flyway="$("${COMPOSE[@]}" exec -T postgres sh -c \
    'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -tA -c "select count(*) filter (where success), count(*) filter (where not success) from flyway_schema_history"' \
    2>/dev/null || true)"
  [[ -n "${flyway}" ]] || flyway="unavailable (informational only)"

  echo
  echo "================ DEPLOYMENT RECORD (no secrets) ================"
  echo "Time UTC            : $(date -u '+%Y-%m-%d %H:%M:%S')"
  echo "Time Vietnam (ICT)  : $(TZ=Asia/Ho_Chi_Minh date '+%Y-%m-%d %H:%M:%S')"
  echo "Host                : $(hostname)"
  echo "Approved origin     : ${ORIGIN}"
  echo "Script checkout rev : ${rev} (${dirty})"
  echo "Image source rev    : ${SOURCE_REVISION} (declared by operator)"
  [[ -z "${ARCHIVE_SHA256}" ]] || echo "Archive SHA-256     : ${ARCHIVE_SHA256,,}"
  echo "Flyway success|fail : ${flyway}"
  echo "DB role separation  : ${DB_SEPARATION_RESULT}"
  echo "--- images in use ---"
  "${COMPOSE[@]}" images 2>/dev/null || echo "(unavailable)"
  echo "--- containers ---"
  "${COMPOSE[@]}" ps -a 2>/dev/null || echo "(unavailable)"
  echo "--- results (kept separate) ---"
  echo "SCRIPT_RESULT       : steps completed"
  echo "SERVICE_HEALTH      : ${HEALTH_RESULT} (health alone proves no feature)"
  echo "FEATURE_BEHAVIOR    : NOT_VERIFIED (Auth, catalogue, TLS from outside: QA01/QA07/DP03)"
  echo "External access     : NOT_VERIFIED (requires independent outside-network check, DP03)"
  echo "================================================================"
}

# ----------------------------------------------------------------------------
main() {
  parse_args "$@"
  require_inputs
  case "${SUBCOMMAND}" in
    (preflight) run_preflight ;;
    (load)      run_preflight; run_load ;;
    (provision) run_preflight; run_provision ;;
    (deploy)    run_preflight; run_deploy ;;
    (all)       run_preflight; run_load; run_provision; run_deploy ;;
  esac
}

main "$@"