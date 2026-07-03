-- V10 — M7 pilot content, third expansion. Adds an A1 "Common verbs" unit to pilot-v1,
-- teaching everyday verb vocabulary and then the signature Bengali L1-transfer error in the
-- present simple:
--   • THIRD-PERSON -s — Bangla conjugates for person differently and does not add an "-s" for
--     he / she / it, so learners say "she eat" / "he go" instead of "she eats" / "he goes".
-- Mix of MCQ, TYPE_TRANSLATION, FILL_BLANK and WORD_BANK exercises with curated bilingual hints
-- (DEFAULT / PATTERN / WRONG_ANSWER precedence, LLD §5). Appends to the PUBLISHED pilot-v1 tree
-- (fixed UUIDs) at ordinal 6, so all existing lesson/session/review paths keep working.
-- WORD_BANK render tokens are exercise_options rows (shown shuffled); the accepted word order
-- lives in exercise_answers and is graded server-side — never serialized to clients.

-- ── New L1-transfer pattern ─────────────────────────────────────────────────────────────────
insert into l1_patterns (id, code, name_en, name_bn)
values ('60000000-0000-0000-0000-000000000005', 'VERB_S',
        'Missing third-person -s (he/she/it)', 'তৃতীয় পুরুষে -s বাদ (he/she/it)');

-- ═══════════════════════════════════════════════════════════════════════════════════════════
-- Unit 6 (NEW) — Common verbs. Everyday verbs, then the he/she/it "-s" Bangla speakers drop.
-- ═══════════════════════════════════════════════════════════════════════════════════════════
insert into units (id, level_id, code, title_en, title_bn, ordinal)
values ('30000000-0000-0000-0000-000000000006',
        '20000000-0000-0000-0000-000000000001', 'A1-U6',
        'Common verbs', 'সাধারণ ক্রিয়া', 6);

insert into lessons (id, unit_id, code, title_en, title_bn, ordinal)
values ('40000000-0000-0000-0000-000000000061',
        '30000000-0000-0000-0000-000000000006', 'A1-U6-L1', 'Everyday verbs', 'প্রতিদিনের ক্রিয়া', 1),
       ('40000000-0000-0000-0000-000000000062',
        '30000000-0000-0000-0000-000000000006', 'A1-U6-L2', 'He eats, she goes', 'He eats, she goes', 2);

-- U6-L1 "Everyday verbs" — vocabulary + base form
insert into exercises (id, lesson_id, type, ordinal, prompt_en, prompt_bn)
values ('50000000-0000-0000-0000-000000000080',
        '40000000-0000-0000-0000-000000000061', 'MCQ', 1,
        'Which word means "to eat"?', 'কোন শব্দের অর্থ "খাওয়া"?'),
       ('50000000-0000-0000-0000-000000000081',
        '40000000-0000-0000-0000-000000000061', 'MCQ', 2,
        'Which word means "to go"?', 'কোন শব্দের অর্থ "যাওয়া"?'),
       ('50000000-0000-0000-0000-000000000082',
        '40000000-0000-0000-0000-000000000061', 'TYPE_TRANSLATION', 3,
        'Translate to English: আমি পানি পান করি', 'ইংরেজিতে অনুবাদ করুন: আমি পানি পান করি'),
       ('50000000-0000-0000-0000-000000000083',
        '40000000-0000-0000-0000-000000000061', 'WORD_BANK', 4,
        'Arrange the words: "I read a book"', 'শব্দগুলো সাজান: "I read a book"');

insert into exercise_options (exercise_id, text_en, text_bn, is_correct, ordinal)
values ('50000000-0000-0000-0000-000000000080', 'eat', 'খাওয়া', true, 1),
       ('50000000-0000-0000-0000-000000000080', 'sleep', 'ঘুমানো', false, 2),
       ('50000000-0000-0000-0000-000000000080', 'run', 'দৌড়ানো', false, 3),
       ('50000000-0000-0000-0000-000000000081', 'go', 'যাওয়া', true, 1),
       ('50000000-0000-0000-0000-000000000081', 'come', 'আসা', false, 2),
       ('50000000-0000-0000-0000-000000000081', 'sit', 'বসা', false, 3);

insert into exercise_answers (exercise_id, accepted_answer, is_primary)
values ('50000000-0000-0000-0000-000000000082', 'I drink water', true),
       ('50000000-0000-0000-0000-000000000082', 'I drink the water', false);

-- WORD_BANK tokens (shuffled render order); accepted order is in exercise_answers.
insert into exercise_options (exercise_id, text_en, text_bn, is_correct, ordinal)
values ('50000000-0000-0000-0000-000000000083', 'a', 'a', false, 1),
       ('50000000-0000-0000-0000-000000000083', 'read', 'read', false, 2),
       ('50000000-0000-0000-0000-000000000083', 'I', 'I', false, 3),
       ('50000000-0000-0000-0000-000000000083', 'book', 'book', false, 4);

insert into exercise_answers (exercise_id, accepted_answer, is_primary)
values ('50000000-0000-0000-0000-000000000083', 'I read a book', true);

