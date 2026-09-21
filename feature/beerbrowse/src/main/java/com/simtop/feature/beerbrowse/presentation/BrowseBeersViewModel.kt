package com.simtop.feature.beerbrowse.presentation

import com.simtop.beerdomain.domain.models.BeersQuery
import com.simtop.beerdomain.domain.repositories.BeersPagerFactory
import com.simtop.billionbeers.shared.beerbrowse.BrowseBeersEvent as SharedBrowseBeersEvent
import com.simtop.billionbeers.shared.beerbrowse.BrowseBeersViewModel as SharedBrowseBeersViewModel
import com.simtop.core.core.CoroutineDispatcherProvider
import com.simtop.feature.beerbrowse.presentation.di.FeatureBrowseScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey

class BrowseBeersViewModel
@AssistedInject
constructor(
  coroutineDispatcher: CoroutineDispatcherProvider,
  beersPagerFactory: BeersPagerFactory,
  @Assisted query: BeersQuery,
) : SharedBrowseBeersViewModel(coroutineDispatcher, beersPagerFactory, query) {

  @AssistedFactory
  @ManualViewModelAssistedFactoryKey(Factory::class)
  @ContributesIntoMap(FeatureBrowseScope::class)
  interface Factory : ManualViewModelAssistedFactory {
    fun create(@Assisted query: BeersQuery): BrowseBeersViewModel
  }
}

typealias BrowseBeersEvent = SharedBrowseBeersEvent
