package com.simtop.feature.beersearch

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.simtop.beerdomain.domain.errors.FetchBeersError
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.beerdomain.domain.models.BeersQuery
import com.simtop.beerdomain.fakes.FakeBeersPagerFactory
import com.simtop.beerdomain.fakes.FakeBeersRepository
import com.simtop.core.core.CommonUiState
import com.simtop.core.core.CoroutineDispatcherProvider
import com.simtop.core.core.PagedListUiModel
import com.simtop.core.core.PagingState
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isA
import strikt.assertions.isEmpty
import strikt.assertions.isEqualTo

@ExperimentalCoroutinesApi
class BeersSearchViewModelTest {

  private val coroutineDispatcherProvider = mockk<CoroutineDispatcherProvider>()
  private val fakeRepository = FakeBeersRepository()
  private val fakeFactory = FakeBeersPagerFactory(fakeRepository)

  private lateinit var testDispatcher: TestDispatcher

  private val pastDebounce = 701L // just over the 700ms debounce

  @BeforeEach
  fun setUp() {
    testDispatcher = UnconfinedTestDispatcher()
    Dispatchers.setMain(testDispatcher)
    every { coroutineDispatcherProvider.io } returns testDispatcher
    every { coroutineDispatcherProvider.main } returns testDispatcher
  }

  @AfterEach fun tearDown() = Dispatchers.resetMain()

  private fun buildViewModel(savedStateHandle: SavedStateHandle = SavedStateHandle()) =
    BeersSearchViewModel(coroutineDispatcherProvider, fakeFactory, savedStateHandle)

  @Test
  fun `rapid typing debounces into a single query`() =
    runTest(testDispatcher) {
      val viewModel = buildViewModel()

      viewModel.viewState.test {
        expectThat(awaitItem()).isEqualTo(CommonUiState.Empty) // pre-search prompt

        viewModel.onQueryChange("i")
        viewModel.onQueryChange("ip")
        viewModel.onQueryChange("ipa")
        advanceTimeBy(pastDebounce)
        runCurrent()

        expectThat(fakeFactory.createdQueries.toList()).isEqualTo(listOf(BeersQuery("ipa")))
        cancelAndIgnoreRemainingEvents()
      }
    }

  @Test
  fun `a query under two characters creates no pager`() =
    runTest(testDispatcher) {
      val viewModel = buildViewModel()

      viewModel.viewState.test {
        expectThat(awaitItem()).isEqualTo(CommonUiState.Empty)

        viewModel.onQueryChange("i")
        advanceTimeBy(pastDebounce)
        runCurrent()

        expectThat(fakeFactory.createdQueries).isEmpty()
        cancelAndIgnoreRemainingEvents()
      }
    }

  @Test
  fun `results are exposed with the server total as the result count`() =
    runTest(testDispatcher) {
      val viewModel = buildViewModel()

      viewModel.viewState.test {
        expectThat(awaitItem()).isEqualTo(CommonUiState.Empty)

        viewModel.onQueryChange("ipa")
        advanceTimeBy(pastDebounce)
        runCurrent()
        val pager = fakeFactory.searchPagers.last()
        pager.setData(listOf(Beer.empty.copy(id = "1")))
        pager.setPagingState(PagingState.Success(totalCount = 159))
        runCurrent()

        val state = expectMostRecentItem()
        expectThat(state).isA<CommonUiState.Success<PagedListUiModel<Beer>>>()
        val model = (state as CommonUiState.Success).data
        expectThat(model.items.map { it.id }).isEqualTo(listOf("1"))
        expectThat(model.totalCount).isEqualTo(159)
      }
    }

