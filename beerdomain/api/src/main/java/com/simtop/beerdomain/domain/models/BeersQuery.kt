package com.simtop.beerdomain.domain.models

/**
 * The identity of a paged beers surface: what to fetch, as a value. A pager is created *for* a
 * query, and a changed query means a *new* pager, not a mutation - which keeps invalidation out of
 * the mediator (see `BeersPagerFactory`).
 *
 * Phase 2 wires only [search] (`q=`); language stays on the `LanguageProvider` the way the catalog
 * does it, so it isn't part of the query. Style/brewery/abv/sort filters are Phase 3 - additive and
 * non-breaking when they arrive.
 */
data class BeersQuery(val search: String)
