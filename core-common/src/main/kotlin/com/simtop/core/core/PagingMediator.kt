package com.simtop.core.core

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed class PagingState<out Error : Any> {
  object Idle : PagingState<Nothing>()

  object Loading : PagingState<Nothing>()

  object LoadingNextPage : PagingState<Nothing>()

  object Success : PagingState<Nothing>()

  data class Error<out E : Any>(val error: E) : PagingState<E>()

  object EndOfPagination : PagingState<Nothing>()
}

/**
 * A generic mediator to handle paging from a remote source and optionally caching in a local
 * source.
 *
 * @param Key The type of the key used for paging (e.g., Int for page number).
 * @param Value The type of the data being paged.
 * @param Error The caller's typed error for a failed fetch, produced from the caught [Throwable]
 *   by [classifyError] - callers get to `when` over real failure modes instead of a raw message.
 */
class PagingMediator<Key : Any, Value : Any, Error : Any>(
  private val initialKey: Key,
  private val nextKey: (currentKey: Key, lastPage: List<Value>) -> Key?,
  private val fetchRemote: suspend (key: Key) -> List<Value>,
  private val classifyError: (Throwable) -> Error,
  private val saveLocal: (suspend (List<Value>) -> Unit)? = null,
  private val fetchLocal: (() -> Flow<List<Value>>)? = null,
) {

  private val _pagingState = MutableStateFlow<PagingState<Error>>(PagingState.Idle)
  val pagingState: StateFlow<PagingState<Error>> = _pagingState.asStateFlow()

  private val mutex = Mutex()
  private var currentKey: Key? = initialKey
  private var isLastPage = false

  /**
   * Returns the data stream. If [fetchLocal] is provided (SSOT), it returns the local flow. If
   * [fetchLocal] is null (Network only), it returns a flow that emits results from [fetchRemote].
   *
   * Note: For Network only, this simple implementation might need a buffer or a way to accumulate
   * results if we want to show a growing list. However, usually for Network only we might use a
   * different approach or just rely on the UI to append. But to keep it consistent with SSOT, let's
   * assume for Network Only we might want to expose a Flow that emits the *accumulated* list if we
   * managed it here, OR just the pages.
   *
   * For this implementation, let's prioritize SSOT (DB as source).
   */
  val data: Flow<List<Value>> =
    fetchLocal?.invoke()
      ?: flow {
        // Fallback for network-only if needed, or we could throw if not supported yet
        // For now, let's assume we always use DB for this project as requested "Network + DB"
        // But to support "Network Only", we would need an internal cache.
      }

  suspend fun loadFirstPage() {
    mutex.withLock {
      reset()
      loadPage(initialKey, isFirstLoad = true)
    }
  }

  suspend fun loadNextPage() {
    mutex.withLock {
      if (isRequestInFlight() || isLastPage) {
        return
      }

      currentKey?.let { key -> loadPage(key, isFirstLoad = false) }
    }
  }

  @Suppress("TooGenericExceptionCaught")
  private suspend fun loadPage(key: Key, isFirstLoad: Boolean) {
    try {
      _pagingState.value = if (isFirstLoad) PagingState.Loading else PagingState.LoadingNextPage

      val remoteData = fetchRemote(key)

      if (remoteData.isEmpty()) {
        isLastPage = true
        _pagingState.value = PagingState.EndOfPagination
        return
      }

      saveLocal?.invoke(remoteData)

      currentKey = nextKey(key, remoteData)
      if (currentKey == null) {
        isLastPage = true
        _pagingState.value = PagingState.EndOfPagination
      } else {
        _pagingState.value = PagingState.Success
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      _pagingState.value = PagingState.Error(classifyError(e))
    }
  }

  private fun reset() {
    currentKey = initialKey
    isLastPage = false
    _pagingState.value = PagingState.Idle
  }

  private fun isRequestInFlight(): Boolean =
    _pagingState.value is PagingState.Loading || _pagingState.value is PagingState.LoadingNextPage
}
