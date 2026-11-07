package com.simtop.beer_data.repositories

import com.simtop.beer_network.network.BeersService
import com.simtop.beerdomain.domain.errors.FetchBeersError
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.beerdomain.domain.models.BeerLocalFlags
import com.simtop.beerdomain.domain.models.BeersQuery
import com.simtop.beerdomain.domain.models.CatalogCacheStatus
import com.simtop.beerdomain.domain.repositories.BeersPagerFactory
import com.simtop.beerdomain.domain.repositories.BeersRepository
import com.simtop.core.core.InMemoryPagingStorage
import com.simtop.core.core.LanguageProvider
import com.simtop.core.core.Logger
import com.simtop.core.core.PageResult
import com.simtop.core.core.Pager
import com.simtop.core.core.PagingMediator
import com.simtop.core.core.PagingStorage
import com.simtop.core.core.StoredPagingCompletion
import com.simtop.core.core.retryWithErrorState
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

@Inject
class BeersPagerFactoryImpl(
  private val repository: BeersRepository,
  private val languageProvider: LanguageProvider,
  private val logger: Logger,
) : BeersPagerFactory {

  override fun create(): Pager<Beer, FetchBeersError> {
    return PagingMediator(
      initialKey = CatalogPageKey(FIRST_PAGE, languageProvider.currentLanguageCode()),
      fetchRemote = ::fetchCatalogPage,
      classifyError = { it.toFetchBeersError() },
      storage = BeersPagingStorage(repository),
      // Exact resume: the paging_state bookmark records the first uncached page, written in the
      // same transaction as the rows. On a bookmark miss the fallback depends on *why* it missed:
      // - LanguageMismatch (rows fetched under another language): restart from page 1, so
      //   "load more" re-walks and re-translates the stale pages through the upsert instead of
      //   skipping them - the count estimate would jump past rows that still need re-fetching.
      // - Otherwise (fresh install, or a legacy cache from before paging_state existed): the
      //   row-count estimate (N fully cached pages -> page N+1) keeps its original job.
      nextKeyFromStorage = {
        withFetchDiagnostics(logger, FetchFailureOrigin.STORAGE) {
          val language = languageProvider.currentLanguageCode()
          val page =
            repository.pagingNextKey(catalogSurface(language))
              ?: when (repository.catalogCacheStatus()) {
                CatalogCacheStatus.LanguageMismatch -> FIRST_PAGE
                else ->
                  repository.countCatalogDBEntries() / BeersService.DEFAULT_ITEMS_PER_PAGE +
                    FIRST_PAGE
              }
          CatalogPageKey(page, language)
        }
      },
      completionFromStorage = {
        withFetchDiagnostics(logger, FetchFailureOrigin.STORAGE) {
          repository
            .pagingCompletionTotal(catalogSurface(languageProvider.currentLanguageCode()))
            ?.let {
              StoredPagingCompletion(it.totalCount)
            }
        }
      },
    )
  }

  override fun create(query: BeersQuery): Pager<Beer, FetchBeersError> =
    // A query surface keeps its own in-memory membership while fetched rows are cached by ID for
    // later detail lookup. Durable catalog membership and paging state stay catalog-only. No
    // nextKeyFromStorage - a query has no bookmark to resume from. The overlay keeps local flags
    // current after detail edits.
    PagingMediator(
      initialKey = FIRST_PAGE,
      fetchRemote = { page -> fetchPage(page, query) },
      classifyError = { it.toFetchBeersError() },
      storage = QueryPagingStorage(InMemoryPagingStorage(keyOf = Beer::id), repository),
    )

  private suspend fun fetchCatalogPage(key: CatalogPageKey): PageResult<CatalogPageKey, Beer> {
    val language = languageProvider.currentLanguageCode()
    // A continuation belongs to one language. If it changed, re-walk the catalog from page 1.
    val requested = if (key.languageCode == language) key else CatalogPageKey(FIRST_PAGE, language)
    val result = fetchPage(requested.page, BeersQuery(), language)
    return PageResult(
      items = result.items,
      nextKey = result.nextKey?.let { requested.copy(page = it) },
      totalCount = result.totalCount,
      pageKey = requested,
    )
  }

  /**
   * One page fetch plus the shared nextKey math: with a known total the end is exact (no wasted
   * empty fetch); without it, keep advancing and let the mediator's empty-page probe find the end.
   */
  private suspend fun fetchPage(
    page: Int,
    query: BeersQuery,
    languageCode: String? = null,
  ): PageResult<Int, Beer> {
    val beerPage = repository.getBeersPageFromApi(page, query, languageCode)
    val total = beerPage.totalCount
    val nextKey =
      if (total != null && page * BeersService.DEFAULT_ITEMS_PER_PAGE >= total) null else page + 1
    return PageResult(items = beerPage.items, nextKey = nextKey, totalCount = total)
  }

  private companion object {
    const val FIRST_PAGE = 1
  }
}

