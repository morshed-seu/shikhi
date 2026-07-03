-- V9 — M7 pilot content, second expansion. Adds two more A1 units to pilot-v1, each targeting
-- a further Bengali L1-transfer error:
--   • PLURALS     — Bangla marks plural differently (often unmarked, or with আরা/গুলো), so
--                   learners forget the English "-s" (and trip on irregulars: child→children).
--   • PREPOSITIONS (in / on / at) — Bangla uses postpositions and one form often maps to several
--                   English prepositions, so in/on/at for place and time are frequently confused.
-- Mix of MCQ, FILL_BLANK and WORD_BANK exercises with curated bilingual hints (DEFAULT / PATTERN /
-- WRONG_ANSWER precedence, LLD §5). Appends to the PUBLISHED pilot-v1 tree (fixed UUIDs) at
-- ordinals 4 and 5, so all existing lesson/session/review paths keep working. WORD_BANK render
-- tokens are exercise_options rows (shown shuffled); the accepted word order lives in
-- exercise_answers and is graded server-side — never serialized to clients.

-- ── New L1-transfer patterns ────────────────────────────────────────────────────────────────
insert into l1_patterns (id, code, name_en, name_bn)
values ('60000000-0000-0000-0000-000000000003', 'PLURAL',
        'Missing or wrong plural (-s)', 'বহুবচন বাদ বা ভুল (-s)'),
       ('60000000-0000-0000-0000-000000000004', 'PREPOSITION',
        'Wrong preposition (in / on / at)', 'ভুল প্রিপজিশন (in / on / at)');

-- ═══════════════════════════════════════════════════════════════════════════════════════════
-- Unit 4 (NEW) — Plurals: one / many. English adds "-s"; Bangla marks plural differently.
-- ═══════════════════════════════════════════════════════════════════════════════════════════
insert into units (id, level_id, code, title_en, title_bn, ordinal)
values ('30000000-0000-0000-0000-000000000004',
        '20000000-0000-0000-0000-000000000001', 'A1-U4',
        'Plurals (one / many)', 'বহুবচন (এক / অনেক)', 4);

insert into lessons (id, unit_id, code, title_en, title_bn, ordinal)
values ('40000000-0000-0000-0000-000000000041',
        '30000000-0000-0000-0000-000000000004', 'A1-U4-L1', 'One and many', 'এক ও অনেক', 1),
       ('40000000-0000-0000-0000-000000000042',
        '30000000-0000-0000-0000-000000000004', 'A1-U4-L2', 'Special plurals', 'বিশেষ বহুবচন', 2);

-- U4-L1 "One and many" — regular -s plurals
insert into exercises (id, lesson_id, type, ordinal, prompt_en, prompt_bn)
values ('50000000-0000-0000-0000-000000000060',
        '40000000-0000-0000-0000-000000000041', 'MCQ', 1,
        'Choose the plural: one book, two ___.', 'বহুবচনটি বেছে নিন: one book, two ___.'),
       ('50000000-0000-0000-0000-000000000061',
        '40000000-0000-0000-0000-000000000041', 'FILL_BLANK', 2,
        'Fill in the plural: I see three ___ (cat).',
        'বহুবচন দিয়ে পূরণ করুন: I see three ___ (cat).'),
       ('50000000-0000-0000-0000-000000000062',
        '40000000-0000-0000-0000-000000000041', 'WORD_BANK', 3,
        'Arrange the words: "I have two books"', 'শব্দগুলো সাজান: "I have two books"');

insert into exercise_options (exercise_id, text_en, text_bn, is_correct, ordinal)
values ('50000000-0000-0000-0000-000000000060', 'books', 'books', true, 1),
       ('50000000-0000-0000-0000-000000000060', 'book', 'book', false, 2),
       ('50000000-0000-0000-0000-000000000060', 'bookes', 'bookes', false, 3);

insert into exercise_answers (exercise_id, accepted_answer, is_primary)
values ('50000000-0000-0000-0000-000000000061', 'cats', true);

