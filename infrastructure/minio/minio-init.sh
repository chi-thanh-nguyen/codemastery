#!/bin/bash
# CodeMastery - infrastructure/minio/minio-init.sh   (DP01 storage initializer)
#
# One-shot executable of the minio-init release image. It provisions the
# application bucket and the separate bucket-scoped application identity on the
# private MinIO server at http://minio:9000, the endpoint fixed by the Compose
# contract. Only the six environment variables the VM contract admits are read:
#   MINIO_ROOT_USER, MINIO_ROOT_PASSWORD   administrator (provisioning only)
#   STORAGE_ACCESS_KEY, STORAGE_SECRET_KEY  application identity
#   STORAGE_BUCKET, STORAGE_REGION          shared with the backend
#
# Safety rules:
#   - existing state is inspected before any change and must be exactly the
#     state this initializer creates; anything else stops the run unchanged
#     (no policy overwrite, detach, group removal, re-enable or secret reset);
#   - only absent resources are created, so a repeat run on initialized state
#     makes no change;
#   - the policy is compared with the server's stored document, and the
#     application credentials are exercised, not only named;
#   - secrets never reach argv, child environments or output: mc reads them
#     from a private temporary config directory or stdin, and mc output (which
#     can echo a secret) is captured and never printed.
set -Eeuo pipefail
umask 077

readonly ENDPOINT="http://minio:9000"
readonly READY_TIMEOUT_SECONDS=120
readonly POLICY_NAME="codemastery-local-storage"
# Listing this prefix needs s3:ListBucket only and creates no object.
readonly PROBE_PREFIX=".codemastery-minio-init-probe/"

changes=0
work_dir=""
MC_OUT=""
MC_RC=0

log() { printf 'minio-init: %s\n' "$1"; }

fail() {
  MC_OUT=""
  printf 'minio-init: ERROR: %s\n' "$1" >&2
  exit 1
}

cleanup() { [[ -n "${work_dir}" ]] && rm -rf "${work_dir}"; }
trap cleanup EXIT

# ---------------------------------------------------------------- inputs

require_value() {  # name
  local value="${!1:-}"
  [[ -n "${value}" ]] || fail "$1 must be set."
  [[ "${value}" != *[[:cntrl:]]* ]] || fail "$1 must not contain control characters."
}

for name in MINIO_ROOT_USER MINIO_ROOT_PASSWORD STORAGE_ACCESS_KEY \
            STORAGE_SECRET_KEY STORAGE_BUCKET STORAGE_REGION; do
  require_value "${name}"
done
root_user="${MINIO_ROOT_USER}"
root_password="${MINIO_ROOT_PASSWORD}"
app_key="${STORAGE_ACCESS_KEY}"
app_secret="${STORAGE_SECRET_KEY}"
bucket="${STORAGE_BUCKET}"
region="${STORAGE_REGION}"
# mc and its helpers never need the raw credentials in their environment.
export -n MINIO_ROOT_USER MINIO_ROOT_PASSWORD STORAGE_ACCESS_KEY STORAGE_SECRET_KEY

[[ "${app_key}" != "${root_user}" ]] \
  || fail "Application storage identity must differ from MinIO root identity."
[[ "${app_secret}" != "${root_password}" ]] \
  || fail "STORAGE_SECRET_KEY must differ from MINIO_ROOT_PASSWORD."
# MinIO accepts 3-20 character user access keys. This character set needs no
# JSON escaping, so the identity can be matched exactly in server responses.
[[ "${app_key}" =~ ^[A-Za-z0-9._@+=-]{3,20}$ ]] \
  || fail "STORAGE_ACCESS_KEY must be 3-20 characters from A-Z a-z 0-9 . _ @ + = -."
