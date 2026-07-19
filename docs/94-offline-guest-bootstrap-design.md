# 94 — Offline Guest Bootstrap on Android: Engineering Design

**Project:** Shikhi (শিখি — "I learn")
**Document type:** Subsystem Architecture & Implementation Specification
**Author role:** Architect
**Status:** DRAFT
**Version:** 1.0
**Audience:** Android/backend engineers, AI coding agents, product owners
**Builds on:** `adr/0011-guest-learning-and-account-claim.md`, `adr/0014-offline-guest-bootstrap.md`,
`93-offline-learning-design.md` (§3.2 two Room databases, §3.4 sync model, §4.2 local tables),
guest-first launch (`GF1`–`GF5`, no doc — see memory `guest-first-launch-track`)
**Gate prefix:** `OG` (`OG0`–`OG4`)

---

## 1. Purpose

`GF1`–`GF5` removed the blocking login wall: the app auto-provisions a guest session instead of
forcing sign-up. Verified on-device (2026-07-20) this works — **when the backend is reachable**.
With no reachable backend at first launch, the app now shows `ConnectingScreen`'s retry loop
forever instead of a login wall. For a device whose entire reason to bundle content offline
(doc `93`) is *no connectivity*, that is the same failure in a friendlier costume.

**Goal:** first launch, with zero network, ever, lands the learner on the home screen and lets
them practice/learn immediately, using the same local engine doc `93` already built. The server
account is created opportunistically, the moment connectivity appears — transparently, with no
learner-visible step.

## 2. Current State

- `AuthRepository.kt` (`init`/`bootstrap()`/`provisionGuestOrFail()`) calls `POST /v1/auth/guest`
  unconditionally on cold start; failure (including "no network") surfaces as
  `SessionState.GuestUnavailable`, which `MainActivity.kt` renders as `ConnectingScreen` with a
  retry button — no path to `ShikhiNavHost` without a successful call.
- `ShikhiNavHost` starts at `"home"`; nothing in the nav graph depends on a *server* session
  existing, only on `MainViewModel.session` reaching a non-`Loading`, non-`GuestUnavailable`
  state.
- Doc `93`'s local engine (`LocalLessonSource`/`LocalPracticeSource`, `ContentDatabase`,
  `LocalWordProgress`/`LocalPracticeSession`/etc. in `ShikhiDatabase`) is fully wired and keys
  every mutable local table by `userId` — today that `userId` is always a server-issued id,
  because nothing local ever runs before `AuthRepository` has one.
- The offline outbox (doc `93` §5) already drains without needing a `userId` on the event
  itself — attribution happens from whichever token is current at drain time.
- `DataStoreTokenStore` persists the JWT pair; nothing today persists an identity *before* a
  token exists.

## 3. Architecture Decisions

### 3.1 `localGuestId`: a client-only bridge id

A UUIDv4 generated on-device the first time `AuthRepository.bootstrap()` runs and no stored
identity (local or server) exists yet. Persisted in the existing app `DataStore` alongside
today's token fields, under a new key, `local_guest_id`. Never transmitted to the server. Exists
only to give the local Room tables a stable `userId` value before a server one exists.

### 3.2 New session state: `SessionState.LocalGuest`

```kotlin
sealed interface SessionState {
    data object Loading : SessionState
    data object LocalGuest : SessionState   // new — no server contact yet, fully usable
    data object GuestUnavailable : SessionState  // kept: distinct "retrying" transient during 3.3
    data object LoggedOut : SessionState
    data class Active(val userId: String) : SessionState
}
```

`bootstrap()`'s decision order changes from "call guest endpoint, block on result" to:

1. Stored server session (existing token) → `Active` (unchanged).
2. Stored `localGuestId`, no server session yet → `SessionState.LocalGuest` immediately; kick off
   3.3's worker in the background; do **not** wait for it.
3. Neither exists (true first launch) → mint `localGuestId`, persist it, go to step 2.

`MainActivity` renders `LocalGuest` exactly like `Active` (`ShikhiNavHost`) — the learner never
sees a difference. `GuestUnavailable`/`ConnectingScreen` is now reached only from `LocalGuest` if
3.3 is actively mid-attempt and something (e.g. `LessonView`'s online-only content browse, doc
`93` non-goals) specifically requires a live server call; the common local-content path never
routes through it.

### 3.3 `GuestRegistrationWorker`: opportunistic, idempotent registration

A WorkManager one-shot worker, constrained on `NetworkType.CONNECTED` (same constraint class
already used by the outbox drainer, doc `93` §3.4), enqueued whenever `SessionState.LocalGuest`
is entered and re-enqueued on every process start while still in that state:

