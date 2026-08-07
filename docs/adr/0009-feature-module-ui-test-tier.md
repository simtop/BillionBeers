# 0009: Instrumented UI tests live in feature modules, not only in `:app`

## Status

Accepted. PRs #137 (the two convention defects that blocked this), #138 (the tier, `:feature:beerslist`,
invariant 10), and the PR adding `:testing-utils-android` and `:feature:beersearch`.

## Context

Every instrumented test lived in `:app`. That was never a considered choice — it was where the
first one was written, and nothing made it possible to write one anywhere else. Two defects in the
convention plugins meant a second module *could not* host instrumented tests at all:

- `billionbeers.android.screenshot` fed its generated Paparazzi runner into every compile task
  matching `"Test"`, which includes `compileDebugAndroidTestKotlin`. Any screenshot-enabled module
  that grew an `androidTest` source set failed with `Unresolved reference 'Paparazzi'`.
- `PROJECT_TEST_RUNNER` named `com.simtop.billionbeers.di.MockTestRunner`, a class in
  `app/src/androidTest`. Every other module produced a test APK naming a runner it did not contain
  and died on device with `ClassNotFoundException`. `:beer_database` had privately worked around
  this; nobody had fixed the default.

Both are fixed (#137). The question this ADR settles is what to do with the possibility.

Running an instrumented test from `:app` means assembling the whole application first — base module
and both dynamic-feature splits — before a single assertion executes. A test in
`:feature:beersearch` builds that module's own graph and nothing else. Gradle's up-to-date checks
and build cache then supply incrementality for free.

> **Correction, 2026-08-07.** This paragraph originally said "the 27-module graph behind them" and
> "needs three modules". Both were wrong, and measured against
> `./gradlew <project>:dependencies --configuration debugRuntimeClasspath` the real figures are
> **16 and 9**: `:app`'s own closure is 13 projects, plus `:app` and the two dynamic features;
> `:feature:beersearch` resolves 8 projects plus itself. So the gap is roughly 1.8x by module
> count, not the 9x the old numbers implied.
>
> The decision is unaffected, because it never rested on those counts. The load-bearing evidence is
> the profiling table below — ~49s of fixed per-module overhead against ~2s per test, measured on a
> device rather than inferred from the graph. Module count is also a poor proxy for build cost:
> `:app` additionally dexes, packages and bundles two splits, which `:feature:beersearch` does not,
> and ADR 0011 later measured that fixed per-build overhead dominates compilation at this scale
> anyway.

## Decision

**Single-screen behaviour that requires a real device is tested in the feature module that owns the
screen. Cross-feature and app-assembly behaviour stays in `:app`.**

Feature modules opt in with `billionbeers.android.feature.uitest`, which supplies the managed
device, the Compose test rules, `:beerdomain:fakes` and `:testing-utils-android`.

The split, concretely:

| Concern | Home | Why |
|---|---|---|
| Cross-feature navigation, back stack | `:app` | The nav host is there, and invariant 2 forbids feature↔feature deps |
| Dynamic-feature install gate | `:app` | Needs the bundle and the fake `SplitInstallManager` |
| Single-screen behaviour needing real layout, IME, or focus | the feature module | Cheap to build, and the module owns the screen |
| Room migrations | `:beer_database` | Already there |
| Anything a JVM test can prove | `src/test/` | Faster, no emulator |
| Anything about a static rendering | Paparazzi | Faster, no emulator |

Invariant 10 (`InstrumentedTestOptInBoundaryTest`) enforces that a module with `src/androidTest/`
actually opts into the managed device — otherwise its tests compile, read as coverage, and never
run. `:benchmark:*` is exempt: benchmarks declare `AndroidBenchmarkRunner`, build against
`testBuildType = "release"` and suppress the `EMULATOR` error class, because a measurement taken on
a managed virtual device is meaningless.

## The alternative: keep everything in `:app`

This was seriously considered and is not unreasonable. Recorded so it does not have to be
re-derived.

**For:** one place to look. One test APK, so no per-module fixed cost (see below). No convention
plugin, no robots module, no new invariant. The three tests that existed were *already correctly
placed* — all three are cross-feature or install-gate concerns that cannot move down, so a
"migration" was never the argument.

**Against, and why it lost:** the tests that justify the tier are the ones that *did not exist*.
There was no cheap place to put a device-dependent single-screen test, so nobody wrote one, and the
gap was a consequence of the architecture rather than an oversight. `BeersListScreen`'s
`footer !is PagedListFooter.Retry` guard — a documented rule about not re-firing a load the user
was just told had failed — had no test anywhere until the tier existed. Neither did the search
screen's auto-focus. Both are now covered, and both were verified by mutation: removing the
production behaviour fails exactly one test.

## Consequences

**The instrumented lane gets slower, not faster.** CI runs `ciGroupDebugAndroidTest` across every
opted-in module, so each addition costs another APK build and install. This tier is not a CI
speedup and should never be sold as one. It buys correct placement, fast local iteration, isolation
from app assembly, and shardability.

**Cost scales with modules, not tests.** Profiled on one device boot (2026-08-06):

| Module | Device task | Tests | Test execution |
|---|---|---|---|
| `:app` | 62.3s | 14 | — |
| `:beer_database` | 45.4s | 6 | — |
| `:feature:beerslist` | 53.1s | 2 | 3.7s |

Of beerslist's 53.1s, only 3.7s is test execution; the remaining ~49s is fixed per-module overhead —
test-APK build, install, instrumentation start, teardown. So one more *test* in a module already in
the tier costs ~2s, while one more *module* costs ~49s: a factor of about 27.

**The practical rule that falls out: concentrate tests in modules already in the tier, and add
modules deliberately.** Two tests each across six feature modules would spend ~300s of overhead to
run twelve tests worth ~23s.

**Dynamic features cannot use the tier.** `:feature:beerdetail` and `:feature:beerbrowse` are
on-demand modules, and invariant 5 exists because resources declared inside them crash instrumented
tests — a failure this project has hit twice. Their UI coverage stays in `:app`, where the install
gate lives anyway. This hole is permanent unless the dynamic-feature resource problem is solved.

## When to revisit

- **Sharding the GMD group across matrix runners** becomes worth it at roughly five or six opted-in
  modules. Below that, per-shard fixed cost (checkout, Gradle setup, emulator boot) exceeds the
  serial device work it would parallelise — at three modules that work is ~161s total.
- **If the dynamic-feature resource crash is ever fixed**, `beerdetail` and `beerbrowse` become
  candidates and the `:app` integration tier shrinks.

## Note on measuring any of this

Do not evaluate a change to the instrumented lane by comparing CI run durations. Across 19 master
runs every lane correlates r = 0.93–0.99 with Detekt, which no test change can affect: a lane's
duration is mostly which runner the run drew. The instrumented baseline is a 3m42s median with a
65s standard deviation and a 3m05s–7m21s range, so detecting a ~50s change needs about ten runs per
side, and normalising against the unaffected lanes only gets that to six. Profile locally
(`--profile --rerun`) instead — that is where the numbers above come from.
