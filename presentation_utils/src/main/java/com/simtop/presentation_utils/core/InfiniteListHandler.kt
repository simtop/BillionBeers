package com.simtop.presentation_utils.core

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map

/**
 * A scroll position sampled from a [LazyListState]: how many items exist and the last one shown.
 */
internal data class ListPosition(val totalItems: Int, val lastVisibleIndex: Int)

/**
 * Emits one "load more" signal each time the list first reaches near its bottom for a *new* length.
 *
 * The latch key is the item count, not a boolean: firing once per distinct at-bottom count means a
 * load already in flight (count unchanged) or a load that failed (count unchanged) produces no
 * further signal on its own - no double-fetch, no auto-retry loop - while a page that grew the list
 * re-arms the next fetch. The old implementation gated on an `isLoadingNextPage` flag captured in
 * an unkeyed `remember`, so the flag was stale and effectively dead; this needs no such flag - the
 * pager's own mutex collapses any overlap.
 */
internal fun Flow<ListPosition>.loadMoreSignals(buffer: Int): Flow<Unit> =
  map { position ->
      val lastVisiblePlusOne = position.lastVisibleIndex + 1
      // `it > 0` guards the pre-layout snapshot: layoutInfo reports 0 items before the first
      // measure pass, which would otherwise count as "near bottom" and fire a load on entry.
      position.totalItems.takeIf { it > 0 && lastVisiblePlusOne > it - buffer }
    }
    .distinctUntilChanged()
    .filterNotNull()
    .map {}

/**
 * Calls [onLoadMore] when the user scrolls near the end of [listState] (within [buffer] items),
 * once per distinct list length - see [loadMoreSignals] for why the dedup is count-based. Correct
 * on its own: a caller need not suppress it during a load or an error to avoid a re-fetch loop.
 */
@Composable
fun InfiniteListHandler(listState: LazyListState, buffer: Int = 1, onLoadMore: () -> Unit) {
  LaunchedEffect(listState, buffer) {
    snapshotFlow {
        val layoutInfo = listState.layoutInfo
        ListPosition(
          totalItems = layoutInfo.totalItemsCount,
          lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0,
        )
      }
      .loadMoreSignals(buffer)
      .collect { onLoadMore() }
  }
}
