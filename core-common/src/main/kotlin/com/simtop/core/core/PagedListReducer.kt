package com.simtop.core.core

/** The bottom-of-list affordance for a paged list: nothing, a retry row, or the end caption. */
sealed interface PagedListFooter {
  data object Hidden : PagedListFooter

  data object Retry : PagedListFooter

  data object EndReached : PagedListFooter
}

/** What every paged-list screen renders: the items plus the transient paging affordances. */
data class PagedListUiModel<T>(
  val items: List<T> = emptyList(),
  val isLoadingNextPage: Boolean = false,
  val isRefreshing: Boolean = false,
  val footer: PagedListFooter = PagedListFooter.Hidden,
  // Server-reported total (X-Total-Count); null until the server reports it. Renders "N of M"
  // or "M results".
  val totalCount: Int? = null,
)

/**
 * The one [PagingState] -> screen-state mapping shared by every paged list, replacing the
 * near-identical reducers each ViewModel used to hand-roll. Feed it each `(items, pagingState)`
 * pair (typically `combine(pager.data, pager.pagingState, reducer::reduce)`); it is pure over its
 * inputs except for latching the last server-reported total, so "N of M" survives emissions that
 * don't carry it.
 *
 * Shared semantics:
 * - No items yet: Loading while anything is pending, [CommonUiState.Error] on a first-page failure,
 *   [endedEmpty] when pagination ends with nothing (screens disagree here - a catalog is *empty*, a
 *   search has *no results* - hence the parameter).
 * - Items on screen stay on screen: a first-page load is a refresh in progress
 *   ([PagedListUiModel.isRefreshing]), a failed refresh keeps the list (feedback is the caller's
 *   one-shot event; repeating the refresh is the retry), and only a failed *load more* shows
 *   [PagedListFooter.Retry].
 *
 * One instance per pager: it carries the latched total, so it must live and die with the pager
 * whose states it reduces (a new search term means a new reducer).
 */
class PagedListReducer<T, E : Any>(
  private val errorState: (E) -> CommonUiState.Error,
  private val endedEmpty: () -> CommonUiState<PagedListUiModel<T>> = { CommonUiState.Empty },
) {

  private var lastTotalCount: Int? = null

  fun reduce(items: List<T>, state: PagingState<E>): CommonUiState<PagedListUiModel<T>> {
    if (state is PagingState.Success) state.totalCount?.let { lastTotalCount = it }
    return when (state) {
      PagingState.Idle -> if (items.isEmpty()) CommonUiState.Loading else success(items)
      PagingState.Loading ->
        // With items already on screen, a first-page load is a refresh in progress.
        if (items.isEmpty()) CommonUiState.Loading else success(items, isRefreshing = true)
      PagingState.LoadingNextPage ->
        if (items.isEmpty()) CommonUiState.Loading else success(items, isLoadingNextPage = true)
      is PagingState.Success ->
        // Success with no items yet: a DB-backed data flow may emit just after the state does, so
        // hold Loading until the rows arrive.
        if (items.isEmpty()) CommonUiState.Loading else success(items)
      PagingState.EndOfPagination ->
        if (items.isEmpty()) endedEmpty() else success(items, footer = PagedListFooter.EndReached)
      is PagingState.Error ->
        when {
          items.isEmpty() -> errorState(state.error)
          // A failed refresh keeps the list with no footer: the load-more retry row would carry
          // the wrong copy and target the wrong load - repeating the refresh is the retry.
          state.isFirstPage -> success(items)
          else -> success(items, footer = PagedListFooter.Retry)
        }
    }
  }

  private fun success(
    items: List<T>,
    isLoadingNextPage: Boolean = false,
    isRefreshing: Boolean = false,
    footer: PagedListFooter = PagedListFooter.Hidden,
  ) =
    CommonUiState.Success(
      PagedListUiModel(items, isLoadingNextPage, isRefreshing, footer, lastTotalCount)
    )
}
