package com.simtop.feature.beerdetail.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.serialization.saved
import androidx.lifecycle.viewModelScope
import com.simtop.beerdomain.domain.errors.UpdateAvailabilityError
import com.simtop.beerdomain.domain.errors.UpdateFavoriteError
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.beerdomain.domain.repositories.BeersRepository
import com.simtop.core.core.CommonUiState
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class BeerDetailViewModel
@AssistedInject
constructor(
  private val beersRepository: BeersRepository,
  @Assisted beer: Beer,
  @Assisted savedStateHandle: SavedStateHandle,
) : ViewModel() {

  @AssistedFactory
  @ManualViewModelAssistedFactoryKey(Factory::class)
  @ContributesIntoMap(FeatureDetailScope::class)
  interface Factory : ManualViewModelAssistedFactory {
    fun create(
      @Assisted beer: Beer,
      @Assisted savedStateHandle: SavedStateHandle,
    ): BeerDetailViewModel
  }

  // Last state shown on screen, saved across process death. The assisted beer param comes from
  // the nav arg, which goes stale the moment availability is toggled - restoring from it would
  // silently revert the toggle.
  private var lastKnownBeer: Beer by savedStateHandle.saved(serializer = Beer.serializer()) { beer }

  private val _beerDetailViewState = MutableStateFlow<CommonUiState<Beer>>(CommonUiState.Loading)
  val beerDetailViewState: StateFlow<CommonUiState<Beer>> = _beerDetailViewState.asStateFlow()

  private val _events = Channel<BeerDetailEvent>(Channel.BUFFERED)
  val events: Flow<BeerDetailEvent> = _events.receiveAsFlow()

  private val availabilityUpdateMutex = Mutex()
  private val favoriteUpdateMutex = Mutex()
  private var availabilityUpdateVersion = 0L
  private var favoriteUpdateVersion = 0L

  init {
    setBeer(lastKnownBeer)
  }

  fun updateAvailability(beer: Beer) {
    viewModelScope.launch {
      availabilityUpdateMutex.withLock {
        val originalAvailability = beer.availability
        val newAvailability = !originalAvailability
        val version = ++availabilityUpdateVersion
        setBeer(currentBeer().copy(availability = newAvailability))
        treatResponse(
          result = beersRepository.updateAvailability(beer.copy(availability = newAvailability)),
          originalAvailability = originalAvailability,
          optimisticAvailability = newAvailability,
          version = version,
        )
      }
    }
  }

  fun updateFavorite(beer: Beer) {
    viewModelScope.launch {
      favoriteUpdateMutex.withLock {
        val originalFavorite = beer.isFavorite
        val newFavorite = !originalFavorite
        val version = ++favoriteUpdateVersion
        setBeer(currentBeer().copy(isFavorite = newFavorite))
        treatFavoriteResponse(
          result = beersRepository.updateFavorite(beer.copy(isFavorite = newFavorite)),
          originalFavorite = originalFavorite,
          optimisticFavorite = newFavorite,
          version = version,
        )
      }
    }
  }

  private fun currentBeer(): Beer =
    when (val state = _beerDetailViewState.value) {
      is CommonUiState.Success -> state.data
      else -> lastKnownBeer
    }

  private fun setBeer(beer: Beer) {
    lastKnownBeer = beer
    _beerDetailViewState.value = CommonUiState.Success(beer)
  }

  private suspend fun treatFavoriteResponse(
    result: Either<UpdateFavoriteError, Unit>,
    originalFavorite: Boolean,
    optimisticFavorite: Boolean,
    version: Long,
  ) {
    when (result) {
      is Either.Left -> {
        if (version == favoriteUpdateVersion && currentBeer().isFavorite == optimisticFavorite) {
          setBeer(currentBeer().copy(isFavorite = originalFavorite))
        }
        _events.send(BeerDetailEvent.ShowError(result.value.toUiMessage()))
      }
      is Either.Right -> _events.send(BeerDetailEvent.FavoriteUpdated)
    }
  }

  private suspend fun treatResponse(
    result: Either<UpdateAvailabilityError, Unit>,
    originalAvailability: Boolean,
    optimisticAvailability: Boolean,
    version: Long,
  ) {
    when (result) {
      is Either.Left -> {
        if (
          version == availabilityUpdateVersion &&
            currentBeer().availability == optimisticAvailability
        ) {
          setBeer(currentBeer().copy(availability = originalAvailability))
        }
        _events.send(BeerDetailEvent.ShowError(result.value.toUiMessage()))
      }
      is Either.Right -> Unit
    }
  }

  private fun UpdateAvailabilityError.toUiMessage(): String =
    when (this) {
      is UpdateAvailabilityError.Unknown -> cause.message ?: "Unable to update availability"
    }

  private fun UpdateFavoriteError.toUiMessage(): String =
    when (this) {
      is UpdateFavoriteError.Unknown -> cause.message ?: "Unable to update favorite"
    }
}

sealed interface BeerDetailEvent {
  data class ShowError(val message: String) : BeerDetailEvent

  data object FavoriteUpdated : BeerDetailEvent
}
