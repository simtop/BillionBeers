# BillionBeers Health Report — Historical Sample

> [!WARNING]
> This is a historical example captured on **2026-07-23**, not the repository's current health.
> Download the latest `health-report` artifact or read the Step Summary from the
> [Weekly Health Report workflow](https://github.com/simtop/BillionBeers/actions/workflows/health-report.yml).
> Run `make health` to generate current local reports under `build/reports/health/`.

_Generated 2026-07-23 21:26 UTC_ · retained only to show the report format that existed when health reporting was introduced.

| Check | Metric | Status | What to do |
|---|---|---|---|
| Android Lint backlog | 54 baselined | 🟡 burn down | top: UnusedResources×35 PluralsCandidate×5 IconLocation×4 · `make android-lint`, fix, re-baseline |
| Detekt findings | 20 | 🟡 review | `make lint`; baseline or fix |
| Compose unstable params | 1 | 🟢 good | 1 = the framework Uri?, expected |
| Line coverage | 51.2% | ℹ️ | raise covered paths; see jacocoRootReport |
| Dependency graph | 211 deps locked | 🟢 guarded | drift fails CI; re-baseline intentionally |

> Legend: 🟢 healthy · 🟡 backlog to burn down · ℹ️ informational · ⚪ not measured this run.
