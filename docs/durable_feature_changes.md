# Changing a durable feature across targets

Use saved filters as a map for changes that persist local data. Follow the affected owners;
a label or error-handling change does not require a schema migration. Code, build rules and
[ADR 0016](adr/0016-incremental-kmp-targets-and-platform-adapters.md) own the architecture.
The [support matrix](kmp_support_matrix.md) separates implementation from runtime and release proof.

## Start with the behavior

Saved filters persist a named `BeersQuery`, remain local-only, and have stable IDs. The domain
model defines the ten-preset cap, nonblank names of 1–64 characters, and non-negative timestamps. Saving
an existing ID remains possible at capacity. Storage rejects a new ID beyond the cap; the
repository maps that failure to a typed result and propagates cancellation. Hosts retain drafts
and rows when writes fail and provide feedback before allowing a retry.

Define the changed behavior before editing: field validity, identity, capacity, ordering,
query reconstruction, and what the user sees after failure. Use the owners below to locate the
contract and its implementations.

## Owners in the saved-filter example

| Area | Source to inspect | Responsibility |
|---|---|---|
| Domain | [SavedFilterPreset](../beerdomain/api/src/commonMain/kotlin/com/simtop/beerdomain/domain/models/SavedFilterPreset.kt), [BeersRepository](../beerdomain/api/src/commonMain/kotlin/com/simtop/beerdomain/domain/repositories/BeersRepository.kt), [errors](../beerdomain/api/src/commonMain/kotlin/com/simtop/beerdomain/domain/errors/) | Immutable product values, query and mutation contracts, typed failures. Keep storage records and platform types out of these APIs. |
| Storage contract | [BeersStorage and StoredFilterPreset](../beer_storage/api/src/commonMain/kotlin/com/simtop/beer_storage/api/BeersStorage.kt) | Portable operations and stored fields, including the storage capacity exception. This contract is independent of Room and browser handles. |
| Repository | [BeersRepositoryImpl](../beer_data/src/commonMain/kotlin/com/simtop/beer_data/repositories/BeersRepositoryImpl.kt) | Domain/storage conversion, observation and failure mapping. A storage failure becomes a domain result; cancellation is rethrown. |
| Room adapter | [entity](../beer_database/src/commonMain/kotlin/com/simtop/beer_database/models/SavedFilterPresetDbModel.kt), [DAO](../beer_database/src/commonMain/kotlin/com/simtop/beer_database/database/BeersDao.kt), [local source](../beer_database/src/commonMain/kotlin/com/simtop/beer_database/localsources/BeersLocalSource.kt) | SQL, capacity transaction, ordering, validation and record conversion on Android/JVM/iOS. |
| Browser adapter | [IndexedDbBeersStorage](../beer_storage/browser/src/wasmJsMain/kotlin/com/simtop/beer_storage/browser/IndexedDbBeersStorage.kt) | `PresetRecord`, JSON conversion, object-store operations, transaction completion and peer invalidation. Update both Kotlin records and JavaScript payload handling when stored fields change. |
| Fakes | [FakeBeersRepository](../beerdomain/fakes/src/commonMain/kotlin/com/simtop/beerdomain/fakes/FakeBeersRepository.kt), [FakeBeersLocalSource](../beer_data/src/jvmTest/kotlin/com/simtop/beer_data/fakes/FakeBeersLocalSource.kt) | Observed state and failure behavior used by host/repository tests. Fake failure injection does not prove transaction rollback. |
| Android UI and entry | [saved-filter feature](../feature/savedfilters/src/main/java/com/simtop/feature/savedfilters/), [AppNavigationViewModel](../app/src/main/java/com/simtop/billionbeers/presentation/AppNavigationViewModel.kt), [AppNavigation](../app/src/main/java/com/simtop/billionbeers/presentation/AppNavigation.kt), [routes](../navigation/src/main/java/com/simtop/navigation/Routes.kt) | Screen/ViewModel state, saving from query surfaces, typed-result feedback and Android route payloads. |
| Shared UI and entry | [SharedAppNavigationState](../shared/app/src/commonMain/kotlin/com/simtop/billionbeers/shared/app/SharedAppNavigationState.kt), [SharedAppShell](../shared/app/src/commonMain/kotlin/com/simtop/billionbeers/shared/app/SharedAppShell.kt), [SavedFiltersDestination](../shared/app/src/commonMain/kotlin/com/simtop/billionbeers/shared/app/SavedFiltersDestination.kt), [portable routes](../navigation-contract/src/commonMain/kotlin/com/simtop/navigation/contract/Routes.kt) | Shared entries, entry disposal, mutations, feedback and keyed editors. Saved results are internal entries; inspect each host's history/restoration policy before changing routes. |

