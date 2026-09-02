# 0008: Per-lane CI test selection with verdict adoption

## Status

Accepted. Extends the blanket path filter added in PR #110 (recorded in the `changes` job comment,
never given an ADR); PRs #117, #119, #120, #121.

## Context

The three heavy CI lanes — unit, Paparazzi screenshot, and the Gradle Managed Device instrumented
run — cost roughly 2.4, 2.4 and 4.5 minutes here. PR #110 already skipped all three when a PR's
whole diff was docs/skills/scripts. What it could not do is anything finer: a PR that touches code
*anywhere* ran the full suite on *every* push, including the push that only re-records a golden
image or only edits an ADR.

The interesting cases are the ones where a push cannot affect a lane that has already passed:
re-recording screenshots after a Paparazzi failure, fixing a unit test, tweaking docs on a code PR.
Re-running an unaffected lane proves nothing, and at scale it dominates: this mechanism was built
here deliberately as a rehearsal for a work project whose lanes cost 20 minutes (unit), 30
(screenshot) and 40 (UI on Firebase), where the same push pattern wastes ~90 minutes.

The naive version of this is dangerous. Since the `CI Gate` treats `skipped` as pass (ADR-less, see
its job comment), a lane that skips is *asserting* it would have passed. Get that assertion wrong
and a red PR silently turns green.

## Decision

**A lane runs if the push could affect it, or if its verdict on the previous head was not green.
Otherwise it adopts that green verdict and skips.** Red stays red; green stays green.

The implementation is `.github/scripts/detect-change-scope.sh`, which emits one output per lane.

### 1. The rules live in exactly one function

`classify_path` maps one changed file onto the lanes it can affect. Both levels of the filter call
it — "is this whole PR inert?" and "what did this push touch?" — so there is a single place to edit
when the rules change, and no second copy to drift. Its final `else` runs everything, so an
unrecognised path is never silently skipped.

The current map: goldens and `screenshot/` test sources → screenshot; `src/androidTest/` →
instrumented; other `src/test/` → unit; `scripts/coverage-check.sh` → unit (the unit lane executes
it); docs, skills, local notes and other scripts → inert; everything else → all three.

The unit/screenshot split is only sound because the build already partitions them the same way:
`billionbeers.android.screenshot.gradle.kts` excludes `com.simtop.billionbeers.screenshot.*` from
plain test runs and includes only it in Paparazzi runs. **If that filter changes, this map changes
with it.**

### 2. The script lives under `.github/`, not `scripts/`

`scripts/` is inert. A filter that could classify an edit to itself as inert would be able to skip
validating its own change.

### 3. "Green" is anchored on the filter job, not the `CI Gate`

A lane's previous conclusion of `skipped` counts as green only if the `Detect change scope` job
succeeded in that run — because that skip was itself a sound adoption (induction).

Anchoring on the aggregate `CI Gate` instead, as #117 originally did, was measurably wrong. The
gate goes red when *any* job fails, including ones that say nothing about test freshness. On a PR
being fixed over several pushes, the first fix push is cheap (the other lanes still hold
`success`), but from the second push onward their conclusion is `skipped` under a red gate, so
nothing is trusted and every unaffected lane re-runs — exactly the case the mechanism exists to
speed up. Note `CI Gate` success implies filter success, so this rule skips in a strict superset of
the old one's cases.

The trade is explicit: the gate anchor was also the only thing periodically forcing a full
re-execution. **The path map is now the sole safety net.**

### 4. The pushed range is a direct tree diff, not a merge-base diff

`git diff BEFORE AFTER`, never `BEFORE...AFTER`. A force-push that *drops* a commit makes the new
head an ancestor of the old one, so the merge base **is** the new head and a three-dot diff reports
zero files — measured. The filter would then adopt verdicts from a tree that still contained the
reverted code. The two-dot form reports the revert as a change and runs everything.

### 5. Everything uncertain fails open

Non-PR events, a PR's first run, an unreachable `before` (the normal outcome of a force-push, since
the orphaned commit is never fetched), no or unreadable previous run, a non-numeric run id, a
renamed job, and a previous run still in flight all run the full suite. Skipping a real test is the
costly mistake; an extra run is minutes.

### 6. Merge-group candidates always run complete validation

