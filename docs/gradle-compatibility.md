# Gradle compatibility inventory

The project treats Gradle warnings as an inventory, not as noise. Run:

```text
./gradlew help --no-configuration-cache --warning-mode all --console=plain
```

## Current status

As of 2026-08-23, the repository-owned Gradle 9.6 warnings are fixed:

- configuration creation uses `configurations.create(...)` rather than the deprecated Kotlin DSL
  delegated-property form;
- Android source directories use the AGP `directories` set rather than `srcDir(...)`;
- JaCoCo variant names use `replaceFirstChar` rather than Kotlin `capitalize()`;
- Detekt and lint baselines use `layout.projectDirectory.file(...)`;
- project dependencies use `DependencyHandler.project(...)` explicitly (`this.project(...)` inside a
  `dependencies {}` block).

Two warnings remain and are upstream contracts:

| Warning | Owner | Reopen trigger |
|---|---|---|
| `ReportingExtension.file(String)` from `billionbeers.detekt.gradle.kts` | Detekt Gradle plugin | Upgrade to a Detekt release that no longer calls this deprecated Gradle API; remove the inventory entry and verify with `--warning-mode all`. |
| Project-object dependency notation from `baselineProfile { from(...) }` | AndroidX Baseline Profile Gradle plugin | The consumer extension currently exposes `from(Project)` only. Replace it with the path/Provider overload when AndroidX publishes one. |

Metro's IDE-support/Delicate API notices and the Java `URL(String)` notice in the unused-dependency
scanner are compiler warnings, not Gradle deprecations. They remain separately owned: update the
Metro opt-in when its API changes, and migrate the scanner to `URI`/`HttpClient` when its minimum
JDK and network behavior are deliberately revisited.

This file should be updated in the same change as a Gradle/AGP/Detekt/AndroidX upgrade. A clean
warning report is a release criterion for moving to Gradle 10, not a reason to suppress warnings.

## AGP/Kotlin compatibility properties

`gradle.properties` retains five AGP/Kotlin compatibility settings while the project moves through
the AGP 9 migration: two built-in-Kotlin coexistence suppressions, `android.uniquePackageNames=false`,
`android.dependency.useConstraints=false` and `android.r8.strictFullModeForKeepRules=false`. Each
comment names the owning tool and the behavior that must be exercised before removing it.

Run the reproducible removal probe with:

```text
make check-gradle-compatibility-flags
```

The probe copies the checkout to a temporary directory, removes one logical exception at a time,
and exercises the relevant behavior: full-graph configuration, debug assembly, release dependency
resolution or minified release-smoke assembly. A passing probe demonstrates build compatibility on
the current toolchain; it does not by itself approve a changed dependency graph or prove every
future Kotlin/AGP combination. The monthly forward-compatibility workflow runs the same probe with
both the supported wrapper and Gradle nightly.

## Automated forward-compatibility check

`.github/workflows/weekly-build-tool-compat.yml` runs monthly and on demand. It configures
the full project with the checked-in Gradle wrapper, runs the convention-plugin contract tests, and
repeats the same smoke check with Gradle nightly. The nightly job is intentionally outside the
required PR gate: it is an early signal for Gradle/AGP changes, not a claim that nightly is a
supported release. Both runs use `--warning-mode all`; `scripts/check-gradle-warnings.sh` fails on
new deprecations while allowing only the two upstream warnings listed above.
