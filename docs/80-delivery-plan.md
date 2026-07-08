# 80 — Delivery Plan

**Project:** Shikhi (শিখি — "I learn")
**Document type:** Delivery Plan (sequencing, RACI, DoR/DoD, risk, traceability)
**Author role:** Product Manager (with all roles)
**Status:** DRAFT — Phase C; reviewed at **Gate C** (green light to build)
**Version:** 0.1
**Builds on:** all prior docs (`00`–`70`)

> **How to read this (for a non-specialist):** This is the plan for **actually building**
> Shikhi now that requirements, architecture, and test/infra strategy exist. It sequences
> the work into **milestones**, says **who does what** (RACI), defines when a piece of work
> is **ready to start** and **done**, tracks **risks**, and provides the **traceability
> matrix** that links every requirement to its design and its tests. Phase D executes this;
> each milestone ends in a **per-epic gate** you review.

---

## 1. Delivery approach

- **Doc-first SDLC:** Phases A–C (done at Gate C) produced requirements, architecture, and
  test/infra strategy. **Phase D** builds it; **Phase E** hardens and launches.
- **Phase D = epic by epic, vertical slices.** Each milestone delivers a runnable slice
  (DB → API → UI) and runs a **mini-cycle**: *implement (Developer) → automated tests (QA)
  → security check (Security, where relevant) → independent review (Reviewer) → demo via
  `/run`*. **Per-epic gate** = your review of the working slice.
- **Content authoring is a continuous parallel track** (D6 pilot-first), not a single
  milestone — the biggest non-engineering effort (R3/AB-2).
- **Gate discipline continues** — per-epic in Phase D, then Gate E before launch.

---

## 2. Workstreams

| Stream | Owner role | Content |
|---|---|---|
| **Platform/Infra** | DevOps/SRE | Scaffold, CI/CD, IaC, observability, environments |
| **Backend** | Developer | Spring modules per epic, API per contract |
| **Frontend** | Developer | React SPA per epic, i18n, resilient sync |
| **Content** | Author (Shreya) + BA | Curriculum authoring, validation, linguistic review — continuous |
| **Quality/Security** | QA + Security | Tests, gates, reviews across all streams |

---

## 3. Milestone sequence (Phase D) & critical path

> Pilot scope = PRD §9. Post-pilot items listed in §4. Dependencies drive the order;
> content authoring runs in parallel from M2.

```
M0 Foundations ─▶ M1 Identity(email) ─▶ M2 Content+curriculum ─▶ M3 Lesson player+grading
                                                   │                        │
                                                   └───────────┬────────────┘
                                                               ▼
                                              M4 Progress & gamification ─▶ M5 Learner UX & resilience
                                                               │
                                                               ▼
                                                        M6 Review (basic)
   Content authoring track ══════════════(parallel from M2)══════════▶ M7 Pilot content ready
                                                               │
                                                               ▼
                                                   Phase E: Hardening & launch (Gate E)
```

| M | Name | Delivers (epics) | Exit criteria (per-epic gate) |
|---|---|---|---|
| **M0** | Foundations / walking skeleton | Platform: repo (BE+FE), CI gates, Testcontainers, Docker, Flyway baseline, Modulith skeleton, health/ready, observability + i18n scaffold, OpenAPI wiring | SPA calls API `/health`; a trivial end-to-end request works; **all CI gates green**; runnable locally |
| **M1** | Identity (email) | E1 (email+password, verify, reset, profile, roles, delete/export basics) | Sign up/login/refresh/logout; IDOR & auth tests pass; J6 basics |
| **M2** | Content & curriculum | E3 + E9 (content model, authoring+validation, versioning, publish, cached curriculum read) | Author→validate→publish; learner sees curriculum map; cache invalidation works (F4/J5) |
| **M3** | Lesson player & grading | E4 + E5 (exercise types, rule-based grading + hints, **grading seam**) | Play a lesson, all pilot exercise types graded; hint precedence; verdict shape stable (J2, SC-5) |
| **M4** | Progress & gamification | E6 + E8 (XP, hearts, streak, unlocking, **idempotent** submit/complete, sync, stats) | XP/streak/hearts correct; replay-safe; cross-device sync (J3/J4/NFR-DI1) |
| **M5** | Learner UX & resilience | E2 + E10 + D2 polish (onboarding, simple placement, locale switch, resilient sync, a11y baseline) | J1 activation path; BN/EN switch + Bengali rendering (J7); resilient on throttled network (J4) |
| **M6** | Review (basic) | E7 (Leitner scheduling, review session) | Missed items resurface; scheduling updates |
| **M7** | Pilot content ready | Content track (curated beginner level, linguistically reviewed) | Enough validated content for a credible pilot |

