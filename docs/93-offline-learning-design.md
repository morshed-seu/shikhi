# 93 — Offline Learning on Android: Engineering Design

**Project:** Shikhi (শিখি — "I learn")
**Document type:** Subsystem Architecture & Implementation Specification
**Author role:** Architect
**Status:** ACCEPTED
**Version:** 1.0
**Audience:** Android/backend engineers, AI coding agents, product owners
**Builds on:** `21-prd-android.md` (§Phase C names the gap this closes), `42-vocabulary-engine-v2-design.md` + `43-vocabulary-engine-v2-implementation.md` (VE1–VE5, the practice engine being ported), `adr/0012-native-android-client.md`
**Gate prefix:** `OF` (`OF0`–`OF7`)

---

## 1. Purpose

Today the Android app can browse cached vocabulary/curriculum content offline
(`CachedContentRepository`, MA4), but **playing** a lesson or a vocabulary-practice session
always requires the network, because grading happens server-side. This document specifies
how to make the full learning experience — vocabulary browsing, vocabulary practice (spaced
repetition), curriculum/lesson play, and word pronunciation — bundled into the app and usable
with **zero API calls**, while still syncing progress back to the server opportunistically
when connectivity returns.

**Goal:** a learner with no network connection can install the app once (over Wi-Fi/data) and
thereafter browse, practice, and complete lessons indefinitely offline, with progress
reconciling correctly once they reconnect.

## 2. Current State

- `CachedContentRepository.kt` (`android/app/src/main/java/com/shikhi/app/data/content/`) is
  network-first with a Room JSON-blob cache fallback (`content_cache` table, `ContentCache.kt`)
  for `curriculum()`, `stats()`, `vocabulary(level)` only. Its own code comment states: "lesson
  play needs the network (grading is server-side)."
- `LessonViewModel.kt` already holds full local play-through state (exercise index, selected
  answer, verdict, correct count) and only calls the network to grade (`check()`) and finalize
  (`complete()`); a failed `complete()` already buffers into the outbox.
- `PracticeViewModel.kt` has **no offline handling at all** — `check()`, `keepGoing()`,
  `finish()` are bare network calls; a failed `check()` silently fabricates a "wrong" verdict
  and the real answer is lost. This is a materially bigger gap than lessons.
- Backend vocabulary (`vocabulary` table, `V11__vocabulary.sql`/`V17`) is ~5,011 rows of pure
  text — no audio/image columns — served read-only by `VocabularyController`.
- The vocabulary-practice engine (`com.shikhi.practice`) and lesson grading
  (`com.shikhi.learning.grading`) are both written as pure, dependency-free Java with no
  Spring/JPA coupling — explicitly designed as portable/swappable seams (see `42-...md` §4.1,
  §7.2). This is what makes a Kotlin port tractable rather than a rewrite.
- The web frontend just shipped word pronunciation (`ed74fd1`, `938e39a`) via the browser's
  Web Speech API — client-side TTS with **no new data or backend changes**. Android has no
  equivalent yet.
- `AppModule.kt` provides a single `ShikhiDatabase` via
  `.fallbackToDestructiveMigration(dropAllTables = true)` — acceptable today (cache/outbox
  only) but not once durable per-user mastery/review state lives there.

## 3. Architecture Decisions

### 3.1 Content bundling: JSON assets + first-run Room import

Rejected alternative: Room's `createFromAsset` prebuilt-SQLite-file approach. Content volume
(~5,011 vocabulary rows + ~13 pilot lesson exercises, estimated 1.5–2.5 MB as JSON) is small
enough that the prebuilt-DB approach's zero-parse-cost advantage isn't worth its schema
fragility (SQLite version/`room_master_table` mismatches) or the extra offline-tooling
language it would require. JSON + a one-time importer reuses the existing
Kotlin/kotlinx.serialization toolchain and matches this project's bias toward explicit,
hand-written code over generated/opaque artifacts (ADR-0012).

### 3.2 Two Room databases

- **`ShikhiDatabase`** (existing, mutable): `content_cache`, `outbox_events`, plus new local
  progress/session tables (§4.2). Gets real `Migration` objects going forward — this feature
  is what makes durable per-user state worth preserving across schema bumps; the blanket
  `fallbackToDestructiveMigration` is retired as part of `OF1`/`OF4`.
