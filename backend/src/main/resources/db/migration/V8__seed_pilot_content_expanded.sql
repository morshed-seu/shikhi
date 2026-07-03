-- V8 — M7 pilot content expansion. Grows the A1 (Beginner) level of pilot-v1 from one unit
-- into a fuller, linguistically-reviewed beginner course that targets the two signature
-- Bengali L1-transfer errors:
--   • ARTICLES (a / an / the) — Bangla has no articles, so learners drop or misuse them.
--   • COPULA  (am / is / are) — Bangla drops the "to be" verb, so learners omit it in English.
-- It adds a WORD_BANK lesson to the Greetings unit and two new units (Articles, To be) with a
-- mix of MCQ, FILL_BLANK, TYPE_TRANSLATION and WORD_BANK exercises, plus curated bilingual
-- hints (DEFAULT / PATTERN / WRONG_ANSWER precedence, LLD §5).
--
-- This APPENDS to the existing PUBLISHED pilot-v1 tree (fixed UUIDs from V3), so all existing
-- lesson/session/review paths keep working while the course gets deeper. A future content
-- iteration would cut a fresh version rather than extend a published one; for pilot seed data
-- we grow in place. WORD_BANK render tokens are stored as exercise_options rows (shown to the
-- learner, shuffled); the accepted word order lives in exercise_answers and is graded
-- server-side — never serialized to clients.

-- ── New L1-transfer pattern: articles (COPULA already seeded in V5) ─────────────────────────
insert into l1_patterns (id, code, name_en, name_bn)
values ('60000000-0000-0000-0000-000000000002', 'ARTICLE',
        'Missing or wrong article (a/an/the)', 'আর্টিকেল বাদ বা ভুল (a/an/the)');

-- ═══════════════════════════════════════════════════════════════════════════════════════════
-- Unit 1 (Greetings) — add Lesson 3: "Saying goodbye" with a WORD_BANK exercise.
-- ═══════════════════════════════════════════════════════════════════════════════════════════
insert into lessons (id, unit_id, code, title_en, title_bn, ordinal)
values ('40000000-0000-0000-0000-000000000003',
        '30000000-0000-0000-0000-000000000001', 'A1-U1-L3', 'Saying goodbye', 'বিদায় বলা', 3);

insert into exercises (id, lesson_id, type, ordinal, prompt_en, prompt_bn)
values ('50000000-0000-0000-0000-000000000010',
        '40000000-0000-0000-0000-000000000003', 'MCQ', 1,
        'Which word means "goodbye"?', 'কোন শব্দের অর্থ "বিদায়"?'),
       ('50000000-0000-0000-0000-000000000011',
        '40000000-0000-0000-0000-000000000003', 'WORD_BANK', 2,
        'Arrange the English words to say: "See you tomorrow"',
        'ইংরেজি শব্দগুলো সাজিয়ে বলুন: "আগামীকাল দেখা হবে"');

insert into exercise_options (exercise_id, text_en, text_bn, is_correct, ordinal)
values ('50000000-0000-0000-0000-000000000010', 'Goodbye', 'বিদায়', true, 1),
       ('50000000-0000-0000-0000-000000000010', 'Welcome', 'স্বাগতম', false, 2),
       ('50000000-0000-0000-0000-000000000010', 'Sorry', 'দুঃখিত', false, 3);

-- WORD_BANK tokens (shuffled render order); accepted order is in exercise_answers below.
insert into exercise_options (exercise_id, text_en, text_bn, is_correct, ordinal)
values ('50000000-0000-0000-0000-000000000011', 'tomorrow', 'tomorrow', false, 1),
       ('50000000-0000-0000-0000-000000000011', 'See', 'See', false, 2),
       ('50000000-0000-0000-0000-000000000011', 'you', 'you', false, 3);

insert into exercise_answers (exercise_id, accepted_answer, is_primary)
values ('50000000-0000-0000-0000-000000000011', 'See you tomorrow', true);

