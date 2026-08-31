#!/usr/bin/env bash
#
# Aggregates BillionBeers' read-only health checks into Markdown and JSON reports:
# "what's wrong and what needs solving." Powers the weekly health job
# (.github/workflows/health-report.yml) and `make health`.
#
# Usage:
#   scripts/health-report.sh [--run] [--json OUTPUT.json]
#                            [--previous-json PREVIOUS.json] [OUTPUT.md]
#
#     --run                    first execute the Gradle checks that produce the artifacts this
#                              script parses (slow, full build); omit to render from existing
#                              artifacts (fast local preview).
#     --json OUTPUT.json       also write the versioned machine-readable report.
#     --previous-json FILE     compare numeric metrics with a previous health JSON report.
#     OUTPUT.md                write Markdown here (default: stdout).
#
# Design: each check is best-effort. A missing artifact yields an "n/a" row with how to
# produce it, never a hard failure - the report must always render, even when a check can't.

set -uo pipefail
cd "$(dirname "$0")/.."

bt='`' # literal backtick for markdown inline code, built as a var so it never triggers
       # command substitution inside the double-quoted metric strings below.

RUN=0
OUT=""
JSON_OUT=""
PREVIOUS_JSON=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --run)
      RUN=1
      shift
      ;;
    --json)
      [[ $# -ge 2 ]] || { echo "error: --json needs a path" >&2; exit 2; }
      JSON_OUT="$2"
      shift 2
      ;;
    --previous-json)
      [[ $# -ge 2 ]] || { echo "error: --previous-json needs a path" >&2; exit 2; }
      PREVIOUS_JSON="$2"
      shift 2
      ;;
    -h|--help)
      sed -n '7,18p' "$0"
      exit 0
      ;;
    --*)
      echo "error: unknown option: $1" >&2
      exit 2
      ;;
    *)
      [[ -z "$OUT" ]] || { echo "error: only one Markdown output path is supported" >&2; exit 2; }
      OUT="$1"
      shift
      ;;
  esac
done

DETEKT_RUN_STATUS="not-run"
COMPOSE_RUN_STATUS="not-run"
COVERAGE_RUN_STATUS="not-run"
ARCHITECTURE_RUN_STATUS="not-run"
if [[ "$RUN" == "1" ]]; then
  echo "Running health checks (this builds the project)..." >&2
  # Clear only the artifacts parsed below so a failed producer cannot make a previous run look
  # current. The checks run separately because one failure must not invalidate fresh output from
  # the others. Compose reports are not declared Gradle outputs, so their compile is rerun.
  find . -name 'detekt.xml' -path '*/build/reports/detekt/*' -delete 2>/dev/null || true
  if ./gradlew --console=plain --continue detekt >&2 2>&1; then
    DETEKT_RUN_STATUS="success"
  else
    DETEKT_RUN_STATUS="failed"
  fi

  rm -rf build/reports/jacoco/jacocoRootReport
  if ./gradlew --console=plain jacocoRootReport >&2 2>&1; then
    COVERAGE_RUN_STATUS="success"
  else
    COVERAGE_RUN_STATUS="failed"
  fi

  find . -name '*composables.txt' -path '*/compose_compiler/*' -delete 2>/dev/null || true
  if ./gradlew --console=plain --rerun-tasks -PcomposeCompilerReports=true \
    compileReleaseKotlin >&2 2>&1; then
    COMPOSE_RUN_STATUS="success"
  else
    COMPOSE_RUN_STATUS="failed"
  fi

  if ./gradlew --console=plain generateModuleGraph verifyArchitectureGraph >&2 2>&1; then
    ARCHITECTURE_RUN_STATUS="success"
  else
    ARCHITECTURE_RUN_STATUS="failed"
  fi
fi

