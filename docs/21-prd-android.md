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
| Home | Stats bar (XP, streak, hearts — E6); backend "warming up" state for free-tier cold starts. **Practice-first home** — see §7 divergence: the guided curriculum/lesson tree is *not* surfaced on the Android home. | `GET /stats`, `GET /health` |
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

## 7. Post-MA4 divergences (reconciled 2026-07-08)

These shipped as code after Gate MA4 and are recorded here to keep the doc-first record
true. None add backend behavior.

- **Practice-first home (curriculum tree hidden).** The home screen no longer renders the
  curriculum levels → units → lessons tree (`HomeScreen.kt`, commit `0e5385f`). The home
  surface is now **stats + practice + review + vocabulary**. §2 Phase-A "Home / curriculum
  map" and the offline-curriculum copy in §3/§6 are superseded for what the learner *sees*;
  the `/curriculum` and `/lessons/*` endpoints and the lesson-player renderers still exist
  in the client but are not linked from home.
  - **Open item for the product owner:** MA4 acceptance #1 ("completes a lesson") and #4
    ("previously viewed curriculum … render offline") assume a reachable lesson tree. With
    the tree hidden, either re-expose an entry point or restate those criteria around the
    practice loop. *(Deferred — flagged, not decided.)*
- **Login convenience (E1).** Onboarding/login now offers **"remember me"** (persists the
  email, and — later — the password) and a **reveal-password** toggle, mirroring the web
  `AuthPanel` (commits `ae3c863`, `ecc4836`; web `63b9109`). Stored via `LoginPrefs`.
  Guest-first entry is unchanged.
- **Release base URL pinned.** The release build now pins the hosted backend
  (`shikhi.onrender.com`) via `-PreleaseApiBaseUrl` (commit `0e5385f`), closing the MA4
  risk in Delivery Plan `80` §12 about the base URL depending on the hosted-stack merge.

## 8. Profile & dashboard (E13 — added 2026-07-08; Delivery Plan `80` §13, gates MD3/MD5)

Android parity for PRD `20` E13. Same behavior as the web dashboard; same endpoints
(contract `43`: enriched `GET /me`, new `GET /dashboard`, later `GET /reports/activity`).
Like the rest of this addendum it adds **no Android-only backend behavior**.

| Surface | Behavior | Endpoints |
|---|---|---|
| Profile entry | Profile icon in the home header; opens a `profile` navigation route. | — |
| Profile card | Display name (inline edit), UI locale, masked email (registered) or guest badge, CEFR badge, joined date. Edits require the network (disabled offline). | `GET/PATCH /me`, `GET /me/identities` |
| Dashboard snapshot (MD3) | Stats grid (XP, streaks, hearts, daily goal, lifetime totals, review due) + words-mastered-per-band bars A1–C1. | `GET /dashboard` |
| Activity report (MD5) | Last-30-days answered/accuracy chart (Compose Canvas), UTC days. | `GET /reports/activity` |
| Account actions | Registered: export, delete (confirm → sign-out). Guest: claim CTA (reuses the MA3 claim flow). | `GET /me/export`, `DELETE /me`, `POST /auth/claim` |

- **Offline:** the dashboard snapshot is cached like curriculum/stats/vocabulary
  (network-first, cache fallback, "offline copy" indicator — §2 Phase C pattern). Profile
  **edits** and account actions need the network.
- **Logout moves home → profile.** The home header's logout button relocates to the
  profile screen, decluttering the practice-first home (§7). *Flagged for explicit
  confirmation at the MD3 gate.*
- l10n: all new strings ship bn + en, same as every other surface (§2 Phase C).
