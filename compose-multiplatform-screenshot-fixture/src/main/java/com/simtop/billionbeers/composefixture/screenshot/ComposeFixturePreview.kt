package com.simtop.billionbeers.composefixture.screenshot

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.simtop.billionbeers.composefixture.ComposeFixtureContent
import com.simtop.billionbeers.composefixture.ComposeFixtureUiState
import com.simtop.billionbeers.core.designsystem.theme.BillionBeersTheme

@Preview(name = "Compose Multiplatform fixture", showBackground = true)
@Composable
@Suppress("PreviewPublic")
internal fun ComposeFixturePreview() {
  BillionBeersTheme {
    ComposeFixtureContent(
      state = ComposeFixtureUiState(text = "Preview", submissionCount = 1),
      onTextChanged = {},
      onSubmit = {},
      title = "Shared Compose fixture",
      inputLabel = "Focus input",
      submitLabel = "Submit event",
      requestInitialFocus = false,
    )
  }
}
