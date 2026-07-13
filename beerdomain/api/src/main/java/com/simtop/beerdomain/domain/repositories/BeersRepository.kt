package com.simtop.beerdomain.domain.repositories

import com.simtop.beerdomain.domain.errors.UpdateAvailabilityError
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.beerdomain.domain.models.BeerPage
import com.simtop.core.core.Either
import kotlinx.coroutines.flow.Flow

/**
 * Stateless data accessor for beers. Paging coordination deliberately lives elsewhere (see
 * [BeersPagerFactory]): this interface is bound as an app-scoped singleton, and a process-lifetime
 * instance must not own screen-lifetime state like a page counter.
 */
interface BeersRepository {
  suspend fun countDBEntries(): Int

  fun observeBeers(): Flow<List<Beer>>

  suspend fun getAllBeersFromDB(): List<Beer>

  suspend fun getBeerById(id: String): Beer?

  /**
   * Keyed upsert: rows are inserted, or updated column-by-column if the id already exists.
   * [Beer.availability] is treated as local-only: the server does expose an `available` field, but
   * it only *seeds* a row's initial value on first insert. An upsert of freshly fetched beers never
   * overwrites the availability of an existing id, so a locally edited value stays authoritative.
   */
  suspend fun insertAllToDB(beers: List<Beer>)

  /**
   * Same keyed upsert as [insertAllToDB], but also records the paging bookmark for [surface] (the
   * resume [nextKey] and [totalCount]) in the *same* transaction, so the pager's position can never
   * diverge from the rows actually stored. [nextKey] is merged monotonically - a refresh
   * re-fetching an earlier page never rewinds a warm cache's bookmark.
   */
  suspend fun insertPage(beers: List<Beer>, surface: String, nextKey: Int?, totalCount: Int?)

  /**
   * The stored resume key for [surface] - the first page not yet cached - or null when no bookmark
   * has been recorded yet (fresh install, or a cache written before paging_state existed). Callers
   * fall back to a row-count estimate in that case.
   */
  suspend fun pagingNextKey(surface: String): Int?

  suspend fun updateAvailability(beer: Beer): Either<UpdateAvailabilityError, Unit>

  /** Fetches one page from the API, carrying the server total for end-detection and "N of M" UI. */
  suspend fun getBeersPageFromApi(page: Int): BeerPage
}
