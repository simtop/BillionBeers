# BillionBeers Health Report

_Generated 2026-07-23 21:26 UTC_ · read-only checks only. Regenerate with `make health`.

| Check | Metric | Status | What to do |
|---|---|---|---|
| Android Lint backlog | 54 baselined | 🟡 burn down | top: UnusedResources×35 PluralsCandidate×5 IconLocation×4 · `make android-lint`, fix, re-baseline |
| Detekt findings | 20 | 🟡 review | `make lint`; baseline or fix |
| Compose unstable params | 1 | 🟢 good | 1 = the framework Uri?, expected |
| Line coverage | 51.2% | ℹ️ | raise covered paths; see jacocoRootReport |
| Dependency graph | 211 deps locked | 🟢 guarded | drift fails CI; re-baseline intentionally |

> Legend: 🟢 healthy · 🟡 backlog to burn down · ℹ️ informational · ⚪ not measured this run.
