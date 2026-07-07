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

  data object Success : PagingState<Nothing>()

  /**
   * A page failed to load. [isFirstPage] tells the UI which affordance fits: a full-screen error
   * (nothing is on screen yet) vs. keeping the list and showing a snackbar/footer retry.
   */
  data class Error<out E : Any>(val error: E, val isFirstPage: Boolean) : PagingState<E>()

  data object EndOfPagination : PagingState<Nothing>()
}

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
 * - [InMemoryPagingStorage] (network-only): [replaceAll] swaps the whole list, [append] grows it.
 * - A database-backed SSOT storage can make [replaceAll] a keyed upsert that preserves local-only
 *   columns (e.g. a user-edited flag the API doesn't know about) instead of a destructive
 *   delete-and-reinsert.
 */
interface PagingStorage<Value : Any> {
  val data: Flow<List<Value>>

  /** The new first page, after a successful initial load or refresh. May be empty. */
  suspend fun replaceAll(page: List<Value>)

  /** A successfully fetched subsequent page. Never empty. */
  suspend fun append(page: List<Value>)
}

/** Network-only storage: pages accumulate in memory and vanish with the pager. */
class InMemoryPagingStorage<Value : Any> : PagingStorage<Value> {
  private val pages = MutableStateFlow<List<Value>>(emptyList())

  override val data: Flow<List<Value>> = pages.asStateFlow()

  override suspend fun replaceAll(page: List<Value>) {
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
 * [PagingStorage.replaceAll] - only *after* a successful fetch, so a failed refresh never touches
 * stored data. Concurrent [loadNextPage] calls (e.g. scroll-spam) collapse into the single
 * in-flight load.
 *
 * ```kotlin
 * val pager: Pager<Beer, FetchBeersError> = PagingMediator(
 *   initialKey = 1,
 *   nextKey = { key, page -> if (page.size < PAGE_SIZE) null else key + 1 },
 *   fetchRemote = { page -> api.getBeers(page) },
 *   classifyError = { it.toFetchBeersError() },
 *   storage = roomBackedStorage, // omit for in-memory paging
 * )
 * ```
 *
 * @param Key The type of the key used for paging (e.g., Int for page number).
 * @param Value The type of the data being paged.
 * @param E The caller's typed error for a failed load, produced from the caught [Throwable] by
 *   [classifyError] - callers get to `when` over real failure modes instead of a raw message.
 */
class PagingMediator<Key : Any, Value : Any, E : Any>(
  private val initialKey: Key,
  private val nextKey: (currentKey: Key, lastPage: List<Value>) -> Key?,
  private val fetchRemote: suspend (key: Key) -> List<Value>,
  private val classifyError: (Throwable) -> E,
  private val storage: PagingStorage<Value> = InMemoryPagingStorage(),
) : Pager<Value, E> {

  private val _pagingState = MutableStateFlow<PagingState<E>>(PagingState.Idle)
  override val pagingState: StateFlow<PagingState<E>> = _pagingState.asStateFlow()

  override val data: Flow<List<Value>> = storage.data

  private val mutex = Mutex()
  private var currentKey: Key? = initialKey
  private var isLastPage = false

  override suspend fun loadFirstPage() {
    mutex.withLock {
      currentKey = initialKey
      isLastPage = false
      loadPage(initialKey, isFirstPage = true)
    }
  }

  override suspend fun loadNextPage() {
    if (!mutex.tryLock()) return
    try {
      if (isLastPage) return
      currentKey?.let { key -> loadPage(key, isFirstPage = false) }
    } finally {
      mutex.unlock()
    }
  }

  @Suppress("TooGenericExceptionCaught")
  private suspend fun loadPage(key: Key, isFirstPage: Boolean) {
    try {
      _pagingState.value = if (isFirstPage) PagingState.Loading else PagingState.LoadingNextPage

      val page = fetchRemote(key)
      if (isFirstPage) storage.replaceAll(page) else if (page.isNotEmpty()) storage.append(page)

      if (page.isEmpty()) {
        endPagination()
        return
      }

      currentKey = nextKey(key, page)
      if (currentKey == null) {
        endPagination()
      } else {
        _pagingState.value = PagingState.Success
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
