# 20 — Product Requirements Document (PRD)

**Project:** Shikhi (শিখি — "I learn")
**Document type:** Product Requirements Document
**Author role:** Business Analyst / Product Manager
**Status:** DRAFT — part of Phase A; reviewed at Gate A
**Version:** 0.1
**Builds on:** `00-vision-and-discovery.md`, `10-brd.md` (decisions D1–D8)

> **How to read this (for a non-specialist):** The BRD said *what business outcomes* we
> need. This PRD says *what the product does* to deliver them — broken into **epics**
> (large feature areas), **user stories** ("As a … I want … so that …"), and **acceptance
> criteria** (testable conditions in *Given/When/Then* form). It also defines the
> **content model**, the **exercise types**, the **gamification rules**, and the **UX
> flows**. It still does **not** specify code, database tables, or screens pixel-by-pixel —
> that's the architecture (Phase B) and design. IDs like **FR-…**, **E…**, **US-…** let
> later docs trace back here.

---

## 1. Scope recap (decisions carried in)

| Dec | Meaning for the product |
|---|---|
| D1 | UI chrome is **user-selectable Bengali/English** (default Bengali); content & feedback bilingual |
| D2 | **Online-only but resilient** (lean payloads, caching, graceful on slow networks); no offline/PWA in v1 |
| D3/D6 | Platform supports **broad multi-level** curriculum, but **launch with a curated beginner pilot** and expand |
| D4 | **AI grading deferred**; v1 grading is **deterministic**, behind a **grading-strategy seam** for later AI |
| D5 | **Multi-method identity**: email+password, phone+OTP, social (Google), extensible (phaseable) |
| D7 | **No monetization** in the first generation |
| D8 | **No hard deadline/budget**; optimize for quality, apply sensible cost discipline |

---

## 2. Domain model (conceptual)

> This is the *vocabulary* of the product. The database design (Phase B LLD) will refine
> it, but these concepts and relationships are stable.

```
Curriculum
└── Level            (e.g., "Beginner A1", "Beginner A2", "Lower-Intermediate B1")
    └── Unit         (a themed group, e.g., "Greetings", "Daily routine")
        └── Lesson   (a single playable session, ~5–10 exercises)
            └── Exercise  (one question of a given type)

Learner
├── Identity(ies)    (email / phone / social — one or more per learner)
├── Profile          (display name, UI locale, learning prefs)
├── Progress         (per lesson/unit/level: status, score)
├── Stats            (XP, level/rank, streak, hearts state)
├── ReviewItems      (items previously missed, scheduled for review)
└── AchievementsEarned

Content authoring
├── ContentDraft / ContentVersion  (versioned curriculum)
└── ValidationResult               (checks before publish)
```

**Cross-cutting content attributes** (apply to lessons/exercises):
- **Bilingual text**: every learner-facing string has Bengali + English forms as needed.
- **L1-pattern tag(s)**: e.g., `articles`, `tense-aspect`, `prepositions`, `word-order`,
  `gendered-pronouns`, `agreement`, `question-formation` — used for targeted feedback,
  review grouping, and effectiveness measurement (SC-3).
- **Difficulty/level placement** and ordering.
- **Accepted answers + normalization rules** (for gradable exercises).
- **Curated hint(s)** keyed to common wrong answers / patterns (bilingual).

---

## 3. Epics overview

| Epic | Name | Delivers | Maps to BR |
|---|---|---|---|
| **E1** | Identity & accounts | Multi-method sign-up/login, profile, deletion | BR-1 (D5) |
| **E2** | Onboarding & placement | Locale choice, optional placement, first-run | BR-2, P1 |
| **E3** | Curriculum consumption | Browse levels/units/lessons; unlock map | BR-3 |
| **E4** | Lesson player & exercises | Playable lessons; exercise type catalog | BR-4 |
| **E5** | Grading & feedback | Deterministic grading + bilingual hints; AI seam | BR-5, BR-11 (D4) |
| **E6** | Progression & gamification | XP, streaks, hearts, unlocking, achievements | BR-6 |
| **E7** | Spaced-repetition review | Resurface missed items | BR-7 |
| **E8** | Progress sync & dashboard | Cross-device sync; stats/history view | BR-8 |
| **E9** | Content authoring & validation | Author/version/validate curriculum | BR-9 (D3/D6) |
| **E10** | Localization & accessibility | BN/EN UI, Bengali rendering, a11y | BR-2 (D1) |
| **E11** | Operational surfaces | Health, privacy controls (export/delete) | BR-10 |
| **E12** | Adaptive vocabulary practice | One-tap generated practice sessions from the Oxford-5000 layer, matched to the learner's CEFR level | BR-4, BR-6, BR-7 |

