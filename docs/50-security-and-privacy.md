# 50 — Security & Privacy Design (Threat Model)

**Project:** Shikhi (শিখি — "I learn")
**Document type:** Security & Privacy Design / Threat Model
**Author role:** Security Engineer
**Status:** DRAFT — Phase B; reviewed at **Gate B**
**Version:** 0.1
**Builds on:** `30-nfr.md` (NFR-SEC*, NFR-PR*), `40/41` (architecture), ADR-0005

> **How to read this (for a non-specialist):** This document answers "how could this system
> be attacked or misuse personal data, and what stops that?" It classifies the data we
> hold, maps the trust boundaries, walks threats using **STRIDE** (a standard checklist),
> specifies the authentication/authorization design, and sets privacy rules (what we
> collect, keep, and delete). It sets the security bar the build (Phase D) and tests
> (Phase C) must meet.

---

## 1. Scope & security objectives

Protect: learner accounts and personal data; content integrity; and service availability.
Objectives map to NFRs: confidentiality & integrity of user data (NFR-SEC/PR), availability
(NFR-A), and **least data collected/retained** (NFR-PR1). Target: **OWASP ASVS Level 2**
(NFR-SEC8), OWASP Top 10 coverage.

---

## 2. Assets & data classification

| Asset | Classification | Handling |
|---|---|---|
| Passwords | **Secret** | Hashed (Argon2id/bcrypt); never logged/returned |
| Refresh tokens | **Secret** | Stored **hashed**; rotated; revocable |
| Access tokens (JWT) | **Sensitive** | Short-lived; not persisted server-side |
| Email / phone | **PII** | Minimized; masked in UI; encrypted at rest (provider) |
| OAuth identifiers (Google sub) | **PII** | Stored as identity ref only |
| OTP codes | **Secret, ephemeral** | Redis, short TTL, single-use, rate-limited |
| Learner progress / stats | **Personal** | Access-controlled to the owner |
| Submitted answers | **Personal** | Minimize retention (OQ-L4); review value vs. NFR-PR1 |
| Curriculum content | **Business** | Integrity-protected; authoring role-gated |
| App secrets (DB creds, API keys, signing keys) | **Secret** | Secret manager; never in repo (NFR-SEC6) |
| Audit/event logs | **Sensitive** | No secrets/PII payloads (NFR-SEC9/O1) |

---

## 3. Trust boundaries & data flow

```
[ Learner browser ]  --TLS-->  [ CDN / SPA ]  --TLS-->  [ API (Spring) ]
       (untrusted)                                   |  ┌─ PostgreSQL (private)
                                                     |  ├─ Redis (private)
                                                     |  └─ Secret manager (private)
   External IdPs / providers (Google, SMS, email) <--TLS--> API (server-to-server)
   Claude API (FUTURE, D4)                          <--TLS--> API (server-to-server)
```

**Boundaries:** (1) browser ↔ API is the primary untrusted boundary — everything from the
client is untrusted input; (2) API ↔ data stores are private (no public exposure);
(3) API ↔ third parties are server-to-server over TLS with secrets held server-side only.
**The SPA never holds DB creds or the (future) Claude API key** — all privileged calls are
server-side.

---

## 4. STRIDE threat analysis (by area)

> **STRIDE** = Spoofing, Tampering, Repudiation, Information disclosure, Denial of service,
> Elevation of privilege.

### 4.1 Authentication (identity module)
| Threat | Vector | Mitigation |
|---|---|---|
| **S**poofing | Credential stuffing, OTP guessing, token theft | Strong hashing; rate limiting + lockout/backoff; OTP TTL+attempts+single-use; short-lived JWT; refresh rotation w/ replay detection |
| **T**ampering | Forged/altered JWT | Signed JWT (strong key in secret manager); verify signature+claims (exp, aud, iss) |
| **R**epudiation | "I didn't do that" | Audit log of auth events (login, reset, deletion) w/o secrets |
| **I**nfo disclosure | Account enumeration on register/reset/login | Uniform responses; `/password/forgot` always 202; generic login errors |
| **D**oS | OTP/email flooding, login brute force | Per-IP + per-subject rate limits (Redis); provider send caps |
| **E**levation | Privilege via forged role | Roles are server-side; never trust client-supplied role/claims for authz decisions beyond verified token subject |

