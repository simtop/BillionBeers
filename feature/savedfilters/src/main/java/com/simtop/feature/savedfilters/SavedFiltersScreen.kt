package com.simtop.feature.savedfilters

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.beerdomain.domain.models.BeersQuery
import com.simtop.beerdomain.domain.models.SavedFilterPreset
import com.simtop.billionbeers.core.designsystem.component.PreviewLightDark
import com.simtop.billionbeers.core.designsystem.theme.BillionBeersTheme
import com.simtop.billionbeers.shared.beerbrowse.BrowseStrings
import com.simtop.billionbeers.shared.beerbrowse.SharedBrowseBeersContent
import com.simtop.core.core.CommonUiState
import com.simtop.core.core.PagedListUiModel
import com.simtop.navigation.SavedFilterResults
import com.simtop.presentation_utils.R as PresentationUtilsR
import com.simtop.presentation_utils.core.resolvedMessage
import com.simtop.presentation_utils.custom_views.ComposeBeersListItem
import com.simtop.presentation_utils.custom_views.ComposeErrorView
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import dev.zacsweers.metrox.viewmodel.metroViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedFiltersScreen(
  onApply: (SavedFilterResults) -> Unit,
  modifier: Modifier = Modifier,
  viewModel: SavedFiltersViewModel = metroViewModel(),
) {
  val presets by viewModel.presets.collectAsState()
  SavedFiltersContent(
    presets = presets,
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
    onRename = viewModel::rename,
    onDelete = viewModel::delete,
    modifier = modifier,
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedFiltersContent(
  presets: List<SavedFilterPreset>,
  onApply: (SavedFilterPreset) -> Unit,
  onRename: (SavedFilterPreset, String) -> Unit,
  onDelete: (SavedFilterPreset) -> Unit,
  modifier: Modifier = Modifier,
) {
  Scaffold(
    modifier = modifier,
    topBar = { TopAppBar(title = { Text(stringResource(R.string.savedfilters_title)) }) },
  ) { paddingValues ->
    Column(Modifier.fillMaxSize().padding(paddingValues).padding(12.dp)) {
      if (presets.isEmpty()) Text(stringResource(R.string.savedfilters_empty))
      presets.forEach { preset -> SavedFilterRow(preset, onApply, onRename, onDelete) }
    }
  }
}

@Composable
private fun SavedFilterRow(
  preset: SavedFilterPreset,
  onApply: (SavedFilterPreset) -> Unit,
  onRename: (SavedFilterPreset, String) -> Unit,
  onDelete: (SavedFilterPreset) -> Unit,
) {
  var editing by remember(preset.id) { mutableStateOf(false) }
  var name by remember(preset.id, preset.name) { mutableStateOf(preset.name) }
  if (editing) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
      OutlinedTextField(
        value = name,
        onValueChange = { value ->
          if (value.length <= SavedFilterPreset.MAX_NAME_LENGTH) name = value
        },
        label = { Text(stringResource(R.string.savedfilters_name)) },
        modifier = Modifier.weight(1f),
        singleLine = true,
      )
      Button(
        onClick = {
          onRename(preset, name)
          editing = false
        },
        modifier = Modifier.padding(start = 8.dp),
      ) {
        Text(stringResource(R.string.savedfilters_rename))
      }
    }
  } else {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
      Button(onClick = { onApply(preset) }, modifier = Modifier.weight(1f)) {
        Text(preset.name)
      }
      Button(
        onClick = { editing = true },
        modifier = Modifier.padding(start = 8.dp),
      ) {
        Text(stringResource(R.string.savedfilters_rename))
      }
      Button(
        onClick = { onDelete(preset) },
        modifier = Modifier.padding(start = 8.dp),
      ) {
        Text(stringResource(R.string.savedfilters_delete))
      }
    }
  }
}

@Composable
fun SavedFilterResultsScreen(
  route: SavedFilterResults,
  onBack: () -> Unit,
  onBeerClick: (Beer) -> Unit,
  modifier: Modifier = Modifier,
) {
  val viewModel =
    assistedMetroViewModel<SavedFilterResultsViewModel, SavedFilterResultsViewModel.Factory>(
      key = route.id
    ) {
      create(route.toQuery())
    }
  val viewState by viewModel.viewState.collectAsState()
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
  SharedBrowseBeersContent(
    modifier = modifier,
    strings = androidBrowseStrings,
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

private val androidBrowseStrings
  @Composable
  get() =
    BrowseStrings(
      back = stringResource(PresentationUtilsR.string.browse_back),
      title = stringResource(PresentationUtilsR.string.browse_title),
      stylesTab = stringResource(PresentationUtilsR.string.browse_tab_styles),
      breweriesTab = stringResource(PresentationUtilsR.string.browse_tab_breweries),
      emptyState = stringResource(PresentationUtilsR.string.empty_state),
      noBeers = stringResource(PresentationUtilsR.string.browse_no_beers),
      retry = stringResource(PresentationUtilsR.string.retry),
      loadMoreFailed = stringResource(PresentationUtilsR.string.paged_list_load_more_failed),
      breweryFounded = { country, year ->
        stringResource(PresentationUtilsR.string.browse_brewery_founded, country, year)
      },
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
    SavedFiltersContent(presets, onApply = {}, onRename = { _, _ -> }, onDelete = {})
  }
}
