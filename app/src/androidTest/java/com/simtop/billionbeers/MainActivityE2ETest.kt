package com.simtop.billionbeers

import android.view.View
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.IdlingPolicies
import androidx.test.espresso.IdlingRegistry
import androidx.test.espresso.NoMatchingViewException
import androidx.test.espresso.ViewInteraction
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.RecyclerViewActions
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner
import com.simtop.billionbeers.core.BaseBindView
import com.simtop.billionbeers.core.ViewWrapper
import com.simtop.billionbeers.domain.models.Beer
import com.simtop.billionbeers.presentation.MainActivity
import com.simtop.billionbeers.utils.RecyclerViewMatchers.withItemCount
import com.simtop.billionbeers.utils.ViewVisibilityIdlingResource
import com.simtop.billionbeers.utils.waitUntilVisible
import org.hamcrest.Matchers.not
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/*
Remember to turn off:
Window animation scale
Transition animation scale
Animator duration scale
 */
@RunWith(AndroidJUnit4ClassRunner::class)
class MainActivityE2ETest {

    @get:Rule
    val scenarioRule = ActivityScenarioRule(MainActivity::class.java)

    private val progressBarVisibility by lazy {
        ViewVisibilityIdlingResource(
            R.id.progress_bar,
            View.GONE
        )
    }

    @Test
    fun shouldDisplayListOfBeersWith100ItemsAndOpensDetail() {
        IdlingPolicies.setIdlingResourceTimeout(2, TimeUnit.SECONDS)
        IdlingRegistry.getInstance().register(progressBarVisibility)
        onView(withId(R.id.beers_recyclerview))
            .check(matches(withItemCount(100)))

        IdlingRegistry.getInstance().unregister(progressBarVisibility)
        onView(withId(R.id.beers_recyclerview))
            .perform(
                RecyclerViewActions.actionOnItemAtPosition<ViewWrapper<BaseBindView<Beer>>>(
                    0,
                    click()
                )
            )

        onView(withText("Buzz"))
            .check(matches(isDisplayed()))
    }

    @Test
    fun shouldDisplayListOfBeersWith100Items() {
        IdlingPolicies.setIdlingResourceTimeout(2, TimeUnit.SECONDS)
        IdlingRegistry.getInstance().register(progressBarVisibility)
        onView(withId(R.id.beers_recyclerview))
            .check(matches(withItemCount(100)))

        IdlingRegistry.getInstance().unregister(progressBarVisibility)
        onView(withId(R.id.beers_recyclerview))
            .perform(
                RecyclerViewActions.actionOnItemAtPosition<ViewWrapper<BaseBindView<Beer>>>(
                    0,
                    click()
                )
            )

    }

    @Test
    fun shouldOpenDetailToggleAvailabilityAndShowWarningText() {
        IdlingPolicies.setIdlingResourceTimeout(2, TimeUnit.SECONDS)
        IdlingRegistry.getInstance().register(progressBarVisibility)
        onView(withId(R.id.beers_recyclerview))
            .check(matches(withItemCount(100)))

        IdlingRegistry.getInstance().unregister(progressBarVisibility)
        onView(withId(R.id.beers_recyclerview))
            .perform(
                RecyclerViewActions.actionOnItemAtPosition<ViewWrapper<BaseBindView<Beer>>>(
                    1,
                    click()
                )
            )

        onView(withId(R.id.single_beer_name))
            .check(matches(isDisplayed()))
        onView(withText("Trashy Blonde"))
            .check(matches(isDisplayed()))
        onView(withId(R.id.detailScrollView))
            .perform(swipeUp(), click())
        onView((withId(R.id.toggle_availability)))
            .waitUntilVisible(2000)
            .perform(click())

        //TODO: for some emulators we need to use multiple scroll downs, report the issue too google
        // for now only adding multiple scrolldowns fixes this bug
        onView(withId(R.id.detailScrollView))
            .perform(swipeUp(), click())

        //This comprobation is flaky
        onView(withId(R.id.emergency_text))
            .waitUntilVisible(5000)
            .check(matches(isDisplayed()))
    }
}