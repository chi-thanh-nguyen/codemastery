#!/usr/bin/env bash
# CI regression test for job "VM HTTPS ingress" (deployment-ingress),
# step "Build the release and local images; reject invalid release origins" in .github/workflows/ci-vm.yml.
# Body moved verbatim from that step's former inline `run:` script.
# CI runs it from the repository root, where its relative paths resolve:
#   bash infrastructure/vm/tests/test-ingress-release-image-build.sh
set -euo pipefail
# Quiet on success; on failure print the end of the plain build log so
# the real cause (for example an apk resolution error) is visible.
# No build argument here carries a secret.
build_image() {
  local log; log="$(mktemp)"
  if ! docker build --progress=plain "$@" >"$log" 2>&1; then
    echo "docker build $* failed; last 80 lines of the build log:" >&2
    tail -n 80 "$log" >&2; rm -f "$log"; return 1
  fi
  rm -f "$log"
}
build_image --target release --build-arg PUBLIC_ORIGIN=https://ingress.codemastery.test \
  -t "$FRONTEND_RELEASE_IMAGE" frontend
build_image -t "$FRONTEND_LOCAL_IMAGE" frontend
build_image -t "$BACKEND_IMAGE" backend
bash infrastructure/vm/tests/test-java-truststore.sh "$BACKEND_IMAGE"
docker image inspect --format 'release frontend: user {{.Config.User}}, healthcheck {{json .Config.Healthcheck.Test}}' \
  "$FRONTEND_RELEASE_IMAGE"
# The runtime user (UID 101) cannot change the Nginx configuration; only
# the cache directory (and /tmp, for the pid file) stays writable.
for image in "$FRONTEND_RELEASE_IMAGE" "$FRONTEND_LOCAL_IMAGE"; do
  docker run --rm --entrypoint sh "$image" -ec '
    [ "$(id -u)" = 101 ]
    [ "$(nginx -v 2>&1)" = "nginx version: nginx/1.30.6" ]
    bad="$(find /etc/nginx ! -type l \( ! -user root -o ! -group root -o -perm -g+w -o -perm -o+w \))"
    [ -z "$bad" ] || { echo "Nginx configuration not root-owned and read-only: $bad" >&2; exit 1; }
    for target in /etc/nginx/nginx.conf /etc/nginx/conf.d/default.conf /etc/nginx/conf.d/probe.conf /etc/nginx/probe; do
      if (: > "$target") 2>/dev/null; then echo "UID 101 can write $target" >&2; exit 1; fi
    done
    : > /var/cache/nginx/probe && rm /var/cache/nginx/probe'
  echo "$image: patched Nginx configuration is root-owned and not writable by UID 101"
done
# No application runtime image keeps a setuid/setgid program.
for image in "$FRONTEND_RELEASE_IMAGE" "$FRONTEND_LOCAL_IMAGE" "$BACKEND_IMAGE"; do
  privileged="$(docker run --rm --user 0:0 --entrypoint find "$image" / -xdev -type f \( -perm -4000 -o -perm -2000 \))"
  [[ -z "$privileged" ]] || { printf 'setuid/setgid files in %s:\n%s\n' "$image" "$privileged" >&2; exit 1; }
done
echo "both frontend targets and backend: no setuid/setgid files"
# The release target fails closed without exactly one valid public origin.
reject_origin() {  # expected-message [build args...]
  local want="$1" output; shift
  if output="$(docker build --progress=plain --target release "$@" frontend 2>&1)"; then
    echo "Release build accepted: ${*:-no PUBLIC_ORIGIN}" >&2; exit 1
  fi
  # Match the step's output line, not the echoed RUN text.
  printf '%s\n' "$output" | grep -E '^#[0-9]+ [0-9.]+ PUBLIC_ORIGIN must' | grep -qF "$want" \
    || { echo "Unexpected failure for ${*:-no PUBLIC_ORIGIN}:" >&2; printf '%s\n' "$output" | tail -5 >&2; exit 1; }
  echo "rejected: ${*:-no PUBLIC_ORIGIN}"
}
reject_origin 'bare https://hostname'
for origin in http://ingress.codemastery.test https://ingress.codemastery.test/ \
              https://ingress.codemastery.test:8443 'https://*.codemastery.test' https://Ingress.codemastery.test; do
  reject_origin 'bare https://hostname' --build-arg PUBLIC_ORIGIN="$origin"
done
reject_origin 'not an IP address' --build-arg PUBLIC_ORIGIN=https://203.0.113.10
reject_origin 'must not be localhost' --build-arg PUBLIC_ORIGIN=https://localhost.codemastery.test
