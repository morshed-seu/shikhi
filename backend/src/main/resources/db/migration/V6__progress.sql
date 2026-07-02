-- V6 — progress & gamification (LLD §2.5, §3.3, §7). Per-learner XP/streak/hearts live in
-- user_stats (one row per learner, created lazily). user_progress records lesson completion
-- pinned to the content version played, with UNIQUE(user_id, lesson_id, content_version_id)
-- so a lesson counts once per version. processed_events is an idempotency ledger for the
-- offline sync batch (a key is applied at most once) — replays are safe (NFR-A4/DI1/N2).

create table user_stats (
    user_id           uuid primary key references users (id) on delete cascade,
    xp                int  not null default 0,
    rank              int  not null default 0,
    current_streak    int  not null default 0,
    longest_streak    int  not null default 0,
    last_active_date  date,
    hearts            int  not null default 5,
    hearts_updated_at timestamptz not null default now(),
    daily_goal        int  not null default 20
);

create table user_progress (
    id                 uuid primary key     default gen_random_uuid(),
    user_id            uuid        not null references users (id) on delete cascade,
    lesson_id          uuid        not null references lessons (id) on delete cascade,
    content_version_id uuid        not null references content_versions (id) on delete cascade,
    status             varchar(20) not null default 'NOT_STARTED'
                       check (status in ('NOT_STARTED', 'IN_PROGRESS', 'COMPLETED')),
    best_score         int         not null default 0,
    completed_at       timestamptz,
    constraint uq_user_progress unique (user_id, lesson_id, content_version_id)
);

create table processed_events (
    id              uuid primary key     default gen_random_uuid(),
    user_id         uuid         not null references users (id) on delete cascade,
    idempotency_key varchar(120) not null,
    processed_at    timestamptz  not null default now(),
    constraint uq_processed_events unique (user_id, idempotency_key)
);

create index ix_user_progress_user_version on user_progress (user_id, content_version_id);
