# ADR-0005 — Authentication: in-house with identity-provider abstraction; JWT + refresh

**Status:** Accepted (Gate B, 2026-07-01)
**Related:** D5 (multi-method identity), NFR-SEC1–SEC6, NFR-S1/S6, HLD §4, LLD §2.1, Security doc `50`

## Context
D5 requires **multiple, extensible** sign-in methods (email+password, phone+OTP, Google
social), with strong security (NFR-SEC*) and **stateless** scaling (NFR-S1). Two broad
options: build in-house on Spring Security, or delegate to a managed identity provider
(IdP) such as a hosted auth service.

## Decision
Build **in-house on Spring Security** behind an **`IdentityProvider` abstraction** (D5):
- **Access token = short-lived JWT** (stateless auth on the hot path, NFR-S1/S6).
- **Refresh token = rotating**, stored **hashed** with family/replay detection.
- Passwords hashed with a strong adaptive algorithm (**Argon2id** preferred, bcrypt
  acceptable).
- **Phone OTP** via an SMS provider; OTP + rate-limit state in Redis.
- **Google** via standard **OAuth2** (Spring Security OAuth2 client).
- Methods can be **phased** (email first) without redesign (abstraction).

**Revisit trigger:** if in-house social/OTP proves too costly to secure, a managed IdP can
be adopted *behind the same abstraction* — the app code shouldn't care.

## Consequences
- ✅ Full control, no per-MAU vendor cost, no lock-in; matches D5 extensibility.
- ✅ Stateless JWT scales cleanly; rotation limits refresh-token abuse.
- ⚠️ We own more security responsibility (verification, OTP, reset, revocation) → covered
  by the Security phase (`50`) and tests; token revocation strategy defined there.
- ⚠️ JWT revocation is non-trivial (short TTL + refresh rotation + optional deny-list).

## Alternatives considered
- **Managed IdP (Auth0/Cognito/Firebase/Keycloak):** less security burden, faster social/
  OTP, but cost, lock-in, and less control; kept as a **fallback behind the abstraction**.
- **Server-side sessions:** rejected — breaks stateless horizontal scaling (NFR-S1).