### 4.2 Authorization (all modules)
- **Server-side authz on every request.** Learners may access only their own data (object-
  level checks by `userId` from the verified token — prevents **IDOR/BOLA**).
- `AUTHOR`/`ADMIN` endpoints (`/admin/content/*`) gated by role.
- Threats: **E**levation via IDOR → mitigated by mandatory ownership checks on every
  learner-scoped resource; covered by tests (Phase C).

### 4.3 Content & learning APIs
| Threat | Mitigation |
|---|---|
| **T**ampering with answers/XP (client sends "correct") | **Grading is server-side**; correctness flags never sent to client; XP/hearts computed server-side; idempotency keys prevent replay inflation (NFR-DI1) |
| **T**ampering with content (unauthorized publish) | Authoring role-gated; validation before publish; published versions immutable |
| **I**njection (SQL/NoSQL) | Parameterized queries via JPA; no string-built SQL; `JSONB` handled safely |
| **DoS** on write endpoints | Rate limiting; input size caps |

### 4.4 Web/client (SPA)
| Threat | Mitigation |
|---|---|
| **XSS** | React escaping by default; avoid `dangerouslySetInnerHTML`; sanitize any rich content; strict **Content-Security-Policy** |
| **CSRF** | Token-based auth via `Authorization` header (not cookies) reduces CSRF; if any cookie is used, `SameSite`+CSRF token |
| **Token theft (XSS)** | Minimize token exposure; short TTL; consider secure storage strategy (finalize in Phase D); refresh rotation limits blast radius |
| **Clickjacking** | `X-Frame-Options`/CSP frame-ancestors |

### 4.5 Infrastructure & data stores
- Postgres/Redis on **private networking**, not publicly reachable; least-privilege DB
  users; TLS in transit; encryption at rest (managed provider).
- Secrets in a **secret manager**; rotation supported (NFR-SEC6).
- **DoS/availability**: platform autoscale, rate limiting, timeouts/circuit breakers
  (NFR-A5); managed DDoS protection at the edge/CDN.

### 4.6 Third parties
- Email/SMS/OAuth: server-side credentials; verify OAuth tokens against the provider;
  validate webhooks/signatures where applicable; handle provider outages via
  queue/retry/degradation (NFR-A3).

---

## 5. Authentication & session design (detail; ADR-0005)

- **Access token:** short-lived JWT (e.g., ~15 min *TBC*), signed (asymmetric preferred so
  verification keys can be distributed); claims: sub, roles, exp, iat, iss, aud.
- **Refresh token:** longer-lived, **rotating**, stored **hashed** with a `family_id`;
  reuse of a rotated token ⇒ revoke the whole family (theft detection).
- **Revocation:** logout revokes the refresh token; short access TTL bounds exposure; an
  optional access-token deny-list (Redis) for emergency revocation (*decide in Phase D*).
- **Password policy:** min length + breached-password check *(TBC)*; Argon2id params tuned.
- **OTP:** 6-digit, single-use, short TTL, max attempts, rate-limited issuance.
- **OAuth (Google):** standard authorization-code flow; verify ID token; link/create
  identity.

---

## 6. Privacy design

| Principle | Implementation |
|---|---|
| **Minimization** (NFR-PR1) | Collect only email/phone/social id + learning data; **no monetization data** (D7) |
| **User rights** (NFR-PR2) | `GET /me/export` (data export); `DELETE /me` (delete/anonymize) |
| **Retention** (NFR-PR3) | Retention windows per data class; e.g., ephemeral OTP minutes; audit logs bounded; submitted-answer retention decided (OQ-L4) balancing analytics vs. minimization |
| **Deletion semantics** | Soft-delete → anonymize PII, retain only aggregate/non-identifying learning stats if needed; hard-delete per policy |
| **Guest accounts** (ADR-0011) | Guests are anonymous `users` rows (no email/PII). Guest creation is rate-limited like other `/auth/*` endpoints. Abandoned guests (never claimed) are **hard-deleted** by a daily reaper once idle past `shikhi.identity.guest-ttl` (default 30d); DB `on delete cascade` removes their progress. Claiming attaches email/password to the same row — progress is never copied. |
| **PII to LLM (FUTURE, D4)** (NFR-PR4) | When AI ships: send **only the answer + exercise context needed**; **no names/emails/identifiers**; document the data flow; prompt-injection safeguards below |
| **Transparency** | Clear privacy notice; lawful basis; jurisdiction TBC (OQ-N2) |

