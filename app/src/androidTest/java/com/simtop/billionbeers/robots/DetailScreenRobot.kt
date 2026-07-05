package com.simtop.billionbeers.robots

import androidx.compose.ui.test.junit4.ComposeTestRule

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
    assertTextIsDisplayed("Mark as Empty")
  }

  fun assertToggleButtonShowsRefillBarrels() {
    assertTextIsDisplayed("Refill Barrels")
  }

  fun navigateBack() {
    pressBack()
  }
}
