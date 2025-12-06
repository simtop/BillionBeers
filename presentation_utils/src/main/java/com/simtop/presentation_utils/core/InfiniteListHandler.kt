package com.simtop.presentation_utils.core

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.*

@Composable
fun InfiniteListHandler(
  listState: LazyListState,
  isLoadingNextPage: Boolean,
  buffer: Int = 3,
  onLoadMore: () -> Unit
) {
  val shouldLoadMore by
    remember(listState, isLoadingNextPage) {
      derivedStateOf {
        val layoutInfo = listState.layoutInfo
        val visibleItemsInfo = layoutInfo.visibleItemsInfo
        val totalItemsCount = layoutInfo.totalItemsCount

        if (totalItemsCount == 0 || visibleItemsInfo.isEmpty()) {
          false
        } else {
          val lastVisibleItemIndex = visibleItemsInfo.last().index
          // Trigger when we're within 'buffer' items of the end
          // Subtract 1 if loading footer is showing to account for it
          val threshold = if (isLoadingNextPage) 1 else buffer
          lastVisibleItemIndex >= totalItemsCount - threshold
        }
      }
    }

  LaunchedEffect(shouldLoadMore, isLoadingNextPage) {
    if (shouldLoadMore && !isLoadingNextPage) {
      onLoadMore()
    }
  }
}