[[ -z "$OUT" ]] || mkdir -p "$(dirname "$OUT")"
[[ -z "$JSON_OUT" ]] || mkdir -p "$(dirname "$JSON_OUT")"
if [[ -n "$OUT" && -n "$JSON_OUT" ]]; then
  OUTPUTS_MATCH=$(python3 - "$OUT" "$JSON_OUT" <<'PY'
from pathlib import Path
import sys
print("yes" if Path(sys.argv[1]).resolve() == Path(sys.argv[2]).resolve() else "no")
PY
)
  if [[ "$OUTPUTS_MATCH" == "yes" ]]; then
    echo "error: Markdown and JSON outputs must use different paths" >&2
    exit 2
  fi
fi

METRICS_FILE=$(mktemp)
trap 'rm -f "$METRICS_FILE"' EXIT

metric() {
  python3 - "$@" >> "$METRICS_FILE" <<'PY'
import json
import sys

metric_id, name, display_value, numeric_value, unit, status, status_label, action = sys.argv[1:]
value = None
if numeric_value:
    value = float(numeric_value) if "." in numeric_value else int(numeric_value)
print(json.dumps({
    "id": metric_id,
    "name": name,
    "displayValue": display_value,
    "value": value,
    "unit": unit or None,
    "status": status,
    "statusLabel": status_label,
    "action": action,
}, ensure_ascii=False))
PY
}

# --- 1. Android Lint backlog (parse the committed baseline; no build needed) ----------------
lint_metric() {
  local file="app/lint-baseline.xml"
  if [[ -f "$file" ]]; then
    local count top
    count=$(grep -c '<issue[[:space:]>]' "$file" 2>/dev/null || true)
    count=${count:-0}
    top=$(grep -oE 'id="[^"]+"' "$file" 2>/dev/null | sed 's/id="//;s/"//' | sort | uniq -c \
      | sort -rn | head -3 | awk '{printf "%s×%s ", $2, $1}')
    if [[ "$count" -eq 0 ]]; then
      metric "android_lint_baseline_entries" "Android Lint baseline debt" "0" "$count" "entries" \
        "healthy" "🟢 clean" "—"
    else
      metric "android_lint_baseline_entries" "Android Lint baseline debt" "$count baselined" "$count" "entries" \
        "warning" "🟡 burn down" "top: ${top:-—}· ${bt}make android-lint${bt}, fix, re-baseline"
    fi
  else
    metric "android_lint_baseline_entries" "Android Lint baseline debt" "n/a" "" "entries" \
      "unavailable" "⚪ not measured" "no baseline (${bt}make android-lint${bt})"
  fi
}

