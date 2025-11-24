package com.simtop.feature.beerslist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.beerdomain.domain.usecases.GetAllBeersUseCase
import com.simtop.beerdomain.domain.usecases.LoadNextPageUseCase
import com.simtop.beerdomain.domain.usecases.ObservePagingStateUseCase
import com.simtop.core.core.CoroutineDispatcherProvider
import com.simtop.core.core.PagingState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BeersListViewModel @Inject constructor(
    private val coroutineDispatcher: CoroutineDispatcherProvider,
    private val getAllBeersUseCase: GetAllBeersUseCase,
    private val observePagingStateUseCase: ObservePagingStateUseCase,
    private val loadNextPageUseCase: LoadNextPageUseCase
) : ViewModel() {

    private val _beerListViewState = MutableStateFlow<BeersListViewState>(BeersListViewState.Loading)
    val beerListViewState: StateFlow<BeersListViewState> = _beerListViewState.asStateFlow()

    init {
        getAllBeers()
        observePaging()
    }

    fun getAllBeers(quantity: Int = 1) {
        getAllBeersUseCase.execute(GetAllBeersUseCase.Params(quantity))
            .onEach { beers ->
                if (beers.isEmpty()) {
                     // If DB is empty, we might be loading or empty state
                     // We rely on PagingState to tell us if we are loading
                } else {
                    val currentState = _beerListViewState.value
                    // Preserve isLoadingNextPage flag when updating the list
                    val isLoadingNextPage = if (currentState is BeersListViewState.Success) {
                        currentState.isLoadingNextPage
                    } else {
                        false
                    }
                    _beerListViewState.value = BeersListViewState.Success(
                        result = beers,
                        isLoadingNextPage = isLoadingNextPage
                    )
                }
            }
            .launchIn(viewModelScope)
    }
    
    private fun observePaging() {
        observePagingStateUseCase.execute()
            .onEach { pagingState ->
                val currentState = _beerListViewState.value
                when (pagingState) {
                    is PagingState.Loading -> {
                        if (currentState !is BeersListViewState.Success) {
                            _beerListViewState.value = BeersListViewState.Loading
                        }
                    }
                    is PagingState.LoadingNextPage -> {
                        if (currentState is BeersListViewState.Success) {
                            _beerListViewState.value = currentState.copy(isLoadingNextPage = true)
                        }
                    }
                    is PagingState.Success -> {
                        if (currentState is BeersListViewState.Success) {
                            _beerListViewState.value = currentState.copy(isLoadingNextPage = false)
                        }
                    }
                    is PagingState.Error -> {
                         if (currentState !is BeersListViewState.Success) {
                            _beerListViewState.value = BeersListViewState.Error(pagingState.message)
                        } else {
                            // Show error as a toast or snackbar event? 
                            // For now, we update state but maybe we should use a separate event flow
                            _beerListViewState.value = currentState.copy(isLoadingNextPage = false)
                        }
                    }
                    is PagingState.EndOfPagination -> {
                        if (currentState is BeersListViewState.Success) {
                            _beerListViewState.value = currentState.copy(isLoadingNextPage = false)
                        }
                    }
                    else -> {}
                }
            }
            .launchIn(viewModelScope)
    }

    fun onScrollToBottom() {
        viewModelScope.launch(coroutineDispatcher.io) {
            loadNextPageUseCase.execute()
        }
    }

    fun showEmptyState() {
        _beerListViewState.value = BeersListViewState.EmptyState
    }

    fun setProgress(showDialog: Boolean, progress: Float) {
        val currentState = _beerListViewState.value
        if (currentState is BeersListViewState.Success) {
            _beerListViewState.value = currentState.copy(
                showDialog = showDialog,
                progress = progress
            )
        }
    }
}

sealed class BeersListViewState {
    data class Success(
        val result: List<Beer>,
        val showDialog: Boolean = false,
        val progress: Float = 0.0f,
        val isLoadingNextPage: Boolean = false
    ) : BeersListViewState()

    data class Error(val result: String?) : BeersListViewState()
    object Loading : BeersListViewState()
    object EmptyState : BeersListViewState()
}