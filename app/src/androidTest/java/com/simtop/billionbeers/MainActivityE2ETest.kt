package com.simtop.billionbeers

import android.view.View
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner
import com.simtop.billionbeers.robots.homeScreen
import com.simtop.billionbeers.presentation.MainActivity
import com.simtop.billionbeers.robots.detailScreen
import com.simtop.billionbeers.utils.ViewVisibilityIdlingResource
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/*
Remember to turn off:
Window animation scale
Transition animation scale
Animator duration scale
 */
@RunWith(AndroidJUnit4ClassRunner::class)
@HiltAndroidTest
class MainActivityE2ETest {

    @get:Rule(order = 0)
    var hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val scenarioRule = ActivityScenarioRule(MainActivity::class.java)

    private val progressBarVisibility by lazy {
        ViewVisibilityIdlingResource(
            R.id.progress_bar,
            View.GONE
        )
    }

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @Test
    fun shouldDisplayListOfBeersWith100ItemsAndOpensDetail() {
        homeScreen {
            setIdlingResourceTimeout(2)
            registerIdlingRegistry(progressBarVisibility)
            matchCountRecyclerViewItems(R.id.beers_recyclerview, 100)
            unregisterIdlingRegistry(progressBarVisibility)
            clickRecycler(R.id.beers_recyclerview, 0)
            matchText(R.id.single_beer_name, "Buzz")
        }
    }

    @Test
    fun shouldDisplayListOfBeersWith100Items() {
        homeScreen {
            setIdlingResourceTimeout(2)
            registerIdlingRegistry(progressBarVisibility)
            matchCountRecyclerViewItems(R.id.beers_recyclerview, 100)
            unregisterIdlingRegistry(progressBarVisibility)
        }
    }

    @Test
    fun shouldOpenDetailToggleAvailabilityAndShowWarningText() {

        homeScreen {
            setIdlingResourceTimeout(2)
            registerIdlingRegistry(progressBarVisibility)
            matchCountRecyclerViewItems(R.id.beers_recyclerview, 100)
            unregisterIdlingRegistry(progressBarVisibility)
            clickRecycler(R.id.beers_recyclerview, 1)
        }
        detailScreen {
            matchText(R.id.single_beer_name, "Trashy Blonde")
            swipeUpScrollView(R.id.detail_scroll_view)
            //TODO: for some emulators we need to use multiple scroll downs, report the issue too google
            // for now only adding multiple scrolldowns fixes this bug, is Displayed is Flaky
            clickAndWaitView(R.id.toggle_availability)
            swipeUpScrollView(R.id.detail_scroll_view)
            isDisplayedViewAfterWaiting(R.id.emergency_text, 5000)
        }
    }
}