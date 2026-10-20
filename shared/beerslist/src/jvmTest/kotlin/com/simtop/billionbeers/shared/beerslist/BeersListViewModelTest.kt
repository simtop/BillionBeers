package com.simtop.billionbeers.shared.beerslist

import app.cash.turbine.test
import com.simtop.beerdomain.domain.errors.FetchBeersError
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.beerdomain.domain.models.BeersQuery
import com.simtop.beerdomain.domain.models.CatalogCacheStatus
import com.simtop.beerdomain.domain.repositories.BeersPagerFactory
import com.simtop.beerdomain.fakes.FakeBeersPagerFactory
import com.simtop.beerdomain.fakes.FakeBeersRepository
import com.simtop.beerdomain.fakes.FakePager
import com.simtop.billionbeers.testing_utils.MainDispatcherExtension
import com.simtop.core.core.CommonUiErrorKey
import com.simtop.core.core.CommonUiState
import com.simtop.core.core.FLOW_RETRY_DELAY_MILLIS
import com.simtop.core.core.PagedListFooter
import com.simtop.core.core.PagedListUiModel
import com.simtop.core.core.Pager
import com.simtop.core.core.PagingEvent
import com.simtop.core.core.PagingState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import strikt.api.expectThat
import strikt.assertions.isA
import strikt.assertions.isEqualTo
import strikt.assertions.isFalse
import strikt.assertions.isTrue

@ExperimentalCoroutinesApi
class BeersListViewModelTest {

  private val fakeBeersRepository = FakeBeersRepository()
  private val fakeBeersPagerFactory = FakeBeersPagerFactory(fakeBeersRepository)
  @JvmField
  @RegisterExtension
  val mainDispatcher = MainDispatcherExtension(UnconfinedTestDispatcher())
  private val testDispatcher
    get() = mainDispatcher.testDispatcher

  private fun buildViewModel() = BeersListViewModel(fakeBeersRepository, fakeBeersPagerFactory)

  @Test
  fun `when onScrollToBottom is called, isLoadingNextPage should be true`() =
    runTest(testDispatcher) {
      val beer = Beer.empty.copy(id = "1", name = "Test Beer")
      val viewModel = buildViewModel()
      val pager = fakeBeersPagerFactory.pager

      viewModel.beerListViewState.test {
        // Initial state (Loading)
        expectThat(awaitItem()).isA<CommonUiState.Loading>()

        // Trigger data load
        fakeBeersRepository.setBeers(listOf(beer))

        // Success state from the pager's data flow
        val successState = awaitItem()
        expectThat(successState).isA<CommonUiState.Success<PagedListUiModel<Beer>>>()
        expectThat((successState as CommonUiState.Success).data.items.size).isEqualTo(1)
        expectThat(successState.data.isLoadingNextPage).isFalse()

        // Trigger scroll to bottom
        viewModel.onScrollToBottom()
        expectThat(pager.loadNextPageCallCount).isEqualTo(1)

        // Simulate the pager reporting a next-page load
        pager.setPagingState(PagingState.LoadingNextPage)

        // Expect update with isLoadingNextPage = true
        val loadingNextPageState = awaitItem()
        expectThat(loadingNextPageState).isA<CommonUiState.Success<PagedListUiModel<Beer>>>()
        expectThat((loadingNextPageState as CommonUiState.Success).data.isLoadingNextPage).isTrue()

        // Simulate PagingState Success
        pager.setPagingState(PagingState.Success())

        // Expect update with isLoadingNextPage = false
        val finalState = awaitItem()
        expectThat(finalState).isA<CommonUiState.Success<PagedListUiModel<Beer>>>()
        expectThat((finalState as CommonUiState.Success).data.isLoadingNextPage).isFalse()
      }
    }

