package com.simtop.billionbeers.robots

import androidx.compose.ui.test.junit4.ComposeTestRule
import com.simtop.feature.beerslist.R as BeersListR
import com.simtop.presentation_utils.R
import com.simtop.testing_utils_android.BaseTestRobot

fun homeScreen(composeTestRule: ComposeTestRule, func: HomeScreenRobot.() -> Unit) =
  HomeScreenRobot(composeTestRule).apply { func() }

class HomeScreenRobot(composeTestRule: ComposeTestRule) : BaseTestRobot(composeTestRule) {

  fun clickOnBrowse() {
    clickOnNodeWithContentDescription(string(BeersListR.string.beers_browse))
  }

  fun assertBeerNameIsDisplayed(beerName: String) {
    assertTextIsDisplayed(beerName)
  }

  fun assertBeerListIsDisplayed() {
    assertNodeWithTagIsDisplayed("beer_list")
  }

  fun clickOnBeer(beerName: String) {
    clickOnNodeWithText(beerName)
  }

  fun assertBeerIsAvailable(beerName: String) {
    assertNodeWithTextsIsDisplayed(beerName, string(R.string.beer_available))
  }

  fun assertBeerIsUnavailable(beerName: String) {
    assertNodeWithTextsIsDisplayed(beerName, string(R.string.beer_out_of_stock))
  }
}
