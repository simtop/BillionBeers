#!/bin/bash
set -euo pipefail

# Parses a macrobenchmark JSON results file and fails if a measured metric's median exceeds its
# configured budget. The report is the only source of results because measureRepeated() returns
# Unit in androidx.benchmark 1.4.1.
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
# Calibrated on a PHYSICAL device. Reference: Pixel 8 (shiba), API 37.
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
# Emulator reports are valid evidence but never gated: an identical-code before/after comparison
# swung 160 ms on emulator versus 2.5 ms on Pixel 8. Enforcing a physical-device budget there would
# either fail constantly or stop meaning anything, as the old 3000 ms budget did.
budget_ms_for() {
  case "$1" in
    "startup:timeToInitialDisplayMs") echo "500" ;;
    *) echo "" ;;
  esac
}

# Parse and validate once. Capturing the command substitution propagates Python's failure under
# `set -e`; a process substitution would hide it and let an empty loop look like a green result.
REPORT=$(python3 - "$JSON_FILE" <<'PY'
import json
import math
import sys

path = sys.argv[1]
try:
    with open(path, encoding="utf-8") as report_file:
        data = json.load(report_file)
except (OSError, json.JSONDecodeError) as error:
    raise SystemExit(f"error: cannot read benchmark report {path!r}: {error}")

if not isinstance(data, dict):
    raise SystemExit("error: benchmark report root must be an object")
benchmarks = data.get("benchmarks")
if not isinstance(benchmarks, list) or not benchmarks:
    raise SystemExit("error: benchmark report must contain a non-empty 'benchmarks' list")

rows = []
has_required_startup_metric = False
for index, benchmark in enumerate(benchmarks):
    if not isinstance(benchmark, dict):
        raise SystemExit(f"error: benchmark entry {index} must be an object")
    name = benchmark.get("name")
    metrics = benchmark.get("metrics")
    if not isinstance(name, str) or not name.strip() or any(c in name for c in "\t\r\n"):
        raise SystemExit(f"error: benchmark entry {index} has an invalid name")
    if not isinstance(metrics, dict) or not metrics:
        raise SystemExit(f"error: benchmark {name!r} must contain a non-empty 'metrics' object")
    for metric_name, values in metrics.items():
        if (not isinstance(metric_name, str) or not metric_name.strip()
                or any(c in metric_name for c in "\t\r\n")):
            raise SystemExit(f"error: benchmark {name!r} has an invalid metric name")
        if not isinstance(values, dict) or "median" not in values:
            raise SystemExit(f"error: metric {name}:{metric_name} has no median")
        median = values["median"]
        if isinstance(median, bool) or not isinstance(median, (int, float)):
            raise SystemExit(f"error: metric {name}:{metric_name} median must be finite and non-negative")
        try:
            median = float(median)
        except (OverflowError, ValueError):
            raise SystemExit(f"error: metric {name}:{metric_name} median must be finite and non-negative")
        if not math.isfinite(median) or median < 0:
            raise SystemExit(f"error: metric {name}:{metric_name} median must be finite and non-negative")
        if name == "startup" and metric_name == "timeToInitialDisplayMs":
            has_required_startup_metric = True
        rows.append((name, metric_name, str(median)))

if not has_required_startup_metric:
    raise SystemExit("error: benchmark report is missing required metric startup:timeToInitialDisplayMs")

context = data.get("context")
if not isinstance(context, dict) or not isinstance(context.get("build"), dict):
    raise SystemExit("error: benchmark report has no valid device context/build object")
build = context["build"]
version = build.get("version", {})
if not isinstance(version, dict):
    raise SystemExit("error: benchmark report device build version must be an object")
parts = [build.get("fingerprint", ""), build.get("model", ""), build.get("device", "")]
if not all(isinstance(part, (str, int, float)) for part in parts):
    raise SystemExit("error: benchmark report device identifiers must be scalar values")
blob = " ".join(str(part) for part in parts).lower()
is_emulator = any(marker in blob for marker in ("sdk_gphone", "generic", "emulator", "goldfish", "ranchu"))
model = str(build.get("model", "?"))
sdk = str(version.get("sdk", "?"))
cpu_locked = str(context.get("cpuLocked", "?"))
if any(c in value for value in (model, sdk, cpu_locked) for c in "\t\r\n"):
    raise SystemExit("error: benchmark report device label contains control characters")

print(f"DEVICE\t{model} (API {sdk}), cpuLocked={cpu_locked}")
print(f"EMULATOR\t{'yes' if is_emulator else 'no'}")
for name, metric_name, median in rows:
    print(f"METRIC\t{name}\t{metric_name}\t{median}")
PY
)

IS_EMULATOR=""
DEVICE_LABEL=""
FAILED=0
while IFS=$'\t' read -r kind first second third; do
  case "$kind" in
    DEVICE) DEVICE_LABEL="$first" ;;
    EMULATOR) IS_EMULATOR="$first" ;;
    METRIC)
      key="${first}:${second}"
      budget="$(budget_ms_for "$key")"
      if [ -z "$budget" ]; then
        echo "warning: no budget configured for '$key' (median ${third}ms) - skipping" >&2
        continue
      fi
      if python3 - "$third" "$budget" <<'PY'
import sys
sys.exit(0 if float(sys.argv[1]) <= float(sys.argv[2]) else 1)
PY
      then
        echo "OK   $key: ${third}ms <= ${budget}ms budget"
      elif [ "$IS_EMULATOR" = "yes" ]; then
        echo "SKIP $key: ${third}ms > ${budget}ms budget - reported, not gated (emulator)"
      else
        echo "FAIL $key: ${third}ms > ${budget}ms budget"
        FAILED=1
      fi
      ;;
  esac
done <<< "$REPORT"

if [ -z "$DEVICE_LABEL" ] || [ -z "$IS_EMULATOR" ]; then
  echo "error: validated benchmark report did not provide device evidence" >&2
  exit 1
fi
echo "Device: $DEVICE_LABEL"

if [ "$FAILED" -eq 1 ]; then
  echo "One or more benchmarks exceeded their performance budget." >&2
  exit 1
fi

if [ "$IS_EMULATOR" = "yes" ]; then
  echo "All benchmarks reported (emulator - budgets are not enforced here)."
else
  echo "All benchmarks within budget."
fi
