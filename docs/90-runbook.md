# 90 — Operations runbook (Phase E)

How to run, observe, and recover the Shikhi backend. Pairs with
[`70-devops-and-infra.md`](70-devops-and-infra.md) (topology, CI/CD) and
[`50-security-and-privacy.md`](50-security-and-privacy.md).

> Scope note: the production deployment target is still an open ADR (managed containers
> assumed). Procedures below are written to be platform-agnostic — substitute your
> orchestrator's deploy/rollback command where noted.

## 1. Service at a glance

| Thing | Value |
|---|---|
| Service | `shikhi` (Spring Boot modular monolith, stateless) |
| App port | `8080` (`/v1/**`, `/actuator/**`) |
| Profiles | default = dev; `prod` = ECS JSON logs + hardened health details |
| Data tier | PostgreSQL 16 (Flyway-migrated), Redis (cache/rate — see §7) |
| Frontend | static SPA (Vite build) served via CDN/gateway |

## 2. Required configuration (env)

All secrets come from the environment / secret manager — **never** from the repo.

| Var | Purpose | Notes |
|---|---|---|
| `SHIKHI_JWT_SECRET` | JWT signing key | **Mandatory in prod**; ≥32 bytes, high-entropy. The in-repo default is an insecure placeholder that only lets the app boot locally. |
| `SHIKHI_DB_URL` / `SHIKHI_DB_USER` / `SHIKHI_DB_PASSWORD` | Postgres connection | |
| `SHIKHI_CORS_ORIGINS` | Comma-separated SPA origins allowed cross-origin | Empty = same-origin only |
| `SHIKHI_RATE_LIMIT_*` | Auth rate-limit tuning | `ENABLED`, `CAPACITY`, `REFILL`, `PERIOD` |
| `SPRING_PROFILES_ACTIVE=prod` | Enable production overrides | Structured JSON logging |

## 3. Start / stop

**Local:**
```bash
docker compose up -d                      # Postgres:5432, Redis:6379 — wait for pg healthy
cd backend && ./gradlew bootRun           # http://localhost:8080
```
**Production (per-platform):** deploy the built image with the env above; the orchestrator
runs ≥2 replicas behind the load balancer (NFR-S1/A6). Graceful shutdown is enabled (Tomcat
drains in-flight requests).

Flyway runs migrations automatically on boot; Hibernate is `validate`-only (never mutates
schema).

## 4. Health, metrics, logs

- **Liveness/readiness (public, for probes):** `GET /actuator/health/liveness`,
  `/actuator/health/readiness`; app health `GET /v1/health`.
- **Metrics (ADMIN-only):** `GET /actuator/prometheus` — scrape with an admin token or from
  an internal-only network path. Key series: `http_server_requests_seconds`,
  `shikhi_auth_rate_limited_total`, JVM/DB pool gauges.
- **Logs:** `prod` emits ECS JSON to stdout for aggregation. Every line carries
  `correlationId`; it is also echoed to clients as `X-Correlation-Id`, so a user error
  report maps straight to server logs.

## 5. Incident playbook

| Symptom | First checks | Response |
|---|---|---|
| **5xx spike / latency SLO breach** | `http_server_requests` p95/p99; DB pool saturation; recent deploy | Scale out replicas; if deploy-correlated, **roll back** (§6); check slow queries |
| **DB unreachable** | Postgres health; connection count; disk | Fail readiness (LB stops routing); restore DB connectivity; app self-recovers (stateless) |
| **Auth abuse / brute force** | `shikhi_auth_rate_limited_total` climbing; source IPs in logs | Rate limiter already 429ing; tighten `SHIKHI_RATE_LIMIT_*`; block offending IPs at the edge |
| **Cache stale after publish** | curriculum not reflecting new content | Publish invalidates cache; if needed, restart a replica to warm-clear |
| **Secret/JWT rotation** | — | Set new `SHIKHI_JWT_SECRET`, rolling-restart; existing access tokens expire ≤15m, refresh tokens re-mint |

## 6. Rollback

**Rollback is safe by design.** All schema migrations to date (V1–V8) are **additive only**
(create/insert/index — no destructive `drop`/`alter drop`/`rename`), so the previous
application image runs against the current schema without conflict (contract-phase change).

Procedure:
1. Redeploy the previous known-good image tag (orchestrator rollback / re-point tag).
2. Confirm `/actuator/health/readiness` is `UP` on new replicas before shifting traffic.
3. Verify a smoke path: register → curriculum → start session → submit answer.
4. Do **not** roll the database back; forward-only, additive migrations mean the old code
   simply ignores newer, unused columns/tables.

> When a future migration must remove/rename, use the **expand→migrate→contract** pattern
> so every deploy stays one step backward-compatible; only then is image rollback safe.

## 7. Known limitations / follow-ups (post-pilot)

- **Rate limiter is in-process** (per instance). Correct for the pilot/single instance; for
  horizontal scale, move the token bucket to **Redis** (already provisioned) so the limit is
  global. Seam is isolated in `platform.security`.
- **Distributed tracing** (OpenTelemetry SPA→API→store) not yet wired; correlation-id log
  tracing is in place.
- **Backups:** target RPO ≤ 24 h, RTO ≤ 4 h (NFR-A7) — configure managed-Postgres automated
  backups + a restore drill in staging.
- **Container image + Java SCA scanning** run at the promotion stage of the deploy pipeline
  (docs/70 §5); CI runs secret + frontend dependency scans (see `.github/workflows/ci.yml`).
