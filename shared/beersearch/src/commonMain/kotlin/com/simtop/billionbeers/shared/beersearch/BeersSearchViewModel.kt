package com.simtop.billionbeers.shared.beersearch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simtop.beerdomain.domain.errors.FetchBeersError
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.beerdomain.domain.models.BeersQuery
import com.simtop.beerdomain.domain.repositories.BeersPagerFactory
import com.simtop.billionbeers.shared.presentation.toCommonUiErrorState
import com.simtop.core.core.CommonUiState
import com.simtop.core.core.CoroutineDispatcherProvider
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

open class BeersSearchViewModel(
  private val coroutineDispatcher: CoroutineDispatcherProvider,
  private val beersPagerFactory: BeersPagerFactory,
  initialQuery: String = "",
) : ViewModel() {

  private val queryText = MutableStateFlow(initialQuery)
  val query: StateFlow<String> = queryText

  private var currentPager: Pager<Beer, FetchBeersError>? = null

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
          emit(CommonUiState.Empty)
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

  open fun onQueryChange(text: String) {
    queryText.value = text
  }

  fun onScrollToBottom() {
    viewModelScope.launch { currentPager?.loadNextPage() }
  }

  fun onRetryLoadMore() {
    viewModelScope.launch { currentPager?.loadNextPage() }
  }

  fun onRetrySearch() {
    viewModelScope.launch { currentPager?.loadFirstPage() }
  }

  private companion object {
    const val DEBOUNCE_MILLIS = 700L
    const val MIN_QUERY_LENGTH = 2
    const val SUBSCRIPTION_TIMEOUT_MILLIS = 5_000L
  }
}

sealed interface BeersSearchEvent {
  data object ShowLoadMoreError : BeersSearchEvent
}
