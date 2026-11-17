#!/usr/bin/env bash
# CI regression test for job "VM HTTPS ingress" (deployment-ingress),
# step "Exercise the HTTPS ingress against a stub backend" in .github/workflows/ci-vm.yml.
# Body moved verbatim from that step's former inline `run:` script.
# CI runs it from the repository root, where its relative paths resolve:
#   bash infrastructure/vm/tests/test-ingress-stub-backend.sh
set -euo pipefail
# The frontend release image's HTTPS ingress against a stub backend.
fe_image="$FRONTEND_RELEASE_IMAGE"; local_image="$FRONTEND_LOCAL_IMAGE"
host=ingress.codemastery.test
work="$(mktemp -d)"
prefix="codemastery-ingress-${GITHUB_RUN_ID:-local}-$$"
net="$prefix-net"
cleanup() {
  docker rm -f "$prefix-backend" "$prefix-frontend" "$prefix-wronghost" "$prefix-local" >/dev/null 2>&1 || true
  docker network rm "$net" >/dev/null 2>&1 || true
  if [[ "$(uname -s)" == Linux ]]; then sudo rm -rf "$work"; else rm -rf "$work"; fi
}
trap cleanup EXIT

# Synthetic PKI shaped like the real one: root -> intermediate -> ECDSA P-256
# leaf; fullchain = leaf + intermediate; "EC PRIVATE KEY" as written by acme.sh.
pki="$work/pki"; mkdir -p "$pki"
openssl ecparam -name prime256v1 -genkey -noout -out "$pki/root.key"
openssl req -x509 -new -key "$pki/root.key" -sha256 -days 2 -subj '/CN=CodeMastery CI root' \
  -addext 'basicConstraints=critical,CA:true' -addext 'keyUsage=critical,keyCertSign,cRLSign' \
  -out "$pki/root.pem" 2>/dev/null
openssl ecparam -name prime256v1 -genkey -noout -out "$pki/int.key"
openssl req -new -key "$pki/int.key" -subj '/CN=CodeMastery CI intermediate' -out "$pki/int.csr" 2>/dev/null
printf 'basicConstraints=critical,CA:true,pathlen:0\nkeyUsage=critical,keyCertSign,cRLSign\n' > "$pki/int.ext"
openssl x509 -req -in "$pki/int.csr" -CA "$pki/root.pem" -CAkey "$pki/root.key" -CAcreateserial \
  -days 2 -sha256 -extfile "$pki/int.ext" -out "$pki/int.pem" 2>/dev/null
issue() {  # directory hostname: a serving directory laid out like /etc/codemastery/tls
  local dir="$1" name="$2"
  mkdir -m 700 "$dir"
  openssl ecparam -name prime256v1 -genkey -noout -out "$dir/privkey.pem"
  openssl req -new -key "$dir/privkey.pem" -subj "/CN=$name" -out "$pki/$name.csr" 2>/dev/null
  printf 'subjectAltName=DNS:%s\nbasicConstraints=CA:false\nkeyUsage=critical,digitalSignature\nextendedKeyUsage=serverAuth\n' \
    "$name" > "$pki/$name.ext"
  openssl x509 -req -in "$pki/$name.csr" -CA "$pki/int.pem" -CAkey "$pki/int.key" -CAcreateserial \
    -days 2 -sha256 -extfile "$pki/$name.ext" -out "$pki/$name.pem" 2>/dev/null
  cat "$pki/$name.pem" "$pki/int.pem" > "$dir/fullchain.pem"
  chmod 644 "$dir/fullchain.pem"; chmod 400 "$dir/privkey.pem"
  # Real layout: directory and key owned by the Nginx UID 101, chain by root.
  if [[ "$(uname -s)" == Linux ]]; then
    sudo chown 0:0 "$dir/fullchain.pem"; sudo chown 101:101 "$dir" "$dir/privkey.pem"
  fi
}
issue "$work/tls" "$host"
issue "$work/tls-wrong" other.codemastery.test
mkdir -p "$work/trust"; cp "$pki/root.pem" "$work/trust/root.pem"; chmod 644 "$work/trust/root.pem"

