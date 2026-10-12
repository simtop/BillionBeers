package com.simtop.billionbeers.shared.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.simtop.beerdomain.domain.models.SavedFilterPreset
import com.simtop.billionbeers.shared.savedfilters.SavedFiltersStrings
import com.simtop.billionbeers.shared.savedfilters.SharedSavedFiltersContent

@Composable
internal fun SavedFiltersDestination(
  entry: SavedFiltersEntry,
  strings: SharedAppStrings,
  onApply: (SavedFilterPreset) -> Unit,
  onRename: suspend (SavedFilterPreset, String) -> Boolean,
  onDelete: suspend (SavedFilterPreset) -> Boolean,
) {
  val presets by entry.presets.collectAsState(emptyList())
  SharedSavedFiltersContent(
    presets = presets,
    strings =
      SavedFiltersStrings(
        empty = strings.savedFiltersEmpty,
        name = strings.filterNameHint,
        rename = strings.renameFilter,
        delete = strings.deleteFilter,
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
  val viewState by entry.viewModel.viewState.collectAsState()
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
