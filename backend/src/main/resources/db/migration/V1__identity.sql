-- V1 — identity tables (ADR-0005; LLD §3.1).
-- The `identity` module is foundational and depends on no other module's tables.
-- gen_random_uuid() is built into PostgreSQL 13+ (pgcrypto merged into core).

create table users (
    id           uuid primary key     default gen_random_uuid(),
    display_name varchar(100),
    ui_locale    varchar(2)  not null default 'bn' check (ui_locale in ('bn', 'en')),
    status       varchar(20) not null default 'ACTIVE'
                 check (status in ('ACTIVE', 'SUSPENDED', 'DELETED')),
    created_at   timestamptz not null default now(),
    updated_at   timestamptz not null default now(),
    deleted_at   timestamptz  -- soft-delete for account deletion/anonymization (BR-10/J6)
);

-- One row per sign-in method a user has linked (email now; phone/Google post-pilot, D5).
create table identities (
    id           uuid primary key     default gen_random_uuid(),
    user_id      uuid        not null references users (id) on delete cascade,
    provider     varchar(10) not null check (provider in ('EMAIL', 'PHONE', 'GOOGLE')),
    external_ref varchar(320) not null,  -- email address / phone / provider subject id
    verified_at  timestamptz,
    created_at   timestamptz not null default now(),
    constraint uq_identity_provider_ref unique (provider, external_ref)
);
create index ix_identities_user on identities (user_id);

-- Password material — only for the EMAIL provider. Never store plaintext.
create table credentials (
    id            uuid primary key     default gen_random_uuid(),
    user_id       uuid        not null unique references users (id) on delete cascade,
    password_hash varchar(255) not null,
    algo          varchar(20)  not null,
    updated_at    timestamptz  not null default now()
);

create table user_roles (
    user_id uuid        not null references users (id) on delete cascade,
    role    varchar(20) not null check (role in ('LEARNER', 'AUTHOR', 'ADMIN')),
    primary key (user_id, role)
);

-- Rotating refresh tokens: store only the SHA-256 hex (64 chars), never the raw token.
-- family_id ties a rotation chain together so a replayed (already-rotated) token can
-- revoke the whole family (replay detection).
create table refresh_tokens (
    id          uuid primary key     default gen_random_uuid(),
    user_id     uuid        not null references users (id) on delete cascade,
    token_hash  varchar(64) not null unique,
    family_id   uuid        not null,
    issued_at   timestamptz not null default now(),
    expires_at  timestamptz not null,
    revoked_at  timestamptz,
    device_info varchar(255)
);
create index ix_refresh_user on refresh_tokens (user_id);
create index ix_refresh_family on refresh_tokens (family_id);
