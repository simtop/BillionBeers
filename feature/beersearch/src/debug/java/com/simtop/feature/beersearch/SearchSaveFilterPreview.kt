package com.simtop.feature.beersearch

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.beerdomain.domain.models.BeerStyle
import com.simtop.beerdomain.domain.models.BeersQuery
import com.simtop.billionbeers.core.designsystem.component.PreviewLightDark
import com.simtop.billionbeers.core.designsystem.theme.BillionBeersTheme
import com.simtop.core.core.CommonUiState
import com.simtop.core.core.PagedListUiModel

@PreviewLightDark
@Preview(name = "Search Save Large Text", group = "Design Review", fontScale = 1.5f)
@Composable
internal fun SearchSaveFilterPreview() {
  SearchFilterReviewFrame(expanded = true)
}

@PreviewLightDark
@Composable
internal fun SearchQueryDesignReviewPreview() {
  SearchFilterReviewFrame(expanded = false)
}

@Composable
private fun SearchFilterReviewFrame(expanded: Boolean) {
  BillionBeersTheme {
    BeersSearchContent(
      viewState = reviewResultState,
      query = "ipa",
      onQueryChange = {},
      activeQuery = BeersQuery(search = "ipa", styleId = "ipa-style"),
      styles = CommonUiState.Success(listOf(BeerStyle(id = "ipa-style", name = "IPA"))),
      selectedStyle = BeerStyle(id = "ipa-style", name = "IPA"),
      onBeerClick = {},
      onBack = {},
      onSaveQuery = { _, _ -> false },
      onScrollToBottom = {},
      onRetryLoadMore = {},
      onRetrySearch = {},
      autoFocus = false,
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
            id = "beersearch-punk-ipa",
            name = "Punk IPA",
            tagline = "Post Modern Classic.",
            abv = 5.6,
            ibu = 41.5,
          ),
          Beer.empty.copy(
            id = "beersearch-hazy-jane",
            name = "Hazy Jane",
            tagline = "New England IPA.",
            abv = 5.0,
            ibu = 25.0,
          ),
        ),
      totalCount = 159,
    )
  )
