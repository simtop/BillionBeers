package com.simtop.billionbeers.presentation.beerdetail

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simtop.billionbeers.core.CoroutineDispatcherProvider
import com.simtop.billionbeers.core.Either
import com.simtop.billionbeers.domain.models.Beer
import com.simtop.billionbeers.domain.usecases.UpdateAvailabilityUseCase
import kotlinx.coroutines.launch
import javax.inject.Inject

class BeerDetailViewModel @Inject constructor(
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