-- V3 — a small PUBLISHED pilot content version so the curriculum/lesson read paths have
-- real bilingual data from first boot. Fixed UUIDs keep the seed deterministic (tests and
-- demos can reference them). Larger, linguistically-reviewed content is the M7 track.

insert into content_versions (id, label, status, published_at, notes)
values ('11111111-1111-1111-1111-111111111111', 'pilot-v1', 'PUBLISHED', now(),
        'Seed pilot content (M2).');

insert into levels (id, content_version_id, code, title_en, title_bn, ordinal)
values ('20000000-0000-0000-0000-000000000001',
        '11111111-1111-1111-1111-111111111111', 'A1', 'Beginner', 'প্রাথমিক', 1);

insert into units (id, level_id, code, title_en, title_bn, ordinal)
values ('30000000-0000-0000-0000-000000000001',
        '20000000-0000-0000-0000-000000000001', 'A1-U1', 'Greetings', 'শুভেচ্ছা', 1);

insert into lessons (id, unit_id, code, title_en, title_bn, ordinal)
values ('40000000-0000-0000-0000-000000000001',
        '30000000-0000-0000-0000-000000000001', 'A1-U1-L1', 'Hello', 'হ্যালো', 1),
       ('40000000-0000-0000-0000-000000000002',
        '30000000-0000-0000-0000-000000000001', 'A1-U1-L2', 'Introductions', 'পরিচয়', 2);

-- Lesson 1 exercises
insert into exercises (id, lesson_id, type, ordinal, prompt_en, prompt_bn)
values ('50000000-0000-0000-0000-000000000001',
        '40000000-0000-0000-0000-000000000001', 'MCQ', 1,
        'Which word is a greeting?', 'কোন শব্দটি একটি শুভেচ্ছা?'),
       ('50000000-0000-0000-0000-000000000002',
        '40000000-0000-0000-0000-000000000001', 'TYPE_TRANSLATION', 2,
        'Translate to English: আমি ভালো আছি', 'ইংরেজিতে অনুবাদ করুন: আমি ভালো আছি');

insert into exercise_options (exercise_id, text_en, text_bn, is_correct, ordinal)
values ('50000000-0000-0000-0000-000000000001', 'Hello', 'হ্যালো', true, 1),
       ('50000000-0000-0000-0000-000000000001', 'Goodbye', 'বিদায়', false, 2),
       ('50000000-0000-0000-0000-000000000001', 'Table', 'টেবিল', false, 3);

insert into exercise_answers (exercise_id, accepted_answer, is_primary)
values ('50000000-0000-0000-0000-000000000002', 'I am fine', true),
       ('50000000-0000-0000-0000-000000000002', 'I am well', false),
       ('50000000-0000-0000-0000-000000000002', 'I''m fine', false);

-- Lesson 2 exercises
insert into exercises (id, lesson_id, type, ordinal, prompt_en, prompt_bn)
values ('50000000-0000-0000-0000-000000000003',
        '40000000-0000-0000-0000-000000000002', 'MCQ', 1,
        'Which word means "name"?', 'কোন শব্দের অর্থ "নাম"?');

insert into exercise_options (exercise_id, text_en, text_bn, is_correct, ordinal)
values ('50000000-0000-0000-0000-000000000003', 'Name', 'নাম', true, 1),
       ('50000000-0000-0000-0000-000000000003', 'Age', 'বয়স', false, 2),
       ('50000000-0000-0000-0000-000000000003', 'City', 'শহর', false, 3);
