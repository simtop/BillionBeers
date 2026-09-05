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

The evaluated Paging3 integration exposed `Flow<PagingData<Beer>>` across the repository/UI
boundary. This project instead chooses plain data and explicit paging state as its contract:

- A `List<Beer>` and a `PagingState` are ordinary values that our domain fakes, reducers and
  ViewModel tests can inspect directly. Adopting a paging-library-specific stream would change
  that contract or require an adapter whose complexity would need to earn its place.
- The existing `FakeBeersRepository`/pager fakes and Turbine assertions share one testing model
  across paged and unpaged screens. Keeping that model is a project preference, not a claim that
  Paging3 cannot be transformed or tested without a UI differ.

The decision does not prohibit domain logic around a paging library. A pass-through use case is
unnecessary under ADR 0003 regardless of which paging implementation is behind the repository.

`PagingMediator` preserves this contract: it exposes a plain `StateFlow<PagingState>` and a `Flow<List<Value>>`
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

`PagingMediator` has no Android dependency, which reduces one migration constraint. This does not
make the whole `:core-common` module multiplatform for free: its build, platform-specific APIs and
tests still need a target-by-target audit. Do not infer an alternative library's target support
from its AndroidX name; verify the artifacts and test APIs available when a migration is proposed.

## Consequences

- Mid-list pagination errors remain the app's responsibility. This follow-up is complete:
  `PagedListReducer` keeps existing items visible with `PagedListFooter.Retry`, and the shared
  `LoadMoreRetryFooter` provides explicit retry. Do not reopen it as missing error UI.
- Future paged screens reuse the screen-scoped `Pager`/`PagingStorage` and `BeersPagerFactory`
  contracts. Search and browse already exercise that reuse; a new filtered surface is not a
  reason to build a second paging framework.
- (Update, July 2026) `PagingMediator` now implements a small `Pager` interface and delegates
  writes to a `PagingStorage` strategy - `InMemoryPagingStorage` for network-only paging, or a
  DB-backed implementation for SSOT (the beers one upserts and never deletes, so the local-only
  `availability` field survives pull-to-refresh). Screens get their own instance from a factory
  (`BeersPagerFactory`), keeping paging state screen-scoped instead of living on the AppScope
  repository. Because such a cache outlives any single pager (warm
  launches) and survives refresh (upsert, no delete), `nextKeyFromStorage` reads an exact
  surface-scoped bookmark from `paging_state`, written transactionally with the page. The bookmark
  is authoritative when present. On a miss, `BeersPagerFactoryImpl` restarts at page 1 for a
  language mismatch and retains the row-count estimate for a fresh/legacy cache; the heuristic
  has not been removed entirely. Search and browse use per-query in-memory storage instead.
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
- Revisit if a concrete product requirement exceeds this pager's supported behavior, or a measured
  alternative reduces its correctness/maintenance cost while preserving the required boundaries.
  Neither a hypothetical KMP migration nor an old claim about library target support is a trigger
  by itself.
