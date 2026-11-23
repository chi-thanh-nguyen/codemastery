#!/usr/bin/env bash
# CodeMastery - infrastructure/release/verify-release.sh   (ADR015, operator verification)
#
# Verifies a downloaded release package before it is transferred to a VM. It
# changes nothing and needs no Docker: only bash, python3, sha256sum and gh.
#
#   verify-release.sh PACKAGE_DIR --version vX.Y.Z [--source CHECKOUT]
#                     [--store containerd|graphdriver] [--trusted-root FILE]
#
#   PACKAGE_DIR    the release draft's assets, downloaded into one directory
#   --version      the release the operator intends to transfer; the package must
#                  be exactly that release (a validly attested older or other
#                  release is refused)
#   --source       a clean checkout of the release's source revision; enables the
#                  deploy.sh archive verifier and the deploy.sh/Compose hash checks
#   --store        print the --expect-image values for this image store (deploy.sh
#                  preflight reports which one the VM uses); default containerd
#   --trusted-root offline mode: verify the bundled attestations against this
#                  Sigstore trusted root (`gh attestation trusted-root > FILE`,
#                  fetched earlier on a connected host) instead of GitHub's API;
#                  the online checks of GitHub's run and approval records are then
#                  skipped and must be done on a connected host
#
# The repository (trust anchor) is fixed: chi-thanh-nguyen/codemastery.
#
# What each check establishes (ADR015 A1):
#   integrity         SHA256SUMS, release.json file map, archive and identities
#   signing identity  Sigstore attestations by release.yml on refs/heads/main at
#                     the recorded tooling commit (offline-capable)
#   admission         admission.json re-derived from its evidence and the candidate,
#                     the decision of the pinned admission authority, sealed by the
#                     same dispatch run that promoted the package (offline-capable,
#                     as a statement of the attested workflow)
#   authority         online only: GitHub's own run record (dispatcher) and
#                     approval record (approver) for that run match admission.json
# It does not authorize loading, deploying or updating anything (deploy.sh's own
# authority gates stay), and it is not publication.
set -Eeuo pipefail
umask 077

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPOSITORY="chi-thanh-nguyen/codemastery"
readonly REPOSITORY
PACKAGE="" SOURCE="" STORE="containerd" TRUSTED_ROOT="" VERSION=""
fail() { printf 'VERIFY FAILED: %s\n' "$*" >&2; exit 1; }

