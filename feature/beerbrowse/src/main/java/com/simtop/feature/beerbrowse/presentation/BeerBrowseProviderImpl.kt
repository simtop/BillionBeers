package com.simtop.feature.beerbrowse.presentation

import androidx.annotation.Keep
import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavKey
import com.simtop.beerdomain.domain.models.BeersQuery
import com.simtop.navigation.DynamicFeatureContentProvider
import com.simtop.navigation.contract.PortableRoute

@Suppress("MISSING_DEPENDENCY_SUPERCLASS_IN_TYPE_ARGUMENT")
@Keep
class BeerBrowseProviderImpl : DynamicFeatureContentProvider<PortableRoute.BeerBrowse> {
  @Composable
  override fun Content(
    key: PortableRoute.BeerBrowse,
    onBack: () -> Unit,
    onNavigate: (NavKey) -> Unit,
    onSaveQuery: (suspend (String, BeersQuery) -> Boolean)?,
    showBackButton: Boolean,
  ) {
    BeerBrowseScreenImpl(
      onBack = onBack,
      onNavigate = onNavigate,
      onSaveQuery = onSaveQuery,
    )
  }
}
