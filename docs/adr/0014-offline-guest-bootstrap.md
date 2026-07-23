# ADR-0014 — Offline guest bootstrap: local-first identity, deferred server registration

**Status:** Proposed (2026-07-20)
**Related:** ADR-0011 (guest learning, claim-in-place), ADR-0012 (native Android client), doc `93`
(offline learning design — §7 non-goals, §9 risk 3), doc `21` (PRD Android addendum)

## Context

ADR-0011 made every guest a **real, server-minted `users` row**: `POST /v1/auth/guest` creates
the row and issues the JWT pair before the app is usable. It explicitly rejected a client-only
guest because at the time "the engine is server-side" — grading, practice selection, and
mastery bookkeeping all lived in the backend, so a local-only identity would have had nothing to
attach progress to.

Doc `93` (offline learning, `OF0`–`OF7`) changed that premise: lesson grading and the vocabulary
practice engine are now ported to on-device Kotlin (`LocalLessonSource`/`LocalPracticeSource`),
and doc `93` §7 recorded "no offline account creation/auth" as an explicit non-goal, assuming a
session already exists before the device goes offline.

The guest-first launch work (ADR-0011's UI counterpart, gates `GF1`–`GF5`) removed the blocking
login screen — but bootstrap still means one live `POST /v1/auth/guest` call. Verified on-device
(2026-07-20): with no reachable backend, the app sits on `ConnectingScreen`'s retry loop
indefinitely. For a learner whose entire reason to want bundled content is *no connectivity*,
this reproduces the original problem under a friendlier name. This ADR closes that gap: guest
identity must be creatable with **zero network, ever**, on first launch.

## Decision

A guest identity now has two phases, bridged by a client-generated id:

1. **Local phase (day zero, offline).** On first launch with no prior session, the app mints a
   `localGuestId` (UUIDv4, generated on-device, no network) and treats that as the `userId` for
   every local table already scoped by `userId` (`LocalWordProgress`, `LocalReviewProgress`,
   `LocalPracticeSession`, `LocalLessonSession`, per doc `93` §4.2). `MainViewModel` gains a
   `SessionState.LocalGuest` that routes straight to `ShikhiNavHost` — no spinner, no retry
   screen, no network attempt blocks first use.
2. **Registration phase (opportunistic, background).** A `GuestRegistrationWorker`
   (WorkManager, existing constraint pattern from the outbox drainer) retries
   `POST /v1/auth/guest` whenever connectivity appears. On success it: (a) persists the returned
   JWT pair via the existing `DataStoreTokenStore`, (b) re-keys every local table's rows from
   `localGuestId` to the server-issued `userId` in one Room transaction, (c) flips
   `SessionState` to today's `Active`. The outbox drain path is unaffected — per doc `93` §5,
   outbox events already carry no `userId` (attribution happens at drain time from the current
   token), so anything queued during the local phase syncs correctly once registration
   completes and the drainer runs.

A `localGuestId` never leaves the device and is never sent to the server; it is purely an
internal Room foreign key until step 2(b) replaces it. If step 2 never succeeds (permanently
offline device), the app is fully usable indefinitely — it just never gets a claimable/syncable
account, which is a strict improvement over today's "unusable until first contact."

## Consequences

- ✅ Closes the gap this ADR was written for: guest bootstrap requires no network at any point.
- ✅ No change to the server: `POST /v1/auth/guest` is called with the same shape, just later.
- ✅ No change to the sync model (doc `93` §3.4/§5) — event-sourced outbox already defers
  attribution to drain-time, so it composes with a delayed-registration guest for free.
- ✅ `GuestFlowIntegrationTest` (doc `93` §9 risk 3) still holds: claim-in-place still upgrades
  one row; the local phase only adds an earlier, purely-client stage before that row exists.
- ⚠️ New local re-keying step (2b) is a new failure surface — must be one atomic transaction
  (partial re-key would split a guest's history across two ids). Needs its own test
  (`LocalGuestRegistrationTest`: interrupt mid-transaction, assert all-or-nothing).
- ⚠️ Two devices, both offline at first launch, each mint their own `localGuestId` and — once
  online — each register as a **separate** server account. This is identical to today's
  existing "guest progress is device-bound" caveat (ADR-0011 consequences); not a new risk, just
  extended one phase earlier.
- ⚠️ `SessionState.LocalGuest` must be visibly distinguishable from `Active` in `GuestBanner`
  (e.g. still show "learning as guest," but the "sign in to existing account" / claim paths must
  be disabled or deferred until an account actually exists to attach to).

## Alternatives considered

- **Keep current design (network required once, GF4's cold-start-tolerant retry is "good
  enough"):** rejected per explicit user direction (2026-07-20) — the offline-learning epic's
  entire premise is a device that may never have connectivity at first launch, and a
  permanently-spinning `ConnectingScreen` fails that premise as surely as a login wall did.
- **Generate the server row's UUID client-side and pre-declare it to the server on first
  contact (idempotent create-with-id) instead of a two-id re-key:** rejected — `users.id`
  generation is Postgres-side (`gen_random_uuid()`); changing that to accept client-supplied ids
  widens the server's trust boundary (a client could target/collide an id) for no real benefit,
  since the re-key transaction is local-only and already required to be atomic regardless.
- **Never register with the server unless the user explicitly claims (signs up):** rejected —
  loses the guest-TTL reaper's counterpart benefit (server never learns the device exists) and
  removes any server-side backup/sync for guests who stay anonymous but do get online
  eventually; today's `GuestBanner` "Save my progress" / cross-device story depends on the row
  existing.
