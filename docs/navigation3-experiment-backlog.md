# Navigation 3 experiment backlog

This document records bounded follow-up experiments after the common Navigation 3 evaluation in
[ADR 0017](adr/0017-common-navigation-3-evaluation.md). It is a queue of falsifiable engineering
questions, not authorization to migrate the application or replace host-specific navigation.

## Current decision

The evaluation established a **shared foundation, platform-owned hosts** boundary:

- Portable route identity, serialization, and back-stack semantics may be shared.
- Android keeps `NavDisplay`, adaptive list/detail scenes, and Play dynamic-feature installation.
- Web, iOS, and Desktop keep `SharedAppShell` and their host adapters for browser history, scene
  lifecycle, window lifecycle, and platform input/back behavior.
- Production destinations are not migrated merely because the Navigation 3 runtime resolves from
  common source.

The completed proofs are recorded in ADR 0017 and include common runtime resolution, portable route
state restoration, Web hash history behavior, root retention, and independent tab back stacks.

## Experiment rules

Every new experiment must:

1. name one host behavior or production requirement that is currently uncertain;
2. start from the existing host implementation and a representative user journey;
3. compare behavior, dependency/package cost, lifecycle ownership, and accessibility—not only
   screenshots or compilation;
4. keep route contracts and host adapters independently revertible;
5. avoid a second persistence format, whole-domain objects in public URLs, backend work, or a broad
   navigation rewrite;
6. record a retain/adopt/defer decision with the evidence that caused it.

A missing device, browser, framework linker, or deployment environment leaves the claim unproven; it
is not a passing result.

## Bounded follow-up queue

### N3-1 — Browser cold deep links and missing-cache behavior

**Question:** Can a portable route enter the Web application directly, reload safely, and produce an
explicit result when the route refers to data that is not in the local cache?

**Scope:** One detail route and one browse-selection route. Exercise direct hash entry, reload,
malformed values, missing local beer/category data, browser Back/Forward, and URL length/public-data
constraints. Keep the existing hash history authority.

**Pass condition:** The visible route and browser history remain coherent, missing data has an
intentional localized outcome, and no whole domain record is placed in the URL.

**Reject/defer when:** The route requires an implicit network/cache contract that is not defined, or
history and application state can diverge without a host-specific repair.

### N3-2 — iOS scene recreation and native Back integration

**Question:** Does a portable route/back-stack adapter preserve state through the supported iOS scene
lifecycle and cooperate with native back gestures/buttons?

**Scope:** Use the existing `IosAppSession` and route request buffer. Test creation, recreation,
background/foreground where the host supports it, deep-link delivery before collection, Back ordering,
and idempotent close. Do not replace the Compose view-controller ownership model.

**Pass condition:** Route requests are neither lost nor duplicated, close remains idempotent, and
native Back produces the same visible stack transitions as the current host.

**Reject/defer when:** Scene ownership or native gesture timing requires a second navigation owner or
lifecycle-specific state that the common layer cannot model without leaking iOS types.

### N3-3 — Desktop window lifecycle and keyboard Back

**Question:** Can a Desktop adapter retain and dispose route entries correctly across window close,
reopen, and keyboard Back without making the common layer own window state?

**Scope:** One window and one reopened window using the existing `SharedAppShell`. Exercise keyboard
Back/Escape semantics, focus restoration, route replacement, window close, and a second window only if
the product supports it.

**Pass condition:** Closed windows release their entry owners, reopened windows restore only the
intended state, and keyboard/focus behavior remains host-owned and accessible.

**Reject/defer when:** Desktop window ownership cannot be separated from application navigation state
without introducing a global lifecycle registry or a second persistence format.

### N3-4 — Android dynamic-feature failure and restoration proof

**Question:** Does a Navigation 3 route adapter preserve Android's on-demand delivery boundary when
installation is cancelled or fails during navigation and when the activity is recreated?

**Scope:** Use the existing `rememberDynamicFeatureNavigator` and managed install path. Exercise
pending, downloading, confirmation, cancellation, failure, successful installation, Back, and
recreation. Do not move feature providers or feature UI into common source.

**Pass condition:** A missing split never renders feature content, failed/cancelled requests leave no
invalid back-stack entry, and successful installation restores the intended destination exactly once.

**Reject/defer when:** Common route handling would eagerly link a dynamic feature or make installation
state indistinguishable from destination state.

### N3-5 — One low-risk production route adapter

**Question:** Does one non-critical destination gain enough value from a Navigation 3-backed adapter to
justify production adoption after the host proofs are complete?

**Prerequisites:** The relevant host-specific experiment is green, route restoration and accessibility
are already covered, and a rollback path is identified.

**Scope:** Choose one route with no dynamic delivery or large domain payload. Keep the current host
available behind a small adapter boundary and migrate only that destination. Do not combine this with
a database migration, broad UI extraction, or another navigation rewrite.

**Pass condition:** The destination has equivalent or better lifecycle, Back, restoration, accessibility,
package, and test evidence on every claimed host.

**Reject/defer when:** The adapter exists only for symmetry, increases ownership ambiguity, or has no
measurable user or maintenance benefit over the current host.

## Explicitly out of scope

The following are not Navigation 3 experiments in this repository unless their premises change:

- replacing every host with one identical Jetpack Navigation UI layer;
- moving Android dynamic-feature delivery into common source;
- adding `SavedFilterResults` to `PortableRoute` before Web, iOS, and Desktop semantics are defined;
- introducing a second serialized state or persistence format;
- adding authentication, remote writes, analytics, crash reporting, or a backend;
- using Navigation 3 as a reason to redesign the shared application shell.

## Relationship to product work

Combined filters (audit item S17) are a separate product extensibility slice, not a Navigation 3
migration task. It should reuse the existing `BeersQuery` value and pager identity, and it should not
change Android navigation or add a query DSL. Navigation experiments may later verify that the chosen
filter state restores correctly on each host, but filter composition should be implemented and tested
independently first.
