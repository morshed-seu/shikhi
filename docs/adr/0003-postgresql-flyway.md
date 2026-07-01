# ADR-0003 — PostgreSQL 16 + Flyway migrations

**Status:** Accepted (Gate B, 2026-07-01)
**Related:** CO-1, NFR-S4, NFR-D5, NFR-DI*, HLD §6

## Context
We need a reliable, widely-supported relational database (fixed direction: PostgreSQL,
CO-1) with strong integrity guarantees (NFR-DI*), read-scaling potential (NFR-S4), and a
disciplined schema-change process (NFR-D5).

## Decision
- **PostgreSQL 16**, run as a **managed service** (backups/HA by the provider).
- **Flyway** for versioned, automated, forward migrations (reversible where feasible).
- Use `JSONB` for exercise type-specific extras; normalize everything needed for querying,
  grading, and integrity (LLD §3).
- Design access patterns to be **read-replica-ready** (NFR-S4).

## Consequences
- ✅ Strong integrity (constraints, transactions) for XP/progress correctness.
- ✅ Managed service reduces ops for a solo team; automated backups (NFR-A7).
- ✅ Flyway makes schema changes safe and repeatable across environments.
- ⚠️ `JSONB` misuse could hide structure → validation + review keep it disciplined.

## Alternatives considered
- **Liquibase** vs Flyway: both fine; Flyway chosen for simplicity (plain SQL migrations).
- **NoSQL:** rejected — relational integrity fits our progress/content model better.
