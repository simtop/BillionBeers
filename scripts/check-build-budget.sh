#!/usr/bin/env bash
#
# Build-time budget check — the developer-perf counterpart to check-benchmark-budget.sh.
#
# Reads a gradle-profiler benchmark.csv and fails if a scenario's median exceeds its budget in
# config/build-time-budget.txt. Raising a budget is a deliberate, reviewable edit to that file,
# exactly like lowering the coverage floor.
#
# Why this is not a CI lane (see docs/adr/0011): a build-time number measured on a shared CI runner
# is dominated by which runner the job drew, not by the build. ADR 0009 measured that effect on this
# repo's own lanes - every lane correlates r = 0.93-0.99 with Detekt, which no test change can
# affect. So this runs locally and deliberately, the same way `make benchmark-check` does.
#
# Usage:
#   scripts/check-build-budget.sh [path-to-benchmark.csv]   (default: profile-out/baseline/benchmark.csv)

set -euo pipefail
cd "$(dirname "$0")/.."

CSV="${1:-profile-out/baseline/benchmark.csv}"
BUDGET_FILE="config/build-time-budget.txt"

if [[ ! -f "$CSV" ]]; then
  echo "❌ No benchmark results at $CSV. Run 'make build-budget' to measure first." >&2
  exit 1
fi
if [[ ! -f "$BUDGET_FILE" ]]; then
  echo "❌ Missing $BUDGET_FILE." >&2
  exit 1
fi

python3 - "$CSV" "$BUDGET_FILE" <<'PY'
import csv, statistics, sys

csv_path, budget_path = sys.argv[1], sys.argv[2]

with open(csv_path, newline="", encoding="utf-8") as f:
    rows = list(csv.reader(f))

if not rows or rows[0][0] != "scenario":
    sys.exit(f"❌ {csv_path} is not a gradle-profiler benchmark.csv (no 'scenario' header row).")

# Header carries scenario ids, because benchmark.scenarios deliberately sets no `title`.
scenarios = [name for name in rows[0][1:] if name]

# Only measured builds count. Warm-ups are excluded by gradle-profiler's own labelling, and
# including them is the classic way to bake daemon JIT warm-up into a "build time".
samples = {name: [] for name in scenarios}
for row in rows:
    if not row or not row[0].startswith("measured build"):
        continue
    for i, name in enumerate(scenarios, start=1):
        if i < len(row) and row[i].strip():
            samples[name].append(float(row[i]) / 1000.0)

absolute, ratios = {}, []
with open(budget_path, encoding="utf-8") as f:
    for lineno, raw in enumerate(f, 1):
        line = raw.split("#", 1)[0].strip()
        if not line:
            continue
        parts = line.split()
        if parts[0] == "ratio":
            if len(parts) != 4:
                sys.exit(f"❌ {budget_path}:{lineno}: expected 'ratio <slow> <fast> <max>'.")
            ratios.append((parts[1], parts[2], float(parts[3])))
        elif len(parts) == 2:
            absolute[parts[0]] = float(parts[1])
        else:
            sys.exit(f"❌ {budget_path}:{lineno}: expected '<scenario> <seconds>'.")

failed = False
measured = {}

for name in scenarios:
    values = samples[name]
    if not values:
        print(f"warning: scenario '{name}' produced no measured builds - skipping", file=sys.stderr)
        continue
    median = statistics.median(values)
    measured[name] = median
    spread = (max(values) - min(values)) / median * 100 if median else 0.0

    if name not in absolute:
        print(f"warning: no budget configured for '{name}' (median {median:.1f}s) - skipping", file=sys.stderr)
        continue

    budget = absolute[name]
    if median > budget:
        print(f"FAIL {name}: median {median:.1f}s > {budget:.0f}s budget  (n={len(values)}, spread {spread:.0f}%)")
        failed = True
    else:
        # Deliberately no "you have headroom, tighten this" nudge, unlike coverage-check.sh.
        # Coverage should ratchet up; a build-time budget should not ratchet down. These scenarios
        # measured 32-207% spread (ADR 0011) and the numbers are machine-specific, so headroom is
        # the policy rather than slack to be reclaimed. Nudging here would argue against the
        # reasoning written into config/build-time-budget.txt.
        print(f"OK   {name}: median {median:.1f}s <= {budget:.0f}s budget  (n={len(values)}, spread {spread:.0f}%)")

# The scale-invariant half. Absolute seconds are a property of the machine that measured them;
# a ratio between two scenarios on the same machine in the same run is not, which is why the
# modularization payoff is expressed this way.
for slow, fast, limit in ratios:
    if slow not in measured or fast not in measured:
        print(f"warning: ratio {slow}/{fast} needs both scenarios in this run - skipping", file=sys.stderr)
        continue
    if measured[fast] == 0:
        print(f"warning: ratio {slow}/{fast} has a zero denominator - skipping", file=sys.stderr)
        continue
    actual = measured[slow] / measured[fast]
    if actual > limit:
        print(f"FAIL {slow}/{fast}: {actual:.2f}x > {limit:.2f}x  "
              f"({measured[slow]:.1f}s vs {measured[fast]:.1f}s)")
        failed = True
    else:
        print(f"OK   {slow}/{fast}: {actual:.2f}x <= {limit:.2f}x  "
              f"({measured[slow]:.1f}s vs {measured[fast]:.1f}s)")

if failed:
    print(f"\nOne or more build-time budgets were exceeded. Fix the regression, or raise the "
          f"budget in {budget_path} as a reviewed decision.", file=sys.stderr)
    sys.exit(1)

print("\nAll build-time budgets met.")
PY
