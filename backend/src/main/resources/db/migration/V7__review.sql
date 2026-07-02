-- V7 — spaced-repetition review (LLD §2.6, §3.4). One review_item per (learner, exercise):
-- when a learner misses an exercise in a lesson it enters box 1; correct recalls promote it
-- to higher Leitner boxes with longer intervals, a miss sends it back to box 1. due_at drives
-- the review queue. UNIQUE(user_id, exercise_id) keeps a single schedule per exercise.

create table review_items (
    id          uuid primary key     default gen_random_uuid(),
    user_id     uuid        not null references users (id) on delete cascade,
    exercise_id uuid        not null references exercises (id) on delete cascade,
    box_level   int         not null default 1,
    due_at      timestamptz not null,
    last_result boolean,
    updated_at  timestamptz not null default now(),
    constraint uq_review_items unique (user_id, exercise_id)
);

create index ix_review_items_user_due on review_items (user_id, due_at);
