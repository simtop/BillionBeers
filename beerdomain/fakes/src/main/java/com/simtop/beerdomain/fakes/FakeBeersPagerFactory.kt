package com.simtop.beerdomain.fakes

import com.simtop.beerdomain.domain.errors.FetchBeersError
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.beerdomain.domain.repositories.BeersPagerFactory
import com.simtop.core.core.Pager
import com.simtop.core.core.PagingState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A [Pager] whose [data] is whatever flow the test wires in and whose [pagingState] is set directly
 * with [setPagingState] - load calls are only recorded, never fetch anything.
 */
class FakePager<Value : Any, E : Any>(override val data: Flow<List<Value>>) : Pager<Value, E> {

  private val _pagingState = MutableStateFlow<PagingState<E>>(PagingState.Idle)
  override val pagingState: StateFlow<PagingState<E>> = _pagingState.asStateFlow()

  var loadFirstPageCallCount = 0
    private set

  var loadNextPageCallCount = 0
    private set

  fun setPagingState(state: PagingState<E>) {
    _pagingState.value = state
  }

  override suspend fun loadFirstPage() {
    loadFirstPageCallCount++
  }

  override suspend fun loadNextPage() {
    loadNextPageCallCount++
  }
}

/**
 * Hands out a single [FakePager] backed by [repository]'s beers flow, so tests drive the list with
 * [FakeBeersRepository.setBeers] and paging states with [FakePager.setPagingState].
 */
class FakeBeersPagerFactory(repository: FakeBeersRepository) : BeersPagerFactory {

  val pager: FakePager<Beer, FetchBeersError> = FakePager(repository.observeBeers())

  override fun create(): Pager<Beer, FetchBeersError> = pager
}
