package com.simtop.konsist

import com.lemonappdev.konsist.api.Konsist
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The app's JUnit 4 runner silently ignores Jupiter tests, even when their API compiles. */
class AppUnitTestFrameworkTest {

  @Test
  fun `app unit tests use the framework configured by the application convention`() {
    val files = Konsist.scopeFromDirectory("app/src/test").files
    assertTrue(files.isNotEmpty(), "No app unit test sources found; update the checked directory")
    assertTrue(files.any { it.hasImport { import -> import.name == "org.junit.Test" } }) {
      "The app JUnit 4 test tier disappeared; review this rule alongside its runner configuration"
    }

    val violations = files.filter { file ->
      file.hasImport { import -> import.name.startsWith("org.junit.jupiter.") }
    }

    assertTrue(violations.isEmpty()) {
      "App unit tests must use JUnit 4: billionbeers.android.application does not enable " +
        "JUnit Platform, so Jupiter tests and extensions never execute. Offending files: " +
        violations.joinToString { it.name }
    }
  }
}