  @Test
  fun `catalog observation emits error and recovers after retry`() =
    runTest(testDispatcher) {
      var subscriptions = 0
      val beer = Beer.empty.copy(id = "recovered", name = "Recovered")
      fakeBeersRepository.setBeersObservationFlow(
        flow {
          if (subscriptions++ == 0) error("database unavailable")
          emit(listOf(beer))
          awaitCancellation()
        }
      )
      val pagerFactory = FakeBeersPagerFactory(fakeBeersRepository)
      val viewModel = BeersListViewModel(fakeBeersRepository, pagerFactory)

      viewModel.beerListViewState.test {
        val error = awaitItem()
        expectThat(error).isA<CommonUiState.Error>()
        expectThat((error as CommonUiState.Error).errorKey)
          .isEqualTo(CommonUiErrorKey.FailedToLoadBeers)

        advanceTimeBy(FLOW_RETRY_DELAY_MILLIS)
        testDispatcher.scheduler.runCurrent()

        val recovered = awaitItem()
        expectThat(recovered).isA<CommonUiState.Success<PagedListUiModel<Beer>>>()
        expectThat((recovered as CommonUiState.Success).data.items).isEqualTo(listOf(beer))
        expectThat(subscriptions).isEqualTo(2)
        cancelAndIgnoreRemainingEvents()
      }
    }

  @Test
  fun `a failed load-more surfaces a retry footer and a one-shot error event`() =
    runTest(testDispatcher) {
      fakeBeersRepository.setBeers(listOf(Beer.empty.copy(id = "1")))
      val viewModel = buildViewModel()
      val pager = fakeBeersPagerFactory.pager

      viewModel.events.test {
        viewModel.beerListViewState.test {
          expectThat(awaitItem()).isA<CommonUiState.Success<PagedListUiModel<Beer>>>()

          // The pager reports a non-first-page failure and emits its one-shot event.
          pager.setPagingState(PagingState.Error(FetchBeersError.RateLimited, isFirstPage = false))
          pager.emitEvent(PagingEvent.LoadMoreFailed(FetchBeersError.RateLimited))

          val state = awaitItem()
          expectThat((state as CommonUiState.Success).data.footer).isEqualTo(PagedListFooter.Retry)
        }
        expectThat(awaitItem()).isEqualTo(BeersListEvent.ShowLoadMoreError)
      }
    }

  @Test
  fun `end of pagination surfaces the end-of-list footer`() =
    runTest(testDispatcher) {
      fakeBeersRepository.setBeers(listOf(Beer.empty.copy(id = "1")))
      val viewModel = buildViewModel()
      val pager = fakeBeersPagerFactory.pager

      viewModel.beerListViewState.test {
        expectThat(awaitItem()).isA<CommonUiState.Success<PagedListUiModel<Beer>>>()

        pager.setPagingState(PagingState.EndOfPagination())

        val state = awaitItem()
        expectThat((state as CommonUiState.Success).data.footer)
          .isEqualTo(PagedListFooter.EndReached)
      }
    }

  @Test
  fun `totalCount reported by Success is exposed on the ui model for N of M`() =
    runTest(testDispatcher) {
      fakeBeersRepository.setBeers(listOf(Beer.empty.copy(id = "1")))
      val viewModel = buildViewModel()
      val pager = fakeBeersPagerFactory.pager

      viewModel.beerListViewState.test {
        expectThat(awaitItem()).isA<CommonUiState.Success<PagedListUiModel<Beer>>>()

        pager.setPagingState(PagingState.Success(totalCount = 206))

        val state = awaitItem()
        expectThat((state as CommonUiState.Success).data.totalCount).isEqualTo(206)
      }
    }

  @Test
  fun `loads the first page on start only when the cache is empty`() =
    runTest(testDispatcher) {
      buildViewModel()

      expectThat(fakeBeersPagerFactory.pager.loadFirstPageCallCount).isEqualTo(1)
    }

  @Test
  fun `does not load the first page on start when the cache has beers`() =
    runTest(testDispatcher) {
      fakeBeersRepository.setBeers(listOf(Beer.empty.copy(id = "1")))

      buildViewModel()

      expectThat(fakeBeersPagerFactory.pager.loadFirstPageCallCount).isEqualTo(0)
    }