insert into hints (exercise_id, trigger, trigger_key, text_en, text_bn)
values ('50000000-0000-0000-0000-000000000010', 'DEFAULT', null,
        'A goodbye is what you say when you leave.',
        'বিদায় হলো চলে যাওয়ার সময় যা বলা হয়।'),
       ('50000000-0000-0000-0000-000000000011', 'DEFAULT', null,
        'Start with the verb: "See you …".',
        'ক্রিয়া দিয়ে শুরু করুন: "See you …"।');

-- ═══════════════════════════════════════════════════════════════════════════════════════════
-- Unit 2 (NEW) — Articles: a / an / the. The flagship Bengali-learner differentiator.
-- ═══════════════════════════════════════════════════════════════════════════════════════════
insert into units (id, level_id, code, title_en, title_bn, ordinal)
values ('30000000-0000-0000-0000-000000000002',
        '20000000-0000-0000-0000-000000000001', 'A1-U2',
        'Articles (a / an / the)', 'আর্টিকেল (a / an / the)', 2);

insert into lessons (id, unit_id, code, title_en, title_bn, ordinal)
values ('40000000-0000-0000-0000-000000000021',
        '30000000-0000-0000-0000-000000000002', 'A1-U2-L1', 'A and An', 'A এবং An', 1),
       ('40000000-0000-0000-0000-000000000022',
        '30000000-0000-0000-0000-000000000002', 'A1-U2-L2', 'Using "the"', '"the" এর ব্যবহার', 2);

-- U2-L1 "A and An"
insert into exercises (id, lesson_id, type, ordinal, prompt_en, prompt_bn)
values ('50000000-0000-0000-0000-000000000020',
        '40000000-0000-0000-0000-000000000021', 'MCQ', 1,
        'Choose the correct article: ___ apple', 'সঠিক আর্টিকেলটি বেছে নিন: ___ apple'),
       ('50000000-0000-0000-0000-000000000021',
        '40000000-0000-0000-0000-000000000021', 'FILL_BLANK', 2,
        'Fill in the blank: I have ___ book.', 'শূন্যস্থান পূরণ করুন: I have ___ book.'),
       ('50000000-0000-0000-0000-000000000022',
        '40000000-0000-0000-0000-000000000021', 'WORD_BANK', 3,
        'Arrange the words: "She is a doctor"', 'শব্দগুলো সাজান: "She is a doctor"');

insert into exercise_options (exercise_id, text_en, text_bn, is_correct, ordinal)
values ('50000000-0000-0000-0000-000000000020', 'an', 'an', true, 1),
       ('50000000-0000-0000-0000-000000000020', 'a', 'a', false, 2),
       ('50000000-0000-0000-0000-000000000020', 'the', 'the', false, 3);

insert into exercise_answers (exercise_id, accepted_answer, is_primary)
values ('50000000-0000-0000-0000-000000000021', 'a', true);

insert into exercise_options (exercise_id, text_en, text_bn, is_correct, ordinal)
values ('50000000-0000-0000-0000-000000000022', 'a', 'a', false, 1),
       ('50000000-0000-0000-0000-000000000022', 'doctor', 'doctor', false, 2),
       ('50000000-0000-0000-0000-000000000022', 'She', 'She', false, 3),
       ('50000000-0000-0000-0000-000000000022', 'is', 'is', false, 4);

insert into exercise_answers (exercise_id, accepted_answer, is_primary)
values ('50000000-0000-0000-0000-000000000022', 'She is a doctor', true);

insert into exercise_patterns (exercise_id, pattern_id)
values ('50000000-0000-0000-0000-000000000020', '60000000-0000-0000-0000-000000000002'),
       ('50000000-0000-0000-0000-000000000021', '60000000-0000-0000-0000-000000000002'),
       ('50000000-0000-0000-0000-000000000022', '60000000-0000-0000-0000-000000000002');

