package com.simtop.presentation_utils.custom_views

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.simtop.billionbeers.core.designsystem.theme.BillionBeersTheme
import com.simtop.core.core.PagedListFooter
import com.simtop.core.core.PagedListUiModel
import com.simtop.presentation_utils.R

/**
 * The shared bottom-of-list affordance for a paged [LazyListScope]: a spinner while the next page
 * loads, a retry row after a failed "load more", or the end caption once everything is in.
 * [endOfListText] is caller-resolved because its copy is per-surface ("That's all N beers" vs "N
 * results").
 */
fun LazyListScope.pagedListFooter(
  model: PagedListUiModel<*>,
  endOfListText: String,
  onRetryLoadMore: () -> Unit,
) {
  when {
    model.isLoadingNextPage -> item(key = "loading_footer") { LoadingMoreFooter() }
    model.footer is PagedListFooter.Retry ->
      item(key = "retry_footer") { LoadMoreRetryFooter(onRetry = onRetryLoadMore) }
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
private fun LoadMoreRetryFooter(onRetry: () -> Unit) {
  Column(
    modifier = Modifier.fillMaxWidth().padding(BillionBeersTheme.spacing.medium),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Text(
      text = stringResource(R.string.paged_list_load_more_failed),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    TextButton(onClick = onRetry) { Text(text = stringResource(R.string.retry)) }
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
