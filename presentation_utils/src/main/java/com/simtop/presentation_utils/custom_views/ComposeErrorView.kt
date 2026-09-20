package com.simtop.presentation_utils.custom_views

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.simtop.billionbeers.catalog_annotations.CatalogComponent
import com.simtop.billionbeers.core.designsystem.component.PreviewLightDark
import com.simtop.billionbeers.core.designsystem.theme.BillionBeersTheme
import com.simtop.billionbeers.shared.presentation.SharedErrorView
import com.simtop.presentation_utils.R

@CatalogComponent(tab = "Utilities")
@Composable
fun ComposeErrorView(
  modifier: Modifier = Modifier,
  message: String = stringResource(R.string.empty_state),
  onRetry: () -> Unit = {},
) {
  SharedErrorView(
    message = message,
    retryLabel = stringResource(R.string.retry),
    modifier = modifier,
    onRetry = onRetry,
    leadingContent = {
      Icon(
        imageVector = Icons.Default.Warning,
        contentDescription = null,
        modifier = Modifier.size(64.dp),
        tint = MaterialTheme.colorScheme.error,
      )
    },
  )
}

@PreviewLightDark
@Composable
internal fun ComposeErrorViewPreview() {
  BillionBeersTheme { ComposeErrorView() }
}