Domain changes do not authorize remote writes or synchronization; see
[ADR 0010](adr/0010-non-goals.md). Android retains Play delivery and Glance ownership. Shared
feature code does not depend on sibling features or data implementations. A new project edge
also needs the [dependency policy](../config/architecture/project-dependency-policy.json) and
enforcement updated in the same change.

## Storage versions and database builders

Room is currently schema **5**, with migrations 1→2→3→4→5. The exported schema belongs to
`:beer_database`, configured by the [Room convention](../build-logic/convention/src/main/kotlin/billionbeers.room.gradle.kts).
Inspect [BeersDatabase](../beer_database/src/commonMain/kotlin/com/simtop/beer_database/database/BeersDatabase.kt),
[Migrations](../beer_database/src/commonMain/kotlin/com/simtop/beer_database/database/Migrations.kt)
and [schema 5](../beer_database/schemas/com.simtop.beer_database.database.BeersDatabase/5.json).

When a Room schema changes, review the migration list in every runtime builder:

- Android: [BeersDatabaseModule](../beer_database/src/androidMain/kotlin/com/simtop/beer_database/di/BeersDatabaseModule.kt).
- Desktop: [DesktopDataRuntime](../desktop-app/src/main/kotlin/com/simtop/billionbeers/desktop/DesktopDataRuntime.kt).
- iOS: [IosPlatformModule.ios](../ios-shared/src/iosMain/kotlin/com/simtop/billionbeers/iosshared/IosPlatformModule.ios.kt).
- JVM iOS-session runtime: [IosPlatformModule.jvm](../ios-shared/src/jvmMain/kotlin/com/simtop/billionbeers/iosshared/IosPlatformModule.jvm.kt).

Also inspect the migration-aware builders in
[BeersDatabaseJvmTest](../beer_database/src/jvmTest/kotlin/com/simtop/beer_database/database/BeersDatabaseJvmTest.kt)
and [BeersDatabaseIosTest](../beer_database/src/iosSimulatorArm64Test/kotlin/com/simtop/beer_database/database/BeersDatabaseIosTest.kt),
plus [Android migration tests](../beer_database/src/androidTest/java/com/simtop/beer_database/database/BeersDatabaseMigrationTest.kt).
Fresh-file tests such as `SavedFilterPersistenceJvmTest` exercise the current schema and are not
upgrade tests. Android's debug-only destructive fallback does not establish preservation of
an existing release database. Keep database paths stable and verify upgrades without clearing
real user data.

IndexedDB independently uses database `billionbeers`, version **2**, and stores `beers`,
`paging_state` and `filter_presets`. Its version and `onupgradeneeded` handling live in
`IndexedDbBeersStorage`; a Room version bump does not migrate browser data. Review retained
records, defaults and reopen behavior on that adapter separately. No version bump is needed
for changes that leave stored fields and schema intact.

## Host strings and navigation

Android saved-filter labels and errors live in [feature resources](../feature/savedfilters/src/main/res/)
and [app resources](../app/src/main/res/), including English, French and Spanish. Dynamic-feature
strings retain their separate owner in [presentation_utils](../presentation_utils/src/main/res/).

The shared shell accepts `SharedAppStrings`; inspect each provider when a field or label changes:
[DesktopShell](../desktop-app/src/main/kotlin/com/simtop/billionbeers/desktop/DesktopShell.kt),
[IosLocalizedStrings](../ios-shared/src/iosMain/kotlin/com/simtop/billionbeers/iosshared/IosLocalizedStrings.ios.kt),
and [WebMain](../web-app/src/wasmJsMain/kotlin/com/simtop/billionbeers/web/WebMain.kt).
Other Web shared-resource strings live in [composeResources](../web-app/src/commonMain/composeResources/).
Compile-time providers and literal test labels are not proof of runtime resource loading or
accessibility on every host.

