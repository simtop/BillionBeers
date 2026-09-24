package com.simtop.billionbeers.shared.beerbrowse

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.beerdomain.domain.models.BeerStyle
import com.simtop.beerdomain.domain.models.Brewery
import com.simtop.billionbeers.shared.designsystem.theme.BillionBeersTheme
import com.simtop.billionbeers.shared.presentation.sharedPagedListFooter
import com.simtop.core.core.CommonUiState
import com.simtop.core.core.PagedListFooter
import com.simtop.core.core.PagedListUiModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull

private const val TAB_STYLES = 0
private const val TAB_BREWERIES = 1
private const val LIST_END_BUFFER = 1

data class BrowseStrings(
  val back: String,
  val title: String,
  val stylesTab: String,
  val breweriesTab: String,
  val emptyState: String,
  val noBeers: String,
  val retry: String,
  val loadMoreFailed: String,
  val breweryFounded: @Composable (String, Int) -> String,
  val beersCount: @Composable (Int) -> String,
  val endOfList: @Composable (Int) -> String,
)

@Composable
fun SharedBrowseHomeContent(
  strings: BrowseStrings,
  styles: CommonUiState<List<BeerStyle>>,
  breweries: CommonUiState<List<Brewery>>,
  selectedTab: Int,
  onTabSelected: (Int) -> Unit,
  onStyleClick: (BeerStyle) -> Unit,
  onBreweryClick: (Brewery) -> Unit,
  onBack: () -> Unit,
  backIcon: @Composable (String) -> Unit,
  onRetryStyles: () -> Unit,
  onRetryBreweries: () -> Unit,
  errorContent: @Composable (CommonUiState.Error, () -> Unit) -> Unit,
  modifier: Modifier = Modifier,
) {
  Scaffold(
    modifier = modifier,
    topBar = {
      BrowseTopAppBar(
        title = strings.title,
        back = strings.back,
        onBack = onBack,
        backIcon = backIcon,
      )
    },
  ) { padding ->
    Column(
      modifier =
        Modifier.fillMaxSize()
          .consumeWindowInsets(padding)
          .padding(top = padding.calculateTopPadding())
    ) {
      TabRow(selectedTabIndex = selectedTab) {
        Tab(
          selected = selectedTab == TAB_STYLES,
          onClick = { onTabSelected(TAB_STYLES) },
          text = { Text(strings.stylesTab) },
        )
        Tab(
          selected = selectedTab == TAB_BREWERIES,
          onClick = { onTabSelected(TAB_BREWERIES) },
          text = { Text(strings.breweriesTab) },
        )
      }

      when (selectedTab) {
        TAB_STYLES ->
          BrowseListState(
            state = styles,
            emptyText = strings.emptyState,
            onRetry = onRetryStyles,
            errorContent = errorContent,
          ) { items ->
            LazyColumn(
              modifier = Modifier.fillMaxSize(),
              contentPadding = PaddingValues(bottom = padding.calculateBottomPadding()),
            ) {
              items(items.size) { index ->
                val style = items[index]
                ListItem(
                  headlineContent = { Text(style.name) },
                  modifier = Modifier.clickable { onStyleClick(style) },
                )
                HorizontalDivider()
              }
            }
          }
        TAB_BREWERIES ->
          BrowseListState(
            state = breweries,
            emptyText = strings.emptyState,
            onRetry = onRetryBreweries,
            errorContent = errorContent,
          ) { items ->
            LazyColumn(
              modifier = Modifier.fillMaxSize(),
              contentPadding = PaddingValues(bottom = padding.calculateBottomPadding()),
            ) {
              items(items.size) { index ->
                val brewery = items[index]
                ListItem(
                  headlineContent = { Text(brewery.name) },
                  supportingContent = {
                    val foundedYear = brewery.foundedYear
                    when {
                      brewery.countryCode.isNotEmpty() && foundedYear != null ->
                        Text(strings.breweryFounded(brewery.countryCode, foundedYear))
                      brewery.countryCode.isNotEmpty() -> Text(brewery.countryCode)
                    }
                  },
                  modifier = Modifier.clickable { onBreweryClick(brewery) },
                )
                HorizontalDivider()
              }
            }
          }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedBrowseBeersContent(
  strings: BrowseStrings,
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
) {
  Scaffold(
    modifier = modifier,
    topBar = {
      BrowseTopAppBar(
        title = title,
        back = strings.back,
        onBack = onBack,
        backIcon = backIcon,
      )
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
            CircularProgressIndicator()
          }
        is CommonUiState.Error ->
          Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            errorContent(state, onRetryFirstPage)
          }
        is CommonUiState.Success ->
          if (state.data.items.isEmpty()) {
            BrowseCenteredHint(strings.noBeers, Modifier.padding(padding))
          } else {
            SharedBrowseBeersResults(
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
private fun <T> BrowseListState(
  state: CommonUiState<List<T>>,
  emptyText: String,
  onRetry: () -> Unit,
  errorContent: @Composable (CommonUiState.Error, () -> Unit) -> Unit,
  content: @Composable (List<T>) -> Unit,
) {
  when (state) {
    CommonUiState.Loading ->
      Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
      }
    is CommonUiState.Error -> errorContent(state, onRetry)
    CommonUiState.Empty -> BrowseCenteredHint(emptyText, Modifier)
    is CommonUiState.Success -> content(state.data)
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SharedBrowseBeersResults(
  strings: BrowseStrings,
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
  if (model.footer !is PagedListFooter.Retry) {
    ObserveListEnd(listState = resolvedListState, onScrollToBottom = onScrollToBottom)
  }

  PullToRefreshBox(isRefreshing = model.isRefreshing, onRefresh = onRefresh) {
    Column {
      model.totalCount?.let { count ->
        Text(
          text = strings.beersCount(count),
          style = MaterialTheme.typography.labelLarge,
          modifier =
            Modifier.fillMaxWidth()
              .padding(
                horizontal = BillionBeersTheme.spacing.medium,
                vertical = BillionBeersTheme.spacing.small,
              ),
        )
      }

      val endOfListText = strings.endOfList(model.items.size)
      LazyColumn(
        state = resolvedListState,
        modifier = Modifier.fillMaxSize().consumeWindowInsets(contentPadding),
        contentPadding = contentPadding,
      ) {
        items(model.items.size, key = { index -> "${model.items[index].id}:$index" }) { index ->
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BrowseTopAppBar(
  title: String,
  back: String,
  onBack: () -> Unit,
  backIcon: @Composable (String) -> Unit,
) {
  TopAppBar(
    navigationIcon = {
      androidx.compose.material3.IconButton(onClick = onBack) { backIcon(back) }
    },
    title = { Text(title) },
  )
}

@Composable
fun BrowseCenteredHint(text: String, modifier: Modifier = Modifier) {
  Box(
    modifier = modifier.fillMaxSize().padding(BillionBeersTheme.spacing.large),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text = text,
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = TextAlign.Center,
      modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
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
