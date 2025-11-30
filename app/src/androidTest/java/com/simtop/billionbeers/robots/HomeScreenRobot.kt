package com.simtop.billionbeers.robots

fun homeScreen(func: HomeScreenRobot.() -> Unit) = HomeScreenRobot()
    .apply { func() }

open class HomeScreenRobot : BaseTestRobot() {
}