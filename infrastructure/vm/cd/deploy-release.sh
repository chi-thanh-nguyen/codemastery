#!/usr/bin/env bash
# CodeMastery - infrastructure/vm/cd/deploy-release.sh   (ADR017; bash 3.2 compatible)
#
# One driver for the GitHub Deploy workflow and for the local operator fallback:
#
#   deploy-release.sh --operation plan|prepare|deploy|verify|resume|status --version vX.Y.Z
#       [--package DIR]                     already downloaded release assets (else gh downloads them)
#       [--recovery-set ID --recovery-manifest-sha256 HEX]   deploy only
#       [--context-file FILE]               deploy only: {"encoded": ...} from cd.py context
#       [--result FILE]                     where to write the VM's JSON result
#   Transport: VM_SSH_IDENTITY (key file), VM_SSH_USER (default cmdeploy), VM_SSH_HOST.
#
# Steps: (1) download only the assets the operation needs; (2) verify them: release.json
# must carry release.yml's attestation from main at the tooling commit it names, and for
# prepare the whole package is verified by that commit's own verify-release.sh (with the
# source revision checkout); (3) upload the deployment tooling (this checkout's HEAD, a
# clean commit) and the source revision as git archives, which the VM accepts only with
# their exact git tree IDs, plus the package files; (4) run the VM operation.
# The VM re-checks every hash against the release.json digest named here.
set -Eeuo pipefail
umask 077
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO="$(cd "${HERE}/../../.." && pwd -P)"
REPOSITORY="chi-thanh-nguyen/codemastery"
fail() { printf 'DEPLOY FAILED: %s\n' "$*" >&2; exit 2; }
note() { printf '==> %s\n' "$*" >&2; }

OPERATION="" VERSION="" PACKAGE="" RSET="" RSHA="" CONTEXT_FILE="" RESULT=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    (--operation) OPERATION="${2:?}"; shift 2 ;;
    (--version) VERSION="${2:?}"; shift 2 ;;
    (--package) PACKAGE="${2:?}"; shift 2 ;;
    (--recovery-set) RSET="${2:?}"; shift 2 ;;
    (--recovery-manifest-sha256) RSHA="${2:?}"; shift 2 ;;
    (--context-file) CONTEXT_FILE="${2:?}"; shift 2 ;;
    (--result) RESULT="${2:?}"; shift 2 ;;
    (*) fail "unknown option $1" ;;
  esac
done
case "${OPERATION}" in (plan|prepare|deploy|verify|resume|status) ;; (*) fail "--operation is required." ;; esac
if [[ "${OPERATION}" != resume && "${OPERATION}" != status && "${OPERATION}" != verify ]]; then
  [[ "${VERSION}" =~ ^v[0-9]+\.[0-9]+\.[0-9]+(-[0-9A-Za-z]+(\.[0-9A-Za-z]+)*)?$ ]] || fail "--version vX.Y.Z is required."