-- WORD_BANK tokens (shuffled render order); accepted order is in exercise_answers.
insert into exercise_options (exercise_id, text_en, text_bn, is_correct, ordinal)
values ('50000000-0000-0000-0000-000000000062', 'books', 'books', false, 1),
       ('50000000-0000-0000-0000-000000000062', 'I', 'I', false, 2),
       ('50000000-0000-0000-0000-000000000062', 'two', 'two', false, 3),
       ('50000000-0000-0000-0000-000000000062', 'have', 'have', false, 4);

insert into exercise_answers (exercise_id, accepted_answer, is_primary)
values ('50000000-0000-0000-0000-000000000062', 'I have two books', true);

insert into exercise_patterns (exercise_id, pattern_id)
values ('50000000-0000-0000-0000-000000000060', '60000000-0000-0000-0000-000000000003'),
       ('50000000-0000-0000-0000-000000000061', '60000000-0000-0000-0000-000000000003'),
       ('50000000-0000-0000-0000-000000000062', '60000000-0000-0000-0000-000000000003');

insert into hints (exercise_id, trigger, trigger_key, text_en, text_bn)
values ('50000000-0000-0000-0000-000000000060', 'PATTERN', 'PLURAL',
        'For more than one, English adds "-s": book → books.',
        'একাধিক বোঝাতে ইংরেজিতে "-s" যোগ হয়: book → books।'),
       ('50000000-0000-0000-0000-000000000060', 'DEFAULT', null,
        'Two or more of something takes "-s".',
        'কোনো কিছু দুই বা তার বেশি হলে "-s" বসে।'),
       ('50000000-0000-0000-0000-000000000061', 'WRONG_ANSWER', 'cat',
        'More than one — add "-s": "cats".',
        'একাধিক — "-s" যোগ করুন: "cats"।'),
       ('50000000-0000-0000-0000-000000000061', 'PATTERN', 'PLURAL',
        'After a number bigger than one, the noun is plural: "three cats".',
        'একের বেশি সংখ্যার পরে বিশেষ্যটি বহুবচন হয়: "three cats"।'),
       ('50000000-0000-0000-0000-000000000061', 'DEFAULT', null,
        'Add "-s" to make the plural of "cat".',
        '"cat"-এর বহুবচন করতে "-s" যোগ করুন।'),
       ('50000000-0000-0000-0000-000000000062', 'PATTERN', 'PLURAL',
        'The number "two" needs the plural noun "books", not "book".',
        '"two" সংখ্যার সাথে বহুবচন "books" লাগে, "book" নয়।'),
       ('50000000-0000-0000-0000-000000000062', 'DEFAULT', null,
        'Order: I + have + two + books.', 'ক্রম: I + have + two + books।');

-- U4-L2 "Special plurals" — common irregulars
insert into exercises (id, lesson_id, type, ordinal, prompt_en, prompt_bn)
values ('50000000-0000-0000-0000-000000000063',
        '40000000-0000-0000-0000-000000000042', 'MCQ', 1,
        'Choose the plural: one child, two ___.', 'বহুবচনটি বেছে নিন: one child, two ___.'),
       ('50000000-0000-0000-0000-000000000064',
        '40000000-0000-0000-0000-000000000042', 'MCQ', 2,
        'Choose the plural: one man, two ___.', 'বহুবচনটি বেছে নিন: one man, two ___.');

insert into exercise_options (exercise_id, text_en, text_bn, is_correct, ordinal)
values ('50000000-0000-0000-0000-000000000063', 'children', 'children', true, 1),
       ('50000000-0000-0000-0000-000000000063', 'childs', 'childs', false, 2),
       ('50000000-0000-0000-0000-000000000063', 'childrens', 'childrens', false, 3),
       ('50000000-0000-0000-0000-000000000064', 'men', 'men', true, 1),
       ('50000000-0000-0000-0000-000000000064', 'mans', 'mans', false, 2),
       ('50000000-0000-0000-0000-000000000064', 'mens', 'mens', false, 3);

insert into exercise_patterns (exercise_id, pattern_id)
values ('50000000-0000-0000-0000-000000000063', '60000000-0000-0000-0000-000000000003'),
       ('50000000-0000-0000-0000-000000000064', '60000000-0000-0000-0000-000000000003');