- **`ContentDatabase`** (new, read-only): bundled vocabulary/curriculum/answer-key/hint
  tables (§4.1). Reseeded wholesale from a new APK build, never migrated row-by-row — always
  safe to drop and reseed since it's a pure function of the bundled asset. Kept separate so the
  answer-key tables sit behind a DAO surface only the grading engine touches, never the
  UI-facing read paths (mirrors the server-side invariant that answer keys never reach
  `LessonView`).

### 3.3 Grading mode: online-first, offline-fallback

Online users keep today's behavior exactly — the server grades live, hearts/XP/streak update
in real time. The app resolves to the local grading path **only** when the device has no
connectivity. This is implemented as one abstraction with two implementations, selected by a
connectivity check, not two diverging code paths:

```kotlin
interface LessonPlaySource {
    suspend fun start(lessonId: String): PlayableLesson
    fun grade(exercise: Exercise, answer: JsonObject): Verdict
    suspend fun complete(sessionId: String, correctCount: Int): LessonResult
}

interface PracticePlaySource {
    suspend fun start(cefrLevel: String): PracticeRoundLocal
    fun grade(exercise: PracticeExercise, answer: JsonObject): Verdict
    suspend fun nextRound(sessionId: String): PracticeRoundLocal
    suspend fun complete(sessionId: String): PracticeResult
}
```

`RemoteLessonSource`/`RemotePracticeSource` wrap today's `ContentApi`/`LearningApi`/
`PracticeApi` calls unchanged. `LocalLessonSource`/`LocalPracticeSource` are new, backed by
`ContentDatabase` + the ported grading/scheduling engines (§4.3) + the new local mutable
tables (§4.2). `LessonViewModel`/`PracticeViewModel` depend only on the interface; a
connectivity check (Android `ConnectivityManager`, observed once per session start — not
polled mid-session, to avoid mid-answer source switches) picks which implementation Hilt
binds for that session.

### 3.4 Sync: event-sourced, never state-merge

The client never uploads its locally-computed `masteryScore`/`reviewStage`. It uploads the
same primitive the server already treats as its unit of truth — a stream of
`(vocabularyId, correct, idempotencyKey)` events, replaying through the server's existing
idempotent transaction (`ProgressEventApplier.applyIfNew`). The server remains the sole
authority that recomputes mastery/review state. This means:

- Client-side port arithmetic does **not** need bit-for-bit parity with the server for
  correctness — only plausible local UX (don't re-serve an already-mastered word 40 times in
  one offline session).
- Two-device offline divergence is not a design problem: each device's buffered events apply
  in whatever order they arrive, against the server's state at application time — exactly like
  two devices independently practicing online today.

### 3.5 Pronunciation: on-device TTS, not bundled audio

