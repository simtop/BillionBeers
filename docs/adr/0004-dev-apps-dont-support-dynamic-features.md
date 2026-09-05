# 0004: Dev-app modules don't support dynamic features

## Status

Accepted

## Context

`app-dev-<feature>` modules are standalone applications that
compile only one feature module + fakes, for a seconds-scale build/install loop instead of the
full `:app`. `app-dev-beerslist` (`:feature:beerslist`, a regular feature module) proved this out
successfully. `scripts/new-dev-app.sh` and `.claude/skills/new-dev-app/SKILL.md` generalized the
pattern into a reusable generator.

The natural next test was `:feature:beerdetail` - a **dynamic feature**
(`com.android.dynamic-feature`, installed on demand via Play Feature Delivery). Attempting to
generate `app-dev-beerdetail` surfaced a structural AGP limitation, not just a slower build. Full
investigation, error output, and what was tried: `docs/beerdetail_dev_app.md`.

## Decision

`app-dev-<feature>` dev-apps only support regular feature modules. Dynamic feature modules are
explicitly unsupported: `scripts/new-dev-app.sh` detects the target module's
`billionbeers.android.dynamic.feature` plugin application and refuses to scaffold, and
`.claude/skills/new-dev-app/SKILL.md` instructs against attempting a workaround without the
user's explicit go-ahead.

## Why

A dynamic feature module's Gradle convention plugin
(`billionbeers.android.dynamic.feature.gradle.kts`) hardcodes `implementation(project(":app"))` -
this is not incidental, it's how AGP models the Play Feature Delivery base/split relationship: a
dynamic feature is compiled as a split APK belonging to exactly one base application, declared on
both sides (`dynamicFeatures += setOf(...)` on the base, `implementation(project(":base"))` on the
split). AGP enforces this at the resource-linking step of `assembleDebug`/`bundleDebug`:

```
This application (com.simtop.billionbeers.devbeerdetail) is not configured to use dynamic features.
```

`compileDebugKotlin` succeeding first is a trap, not a sign of progress - plain Kotlin/javac
compilation has no awareness of the dynamic-feature/base-app contract, so a dev-app for a dynamic
feature can look like it's working right up until the assemble step.

Even setting the hard assemble failure aside, `:feature:beerdetail`'s own
`implementation(project(":app"))` means merely resolving `app-dev-beerdetail`'s task graph pulls
in 84 tasks under `:app:`, `:beer_data:`, `:beer_network:`, and `:beer_database:` - the entire real
app's dependency closure. So even a hypothetical fix for the assemble failure wouldn't deliver the
seconds-scale build this pattern exists for.

## Cost accepted

No standalone dev-app loop for dynamic features. Both `:feature:beerdetail` and
`:feature:beerbrowse` are now on-demand features. App-assembly and install-flow iteration still
uses `:app`; Paparazzi covers static rendering, and feature-owned instrumented tests cover
single-screen device behavior under ADR 0009. Neither test path is a standalone dev-app.

## Consequences

- `docs/beerdetail_dev_app.md` records two real (not attempted) paths if this becomes painful
  enough to revisit: extracting beerdetail's presentational composables into a plain library
  module both the dynamic feature and a dev-app could depend on (the structurally correct fix,
  real module-boundary work), or source-duplicating the composable into the dev-app (works
  immediately, drifts out of sync silently, should stay a last resort).
- The second dynamic feature now exists, but module count alone does not justify extracting UI
  modules. Revisit extraction when repeated, measured dev-loop pain warrants it; the unsupported
  direct dev-app dependency remains unsupported meanwhile.