**Critical path:** M0 → M1 → M2 → M3 → M4 → M5 → (content M7) → Phase E.

---

## 4. Post-pilot backlog (designed-for; sequenced after launch)

- **Auth:** phone+OTP, Google social (rest of D5) — behind the existing abstraction.
- **AI grading strategy** (E5/US-5.3) behind the seam (D4) + LLM cost telemetry + eval set.
- **Review depth** (richer scheduling), **achievements** depth, richer **stats**.
- **More curriculum levels** (continuous authoring toward broad multi-level, D3).
- **Listening** expansion (audio assets), **streak-freeze**.
- **Future options:** offline/PWA (D2), native mobile (OOS-1). *Monetization remains out
  (D7).*

---

## 5. RACI (roles × activities)

> **R**esponsible (does it) · **A**ccountable (owns/approves) · **C**onsulted · **I**nformed.
> Single-thread role-phases: I wear each role; **you are Accountable at every gate**.

| Activity | Prod Owner (you) | BA | Architect | Developer | QA | Security | DevOps | Reviewer |
|---|---|---|---|---|---|---|---|---|
| Requirements change | A | R | C | I | C | C | I | C |
| Architecture/ADR change | A | C | R | C | I | C | C | C |
| Implement a story | A | I | C | R | C | C | C | C |
| Write/run tests | A | I | I | C | R | C | C | C |
| Security review | A | I | C | C | C | R | C | C |
| Infra/CI/CD | A | I | C | C | C | C | R | C |
| Release/rollback | A | I | C | C | C | C | R | C |
| Gate approval | **A/R** | C | C | C | C | C | C | C |

---

## 6. Definition of Ready (DoR) — before a story enters a milestone

- Acceptance criteria written (Given/When/Then) and unambiguous.
- Linked to design (HLD/LLD section, API contract path) and requirement ID.
- Test approach identified (levels + key scenarios).
- Dependencies known and available; content/assets identified if needed.
- Sized/estimated; fits a vertical slice.

## 7. Definition of Done (DoD) — before a story is "complete"

- Acceptance criteria have **passing automated tests**; coverage targets met for new logic.
- Security/authorization tests for touched endpoints pass; no new high/critical SAST/SCA.
- API contract updated if the API changed; contract tests pass.
- Independent **Reviewer** pass done; feedback addressed.
- Demoed via `/run`; docs + **traceability matrix** updated.
- CI fully green.

---

## 8. Risk register (consolidated & updated)

