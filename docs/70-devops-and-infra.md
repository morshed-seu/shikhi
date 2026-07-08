# 70 — DevOps & Infrastructure

**Project:** Shikhi (শিখি — "I learn")
**Document type:** DevOps / Infrastructure Design
**Author role:** DevOps / SRE
**Status:** DRAFT — Phase C; reviewed at **Gate C**
**Version:** 0.1
**Builds on:** `30-nfr.md` (NFR-D*, A*, O*, C*), `40/41`, ADR-0008 (cloud **deferred**),
ADR-0009 (observability)

> **How to read this (for a non-specialist):** This document describes **how Shikhi is
> built, shipped, run, and monitored** — the automated pipeline from code to production, the
> environments, how infrastructure is created reproducibly, and how we watch it in
> production. Because the **cloud provider is deferred** (ADR-0008), everything here is
> written **provider-agnostically**: standard containers + managed Postgres/Redis + a CDN,
> defined as code, with a mapping table (§9) showing the equivalent service on each provider.

---

## 1. Principles

- **Everything as code:** app, infra (Terraform), and pipeline (CI config) all in the repo
  (NFR-D2).
- **Provider-agnostic building blocks:** container runtime, managed Postgres, managed
  Redis, object storage + CDN, secret manager — all have equivalents on every candidate
  provider (§9), so the choice is late-binding and reversible.
- **Immutable, reproducible builds:** versioned container images promoted through
  environments (no editing servers by hand).
- **Safe, automated, reversible releases** (NFR-D4).
- **Secure by default:** least privilege, private data tier, secrets in a manager.

---

## 2. Environments (NFR-D3)

| Env | Purpose | Data | Scale |
|---|---|---|---|
| **dev** | Local + shared dev; fast iteration | Disposable/seed | Minimal |
| **staging** | Prod-like; E2E, load, DAST, sign-off | Prod-like synthetic (no real PII) | Prod-like (smaller) |
| **production** | Live | Real | ≥2 API instances behind LB (NFR-A6) |

Each environment is isolated (separate data stores, secrets, config). Config via
environment variables; **no secrets in images or repo** (NFR-SEC6).

---

## 3. Build & package

- **Backend:** Gradle build → tests → container image (minimal, patched base; non-root
  user).
- **Frontend:** Vite build → static assets (hashed filenames) → published to object storage
  behind a **CDN** (NFR-P6).
- **Images** tagged by commit SHA + semantic version; stored in a container registry;
  **scanned** before promotion (NFR-SEC7).

---

## 4. CI/CD pipeline (NFR-D4)

```
 PR opened ──▶ CI (fast gates)
   • build (BE+FE)
   • unit + integration (Testcontainers) + contract + component tests
   • module-boundary check
   • lint / format / static analysis
   • SAST + SCA + secret scan
   • OpenAPI contract validation
   • coverage gate
        │ (all green)
        ▼
 merge to main ──▶ CD to STAGING
   • build & scan image
   • provision/verify infra (Terraform plan/apply)
   • DB migrate (Flyway) — expand/contract, backward-compatible
   • deploy (rolling) ─▶ smoke tests
   • scheduled/pre-release: E2E (Playwright), load (k6), DAST (ZAP)
        │ (sign-off — Phase E gate)
        ▼
 release ──▶ PRODUCTION
   • same image promoted (no rebuild)
   • DB migrate ─▶ deploy (rolling/blue-green) ─▶ smoke ─▶ monitor
   • automated rollback on smoke/health failure
```

**Tooling:** **GitHub Actions** (ADR-0008) for CI/CD; **Terraform** for infra; **Flyway**
for migrations. Gates defined in `60-test-strategy.md` §9.

---

## 5. Database migrations (NFR-D5, zero-downtime)

- **Flyway** versioned migrations run in the pipeline before deploy.
- **Expand/contract pattern** for zero-downtime: add columns/tables (expand) → deploy code
  that writes both → backfill → switch reads → remove old (contract) in a later release.
- Migrations are **forward-only** by default; reversible where feasible; tested on staging
  with prod-like data first.
- Backups taken before production migration (NFR-A7).

---

## 6. Infrastructure as Code (Terraform)

Provider-agnostic module layout (concrete provider bound late — §9):

```
infra/
├── modules/
│   ├── network/         # private networking for data tier
│   ├── api-runtime/     # container service (≥2 instances, autoscale)
│   ├── postgres/        # managed Postgres (backups, HA)
│   ├── redis/           # managed/serverless Redis
│   ├── cdn-static/      # object storage + CDN for SPA/fonts
│   ├── secrets/         # secret manager + access
│   └── observability/   # metrics/logs/traces/alerts wiring
└── envs/
    ├── staging/
    └── production/
```

- State stored in a remote backend (locked).
- Least-privilege IAM defined in code.
- Data stores on **private networking**; only the API runtime and CDN are internet-facing.

---

## 7. Runtime topology (production)

