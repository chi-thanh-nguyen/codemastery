#!/usr/bin/env bash
# CodeMastery - infrastructure/vm/cd/bootstrap-remote.sh   (ADR017; runs as root ON the VM)
#
# Called only by bootstrap.sh --apply with: SHA, TREE (the tooling commit and its git tree),
# WAIT (the operator's deploy.sh readiness deadline) and $1 = a directory holding
# tooling.tar, deploy.pub and recipient.crt. Idempotent; never replaces existing keys,
# users or settings; never touches containers, volumes, the env file or TLS material.
set -Eeuo pipefail
umask 077
export LC_ALL=C PATH=/usr/sbin:/usr/bin:/sbin:/bin
fail() { printf 'BOOTSTRAP FAILED: %s\n' "$*" >&2; exit 2; }
say() { printf '  %s\n' "$*"; }
in="${1:?}"
[[ "${EUID}" -eq 0 ]] || fail "must run as root."
[[ "${SHA}" =~ ^[0-9a-f]{40}$ && "${TREE}" =~ ^[0-9a-f]{40}$ && "${WAIT}" =~ ^[1-9][0-9]*$ ]] || fail "bad parameters."
. /etc/os-release
[[ "${ID}:${VERSION_ID}" == ubuntu:24.04 && "$(uname -m)" == x86_64 ]] || fail "not the approved Ubuntu 24.04 amd64 host."

echo "== locations (ADR009 sections 9.3-9.5)"
install -d -m 0755 -o root -g root /opt/codemastery/tooling /opt/codemastery/releases
for d in /var/lib/codemastery /var/lib/codemastery/incoming /var/lib/codemastery/releases \
         /var/lib/codemastery/backup /var/lib/codemastery/deployments /var/lib/codemastery/tls \
         /etc/codemastery/deploy /etc/codemastery/backup; do
  install -d -m 0700 -o root -g root "$d"; say "$d"
done
printf 'd /run/codemastery 0700 root root -\n' > /etc/tmpfiles.d/codemastery.conf
systemd-tmpfiles --create /etc/tmpfiles.d/codemastery.conf

echo "== deployment tooling ${SHA}"
install -m 0600 "${in}/tooling.tar" "/var/lib/codemastery/incoming/tooling-${SHA}.tar"
if [[ -x /usr/local/sbin/codemastery-ops ]]; then
  /usr/local/sbin/codemastery-ops install-tooling "${SHA}" "${TREE}" >/dev/null
else
  work="$(mktemp -d)"; trap 'rm -rf "${work}"' EXIT
  tar -x --no-same-owner -C "${work}" -f "${in}/tooling.tar" infrastructure/vm/host/codemastery-ops
  CODEMASTERY_TOOLING_ROOT=/opt/codemastery/tooling bash "${work}/infrastructure/vm/host/codemastery-ops" \
    install-tooling "${SHA}" "${TREE}" >/dev/null
fi
cur="/opt/codemastery/tooling/current"
install -m 0755 -o root -g root "${cur}/infrastructure/vm/host/codemastery-ops" /usr/local/sbin/codemastery-ops
install -m 0755 -o root -g root "${cur}/infrastructure/vm/host/codemastery-ssh-gate" /usr/local/sbin/codemastery-ssh-gate
install -d -m 0755 -o root -g root /usr/local/lib/codemastery
install -m 0644 -o root -g root "${cur}/infrastructure/vm/host/authorize.py" /usr/local/lib/codemastery/authorize.py
say "/usr/local/sbin/codemastery-ops, /usr/local/sbin/codemastery-ssh-gate"

echo "== restricted deployment identity cmdeploy"
if ! id cmdeploy >/dev/null 2>&1; then
  useradd --system --create-home --home-dir /var/lib/cmdeploy --shell /bin/sh --comment "CodeMastery deployment (forced command)" cmdeploy
