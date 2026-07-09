# ADR-0013 — Client-side grading + batched submission for web practice

**Status:** Accepted (2026-07-09, Epic E12 — see Delivery Plan `80` §Practice track)
**Related:** ADR-0006 (AI grading seam), ADR-0012 (Android client), Security & Privacy `50`, Test Strategy `60` §5, API contract `43`, PRD E12

## Context
The web adaptive-practice flow (E12) makes one network round-trip **per answer**:
`POST /v1/practice/sessions/{sessionId}/answers` grades the answer server-side and returns a
verdict, and the UI blocks on that call before showing correct/incorrect. Every tap pays the
latency, and the "moment of learning" (the wrong-answer reveal, US-12.5) is coupled to network
health — poor on the mid-range/flaky-connectivity devices Shikhi targets.

Practice is **low-stakes, self-directed review** — unlike graded lesson sessions, nothing rides
on the learner not seeing an answer early (there is no score of record, no anti-cheat surface).
This is materially different from the lesson flow, whose server-only answer key is a deliberate
integrity control.

## Decision
For the **practice** module only, move per-answer grading to the **client**, and persist answers
in a **single batch** when the learner leaves the flow.

- The practice round response (`POST /practice/sessions`, `.../rounds`) now carries a
  per-exercise **`solution`** — `{ correctOptionId?, accepted?, reveal }` — so the browser grades
  instantly and locally (reproducing the server's normalization rules,
  `learning/grading/AnswerNormalizer`).
- A new endpoint **`POST /practice/sessions/{sessionId}/answers/batch`** accepts all buffered
  answers, **re-grades them server-side authoritatively** against the stored `answerKey`
  (client verdicts are display-only and never trusted), applies XP/hearts/streak/word-strength,
  and returns the resulting `Stats`. It is idempotent per answer `idempotencyKey`.
- The web client buffers answers locally and flushes the batch on every exit path — next round,
  finish (before `complete`), navigate-away, and tab close (`visibilitychange`/`sendBeacon`) —
  routed through the existing offline **outbox** (`frontend/src/api/outbox.ts`) for durability.
- The existing per-answer endpoint is **retained unchanged** for the Android client (ADR-0012),
  which continues server-side per-answer grading and may migrate later.

## Consequences
- ✅ Instant, offline-tolerant feedback on web; one write per round instead of ten.
- ✅ Progress integrity preserved: the server remains the source of truth for XP/hearts/strength;
  idempotency keys keep batch replay (and the flush-then-complete sequence) safe.
- ⚠️ **Practice solutions are now served to the client** — a deliberate, scoped reversal of the
  "answer key never serialized" property. It applies to **practice only**; graded lesson sessions
  keep their server-only answer key. Test Strategy `60` §5 is amended to scope that gate to graded
  lessons and to add practice-specific expectations. Security review of `50` covers this as an
  accepted low-stakes exposure.
- ⚠️ Grading logic now exists in two places (server Java + client TS). The client grader is
  covered by unit tests over all five exercise types, and the server re-grade is the authority, so
  a client/server divergence affects only transient display, never stored progress.
- ⚠️ Web and Android now diverge on the practice submission path until Android migrates.

## Alternatives considered
- **Keep server round-trip, optimize latency:** does not deliver offline-tolerant instant
  feedback and keeps the moment-of-learning coupled to the network. Rejected against the E12 goal.
- **Ship an opaque/HMAC token instead of the plaintext solution** so the client can verify but not
  read the answer: security theater for a low-stakes flow, and the client still needs the reveal
  text to render the wrong-answer explanation. Rejected as complexity without benefit.
- **Batch-only, no local grading (submit at end, show results after):** loses the per-answer
  instant reveal that is the whole point. Rejected.
