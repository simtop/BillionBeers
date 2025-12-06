package com.simtop.billionbeers.robots

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick

open class BaseTestRobot(private val composeTestRule: ComposeTestRule) {

  fun clickOnNodeWithTag(testTag: String) {
    composeTestRule.onNodeWithTag(testTag).performClick()
  }

  fun clickOnNodeWithText(text: String) {
    composeTestRule.onNodeWithText(text).performClick()
  }

  fun assertTextIsDisplayed(text: String) {
    composeTestRule.onNodeWithText(text).assertIsDisplayed()
  }

  fun assertNodeWithTagIsDisplayed(testTag: String) {
    composeTestRule.onNodeWithTag(testTag).assertIsDisplayed()
  }

  @OptIn(ExperimentalTestApi::class)
  fun waitUntilNodeWithTextIsDisplayed(text: String, timeoutMillis: Long = 5000) {
    composeTestRule.waitUntilExactlyOneExists(hasText(text), timeoutMillis)
  }
}
