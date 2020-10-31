package com.simtop.billionbeers.presentation.beerslist


import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simtop.billionbeers.core.*
import com.simtop.billionbeers.domain.models.Beer
import com.simtop.billionbeers.domain.usecases.GetAllBeersUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

class BeersListViewModel @Inject constructor(
    private val getAllBeersUseCase: GetAllBeersUseCase
) : ViewModel() {

    private val _beerListViewState =
        MutableStateFlow<BeersListViewState<List<Beer>>>(BeersListViewState.Loading)
    val beerListViewState: StateFlow<BeersListViewState<List<Beer>>>
        get() = _beerListViewState

    fun getAllBeers(quantity: Int = MAX_PAGES_FOR_PAGINATION) {
        _beerListViewState.value = BeersListViewState.Loading
        viewModelScope.launch(Dispatchers.IO) {
            getAllBeersUseCase.execute(getAllBeersUseCase.Params(quantity)).collect {
                process(it)
            }
        }
    }

    private fun process(result: Either<Exception, List<Beer>>) {
        result.either(
            {
                _beerListViewState.value = BeersListViewState.Error(it)
            },
            {
                if (it.isEmpty()) _beerListViewState.value = BeersListViewState.EmptyState
                else _beerListViewState.value = BeersListViewState.Success(it)
            }
        )
    }

    fun showEmptyState() {
        _beerListViewState.value = BeersListViewState.EmptyState
    }
}

sealed class BeersListViewState<out T> {
    data class Success<out T>(val result: T) : BeersListViewState<T>()
    data class Error<out T>(val result: Exception) : BeersListViewState<T>()
    object Loading : BeersListViewState<Nothing>()
    object EmptyState : BeersListViewState<Nothing>()
}