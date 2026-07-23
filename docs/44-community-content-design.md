# 44 — Community Content Contribution: Design

**Project:** Shikhi (শিখি — "I learn")
**Document type:** Combined BRD-lite / PRD-lite / Architecture Specification
**Author role:** BA + Architect
**Status:** DRAFT — awaiting Gate (approval needed before implementation)
**Version:** 1.0
**Audience:** Product owner, backend engineers, future open-source contributors
**Builds on:** `41-architecture-lld.md` (content schema, §2.2/3.2), `20-prd.md` (E3 content),
`adr/0013-community-content-contribution.md` (the binding decision — read that first; this
document is the buildable spec behind it)

> **Decisions already locked (Q&A with product owner, 2026-07-18):** license = **CC BY-SA
> 4.0**; MCQ/answer keys = **public** in the content repo; v1 contribution mechanism =
> **GitHub pull requests only** (no web form yet). This document designs around those three
> choices; it does not re-litigate them.

---

## 1. Purpose & Goals

Today only the project owner can add vocabulary, lessons, or curriculum — every word and
exercise is a hand-written SQL row in the private app repo. The goal is to let outside
contributors (native Bengali speakers, English teachers, other volunteers) propose new
**words, meanings/glosses, examples, lessons, and curriculum structure**, have it reviewed in
public, and get it into the live app — without ever touching the app's source code, database,
or secrets.

**Non-goals (v1):** grading algorithm changes, UI/app code contributions (a normal PR to the
`shikhi` monorepo already covers that), non-technical contributor UX (phase 2, §7), real-time
or auto-merge publishing.

## 2. Personas & Scope

| Persona | Wants to contribute | Needs |
|---|---|---|
| Native Bengali-speaking volunteer | New words, better `bn_gloss` translations, natural example sentences | Git/GitHub basics; no app knowledge |
| English teacher / curriculum designer | New lessons, exercises, hint wording, L1-transfer-pattern tagging | Git/GitHub basics; understanding of CEFR levels and exercise types |
| Existing project maintainer (owner) | Reviews PRs, triggers import, publishes | Full access to both repos |

**In scope for v1:** vocabulary entries (Oxford-5000 style: headword, sense, POS, CEFR level,
Bengali gloss, bilingual example), lessons/exercises/hints within the existing curriculum tree,
L1-transfer pattern tags.
**Out of scope for v1:** new exercise *types* (MCQ/MATCH/WORD_BANK/FILL_BLANK/
TYPE_TRANSLATION/LISTENING is a closed set — adding a type is an app-code change, not content),
media assets (audio/image upload — `media_ref` stays a reference to existing hosted assets),
new CEFR levels beyond A1–C1.

## 3. Current State (what this builds on)

- **Vocabulary** (`backend/.../V11__vocabulary.sql`): flat table, one row per
  `(headword, sense_label, part_of_speech)`, columns `cefr_level (A1-B2, C1 added V17)`,
  `bn_gloss`, `example_en`, `example_bn`, `ordinal`. Seeded 5011 words A1–C1 (per project memory:
  examples must be short and contain their headword — this rule must be enforced by CI, not
  just convention).
- **Curriculum** (`backend/.../V2__content.sql`): versioned tree
  `content_versions (DRAFT/PUBLISHED/ARCHIVED) → levels → units → lessons → exercises`, plus
  `exercise_options` (MCQ/MATCH, `is_correct` server-side), `exercise_answers`
  (TYPE_TRANSLATION/FILL_BLANK accepted answers), `hints` (DEFAULT/PATTERN/WRONG_ANSWER),
  `l1_patterns` + `exercise_patterns` (Bengali L1-transfer tagging, e.g. `ARTICLE`, `COPULA`). A
  published `content_version` is **immutable**; new content is a new DRAFT, deep-cloned and
  amended, then published (cache-evicted on publish).
- **Existing authoring API** (built in M2): `POST /v1/admin/content/drafts|validate|publish`,
  AUTHOR/ADMIN-gated. This is the machinery the import pipeline (§6) reuses — we are **not**
  building a second way to write curriculum into the database.

## 4. Repository & Content Model

### 4.1 New repo: `shikhi-content` (public)

Independent of the app monorepo (ADR-0010 is unaffected — the private repo does not grow a
content folder). Layout:

```
shikhi-content/
  LICENSE                     # CC BY-SA 4.0
  CONTRIBUTING.md
  CODE_OF_CONDUCT.md
  schema/
    vocabulary.schema.json
    lesson.schema.json
    level.schema.json
  vocabulary/
    A1/
      time--n.yaml
      time--v.yaml           # sense_label disambiguates homographs
    A2/ ... B1/ ... B2/ ... C1/
  curriculum/
    a1/
      _level.yaml             # level title_en/title_bn/ordinal
      greetings/
        _unit.yaml            # unit title_en/title_bn/ordinal
        l1-saying-hello.yaml  # one lesson = one file (exercises inline)
        l2-saying-goodbye.yaml
  reference/
    l1-patterns.yaml          # code/name_en/name_bn — additions only, no deletes (FK'd)
  .github/workflows/validate.yml
```

