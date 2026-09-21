package com.simtop.feature.beerdetail.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.serialization.saved
import com.simtop.beerdomain.domain.models.Beer
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
  @Assisted beer: Beer,
  @Assisted savedStateHandle: SavedStateHandle,
) :
  SharedBeerDetailViewModel(
    beersRepository = beersRepository,
    initialBeer = beer,
  ) {

  @AssistedFactory
  @ManualViewModelAssistedFactoryKey(Factory::class)
  @ContributesIntoMap(FeatureDetailScope::class)
  interface Factory : ManualViewModelAssistedFactory {
    fun create(
      @Assisted beer: Beer,
      @Assisted savedStateHandle: SavedStateHandle,
    ): BeerDetailViewModel
  }

  private var lastKnownBeer: Beer by savedStateHandle.saved(serializer = Beer.serializer()) { beer }

  init {
    restoreBeer(lastKnownBeer)
  }

  override fun persistBeer(beer: Beer) {
    lastKnownBeer = beer
  }
}

typealias BeerDetailEvent = SharedBeerDetailEvent