private data class CatalogPageKey(val page: Int, val languageCode: String)

/**
 * Owns a query's in-memory membership, persists each fetched page for later detail lookup before
 * exposing it, and overlays the current local flags from the durable store. The durable cache
 * operation does not change catalog membership, catalog positions, or paging bookmarks.
 */
@OptIn(ExperimentalCoroutinesApi::class)
private class QueryPagingStorage(
  private val delegate: PagingStorage<Int, Beer>,
  private val repository: BeersRepository,
) : PagingStorage<Int, Beer> by delegate {

  override val data: Flow<List<Beer>> = flow {
    // Keep one collector-owned snapshot as membership changes. Bound it to the current result IDs
    // so refreshed-out rows do not accumulate while still preserving flags for overlapping rows.
    var lastGoodFlags = emptyList<BeerLocalFlags>()
    emitAll(
      delegate.data.flatMapLatest { items ->
        val ids = items.mapTo(mutableSetOf(), Beer::id)
        lastGoodFlags = lastGoodFlags.filter { it.id in ids }
        repository
          .observeLocalFlags(ids)
          .onEach { lastGoodFlags = it }
          .retryWithErrorState { lastGoodFlags }
          .map { flags ->
            val flagsById = flags.associateBy { it.id }
            items.map { beer ->
              flagsById[beer.id]?.let { local ->
                beer.copy(availability = local.availability, isFavorite = local.isFavorite)
              } ?: beer
            }
          }
      }
    )
  }

  override suspend fun storeFirstPage(page: PageResult<Int, Beer>) {
    repository.cacheBeersForDetail(page.items)
    delegate.storeFirstPage(page)
  }

  override suspend fun append(page: PageResult<Int, Beer>) {
    repository.cacheBeersForDetail(page.items)
    delegate.append(page)
  }
}

/**
 * Persistent SSOT storage for the single full-catalog beers surface. Availability is treated as a
 * local-only field: the server's `available` only seeds a row on first insert, so every write -
 * refresh included - goes through the keyed upsert that preserves both local flags. Refresh
 * deliberately never deletes rows: a delete-and-reinsert would reset every locally edited (or
 * seeded) availability back to the default. The accepted trade-off is that beers removed
 * server-side linger in the cache until the next reinstall/clear-data. Because [storeFirstPage]
 * merges instead of replacing, the factory pairs this storage with a `nextKeyFromStorage` so the
 * pager's position tracks the cache, not the session.
 *
 * Scoping: this storage reads catalog membership and owns its paging bookmarks. Query pagers keep
 * their own in-memory membership and use shared beer records for detail lookup and local flags.
 * They must not reuse this catalog storage or its continuation state.
 */
private class BeersPagingStorage(private val repository: BeersRepository) :
  PagingStorage<CatalogPageKey, Beer> {

  override val data: Flow<List<Beer>> = repository.observeCatalogBeers()

  override suspend fun storeFirstPage(page: PageResult<CatalogPageKey, Beer>) = append(page)

  override suspend fun append(page: PageResult<CatalogPageKey, Beer>) {
    val requested = checkNotNull(page.pageKey)
    repository.insertPage(
      page.items,
      catalogSurface(requested.languageCode),
      page.nextKey?.page,
      page.totalCount,
      requested.page,
    )
  }
}
