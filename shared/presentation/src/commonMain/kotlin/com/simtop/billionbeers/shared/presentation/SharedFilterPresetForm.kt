package com.simtop.billionbeers.shared.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.simtop.beerdomain.domain.models.SavedFilterPreset
import com.simtop.billionbeers.shared.designsystem.theme.BillionBeersTheme

@Immutable data class FilterPresetFormLabels(val nameHint: String, val save: String)

/**
 * A full-width name field above Save, so translated actions and large text cannot squeeze the
 * editor. Hosts own the draft and save result; this component enforces the name-length limit and
 * disables Save for a blank name or when the host cannot save its query. Labels are supplied by the
 * host.
 */
@Composable
fun SharedFilterPresetForm(
  name: String,
  onNameChange: (String) -> Unit,
  onSave: () -> Unit,
  labels: FilterPresetFormLabels,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
) {
  Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(BillionBeersTheme.spacing.small),
    horizontalAlignment = Alignment.End,
  ) {
    OutlinedTextField(
      value = name,
      onValueChange = { if (it.length <= SavedFilterPreset.MAX_NAME_LENGTH) onNameChange(it) },
      label = { Text(labels.nameHint) },
      modifier = Modifier.fillMaxWidth(),
      singleLine = true,
    )
    Button(onClick = onSave, enabled = enabled && name.isNotBlank()) {
      Text(labels.save)
    }
  }
}
