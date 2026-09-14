package com.simtop.core.core

import org.junit.Assert.assertEquals
import org.junit.Test

class PagedListReducerTest {

  private fun reducer(
    endedEmpty: () -> CommonUiState<PagedListUiModel<String>> = { CommonUiState.Empty }
  ) =
    PagedListReducer<String, String>(
      errorState = { CommonUiState.Error("error: $it") },
      endedEmpty = endedEmpty,
    )

  private val items = listOf("a", "b")

  @Test
  fun `nothing loaded yet is Loading for Idle, Loading and Success states`() {
    val reducer = reducer()

    assertEquals(CommonUiState.Loading, reducer.reduce(emptyList(), PagingState.Idle))
    assertEquals(CommonUiState.Loading, reducer.reduce(emptyList(), PagingState.Loading))
    // Success with no items yet: a DB-backed data flow may emit just after the state does.
    assertEquals(CommonUiState.Loading, reducer.reduce(emptyList(), PagingState.Success()))
  }

  @Test
  fun `items with an idle or successful pager are a plain Success`() {
    val reducer = reducer()

    assertEquals(
      CommonUiState.Success(PagedListUiModel(items)),
      reducer.reduce(items, PagingState.Idle), // warm cache, nothing fetched yet
    )
    assertEquals(
      CommonUiState.Success(PagedListUiModel(items)),
      reducer.reduce(items, PagingState.Success()),
    )
  }

  @Test
  fun `a first-page load over existing items is a refresh in progress`() {
    val state = reducer().reduce(items, PagingState.Loading)

    assertEquals(CommonUiState.Success(PagedListUiModel(items, isRefreshing = true)), state)
  }

  @Test
  fun `loading the next page flags the footer spinner`() {
    val state = reducer().reduce(items, PagingState.LoadingNextPage)

    assertEquals(CommonUiState.Success(PagedListUiModel(items, isLoadingNextPage = true)), state)
  }

  @Test
  fun `the server total is latched across later reductions`() {
    val reducer = reducer()

    reducer.reduce(items, PagingState.Success(totalCount = 206))
    val later = reducer.reduce(items, PagingState.LoadingNextPage)

    assertEquals(
      CommonUiState.Success(PagedListUiModel(items, isLoadingNextPage = true, totalCount = 206)),
      later,
    )
  }

  @Test
  fun `end of pagination with items shows the end footer`() {
    val state = reducer().reduce(items, PagingState.EndOfPagination())

    assertEquals(
      CommonUiState.Success(PagedListUiModel(items, footer = PagedListFooter.EndReached)),
      state,
    )
  }

  // A single-page surface (browse-by-style, a short search) goes straight to EndOfPagination
  // without ever passing through Success - the total it carries must still reach the ui model,
  // or those screens can never render their "N results" header.
  @Test
  fun `a total carried by end of pagination is latched like one from Success`() {
    val state = reducer().reduce(items, PagingState.EndOfPagination(totalCount = 14))

    assertEquals(
      CommonUiState.Success(
        PagedListUiModel(items, footer = PagedListFooter.EndReached, totalCount = 14)
      ),
      state,
    )
  }

  @Test
  fun `end of pagination with nothing is Empty by default`() {
    val state = reducer().reduce(emptyList(), PagingState.EndOfPagination())

    assertEquals(CommonUiState.Empty, state)
  }

  @Test
  fun `end of pagination with nothing honours the endedEmpty override`() {
    // Search: an empty result set is a Success (the "no results" hint), not the Empty prompt.
    val noResults = CommonUiState.Success(PagedListUiModel<String>())
    val reducer = reducer(endedEmpty = { noResults })

    assertEquals(noResults, reducer.reduce(emptyList(), PagingState.EndOfPagination()))
  }

  @Test
  fun `a failure with nothing on screen is the full-screen error`() {
    val state = reducer().reduce(emptyList(), PagingState.Error("boom", isFirstPage = true))

    assertEquals(CommonUiState.Error("error: boom"), state)
  }

  @Test
  fun `a failed refresh keeps the list with no footer`() {
    val state = reducer().reduce(items, PagingState.Error("boom", isFirstPage = true))

    assertEquals(CommonUiState.Success(PagedListUiModel(items)), state)
  }

  @Test
  fun `a failed load-more keeps the list and shows the retry footer`() {
    val state = reducer().reduce(items, PagingState.Error("boom", isFirstPage = false))

    assertEquals(
      CommonUiState.Success(PagedListUiModel(items, footer = PagedListFooter.Retry)),
      state,
    )
  }
}
