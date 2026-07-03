package com.simtop.feature.beerdetail.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simtop.beerdomain.domain.errors.UpdateAvailabilityError
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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class BeerDetailViewModel
@AssistedInject
constructor(
  private val coroutineDispatcher: CoroutineDispatcherProvider,
  private val availabilityUseCase: UpdateAvailabilityUseCase,
  @Assisted private val beer: Beer,
) : ViewModel() {

  @AssistedFactory
  @ManualViewModelAssistedFactoryKey(Factory::class)
  @ContributesIntoMap(FeatureDetailScope::class)
  interface Factory : ManualViewModelAssistedFactory {
    fun create(@Assisted beer: Beer): BeerDetailViewModel
  }

  private val _beerDetailViewState = MutableStateFlow<CommonUiState<Beer>>(CommonUiState.Loading)
  val beerDetailViewState: StateFlow<CommonUiState<Beer>> = _beerDetailViewState.asStateFlow()

  private val _events = Channel<BeerDetailEvent>(Channel.BUFFERED)
  val events: Flow<BeerDetailEvent> = _events.receiveAsFlow()

  init {
    setBeer(beer)
  }

  fun updateAvailability(beer: Beer) {
    viewModelScope.launch(coroutineDispatcher.io) {
      val newBeer = beer.copy(availability = !beer.availability)
      changeAvailability(newBeer)
      treatResponse(result = availabilityUseCase(newBeer), originalBeer = beer)
    }
  }

  private fun setBeer(beer: Beer) {
    _beerDetailViewState.value = CommonUiState.Success(beer)
  }

  private suspend fun treatResponse(
    result: Either<UpdateAvailabilityError, Unit>,
    originalBeer: Beer,
  ) {
    when (result) {
      is Either.Left -> {
        _beerDetailViewState.value = CommonUiState.Success(originalBeer)
        _events.send(BeerDetailEvent.ShowError(result.value.toUiMessage()))
      }
      is Either.Right -> Unit
    }
  }

  private fun UpdateAvailabilityError.toUiMessage(): String =
    when (this) {
      is UpdateAvailabilityError.Unknown -> cause.message ?: "Unable to update availability"
    }

  private fun changeAvailability(beer: Beer) {
    _beerDetailViewState.value = CommonUiState.Success(beer)
  }
}

sealed interface BeerDetailEvent {
  data class ShowError(val message: String) : BeerDetailEvent
}
