package com.simtop.presentation_utils.custom_views

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.simtop.billionbeers.shared.presentation.FilterPresetFormLabels
import com.simtop.billionbeers.shared.presentation.SharedFilterPresetForm
import com.simtop.presentation_utils.R

/** Android resource adapter for the shared full-width saved-filter form. */
@Composable
fun ComposeFilterPresetForm(
  name: String,
  onNameChange: (String) -> Unit,
  onSave: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
) {
  SharedFilterPresetForm(
    name = name,
    onNameChange = onNameChange,
    onSave = onSave,
    labels =
      FilterPresetFormLabels(
        nameHint = stringResource(R.string.filter_name_hint),
        save = stringResource(R.string.save_filter),
      ),
    modifier = modifier,
    enabled = enabled,
  )
}
