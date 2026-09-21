package com.simtop.feature.beersearch

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.simtop.beerdomain.domain.repositories.BeersPagerFactory
import com.simtop.billionbeers.shared.beersearch.BeersSearchEvent as SharedBeersSearchEvent
import com.simtop.billionbeers.shared.beersearch.BeersSearchViewModel as SharedBeersSearchViewModel
import com.simtop.core.core.CoroutineDispatcherProvider
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ViewModelAssistedFactoryKey
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@AssistedInject
class BeersSearchViewModel(
  coroutineDispatcher: CoroutineDispatcherProvider,
  beersPagerFactory: BeersPagerFactory,
  @Assisted private val savedStateHandle: SavedStateHandle,
) :
  SharedBeersSearchViewModel(
    coroutineDispatcher = coroutineDispatcher,
    beersPagerFactory = beersPagerFactory,
    initialQuery = savedStateHandle.get<String>(KEY_QUERY).orEmpty(),
  ) {

  @AssistedFactory
  @ViewModelAssistedFactoryKey(BeersSearchViewModel::class)
  @ContributesIntoMap(AppScope::class)
  fun interface Factory : ViewModelAssistedFactory {
    override fun create(extras: CreationExtras): BeersSearchViewModel =
      create(extras.createSavedStateHandle())

    fun create(@Assisted savedStateHandle: SavedStateHandle): BeersSearchViewModel
  }

  init {
    query.onEach { savedStateHandle[KEY_QUERY] = it }.launchIn(viewModelScope)
  }

  private companion object {
    const val KEY_QUERY = "search_query"
  }
}

typealias BeersSearchEvent = SharedBeersSearchEvent