  // Warm start: a previous process left beers cached, so the screen shows them straight away
  // (Success, not the Loading skeleton) with no network first-page load.
  @Test
  fun `a warm cache surfaces the cached beers as Success without loading the first page`() =
    runTest(testDispatcher) {
      fakeBeersRepository.setBeers(listOf(Beer.empty.copy(id = "1"), Beer.empty.copy(id = "2")))
      val viewModel = buildViewModel()

      viewModel.beerListViewState.test {
        val state = awaitItem()
        expectThat(state).isA<CommonUiState.Success<PagedListUiModel<Beer>>>()
        expectThat((state as CommonUiState.Success).data.items.map { it.id })
          .isEqualTo(listOf("1", "2"))
      }
      expectThat(fakeBeersPagerFactory.pager.loadFirstPageCallCount).isEqualTo(0)
    }

  // The TTL policy (Paging 2.0 Phase 4): a warm cache past its staleAfter still shows instantly,
  // but start also kicks a background first-page load - the reducer renders it as the refresh
  // spinner over the intact list.
  @Test
  fun `a stale warm cache loads the first page in the background on start`() =
    runTest(testDispatcher) {
      fakeBeersRepository.setBeers(listOf(Beer.empty.copy(id = "1")))
      fakeBeersRepository.catalogCacheStatus = CatalogCacheStatus.Stale

      val viewModel = buildViewModel()

      viewModel.beerListViewState.test {
        expectThat(awaitItem()).isA<CommonUiState.Success<PagedListUiModel<Beer>>>()
      }
      expectThat(fakeBeersPagerFactory.pager.loadFirstPageCallCount).isEqualTo(1)
    }

  @Test
  fun `a language-mismatched cache loads the first page in the background on start`() =
    runTest(testDispatcher) {
      fakeBeersRepository.setBeers(listOf(Beer.empty.copy(id = "1")))
      fakeBeersRepository.catalogCacheStatus = CatalogCacheStatus.LanguageMismatch

      buildViewModel()

      expectThat(fakeBeersPagerFactory.pager.loadFirstPageCallCount).isEqualTo(1)
    }

  // Regression test: PagingState.Loading over an already-shown list is a refresh in progress, so
  // the pull-to-refresh indicator must stay visible until the load resolves.
  @Test
  fun `refresh shows the refreshing indicator until the load completes`() =
    runTest(testDispatcher) {
      fakeBeersRepository.setBeers(listOf(Beer.empty.copy(id = "1")))
      val viewModel = buildViewModel()
      val pager = fakeBeersPagerFactory.pager

      viewModel.beerListViewState.test {
        expectThat(awaitItem()).isA<CommonUiState.Success<PagedListUiModel<Beer>>>()

        viewModel.refresh()
        pager.setPagingState(PagingState.Loading)

        val refreshing = awaitItem()
        expectThat(refreshing).isA<CommonUiState.Success<PagedListUiModel<Beer>>>()
        expectThat((refreshing as CommonUiState.Success).data.isRefreshing).isTrue()

        pager.setPagingState(PagingState.Success())

        val done = awaitItem()
        expectThat(done).isA<CommonUiState.Success<PagedListUiModel<Beer>>>()
        expectThat((done as CommonUiState.Success).data.isRefreshing).isFalse()
      }
    }

  // Regression test: an empty catalog (first fetch returns no items) must resolve to Empty. The
  // beers flow never emits a non-empty list, so only the paging state can end the skeleton.
  @Test
  fun `an empty catalog resolves to Empty instead of staying on the skeleton`() =
    runTest(testDispatcher) {
      val viewModel = buildViewModel()
      val pager = fakeBeersPagerFactory.pager

      viewModel.beerListViewState.test {
        expectThat(awaitItem()).isA<CommonUiState.Loading>()

        pager.setPagingState(PagingState.EndOfPagination())

        expectThat(awaitItem()).isEqualTo(CommonUiState.Empty)
      }
    }