| ID | Risk | L | I | Mitigation | Owner |
|---|---|---|---|---|---|
| R1 | Wrong grading feedback (esp. future AI) erodes trust | M | H | Rule-based v1; seam + eval set + fallback for AI; curated hints reviewed | QA/Arch |
| R2 | LLM cost (future) grows faster than value | M | H | Deferred (D4); caching/fallback/budget when added | Arch |
| R3 | Content authoring capacity can't fill broad curriculum | **H** | H | **Pilot-first (D6)**; author tooling (E9) prioritized; linguistic review; continuous track | PM/BA |
| R4 | Bengali rendering/input issues on real devices | M | M | i18n as NFR; automated + **real-device** tests (J7) | QA |
| R5 | Scope creep (social/mobile/speech/AI) | M | M | Explicit non-goals; per-epic gates; post-pilot backlog | PM |
| R6 | Poor connectivity degrades UX | M | M | Resilient sync (D2/NFR-N*); perf budgets; J4 | Dev/QA |
| R7 | Security/privacy failure | L | H | Threat model (`50`); auth/IDOR tests; secret scanning; export/delete | Security |
| R8 | Solo/beginner build stalls on complexity | M | M | Modular monolith; explain-as-we-go; small slices; managed infra | All |
| R9 | Over-engineering for absent scale | M | M | MoSCoW (NFR §15); "measure then scale"; pragmatic infra | Arch |
| R10 | **Multi-method auth (D5) inflates pilot scope** | M | M | Phase email→phone→social; abstraction; managed-IdP fallback (ADR-0005) | Arch |
| R11 | **Cloud provider undecided delays deploy** | L | M | App cloud-agnostic; **bind provider before M-deploy** (ADR-0008/OQ-D2) | DevOps |

---

## 9. Consolidated traceability matrix (requirement → design → test → milestone)

> Maintained live through Phase D. Abbreviated to key items; full per-story rows added as
> stories are built.

| Req ID | Requirement | Design ref | Test level(s) | Milestone |
|---|---|---|---|---|
| BR-1/E1 (D5) | Multi-method identity | LLD §2.1, ADR-0005, API `/auth/*`, `50` §5 | Unit, integration, auth/IDOR, E2E J1/J6 | M1 (email); post-pilot (phone/social) |
| BR-2/E2/E10 (D1) | BN/EN UI + Bengali rendering | ADR-0007, NFR-I* | Component, a11y, real-device J7 | M5 |
| BR-3/E3 (D3) | Curriculum consumption | LLD §2.2/§3.2, API `/curriculum`,`/lessons` | Integration, E2E | M2 |
| BR-4/E4 | Lesson player & exercise types | LLD §2.3/§5, API `/sessions/*` | Unit, integration, E2E J2 | M3 |
| BR-5/E5 (D4) | Deterministic grading + hints | LLD §2.4/§5, ADR-0006 | Unit (per type), integration | M3 |
| BR-11/E5 seam | AI grading seam preserved | ADR-0006, NFR-M6 | Contract/seam tests (SC-5) | M3 (seam); AI post-pilot |
| BR-6/E6 | XP/streak/hearts/unlock | LLD §2.5/§7, API `/stats`,`/sessions/complete` | Unit, integration (idempotency), E2E | M4 |
| BR-7/E7 | Spaced-repetition review | LLD §2.6, API `/review/*` | Unit, integration | M6 |
| BR-8/E8 | Cross-device sync | LLD §4.4/§7, API `/progress/sync` | Integration, E2E J3/J4 | M4 |
| BR-9/E9 (D6) | Authoring/validation/versioning | LLD §2.2, API `/admin/content/*` | Integration, E2E J5 | M2 + content track |
| BR-10/E11 | Ops + privacy (export/delete) | `50` §6, API `/me/export`,`DELETE /me`,`/health` | Integration, E2E J6 | M0 (ops) / M1 (privacy) |
| NFR-P1/P2/S* | Latency & scale budgets | `40` §9, `70` §7 | Load (k6) | Phase E (+ ongoing) |
| NFR-A*/N* | Reliability & resilience | `40` §9, `70` §10 | Integration fault-injection, E2E J4 | M4/M5 + Phase E |
| NFR-SEC*/`50` | Security controls | `50` (STRIDE/OWASP) | SAST/SCA/DAST, auth tests | All + Phase E |
| NFR-I2 (CO-4) | Bengali Unicode/fonts | ADR-0007, NFR-I2 | Automated + real-device J7 | M5 + Phase E |
| NFR-AC* | Accessibility | NFR-AC*, `60` §3 | axe/a11y | M5 + Phase E |
| NFR-O* | Observability | ADR-0009, `70` §8 | Smoke/synthetic; dashboard checks | M0 + ongoing |
| NFR-D* | CI/CD, IaC, migrations | `70` §4–6 | Pipeline self-tests, staging deploy | M0 + ongoing |

