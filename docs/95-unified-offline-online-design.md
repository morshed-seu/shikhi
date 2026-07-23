# 95 — Unified Offline/Online Experience: Engineering Design

**Project:** Shikhi (শিখি — "I learn")
**Document type:** Subsystem Architecture & Implementation Specification
**Author role:** Architect
**Status:** ACCEPTED
**Version:** 1.0
**Audience:** Android/backend engineers, AI coding agents, product owners
**Builds on:** `93-offline-learning-design.md` (§3.2 two Room databases, §3.4 event-sourced sync,
§4.2 local tables), `94-offline-guest-bootstrap-design.md` (`LocalGuest`, `GuestRegistrationWorker`),
`adr/0011-guest-learning-and-account-claim.md`, `adr/0012-native-android-client.md`,
`adr/0014-offline-guest-bootstrap.md`
**Gate prefix:** `UO` (`UO0`–`UO7`)

---

## 1. Purpose

Docs `93`/`94` made the Android app playable and bootstrappable with zero network. Four gaps
remain before the app is genuinely usable in *either* mode without a learner-visible seam:

- **(A) Offline CEFR level change.** `PUT /v1/stats/level` is network-only
  (`ProgressApi.setLevel`); a learner who changes level while offline gets no local effect at all.
- **(B) No durable local stats projection.** `LocalPracticeSource` reports provisional,
  never-reconciled numbers offline (fixed `+10`/answer XP, hearts always `5`, `levelUpEligible`
  always `false`) and there is no mechanism that ever corrects them once the device is back
  online — the learner's on-screen XP/hearts can silently diverge from the server's forever.
- **(C) No pull sync.** Sync today is push-only (`OutboxRepository.flush()` → `POST
  /v1/progress/sync`). A second device's server-side progress, or corrections applied directly on
  the server, never reach a device that only ever pushes.
- **(D, out of scope, product owner decision):** guest → *different* pre-existing account merge
  stays a warned, lossy discard (unchanged from `AuthRepository.activate()` today).

Alongside (A)–(C), two leftover items from the guest-bootstrap epic are folded in because they
touch the same session-state seam:

- **OG4**: `GuestBannerViewModel` does not yet observe `SessionState` — the banner's "Save my
  progress" / "Sign in to existing account" actions are not actually disabled while
  `SessionState.LocalGuest`, contrary to doc `94` §3.4's intent.
- **OG5**: a short writeup closing out the offline-guest-bootstrap epic (no code).

**Goal:** a learner can browse, practice, complete lessons, change their CEFR level, and view an
accurate profile/dashboard in either connectivity state, with local state always converging to
the server's the next time a sync completes — never silently drifting.

## 2. Current State

- **Level change** is a bare network call: `ui/home/HomeViewModel.kt` `setLevel` and
  `ui/practice/PracticeViewModel.kt` `acceptLevelUp` both call `progressApi.setLevel(...)`
  directly; there is no outbox event, no offline fallback, no local cache update.
- **Stats display offline** reads the `"stats"` `content_cache` blob
  (`data/db/ContentCache.kt`) as a static snapshot taken at last successful fetch.
  `LocalPracticeSource` (doc `93` §3.3) never updates it — it computes `correctCount * 10` as a
  session-local number shown only in the practice-session summary, not written back to the cache,
  and reports hearts as the hardcoded constant `5` regardless of what the cached blob or the
  server actually has.
- **`OutboxRepository.flush()`** already receives a fresh `Stats` object back from
  `POST /v1/progress/sync` (`ProgressSyncService` returns `progress.getState`) — today this
  response is discarded once the synced rows are deleted.
- **No pull endpoint exists.** `ProgressApi`/`DashboardApi` only expose per-resource GETs
  (`stats()`, `dashboard()`); nothing returns a single consistent snapshot for a local-table
  overwrite.
- **`user_stats`** (`progress/domain/UserStats.java`) has no column recording *when* `cefrLevel`
  last changed — two offline `SET_LEVEL`-shaped writes (or an offline change racing an online one
  from a second device) have no way to be ordered at reconcile time.

## 3. Architecture Decisions

### 3.1 Split-projection reconciliation model (the crux)

The system stays **event-sourced: the server alone recomputes XP/mastery from replayed primitive
events; the client never uploads a computed total** (doc `93` §3.4, unchanged). What's new is a
*durable* local projection of `Stats` for offline display, built by splitting local mutable state
by whether the quantity is additive:

