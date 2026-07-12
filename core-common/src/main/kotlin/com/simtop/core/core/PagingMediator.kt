package com.simtop.core.core

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed class PagingState<out E : Any> {
  data object Idle : PagingState<Nothing>()

  data object Loading : PagingState<Nothing>()

  data object LoadingNextPage : PagingState<Nothing>()

  /**
   * A page loaded successfully. [totalCount] is the server-reported size of the whole result (from
   * `X-Total-Count`), or null when the server didn't report it - the UI can render "N of
   * [totalCount]".
   */
  data class Success(val totalCount: Int? = null) : PagingState<Nothing>()

  /**
   * A page failed to load. [isFirstPage] tells the UI which affordance fits: a full-screen error
   * (nothing is on screen yet) vs. keeping the list and showing a snackbar/footer retry.
   */
  data class Error<out E : Any>(val error: E, val isFirstPage: Boolean) : PagingState<E>()

  data object EndOfPagination : PagingState<Nothing>()
}

/**
 * One fetched page. The fetch owns key math now (it has the server headers): [nextKey] is the key
 * of the page after this one, or null if this is the last page - letting the mediator end
 * pagination *without* a wasted known-empty fetch. [totalCount] carries `X-Total-Count` through to
 * [PagingState.Success]; both stay nullable so a header-less server falls back to the empty-page
 * probe.
 */
data class PageResult<Key : Any, Value : Any>(
  val items: List<Value>,
  val nextKey: Key?,
  val totalCount: Int? = null,
)

/**
 * What a screen needs from a paginated source: the accumulated [data], the load [pagingState], and
 * the two entry points. Consumers (ViewModels, fakes) depend on this, not on [PagingMediator], so
 * tests can drive paging states directly with a hand-rolled fake.
 *
 * A pager instance holds per-screen state (current page, end-of-list), so it must be owned by the
 * screen's ViewModel - never by an app-scoped singleton shared across screens.
 */
interface Pager<Value : Any, out E : Any> {
  val data: Flow<List<Value>>
  val pagingState: StateFlow<PagingState<E>>

  /** Loads (or reloads, acting as refresh) the first page. Waits for any in-flight load. */
  suspend fun loadFirstPage()

  /** Loads the page after the last successfully stored one. No-op if a load is in flight. */
  suspend fun loadNextPage()
}

/**
 * Where fetched pages are written and where [Pager.data] reads from. Implementations own the write
 * semantics, which is exactly where app-specific policy belongs:
 * - [InMemoryPagingStorage] (network-only): [storeFirstPage] swaps the whole list, [append] grows
 *   it.
 * - A database-backed SSOT storage can make [storeFirstPage] a keyed upsert that preserves
 *   local-only columns (e.g. a user-edited flag the API doesn't know about) instead of a
 *   destructive delete-and-reinsert. Such a non-replacing storage must be paired with a
 *   `nextKeyFromStorage` on its [PagingMediator], so the pager's position tracks what the storage
 *   actually holds.
 *
 * One storage instance = one paged surface. [data], both writes, and any storage-derived key must
 * all target the same scoped slice: a second, differently-filtered paged surface (search,
 * favorites) needs its own storage whose queries are keyed by that surface's fetch parameters. Two
 * surfaces must never page one unscoped shared table.
 */
interface PagingStorage<Value : Any> {
  val data: Flow<List<Value>>

  /**
   * The new first page, after a successful initial load or refresh. May be empty. Implementations
   * choose what "store" means - replace everything (in-memory) or merge/upsert into existing rows
   * (DB SSOT); the mediator does not assume [data] equals this page afterwards.
   */
  suspend fun storeFirstPage(page: List<Value>)

  /** A successfully fetched subsequent page. Never empty. */
  suspend fun append(page: List<Value>)
}

/** Network-only storage: pages accumulate in memory and vanish with the pager. */
class InMemoryPagingStorage<Value : Any> : PagingStorage<Value> {
  private val pages = MutableStateFlow<List<Value>>(emptyList())

  override val data: Flow<List<Value>> = pages.asStateFlow()

  override suspend fun storeFirstPage(page: List<Value>) {
    pages.value = page
  }

  override suspend fun append(page: List<Value>) {
    pages.update { current -> current + page }
  }
}

