package com.simtop.feature.beerdetail.presentation

import com.simtop.beerdomain.domain.repositories.BeersRepository
import com.simtop.billionbeers.shared.beerdetail.BeerDetailEvent as SharedBeerDetailEvent
import com.simtop.billionbeers.shared.beerdetail.BeerDetailViewModel as SharedBeerDetailViewModel
import com.simtop.feature.beerdetail.presentation.di.FeatureDetailScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey

class BeerDetailViewModel
@AssistedInject
constructor(
  beersRepository: BeersRepository,
  @Assisted beerId: String,
) :
  SharedBeerDetailViewModel(
    beersRepository = beersRepository,
    beerId = beerId,
  ) {

  @AssistedFactory
  @ManualViewModelAssistedFactoryKey(Factory::class)
  @ContributesIntoMap(FeatureDetailScope::class)
  interface Factory : ManualViewModelAssistedFactory {
    fun create(@Assisted beerId: String): BeerDetailViewModel
  }
}

typealias BeerDetailEvent = SharedBeerDetailEvent
