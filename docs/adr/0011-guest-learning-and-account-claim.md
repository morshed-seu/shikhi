# ADR-0011 — Guest learning: anonymous user, claimed in place (no progress migration)

**Status:** Accepted (2026-07-06)
**Related:** ADR-0005 (auth strategy), D5 (multi-method identity), NFR-SEC5, PRD `20` (guest onboarding), Security doc `50`

## Context
We want visitors to start learning **without signing up**, then keep **all** their progress
when they later create an account. Today the app is sign-up-gated. The whole learning stack is
server-side (the adaptive practice engine picks words from `practice_word_progress`, keeps
answer keys server-only, etc.), and **every** progress table (`user_stats`, `user_progress`,
`practice_*`, `lesson_sessions`, `answer_submissions`, `review_items`, `processed_events`)
already keys on a single `user_id` FK → `users(id)`.

## Decision
A guest is a **real but anonymous `users` row** (`status = ANONYMOUS`, role `LEARNER`, no
identity/credential). It receives the same JWT access + rotating refresh token pair as any
learner, so it drives the **entire existing learning loop unchanged** — no guest-specific
engine, no per-endpoint branching.

Signing up **claims** (upgrades) that same row **in place**: attach an EMAIL `Identity` +
`Credential` to the existing `user_id` and flip `status` to `ACTIVE`. Because all progress
already references that id, **nothing is copied or migrated** — the data was always owned by
the right user.

- `POST /v1/auth/guest` — provision an anonymous learner (public, rate-limited like other auth).
- `POST /v1/auth/claim` — authenticated as the guest; adds email+password, returns rotated tokens.
- **Email already registered:** reject with `EMAIL_ALREADY_REGISTERED`; the guest is told to log
  in instead (guest progress from that session is discarded). Merging into an existing account
  is **out of scope** (would need conflict resolution across every progress table).
- **Abandoned guests** are reclaimed by a daily reaper (`GuestReaper`) that hard-deletes
  anonymous rows idle longer than `shikhi.identity.guest-ttl` (default 30d); DB
  `on delete cascade` removes their progress.

## Consequences
- ✅ Zero-migration conversion: the hard part (moving progress) simply doesn't exist.
- ✅ Full reuse — the practice/curriculum/review/stats stack works for guests as-is.
- ✅ Fits the stateless JWT design (ADR-0005); guest is just another token subject.
- ⚠️ Anonymous rows accumulate → mitigated by the reaper + guest-creation rate limiting.
- ⚠️ Guest progress is device-bound (refresh token in `localStorage`); clearing storage before
  claiming loses it. Acceptable for pilot; a shorter guest refresh TTL can be added later.
- ⚠️ `ANONYMOUS` widens the `users.status` domain (migration `V20`); `login` still requires
  `ACTIVE`, so a guest cannot password-login until claimed.

## Alternatives considered
- **Client-only guest (progress in browser storage):** rejected — the engine is server-side, so
  this would mean reimplementing practice/selection/grading client-side and then a real
  copy-on-signup migration. Most work, most divergence.
- **Copy progress on signup (guest row → new registered row):** rejected — needs bespoke,
  idempotent copy logic for every progress table and careful id remapping. In-place upgrade
  makes it a no-op.
- **Merge guest into an existing account on email clash:** deferred — real conflict-resolution
  scope (two `user_stats`, streaks, word mastery). Pilot rejects and asks the user to log in.