insert into hints (exercise_id, trigger, trigger_key, text_en, text_bn)
values ('50000000-0000-0000-0000-000000000063', 'PATTERN', 'PLURAL',
        'Some plurals are irregular — no "-s": child → children.',
        'কিছু বহুবচন অনিয়মিত — "-s" ছাড়া: child → children।'),
       ('50000000-0000-0000-0000-000000000063', 'DEFAULT', null,
        'The plural of "child" is "children".',
        '"child"-এর বহুবচন "children"।'),
       ('50000000-0000-0000-0000-000000000064', 'PATTERN', 'PLURAL',
        'Irregular plural: the vowel changes — man → men.',
        'অনিয়মিত বহুবচন: স্বরধ্বনি বদলায় — man → men।'),
       ('50000000-0000-0000-0000-000000000064', 'DEFAULT', null,
        'The plural of "man" is "men".', '"man"-এর বহুবচন "men"।');

-- ═══════════════════════════════════════════════════════════════════════════════════════════
-- Unit 5 (NEW) — Prepositions: in / on / at. One Bangla form maps to several English ones.
-- ═══════════════════════════════════════════════════════════════════════════════════════════
insert into units (id, level_id, code, title_en, title_bn, ordinal)
values ('30000000-0000-0000-0000-000000000005',
        '20000000-0000-0000-0000-000000000001', 'A1-U5',
        'Prepositions (in / on / at)', 'প্রিপজিশন (in / on / at)', 5);

insert into lessons (id, unit_id, code, title_en, title_bn, ordinal)
values ('40000000-0000-0000-0000-000000000051',
        '30000000-0000-0000-0000-000000000005', 'A1-U5-L1', 'Places', 'স্থান', 1),
       ('40000000-0000-0000-0000-000000000052',
        '30000000-0000-0000-0000-000000000005', 'A1-U5-L2', 'Times', 'সময়', 2);

-- U5-L1 "Places"
insert into exercises (id, lesson_id, type, ordinal, prompt_en, prompt_bn)
values ('50000000-0000-0000-0000-000000000070',
        '40000000-0000-0000-0000-000000000051', 'MCQ', 1,
        'Choose the preposition: The book is ___ the table.',
        'প্রিপজিশনটি বেছে নিন: The book is ___ the table.'),
       ('50000000-0000-0000-0000-000000000071',
        '40000000-0000-0000-0000-000000000051', 'MCQ', 2,
        'Choose the preposition: I live ___ Dhaka.',
        'প্রিপজিশনটি বেছে নিন: I live ___ Dhaka.'),
       ('50000000-0000-0000-0000-000000000072',
        '40000000-0000-0000-0000-000000000051', 'WORD_BANK', 3,
        'Arrange the words: "The cat is in the box"',
        'শব্দগুলো সাজান: "The cat is in the box"');

insert into exercise_options (exercise_id, text_en, text_bn, is_correct, ordinal)
values ('50000000-0000-0000-0000-000000000070', 'on', 'on', true, 1),
       ('50000000-0000-0000-0000-000000000070', 'in', 'in', false, 2),
       ('50000000-0000-0000-0000-000000000070', 'at', 'at', false, 3),
       ('50000000-0000-0000-0000-000000000071', 'in', 'in', true, 1),
       ('50000000-0000-0000-0000-000000000071', 'on', 'on', false, 2),
       ('50000000-0000-0000-0000-000000000071', 'at', 'at', false, 3);

-- WORD_BANK tokens (shuffled render order); accepted order is in exercise_answers.
insert into exercise_options (exercise_id, text_en, text_bn, is_correct, ordinal)
values ('50000000-0000-0000-0000-000000000072', 'the', 'the', false, 1),
       ('50000000-0000-0000-0000-000000000072', 'cat', 'cat', false, 2),
       ('50000000-0000-0000-0000-000000000072', 'box', 'box', false, 3),
       ('50000000-0000-0000-0000-000000000072', 'The', 'The', false, 4),
       ('50000000-0000-0000-0000-000000000072', 'is', 'is', false, 5),
       ('50000000-0000-0000-0000-000000000072', 'in', 'in', false, 6);

insert into exercise_answers (exercise_id, accepted_answer, is_primary)
values ('50000000-0000-0000-0000-000000000072', 'The cat is in the box', true);

