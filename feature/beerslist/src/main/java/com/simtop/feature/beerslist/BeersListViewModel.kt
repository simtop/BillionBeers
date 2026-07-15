package com.simtop.feature.beerslist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simtop.beerdomain.domain.errors.FetchBeersError
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.beerdomain.domain.repositories.BeersPagerFactory
import com.simtop.beerdomain.domain.repositories.BeersRepository
import com.simtop.core.core.CommonUiState
import com.simtop.core.core.CoroutineDispatcherProvider
import com.simtop.core.core.PagingEvent
import com.simtop.core.core.PagingHandler
import com.simtop.core.core.PagingState
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

@ContributesIntoMap(AppScope::class)
@ViewModelKey(BeersListViewModel::class)
@Inject
class BeersListViewModel(
  private val coroutineDispatcher: CoroutineDispatcherProvider,
  private val beersRepository: BeersRepository,
  beersPagerFactory: BeersPagerFactory,
) : ViewModel() {

  // Created here (not injected as an instance) so paging state lives and dies with this screen;
  // the app-scoped repository stays a stateless data accessor.
  private val pager = beersPagerFactory.create()

  // Last server-reported total (X-Total-Count). Lives here, not only in the UI model, so a beers
  // emission that arrives before/after a Success can still render "N of total".
  private var lastTotalCount: Int? = null

  private val _beerListViewState =
    MutableStateFlow<CommonUiState<BeersListUiModel>>(CommonUiState.Loading)
  val beerListViewState: StateFlow<CommonUiState<BeersListUiModel>> =
    _beerListViewState.asStateFlow()

  // One-shot UI effects (a transient toast on load-more failure). Channel-backed so a config change
  // re-collecting the flow doesn't replay a stale toast; mirrors the BeerDetail events pattern.
  private val _events = Channel<BeersListEvent>(Channel.BUFFERED)
  val events: Flow<BeersListEvent> = _events.receiveAsFlow()

  private val pagingHandler =
    PagingHandler<CommonUiState<BeersListUiModel>, FetchBeersError>(_beerListViewState) {
      currentState,
      pagingState ->
      val currentUiModel = (currentState as? CommonUiState.Success)?.data
      when (pagingState) {
        is PagingState.Loading ->
          if (currentUiModel != null) {
            // A list is already on screen, so this Loading is a refresh in progress.
            CommonUiState.Success(
              currentUiModel.copy(isRefreshing = true, footer = ListFooter.Hidden)
            )
          } else {
            CommonUiState.Loading
          }
        is PagingState.LoadingNextPage ->
          if (currentUiModel != null) {
            CommonUiState.Success(
              currentUiModel.copy(isLoadingNextPage = true, footer = ListFooter.Hidden)
            )
          } else {
            currentState
          }
        is PagingState.Success ->
          if (currentUiModel != null) {
            CommonUiState.Success(
              currentUiModel.copy(
                isLoadingNextPage = false,
                isRefreshing = false,
                footer = ListFooter.Hidden,
                totalCount = pagingState.totalCount ?: currentUiModel.totalCount,
              )
            )
          } else {
            currentState
          }
        is PagingState.EndOfPagination ->
          if (currentUiModel != null) {
            // The whole catalog is loaded: show the end-of-list caption instead of a footer
            // spinner.
            CommonUiState.Success(
              currentUiModel.copy(
                isLoadingNextPage = false,
                isRefreshing = false,
                footer = ListFooter.EndReached,
              )
            )
          } else {
            // Pagination ended with nothing on screen: the catalog is genuinely empty. Without
            // this, observeBeers (which ignores empty emissions) never resolves the state and the
            // skeleton spins forever.
            CommonUiState.Empty
          }
        is PagingState.Error ->
          if (currentUiModel != null) {
            if (pagingState.isFirstPage) {
              // A failed refresh: keep the list and stop the indicator. Feedback is the one-shot
              // refresh-error toast - the load-more footer would show the wrong copy, and pulling
              // again is the natural retry.
              CommonUiState.Success(
                currentUiModel.copy(
                  isLoadingNextPage = false,
                  isRefreshing = false,
                  footer = ListFooter.Hidden,
                )
              )
            } else {
              // A load-more failure keeps the list and offers a retry row; the transient toast is
              // driven separately off the pager's one-shot events.
              CommonUiState.Success(
                currentUiModel.copy(
                  isLoadingNextPage = false,
                  isRefreshing = false,
                  footer = ListFooter.Retry,
                )
              )
            }
          } else {
            CommonUiState.Error(pagingState.error.toUiMessage())
          }
        is PagingState.Idle -> currentState
      }
    }

  private fun FetchBeersError.toUiMessage(): String =
    when (this) {
      FetchBeersError.Network -> "No internet connection"
      FetchBeersError.NotFound -> "Beers not found"
      FetchBeersError.Forbidden -> "Access denied"
      FetchBeersError.RateLimited -> "Too many requests. Please wait a moment."
      is FetchBeersError.Unknown -> cause.message ?: "Failed to load beers"
    }

  init {
    observeBeers()
    observePaging()
    observeEvents()
    loadFirstPageIfCacheIsEmpty()
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

  private fun loadFirstPageIfCacheIsEmpty() {
    viewModelScope.launch(coroutineDispatcher.io) {
      if (beersRepository.countDBEntries() == 0) {
        pager.loadFirstPage()
      }
    }
  }

  private fun observeBeers() {
    pager.data
      .onEach { beers ->
        if (beers.isEmpty()) {
          // If DB is empty, we might be loading or empty state
          // We rely on PagingState to tell us if we are loading
        } else {
          val currentState = _beerListViewState.value
          // A fresh beers emission rebuilds the model, so carry over the transient paging flags the
          // reducer owns (loading/refreshing/footer) instead of resetting them under the new list.
          val current = (currentState as? CommonUiState.Success)?.data
          _beerListViewState.value =
            CommonUiState.Success(
              BeersListUiModel(
                beers = beers,
                isLoadingNextPage = current?.isLoadingNextPage ?: false,
                isRefreshing = current?.isRefreshing ?: false,
                footer = current?.footer ?: ListFooter.Hidden,
                totalCount = lastTotalCount,
              )
            )
        }
      }
      .launchIn(viewModelScope)
  }

  private fun observePaging() {
    pager.pagingState
      .onEach { pagingState ->
        if (pagingState is PagingState.Success) {
          pagingState.totalCount?.let { lastTotalCount = it }
        }
        // A first-page failure with a list already on screen is a failed refresh: the reducer
        // keeps the list, so the user's only feedback is this one-shot toast.
        if (
          pagingState is PagingState.Error &&
            pagingState.isFirstPage &&
            _beerListViewState.value is CommonUiState.Success
        ) {
          _events.trySend(BeersListEvent.ShowRefreshError)
        }
        pagingHandler.handlePagingState(pagingState)
      }
      .launchIn(viewModelScope)
  }

  fun onScrollToBottom() {
    viewModelScope.launch(coroutineDispatcher.io) { pager.loadNextPage() }
  }

  /** Footer retry tap: reloads the failed next page. Retry is free - the key never advanced. */
  fun onRetryLoadMore() {
    viewModelScope.launch(coroutineDispatcher.io) { pager.loadNextPage() }
  }

  fun refresh() {
    viewModelScope.launch(coroutineDispatcher.io) { pager.loadFirstPage() }
  }
}

/** The list's bottom item: nothing, a retry row after a failed "load more", or the end caption. */
sealed interface ListFooter {
  data object Hidden : ListFooter

  data object Retry : ListFooter

  data object EndReached : ListFooter
}

/** One-shot effects the screen consumes exactly once. */
sealed interface BeersListEvent {
  data object ShowLoadMoreError : BeersListEvent

  data object ShowRefreshError : BeersListEvent
}

data class BeersListUiModel(
  val beers: List<Beer> = emptyList(),
  val isLoadingNextPage: Boolean = false,
  val isRefreshing: Boolean = false,
  val footer: ListFooter = ListFooter.Hidden,
  // Server-reported catalog size (X-Total-Count); null until the server reports it. Renders "N of
  // M".
  val totalCount: Int? = null,
)
