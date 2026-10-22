package com.simtop.billionbeers.shared.savedfilters

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.simtop.beerdomain.domain.models.SavedFilterPreset
import com.simtop.billionbeers.shared.designsystem.components.SharedLazyColumn
import com.simtop.billionbeers.shared.designsystem.controls.BrandButton
import com.simtop.billionbeers.shared.designsystem.controls.BrandCircularProgressIndicator
import com.simtop.billionbeers.shared.designsystem.controls.BrandOutlinedTextField
import com.simtop.billionbeers.shared.designsystem.controls.BrandText
import com.simtop.core.core.CommonUiState
import kotlinx.coroutines.launch

data class SavedFiltersStrings(
  val empty: String,
  val loadFailed: String,
  val name: String,
  val rename: String,
  val delete: String,
)

@Composable
fun SharedSavedFiltersContent(
  viewState: CommonUiState<List<SavedFilterPreset>>,
  strings: SavedFiltersStrings,
  onApply: (SavedFilterPreset) -> Unit,
  onRename: suspend (SavedFilterPreset, String) -> Boolean,
  onDelete: suspend (SavedFilterPreset) -> Boolean,
  modifier: Modifier = Modifier,
) {
  when (val state = viewState) {
    CommonUiState.Loading ->
      Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        BrandCircularProgressIndicator()
      }
    CommonUiState.Empty ->
      SavedFiltersList(emptyList(), strings, onApply, onRename, onDelete, modifier)
    is CommonUiState.Error ->
      Box(
        modifier.fillMaxSize().testTag("saved_filters_load_error"),
        contentAlignment = Alignment.Center,
      ) {
        BrandText(strings.loadFailed)
      }
    is CommonUiState.Success ->
      SavedFiltersList(state.data, strings, onApply, onRename, onDelete, modifier)
  }
}

@Composable
private fun SavedFiltersList(
  presets: List<SavedFilterPreset>,
  strings: SavedFiltersStrings,
  onApply: (SavedFilterPreset) -> Unit,
  onRename: suspend (SavedFilterPreset, String) -> Boolean,
  onDelete: suspend (SavedFilterPreset) -> Boolean,
  modifier: Modifier,
) {
  SharedLazyColumn(modifier.padding(12.dp)) {
    if (presets.isEmpty()) item { BrandText(strings.empty) }
    items(presets, key = SavedFilterPreset::id) { preset ->
      SavedFilterRow(preset, strings, onApply, onRename, onDelete)
    }
  }
}

@Composable
private fun SavedFilterRow(
  preset: SavedFilterPreset,
  strings: SavedFiltersStrings,
  onApply: (SavedFilterPreset) -> Unit,
  onRename: suspend (SavedFilterPreset, String) -> Boolean,
  onDelete: suspend (SavedFilterPreset) -> Boolean,
) {
  var editing by rememberSaveable(preset.id) { mutableStateOf(false) }
  var name by rememberSaveable(preset.id, preset.name) { mutableStateOf(preset.name) }
  var renaming by remember { mutableStateOf(false) }
  val scope = rememberCoroutineScope()
  Column(
    modifier =
      Modifier.fillMaxWidth().padding(vertical = 4.dp).testTag("saved-filter-${preset.id}"),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    if (editing) {
      BrandOutlinedTextField(
        value = name,
        onValueChange = { value ->
          if (renaming) return@BrandOutlinedTextField
          if (value.length <= SavedFilterPreset.MAX_NAME_LENGTH) name = value
        },
        label = { BrandText(strings.name) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        enabled = !renaming,
      )
      BrandButton(
        onClick = {
          if (!renaming) {
            renaming = true
            scope.launch {
              try {
                if (onRename(preset, name)) editing = false
              } finally {
                renaming = false
              }
            }
          }
        },
        enabled = !renaming,
      ) {
        BrandText(strings.rename)
      }
    } else {
      BrandButton(onClick = { onApply(preset) }, modifier = Modifier.fillMaxWidth()) {
        BrandText(preset.name)
      }
      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        BrandButton(onClick = { editing = true }) { BrandText(strings.rename) }
        BrandButton(onClick = { scope.launch { onDelete(preset) } }) { BrandText(strings.delete) }
      }
    }
  }
}
