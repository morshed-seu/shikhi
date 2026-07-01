# ADR-0006 — Grading-strategy seam; AI grading deferred behind it

**Status:** Accepted (Gate B, 2026-07-01)
**Related:** D4, BR-5, BR-11, NFR-M6, NFR-P7, NFR-C3, HLD §5(F5), LLD §2.4

## Context
AI grading is deferred (D4) but must be addable later **without disrupting** the lesson
player or stored progress (NFR-M6, SC-5). We must design the seam now and build only the
rule-based path.

## Decision
Introduce a **`GradingStrategy`** interface returning a fixed **`GradingVerdict`** shape
`{correct, feedback(bilingual), matchedPatternCode?, confidence?, source}`.
- **v1:** `RuleBasedStrategy` only.
- **Later (AI phase):** compose `CachingStrategy(FallbackStrategy(AiStrategy,
  RuleBasedStrategy))` — cache → AI → rule-based fallback. `AiStrategy` uses the **Anthropic
  Java SDK**, model **`claude-opus-4-8`**, **structured outputs** for a parseable verdict,
  with **timeouts, retries, circuit breaker, result caching, and budget/telemetry**
  (NFR-P7/C3/O5). Minimal PII sent (NFR-PR4).
- The lesson player and progress depend only on the **verdict shape**, never on how it's
  produced.

## Consequences
- ✅ AI becomes purely additive; no rework to existing flows (SC-5).
- ✅ Fallback guarantees learning continues if AI is down (NFR-A3).
- ⚠️ Slight up-front design cost for a seam we won't fully use yet — deliberate and small.

## Alternatives considered
- **Bake grading into the lesson player:** rejected — would force a rewrite to add AI.
- **Build AI now:** rejected per D4 (cost/scope deferral).
