# 0017: Evaluate a common Navigation 3 foundation

## Status

Proposed evaluation plan. This document authorizes a bounded compatibility spike, not a product
navigation migration. Until the spike is accepted and this ADR is amended, Android keeps its
platform-owned Navigation 3 host and dynamic-feature delivery described by ADR 0016.

## Context

Navigation 3 runtime primitives are Kotlin Multiplatform-compatible, and Compose Multiplatform
provides corresponding non-Android UI artifacts. A common route and back-stack foundation is
therefore technically plausible. That fact alone does not prove that the repository can preserve
Android adaptive list/detail scenes, Play dynamic-feature gates, iOS back behavior, browser history,
reload restoration, accessibility, or current dependency and packaging boundaries.

The repository currently has two deliberate families of navigation:

- Android uses an app-owned `NavDisplay`, serialized `NavKey` entries, adaptive list/detail scene
  strategy, and dynamic-feature providers for beer browse and detail.
- Desktop, iOS, and Web use the portable route model and their shared app shell, with host-specific
  resources, icons, browser behavior, and framework/runtime constraints.

Saved-filter results make this boundary concrete: the feature needs a query-bearing route and exact
state restoration, while Android still needs to reach the result screen through its native host.

## Decision

Run a small, isolated Navigation 3 proof before proposing any migration. The spike must reuse the
repository's existing target conventions and must not replace the released Android host, move
production destinations, change persistence, or add a backend.

### Spike stages

1. **Inventory and route mapping**
   - Record every `PortableRoute`, Android `NavKey`, deep-link entry, dynamic-feature key, and
     saved-filter result route.
   - Define a deliberately small common route representation and a mapping to the existing
     platform routes. Do not serialize whole domain payloads into public URLs.
   - Identify which route values are durable state, which are host-only handles, and which require
     local-cache resolution.

2. **Isolated common back-stack proof**
   - Build a small KMP fixture using the repository's verified common/JVM, Android, iOS simulator,
     and Wasm target conventions.
   - Prove push, pop, root replacement, duplicate prevention where currently required, route
     serialization, and state restoration without importing Android app resources or dynamic-feature
     implementations into common code.
   - Keep the fixture independently removable and avoid production dependency edges until its target
     compilation and framework-link evidence exists.

3. **Behavioral parity checks**
   - Compare the fixture and current hosts for process recreation/state restoration, deep links,
     nested back behavior, and root tab selection.
   - On Android, prove the adaptive list/detail layout and ensure a missing dynamic split cannot be
     bypassed by a common route layer.
   - On iOS, prove framework compilation/linking and platform back behavior rather than relying on
     JVM compilation.
   - On Wasm/browser, prove URL encoding, browser history, forward/back behavior, reload restoration,
     cold deep links, and the explicit missing-local-cache outcome.
   - Exercise keyboard navigation, focus restoration, screen-reader labels, and back affordances on
     each host where the target supports them.

4. **Dependency and architecture impact**
   - Inspect resolved graphs and dependency verification for every target. Record artifact size and
     newly reachable platform libraries where measurable.
   - Confirm that common code does not acquire Android `Context`, Android resource IDs, Room entities,
     browser handles, dynamic-feature classes, or platform-only scene implementations.
   - Run source-boundary, project-policy, dependency-verification, and relevant target checks. A
     successful metadata compile is not sufficient evidence for framework linking or browser runtime.

5. **Decision and follow-up**
   - Amend this ADR only after the spike has produced evidence for every required target and behavior.
   - If the evidence is positive, propose an incremental migration: route contracts and back-stack
     primitives first, host displays and scene strategies second, and individual destinations last.
   - If any required behavior cannot be preserved at reasonable complexity, retain platform-owned
     hosts and document the specific falsifying result instead of introducing an abstraction for
     symmetry.

## Proof gates and falsification matrix

| Claim | Required proof | Falsifier / rejection condition |
|---|---|---|
| Common routes compile everywhere | Android/JVM compile, iOS simulator framework link, Wasm compile and browser execution | Any target needs platform-only source leakage or cannot link |
| State survives recreation/reload | Android recreation test, iOS host recreation proof where available, browser close/reload test | Route or query is lost, duplicated, or restored with stale mutable data |
| Deep links remain safe | Serialized route tests plus Android and browser host checks | Public URL requires a whole domain object, or cold cache behavior is ambiguous |
| Android delivery remains correct | Dynamic-feature install gate and adaptive list/detail device proof | Common navigation eagerly links a dynamic feature or breaks scene strategy |
| Browser history is meaningful | Chromium history/back/forward/reload execution | Browser back diverges from the in-app stack or reload cannot restore the route |
| Accessibility is preserved | Existing accessibility previews/tests plus host interaction checks | Back/actions lose labels, focus, or keyboard reachability |
| Dependency cost is acceptable | Resolved graph, verification, and artifact-size comparison | New dependencies violate policy or materially expand unrelated targets |
| Rollback is safe | Fixture and destination changes can be reverted independently | A spike requires schema, route-data, or host changes that cannot be rolled back independently |

A single target's successful compilation cannot override a failed runtime, framework-link, delivery, or
accessibility gate. Missing tooling or unavailable devices must be reported as an unproven claim, not
converted into a success.

## Incremental adoption boundaries if accepted

- First adopt common route contracts and serialization tests while retaining every current host.
- Then add platform adapters for Android dynamic-feature gates, adaptive scenes, iOS back behavior,
  and browser history/reload integration.
- Migrate one non-critical destination at a time, beginning with a destination whose state and
  delivery requirements are already covered by tests.
- Keep the current Android `AppNavigation` host as the rollback path until all migrated destinations
  have equivalent device evidence.
- Do not combine a navigation migration with a database schema change, dynamic-feature packaging
  change, or broad Compose UI extraction.

## Explicit non-goals

- This plan does not authorize replacing Android's native host.
- This plan does not make dynamic features common-source modules or remove Play split delivery.
- This plan does not require identical navigation chrome, resources, widgets, or accessibility
  implementation across platforms.
- This plan does not add browser proxies, remote detail endpoints, authentication, remote writes,
  analytics, or crash-reporting infrastructure.
- This plan does not claim that Navigation 3's multiplatform runtime makes every AndroidX adaptive
  or platform integration multiplatform automatically.

## Rollback

Delete the isolated fixture and any unmerged adapter changes. For an incremental production trial,
restore the affected destination to the existing platform host and retain the route contract only if
it has independent value and passing tests. No rollback may clear user data, alter database identity,
or disable existing Android architecture gates.

## Related

- [Incremental KMP targets and platform adapters](0016-incremental-kmp-targets-and-platform-adapters.md)
- [Dependency verification](0007-gradle-dependency-verification.md)
- [CI lane selection](0008-per-lane-ci-test-selection.md)
- [Feature-owned Android UI test tier](0009-feature-module-ui-test-tier.md)
