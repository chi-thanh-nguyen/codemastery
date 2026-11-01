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
#   - updates require checked recovery, schema/image compatibility, preserved
#     state and a reference to operator authority before any changes (P9);
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
INITIALIZER_QUALIFICATION=""
PROVISION_AUTHORIZATION=""
DEPLOY_AUTHORIZATION=""
EXPOSURE_AUTHORIZATION=""
PACKET_FILTER_VERIFICATION=""
WAIT_TIMEOUT=""
RECOVERY_VERIFIED="no"
COMPATIBILITY_VERIFIED="no"
PERSISTENT_STATE_PRESERVED="no"
UPDATE_AUTHORIZATION=""
OVERRIDES=()
EXPECT_IMAGES=()
COMPOSE=()
DB_SEPARATION_RESULT="not checked"
DB_GRANTS_RESULT="not checked"
HEALTH_RESULT="not checked"
PUBLISHED_PORTS_RESULT="not checked"
ENV_FILE_IDENTITY=""
COMPOSE_MODEL=""
RELEASE_IMAGES=""
CONTAIN_ON_FAILURE="no"
RELEASE_COMPLETE="no"
STAGED_FRONTEND_CID=""
HOST_PLATFORM=""
IMAGE_STORE=""
DOCKER_SERVER_VERSION=""
DOCKER_COMPOSE_VERSION=""

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
                                Qualified images supply the minio-init executable
                                and frontend healthcheck. Clear the local Compose
                                overrides for those fields; certificate binds must
                                contain only PEM material, with private keys 0400.

load / deploy / all:
  --archive PATH                `docker save` archive of the reviewed release
  --archive-sha256 HEX          Expected SHA-256 of that archive

Every state-changing command (including provision):
  --expect-image REF=sha256:ID  Verified artifact for EVERY effective service image
                                (repeatable; no extra or duplicate references).
                                ID is the Docker image ID on this host's image
                                store: the OCI index/manifest digest with the
                                containerd image store, otherwise the image
                                config digest (`docker image inspect` .Id).

provision / deploy / all:
  --wait-timeout SECONDS        Deadline for readiness waits (no default)

deploy / all:
  --source-revision REV         Source revision the images were built from

provision / deploy / all:
  --initializer-qualification REF
                                Non-secret reference to repeat-run and
                                conflict-stop qualification for the exact init image
  provision requires backend and frontend to already be stopped. Use `all` for
  a coordinated update that quiesces and then restarts the complete application.

provision / deploy / all:
  --provision-authorization REF
                                Non-secret reference to the separate approved
                                provisioning authority. deploy also creates
                                missing roles and changes database grants.

