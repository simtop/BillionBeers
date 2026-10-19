package com.simtop.billionbeers.shared.beersearch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simtop.beerdomain.domain.errors.FetchBeersError
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.beerdomain.domain.models.BeerStyle
import com.simtop.beerdomain.domain.models.BeersQuery
import com.simtop.beerdomain.domain.repositories.BeerCatalogMetadataRepository
import com.simtop.beerdomain.domain.repositories.BeersPagerFactory
import com.simtop.billionbeers.shared.presentation.toCommonUiErrorState
import com.simtop.core.core.CommonUiState
import com.simtop.core.core.CoroutineDispatcherProvider
import com.simtop.core.core.Either
import com.simtop.core.core.PagedListReducer
import com.simtop.core.core.PagedListUiModel
import com.simtop.core.core.Pager
import com.simtop.core.core.PagingEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch

open class BeersSearchViewModel(
  private val coroutineDispatcher: CoroutineDispatcherProvider,
  private val beersPagerFactory: BeersPagerFactory,
  private val catalogMetadataRepository: BeerCatalogMetadataRepository,
  initialQuery: String = "",
  initialStyleId: String? = null,
) : ViewModel() {

  private val queryText = MutableStateFlow(initialQuery)

  private val selectedStyleId = MutableStateFlow(initialStyleId)

  private val _styles = MutableStateFlow<CommonUiState<List<BeerStyle>>>(CommonUiState.Loading)
  private val styles: StateFlow<CommonUiState<List<BeerStyle>>> = _styles.asStateFlow()

  private val selectedStyle: StateFlow<BeerStyle?> =
    combine(selectedStyleId, styles) { id, state ->
        (state as? CommonUiState.Success)?.data?.firstOrNull { it.id == id }
      }
      .stateIn(viewModelScope, SharingStarted.Eagerly, null)

  private var currentPager: Pager<Beer, FetchBeersError>? = null

  private val _events = Channel<BeersSearchEvent>(Channel.BUFFERED)
  val uiEvents: Flow<BeersSearchEvent> = _events.receiveAsFlow()

  @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
  private val searchResults: StateFlow<SearchResults> =
    merge(
        flowOf(effectiveQuery(initialQuery, initialStyleId)),
        queryText.map(String::trim).debounce(DEBOUNCE_MILLIS).distinctUntilChanged().map { term ->
          effectiveQuery(term, selectedStyleId.value)
        },
        selectedStyleId.drop(1).map { styleId -> effectiveQuery(queryText.value, styleId) },
      )
      .distinctUntilChanged()
      .transformLatest { query ->
        if (!query.isActive()) {
          currentPager = null
          emit(SearchResults(query, CommonUiState.Empty))
        } else {
          emit(SearchResults(query, CommonUiState.Loading))
          emitAll(searchFlow(query).map { SearchResults(query, it) })
        }
      }
      .stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        SearchResults(effectiveQuery(initialQuery, initialStyleId), CommonUiState.Empty),
      )

  val uiState: StateFlow<BeersSearchUiState> =
    combine(queryText, styles, selectedStyleId, selectedStyle, searchResults) {
        queryText,
        styles,
        selectedStyleId,
        selectedStyle,
        searchResults ->
        BeersSearchUiState(
          query = queryText,
          styles = styles,
          selectedStyle = selectedStyle,
          selectedStyleId = selectedStyleId,
          activeQuery = searchResults.query,
          viewState = searchResults.state,
        )
      }
      .stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        BeersSearchUiState(
          query = initialQuery,
          selectedStyleId = initialStyleId,
          activeQuery = effectiveQuery(initialQuery, initialStyleId),
        ),
      )

  init {
    retryStyles()
  }

  open fun onQueryChange(text: String) {
    queryText.value = text
  }

  fun onStyleSelected(styleId: String?) {
    selectedStyleId.value = styleId
  }

  fun onClearStyle() {
    onStyleSelected(null)
  }

  fun onResetFilters() {
    queryText.value = ""
    selectedStyleId.value = null
  }

  fun retryStyles() {
    viewModelScope.launch {
      _styles.value = CommonUiState.Loading
      _styles.value = catalogMetadataRepository.getBeerStyles().toUiState()
    }
  }

  private fun searchFlow(query: BeersQuery): Flow<CommonUiState<PagedListUiModel<Beer>>> =
    channelFlow {
      val pager = beersPagerFactory.create(query)
      currentPager = pager
      val reducer =
        PagedListReducer<Beer, FetchBeersError>(
          errorState = { it.toCommonUiErrorState() },
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
    .flowOn(coroutineDispatcher.default)

  fun onScrollToBottom() {
    viewModelScope.launch { currentPager?.loadNextPage() }
  }

  fun onRetryLoadMore() {
    viewModelScope.launch { currentPager?.loadNextPage() }
  }

  fun onRetrySearch() {
    viewModelScope.launch { currentPager?.loadFirstPage() }
  }

  private fun effectiveQuery(text: String, styleId: String?): BeersQuery =
    BeersQuery(search = text.trim().takeIf { it.length >= MIN_QUERY_LENGTH }, styleId = styleId)

  private fun BeersQuery.isActive(): Boolean = search != null || styleId != null

  private fun <T> Either<FetchBeersError, List<T>>.toUiState(): CommonUiState<List<T>> =
    either(
      fnL = { error -> error.toCommonUiErrorState() },
      fnR = { list -> if (list.isEmpty()) CommonUiState.Empty else CommonUiState.Success(list) },
    )

  private companion object {
    const val DEBOUNCE_MILLIS = 700L
    const val MIN_QUERY_LENGTH = 2
  }
}

sealed interface BeersSearchEvent {
  data object ShowLoadMoreError : BeersSearchEvent
}

data class BeersSearchUiState(
  val query: String = "",
  val styles: CommonUiState<List<BeerStyle>> = CommonUiState.Loading,
  val selectedStyle: BeerStyle? = null,
  val selectedStyleId: String? = null,
  val activeQuery: BeersQuery = BeersQuery(),
  val viewState: CommonUiState<PagedListUiModel<Beer>> = CommonUiState.Empty,
)

private data class SearchResults(
  val query: BeersQuery,
  val state: CommonUiState<PagedListUiModel<Beer>>,
)
