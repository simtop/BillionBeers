package com.simtop.billionbeers.composefixture

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.simtop.navigation.contract.BrowseCategory
import com.simtop.navigation.contract.PortableRoute
import com.simtop.navigation.contract.decodeRoute
import com.simtop.navigation.contract.encodeRoute
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private data class PortableRouteKey(val route: PortableRoute) : NavKey

private val routeStateJson = Json { ignoreUnknownKeys = true }

private fun encodeRouteState(entries: List<PortableRouteKey>): String =
  routeStateJson.encodeToString(entries.map { encodeRoute(it.route) })

private fun decodeRouteState(payload: String): List<PortableRouteKey> =
  routeStateJson.decodeFromString<List<String>>(payload).map { payload ->
    PortableRouteKey(decodeRoute(payload))
  }

class Navigation3RuntimeProbeTest {

  @Test
  fun `common Navigation 3 back stack supports push and pop`() {
    val backStack = navigation3ProbeBackStack()

    backStack += Navigation3ProbeKey.Detail("42")

    assertEquals(2, backStack.size)
    assertEquals(Navigation3ProbeKey.Home, backStack[0])
    assertEquals(Navigation3ProbeKey.Detail("42"), backStack[1])
    val removed: Navigation3ProbeKey = backStack.removeLast()

    assertEquals(Navigation3ProbeKey.Detail("42"), removed)
    assertEquals(1, backStack.size)
    assertEquals(Navigation3ProbeKey.Home, backStack[0])
  }

  @Test
  fun `repeated keys remain distinct entries when their values differ`() {
    val backStack = navigation3ProbeBackStack()
    backStack += Navigation3ProbeKey.Detail("first")
    backStack += Navigation3ProbeKey.Detail("second")

    assertEquals(3, backStack.size)
    assertTrue(backStack[1] != backStack[2])
    assertEquals(Navigation3ProbeKey.Detail("second"), backStack.last())
  }

  @Test
  fun `portable route contract can back Navigation 3 entries`() {
    val root = PortableRouteKey(PortableRoute.BeersList)
    val selection =
      PortableRouteKey(PortableRoute.BeerBrowseSelection(BrowseCategory.Style("ipa", "IPA")))
    val backStack = NavBackStack(root)

    backStack += selection

    assertEquals(root, backStack.first())
    assertEquals(selection, backStack.last())
    assertEquals(PortableRoute.BeersList, backStack.first().route)
    assertEquals(
      PortableRoute.BeerBrowseSelection(BrowseCategory.Style("ipa", "IPA")),
      backStack.last().route,
    )
  }

  @Test
  fun `portable route stack state restores through serialization`() {
    val entries =
      listOf(
        PortableRouteKey(PortableRoute.BeersList),
        PortableRouteKey(
          PortableRoute.BeerBrowseSelection(BrowseCategory.Brewery("brewery-1", "Brewery"))
        ),
      )

    val restoredEntries = decodeRouteState(encodeRouteState(entries))
    val restoredBackStack = NavBackStack(restoredEntries.first())
    restoredEntries.drop(1).forEach { restoredBackStack += it }

    assertEquals(entries.size, restoredBackStack.size)
    assertEquals(entries.first(), restoredBackStack.first())
    assertEquals(entries.last(), restoredBackStack.last())
  }

  @Test
  fun `separate tab back stacks retain their active entries`() {
    val fixture = Navigation3TabBackStackFixture()
    val selection = PortableRoute.BeerBrowseSelection(BrowseCategory.Style("ipa", "IPA"))

    fixture.push(selection)
    fixture.selectTab(Navigation3Tab.Favorites)
    fixture.push(PortableRoute.BeersSearch)

    assertEquals(listOf(PortableRoute.Favorites, PortableRoute.BeersSearch), fixture.current)
    fixture.selectTab(Navigation3Tab.Catalog)
    assertEquals(listOf(PortableRoute.BeersList, selection), fixture.current)
    assertEquals(
      mapOf(
        Navigation3Tab.Catalog to listOf(PortableRoute.BeersList, selection),
        Navigation3Tab.Favorites to listOf(PortableRoute.Favorites, PortableRoute.BeersSearch),
      ),
      fixture.snapshot(),
    )
  }

  @Test
  fun `tab back stacks retain roots when popped`() {
    val fixture = Navigation3TabBackStackFixture()
    fixture.push(PortableRoute.BeersSearch)

    assertTrue(fixture.pop())
    assertFalse(fixture.pop())
    assertEquals(listOf(PortableRoute.BeersList), fixture.current)
  }

  @Test
  fun `root remains available after popping the active entry`() {
    val backStack = navigation3ProbeBackStack()
    backStack += Navigation3ProbeKey.Detail("42")

    backStack.removeLast()

    assertFalse(backStack.isEmpty())
    assertEquals(Navigation3ProbeKey.Home, backStack.single())
  }
}
