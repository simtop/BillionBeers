package com.simtop.billionbeers.composefixture

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.simtop.navigation.contract.BrowseCategory
import com.simtop.navigation.contract.PortableRoute
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.serialization.Serializable

@Serializable private data class PortableRouteKey(val route: PortableRoute) : NavKey

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
  fun `root remains available after popping the active entry`() {
    val backStack = navigation3ProbeBackStack()
    backStack += Navigation3ProbeKey.Detail("42")

    backStack.removeLast()

    assertFalse(backStack.isEmpty())
    assertEquals(Navigation3ProbeKey.Home, backStack.single())
  }
}
