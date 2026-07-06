# Architecture Decision Records (ADRs)

> **What is an ADR (for a non-specialist)?** A short note capturing **one significant
> technical decision**: the context, the decision, and its consequences. ADRs make choices
> explicit and reviewable, and let a future reader understand *why* something is the way it
> is. Each has a **status**: `Proposed` (awaiting Gate B), `Accepted`, `Superseded`, or
> `Deprecated`.

## Index

| ADR | Decision | Status |
|---|---|---|
| [0001](0001-modular-monolith.md) | Modular monolith (not microservices) | Accepted |
| [0002](0002-backend-java-spring-gradle.md) | Backend: Java 21 + Spring Boot + Gradle + Spring Modulith | Accepted |
| [0003](0003-postgresql-flyway.md) | PostgreSQL + Flyway migrations | Accepted |
| [0004](0004-redis.md) | Redis for cache / rate-limit / OTP / ephemeral tokens | Accepted |
| [0005](0005-authentication-strategy.md) | In-house auth with identity-provider abstraction; JWT + refresh | Accepted |
| [0006](0006-ai-grading-seam.md) | Grading-strategy seam; AI deferred behind it | Accepted |
| [0007](0007-frontend-react-vite.md) | Frontend: React + Vite + TS + supporting libraries | Accepted |
| [0008](0008-deployment-and-cloud.md) | Deployment: cloud-agnostic; **provider deferred** | Accepted (provider TBD) |
| [0009](0009-observability.md) | Observability stack (OpenTelemetry/Micrometer) | Accepted |
| [0010](0010-repository-strategy.md) | Single repository (monorepo) for backend + frontend | Accepted |
| [0011](0011-guest-learning-and-account-claim.md) | Guest learning as anonymous user, claimed (upgraded) in place — no progress migration | Accepted |

**Accepted at Gate B (2026-07-01).** ADR-0008's cloud-agnostic approach is accepted; the
**specific provider is deferred** until before Phase D deployment work (Google Cloud is the
standing recommendation).
