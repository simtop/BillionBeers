@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.simtop.feature.beerdetail

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.billionbeers.core.designsystem.theme.BillionBeersTheme
import com.simtop.feature.beerdetail.presentation.ComposeBeerDetail
import com.simtop.feature.beerdetail.presentation.DetailScrolledDesignReviewPreview
import com.simtop.presentation_utils.R
import com.simtop.testing_utils_android.BaseTestRobot
import com.simtop.testing_utils_android.withFailureScreenshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class BeerDetailUiTest {

  private val composeTestRule = createComposeRule()
  @get:Rule val failureScreenshots = withFailureScreenshot(composeTestRule)

  private val beer =
    Beer.empty.copy(
      name = "Punk IPA",
      tagline = "Post Modern Classic.",
      description = "A crisp and bitter IPA.",
      availability = true,
      minServingTemperature = 4,
      maxServingTemperature = 4,
      foodPairing = listOf("Spicy chicken tikka masala"),
      ingredients = listOf("Pale malt"),
      recommendedGlasses = listOf("Pint glass"),
    )

  @Test
  fun detailScreenExposesContentAndAvailabilityState() {
    var backCount = 0

    composeTestRule.setContent {
      var currentBeer by remember { mutableStateOf(beer) }
      BillionBeersTheme {
        ComposeBeerDetail(
          beer = currentBeer,
          onBackClick = { backCount++ },
          onToggleAvailability = {
            currentBeer = currentBeer.copy(availability = !currentBeer.availability)
          },
        )
      }
    }

    val robot = BaseTestRobot(composeTestRule)
    robot.assertTextIsDisplayed(beer.name)
    robot.assertTextIsDisplayed(beer.description)
    robot.assertTextIsDisplayed("4°C")
    robot.assertNodeWithTagHasStateDescription(
      "toggle_availability",
      string(R.string.beer_available),
    )
    robot.assertInteractiveNodesAreAccessible()

    robot.clickOnNodeWithTag("toggle_availability")
    robot.assertNodeWithTagHasStateDescription(
      "toggle_availability",
      string(R.string.beer_out_of_stock),
    )
    robot.assertInteractiveNodesAreAccessible()

    composeTestRule.onNodeWithContentDescription(string(R.string.beer_detail_back)).performClick()
    assertEquals(1, backCount)
  }

  @Test
  fun scrollingReleasesHeaderSpaceAndPreservesControlsAndRestoration() {
    verifyCollapse(fontScale = 1f, heightDp = 760)
  }

  @Test
  fun collapseWorksWithLargeTextInShortWindow() {
    verifyCollapse(fontScale = 2f, heightDp = 400)
  }

  @Test
  fun scrolledReferenceShowsEndOfBodyUnderCompactHeader() {
    verifyScrolledReference(fontScale = 1f)
  }

  @Test
  fun largeTextScrolledReferenceShowsEndOfBodyUnderCompactHeader() {
    verifyScrolledReference(fontScale = 1.5f)
  }

  private fun verifyScrolledReference(fontScale: Float) {
    composeTestRule.setContent {
      val density = LocalDensity.current.density
      CompositionLocalProvider(LocalDensity provides Density(density, fontScale)) {
        Box(Modifier.width(360.dp).height(760.dp)) {
          DetailScrolledDesignReviewPreview()
        }
      }
    }
    val robot = BaseTestRobot(composeTestRule)
    robot.assertTextIsDisplayed("Pint glass")
    robot.assertTextIsDisplayed("Tulip")
    composeTestRule.onNodeWithText("A Real Bitter Experience.").assertIsNotDisplayed()
    val header = composeTestRule.onNodeWithTag("detail_header").getUnclippedBoundsInRoot()
    assertTrue("The scrolled reference uses the compact bar", header.bottom - header.top <= 100.dp)
    val range =
      composeTestRule
        .onNodeWithTag("detail_scroll_view")
        .fetchSemanticsNode()
        .config[SemanticsProperties.VerticalScrollAxisRange]
    assertTrue("The reference body must actually scroll", range.value() > 0f)
    assertEquals(range.maxValue(), range.value(), 1f)
  }

  private fun verifyCollapse(fontScale: Float, heightDp: Int) {
    var backCount = 0
    var favoriteCount = 0
    val restoration = StateRestorationTester(composeTestRule)
    restoration.setContent {
      val density = LocalDensity.current.density
      CompositionLocalProvider(LocalDensity provides Density(density, fontScale)) {
        BillionBeersTheme {
          Box(Modifier.width(360.dp).height(heightDp.dp)) {
            ComposeBeerDetail(
              beer =
                beer.copy(description = List(40) { "A crisp and bitter IPA." }.joinToString(" ")),
              onBackClick = { backCount++ },
              onToggleAvailability = {},
              onToggleFavorite = { favoriteCount++ },
            )
          }
        }
      }
    }
    val header = composeTestRule.onNodeWithTag("detail_header")
    val content = composeTestRule.onNodeWithTag("detail_scroll_view")
    val expanded = header.getUnclippedBoundsInRoot().run { bottom - top }
    val contentTop = content.getUnclippedBoundsInRoot().top
    content.performTouchInput { swipeUp(durationMillis = 600) }
    composeTestRule.waitForIdle()
    val collapsed = header.getUnclippedBoundsInRoot().run { bottom - top }
    val bodyOffset =
      content.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
    assertTrue("The body must scroll after the header collapses", bodyOffset > 0f)
    assertTrue("Header must release space", collapsed < expanded - 40.dp)
    assertTrue(
      "Body must move into released space",
      content.getUnclippedBoundsInRoot().top < contentTop - 40.dp,
    )
    assertTrue("Compact bar stays within its controls and status inset", collapsed <= 100.dp)
    BaseTestRobot(composeTestRule).assertInteractiveNodesAreAccessible()
    composeTestRule.onNodeWithTag("toggle_favorite").performClick()
    composeTestRule.onNodeWithContentDescription(string(R.string.beer_detail_back)).performClick()
    assertEquals(1, favoriteCount)
    assertEquals(1, backCount)
    restoration.emulateSavedInstanceStateRestore()
    composeTestRule.waitForIdle()
    assertEquals(collapsed.value, header.getUnclippedBoundsInRoot().run { bottom - top }.value, 1f)
    assertEquals(
      bodyOffset,
      content.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value(),
      1f,
    )
    repeat(6) { content.performTouchInput { swipeDown(durationMillis = 600) } }
    composeTestRule.waitForIdle()
    assertEquals(expanded.value, header.getUnclippedBoundsInRoot().run { bottom - top }.value, 1f)
  }

  private fun string(resId: Int): String =
    androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
      .targetContext
      .getString(resId)
}
