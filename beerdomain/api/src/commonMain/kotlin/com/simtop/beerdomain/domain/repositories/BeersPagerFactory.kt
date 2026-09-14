package com.simtop.beerdomain.domain.repositories

import com.simtop.beerdomain.domain.errors.FetchBeersError
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.beerdomain.domain.models.BeersQuery
import com.simtop.core.core.Pager

/**
 * Creates a fully wired beers pager. Each screen calls a `create` once and owns the returned
 * instance, so paging *position* (current page, end-of-list) is scoped to that screen instead of
 * living on an app-scoped singleton (docs/adr/0002-hand-rolled-paging.md).
 *
 * Two surfaces exist, and they deliberately do **not** share storage:
 * - [create] (no args) is the **catalog**: the Room-backed single source of truth, whose pager
 *   reads and writes the one beers table and resumes from the `paging_state` bookmark.
 * - [create] with a [BeersQuery] is a **query** surface (search, beers-by-style, beers-by-brewery):
 *   a per-query in-memory pager whose results die with the screen and never touch the beers table
 *   (so the catalog's `SELECT * FROM beers` view can't be polluted by filtered hits). A changed
 *   query means a new pager, not a mutation.
 *
 * The factory itself is stateless, so it can safely be an app-scoped binding.
 */
interface BeersPagerFactory {
  fun create(): Pager<Beer, FetchBeersError>

  fun create(query: BeersQuery): Pager<Beer, FetchBeersError>
}
