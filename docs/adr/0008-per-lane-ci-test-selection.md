# 0008: Per-lane CI test selection with verdict adoption

## Status

Accepted. Extends the blanket path filter added in PR #110 (recorded in the `changes` job comment,
never given an ADR); PRs #117, #119, #120, #121.

## Context

The five CI test lanes — unit, Paparazzi screenshot, Gradle Managed Device instrumented, Apple
native, and Web — include the expensive platform validations. PR #110 already skipped the Android
lanes when a PR's whole diff was docs/skills/scripts. What it could not do is anything finer: a PR that touches code
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

The current map: common production sources → all five lanes; common tests → unit and native;
Apple production/test sources → native; goldens and `screenshot/` test sources → screenshot;
`src/androidTest/` → instrumented; other `src/test/` → unit; Wasm production/test sources →
unit, screenshot, instrumented and Web; `scripts/coverage-check.sh`,
`scripts/test-tier-inventory.sh` and `scripts/test-tier-inventory-test.py` → unit;
Web verifier, image-proxy and smoke scripts → Web; docs, skills, local notes and other scripts →
inert; everything else → all five.

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

The gate anchor was also periodically forcing a full re-execution. That reduced repeated adoption,
but it made unaffected lanes rerun on any unrelated gate failure. The weekly full-PR validation in
§8 now provides a separate freshness bound while keeping per-push adoption anchored to the filter.

### 4. The pushed range is a direct tree diff, not a merge-base diff

`git diff BEFORE AFTER`, never `BEFORE...AFTER`. A force-push that *drops* a commit makes the new
head an ancestor of the old one, so the merge base **is** the new head and a three-dot diff reports
zero files — measured. The filter would then adopt verdicts from a tree that still contained the
reverted code. The two-dot form reports the revert as a change and runs everything.

### 5. Everything uncertain fails open

Non-PR events, except the verified post-merge master case in §7, a PR's first run, an unreachable
`before` (the normal outcome of a force-push, since the orphaned commit is never fetched), no or
unreadable previous run, a non-numeric run id, a renamed job, and a previous run still in flight all
run the full suite. Skipping a real test is the costly mistake; an extra run is minutes.

### 6. Merge-group candidates always run complete validation

The required workflow subscribes to GitHub's `merge_group: checks_requested` event. A merge-group
SHA represents the latest base plus the queued entries, not the already-tested pull-request head, so
it goes through the existing non-PR fail-open branch and runs all five test lanes. It never adopts
a PR-head verdict. The same stable `CI Gate` is then reported for branch protection.

### 7. Post-merge runs may reuse a verified PR result only for the identical tree

Master pushes and manual runs on `master` may skip the five heavy lanes only when the current
commit is the exact merge commit of one merged, same-repository PR and a bounded proof artifact
from that PR's newest CI run establishes the same tested tree. The proof records the actual
checkout commit, tree, workflow blob, repository and PR identities, run and attempt, and event.
The selector independently resolves the merged PR, source run, required job results, artifact,
checkout commit parents, tree, and workflow blobs through the GitHub API. The source workflow
blob must also match the workflow at the PR base, so a PR cannot change its own proof producer and
then authorize reuse after merge. The tested base must be the merged commit's first parent, and
the source run must have succeeded on attempt 1 with every heavy lane and required static check
executed successfully. Missing, stale, skipped, ambiguous, expired, or inconsistent evidence
runs the full suite. Pull requests, merge groups, and all other events keep the existing
selection behavior. The dependency API job may succeed or skip, while the master-only failure
reporter must skip in the source PR run. The selector rejects missing, duplicate, or unexpected
jobs. Logs identify validation rejections without exposing API or parser error details.

The proof is produced inline in the final `CI Gate`, after all required jobs succeed, then uploaded
as a 30-day artifact. The `changes` job only consumes it for later master validation. The first
merge that changes the CI workflow cannot reuse its PR result because the PR-base workflow differs.
The merged, reviewed workflow version can then issue proofs for later eligible runs.

