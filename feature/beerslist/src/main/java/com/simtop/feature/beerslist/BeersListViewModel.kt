package com.simtop.feature.beerslist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.beerdomain.domain.usecases.GetAllBeersUseCase
import com.simtop.beerdomain.domain.usecases.LoadNextPageUseCase
import com.simtop.beerdomain.domain.usecases.ObservePagingStateUseCase
import com.simtop.core.core.CoroutineDispatcherProvider
import com.simtop.core.core.PagingHandler
import com.simtop.core.core.PagingState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

import com.simtop.core.core.CommonUiState

@HiltViewModel
class BeersListViewModel @Inject constructor(
    private val coroutineDispatcher: CoroutineDispatcherProvider,
    private val getAllBeersUseCase: GetAllBeersUseCase,
    private val observePagingStateUseCase: ObservePagingStateUseCase,
    private val loadNextPageUseCase: LoadNextPageUseCase
) : ViewModel() {

    private val _beerListViewState = MutableStateFlow<CommonUiState<BeersListUiModel>>(CommonUiState.Loading)
    val beerListViewState: StateFlow<CommonUiState<BeersListUiModel>> = _beerListViewState.asStateFlow()

    private val pagingHandler = PagingHandler(_beerListViewState) { currentState, pagingState ->
        if (currentState is CommonUiState.Success) {
            val currentUiModel = currentState.data
            when (pagingState) {
                is PagingState.LoadingNextPage -> {
                    CommonUiState.Success(currentUiModel.copy(isLoadingNextPage = true))
                }
                is PagingState.Success -> {
                    CommonUiState.Success(currentUiModel.copy(isLoadingNextPage = false))
                }
                is PagingState.Error -> {
                    // For pagination error, we might want to show a snackbar but keep the data
                    // For now, just stop loading
                    CommonUiState.Success(currentUiModel.copy(isLoadingNextPage = false))
                }
                is PagingState.EndOfPagination -> {
                    CommonUiState.Success(currentUiModel.copy(isLoadingNextPage = false))
                }
                else -> currentState
            }
        } else {
             when (pagingState) {
                 is PagingState.Loading -> CommonUiState.Loading
                 is PagingState.Error -> CommonUiState.Error(pagingState.message)
                 else -> currentState
             }
        }
    }

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
                    val isLoadingNextPage = if (currentState is CommonUiState.Success) {
                        currentState.data.isLoadingNextPage
                    } else {
                        false
                    }
                    _beerListViewState.value = CommonUiState.Success(
                        BeersListUiModel(
                            beers = beers,
                            isLoadingNextPage = isLoadingNextPage
                        )
                    )
                }
            }
            .launchIn(viewModelScope)
    }
    
    private fun observePaging() {
        observePagingStateUseCase.execute()
            .onEach { pagingState ->
                pagingHandler.handlePagingState(pagingState)
            }
            .launchIn(viewModelScope)
    }

    fun onScrollToBottom() {
        viewModelScope.launch(coroutineDispatcher.io) {
            loadNextPageUseCase.execute()
        }
    }

    fun showEmptyState() {
        _beerListViewState.value = CommonUiState.Empty
    }

    fun setProgress(showDialog: Boolean, progress: Float) {
        val currentState = _beerListViewState.value
        if (currentState is CommonUiState.Success) {
            _beerListViewState.value = CommonUiState.Success(
                currentState.data.copy(
                    showDialog = showDialog,
                    progress = progress
                )
            )
        }
    }
}

data class BeersListUiModel(
    val beers: List<Beer> = emptyList(),
    val showDialog: Boolean = false,
    val progress: Float = 0.0f,
    val isLoadingNextPage: Boolean = false
)