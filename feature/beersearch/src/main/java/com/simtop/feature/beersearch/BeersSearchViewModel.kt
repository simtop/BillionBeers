package com.simtop.feature.beersearch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simtop.beerdomain.domain.errors.FetchBeersError
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.beerdomain.domain.models.BeersQuery
import com.simtop.beerdomain.domain.repositories.BeersPagerFactory
import com.simtop.core.core.CommonUiState
import com.simtop.core.core.CoroutineDispatcherProvider
import com.simtop.core.core.Pager
import com.simtop.core.core.PagingEvent
import com.simtop.core.core.PagingState
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch

/**
 * Search-as-you-type over the beers API. Each debounced term gets a *fresh* pager
 * ([BeersPagerFactory.create] with a [BeersQuery]); [transformLatest] cancels the previous term's
 * collection (and its in-flight first-page load) the moment a newer term arrives, so a slow stale
 * response can never overwrite results for what the user is now typing - the crux of a correct
 * as-you-type search.
 *
 * Results are in-memory only (the pager's storage is), so they die with the screen and a new query
 * invalidates instantly. The catalog's Room cache is never touched.
 */
@ContributesIntoMap(AppScope::class)
@ViewModelKey(BeersSearchViewModel::class)
@Inject
class BeersSearchViewModel(
  private val coroutineDispatcher: CoroutineDispatcherProvider,
  private val beersPagerFactory: BeersPagerFactory,
) : ViewModel() {

  private val queryText = MutableStateFlow("")

  // The pager backing whatever term is on screen now, so scroll/retry act on it. Written on Main
  // (short-query reset) and IO (each term's searchFlow), read on IO (scroll/retry) - volatile for
  // visibility. Atomicity isn't needed: a scroll lands long after the pager is set.
  @Volatile private var currentPager: Pager<Beer, FetchBeersError>? = null

  private val _events = Channel<BeersSearchEvent>(Channel.BUFFERED)
  val events: Flow<BeersSearchEvent> = _events.receiveAsFlow()

  @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
  val viewState: StateFlow<CommonUiState<BeersSearchUiModel>> =
    queryText
      .map { it.trim() }
      .debounce(DEBOUNCE_MILLIS)
      .distinctUntilChanged()
      .transformLatest { term ->
        if (term.length < MIN_QUERY_LENGTH) {
          currentPager = null
          emit(CommonUiState.Empty) // pre-search prompt
        } else {
          emitAll(searchFlow(term))
        }
      }
      .stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MILLIS),
        CommonUiState.Empty,
      )

  private fun searchFlow(term: String): Flow<CommonUiState<BeersSearchUiModel>> =
    channelFlow {
        val pager = beersPagerFactory.create(BeersQuery(term))
        currentPager = pager
        var lastTotalCount: Int? = null

        launch { pager.loadFirstPage() }
        launch {
          pager.events.collect { event ->
            when (event) {
              is PagingEvent.LoadMoreFailed -> _events.trySend(BeersSearchEvent.ShowLoadMoreError)
            }
          }
        }
        combine(pager.data, pager.pagingState) { beers, state ->
            if (state is PagingState.Success) state.totalCount?.let { lastTotalCount = it }
            reduce(beers, state, lastTotalCount)
          }
          .collect { send(it) }
      }
      .flowOn(coroutineDispatcher.io)

  private fun reduce(
    beers: List<Beer>,
    state: PagingState<FetchBeersError>,
    totalCount: Int?,
  ): CommonUiState<BeersSearchUiModel> =
    when (state) {
      is PagingState.Error ->
        if (state.isFirstPage) {
          CommonUiState.Error(state.error.toUiMessage())
        } else {
          success(beers, totalCount, SearchFooter.Retry)
        }
      is PagingState.Loading,
      PagingState.Idle -> if (beers.isEmpty()) CommonUiState.Loading else success(beers, totalCount)
      is PagingState.LoadingNextPage -> success(beers, totalCount, isLoadingNextPage = true)
      is PagingState.Success -> success(beers, totalCount)
      PagingState.EndOfPagination ->
        success(
          beers,
          totalCount,
          if (beers.isEmpty()) SearchFooter.Hidden else SearchFooter.EndReached,
        )
    }

  private fun success(
    beers: List<Beer>,
    totalCount: Int?,
    footer: SearchFooter = SearchFooter.Hidden,
    isLoadingNextPage: Boolean = false,
  ) =
    CommonUiState.Success(
      BeersSearchUiModel(
        beers = beers,
        resultCount = totalCount,
        isLoadingNextPage = isLoadingNextPage,
        footer = if (beers.isEmpty()) SearchFooter.Hidden else footer,
      )
    )

  private fun FetchBeersError.toUiMessage(): String =
    when (this) {
      FetchBeersError.Network -> "No internet connection"
      FetchBeersError.NotFound -> "No beers found"
      FetchBeersError.Forbidden -> "Access denied"
      FetchBeersError.RateLimited -> "Too many requests. Please wait a moment."
      is FetchBeersError.Unknown -> cause.message ?: "Search failed"
    }

  fun onQueryChange(text: String) {
    queryText.value = text
  }

  fun onScrollToBottom() {
    viewModelScope.launch(coroutineDispatcher.io) { currentPager?.loadNextPage() }
  }

  fun onRetryLoadMore() {
    viewModelScope.launch(coroutineDispatcher.io) { currentPager?.loadNextPage() }
  }

  /**
   * Full-screen error retry: re-runs the current term's first page (a re-typed same query would be
   * swallowed by distinctUntilChanged, so it can't drive this).
   */
  fun onRetrySearch() {
    viewModelScope.launch(coroutineDispatcher.io) { currentPager?.loadFirstPage() }
  }

  private companion object {
    const val DEBOUNCE_MILLIS = 700L
    const val MIN_QUERY_LENGTH = 2
    const val SUBSCRIPTION_TIMEOUT_MILLIS = 5_000L
  }
}

/** The bottom-of-list affordance for search results: nothing, a retry row, or the end caption. */
sealed interface SearchFooter {
  data object Hidden : SearchFooter

  data object Retry : SearchFooter

  data object EndReached : SearchFooter
}

/** One-shot effects the search screen consumes once. */
sealed interface BeersSearchEvent {
  data object ShowLoadMoreError : BeersSearchEvent
}

data class BeersSearchUiModel(
  val beers: List<Beer>,
  // X-Total-Count for "N results"; null until the server reports it.
  val resultCount: Int?,
  val isLoadingNextPage: Boolean = false,
  val footer: SearchFooter = SearchFooter.Hidden,
)
