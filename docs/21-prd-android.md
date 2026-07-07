# 21 — PRD Addendum: Native Android App

> **Status:** Draft for Gate MA0. **Parent docs:** PRD `20` (epics E1–E12 stay the source
> of truth for learning behavior), ADR-0012 (decision + technical shape), Delivery Plan
> `80` §Android track (milestones MA0–MA4), NFR `30` §Android addendum, Test Strategy `60`
> §Android addendum.
>
> This addendum defines **what the Android app shows and does**. It adds **no new backend
> behavior**: every screen consumes existing `/v1` endpoints (contract `43`). Where the
> web SPA and this app differ, this document says so explicitly.

## 1. Product framing

- **Who:** the same personas as PRD `20` — Bengali-speaking learners on **mid-range
  Android phones**, often on intermittent mobile data.
- **Why an app:** installability (home-screen presence, no browser chrome), better
  offline behavior than the "lean online + resilient" web app, and a foundation for
  later re-engagement features (notifications are a non-goal for v1, see §4).
- **Positioning:** the web app remains the primary client and is unchanged. The Android
  app targets **learner surface only** — content authoring/admin (E9) stays web-only.

## 2. Scope by phase (mirrors Delivery Plan MA1–MA4)

### Phase A — Walking skeleton + core loop (MA1–MA2)
| Surface | Behavior | Endpoints |
|---|---|---|
| Onboarding | Guest-first start (ADR-0011): one tap → learning. Locale picker (bn default). | `POST /auth/guest` |
| Session resume | Silent sign-in on launch via stored refresh token; expired/revoked → onboarding. | `POST /auth/refresh`, `GET /me` |
| Home / curriculum map | Levels → units → lessons with progress overlay and lock states (E3); stats bar (XP, streak, hearts — E6); backend "warming up" state for free-tier cold starts. | `GET /curriculum`, `GET /stats`, `GET /health` |
| Lesson player (E4) | **MCQ and WORD_BANK** renderers (all seeded pilot content); other exercise types show a graceful "not yet supported on Android" card and are skipped without losing the session. Hearts, per-answer verdict + feedback (E5), completion screen (score, XP, unlocks). | `GET /lessons/{id}`, `POST /sessions`, `POST /sessions/{id}/answers`, `POST /sessions/{id}/complete` |
| Progress durability (E8) | Failed completions/answers are buffered in a local outbox and re-synced idempotently — same semantics as the web outbox. | `POST /progress/sync` |

### Phase B — Learner-surface parity (MA3)
| Surface | Behavior | Endpoints |
|---|---|---|
| Practice (E12) | Adaptive practice at my CEFR level: all five generated types (WORD_MEANING, MEANING_WORD, SENTENCE_GAP with Bengali context hint, SENTENCE_BUILD, TYPE_WORD), multi-round "keep going", completion with level-up offer. | `POST /practice/sessions`, `…/answers`, `…/rounds`, `…/complete`, `PUT /stats/level` |
| Review (E7) | Due-items queue, knew-it / still-learning flow. | `GET /review/due`, `POST /review/results` |
| Vocabulary browser | Oxford-5000 dictionary: CEFR tabs A1–C1, search, paging. | `GET /vocabulary?level=` |
| Accounts (E1) | Register, log in, log out, profile (display name, UI locale); **claim guest** flow with progress kept in place; email-taken (409) explains "log in instead, guest progress is discarded" exactly like the web. | `POST /auth/{register,login,claim,logout}`, `GET/PATCH /me` |

### Phase C — Offline & polish (MA4)
- **Offline content (goes beyond web):** curriculum tree, learner stats, and vocabulary
  lists cached locally; browsing them works offline (network-first, cache fallback,
  with a visible "offline copy" indicator). Playing a lesson or practice still requires
  the network — grading is server-side (E6), so the app never fakes answer results
  from a cache.
- **Outbox v2:** background sync via scheduled work (network-constrained, backoff) instead
  of foreground-only flush.
- **Localization (E10):** full bn/en UI, Bengali default, per-app language setting;
  Noto Sans Bengali everywhere; locale choice persisted to the account.
- **Theming:** light/dark from the web design tokens; follows system setting with a
  manual override.

## 3. UX rules carried over from PRD `20`

- Guest-first: no sign-up wall anywhere before learning (E2).
- Feedback is bilingual and actionable (E5); Bengali renders correctly at every size.
- Hearts/XP/streak semantics are the backend's — the app never computes them locally,
  it displays what `/stats` and answer results return (E6).
- Idempotency keys are generated client-side once per action and retained across retries
  (E8), so retries and outbox replays never double-count.

## 4. Non-goals for Android v1 (explicit)

- **No OTP / Google sign-in** — those contract endpoints are post-pilot and unimplemented
  server-side; email+password + guest only.
- **No LISTENING/audio exercises** — no media pipeline exists anywhere in the product.
- **No push notifications / reminders** (foundation only; a later phase).
- **No Play Store release in this track** — signed sideloadable APK is the deliverable;
  identifiers and signing stay Play-compatible (ADR-0012).
- **No tablet-optimized layouts** — phone-first, sane behavior on large screens.
- **No content authoring/admin surfaces** (E9 stays web-only).

## 5. Device & platform matrix

| Dimension | Target |
|---|---|
| OS | Android 8.0+ (minSdk 26), target latest stable |
| Reference device class | mid-range: 2–4 GB RAM, 720p–1080p, Android 9–13 |
| Network | assume intermittent 3G/4G; every mutating flow must survive a drop (E8/D2) |
| Locales | bn (default), en |

## 6. Acceptance criteria for "done" (Gate MA4)

1. A first-time user installs the APK, taps "continue as guest", completes a lesson, and
   sees XP/streak update — under 2 minutes, no account.
2. Completing a lesson in airplane mode syncs correctly (once, idempotently) on reconnect.
3. A guest who registers keeps all progress (claim-in-place, ADR-0011); the 409
   email-taken path matches web behavior.
4. Previously viewed curriculum, stats, and vocabulary render offline from cache
   (browse-only; starting a lesson offline explains that grading needs a connection).
5. Full UI renders correctly in Bengali and English, light and dark.
6. Kill/relaunch resumes the session silently; a revoked refresh token lands on
   onboarding without a crash or a stuck state.
