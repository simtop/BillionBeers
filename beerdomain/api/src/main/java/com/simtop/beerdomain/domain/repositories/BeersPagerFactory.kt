package com.simtop.beerdomain.domain.repositories

import com.simtop.beerdomain.domain.errors.FetchBeersError
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.core.core.Pager

/**
 * Creates a fully wired beers pager. Each screen calls [create] once and owns the returned
 * instance, so paging *position* (current page, end-of-list) is scoped to that screen instead of
 * living on an app-scoped singleton (docs/adr/0002-hand-rolled-paging.md).
 *
 * The paged *data* is not screen-scoped: every pager from this factory reads and writes the one
 * beers table, which is only sound while the full catalog list is the app's single paged surface. A
 * future differently-filtered surface (favorites, search) needs its own factory whose storage
 * queries are scoped by that surface's parameters - not a second pager over this table.
 *
 * The factory itself is stateless, so it can safely be an app-scoped binding.
 */
interface BeersPagerFactory {
  fun create(): Pager<Beer, FetchBeersError>
}
