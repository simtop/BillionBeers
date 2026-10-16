package com.simtop.feature.beerdetail.presentation

import androidx.annotation.Keep
import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavKey
import com.simtop.beerdomain.domain.models.BeersQuery
import com.simtop.navigation.DynamicFeatureContentProvider
import com.simtop.navigation.contract.PortableRoute

@Suppress("MISSING_DEPENDENCY_SUPERCLASS_IN_TYPE_ARGUMENT")
@Keep
class BeerDetailProviderImpl : DynamicFeatureContentProvider<PortableRoute.BeerDetail> {
  // Detail is a leaf screen: it never navigates forward, so onNavigate is unused.
  @Composable
  override fun Content(
    key: PortableRoute.BeerDetail,
    onBack: () -> Unit,
    onNavigate: (NavKey) -> Unit,
    onSaveQuery: (suspend (String, BeersQuery) -> Boolean)?,
    showBackButton: Boolean,
  ) {
    BeerDetailScreenImpl(
      beerId = key.beerId,
      onBackClick = onBack,
      showBackButton = showBackButton,
    )
  }
}
