# Heavy CI lane sharding experiment

> Experiment archive only. This branch and pull request are deliberately not intended to merge.
> Production keeps the single screenshot and managed-device jobs because the candidate missed its
> performance thresholds.

## What this branch preserves

This branch is the exact two-way matrix topology exercised by GitHub Actions runs `33597137353` and
`33598554628`, plus a seven-day retention policy for its reports:

- static matrices with `fail-fast: false` and `max-parallel: 2`;
- stable aggregate job IDs and names consumed by verdict adoption and `CI Gate`;
- exhaustive explicit task assignment for seven Paparazzi modules / 273 goldens;
- exhaustive explicit task assignment for six managed-device debug modules plus release smoke;
- per-shard JUnit, screenshot-failure, managed-device HTML/JUnit/logcat artifacts;
- host-native tested APK ABI (`x86_64` on Linux CI, `arm64-v8a` on Apple Silicon).

Local commands remain unsharded. The matrices are CI orchestration only.

## Screenshot topology

| Shard | Tasks | Goldens |
|---|---|---:|
| `browse-detail` | `:feature:beerbrowse`, `:feature:beerdetail`, `:catalog` | 125 |
| `design-list-search` | `:core:designsystem`, `:feature:beersearch`, `:feature:beerslist`, `:presentation_utils` | 148 |

The stable aggregate is `screenshot-tests` / `Screenshot Tests (Paparazzi)`.

| Run | `browse-detail` | `design-list-search` | Lane wall clock | Total job work |
|---|---:|---:|---:|---:|
| `33597137353` | 4m15s | 4m28s | 4m34s | 8m47s |
| `33598554628` | 4m22s | 4m15s | 4m30s | 8m42s |

Against the ten-run `master` median of 5m18s, candidate median wall clock improved about 14.5% while
runner work increased about 65%. The default keep thresholds were at least 20% faster and less than
50% additional work.

The assignments were already within 13 seconds and then 7 seconds. A greedy repartition cannot fix
the duplicated checkout, LFS, JDK/Gradle setup, and shared compilation cost.

## Managed-device topology

| Shard | Tasks |
|---|---|
| `integration` | `:app`, `:feature:beerbrowse`, `:feature:beerdetail`, `:app-release-smoke` |
| `standalone` | `:beer_database`, `:feature:beerslist`, `:feature:beersearch` |

The stable aggregate is `instrumented-tests` / `Instrumented Tests (Gradle Managed Device)`.

| Run | `integration` | `standalone` | Lane wall clock | Total job work |
|---|---:|---:|---:|---:|
| `33597137353` | 11m54s | 6m37s | 11m59s | 18m34s |
| `33598554628` | 10m33s | 6m27s | 10m38s | 17m03s |

Against the ten-run `master` median of 9m36s, candidate median wall clock was about 17.8% slower and
runner work increased about 85.5%.

A basic greedy algorithm is not valid for these tasks because their costs are not independent:
feature tasks share app assembly, while release smoke shares release/app outputs on the integration
runner. Moving work to equalize visible durations can duplicate more of the graph.

## Failure visibility

- Each screenshot shard summary identifies failed modules.
- `screenshot-failures-<shard>` preserves failure/delta PNGs whose names include the exact snapshot
  or generated preview configuration.
- `screenshot-test-results-<shard>` preserves JUnit class/method results.
- `instrumented-test-reports-<shard>` preserves module-rooted HTML, JUnit, test logs, and per-test
  logcat files.
- The aggregate propagates failed/cancelled matrix conclusions; `CI Gate` still depends only on the
  stable aggregate.

All artifacts expire after seven days.

## Reusing this experiment

Before retrying:

1. rebase this branch's workflow topology onto current `master`;
2. regenerate the task-union checks from repository source trees/convention application;
3. profile incremental cost inside the proposed shard graph rather than sorting independent task
   durations blindly;
4. run at least ten representative samples, or six normalized samples;
5. keep only if wall-clock and runner-work thresholds both pass;
6. deliberately fail one task in each matrix and cancel one shard to re-prove aggregate and artifact
   behavior.

Do not use this branch as precedent for unit-test sharding. That lane needs a JaCoCo artifact-merge
design and a mutation proving incomplete coverage cannot remain green.
