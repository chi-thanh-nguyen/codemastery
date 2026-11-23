#!/usr/bin/env bash
# CodeMastery - infrastructure/vm/cd/vm-transport.sh   (ADR017 SSH transport)
#
# The one SSH client path to the VM, for GitHub Actions and for the local operator:
#
#   vm-transport.sh [--host H] [--user U] [--identity KEY] [--known-hosts FILE] [--port P] COMMAND
#     check                      TCP reachability, pinned host key, authentication, ops status
#     upload FILE [NAME]         stream FILE to `codemastery-ops receive NAME SHA256 SIZE`
#     ops OPERATION [ARGS...]    run `codemastery-ops OPERATION ARGS...` (stdout is its output)
#
# Defaults: host 20.205.56.236, user cmdeploy (the restricted deployment identity, whose
# key is forced to codemastery-ssh-gate), known hosts = the pin committed beside this
# script. Any other user is the operator and runs `sudo -n codemastery-ops`.
#
# Host authenticity: StrictHostKeyChecking=yes against ONLY the pinned file (no global or
# user known_hosts, ED25519 only, no host-key updates). A changed host key fails closed.
# No agent or port forwarding, batch mode (never prompts). Arguments are restricted to a
# character set that needs no shell quoting, on both sides.
set -Eeuo pipefail
umask 077
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
HOST="${VM_SSH_HOST:-20.205.56.236}"
USER_NAME="${VM_SSH_USER:-cmdeploy}"
IDENTITY="${VM_SSH_IDENTITY:-}"
KNOWN_HOSTS="${VM_SSH_KNOWN_HOSTS:-${HERE}/known_hosts}"
PORT="${VM_SSH_PORT:-22}"
fail() { printf 'TRANSPORT FAILED: %s\n' "$*" >&2; exit 2; }

while [[ $# -gt 0 ]]; do
  case "$1" in
    (--host) HOST="${2:?}"; shift 2 ;;
    (--user) USER_NAME="${2:?}"; shift 2 ;;
    (--identity) IDENTITY="${2:?}"; shift 2 ;;
    (--known-hosts) KNOWN_HOSTS="${2:?}"; shift 2 ;;
    (--port) PORT="${2:?}"; shift 2 ;;
    (*) break ;;
  esac
done
[[ $# -ge 1 ]] || { sed -n '2,20p' "$0" >&2; exit 2; }
command="$1"; shift

[[ "${HOST}" =~ ^[A-Za-z0-9.-]{1,253}$ ]] || fail "malformed host."
[[ "${USER_NAME}" =~ ^[a-z_][a-z0-9_-]{0,31}$ ]] || fail "malformed user."
[[ "${PORT}" =~ ^[0-9]{1,5}$ ]] || fail "malformed port."
[[ -n "${IDENTITY}" && -f "${IDENTITY}" ]] || fail "an SSH identity file is required (--identity or VM_SSH_IDENTITY)."
[[ -f "${KNOWN_HOSTS}" ]] || fail "the pinned known_hosts file is missing."
perm="$( (stat -c '%a' "${IDENTITY}" 2>/dev/null || stat -f '%Lp' "${IDENTITY}") )"
[[ "${perm}" == 600 || "${perm}" == 400 ]] || fail "the identity file must be mode 0600 or 0400."
grep -Eq "^${HOST//./\\.} ssh-ed25519 " "${KNOWN_HOSTS}" || [[ "${PORT}" != 22 ]] \
  || fail "the pinned known_hosts file has no ED25519 key for ${HOST}."

SSH=(ssh -p "${PORT}" -i "${IDENTITY}"
     -o BatchMode=yes -o IdentitiesOnly=yes -o IdentityAgent=none
     -o StrictHostKeyChecking=yes -o UserKnownHostsFile="${KNOWN_HOSTS}" -o GlobalKnownHostsFile=/dev/null
     -o HostKeyAlgorithms=ssh-ed25519 -o UpdateHostKeys=no -o CheckHostIP=no -o VerifyHostKeyDNS=no
     -o ConnectTimeout=15 -o ServerAliveInterval=15 -o ServerAliveCountMax=4
     -o ForwardAgent=no -o ForwardX11=no -o ClearAllForwardings=yes -o RequestTTY=no -o LogLevel=ERROR
     "${USER_NAME}@${HOST}")

check_words() {
  local w
  for w in "$@"; do
    [[ "${w}" =~ ^[A-Za-z0-9._:/=@+_-]+$ && ${#w} -le 4096 ]] || fail "argument contains unsupported characters."
  done
}

remote_ops() {
  check_words "$@"
  if [[ "${USER_NAME}" == cmdeploy ]]; then
    local token
    token="$(python3 -I "${HERE}/../host/authorize.py" request "$@")" || fail "GitHub command authorization unavailable."
    # The token is a single stdin header, never an SSH argument, environment value
    # or file. For receive, the exact size/hash-bound upload follows it.
    { printf '%s\n' "${token}"; cat; } | "${SSH[@]}" -- "$*"
  else
    "${SSH[@]}" -- sudo -n /usr/local/sbin/codemastery-ops "$@"
  fi
}

sha256_of() { if command -v sha256sum >/dev/null; then sha256sum "$1" | awk '{print $1}'; else shasum -a 256 "$1" | awk '{print $1}'; fi; }
size_of() { stat -c '%s' "$1" 2>/dev/null || stat -f '%z' "$1"; }

case "${command}" in
  (check)
    if command -v nc >/dev/null 2>&1; then
      nc -z -w 10 "${HOST}" "${PORT}" >/dev/null 2>&1 \
        || fail "TCP ${HOST}:${PORT} is unreachable from here (packet filtering, e.g. the NSG source restriction)."
    fi
    remote_ops status >/dev/null || fail "SSH authentication or the remote status failed (host key, key or sudo)."
    echo "SSH OK: ${USER_NAME}@${HOST}:${PORT}, pinned ED25519 host key, remote ops answered."
    ;;
  (upload)
    file="${1:?upload FILE [NAME]}"; name="${2:-$(basename "${file}")}"
    [[ -f "${file}" ]] || fail "no such file."
    remote_ops receive "${name}" "$(sha256_of "${file}")" "$(size_of "${file}")" < "${file}"
    ;;
  (ops)
    remote_ops "$@"
    ;;
  (*) fail "unknown command ${command}." ;;
esac
