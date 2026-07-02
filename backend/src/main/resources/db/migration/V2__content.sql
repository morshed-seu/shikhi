-- V2 — content & curriculum (LLD §2.2, §3.2). Content is versioned: a ContentVersion owns
-- an immutable-once-PUBLISHED tree of levels → units → lessons → exercises. Correctness
-- (option flags, accepted answers) is stored here but never sent to learners (grading is
-- server-side, M3). config JSONB carries type-specific render data for future exercise types.

create table content_versions (
    id           uuid primary key     default gen_random_uuid(),
    label        varchar(80) not null unique,
    status       varchar(20) not null default 'DRAFT'
                 check (status in ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
    published_at timestamptz,
    notes        varchar(500),
    created_at   timestamptz not null default now()
);

create table levels (
    id                 uuid primary key default gen_random_uuid(),
    content_version_id uuid        not null references content_versions (id) on delete cascade,
    code               varchar(40) not null,
    title_en           varchar(120) not null,
    title_bn           varchar(120) not null,
    ordinal            int         not null,
    constraint uq_levels_version_code unique (content_version_id, code),
    constraint uq_levels_version_ordinal unique (content_version_id, ordinal)
);

create table units (
    id       uuid primary key default gen_random_uuid(),
    level_id uuid        not null references levels (id) on delete cascade,
    code     varchar(40) not null,
    title_en varchar(120) not null,
    title_bn varchar(120) not null,
    ordinal  int         not null,
    constraint uq_units_level_ordinal unique (level_id, ordinal)
);

create table lessons (
    id       uuid primary key default gen_random_uuid(),
    unit_id  uuid        not null references units (id) on delete cascade,
    code     varchar(40) not null,
    title_en varchar(120) not null,
    title_bn varchar(120) not null,
    ordinal  int         not null,
    constraint uq_lessons_unit_ordinal unique (unit_id, ordinal)
);

create table exercises (
    id        uuid primary key default gen_random_uuid(),
    lesson_id uuid        not null references lessons (id) on delete cascade,
    type      varchar(20) not null
              check (type in ('MCQ', 'MATCH', 'WORD_BANK', 'FILL_BLANK',
                              'TYPE_TRANSLATION', 'LISTENING')),
    ordinal   int         not null,
    prompt_en varchar(400) not null,
    prompt_bn varchar(400) not null,
    media_ref varchar(300),
    config    jsonb,
    constraint uq_exercises_lesson_ordinal unique (lesson_id, ordinal)
);

-- For MCQ/MATCH. is_correct is server-side only (never serialized to learners).
create table exercise_options (
    id          uuid primary key default gen_random_uuid(),
    exercise_id uuid        not null references exercises (id) on delete cascade,
    text_en     varchar(300) not null,
    text_bn     varchar(300) not null,
    is_correct  boolean     not null default false,
    ordinal     int         not null,
    constraint uq_options_exercise_ordinal unique (exercise_id, ordinal)
);

-- Accepted answers for TYPE_TRANSLATION / FILL_BLANK (server-side grading, M3).
create table exercise_answers (
    id              uuid primary key default gen_random_uuid(),
    exercise_id     uuid         not null references exercises (id) on delete cascade,
    accepted_answer varchar(400) not null,
    is_primary      boolean      not null default false
);

-- Curated bilingual feedback (M3 hint precedence).
create table hints (
    id          uuid primary key default gen_random_uuid(),
    exercise_id uuid        not null references exercises (id) on delete cascade,
    trigger     varchar(20) not null default 'DEFAULT'
                check (trigger in ('DEFAULT', 'PATTERN', 'WRONG_ANSWER')),
    trigger_key varchar(120),
    text_en     varchar(500) not null,
    text_bn     varchar(500) not null
);

-- Reference: Bengali-speaker L1-transfer patterns (articles, tense/aspect, …).
create table l1_patterns (
    id      uuid primary key default gen_random_uuid(),
    code    varchar(40) not null unique,
    name_en varchar(120) not null,
    name_bn varchar(120) not null
);

create table exercise_patterns (
    exercise_id uuid not null references exercises (id) on delete cascade,
    pattern_id  uuid not null references l1_patterns (id) on delete cascade,
    primary key (exercise_id, pattern_id)
);

create index ix_units_level on units (level_id);
create index ix_lessons_unit on lessons (unit_id);
create index ix_exercises_lesson on exercises (lesson_id);
create index ix_options_exercise on exercise_options (exercise_id);
create index ix_answers_exercise on exercise_answers (exercise_id);
create index ix_content_versions_status on content_versions (status);
