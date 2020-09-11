package com.simtop.billionbeers.utils

import android.app.Activity
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage

object ActivityInstance {

    /**
     * Returns an instance of [Activity] in [Stage.RESUMED] state.
     */
    fun getInstance(): Activity {
        var currentActivity: Activity? = null
        val resumedActivities =
            ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
        if (resumedActivities.iterator().hasNext()) {
            currentActivity = resumedActivities.iterator().next()
        }
        return currentActivity!!
    }
}