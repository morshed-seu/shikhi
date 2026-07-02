-- V5 — curated bilingual hints for the pilot (M3 grading feedback). Demonstrates the hint
-- precedence WRONG_ANSWER → PATTERN → DEFAULT (LLD §5) and the Bengali L1-transfer
-- specialisation: the TYPE_TRANSLATION item carries a PATTERN hint about copula omission
-- ("to be" is often dropped in Bangla but required in English). trigger_key for WRONG_ANSWER
-- is stored already-normalized (lower-case, collapsed spaces) so grading can match it directly.

insert into l1_patterns (id, code, name_en, name_bn)
values ('60000000-0000-0000-0000-000000000001', 'COPULA',
        'Missing “to be” verb', '“to be” ক্রিয়ার অনুপস্থিতি');

-- MCQ "Which word is a greeting?" — default hint only.
insert into hints (exercise_id, trigger, trigger_key, text_en, text_bn)
values ('50000000-0000-0000-0000-000000000001', 'DEFAULT', null,
        'A greeting is how you say hi to someone.',
        'শুভেচ্ছা হলো কাউকে সম্ভাষণ জানানোর উপায়।');

-- MCQ "Which word means name?" — default hint only.
insert into hints (exercise_id, trigger, trigger_key, text_en, text_bn)
values ('50000000-0000-0000-0000-000000000003', 'DEFAULT', null,
        'Think about which word labels a person.',
        'ভাবুন কোন শব্দটি একজন ব্যক্তিকে চিহ্নিত করে।');

-- TYPE_TRANSLATION "আমি ভালো আছি" → "I am fine": full precedence chain.
insert into hints (exercise_id, trigger, trigger_key, text_en, text_bn)
values ('50000000-0000-0000-0000-000000000002', 'WRONG_ANSWER', 'i fine',
        'Almost — English needs the verb “am”: “I am fine.”',
        'প্রায় ঠিক — ইংরেজিতে “am” ক্রিয়াটি দরকার: “I am fine.”'),
       ('50000000-0000-0000-0000-000000000002', 'PATTERN', 'COPULA',
        'Bengali often drops “to be”, but English keeps it — use am/is/are.',
        'বাংলায় প্রায়ই “to be” বাদ পড়ে, কিন্তু ইংরেজিতে তা রাখতে হয় — am/is/are ব্যবহার করুন।'),
       ('50000000-0000-0000-0000-000000000002', 'DEFAULT', null,
        'Translate word by word, then check the verb.',
        'শব্দে শব্দে অনুবাদ করুন, তারপর ক্রিয়াটি মিলিয়ে নিন।');

insert into exercise_patterns (exercise_id, pattern_id)
values ('50000000-0000-0000-0000-000000000002', '60000000-0000-0000-0000-000000000001');
