package com.simtop.billionbeers.robots

fun detailScreen(func: DetailScreenRobot.() -> Unit) = DetailScreenRobot()
    .apply { func() }

open class DetailScreenRobot : BaseTestRobot() {
    fun matchViewWithText(resId: Int, text: String) = matchText(resId, text)

    fun swipeUp(resId: Int) = swipeUpScrollView(resId)

    fun clickAndWait(resId: Int, timeout: Long = 2000) = clickAndWaitView(resId, timeout)

    fun isDisplayedAfterWaiting(resId: Int, timeout: Long = 2000) =
        isDisplayedViewAfterWaiting(resId, timeout)
}