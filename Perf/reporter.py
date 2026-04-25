"""
reporter.py
===========
Handles all output:
  - Live per-request rows (every Nth request)
  - Final summary table with percentiles
  - CSV + JSON dump to results/
"""

import csv
import json
import os
import statistics
from datetime import datetime
from typing import List

from http_timer import TimingResult


# ── ANSI helpers ────────────────────────────────────────────────────────────
_GREEN  = "\033[92m"
_RED    = "\033[91m"
_YELLOW = "\033[93m"
_CYAN   = "\033[96m"
_BOLD   = "\033[1m"
_RESET  = "\033[0m"

_HEADER = (
    f"{'Req':>5}  {'Code':>4}  "
    f"{'DNS':>7}  {'TCP':>7}  {'TLS':>7}  "
    f"{'Server':>8}  {'Transfer':>9}  {'Total':>8}  "
    f"{'Bytes':>7}  Status"
)
_SEP = "-" * len(_HEADER)


def _color_ms(val: float, warn: float = 200, crit: float = 500) -> str:
    s = f"{val:>8.1f}ms"
    if val >= crit:
        return _RED + s + _RESET
    if val >= warn:
        return _YELLOW + s + _RESET
    return s


def print_header():
    print()
    print(_BOLD + _HEADER + _RESET)
    print(_SEP)


def print_row(r: TimingResult):
    status = (_GREEN + "OK  " + _RESET) if r.success else (_RED + "FAIL" + _RESET)
    print(
        f"{r.req_id:>5}  {r.http_code:>4}  "
        f"{_color_ms(r.dns_ms, 50, 200)}  "
        f"{_color_ms(r.tcp_connect_ms, 100, 300)}  "
        f"{_color_ms(r.tls_ms, 100, 300)}  "
        f"{_color_ms(r.server_ms, 200, 500):>8}  "
        f"{_color_ms(r.transfer_ms, 100, 300):>9}  "
        f"{_color_ms(r.total_ms, 500, 1000):>8}  "
        f"{r.bytes:>7}  {status}"
        + (f"  ← {r.error}" if r.error else "")
    )


def _pct(data: list[float], p: int) -> float:
    if not data:
        return 0.0
    data_sorted = sorted(data)
    idx = max(0, int(len(data_sorted) * p / 100) - 1)
    return round(data_sorted[idx], 2)


def print_summary(results: List[TimingResult], run_id: str, elapsed_s: float):
    ok      = [r for r in results if r.success]
    failed  = [r for r in results if not r.success]
    total   = len(results)

    fields = {
        "TCP Connect":      [r.tcp_connect_ms for r in ok],
        "TLS":              [r.tls_ms         for r in ok],
        "Server Processing":[r.server_ms      for r in ok],
        "Transfer":         [r.transfer_ms    for r in ok],
        "Total":            [r.total_ms       for r in ok],
    }

    print()
    print(_BOLD + "=" * 70 + _RESET)
    print(_BOLD + _CYAN + f"  PERF RESULTS  — run {run_id}" + _RESET)
    print(_BOLD + "=" * 70 + _RESET)
    print(f"  Total requests : {total}")
    print(f"  Successful     : {len(ok)}  ({100*len(ok)//total}%)")
    print(f"  Failed         : {len(failed)}")
    print(f"  Elapsed        : {elapsed_s:.2f}s")
    print(f"  Actual TPS     : {total / elapsed_s:.1f}")
    print()

    # Timing breakdown table
    col_w = 10
    labels = ["p50", "p75", "p90", "p95", "p99", "max", "mean"]
    header = f"  {'Metric':<22}" + "".join(f"{l:>{col_w}}" for l in labels)
    print(_BOLD + header + _RESET)
    print("  " + "-" * (22 + col_w * len(labels)))

    for name, vals in fields.items():
        if not vals:
            continue
        row = (
            f"  {name:<22}"
            f"{_pct(vals,50):>{col_w}.1f}"
            f"{_pct(vals,75):>{col_w}.1f}"
            f"{_pct(vals,90):>{col_w}.1f}"
            f"{_pct(vals,95):>{col_w}.1f}"
            f"{_pct(vals,99):>{col_w}.1f}"
            f"{max(vals):>{col_w}.1f}"
            f"{statistics.mean(vals):>{col_w}.1f}"
        )
        print(row)

    # Server processing note (the key metric we care about)
    if fields["Server Processing"]:
        sp = fields["Server Processing"]
        print()
        print(_BOLD + "  ★ Server Processing Time (p50 / p95 / p99):" + _RESET +
              f"  {_pct(sp,50):.1f}ms / {_pct(sp,95):.1f}ms / {_pct(sp,99):.1f}ms")
        print("    (pure server-side work; DNS+TCP excluded — not server's fault)")

    if failed:
        print()
        print(_RED + f"  ✗ Failures ({len(failed)}):" + _RESET)
        for r in failed[:5]:
            print(f"    req#{r.req_id}: {r.error}")
        if len(failed) > 5:
            print(f"    ... and {len(failed)-5} more")

    print(_BOLD + "=" * 70 + _RESET)
    print()


def save_results(results: List[TimingResult], run_id: str, results_dir: str,
                 save_csv: bool = True, save_json: bool = True):
    os.makedirs(results_dir, exist_ok=True)

    if save_csv:
        path = os.path.join(results_dir, f"{run_id}.csv")
        fieldnames = list(TimingResult().to_dict().keys())
        with open(path, "w", newline="") as f:
            w = csv.DictWriter(f, fieldnames=fieldnames)
            w.writeheader()
            w.writerows(r.to_dict() for r in results)
        print(f"  CSV  → {path}")

    if save_json:
        path = os.path.join(results_dir, f"{run_id}.json")
        with open(path, "w") as f:
            json.dump([r.to_dict() for r in results], f, indent=2)
        print(f"  JSON → {path}")

