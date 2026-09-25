package com.simtop.billionbeers.shared.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.simtop.billionbeers.shared.designsystem.theme.BillionBeersTheme
import com.simtop.core.core.PagedListFooter
import com.simtop.core.core.PagedListUiModel

fun LazyListScope.sharedPagedListFooter(
  model: PagedListUiModel<*>,
  loadMoreFailedText: @Composable () -> String,
  retryText: @Composable () -> String,
  endOfListText: String,
  onRetryLoadMore: () -> Unit,
) {
  when {
    model.isLoadingNextPage -> item(key = "loading_footer") { LoadingMoreFooter() }
    model.footer is PagedListFooter.Retry ->
      item(key = "retry_footer") {
        LoadMoreRetryFooter(
          message = loadMoreFailedText(),
          retryLabel = retryText(),
          onRetry = onRetryLoadMore,
        )
      }
    model.footer is PagedListFooter.EndReached ->
      item(key = "end_footer") { EndOfListFooter(text = endOfListText) }
    else -> Unit
  }
}

@Composable
private fun LoadingMoreFooter() {
  Box(
    modifier = Modifier.fillMaxWidth().padding(BillionBeersTheme.spacing.large),
    contentAlignment = Alignment.Center,
  ) {
    CircularProgressIndicator(
      modifier = Modifier.size(BillionBeersTheme.spacing.extraLarge),
      strokeWidth = 3.dp,
    )
  }
}

@Composable
private fun LoadMoreRetryFooter(
  message: String,
  retryLabel: String,
  onRetry: () -> Unit,
) {
  Column(
    modifier = Modifier.fillMaxWidth().padding(BillionBeersTheme.spacing.medium),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Text(
      text = message,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
    TextButton(
      onClick = onRetry,
      modifier = Modifier.semantics { contentDescription = retryLabel },
    ) {
      Text(text = retryLabel)
    }
  }
}

@Composable
private fun EndOfListFooter(text: String) {
  Box(
    modifier = Modifier.fillMaxWidth().padding(BillionBeersTheme.spacing.large),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text = text,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}
