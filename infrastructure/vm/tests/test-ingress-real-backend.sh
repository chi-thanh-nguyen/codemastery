#!/usr/bin/env bash
# CI regression test for job "VM HTTPS ingress" (deployment-ingress),
# step "Exercise the HTTPS ingress with the real backend" in .github/workflows/ci-vm.yml.
# Body moved verbatim from that step's former inline `run:` script.
# CI runs it from the repository root, where its relative paths resolve:
#   bash infrastructure/vm/tests/test-ingress-real-backend.sh
set -euo pipefail
# The real backend behind the HTTPS ingress: same-origin browser semantics,
# exact-origin CORS, ProblemDetail paths and Auth headers through the proxy.
# Database privilege separation is exercised by the runtime-privilege job.
fe_image="$FRONTEND_RELEASE_IMAGE"; be_image="$BACKEND_IMAGE"
host=ingress.codemastery.test
origin="https://$host"
work="$(mktemp -d)"
prefix="codemastery-ingress-be-${GITHUB_RUN_ID:-local}-$$"
net="$prefix-net"
cleanup() {
  docker rm -f "$prefix-postgres" "$prefix-backend" "$prefix-frontend" >/dev/null 2>&1 || true
  docker network rm "$net" >/dev/null 2>&1 || true
  if [[ "$(uname -s)" == Linux ]]; then sudo rm -rf "$work"; else rm -rf "$work"; fi
}
trap cleanup EXIT
chmod 700 "$work"

pki="$work/pki"; tls="$work/tls"; mkdir -p "$pki" "$work/trust"; mkdir -m 700 "$tls"
openssl ecparam -name prime256v1 -genkey -noout -out "$pki/root.key"
openssl req -x509 -new -key "$pki/root.key" -sha256 -days 2 -subj '/CN=CodeMastery CI root' \
  -addext 'basicConstraints=critical,CA:true' -addext 'keyUsage=critical,keyCertSign,cRLSign' \
  -out "$work/trust/root.pem" 2>/dev/null
openssl ecparam -name prime256v1 -genkey -noout -out "$tls/privkey.pem"
openssl req -new -key "$tls/privkey.pem" -subj "/CN=$host" -out "$pki/leaf.csr" 2>/dev/null
printf 'subjectAltName=DNS:%s\nextendedKeyUsage=serverAuth\n' "$host" > "$pki/leaf.ext"
openssl x509 -req -in "$pki/leaf.csr" -CA "$work/trust/root.pem" -CAkey "$pki/root.key" -CAcreateserial \
  -days 2 -sha256 -extfile "$pki/leaf.ext" -out "$tls/fullchain.pem" 2>/dev/null
chmod 644 "$tls/fullchain.pem" "$work/trust/root.pem"; chmod 755 "$work/trust"; chmod 400 "$tls/privkey.pem"
if [[ "$(uname -s)" == Linux ]]; then
  sudo chown 0:0 "$tls/fullchain.pem"; sudo chown 101:101 "$tls" "$tls/privkey.pem"
fi

password="$(openssl rand -hex 16)"
{ printf 'DB_HOST=postgres\nDB_PORT=5432\nDB_NAME=codemastery\nDB_USER=cm_ci\nDB_PASSWORD=%s\n' "$password"
  printf 'JWT_SECRET=%s\nCORS_ALLOWED_ORIGINS=%s\n' "$(openssl rand -hex 32)" "$origin"
  printf 'SPRING_PROFILES_ACTIVE=prod\nSERVER_FORWARD_HEADERS_STRATEGY=NONE\n'
  printf 'STORAGE_ACCESS_KEY=unused-in-this-test\nSTORAGE_SECRET_KEY=unused-in-this-test\n'
} > "$work/backend.env"
chmod 600 "$work/backend.env"

docker network create "$net" >/dev/null
docker run -d --name "$prefix-postgres" --network "$net" --network-alias postgres \
  --tmpfs /var/lib/postgresql/data:rw,size=256m \
  -e POSTGRES_DB=codemastery -e POSTGRES_USER=cm_ci -e POSTGRES_PASSWORD="$password" \
  postgres:17.11-bookworm >/dev/null
for _ in $(seq 1 60); do
  docker exec "$prefix-postgres" pg_isready -U cm_ci -d codemastery >/dev/null 2>&1 && break
  sleep 1
done
docker run -d --name "$prefix-backend" --network "$net" --network-alias backend \
  --env-file "$work/backend.env" "$be_image" >/dev/null
docker run -d --name "$prefix-frontend" --network "$net" --network-alias "$host" \
  -v "$tls:/etc/nginx/tls:ro" "$fe_image" >/dev/null
