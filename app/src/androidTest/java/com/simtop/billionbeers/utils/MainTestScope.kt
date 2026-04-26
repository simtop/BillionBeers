package com.simtop.billionbeers.utils

import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.test.core.app.ActivityScenario
import com.simtop.billionbeers.presentation.MainActivity
import com.simtop.billionbeers.robots.HomeScreenRobot
import com.simtop.billionbeers.robots.DetailScreenRobot

fun ComposeTestRule.homeScreen(func: HomeScreenRobot.() -> Unit) {
    HomeScreenRobot(this).apply(func)
}

fun ComposeTestRule.detailScreen(func: DetailScreenRobot.() -> Unit) {
    DetailScreenRobot(this).apply(func)
}

fun runMainActivityTest(
    composeTestRule: ComposeTestRule,
    block: ComposeTestRule.() -> Unit
) {
    ActivityScenario.launch(MainActivity::class.java).use {
        block(composeTestRule)
    }
}
