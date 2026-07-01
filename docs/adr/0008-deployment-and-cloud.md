# ADR-0008 — Deployment target & cloud provider (recommendation)

**Status:** Accepted (Gate B, 2026-07-01) — **cloud-agnostic approach accepted; specific
provider DEFERRED to before Phase D deployment work (your call)**
**Related:** D8 (quality-first, no hard budget), NFR-C1 (low idle cost), NFR-D1–D6,
NFR-S1/S2, NFR-A6, HLD §8

## Context
You have **no cloud preference** and asked for a recommendation. Priorities: **low idle
cost** (NFR-C1/D8), **managed Postgres & Redis** (less ops for a solo/beginner team),
**standard containers** (portability, avoid deep lock-in), a clear **horizontal scale
path** (NFR-S2/A6), and **IaC + CI/CD** (NFR-D2/D4).

## Decision
Keep the app **cloud-agnostic** — Docker containers, standard PostgreSQL/Redis, config via
env, **Terraform** IaC, **GitHub Actions** CI/CD — so the provider is a *replaceable
detail*, then run it on a **fully-managed container platform with scale-to-zero**:

**Primary recommendation: Google Cloud**
- **Cloud Run** for the API (managed containers, **scales to zero** → low idle cost, easy
  horizontal scale, standard Docker → portable).
- **Cloud SQL for PostgreSQL** (managed, backups/HA — ADR-0003).
- **Serverless/managed Redis** (Memorystore, or a low-idle serverless Redis for the pilot —
  ADR-0004).
- **Cloud CDN** + object storage for the SPA/fonts (NFR-P6).

**Portability guarantee:** because we use only Docker + standard Postgres/Redis + Terraform,
moving to **AWS** (App Runner/ECS + RDS + ElastiCache + CloudFront) or a **PaaS** (Render/
Railway/Fly) later is a config/IaC change, not an app rewrite.

## Consequences
- ✅ Very low ops burden and low idle cost for the pilot; clear scale path.
- ✅ No deep lock-in (portable building blocks).
- ⚠️ Some managed services (e.g., always-on Redis) carry idle cost → mitigated by
  serverless Redis for the pilot; revisit as usage grows.
- ⚠️ Cloud Run cold starts → mitigated with a minimum-instance setting once traffic exists.

## Alternatives considered
- **AWS (App Runner/ECS…):** excellent scale story, but steeper learning curve/ops for a
  beginner; kept as a portable fallback.
- **PaaS (Render/Railway/Fly):** simplest of all for the pilot; lower ceiling and more
  lock-in; a reasonable **pilot-only** option if you prefer minimum setup — happy to switch
  the recommendation if you'd rather start there.

> **Gate B outcome:** the **cloud-agnostic approach is accepted** (Docker + standard
> Postgres/Redis + Terraform + GitHub Actions). **Provider selection is DEFERRED** — Phase C
> infra docs are written **provider-agnostically**, and the provider is chosen (Google Cloud
> recommendation stands, AWS or simplest-PaaS as alternatives) **before Phase D deployment
> work begins**. No app-code impact either way.