fi
passwd -l cmdeploy >/dev/null
install -d -m 0700 -o cmdeploy -g cmdeploy /var/lib/cmdeploy/.ssh
key="$(cat "${in}/deploy.pub")"
line="restrict,command=\"/usr/local/sbin/codemastery-ssh-gate\" ${key}"
printf '%s\n' "${line}" > /var/lib/cmdeploy/.ssh/authorized_keys.new
chown cmdeploy:cmdeploy /var/lib/cmdeploy/.ssh/authorized_keys.new
chmod 0600 /var/lib/cmdeploy/.ssh/authorized_keys.new
mv -T /var/lib/cmdeploy/.ssh/authorized_keys.new /var/lib/cmdeploy/.ssh/authorized_keys
say "authorized key: $(ssh-keygen -lf /var/lib/cmdeploy/.ssh/authorized_keys | awk '{print $2}') (forced command, restrict)"
cat > /etc/sudoers.d/.codemastery-deploy.new <<'SUDO'
# ADR017: the deployment identity may run exactly one program as root; its role there is
# "deploy", which permits only the deployment operations (codemastery-ops).
Defaults:cmdeploy !use_pty, !requiretty, env_reset
cmdeploy ALL=(root) NOPASSWD: /usr/local/sbin/codemastery-ops
SUDO
chmod 0440 /etc/sudoers.d/.codemastery-deploy.new
visudo -cqf /etc/sudoers.d/.codemastery-deploy.new || { rm -f /etc/sudoers.d/.codemastery-deploy.new; fail "sudoers rule rejected."; }
mv -T /etc/sudoers.d/.codemastery-deploy.new /etc/sudoers.d/codemastery-deploy
visudo -cq || fail "the complete sudoers configuration does not validate."
say "/etc/sudoers.d/codemastery-deploy"

echo "== ops configuration"
if [[ ! -f /etc/codemastery/deploy/ops.conf ]]; then
  acme=/etc/codemastery/acme; [[ -d "${acme}" ]] || acme=/root/.acme.sh
  python3 -I - "${WAIT}" "${acme}" > /etc/codemastery/deploy/ops.conf <<'PY'
import json, sys
print(json.dumps({"origin": "https://codemastery.app", "env_file": "/etc/codemastery/runtime/.env",
                  "wait_timeout": int(sys.argv[1]), "stop_timeout": int(sys.argv[1]),
                  "tls_dir": "/etc/codemastery/tls", "tls_backup_root": "/opt/codemastery/backups",
                  "acme_home": sys.argv[2], "acme_domain": "codemastery.app", "tar_mode": "host",
                  "capacity": {}}, indent=2, sort_keys=True))
PY
  chmod 0600 /etc/codemastery/deploy/ops.conf
  say "written (wait_timeout ${WAIT} s)"
else
  say "kept existing /etc/codemastery/deploy/ops.conf"
fi

echo "== recovery custody (ADR009 section 9.5)"
install -m 0644 -o root -g root "${in}/recipient.crt" /etc/codemastery/backup/recipient.crt.new
openssl x509 -in /etc/codemastery/backup/recipient.crt.new -noout >/dev/null || fail "recipient certificate unreadable."
if [[ -f /etc/codemastery/backup/recipient.crt ]] && ! cmp -s /etc/codemastery/backup/recipient.crt /etc/codemastery/backup/recipient.crt.new; then
  rm -f /etc/codemastery/backup/recipient.crt.new
  fail "a different recipient certificate is installed; rotate it deliberately, not by bootstrap."
fi
mv -T /etc/codemastery/backup/recipient.crt.new /etc/codemastery/backup/recipient.crt
if [[ ! -f /etc/codemastery/backup/producer-signing.key ]]; then
  openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 -out /etc/codemastery/backup/producer-signing.key 2>/dev/null
  chmod 0400 /etc/codemastery/backup/producer-signing.key
  say "generated the producer signing key on the VM"
fi
echo "-- producer public key (pin it: recovery.py trust-producer FILE --fingerprint HEX)"
openssl pkey -in /etc/codemastery/backup/producer-signing.key -pubout
printf 'producer key SHA-256: %s\n' "$(openssl pkey -in /etc/codemastery/backup/producer-signing.key -pubout -outform DER | sha256sum | awk '{print $1}')"

echo "== systemd units (installed DISABLED)"
for unit in codemastery-capture.service codemastery-capture.timer codemastery-tls-renew.service codemastery-tls-renew.timer; do
  install -m 0644 -o root -g root "${cur}/infrastructure/vm/host/systemd/${unit}" "/etc/systemd/system/${unit}"
done
systemctl daemon-reload
say "enable after qualification: sudo codemastery-ops timers enable"
echo "BOOTSTRAP COMPLETE (tooling ${SHA}). Nothing was started, stopped or exposed."
