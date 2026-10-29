package com.simtop.billionbeers.widget

import android.content.Context
import com.simtop.beerdomain.domain.repositories.BeersRepository
import com.simtop.billionbeers.BillionBeersApplication
import com.simtop.core.core.retryWithErrorState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

internal fun Context.favoritesWidgetRepository(): BeersRepository =
  (applicationContext as BillionBeersApplication).appGraph.beersRepository

internal fun BeersRepository.observeFavoritesWidgetItems(
  onReadFailure: (Throwable) -> Unit = {}
): Flow<List<FavoritesWidgetItem>> = flow {
  var lastGoodItems = emptyList<FavoritesWidgetItem>()
  emitAll(
    observeFavoriteBeers()
      .map { it.toFavoritesWidgetItems() }
      .onEach { lastGoodItems = it }
      .retryWithErrorState { error ->
        onReadFailure(error)
        lastGoodItems
      }
      .distinctUntilChanged()
  )
}
