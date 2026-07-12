package com.simtop.feature.beerslist

import app.cash.turbine.test
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.beerdomain.fakes.FakeBeersPagerFactory
import com.simtop.beerdomain.fakes.FakeBeersRepository
import com.simtop.core.core.CommonUiState
import com.simtop.core.core.CoroutineDispatcherProvider
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

  @Test
  fun `refresh delegates to the pager's first page load`() =
    runTest(testDispatcher) {
      fakeBeersRepository.setBeers(listOf(Beer.empty.copy(id = "1")))
      val viewModel = buildViewModel()

      viewModel.refresh()

      expectThat(fakeBeersPagerFactory.pager.loadFirstPageCallCount).isEqualTo(1)
    }
}