```
        Internet
           │
   ┌───────┴────────┐
   │      CDN        │  (SPA static assets, Bengali fonts, cache)
   └───────┬────────┘
           │ /api → 
   ┌───────┴────────┐        ┌──────────────┐
   │ Load balancer  │──────▶ │ API instances │ ×N (stateless, autoscale)
   └────────────────┘        └──────┬────────┘
                                    │ private
                        ┌───────────┼───────────┐
                        ▼           ▼           ▼
                   Postgres      Redis     Secret mgr
                  (+replica     (cache/…)
                   ready)
```

- Stateless API (NFR-S1) → horizontal autoscale (NFR-S2); ≥2 instances (NFR-A6).
- Health/readiness endpoints wired to LB/orchestrator (NFR-A2).
- Single region at launch (NFR-D6); topology unchanged for multi-region later.

---

## 8. Observability in operation (ADR-0009)

- **Metrics** (Micrometer→Prometheus-compatible): latency, throughput, errors, saturation +
  business metrics; **SLO dashboards** (availability, latency) with **error-budget-burn
  alerts** (NFR-O4).
- **Logs:** structured JSON with correlation IDs, centralized, no secrets/PII.
- **Traces:** OpenTelemetry across SPA→API→stores.
- **Alerting:** on SLO burn, elevated error rate, saturation, failed deploys, backup
  failures.
- **Synthetic monitoring:** J1/J2 critical journeys probed from outside.
- **Cost visibility** per environment (NFR-C4); hooks reserved for LLM cost (NFR-O5, future).

---

## 9. Provider mapping (late-binding — ADR-0008)

| Building block | Google Cloud (recommended) | AWS | Simplest-PaaS |
|---|---|---|---|
| Container runtime | Cloud Run | App Runner / ECS Fargate | Render/Railway/Fly service |
| Managed Postgres | Cloud SQL | RDS | Managed Postgres add-on |
| Managed/serverless Redis | Memorystore / serverless Redis | ElastiCache / serverless | Managed Redis add-on |
| Object storage + CDN | Cloud Storage + Cloud CDN | S3 + CloudFront | Platform static + CDN |
| Secret manager | Secret Manager | Secrets Manager / SSM | Platform env/secrets |
| Container registry | Artifact Registry | ECR | Platform registry |
| IaC | Terraform (google) | Terraform (aws) | Terraform/provider or platform config |

> Choosing a provider = swapping the Terraform module implementations + CI deploy target.
> **No application code changes** (containers + standard Postgres/Redis + env config).

---

## 10. Reliability & DR

- **Backups:** automated managed Postgres backups; **RPO ≤24 h, RTO ≤4 h** at launch (NFR-A7)
  — restore **tested** on staging.
- **Rollback:** redeploy previous image; DB contract-phase changes make rollback safe.
- **Degradation:** Redis down → DB; email/SMS down → queue/retry; (future) AI down →
  rule-based (NFR-A3).
- **Incident basics:** on-call/runbook + escalation defined in **Phase E**.

---

## 11. Security in the pipeline & infra (with `50`)

- Secret scanning, SAST, SCA, image scanning as **merge/promotion gates** (NFR-SEC7).
- Least-privilege cloud IAM (IaC).
- Private data tier; TLS everywhere; secrets rotated via manager.
- DAST against staging pre-release.

---

## 12. Open DevOps questions (for Gate C)

- **OQ-D1:** Confirm **GitHub** as the repo/CI host (assumed by ADR-0008).
- **OQ-D2:** When to bind the **cloud provider** (recommend: before Phase D deployment work;
  local dev needs none).
- **OQ-D3:** Managed vs. serverless **Redis** for the pilot (idle-cost trade-off, ADR-0004).
- **OQ-D4:** Log/metric **retention windows** and any budget guardrails (NFR-C1/C4).

> **Next:** `80-delivery-plan.md` — the sequencing of Phase D (epic-by-epic build), RACI,
> Definition of Ready/Done, updated risk register, and the consolidated **traceability
> matrix** (requirement → design → test) — after which we reach **Gate C** (green light to
> build).

---

## 13. Android app addendum (ADR-0012) — added 2026-07-07

- **CI:** an `android` job joins `ci.yml` alongside `backend`/`frontend`/`security`:
  setup-java (Temurin 21) + Gradle cache, `working-directory: android`, runs
  `./gradlew lint testDebugUnitTest assembleDebug`; uploads the debug APK as a build
  artifact. Path-filtered to `android/**` and `docs/43-api-contract.openapi.yaml`
  (the client is generated from the contract at build time).
- **Secrets/signing policy:** the release keystore and `keystore.properties` are **never
  committed** (gitignored; `keystore.properties.example` documents the shape). Local
  release signing reads them from outside the repo; CI release signing (later, with Play
  work) injects them via GitHub Actions secrets.
- **Release artifact:** signed APK, versioned `versionCode = 10000·major + 100·minor +
  patch`; sideload distribution for now (no store pipeline yet).
