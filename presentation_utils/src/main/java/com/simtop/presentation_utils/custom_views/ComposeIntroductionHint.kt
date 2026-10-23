package com.simtop.presentation_utils.custom_views

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.simtop.billionbeers.shared.presentation.SharedIntroductionHint

@Composable
fun ComposeIntroductionHint(
  text: String,
  dismissLabel: String,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier,
) {
  SharedIntroductionHint(text, dismissLabel, onDismiss, modifier)
}
