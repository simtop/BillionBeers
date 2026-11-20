package com.simtop.billionbeers.buildlogic

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class UnusedDependenciesHeuristicsTest {

  @Test
  fun `package cache keys include the artifact version`() {
    assertEquals(
      "org.example:library:1.0",
      mavenPackageCacheKey("org.example", "library", "1.0"),
    )
    assertFalse(
      mavenPackageCacheKey("org.example", "library", "1.0") ==
        mavenPackageCacheKey("org.example", "library", "2.0"),
    )
  }

  @Test
  fun `package matching respects package segment boundaries`() {
    assertTrue(packageMatchesImport("com.example", "com.example.Widget"))
    assertTrue(packageMatchesImport("com.example", "com.example"))
    assertFalse(packageMatchesImport("com.example", "com.examples.Widget"))
  }
}
