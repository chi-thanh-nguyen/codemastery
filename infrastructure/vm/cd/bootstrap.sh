#!/usr/bin/env bash
# CodeMastery - infrastructure/vm/cd/bootstrap.sh   (ADR017 one-time VM setup; bash 3.2 compatible)
#
# Run ONCE by the Leader from a clean checkout of main, over the Leader's own operator key:
#
#   bootstrap.sh --identity ~/.ssh/vm-codemastery_key.pem --check
#       read-only: reports which ADR017 items exist on the VM; changes nothing
#   bootstrap.sh --identity ~/.ssh/vm-codemastery_key.pem --apply \
#       --deploy-public-key cm-deploy.pub --recipient-cert ~/CodeMasteryRecovery/keys/recipient.crt \
#       --wait-timeout SECONDS
#
# --apply creates the ADR009 section 9.3/9.4 locations, installs the deployment tooling of
# this checkout (git tree verified on the VM), /usr/local/sbin/codemastery-ops and the
# forced-command gate, the restricted user `cmdeploy` (key-only, forced command, sudo for
# codemastery-ops alone, validated with visudo), /etc/codemastery/deploy/ops.conf, the
# recovery recipient certificate, a producer signing key generated ON the VM (only its
# public key and fingerprint are printed, for `recovery.py trust-producer`) and the systemd
# units, which stay DISABLED (`codemastery-ops timers enable` after qualification).
#
# It never touches containers, volumes, the runtime env file, TLS material, the NSG, the
# firewall or SSH daemon settings, and never reads a secret. Re-running is idempotent; an
# existing producer key, user or setting is kept, never replaced.
set -Eeuo pipefail
umask 077
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO="$(cd "${HERE}/../../.." && pwd -P)"
fail() { printf 'BOOTSTRAP FAILED: %s\n' "$*" >&2; exit 2; }
MODE="" IDENTITY="${VM_SSH_IDENTITY:-}" HOST="${VM_SSH_HOST:-20.205.56.236}" OPERATOR="${VM_SSH_OPERATOR:-azureuser}"
DEPLOY_PUB="" RECIPIENT="" WAIT=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    (--check) MODE=check; shift ;;
    (--apply) MODE=apply; shift ;;
    (--identity) IDENTITY="${2:?}"; shift 2 ;;
    (--host) HOST="${2:?}"; shift 2 ;;
    (--operator) OPERATOR="${2:?}"; shift 2 ;;
    (--deploy-public-key) DEPLOY_PUB="${2:?}"; shift 2 ;;
    (--recipient-cert) RECIPIENT="${2:?}"; shift 2 ;;
    (--wait-timeout) WAIT="${2:?}"; shift 2 ;;
    (*) fail "unknown option $1" ;;
  esac
done
[[ -n "${MODE}" ]] || { sed -n '2,24p' "$0" >&2; exit 2; }
[[ -f "${IDENTITY}" ]] || fail "--identity (the operator's private key file) is required."
SSH=(ssh -i "${IDENTITY}" -o BatchMode=yes -o IdentitiesOnly=yes -o IdentityAgent=none -o StrictHostKeyChecking=yes
     -o UserKnownHostsFile="${HERE}/known_hosts" -o GlobalKnownHostsFile=/dev/null -o HostKeyAlgorithms=ssh-ed25519
     -o UpdateHostKeys=no -o ConnectTimeout=15 -o ForwardAgent=no -o ClearAllForwardings=yes -o LogLevel=ERROR
     "${OPERATOR}@${HOST}")

if [[ "${MODE}" == check ]]; then
  "${SSH[@]}" sudo -n bash -s <<'REMOTE'
set -u
for p in /opt/codemastery/tooling /opt/codemastery/releases /var/lib/codemastery /var/lib/codemastery/backup \
         /etc/codemastery/deploy /etc/codemastery/backup /usr/local/sbin/codemastery-ops \
         /usr/local/sbin/codemastery-ssh-gate /etc/sudoers.d/codemastery-deploy /etc/codemastery/deploy/ops.conf \
         /etc/codemastery/backup/recipient.crt /etc/codemastery/backup/producer-signing.key \
         /etc/systemd/system/codemastery-capture.timer /etc/systemd/system/codemastery-tls-renew.timer \
         /etc/tmpfiles.d/codemastery.conf; do
  if [ -e "$p" ]; then stat -c 'present  %A %U:%G %n' "$p"; else echo "missing  $p"; fi
done
id cmdeploy >/dev/null 2>&1 && echo "present  user cmdeploy" || echo "missing  user cmdeploy"
[ -L /opt/codemastery/tooling/current ] && echo "tooling  $(readlink /opt/codemastery/tooling/current)" || echo "tooling  none"
REMOTE
  exit $?
fi

[[ -f "${DEPLOY_PUB}" ]] || fail "--deploy-public-key (the cmdeploy ED25519 public key) is required."
grep -Eq '^ssh-ed25519 [A-Za-z0-9+/=]+( [A-Za-z0-9@._-]+)?$' "${DEPLOY_PUB}" || fail "the deploy key must be one ssh-ed25519 public key."
[[ -f "${RECIPIENT}" ]] || fail "--recipient-cert (from recovery.py init-workstation) is required."
grep -q 'BEGIN CERTIFICATE' "${RECIPIENT}" && ! grep -q 'PRIVATE KEY' "${RECIPIENT}" || fail "--recipient-cert must be a certificate only."
[[ "${WAIT}" =~ ^[1-9][0-9]{0,4}$ ]] || fail "--wait-timeout SECONDS (the deploy.sh readiness deadline you choose) is required."
[[ -z "$(git -C "${REPO}" status --porcelain)" ]] || fail "the checkout has local changes."
sha="$(git -C "${REPO}" rev-parse HEAD)"; tree="$(git -C "${REPO}" rev-parse 'HEAD^{tree}')"
work="$(mktemp -d)"; trap 'rm -rf "${work}"' EXIT
git -C "${REPO}" archive --format=tar -o "${work}/tooling.tar" HEAD
cp "${DEPLOY_PUB}" "${work}/deploy.pub"; cp "${RECIPIENT}" "${work}/recipient.crt"
cp "${HERE}/bootstrap-remote.sh" "${work}/bootstrap-remote.sh"

# Only fixed hex IDs and an integer reach the remote command line; files travel as one tar on stdin.
# Python's tarfile writes a plain ustar stream (no macOS extended attributes).
python3 -I -c 'import sys,tarfile,os
with tarfile.open(fileobj=sys.stdout.buffer, mode="w|", format=tarfile.USTAR_FORMAT) as t:
    for n in sys.argv[2:]:
        t.add(os.path.join(sys.argv[1], n), arcname=n)' "${work}" tooling.tar deploy.pub recipient.crt bootstrap-remote.sh | "${SSH[@]}" \
  "sudo -n env SHA=${sha} TREE=${tree} WAIT=${WAIT} bash -c 'set -Eeuo pipefail; d=\$(mktemp -d); trap \"rm -rf \$d\" EXIT; tar -C \"\$d\" -xf -; bash \"\$d/bootstrap-remote.sh\" \"\$d\"'"
