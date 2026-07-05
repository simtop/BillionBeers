package com.simtop.beerdomain.fakes

import com.simtop.beerdomain.domain.errors.FetchBeersError
import com.simtop.beerdomain.domain.errors.UpdateAvailabilityError
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.beerdomain.domain.repositories.BeersRepository
import com.simtop.core.core.Either
import com.simtop.core.core.PagingState
import kotlin.collections.plus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

@Suppress("TooManyFunctions")
class FakeBeersRepository(initialBeers: List<Beer> = emptyList()) : BeersRepository {

  private val beersFlow = MutableStateFlow<List<Beer>>(initialBeers)
  private val pagingStateFlow = MutableStateFlow<PagingState<FetchBeersError>>(PagingState.Idle)

  // Helper to inspect state
  fun getBeers(): List<Beer> = beersFlow.value

  fun getPagingState(): PagingState<FetchBeersError> = pagingStateFlow.value

  fun setBeers(beers: List<Beer>) {
    beersFlow.value = beers
  }

  fun setPagingState(state: PagingState<FetchBeersError>) {
    pagingStateFlow.value = state
  }

  private var exceptionToThrow: Exception? = null

  fun setExceptionToThrow(exception: Exception?) {
    exceptionToThrow = exception
  }

  override suspend fun countDBEntries(): Int {
    return beersFlow.value.size
  }

  override suspend fun getAllBeersFromDB(): List<Beer> {
    return beersFlow.value
  }

  override suspend fun getBeerById(id: String): Beer? {
    return beersFlow.value.find { it.id == id }
  }

  override suspend fun insertAllToDB(beers: List<Beer>) {
    beersFlow.value = beersFlow.value + beers
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

  override fun getBeersFromSingleSource(): Flow<List<Beer>> {
    return beersFlow
  }

  override fun observePagingState(): Flow<PagingState<FetchBeersError>> {
    return pagingStateFlow
  }

  override suspend fun loadNextPage() {
    pagingStateFlow.value = PagingState.LoadingNextPage
    pagingStateFlow.value = PagingState.Success
  }

  override suspend fun getListOfBeerFromApi(page: Int): List<Beer> {
    return emptyList()
  }

  override suspend fun refresh() {}
}
