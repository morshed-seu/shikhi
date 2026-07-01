# 10 — Business Requirements Document (BRD)

**Project:** Shikhi (শিখি — "I learn")
**Document type:** Business Requirements Document
**Author role:** Business Analyst / Product Manager
**Status:** DRAFT — part of Phase A; reviewed at Gate A
**Version:** 0.1
**Builds on:** `00-vision-and-discovery.md` (and its Gate-A decisions D1–D4)

> **How to read this (for a non-specialist):** The Vision doc said *why* we're building
> Shikhi. This BRD translates that into **business-level requirements** — the outcomes the
> product must deliver, who's involved, what's in and out of scope, and how we'll judge
> success — *without* yet describing screens or features in detail (that's the PRD, doc 20).
> Each requirement has an ID like **BR-1** so later documents (PRD, design, tests) can
> trace back to it.

---

## 1. Business context & opportunity

Bengali is among the most-spoken first languages in the world, and functional English is a
gateway to education, employment, and the wider internet for its speakers. Existing
language apps largely assume English literacy and ignore the specific difficulties Bengali
speakers face. Shikhi addresses an underserved, large-population need with a focused,
bilingual, Bengali-first learning experience.

This BRD covers the **first product generation**: a scale-ready web platform delivering a
broad, multi-level English curriculum to Bengali speakers, with deterministic grading in
v1 and an AI grading capability designed-for but delivered later (decision **D4**).

---

## 2. Business objectives

> Business objectives are the measurable outcomes the *organization/product* wants. They
> are deliberately broader than product KPIs and set the direction for everything below.

| ID | Objective | Rationale | Tied to |
|---|---|---|---|
| **BO-1** | Establish Shikhi as a credible, trusted English-learning product for Bengali speakers | Trust drives adoption and retention in education | Vision G1, G5 |
| **BO-2** | Drive sustained learning habits (daily engagement, returning learners) | Habit is the core value and the growth engine | Vision G3 |
| **BO-3** | Deliver demonstrably better learning outcomes for Bengali-L1 errors than generic apps | The core differentiator and reason to choose Shikhi | Vision G2 |
| **BO-4** | Operate reliably and securely at growing scale, within a controlled cost base | A real product must be dependable and economically sane | Vision G5 |
| **BO-5** | Build a platform that can extend later (AI feedback, more levels, mobile) without re-platforming | Protect future optionality; avoid rework | Vision NG1–NG3, D4 |

---

## 3. Stakeholders

> A **stakeholder** is anyone affected by, or with influence over, the product.

| Stakeholder | Interest / concern | Role in this project |
|---|---|---|
| **Product owner (you)** | Vision, scope, budget, sign-off | Approves every gate; final decision-maker |
| **Learners** (Personas Rahim, Anika, Mr. Das) | Effective, motivating, affordable learning in Bengali | End users; success measured by their outcomes |
| **Content author** (Persona Shreya) | Safe, simple authoring; content quality | Internal user; produces/curates curriculum |
| **Build team** (you + AI roles: BA, Architect, Dev, QA, Reviewer, DevOps, Security) | Clear requirements; maintainable system | Deliver the product through the SDLC |
| **Anthropic (Claude API)** | Acceptable use; cost | External dependency (future AI phase) |
| **Hosting / infra & email providers** | Reliable operation | External dependencies (selected in Phase B) |
| **Regulators / data-protection regimes** | Lawful handling of personal data | Compliance constraints (Security phase) |

---

## 4. Scope

### 4.1 In scope (first product generation)

- **BR-1 — Learner accounts (multi-method identity):** self-service sign-up, login,
  profile, and account deletion, supporting **email+password, phone-number+OTP, and social
  login (e.g., Google)** behind an **extensible identity model** so methods can be added or
  phased. Includes email verification and password reset where applicable. *(D5; BO-1,
  BO-4.)*
- **BR-2 — Bilingual product surface:** user-selectable Bengali/English UI (default
  Bengali), with all learning content and feedback bilingual. *(D1; BO-1, BO-3.)*
- **BR-3 — Broad multi-level curriculum:** a structured English curriculum spanning
  beginner → lower-intermediate, organized into levels → units → lessons → exercises,
  with content explicitly targeting Bengali-L1 error patterns. *(D3; BO-3.)*
- **BR-4 — Core learning loop:** short lessons composed of varied exercise types with
  immediate feedback, a "lives/hearts" mechanic, and a results summary. *(BO-2, BO-3.)*
- **BR-5 — Deterministic grading with actionable feedback:** rule-based grading
  (accepted-answer sets, normalization) including for free-form translation, with curated,
  bilingual, pattern-named hints. *(D4; BO-3.)*
- **BR-6 — Progression & motivation:** XP, levels, daily streaks, lesson/unit unlocking,
  and achievements. *(BO-2.)*
- **BR-7 — Spaced-repetition review:** resurface previously-missed items for durable
  learning. *(BO-3.)*
- **BR-8 — Cross-device progress:** progress, stats, and streaks persist per account and
  sync across devices/browsers. *(BO-1, BO-2.)*
- **BR-9 — Content authoring & validation tooling:** internal capability for a
  non-engineer to create, edit, version, and validate curriculum safely. *(D3; BO-3, BO-5.)*
- **BR-10 — Operational readiness:** health/observability surfaces, security, privacy
  controls (data export/delete), and the reliability needed to run in production. *(BO-4.)*
- **BR-11 — Extensibility seams:** designed-for, not-yet-built integration points —
  notably a **grading-strategy abstraction** so an AI grader can be added later, and a
  curriculum model that can grow to more levels. *(D4; BO-5.)*

### 4.2 Out of scope (first generation — recorded, not forgotten)

- **OOS-1:** Native iOS/Android apps. *(Vision NG1)*
- **OOS-2:** AI-powered grading/feedback in v1 — *designed-for via BR-11, delivered
  later.* *(D4; Vision G4)*
- **OOS-3:** Offline / installable PWA — v1 is "lean online + resilient." *(D2)*
- **OOS-4:** Speech recognition / pronunciation scoring. *(Vision NG2)*
- **OOS-5:** Social features — friends, leagues, leaderboards, chat. *(Vision NG3)*
- **OOS-6:** Live human tutoring / community Q&A. *(Vision NG4)*
- **OOS-7:** Monetization/payments/subscriptions (assumed not in first generation; confirm
  in OQ-B1 below).
- **OOS-8:** Languages other than English, or learners with a first language other than
  Bengali. *(Vision NG5)*

---

## 5. Key business processes (conceptual, not UI)

> These describe *what happens in the business sense*, independent of screens. The PRD
> turns them into concrete flows.

- **P1 — Onboarding & placement:** a new learner creates an account, selects interface
  language, and (optionally) takes a placement assessment that sets a sensible starting
  point in the curriculum.
- **P2 — Daily learning session:** a learner opens the app, plays one or more lessons,
  receives feedback, earns XP, maintains their streak, and unlocks new content.
- **P3 — Review cycle:** the system periodically surfaces items the learner previously got
  wrong, to reinforce them.
- **P4 — Content lifecycle:** an author drafts curriculum, it is validated and versioned,
  then published to learners; corrections can be issued without disrupting learners.
- **P5 — Account & data lifecycle:** a learner manages their profile and can export or
  delete their data; the business retains only what's needed, for only as long as needed.

---

## 6. Success criteria (business-level)

> These restate the Vision metrics as **business acceptance criteria**. Concrete numeric
> targets are finalized with the PRD/Delivery Plan once we agree what's realistic
> (Vision §7 notes targets are directional).

- **SC-1 (Adoption):** a meaningful share of sign-ups complete their first lesson
  (activation), validating the on-ramp. *(BO-1)*
- **SC-2 (Retention/habit):** learners return and sustain streaks at target Day-1/7/30
  rates. *(BO-2)*
- **SC-3 (Learning effectiveness):** measurable improvement on targeted Bengali-L1 error
  categories (e.g., reduced error rate on article/tense/preposition exercises over time).
  *(BO-3)*
- **SC-4 (Reliability & cost):** the platform meets its availability/latency SLOs (defined
  in NFRs) and operates within its cost budget as usage grows. *(BO-4)*
- **SC-5 (Extensibility realized):** the AI grading strategy can later be added behind the
  BR-11 seam without rework to existing grading flows. *(BO-5)*

---

## 7. Constraints (business & solution)

Carried from Vision §10 and the Gate-A decisions:

- **CO-1:** Fixed technology direction — React+Vite+TS front end, Java/Spring Boot back
  end, PostgreSQL, modular-monolith approach.
- **CO-2:** Document-first full SDLC with role-phases and approval gates.
- **CO-3:** Beginner-friendly delivery — explanations accompany artifacts; avoid
  over-engineering relative to current needs.
- **CO-4:** Bilingual (BN/EN) requirement with correct Bengali Unicode/font support is
  mandatory (D1).
- **CO-5:** v1 is online-only but must degrade gracefully on slow/dropped connections (D2).
- **CO-6:** AI is deferred (D4); v1 must deliver value without it.
- **CO-7:** Cost discipline — even before AI, hosting/infra cost must be controlled and
  monitored.

---

## 8. Assumptions (business-level; validate at Gate A)

- **AB-1:** No payments/monetization in the first generation (see OOS-7 / OQ-B1).
- **AB-2:** Content-authoring capacity exists (you or a collaborator) to fill a broad
  multi-level curriculum over time — likely phased (Vision OQ7). *Material risk if false.*
- **AB-3:** A single production region is acceptable initially (multi-region is a future
  scaling step).
- **AB-4:** English target register is general/standard, not exam-specific (Vision A6).
- **AB-5:** Email-based identity is acceptable (vs. phone-number/OTP, common in the region
  — see OQ-B2).

---

## 9. Dependencies

- **DEP-1:** Cloud hosting provider — selected via ADR in Phase B (Vision OQ5).
- **DEP-2:** Transactional email provider — for verification/reset (Phase B).
- **DEP-3:** Anthropic Claude API — *future* AI phase only (D4).
- **DEP-4:** Bengali font/Unicode tooling and real-device testing capability (CO-4).

---

## 10. High-level cost/benefit (qualitative)

- **Benefits:** addresses a large underserved audience; differentiated by Bengali-L1
  focus; habit loop drives retention; deferring AI reduces initial cost and risk while the
  seam preserves the future upside.
- **Costs/effort:** broad curriculum authoring (the largest non-engineering cost, AB-2);
  building a scale-ready platform; bilingual i18n; ongoing hosting; *future* AI usage cost.
- **Key trade-off accepted:** broad curriculum at launch (D3) increases authoring effort;
  mitigated by phasing content and prioritizing the authoring tool (BR-9) — to be
  sequenced in the Delivery Plan.

---

## 11. Resolved business decisions (Gate A)

| ID | Decision | Resolves | Implication |
|---|---|---|---|
| **D5** | **Multi-method identity**: email+password, phone+OTP, and social (Google) behind an extensible identity model | OQ-B2 | Auth design must abstract "identity provider"; SMS + OAuth dependencies added; can be **phased** (email first) in the Delivery Plan |
| **D6** | **Curated pilot first, then expand**: build the platform for broad multi-level content, but **launch with a polished beginner subset** and add levels over time | OQ-B3, Vision OQ7 | Reduces pre-launch authoring risk (AB-2); content model + author tooling (BR-9) prioritized; curriculum growth is a continuous track in the Delivery Plan |
| **D7** | **Monetization fully out of scope** for the first generation | OQ-B1 | No billing/payments work; no special "design-for-payments" effort required (supersedes any BR-11 payments hook); revisit as a separate future effort |
| **D8** | **No hard deadline or budget ceiling** — optimize for quality and learning | OQ-B4 | Sequence work for quality and de-risking; still apply cost discipline (CO-7) as good practice, not a hard cap |

> AB-1 is now **confirmed** (no monetization). The previously-open OQ-B1–B4 are resolved
> above. Remaining open items (cloud provider — Vision OQ5; detailed content-authoring
> capacity/phasing — Vision OQ6/OQ7) are handled in Phase B (architecture) and the
> Delivery Plan respectively.

---

## 12. Traceability (Vision → BRD)

| Vision item | BRD coverage |
|---|---|
| G1 Comprehension-first | BR-2, BO-1, SC-1 |
| G2 Targeted learning | BR-3, BR-5, BR-7, BO-3, SC-3 |
| G3 Habit & motivation | BR-4, BR-6, BO-2, SC-2 |
| G4 Helpful feedback (phased) | BR-5 (v1 rule-based), BR-11 + OOS-2 (AI later), D4 |
| G5 Trustworthy & durable | BR-1, BR-10, BO-4, SC-4 |
| D1 User-selectable BN/EN | BR-2, CO-4 |
| D2 Lean online + resilient | OOS-3, CO-5 |
| D3 Broad multi-level | BR-3, BR-9, AB-2, OQ-B3 |
| D4 AI deferred, designed-for | BR-5, BR-11, OOS-2, CO-6, SC-5 |

> **Next step after Gate A:** continue Phase A as BA/PM with `docs/20-prd.md` (Product
> Requirements Document) — turning these business requirements into epics, user stories
> with acceptance criteria, UX flows, and the detailed content model. The PRD will resolve
> several open questions above into concrete product behavior.
