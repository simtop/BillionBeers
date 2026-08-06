package com.simtop.feature.beerbrowse.presentation

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
import com.simtop.feature.beerbrowse.presentation.di.FeatureBrowseScope
import com.simtop.presentation_utils.core.toErrorState
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The beers matching one browse selection (a style or a brewery), paged with the same machinery as
 * the catalog and search: a per-query in-memory pager from [BeersPagerFactory] reduced by the
 * shared [PagedListReducer]. One ViewModel per selection ([query] is assisted), so switching
 * selections is a new pager, not a mutation - the same invalidation rule every query surface
 * follows.
 */
class BrowseBeersViewModel
@AssistedInject
constructor(
  private val coroutineDispatcher: CoroutineDispatcherProvider,
  beersPagerFactory: BeersPagerFactory,
  @Assisted query: BeersQuery,
) : ViewModel() {

  @AssistedFactory
  @ManualViewModelAssistedFactoryKey(Factory::class)
  @ContributesIntoMap(FeatureBrowseScope::class)
  interface Factory : ManualViewModelAssistedFactory {
    fun create(@Assisted query: BeersQuery): BrowseBeersViewModel
  }

  private val pager = beersPagerFactory.create(query)

  // Ended empty is a Success with no items (the "no beers here" hint), not the generic Empty.
  private val reducer =
    PagedListReducer<Beer, FetchBeersError>(
      errorState = { it.toErrorState() },
      endedEmpty = { CommonUiState.Success(PagedListUiModel()) },
    )

  private val _events = Channel<BrowseBeersEvent>(Channel.BUFFERED)
  val events: Flow<BrowseBeersEvent> = _events.receiveAsFlow()

  val viewState: StateFlow<CommonUiState<PagedListUiModel<Beer>>> =
    combine(pager.data, pager.pagingState, reducer::reduce)
      .flowOn(coroutineDispatcher.io)
      .stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MILLIS),
        CommonUiState.Loading,
      )

  init {
    viewModelScope.launch(coroutineDispatcher.io) { pager.loadFirstPage() }
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
        // A first-page failure with a list already on screen is a failed refresh: the reducer keeps
        // the list, so the user's only feedback is this one-shot toast.
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
    viewModelScope.launch(coroutineDispatcher.io) { pager.loadNextPage() }
  }

  fun onRetryLoadMore() {
    viewModelScope.launch(coroutineDispatcher.io) { pager.loadNextPage() }
  }

  /**
   * Re-runs the first page of this selection, for both callers that want one: the full-screen error
   * retry, and pull-to-refresh. With a list already on screen the reducer turns this into the
   * refresh spinner and keeps the items under it; a failure degrades to the one-shot toast.
   */
  fun onRetryFirstPage() {
    viewModelScope.launch(coroutineDispatcher.io) { pager.loadFirstPage() }
  }

  private companion object {
    const val SUBSCRIPTION_TIMEOUT_MILLIS = 5_000L
  }
}

/** One-shot effects the browse beers screen consumes once. */
sealed interface BrowseBeersEvent {
  data object ShowLoadMoreError : BrowseBeersEvent

  data object ShowRefreshError : BrowseBeersEvent
}
