package com.simtop.beerdomain.fakes

import com.simtop.beerdomain.domain.errors.FetchBeersError
import com.simtop.beerdomain.domain.errors.UpdateAvailabilityError
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.beerdomain.domain.models.BeerPage
import com.simtop.beerdomain.domain.models.BeerStyle
import com.simtop.beerdomain.domain.models.BeersQuery
import com.simtop.beerdomain.domain.models.Brewery
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

  private val pagingNextKeys = mutableMapOf<String, Int?>()

  /** Test helper: seed a stored resume bookmark for [surface] as a warm cache would leave. */
  fun setPagingNextKey(surface: String, nextKey: Int?) {
    pagingNextKeys[surface] = nextKey
  }

  /** Mirrors the DAO: upsert the rows and merge the bookmark monotonically in one step. */
  override suspend fun insertPage(
    beers: List<Beer>,
    surface: String,
    nextKey: Int?,
    totalCount: Int?,
  ) {
    insertAllToDB(beers)
    pagingNextKeys[surface] = listOfNotNull(pagingNextKeys[surface], nextKey).maxOrNull()
  }

  override suspend fun pagingNextKey(surface: String): Int? = pagingNextKeys[surface]

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

  var apiPage: BeerPage = BeerPage(emptyList(), null)

  /** Records every (page, query) the pager fetched, so tests can assert what was requested. */
  val apiRequests = mutableListOf<Pair<Int, BeersQuery>>()

  override suspend fun getBeersPageFromApi(page: Int, query: BeersQuery): BeerPage {
    apiRequests += page to query
    return apiPage
  }

  var beerStyles: Either<FetchBeersError, List<BeerStyle>> = Either.Right(emptyList())

  var breweries: Either<FetchBeersError, List<Brewery>> = Either.Right(emptyList())

  override suspend fun getBeerStyles(): Either<FetchBeersError, List<BeerStyle>> = beerStyles

  override suspend fun getBreweries(): Either<FetchBeersError, List<Brewery>> = breweries
}
