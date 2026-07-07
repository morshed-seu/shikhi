# 00 — Vision & Discovery

**Project:** Shikhi (শিখি — "I learn")
**Document type:** Vision & Discovery
**Author role:** Business Analyst / Product Manager
**Status:** DRAFT — awaiting review at Gate A
**Version:** 0.1

> **How to read this document (for a non-specialist):** This is the very first SDLC
> artifact. It does *not* describe screens, code, or databases. Its job is to pin down
> **why** we are building Shikhi, **who** it is for, **what "success" means**, and
> **what could go wrong** — so every later decision (requirements, architecture, tests)
> can be traced back to a clear purpose. Where a term might be unfamiliar, it is defined
> in §13 Glossary.

---

## 1. Problem statement

Hundreds of millions of people speak Bengali (বাংলা) as a first language, and a large
share of them want or need functional English for education, employment, and digital
life. Yet most popular language-learning apps:

1. **Treat English as the destination but English (or a major European language) as the
   starting point too** — instruction, hints, and error explanations are in English,
   which is exactly what a beginner cannot yet read.
2. **Are not tailored to a Bengali speaker's specific difficulties.** A learner whose
   first language is Bengali makes *predictable* mistakes in English (see §5) that a
   generic course neither anticipates nor explains well.
3. **Give shallow feedback on open-ended answers** — typically "correct / incorrect" with
   no explanation a beginner can act on, especially for translation.

**The gap:** there is no widely available, well-designed, mobile-first-quality web app
that teaches English to Bengali speakers *in Bengali*, targets their specific error
patterns, and gives genuinely helpful, language-appropriate feedback.

---

## 2. Vision statement

> **Shikhi helps Bengali speakers learn practical English through short, game-like daily
> lessons that explain things in their own language and coach them through the exact
> mistakes Bengali speakers tend to make.**

In one line: *Duolingo-quality habit and motivation, but built from the ground up for the
Bengali → English learner, with AI-assisted, bilingual feedback.*

---

## 3. Goals and non-goals

### 3.1 Product goals
- **G1 — Comprehension-first:** a true beginner can use the app on day one because
  instructions and feedback are available in Bengali.
- **G2 — Targeted learning:** lessons and feedback explicitly address common Bengali-L1
  error patterns (§5), not just generic vocabulary.
- **G3 — Habit & motivation:** a daily-streak, XP, and progression loop that keeps
  learners coming back (the proven engagement model).
- **G4 — Helpful feedback on open answers:** for translation exercises, give specific,
  actionable feedback. **v1** uses curated accepted-answer sets + normalization + targeted
  hints (no AI). **Later** an AI grading strategy adds lenient judgment and richer Bengali
  explanations. The design must keep a clean seam so AI drops in without rework (see §11.1).
- **G5 — Trustworthy & durable:** a real product — secure accounts, reliable
  cross-device progress, and content quality we control.

### 3.2 Non-goals (initial — recorded so scope stays honest)
- **NG1:** ~~Native iOS/Android apps~~ **Amended 2026-07-07 (ADR-0012):** a **native
  Android app is now in scope** as a second client of the same `/v1` API (see PRD `21`).
  Native **iOS remains a non-goal**. The original "leave room for NG1" clause is exactly
  what this amendment exercises — the web app stays the primary, unchanged client.
- **NG2:** Speech recognition / pronunciation scoring.
- **NG3:** Social features (friends, leagues, leaderboards, chat).
- **NG4:** Live human tutoring or community Q&A.
- **NG5:** Teaching languages *other* than English, or to speakers of languages *other*
  than Bengali. (The architecture should not make this impossible later, but it is not in
  scope now.)

These non-goals are revisited in the PRD and Delivery Plan; the architecture (Phase B) is
expected to leave room for NG1–NG3 without building them now.

---

## 4. Target users & personas

> A **persona** is a short, concrete sketch of a representative user. We design for these
> people, not for an abstract "user."

