package com.simtop.testing_utils_android

import androidx.annotation.StringRes
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry

open class BaseTestRobot(private val composeTestRule: ComposeTestRule) {

  fun clickOnNodeWithTag(testTag: String) {
    composeTestRule.onNodeWithTag(testTag).performClick()
  }

  fun clickOnNodeWithText(text: String) {
    composeTestRule.onNodeWithText(text).performClick()
  }

  fun clickOnNodeWithContentDescription(label: String) {
    composeTestRule.onNodeWithContentDescription(label).performClick()
  }

  fun assertTextIsDisplayed(text: String) {
    composeTestRule.onNodeWithText(text).assertIsDisplayed()
  }

  fun assertNodeWithTagIsDisplayed(testTag: String) {
    composeTestRule.onNodeWithTag(testTag).assertIsDisplayed()
  }

  fun assertNodeWithTextsIsDisplayed(vararg texts: String) {
    composeTestRule.onNode(texts.map(::hasText).reduce(SemanticsMatcher::and)).assertIsDisplayed()
  }

  /**
   * Types through the real input connection, one commit per call - not a state assignment. That is
   * the point of doing it on a device: it exercises the IME path a user actually goes through.
   */
  fun typeIntoNodeWithTag(testTag: String, text: String) {
    composeTestRule.onNodeWithTag(testTag).performTextInput(text)
  }

  fun assertNodeWithTagIsFocused(testTag: String) {
    composeTestRule.onNodeWithTag(testTag).assertIsFocused()
  }

  fun assertNodeWithContentDescriptionIsDisplayed(label: String) {
    composeTestRule.onNodeWithContentDescription(label).assertIsDisplayed()
  }

  fun assertNodeWithContentDescriptionDoesNotExist(label: String) {
    composeTestRule.onNodeWithContentDescription(label).assertDoesNotExist()
  }

  fun string(@StringRes resId: Int): String =
    InstrumentationRegistry.getInstrumentation().targetContext.getString(resId)

  @OptIn(ExperimentalTestApi::class)
  fun waitUntilNodeWithTextIsDisplayed(text: String, timeoutMillis: Long = 5000) {
    composeTestRule.waitUntilExactlyOneExists(hasText(text), timeoutMillis)
  }

  fun pressBack() {
    Espresso.pressBack()
    composeTestRule.waitForIdle()
  }
}
