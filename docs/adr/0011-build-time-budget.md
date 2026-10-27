# 0011: Build time is budgeted, and measured locally rather than gated in CI

## Status

Accepted.

## Context

Every axis of this project's health is ratcheted against a committed number except the one that
decides whether its structure was worth building.

| Axis | Committed artifact |
|---|---|
| Line coverage | `config/coverage-floor.txt` |
| Resolved dependency graph | `app/dependencies/releaseRuntimeClasspath.txt` |
| Android Lint findings | `app/lint-baseline.xml` |
| App startup time | budget in `scripts/check-benchmark-budget.sh` |
| **Build time** | **nothing** |

The instrument already existed — `benchmark.scenarios` and `make gradle-benchmark` have been in the
repo for months — so this was never a tooling gap. The reading was simply never taken, never
committed, and therefore never able to catch a regression.

That matters more here than in most projects, because the repository's central claim is structural —
a deliberately modular graph, convention plugins, ten enforced invariants. The question a reviewer is
entitled to ask is whether the split pays for itself, and the only honest answer is a number.

(Deliberately no current module count here. ADR 0009's dated correction distinguishes the app's
dependency closure from the whole configured build. Neither number substitutes for measuring build
cost, and neither is a permanent property of a changing graph.)

## Decision

**Build time is budgeted in `config/build-time-budget.txt`, measured by `make build-budget`, and
checked by `scripts/check-build-budget.sh`. It runs locally and deliberately. It is not a CI lane.**

This mirrors `make benchmark-check`, which is also absent from every workflow for the same reason:
a measurement taken in an environment that does not control for hardware is not a measurement.

## Why this is not a CI lane

Not primarily cost. A converged run is 15–20 minutes, which is affordable if it bought anything.

It is validity. **ADR 0009 already measured this effect on this repository's own CI**: across 19
master runs every lane correlated r = 0.93–0.99 with Detekt, a lane no test change can affect. Lane
duration is mostly a function of which runner the job drew. A CI build-time gate would therefore be
a runner-assignment detector with a build-time label on it, and the failure mode is worse than
having no gate — it would go red for reasons no one can act on, and be disabled within a month.

The local numbers below reinforce it independently: even on one quiet machine, `clean_build_cold`
ranged 24.4s–92.1s across ten iterations. Adding shared-runner variance on top of that is not a
signal anyone can threshold.

A deterministic, machine-independent CI check was considered — executed-task count or task-graph
size for a fixed change. Rejected for this ADR: it catches "a plugin added 200 tasks" and is blind
to incremental compilation breaking, which is the actual risk. Graph-shape enforcement belongs in
`:konsist`, not here.

## The latest baseline

Measured 2026-09-06 on the same Apple M1 Pro (8 cores, 16 GB, macOS 26.5, JDK 24), using Gradle
9.7.1 and the settings `make build-budget` uses — 6 warm-ups and 10 measured iterations per
scenario.

| Scenario | median | min | max | spread | stdev |
|---|---|---|---|---|---|
| `clean_build_cold` | 26.8s | 24.4s | 92.1s | 253% | 20.7 |
| `clean_build_warm` | 8.7s | 3.8s | 14.6s | 125% | 4.0 |
| `incremental_leaf` | 3.0s | 2.5s | 3.5s | 33% | 0.3 |
| `incremental_deep` | 2.8s | 2.5s | 3.5s | 35% | 0.3 |
| `unit_tests` | 4.6s | 4.1s | 6.7s | 56% | 0.8 |

On 2026-09-06, the median clean build took 27s with caches disabled and 9s with warm caches.
These historical measurements do not establish build times for the current graph or toolchain.

The previous baseline, measured 2026-08-07, was `37.3s / 4.3s / 2.3s / 2.5s / 5.6s` for the
same five scenarios in table order. The change is retained here as dated history rather than
treated as a regression signal: the cold and warm samples are noisy, machine-specific measurements.

### The incremental ratio does not measure model changes

`incremental_deep` was added to compare an API change in `:beerdomain:api` with one in `:app`.
The historical run measured a ratio of 0.95x, or 2.8s against 3.0s.

