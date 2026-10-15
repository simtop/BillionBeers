package com.simtop.billionbeers.shared.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.simtop.beerdomain.domain.models.SavedFilterPreset
import com.simtop.billionbeers.shared.savedfilters.SharedSavedFiltersContent
import com.simtop.core.core.CommonUiState

@Composable
internal fun SavedFiltersDestination(
  entry: SavedFiltersEntry,
  strings: SharedAppStrings,
  onApply: (SavedFilterPreset) -> Unit,
  onRename: suspend (SavedFilterPreset, String) -> Boolean,
  onDelete: suspend (SavedFilterPreset) -> Boolean,
) {
  val viewState by entry.presets.collectAsStateWithLifecycle(CommonUiState.Loading)
  SharedSavedFiltersContent(
    viewState = viewState,
    strings =
      com.simtop.billionbeers.shared.savedfilters.SavedFiltersStrings(
        empty = strings.savedFilters.empty,
        loadFailed = strings.savedFilters.loadFailed,
        name = strings.savedFilters.name,
        rename = strings.savedFilters.rename,
        delete = strings.savedFilters.delete,
      ),
    onApply = onApply,
    onRename = onRename,
    onDelete = onDelete,
    modifier = Modifier,
  )
}

@Composable
internal fun SavedFilterResultsDestination(
  entry: SavedFilterResultsEntry,
  strings: SharedAppStrings,
  host: SharedAppHost,
  onBack: () -> Unit,
  onBeerClick: (com.simtop.beerdomain.domain.models.Beer) -> Unit,
) {
  val viewState by entry.viewModel.viewState.collectAsStateWithLifecycle()
  com.simtop.billionbeers.shared.beerbrowse.SharedBrowseBeersContent(
    strings = strings.browseStrings,
    title = entry.preset.name,
    viewState = viewState,
    onBack = onBack,
    backIcon = host.backIcon,
    onBeerClick = onBeerClick,
    onScrollToBottom = entry.viewModel::onScrollToBottom,
    onRetryLoadMore = entry.viewModel::onRetryLoadMore,
    onRetryFirstPage = entry.viewModel::onRetryFirstPage,
    errorContent = host.errorContent,
    beerRow = { beer, onClick -> host.beerRow(beer, onClick) },
    listState = entry.listState,
  )
}
