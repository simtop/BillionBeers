package com.simtop.billionbeers.robots

import com.simtop.billionbeers.domain.models.Beer
import com.simtop.billionbeers.utils.ViewVisibilityIdlingResource
import java.util.concurrent.TimeUnit

fun homeScreen(func: HomeScreenRobot.() -> Unit) = HomeScreenRobot()
    .apply { func() }

open class HomeScreenRobot : BaseTestRobot() {
    fun clickRecycler(
        listRes: Int,
        position: Int,
        detailScreenRobot: BaseTestRobot = DetailScreenRobot(),
        func: (DetailScreenRobot.() -> Unit)? = null
    ): DetailScreenRobot = clickRecyclerViewPosition(
        listRes,
        position,
        Beer,
        detailScreenRobot,
        func as (BaseTestRobot.() -> Unit)?
    ) as DetailScreenRobot

    fun matchListCount(listRes: Int, count: Int) = matchCountRecyclerViewItems(listRes, count)

    fun matchViewWithText(resId: Int, text: String) = matchText(resId, text)

    fun registerIdling(idlingResource: ViewVisibilityIdlingResource) =
        registerIdlingRegistry(idlingResource)

    fun unregisterIdling(idlingResource: ViewVisibilityIdlingResource) =
        unregisterIdlingRegistry(idlingResource)

    fun setIdlingTimeout(time: Long, timeUnit: TimeUnit = TimeUnit.SECONDS) =
        setIdlingResourceTimeout(time, timeUnit)
}