---

## 10. Decisions to close before/within Phase D (checklist)

| Item | Where | Needed by |
|---|---|---|
| **Cloud provider** (OQ-D2/ADR-0008) | You + DevOps | Before deployment work (not local dev) |
| Token TTLs & SPA token storage (OQ-S1) | Security + Dev | M1 |
| Streak **timezone** source (OQ-L1) | Dev | M4 |
| Submitted-answer **retention** (OQ-L4/S3) | Security | M4 |
| Privacy **jurisdiction** (OQ-N2/S2) | You | Before launch (Phase E) |
| Reference **device/network** profile (OQ-N3) | You + QA | Before perf tests (Phase E) |
| **GitHub** as repo/CI host (OQ-D1) | You | M0 |
| Redis managed vs serverless (OQ-D3) | DevOps | Before deployment |
| Gamification values, placement depth, listening-in-pilot (OQ-P1–P3) | PM playtesting | M3–M5 |

---

## 11. Gate C checklist (what your approval confirms)

- [ ] Test strategy (`60`) is sufficient and its gates/coverage targets are right.
- [ ] DevOps/infra approach (`70`) is right (provider-agnostic, bind provider later).
- [ ] Milestone sequence (§3) and pilot scope match your intent.
- [ ] RACI, DoR/DoD, risk register are acceptable.
- [ ] You're comfortable this is the **green light to begin building (Phase D, M0)**.

> **On Gate C approval, Phase D begins with M0 (Foundations).** I'll switch to the
> **Developer** role (with QA/Reviewer per the mini-cycle) and scaffold the walking
> skeleton, stopping at the M0 per-epic gate for your review.

---

## 12. Android track (MA0–MA4) — added 2026-07-07 (ADR-0012, PRD `21`)

A parallel milestone track for the native Android client. Same gate discipline as §3;
each gate is a user review. Backend and web milestones are unaffected.

| Milestone | Deliverable | Gate exit |
|---|---|---|
| **MA0 — Docs & scope** | ADR-0012; Vision NG1 / BRD OOS-1 amendments; PRD `21`; NFR/test/devops addenda; contract trued (`/vocabulary`) | Docs approved, ADR-0012 → Accepted |
| **MA1 — Walking skeleton** | `android/` standalone Gradle build; generated `/v1` client (or DTO fallback per spike); guest auth + refresh rotation; home shell with health + `/me` | Debug APK on emulator: guest start, session resume after relaunch; Authenticator tests green |
| **MA2 — MVP core loop** | Curriculum home + stats; lesson player (MCQ, WORD_BANK, fallback card); completion; Room outbox → `/progress/sync` | On-device: guest → lesson → XP/streak; airplane-mode completion syncs on reconnect |
| **MA3 — Learner parity** | Practice (5 types, rounds, level-up), review, vocabulary browser, accounts + claim flow | Learner surface matches web SPA, demoed on device |
| **MA4 — Offline & release** | Room content cache, WorkManager outbox, bn/en l10n, theming, signed release APK, `android` CI job | PRD `21` §6 acceptance list passes on a clean device against the hosted backend |

**Risks carried in this track:** OpenAPI-3.1→Kotlin generator fidelity (spike + DTO
fallback, MA1); ~~release base URL depends on merging the hosted stack from
`chore/deployable-stack` (decide before MA4)~~ **resolved 2026-07-08 — release build pins
`shikhi.onrender.com` via `-PreleaseApiBaseUrl`**; contract drift now has two consumers
(future CI spec-diff check noted in ADR-0012).

### Post-MA4 changes (recorded 2026-07-08)

