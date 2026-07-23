# BillionBeers Health Report

_Generated 2026-07-23 20:05 UTC_ · read-only checks only. Regenerate with `make health`.

| Check | Metric | Status | What to do |
|---|---|---|---|
| Android Lint backlog | 54 baselined | 🟡 burn down | top: UnusedResources×35 PluralsCandidate×5 IconLocation×4 · `make android-lint`, fix, re-baseline |
| Detekt findings | 20 | 🟡 review | `make lint`; baseline or fix |
| Compose unstable params | 1 | 🟢 good | 1 = the framework Uri?, expected |
| Line coverage | n/a | 🔴 broken | `jacocoRootReport` fails: benchmark variant wants a non-existent testBenchmarkReleaseUnitTest — repair to restore coverage |
| Dependency graph | n/a | ⚪ | `make dependency-guard-baseline` |

> Legend: 🟢 healthy · 🟡 backlog to burn down · ℹ️ informational · ⚪ not measured this run.
