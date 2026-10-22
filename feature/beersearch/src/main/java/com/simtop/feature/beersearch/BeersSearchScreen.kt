package com.simtop.feature.beersearch

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.beerdomain.domain.models.BeerStyle
import com.simtop.beerdomain.domain.models.BeersQuery
import com.simtop.billionbeers.core.designsystem.component.AccessibilityMatrixPreview
import com.simtop.billionbeers.core.designsystem.component.PreviewLightDark
import com.simtop.billionbeers.core.designsystem.component.showToast
import com.simtop.billionbeers.core.designsystem.theme.BillionBeersTheme
import com.simtop.billionbeers.shared.beersearch.BeersSearchEvent as SharedBeersSearchEvent
import com.simtop.billionbeers.shared.beersearch.BeersSearchStyleFilter
import com.simtop.billionbeers.shared.beersearch.SharedBeersSearchContent
import com.simtop.billionbeers.shared.designsystem.controls.BrandCircularProgressIndicator
import com.simtop.billionbeers.shared.designsystem.controls.BrandIcon
import com.simtop.billionbeers.shared.designsystem.controls.BrandIconButton
import com.simtop.billionbeers.shared.designsystem.controls.BrandSearchField
import com.simtop.billionbeers.shared.designsystem.controls.BrandText
import com.simtop.billionbeers.shared.designsystem.controls.BrandTextButton
import com.simtop.billionbeers.shared.designsystem.controls.BrandTopAppBar
import com.simtop.core.core.CommonUiState
import com.simtop.core.core.PagedListUiModel
import com.simtop.presentation_utils.R as PresentationUtilsR
import com.simtop.presentation_utils.core.resolvedMessage
import com.simtop.presentation_utils.custom_views.ComposeBeersListItem
import com.simtop.presentation_utils.custom_views.ComposeErrorView
import com.simtop.presentation_utils.custom_views.ComposeSaveFilterAction
import com.simtop.presentation_utils.custom_views.ComposeSaveFilterForm
import com.simtop.presentation_utils.custom_views.rememberSaveFilterState
import com.simtop.presentation_utils.custom_views.saveFilterActionNeedsOwnRow
import dev.zacsweers.metrox.viewmodel.metroViewModel

/**
 * The search input, addressed by tag rather than by its placeholder so an instrumented test does
 * not break when the hint copy changes. Same reasoning as `beer_list` on the catalog's list.
 */
const val SEARCH_FIELD_TAG = "search_field"

