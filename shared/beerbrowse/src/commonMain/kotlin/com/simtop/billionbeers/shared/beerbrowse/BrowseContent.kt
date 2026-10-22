package com.simtop.billionbeers.shared.beerbrowse

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
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
import com.simtop.billionbeers.shared.designsystem.components.SharedLazyColumn
import com.simtop.billionbeers.shared.designsystem.controls.BrandCircularProgressIndicator
import com.simtop.billionbeers.shared.designsystem.controls.BrandHorizontalDivider
import com.simtop.billionbeers.shared.designsystem.controls.BrandIconButton
import com.simtop.billionbeers.shared.designsystem.controls.BrandListItem
import com.simtop.billionbeers.shared.designsystem.controls.BrandTab
import com.simtop.billionbeers.shared.designsystem.controls.BrandTabRow
import com.simtop.billionbeers.shared.designsystem.controls.BrandText
import com.simtop.billionbeers.shared.designsystem.controls.BrandTopAppBar
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
      BrandTabRow(selectedTabIndex = selectedTab) {
        BrandTab(
          selected = selectedTab == TAB_STYLES,
          onClick = { onTabSelected(TAB_STYLES) },
          text = { BrandText(strings.stylesTab) },
        )
        BrandTab(
          selected = selectedTab == TAB_BREWERIES,
          onClick = { onTabSelected(TAB_BREWERIES) },
          text = { BrandText(strings.breweriesTab) },
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
            SharedLazyColumn(
              modifier = Modifier.fillMaxSize(),
              contentPadding = PaddingValues(bottom = padding.calculateBottomPadding()),
            ) {
              items(items.size) { index ->
                val style = items[index]
                BrandListItem(
                  headlineContent = { BrandText(style.name) },
                  modifier = Modifier.clickable { onStyleClick(style) },
                )
                BrandHorizontalDivider()
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
            SharedLazyColumn(
              modifier = Modifier.fillMaxSize(),
              contentPadding = PaddingValues(bottom = padding.calculateBottomPadding()),
            ) {
              items(items.size) { index ->
                val brewery = items[index]
                BrandListItem(
                  headlineContent = { BrandText(brewery.name) },
                  supportingContent = {
                    val foundedYear = brewery.foundedYear
                    when {
                      brewery.countryCode.isNotEmpty() && foundedYear != null ->
                        BrandText(strings.breweryFounded(brewery.countryCode, foundedYear))
                      brewery.countryCode.isNotEmpty() -> BrandText(brewery.countryCode)
                    }
                  },
                  modifier = Modifier.clickable { onBreweryClick(brewery) },
                )
                BrandHorizontalDivider()
              }
            }
          }
      }
    }
  }
}

@Composable
// Keep independent events and rendering slots explicit at this Compose boundary.
@Suppress("LongParameterList")
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
  headerActions: @Composable () -> Unit = {},
  headerContent: @Composable () -> Unit = {},
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
    headerActions = headerActions,
    headerContent = headerContent,
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
        BrandCircularProgressIndicator()
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
  BrandTopAppBar(
    navigationIcon = {
      BrandIconButton(onClick = onBack) { backIcon(back) }
    },
    title = { BrandText(title) },
  )
}

@Composable
fun BrowseCenteredHint(text: String, modifier: Modifier = Modifier) {
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
