package com.simtop.beerdomain.domain.repositories

import com.simtop.beerdomain.domain.errors.FetchBeersError
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.core.core.Pager

/**
 * Creates a fully wired beers pager. Each screen calls [create] once and owns the returned
 * instance, so paging state (current page, end-of-list) is scoped to that screen: a future second
 * paged surface (favorites, search) gets its own pager instead of sharing - and corrupting - a
 * singleton's page counter, docs/adr/0002-hand-rolled-paging.md).
 *
 * The factory itself is stateless, so it can safely be an app-scoped binding.
 */
interface BeersPagerFactory {
  fun create(): Pager<Beer, FetchBeersError>
}
