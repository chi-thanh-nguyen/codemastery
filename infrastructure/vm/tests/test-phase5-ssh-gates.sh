#!/usr/bin/env bash
# CI regression test for job "VM continuous deployment and recovery" in .github/workflows/ci-vm.yml (ADR017).
#
# A disposable Ubuntu 24.04 SSH host (loopback-only port) with the real codemastery-ops
# dispatcher, the forced-command gate, the cmdeploy sudoers rule from bootstrap-remote.sh
# and an operator account. It exercises vm-transport.sh and the VM-side gates:
# host-key pinning (mismatch, missing pin), authentication failure, the deploy role's
# operation allowlist, shell-injection and interactive-login refusal, interrupted/altered/
# unsafe uploads, and tooling installation bound to an exact git tree.
# Run from the repository root:  bash infrastructure/vm/tests/test-phase5-ssh-gates.sh
set -Eeuo pipefail
name="cm-sshgate-$(printf '%s' "${GITHUB_RUN_ID:-local}$$" | cksum | cut -c1-8)"
image="codemastery-test/sshgate:${name}"
work="$(mktemp -d)"; chmod 700 "${work}"
cleanup() { docker rm -f "${name}" >/dev/null 2>&1 || true; docker image rm "${image}" >/dev/null 2>&1 || true; rm -rf "${work}"; }
trap cleanup EXIT
pass() { printf '  PASS: %s\n' "$*"; }
refused() {  # label substring command...
  local label="$1" want="$2" out; shift 2
  if out="$("$@" 2>&1)"; then echo "NOT REFUSED: ${label}" >&2; exit 1; fi
  [[ "${out}" == *"${want}"* ]] || { echo "WRONG REFUSAL for ${label}: ${out}" >&2; exit 1; }
  printf '  refused: %-56s [%s]\n' "${label}" "${want}"
}

