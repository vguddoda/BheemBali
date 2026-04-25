"""
perf_test.py
============
Main orchestrator.

Load model
──────────
  TPS=20, TOTAL=200, CONCURRENCY=10
  → dispatches 1 request every 50 ms (drift-corrected)
  → up to 10 requests in flight at any moment
  → finishes in ~10 s wall time (200 / 20)

TPS dispatch algorithm (drift-corrected)
──────────────────────────────────────────
  interval = 1.0 / TPS
  next_dispatch = now()
  for each request:
      submit to thread pool
      next_dispatch += interval          ← fixed schedule, not "now + interval"
      sleep until next_dispatch          ← corrects for any overshoot
  wait for all futures to finish

This ensures exactly TPS requests are dispatched per second regardless of
how long each request takes (they run concurrently in the thread pool).

Usage
─────
  python3 perf_test.py                          # uses config.py defaults
  python3 perf_test.py --url https://example.com --tps 10 --total 100
  python3 perf_test.py --help
"""

import argparse
import os
import sys
import time
import threading
from concurrent.futures import ThreadPoolExecutor, as_completed
from datetime import datetime

import config
from http_timer import measure, TimingResult
from reporter import print_header, print_row, print_summary, save_results

# ── Results collection (thread-safe) ────────────────────────────────────────
_results: list[TimingResult] = []
_results_lock = threading.Lock()
_completed = 0
_completed_lock = threading.Lock()


def _worker(req_id: int, url: str, args) -> TimingResult:
    r = measure(
        url             = url,
        req_id          = req_id,
        extra_headers   = config.EXTRA_HEADERS,
        follow_redirects= config.FOLLOW_REDIRECTS,
        connect_timeout = config.CONNECT_TIMEOUT,
        max_time        = config.MAX_TIME,
    )
    global _completed
    with _completed_lock:
        _completed += 1
        completed_now = _completed

    with _results_lock:
        _results.append(r)

    if config.PRINT_LIVE_ROWS and (completed_now % config.LIVE_ROW_EVERY == 0
                                    or completed_now == args.total):
        print_row(r)

    return r


def _progress_bar(total: int, stop_event: threading.Event):
    """Prints a live progress bar on a background thread."""
    while not stop_event.is_set():
        with _completed_lock:
            done = _completed
        pct  = done / total
        bar  = int(40 * pct)
        line = f"\r  [{('#' * bar):<40}] {done}/{total} ({100*pct:.0f}%)"
        sys.stdout.write(line)
        sys.stdout.flush()
        if done >= total:
            break
        time.sleep(0.2)
    sys.stdout.write("\n")
    sys.stdout.flush()


def run(url: str, tps: int, total: int, concurrency: int):
    run_id    = datetime.now().strftime("%Y%m%d_%H%M%S")
    interval  = 1.0 / tps

    print(f"\n{'='*60}")
    print(f"  Perf Test — {run_id}")
    print(f"{'='*60}")
    print(f"  URL         : {url}")
    print(f"  TPS         : {tps}")
    print(f"  Total reqs  : {total}")
    print(f"  Concurrency : {concurrency}")
    print(f"  Duration    : ~{total/tps:.0f}s")
    print(f"{'='*60}\n")

    if config.PRINT_LIVE_ROWS:
        print(f"  (Printing every {config.LIVE_ROW_EVERY}th result live)\n")
        print_header()

    stop_event  = threading.Event()
    prog_thread = threading.Thread(target=_progress_bar, args=(total, stop_event), daemon=True)
    prog_thread.start()

    futures = []
    start_time    = time.perf_counter()
    next_dispatch = start_time

    with ThreadPoolExecutor(max_workers=concurrency) as pool:
        for req_id in range(1, total + 1):
            # Submit the request
            args_obj = argparse.Namespace(total=total)
            future = pool.submit(_worker, req_id, url, args_obj)
            futures.append(future)

            # ── Drift-corrected pacing ───────────────────────────────────
            next_dispatch += interval
            sleep_for = next_dispatch - time.perf_counter()
            if sleep_for > 0:
                time.sleep(sleep_for)
            # If sleep_for < 0 we're behind — skip sleep to catch up

        # Wait for all in-flight requests to complete
        for f in futures:
            f.result()

    elapsed = time.perf_counter() - start_time
    stop_event.set()
    prog_thread.join(timeout=1)

    # ── Sort results by req_id for clean output ──────────────────────────
    _results.sort(key=lambda r: r.req_id)

    print_summary(_results, run_id, elapsed)

    results_dir = os.path.join(os.path.dirname(__file__), config.RESULTS_DIR)
    save_results(_results, run_id, results_dir,
                 save_csv=config.SAVE_CSV, save_json=config.SAVE_JSON)


def _parse_args():
    p = argparse.ArgumentParser(
        description="HTTP Performance Tester — measures TCP connect + server processing time"
    )
    p.add_argument("--url",         default=config.TARGET_URL,    help="Target URL")
    p.add_argument("--tps",         default=config.TPS,           type=int, help="Requests/sec to dispatch")
    p.add_argument("--total",       default=config.TOTAL_REQUESTS,type=int, help="Total requests")
    p.add_argument("--concurrency", default=config.CONCURRENCY,   type=int, help="Max in-flight requests")
    return p.parse_args()


if __name__ == "__main__":
    args = parse_args() if False else _parse_args()
    run(
        url         = args.url,
        tps         = args.tps,
        total       = args.total,
        concurrency = args.concurrency,
    )

