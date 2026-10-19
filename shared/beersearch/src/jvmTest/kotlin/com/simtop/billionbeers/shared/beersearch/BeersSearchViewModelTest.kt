package com.simtop.billionbeers.shared.beersearch

import app.cash.turbine.test
import com.simtop.beerdomain.domain.errors.FetchBeersError
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.beerdomain.domain.models.BeerStyle
import com.simtop.beerdomain.domain.models.BeersQuery
import com.simtop.beerdomain.fakes.FakeBeersPagerFactory
import com.simtop.beerdomain.fakes.FakeBeersRepository
import com.simtop.billionbeers.testing_utils.MainDispatcherExtension
import com.simtop.core.core.CommonUiState
import com.simtop.core.core.Either
import com.simtop.core.core.PagedListUiModel
import com.simtop.core.core.PagingState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import strikt.api.expectThat
import strikt.assertions.isA
import strikt.assertions.isEmpty
import strikt.assertions.isEqualTo

@ExperimentalCoroutinesApi
class BeersSearchViewModelTest {

  @JvmField
  @RegisterExtension
  val mainDispatcher = MainDispatcherExtension(UnconfinedTestDispatcher())
  private val fakeRepository = FakeBeersRepository()
  private val fakeFactory = FakeBeersPagerFactory(fakeRepository)

  private val pastDebounce = 701L // just over the 700ms debounce

  private fun buildViewModel(initialQuery: String = "", initialStyleId: String? = null) =
    BeersSearchViewModel(
      coroutineDispatcher = mainDispatcher.dispatcherProvider,
      beersPagerFactory = fakeFactory,
      catalogMetadataRepository = fakeRepository,
      initialQuery = initialQuery,
      initialStyleId = initialStyleId,
    )

