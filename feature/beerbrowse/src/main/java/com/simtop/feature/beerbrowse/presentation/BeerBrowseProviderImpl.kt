package com.simtop.feature.beerbrowse.presentation

import androidx.annotation.Keep
import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavKey
import com.simtop.navigation.BeerBrowse
import com.simtop.navigation.DynamicFeatureContentProvider

@Suppress("MISSING_DEPENDENCY_SUPERCLASS_IN_TYPE_ARGUMENT")
@Keep
class BeerBrowseProviderImpl : DynamicFeatureContentProvider<BeerBrowse> {
  @Composable
  override fun Content(key: BeerBrowse, onBack: () -> Unit, onNavigate: (NavKey) -> Unit) {
    BeerBrowseScreenImpl(onBack = onBack, onNavigate = onNavigate)
  }
}
