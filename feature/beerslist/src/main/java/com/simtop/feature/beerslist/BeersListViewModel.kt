package com.simtop.feature.beerslist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simtop.beerdomain.domain.errors.FetchBeersError
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.beerdomain.domain.models.CatalogCacheStatus
import com.simtop.beerdomain.domain.repositories.BeersPagerFactory
import com.simtop.beerdomain.domain.repositories.BeersRepository
import com.simtop.core.core.CachePolicy
import com.simtop.core.core.CommonUiState
import com.simtop.core.core.PagedListReducer
import com.simtop.core.core.PagedListUiModel
import com.simtop.core.core.PagingEvent
import com.simtop.core.core.PagingState
import com.simtop.presentation_utils.core.toErrorState
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
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

@ContributesIntoMap(AppScope::class)
@ViewModelKey(BeersListViewModel::class)
@Inject
class BeersListViewModel(
  private val beersRepository: BeersRepository,
  beersPagerFactory: BeersPagerFactory,
) : ViewModel() {

  // Created here (not injected as an instance) so paging state lives and dies with this screen;
  // the app-scoped repository stays a stateless data accessor.
  private val pager = beersPagerFactory.create()

  private val reducer = PagedListReducer<Beer, FetchBeersError>(errorState = { it.toErrorState() })

  // Eagerly: the screen state has always been hot (it previously accumulated in a MutableStateFlow
  // from init), and the refresh-failure check below reads the current value between subscribers.
  val beerListViewState: StateFlow<CommonUiState<PagedListUiModel<Beer>>> =
    combine(pager.data, pager.pagingState, reducer::reduce)
      .stateIn(viewModelScope, SharingStarted.Eagerly, CommonUiState.Loading)

  // One-shot UI effects (transient toasts). Channel-backed so a config change re-collecting the
  // flow doesn't replay a stale toast; mirrors the BeerDetail events pattern.
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
        // A first-page failure with a list already on screen is a failed refresh: the reducer
        // keeps the list, so the user's only feedback is this one-shot toast.
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

  /**
   * The cache policy at work: cached rows always show immediately, and anything non-fresh also
   * triggers a first-page load. Empty is the cold start (full-screen Loading, as before); Stale and
   * LanguageMismatch load *behind* the visible list - the reducer turns that into the refresh
   * spinner, the upsert keeps the list stable under it, and a failure degrades to the existing
   * one-shot refresh-failed toast.
   */
  private fun loadFirstPageUnlessCacheIsFresh() {
    viewModelScope.launch {
      if (beersRepository.catalogCacheStatus(CACHE_POLICY) != CatalogCacheStatus.Fresh) {
        pager.loadFirstPage()
      }
    }
  }

  fun onScrollToBottom() {
    viewModelScope.launch { pager.loadNextPage() }
  }

  /** Footer retry tap: reloads the failed next page. Retry is free - the key never advanced. */
  fun onRetryLoadMore() {
    viewModelScope.launch { pager.loadNextPage() }
  }

  fun refresh() {
    viewModelScope.launch { pager.loadFirstPage() }
  }

  private companion object {
    // The default 24h TTL; becomes an injected/screen-specific value only when a second surface
    // wants a different one.
    val CACHE_POLICY = CachePolicy()
  }
}

/** One-shot effects the screen consumes exactly once. */
sealed interface BeersListEvent {
  data object ShowLoadMoreError : BeersListEvent

  data object ShowRefreshError : BeersListEvent
}
