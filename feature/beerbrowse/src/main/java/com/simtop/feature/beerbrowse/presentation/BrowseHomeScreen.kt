package com.simtop.feature.beerbrowse.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.simtop.beerdomain.domain.models.BeerStyle
import com.simtop.beerdomain.domain.models.Brewery
import com.simtop.billionbeers.core.designsystem.component.AccessibilityMatrixPreview
import com.simtop.billionbeers.core.designsystem.component.PreviewLightDark
import com.simtop.billionbeers.core.designsystem.theme.BillionBeersTheme
import com.simtop.core.core.CommonUiState
import com.simtop.presentation_utils.R
import com.simtop.presentation_utils.core.resolvedMessage
import com.simtop.presentation_utils.custom_views.ComposeErrorView
import dev.zacsweers.metrox.viewmodel.metroViewModel

private const val TAB_STYLES = 0
private const val TAB_BREWERIES = 1

@Composable
internal fun BrowseHomeScreen(
  onBack: () -> Unit,
  onStyleClick: (BeerStyle) -> Unit,
  onBreweryClick: (Brewery) -> Unit,
  viewModel: BrowseViewModel = metroViewModel(),
) {
  val styles by viewModel.styles.collectAsState()
  val breweries by viewModel.breweries.collectAsState()
  var selectedTab by rememberSaveable { mutableIntStateOf(TAB_STYLES) }

  // Runs on selection *and* on process-death restore with the breweries tab selected - the
  // recreated ViewModel must be told the tab is visible or its list would stay Loading forever.
  LaunchedEffect(selectedTab) {
    if (selectedTab == TAB_BREWERIES) viewModel.onBreweriesTabSelected()
  }

  BrowseHomeContent(
    styles = styles,
    breweries = breweries,
    selectedTab = selectedTab,
    onTabSelected = { selectedTab = it },
    onStyleClick = onStyleClick,
    onBreweryClick = onBreweryClick,
    onBack = onBack,
    onRetryStyles = { viewModel.retryStyles() },
    onRetryBreweries = { viewModel.retryBreweries() },
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BrowseHomeContent(
  styles: CommonUiState<List<BeerStyle>>,
  breweries: CommonUiState<List<Brewery>>,
  selectedTab: Int,
  onTabSelected: (Int) -> Unit,
  onStyleClick: (BeerStyle) -> Unit,
  onBreweryClick: (Brewery) -> Unit,
  onBack: () -> Unit,
  onRetryStyles: () -> Unit,
  onRetryBreweries: () -> Unit,
) {
  Scaffold(
    topBar = {
      TopAppBar(
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(
              Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = stringResource(R.string.browse_back),
            )
          }
        },
        title = { Text(stringResource(R.string.browse_title)) },
      )
    }
  ) { padding ->
    Column(modifier = Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
      TabRow(selectedTabIndex = selectedTab) {
        Tab(
          selected = selectedTab == TAB_STYLES,
          onClick = { onTabSelected(TAB_STYLES) },
          text = { Text(stringResource(R.string.browse_tab_styles)) },
        )
        Tab(
          selected = selectedTab == TAB_BREWERIES,
          onClick = { onTabSelected(TAB_BREWERIES) },
          text = { Text(stringResource(R.string.browse_tab_breweries)) },
        )
      }

      when (selectedTab) {
        TAB_STYLES ->
          BrowseListState(state = styles, onRetry = onRetryStyles) { items ->
            LazyColumn(modifier = Modifier.fillMaxSize()) {
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
          BrowseListState(state = breweries, onRetry = onRetryBreweries) { items ->
            LazyColumn(modifier = Modifier.fillMaxSize()) {
              items(items.size) { index ->
                val brewery = items[index]
                ListItem(
                  headlineContent = { Text(brewery.name) },
                  supportingContent = {
                    breweryCaption(brewery)?.let { caption -> Text(caption) }
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

/** The one Loading/Error/Empty/Success wrapper both unpaged browse lists share. */
@Composable
private fun <T> BrowseListState(
  state: CommonUiState<List<T>>,
  onRetry: () -> Unit,
  content: @Composable (List<T>) -> Unit,
) {
  when (state) {
    CommonUiState.Loading ->
      Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
      }
    is CommonUiState.Error ->
      ComposeErrorView(message = state.resolvedMessage().orEmpty(), onRetry = onRetry)
    CommonUiState.Empty -> CenteredHint(stringResource(R.string.empty_state))
    is CommonUiState.Success -> content(state.data)
  }
}

/** "KP · Founded 1972", degrading gracefully when either half is missing. */
@Composable
private fun breweryCaption(brewery: Brewery): String? {
  val foundedYear = brewery.foundedYear
  return when {
    brewery.countryCode.isNotEmpty() && foundedYear != null ->
      stringResource(R.string.browse_brewery_founded, brewery.countryCode, foundedYear)
    brewery.countryCode.isNotEmpty() -> brewery.countryCode
    else -> null
  }
}

@Composable
internal fun CenteredHint(text: String) {
  Box(
    modifier = Modifier.fillMaxSize().padding(BillionBeersTheme.spacing.large),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text = text,
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = TextAlign.Center,
    )
  }
}

class BrowseHomePreviewParameterProvider :
  PreviewParameterProvider<BrowseHomePreviewParameterProvider.Case> {

  data class Case(
    val styles: CommonUiState<List<BeerStyle>>,
    val breweries: CommonUiState<List<Brewery>>,
    val selectedTab: Int,
  )

  private val sampleStyles =
    listOf(
      BeerStyle(id = "1", name = "IPA (Indian Pale Ale)"),
      BeerStyle(id = "2", name = "Stout"),
      BeerStyle(id = "3", name = "Lager"),
    )

  private val sampleBreweries =
    listOf(
      Brewery(
        id = "1",
        name = "Supreme Suds Collective",
        countryCode = "KP",
        foundedYear = 1972,
        imageUrl = "",
      ),
      Brewery(id = "2", name = "Hop Haven", countryCode = "BE", foundedYear = null, imageUrl = ""),
    )

  override val values =
    sequenceOf(
      Case(CommonUiState.Success(sampleStyles), CommonUiState.Loading, 0),
      Case(CommonUiState.Success(sampleStyles), CommonUiState.Success(sampleBreweries), 1),
      Case(CommonUiState.Error(messageRes = R.string.error_no_internet), CommonUiState.Loading, 0),
    )
}

@PreviewLightDark
@Composable
fun BrowseHomeScreenPreview(
  @PreviewParameter(BrowseHomePreviewParameterProvider::class)
  case: BrowseHomePreviewParameterProvider.Case
) {
  BillionBeersTheme {
    BrowseHomeContent(
      styles = case.styles,
      breweries = case.breweries,
      selectedTab = case.selectedTab,
      onTabSelected = {},
      onStyleClick = {},
      onBreweryClick = {},
      onBack = {},
      onRetryStyles = {},
      onRetryBreweries = {},
    )
  }
}

@AccessibilityMatrixPreview
@Composable
@Suppress("PreviewPublic")
internal fun BrowseHomeAccessibilityMatrixPreview() {
  BillionBeersTheme {
    BrowseHomeContent(
      styles =
        CommonUiState.Success(
          listOf(
            BeerStyle(id = "long-style", name = "A Very Long Beer Style Name That Must Wrap"),
            BeerStyle(id = "stout", name = "Imperial Stout"),
          )
        ),
      breweries =
        CommonUiState.Success(
          listOf(
            Brewery(
              id = "long-brewery",
              name = "A Brewery With A Deliberately Long Name For Accessibility Testing",
              countryCode = "ES",
              foundedYear = 1972,
              imageUrl = "",
            )
          )
        ),
      selectedTab = TAB_BREWERIES,
      onTabSelected = {},
      onStyleClick = {},
      onBreweryClick = {},
      onBack = {},
      onRetryStyles = {},
      onRetryBreweries = {},
    )
  }
}
