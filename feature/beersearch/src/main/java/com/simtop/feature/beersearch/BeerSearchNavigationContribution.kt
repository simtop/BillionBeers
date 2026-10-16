package com.simtop.feature.beersearch

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.beerdomain.domain.models.BeersQuery
import com.simtop.navigation.BeersSearch

/** Registers the search destination owned by this feature. */
fun EntryProviderScope<NavKey>.beerSearchNavigationContribution(
  onBeerClick: (Beer) -> Unit,
  onBack: () -> Unit,
  onSaveQuery: suspend (String, BeersQuery) -> Boolean,
) {
  entry<BeersSearch> {
    BeersSearchScreen(
      onBeerClick = onBeerClick,
      onBack = onBack,
      onSaveQuery = onSaveQuery,
    )
  }
}
