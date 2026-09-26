package com.simtop.billionbeers.shared.beerslist

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.billionbeers.shared.presentation.InfiniteListHandler
import com.simtop.billionbeers.shared.presentation.sharedPagedListFooter
import com.simtop.core.core.CommonUiState
import com.simtop.core.core.PagedListUiModel

private const val LIST_STATE_ANIMATION_DURATION_MILLIS = 300

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedBeersListContent(
  viewState: CommonUiState<PagedListUiModel<Beer>>,
  beerRow: @Composable (Beer) -> Unit,
  loadingContent: @Composable () -> Unit,
  emptyContent: @Composable (onRetry: () -> Unit) -> Unit,
  errorContent: @Composable (CommonUiState.Error, onRetry: () -> Unit) -> Unit,
  loadMoreFailedText: String,
  retryText: String,
  endOfListText: String,
  onScrollToBottom: () -> Unit,
  onRefresh: () -> Unit,
  onRetry: () -> Unit,
  onRetryLoadMore: () -> Unit,
  listContentPadding: PaddingValues = PaddingValues(),
  animationsDisabled: Boolean = false,
  listState: LazyListState? = null,
  modifier: Modifier = Modifier,
) {
  val animationDuration = if (animationsDisabled) 0 else LIST_STATE_ANIMATION_DURATION_MILLIS

  AnimatedContent(
    targetState = viewState,
    label = "ScreenStateAnimation",
    contentKey = { state -> state::class },
    transitionSpec = {
      fadeIn(animationSpec = tween(animationDuration)) togetherWith
        fadeOut(animationSpec = tween(animationDuration))
    },
    modifier = modifier.fillMaxSize(),
  ) { state ->
    when (state) {
      CommonUiState.Loading -> loadingContent()
      CommonUiState.Empty -> emptyContent(onRetry)
      is CommonUiState.Error -> errorContent(state, onRetry)
      is CommonUiState.Success ->
        SharedBeersListSuccessContent(
          model = state.data,
          beerRow = beerRow,
          loadMoreFailedText = loadMoreFailedText,
          retryText = retryText,
          endOfListText = endOfListText,
          onScrollToBottom = onScrollToBottom,
          onRefresh = onRefresh,
          onRetryLoadMore = onRetryLoadMore,
          listContentPadding = listContentPadding,
          listState = listState,
        )
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SharedBeersListSuccessContent(
  model: PagedListUiModel<Beer>,
  beerRow: @Composable (Beer) -> Unit,
  loadMoreFailedText: String,
  retryText: String,
  endOfListText: String,
  onScrollToBottom: () -> Unit,
  onRefresh: () -> Unit,
  onRetryLoadMore: () -> Unit,
  listContentPadding: PaddingValues,
  listState: LazyListState?,
) {
  val resolvedListState = listState ?: rememberLazyListState()

  if (model.footer !is com.simtop.core.core.PagedListFooter.Retry) {
    InfiniteListHandler(listState = resolvedListState, onLoadMore = onScrollToBottom)
  }

  PullToRefreshBox(
    isRefreshing = model.isRefreshing,
    onRefresh = onRefresh,
    modifier = Modifier.fillMaxSize(),
  ) {
    LazyColumn(
      state = resolvedListState,
      modifier = Modifier.fillMaxSize().testTag("beer_list"),
      contentPadding = listContentPadding,
    ) {
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
}
