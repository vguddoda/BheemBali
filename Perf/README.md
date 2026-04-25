# Perf — HTTP Performance Testing Framework

Measures **per-phase HTTP timing** for every request so you can distinguish
network cost from server cost.

---

## Why curl-based timing?

Python's `requests` / `httpx` only give total elapsed time.  
`curl --write-out` exposes OS-level TCP timestamps that reveal:

| Phase | What it measures | Who to blame |
|-------|-----------------|-------------|
| `dns_ms` | DNS resolution | DNS infra |
| `tcp_connect_ms` | TCP 3-way handshake | Network + server accept |
| `tls_ms` | TLS negotiation | Server + client TLS config |
| **`server_ms`** | **Pretransfer → first byte (TTFB)** | **Pure server processing** |
| `transfer_ms` | Body download | Network bandwidth |
| `total_ms` | End-to-end wall time | Everything |

> **`server_ms` is the metric we optimize.** It's the only number unaffected
> by client↔server network distance — stable across test runs.

---

## Quick Start

```bash
cd Perf

# Run with defaults (200 reqs @ 20 TPS, concurrency=10, target=httpbin.org)
./run.sh

# Override anything
python3 perf_test.py --url https://jsonplaceholder.typicode.com/posts/1 \
                     --tps 10 --total 100 --concurrency 5

# Help
python3 perf_test.py --help
```

---

## Configuration (`config.py`)

All defaults live in `config.py`. Edit once, run without flags.

```python
TARGET_URL      = "https://httpbin.org/get"
TPS             = 20      # requests dispatched per second
TOTAL_REQUESTS  = 200     # total requests in the run
CONCURRENCY     = 10      # max in-flight at any moment
CONNECT_TIMEOUT = 5       # curl connect timeout (s)
MAX_TIME        = 10      # curl max total time (s)
SAVE_CSV        = True    # write results/<run_id>.csv
SAVE_JSON       = True    # write results/<run_id>.json
LIVE_ROW_EVERY  = 10      # print every Nth result live
```

---

## TPS Dispatch Algorithm

```
interval = 1.0 / TPS          # 50ms for TPS=20
next_dispatch = now()

for each request:
    submit to thread pool      # non-blocking
    next_dispatch += interval  # advance fixed clock
    sleep until next_dispatch  # drift-corrected sleep
```

- **Fixed-clock dispatch** — `next_dispatch += interval` (not `now() + interval`)
  means any dispatch that runs late is automatically corrected on the next tick.
- **Thread pool** handles concurrency — slow responses don't block the dispatcher.
- **200 requests ÷ 20 TPS = exactly 10 s** of dispatching, same every run.

---

## Output

### Live rows (every 10th request)
```
 Req  Code     DNS      TCP      TLS    Server  Transfer    Total    Bytes  Status
  10   200    3.0ms  285.8ms  703.5ms  287.7ms     0.3ms  1280.6ms    255  OK
```

### Final summary (percentiles)
```
  Metric              p50    p75    p90    p95    p99    max   mean
  TCP Connect       279.5  284.8  330.7  330.7  330.7  343.6  288.2
  TLS               625.0  648.2  703.5  703.5  703.5  718.0  630.4
  Server Processing 292.3  326.9  509.5  509.5  509.5 1077.8  398.9
  Transfer            0.1    0.2    0.2    0.2    0.2    0.3    0.2
  Total            1261.2 1280.6 1350.6 1350.6 1350.6 1978.7 1323.2

  ★ Server Processing Time (p50 / p95 / p99):  292.3ms / 509.5ms / 509.5ms
```

### Files saved
```
results/
  20260425_160616.csv    ← one row per request, all timing columns
  20260425_160616.json   ← same data as JSON array
```

---

## File Layout

```
Perf/
  config.py        ← all tunables
  perf_test.py     ← main orchestrator (TPS pacing + concurrency)
  http_timer.py    ← curl wrapper, returns TimingResult per request
  reporter.py      ← live rows, percentile table, CSV/JSON export
  run.sh           ← convenience shell wrapper
  results/         ← created automatically, one file per run
```

---

## Interview Angle

This setup answers "how do you isolate server latency from network latency in a perf test?":
- **DNS + TCP** are network/infra costs — variable, not your app's fault
- **TLS** is config cost — optimize once
- **Server processing** (`time_starttransfer - time_pretransfer`) is what your app code controls
- Consistent hashing (like in `ratelimit/`) keeps same-tenant traffic on the same pod,
  making server_ms reproducible across runs

