#!/usr/bin/env bash
# CI regression test for job "VM deployment script" (deployment-script),
# step "Check preflight against rendered Compose" in .github/workflows/ci-vm.yml.
# Body moved verbatim from that step's former inline `run:` script.
# CI runs it from the repository root, where its relative paths resolve:
#   bash infrastructure/vm/tests/test-deploy-preflight-rendered-compose.sh
set -euo pipefail
script=infrastructure/vm/deploy.sh
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
mkdir -m 700 "$work/env" "$work/certs"
origin=https://deploy-ci.example.invalid
secret() { openssl rand -hex 16; }
# Exercise smoke's actual generator in isolation, without invoking its deployment path.
bash -c 'source "$1"; write_smoke_env "$2" "$3"' _ \
  infrastructure/release/release.sh "$origin" "$work/env/.env"
chmod 600 "$work/env/.env"
openssl req -x509 -newkey rsa:2048 -nodes -days 1 -subj /CN=deploy-ci.example.invalid \
  -keyout "$work/certs/privkey.pem" -out "$work/certs/fullchain.pem" 2>/dev/null
chmod 400 "$work/certs/privkey.pem"
cat > "$work/release.yml" <<EOF
services:
  postgres:
    image: registry.invalid/codemastery/postgres:ci-fixture
    build: !reset null
    ports: !reset []
    restart: unless-stopped
    read_only: true
    tmpfs: [/var/run/postgresql]
    cap_drop: [ALL]
    cap_add: [CHOWN, DAC_OVERRIDE, FOWNER, SETGID, SETUID]
    security_opt: ["no-new-privileges:true"]
  minio:
    ports: !reset []
    restart: unless-stopped
    environment:
      MINIO_BROWSER: "off"
    read_only: true
    cap_drop: [ALL]
    security_opt: ["no-new-privileges:true"]
  minio-init:
    image: registry.invalid/codemastery/minio-init@sha256:$(printf 'a%.0s' {1..64})
    entrypoint: !reset null
    command: !reset null
    read_only: true
    tmpfs: [/tmp]
    cap_drop: [ALL]
    security_opt: ["no-new-privileges:true"]
  backend:
    image: registry.invalid/codemastery/backend:ci-fixture
    ports: !reset []
    restart: unless-stopped
    environment:
      CORS_ALLOWED_ORIGINS: \${CORS_ALLOWED_ORIGINS:?}
      SPRING_FLYWAY_URL: \${SPRING_FLYWAY_URL:?}
      SPRING_FLYWAY_USER: \${SPRING_FLYWAY_USER:?}
      SPRING_FLYWAY_PASSWORD: \${SPRING_FLYWAY_PASSWORD:?}
      SERVER_FORWARD_HEADERS_STRATEGY: NONE
      SPRING_PROFILES_ACTIVE: prod
    read_only: true
    tmpfs: [/tmp]
    cap_drop: [ALL]
    security_opt: ["no-new-privileges:true"]
  frontend:
    image: registry.invalid/codemastery/frontend:ci-fixture
    ports: !override
      - "443:8080"
    healthcheck: !reset null
    restart: unless-stopped
    read_only: true
    tmpfs: [/tmp]
    cap_drop: [ALL]
    security_opt: ["no-new-privileges:true"]
    volumes:
      - type: bind
        source: $work/certs
        target: /etc/nginx/certs
        read_only: true
EOF
preflight() {
  bash "$script" preflight --env-file "$work/env/.env" --origin "$origin" \
    --compose-override "$work/release.yml" "$@"
}
docker compose --project-name codemastery --env-file "$work/env/.env" \
  -f infrastructure/docker-compose.yml -f "$work/release.yml" config --format json \
  | python3 -c 'import json,sys; print("Rendered default network keys:", sorted(json.load(sys.stdin)["networks"]["default"]))'
if ! output="$(preflight 2>&1)" || [[ "$output" != *"PREFLIGHT OK"* ]]; then
  printf '%s\n' "$output" >&2
  exit 1