**Persona A — "Rahim," the aspiring earner (primary).**
Early 20s, finished school in Bengali medium, wants better job prospects. Owns a
mid-range Android phone on a sometimes-slow connection. Reads Bengali fluently, English
haltingly. Motivated but easily discouraged by apps that assume English knowledge.
*Needs:* Bengali instructions, very gentle on-ramp, visible progress, works on a phone
browser with patchy data.

**Persona B — "Anika," the student (primary).**
Mid-teens, in school, needs English for exams and higher study. Comfortable with apps and
gamification. Reads some English already. *Needs:* structured practice that targets the
grammar she keeps getting wrong; quick daily sessions; a sense of achievement.

**Persona C — "Mr. Das," the returner/professional (secondary).**
30s–40s, uses some English at work, wants to improve fluency and confidence. Time-poor.
*Needs:* efficient review, meaningful feedback on real sentences, flexible pacing.

**Persona D — "Shreya," the content author (internal user).**
Creates and curates lessons. Not an engineer. *Needs:* a safe, simple way to author and
validate lesson content without breaking the app.

**Anti-persona (explicitly not designing for now):** advanced/near-native English users
seeking test-prep cram tools (e.g., IELTS coaching), and non-Bengali speakers.

---

## 5. Domain insight — why "Bengali → English" is special (our edge)

These are well-documented **first-language (L1) transfer** patterns: mistakes that happen
because Bengali grammar differs from English. Targeting them is our core differentiator,
and they will directly shape the curriculum and the AI feedback prompts.

| Area | Why it's hard for Bengali speakers | Example error |
|---|---|---|
| **Articles (a/an/the)** | Bengali has no direct equivalent of "a/an/the" | "I am student" / "I saw the sun yesterday" |
| **Verb tense & aspect** | Tense/aspect map differently | "I am knowing the answer" |
| **Prepositions** | Different prepositional logic | "I am good in English" (vs. "good at") |
| **Word order** | Bengali is Subject-Object-Verb; English is Subject-Verb-Object | "I rice eat" |
| **Gendered pronouns (he/she)** | Bengali pronouns are not gendered | "My mother, he is a teacher" |
| **Pluralization & subject–verb agreement** | Different number/agreement rules | "He go to school" / "two book" |
| **Question formation / auxiliaries (do/does)** | No direct equivalent | "You like tea?" / "Why you came?" |