# S3 bucket naming; it also keeps the name safe to embed in the policy JSON.
if [[ ! "${bucket}" =~ ^[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]$ ]] \
   || [[ "${bucket}" == *..* || "${bucket}" == *.-* || "${bucket}" == *-.* ]] \
   || [[ "${bucket}" =~ ^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  fail "Use a valid S3 bucket name for STORAGE_BUCKET."
fi
[[ "${region}" =~ ^[A-Za-z0-9_-]{1,64}$ ]] \
  || fail "STORAGE_REGION must contain only letters, digits, _ and -."

# The approved bucket-scoped policy in MinIO's compact form. Existing policies
# must equal it exactly, apart from the order of set-valued arrays.
readonly POLICY_JSON='{"Version":"2012-10-17","Statement":[{"Effect":"Allow","Action":["s3:GetBucketLocation","s3:ListBucket"],"Resource":["arn:aws:s3:::'"${bucket}"'"]},{"Effect":"Allow","Action":["s3:DeleteObject","s3:GetObject","s3:PutObject"],"Resource":["arn:aws:s3:::'"${bucket}"'/*"]}]}'

# ---------------------------------------------------------------- mc access

work_dir="$(mktemp -d)"
export MC_CONFIG_DIR="${work_dir}/mc"
mkdir "${MC_CONFIG_DIR}"

json_escape() {  # value -> REPLY (control characters were rejected above)
  local s="$1"
  s="${s//\\/\\\\}"
  REPLY="${s//\"/\\\"}"
}

# Aliases live only in this private config file, never in argv or environment.
write_aliases() {
  local admin_user admin_secret user secret
  json_escape "${root_user}"; admin_user="${REPLY}"
  json_escape "${root_password}"; admin_secret="${REPLY}"
  json_escape "${app_key}"; user="${REPLY}"
  json_escape "${app_secret}"; secret="${REPLY}"
  printf '{"version":"10","aliases":{"admin":{"url":"%s","accessKey":"%s","secretKey":"%s","api":"S3v4","path":"on"},"app":{"url":"%s","accessKey":"%s","secretKey":"%s","api":"S3v4","path":"on"}}}\n' \
    "${ENDPOINT}" "${admin_user}" "${admin_secret}" "${ENDPOINT}" "${user}" "${secret}" \
    > "${MC_CONFIG_DIR}/config.json"
}
write_aliases

# Runs mc --json; output stays in MC_OUT and is never printed. Some mc
# commands exit 0 after reporting an error, so success needs both checks.
mc_json() {
  MC_RC=0
  MC_OUT="$(mc --json "$@" 2>&1 </dev/null)" || MC_RC=$?
  [[ ${MC_RC} -eq 0 && "${MC_OUT}" != *'"status":"error"'* ]]
}

# MinIO stores Action/Resource values as sets and returns them in varying
# order. Sorting their members makes documents comparable; everything else,
# including statement order, must match as returned.
canonical_sets() {  # json -> REPLY
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
        out+="${before}${delim}${inner}]"
        ;;
      *) out+="${before}${delim}" ;;
    esac
  done
  REPLY="${out}${s}"
}

error_code() {  # -> REPLY: server/client error code from MC_OUT, or "unknown"
  REPLY=unknown
  if [[ "${MC_OUT}" =~ \"Code\":\"([A-Za-z0-9]{1,64})\" ]]; then
    REPLY="${BASH_REMATCH[1]}"
  fi
}

fail_mc() {  # message
  error_code
  fail "$1 (MinIO error code: ${REPLY})."
}

fail_if_admin_rejected() {
  error_code
  case "${REPLY}" in
    SignatureDoesNotMatch|InvalidAccessKeyId|AccessDenied)
      fail "MinIO rejected the administrator credentials (MINIO_ROOT_USER/MINIO_ROOT_PASSWORD)." ;;
  esac
}

# ---------------------------------------------------------------- inspection

user_state=""     # absent | unbound | bound
policy_state=""   # absent | approved
bucket_state=""   # absent | private

inspect_user() {
  if ! mc_json admin user info admin "${app_key}"; then
    fail_if_admin_rejected
    error_code
    [[ "${REPLY}" == XMinioAdminNoSuchUser ]] || fail_mc "Could not inspect the application identity"
    user_state=absent
    return
  fi
  local prefix='{"status":"success","accessKey":"'"${app_key}"'",'
  case "${MC_OUT}" in
    "${prefix}"'"userStatus":"enabled"}') user_state=unbound ;;
    "${prefix}"'"policyName":"'"${POLICY_NAME}"'","userStatus":"enabled"}') user_state=bound ;;
    *'"userStatus":"disabled"'*)
      fail "The existing application identity is disabled; reconcile it before startup." ;;
    *'"memberOf":'*)
      fail "Application identity must not inherit group policies." ;;
    *'"policyName":'*)
      fail "Unexpected application policy bindings; reconcile this identity before startup." ;;
    *) fail "Unexpected application identity state; reconcile it before startup." ;;
  esac
}

inspect_policy() {
  if ! mc_json admin policy info admin "${POLICY_NAME}"; then
    error_code
    [[ "${REPLY}" == XMinioAdminNoSuchPolicy ]] || fail_mc "Could not inspect the storage policy"
    policy_state=absent
    return
  fi
  local expected actual
  canonical_sets "${POLICY_JSON}"; expected="${REPLY}"
  canonical_sets "${MC_OUT}"; actual="${REPLY}"
  expected='{"status":"success","policy":"'"${POLICY_NAME}"'","policyInfo":{"PolicyName":"'"${POLICY_NAME}"'","Policy":'"${expected}"
  # The document must end where the expected one ends.
  if [[ "${actual}" == "${expected}"[,\}]* ]]; then
    policy_state=approved
  else
    fail "Existing policy ${POLICY_NAME} differs from the approved bucket-scoped policy; it was not changed."
  fi
}

