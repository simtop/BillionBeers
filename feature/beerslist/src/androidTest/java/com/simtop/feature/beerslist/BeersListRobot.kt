package com.simtop.feature.beerslist

import androidx.compose.ui.test.junit4.ComposeTestRule
import com.simtop.presentation_utils.R as PresentationUtilsR
import com.simtop.testing_utils_android.BaseTestRobot

/** The screen's own tag, kept beside the robot that addresses it rather than inside one test. */
private const val BEER_LIST_TAG = "beer_list"

fun beersList(composeTestRule: ComposeTestRule, func: BeersListRobot.() -> Unit) =
  BeersListRobot(composeTestRule).apply { func() }

class BeersListRobot(composeTestRule: ComposeTestRule) : BaseTestRobot(composeTestRule) {

  fun scrollToBeerAt(index: Int) {
    scrollToIndexInNodeWithTag(BEER_LIST_TAG, index)
  }

  fun assertLoadMoreFailedFooterIsDisplayed() {
    assertTextIsDisplayed(string(PresentationUtilsR.string.paged_list_load_more_failed))
  }
}
