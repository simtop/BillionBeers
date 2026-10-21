package com.simtop.feature.beerbrowse.presentation

import androidx.compose.runtime.Composable
import com.simtop.billionbeers.core.designsystem.component.PreviewLightDark
import com.simtop.billionbeers.core.designsystem.theme.BillionBeersTheme

@PreviewLightDark
@Composable
internal fun BrowseSaveFilterPreview() {
  BillionBeersTheme {
    BrowseBeersContent(
      title = "ChuckleCraft Brewery",
      viewState = BrowseBeersPreviewParameterProvider().values.first().state,
      onBack = {},
      onBeerClick = {},
      onSaveQuery = { false },
      onScrollToBottom = {},
      onRetryLoadMore = {},
      onRetryFirstPage = {},
      saveInitiallyExpanded = true,
    )
  }
}
