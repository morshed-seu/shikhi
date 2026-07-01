# ADR-0009 — Observability: OpenTelemetry + Micrometer/Prometheus + structured logs

**Status:** Accepted (Gate B, 2026-07-01)
**Related:** NFR-O1–O5, NFR-A1/A4, SC-4, HLD §7

## Context
A scale-ready product needs to be observable to meet SLOs (NFR-A1) and debug issues fast
(NFR-O*). We need logs, metrics, and traces that work across horizontally-scaled instances
and are ready to track LLM cost when AI ships (NFR-O5).

## Decision
- **Structured JSON logging** (Logback) with a **correlation ID** propagated
  client → server → logs/traces (NFR-O1).
- **Metrics via Micrometer** exposed for **Prometheus** (latency, throughput, error rate,
  saturation) + **business metrics** (activation, lessons completed) feeding KPIs.
- **Distributed tracing via OpenTelemetry** (NFR-O3).
- **Dashboards + SLO alerts** (error-budget burn, latency, availability) — NFR-O4.
- Reserve hooks for **LLM cost/usage telemetry** (tokens, cache-hit, fallback rate) for the
  AI phase (NFR-O5).

## Consequences
- ✅ Vendor-neutral (OTel) → export to the chosen provider's monitoring or an OSS stack.
- ✅ Correlation IDs make cross-instance debugging tractable.
- ⚠️ Instrumentation is upfront effort → justified by the scale-ready NFR bar (D8).

## Alternatives considered
- **Logs only (no metrics/traces):** rejected — insufficient for SLOs and scale debugging.
- **Proprietary APM only:** viable, but OTel keeps us portable (matches ADR-0008).
