# ADR-0007 — Frontend: React + Vite + TypeScript + supporting libraries

**Status:** Accepted (Gate B, 2026-07-01)
**Related:** CO-1, D1 (BN/EN), D2 (resilient), NFR-I*, NFR-AC*, NFR-P4/P5, HLD §3

## Context
Frontend direction is fixed (React + Vite + TS, CO-1). We must choose supporting libraries
for routing, server-state, i18n (D1), and resilient sync (D2), keeping payloads lean
(NFR-P4/P5) and the app accessible (NFR-AC*).

## Decision
- **React 18 + Vite + TypeScript** SPA.
- **React Router** for routing.
- **TanStack Query** for server-state (caching, retries, background refetch) — supports
  resilient sync and lean re-fetching (D2, NFR-N*).
- **react-i18next** for BN/EN catalogs and runtime switching (D1, NFR-I1).
- **Lightweight client state** (Context or Zustand) for local UI/sync-buffer state.
- Bundle & verify **Bengali fonts**; code-split and lazy-load to keep payloads small
  (NFR-P5); build output served via **CDN** (NFR-P6).

## Consequences
- ✅ Mainstream, well-documented stack; strong TS type-safety with the API contract.
- ✅ TanStack Query reduces hand-rolled caching/retry logic (D2 resilience).
- ⚠️ SPA needs deliberate accessibility work (focus management) → tracked in NFR-AC & tests.

## Alternatives considered
- **Next.js:** SSR/routing benefits but overlaps the Java API's role and adds a Node
  server; not needed for an authed app SPA. (Vision considered and set aside.)
- **Redux:** heavier than needed for our client state; TanStack Query covers server state.
