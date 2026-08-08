package com.simtop.feature.beerslist

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.beerdomain.fakes.fakeBeerModel
import com.simtop.billionbeers.core.designsystem.theme.BillionBeersTheme
import com.simtop.core.core.CommonUiState
import com.simtop.core.core.PagedListFooter
import com.simtop.core.core.PagedListUiModel
import org.junit.Rule
import org.junit.Test

/**
 * Proves the catalog's *composition-level* saved state actually restores. The screen is full of
 * `rememberSaveable` - `rememberLazyListState()` is one - and nothing exercised the restore path,
 * so "handled" rested entirely on the API being called rather than on it working.
 *
 * The state under test is the scroll position, because it is the only saveable on this screen a
 * user can observe. (`BeersListScreen`'s `dataVisibility` is a `rememberSaveable` that is written
 * and never read - it restores, but no assertion can tell.)
 *
 * **What this cannot cover:** `SavedStateHandle`. [StateRestorationTester] recreates the
 * *composition*, not the `ViewModel`, so a handle-backed value is out of its reach by
 * construction - a different mechanism, proven at a different tier. `BeersListViewModel` holds no
 * handle, so there is nothing here to prove; the one place the handle's *restore* path is exercised
 * is `BeersSearchViewModelTest`, which builds a ViewModel from a pre-populated handle. Read neither
 * as covering the other, and neither as covering the whole project.
 */
class BeersListStateRestorationUiTest {

  @get:Rule val composeTestRule = createComposeRule()

  /**
   * Content goes through the tester, **not** through `composeTestRule.setContent`. The tester only
   * recreates a composition it owns; set the content on the rule instead and
   * `emulateSavedInstanceStateRestore()` is a silent no-op that leaves the test green.
   */
  private val restorationTester = StateRestorationTester(composeTestRule)

  /**
   * Long enough that item 39 is far off-screen on first layout, so the scroll has real distance.
   */
  private val beers: List<Beer> =
    List(TOTAL_BEERS) { index -> fakeBeerModel.copy(id = "$index", name = "Beer $index") }

  @Composable
  private fun Content(items: List<Beer>) {
    BillionBeersTheme {
      BeersListContent(
        viewState =
          CommonUiState.Success(
            PagedListUiModel(
              items = items,
              footer = PagedListFooter.Hidden,
              totalCount = TOTAL_BEERS,
            )
          ),
        onBeerClick = {},
        onSearchClick = {},
        onBrowseClick = {},
        onScrollToBottom = {},
        onRefresh = {},
        onRetry = {},
        onRetryLoadMore = {},
      )
    }
  }

  /**
   * Mutation-probed, because a restore test that cannot fail is the default outcome here - set the
   * content on the rule instead of on the tester and `emulateSavedInstanceStateRestore()` is a
   * silent no-op. With `rememberLazyListState()` swapped for a plain `remember { LazyListState() }`
   * this fails on the post-restore assertion with `'Beer 39' ... is not displayed`; restored, it is
   * green.
   */
  @Test
  fun theScrollPositionSurvivesSavedInstanceStateRestore() {
    val firstPage = beers.take(FIRST_PAGE_SIZE)
    restorationTester.setContent { Content(items = firstPage) }

    beersList(composeTestRule) {
      waitForIdle()
      assertTextIsDisplayed("Beer 0")

      scrollToBeerAt(firstPage.lastIndex)
      assertTextIsDisplayed("Beer ${firstPage.lastIndex}")
    }

    restorationTester.emulateSavedInstanceStateRestore()

    beersList(composeTestRule) {
      waitForIdle()
      assertTextIsDisplayed("Beer ${firstPage.lastIndex}")
    }
  }

  /**
   * Regression test for the bug the restore test above cannot see, and did not see: a scroll
   * position lost while the process is very much alive.
   *
   * The list is composed inside an `AnimatedContent`, which wraps each content in
   * `key(contentKey(state))`. With the default `contentKey` that key **is** the state, so every
   * appended page - an `equals`-different `CommonUiState.Success` - replaced the group holding
   * `rememberLazyListState()`. Nothing was saved and nothing was restored; the state was simply
   * discarded, and the user was dropped back at the top of the catalog on every page load.
   *
   * Measured before the `contentKey = { it::class }` fix: scrolled to item 39, a page arriving left
   * items **0-6** composed. After: **33-40**, the position held and the new page composed below it.
   */
  @Test
  fun theScrollPositionSurvivesANewPageArriving() {
    var items by mutableStateOf(beers.take(FIRST_PAGE_SIZE))
    composeTestRule.setContent { Content(items = items) }

    beersList(composeTestRule) {
      waitForIdle()
      scrollToBeerAt(FIRST_PAGE_SIZE - 1)
      assertTextIsDisplayed("Beer ${FIRST_PAGE_SIZE - 1}")
    }

    composeTestRule.runOnUiThread { items = beers }

    beersList(composeTestRule) {
      waitForIdle()
      assertTextIsDisplayed("Beer ${FIRST_PAGE_SIZE - 1}")
    }
  }

  private companion object {
    const val TOTAL_BEERS = 80
    const val FIRST_PAGE_SIZE = 40
  }
}