---

## 4. Detailed epics, stories & acceptance criteria

> Acceptance criteria use **Given / When / Then**. Only representative criteria are listed
> per story; the full set is finalized with QA in Phase C (test strategy) and refined per
> epic in Phase D.

### E1 — Identity & accounts *(BR-1, D5)*

- **US-1.1** As a new learner, I can **sign up with email + password** so that I have an
  account.
  - *Given* valid, unused email and a password meeting policy, *When* I submit, *Then* an
    account is created and a verification email is sent; I can use the app in a
    limited/unverified state per policy (policy TBD with Security).
  - *Given* an email already in use, *When* I submit, *Then* I get a clear, localized error
    (and no account enumeration leak — see Security phase).
- **US-1.1b** As a returning learner, I can opt into **"remember me"** so my email (and,
  when chosen, password) is pre-filled next time, and I can **reveal the password** while
  typing to check it. *(Added 2026-07-08 — implemented on web `AuthPanel` and the Android
  onboarding/login; convenience only, does not change auth semantics.)*
- **US-1.2** As a learner, I can **sign in with phone number + OTP** so that I don't need a
  password. *(Phaseable — see §9.)*
  - *Given* a valid phone number, *When* I request a code, *Then* an OTP is sent and, *When*
    I enter it correctly within its validity window, *Then* I'm signed in; rate-limited
    against abuse.
- **US-1.3** As a learner, I can **sign in with Google** so that onboarding is one tap.
- **US-1.4** As a learner, I can **reset my password / recover access** so that I'm never
  locked out.
- **US-1.5** As a learner, I can **edit my profile** (display name, UI language) so the app
  fits me.
- **US-1.6** As a learner, I can **delete my account and data** so I control my privacy.
  - *Given* I confirm deletion, *Then* my personal data is deleted/anonymized per the
    retention policy and I'm signed out. *(Ties to E11 / Security.)*
- **US-1.7** As a learner with multiple methods, I can have them **linked to one account**
  so my progress is unified. *(Extensible identity model, D5.)*
- **US-1.8** As a first-time visitor, I can **start learning as a guest — without signing up**
  — so I can try Shikhi with zero friction, and later **create an account that keeps all the
  progress** I made as a guest. *(Guest onboarding; ADR-0011.)*
  - *Given* I'm on the sign-in screen, *When* I tap "Try it without an account", *Then* I get
    a guest session and can use the full learning loop (practice, lessons, review, stats).
  - *Given* I've made progress as a guest, *When* I create an account (email + password) from
    the guest prompt, *Then* my account is upgraded **in place** and **all my XP, streak, and
    word mastery are preserved** (no data is lost or reset).
  - *Given* the email I choose already belongs to an account, *When* I submit, *Then* I get a
    clear, localized message to **log in instead**, and I understand this session's guest
    progress won't be saved. *(Merging into an existing account is out of scope for pilot.)*
  - *Given* a guest never converts, *Then* the anonymous account and its data are cleaned up
    after an inactivity window *(retention in Security `50`)*.

> **FR-1.x highlights:** extensible identity-provider abstraction; verification &
> rate-limiting; secure credential handling (detailed in Security phase). Auth methods may
> ship in phases (email → phone → social) per the Delivery Plan. Guest sessions are anonymous
> accounts claimed in place — see ADR-0011.

### E2 — Onboarding & placement *(BR-2, P1)*

- **US-2.1** As a first-time visitor, I can **choose Bengali or English UI** immediately so
  I can understand the app. *(D1)*