insert into exercise_patterns (exercise_id, pattern_id)
values ('50000000-0000-0000-0000-000000000070', '60000000-0000-0000-0000-000000000004'),
       ('50000000-0000-0000-0000-000000000071', '60000000-0000-0000-0000-000000000004'),
       ('50000000-0000-0000-0000-000000000072', '60000000-0000-0000-0000-000000000004');

insert into hints (exercise_id, trigger, trigger_key, text_en, text_bn)
values ('50000000-0000-0000-0000-000000000070', 'PATTERN', 'PREPOSITION',
        'Use "on" for a surface — something rests on top of the table.',
        'কোনো তলের জন্য "on" ব্যবহার করুন — কিছু টেবিলের উপরে থাকে।'),
       ('50000000-0000-0000-0000-000000000070', 'DEFAULT', null,
        'On top of a surface → "on".', 'কোনো তলের উপরে → "on"।'),
       ('50000000-0000-0000-0000-000000000071', 'PATTERN', 'PREPOSITION',
        'Use "in" for a city or country: "in Dhaka", "in Bangladesh".',
        'শহর বা দেশের জন্য "in": "in Dhaka", "in Bangladesh"।'),
       ('50000000-0000-0000-0000-000000000071', 'DEFAULT', null,
        'A city takes "in".', 'শহরের জন্য "in" বসে।'),
       ('50000000-0000-0000-0000-000000000072', 'PATTERN', 'PREPOSITION',
        'Inside a container → "in": the cat is in the box.',
        'কোনো পাত্রের ভেতরে → "in": the cat is in the box।'),
       ('50000000-0000-0000-0000-000000000072', 'DEFAULT', null,
        'Order: The + cat + is + in + the + box.',
        'ক্রম: The + cat + is + in + the + box।');

-- U5-L2 "Times"
insert into exercises (id, lesson_id, type, ordinal, prompt_en, prompt_bn)
values ('50000000-0000-0000-0000-000000000073',
        '40000000-0000-0000-0000-000000000052', 'MCQ', 1,
        'Choose the preposition: I wake up ___ 7 o''clock.',
        'প্রিপজিশনটি বেছে নিন: I wake up ___ 7 o''clock.'),
       ('50000000-0000-0000-0000-000000000074',
        '40000000-0000-0000-0000-000000000052', 'FILL_BLANK', 2,
        'Fill in the preposition: My birthday is ___ July.',
        'প্রিপজিশন দিয়ে পূরণ করুন: My birthday is ___ July.');

insert into exercise_options (exercise_id, text_en, text_bn, is_correct, ordinal)
values ('50000000-0000-0000-0000-000000000073', 'at', 'at', true, 1),
       ('50000000-0000-0000-0000-000000000073', 'in', 'in', false, 2),
       ('50000000-0000-0000-0000-000000000073', 'on', 'on', false, 3);

insert into exercise_answers (exercise_id, accepted_answer, is_primary)
values ('50000000-0000-0000-0000-000000000074', 'in', true);

insert into exercise_patterns (exercise_id, pattern_id)
values ('50000000-0000-0000-0000-000000000073', '60000000-0000-0000-0000-000000000004'),
       ('50000000-0000-0000-0000-000000000074', '60000000-0000-0000-0000-000000000004');

insert into hints (exercise_id, trigger, trigger_key, text_en, text_bn)
values ('50000000-0000-0000-0000-000000000073', 'PATTERN', 'PREPOSITION',
        'Use "at" for a clock time: "at 7 o''clock", "at noon".',
        'ঘড়ির সময়ের জন্য "at": "at 7 o''clock", "at noon"।'),
       ('50000000-0000-0000-0000-000000000073', 'DEFAULT', null,
        'A clock time takes "at".', 'ঘড়ির সময়ের জন্য "at" বসে।'),
       ('50000000-0000-0000-0000-000000000074', 'WRONG_ANSWER', 'on',
        'For a month, use "in", not "on": "in July".',
        'মাসের জন্য "on" নয়, "in": "in July"।'),
       ('50000000-0000-0000-0000-000000000074', 'PATTERN', 'PREPOSITION',
        'Use "in" for months and years: "in July", "in 2026".',
        'মাস ও বছরের জন্য "in": "in July", "in 2026"।'),
       ('50000000-0000-0000-0000-000000000074', 'DEFAULT', null,
        'A month takes "in".', 'মাসের জন্য "in" বসে।');
