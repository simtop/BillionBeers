# 0001: Test fixtures live in sibling `:module:fixtures` Gradle modules

## Status

Accepted

## Context

The `:testing-utils` module extraction needed a home for two sets of test fixtures that had been
living in a shared `TestingConstants.kt`:

- Domain fixtures (`fakeBeerModel`, `fakeBeerListModel`, `fakeException`) — types owned by
  `:beerdomain:api`.
- Network fixtures (`fakeBeersApiResponseItem`, `fakeBeerApiResponse`, `FAKE_JSON`) — types owned
  by `:beer_network`.

Gradle's `java-test-fixtures` plugin (and its AGP equivalent, `android { testFixtures { enable =
true } }`) is the standard tool for exactly this: it publishes a `testFixtures` source set as a
consumable variant of the owning module, so `testImplementation(testFixtures(project(":x")))` is
all a consumer needs.

## Decision

Use plain sibling Gradle modules instead — `:beerdomain:fakes` (already existed) and
`:beer_network:fixtures` (added here) — rather than the `java-test-fixtures`/AGP `testFixtures`
mechanism.

## Why

Locally benchmarked: enabling `testFixtures` on a module in this project's current Gradle/AGP
setup adds measurable configuration and build-graph overhead (an extra variant, extra dependency
resolution, extra task-graph nodes) versus a plain sibling module consumed via a normal
`api`/`implementation` project dependency. The sibling-module approach gives the identical
consumption story (`testImplementation(project(":beer_network:fixtures"))`) without that cost.

## Consequences

- Every module that owns types needing fixtures gets a small sibling module (`:x:fakes` or
  `:x:fixtures`) rather than a `testFixtures` source set inside `:x` itself. The root
  `settings.gradle.kts` auto-discovers directories with build scripts, so a new fixture module
  needs no manual `include(...)` entry. The separate `build-logic` composite remains explicit.
- Revisit if a future Gradle/AGP release closes the performance gap — re-benchmark before
  reverting this decision, don't assume it's fixed.
- The sibling module must use the same plugin (`billionbeers.android.library` vs.
  `billionbeers.jvm.library`) as the module it fixtures for. A pure-JVM fixtures module cannot
  depend on an Android library module — Gradle can't pick between the `debugApiElements` /
  `releaseApiElements` variants without an Android consumer, so `:beer_network:fixtures` is
  `billionbeers.android.library` even though its one file has no Android dependency, mirroring
  `:beer_network` itself.
