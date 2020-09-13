package com.simtop.billionbeers.robots

fun detailScreen(func: DetailScreenRobot.() -> Unit) = DetailScreenRobot()
    .apply { func() }

open class DetailScreenRobot : BaseTestRobot() {

}