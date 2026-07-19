package com.simtop.feature.beerbrowse

import app.cash.turbine.test
import com.simtop.beerdomain.domain.errors.FetchBeersError
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.beerdomain.domain.models.BeersQuery
import com.simtop.beerdomain.fakes.FakeBeersPagerFactory
import com.simtop.beerdomain.fakes.FakeBeersRepository
import com.simtop.core.core.CommonUiState
import com.simtop.core.core.CoroutineDispatcherProvider
import com.simtop.core.core.PagedListFooter
import com.simtop.core.core.PagedListUiModel
import com.simtop.core.core.PagingEvent
import com.simtop.core.core.PagingState
import com.simtop.feature.beerbrowse.presentation.BrowseBeersEvent
import com.simtop.feature.beerbrowse.presentation.BrowseBeersViewModel
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isA
import strikt.assertions.isEqualTo

@ExperimentalCoroutinesApi
class BrowseBeersViewModelTest {

  private val coroutineDispatcherProvider = mockk<CoroutineDispatcherProvider>()
  private val fakeRepository = FakeBeersRepository()
  private val fakeFactory = FakeBeersPagerFactory(fakeRepository)

  private lateinit var testDispatcher: TestDispatcher

  private val styleQuery = BeersQuery(styleId = "style-1")

  @BeforeEach
  fun setUp() {
    testDispatcher = UnconfinedTestDispatcher()
    Dispatchers.setMain(testDispatcher)
    every { coroutineDispatcherProvider.io } returns testDispatcher
    every { coroutineDispatcherProvider.main } returns testDispatcher
  }

  @AfterEach fun tearDown() = Dispatchers.resetMain()

  private fun buildViewModel(query: BeersQuery = styleQuery) =
    BrowseBeersViewModel(coroutineDispatcherProvider, fakeFactory, query)

  @Test
  fun `creates one pager for its query and loads the first page`() =
    runTest(testDispatcher) {
      val viewModel = buildViewModel()

      viewModel.viewState.test {
        runCurrent()

        expectThat(fakeFactory.createdQueries.toList()).isEqualTo(listOf(styleQuery))
        expectThat(fakeFactory.searchPagers.last().loadFirstPageCallCount).isEqualTo(1)
        cancelAndIgnoreRemainingEvents()
      }
    }

  @Test
  fun `results surface with the server total`() =
    runTest(testDispatcher) {
      val viewModel = buildViewModel()

      viewModel.viewState.test {
        runCurrent()
        val pager = fakeFactory.searchPagers.last()
        pager.setData(listOf(Beer.empty.copy(id = "1")))
        pager.setPagingState(PagingState.Success(totalCount = 9))
        runCurrent()

        val state = expectMostRecentItem()
        expectThat(state).isA<CommonUiState.Success<PagedListUiModel<Beer>>>()
        val model = (state as CommonUiState.Success).data
        expectThat(model.items.map { it.id }).isEqualTo(listOf("1"))
        expectThat(model.totalCount).isEqualTo(9)
      }
    }

  @Test
  fun `a selection with no beers exposes zero results, not Empty`() =
    runTest(testDispatcher) {
      val viewModel = buildViewModel()

      viewModel.viewState.test {
        runCurrent()
        val pager = fakeFactory.searchPagers.last()
        pager.setData(emptyList())
        pager.setPagingState(PagingState.EndOfPagination())
        runCurrent()

        val state = expectMostRecentItem()
        expectThat(state).isA<CommonUiState.Success<PagedListUiModel<Beer>>>()
        expectThat((state as CommonUiState.Success).data.items).isEqualTo(emptyList())
      }
    }

  @Test
  fun `a first-page failure surfaces the error state`() =
    runTest(testDispatcher) {
      val viewModel = buildViewModel()

      viewModel.viewState.test {
        runCurrent()
        fakeFactory.searchPagers
          .last()
          .setPagingState(PagingState.Error(FetchBeersError.RateLimited, isFirstPage = true))
        runCurrent()

        expectThat(expectMostRecentItem()).isA<CommonUiState.Error>()
      }
    }

  @Test
  fun `a failed load more keeps the list, shows the retry footer and emits the one-shot event`() =
    runTest(testDispatcher) {
      val viewModel = buildViewModel()

      viewModel.events.test {
        viewModel.viewState.test {
          runCurrent()
          val pager = fakeFactory.searchPagers.last()
          pager.setData(listOf(Beer.empty.copy(id = "1")))
          pager.setPagingState(PagingState.Error(FetchBeersError.Network, isFirstPage = false))
          pager.emitEvent(PagingEvent.LoadMoreFailed(FetchBeersError.Network))
          runCurrent()

          val state = expectMostRecentItem()
          val model = (state as CommonUiState.Success).data
          expectThat(model.footer).isEqualTo(PagedListFooter.Retry)
          cancelAndIgnoreRemainingEvents()
        }
        expectThat(awaitItem()).isEqualTo(BrowseBeersEvent.ShowLoadMoreError)
      }
    }

  @Test
  fun `scroll to bottom and retry both load the next page of the same pager`() =
    runTest(testDispatcher) {
      val viewModel = buildViewModel()

      viewModel.viewState.test {
        runCurrent()
        val pager = fakeFactory.searchPagers.last()

        viewModel.onScrollToBottom()
        viewModel.onRetryLoadMore()
        runCurrent()

        expectThat(pager.loadNextPageCallCount).isEqualTo(2)
        cancelAndIgnoreRemainingEvents()
      }
    }
}
