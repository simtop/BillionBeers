package com.simtop.billionbeers.shared.beersearch

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.billionbeers.shared.presentation.sharedPagedListFooter
import com.simtop.core.core.CommonUiState
import com.simtop.core.core.PagedListFooter
import com.simtop.core.core.PagedListUiModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull

private const val LIST_END_BUFFER = 1

@Composable
fun SharedBeersSearchContent(
  viewState: CommonUiState<PagedListUiModel<Beer>>,
  query: String,
  beerRow: @Composable (Beer) -> Unit,
  loadingContent: @Composable () -> Unit,
  emptyContent: @Composable () -> Unit,
  noResultsContent: @Composable (String) -> Unit,
  errorContent: @Composable (CommonUiState.Error, onRetry: () -> Unit) -> Unit,
  resultCountContent: @Composable (Int) -> Unit,
  loadMoreFailedText: String,
  retryText: String,
  endOfListText: String,
  onScrollToBottom: () -> Unit,
  onRetryLoadMore: () -> Unit,
  onRetrySearch: () -> Unit,
  contentPadding: PaddingValues,
  listState: LazyListState? = null,
  modifier: Modifier = Modifier,
) {
  when (val state = viewState) {
    CommonUiState.Empty -> emptyContent()
    CommonUiState.Loading -> loadingContent()
    is CommonUiState.Error -> errorContent(state, onRetrySearch)
    is CommonUiState.Success ->
      if (state.data.items.isEmpty()) {
        noResultsContent(query)
      } else {
        SharedBeersSearchResults(
          model = state.data,
          beerRow = beerRow,
          resultCountContent = resultCountContent,
          loadMoreFailedText = loadMoreFailedText,
          retryText = retryText,
          endOfListText = endOfListText,
          onScrollToBottom = onScrollToBottom,
          onRetryLoadMore = onRetryLoadMore,
          contentPadding = contentPadding,
          listState = listState,
          modifier = modifier,
        )
      }
  }
}

@Composable
private fun SharedBeersSearchResults(
  model: PagedListUiModel<Beer>,
  beerRow: @Composable (Beer) -> Unit,
  resultCountContent: @Composable (Int) -> Unit,
  loadMoreFailedText: String,
  retryText: String,
  endOfListText: String,
  onScrollToBottom: () -> Unit,
  onRetryLoadMore: () -> Unit,
  contentPadding: PaddingValues,
  listState: LazyListState?,
  modifier: Modifier,
) {
  val resolvedListState = listState ?: rememberLazyListState()
  if (model.footer !is PagedListFooter.Retry) {
    ObserveListEnd(listState = resolvedListState, onScrollToBottom = onScrollToBottom)
  }

  LazyColumn(
    state = resolvedListState,
    modifier = modifier.fillMaxSize().consumeWindowInsets(contentPadding).testTag("beer_list"),
    contentPadding = contentPadding,
  ) {
    model.totalCount?.let { count ->
      item { resultCountContent(count) }
    }
    items(model.items.size, key = { index -> model.items[index].id }) { index ->
      beerRow(model.items[index])
    }
    sharedPagedListFooter(
      model = model,
      loadMoreFailedText = { loadMoreFailedText },
      retryText = { retryText },
      endOfListText = endOfListText,
      onRetryLoadMore = onRetryLoadMore,
    )
  }
}

@Composable
private fun ObserveListEnd(
  listState: androidx.compose.foundation.lazy.LazyListState,
  onScrollToBottom: () -> Unit,
) {
  val callback = rememberUpdatedState(onScrollToBottom)
  LaunchedEffect(listState) {
    snapshotFlow {
        val layout = listState.layoutInfo
        val lastVisiblePlusOne = (layout.visibleItemsInfo.lastOrNull()?.index ?: 0) + 1
        layout.totalItemsCount.takeIf {
          it > 0 && lastVisiblePlusOne > it - LIST_END_BUFFER
        }
      }
      .distinctUntilChanged()
      .filterNotNull()
      .collect { callback.value() }
  }
}
