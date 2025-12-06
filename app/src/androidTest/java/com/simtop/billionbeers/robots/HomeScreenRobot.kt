package com.simtop.billionbeers.robots

import androidx.compose.ui.test.junit4.ComposeTestRule

fun homeScreen(composeTestRule: ComposeTestRule, func: HomeScreenRobot.() -> Unit) =
  HomeScreenRobot(composeTestRule).apply { func() }

class HomeScreenRobot(composeTestRule: ComposeTestRule) : BaseTestRobot(composeTestRule) {

  fun assertBeerNameIsDisplayed(beerName: String) {
    assertTextIsDisplayed(beerName)
  }

  fun clickOnBeer(beerName: String) {
    clickOnNodeWithText(beerName)
  }
}
