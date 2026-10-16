package com.simtop.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.beerdomain.domain.models.BeersQuery

data class AndroidNavigationActions(
  val navigate: (NavKey) -> Unit,
  val onBeerClick: (Beer) -> Unit,
  val onBack: () -> Unit,
  val onSaveQuery: suspend (String, BeersQuery) -> Boolean,
)

/** A feature-owned contributor to the app's Navigation 3 entry provider. */
interface AndroidNavigationContribution {
  fun contribute(scope: EntryProviderScope<NavKey>, actions: AndroidNavigationActions)
}
