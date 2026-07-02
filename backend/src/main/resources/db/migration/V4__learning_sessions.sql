-- V4 — the learning module's play-through state (LLD §3.2, §4.1). A lesson_session is one
-- attempt at a lesson, pinned to the content version it started on (F4). answer_submissions
-- record each graded answer; UNIQUE(user_id, idempotency_key) makes client replays safe
-- (NFR-A4/DI1). XP/streak/unlocking aggregation across sessions is M4 — not modelled here.

create table lesson_sessions (
    id                 uuid primary key     default gen_random_uuid(),
    user_id            uuid        not null references users (id) on delete cascade,
    lesson_id          uuid        not null references lessons (id) on delete cascade,
    content_version_id uuid        not null references content_versions (id),
    status             varchar(20) not null default 'IN_PROGRESS'
                       check (status in ('IN_PROGRESS', 'COMPLETED', 'ABANDONED')),
    hearts_remaining   int         not null default 5,
    score              int         not null default 0,
    started_at         timestamptz not null default now(),
    completed_at       timestamptz
);

create table answer_submissions (
    id                   uuid primary key     default gen_random_uuid(),
    session_id           uuid         not null references lesson_sessions (id) on delete cascade,
    user_id              uuid         not null references users (id) on delete cascade,
    exercise_id          uuid         not null references exercises (id),
    idempotency_key      varchar(120) not null,
    correct              boolean      not null,
    matched_pattern_code varchar(40),
    -- Feedback is stored (both locales) so a replay returns a byte-identical verdict without
    -- re-grading; grading itself is deterministic, but this keeps idempotency self-contained.
    feedback_en          varchar(500) not null,
    feedback_bn          varchar(500) not null,
    submitted_at         timestamptz  not null default now(),
    constraint uq_answer_idem unique (user_id, idempotency_key)
);

create index ix_sessions_user on lesson_sessions (user_id);
create index ix_submissions_session on answer_submissions (session_id);
