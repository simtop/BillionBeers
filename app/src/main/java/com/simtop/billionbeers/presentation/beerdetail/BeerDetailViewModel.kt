package com.simtop.billionbeers.presentation.beerdetail

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simtop.billionbeers.core.Either
import com.simtop.billionbeers.domain.models.Beer
import com.simtop.billionbeers.domain.usecases.UpdateAvailabilityUseCase
import com.simtop.billionbeers.presentation.beerslist.BeersListViewState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import javax.inject.Inject

class BeerDetailViewModel @Inject constructor(private val availabilityUseCase: UpdateAvailabilityUseCase) :
    ViewModel() {

    private val _myViewState3 =
        MutableStateFlow<BeersDetailViewState<Beer>>(BeersDetailViewState.Loading)
    val beerDetailViewState: StateFlow<BeersDetailViewState<Beer>>
        get() = _myViewState3

    fun updateAvailability(beer: Beer) {
        viewModelScope.launch(Dispatchers.IO) {
            changeAvailability(beer)
            availabilityUseCase.execute(availabilityUseCase.Params(beer)).collect {
                process(it)
            }
        }
    }

    fun setBeer(beer: Beer) {
        _myViewState3.value = BeersDetailViewState.Success(beer)
    }

    private fun process(result: Either<Exception, Unit>) {
        result.either(
            {
                _myViewState3.value = BeersDetailViewState.Error(it)
            },
            {
                //Unit
            }
        )
    }

    private fun changeAvailability(beer: Beer) {
        //Important to use copy, if not, the FlowState will not update
        _myViewState3.value = BeersDetailViewState.Success(beer.copy(availability = !beer.availability))
    }
}

sealed class BeersDetailViewState<out T> {
    data class Success<out T>(val result: T) : BeersDetailViewState<T>()
    data class Error<out T>(val result: Exception) : BeersDetailViewState<T>()

    //Unused state, added to set initial state for the StateFlow, we could also use null to set up
    // TODO: Think if there is a better solution
    object Loading : BeersDetailViewState<Nothing>()
}