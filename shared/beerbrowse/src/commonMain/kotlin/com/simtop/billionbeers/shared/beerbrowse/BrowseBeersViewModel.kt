package com.simtop.billionbeers.shared.beerbrowse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simtop.beerdomain.domain.errors.FetchBeersError
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.beerdomain.domain.models.BeersQuery
import com.simtop.beerdomain.domain.repositories.BeersPagerFactory
import com.simtop.core.core.CommonUiState
import com.simtop.core.core.CoroutineDispatcherProvider
import com.simtop.core.core.PagedListReducer
import com.simtop.core.core.PagedListUiModel
import com.simtop.core.core.PagingEvent
import com.simtop.core.core.PagingState
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

open class BrowseBeersViewModel(
  private val coroutineDispatcher: CoroutineDispatcherProvider,
  beersPagerFactory: BeersPagerFactory,
  query: BeersQuery,
) : ViewModel() {

  private val pager = beersPagerFactory.create(query)

  private val reducer =
    PagedListReducer<Beer, FetchBeersError>(
      errorState = { CommonUiState.Error(errorKey = it.toErrorKey()) },
      endedEmpty = { CommonUiState.Success(PagedListUiModel()) },
    )

  private val _events = Channel<BrowseBeersEvent>(Channel.BUFFERED)
  val events: Flow<BrowseBeersEvent> = _events.receiveAsFlow()

  val viewState: StateFlow<CommonUiState<PagedListUiModel<Beer>>> =
    combine(pager.data, pager.pagingState, reducer::reduce)
      .flowOn(coroutineDispatcher.default)
      .stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MILLIS),
        CommonUiState.Loading,
      )

  init {
    viewModelScope.launch { pager.loadFirstPage() }
    viewModelScope.launch {
      pager.events.collect { event ->
        when (event) {
          is PagingEvent.LoadMoreFailed -> _events.trySend(BrowseBeersEvent.ShowLoadMoreError)
        }
      }
    }
    observeRefreshFailures()
  }

  private fun observeRefreshFailures() {
    viewModelScope.launch {
      pager.pagingState.collect { pagingState ->
        if (
          pagingState is PagingState.Error &&
            pagingState.isFirstPage &&
            viewState.value is CommonUiState.Success
        ) {
          _events.trySend(BrowseBeersEvent.ShowRefreshError)
        }
      }
    }
  }

  fun onScrollToBottom() {
    viewModelScope.launch { pager.loadNextPage() }
  }

  fun onRetryLoadMore() {
    viewModelScope.launch { pager.loadNextPage() }
  }

  fun onRetryFirstPage() {
    viewModelScope.launch { pager.loadFirstPage() }
  }

  private companion object {
    const val SUBSCRIPTION_TIMEOUT_MILLIS = 5_000L
  }
}

sealed interface BrowseBeersEvent {
  data object ShowLoadMoreError : BrowseBeersEvent

  data object ShowRefreshError : BrowseBeersEvent
}