Correction from the 2026-10-27 audit: inspecting the profiler's temporary Kotlin edit showed that
`apply-abi-change-to` appends an unused top-level function to the selected file. Selecting `Beer.kt`
does not change the `Beer` class, its constructor, or a member referenced by consumers.
Kotlin's member-level classpath snapshots can avoid recompilation of consumers unaffected by that
addition. See [Kotlin incremental compilation](https://kotlinlang.org/docs/gradle-compilation-and-caches.html).

The ratio therefore compares unused function additions in two modules. It does not establish
the cost of changing a consumed model signature, the number of consumers recompiled, or the
benefit of splitting or consolidating modules. The earlier inference that fixed overhead dominates
all incremental model changes was too broad.

The scenario tasks are also unscoped. `assembleDebug` assembles every matching Android project,
including the catalog and dev app. The table is not a measurement of `:app:assembleDebug` alone.

Keep module boundaries justified by their responsibilities and enforced invariants. Before changing
them for build speed, measure the affected workflow. For a model-change claim, change a consumed
member or constructor and record the downstream compilation tasks that execute.

A diagnostic probe on 2026-10-27 temporarily added `val buildAuditMarker: Int = 0` to the `Beer`
primary constructor. After a baseline build, `make build MODULE=:app GRADLE_RUNNER=./gradlew`, with
`GRADLE_USER_HOME` set to the checkout's `gradle-user-home`, executed 20 Kotlin compilation tasks
across 19 modules, including the changed domain module. The build reused its configuration cache
and took 22.2s. The model was then restored. This single observation confirms that a consumed model
change can cause downstream compilation. It is not a new timing baseline or evidence that the
proposed module mergers would improve it.

## Why the scenarios changed

The three pre-existing scenarios each measured something other than their name, and all three
defects were the same class — a measurement that looks plausible and is inert.

- **`clean_build` did not measure a clean build.** `clean` deletes the build directory. It leaves
  the build cache (1.4 GB in a project-local Gradle home) and the configuration cache (50 MB in
  `.gradle/`) fully warm. Split into `clean_build_cold`, which disables both by flag — deleting is
  slow, destructive and unreproducible, whereas flags are none of those — and `clean_build_warm`,
  which is what a developer actually experiences after `make clean`.
- **`incremental_build` measured the cheapest possible case**, as described above.
- **`unit_tests` measured Gradle startup.** With no mutator, `testDebugUnitTest` is up-to-date after
  the warm-up, so every measured iteration was a no-op. Confirmed: it reported **545 ms**. It now
  applies a non-ABI change to a file its tests exercise. `--rerun-tasks` was rejected as the fix —
  it drags in a full recompile and would measure something different again.

Recording these because the symptom in every case was a number that looked fine.

## Cost accepted

**The absolute budgets are machine-specific.** They are calibrated on one laptop and a slower
machine can fail them with nothing wrong. Mitigated two ways, not solved: budgets sit at roughly
3–4x the measured median and comfortably above the worst of ten iterations; and the `ratio` check
between two scenarios in the same run reduces shared hardware variance. It does not eliminate all
hardware or workload effects. The separate startup budget is now 500 ms on physical devices, as
documented in `scripts/check-benchmark-budget.sh`, not the earlier emulator-derived 3000 ms policy.

**A local gate depends on someone running it.** Unlike the coverage floor, nothing forces this
before a merge. Accepted for the same reason `make benchmark-check` is accepted: a number that is
occasionally checked and true beats a number that is continuously checked and meaningless.

## Consequences

- `make build-budget` measures and checks; `make build-budget-check` re-checks the last measurement
  without re-measuring. Both are local-only.
- `profile-out/` is gitignored, so the raw CSV does not survive. The table above is the record.
- The check fails if a scenario has no measured builds, because an empty sample cannot prove the
  scenario ran. It reports median, sample count and spread when samples exist, then skips a scenario
  with no budget line. Adding a scenario therefore does not silently budget it.
- **There is deliberately no "you have headroom, tighten this" nudge**, unlike `coverage-check.sh`.
  Coverage should ratchet up; a build-time budget should not ratchet down, because headroom here is
  policy rather than reclaimable slack.

## When to revisit

- **If `incremental_deep / incremental_leaf` ever trips 3x**, inspect executed tasks and their
  invalidation reasons. This threshold covers unused function additions, not every model ABI edit.
- **If per-build overhead becomes the complaint**, measure configuration time directly
  (`gradle-profiler --measure-config-time`) before touching the module graph. The incremental ratio
  alone does not distinguish configuration, task checks, and compilation costs.
- **After a major Gradle, AGP or Kotlin bump**, re-measure and update the table. Nothing forces this
  — that is the acknowledged cost of a local gate — so it needs a trigger, and those bumps are the
  right one: they can change both configuration and compilation costs.
- **If a shared build cache or build scans are ever adopted**, re-measure `clean_build_warm` first:
  it is the scenario a remote cache would change most, and its 207% spread today makes it the
  weakest number in the table.