- **US-2.2** As a new learner, I can **take a short placement check** (or **skip** to start
  from the beginning) so I start at an appropriate level.
  - *Given* I take placement, *When* I finish, *Then* I'm placed at a sensible
    starting unit and shown why. *Given* I skip, *Then* I start at the first unit.
- **US-2.3** As a new learner, I see a **brief, Bengali-first explanation of how Shikhi
  works** (hearts, streak, XP) so I know what to do.

### E3 — Curriculum consumption *(BR-3)*

- **US-3.1** As a learner, I can **see a map of levels → units → lessons** with my progress
  and what's locked/unlocked so I know where I am and what's next.
- **US-3.2** As a learner, I can **start the next available lesson** in one tap from the map.
- **US-3.3** As a learner, **locked content is clearly indicated** with what unlocks it.

### E4 — Lesson player & exercises *(BR-4)*

- **US-4.1** As a learner, I can **play a lesson one exercise at a time** with a visible
  progress indicator so a session feels short and achievable.
- **US-4.2** As a learner, I get **immediate right/wrong feedback** after each answer.
- **US-4.3** As a learner, I see a **lesson results summary** (score, XP earned, items to
  review) at the end.
- **US-4.4** As a learner, the lesson **handles a slow/dropped connection gracefully**
  (D2): no lost progress on transient failure; clear retry. *(NFR-backed.)*

**Exercise type catalog (v1 — all deterministically gradable):**

| Type | What the learner does | Especially good for |
|---|---|---|
| **Multiple choice** | Pick the correct translation/answer from options | Vocabulary, comprehension |
| **Match pairs** | Match Bengali ↔ English words/phrases | Vocabulary building |
| **Word-bank / tap-to-build** | Assemble a sentence from given word tiles | **Word order (SOV→SVO)**, syntax |
| **Fill in the blank** | Choose/type the missing word | **Articles, prepositions, verb forms, agreement** |
| **Type the translation** | Type the English (or Bengali) for a prompt | Production; graded via accepted-answer sets + normalization |
| **Listening (comprehension)** | Hear audio, then choose/type | Listening *(needs audio assets — may be limited in pilot; see §9)* |

> Out of scope (NG2): speaking/pronunciation scoring.

### E5 — Grading & feedback *(BR-5, BR-11, D4)*

- **US-5.1** As a learner, when I answer, my response is **graded correctly by
  deterministic rules** so feedback is instant and free.
  - *Given* a "type the translation" exercise with accepted answers and normalization
    (trim, case, punctuation, common variants), *When* my answer matches after
    normalization, *Then* it's marked correct.
- **US-5.2** As a learner, when I'm wrong, I get a **specific, bilingual hint** — ideally
  naming the **L1 pattern** (e.g., "Articles — a/an/the") — so I learn the rule.
  - *Given* my wrong answer matches a known mistake pattern, *When* feedback shows, *Then*
    it includes the curated hint for that pattern in my UI language.
- **US-5.3 (designed-for, not in v1)** As the product, grading can later route free-form
  answers to an **AI grading strategy** for lenient judgment + richer Bengali explanations,
  **without changing the lesson player or stored progress**. *(BR-11, D4.)*

> **FR-5.x highlights:** a **GradingStrategy** abstraction with a `RuleBasedStrategy`
> (v1). Grading inputs/outputs (verdict + feedback) are strategy-agnostic so an
> `AiGradingStrategy` can be added behind the seam. Normalization and accepted-answer
> handling are content-driven (authored in E9). The AI phase will add caching, fallback to
> rule-based, timeouts, and cost controls (per NFRs).

### E6 — Progression & gamification *(BR-6)*

- **US-6.1** As a learner, I **earn XP** for completing exercises/lessons (bonus for no
  mistakes) so effort is rewarded.
- **US-6.2** As a learner, I have a **daily streak** that increases on each day I meet my
  daily goal so I build a habit.
- **US-6.3** As a learner, I have **hearts (lives)**; wrong answers cost a heart, and
  running out ends/limits the session so mistakes carry gentle stakes.
- **US-6.4** As a learner, **completing a lesson/unit unlocks the next** so there's a clear
  path forward.
- **US-6.5** As a learner, I **earn achievements** for milestones so progress feels
  rewarding.