echo "== disposable SSH host"
docker build -q -t "${image}" - >/dev/null <<'DOCKERFILE'
FROM ubuntu:24.04
RUN apt-get update -qq && DEBIAN_FRONTEND=noninteractive apt-get install -y -qq --no-install-recommends \
      openssh-server sudo python3 git ca-certificates >/dev/null && rm -rf /var/lib/apt/lists/* \
 && useradd --system --create-home --home-dir /var/lib/cmdeploy --shell /bin/sh cmdeploy && passwd -l cmdeploy \
 && useradd --create-home --shell /bin/bash opsuser && passwd -l opsuser \
 && printf 'opsuser ALL=(ALL) NOPASSWD: ALL\n' > /etc/sudoers.d/opsuser && chmod 0440 /etc/sudoers.d/opsuser \
 && mkdir -p /run/sshd && printf 'Port 2222\nPasswordAuthentication no\nKbdInteractiveAuthentication no\n' > /etc/ssh/sshd_config.d/test.conf
CMD ["/usr/sbin/sshd", "-D", "-e"]
DOCKERFILE
docker run -d --name "${name}" -p 127.0.0.1::2222 "${image}" sh -c 'ssh-keygen -A >/dev/null && exec /usr/sbin/sshd -D -e' >/dev/null
port="$(docker port "${name}" 2222/tcp | head -1 | sed 's/.*://')"
docker cp infrastructure/vm/host/codemastery-ops "${name}:/usr/local/sbin/codemastery-ops"
docker cp infrastructure/vm/host/codemastery-ssh-gate "${name}:/usr/local/sbin/codemastery-ssh-gate"
# The exact sudoers rule bootstrap-remote.sh installs.
sed -n "/^cat > \/etc\/sudoers.d\/.codemastery-deploy.new <<'SUDO'$/,/^SUDO$/p" infrastructure/vm/cd/bootstrap-remote.sh | sed '1d;$d' > "${work}/sudoers"
grep -q '^cmdeploy ALL=(root) NOPASSWD: /usr/local/sbin/codemastery-ops$' "${work}/sudoers"
docker cp "${work}/sudoers" "${name}:/etc/sudoers.d/codemastery-deploy"
for k in deploy operator stranger; do ssh-keygen -q -t ed25519 -N '' -f "${work}/$k" -C "$k"; done
docker exec -i "${name}" sh -c 'set -e; chown root:root /usr/local/sbin/codemastery-*; chmod 0755 /usr/local/sbin/codemastery-*
  chown root:root /etc/sudoers.d/codemastery-deploy; chmod 0440 /etc/sudoers.d/codemastery-deploy; visudo -c >/dev/null
  install -d -m 0700 -o cmdeploy -g cmdeploy /var/lib/cmdeploy/.ssh; install -d -m 0700 -o opsuser -g opsuser /home/opsuser/.ssh'
printf 'restrict,command="/usr/local/sbin/codemastery-ssh-gate" %s\n' "$(cat "${work}/deploy.pub")" \
  | docker exec -i "${name}" sh -c 'cat > /var/lib/cmdeploy/.ssh/authorized_keys; chown cmdeploy:cmdeploy /var/lib/cmdeploy/.ssh/authorized_keys; chmod 600 /var/lib/cmdeploy/.ssh/authorized_keys'
docker exec -i "${name}" sh -c 'cat > /home/opsuser/.ssh/authorized_keys; chown opsuser:opsuser /home/opsuser/.ssh/authorized_keys; chmod 600 /home/opsuser/.ssh/authorized_keys' < "${work}/operator.pub"
for i in $(seq 1 30); do nc -z 127.0.0.1 "${port}" 2>/dev/null && break; sleep 1; done
printf '[127.0.0.1]:%s %s\n' "${port}" "$(docker exec "${name}" cut -d' ' -f1,2 /etc/ssh/ssh_host_ed25519_key.pub)" > "${work}/known_hosts"
ssh-keygen -q -t ed25519 -N '' -f "${work}/fakehost"
printf '[127.0.0.1]:%s %s\n' "${port}" "$(cut -d' ' -f1,2 "${work}/fakehost.pub")" > "${work}/wrong_known_hosts"
: > "${work}/empty_known_hosts"
T=(bash infrastructure/vm/cd/vm-transport.sh --host 127.0.0.1 --port "${port}")
deploy=("${T[@]}" --user cmdeploy --identity "${work}/deploy" --known-hosts "${work}/known_hosts")
operator=("${T[@]}" --user opsuser --identity "${work}/operator" --known-hosts "${work}/known_hosts")
raw=(ssh -p "${port}" -i "${work}/deploy" -o BatchMode=yes -o IdentitiesOnly=yes -o StrictHostKeyChecking=yes
     -o UserKnownHostsFile="${work}/known_hosts" -o GlobalKnownHostsFile=/dev/null cmdeploy@127.0.0.1)

echo "== host authenticity and authentication"
refused "a host key that differs from the pin" "Host key verification failed" \
  "${T[@]}" --user cmdeploy --identity "${work}/deploy" --known-hosts "${work}/wrong_known_hosts" check
refused "a host with no pin (never trust on first use)" "Host key verification failed" \
  "${T[@]}" --user cmdeploy --identity "${work}/deploy" --known-hosts "${work}/empty_known_hosts" check
refused "an unauthorized key" "Permission denied" \
  "${T[@]}" --user cmdeploy --identity "${work}/stranger" --known-hosts "${work}/known_hosts" check
chmod 644 "${work}/deploy"
refused "an identity file readable by others" "mode 0600" "${deploy[@]}" ops status
chmod 600 "${work}/deploy"

echo "== tooling installation bound to its git tree"
export GIT_INDEX_FILE="${work}/index"
git read-tree HEAD && git add -A . >/dev/null
tree="$(git write-tree)"; unset GIT_INDEX_FILE
git archive --format=tar -o "${work}/tooling-${tree}.tar" "${tree}"
refused "operations before any tooling is installed" "no tooling is installed" "${deploy[@]}" ops status
"${deploy[@]}" upload "${work}/tooling-${tree}.tar" >/dev/null
refused "tooling whose git tree differs" "does not have git tree" "${deploy[@]}" ops install-tooling "${tree}" "$(printf '%040d' 0)"
mkdir -p "${work}/evil"; ln -s /etc/shadow "${work}/evil/link"; tar -C "${work}/evil" -cf "${work}/tooling-$(printf 'e%.0s' {1..40}).tar" link
"${deploy[@]}" upload "${work}/tooling-$(printf 'e%.0s' {1..40}).tar" >/dev/null
refused "tooling holding an absolute symlink" "absolute symlink" "${deploy[@]}" ops install-tooling "$(printf 'e%.0s' {1..40})" "${tree}"
"${deploy[@]}" ops install-tooling "${tree}" "${tree}" | grep -q '"operation":"install-tooling"'
"${deploy[@]}" check >/dev/null
pass "exact tree installed; transport check (pinned key, forced command, sudo, ops status) succeeds"

echo "== deploy-role allowlist, injection and interactive refusal"
for op in recovery-list recovery-send recovery-ack capture tls-renew timers; do
  refused "deploy identity: ${op}" "not permitted for the deployment identity" "${deploy[@]}" ops "${op}"
done
refused "a shell metacharacter through the forced command" "unsupported characters" "${raw[@]}" 'status;id'
refused "command substitution through the forced command" "unsupported characters" "${raw[@]}" 'status $(id)'
refused "an interactive login" "interactive login is disabled" "${raw[@]}"
refused "sudo of another program" "not permitted for the deployment identity" "${raw[@]}" sudo id
"${operator[@]}" ops recovery-list | grep -q '^\[\]$'
pass "the operator role may list recovery sets"

echo "== uploads: exact size and hash, safe names only"
head -c 100000 /dev/urandom > "${work}/blob.bin"
sum="$( (sha256sum "${work}/blob.bin" 2>/dev/null || shasum -a 256 "${work}/blob.bin") | awk '{print $1}')"
refused "an interrupted transfer" "was interrupted" sh -c "head -c 5000 '${work}/blob.bin' | ${raw[*]} 'receive blob.bin ${sum} 100000'"
refused "an altered transfer" "does not match its SHA-256" sh -c "${raw[*]} 'receive blob.bin $(printf '%064d' 0) 100000' < '${work}/blob.bin'"
refused "a path-traversal file name" "unsafe file name" sh -c "${raw[*]} 'receive ../../etc/x ${sum} 100000' < '${work}/blob.bin'"
docker exec "${name}" test ! -e /var/lib/codemastery/incoming/blob.bin
"${deploy[@]}" upload "${work}/blob.bin" >/dev/null
[[ "$(docker exec "${name}" sha256sum /var/lib/codemastery/incoming/blob.bin | awk '{print $1}')" == "${sum}" ]]
[[ "$(docker exec "${name}" stat -c '%U %a' /var/lib/codemastery/incoming/blob.bin)" == "root 600" ]]
pass "only a complete, hash-verified upload becomes a root-only file"
echo 'PHASE5_SSH_GATES_PASS'
