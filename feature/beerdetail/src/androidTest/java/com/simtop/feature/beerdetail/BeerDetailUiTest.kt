package com.simtop.feature.beerdetail

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.billionbeers.core.designsystem.theme.BillionBeersTheme
import com.simtop.feature.beerdetail.presentation.ComposeBeerDetail
import com.simtop.presentation_utils.R
import com.simtop.testing_utils_android.BaseTestRobot
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class BeerDetailUiTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val beer =
    Beer.empty.copy(
      name = "Punk IPA",
      tagline = "Post Modern Classic.",
      description = "A crisp and bitter IPA.",
      availability = true,
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
    robot.assertNodeWithTagHasStateDescription(
      "toggle_availability",
      string(R.string.beer_available),
    )
    robot.assertEveryClickableIsLabelled()

    robot.clickOnNodeWithTag("toggle_availability")
    robot.assertNodeWithTagHasStateDescription(
      "toggle_availability",
      string(R.string.beer_out_of_stock),
    )
    robot.assertEveryClickableIsLabelled()

    composeTestRule.onNodeWithContentDescription(string(R.string.beer_detail_back)).performClick()
    assertEquals(1, backCount)
  }

  private fun string(resId: Int): String =
    androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
      .targetContext
      .getString(resId)
}
