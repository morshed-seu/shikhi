# 60 — Test Strategy & QA Plan

**Project:** Shikhi (শিখি — "I learn")
**Document type:** Test Strategy
**Author role:** QA Lead
**Status:** DRAFT — Phase C; reviewed at **Gate C**
**Version:** 0.1
**Builds on:** `20-prd.md` (acceptance criteria), `30-nfr.md`, `40/41` (architecture),
`43` (API contract), `50` (security)

> **How to read this (for a non-specialist):** This document defines **how we prove Shikhi
> works and stays working** — the kinds of tests, what each covers, the automated "gates"
> that block a broken change from merging, and how every requirement maps to a test so
> nothing ships untested. It's the QA counterpart to the requirements and design docs.

---

## 1. Testing philosophy

- **Shift-left & automation-first:** catch defects early; automate everything that runs in
  CI; manual testing only where automation is impractical (e.g., real-device Bengali
  rendering spot-checks).
- **Test the contract, not the implementation:** tests assert observable behavior (API
  contract, acceptance criteria), so refactoring doesn't break them.
- **Traceability:** every requirement (PRD story / NFR / security control) maps to at least
  one test (§11).
- **Gates over vibes:** merges are blocked by objective CI checks (§9), not judgment calls.
- **Proportional to risk:** the most testing goes where errors hurt most — grading
  correctness, progress integrity, auth, and privacy.

---

## 2. Test pyramid (where effort goes)

```
        ▲  fewer, slower, broadest
   E2E  │  (critical user journeys — Playwright)
        │
 Integration / Contract  (module + DB/Redis via Testcontainers; API-contract tests)
        │
   Unit │  many, fast, focused (grading rules, normalization, streak/XP, validators)
        ▼  most tests here
```

Rationale: pure logic (grading, normalization, streak/XP, content validation) is unit-heavy
and cheap to test exhaustively; a smaller set of integration/contract tests proves the
pieces fit; a thin layer of E2E covers the handful of journeys that must never break.

---

## 3. Test levels & scope

| Level | Scope | Tooling (proposed) | Notes |
|---|---|---|---|
| **Unit** | Pure logic: grading strategies + normalization, streak/XP/hearts, validators, mappers | JUnit 5, AssertJ, Mockito (backend); Vitest + Testing Library (frontend) | Fast, deterministic, exhaustive on rules |
| **Integration** | Module + real Postgres + Redis; repositories; transactions; idempotency | **Testcontainers** (Postgres, Redis) | No mocks for the DB — real behavior |
| **Module boundary** | Spring Modulith boundary verification | Spring Modulith test support | Enforces ADR-0001 boundaries |
| **API contract** | Requests/responses conform to `43-api-contract.openapi.yaml` | Schema validation + provider tests (e.g., REST-assured/Spring Cloud Contract) | Contract is the source of truth |
| **Frontend component** | React components, i18n switching, form validation | Vitest + React Testing Library | BN/EN rendering paths |
| **End-to-end (E2E)** | Critical journeys across SPA + API | **Playwright** | See §4 |
| **Performance/Load** | NFR latency/throughput budgets | **k6** or Gatling | §6 |
| **Security** | SAST, SCA, DAST, auth/IDOR, secrets | §7 tooling | §7 |
| **Accessibility** | WCAG checks | axe-core / Playwright a11y | NFR-AC* |
| **i18n/localization** | Bengali Unicode/font rendering, locale switch | Automated + **real-device manual** spot-checks | NFR-I2 (CO-4) |
| **Resilience** | Slow/dropped-network behavior, dependency-down degradation | Network throttling in E2E; fault injection in integration | D2/NFR-N*, NFR-A3 |
| **(Future) LLM-output eval** | AI grading quality vs. an eval set | §8 | D4 — when AI ships |

---

## 4. Critical end-to-end journeys (must always pass)

- **J1 — Sign up → verify → onboard → first lesson completed** (activation path, SC-1).
- **J2 — Play a lesson:** answer correct/incorrect, see feedback + hint, lose a heart, get
  results, XP/streak update (BR-4/5/6).
- **J3 — Cross-device sync:** progress made on one session appears after re-login (BR-8).
- **J4 — Resilience:** answer submitted on a throttled/dropped connection is not lost and
  not double-counted (D2/NFR-N2/DI1).
