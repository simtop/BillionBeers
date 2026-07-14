package com.simtop.beer_data.repositories

import com.simtop.beer_network.network.BeersService
import com.simtop.beerdomain.domain.errors.FetchBeersError
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.beerdomain.domain.models.BeersQuery
import com.simtop.beerdomain.domain.repositories.BeersPagerFactory
import com.simtop.beerdomain.domain.repositories.BeersRepository
import com.simtop.core.core.InMemoryPagingStorage
import com.simtop.core.core.LanguageProvider
import com.simtop.core.core.PageResult
import com.simtop.core.core.Pager
import com.simtop.core.core.PagingMediator
import com.simtop.core.core.PagingStorage
import dev.zacsweers.metro.Inject
import java.io.IOException
import java.net.HttpURLConnection
import kotlinx.coroutines.flow.Flow
import retrofit2.HttpException

@Inject
class BeersPagerFactoryImpl(
  private val repository: BeersRepository,
  private val languageProvider: LanguageProvider,
) : BeersPagerFactory {

  override fun create(): Pager<Beer, FetchBeersError> {
    // One paged surface, keyed by language so a future language switch can invalidate it (Phase 4);
    // for now the key just scopes the bookmark. Read (resume) and write (store) share this value.
    val surface = CATALOG_SURFACE_PREFIX + languageProvider.currentLanguageCode()
    return PagingMediator(
      initialKey = FIRST_PAGE,
      fetchRemote = { page ->
        val beerPage = repository.getBeersPageFromApi(page)
        // With a known total we can end exactly (no wasted empty fetch); without it, keep advancing
        // and let the mediator's empty-page probe find the end.
        val total = beerPage.totalCount
        val nextKey =
          if (total != null && page * BeersService.DEFAULT_ITEMS_PER_PAGE >= total) null
          else page + 1
        PageResult(items = beerPage.items, nextKey = nextKey, totalCount = total)
      },
      classifyError = { it.toFetchBeersError() },
      storage = BeersPagingStorage(repository, surface),
      // Exact resume: the paging_state bookmark records the first uncached page, written in the
      // same
      // transaction as the rows. It falls back to the row-count estimate (N fully cached pages ->
      // page N+1) only when no bookmark exists yet - a fresh install, or a cache written before
      // paging_state existed (the v1->v2 migration gap). Either way "load more" resumes after
      // everything cached instead of silently re-fetching cached pages over the network.
      nextKeyFromStorage = {
        repository.pagingNextKey(surface)
          ?: (repository.countDBEntries() / BeersService.DEFAULT_ITEMS_PER_PAGE + FIRST_PAGE)
      },
    )
  }

  override fun create(query: BeersQuery): Pager<Beer, FetchBeersError> =
    // Search is its own surface: results live only in memory (they die with the screen and a new
    // query instantly invalidates them) and never touch the beers table, so the catalog's
    // SELECT * view is untouched. No nextKeyFromStorage - there's no warm cache to resume from.
    PagingMediator(
      initialKey = FIRST_PAGE,
      fetchRemote = { page ->
        val beerPage = repository.getBeersPageFromApi(page, query.search)
        val total = beerPage.totalCount
        val nextKey =
          if (total != null && page * BeersService.DEFAULT_ITEMS_PER_PAGE >= total) null
          else page + 1
        PageResult(items = beerPage.items, nextKey = nextKey, totalCount = total)
      },
      classifyError = { it.toFetchBeersError() },
      storage = InMemoryPagingStorage(),
    )

  private fun Throwable.toFetchBeersError(): FetchBeersError =
    when (this) {
      is HttpException ->
        when (code()) {
          HttpURLConnection.HTTP_NOT_FOUND -> FetchBeersError.NotFound
          HttpURLConnection.HTTP_FORBIDDEN -> FetchBeersError.Forbidden
          HTTP_TOO_MANY_REQUESTS -> FetchBeersError.RateLimited
          else -> FetchBeersError.Unknown(this)
        }
      is IOException -> FetchBeersError.Network
      else -> FetchBeersError.Unknown(this)
    }

  private companion object {
    const val FIRST_PAGE = 1
    const val CATALOG_SURFACE_PREFIX = "catalog:"
    // No HttpURLConnection constant exists for 429.
    const val HTTP_TOO_MANY_REQUESTS = 429
  }
}

/**
 * Room-backed SSOT storage for the single full-catalog beers surface. Availability is treated as a
 * local-only field: the server's `available` only seeds a row on first insert, so every write -
 * refresh included - goes through the keyed upsert that updates all columns *except* availability.
 * Refresh deliberately never deletes rows: a delete-and-reinsert would reset every locally edited
 * (or seeded) availability back to the default. The accepted trade-off is that beers removed
 * server-side linger in the cache until the next reinstall/clear-data. Because [storeFirstPage]
 * merges instead of replacing, the factory pairs this storage with a `nextKeyFromStorage` so the
 * pager's position tracks the cache, not the session.
 *
 * Scoping: this storage owns the whole beers table only because the catalog list is the app's one
 * paged surface. A second, differently-filtered paged surface (search, favorites) must get its own
 * storage whose observe/write/count queries are keyed by that surface's parameters - it must not
 * reuse this one, or the two screens' data and resume positions would bleed into each other.
 */
private class BeersPagingStorage(
  private val repository: BeersRepository,
  private val surface: String,
) : PagingStorage<Int, Beer> {

  override val data: Flow<List<Beer>> = repository.observeBeers()

  override suspend fun storeFirstPage(page: PageResult<Int, Beer>) =
    repository.insertPage(page.items, surface, page.nextKey, page.totalCount)

  override suspend fun append(page: PageResult<Int, Beer>) =
    repository.insertPage(page.items, surface, page.nextKey, page.totalCount)
}
