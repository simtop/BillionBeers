package com.simtop.beerdomain.fakes

import com.simtop.beerdomain.domain.errors.FetchBeersError
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.beerdomain.domain.models.BeersQuery
import com.simtop.beerdomain.domain.repositories.BeersPagerFactory
import com.simtop.core.core.Pager
import com.simtop.core.core.PagingEvent
import com.simtop.core.core.PagingState
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * A [Pager] whose [pagingState]/[events] are set directly with [setPagingState]/[emitEvent] and
 * whose [data] is either an external flow wired in (catalog, backed by the repository) or, when
 * none is given, an internal source driven with [setData] (search). Load calls are only recorded.
 */
class FakePager<Value : Any, E : Any>(dataFlow: Flow<List<Value>>? = null) : Pager<Value, E> {

  private val _data = MutableStateFlow<List<Value>>(emptyList())
  override val data: Flow<List<Value>> = dataFlow ?: _data

  /** Pushes items when this pager owns its data source (no external flow was supplied). */
  fun setData(items: List<Value>) {
    _data.value = items
  }

  private val _pagingState = MutableStateFlow<PagingState<E>>(PagingState.Idle)
  override val pagingState: StateFlow<PagingState<E>> = _pagingState.asStateFlow()

  private val _events = Channel<PagingEvent<E>>(Channel.UNLIMITED)
  override val events: Flow<PagingEvent<E>> = _events.receiveAsFlow()

  var loadFirstPageCallCount = 0
    private set

  var loadNextPageCallCount = 0
    private set

  fun setPagingState(state: PagingState<E>) {
    _pagingState.value = state
  }

  fun emitEvent(event: PagingEvent<E>) {
    _events.trySend(event)
  }

  override suspend fun loadFirstPage() {
    loadFirstPageCallCount++
  }

  override suspend fun loadNextPage() {
    loadNextPageCallCount++
  }
}

/**
 * The catalog [create] hands out a single [pager] backed by [repository]'s beers flow (drive it
 * with [FakeBeersRepository.setBeers] + [FakePager.setPagingState]). Each search [create] mints a
 * *fresh* pager with its own data source, recorded in [searchPagers]/[createdQueries], so a test
 * can drive different terms independently and prove a stale one can't overwrite a newer one.
 */
class FakeBeersPagerFactory(repository: FakeBeersRepository) : BeersPagerFactory {

  val pager: FakePager<Beer, FetchBeersError> = FakePager(repository.observeBeers())

  /** Every query a search pager was created for, in order. */
  val createdQueries = mutableListOf<BeersQuery>()

  /** The per-query search pagers handed out, in order - one per [create] with a query. */
  val searchPagers = mutableListOf<FakePager<Beer, FetchBeersError>>()

  override fun create(): Pager<Beer, FetchBeersError> = pager

  override fun create(query: BeersQuery): Pager<Beer, FetchBeersError> {
    createdQueries += query
    return FakePager<Beer, FetchBeersError>().also { searchPagers += it }
  }
}
