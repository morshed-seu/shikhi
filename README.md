# Shikhi (শিখি — "I learn")

A scale-ready, bilingual web platform that teaches **English to Bengali (বাংলা) speakers**
with a Duolingo-style learning loop. Built document-first (full SDLC): see [`docs/`](docs/).

> **Status:** Phase E · **Hardening & launch readiness — GO for pilot**. The full learning
> loop (M1–M7) is complete; Phase E added defence-in-depth security headers, CORS, auth rate
> limiting and actuator lockdown; structured JSON logging + metrics; a load test proving core
> reads at **p95 9–16 ms** (budget 200 ms); and an operations runbook + launch checklist. See
> [`docs/92-launch-checklist.md`](docs/92-launch-checklist.md), [`docs/90-runbook.md`](docs/90-runbook.md)
> and [`docs/91-performance-results.md`](docs/91-performance-results.md). Post-pilot hardening
> (staging, backups, tracing, image/SCA scanning, deploy target) is tracked in the checklist.

## Repository layout

```
backend/    Spring Boot 4 (Java 21, Gradle) — modular-monolith API   (ADR-0001/0002)
frontend/   React + Vite + TypeScript SPA (bilingual BN/EN)          (ADR-0007)
docs/       SDLC artifacts: vision, BRD, PRD, NFRs, architecture,
            ADRs, OpenAPI contract, security, test/infra/delivery plans
infra/      (later) Terraform IaC
docker-compose.yml   Local Postgres + Redis (for M1 onward)
.github/workflows/   CI (build/lint/test gates)
```

## Prerequisites

- **Java 21** (Temurin) · **Node 24+** · **Docker** (for the data tier, M1 onward)

## Run it

**1. Data tier** — the backend now needs Postgres to boot (Flyway migrations + JPA):
```bash
docker compose up -d       # Postgres:5432, Redis:6379
```

**2. Backend** (http://localhost:8080):
```bash
cd backend
./gradlew bootRun
# health:   curl http://localhost:8080/v1/health   ->  {"status":"UP","service":"shikhi"}
# register: curl -X POST http://localhost:8080/v1/auth/register -H 'Content-Type: application/json' \
#             -d '{"email":"you@example.com","password":"s3cretpassword"}'
# metrics:  http://localhost:8080/actuator/health , /actuator/prometheus
```

**3. Frontend** (http://localhost:5173) — proxies `/v1` to the backend:
```bash
cd frontend
npm install
npm run dev
# Open the app: sign up / log in, then open a lesson to play it — answer the
# exercises, see instant feedback + hearts, finish for your score. Toggle বাংলা/English.
```

> Config (backend) is env-overridable with local defaults: `SHIKHI_DB_URL`,
> `SHIKHI_DB_USER`, `SHIKHI_DB_PASSWORD`, and **`SHIKHI_JWT_SECRET`** (set a strong secret
> outside local dev — the default is insecure and only lets the app boot).

## Tests & quality gates

```bash
# Backend
cd backend && ./gradlew test

# Frontend
cd frontend && npm run lint && npm run test && npm run build
```

CI runs these on every push/PR ([`.github/workflows/ci.yml`](.github/workflows/ci.yml)).
The backend tests use Testcontainers, so a running Docker daemon is required locally and
in CI.

## Documentation

Start at [`docs/00-vision-and-discovery.md`](docs/00-vision-and-discovery.md) and the
[delivery plan](docs/80-delivery-plan.md). Architecture decisions are in
[`docs/adr/`](docs/adr/); the API contract is
[`docs/43-api-contract.openapi.yaml`](docs/43-api-contract.openapi.yaml).
