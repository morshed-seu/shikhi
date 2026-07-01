# ADR-0002 — Backend: Java 21 + Spring Boot 3 + Gradle + Spring Modulith

**Status:** Accepted (Gate B, 2026-07-01)
**Related:** CO-1 (fixed direction), HLD §4, ADR-0001

## Context
The backend technology direction (Java/Spring Boot) is fixed by prior decision (CO-1). We
must choose concrete versions and supporting frameworks that are current, well-supported,
and beginner-friendly with strong documentation.

## Decision
- **Java 21 (LTS)** — long-term support, modern language features.
- **Spring Boot 3.x** — mature, batteries-included (Web, Data JPA, Security, Validation,
  Actuator for health/metrics).
- **Gradle** — concise build, good multi-module support.
- **Spring Modulith** — enforce module boundaries within the monolith (ADR-0001).
- **Bean Validation** for input validation; **Spring Actuator** for health/metrics.

## Consequences
- ✅ Huge ecosystem, docs, and examples — good for a beginner with AI assistance.
- ✅ Actuator gives health/readiness/metrics with little effort (NFR-A2/O2).
- ⚠️ Spring's breadth has a learning curve → mitigated by explaining artifacts as we build.

## Alternatives considered
- **Maven** instead of Gradle: viable; Gradle chosen for concise multi-module config.
- **Quarkus/Micronaut:** lighter/faster startup but smaller ecosystem and less beginner
  material; not chosen given CO-1 and learning goals.
