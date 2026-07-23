-- V23 — track when the CEFR level last changed (UO1). Level is not additive, so a stale
-- offline SET_LEVEL event must not clobber a newer one; the server needs a per-row timestamp
-- to compare against, applying last-write-wins on sync.

alter table user_stats add column cefr_level_changed_at timestamptz;
