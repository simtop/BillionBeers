package com.simtop.feature.beersearch

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import com.simtop.billionbeers.core.designsystem.theme.BillionBeersTheme
import com.simtop.core.core.CommonUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * The search screen's input path, which only a device exercises.
 *
 * Deliberately *not* about debouncing: `BeersSearchViewModelTest` already covers that with virtual
 * time (`rapid typing debounces into a single query`), and re-running it here would only make it
 * slower and flakier. What no JVM test can see is the layer beneath - a real IME committing text
 * through the input connection into `TextField`, and a `FocusRequester` fired from a
 * `LaunchedEffect` actually landing on a composed node.
 *
 * The auto-focus case is the one that earns its keep: `requestFocus()` on a node that has not been
 * placed yet throws, and whether it has been placed depends on real composition and layout timing.
 */
class BeersSearchInputUiTest {

  @get:Rule val composeTestRule = createComposeRule()

  /**
   * Mirrors the screen's real contract: the query is hoisted, so the field is only ever as correct
   * as the round-trip.
   */
  private fun setContent(autoFocus: Boolean = true, onQueryChange: (String) -> Unit = {}) {
    composeTestRule.setContent {
      var query by remember { mutableStateOf("") }
      BillionBeersTheme {
        BeersSearchContent(
          viewState = CommonUiState.Empty,
          query = query,
          onQueryChange = {
            query = it
            onQueryChange(it)
          },
          onBeerClick = {},
          onBack = {},
          onScrollToBottom = {},
          onRetryLoadMore = {},
          onRetrySearch = {},
          autoFocus = autoFocus,
        )
      }
    }
  }

  @Test
  fun theSearchFieldTakesFocusOnEntry() {
    setContent(autoFocus = true)

    searchScreen(composeTestRule) { assertSearchFieldIsFocused() }
  }

  /**
   * The screen's controls are icon-only - a back arrow and a clear affordance - so a dropped
   * `contentDescription` leaves them silent to TalkBack while looking untouched in a screenshot.
   * Asserted with text present, because the clear action only composes once the field is non-empty.
   */
  @Test
  fun everyControlIsReachableByAScreenReader() {
    setContent()

    searchScreen(composeTestRule) {
      assertEveryClickableIsLabelled()

      typeQuery("stout")
      assertClearActionIsPresent()
      assertEveryClickableIsLabelled()
    }
  }

  @Test
  fun typingThroughTheImeReachesTheQueryCallback() {
    val typed = mutableListOf<String>()
    setContent(onQueryChange = { typed += it })

    searchScreen(composeTestRule) {
      typeQuery("punk")
      assertQueryIsDisplayed("punk")
    }

    // performTextInput commits through the real input connection, so the callback sees the whole
    // committed string rather than one emission per character.
    assertEquals("probe-mutation", typed.last())
  }

  /**
   * The clear affordance is conditional on `query.isNotEmpty()`, so it is a real piece of state
   * wiring rather than a static icon - and it is the only way back to the pre-search prompt without
   * leaving the screen.
   */
  @Test
  fun theClearActionAppearsWithTextAndEmptiesTheField() {
    setContent()

    searchScreen(composeTestRule) {
      assertClearActionIsAbsent()

      typeQuery("stout")
      assertQueryIsDisplayed("stout")
      assertClearActionIsPresent()

      clearQuery()
      assertClearActionIsAbsent()
    }
  }
}
