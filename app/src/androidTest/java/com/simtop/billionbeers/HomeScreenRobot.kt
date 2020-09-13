package com.simtop.billionbeers

import com.simtop.billionbeers.domain.models.Beer

fun homeScreen(func: HomeScreenRobot.() -> Unit) = HomeScreenRobot().apply { func() }

open class HomeScreenRobot : BaseTestRobot(){
    fun click(listRes: Int, position: Int) = clickRecyclerViewPosition(listRes, position, Beer)

    fun matchListCount(listRes: Int, count: Int) = matchCountRecyclerViewItems(listRes, count)
}