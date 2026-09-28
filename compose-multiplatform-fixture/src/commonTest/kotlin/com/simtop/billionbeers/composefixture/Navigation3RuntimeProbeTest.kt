package com.simtop.billionbeers.composefixture

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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
  fun `root remains available after popping the active entry`() {
    val backStack = navigation3ProbeBackStack()
    backStack += Navigation3ProbeKey.Detail("42")

    backStack.removeLast()

    assertFalse(backStack.isEmpty())
    assertEquals(Navigation3ProbeKey.Home, backStack.single())
  }
}