  // A failed refresh keeps the list on screen: no load-more retry footer (its copy would be wrong
  // and its tap targets the wrong load) - the one-shot toast is the feedback, pulling again the
  // retry.
  @Test
  fun `a failed refresh keeps the list, hides the footer and emits a one-shot refresh error`() =
    runTest(testDispatcher) {
      fakeBeersRepository.setBeers(listOf(Beer.empty.copy(id = "1")))
      val viewModel = buildViewModel()
      val pager = fakeBeersPagerFactory.pager

      viewModel.events.test {
        viewModel.beerListViewState.test {
          expectThat(awaitItem()).isA<CommonUiState.Success<PagedListUiModel<Beer>>>()

          viewModel.refresh()
          pager.setPagingState(PagingState.Loading)
          expectThat((awaitItem() as CommonUiState.Success).data.isRefreshing).isTrue()

          pager.setPagingState(PagingState.Error(FetchBeersError.Network, isFirstPage = true))

          val state = awaitItem()
          expectThat(state).isA<CommonUiState.Success<PagedListUiModel<Beer>>>()
          expectThat((state as CommonUiState.Success).data.isRefreshing).isFalse()
          expectThat(state.data.footer).isEqualTo(PagedListFooter.Hidden)
        }
        expectThat(awaitItem()).isEqualTo(BeersListEvent.ShowRefreshError)
      }
    }

  @Test
  fun `refresh delegates to the pager's first page load`() =
    runTest(testDispatcher) {
      fakeBeersRepository.setBeers(listOf(Beer.empty.copy(id = "1")))
      val viewModel = buildViewModel()

      viewModel.refresh()

      expectThat(fakeBeersPagerFactory.pager.loadFirstPageCallCount).isEqualTo(1)
    }

  @Test
  fun `each view model owns one isolated catalog pager`() =
    runTest(testDispatcher) {
      val factory = IsolatedPagerFactory()

      BeersListViewModel(FakeBeersRepository(), factory)
      BeersListViewModel(FakeBeersRepository(), factory)

      expectThat(factory.createdPagers.size).isEqualTo(2)
      expectThat(factory.createdPagers[0] === factory.createdPagers[1]).isFalse()
    }

  @Test
  fun `inline search debounces trimmed criteria without filtering the catalog`() =
    runTest(testDispatcher) {
      val original = Beer.empty.copy(id = "catalog", name = "Catalog beer")
      fakeBeersRepository.setBeers(listOf(original))
      val model = buildViewModel()
      model.onQueryChange("  IPA  ")
      advanceTimeBy(699)
      runCurrent()
      expectThat(fakeBeersPagerFactory.createdQueries.size).isEqualTo(0)
      advanceTimeBy(1)
      runCurrent()
      expectThat(fakeBeersPagerFactory.createdQueries.toList())
        .isEqualTo(listOf(BeersQuery(search = "IPA")))
      val search = fakeBeersPagerFactory.searchPagers.single()
      expectThat(search.loadFirstPageCallCount).isEqualTo(1)
      search.setData(listOf(Beer.empty.copy(id = "search", name = "IPA")))
      runCurrent()
      expectThat((model.beerListViewState.value as CommonUiState.Success).data.items.single().id)
        .isEqualTo("search")
      model.onQueryChange("")
      runCurrent()
      expectThat((model.beerListViewState.value as CommonUiState.Success).data.items)
        .isEqualTo(listOf(original))
    }

  @Test
  fun `replaced search cannot overwrite newer results`() =
    runTest(testDispatcher) {
      val model = buildViewModel()
      model.onQueryChange("IPA")
      model.submitSearch()
      runCurrent()
      val old = fakeBeersPagerFactory.searchPagers.single()
      old.setData(listOf(Beer.empty.copy(id = "old")))
      model.onQueryChange("stout")
      model.submitSearch()
      runCurrent()
      val newest = fakeBeersPagerFactory.searchPagers.last()
      old.setData(listOf(Beer.empty.copy(id = "stale")))
      runCurrent()
      expectThat(model.beerListViewState.value).isA<CommonUiState.Loading>()
      newest.setData(listOf(Beer.empty.copy(id = "new")))
      runCurrent()
      expectThat((model.beerListViewState.value as CommonUiState.Success).data.items.single().id)
        .isEqualTo("new")
    }

  @Test
  fun `submit applies pending search immediately without duplicating the pager`() =
    runTest(testDispatcher) {
      val model = buildViewModel()
      model.onQueryChange("  pale ale  ")
      model.submitSearch()
      runCurrent()
      expectThat(model.activeQuery.value).isEqualTo(BeersQuery(search = "pale ale"))
      advanceTimeBy(1000)
      runCurrent()
      expectThat(fakeBeersPagerFactory.createdQueries.size).isEqualTo(1)
      model.onQueryChange("x")
      expectThat(model.activeQuery.value).isEqualTo(BeersQuery())
    }

