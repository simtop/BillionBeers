package com.simtop.beerdomain.fakes

import com.simtop.beerdomain.domain.errors.UpdateAvailabilityError
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.beerdomain.domain.repositories.BeersRepository
import com.simtop.core.core.Either
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeBeersRepository(initialBeers: List<Beer> = emptyList()) : BeersRepository {

  private val beersFlow = MutableStateFlow<List<Beer>>(initialBeers)

  // Helper to inspect state
  fun getBeers(): List<Beer> = beersFlow.value

  fun setBeers(beers: List<Beer>) {
    beersFlow.value = beers
  }

  private var exceptionToThrow: Exception? = null

  fun setExceptionToThrow(exception: Exception?) {
    exceptionToThrow = exception
  }

  override suspend fun countDBEntries(): Int {
    return beersFlow.value.size
  }

  override fun observeBeers(): Flow<List<Beer>> {
    return beersFlow
  }

  override suspend fun getAllBeersFromDB(): List<Beer> {
    return beersFlow.value
  }

  override suspend fun getBeerById(id: String): Beer? {
    return beersFlow.value.find { it.id == id }
  }

  /** Mirrors the real DAO contract: keyed upsert that never touches the local-only availability. */
  override suspend fun insertAllToDB(beers: List<Beer>) {
    val current = beersFlow.value.toMutableList()
    beers.forEach { newBeer ->
      val index = current.indexOfFirst { it.id == newBeer.id }
      if (index != -1) {
        current[index] = newBeer.copy(availability = current[index].availability)
      } else {
        current.add(newBeer)
      }
    }
    beersFlow.value = current
  }

  override suspend fun updateAvailability(beer: Beer): Either<UpdateAvailabilityError, Unit> {
    exceptionToThrow?.let {
      return Either.Left(UpdateAvailabilityError.Unknown(it))
    }
    val currentList = beersFlow.value.toMutableList()
    val index = currentList.indexOfFirst { it.id == beer.id }
    if (index != -1) {
      currentList[index] = beer
      beersFlow.value = currentList
    } else {
      currentList.add(beer)
      beersFlow.value = currentList
    }
    return Either.Right(Unit)
  }

  override suspend fun getListOfBeerFromApi(page: Int): List<Beer> {
    return emptyList()
  }
}
