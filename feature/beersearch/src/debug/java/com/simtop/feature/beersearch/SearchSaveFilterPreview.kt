package com.simtop.feature.beersearch

import androidx.compose.runtime.Composable
import com.simtop.beerdomain.domain.models.BeerStyle
import com.simtop.beerdomain.domain.models.BeersQuery
import com.simtop.billionbeers.core.designsystem.component.PreviewLightDark
import com.simtop.billionbeers.core.designsystem.theme.BillionBeersTheme
import com.simtop.core.core.CommonUiState

@PreviewLightDark
@Composable
internal fun SearchSaveFilterPreview() {
  BillionBeersTheme {
    BeersSearchContent(
      viewState = BeersSearchPreviewParameterProvider().values.elementAt(2).state,
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
      saveInitiallyExpanded = true,
    )
  }
}
