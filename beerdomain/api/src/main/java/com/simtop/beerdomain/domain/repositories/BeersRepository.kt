package com.simtop.beerdomain.domain.repositories

import com.simtop.beerdomain.domain.errors.FetchBeersError
import com.simtop.beerdomain.domain.errors.UpdateAvailabilityError
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.core.core.Either
import com.simtop.core.core.PagingState
import kotlinx.coroutines.flow.Flow

interface BeersRepository {
  suspend fun countDBEntries(): Int

  suspend fun getAllBeersFromDB(): List<Beer>

  suspend fun getBeerById(id: String): Beer?

  suspend fun insertAllToDB(beers: List<Beer>)

  suspend fun updateAvailability(beer: Beer): Either<UpdateAvailabilityError, Unit>

  fun getBeersFromSingleSource(): Flow<List<Beer>>

  fun observePagingState(): Flow<PagingState<FetchBeersError>>

  suspend fun loadNextPage()

  suspend fun getListOfBeerFromApi(page: Int): List<Beer>

  suspend fun refresh()
}
