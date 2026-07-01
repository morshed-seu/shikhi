# 30 — Non-Functional Requirements (NFR)

**Project:** Shikhi (শিখি — "I learn")
**Document type:** Non-Functional Requirements
**Author role:** Architect
**Status:** DRAFT — final Phase-A artifact; reviewed at **Gate A**
**Version:** 0.1
**Builds on:** `00-vision-and-discovery.md`, `10-brd.md`, `20-prd.md` (D1–D8)

> **How to read this (for a non-specialist):** Functional requirements (the PRD) say
> *what the product does*. **Non-functional requirements say how *well* it must do it** —
> how fast, how reliable, how secure, how accessible, how cheap to run. These set the
> "quality bar" the architecture (Phase B) must meet and the tests (Phase C) must verify.
> Each NFR has an **ID** and, wherever possible, a **measurable target**. Targets marked
> *(TBC)* are sensible defaults to confirm at Gate A; "scale-ready" (per the brief) means
> we **design and instrument** for these even before traffic demands them.

> **Measurement note:** every target needs a *condition* (load, percentile, environment).
> Unless stated, latency targets are **server-side p95 under expected steady-state load in
> the production region**, excluding client network time and excluding any future AI call.

---

## 1. Performance & responsiveness

| ID | Requirement | Target | Notes |
|---|---|---|---|
| **NFR-P1** | Core read API latency (curriculum map, lesson fetch, progress read) | **p95 ≤ 200 ms**, p99 ≤ 400 ms (server-side) | The interactive hot path |
| **NFR-P2** | Write API latency (submit answer, complete lesson, sync progress) | **p95 ≤ 300 ms**, p99 ≤ 600 ms | Must feel instant in the lesson loop |
| **NFR-P3** | Deterministic grading time (in-process) | **≤ 50 ms** typical | No network/AI in v1; trivial compute |
| **NFR-P4** | Initial web app load (first meaningful content) on mid-range mobile, typical 3G/4G | **TTI ≤ 5 s** on a defined reference device/network *(TBC)* | Critical for the target audience (D2) |
| **NFR-P5** | Per-lesson payload size | Lean; **lesson bundle ≤ ~250 KB** transferred *(TBC)* | Small payloads for variable data (D2) |
| **NFR-P6** | Static asset delivery | Served via **CDN**, cache-friendly, compressed (gzip/br) | Fonts (Bengali) and JS/CSS |
| **NFR-P7** | (Future) AI grading latency budget | **p95 ≤ 3 s** with async UX + timeout & fallback | Applies only when E5 AI phase ships (D4) |

---

## 2. Scalability

| ID | Requirement | Target | Notes |
|---|---|---|---|
| **NFR-S1** | Backend services are **stateless** (no in-memory session affinity) | Mandatory | Enables horizontal scaling behind a load balancer |
| **NFR-S2** | Horizontal scaling of the API | Scale out to handle **10× baseline** by adding instances, no code change | "Scale-ready" intent |
| **NFR-S3** | Concurrency baseline the design must comfortably handle | **≥ 1,000 concurrent active learners** at launch capacity *(TBC)* | Sizing assumption to validate |
| **NFR-S4** | Database read scaling | Schema & access patterns **read-replica-ready**; heavy reads cacheable | Curriculum is read-heavy, write-light |
| **NFR-S5** | Caching tier | Cache hot, rarely-changing data (curriculum/content) with explicit invalidation on publish | Reduces DB load; aids NFR-P1 |
| **NFR-S6** | Statelessness of auth | Token-based (no server session store on the hot path); revocation strategy defined in Security | Supports NFR-S1/S2 |

---

## 3. Availability & reliability

| ID | Requirement | Target | Notes |
|---|---|---|---|
| **NFR-A1** | Production availability (monthly) | **≥ 99.9%** SLO *(TBC)* | ~43 min/month error budget |
| **NFR-A2** | Health & readiness endpoints | Present for every deployable; used by LB/orchestrator | NFR-A, ops |
| **NFR-A3** | Graceful degradation | If a non-core dependency fails, core learning still works | e.g., (future) AI down ⇒ rule-based grading; email provider down ⇒ queue/retry |
| **NFR-A4** | Retries & backoff | Transient downstream failures retried with exponential backoff + jitter; **idempotent** writes (answer submit, lesson complete) | Prevents double-counting XP/progress |
| **NFR-A5** | Circuit breaking & timeouts | All outbound calls have explicit timeouts; breakers on flaky deps | Prevents cascading failure (esp. future AI) |
| **NFR-A6** | No single point of failure (design intent) | ≥ 2 instances of stateless services; managed/HA data store | Scale-ready |
| **NFR-A7** | Data durability & backup | Automated backups; **RPO ≤ 24 h, RTO ≤ 4 h** at launch *(TBC)* | Tighten later as needed |

---

## 4. Network resilience (D2 — lean online + resilient)

