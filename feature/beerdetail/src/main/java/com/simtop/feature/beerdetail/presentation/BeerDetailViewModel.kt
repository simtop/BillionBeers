package com.simtop.feature.beerdetail.presentation

import androidx.hilt.lifecycle.ViewModelInject
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simtop.beerdomain.core.Either
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.beerdomain.domain.usecases.UpdateAvailabilityUseCase
import com.simtop.billionbeers.core.CoroutineDispatcherProvider
import kotlinx.coroutines.launch

class BeerDetailViewModel @ViewModelInject constructor(
        private val coroutineDispatcher: CoroutineDispatcherProvider,
        private val availabilityUseCase: UpdateAvailabilityUseCase
) : ViewModel() {

    private val _beerDetailViewState =
            MutableLiveData<BeersDetailViewState<Beer>>()
    val beerDetailViewState: LiveData<BeersDetailViewState<Beer>>
        get() = _beerDetailViewState

    fun updateAvailability(beer: Beer) {
        viewModelScope.launch(coroutineDispatcher.io) {
            changeAvailability(beer)
            availabilityUseCase.execute(availabilityUseCase.Params(beer))
                    .also(::treatResponse)
        }
    }

    fun setBeer(beer: Beer) {
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
}

sealed class BeersDetailViewState<out T> {
    data class Success<out T>(val result: T) : BeersDetailViewState<T>()
    data class Error(val result: String?) : BeersDetailViewState<Nothing>()
}