package com.simtop.billionbeers.presentation.beerslist

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simtop.billionbeers.core.*
import com.simtop.billionbeers.domain.models.Beer
import com.simtop.billionbeers.domain.usecases.GetAllBeersUseCase
import com.simtop.billionbeers.domain.usecases.UpdateAvailabilityUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

class BeersListViewModel @Inject constructor(
    private val getAllBeersUseCase: GetAllBeersUseCase
) : ViewModel() {

    private val _beerListViewState =
        MutableLiveData<BeersListViewState<List<Beer>>>()
    val beerListViewState: LiveData<BeersListViewState<List<Beer>>>
        get() = _beerListViewState

    fun getAllBeers(quantity: Int = MAX_PAGES_FOR_PAGINATION) {
        _beerListViewState.postValue(BeersListViewState.Loading)
        viewModelScope.launch(Dispatchers.IO) {
            getAllBeersUseCase.execute(getAllBeersUseCase.Params(quantity))
                .also(::process)
        }
    }

    private fun process(result: Either<Exception, List<Beer>>) {
        result.either(
            {
                _beerListViewState.postValue(BeersListViewState.Error(it))
            },
            {
                if(it.isEmpty()) _beerListViewState.postValue(BeersListViewState.EmptyState)
                else _beerListViewState.postValue(BeersListViewState.Success(it))
            }
        )
    }

    fun showEmptyState() {
        _beerListViewState.postValue(BeersListViewState.EmptyState)
    }
}

sealed class BeersListViewState <out T> {
    data class Success<out T>(val result: T) : BeersListViewState<T>()
    data class Error<out T>(val result: Exception) : BeersListViewState<T>()
    object Loading : BeersListViewState<Nothing>()
    object EmptyState : BeersListViewState<Nothing>()
}