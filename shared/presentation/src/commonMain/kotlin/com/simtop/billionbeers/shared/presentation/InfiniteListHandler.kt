package com.simtop.billionbeers.shared.presentation

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map

/** A sampled [LazyListState] position used to detect near-end paging. */
internal data class ListPosition(val totalItems: Int, val lastVisibleIndex: Int)

/** Emits one load-more signal for each distinct list length reached near the end. */
internal fun Flow<ListPosition>.loadMoreSignals(buffer: Int): Flow<Unit> =
  map { position ->
      val lastVisiblePlusOne = position.lastVisibleIndex + 1
      position.totalItems.takeIf { it > 0 && lastVisiblePlusOne > it - buffer }
    }
    .distinctUntilChanged()
    .filterNotNull()
    .map {}

/** Calls [onLoadMore] when [listState] reaches its end, once per distinct list length. */
@Composable
fun InfiniteListHandler(listState: LazyListState, buffer: Int = 1, onLoadMore: () -> Unit) {
  val currentOnLoadMore by rememberUpdatedState(onLoadMore)
  LaunchedEffect(listState, buffer) {
    snapshotFlow {
        val layoutInfo = listState.layoutInfo
        ListPosition(
          totalItems = layoutInfo.totalItemsCount,
          lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0,
        )
      }
      .loadMoreSignals(buffer)
      .collect { currentOnLoadMore() }
  }
}