**Proposed default rules (tunable; final values set with QA/Delivery):**
- **XP:** fixed XP per correct exercise; lesson-completion bonus; perfect-lesson bonus.
- **Daily goal:** learner-selectable (e.g., casual/regular/serious) measured in XP/day.
- **Streak:** +1 on any day the daily goal is met; resets if a day is missed. *(Streak
  freeze = later.)*
- **Hearts:** start each session with a fixed number (e.g., 5); −1 per wrong answer;
  at zero, the session ends and the learner is offered a **review** (E7) to continue.
  Hearts replenish at session start (and, later, via review/time). *(Tunable.)*
- **Unlocking:** linear by default; placement (E2) can set a later start point.

### E7 — Spaced-repetition review *(BR-7)*

- **US-7.1** As a learner, items I get wrong are **added to a review queue** so I can
  reinforce them.
- **US-7.2** As a learner, I can **do a review session** that resurfaces due items so I
  retain what I've learned.
- **US-7.3** As a learner, **mastered items appear less often**; **missed items appear
  sooner** so practice is efficient.

> **FR-7.x:** v1 uses a **simple, well-understood scheduling scheme** (e.g., Leitner-style
> boxes) for transparency and low complexity. A richer algorithm (e.g., SM-2-like) is a
> later option.

### E8 — Progress sync & learner dashboard *(BR-8)*

- **US-8.1** As a learner, my **progress, XP, streak, and review queue persist to my
  account** and are the same on any device/browser I log into. *(BO-2, SC-2.)*
  - *Given* I complete lessons on device A, *When* I log in on device B, *Then* I see the
    same progress.
- **US-8.2** As a learner, I can **view my stats/history** (XP over time, streak,
  units completed, accuracy by L1 pattern) so I can see my growth. *(Supports SC-3.)*

### E9 — Content authoring & validation *(BR-9, D3/D6)*

- **US-9.1** As a content author, I can **create/edit levels, units, lessons, and
  exercises** (bilingual text, accepted answers, hints, L1-pattern tags) without engineering
  help.
- **US-9.2** As a content author, content is **versioned**, so I can publish updates and
  roll back without breaking active learners.
- **US-9.3** As a content author, content is **validated before publish** (e.g., required
  bilingual fields present, at least one accepted answer for gradable types, valid
  pattern tags, well-formed structure) so bad content can't reach learners.
- **US-9.4** As a content author, I can **preview a lesson as a learner would see it**
  before publishing.

> **FR-9.x:** authoring may begin as a structured, validated content format (e.g.,
> reviewed files) with tooling, evolving toward a richer editor — sequencing in the
> Delivery Plan. Validation rules are part of the content pipeline.

### E10 — Localization & accessibility *(BR-2, D1)*

- **US-10.1** As a learner, **all UI chrome** is available in Bengali and English and I can
  switch at any time. *(D1)*
- **US-10.2** As a learner, **Bengali text renders correctly** (Unicode, conjuncts, fonts)
  on my device. *(CO-4; verified on real devices.)*
- **US-10.3** As a learner using assistive technology, the app meets **accessibility**
  standards (keyboard navigation, contrast, labels). *(NFR-backed; WCAG target in NFRs.)*

### E11 — Operational & privacy surfaces *(BR-10)*

- **US-11.1** As the operator, the system exposes **health/readiness** so it can be
  monitored. *(Detail in NFR/infra.)*
- **US-11.2** As a learner, I can **export my data** and **delete my account** (with E1.6)
  so my privacy rights are honored. *(Security/privacy phase.)*

### E12 — Adaptive vocabulary practice *(BR-4, BR-6, BR-7)*

> Turns the Oxford-5000 vocabulary layer (V11–V19, bands A1–C1) from a read-only dictionary
> into the primary learning experience: exercises are **generated from vocabulary rows**
> matched to the learner's CEFR level, flowing continuously in rounds — no authored lessons
> required.

- **US-12.1** As a learner, my account carries a **CEFR level** (A1–C1, default A1) that I
  pick at onboarding and can change anytime, so practice matches my ability.
  - *Given* a new account, *When* I first sign in, *Then* I can self-place at A1–C1 (or
    keep the A1 default) and my choice is saved to my profile.
  - *Note (2026-07-08):* self-placement and the practice engine originally capped at B2;
    the C1 band (vocabulary V17/V19) was admitted to the practice `BAND_ORDER`, the
    `PUT /stats/level` validation, and the DB check constraints (migration V21). The top
    band is C1 — C2 is still rejected.
