package com.simtop.beer_data.repositories

import com.simtop.beer_network.network.BeersService
import com.simtop.beerdomain.domain.errors.FetchBeersError
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.beerdomain.domain.repositories.BeersPagerFactory
import com.simtop.beerdomain.domain.repositories.BeersRepository
import com.simtop.core.core.Pager
import com.simtop.core.core.PagingMediator
import com.simtop.core.core.PagingStorage
import dev.zacsweers.metro.Inject
import java.io.IOException
import java.net.HttpURLConnection
import kotlinx.coroutines.flow.Flow
import retrofit2.HttpException

@Inject
class BeersPagerFactoryImpl(private val repository: BeersRepository) : BeersPagerFactory {

  override fun create(): Pager<Beer, FetchBeersError> =
    PagingMediator(
      initialKey = FIRST_PAGE,
      nextKey = { currentKey, _ -> currentKey + 1 },
      fetchRemote = { page -> repository.getListOfBeerFromApi(page) },
      classifyError = { it.toFetchBeersError() },
      storage = BeersPagingStorage(repository),
      // The Room cache both outlives this pager (warm launch) and survives refresh (the storage
      // upserts, never deletes). Either way the next unseen page comes after everything cached -
      // N fully cached pages mean page N+1 - otherwise "load more" would silently re-fetch every
      // cached page over the network before the list could grow.
      nextKeyFromStorage = {
        repository.countDBEntries() / BeersService.DEFAULT_ITEMS_PER_PAGE + FIRST_PAGE
      },
    )

  private fun Throwable.toFetchBeersError(): FetchBeersError =
    when (this) {
      is HttpException ->
        when (code()) {
          HttpURLConnection.HTTP_NOT_FOUND -> FetchBeersError.NotFound
          HttpURLConnection.HTTP_FORBIDDEN -> FetchBeersError.Forbidden
          else -> FetchBeersError.Unknown(this)
        }
      is IOException -> FetchBeersError.Network
      else -> FetchBeersError.Unknown(this)
    }

  private companion object {
    const val FIRST_PAGE = 1
  }
}

/**
 * Room-backed SSOT storage for the single full-catalog beers surface. Availability is a local-only
 * field (the API doesn't have it), so every write - refresh included - goes through the keyed
 * upsert that updates all columns *except* availability. Refresh deliberately never deletes rows: a
 * delete-and-reinsert would reset every locally edited availability back to the default. The
 * accepted trade-off is that beers removed server-side linger in the cache until the next
 * reinstall/clear-data. Because [storeFirstPage] merges instead of replacing, the factory pairs
 * this storage with a `nextKeyFromStorage` so the pager's position tracks the cache, not the
 * session.
 *
 * Scoping: this storage owns the whole beers table only because the catalog list is the app's one
 * paged surface. A second, differently-filtered paged surface (search, favorites) must get its own
 * storage whose observe/write/count queries are keyed by that surface's parameters - it must not
 * reuse this one, or the two screens' data and resume positions would bleed into each other.
 */
private class BeersPagingStorage(private val repository: BeersRepository) : PagingStorage<Beer> {

  override val data: Flow<List<Beer>> = repository.observeBeers()

  override suspend fun storeFirstPage(page: List<Beer>) = repository.insertAllToDB(page)

  override suspend fun append(page: List<Beer>) = repository.insertAllToDB(page)
}