For an operator-requested full master validation, manually dispatch CI with
`gh workflow run ci.yml --ref master -f force_full=true`. This bypasses the verified-result lookup
and runs all five heavy lanes; the default remains reuse when exact proof is available. PR and
merge-group selection is unaffected.

### 8. Weekly full validation bounds PR verdict reuse

The `Weekly full PR CI` workflow runs every Monday at 05:00 UTC and dispatches a full CI run for
each open PR targeting `main` or `master`. The dispatch runs the trusted workflow definition from
`master`, sets `force_full=true`, and checks out `refs/pull/<number>/merge` in every job. This
validates the current merge tree, including fork PRs, without running workflow code from an untrusted
head branch. Failed dispatches fail the scheduled workflow and identify the affected PR numbers.

This scheduled run is a freshness backstop. Normal PR pushes retain the one-run verdict adoption
behavior above, and merge-group validation still runs the full suite independently.

### 9. Observe module impact before enabling narrower execution

PR formatting checks produce an advisory `ci-impact-shadow` artifact. The report uses the current
Gradle project graph and registered verification task names on the reporting host, including KMP
task exclusions. An absent host-specific task is not proof that a target has no tests. The report
traverses reverse dependencies across every configuration, including runtime, KSP, and test-helper
edges. Platform source sets constrain the candidate tiers. Production changes include consumers;
test-only changes stay with their owner. Plain JVM test helpers also invalidate that module's
screenshots. Android production changes retain app integration candidates because Play delivery
is not an ordinary app-to-feature compile dependency.

Unknown paths, build changes, and unknown source sets select the complete inventory. An invalid
model or unavailable diff produces an explicit unavailable report. Neither case changes execution.
The report writes no workflow outputs and cannot authorize a skipped lane or a verified-result
proof. Coverage, architecture, build-logic, report checks, tablet journeys, and release smoke keep
their existing scope. Configured task counts include empty and cached tasks, so they are not test
counts or estimates of time saved.

The Apple and screenshot lanes also archive Gradle HTML profiles and JSON task summaries. Summed
task durations include parallel work and must not be reported as wall clock. The existing phase
timings remain the wall-clock evidence. Apple Gradle commands are profiled; the Xcode host phase
retains its separate command timing. Screenshot verification keeps `--rerun-tasks` while its
compilation and execution costs are observed. Diagnostic failures do not suppress test failures.

Narrower execution requires a separate decision after full-run comparisons and omission probes
show which failures the candidate set would miss. The acceptance evidence must include resources,
generated sources, shared helpers, dynamic delivery, renames, and deletions. It must also preserve
complete coverage and distinguish partial test results from a full-suite reuse proof.

### 10. Test a run-isolated Kotlin/Native cache before adopting it

