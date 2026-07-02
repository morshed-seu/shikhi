# ADR-0010 — Single repository (monorepo) for backend + frontend

**Status:** Accepted (M0 review, 2026-07-02)
**Deciders:** Architect (role-phase), Product Owner (approval)
**Related:** ADR-0001 (modular monolith), ADR-0002 (backend), ADR-0007 (frontend);
delivery plan §milestones; CO-3 (solo/beginner team)

## Context
Shikhi has two deployable artifacts: a Spring Boot backend (`backend/`) and a React/Vite
SPA (`frontend/`). They change together — most feature work (e.g. M1 email login) touches
the API and the UI in the same unit of work, both bound to one source of truth, the
OpenAPI contract (`docs/43-api-contract.openapi.yaml`). The team is a solo/beginner build
(CO-3). At M0 the two tiers were placed in one Git repository as sibling folders; this ADR
records that choice explicitly rather than leaving it as an undocumented default.

## Decision
Keep **one Git repository (a monorepo)** containing `backend/`, `frontend/`, `docs/`, and
infra, through at least the pilot. Independence between tiers is preserved at the
**build/deploy** layer (separate Docker image / static build per folder), not at the repo
layer. CI has a job per tier and may add path filters so each job runs only when its folder
changes.

## Consequences
- ✅ Cross-tier features ship as **one branch / commit / PR**; CI checks both sides against
  the API contract together, so the SPA can't silently drift from the API.
- ✅ **Atomic revert** — a bad cross-tier change is undone in a single commit.
- ✅ One clone, one CI config, one issue tracker — least ceremony for a solo build.
- ✅ Consistent with the modular-monolith philosophy (ADR-0001): keep boundaries clean
  *inside*, defer physical splits until there is a concrete reason.
- ⚠️ Cannot grant folder-scoped repo access → not a concern solo; use CODEOWNERS later.
- ⚠️ At extreme scale monorepos need special tooling → far beyond our ceiling.

## Reversibility
Splitting into `shikhi-backend` / `shikhi-frontend` later is a mechanical, low-risk
operation (`git filter-repo` preserves each folder's history). Merging two repos back into
one is messier. The monorepo is therefore the option-preserving default. **Revisit when** a
separate team, release cadence, or security domain emerges — most likely if/when a backend
module is extracted into its own service.

## Alternatives considered
- **Two repositories now:** rejected — forces coordinated PRs and risks contract drift for
  no benefit at our team size; premature.
- **Polyrepo per module:** rejected — contradicts ADR-0001; only sensible after service
  extraction, which we have not reached.
