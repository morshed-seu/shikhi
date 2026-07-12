-- V22 — vocabulary engine v2, milestone VE1 (doc 43 §3/§5/§6): schema for word-level
-- spaced-repetition review and daily learning plans, adopted from doc 42 behind the
-- existing E12 practice API. Schema + rename only — planner/scheduler logic lands in
-- VE2-VE4.

-- practice_word_progress.strength becomes mastery_score (same 0..5 semantics; the check
-- constraint follows the column rename automatically). times_wrong/last_wrong_at feed the
-- weak-word priority's "recent mistake" bonus (doc 43 §3, deviation #6) without needing an
-- event log.
alter table practice_word_progress rename column strength to mastery_score;
alter table practice_word_progress
    add column times_wrong   int not null default 0,
    add column last_wrong_at timestamptz;

-- One row per learner per GRADUATED word (mastery_score >= 3 and times_correct >= 2 and
-- times_seen >= 3 — doc 43 §3 graduation gate). Tracks position on the 10-stage interval
-- ladder; due_at drives the REVIEW bucket of the daily plan. Same FK style as
-- practice_word_progress (V16): both parents cascade on delete.
create table review_progress (
    user_id            uuid        not null references users (id) on delete cascade,
    vocabulary_id      uuid        not null references vocabulary (id) on delete cascade,
    review_stage       int         not null default 0,
    due_at             timestamptz not null,
    last_reviewed_at   timestamptz,
    review_count       int         not null default 0,
    successful_reviews int         not null default 0,
    failed_reviews     int         not null default 0,
    failure_streak     int         not null default 0,
    last_failure_at    timestamptz,
    created_at         timestamptz not null default now(),
    updated_at         timestamptz not null default now(),
    primary key (user_id, vocabulary_id)
);

create index ix_review_progress_user_due on review_progress (user_id, due_at);
create index ix_review_progress_user_stage on review_progress (user_id, review_stage);

-- One header per learner per day (doc 43 §3 DailyLearningPlan). UNIQUE(user_id, plan_date)
-- backs the idempotent getOrCreateToday (VE3); optimistic `version` guards concurrent
-- completions. config_snapshot freezes the PlannerProperties used to build this plan so a
-- mid-day config change never reshuffles an in-progress day.
create table daily_learning_plans (
    id                uuid primary key     default gen_random_uuid(),
    user_id           uuid        not null references users (id) on delete cascade,
    plan_date         date        not null,
    status            varchar(20) not null
                      check (status in ('PENDING', 'ACTIVE', 'COMPLETED', 'EXPIRED', 'CANCELLED')),
    daily_capacity    int         not null,
    planned_new       int         not null,
    planned_weak      int         not null,
    planned_review    int         not null,
    remaining_new     int         not null,
    remaining_weak    int         not null,
    remaining_review  int         not null,
    config_snapshot   jsonb,
    version           bigint      not null default 0,
    created_at        timestamptz not null default now(),
    completed_at      timestamptz,
    constraint uq_daily_learning_plans_user_date unique (user_id, plan_date)
);

-- Eagerly materialized plan rows (doc 43 deviation #3), one per word slotted into
-- New/Weak/Review in serve order. consumed_session_id/consumed_at record which practice
-- session actually served the word, for bucket-mixed round composition (VE4).
create table daily_learning_plan_items (
    id                  uuid primary key     default gen_random_uuid(),
    plan_id             uuid        not null references daily_learning_plans (id) on delete cascade,
    vocabulary_id       uuid        not null references vocabulary (id) on delete cascade,
    bucket              varchar(10) not null check (bucket in ('NEW', 'WEAK', 'REVIEW')),
    sequence            int         not null,
    status              varchar(20) not null default 'PENDING'
                        check (status in ('PENDING', 'IN_PROGRESS', 'COMPLETED', 'SKIPPED')),
    consumed_session_id uuid,
    consumed_at         timestamptz,
    constraint uq_daily_learning_plan_items_plan_word unique (plan_id, vocabulary_id)
);

create index ix_daily_learning_plan_items_status on daily_learning_plan_items (plan_id, status);
create index ix_daily_learning_plan_items_bucket on daily_learning_plan_items (plan_id, bucket);
create index ix_daily_learning_plan_items_sequence on daily_learning_plan_items (plan_id, sequence);

-- Data migration (doc 42 §11.2, doc 43 §3 last bullet): seed review_progress at stage 1, due
-- tomorrow, for every word already at mastery_score >= 3 — preserving the existing confidence
-- value under its new name. This is deliberately mastery_score alone, NOT the full three-part
-- graduation gate (mastery_score >= 3 AND times_correct >= 2 AND times_seen >= 3) that governs
-- graduation going forward: doc 42 §11.2 backfills on the single threshold every pre-existing
-- learner already has, so no one loses progress just because the gate grew two more legs on
-- the same day this migration runs.
insert into review_progress (user_id, vocabulary_id, review_stage, due_at, created_at, updated_at)
select user_id, vocabulary_id, 1, now() + interval '1 day', now(), now()
from practice_word_progress
where mastery_score >= 3;
