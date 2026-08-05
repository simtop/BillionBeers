package com.simtop.feature.beerslist

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToIndex
import androidx.test.platform.app.InstrumentationRegistry
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.beerdomain.fakes.fakeBeerModel
import com.simtop.billionbeers.core.designsystem.theme.BillionBeersTheme
import com.simtop.core.core.CommonUiState
import com.simtop.core.core.PagedListFooter
import com.simtop.core.core.PagedListUiModel
import com.simtop.presentation_utils.R as PresentationUtilsR
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * The half of load-more that a JVM test cannot reach: a real `LazyColumn` laying itself out and
 * feeding [com.simtop.presentation_utils.core.InfiniteListHandler] through `snapshotFlow`.
 *
 * The signal logic itself is *not* retested here - `InfiniteListHandlerTest` in
 * `:presentation_utils` already covers it with seven cases over synthetic `ListPosition` values,
 * including the count-based dedup that PR #70 fixed. What those cannot see is whether the handler
 * is wired to the list at all, whether it is wired to the *same* `LazyListState` the `LazyColumn`
 * uses, and whether the screen's own suppression rule holds. Those need real layout, so they live
 * here.
 */
class BeersListPagingUiTest {

  @get:Rule val composeTestRule = createComposeRule()

  /**
   * Long enough that the bottom is well off-screen on first layout - the tests assert the callback
   * has *not* fired before scrolling, which is what proves they are testing the scroll rather than
   * a list that was already at its end.
   */
  private val beers: List<Beer> =
    List(40) { index -> fakeBeerModel.copy(id = "$index", name = "Beer $index") }

  private val lastBeerIndex = beers.lastIndex

  private fun string(resId: Int): String =
    InstrumentationRegistry.getInstrumentation().targetContext.getString(resId)

  private fun setContent(footer: PagedListFooter, onScrollToBottom: () -> Unit) {
    composeTestRule.setContent { Content(footer = footer, onScrollToBottom = onScrollToBottom) }
  }

  @Composable
  private fun Content(footer: PagedListFooter, onScrollToBottom: () -> Unit) {
    BillionBeersTheme {
      BeersListContent(
        viewState =
          CommonUiState.Success(
            PagedListUiModel(items = beers, footer = footer, totalCount = beers.size)
          ),
        onBeerClick = {},
        onSearchClick = {},
        onBrowseClick = {},
        onScrollToBottom = onScrollToBottom,
        onRefresh = {},
        onRetry = {},
        onRetryLoadMore = {},
      )
    }
  }

  @Test
  fun scrollingToTheBottomRequestsTheNextPage() {
    var loadMoreCount = 0
    setContent(PagedListFooter.Hidden) { loadMoreCount++ }

    composeTestRule.waitForIdle()
    assertEquals(
      "load-more fired before any scroll - the list was already at its end",
      0,
      loadMoreCount,
    )

    composeTestRule.onNodeWithTag(BEER_LIST_TAG).performScrollToIndex(lastBeerIndex)

    // The callback runs from a LaunchedEffect collecting a snapshotFlow, so it can land after the
    // scroll returns. Waiting on the condition rather than asserting straight away is what keeps
    // this from passing on a warm local device and flaking on a CI runner.
    composeTestRule.waitUntil(WAIT_TIMEOUT_MS) { loadMoreCount > 0 }

    assertEquals(1, loadMoreCount)
  }

  /**
   * The rule at `BeersListScreen`'s `footer !is PagedListFooter.Retry` guard: after a failed load
   * the user is parked at the bottom, so auto-load has to stand down and let the Retry button be
   * the only way forward. Otherwise scrolling away and back silently re-fires the request the user
   * was just told had failed.
   *
   * Structured so it cannot pass vacuously: it asserts the retry footer is on screen after the
   * scroll, which proves the gesture actually reached the bottom. A negative assertion alone would
   * also "pass" if the scroll had done nothing at all.
   */
  @Test
  fun theRetryFooterSuspendsAutoLoad() {
    var loadMoreCount = 0
    setContent(PagedListFooter.Retry) { loadMoreCount++ }

    composeTestRule.waitForIdle()
    assertEquals(0, loadMoreCount)

    // One past the last beer: the retry footer is its own item in the lazy list.
    composeTestRule.onNodeWithTag(BEER_LIST_TAG).performScrollToIndex(lastBeerIndex + 1)
    composeTestRule.waitForIdle()

    composeTestRule
      .onNodeWithText(string(PresentationUtilsR.string.paged_list_load_more_failed))
      .assertIsDisplayed()

    assertEquals(
      "auto-load fired while the retry footer was up - the user must drive this with Retry",
      0,
      loadMoreCount,
    )
  }

  private companion object {
    const val BEER_LIST_TAG = "beer_list"
    const val WAIT_TIMEOUT_MS = 5_000L
  }
}
