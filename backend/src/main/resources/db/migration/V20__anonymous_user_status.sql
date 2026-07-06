-- V20 — allow ANONYMOUS (guest) accounts.
-- A guest learner is a real users row with status='ANONYMOUS' and no identity/credential.
-- It becomes 'ACTIVE' in place when claimed (email+password added), so no progress ever
-- migrates — every progress table already keys on this user_id. See ADR-0011.
--
-- The status check in V1 is an inline (unnamed) constraint; Postgres names it
-- users_status_check. Drop and re-add it widened to include ANONYMOUS.
alter table users drop constraint users_status_check;
alter table users add constraint users_status_check
    check (status in ('ACTIVE', 'SUSPENDED', 'DELETED', 'ANONYMOUS'));

-- Reaper support: quickly find stale guests that never converted (status + last-touched).
create index ix_users_anonymous_updated on users (updated_at)
    where status = 'ANONYMOUS';
