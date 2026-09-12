# 0016: Migrate incrementally to KMP with platform-owned adapters

## Status

Accepted as the migration direction. Android is the only implemented product target today;
compatibility, runtime and distribution support for the other targets are not yet proved.

## Context

BillionBeers is an Android application with a read-only remote catalog and durable local favorites
and availability. It uses Room as source of truth, typed errors, hand-rolled paging, Metro DI,
Compose, two on-demand dynamic features and a Room-backed Glance widget. Existing expanded-width
list/detail navigation is also behavior to preserve, not a prerequisite feature to invent.

The chosen objective is to learn Kotlin Multiplatform through small, independently verifiable
changes while retaining an installable and releasable Android application. The product scope now
includes Desktop, iOS and Web. Pure-JVM source, shared package names or a sample project's successful
build do not prove common-source compatibility or browser support.

## Decision

### Order and proof boundaries

1. Establish the Android baseline and characterize missing behavior tests before source moves.
2. Prove a minimal Android KMP library plus a consistently named `jvm()` target. Separately probe
   Apple compilation/linking and browser Wasm execution early, before committing production modules
   to unsupported dependencies. Extend source, resolved-graph and test/report enforcement first.
3. Share core/domain and replace Retrofit behind the existing remote-source seam, proving Android
   behavior before moving the transport implementation. Retain OkHttp on Android/JVM; use Darwin on
   iOS and a verified Ktor browser engine for Wasm.
4. Prove real JVM persistence and repository integration, then Apple and browser data runtimes.
5. Extract Compose screens incrementally, starting with unpaged durable favorites. Complete Desktop
   UI first, iOS next and the Web host next. Browser storage and networking must already be proved
   before shared UI extraction, not deferred until the final host.

Each change must demonstrate the smallest relevant falsifying test and explain its mechanism.
Metadata compilation, target compilation, framework linking, test execution, host rendering and
packaging are separate claims. A missing, skipped or `NO-SOURCE` test is not a successful execution.
Unavailable or failing baseline checks remain explicit limitations with a next action.

### Shared contracts, platform-owned delivery

Keep domain models immutable, repositories domain-facing, typed errors, injected mapper classes,
current paging/cancellation behavior and dispatcher ownership. Do not add a pass-through use-case
layer, change DI or rename the module graph wholesale for portability.

Existing Android feature modules remain adapters for delivery, route entry, DI, resources, previews
and device tests. A shared feature may not depend on a sibling feature or data implementation.
An Android adapter may reach its own shared implementation only; enforce this identity relation
rather than allowing a wildcard feature-to-shared edge.

Android retains its navigation host and on-demand detail/browse delivery. The base app must not
link those shared implementations eagerly through a shared app shell. The non-Android shell may
link all screens statically. Android also retains its Glance widget, graph-bound synchronizer,
active-session observation and recovery semantics from ADR 0015; neither domain nor shared storage
may acquire Android widget callbacks.

Use constructor injection for OS edges. Use `expect`/`actual` only where it has a concrete purpose,
such as Room's generated constructor contract. Common code must not expose Android Context,
Retrofit responses, browser handles, Room entities or platform resource IDs through domain APIs.

### Product parity does not require Android OS feature parity

The owner explicitly accepts omitting Android-only capabilities from Desktop, iOS and Web.
In-app catalog, search, browse/detail, favorites and availability remain shared product scope;
Glance widgets, Play dynamic delivery, split-install progress/retry UI and Android component
machinery do not need substitutes on other platforms. Detail/browse link normally in those hosts;
omitting Play delivery does not mean omitting those screens.

Android resources, Toast/Intent helpers, system bars, StrictMode and app-widget actions remain at
the Android edge. Other hosts use their own meaningful navigation, resources, accessibility,
layout and diagnostics; identical platform plumbing or an Android-style debug drawer is not a
parity requirement. Android screenshots, device tests, shrinking and baseline profiles remain
Android verification mechanisms, not toolchains to port. Each new target still needs its own
behavior and packaging proof. Additional OS widgets and distribution services remain optional,
separately scoped product choices.

### Persistence differs on Web

Retain Room 2.8.4 initially for Android/JVM/iOS, subject to version-specific API and runtime proof.
Keep the existing Android database identity (`beers_database.db`), schema version 4 and migrations
1→2→3→4. No automatic schema bump, destructive fallback or database-path change is authorized.
The existing debug-only fallback must not be enabled in upgrade tests or common/default providers.

For Web, the default is a narrow IndexedDB adapter behind a storage contract extracted from the
existing local-source operations. The contract and stored values are independent of Room and the
DOM. Room implements that same contract on Android/JVM/iOS; shared repository common code must not
resolve a Room dependency on Wasm. This is an implementation-layer boundary, not a new public
domain repository hierarchy or a generic database framework.

