package com.simtop.billionbeers.shared.beerdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simtop.beerdomain.domain.errors.UpdateAvailabilityError
import com.simtop.beerdomain.domain.errors.UpdateFavoriteError
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.beerdomain.domain.repositories.BeersRepository
import com.simtop.core.core.CommonUiState
import com.simtop.core.core.Either
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

open class BeerDetailViewModel(
  private val beersRepository: BeersRepository,
  private val beerId: String,
  initialBeer: Beer? = null,
) : ViewModel() {

  constructor(
    beersRepository: BeersRepository,
    initialBeer: Beer,
  ) : this(beersRepository, initialBeer.id, initialBeer)

  private var lastKnownBeer = initialBeer

  private val _beerDetailViewState =
    MutableStateFlow<CommonUiState<Beer>>(
      initialBeer?.let { CommonUiState.Success(it) } ?: CommonUiState.Loading
    )
  val beerDetailViewState: StateFlow<CommonUiState<Beer>> = _beerDetailViewState.asStateFlow()

  private val _events = Channel<BeerDetailEvent>(Channel.BUFFERED)
  val events: Flow<BeerDetailEvent> = _events.receiveAsFlow()

  private val availabilityUpdateMutex = Mutex()
  private val favoriteUpdateMutex = Mutex()
  private var availabilityUpdateVersion = 0L
  private var favoriteUpdateVersion = 0L

  init {
    if (initialBeer == null) {
      viewModelScope.launch {
        try {
          val beer = beersRepository.getBeerById(beerId)
          if (beer == null) _beerDetailViewState.value = CommonUiState.Error() else setBeer(beer)
        } catch (e: kotlinx.coroutines.CancellationException) {
          throw e
        } catch (_: Exception) {
          _beerDetailViewState.value = CommonUiState.Error()
        }
      }
    }
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

  protected fun restoreBeer(beer: Beer) {
    if (beer != currentBeer()) setBeer(beer)
  }

  private fun currentBeer(): Beer =
    when (val state = _beerDetailViewState.value) {
      is CommonUiState.Success -> state.data
      else -> checkNotNull(lastKnownBeer) { "Beer $beerId has not loaded" }
    }

  private fun setBeer(beer: Beer) {
    lastKnownBeer = beer
    persistBeer(beer)
    _beerDetailViewState.value = CommonUiState.Success(beer)
  }

  protected open fun persistBeer(beer: Beer) = Unit

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
        _events.send(BeerDetailEvent.ShowError(BeerDetailError.FavoriteUpdate))
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
        _events.send(BeerDetailEvent.ShowError(BeerDetailError.AvailabilityUpdate))
      }
      is Either.Right -> Unit
    }
  }
}

enum class BeerDetailError {
  FavoriteUpdate,
  AvailabilityUpdate,
}

sealed interface BeerDetailEvent {
  data class ShowError(val error: BeerDetailError) : BeerDetailEvent

  data object FavoriteUpdated : BeerDetailEvent
}
