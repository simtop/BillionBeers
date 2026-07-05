# Why there's no app-dev-beerdetail (yet)

Context for anyone (human or AI) who wants to revisit this. Short version: `:feature:beerdetail`
is a dynamic feature module, and AGP structurally forbids any application other than its one
declared base app from depending on it. This isn't a build-speed inconvenience like it would be
for a regular feature module - it's a hard failure. See also
`docs/adr/0004-dev-apps-dont-support-dynamic-features.md` for the settled decision this doc backs.

## How this was discovered

While building `scripts/new-dev-app.sh` and `.claude/skills/new-dev-app/SKILL.md` (the
`app-dev-<feature>` generator, first proven out on `app-dev-beerslist`), the natural next test was
generating `app-dev-beerdetail` for `:feature:beerdetail`.

1. `scripts/new-dev-app.sh beerdetail BeerDetail :feature:beerdetail` scaffolded the module fine
   (build.gradle.kts, manifest, Application, DevAppGraph, etc. - same shape as beerslist's).
2. `:app-dev-beerdetail:compileDebugKotlin` **succeeded**. This is misleading - Kotlin/javac
   compilation doesn't know or care about AGP's dynamic-feature/base-app relationship, so this
   step gives false confidence.
3. Investigating how to host the real screen surfaced a second issue first: `BeerDetailProviderImpl`
   (the `DynamicFeatureContentProvider` reflectively loaded by the real app) delegates to
   `BeerDetailScreenImpl`, which does:
   ```kotlin
   val appGraph = (context.applicationContext as BillionBeersApplication).appGraph
   val component = createGraphFactory<FeatureDetailComponent.Factory>().create(appGraph as DynamicDependencies)
   ```
   `BillionBeersApplication` and `com.simtop.billionbeers.di.DynamicDependencies` are `:app`-module
   types. A standalone application module can never depend on `:app` (Gradle/AGP don't support an
   application module depending on another application module), so `BeerDetailScreenImpl` can't be
   called from a dev-app either. The workaround tried: call `ComposeBeerDetail(beer, onBackClick,
   onToggleAvailability)` directly instead - the pure presentational composable underneath the
   ViewModel, with no DI and no `:app` dependency. That part of the fix is fine and would work on
   its own.
4. `:app-dev-beerdetail:assembleDebug` **failed**:
   ```
   This application (com.simtop.billionbeers.devbeerdetail) is not configured to use dynamic features.
   Please ensure dynamic features are configured in the build file.
   ```
   This is the real, unavoidable blocker. `feature/beerdetail/build.gradle.kts` applies the
   `billionbeers.android.dynamic.feature` convention plugin
   (`build-logic/convention/src/main/kotlin/billionbeers.android.dynamic.feature.gradle.kts`),
   which hardcodes `implementation(project(":app"))` - a dynamic-feature module is compiled as a
   split APK of exactly one base application, declared via that dependency plus the base app's own
   `android { dynamicFeatures += setOf(...) }`. AGP enforces this relationship at the
   resource-linking step of `assembleDebug`/`bundleDebug`, not just at runtime. There is no Gradle
   property or plugin config that relaxes this - it's how Play Feature Delivery's base/split model
   works.
5. Confirmed the (separate, secondary) build-time cost too: even just resolving
   `:app-dev-beerdetail:compileDebugKotlin`'s task graph pulls in 84 tasks under `:app:`,
   `:beer_data:`, `:beer_network:`, and `:beer_database:` - because `:feature:beerdetail`'s own
   `implementation(project(":app"))` transitively drags in everything `:app` depends on. So even
   setting the assemble failure aside, this would never have been the fast, isolated build the
   dev-app pattern is meant to deliver.

## What was tried and reverted

- `app-dev-beerdetail/` was generated, given a `MainActivity` hosting `ComposeBeerDetail` directly
  with a hand-built sample `Beer` and local `mutableStateOf` for the availability toggle, and an
  empty `di/DevFakesModule.kt` (no repository needed at that presentational level). This compiled
  but could not assemble, per the error above, so it was removed from
  `phase3-new-dev-app-generator`. A raw (unfinished, pre-this-investigation) copy of the generated
  scaffold is preserved on branch `phase3-dev-app-beerdetail-scaffold` for reference.
- `scripts/new-dev-app.sh` now detects `dynamic.feature`/`dynamic-feature` in the target module's
  `build.gradle.kts` and refuses to scaffold, pointing here.

## Possible future paths (not attempted)

1. **Extract beerdetail's presentational composables into a plain library module.** E.g. a new
   `:feature:beerdetail:ui` (or similar) module with no dynamic-feature plugin, no `:app`
   dependency - just `ComposeBeerDetail` and friends. `:feature:beerdetail` itself would depend on
   it (for the real app), and a dev-app could too (for the sandbox). This is the structurally
   correct fix, but it's real module-boundary work: deciding exactly where the api/impl split
   falls, whether `BeerDetailViewModel` moves too or stays behind in the dynamic feature, and
   updating Konsist rules if any assume `:feature:beerdetail` is self-contained.
2. **Source-duplicate the composable file into the dev-app.** Copy `ComposeBeerDetail.kt`'s
   content directly into `app-dev-beerdetail`, with a comment pointing back at the real file as the
   source of truth. Works immediately, no architecture change, but the copy silently drifts out of
   sync with the real file over time - exactly the kind of thing this project's Konsist/screenshot
   discipline exists to prevent for production code, so this should probably never be promoted
   past "quick local hack."
3. **Don't bother for beerdetail specifically.** Given option 1 is the only durable fix and is
   nontrivial, it may simply not be worth it for a single screen - revisit if a second dynamic
   feature is added and the same need comes up twice.

## Takeaway for the generator/skill

`scripts/new-dev-app.sh` and `.claude/skills/new-dev-app/SKILL.md` both now treat "target is a
dynamic feature" as a hard stop, not a workaround to route around. Don't re-attempt the
`ComposeBeerDetail`-direct approach without first solving the `assembleDebug` blocker (option 1 or
2 above) - it compiles fine and will look like progress right up until `assembleDebug`.