Shipped after Gate MA4; docs reconciled rather than re-gated (small increments / one pivot):

| Change | What | Docs updated |
|---|---|---|
| **C1 CEFR band** | Self-placement, practice `BAND_ORDER`, `PUT /stats/level`, and DB check constraints (V21) widened A1–B2 → **A1–C1** (C1 vocab was already seeded V17/V19) | PRD `20` E12/US-12.1; LLD `41` §3.5 |
| **Practice-first Android home** | Curriculum/lesson tree hidden from the Android home; surface = stats + practice + review + vocabulary | PRD `21` §7 (+ open item on MA4 acceptance #1/#4) |
| **Login convenience** | "Remember me" (email + password) and reveal-password toggle on web + Android login | PRD `20` US-1.1b; PRD `21` §7 |
| **Release URL pinned** | Release APK points at the hosted Render backend | Runbook `90` §build; risk above closed |

---

## 13. Dashboard track (MD0–MD5) — added 2026-07-08 (PRD `20` E13, PRD `21` §8)

Learner profile & dashboard across both clients. Same gate discipline as §3/§12; each
gate is a user review. Read-only feature: no gamification/grading behavior changes.
Phase 1 = snapshot from existing data (MD1–MD3); Phase 2 = time-series reports (MD4–MD5).

| Milestone | Deliverable | Gate exit |
|---|---|---|
| **MD0 — Docs & contract** | PRD `20` E13 (US-13.1–13.6); PRD `21` §8; LLD `41` §1 reporting seam + §2.9 + §3.5a; contract `43` (`User.joinedAt`, `/dashboard`, `/reports/activity`; C1 enum drift trued); this section; test-strategy `60` addendum | Docs + contract diff approved |
| **MD1 — Backend Phase 1** | `com.shikhi.dashboard` module (`GET /dashboard`); `joinedAt` on `/me`; `ReviewService.dueCount`; `PracticeStatsService.masteryByBand`; no migration | `./gradlew test` green incl. new `DashboardFlowIntegrationTest`; shapes match contract; curl demo |
| **MD2 — Web Phase 1** | Profile view in the SPA (view state + header entry, clickable stats bar): profile card w/ edit, stats grid, mastery bars, account actions; bn/en strings | Demo: edit display name in bn UI; real mastery bars; export downloads; guest sees claim CTA (not delete/export) |
| **MD3 — Android Phase 1** | `profile` route + screen (cached dashboard, `Sourced` offline badge); logout moved home→profile; DTOs per contract; bn/en strings. **Precondition: MD1 deployed to Render** | Clean device vs hosted backend: guest → practice → real mastery; airplane-mode cached dashboard; name edit visible on web. Confirm logout move |
| **MD4 — Phase 2 reports (BE+web)** | V22 reporting indexes; `GET /reports/activity` (UTC days, cap 90); `accuracyByPattern` filled in dashboard payload; hand-rolled SVG activity/accuracy chart | 30-day chart on real (retro-derived) data; report p95 sampled locally per `91` habit |
| **MD5 — Android Phase 2** | Activity chart (Compose Canvas) + DTOs. **Precondition: MD4 deployed to Render** | Chart parity on device against prod; this table marked complete |

**Decisions recorded (defaults; revisit at the named gate):** guests get the full
dashboard with claim CTA replacing delete/export (MD2); Android logout relocates to the
profile screen (MD3); `rank` stays stubbed — leaderboard remains post-pilot; XP-over-time
is out (not back-derivable; daily rollup table recorded in LLD §2.9 as the future path);
day boundary UTC (OQ-L1 unchanged).

**Traceability additions:**

| Req ID | Requirement | Design ref | Test level(s) | Milestone |
|---|---|---|---|---|
| BR-8/E13 (US-8.2) | Learner profile & dashboard | LLD §2.9, API `/dashboard`, `/reports/activity`, PRD `21` §8 | Unit, integration (aggregation correctness), E2E demo per gate | MD1–MD5 |