  @Test
  fun `paging refresh and retries follow the active search pager`() =
    runTest(testDispatcher) {
      val model = buildViewModel()
      val originalLoads = fakeBeersPagerFactory.pager.loadFirstPageCallCount
      model.onQueryChange("IPA")
      model.submitSearch()
      runCurrent()
      val search = fakeBeersPagerFactory.searchPagers.single()
      model.onScrollToBottom()
      model.onRetryLoadMore()
      model.refresh()
      runCurrent()
      expectThat(search.loadNextPageCallCount).isEqualTo(2)
      expectThat(search.loadFirstPageCallCount).isEqualTo(2)
      expectThat(fakeBeersPagerFactory.pager.loadNextPageCallCount).isEqualTo(0)
      expectThat(fakeBeersPagerFactory.pager.loadFirstPageCallCount).isEqualTo(originalLoads)
      search.setPagingState(PagingState.EndOfPagination())
      runCurrent()
      expectThat(model.beerListViewState.value).isEqualTo(CommonUiState.Empty)
      model.onQueryChange("")
      model.onScrollToBottom()
      runCurrent()
      expectThat(fakeBeersPagerFactory.pager.loadNextPageCallCount).isEqualTo(1)
    }

  @Test
  fun `search refresh failure is one event even if data changes again`() =
    runTest(testDispatcher) {
      val model = buildViewModel()
      model.onQueryChange("IPA")
      model.submitSearch()
      runCurrent()
      val search = fakeBeersPagerFactory.searchPagers.single()
      search.setData(listOf(Beer.empty.copy(id = "first")))
      runCurrent()
      model.events.test {
        fakeBeersPagerFactory.pager.emitEvent(PagingEvent.LoadMoreFailed(FetchBeersError.Network))
        runCurrent()
        expectNoEvents()
        search.setPagingState(PagingState.Error(FetchBeersError.Network, isFirstPage = true))
        runCurrent()
        expectThat(awaitItem()).isEqualTo(BeersListEvent.ShowRefreshError)
        search.setData(listOf(Beer.empty.copy(id = "updated")))
        runCurrent()
        expectNoEvents()
        expectThat((model.beerListViewState.value as CommonUiState.Success).data.items.single().id)
          .isEqualTo("updated")
      }
    }

  @Test
  fun `search observation recovers from a transient read failure and stops on clear`() =
    runTest(testDispatcher) {
      var subscriptions = 0
      val base = FakePager<Beer, FetchBeersError>()
      val search =
        FakePager<Beer, FetchBeersError>(
          flow {
            if (subscriptions++ == 0) error("temporary read failure")
            emit(listOf(Beer.empty.copy(id = "recovered")))
            awaitCancellation()
          }
        )
      val factory =
        object : BeersPagerFactory {
          override fun create(): Pager<Beer, FetchBeersError> = base

          override fun create(query: BeersQuery): Pager<Beer, FetchBeersError> = search
        }
      val model = BeersListViewModel(fakeBeersRepository, factory)
      model.onQueryChange("IPA")
      model.submitSearch()
      runCurrent()
      expectThat(model.beerListViewState.value).isA<CommonUiState.Error>()
      advanceTimeBy(FLOW_RETRY_DELAY_MILLIS + 1)
      runCurrent()
      expectThat((model.beerListViewState.value as CommonUiState.Success).data.items.single().id)
        .isEqualTo("recovered")
      model.onQueryChange("")
      advanceTimeBy(FLOW_RETRY_DELAY_MILLIS * 2)
      runCurrent()
      expectThat(subscriptions).isEqualTo(2)
      expectThat(model.activeQuery.value).isEqualTo(BeersQuery())
    }

  private class IsolatedPagerFactory : BeersPagerFactory {
    val createdPagers = mutableListOf<FakePager<Beer, FetchBeersError>>()

    override fun create(): Pager<Beer, FetchBeersError> =
      FakePager<Beer, FetchBeersError>().also { createdPagers += it }

    override fun create(query: BeersQuery): Pager<Beer, FetchBeersError> =
      FakePager<Beer, FetchBeersError>().also { createdPagers += it }
  }
}