One file per vocabulary entry / per lesson keeps PR diffs small and avoids two contributors
colliding in one giant file — this matters once volunteers work concurrently.

### 4.2 Vocabulary file schema (maps 1:1 to `vocabulary` table)

```yaml
# vocabulary/A1/time--n.yaml
headword: time
sense_label: null            # set when disambiguating a homograph, e.g. "river" vs "money" bank
part_of_speech: "n."
cefr_level: A1
bn_gloss: "সময়"
example_en: "What time is it?"
example_bn: "এখন কয়টা বাজে?"
ordinal: 42                  # stable alphabetical-within-level position; CI checks no collision
contributor: "@github-handle" # attribution, for CC BY-SA compliance
```

CI-enforced rules (mirrors the existing vocab-layer convention, now made mechanical instead of
tribal knowledge): `example_en` must contain `headword` (case-insensitive); both examples ≤ the
DB's `varchar(300)`; `cefr_level ∈ {A1,A2,B1,B2,C1}`; file path's CEFR folder matches
`cefr_level`; `(headword, sense_label, part_of_speech)` unique across the whole repo (matches
the DB's `uq_vocabulary_head_sense_pos` constraint — catching the conflict in CI is cheaper than
catching it at import time).

### 4.3 Lesson file schema (maps to `lessons`/`exercises`/`exercise_options`/
`exercise_answers`/`hints`/`exercise_patterns`)

```yaml
# curriculum/a1/greetings/l1-saying-hello.yaml
code: l1-saying-hello
title_en: "Saying hello"
title_bn: "শুভেচ্ছা জানানো"
ordinal: 1
exercises:
  - type: MCQ
    ordinal: 1
    prompt_en: "How do you say 'হ্যালো' in English?"
    prompt_bn: "'হ্যালো' ইংরেজিতে কীভাবে বলবেন?"
    options:
      - {text_en: "Hello", text_bn: "হ্যালো", is_correct: true, ordinal: 1}
      - {text_en: "Goodbye", text_bn: "বিদায়", is_correct: false, ordinal: 2}
    l1_patterns: []
    hints:
      - {trigger: DEFAULT, text_en: "Think of a common greeting.", text_bn: "একটি সাধারণ শুভেচ্ছাবাক্য ভাবুন।"}
  - type: TYPE_TRANSLATION
    ordinal: 2
    prompt_en: "Translate: আমি ভালো আছি"
    prompt_bn: "অনুবাদ করুন: আমি ভালো আছি"
    accepted_answers:
      - {text: "I am fine", is_primary: true}
      - {text: "I'm fine", is_primary: false}
    l1_patterns: [COPULA]
    hints:
      - {trigger: WRONG_ANSWER, trigger_key: "I fine", text_en: "Bengali has no verb 'to be' here — English needs one.", text_bn: "..."}
```

CI-enforced rules: `type` is one of the six existing values; MCQ/MATCH need `options` with
≥1 `is_correct: true`; TYPE_TRANSLATION/FILL_BLANK need ≥1 `accepted_answers` with exactly one
`is_primary: true`; WORD_BANK needs ≥2 tokens; every `l1_patterns` code must already exist in
`reference/l1-patterns.yaml`; `ordinal` unique within its parent (lesson-within-unit,
exercise-within-lesson) — same uniqueness constraints the DB enforces, checked before a human
ever reviews the PR.

## 5. Contribution Workflow

1. Contributor forks `shikhi-content`, adds/edits one or more YAML files, opens a PR.
2. **CI (`validate.yml`)** runs the JSON-Schema + cross-file checks from §4.2/4.3 on changed
   files only. Failing CI blocks merge (branch protection) — this is the first line of defense
   against malformed or spam content and needs no maintainer time.
3. A maintainer (or a trusted reviewer role, phase 2) reviews for pedagogical/linguistic
   correctness — CI cannot judge whether a Bengali gloss is *accurate*, only that it's
   well-formed. This is a standing human cost (flagged as an NFR risk, §8).
4. On approval, maintainer merges to `main`. **Nothing auto-deploys** — merging only means "this
   is accepted into the public content corpus," not "this is live for learners" (ADR-0013).

## 6. Import Pipeline (merged content → live app)

Maintainer-triggered (not automatic on merge — keeps a human checkpoint before touching
production), reusing existing machinery rather than a new write path:

- **Vocabulary:** import job diffs `shikhi-content/vocabulary/**` against the `vocabulary`
  table and **upserts** by `(headword, sense_label, part_of_speech)` — new rows insert, changed
  `bn_gloss`/examples update in place (git history is the audit trail; no new versioning
  concept needed since vocabulary was never versioned). Re-validates every rule from §4.2
  server-side before writing (never trust CI-only validation for a write path).