# Read the whole log before matching: `docker logs | grep -q` fails under pipefail
# (docker logs gets SIGPIPE, status 141) once the log outgrows the pipe buffer.
backend_started() { local logs; logs="$(docker logs "$prefix-backend" 2>&1)" && [[ "$logs" == *'Started CodeMasteryApplication'* ]]; }
backend_failed() {
  echo "backend did not start: $1" >&2
  docker inspect -f 'state={{.State.Status}} exit={{.State.ExitCode}} oom={{.State.OOMKilled}} error={{.State.Error}} started={{.State.StartedAt}} finished={{.State.FinishedAt}}' "$prefix-backend" >&2 || true
  docker logs --tail 80 "$prefix-backend" >&2 2>&1 || true
  exit 1
}
started=no
for _ in $(seq 1 180); do
  if backend_started; then started=yes; break; fi
  [[ "$(docker inspect -f '{{.State.Running}}' "$prefix-backend")" == true ]] || backend_failed "the container exited during startup"
  sleep 1
done
# One last read after the final sleep, so a marker logged during it still counts.
[[ "$started" == yes ]] || backend_started || backend_failed "no 'Started CodeMasteryApplication' within 180 s"

fail() { echo "FAIL: $*" >&2; docker logs "$prefix-backend" 2>&1 | tail -15 >&2; exit 1; }
pass() { printf '  ok: %s\n' "$*"; }
request() {  # method path [curl args...] -> STATUS TYPE HEADERS BODY
  local method="$1" path="$2" out; shift 2
  out="$(docker run --rm --network "$net" -v "$work/trust:/trust:ro" --entrypoint curl "$fe_image" \
    --silent --show-error --cacert /trust/root.pem --dump-header - \
    --write-out '\n%{http_code} %{content_type}' -X "$method" "$@" "$origin:8080$path" 2>&1)" \
    || fail "request $method $path failed: $out"
  STATUS="$(printf '%s\n' "$out" | tail -1 | cut -d' ' -f1)"
  TYPE="$(printf '%s\n' "$out" | tail -1 | cut -d' ' -f2- | cut -d';' -f1)"
  HEADERS="$(printf '%s\n' "$out" | tr -d '\r' | sed -n '1,/^$/p')"
  BODY="$(printf '%s\n' "$out" | tr -d '\r' | sed '1,/^$/d' | sed '$d')"
}
expect() { [[ "$2" == "$3" ]] || fail "$1: expected '$3', got '$2'"; pass "$1 ($2)"; }
contains() { [[ "$2" == *"$3"* ]] || fail "$1: '$3' not found in: $2"; pass "$1"; }
lacks() { [[ "$2" != *"$3"* ]] || fail "$1: unexpected '$3' in: $2"; pass "$1"; }
json='Content-Type: application/json'
credentials='{"email":"ingress.learner@example.test","password":"Correct-Horse-Battery-2026"}'

echo "== same-origin browser writes carry Origin; CORS is bound to the exact HTTPS origin"
request POST /api/auth/register -H "$json" -H "Origin: $origin" \
  --data '{"email":"ingress.learner@example.test","password":"Correct-Horse-Battery-2026","displayName":"Ingress Learner"}'
expect "register through the ingress (approved Origin)" "$STATUS" 201
request POST /api/auth/register -H "$json" -H 'Origin: https://evil.example' \
  --data '{"email":"other.learner@example.test","password":"Correct-Horse-Battery-2026","displayName":"Other"}'
expect "register from a foreign Origin is refused by backend CORS" "$STATUS" 403
# The allowlisted preflight inputs reach Spring's CORS processing: without
# them an OPTIONS request would fall through to default-deny (401).
request OPTIONS /api/courses/3f1d2c4b-0000-4000-8000-000000000001/membership -H "Origin: $origin" \
  -H 'Access-Control-Request-Method: PUT' -H 'Access-Control-Request-Headers: authorization'
expect "CORS preflight from the approved origin" "$STATUS" 200
contains "preflight allows exactly the approved origin" "$HEADERS" "Access-Control-Allow-Origin: $origin"
request OPTIONS /api/courses/3f1d2c4b-0000-4000-8000-000000000001/membership -H 'Origin: https://evil.example' \
  -H 'Access-Control-Request-Method: PUT'
expect "CORS preflight from a foreign origin is refused" "$STATUS" 403
request POST /api/auth/login -H "$json" -H "Origin: $origin" --data "$credentials"
expect "login through the ingress" "$STATUS" 200
expect "login content type" "$TYPE" application/json
contains "login Cache-Control preserved" "$HEADERS" "Cache-Control: no-store"
token="$(printf '%s' "$BODY" | sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p')"
[[ -n "$token" ]] || fail "no access token in the login response"
pass "bearer token issued"

echo "== REST through the ingress keeps the /api prefix in paths and ProblemDetail"
request GET '/api/courses?page=0&size=20'
expect "public catalogue" "$STATUS" 200; expect "catalogue content type" "$TYPE" application/json
missing=3f1d2c4b-0000-4000-8000-0000000000ff
request GET "/api/courses/$missing"
expect "missing Course" "$STATUS" 404; expect "missing Course content type" "$TYPE" application/problem+json
contains "ProblemDetail instance is the original /api path" "$BODY" "\"instance\":\"/api/courses/$missing\""

