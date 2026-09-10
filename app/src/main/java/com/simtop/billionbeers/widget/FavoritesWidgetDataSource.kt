package com.simtop.billionbeers.widget

import android.content.Context
import com.simtop.beerdomain.domain.repositories.BeersRepository
import com.simtop.billionbeers.BillionBeersApplication
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

internal fun Context.favoritesWidgetRepository(): BeersRepository =
  (applicationContext as BillionBeersApplication).appGraph.beersRepository

internal fun BeersRepository.observeFavoritesWidgetItems(): Flow<List<FavoritesWidgetItem>> =
  observeFavoriteBeers().map { it.toFavoritesWidgetItems() }.distinctUntilChanged()
