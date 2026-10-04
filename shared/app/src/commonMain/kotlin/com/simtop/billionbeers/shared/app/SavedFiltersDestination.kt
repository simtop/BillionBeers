package com.simtop.billionbeers.shared.app

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.simtop.beerdomain.domain.models.SavedFilterPreset

@Composable
internal fun SavedFiltersDestination(
  entry: SavedFiltersEntry,
  strings: SharedAppStrings,
  onApply: (SavedFilterPreset) -> Unit,
  onRename: (SavedFilterPreset, String) -> Unit,
  onDelete: (SavedFilterPreset) -> Unit,
) {
  val presets by entry.presets.collectAsState(emptyList())
  LazyColumn(Modifier.padding(12.dp)) {
    if (presets.isEmpty()) {
      item { Text(strings.savedFiltersEmpty) }
    }
    items(presets, key = SavedFilterPreset::id) { preset ->
      SavedFilterRow(
        preset = preset,
        strings = strings,
        onApply = onApply,
        onRename = onRename,
        onDelete = onDelete,
      )
    }
  }
}

@Composable
private fun SavedFilterRow(
  preset: SavedFilterPreset,
  strings: SharedAppStrings,
  onApply: (SavedFilterPreset) -> Unit,
  onRename: (SavedFilterPreset, String) -> Unit,
  onDelete: (SavedFilterPreset) -> Unit,
) {
  var editing by rememberSaveable(preset.id) { mutableStateOf(false) }
  var name by rememberSaveable(preset.id, preset.name) { mutableStateOf(preset.name) }
  if (editing) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
      OutlinedTextField(
        value = name,
        onValueChange = { value ->
          if (value.length <= SavedFilterPreset.MAX_NAME_LENGTH) name = value
        },
        label = { Text(strings.filterNameHint) },
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
        Text(strings.renameFilter)
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
        Text(strings.renameFilter)
      }
      Button(
        onClick = { onDelete(preset) },
        modifier = Modifier.padding(start = 8.dp),
      ) {
        Text(strings.deleteFilter)
      }
    }
  }
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
