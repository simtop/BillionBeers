package com.simtop.billionbeers.robots

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.IdlingPolicies
import androidx.test.espresso.IdlingRegistry
import androidx.test.espresso.ViewInteraction
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions
import androidx.test.espresso.contrib.RecyclerViewActions
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.espresso.matcher.ViewMatchers.withId
import com.simtop.billionbeers.utils.RecyclerViewMatchers
import com.simtop.billionbeers.utils.ViewVisibilityIdlingResource
import com.simtop.billionbeers.utils.waitUntilVisible
import com.simtop.presentation_utils.core.BaseBindView
import com.simtop.presentation_utils.core.ViewWrapper
import java.util.concurrent.TimeUnit

open class BaseTestRobot {

    fun textView(resId: Int): ViewInteraction = onView(withId(resId))

    fun matchText(viewInteraction: ViewInteraction, text: String): ViewInteraction = viewInteraction
        .check(ViewAssertions.matches(ViewMatchers.withText(text)))

    fun matchText(resId: Int, text: String): ViewInteraction = matchText(textView(resId), text)

    fun <T : Any> clickRecyclerViewPosition(listRes: Int,
                                            position: Int = 0,
                                            obj : T,
                                            detailScreenRobot: BaseTestRobot,
                                            func: (BaseTestRobot.() -> Unit)?): BaseTestRobot {
        onView(withId(listRes))
            .perform(
                RecyclerViewActions.actionOnItemAtPosition<com.simtop.presentation_utils.core.ViewWrapper<com.simtop.presentation_utils.core.BaseBindView<T>>>(
                    position,
                    ViewActions.click()
                )
            )
        return detailScreenRobot.apply { func?.let { it() } }
    }

    fun matchCountRecyclerViewItems(listRes: Int, count: Int = 0) {
        onView(withId(listRes))
            .check(ViewAssertions.matches(RecyclerViewMatchers.withItemCount(count)))
    }

    fun registerIdlingRegistry(idlingResource: ViewVisibilityIdlingResource) {
        IdlingRegistry.getInstance().register(idlingResource)
    }

    fun unregisterIdlingRegistry(idlingResource: ViewVisibilityIdlingResource) {
        IdlingRegistry.getInstance().unregister(idlingResource)
    }

    fun setIdlingResourceTimeout(time: Long, timeUnit: TimeUnit = TimeUnit.SECONDS) {
        IdlingPolicies.setIdlingResourceTimeout(time, timeUnit)
    }

    fun swipeUpScrollView(resId: Int) {
        onView(withId(resId)).perform(swipeUp(), click())
    }

    fun swipeDownScrollView(resId: Int) {
        onView(withId(resId)).perform(swipeDown(), click())
    }

    fun clickAndWaitView(resId: Int, timeout: Long = 2000) {
        onView((withId(resId)))
            .waitUntilVisible(timeout)
            .perform(click())
    }

    fun isDisplayedViewAfterWaiting(resId: Int, timeout: Long = 2000) {
        onView(withId(resId))
            .waitUntilVisible(timeout)
            .check(ViewAssertions.matches(ViewMatchers.isDisplayed()))
    }
}