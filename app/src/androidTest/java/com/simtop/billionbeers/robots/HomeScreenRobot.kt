package com.simtop.billionbeers.robots

import com.simtop.billionbeers.domain.models.Beer

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
}