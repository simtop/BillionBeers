package com.simtop.billionbeers.shared.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.simtop.billionbeers.shared.designsystem.theme.BillionBeersTheme

@Composable
fun SharedErrorView(
  message: String,
  retryLabel: String,
  modifier: Modifier = Modifier,
  onRetry: () -> Unit = {},
  leadingContent: @Composable () -> Unit = {},
) {
  Column(
    modifier = modifier.fillMaxSize().padding(BillionBeersTheme.spacing.large),
    verticalArrangement = Arrangement.Center,
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    leadingContent()

    Spacer(modifier = Modifier.height(BillionBeersTheme.spacing.medium))

    Text(
      text = message,
      style = MaterialTheme.typography.bodyLarge,
      textAlign = TextAlign.Center,
      color = MaterialTheme.colorScheme.onSurface,
      modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )

    Spacer(modifier = Modifier.height(BillionBeersTheme.spacing.large))

    Button(onClick = onRetry) { Text(text = retryLabel) }
  }
}
