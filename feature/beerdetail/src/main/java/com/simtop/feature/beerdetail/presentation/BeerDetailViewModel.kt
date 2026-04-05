package com.simtop.feature.beerdetail.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.beerdomain.domain.usecases.UpdateAvailabilityUseCase
import com.simtop.core.core.CommonUiState
import com.simtop.core.core.CoroutineDispatcherProvider
import com.simtop.core.core.Either
import com.simtop.feature.beerdetail.presentation.di.FeatureDetailScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class BeerDetailViewModel
@AssistedInject
constructor(
  private val coroutineDispatcher: CoroutineDispatcherProvider,
  private val availabilityUseCase: UpdateAvailabilityUseCase,
  @Assisted private val beer: Beer
) : ViewModel() {

  @AssistedFactory
  @ManualViewModelAssistedFactoryKey(Factory::class)
  @ContributesIntoMap(FeatureDetailScope::class)
  interface Factory : ManualViewModelAssistedFactory {
    fun create(@Assisted beer: Beer): BeerDetailViewModel
  }

  private val _beerDetailViewState = MutableStateFlow<CommonUiState<Beer>>(CommonUiState.Loading)
  val beerDetailViewState: StateFlow<CommonUiState<Beer>> = _beerDetailViewState.asStateFlow()

  init {
    setBeer(beer)
  }

  fun updateAvailability(beer: Beer) {
    viewModelScope.launch(coroutineDispatcher.io) {
      val newBeer = beer.copy(availability = !beer.availability)
      changeAvailability(newBeer)
      availabilityUseCase.execute(availabilityUseCase.Params(newBeer)).also(::treatResponse)
    }
  }

  private fun setBeer(beer: Beer) {
    _beerDetailViewState.value = CommonUiState.Success(beer)
  }

  private fun treatResponse(result: Either<Exception, Unit>) {
    result.either({ _beerDetailViewState.value = CommonUiState.Error(it.message) }, {})
  }

  private fun changeAvailability(beer: Beer) {
    _beerDetailViewState.value = CommonUiState.Success(beer)
  }
}
