# app-dev-beerslist

A minimal Android application module that compiles **only** `:feature:beerslist` against an
in-memory fake `BeersRepository` - no `:beer_data`, `:beer_network`, `:beer_database`, or
`:feature:beerdetail`.

## Why

Building and installing the real `:app` module pulls in the network stack, Room, and the
beerdetail dynamic feature - seconds turn into minutes on every iteration of the list screen.
This module exists purely so `./gradlew :app-dev-beerslist:installDebug` gives you a running,
interactive `BeersListScreen` in the time it takes to compile one feature module.

## What's fake

`di/DevBeersRepositoryModule.kt` binds `BeersRepository` to `FakeBeersRepository` (from
`:beerdomain:fakes`), seeded with a handful of sample beers. Edit that file to try out different
states (empty list, all beers unavailable, large lists for scroll testing, etc).

## Known limitation

Tapping a beer still goes through `BeersListScreen`'s own `DynamicFeatureLoader` gate (it's
unconditional inside the composable, not something a caller can skip). Since `:feature:beerdetail`
isn't declared as a dynamic feature of this app, the install request fails immediately - no crash,
just a briefly-shown loading dialog that dismisses itself. This is expected: this module is for
iterating on the list screen, not the detail screen.

## Adding another dev-app

Copy this module's shape for a different feature: its own `build.gradle.kts` (apply
`billionbeers.android.application` + `billionbeers.android.compose` + `billionbeers.android.metro`,
depend only on the feature under test + its fakes), its own `Application`/`DevAppGraph`/
`MainActivity`, and a `di/ViewModelMapsModule.kt` copy (Metro's per-graph multibinding maps don't
cross application-module boundaries, so every standalone app graph needs its own).
