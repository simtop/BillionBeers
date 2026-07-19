package com.simtop.beer_data.repositories

import com.simtop.beer_network.network.BeersService
import com.simtop.beerdomain.domain.errors.FetchBeersError
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.beerdomain.domain.models.BeersQuery
import com.simtop.beerdomain.domain.models.CatalogCacheStatus
import com.simtop.beerdomain.domain.repositories.BeersPagerFactory
import com.simtop.beerdomain.domain.repositories.BeersRepository
import com.simtop.core.core.InMemoryPagingStorage
import com.simtop.core.core.LanguageProvider
import com.simtop.core.core.PageResult
import com.simtop.core.core.Pager
import com.simtop.core.core.PagingMediator
import com.simtop.core.core.PagingStorage
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.Flow

@Inject
class BeersPagerFactoryImpl(
  private val repository: BeersRepository,
  private val languageProvider: LanguageProvider,
) : BeersPagerFactory {

  override fun create(): Pager<Beer, FetchBeersError> {
    // One paged surface, keyed by language (shared with the repository's cache-status check via
    // catalogSurface). Read (resume) and write (store) share this value.
    val surface = catalogSurface(languageProvider)
    return PagingMediator(
      initialKey = FIRST_PAGE,
      fetchRemote = fetchPage(BeersQuery()),
      classifyError = { it.toFetchBeersError() },
      storage = BeersPagingStorage(repository, surface),
      // Exact resume: the paging_state bookmark records the first uncached page, written in the
      // same transaction as the rows. On a bookmark miss the fallback depends on *why* it missed:
      // - LanguageMismatch (rows fetched under another language): restart from page 1, so
      //   "load more" re-walks and re-translates the stale pages through the upsert instead of
      //   skipping them - the count estimate would jump past rows that still need re-fetching.
      // - Otherwise (fresh install, or a legacy cache from before paging_state existed): the
      //   row-count estimate (N fully cached pages -> page N+1) keeps its original job.
      nextKeyFromStorage = {
        repository.pagingNextKey(surface)
          ?: when (repository.catalogCacheStatus()) {
            CatalogCacheStatus.LanguageMismatch -> FIRST_PAGE
            else -> repository.countDBEntries() / BeersService.DEFAULT_ITEMS_PER_PAGE + FIRST_PAGE
          }
      },
    )
  }

  override fun create(query: BeersQuery): Pager<Beer, FetchBeersError> =
    // A query surface (search, beers-by-style, beers-by-brewery) is its own pager: results live
    // only in memory (they die with the screen and a new query instantly invalidates them) and
    // never touch the beers table, so the catalog's SELECT * view is untouched. No
    // nextKeyFromStorage - there's no warm cache to resume from.
    PagingMediator(
      initialKey = FIRST_PAGE,
      fetchRemote = fetchPage(query),
      classifyError = { it.toFetchBeersError() },
      storage = InMemoryPagingStorage(),
    )

  /**
   * One page fetch plus the shared nextKey math: with a known total the end is exact (no wasted
   * empty fetch); without it, keep advancing and let the mediator's empty-page probe find the end.
   */
  private fun fetchPage(query: BeersQuery): suspend (Int) -> PageResult<Int, Beer> = { page ->
    val beerPage = repository.getBeersPageFromApi(page, query)
    val total = beerPage.totalCount
    val nextKey =
      if (total != null && page * BeersService.DEFAULT_ITEMS_PER_PAGE >= total) null else page + 1
    PageResult(items = beerPage.items, nextKey = nextKey, totalCount = total)
  }

  private companion object {
    const val FIRST_PAGE = 1
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