@Composable
fun BeersSearchScreen(
  onBeerClick: (Beer) -> Unit,
  onBack: () -> Unit,
  onSaveQuery: suspend (String, BeersQuery) -> Boolean,
  viewModel: BeersSearchViewModel = metroViewModel(),
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val loadMoreFailedMessage = stringResource(PresentationUtilsR.string.paged_list_load_more_failed)

  LaunchedEffect(viewModel, lifecycleOwner) {
    lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
      viewModel.uiEvents.collect { event ->
        when (event) {
          SharedBeersSearchEvent.ShowLoadMoreError -> showToast(context, loadMoreFailedMessage)
        }
      }
    }
  }

  BeersSearchContent(
    viewState = uiState.viewState,
    query = uiState.query,
    onQueryChange = viewModel::onQueryChange,
    styles = uiState.styles,
    selectedStyle = uiState.selectedStyle,
    activeQuery = uiState.activeQuery,
    onStyleChange = viewModel::onStyleSelected,
    onRetryStyles = viewModel::retryStyles,
    onResetFilters = viewModel::onResetFilters,
    onBeerClick = onBeerClick,
    onBack = onBack,
    onSaveQuery = onSaveQuery,
    onScrollToBottom = { viewModel.onScrollToBottom() },
    onRetryLoadMore = { viewModel.onRetryLoadMore() },
    onRetrySearch = { viewModel.onRetrySearch() },
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
// The search surface exposes each user action independently to keep the screen easy to test and
// wire from navigation. Its callback-heavy shape is intentional at this UI boundary.
@Suppress("ComposableParamOrder", "LongParameterList")
fun BeersSearchContent(
  viewState: CommonUiState<PagedListUiModel<Beer>>,
  query: String,
  onQueryChange: (String) -> Unit,
  styles: CommonUiState<List<BeerStyle>> = CommonUiState.Empty,
  selectedStyle: BeerStyle? = null,
  activeQuery: BeersQuery = BeersQuery(),
  onStyleChange: (String?) -> Unit = {},
  onRetryStyles: () -> Unit = {},
  onResetFilters: () -> Unit = {},
  onBeerClick: (Beer) -> Unit,
  onBack: () -> Unit,
  onSaveQuery: suspend (String, BeersQuery) -> Boolean,
  onScrollToBottom: () -> Unit,
  onRetryLoadMore: () -> Unit,
  onRetrySearch: () -> Unit,
  modifier: Modifier = Modifier,
  autoFocus: Boolean = true,
  saveInitiallyExpanded: Boolean = false,
) {
  val saveFilter = rememberSaveFilterState(saveInitiallyExpanded)
  val canSave = activeQuery.search != null || activeQuery.styleId != null
  Scaffold(
    modifier = modifier,
    topBar = {
      Column {
        BrandTopAppBar(
          navigationIcon = {
            BrandIconButton(
              onClick = { if (saveFilter.expanded) saveFilter.close() else onBack() }
            ) {
              BrandIcon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.search_back),
              )
            }
          },
          title = {
            SearchField(query = query, onQueryChange = onQueryChange, autoFocus = autoFocus)
          },
          actions = {
            if (!saveFilterActionNeedsOwnRow()) {
              ComposeSaveFilterAction(saveFilter, enabled = canSave)
            }
            if (query.isNotEmpty()) {
              BrandIconButton(onClick = { onQueryChange("") }) {
                BrandIcon(
                  Icons.Filled.Close,
                  contentDescription = stringResource(R.string.search_clear),
                )
              }
            }
          },
        )
        if (saveFilterActionNeedsOwnRow()) {
          Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
          ) {
            ComposeSaveFilterAction(saveFilter, enabled = canSave, maxWidth = Dp.Unspecified)
          }
        }
        ComposeSaveFilterForm(
          state = saveFilter,
          onSave = { name -> onSaveQuery(name, activeQuery) },
          enabled = canSave,
          modifier = Modifier.padding(horizontal = BillionBeersTheme.spacing.medium),
        )
      }
    },
  ) { padding ->
    val layoutDirection = LocalLayoutDirection.current
    val contentPadding =
      PaddingValues(
        start = padding.calculateStartPadding(layoutDirection),
        end = padding.calculateEndPadding(layoutDirection),
        bottom = padding.calculateBottomPadding(),
      )
    Column(
      modifier =
        Modifier.fillMaxSize()
          .consumeWindowInsets(padding)
          .padding(top = padding.calculateTopPadding())
          .imePadding()
    ) {
      Row(
        Modifier.fillMaxWidth().padding(horizontal = BillionBeersTheme.spacing.medium),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        BeersSearchStyleFilter(
          styles = styles,
          selectedStyle = selectedStyle,
          styleLabel = stringResource(PresentationUtilsR.string.browse_tab_styles),
          allStylesLabel = stringResource(PresentationUtilsR.string.all_styles),
          clearStyleLabel = stringResource(PresentationUtilsR.string.clear_style),
          retryLabel = stringResource(PresentationUtilsR.string.retry),
          onStyleChange = onStyleChange,
          onRetryStyles = onRetryStyles,
        )
        if (activeQuery.search != null || activeQuery.styleId != null) {
          BrandTextButton(onClick = onResetFilters) {
            BrandText(stringResource(PresentationUtilsR.string.clear_filters))
          }
        }
      }
      val resultCount = (viewState as? CommonUiState.Success)?.data?.items?.size ?: 0
      Box(Modifier.weight(1f)) {
        SharedBeersSearchContent(
          viewState = viewState,
          query = query,
          beerRow = { beer -> ComposeBeersListItem(beer = beer, onClick = onBeerClick) },
          loadingContent = {
            Box(
              modifier = Modifier.fillMaxSize().padding(contentPadding),
              contentAlignment = Alignment.Center,
            ) {
              BrandCircularProgressIndicator()
            }
          },
          emptyContent = {
            CenteredHint(
              text = stringResource(R.string.search_prompt),
              modifier = Modifier.fillMaxSize().padding(contentPadding),
            )
          },
          noResultsContent = { term ->
            CenteredHint(
              text = stringResource(R.string.search_no_results, term),
              modifier = Modifier.fillMaxSize().padding(contentPadding),
            )
          },
          errorContent = { error, retry ->
            ComposeErrorView(
              message = error.resolvedMessage().orEmpty(),
              onRetry = retry,
              modifier = Modifier.fillMaxSize().padding(contentPadding),
            )
          },
          resultCountContent = { count ->
            BrandText(
              text = pluralStringResource(R.plurals.search_result_count, count, count),
              style = BillionBeersTheme.typography.labelLarge,
              modifier =
                Modifier.fillMaxWidth()
                  .padding(
                    horizontal = BillionBeersTheme.spacing.medium,
                    vertical = BillionBeersTheme.spacing.small,
                  ),
            )
          },
          loadMoreFailedText =
            stringResource(PresentationUtilsR.string.paged_list_load_more_failed),
          retryText = stringResource(PresentationUtilsR.string.retry),
          endOfListText =
            pluralStringResource(R.plurals.search_end_of_list, resultCount, resultCount),
          onScrollToBottom = onScrollToBottom,
          onRetryLoadMore = onRetryLoadMore,
          onRetrySearch = onRetrySearch,
          contentPadding = contentPadding,
        )
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit, autoFocus: Boolean) {
  val focusRequester = remember { FocusRequester() }
  if (autoFocus) {
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
  }
  BrandSearchField(
    value = query,
    onValueChange = onQueryChange,
    placeholder = { BrandText(stringResource(R.string.search_hint)) },
    singleLine = true,
    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester).testTag(SEARCH_FIELD_TAG),
  )
}

@Composable
private fun CenteredHint(text: String, modifier: Modifier = Modifier) {
  Box(
    modifier = modifier.padding(BillionBeersTheme.spacing.large),
    contentAlignment = Alignment.Center,
  ) {
    BrandText(
      text = text,
      style = BillionBeersTheme.typography.bodyLarge,
      color = BillionBeersTheme.colorScheme.onSurfaceVariant,
      textAlign = TextAlign.Center,
    )
  }
}

class BeersSearchPreviewParameterProvider :
  PreviewParameterProvider<BeersSearchPreviewParameterProvider.Case> {

  data class Case(val query: String, val state: CommonUiState<PagedListUiModel<Beer>>)

  private val sampleBeers =
    listOf(
      Beer.empty.copy(
        id = "beersearch-punk-ipa",
        name = "Punk IPA",
        tagline = "Post Modern Classic.",
        abv = 5.6,
        ibu = 41.5,
      ),
      Beer.empty.copy(
        id = "beersearch-hazy-jane",
        name = "Hazy Jane",
        tagline = "New England IPA.",
        abv = 5.0,
        ibu = 25.0,
      ),
    )

  override val values =
    sequenceOf(
      Case("", CommonUiState.Empty),
      Case("ip", CommonUiState.Loading),
      Case(
        "ipa",
        CommonUiState.Success(PagedListUiModel(items = sampleBeers, totalCount = 159)),
      ),
      Case(
        "xyzzy",
        CommonUiState.Success(PagedListUiModel<Beer>(totalCount = 0)),
      ),
      Case("ipa", CommonUiState.Error(message = "Too many requests. Please wait a moment.")),
    )
}

@PreviewLightDark
@Composable
internal fun BeersSearchScreenPreview(
  @PreviewParameter(BeersSearchPreviewParameterProvider::class)
  case: BeersSearchPreviewParameterProvider.Case
) {
  BillionBeersTheme {
    BeersSearchContent(
      viewState = case.state,
      query = case.query,
      activeQuery = BeersQuery(search = case.query.takeIf { it.length >= 2 }),
      onQueryChange = {},
      onBeerClick = {},
      onBack = {},
      onSaveQuery = { _, _ -> false },
      onScrollToBottom = {},
      onRetryLoadMore = {},
      onRetrySearch = {},
      autoFocus = false,
    )
  }
}

@AccessibilityMatrixPreview
@Composable
@Suppress("PreviewPublic")
internal fun BeersSearchAccessibilityMatrixPreview() {
  BillionBeersTheme {
    BeersSearchContent(
      viewState =
        CommonUiState.Success(
          PagedListUiModel(
            items =
              listOf(
                Beer.empty.copy(
                  id = "beersearch-accessibility-long-name",
                  name = "A Very Long Search Result Name That Must Wrap",
                  tagline =
                    "A long tagline exercises the search result layout at large font sizes.",
                ),
                Beer.empty.copy(
                  id = "beersearch-accessibility-second",
                  name = "Second Result",
                  tagline = "Another result",
                ),
              ),
            totalCount = 2,
          )
        ),
      query = "a very long query that should remain understandable",
      activeQuery = BeersQuery(search = "a very long query that should remain understandable"),
      onQueryChange = {},
      onBeerClick = {},
      onBack = {},
      onSaveQuery = { _, _ -> false },
      onScrollToBottom = {},
      onRetryLoadMore = {},
      onRetrySearch = {},
      autoFocus = false,
    )
  }
}
