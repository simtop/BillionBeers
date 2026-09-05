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
ranged 29.7s–59.3s across ten iterations. Adding shared-runner variance on top of that is not a
signal anyone can threshold.

A deterministic, machine-independent CI check was considered — executed-task count or task-graph
size for a fixed change. Rejected for this ADR: it catches "a plugin added 200 tasks" and is blind
to incremental compilation breaking, which is the actual risk. Graph-shape enforcement belongs in
`:konsist`, not here.

## The baseline

Measured 2026-08-07 on an Apple M1 Pro (8 cores, 16 GB, macOS 26.5, JDK 24) with the settings
`make build-budget` uses — 6 warm-ups and 10 measured iterations per scenario.

| Scenario | median | min | max | spread | stdev |
|---|---|---|---|---|---|
| `clean_build_cold` | 37.3s | 29.7s | 59.3s | 79% | 9.6 |
| `clean_build_warm` | 4.3s | 2.5s | 11.5s | 207% | 2.8 |
| `incremental_leaf` | 2.3s | 1.8s | 3.7s | 81% | 0.6 |
| `incremental_deep` | 2.5s | 2.0s | 3.4s | 58% | 0.5 |
| `unit_tests` | 5.6s | 4.9s | 6.6s | 32% | 0.6 |

**A clean build with no caches is 37s; with warm caches it is 4s.** That is the answer to the
question this ADR exists to make answerable.

### The finding that contradicts the scenario's own premise

`incremental_deep` was added specifically because the pre-existing incremental scenario applied its
ABI change to `MainActivity.kt` in `:app`, which nothing depends on — the cheapest possible
incremental build, presented as the representative one. The replacement changes `Beer` in
`:beerdomain:api`, which ten modules depend on.

The expectation was that the deep change would be substantially more expensive, and that the gap
would quantify what modularization buys. **It measured 1.11x — 2.5s against 2.3s.**

The honest reading is not "incremental compilation is extraordinarily good." It is that at ~2.4s per
build, **fixed per-build overhead dominates and compilation is not what you are paying for.**
Configuration, task-graph construction and up-to-date checking swamp the difference between
recompiling one module and recompiling eleven.

Two consequences follow, and both cut against instinct:

- **Further module splitting will not make incremental builds faster.** There is no compilation time
  left to parallelise away at this scale. Modules must be justified by boundaries and enforced
  invariants — which is exactly how ADR 0009 justifies the UI-test tier — never by build speed.
- **The lever, if build time ever becomes a problem, is per-build overhead**: configuration cache
  hit rate, plugin configuration cost, task count. Not the graph.

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
- The check reports median, sample count and spread per scenario, and skips any scenario with no
  budget line rather than failing. Adding a scenario therefore does not silently gate it.
- **There is deliberately no "you have headroom, tighten this" nudge**, unlike `coverage-check.sh`.
  Coverage should ratchet up; a build-time budget should not ratchet down, because headroom here is
  policy rather than reclaimable slack.

## When to revisit

- **If `incremental_deep / incremental_leaf` ever trips 3x**, the question is what stopped being
  incremental — an annotation processor without incremental support, a plugin forcing full
  recompilation — not which module got slower.
- **If per-build overhead becomes the complaint**, measure configuration time directly
  (`gradle-profiler --measure-config-time`) before touching the module graph. This ADR's central
  finding is that the graph is not where the time goes.
- **After a major Gradle, AGP or Kotlin bump**, re-measure and update the table. Nothing forces this
  — that is the acknowledged cost of a local gate — so it needs a trigger, and those bumps are the
  right one: they move per-build overhead, which this ADR identifies as the dominant term.
- **If a shared build cache or build scans are ever adopted**, re-measure `clean_build_warm` first:
  it is the scenario a remote cache would change most, and its 207% spread today makes it the
  weakest number in the table.
