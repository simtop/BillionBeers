# Navigation 3 evidence matrix

This matrix records the evidence behind the Navigation 3 decision in
[ADR 0017](adr/0017-common-navigation-3-evaluation.md). It separates portable runtime proofs from
host-level product behavior. A fixture or compile result does not prove an equivalent production
host journey.

## Status vocabulary

- **Proven** — the named behavior was exercised by the cited test or verification command.
- **Retained** — the current platform-owned architecture is an intentional decision supported by the
  available evidence.
- **Deferred** — the question is bounded and known, but no production change is justified yet.
- **Unproven** — the required host, device, browser, framework linker, or manual journey was not
  exercised; this is not a passing result.

## Behavioral evidence

| Claim | Owner and evidence | Target | Verification | Artifact/report | Status | Falsifier or boundary |
|---|---|---|---|---|---|---|
| Navigation 3 runtime primitives resolve from common code | `Navigation3RuntimeProbeTest` and `Navigation3RuntimeProbe` | JVM, Android host-test, Desktop, Wasm, iOS framework | `make test MODULE=:compose-multiplatform-fixture` plus the iOS compile/framework checks | Fixture test reports and linked iOS framework | Proven | A target requires platform-only imports or cannot link the common proof |
| Portable routes can wrap Navigation 3 entries | `RouteContractTest` and `Navigation3BackStackFixtureTest` | Common/JVM and platform source sets | `make test MODULE=:navigation-contract` | Navigation contract test reports | Proven | Route identity or mapping requires Android resources, feature classes, or domain payloads |
| Route order and active state survive serialization/restoration | Navigation 3 runtime probe and route-contract fixtures | Common/JVM fixture | `make test MODULE=:navigation-contract` and `make test MODULE=:compose-multiplatform-fixture` | Fixture test reports | Proven | Restored routes are lost, duplicated, stale, or unsafe for public URLs |
| Independent tab back stacks retain roots and active entries | `Navigation3TabBackStackFixture` and runtime probe test | Common/JVM fixture | `make test MODULE=:compose-multiplatform-fixture` | Fixture test reports | Proven | A production host cannot retain or dispose equivalent entries without a second state owner |
| Web hash routes, malformed values, Back/Forward ordering, reload-root parsing, duplicate entries, and forward truncation are deterministic | `WebHistoryFixture` and `WebRoutesTest` | Wasm browser test target | `make test MODULE=:web-app`; packaged smoke through `make web-verify` when available | `web-app` test reports and Web release-confidence packet | Proven for the bounded test surface | Deployed-host behavior, broad browser support, and every cold-cache journey remain outside this proof |
| Production Browse selection notifies Web history; visible content follows in-app Back, browser Back/Forward, and category reload | `SharedAppNavigationStateTest` and `verifyBrowseHistory` in `scripts/smoke_web_distribution.cjs`; `WebRouteSession` tracks hashes written by `pushState`/`replaceState` | Shared JVM tests and packaged Web host in local Chromium | `make test MODULE=:shared:app`; `make web-verify` | Navigation test reports, smoke log's `browseHistory` trace, and Web release-confidence packet | Proven for the deterministic local style-category journey | Baseline selection rendered category results with `#browse`; Back from detail could leave detail visible at `#catalog`. Both are repaired. Brewery notifications are covered in JVM tests; packaged brewery journeys, deployed origins and broad browser support remain unproven |
| Uncached external Web detail links retain their URL and show translated explanation/recovery; cached details survive reload | `WebRoutesTest`, `verifyColdMissingBeer` and `verifyWarmMissingBeer` in `scripts/smoke_web_distribution.cjs`; Web-owned `WebRouteResolution` | Wasm tests and packaged Web host in local Chromium | `make web-verify` | Browser test reports, smoke log's `coldMissingBeer` / `cachedDetailReload` / `warmMissingBeer` traces, and Web release-confidence packet | Proven for the deterministic local journey | Fresh-profile entry and English/French/Spanish reloads make zero API requests before recovery. Recovery replaces the unavailable history entry; warm Back/Forward and reload follow visible content. The final Gradle distribution includes Compose resources. Deployed origins, other browsers, assistive-technology review and retention of unsaved shell state across unavailable entries remain outside this proof. The exact Kotlin memory-access deprecation is recorded as a known warning |
| Web preset results have distinct transient history identity despite their shared list URL | `verifySavedFilterHistory`, `WebSavedFilterHistoryTest` and shared saved-result restoration test | Shared JVM/Wasm tests and packaged Web host in local Chromium | `make test MODULE=:shared:app`; `make web-verify` | Shared/Web test reports and smoke `savedFilterHistory` trace | Proven for the bounded local session/reload journey | Applied query snapshots stay in page-session memory behind opaque history tokens. Back/Forward restores results; reload and stale tokens intentionally restore the durable list. Shareable/durable result routes, other browsers and deployed origins remain unproven |
| Android adaptive list/detail scenes remain platform-owned | `AppNavigation` with `ListDetailSceneStrategy`; expanded-device `MainActivityComposeTest` | Android | `make ui-test` or `make ui-test-local` with the expanded/resizable device | Managed-device/instrumented test reports | Proven for the exercised device journey; retained as host architecture | A supported device loses list retention, detail selection, or Back behavior |
| Repeated identical Android deep links are delivered to the existing Activity; a consumed link does not override navigation after recreation | `MainActivityComposeTest`: `repeatedFavoritesDeepLinkReturnsToFavoritesInTheSameActivity`, `repeatedCachedBeerDeepLinkReopensDetailAfterBack`, and `consumedDeepLinkDoesNotOverrideNavigationAfterRecreation`; `MainActivity` delivery identity and consumption acknowledgment | Android API 35 phone emulator | `ANDROID_SERIAL=<serial> GRADLE_RUNNER=./gradlew make ui-test MODULE=:app` | `app/build/outputs/androidTest-results/connected/debug/` JUnit report | Proven for these cold/warm and explicit Activity-recreation journeys | The baseline failed all three regressions; the fixed suite passed 9 tests with the expanded-width test skipped. Warm test intents clear ActivityScenario launch flags to exercise `singleTask` reuse. Pending asynchronous lookup/recreation, process death, and other API/device profiles remain unproven |
| A cached external detail link cannot render a missing split after installation fails | `MainActivityComposeTest.cachedBeerDeepLinkDoesNotBypassAMissingDetailSplit` and `FakeSplitInstallManager` | Android API 35 phone emulator | `make ui-test MODULE=:app` on the selected device | Connected JUnit report | Proven for an injected network-error outcome | The test verifies the requested `beerdetail` module, visible failure feedback, and absent detail content. Real Play installation, confirmation, cancellation, retry, and recreation during installation remain unproven |
| Android on-demand detail and browse entry points use the dynamic-feature gate | `DynamicFeatureNavigator`, Android host tests, feature provider tests | Android | `make ui-test` or `make ui-test-local`; `make test MODULE=:feature:beerdetail` and `make test MODULE=:feature:beerbrowse` where supported | Instrumented reports and release-smoke split artifacts | Proven for successful/fake-installed journeys; retained as host architecture | Common navigation eagerly links a split, bypasses installation, or renders an unavailable feature |
| Dynamic-feature pending, failure, cancellation, and retry state transitions are modeled | `DynamicFeatureInstallerTest` | JVM test infrastructure | Module-specific presentation-utils test task | JVM test report | Proven for installer state transitions | End-to-end Play/device cancellation and Activity-recreation behavior is not covered by the fake-installed UI journey |
| iOS route parsing, buffering before collection, ordering, idempotent close, and runtime close work | `IosHostAdapterTest` and `IosRouteRequestBufferTest` | iOS shared/common tests | `make test MODULE=:ios-shared`; `make ios-compile`; `make ios-framework`; `make ios-test` | iOS test and framework-link reports | Proven for the tested adapter behavior | Native Back, scene recreation, background/foreground, and cancellation remain host-level gaps |
| Desktop data/runtime integration works | Existing Desktop integration tests | Desktop JVM | `make desktop-test` | Desktop test report | Proven for the data slice only | Window close/reopen, focus restoration, keyboard Back, and multi-window lifecycle are not covered |
| Accessibility, focus, keyboard, and screen-reader parity across hosts | Compose accessibility previews, host-specific tests, and manual QA | Android, Web, iOS, Desktop | Target-specific UI/browser/manual checks | Preview/test reports where available | Unproven as a complete cross-host claim | Any claimed host loses labels, focus restoration, keyboard reachability, or Back affordances |