fi
work="$(mktemp -d "${TMPDIR:-/tmp}/cm-deploy.XXXXXXXX")"
trap 'rm -rf "${work}"; git -C "${REPO}" worktree prune >/dev/null 2>&1 || true' EXIT
RESULT="${RESULT:-${work}/result.json}"
transport() { bash "${HERE}/vm-transport.sh" "$@"; }
sha256_of() { if command -v sha256sum >/dev/null; then sha256sum "$1" | awk '{print $1}'; else shasum -a 256 "$1" | awk '{print $1}'; fi; }
json_get() { python3 -I -c 'import json,sys
d=json.load(open(sys.argv[1]))
for k in sys.argv[2].split("."): d=d[k]
print(d)' "$1" "$2"; }

# Deployment tooling: this checkout, a clean commit (on main in the workflow).
[[ -z "$(git -C "${REPO}" status --porcelain)" ]] || fail "the deployment tooling checkout ${REPO} has local changes."
TOOLING_SHA="$(git -C "${REPO}" rev-parse HEAD)"
TOOLING_TREE="$(git -C "${REPO}" rev-parse "HEAD^{tree}")"
if git -C "${REPO}" rev-parse --verify -q origin/main >/dev/null && \
   ! git -C "${REPO}" merge-base --is-ancestor "${TOOLING_SHA}" origin/main; then
  note "WARNING: deployment tooling ${TOOLING_SHA} is not on origin/main (local operator use only)."
fi

run_vm() {  # ops OPERATION ARGS...; captures stdout JSON into RESULT
  local rc=0
  transport ops "$@" > "${RESULT}" || rc=$?
  cat "${RESULT}"
  return "${rc}"
}

case "${OPERATION}" in
  (status) run_vm status; exit $? ;;
  (resume) run_vm resume; exit $? ;;
  (verify) run_vm verify ${VERSION:+--version "${VERSION}"}; exit $? ;;
esac

# 1. Assets.
small=(release.json release.sigstore.json expected-images.containerd.txt expected-images.graphdriver.txt
       admission.json admission.sigstore.json)
if [[ -z "${PACKAGE}" ]]; then
  PACKAGE="${work}/package"; mkdir -p "${PACKAGE}"
  command -v gh >/dev/null || fail "gh is required to download the release draft."
  if [[ "${OPERATION}" == prepare ]]; then
    note "downloading every asset of ${VERSION}"
    gh release download "${VERSION}" -R "${REPOSITORY}" -D "${PACKAGE}" >/dev/null
  else
    args=(); for f in "${small[@]}"; do args+=(-p "${f}"); done
    note "downloading the manifests of ${VERSION}"
    gh release download "${VERSION}" -R "${REPOSITORY}" -D "${PACKAGE}" "${args[@]}" >/dev/null
  fi
fi
[[ -f "${PACKAGE}/release.json" ]] || fail "the package has no release.json."
RELEASE_SHA="$(sha256_of "${PACKAGE}/release.json")"
REL_TOOLING="$(python3 -I -c 'import json,sys,re; v=json.load(open(sys.argv[1]))["tooling"]["sha"]; assert re.fullmatch("[0-9a-f]{40}",v); print(v)' "${PACKAGE}/release.json")" \
  || fail "release.json names no tooling commit."
SOURCE_REV="$(python3 -I -c 'import json,sys,re; v=json.load(open(sys.argv[1]))["source_revision"]; assert re.fullmatch("[0-9a-f]{40}",v); print(v)' "${PACKAGE}/release.json")" \
  || fail "release.json names no source revision."
[[ "$(json_get "${PACKAGE}/release.json" version)" == "${VERSION}" ]] || fail "the package is not release ${VERSION}."

# 2. Verification.
note "verifying release.json's attestation (release.yml on main at ${REL_TOOLING})"
gh attestation verify "${PACKAGE}/release.json" --repo "${REPOSITORY}" \
  --cert-identity "https://github.com/${REPOSITORY}/.github/workflows/release.yml@refs/heads/main" \
  --source-ref refs/heads/main --source-digest "${REL_TOOLING}" --deny-self-hosted-runners >/dev/null \
  || fail "release.json has no valid release-workflow attestation."
python3 -I - "${PACKAGE}" <<'PY' || fail "a downloaded file does not match release.json."
import hashlib, json, os, sys
pkg = sys.argv[1]
files = json.load(open(os.path.join(pkg, "release.json")))["files"]
def digest(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for block in iter(lambda: f.read(1 << 20), b""):
            h.update(block)
    return h.hexdigest()
for name in sorted(os.listdir(pkg)):
    if name in ("release.json", "release.sigstore.json"):
        continue
    if name == "SHA256SUMS":
        # release.py leaves SHA256SUMS (and release.json) out of the file map; accept it
        # only when it lists exactly the mapped files plus release.json, with their hashes.
        expected = dict(files, **{"release.json": digest(os.path.join(pkg, "release.json"))})
        listed = {}
        for line in open(os.path.join(pkg, name), encoding="utf-8").read().splitlines():
            parts = line.split()
            if len(parts) != 2 or parts[1] in listed:
                sys.exit("malformed SHA256SUMS")
            listed[parts[1]] = parts[0]
        if listed != expected:
            sys.exit("SHA256SUMS disagrees with release.json")
        continue
    if name not in files:
        sys.exit("unexpected file " + name)
    if digest(os.path.join(pkg, name)) != files[name]:
        sys.exit(name + " differs")
PY
git -C "${REPO}" cat-file -e "${REL_TOOLING}^{commit}" 2>/dev/null || git -C "${REPO}" fetch -q origin "${REL_TOOLING}" \
  || fail "the release tooling commit ${REL_TOOLING} is not available."
git -C "${REPO}" cat-file -e "${SOURCE_REV}^{commit}" 2>/dev/null || git -C "${REPO}" fetch -q origin "${SOURCE_REV}" \
  || fail "the source revision ${SOURCE_REV} is not available."
if [[ "${OPERATION}" == prepare ]]; then
  note "verifying the whole package with the release tooling's own verifier"
  git -C "${REPO}" worktree add -q --detach "${work}/release-tooling" "${REL_TOOLING}"
  git -C "${REPO}" worktree add -q --detach "${work}/source" "${SOURCE_REV}"
  store="$(transport ops inventory | python3 -I -c 'import json,sys; print(json.load(sys.stdin)["image_store"])')" \
    || fail "cannot read the VM's image store."
  bash "${work}/release-tooling/infrastructure/release/verify-release.sh" "${PACKAGE}" --version "${VERSION}" \
    --source "${work}/source" --store "${store}" >&2 || fail "verify-release.sh refused the package."
fi

# 3. Upload.
note "uploading deployment tooling ${TOOLING_SHA} and source ${SOURCE_REV}"
git -C "${REPO}" archive --format=tar -o "${work}/tooling-${TOOLING_SHA}.tar" "${TOOLING_SHA}"
git -C "${REPO}" archive --format=tar -o "${work}/source-${SOURCE_REV}.tar" "${SOURCE_REV}"
transport upload "${work}/tooling-${TOOLING_SHA}.tar" >/dev/null
transport ops install-tooling "${TOOLING_SHA}" "${TOOLING_TREE}" >/dev/null
transport upload "${work}/source-${SOURCE_REV}.tar" >/dev/null
if [[ "${OPERATION}" != deploy ]]; then
  upload=(release.json expected-images.containerd.txt expected-images.graphdriver.txt admission.json)
  if [[ "${OPERATION}" == prepare ]]; then
    upload+=(mc-source.tar.gz "$(json_get "${PACKAGE}/release.json" archive.name)")
  fi
  for f in "${upload[@]}"; do
    note "uploading ${f}"
    transport upload "${PACKAGE}/${f}" >/dev/null
  done
fi

# 4. Operation.
case "${OPERATION}" in
  (plan)    run_vm plan --version "${VERSION}" --release-json-sha256 "${RELEASE_SHA}" ;;
  (prepare) run_vm prepare --version "${VERSION}" --release-json-sha256 "${RELEASE_SHA}" ;;
  (deploy)
    [[ -f "${CONTEXT_FILE}" ]] || fail "--context-file is required for deploy."
    encoded="$(json_get "${CONTEXT_FILE}" encoded)"
    args=(deploy --version "${VERSION}" --release-json-sha256 "${RELEASE_SHA}" --context "${encoded}")
    [[ -z "${RSET}" ]] || args+=(--recovery-set "${RSET}" --recovery-manifest-sha256 "${RSHA}")
    run_vm "${args[@]}"
    ;;
esac
