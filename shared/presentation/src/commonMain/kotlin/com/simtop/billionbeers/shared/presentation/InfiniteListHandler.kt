package com.simtop.billionbeers.shared.presentation

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow

/** A sampled [LazyListState] position plus the paging state that controls its load-more latch. */
internal data class ListPosition(
  val totalItems: Int,
  val lastVisibleIndex: Int,
  val itemCount: Int = totalItems,
  val loadMoreEnabled: Boolean = true,
)

private data class LoadMoreLatchKey(val itemCount: Int, val loadMoreEnabled: Boolean)

/**
 * Emits once per distinct data-item count reached near the end while load-more is enabled.
 * Disabling and re-enabling paging re-arms the same count, while scrolling away and back does not.
 */
internal fun Flow<ListPosition>.loadMoreSignals(buffer: Int): Flow<Unit> = flow {
  var currentKey: LoadMoreLatchKey? = null
  var currentKeyConsumed = false

  collect { position ->
    val key = LoadMoreLatchKey(position.itemCount, position.loadMoreEnabled)
    if (key != currentKey) {
      currentKey = key
      currentKeyConsumed = false
    }

    val lastVisiblePlusOne = position.lastVisibleIndex + 1
    val isNearEnd = position.totalItems > 0 && lastVisiblePlusOne > position.totalItems - buffer
    if (position.loadMoreEnabled && isNearEnd && !currentKeyConsumed) {
      currentKeyConsumed = true
      emit(Unit)
    }
  }
}

/** Calls [onLoadMore] near the end, once per item count and enabled paging cycle. */
@Composable
fun InfiniteListHandler(
  listState: LazyListState,
  itemCount: Int,
  loadMoreEnabled: Boolean,
  buffer: Int = 1,
  onLoadMore: () -> Unit,
) {
  val currentItemCount = rememberUpdatedState(itemCount)
  val currentLoadMoreEnabled = rememberUpdatedState(loadMoreEnabled)
  val currentOnLoadMore = rememberUpdatedState(onLoadMore)
  LaunchedEffect(listState, buffer) {
    snapshotFlow {
        val layoutInfo = listState.layoutInfo
        ListPosition(
          totalItems = layoutInfo.totalItemsCount,
          lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0,
          itemCount = currentItemCount.value,
          loadMoreEnabled = currentLoadMoreEnabled.value,
        )
      }
      .loadMoreSignals(buffer)
      .collect { currentOnLoadMore.value() }
  }
}
