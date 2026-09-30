package com.simtop.billionbeers.shared.beersearch

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.beerdomain.domain.models.BeerStyle
import com.simtop.billionbeers.shared.presentation.InfiniteListHandler
import com.simtop.billionbeers.shared.presentation.sharedPagedListFooter
import com.simtop.core.core.CommonUiState
import com.simtop.core.core.PagedListFooter
import com.simtop.core.core.PagedListUiModel

@Composable
fun BeersSearchStyleFilter(
  styles: CommonUiState<List<BeerStyle>>,
  selectedStyle: BeerStyle?,
  styleLabel: String,
  allStylesLabel: String,
  clearStyleLabel: String,
  retryLabel: String,
  onStyleChange: (String?) -> Unit,
  onRetryStyles: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var expanded by remember { mutableStateOf(false) }
  Box(modifier) {
    FilterChip(
      selected = selectedStyle != null,
      onClick = { expanded = true },
      label = { Text(selectedStyle?.name ?: styleLabel) },
      modifier = Modifier.testTag("style_filter"),
    )
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
      DropdownMenuItem(
        text = { Text(allStylesLabel) },
        onClick = {
          onStyleChange(null)
          expanded = false
        },
        enabled = selectedStyle != null,
      )
      when (val state = styles) {
        CommonUiState.Loading -> DropdownMenuItem(text = { Text(styleLabel) }, onClick = {})
        CommonUiState.Empty -> DropdownMenuItem(text = { Text(allStylesLabel) }, onClick = {})
        is CommonUiState.Error ->
          DropdownMenuItem(
            text = { Text(state.message ?: styleLabel) },
            onClick = {
              onRetryStyles()
              expanded = false
            },
            trailingIcon = { Text(retryLabel) },
          )
        is CommonUiState.Success ->
          state.data.forEach { style ->
            DropdownMenuItem(
              text = { Text(style.name) },
              onClick = {
                onStyleChange(style.id)
                expanded = false
              },
            )
          }
      }
      if (selectedStyle != null) {
        DropdownMenuItem(
          text = { Text(clearStyleLabel) },
          onClick = {
            onStyleChange(null)
            expanded = false
          },
        )
      }
    }
  }
}

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
    InfiniteListHandler(listState = resolvedListState, onLoadMore = onScrollToBottom)
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