If a change affects navigation, inspect the Android host above and the shared event consumer in
`WebMain`, [WebRoutes](../web-app/src/commonMain/kotlin/com/simtop/billionbeers/web/WebRoutes.kt)
and [IosAppSession](../ios-shared/src/iosMain/kotlin/com/simtop/billionbeers/iosshared/IosAppController.ios.kt).
Check browser Back/Forward and reload separately from shared stack tests. Keep host-specific
delivery, URL resolution and lifecycle responsibilities explicit.

## Choose the checks for the changed owners

Use the [Makefile](../Makefile) wrappers. A scoped `make test MODULE=...` compiles and runs the
registered target suite; it does not run every target of a KMP module.

| Change | Checks and examples to inspect |
|---|---|
| Domain/repository/storage behavior | `make test MODULE=:beerdomain:api`, `MODULE=:beerdomain:fakes`, `MODULE=:beer_data` or `MODULE=:beer_storage:api`, as affected. [BeersRepositoryTest](../beer_data/src/jvmTest/kotlin/com/simtop/beer_data/repositories/BeersRepositoryTest.kt) covers error mapping, cancellation and retry. |
| Room persistence/schema | `make test MODULE=:beer_database`; inspect [file-backed failure/reopen tests](../beer_database/src/jvmTest/kotlin/com/simtop/beer_database/database/SavedFilterPersistenceJvmTest.kt). Use `make ui-test-managed MODULE=:beer_database` for Android migration/local-source tests, and `make ios-test` for the existing simulator data suite. These are separate runtime proofs. |
| Browser persistence | `make test MODULE=:beer_storage:browser`; inspect [IndexedDB tests](../beer_storage/browser/src/wasmJsTest/kotlin/com/simtop/beer_storage/browser/IndexedDbBeersStorageBrowserTest.kt) for capacity, real aborts, reopen and peer observation. Quota, eviction and other browsers remain separate acceptance work. |
| Android saved-filter UI | `make test MODULE=:feature:savedfilters`, `make screenshot-verify`, and `make ui-test-managed MODULE=:app`. [layout](../app/src/androidTest/java/com/simtop/billionbeers/SavedFiltersLayoutTest.kt) and [mutation feedback](../app/src/androidTest/java/com/simtop/billionbeers/SavedFiltersMutationUiTest.kt) tests cover ten-item reachability, large text, retained drafts and retry. Resource changes also need `make android-lint`. |
| Shared UI/navigation | `make test MODULE=:shared:app`; inspect [layout](../shared/app/src/jvmTest/kotlin/com/simtop/billionbeers/shared/app/SavedFiltersLayoutTest.kt), [host failure](../shared/app/src/jvmTest/kotlin/com/simtop/billionbeers/shared/app/SavedFiltersHostFailureTest.kt) and [stack](../shared/app/src/jvmTest/kotlin/com/simtop/billionbeers/shared/app/SharedAppNavigationStateTest.kt) tests. Run `make test MODULE=:web-app` for browser routes/history; use `make web-verify` when the Web distribution is affected. |
| Module boundaries | `make konsist` and `make architecture-policy`. Source rules and resolved project edges check different failure modes. |
| Documentation only | `make docs-check`; no Android build is needed. |

Before landing code, run the full `make test` gate and the applicable static/UI checks. Report
the target and executed suite with each result. A fake repository, a shared JVM render, a
physical database reopen, a simulator journey and a packaged artifact establish different claims.

## Review a new persisted query field

For example, scope a proposed field from `BeersQuery` through `SavedFilterPreset`, the repository
conversion, `StoredFilterPreset`, both adapter records and serialization, and the affected route
payloads. Decide its default for old rows and whether each schema needs migration. Update the
affected fakes and host string providers, then check query reconstruction, failure/draft retention,
upgrade/reopen and navigation on the changed targets. The ownership map makes these changes
discoverable; it does not select a new product field or require a generic migration framework.