load / deploy / all:
  --deployment-authorization REF
                                Non-secret reference to the separate approved
                                deployment authority (load installs release
                                artifacts into this host's image store)

deploy / all:
  --public-exposure-authorization REF
                                Non-secret reference to Leader authorization for
                                this actual public HTTPS origin
  --packet-filter-verification REF
                                Non-secret reference to operator evidence that
                                this host's packet filtering, including the path
                                Docker uses for published ports (a UFW rule set
                                alone is not proof), admits only the approved
                                HTTPS ingress and operator access (CT17 B2).
                                This script cannot verify host firewall rules.

Updates:
  --recovery-artifact-verified  Operator attests that an independently checked,
                                restorable pre-update artifact is in approved custody.
  --schema-image-compatibility-verified
                                Candidate images are verified compatible with the
                                current schema/data and reviewed migration plan.
  --persistent-state-preserved Operator verified the intended existing PostgreSQL
                                and MinIO state/volume bindings are preserved.
  --update-authorization REF   Reference to the existing operator/update authority
                                (a record reference, never a credential).
                                All four are required before changes when project
                                containers or persistent volumes already exist.
                                Inventory errors stop work.
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
      (--initializer-qualification) INITIALIZER_QUALIFICATION="${2:?--initializer-qualification needs a reference}"; shift 2 ;;
      (--provision-authorization) PROVISION_AUTHORIZATION="${2:?--provision-authorization needs a reference}"; shift 2 ;;
      (--deployment-authorization) DEPLOY_AUTHORIZATION="${2:?--deployment-authorization needs a reference}"; shift 2 ;;
      (--public-exposure-authorization) EXPOSURE_AUTHORIZATION="${2:?--public-exposure-authorization needs a reference}"; shift 2 ;;
      (--packet-filter-verification) PACKET_FILTER_VERIFICATION="${2:?--packet-filter-verification needs a reference}"; shift 2 ;;
      (--recovery-artifact-verified) RECOVERY_VERIFIED="yes"; shift ;;
      (--schema-image-compatibility-verified) COMPATIBILITY_VERIFIED="yes"; shift ;;
      (--persistent-state-preserved) PERSISTENT_STATE_PRESERVED="yes"; shift ;;
      (--update-authorization) UPDATE_AUTHORIZATION="${2:?--update-authorization needs a reference}"; shift 2 ;;
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
  if [[ "${SUBCOMMAND}" != "preflight" ]]; then
    [[ ${#EXPECT_IMAGES[@]} -ge 1 ]] || fail "Every state-changing command requires --expect-image for all effective service images."
  fi
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
  case "${SUBCOMMAND}" in
    (provision|deploy|all)
      [[ -n "${INITIALIZER_QUALIFICATION}" ]] || fail "--initializer-qualification is required for ${SUBCOMMAND}."
      ;;
  esac
  # deploy re-provisions database roles and grants (provision_db_roles,
  # harden_after_migration), so it needs the same provisioning authority.
  case "${SUBCOMMAND}" in
    (provision|deploy|all)
      [[ -n "${PROVISION_AUTHORIZATION}" ]] || fail "--provision-authorization is required for ${SUBCOMMAND}."
      ;;
  esac
  # load installs release artifacts into the host image store: the first deployment step.
  case "${SUBCOMMAND}" in
    (load|deploy|all)
      [[ -n "${DEPLOY_AUTHORIZATION}" ]] || fail "--deployment-authorization is required for ${SUBCOMMAND}."
      ;;
  esac
  case "${SUBCOMMAND}" in
    (deploy|all)
      [[ -n "${EXPOSURE_AUTHORIZATION}" ]] || fail "--public-exposure-authorization is required for ${SUBCOMMAND}."
      [[ -n "${PACKET_FILTER_VERIFICATION}" ]] || fail "--packet-filter-verification is required for ${SUBCOMMAND}."
      ;;
  esac
  [[ "${SOURCE_REVISION}" != *$'\n'* && "${SOURCE_REVISION}" != *$'\r'* ]] \
    || fail "--source-revision must be a single-line identifier."
  [[ "${UPDATE_AUTHORIZATION}" != *$'\n'* && "${UPDATE_AUTHORIZATION}" != *$'\r'* ]] \
    || fail "--update-authorization must be a single-line record reference."
  [[ "${INITIALIZER_QUALIFICATION}" != *$'\n'* && "${INITIALIZER_QUALIFICATION}" != *$'\r'* ]] \
    || fail "--initializer-qualification must be a single-line record reference."
  [[ "${PROVISION_AUTHORIZATION}" != *$'\n'* && "${PROVISION_AUTHORIZATION}" != *$'\r'* ]] \
    || fail "--provision-authorization must be a single-line record reference."
  [[ "${DEPLOY_AUTHORIZATION}" != *$'\n'* && "${DEPLOY_AUTHORIZATION}" != *$'\r'* ]] \
    || fail "--deployment-authorization must be a single-line record reference."
  [[ "${EXPOSURE_AUTHORIZATION}" != *$'\n'* && "${EXPOSURE_AUTHORIZATION}" != *$'\r'* ]] \
    || fail "--public-exposure-authorization must be a single-line record reference."
  [[ "${PACKET_FILTER_VERIFICATION}" != *$'\n'* && "${PACKET_FILTER_VERIFICATION}" != *$'\r'* ]] \
    || fail "--packet-filter-verification must be a single-line record reference."
  for ref in "${UPDATE_AUTHORIZATION}" "${INITIALIZER_QUALIFICATION}" \
             "${PROVISION_AUTHORIZATION}" "${DEPLOY_AUTHORIZATION}" "${EXPOSURE_AUTHORIZATION}" \
             "${PACKET_FILTER_VERIFICATION}"; do
    [[ -z "${ref}" || "${ref}" =~ [^[:space:]] ]] \
      || fail "Authorization and qualification references must not be whitespace-only."
  done
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
  # CT17 B3 custody: the file Compose reads is a regular 0600 file with a single
  # link, owned by the invoking user, in a 0700 directory that user owns. Resolve
  # the directory physically and refuse a symlinked file, so the checked path is
  # the read path. Every ancestor must belong to root or the invoking user and be
  # unwritable by others (sticky directories excepted), or the 0700 directory
  # could be renamed and replaced between checks.
  [[ ! -L "${ENV_FILE}" ]] || fail "Env file must not be a symbolic link: ${ENV_FILE}"
  [[ -f "${ENV_FILE}" && -r "${ENV_FILE}" ]] || fail "Env file not found or unreadable: ${ENV_FILE}"
  local dir uid ancestor owner mode identity
  dir="$(cd -P -- "$(dirname -- "${ENV_FILE}")" && pwd -P)" || fail "Cannot resolve the env file directory."
  ENV_FILE="${dir}/$(basename -- "${ENV_FILE}")"
  [[ ! -L "${ENV_FILE}" && -f "${ENV_FILE}" ]] || fail "Env file must be a regular file, not a symbolic link."
  uid="$(id -u)"
  [[ "$(stat -c '%u:%a:%h' -- "${ENV_FILE}")" == "${uid}:600:1" ]] \
    || fail "Env file must be mode 0600, owned by the invoking user and have no other hard link (CT17 B3)."
  [[ "$(stat -c '%u:%a' -- "${dir}")" == "${uid}:700" ]] \
    || fail "Env file directory must be mode 0700 and owned by the invoking user (CT17 B3)."
  ancestor="${dir}"
  while [[ "${ancestor}" != "/" ]]; do
    ancestor="$(dirname -- "${ancestor}")"
    IFS=: read -r owner mode <<<"$(stat -c '%u:%a' -- "${ancestor}")"
    [[ "${owner}" == "0" || "${owner}" == "${uid}" ]] \
      && (( (8#${mode} & 8#022) == 0 || (8#${mode} & 8#1000) != 0 )) \
      || fail "Env file ancestor ${ancestor} is owned or writable by another user; the secret directory is not under operator control."
  done
  # Later re-validation must see the same file and bytes, not a replacement.
  identity="$(stat -c '%d:%i' -- "${ENV_FILE}"):$(sha256sum < "${ENV_FILE}" | awk '{print $1}')"
  [[ -z "${ENV_FILE_IDENTITY}" || "${ENV_FILE_IDENTITY}" == "${identity}" ]] \
    || fail "Env file was replaced or changed since preflight; release stopped."
  ENV_FILE_IDENTITY="${identity}"

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
  for nm in "${pg_user}" "${db_user}" "${fw_user}" "$(env_get DB_NAME)"; do
    [[ "${nm}" =~ ^[a-z_][a-z0-9_]{0,62}$ ]] \
      || fail "Database role names must be lowercase letters, digits and '_' (start with a letter or '_')."
  done
  [[ "$(env_get DB_NAME)" == "$(env_get POSTGRES_DB)" ]] \
    || fail "DB_NAME must equal POSTGRES_DB (one application database)."

  #Flyway URL targets the same private database, carries no credentials.
  local fw_url; fw_url="$(env_get SPRING_FLYWAY_URL)"
  [[ "${fw_url}" == "jdbc:postgresql://postgres:5432/$(env_get DB_NAME)" ||
     "${fw_url}" == "jdbc:postgresql://postgres:5432/$(env_get DB_NAME)?currentSchema=public" ]] \
    || fail "SPRING_FLYWAY_URL must target jdbc:postgresql://postgres:5432/<DB_NAME>."
  [[ "$(env_get DB_PASSWORD)" != "$(env_get POSTGRES_PASSWORD)" ]] \
    || fail "DB_PASSWORD must differ from POSTGRES_PASSWORD."

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
  local distro context endpoint daemon_os
  distro="$(awk -F= '$1 == "ID" { gsub(/\"/, "", $2); id=$2 } $1 == "VERSION_ID" { gsub(/\"/, "", $2); version=$2 } END { print id ":" version }' /etc/os-release 2>/dev/null)"
  [[ "${distro}" == "ubuntu:24.04" ]] \
    || fail "CT17 B1 requires the approved Ubuntu Server 24.04 host; found ${distro:-unknown}."
  HOST_PLATFORM="${distro} $(uname -m)"
  command -v docker  >/dev/null 2>&1 || fail "docker not found."
  docker compose version >/dev/null 2>&1 || fail "Docker Compose plugin not found."
  [[ -z "${DOCKER_HOST:-}" ]] \
    || fail "DOCKER_HOST is set; refusing to target an unverified Docker daemon."
  context="$(docker context show 2>/dev/null)" || fail "Cannot identify the active Docker context."
  endpoint="$(docker context inspect "${context}" --format '{{(index .Endpoints "docker").Host}}' 2>/dev/null)" \
    || fail "Cannot inspect the active Docker endpoint."
  [[ "${endpoint}" == unix://* ]] \
    || fail "Docker endpoint must be a local Unix socket; remote Docker contexts are not approved."
  docker info >/dev/null 2>&1 || fail "Docker daemon is not reachable for this user."
  daemon_os="$(docker info --format '{{.OSType}}' 2>/dev/null)" || fail "Cannot inspect Docker daemon platform."
  [[ "${daemon_os}" == "linux" ]] || fail "Docker daemon must be Linux; found ${daemon_os}."
  detect_image_store
  DOCKER_SERVER_VERSION="$(docker version --format '{{.Server.Version}}' 2>/dev/null)" \
    || fail "Cannot record Docker Engine version."
  DOCKER_COMPOSE_VERSION="$(docker compose version --short 2>/dev/null)" \
    || fail "Cannot record Docker Compose version."
  [[ -n "${DOCKER_SERVER_VERSION}" && -n "${DOCKER_COMPOSE_VERSION}" ]] \
    || fail "Docker Engine and Compose versions must be identifiable."
  command -v python3 >/dev/null 2>&1 || fail "python3 is required to inspect the rendered Compose configuration."
  command -v sha256sum >/dev/null 2>&1 || fail "sha256sum not found."
  command -v timeout >/dev/null 2>&1 || fail "timeout not found."
  command -v openssl >/dev/null 2>&1 || fail "openssl is required to validate certificate-only bind directories."
}

# Docker image IDs are store-specific. The containerd image store (the default
# for fresh Docker Engine 29+ installs) reports the OCI index/manifest digest;
# a classic graph driver reports the image config digest. Archive and image
# checks must compare the same kind of digest.
detect_image_store() {
  local driver_type
  driver_type="$(docker info --format '{{range .DriverStatus}}{{if eq (index . 0) "driver-type"}}{{index . 1}}{{end}}{{end}}' 2>/dev/null)" \
    || fail "Cannot identify the Docker image store."
  case "${driver_type}" in
    (io.containerd.snapshotter.v1) IMAGE_STORE="containerd" ;;
    ("") IMAGE_STORE="graphdriver" ;;
    (*) fail "Unrecognized Docker image store; image identities cannot be verified." ;;
  esac
}

build_compose_cmd() {
  [[ -f "${BASE_COMPOSE}" ]] || fail "Base Compose file missing: ${BASE_COMPOSE}"
  # The base file names this single stack "codemastery". Do not let a shell
  # COMPOSE_PROJECT_NAME switch recovery inventory to an unrelated empty project.
  COMPOSE=(docker compose --project-name codemastery --env-file "${ENV_FILE}" -f "${BASE_COMPOSE}")
  local f
  for f in ${OVERRIDES[@]+"${OVERRIDES[@]}"}; do
    [[ -f "${f}" ]] || fail "Compose override not found: ${f}"
    COMPOSE+=(-f "$(cd "$(dirname "${f}")" && pwd)/$(basename "${f}")")
  done
}

read -r -d '' PY_ENV_POLICY <<'PYEOF' || true
def forbidden_backend_key(key):
    normalized = key.upper().replace('.', '_').replace('-', '_')
    native = {'SPRING_FLYWAY_URL', 'SPRING_FLYWAY_USER', 'SPRING_FLYWAY_PASSWORD',
              'SPRING_PROFILES_ACTIVE', 'SERVER_PORT', 'SERVER_FORWARD_HEADERS_STRATEGY'}
    return ((normalized.startswith(('SPRING_', 'SERVER_', 'CODEMASTERY_', 'HIBERNATE_'))
             and key not in native)
            or normalized in {'JAVA_TOOL_OPTIONS', 'JDK_JAVA_OPTIONS', 'JAVA_OPTS', '_JAVA_OPTIONS'})
PYEOF

read -r -d '' PY_CHECK <<'PYEOF' || true
import hashlib, ipaddress, json, os, pathlib, re, stat, subprocess, sys

origin, env_path = sys.argv[1:]
cfg = json.load(sys.stdin)
svc = cfg.get("services", {})
problems = []
expected = {}
with open(env_path) as f:
    for line in f:
        line = line.rstrip("\r\n")
        if line and not line.lstrip().startswith("#") and "=" in line:
            k, v = line.split("=", 1)
            expected[k] = v

def env_of(s):
    e = s.get("environment", {}) or {}
    if isinstance(e, list):
        e = dict(x.split("=", 1) for x in e if "=" in x)
    return e

allowed_environment = {
    "postgres": {"POSTGRES_DB", "POSTGRES_USER", "POSTGRES_PASSWORD"},
    "minio": {"MINIO_ROOT_USER", "MINIO_ROOT_PASSWORD", "MINIO_BROWSER"},
    "minio-init": {"MINIO_ROOT_USER", "MINIO_ROOT_PASSWORD", "STORAGE_ACCESS_KEY",
                   "STORAGE_SECRET_KEY", "STORAGE_BUCKET", "STORAGE_REGION"},
    "backend": {"DB_HOST", "DB_PORT", "DB_NAME", "DB_USER", "DB_PASSWORD",
                "SPRING_FLYWAY_URL", "SPRING_FLYWAY_USER", "SPRING_FLYWAY_PASSWORD",
                "STORAGE_ENDPOINT", "STORAGE_PATH_STYLE_ACCESS", "STORAGE_BUCKET",
                "STORAGE_REGION", "STORAGE_ACCESS_KEY", "STORAGE_SECRET_KEY",
                "JWT_SECRET", "JWT_EXPIRATION", "CORS_ALLOWED_ORIGINS",
                "SERVER_PORT", "SERVER_FORWARD_HEADERS_STRATEGY", "SPRING_PROFILES_ACTIVE"},
    "frontend": set(),
}

names = {"postgres", "minio", "minio-init", "backend", "frontend"}
if set(svc) != names:
    problems.append("effective configuration must contain exactly the five approved services")

# The local Compose foundation uses only these service options. Keep the release
# model closed to hooks, deploy extensions, log sinks, tmpfs mounts, secrets,
# configs, and future options that could change execution or security policy.
allowed_service_options = {
    "postgres": {"command", "depends_on", "entrypoint", "environment", "healthcheck", "image", "networks", "ports", "restart", "volumes"},
    "minio": {"command", "entrypoint", "environment", "image", "networks", "ports", "restart", "volumes"},
    "minio-init": {"command", "depends_on", "entrypoint", "environment", "image", "networks", "restart"},
    "backend": {"build", "command", "depends_on", "entrypoint", "environment", "image", "networks", "ports", "restart"},
    "frontend": {"build", "command", "entrypoint", "environment", "healthcheck", "image", "networks", "ports", "restart", "volumes"},
}
for n, s in svc.items():
    extra = set(s) - allowed_service_options.get(n, set())
    if extra:
        problems.append("service '%s' contains unsupported Compose options: %s" % (n, ", ".join(sorted(extra))))
    executable_or_unapproved = {"pre_start", "post_start", "pre_stop", "post_stop",
                                "deploy", "logging", "tmpfs", "configs", "secrets"}
    if executable_or_unapproved.intersection(s):
        problems.append("service '%s' contains an unapproved lifecycle, deploy, logging, tmpfs, config or secret option" % n)

if set(cfg) - {"name", "services", "networks", "volumes"}:
    problems.append("effective configuration contains unsupported top-level Compose options")

# Keep every service on this Compose project's private bridge network. External,
# custom-driver, and additional networks can connect private state to other stacks.
networks = cfg.get("networks", {})
if set(networks) != {"default"}:
    problems.append("services must use only the Compose project's default network")
else:
    default_network = networks["default"]
    if default_network.get("external") or default_network.get("driver") not in (None, "bridge"):
        problems.append("the Compose default network must be a project-managed bridge")
    # Compose renders an empty ipam mapping for its implicit default network.
    # Any IPAM driver, subnet, range, gateway or option remains unapproved.
    if (set(default_network) - {"name", "driver", "internal", "ipam"}
            or default_network.get("ipam") not in (None, {})):
        problems.append("the Compose default network contains unapproved network options")
    if default_network.get("name") not in (None, "codemastery_default"):
        problems.append("the Compose default network must remain private to the codemastery project")
for n, s in svc.items():
    if set(env_of(s)) - allowed_environment.get(n, set()):
        problems.append("service '%s' contains unapproved environment variables" % n)
    if not s.get("image") or any(c.isspace() for c in s["image"]):
        problems.append("service '%s' needs an explicit immutable-release image reference" % n)
    if s.get("platform", "linux/amd64") != "linux/amd64":
        problems.append("service '%s' has an incompatible platform" % n)
    if s.get("network_mode") or s.get("profiles") or s.get("scale", 1) != 1:
        problems.append("service '%s' must use the private Compose network without profiles or scaling" % n)
    if s.get("pull_policy") == "build":
        problems.append("service '%s' must not force an image build" % n)
    # A release cannot smuggle another service into an up/run dependency traversal.
    if set(s.get("depends_on", {})) - names:
        problems.append("service '%s' has an unapproved dependency" % n)
    elevated_options = ('cap_add', 'cap_drop', 'devices', 'device_cgroup_rules',
                        'security_opt', 'userns_mode', 'cgroup', 'cgroup_parent',
                        'uts', 'volumes_from', 'group_add', 'runtime',
                        'use_api_socket', 'env_file', 'user', 'sysctls')
    if (s.get("privileged") or s.get("pid") or s.get("ipc")
            or s.get("uts") or s.get("cgroup")
            or any(s.get(option) for option in elevated_options)):
        problems.append("service '%s' requests an unapproved privilege, host namespace, identity or external environment" % n)
    attached = s.get("networks")
    if attached and set(attached) != {"default"}:
        problems.append("service '%s' must attach only to the private Compose network" % n)
    if isinstance(attached, dict) and attached.get("default"):
        default_attachment = attached["default"]
        if set(default_attachment):
            problems.append("service '%s' has unapproved network attachment options" % n)

minio = svc.get("minio", {})
if minio.get("entrypoint") is not None or minio.get("command") != ["server", "/data", "--console-address", ":9001"]:
    problems.append("minio must use the approved server command and image entrypoint")
if set(env_of(minio)) - {"MINIO_ROOT_USER", "MINIO_ROOT_PASSWORD", "MINIO_BROWSER"}:
    problems.append("minio contains unapproved environment bindings")
# The release disables the embedded Console; only the exact value is accepted.
if env_of(minio).get("MINIO_BROWSER") != "off":
    problems.append("minio must disable the embedded Console with MINIO_BROWSER=off")

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
    if p.get("protocol", "tcp") != "tcp":
        problems.append("frontend HTTPS ingress must use TCP")
    # B2 selects public 443 without naming an interface: an omitted host_ip is
    # allowed. A declared one must be a real, non-loopback address. A valid
    # mapping is not evidence about the host's packet filtering.
    if p.get("host_ip"):
        try:
            if ipaddress.ip_address(p["host_ip"]).is_loopback:
                problems.append("frontend 443 must not be loopback-only")
        except ValueError:
            problems.append("frontend 443 host_ip must be an IP address")

# B3: certificate material through a read-only bind directory.
fe_volumes = fe.get("volumes") or []
if not fe_volumes or any(v.get("type") != "bind" or not v.get("read_only") for v in fe_volumes):
    problems.append("frontend needs a read-only bind mount for serving certificate material")
certificate_public_keys = []
private_key_public_keys = []
pem_pattern = re.compile(
    rb'-----BEGIN (CERTIFICATE|PRIVATE KEY|RSA PRIVATE KEY|EC PRIVATE KEY|DSA PRIVATE KEY)-----\r?\n.*?-----END \1-----',
    re.S)

def openssl_public_key_digest(command, pem):
    result = subprocess.run(command, input=pem + b'\n', stdout=subprocess.PIPE,
                            stderr=subprocess.DEVNULL)
    if result.returncode:
        raise ValueError()
    return hashlib.sha256(result.stdout).digest()

def walk_error(error):
    raise error

for volume in fe_volumes:
    # B3 permits serving certificate directories, not config/code overlays. Check
    # content rather than trusting a filename or an operator-supplied mount label.
    try:
        target = pathlib.PurePosixPath(volume.get('target', ''))
        # These are code/config locations used by the existing frontend image,
        # not newly selected certificate locations. Never serve key material or
        # shadow executable configuration through a certificate mount.
        protected = map(pathlib.PurePosixPath, ('/usr/share/nginx/html', '/etc/nginx/nginx.conf',
                         '/etc/nginx/conf.d', '/docker-entrypoint.sh', '/docker-entrypoint.d'))
        if not target.is_absolute() or '..' in target.parts or any(
                target == p or target in p.parents or p in target.parents for p in protected):
            raise ValueError()
        source = pathlib.Path(volume.get('source', ''))
        if source.is_symlink() or not source.is_dir():
            raise ValueError()
        root_owner = source.lstat().st_uid
        for root, dirs, files in os.walk(source, followlinks=False, onerror=walk_error):
            root_path = pathlib.Path(root)
            root_stat = root_path.lstat()
            root_mode = stat.S_IMODE(root_stat.st_mode)
            if (root_stat.st_uid != root_owner or root_mode & 0o077
                    or root_mode & 0o500 != 0o500):
                raise ValueError()
            for directory in dirs:
                child = root_path / directory
                child_stat = child.lstat()
                child_mode = stat.S_IMODE(child_stat.st_mode)
                if (child.is_symlink() or not child.is_dir()
                        or child_stat.st_uid != root_owner or child_mode & 0o077
                        or child_mode & 0o500 != 0o500):
                    raise ValueError()
            for name in files:
                path = root_path / name
                file_stat = path.lstat()
                if path.is_symlink() or not stat.S_ISREG(file_stat.st_mode):
                    raise ValueError()
                data = path.read_bytes()
                blocks = list(pem_pattern.finditer(data))
                if not blocks or pem_pattern.sub(b'', data).strip():
                    raise ValueError()
                for block in blocks:
                    pem = block.group(0)
                    if block.group(1) == b'CERTIFICATE':
                        public_pem = subprocess.run(
                            ['openssl', 'x509', '-pubkey', '-noout'], input=pem + b'\n',
                            stdout=subprocess.PIPE, stderr=subprocess.DEVNULL)
                        if public_pem.returncode:
                            raise ValueError()
                        certificate_public_keys.append(openssl_public_key_digest(
                            ['openssl', 'pkey', '-pubin', '-outform', 'DER'], public_pem.stdout))
                    else:
                        if (stat.S_IMODE(file_stat.st_mode) != 0o400
                                or file_stat.st_uid != root_owner or file_stat.st_nlink != 1):
                            raise ValueError()
                        private_key_public_keys.append(openssl_public_key_digest(
                            ['openssl', 'pkey', '-pubout', '-outform', 'DER', '-passin', 'pass:'], pem))
    except Exception:
        problems.append('frontend certificate directory must be owner-restricted, symlink-free, and contain only valid serving certificates plus one mode-0400 private key')
if (not certificate_public_keys or len(private_key_public_keys) != 1
        or sum(cert == private_key_public_keys[0] for cert in certificate_public_keys) != 1):
    problems.append('frontend certificate chain must contain exactly one certificate matching its single private key')
if fe.get('command') is not None or fe.get('entrypoint') is not None or any(fe.get(k) for k in ('configs', 'secrets')):
    problems.append("frontend must use the verified image configuration with read-only certificate binds only")
hc = fe.get("healthcheck", {})
if hc:
    problems.append("frontend healthcheck must come from the verified image; remove the local Compose healthcheck override")

# P8: restart policy.
for n in ("postgres", "minio", "backend", "frontend"):
    if svc.get(n, {}).get("restart") != "unless-stopped":
        problems.append("service '%s' needs restart: unless-stopped" % n)
if svc.get("minio-init", {}).get("restart") not in (None, "no"):
    problems.append("minio-init must not auto-restart")

# P5/P6/P7: backend runtime bindings.
be = env_of(svc.get("backend", {}))

def bindings(service, keys):
    actual = env_of(svc.get(service, {}))
    for k in keys:
        if not expected.get(k) or actual.get(k) != expected[k]:
            # Report names only; neither effective nor expected values are logged.
            problems.append("%s effective %s differs from the authoritative env file" % (service, k))

bindings("postgres", ("POSTGRES_DB", "POSTGRES_USER", "POSTGRES_PASSWORD"))
bindings("backend", ("DB_NAME", "DB_USER", "DB_PASSWORD", "SPRING_FLYWAY_URL",
                     "SPRING_FLYWAY_USER", "SPRING_FLYWAY_PASSWORD", "JWT_SECRET",
                     "CORS_ALLOWED_ORIGINS", "STORAGE_ACCESS_KEY", "STORAGE_SECRET_KEY"))
bindings("minio", ("MINIO_ROOT_USER", "MINIO_ROOT_PASSWORD"))
bindings("minio-init", ("MINIO_ROOT_USER", "MINIO_ROOT_PASSWORD", "STORAGE_ACCESS_KEY", "STORAGE_SECRET_KEY"))
for k, v in {"DB_HOST": "postgres", "DB_PORT": "5432", "STORAGE_ENDPOINT": "http://minio:9000",
             "STORAGE_PATH_STYLE_ACCESS": "true", "SERVER_PORT": "8080",
             "SERVER_FORWARD_HEADERS_STRATEGY": "NONE", "SPRING_PROFILES_ACTIVE": "prod"}.items():
    if str(be.get(k, "")).lower() != v.lower():
        problems.append("backend %s does not match the approved deployed binding" % k)
if be.get("JWT_EXPIRATION", "PT15M") != "PT15M":
    problems.append("backend JWT_EXPIRATION must remain PT15M")
# Spring relaxed binding, JVM options, config imports and command-line properties
# can bypass DB_* / the native Flyway triple. Admit only the approved native keys.
for k in be:
    if forbidden_backend_key(k):
        problems.append("backend contains an unapproved configuration override key")
for n in ("backend", "postgres", "minio-init"):
    s = svc.get(n, {})
    if s.get("command") is not None or s.get("entrypoint") is not None or s.get("configs") or s.get("secrets"):
        problems.append("%s must use the verified image's configuration/entrypoint" % n)
for n in ('backend', 'minio-init'):
    if svc.get(n, {}).get('volumes'):
        problems.append('%s must not overlay the verified image with configuration/code mounts' % n)
if any(svc.get("backend", {}).get(k) for k in ("extra_hosts", "dns", "dns_search", "links", "external_links")):
    problems.append("backend must not override private service-name resolution")
pg = env_of(svc.get("postgres", {}))
if any(k.startswith("PG") or (k.startswith("POSTGRES_") and k not in {"POSTGRES_DB", "POSTGRES_USER", "POSTGRES_PASSWORD"}) for k in pg):
    problems.append("postgres contains an unapproved initialization/authentication override")
for k in ("STORAGE_BUCKET", "STORAGE_REGION"):
    values = [env_of(svc.get(n, {})).get(k) for n in ("backend", "minio-init")]
    if not values[0] or values[0] != values[1] or (k in expected and values[0] != expected[k]):
        problems.append("backend and initializer must use the same approved %s" % k)
if any(k.startswith("MINIO_ROOT_") for k in be):
    problems.append("backend must not receive MinIO root credentials")

# Inventory must cover the actual persistent state, including an external volume.
for n, target in (("postgres", "/var/lib/postgresql/data"), ("minio", "/data")):
    volumes = svc.get(n, {}).get("volumes") or []
    if len(volumes) != 1 or any(v.get("type") != "volume" or v.get("source") not in cfg.get("volumes", {}) or v.get("target") != target for v in volumes):
        problems.append("%s persistence must use declared named volumes" % n)
for v in cfg.get("volumes", {}).values():
    if set(v) - {"name"} or not v.get("name"):
        problems.append("every persistent volume needs a resolved name for recovery inventory")

if problems:
    for p in problems:
        print("  - " + p, file=sys.stderr)
    sys.exit(1)
PYEOF
PY_CHECK="${PY_ENV_POLICY}"$'\n'"${PY_CHECK}"

validate_compose() {
  log "Validating Compose configuration (no rendered output is printed)."
  COMPOSE_MODEL="$("${COMPOSE[@]}" config --format json 2>/dev/null)" \
    || fail "docker compose config failed (diagnostics suppressed to protect configuration values)."
  if ! printf '%s' "${COMPOSE_MODEL}" | python3 -c "${PY_CHECK}" "${ORIGIN}" "${ENV_FILE}"; then
    fail "Rendered Compose configuration is not deployable under the approved CT17 policy (see items above). The local base file alone is not; an approved override is required."
  fi
  RELEASE_IMAGES="$(printf '%s' "${COMPOSE_MODEL}" | python3 -c 'import json,sys; print("\n".join(sorted(set(s["image"] for s in json.load(sys.stdin)["services"].values()))))')"
}

assert_release_configuration() {
  local current
  current="$("${COMPOSE[@]}" config --format json 2>/dev/null)" || fail "Cannot recheck effective release configuration."
  [[ "${current}" == "${COMPOSE_MODEL}" ]] || fail "Effective Compose configuration changed since preflight; release stopped."
  validate_env_file
  printf '%s' "${current}" | python3 -c "${PY_CHECK}" "${ORIGIN}" "${ENV_FILE}" \
    || fail "Env bindings changed since preflight; release stopped."
}

run_preflight() {
  validate_origin
  validate_env_file
  validate_host
  build_compose_cmd
  validate_compose
  log "PREFLIGHT OK: inputs, env file, host and Compose policy checks passed."
  log "HOST_PLATFORM: ${HOST_PLATFORM}; Docker Engine ${DOCKER_SERVER_VERSION}; Docker Compose ${DOCKER_COMPOSE_VERSION}; image store ${IMAGE_STORE}"
}

# Release archive and image identity (P3, P8)
verify_image_coverage() {
  local entry ref want seen=$'\n' count=0
  for entry in ${EXPECT_IMAGES[@]+"${EXPECT_IMAGES[@]}"}; do
    ref="${entry%%=*}"; want="${entry#*=}"
    [[ -n "${ref}" && "${ref}" != *[[:space:]]* && "${want}" =~ ^sha256:[0-9a-f]{64}$ ]] \
      || fail "--expect-image must look like REF=sha256:<64 lowercase hex>."
    [[ "${seen}" != *$'\n'"${ref}"$'\n'* ]] || fail "Duplicate --expect-image reference."
    printf '%s\n' "${RELEASE_IMAGES}" | grep -Fxq -- "${ref}" || fail "An expected image is not used by the effective Compose release."
    seen+="${ref}"$'\n'; count=$(( count + 1 ))
  done
  [[ "${count}" -eq "$(printf '%s\n' "${RELEASE_IMAGES}" | wc -l | tr -d ' ')" ]] \
    || fail "Every effective Compose service image must have an approved --expect-image identity."
}

verify_images() {
  verify_image_coverage
  local entry ref want got plat
  for entry in "${EXPECT_IMAGES[@]}"; do
    ref="${entry%%=*}"; want="${entry#*=}"
    got="$(docker image inspect --format '{{.Id}}' "${ref}" 2>/dev/null)" \
      || fail "Image not present locally: ${ref}"
    [[ "${got}" == "${want}" ]] || fail "Image ${ref} content ID does not match the expected value."
    plat="$(docker image inspect --format '{{.Os}}/{{.Architecture}}' "${ref}")"
    [[ "${plat}" == "linux/amd64" ]] || fail "Image ${ref} platform is ${plat}; linux/amd64 required."
  done
  # Inspect baked defaults as well as rendered Compose inputs before starting
  # anything. Image identity alone does not establish effective configuration.
  local service
  for service in backend postgres minio minio-init frontend; do
    ref="$(printf '%s' "${COMPOSE_MODEL}" | python3 -c 'import json,sys; print(json.load(sys.stdin)["services"][sys.argv[1]]["image"])' "${service}")"
    verify_image_configuration "${service}" "${ref}"
  done
  log "All effective service image identities verified (content ID, linux/amd64)."
}

verify_image_configuration() {
  local service="$1" ref="$2"
  { printf '{"model":%s,"image":' "${COMPOSE_MODEL}"; docker image inspect --format '{{json .Config}}' "${ref}" 2>/dev/null; printf '}'; } | python3 -c "${PY_IMAGE_POLICY}" "${service}" \
    || fail "${service} image has an unsafe environment or missing executable/healthcheck configuration."
}

read -r -d '' PY_IMAGE_POLICY <<'PYEOF' || true
import json, sys
try:
    data = json.load(sys.stdin)
    service = sys.argv[1]
    expected = data['model']['services'][service].get('environment', {})
    image = data['image']
    environment = {}
    for entry in image.get('Env') or []:
        key, value = entry.split('=', 1)
        if key in environment:
            raise ValueError()
        environment[key] = value
    if service == 'backend':
        for key, value in environment.items():
            if forbidden_backend_key(key):
                raise ValueError()
            if key.startswith(('SPRING_', 'SERVER_', 'CODEMASTERY_', 'HIBERNATE_')) and key not in expected:
                raise ValueError()
    # A baked MINIO_* default (for example MINIO_CONFIG_ENV_FILE) could re-enable
    # the Console or replace the configured credentials behind the Compose model.
    if service == 'minio' and any(key.startswith('MINIO_') for key in environment):
        raise ValueError()
    if not image.get('Entrypoint') and not image.get('Cmd'):
        raise ValueError()
    if service == 'frontend':
        health = image.get('Healthcheck') or {}
        if not health.get('Test') or health['Test'][0] == 'NONE':
            raise ValueError()
except Exception:
    sys.exit(1)
PYEOF
PY_IMAGE_POLICY="${PY_ENV_POLICY}"$'\n'"${PY_IMAGE_POLICY}"

verify_archive() {
  verify_image_coverage
  [[ -f "${ARCHIVE}" && -r "${ARCHIVE}" ]] || fail "Archive not found or unreadable: ${ARCHIVE}"
  [[ "${ARCHIVE_SHA256}" =~ ^[0-9A-Fa-f]{64}$ ]] || fail "--archive-sha256 must be 64 hex characters."
  ARCHIVE_SHA256="$(printf '%s' "${ARCHIVE_SHA256}" | tr '[:upper:]' '[:lower:]')"
  local actual; actual="$(sha256sum "${ARCHIVE}" | awk '{print $1}')"
  [[ "${actual}" == "${ARCHIVE_SHA256}" ]] || fail "Archive SHA-256 mismatch; refusing release."
  [[ "${IMAGE_STORE}" == "containerd" || "${IMAGE_STORE}" == "graphdriver" ]] \
    || fail "Docker image store is unidentified; image identities cannot be verified."
  # Inspect Docker's native save manifest/configs without extracting the archive.
  # Identities are hashes of exact config/OCI bytes, not archive-supplied labels,
  # computed as the image ID this host's image store will report after load.
  python3 - "${ARCHIVE}" "${IMAGE_STORE}" "${EXPECT_IMAGES[@]}" <<'PY' || fail "Archive does not contain exactly the verified linux/amd64 release artifacts."
import hashlib, json, sys, tarfile

PLATFORM = ("linux", "amd64")
UNKNOWN_PLATFORM = ("unknown", "unknown")
INDEX_TYPES = {"application/vnd.oci.image.index.v1+json",
               "application/vnd.docker.distribution.manifest.list.v2+json"}
MANIFEST_TYPES = {"application/vnd.oci.image.manifest.v1+json",
                  "application/vnd.docker.distribution.manifest.v2+json"}
IN_TOTO = "application/vnd.in-toto+json"
SUBJECT = "io.containerd.manifest.subject"
NAME = "io.containerd.image.name"
HEX = set("0123456789abcdef")

def reference(name):
    # Docker reference normalization: default registry, library namespace and
    # latest tag. With a digest, a tag beside it is kept only for matching.
    repository, _, digest = name.partition("@")
    tag = ""
    if repository.rfind(":") > repository.rfind("/"):
        repository, tag = repository.rsplit(":", 1)
    parts = repository.split("/")
    if len(parts) == 1 or not ("." in parts[0] or ":" in parts[0] or parts[0] == "localhost"):
        parts.insert(0, "docker.io")
    if parts[0] == "docker.io" and len(parts) == 2:
        parts.insert(1, "library")
    if not tag and not digest:
        tag = "latest"
    return "/".join(parts), tag, digest

def denotes(name, ref):
    (repository, tag, digest), (want_repository, want_tag, want_digest) = name, ref
    return (repository == want_repository and bool(tag or digest)
            and (not tag or tag == want_tag) and (not digest or digest == want_digest))

def platform(value):
    value = value or {}
    return value.get("os"), value.get("architecture")

try:
    store = sys.argv[2]
    expected = dict(x.rsplit("=", 1) for x in sys.argv[3:])
    approved = [(reference(ref), identity) for ref, identity in expected.items()]

    def check_names(names, identity):
        # Every name the archive applies must be an approved reference to this image.
        for name in names:
            if {i for ref, i in approved if denotes(reference(name), ref)} != {identity}:
                raise ValueError("unapproved archive tag mapping")

    with tarfile.open(sys.argv[1], "r:*") as t:
        members = t.getmembers()
        if len({m.name for m in members}) != len(members):
            raise ValueError("duplicate archive entries")

        def blob(descriptor):
            # Every followed descriptor must match its content-addressed bytes and size.
            algorithm, _, hexdigest = descriptor["digest"].partition(":")
            if algorithm != "sha256" or len(hexdigest) != 64 or set(hexdigest) - HEX:
                raise ValueError("descriptor")
            data = t.extractfile("blobs/sha256/" + hexdigest).read()
            if hashlib.sha256(data).hexdigest() != hexdigest:
                raise ValueError("blob digest")
            if "size" in descriptor and descriptor["size"] != len(data):
                raise ValueError("descriptor size")
            return data

        def document(descriptor, kinds):
            if descriptor.get("mediaType") not in kinds:
                raise ValueError("media type")
            parsed = json.loads(blob(descriptor))
            if parsed.get("mediaType", descriptor["mediaType"]) != descriptor["mediaType"]:
                raise ValueError("media type")
            return parsed

        targets = {}      # runnable config digest -> image ID on this store
        selected = set()  # runnable manifest digests that a referrer may describe
        names = {}        # image ID -> names the archive index applies
        if store == "containerd":
            # The image ID is the top-level index or manifest digest. A registry
            # index lists every platform, but Docker saves only the platforms it
            # holds: select the unique linux/amd64 manifest and never read the
            # other platforms. Attestation children are not runnable images.
            referrers = []
            for top in json.load(t.extractfile("index.json"))["manifests"]:
                annotations = top.get("annotations") or {}
                if SUBJECT in annotations:
                    referrers.append(top)
                    continue
                if top.get("mediaType") in INDEX_TYPES:
                    candidates = []
                    for child in document(top, INDEX_TYPES)["manifests"]:
                        if (child.get("annotations") or {}).get("vnd.docker.reference.type") == "attestation-manifest":
                            if platform(child.get("platform")) != UNKNOWN_PLATFORM:
                                raise ValueError("attestation platform")
                        elif platform(child.get("platform")) == PLATFORM:
                            candidates.append(child)
                    if len(candidates) != 1 or candidates[0]["platform"].get("variant"):
                        raise ValueError("platform")
                    descriptor = candidates[0]
                else:
                    descriptor = top
                image = document(descriptor, MANIFEST_TYPES)
                if targets.setdefault(image["config"]["digest"], top["digest"]) != top["digest"]:
                    raise ValueError("ambiguous image identity")
                selected.add(descriptor["digest"])
                names.setdefault(top["digest"], set())
                if NAME in annotations:
                    names[top["digest"]].add(annotations[NAME])
            # `docker save --platform` writes each image's attestation as a
            # top-level referrer. Accept only an unnamed in-toto statement about
            # a selected image; it must never resolve to a runnable image.
            for top in referrers:
                annotations = top["annotations"]
                if (NAME in annotations or "org.opencontainers.image.ref.name" in annotations
                        or annotations[SUBJECT] not in selected):
                    raise ValueError("unexpected referrer")
                statement = document(top, MANIFEST_TYPES)
                layers = statement.get("layers") or []
                if (not layers or any(layer.get("mediaType") != IN_TOTO for layer in layers)
                        or platform(json.loads(blob(statement["config"]))) != UNKNOWN_PLATFORM
                        or statement["config"]["digest"] in targets):
                    raise ValueError("unexpected referrer")
        elif store != "graphdriver":
            raise ValueError("image store")

        ids = set()
        resolved = set()
        for item in json.load(t.extractfile("manifest.json")):
            data = t.extractfile(item["Config"]).read()
            config_digest = "sha256:" + hashlib.sha256(data).hexdigest()
            identity = targets.get(config_digest) if store == "containerd" else config_digest
            if identity is None:
                raise ValueError("image without a verified OCI identity")
            resolved.add(config_digest)
            if platform(json.loads(data)) != PLATFORM:
                raise ValueError("platform")
            if identity not in expected.values():
                raise ValueError("unapproved archive image")
            ids.add(identity)
            check_names(item.get("RepoTags") or [], identity)
        if ids != set(expected.values()):
            raise ValueError("missing artifact")
        if store == "containerd":
            if set(targets) != resolved:
                raise ValueError("unexpected archive image")
            for identity, applied in names.items():
                check_names(applied, identity)
except Exception:
    sys.exit(1)  # Do not print untrusted archive metadata or contents.
PY
}

run_load() {
  verify_archive
  log "Archive checksum verified. Loading images (no registry pull, no rebuild)."
  docker load -i "${ARCHIVE}" >/dev/null 2>&1 || fail "docker load failed."
  verify_images
  log "Deployment authority reference: ${DEPLOY_AUTHORIZATION}"
}

verify_container_images() {
  local service cid
  for service in "$@"; do
    cid="$("${COMPOSE[@]}" ps -a -q "${service}" 2>/dev/null)" || fail "Cannot inspect release container inventory."
    [[ -n "${cid}" && "${cid}" != *$'\n'* ]] || fail "Expected exactly one ${service} container."
    verify_service_container "${service}" "${cid}"
  done
}

verify_service_container() {
  local service="$1" cid="$2" ref want="" got entry
  ref="$(printf '%s' "${COMPOSE_MODEL}" | python3 -c 'import json,sys; print(json.load(sys.stdin)["services"][sys.argv[1]]["image"])' "${service}")"
  for entry in "${EXPECT_IMAGES[@]}"; do
    [[ "${entry%%=*}" != "${ref}" ]] || want="${entry#*=}"
  done
  got="$(docker inspect --format '{{.Image}}' "${cid}" 2>/dev/null)" || fail "Cannot inspect ${service} image."
  [[ -n "${want}" && "${got}" == "${want}" ]] || fail "${service} is not running its verified release image."
  # An existing container can use the right image with stale database/secrets
  # bindings. Compare actual container values in memory, never on the command line.
  if ! { printf '{"model":%s,"container":' "${COMPOSE_MODEL}"; docker inspect --format '{{json .Config}}' "${cid}" 2>/dev/null; printf ',"image":'; docker image inspect --format '{{json .Config}}' "${ref}" 2>/dev/null; printf '}'; } | python3 -c "${PY_ENV_POLICY}"$'\n''
import json,sys
data = json.load(sys.stdin)
cfg = data["model"]
container = data["container"]
actual = {}
for entry in container.get("Env") or []:
    key, value = entry.split("=",1)
    if key in actual:
        sys.exit(1)
    actual[key] = value
expected = cfg["services"][sys.argv[1]].get("environment",{})
if any(actual.get(k) != str(v) for k,v in expected.items()):
    sys.exit(1)
if sys.argv[1] == "backend" and any(forbidden_backend_key(k) or (k.startswith(("SPRING_", "SERVER_", "CODEMASTERY_", "HIBERNATE_")) and k not in expected) for k in actual):
    sys.exit(1)
if sys.argv[1] == "minio" and {k for k in actual if k.startswith("MINIO_")} != {k for k in expected if k.startswith("MINIO_")}:
    sys.exit(1)
if sys.argv[1] in ("backend", "postgres", "minio", "minio-init", "frontend"):
    service = cfg["services"][sys.argv[1]]
    expected_entrypoint = service.get("entrypoint")
    expected_command = service.get("command")
    if expected_entrypoint is None:
        expected_entrypoint = data["image"].get("Entrypoint")
    if expected_command is None:
        expected_command = data["image"].get("Cmd")
    if (container.get("Entrypoint") or []) != (expected_entrypoint or []):
        sys.exit(1)
    if (container.get("Cmd") or []) != (expected_command or []):
        sys.exit(1)
if sys.argv[1] == "frontend" and container.get("Healthcheck") != data["image"].get("Healthcheck"):
    sys.exit(1)
' "${service}" 2>/dev/null; then
    fail "${service} container environment, executable or healthcheck differs from the verified release configuration."
  fi
}

# Actual Docker port publication (P2/B2). Static Compose checks cover the model;
# this checks what Docker was asked to publish and is publishing. It says nothing
# about host packet filtering, which remains operator evidence.
verify_published_ports() {
  local service cid summary
  for service in "$@"; do
    cid="$("${COMPOSE[@]}" ps -a -q "${service}" 2>/dev/null)" || fail "Cannot inspect release container inventory."
    [[ -n "${cid}" && "${cid}" != *$'\n'* ]] || fail "Expected exactly one ${service} container."
    summary="$({ printf '{"model":%s,"container":' "${COMPOSE_MODEL}"; docker inspect --format '{"host":{{json .HostConfig}},"ports":{{json .NetworkSettings.Ports}},"running":{{json .State.Running}}}' "${cid}" 2>/dev/null; printf '}'; } | python3 -c "${PY_PORT_POLICY}" "${service}")" \
      || fail "${service} host port publication differs from the approved ingress (frontend 443/tcp to 8080 only)."
    [[ "${service}" != "frontend" ]] \
      || PUBLISHED_PORTS_RESULT="frontend 8080/tcp on ${summary}; no other release service publishes host ports"
  done
}

read -r -d '' PY_PORT_POLICY <<'PYEOF' || true
import json, sys
try:
    data = json.load(sys.stdin)
    service = sys.argv[1]
    host = data["container"]["host"] or {}
    network_mode = str(host.get("NetworkMode") or "")
    if host.get("PublishAllPorts") or network_mode == "host" or network_mode.startswith("container:"):
        raise ValueError()
    configured = {k: v for k, v in (host.get("PortBindings") or {}).items() if v}
    actual = {k: v for k, v in (data["container"]["ports"] or {}).items() if v}
    if service != "frontend":
        if configured or actual:
            raise ValueError()
        sys.exit(0)
    declared = [p.get("host_ip") or "" for p in data["model"]["services"]["frontend"].get("ports") or []]
    if len(declared) != 1 or data["container"]["running"] is not True:
        raise ValueError()
    if set(configured) != {"8080/tcp"} or set(actual) != {"8080/tcp"}:
        raise ValueError()
    if any(b.get("HostPort") != "443" or (b.get("HostIp") or "") != declared[0]
           for b in configured["8080/tcp"]):
        raise ValueError()
    # Without a declared host_ip Docker binds its unspecified addresses.
    addresses = {declared[0]} if declared[0] else {"0.0.0.0", "::"}
    if any(b.get("HostPort") != "443" or b.get("HostIp") not in addresses
           for b in actual["8080/tcp"]):
        raise ValueError()
    print(", ".join(sorted(("[%s]" if ":" in b["HostIp"] else "%s") % b["HostIp"] + ":443"
                           for b in actual["8080/tcp"])))
except Exception:
    sys.exit(1)
PYEOF

# PostgreSQL identities (P6): provision, harden, verify.
# Only expected SQL goes to these helpers; diagnostics are never emitted verbatim.
psql_admin() {  # bootstrap administrator over the container's local socket
  "${COMPOSE[@]}" exec -T postgres sh -c \
    'unset PGSERVICE PGSERVICEFILE PGHOST PGHOSTADDR PGPORT PGOPTIONS; exec psql -X -q -tA -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" -f -'
}

psql_as() {  # $1=role $2=password; SQL on stdin. TCP to the service name, so the password is really checked.
  local role="$1" pw="$2" sql
  sql="$(cat)"
  printf '%s\n%s\n' "${pw}" "${sql}" | "${COMPOSE[@]}" exec -T postgres sh -c \
    'unset PGSERVICE PGSERVICEFILE PGHOSTADDR PGOPTIONS; IFS= read -r PGPASSWORD; export PGPASSWORD; exec psql -X -q -tA -v ON_ERROR_STOP=1 -v VERBOSITY=verbose -h postgres -p 5432 -U "'"${role}"'" -d "$POSTGRES_DB" -f -'
}

expect_denied() {  # description expected-SQLSTATE role password; SQL on stdin
  local desc="$1" expected_state="$2" output; shift 2
  if output="$(psql_as "$@" 2>&1)"; then
    fail "ROLE SEPARATION FAILED: ${desc} was allowed."
  fi
  case "${expected_state}" in
    (28P01)
      [[ "${output}" == *"password authentication failed"* ]] \
        || fail "ROLE SEPARATION INCONCLUSIVE: ${desc} did not fail with PostgreSQL's password-authentication error."
      ;;
    (*)
      [[ "${output}" == *"SQL state: ${expected_state}"* || "${output}" == *"${expected_state}:"* ]] \
        || fail "ROLE SEPARATION INCONCLUSIVE: ${desc} did not fail with the expected PostgreSQL SQLSTATE ${expected_state}."
      ;;
  esac
  log "  ok: ${desc} is denied"
}

expect_allowed() {  # description role password; SQL on stdin
  local desc="$1"; shift
  psql_as "$@" >/dev/null 2>&1 || fail "ROLE CHECK FAILED: ${desc} was not allowed."
  log "  ok: ${desc} is allowed"
}

provision_db_roles() {
  local db app app_pw mig mig_pw sql existing_roles
  db="$(env_get POSTGRES_DB)"
  app="$(env_get DB_USER)";            app_pw="$(env_get DB_PASSWORD)"
  mig="$(env_get SPRING_FLYWAY_USER)"; mig_pw="$(env_get SPRING_FLYWAY_PASSWORD)"
  # POSTGRES_PASSWORD only initializes an empty volume; an existing cluster can
  # retain a different password despite a matching container environment.
  expect_allowed "bootstrap database connection" "$(env_get POSTGRES_USER)" "$(env_get POSTGRES_PASSWORD)" <<<'SELECT 1;'
  expect_denied "bootstrap connection with an incorrect password" 28P01 "$(env_get POSTGRES_USER)" "$(env_get POSTGRES_PASSWORD).invalid" <<<'SELECT 1;'
  existing_roles="$(psql_admin 2>/dev/null <<<"SELECT coalesce(string_agg(rolname, ',' ORDER BY rolname), '') FROM pg_roles WHERE rolname IN ('${app}', '${mig}');")" \
    || fail "Could not inspect existing database identities before provisioning."
  if [[ ",${existing_roles}," == *",${app},"* ]]; then
    verify_db_credential "runtime" "${app}" "${app_pw}"
  fi
  if [[ ",${existing_roles}," == *",${mig},"* ]]; then
    verify_db_credential "migrator" "${mig}" "${mig_pw}"
  fi
  if [[ ",${existing_roles}," == *",${app},"* ]]; then
    report_runtime_privilege_drift
  fi

  read -r -d '' sql <<'SQL' || true
-- Never write statement text (it contains passwords) to the server log on error.
SET log_min_error_statement = 'panic';
SET log_statement = 'none';
SET log_min_duration_statement = -1;
SET log_min_duration_sample = -1;
SET log_transaction_sample_rate = 0;
BEGIN;
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
GRANT CONNECT ON DATABASE "@DB@" TO "@APP@", "@MIG@";
-- TEMPORARY is granted to PUBLIC by PostgreSQL. A per-role REVOKE cannot
-- override that grant. Conflicting explicit/membership grants fail the policy.
REVOKE TEMPORARY ON DATABASE "@DB@" FROM PUBLIC;
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
GRANT USAGE, CREATE ON SCHEMA public TO "@MIG@";
GRANT USAGE ON SCHEMA public TO "@APP@";
-- Do not grant runtime rights by default: that would expose Flyway history while
-- migrations run. Grant existing application objects explicitly, before ingress.
ALTER DEFAULT PRIVILEGES FOR ROLE "@MIG@" IN SCHEMA public REVOKE ALL ON TABLES FROM "@APP@";
ALTER DEFAULT PRIVILEGES FOR ROLE "@MIG@" IN SCHEMA public REVOKE ALL ON SEQUENCES FROM "@APP@";
SQL
  sql="${sql//@MIGPW@/${mig_pw}}"; sql="${sql//@APPPW@/${app_pw}}"
  sql="${sql//@MIG@/${mig}}";      sql="${sql//@APP@/${app}}";  sql="${sql//@DB@/${db}}"

  log "Provisioning PostgreSQL roles: migrator and runtime (existing roles are not overwritten)."
  if ! { printf '%s\n' "${sql}"; role_policy_sql; application_grants_sql; printf 'COMMIT;\n'; } | psql_admin >/dev/null 2>&1; then
    fail "PostgreSQL role provisioning failed (output suppressed: it may contain credentials). Check role state with the bootstrap administrator."
  fi
  verify_db_credentials
  verify_runtime_privileges present
  log "Roles ready: '${mig}' (migrations) and '${app}' (runtime)."
}

verify_db_credentials() {
  local app app_pw mig mig_pw
  app="$(env_get DB_USER)"; app_pw="$(env_get DB_PASSWORD)"
  mig="$(env_get SPRING_FLYWAY_USER)"; mig_pw="$(env_get SPRING_FLYWAY_PASSWORD)"
  verify_db_credential "runtime" "${app}" "${app_pw}"
  verify_db_credential "migrator" "${mig}" "${mig_pw}"
}

verify_db_credential() {
  local label="$1" role="$2" password="$3"
  expect_allowed "${label} database connection" "${role}" "${password}" <<<'SELECT 1;'
  expect_denied "${label} connection with an incorrect password" 28P01 "${role}" "${password}.invalid" <<<'SELECT 1;'
}

role_policy_sql() {
  local sql app mig
  app="$(env_get DB_USER)"; mig="$(env_get SPRING_FLYWAY_USER)"
  read -r -d '' sql <<'SQL' || true
DO $policy$
BEGIN
  -- Examine the complete membership closure, including NOINHERIT groups whose
  -- SET option permits a later SET ROLE, and cycles (UNION makes this finite).
  IF EXISTS (
    WITH RECURSIVE reachable(root, roleid) AS (
      SELECT oid, oid FROM pg_roles WHERE rolname IN ('@APP@', '@MIG@')
      UNION
      SELECT r.root, m.roleid FROM reachable r JOIN pg_auth_members m ON m.member=r.roleid
    )
    SELECT 1 FROM reachable r JOIN pg_roles target ON target.oid=r.roleid
      JOIN pg_roles origin ON origin.oid=r.root
    WHERE target.rolsuper OR target.rolcreatedb OR target.rolcreaterole
      OR target.rolreplication OR target.rolbypassrls
      OR target.rolname LIKE 'pg\_%' ESCAPE '\'
      OR EXISTS (SELECT 1 FROM pg_database WHERE datdba=target.oid)
      OR EXISTS (SELECT 1 FROM pg_database WHERE NOT datistemplate
                 AND datname NOT IN (current_database(), 'postgres')
                 AND has_database_privilege(target.oid, oid, 'CONNECT'))
      OR has_database_privilege(target.oid, current_database(), 'CREATE')
      OR EXISTS (
        SELECT 1 FROM pg_namespace n
        WHERE n.nspname NOT LIKE 'pg\_%' ESCAPE '\'
          AND n.nspname <> 'information_schema'
          AND (n.nspowner=target.oid OR has_schema_privilege(target.oid, n.oid, 'CREATE'))
          AND NOT (origin.rolname='@MIG@' AND n.nspname='public'))
      OR EXISTS (
        SELECT 1 FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
        WHERE n.nspname NOT LIKE 'pg\_%' ESCAPE '\'
          AND n.nspname <> 'information_schema' AND c.relowner=target.oid
          AND NOT (origin.rolname='@MIG@' AND n.nspname='public'))
      OR EXISTS (
        SELECT 1 FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
        WHERE n.nspname NOT LIKE 'pg\_%' ESCAPE '\'
          AND n.nspname NOT IN ('information_schema', 'public')
          AND c.relkind IN ('r','p','v','m','S')
          AND (CASE WHEN c.relkind='S'
                    THEN has_sequence_privilege(target.oid,c.oid,'USAGE,SELECT,UPDATE')
                    ELSE has_table_privilege(target.oid,c.oid,'SELECT,INSERT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER') END))
      -- The runtime role needs no membership: any granted role (INHERIT or SET)
      -- could add privileges outside the approved matrix.
      OR (origin.rolname='@APP@' AND (
        target.oid <> origin.oid
        OR target.rolname='@MIG@'
        OR has_database_privilege(target.oid, current_database(), 'TEMPORARY')
        OR has_schema_privilege(target.oid, 'public', 'CREATE')
        OR EXISTS (SELECT 1 FROM pg_namespace WHERE nspowner=target.oid AND nspname='public')
        OR EXISTS (SELECT 1 FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
                   WHERE n.nspname='public' AND c.relowner=target.oid)))
  ) THEN
    RAISE EXCEPTION 'Unexpected direct or indirect application-role elevation; operator reconciliation required';
  END IF;
END
$policy$;
SQL
  printf '%s\n' "${sql//@APP@/${app}}" | sed "s/@MIG@/${mig}/g"
}

# CT17-P6-PRIVILEGES-2026-10-28-r1 (docs/deployment.md): the complete runtime
# privilege matrix for the reviewed V001-V006 schema. Every relation in schema
# public must be listed, and its reviewed columns are pinned, so a later migration
# cannot silently widen a table-level grant. Nothing is granted to future objects.
# Change a row only together with that record and an implemented, approved consumer.
# Fields: relation, table privileges, column privileges (PRIV(col,...) joined by
# '+'), reviewed columns ('*' = Flyway-managed, not pinned); '-' means none.
RUNTIME_PRIVILEGE_MATRIX='
users                    SELECT,INSERT  -                                        id,email,password_hash,display_name,role,account_status
courses                  SELECT         UPDATE(id)                               id,instructor_id,title,description,prerequisite_description,publication_status,adaptive_enabled,adaptive_settings
chapters                 SELECT         -                                        id,course_id,title,position
lessons                  SELECT         -                                        id,chapter_id,title,content_markdown,position,publication_status
learning_materials       -              SELECT(id,lesson_id,publication_status)  id,lesson_id,title,material_type,publication_status,text_content,object_key,original_filename,external_video_url
enrollments              SELECT,INSERT  UPDATE(status)                           id,user_id,course_id,status,resume_lesson_id
lesson_progress          -              -                                        id,enrollment_id,lesson_id,completed,mastery_skipped
skills                   -              -                                        id,course_id,name,description
skill_prerequisites      -              -                                        id,course_id,skill_id,prerequisite_skill_id
lesson_skills            -              -                                        id,lesson_id,skill_id
quizzes                  -              -                                        id,course_id,title,kind,time_limit_seconds,publication_status
questions                -              -                                        id,skill_id,prompt,kind,difficulty,options,correct_options
quiz_questions           -              -                                        id,quiz_id,question_id,position
attempts                 -              -                                        id,user_id,quiz_id,attempt_number,started_at,expires_at,submitted_at,score_percentage
attempt_answers          -              -                                        id,attempt_id,question_id,position,selected_options,is_correct
assignments              -              -                                        id,course_id,lesson_id,title,instructions,deadline_at,publication_status
submissions              -              -                                        id,assignment_id,user_id,object_key,original_filename,submitted_at,grade_percentage,feedback,graded_at
learner_skill_masteries  -              -                                        id,user_id,skill_id,state
path_recommendations     -              -                                        id,enrollment_id,lesson_id,recommendation_type,reason,recommended_at
discussion_posts         -              -                                        id,lesson_id,author_id,parent_post_id,content,created_at,is_hidden
notifications            -              -                                        id,user_id,message,is_read,created_at
content_reports          -              -                                        id,reporter_id,discussion_post_id,lesson_id,reason,status,created_at,resolved_at
audit_logs               -              -                                        id,actor_id,action,target_type,target_reference,occurred_at
flyway_schema_history    -              -                                        *
'

runtime_matrix_values() {  # the matrix as SQL VALUES rows; non-zero on any malformed row
  local rel tprivs cprivs cols extra group priv col t c k rows="" seen="," nl=$'\n'
  local ident='[a-z_][a-z0-9_]*'
  while read -r rel tprivs cprivs cols extra; do
    [[ -n "${rel}" ]] || continue
    [[ -z "${extra}" && "${rel}" =~ ^${ident}$ && "${seen}" != *",${rel},"* ]] || return 1
    seen+="${rel},"
    [[ "${tprivs}" == "-" || "${tprivs}" =~ ^(SELECT|INSERT|UPDATE|DELETE)(,(SELECT|INSERT|UPDATE|DELETE))*$ ]] || return 1
    [[ "${cols}" == "*" || "${cols}" =~ ^${ident}(,${ident})*$ ]] || return 1
    [[ "${cprivs}" == "-" || ( "${cols}" != "*" \
       && "${cprivs}" =~ ^(SELECT|INSERT|UPDATE)\(${ident}(,${ident})*\)(\+(SELECT|INSERT|UPDATE)\(${ident}(,${ident})*\))*$ ) ]] || return 1
    t=""; c=""; k=""
    [[ "${tprivs}" == "-" ]] || for priv in ${tprivs//,/ }; do t+="${t:+,}'${priv}'"; done
    [[ "${cols}" == "*" ]] || for col in ${cols//,/ }; do k+="${k:+,}'${col}'"; done
    if [[ "${cprivs}" != "-" ]]; then
      for group in ${cprivs//+/ }; do
        priv="${group%%(*}"; group="${group#*(}"; group="${group%)}"
        for col in ${group//,/ }; do
          [[ ",${cols}," == *",${col},"* ]] || return 1
          c+="${c:+,}'${priv}:${col}'"
        done
      done
    fi
    rows+="${rows:+,${nl}}('${rel}',ARRAY[${t}]::text[],ARRAY[${c}]::text[],ARRAY[${k}]::text[])"
  done <<<"${RUNTIME_PRIVILEGE_MATRIX}"
  [[ -n "${rows}" ]] || return 1
  printf '%s\n' "${rows}"
}

application_grants_sql() {
  local sql app mig values
  app="$(env_get DB_USER)"; mig="$(env_get SPRING_FLYWAY_USER)"
  values="$(runtime_matrix_values)" || fail "The runtime privilege matrix is malformed; nothing was granted."
  read -r -d '' sql <<'SQL' || true
DO $grants$
DECLARE obj record; entry record; item text;
BEGIN
  FOR obj IN SELECT c.relname, c.relkind, c.relowner FROM pg_class c
    JOIN pg_namespace n ON n.oid=c.relnamespace
    WHERE n.nspname='public' AND c.relkind IN ('r','p','v','m','S','f')
  LOOP
    IF obj.relowner <> (SELECT oid FROM pg_roles WHERE rolname='@MIG@') THEN
      RAISE EXCEPTION 'Unexpected application object owner; no ownership transfer is permitted';
    END IF;
    -- Reset the owner-granted runtime ACL, column grants included. No CASCADE:
    -- grants that the runtime role passed on to other roles stop provisioning.
    EXECUTE format('REVOKE ALL ON %s public.%I FROM %I',
                   CASE WHEN obj.relkind='S' THEN 'SEQUENCE' ELSE 'TABLE' END, obj.relname, '@APP@');
  END LOOP;
  -- Grant exactly the approved matrix on reviewed tables that exist. Verification
  -- rejects unexpected relations, column drift and privileges from any other source.
  FOR entry IN SELECT * FROM (VALUES
@MATRIX@
  ) AS m(relname, table_privs, column_privs, columns)
  LOOP
    CONTINUE WHEN NOT EXISTS (SELECT 1 FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
                              WHERE n.nspname='public' AND c.relname=entry.relname AND c.relkind='r');
    IF cardinality(entry.table_privs) > 0 THEN
      EXECUTE format('GRANT %s ON TABLE public.%I TO %I',
                     array_to_string(entry.table_privs, ', '), entry.relname, '@APP@');
    END IF;
    FOREACH item IN ARRAY entry.column_privs LOOP
      EXECUTE format('GRANT %s (%I) ON TABLE public.%I TO %I',
                     split_part(item, ':', 1), split_part(item, ':', 2), entry.relname, '@APP@');
    END LOOP;
  END LOOP;
END
$grants$;
SQL
  sql="${sql//@MATRIX@/${values}}"
  printf '%s\n' "${sql//@APP@/${app}}" | sed "s/@MIG@/${mig}/g"
}

runtime_privilege_violations_sql() {  # present|complete; one row per violation, none when exact
  local sql app values mode="$1"
  [[ "${mode}" == "present" || "${mode}" == "complete" ]] || fail "Unknown privilege verification mode."
  app="$(env_get DB_USER)"
  values="$(runtime_matrix_values)" || fail "The runtime privilege matrix is malformed."
  # Effective privileges count PUBLIC and inherited grants. has_table_privilege
  # ignores column grants, so both levels are compared separately.
  read -r -d '' sql <<'SQL' || true
WITH matrix(relname, table_privs, column_privs, columns) AS (VALUES
@MATRIX@
), app AS (SELECT oid FROM pg_roles WHERE rolname='@APP@'),
rel AS (SELECT c.oid, c.relname::text AS relname, c.relkind FROM pg_class c
        JOIN pg_namespace n ON n.oid=c.relnamespace
        WHERE n.nspname='public' AND c.relkind IN ('r','p','v','m','S','f')),
reviewed AS (SELECT r.oid, m.* FROM matrix m JOIN rel r USING (relname) WHERE r.relkind='r'),
table_priv(priv) AS (VALUES ('SELECT'),('INSERT'),('UPDATE'),('DELETE'),('TRUNCATE'),('REFERENCES'),('TRIGGER')),
column_priv(priv) AS (VALUES ('SELECT'),('INSERT'),('UPDATE'),('REFERENCES'))
SELECT 'runtime role @APP@ does not exist' WHERE NOT EXISTS (SELECT 1 FROM app)
UNION ALL
SELECT 'unexpected relation public.' || r.relname FROM rel r
 WHERE r.relname NOT IN (SELECT relname FROM matrix)
UNION ALL
SELECT 'unexpected relation kind for public.' || r.relname FROM rel r JOIN matrix m USING (relname)
 WHERE r.relkind <> 'r'
UNION ALL
SELECT 'missing reviewed table public.' || m.relname FROM matrix m
 WHERE '@MODE@'='complete' AND NOT EXISTS (SELECT 1 FROM rel r WHERE r.relname=m.relname)
UNION ALL
SELECT 'columns differ from the reviewed matrix on public.' || v.relname FROM reviewed v
 WHERE cardinality(v.columns) > 0
   AND ARRAY(SELECT a.attname::text FROM pg_attribute a
             WHERE a.attrelid=v.oid AND a.attnum>0 AND NOT a.attisdropped ORDER BY a.attname::text COLLATE "C")
       <> ARRAY(SELECT x FROM unnest(v.columns) x ORDER BY x COLLATE "C")
UNION ALL
SELECT format('table privilege %s on public.%s: expected %s', p.priv, v.relname, (p.priv = ANY (v.table_privs))::text)
  FROM reviewed v CROSS JOIN app CROSS JOIN table_priv p
 WHERE has_table_privilege(app.oid, v.oid, p.priv) <> (p.priv = ANY (v.table_privs))
UNION ALL
SELECT format('table privilege MAINTAIN on public.%s: expected false', v.relname)
  FROM reviewed v CROSS JOIN app
 WHERE CASE WHEN current_setting('server_version_num')::int >= 170000
            THEN has_table_privilege(app.oid, v.oid, 'MAINTAIN') ELSE false END
UNION ALL
SELECT format('column privilege %s on public.%s.%s: expected %s', p.priv, v.relname, a.attname,
              (p.priv = ANY (v.table_privs) OR (p.priv || ':' || a.attname) = ANY (v.column_privs))::text)
  FROM reviewed v CROSS JOIN app CROSS JOIN column_priv p
  JOIN pg_attribute a ON a.attrelid=v.oid AND a.attnum>0 AND NOT a.attisdropped
 WHERE has_column_privilege(app.oid, v.oid, a.attnum, p.priv)
       <> (p.priv = ANY (v.table_privs) OR (p.priv || ':' || a.attname) = ANY (v.column_privs))
UNION ALL
SELECT format('unexpected routine public.%s(%s)', p.proname, pg_get_function_identity_arguments(p.oid)) FROM pg_proc p
  JOIN pg_namespace n ON n.oid=p.pronamespace WHERE n.nspname='public'
UNION ALL
SELECT 'default privileges grant future ' || CASE d.defaclobjtype WHEN 'r' THEN 'tables' ELSE 'sequences' END
       || ' to ' || CASE WHEN x.grantee=0 THEN 'PUBLIC' ELSE 'the runtime role' END
  FROM pg_default_acl d CROSS JOIN LATERAL aclexplode(d.defaclacl) x
 WHERE d.defaclobjtype IN ('r','S') AND d.defaclnamespace IN (0, 'public'::regnamespace)
   AND (x.grantee=0 OR x.grantee IN (SELECT oid FROM app))
ORDER BY 1;
SQL
  sql="${sql//@MATRIX@/${values}}"; sql="${sql//@MODE@/${mode}}"
  printf '%s\n' "${sql//@APP@/${app}}"
}

report_runtime_privilege_drift() {
  # Owner-granted excess is reset by application_grants_sql; any other source
  # (PUBLIC, membership, another grantor) survives that reset and fails verification.
  local excess
  excess="$(runtime_privilege_violations_sql present | psql_admin 2>/dev/null | grep -F ': expected false' || true)"
  [[ -n "${excess}" ]] || return 0
  log "Runtime privileges beyond the approved matrix were detected; owner-granted ones are revoked now:"
  printf '%s\n' "${excess}" | head -n 25 | sed 's/^/    excess: /'
}

verify_runtime_privileges() {  # present (before migration) | complete (after migration)
  local mode="$1" violations
  violations="$(runtime_privilege_violations_sql "${mode}" | psql_admin 2>/dev/null)" \
    || fail "Could not verify runtime privileges against the approved matrix; ingress remains closed."
  if [[ -n "${violations}" ]]; then
    printf '%s\n' "${violations}" | head -n 25 | sed 's/^/  violation: /' >&2
    fail "Runtime database privileges or objects differ from the approved matrix (CT17-P6-PRIVILEGES-2026-10-28-r1); reconcile explicitly. Ingress remains closed."
  fi
  if [[ "${mode}" == "complete" ]]; then
    DB_GRANTS_RESULT="exact CT17-P6-PRIVILEGES-2026-10-28-r1 matrix verified (every public relation reviewed)"
  fi
  log "  ok: runtime privileges equal the approved matrix (${mode} schema)"
}

harden_after_migration() {
  report_runtime_privilege_drift
  { printf 'BEGIN;\n'; role_policy_sql; application_grants_sql; printf 'COMMIT;\n'; } | psql_admin >/dev/null 2>&1 \
    || fail "Application grants/history hardening failed; ingress remains closed. Reconcile unexpected ownership/privileges explicitly."
}

verify_role_separation() {
  local app app_pw mig mig_pw owned by
  app="$(env_get DB_USER)";            app_pw="$(env_get DB_PASSWORD)"
  mig="$(env_get SPRING_FLYWAY_USER)"; mig_pw="$(env_get SPRING_FLYWAY_PASSWORD)"
  log "Verifying migration/runtime privilege separation (all probes are rolled back)."
  role_policy_sql | psql_admin >/dev/null 2>&1 || fail "Direct/indirect database role elevation detected."

  verify_db_credentials
  expect_denied  "runtime role creating a table (DDL)" 42501 "${app}" "${app_pw}" <<'SQL'
BEGIN;
CREATE TABLE public.cm_ddl_probe (x integer);
ROLLBACK;
SQL
  expect_denied "runtime role creating a temporary table (DDL)" 42501 "${app}" "${app_pw}" <<'SQL'
BEGIN;
CREATE TEMP TABLE cm_temp_ddl_probe (x integer);
ROLLBACK;
SQL
  expect_denied  "runtime role reading Flyway history" 42501 "${app}" "${app_pw}" <<<'SELECT 1 FROM public.flyway_schema_history LIMIT 1;'
  # Behavioral probes complement the exact ACL comparison below; none touches a row.
  expect_denied  "runtime role changing account roles or credentials" 42501 "${app}" "${app_pw}" <<<'UPDATE public.users SET role = role WHERE false;'
  expect_denied  "runtime role changing Course publication" 42501 "${app}" "${app_pw}" <<<'UPDATE public.courses SET publication_status = publication_status WHERE false;'
  expect_denied  "runtime role deleting memberships" 42501 "${app}" "${app_pw}" <<<'DELETE FROM public.enrollments WHERE false;'
  expect_denied  "runtime role reading question answer keys" 42501 "${app}" "${app_pw}" <<<'SELECT correct_options FROM public.questions WHERE false;'
  expect_denied  "runtime role reading protected material payloads" 42501 "${app}" "${app_pw}" <<<'SELECT object_key FROM public.learning_materials WHERE false;'
  expect_denied  "runtime role erasing audit records" 42501 "${app}" "${app_pw}" <<<'DELETE FROM public.audit_logs WHERE false;'
  expect_allowed "runtime role locking a published Course root (CT22 membership)" "${app}" "${app_pw}" <<'SQL'
BEGIN;
SELECT id FROM public.courses WHERE false FOR UPDATE;
ROLLBACK;
SQL
  expect_allowed "migrator role creating a table (DDL, rolled back)" "${mig}" "${mig_pw}" <<'SQL'
BEGIN;
CREATE TABLE public.cm_ddl_probe (x integer);
ROLLBACK;
SQL
  verify_runtime_privileges complete

  owned="$(psql_admin 2>/dev/null <<<"SELECT count(*) FROM pg_tables WHERE schemaname='public' AND tableowner='${app}';")"
  [[ "${owned}" == "0" ]] || fail "ROLE SEPARATION FAILED: the runtime role owns ${owned} table(s) in public."
  log "  ok: runtime role owns no tables"

  by="$(psql_admin 2>/dev/null <<<"SELECT coalesce(string_agg(DISTINCT installed_by, ','), 'none') FROM public.flyway_schema_history;")"
  if [[ "${by}" == "${mig}" ]]; then
    log "  ok: Flyway history installed_by = migrator only"
  else
    log "  WARNING: Flyway history installed_by = ${by} (expected '${mig}'; mixed identities are expected only when updating an older setup)."
  fi
  DB_SEPARATION_RESULT="verified (Flyway installed_by: ${by})"
}
# Provisioning: PostgreSQL + MinIO availability (not feature behavior)
run_provision() {
  assert_release_configuration
  verify_images
  log "Starting PostgreSQL and MinIO (private network; images must already be loaded)."
  "${COMPOSE[@]}" up -d --no-build --pull never --no-deps --wait --wait-timeout "${WAIT_TIMEOUT}" postgres minio \
    || fail "PostgreSQL/MinIO did not become ready within ${WAIT_TIMEOUT}s."
  verify_container_images postgres minio

  provision_db_roles

  log "Running the verified-image MinIO initializer; qualification record ${INITIALIZER_QUALIFICATION} is operator-supplied."
  assert_release_configuration
  "${COMPOSE[@]}" up -d --no-build --pull never --no-deps minio-init >/dev/null \
    || fail "Could not start minio-init."
  local cid code
  cid="$("${COMPOSE[@]}" ps -a -q minio-init)"
  [[ -n "${cid}" ]] || fail "minio-init container not found."
  code="$(timeout "${WAIT_TIMEOUT}" docker wait "${cid}")" \
    || fail "minio-init did not finish within ${WAIT_TIMEOUT}s."
  if [[ "${code}" != "0" ]]; then
    fail "minio-init exited with code ${code}; backend startup must not proceed."
  fi
  verify_container_images minio-init
  verify_published_ports postgres minio minio-init
  log "PROVISION PROCESS CHECK: postgres ready, minio running, minio-init exited 0."
  log "MinIO policy/state correctness and safe re-execution require qualified initializer evidence; storage feature behavior is NOT verified."
  log "Provision authority reference: ${PROVISION_AUTHORIZATION}"
}

# A standalone provision must never take an already-running app offline and
# report success without restoring it. Coordinated updates use `all`, which
# quiesces and then runs the complete deploy path.
require_application_stopped() {
  local running
  running="$("${COMPOSE[@]}" ps -q backend frontend 2>/dev/null)" \
    || fail "Cannot determine whether application services are running; provisioning stopped."
  [[ -z "${running}" ]] \
    || fail "Application services are running; use the coordinated 'all' command or stop them under separate operator authority before provisioning."
}

# Deploy: backend + frontend, private health, deployment record
guard_update() {
  local existing volumes project_volumes names name persisted="no"
  existing="$("${COMPOSE[@]}" ps -a -q 2>/dev/null)" || fail "Recovery inventory failed; no state-changing operation is permitted."
  volumes="$(docker volume ls --format '{{.Name}}' 2>/dev/null)" || fail "Persistent-volume inventory failed; no state-changing operation is permitted."
  project_volumes="$(docker volume ls --filter label=com.docker.compose.project=codemastery --format '{{.Name}}' 2>/dev/null)" \
    || fail "Project-volume inventory failed; no state-changing operation is permitted."
  [[ -z "${project_volumes}" ]] || persisted="yes"
  names="$(printf '%s' "${COMPOSE_MODEL}" | python3 -c 'import json,sys; print("\n".join(v["name"] for v in json.load(sys.stdin).get("volumes",{}).values()))')" \
    || fail "Cannot determine persistent-state inventory; no changes permitted."
  while IFS= read -r name; do
    [[ -n "${name}" ]] || continue
    if printf '%s\n' "${volumes}" | grep -Fxq -- "${name}"; then persisted="yes"; fi
  done <<<"${names}"
  if [[ -n "${existing}" || "${persisted}" == "yes" ]]; then
    [[ "${RECOVERY_VERIFIED}" == "yes" ]] \
      || fail "Existing containers or persistent state found. No verified recovery artifact attested; no changes permitted (P9)."
    [[ "${COMPATIBILITY_VERIFIED}" == "yes" && "${PERSISTENT_STATE_PRESERVED}" == "yes" && -n "${UPDATE_AUTHORIZATION}" ]] \
      || fail "Update requires verified schema/image compatibility, preserved persistent state and an operator authorization reference (P9); no changes permitted."
    log "Update detected; operator attested compatibility, preserved state and checked recovery under referenced authority."
  else
    log "No existing project containers or declared persistent volumes found."
  fi
}

contain_failure() {
  local rc=$?
  trap - EXIT INT TERM
  if [[ "${RELEASE_COMPLETE}" != "yes" && "${CONTAIN_ON_FAILURE}" == "yes" ]]; then
    [[ "${rc}" -ne 0 ]] || rc=1
    log "Release stopped. Containing application services; preserved data requires explicit operator recovery."
    if ! "${COMPOSE[@]}" stop --timeout "${WAIT_TIMEOUT}" frontend backend >/dev/null 2>&1; then
      printf 'ERROR: Application stop failed; ingress containment is NOT verified. Operator intervention required.\n' >&2
    fi
  fi
  if [[ -n "${STAGED_FRONTEND_CID}" ]]; then
    [[ "${STAGED_FRONTEND_CID}" =~ ^[0-9a-f]{64}$ ]] \
      && timeout --signal=KILL "${WAIT_TIMEOUT}" docker stop --timeout "${WAIT_TIMEOUT}" "${STAGED_FRONTEND_CID}" >/dev/null 2>&1 \
      || { printf 'ERROR: Private frontend probe cleanup failed; inspect the one-off container.\n' >&2; rc=1; }
  fi
  exit "${rc}"
}

quiesce_application() {
  assert_release_configuration
  # Mark before stop: a partial stop/error must still trigger containment.
  CONTAIN_ON_FAILURE="yes"
  "${COMPOSE[@]}" stop --timeout "${WAIT_TIMEOUT}" frontend backend >/dev/null 2>&1 \
    || fail "Could not close ingress and stop the existing backend; release stopped."
}

verify_private_dependencies() {
  local cid state
  verify_container_images postgres minio minio-init
  cid="$("${COMPOSE[@]}" ps -a -q postgres)"
  state="$(docker inspect --format '{{.State.Health.Status}}' "${cid}" 2>/dev/null)" || fail "Cannot inspect PostgreSQL health."
  [[ "${state}" == "healthy" ]] || fail "PostgreSQL is not healthy."
  cid="$("${COMPOSE[@]}" ps -a -q minio)"
  state="$(docker inspect --format '{{.State.Running}}' "${cid}" 2>/dev/null)" || fail "Cannot inspect MinIO state."
  [[ "${state}" == "true" ]] || fail "MinIO is not running."
  cid="$("${COMPOSE[@]}" ps -a -q minio-init)"
  state="$(docker inspect --format '{{.State.Status}}:{{.State.ExitCode}}' "${cid}" 2>/dev/null)" || fail "Cannot inspect initializer completion."
  [[ "${state}" == "exited:0" ]] || fail "MinIO initializer has not completed successfully."
}

stage_frontend() {
  # Compose run omits service ports by default. Never add --service-ports/-p.
  # Exercise the approved image, certificate mount and TLS healthcheck privately.
  STAGED_FRONTEND_CID="$("${COMPOSE[@]}" run -d --rm --no-deps --pull never frontend 2>/dev/null)" \
    || fail "Could not stage the frontend without published ports."
  [[ -n "${STAGED_FRONTEND_CID}" && "${STAGED_FRONTEND_CID}" != *$'\n'* ]] || fail "Invalid private frontend container identity."
  verify_service_container frontend "${STAGED_FRONTEND_CID}"
  docker exec "${STAGED_FRONTEND_CID}" nginx -t >/dev/null 2>&1 \
    || fail "Frontend Nginx configuration check failed; ingress remains closed."
  docker inspect --format '{{json .NetworkSettings.Ports}}' "${STAGED_FRONTEND_CID}" 2>/dev/null | python3 -c \
    'import json,sys; sys.exit(1 if any((json.load(sys.stdin) or {}).values()) else 0)' \
    || fail "Private frontend probe unexpectedly publishes a host port."
  local deadline state
  deadline=$(( SECONDS + WAIT_TIMEOUT ))
  while :; do
    state="$(docker inspect --format '{{.State.Running}}:{{.State.Health.Status}}' "${STAGED_FRONTEND_CID}" 2>/dev/null)" \
      || fail "Private frontend exited or has no qualified TLS healthcheck."
    [[ "${state}" != "true:healthy" ]] || break
    [[ "${state}" == "true:starting" && SECONDS -lt deadline ]] || fail "Private frontend TLS healthcheck failed."
    sleep "${POLL_INTERVAL_SECONDS}"
  done
  verify_private_tls
}

read -r -d '' PY_TLS_CHECK <<'PYEOF' || true
import http.client, ipaddress, os, socket, ssl, sys, time, urllib.parse
try:
    origin, address, port, seconds = sys.argv[1:]
    host = urllib.parse.urlsplit(origin).hostname
    if not ipaddress.ip_address(address).is_private:
        raise ValueError()
    deadline = time.monotonic() + int(seconds)
    context = ssl.create_default_context(cafile=os.environ.get('SSL_CERT_FILE') or None,
                                         capath=os.environ.get('SSL_CERT_DIR') or None)
    context.minimum_version = ssl.TLSVersion.TLSv1_2
    context.maximum_version = ssl.TLSVersion.TLSv1_3
    with socket.create_connection((address, int(port)), timeout=int(seconds)) as raw:
        raw.settimeout(max(0.001, deadline - time.monotonic()))
        with context.wrap_socket(raw, server_hostname=host) as connection:
            # The real handshake verifies trust, expiry, hostname and possession
            # of the matching private key. Never substitute Docker health alone.
            connection.settimeout(max(0.001, deadline - time.monotonic()))
            connection.sendall(('GET / HTTP/1.1\r\nHost: ' + host + '\r\nConnection: close\r\n\r\n').encode('ascii'))
            response = http.client.HTTPResponse(connection)
            response.begin()
            if response.status != 200 or time.monotonic() > deadline:
                raise ValueError()
except Exception:
    sys.exit(1)  # Never print response bodies, TLS material or raw diagnostics.
PYEOF

verify_private_tls() {
  local address
  address="$({ printf '{"model":%s,"networks":' "${COMPOSE_MODEL}"; docker inspect --format '{{json .NetworkSettings.Networks}}' "${STAGED_FRONTEND_CID}" 2>/dev/null; printf '}'; } | python3 -c '
import ipaddress,json,sys
try:
    data=json.load(sys.stdin)
    model=data["model"]
    approved={model["networks"][n]["name"] for n in model["services"]["frontend"]["networks"]}
    if set(data["networks"]) != approved:
        raise ValueError()
    addresses=[v.get("IPAddress") or v.get("GlobalIPv6Address") for k,v in sorted(data["networks"].items())]
    if not addresses or any(not ipaddress.ip_address(a).is_private for a in addresses):
        raise ValueError()
    print(addresses[0])
except Exception:
    sys.exit(1)
')" || fail "Cannot identify the private frontend address on the approved Compose network."
  python3 -c "${PY_TLS_CHECK}" "${ORIGIN}" "${address}" 8080 "${WAIT_TIMEOUT}" \
    || fail "Private HTTPS certificate/hostname/expiry or HTTP readiness verification failed; ingress remains closed."
  log "Private HTTPS handshake and ingress response verified."
}

wait_backend_health() {
  local deadline out code remaining
  deadline=$(( SECONDS + WAIT_TIMEOUT ))
  log "Waiting for the backend to answer privately (up to ${WAIT_TIMEOUT}s)."
  while :; do
    remaining=$(( deadline - SECONDS ))
    (( remaining > 0 )) || fail "Backend readiness deadline expired; release stopped."
    out="$(timeout "${remaining}" docker exec "${STAGED_FRONTEND_CID}" wget -S -O - http://backend:8080/actuator/health 2>&1 </dev/null || true)"
    code="$(printf '%s\n' "${out}" | grep -Eo 'HTTP/[0-9.]+ [0-9]{3}' | tail -1 | awk '{print $2}' || true)"
    case "${code}" in
      (200)
        if [[ "${out}" == *'"status":"UP"'* ]]; then
          HEALTH_RESULT="UP (HTTP 200, non-detailed)"; break
        fi ;;
      (401|403)
        fail "Backend health is protected (HTTP ${code}); UP is not verified, so ingress remains closed." ;;
    esac
    if (( SECONDS >= deadline )); then
      fail "Backend did not answer within ${WAIT_TIMEOUT}s. Migration/config failure stops the release; nothing is retried or reset."
    fi
    remaining=$(( deadline - SECONDS ))
    if (( remaining < POLL_INTERVAL_SECONDS )); then sleep "${remaining}"; else sleep "${POLL_INTERVAL_SECONDS}"; fi
  done
  log "SERVICE_HEALTH: ${HEALTH_RESULT}"
}

staged_frontend_present() {
  local inventory
  # A successful empty inventory proves absence; a failed inspect does not.
  # Full IDs avoid prefix matches, and unexpected/multiple results fail closed.
  inventory="$(timeout --signal=KILL "$1" docker container ls --all --quiet --no-trunc \
    --filter "id=${STAGED_FRONTEND_CID}" 2>/dev/null)" \
    || fail "Cannot verify private frontend probe inventory; ingress remains closed."
  [[ -n "${inventory}" ]] || return 1
  [[ "${inventory}" == "${STAGED_FRONTEND_CID}" ]] \
    || fail "Private frontend probe inventory is inconsistent; ingress remains closed."
}

remove_staged_frontend() {
  [[ "${STAGED_FRONTEND_CID}" =~ ^[0-9a-f]{64}$ ]] \
    || fail "Private frontend probe identity is not a full container ID."
  # Each phase is bounded by the existing operator-supplied readiness timeout:
  # initial inventory, stop request, then the entire auto-removal polling window.
  if staged_frontend_present "${WAIT_TIMEOUT}"; then
    timeout --signal=KILL "${WAIT_TIMEOUT}" docker stop --timeout "${WAIT_TIMEOUT}" "${STAGED_FRONTEND_CID}" >/dev/null 2>&1 \
      || fail "Could not stop private frontend probe; ingress remains closed."
    local deadline=$(( SECONDS + WAIT_TIMEOUT )) remaining
    while :; do
      remaining=$(( deadline - SECONDS ))
      (( remaining > 0 )) \
        || fail "Private frontend probe was not removed; ingress remains closed."
      if ! staged_frontend_present "${remaining}"; then
        break
      fi
      sleep 1
    done
  fi
  STAGED_FRONTEND_CID=""
}

run_deploy() {
  verify_images
  verify_private_dependencies
  # Deploy may be invoked independently of provision. Do not silently assume the
  # existing grants/defaults/history permissions already satisfy this release.
  provision_db_roles
  assert_release_configuration
  log "Starting the backend privately; public frontend remains stopped."
  "${COMPOSE[@]}" up -d --no-build --pull never --no-deps backend \
    || fail "Private backend startup failed."
  verify_container_images backend
  verify_published_ports postgres minio minio-init backend
  stage_frontend
  wait_backend_health
  local migration_state
  migration_state="$(psql_admin 2>/dev/null <<'SQL'
SELECT CASE WHEN count(*) FILTER (WHERE success)>0 AND count(*) FILTER (WHERE NOT success)=0
 THEN 'ok' ELSE 'invalid' END FROM public.flyway_schema_history;
SQL
  )" || fail "Flyway history could not be verified; ingress remains closed."
  [[ "${migration_state}" == "ok" ]] || fail "Flyway history does not establish successful migrations."
  harden_after_migration
  verify_role_separation
  verify_images
  assert_release_configuration
  verify_private_tls
  log "Removing private frontend probe before public ingress."
  remove_staged_frontend
  log "Private release checks passed. Starting the sole HTTPS ingress."
  "${COMPOSE[@]}" up -d --no-build --pull never --no-deps --wait --wait-timeout "${WAIT_TIMEOUT}" frontend \
    || fail "Frontend startup failed; stopping application services."
  verify_container_images frontend backend
  verify_published_ports postgres minio minio-init backend frontend
  print_record
}

print_record() {
  local dirty="clean" rev="unavailable" flyway entry identity_kind="image config digests"
  [[ "${IMAGE_STORE}" != "containerd" ]] || identity_kind="OCI index/manifest digests"
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
  echo "Host platform       : ${HOST_PLATFORM}"
  echo "Docker Engine       : ${DOCKER_SERVER_VERSION}"
  echo "Docker Compose      : ${DOCKER_COMPOSE_VERSION}"
  echo "Image store         : ${IMAGE_STORE} (image IDs are ${identity_kind})"
  echo "Approved origin     : ${ORIGIN}"
  echo "Script checkout rev : ${rev} (${dirty})"
  echo "Image source rev    : ${SOURCE_REVISION} (declared by operator)"
  echo "Initializer qualification: ${INITIALIZER_QUALIFICATION:-not applicable} (operator-supplied reference)"
  echo "Deployment authority : ${DEPLOY_AUTHORIZATION:-not applicable} (operator-supplied reference)"
  echo "Public exposure authority: ${EXPOSURE_AUTHORIZATION:-not applicable} (operator-supplied reference)"
  echo "Packet-filter evidence: ${PACKET_FILTER_VERIFICATION:-not applicable} (operator-supplied reference; not verified by this script)"
  echo "Provision authority  : ${PROVISION_AUTHORIZATION:-not applicable} (operator-supplied reference)"
  echo "Update authority    : ${UPDATE_AUTHORIZATION:-not an update} (declared by operator)"
  echo "Recovery preconditions: artifact=${RECOVERY_VERIFIED}, compatibility=${COMPATIBILITY_VERIFIED}, state=${PERSISTENT_STATE_PRESERVED} (operator attestations)"
  [[ -z "${ARCHIVE_SHA256}" ]] || echo "Archive SHA-256     : ${ARCHIVE_SHA256}"
  echo "Flyway success|fail : ${flyway}"
  echo "DB role separation  : ${DB_SEPARATION_RESULT}"
  echo "DB runtime grants   : ${DB_GRANTS_RESULT}"
  echo "Published ports     : ${PUBLISHED_PORTS_RESULT}"
  echo "--- verified image identities ---"
  for entry in "${EXPECT_IMAGES[@]}"; do echo "${entry}"; done
  echo "--- images in use ---"
  "${COMPOSE[@]}" images 2>/dev/null || echo "(unavailable)"
  echo "--- containers ---"
  "${COMPOSE[@]}" ps -a 2>/dev/null || echo "(unavailable)"
  echo "--- results (kept separate) ---"
  echo "SCRIPT_RESULT       : steps completed"
  echo "SERVICE_HEALTH      : ${HEALTH_RESULT} (health alone proves no feature)"
  echo "FEATURE_BEHAVIOR    : NOT_VERIFIED (Auth, catalogue, TLS from outside: QA01/QA07/DP03)"
  echo "External access     : NOT_VERIFIED (requires independent outside-network check, DP03)"
  echo "Host packet filtering: NOT_VERIFIED by this script (operator evidence above; DP03 outside scan)"
  echo "================================================================"
}

# ----------------------------------------------------------------------------
main() {
  parse_args "$@"
  require_inputs
  run_preflight
  [[ "${SUBCOMMAND}" != "preflight" ]] || return 0
  verify_image_coverage
  case "${SUBCOMMAND}" in (load|deploy|all) verify_archive ;; esac
  assert_release_configuration
  # This read-only guard precedes load, container changes AND role/ACL changes.
  guard_update
  trap contain_failure EXIT
  trap 'exit 130' INT
  trap 'exit 143' TERM
  case "${SUBCOMMAND}" in
    (load) run_load ;;
    (provision) verify_images; require_application_stopped; run_provision ;;
    (deploy) verify_images; quiesce_application; run_deploy ;;
    (all) run_load; quiesce_application; run_provision; run_deploy ;;
  esac
  RELEASE_COMPLETE="yes"
}

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
  main "$@"
fi
