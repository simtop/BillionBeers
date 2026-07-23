#!/usr/bin/env bash
#
# Coverage high-water ratchet. Fails if line coverage has dropped below the committed floor in
# config/coverage-floor.txt. Lowering the floor is a deliberate, reviewable edit to that file;
# raising it when coverage improves is encouraged (the script nudges when there's headroom).
#
# Expects jacocoRootReport to have run already (build/reports/.../jacocoRootReport.xml). `make
# coverage-check` runs the report first; the CI unit-tests job reuses the tests it already ran.
#
# Scope note: the root report currently aggregates the Android debug variants only (the JVM
# modules' test.exec is excluded by the report's Debug filter), so this floor tracks that subset.

set -euo pipefail
cd "$(dirname "$0")/.."

FLOOR_FILE="config/coverage-floor.txt"
floor=$(tr -d '[:space:]' < "$FLOOR_FILE")

xml=$(find . -name 'jacocoRootReport.xml' -path '*/build/*' 2>/dev/null | head -1)
if [[ -z "$xml" || ! -f "$xml" ]]; then
  echo "❌ No coverage report found. Run 'make jacoco-report' (or 'make coverage-check') first." >&2
  exit 1
fi

current=$(python3 - "$xml" <<'PY'
import sys, re
data = open(sys.argv[1], encoding="utf-8", errors="ignore").read()
m = re.findall(r'<counter type="LINE" missed="(\d+)" covered="(\d+)"', data)
if not m:
    print("ERR"); raise SystemExit
missed, covered = map(int, m[-1])
total = missed + covered
print(f"{100*covered/total:.1f}" if total else "0.0")
PY
)

if [[ "$current" == "ERR" ]]; then
  echo "❌ Could not read a LINE coverage counter from $xml." >&2
  exit 1
fi

awk -v c="$current" -v f="$floor" -v file="$FLOOR_FILE" 'BEGIN {
  if (c + 0 < f + 0) {
    printf "❌ Line coverage %.1f%% is below the floor %.1f%%.\n", c, f
    printf "   Add tests to recover it, or lower the floor in %s as a reviewed decision.\n", file
    exit 1
  }
  printf "✅ Line coverage %.1f%% ≥ floor %.1f%%.\n", c, f
  if (c + 0 >= f + 0 + 1.0)
    printf "   Headroom of %.1f pts - consider raising the floor in %s to lock the gain in.\n", c - f, file
}'
