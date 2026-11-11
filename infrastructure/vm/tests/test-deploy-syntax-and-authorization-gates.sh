#!/usr/bin/env bash
# CI regression test for job "VM deployment script" (deployment-script),
# step "Check shell syntax and authorization gates" in .github/workflows/ci-vm.yml.
# Body moved verbatim from that step's former inline `run:` script.
# CI runs it from the repository root, where its relative paths resolve:
#   bash infrastructure/vm/tests/test-deploy-syntax-and-authorization-gates.sh
set -euo pipefail
script=infrastructure/vm/deploy.sh
bash -n "$script"

require_gate() {
  local expected="$1"
  shift
  local output
  if output="$(bash "$script" "$@" 2>&1)"; then
    echo "Expected deployment preflight to reject missing authorization: $expected" >&2
    exit 1
  fi
  if [[ "$output" != *"$expected"* ]]; then
    echo "Expected gate '$expected'; received:" >&2
    printf '%s\n' "$output" >&2
    exit 1
  fi
}

common=(--env-file /dev/null --origin https://example.invalid
        --expect-image backend=sha256:0000000000000000000000000000000000000000000000000000000000000000
        --wait-timeout 30 --initializer-qualification fixture)
deploy_common=("${common[@]}" --archive /dev/null
               --archive-sha256 0000000000000000000000000000000000000000000000000000000000000000
               --source-revision fixture)
require_gate '--provision-authorization is required' provision "${common[@]}"
require_gate 'must not be whitespace-only' provision "${common[@]}" \
  --provision-authorization '   '
# deploy re-provisions database roles and grants, so it needs the same
# provisioning authority as provision and all.
require_gate '--provision-authorization is required' deploy "${deploy_common[@]}"
deploy_common+=(--provision-authorization provisioning-record)
require_gate '--deployment-authorization is required' deploy "${deploy_common[@]}"
require_gate '--public-exposure-authorization is required' deploy "${deploy_common[@]}" \
  --deployment-authorization deployment-record
authorized=("${deploy_common[@]}" --deployment-authorization deployment-record
            --public-exposure-authorization exposure-record)
for command in deploy all; do
  require_gate '--packet-filter-verification is required' "$command" "${authorized[@]}"
done
require_gate 'must not be whitespace-only' deploy "${authorized[@]}" \
  --packet-filter-verification '   '
# With every reference present, input gates pass and validation moves on.
require_gate 'Env file not found or unreadable' deploy "${authorized[@]}" \
  --packet-filter-verification packet-filter-record
# load installs release artifacts on the host, the first deployment step.
load_common=(--env-file /dev/null --origin https://example.invalid
             --expect-image backend=sha256:0000000000000000000000000000000000000000000000000000000000000000
             --archive /dev/null
             --archive-sha256 0000000000000000000000000000000000000000000000000000000000000000)
require_gate '--deployment-authorization is required' load "${load_common[@]}"
require_gate 'must not be whitespace-only' load "${load_common[@]}" \
  --deployment-authorization '   '
require_gate 'Env file not found or unreadable' load "${load_common[@]}" \
  --deployment-authorization deployment-record