---

## 7. Future AI-specific risks (D4 — designed-for, not built)

| Risk | Mitigation (to apply when AI ships) |
|---|---|
| **Prompt injection** via learner-typed answers influencing the model | Treat learner text as untrusted data, not instructions; constrained prompt + **structured outputs** verdict schema; ignore/patternize free instructions; never let model output execute actions |
| **PII leakage to third party** | Minimal payload (NFR-PR4); no identifiers; DPA/terms review |
| **Over-reliance / wrong corrections** (R1) | Rule-based **fallback**; confidence gating; evaluation set (Phase C); human-reviewed prompts |
| **Cost abuse** | Caching, rate limits, budgets/alerts (NFR-C3/O5) |

---

## 8. OWASP Top 10 coverage (summary)

| OWASP | Covered by |
|---|---|
| A01 Broken Access Control | §4.2 server-side authz, ownership checks (anti-IDOR) |
| A02 Cryptographic Failures | TLS everywhere; hashing; secrets manager; encryption at rest |
| A03 Injection | Parameterized JPA; validation; output encoding |
| A04 Insecure Design | This threat model; ADRs; grading server-side |
| A05 Security Misconfiguration | IaC baselines; least privilege; secure headers/CSP |
| A06 Vulnerable Components | Dependency scanning in CI (NFR-SEC7), pinned deps |
| A07 Auth Failures | §5 auth design; rate limiting; rotation |
| A08 Integrity Failures | Signed tokens; immutable content versions; CI supply-chain checks |
| A09 Logging/Monitoring Failures | Structured audit logs + alerting (NFR-O*) |
| A10 SSRF | No user-controlled server-side fetch; allow-list any outbound |

---

## 9. Supply-chain & operational security

- **Dependency scanning** (SCA) and **static analysis** (SAST) in CI (NFR-SEC7/M5); fail on
  high-severity.
- Container base images minimal and patched; image scanning.
- Secrets never committed; pre-commit/secret-scanning in CI.
- Least-privilege cloud IAM (IaC-managed).
- Backups encrypted; restore tested (NFR-A7); incident/runbook basics in Phase E.

---

## 10. Security requirements → verification (traceability; tests in Phase C)

| Requirement | Verified by (Phase C/E) |
|---|---|
| Server-side authz / no IDOR | Authorization tests per endpoint; negative tests |
| No client-trusted grading/XP | Integration tests attempting client-forged correctness |
| Rate limiting on auth/OTP/writes | Load/abuse tests |
| Token rotation/replay detection | Auth flow tests |
| Input validation / injection / XSS | SAST + targeted security tests |
| Secrets not in repo | Secret scanning in CI |
| Dependency vulnerabilities | SCA gate in CI |
| Data export/delete correctness | Privacy-rights tests |

---

## 11. Open security questions (for Gate B)

- **OQ-S1:** Token storage strategy in the SPA (in-memory vs. storage) and exact access/
  refresh TTLs — finalize in Phase D with the frontend design.
- **OQ-S2:** Jurisdiction(s) for privacy compliance (OQ-N2) — sets concrete retention/notice
  rules.
- **OQ-S3:** Retention of `submitted_answer` long-term (OQ-L4) — analytics value vs.
  minimization.
- **OQ-S4:** Whether to adopt an emergency access-token deny-list now or rely on short TTL +
  refresh revocation.

> **This completes Phase B's artifacts.** Next is **Gate B**: your review of the HLD (40),
> LLD (41), ADRs (0001–0009), the OpenAPI contract (43), and this security/privacy design
> (50) — with the one explicit decision I need from you (cloud provider, ADR-0008). On
> approval, Phase C (Test Strategy, DevOps/Infra, Delivery Plan) begins.
