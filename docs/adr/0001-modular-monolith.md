# ADR-0001 — Modular monolith (not microservices)

**Status:** Accepted (Gate B, 2026-07-01)
**Deciders:** Architect (role-phase), Product Owner (approval)
**Related:** HLD §1/§3; drivers CO-3, R8, NFR-S/M6

## Context
Shikhi must be scale-ready (NFR-S) yet built and operated by a solo/beginner team (CO-3,
R8). Microservices offer independent scaling but impose heavy operational cost
(distributed systems, network failure modes, multiple deploys) that is inappropriate for a
one-person team and unnecessary at launch traffic.

## Decision
Build a **modular monolith**: a single deployable backend internally divided into strict
modules (identity, content, learning, grading, progress, review, admin) with explicit
interfaces, enforced by **Spring Modulith**. Modules must not access each other's data;
communication is via published application-service interfaces.

## Consequences
- ✅ Low operational complexity; one deploy, one runtime, simple local dev.
- ✅ Horizontal scale by running multiple stateless instances (NFR-S1/S2).
- ✅ Clean boundaries make later **extraction** of a module into a service cheap.
- ⚠️ Risk of boundary erosion → mitigated by Modulith checks, ADRs, review gates.
- ⚠️ One module's heavy load scales the whole app until extracted → acceptable at our scale.

## Alternatives considered
- **Microservices:** rejected now (ops burden, premature for traffic/team).
- **Unstructured monolith:** rejected (turns into a "big ball of mud"; no extraction path).
