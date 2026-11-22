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
#   --trusted-root offline mode: verify the bundled attestation against this
#                  Sigstore trusted root (`gh attestation trusted-root > FILE`,
#                  fetched earlier on a connected host) instead of GitHub's API;
#                  the online check that the admission record is still on main
#                  is then skipped and must be done on a connected host
#
# The repository (trust anchor) is fixed: chi-thanh-nguyen/codemastery.
#
# Passing verification proves that the package was produced by this repository's
# release workflow from main, that its admission record, candidate and release
# identities agree, and that deploy.sh accepts the archive. It does not authorize
# loading, deploying or updating anything (deploy.sh's own authority gates stay).
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
attest=(--repo "$REPOSITORY" --cert-identity "https://github.com/$REPOSITORY/.github/workflows/release.yml@refs/heads/main"
        --source-ref refs/heads/main --source-digest "$tooling_sha" --deny-self-hosted-runners)
if [[ -n "$TRUSTED_ROOT" ]]; then
  [[ -f "$PACKAGE/release.sigstore.json" ]] || fail "Offline mode needs release.sigstore.json in the package."
  attest+=(--bundle "$PACKAGE/release.sigstore.json" --custom-trusted-root "$TRUSTED_ROOT")
fi
for file in release.json "$archive" mc-source.tar.gz admission.json; do
  gh attestation verify "$PACKAGE/$file" "${attest[@]}" >/dev/null 2>&1 \
    || fail "No valid release-workflow attestation for $file."
done
echo "Attestation verified for release.json, the archive, mc-source.tar.gz and admission.json (signer: release.yml on main)."

# Independent of the promote job: the admission record must still be on main,
# unchanged (a later revocation or edit of the record fails this check).
if [[ -z "$TRUSTED_ROOT" ]]; then
  record="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["record_id"])' "$PACKAGE/admission.json")"
  [[ "$record" =~ ^[A-Z0-9][A-Z0-9-]{7,79}$ ]] || fail "admission.json has a malformed record_id."
  main_blob="$(gh api "repos/$REPOSITORY/contents/docs/decisions/release-admissions/$record.json?ref=main" --jq .sha 2>/dev/null)" \
    || fail "The admission record $record is not on main."
  local_blob="$(python3 -c 'import hashlib,sys; d=open(sys.argv[1],"rb").read(); print(hashlib.sha1(b"blob %d\0" % len(d) + d).hexdigest())' "$PACKAGE/admission.json")"
  [[ "$main_blob" == "$local_blob" ]] || fail "The admission record on main differs from the package's admission.json."
  echo "Admission record $record is on main, unchanged."
else
  echo "Offline: check on a connected host that docs/decisions/release-admissions/<record_id>.json on main equals admission.json."
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
