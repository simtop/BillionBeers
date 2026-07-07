package com.simtop.beer_data.repositories

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
 * Room-backed SSOT storage. Availability is a local-only field (the API doesn't have it), so every
 * write - refresh included - goes through the keyed upsert that updates all columns *except*
 * availability. Refresh deliberately never deletes rows: a delete-and-reinsert would reset every
 * locally edited availability back to the default. The accepted trade-off is that beers removed
 * server-side linger in the cache until the next reinstall/clear-data.
 */
private class BeersPagingStorage(private val repository: BeersRepository) : PagingStorage<Beer> {

  override val data: Flow<List<Beer>> = repository.observeBeers()

  override suspend fun replaceAll(page: List<Beer>) = repository.insertAllToDB(page)

  override suspend fun append(page: List<Beer>) = repository.insertAllToDB(page)
}
