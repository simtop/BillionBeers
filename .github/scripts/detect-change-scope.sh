#!/usr/bin/env bash
# Change-scope detector with per-lane verdict adoption. Called by the `changes` job in
# .github/workflows/ci.yml - the job comment there has the short version of the design.
#
# Inputs (env, set by the workflow step):
#   EVENT_NAME    github.event_name
#   EVENT_ACTION  github.event.action
#   BASE_SHA      github.event.pull_request.base.sha
#   BEFORE/AFTER  github.event.before / .after (previous and new PR head on synchronize)
#   REPO          github.repository
#   GH_TOKEN      token with actions:read (for the previous run's job conclusions)
#   GITHUB_OUTPUT
#
# Outputs: unit / screenshot / instrumented - "true" means that lane runs.
#
# Soundness rules - keep these when editing:
# - This file must live under .github/ (NOT scripts/): scripts/ is in the safe-list, and an edit
#   to the filter itself has to run the full suite so it validates itself.
# - The pushed range is the direct tree diff BEFORE -> AFTER, NOT a merge-base diff: a force-push
#   that *drops* a code commit changes the tree without adding commits and must still classify
#   as code.
# - "Green" means the job succeeded, or was skipped in a run where THIS filter job succeeded (that
#   skip was itself a sound adoption - induction). The trust anchor is the filter job, not the
#   aggregate CI Gate: the filter's decision that lane L could skip does not become unsound because
#   some other lane M failed. Anchoring on the gate instead made adoption collapse on any red PR -
#   from the second push onward every unaffected lane rerun, which is the exact case (a PR being
#   fixed over several pushes) the mechanism exists to speed up. A cancelled run is still handled:
#   its unstarted lanes are `cancelled`, not `skipped`, so they fail open.
# - Every uncertainty fails open to running everything: non-PR events, first run of a PR,
#   unreachable BEFORE, no or unreadable previous run, renamed jobs, a previous run still in
#   flight. Skipping a real test is the costly mistake; an extra run is just minutes.
# - The unit/screenshot split mirrors the test filter in
#   build-logic/convention/src/main/kotlin/billionbeers.android.screenshot.gradle.kts: plain test
#   runs exclude com.simtop.billionbeers.screenshot.*, Paparazzi runs include only it, and the
#   package lives in a `screenshot/` source folder. If that filter changes, this map changes with
#   it. Test *compilation* is still shared - but a compile break fails whichever lane reruns, so
#   the gate catches it even when the other lane adopted green.
set -euo pipefail

emit() {
  echo "unit=$1" >> "$GITHUB_OUTPUT"
  echo "screenshot=$2" >> "$GITHUB_OUTPUT"
  echo "instrumented=$3" >> "$GITHUB_OUTPUT"
  echo "Decision: unit=$1 screenshot=$2 instrumented=$3 ($4)"
}

# Docs / skills / local-notes paths that no test lane can be affected by.
# scripts/coverage-check.sh is carved out: the unit lane executes it (make coverage-check).
is_safe() {
  [ "$1" = "scripts/coverage-check.sh" ] && return 1
  echo "$1" | grep -qE '^(docs/|rod/|imagesForReadme/|\.claude/skills/|scripts/|.*\.md$|LICENSE$)'
}

if [ "$EVENT_NAME" != "pull_request" ]; then
  emit true true true "not a PR - post-merge validation is always complete"
  exit 0
fi

CHANGED=$(git diff --name-only "$BASE_SHA...HEAD")
echo "PR diff:"; echo "$CHANGED"
all_safe=true
while IFS= read -r f; do
  [ -z "$f" ] && continue
  is_safe "$f" || { all_safe=false; break; }
done <<< "$CHANGED"
if $all_safe; then
  emit false false false "docs/skills/scripts-only PR"
  exit 0
fi

if [ "$EVENT_ACTION" != "synchronize" ]; then
  emit true true true "first run of a code PR"
  exit 0
fi

if [ -z "${BEFORE:-}" ] || ! git cat-file -e "$BEFORE" 2>/dev/null; then
  emit true true true "previous head unreachable (force-push) - failing open"
  exit 0
fi

PUSHED=$(git diff --name-only "$BEFORE" "$AFTER")
echo "Pushed diff ($BEFORE -> $AFTER):"; echo "$PUSHED"

# Path -> lane map. Keep it sound: when in doubt a path must fall through to
# "affects everything".
a_unit=false; a_screenshot=false; a_instrumented=false
while IFS= read -r f; do
  [ -z "$f" ] && continue
  if [ "$f" = "scripts/coverage-check.sh" ]; then
    a_unit=true # runs inside the unit lane
  elif is_safe "$f"; then
    : # affects no lane
  elif [[ "$f" == */src/test/snapshots/* ]]; then
    a_screenshot=true # recorded goldens
  elif [[ "$f" == */src/test/* && "$f" == */screenshot/* ]]; then
    a_screenshot=true # screenshot-test source, excluded from plain test runs
  elif [[ "$f" == */src/androidTest/* ]]; then
    a_instrumented=true
  elif [[ "$f" == */src/test/* ]]; then
    a_unit=true # plain unit-test source, excluded from Paparazzi runs
  else
    a_unit=true; a_screenshot=true; a_instrumented=true
  fi
done <<< "$PUSHED"

if $a_unit && $a_screenshot && $a_instrumented; then
  emit true true true "push touches code paths"
  exit 0
fi

# At least one lane is unaffected by this push. It may skip IFF its verdict on the previous
# head was green - look that up.
PREV_RUN=$(gh api "repos/$REPO/actions/workflows/ci.yml/runs?head_sha=$BEFORE&event=pull_request&per_page=1" \
  --jq '.workflow_runs[0].id // empty' || true)
if [ -z "$PREV_RUN" ]; then
  emit true true true "no previous CI run for $BEFORE - failing open"
  exit 0
fi
JOBS=$(gh api "repos/$REPO/actions/runs/$PREV_RUN/jobs?per_page=100" \
  --jq '[.jobs[] | {name, conclusion}]' || true)
if [ -z "$JOBS" ]; then
  emit true true true "could not read jobs of previous run $PREV_RUN - failing open"
  exit 0
fi
echo "Previous run $PREV_RUN job conclusions: $JOBS"

concl() { echo "$JOBS" | jq -r --arg n "$1" 'map(select(.name == $n))[0].conclusion // "missing"'; }
# This job's own name - if it is ever renamed, the lookup returns "missing" and every skip stops
# being trusted, so the mechanism degrades to running everything rather than to skipping wrongly.
PREV_FILTER=$(concl "Detect change scope")

was_green() {
  local c; c=$(concl "$1")
  [ "$c" = "success" ] || { [ "$c" = "skipped" ] && [ "$PREV_FILTER" = "success" ]; }
}

decide() { # $1 = affected by this push (true/false), $2 = previous job name
  if [ "$1" = "true" ]; then echo true
  elif was_green "$2"; then echo false
  else echo true
  fi
}

UNIT=$(decide "$a_unit" "Unit Tests")
SCREENSHOT=$(decide "$a_screenshot" "Screenshot Tests (Paparazzi)")
INSTRUMENTED=$(decide "$a_instrumented" "Instrumented Tests (Gradle Managed Device)")
emit "$UNIT" "$SCREENSHOT" "$INSTRUMENTED" \
  "per-lane: rerunning affected or previously-red lanes, adopting green verdicts from run $PREV_RUN"
