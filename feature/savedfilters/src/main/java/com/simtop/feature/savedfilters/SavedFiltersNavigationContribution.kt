package com.simtop.feature.savedfilters

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.simtop.navigation.AndroidNavigationActions
import com.simtop.navigation.AndroidNavigationContribution
import com.simtop.navigation.SavedFilterResults
import com.simtop.navigation.contract.PortableRoute
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject

@ContributesIntoSet(AppScope::class)
@Inject
class SavedFiltersNavigationContribution : AndroidNavigationContribution {
  override fun contribute(
    scope: EntryProviderScope<NavKey>,
    actions: AndroidNavigationActions,
  ) {
    with(scope) {
      entry<PortableRoute.SavedFilterPresets> { SavedFiltersScreen(onApply = actions.navigate) }

      entry<SavedFilterResults> { route ->
        SavedFilterResultsScreen(
          route = route,
          onBack = actions.onBack,
          onBeerClick = actions.onBeerClick,
        )
      }
    }
  }
}
