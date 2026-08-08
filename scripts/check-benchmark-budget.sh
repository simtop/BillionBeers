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

# Budget in milliseconds, keyed by "<@Test method name>:<metric name>".
#
# Calibrated on a PHYSICAL device and enforced only there. Reference: Pixel 8 (shiba), API 37.
#
# Observed run medians across five separate `make benchmark-check` invocations (5 cold starts each):
# 202, 208, 210, 211, 282 ms. The 282 ms run is the important one - an initial sample of ten cold
# starts suggested a max of 238 ms, and a later run beat that by 44 ms with no code change. Real
# devices drift with thermal state and background load in a way a short sample does not show, so the
# budget is set against the worst observed median rather than the tidy one.
#
# 500 ms is ~2.4x the typical median and ~1.8x that worst run: it fails on a doubling of startup
# while surviving a warm phone. A tighter number (400 ms was tried) sits only 1.4x above a run that
# has already happened, and a gate that cries wolf gets deleted - which is the failure mode this is
# trying to avoid, not a hypothetical.
#
# It used to be 3000 ms, set from emulator numbers (median ~1078 ms, and 160 ms swings between two
# runs of identical code). Against a real 209 ms that gate could never have fired - it was 14x the
# value it was guarding.
#
# Like config/build-time-budget.txt, this is a property of the reference hardware. A slower physical
# device can exceed it with nothing wrong; re-calibrate rather than assume a regression.
#
# A case statement (not an associative array) on purpose: macOS ships bash 3.2 (no `declare -A`
# support), while CI runners default to bash 5.x - this has to work on both.
budget_ms_for() {
  case "$1" in
    "startup:timeToInitialDisplayMs") echo "500" ;;
    *) echo "" ;;
  esac
}

# Emulator runs are reported but never gated.
#
# This project already holds that a benchmark taken on a managed virtual device is meaningless -
# it is why :benchmark:microbenchmark is deliberately outside the ATD lane (AGENTS.md §5) and why
# androidx.benchmark.suppressErrors lists EMULATOR. Measured here: the same before/after comparison
# swung 160 ms between two identical-code runs on an emulator versus 2.5 ms on a Pixel 8. Enforcing
# a physical-device budget there would either fail constantly or, if loosened to fit, stop meaning
# anything - which is exactly how the old 3000 ms number came about.
IS_EMULATOR=$(python3 -c "
import json
c = json.load(open('$JSON_FILE')).get('context', {}).get('build', {})
blob = (c.get('fingerprint','') + ' ' + c.get('model','') + ' ' + c.get('device','')).lower()
print('yes' if any(m in blob for m in ('sdk_gphone', 'generic', 'emulator', 'goldfish', 'ranchu')) else 'no')
")

DEVICE_LABEL=$(python3 -c "
import json
c = json.load(open('$JSON_FILE')).get('context', {})
b = c.get('build', {})
print(f\"{b.get('model','?')} (API {b.get('version',{}).get('sdk','?')}), cpuLocked={c.get('cpuLocked')}\")
")

echo "Device: $DEVICE_LABEL"

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
  elif [ "$IS_EMULATOR" = "yes" ]; then
    echo "SKIP $key: ${median}ms > ${budget}ms budget - reported, not gated (emulator)"
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

if [ "$IS_EMULATOR" = "yes" ]; then
  echo "All benchmarks reported (emulator - budgets are not enforced here)."
else
  echo "All benchmarks within budget."
fi
