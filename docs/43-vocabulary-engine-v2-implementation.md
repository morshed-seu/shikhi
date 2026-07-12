# 43 — Vocabulary Engine v2: Implementation Decision Record (Hybrid)

**Project:** Shikhi (শিখি — "I learn")
**Document type:** Decision Record & Implementation Spec
**Status:** ACCEPTED — amends `42-vocabulary-engine-v2-design.md`
**Date:** 2026-07-12
**Builds on:** `42-vocabulary-engine-v2-design.md`, `41-architecture-lld.md`, `30-nfr.md`

> **How to read this:** Doc 42 is the full design exploration; this document records
> which parts of it we are building now, which parts of the *current* system we
> deliberately keep, and where the implementation deviates from doc 42 and why. Where
> the two documents disagree, **this document wins.**

---

## 1. Decision

Adopt doc 42's educational core — two-axis mastery/scheduling, a daily learning plan with
New/Weak/Review buckets, a 10-stage interval ladder, a graduation gate, a computed weak
bucket, and pure policy classes — **behind the existing E12 API and session tables**, with
the lesson-review module (M6) left untouched.

Rationale for the hybrid rather than the full doc-42 rewrite:

1. **Lesson-exercise review has live consumers.** `DashboardService` (E13 review-load
   tile) and `LessonSessionService` (lesson results) depend on `review_items` /
   `ReviewService`. Doc 42's plan to delete that module breaks both. Word-level review is
   therefore a *new* mechanism inside the practice module; unifying the two is future work.
2. **The E12 API contract is load-bearing.** The Android app consumes
   `POST /v1/practice/sessions[/{id}/answers|/rounds|/complete]`; its DTOs tolerate
   additive changes only. The planner slots in *behind* these endpoints — zero client
   changes.
3. **Existing plumbing is already right.** Idempotent answer submission
   (idempotency key + unique constraint), persisted per-exercise answered state
   (`practice_exercises`), `PracticeGenerator` and grading are kept as-is; doc 42's
   proposed `PracticeSession`/`PracticeSessionItem` tables would duplicate them.
4. **Scale machinery is premature.** The app is pre-launch on free-tier infra. Lazy
   plan-item materialization (§13.3), shadow mode, and staged A/B rollout (§11 phases 5–7)
   are replaced by a feature flag with automatic legacy fallback. Doc 42 itself concedes
   eager materialization is acceptable at small scale.

## 2. What is kept from the current implementation

| Kept | Where |
|---|---|
| E12 endpoints + response shapes | `practice/web/PracticeController` and DTOs |
| Exercise generation (5-type cycle, distractors) | `practice/service/PracticeGenerator` |
| Idempotent answers + race handling | `PracticeSessionService.submitAnswer` |
| Session/exercise persistence | `practice_sessions`, `practice_exercises` |
| Lesson review (Leitner, exercise-level) | `review` module — untouched |
| Level-up eligibility (`times_correct`-based) | `PracticeSessionService.levelUpEligible` |
| Mastery update rule (+1 correct / −2 wrong, clamp 0..5) | `PracticeWordProgress` |
| Legacy picker as fallback path | `practice/service/PracticeWordPicker` |

## 3. What is adopted from doc 42

- **Rename** `strength` → `masteryScore` (`mastery_score`), plus new fields `times_wrong`,
  `last_wrong_at` on `practice_word_progress`.
- **`ReviewProgress`** (word-level memory schedule): `review_stage`, `due_at`, review
  counters, `failure_streak`. Rows exist only for *graduated* words.
- **Graduation gate** (configurable): `masteryScore >= 3 AND timesCorrect >= 2 AND
  timesSeen >= 3`.
- **Interval ladder** (configurable): `[0, 1, 3, 7, 14, 30, 60, 120, 180, 365]` days.
- **Ladder transitions:** promotion **only when answered while due**; a wrong answer
  always demotes (`stage -= 2`, min 0, `failureStreak++`) and drops the word into
  weak-repair. Late reviews promote normally (never punished).
- **`DailyLearningPlan`** header (`UNIQUE(user_id, plan_date)`, optimistic `version`) +
  eagerly materialized `DailyLearningPlanItem` rows.
- **Allocation** 60/25/15 (config), redistribution priority review → weak → new, backlog
  protection, per-bucket CEFR split (new 90/10, weak 60/40, review unrestricted).
- **Computed weak bucket** (never persisted), priority
  `(5 − masteryScore)×3 + failureStreak×2 + recentMistakeBonus`.
- **Pure policies** — repositories fetch candidate lists; policies (no SQL, no JPA) decide.
- **Bucket mixing** in round composition — max 3 consecutive same-bucket items.
- **Free practice** once the plan is exhausted: weak + review + random already-learned
  words, never unseen — `next-round` stays endless, preserving current UX.
