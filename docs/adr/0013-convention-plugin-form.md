# 0013: Convention plugins stay precompiled script plugins, and the alternative gets measured before it gets adopted

## Status

Accepted.

## Context

The build's conventions live in `build-logic/convention/src/main/kotlin` as **precompiled script
plugins** — sixteen `billionbeers.*.gradle.kts` files whose plugin id is their filename. Two
**binary plugins** (`DuplicateClassesPlugin`, `UnusedDependenciesPlugin`) sit beside them as
ordinary `Plugin<Project>` classes, so the build already runs both forms and the wiring for either
is proven.

This is not the original shape. The project moved *from* binary plugins *to* precompiled scripts
deliberately, on the grounds that they are simpler, easier to maintain, and faster. That history is
the reason this ADR exists: without it written down, the reverse migration looks like an obvious
improvement to anyone reading the code fresh, and it was in fact proposed on exactly that basis in
August 2026.

The case for converting back is real but narrower than it first appears:

- **The Gradle 9 accessor workaround would disappear.** Six conventions apply their siblings with
  `apply(plugin = "billionbeers.…")` instead of a `plugins { }` block, to route around an
  accessor-generation bug. Binary plugins generate no accessors, so the bug cannot apply and the
  workaround could be deleted.
- **Testing ergonomics can differ.** Binary classes offer a direct unit-test surface, but consumer
  behavior can be tested with TestKit in either form. The lack of a suite when this ADR was first
  written was a coverage gap, not a limitation of precompiled scripts.
- **Build/configuration cost needs a local measurement.** Precompiled script classes are build
  outputs of the convention build, not classes regenerated on every consumer configuration.
  Accessor generation, compilation and consumer configuration must be measured separately;
  another project's conversion benchmark does not establish the cost here.

Two things weaken that third point specifically, which is the one usually used to justify the
migration:

1. This build has `org.gradle.configuration-cache=true`. On a cache *hit* the configuration phase
   is not executed at all, so the cost is not paid. It surfaces only on cache misses — CI cold
   runs, build-script edits, and `build-logic` changes.
2. The measured numbers here are not consistent with a 12-second configuration phase. ADR 0011
   records a 4s warm clean build, and `make build-budget` on 2026-08-09 measured a 3.3s warm clean
   build and 1.6s incremental. Whatever configuration costs in this project, it is a small fraction
   of that.

Against the migration: the precompiled form is what the team chose for readability and maintenance,
and one commonly-cited disadvantage of it turns out to be false. Precompiled script plugins **can**
share helper functions — a top-level declaration in a plain `.kt` file in the same source set is
visible to every script, which is how `Versions.kt` and `AndroidCommon.kt` already work. So the
"you must go binary to factor out duplication" argument does not hold, and the two largest reuse
cleanups (a shared `configureBillionBeersAndroid`, and a `billionbeers.android.testing` plugin)
were both delivered in the precompiled form with no migration at all.

## Decision

**Conventions stay precompiled script plugins.** No migration, opportunistic or otherwise.

The two existing binary plugins stay binary — they are task-and-extension implementations rather
than conventions, which is what that form is good at.

**The comparison is a measurement task, not a judgement call.** Before this is reopened, someone
should measure it on this repo rather than argue from another project's numbers:

1. Pick one convention — `billionbeers.android.testing` is the smallest and has no accessor use.
2. Convert it to a `Plugin<Project>` on a branch, registered via `gradlePlugin { plugins { … } }`.
   Consumers do not change; the plugin id is the same.
3. Measure configuration time on a **configuration-cache miss** both ways, since that is the only
   state where the difference can appear:
   `./gradlew help --no-configuration-cache` and a `build-logic`-touching run, several iterations.
4. Compare against `config/build-time-budget.txt` methodology — medians, not single runs, and on a
   machine doing nothing else (ADR 0011).

## Consequences

- The `apply(plugin = "billionbeers.…")` workaround stays, and stays commented, until either the
  Gradle bug is confirmed fixed or the measurement above justifies conversion. Flipping one back to
  a `plugins { }` block and running `./gradlew help` is the cheap way to test the former.
- **Update, 2026-09-05:** build logic now has behavioral TestKit coverage without changing plugin
  form. `ConventionPluginFunctionalTest` covers convention application, managed-device task
  discovery and configuration-cache reuse, screenshot unit-test wiring, transitive feature/data
  boundaries, coverage inputs and missing signing credentials. Architecture-policy and module-graph
  fixtures complement it. This is representative coverage, not proof of every task execution or
  toolchain combination; extend it for a concrete missing contract, not to justify conversion.
- **Reopen triggers:** a measured configuration-time difference that matters at this repo's scale;
  an actual publishing requirement that the current form cannot satisfy; or a
  Gradle release that makes precompiled scripts materially worse.

## Related

- Declarative Gradle (`.gradle.dcl`) is a separate question and further out. It replaces the
  *consumer* build files, not the build logic, and requires binary plugins registering software
  types underneath — so it sits on top of this decision rather than competing with it. It remains
  experimental, with prototype-grade AGP support, and has no expression for several things this
  build does (the baseline-profile `finalizeDsl` hook, the Paparazzi source generation, the
  auto-discovering `settings.gradle.kts`). Not before a stable release.
