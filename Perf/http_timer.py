"""
http_timer.py
=============
Fires a single HTTP request via curl and returns a structured timing breakdown.

Why curl (not requests/httpx)?
  curl's --write-out gives us OS-level TCP timing variables that Python's
  high-level HTTP libs do not expose:
    time_connect      → wall-clock when TCP handshake completed
    time_pretransfer  → wall-clock when curl was ready to transfer (TLS done)
    time_starttransfer→ wall-clock of first response byte (TTFB)

Timing fields returned (all in milliseconds):
  dns_ms          → DNS resolution
  tcp_connect_ms  → TCP 3-way handshake         ← "client→server" connection cost
  tls_ms          → TLS negotiation (0 for HTTP)
  server_ms       → Server processing time      ← time_starttransfer - time_pretransfer
                     This is pure server-side work; not affected by network RTT.
  transfer_ms     → Download body
  total_ms        → End-to-end wall time
  http_code       → HTTP status code
  bytes           → Response size in bytes
  error           → Non-empty string if curl failed
"""

import json
import subprocess
import shlex
from dataclasses import dataclass, field

# curl --write-out format — values land on stdout after the body (separated by \n\n---TIMING---\n)
_CURL_FORMAT = json.dumps({
    "time_namelookup":   "%{time_namelookup}",
    "time_connect":      "%{time_connect}",
    "time_appconnect":   "%{time_appconnect}",
    "time_pretransfer":  "%{time_pretransfer}",
    "time_starttransfer":"%{time_starttransfer}",
    "time_total":        "%{time_total}",
    "http_code":         "%{http_code}",
    "size_download":     "%{size_download}",
}, separators=(",", ":"))

_TIMING_SENTINEL = "\n---TIMING---\n"


@dataclass
class TimingResult:
    req_id:          int   = 0
    http_code:       int   = 0
    dns_ms:          float = 0.0
    tcp_connect_ms:  float = 0.0   # TCP handshake duration
    tls_ms:          float = 0.0   # TLS handshake duration (0 for plain HTTP)
    server_ms:       float = 0.0   # Server processing (pretransfer → first byte)
    transfer_ms:     float = 0.0   # Body download duration
    total_ms:        float = 0.0   # Full wall-clock
    bytes:           int   = 0
    success:         bool  = True
    error:           str   = ""

    def to_dict(self) -> dict:
        return self.__dict__


def measure(
    url: str,
    req_id: int = 0,
    extra_headers: list[str] | None = None,
    follow_redirects: bool = True,
    connect_timeout: int = 5,
    max_time: int = 10,
) -> TimingResult:
    """
    Run a single GET request and return a TimingResult with per-phase timings.

    Connection-time breakdown (what we care about in perf testing):
      tcp_connect_ms  → how long the TCP handshake took          (network + server accept latency)
      server_ms       → how long the server took to produce bytes (pure server processing)

    We intentionally do NOT report raw RTT because in a perf test the
    client→server network path varies; server_ms is the stable signal.
    """
    cmd = [
        "curl", "-s", "-o", "/dev/null",
        "--write-out", _TIMING_SENTINEL + _CURL_FORMAT,
        f"--connect-timeout", str(connect_timeout),
        f"--max-time",        str(max_time),
    ]
    if follow_redirects:
        cmd.append("-L")
    for h in (extra_headers or []):
        cmd += ["-H", h]
    cmd.append(url)

    try:
        out = subprocess.run(
            cmd,
            capture_output=True,
            text=True,
            timeout=max_time + 2,
        )
        raw = out.stdout
        if _TIMING_SENTINEL not in raw:
            return TimingResult(req_id=req_id, success=False,
                                error=f"curl stderr: {out.stderr.strip()[:200]}")

        timing_json = raw.split(_TIMING_SENTINEL, 1)[1].strip()
        t = json.loads(timing_json)

        nl   = float(t["time_namelookup"])
        tc   = float(t["time_connect"])
        ta   = float(t["time_appconnect"])    # 0.0 for plain HTTP
        tpre = float(t["time_pretransfer"])
        tst  = float(t["time_starttransfer"])
        tot  = float(t["time_total"])

        # ── Phase durations ─────────────────────────────────────────────
        dns_ms        = nl * 1000
        tcp_ms        = (tc - nl)  * 1000                  # pure TCP handshake
        tls_ms        = (ta - tc)  * 1000 if ta > tc else 0.0  # TLS (0 for HTTP)
        server_ms     = (tst - tpre) * 1000                # server processing
        transfer_ms   = (tot - tst) * 1000                 # body download
        total_ms      = tot * 1000

        return TimingResult(
            req_id         = req_id,
            http_code      = int(t["http_code"]),
            dns_ms         = round(dns_ms,    2),
            tcp_connect_ms = round(tcp_ms,    2),
            tls_ms         = round(tls_ms,    2),
            server_ms      = round(server_ms, 2),
            transfer_ms    = round(transfer_ms, 2),
            total_ms       = round(total_ms,  2),
            bytes          = int(float(t["size_download"])),
            success        = (out.returncode == 0),
            error          = out.stderr.strip()[:200] if out.returncode != 0 else "",
        )

    except subprocess.TimeoutExpired:
        return TimingResult(req_id=req_id, success=False, error="timeout")
    except Exception as exc:
        return TimingResult(req_id=req_id, success=False, error=str(exc))

