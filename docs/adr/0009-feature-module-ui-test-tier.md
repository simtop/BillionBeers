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

**Dynamic features now use the tier without owning user-facing resources.**
`:feature:beerdetail` and `:feature:beerbrowse` have module-local screen tests, while invariant 5
keeps the strings those tests need in `:presentation_utils`. The resource boundary, not dynamic
feature status itself, was the constraint. Cross-feature navigation and split-install behaviour
still stay in `:app`.

**The six-module threshold for CI sharding was reached and evaluated, but the two-way matrix was
rejected.** PR #200 tested this split:

- `integration`: `:app`, `:feature:beerbrowse`, `:feature:beerdetail`, and the standalone
  `:app-release-smoke` release task;
- `standalone`: `:beer_database`, `:feature:beerslist`, and `:feature:beersearch`.

Stable aggregate semantics and report coverage worked, but performance did not:

| Run | `integration` | `standalone` | Lane wall clock | Total job work |
|---|---:|---:|---:|---:|
| `33597137353` | 11m54s | 6m37s | 11m59s | 18m34s |
| `33598554628` | 10m33s | 6m27s | 10m38s | 17m03s |

Against the ten-run `master` median of 9m36s, the candidate median was about 17.8% slower and used
about 85.5% more runner time. Reassigning modules cannot remove the duplicated fixed costs; moving a
dynamic feature would also duplicate more app assembly, while moving release smoke trades shared
build outputs for nominal balance. Keep one job running `ciGroupDebugAndroidTest` followed by release
smoke.

The single instrumented artifact includes both the HTML report and raw managed-device JUnit/logcat
outputs. Those paths preserve the owning module, and failed test cases retain their class and method,
so a failure is diagnosable without a matrix.

**The remaining single job was profiled before considering a device cache.** PR #206 added
timing-only Gradle profiles without changing the tasks, their order, or the gate contract. Six live
samples met the normalized-run threshold:

| Run | Debug managed device | Release smoke | Measured phases | Whole job | Detekt | Job / Detekt |
|---|---:|---:|---:|---:|---:|---:|
| `33687414992` | 5m07s | 47s | 5m54s | 6m27s | 50s | 7.74x |
| `33688959440`, attempt 1 | 5m09s | 45s | 5m54s | 6m32s | 44s | 8.91x |
| `33688959440`, attempt 2 | 4m25s | 33s | 4m58s | 5m41s | 1m02s | 5.50x |
| `33688959440`, attempt 3 | 4m13s | 35s | 4m48s | 5m33s | 1m01s | 5.46x |
| `33688959440`, attempt 4 | 4m12s | 26s | 4m38s | 5m18s | 1m24s | 3.79x |
| `33688959440`, attempt 5 | 4m37s | 34s | 5m11s | 5m46s | 1m03s | 5.49x |

The medians are 4m31s for the debug managed-device build, 35s for release smoke, 5m05s for
measured phases, and 5m44s for the whole job. The debug build is about 89% of measured phase time.
Four preserved profiles put summed `atdApi35Setup` work in a narrow 6m27s–6m55s band and the
leading `:app:atdApi35Setup` task at 1m34s–1m41s; those task durations overlap and must not be added
and reported as wall clock.

A bare system-image cache is still rejected, now on performance as well as correctness. Historical
cache-hit run `29949691071` restored its 732 MB entry in about 12 seconds and then failed
`:app:atdApi35Setup` because an AGP setup input had no value. The inspected current run downloaded
the ATD image in about 17 seconds, so even a repaired image-only cache has a theoretical saving of
roughly five seconds. A coherent SDK plus created-AVD cache is a separate experiment: it has at most
the leading setup task's ~1m40s ceiling before restore overhead, must exclude active-device locks,
must invalidate on the device definition and runner OS, and must pass a live cache-hit run. Keep it
only if the normalized whole-job gain reaches 20%; one green hit is a correctness proof, not a speed
claim.

**The coherent SDK/AVD cache was tested and rejected.** PR #208 isolated the candidate with a
run-ID key, so attempt 1 had to miss and its rerun had to hit without leaking cached device state
into ordinary CI. The first archive also included the preinstalled SDK `licenses` directory; its
restore failed because the hosted runner could not change that root-owned directory's timestamps or
mode. The corrected archive left licenses runner-provided and cached the SDK package registry,
emulator, ATD image, Android repository cache, created AVD, and AVD metadata while excluding locks.
Both corrected attempts were green:

| Run | Cache | Debug managed device | Release smoke | Measured phases | Whole job | Detekt | Job / Detekt |
|---|---|---:|---:|---:|---:|---:|---:|
| `33736989531`, attempt 1 | miss | 4m52s | 45s | 5m37s | 6m24s | 53s | 7.25x |
| `33736989531`, attempt 2 | hit | 4m54s | 43s | 5m37s | 6m27s | 53s | 7.30x |

The hit restored a 1,087,542,503-byte compressed archive in about 13 seconds. It saved no measured
phase time and made the whole job three seconds slower despite an identical same-run Detekt time.
The profiles explain why: summed setup-task work changed from about 6m45s on the miss to 7m05s on
the hit, while device-test work changed from about 4m51s to 4m33s. The restored AVD did not remove
the per-module provisioning and boot work; ordinary runner variance merely moved time between the
overlapping setup and execution tasks.

More samples would be necessary to claim a small improvement, but not to reject a candidate whose
paired normalizer and measured total are identical and whose profile shows that its intended work
was not eliminated. It is already on the wrong side of the 20% acceptance threshold. Keep the
single job with timing profiles and no managed-device cache. PR #208 remains the implementation
archive.

## When to revisit

- Reconsider sharding only after the fixed setup/device cost falls materially or the module graph
  grows enough that execution dominates it. Profile by task before choosing a new split.
- Reconsider caching only if AGP can reuse restored device state without repeating the per-module
  setup work, or if a new profile shows at least a 20% whole-job ceiling after archive restore;
  never restore `system-images` alone.
- If another module joins the tier, its managed-device convention opt-in automatically adds it to
  `ciGroupDebugAndroidTest`; no CI task list should duplicate that ownership.

## Note on measuring any of this

Do not use one or two hosted runs to claim a small speedup. Earlier measurements found every lane
correlating r = 0.93–0.99 with Detekt, which no test change can affect: runner allocation dominates
normal variance. A performance claim still needs about ten runs per side, or six when normalized
against unaffected lanes, plus local `--profile --rerun` evidence.

The rejected matrix did not depend on a precise small regression estimate: neither candidate run beat
the existing median, and duplicating the job increased measured runner work by roughly 85%. That is a
stop condition even before a larger sample.
