package com.simtop.feature.savedfilters

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.beerdomain.domain.models.BeersQuery
import com.simtop.beerdomain.domain.models.SavedFilterPreset
import com.simtop.billionbeers.core.designsystem.component.PreviewLightDark
import com.simtop.billionbeers.core.designsystem.theme.BillionBeersTheme
import com.simtop.billionbeers.shared.presentation.SharedPagedBeersContent
import com.simtop.billionbeers.shared.presentation.SharedPagedBeersStrings
import com.simtop.billionbeers.shared.savedfilters.SavedFiltersStrings
import com.simtop.billionbeers.shared.savedfilters.SharedSavedFiltersContent
import com.simtop.core.core.CommonUiState
import com.simtop.core.core.PagedListUiModel
import com.simtop.navigation.SavedFilterResults
import com.simtop.presentation_utils.R as PresentationUtilsR
import com.simtop.presentation_utils.core.resolvedMessage
import com.simtop.presentation_utils.custom_views.ComposeBeersListItem
import com.simtop.presentation_utils.custom_views.ComposeErrorView
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import dev.zacsweers.metrox.viewmodel.metroViewModel
import kotlinx.coroutines.flow.collect

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedFiltersScreen(
  onApply: (SavedFilterResults) -> Unit,
  modifier: Modifier = Modifier,
  viewModel: SavedFiltersViewModel = metroViewModel(),
) {
  val viewState by viewModel.presets.collectAsStateWithLifecycle()
  val mutationFailedMessage = stringResource(R.string.savedfilters_mutation_failed)
  val snackbarHostState = remember { SnackbarHostState() }
  LaunchedEffect(viewModel, mutationFailedMessage) {
    viewModel.mutationFailed.collect { snackbarHostState.showSnackbar(mutationFailedMessage) }
  }
  SavedFiltersContent(
    viewState = viewState,
    onApply = { preset ->
      onApply(
        SavedFilterResults(
          id = preset.id,
          name = preset.name,
          search = preset.query.search,
          styleId = preset.query.styleId,
          breweryId = preset.query.breweryId,
        )
      )
    },
    snackbarHostState = snackbarHostState,
    onRename = viewModel::rename,
    onDelete = viewModel::delete,
    modifier = modifier,
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("LongParameterList")
fun SavedFiltersContent(
  viewState: CommonUiState<List<SavedFilterPreset>>,
  onApply: (SavedFilterPreset) -> Unit,
  onRename: suspend (SavedFilterPreset, String) -> Boolean,
  onDelete: suspend (SavedFilterPreset) -> Boolean,
  modifier: Modifier = Modifier,
  snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
  val strings =
    SavedFiltersStrings(
      empty = stringResource(R.string.savedfilters_empty),
      loadFailed = stringResource(R.string.savedfilters_load_failed),
      name = stringResource(R.string.savedfilters_name),
      rename = stringResource(R.string.savedfilters_rename),
      delete = stringResource(R.string.savedfilters_delete),
    )
  Scaffold(
    modifier = modifier,
    topBar = { TopAppBar(title = { Text(stringResource(R.string.savedfilters_title)) }) },
    snackbarHost = { SnackbarHost(snackbarHostState) },
  ) { paddingValues ->
    SharedSavedFiltersContent(
      viewState = viewState,
      strings = strings,
      onApply = onApply,
      onRename = onRename,
      onDelete = onDelete,
      modifier = Modifier.fillMaxSize().padding(paddingValues),
    )
  }
}

@Composable
fun SavedFilterResultsScreen(
  route: SavedFilterResults,
  onBack: () -> Unit,
  onBeerClick: (Beer) -> Unit,
  modifier: Modifier = Modifier,
  viewModel: SavedFilterResultsViewModel =
    assistedMetroViewModel<SavedFilterResultsViewModel, SavedFilterResultsViewModel.Factory>(
      key = route.id
    ) {
      create(route.toQuery())
    },
) {
  val viewState by viewModel.viewState.collectAsStateWithLifecycle()
  SavedFilterResultsContent(
    title = route.name,
    viewState = viewState,
    onBack = onBack,
    onBeerClick = onBeerClick,
    onScrollToBottom = viewModel::onScrollToBottom,
    onRetryLoadMore = viewModel::onRetryLoadMore,
    onRetryFirstPage = viewModel::onRetryFirstPage,
    modifier = modifier,
  )
}

@Composable
@Suppress("LongParameterList")
private fun SavedFilterResultsContent(
  title: String,
  viewState: CommonUiState<PagedListUiModel<Beer>>,
  onBack: () -> Unit,
  onBeerClick: (Beer) -> Unit,
  onScrollToBottom: () -> Unit,
  onRetryLoadMore: () -> Unit,
  onRetryFirstPage: () -> Unit,
  modifier: Modifier = Modifier,
) {
  SharedPagedBeersContent(
    modifier = modifier,
    strings = androidPagedBeersStrings,
    title = title,
    viewState = viewState,
    onBack = onBack,
    backIcon = { description ->
      Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = description)
    },
    onBeerClick = onBeerClick,
    onScrollToBottom = onScrollToBottom,
    onRetryLoadMore = onRetryLoadMore,
    onRetryFirstPage = onRetryFirstPage,
    errorContent = { state, retry ->
      ComposeErrorView(message = state.resolvedMessage().orEmpty(), onRetry = retry)
    },
    beerRow = { beer, onClick -> ComposeBeersListItem(beer = beer, onClick = { onClick() }) },
  )
}

private val androidPagedBeersStrings
  @Composable
  get() =
    SharedPagedBeersStrings(
      back = stringResource(PresentationUtilsR.string.browse_back),
      noBeers = stringResource(PresentationUtilsR.string.browse_no_beers),
      retry = stringResource(PresentationUtilsR.string.retry),
      loadMoreFailed = stringResource(PresentationUtilsR.string.paged_list_load_more_failed),
      beersCount = { count ->
        pluralStringResource(PresentationUtilsR.plurals.browse_beers_count, count, count)
      },
      endOfList = { count ->
        pluralStringResource(PresentationUtilsR.plurals.browse_beers_end_of_list, count, count)
      },
    )

internal class SavedFiltersPreviewProvider : PreviewParameterProvider<List<SavedFilterPreset>> {
  override val values =
    sequenceOf(
      emptyList(),
      listOf(SavedFilterPreset("preset-1", "IPA", BeersQuery(search = "ipa"), 1L)),
    )
}

@PreviewLightDark
@Composable
internal fun SavedFiltersPreview(
  @PreviewParameter(SavedFiltersPreviewProvider::class) presets: List<SavedFilterPreset>
) {
  BillionBeersTheme {
    SavedFiltersContent(
      viewState = if (presets.isEmpty()) CommonUiState.Empty else CommonUiState.Success(presets),
      onApply = {},
      onRename = { _, _ -> false },
      onDelete = { false },
    )
  }
}

@Preview(widthDp = 320, heightDp = 640, fontScale = 2f)
@Preview(widthDp = 320, heightDp = 640, fontScale = 2f, locale = "fr")
@Composable
internal fun SavedFiltersLargeTextPreview() {
  BillionBeersTheme {
    SavedFiltersContent(
      viewState =
        CommonUiState.Success(
          List(SavedFilterPreset.MAX_COUNT) { index ->
            SavedFilterPreset(
              "preset-$index",
              "Favorite IPA ${index + 1}",
              BeersQuery(),
              index.toLong(),
            )
          }
        ),
      onApply = {},
      onRename = { _, _ -> false },
      onDelete = { false },
      modifier = Modifier.size(width = 320.dp, height = 640.dp),
    )
  }
}
