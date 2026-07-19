# ADR-0013 — Community content contribution: separate public repo, GitHub-PR workflow

**Status:** Proposed
**Related:** ADR-0006 (AI grading seam — answer confidentiality precedent this ADR relaxes for
community content), ADR-0010 (repository strategy — this ADR deliberately does **not** extend
the monorepo), `20-prd.md` (E3 content/curriculum), `41-architecture-lld.md` §2.2/3.2 (content
schema), `44-community-content-design.md` (full design)

## Context
Shikhi's lessons, exercises, and vocabulary today live as hand-written Flyway seed migrations
(`V2`–`V19`, `V22`) inside the private app monorepo (ADR-0010), authored solely by the project
owner. The owner wants outside contributors — native Bengali speakers, teachers, other
volunteers — to be able to propose new words, meanings, examples, lessons, and curriculum
without needing write access to the app's source code or database.

Two shapes were considered: (a) build an in-app CMS/review-queue backed by a login-gated
contributor role, or (b) keep contribution entirely outside the app, in a public repository
review contributors already know how to use.

## Decision
**Content moves to a separate public repository** (`shikhi-content`), independent of the
private `shikhi` app monorepo. It holds no application code — only structured content files
(one YAML file per vocabulary entry and per lesson) plus a CI schema validator. Contribution
happens through **standard GitHub pull requests** (v1 scope only — no custom web form or CMS in
this phase; see `44-community-content-design.md` §7 for the phase-2 form option).

Supporting decisions bundled into this ADR:
- **License: CC BY-SA 4.0.** Contributed words/lessons/curriculum are openly licensed
  (attribution + share-alike), consistent with treating the corpus as a public-good language
  resource rather than proprietary app content.
- **Answer keys are public.** MCQ `is_correct` flags and `exercise_answers.accepted_answer`
  values are stored in plain sight in the content repo, reviewable by anyone. This **reverses**
  the closed-answer stance recorded in `V2__content.sql` ("`is_correct` is server-side only,
  never serialized to learners") and implicitly assumed by ADR-0006 — that stance is retained
  for the *app API* (grading responses still never leak answers to a learner mid-session) but no
  longer holds for the *source of the content itself*. Accepted trade-off: a motivated learner
  can look up answers on GitHub; simplicity and full community reviewability were prioritized
  over answer secrecy.
- **Import is one-directional and review-gated.** A merged PR in `shikhi-content` does not
  touch production directly. A maintainer-triggered import job pulls the merged content,
  validates it again server-side, and lands it as a new **DRAFT** `content_version` (curriculum)
  or an idempotent upsert (vocabulary) — both using machinery already built for the in-app
  authoring API (`POST /v1/admin/content/drafts|validate|publish`, ADR/§ M2). A human still
  clicks publish. No PR merge auto-deploys to learners.

## Consequences
- ✅ Contributors need only git/GitHub literacy, not access to the app codebase, database, or
  CI secrets — the private monorepo's blast radius is untouched.
- ✅ Reuses the existing content-versioning system (immutable-once-published `content_version`
  tree) instead of inventing a new one — imports are just another producer of DRAFT versions.
- ✅ Full public review history (PR discussion, CI checks, diffs) for pedagogical/linguistic
  correctness — valuable given the L1-transfer-pattern curation is specialist work.
- ⚠️ Public answer keys are a real, accepted trade-off (see above) — must be called out to
  learners/stakeholders, not silently discovered later.
- ⚠️ GitHub-PR-only contribution (v1) excludes non-technical contributors (many native-speaker
  teachers won't know git). Documented as a known gap; phase-2 form/CMS is deferred, not
  rejected — see `44-community-content-design.md` §7.
- ⚠️ Two repos to keep in sync (schema drift risk): the content repo's YAML schema must track
  the app's DB schema (`vocabulary`, `exercises`, `exercise_options`, `exercise_answers`,
  `hints`, `l1_patterns`). Mitigated by generating/validating the YAML schema from the same
  source of truth documented in `41-architecture-lld.md`, and by CI validation in both repos.
- ⚠️ Moderation load: every PR needs a maintainer review before merge (spam, low-quality,
  offensive, or pedagogically wrong content) — this is a standing ongoing cost, not a one-time
  build cost.

## Alternatives considered
- **In-app CMS with a contributor role:** rejected for v1 — much larger build (auth for
  external users, review-queue UI, spam/abuse tooling duplicate of what GitHub already gives
  for free). Revisit if GitHub-PR friction proves to be the adoption blocker phase-2 anticipates.
- **Content folder inside the existing private monorepo, made public:** rejected — would force
  the whole monorepo (including auth, grading internals, infra config) to go public, or require
  splitting history, to get a public content folder. A dedicated repo has no such coupling.
- **Auto-publish on PR merge (no DRAFT/review gate):** rejected — removes the human checkpoint
  between "community agreed this is good" and "this is live for learners," and bypasses the
  existing immutable-publish safety model for no real benefit.
