#!/usr/bin/env bash
# run.sh — convenience wrapper
# Usage:
#   ./run.sh                                      # defaults from config.py
#   ./run.sh --tps 10 --total 50                  # override TPS and count
#   ./run.sh --url https://jsonplaceholder.typicode.com/posts/1

set -e
cd "$(dirname "$0")"

echo "Starting perf test..."
python3 perf_test.py "$@"