[Run 40663275577](https://github.com/simtop/BillionBeers/actions/runs/40663275577) produced the first
hosted task profiles. Apple compilation took 534 seconds, framework linking 644 seconds, and the
simulator-test command 496 seconds. Both framework links individually occupied about 625 seconds
of parallel task work. The simulator phase also spent most of its task work linking test binaries.
The job restored Gradle state but downloaded Kotlin/Native components into a fresh `.konan` tree.

The cache experiment preserves only Native dependencies and the currently selected compiler,
including its library caches. A staged copy excludes lock files before archiving; cache-action
negated globs alone do not filter descendants when the tar input is a parent directory. Gradle
state stays under its existing cache owner. The hypothesis is that these Native components avoid repeated dependency work during
linking. Archive restore and save time count against any gain.

The probe runs only on the same-repository `codex/native-cache-probe` PR. Its exact key includes the
workflow run ID, runner architecture and image, Xcode version, and tracked Gradle configuration.
A retry of the same run can hit its first attempt's cache; another workflow run cannot. No prefix
restore is allowed. Cache errors leave full Apple verification enabled, and a cache is saved only
after successful compilation, framework links, host build, simulator tests, and report validation.

A cache hit must pass those same checks on the same source and demonstrate at least a 20% whole-job
ceiling after transfer overhead before promotion to normal CI. Profiles must show the intended
work being removed. Small speedup claims still require repeated normalized samples as described
in ADR 0009. A failed or unhelpful candidate is removed rather than made a permanent CI dependency.

## Consequences

Validated live rather than by argument. A docs-only push skipped all five lanes with the gate
green; a push touching only a plain unit test ran unit alone, adopting from a run where all five
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

## Rejected: splitting architecture checks out of the unit lane

PR #203 added timing-only instrumentation around the unit lane's six existing commands. Six live
samples were then normalized against the unaffected Detekt job from the same run; the sixth was a
full rerun of the post-merge PR #204 commit rather than a different code change.

| Run | Unit tests | Architecture checks | Coverage | Measured phases | Whole job | Detekt | Job / Detekt |
|---|---:|---:|---:|---:|---:|---:|---:|
| `33652279325` | 3m43s | 11s | 11s | 4m05s | 4m31s | 1m00s | 4.52x |
| `33653205398` | 4m26s | 12s | 12s | 4m50s | 5m35s | 57s | 5.88x |
| `33657305914` | 2m53s | 11s | 11s | 3m15s | 3m38s | 50s | 4.36x |
| `33659002183` | 3m12s | 16s | 12s | 3m40s | 4m07s | 53s | 4.66x |
| `33660584195`, attempt 1 | 2m34s | 9s | 8s | 2m51s | 3m36s | 42s | 5.14x |
| `33660584195`, attempt 3 | 3m33s | 8s | 9s | 3m51s | 4m28s | 1m12s | 3.72x |

The medians are 3m23s for `make test`, 11s for Konsist plus both resolved architecture checks,
11s for coverage, 3m46s for all measured phases, and 4m18s for the whole job. Unit execution is
about 90.3% of measured phase time; the architecture bundle is about 4.9%. Dividing each sample by
its same-run Detekt duration changes the variance, not the conclusion: architecture remains only
8–16 seconds while runner allocation moves the whole job between 3m36s and 5m35s.

Moving the architecture checks to a parallel job therefore has a theoretical median saving of only
11 seconds, about 4% of the existing job and far below the 20% experiment threshold, before paying
for another checkout, JDK/Gradle setup, and configuration. Unit-test sharding is also premature:
it needs artifact-based JaCoCo merging and an omission mutation to prove aggregate coverage cannot
silently pass, while the unit lane already finishes well before the managed-device lane that sets
the current CI critical path. Keep the single unit job and its timing summary. Revisit only if unit
execution becomes the critical path or its topology changes materially.

## Hosted unit compiler memory

The unit lane runs all KMP browser tests alongside Android/JVM tests. With the locally measured
2 GiB Kotlin daemon heap, hosted run `36839525307` failed linking
`:shared:presentation:compileTestDevelopmentExecutableKotlinWasmJs` with
`OutOfMemoryError: GC overhead limit exceeded`; no failing test assertion was established.
The separate Web lane passed but does not compile every module's browser tests.

The unit job therefore overrides only its compiler heap to 4 GiB and limits Gradle to two workers.
It keeps the complete test inventory and local defaults. Runner capacity is logged before testing,
and archived compiler GC logs record the actual heap and collection behavior. This is a targeted
OOM mitigation, not a measured optimal heap or a CI speedup claim. Reassess from hosted evidence
if compilation still fails or machine memory becomes the constraint.

## Rejected: walking back more than one run

When the previous run was cancelled or is still in flight, its lanes are untrusted and re-run. The
obvious improvement is to walk further back to the last run where the lane actually executed.

Rejected because it is not a lookup, it is the classification problem again — recursively. To trust
a verdict from N runs ago you must prove that *every* intermediate diff was neutral for that lane,
so a single wrong link in the chain silently invalidates the conclusion, and the chain is
unbounded. The failure mode is the worst one available (a skipped lane that should have run) and
the payoff is bounded by how often people push twice in quick succession. The one-run lookback
fails open instead, which costs a few minutes and cannot be wrong.

The weekly full-PR run in §8 bounds coasting without recursively classifying multiple push diffs.
An operator can also force all lanes on a specific master validation with `force_full=true`.

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
