#!/bin/bash
set -euo pipefail

# Parses a macrobenchmark JSON results file and fails if a measured metric's median exceeds its
# configured budget.
#
# Why this exists as a separate script rather than an in-test assertion: androidx.benchmark's
# measureRepeated() returns Unit in this project's version (1.4.1) for both micro and macro
# benchmarks - there's no BenchmarkResult to assert against inside the test itself. Metrics only
# exist in the JSON file written after the run (build/outputs/connected_android_test_additional_
# output/.../*-benchmarkData.json), so regression-checking has to be a post-processing step.
#
# Usage:
#   scripts/check-benchmark-budget.sh <path-to-benchmarkData.json>

JSON_FILE="${1:-}"
if [ -z "$JSON_FILE" ] || [ ! -f "$JSON_FILE" ]; then
  echo "Usage: scripts/check-benchmark-budget.sh <path-to-benchmarkData.json>" >&2
  exit 1
fi

# Budget in milliseconds, keyed by "<@Test method name>:<metric name>". Deliberately generous -
# these run on whatever device/emulator is available (including CI, if ever wired up), and
# emulator timing is noisier than a physical device (see docs/improvements.md §13.11 and
# docs/MASTER_PLAN.md's note on the deferred CI wiring decision). The point is to catch a real
# regression (a multi-hundred-ms/multi-x slowdown), not to enforce a tight physical-device number.
#
# A case statement (not an associative array) on purpose: macOS ships bash 3.2 (no `declare -A`
# support), while CI runners default to bash 5.x - this has to work on both.
budget_ms_for() {
  case "$1" in
    "startup:timeToInitialDisplayMs") echo "3000" ;;
    *) echo "" ;;
  esac
}

FAILED=0

while IFS=$'\t' read -r test_name metric_name median; do
  key="${test_name}:${metric_name}"
  budget="$(budget_ms_for "$key")"
  if [ -z "$budget" ]; then
    echo "warning: no budget configured for '$key' (median ${median}ms) - skipping" >&2
    continue
  fi
  if python3 -c "import sys; sys.exit(0 if float('$median') <= float('$budget') else 1)"; then
    echo "OK   $key: ${median}ms <= ${budget}ms budget"
  else
    echo "FAIL $key: ${median}ms > ${budget}ms budget"
    FAILED=1
  fi
done < <(python3 -c "
import json

with open('$JSON_FILE') as f:
    data = json.load(f)

for benchmark in data['benchmarks']:
    for metric_name, values in benchmark['metrics'].items():
        print(f\"{benchmark['name']}\t{metric_name}\t{values['median']}\")
")

if [ "$FAILED" -eq 1 ]; then
  echo "One or more benchmarks exceeded their performance budget." >&2
  exit 1
fi

echo "All benchmarks within budget."
