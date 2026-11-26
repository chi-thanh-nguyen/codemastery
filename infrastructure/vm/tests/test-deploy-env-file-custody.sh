#!/usr/bin/env bash
# CI regression test for job "VM deployment script" (deployment-script),
# step "Check env-file custody" in .github/workflows/ci-vm.yml.
# Body moved verbatim from that step's former inline `run:` script.
# CI runs it from the repository root, where its relative paths resolve:
#   bash infrastructure/vm/tests/test-deploy-env-file-custody.sh
set -euo pipefail
script="$PWD/infrastructure/vm/deploy.sh"
base="$(mktemp -d)"
trap 'sudo rm -rf "$base"' EXIT
chmod 755 "$base"
origin=https://deploy-ci.example.invalid
secrets=("$(openssl rand -hex 16)" "$(openssl rand -hex 16)" "$(openssl rand -hex 32)")
write_env() {
  cat > "$1" <<EOF
POSTGRES_DB=codemastery
POSTGRES_USER=cm_bootstrap
POSTGRES_PASSWORD=${secrets[0]}a
DB_NAME=codemastery
DB_USER=cm_runtime
DB_PASSWORD=${secrets[0]}b
SPRING_FLYWAY_URL=jdbc:postgresql://postgres:5432/codemastery
SPRING_FLYWAY_USER=cm_migrator
SPRING_FLYWAY_PASSWORD=${secrets[0]}c
MINIO_ROOT_USER=ci-root
MINIO_ROOT_PASSWORD=${secrets[1]}
STORAGE_ACCESS_KEY=ci-app
STORAGE_SECRET_KEY=${secrets[1]}d
JWT_SECRET=${secrets[2]}
APP_QUIZ_AUTO_SUBMIT_ENABLED=false
APP_QUIZ_AUTO_SUBMIT_DELAY_MS=1000
APP_QUIZ_AUTO_SUBMIT_BATCH_SIZE=2
CORS_ALLOWED_ORIGINS=$origin
EOF
  chmod 600 "$1"
}
fresh() {  # a compliant 0600 file in a new 0700 directory
  mkdir -m 700 "$base/$1"
  write_env "$base/$1/.env"
  printf '%s\n' "$base/$1/.env"
}
validate() {  # [sudo] path; prints VALID or the script's error
  local runner=()
  [[ "$1" != sudo ]] || { runner=(sudo); shift; }
  "${runner[@]}" bash -c 'source "$1"; ENV_FILE="$2"; ORIGIN="$3"; validate_env_file && echo VALID' \
    _ "$script" "$1" "$origin" 2>&1 || true
}
expect() {  # expected-fragment case-name output
  local secret
  if [[ "$3" != *"$1"* ]]; then
    echo "Env custody case '$2' expected '$1'; received:" >&2
    printf '%s\n' "$3" >&2
    exit 1
  fi
  for secret in "${secrets[@]}"; do
    [[ "$3" != *"$secret"* ]] || { echo "Env custody case '$2' printed a secret." >&2; exit 1; }
  done
  echo "ok: $2"
}
mode_error='must be mode 0600, owned by the invoking user and have no other hard link'
directory_error='directory must be mode 0700 and owned by the invoking user'
ancestor_error='the secret directory is not under operator control'

expect VALID 'compliant 0600 file in 0700 directory' "$(validate "$(fresh valid)")"
for mode in 400 640 644 700 2600; do
  env="$(fresh "file-$mode")"; chmod "$mode" "$env"
  expect "$mode_error" "file mode $mode" "$(validate "$env")"
done
for mode in 500 750 755 2700; do
  env="$(fresh "dir-$mode")"; chmod "$mode" "$(dirname "$env")"
  expect "$directory_error" "directory mode $mode" "$(validate "$env")"
done
env="$(fresh hard-link)"; mkdir -m 777 "$base/public"; ln "$env" "$base/public/copy.env"
expect "$mode_error" 'extra hard link in a public directory' "$(validate "$env")"
target="$(fresh symlink-target)"; mkdir -m 700 "$base/symlink"; ln -s "$target" "$base/symlink/.env"
expect 'must not be a symbolic link' 'symlinked env file' "$(validate "$base/symlink/.env")"
ln -s "$base/valid" "$base/valid-alias"
expect VALID 'directory symlink resolved to a compliant directory' "$(validate "$base/valid-alias/.env")"
mkdir -m 755 "$base/open"; write_env "$base/open/.env"; ln -s "$base/open" "$base/open-alias"
expect "$directory_error" 'directory symlink resolved to a 0755 directory' "$(validate "$base/open-alias/.env")"
mkdir -m 777 "$base/world"; mkdir -m 700 "$base/world/inner"; write_env "$base/world/inner/.env"
expect "$ancestor_error" '0700 directory inside a world-writable parent' "$(validate "$base/world/inner/.env")"
mkdir -m 1777 "$base/sticky"; mkdir -m 700 "$base/sticky/inner"; write_env "$base/sticky/inner/.env"
expect VALID '0700 directory inside a sticky shared parent' "$(validate "$base/sticky/inner/.env")"
sudo mkdir -m 755 "$base/foreign"; sudo chown nobody "$base/foreign"
sudo mkdir -m 700 "$base/foreign/inner"; sudo chown "$(id -u):$(id -g)" "$base/foreign/inner"
write_env "$base/foreign/inner/.env"
expect "$ancestor_error" 'parent owned by another user' "$(validate "$base/foreign/inner/.env")"
env="$(fresh other-owner)"; sudo chown nobody "$env"
expect "$mode_error" 'file owned by another user, checked as root' "$(validate sudo "$env")"
expect 'not found or unreadable' 'missing path' "$(validate "$base/valid/missing.env")"
expect 'not found or unreadable' 'directory instead of a file' "$(validate "$base/valid")"
# Re-validation (assert_release_configuration) must notice replacement.
env="$(fresh replaced)"; write_env "$base/replacement.env"
output="$(bash -c 'source "$1"; ENV_FILE="$2"; ORIGIN="$3"; validate_env_file
                   mv "$4" "$2"; validate_env_file && echo VALID' \
            _ "$script" "$env" "$origin" "$base/replacement.env" 2>&1 || true)"
expect 'replaced or changed since preflight' 'file replaced after preflight' "$output"
env="$(fresh edited)"
output="$(bash -c 'source "$1"; ENV_FILE="$2"; ORIGIN="$3"; validate_env_file
                   printf "# edited\n" >> "$2"; validate_env_file && echo VALID' \
            _ "$script" "$env" "$origin" 2>&1 || true)"
expect 'replaced or changed since preflight' 'file edited after preflight' "$output"
