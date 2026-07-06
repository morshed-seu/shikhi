-- V17 — extends the vocabulary dictionary layer from the Oxford 3000 to the Oxford 5000: allow
-- the C1 band (the Oxford 5000 adds ~2000 words at B2-C1, on top of the Oxford 3000's A1-B2).
-- Seeds arrive in V18 (new B2 words) and V19 (the new C1 band).

alter table vocabulary drop constraint vocabulary_cefr_level_check;
alter table vocabulary add constraint vocabulary_cefr_level_check
    check (cefr_level in ('A1', 'A2', 'B1', 'B2', 'C1'));