insert into hints (exercise_id, trigger, trigger_key, text_en, text_bn)
values ('50000000-0000-0000-0000-000000000020', 'PATTERN', 'ARTICLE',
        'Bengali has no a/an/the — English needs an article before most singular nouns.',
        'বাংলায় a/an/the নেই — ইংরেজিতে বেশিরভাগ একবচন বিশেষ্যের আগে একটি আর্টিকেল লাগে।'),
       ('50000000-0000-0000-0000-000000000020', 'DEFAULT', null,
        'Use "an" before a vowel sound (a, e, i, o, u).',
        'স্বরধ্বনির (a, e, i, o, u) আগে "an" ব্যবহার করুন।'),
       ('50000000-0000-0000-0000-000000000021', 'WRONG_ANSWER', 'the',
        'Use "a" here — "the" is for something specific.',
        'এখানে "a" ব্যবহার করুন — নির্দিষ্ট কিছুর জন্য "the" হয়।'),
       ('50000000-0000-0000-0000-000000000021', 'PATTERN', 'ARTICLE',
        'A single, general thing takes "a" (consonant sound) or "an" (vowel sound).',
        'একটি সাধারণ জিনিসের আগে "a" (ব্যঞ্জনধ্বনি) বা "an" (স্বরধ্বনি) বসে।'),
       ('50000000-0000-0000-0000-000000000021', 'DEFAULT', null,
        'Use "a" before a consonant sound.',
        'ব্যঞ্জনধ্বনির আগে "a" ব্যবহার করুন।'),
       ('50000000-0000-0000-0000-000000000022', 'PATTERN', 'ARTICLE',
        'Job titles take an article: "a doctor", "an engineer".',
        'পেশার নামের আগে আর্টিকেল বসে: "a doctor", "an engineer"।'),
       ('50000000-0000-0000-0000-000000000022', 'DEFAULT', null,
        'Order: person + is + a + job. → "She is a doctor".',
        'ক্রম: ব্যক্তি + is + a + পেশা। → "She is a doctor"।');

-- U2-L2 "Using the"
insert into exercises (id, lesson_id, type, ordinal, prompt_en, prompt_bn)
values ('50000000-0000-0000-0000-000000000030',
        '40000000-0000-0000-0000-000000000022', 'MCQ', 1,
        'Choose the correct article: ___ sun is hot.',
        'সঠিক আর্টিকেলটি বেছে নিন: ___ sun is hot.'),
       ('50000000-0000-0000-0000-000000000031',
        '40000000-0000-0000-0000-000000000022', 'WORD_BANK', 2,
        'Arrange the words: "Open the door"', 'শব্দগুলো সাজান: "Open the door"');

insert into exercise_options (exercise_id, text_en, text_bn, is_correct, ordinal)
values ('50000000-0000-0000-0000-000000000030', 'The', 'The', true, 1),
       ('50000000-0000-0000-0000-000000000030', 'A', 'A', false, 2),
       ('50000000-0000-0000-0000-000000000030', 'An', 'An', false, 3);

insert into exercise_options (exercise_id, text_en, text_bn, is_correct, ordinal)
values ('50000000-0000-0000-0000-000000000031', 'the', 'the', false, 1),
       ('50000000-0000-0000-0000-000000000031', 'Open', 'Open', false, 2),
       ('50000000-0000-0000-0000-000000000031', 'door', 'door', false, 3);

insert into exercise_answers (exercise_id, accepted_answer, is_primary)
values ('50000000-0000-0000-0000-000000000031', 'Open the door', true);

insert into exercise_patterns (exercise_id, pattern_id)
values ('50000000-0000-0000-0000-000000000030', '60000000-0000-0000-0000-000000000002'),
       ('50000000-0000-0000-0000-000000000031', '60000000-0000-0000-0000-000000000002');

insert into hints (exercise_id, trigger, trigger_key, text_en, text_bn)
values ('50000000-0000-0000-0000-000000000030', 'PATTERN', 'ARTICLE',
        'Use "the" for a unique thing everyone knows — like the sun.',
        'সবাই চেনে এমন অনন্য জিনিসের জন্য "the" ব্যবহার করুন — যেমন the sun।'),
       ('50000000-0000-0000-0000-000000000030', 'DEFAULT', null,
        'There is only one sun, so it takes "the".',
        'সূর্য একটাই, তাই এর আগে "the" বসে।'),
       ('50000000-0000-0000-0000-000000000031', 'DEFAULT', null,
        'A command starts with the verb: "Open …".',
        'আদেশ ক্রিয়া দিয়ে শুরু হয়: "Open …"।');

