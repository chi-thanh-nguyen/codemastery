#!/usr/bin/env bash
# CI regression test for job "VM HTTPS ingress" (deployment-ingress),
# step "Run DP01 preflight with the committed override and certificate layout" in .github/workflows/ci-vm.yml.
# Body moved verbatim from that step's former inline `run:` script.
# CI runs it from the repository root, where its relative paths resolve:
#   bash infrastructure/vm/tests/test-ingress-dp01-preflight.sh
set -euo pipefail
# DP01 preflight with the committed override on a disposable Ubuntu 24.04 host.
# It recreates the reported certificate layout with synthetic material:
# /etc/codemastery root 0700, tls/ UID 101 0700, privkey UID 101 0400,
# fullchain root 0644. Not a VM preflight and not a release.
script="$PWD/infrastructure/vm/deploy.sh"
override="$PWD/infrastructure/vm/docker-compose.vm.yml"
origin=https://ingress.codemastery.test
tls=/etc/codemastery/tls
root_env_dir=/root/codemastery-preflight-ci
[[ ! -e /etc/codemastery ]] || { echo "/etc/codemastery already exists; refusing to modify it." >&2; exit 1; }
work="$(mktemp -d)"
cleanup() { sudo rm -rf /etc/codemastery "$root_env_dir" "$work"; }
trap cleanup EXIT
chmod 700 "$work"

openssl ecparam -name prime256v1 -genkey -noout -out "$work/privkey.pem"
openssl req -x509 -new -key "$work/privkey.pem" -sha256 -days 1 -subj "/CN=${origin#https://}" \
  -addext "subjectAltName=DNS:${origin#https://}" -out "$work/fullchain.pem" 2>/dev/null
sudo install -d -m 700 -o root -g root /etc/codemastery
sudo install -d -m 700 -o 101 -g 101 "$tls"
sudo install -m 400 -o 101 -g 101 "$work/privkey.pem" "$tls/privkey.pem"
sudo install -m 644 -o root -g root "$work/fullchain.pem" "$tls/fullchain.pem"
rm "$work/privkey.pem"

secret() { openssl rand -hex 16; }
cat > "$work/env" <<EOF
POSTGRES_DB=codemastery
POSTGRES_USER=cm_bootstrap
POSTGRES_PASSWORD=$(secret)
DB_NAME=codemastery
DB_USER=cm_runtime
DB_PASSWORD=$(secret)
SPRING_FLYWAY_URL=jdbc:postgresql://postgres:5432/codemastery
SPRING_FLYWAY_USER=cm_migrator
SPRING_FLYWAY_PASSWORD=$(secret)
MINIO_ROOT_USER=ci-root
MINIO_ROOT_PASSWORD=$(secret)
STORAGE_ACCESS_KEY=ci-app
STORAGE_SECRET_KEY=$(secret)
JWT_SECRET=$(openssl rand -hex 32)
CORS_ALLOWED_ORIGINS=$origin
EOF
# Non-root operator custody: user-owned 0600 file in a user-owned 0700 directory.
mkdir -m 700 "$work/operator"; install -m 600 "$work/env" "$work/operator/.env"
# Root custody: root-owned 0600 file in a root-owned 0700 directory.
sudo install -d -m 700 -o root -g root "$root_env_dir"
sudo install -m 600 -o root -g root "$work/env" "$root_env_dir/.env"
rm "$work/env"

