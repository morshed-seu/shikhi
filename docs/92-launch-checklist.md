# 92 — Launch readiness checklist (Gate E go/no-go)

Consolidated go/no-go for the pilot. Each item links to the evidence produced in Phase E.
Legend: ✅ done · 🟡 pilot-ready, hardening tracked · ⬜ required before scale/GA.

## Product & functional
- ✅ Core learning loop end-to-end: auth → curriculum → lesson play → grading → progress →
  review (M1–M6), verified by tests + live smoke each milestone.
- ✅ Pilot content: A1 course with Greetings / Articles / To-be units and MCQ, FILL_BLANK,
  TYPE_TRANSLATION, WORD_BANK exercises + bilingual L1-transfer hints (M7).
- ✅ Bilingual UI (বাংলা default / English), locale persistence, a11y focus handling.

## Security (docs/50, E1)
- ✅ Passwords hashed with Argon2id; refresh tokens stored only as SHA-256 hash.
- ✅ Secrets from env/secret manager; insecure dev default clearly fenced (`SHIKHI_JWT_SECRET`).
- ✅ Server-side grading; correct answers / option flags never serialized to clients
  (integration-tested, incl. WORD_BANK tokens).
- ✅ AuthZ enforced server-side; learners reach only their own data; author/admin role-gated.
- ✅ Defence-in-depth headers (HSTS, nosniff, frame DENY, CSP, Referrer/Permissions-Policy).
- ✅ CORS allowlist; bearer-token auth (no cookies → low CSRF surface).
- ✅ Auth rate limiting (429 + Retry-After) against credential stuffing / brute force.
- ✅ Actuator locked down (probes public; metrics/info ADMIN-only).
- ✅ Input validation on all request DTOs; non-leaking global error handler.
- 🟡 CI secret scan + frontend dependency scan; **image + Java SCA at promotion stage** ⬜.

## Reliability & data (docs/30)
- ✅ Stateless service (horizontal-scale ready); graceful shutdown.
- ✅ Idempotent writes (answer submit, lesson complete, progress sync) — no double-counting.
- ✅ Offline-tolerant client (timeout+retry+backoff, outbox) reconciles on reconnect.
- ✅ Rollback-safe: all migrations additive; previous image runs on current schema (runbook §6).
- 🟡 Automated DB backups + restore drill (RPO≤24h/RTO≤4h) — configure in staging ⬜.

## Performance (docs/91, E3)
- ✅ Core reads p95 ≤ 200 ms (measured 9–16 ms) and writes p95 ≤ 300 ms (15 ms) — ALL PASS,
  zero errors, ~10–20× headroom on a single instance.
- 🟡 Re-measure in staging with real TLS/LB topology; soak + 1,000-concurrent (NFR-S3) ⬜.

## Observability (docs/70, E2)
- ✅ Health/liveness/readiness; 221 Prometheus metrics incl. auth-abuse counter.
- ✅ Structured ECS JSON logs (prod) with request correlation id; echoed to clients.
- 🟡 Distributed tracing (OpenTelemetry) — follow-up ⬜.

## Android client (docs/21, ADR-0012 — sideload pilot, not Play Store)
- ✅ Learner-surface parity: guest-first onboarding, lessons, practice (E12), review,
  vocabulary, claim-guest accounts — emulator-verified against the live backend (MA1–MA3).
- ✅ Offline: Room outbox with retained idempotency keys + WorkManager background sync;
  curriculum/stats/vocabulary browse from cache with an "offline copy" indicator (MA4).
- ✅ Refresh-token rotation handled single-flight with persist-before-retry (family-replay
  revocation safe); refresh token AES/GCM-encrypted at rest, access token in-memory only.
- ✅ bn/en per-app language, bundled Bengali font, light/dark from web design tokens, with a
  System/Light/Dark switch persisted across restarts (E14).
- ✅ Android CI job (lint + unit tests + debug APK artifact), path-filtered.
- 🟡 **Edge-to-edge insets** now handled on every screen. `targetSdk = 36` means Android 15+
  draws under the system bars; `OnboardingScreen` and the `HomeScreen` header already called
  `statusBarsPadding()` (added in E14, after a theme button rendered *behind* the status bar
  and became untappable — the bar consumed every touch). `ProfileScreen`, `LessonScreen` and
  `PracticeScreen` used the same `padding(top = 12.dp)` header pattern with no inset handling,
  so their top controls — including the profile back button — sat under the bar (status-bar
  inset measured at ~163px on a Pixel 8, API 35). All three now call `statusBarsPadding()` on
  the header, matching the E14 idiom; their scrollable columns and `HomeScreen`'s list also
  carry `navigationBarsPadding()` / nav-bar `contentPadding` so bottom controls scroll clear of
  the gesture bar instead of sitting under it. Confirmation on a physical Android 15+ device
  (only emulator/tooling checks possible here) before Play submission ⬜.
- ✅ Release API base URL pinned to the real hosted backend: `https://shikhi.onrender.com/v1/`
  is live (`GET /v1/health` → `{"status":"UP","service":"shikhi"}`, verified 2026-07-10) and
  the release build config already points at it.
- ⬜ Play Store publishing (identifiers/signing kept compatible; separate track).

## Ops & process
- ✅ CI gates: backend build+test (Testcontainers), frontend lint+test+build, security scan.
- ✅ Operations runbook with incident playbook + rollback procedure (docs/90).
- ⬜ Production deployment target chosen (open ADR) + IaC; on-call rotation + alert routing.
- ⬜ Staging environment stood up for pre-prod validation of the 🟡 items.

## Verdict

**GO for a controlled pilot** on the current single-region/single-instance footprint: the
product loop is complete and every launch-blocking security, reliability, performance, and
observability control is in place and verified. The ⬜ items are **scale/GA gates**, not
pilot blockers, and are captured above and in the runbook §7 for the post-pilot hardening
track. Recommend standing up **staging** next to validate backups, TLS-topology latency, and
soak/concurrency before opening to the general public.
