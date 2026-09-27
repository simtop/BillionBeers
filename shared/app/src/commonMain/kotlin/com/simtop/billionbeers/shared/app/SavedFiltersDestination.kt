package com.simtop.billionbeers.shared.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
  var editingId by remember { mutableStateOf<String?>(null) }
  var editingName by remember { mutableStateOf("") }
  Column(Modifier.padding(12.dp)) {
    if (presets.isEmpty()) {
      Text(strings.savedFiltersEmpty)
    }
    presets.forEach { preset ->
      if (editingId == preset.id) {
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
          OutlinedTextField(
            value = editingName,
            onValueChange = { editingName = it },
            label = { Text(strings.filterNameHint) },
            modifier = Modifier.weight(1f),
            singleLine = true,
          )
          Button(
            onClick = {
              onRename(preset, editingName)
              editingId = null
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
            onClick = {
              editingId = preset.id
              editingName = preset.name
            },
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
