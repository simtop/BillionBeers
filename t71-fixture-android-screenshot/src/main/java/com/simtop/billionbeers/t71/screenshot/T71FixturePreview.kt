package com.simtop.billionbeers.t71.screenshot

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.simtop.billionbeers.core.designsystem.theme.BillionBeersTheme
import com.simtop.billionbeers.t71.T71FixtureContent
import com.simtop.billionbeers.t71.T71UiState

@Preview(name = "T7.1 fixture", showBackground = true)
@Composable
@Suppress("PreviewPublic")
internal fun T71FixturePreview() {
  BillionBeersTheme {
    T71FixtureContent(
      state = T71UiState(text = "Preview", submissionCount = 1),
      onTextChanged = {},
      onSubmit = {},
      title = "Shared Compose fixture",
      inputLabel = "Focus input",
      submitLabel = "Submit event",
      requestInitialFocus = false,
    )
  }
}
