#!/usr/bin/env bash
#
# Aggregates BillionBeers' read-only health checks into one markdown table:
# "what's wrong and what needs solving." Powers the weekly health job
# (.github/workflows/health-report.yml) and `make health`.
#
# Usage:
#   scripts/health-report.sh [--run] [OUTPUT.md]
#     --run       first execute the Gradle checks that produce the artifacts this script
#                 parses (slow, full build); omit to render from whatever artifacts already
#                 exist (fast local preview).
#     OUTPUT.md   write the report here (default: stdout).
#
# Design: each check is best-effort. A missing artifact yields an "n/a" row with how to
# produce it, never a hard failure - the report must always render, even when a check can't.

set -uo pipefail
cd "$(dirname "$0")/.."

bt='`' # literal backtick for markdown inline code, built as a var so it never triggers
       # command substitution inside the double-quoted row strings below.

RUN=0
OUT=""
for arg in "$@"; do
  case "$arg" in
    --run) RUN=1 ;;
    *) OUT="$arg" ;;
  esac
done

if [[ "$RUN" == "1" ]]; then
  echo "Running health checks (this builds the project)..." >&2
  # --continue: one check failing must not stop the others from producing their artifacts;
  # failures surface as report rows, not by aborting. Only tasks whose *artifacts* this script
  # parses are run here - the Lint and dependency-guard rows read committed baseline files, so
  # those tasks are deliberately not invoked.
  # jacocoRootReport is intentionally absent: it currently fails at configuration
  # (:app:jacocoBenchmarkReleaseReport wants a non-existent testBenchmarkReleaseUnitTest), which
  # the coverage row reports as a finding. Re-add it here once that task is repaired.
  ./gradlew --console=plain --continue -PcomposeCompilerReports=true \
    detekt compileReleaseKotlin \
    >&2 2>&1 || true
fi

ROWS=()
row() { ROWS+=("| $1 | $2 | $3 | $4 |"); }

# --- 1. Android Lint backlog (parse the committed baseline; no build needed) ----------------
lint_row() {
  local f="app/lint-baseline.xml"
  if [[ -f "$f" ]]; then
    local n top
    n=$(grep -c '<issue' "$f" 2>/dev/null || echo 0)
    top=$(grep -oE 'id="[^"]+"' "$f" | sed 's/id="//;s/"//' | sort | uniq -c | sort -rn | head -3 \
          | awk '{printf "%s×%s ", $2, $1}')
    if [[ "$n" -eq 0 ]]; then
      row "Android Lint backlog" "0" "🟢 clean" "—"
    else
      row "Android Lint backlog" "$n baselined" "🟡 burn down" "top: ${top:-—}· ${bt}make android-lint${bt}, fix, re-baseline"
    fi
  else
    row "Android Lint backlog" "n/a" "⚪" "no baseline (${bt}make android-lint${bt})"
  fi
}

# --- 2. Detekt (current, non-baselined findings) ------------------------------------------
detekt_row() {
  if find . -name 'detekt.xml' -path '*/build/reports/detekt/*' 2>/dev/null | grep -q .; then
    local n
    n=$(find . -name 'detekt.xml' -path '*/build/reports/detekt/*' -exec grep -oh '<error ' {} + 2>/dev/null | wc -l | tr -d ' ')
    if [[ "$n" -eq 0 ]]; then
      row "Detekt findings" "0" "🟢 clean" "—"
    else
      row "Detekt findings" "$n" "🟡 review" "${bt}make lint${bt}; baseline or fix"
    fi
  else
    row "Detekt findings" "n/a" "⚪" "run with --run (or ${bt}make lint${bt})"
  fi
}

# --- 3. Compose unstable params ------------------------------------------------------------
compose_row() {
  if find . -name '*composables.txt' -path '*/compose_compiler/*' 2>/dev/null | grep -q .; then
    local n
    n=$(find . -name '*composables.txt' -path '*/compose_compiler/*' -exec grep -oh 'unstable ' {} + 2>/dev/null | wc -l | tr -d ' ')
    if [[ "$n" -le 1 ]]; then
      row "Compose unstable params" "$n" "🟢 good" "1 = the framework Uri?, expected"
    else
      row "Compose unstable params" "$n" "🟡 review" "stabilise immutable types in compose-stability.conf"
    fi
  else
    row "Compose unstable params" "n/a" "⚪" "${bt}make compose-metrics${bt}"
  fi
}

# --- 4. Line coverage (jacoco root report) -------------------------------------------------
coverage_row() {
  local xml
  xml=$(find . -name 'jacocoRootReport.xml' -path '*/build/*' 2>/dev/null | head -1)
  if [[ -n "$xml" && -f "$xml" ]]; then
    local pct
    pct=$(python3 - "$xml" <<'PY'
import sys, re
data = open(sys.argv[1], encoding="utf-8", errors="ignore").read()
m = re.findall(r'<counter type="LINE" missed="(\d+)" covered="(\d+)"', data)
if m:
    missed, covered = map(int, m[-1])
    total = missed + covered
    print(f"{100*covered/total:.1f}" if total else "0.0")
else:
    print("?")
PY
)
    row "Line coverage" "${pct}%" "ℹ️" "raise covered paths; see jacocoRootReport"
  else
    row "Line coverage" "n/a" "🔴 broken" "${bt}jacocoRootReport${bt} fails: benchmark variant wants a non-existent testBenchmarkReleaseUnitTest — repair to restore coverage"
  fi
}

# --- 5. Dependency graph lock --------------------------------------------------------------
depguard_row() {
  local f="app/dependencies/releaseRuntimeClasspath.txt"
  if [[ -f "$f" ]]; then
    local n; n=$(grep -c . "$f")
    row "Dependency graph" "$n deps locked" "🟢 guarded" "drift fails CI; re-baseline intentionally"
  else
    row "Dependency graph" "n/a" "⚪" "${bt}make dependency-guard-baseline${bt}"
  fi
}

lint_row
detekt_row
compose_row
coverage_row
depguard_row

# --- render --------------------------------------------------------------------------------
{
  echo "# BillionBeers Health Report"
  echo
  echo "_Generated $(date -u '+%Y-%m-%d %H:%M UTC')_ · read-only checks only. Regenerate with ${bt}make health${bt}."
  echo
  echo "| Check | Metric | Status | What to do |"
  echo "|---|---|---|---|"
  printf '%s\n' "${ROWS[@]}"
  echo
  echo "> Legend: 🟢 healthy · 🟡 backlog to burn down · ℹ️ informational · ⚪ not measured this run."
} > "${OUT:-/dev/stdout}"

[[ -n "$OUT" ]] && echo "Wrote $OUT" >&2
exit 0