# Stub backend: echoes what reaches it and returns fixed API outcomes.
mkdir -p "$work/stub"
cat > "$work/stub/default.conf" <<'NGINX'
server {
    listen 8080;
    default_type text/plain;
    location = /api { return 200 "reached uri=$request_uri\n"; }
    location /api/echo {
        return 200 "reached uri=$request_uri method=$request_method host=$http_host auth=$http_authorization origin=$http_origin accept=$http_accept ctype=$http_content_type clen=$http_content_length acrm=$http_access_control_request_method acrh=$http_access_control_request_headers forwarded=$http_forwarded xff=$http_x_forwarded_for xfh=$http_x_forwarded_host xfproto=$http_x_forwarded_proto xfport=$http_x_forwarded_port xfprefix=$http_x_forwarded_prefix xfserver=$http_x_forwarded_server xfssl=$http_x_forwarded_ssl xrealip=$http_x_real_ip xfscheme=$http_x_forwarded_scheme xfprotocol=$http_x_forwarded_protocol xfuri=$http_x_forwarded_uri xfpath=$http_x_forwarded_path xfother=$http_x_forwarded_codemastery_probe xorigurl=$http_x_original_url xrewriteurl=$http_x_rewrite_url xclientip=$http_x_client_ip trueclientip=$http_true_client_ip xorigxff=$http_x_original_forwarded_for override=$http_x_http_method_override cookie=$http_cookie proxyauth=$http_proxy_authorization ua=$http_user_agent end\n";
    }
    location = /api/problem-401 {
        default_type application/problem+json;
        add_header WWW-Authenticate 'Bearer' always;
        add_header Cache-Control 'no-store' always;
        return 401 '{"type":"about:blank","status":401}';
    }
    location = /api/problem-404 { default_type application/problem+json; return 404 '{"type":"about:blank","status":404}'; }
    location = /api/server-error { default_type application/problem+json; return 500 '{"type":"about:blank","status":500}'; }
    location = /api/json { default_type application/json; return 200 '{"ok":true}'; }
    # ADR014 cookie operations and a non-cookie Auth path: echo what reaches them.
    location /api/auth/ {
        add_header Set-Cookie '__Host-cm_refresh=rotated; Path=/; Max-Age=60; Secure; HttpOnly; SameSite=Strict' always;
        return 200 "reached uri=$request_uri auth=$http_authorization origin=$http_origin cookie=$http_cookie sfs=$http_sec_fetch_site sfm=$http_sec_fetch_mode xff=$http_x_forwarded_for end\n";
    }
    location / { return 200 "reached backend-non-api uri=$request_uri\n"; }
}
NGINX
chmod 755 "$work/stub"; chmod 644 "$work/stub/default.conf"

docker network create "$net" >/dev/null
start_stub() {
  docker run -d --name "$prefix-backend" --network "$net" --network-alias backend \
    -v "$work/stub/default.conf:/etc/nginx/conf.d/default.conf:ro" "$local_image" >/dev/null
}
start_stub
# The release image exactly as deploy.sh runs it: no command, entrypoint or
# environment override; read-only directory bind. Only the test trust anchor is
# added, through curl's CA-bundle variable, because the PKI is synthetic.
docker run -d --name "$prefix-frontend" --network "$net" --network-alias "$host" \
  -v "$work/tls:/etc/nginx/tls:ro" -v "$work/trust:/trust:ro" -e CURL_CA_BUNDLE=/trust/root.pem \
  "$fe_image" >/dev/null

fail() { echo "FAIL: $*" >&2; docker logs "$prefix-frontend" 2>&1 | tail -20 >&2; exit 1; }
pass() { printf '  ok: %s\n' "$*"; }
client() {  # curl from a separate container of the same image on the private network
  docker run --rm --network "$net" -v "$work/trust:/trust:ro" --entrypoint curl "$fe_image" \
    --silent --show-error --cacert /trust/root.pem "$@"
}
request() {  # path [curl args...] -> STATUS, TYPE, BODY, HEADERS
  local path="$1" out; shift
  out="$(client --dump-header - --write-out '\n%{http_code} %{content_type}' "$@" "https://$host:8080$path" 2>&1)" \
    || fail "request $path failed: $out"
  STATUS="$(printf '%s\n' "$out" | tail -1 | cut -d' ' -f1)"
  TYPE="$(printf '%s\n' "$out" | tail -1 | cut -d' ' -f2- | cut -d';' -f1)"
  HEADERS="$(printf '%s\n' "$out" | tr -d '\r' | sed -n '1,/^$/p')"
  BODY="$(printf '%s\n' "$out" | tr -d '\r' | sed '1,/^$/d' | sed '$d')"
}
expect() { [[ "$2" == "$3" ]] || fail "$1: expected '$3', got '$2'"; pass "$1 ($2)"; }
contains() { [[ "$2" == *"$3"* ]] || fail "$1: '$3' not found in: $2"; pass "$1"; }
lacks() { [[ "$2" != *"$3"* ]] || fail "$1: unexpected '$3' in: $2"; pass "$1"; }
spa_marker='<div id="root">'

