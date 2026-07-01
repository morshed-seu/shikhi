# 40 — Architecture: High-Level Design (HLD)

**Project:** Shikhi (শিখি — "I learn")
**Document type:** High-Level Design
**Author role:** Architect
**Status:** DRAFT — Phase B; reviewed at **Gate B**
**Version:** 0.1
**Builds on:** `00`–`30` (decisions D1–D8; NFRs)

> **How to read this (for a non-specialist):** The HLD is the "map from orbit." It shows
> the major pieces of the system, how they talk to each other, what technology each uses,
> and how it's deployed and scaled — **without** the fine detail of tables and endpoints
> (that's the LLD, doc 41). Big technology choices are only *summarized* here; each is
> justified in its own **ADR** (doc 42 set). Terms are defined in §12.

---

## 1. Architectural drivers (what shapes the design)

| Driver | Source | Consequence |
|---|---|---|
| Scale-ready, reliable, cost-disciplined | BO-4, NFR-S/A/C | Stateless services, horizontal scale, caching, managed infra |
| Beginner/solo builder | CO-3, R8 | **Modular monolith** (not microservices); managed services; simple ops |
| AI deferred but designed-for | D4, BR-11, NFR-M6 | **Grading-strategy seam**; async-capable grading path |
| Multi-method identity, extensible | D5 | **Identity-provider abstraction**; token-based stateless auth |
| Bilingual + resilient on poor networks | D1, D2 | SPA + CDN, i18n framework, lean payloads, idempotent sync |
| Broad content, pilot-first | D3/D6 | First-class **content model + authoring/versioning**; cacheable read-heavy content |
| Quality-first, no hard budget | D8 | Room for proper testing, observability, IaC |

**Chosen style:** a **modular monolith** — one deployable backend internally divided into
strong modules with explicit boundaries, designed so a module can later be extracted into
its own service if scale demands. This gives scale-readiness *and* low operational
complexity for a solo/beginner builder (see ADR-0001).

---

## 2. System context (who/what interacts)

```
        ┌─────────────┐        ┌──────────────┐        ┌───────────────────┐
        │  Learner    │        │ Content      │        │ Operator / You    │
        │ (mobile web)│        │ author       │        │ (ops dashboards)  │
        └──────┬──────┘        └──────┬───────┘        └─────────┬─────────┘
               │ HTTPS                │ HTTPS                     │
               ▼                      ▼                           ▼
        ┌───────────────────────────────────────────────────────────────┐
        │                     SHIKHI PLATFORM                            │
        │   React SPA (CDN)  ─▶  Spring Boot modular monolith (API)      │
        │                         ├─ PostgreSQL   ├─ Redis (cache/…)     │
        └───────────────────────────────────────────────────────────────┘
               │                       │                        │
               ▼                       ▼                        ▼
        ┌────────────┐        ┌─────────────────┐      ┌──────────────────┐
        │ Email/SMS  │        │ Social IdP       │      │ Claude API       │
        │ providers  │        │ (Google OAuth)   │      │ (FUTURE — D4)    │
        └────────────┘        └─────────────────┘      └──────────────────┘
```

**External systems:** email provider (verification/reset), SMS provider (phone OTP),
Google OAuth (social login), and — **later only** — the Anthropic Claude API for AI
grading. All are behind abstractions so they can be swapped or phased.

---

## 3. Logical architecture (containers & responsibilities)

```
┌───────────────────────────── Client tier ──────────────────────────────┐
│ React + Vite + TypeScript SPA                                           │
│  • Lesson player, curriculum map, auth screens, dashboard, author UI    │
│  • i18n (BN/EN), server-state cache, offline-resilient sync buffer      │
│  • Served as static assets via CDN                                      │
└─────────────────────────────────────────────────────────────────────────┘
                    │ JSON over HTTPS (REST, OpenAPI contract)
                    ▼
┌───────────────────────────── API / app tier ───────────────────────────┐
│ Spring Boot modular monolith (stateless, horizontally scalable)         │
│                                                                         │
│  Cross-cutting: security · i18n · validation · observability · errors   │
│                                                                         │
│  Modules (strong boundaries, explicit interfaces):                      │
│   ┌────────────┐ ┌────────────┐ ┌────────────┐ ┌────────────┐          │
│   │ identity   │ │ content    │ │ learning   │ │ grading    │          │
│   │ auth/accts │ │ curriculum │ │ lesson orch│ │ strategy   │          │
│   │ (D5 abstr.)│ │ author/ver │ │            │ │ seam (D4)  │          │
│   └────────────┘ └────────────┘ └────────────┘ └────────────┘          │
│   ┌────────────┐ ┌────────────┐ ┌────────────┐                         │
│   │ progress   │ │ review     │ │ admin/ops  │                         │
│   │ xp/streak/ │ │ spaced-rep │ │ authoring, │                         │
│   │ hearts/sync│ │            │ │ health     │                         │
│   └────────────┘ └────────────┘ └────────────┘                         │
└─────────────────────────────────────────────────────────────────────────┘
        │ JPA/SQL                 │ cache/rate/OTP/session      │ (future) SDK
        ▼                         ▼                             ▼
┌────────────────┐      ┌────────────────────┐      ┌──────────────────────┐
│ PostgreSQL     │      │ Redis               │      │ Claude API (future)  │
│ system of      │      │ cache · rate-limit  │      │ AiGradingStrategy    │
│ record         │      │ · OTP · ephemeral   │      │ behind grading seam  │
│ (+read replica │      │   tokens · queue    │      └──────────────────────┘
│  ready)        │      └────────────────────┘
└────────────────┘
```

**Module responsibilities (summary):**

| Module | Owns | Notes |
|---|---|---|
| **identity** | Sign-up/login (email/phone/social), tokens, profile, account deletion | Identity-provider abstraction (D5); stateless JWT + refresh |
| **content** | Levels/units/lessons/exercises, authoring, versioning, validation, publish | Read-heavy → cached; author tooling |
| **learning** | Orchestrates a lesson session; serves exercises; records outcomes | Depends on grading + progress |
| **grading** | `GradingStrategy` seam; `RuleBasedStrategy` (v1) | AI strategy added later (D4) with fallback |
| **progress** | XP, streak, hearts, unlocking, cross-device sync (idempotent) | Data-integrity critical (NFR-DI1) |
| **review** | Spaced-repetition scheduling & sessions | Leitner-style v1 |
| **admin/ops** | Authoring surfaces, health, operational endpoints | Role-gated |

Modules communicate **in-process via interfaces** (not network calls). Enforcing the
boundaries (e.g., with Spring Modulith) keeps future extraction cheap (ADR-0001).

---

## 4. Technology stack (summarized; each in an ADR)

| Layer | Choice (proposed) | ADR |
|---|---|---|
| Frontend | React 18 + Vite + TypeScript; React Router; TanStack Query (server state); react-i18next (BN/EN); lightweight client state | ADR-0007 |
| Backend | Java 21 (LTS) + Spring Boot 3.x; Spring Web, Spring Data JPA, Spring Security, Bean Validation; **Spring Modulith** for boundaries | ADR-0001, 0002 |
| Build | Gradle (backend), Vite/npm (frontend) | ADR-0002 |
| Database | PostgreSQL 16 (managed); **Flyway** migrations | ADR-0003 |
| Cache/ephemeral | Redis (managed) — caching, rate-limit counters, OTP store, ephemeral tokens, future async queue | ADR-0004 |
| AuthN | Stateless **JWT access + refresh tokens**; multi-method behind identity abstraction; social via OAuth2; OTP via SMS provider | ADR-0005 |
| AI seam | `GradingStrategy` interface; future `AiGradingStrategy` via **Anthropic Java SDK**, model `claude-opus-4-8`, structured outputs, caching + fallback | ADR-0006 |
| Observability | OpenTelemetry traces; Micrometer/Prometheus metrics; structured JSON logs; correlation IDs | ADR-0009 |
| Deployment | Containers (Docker) on a **managed container platform**; managed Postgres & Redis; CDN for SPA; **Terraform** IaC; **GitHub Actions** CI/CD | ADR-0008 |

> Cloud provider: you have no preference → ADR-0008 will **recommend** one, optimizing for
> low idle cost, managed Postgres/Redis, and simple ops for a solo builder, with a stated
> scale path.

---

## 5. Key runtime flows (high level; sequence detail in LLD)

**F1 — Play an exercise & submit an answer:**
`SPA → GET lesson (cached content) → learner answers → POST answer → learning module →
grading (RuleBasedStrategy) → verdict → progress module updates XP/hearts idempotently →
response (verdict + feedback + updated stats) → SPA renders.`

**F2 — Cross-device progress sync (resilient, D2/NFR-N2):**
`SPA buffers answers locally → POST batched, idempotent sync → progress reconciles by
idempotency key → authoritative state returned → SPA reconciles.`

**F3 — Auth (multi-method, D5):**
`SPA → identity module (email/password | phone OTP | Google OAuth) → issue JWT access +
refresh → SPA stores tokens → attaches access token to API calls → refresh on expiry.`

**F4 — Content publish (versioned, NFR-DI2):**
`Author edits draft → validation → publish new ContentVersion → cache invalidated →
learners get consistent version; in-progress sessions pinned to their version.`

**F5 — (Future) AI grading (D4):**
`Same as F1 but grading routes free-form answers to AiGradingStrategy → cache check →
Claude API (structured output) with timeout → on error/timeout, fallback to
RuleBasedStrategy → verdict shape unchanged.`

---

## 6. Data architecture (overview; ERD in LLD)

- **PostgreSQL is the system of record** for identity, content, progress, review.
- **Read-heavy content** (curriculum, lessons, exercises) is **cached in Redis** with
  explicit invalidation on publish → protects NFR-P1 and reduces DB load.
- **Read-replica-ready** access patterns (NFR-S4): reads and writes separable later.
- **Idempotency** for progress writes (NFR-A4/DI1): each answer/lesson-completion carries a
  client-generated key to prevent double-counting under retries/poor networks.
- **Content versioning** (NFR-DI2): immutable published versions; learners pinned per
  session.

---

## 7. Cross-cutting concerns

| Concern | Approach |
|---|---|
| **Security** | TLS everywhere; stateless JWT; server-side authz; validation; rate limiting (Redis); secrets in a manager. Full threat model in `50`. |
| **i18n (D1)** | react-i18next on the client (BN/EN catalogs); content carries bilingual fields; Bengali fonts bundled/verified. |
| **Resilience (D2)** | Timeouts, retries+backoff, circuit breakers on outbound calls; client sync buffer; graceful degradation. |
| **Observability** | Correlation ID per request propagated client→server→logs/traces; metrics + dashboards + SLO alerts. |
| **Error handling** | Consistent API error model (localized, machine-readable codes); no leakage of internals. |
| **Config & secrets** | Externalized config per environment; no secrets in repo (NFR-SEC6). |

---

## 8. Deployment & environments (overview; detail in `70`)

```
GitHub → CI (build, test, scan) → container registry → CD
   → dev → staging → production   (each: SPA on CDN, API containers ×N behind LB,
                                    managed Postgres + Redis, secrets manager)
```

- **≥2 API instances** in production behind a load balancer (NFR-A6); stateless (NFR-S1).
- **Single region** at launch (NFR-D6/AB-3); design does not preclude multi-region later.
- **Managed** Postgres & Redis (backups/HA handled by provider) → less ops for a solo team.
- **IaC (Terraform)** + **CI/CD (GitHub Actions)**; safe rollback (NFR-D4).

---

## 9. Scalability & reliability strategy (mapping to NFRs)

- **Scale out** the stateless API horizontally (NFR-S2); Redis cache absorbs read load
  (NFR-S5); Postgres read replicas when needed (NFR-S4).
- **Graceful degradation** (NFR-A3): future AI down ⇒ rule-based; email/SMS down ⇒
  queue+retry; cache down ⇒ serve from DB (slower, still correct).
- **Circuit breakers/timeouts** (NFR-A5) on all external calls.
- **Idempotency + backup/restore** (NFR-A4/A7/DI1) protect learner data.

---

## 10. Future-proofing seams (explicit)

| Future capability | Seam provided now |
|---|---|
| AI grading (D4) | `GradingStrategy` interface; verdict shape is strategy-agnostic (NFR-M6) |
| More auth methods (D5) | Identity-provider abstraction |
| Offline/PWA (D2) | Client kept SPA + resilient sync (not precluded) |
| Service extraction | Modular monolith with enforced boundaries (ADR-0001) |
| More locales (I5) | Externalized i18n catalogs |
| Multi-region | Stateless services + managed data tier |

---

## 11. Risks introduced/addressed at architecture level

| Risk | Handling |
|---|---|
| Modular monolith erodes into a "big ball of mud" | Enforce boundaries (Spring Modulith), ADRs, review gates |
| Managed-service lock-in | Prefer standard interfaces (SQL, Redis, OAuth), IaC; document swap cost |
| Auth complexity (D5) | Abstraction + phased delivery (email first); consider managed IdP (ADR-0005) |
| Cache/DB consistency on publish | Explicit invalidation + version pinning (F4) |
| Future AI cost/latency | Async-capable path + cache + fallback + budget (ADR-0006, NFRs) |

---

## 12. Glossary (additions)

- **Modular monolith** — a single deployable application internally split into strict
  modules; simpler to run than microservices, but organized to be split later.
- **Stateless service** — the server keeps no per-user memory between requests; any
  instance can handle any request → easy horizontal scaling.
- **JWT** — a signed token proving who you are, sent with each request (no server session
  needed).
- **Idempotency** — doing the same operation twice has the same effect as once (protects
  against double-counting on retries).
- **CDN** — a network that serves static files (JS/CSS/fonts) fast from near the user.
- **IaC (Terraform)** — infrastructure defined as code, so environments are reproducible.
- **ADR** — Architecture Decision Record: a short doc capturing one decision + why.

> **Next:** `41-architecture-lld.md` (module internals, data model/ERD, sequence diagrams,
> error/caching detail), then the ADR set, the OpenAPI contract, and `50` security/privacy.
