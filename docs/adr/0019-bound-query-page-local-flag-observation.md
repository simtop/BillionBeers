# 0019: Bound query page flag observation to result IDs

## Status

Accepted for Room-backed query pages.

## Context

Query pages combine remote beer results with local availability and favorite state. The pager keeps
remote results as its page data, then overlays those two fields from local storage. Before this
change, the overlay observed every stored beer and rebuilt an ID map whenever any stored row changed.
An update to an unrelated cached beer therefore made the query page read and map the full local
catalog.

The query page already knows the IDs it displays. Room can observe only those rows and project only
the ID, availability, and favorite columns. Other storage backends may not support an indexed
projection, so the storage API needs a full-observation fallback.

## Decision

Observe local flags for the current query result IDs. The Room implementation uses a query over those
IDs and returns only the three required columns. If the page changes, cancel the previous flag
observation and subscribe to the new ID set.

Keep the full-snapshot fallback in `BeersStorage` and `BeersRepository` for backends without an
indexed read. This fallback filters the full beer observation by ID. It preserves behavior for the
browser backend, but does not bound its work by the query result count.

When a local flag observation fails, keep the last successfully observed flags visible and retry
after the shared one-second delay. Rethrow coroutine cancellation so a replaced page or a stopped
collector does not retry as an error.

## Measurement

The AndroidX microbenchmark toggles the favorite flag on a cached beer outside a query page of 25
results. It records the update and the resulting page emission with an in-memory Room database.
Each size ran 50 measured iterations on a Pixel 8 with Android 17, SDK 37.

| Unrelated cached rows | Before: time (ms) | Before: allocations | After: time (ms) | After: allocations |
|---:|---:|---:|---:|---:|
| 250 | 21.46 | 21,177 | 5.29 | 699 |
| 2,500 | 51.29 | 189,933 | 4.07 | 699 |
| 25,000 | 199.99 | 1,877,452 | 4.97 | 699 |

For the measured Room path, time and allocations no longer grow with unrelated cached rows. The
timing coefficient of variation ranged from 0.033 to 0.205, so the table is evidence for the
observed trend rather than a device-independent latency guarantee.

## Consequences

- A favorite or availability change to a visible query result updates its overlay from Room.
- An update to an unrelated row does not make the Room query page materialize the full catalog.
- Query page membership, order, and paging bookmarks remain owned by the existing pager.
- Backends using the default storage method still scan their full stored beer observation.
- A transient local read failure does not clear the last good overlay or permanently end observation.

## Revisit triggers

Revisit this decision if a backend gains an indexed flag query, the browser catalog size makes its
fallback measurable, or the query result needs additional local-only fields. Benchmark that backend
before claiming that its work is bounded by the visible result count.

## Related

- [Hand-rolled paging](0002-hand-rolled-paging.md)
- [Incremental KMP targets and platform adapters](0016-incremental-kmp-targets-and-platform-adapters.md)