- **Curriculum:** import job calls the existing `POST /v1/admin/content/drafts` (clone latest
  PUBLISHED), applies the new/changed lessons from the merged files, `POST .../validate`, then
  a maintainer reviews the resulting DRAFT in the app admin view and `POST .../publish` when
  ready — identical to how the owner already ships curriculum changes today (M2). Community
  content becomes just another input to a process that already exists.
- Both paths are idempotent and safe to re-run (re-importing an unchanged file is a no-op).

## 7. Governance & Licensing

- **LICENSE:** CC BY-SA 4.0 for all files under `vocabulary/`, `curriculum/`, `reference/`.
  `CONTRIBUTING.md` must state this explicitly so contributors know their submission is
  released under it (no separate CLA — the license terms serve that role).
- **Attribution:** `contributor:` field per file (§4.2) plus normal git blame/PR history; a
  generated `CONTRIBUTORS.md` (or in-app credits page, future work) rolls these up.
- **CODE_OF_CONDUCT.md:** standard contributor covenant — needed once the repo is public and
  accepting outside PRs.
- **Phase 2 (deferred, not rejected):** a lightweight web form for non-technical contributors
  (teachers without git literacy) that opens a PR on their behalf via the GitHub API — noted
  explicitly per the product owner's decision to ship GitHub-PR-only first and revisit if
  adoption data shows this is the blocker. Not designed further here.

## 8. Non-Functional Requirements

| # | Requirement | Mitigation |
|---|---|---|
| NFR-C1 | Spam/malformed PRs must not consume unbounded maintainer time | CI schema validation as first gate (§5.2); GitHub's own PR spam tooling |
| NFR-C2 | Malicious content must not reach production | Content is data (YAML → typed fields), never executed; `config`/free-text fields are size-capped and re-validated server-side at import (§6), not trusted from CI alone |
| NFR-C3 | Import must not silently corrupt existing published content | Curriculum import goes through DRAFT→validate→publish (existing immutable-publish guarantee); vocabulary upsert is keyed on the existing unique constraint, never a blind overwrite of unrelated rows |
| NFR-C4 | Public answer keys are a known, accepted trade-off (ADR-0013) | Documented in this doc and the ADR; not treated as a bug to "fix" later without a product decision |
| NFR-C5 | Two schemas (content repo YAML, app DB) must not silently drift | Both derive from the same field list in this document; a future improvement (not v1-blocking) is generating the JSON Schema from the DB migration or vice versa |

## 9. Phased Delivery Plan

- **Phase 1 — Repo bootstrap:** create `shikhi-content` (public, CC BY-SA 4.0 LICENSE,
  CONTRIBUTING.md, CODE_OF_CONDUCT.md), commit `schema/*.json`, seed it from the **existing**
  vocabulary/curriculum data (export current DB rows → YAML) so day one already has full
  coverage, not an empty shell. CI validate workflow + branch protection (require CI green +
  1 review).
- **Phase 2 — Import tooling:** vocabulary upsert script + curriculum-draft importer (§6),
  run manually by the maintainer against a merged batch; dry-run/`--check` mode first.
- **Phase 3 (future, not this gate):** non-technical contribution form/CMS (§7); trusted-reviewer
  role beyond the single maintainer; automated linguistic QA (e.g. flag suspiciously short
  glosses) as a CI advisory check.

This gate covers **Phase 1 + 2 design approval**; Phase 3 is explicitly deferred and not part
of what's being asked for approval now.

## 10. Open Questions

1. Repo name/org: `shikhi-content` under the same GitHub account/org as `shikhi`? (assumed yes,
   confirm before repo creation — creating a new public repo is an action to confirm explicitly,
   not something to do silently.)
2. Review bandwidth: is the product owner the sole reviewer initially, or is a trusted second
   reviewer needed from day one given bilingual (Bengali) review is required for gloss accuracy?
3. Should `reference/l1-patterns.yaml` additions require the same PR review bar as lessons, given
   how central correct L1-pattern tagging is to the product's differentiator (per project memory)?

## 11. Test Strategy (for the tooling, not the content)

- **Schema validator (CI):** unit tests over fixture files — one valid + one invalid fixture per
  rule in §4.2/4.3 (missing accepted answer, duplicate ordinal, example without headword, bad
  CEFR/folder mismatch, unknown `l1_patterns` code, etc.).
- **Import scripts:** integration tests against a Testcontainers Postgres (matches the app's
  existing test pattern) — upsert idempotency (re-run = no-op), draft/validate/publish
  round-trip using the real authoring API, and a rejection case (bad data must fail validate,
  never reach publish).

---

**Gate for this document:** approve Phase 1+2 design as specified above (repo layout, schemas,
CI rules, import pipeline reusing the existing authoring API, CC BY-SA 4.0 + public answers per
prior decisions) → proceed to Phase 1 build; or request revisions to specific sections.
