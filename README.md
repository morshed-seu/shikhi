# Shikhi (শিখি — "I learn")

A scale-ready, bilingual web platform that teaches **English to Bengali (বাংলা) speakers**
with a Duolingo-style learning loop. Built document-first (full SDLC): see [`docs/`](docs/).

> **Status:** Phase D · **M4 (Progress & gamification)**. Adds persistent XP, a daily streak,
> hearts spent across lessons, sequential lesson unlocking, a stats bar, and idempotent
> offline sync — on top of the M3 lesson loop (server-side grading behind a `GradingStrategy`
> seam), M2 content, and M1 auth. Next up is M5 (Learner UX & resilience); see
> [`docs/80-delivery-plan.md`](docs/80-delivery-plan.md).

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
