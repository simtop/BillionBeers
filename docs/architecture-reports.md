# Architecture reports

BillionBeers exposes two complementary architecture views. They answer different questions and should
not be treated as interchangeable.

## Gradle module graph

```text
make module-graph
make module-graph MODULE=:feature:beerslist
```

Outputs:

```text
build/reports/module-graph/modules.json
build/reports/module-graph/index.html
```

The JSON and webpage describe direct project dependencies in the configured main Gradle build. An
edge points from the **consumer to its dependency**. For example,
`:feature:beerslist -> :beerdomain:api` means the beers-list module declares a project dependency on
the domain API module.

The viewer is self-contained and can be opened directly from `file://`. It supports:

- mouse or trackpad zoom and pan;
- module search by full or partial Gradle path;
- direct dependency and dependent highlighting;
- optional transitive traversal in both directions;
- a focused neighborhood that hides unrelated modules;
- filters for `main`, `test`, `androidTest`, `benchmark`, and `tooling/other` edges;
- raw declaring configurations in the selection details;
- strongly connected component warnings when the real graph is not acyclic.

Passing `MODULE=` does not create a partial or different report. Make prints the same report URL with
a fragment that selects and focuses that module, so the underlying JSON remains deterministic.

### JSON contract

`modules.json` has a versioned, deterministic schema:

- `schemaVersion` — currently `1`;
- `rootProject` — the configured build name;
- `includedBuilds` — composite-build names, with an explicit flag showing that their internal
  projects are not included;
- `nodes` — Gradle path, display name, repository-relative directory, and broad plugin-derived kind;
- `edges` — source, target, merged and sorted declaring configurations, and coarse scopes;
- `cycles` — sorted strongly connected components containing at least two real modules;
- `summary` — node, edge, cycle, fan-in and fan-out counts, plus project `api` edge count.

The report uses Gradle's configured `ProjectDependency` model. It does not parse build-script text,
resolve external libraries, infer relationships from imports, or include synthetic container
projects that have no build script. Self-referential project dependencies are excluded because AGP
creates them in generated test configurations and they do not describe module architecture. Repeated
declarations of the same source/target pair are merged while preserving all configuration names.

Cycles are facts about the selected whole configured graph, not automatically architecture defects.
For example, Android dynamic-feature delivery creates structural app/feature relationships, and test
fixture edges can create a cycle only when test scopes are included. Use the scope filters and raw
configurations before deciding whether a component is problematic.

The model and generator deliberately avoid Android and Metro APIs. They live in build logic today for
simple repository integration, but can later be extracted into a standalone Gradle plugin for other
projects without changing the JSON contract.

## Metro dependency-injection graph

```text
make metro-graph
```

Outputs:

```text
app/build/reports/metro/analysis.json
app/build/reports/metro/html/index.html
```

This is Metro's graph-analysis report. It describes dependency-injection bindings, entry points,
scopes, and injection paths inside `:app`; it does **not** describe Gradle module dependencies.

Metro graph reporting is intentionally opt-in. The Make target supplies Metro's documented
`metro.reportsDestination` property and reruns the report tasks because graph metadata generation is
verbose and adds work that normal builds do not need.

## Generate both

```text
make architecture-report
```

Use the Gradle report to understand project boundaries and dependency direction. Use the Metro report
to investigate how an object is provided through the assembled application graph. The visualization
itself does not guess or enforce an allowlist. The checked-in
`config/architecture/project-dependency-policy.json` is the authority for production dependency
direction. Run `make architecture-policy` to resolve each module's compile classpath and fail on
forbidden direct or transitive project edges. Role rules match architectural path patterns, so
adding `:feature:new-screen` does not require editing a feature/package list. Every production
project `api(...)` edge also needs an explicit entry in `allowedApiEdges`, which makes API exposure
growth visible in review.
