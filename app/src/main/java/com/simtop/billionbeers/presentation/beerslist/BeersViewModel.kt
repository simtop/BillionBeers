package com.simtop.billionbeers.presentation.beerslist

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simtop.billionbeers.core.Either
import com.simtop.billionbeers.core.MAX_PAGES_FOR_PAGINATION
import com.simtop.billionbeers.core.ViewState
import com.simtop.billionbeers.domain.models.Beer
import com.simtop.billionbeers.domain.usecases.GetAllBeersUseCase
import com.simtop.billionbeers.domain.usecases.UpdateAvailabilityUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

class BeersViewModel @Inject constructor(
    private val getAllBeersUseCase: GetAllBeersUseCase,
    private val availabilityUseCase: UpdateAvailabilityUseCase
) : ViewModel() {

    private val _myViewState =
        MutableLiveData<ViewState<Exception, List<Beer>>>()
    val myViewState: LiveData<ViewState<Exception, List<Beer>>>
        get() = _myViewState

    private val _detailBeer =
        MutableLiveData<Beer>()
    val detailBeer: LiveData<Beer>
        get() = _detailBeer

    fun getAllBeers(quantity: Int = MAX_PAGES_FOR_PAGINATION) {
        _myViewState.postValue(ViewState.Loading)
        viewModelScope.launch(Dispatchers.IO) {
            getAllBeersUseCase.execute(getAllBeersUseCase.Params(quantity))
                .also(::process)
        }
    }

    private fun process(either: Either<Exception, List<Beer>>) {
        _myViewState.postValue(ViewState.Result(either))
    }

    fun saveBeerDetail(beer: Beer) {
        _detailBeer.value = beer
    }

    fun updateAvailability() {
        viewModelScope.launch(Dispatchers.IO) {
            changeAvailability()
            availabilityUseCase.execute(availabilityUseCase.Params(detailBeer.value!!))
                .also(::updateListBeers)
        }
    }

    fun showEmptyState() {
        _myViewState.postValue(ViewState.EmptyState)
    }

    private fun changeAvailability() {
        _detailBeer.value!!.availability = !detailBeer.value!!.availability
        _detailBeer.postValue(_detailBeer.value)
    }

    private fun updateListBeers(either: Either<Exception, Unit>) {
        when (either) {
            is Either.Left -> _myViewState.postValue(ViewState.Result(either))
            is Either.Right -> getAllBeers()
        }
    }

}