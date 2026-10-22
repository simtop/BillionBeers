package com.simtop.billionbeers.shared.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.simtop.billionbeers.shared.designsystem.controls.BrandCircularProgressIndicator
import com.simtop.billionbeers.shared.designsystem.controls.BrandText
import com.simtop.billionbeers.shared.designsystem.controls.BrandTextButton
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
    BrandCircularProgressIndicator(
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
    BrandText(
      text = message,
      style = BillionBeersTheme.typography.bodyMedium,
      color = BillionBeersTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
    BrandTextButton(
      onClick = onRetry,
      modifier = Modifier.semantics { contentDescription = retryLabel },
    ) {
      BrandText(text = retryLabel)
    }
  }
}

@Composable
private fun EndOfListFooter(text: String) {
  Box(
    modifier = Modifier.fillMaxWidth().padding(BillionBeersTheme.spacing.large),
    contentAlignment = Alignment.Center,
  ) {
    BrandText(
      text = text,
      style = BillionBeersTheme.typography.bodyMedium,
      color = BillionBeersTheme.colorScheme.onSurfaceVariant,
    )
  }
}