fi
printf '%s\n' "$output" | grep 'HOST_PLATFORM'
# Prove the actual generated inputs pass, then each omission/invalid value fails.
# Start every negative case from a newly generated file, never a populated fixture.
for key in APP_QUIZ_AUTO_SUBMIT_ENABLED APP_QUIZ_AUTO_SUBMIT_DELAY_MS APP_QUIZ_AUTO_SUBMIT_BATCH_SIZE; do
  for mutation in missing invalid; do
    bash -c 'source "$1"; write_smoke_env "$2" "$3"' _ \
      infrastructure/release/release.sh "$origin" "$work/env/.env"
    python3 - "$work/env/.env" "$key" "$mutation" <<'PYENV'
import pathlib, sys
path, key, mutation = pathlib.Path(sys.argv[1]), sys.argv[2], sys.argv[3]
lines = [line for line in path.read_text().splitlines() if not line.startswith(key + "=")]
if mutation == "invalid":
    lines.append(key + "=" + ("invalid" if key.endswith("ENABLED") else "0"))
path.write_text("\n".join(lines) + "\n")
PYENV
    if output="$(preflight 2>&1)"; then
      echo "Preflight accepted $mutation smoke input $key." >&2; exit 1
    fi
    [[ "$output" == *"$key"* ]] || { echo "Wrong smoke input rejection: $key" >&2; exit 1; }
    printf '  refused generated smoke input: %s (%s)\n' "$key" "$mutation"
  done
done
bash -c 'source "$1"; write_smoke_env "$2" "$3"' _ \
  infrastructure/release/release.sh "$origin" "$work/env/.env"
reject() {
  local expected="$1" name="$2" output
  cat > "$work/$name.yml"
  if output="$(preflight --compose-override "$work/$name.yml" 2>&1)"; then
    echo "Preflight accepted the $name policy override." >&2
    exit 1
  fi
  if [[ "$output" != *"$expected"* ]]; then
    echo "Unexpected preflight result for $name:" >&2
    printf '%s\n' "$output" >&2
    exit 1
  fi
}
reject 'must run with exactly no-new-privileges' no-new-privileges-removed <<'EOF'
services:
  backend:
    security_opt: !reset []
EOF
reject 'unapproved privilege' seccomp-unconfined <<'EOF'
services:
  frontend:
    security_opt: ["seccomp=unconfined"]
EOF
reject 'unapproved privilege' backend-cap-add <<'EOF'
services:
  backend:
    cap_add: [NET_ADMIN]
EOF
reject 'unapproved privilege' postgres-sys-admin <<'EOF'
services:
  postgres:
    cap_add: [SYS_ADMIN]
EOF
reject 'must run with exactly no-new-privileges' minio-capabilities-kept <<'EOF'
services:
  minio:
    cap_drop: !reset []
EOF
reject 'must run with exactly no-new-privileges' postgres-writable-root <<'EOF'
services:
  postgres:
    read_only: false
EOF
reject 'tmpfs must be exactly /tmp' backend-extra-tmpfs <<'EOF'
services:
  backend:
    tmpfs: [/run]
EOF
reject 'unapproved network options' ipam-subnet <<'EOF'
networks:
  default:
    ipam:
      config:
        - subnet: 10.231.0.0/24
EOF
reject 'unapproved network options' ipam-driver <<'EOF'
networks:
  default:
    ipam:
      driver: default
EOF
reject 'unapproved network options' driver-options <<'EOF'
networks:
  default:
    driver_opts:
      com.docker.network.bridge.enable_icc: "true"
EOF
reject 'unapproved network options' ipv6 <<'EOF'
networks:
  default:
    enable_ipv6: true
EOF
reject 'project-managed bridge' custom-driver <<'EOF'
networks:
  default:
    driver: macvlan
EOF
reject 'project-managed bridge' external <<'EOF'
networks:
  default:
    name: shared-host-network
    external: true
EOF
reject "only the Compose project's default network" extra-network <<'EOF'
services:
  backend:
    networks: [default, side]
networks:
  side: {}
EOF
reject 'unsupported Compose options' post-start-hook <<'EOF'
services:
  frontend:
    post_start:
      - command: ["sh", "-c", "echo synthetic-hook"]
EOF
reject 'unsupported Compose options' pre-stop-hook <<'EOF'
services:
  backend:
    pre_stop:
      - command: ["sh", "-c", "echo synthetic-hook"]
