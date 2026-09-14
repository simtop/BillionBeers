package com.simtop.beerdomain.fakes

import app.cash.turbine.test
import com.simtop.beerdomain.domain.errors.FetchBeersError
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.beerdomain.domain.models.BeersQuery
import com.simtop.core.core.PagingEvent
import com.simtop.core.core.PagingState
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FakeBeersPagerFactoryTest {

  @Test
  fun `catalog pager is shared while query pagers are independent`() = runTest {
    val repository = FakeBeersRepository()
    val factory = FakeBeersPagerFactory(repository)
    val catalog = factory.create()
    val catalogAgain = factory.create()
    val firstQuery = BeersQuery(search = "ipa")
    val secondQuery = BeersQuery(search = "lager")
    val first = factory.create(firstQuery) as FakePager<*, *>
    val second = factory.create(secondQuery) as FakePager<*, *>

    assertTrue(catalog === catalogAgain)
    assertTrue(first !== second)
    assertEquals(listOf(firstQuery, secondQuery), factory.createdQueries)
    assertEquals(listOf(first, second), factory.searchPagers)
  }

  @Test
  fun `query pager data does not leak between instances`() = runTest {
    val factory = FakeBeersPagerFactory(FakeBeersRepository())
    val first =
      factory.create(BeersQuery(search = "ipa")) as
        FakePager<Beer, FetchBeersError>
    val second =
      factory.create(BeersQuery(search = "lager")) as
        FakePager<Beer, FetchBeersError>

    first.data.test {
      assertEquals(emptyList(), awaitItem())
      first.setData(listOf(fakeBeerModel))
      assertEquals(listOf(fakeBeerModel), awaitItem())
      cancelAndIgnoreRemainingEvents()
    }
    second.data.test {
      assertEquals(emptyList(), awaitItem())
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `pager exposes controllable state events data and load counters`() = runTest {
    val pager = FakePager<Beer, FetchBeersError>()

    pager.pagingState.test {
      assertEquals(PagingState.Idle, awaitItem())
      pager.setPagingState(PagingState.Loading)
      assertEquals(PagingState.Loading, awaitItem())
      cancelAndIgnoreRemainingEvents()
    }
    pager.events.test {
      pager.emitEvent(PagingEvent.LoadMoreFailed(FetchBeersError.Network))
      assertEquals(PagingEvent.LoadMoreFailed(FetchBeersError.Network), awaitItem())
      cancelAndIgnoreRemainingEvents()
    }
    pager.loadFirstPage()
    pager.loadNextPage()
    assertEquals(1, pager.loadFirstPageCallCount)
    assertEquals(1, pager.loadNextPageCallCount)
  }
}
