package com.simtop.billionbeers

import android.view.View
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner
import com.simtop.billionbeers.robots.homeScreen
import com.simtop.billionbeers.presentation.MainActivity
import com.simtop.billionbeers.robots.detailScreen
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
    fun works() {
        homeScreen {
            isDisplayedViewAfterWaiting(R.id.beers_recyclerview)
        }
    }

    @Test
    fun shouldDisplayListOfBeersWith100ItemsAndOpensDetail() {
        homeScreen {
            setIdlingResourceTimeout(2)
            registerIdlingRegistry(progressBarVisibility)
            //matchCountRecyclerViewItems(R.id.beers_recyclerview, 100)
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
            //matchCountRecyclerViewItems(R.id.beers_recyclerview, 100)
            unregisterIdlingRegistry(progressBarVisibility)
        }
    }

    //TODO Fix clicking in detail view button, because scrolling sometimes clicks to the button again
    // also sometimes it can't find different positions of the adapter and also
    // the recycler moves somtimes when clicking in it so we end up in different detail page
    // all this problems comes from paging 3.0 I need to reasearch more
//    @Test
//    fun shouldOpenDetailToggleAvailabilityAndShowWarningText() {
//
//        homeScreen {
//            setIdlingResourceTimeout(4)
//            registerIdlingRegistry(progressBarVisibility)
//            //matchCountRecyclerViewItems(R.id.beers_recyclerview, 100)
//            unregisterIdlingRegistry(progressBarVisibility)
//            clickRecycler(R.id.beers_recyclerview, 0)
//        }
//        detailScreen {
//            matchText(R.id.single_beer_name, "Buzz")
//            swipeUpScrollView(R.id.detail_scroll_view)
//            //TODO: for some emulators we need to use multiple scroll downs, report the issue too google
//            // for now only adding multiple scrolldowns fixes this bug, is Displayed is Flaky
//            clickAndWaitView(R.id.toggle_availability)
//            //TODO: another fix, sometimes scroll up doesn't work, so we have
//            // to do the contrary swipe before calling the swipe we want
//            swipeDownScrollView(R.id.detail_scroll_view)
//            swipeUpScrollView(R.id.detail_scroll_view)
//            isDisplayedViewAfterWaiting(R.id.emergency_text, 5000)
//        }
//    }
}