echo "== image configuration and startup"
docker exec "$prefix-frontend" nginx -t >/dev/null 2>&1 || fail "nginx -t"
pass "nginx -t with the mounted serving directory"
health=""
for _ in $(seq 1 60); do
  health="$(docker inspect --format '{{.State.Health.Status}}' "$prefix-frontend")"
  [[ "$health" == starting ]] || break
  sleep 1
done
expect "baked HTTPS healthcheck with a trusted chain" "$health" healthy
docker exec -e CURL_CA_BUNDLE= "$prefix-frontend" /usr/local/bin/codemastery-https-healthcheck >/dev/null 2>&1 \
  && fail "healthcheck accepted a certificate outside the image CA store"
pass "healthcheck rejects an untrusted chain (image CA store only)"
docker run -d --name "$prefix-wronghost" --network "$net" \
  -v "$work/tls-wrong:/etc/nginx/tls:ro" -v "$work/trust:/trust:ro" -e CURL_CA_BUNDLE=/trust/root.pem \
  "$fe_image" >/dev/null
sleep 2
docker exec "$prefix-wronghost" /usr/local/bin/codemastery-https-healthcheck >/dev/null 2>&1 \
  && fail "healthcheck accepted a trusted certificate for another hostname"
pass "healthcheck rejects a trusted certificate for another hostname"
docker rm -f "$prefix-wronghost" >/dev/null

echo "== TLS listener: protocols, SNI and Host"
for version in 1.2 1.3; do
  request / --tlsv"$version" --tls-max "$version"
  expect "TLS $version handshake and SPA response" "$STATUS" 200
done
out="$(client --tlsv1.1 --tls-max 1.1 --ciphers 'DEFAULT:@SECLEVEL=0' -o /dev/null "https://$host:8080/" 2>&1 || true)"
contains "TLS 1.1 refused by the server" "$out" "alert protocol version"
out="$(client -o /dev/null "https://$(docker inspect --format "{{(index .NetworkSettings.Networks \"$net\").IPAddress}}" "$prefix-frontend"):8080/" 2>&1 || true)"
contains "no SNI (IP address): handshake refused" "$out" "unrecognized name"
out="$(client -o /dev/null --resolve "other.codemastery.test:8080:$(docker inspect --format "{{(index .NetworkSettings.Networks \"$net\").IPAddress}}" "$prefix-frontend")" \
  https://other.codemastery.test:8080/ 2>&1 || true)"
contains "unexpected SNI: handshake refused" "$out" "unrecognized name"
request /api/echo -H 'Host: evil.example'
expect "unexpected Host after a valid handshake" "$STATUS" 421
lacks "unexpected Host never reaches the backend" "$BODY" reached
request / -H 'Host: 203.0.113.10'
expect "IP-literal Host" "$STATUS" 421
out="$(client -o - --write-out ' %{http_code}' "http://$host:8080/api/echo" 2>&1 || true)"
contains "plaintext HTTP on the TLS port is rejected" "$out" " 400"
lacks "plaintext HTTP never reaches the backend" "$out" reached

echo "== SPA and static files"
request /
expect "/ status" "$STATUS" 200; expect "/ content type" "$TYPE" text/html; contains "/ is the SPA shell" "$BODY" "$spa_marker"
request /courses/3f1d2c4b-0000-4000-8000-000000000001
expect "client deep link" "$STATUS" 200; contains "deep link loads the SPA" "$BODY" "$spa_marker"
request /profile
expect "nested client route" "$STATUS" 200
request /assets/does-not-exist.js
expect "missing build asset" "$STATUS" 404; lacks "missing asset is not the SPA" "$BODY" "$spa_marker"
request /missing-image.png
expect "missing root static file" "$STATUS" 404
request /favicon.svg
expect "existing public file" "$STATUS" 200
asset="$(docker exec "$prefix-frontend" sh -c 'ls /usr/share/nginx/html/assets | head -1')"
request "/assets/$asset"
expect "existing build asset" "$STATUS" 200

