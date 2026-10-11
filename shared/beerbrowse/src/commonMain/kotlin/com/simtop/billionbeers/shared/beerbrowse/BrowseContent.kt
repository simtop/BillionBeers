package com.simtop.billionbeers.shared.beerbrowse

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.simtop.billionbeers.shared.presentation.SharedPagedBeersContent
import com.simtop.billionbeers.shared.presentation.SharedPagedBeersStrings
import com.simtop.core.core.CommonUiState
import com.simtop.core.core.PagedListUiModel

private const val TAB_STYLES = 0
private const val TAB_BREWERIES = 1

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
  SharedPagedBeersContent(
    strings =
      SharedPagedBeersStrings(
        back = strings.back,
        noBeers = strings.noBeers,
        retry = strings.retry,
        loadMoreFailed = strings.loadMoreFailed,
        beersCount = strings.beersCount,
        endOfList = strings.endOfList,
      ),
    title = title,
    viewState = viewState,
    onBack = onBack,
    backIcon = backIcon,
    onBeerClick = onBeerClick,
    onScrollToBottom = onScrollToBottom,
    onRetryLoadMore = onRetryLoadMore,
    onRetryFirstPage = onRetryFirstPage,
    errorContent = errorContent,
    beerRow = beerRow,
    listState = listState,
    modifier = modifier,
  )
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
