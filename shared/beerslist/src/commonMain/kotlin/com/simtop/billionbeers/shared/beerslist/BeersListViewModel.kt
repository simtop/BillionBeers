package com.simtop.billionbeers.shared.beerslist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simtop.beerdomain.domain.errors.FetchBeersError
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.beerdomain.domain.models.BeersQuery
import com.simtop.beerdomain.domain.models.CatalogCacheStatus
import com.simtop.beerdomain.domain.repositories.BeersPagerFactory
import com.simtop.beerdomain.domain.repositories.BeersRepository
import com.simtop.billionbeers.shared.presentation.toCommonUiErrorState
import com.simtop.core.core.CommonUiErrorKey
import com.simtop.core.core.CommonUiState
import com.simtop.core.core.PagedListReducer
import com.simtop.core.core.PagedListUiModel
import com.simtop.core.core.Pager
import com.simtop.core.core.PagingEvent
import com.simtop.core.core.PagingState
import com.simtop.core.core.retryWithErrorState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

open class BeersListViewModel(
  private val beersRepository: BeersRepository,
  private val beersPagerFactory: BeersPagerFactory,
) : ViewModel() {

  // Created here so paging state lives and dies with this screen; the app-scoped repository stays a
  // stateless data accessor.
  private val pager = beersPagerFactory.create()

  private val reducer =
    PagedListReducer<Beer, FetchBeersError>(errorState = { it.toCommonUiErrorState() })

  private val observedBeers: Flow<CatalogBeersObservation> =
    pager.data
      .map { CatalogBeersObservation.Items(it) }
      .retryWithErrorState { CatalogBeersObservation.Failed }

  private val catalogState: StateFlow<CommonUiState<PagedListUiModel<Beer>>> =
    combine(observedBeers, pager.pagingState) { observation, pagingState ->
        when (observation) {
          is CatalogBeersObservation.Items -> reducer.reduce(observation.items, pagingState)
          CatalogBeersObservation.Failed ->
            CommonUiState.Error(errorKey = CommonUiErrorKey.FailedToLoadBeers)
        }
      }
      .stateIn(viewModelScope, SharingStarted.Eagerly, CommonUiState.Loading)

  private val _events = Channel<BeersListEvent>(Channel.BUFFERED)
  val events: Flow<BeersListEvent> = _events.receiveAsFlow()

  private val _query = MutableStateFlow("")
  val query: StateFlow<String> = _query.asStateFlow()
  private val _activeQuery = MutableStateFlow(BeersQuery())
  val activeQuery: StateFlow<BeersQuery> = _activeQuery.asStateFlow()
  private var queryJob: Job? = null
  private var searchPager: Pager<Beer, FetchBeersError>? = null

  @OptIn(ExperimentalCoroutinesApi::class)
  private val searchState =
    activeQuery
      .flatMapLatest { criteria ->
        if (criteria.search == null) flowOf(SearchResult(criteria, CommonUiState.Empty))
        else searchFlow(criteria)
      }
      .stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        SearchResult(BeersQuery(), CommonUiState.Empty),
      )

  val beerListViewState: StateFlow<CommonUiState<PagedListUiModel<Beer>>> =
    combine(catalogState, activeQuery, searchState) { catalog, criteria, result ->
        when {
          criteria.search == null -> catalog
          result.query != criteria -> CommonUiState.Loading
          else -> result.state
        }
      }
      .stateIn(viewModelScope, SharingStarted.Eagerly, CommonUiState.Loading)

  fun onQueryChange(text: String) {
    _query.value = text
    queryJob?.cancel()
    if (text.trim().length < MIN_QUERY_LENGTH) {
      applyQuery(BeersQuery())
    } else {
      queryJob = viewModelScope.launch {
        delay(SEARCH_DEBOUNCE_MILLIS)
        applyQuery(requestedQuery)
      }
    }
  }

  fun submitSearch() {
    queryJob?.cancel()
    applyQuery(requestedQuery)
  }

  private val requestedQuery: BeersQuery
    get() = BeersQuery(search = query.value.trim().takeIf { it.length >= MIN_QUERY_LENGTH })

  private fun applyQuery(criteria: BeersQuery) {
    if (criteria != activeQuery.value) {
      searchPager = null
      _activeQuery.value = criteria
    }
  }

  private fun searchFlow(criteria: BeersQuery): Flow<SearchResult> = channelFlow {
    val current = beersPagerFactory.create(criteria)
    searchPager = current
    val searchReducer =
      PagedListReducer<Beer, FetchBeersError>(errorState = { it.toCommonUiErrorState() })
    launch { current.loadFirstPage() }
    launch {
      current.events.collect { event ->
        if (event is PagingEvent.LoadMoreFailed) _events.trySend(BeersListEvent.ShowLoadMoreError)
      }
    }
    val observation: Flow<CatalogBeersObservation> =
      current.data
        .map { CatalogBeersObservation.Items(it) }
        .retryWithErrorState { CatalogBeersObservation.Failed }
    val paging =
      current.pagingState.onEach { state ->
        val wasShowingResults =
          searchState.value.query == criteria && searchState.value.state is CommonUiState.Success
        if (state is PagingState.Error && state.isFirstPage && wasShowingResults) {
          _events.trySend(BeersListEvent.ShowRefreshError)
        }
      }
    combine(observation, paging) { data, state ->
        when (data) {
          is CatalogBeersObservation.Items -> searchReducer.reduce(data.items, state)
          CatalogBeersObservation.Failed ->
            CommonUiState.Error(errorKey = CommonUiErrorKey.FailedToLoadBeers)
        }
      }
      .collect { send(SearchResult(criteria, it)) }
  }

  private val currentPager: Pager<Beer, FetchBeersError>?
    get() = if (activeQuery.value.search == null) pager else searchPager

  init {
    observeEvents()
    observeRefreshFailures()
    loadFirstPageUnlessCacheIsFresh()
  }

  private fun observeEvents() {
    pager.events
      .onEach { event ->
        when (event) {
          is PagingEvent.LoadMoreFailed ->
            if (activeQuery.value.search == null) _events.trySend(BeersListEvent.ShowLoadMoreError)
        }
      }
      .launchIn(viewModelScope)
  }

  private fun observeRefreshFailures() {
    pager.pagingState
      .onEach { pagingState ->
        val wasShowingCatalog =
          activeQuery.value.search == null && catalogState.value is CommonUiState.Success
        if (pagingState is PagingState.Error && pagingState.isFirstPage && wasShowingCatalog) {
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
    viewModelScope.launch { currentPager?.loadNextPage() }
  }

  fun onRetryLoadMore() {
    viewModelScope.launch { currentPager?.loadNextPage() }
  }

  fun refresh() {
    viewModelScope.launch { currentPager?.loadFirstPage() }
  }

  private companion object {
    const val SEARCH_DEBOUNCE_MILLIS = 700L
    const val MIN_QUERY_LENGTH = 2
  }
}

private sealed interface CatalogBeersObservation {
  data class Items(val items: List<Beer>) : CatalogBeersObservation

  data object Failed : CatalogBeersObservation
}

sealed interface BeersListEvent {
  data object ShowLoadMoreError : BeersListEvent

  data object ShowRefreshError : BeersListEvent
}

private data class SearchResult(
  val query: BeersQuery,
  val state: CommonUiState<PagedListUiModel<Beer>>,
)
