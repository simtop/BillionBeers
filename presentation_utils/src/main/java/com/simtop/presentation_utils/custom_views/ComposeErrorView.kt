package com.simtop.presentation_utils.custom_views

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.simtop.billionbeers.catalog_annotations.CatalogComponent
import com.simtop.billionbeers.core.designsystem.component.PreviewLightDark
import com.simtop.billionbeers.core.designsystem.theme.BillionBeersTheme
import com.simtop.presentation_utils.R

@CatalogComponent(tab = "Utilities")
@Composable
fun ComposeErrorView(
  modifier: Modifier = Modifier,
  message: String = stringResource(R.string.empty_state),
  onRetry: () -> Unit = {},
) {
  Column(
    modifier = modifier.fillMaxSize().padding(BillionBeersTheme.spacing.large),
    verticalArrangement = Arrangement.Center,
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Icon(
      imageVector = Icons.Default.Warning,
      contentDescription = null,
      modifier = Modifier.size(64.dp),
      tint = MaterialTheme.colorScheme.error,
    )

    Spacer(modifier = Modifier.height(BillionBeersTheme.spacing.medium))

    Text(
      text = message,
      style = MaterialTheme.typography.bodyLarge,
      textAlign = TextAlign.Center,
      color = MaterialTheme.colorScheme.onSurface,
      modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )

    Spacer(modifier = Modifier.height(BillionBeersTheme.spacing.large))

    Button(onClick = onRetry) { Text(text = stringResource(R.string.retry)) }
  }
}

@PreviewLightDark
@Composable
fun ComposeErrorViewPreview() {
  BillionBeersTheme { ComposeErrorView() }
}
