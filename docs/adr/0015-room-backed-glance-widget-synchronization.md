# 0015: Synchronize Glance widgets from Room at active and dormant boundaries

## Status

Accepted.

## Context

The Favorites home-screen widget renders data whose source of truth is Room. Its first implementation
read the favorite beers before calling `provideContent()` and captured that immutable list in the
Glance composition. That works when a widget session is created, but not when Glance recomposes an
existing session: `updateAll()` can update the active composition without rerunning
`provideGlance()`, so the composition can publish its original list again.

The observable failure was state that did not converge after rapid committed changes, particularly
empty to two favorites and back to empty. Resizing or recreating the widget appeared to repair it
because doing so started a new session and took a new database snapshot.

A workaround used app lifecycle broadcasts, receiver-owned asynchronous work, polling and debounce
state. It introduced a second synchronization protocol beside Room, tied correctness to screen
lifecycle timing, and still could not guarantee that an update happened after the database commit.

Android app-widget processes are not permanently resident. A Room collector inside an active Glance
composition handles changes while that session exists, but it cannot start a dormant widget session.
Conversely, `updateAll()` can start or update dormant sessions, but does not make an immutable active
composition observe later database values. Both boundaries must be handled.

## Decision

**Room remains the only favorite-state source of truth, with one shared observable widget
projection.** The projection uses the repository's ordered favorite Flow, maps it to the at-most-three
items the widget can display, and applies `distinctUntilChanged()` after that mapping. Changes below
the visible three items therefore do not schedule unnecessary widget work.

**An active Glance composition collects that projection.** `provideGlance()` converts the projection
to observable state and `provideContent()` reads it with Compose Runtime `collectAsState()`. No
favorite snapshot is captured before the content is installed. Image requests are bounded to the
rendered size, loaded concurrently, cancelled when a newer database emission supersedes them, and
fall back after a short timeout so image work cannot indefinitely block a text-state update.

**An application-scoped synchronizer starts or updates dormant widget sessions.** It collects the
same visible projection and calls `FavoritesWidget.updateAll()` after each distinct emission. Each
update failure is logged without terminating future collection; coroutine cancellation still
propagates. `BillionBeersApplication` owns this graph-bound job because favorite mutations occur while
the application process is active and Glance receivers are not a reliable lifetime for a permanent
collector.

Activating a replacement application graph cancels the old synchronizer before starting one against
the replacement repository and dispatcher. This is required for debug graph rebuilds and
instrumentation tests as well as normal startup.

The widget provider keeps Android's 30-minute periodic interval only as eventual reconciliation for a
narrow process-death window. It is not the normal update mechanism and is not expected to provide
interactive latency.

## Rejected alternatives

### Call `updateAll()` from every favorite mutation

This couples domain or data operations to an Android presentation surface and makes correctness
depend on every present and future mutation path remembering the side effect. It also does not fix an
active composition that captured an immutable value.

### Broadcast favorite changes from screens or ViewModels

A broadcast duplicates Room's invalidation mechanism, can run before the transaction commits, and
ties widget correctness to UI lifecycle and event delivery. Availability and future background
changes would require additional event plumbing.

### Keep a collector in `GlanceAppWidgetReceiver`

A broadcast receiver provides bounded work, not an application-lifetime service. Manual `goAsync()`
coordination adds pending-result and cancellation races without making the process permanent.

### Poll or debounce snapshots

Polling adds latency and repeated database work while retaining timing windows. Debouncing can skip
intermediate values but cannot repair an observer attached to the wrong lifecycle or an active
composition that does not observe state.

### Use only WorkManager or `updatePeriodMillis`

These are suitable for eventual maintenance, not immediate convergence after an in-app transaction.
Platform periodic app-widget updates are also clamped to a long interval.

## Consequences

- Active and dormant widget sessions use complementary mechanisms but consume the same Room-backed
  visible projection.
- The application owns one graph-bound synchronization scope and must restart it whenever the active
  graph changes.
- The first repository emission may call `updateAll()` even when no widget is pinned; Glance handles
  the empty target set, and avoiding this small call is not worth introducing another state source.
- The widget displays at most three ordered favorites and refreshes when their identity, name, image
  URL or availability changes.
- A failed Glance or WorkManager update does not permanently stop later updates.
- The 30-minute provider interval is recovery only; correctness during normal app use does not depend
  on it.
- Tests cover the visible projection, synchronizer emissions and failure recovery, and Room emissions
  after committed writes. Active-session recomposition is additionally checked on a pinned launcher
  widget because the available Glance JVM harness did not observe state-driven node-tree
  recomposition reliably.

## Evidence

A pinned widget was exercised without resizing or recreation through empty, two favorites, and empty
again. The two-item state and final empty state were observed 27 seconds apart, within one active
Glance-session interval. The Room managed-device test separately verifies ordered favorite,
availability and removal emissions after committed writes.

## Reconsideration criteria

Revisit this design if Glance provides a documented process-independent observable data integration
that both updates active compositions and wakes dormant widget sessions, or if favorites can be
mutated by another process while the application observer is absent. Until then, moving the trigger
to individual writers or receiver lifecycle code would weaken the single-source-of-truth boundary.

## Related

- `docs/adr/0012-dispatcher-placement.md` — dispatcher ownership and application-scope work.
- `app/src/main/java/com/simtop/billionbeers/widget/FavoritesWidget.kt`
- `app/src/main/java/com/simtop/billionbeers/widget/FavoritesWidgetSynchronizer.kt`
- `app/src/main/java/com/simtop/billionbeers/BillionBeersApplication.kt`
