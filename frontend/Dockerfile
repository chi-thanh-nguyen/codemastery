FROM node:22.23.3-bookworm-slim AS dependencies

WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY tsconfig.json vite.config.ts index.html ./
COPY src/ src/
COPY public/ public/

# ---------------------------------------------------------------------------
# Release target for the one-VM HTTPS deployment (docs/deployment.md, DP01).
# Build it off the VM: docker build --target release \
#   --build-arg PUBLIC_ORIGIN=https://<approved-hostname> ./frontend
# The default target at the end of this file remains the local HTTP image.
# ---------------------------------------------------------------------------

# Single-origin API base (CT16/CT17 P1, B2).
FROM dependencies AS release-build
RUN VITE_API_BASE_URL=/api npm run build

# Renders the ingress for exactly one origin. PUBLIC_ORIGIN is public build
# input (https://hostname, as passed to deploy.sh --origin); it has no default.
FROM node:22.23.3-bookworm-slim AS release-config
ARG PUBLIC_ORIGIN
COPY <<'NGINX' /render/default.conf
# HTTPS ingress for the single approved origin (CT17 P1/P7, B2/B3).

# Missing or unexpected TLS server name: refuse the handshake, show no
# certificate. Requests whose Host is not the approved name also land here.
server {
    listen 8080 ssl default_server;
    ssl_reject_handshake on;
    return 421;
}

server {
    listen 8080 ssl;
    server_name @PUBLIC_HOST@;

    # Read-only certificate directory bind (CT17 B3); never part of the image.
    ssl_certificate     /etc/nginx/tls/fullchain.pem;
    ssl_certificate_key /etc/nginx/tls/privkey.pem;
    ssl_protocols       TLSv1.2 TLSv1.3;

    root  /usr/share/nginx/html;
    index index.html;

    # Resolve the private backend through Docker's embedded DNS when proxying,
    # so ingress start does not depend on backend start order and an
    # unavailable backend is an HTTP 502, not a stale address.
    resolver 127.0.0.11 valid=10s ipv6=off;
    set $backend http://backend:8080;

    # The backend ignores forwarding headers (forward-headers-strategy NONE);
    # caller-supplied values are dropped, none are added (CT17 P7).
    proxy_http_version 1.1;
    proxy_set_header Connection         "";
    proxy_set_header Forwarded          "";
    proxy_set_header X-Forwarded-For    "";
    proxy_set_header X-Forwarded-Host   "";
    proxy_set_header X-Forwarded-Port   "";
    proxy_set_header X-Forwarded-Prefix "";
    proxy_set_header X-Forwarded-Proto  "";
    proxy_set_header X-Forwarded-Server "";
    proxy_set_header X-Forwarded-Ssl    "";
    proxy_set_header X-Real-IP          "";

    # REST: exact /api and the /api/ subtree, before any SPA fallback. With no
    # URI part, proxy_pass forwards the original path and query unchanged, and
    # backend statuses, content types and headers are passed through.
    location = /api {
        proxy_pass $backend;
    }
    location ^~ /api/ {
        # Tomcat drops ";" path parameters, so "/api/..;/x" would resolve
        # outside /api behind this proxy. REST paths never contain one; the
        # query string is not checked.
        if ($uri ~ ";") {
            return 400;
        }
        proxy_pass $backend;
    }

    # A missing build asset or other file is a real 404, never index.html.
    location /assets/ {
        try_files $uri =404;
    }
    location ~ \.[A-Za-z0-9]+$ {
        try_files $uri =404;
    }

    # Client-side routes (deep links) load the SPA.
    location / {
        try_files $uri $uri/ /index.html;
    }
}
NGINX
COPY <<'SH' /render/healthcheck
#!/bin/sh
# Private probe of the TLS listener. It verifies the served chain against the
# image CA store, its validity and the approved hostname; verification is never
# disabled and no health route is added.
exec curl --fail --silent --show-error --output /dev/null \
    --proto '=https' --tlsv1.2 \
    --resolve '@PUBLIC_HOST@:8080:127.0.0.1' 'https://@PUBLIC_HOST@:8080/'
SH
# Same origin rules as deploy.sh validate_origin: a bare https://hostname.
RUN set -eu; \
    origin="${PUBLIC_ORIGIN:-}"; \
    printf '%s\n' "${origin}" | grep -Eqx 'https://[a-z0-9]([a-z0-9-]*[a-z0-9])?(\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+' \
      || { echo 'PUBLIC_ORIGIN must be a bare https://hostname origin (no port, path or wildcard).' >&2; exit 1; }; \
    host="${origin#https://}"; \
    if printf '%s\n' "${host}" | grep -Eqx '[0-9.]+'; then echo 'PUBLIC_ORIGIN must be a hostname, not an IP address.' >&2; exit 1; fi; \
    case "${host}" in (*localhost*) echo 'PUBLIC_ORIGIN must not be localhost.' >&2; exit 1 ;; esac; \
    sed -i "s/@PUBLIC_HOST@/${host}/g" /render/default.conf /render/healthcheck; \
    ! grep -q '@PUBLIC_HOST@' /render/default.conf /render/healthcheck

# The non-slim variant of the same Nginx image supplies curl, which can probe
# the listener by hostname over loopback with full certificate verification.
FROM nginxinc/nginx-unprivileged:1.30.5-alpine AS release

COPY --from=release-build /app/dist/ /usr/share/nginx/html/
COPY --from=release-config --chown=root:root --chmod=0444 \
    /render/default.conf /etc/nginx/conf.d/default.conf
COPY --from=release-config --chown=root:root --chmod=0555 \
    /render/healthcheck /usr/local/bin/codemastery-https-healthcheck
EXPOSE 8080
# Timings reuse the local Compose frontend healthcheck values.
HEALTHCHECK --interval=5s --timeout=5s --retries=12 --start-period=5s \
    CMD ["/usr/local/bin/codemastery-https-healthcheck"]

# ---------------------------------------------------------------------------
# Local development image (default target): plain HTTP on 8080, unchanged.
# ---------------------------------------------------------------------------
FROM dependencies AS build

# Optional public Vite build input; never pass secrets here.
ARG VITE_API_BASE_URL
RUN if [ -n "${VITE_API_BASE_URL:-}" ]; then \
        VITE_API_BASE_URL="$VITE_API_BASE_URL" npm run build; \
    else \
        npm run build; \
    fi

FROM nginxinc/nginx-unprivileged:1.30.5-alpine-slim AS runtime

COPY --from=build /app/dist/ /usr/share/nginx/html/
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 8080
