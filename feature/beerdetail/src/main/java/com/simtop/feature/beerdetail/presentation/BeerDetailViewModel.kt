package com.simtop.feature.beerdetail.presentation

import androidx.lifecycle.*
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.beerdomain.domain.usecases.UpdateAvailabilityUseCase
import com.simtop.core.core.CoroutineDispatcherProvider
import com.simtop.core.core.Either
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.launch

class BeerDetailViewModel @AssistedInject constructor(
    private val coroutineDispatcher: CoroutineDispatcherProvider,
    private val availabilityUseCase: UpdateAvailabilityUseCase,
    @Assisted private val beer: Beer
) : ViewModel() {

    private val _beerDetailViewState =
            MutableLiveData<BeersDetailViewState<Beer>>()
    val beerDetailViewState: LiveData<BeersDetailViewState<Beer>>
        get() = _beerDetailViewState

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
        _beerDetailViewState.postValue(BeersDetailViewState.Success(beer))
    }

    private fun treatResponse(result: Either<Exception, Unit>) {
        result.either(
                {
                    _beerDetailViewState.postValue(BeersDetailViewState.Error(it.message))
                },
                {
                }
        )
    }

    private fun changeAvailability(beer: Beer) {
        beer.availability = !beer.availability
        _beerDetailViewState.postValue(BeersDetailViewState.Success(beer))
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

sealed class BeersDetailViewState<out T> {
    data class Success<out T>(val result: T) : BeersDetailViewState<T>()
    data class Error(val result: String?) : BeersDetailViewState<Nothing>()
}