- **US-12.2** As a learner, after signing in I see one clear **"Start session"** action, so
  starting to learn takes a single tap.
- **US-12.3** As a learner, exercises **keep coming one after another** in rounds of ~10;
  each round ends with a summary (accuracy, XP, streak) and a one-tap **"Keep going"**, so
  a session lasts exactly as long as I want.
- **US-12.4** As a learner, exercises are **generated from level-appropriate words**:
  ~70% from my current band, ~30% lighter review from earlier bands.
  - *Given* my level is B1, *When* a round is generated, *Then* most items use B1 words and
    the rest reinforce A1/A2 words — prioritizing words I've missed or never seen.
- **US-12.5** As a learner, item formats **vary between single words and short sentences**
  (word→meaning, meaning→word, sentence gap-fill, build-the-short-sentence, type-the-word),
  so practice stays fresh and sentences stay small and readable.
- **US-12.6** As a learner, my **per-word strength** is tracked (seen/correct counts), so
  missed words come back sooner and mastered words fade out. *(Complements E7 review.)*
- **US-12.7** As a learner, when I've shown mastery of most of my band, I get a **level-up
  suggestion** I can accept with one tap (never an automatic promotion), so difficulty
  keeps pace with me.
- **US-12.8** As the product, answer grading and hearts/XP/streak reuse the **existing
  grading rules and progress engine** (E5/E6): correctness never reaches the client, wrong
  answers cost a heart, correct answers earn XP, replays are idempotent.

> **FR-12.x highlights:** generation is deterministic and rule-based (templates over
> vocabulary rows + distractor selection); sentence-based formats are only chosen when the
> example sentence is short (≤ ~8 words). AI-generated fresh sentences are a later,
> additive strategy behind the same seam (D4). Out of scope for this slice: automatic
> promotion, listening/audio formats, per-user timezone.

---

## 5. Key UX flows (conceptual)

> Described as steps/wireflow, not visual design. Visual/UX design is later; these define
> behavior.

**Flow A — First run & onboarding (P1):**
`Land → choose UI language (BN/EN) → choose sign-up method (email/phone/Google) → verify →
brief how-it-works → optional placement or skip → land on curriculum map at start point.`

**Flow B — Daily learning session (P2):**
`Open → curriculum map → start next lesson → exercise → answer → instant feedback (+hint if
wrong, −heart if wrong) → repeat → results summary (XP, streak update, items added to
review) → back to map (next lesson unlocked if criteria met).`

**Flow B2 — Adaptive practice session (E12; the default daily flow once shipped):**
`Open → home hero shows level badge + streak + "Start session" → tap → round of ~10
generated exercises (word ↔ meaning, short-sentence gap/build, type-the-word) → answer →
instant feedback (−heart if wrong; missed word rescheduled sooner) → round summary
(accuracy, XP, streak; level-up suggestion when eligible) → "Keep going" for the next round
or "Finish" → session summary → back to home.`

**Flow C — Review session (P3):**
`Open → "Review due" prompt or menu → review session of due items → feedback → updated
review scheduling → results.`

**Flow D — Authoring (P4):**
`Author drafts content → validation runs → fix errors → preview as learner → publish new
version → learners receive updated content (in-progress learners handled gracefully).`

**Flow E — Account/data (P5):**
`Profile → manage identities/profile → export data / delete account → confirmation →
deletion per retention policy.`

---

## 6. Content model details (for authoring & grading)

Each **Exercise** carries (final field list in LLD):
- `type` (from the catalog), `level/unit/lesson` placement, ordering.
- `prompt` (bilingual as needed), `media` (optional audio for listening).
- For gradable types: `acceptedAnswers[]`, `normalizationRules`, `distractors[]` (for
  choice), `tokens[]` (for word-bank).
- `l1PatternTags[]`, `hints[]` (each hint: bilingual text, optionally keyed to a wrong
  answer or pattern).
