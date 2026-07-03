-- V11 — vocabulary (dictionary layer). A flat, CEFR-tagged reference list of the words a
-- learner is expected to know, drawn from the Oxford 3000 (American English). Unlike the
-- curriculum tree (levels → units → lessons → exercises), this is standalone reference data
-- (like l1_patterns): every headword carries a Bengali gloss and a bilingual example so it can
-- back a word browser now and feed flashcards / auto-generated exercises later. Seeds arrive
-- per CEFR band in later migrations (A1 in V12, then A2/B1/B2) to keep each batch reviewable.

create table vocabulary (
    id             uuid primary key     default gen_random_uuid(),
    headword       varchar(80)  not null,
    -- disambiguates homographs (e.g. bank "money" vs "river"); null for the common case.
    sense_label    varchar(80),
    -- display form of the part(s) of speech, e.g. 'n.', 'v.', 'adj., n.'.
    part_of_speech varchar(60)  not null,
    cefr_level     varchar(2)   not null
                   check (cefr_level in ('A1', 'A2', 'B1', 'B2')),
    bn_gloss       varchar(300) not null,  -- Bengali meaning (বাংলা অর্থ)
    example_en     varchar(300),
    example_bn     varchar(300),
    ordinal        int          not null,  -- stable (alphabetical) order within a level
    created_at     timestamptz  not null default now(),
    constraint uq_vocabulary_head_sense_pos unique (headword, sense_label, part_of_speech)
);

-- Learners browse one CEFR band at a time, in order.
create index ix_vocabulary_level on vocabulary (cefr_level, ordinal);