- **XP is DERIVED, never a stored counter:**
  `displayXp = baselineXp + Σ pendingXpDelta(un-synced outbox events)`, where `pendingXpDelta` is:
  `PRACTICE_ANSWER` correct → `+10`; first-time `COMPLETE_LESSON` → `score * 10`;
  `ANSWER` / `SET_LEVEL` / `RETRY_PRACTICE_SUBMIT` → `0`. `baselineXp` is the `xp` field from the
  last reconciled `Stats` snapshot. Double-counting is structurally impossible because "replace
  `baselineXp`" and "delete the outbox rows it now subsumes" happen inside **one Room
  transaction** — a given delta lives in *pending* XOR *baseline*, never both, and never twice.
- **Hearts / `currentStreak` / `longestStreak` are NON-additive** (floor-0 clamp, daily refill,
  UTC-day rollover all live server-side in `UserStats`). These are **overwritten wholesale from
  server truth on every reconcile**, and updated inline, locally, as the user plays offline (e.g.
  a wrong practice answer decrements the locally-cached hearts immediately for responsive UI,
  same as `WordProgressService`'s local mastery bookkeeping already does) — never derived from
  the outbox.
- **Reconcile source = the `Stats` `POST /v1/progress/sync` already returns.** No new endpoint is
  needed for XP/hearts/streak reconciliation; `OutboxRepository.flush()` simply stops discarding
  the response and instead writes it into the durable projection.

Rejected alternative: a live local XP counter incremented at answer-time and independently synced
— rejected because it duplicates the server's authoritative computation and reintroduces exactly
the double-count risk event-sourcing was adopted to avoid (a delta could be counted once in the
live counter and again when its outbox event finally syncs).

### 3.2 Pull sync: gated on an empty outbox, one-shot overwrite

Bidirectional sync is added as a **pull**, not a merge:

1. **Flush.** Run the existing push path to completion.
2. **Gate.** If the outbox is non-empty after flush (a row failed), **abort the pull** — pulling
   over local tables while un-synced local writes still exist would silently discard learner
   progress the server doesn't know about yet.
3. **Pull.** Only once the outbox is empty, call `GET /v1/progress/snapshot` and receive the
   server's full current state for this user.
4. **Overwrite.** In one Room transaction: overwrite `LocalWordProgress`/`LocalReviewProgress`
   rows from the snapshot, overwrite the durable stats projection (baseline `xp`, hearts,
   streaks, `cefrLevel`), and advance a `lastSyncedAt` cursor.

Gating pull on an empty outbox removes all need for per-row local-vs-remote conflict resolution:
by the time a pull is allowed to run, the server has already seen and applied everything the
device has produced, so "the server's answer" and "this device's answer" cannot disagree for any
row this device touched. Rejected alternative: per-row merge (compare local vs. remote timestamps
per `LocalWordProgress` row) — rejected for complexity disproportionate to the actual failure
mode (a second device or a server-side correction), which the flush-then-pull gate already
handles correctly and more simply.

### 3.3 Offline CEFR level change: new outbox event + server LWW

`SET_LEVEL` becomes a fifth `OutboxEventType`, alongside `ANSWER`, `COMPLETE_LESSON`,
`PRACTICE_ANSWER`, `RETRY_PRACTICE_SUBMIT`:

```json
{
  "type": "SET_LEVEL",
  "idempotencyKey": "<uuid, minted once at enqueue time>",
  "payload": {
    "cefrLevel": "B1",
    "changedAt": "2026-07-23T10:15:00Z"
  }
}
```

`HomeViewModel.setLevel` / `PracticeViewModel.acceptLevelUp` change from a bare `progressApi.
setLevel(...)` call to: update the local stats projection's `cefrLevel` immediately (optimistic,
responsive UI, same pattern as hearts in §3.1), enqueue one `SET_LEVEL` outbox event, and let the
existing flush path deliver it like any other event.

