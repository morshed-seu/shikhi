-- V16 — adaptive vocabulary practice (E12, LLD §2.8/§3.5). Practice sessions generate
-- exercises on the fly from the vocabulary layer (V11) at the learner's CEFR level. Each
-- generated item is persisted with a learner-visible payload and a server-only answer key
-- (correctness never reaches the client — same rule as curriculum grading). Replays are
-- safe via UNIQUE(user_id, idempotency_key), mirroring answer_submissions (NFR-DI1).

-- The learner's self-placed / confirmed CEFR band (US-12.1). Owned by the progress module.
alter table user_stats
    add column cefr_level varchar(2) not null default 'A1'
    check (cefr_level in ('A1', 'A2', 'B1', 'B2'));

-- One continuous practice run. The level is pinned at start so a mid-session level change
-- does not reshuffle an active session.
create table practice_sessions (
    id            uuid primary key     default gen_random_uuid(),
    user_id       uuid        not null references users (id) on delete cascade,
    cefr_level    varchar(2)  not null check (cefr_level in ('A1', 'A2', 'B1', 'B2')),
    status        varchar(20) not null default 'IN_PROGRESS'
                  check (status in ('IN_PROGRESS', 'COMPLETED', 'ABANDONED')),
    rounds_played int         not null default 0,
    correct_count int         not null default 0,
    total_count   int         not null default 0,
    started_at    timestamptz not null default now(),
    completed_at  timestamptz
);

-- A generated exercise. payload is what the learner sees (options/tokens WITHOUT
-- correctness flags); answer_key is server-only (correct option id / accepted answers).
create table practice_exercises (
    id            uuid primary key     default gen_random_uuid(),
    session_id    uuid        not null references practice_sessions (id) on delete cascade,
    round         int         not null,
    ordinal       int         not null,
    vocabulary_id uuid        not null references vocabulary (id),
    type          varchar(20) not null
                  check (type in ('WORD_MEANING', 'MEANING_WORD', 'SENTENCE_GAP',
                                  'SENTENCE_BUILD', 'TYPE_WORD')),
    prompt_en     varchar(400) not null,
    prompt_bn     varchar(400) not null,
    payload       jsonb        not null,
    answer_key    jsonb        not null,
    -- null until answered; set by the first (non-replayed) submission.
    answered_correct boolean
);

create table practice_answers (
    id              uuid primary key      default gen_random_uuid(),
    session_id      uuid         not null references practice_sessions (id) on delete cascade,
    user_id         uuid         not null references users (id) on delete cascade,
    exercise_id     uuid         not null references practice_exercises (id) on delete cascade,
    idempotency_key varchar(120) not null,
    correct         boolean      not null,
    submitted_at    timestamptz  not null default now(),
    constraint uq_practice_answer_idem unique (user_id, idempotency_key)
);

-- Per-word learning state: drives weak-word priority (low strength first), the 70/30 band
-- mix bookkeeping, and level-up eligibility (US-12.6/12.7). strength 0..5; unseen words
-- have no row and are treated as strength 2 by the picker.
create table practice_word_progress (
    user_id       uuid        not null references users (id) on delete cascade,
    vocabulary_id uuid        not null references vocabulary (id) on delete cascade,
    times_seen    int         not null default 0,
    times_correct int         not null default 0,
    strength      int         not null default 2 check (strength between 0 and 5),
    last_seen_at  timestamptz not null default now(),
    primary key (user_id, vocabulary_id)
);

create index ix_practice_sessions_user on practice_sessions (user_id);
create index ix_practice_exercises_session on practice_exercises (session_id, round, ordinal);
create index ix_practice_answers_session on practice_answers (session_id);
