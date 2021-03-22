package com.simtop.feature.beerslist

import androidx.hilt.lifecycle.ViewModelInject
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.beerdomain.domain.usecases.GetAllBeersUseCase
import kotlinx.coroutines.launch

class BeersListViewModel @ViewModelInject constructor(
    private val coroutineDispatcher: com.simtop.core.core.CoroutineDispatcherProvider,
    private val getAllBeersUseCase: GetAllBeersUseCase
) : ViewModel() {

    private val _beerListViewState =
            MutableLiveData<BeersListViewState<List<Beer>>>()
    val beerListViewState: LiveData<BeersListViewState<List<Beer>>>
        get() = _beerListViewState

    init {
        getAllBeers()
    }

    fun getAllBeers(quantity: Int = com.simtop.core.core.MAX_PAGES_FOR_PAGINATION) {
        _beerListViewState.postValue(BeersListViewState.Loading)
        viewModelScope.launch(coroutineDispatcher.io) {
            getAllBeersUseCase.execute(getAllBeersUseCase.Params(quantity))
                    .also(::process)
        }
    }

    private fun process(result: com.simtop.core.core.Either<Exception, List<Beer>>) {
        result.either(
                {
                    _beerListViewState.postValue(BeersListViewState.Error(it.message))
                },
                {
                    if (it.isEmpty()) _beerListViewState.postValue(BeersListViewState.EmptyState)
                    else _beerListViewState.postValue(BeersListViewState.Success(it))
                }
        )
    }

    fun showEmptyState() {
        _beerListViewState.postValue(BeersListViewState.EmptyState)
    }
}

sealed class BeersListViewState<out T> {
    data class Success<out T>(val result: T) : BeersListViewState<T>()
    data class Error(val result: String?) : BeersListViewState<Nothing>()
    object Loading : BeersListViewState<Nothing>()
    object EmptyState : BeersListViewState<Nothing>()
}