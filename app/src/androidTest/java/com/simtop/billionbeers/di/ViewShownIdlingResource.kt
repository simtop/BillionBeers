package com.simtop.billionbeers.di

import android.view.View
import androidx.test.espresso.Espresso
import androidx.test.espresso.IdlingResource
import androidx.test.espresso.ViewFinder
import org.hamcrest.Matcher

class ViewShownIdlingResource(
    private val viewMatcher: Matcher<View>,
    private val idlerMatcher: Matcher<View>
) : IdlingResource {

    private var resourceCallback: IdlingResource.ResourceCallback? = null

    override fun isIdleNow(): Boolean {
        val view: View? =
            getView(
                viewMatcher
            )
        val idle = idlerMatcher.matches(view)
        if (idle && resourceCallback != null) {
            resourceCallback!!.onTransitionToIdle()
        }
        return idle
    }

    override fun registerIdleTransitionCallback(resourceCallback: IdlingResource.ResourceCallback) {
        this.resourceCallback = resourceCallback
    }

    override fun getName(): String {
        return this.toString() + viewMatcher.toString()
    }


    companion object {

        private fun getView(viewMatcher: Matcher<View>): View? {
            return try {
                val viewInteraction = Espresso.onView(viewMatcher)
                val finderField =
                    viewInteraction.javaClass.getDeclaredField("viewFinder")
                finderField.isAccessible = true
                val finder = finderField[viewInteraction] as ViewFinder
                finder.view
            } catch (e: Exception) {
                null
            }
        }

    }
}
