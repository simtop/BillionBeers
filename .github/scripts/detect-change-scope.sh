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
#   the gate catches it even when the other lane adopted green. What is NOT covered is *behavioural*
#   sharing: a helper under `src/test/` outside a `screenshot/` folder that a hand-written screenshot
#   test depends on would let the screenshot lane adopt a stale green. No such helper exists today
#   (:catalog is the only module with hand-written screenshot tests and it has no other test
#   sources). If that changes, a plain `src/test/` edit in a module that also holds screenshot tests
#   has to set a_screenshot too.
set -euo pipefail

emit() {
  echo "unit=$1" >> "$GITHUB_OUTPUT"
  echo "screenshot=$2" >> "$GITHUB_OUTPUT"
  echo "instrumented=$3" >> "$GITHUB_OUTPUT"
  echo "Decision: unit=$1 screenshot=$2 instrumented=$3 ($4)"
  # Also surface it on the run page: "why did that lane not run?" should not require opening a log.
  if [ -n "${GITHUB_STEP_SUMMARY:-}" ]; then
    { echo "### Test lane selection"
      echo ""
      echo "$4"
      echo ""
      echo "| lane | runs |"
      echo "|---|---|"
      echo "| Unit Tests | $1 |"
      echo "| Screenshot Tests (Paparazzi) | $2 |"
      echo "| Instrumented Tests | $3 |"
    } >> "$GITHUB_STEP_SUMMARY"
  fi
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
# Must be a bare run id. On a transient API error (observed: HTTP 504) gh prints the error body to
# *stdout*, so this lands here as JSON rather than as the empty string - an emptiness check alone
# lets that text through into the next request URL. Anything that is not digits fails open.
if ! [[ "$PREV_RUN" =~ ^[0-9]+$ ]]; then
  emit true true true "no usable previous CI run for $BEFORE - failing open"
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

# Every name below is matched against the previous run's job list as a literal string, so renaming
# a job silently turns its lookup into "missing" -> nothing is ever adopted -> CI just quietly gets
# slower, with no failure to notice. Warn loudly instead.
#
# Warn on an absent *name*, not on a "missing" conclusion. The two are different and only the first
# means a rename: a job that is still running is listed by name with a null conclusion, which
# `concl` also reports as "missing". That happens whenever the previous run was superseded by this
# push (concurrency cancels it mid-flight), so keying the warning off `concl` would cry wolf on
# every rapid second push. The *decision* still fails open in both cases, which is what matters.
# (A PR that adds a lane trips this once, because the previous run predates the job - self-clearing.)
has_job() { echo "$JOBS" | jq -e --arg n "$1" 'any(.[]; .name == $n)' >/dev/null 2>&1; }
for n in "Detect change scope" "Unit Tests" "Screenshot Tests (Paparazzi)" \
  "Instrumented Tests (Gradle Managed Device)"; do
  has_job "$n" ||
    echo "::warning::Job '$n' not found in previous run $PREV_RUN - verdict adoption is disabled for it. If this job was renamed, update .github/scripts/detect-change-scope.sh to match."
done

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