- `metadata` (author, version, status).

**Normalization rules (v1 deterministic grading):** trim whitespace, collapse internal
spaces, case-fold, ignore trailing punctuation, accept a curated list of equivalent
variants/synonyms per item. Authors are responsible for supplying enough accepted answers;
validation (US-9.3) flags gradable items with too few.

---

## 7. Grading-strategy seam (so AI drops in later — D4)

- Grading takes `(exercise, learnerAnswer)` and returns a **verdict** `(correct: bool,
  feedback: bilingual, matchedPattern?: tag, confidence?)`.
- v1: `RuleBasedStrategy` only. Later: `AiGradingStrategy` selected per-exercise-type or
  per-config, with **rule-based fallback**, caching, timeouts, and cost controls (NFR).
- The lesson player, progress, and review logic depend on the **verdict shape**, not on
  *how* it was produced — this is what makes AI additive, not disruptive (SC-5).

---

## 8. Non-functional expectations (pointer)

This PRD assumes the quality bar defined in `30-nfr.md` (performance, resilience on poor
networks per D2, security/privacy, i18n correctness per D1, accessibility, observability,
scale-readiness). Stories above reference it where behavior depends on it (e.g., US-4.4,
US-10.2/3).

---

## 9. Pilot scope vs. later (D6 — curated pilot first)

> The platform is **built for** the full feature set; the **first launch** is deliberately
> curated to de-risk authoring (AB-2) and get a real product live. Exact sequencing is set
> in the Delivery Plan; this is the product intent.

**In the curated pilot launch:**
- E1 identity — **email+password first** (phone/social phased in shortly after).
- E2 onboarding + **skip-or-simple placement**.
- E3 curriculum map; E4 lesson player with the **core exercise types** (multiple choice,
  match, word-bank, fill-blank, type-translation; listening if audio is ready).
- E5 **deterministic grading** + curated bilingual hints (AI seam present, AI off).
- E6 XP, streak, hearts, unlocking (achievements minimal).
- E8 cross-device sync + a basic stats view.
- E9 authoring/validation sufficient to produce the pilot content.
- E10 BN/EN UI + correct Bengali rendering; baseline accessibility.
- E11 health + data export/delete.
- **Content:** a polished **beginner level** (a few units), authored and linguistically
  reviewed.

**Shortly after / later tracks:**
- Phone+OTP and social login (rest of D5).
- Broader curriculum levels (continuous authoring track).
- Spaced-repetition (E7) richer scheduling; achievements depth; richer stats; listening
  expansion; streak-freeze.
- **AI grading strategy** (E5 US-5.3) behind the seam.
- Future options (recorded, not committed): offline/PWA (D2), native mobile (OOS-1).

---

## 10. Traceability (BR → epics) & open items

| BR | Epics |
|---|---|
| BR-1 (D5) | E1 |
| BR-2 (D1) | E2, E10 |
| BR-3 (D3/D6) | E3, E9 |
| BR-4 | E4 |
| BR-5 (D4) | E5 |
| BR-6 | E6 |
| BR-7 | E7 |
| BR-8 | E8 |
| BR-9 | E9 |
| BR-10 | E11 |
| BR-11 (D4) | E5 §7 seam |
| BR-4/6/7 (adaptive) | E12 |

**Open product questions (mostly refine later, not blocking Gate A):**
- **OQ-P1:** Final **gamification values** (XP amounts, hearts count, daily-goal tiers) —
  tune with QA/Delivery using playtesting.
- **OQ-P2:** Placement test depth for pilot (simple skip vs. short adaptive) — decide in
  Delivery Plan.
- **OQ-P3:** Whether **listening** exercises are in the pilot (depends on audio-asset
  capacity) — Delivery Plan.
- **OQ-P4:** Authoring tooling form for the pilot (validated content files + tooling vs.
  early editor UI) — Architecture/Delivery.

> **Next step:** continue Phase A as **Architect** with `docs/30-nfr.md` (Non-Functional
> Requirements) — the measurable quality bar (performance, scalability, availability,
> security, privacy, i18n, accessibility, observability, cost) that this PRD assumes. After
> NFRs, we reach **Gate A** (review of 00–30 together).
