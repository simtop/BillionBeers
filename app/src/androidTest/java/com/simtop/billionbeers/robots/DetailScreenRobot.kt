package com.simtop.billionbeers.robots

import androidx.compose.ui.test.junit4.ComposeTestRule
import com.simtop.presentation_utils.R

fun detailScreen(composeTestRule: ComposeTestRule, func: DetailScreenRobot.() -> Unit) =
  DetailScreenRobot(composeTestRule).apply { func() }

class DetailScreenRobot(composeTestRule: ComposeTestRule) : BaseTestRobot(composeTestRule) {

  fun assertBeerDetailIsDisplayed(beerName: String, beerDescription: String) {
    assertTextIsDisplayed(beerName)
    assertTextIsDisplayed(beerDescription)
  }

  fun clickToggleAvailability() {
    clickOnNodeWithTag("toggle_availability")
  }

  fun assertToggleButtonShowsMarkAsEmpty() {
    assertTextIsDisplayed(string(R.string.mark_as_empty))
  }

  fun waitUntilToggleButtonShowsRefillBarrels() {
    waitUntilNodeWithTextIsDisplayed(string(R.string.refill_barrels))
  }

  fun assertToggleButtonShowsRefillBarrels() {
    assertTextIsDisplayed(string(R.string.refill_barrels))
  }

  fun navigateBack() {
    pressBack()
  }
}
