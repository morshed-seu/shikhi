# 91 — Performance validation results (Phase E)

Validates the interactive hot-path latency budgets from [`30-nfr.md`](30-nfr.md) before
launch. Re-run any time with [`../perf/loadtest.py`](../perf/loadtest.py) (stdlib only, no
install).

## Budgets under test

| NFR | Path | Budget |
|---|---|---|
| **NFR-P1** | Core reads (curriculum, lesson, stats) | p95 ≤ 200 ms, p99 ≤ 400 ms |
| **NFR-P2** | Writes (start session) | p95 ≤ 300 ms, p99 ≤ 600 ms |

## Method

- `python3 perf/loadtest.py --concurrency 25 --requests 1500`
- Registers a learner, discovers a published lesson, warms each endpoint (50 calls), then
  drives 25 concurrent workers per endpoint and records the client-side latency
  distribution. On loopback, network overhead is negligible, so this is a fair proxy for
  the server-side budget.
- Single backend instance + local PostgreSQL 16 (Testcontainers-equivalent), curriculum &
  lesson reads served through the app cache (as in production, NFR-S5).

## Results — 2026-07-03 (pilot-v1 content, commit at E3)

| Endpoint | Type | p50 | p95 | p99 | max | Throughput | Errors | Verdict |
|---|---|---|---|---|---|---|---|---|
| `GET /v1/curriculum` | read | 7.1 ms | **16.3 ms** | 20.9 ms | 27.0 ms | 3,042 req/s | 0 | ✅ PASS |
| `GET /v1/lessons/{id}` | read | 4.7 ms | **9.2 ms** | 13.2 ms | 21.4 ms | 4,650 req/s | 0 | ✅ PASS |
| `GET /v1/stats` | read | 5.3 ms | **9.4 ms** | 11.6 ms | 13.7 ms | 4,271 req/s | 0 | ✅ PASS |
| `POST /v1/sessions` | write | 8.0 ms | **15.3 ms** | 17.9 ms | 19.4 ms | 2,597 req/s | 0 | ✅ PASS |

**OVERALL: ALL PASS** — every path clears its budget with ~10–20× headroom and zero errors.

## Interpretation & caveats

- The budgets are met with wide margin on a single instance; the design is **stateless**
  (NFR-S1) and horizontally scalable (NFR-S2), so headroom grows by adding instances.
- Measured on loopback without TLS or a real load balancer. Production adds network + TLS
  latency (typically single-digit to low-tens of ms), which the current margin absorbs
  comfortably, but the budget should be **re-measured in staging** against the real
  topology before declaring the SLO met at scale.
- Not yet exercised: sustained soak (hours), 1,000+ concurrent learners (NFR-S3) on
  production-sized data, and the future AI grading path (NFR-P7). These are staging/soak
  activities tracked for post-pilot.
- Deterministic grading (NFR-P3, ≤50 ms) is in-process and trivially within budget; it is
  covered indirectly by the write path above and by unit tests.