-- ═══════════════════════════════════════════════════════════════════════════════════════════
-- Unit 3 (NEW) — To be: am / is / are. The copula Bengali speakers tend to drop.
-- ═══════════════════════════════════════════════════════════════════════════════════════════
insert into units (id, level_id, code, title_en, title_bn, ordinal)
values ('30000000-0000-0000-0000-000000000003',
        '20000000-0000-0000-0000-000000000001', 'A1-U3',
        'To be (am / is / are)', 'To be ক্রিয়া (am / is / are)', 3);

insert into lessons (id, unit_id, code, title_en, title_bn, ordinal)
values ('40000000-0000-0000-0000-000000000031',
        '30000000-0000-0000-0000-000000000003', 'A1-U3-L1', 'I am, you are', 'I am, you are', 1),
       ('40000000-0000-0000-0000-000000000032',
        '30000000-0000-0000-0000-000000000003', 'A1-U3-L2', 'He is, she is', 'He is, she is', 2);

-- U3-L1 "I am, you are"
insert into exercises (id, lesson_id, type, ordinal, prompt_en, prompt_bn)
values ('50000000-0000-0000-0000-000000000040',
        '40000000-0000-0000-0000-000000000031', 'MCQ', 1,
        'Choose the correct verb: I ___ a student.',
        'সঠিক ক্রিয়াটি বেছে নিন: I ___ a student.'),
       ('50000000-0000-0000-0000-000000000041',
        '40000000-0000-0000-0000-000000000031', 'TYPE_TRANSLATION', 2,
        'Translate to English: আমি একজন ছাত্র', 'ইংরেজিতে অনুবাদ করুন: আমি একজন ছাত্র'),
       ('50000000-0000-0000-0000-000000000042',
        '40000000-0000-0000-0000-000000000031', 'WORD_BANK', 3,
        'Arrange the words: "You are my friend"', 'শব্দগুলো সাজান: "You are my friend"');

insert into exercise_options (exercise_id, text_en, text_bn, is_correct, ordinal)
values ('50000000-0000-0000-0000-000000000040', 'am', 'am', true, 1),
       ('50000000-0000-0000-0000-000000000040', 'is', 'is', false, 2),
       ('50000000-0000-0000-0000-000000000040', 'are', 'are', false, 3);

insert into exercise_answers (exercise_id, accepted_answer, is_primary)
values ('50000000-0000-0000-0000-000000000041', 'I am a student', true),
       ('50000000-0000-0000-0000-000000000041', 'I''m a student', false);

insert into exercise_options (exercise_id, text_en, text_bn, is_correct, ordinal)
values ('50000000-0000-0000-0000-000000000042', 'are', 'are', false, 1),
       ('50000000-0000-0000-0000-000000000042', 'You', 'You', false, 2),
       ('50000000-0000-0000-0000-000000000042', 'friend', 'friend', false, 3),
       ('50000000-0000-0000-0000-000000000042', 'my', 'my', false, 4);

insert into exercise_answers (exercise_id, accepted_answer, is_primary)
values ('50000000-0000-0000-0000-000000000042', 'You are my friend', true);

insert into exercise_patterns (exercise_id, pattern_id)
values ('50000000-0000-0000-0000-000000000040', '60000000-0000-0000-0000-000000000001'),
       ('50000000-0000-0000-0000-000000000041', '60000000-0000-0000-0000-000000000001'),
       ('50000000-0000-0000-0000-000000000041', '60000000-0000-0000-0000-000000000002'),
       ('50000000-0000-0000-0000-000000000042', '60000000-0000-0000-0000-000000000001');

