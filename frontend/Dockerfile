FROM node:22.23.3-bookworm-slim AS build

WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY tsconfig.json vite.config.ts index.html ./
COPY src/ src/
COPY public/ public/

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