| ID | Requirement | Target | Notes |
|---|---|---|---|
| **NFR-N1** | Tolerate slow/intermittent connections | No data loss on transient failure during a lesson; clear retry; resumable session | Directly serves the target audience |
| **NFR-N2** | Optimistic, recoverable progress sync | Local buffering of answers with reliable, idempotent server reconciliation | Pairs with NFR-A4 |
| **NFR-N3** | Minimal chattiness | Batch/prefetch lesson data to reduce round-trips on poor networks | Aids NFR-P4/P5 |
| **NFR-N4** | Offline (PWA) | **Out of scope v1**, architecture must not preclude later | Recorded per D2 |

---

## 5. Security (high-level here; full treatment in `50-security-and-privacy.md`)

| ID | Requirement | Target | Notes |
|---|---|---|---|
| **NFR-SEC1** | Transport security | **TLS everywhere**; HSTS; no plaintext | |
| **NFR-SEC2** | AuthN | Secure multi-method auth (D5); credentials hashed with a strong adaptive algorithm; OTP & OAuth handled to spec | Detail in Security phase |
| **NFR-SEC3** | AuthZ | Enforced server-side; learners access only their own data; author/admin capabilities gated by role | |
| **NFR-SEC4** | Input validation & output encoding | All inputs validated; protections against injection/XSS/CSRF | OWASP-aligned |
| **NFR-SEC5** | Rate limiting & abuse protection | On auth, OTP, and write endpoints | Cost & abuse control |
| **NFR-SEC6** | Secrets management | No secrets in code/repo; via secret manager/env; rotation possible | Incl. future Claude API key |
| **NFR-SEC7** | Dependency & supply-chain | Automated vulnerability scanning in CI; pinned/locked deps | |
| **NFR-SEC8** | Standard alignment | Target **OWASP ASVS Level 2** *(TBC)* | Verified in Security phase & tests |
| **NFR-SEC9** | Audit logging | Security-relevant events logged (auth, deletion, role actions) without sensitive payloads | |

---

## 6. Privacy & compliance

| ID | Requirement | Target | Notes |
|---|---|---|---|
| **NFR-PR1** | Data minimization | Collect only what's needed; no monetization data (D7) | |
| **NFR-PR2** | User rights | **Export** and **delete/anonymize** account data (PRD E11/US-1.6) | |
| **NFR-PR3** | Retention policy | Defined retention windows per data class; enforced | Security phase defines classes |
| **NFR-PR4** | (Future) Minimal PII to LLM | When AI ships (D4), send the **minimum** content needed; no unnecessary PII; document data flow | |
| **NFR-PR5** | Privacy-by-design alignment | GDPR-style principles (lawful basis, transparency, rights) as a baseline *(jurisdiction TBC)* | |

---

## 7. Internationalization & localization (D1)

| ID | Requirement | Target | Notes |
|---|---|---|---|
| **NFR-I1** | Full BN/EN UI | All chrome strings externalized in catalogs for both locales; runtime switch | D1 |
| **NFR-I2** | Correct Bengali rendering | Unicode, conjuncts/ligatures, and bundled/verified fonts render correctly on target devices | CO-4; tested on real devices |
| **NFR-I3** | Locale-aware formatting | Dates, numbers per locale | |
| **NFR-I4** | Bengali text input | Reliable Bengali input where learners type Bengali | |
| **NFR-I5** | Extensible i18n | Adding a locale later requires no structural change | Future optionality |

---

## 8. Accessibility

| ID | Requirement | Target | Notes |
|---|---|---|---|
| **NFR-AC1** | Standards conformance | **WCAG 2.1 AA** target *(TBC)* | |
| **NFR-AC2** | Keyboard & screen-reader | Full keyboard nav; labeled controls; focus management in the lesson player | |
| **NFR-AC3** | Contrast & sizing | Meets AA contrast; supports text scaling | |

---

## 9. Observability

| ID | Requirement | Target | Notes |
|---|---|---|---|
| **NFR-O1** | Structured logging | JSON logs with correlation IDs; no sensitive data | |
| **NFR-O2** | Metrics | Latency, throughput, error rates, saturation per service; business metrics (activation, lessons completed) | Feeds KPIs (Vision §7) |
| **NFR-O3** | Distributed tracing | Trace requests across components (e.g., OpenTelemetry) | Faster debugging |
| **NFR-O4** | Dashboards & alerting | SLO dashboards; alerts on error-budget burn, latency, availability | |
| **NFR-O5** | (Future) LLM cost/usage telemetry | Track tokens, cost, cache-hit rate, fallback rate when AI ships | Guardrail (Vision §7.3) |

---

## 10. Maintainability & quality

