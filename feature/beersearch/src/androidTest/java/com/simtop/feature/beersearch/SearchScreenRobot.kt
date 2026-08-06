package com.simtop.feature.beersearch

import androidx.compose.ui.test.junit4.ComposeTestRule
import com.simtop.testing_utils_android.BaseTestRobot

fun searchScreen(composeTestRule: ComposeTestRule, func: SearchScreenRobot.() -> Unit) =
  SearchScreenRobot(composeTestRule).apply { func() }

class SearchScreenRobot(composeTestRule: ComposeTestRule) : BaseTestRobot(composeTestRule) {

  fun typeQuery(text: String) {
    typeIntoNodeWithTag(SEARCH_FIELD_TAG, text)
  }

  fun assertSearchFieldIsFocused() {
    assertNodeWithTagIsFocused(SEARCH_FIELD_TAG)
  }

  fun assertQueryIsDisplayed(text: String) {
    assertTextIsDisplayed(text)
  }

  fun clearQuery() {
    clickOnNodeWithContentDescription(string(R.string.search_clear))
  }

  fun assertClearActionIsPresent() {
    assertNodeWithContentDescriptionIsDisplayed(string(R.string.search_clear))
  }

  fun assertClearActionIsAbsent() {
    assertNodeWithContentDescriptionDoesNotExist(string(R.string.search_clear))
  }
}
