# 0002: Hand-rolled `PagingMediator` instead of Paging3

## Status

Accepted

## Context

The beers list is paginated: fetch page N from the network, persist it, and merge with what's
already in the local DB (SSOT). Jetpack's Paging3 library is the standard tool for exactly this
shape of problem, and it wasn't used - `core-common`'s `PagingMediator` was hand-rolled instead.
That needs a documented reason, because "we don't use Paging3" is not by itself a defensible
position; it should be "we rejected Paging3, and here's the trade-off."

This isn't a theoretical rejection. Paging3 was actually implemented and iterated on across
several branches early in this project's history - `feature/network_paging` (a first pass with
`PagingSource` and a `LoadStateAdapter`) and `feature/network_room_paging` (a network+DB
`RemoteMediator` setup, matching this project's SSOT shape). Commit messages on those branches
("TODO Fix Unit and UI Tests and add more tests for paging", "I need to investigate how to add
more Espresso and Unit tests ... and how to add a usecase for the paging") and a later branch
named `Mediator_for_paging_not_working` are the friction this ADR generalizes into a decision.

## Decision

Keep the hand-rolled `PagingMediator<Key, Value>` (a small state machine over `currentKey`,
`isLastPage`, and a `PagingState` sealed class) instead of adopting Paging3.

## Why

Paging3's `PagingData<T>` type is designed to flow, unmodified, from repository to UI:

- The repository would have to return `Flow<PagingData<Beer>>` instead of `Flow<List<Beer>>`.
  `PagingData` is not a value type you can inspect, transform, or compare - it's a bespoke
  stream of load/insert/drop events consumed only by `AsyncPagingDataDiffer` or a
  `LazyPagingItems` composable. That means the domain layer can't meaningfully sit between the
  repository and the UI: any use case wrapping `PagingData` becomes a pass-through, and the "no
  data-layer types past the repository boundary" rule this project follows everywhere else
  breaks specifically for paging.
- ViewModel tests lose the plain-fake pattern used everywhere else in this codebase
  (`FakeBeersRepository` + Turbine on a `StateFlow`). Asserting on `PagingData` requires
  `AsyncPagingDataDiffer` with a real or fake `ListUpdateCallback`, a different and heavier test
  setup than the rest of the suite.

`PagingMediator` avoids both: it exposes a plain `StateFlow<PagingState>` and a `Flow<List<Value>>`
for data, so the repository, domain layer, and ViewModel tests all stay in the same
plain-Flow-and-fakes style as the rest of the codebase.

## Cost accepted

Paging3 buys real things that are given up here: placeholder items for smooth scroll-to-position,
jank-free `DiffUtil`-based list diffing, built-in invalidation, and `RemoteMediator`'s
cache-vs-network load-state choreography (`REFRESH` / `PREPEND` / `APPEND`, retry, error states)
implemented and battle-tested by Google.

Giving that up means this project owns correctness, invalidation, and retry semantics itself.
That cost showed up directly: a retry mechanism (`FailedRequest`, `retryLastFailedRequest()`,
`canRetry`) was added to `PagingMediator` and then reverted because it duplicated state the
mediator already had for free (`currentKey` only advances on success, so every entry point was
already retry-safe) - a mistake that wouldn't have been possible to make inside Paging3's
`RemoteMediator`, because Paging3 owns that state itself.

## Bonus

`PagingMediator` is pure Kotlin with no Android dependency, so it ports to a future Kotlin
Multiplatform migration for free. Paging3 is an AndroidX library with no multiplatform target as
of this writing, so keeping paging logic in Paging3 would have blocked that migration path
entirely.

## Consequences

- Mid-list pagination errors (`PagingState.Error` for page N > 1) are the app's responsibility to
  surface - today they're silently swallowed while keeping the existing list on screen. A
  snackbar or footer-retry affordance for this case is follow-up work, not solved by this
  decision.
- Any future paged screen (favorites, search) needs to either reuse `PagingMediator` or accept
  writing its own equivalent - there's no `RemoteMediator`-style reusable framework backing this,
  by design.
- (Update, July 2026) `PagingMediator` now implements a small `Pager` interface and delegates
  writes to a `PagingStorage` strategy - `InMemoryPagingStorage` for network-only paging, or a
  DB-backed implementation for SSOT (the beers one upserts and never deletes, so the local-only
  `availability` field survives pull-to-refresh). Screens get their own instance from a factory
  (`BeersPagerFactory`), keeping paging state screen-scoped instead of living on the AppScope
  repository (improvements.md §12.3). Because such a cache outlives any single pager (warm
  launches) and survives refresh (upsert, no delete), a storage-derived key
  (`nextKeyFromStorage`, e.g. `rowCount / pageSize + 1`) keeps the pager's position reconciled
  with the data. Note the scoping boundary: the pager *position* is screen-scoped, but the beers
  *data* is one shared table - fine for a single paged surface; a second filtered surface
  (search, favorites) needs its own storage with parameter-scoped queries.
- (Update, July 2026) The repository's per-beer image fetch (a `GET
  /images/{id}` for every item in a page — an N+1, one list request plus 25 image requests) is
  removed. A live audit showed the `brewbuddy.dev` list response embeds `image.url` directly
  (verified non-null across all 206 beers), so `BeersApiResponseItem` gained an embedded `image`
  and `BeersMapper` reads that URL; `image_id`, `enrichBeerWithImage`, and the whole
  `getImage`/`ImageResponse` chain are deleted. A page now
  costs one request instead of 26. This is data-layer groundwork, independent of the paging state
  machine, but it directly protects the rate-limit budget (60/min) that Paging 2.0's
  search-as-you-type spends deliberately.
- (Update, July 2026 — Paging 2.0 Phase 3) The "any future paged screen needs to reuse
  `PagingMediator` or write its own" consequence has now been exercised twice and held: search
  (an in-memory `q=` surface) and browse-by-style/brewery (in-memory `typology.id=`/`brewery.id=`
  surfaces inside the on-demand `beerbrowse` module) each shipped as a `BeersQuery` value plus the
  shared `PagedListReducer`/footer UI, with **zero changes to `core-common`** — the plug-and-play
  goal Paging 2.0 set as its acceptance test. The deliberate counter-example landed alongside:
  styles (17 rows) and breweries (38 rows) are plain unpaged fetches, because a pager for a list
  that small is machinery without a customer. Pagers are per-query values (a changed query is a
  new pager, never a mutation), which is what keeps invalidation out of the mediator.
- Revisit if Paging3 ships a multiplatform target and a `PagingData`-free consumption API; until
  then this is the right trade for a KMP-bound clean-architecture codebase.