**Implication for the product:** the curriculum is organized partly around *fixing these
patterns*, and AI feedback should name the pattern in Bengali (e.g., "Articles —
'a/an/the'") so the learner builds a mental model, not just a one-off correction.

---

## 6. Value proposition & differentiation

**For** Bengali speakers learning English **who** are underserved by English-first apps,
**Shikhi is** a bilingual, game-like learning platform **that** teaches and coaches in
Bengali and targets the mistakes Bengali speakers actually make. **Unlike** generic
language apps, **Shikhi** explains errors in Bengali and is built specifically for the
Bengali → English journey.

| | Generic global apps | Local rote / PDF courses | **Shikhi** |
|---|---|---|---|
| Instruction language for beginners | English-first | Mixed | **Bengali-first** |
| Bengali-specific error coaching | ✗ | rare | **✓ core feature** |
| Motivation loop (streaks/XP) | ✓ | ✗ | **✓** |
| Helpful feedback on translations | shallow | none | **✓ AI + bilingual** |
| Content quality control | n/a | varies | **✓ curated + versioned** |

---

## 7. Success metrics (how we'll know it's working)

> Targets are **directional** for now and will be finalized in the PRD/Delivery Plan once
> we agree what's realistic. They are written so each is measurable.

### 7.1 North-star metric
- **Weekly Active Learners completing ≥1 lesson** — the single number that best captures
  "people are actually learning."

### 7.2 Supporting KPIs
- **Activation:** % of new sign-ups who complete their first lesson (target to be set).
- **Retention:** Day-1 / Day-7 / Day-30 return rate; **streak survival** (% maintaining a
  ≥3-day streak).
- **Learning progress:** lessons completed per active learner per week; units unlocked.
- **Feedback quality (AI):** human-rated usefulness/correctness of AI feedback on a
  sampled set (see Phase C LLM-evaluation).
- **Reliability:** uptime against SLO; API latency within budget (defined in NFRs).
- **Unit economics:** average Claude API cost per active learner per month (must stay
  within a budget to be defined).

### 7.3 Guardrail metrics (don't win one metric by harming another)
- AI feedback error rate (wrong corrections) must stay below a threshold.
- p95 latency and error rate must stay within NFR budgets even as usage grows.
- Monthly LLM spend must stay within budget.

---

## 8. Guiding principles (decision tie-breakers)

1. **Bengali-first for the beginner.** When in doubt, make it usable without English.
2. **AI is an assistant, never a single point of failure.** Deterministic grading by
   default; AI for judgment; always a fallback.
3. **Content quality is owned, not outsourced to the model.** Core curriculum is
   hand-authored and versioned; AI assists grading/feedback, not the syllabus.
4. **Measure, then scale.** Build for growth, instrument everything, but don't gold-plate
   before there's signal.
5. **Privacy by default.** Collect the minimum; send the minimum to third parties.
6. **Explainability for the learner *and* the builder** — feedback explains the "why";
   architecture decisions are recorded (ADRs).

---

## 9. Assumptions (to validate at Gate A)

- **A1:** Target learners read Bengali comfortably; the UI chrome may start in English but
  *learning content and feedback* must support Bengali.
- **A2:** Primary access is a **mobile web browser** on mid-range Android over variable
  connectivity (informs performance/offline thinking later).
- **A3:** A solo/small builder team; you are new to the stack and want plain-language
  explanations alongside artifacts.
- **A4:** "Scale-ready" = designed and instrumented to grow, not pre-provisioned for mass
  traffic on day one.
- **A5:** AI grading (Anthropic Claude API) is **deferred to a later release**, but is a
  planned future capability — the architecture must reserve a clean integration seam for
  it now (grading-strategy abstraction), without building it in v1. (See §11.1 D4.)
- **A6:** English content targets a widely-useful general/"standard" register first
  (not exam-specific syllabi).

---

## 10. Constraints

- **C1 — Technical:** React + Vite + TypeScript frontend; Java/Spring Boot backend;
  PostgreSQL; Anthropic Claude API (Java SDK, `claude-opus-4-8`). (Locked by prior
  decisions.)
- **C2 — Process:** document-first full SDLC; role-phases with approval gates.
- **C3 — Skill:** beginner-friendly explanations required throughout; avoid unexplained
  jargon and over-engineering.
- **C4 — Cost:** LLM usage must be budgeted and controlled (caching, fallback, batching).
- **C5 — Localization:** correct Bengali Unicode rendering and font support are mandatory,
  not optional polish.

---

## 11. Dependencies & open questions

**External dependencies:**
- Anthropic Claude API (availability, latency, cost, terms).
- Cloud hosting provider (TBD via ADR in Phase B).
- Email delivery provider (for verification/reset) — TBD.

### 11.1 Resolved decisions (Gate A validation)

| ID | Decision | Source | Implication |
|---|---|---|---|
| **D1** | **UI chrome is user-selectable Bengali/English**, defaulting to Bengali | OQ1 | Full i18n framework from day one (string catalogs for both locales); both locales tested |
| **D2** | **Lean online + resilient** — online-only, but optimized for small payloads, caching, and graceful behavior on slow/dropped connections; **no PWA/offline in v1** | OQ3 | Performance/resilience are first-class NFRs; offline (PWA) recorded as a future option, architecture shouldn't preclude it |
| **D3** | **Broad multi-level curriculum at launch** (beginner → lower-intermediate) | OQ2 | Large content-authoring + linguistic-review effort; content model and author tooling become high-priority; phased authoring in the delivery plan |
| **D4** | **AI grading deferred to a later release**, but designed-for now | OQ4 | v1 grading is deterministic/rule-based (accepted-answer sets + normalization + curated hints). A **grading-strategy abstraction** is mandatory so an AI strategy slots in later. LLM cost NFRs apply to the future phase |

**Still open (resolve in later phases):**
- OQ5: Preferred **cloud provider / deployment target** — decide via ADR in Phase B.
- OQ6: Launch **geography/timeline** and the **content-authoring capacity** to realistically
  fill a broad multi-level curriculum (D3) — refine in the PRD/Delivery Plan.
- OQ7: Given D3's authoring load, confirm whether a **smaller pilot subset ships first**
  with the broader curriculum following — a sequencing question for the Delivery Plan.

---

## 12. Risks & mitigations (initial risk register — maintained through all phases)

| ID | Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|---|
| R1 | AI feedback gives **wrong corrections**, eroding trust | Med | High | Structured outputs + evaluation set + rule-based fallback + human-reviewed prompt; flag low-confidence |
| R2 | **LLM cost** grows faster than value | Med | High | Cache deterministic gradings; cache identical translations; batch content gen; budget alerts; rule-based first |
| R3 | **Curriculum quality** is poor or thin at launch | Med | High | Hand-authored + content-author tooling + linguistic review; versioned content |
| R4 | **Bengali rendering** issues (fonts, Unicode, input) | Med | Med | Treat i18n as an NFR; test on real devices; bundle/verify fonts |
| R5 | **Scope creep** (social, mobile, speech) delays launch | High | Med | Explicit non-goals; gate discipline; delivery plan sequencing |
| R6 | **Connectivity** on target devices degrades UX | Med | Med | Performance budgets; lean payloads; revisit offline (OQ3) |
| R7 | **Security/privacy** failure (accounts, PII to LLM) | Low | High | Dedicated security phase (STRIDE), minimal PII to model, secrets mgmt |
| R8 | **Solo/beginner build** stalls on complexity | Med | Med | Doc-first clarity; modular monolith (not microservices); explain-as-we-go; small slices in Phase D |
| R9 | **Over-engineering** for scale we don't have yet | Med | Med | "Measure, then scale" principle; pragmatic NFRs; ADRs justify complexity |

---

## 13. Glossary

- **SDLC** — Software Development Life Cycle: the staged process (requirements → design →
  build → test → release) we are following.
- **BRD / PRD** — Business / Product Requirements Document: *why & what* at the business
  level (BRD) and the detailed product level (PRD).
- **NFR** — Non-Functional Requirement: qualities like speed, security, reliability (as
  opposed to features).
- **L1 transfer** — mistakes in a new language caused by habits from one's first language.
- **Persona** — a representative fictional user we design for.
- **KPI** — Key Performance Indicator: a metric we track to judge success.
- **LLM** — Large Language Model (here, Anthropic's Claude) used for AI grading/feedback.
- **Gate** — a checkpoint where you review and approve before the next phase begins.
- **ADR** — Architecture Decision Record: a short note capturing a significant technical
  decision and its rationale (produced in Phase B).

---

## 14. Gate A checklist (what your approval here confirms)

- [ ] The problem and vision are correct and worth building.
- [ ] The personas reflect who we're really serving.
- [ ] The goals/non-goals (scope boundaries) are right.
- [ ] The success metrics are the right things to measure.
- [ ] Assumptions A1–A6 are valid (or corrected).
- [ ] Open questions OQ1–OQ5 are answered or accepted as "decide in next phase."

> **Next step after approval:** continue Phase A as BA/PM with `docs/10-brd.md`
> (Business Requirements Document), which turns this vision into concrete business
> objectives, stakeholders, and in/out-of-scope decisions.
