package com.simtop.billionbeers

import android.view.View
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner
import com.simtop.billionbeers.robots.homeScreen
import com.simtop.billionbeers.presentation.MainActivity
import com.simtop.billionbeers.utils.ViewVisibilityIdlingResource
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
        homeScreen {
            setIdlingTimeout(2)
            registerIdling(progressBarVisibility)
            matchListCount(R.id.beers_recyclerview, 100)
            unregisterIdling(progressBarVisibility)
            clickRecycler(R.id.beers_recyclerview, 0)
            matchViewWithText(R.id.single_beer_name, "Buzz")
        }
    }

    @Test
    fun shouldDisplayListOfBeersWith100Items() {
        homeScreen {
            setIdlingTimeout(2)
            registerIdling(progressBarVisibility)
            matchListCount(R.id.beers_recyclerview, 100)
            unregisterIdling(progressBarVisibility)
        }
    }

    @Test
    fun shouldOpenDetailToggleAvailabilityAndShowWarningText() {

        homeScreen {
            setIdlingTimeout(2)
            registerIdling(progressBarVisibility)
            matchListCount(R.id.beers_recyclerview, 100)
            unregisterIdling(progressBarVisibility)
            clickRecycler(R.id.beers_recyclerview, 1) {
                matchViewWithText(R.id.single_beer_name, "Trashy Blonde")
                swipeUp(R.id.detail_scroll_view)
                //TODO: for some emulators we need to use multiple scroll downs, report the issue too google
                // for now only adding multiple scrolldowns fixes this bug, is Displayed is Flaky
                clickAndWait(R.id.toggle_availability)
                swipeUp(R.id.detail_scroll_view)
                isDisplayedAfterWaiting(R.id.emergency_text, 5000)
            }
        }
    }
}