echo "== /api proxy: prefix, query, statuses, content types and headers"
request '/api'
expect "exact /api" "$STATUS" 200; expect "exact /api forwarded unchanged" "$BODY" "reached uri=/api"
request '/api?probe=1'
expect "exact /api with query" "$BODY" "reached uri=/api?probe=1"
request '/api/echo/a%2Fb?q=caf%C3%A9&page=0&size=20&prerequisites=any'
contains "/api/ prefix and raw query preserved" "$BODY" "reached uri=/api/echo/a%2Fb?q=caf%C3%A9&page=0&size=20&prerequisites=any"
# Only the allowlisted request headers pass (CT17 P7). Every other caller
# header is dropped, including identity, routing and method-override
# claims, and an X-Forwarded-* name that no list could anticipate.
request /api/echo -X POST -H 'Authorization: Bearer test-token' -H "Origin: https://$host" \
  -H 'Content-Type: application/json' -H 'Accept: application/json, application/problem+json' \
  -H 'Access-Control-Request-Method: PUT' -H 'Access-Control-Request-Headers: authorization' \
  --data '{"probe":1}' \
  -H 'Forwarded: for=198.51.100.7;proto=http;host=evil.example' -H 'X-Forwarded-For: 198.51.100.7' \
  -H 'X-Forwarded-Host: evil.example' -H 'X-Forwarded-Proto: http' -H 'X-Forwarded-Port: 80' \
  -H 'X-Forwarded-Prefix: /evil' -H 'X-Forwarded-Server: evil' -H 'X-Forwarded-Ssl: off' -H 'X-Real-IP: 198.51.100.7' \
  -H 'X-Forwarded-Scheme: http' -H 'X-Forwarded-Protocol: http' -H 'X-Forwarded-Uri: /actuator/health' \
  -H 'X-Forwarded-Path: /actuator' -H 'X-Forwarded-Codemastery-Probe: evil' \
  -H 'X-Original-URL: /actuator/health' -H 'X-Rewrite-URL: /actuator/health' -H 'X-Client-IP: 198.51.100.7' \
  -H 'True-Client-IP: 198.51.100.7' -H 'X-Original-Forwarded-For: 198.51.100.7' -H 'X-HTTP-Method-Override: DELETE' \
  -H 'Cookie: session=evil' -H 'Proxy-Authorization: Basic ZXZpbDpldmls'
contains "method preserved" "$BODY" "method=POST"
contains "Authorization forwarded to the private backend" "$BODY" "auth=Bearer test-token"
contains "Origin forwarded for backend CORS" "$BODY" "origin=https://$host"
contains "Accept forwarded" "$BODY" "accept=application/json, application/problem+json"
contains "Content-Type forwarded" "$BODY" "ctype=application/json"
contains "request body framing preserved" "$BODY" "clen=11"
contains "CORS preflight method forwarded" "$BODY" "acrm=PUT"
contains "CORS preflight headers forwarded" "$BODY" "acrh=authorization"
contains "Host to the backend is the private upstream" "$BODY" "host=backend:8080"
for header in forwarded xff xfh xfproto xfport xfprefix xfserver xfssl xrealip \
              xfscheme xfprotocol xfuri xfpath xfother xorigurl xrewriteurl xclientip trueclientip xorigxff \
              override cookie proxyauth ua; do
  contains "caller-supplied $header dropped" "$BODY" " $header= "
done
echo "== ADR014 refresh cookie: forwarded only to the three exact cookie operations"
for path in /api/auth/login /api/auth/refresh /api/auth/logout; do
  request "$path" -X POST -H "Origin: https://$host" -H 'Cookie: __Host-cm_refresh=presented' \
    -H 'Sec-Fetch-Site: same-origin' -H 'Sec-Fetch-Mode: cors' -H 'X-Forwarded-For: 198.51.100.7' \
    -H 'Authorization: Bearer test-token'
  contains "$path forwards the refresh cookie" "$BODY" "cookie=__Host-cm_refresh=presented "
  contains "$path forwards Sec-Fetch-Site" "$BODY" "sfs=same-origin "
  contains "$path forwards Sec-Fetch-Mode" "$BODY" "sfm=cors "
  contains "$path forwards Origin" "$BODY" "origin=https://$host "
  contains "$path keeps the rest of the allowlist" "$BODY" "auth=Bearer test-token "
  contains "$path still drops forwarding claims" "$BODY" " xff= "
  contains "$path passes Set-Cookie through" "$HEADERS" "Set-Cookie: __Host-cm_refresh=rotated; Path=/; Max-Age=60; Secure; HttpOnly; SameSite=Strict"
