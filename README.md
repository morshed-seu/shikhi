# Shikhi (শিখি — "I learn")

A scale-ready, bilingual web platform that teaches **English to Bengali (বাংলা) speakers**
with a Duolingo-style learning loop. Built document-first (full SDLC): see [`docs/`](docs/).

> **Status:** Phase D · **M0 (Foundations / walking skeleton)**. The backend serves health
> endpoints and the frontend calls them; the learning features are built in later milestones
> (see [`docs/80-delivery-plan.md`](docs/80-delivery-plan.md)).

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

## Run it (M0 walking skeleton)

**Backend** (http://localhost:8080):
```bash
cd backend
./gradlew bootRun
# health:  curl http://localhost:8080/v1/health   ->  {"status":"UP","service":"shikhi"}
# ready:   curl http://localhost:8080/v1/ready
# metrics: http://localhost:8080/actuator/health , /actuator/prometheus
```

**Frontend** (http://localhost:5173) — proxies `/v1` to the backend:
```bash
cd frontend
npm install
npm run dev
# Open the app: shows the backend health status; toggle বাংলা/English.
```

## Tests & quality gates

```bash
# Backend
cd backend && ./gradlew test

# Frontend
cd frontend && npm run lint && npm run test && npm run build
```

CI runs these on every push/PR ([`.github/workflows/ci.yml`](.github/workflows/ci.yml)).

## Local data tier (M1 onward)

```bash
docker compose up -d      # Postgres:5432, Redis:6379
```

## Documentation

Start at [`docs/00-vision-and-discovery.md`](docs/00-vision-and-discovery.md) and the
[delivery plan](docs/80-delivery-plan.md). Architecture decisions are in
[`docs/adr/`](docs/adr/); the API contract is
[`docs/43-api-contract.openapi.yaml`](docs/43-api-contract.openapi.yaml).
