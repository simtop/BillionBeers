package com.simtop.billionbeers.composefixture

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.simtop.navigation.contract.PortableRoute

internal enum class Navigation3Tab {
  Catalog,
  Favorites,
}

internal class Navigation3TabBackStackFixture(
  initialRoute: PortableRoute = PortableRoute.BeersList
) {
  private val stacks =
    Navigation3Tab.entries.associateWithTo(linkedMapOf()) { tab ->
      NavBackStack(TabRouteKey(tab.rootRoute(initialRoute)))
    }
  private var selectedTab = Navigation3Tab.Catalog

  val current: List<PortableRoute>
    get() = currentStack().map { it.route }

  fun selectTab(tab: Navigation3Tab) {
    selectedTab = tab
  }

  fun push(route: PortableRoute) {
    currentStack() += TabRouteKey(route)
  }

  fun pop(): Boolean {
    if (currentStack().size == 1) return false
    currentStack().removeLast()
    return true
  }

  fun snapshot(): Map<Navigation3Tab, List<PortableRoute>> = stacks.mapValues { (_, stack) ->
    stack.map { it.route }
  }

  private fun currentStack(): NavBackStack<TabRouteKey> = checkNotNull(stacks[selectedTab])

  private fun Navigation3Tab.rootRoute(initialRoute: PortableRoute): PortableRoute =
    when (this) {
      Navigation3Tab.Catalog -> initialRoute
      Navigation3Tab.Favorites -> PortableRoute.Favorites
    }
}

private data class TabRouteKey(val route: PortableRoute) : NavKey