# --- 2. Detekt current findings and committed baseline debt ---------------------------------
detekt_metrics() {
  local reports=()
  while IFS= read -r report; do reports+=("$report"); done < <(
    find . -name 'detekt.xml' -path '*/build/reports/detekt/*' -print 2>/dev/null | sort
  )

  local count=0
  if [[ ${#reports[@]} -gt 0 ]]; then
    count=$(grep -oh '<error ' "${reports[@]}" 2>/dev/null | wc -l | tr -d ' ')
  fi
  if [[ "$DETEKT_RUN_STATUS" == "failed" ]]; then
    if [[ ${#reports[@]} -gt 0 ]]; then
      metric "detekt_new_findings" "Detekt new findings" "$count+ found before task failure" "" "findings" \
        "unavailable" "⚪ incomplete" "${bt}detekt${bt} failed; inspect the build log and rerun ${bt}make health${bt}"
    else
      metric "detekt_new_findings" "Detekt new findings" "n/a (task failed)" "" "findings" \
        "unavailable" "⚪ not measured" "${bt}detekt${bt} failed before producing reports"
    fi
  elif [[ ${#reports[@]} -gt 0 ]]; then
    if [[ "$count" -eq 0 ]]; then
      metric "detekt_new_findings" "Detekt new findings" "0" "$count" "findings" \
        "healthy" "🟢 clean" "—"
    else
      metric "detekt_new_findings" "Detekt new findings" "$count" "$count" "findings" \
        "warning" "🟡 fix" "${bt}make lint${bt}; fix new findings rather than extending a baseline"
    fi
  else
    metric "detekt_new_findings" "Detekt new findings" "n/a" "" "findings" \
      "unavailable" "⚪ not measured" "run with ${bt}--run${bt} (or ${bt}make lint${bt})"
  fi

  local baselines=() count=0 file file_count
  while IFS= read -r file; do baselines+=("$file"); done < <(
    git ls-files '*detekt-baseline.xml' | sort
  )
  if [[ ${#baselines[@]} -gt 0 ]]; then
    for file in "${baselines[@]}"; do
      file_count=$(grep -c '<ID>' "$file" 2>/dev/null || true)
      count=$((count + ${file_count:-0}))
    done
    if [[ "$count" -eq 0 ]]; then
      metric "detekt_baseline_entries" "Detekt baseline debt" "0 across ${#baselines[@]} files" "$count" "entries" \
        "healthy" "🟢 clean" "remove empty baseline files"
    else
      metric "detekt_baseline_entries" "Detekt baseline debt" "$count across ${#baselines[@]} files" "$count" "entries" \
        "warning" "🟡 burn down" "fix suppressed findings; never regenerate a baseline to bury new debt"
    fi
  else
    metric "detekt_baseline_entries" "Detekt baseline debt" "0 baseline files" "0" "entries" \
      "healthy" "🟢 clean" "—"
  fi
}

# --- 3. Age of the least-recently changed tracked quality baseline ---------------------------
baseline_age_metric() {
  local oldest_date="" oldest_file="" file date
  while IFS= read -r file; do
    date=$(git log -1 --format=%cs -- "$file" 2>/dev/null || true)
    [[ -n "$date" ]] || continue
    if [[ -z "$oldest_date" || "$date" < "$oldest_date" ]]; then
      oldest_date="$date"
      oldest_file="$file"
    fi
  done < <(git ls-files '*detekt-baseline.xml' '*lint-baseline.xml' | sort)

  if [[ -n "$oldest_date" ]]; then
    local age
    age=$(python3 - "$oldest_date" <<'PY'
from datetime import date
import sys
print((date.today() - date.fromisoformat(sys.argv[1])).days)
PY
)
    metric "oldest_quality_baseline_age_days" "Oldest unchanged quality baseline" \
      "$oldest_date (${age} days; ${oldest_file})" "$age" "days" "info" "ℹ️ tracked" \
      "file age is not finding age; review whether its suppressions still reproduce"
  else
    metric "oldest_quality_baseline_age_days" "Oldest unchanged quality baseline" "n/a" "" "days" \
      "unavailable" "⚪ not measured" "no tracked Detekt or Android Lint baseline found"
  fi
}

# --- 4. Compose unstable params ---------------------------------------------------------------
compose_metric() {
  local reports=()
  while IFS= read -r report; do reports+=("$report"); done < <(
    find . -name '*composables.txt' -path '*/compose_compiler/*' -print 2>/dev/null | sort
  )

  if [[ "$COMPOSE_RUN_STATUS" == "failed" ]]; then
    metric "compose_unstable_parameters" "Compose unstable params" "n/a (compile failed)" "" "parameters" \
      "unavailable" "⚪ not measured" "${bt}compileReleaseKotlin${bt} failed while generating Compose reports"
  elif [[ ${#reports[@]} -gt 0 ]]; then
    local count expected_count unexpected_count
    count=$(grep -oh 'unstable ' "${reports[@]}" 2>/dev/null | wc -l | tr -d ' ')
    expected_count=$(grep -hE '^[[:space:]]*unstable deepLinkUri: Uri\?' "${reports[@]}" 2>/dev/null | wc -l | tr -d ' ')
    unexpected_count=$((count - expected_count))
    if [[ "$count" -eq 0 ]]; then
      metric "compose_unstable_parameters" "Compose unstable params" "0" "$count" "parameters" \
        "healthy" "🟢 clean" "—"
    elif [[ "$expected_count" -eq 1 && "$unexpected_count" -eq 0 ]]; then
      metric "compose_unstable_parameters" "Compose unstable params" "1" "$count" "parameters" \
        "healthy" "🟢 expected" "framework ${bt}deepLinkUri: Uri?${bt} only"
    else
      metric "compose_unstable_parameters" "Compose unstable params" "$count (${unexpected_count} unexpected)" "$count" "parameters" \
        "warning" "🟡 review" "inspect Compose compiler reports; stabilise genuinely immutable application types"
    fi
  else
    metric "compose_unstable_parameters" "Compose unstable params" "n/a" "" "parameters" \
      "unavailable" "⚪ not measured" "${bt}make compose-metrics${bt}"
  fi
}

# --- 5. Selected JaCoCo aggregate line coverage ----------------------------------------------
coverage_metric() {
  if [[ "$COVERAGE_RUN_STATUS" == "failed" ]]; then
    metric "jacoco_selected_line_coverage_percent" "Selected JaCoCo line coverage" "n/a (task failed)" "" "percent" \
      "unavailable" "⚪ not measured" "${bt}jacocoRootReport${bt} failed; inspect the build log"
    return
  fi

  local xml
  xml=$(find . -name 'jacocoRootReport.xml' -path '*/build/*' -print 2>/dev/null | sort | head -1)
  if [[ -n "$xml" && -f "$xml" ]]; then
    local percentage
    percentage=$(python3 - "$xml" <<'PY'
import re
import sys

data = open(sys.argv[1], encoding="utf-8", errors="ignore").read()
counters = re.findall(r'<counter type="LINE" missed="(\d+)" covered="(\d+)"', data)
if not counters:
    raise SystemExit(1)
missed, covered = map(int, counters[-1])
total = missed + covered
print(f"{100 * covered / total:.1f}" if total else "0.0")
PY
) || percentage=""
    if [[ -n "$percentage" ]]; then
      metric "jacoco_selected_line_coverage_percent" "Selected JaCoCo line coverage" \
        "${percentage}%" "$percentage" "percent" "info" "ℹ️ selected scope" \
        "see ${bt}build/reports/jacoco/jacocoRootReport/html/index.html${bt}; scope/exclusions are defined by ${bt}jacocoRootReport${bt}"
      return
    fi
  fi
  metric "jacoco_selected_line_coverage_percent" "Selected JaCoCo line coverage" "n/a" "" "percent" \
    "unavailable" "⚪ not measured" "run with ${bt}--run${bt} (or ${bt}make jacoco-report${bt})"
}

# --- 6. Dependency graph lock ----------------------------------------------------------------
depguard_metric() {
  local file="app/dependencies/releaseRuntimeClasspath.txt"
  if [[ -f "$file" ]]; then
    local count
    count=$(grep -c . "$file" 2>/dev/null || true)
    count=${count:-0}
    metric "dependency_guard_locked_entries" "Dependency Guard release graph" "$count deps locked" "$count" "dependencies" \
      "healthy" "🟢 guarded" "drift fails CI; re-baseline intentionally"
  else
    metric "dependency_guard_locked_entries" "Dependency Guard release graph" "n/a" "" "dependencies" \
      "unavailable" "⚪ not measured" "${bt}make dependency-guard-baseline${bt}"
  fi
}

# --- 7. Resolved architecture graph shape -----------------------------------------------------
architecture_metrics() {
  local file="build/reports/module-graph/modules.json"
  if [[ ! -f "$file" ]]; then
    metric "architecture_graph_nodes" "Architecture graph modules" "n/a" "" "modules" \
      "unavailable" "⚪ not measured" "run with ${bt}--run${bt} (or ${bt}make module-graph${bt})"
    metric "architecture_graph_edges" "Architecture graph edges" "n/a" "" "edges" \
      "unavailable" "⚪ not measured" "run with ${bt}--run${bt} (or ${bt}make module-graph${bt})"
    metric "architecture_graph_max_fan_in" "Architecture graph maximum fan-in" "n/a" "" "modules" \
      "unavailable" "⚪ not measured" "run with ${bt}--run${bt} (or ${bt}make module-graph${bt})"
    metric "architecture_graph_max_fan_out" "Architecture graph maximum fan-out" "n/a" "" "modules" \
      "unavailable" "⚪ not measured" "run with ${bt}--run${bt} (or ${bt}make module-graph${bt})"
    metric "architecture_graph_api_project_edges" "Architecture project API edges" "n/a" "" "edges" \
      "unavailable" "⚪ not measured" "run with ${bt}--run${bt} (or ${bt}make module-graph${bt})"
    return
  fi

  local values
  values=$(python3 - "$file" <<'PY'
import json
import sys

data = json.load(open(sys.argv[1], encoding="utf-8"))
summary = data.get("summary", {})
print("\t".join(str(summary.get(key, "")) for key in (
    "nodeCount", "edgeCount", "maxFanIn", "maxFanOut", "apiProjectEdgeCount"
)))
PY
) || values=""
  local nodes edges max_in max_out api_edges
  IFS=$'\t' read -r nodes edges max_in max_out api_edges <<< "$values"
  if [[ "$ARCHITECTURE_RUN_STATUS" == "failed" ]]; then
    local status="unavailable" status_label="⚪ incomplete"
  else
    local status="info" status_label="ℹ️ tracked"
  fi
  metric "architecture_graph_nodes" "Architecture graph modules" "${nodes:-n/a}" "${nodes:-}" "modules" \
    "$status" "$status_label" "role coverage is policy-driven; inspect ${bt}build/reports/module-graph/modules.json${bt}"
  metric "architecture_graph_edges" "Architecture graph edges" "${edges:-n/a}" "${edges:-}" "edges" \
    "$status" "$status_label" "direct project edges; inspect policy changes in review"
  metric "architecture_graph_max_fan_in" "Architecture graph maximum fan-in" "${max_in:-n/a}" "${max_in:-}" "modules" \
    "$status" "$status_label" "trend only; not a module-split target"
  metric "architecture_graph_max_fan_out" "Architecture graph maximum fan-out" "${max_out:-n/a}" "${max_out:-}" "modules" \
    "$status" "$status_label" "trend only; not a module-split target"
  metric "architecture_graph_api_project_edges" "Architecture project API edges" "${api_edges:-n/a}" "${api_edges:-}" "edges" \
    "$status" "$status_label" "review growth against the previous health artifact; every production API edge needs policy approval"
}

lint_metric
detekt_metrics
baseline_age_metric
compose_metric
coverage_metric
depguard_metric
architecture_metrics

GENERATED_AT=$(date -u '+%Y-%m-%dT%H:%M:%SZ')
SOURCE_REVISION=$(git rev-parse HEAD 2>/dev/null || printf 'unknown')
SOURCE_DIRTY=false
[[ -z "$(git status --porcelain 2>/dev/null)" ]] || SOURCE_DIRTY=true

python3 - "$METRICS_FILE" "$OUT" "$JSON_OUT" "$PREVIOUS_JSON" \
  "$GENERATED_AT" "$SOURCE_REVISION" "$SOURCE_DIRTY" <<'PY'
import json
import pathlib
import sys

metrics_path, markdown_path, json_path, previous_path, generated_at, revision, dirty_text = sys.argv[1:]
metrics = [json.loads(line) for line in pathlib.Path(metrics_path).read_text(encoding="utf-8").splitlines() if line]
previous = {"status": "notProvided"}

if previous_path:
    previous_file = pathlib.Path(previous_path)
    if not previous_file.is_file():
        previous = {"status": "missing", "path": previous_path}
    else:
        try:
            previous_report = json.loads(previous_file.read_text(encoding="utf-8"))
            if not isinstance(previous_report, dict) or previous_report.get("schemaVersion") != 1:
                raise ValueError("expected a schemaVersion 1 report object")
            raw_previous_metrics = previous_report.get("metrics")
            if not isinstance(raw_previous_metrics, list):
                raise ValueError("expected metrics to be an array")

            previous_metrics = {}
            for old_metric in raw_previous_metrics:
                if not isinstance(old_metric, dict) or not isinstance(old_metric.get("id"), str):
                    raise ValueError("each previous metric must be an object with a string id")
                previous_metrics[old_metric["id"]] = old_metric

            pending_deltas = {}
            for metric in metrics:
                old = previous_metrics.get(metric["id"])
                if not old or metric["value"] is None or old.get("value") is None:
                    continue
                if metric.get("unit") != old.get("unit"):
                    continue
                current_value = metric["value"]
                old_value = old["value"]
                if isinstance(old_value, bool) or not isinstance(old_value, (int, float)):
                    raise ValueError(f"previous metric {metric['id']} has a non-numeric value")
                delta = current_value - old_value
                if delta == 0:
                    display = "↔ 0"
                    direction = "unchanged"
                elif delta > 0:
                    display = f"↑ {delta:g}"
                    direction = "up"
                else:
                    display = f"↓ {abs(delta):g}"
                    direction = "down"
                pending_deltas[metric["id"]] = {
                    "value": delta,
                    "direction": direction,
                    "display": display,
                    "previousValue": old_value,
                }

            for metric in metrics:
                if metric["id"] in pending_deltas:
                    metric["delta"] = pending_deltas[metric["id"]]
            previous = {
                "status": "loaded",
                "path": previous_path,
                "generatedAt": previous_report.get("generatedAt"),
                "sourceRevision": previous_report.get("sourceRevision"),
            }
        except (OSError, ValueError, TypeError, AttributeError, KeyError) as error:
            previous = {"status": "invalid", "path": previous_path, "error": str(error)}

report = {
    "schemaVersion": 1,
    "generatedAt": generated_at,
    "sourceRevision": revision,
    "sourceDirty": dirty_text == "true",
    "previousRun": previous,
    "metrics": metrics,
}

def escape_markdown(value):
    return str(value).replace("|", "\\|").replace("\n", " ")

revision_label = revision[:12] if revision != "unknown" else revision
if report["sourceDirty"]:
    revision_label += " (dirty checkout)"
lines = [
    "# BillionBeers Health Report",
    "",
    f"_Generated {generated_at} from `{revision_label}` · read-only checks only. Regenerate with `make health`._",
    "",
    "| Check | Metric | Status | What to do |",
    "|---|---|---|---|",
]
for metric in metrics:
    value = metric["displayValue"]
    if "delta" in metric:
        value += f" ({metric['delta']['display']} vs previous)"
    lines.append(
        "| " + " | ".join(escape_markdown(value) for value in (
            metric["name"], value, metric["statusLabel"], metric["action"]
        )) + " |"
    )
lines.extend([
    "",
    "> Legend: 🟢 healthy · 🟡 backlog to burn down · ℹ️ informational · ⚪ not measured this run.",
])
if previous["status"] == "loaded":
    lines.append("> Deltas compare metrics with the supplied previous health artifact; arrows show direction, not whether a change is good or bad.")
elif previous_path:
    lines.append(f"> Previous comparison: {previous['status']}; deltas omitted.")
markdown = "\n".join(lines) + "\n"

if markdown_path:
    pathlib.Path(markdown_path).write_text(markdown, encoding="utf-8")
else:
    sys.stdout.write(markdown)
if json_path:
    pathlib.Path(json_path).write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
PY

[[ -z "$OUT" ]] || echo "Wrote $OUT" >&2
[[ -z "$JSON_OUT" ]] || echo "Wrote $JSON_OUT" >&2
exit 0
