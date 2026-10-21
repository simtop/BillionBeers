package com.simtop.feature.beersearch

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.platform.app.InstrumentationRegistry
import com.simtop.beerdomain.domain.models.BeerStyle
import com.simtop.beerdomain.domain.models.BeersQuery
import com.simtop.billionbeers.core.designsystem.theme.BillionBeersTheme
import com.simtop.core.core.CommonUiState
import com.simtop.testing_utils_android.withFailureScreenshot
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

  private val composeTestRule = createComposeRule()
  @get:Rule val failureScreenshots = withFailureScreenshot(composeTestRule)

  /**
   * Mirrors the screen's real contract: the query is hoisted, so the field is only ever as correct
   * as the round-trip.
   */
  private fun setContent(
    autoFocus: Boolean = true,
    styles: CommonUiState<List<BeerStyle>> = CommonUiState.Empty,
    onQueryChange: (String) -> Unit = {},
    onStyleChange: (String?) -> Unit = {},
  ) {
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
          styles = styles,
          onStyleChange = onStyleChange,
          onBeerClick = {},
          onBack = {},
          onScrollToBottom = {},
          onRetryLoadMore = {},
          onRetrySearch = {},
          onSaveQuery = { _, _ -> false },
          autoFocus = autoFocus,
        )
      }
    }
  }

  @Test
  fun expandedSavePreviewShowsTheHeaderActionAndNameForm() {
    composeTestRule.setContent { SearchSaveFilterPreview() }
    composeTestRule.onNodeWithTag("filter_preset_toggle").assertIsDisplayed()
    composeTestRule.onNodeWithTag("filter_preset_save").assertIsDisplayed()
  }

  @Test
  fun pendingSaveDoesNotStealFocusAfterTheFormWasClosed() {
    val completion = CompletableDeferred<Boolean>()
    var backCount = 0
    var saveCompleted = false
    composeTestRule.setContent {
      var query by remember { mutableStateOf("ipa") }
      BillionBeersTheme {
        BeersSearchContent(
          viewState = CommonUiState.Empty,
          query = query,
          onQueryChange = { query = it },
          activeQuery = BeersQuery(search = "ipa"),
          onBeerClick = {},
          onBack = { backCount++ },
          onSaveQuery = { _, _ -> completion.await().also { saveCompleted = true } },
          onScrollToBottom = {},
          onRetryLoadMore = {},
          onRetrySearch = {},
          autoFocus = false,
        )
      }
    }
    composeTestRule.onNodeWithTag("filter_preset_toggle").performClick()
    composeTestRule
      .onNode(hasSetTextAction() and !hasTestTag(SEARCH_FIELD_TAG))
      .performTextReplacement("Weekend beers")
    composeTestRule.onNodeWithTag("filter_preset_save").performClick()
    val back =
      InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.search_back)
    composeTestRule.onNodeWithContentDescription(back).performClick()
    composeTestRule.onNodeWithTag("filter_preset_save").assertDoesNotExist()
    val search = composeTestRule.onNodeWithTag(SEARCH_FIELD_TAG)
    search.performClick().performTextReplacement("ipa revised")
    search.assertIsFocused()
    composeTestRule.runOnIdle { completion.complete(true) }
    composeTestRule.runOnIdle { assertTrue(saveCompleted) }
    search.assertIsFocused()
    assertEquals(0, backCount)
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
      assertInteractiveNodesAreAccessible()

      typeQuery("stout")
      assertClearActionIsPresent()
      assertInteractiveNodesAreAccessible()
    }
  }

  @Test
  fun selectingAStyleReachesTheStyleCallback() {
    val selectedStyles = mutableListOf<String?>()
    setContent(
      styles = CommonUiState.Success(listOf(BeerStyle(id = "ipa", name = "IPA"))),
      onStyleChange = { selectedStyles += it },
    )

    searchScreen(composeTestRule) {
      assertStyleFilterIsDisplayed()
      openStyleFilter()
      selectStyle("IPA")
    }

    assertEquals(listOf("ipa"), selectedStyles)
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
    assertEquals("punk", typed.last())
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