/**
 * A small paging state machine over a remote source. Fetched pages are written to [storage]
 * (defaulting to [InMemoryPagingStorage] for network-only paging; pass a database-backed
 * [PagingStorage] for single-source-of-truth caching).
 *
 * Retry is free by construction: the page key only advances after a successful fetch-and-store, so
 * after a [PagingState.Error] any entry point simply re-requests the failed page.
 *
 * [loadFirstPage] is also refresh: it resets the key and hands the new first page to
 * [PagingStorage.storeFirstPage] - only *after* a successful fetch, so a failed refresh never
 * touches stored data. Concurrent [loadNextPage] calls (e.g. scroll-spam) collapse into the single
 * in-flight load.
 *
 * ```kotlin
 * val pager: Pager<Beer, FetchBeersError> = PagingMediator(
 *   initialKey = 1,
 *   fetchRemote = { page ->
 *     val res = api.getBeers(page) // items + X-Total-Count
 *     PageResult(res.items, nextKey = if (page * PAGE_SIZE >= res.total) null else page + 1, res.total)
 *   },
 *   classifyError = { it.toFetchBeersError() },
 *   storage = roomBackedStorage, // omit for in-memory paging
 * )
 * ```
 *
 * @param Key The type of the key used for paging (e.g., Int for page number).
 * @param Value The type of the data being paged.
 * @param E The caller's typed error for a failed load, produced from the caught [Throwable] by
 *   [classifyError] - callers get to `when` over real failure modes instead of a raw message.
 * @param fetchRemote Fetches a page and returns a [PageResult]: the items plus the caller-computed
 *   [PageResult.nextKey] (null ends pagination with no extra fetch) and optional total count.
 * @param nextKeyFromStorage Derives the key of the first page *not yet represented in [storage]*
 *   from what storage currently holds (e.g. `rowCount / pageSize + 1`). Required whenever the
 *   storage outlives the pager (a warm cache) or [PagingStorage.storeFirstPage] merges instead of
 *   replacing. Consulted (a) on the first [loadNextPage] when no [loadFirstPage] has run, so "load
 *   more" over pre-existing data doesn't silently re-fetch every stored page from [initialKey], and
 *   (b) after every successful first-page store, so a refresh over a merging storage resumes after
 *   everything stored rather than re-walking pages the storage kept. A refresh itself always
 *   fetches [initialKey]. Without it the mediator assumes storage holds exactly the pages loaded
 *   through it in this session.
 */
class PagingMediator<Key : Any, Value : Any, E : Any>(
  private val initialKey: Key,
  private val fetchRemote: suspend (key: Key) -> PageResult<Key, Value>,
  private val classifyError: (Throwable) -> E,
  private val storage: PagingStorage<Value> = InMemoryPagingStorage(),
  private val nextKeyFromStorage: (suspend () -> Key)? = null,
) : Pager<Value, E> {

  private val _pagingState = MutableStateFlow<PagingState<E>>(PagingState.Idle)
  override val pagingState: StateFlow<PagingState<E>> = _pagingState.asStateFlow()

  override val data: Flow<List<Value>> = storage.data

  private val mutex = Mutex()
  private var currentKey: Key? = null
  private var isKeyInitialized = false
  private var isLastPage = false

  override suspend fun loadFirstPage() {
    mutex.withLock {
      currentKey = initialKey
      isKeyInitialized = true
      isLastPage = false
      loadPage(initialKey, isFirstPage = true)
    }
  }

  @Suppress("TooGenericExceptionCaught")
  override suspend fun loadNextPage() {
    if (!mutex.tryLock()) return
    try {
      if (isLastPage) return
      if (!isKeyInitialized) {
        currentKey =
          try {
            nextKeyFromStorage?.invoke() ?: initialKey
          } catch (e: CancellationException) {
            throw e
          } catch (e: Exception) {
            _pagingState.value = PagingState.Error(classifyError(e), isFirstPage = false)
            return
          }
        isKeyInitialized = true
      }
      currentKey?.let { key -> loadPage(key, isFirstPage = false) }
    } finally {
      mutex.unlock()
    }
  }

  @Suppress("TooGenericExceptionCaught")
  private suspend fun loadPage(key: Key, isFirstPage: Boolean) {
    try {
      _pagingState.value = if (isFirstPage) PagingState.Loading else PagingState.LoadingNextPage

      val result = fetchRemote(key)
      val items = result.items
      if (isFirstPage) storage.storeFirstPage(items)
      else if (items.isNotEmpty()) storage.append(items)

      // Empty-page probe: the fallback end signal when the server reports no total (nextKey stays
      // non-null) - preserves the pre-2.0 behaviour for a header-less backend.
      if (items.isEmpty()) {
        endPagination()
        return
      }

      // After a first-page store the storage - not the fetched page - is the authority on
      // position: a merging storage may still hold pages beyond the one just fetched.
      currentKey =
        if (isFirstPage) nextKeyFromStorage?.invoke() ?: result.nextKey else result.nextKey
      if (currentKey == null) {
        endPagination()
      } else {
        _pagingState.value = PagingState.Success(result.totalCount)
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      _pagingState.value = PagingState.Error(classifyError(e), isFirstPage)
    }
  }

  private fun endPagination() {
    isLastPage = true
    _pagingState.value = PagingState.EndOfPagination
  }
}