Because level is **not additive** — two devices changing level while both offline, or an offline
change racing a direct online one, must resolve to *one* final value, not a merge — the backend
adds **last-write-wins by client timestamp**: `user_stats` gains `cefr_level_changed_at`
(`timestamptz`). `ProgressEventApplier`'s `SET_LEVEL` case applies the incoming `changedAt` only
if it is strictly newer than the stored `cefr_level_changed_at`; otherwise the event is a no-op
(still recorded in the idempotency ledger so it isn't retried, but leaves `cefrLevel` untouched).
This reuses the exact `ProcessedEvent` idempotency ledger every other event type already relies on
for exactly-once application — no new ledger, no new table.

Rejected alternative: ordering by server receipt time (i.e., "last `SET_LEVEL` to arrive at the
server wins") — rejected because sync order does not correlate with when the learner actually
changed their mind; a device that was offline longest could arrive last and incorrectly clobber a
more recent choice made on another device. A client-supplied timestamp compared with LWW captures
learner intent correctly regardless of sync order.

## 4. Data Model

### Android (`ShikhiDatabase`, version bump required — see gate `UO2`)

New durable stats projection table, replacing the ad-hoc `"stats"` `content_cache` blob as the
source of truth for offline display (the blob is retired once this lands):

```
LocalStatsProjection(
  userId,                -- PK; localGuestId or server userId, same scoping as other local tables
  baselineXp,            -- last-reconciled Stats.xp
  hearts,                -- overwritten wholesale on reconcile; updated inline offline
  currentStreak,         -- overwritten wholesale on reconcile
  longestStreak,         -- overwritten wholesale on reconcile
  cefrLevel,             -- overwritten on reconcile; optimistically updated on offline SET_LEVEL
  lastSyncedAt           -- cursor advanced only by a successful pull (§3.2 step 4)
)
```

`displayXp` (§3.1) is computed at read time, never stored: `baselineXp + Σ pendingXpDelta(outbox
rows for this userId)`.

### Backend

`user_stats` gains one column (new migration `V23`, first free version after `V22`):

```sql
ALTER TABLE user_stats ADD COLUMN cefr_level_changed_at TIMESTAMPTZ NOT NULL DEFAULT now();
```

## 5. Sync Shapes

### Push: new outbox event type

See `SET_LEVEL` shape in §3.3. `ProgressEventApplier.apply()`'s switch gains one case:
`SET_LEVEL` → `ProgressService.setLevel(userId, cefrLevel, changedAt)` (the existing `setLevel`
gains an `Instant changedAt` parameter, defaulting to `clock.instant()` for the unchanged online
caller, matching the `answeredAt` precedent doc `93` §5 already established for
`PRACTICE_ANSWER`), gated by the `cefr_level_changed_at` LWW check (§3.3).

### Pull: new bulk snapshot endpoint

```
GET /v1/progress/snapshot
→ 200 OK
{
  "stats": { "xp": 340, "hearts": 4, "currentStreak": 6, "longestStreak": 11,
             "cefrLevel": "B1", "cefrLevelChangedAt": "2026-07-23T09:00:00Z" },
  "wordProgress": [ { "vocabularyId": "...", "masteryScore": 3, "timesSeen": 5, ... }, ... ],
  "reviewProgress": [ { "vocabularyId": "...", "reviewStage": 2, "dueAt": "...", ... }, ... ],
  "asOf": "2026-07-23T10:30:00Z"
}
```

One authenticated GET, backed by the same repositories `ProgressSyncService`/`WordProgressService`
already use for their per-item reads — no new persistence, only a new aggregating read endpoint
(`ProgressSnapshotController` → `ProgressSnapshotService`, read-only, no `@Transactional
REQUIRES_NEW` needed since nothing is mutated). `asOf` becomes the device's new `lastSyncedAt`.

## 6. Non-Goals

- Guest → *different* pre-existing account merge (gap D) — stays a warned, lossy discard
  (`AuthRepository.activate()`), unchanged by this epic; product-owner decision, not a technical
  constraint.
- Per-row conflict resolution/merge for `LocalWordProgress`/`LocalReviewProgress` on pull — the
  empty-outbox gate (§3.2) makes this unnecessary.
- A live, independently-synced local XP counter — rejected in §3.1.
- Any change to the push sync contract (`POST /v1/progress/sync`'s request/response shape) beyond
  adding the `SET_LEVEL` event type and no longer discarding the already-returned `Stats`.
- Offline creation of a *second* server account, multi-device merge UX, or conflict UI — out of
  scope; LWW (§3.3) and the pull gate (§3.2) are silent, automatic resolutions with no learner-
  facing prompt.

## 7. Gate Sequence

See `~/.claude/plans/unified-offline-online/README.md` for full per-gate specs and exit criteria.

`UO0` (this doc + ADR-0015) → `UO1` (backend `SET_LEVEL` event + LWW) → `UO2` (Android durable
local stats projection + reconcile-on-flush) → `UO3` (offline CEFR level change, Android; needs
`UO1`+`UO2`) → `UO4` (offline hearts/streak/lesson-XP feed into the projection; needs `UO2`) →
`UO5` (backend bulk `GET /v1/progress/snapshot`) → `UO6` (Android pull/download reconciliation;
needs `UO2`+`UO4`+`UO5`) → `UO7` (OG4 banner disable + OG5 writeup + epic closeout; needs
`UO0`..`UO6`).

`UO1`, `UO2`, `UO5` have no dependencies on each other and can run as independent sessions once
`UO0` lands.

## 8. Risks

1. **`ShikhiDatabase` migration.** `UO2` adds `LocalStatsProjection` and retires the `"stats"`
   `content_cache` blob as a stats source — needs a real `Migration` (current version is `3`, per
   doc `93` §9 risk 2) proven the same way `ShikhiDatabaseMigrationTest` already proves
   `MIGRATION_2_3`. Must not silently destructive-fallback and lose a learner's cached progress.
2. **Clock skew on `SET_LEVEL` LWW.** The client-supplied `changedAt` (§3.3) assumes device clocks
   are roughly trustworthy. A device with a badly wrong clock could win or lose a race it
   shouldn't. Accepted risk, same class as the existing `answeredAt` trust assumption in doc `93`
   §5 — no server-side clock-skew correction planned this phase.
3. **Snapshot endpoint payload size.** `GET /v1/progress/snapshot` returns every
   `wordProgress`/`reviewProgress` row for a user; for a heavy long-term learner this could grow
   into the low thousands of rows. Acceptable for launch scale (doc `91` performance results);
   revisit pagination only if profiling shows it's a problem.
4. **Pull-gate starvation.** If a device can never successfully flush (e.g. permanently offline
   after accumulating outbox rows, or a persistent server-side rejection), it never pulls either,
   so it never sees other devices' or server-side changes. This is judged acceptable: the same
   device is also unable to push, so it is not silently diverging — it is visibly, entirely
   offline, which is the expected degraded mode, not a new failure this epic introduces.

---

## 9. Delivery Record (`UO7` closeout)

All eight gates landed on `feat/unified-offline-online` (branched off
`feat/offline-guest-bootstrap`; **not** rebased onto `main`, which lacks the `OF`/`GF`/`OG`
foundation).

| Gate | Title | Commit |
|---|---|---|
| `UO0` | This doc + ADR-0015 | `a88d90b` |
| `UO1` | Backend `SET_LEVEL` event + last-write-wins | `62e12b6` |
| `UO2` | Durable local stats projection + reconcile-on-flush | `9391a5e` |
| `UO3` | Offline CEFR level change (Android) | `55e1a9b` |
| `UO4` | Offline hearts/streak/lesson-XP into the projection | `3dc7829` |
| `UO5` | Backend bulk `GET /v1/progress/snapshot` | `2e1096b` |
| `UO6` | Android pull/download reconciliation | `fc5e069`, `3c18293` |
| `UO7` | `OG4` banner disable + `OG5` writeup + closeout | `1224126`, this commit |

### Regression totals at close (2026-07-23)

| Suite | Command | Classes | Tests | Result |
|---|---|---|---|---|
| Backend | `cd backend && ./gradlew test` | 36 | 213 | green, 0 skipped |
| Android unit | `cd android && ./gradlew :app:testDebugUnitTest` | 31 | 231 | green, 0 skipped |

Release APK (`./gradlew :app:assembleRelease`): **3,262,765 bytes ≈ 3.11 MiB** — the epic added
no assets, so the delta over the pre-`UO` build is code and string resources only.

### What the four §1 gaps look like now

1. **Offline CEFR level change** — `SET_LEVEL` outbox event, applied server-side with LWW on the
   client `changedAt` (§3.3); the local level is authoritative for offline play immediately.
2. **Durable stats** — `LocalStatsProjection` replaces the `"stats"` `content_cache` blob. XP is
   derived (`baselineXp + Σ pendingXpDelta`), never a stored counter; hearts/streak are mutable
   fields overwritten wholesale from server truth (§3.1). Offline XP, hearts and streak show real
   live values instead of doc `93`'s provisional constants.
3. **Pull sync** — `GET /v1/progress/snapshot` + a one-shot overwrite gated on an empty outbox
   (§3.2), so a reinstall-then-sign-in rebuilds progress and a second device's work arrives.
4. **`OG4`/`OG5`** — banner claim/sign-in disabled while `LocalGuest`; writeup in doc `94` §8.

### Residual risks

Risks 2–4 of §8 stand as accepted, unchanged. Risk 1 (`ShikhiDatabase` migration) is discharged:
the version bump shipped with a real `Migration` proven by `ShikhiDatabaseMigrationTest`, no
destructive fallback.

### Not covered by the automated suites

The **on-device airplane-mode walkthrough** is the one exit criterion that cannot run headless
(guest first launch offline → practice + complete a lesson + change CEFR level → live XP/streak/
hearts in Profile → go online → server reconciles and the projection matches → claim the account
→ reinstall, sign in, pull rebuilds progress). It needs a physical device and is tracked as the
epic's final manual gate.
