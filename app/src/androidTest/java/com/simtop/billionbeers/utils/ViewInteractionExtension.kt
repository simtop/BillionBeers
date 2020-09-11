package com.simtop.billionbeers.utils

import androidx.test.espresso.NoMatchingViewException
import androidx.test.espresso.ViewInteraction
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import java.util.concurrent.TimeoutException

/**
 * Wait for view to be visible
 */
fun ViewInteraction.waitUntilVisible(timeout: Long): ViewInteraction {
    val startTime = System.currentTimeMillis()
    val endTime = startTime + timeout

    do {
        try {
            check(matches(isDisplayed()))
            return this
        } catch (e: NoMatchingViewException) {
            Thread.sleep(50)
        }
    } while (System.currentTimeMillis() < endTime)

    throw TimeoutException()
}