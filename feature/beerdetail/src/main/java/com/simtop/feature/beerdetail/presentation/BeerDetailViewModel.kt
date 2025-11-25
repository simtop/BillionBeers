package com.simtop.feature.beerdetail.presentation

import androidx.lifecycle.*
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.beerdomain.domain.usecases.UpdateAvailabilityUseCase
import com.simtop.core.core.CoroutineDispatcherProvider
import com.simtop.core.core.Either
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.simtop.core.core.CommonUiState

class BeerDetailViewModel @AssistedInject constructor(
    private val coroutineDispatcher: CoroutineDispatcherProvider,
    private val availabilityUseCase: UpdateAvailabilityUseCase,
    @Assisted private val beer: Beer
) : ViewModel() {

    private val _beerDetailViewState = MutableStateFlow<CommonUiState<Beer>>(CommonUiState.Loading)
    val beerDetailViewState: StateFlow<CommonUiState<Beer>> = _beerDetailViewState.asStateFlow()

    init {
        setBeer(beer)
    }

    fun updateAvailability(beer: Beer) {
        viewModelScope.launch(coroutineDispatcher.io) {
            changeAvailability(beer)
            availabilityUseCase.execute(availabilityUseCase.Params(beer))
                    .also(::treatResponse)
        }
    }

    private fun setBeer(beer: Beer) {
        _beerDetailViewState.value = CommonUiState.Success(beer)
    }

    private fun treatResponse(result: Either<Exception, Unit>) {
        result.either(
                {
                    _beerDetailViewState.value = CommonUiState.Error(it.message)
                },
                {
                }
        )
    }

    private fun changeAvailability(beer: Beer) {
        val newBeer = beer.copy(availability = !beer.availability)
        _beerDetailViewState.value = CommonUiState.Success(newBeer)
    }

    @dagger.assisted.AssistedFactory
    interface AssistedFactory {
        fun create(beer: Beer): BeerDetailViewModel
    }

    companion object {
        fun provideFactory(
            assistedFactory: AssistedFactory,
            beer: Beer
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return assistedFactory.create(beer) as T
            }
        }
    }
}
