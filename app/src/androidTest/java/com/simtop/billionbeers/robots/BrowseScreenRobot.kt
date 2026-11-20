package com.simtop.billionbeers.robots

import androidx.compose.ui.test.junit4.ComposeTestRule
import com.simtop.billionbeers.presentation.android.R
import com.simtop.billionbeers.testing.android.BaseTestRobot

class BrowseScreenRobot(composeTestRule: ComposeTestRule) :
  BaseTestRobot(composeTestRule, supportsActivityRecreation = true) {

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
