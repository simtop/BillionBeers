package com.simtop.billionbeers.utils

import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.test.core.app.ActivityScenario
import com.simtop.billionbeers.presentation.MainActivity
import com.simtop.billionbeers.robots.BrowseScreenRobot
import com.simtop.billionbeers.robots.DetailScreenRobot
import com.simtop.billionbeers.robots.HomeScreenRobot
import com.simtop.billionbeers.robots.SearchScreenRobot
import com.simtop.testing_utils_android.FailureScreenshotRule

fun ComposeTestRule.homeScreen(func: HomeScreenRobot.() -> Unit) {
  HomeScreenRobot(this).apply(func)
}

fun ComposeTestRule.detailScreen(func: DetailScreenRobot.() -> Unit) {
  DetailScreenRobot(this).apply(func)
}

fun ComposeTestRule.browseScreen(func: BrowseScreenRobot.() -> Unit) {
  BrowseScreenRobot(this).apply(func)
}

fun ComposeTestRule.searchScreen(func: SearchScreenRobot.() -> Unit) {
  SearchScreenRobot(this).apply(func)
}

fun runMainActivityTest(composeTestRule: ComposeTestRule, block: ComposeTestRule.() -> Unit) {
  ActivityScenario.launch(MainActivity::class.java).useWithFailureScreenshot {
    block(composeTestRule)
  }
}

fun ActivityScenario<MainActivity>.useWithFailureScreenshot(
  block: (ActivityScenario<MainActivity>) -> Unit
) {
  use { scenario ->
    try {
      block(scenario)
    } catch (failure: Throwable) {
      FailureScreenshotRule.captureBeforeActivityClose(failure)
      throw failure
    }
  }
}