PACKAGE="${1:-}"; [[ -n "$PACKAGE" && "$PACKAGE" != -* ]] || { sed -n '2,29p' "$0" >&2; exit 2; }
shift
while [[ $# -gt 0 ]]; do
  case "$1" in
    (--source)       SOURCE="${2:?--source needs a directory}"; shift 2 ;;
    (--store)        STORE="${2:?--store needs a value}"; shift 2 ;;
    (--trusted-root) TRUSTED_ROOT="${2:?--trusted-root needs a file}"; shift 2 ;;
    (--version)      VERSION="${2:?--version needs vX.Y.Z}"; shift 2 ;;
    (*) fail "Unknown option: $1" ;;
  esac
done
[[ "$STORE" == containerd || "$STORE" == graphdriver ]] || fail "--store must be containerd or graphdriver."
[[ "$VERSION" =~ ^v[0-9]+\.[0-9]+\.[0-9]+(-[0-9A-Za-z]+(\.[0-9A-Za-z]+)*)?$ ]] || fail "--version vX.Y.Z (the intended release) is required."
[[ -d "$PACKAGE" ]] || fail "Package directory not found."
command -v gh >/dev/null 2>&1 || fail "gh (GitHub CLI) is required to verify the attestation."

python3 "$HERE/release.py" verify-release-chain "$PACKAGE" --repository "$REPOSITORY" || fail "Package records are inconsistent."
archive="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["archive"]["name"])' "$PACKAGE/release.json")"
[[ "$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["version"])' "$PACKAGE/release.json")" == "$VERSION" ]] \
  || fail "The package is not release $VERSION."

# Exact signer: release.yml run for refs/heads/main, at the main commit that
# release.json names as its tooling.
tooling_sha="$(python3 -c 'import json,sys; print((json.load(open(sys.argv[1])).get("tooling") or {}).get("sha") or "")' "$PACKAGE/release.json")"
[[ "$tooling_sha" =~ ^[0-9a-f]{40}$ ]] || fail "release.json names no tooling commit."
policy=(--repo "$REPOSITORY" --cert-identity "https://github.com/$REPOSITORY/.github/workflows/release.yml@refs/heads/main"
        --source-ref refs/heads/main --source-digest "$tooling_sha" --deny-self-hosted-runners)
attest=("${policy[@]}")
if [[ -n "$TRUSTED_ROOT" ]]; then
  [[ -f "$PACKAGE/release.sigstore.json" ]] || fail "Offline mode needs release.sigstore.json in the package."
  attest+=(--bundle "$PACKAGE/release.sigstore.json" --custom-trusted-root "$TRUSTED_ROOT")
fi
for file in release.json "$archive" mc-source.tar.gz admission.json; do
  gh attestation verify "$PACKAGE/$file" "${attest[@]}" >/dev/null 2>&1 \
    || fail "No valid release-workflow attestation for $file."
done
echo "Attestation verified for release.json, the archive, mc-source.tar.gz and admission.json (signer: release.yml on main)."

# The admission's own seal: attested by the dispatch run named inside admission.json
# (Sigstore certificate: run invocation, workflow_dispatch trigger, repository ID).
admission_run="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["authorization"]["run_url"])' "$PACKAGE/admission.json")"
[[ "$admission_run" =~ ^https://github\.com/chi-thanh-nguyen/codemastery/actions/runs/[0-9]+/attempts/1$ ]] \
  || fail "admission.json names a malformed admission run."
seal=(--bundle "$PACKAGE/admission.sigstore.json")
[[ -z "$TRUSTED_ROOT" ]] || seal+=(--custom-trusted-root "$TRUSTED_ROOT")
work="$(mktemp -d)"; trap 'rm -rf "$work"' EXIT
gh attestation verify "$PACKAGE/admission.json" "${policy[@]}" "${seal[@]}" --format json > "$work/seal.json" 2>/dev/null \
  || fail "admission.sigstore.json is not a valid release-workflow attestation of admission.json."
python3 "$HERE/release.py" check-attestation-run --verification "$work/seal.json" --run-url "$admission_run" >/dev/null \
  || fail "The admission was not sealed by its own dispatch run."
echo "Admission sealed by its own dispatch run: $admission_run."

# Authority, online only: GitHub's record of who dispatched and who approved that run.
if [[ -z "$TRUSTED_ROOT" ]]; then
  run_api="repos/$REPOSITORY/actions/runs/$(printf '%s' "$admission_run" | sed -E 's#^.*/runs/([0-9]+)/attempts/1$#\1#')"
  gh api "$run_api/attempts/1" > "$work/run.json" 2>/dev/null || fail "GitHub no longer has the admission run's record."
  gh api "$run_api/approvals" > "$work/approvals.json" 2>/dev/null || fail "GitHub's approval record of the admission run is unavailable."
  python3 "$HERE/release.py" check-run-record --admission "$PACKAGE/admission.json" --run-record "$work/run.json" \
    --approvals "$work/approvals.json" || fail "GitHub's run or approval record contradicts admission.json."
else
  echo "Offline: GitHub's record of the dispatching and approving account was not checked; run without --trusted-root on a connected host to check it."
fi

if [[ -n "$SOURCE" ]]; then
  rev="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["source_revision"])' "$PACKAGE/release.json")"
  [[ "$(git -C "$SOURCE" rev-parse HEAD)" == "$rev" ]] || fail "--source is not checked out at the release source revision $rev."
  [[ -z "$(git -C "$SOURCE" status --porcelain)" ]] || fail "--source has uncommitted changes."
  python3 - "$PACKAGE/release.json" "$SOURCE" <<'PY' || fail "deploy.sh or Compose files differ from the released revision."
import hashlib, json, os, sys
c = json.load(open(sys.argv[1]))["compatibility"]
for key, path in (("deploy_sh_sha256", "infrastructure/vm/deploy.sh"),
                  ("compose_base_sha256", "infrastructure/docker-compose.yml"),
                  ("compose_override_sha256", "infrastructure/vm/docker-compose.vm.yml")):
    if hashlib.sha256(open(os.path.join(sys.argv[2], path), "rb").read()).hexdigest() != c[key]:
        sys.exit(1)
PY
  # This verifier's own release.sh, with the source revision's deploy.sh (which may
  # predate infrastructure/release/).
  bash "$HERE/release.sh" verify-archive "$SOURCE" "$PACKAGE/$archive" "$PACKAGE/release.json" --release-names \
    || fail "deploy.sh rejects the archive."
fi

echo
echo "--archive $archive"
echo "--archive-sha256 $(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["archive"]["sha256"])' "$PACKAGE/release.json")"
while IFS= read -r line; do echo "--expect-image $line"; done < "$PACKAGE/expected-images.$STORE.txt"
echo "--source-revision $(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["source_revision"])' "$PACKAGE/release.json")"
echo
echo "VERIFIED. This is not deployment, update or exposure authority; deploy.sh still requires its own references and, on a host with existing state, the four P9 update attestations."
