# Multi-stage build → static SPA served by nginx, which also reverse-proxies /v1 to the
# backend so the browser sees a single origin (mirrors the prod "same-origin via CDN + /v1"
# assumption; avoids CORS in the docker-compose stack).
# Build:  docker build -t shikhi-frontend ./frontend
# Note:   VITE_API_BASE_URL is intentionally left unset here → same-origin /v1 via nginx.

# --- build stage ---
FROM node:24-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

# --- run stage ---
FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
