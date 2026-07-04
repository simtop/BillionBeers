package com.simtop.beer_data.repositories

import com.simtop.beer_data.mappers.BeersMapper
import com.simtop.beer_database.localsources.BeersLocalSource
import com.simtop.beer_network.models.BeersApiResponseItem
import com.simtop.beer_network.remotesources.BeersRemoteSource
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.beerdomain.domain.repositories.BeersRepository
import com.simtop.core.core.PagingMediator
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

@SingleIn(AppScope::class)
@Inject
class BeersRepositoryImpl(
  private val beersRemoteSource: BeersRemoteSource,
  private val beersLocalSource: BeersLocalSource,
  private val beersMapper: BeersMapper,
) : BeersRepository {

  // pagingMediator holds per-screen paging state (currentKey, isLastPage) on an AppScope
  // singleton repository. Only one consumer exists today (BeersListViewModel), so this is
  // latent, not active - deferred rather than extracting a Paginator abstraction for a
  // hypothetical second consumer (docs/MASTER_PLAN.md Phase 1, improvements.md §12.3).
  // Revisit if a second paged screen (favorites, search) is ever added.
  private val pagingMediator =
    PagingMediator<Int, Beer>(
      initialKey = 1,
      nextKey = { currentKey, _ -> currentKey + 1 },
      fetchRemote = { page -> fetchAndEnrichBeers(page) },
      saveLocal = { beers ->
        beersLocalSource.insertAllToDB(beers.map { beersMapper.fromBeerToBeerDbModel(it) })
      },
      fetchLocal = {
        beersLocalSource.getAllBeersFromDB().map { list ->
          list.map { beersMapper.fromBeerDbModelToBeer(it) }
        }
      }
    )

  // Fetches one image per beer (N+1). Deliberate: the API has no batch-image endpoint, so this
  // is a forced trade-off, not a bug - see docs/MASTER_PLAN.md Phase 1.
  private suspend fun fetchAndEnrichBeers(page: Int): List<Beer> {
    val apiItems = beersRemoteSource.getListOfBeers(page)
    return coroutineScope {
      apiItems
        .map { item ->
          async { enrichBeerWithImage(item) }
        }
        .awaitAll()
    }
  }

  private suspend fun enrichBeerWithImage(item: BeersApiResponseItem): Beer {
    val beer = beersMapper.fromBeersApiResponseItemToBeer(item)
    val imageUrl = item.imageId?.takeIf { it.isNotEmpty() }?.let { id ->
      runCatching { beersRemoteSource.getImage(id).url }.getOrNull()
    }
    return imageUrl?.let { beer.copy(imageUrl = it) } ?: beer
  }

  override suspend fun getListOfBeerFromApi(page: Int): List<Beer> {
    return fetchAndEnrichBeers(page)
  }

  override suspend fun updateAvailability(beer: Beer) =
    beersLocalSource.updateBeer(beer.id, beer.availability)

  override suspend fun insertAllToDB(beers: List<Beer>) =
    beersLocalSource.insertAllToDB(beers.map { beersMapper.fromBeerToBeerDbModel(it) })

  override suspend fun getAllBeersFromDB() =
    beersLocalSource.getAllBeersFromDB().first().map { beersMapper.fromBeerDbModelToBeer(it) }

  override suspend fun countDBEntries() = beersLocalSource.getCountFromDB()

  override fun getBeersFromSingleSource(): Flow<List<Beer>> {
    return pagingMediator.data.onStart {
      if (countDBEntries() == 0) {
        pagingMediator.loadFirstPage()
      }
    }
  }

  override fun observePagingState() = pagingMediator.pagingState

  override suspend fun loadNextPage() {
    pagingMediator.loadNextPage()
  }

  // Helper to trigger initial load if needed
  override suspend fun refresh() {
    pagingMediator.loadFirstPage()
  }
}
