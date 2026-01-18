package com.simtop.presentation_utils.custom_views

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.simtop.billionbeers.core.designsystem.theme.BillionBeersTheme
import androidx.compose.material3.MaterialTheme

@Composable
fun ComposeTitle(name: String) {
  Text(
    text = name,
    modifier =
      Modifier.padding(
        start = BillionBeersTheme.spacing.large,
        top = BillionBeersTheme.spacing.small,
        bottom = BillionBeersTheme.spacing.small,
        end = BillionBeersTheme.spacing.large
      ).fillMaxWidth(),
    style = MaterialTheme.typography.headlineMedium,
    textAlign = TextAlign.Center
  )
}

@Preview
@Composable
fun ComposeTitlePreview() {
  BillionBeersTheme {
    ComposeTitle("Hello")
  }
}
