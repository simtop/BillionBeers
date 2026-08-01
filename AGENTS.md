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
| `:snapshot-testing`, `:snapshot-processor` | Paparazzi harness + KSP generation of screenshot tests from previews |
| `:catalog`, `:catalog-annotations`, `:catalog-processor` | Demo/component catalog app + its KSP generator |
| `:benchmark:{macrobenchmark,microbenchmark,baselineprofile}` | Perf budgets and baseline profile generation |
| `build-logic` | Convention plugins (`billionbeers.android.feature`, `…dynamic.feature`, `…screenshot`, unused-deps, duplicate-classes). A separate composite build |

Modules are **auto-discovered** by `settings.gradle.kts` — any directory with a build script is
included. Adding a module needs no `settings.gradle.kts` edit.

**Two stale directories will mislead a grep** (both harmless, both worth deleting):
`beerdomain/impl/` is empty (no build script, so not even a module), and `core-common/bin/` is a
leftover IDE output tree holding *deleted* sources — including `BaseUseCase.kt`, which was removed
from the project. If a search hits `bin/`, you're reading a ghost; search `src/`.

---

## 3. Invariants — break these and the build (or an instrumented test) breaks

The first six are enforced by `:konsist`; the wording here is from the tests themselves.

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

Not yet mechanically enforced (candidates — see `rod/July_Improvements.md` §4.3):

7. **Test fixtures live in sibling `:module:fakes` / `:module:fixtures` modules**, never Gradle's
   `java-test-fixtures` plugin (ADR 0001 — measured build-time cost).
8. **Domain models are immutable; one-shot UI events use `Channel(BUFFERED).receiveAsFlow()`**, not
   `SharedFlow` (which drops events with no active collector).

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

Also settled, without an ADR:

- **Typed errors reuse `Either<L, R>` parameterized with a sealed error type** — the problem was
  `Either<Exception, T>`'s untyped left, not `Either`. No new `Result`/`Outcome` type.
- **`available` is local-only** even though the API has the field. The backend isn't ours, so a
  sync could only ever one-way-overwrite user edits. The server value seeds a row on first insert.
- **The N+1 image fetch is gone** — list responses embed `image.url`. Don't reintroduce
  `GET /images/{id}`.
- **Paging is complete and plug-and-play** (5 phases, 8/8 criteria). A new filtered surface needs a
  wider `BeersQuery` and a screen — **not** `core-common` changes. Reference: `rod/PAGING_2_0.md`.
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
5. **Lint / format** — `make lint`, `make format`
6. **Device** — instrumented tests, install, logcat: use the `billionbeers-android` skill, not
   ad-hoc `adb`

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
- **`rod/`** — local-only notes and plans, gitignored as one folder rule. **It has no git history,
  so deleting a file there is permanent.** `rod/July_Improvements.md` is the current consolidation
  of open work; `rod/MASTER_PLAN.md` is the plan of record.
- A doc that becomes load-bearing gets promoted from `rod/` to `docs/` and committed.
