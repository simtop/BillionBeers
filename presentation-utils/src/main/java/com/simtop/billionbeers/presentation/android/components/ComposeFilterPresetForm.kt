package com.simtop.billionbeers.presentation.android.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.simtop.billionbeers.presentation.android.R
import com.simtop.billionbeers.shared.presentation.FilterPresetFormLabels
import com.simtop.billionbeers.shared.presentation.SharedFilterPresetForm

/** Android resource adapter for the shared full-width saved-filter form. */
@Composable
fun ComposeFilterPresetForm(
  state: SaveFilterState,
  onSave: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
) {
  SharedFilterPresetForm(
    name = state.name,
    onNameChange = { state.name = it },
    onSave = onSave,
    labels =
      FilterPresetFormLabels(
        nameHint = stringResource(R.string.filter_name_hint),
        save = stringResource(R.string.save_filter),
      ),
    modifier = modifier,
    enabled = enabled,
    saving = state.saving,
  )
}