- **J5 — Content publish:** author publishes a new version; learners get consistent content;
  in-progress session stays pinned (F4/NFR-DI2).
- **J6 — Privacy:** export data; delete account removes/anonymizes PII (NFR-PR2).
- **J7 — Locale:** switch BN/EN; Bengali renders correctly (D1/NFR-I2).
- **J8 — Adaptive practice (E12):** sign in → self-place level → start session → play a
  full round (correct/wrong; heart lost; word strength updated) → round summary → keep
  going → finish → totals + stats correct; a second session prioritizes the missed word.

---

## 5. Priority test scenarios by risk (highest first)

| Risk area | Must-cover scenarios |
|---|---|
| **Grading correctness** (R1) | Each exercise type: correct, near-miss (normalization variants), wrong; hint selection precedence; WORD_BANK multiple accepted orders |
| **Progress integrity** (NFR-DI1) | Idempotent replay (same key) does not double-count XP/hearts; concurrent submits; streak across day boundaries/timezone |
| **AuthN/Z** (Security §4) | Login/refresh/rotation/replay; **IDOR** (access another user's data → denied); role-gated authoring; rate-limit/lockout; account-enumeration-safe responses |
| **Content versioning** | Publish/rollback; in-progress pinning; validation rejects bad content (US-9.3) |
| **Grading seam (future-proofing)** | Verdict shape stable; swapping strategy doesn't change player/progress (SC-5) |
| **Resilience** | Redis down → DB fallback; provider (email/SMS) down → queue/retry; timeouts/circuit breakers |
| **Practice generation** (E12) | Band mix ≈70/30; weak/unseen words prioritized; no repeats within a session; distractors valid + unique; sentence formats only for short examples; **answer key never serialized** to the client; idempotent replay; level-up eligibility threshold; IDOR on another user's session → 404 |

---

## 6. Performance & load testing (verifies NFRs)

- **Targets:** NFR-P1 (reads p95 ≤200 ms), NFR-P2 (writes p95 ≤300 ms), NFR-S3 (≥1,000
  concurrent), TTI ≤5 s (NFR-P4), payload budgets (NFR-P5).
- **Approach:** k6 scenarios for the hot path (curriculum load, start session, submit
  answer, sync) at baseline and **10× baseline** (NFR-S2); soak test for stability;
  frontend performance budget checks (Lighthouse) on the reference device/network profile
  (needs OQ-N3 answer).
- **Pass/fail:** latency percentiles and error rate must stay within NFR budgets; results
  recorded against the NFR IDs.

---

## 7. Security testing (verifies §50 controls)

| Type | Tool (proposed) | Gate |
|---|---|---|
| **SAST** (static analysis) | e.g., SpotBugs/Semgrep (backend), ESLint security rules (frontend) | Fail on high severity |
| **SCA** (dependencies) | e.g., OWASP Dependency-Check / Snyk-style | Fail on high/critical CVEs |
| **Secret scanning** | e.g., gitleaks (pre-commit + CI) | Block secrets in repo |
| **DAST** (running app) | e.g., OWASP ZAP baseline against staging | Review findings |
| **Auth/authorization tests** | Custom integration/E2E (IDOR, role, rate-limit, token) | Must pass |
| **Container image scan** | Trivy-style | Fail on high/critical |

Maps to the §50 traceability table; **ASVS L2** items each get a verifying test or control.

---

## 8. (Future) LLM-output evaluation (D4)

When AI grading ships: maintain a curated **evaluation set** of `(exercise, learner answer,
expected verdict)` triples covering correct / near-miss / wrong / adversarial (prompt-
injection attempts). Run it as a **regression gate** on the `AiGradingStrategy`, measuring
correctness, false-correct/false-wrong rates, and fallback rate — guardrails from Vision
§7.3. Not part of the pilot; the harness and eval set are designed now.

---

## 9. CI quality gates (NFR-M3 — blocks merge)

A change may merge only if **all** pass:
1. Build succeeds (backend + frontend).
2. Unit + integration + contract + component tests pass.
3. Module-boundary check passes (Modulith).
4. Coverage ≥ targets (§10).
5. Lint/format/static analysis clean.
6. SAST + SCA + secret scan: no high/critical.
7. OpenAPI contract validation passes; no undocumented API drift.
8. (On PRs touching UI) a11y checks pass.

E2E, load, and DAST run on a schedule / pre-release (staging), not necessarily per-PR
(speed), but must be green before a release (Phase E gate).

---

## 10. Coverage targets (proposed — confirm at Gate C)

- **Domain/logic modules (grading, progress, content validation):** line **≥ 90%**, and
  **branch coverage on grading/normalization** especially high.
- **Overall backend:** **≥ 80%** *(TBC)*.
- **Frontend logic (hooks, state, i18n):** **≥ 80%** *(TBC)*.
- Coverage is a floor, not a goal — paired with the risk-based scenarios (§5) so we don't
  chase numbers on trivial code.

---

## 11. Traceability (requirement → test) — approach

Every PRD story, NFR, and security control carries an ID; each maps to test(s). The
**consolidated traceability matrix lives in `80-delivery-plan.md`** (requirement → design →
test), maintained through Phase D. Example rows:

| Requirement | Test(s) |
|---|---|
| US-5.1 rule-based grading | Unit (per type) + integration |
| US-5.2 bilingual hint precedence | Unit (hint selection) |
| NFR-DI1 idempotency | Integration (replay) + E2E J4 |
| Security §4.2 IDOR | Auth authorization tests + E2E |
| US-8.1 cross-device sync | E2E J3 |
| NFR-I2 Bengali rendering | Automated + real-device manual J7 |

---

## 12. Test data management

- **Seed content:** a small, versioned fixture curriculum (all exercise types) for
  integration/E2E — independent of production authoring.
- **Factories/builders** for users, sessions, progress states.
- **Deterministic** tests (no reliance on wall-clock/timezone except explicit streak tests
  with controlled clocks).
- Test databases are ephemeral (Testcontainers) — no shared mutable state.

---

## 13. Environments & test execution

| Env | Used for |
|---|---|
| Local | Unit/integration (Testcontainers), component |
| CI (per PR) | Gates §9 |
| Staging | E2E, DAST, load, real-device localization checks, pre-release sign-off |
| Production | Smoke tests post-deploy; synthetic monitoring |

---

## 14. Defect management

- **Severity:** S1 (data loss/security/auth broken/learning blocked) → S4 (cosmetic).
- S1/S2 block release; S1 in production triggers rollback (Phase E runbook).
- Every fixed defect gets a **regression test** so it can't recur.

---

## 15. Definition of Done (testing view; feeds Delivery Plan)

A story is "done" only when: acceptance criteria have passing automated tests; coverage
targets met for new logic; security/authorization tests for the touched endpoints pass; no
new high/critical SAST/SCA findings; contract updated if the API changed; docs/traceability
updated.

---

## 16. Open QA questions (for Gate C)

- **OQ-Q1:** Confirm coverage targets (§10) and which E2E/load tools (Playwright + k6
  assumed).
- **OQ-Q2:** Reference device/network profile for performance & rendering tests (ties to
  OQ-N3).
- **OQ-Q3:** DAST cadence (per-release vs. periodic) and who reviews findings.

> **Next:** `70-devops-and-infra.md` (CI/CD, environments, IaC, observability, release/
> rollback — written provider-agnostically per the deferred cloud decision), then
> `80-delivery-plan.md` (sequencing, RACI, DoR/DoD, risk register, traceability matrix).

---

## 17. Android app addendum (ADR-0012, PRD `21`) — added 2026-07-07

Test pyramid for the `android/` client (server-side strategy above is unchanged):

1. **JVM unit tests** (largest layer): ViewModels, domain mapping (typed exercise-config
   parsing), outbox logic — JUnit + MockK + Turbine + coroutines-test.
2. **Repository/HTTP tests:** against **MockWebServer** — auth flows (single-flight
   refresh, rotated-refresh-token persistence, family-revocation handling), idempotency
   key retention across retries, error mapping.
3. **Compose UI tests** (thin): one per critical flow (MCQ lesson flow at minimum);
   Robolectric for Room/DataStore where an emulator isn't warranted.
4. **Manual device E2E per gate:** guest onboarding → lesson → airplane-mode → reconnect
   sync, on a physical mid-range device (PRD `21` §6 acceptance list).

**CI gate:** `./gradlew lint testDebugUnitTest assembleDebug` must pass in the `android`
job for every PR that touches `android/` (see `70` §4).