EOF
reject 'unsupported Compose options' deploy-replicas <<'EOF'
services:
  backend:
    deploy:
      replicas: 2
EOF
reject 'unsupported Compose options' logging-sink <<'EOF'
services:
  backend:
    logging:
      driver: syslog
EOF
reject 'unsupported Compose options' tmpfs-mount <<'EOF'
services:
  backend:
    tmpfs: ["/run"]
EOF
reject 'unsupported Compose options' minio-secret <<'EOF'
secrets:
  synthetic:
    file: /dev/null
services:
  minio:
    secrets: [synthetic]
EOF
reject 'MINIO_BROWSER=off' minio-console-enabled <<'EOF'
services:
  minio:
    environment:
      MINIO_BROWSER: "on"
EOF
reject 'MINIO_BROWSER=off' minio-console-default <<'EOF'
services:
  minio:
    environment: !override
      MINIO_ROOT_USER: ${MINIO_ROOT_USER}
      MINIO_ROOT_PASSWORD: ${MINIO_ROOT_PASSWORD}
EOF
reject "service 'minio' contains unapproved environment variables" minio-config-env-file <<'EOF'
services:
  minio:
    environment:
      MINIO_CONFIG_ENV_FILE: /data/.minio.env
EOF
# The PostgreSQL release image is only ever loaded from the reviewed archive:
# any build configuration, local or hostile, is outside the release model.
postgres_build="service 'postgres' contains unsupported Compose options: build"
reject "$postgres_build" postgres-local-build <<'EOF'
services:
  postgres:
    build:
      context: postgres
EOF
reject "$postgres_build" postgres-remote-git-context <<'EOF'
services:
  postgres:
    build:
      context: https://github.com/attacker.invalid/payload.git#main
EOF
reject "$postgres_build" postgres-privileged-build <<'EOF'
services:
  postgres:
    build:
      context: postgres
      privileged: true
EOF
reject "$postgres_build" postgres-host-network-build <<'EOF'
services:
  postgres:
    build:
      context: postgres
      network: host
EOF
reject "$postgres_build" postgres-insecure-entitlements <<'EOF'
services:
  postgres:
    build:
      context: postgres
      entitlements: [network.host, security.insecure]
EOF
reject_certificate() {
  local expected="$1" name="$2" output
  if output="$(preflight 2>&1)"; then
    echo "Preflight accepted invalid certificate material: $name." >&2
    exit 1
  fi
  if [[ "$output" != *"$expected"* ]]; then
    echo "Unexpected certificate rejection for $name:" >&2
    printf '%s\n' "$output" >&2
    exit 1
  fi
}
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "$work/certs/extra-key.pem" 2>/dev/null
chmod 400 "$work/certs/extra-key.pem"
reject_certificate 'exactly one certificate matching its single private key' extra-private-key
rm "$work/certs/extra-key.pem"
cp "$work/certs/privkey.pem" "$work/privkey.original"
chmod 600 "$work/certs/privkey.pem"
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "$work/certs/privkey.pem" 2>/dev/null
chmod 400 "$work/certs/privkey.pem"
reject_certificate 'exactly one certificate matching its single private key' mismatched-key
mv "$work/privkey.original" "$work/certs/privkey.pem"
chmod 400 "$work/certs/privkey.pem"
chmod 755 "$work/certs"
reject_certificate 'owner-restricted' permissive-directory
chmod 700 "$work/certs"
mkdir -m 777 "$work/certs/nested"
cp "$work/certs/fullchain.pem" "$work/certs/nested/chain.pem"
reject_certificate 'owner-restricted' permissive-nested-directory
rm -rf "$work/certs/nested"
printf '%s\n' 'synthetic unexpected secret material' > "$work/certs/unexpected-secret.txt"
reject_certificate 'owner-restricted' unexpected-secret-file
rm "$work/certs/unexpected-secret.txt"
if ! output="$(preflight 2>&1)" || [[ "$output" != *"PREFLIGHT OK"* ]]; then
  printf '%s\n' "$output" >&2
  echo "Valid serving certificate/key material was rejected after negative cases." >&2
  exit 1
fi

echo "PASS: generated smoke environment and rendered Compose preflight regressions"
