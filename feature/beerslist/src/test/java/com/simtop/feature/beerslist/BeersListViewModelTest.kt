package com.simtop.feature.beerslist

import app.cash.turbine.test
import com.simtop.beerdomain.domain.errors.FetchBeersError
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.beerdomain.fakes.FakeBeersPagerFactory
import com.simtop.beerdomain.fakes.FakeBeersRepository
import com.simtop.core.core.CommonUiState
import com.simtop.core.core.CoroutineDispatcherProvider
import com.simtop.core.core.PagingEvent
import com.simtop.core.core.PagingState
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isA
import strikt.assertions.isEqualTo
import strikt.assertions.isFalse
import strikt.assertions.isTrue

@ExperimentalCoroutinesApi
class BeersListViewModelTest {

  private val coroutineDispatcherProvider = mockk<CoroutineDispatcherProvider>()
  private val fakeBeersRepository = FakeBeersRepository()
  private val fakeBeersPagerFactory = FakeBeersPagerFactory(fakeBeersRepository)

  private lateinit var testDispatcher: TestDispatcher

  @BeforeEach
  fun setUp() {
    testDispatcher = UnconfinedTestDispatcher()
    Dispatchers.setMain(testDispatcher)
    every { coroutineDispatcherProvider.io } returns testDispatcher
    every { coroutineDispatcherProvider.main } returns testDispatcher
  }

  @AfterEach
  fun tearDown() {
    Dispatchers.resetMain()
  }

  private fun buildViewModel() =
    BeersListViewModel(coroutineDispatcherProvider, fakeBeersRepository, fakeBeersPagerFactory)

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
        expectThat(successState).isA<CommonUiState.Success<BeersListUiModel>>()
        expectThat((successState as CommonUiState.Success).data.beers.size).isEqualTo(1)
        expectThat(successState.data.isLoadingNextPage).isFalse()

        // Trigger scroll to bottom
        viewModel.onScrollToBottom()
        expectThat(pager.loadNextPageCallCount).isEqualTo(1)

        // Simulate the pager reporting a next-page load
        pager.setPagingState(PagingState.LoadingNextPage)

        // Expect update with isLoadingNextPage = true
        val loadingNextPageState = awaitItem()
        expectThat(loadingNextPageState).isA<CommonUiState.Success<BeersListUiModel>>()
        expectThat((loadingNextPageState as CommonUiState.Success).data.isLoadingNextPage).isTrue()

        // Simulate PagingState Success
        pager.setPagingState(PagingState.Success())

        // Expect update with isLoadingNextPage = false
        val finalState = awaitItem()
        expectThat(finalState).isA<CommonUiState.Success<BeersListUiModel>>()
        expectThat((finalState as CommonUiState.Success).data.isLoadingNextPage).isFalse()
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
          expectThat(awaitItem()).isA<CommonUiState.Success<BeersListUiModel>>()

          // The pager reports a non-first-page failure and emits its one-shot event.
          pager.setPagingState(PagingState.Error(FetchBeersError.RateLimited, isFirstPage = false))
          pager.emitEvent(PagingEvent.LoadMoreFailed(FetchBeersError.RateLimited))

          val state = awaitItem()
          expectThat((state as CommonUiState.Success).data.footer).isEqualTo(ListFooter.Retry)
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
        expectThat(awaitItem()).isA<CommonUiState.Success<BeersListUiModel>>()

        pager.setPagingState(PagingState.EndOfPagination)

        val state = awaitItem()
        expectThat((state as CommonUiState.Success).data.footer).isEqualTo(ListFooter.EndReached)
      }
    }

  @Test
  fun `totalCount reported by Success is exposed on the ui model for N of M`() =
    runTest(testDispatcher) {
      fakeBeersRepository.setBeers(listOf(Beer.empty.copy(id = "1")))
      val viewModel = buildViewModel()
      val pager = fakeBeersPagerFactory.pager

      viewModel.beerListViewState.test {
        expectThat(awaitItem()).isA<CommonUiState.Success<BeersListUiModel>>()

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
        expectThat(state).isA<CommonUiState.Success<BeersListUiModel>>()
        expectThat((state as CommonUiState.Success).data.beers.map { it.id })
          .isEqualTo(listOf("1", "2"))
      }
      expectThat(fakeBeersPagerFactory.pager.loadFirstPageCallCount).isEqualTo(0)
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
        expectThat(awaitItem()).isA<CommonUiState.Success<BeersListUiModel>>()

        viewModel.refresh()
        pager.setPagingState(PagingState.Loading)

        val refreshing = awaitItem()
        expectThat(refreshing).isA<CommonUiState.Success<BeersListUiModel>>()
        expectThat((refreshing as CommonUiState.Success).data.isRefreshing).isTrue()

        pager.setPagingState(PagingState.Success())

        val done = awaitItem()
        expectThat(done).isA<CommonUiState.Success<BeersListUiModel>>()
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

        pager.setPagingState(PagingState.EndOfPagination)

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
          expectThat(awaitItem()).isA<CommonUiState.Success<BeersListUiModel>>()

          viewModel.refresh()
          pager.setPagingState(PagingState.Loading)
          expectThat((awaitItem() as CommonUiState.Success).data.isRefreshing).isTrue()

          pager.setPagingState(PagingState.Error(FetchBeersError.Network, isFirstPage = true))

          val state = awaitItem()
          expectThat(state).isA<CommonUiState.Success<BeersListUiModel>>()
          expectThat((state as CommonUiState.Success).data.isRefreshing).isFalse()
          expectThat(state.data.footer).isEqualTo(ListFooter.Hidden)
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
}