Mirrors the web's approach exactly. A `Pronouncer` wrapper (`ui/util/Pronouncer.kt`) around
`android.speech.tts.TextToSpeech`, feature-detected via `TextToSpeech.OnInitListener`'s result
code (some devices lack the en-US voice pack — the "listen" control is hidden, not assumed
available, same rule as web's `isSpeechSupported()`). Only ever speaks text already rendered
to the learner — no answer-key leak risk, no new bundled content, no dependency on
`ContentDatabase` or the `PlaySource` abstraction. Independent workstream, folded into `OF2`
(vocabulary browsing) and `OF4` (practice) because that's where the UI touch points are.

## 4. Data Model

### 4.1 `ContentDatabase` (bundled, read-only)

Mirrors backend content entities (`backend/src/main/java/com/shikhi/content/domain/`,
`V2__content.sql`, `V11__vocabulary.sql`):

```
LocalVocabulary(id, headword, senseLabel, partOfSpeech, cefrLevel, bnGloss, exampleEn, exampleBn, ordinal)
LocalLevel(id, code, titleEn, titleBn, ordinal)
LocalUnit(id, levelId, code, titleEn, titleBn, ordinal)
LocalLesson(id, unitId, code, titleEn, titleBn, ordinal)
LocalExercise(id, lessonId, type, ordinal, promptEn, promptBn, mediaRef, configJson, patternTags)
LocalExerciseOption(id, exerciseId, textEn, textBn, isCorrect, ordinal)   -- answer key
LocalExerciseAnswer(id, exerciseId, acceptedAnswer, isPrimary)            -- answer key
LocalHint(id, exerciseId, trigger, triggerKey, textEn, textBn)
```

Two DAO surfaces: a public UI-shaped one (no `isCorrect`/`acceptedAnswer`) used by
`CachedContentRepository`; an answer-key surface used only by the grading engine (`OF3`).

### 4.2 New tables in `ShikhiDatabase` (mutable)

Scoped to local play state — an input to the sync stream, not a 1:1 mirror of server tables:

```
LocalWordProgress(userId, vocabularyId, timesSeen, timesCorrect, timesWrong, masteryScore, lastWrongAt, lastSeenAt)
LocalReviewProgress(userId, vocabularyId, reviewStage, dueAt, lastReviewedAt, reviewCount, successfulReviews, failedReviews, failureStreak, lastFailureAt)
LocalPracticeSession(id, userId, cefrLevel, status, roundsPlayed, correctCount, totalCount, startedAt, completedAt)
LocalPracticeExercise(id, sessionId, round, ordinal, vocabularyId, type, promptEn, promptBn, payloadJson, answerKeyJson, answeredCorrect)
LocalLessonSession(id, lessonId, contentVersionId, status, heartsRemaining, score, startedAt, completedAt)
```

`LocalPracticeExercise` rows are ephemeral (safe to prune after their answer syncs).
`LocalLessonSession` is secondary/resume-state, since `LessonViewModel` already holds this
in-memory during a session.

### 4.3 Kotlin ports (source of truth: the backend files named)

| Port target | Source | Fidelity note |
|---|---|---|
| `PracticeGenerator` | `practice/service/PracticeGenerator.java` | Pure templating, `Random`-seeded — port the `TYPE_CYCLE`, MCQ two-pass distractor algorithm, `MIN/MAX_BUILD_TOKENS` verbatim |
| Word picker | `practice/service/PracticeWordPicker.java` | Native SQL → Room `@Query`: weakest-first `COALESCE(mastery_score, 2), RANDOM()`, same-band distractor pool |
| Mastery/review state machine | `practice/service/WordProgressService.java` + `practice/config/PlannerProperties.java` (hardcode the 3 graduation-threshold defaults as Kotlin constants — daily-planner *config* isn't ported, just its static defaults) | `+1`/`-2` mastery clamp 0–5; graduation `masteryScore>=3 AND timesCorrect>=2 AND timesSeen>=3`; wrong review answers demote 2 stages |
| Review ladder | `practice/schedule/FixedIntervalScheduler.java` | Day ladder `[0,1,3,7,14,30,60,120,180,365]`, clamp to range |
| Lesson grading | `learning/grading/RuleBasedGradingStrategy.java` | `gradeMcq`/`gradeText`/`gradeWordBank` + `WRONG_ANSWER→PATTERN→DEFAULT` hint precedence |
| Text normalization | `learning/grading/AnswerNormalizer.java` | 3-line verbatim port: trim → collapse whitespace → lowercase → strip trailing `.!?।` |
| Practice grading switch | `practice/service/PracticeSessionService.java` `grade()` | MCQ types compare `selectedOptionId`; `SENTENCE_BUILD`/`TYPE_WORD` normalize + match `accepted` |

**Not ported**: `PlanRoundComposer`, `DailyPlanService`, `practice/policy/*` (VE3/VE4
daily-planner layer) — feature-flagged off in prod today (`PlannerProperties.enabled=false`,
confirmed via `PracticeSessionService.generateRound`). Revisit only if the server-side flag is
ever flipped on.

## 5. Sync Event Shapes

New outbox event type, alongside the existing `ANSWER`/`COMPLETE_LESSON`:

```json
{
  "type": "PRACTICE_ANSWER",
  "idempotencyKey": "<uuid, minted once at enqueue time>",
  "payload": {
    "vocabularyId": "<uuid>",
    "correct": true,
    "answeredAt": "2026-07-18T10:15:00Z"
  }
}
```

Backend: `ProgressEventApplier` gains a `"PRACTICE_ANSWER"` case calling both
`ProgressService.recordPracticeAnswer` (XP/hearts) and `WordProgressService.recordAnswer`
(mastery/review ladder) — **do not** route this through the existing `"ANSWER"` case, which
only calls `ProgressService.recordAnswer` (hearts-only, no XP, no mastery/review update at
all). `WordProgressService.recordAnswer` gains an `Instant`-taking overload for the optional
`answeredAt`, falling back to `clock.instant()` when absent (today's online callers pass
nothing — unaffected). `answeredAt` avoids compressing/distorting review-ladder due-dates when
a multi-day-old offline session finally syncs.

Lesson completion needs no new event type: `ProgressService.completeLesson` already awards XP
as an idempotent, first-completion-gated lump sum, so the existing `COMPLETE_LESSON` event
covers offline lesson completion once grading is local. Accepted gap: it cannot seed the
separate Leitner `review_items` queue (`com.shikhi.review`, a different subsystem from the
vocabulary engine) for exercises missed offline — non-goal this phase (§7).

## 6. Content Export & Regeneration (manual, pre-release)

1. Backend: a dev-profile-only endpoint or one-off `main()` CLI reusing existing repositories,
   producing `{ vocabulary: [...], curriculum: { levels/units/lessons/exercises/options/answers/hints } }`.
2. Copy the export into `android/app/src/main/assets/content-seed/{vocabulary.json, curriculum.json}`,
   bump a `CONTENT_SEED_VERSION` constant.
3. `ContentSeedImporter` runs once per app install (or when the asset's version exceeds the
   one recorded in the existing `sessionDataStore` pattern), bulk-inserting into
   `ContentDatabase` in one transaction.
4. Documented as a manual, repeatable developer process in `docs/90-runbook.md` — no in-app
   update pipeline this phase (confirmed non-goal, §7).

## 7. Non-Goals

- No in-app content-update pipeline — bundled content ships with the APK; regenerating the
  seed (§6) is manual and pre-release.
- No bundled/recorded audio — pronunciation is on-device TTS only (§3.5); no new data columns
  or export fields for it.
- No `MATCH` exercise type — unsupported/ungraded server-side already
  (`RuleBasedGradingStrategy.grade` throws `UNSUPPORTED_EXERCISE`), stays unsupported offline.
- No offline account creation/auth — offline mode assumes an already-authenticated session
  (tokens already persisted via `DataStoreTokenStore`).
- No offline seeding of the separate Leitner `review_items` queue for lessons missed offline
  (§5) — the vocabulary engine's own review ladder, the actual "spaced repetition" ask, is
  fully covered.
- No porting of the VE3/VE4 daily-planner layer (§4.3) — disabled in prod today.

## 8. Gate Sequence

See the approved plan (`~/.claude/plans/i-want-to-let-zazzy-boole.md`) for the full
gate-by-gate scope and exit criteria: `OF0` (this doc) → `OF1` (content export + bundling) →
`OF2` (repository swap + vocab pronunciation) → `OF3` (local lesson grading) → `OF4` (local
practice engine + practice pronunciation) → `OF5` (offline sync events) → `OF6`
(reconciliation + DI wiring) → `OF7` (release verification, on-device check).

## 9. Open Risks

1. **SRS timestamp fidelity**: without the optional `answeredAt` (§5), replaying a multi-day-old
   offline session stamps `dueAt`/`lastSeenAt` as sync-time "now," distorting review intervals.
   Mitigated by adding `answeredAt` in `OF5` even though not strictly required for correctness.
2. **`ShikhiDatabase` migration strategy**: this feature is the forcing function to stop using
   `fallbackToDestructiveMigration(dropAllTables = true)`. A real `Migration` must land in
   `OF1`/`OF4`, not as an afterthought — silently wiping a learner's local mastery/review state
   on the next unrelated schema bump would be a real regression once shipped.
3. **Guest-account offline play**: guests already work offline for auth (tokens cached);
   confirm `userId` scoping in the new local tables survives a guest → claimed-account
   transition (`POST /auth/claim`) without orphaning outbox events minted under the guest
   identity. Exercise explicitly in `OF6`.
4. **Content-version coupling**: `LessonView.contentVersion`/`COMPLETE_LESSON`'s
   `contentVersionId` assumes one live content version (true today). If a second version
   publishes before the next content-bundle refresh, an offline-completed lesson could sync
   against a stale version. Low risk given the no-runtime-updates non-goal; worth a runbook
   callout if content versioning ever becomes live.