insert into hints (exercise_id, trigger, trigger_key, text_en, text_bn)
values ('50000000-0000-0000-0000-000000000040', 'PATTERN', 'COPULA',
        'English keeps the "to be" verb — with "I" it is always "am".',
        'ইংরেজিতে "to be" ক্রিয়া থাকে — "I"-এর সাথে সবসময় "am"।'),
       ('50000000-0000-0000-0000-000000000040', 'DEFAULT', null,
        'With "I", use "am".', '"I"-এর সাথে "am" ব্যবহার করুন।'),
       ('50000000-0000-0000-0000-000000000041', 'WRONG_ANSWER', 'i student',
        'Add the verb and the article: "I am a student".',
        'ক্রিয়া ও আর্টিকেল যোগ করুন: "I am a student"।'),
       ('50000000-0000-0000-0000-000000000041', 'WRONG_ANSWER', 'i a student',
        'Almost — add the verb "am": "I am a student".',
        'প্রায় ঠিক — "am" ক্রিয়াটি যোগ করুন: "I am a student"।'),
       ('50000000-0000-0000-0000-000000000041', 'PATTERN', 'COPULA',
        'Bengali drops "to be", but English needs it — use am/is/are.',
        'বাংলায় "to be" বাদ পড়ে, কিন্তু ইংরেজিতে তা লাগে — am/is/are ব্যবহার করুন।'),
       ('50000000-0000-0000-0000-000000000041', 'DEFAULT', null,
        'Remember the verb "am" and the article "a".',
        '"am" ক্রিয়া ও "a" আর্টিকেলটি মনে রাখুন।'),
       ('50000000-0000-0000-0000-000000000042', 'DEFAULT', null,
        'With "You", use "are": "You are …".',
        '"You"-এর সাথে "are" ব্যবহার করুন: "You are …"।');

-- U3-L2 "He is, she is"
insert into exercises (id, lesson_id, type, ordinal, prompt_en, prompt_bn)
values ('50000000-0000-0000-0000-000000000050',
        '40000000-0000-0000-0000-000000000032', 'MCQ', 1,
        'Choose the correct verb: She ___ happy.',
        'সঠিক ক্রিয়াটি বেছে নিন: She ___ happy.'),
       ('50000000-0000-0000-0000-000000000051',
        '40000000-0000-0000-0000-000000000032', 'FILL_BLANK', 2,
        'Fill in the blank: He ___ tall.', 'শূন্যস্থান পূরণ করুন: He ___ tall.');

insert into exercise_options (exercise_id, text_en, text_bn, is_correct, ordinal)
values ('50000000-0000-0000-0000-000000000050', 'is', 'is', true, 1),
       ('50000000-0000-0000-0000-000000000050', 'am', 'am', false, 2),
       ('50000000-0000-0000-0000-000000000050', 'are', 'are', false, 3);

insert into exercise_answers (exercise_id, accepted_answer, is_primary)
values ('50000000-0000-0000-0000-000000000051', 'is', true);

insert into exercise_patterns (exercise_id, pattern_id)
values ('50000000-0000-0000-0000-000000000050', '60000000-0000-0000-0000-000000000001'),
       ('50000000-0000-0000-0000-000000000051', '60000000-0000-0000-0000-000000000001');

insert into hints (exercise_id, trigger, trigger_key, text_en, text_bn)
values ('50000000-0000-0000-0000-000000000050', 'PATTERN', 'COPULA',
        'With he / she / it, the "to be" verb is "is".',
        'he / she / it-এর সাথে "to be" ক্রিয়াটি হলো "is"।'),
       ('50000000-0000-0000-0000-000000000050', 'DEFAULT', null,
        'With "She", use "is".', '"She"-এর সাথে "is" ব্যবহার করুন।'),
       ('50000000-0000-0000-0000-000000000051', 'WRONG_ANSWER', 'are',
        '"He" is singular — use "is", not "are".',
        '"He" একবচন — "are" নয়, "is" ব্যবহার করুন।'),
       ('50000000-0000-0000-0000-000000000051', 'PATTERN', 'COPULA',
        'Do not drop the verb — "He is tall", not "He tall".',
        'ক্রিয়া বাদ দেবেন না — "He tall" নয়, "He is tall"।'),
       ('50000000-0000-0000-0000-000000000051', 'DEFAULT', null,
        'With "He", use "is".', '"He"-এর সাথে "is" ব্যবহার করুন।');