  // A valid query that matches nothing: empty first page -> EndOfPagination over an empty list.
  // Surfaces as Success with zero results (count 0), which 2c's "No results" empty state reads.
  @Test
  fun `a query with no matches exposes zero results`() =
    runTest(testDispatcher) {
      val viewModel = buildViewModel()

      viewModel.viewState.test {
        expectThat(awaitItem()).isEqualTo(CommonUiState.Empty)

        viewModel.onQueryChange("zzz")
        advanceTimeBy(pastDebounce)
        runCurrent()
        val pager = fakeFactory.searchPagers.last()
        pager.setData(emptyList())
        pager.setPagingState(PagingState.EndOfPagination())
        runCurrent()

        val state = expectMostRecentItem()
        expectThat(state).isA<CommonUiState.Success<PagedListUiModel<Beer>>>()
        expectThat((state as CommonUiState.Success).data.items).isEmpty()
      }
    }

  @Test
  fun `a first-page rate limit shows the error state`() =
    runTest(testDispatcher) {
      val viewModel = buildViewModel()

      viewModel.viewState.test {
        expectThat(awaitItem()).isEqualTo(CommonUiState.Empty)

        viewModel.onQueryChange("ipa")
        advanceTimeBy(pastDebounce)
        runCurrent()
        fakeFactory.searchPagers
          .last()
          .setPagingState(PagingState.Error(FetchBeersError.RateLimited, isFirstPage = true))
        runCurrent()

        expectThat(expectMostRecentItem()).isA<CommonUiState.Error>()
      }
    }

  // The crux: a newer term must win. A stale pager emitting late (its collection cancelled by the
  // newer term) can't overwrite the results the user is now looking at.
  @Test
  fun `a newer term supersedes a stale one`() =
    runTest(testDispatcher) {
      val viewModel = buildViewModel()

      viewModel.viewState.test {
        expectThat(awaitItem()).isEqualTo(CommonUiState.Empty)

        viewModel.onQueryChange("ipa")
        advanceTimeBy(pastDebounce)
        runCurrent()
        val stalePager = fakeFactory.searchPagers.last()

        viewModel.onQueryChange("stout")
        advanceTimeBy(pastDebounce)
        runCurrent()
        val freshPager = fakeFactory.searchPagers.last()
        freshPager.setData(listOf(Beer.empty.copy(id = "stout-1")))
        freshPager.setPagingState(PagingState.Success(totalCount = 10))
        runCurrent()

        // The stale "ipa" pager fires late - it must be ignored.
        stalePager.setData(listOf(Beer.empty.copy(id = "ipa-late")))
        stalePager.setPagingState(PagingState.Success(totalCount = 159))
        runCurrent()

        val state = expectMostRecentItem()
        expectThat((state as CommonUiState.Success).data.items.map { it.id })
          .isEqualTo(listOf("stout-1"))
      }
    }

  // Process death: the query survives in the SavedStateHandle, so a recreated ViewModel re-runs
  // the search on its own - the user gets their results back, not just the text in the field.
  @Test
  fun `a query restored from the saved state re-runs the search`() =
    runTest(testDispatcher) {
      val viewModel = buildViewModel(SavedStateHandle(mapOf("search_query" to "ipa")))

      viewModel.viewState.test {
        advanceTimeBy(pastDebounce)
        runCurrent()

        expectThat(fakeFactory.createdQueries.toList()).isEqualTo(listOf(BeersQuery("ipa")))
        expectThat(viewModel.query.value).isEqualTo("ipa")
        cancelAndIgnoreRemainingEvents()
      }
    }

  @Test
  fun `onScrollToBottom loads the next page of the current results`() =
    runTest(testDispatcher) {
      val viewModel = buildViewModel()

      viewModel.viewState.test {
        expectThat(awaitItem()).isEqualTo(CommonUiState.Empty)

        viewModel.onQueryChange("ipa")
        advanceTimeBy(pastDebounce)
        runCurrent()
        val pager = fakeFactory.searchPagers.last()

        viewModel.onScrollToBottom()
        runCurrent()

        expectThat(pager.loadNextPageCallCount).isEqualTo(1)
        cancelAndIgnoreRemainingEvents()
      }
    }
}
