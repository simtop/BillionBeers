package com.simtop.billionbeers.shared.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.billionbeers.shared.designsystem.components.SharedLazyColumn
import com.simtop.billionbeers.shared.designsystem.controls.BrandCircularProgressIndicator
import com.simtop.billionbeers.shared.designsystem.controls.BrandIconButton
import com.simtop.billionbeers.shared.designsystem.controls.BrandPullToRefreshBox
import com.simtop.billionbeers.shared.designsystem.controls.BrandText
import com.simtop.billionbeers.shared.designsystem.controls.BrandTopAppBar
import com.simtop.billionbeers.shared.designsystem.theme.BillionBeersTheme
import com.simtop.core.core.CommonUiState
import com.simtop.core.core.PagedListFooter
import com.simtop.core.core.PagedListUiModel

data class SharedPagedBeersStrings(
  val back: String,
  val noBeers: String,
  val retry: String,
  val loadMoreFailed: String,
  val beersCount: @Composable (Int) -> String,
  val endOfList: @Composable (Int) -> String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
// Keep independent events and rendering slots explicit at this Compose boundary.
@Suppress("LongParameterList")
fun SharedPagedBeersContent(
  strings: SharedPagedBeersStrings,
  title: String,
  viewState: CommonUiState<PagedListUiModel<Beer>>,
  onBack: () -> Unit,
  backIcon: @Composable (String) -> Unit,
  onBeerClick: (Beer) -> Unit,
  onScrollToBottom: () -> Unit,
  onRetryLoadMore: () -> Unit,
  onRetryFirstPage: () -> Unit,
  errorContent: @Composable (CommonUiState.Error, () -> Unit) -> Unit,
  beerRow: @Composable (Beer, () -> Unit) -> Unit,
  listState: LazyListState? = null,
  modifier: Modifier = Modifier,
  headerActions: @Composable () -> Unit = {},
  headerContent: @Composable () -> Unit = {},
) {
  Scaffold(
    modifier = modifier,
    topBar = {
      Column {
        PagedTopAppBar(
          title = title,
          back = strings.back,
          onBack = onBack,
          backIcon = backIcon,
          actions = headerActions,
        )
        headerContent()
      }
    },
  ) { padding ->
    Box(modifier = Modifier.fillMaxSize().consumeWindowInsets(padding)) {
      when (val state = viewState) {
        CommonUiState.Empty,
        CommonUiState.Loading ->
          Box(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.Center,
          ) {
            BrandCircularProgressIndicator()
          }
        is CommonUiState.Error ->
          Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            errorContent(state, onRetryFirstPage)
          }
        is CommonUiState.Success ->
          if (state.data.items.isEmpty()) {
            CenteredHint(strings.noBeers, Modifier.padding(padding))
          } else {
            PagedBeersResults(
              strings = strings,
              model = state.data,
              contentPadding = padding,
              beerRow = beerRow,
              onBeerClick = onBeerClick,
              onScrollToBottom = onScrollToBottom,
              onRefresh = onRetryFirstPage,
              onRetryLoadMore = onRetryLoadMore,
              listState = listState,
            )
          }
      }
    }
  }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun PagedTopAppBar(
  title: String,
  back: String,
  onBack: () -> Unit,
  backIcon: @Composable (String) -> Unit,
  actions: @Composable () -> Unit,
) {
  BrandTopAppBar(
    navigationIcon = {
      BrandIconButton(onClick = onBack) { backIcon(back) }
    },
    title = { BrandText(title) },
    actions = { actions() },
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PagedBeersResults(
  strings: SharedPagedBeersStrings,
  model: PagedListUiModel<Beer>,
  contentPadding: PaddingValues,
  beerRow: @Composable (Beer, () -> Unit) -> Unit,
  onBeerClick: (Beer) -> Unit,
  onScrollToBottom: () -> Unit,
  onRefresh: () -> Unit,
  onRetryLoadMore: () -> Unit,
  listState: LazyListState?,
) {
  val resolvedListState = listState ?: rememberLazyListState()
  InfiniteListHandler(
    listState = resolvedListState,
    itemCount = model.items.size,
    loadMoreEnabled =
      !model.isRefreshing && !model.isLoadingNextPage && model.footer is PagedListFooter.Hidden,
    onLoadMore = onScrollToBottom,
  )

  BrandPullToRefreshBox(isRefreshing = model.isRefreshing, onRefresh = onRefresh) {
    Column {
      model.totalCount?.let { count ->
        BrandText(
          text = strings.beersCount(count),
          style = BillionBeersTheme.typography.labelLarge,
          modifier =
            Modifier.fillMaxWidth()
              .padding(
                horizontal = BillionBeersTheme.spacing.medium,
                vertical = BillionBeersTheme.spacing.small,
              ),
        )
      }

      val endOfListText = strings.endOfList(model.items.size)
      SharedLazyColumn(
        state = resolvedListState,
        modifier = Modifier.fillMaxSize().consumeWindowInsets(contentPadding),
        contentPadding = contentPadding,
      ) {
        items(model.items.size, key = { index -> model.items[index].id }) { index ->
          val beer = model.items[index]
          beerRow(beer) { onBeerClick(beer) }
        }
        sharedPagedListFooter(
          model = model,
          loadMoreFailedText = { strings.loadMoreFailed },
          retryText = { strings.retry },
          endOfListText = endOfListText,
          onRetryLoadMore = onRetryLoadMore,
        )
      }
    }
  }
}

@Composable
private fun CenteredHint(text: String, modifier: Modifier = Modifier) {
  Box(
    modifier = modifier.fillMaxSize().padding(BillionBeersTheme.spacing.large),
    contentAlignment = Alignment.Center,
  ) {
    BrandText(
      text = text,
      style = BillionBeersTheme.typography.bodyLarge,
      color = BillionBeersTheme.colorScheme.onSurfaceVariant,
      textAlign = TextAlign.Center,
      modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
  }
}