| ID | Requirement | Target | Notes |
|---|---|---|---|
| **NFR-M1** | Modular boundaries | Clear modules (auth, content, learning, progress, grading, admin) with explicit interfaces | Modular monolith (CO-1) |
| **NFR-M2** | API contract as source of truth | Documented contract (OpenAPI); frontend/back end conform | Produced in Phase B |
| **NFR-M3** | Automated test gates | Coverage targets + required CI checks (set in Phase C); no merge on red | |
| **NFR-M4** | Decision records | Significant decisions captured as ADRs | Phase B |
| **NFR-M5** | Code quality automation | Linting, formatting, static analysis in CI | |
| **NFR-M6** | Grading-strategy seam preserved | Adding AI strategy requires no change to lesson player/progress | SC-5, D4 |

---

## 11. Portability, deployability & operations

| ID | Requirement | Target | Notes |
|---|---|---|---|
| **NFR-D1** | Containerized builds | Reproducible container images | Phase B/C |
| **NFR-D2** | Infrastructure as Code | Environments provisioned via IaC; no manual snowflakes | |
| **NFR-D3** | Environments | At least **dev, staging, production**, isolated | |
| **NFR-D4** | CI/CD | Automated build→test→deploy with safe rollback | Phase C |
| **NFR-D5** | DB migrations | Versioned, automated, reversible-where-feasible | e.g., Flyway/Liquibase |
| **NFR-D6** | Single-region launch acceptable | Multi-region is a later scaling step | AB-3 |

---

## 12. Cost efficiency (D8 — no hard cap, but disciplined)

| ID | Requirement | Target | Notes |
|---|---|---|---|
| **NFR-C1** | Idle-cost discipline | Prefer managed services with low idle cost; scale-to-need | No hard budget (D8) but be sensible (CO-7) |
| **NFR-C2** | Caching to cut compute/DB cost | Content/curriculum cached aggressively (NFR-S5) | |
| **NFR-C3** | (Future) LLM cost controls | Prompt caching, result caching, batching, budget alerts, rule-based-first | Applies in AI phase (D4) |
| **NFR-C4** | Cost observability | Basic cost visibility per environment | |

---

## 13. Compatibility (target support matrix)

| ID | Requirement | Target | Notes |
|---|---|---|---|
| **NFR-CP1** | Browsers | Recent **Chrome/Android WebView, Firefox, Safari, Edge** (last ~2 major versions) *(TBC)* | Mobile-web priority |
| **NFR-CP2** | Devices | Usable on **mid-range Android** at common small viewport sizes | Persona Rahim |
| **NFR-CP3** | Responsive | Works phone → desktop | |

---

## 14. Data integrity

| ID | Requirement | Target | Notes |
|---|---|---|---|
| **NFR-DI1** | Accurate progress/XP/streak | No double-counting or loss under retries/poor network | Pairs with NFR-A4/N2 (idempotency) |
| **NFR-DI2** | Content versioning integrity | Learners always see a consistent, valid content version; in-progress learners handled on publish | PRD US-9.2 |
| **NFR-DI3** | Referential integrity | Enforced in the data layer | |

---

## 15. Prioritization (MoSCoW for v1 / pilot)

> So we don't gold-plate before there's signal (Vision principle "measure, then scale").

- **Must (pilot):** P1–P5, S1, S4–S6, A1–A5, A7, N1–N3, all SEC*, PR1–PR3/PR5, I1–I4,
  AC1–AC2, O1–O4, M1–M6, D1–D6, DI1–DI3, CP1–CP3.
- **Should (soon after):** S2/S3 validated under load test, A6 hardening, AC3 polish,
  C-series cost tooling.
- **Could / Later:** N4 (offline), P7/O5/PR4/C3 (AI phase), multi-region (D6→later),
  tighter RPO/RTO.

---

## 16. Open NFR questions (for Gate A)

- **OQ-N1:** Confirm headline targets marked *(TBC)*: availability SLO (99.9%?), concurrency
  baseline, TTI/payload budgets, RPO/RTO, OWASP ASVS level, WCAG level.
- **OQ-N2:** Target **jurisdiction(s)** for privacy compliance (affects NFR-PR5)?
- **OQ-N3:** Reference **device/network profile** for performance tests (which mid-range
  Android, which network speed) — needed to make NFR-P4/P5 testable.
- **OQ-N4:** Confirm **single-region** launch (NFR-D6/AB-3) and preferred region.

---

## 17. Traceability (selected)

| Source | NFRs |
|---|---|
| D1 (BN/EN) | NFR-I1–I5, AC* |
| D2 (resilient online) | NFR-N1–N4, P4, P5 |
| D4 (AI later) | NFR-P7, O5, PR4, C3, M6 |
| BO-4 (reliable & cost-controlled) | A*, S*, O*, C* |
| SC-4 (SLOs & cost) | A1, P1–P2, O4, C* |
| SC-5 (extensibility) | M6 |
| PRD US-4.4 / US-10.2 / US-10.3 | N1, I2, AC1–AC3 |

> **This completes Phase A.** Next is **Gate A**: your review of `00`, `10`, `20`, `30`
> together. On approval, Phase B (Architecture & Security) begins as the **Architect** with
> the HLD, then LLD, ADRs, the OpenAPI contract, and the security/privacy threat model.
