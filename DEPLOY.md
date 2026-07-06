# Deploying Shikhi free of cost

Two supported shapes. Pick one.

- **Option A — one free VM (this repo's `docker-compose.yml`).** Run the whole stack on a single
  box (e.g. an Oracle Cloud *Always Free* ARM VM). `cp .env.example .env`, fill the secrets,
  `docker compose up -d --build`, open `http://<host>:8080`. Free forever, no cold starts, but
  you manage the VM + TLS.
- **Option B — managed free tiers (below).** Zero server maintenance; the trade-off is a
  backend cold-start after idle. **This is the chosen path.**

---

## Option B: Cloudflare Pages + Render + Neon

Three services. **Redis / Upstash is NOT needed** — the backend's cache and rate-limiter are
in-process today (ADR-0004 only introduces Redis when scaling to multiple instances).

### 1. Database — Neon (Postgres 16, free)
1. Create a Neon project → copy the connection string.
2. Convert it to a JDBC URL for the backend, keeping SSL:
   `jdbc:postgresql://<host>/<db>?sslmode=require`
   Note the username/password separately.

Flyway runs the `V1…V8` migrations automatically on first boot, so the schema + seed content
build themselves — no manual SQL.

### 2. Backend — Render (free Web Service, Docker)
1. New → Web Service → connect this GitHub repo → **Root Directory: `backend`**, Runtime:
   Docker (it uses `backend/Dockerfile`).
2. Environment variables:
   | Key | Value |
   |---|---|
   | `SPRING_PROFILES_ACTIVE` | `prod` |
   | `SHIKHI_DB_URL` | `jdbc:postgresql://<neon-host>/<db>?sslmode=require` |
   | `SHIKHI_DB_USER` | Neon user |
   | `SHIKHI_DB_PASSWORD` | Neon password |
   | `SHIKHI_JWT_SECRET` | `openssl rand -base64 48` (keep secret) |
   | `SHIKHI_CORS_ORIGINS` | your Pages URL, e.g. `https://shikhi.pages.dev` |
3. Health check path: `/actuator/health`.
4. Deploy → note the URL, e.g. `https://shikhi-backend.onrender.com`.

> Free-tier backend **sleeps after ~15 min idle** → the first request wakes it (~30–60 s).
> Acceptable for a pilot.

### 3. Frontend — Cloudflare Pages (free, static)
1. New Pages project → connect the repo → **Root Directory: `frontend`**, Build command:
   `npm run build`, Output directory: `dist`.
2. Build environment variable:
   | Key | Value |
   |---|---|
   | `VITE_API_BASE_URL` | your Render URL, e.g. `https://shikhi-backend.onrender.com` |
3. Deploy. The SPA now calls the backend cross-origin; the browser is allowed because the
   backend's `SHIKHI_CORS_ORIGINS` lists the Pages origin (step 2.2). **These two must match.**

### Wiring summary (the easy thing to get wrong)
```
Cloudflare Pages (VITE_API_BASE_URL) ──► Render backend URL
Render backend (SHIKHI_CORS_ORIGINS) ──► Cloudflare Pages URL
```
If either is missing/mismatched, the browser blocks API calls with a CORS error.

> **Alternative to CORS:** instead of `VITE_API_BASE_URL`, add a Cloudflare Pages Function that
> proxies `/v1/*` to the Render backend. Then the browser stays same-origin and you can leave
> `VITE_API_BASE_URL` unset and `SHIKHI_CORS_ORIGINS` empty. Slightly more setup; skip for now.

---

## Note on the deferred decision
The devops docs deliberately left the cloud provider unbound. Choosing Option B effectively makes
that call for the pilot — worth recording as a short ADR (`docs/adr/`) so the decision is traceable.