- **Data migration:** seed `review_progress` at stage 1 (due tomorrow) for words already
  at `mastery_score >= 3`.

## 4. Deviations from doc 42 (deltas)

| # | Doc 42 says | We do instead | Why |
|---|---|---|---|
| 1 | Replace `ReviewScheduler`/`ReviewItem` (lesson review) | Keep lesson review untouched | live E13/lesson consumers (§1.1) |
| 2 | New `PracticeSession`/`PracticeSessionItem` tables, resume | Reuse existing session tables; no resume endpoint | duplication; resume is a product decision, not scheduling |
| 3 | Lazy plan-item materialization (§13.3) | Eager materialization | ~100 rows/user/day is trivial at this scale |
| 4 | Shadow mode + 5%→100% A/B (§11) | Feature flag `shikhi.practice.planner.enabled` + automatic legacy fallback | pre-launch, no traffic to A/B |
| 5 | `LearningEvent` analytics table (§9.7) | Deferred | no analytics consumer yet |
| 6 | `recentMistakes` term in weak priority | `lastWrongAt` within 3 days → +2 | avoids needing an event log |
| 7 | Ladder promotion on any successful review | Promote only when answered **while due** | in a unified session, non-due appearances (weak/new path) must not inflate intervals |
| 8 | Plan expiry 04:00 local / 24h | `plan_date = LocalDate.now(UTC)` | UTC midnight = 06:00 Dhaka ≈ same effect; consistent with streak day boundaries (`ProgressService.today()`) |
| 9 | Package `learning/{planner,review,progress,…}` (§11.1) | Sub-packages of `com.shikhi.practice` (`schedule/`, `plan/`, `policy/`) | `com.shikhi.progress` already exists and owns XP/hearts/streaks; `learning` owns lessons |
| 10 | Adaptive accuracy policy in core (§6.4) | Last milestone; 7-day window computed from `practice_answers` timestamps | ship the deterministic core first, tune after |

## 5. Module placement

```
com.shikhi.practice
  schedule/   ReviewProgress, ReviewProgressRepository,
              WordReviewScheduler (interface), FixedIntervalScheduler
  plan/       DailyLearningPlan, DailyLearningPlanItem, repositories,
              DailyPlanService, PlanRoundComposer
  policy/     AllocationPolicy, RedistributionPolicy, WeakSelectionPolicy,
              NewWordSelectionPolicy, ReviewSelectionPolicy, BucketMixer   (all pure)
  service/    PracticeSessionService (orchestrates), WordProgressService
              (extracted from recordWordProgress), PracticeWordPicker (legacy/fallback)
```

Configuration: `PlannerProperties` bound to `shikhi.practice.planner.*` (modeled on
`RateLimitProperties`): `enabled`, `daily-capacity` (100), `new-percent`/`weak-percent`/
`review-percent` (60/25/15), graduation thresholds, `review-intervals-days` ladder.
`enabled=true` in dev, `false` in prod until VE4 verification. A `java.time.Clock` bean
(platform config) is introduced for all **new** time-dependent code.

## 6. Milestones & gates

| Milestone | Scope | Gate |
|---|---|---|
| VE0 | This document; doc 42 status flip | user sign-off (satisfied by plan approval, 2026-07-12) |
| VE1 | Flyway `V22__vocabulary_engine_v2.sql`: rename + new columns + `review_progress`, `daily_learning_plans`, `daily_learning_plan_items` (+ indexes per doc 42 §9.8 + `UNIQUE(plan_id, vocabulary_id)`), seed migration; entity + picker updates | full backend suite green (Testcontainers exercises the migration on real Postgres) |
| VE2 | `schedule/*`, `Clock` bean, `WordProgressService` (one transaction: mastery + graduation + ladder) | unit tests for every transition; suite green |
| VE3 | `plan/*` entities/repos, pure policies, idempotent `DailyPlanService.getOrCreateToday` (insert → catch duplicate → reload) | policy unit tests (no DB) + concurrency integration test; suite green |
| VE4 | `PlanRoundComposer`; `start`/`nextRound` flag-switched (plan-backed / free practice / legacy fallback) | suite green; `PracticeFlowIntegrationTest` unmodified in behavior with flag off; multi-day E2E via `Clock` |
| VE5 | Backlog protection tuning, adaptive accuracy policy, Micrometer metrics, structured logs (doc 42 §12.2) | suite green |

## 7. Out of scope / future work

- Unifying lesson-exercise review with word-level review.
- FSRS / SuperMemo scheduling (swap `WordReviewScheduler` implementation).
- Session resume endpoint; `LearningEvent` analytics; per-tier `LearningPolicy` variants.
- Removing `PracticeWordPicker` — only after the planner has been validated in production.

All percentages, thresholds, and the ladder are configuration, not code — they are
starting hypotheses to be tuned against real learner outcomes (doc 42 §15).
