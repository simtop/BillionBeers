package com.simtop.beerdomain.domain.repositories

import com.simtop.beerdomain.domain.errors.UpdateAvailabilityError
import com.simtop.beerdomain.domain.models.Beer
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
   * [Beer.availability] is local-only (the API has no such field), so an upsert of freshly fetched
   * beers never overwrites a locally edited availability.
   */
  suspend fun insertAllToDB(beers: List<Beer>)

  suspend fun updateAvailability(beer: Beer): Either<UpdateAvailabilityError, Unit>

  suspend fun getListOfBeerFromApi(page: Int): List<Beer>
}
