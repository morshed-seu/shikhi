# ADR-0004 — Redis for caching, rate-limiting, OTP, ephemeral tokens (and future queue)

**Status:** Accepted (Gate B, 2026-07-01)
**Related:** NFR-P1, NFR-S5, NFR-SEC5, HLD §6, LLD §6

## Context
Content is read-heavy and rarely changes; we need caching to hit latency targets
(NFR-P1) and reduce DB load (NFR-S5). We also need fast counters for rate limiting
(NFR-SEC5), short-lived OTP storage, and ephemeral verification/reset tokens — all a poor
fit for the relational store.

## Decision
Use **Redis** (managed) for: curriculum/lesson **cache** (invalidated on publish),
**rate-limit** counters (TTL), **OTP** challenges (TTL), **ephemeral tokens**, and — later
— an **async queue** for AI grading/email. Cache miss or Redis outage falls back to
Postgres (correct, slower) for graceful degradation (NFR-A3).

## Consequences
- ✅ Meets read-latency and offloads the DB; simple TTL semantics for OTP/rate-limit.
- ✅ Ready to host the future async grading/email path (D4).
- ⚠️ A managed Redis has some idle cost → consider a low-idle/serverless Redis for the
  pilot (revisit in ADR-0008); outages are tolerated via DB fallback.

## Alternatives considered
- **In-process cache only:** rejected — doesn't work across horizontally-scaled instances.
- **DB-based rate limiting:** rejected — too slow/contended for hot endpoints.
