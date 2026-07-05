---
name: new-dev-app
description: Scaffold AND finish a standalone app-dev-<feature> module for BillionBeers -
  runs scripts/new-dev-app.sh, then investigates the target feature module and fills in
  the generated TODOs (fake repository bindings, real screen wiring) so the result is a
  working, installable dev-app rather than a stub. Use when asked to create, generate, or
  finish a dev-app / dev sandbox for a specific feature module (e.g. "make a dev app for
  beerdetail").
metadata:
  keywords:
    - android
    - dev-app
    - module template
    - billionbeers
---

## What this produces

A second, independent `app-dev-<feature>` application module (see `app-dev-beerslist/` for the
existing reference example) that depends on only the target feature module and its fakes - no
network, no database, no other features.

**Dynamic feature modules are unsupported - not slower, actually broken.** For a dynamic feature
(`com.android.dynamic-feature`, e.g. `:feature:beerdetail`), AGP hard-rejects any application
other than its one hardcoded base app (`implementation(project(":app"))`, baked in by the
`billionbeers.android.dynamic.feature` convention plugin) from depending on it at all -
`assembleDebug` fails at the resource-linking step with "this application is not configured to
use dynamic features," even though `compileDebugKotlin` alone misleadingly succeeds first. This
was discovered the hard way trying to build `app-dev-beerdetail`; see
`docs/beerdetail_dev_app.md` and `docs/adr/0004-dev-apps-dont-support-dynamic-features.md` for
the full investigation, the error output, and possible future workarounds (mainly: extracting the
feature's pure presentational composables into a plain library module both the dynamic feature
and a dev-app could depend on - real architectural work, not attempted here).

`scripts/new-dev-app.sh` refuses to scaffold a dynamic feature module for exactly this reason -
if it errors out pointing here, that's the script working as intended, not a bug to route
around.

## Arguments

Expects: `<feature> [PascalName] [gradlePath]` - same as `scripts/new-dev-app.sh`. `feature` is
required (lowercase module suffix, e.g. `beerdetail`). If `PascalName` is omitted, derive it
yourself from the feature's actual class names (see Step 2) rather than trusting the script's
naive first-letter-capitalized default - e.g. `beerdetail` should become `BeerDetail`, not
`Beerdetail`. If the script guessed wrong, rename the generated files/package/class identifiers
to match before continuing (cheaper to fix immediately than after wiring is filled in).

## Step 1: run the generator

```
scripts/new-dev-app.sh <feature> <PascalName> [gradlePath]
```

This creates `app-dev-<feature>/` with a compiling scaffold: `build.gradle.kts`, manifest,
`Application`, `DevAppGraph`, `ViewModelMapsModule`, `SplitInstallModule`, and two files with
`TODO` markers you must fill in: `MainActivity.kt` and `di/DevFakesModule.kt`. It also registers
the module in `settings.gradle.kts`.

If the module directory already exists, the script refuses to run - decide whether to delete the
existing one first or whether the user actually wanted to finish an already-scaffolded module
(skip straight to Step 2 in that case).

## Step 2: investigate the target feature module

Before writing anything, read enough of `<gradlePath>` (default `:feature:<feature>`) to answer:

1. **What is the screen's real entry-point composable?** Find the top-level `@Composable fun
   ...Screen(...)` (or similarly named) function. Note its exact signature - required params,
   default params, and whether it takes a domain object directly (like `BeerDetail`'s `Beer`
   param) or fetches everything itself via an injected ViewModel (like `BeersListScreen`).
2. **Is this a dynamic feature? Stop here if so.** Check whether `<gradlePath>`'s
   `build.gradle.kts` applies `billionbeers.android.dynamic.feature` (or check
   `app/build.gradle.kts`'s `dynamicFeatures` set for its Gradle path). If it is one, do not
   attempt to scaffold or wire up a dev-app for it - `scripts/new-dev-app.sh` already refuses to
   generate the module for this exact reason. Tell the user it's unsupported, point them at
   `docs/beerdetail_dev_app.md` and `docs/adr/0004-dev-apps-dont-support-dynamic-features.md`, and
   stop - don't try to route around it with source-duplication or any other workaround unless the
   user explicitly asks you to attempt one (it's a real, non-trivial trade-off, not a quick fix).
3. **What repositories/dependencies does its ViewModel need?** Find the ViewModel's
   `@Inject constructor(...)` params. For each repository-shaped dependency, check whether a fake
   already exists in a sibling `:module:fakes` module (e.g. `beerdomain:fakes`'s
   `FakeBeersRepository`) - reuse it. Only write a brand-new fake if none exists, and keep it as
   small as the existing fakes (in-memory, `MutableStateFlow`-backed, no real logic).

## Step 3: fill in di/DevFakesModule.kt

Uncomment and adapt the template so it provides real fake instances for every repository the
screen's ViewModel needs, seeded with a few realistic sample values (see
`app-dev-beerslist/.../di/DevBeersRepositoryModule.kt` for the seeding style - 2-3 varied sample
domain objects, not just `X.empty`). Match the exact binding shape (`@ContributesTo(AppScope::class)`
interface, `@Provides @SingleIn(AppScope::class)` function) already used elsewhere in this repo -
do not invent a different DI pattern.

## Step 4: fill in MainActivity.kt

Replace the `// TODO` block with the real call to the screen composable identified in Step 2,
wrapped exactly like the existing `CompositionLocalProvider`/`BillionBeersTheme` scaffold already
generated. If the screen needs a domain object as a parameter (rather than fetching it via its own
ViewModel), construct a sample instance inline here, or read one from the fake repository if it's
more natural to look one up (see how `app-dev-beerslist` isn't a good example for this case since
its screen self-fetches; for a detail-style screen, constructing a literal sample object directly
in `MainActivity` is usually simplest).

## Step 5: compile and verify

1. `./gradlew :app-dev-<feature>:compileDebugKotlin` - fix any errors before moving on.
2. If an emulator/device is connected (`adb devices`), do a full on-device smoke test matching
   this project's UI-change verification discipline: `assembleDebug`, `adb install -r`, launch via
   `adb shell am start`, screenshot, confirm no crash in `adb logcat -d | grep -i FATAL`. If no
   device is connected, say so explicitly rather than claiming it was verified.
3. Update the module's `README.md` "Still TODO" section to reflect what's actually still
   incomplete (ideally: nothing).

## Step 6: report

Summarize what was filled in (which fakes, which screen composable, any sample data seeded), the
compile result, and the on-device verification result (or why it was skipped). Don't claim
something works if it wasn't actually run.
