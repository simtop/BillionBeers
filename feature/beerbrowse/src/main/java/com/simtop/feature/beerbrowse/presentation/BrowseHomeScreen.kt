package com.simtop.feature.beerbrowse.presentation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.simtop.beerdomain.domain.models.BeerStyle
import com.simtop.beerdomain.domain.models.Brewery
import com.simtop.billionbeers.core.designsystem.component.AccessibilityMatrixPreview
import com.simtop.billionbeers.core.designsystem.component.PreviewLightDark
import com.simtop.billionbeers.core.designsystem.theme.BillionBeersTheme
import com.simtop.billionbeers.shared.beerbrowse.BrowseCenteredHint
import com.simtop.billionbeers.shared.beerbrowse.BrowseStrings
import com.simtop.billionbeers.shared.beerbrowse.SharedBrowseHomeContent
import com.simtop.core.core.CommonUiErrorKey
import com.simtop.core.core.CommonUiState
import com.simtop.presentation_utils.R
import com.simtop.presentation_utils.core.resolvedMessage
import com.simtop.presentation_utils.custom_views.ComposeErrorView
import dev.zacsweers.metrox.viewmodel.metroViewModel

private const val TAB_STYLES = 0
private const val TAB_BREWERIES = 1

internal val browseStrings
  @Composable
  get() =
    BrowseStrings(
      back = stringResource(R.string.browse_back),
      title = stringResource(R.string.browse_title),
      stylesTab = stringResource(R.string.browse_tab_styles),
      breweriesTab = stringResource(R.string.browse_tab_breweries),
      emptyState = stringResource(R.string.empty_state),
      noBeers = stringResource(R.string.browse_no_beers),
      retry = stringResource(R.string.retry),
      loadMoreFailed = stringResource(R.string.paged_list_load_more_failed),
      breweryFounded = { country, year ->
        stringResource(R.string.browse_brewery_founded, country, year)
      },
      beersCount = { count ->
        pluralStringResource(R.plurals.browse_beers_count, count, count)
      },
      endOfList = { count ->
        pluralStringResource(R.plurals.browse_beers_end_of_list, count, count)
      },
    )

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
    onRetryStyles = viewModel::retryStyles,
    onRetryBreweries = viewModel::retryBreweries,
  )
}

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
  val strings = browseStrings
  SharedBrowseHomeContent(
    strings = strings,
    styles = styles,
    breweries = breweries,
    selectedTab = selectedTab,
    onTabSelected = onTabSelected,
    onStyleClick = onStyleClick,
    onBreweryClick = onBreweryClick,
    onBack = onBack,
    backIcon = { contentDescription ->
      Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = contentDescription)
    },
    onRetryStyles = onRetryStyles,
    onRetryBreweries = onRetryBreweries,
    errorContent = { state, retry ->
      ComposeErrorView(message = state.resolvedMessage().orEmpty(), onRetry = retry)
    },
  )
}

@Composable
internal fun CenteredHint(text: String, modifier: Modifier = Modifier) =
  BrowseCenteredHint(text, modifier)

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
      Case(CommonUiState.Error(errorKey = CommonUiErrorKey.NoInternet), CommonUiState.Loading, 0),
    )
}

@PreviewLightDark
@Composable
internal fun BrowseHomeScreenPreview(
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
