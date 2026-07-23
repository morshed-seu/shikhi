# ADR-0015 — Durable stats projection and bidirectional sync

**Status:** Proposed (2026-07-23)
**Related:** ADR-0011 (guest learning, claim-in-place), ADR-0012 (native Android client),
ADR-0014 (offline guest bootstrap), doc `93` (offline learning design — §3.4 event-sourced sync,
§4.2 local tables, §9 risk 2 migration strategy), doc `94` (offline guest bootstrap — §3.2
`SessionState.LocalGuest`), doc `95` (this epic's design doc)

## Context

Doc `93` made lesson/practice play work with zero network by porting grading on-device, but its
sync model was deliberately push-only and its offline stats were explicitly provisional: fixed
`+10` XP per practice answer, hearts hardcoded to `5`, level-up eligibility hardcoded to `false`
(doc `93` §3.3, `LocalPracticeSource`). Nothing ever reconciled those provisional numbers against
the server, and nothing ever pulled server-side state (a correction, or a second device's
progress) back down to a device that only pushes. Separately, CEFR level change
(`PUT /v1/stats/level`) was never given an offline path at all — a learner who changes level
offline sees no effect, silent or otherwise, until they notice on next server contact.

Doc `95` specifies four fixes. This ADR records the two decisions with the widest blast radius:
how the durable local stats projection is structured, and how pull sync avoids reintroducing the
per-row conflict-resolution complexity that event-sourcing (doc `93` §3.4) was adopted to avoid
in the first place.

## Decision

### 1. Additive-derived vs. non-additive-overwrite split for the local stats projection

The new `LocalStatsProjection` table (doc `95` §4) does not store one `xp` counter. It stores a
`baselineXp` plus, implicitly, whatever un-synced outbox rows currently exist; `displayXp` is
computed at read time as `baselineXp + Σ pendingXpDelta(pending outbox events)`. Hearts,
`currentStreak`, and `longestStreak` are stored as plain mutable fields, but are **only ever
overwritten wholesale** from the `Stats` the server returns on reconcile — never derived from the
outbox, because their real computation (floor-0 clamp, daily refill, UTC-day rollover) lives
entirely in `UserStats.java` server-side and is not something the client should reimplement.

This split exists because XP and hearts/streaks have fundamentally different arithmetic: XP only
ever increases via discrete, independently-idempotent events (an answer either was or wasn't
correct), so "baseline + sum of not-yet-applied deltas" is exact and cannot double-count as long
as an event lives in *pending* xor *baseline*, never both — enforced by doing "replace baseline"
and "delete the now-subsumed outbox rows" in one Room transaction. Hearts/streaks are not
sums-of-events at all — they are stateful, non-monotonic, and rollover-dependent — so the only
correct client-side representation of them is "whatever the server said last," refreshed on
every reconcile and updated inline (optimistically) for responsiveness during offline play.

### 2. Reuse of `POST /v1/progress/sync`'s returned `Stats` as the XP-reconciliation seam

`ProgressSyncService` already returns a fresh `Stats` object (`progress.getState`) from every
sync call; `OutboxRepository.flush()` already receives it and today throws it away. Reconciling
the local projection is implemented purely by no longer discarding this response — no new
endpoint, no new request shape, no new round trip. The response the app already fetches for every
successful flush **is** the reconciliation source.

### 3. Pull gated on an empty outbox

`GET /v1/progress/snapshot` (doc `95` §5) is only ever called immediately after a flush that left
the outbox empty. If the outbox is non-empty after flush, the pull is skipped entirely for that
sync cycle. This ordering guarantee — the server has necessarily already applied everything this
device has produced before the device is allowed to pull — means the pull can be a plain
overwrite of local tables from the snapshot, with no per-row "is my local copy or the server's
copy newer" comparison anywhere in the pull path.

### 4. `SET_LEVEL` LWW requires a new `user_stats.cefr_level_changed_at` column

CEFR level is a single scalar, not additive, so unlike XP it cannot be resolved by summing events
and unlike hearts/streaks there is no single "sole authority" recomputing it from other inputs —
two `SET_LEVEL` writes (one offline, one online, from the same or different devices) are a direct
value conflict that needs an explicit ordering rule. The server adds `cefr_level_changed_at`
(`timestamptz`) to `user_stats` and applies an incoming `SET_LEVEL` event's `changedAt` only if it
is strictly newer than the stored value — last-write-wins by the timestamp the client attached at
the moment the learner actually made the choice, not by whichever event happens to reach the
server first.

## Consequences

- ✅ No new endpoint needed for XP/hearts/streak reconciliation — `flush()` already has the data;
  it just needs to stop discarding it.
- ✅ Pull sync needs no per-row merge logic at all, because the empty-outbox gate makes "server
  state" and "this device's state" provably non-conflicting for every row the pull touches.
- ✅ XP double-counting is structurally impossible, not just tested-to-be-absent: a delta is in
  exactly one of {pending outbox, `baselineXp`} because the transaction that moves it from the
  former to the latter is atomic.
- ✅ `SET_LEVEL` composes with the existing idempotency ledger (`ProcessedEvent`,
  `applyIfNew`, doc `93` §3.4) — no new ledger; a stale `SET_LEVEL` is recorded as processed (so
  it's never retried) but is a no-op against `cefrLevel` itself.
- ⚠️ `LocalStatsProjection` retires the `"stats"` `content_cache` blob as the offline-stats source
  of truth — needs a real `ShikhiDatabase` `Migration` (not `fallbackToDestructiveMigration`),
  same discipline doc `93` §9 risk 2 already established for `MIGRATION_2_3`.
- ⚠️ `SET_LEVEL` LWW trusts the client's device clock for `changedAt`. A device with a
  significantly wrong clock can win or lose a race it should not. Accepted risk (doc `95` §8),
  same class as the existing `answeredAt` trust already extended to `PRACTICE_ANSWER` (doc `93`
  §5) — no server-side skew correction planned.
- ⚠️ A device that can never successfully flush also never pulls (§3 above is intentionally
  strict). This is judged correct, not a gap: such a device is visibly, fully offline — it is not
  silently drifting, which is the failure mode this ADR exists to prevent.

## Alternatives considered

- **A live local XP counter, incremented at answer time and synced as its own value:** rejected —
  reintroduces exactly the double-count risk event-sourcing (doc `93` §3.4) was adopted to avoid;
  a counter incremented locally and also reconstructable from replayed events has two sources of
  truth for the same number with no way to prove they agree.
- **Per-row pull merge** (compare local vs. remote `LocalWordProgress`/`LocalReviewProgress`
  timestamps and reconcile field-by-field): rejected for complexity disproportionate to the actual
  failure mode. The scenarios pull sync exists for — a second device, or a server-side correction
  — are already fully handled by "the server has seen everything this device has sent, so trust
  its answer," which the empty-outbox gate provides for free.
- **Client-supplied change ordering without a server timestamp** (e.g., a per-device monotonic
  counter, or "last event to arrive at the server wins" with no `changedAt` at all): rejected —
  neither reflects when the learner actually made the choice. Arrival order at the server is a
  function of network conditions, not intent; a device offline the longest could arrive last and
  incorrectly clobber a more recent decision made elsewhere. A client timestamp compared via LWW
  is the only ordering that tracks learner intent regardless of sync timing.
