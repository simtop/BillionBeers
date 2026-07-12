package com.simtop.feature.beerslist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simtop.beerdomain.domain.errors.FetchBeersError
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.beerdomain.domain.repositories.BeersPagerFactory
import com.simtop.beerdomain.domain.repositories.BeersRepository
import com.simtop.core.core.CommonUiState
import com.simtop.core.core.CoroutineDispatcherProvider
import com.simtop.core.core.PagingHandler
import com.simtop.core.core.PagingState
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
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

  private val pagingHandler =
    PagingHandler<CommonUiState<BeersListUiModel>, FetchBeersError>(_beerListViewState) {
      currentState,
      pagingState ->
      val currentUiModel = (currentState as? CommonUiState.Success)?.data
      when (pagingState) {
        is PagingState.Loading ->
          if (currentUiModel != null) {
            // A list is already on screen, so this Loading is a refresh in progress.
            CommonUiState.Success(currentUiModel.copy(isRefreshing = true))
          } else {
            CommonUiState.Loading
          }
        is PagingState.LoadingNextPage ->
          if (currentUiModel != null) {
            CommonUiState.Success(currentUiModel.copy(isLoadingNextPage = true))
          } else {
            currentState
          }
        is PagingState.Success ->
          if (currentUiModel != null) {
            CommonUiState.Success(
              currentUiModel.copy(
                isLoadingNextPage = false,
                isRefreshing = false,
                totalCount = pagingState.totalCount ?: currentUiModel.totalCount,
              )
            )
          } else {
            currentState
          }
        is PagingState.EndOfPagination ->
          if (currentUiModel != null) {
            CommonUiState.Success(
              currentUiModel.copy(isLoadingNextPage = false, isRefreshing = false)
            )
          } else {
            currentState
          }
        is PagingState.Error ->
          if (currentUiModel != null) {
            // For pagination error, we might want to show a snackbar but keep the data
            CommonUiState.Success(
              currentUiModel.copy(isLoadingNextPage = false, isRefreshing = false)
            )
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
      is FetchBeersError.Unknown -> cause.message ?: "Failed to load beers"
    }

  init {
    observeBeers()
    observePaging()
    loadFirstPageIfCacheIsEmpty()
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
          // Preserve isLoadingNextPage flag when updating the list
          val isLoadingNextPage =
            if (currentState is CommonUiState.Success) {
              currentState.data.isLoadingNextPage
            } else {
              false
            }
          _beerListViewState.value =
            CommonUiState.Success(
              BeersListUiModel(
                beers = beers,
                isLoadingNextPage = isLoadingNextPage,
                isRefreshing =
                  if (currentState is CommonUiState.Success) currentState.data.isRefreshing
                  else false,
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
        pagingHandler.handlePagingState(pagingState)
      }
      .launchIn(viewModelScope)
  }

  fun onScrollToBottom() {
    viewModelScope.launch(coroutineDispatcher.io) { pager.loadNextPage() }
  }

  fun refresh() {
    viewModelScope.launch(coroutineDispatcher.io) { pager.loadFirstPage() }
  }
}

data class BeersListUiModel(
  val beers: List<Beer> = emptyList(),
  val isLoadingNextPage: Boolean = false,
  val isRefreshing: Boolean = false,
  // Server-reported catalog size (X-Total-Count); null until the server reports it. Renders "N of
  // M".
  val totalCount: Int? = null,
)