insert into hints (exercise_id, trigger, trigger_key, text_en, text_bn)
values ('50000000-0000-0000-0000-000000000080', 'DEFAULT', null,
        '"Eat" is what you do with food.',
        'খাবার নিয়ে যা করা হয় তা হলো "eat"।'),
       ('50000000-0000-0000-0000-000000000081', 'DEFAULT', null,
        '"Go" means to move to another place.',
        '"Go" মানে অন্য জায়গায় যাওয়া।'),
       ('50000000-0000-0000-0000-000000000082', 'WRONG_ANSWER', 'i drink',
        'Say what you drink: "I drink water".',
        'আপনি কী পান করেন তা বলুন: "I drink water"।'),
       ('50000000-0000-0000-0000-000000000082', 'DEFAULT', null,
        'Order: I + drink + water.', 'ক্রম: I + drink + water।'),
       ('50000000-0000-0000-0000-000000000083', 'DEFAULT', null,
        'Order: subject + verb + a + noun. → "I read a book".',
        'ক্রম: কর্তা + ক্রিয়া + a + বিশেষ্য। → "I read a book"।');

-- U6-L2 "He eats, she goes" — third-person singular -s
insert into exercises (id, lesson_id, type, ordinal, prompt_en, prompt_bn)
values ('50000000-0000-0000-0000-000000000084',
        '40000000-0000-0000-0000-000000000062', 'MCQ', 1,
        'Choose the correct verb: She ___ rice.',
        'সঠিক ক্রিয়াটি বেছে নিন: She ___ rice.'),
       ('50000000-0000-0000-0000-000000000085',
        '40000000-0000-0000-0000-000000000062', 'FILL_BLANK', 2,
        'Fill in the verb: He ___ to school. (go)',
        'ক্রিয়া দিয়ে পূরণ করুন: He ___ to school. (go)'),
       ('50000000-0000-0000-0000-000000000086',
        '40000000-0000-0000-0000-000000000062', 'WORD_BANK', 3,
        'Arrange the words: "She drinks tea"', 'শব্দগুলো সাজান: "She drinks tea"');

insert into exercise_options (exercise_id, text_en, text_bn, is_correct, ordinal)
values ('50000000-0000-0000-0000-000000000084', 'eats', 'eats', true, 1),
       ('50000000-0000-0000-0000-000000000084', 'eat', 'eat', false, 2),
       ('50000000-0000-0000-0000-000000000084', 'eating', 'eating', false, 3);

insert into exercise_answers (exercise_id, accepted_answer, is_primary)
values ('50000000-0000-0000-0000-000000000085', 'goes', true);

-- WORD_BANK tokens (shuffled render order); accepted order is in exercise_answers.
insert into exercise_options (exercise_id, text_en, text_bn, is_correct, ordinal)
values ('50000000-0000-0000-0000-000000000086', 'tea', 'tea', false, 1),
       ('50000000-0000-0000-0000-000000000086', 'She', 'She', false, 2),
       ('50000000-0000-0000-0000-000000000086', 'drinks', 'drinks', false, 3);

insert into exercise_answers (exercise_id, accepted_answer, is_primary)
values ('50000000-0000-0000-0000-000000000086', 'She drinks tea', true);

insert into exercise_patterns (exercise_id, pattern_id)
values ('50000000-0000-0000-0000-000000000084', '60000000-0000-0000-0000-000000000005'),
       ('50000000-0000-0000-0000-000000000085', '60000000-0000-0000-0000-000000000005'),
       ('50000000-0000-0000-0000-000000000086', '60000000-0000-0000-0000-000000000005');

-- MCQ gets PATTERN + DEFAULT only: the WRONG_ANSWER key for MCQ is the option's (random)
-- UUID, so a text-keyed WRONG_ANSWER hint could never match (V8 convention).
insert into hints (exercise_id, trigger, trigger_key, text_en, text_bn)
values ('50000000-0000-0000-0000-000000000084', 'PATTERN', 'VERB_S',
        'In the present simple, he / she / it takes a verb ending in "-s".',
        'Present simple-এ he / she / it-এর ক্রিয়ার শেষে "-s" বসে।'),
       ('50000000-0000-0000-0000-000000000084', 'DEFAULT', null,
        'With "She", the verb is "eats".', '"She"-এর সাথে ক্রিয়াটি "eats"।'),
       ('50000000-0000-0000-0000-000000000085', 'WRONG_ANSWER', 'go',
        '"He" needs the "-s" form — and "go" becomes "goes".',
        '"He"-এর জন্য "-s" রূপ লাগে — আর "go" হয় "goes"।'),
       ('50000000-0000-0000-0000-000000000085', 'PATTERN', 'VERB_S',
        'For he / she / it, "go" becomes "goes" (add -es after o).',
        'he / she / it-এর জন্য "go" হয় "goes" (o-এর পরে -es যোগ হয়)।'),
       ('50000000-0000-0000-0000-000000000085', 'DEFAULT', null,
        'The he/she/it form of "go" is "goes".',
        '"go"-এর he/she/it রূপ হলো "goes"।'),
       ('50000000-0000-0000-0000-000000000086', 'PATTERN', 'VERB_S',
        '"She" takes the "-s" verb: "drinks", not "drink".',
        '"She"-এর সাথে "-s" যুক্ত ক্রিয়া বসে: "drink" নয়, "drinks"।'),
       ('50000000-0000-0000-0000-000000000086', 'DEFAULT', null,
        'Order: She + drinks + tea.', 'ক্রম: She + drinks + tea।');
