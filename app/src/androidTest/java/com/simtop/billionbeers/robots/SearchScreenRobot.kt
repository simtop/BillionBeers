package com.simtop.billionbeers.robots

import androidx.compose.ui.test.junit4.ComposeTestRule
import com.simtop.feature.beersearch.R
import com.simtop.feature.beersearch.SEARCH_FIELD_TAG
import com.simtop.testing_utils_android.BaseTestRobot

fun searchScreen(composeTestRule: ComposeTestRule, func: SearchScreenRobot.() -> Unit) =
  SearchScreenRobot(composeTestRule).apply { func() }

class SearchScreenRobot(composeTestRule: ComposeTestRule) : BaseTestRobot(composeTestRule) {

  fun assertSearchFieldIsDisplayed() {
    assertNodeWithTagIsDisplayed(SEARCH_FIELD_TAG)
  }

  fun assertBackButtonIsDisplayed() {
    assertNodeWithContentDescriptionIsDisplayed(string(R.string.search_back))
  }
}
