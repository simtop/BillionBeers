package com.simtop.billionbeers.snapshot_testing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessibilityMatrixTest {
  @Test
  fun `matrix has one case for every theme scale locale direction and width`() {
    val configurations = AccessibilityMatrix.configurations

    assertEquals(36, configurations.size)
    assertEquals(36, configurations.map { it.name }.toSet().size)
    assertEquals(setOf("light", "dark"), configurations.map { it.theme }.toSet())
    assertEquals(setOf(1f, 1.5f, 2f), configurations.map { it.fontScale }.toSet())
    assertEquals(setOf("en", "fr"), configurations.map { it.locale }.toSet())
    assertEquals(setOf("ltr", "rtl"), configurations.map { it.layoutDirection }.toSet())
    assertEquals(setOf("compact", "expanded"), configurations.map { it.width }.toSet())
    assertTrue(configurations.all { it.name.contains(it.theme) })
  }
}
