package com.simtop.feature.beersearch

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.simtop.beerdomain.domain.errors.FetchBeersError
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.beerdomain.domain.models.BeersQuery
import com.simtop.beerdomain.domain.repositories.BeersPagerFactory
import com.simtop.core.core.CommonUiState
import com.simtop.core.core.CoroutineDispatcherProvider
import com.simtop.core.core.PagedListReducer
import com.simtop.core.core.PagedListUiModel
import com.simtop.core.core.Pager
import com.simtop.core.core.PagingEvent
import com.simtop.presentation_utils.core.toErrorState
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ViewModelAssistedFactoryKey
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
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
 *
 * The query lives here in the [SavedStateHandle], not in the screen: process death then restores
 * the *results* (the restored query re-runs the search), not just the text in the field.
 */
@AssistedInject
class BeersSearchViewModel(
  private val coroutineDispatcher: CoroutineDispatcherProvider,
  private val beersPagerFactory: BeersPagerFactory,
  @Assisted private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

  @AssistedFactory
  @ViewModelAssistedFactoryKey(BeersSearchViewModel::class)
  @ContributesIntoMap(AppScope::class)
  fun interface Factory : ViewModelAssistedFactory {
    override fun create(extras: CreationExtras): BeersSearchViewModel =
      create(extras.createSavedStateHandle())

    fun create(@Assisted savedStateHandle: SavedStateHandle): BeersSearchViewModel
  }

  private val queryText = savedStateHandle.getStateFlow(KEY_QUERY, "")

  /** The current query text, owned here so the screen and the search can never disagree. */
  val query: StateFlow<String> = queryText

  // The pager backing whatever term is on screen now, so scroll/retry act on it. Written on Main
  // (short-query reset) and IO (each term's searchFlow), read on IO (scroll/retry) - volatile for
  // visibility. Atomicity isn't needed: a scroll lands long after the pager is set.
  @Volatile private var currentPager: Pager<Beer, FetchBeersError>? = null

  private val _events = Channel<BeersSearchEvent>(Channel.BUFFERED)
  val events: Flow<BeersSearchEvent> = _events.receiveAsFlow()

  @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
  val viewState: StateFlow<CommonUiState<PagedListUiModel<Beer>>> =
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

  private fun searchFlow(term: String): Flow<CommonUiState<PagedListUiModel<Beer>>> =
    channelFlow {
        val pager = beersPagerFactory.create(BeersQuery(term))
        currentPager = pager
        // One reducer per term: it latches the term's own result count, and its ended-empty state
        // is a Success with no items (the "no results for X" hint) - Empty is the pre-search
        // prompt.
        val reducer =
          PagedListReducer<Beer, FetchBeersError>(
            errorState = { it.toErrorState() },
            endedEmpty = { CommonUiState.Success(PagedListUiModel()) },
          )

        launch { pager.loadFirstPage() }
        launch {
          pager.events.collect { event ->
            when (event) {
              is PagingEvent.LoadMoreFailed -> _events.trySend(BeersSearchEvent.ShowLoadMoreError)
            }
          }
        }
        combine(pager.data, pager.pagingState, reducer::reduce).collect { send(it) }
      }
      .flowOn(coroutineDispatcher.io)

  fun onQueryChange(text: String) {
    savedStateHandle[KEY_QUERY] = text
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
    const val KEY_QUERY = "search_query"
    const val DEBOUNCE_MILLIS = 700L
    const val MIN_QUERY_LENGTH = 2
    const val SUBSCRIPTION_TIMEOUT_MILLIS = 5_000L
  }
}

/** One-shot effects the search screen consumes once. */
sealed interface BeersSearchEvent {
  data object ShowLoadMoreError : BeersSearchEvent
}
