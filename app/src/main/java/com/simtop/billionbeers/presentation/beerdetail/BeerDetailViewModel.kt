package com.simtop.billionbeers.presentation.beerdetail

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simtop.billionbeers.core.Either
import com.simtop.billionbeers.domain.models.Beer
import com.simtop.billionbeers.domain.usecases.UpdateAvailabilityUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

class BeerDetailViewModel @Inject constructor(private val availabilityUseCase: UpdateAvailabilityUseCase) :
    ViewModel() {

    private val _myViewState3 =
        MutableLiveData<BeersDetailViewState<Beer>>()
    val beerDetailViewState: LiveData<BeersDetailViewState<Beer>>
        get() = _myViewState3

    fun updateAvailability(beer: Beer) {
        viewModelScope.launch(Dispatchers.IO) {
            changeAvailability(beer)
            availabilityUseCase.execute(availabilityUseCase.Params(beer))
                .also(::treatResponse)
        }
    }

    fun setBeer(beer: Beer){
        _myViewState3.postValue(BeersDetailViewState.Success(beer))
    }

    private fun treatResponse(result: Either<Exception, Unit>) {
        result.either(
            {
                _myViewState3.postValue(BeersDetailViewState.Error(it))
            },
            {
            }
        )
    }

    private fun changeAvailability(beer: Beer) {
        beer.availability = !beer.availability
        _myViewState3.postValue(BeersDetailViewState.Success(beer))
    }
}

sealed class BeersDetailViewState<out T> {
    data class Success<out T>(val result: T) : BeersDetailViewState<T>()
    data class Error<out T>(val result: Exception) : BeersDetailViewState<T>()
}