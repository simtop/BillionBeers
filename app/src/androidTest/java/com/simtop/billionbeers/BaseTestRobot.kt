package com.simtop.billionbeers

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.ViewInteraction
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.assertion.ViewAssertions
import androidx.test.espresso.contrib.RecyclerViewActions
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.espresso.matcher.ViewMatchers.withId
import com.simtop.billionbeers.core.BaseBindView
import com.simtop.billionbeers.core.ViewWrapper
import com.simtop.billionbeers.domain.models.Beer
import com.simtop.billionbeers.utils.RecyclerViewMatchers
import kotlin.reflect.KClass

open class BaseTestRobot {

    fun textView(resId: Int): ViewInteraction = onView(withId(resId))

    fun matchText(viewInteraction: ViewInteraction, text: String): ViewInteraction = viewInteraction
        .check(ViewAssertions.matches(ViewMatchers.withText(text)))

    fun matchText(resId: Int, text: String): ViewInteraction = matchText(textView(resId), text)

    fun <T : Any> clickRecyclerViewPosition(listRes: Int, position: Int = 0, obj : T) {
        onView(withId(listRes))
            .perform(
                RecyclerViewActions.actionOnItemAtPosition<ViewWrapper<BaseBindView<T>>>(
                    position,
                    ViewActions.click()
                )
            )
    }

    fun matchCountRecyclerViewItems(listRes: Int, count: Int = 0) {
        onView(withId(listRes))
            .check(ViewAssertions.matches(RecyclerViewMatchers.withItemCount(count)))
    }

}