done
for path in /api/auth/register /api/auth/password-resets /api/auth/refresh/ /api/auth/refreshx /api/auth/login/extra; do
  request "$path" -X POST -H 'Cookie: __Host-cm_refresh=presented' -H 'Sec-Fetch-Site: same-origin' -H 'Sec-Fetch-Mode: cors'
  contains "$path is proxied" "$BODY" "reached uri=$path "
  contains "$path drops the cookie" "$BODY" " cookie= "
  contains "$path drops Sec-Fetch-Site" "$BODY" " sfs= "
done
for path in '/api/auth/refresh;x' '/api/auth/logout%3Bx' '/api/auth/login;jsessionid=1'; do
  request "$path" --path-as-is -X POST -H 'Cookie: __Host-cm_refresh=presented'
  expect "';' path parameter $path rejected at the ingress" "$STATUS" 400
  lacks "$path never reaches the backend" "$BODY" reached
done

request /api/problem-401
expect "API 401 status" "$STATUS" 401; expect "API 401 content type" "$TYPE" application/problem+json
contains "WWW-Authenticate preserved" "$HEADERS" "WWW-Authenticate: Bearer"
contains "Cache-Control preserved" "$HEADERS" "Cache-Control: no-store"
request /api/problem-404
expect "API 404 status" "$STATUS" 404; expect "API 404 content type" "$TYPE" application/problem+json
lacks "API 404 is not the SPA" "$BODY" "$spa_marker"
request /api/server-error
expect "API 500 status" "$STATUS" 500; lacks "API 500 is not the SPA" "$BODY" "$spa_marker"
request /api/json
expect "API JSON content type" "$TYPE" application/json

# Path parameters are checked on the raw request target, which is what is
# forwarded. Nginx normalization can drop a "..;" segment from the $uri it
# routes on, and can route a target that starts elsewhere into /api.
request '/api/echo?q=a%3Bb'
contains "an encoded ';' in the query is proxied" "$BODY" "reached uri=/api/echo?q=a%3Bb"
request '/api/echo?q=a;b&r=c%3bd'
contains "a literal ';' in the query is proxied" "$BODY" "reached uri=/api/echo?q=a;b&r=c%3bd"
request '/api?q=a;b'
contains "a ';' in the exact /api query is proxied" "$BODY" "reached uri=/api?q=a;b"
for path in '/api/..;/actuator/health' '/api/echo;jsessionid=x' '/api/echo%3Bx' '/api/echo%3bx' \
            '/api/x/..;/../actuator/health' '/api/x/%2e%2e;/../actuator/health' \
            '/api/x/..%3b/../actuator/health' '/api/x/..%3B/../actuator/health' \
            '/api/x/..;jsessionid=1/../actuator/health' '/api/x/y/..;/..;/../../actuator/health' \
            '/x;/../api' '/x%3B/../api/echo' '/api;/../api/echo'; do
  request "$path" --path-as-is
  expect "';' path parameter $path rejected at the ingress" "$STATUS" 400
  expect "$path rejection is Nginx's own response" "$TYPE" text/html
  lacks "$path never reaches the backend" "$BODY" reached
done

echo "== private backend surfaces are not proxied"
for path in /actuator/health /actuator/info /actuator/metrics /v3/api-docs /swagger-ui/index.html /swagger-ui.html /apiextra; do
  request "$path"
  lacks "$path does not reach the backend" "$BODY" reached
done

echo "== upstream failure stays an HTTP failure; recovery without ingress restart"
docker rm -f "$prefix-backend" >/dev/null
request /api/echo
expect "backend unavailable" "$STATUS" 502; lacks "502 is not the SPA" "$BODY" "$spa_marker"
start_stub
for _ in $(seq 1 30); do request /api/echo; [[ "$STATUS" == 200 ]] && break; sleep 1; done
expect "backend recreated: proxied again without restarting the ingress" "$STATUS" 200

echo "== local development image (default target) is unchanged"
docker run -d --name "$prefix-local" --network "$net" "$local_image" >/dev/null
sleep 1
out="$(docker run --rm --network "$net" --entrypoint curl "$fe_image" -s -o /dev/null -w '%{http_code}' "http://$prefix-local:8080/courses/x")"
expect "local image serves the SPA over plain HTTP on 8080" "$out" 200
out="$(docker run --rm --network "$net" --entrypoint curl "$fe_image" -s -o /dev/null -w '%{http_code}' "http://$prefix-local:8080/assets/missing.js")"
expect "local image missing asset" "$out" 404
expect "local image has no baked healthcheck (Compose supplies the HTTP probe)" \
  "$(docker image inspect --format '{{json .Config.Healthcheck}}' "$local_image")" null
echo "INGRESS_STUB_TESTS_PASS"
