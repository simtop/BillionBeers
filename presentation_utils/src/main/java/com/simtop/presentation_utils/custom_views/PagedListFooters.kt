package com.simtop.presentation_utils.custom_views

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.ui.res.stringResource
import com.simtop.billionbeers.shared.presentation.sharedPagedListFooter
import com.simtop.core.core.PagedListUiModel
import com.simtop.presentation_utils.R

/**
 * The shared bottom-of-list affordance for a paged [LazyListScope]: a spinner while the next page
 * loads, a retry row after a failed "load more", or the end caption once everything is in.
 * [endOfListText] is caller-resolved because its copy is per-surface ("That's all N beers" vs "N
 * results"). Android resolves the localized adapter strings before delegating to shared rendering.
 */
fun LazyListScope.pagedListFooter(
  model: PagedListUiModel<*>,
  endOfListText: String,
  onRetryLoadMore: () -> Unit,
) {
  sharedPagedListFooter(
    model = model,
    loadMoreFailedText = { stringResource(R.string.paged_list_load_more_failed) },
    retryText = { stringResource(R.string.retry) },
    endOfListText = endOfListText,
    onRetryLoadMore = onRetryLoadMore,
  )
}
