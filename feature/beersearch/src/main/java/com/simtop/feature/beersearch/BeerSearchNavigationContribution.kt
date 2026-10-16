package com.simtop.feature.beersearch

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.simtop.navigation.AndroidNavigationActions
import com.simtop.navigation.AndroidNavigationContribution
import com.simtop.navigation.BeersSearch
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject

@ContributesIntoSet(AppScope::class)
@Inject
class BeerSearchNavigationContribution : AndroidNavigationContribution {
  override fun contribute(
    scope: EntryProviderScope<NavKey>,
    actions: AndroidNavigationActions,
  ) {
    with(scope) {
      entry<BeersSearch> {
        BeersSearchScreen(
          onBeerClick = actions.onBeerClick,
          onBack = actions.onBack,
          onSaveQuery = actions.onSaveQuery,
        )
      }
    }
  }
}
