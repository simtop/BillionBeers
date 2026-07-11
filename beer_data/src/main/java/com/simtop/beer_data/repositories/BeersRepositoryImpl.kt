package com.simtop.beer_data.repositories

import com.simtop.beer_data.mappers.BeersMapper
import com.simtop.beer_database.localsources.BeersLocalSource
import com.simtop.beer_network.models.BeersApiResponseItem
import com.simtop.beer_network.remotesources.BeersRemoteSource
import com.simtop.beerdomain.domain.errors.UpdateAvailabilityError
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.beerdomain.domain.repositories.BeersRepository
import com.simtop.core.core.Either
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

@SingleIn(AppScope::class)
@Inject
class BeersRepositoryImpl(
  private val beersRemoteSource: BeersRemoteSource,
  private val beersLocalSource: BeersLocalSource,
  private val beersMapper: BeersMapper,
) : BeersRepository {

  // Fetches one image per beer (N+1). Deliberate: the API has no batch-image endpoint, so this
  // is a forced trade-off, not a bug.
  private suspend fun fetchAndEnrichBeers(page: Int): List<Beer> {
    val apiItems = beersRemoteSource.getListOfBeers(page)
    return coroutineScope {
      apiItems.map { item -> async { enrichBeerWithImage(item) } }.awaitAll()
    }
  }

  private suspend fun enrichBeerWithImage(item: BeersApiResponseItem): Beer {
    val beer = beersMapper.fromBeersApiResponseItemToBeer(item)
    val imageUrl =
      item.imageId
        ?.takeIf { it.isNotEmpty() }
        ?.let { id -> runCatching { beersRemoteSource.getImage(id).url }.getOrNull() }
    return imageUrl?.let { beer.copy(imageUrl = it) } ?: beer
  }

  override suspend fun getListOfBeerFromApi(page: Int): List<Beer> {
    return fetchAndEnrichBeers(page)
  }

  @Suppress("TooGenericExceptionCaught")
  override suspend fun updateAvailability(beer: Beer): Either<UpdateAvailabilityError, Unit> {
    return try {
      beersLocalSource.updateBeer(beer.id, beer.availability)
      Either.Right(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Either.Left(UpdateAvailabilityError.Unknown(e))
    }
  }

  override suspend fun insertAllToDB(beers: List<Beer>) =
    beersLocalSource.insertAllToDB(beers.map { beersMapper.fromBeerToBeerDbModel(it) })

  override fun observeBeers(): Flow<List<Beer>> =
    beersLocalSource.getAllBeersFromDB().map { list ->
      list.map { beersMapper.fromBeerDbModelToBeer(it) }
    }

  override suspend fun getAllBeersFromDB() = observeBeers().first()

  override suspend fun getBeerById(id: String): Beer? = getAllBeersFromDB().find { it.id == id }

  override suspend fun countDBEntries() = beersLocalSource.getCountFromDB()
}
