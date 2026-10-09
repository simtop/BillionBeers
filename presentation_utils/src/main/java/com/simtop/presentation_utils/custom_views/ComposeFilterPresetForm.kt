package com.simtop.presentation_utils.custom_views

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.simtop.beerdomain.domain.models.SavedFilterPreset
import com.simtop.billionbeers.core.designsystem.theme.BillionBeersTheme
import com.simtop.presentation_utils.R

/** Keep the name usable independently of the translated Save label and system text size. */
@Composable
fun ComposeFilterPresetForm(
  name: String,
  onNameChange: (String) -> Unit,
  onSave: () -> Unit,
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
      label = { Text(stringResource(R.string.filter_name_hint)) },
      modifier = Modifier.fillMaxWidth(),
      singleLine = true,
    )
    Button(onClick = onSave, enabled = enabled && name.isNotBlank()) {
      Text(stringResource(R.string.save_filter))
    }
  }
}
