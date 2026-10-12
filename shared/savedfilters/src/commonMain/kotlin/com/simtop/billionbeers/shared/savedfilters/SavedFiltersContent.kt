package com.simtop.billionbeers.shared.savedfilters

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.simtop.beerdomain.domain.models.SavedFilterPreset
import kotlinx.coroutines.launch

data class SavedFiltersStrings(
  val empty: String,
  val name: String,
  val rename: String,
  val delete: String,
)

@Composable
fun SharedSavedFiltersContent(
  presets: List<SavedFilterPreset>,
  strings: SavedFiltersStrings,
  onApply: (SavedFilterPreset) -> Unit,
  onRename: suspend (SavedFilterPreset, String) -> Boolean,
  onDelete: suspend (SavedFilterPreset) -> Boolean,
  modifier: Modifier = Modifier,
) {
  LazyColumn(modifier.padding(12.dp)) {
    if (presets.isEmpty()) item { Text(strings.empty) }
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
      OutlinedTextField(
        value = name,
        onValueChange = { value ->
          if (renaming) return@OutlinedTextField
          if (value.length <= SavedFilterPreset.MAX_NAME_LENGTH) name = value
        },
        label = { Text(strings.name) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        enabled = !renaming,
      )
      Button(
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
        Text(strings.rename)
      }
    } else {
      Button(onClick = { onApply(preset) }, modifier = Modifier.fillMaxWidth()) {
        Text(preset.name)
      }
      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Button(onClick = { editing = true }) { Text(strings.rename) }
        Button(onClick = { scope.launch { onDelete(preset) } }) { Text(strings.delete) }
      }
    }
  }
}