run() {  # expected-fragment label [sudo]: preflight must print the fragment
  local want="$1" label="$2" output runner=(); shift 2
  [[ "${1:-}" != sudo ]] || { runner=(sudo); shift; }
  local env="$work/operator/.env"
  [[ ${#runner[@]} -eq 0 ]] || env="$root_env_dir/.env"
  output="$("${runner[@]}" bash "$script" preflight --env-file "$env" --origin "$origin" \
    --compose-override "$override" "$@" 2>&1 || true)"
  [[ "$output" == *"$want"* ]] || { echo "Preflight case '$label' expected '$want'; received:" >&2; printf '%s\n' "$output" >&2; exit 1; }
  echo "ok: $label"
}

docker info >/dev/null 2>&1 || { echo "This check needs Docker access for the non-root user." >&2; exit 1; }
# Even with Docker access, a non-root operator cannot traverse the root-only
# /etc/codemastery parent, so the certificate checks cannot pass.
run 'frontend certificate directory must be owner-restricted' 'non-root operator with Docker access'
run 'PREFLIGHT OK' 'root operator with root-custody env file (committed override)' sudo

# The mounted directory must keep exactly the serving pair (renewal constraint).
sudo install -m 400 -o 101 -g 101 /dev/null "$tls/privkey.pem.bak"
sudo sh -c "cat '$tls/privkey.pem' > '$tls/privkey.pem.bak'"
run 'exactly one certificate matching its single private key' 'backup key left in the mounted directory' sudo
sudo rm "$tls/privkey.pem.bak"
sudo chmod 600 "$tls/privkey.pem"
run 'owner-restricted' 'private key mode 0600 after a renewal install' sudo
sudo chmod 400 "$tls/privkey.pem"
sudo chown 0 "$tls/privkey.pem"
run 'owner-restricted' 'private key ownership reset to root after a renewal install' sudo
sudo chown 101 "$tls/privkey.pem"
run 'PREFLIGHT OK' 'restored layout' sudo
run 'CORS_ALLOWED_ORIGINS must equal --origin' 'origin differs from the bound CORS origin' sudo \
  --origin https://other.codemastery.test

# Requirements that deploy.sh does not itself enforce, on the same effective model.
model="$(docker compose --project-name codemastery --env-file "$work/operator/.env" \
  -f infrastructure/docker-compose.yml -f "$override" config --format json)"
printf '%s' "$model" | python3 -c '
import json, sys
svc = json.load(sys.stdin)["services"]
fe, be = svc["frontend"], svc["backend"]
assert all("golithus" not in s["image"] and "build" not in s for s in svc.values()), "local-only image or build"
assert fe["ports"] == [{"mode": "ingress", "host_ip": "0.0.0.0", "target": 8080, "published": "443", "protocol": "tcp"}], fe["ports"]
(volume,) = fe["volumes"]
# Older Compose omits the false create_host_path value; newer renders it.
assert not volume.pop("bind", {}).get("create_host_path"), fe["volumes"]
assert volume == {"type": "bind", "source": "/etc/codemastery/tls", "target": "/etc/nginx/tls",
                  "read_only": True}, fe["volumes"]
assert be["environment"]["JWT_EXPIRATION"] == "PT15M"
assert svc["minio-init"].get("restart") is None and svc["minio-init"]["entrypoint"] is None
minio_env = svc["minio"]["environment"]
assert sorted(minio_env) == ["MINIO_BROWSER", "MINIO_ROOT_PASSWORD", "MINIO_ROOT_USER"], sorted(minio_env)
assert minio_env["MINIO_BROWSER"] == "off", minio_env["MINIO_BROWSER"]
writable = {"postgres": ["/var/run/postgresql"], "minio": [], "minio-init": ["/tmp"],
            "backend": ["/tmp"], "frontend": ["/tmp"]}
for name, s in svc.items():
    assert s.get("read_only") is True, name
    assert s.get("security_opt") == ["no-new-privileges:true"], (name, s.get("security_opt"))
    assert s.get("cap_drop") == ["ALL"], (name, s.get("cap_drop"))
    assert sorted(s.get("cap_add") or []) == (["CHOWN", "DAC_OVERRIDE", "FOWNER", "SETGID", "SETUID"]
                                              if name == "postgres" else []), (name, s.get("cap_add"))
    assert sorted(s.get("tmpfs") or []) == writable[name], (name, s.get("tmpfs"))
print("ok: effective model: no local-only images or builds, IPv4 443 only, certificate bind, PT15M, MinIO Console off, runtime hardening")'
# Image-level policy (verify_images) for the release images built in this job.
images() {
  bash -c 'source "$1"; COMPOSE_MODEL="$2"; shift 2
           while [[ $# -gt 0 ]]; do verify_image_configuration "$1" "$2"; shift 2; done' \
    _ "$script" "$model" "$@"
}
images frontend "$FRONTEND_RELEASE_IMAGE" backend "$BACKEND_IMAGE"
echo "ok: deploy.sh image policy accepts the release frontend and backend images"
if (images frontend "$FRONTEND_LOCAL_IMAGE") >/dev/null 2>&1; then
  echo "The local frontend image (no baked TLS healthcheck) was accepted as a release image." >&2; exit 1
fi
echo "ok: deploy.sh image policy rejects the local frontend image as a release image"
echo "INGRESS_PREFLIGHT_TESTS_PASS"
