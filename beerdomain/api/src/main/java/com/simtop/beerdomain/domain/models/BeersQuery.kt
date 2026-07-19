package com.simtop.beerdomain.domain.models

/**
 * The identity of a paged beers surface: what to fetch, as a value. A pager is created *for* a
 * query, and a changed query means a *new* pager, not a mutation - which keeps invalidation out of
 * the mediator (see `BeersPagerFactory`).
 *
 * [search] maps to `q=`, [styleId] to `typology.id=`, [breweryId] to `brewery.id=`. The API ANDs
 * whatever is present, so combinations are legal even though each screen sets exactly one. Language
 * stays on the `LanguageProvider` the way the catalog does it, so it isn't part of the query.
 */
data class BeersQuery(
  val search: String? = null,
  val styleId: String? = null,
  val breweryId: String? = null,
)
