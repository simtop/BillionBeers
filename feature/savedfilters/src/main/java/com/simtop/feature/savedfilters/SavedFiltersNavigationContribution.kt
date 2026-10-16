package com.simtop.feature.savedfilters

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.navigation.SavedFilterPresets
import com.simtop.navigation.SavedFilterResults

/** Registers the saved-filter destinations owned by this feature. */
fun EntryProviderScope<NavKey>.savedFiltersNavigationContribution(
  onApply: (SavedFilterResults) -> Unit,
  onBack: () -> Unit,
  onBeerClick: (Beer) -> Unit,
) {
  entry<SavedFilterPresets> { SavedFiltersScreen(onApply = onApply) }

  entry<SavedFilterResults> { route ->
    SavedFilterResultsScreen(
      route = route,
      onBack = onBack,
      onBeerClick = onBeerClick,
    )
  }
}