## Dependency and artifact evidence

| Claim | Evidence | Verification | Artifact/report | Status | Boundary |
|---|---|---|---|---|---|
| Navigation 3 coordinates are explicit and version-pinned | `gradle/libs.versions.toml`, `navigation/build.gradle.kts`, `app/build.gradle.kts` | Source review plus `make dependency-guard` | `app/dependencies/releaseRuntimeClasspath.txt` | Proven | Current production graph includes Navigation 3 runtime/UI and adaptive Navigation 3 artifacts; this is not a before/after migration delta |
| Common route contracts do not acquire AndroidX Navigation dependencies | `navigation-contract/build.gradle.kts` and architecture/source boundaries | `make dependency-guard`, `make architecture-policy`, `make check-data-layer-boundary`, `make konsist` | Dependency guard, architecture reports, and module graph | Proven if gates pass | A new transitive AndroidX or platform edge would invalidate the boundary |
| Dependency verification covers the resolved graph | ADR 0007 workflow and checked-in verification metadata | `make verification-metadata-reference` when a full graph refresh is required; `make dependency-guard` for the committed release graph | `gradle/verification-metadata.xml` and dependency reports | Retained | Metadata regeneration is a controlled review action, not a routine documentation output |
| Released Android artifacts are identified and hashed | `make release-smoke` and `scripts/verify-release-smoke-artifacts.sh` | `make release-smoke` when managed-device/tooling is available | Release-smoke manifest with revision, SHA-256, and byte counts for app/features/profiles/mapping | Proven when the release smoke passes | No isolated Navigation 3-only artifact delta is emitted |
| Navigation 3 package cost has a dedicated size threshold or apkanalyzer comparison | No repository-owned threshold or isolated comparison exists; ADR 0010 leaves size reporting deferred until an artifact is defined | Not applicable without a defined baseline and artifact | Existing release-smoke manifest is the available size evidence | Unproven / not claimed | Do not infer zero cost from a passing build; define a concrete comparison before adding a gate |

## Production decision

Retain the shared-foundation/platform-owned-hosts boundary:

- Android keeps `AppNavigation`, `NavDisplay`, adaptive list/detail scenes, and the dynamic-feature
  installation gate.
- Web, iOS, and Desktop keep `SharedAppShell` and explicit host adapters for history, lifecycle,
  window behavior, and platform Back/input semantics.
- Portable route identity, serialization, and back-stack primitives remain the shareable seam.
- `SavedFilterResults` stays out of `PortableRoute` until its URL, lifecycle, and restoration
  semantics are explicitly defined for Web, iOS, and Desktop.
- No second persistence format or broad host migration is justified by the current evidence.

The remaining host-specific questions are tracked in the
[Navigation 3 experiment backlog](navigation3-experiment-backlog.md). New work should begin only
when a named unproven behavior becomes a product requirement and has an executable acceptance
journey.
