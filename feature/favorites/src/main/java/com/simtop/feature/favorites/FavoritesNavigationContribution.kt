package com.simtop.feature.favorites

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.navigation.Favorites

/** Registers the favorites destination owned by this feature. */
fun EntryProviderScope<NavKey>.favoritesNavigationContribution(onBeerClick: (Beer) -> Unit) {
  entry<Favorites> { FavoritesScreen(onBeerClick = onBeerClick) }
}
