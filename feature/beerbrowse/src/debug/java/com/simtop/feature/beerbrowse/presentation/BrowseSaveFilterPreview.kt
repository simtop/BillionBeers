package com.simtop.feature.beerbrowse.presentation

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.billionbeers.core.designsystem.component.PreviewLightDark
import com.simtop.billionbeers.core.designsystem.theme.BillionBeersTheme
import com.simtop.core.core.CommonUiState
import com.simtop.core.core.PagedListUiModel

@PreviewLightDark
@Preview(name = "Brewery Save Large Text", group = "Design Review", fontScale = 1.5f)
@Composable
internal fun BrowseSaveFilterPreview() {
  BrowseFilterReviewFrame("ChuckleCraft Brewery", expanded = true)
}

@PreviewLightDark
@Composable
internal fun BrowseBreweryDesignReviewPreview() {
  BrowseFilterReviewFrame("ChuckleCraft Brewery", expanded = false)
}

@PreviewLightDark
@Composable
internal fun BrowseStyleDesignReviewPreview() {
  BrowseFilterReviewFrame("IPA (Indian Pale Ale)", expanded = false)
}

@PreviewLightDark
@Composable
internal fun BrowseStyleSaveFilterPreview() {
  BrowseFilterReviewFrame("IPA (Indian Pale Ale)", expanded = true)
}

@Composable
private fun BrowseFilterReviewFrame(title: String, expanded: Boolean) {
  BillionBeersTheme {
    BrowseBeersContent(
      title = title,
      viewState = reviewResultState,
      onBack = {},
      onBeerClick = {},
      onSaveQuery = { false },
      onScrollToBottom = {},
      onRetryLoadMore = {},
      onRetryFirstPage = {},
      saveInitiallyExpanded = expanded,
    )
  }
}

// Fixed review data stays separate from production renderers so the adapter can freeze it.
private val reviewResultState =
  CommonUiState.Success(
    PagedListUiModel(
      items =
        listOf(
          Beer.empty.copy(
            id = "beerbrowse-punk-ipa",
            name = "Punk IPA",
            tagline = "Post Modern Classic.",
            abv = 5.6,
            ibu = 41.5,
          ),
          Beer.empty.copy(
            id = "beerbrowse-hazy-jane",
            name = "Hazy Jane",
            tagline = "New England IPA.",
            abv = 5.0,
            ibu = 25.0,
          ),
        ),
      totalCount = 9,
    )
  )
