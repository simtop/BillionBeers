package com.simtop.billionbeers.utils

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import androidx.test.espresso.matcher.BoundedMatcher
import org.hamcrest.Description
import org.hamcrest.Matcher

object RecyclerViewMatchers {

    fun withItemCount(count: Int): Matcher<View> {
        return object : BoundedMatcher<View, RecyclerView>(RecyclerView::class.java) {
            override fun describeTo(description: Description) {
                description.appendText("has $count items")
            }

            override fun matchesSafely(recyclerView: RecyclerView?): Boolean {
                val adapter = recyclerView?.adapter
                return adapter?.itemCount == count
            }
        }
    }
}