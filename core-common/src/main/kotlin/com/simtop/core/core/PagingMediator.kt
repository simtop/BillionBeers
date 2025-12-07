package com.simtop.core.core

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow

sealed class PagingState {
  object Idle : PagingState()

  object Loading : PagingState()

  object LoadingNextPage : PagingState()

  object Success : PagingState()

  data class Error(val message: String) : PagingState()

  object EndOfPagination : PagingState()
}

/**
 * A generic mediator to handle paging from a remote source and optionally caching in a local
 * source.
 *
 * @param Key The type of the key used for paging (e.g., Int for page number).
 * @param Value The type of the data being paged.
 */
class PagingMediator<Key : Any, Value : Any>(
  private val initialKey: Key,
  private val nextKey: (currentKey: Key, lastPage: List<Value>) -> Key?,
  private val fetchRemote: suspend (key: Key) -> List<Value>,
  private val saveLocal: (suspend (List<Value>) -> Unit)? = null,
  private val fetchLocal: (() -> Flow<List<Value>>)? = null
) {

  private val _pagingState = MutableStateFlow<PagingState>(PagingState.Idle)
  val pagingState: StateFlow<PagingState> = _pagingState.asStateFlow()

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
    reset()
    loadPage(initialKey, isFirstLoad = true)
  }

  suspend fun loadNextPage() {
    if (
      _pagingState.value is PagingState.Loading ||
        _pagingState.value is PagingState.LoadingNextPage ||
        isLastPage
    ) {
      return
    }

    currentKey?.let { key -> loadPage(key, isFirstLoad = false) }
  }

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
    } catch (e: Exception) {
      _pagingState.value = PagingState.Error(e.message ?: "Unknown error")
    }
  }

  private fun reset() {
    currentKey = initialKey
    isLastPage = false
    _pagingState.value = PagingState.Idle
  }
}