  @Test
  fun `rapid typing debounces into a single query`() =
    runTest(mainDispatcher.testDispatcher) {
      val viewModel = buildViewModel()

      viewModel.viewState().test {
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
  fun `active query follows results rather than the edited search text`() =
    runTest(mainDispatcher.testDispatcher) {
      val viewModel = buildViewModel()

      viewModel.onQueryChange("ipa")
      expectThat(viewModel.uiState.value.query).isEqualTo("ipa")
      expectThat(viewModel.uiState.value.activeQuery).isEqualTo(BeersQuery())

      advanceTimeBy(pastDebounce)
      runCurrent()
      expectThat(viewModel.uiState.value.activeQuery).isEqualTo(BeersQuery(search = "ipa"))

      viewModel.onQueryChange("stout")
      expectThat(viewModel.uiState.value.query).isEqualTo("stout")
      expectThat(viewModel.uiState.value.activeQuery).isEqualTo(BeersQuery(search = "ipa"))
    }

  @Test
  fun `a query under two characters creates no pager`() =
    runTest(mainDispatcher.testDispatcher) {
      val viewModel = buildViewModel()

      viewModel.viewState().test {
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
    runTest(mainDispatcher.testDispatcher) {
      val viewModel = buildViewModel()

      viewModel.viewState().test {
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

  @Test
  fun `search results survive a collector gap beyond the old timeout`() =
    runTest(mainDispatcher.testDispatcher) {
      val viewModel = buildViewModel()
      val result = Beer.empty.copy(id = "1")

      viewModel.viewState().test {
        expectThat(awaitItem()).isEqualTo(CommonUiState.Empty)

        viewModel.onQueryChange("ipa")
        advanceTimeBy(pastDebounce)
        runCurrent()
        val pager = fakeFactory.searchPagers.single()
        pager.setData(listOf(result))
        pager.setPagingState(PagingState.Success(totalCount = 159))
        runCurrent()

        val state = expectMostRecentItem()
        expectThat((state as CommonUiState.Success).data.items.map { it.id }).isEqualTo(listOf("1"))
        cancelAndIgnoreRemainingEvents()
      }

      val originalPager = fakeFactory.searchPagers.single()
      advanceTimeBy(5_001L)
      runCurrent()

      viewModel.viewState().test {
        expectThat(awaitItem()).isA<CommonUiState.Success<PagedListUiModel<Beer>>>()
        advanceTimeBy(pastDebounce)
        runCurrent()

        expectThat(fakeFactory.createdQueries.toList()).isEqualTo(listOf(BeersQuery("ipa")))
        expectThat(fakeFactory.searchPagers.toList()).isEqualTo(listOf(originalPager))
        expectThat(originalPager.loadFirstPageCallCount).isEqualTo(1)
        val retained = viewModel.uiState.value.viewState as CommonUiState.Success
        expectThat(retained.data.items.map { it.id }).isEqualTo(listOf("1"))
        expectThat(retained.data.totalCount).isEqualTo(159)

        viewModel.onScrollToBottom()
        runCurrent()

        expectThat(originalPager.loadNextPageCallCount).isEqualTo(1)
        cancelAndIgnoreRemainingEvents()
      }
    }

  // A valid query that matches nothing: empty first page -> EndOfPagination over an empty list.
  // Surfaces as Success with zero results (count 0), which 2c's "No results" empty state reads.
  @Test
  fun `a query with no matches exposes zero results`() =
    runTest(mainDispatcher.testDispatcher) {
      val viewModel = buildViewModel()

      viewModel.viewState().test {
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
    runTest(mainDispatcher.testDispatcher) {
      val viewModel = buildViewModel()

      viewModel.viewState().test {
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
    runTest(mainDispatcher.testDispatcher) {
      val viewModel = buildViewModel()

      viewModel.viewState().test {
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

  @Test
  fun `an initial query starts the search without debounce`() =
    runTest(mainDispatcher.testDispatcher) {
      val viewModel = buildViewModel(initialQuery = "ipa")

      viewModel.viewState().test {
        runCurrent()

        expectThat(fakeFactory.createdQueries.toList()).isEqualTo(listOf(BeersQuery("ipa")))
        expectThat(viewModel.uiState.value.query).isEqualTo("ipa")
        cancelAndIgnoreRemainingEvents()
      }
    }

  @Test
  fun `onScrollToBottom loads the next page of the current results`() =
    runTest(mainDispatcher.testDispatcher) {
      val viewModel = buildViewModel()

      viewModel.viewState().test {
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

  @Test
  fun `selected style resolves to the matching style`() =
    runTest(mainDispatcher.testDispatcher) {
      fakeRepository.beerStyles = Either.Right(listOf(BeerStyle(id = "style-1", name = "IPA")))
      val viewModel = buildViewModel()

      runCurrent()
      viewModel.onStyleSelected("style-1")
      runCurrent()

      expectThat(viewModel.uiState.value.selectedStyle).isEqualTo(BeerStyle("style-1", "IPA"))
    }

  @Test
  fun `initial style starts one style-only query without debounce`() =
    runTest(mainDispatcher.testDispatcher) {
      val viewModel = buildViewModel(initialStyleId = "style-1")

      viewModel.viewState().test {
        runCurrent()

        expectThat(fakeFactory.createdQueries.toList())
          .isEqualTo(listOf(BeersQuery(styleId = "style-1")))
        cancelAndIgnoreRemainingEvents()
      }
    }

  @Test
  fun `selected style creates a style-only query`() =
    runTest(mainDispatcher.testDispatcher) {
      val viewModel = buildViewModel()

      viewModel.viewState().test {
        expectThat(awaitItem()).isEqualTo(CommonUiState.Empty)

        viewModel.onStyleSelected("style-1")
        runCurrent()

        expectThat(fakeFactory.createdQueries.toList())
          .isEqualTo(listOf(BeersQuery(styleId = "style-1")))
        cancelAndIgnoreRemainingEvents()
      }
    }

  @Test
  fun `search and selected style create a combined query`() =
    runTest(mainDispatcher.testDispatcher) {
      val viewModel = buildViewModel()

      viewModel.viewState().test {
        expectThat(awaitItem()).isEqualTo(CommonUiState.Empty)

        viewModel.onStyleSelected("style-1")
        viewModel.onQueryChange("ipa")
        advanceTimeBy(pastDebounce)
        runCurrent()

        expectThat(fakeFactory.createdQueries.toList())
          .isEqualTo(
            listOf(
              BeersQuery(styleId = "style-1"),
              BeersQuery(search = "ipa", styleId = "style-1"),
            )
          )
        cancelAndIgnoreRemainingEvents()
      }
    }

  @Test
  fun `clearing text preserves selected style and clearing style returns to empty`() =
    runTest(mainDispatcher.testDispatcher) {
      val viewModel = buildViewModel()

      viewModel.viewState().test {
        expectThat(awaitItem()).isEqualTo(CommonUiState.Empty)

        viewModel.onStyleSelected("style-1")
        viewModel.onQueryChange("ipa")
        advanceTimeBy(pastDebounce)
        runCurrent()
        viewModel.onQueryChange("")
        advanceTimeBy(pastDebounce)
        runCurrent()

        expectThat(fakeFactory.createdQueries.last()).isEqualTo(BeersQuery(styleId = "style-1"))

        viewModel.onClearStyle()
        advanceTimeBy(pastDebounce)
        runCurrent()
        expectThat(viewModel.uiState.value.viewState).isEqualTo(CommonUiState.Empty)
        cancelAndIgnoreRemainingEvents()
      }
    }

  @Test
  fun `style loading can be retried`() =
    runTest(mainDispatcher.testDispatcher) {
      fakeRepository.beerStyles = Either.Right(listOf(BeerStyle("style-1", "IPA")))
      val viewModel = buildViewModel()
      runCurrent()

      expectThat(viewModel.uiState.value.styles)
        .isEqualTo(CommonUiState.Success(listOf(BeerStyle("style-1", "IPA"))))
    }
}

private fun BeersSearchViewModel.viewState() = uiState.map { it.viewState }
