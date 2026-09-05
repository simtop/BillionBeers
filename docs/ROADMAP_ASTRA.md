# ROADMAP_ASTRA — what to do next

**Reviewed:** 2026-09-05. **Source baseline:** `97bef93` (PR #207).

This is the consolidated work queue for BillionBeers, not another architecture redesign. The
[ADRs](adr/) remain authoritative for decisions. This roadmap separates verified implementation,
specific remaining gaps, optional learning/product work, and ideas that should no longer be treated
as obligations.

**Recommendation:** finish a few concrete confidence gaps, then choose one product or learning
milestone. Do not keep extending the base to chase a subjective “10/10.” Today this is a reference
application; a supported domain-neutral generator would be a separate product commitment.

## Evidence and limits

- Reviewed the ADRs, accumulated planning material, current source/tests, build conventions,
  workflows, quality baselines and recent local Git history. Historical plans are not proof that a
  task is still open; a test name is not proof of the behavior its assertions cover.
- “Implemented” below means present in this checkout, not that its full verification suite was
  rerun during this documentation audit. No device, full build, benchmark or hosted CI run was
  performed for this review.
- Repository settings, external service behavior and sibling tool/template repositories were not
  revalidated. Their earlier publication/setup claims must not become checked-off work here.
- Existing local planning notes are preserved. This document is self-contained for a fresh clone
  and does not depend on those notes.

## 1. Recommended execution order

### N1 — Fix tests that can pass without proving behavior

**Priority: first. Two small changes, not a test-framework migration.**

- Move and rewrite [EitherTest](../app/src/test/java/com/simtop/billionbeers/core/EitherTest.kt)
  beside `Either` in `:core-common`. The `mapRight`/`mapLeft` assertions currently sit inside the
  callback: skipping that callback can leave a green test. Assert transformed return values,
  preservation of the opposite branch and callback invocation/non-invocation as appropriate.
- Consolidate [LocalDataSourceTest](../app/src/androidTest/java/com/simtop/billionbeers/LocalDataSourceTest.kt)
  and [LocalDataSourceTest2](../app/src/androidTest/java/com/simtop/billionbeers/di/LocalDataSourceTest2.kt)
  into `:beer_database`'s existing device tier. Preserve CRUD, duplicate insertion, constraint
  failure and the unique local-availability-preservation test. Remove duplicates only after the
  owning module runs the replacement suite.
- Add focused [BeerDetailViewModelTest](../feature/beerdetail/src/test/java/com/simtop/feature/beerdetail/BeerDetailViewModelTest.kt)
  cases for restored state, overlapping availability updates and no error event on success. Define
  the intended overlapping-update behavior before encoding an assertion; the present three tests
  do not settle it.

**Done when:** a deliberately broken mapping implementation fails the new JVM tests; each unique
database behavior exists once in its owning tier; detail tests assert values/events, not only state
types. Use explicit `:core-common:test`, the narrow feature unit task and the project Android skill
for device verification. No mutation-testing product is required to prove these fixes.

### N2 — Extend release confidence beyond “a window appeared”

**Priority: next. Keep the existing black-box, debug-signed minified target.**

[ReleaseLaunchSmokeTest](../app-release-smoke/src/main/java/com/simtop/billionbeers/releasesmoke/ReleaseLaunchSmokeTest.kt)
already checks launch/package visibility, and [CI](../.github/workflows/ci.yml) executes it. It does
not assert a successful data load, persisted data, mapping correctness or a navigation result.

1. Add a deterministic public-behavior journey through the minified app: known catalog data,
   navigation and a local availability edit that survives relaunch. If it uses a dynamic feature,
   explicitly provide the required split-install setup rather than assuming it is installed.
2. Keep this independent of the public backend's availability. Design a narrow test input/server
   seam that retains production repository, mapper, database and DI implementations. Do not replace
   the graph with fakes or loosen shipping keep rules merely to make the smoke pass.
3. Verify expected mapping and baseline-profile artifacts from a documented release build; archive
   them with revision and artifact hashes. Confirm release environment validation is reached from
   the assembled graph, not just from `EnvironmentConfigTest`.

**Done when:** a targeted R8/route/data-wiring regression fails deterministically, artifacts are
checked rather than merely uploaded, and neither store signing secrets nor live network access are
required. Keep existing Room migration tests; add a minified upgrade fixture only for a distinct
release-specific risk, not to duplicate the complete migration matrix.

**Later, not prerequisites:** report the size of one precisely defined APK/AAB before setting a
budget. Add SBOM/release attestation when there is a named consumer and provenance contract;
artifact hashes alone are not an attestation. Store upload and staged rollout stay separate.

### N3 — Make the existing observability seam typed and privacy-bounded

**Priority: next independent slice. No shipped vendor SDK.**

[AnalyticsTracker](../core-common/src/main/kotlin/com/simtop/core/core/AnalyticsTracker.kt) accepts a
free-form event name and string map. [CrashReporter](../core-common/src/main/kotlin/com/simtop/core/core/CrashReporter.kt)
accepts unrestricted messages, throwables and keys. [ObservabilityModule](../core/src/main/java/com/simtop/core/di/ObservabilityModule.kt)
binds no-op analytics/crash reporters but a real Logcat logger; privacy review must include that
logger rather than assuming the whole seam is inert.

- Define a small catalog for events already emitted, typed allowed parameters and a bounded
  diagnostic/error classification. Do not build a generic event platform.
- Specify what cannot leave the boundary: credentials, raw search text, URL query/fragment data and
  uncontrolled exception messages. Test the actual emission path with an in-memory recorder.
- Keep default bindings account-free and document the point where a future adapter supplies
  consent/provider policy. Add sampling or breadcrumb storage only if something consumes them.

**Done when:** existing call sites use the typed contract, unsafe payload examples are rejected or
redacted by tested behavior, and cloning/building still requires no provider account or private
configuration. [ADR 0010](adr/0010-non-goals.md) continues to decline a shipped analytics/crash SDK.

### N4 — Finish narrow build-maintenance gaps, not another TestKit program

**Priority: maintenance alongside relevant dependency/build changes.**

- Add rationale, owner and a concrete removal probe for the remaining compatibility flags in
  [gradle.properties](../gradle.properties): the built-in-Kotlin suppressions,
  `android.uniquePackageNames=false`, `android.dependency.useConstraints=false` and
  `android.r8.strictFullModeForKeepRules=false`. The [weekly build-tool check](../.github/workflows/weekly-build-tool-compat.yml)
  already exists, but running with a flag still enabled does not prove it is still necessary.
- Extend an existing TestKit fixture only for a demonstrated gap. Current configuration-cache
  coverage proves managed-device **task discovery** reuses a cache; current screenshot wiring uses
  a dry-run; current coverage assertions check dependencies/input paths. Do not describe these as
  proof of every real execution path or Kotlin/AGP combination.
- Re-measure `make build-budget` after material toolchain/build changes on a quiet local machine.
  The ADR's dated baseline is not a September measurement and CI wall time is not a substitute.
- Record an explicit trust decision for standalone `build-logic` tests: [ADR 0007](adr/0007-gradle-dependency-verification.md)
  correctly notes that `./gradlew -p build-logic :convention:test` has no independent verification
  ledger. If protection is added, include a sustainable regeneration path for its test fixtures;
  do not assume the root ledger already protects that invocation.

**Done when:** compatibility exceptions have reproducible removal checks, each new fixture closes a
named risk, and measurement/security claims state which build and task graph they cover.

### N5 — Retire quality debt selectively and prevent documentation drift

**Priority: continuous, with bounded scope.**

- The checked-in Android Lint baseline contains **zero issues**. Detekt has **75 baseline entries**
  across 18 files at the reviewed revision, including 26 in `:core:designsystem`. These are source
  counts, not fresh lint results. Fix behavioral/Compose findings first; named color literals do
  not need artificial abstractions just to reduce a number. Deliberate exceptions need a narrow
  rationale, not a regenerated baseline hiding new findings.
- Add a small offline documentation check for repository-relative links and deliberately generated
  indexes. Avoid brittle checks for incidental numbers or external link availability. The existing
  README version generator remains the version-table authority.
- Keep this roadmap's active queue current and preserve ADR measurement history as dated evidence.
  Do not create another audit scorecard or duplicate the accepted coverage denominator.
- `ACCESS_NETWORK_STATE` is still explicitly declared in the app manifest. The cleanup mentioned
  in ADR 0010 is genuinely unfinished, but small: inspect merged manifests, remove the explicit
  line only if redundant, then verify lint and dynamic-feature installation. This review does not
  claim the merged app can dispense with it.

**Done when:** each cleanup removes or explains a concrete exception, local documentation links are
checked, and published claims do not exceed their enforcement. Do not block useful feature work on
achieving an arbitrary zero-Detekt score.

## 2. Implemented — remove from the active backlog

These capabilities should be maintained, not rebuilt under older task names.

| Historical request | Current evidence / important limit |
|---|---|
| A real agent onboarding map and verification ladder | [AGENTS.md](../AGENTS.md); the “63-line tooling-only file” description is obsolete |
| Paging 2.0, search/browse reuse, append retry and no N+1 image request | [ADR 0002](adr/0002-hand-rolled-paging.md), `BeersPagerFactoryImpl`, `PagedListReducer`, storage contract tests; bookmark-miss fallback still exists |
| Debug controls, feature flags, standalone dev-app generator and robot tier | `:app-dev-beerslist`, `scripts/new-dev-app.sh`, debug graph and feature test sources; remote flags and dynamic-feature dev-apps are not thereby implemented |
| All-feature isolation and discovered dynamic-feature resource boundaries | [FeatureModuleBoundaryTest](../konsist/src/test/kotlin/com/simtop/konsist/FeatureModuleBoundaryTest.kt) and [DynamicFeatureResourceBoundaryTest](../konsist/src/test/kotlin/com/simtop/konsist/DynamicFeatureResourceBoundaryTest.kt); no longer limited to a historical package pair |
| Resolved architecture policy, negative fixtures and intentional project `api` edges | [Architecture policy](../config/architecture/project-dependency-policy.json), `verifyArchitectureGraph`, `ArchitecturePolicyFunctionalTest` and `ArchitecturePolicyTest`; this is a project-graph policy, not a published binary-API checker |
| Gradle and DI graph viewers | [Architecture reports](architecture-reports.md); report generation and policy enforcement are different tools |
| Representative convention-plugin tests | [ConventionPluginFunctionalTest](../build-logic/convention/src/test/kotlin/com/simtop/billionbeers/buildlogic/ConventionPluginFunctionalTest.kt); includes transitive boundaries, signing diagnostics and coverage inputs |
| Gradle warning inventory and scheduled forward-compatibility work | [Compatibility inventory](gradle-compatibility.md) and weekly workflow; remaining flag expiry/ownership is N4, not a missing workflow |
| Pure-JVM participation and honest selected-coverage labeling | [Root report](../build.gradle.kts) and [health script](../scripts/health-report.sh); explicit project/class exclusions remain intentional, not whole-repository coverage |
| Feature-owned browse/detail UI tests, accessibility journey and test-tier inventory | Feature `androidTest` sources, app journey and `scripts/test-tier-inventory.sh`; preserve the division in [ADR 0009](adr/0009-feature-module-ui-test-tier.md) |
| Accessibility screenshot matrix, design-system governance and manual release QA | [Governance](design-system-governance.md), [release QA](accessibility-release-qa.md), `:snapshot-testing`; expanded-width previews are not a two-pane feature |
| Repaired KSP discovery and CPS/KSP comparison | [ADR 0014](adr/0014-screenshot-preview-discovery.md): 272 discovered cases plus one manual catalog canary at the comparison revision; no backend migration is pending |
| Minified release launch smoke | `:app-release-smoke` in CI; deeper behavior/artifacts remain N2 |
| Typed endpoint/timeouts and release endpoint validation | [EnvironmentConfig](../core-common/src/main/kotlin/com/simtop/core/core/EnvironmentConfig.kt), its tests and app `EnvironmentModule`; assembled-graph assertion remains N2 |
| Read-only repository doctor and durable health artifacts | [Repository setup](repository-setup.md), `scripts/repo-doctor.sh`, weekly Markdown/JSON artifacts with deltas and historical [SAMPLE](health/SAMPLE.md); scheduled settings checks remain optional |
| Dependency-update automation and actionable CI failures | [ADR 0007](adr/0007-gradle-dependency-verification.md), verification-update safety tests and `.github/scripts/summarize-test-failures.py`; faster regeneration is still an experiment |
| Splash screen and ordinary deep-link plumbing | App theme/launcher, navigation code and tests; do not confuse these with missing billing or verified HTTPS App Links |

The catalog's handwritten screenshot is a harness canary in the accepted discovery comparison.
Do not delete it as “filler” without intentionally replacing that signal and updating the inventory.
Likewise, reflection-provider name tests protect install-time contracts despite their small size.

## 3. Park, reject or move to a separate track

“Optional” does not mean unimportant: an explicit learning objective is a valid reason to choose one
of these. It does mean that none is required to finish the reference app's current architecture.

| Idea | Disposition and reopen condition |
|---|---|
| Auth, token refresh, encrypted storage, integrity, push, server sync and offline write queue | **Out of the present base.** Apply ADR 0010's real backend/data/entitlement triggers. A local boolean update does not justify a server delivery queue |
| Remote flags, kill switch and force-update | **Optional operational milestone.** Name a real remotely controlled feature or a bounded learning goal; define offline defaults, expiry and recovery before choosing a provider |
| Favorites + widget, adaptive two-pane layout, richer filters | **Optional product milestone.** Choose one user-facing behavior with acceptance tests; do not change paging internals unless that behavior actually requires it |
| KMP / Compose Multiplatform | **Separate migration track.** Pick the first non-Android target and prove a thin compile/test slice. `EnvironmentConfig` uses `java.net.URI`, so pure-JVM does not already mean `commonMain`-ready. Preserve a shippable Android app at each step |
| Domain-neutral instantiation / monetization template | **Separate product decision.** Do not build a large rename/generation framework to earn a template-readiness score. If selected, require clean-output generation, identity-residue checks, CI validation and a support/update policy before advertising support |
| RevenueCat, ads, licensed SDK and backend business experiments | **Separate learning/product track.** Start with entitlement/backend requirements, not speculative security infrastructure in this public catalog |
| OSS publication of catalog/duplicate-class tooling | **External follow-up.** Inspect the actual sibling repos, publishing identity and release state first; earlier notes are not evidence of current readiness or publication |
| Publishing unused-dependency tooling, MutantForge or a generic screenshot processor | **Not a base prerequisite.** Require a differentiated consumer and a small validated prototype; existing local mutation probes do not need a new product |
| KSP member previews, exclusion annotations and similarity-based deduplication | **Conditional.** First add precise unsupported-shape diagnostics if needed by contributors. Add object/companion support for a real preview or publishing requirement. Similar images are not proof of redundant coverage; no automatic golden removal |
| Firebase Test Lab / Flank | **Not the default test lane.** Managed-device ownership already exists. Reopen only for required device coverage the current lane cannot provide |
| Mandatory assertion-library migration, more modules, binary convention rewrite, graph-depth limits | **Reject as generic cleanup.** Fix weak assertions directly; keep ADRs 0003, 0009, 0011 and 0013. No new invariant just to enforce taste |
| Checked-in agent permission settings or another knowledge system | **Do not revive by default.** Local permissions are deliberately ignored; accurate onboarding and this consolidated queue address the demonstrated drift |
| SLOs, error budgets, build scans and remote build cache | **Premise/measurement-gated.** Name a consumer or a bottleneck first; neither a service-level program nor another cache is missing merely because the interface could exist |

### CI performance: preserve the negative results

- **Do not retry two-way screenshot/device sharding, splitting architecture out of the unit lane,
  or a system-image-only cache unchanged.** ADRs 0008/0009 record the measured rejection. Unit
  sharding is not justified while the managed-device lane remains the measured critical path.
- A **coherent SDK/created-AVD cache** is a bounded optional experiment, not an accepted speedup.
  Preserve the current profile baseline, exclude locks and invalidate the full device/runner
  definition. Require a real cold miss and warm hit, unchanged test/report coverage, and the
  existing normalized whole-job improvement threshold before keeping it.
- A **resolution-only verification writer** already has reference/candidate modes. Compare
  artifacts from cold Linux runs on the same source SHA using
  `.github/scripts/check-verification-metadata-update.py --require-equivalent`; do not switch the
  production writer based on “assembly should resolve everything.”
- **Native merge-queue activation is externally constrained**, as recorded in ADR 0005. Do not
  transfer repository ownership or build a home-grown queue as incidental cleanup. A scheduled
  read-only repo-doctor run is optional after checking the credentials/scopes its APIs require;
  “unreadable” must remain distinct from “configured correctly.”

## 4. ADR review outcome

The decisions mostly hold. The problems were stale present-tense consequences, overbroad technical
claims and treating optional ambitions as required work. This documentation change corrects the
identified drift without changing the selected architecture.

| ADR | Review outcome |
|---|---|
| [0001](adr/0001-test-fixtures-via-sibling-modules.md) | Corrected the manual `settings.gradle.kts` entry claim; modules are auto-discovered. Preserve the fixture decision; future performance reevaluation needs a reproducible measurement, not the unsized historical assertion alone |
| [0002](adr/0002-hand-rolled-paging.md) | Corrected missing-retry/reuse claims and described exact bookmarks plus the actual legacy fallback. Removed categorical Paging3 testing/target claims and “KMP for free”; the plain-value contract remains the reason |
| [0003](adr/0003-use-case-policy.md) | Removed stale surviving-use-case/audit references and described build enforcement rather than compiler enforcement; the boundary test already exists |
| [0004](adr/0004-dev-apps-dont-support-dynamic-features.md) | Corrected the typo and one-dynamic-feature claim. A second feature exists, but extraction still needs demonstrated dev-loop pain |
| [0005](adr/0005-dependabot-over-renovate.md) | Keep Dependabot and the documented ownership-dependent merge-queue constraint. Source review does not revalidate live GitHub settings |
| [0006](adr/0006-ci-supply-chain-hardening.md) | Keep as historical rationale; its verification deferral is explicitly superseded by 0007. Old workflow/action counts describe adoption, not today's inventory |
| [0007](adr/0007-gradle-dependency-verification.md) | Keep safety checks and full reference writer. Candidate equivalence and standalone build-logic ledger scope remain explicit boundaries, not completed optimizations |
| [0008](adr/0008-per-lane-ci-test-selection.md) | Keep fail-open selection, one-run verdict adoption and recorded sharding/unit-lane rejection; no new orchestration plan needed |
| [0009](adr/0009-feature-module-ui-test-tier.md) | Keep feature ownership, existing minified smoke and the measured caching/sharding limits; don't repeat experiments without changed inputs |
| [0010](adr/0010-non-goals.md) | Fixed the contradiction that called a server-write queue merely deferred despite no server write path. Marked release smoke implemented and separated optional remote/KMP/product work from missing necessities |
| [0011](adr/0011-build-time-budget.md) | Removed the unresolved historical module-count claim and outdated startup-budget reference; ratios reduce rather than eliminate measurement variance. Preserve the dated local table |
| [0012](adr/0012-dispatcher-placement.md) | Scoped `setMain` reasoning to this app's default-scope ViewModels instead of claiming scopes cannot be supplied; removed the fixed IO-pool-size rationale. Dispatcher placement is unchanged |
| [0013](adr/0013-convention-plugin-form.md) | Corrected “build logic remains untested” and the claim that precompiled scripts need conversion to be testable. Distinguish plugin compilation from consumer configuration; representative tests now exist |
| [0014](adr/0014-screenshot-preview-discovery.md) | Keep the measured KSP decision and explicit publishing compatibility gaps. Older “migrate to CPS” plans are superseded, not parallel work |

The README now includes the feature/data and resolved-graph checks without hard-coded ADR/dependency
counts. Onboarding no longer describes feature isolation as limited to beerslist/beerdetail.

## 5. Keep one queue current

1. Start with **N1**, then **N2** and **N3** in separate reviewable changes; do N4/N5 when the relevant
   build or documentation surface is touched. No large “complete the base” branch is needed.
2. When an item lands, remove it from the active queue and retain only a short evidence entry if
   useful. A checkbox must not stand in for assertions or a measured result.
3. Choose at most one optional product/learning milestone next. Write its success criterion and
   non-goals before adding provider SDKs, targets, modules or CI lanes.
4. Verification follows [AGENTS.md](../AGENTS.md): narrow compile/tests first, then the applicable
   architecture, screenshot and static-analysis gates; resources also require Android Lint.
   Device work uses the project Android skill, and timing claims require local measurements.
