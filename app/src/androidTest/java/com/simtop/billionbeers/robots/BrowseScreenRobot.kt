package com.simtop.billionbeers.robots

import androidx.compose.ui.test.junit4.ComposeTestRule
import com.simtop.presentation_utils.R
import com.simtop.testing_utils_android.BaseTestRobot

class BrowseScreenRobot(composeTestRule: ComposeTestRule) : BaseTestRobot(composeTestRule) {

  fun assertBrowseTitleIsDisplayed() {
    assertTextIsDisplayed(string(R.string.browse_title))
  }

  fun assertStyleIsDisplayed(styleName: String) {
    assertTextIsDisplayed(styleName)
  }

  fun clickOnBreweriesTab() {
    clickOnNodeWithText(string(R.string.browse_tab_breweries))
  }

  fun assertBreweriesTabIsSelected() {
    assertTextIsSelected(string(R.string.browse_tab_breweries))
  }

  fun assertBreweryIsDisplayed(breweryName: String) {
    assertTextIsDisplayed(breweryName)
  }

  fun clickOnStyle(styleName: String) {
    clickOnNodeWithText(styleName)
  }
}
