# 0002: Hand-rolled `PagingMediator` instead of Paging3

## Status

Accepted

## Context

The beers list is paginated: fetch page N from the network, persist it, and merge with what's
already in the local DB (SSOT). Jetpack's Paging3 library is the standard tool for exactly this
shape of problem, and it wasn't used - `core-common`'s `PagingMediator` was hand-rolled instead.
That needs a documented reason, because "we don't use Paging3" is not by itself a defensible
position; it should be "we rejected Paging3, and here's the trade-off."

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
The `docs/MASTER_PLAN.md` Part 1.2 findings are exactly that cost showing up: a retry mechanism
(`FailedRequest`, `retryLastFailedRequest()`, `canRetry`) was added and then reverted because it
duplicated state `PagingMediator` already had for free (`currentKey` only advances on success, so
every entry point was already retry-safe) - a mistake that wouldn't have been possible to make
inside Paging3's `RemoteMediator`, because Paging3 owns that state itself.

## Bonus

`PagingMediator` is pure Kotlin with no Android dependency, so it ports to Kotlin Multiplatform
for free (see `docs/FUTURE_ROADMAP.md`'s KMP migration). Paging3 is an AndroidX library with no
multiplatform target as of this writing, so keeping paging logic in Paging3 would have blocked
that migration path entirely.

## Consequences

- Mid-list pagination errors (`PagingState.Error` for page N > 1) are the app's responsibility to
  surface - today they're silently swallowed while keeping the existing list on screen. A
  snackbar or footer-retry affordance for this case is tracked as follow-up work
  (`docs/MASTER_PLAN.md` §5.1), not solved by this decision.
- Any future paged screen (favorites, search) needs to either reuse `PagingMediator` or accept
  writing its own equivalent - there's no `RemoteMediator`-style reusable framework backing this,
  by design.
- Revisit if Paging3 ships a multiplatform target and a `PagingData`-free consumption API; until
  then this is the right trade for a KMP-bound clean-architecture codebase.
