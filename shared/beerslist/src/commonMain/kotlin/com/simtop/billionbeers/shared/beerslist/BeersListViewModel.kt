package com.simtop.billionbeers.shared.beerslist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simtop.beerdomain.domain.errors.FetchBeersError
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.beerdomain.domain.models.CatalogCacheStatus
import com.simtop.beerdomain.domain.repositories.BeersPagerFactory
import com.simtop.beerdomain.domain.repositories.BeersRepository
import com.simtop.core.core.CommonUiErrorKey
import com.simtop.core.core.CommonUiState
import com.simtop.core.core.PagedListReducer
import com.simtop.core.core.PagedListUiModel
import com.simtop.core.core.PagingEvent
import com.simtop.core.core.PagingState
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

open class BeersListViewModel(
  private val beersRepository: BeersRepository,
  beersPagerFactory: BeersPagerFactory,
) : ViewModel() {

  // Created here so paging state lives and dies with this screen; the app-scoped repository stays a
  // stateless data accessor.
  private val pager = beersPagerFactory.create()

  private val reducer = PagedListReducer<Beer, FetchBeersError>(errorState = { it.toErrorState() })

  val beerListViewState: StateFlow<CommonUiState<PagedListUiModel<Beer>>> =
    combine(pager.data, pager.pagingState, reducer::reduce)
      .stateIn(viewModelScope, SharingStarted.Eagerly, CommonUiState.Loading)

  private val _events = Channel<BeersListEvent>(Channel.BUFFERED)
  val events: Flow<BeersListEvent> = _events.receiveAsFlow()

  init {
    observeEvents()
    observeRefreshFailures()
    loadFirstPageUnlessCacheIsFresh()
  }

  private fun observeEvents() {
    pager.events
      .onEach { event ->
        when (event) {
          is PagingEvent.LoadMoreFailed -> _events.trySend(BeersListEvent.ShowLoadMoreError)
        }
      }
      .launchIn(viewModelScope)
  }

  private fun observeRefreshFailures() {
    pager.pagingState
      .onEach { pagingState ->
        if (
          pagingState is PagingState.Error &&
            pagingState.isFirstPage &&
            beerListViewState.value is CommonUiState.Success
        ) {
          _events.trySend(BeersListEvent.ShowRefreshError)
        }
      }
      .launchIn(viewModelScope)
  }

  private fun loadFirstPageUnlessCacheIsFresh() {
    viewModelScope.launch {
      if (beersRepository.catalogCacheStatus() != CatalogCacheStatus.Fresh) {
        pager.loadFirstPage()
      }
    }
  }

  fun onScrollToBottom() {
    viewModelScope.launch { pager.loadNextPage() }
  }

  fun onRetryLoadMore() {
    viewModelScope.launch { pager.loadNextPage() }
  }

  fun refresh() {
    viewModelScope.launch { pager.loadFirstPage() }
  }
}

sealed interface BeersListEvent {
  data object ShowLoadMoreError : BeersListEvent

  data object ShowRefreshError : BeersListEvent
}

private fun FetchBeersError.toErrorState(): CommonUiState.Error =
  when (this) {
    FetchBeersError.Network -> CommonUiState.Error(errorKey = CommonUiErrorKey.NoInternet)
    FetchBeersError.NotFound -> CommonUiState.Error(errorKey = CommonUiErrorKey.NoBeersFound)
    FetchBeersError.Forbidden -> CommonUiState.Error(errorKey = CommonUiErrorKey.AccessDenied)
    FetchBeersError.RateLimited -> CommonUiState.Error(errorKey = CommonUiErrorKey.RateLimited)
    is FetchBeersError.Unknown ->
      cause.message?.let { CommonUiState.Error(message = it) }
        ?: CommonUiState.Error(errorKey = CommonUiErrorKey.FailedToLoadBeers)
  }
