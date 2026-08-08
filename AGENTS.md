# BillionBeers — agent onboarding

Read this first. It exists so an agent doesn't have to re-derive the same facts by grepping every
session: what the modules are, what the rules are, and what's already been decided.

**Ground truth beats this file.** Where it names an ADR or a Konsist test, that file is
authoritative — this is the map, not the territory. If you find a contradiction, the code wins and
this file needs a fix.

---

## 1. What this project is

A production-shaped multi-module Android app (beer catalog) over `brewbuddy.dev`, a read-only
json-server-style REST API. Compose UI, Metro DI, Room SSOT, hand-rolled paging, two on-demand
dynamic feature modules, architecture rules enforced by Konsist in CI.

It is a portfolio and template base, so the *shape* is the product: conventions are enforced by
tooling rather than by memory, and deviations need a written reason.

---

## 2. Module map

| Module | What lives there |
|---|---|
| `:app` | The shipping app: Metro graph assembly, `MainActivity`, nav host, `dynamicFeatures` declaration |
| `:app-dev-beerslist` | Standalone dev-app for fast feature iteration (see `scripts/new-dev-app.sh`, `new-dev-app` skill) |
| `:core` | Android-side core: DI modules (networking, observability), production `Logger` binding |
| `:core:designsystem` | Theme, tokens, `@LightDarkPreviews` multipreview, `PreviewTheme` |
| `:core-common` | **Pure JVM.** `PagingMediator`, `PagedListReducer`, `CachePolicy`, `Either`, `CommonUiState`, dispatchers, `Logger`/`AnalyticsTracker`/`CrashReporter` interfaces + no-op impls, `FeatureFlagProvider`, `LanguageProvider`, `ThemeController`, `NetworkFaultController` |
| `:beerdomain:api` | Domain models + repository *interfaces* + typed sealed errors + nav values |
| `:beerdomain:fakes` | Fakes for the domain interfaces, consumed by feature tests and dev-apps |
| `:beer_network` | Retrofit service, DTOs (`@Serializable`), `BeersPage` header envelope |
| `:beer_database` | Room database, DAOs, entities, migrations (currently v3) |
| `:beer_data` | Repository impls, `BeersMapper`, `BeersPagerFactoryImpl`, error mapping at the data boundary |
| `:feature:beerslist` | Catalog (paged) — regular feature module |
| `:feature:beersearch` | Search-as-you-type (paged, in-memory) — regular feature module |
| `:feature:beerdetail` | Detail — **on-demand dynamic feature** |
| `:feature:beerbrowse` | Browse by style / brewery (tabs, paged) — **on-demand dynamic feature** |
| `:presentation_utils` | Shared UI: paged-list scaffolding, `InfiniteListHandler`, dynamic-feature install (`InstallStatus`, `DynamicFeatureInstaller`, `DynamicFeatureLoader`), **and the strings dynamic features need** (see §3) |
| `:navigation` | Nav keys / typed routes shared across features |
| `:konsist` | The architecture rules. Pure JVM — see the gotcha in §5 |
| `:testing-utils` | Pure-JVM shared test helpers |
| `:testing-utils-android` | `BaseTestRobot` — the shared instrumented-test robot base, consumed by `:app` and by every feature module in the UI-test tier (ADR 0009) |
| `:snapshot-testing`, `:snapshot-processor` | Paparazzi harness + KSP generation of screenshot tests from previews |
| `:catalog`, `:catalog-annotations`, `:catalog-processor` | Demo/component catalog app + its KSP generator |
| `:benchmark:{macrobenchmark,microbenchmark,baselineprofile}` | Perf budgets and baseline profile generation |
| `build-logic` | Convention plugins (`billionbeers.android.feature`, `…dynamic.feature`, `…screenshot`, unused-deps, duplicate-classes). A separate composite build |

Modules are **auto-discovered** by `settings.gradle.kts` — any directory with a build script is
included. Adding a module needs no `settings.gradle.kts` edit.