echo "== caller-supplied forwarding, routing and method claims cannot steer the backend"
request GET "/api/courses/$missing" -H 'X-Forwarded-Prefix: /evil' -H 'X-Forwarded-Host: evil.example' \
  -H 'X-Forwarded-Proto: http' -H 'X-Forwarded-Port: 80' -H 'Forwarded: for=198.51.100.7;proto=http;host=evil.example' \
  -H 'X-Forwarded-Uri: /actuator/health' -H 'X-Forwarded-Path: /actuator/health' \
  -H 'X-Original-URL: /actuator/health' -H 'X-Rewrite-URL: /actuator/health'
expect "missing Course with forged forwarding/routing headers" "$STATUS" 404
expect "forged headers keep the ProblemDetail type" "$TYPE" application/problem+json
contains "forged prefix/host/URL headers leave the instance path unchanged" "$BODY" "\"instance\":\"/api/courses/$missing\""
lacks "forged headers expose no health response" "$BODY" '"status":"UP"'
request GET '/api/courses?page=0&size=20' -H 'X-HTTP-Method-Override: POST'
expect "method override on the public catalogue keeps GET" "$STATUS" 200
expect "method override keeps the catalogue representation" "$TYPE" application/json

request GET /api/me/courses
expect "protected route without a token" "$STATUS" 401
expect "401 content type" "$TYPE" application/problem+json
contains "WWW-Authenticate preserved" "$HEADERS" "WWW-Authenticate: Bearer"
request GET /api/me/courses -H "Authorization: Bearer $token"
expect "protected route with the bearer token" "$STATUS" 200
request GET /api/me/courses -H "Authorization: Bearer $token" -H 'X-HTTP-Method-Override: DELETE'
expect "method override cannot turn a protected GET into another operation" "$STATUS" 200
request GET /api/not-an-endpoint
expect "unknown API path stays a backend failure" "$STATUS" 401
lacks "unknown API path is not the SPA" "$BODY" '<div id="root">'

echo "== management and API documentation are not reachable through the ingress"
for path in /actuator/health /actuator/metrics /v3/api-docs; do
  request GET "$path"
  lacks "$path is not served by the backend" "$BODY" '"status"'
  lacks "$path exposes no OpenAPI document" "$BODY" '"openapi"'
done
# Tomcat path parameters must not turn an /api request into a management path,
# including "..;" segments that Nginx normalization removes before routing.
for path in '/api/..;/actuator/health' '/api/courses/..;/..;/actuator/health' '/api/..;/v3/api-docs' \
            '/api/x/..;/../actuator/health' '/api/x/%2e%2e;/../actuator/health' \
            '/api/x/..%3b/../actuator/health' '/api/x/..%3B/../v3/api-docs' \
            '/api/x/..;jsessionid=1/../actuator/health' '/x;/../api/courses'; do
  request GET "$path" --path-as-is
  expect "$path rejected at the ingress" "$STATUS" 400
  expect "$path rejection is Nginx's own response" "$TYPE" text/html
done
request GET '/api/courses?page=0&size=20&q=a;b'
expect "a ';' in the query reaches the real catalogue" "$STATUS" 200
expect "query ';' keeps the catalogue representation" "$TYPE" application/json

echo "== deploy.sh private backend health probe (exact command from wait_backend_health)"
probe() {  # path -> PROBE_CODE PROBE_OUT, over the private Compose network
  PROBE_OUT="$(timeout 90 docker exec "$prefix-frontend" wget -S -O - "http://backend:8080$1" 2>&1 </dev/null || true)"
  PROBE_CODE="$(printf '%s\n' "$PROBE_OUT" | grep -Eo 'HTTP/[0-9.]+ [0-9]{3}' | tail -1 | awk '{print $2}' || true)"
}
probe /actuator/health
expect "private health probe without a JWT" "$PROBE_CODE" 200
contains "health probe reports the token deploy.sh requires" "$PROBE_OUT" '"status":"UP"'
lacks "health probe exposes no components" "$PROBE_OUT" components
lacks "health probe exposes no details" "$PROBE_OUT" details
for path in /actuator/health/liveness /actuator/health/readiness /actuator/health/db /actuator/info /actuator/metrics /actuator; do
  probe "$path"
  expect "private $path keeps default-deny" "$PROBE_CODE" 401
done
docker stop "$prefix-postgres" >/dev/null
probe /actuator/health
expect "private health probe reports a stopped PostgreSQL" "$PROBE_CODE" 503
lacks "unhealthy dependency is not reported UP" "$PROBE_OUT" '"status":"UP"'
echo "INGRESS_BACKEND_TESTS_PASS"