```kotlin
class GuestRegistrationWorker(...) : CoroutineWorker(...) {
    override suspend fun doWork(): Result {
        val localId = tokenStore.localGuestId() ?: return Result.success() // already registered
        val pair = authApi.guest(GuestRequest(uiLocale)) // POST /v1/auth/guest, unchanged contract
        val user = userApi.me() // TokenAuthenticator attaches the new access token
        db.withTransaction {
            wordProgressDao.rekey(localId, user.id)        // local_word_progress
            wordProgressDao.rekeyReview(localId, user.id)   // local_review_progress
            practiceSessionDao.rekey(localId, user.id)      // local_practice_sessions
            // local_practice_exercises has no userId column (keyed by sessionId) — no rekey needed.
        }
        tokenStore.setSession(pair.accessToken, pair.refreshToken)
        tokenStore.clearLocalGuestId()
        return Result.success()
    }
}
```

`WordProgressDao` (`app/src/main/java/com/shikhi/app/data/db/LocalPractice.kt`) already owns both
`local_word_progress` and `local_review_progress` queries — `rekey`/`rekeyReview` are two new
`@Query("UPDATE ... SET userId = :new WHERE userId = :old")` methods on that same interface, not
new DAOs. Likewise `LocalPracticeSessionDao` gains one `rekey` method for `local_practice_sessions`
only. There is no `LocalLessonSession` table in code today (doc `93`'s data model listed it as
planned, but `LessonViewModel`'s in-memory resume state made it unnecessary in practice) — nothing
to re-key for lessons.

`rekey(old, new)` is a single `UPDATE ... SET userId = :new WHERE userId = :old` per table,
all inside one `db.withTransaction` — atomicity is the point (ADR-0014 consequence: a partial
re-key would split one guest's history across two ids). `MainViewModel` observes the token
store and flips `LocalGuest → Active` the moment `saveSession` commits.

Retry policy: WorkManager's default exponential backoff is sufficient — this mirrors the outbox
drainer's existing retry shape, no new backoff design needed.

### 3.4 `GuestBanner` in the `LocalGuest` state

The banner already distinguishes "learning as guest" from signed-in (`guest-first-launch-track`
memory, `GF3`). Its "Save my progress" / "Sign in to existing account" actions both assume a
*server* guest account exists to claim or discard. In `SessionState.LocalGuest`, both actions are
disabled with a short inline note ("still setting up your account — will be ready once you're
online") rather than hidden outright, so the banner's layout doesn't shift once registration
completes. No new screen; a boolean flag on the existing composable.

### 3.5 What does *not* change

- The local practice/lesson engines, `ContentDatabase`, and the outbox sync model (doc `93`
  §3.2–§3.4) are untouched — they already work against whatever `userId` they're given.
- `POST /v1/auth/guest`'s request/response contract is unchanged; it's simply called later and
  from a background worker instead of blocking startup.
- The claim flow (ADR-0011, `GF3`) is unchanged once a server account exists; `LocalGuest` is
  strictly a state that precedes it, not a replacement for it.

## 4. Data Model

No new tables. One column addition:

```
DataStore key: local_guest_id (String, nullable, UUID) — cleared once 3.3 succeeds
```

`LocalWordProgress`, `LocalReviewProgress`, `LocalPracticeSession`, `LocalLessonSession` (doc `93`
§4.2) are unchanged in shape; only the *value* stored in their existing `userId` column differs
before vs. after 3.3 completes.

## 5. Non-Goals

- No offline-created **server** account — `localGuestId` never becomes a `users.id`; it is
  discarded once a real one is issued (ADR-0014, rejected alternative: client-supplied server
  ids).
- No merge/reconciliation across two `localGuestId`s (e.g. reinstall after wipe generates a new
  one) — identical to today's existing device-bound guest caveat (ADR-0011).
- No change to `POST /v1/auth/guest`'s rate limiting or reaper (`shikhi.identity.guest-ttl`) —
  registration is still exactly one call per device, just deferred.
- No offline claim/registration (register/login still require connectivity — unchanged, and
  unaffected by this doc since claim only applies once `Active`).

## 6. Gate Sequence

- `OG0` (this doc + ADR-0014) — architecture accepted.
- `OG1` — `localGuestId` bootstrap + `SessionState.LocalGuest` + `MainActivity` routing change.
- `OG2` — `GuestRegistrationWorker` + atomic re-key DAOs + `LocalGuestRegistrationTest`
  (interrupt-mid-transaction all-or-nothing proof).
- `OG3` — `GuestBanner` disabled-state during `LocalGuest`.
- `OG4` — release verification: on-device airplane-mode-from-first-launch walkthrough (install →
  airplane on → open app → practice a full session → airplane off → confirm registration +
  re-key + banner update, all without a restart).

## 7. Open Risks

1. **Worker timing vs. manual content browse.** If some UI path (none identified as of doc `93`)
   ever requires a live server call while still `LocalGuest`, it needs its own short-timeout
   "not available offline yet" affordance rather than falling through to `ConnectingScreen`
   indefinitely — confirm no such path exists before `OG1` ships (doc `93`'s online-first/
   offline-fallback split (§3.3) suggests there isn't one, but it was designed assuming an
   *existing* account, not zero account yet).
2. **Multiple `GuestRegistrationWorker` runs racing.** WorkManager's unique work name
   (`enqueueUniqueWork`, `ExistingWorkPolicy.KEEP`) avoids double-registration from overlapping
   process starts — must be specified in `OG2`, not left to default `enqueue`.