Preserve keyed upserts, local flags, atomic page-plus-bookmark writes, bookmark merge rules, cache
freshness, unpaged favorite ordering and observation after committed writes on both adapters.
Browser transactions must signal success only after commit. Test reopen/reload, version upgrades,
abort/quota failures and multi-tab invalidation. Do not substitute a volatile database or silently
fall back to memory/localStorage and report a successful durable edit.

Browser storage is origin/profile-scoped and can be cleared or evicted by the browser or user;
private browsing is not promised durable retention. State survives ordinary reload/restart on a
supported persistent profile. No cross-device synchronization is implied. Ask for persistent-storage
grants only with a deliberate UX; denial must be handled. Stable deployment origin is a data-identity
requirement just as a stable database filename is on Android.

Room 3 plus a browser SQLite worker is an alternative to revisit if an executable spike shows the
adapter cannot preserve required semantics at reasonable complexity. It is not a prerequisite or
an approved global dependency upgrade. A browser storage contract remains useful even if its
implementation changes later.

### Browser host and network boundaries

Start with `wasmJs { browser() }`, not an unverified dual Wasm/JavaScript product matrix. Select
supported browser versions by actual execution, including rendering, keyboard/accessibility and
storage tests. No fallback support claim follows from a successful build in one browser.

Prove requests from a real HTTP browser origin, including visibility of `X-Total-Count`, API CORS,
image origins and resource loading. A curl response does not prove browser Fetch access. Do not
use `no-cors`, browser security-disable flags or opaque responses as a workaround. If the API or
images block the browser, record the limitation; adding a proxy changes operational ownership and
requires a separate decision. A development proxy alone does not establish production parity.

Browser history, URLs, reload restoration, visibility/disposal and asynchronous initialization are
host responsibilities. In-app detail still uses a serialized Beer; external ID links remain
local-cache-only. Public URLs must not contain whole domain payloads. Keep an explicit missing-cache
outcome on a cold deep link rather than introducing a remote detail endpoint.

Static distribution output must include verified MIME types, resources, base-path behavior and
cache/version policy. If a selected runtime needs workers or cross-origin isolation, test the exact
production headers and all affected external resources. PWA installation, service-worker offline
shell caching, SEO/server rendering and server writes are not part of this decision. Offline data
is not a promise that an uncached app shell loads without a network connection.

### Verification and rollback

Extend Make routing, per-target resolved architecture checks, static source roots, dependency
verification, CI lane selection and report discovery as targets arrive. Preserve current Android
screenshot discovery and device tiers. Measure shared code once in JVM coverage rather than losing
or double-counting it; Native/Wasm execution requires its own reports, not a fabricated Jacoco claim.

Retain Android migration tests and a controlled, existing-install upgrade proof. Reading installed
package metadata is not a backup; exporting installed app data requires permission for its source
and destination. Do not uninstall, clear data, change signing identity or regenerate baselines to
hide failures. Existing local data and unrelated working-tree changes are outside migration cleanup.

Keep commits small enough to revert code independently. A schema change, if later justified, needs
its own forward/backward recovery decision; rolling code back does not undo an upgraded database.
A blocked new platform must not require disabling Android gates to continue unrelated safe work.

## Consequences

- More adapter modules and explicit host factories are accepted for preserving Android delivery and
  making platform responsibilities visible. They are not justified by assumed build-speed gains.
- Web adds a second persistence implementation and browser-specific QA. Shared behavior contracts
  and contract tests, rather than identical SQL, protect parity.
- Compatibility experiments can change proposed artifact choices without silently changing product
  behavior. Material alternatives require evidence and an amended decision.
- No app-store submission, external hosting deployment, account provisioning or artifact upload is
  authorized by choosing this architecture. Those remain separately approved operations.
- Auth, remote writes, analytics vendors and other absent-premise infrastructure remain non-goals
  under ADR 0010. OS widgets on Desktop/iOS/Web are not part of preserving the Android widget.

## Related

- [Paging semantics](0002-hand-rolled-paging.md)
- [Dependency verification](0007-gradle-dependency-verification.md)
- [CI lane selection](0008-per-lane-ci-test-selection.md)
- [Feature-owned Android UI tests](0009-feature-module-ui-test-tier.md)
- [Non-goals](0010-non-goals.md)
- [Dispatcher ownership](0012-dispatcher-placement.md)
- [Convention plugin form](0013-convention-plugin-form.md)
- [Screenshot discovery](0014-screenshot-preview-discovery.md)
- [Glance synchronization](0015-room-backed-glance-widget-synchronization.md)
