package com.simtop.beer_data.repositories

import com.simtop.beer_data.mappers.BeersMapper
import com.simtop.beer_database.localsources.BeersLocalSource
import com.simtop.beer_network.remotesources.BeersRemoteSource
import com.simtop.beerdomain.domain.errors.FetchBeersError
import com.simtop.beerdomain.domain.errors.UpdateAvailabilityError
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.beerdomain.domain.models.BeerPage
import com.simtop.beerdomain.domain.models.BeerStyle
import com.simtop.beerdomain.domain.models.BeersQuery
import com.simtop.beerdomain.domain.models.Brewery
import com.simtop.beerdomain.domain.models.CatalogCacheStatus
import com.simtop.beerdomain.domain.repositories.BeersRepository
import com.simtop.core.core.CachePolicy
import com.simtop.core.core.Either
import com.simtop.core.core.LanguageProvider
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

@SingleIn(AppScope::class)
@Inject
class BeersRepositoryImpl(
  private val beersRemoteSource: BeersRemoteSource,
  private val beersLocalSource: BeersLocalSource,
  private val beersMapper: BeersMapper,
  private val languageProvider: LanguageProvider,
) : BeersRepository {

  override suspend fun getBeersPageFromApi(page: Int, query: BeersQuery): BeerPage {
    val remotePage =
      beersRemoteSource.getListOfBeers(page, query.search, query.styleId, query.breweryId)
    return BeerPage(
      items = remotePage.items.map { beersMapper.fromBeersApiResponseItemToBeer(it) },
      totalCount = remotePage.totalCount,
    )
  }

  override suspend fun getBeerStyles(): Either<FetchBeersError, List<BeerStyle>> = fetchClassified {
    beersRemoteSource.getTypologies().map { beersMapper.fromTypologyToBeerStyle(it) }
  }

  override suspend fun getBreweries(): Either<FetchBeersError, List<Brewery>> = fetchClassified {
    beersRemoteSource.getBreweries().map { beersMapper.fromBreweryApiResponseItemToBrewery(it) }
  }

  /**
   * Unpaged fetches have no pager to classify their errors, so the repository does it - with the
   * same shared mapping the pager paths use.
   */
  @Suppress("TooGenericExceptionCaught")
  private inline fun <T> fetchClassified(fetch: () -> T): Either<FetchBeersError, T> =
    try {
      Either.Right(fetch())
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Either.Left(e.toFetchBeersError())
    }

  @Suppress("TooGenericExceptionCaught")
  override suspend fun updateAvailability(beer: Beer): Either<UpdateAvailabilityError, Unit> {
    return try {
      // Upsert, not update: a beer reached through search/browse may not be in the catalog cache
      // yet, and a zero-row UPDATE would silently drop the user's edit.
      beersLocalSource.upsertAvailability(beersMapper.fromBeerToBeerDbModel(beer))
      Either.Right(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Either.Left(UpdateAvailabilityError.Unknown(e))
    }
  }

  override suspend fun insertAllToDB(beers: List<Beer>) =
    beersLocalSource.insertAllToDB(beers.map { beersMapper.fromBeerToBeerDbModel(it) })

  override suspend fun insertPage(
    beers: List<Beer>,
    surface: String,
    nextKey: Int?,
    totalCount: Int?,
  ) =
    beersLocalSource.insertPageToDB(
      beers.map { beersMapper.fromBeerToBeerDbModel(it) },
      surface,
      nextKey,
      totalCount,
    )

  override suspend fun pagingNextKey(surface: String): Int? =
    beersLocalSource.getPagingState(surface)?.nextKey

  override suspend fun catalogCacheStatus(policy: CachePolicy): CatalogCacheStatus {
    if (beersLocalSource.getCountFromDB() == 0) return CatalogCacheStatus.Empty
    val state = beersLocalSource.getPagingState(catalogSurface(languageProvider))
    return when {
      // No bookmark for this language: either a legacy cache from before paging_state existed
      // (age unknowable -> treat as stale) or bookmarks that all belong to another language.
      state == null ->
        if (beersLocalSource.countPagingStates() == 0) CatalogCacheStatus.Stale
        else CatalogCacheStatus.LanguageMismatch
      System.currentTimeMillis() - state.refreshedAt > policy.staleAfter.inWholeMilliseconds ->
        CatalogCacheStatus.Stale
      else -> CatalogCacheStatus.Fresh
    }
  }

  override fun observeBeers(): Flow<List<Beer>> =
    beersLocalSource.getAllBeersFromDB().map { list ->
      list.map { beersMapper.fromBeerDbModelToBeer(it) }
    }

  override suspend fun getAllBeersFromDB() = observeBeers().first()

  override suspend fun getBeerById(id: String): Beer? = getAllBeersFromDB().find { it.id == id }

  override suspend fun countDBEntries() = beersLocalSource.getCountFromDB()
}