The required workflow subscribes to GitHub's `merge_group: checks_requested` event. A merge-group
SHA represents the latest base plus the queued entries, not the already-tested pull-request head, so
it goes through the existing non-PR fail-open branch and runs all three heavy lanes. It never adopts
a PR-head verdict. The same stable `CI Gate` is then reported for branch protection.

## Consequences

Validated live rather than by argument. A docs-only push skipped all three lanes with the gate
green; a push touching only a plain unit test ran unit alone, adopting from a run where all three
had genuinely executed; and the safety property was tested directly by making the unit lane red and
then pushing docs — **the red lane re-ran and failed again, and the gate stayed red.**

Costs and sharp edges worth knowing:

- **A force-push or rebase runs everything.** Correct — a rebase changes the code under test — but
  at work, where strict branch protection makes "Update branch" frequent, this is a real recurring
  cost.
- **A rapid second push loses adoption for in-flight lanes.** Concurrency cancels the previous run;
  lanes still running report a null conclusion and are not trusted. Lanes that had already finished
  keep `success` and are still adopted, so the loss is partial.
- **Job names are matched as literal strings.** A rename disables adoption for that lane and only
  makes CI slower, with no failure to notice — so an absent name emits a `::warning::`. This is
  keyed on the name being absent, not on the conclusion being unreadable, because a job that is
  merely unfinished looks identical to a renamed one otherwise.
- **The map is now the only thing standing between a stale verdict and a green PR.** Shared test
  *compilation* is still covered (a compile break fails whichever lane re-runs); shared *behaviour*
  is not.

## Rejected: two-way screenshot sharding

PR #200 evaluated a static two-runner split over all seven modules and 273 goldens. Stable aggregate
semantics worked, and both assignments were already balanced:

| Run | `browse-detail` | `design-list-search` | Lane wall clock | Total job work |
|---|---:|---:|---:|---:|
| `33597137353` | 4m15s | 4m28s | 4m34s | 8m47s |
| `33598554628` | 4m22s | 4m15s | 4m30s | 8m42s |

Against the ten-run `master` median of 5m18s, the candidate median improved wall clock by only about
14.5% while increasing runner work by about 65%. That misses both experiment thresholds (20% faster,
less than 50% more work). A greedy module repartition cannot fix this: the existing shards differed
by only 13 seconds and then 7 seconds; duplicated checkout, JDK/Gradle setup, LFS, and shared
compilation are the cost. Keep one screenshot job unless the module graph or runner setup changes
materially.

The experiment also exposed a pre-existing empty artifact: Paparazzi's HTML reporter is disabled for
Gradle 9 compatibility, so `build/reports/paparazzi` contains no files on a green run. CI now archives
the JUnit XML under `build/test-results` instead. Screenshot failures remain separate PNG artifacts
whose filenames identify the failed snapshot configuration.

## Rejected: walking back more than one run

When the previous run was cancelled or is still in flight, its lanes are untrusted and re-run. The
obvious improvement is to walk further back to the last run where the lane actually executed.

Rejected because it is not a lookup, it is the classification problem again — recursively. To trust
a verdict from N runs ago you must prove that *every* intermediate diff was neutral for that lane,
so a single wrong link in the chain silently invalidates the conclusion, and the chain is
unbounded. The failure mode is the worst one available (a skipped lane that should have run) and
the payoff is bounded by how often people push twice in quick succession. The one-run lookback
fails open instead, which costs a few minutes and cannot be wrong.

If bounded coasting is wanted later, the right lever is an explicit one — a scheduled full run on
open PRs, or a `ci:full` label — not a longer inference chain.

## Notes for reuse elsewhere

- **Branch protection must require only the aggregate gate.** A skipped lane never reports a
  status, so a *required* lane check would leave the PR permanently pending.
- **No third-party action is used.** `git`, `gh` and `jq` only, consistent with ADR 0006's
  preference for pinned binaries over marketplace actions. Where a repo already uses
  `dorny/paths-filter`, keep it for classification and add only the adoption lookup — path
  classification is the half dorny already does well, and verdict adoption is the half it has no
  concept of.
- **Paginate the jobs lookup.** A sharded matrix can exceed 100 jobs in one run; a lane whose job
  falls off the first page reads as renamed and quietly stops being adopted.
