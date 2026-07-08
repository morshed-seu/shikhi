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
> A **native Android client** (Kotlin/Compose, ADR-0012) reached learner-surface parity and a
> signed release build across gates MA0–MA4 — see [`android/`](android/README.md) and
> [`docs/21-prd-android.md`](docs/21-prd-android.md).

## Repository layout

```
backend/    Spring Boot 4 (Java 21, Gradle) — modular-monolith API   (ADR-0001/0002)
frontend/   React + Vite + TypeScript SPA (bilingual BN/EN)          (ADR-0007)
android/    Native Android client (Kotlin + Jetpack Compose)         (ADR-0012)
docs/       SDLC artifacts: vision, BRD, PRD, NFRs, architecture,
            ADRs, OpenAPI contract, security, test/infra/delivery plans
infra/      (later) Terraform IaC
docker-compose.yml   Local Postgres + Redis (for M1 onward)
.github/workflows/   CI (build/lint/test gates)
```

## Prerequisites

- **Java 21** (Temurin) · **Node 24+** · **Docker** (for the data tier, M1 onward)
- Android app only: **JDK 17+** (21 works) and the **Android SDK** — see [`android/README.md`](android/README.md)

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

**4. Android app** (optional) — native Kotlin/Compose client, standalone Gradle build:
```bash
cd android
./gradlew installDebug          # build + install the debug APK on a running emulator/device
```
The debug build talks to the host backend via `http://10.0.2.2:8080/v1/` (emulator) — for a
physical device or a LAN/hosted backend, see [`android/README.md`](android/README.md) for the
`-PapiBaseUrl` override, port forwarding, and release signing.

> Config (backend) is env-overridable with local defaults: `SHIKHI_DB_URL`,
> `SHIKHI_DB_USER`, `SHIKHI_DB_PASSWORD`, and **`SHIKHI_JWT_SECRET`** (set a strong secret
> outside local dev — the default is insecure and only lets the app boot).

## Tests & quality gates

```bash
# Backend
cd backend && ./gradlew test

# Frontend
cd frontend && npm run lint && npm run test && npm run build

# Android
cd android && ./gradlew testDebugUnitTest assembleDebug
```

CI runs these on every push/PR ([`.github/workflows/ci.yml`](.github/workflows/ci.yml)).
The backend tests use Testcontainers, so a running Docker daemon is required locally and
in CI.

## Documentation

Start at [`docs/00-vision-and-discovery.md`](docs/00-vision-and-discovery.md) and the
[delivery plan](docs/80-delivery-plan.md). Architecture decisions are in
[`docs/adr/`](docs/adr/); the API contract is
[`docs/43-api-contract.openapi.yaml`](docs/43-api-contract.openapi.yaml).

## License

Licensed under the [Apache License, Version 2.0](LICENSE) — see [`NOTICE`](NOTICE).
This license covers the code and content in this repository; it does not grant
rights to the **Shikhi** name or logo.
