package com.simtop.feature.beerdetail.presentation

import androidx.annotation.Keep
import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavKey
import com.simtop.navigation.BeerDetail
import com.simtop.navigation.DynamicFeatureContentProvider

@Suppress("MISSING_DEPENDENCY_SUPERCLASS_IN_TYPE_ARGUMENT")
@Keep
class BeerDetailProviderImpl : DynamicFeatureContentProvider<BeerDetail> {
  // Detail is a leaf screen: it never navigates forward, so onNavigate is unused.
  @Composable
  override fun Content(
    key: BeerDetail,
    onBack: () -> Unit,
    onNavigate: (NavKey) -> Unit,
    showBackButton: Boolean,
  ) {
    BeerDetailScreenImpl(
      beer = key.beer,
      onBackClick = onBack,
      showBackButton = showBackButton,
    )
  }
}
