-- V21 — admit the C1 band to the progress/practice CEFR checks. The vocabulary table
-- already gained C1 in V17, and the C1 words were seeded in V19, but self-placement
-- (user_stats.cefr_level) and a pinned practice run (practice_sessions.cefr_level) still
-- rejected C1 — so picking C1 in the app failed at the DB. Widen both checks to match.

alter table user_stats drop constraint user_stats_cefr_level_check;
alter table user_stats add constraint user_stats_cefr_level_check
    check (cefr_level in ('A1', 'A2', 'B1', 'B2', 'C1'));

alter table practice_sessions drop constraint practice_sessions_cefr_level_check;
alter table practice_sessions add constraint practice_sessions_cefr_level_check
    check (cefr_level in ('A1', 'A2', 'B1', 'B2', 'C1'));
