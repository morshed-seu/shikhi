#!/usr/bin/env python3
"""Shikhi load test — validates core API latency against the NFR budgets.

No external dependencies (stdlib only). Registers a learner, discovers a lesson, then
drives concurrent traffic at the core read paths (NFR-P1: p95 <= 200 ms, p99 <= 400 ms)
and a representative write (NFR-P2: p95 <= 300 ms, p99 <= 600 ms), reporting a latency
distribution and PASS/FAIL per budget.

Latencies are measured client-side against a local instance; on loopback the network
overhead is negligible, so this is a fair proxy for the server-side budget. Run against a
warm instance (this script warms each endpoint first).

Usage:  python3 perf/loadtest.py [--base http://localhost:8080] [--concurrency 25] [--requests 1500]
"""
import argparse
import json
import statistics
import sys
import time
import urllib.error
import urllib.request
import uuid
from concurrent.futures import ThreadPoolExecutor

# Budgets (ms) from docs/30-nfr.md
BUDGETS = {
    "read": {"p95": 200, "p99": 400},   # NFR-P1
    "write": {"p95": 300, "p99": 600},  # NFR-P2
}


def call(method, url, token=None, body=None, timeout=15):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(url, data=data, method=method)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    start = time.perf_counter()
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            resp.read()
            status = resp.status
    except urllib.error.HTTPError as e:
        e.read()
        status = e.code
    elapsed_ms = (time.perf_counter() - start) * 1000.0
    return status, elapsed_ms


def register(base):
    email = f"perf_{uuid.uuid4().hex[:12]}@example.com"
    status, _ = call("POST", f"{base}/v1/auth/register", body={"email": email, "password": "s3cretpassword"})
    # Read the token by calling again is wasteful; do a login to fetch it cleanly.
    data = json.dumps({"email": email, "password": "s3cretpassword"}).encode()
    req = urllib.request.Request(f"{base}/v1/auth/login", data=data, method="POST")
    req.add_header("Content-Type", "application/json")
    with urllib.request.urlopen(req, timeout=15) as resp:
        body = json.loads(resp.read())
    return body["accessToken"]


def first_lesson_id(base, token):
    req = urllib.request.Request(f"{base}/v1/curriculum", method="GET")
    req.add_header("Authorization", "Bearer " + token)
    with urllib.request.urlopen(req, timeout=15) as resp:
        tree = json.loads(resp.read())
    for level in tree.get("levels", []):
        for unit in level.get("units", []):
            for lesson in unit.get("lessons", []):
                return lesson["id"]
    return None


def run_scenario(name, kind, make_request, concurrency, total):
    # Warm up (JIT + caches) before measuring.
    for _ in range(min(50, total)):
        make_request()
    latencies = []
    errors = 0

    def worker(_):
        status, ms = make_request()
        return status, ms

    start = time.perf_counter()
    with ThreadPoolExecutor(max_workers=concurrency) as pool:
        for status, ms in pool.map(worker, range(total)):
            if status >= 400:
                errors += 1
            else:
                latencies.append(ms)
    wall = time.perf_counter() - start

    latencies.sort()
    def pct(p):
        if not latencies:
            return float("nan")
        idx = min(len(latencies) - 1, int(round(p / 100.0 * len(latencies))) - 1)
        return latencies[max(0, idx)]

    budget = BUDGETS[kind]
    p95, p99 = pct(95), pct(99)
    ok = errors == 0 and p95 <= budget["p95"] and p99 <= budget["p99"]
    print(f"\n## {name}  ({kind} budget: p95<={budget['p95']}ms p99<={budget['p99']}ms)")
    print(f"   requests={total} concurrency={concurrency} errors={errors} "
          f"throughput={total/wall:,.0f} req/s")
    if latencies:
        print(f"   p50={statistics.median(latencies):6.1f}ms  p95={p95:6.1f}ms  "
              f"p99={p99:6.1f}ms  max={latencies[-1]:6.1f}ms")
    print(f"   -> {'PASS' if ok else 'FAIL'}")
    return ok


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--base", default="http://localhost:8080")
    ap.add_argument("--concurrency", type=int, default=25)
    ap.add_argument("--requests", type=int, default=1500)
    args = ap.parse_args()
    base = args.base.rstrip("/")

    print(f"Shikhi load test → {base}  (concurrency={args.concurrency}, requests/endpoint={args.requests})")
    token = register(base)
    lesson_id = first_lesson_id(base, token)
    if not lesson_id:
        print("No lesson found in the published curriculum; aborting.")
        sys.exit(2)

    results = []
    results.append(run_scenario(
        "GET /v1/curriculum", "read",
        lambda: call("GET", f"{base}/v1/curriculum", token),
        args.concurrency, args.requests))
    results.append(run_scenario(
        "GET /v1/lessons/{id}", "read",
        lambda: call("GET", f"{base}/v1/lessons/{lesson_id}", token),
        args.concurrency, args.requests))
    results.append(run_scenario(
        "GET /v1/stats", "read",
        lambda: call("GET", f"{base}/v1/stats", token),
        args.concurrency, args.requests))
    results.append(run_scenario(
        "POST /v1/sessions (start)", "write",
        lambda: call("POST", f"{base}/v1/sessions", token, {"lessonId": lesson_id}),
        args.concurrency, max(400, args.requests // 3)))

    print(f"\n{'=' * 50}\nOVERALL: {'ALL PASS' if all(results) else 'SOME FAILED'}")
    sys.exit(0 if all(results) else 1)


if __name__ == "__main__":
    main()