inspect_bindings() {  # expected: none | app
  mc_json admin policy entities admin --policy "${POLICY_NAME}" \
    || fail_mc "Could not inspect the storage policy bindings"
  local head='{"status":"success","result":{"timestamp":"' rest
  rest="${MC_OUT#"${head}"}"
  [[ "${rest}" != "${MC_OUT}" && "${rest}" == *'"'* ]] \
    || fail "Unexpected policy binding response from MinIO."
  rest="${rest#*'"'}"   # drop the timestamp value, which contains no quote
  case "$1:${rest}" in
    'none:}}') return ;;
    'app:,"policyMappings":[{"policy":"'"${POLICY_NAME}"'","users":["'"${app_key}"'"],"groups":null}]}}') return ;;
  esac
  fail "Policy ${POLICY_NAME} is bound to an unexpected user or group; it must belong to the application identity only."
}

inspect_bucket() {
  if ! mc_json anonymous get "admin/${bucket}"; then
    fail_if_admin_rejected
    error_code
    [[ "${REPLY}" == NoSuchBucket ]] || fail_mc "Could not inspect bucket ${bucket}"
    bucket_state=absent
    return
  fi
  [[ "${MC_OUT}" == '{"operation":"get","status":"success","bucket":"admin/'"${bucket}"'","permission":"private"}' ]] \
    || fail "Bucket ${bucket} allows anonymous access; it was not changed."
  bucket_state=private
}

inspect_service_accounts() {
  mc_json admin user svcacct ls admin "${app_key}" \
    || fail_mc "Could not inspect application service accounts"
  [[ -z "${MC_OUT}" ]] \
    || fail "The application identity has service accounts that would share its access; reconcile them before startup."
}

# Authenticates as the application without needing any S3 permission. A
# rejected signature means the existing identity has another secret.
verify_app_credentials() {
  if mc_json admin user list app; then
    fail "The application identity has administrative privileges."
  fi
  error_code
  case "${REPLY}" in
    AccessDenied) ;;
    SignatureDoesNotMatch|InvalidAccessKeyId)
      fail "The existing application identity does not accept STORAGE_SECRET_KEY; its secret was not reset." ;;
    *) fail_mc "Could not verify the application credentials" ;;
  esac
}

verify_app_bucket_access() {
  mc_json ls "app/${bucket}/${PROBE_PREFIX}" \
    || fail_mc "The application identity cannot list bucket ${bucket}"
}

# ---------------------------------------------------------------- run

log "Waiting for MinIO at ${ENDPOINT} (up to ${READY_TIMEOUT_SECONDS}s)."
timeout "${READY_TIMEOUT_SECONDS}" mc ready admin >/dev/null 2>&1 </dev/null \
  || fail "MinIO did not become ready within ${READY_TIMEOUT_SECONDS} seconds."

log "Inspecting existing storage state."
inspect_user
inspect_policy
if [[ "${user_state}" == bound && "${policy_state}" == absent ]]; then
  fail "Application identity references a missing policy; reconcile it before startup."
fi
if [[ "${policy_state}" == approved ]]; then
  if [[ "${user_state}" == bound ]]; then inspect_bindings app; else inspect_bindings none; fi
fi
inspect_bucket
if [[ "${user_state}" != absent ]]; then
  inspect_service_accounts
  verify_app_credentials
fi

if [[ "${bucket_state}" == absent ]]; then
  mc_json mb --region "${region}" "admin/${bucket}" || fail_mc "Could not create bucket ${bucket}"
  log "Created bucket ${bucket}."
  changes=$((changes + 1))
fi
if [[ "${policy_state}" == absent ]]; then
  printf '%s\n' "${POLICY_JSON}" > "${work_dir}/policy.json"
  mc_json admin policy create admin "${POLICY_NAME}" "${work_dir}/policy.json" \
    || fail_mc "Could not create policy ${POLICY_NAME}"
  log "Created policy ${POLICY_NAME}."
  changes=$((changes + 1))
fi
if [[ "${user_state}" == absent ]]; then
  # The secret is read from stdin; the JSON reply echoes it and is discarded.
  MC_RC=0
  MC_OUT="$(printf '%s\n' "${app_secret}" | mc --json admin user add admin "${app_key}" 2>&1)" || MC_RC=$?
  [[ ${MC_RC} -eq 0 && "${MC_OUT}" != *'"status":"error"'* ]] \
    || fail_mc "Could not create the application identity"
  MC_OUT=""
  log "Created application identity."
  changes=$((changes + 1))
fi
if [[ "${user_state}" != bound ]]; then
  mc_json admin policy attach admin "${POLICY_NAME}" --user "${app_key}" \
    || fail_mc "Could not attach policy ${POLICY_NAME}"
  log "Attached policy ${POLICY_NAME} to the application identity."
  changes=$((changes + 1))
fi

log "Verifying the resulting state."
inspect_user
[[ "${user_state}" == bound ]] || fail "The application identity is not bound to ${POLICY_NAME}."
inspect_policy
inspect_bindings app
inspect_bucket
[[ "${bucket_state}" == private ]] || fail "Bucket ${bucket} is missing after initialization."
inspect_service_accounts
verify_app_credentials
verify_app_bucket_access

log "MinIO bucket and application identity initialized (changes: ${changes})."