**`bin/` directories will mislead a grep.** `core-common/bin/` and `build-logic/convention/bin/`
are leftover IDE output trees holding *deleted* sources — including `BaseUseCase.kt`, which was
removed from the project. Both are gitignored and untracked, so they never reach a clone; the cost
is local only. If a search hits `bin/`, you're reading a ghost — search `src/`, and `rm -rf` them
when they get in the way. (`:konsist`'s build-script rules skip `bin/` for this reason.)

---

## 3. Invariants — break these and the build (or an instrumented test) breaks

All ten are enforced by `:konsist`; the wording here is from the tests themselves.

1. **Repository interfaces do not import data-layer types.** (`RepositoryBoundaryTest`)
2. **Feature modules never depend on other feature modules** — `beerslist` ⊥ `beerdetail`, and so
   on. Cross-feature navigation goes through `:navigation`. (`FeatureModuleBoundaryTest`)
3. **The `beerdomain` domain layer has no Android imports.** (`DomainLayerPurityTest`)
4. **ViewModels depend only on domain-layer types.** This is the load-bearing precondition for the
   use-case policy in ADR 0003 — without it, "inject the repository directly" becomes "inject
   whatever you like". (`ViewModelBoundaryTest`)
5. **User-facing strings for dynamic-feature modules live in `:presentation_utils`.** Resources
   declared inside a dynamic feature module crash instrumented tests. This has bitten twice.
   (`DynamicFeatureResourceBoundaryTest` — it catches the failure path, a dynamic-feature file
   importing its *own* module `R`. Being Kotlin-only it can't see a stray `strings.xml` that
   nothing references, which is harmless anyway.)
6. **Dev-apps depend only on `api` + `fakes` modules** — that's what keeps their build fast.
   (`DevAppDependencyBoundaryTest` enforces the load-bearing half: no `app-dev-*` build script may
   declare `:beer_data`, `:beer_database` or `:beer_network`. It reads the build scripts directly,
   since Konsist doesn't scan `.kts`.)

7. **Test fixtures live in sibling `:module:fakes` / `:module:fixtures` modules**, never Gradle's
   `java-test-fixtures` plugin (ADR 0001 — measured build-time cost).
   (`TestFixturesPluginBoundaryTest` — reads the build scripts, ignoring comment lines.)
8. **One-shot UI events use `Channel(BUFFERED).receiveAsFlow()`**, never `MutableSharedFlow`, which
   drops events when nothing is collecting. (`OneShotEventBoundaryTest`.)

9. **Domain models are immutable** — no `var`, and no `val` holding a mutable collection
   (`MutableList`, `HashMap`, `Array`, …), which is the half that slips through review.
   (`DomainModelImmutabilityTest`.)

10. **A module with `src/androidTest/` opts into the managed device** — via
    `billionbeers.android.feature.uitest` (feature modules) or `billionbeers.android.managed.device`
    directly. Without it the module has no `atdApi35DebugAndroidTest` task and is absent from
    `ciGroupDebugAndroidTest`, so its tests compile, look like coverage, and never run.
    `:benchmark:*` is exempt — benchmarks use their own runner and real hardware, not the ATD lane.
    (`InstrumentedTestOptInBoundaryTest` — reads the build scripts.)

11. **A `src/` directory has a build script beside it.** Otherwise nothing compiles, tests or ships
    it, but a grep still finds it — the same failure mode as the stale `bin/` trees in §2. Note that
    `./gradlew projects` is not proof a module is real: `:beerdomain`, `:feature` and `:benchmark`
    appear there as containers Gradle synthesizes from nested includes, with no build script of
    their own. (`OrphanedSourceTreeTest` — reads the filesystem. Caught
    `beerdomain/src/main/AndroidManifest.xml` on adoption, orphaned when `:beerdomain` split into
    `api`/`fakes`.)

12. **Test-only libraries never sit on `implementation`/`api`** — they ship. `:core-common` had
    `implementation(libs.coroutinesTest)` beside the `coroutines-core` line that replaced it, so
    `kotlinx-coroutines-test` reached `:app`'s release classpath and its `TestMainDispatcherFactory`
    was in the shipped APK's `META-INF/services`. `dependency-guard` could not catch it: the
    baseline was recorded *with* the dependency already present, so it protected the defect.
    `:benchmark:*`, `testing-utils*` and `build-logic` are exempt — for them a test library on
    `implementation` is correct. (`TestLibraryBoundaryTest` — reads the catalog and build scripts.)

Every invariant in this list is now mechanically enforced. If you add one, add its rule in the
same change — a convention with no test is a convention that drifts.

---

## 4. Settled decisions — don't re-litigate, read the ADR

| Decision | Where |
|---|---|
| Test fixtures via sibling modules, not `java-test-fixtures` | `docs/adr/0001` |
| Hand-rolled paging, no Paging3 (`PagingData` leaks through every layer) | `docs/adr/0002` |
| A use case exists iff it does something a repository call doesn't — in practice **none survive today**; ViewModels inject `BeersRepository` directly, which is only safe because invariant 4 is enforced | `docs/adr/0003` |
| Dev-apps can't host dynamic features | `docs/adr/0004` |
| Dependabot over Renovate | `docs/adr/0005` |
| CI supply-chain hardening: Actions SHA-pinned, gitleaks on PR ranges | `docs/adr/0006` |
| Dependency verification enforced; `make verification-metadata` regenerates the ledger, and a CI workflow does it on Dependabot branches so auto-merge survives — it re-baselines dependency-guard first (else the regen aborts on stale-baseline drift), but only when the drift is version-only | `docs/adr/0007` |
| Per-lane CI test selection: a lane runs if the push could affect it or it was red last time, else it adopts the green verdict. Rules live in one function in `.github/scripts/detect-change-scope.sh` | `docs/adr/0008` |
| Instrumented UI tests live in the feature module that owns the screen; cross-feature and install-gate tests stay in `:app`. Cost is ~49s per *module* vs ~2s per *test*, so concentrate tests and add modules deliberately | `docs/adr/0009` |
| **Non-goals** — auth, cert pinning, encrypted storage, integrity/anti-tamper, push, server-side `available` sync, a shipped analytics/crash SDK, consent flows, automatic retry, multi-process. All declined because the premise is absent (the backend isn't ours, is read-only, and is unauthenticated), each with its reopen trigger. Read it before "adding what a real app has" — and delete a row in the same PR that builds it | `docs/adr/0010` |
| Dispatchers are chosen where the work is, not where the coroutine starts. ViewModels use a bare `viewModelScope.launch { }` — Room and Retrofit suspend calls are already main-safe, so an IO hop only moves state assignment off Main. Inject `CoroutineDispatcherProvider` only where a class actually calls `withContext`/`flowOn` (a blocking SDK, CPU work). StrictMode in debug is the detector that makes this safe | `docs/adr/0012` |
| Build time is budgeted in `config/build-time-budget.txt` and measured **locally** by `make build-budget`, never in CI — a CI wall-clock number measures which runner the job drew. Clean build 37s cold / 4s warm; a deep ABI change costs only 1.11x a leaf one, so **per-build overhead dominates, not compilation** — do not justify a new module by build speed | `docs/adr/0011` |

Also settled, without an ADR:

- **Typed errors reuse `Either<L, R>` parameterized with a sealed error type** — the problem was
  `Either<Exception, T>`'s untyped left, not `Either`. No new `Result`/`Outcome` type.
- **`available` is local-only** even though the API has the field. The server value seeds a row on
  first insert; nothing writes back. Now has an ADR — the reasoning and its reopen trigger live in
  `docs/adr/0010`.
- **The N+1 image fetch is gone** — list responses embed `image.url`. Don't reintroduce
  `GET /images/{id}`.
- **Paging is complete and plug-and-play** (5 phases, 8/8 criteria). A new filtered surface needs a
  wider `BeersQuery` and a screen — **not** `core-common` changes.
- **Mappers are injected classes, not objects**, so they're testable and swappable.

---

## 5. Verification ladder

Cheapest first. Run the narrowest rung that can falsify your change, then the ones above it before
declaring done.

1. **Compile** — `./gradlew :module:compileDebugKotlin --console=plain`
2. **Unit tests** — `make test [MODULE=:feature:beerslist]`
3. **Architecture** — `make konsist`
4. **Screenshots** — `make screenshot-verify` (record with `make screenshot-record` and inspect the
   PNGs; they are your eyes on the UI)
5. **Lint / format** — `make lint`, `make format`, and **`make android-lint` whenever resources
   change**. `make lint` is Detekt only; the Android Lint gate CI runs is `make android-lint`
   (`:app:lintDebug`, checkDependencies across the whole graph). A string added to
   `values/strings.xml` without its `values-fr` / `values-es` siblings passes every other rung and
   fails CI with `MissingTranslation` — that is how PR #140 broke master.
6. **Device** — instrumented tests, install, logcat: use the `billionbeers-android` skill, not
   ad-hoc `adb`

**Off the ladder, on purpose: `make build-budget` and `make benchmark-check`.** Both measure
wall-clock, so both are local-and-deliberate rather than per-change gates — CI cannot control for
hardware (ADR 0009 §"Note on measuring any of this", ADR 0011). Run `build-budget` when you change
the build itself: a convention plugin, an annotation processor, the module graph. It takes 15–20
minutes and it measures *your* machine, so close anything heavy first.

**CI note: heavy lanes skip per-lane on PR pushes.** A push to an open PR reruns only the test
lanes its diff can affect (goldens and `screenshot/` test folders → screenshot; `src/androidTest/`
→ instrumented; other `src/test/` → unit; anything else → all) plus any lane that was red on the
previous head; unaffected green lanes adopt their previous verdict. Docs/skills/scripts-only PRs
still skip all heavy lanes. **To change what runs when, edit the single `classify_path` function**
in `.github/scripts/detect-change-scope.sh`; the reasoning is in ADR 0008.

**Gotcha: pure-JVM modules are invisible to `make test`.** It targets `testDebugUnitTest`, which
plain `org.jetbrains.kotlin.jvm` modules (`:core-common`, `:konsist`, `:testing-utils`) don't have.
Their tests need explicit invocation (`./gradlew :core-common:test`) or `make check`. `:konsist:test`
silently never ran for a while because of exactly this.

**Gotcha: `adb shell pm clear` breaks on-demand installs.** It wipes bundletool local-testing's
staged splits; installs then fail with SplitInstall error −5 until `scripts/install-local-testing.sh`
is re-run. Not an app bug.

**Gotcha: `installDebug` is broken** by a duplicate PreviewProvider service entry — install the
split APKs with `adb install-multiple` (the `billionbeers-android` skill handles this).

**Adding `androidTest` to a module: two traps, both now fixed in build-logic.** Recorded because
the symptoms point away from the cause. (1) The screenshot convention used to feed its generated
Paparazzi runner into *every* compile task matching `"Test"`, including
`compileDebugAndroidTestKotlin` — so the first screenshot-enabled module to gain an androidTest
source set failed with `Unresolved reference 'Paparazzi'` in a file it never wrote. The filter now
matches `"UnitTest"`. (2) `PROJECT_TEST_RUNNER` used to name `MockTestRunner`, which lives in
`app/src/androidTest` — every other module built a test APK referencing a class it did not contain
and died on device with `ClassNotFoundException`. The default is now the stock
`AndroidJUnitRunner`; `:app` opts up in its own build script. A module that needs a custom runner
sets it in its own `android { defaultConfig { … } }`, which overrides the convention.

**Instrumented tests are opt-in per module.** A module needs `billionbeers.android.managed.device`
— directly, or via `billionbeers.android.feature.uitest` — for `atdApi35DebugAndroidTest` to exist
and for CI's `ciGroupDebugAndroidTest` to pick it up. Without it the tests compile and never run,
the `:konsist:test` failure mode; invariant 10 now enforces this. Opted in today: `:app`,
`:beer_database`, `:feature:beerslist`. `:benchmark:microbenchmark` has `androidTest` sources but is
*deliberately* out — it declares `AndroidBenchmarkRunner`, builds against
`testBuildType = "release"`, and suppresses the `EMULATOR` error class, because a measurement taken
on a managed virtual device is meaningless. It runs via `make benchmark-check`.

---

## 6. Build & development commands

Use the `Makefile` wrappers — they auto-detect `bb`/`rtk` and route through them.

`make clean` · `deep-clean` · `build` · `install` · `test [MODULE=…]` · `konsist` · `lint` ·
`format` · `check` · `screenshot-record` · `screenshot-verify` · `ui-test` · `benchmark-check` ·
`generate-baseline` · `new-feature-module` · `new-dev-app` · `update-android-skills`
(`make help` lists them all.)

### Token optimization tools

Three optional tools cut stdout volume; the Makefile uses them when installed.

- **`rtk`** — compresses stdout from `git status`, test runners, etc. `rtk git status`
- **`snip`** — YAML-driven CLI proxy filtering LLM context inputs. `snip git diff`
- **`bb` (build-brief)** — filters Gradle output to summaries + failures, raw log to `/tmp`.
  `bb ./gradlew assembleDebug`

---

## 7. Skills

Skills live in `.claude/skills/`. Reach for one whenever the task matches its domain.

**Project skills:**
- **`billionbeers-android`** — **always** for anything touching a device or emulator: emulators,
  devices/SDK status, install/launch, screenshots, logcat, instrumented tests. It knows the correct
  `adb` path (`$ANDROID_HOME/platform-tools/adb`) and the `installDebug` workaround.
- **`new-dev-app`** — scaffold *and finish* a `app-dev-<feature>` sandbox module.
- **`land`** — commit / push / PR with this repo's hygiene gates.

**Official Android skills** — a curated subset of [android/skills](https://github.com/android/skills)
is vendored (each carries a `.android-skill-source` marker): `android-cli`, `navigation-3`,
`r8-analyzer`, `perfetto-trace-analysis`, `testing-setup`, `edge-to-edge`, `adaptive`,
`android-intent-security`, and more.

- **Sync:** `make update-android-skills` — installs new upstream skills, prunes removed ones, never
  touches locally-authored skills.
- **Opt out:** add the name to `.claude/skills/.android-skills-ignore`. Currently ignored:
  `agp-9-upgrade`, `camera1-to-camerax`, `display-glasses-with-jetpack-compose-glimmer`,
  `migrate-xml-views-to-jetpack-compose`, `wear-compose-m3`.

---

## 8. Docs & planning

- **`docs/`** — committed, load-bearing docs only. `docs/adr/` is the decision record.
- **Planning notes are local-only and gitignored**, so nothing in a clone points at them and no
  committed file should cite one. **They also have no git history, so deleting one is permanent** —
  never delete or overwrite a file in an ignored notes directory without being asked to.
- Work that is decided but not built is described in `docs/adr/0010` as planned future work, not as
  a pointer to a plan the reader cannot open.
- A note that becomes load-bearing gets rewritten into `docs/` and committed.
